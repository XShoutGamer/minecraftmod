package io.github.xshoutgamer.minecraftmod.nail;

import java.util.Random;
import io.github.xshoutgamer.minecraftmod.CelestialBlocks;
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
        int y = Math.min(center.getY() + 20, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ()) - 1);
        for (; y >= Math.max(level.getMinBuildHeight(), center.getY() - 24); y--) {
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

    static void cut(ServerLevel level, ServerPlayer owner, BlockPos surface, int depth) {
        if (surface == null) return;
        for (int i = 0; i < depth; i++) {
            BlockPos pos = surface.below(i);
            if (level.isOutsideBuildHeight(pos) || protectedBlock(level, owner, pos)) break;
            level.removeBlock(pos, false);
        }
    }

    static BlockPos placeLight(ServerLevel level, ServerPlayer owner, BlockPos center, int depth) {
        var floor = surface(level, center, 0, 0);
        if (floor == null) return null;
        var pos = floor.above(Math.min(depth, 2));
        return place(level, owner, pos, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 14)) ? pos : null;
    }

    static void scatterRubble(ServerLevel level, ServerPlayer owner, BlockPos center, int radius, long seed) {
        Random random = new Random(seed ^ 0xABE2A79F);
        for (int i = 0; i < 11; i++) {
            double angle = i * Math.PI * 2 / 11 + random.nextDouble() * 0.42;
            double distance = radius * (0.91 + random.nextDouble() * 0.23);
            var floor = surface(level, center, (int) Math.round(Math.cos(angle) * distance),
                    (int) Math.round(Math.sin(angle) * distance));
            if (floor == null || protectedBlock(level, owner, floor)) continue;
            var material = level.getBlockState(floor.getY() == level.getMinBuildHeight() ? floor : floor.below());
            var rubble = material.is(Blocks.DEEPSLATE) ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE;
            if (material.is(BlockTags.BASE_STONE_NETHER)) rubble = Blocks.NETHERRACK;
            place(level, owner, floor.above(), rubble.defaultBlockState());
        }
    }

    static void rim(ServerLevel level, ServerPlayer owner, BlockPos floor, int height) {
        if (floor == null || protectedBlock(level, owner, floor)) return;
        var stone = level.getBlockState(floor).is(BlockTags.BASE_STONE_NETHER) ? Blocks.NETHERRACK : Blocks.TUFF;
        for (int i = 1; i <= height; i++) {
            if (!place(level, owner, floor.above(i), stone.defaultBlockState())) break;
        }
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
