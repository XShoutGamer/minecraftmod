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

    NailMesh(PoseStack poses, VertexConsumer consumer, boolean additive, float opacity) {
        this.pose = poses.last();
        this.consumer = consumer;
        this.additive = additive;
        this.opacity = opacity;
    }

    void quad(Point a, Point b, Point c, Point d, int color, float opacity) {
        float gain = Mth.clamp(opacity * this.opacity, 0, 1);
        if (gain < 0.003F) return;
        float rgb = additive ? gain : 1;
        float r = ((color >> 16) & 255) / 255F * rgb;
        float g = ((color >> 8) & 255) / 255F * rgb;
        float bChannel = (color & 255) / 255F * rgb;
        float alpha = additive ? 1 : gain;
        vertex(a, r, g, bChannel, alpha, 0, 0);
        vertex(b, r, g, bChannel, alpha, 1, 0);
        vertex(c, r, g, bChannel, alpha, 1, 1);
        vertex(d, r, g, bChannel, alpha, 0, 1);
    }

    private void vertex(Point p, float r, float g, float b, float alpha, float u, float v) {
        consumer.addVertex(pose.pose(), p.x, p.y, p.z).setColor(r, g, b, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    void section(float x, float y, float z, float height, float bottom, float top, int sides,
                 double turn, int colorA, int colorB, float alpha) {
        for (int i = 0; i < sides; i++) {
            double a = turn + TAU * i / sides, b = turn + TAU * (i + 1) / sides;
            quad(polar(x, y, z, bottom, a), polar(x, y, z, bottom, b),
                    polar(x, y + height, z, top, b), polar(x, y + height, z, top, a),
                    i % 3 == 1 ? colorB : colorA, alpha);
        }
    }

    void ring(float y, float radius, float width, double turn, int sides, int every, int color, float alpha) {
        if (radius <= 0) return;
        for (int i = 0; i < sides; i++) {
            if (i % every == every - 1 && every > 1) continue;
            double a = turn + TAU * i / sides, b = turn + TAU * (i + 1) / sides;
            quad(polar(0, y, 0, radius, a), polar(0, y, 0, radius, b),
                    polar(0, y, 0, radius + width, b), polar(0, y, 0, radius + width, a), color, alpha);
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
