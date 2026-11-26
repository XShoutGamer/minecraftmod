package io.github.xshoutgamer.minecraftmod.client;

import net.minecraft.util.Mth;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.nail.ImpactShockwave;
import static io.github.xshoutgamer.minecraftmod.client.NailMesh.*;

final class WeatherMeshes {
    private static final double TAU = Math.PI * 2;

    private WeatherMeshes() {}

    static void seal(NailMesh mesh, float y, float age, float time, boolean materializing, float squeeze, float charge, int quality) {
        float visibility = materializing ? Mth.clamp(age / 18, 0, 1) : 1;
        float scale = visibility * (1 - 0.60F * squeeze) * CelestiaConfig.SEAL_DIAMETER.get() / 92F;
        int sides = quality == 0 ? 44 : quality > 2 ? 144 : 96;
        mesh.ring(y, 46 * scale, 0.21F, time * 0.007, sides, 1, GOLD, 0.61F * visibility);
        mesh.ring(y - 0.06F, 44 * scale, 0.49F, -time * 0.009, sides, 6, WHITE, 0.40F * visibility);
        mesh.ring(y - 0.13F, 38 * scale, 0.21F, time * 0.017, sides, 4, CYAN, 0.66F * visibility);
        mesh.ring(y - 0.23F, 29 * scale, 0.13F, -time * 0.024, sides, 1, GOLD, 0.50F * visibility);
        mesh.glyphs(y - 0.11F, 42 * scale, time * 0.007, 28, GOLD, visibility * 0.81F);
        mesh.glyphs(y - 0.24F, 34 * scale, -time * 0.024, 20, CYAN, visibility * 0.65F);
        float beam = CelestiaConfig.BEAM_HEIGHT.get();
        mesh.beam(-beam * 0.61F, beam, 2.7F + squeeze * 2.2F, 0.7F, time * 0.02, CYAN,
                visibility * (0.15F + charge * 0.28F));
        mesh.ring(y + 31, 56 * scale, 0.46F, time * 0.009, sides, 5, BLUE, visibility * 0.27F);
        mesh.ring(y - 47, 41 * scale, 0.38F, -time * 0.014, sides, 7, GOLD, visibility * 0.25F);
        if (quality >= 2 && visibility > 0.05F) {
            for (int layer = 0; layer < 3; layer++) {
                float drift = 0.91F + 0.07F * Mth.sin(time * 0.018F + layer);
                mesh.section(0, y + 6.5F * layer, 0, 4.5F, (43 + layer * 5) * scale * drift,
                        (49 + layer * 6) * scale, 32, time * 0.014 * (layer + 1), BLUE, CYAN,
                        visibility * (0.038F + squeeze * 0.023F));
            }
        }
        int streams = quality == 0 ? 6 : 11 + quality * 7;
        float reach = 70 + 45 * charge;
        for (int i = 0; i < streams; i++) {
            double spin = TAU * i / streams + time * 0.013;
            Point from = polar(0, y + 20, 0, reach * scale, spin);
            for (int step = 1; step <= 12; step++) {
                float t = step / 12F;
                Point to = polar(0, y + 20 * (1 - t) + Mth.sin(t * Mth.PI) * 14, 0,
                        reach * (1 - t) * scale, spin + t * t * 1.7);
                stripe(mesh, from, to, (0.13F + t * t * 0.33F) * visibility, i % 3 == 0 ? GOLD : CYAN,
                        visibility * (0.20F + squeeze * 0.26F) * (0.67F + 0.33F * Mth.sin(time * 0.27F - t * 10 + i)));
                from = to;
            }
            if (quality > 0) {
                float drift = (time * 0.027F + i * 0.37F) % 1;
                Point spark = polar(0, y + 17 * (1 - drift), 0, reach * (1 - drift) * scale, spin + drift * drift * 1.7);
                mesh.diamond(spark.x(), spark.y(), spark.z(), 0.34F, 1.15F, spin, i % 3 == 0 ? GOLD : WHITE, visibility * 0.62F);
            }
        }
    }

