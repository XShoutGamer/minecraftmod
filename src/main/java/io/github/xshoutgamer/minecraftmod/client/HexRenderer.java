package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexNetwork;
import io.github.xshoutgamer.minecraftmod.hex.HexSpell;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import static io.github.xshoutgamer.minecraftmod.client.HexGeometry.*;
import io.github.xshoutgamer.minecraftmod.client.NailMesh.Point;

final class HexRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS = buffers();

    private static MultiBufferSource.BufferSource buffers() {
        var types = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        for (var type : List.of(HexMaterials.SHADE, HexMaterials.SMOKE, HexMaterials.FILAMENT)) {
            types.put(type, new ByteBufferBuilder(65536));
        }
        return MultiBufferSource.immediateWithBuffers(types, new ByteBufferBuilder(4096));
    }

    private record Smoke(Vec3 at, float width, float height, float alpha, float heat, float seed) {}
    private final PoseStack pose;
    private final MultiBufferSource.BufferSource buffers;
    private final Camera camera;
    private final Vec3 origin;
    private final int quality;
    private final float time;
    private final float glow;
    private final double clock;
    private final List<Smoke> clouds = new ArrayList<>();

    private HexRenderer(PoseStack pose, Camera camera, Vec3 origin, double clock) {
        this.pose = pose;
        this.buffers = BUFFERS;
        this.camera = camera;
        this.origin = origin;
        this.clock = clock;
        quality = CinematicEffects.quality();
        time = (float) (clock % 24000) * (CelestiaConfig.REDUCED_MOTION.get() ? .23F : 1);
        glow = HexConfig.GLOW.get().floatValue();
    }

    static void world(RenderLevelStageEvent event, Collection<HexNetwork.Scene> domains, Collection<HexNetwork.Scene> effects) {
        var camera = event.getCamera();
        var pose = event.getPoseStack();
        var view = new HexRenderer(pose, camera, camera.getPosition(),
                HexClient.clock(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        for (var domain : domains) {
            if (domain.origin().distanceTo(view.origin) <= domain.radius() + view.drawDistance()
                    || domain.target().distanceTo(view.origin) < view.drawDistance()) view.domain(domain);
        }
        for (var scene : effects) {
            if (scene.target().distanceTo(view.origin) < view.drawDistance() + scene.radius()
                    || scene.origin().distanceTo(view.origin) < view.drawDistance()) view.spell(scene);
        }
        view.finish();
    }

    static void hands(RenderHandEvent event, boolean casting) {
        var game = Minecraft.getInstance();
        if (game.player == null) return;
        var pose = event.getPoseStack();
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? game.player.getMainArm() : game.player.getMainArm().getOpposite();
        var view = new HexRenderer(pose, game.gameRenderer.getMainCamera(),
                Vec3.ZERO, HexClient.clock(event.getPartialTick()));
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.pushPose();
        pose.translate(side * (.31 + event.getEquipProgress() * .24), -.28, -.66);
        pose.mulPose(Axis.ZP.rotationDegrees(side * 14));
        pose.scale(.37F, .37F, .37F);
        view.aura(view.mesh(true), view.mesh(false), new Vec3(0, 0, 2), casting ? .98F : .62F, side * 2);
        if (HexConfig.SMOKE.get() > 0) {
            view.cloud(pose, new Smoke(Vec3.ZERO, 1.10F, 1.57F, casting ? .87F : .62F,
                    casting ? 1 : .63F, arm == HumanoidArm.RIGHT ? .2F : .7F));
            pose.translate(side * .3F, .21F, -.18F);
            view.cloud(pose, new Smoke(Vec3.ZERO, .92F, 1.2F, .44F, .57F, side > 0 ? .66F : .39F));
        }
        pose.popPose();
        view.flush();
    }

    private int drawDistance() { return quality >= 6 ? 740 : quality >= 4 ? 460 : quality >= 2 ? 315 : 190; }

    private NailMesh mesh(boolean emissive) {
        return new NailMesh(pose, buffers.getBuffer(emissive ? HexMaterials.FILAMENT : HexMaterials.SHADE), emissive,
                emissive ? glow : 1);
    }

    private void begin(Vec3 at) {
        pose.pushPose();
        var relative = at.subtract(origin);
        pose.translate(relative.x, relative.y, relative.z);
    }

    private void domain(HexNetwork.Scene scene) {
        var game = Minecraft.getInstance();
        var player = game.level == null ? null : game.level.getPlayerByUUID(scene.owner());
        Vec3 owner = player == null ? scene.target() : player.getPosition((float) (clock % 1));
        boolean firstPerson = player != null && player == game.player && game.options.getCameraType().isFirstPerson();
        var radius = HexClient.reach(scene, clock);
        boolean enhanced = scene.kind() == HexNetwork.ENHANCED;
        float show = scene.kind() == HexNetwork.COLLAPSING ? Mth.clamp((float) ((scene.start() + scene.duration() - clock) / scene.duration()), 0, 1) : 1;
        if (!firstPerson && owner.distanceTo(origin) < 210 && show > 0) {
            begin(owner);
            var spark = mesh(true);
            var shadow = mesh(false);
            var eye = origin.subtract(owner);
            float yaw = player == null ? 0 : player.getYRot();
            for (int side : new int[]{-1, 1}) {
                pose.pushPose();
                var hand = new Vec3(side * .57, 1.11, -.2).yRot(-yaw * Mth.DEG_TO_RAD);
                pose.translate(hand.x, hand.y, hand.z);
                pose.scale(.42F, .42F, .42F);
                aura(mesh(true), mesh(false), eye.subtract(hand), (enhanced ? 1.35F : .72F) * show, side * 2);
                pose.popPose();
                smoke(owner.add(hand), .52F, .78F, .50F * show, .7F, side * .31F);
            }
            motes(spark, count(27, HexConfig.DENSITY.get()), 2.1F, 3.2F, time, .58F * show, 0);
            if (scene.kind() == HexNetwork.AWAKENING || enhanced) {
                arc(spark, shadow, new Vec3(0,.32,0), new Vec3(0,4,0), eye, time, strands(4), .94, 4);
                glyph(spark, .26F, 2.8F, time * .02F, 7, .65F * show, false);
            }
            pose.popPose();
        }
        if (radius < 1 || show <= 0) return;

        begin(scene.origin());
        var mesh = mesh(true);
        var dark = mesh(false);
        Vec3 eye = origin.subtract(scene.origin());
        int divisions = quality >= 6 ? 192 : quality >= 4 ? 128 : quality >= 2 ? 90 : 52;
        float height = Math.min(enhanced ? 106 : 75, 13 + radius * .39F);
        Vec3[] crest = new Vec3[divisions + 1], ridge = new Vec3[divisions + 1];
        for (int i = 0; i <= divisions; i++) {
            double angle = TURN * (i % divisions) / divisions;
            double curl = Math.sin(angle * 7 + time * .017) * .9 + Math.sin(angle * 19 - time * .063) * .52;
            var at = polar(0, 0, 0, radius + curl * Math.min(1, radius * .07), angle);
            double bottom = floor(scene.origin(), at);
            crest[i] = new Vec3(at.x, bottom, at.z);
            ridge[i] = new Vec3(at.x * .985, height * (.76 + .23 * Math.sin(angle * 5 - time * .028)), at.z * .985);
        }
        for (int i = 0; i < divisions; i++) {
            var low = crest[i];
            var next = crest[i + 1];
            var top = ridge[i];
            var topNext = ridge[i + 1];
            float pulse = (.18F + .32F * Mth.sin(i * .57F + time * .084F) * Mth.sin(i * .57F + time * .084F)) * show;
            var a = low.lerp(top, .19 + .07 * Mth.sin(i * .14F + time * .09F));
            var b = next.lerp(topNext, .19 + .07 * Mth.sin((i + 1) * .14F + time * .09F));
            dark.quad(point(low), point(next), point(topNext), point(top), BLOOD, pulse * .34F);
            mesh.softQuad(point(low), point(next), point(b), point(a), WINE, pulse * .82F);
            if (i % 4 == 0) {
                var edge = low.lerp(top, .54);
                var twist = low.lerp(top, .27).add(Math.sin(i) * 3, 0, Math.cos(i) * 3);
                EnergyPath.render(mesh, new Vec3[]{low, twist, edge, top}, eye, .22F, pulse, HOT, time * .19F, 1.1F, true);
            }
        }
        EnergyPath.render(mesh, crest, eye, Math.min(1.2F, radius * .04F), .86F * show, HOT, time * .12F, 1.12F, false);
        EnergyPath.render(mesh, ridge, eye, .24F, .55F * show, WINE, time * .08F, 1.12F, false);
        int bolts = Math.min(quality >= 6 ? 30 : 19, count(enhanced ? 12 : 7, HexConfig.LIGHTNING.get()));
        for (int i = 0; i < bolts; i++) {
            int a = (int) (i * (divisions / (double) Math.max(1, bolts)));
            int b = (a + 4 + i % 4) % divisions;
            arc(mesh, dark, crest[a], ridge[b], eye, time, Math.min(quality >= 6 ? 7 : 4, strands(enhanced ? 3 : 2)),
                    Math.min(8, 1.2 + radius * .046), i * 3.38);
        }
        if (quality > 0) {
            glyph(mesh, height * .72F, radius * (enhanced ? .76F : .46F), time * .005F, enhanced ? 11 : 7, show * .66F, false);
            glyph(mesh, height + 13, radius * .29F, -time * .008F, 9, show * .51F, false);
        }
        motes(mesh, count(enhanced ? 205 : 112, HexConfig.DENSITY.get()), radius * .94F, height * .82F, time, .53F * show, 0);
        if (quality > 0) {
            var sweep = scene.kind() == HexNetwork.EXPANDING ? radius * .8F : radius * .94F;
            groundCurrents(mesh, scene.origin(), eye, sweep, show * (enhanced ? 1 : .66F));
        }
        int haze = count(enhanced ? 16 : 11, HexConfig.SMOKE.get());
        for (int i = 0; i < haze; i++) {
            var at = crest[i * divisions / Math.max(1, haze)].add(0, 4 + height * (i % 4) * .11F, 0);
            smoke(scene.origin().add(at), 4 + height * .36F, 3 + height * .22F, show * .58F, .28F, i * .09F);
        }
        pose.popPose();
    }

    private void spell(HexNetwork.Scene scene) {
        var at = scene.target();
        if (scene.kind() == HexNetwork.ORB_FX && scene.variant() == 0) {
            if (clock - scene.start() > 2.25) return;
            at = scene.origin().lerp(at, Mth.clamp(clock - scene.start() + .25, 0, 1));
        }
        float r = scene.radius();
        float ticks = Math.max(0, (float) (clock - scene.start()));
        float life = Mth.clamp(ticks / Math.max(1, scene.duration()), 0, 1);
        float alpha = 1 - life * .83F;
        begin(at);
        var light = mesh(true);
        var dark = mesh(false);
        var eye = origin.subtract(at);
        var from = scene.origin().subtract(at);
        int kind = scene.kind();
        if (kind == HexNetwork.HAND_FX) {
            pose.popPose();
            begin(scene.origin());
            aura(mesh(true), mesh(false), origin.subtract(scene.origin()), .42F + r * .79F, scene.variant());
            smoke(scene.origin(), .74F + r * .50F, 1.1F + r * .35F, .64F, 1, .35F);
        } else if (kind == HexNetwork.ORB_FX || kind == 6 + HexSpell.ORB.ordinal()) {
            float size = kind == HexNetwork.ORB_FX ? r : r * .6F;
            smoke(at, size * 1.2F, size * 1.2F, .90F * alpha, 1, .6F);
            aura(light, dark, eye, size, scene.variant());
            arc(light, dark, from, Vec3.ZERO, eye, time, strands(4), size * .66, .53);
        } else if (kind == HexNetwork.BARRIER_FX) {
            if (scene.variant() == 2) {
                smoke(at, r * 1.45F, r * 1.12F, alpha * .69F, 1, .33F);
                arc(light, dark, from, Vec3.ZERO, eye, time, strands(5), .82, ticks);
                light.ring(0, .2F + life * r, .06F, time * .013F, 42, 1, ROSE, alpha);
            } else {
                shell(light, dark, eye, r, time, scene.variant() == 4 ? from.normalize() : null);
                motes(light, count(48, HexConfig.DENSITY.get()), r, r, time, .68F, 1);
                smoke(at.add(0, .65, 0), r * .86F, r * .75F, .35F * alpha, .4F, .66F);
            }
        } else if (kind == HexNetwork.RUNE_FX) {
            glyph(light, .14F, r, time * .012F, 3 + scene.variant() * 2, .85F, false);
            glyph(light, .18F, r * .46F, -time * .023F, 4 + scene.variant(), .81F, false);
            if (from.lengthSqr() > 1 && from.lengthSqr() < 1600) arc(light, dark, from, Vec3.ZERO, eye, time, strands(3), 1.4, scene.variant());
            motes(light, count(36, HexConfig.DENSITY.get()), r * 1.18F, 2.1F, time, .63F, 0);
            smoke(at.add(0, .56, 0), r, 1.25F, .30F, .25F, scene.variant() * .12F);
        } else if (kind >= 6 && kind < 6 + HexSpell.values().length) {
            switch (HexSpell.values()[kind - 6]) {
                case TELEKINESIS -> {
                    arc(light, dark, from, Vec3.ZERO, eye, time, strands(8), Math.max(.85, from.length() * .079), scene.variant() + .38);
                    aura(light, dark, eye, r, scene.variant());
                    smoke(at, 1.5F * r, 1.9F * r, .58F, .64F, .6F);
                }
                case REWRITE, GENESIS -> {
                    float radius = r * Math.max(.12F, Mth.clamp(ticks / 21, 0, 1));
                    groundRing(light, at, radius, eye, .72F * alpha);
                    glyph(light, .42F, radius * .56F, time * .029F, kind == 7 ? 8 : 11, alpha, false);
                    motes(light, count(kind == 7 ? 100 : 175, HexConfig.DENSITY.get()), radius, 5, time, alpha, kind == 7 ? 0 : 1);
                    for (int i = 0; i < strands(6); i++) {
                        var end = polar(0, .25, 0, radius, i * 2.399 + time * .029);
                        arc(light, dark, from, end, eye, time, 2, 1.8, i * .42);
                    }
                    smoke(at.add(0, .62, 0), radius * .76F, radius * .19F, alpha * .65F, .6F, .42F);
                }
                case ASCENSION -> {
                    glyph(light, 2.27F, .74F, time * .054F, 7, .88F, false);
                    glyph(light, .09F, r, -time * .034F, 9, .64F, false);
                    for (int i = 0; i < strands(5); i++) {
                        var end = polar(0, -.9 + i * .18, 0, r * 1.08, i * 2.399 - time * .043);
                        arc(light, dark, new Vec3(0,1.4,0), end, eye, time, 2, .56, i);
                    }
                    smoke(at.add(0, .49, 0), r * .54F, r * .77F, .64F, .42F, .37F);
                }
                case FRACTURE -> {
                    portal(at, eye, life, r);
                    if (from.lengthSqr() > 4) {
                        begin(scene.origin());
                        portal(scene.origin(), origin.subtract(scene.origin()), life, r * .77F);
                        pose.popPose();
                    }
                }
                case TEMPORAL, GRAVITY -> {
                    groundRing(light, at, r, eye, alpha);
                    for (int i = 0; i < strands(10); i++) {
                        var start = polar(0, .2, 0, r * (.5 + .08 * (i % 6)), i * 2.399);
                        var end = polar(0, 3 + i % 5, 0, r * .46, i * 2.399 + time * .037);
                        arc(light, dark, start, end, eye, time * (kind == 13 ? .47F : 1), 2, 2, i);
                    }
                    glyph(light, .14F, r * .66F, time * .013F, kind == 13 ? 12 : 5, alpha, false);
                    motes(light, count(105, HexConfig.DENSITY.get()), r * .92F, r * .81F, time * (kind == 13 ? .18F : 1), alpha, 1);
                    smoke(at.add(0, 2.3, 0), r * .86F, r * .39F, alpha * .56F, .29F, .75F);
                }
                case ECHO -> echo(scene, at);
                default -> motes(light, count(51, HexConfig.DENSITY.get()), r, r * 2, time, alpha, 0);
            }
        }
        pose.popPose();
    }

    private void portal(Vec3 at, Vec3 eye, float life, float radius) {
        float open = Mth.sin(life * Mth.PI);
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.atan2(eye.x, eye.z)));
        pose.scale(open, open, open);
        var light = mesh(true);
        var shadow = mesh(false);
        Vec3[] path = new Vec3[41];
        var center = new Vec3(0, radius * .61F, 0);
        for (int i = 0; i < path.length; i++) {
            double a = TURN * (i % 40) / 40;
            double rough = .83 + .16 * Math.sin(a * 5 + time * .14) + .11 * Math.sin(a * 11 - time * .31);
            path[i] = center.add(Math.cos(a) * radius * .47 * rough, Math.sin(a) * radius * .69 * rough, 0);
            if (i > 0) shadow.quad(point(center), point(path[i - 1]), point(path[i]), point(center), BLACK, .95F);
        }
        EnergyPath.render(light, path, new Vec3(0,0,5), .14F, .95F, HOT, time * .21F, 1.1F, false);
        glyph(light, (float) center.y, radius * .77F, time * .012F, 5, .63F, true);
        motes(light, count(34, HexConfig.DENSITY.get()), radius, radius * 1.52F, time, open, 1);
        smoke(at.add(center), radius * .51F * open, radius * .88F * open, open * .68F, .33F, .91F);
        pose.popPose();
    }

    private void echo(HexNetwork.Scene scene, Vec3 at) {
        var level = Minecraft.getInstance().level;
        var who = level == null ? null : level.getPlayerByUUID(scene.owner());
        if (who == null) return;
        pose.mulPose(Axis.YP.rotationDegrees(180 - who.getYRot()));
        figurePart(0, 1.55F, 0, .46F, .47F, .44F, 0);
        figurePart(0, .98F, 0, .54F, .69F, .27F, 0);
        float gait = (float) (Math.sin(time * .49) * Math.min(.78, who.getDeltaMovement().horizontalDistance() * 8));
        for (int side : new int[]{-1, 1}) {
            figurePart(side * .39F, 1.29F, 0, .18F, .61F, .20F, side * gait);
            figurePart(side * .14F, .67F, 0, .23F, .64F, .24F, -side * gait);
            var m = mesh(true);
            m.diamond(side * .105F, 1.58F, -.236F, .052F, .028F, 0, ROSE, .97F);
        }
        smoke(at.add(0, .86, 0), .75F, 1.25F, .54F, .31F, .67F);
    }

    private void figurePart(float x, float y, float z, float w, float h, float depth, float swing) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotation(swing));
        var light = mesh(true);
        var dark = mesh(false);
        for (int i = 0; i < 4; i++) {
            var a = new Point((i == 0 || i == 1 ? -.5F : .5F) * w, 0, (i == 0 || i == 3 ? -.5F : .5F) * depth);
            int j = (i + 1) % 4;
            var b = new Point((j == 0 || j == 1 ? -.5F : .5F) * w, 0, (j == 0 || j == 3 ? -.5F : .5F) * depth);
            var c = new Point(b.x(), -h, b.z());
            var d = new Point(a.x(), -h, a.z());
            dark.quad(a,b,c,d,BLOOD,.72F);
            light.softQuad(a, b, new Point(b.x(), -.037F, b.z()), new Point(a.x(), -.037F, a.z()), HOT, .67F);
            light.softQuad(a, new Point(a.x() * .77F, 0, a.z() * .77F), new Point(d.x() * .77F, d.y(), d.z() * .77F), d, HOT, .47F);
        }
        pose.popPose();
    }

    private void aura(NailMesh light, NailMesh dark, Vec3 eye, float size, float seed) {
        for (int strand = 0; strand < Math.min(quality >= 6 ? 14 : 8, strands(6)); strand++) {
            var from = polar(0, -.17 * size, 0, size * .20, seed + strand * 1.11);
            var to = polar(0, (.5 + strand % 3 * .23) * size, 0, size * (.56 + strand % 3 * .19),
                    seed + strand * 1.49 + time * .11);
            arc(light, dark, from, to, eye, time, 1, size * .52, strand + seed);
        }
        motes(light, count(34, HexConfig.DENSITY.get()), size * 1.45F, size * 1.8F, time, .71F, 1);
    }

    private void shell(NailMesh light, NailMesh dark, Vec3 eye, float radius, float time, Vec3 back) {
        int layers = quality >= 6 ? 18 : quality >= 4 ? 12 : 7;
        int sides = quality >= 6 ? 92 : quality >= 2 ? 54 : 32;
        for (int row = 0; row < layers; row++) {
            double a = row * Math.PI / layers, b = (row + 1) * Math.PI / layers;
            for (int col = 0; col < sides; col++) {
                double phi = col * TURN / sides, next = (col + 1) * TURN / sides;
                Vec3 p = glob(radius, a, phi), q = glob(radius, a, next);
                Vec3 r = glob(radius, b, next), s = glob(radius, b, phi);
                if (back != null && p.dot(back) > .22 * radius) continue;
                float gain = .10F + .18F * Mth.sin(row * .72F + col * .44F - time * .13F);
                dark.quad(point(p), point(q), point(r), point(s), BLOOD, .18F);
                if ((row + col) % 3 == 1) light.softQuad(point(p), point(q), point(r), point(s), HOT, gain);
                if (col % 13 == 0 && row % 4 == 0) arc(light, dark, p, r, eye, time, 1, .19, row + col);
            }
        }
    }

    private Vec3 glob(float r, double lat, double lon) {
        float radius = r * (.97F + .04F * Mth.sin((float) (lat * 8 + lon * 5 - time * .13)));
        return polar(0, radius * Math.cos(lat), 0, radius * Math.sin(lat), lon);
    }

    private void groundCurrents(NailMesh light, Vec3 center, Vec3 eye, float radius, float alpha) {
        int lanes = quality >= 6 ? 38 : quality >= 4 ? 22 : 12;
        int steps = quality >= 6 ? 33 : 21;
        for (int i = 0; i < lanes; i++) {
            var path = new Vec3[steps];
            double turn = i * 2.399963;
            double length = radius * (.53 + .11 * (i % 5));
            for (int j = 0; j < steps; j++) {
                double t = j / (double) (steps - 1);
                var at = polar(0, 0, 0, length * (.17 + .83 * t), turn + .23 * Math.sin(t * 5 + i) + .31 * t);
                path[j] = new Vec3(at.x, floor(center, at) + .027, at.z);
            }
            EnergyPath.render(light, path, eye, Math.min(.45F, radius * .011F), alpha * (.41F + (i % 4) * .095F),
                    i % 3 == 0 ? HOT : WINE, time * .13F, 1.09F, true);
        }
    }

    private void groundRing(NailMesh light, Vec3 center, float radius, Vec3 eye, float alpha) {
        Vec3[] path = new Vec3[73];
        for (int i = 0; i < path.length; i++) {
            var at = polar(0, 0, 0, radius, (i % 72) * TURN / 72);
            path[i] = new Vec3(at.x, floor(center, at), at.z);
        }
        EnergyPath.render(light, path, eye, .27F, alpha, HOT, time * .09F, 1.12F, false);
    }

    private static float floor(Vec3 center, Vec3 relative) {
        var world = Minecraft.getInstance().level;
        if (world == null) return .12F;
        var column = BlockPos.containing(center.add(relative));
        if (!world.hasChunkAt(column)) return .12F;
        return Math.clamp(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - (float) center.y + .12F, -18, 29);
    }

    private int count(int base, double setting) {
        float multiplier = quality >= 6 ? 4.2F : quality >= 5 ? 2.2F : quality >= 3 ? 1.47F : quality >= 1 ? .77F : .37F;
        return Math.clamp((int) (base * setting * multiplier), 0, 2900);
    }

    private int strands(int base) {
        return Math.clamp(count(base, HexConfig.TENDRILS.get()), 0, quality >= 6 ? 30 : 18);
    }

    private void smoke(Vec3 at, float w, float h, float alpha, float heat, float seed) {
        if (HexConfig.SMOKE.get() <= 0 || w < .05F || at.distanceToSqr(origin) < w * w * .43 || clouds.size() >= 380) return;
        clouds.add(new Smoke(at, w, h, Math.min(.98F, alpha * HexConfig.SMOKE.get().floatValue()), heat, seed));
    }

    private void finish() {
        clouds.sort(Comparator.comparingDouble(cloud -> -cloud.at.distanceToSqr(origin)));
        for (var fog : clouds) {
            begin(fog.at);
            pose.mulPose(camera.rotation());
            cloud(pose, fog);
            pose.popPose();
        }
        flush();
    }

    private void flush() {
        buffers.endBatch(HexMaterials.SHADE);
        buffers.endBatch(HexMaterials.SMOKE);
        buffers.endBatch(HexMaterials.FILAMENT);
    }

    private void cloud(PoseStack pose, Smoke fog) {
        if (!HexMaterials.volumeReady()) {
            var mesh = mesh(false);
            for (int i = 0; i < 5; i++) {
                float spread = fog.width * (1 - i * .12F);
                float height = fog.height * .74F;
                mesh.softQuad(new Point(-spread, -height, 0), new Point(spread, -height, 0), new Point(spread, height, 0),
                        new Point(-spread, height, 0), i % 2 == 0 ? BLOOD : BLACK, fog.alpha * .18F);
            }
            return;
        }
        HexMaterials.prepare(time * .046F, quality);
        var out = buffers.getBuffer(HexMaterials.SMOKE);
        var matrix = pose.last().pose();
        for (int corner = 0; corner < 4; corner++) {
            int x = corner == 0 || corner == 3 ? 0 : 1, y = corner < 2 ? 0 : 1;
            out.addVertex(matrix, (x * 2 - 1) * fog.width, (y * 2 - 1) * fog.height, 0)
                    .setColor(fog.heat, 0, (fog.seed % 1 + 1) % 1, fog.alpha).setUv(x, y)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
        }
    }
}
