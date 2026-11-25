package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static io.github.xshoutgamer.minecraftmod.client.NailMesh.*;

final class EnergyChoreography {
    private EnergyChoreography() {}

    static void sky(NailMesh mesh, Vec3 eye, float y, float time, float intensity, float charge, int quality) {
        float scale = CelestiaConfig.SEAL_DIAMETER.get() * .5F;
        int density = Math.min(9, (int) ((quality + 2) * CelestiaConfig.RIBBONS.get()));
        for (int i = 0; i < density; i++) {
            Vec3[] knots = new Vec3[13];
            double spin = i * 2.39996 + time * (i % 2 == 0 ? .016 : -.012);
            double tilt = .40 + .73 * Mth.sin(i * 1.9F);
            for (int n = 0; n < knots.length; n++) {
                double t = n / 12.0;
                double a = spin + t * Math.PI * 2;
                double r = scale * (.80 + .2 * Mth.sin(time * .058F + i) + .14 * Math.cos(a * 2 + i));
                knots[n] = new Vec3(Math.cos(a) * r, y + Math.sin(a) * r * tilt, Math.sin(a) * r);
            }
            var shell = EnergyPath.spline(knots, 4);
            EnergyPath.render(mesh, shell, eye, 1.35F + charge * 1.25F, (.24F + charge * .34F) * intensity,
                    i % 3 == 0 ? GOLD : CYAN, time * .21F + i, 1.04F, false);
        }
    }

    static void land(CelestialNail nail, NailMesh mesh, Vec3 eye, float floor, float age, float time, int quality) {
        int duration = CelestiaConfig.RESIDUAL_TICKS.get();
        if (age > duration) return;
        float alive = Mth.clamp(1 - age / duration, 0, 1);
        float range = CelestiaConfig.BLAST_RADIUS.get();
        int density = Math.min(14, (int) ((quality * 2 + 2) * CelestiaConfig.RIBBONS.get()));
        if (age < 118) {
            for (int branch = 0; branch < density; branch++) {
                Vec3[] knots = new Vec3[16];
                double angle = branch * 2.39996 + Math.floorMod(nail.getUUID().hashCode(), 11) * .29;
                for (int n = 0; n < knots.length; n++) {
                    double t = n / 15.0, a = angle + .23 * Math.sin(t * 8 + branch);
                    double r = range * t * (.63 + branch % 5 * .085);
                    float x = (float) (Math.cos(a) * r), z = (float) (Math.sin(a) * r);
                    float y = NailRenderer.floor(nail, x, z, floor) + .74F + (float) Math.sin(t * 14 + branch) * 1.2F;
                    knots[n] = new Vec3(x, y, z);
                }
                var path = EnergyPath.spline(knots, quality == 4 ? 3 : 2);
                float pulse = Mth.clamp((age - branch * .75F) / 33, 0, 1.06F);
                float gain = Mth.clamp(1 - age / 118, 0, 1);
                EnergyPath.render(mesh, path, null, 2.5F, gain * .34F, 0xc2863f, time * .24F, pulse, true);
                EnergyPath.render(mesh, path, null, 1.15F, gain * .84F, branch % 4 == 0 ? WHITE : GOLD,
                        time * .37F, pulse, true);
            }
        }
        for (int i = 0; i < Math.min(6, density); i++) {
            Vec3[] knots = new Vec3[15];
            for (int n = 0; n < knots.length; n++) {
                double t = n / 14.0;
                double a = i * 2.39996 + t * 13 + time * (i % 2 == 0 ? .037 : -.028);
                double radius = (9 + 16 * t + 12 * Math.sin(t * Math.PI)) * (1 + .31 * (i % 3));
                knots[n] = new Vec3(Math.cos(a) * radius, floor + t * (64 + i * 13), Math.sin(a) * radius);
            }
            var helix = EnergyPath.spline(knots, 4);
            EnergyPath.render(mesh, helix, eye, 1.15F, alive * (.28F + .23F * Mth.sin(time * .13F + i)),
                    i % 3 == 0 ? GOLD : CYAN, time * .48F + i, Math.min(1.05F, age / 27), true);
        }
        fallingLights(mesh, floor, time, range, alive, quality);
    }

    private static void fallingLights(NailMesh mesh, float y, float time, float reach, float fade, int quality) {
        int variety = CelestiaConfig.PARTICLE_VARIETY.get();
        float density = CelestiaConfig.PARTICLES.get().floatValue();
        int count = (int) ((quality == 4 ? 180 : 62) * density);
        for (int i = 0; i < count; i++) {
            float age = Mth.frac(time * (.012F + (i % 5) * .0026F) + i * .381966F);
            double angle = i * 2.39996 + time * .003;
            float radius = reach * (.09F + .81F * Mth.frac(i * .618034F));
            var p = polar(0, y + (1 - age) * (40 + i % 37), 0, radius, angle);
            float gain = fade * Mth.sin(age * Mth.PI);
            switch (i % variety) {
                case 0 -> mesh.diamond(p.x(), p.y(), p.z(), .075F, 2.3F, angle, WHITE, gain * .72F);
                case 1 -> mesh.diamond(p.x(), p.y(), p.z(), .24F, .34F, time * .21F + i, GOLD, gain * .68F);
                case 2 -> mesh.section(p.x(), p.y(), p.z(), .84F, .08F, .33F, 5, angle, CYAN, BLUE, gain * .36F);
                default -> mesh.diamond(p.x(), p.y(), p.z(), .43F, 1.05F, time * .08F, 0xf96c5d, gain * .39F);
            }
        }
    }

    static void channel(NailMesh mesh, float time, float glow) {
        for (int side = 0; side < 8; side++) {
            Vec3[] knots = new Vec3[21];
            double a = side * Math.PI / 4;
            for (int i = 0; i < knots.length; i++) {
                float y = 7.8F + i * .65F;
                float radius = (float) io.github.xshoutgamer.minecraftmod.nail.NailProfile.widthAt(y / 24) * 3.9F;
                double angle = a + Math.sin(y * .48 + time * .08 + side) * .058;
                knots[i] = new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            }
            var current = EnergyPath.spline(knots, 2);
            Vec3 eye = new Vec3(Math.cos(a) * 12, 12, Math.sin(a) * 12);
            EnergyPath.render(mesh, current, eye, .21F, glow * .27F, BLUE, time * .18F + side, 1.04F, false);
            EnergyPath.render(mesh, current, eye, .055F, glow * .72F, side % 3 == 0 ? GOLD : CYAN,
                    time * (side % 2 == 0 ? .51F : -.37F), 1.04F, false);
        }
    }
}
