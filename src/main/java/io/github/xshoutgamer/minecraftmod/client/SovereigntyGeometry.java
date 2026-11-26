package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.ScarletSovereignty.Phase;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static io.github.xshoutgamer.minecraftmod.client.HexGeometry.*;

final class SovereigntyGeometry {
    private SovereigntyGeometry() {}

    static void presence(NailMesh light, NailMesh dark, Vec3 eye, float time, float strength, double scale,
                         Phase phase, int quality, float yaw, boolean firstPerson) {
        float alpha = phase == Phase.COLLAPSING ? strength : 1;
        if (phase.charging() && strength > .14F) absorb(light, dark, eye, time, strength, scale, quality, yaw, firstPerson);
        if (!firstPerson) {
            var right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            var forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            var head = new Vec3(0, 1.63, 0).add(forward.scale(.25));
            for (int i : new int[]{-1, 1}) {
                var face = head.add(right.scale(i * .12));
                EnergyPath.render(light, new Vec3[]{face.add(right.scale(-.05)), face.add(right.scale(.05))}, eye,
                        .028F, .95F * alpha, ROSE, time * .13F, 2, false);
                var hand = hand(i, yaw);
                arc(light, dark, hand.add(right.scale(i * .2)), hand.add(0, .47, 0), eye, time, 3, .24, i);
            }
            float crown = phase.charging() ? smooth((strength - .78F) / .19F) : alpha;
            if (crown > .01F) crown(light, dark, eye, time, 2.06F, .47F, crown, yaw, 5);
            if (crown > .01F && HexConfig.SOVEREIGN_COSTUME.get()) mantle(light, dark, eye, time, yaw, crown);
        }
        if (phase.reigning() || phase == Phase.COLLAPSING) {
            if (HexConfig.SOVEREIGN_THRONE.get()) {
                crown(light, dark, eye, time, 28 + 6 * alpha, 25 * Math.max(.08F, alpha), alpha, time * .007, 11);
                glyph(light, 32, 19 * Math.max(.08F, alpha), -time * .011F, 11, .52F * alpha, false);
                motes(light, quality >= 6 ? 580 : 220, 28 * alpha, 60, time, .65F * alpha, 0);
            }
            fractures(light, dark, eye, time, (float) (scale * .43), alpha, quality >= 6 ? 18 : 9);
        }
    }

    private static Vec3 hand(int side, float yaw) {
        return new Vec3(Math.cos(yaw) * side * .57, .98, Math.sin(yaw) * side * .57)
                .add(-Math.sin(yaw) * .25, 0, Math.cos(yaw) * .25);
    }

