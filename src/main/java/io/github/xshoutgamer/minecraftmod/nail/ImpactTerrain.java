package io.github.xshoutgamer.minecraftmod.nail;

import io.github.xshoutgamer.minecraftmod.CelestialBlocks;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

final class ImpactTerrain {
    static BlockPos surface(ServerLevel level, BlockPos center, int x, int z) {
        BlockPos column = center.offset(x, 0, z);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) return null;
        // Search a bounded vertical band around the chosen ground. This keeps underground
        // casts from modifying the surface far above them.
        int y = Math.min(center.getY() + 44, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ()) - 1);
        for (; y >= Math.max(level.getMinBuildHeight(), center.getY() - 60); y--) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            var state = level.getBlockState(pos);
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) return pos;
        }
        return null;
    }

    static boolean protectedBlock(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (owner == null || !level.mayInteract(owner, pos) || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0 || !state.getFluidState().isEmpty()
                || state.is(CelestialNail.IMPACT_IMMUNE)) return true;
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, owner)).isCanceled();
    }

    static int cut(ServerLevel level, ServerPlayer owner, BlockPos surface, int depth) {
        if (surface == null) return 0;
        int i = 0;
        for (; i < depth; i++) {
            BlockPos pos = surface.below(i);
            if (level.isOutsideBuildHeight(pos)) break;
            if (level.isEmptyBlock(pos)) continue;
            if (protectedBlock(level, owner, pos)) break;
            removeForImpact(level, owner, pos);
        }
        return i;
    }

    static void clearDescent(ServerLevel level, ServerPlayer owner, CelestialNail nail, int bottom, int top) {
        int low = Math.max(bottom, level.getMinBuildHeight());
        int high = Math.min(top, level.getMaxBuildHeight());
        if (low >= high) return;

        BlockPos target = nail.target();
        double center = nail.centerOffset();
        double radius = Math.max(6, nail.modelWidth() * 0.5 + 1.5);
        int extent = (int) Math.ceil(radius);
        var pos = new BlockPos.MutableBlockPos();
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                double dx = x + .5 - center, dz = z + .5 - center;
                if (dx * dx + dz * dz > radius * radius) continue;
                pos.set(target.getX() + x, low, target.getZ() + z);
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                for (int y = Math.min(high, surface) - 1; y >= low; y--) {
                    pos.setY(y);
                    if (level.isEmptyBlock(pos)) continue;
                    var block = pos.immutable();
                    if (!protectedBlock(level, owner, block)) removeForImpact(level, owner, block);
                }
            }
        }
    }

    private static void removeForImpact(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        if (HexConfig.RECORD_NAIL.get()) {
            var original = level.getBlockState(pos);
            if (level.removeBlock(pos, false)) HexTerrain.recordNailCut(level, owner, pos, original);
        } else level.removeBlock(pos, false);
    }

    static BlockPos placeLight(ServerLevel level, ServerPlayer owner, BlockPos center, int depth) {
        var floor = surface(level, center, 0, 0);
        if (floor == null) return null;
        var pos = floor.above(Math.min(depth, 2));
        return place(level, owner, pos, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 14)) ? pos : null;
    }

    static void residue(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        place(level, owner, pos, CelestialBlocks.FRACTURE.get().defaultBlockState());
    }

    private static boolean place(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState state) {
        if (owner == null || level.isOutsideBuildHeight(pos) || !level.isEmptyBlock(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(owner, pos)) return false;
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlockAndUpdate(pos, state)) return false;
        if (EventHooks.onBlockPlace(owner, snapshot, Direction.UP)) {
            snapshot.restore();
            return false;
        }
        return true;
    }
}
