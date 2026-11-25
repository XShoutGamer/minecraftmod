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
        int baseY = Math.max(level.getMinBuildHeight(), (int) Math.floor(nail.getY()));
        var base = new BlockPos(nail.target().getX(), baseY, nail.target().getZ());
        var layout = layout(base, Math.round(nail.modelHeight()));
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

    private static Map<BlockPos, BlockState> layout(BlockPos base, int height) {
        Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) {
            double f = y / (double) (height - 1);
            int radius = f < 0.11 || f > 0.96 ? 0 : f < 0.28 || f > 0.84 ? 1 : 2;
            boolean collar = in(f, 0.30, 0.38) || in(f, 0.69, 0.75) || in(f, 0.82, 0.90);
            if (collar) radius = 3;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d = Math.abs(x) + Math.abs(z);
                    if (d > radius + (radius > 1 ? 1 : 0)) continue;
                    var block = f < 0.30 || f > 0.93 ? CelestialBlocks.CRYSTAL.get() : CelestialBlocks.STONE.get();
                    if (d == 0 || (d == radius && (x == 0 || z == 0))) block = CelestialBlocks.CORE.get();
                    if (collar && d > 0) block = (d == radius + 1 ? CelestialBlocks.FRAME : CelestialBlocks.ENGRAVED).get();
                    layout.put(base.offset(x, y, z), block.defaultBlockState());
                }
            }
        }
        return layout;
    }

    private static boolean in(double value, double minimum, double maximum) {
        return value >= minimum && value <= maximum;
    }
}
