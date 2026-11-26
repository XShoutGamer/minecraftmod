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
    private record Change(UUID owner, BlockState before, BlockState after, long expires) {}
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
        journal.changes.put(pos.immutable(), new Change(player.getUUID(), before, Blocks.AIR.defaultBlockState(), 0));
        journal.setDirty();
    }

    void orbScorch(ServerPlayer player, net.minecraft.world.phys.Vec3 impact, double radius) {
        var level = player.serverLevel();
        int edits = 0;
        for (var column : patch(BlockPos.containing(impact), (int) Math.min(7, radius * .43))) {
            if (edits >= HexConfig.EDITS.get() || changes.size() >= HexConfig.RECORDS.get()) break;
            if (!level.hasChunkAt(column)) continue;
            for (int dy = 2; dy >= -3; dy--) {
                var pos = column.above(dy);
                if (level.isOutsideBuildHeight(pos)) continue;
                var state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (changes.containsKey(pos) || material(state) == null) break;
                var air = Blocks.AIR.defaultBlockState();
                if (replace(player, pos, state, air)) {
                    changes.put(pos, new Change(player.getUUID(), state, air, 0));
                    setDirty();
                    edits++;
                }
                break;
            }
        }
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

    int rewrite(ServerPlayer player, BlockPos column) {
        var level = player.serverLevel();
        if (!HexConfig.EDIT.get() || !player.hasPermissions(2) || !level.hasChunkAt(column)
                || changes.size() >= HexConfig.RECORDS.get()) return 0;
        int surface = Math.min(level.getMaxBuildHeight() - 1, column.getY() + 10);
        int minY = Math.max(level.getMinBuildHeight(), column.getY() - 13);
        while (surface >= minY && level.isEmptyBlock(column.atY(surface))) surface--;
        if (surface < minY) return 0;
        int changed = 0;
        long duration = HexConfig.REWRITE_SECONDS.get() * 20L;
        long expires = duration > 0 ? level.getGameTime() + duration : 0;
        for (int step = 0; step < HexConfig.REWRITE_DEPTH.get() && changes.size() < HexConfig.RECORDS.get(); step++) {
            var pos = column.atY(surface - step);
            if (level.isOutsideBuildHeight(pos) || changes.containsKey(pos)) break;
            var before = level.getBlockState(pos);
            var after = material(before);
            if (after == null) break;
            if (before.equals(after)) continue;
            if (!replace(player, pos, before, after)) break;
            changes.put(pos, new Change(player.getUUID(), before, after, expires));
            changed++;
        }
        if (changed > 0) setDirty();
        return changed;
    }

    private static BlockState material(BlockState before) {
        boolean stone = before.is(Blocks.STONE) || before.is(Blocks.ANDESITE) || before.is(Blocks.COBBLESTONE)
                || before.is(Blocks.DEEPSLATE) || before.is(Blocks.GRANITE) || before.is(Blocks.DIORITE);
        boolean dirt = before.is(net.minecraft.tags.BlockTags.DIRT);
        boolean sand = before.is(net.minecraft.tags.BlockTags.SAND);
        if (!stone && !dirt && !sand) return null;
        var block = switch (HexConfig.REWRITE.get()) {
            case SCARLET -> stone ? Blocks.AMETHYST_BLOCK : dirt ? Blocks.OBSIDIAN : Blocks.RED_STAINED_GLASS;
            case CRYSTALLINE -> stone ? Blocks.AMETHYST_BLOCK : Blocks.TINTED_GLASS;
            case CELESTIAL -> stone ? Blocks.QUARTZ_BLOCK : Blocks.CALCITE;
            case GOLDEN -> stone ? Blocks.YELLOW_TERRACOTTA : Blocks.SMOOTH_SANDSTONE;
            case OBSIDIAN -> Blocks.OBSIDIAN;
            case NATURAL -> stone ? Blocks.STONE : dirt ? Blocks.GRASS_BLOCK : Blocks.SAND;
            case CUSTOM -> {
                var id = net.minecraft.resources.ResourceLocation.tryParse(HexConfig.REWRITE_BLOCK.get());
                yield id == null ? Blocks.AIR : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
            }
        };
        var state = block.defaultBlockState();
        return state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.is(io.github.xshoutgamer.minecraftmod.nail.CelestialNail.IMPACT_IMMUNE)
                || block instanceof net.minecraft.world.level.block.FallingBlock
                || !state.blocksMotion() ? null : state;
    }

    boolean restore(ServerPlayer player, BlockPos pos) {
        var change = changes.get(pos);
        var level = player.serverLevel();
        if (change == null || !change.owner.equals(player.getUUID()) || !player.hasPermissions(2)
                || !level.hasChunkAt(pos) || !level.getBlockState(pos).equals(change.after)) return false;
        if (!replace(player, pos, change.after, change.before)) return false;
        changes.remove(pos);
        setDirty();
        return true;
    }

    void restoreExpired(ServerPlayer player) {
        long now = player.level().getGameTime();
        var positions = changes.entrySet().stream()
                .filter(entry -> entry.getValue().expires > 0 && entry.getValue().expires <= now
                        && entry.getValue().owner.equals(player.getUUID()) && player.serverLevel().hasChunkAt(entry.getKey())
                        && player.level().getBlockState(entry.getKey()).equals(entry.getValue().after))
                .map(Map.Entry::getKey).limit(HexConfig.EDITS.get()).toList();
        for (var pos : positions) restore(player, pos);
    }

    private static boolean replace(ServerPlayer player, BlockPos pos, BlockState before, BlockState after) {
        var level = player.serverLevel();
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || (!after.isAir() && !level.isUnobstructed(after, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                || !level.mayInteract(player, pos) || before.hasBlockEntity() || !before.getFluidState().isEmpty()
                || before.getDestroySpeed(level, pos) < 0 || after.getDestroySpeed(level, pos) < 0
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
            entry.putLong("Expires", change.expires);
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
                    NbtUtils.readBlockState(blocks, entry.getCompound("After")), entry.getLong("Expires")));
        }
        return data;
    }
}
