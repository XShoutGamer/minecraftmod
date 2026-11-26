package io.github.xshoutgamer.minecraftmod.hex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class HeavenfallTerrain {
    private record Column(BlockPos pos, double distance, double noise, int depth, boolean inBeam) {}

    private final UUID event;
    private final HeavenfallConfig.Scale scale;
    private final ArrayDeque<Column> columns = new ArrayDeque<>();
    private final int y;
    private final boolean excavation;
    private final int beamTop;
    private int elevation, lastElevation;
    private float editedRadius;
    private boolean centerExcavated;
    private Column current;

    HeavenfallTerrain(BlockPos center, HeavenfallConfig.Scale scale, UUID event, boolean excavation) {
        this(center, scale, event, excavation, null);
    }

    HeavenfallTerrain(BlockPos center, HeavenfallConfig.Scale scale, UUID event, boolean excavation, Vec3 sky) {
        this.scale = scale;
        this.event = event;
        this.excavation = excavation;
        y = center.getY();
        beamTop = sky == null ? y : BlockPos.containing(sky).getY();
        int radius = excavation ? scale.crater() : scale.corruption();
        if (radius <= 0) return;
        int outer = (int) Math.ceil(Math.max(radius * 1.15, sky == null ? 0 : scale.beam() + 1));
        var pending = new ArrayList<Column>();
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double distance = Math.hypot(x, z);
                double angle = Math.atan2(z, x);
                double seed = (event.getLeastSignificantBits() & 255) * .04;
                double noise = .073 * Math.sin(angle * 5 + seed) + .056 * Math.cos(angle * 9 - seed)
                        + .014 * Math.sin(x * .91 + z * .71 + seed);
                double fraction = distance / (radius * (1 + noise));
                var pos = center.offset(x, 0, z);
                boolean inBeam = excavation && sky != null
                        && Math.hypot(pos.getX() + .5 - sky.x, pos.getZ() + .5 - sky.z) <= scale.beam();
                if (fraction > 1 && !inBeam) continue;
                double bowl = Math.pow(Math.max(0, 1 - fraction * fraction), 1.31);
                int depth = fraction > 1 ? 0 : Math.max(1, (int) Math.round(scale.depth() * bowl));
                pending.add(new Column(pos, distance, noise, depth, inBeam));
            }
        }
        pending.sort(Comparator.comparing(Column::inBeam).reversed().thenComparingDouble(Column::distance));
        columns.addAll(pending);
    }

    boolean centerExcavated() { return centerExcavated; }

    float editedRadius() { return editedRadius; }

    boolean finished() { return current == null && columns.isEmpty(); }

    int tick(ServerPlayer player, int budget, float progress) {
        var level = player.serverLevel();
        var journal = HexTerrain.get(level);
        double front = Math.max(2, (excavation ? scale.crater() : scale.corruption()) * 1.15 * progress);
        int edited = 0;
        for (int scans = 0; scans < budget * 12 && edited < budget && !journal.full(); scans++) {
            if (current == null) {
                var next = columns.peekFirst();
                if (next == null || (!next.inBeam() && next.distance() > front)) break;
                current = columns.removeFirst();
                int top = excavation ? Math.min(18, scale.depth() / 2) : Math.min(36, scale.corruption() / 2);
                int bottom = excavation ? current.depth() - 1 : Math.min(42, scale.corruption());
                elevation = Math.min(current.inBeam() ? Math.max(y + top, beamTop) : y + top, level.getMaxBuildHeight() - 1);
                lastElevation = Math.max(y - bottom, level.getMinBuildHeight());
                if (elevation < lastElevation || !level.hasChunkAt(current.pos())) {
                    current = null;
                    continue;
                }
            }
            var pos = current.pos().atY(elevation--);
            boolean covered = excavation || covered(pos);
            if (covered && journal.heavenfall(player, pos, event, excavation) > 0) {
                edited++;
                if (excavation && current.distance() == 0 && pos.getY() <= y) centerExcavated = true;
                editedRadius = Math.max(editedRadius, (float) current.distance());
            }
            if (elevation < lastElevation) current = null;
        }
        return edited;
    }

    private boolean covered(BlockPos pos) {
        double range = Math.hypot(current.distance(), (pos.getY() - y) * .85);
        double fraction = range / Math.max(1, scale.corruption() * (1 + current.noise()));
        if (fraction >= 1) return false;
        if (fraction < .70) return true;
        int hash = Math.floorMod(Long.hashCode(pos.asLong() * 0x9e3779b97f4a7c15L ^ event.getMostSignificantBits()), 100);
        return hash < 100 * (1 - fraction) / .30;
    }
}
