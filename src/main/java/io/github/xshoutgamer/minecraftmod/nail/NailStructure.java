package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.CelestialBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

final class NailStructure {
    private NailStructure() {}

    static boolean place(ServerLevel level, ServerPlayer owner, CelestialNail nail, boolean terrain, BlockPos light) {
        if (owner == null) return false;
        int baseY = nail.baseY();
        var base = new BlockPos(nail.target().getX(), baseY, nail.target().getZ());
        var layout = layout(base, Math.round(nail.modelHeight()), nail.modelWidth());
        if (terrain) addFoundation(level, base, layout);
        List<BlockSnapshot> snapshots = new ArrayList<>(layout.size());
        for (BlockPos pos : layout.keySet()) {
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)
                    || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(owner, pos)) return false;
            var original = level.getBlockState(pos);
            boolean temporaryLight = pos.equals(light) && original.is(Blocks.LIGHT);
            if (!original.isAir() && !temporaryLight
                    && (!terrain || pos.getY() > nail.target().getY() || ImpactTerrain.protectedBlock(level, owner, pos))) return false;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) return false;
            snapshots.add(BlockSnapshot.create(level.dimension(), level, pos));
        }

        int placed = 0;
        boolean committed = false;
        try {
            for (var part : layout.entrySet()) {
                if (!level.setBlock(part.getKey(), part.getValue(), Block.UPDATE_KNOWN_SHAPE)) return false;
                placed++;
            }
            BlockState support = level.getBlockState(base.below());
            if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityMultiPlaceEvent(snapshots, support, owner)).isCanceled()) return false;
            committed = true;
            return true;
        } finally {
            if (committed) {
                for (var snapshot : snapshots) {
                    var pos = snapshot.getPos();
                    var state = layout.get(pos);
                    level.sendBlockUpdated(pos, snapshot.getState(), state, Block.UPDATE_CLIENTS);
                    level.updateNeighborsAt(pos, state.getBlock());
                }
            } else {
                for (int i = placed - 1; i >= 0; i--) snapshots.get(i).restore();
            }
        }
    }

    static Map<BlockPos, BlockState> layout(BlockPos base, int height, int width) {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        double centerOffset = width % 2 == 0 ? 0 : 0.5;
        int min = -width / 2, max = min + width;
        for (int y = 0; y < height; y++) {
            double elevation = (y + 0.5) / height;
            double radius = Math.max(0.76, width * NailProfile.widthAt(elevation) * 0.5);
            boolean collar = (elevation > .32 && elevation < .39) || (elevation > .82 && elevation < .89);
            boolean crystalline = elevation < .28 || elevation > .93;
            for (int x = min; x < max; x++) {
                for (int z = min; z < max; z++) {
                    double dx = x + .5 - centerOffset, dz = z + .5 - centerOffset;
                    double distance = Math.hypot(dx, dz);
                    if (distance > radius) continue;
                    boolean edge = distance >= radius - 0.9;
                    boolean channel = (Math.abs(dx) < 0.55 || Math.abs(dz) < 0.55) && y % 5 != 2;
                    var block = crystalline ? CelestialBlocks.CRYSTAL.get() : CelestialBlocks.STONE.get();
                    if (distance < 0.76 || (edge && channel)) block = CelestialBlocks.CORE.get();
                    else if (collar && edge) block = CelestialBlocks.FRAME.get();
                    else if (edge && (y % 7 == 2 || y % 7 == 3)) block = CelestialBlocks.ENGRAVED.get();
                    layout.put(base.offset(x, y, z), block.defaultBlockState());
                }
            }
        }
        return layout;
    }

    private static void addFoundation(ServerLevel level, BlockPos base, Map<BlockPos, BlockState> layout) {
        if (!level.hasChunkAt(base)) return;
        for (int down = 1; down <= 20; down++) {
            BlockPos support = base.below(down);
            if (level.isOutsideBuildHeight(support) || !level.isEmptyBlock(support)) break;
            layout.put(support, CelestialBlocks.FRACTURE.get().defaultBlockState());
        }
    }
}
