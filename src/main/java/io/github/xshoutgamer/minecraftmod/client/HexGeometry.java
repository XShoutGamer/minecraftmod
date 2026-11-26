package io.github.xshoutgamer.minecraftmod.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import static io.github.xshoutgamer.minecraftmod.client.NailMesh.Point;

final class HexGeometry {
    static final int HOT = 0xff1b36, ROSE = 0xff725c, WINE = 0xb20929, BLOOD = 0x570a1b, BLACK = 0x14040f;
    static final double TURN = Math.PI * 2;

    private HexGeometry() {}

    static Vec3 polar(double x, double y, double z, double r, double angle) {
        return new Vec3(x + Math.cos(angle) * r, y, z + Math.sin(angle) * r);
    }

    static void arc(NailMesh glow, NailMesh dark, Vec3 a, Vec3 b, Vec3 eye, float time, int count, double width, double seed) {
        Vec3 axis = b.subtract(a).normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < .001) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(axis);
        int knots = Math.clamp((int) (a.distanceTo(b) * .56), 5, 32);
        float intensity = Math.min(1, 1.7F / (float) Math.sqrt(Math.max(1, count)));
        if (width > 2) intensity *= .66F;
        for (int strand = 0; strand < count; strand++) {
            Vec3[] path = new Vec3[knots + 1];
            double state = seed + strand * 3.239;
            for (int i = 0; i <= knots; i++) {
                double t = i / (double) knots;
                double envelope = Math.sin(Math.PI * t) * width;
                double angle = state + t * (7 + 9 * Math.abs(Math.sin(state * 2.27))) - time * .086;
                double raw = Math.sin(i * 3.109 + state + Math.floor(time * .57) * 1.71);
                double drift = Math.sin(t * 5.2 + state * .64 - time * .12);
                double x = Math.cos(angle) * .57 + drift * .92 + raw * .22;
                double y = Math.sin(angle + drift * .85) * .69 + raw * .19;
                path[i] = a.lerp(b, t).add(side.scale(x * envelope)).add(up.scale(y * envelope));
            }
            Vec3[] flow = strand == 0 && width >= 4 ? path : EnergyPath.spline(path, 2);
            float thickness = (float) Math.clamp(width * .11 / (1 + strand * .51), .014, .31);
            EnergyPath.render(dark, flow, eye, thickness * 1.8F, .91F, BLACK, time * .08F, 1.05F, false);
            EnergyPath.render(glow, flow, eye, thickness * 2.6F, .30F * intensity, WINE, time * .13F, 1.05F, false);
            EnergyPath.render(glow, flow, eye, thickness * .49F, .68F * intensity, strand % 5 == 0 ? ROSE : HOT, time * .18F, 1.05F, false);
            if (strand == 0 && width > 2 && count > 2) {
                for (int i = 2; i < knots; i += 3) {
                    var attach = path[i];
                    var out = attach.add(side.scale(width * Math.sin(i * .9 + seed)))
                            .add(up.scale(width * Math.cos(i * .7 + time * .23)))
                            .lerp(b, .20);
                    EnergyPath.render(glow, new Vec3[]{attach, attach.lerp(out, .48).add(up.scale(width * .22)), out},
                            eye, thickness * .62F, .51F * intensity, HOT, time * .16F, 1.05F, true);
                }
            }
        }
    }

    static void glyph(NailMesh mesh, float y, float radius, float spin, int points, float alpha, boolean upright) {
        for (int i = 0; i < points; i++) {
            double a = spin + TURN * i / points;
            double b = a + TURN / points * (points >= 5 ? 2 : 1);
            Vec3 p = glyphPoint(a, radius, y, upright), q = glyphPoint(b, radius, y, upright);
            Vec3 mid = glyphPoint(a + .12, radius * 1.16F, y, upright);
            stroke(mesh, p, q, radius * .012, alpha * .7F, upright);
            stroke(mesh, p, mid, radius * .02, alpha, upright);
            stroke(mesh, mid, glyphPoint(a + .22, radius * .9F, y, upright), radius * .02, alpha, upright);
        }
        if (!upright) mesh.ring(y, radius * 1.19F, radius * .012F, -spin * 1.3, 64, 3, HOT, alpha);
    }

    private static Vec3 glyphPoint(double a, float r, float y, boolean upright) {
        return upright ? new Vec3(Math.cos(a) * r, y + Math.sin(a) * r, 0) : polar(0, y, 0, r, a);
    }

    private static void stroke(NailMesh mesh, Vec3 a, Vec3 b, double width, float alpha, boolean upright) {
        var n = b.subtract(a).cross(upright ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0)).normalize().scale(width);
        mesh.softQuad(point(a.add(n)), point(a.subtract(n)), point(b.subtract(n)), point(b.add(n)), HOT, alpha);
    }

    static Point point(Vec3 v) { return new Point((float) v.x, (float) v.y, (float) v.z); }

    static void motes(NailMesh mesh, int count, float radius, float height, float time, float intensity, int mode) {
        for (int i = 0; i < count; i++) {
            float cycle = (float) ((i * .618033 + time * (.008 + (i % 11) * .00031)) % 1);
            double angle = i * 2.399963 + time * (i % 2 == 0 ? .013 : -.017) + cycle * 1.1;
            float distance = radius * (.11F + .89F * (mode == 1 ? 1 - cycle : cycle)) * (.6F + .4F * Mth.sin(i * .84F));
            int form = i % 25;
            float size = .033F + radius * (.0019F + .0011F * (form % 5));
            var p = polar(0, cycle * height, 0, distance, angle);
            float alpha = (float) Math.sin(cycle * Math.PI) * intensity;
            int color = switch (form / 5) {
                case 0 -> HOT;
                case 1 -> ROSE;
                case 2 -> 0xf70633;
                case 3 -> WINE;
                default -> 0xef5364;
            };
            double lean = angle + form * .61;
            Vec3 side = new Vec3(Math.cos(lean), 0, Math.sin(lean)).scale(size);
            if (form % 5 == 0) {
                mesh.diamond((float) p.x, (float) p.y, (float) p.z, size, size * 1.4F, lean, color, alpha * .76F);
                continue;
            }
            Vec3 rise = new Vec3(Math.sin(angle) * size, size * (1 + form % 5), Math.cos(angle) * size);
            Vec3 tail = p.subtract(rise);
            mesh.joinedQuad(point(tail.subtract(side)), point(tail.add(side)), point(p.add(side)), point(p.subtract(side)),
                    color, 0, alpha * .8F, 0, 1);
            mesh.joinedQuad(point(p.subtract(side)), point(p.add(side)), point(p.add(rise.scale(.34)).add(side)),
                    point(p.add(rise.scale(.34)).subtract(side)), color, alpha * .8F, 0, 0, 1);
        }
    }
}
