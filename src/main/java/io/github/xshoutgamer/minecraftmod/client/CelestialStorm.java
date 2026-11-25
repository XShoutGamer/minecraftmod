package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class CelestialStorm {
    private enum Type {
        DIVINE(0xc2eaff, 0x499cff, 1.20F, 170),
        GOLDEN(0xffe8af, 0xf9aa28, .39F, 165),
        CRIMSON(0xff7868, 0xd52b4e, 1.65F, 39),
        RESIDUAL(0x9aeaff, 0x397bff, .23F, 86);

        final int core, glow;
        final float width;
        final int lifetime;

        Type(int core, int glow, float width, int lifetime) {
            this.core = core;
            this.glow = glow;
            this.width = width;
            this.lifetime = lifetime;
        }
    }

    private record Branch(Vec3[] points, Type type, int delay, int detail, int group, float width) {}
    private record Visible(Vec3[] points, Type type, float width, float gain, float growth) {}
    private final List<Branch> arcs = new ArrayList<>();

    CelestialStorm(long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < 15; i++) {
            double angle = i * 2.39996 + random.nextDouble() * .6;
            add(random, Type.DIVINE, point(.43, 0, angle), point(1.07, (i % 6 - 1.8) * .20, angle + .28), i, i % 13, 19);
            add(random, Type.GOLDEN, point(.13, -.05, angle), point(.56, -.16 + .064 * (i % 5), angle + .58), i, i % 23, 13);
            add(random, Type.CRIMSON, point(.02, .02, angle), point(.95, .16 + .15 * (i % 7), angle + .25), i, i % 3, 25);
            add(random, Type.RESIDUAL, point(.055, .035, angle), point(.25, .06 + .035 * (i % 4), angle + .36), i, 42 + i * 13, 10);
        }
    }

    private void add(Random random, Type type, Vec3 root, Vec3 tip, int group, int delay, int nodes) {
        Vec3[] primary = jagged(root, tip, nodes, random);
        arcs.add(new Branch(primary, type, delay, 1, group, 1));
        for (int fork = 0; fork < (type == Type.CRIMSON || type == Type.DIVINE ? 4 : 2); fork++) {
            int index = nodes * (fork + 2) / 7;
            Vec3 split = primary[index];
            var outward = tip.subtract(root).scale(.21 + random.nextDouble() * .15);
            var kick = new Vec3(random.nextGaussian() * .09, random.nextGaussian() * .11, random.nextGaussian() * .11);
            var branch = jagged(split, split.add(outward).add(kick), 8, random);
            arcs.add(new Branch(branch, type, delay + 2 + fork, 2, group, .47F));
            if (type == Type.CRIMSON || type == Type.DIVINE) {
                Vec3 join = branch[5];
                Vec3 end = join.add(outward.scale(.37)).add(kick.scale(-.62));
                arcs.add(new Branch(jagged(join, end, 5, random), type, delay + 4 + fork, 3, group, .26F));
            }
        }
    }

    private static Vec3[] jagged(Vec3 root, Vec3 tip, int segments, Random random) {
        Vec3[] result = new Vec3[segments + 1];
        result[0] = root;
        result[segments] = tip;
        Vec3 span = tip.subtract(root);
        double amplitude = span.length() / segments * .56;
        for (int i = 1; i < segments; i++) {
            Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            result[i] = root.add(span.scale(i / (double) segments)).add(offset.scale(amplitude));
        }
        return result;
    }

    private static Vec3 point(double r, double y, double a) {
        return new Vec3(r * Math.cos(a), y, r * Math.sin(a));
    }

    Frame frame(boolean sky, float age, float time, float y, float radius, int quality, Vec3 eye) {
        List<Visible> visible = new ArrayList<>();
        int max = Math.min(15, (int) ((4 + quality * 3) * CelestiaConfig.LIGHTNING.get()));
        int complexity = Math.min(CelestiaConfig.LIGHTNING_DETAIL.get(), Math.max(1, quality));
        for (var arc : arcs) {
            if (arc.group >= max || arc.detail > complexity) continue;
            if (sky != (arc.type == Type.DIVINE || arc.type == Type.GOLDEN)) continue;
            float local = age - arc.delay;
            if (local < 0 || local > arc.type.lifetime) continue;
            if (arc.type == Type.RESIDUAL && age > CelestiaConfig.RESIDUAL_TICKS.get()) continue;
            float growth = Mth.clamp((local + 1) / (arc.type == Type.RESIDUAL ? 10 : 6), 0, 1.05F);
            float tail = sky ? .72F + .21F * Mth.sin(local * .17F + arc.group) : 1 - local / arc.type.lifetime;
            float gain = Mth.clamp(tail, 0, 1) * (.84F + .16F * Mth.sin(time * .63F + arc.group));
            Vec3[] path = new Vec3[arc.points.length];
            for (int i = 0; i < path.length; i++) {
                var p = arc.points[i].scale(radius);
                // Spatially coherent motion keeps every child rooted to its parent.
                double shift = Math.sin(time * .24 + p.x * .065 + p.z * .097);
                path[i] = p.add(shift * .54, y + shift * .95, shift * .48);
            }
            visible.add(new Visible(path, arc.type, arc.width * arc.type.width, gain, growth));
        }
        return new Frame(visible, eye, time);
    }

    record Frame(List<Visible> arcs, Vec3 eye, float time) {
        void glow(NailMesh mesh) {
            for (var arc : arcs) {
                var type = arc.type;
                EnergyPath.render(mesh, arc.points, eye, arc.width * 3.7F, arc.gain * .14F, type.glow, time * .3F, arc.growth, true);
                EnergyPath.render(mesh, arc.points, eye, arc.width * 1.6F, arc.gain * .8F, type.glow, time * .51F, arc.growth, true);
                EnergyPath.render(mesh, arc.points, eye, arc.width * (type == Type.CRIMSON ? .72F : .49F),
                        arc.gain * .92F, type.core, time * .64F, arc.growth, true);
                if (arc.growth > .95F && arc.width > .70F) {
                    var tip = arc.points[arc.points.length - 1];
                    mesh.diamond((float) tip.x, (float) tip.y, (float) tip.z, .47F, 1.45F, time * .12F,
                            type.core, arc.gain * .64F);
                }
            }
        }

        void darkCenters(NailMesh mesh) {
            for (var arc : arcs) {
                if (arc.type != Type.CRIMSON) continue;
                EnergyPath.render(mesh, arc.points, eye, arc.width * .38F, arc.gain * .97F,
                        0x180717, time * .22F, arc.growth, true);
            }
        }
    }
}