    static void impact(NailMesh mesh, float y, float age, float time, int crater, int quality) {
        float radius = Math.max(crater * 3F, CelestiaConfig.BLAST_RADIUS.get());
        int sides = quality >= 6 ? 242 : quality == 0 ? 44 : quality == 1 ? 64 : quality <= 3 ? 112 : 152;
        if (age > 0 && age < 24) {
            float front = ImpactShockwave.reach(age);
            float fade = 1 - Mth.clamp((age - 14) / 10, 0, 1);
            mesh.ring(y + 0.3F, front, 1.25F, 0, sides, 1, CYAN, fade * 0.48F);
            mesh.ring(y + 0.4F, front, 0.24F, 0, sides, 1, WHITE, fade * 0.65F);
        }
        if (age < 48) {
            float much = Mth.clamp((age + 2.1F) / 37, 0, 1);
            float spread = radius * (1 - (1 - much) * (1 - much));
            float fade = (1 - age / 48);
            float flash = CelestiaConfig.REDUCED_FLASH.get() ? 0 : CelestiaConfig.FLASH.get().floatValue();
            hemisphere(mesh, y - 0.9F, spread, sides, quality >= 2 ? 15 : 7, CYAN, fade * 0.083F);
            hemisphere(mesh, y, spread * 0.78F, sides, quality >= 2 ? 11 : 5, WHITE, fade * (0.034F + flash * 0.09F));
            if (age < 12 && flash > 0) {
                hemisphere(mesh, y + 5, 12 + age * 8F, sides, 9, WHITE, flash * (1 - age / 12) * 0.27F);
            }
            star(mesh, y + 5, radius, time, fade * (0.47F + flash * 0.23F), quality, false);
        }

        for (int wave = 0; wave < 8; wave++) {
            float t = (age - wave * (wave < 3 ? 4.5F : 8.5F)) / (32 + 5F * wave);
            if (t < 0 || t >= 1) continue;
            float distance = radius * (0.05F + (1 - (1 - t) * (1 - t)) * (0.67F + wave * 0.035F));
            float alpha = (1 - t) * (wave == 0 ? 0.76F : 0.56F);
            int color = wave % 2 == 0 ? CYAN : GOLD;
            mesh.ring(y + wave * 0.45F, distance, 0.48F + t, wave * 0.72, sides, wave == 0 ? 1 : 7, color, alpha);
            mesh.ring(y + 0.15F + wave * 0.45F, distance - 0.6F, 0.23F, 0, sides, 1, WHITE, alpha * 0.37F);
            if (quality > 0) mesh.section(0, y - 1, 0, 2 + (1 - t) * 5, distance, distance - 0.9F,
                    sides, 0, color, WHITE, alpha * 0.10F);
        }

        if (quality > 0) atmosphere(mesh, y, radius, age, time, quality);
        if (quality == 0 || age > 126) return;
        int rays = quality >= 3 ? 54 : quality == 2 ? 32 : 16;
        for (int i = 0; i < rays; i++) {
            float progress = (age - i % 12) / 105;
            if (progress < 0 || progress >= 1) continue;
            double angle = i * 2.399963;
            float drift = radius * progress * (0.48F + i % 6 * 0.082F);
            float height = 3.1F + Mth.sin(progress * Mth.PI) * (5 + (i % 8) * 5.2F);
            var from = polar(0, y + height * 0.45F, 0, drift, angle);
            var to = polar(0, y + height, 0, drift + 4 + progress * 15, angle + Mth.sin(time * 0.04F + i) * 0.07);
            stripe(mesh, from, to, (1 - progress) * 0.36F, i % 3 == 0 ? GOLD : CYAN, (1 - progress) * 0.39F);
            if (quality >= 2) stripe(mesh, from, to, (1 - progress) * 1.7F, CYAN, (1 - progress) * 0.045F);
        }
    }

    static void star(NailMesh mesh, float y, float reach, float time, float alpha, int quality, boolean skyward) {
        int rays = quality >= 6 ? 92 : quality == 0 ? 15 : quality <= 2 ? 30 : 45;
        Point origin = new Point(0, y, 0);
        mesh.diamond(0, y - 2.4F, 0, 2.6F, 5.3F, time * 0.09, WHITE, alpha * 0.89F);
        mesh.diamond(0, y - 6.5F, 0, 4.5F, 13.8F, -time * 0.04, BLUE, alpha * 0.32F);
        for (int ray = 0; ray < rays; ray++) {
            double angle = ray * 2.399963 + time * 0.008;
            float height = (float) Math.sin(ray * 3.814 + 0.5);
            if (!skyward) height = Math.abs(height) * 0.82F + 0.12F;
            float length = reach * (0.48F + (ray % 7) * 0.078F);
            float power = alpha * (ray % 6 == 0 ? 0.54F : 0.23F);
            Point end = polar(0, y + height * length * 0.59F, 0, length, angle);
            shaft(mesh, origin, end, 0.46F + ray % 3 * 0.31F, ray % 4 == 0 ? GOLD : WHITE, power);
            if (quality > 1) shaft(mesh, origin, end, 4.8F + ray % 4 * 0.86F, CYAN, power * 0.17F);
        }
    }

