package io.github.xshoutgamer.minecraftmod.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static io.github.xshoutgamer.minecraftmod.client.NailMesh.Point;

/** One joined strip per path. Shared cross-sections keep the joints closed. */
final class EnergyPath {
    private EnergyPath() {}

    static void render(NailMesh mesh, Vec3[] path, Vec3 eye, float width, float intensity, int tint,
                       float clock, float growth, boolean tapered) {
        if (path.length < 2 || growth <= 0 || intensity <= .003F) return;
        boolean closed = path[0].equals(path[path.length - 1]);
        Point left = null, right = null;
        float previous = 0;
        double meters = 0;
        double previousMeters = 0;
        for (int i = 0; i < path.length; i++) {
            float progress = i / (float) (path.length - 1);
            if (i > 0) meters += path[i].distanceTo(path[i - 1]);
            var here = path[i];
            int before = closed && i == 0 ? path.length - 2 : Math.max(0, i - 1);
            int after = closed && i == path.length - 1 ? 1 : Math.min(path.length - 1, i + 1);
            var tangent = path[after].subtract(path[before]);
            Vec3 edge = tangent.cross(eye == null ? new Vec3(0, 1, 0) : eye.subtract(here)).normalize();
            if (edge.lengthSqr() < 1e-6) edge = tangent.cross(new Vec3(1, 0, 0)).normalize();
            float taper = tapered ? 1 - .94F * progress : 1;
            edge = edge.scale(width * taper);
            Point a = point(here.add(edge)), b = point(here.subtract(edge));
            float traveling = .78F + .14F * Mth.sin((float) meters * .14F - clock) + .08F * Mth.sin((float) meters * .39F + clock * .55F);
            float gain = intensity * (closed ? Mth.lerp((float) Math.pow(Math.abs(2 * progress - 1), 8),
                    traveling, .8F) : traveling) * Mth.clamp((growth - progress) * (path.length - 1) + .4F, 0, 1);
            if (left != null) mesh.joinedQuad(left, right, b, a, tint, previous, gain, (float) previousMeters * .0625F, (float) meters * .0625F);
            if (progress > growth) break;
            left = a;
            right = b;
            previous = gain;
            previousMeters = meters;
        }
    }

    static Vec3[] spline(Vec3[] knots, int divisions) {
        Vec3[] result = new Vec3[(knots.length - 1) * divisions + 1];
        for (int i = 0; i < knots.length - 1; i++) {
            Vec3 a = knots[Math.max(0, i - 1)], b = knots[i], c = knots[i + 1], d = knots[Math.min(knots.length - 1, i + 2)];
            Vec3 m = c.subtract(a).scale(.5), n = d.subtract(b).scale(.5);
            for (int step = 0; step < divisions; step++) {
                double t = step / (double) divisions, t2 = t * t, t3 = t2 * t;
                result[i * divisions + step] = b.scale(2*t3 - 3*t2 + 1).add(c.scale(-2*t3 + 3*t2))
                        .add(m.scale(t3 - 2*t2 + t)).add(n.scale(t3 - t2));
            }
        }
        result[result.length - 1] = knots[knots.length - 1];
        return result;
    }

    private static Point point(Vec3 v) { return new Point((float) v.x, (float) v.y, (float) v.z); }
}
