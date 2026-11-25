package io.github.xshoutgamer.minecraftmod.nail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public final class FracturePattern {
    public record Segment(float x0, float z0, float x1, float z1, float width, int delay) {}
    public record Column(int x, int z, int depth) {}
    public record FractureCut(int x, int z, int depth, int delay) {}

    public static List<Segment> cracks(long seed, int length) {
        Random random = new Random(seed);
        List<Segment> segments = new ArrayList<>();
        for (int arm = 0; arm < 9; arm++) {
            double heading = (arm + random.nextDouble() * 0.65) * Math.PI * 2 / 9;
            double angle = heading;
            float x = (float) Math.cos(angle) * 2, z = (float) Math.sin(angle) * 2;
            int steps = Math.max(4, (int) (length * (0.75 + random.nextDouble() * 0.25) / 1.65));
            for (int step = 0; step < steps; step++) {
                angle = heading + (angle - heading) * 0.87 + (random.nextDouble() - 0.5) * 0.78;
                float nextX = x + (float) Math.cos(angle) * 1.65F;
                float nextZ = z + (float) Math.sin(angle) * 1.65F;
                segments.add(new Segment(x, z, nextX, nextZ, 0.36F * (1F - 0.7F * step / steps), step * 2));
                if (step > 2 && step % 6 == 0) {
                    float bx = nextX, bz = nextZ;
                    double branch = angle + (random.nextBoolean() ? 0.9 : -0.9);
                    for (int part = 0; part < 3; part++) {
                        branch += (random.nextDouble() - 0.5) * 0.65;
                        float ex = bx + (float) Math.cos(branch) * 1.55F;
                        float ez = bz + (float) Math.sin(branch) * 1.55F;
                        segments.add(new Segment(bx, bz, ex, ez, 0.18F - part * 0.04F, step * 2 + 2 + part * 3));
                        bx = ex;
                        bz = ez;
                    }
                }
                x = nextX;
                z = nextZ;
            }
        }
        segments.sort(Comparator.comparingInt(Segment::delay));
        return List.copyOf(segments);
    }

    public static List<FractureCut> cuts(List<Segment> segments, int craterRadius) {
        var cuts = new ArrayList<FractureCut>();
        var occupied = new HashSet<Long>();
        for (Segment segment : segments) {
            for (int sample = 0; sample <= 4; sample++) {
                float t = sample / 4F;
                int x = Math.round(segment.x0 + (segment.x1 - segment.x0) * t);
                int z = Math.round(segment.z0 + (segment.z1 - segment.z0) * t);
                if (Math.hypot(x, z) < craterRadius) continue;
                long key = ((long) x << 32) ^ (z & 0xffffffffL);
                if (occupied.add(key)) cuts.add(new FractureCut(x, z, segment.width > 0.24F ? 3 : 2,
                        segment.delay + sample));
            }
        }
        cuts.sort(Comparator.comparingInt(FractureCut::delay));
        return List.copyOf(cuts);
    }

    public static List<Column> crater(long seed, int radius, int depth) {
        Random random = new Random(seed ^ 0x2F891E2F);
        double phase = random.nextDouble() * Math.PI * 2;
        List<Column> columns = new ArrayList<>();
        int bounds = (int) Math.ceil(radius * 1.48);
        for (int x = -bounds; x <= bounds; x++) {
            for (int z = -bounds; z <= bounds; z++) {
                double angle = Math.atan2(z, x);
                double edge = radius * (1 + 0.11 * Math.sin(3 * angle + phase)
                        + 0.07 * Math.cos(7 * angle - phase) + 0.035 * Math.sin(13 * angle + phase));
                double distance = Math.hypot(x, z) / edge;
                double ridges = Math.sin(x * 0.21 + z * 0.13 + phase)
                        * Math.cos(z * 0.27 - x * 0.11 - phase);
                if (distance < 0.94) {
                    double bowl = depth * Math.pow(1 - distance * distance, 1.32);
                    double shelves = 1.5 * ridges * Math.sin(Math.PI * distance);
                    double grain = (random.nextDouble() - 0.5) * 1.7 * Math.min(1, distance * 4);
                    int cut = Math.min(depth, (int) Math.round(bowl + shelves + grain));
                    if (cut > 0) columns.add(new Column(x, z, cut));
                } else if (distance < 1.14 && ridges > -0.12) {
                    int rim = Math.max(1, (int) Math.round(3.4 * Math.max(0, 1 - Math.abs(distance - 1.01) * 7)
                            * (0.68 + 0.38 * ridges)));
                    columns.add(new Column(x, z, -rim));
                } else if (distance > 1.18 && distance < 1.52 && ridges > 0.79 && radius >= 25) {
                    columns.add(new Column(x, z, ridges > 0.94 ? 3 : 2));
                }
            }
        }
        columns.sort(Comparator.comparingInt(c -> c.x * c.x + c.z * c.z));
        return List.copyOf(columns);
    }

    private FracturePattern() {}
}