    private static void shaft(NailMesh mesh, Point start, Point end, float width, int color, float strength) {
        float dx = end.x() - start.x(), dz = end.z() - start.z();
        float flatLength = (float) Math.hypot(dx, dz);
        if (flatLength < 0.01F) return;
        float nx = -dz / flatLength, nz = dx / flatLength;
        for (int band = 0; band < 7; band++) {
            float a = band / 7F, b = (band + 1) / 7F;
            float wa = width * (1 + a * 1.9F), wb = width * (1 + b * 1.9F);
            Point from = between(start, end, a), to = between(start, end, b);
            float glow = strength * (1 - a) * (1 - a);
            mesh.softQuad(new Point(from.x() + nx * wa, from.y(), from.z() + nz * wa),
                    new Point(from.x() - nx * wa, from.y(), from.z() - nz * wa),
                    new Point(to.x() - nx * wb, to.y(), to.z() - nz * wb),
                    new Point(to.x() + nx * wb, to.y(), to.z() + nz * wb), color, glow);
            mesh.softQuad(new Point(from.x(), from.y() + wa, from.z()), new Point(from.x(), from.y() - wa, from.z()),
                    new Point(to.x(), to.y() - wb, to.z()), new Point(to.x(), to.y() + wb, to.z()), color, glow * 0.73F);
        }
    }

    private static Point between(Point a, Point b, float t) {
        return new Point(Mth.lerp(t, a.x(), b.x()), Mth.lerp(t, a.y(), b.y()), Mth.lerp(t, a.z(), b.z()));
    }

    private static void atmosphere(NailMesh mesh, float y, float radius, float age, float time, int quality) {
        float fade = (float) Math.exp(-age / 225F);
        float spread = radius * Math.min(1, 0.1F + age / 180F);
        if (fade < 0.019F) return;
        int strands = quality <= 1 ? 4 : quality <= 3 ? 8 : 15;
        for (int layer = 0; layer < strands; layer++) {
            double angle = layer * 2.399963 + time * 0.005;
            float r = spread * (0.22F + 0.056F * layer);
            float altitude = y + 8 + layer * 4;
            Point from = polar(0, altitude, 0, r, angle);
            for (int step = 1; step <= 22; step++) {
                double shift = step * 0.09;
                float drift = (float) Math.sin(shift * 4.2 + time * 0.027 + layer);
                Point to = polar(0, altitude + drift * 13, 0, r + step * spread * 0.014F, angle + shift);
                stripe(mesh, from, to, (6.4F + 4.1F * drift) * fade, layer % 4 == 0 ? GOLD : BLUE,
                        fade * 0.07F);
                stripe(mesh, from, to, 0.68F, layer % 3 == 0 ? GOLD : CYAN, fade * 0.16F);
                from = to;
            }
            if (age < 108) {
                float pulse = Math.max(0, 1 - Math.abs(age - (17 + 4 * layer)) / 18);
                mesh.section(from.x(), y, from.z(), 28 + layer * 6, 0.37F, 0.08F,
                        5, angle, GOLD, CYAN, 0.55F * pulse);
            }
        }
    }

    private static void hemisphere(NailMesh mesh, float y, float radius, int sides, int bands, int color, float alpha) {
        for (int band = 0; band < bands; band++) {
            double low = band * Math.PI / (2 * bands), high = (band + 1) * Math.PI / (2 * bands);
            float ry = y + radius * (float) Math.sin(low), sy = y + radius * (float) Math.sin(high);
            float rr = radius * (float) Math.cos(low), sr = radius * (float) Math.cos(high);
            for (int i = 0; i < sides; i++) {
                double a = TAU * i / sides, b = TAU * (i + 1) / sides;
                mesh.quad(polar(0, ry, 0, rr, a), polar(0, ry, 0, rr, b),
                        polar(0, sy, 0, sr, b), polar(0, sy, 0, sr, a), color,
                        alpha * (0.6F + 0.4F * Mth.sin(i * 0.57F + band * 0.42F)));
            }
        }
    }

    private static void stripe(NailMesh mesh, Point a, Point b, float width, int color, float alpha) {
        float dx = b.x() - a.x(), dz = b.z() - a.z();
        float flat = (float) Math.hypot(dx, dz);
        if (flat < 0.001F) return;
        float x = -dz / flat * width, z = dx / flat * width;
        mesh.softQuad(new Point(a.x() + x, a.y(), a.z() + z), new Point(a.x() - x, a.y(), a.z() - z),
                new Point(b.x() - x * 0.4F, b.y(), b.z() - z * 0.4F),
                new Point(b.x() + x * 0.4F, b.y(), b.z() + z * 0.4F), color, alpha);
    }
}
