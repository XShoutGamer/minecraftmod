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

    private static final int CACHED_STATES = 4;
    private record Branch(Vec3[][] states, Type type, int delay, int detail, int group, float width) {}
    private record Visible(Vec3[] points, Type type, float width, float gain, float growth) {}
    private record Animation(int before, int current, float blend, float local, float growth, float gain, double rotation) {}
    private final List<Branch> arcs = new ArrayList<>();

    CelestialStorm(long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < 15; i++) {
            double angle = i * 2.39996 + random.nextDouble() * .6;
            add(random, Type.DIVINE, point(.43, 0, angle), point(1.07, (i % 6 - 1.8) * .20, angle + .28), i, i % 13, 19);
            add(random, Type.GOLDEN, point(.035 + .012 * (i % 3), -.08, angle), point(.56, -.16 + .064 * (i % 5), angle + .58), i, i % 23, 13);
            add(random, Type.CRIMSON, point(.02, .02, angle), point(.95, .16 + .15 * (i % 7), angle + .25), i, i % 3, 25);
            add(random, Type.RESIDUAL, point(.055, .035, angle), point(.25, .06 + .035 * (i % 4), angle + .36), i, 42 + i * 13, 10);
        }
    }

    private void add(Random random, Type type, Vec3 root, Vec3 tip, int group, int delay, int nodes) {
        int forks = type == Type.CRIMSON || type == Type.DIVINE ? 4 : 2;
        Vec3[][] trunks = new Vec3[CACHED_STATES][];
        Vec3[][][] branches = new Vec3[forks][CACHED_STATES][];
        Vec3[][][] twigs = new Vec3[forks][CACHED_STATES][];
        for (int state = 0; state < CACHED_STATES; state++) {
            Vec3 span = tip.subtract(root);
            Vec3 endpoint = type == Type.DIVINE || type == Type.GOLDEN
                    ? tip.add(random.nextGaussian() * .055, random.nextGaussian() * .085, random.nextGaussian() * .055) : tip;
            Vec3[] trunk = jagged(root, endpoint, nodes, random);
            trunks[state] = trunk;
            for (int fork = 0; fork < forks; fork++) {
                Vec3 split = trunk[nodes * (fork + 2) / 7];
                Vec3 outward = span.scale(.21 + random.nextDouble() * .15);
                Vec3 kick = new Vec3(random.nextGaussian() * .09, random.nextGaussian() * .11, random.nextGaussian() * .11);
                Vec3[] branch = jagged(split, split.add(outward).add(kick), 8, random);
                branches[fork][state] = branch;
                if (forks == 4) {
                    Vec3 join = branch[5];
                    twigs[fork][state] = jagged(join, join.add(outward.scale(.37)).add(kick.scale(-.62)), 5, random);
                }
            }
        }
        arcs.add(new Branch(trunks, type, delay, 1, group, 1));
        for (int fork = 0; fork < forks; fork++) {
            arcs.add(new Branch(branches[fork], type, delay + 2 + fork, 2, group, .47F));
            if (forks == 4) arcs.add(new Branch(twigs[fork], type, delay + 4 + fork, 3, group, .26F));
        }
    }

    private static Vec3[] jagged(Vec3 root, Vec3 tip, int segments, Random random) {
        Vec3[] result = new Vec3[segments + 1];
        result[0] = root;
        result[segments] = tip;
        Vec3 span = tip.subtract(root);
        Vec3 horizontal = span.cross(new Vec3(0, 1, 0)).normalize();
        if (horizontal.lengthSqr() < .001) horizontal = new Vec3(1, 0, 0);
        Vec3 vertical = span.cross(horizontal).normalize();
        double amplitude = span.length() * .062;
        double side = 0, height = 0;
        for (int i = 1; i < segments; i++) {
            double t = i / (double) segments;
            side = side * .66 + random.nextGaussian() * .67;
            height = height * .68 + random.nextGaussian() * .67;
            double taper = Math.sin(Math.PI * t);
            result[i] = root.add(span.scale(t)).add(horizontal.scale(side * amplitude * taper))
                    .add(vertical.scale(height * amplitude * taper));
        }
        return result;
    }

    private static Vec3 point(double r, double y, double a) {
        return new Vec3(r * Math.cos(a), y, r * Math.sin(a));
    }

    Frame frame(boolean sky, float age, float time, float y, float radius, int quality, float charge, Vec3 eye) {
        List<Visible> visible = new ArrayList<>();
        float movement = CelestiaConfig.BOLT_MOVEMENT.get().floatValue();
        int states = CelestiaConfig.BOLT_STATES.get();
        float interval = CelestiaConfig.BOLT_INTERVAL.get().floatValue() * 20;
        float speed = CelestiaConfig.BOLT_GROWTH.get().floatValue();
        boolean alternate = CelestiaConfig.ALTERNATE_BOLTS.get();
        boolean secondary = CelestiaConfig.SECONDARY_MOTION.get();
        int residual = CelestiaConfig.BOLT_RESIDUAL.get();
        int base = sky ? 3 + quality + Math.round((2 + quality) * charge) : 4 + quality * 3;
        int max = Math.min(15, (int) (base * CelestiaConfig.LIGHTNING.get()));
        int complexity = Math.min(CelestiaConfig.LIGHTNING_DETAIL.get(), Math.max(1, quality - (sky && charge < .45F ? 1 : 0)));
        for (var arc : arcs) {
            if (arc.group >= max || arc.detail > complexity) continue;
            if (sky != (arc.type == Type.DIVINE || arc.type == Type.GOLDEN)) continue;
            float elapsed = age - arc.delay;
            int life = arc.type == Type.RESIDUAL ? residual : arc.type.lifetime;
            if (elapsed < 0 || !sky && elapsed > life) continue;
            if (arc.type == Type.RESIDUAL && age > CelestiaConfig.RESIDUAL_TICKS.get()) continue;

            Animation animation = sky ? sky(arc, age, time, states, speed, charge)
                    : impact(arc, age, elapsed, life, states, interval, speed, alternate);
            if (animation.gain <= .007F || animation.growth <= 0) continue;
            float thickness = arc.width * arc.type.width * (1 + movement * .14F * Mth.sin(animation.local * .42F + arc.group));
            float branchPulse = secondary && arc.detail > 1 ? .46F + .54F * Mth.sin(time * .64F + arc.delay * 1.7F) * .5F + .27F : 1;
            float brightness = animation.gain * branchPulse * CelestiaConfig.BOLT_GLOW.get().floatValue();
            Vec3[] path = sample(arc, animation, radius, y, time, movement);
            visible.add(new Visible(path, arc.type, thickness, brightness, animation.growth));
            if (sky && quality >= 3 && arc.detail == 1 && animation.local < 4) {
                var ghost = new Animation(animation.before, animation.before, 1, 0, 1, 1, animation.rotation);
                visible.add(new Visible(sample(arc, ghost, radius, y, time, movement), arc.type,
                        thickness * .65F, (.12F - animation.local * .028F) * brightness, 1.04F));
            }
        }
        return new Frame(visible, eye, time);
    }

    private static Animation sky(Branch arc, float age, float time, int states, float speed, float charge) {
        int duration = 23 + (arc.group * 7 % 17);
        int offset = (arc.group * 13 % duration);
        float clock = age + offset;
        int cycle = Mth.floor(clock / duration);
        float local = clock - cycle * duration;
        int current = Math.floorMod(cycle + arc.group, states);
        int previous = Math.floorMod(cycle - 1 + arc.group, states);
        float branchTime = local - (arc.delay - arc.group % (arc.type == Type.DIVINE ? 13 : 23));
        float growth = Mth.clamp((branchTime + .6F) * speed / (arc.detail == 1 ? 7.5F : 5.5F), 0, 1.05F);
        float leader = smooth((branchTime + 1) / 6);
        float decay = 1 - smooth((local - duration * .60F) / (duration * .40F));
        float discharge = .57F + .43F * (float) Math.exp(-Math.pow((local - 9) / 4.5, 2));
        double rotation = time * (arc.type == Type.DIVINE ? .008 : -.014) + .025 * Math.sin(time * .078 + arc.group);
        return new Animation(previous, current, smooth(local / 2.4F), local, growth,
                leader * decay * discharge * (.77F + .23F * charge), rotation);
    }

    private static Animation impact(Branch arc, float age, float elapsed, int life, int states, float interval, float speed, boolean alternate) {
        float shift = arc.group == 0 ? 0 : (arc.group * .618034F) % 1 * interval * .78F;
        float clock = age + shift;
        int step = arc.type == Type.CRIMSON && !alternate ? 0 : Mth.floor(clock / interval);
        int current = Math.floorMod(step, states);
        int previous = step == 0 ? current : Math.floorMod(step - 1, states);
        float blend = smooth((clock - step * interval) / Math.min(2.6F, interval * .24F));
        float growth = Mth.clamp((elapsed + 1) * speed / (arc.type == Type.RESIDUAL ? 10 : 6), 0, 1.05F);
        float gain = (1 - elapsed / life) * (.88F + .12F * Mth.sin(age * .65F + arc.group));
        return new Animation(previous, current, blend, elapsed, growth, gain, 0);
    }

    private static Vec3[] sample(Branch arc, Animation animation, float radius, float y, float time,
                                 float movement) {
        Vec3[] first = arc.states[animation.before], second = arc.states[animation.current];
        Vec3[] path = new Vec3[first.length];
        for (int i = 0; i < path.length; i++) {
            float t = i / (float) (path.length - 1);
            Vec3 p = first[i].lerp(second[i], animation.blend);
            p = arc.states[0][i].lerp(p, Math.min(1F, movement));
            p = p.yRot((float) animation.rotation).scale(radius);
            // The jitter is a spatial field shared by all the connected paths.
            if (movement > 0) {
                double drift = Math.sin(time * .29 + p.x * .071 + p.z * .039) * movement;
                double anchor = Math.min(1, p.horizontalDistance() / 16);
                p = p.add(drift * .49 * anchor, drift * .71 * anchor, drift * .38 * anchor);
            }
            if (arc.type == Type.CRIMSON && arc.detail == 1 && (i == 0 || i == path.length - 1)) {
                p = first[i].scale(radius);
            }
            path[i] = p.add(0, y, 0);
        }
        return path;
    }

    private static float smooth(float t) {
        float x = Mth.clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
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
