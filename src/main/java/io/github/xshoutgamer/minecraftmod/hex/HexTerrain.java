package io.github.xshoutgamer.minecraftmod.hex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class HexTerrain extends SavedData {
    private record Change(UUID owner, BlockState before, BlockState after) {}
    private static final Factory<HexTerrain> FACTORY = new Factory<>(HexTerrain::new, HexTerrain::load);
    private final Map<BlockPos, Change> changes = new LinkedHashMap<>();

    static HexTerrain get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "minecraftmod_hex_terrain");
    }

    public static void recordNailCut(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState before) {
        if (!HexConfig.RECORD_NAIL.get() || !player.hasPermissions(2) || before.hasBlockEntity()
                || !before.getFluidState().isEmpty() || !level.isEmptyBlock(pos)) return;
        var journal = get(level);
        if (journal.changes.size() >= HexConfig.RECORDS.get() || journal.changes.containsKey(pos)) return;
        journal.changes.put(pos.immutable(), new Change(player.getUUID(), before, Blocks.AIR.defaultBlockState()));
        journal.setDirty();
    }

    static Deque<BlockPos> patch(BlockPos center, int radius) {
        var positions = new ArrayList<BlockPos>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) positions.add(center.offset(x, 0, z));
            }
        }
        positions.sort(Comparator.comparingDouble(center::distSqr));
        return new ArrayDeque<>(positions);
    }

    Deque<BlockPos> restoration(UUID owner, BlockPos center, double radius) {
        var positions = changes.entrySet().stream()
                .filter(entry -> entry.getValue().owner.equals(owner) && entry.getKey().distSqr(center) <= radius * radius)
                .map(Map.Entry::getKey).sorted(Comparator.comparingDouble(center::distSqr)).toList();
        return new ArrayDeque<>(positions);
    }

    void rewrite(ServerPlayer player, BlockPos column) {
        var level = player.serverLevel();
        if (!HexConfig.EDIT.get() || !player.hasPermissions(2) || !level.hasChunkAt(column)
                || changes.size() >= HexConfig.RECORDS.get()) return;
        for (int dy = 2; dy >= -3; dy--) {
            var pos = column.above(dy);
            if (level.isOutsideBuildHeight(pos)) continue;
            var before = level.getBlockState(pos);
            if (before.isAir()) continue;
            if (!level.isEmptyBlock(pos.above()) || changes.containsKey(pos)) return;
            BlockState after;
            if (before.is(Blocks.STONE) || before.is(Blocks.ANDESITE) || before.is(Blocks.COBBLESTONE)) {
                after = Blocks.AMETHYST_BLOCK.defaultBlockState();
            } else if (before.is(Blocks.DIRT) || before.is(Blocks.GRASS_BLOCK)) {
                after = Blocks.OBSIDIAN.defaultBlockState();
            } else if (before.is(Blocks.SAND) || before.is(Blocks.RED_SAND)) {
                after = Blocks.RED_STAINED_GLASS.defaultBlockState();
            } else return;

            if (replace(player, pos, before, after)) {
                changes.put(pos, new Change(player.getUUID(), before, after));
                setDirty();
            }
            return;
        }
    }

    void restore(ServerPlayer player, BlockPos pos) {
        var change = changes.get(pos);
        var level = player.serverLevel();
        if (change == null || !change.owner.equals(player.getUUID()) || !player.hasPermissions(2)
                || !level.hasChunkAt(pos) || !level.getBlockState(pos).equals(change.after)) return;
        if (replace(player, pos, change.after, change.before)) {
            changes.remove(pos);
            setDirty();
        }
    }

    private static boolean replace(ServerPlayer player, BlockPos pos, BlockState before, BlockState after) {
        var level = player.serverLevel();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || (!after.isAir() && !level.isUnobstructed(after, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                || !level.mayInteract(player, pos) || before.hasBlockEntity() || !before.getFluidState().isEmpty()
                || before.is(io.github.xshoutgamer.minecraftmod.nail.CelestialNail.IMPACT_IMMUNE)
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, before, player)).isCanceled()) return false;
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, after, Block.UPDATE_ALL)) return false;
        if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore();
            return false;
        }
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        changes.forEach((pos, change) -> {
            var entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.putUUID("Owner", change.owner);
            entry.put("Before", NbtUtils.writeBlockState(change.before));
            entry.put("After", NbtUtils.writeBlockState(change.after));
            entries.add(entry);
        });
        tag.put("Changes", entries);
        return tag;
    }

    private static HexTerrain load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new HexTerrain();
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag item : tag.getList("Changes", Tag.TAG_COMPOUND)) {
            if (!(item instanceof CompoundTag entry) || !entry.hasUUID("Owner")) continue;
            data.changes.put(BlockPos.of(entry.getLong("Pos")), new Change(entry.getUUID("Owner"),
                    NbtUtils.readBlockState(blocks, entry.getCompound("Before")),
                    NbtUtils.readBlockState(blocks, entry.getCompound("After"))));
        }
        return data;
    }
}
