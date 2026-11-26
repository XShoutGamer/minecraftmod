package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.hex.Heavenfall.Phase;
import net.minecraft.world.phys.Vec3;

import static io.github.xshoutgamer.minecraftmod.client.HexGeometry.*;

final class HeavenfallGeometry {
    private HeavenfallGeometry() {}

    static void aperture(NailMesh light, NailMesh dark, Vec3 eye, float time, float r, float strength, float gathering, int quality) {
        if (r < .1F) return;
        double spin = time * .021;
        for (int layer = 0; layer < 4; layer++) {
            float radius = r * (.36F + layer * .22F);
            double rotation = spin * (layer % 2 == 0 ? 1 : -.78) + layer;
            light.ring(layer * r * .045F - 2, radius, r * (.013F + layer * .002F), rotation,
                    quality >= 6 ? 120 : 72, layer % 2 + 1, layer == 1 ? ROSE : HOT, .69F * strength);
            glyph(light, layer * r * .045F + .7F, radius, (float) (-rotation * .66), 7 + 3 * layer, .47F * strength, false);
        }
        for (int i = 0; i < 48; i++) {
            var a = polar(0, 0, 0, r * .39, i * TURN / 48);
            var b = polar(0, 0, 0, r * .39, (i + 1) * TURN / 48);
            dark.quad(point(Vec3.ZERO), point(a), point(b), point(Vec3.ZERO), BLACK, strength * .91F);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * TURN / 16 - spin * .84;
            var shard = polar(0, r * (.16 + .10 * Math.sin(time * .064 + i)), 0, r * (1.04 + .07 * Math.sin(i * 9.7)), a);
            float width = r * (.024F + .011F * (i % 3));
            dark.diamond((float) shard.x, (float) shard.y, (float) shard.z, width * 1.9F, width * 4, spin - i, BLOOD, strength * .84F);
            light.diamond((float) shard.x, (float) shard.y, (float) shard.z, width * .75F, width * 2.8F, i + spin * 1.3, ROSE, strength * .76F);
            if (i % 2 == 0) {
                var end = polar(0, -r * .15, 0, r * 1.29, a + .32);
                lightning(light, dark, shard, end, eye, time, r * .045F, strength, i);
            }
        }
        if (gathering > .015F) converge(light, dark, eye, time, r, gathering, quality);
    }

    private static void converge(NailMesh light, NailMesh dark, Vec3 eye, float time, float r, float strength, int quality) {
        int streams = quality >= 6 ? 25 : 13;
        int knots = quality >= 6 ? 44 : 30;
        for (int i = 0; i < streams; i++) {
            double angle = i * 2.399963 + time * .007;
            double length = r * (1.40 + (i % 7) * .22);
            Vec3[] path = new Vec3[knots + 1];
            for (int n = 0; n <= knots; n++) {
                double t = n / (double) knots;
                double wave = Math.sin(t * 16 + i - time * .09) + .26 * Math.cos(t * 33 - time * .14);
                double radius = r * .052 + length * t * t;
                path[n] = polar(0, t * r * (.11 + .31 * Math.cos(i)) + wave * r * .094 * t, 0,
                        radius, angle + t * (2.1 + i % 4 * .27) + wave * .22 * t);
            }
            float width = r * (.025F + .005F * (i % 4));
            EnergyPath.converging(dark, path, eye, width * 1.9F, strength * .36F, BLOOD, -time * .23F, 1.1F);
            EnergyPath.converging(light, path, eye, width * 1.4F, strength * .32F, WINE, -time * .34F, 1.1F);
            EnergyPath.converging(light, path, eye, width * .21F, strength * .87F, HOT, -time * .47F, 1.1F);
            double pulse = time * .026 + i * .713;
            int point = Math.min(knots - 7, (int) ((1 - (pulse - Math.floor(pulse))) * (knots - 6)));
            EnergyPath.render(light, new Vec3[]{path[point], path[point + 2], path[point + 4], path[point + 6]},
                    eye, width * .64F, strength * .95F, ROSE, -time * .5F, 1.1F, true);
        }
    }

