package io.github.xshoutgamer.minecraftmod.nail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
            float x = (float) Math.cos(angle) * 1.7F, z = (float) Math.sin(angle) * 1.7F;
            int steps = Math.max(4, (int) (length * (0.86 + random.nextDouble() * 0.14) / 1.65));
            for (int step = 0; step < steps; step++) {
                angle = heading + (angle - heading) * 0.87 + (random.nextDouble() - 0.5) * 0.78;
                float nextX = x + (float) Math.cos(angle) * 1.65F;
                float nextZ = z + (float) Math.sin(angle) * 1.65F;
                segments.add(new Segment(x, z, nextX, nextZ, 0.68F + 1.35F * (1F - step / (float) steps), step * 2));
                if (step > 2 && step % 6 == 0) {
                    float bx = nextX, bz = nextZ;
                    double branch = angle + (random.nextBoolean() ? 0.9 : -0.9);
                    for (int part = 0; part < 3; part++) {
                        branch += (random.nextDouble() - 0.5) * 0.65;
                        float ex = bx + (float) Math.cos(branch) * 1.55F;
                        float ez = bz + (float) Math.sin(branch) * 1.55F;
                        segments.add(new Segment(bx, bz, ex, ez, 0.75F - part * 0.18F, step * 2 + 2 + part * 3));
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
        var occupied = new LinkedHashMap<Long, FractureCut>();
        for (Segment segment : segments) {
            for (int sample = 0; sample <= 4; sample++) {
                float t = sample / 4F;
                float cx = segment.x0 + (segment.x1 - segment.x0) * t;
                float cz = segment.z0 + (segment.z1 - segment.z0) * t;
                int brush = Math.min(2, Math.round(segment.width));
                for (int ox = -brush; ox <= brush; ox++) {
                    for (int oz = -brush; oz <= brush; oz++) {
                        int distance = ox * ox + oz * oz;
                        if (distance > brush * brush + 1) continue;
                        int x = Math.round(cx) + ox, z = Math.round(cz) + oz;
                        double radius = Math.hypot(x, z);
                        if (radius < craterRadius - 0.8) continue;
                        int depth = Math.clamp(Math.round(3 + 4.1F * segment.width - 1.4F * distance), 3, 12);
                        int delay = Math.max(0, segment.delay - 12) + sample;
                        long key = ((long) x << 32) ^ (z & 0xffffffffL);
                        var previous = occupied.get(key);
                        if (previous == null || previous.depth < depth) {
                            occupied.put(key, new FractureCut(x, z, depth, previous == null ? delay : Math.min(delay, previous.delay)));
                        }
                    }
                }
            }
        }
        var cuts = new ArrayList<>(occupied.values());
        cuts.sort(Comparator.comparingInt(FractureCut::delay));
        return List.copyOf(cuts);
    }

    public static List<Column> crater(long seed, int radius, int depth, int burial, int bodyWidth) {
        Random random = new Random(seed ^ 0x2F891E2F);
        double phase = random.nextDouble() * Math.PI * 2;
        List<Column> columns = new ArrayList<>();
        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                double dx = x + 0.5, dz = z + 0.5;
                double distance = Math.hypot(dx, dz);
                if (distance > radius) continue;
                double a = Math.atan2(dz, dx);
                double ridge = 0.57 * Math.sin(5 * a + phase) + 0.31 * Math.cos(8 * a - phase)
                        + 0.12 * Math.sin(dx * 1.15 + dz * .64);
                double width = radius - (0.55 + 0.7 * ridge);
                double u = distance / width;
                double shaft = depth * Math.pow(Math.max(0, 1 - u * u * u * u), 0.66);
                double shelves = Math.rint(shaft / 6) * 6;
                double cut = shaft * 0.64 + shelves * 0.36 + ridge * 3.0 + random.nextDouble() * 1.2;
                double baseRadius = Math.max(2.0, bodyWidth * 0.32);
                double pillar = Math.clamp((distance - baseRadius) / 2.7, 0, 1);
                if (pillar < 1) cut = Math.min(cut, Math.max(0, burial) + (depth - burial) * pillar * pillar);
                if (distance < baseRadius) cut = Math.min(cut, Math.max(0, burial));
                if (distance > baseRadius + 2.7 && distance < radius * .56 && ridge > -.50) cut = depth;
                // A shallow outer bench makes a jagged lip without expanding the bowl.
                if (u > 1) cut = 1 + (ridge > 0.22 ? 2 : 0);
                int rounded = Math.clamp((int) Math.round(cut), 1, depth);
                columns.add(new Column(x, z, rounded));
            }
        }
        columns.sort(Comparator.comparingInt(c -> c.x * c.x + c.z * c.z));
        return List.copyOf(columns);
    }

    private FracturePattern() {}
}
