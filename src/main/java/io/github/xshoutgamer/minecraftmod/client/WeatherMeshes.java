package io.github.xshoutgamer.minecraftmod.client;

import net.minecraft.util.Mth;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import static io.github.xshoutgamer.minecraftmod.client.NailMesh.*;

final class WeatherMeshes {
    private static final double TAU = Math.PI * 2;

    private WeatherMeshes() {}

    static void seal(NailMesh mesh, float y, float age, float time, boolean materializing, float squeeze, int quality) {
        float visibility = materializing ? Mth.clamp(age / 18, 0, 1) : 1;
        float scale = visibility * (1 - 0.56F * squeeze);
        int sides = quality == 0 ? 36 : 96;
        mesh.ring(y, 46 * scale, 0.21F, time * 0.007, sides, 1, GOLD, 0.61F * visibility);
        mesh.ring(y - 0.06F, 44 * scale, 0.49F, -time * 0.009, sides, 6, WHITE, 0.40F * visibility);
        mesh.ring(y - 0.13F, 38 * scale, 0.21F, time * 0.017, sides, 4, CYAN, 0.66F * visibility);
        mesh.ring(y - 0.23F, 29 * scale, 0.13F, -time * 0.024, sides, 1, GOLD, 0.50F * visibility);
        mesh.glyphs(y - 0.11F, 42 * scale, time * 0.007, 28, GOLD, visibility * 0.81F);
        mesh.glyphs(y - 0.24F, 34 * scale, -time * 0.024, 20, CYAN, visibility * 0.65F);
        mesh.section(0, -105, 0, y + 188, 0.7F + squeeze * 1.5F, 0.25F, 8, time * 0.02, CYAN, WHITE,
                visibility * (0.08F + squeeze * 0.16F));
        if (quality >= 2 && visibility > 0.05F) {
            for (int layer = 0; layer < 3; layer++) {
                float drift = 0.91F + 0.07F * Mth.sin(time * 0.018F + layer);
                mesh.section(0, y + 6.5F * layer, 0, 4.5F, (43 + layer * 5) * scale * drift,
                        (49 + layer * 6) * scale, 32, time * 0.014 * (layer + 1), BLUE, CYAN,
                        visibility * (0.038F + squeeze * 0.023F));
            }
        }
        int streams = quality == 0 ? 5 : 9 + quality * 4;
        for (int i = 0; i < streams; i++) {
            double spin = TAU * i / streams + time * 0.013;
            Point from = polar(0, y + 20, 0, 53 * scale, spin);
            for (int step = 1; step <= 12; step++) {
                float t = step / 12F;
                Point to = polar(0, y + 20 * (1 - t) + Mth.sin(t * Mth.PI) * 14, 0,
                        53 * (1 - t) * scale, spin + t * t * 1.7);
                stripe(mesh, from, to, (0.13F + t * t * 0.33F) * visibility, i % 3 == 0 ? GOLD : CYAN,
                        visibility * (0.20F + squeeze * 0.26F) * (0.67F + 0.33F * Mth.sin(time * 0.27F - t * 10 + i)));
                from = to;
            }
            if (quality > 0) {
                float drift = (time * 0.027F + i * 0.37F) % 1;
                Point spark = polar(0, y + 17 * (1 - drift), 0, 52 * (1 - drift) * scale, spin + drift * drift * 1.7);
                mesh.diamond(spark.x(), spark.y(), spark.z(), 0.34F, 1.15F, spin, i % 3 == 0 ? GOLD : WHITE, visibility * 0.62F);
            }
        }
    }

    static void impact(NailMesh mesh, float y, float age, float time, int crater, int quality) {
        float radius = Math.max(68, crater * 2.45F);
        int sides = quality == 0 ? 32 : quality == 1 ? 52 : 84;
        if (age < 32) {
            float much = Mth.clamp((age + 1.15F) / 27, 0, 1);
            float spread = radius * (1 - (1 - much) * (1 - much));
            float fade = (1 - age / 32);
            float flash = CelestiaConfig.FLASH.get().floatValue();
            hemisphere(mesh, y - 0.9F, spread, sides, quality >= 2 ? 13 : 7, CYAN, fade * 0.10F);
            hemisphere(mesh, y, spread * 0.92F, sides, quality >= 2 ? 9 : 5, WHITE, fade * (0.045F + flash * 0.13F));
            if (age < 10 && flash > 0) {
                hemisphere(mesh, y + 4, 6 + age * 4.4F, sides, 9, WHITE, flash * (1 - age / 10) * 0.34F);
            }
        }

        for (int wave = 0; wave < 6; wave++) {
            float t = (age - wave * (wave < 3 ? 4.5F : 8.5F)) / (24 + 3F * wave);
            if (t < 0 || t >= 1) continue;
            float distance = radius * (0.05F + (1 - (1 - t) * (1 - t)) * (0.81F + wave * 0.055F));
            float alpha = (1 - t) * (wave == 0 ? 0.76F : 0.56F);
            int color = wave % 2 == 0 ? CYAN : GOLD;
            mesh.ring(y + wave * 0.45F, distance, 0.48F + t, wave * 0.72, sides, wave == 0 ? 1 : 7, color, alpha);
            mesh.ring(y + 0.15F + wave * 0.45F, distance - 0.6F, 0.23F, 0, sides, 1, WHITE, alpha * 0.37F);
            if (quality > 0) mesh.section(0, y - 1, 0, 2 + (1 - t) * 5, distance, distance - 0.9F,
                    sides, 0, color, WHITE, alpha * 0.10F);
        }

        if (quality == 0 || age > 92) return;
        int rays = quality == 3 ? 38 : quality == 2 ? 23 : 12;
        for (int i = 0; i < rays; i++) {
            float progress = (age - i % 12) / 65;
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
        mesh.quad(new Point(a.x() + x, a.y(), a.z() + z), new Point(a.x() - x, a.y(), a.z() - z),
                new Point(b.x() - x * 0.4F, b.y(), b.z() - z * 0.4F),
                new Point(b.x() + x * 0.4F, b.y(), b.z() + z * 0.4F), color, alpha);
    }
}