    static void column(NailMesh light, NailMesh dark, Vec3 eye, Vec3 sky, float r, float time,
                       float life, Phase phase, int quality) {
        float min = phase == Phase.DESCENT ? 1 - Math.min(1, life * 1.18F)
                : phase == Phase.COLLAPSING ? Math.clamp((life - .37F) / .63F, 0, 1) : 0;
        float gain = phase == Phase.COLLAPSING ? 1 - life : 1;
        if (min >= .998F || gain < .015F) return;
        int knots = quality >= 6 ? 80 : 48;
        int streams = quality >= 6 ? 12 : 7;
        float width = r * (.29F + .71F * gain);
        for (int i = 0; i < streams; i++) {
            var path = new Vec3[knots + 1];
            for (int j = 0; j <= knots; j++) {
                double t = min + (1 - min) * j / knots;
                double wave = Math.sin(t * 32 - time * .21 + i) + .29 * Math.cos(t * 77 + time * .18 + i * 2.1);
                double radius = Math.sin(Math.PI * t) * width * (i == 0 ? .20 : .21 + .079 * i);
                double angle = t * (24 + i % 3 * 6) + i * 2.399963 + time * (i % 2 == 0 ? .095 : -.072);
                path[j] = sky.scale(t).add(Math.cos(angle) * radius + wave * radius * .40, 0, Math.sin(angle) * radius);
            }
            float core = width * (i == 0 ? .51F : .15F + .014F * (i % 5));
            float flow = phase == Phase.COLLAPSING ? time : -time;
            EnergyPath.render(dark, path, eye, core * 2.9F, gain * .48F, i % 3 == 0 ? BLACK : BLOOD, flow * .23F, 1.1F, false);
            EnergyPath.render(light, path, eye, core * 2.2F, gain * .33F, WINE, flow * .38F, 1.1F, false);
            EnergyPath.render(light, path, eye, core, gain * .66F, i == 0 ? 0xffd1bd : HOT, flow * .55F, 1.1F, false);
            EnergyPath.render(light, path, eye, core * .26F, gain * .86F, ROSE, flow * .81F, 1.1F, false);
            if (i % 3 == 1) {
                var start = path[(i * 13) % knots];
                var end = start.add(Math.cos(i * 8 + time * .09) * r * 2.7, sky.y * .08, Math.sin(i * 8 + time * .09) * r * 2.7);
                lightning(light, dark, start, end, eye, time, width * .25F, gain, i);
            }
        }
        for (int i = 0; i < 5; i++) {
            double tick = time * .049 + i * .2;
            double height = phase == Phase.COLLAPSING ? tick - Math.floor(tick) : 1 - (tick - Math.floor(tick));
            if (height > min) light.ring((float) (sky.y * height), r * (1.06F + i * .16F), r * .082F,
                    time * .045 + i, 66, 3, HOT, gain * .41F);
        }
    }

    static void lightning(NailMesh light, NailMesh dark, Vec3 start, Vec3 end, Vec3 eye,
                          float time, float width, float power, int seed) {
        var along = end.subtract(start);
        var across = along.cross(new Vec3(0, 1, 0)).normalize();
        if (across.lengthSqr() < .01) across = new Vec3(1, 0, 0);
        var up = across.cross(along).normalize();
        double alternate = ((long) Math.floor(time / 10) & 1) == 0 ? 0 : 2.67;
        var path = new Vec3[14];
        for (int i = 0; i < path.length; i++) {
            double t = i / (double) (path.length - 1);
            double scale = Math.sin(Math.PI * t) * width * 3.3;
            path[i] = start.lerp(end, t)
                    .add(across.scale(Math.sin(i * 8.03 + seed * 2.4 + alternate) * scale))
                    .add(up.scale(Math.sin(i * 7.11 + seed * .43 - alternate) * scale));
        }
        EnergyPath.render(light, path, eye, width * 1.52F, power * .64F, HOT, time * .22F, 1.1F, false);
        EnergyPath.render(dark, path, eye, width * .57F, power * .94F, BLACK, time * .08F, 1.1F, false);
        EnergyPath.render(light, path, eye, width * .23F, power * .46F, ROSE, time * .27F, 1.1F, false);
        for (int i = 3; i < 12; i += 4) {
            var fork = path[i].add(across.scale(width * 5 * Math.sin(seed + i + alternate))).add(up.scale(width * 2.4));
            EnergyPath.render(light, new Vec3[]{path[i], path[i+1].lerp(fork, .53), fork}, eye, width * .37F,
                    power * .78F, HOT, time * .18F, 1.1F, true);
        }
    }
}
