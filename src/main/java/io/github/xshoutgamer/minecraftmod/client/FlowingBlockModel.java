package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

final class FlowingBlockModel extends BakedModelWrapper<BakedModel> {
    static final int HORIZONTAL_PERIOD = 8;
    static final int VERTICAL_PERIOD = 32;
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int UV = 4;
    private static final ModelProperty<BlockPos> POSITION = new ModelProperty<>();
    private static final Set<String> BLOCKS = Set.of("luminous_core", "celestial_crystal", "celestial_fracture");
    private record Key(BakedQuad quad, int u, int v) {}
    private final ConcurrentHashMap<Key, BakedQuad> cache = new ConcurrentHashMap<>();

    private FlowingBlockModel(BakedModel original) { super(original); }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) -> id.id().getNamespace().equals(MinecraftMod.MOD_ID)
                && id.variant().isEmpty() && BLOCKS.contains(id.id().getPath()) ? new FlowingBlockModel(model) : model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return super.getModelData(level, pos, state, data).derive().with(POSITION, pos.immutable()).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        var original = super.getQuads(state, side, random, data, type);
        var pos = data.get(POSITION);
        if (pos == null || original.isEmpty()) return original;
        int u = Math.floorMod(pos.getX() + pos.getZ(), HORIZONTAL_PERIOD);
        int v = Math.floorMod(-pos.getY() - 1, VERTICAL_PERIOD);
        return original.stream().map(quad ->
                cache.computeIfAbsent(new Key(quad, u, v), FlowingBlockModel::project)).toList();
    }

    private static BakedQuad project(Key key) {
        var source = key.quad;
        var sprite = source.getSprite();
        int[] vertices = source.getVertices().clone();
        for (int i = 0; i < vertices.length; i += STRIDE) {
            float x = Float.intBitsToFloat(vertices[i]);
            float y = Float.intBitsToFloat(vertices[i + 1]);
            float z = Float.intBitsToFloat(vertices[i + 2]);
            // Opposite and adjacent faces share the same world-space axes.
            float fixed = switch (source.getDirection()) {
                case EAST, SOUTH -> 1;
                default -> 0;
            };
            float along = switch (source.getDirection().getAxis()) {
                case X -> z;
                case Y -> x + z;
                case Z -> x;
            };
            int offset = Math.floorMod(key.u + (int) fixed, HORIZONTAL_PERIOD);
            // Two horizontal copies allow the quad to cross the integer period without wrapping its vertices.
            vertices[i + UV] = Float.floatToRawIntBits(sprite.getU(((offset + along) * 256 / HORIZONTAL_PERIOD + .5F) / 512));
            float v = (key.v + (1 - y)) / VERTICAL_PERIOD;
            vertices[i + UV + 1] = Float.floatToRawIntBits(sprite.getV((v * 255 + .5F) / 256));
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), sprite, source.isShade(), source.hasAmbientOcclusion());
    }
}
