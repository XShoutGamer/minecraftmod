package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

final class NailMesh {
    record Point(float x, float y, float z) {}
    static final int IVORY = 0xe6eef4, SILVER = 0x91acc7, GOLD = 0xffc35d;
    static final int CYAN = 0x57f5ff, BLUE = 0x388dff, WHITE = 0xd7fbff;
    private static final double TAU = Math.PI * 2;

    private final PoseStack.Pose pose;
    private final VertexConsumer consumer;
    private final boolean additive;
    private final float opacity;
    private final java.util.function.ToDoubleFunction<Point> visibility;

    NailMesh(PoseStack poses, VertexConsumer consumer, boolean additive, float opacity) {
        this(poses, consumer, additive, opacity, null);
    }

    NailMesh(PoseStack poses, VertexConsumer consumer, boolean additive, float opacity,
             java.util.function.ToDoubleFunction<Point> visibility) {
        this.pose = poses.last();
        this.consumer = consumer;
        this.additive = additive;
        this.opacity = opacity;
        this.visibility = visibility;
    }

    void quad(Point a, Point b, Point c, Point d, int color, float opacity) {
        quad(a, b, c, d, color, opacity, opacity, -1);
    }

    private void quad(Point a, Point b, Point c, Point d, int color, float start, float end, int tile) {
        if (Math.max(start, end) * opacity < 0.003F) return;
        float u = tile < 0 ? 0 : (tile % 2) * .5F + 2 / 512F;
        float v = tile < 0 ? 0 : (tile / 2) * .5F + 2 / 512F;
        float size = tile < 0 ? 1 : .5F - 4 / 512F;
        float shade = 1;
        if (tile >= 0) {
            float dx = b.x - a.x, dz = b.z - a.z;
            float len = Mth.sqrt(dx * dx + dz * dz);
            if (len > .001F) shade = Math.min(1, .86F + .14F * (dx - dz * .6F) / len);
        }
        vertex(a, color, start, shade, u, v + size);
        vertex(b, color, end, shade, u + size, v + size);
        vertex(c, color, end, shade, u + size, v);
        vertex(d, color, start, shade, u, v);
    }

    private void vertex(Point p, int color, float intensity, float shade, float u, float v) {
        float mask = visibility == null ? 1 : (float) visibility.applyAsDouble(p);
        float gain = Mth.clamp(intensity * opacity * mask, 0, 1);
        float rgb = additive ? gain : shade;
        consumer.addVertex(pose.pose(), p.x, p.y, p.z)
                .setColor(((color >> 16) & 255) / 255F * rgb, ((color >> 8) & 255) / 255F * rgb,
                        (color & 255) / 255F * rgb, additive ? 1 : gain).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    // The narrow hot core rolls off into a broad, transparent penumbra.
    void softQuad(Point a, Point b, Point c, Point d, int color, float alpha) {
        Point leftCore = between(a, b, .36F), rightCore = between(a, b, .64F);
        Point farLeft = between(d, c, .36F), farRight = between(d, c, .64F);
        quad(a, leftCore, farLeft, d, color, 0, alpha, -1);
        quad(leftCore, rightCore, farRight, farLeft, color, alpha, alpha, -1);
        quad(rightCore, b, c, farRight, color, alpha, 0, -1);
    }

    void joinedQuad(Point a, Point b, Point c, Point d, int color, float near, float far, float v0, float v1) {
        if (Math.max(near, far) * opacity < .003F) return;
        Point a1 = between(a, b, .35F), b1 = between(a, b, .65F);
        Point d1 = between(d, c, .35F), c1 = between(d, c, .65F);
        joinedBand(a, a1, d1, d, color, 0, near, far, 0, v0, v1, 0, .35F);
        joinedBand(a1, b1, c1, d1, color, near, near, far, far, v0, v1, .35F, .65F);
        joinedBand(b1, b, c, c1, color, near, 0, 0, far, v0, v1, .65F, 1);
    }

    private void joinedBand(Point a, Point b, Point c, Point d, int color, float aa, float ab, float ac, float ad,
                            float v0, float v1, float u0, float u1) {
        vertex(a, color, aa, 1, u0, v0);
        vertex(b, color, ab, 1, u1, v0);
        vertex(c, color, ac, 1, u1, v1);
        vertex(d, color, ad, 1, u0, v1);
    }

    private static Point between(Point a, Point b, float t) {
        return new Point(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
    }

    void section(float x, float y, float z, float height, float bottom, float top, int sides,
                 double turn, int colorA, int colorB, float alpha) {
        section(x, y, z, height, bottom, top, sides, turn, colorA, colorB, alpha, additive ? -1 : 0);
    }

    void section(float x, float y, float z, float height, float bottom, float top, int sides,
                 double turn, int colorA, int colorB, float alpha, int tile) {
        for (int i = 0; i < sides; i++) {
            double a = turn + TAU * i / sides, b = turn + TAU * (i + 1) / sides;
            quad(polar(x, y, z, bottom, a), polar(x, y, z, bottom, b),
                    polar(x, y + height, z, top, b), polar(x, y + height, z, top, a),
                    i % 3 == 1 ? colorB : colorA, alpha, alpha, tile);
        }
    }

    void beam(float y, float height, float base, float tip, double turn, int color, float alpha) {
        for (int plane = 0; plane < 3; plane++) {
            double angle = turn + Math.PI * plane / 3;
            softQuad(polar(0, y, 0, base, angle), polar(0, y, 0, base, angle + Math.PI),
                    polar(0, y + height, 0, tip, angle + Math.PI), polar(0, y + height, 0, tip, angle),
                    color, alpha * .65F);
        }
    }

    void ring(float y, float radius, float width, double turn, int sides, int every, int color, float alpha) {
        if (radius <= 0) return;
        for (int i = 0; i < sides; i++) {
            if (i % every == every - 1 && every > 1) continue;
            double a = turn + TAU * i / sides, b = turn + TAU * (i + 1) / sides;
            softQuad(polar(0, y, 0, radius, a), polar(0, y, 0, radius + width, a),
                    polar(0, y, 0, radius + width, b), polar(0, y, 0, radius, b), color, alpha);
        }
    }

    void glyphs(float y, float radius, double turn, int count, int color, float alpha) {
        for (int i = 0; i < count; i++) {
            double angle = turn + TAU * i / count;
            float step = 0.028F;
            quad(polar(0, y, 0, radius - 0.34F, angle), polar(0, y, 0, radius, angle - step),
                    polar(0, y, 0, radius + 0.45F, angle), polar(0, y, 0, radius, angle + step), color, alpha);
        }
    }

    void diamond(float x, float y, float z, float width, float height, double turn, int color, float alpha) {
        section(x, y, z, height * 0.55F, 0, width, 4, turn, color, BLUE, alpha);
        section(x, y + height * 0.55F, z, height * 0.45F, width, 0, 4, turn, color, WHITE, alpha);
    }

    static Point polar(float x, float y, float z, float radius, double angle) {
        return new Point(x + (float) (Math.cos(angle) * radius), y, z + (float) (Math.sin(angle) * radius));
    }
}