    private static void absorb(NailMesh light, NailMesh dark, Vec3 eye, float time, float strength, double scale,
                               int quality, float yaw, boolean firstPerson) {
        float amount = smooth((strength - .14F) / .6F);
        int streams = (quality >= 6 ? 28 : 16);
        for (int i = 0; i < streams; i++) {
            double seed = i * 2.399963;
            float alpha = smooth((strength - .14F - i * .46F / streams) * 6);
            if (alpha <= 0) continue;
            double radius = 12 + (scale * .82 - 12) * (.12 + .88 * ((i * 13 % streams) / (double) streams));
            var source = polar(0, -8 + (i % 6) * radius * .11, 0, radius, seed + .13 * Math.sin(time * .011 + i));
            var sink = i % 4 == 0 ? new Vec3(0, 1.32, 0) : hand(i % 2 == 0 ? 1 : -1, yaw);
            if (firstPerson) sink = sink.add(0, -1.20, 0);
            var side = new Vec3(-Math.sin(seed), 0, Math.cos(seed));
            int knots = quality >= 6 ? 36 : 24;
            var path = new Vec3[knots + 1];
            for (int j = 0; j <= knots; j++) {
                double t = j / (double) knots;
                double distance = t * t;
                double taper = Math.sin(Math.PI * t) * (radius * .17 + 1);
                double oscillation = 8.2 * t + seed + time * .089;
                path[j] = sink.lerp(source, distance).add(side.scale(taper * (Math.sin(oscillation) * .58 + Math.sin(t * 5 + seed) * .61)))
                        .add(0, taper * (.65 + .61 * Math.cos(oscillation + seed)), 0);
            }
            float width = (float) Math.clamp(radius * .027, .19, 5.2);
            float reach = .40F + smooth(amount * 1.9F) * .63F;
            EnergyPath.converging(dark, path, eye, width * 1.6F, .27F * alpha, BLOOD, -time * .20F, reach);
            EnergyPath.converging(light, path, eye, width * 1.65F, .22F * alpha, WINE, -time * .27F, reach);
            EnergyPath.converging(light, path, eye, width * .23F, .70F * alpha, HOT, -time * .36F, reach);
            int flares = quality >= 6 ? 4 : 2;
            for (int flare = 0; flare < flares; flare++) {
                double cycle = time * (.017 + i % 5 * .0018) + i * .416 + flare / (double) flares;
                double travel = (1 - (cycle - Math.floor(cycle))) * Math.min(1, reach);
                int start = (int) (travel * (knots - 5));
                var pulse = new Vec3[7];
                for (int p = 0; p < pulse.length; p++) pulse[p] = path[Math.min(knots, start + p)];
                float cameraFade = (float) Math.clamp(pulse[3].distanceToSqr(eye) / 81, 0, 1);
                float thickness = width * (.13F + (float) travel * .39F);
                EnergyPath.render(light, pulse, eye, thickness, alpha * .80F * cameraFade,
                        ROSE, -time * .6F, 1.1F, true);
            }
            if (strength > .49F && i % 3 == 0) {
                var branchStart = path[knots * 2 / 3];
                var branchEnd = branchStart.add(side.scale(width * 4)).add(0, width * (i % 2 == 0 ? 3 : -3), 0);
                arc(light, dark, branchStart, branchEnd, eye, time, 3, width * .68, seed);
            }
        }
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void crown(NailMesh light, NailMesh dark, Vec3 eye, float time,
                              float y, float radius, float alpha, double spin, int count) {
        boolean throne = radius > 2;
        light.ring(y, radius, radius * .025F, spin, 64, 1, WINE, alpha * .76F);
        for (int i = 0; i < count; i++) {
            double angle = spin + i * TURN / count;
            float shape = .74F + (float) Math.sin(i * 2.39) * .17F;
            var lo = polar(0, y, 0, radius * .86, angle - .24);
            var tip = polar(0, y + radius * shape * alpha, 0, radius * 1.15, angle);
            var hi = polar(0, y, 0, radius * .86, angle + .24);
            var keel = polar(0, y + radius * .24, 0, radius * 1.05, angle);
            dark.quad(point(lo), point(tip), point(hi), point(keel), BLACK, .85F * alpha);
            EnergyPath.render(light, new Vec3[]{lo, tip, hi, keel, lo}, eye, radius * .023F,
                    .86F * alpha, HOT, time * .19F, 1.08F, false);
            EnergyPath.render(light, new Vec3[]{keel, tip}, eye, radius * .016F,
                    .93F * alpha, ROSE, time * .23F, 1.1F, false);
            if (throne) {
                light.diamond((float) keel.x, (float) keel.y + 2.4F, (float) keel.z,
                        radius * .095F, radius * .2F, -spin * 2 + i, HOT, alpha * .65F);
                if (i % 2 == 0) arc(light, dark, tip, polar(0, y - 2, 0, radius * 1.38, angle + .61),
                        eye, time, 2, radius * .09, i + .7);
            }
        }
        if (throne) {
            light.diamond(0, y + 7, 0, radius * .21F, radius * .42F, spin * 2, ROSE, alpha);
            light.ring(y + 8, radius * .76F, radius * .065F, -spin, 64, 3, HOT, alpha * .82F);
        }
    }

    private static void mantle(NailMesh light, NailMesh dark, Vec3 eye, float time, float yaw, float alpha) {
        var backward = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        var right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        for (int i = -3; i <= 3; i++) {
            var start = backward.scale(.18).add(right.scale(i * .11)).add(0, 1.4, 0);
            var tip = backward.scale(.94 + Math.cos(time * .1 + i) * .22)
                    .add(right.scale(i * .27)).add(0, -.38 + .18 * Math.sin(time * .15 + i), 0);
            var fold = start.lerp(tip, .51).add(backward.scale(.32));
            var width = right.scale(.13 + Math.abs(i) * .012);
            dark.quad(point(start.add(width)), point(start.subtract(width)), point(tip), point(fold.add(width)),
                    i % 2 == 0 ? BLOOD : BLACK, .86F * alpha);
            EnergyPath.render(light, EnergyPath.spline(new Vec3[]{start, fold, tip}, 5), eye, .045F,
                    .47F * alpha, HOT, -time * .16F, 1.1F, true);
        }
    }

    static void fractures(NailMesh light, NailMesh dark, Vec3 eye, float time, float radius, float alpha, int count) {
        for (int i = 0; i < count; i++) {
            double turn = i * 2.399963;
            var start = polar(0, 9 + i % 5 * 6, 0, radius * (.12 + .052 * (i % 11)), turn);
            var end = polar(0, 16 + i % 7 * 8, 0, radius * (.35 + .071 * (i % 9)), turn + .27);
            float width = Math.max(.4F, radius * .032F);
            var knots = new Vec3[10];
            var normal = new Vec3(-Math.sin(turn), 0, Math.cos(turn));
            for (int j = 0; j < knots.length; j++) {
                double t = j / 9D;
                double change = Math.sin(i * 4.71 + j * 8.28 + Math.floor(time / 9) * 1.83);
                knots[j] = start.lerp(end, t).add(normal.scale(change * width * Math.sin(t * Math.PI)))
                        .add(0, change * width * .55, 0);
            }
            EnergyPath.render(dark, knots, eye, width, .84F * alpha, BLACK, time * .14F, 1.1F, true);
            EnergyPath.render(light, knots, eye, width * 1.12F, .34F * alpha, WINE, time * .18F, 1.1F, true);
            EnergyPath.render(light, knots, eye, width * .16F, .76F * alpha, HOT, time * .29F, 1.06F, true);
            var fork = knots[4].add(normal.scale(width * 4)).add(0, -width * 2, 0);
            EnergyPath.render(light, new Vec3[]{knots[4], knots[5].lerp(fork, .5), fork}, eye,
                    width * .26F, .57F * alpha, HOT, time * .22F, 1.1F, true);
            dark.diamond((float) fork.x, (float) fork.y, (float) fork.z, width, width * 2, turn + time * .032,
                    BLOOD, .88F * alpha);
            light.diamond((float) fork.x, (float) fork.y + width * .55F, (float) fork.z, width * .38F,
                    width * .7F, turn - time * .034, ROSE, .49F * alpha);
        }
    }

    static void rebuild(NailMesh light, NailMesh dark, Vec3 eye, float time, float life, float r, int quality) {
        float alpha = (1 - life) * Math.min(1, life * 8 + .18F);
        light.ring(.35F, .55F + r * life, .18F, time * .019, 40, 1, HOT, alpha);
        int count = quality >= 6 ? 42 : 20;
        for (int i = 0; i < count; i++) {
            double turn = i * 2.399963 + time * .021;
            float size = .11F + .13F * (i % 3);
            float d = (1 - life) * r * (.22F + (i % 8) * .16F);
            var at = polar(0, .35 + d * (.32 + .18 * (i % 4)), 0, d, turn);
            dark.diamond((float) at.x, (float) at.y, (float) at.z, size, size * 1.8F, turn, BLOOD, .75F * alpha);
            var tail = at.add(0, size * 3.5, 0);
            EnergyPath.render(light, new Vec3[]{tail, at}, eye, size, .72F * alpha, HOT, time * .18F, 1.1F, true);
        }
    }
}
