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
    private record Change(UUID owner, BlockState before, BlockState after, long expires, UUID event) {
        Change(UUID owner, BlockState before, BlockState after, long expires) {
            this(owner, before, after, expires, null);
        }
    }
    private static final Factory<HexTerrain> FACTORY = new Factory<>(HexTerrain::new, HexTerrain::load);
    private final Map<BlockPos, Change> changes = new LinkedHashMap<>();
    private long reconstructionTick = Long.MIN_VALUE;

    static HexTerrain get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "minecraftmod_hex_terrain");
    }

    private static boolean isWorldOwner(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        return server.isSingleplayer() && server.isSingleplayerOwner(player.getGameProfile());
    }

    static boolean canRewrite(ServerPlayer player) {
        return isWorldOwner(player) ? HexConfig.OWNER_REWRITE.get()
                : HexConfig.EDIT.get() && player.hasPermissions(2);
    }

    static boolean canRestore(ServerPlayer player) {
        return isWorldOwner(player) || player.hasPermissions(2);
    }

    static String rewriteRestriction(ServerPlayer player) {
        if (isWorldOwner(player)) return "Reality Rewrite is cosmetic. Enable singlePlayerTerrainRewrite in the Hex config to edit blocks.";
        if (!HexConfig.EDIT.get()) return "Reality Rewrite is cosmetic. The server has terrainRewrite disabled.";
        return "Reality Rewrite is cosmetic. Only a server operator can edit blocks with it.";
    }

    public static void recordNailCut(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState before) {
        if (!HexConfig.RECORD_NAIL.get() || !player.hasPermissions(2) || before.hasBlockEntity()
                || !before.getFluidState().isEmpty() || !level.isEmptyBlock(pos)) return;
        var journal = get(level);
        if (journal.full() || journal.changes.containsKey(pos)) return;
        journal.changes.put(pos.immutable(), new Change(player.getUUID(), before, Blocks.AIR.defaultBlockState(), 0));
        journal.setDirty();
    }

    void orbScorch(ServerPlayer player, net.minecraft.world.phys.Vec3 impact, double radius) {
        var level = player.serverLevel();
        int edits = 0;
        for (var column : patch(BlockPos.containing(impact), (int) Math.min(7, radius * .43))) {
            if (edits >= HexConfig.EDITS.get() || full()) break;
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

    boolean full() { return changes.size() >= HexConfig.restorationRecordLimit(); }

    int keep(UUID owner, BlockPos center, int radius, boolean confirm) {
        int count = 0;
        double radiusSquared = (double) radius * radius;
        var entries = changes.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!entry.getValue().owner.equals(owner)
                    || (radius > 0 && entry.getKey().distSqr(center) > radiusSquared)) continue;
            count++;
            if (confirm) entries.remove();
        }
        if (confirm && count > 0) setDirty();
        return count;
    }

    static Deque<BlockPos> volume(BlockPos center, int radius, HexConfig.Volume shape) {
        if (shape == HexConfig.Volume.TERRAIN) return patch(center, radius);
        double inner = HexConfig.CORRUPTION_INNER.get(), middle = HexConfig.CORRUPTION_MIDDLE.get(), edge = HexConfig.CORRUPTION_EDGE.get();
        var shells = new ArrayList<Deque<BlockPos>>(radius + 1);
        for (int i = 0; i <= radius; i++) shells.add(new ArrayDeque<>());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    double distance = switch (shape) {
                        case SPHERE -> Math.sqrt(x * x + y * y + z * z);
                        case CYLINDER -> Math.max(Math.abs(y), Math.hypot(x, z));
                        default -> Math.max(Math.abs(y), Math.max(Math.abs(x), Math.abs(z)));
                    };
                    if (distance > radius) continue;
                    var pos = center.offset(x, y, z);
                    double fraction = distance / radius;
                    double coverage = fraction < .55 ? inner : fraction < .83 ? middle : edge;
                    if (Math.floorMod(Long.hashCode(pos.asLong() * 0x9e3779b97f4a7c15L), 1000) >= coverage * 1000) continue;
                    shells.get((int) Math.ceil(distance)).add(pos);
                }
            }
        }
        var result = new ArrayDeque<BlockPos>();
        for (var shell : shells) result.addAll(shell);
        return result;
    }

    int rewriteBlock(ServerPlayer player, BlockPos pos) {
        long duration = HexConfig.REWRITE_SECONDS.get() * 20L;
        long expires = duration == 0 ? 0 : player.level().getGameTime() + duration;
        return rewriteBlock(player, pos, HexConfig.REWRITE.get(), null, expires);
    }

    int rewriteBlock(ServerPlayer player, BlockPos pos, HexConfig.Rewrite preset, UUID event, long expires) {
        var level = player.serverLevel();
        if (!canRewrite(player) || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || changes.containsKey(pos) || full()) return 0;
        var before = level.getBlockState(pos);
        var after = material(before, preset);
        if (after == null || before.equals(after)) return 0;
        var record = new Change(player.getUUID(), before, after, expires, event);
        changes.put(pos, record);
        if (!replace(player, pos, before, after)) {
            changes.remove(pos, record);
            return 0;
        }
        setDirty();
        return 1;
    }

    Map<UUID, Integer> sovereigntyEvents(UUID owner) {
        var events = new LinkedHashMap<UUID, Integer>();
        for (var change : changes.values()) {
            if (change.event != null && owner.equals(change.owner)) events.merge(change.event, 1, Integer::sum);
        }
        return events;
    }

    Deque<BlockPos> expireEvent(UUID owner, UUID event, long now) {
        var result = new ArrayDeque<BlockPos>();
        for (var entry : changes.entrySet()) {
            var change = entry.getValue();
            if (!owner.equals(change.owner) || !event.equals(change.event)) continue;
            entry.setValue(new Change(change.owner, change.before, change.after, now, event));
            result.addLast(entry.getKey());
        }
        if (!result.isEmpty()) setDirty();
        return interleaveChunks(result);
    }

    boolean disintegrate(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!canRewrite(player) || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || full() || changes.containsKey(pos)) return false;
        var before = level.getBlockState(pos);
        if (before.isAir() || !before.blocksMotion() || before.is(io.github.xshoutgamer.minecraftmod.CelestialBlocks.SCARLET_STONE.get())
                || before.is(Blocks.DIAMOND_BLOCK) || before.is(Blocks.NETHERITE_BLOCK)) return false;
        var air = Blocks.AIR.defaultBlockState();
        if (!replace(player, pos, before, air)) return false;
        changes.put(pos, new Change(player.getUUID(), before, air, 0));
        setDirty();
        return true;
    }

    Deque<BlockPos> restoration(UUID owner, BlockPos center, double radius) {
        var positions = changes.entrySet().stream()
                .filter(entry -> entry.getValue().owner.equals(owner) && entry.getKey().distSqr(center) <= radius * radius)
                .map(Map.Entry::getKey).sorted(Comparator.comparingDouble(center::distSqr)).toList();
        return new ArrayDeque<>(positions);
    }

    Deque<BlockPos> reconstruction(UUID owner, BlockPos center, double radius) {
        return interleaveChunks(restoration(owner, center, radius));
    }

    private static Deque<BlockPos> interleaveChunks(Iterable<BlockPos> work) {
        var chunks = new LinkedHashMap<Long, Deque<BlockPos>>();
        for (var pos : work) {
            long chunk = net.minecraft.world.level.ChunkPos.asLong(pos);
            chunks.computeIfAbsent(chunk, ignored -> new ArrayDeque<>()).add(pos);
        }
        var result = new ArrayDeque<BlockPos>();
        while (!chunks.isEmpty()) {
            var iter = chunks.values().iterator();
            while (iter.hasNext()) {
                var positions = iter.next();
                for (int i = 0; i < 32 && !positions.isEmpty(); i++) result.addLast(positions.removeFirst());
                if (positions.isEmpty()) iter.remove();
            }
        }
        return result;
    }

    int claimReconstructionBudget(long now) {
        return claimReconstructionBudget(now, 1);
    }

    int claimReconstructionBudget(long now, double multiplier) {
        if (now == reconstructionTick) return 0;
        reconstructionTick = now;
        return (int) (genesisBudget() * multiplier);
    }

    static int genesisBudget() {
        return switch (HexConfig.GENESIS_SPEED.get()) {
            case CINEMATIC -> 64;
            case RAPID -> 384;
            case DIVINE -> 1024;
        };
    }

    static double distance(BlockPos center, BlockPos pos, HexConfig.Volume shape) {
        int x = Math.abs(pos.getX() - center.getX()), y = Math.abs(pos.getY() - center.getY());
        int z = Math.abs(pos.getZ() - center.getZ());
        return switch (shape) {
            case SPHERE -> Math.sqrt(center.distSqr(pos));
            case CYLINDER -> Math.max(y, Math.hypot(x, z));
            case CUBE -> Math.max(y, Math.max(x, z));
            case TERRAIN -> Math.hypot(x, z);
        };
    }

    int rewrite(ServerPlayer player, BlockPos column) {
        var level = player.serverLevel();
        if (!canRewrite(player) || !level.hasChunkAt(column)
                || full()) return 0;
        int surface = Math.min(level.getMaxBuildHeight() - 1, column.getY() + 10);
        int minY = Math.max(level.getMinBuildHeight(), column.getY() - 13);
        while (surface >= minY && level.isEmptyBlock(column.atY(surface))) surface--;
        if (surface < minY) return 0;
        int changed = 0;
        long duration = HexConfig.REWRITE_SECONDS.get() * 20L;
        long expires = duration > 0 ? level.getGameTime() + duration : 0;
        for (int step = 0; step < HexConfig.REWRITE_DEPTH.get() && !full(); step++) {
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
        return material(before, HexConfig.REWRITE.get());
    }

    private static BlockState material(BlockState before, HexConfig.Rewrite preset) {
        boolean stone = before.is(Blocks.STONE) || before.is(Blocks.ANDESITE) || before.is(Blocks.COBBLESTONE)
                || before.is(Blocks.DEEPSLATE) || before.is(Blocks.GRANITE) || before.is(Blocks.DIORITE);
        boolean dirt = before.is(net.minecraft.tags.BlockTags.DIRT);
        boolean sand = before.is(net.minecraft.tags.BlockTags.SAND);
        boolean logs = before.is(net.minecraft.tags.BlockTags.LOGS);
        boolean planks = before.is(net.minecraft.tags.BlockTags.PLANKS);
        boolean foliage = before.is(net.minecraft.tags.BlockTags.LEAVES) || before.is(net.minecraft.tags.BlockTags.FLOWERS)
                || before.is(Blocks.SHORT_GRASS) || before.is(Blocks.FERN) || before.is(Blocks.DEAD_BUSH)
                || before.is(net.minecraft.tags.BlockTags.SAPLINGS);
        boolean masonry = before.is(Blocks.STONE_BRICKS) || before.is(Blocks.BRICKS) || before.is(Blocks.DEEPSLATE_BRICKS)
                || before.is(Blocks.POLISHED_ANDESITE) || before.is(Blocks.QUARTZ_BLOCK) || before.is(Blocks.SANDSTONE);
        if (!stone && !dirt && !sand && !logs && !planks && !foliage && !masonry) return null;
        if (preset == HexConfig.Rewrite.SCARLET) {
            if (logs) return io.github.xshoutgamer.minecraftmod.CelestialBlocks.BLIGHT_LOG.get().defaultBlockState().setValue(
                    net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    before.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                            ? before.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) : Direction.Axis.Y);
            if (planks) return io.github.xshoutgamer.minecraftmod.CelestialBlocks.BLIGHT_PLANKS.get().defaultBlockState();
            if (foliage) return Blocks.AIR.defaultBlockState();
            if (masonry) return io.github.xshoutgamer.minecraftmod.CelestialBlocks.BLIGHT_BRICKS.get().defaultBlockState();
            return (stone ? io.github.xshoutgamer.minecraftmod.CelestialBlocks.SCARLET_STONE.get()
                    : dirt ? io.github.xshoutgamer.minecraftmod.CelestialBlocks.BLIGHT_SOIL.get()
                    : io.github.xshoutgamer.minecraftmod.CelestialBlocks.BLIGHT_SAND.get()).defaultBlockState();
        }
        var block = switch (preset) {
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
        if (change == null || !change.owner.equals(player.getUUID()) || !canRestore(player)
                || !level.hasChunkAt(pos) || !level.getBlockState(pos).equals(change.after)) return false;
        if (!replace(player, pos, change.after, change.before)) return false;
        changes.remove(pos);
        setDirty();
        return true;
    }

    void restoreExpired(ServerPlayer player) {
        long now = player.level().getGameTime();
        int normal = HexConfig.EDITS.get();
        int sovereign = Math.min(384, genesisBudget());
        var positions = new ArrayList<BlockPos>();
        for (var entry : changes.entrySet()) {
            var change = entry.getValue();
            var pos = entry.getKey();
            if (change.expires == 0 || change.expires > now || !change.owner.equals(player.getUUID())
                    || (change.event == null ? normal == 0 : sovereign == 0)
                    || !player.serverLevel().hasChunkAt(pos) || !player.level().getBlockState(pos).equals(change.after)) continue;
            positions.add(pos);
            if (change.event == null) normal--;
            else sovereign--;
            if (normal == 0 && sovereign == 0) break;
        }
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
            if (change.event != null) entry.putUUID("Event", change.event);
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
                    NbtUtils.readBlockState(blocks, entry.getCompound("After")), entry.getLong("Expires"), entry.hasUUID("Event") ? entry.getUUID("Event") : null));
        }
        return data;
    }
}
