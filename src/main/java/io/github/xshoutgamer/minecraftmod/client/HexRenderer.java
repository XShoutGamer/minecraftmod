package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexNetwork;
import io.github.xshoutgamer.minecraftmod.hex.HexSpell;
import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic;
import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic.Power;
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
    private org.joml.Matrix4f handProjection;
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
            var game = Minecraft.getInstance();
            boolean localHands = game.player != null && game.options.getCameraType().isFirstPerson()
                    && game.player.getUUID().equals(scene.owner());
            if (localHands && scene.kind() == HexNetwork.HAND_FX) continue;
            if (scene.target().distanceTo(view.origin) < view.drawDistance() + scene.radius()
                    || scene.origin().distanceTo(view.origin) < view.drawDistance()) view.spell(scene);
        }
        view.finish();
    }

    static void hands(RenderHandEvent event, HexSpell spell, float charge) {
        var game = Minecraft.getInstance();
        if (game.player == null || !game.options.getCameraType().isFirstPerson()) return;
        boolean animated = HexConfig.HAND_ANIMATION.get() && !CelestiaConfig.REDUCED_MOTION.get();
        float swing = animated ? charge * .65F : 0;
        // Vanilla can omit the empty offhand event. Draw both corner effects in the main hand pass.
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var pose = new PoseStack();
        var view = new HexRenderer(pose, game.gameRenderer.getMainCamera(), Vec3.ZERO, HexClient.clock(event.getPartialTick()));
        var projection = RenderSystem.getProjectionMatrix();
        var modelView = RenderSystem.getModelViewMatrix();
        pose.mulPose(new org.joml.Matrix4f(modelView).invert());
        view.handProjection = new org.joml.Matrix4f(projection).mul(modelView);
        float xScale = 1 / Math.max(.1F, projection.m00());
        float yScale = 1 / Math.max(.1F, projection.m11());
        float visual = switch (HexConfig.HAND_PRESET.get()) {
            case MINIMAL -> .55F;
            case BALANCED -> .78F;
            case CINEMATIC -> 1F;
            case CINEMATIC_OVERDRIVE -> 1.23F;
        };
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            float drift = animated ? Mth.sin(view.time * .14F + side * 2) * .015F : 0;
            pose.translate(side * (HexConfig.HAND_X.get() + HexConfig.HAND_SPACING.get() + drift) * xScale,
                    -(HexConfig.HAND_Y.get() + drift) * yScale, -1);
            pose.mulPose(Axis.ZP.rotationDegrees(-side * (19 + swing * 9)));
            float size = HexConfig.HAND_SCALE.get().floatValue() * (.14F + charge * .026F);
            pose.scale(size * xScale, size * yScale, size * .50F);
            var sparks = view.mesh(true);
            var shadows = view.mesh(false);
            float length = HexConfig.HAND_LENGTH.get().floatValue();
            int threads = Math.round(7 * visual);
            for (int i = 0; i < threads; i++) {
                double t = i * 2.399963 + view.time * .084;
                var base = new Vec3(side * .23 * Math.sin(t), -.14, 0);
                var tip = new Vec3(side * (.40 + i % 4 * .29), (.12 + i % 5 * .22) * length, -.08);
                arc(sparks, shadows, base, tip, new Vec3(0, 0, 3), view.time, 1, .28 + charge * .19, t);
            }
            if (HexConfig.HAND_PERIPHERAL.get()) {
                motes(sparks, Math.round(28 * visual * HexConfig.HAND_PARTICLES.get().floatValue()), 1.4F,
                        1.12F, view.time, .72F, 0);
            }
            if (spell == HexSpell.ORB && charge > .05F) {
                glyph(sparks, .28F, .48F + charge * .23F, view.time * .08F, 7, charge, true);
            } else if (spell == HexSpell.BARRIER && charge > .05F) {
                glyph(sparks, .27F, .64F, -view.time * .05F, 6, charge * .7F, true);
            }
            float smoke = HexConfig.HAND_SMOKE.get().floatValue() * visual;
            if (smoke > 0) {
                view.cloud(pose, new Smoke(Vec3.ZERO, 1.08F, 1.24F, smoke * (.51F + charge * .19F),
                        .57F + charge * .35F, side > 0 ? .29F : .72F));
            }
            pose.popPose();
        }
        view.flush();
    }

    private java.util.function.ToDoubleFunction<Point> handMask(PoseStack poses) {
        var matrix = new org.joml.Matrix4f(handProjection).mul(poses.last().pose());
        var clip = new org.joml.Vector4f();
        return point -> {
            matrix.transform(clip.set(point.x(), point.y(), point.z(), 1));
            if (clip.w <= .07F) return 0;
            float x = Math.abs(clip.x / clip.w), y = clip.y / clip.w;
            float fade = Mth.lerp(HexConfig.HAND_FADE.get().floatValue(), 1, smooth((clip.w - .11F) / .35F));
            if (!HexConfig.HAND_MASK.get()) return fade;
            float edge = smooth((x - HexConfig.HAND_EXCLUSION.get().floatValue()) / .20F);
            float lower = smooth((-y - .29F) / .31F);
            return edge * lower * fade;
        };
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private int drawDistance() { return quality >= 6 ? 740 : quality >= 4 ? 460 : quality >= 2 ? 315 : 190; }

    private NailMesh mesh(boolean emissive) {
        return new NailMesh(pose, buffers.getBuffer(emissive ? HexMaterials.FILAMENT : HexMaterials.SHADE), emissive,
                emissive ? handProjection == null ? glow : HexConfig.HAND_GLOW.get().floatValue() : 1,
                handProjection == null ? null : handMask(pose));
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
                float size = .42F * HexConfig.WORLD_HAND_SCALE.get().floatValue();
                pose.scale(size, size, size);
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
        if (scene.kind() >= PersonalMagic.BOOK_FX) {
            personal(scene);
            return;
        }
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
        } else if (kind == HexNetwork.ORB_FX && scene.variant() == 1) {
            float front = r * (1 - (1 - life) * (1 - life));
            groundRing(light, at, Math.max(1, front), eye, alpha);
            light.ring(.15F, Math.max(1, front * .71F), .25F, time * .035F, 96, 1, ROSE, alpha * .54F);
            motes(light, count(160, HexConfig.DENSITY.get()), front, front * .55F, time, alpha, 0);
            for (int i = 0; i < strands(9); i++) {
                var end = polar(0, front * (.04 + .06 * (i % 4)), 0, front, i * 2.399963 + time * .023);
                arc(light, dark, Vec3.ZERO, end, eye, time, 2, 1.2 + front * .06, i);
            }
            smoke(at.add(0, 1.3, 0), 1 + front * .48F, 1.6F + front * .32F, alpha * .75F, .82F, .39F);
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
                    boolean local = Minecraft.getInstance().player != null
                            && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                            && Minecraft.getInstance().player.getUUID().equals(scene.owner());
                    var start = local ? from.lerp(Vec3.ZERO, .48) : from;
                    arc(light, dark, start, Vec3.ZERO, eye, time, strands(local ? 3 : 8),
                            Math.max(.35, start.length() * (local ? .032 : .079)), scene.variant() + .38);
                    aura(light, dark, eye, r, scene.variant());
                    smoke(at, 1.5F * r, 1.9F * r, .58F, .64F, .6F);
                }
                case REWRITE, GENESIS -> {
                    if (scene.variant() == 1) {
                        groundRing(light, at, r, eye, .54F);
                        glyph(light, 0, r, time * .008F, 9, .24F, true);
                        glyph(light, .18F, Math.min(2.5F, r * .3F), time * .028F, 6, .56F, false);
                        break;
                    }
                    float radius = kind == 7 ? Math.max(1, Math.min(r, ticks / 5 + 1))
                            : r * Math.max(.12F, Mth.clamp(ticks / 21, 0, 1));
                    if (kind == 7) {
                        for (int ring = 0; ring < 3; ring++) {
                            pose.pushPose();
                            pose.mulPose(Axis.YP.rotation((float) (ring * TURN / 3 + time * .005F)));
                            glyph(mesh(true), 0, radius, time * .008F, 12, .25F * alpha, true);
                            pose.popPose();
                        }
                    }
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

    private void personal(HexNetwork.Scene scene) {
        Vec3 at = scene.target();
        float progress = (float) Math.clamp((clock - scene.start()) / Math.max(1, scene.duration()), 0, 1);
        if (scene.kind() == PersonalMagic.BOOK_FX) {
            var game = Minecraft.getInstance();
            var player = game.level == null ? null : game.level.getPlayerByUUID(scene.owner());
            if (player != null) {
                var forward = player.getLookAngle();
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                var side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
                if (player == game.player && game.options.getCameraType().isFirstPerson() && scene.variant() == 0) {
                    var right = new Vec3(camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z()).scale(-1);
                    var down = new Vec3(camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z()).scale(-1);
                    at = origin.add(forward.scale(2.8)).add(right.scale(2.05)).add(down.scale(1.02));
                } else {
                    at = player.getPosition((float) (clock % 1)).add(side.scale(scene.variant() == 1 ? .9 : 1.2))
                            .add(forward.x * 1.75, scene.variant() == 1 ? 3.8 : 1.1, forward.z * 1.75);
                }
            }
            at = at.add(0, Math.sin(time * .073) * .16, 0);
            begin(at);
            float emergence = .28F + .72F * smooth((float) (clock - scene.start()) / 24);
            book(at, time, emergence * (scene.variant() == 1 ? 1.22F : .84F));
            pose.popPose();
            return;
        }

        begin(at);
        var light = mesh(true);
        var dark = mesh(false);
        var eye = origin.subtract(at);
        var from = scene.origin().subtract(at);
        float radius = scene.radius();
        if (scene.kind() == PersonalMagic.DISSOLVE_FX) {
            float alpha = 1 - progress * .78F;
            arc(light, dark, from.lerp(Vec3.ZERO, .25), Vec3.ZERO, eye, time, strands(7), radius * .48, scene.variant() + .8);
            motes(light, count(75, HexConfig.DENSITY.get()), radius, radius * 2, time, alpha, 1);
            smoke(at, radius, radius * 1.4F, alpha * .62F, .9F, .52F);
        } else if (scene.kind() == PersonalMagic.MEMORY_FX) {
            dark.section(0, -.48F, 0, .96F, .67F, .67F, 4, Math.PI / 4, 0x632456, 0x3a2145, .35F);
            glyph(light, -.43F, .62F, time * .027F, 4, .72F, false);
            glyph(light, .49F, .62F, time * .027F, 4, .72F, false);
        } else if (scene.kind() == PersonalMagic.GUARDIAN_FX) {
            dark.section(0, -.72F, 0, 1.3F, .15F, .42F, 5, time * .04F, 0x190c29, BLACK, .9F);
            light.diamond(0, .67F, 0, .34F, .71F, time * .04F, 0xea493f, .7F);
            glyph(light, .6F, .57F, time * .044F, 5, .76F, true);
            smoke(at, .72F, 1.1F, .59F, .39F, .74F);
        } else if (scene.kind() == PersonalMagic.SHADOW_FX) {
            float fade = 1 - progress;
            if (scene.variant() == 1) arc(light, dark, from, Vec3.ZERO, eye, time, strands(8), 2.3, 6);
            if (scene.variant() == 2) {
                glyph(light, 0, radius * (.5F + progress * .86F), time * .036F, 6, fade * .86F, true);
                glyph(light, .08F, radius * (.42F + progress), -time * .026F, 8, fade, false);
            }
            dark.section(0, 0, 0, radius * (1 + progress), radius * .34F, radius * .17F,
                    9, time * .052, 0x190a1e, 0x08030d, fade * .82F);
            glyph(light, radius * .66F, radius * .54F, time * .09F, 5, fade, true);
            motes(light, count(37, HexConfig.DENSITY.get()), radius, radius * 1.7F, time, fade, 1);
            smoke(at.add(0, radius * .55F, 0), radius, radius * 1.4F, fade * .70F, .3F, .76F);
        } else if (scene.kind() == PersonalMagic.SCRIPTURE_FX) {
            int formation = scene.variant();
            float height = radius * (.7F + formation * .17F);
            glyph(light, height * .32F, radius * .63F, time * (.043F + formation * .008F), 5 + formation, .93F, true);
            glyph(light, -.25F, radius * .74F, -time * .038F, 8 + formation, .69F, false);
            dark.section(0, -.42F, 0, height, .46F, .14F, 5 + formation, time * .02, 0x48233e, BLACK, .78F);
            arc(light, dark, from.add(0,1,0), new Vec3(0,height*.38,0), eye, time, strands(4), .82, formation);
            motes(light,count(47,HexConfig.DENSITY.get()), radius, height * 1.4F, time, .76F, 1);
        } else if (scene.kind() == PersonalMagic.ACTIVATE_FX) {
            if (scene.variant() == Power.VOID.ordinal()) {
                groundRing(light, at, radius, eye, .62F);
                glyph(light, .3F, radius * .58F, time * .029F, 8, .53F, false);
                pose.popPose();
                return;
            }
            glyph(light, .2F, radius * 1.6F, time * .1F, 7, .68F, false);
            arc(light, dark, from, new Vec3(0,1.9,0), eye, time, strands(4), .61, scene.variant());
            smoke(at.add(0, .68, 0), radius * .76F, radius * 1.05F, .46F, .68F, .89F);
        } else if (scene.kind() == PersonalMagic.POWER_FX) {
            var power = Power.values()[scene.variant()];
            float fade = Math.min(1, (1 - progress) * 6);
            switch (power) {
                case STEP, PORTAL -> {
                    portal(at, eye, progress, radius);
                    pose.pushPose();
                    pose.translate(from.x, from.y, from.z);
                    portal(scene.origin(), eye.subtract(from), progress, radius * .78F);
                    pose.popPose();
                }
                case SHIELD -> shell(light, dark, eye, radius, time, null);
                case DREAM -> {
                    echo(scene, at);
                    arc(light, dark, Vec3.ZERO, from, eye, time, strands(4), .72, 4);
                    glyph(light, .12F, 4, time * .016F, 8, fade * .84F, false);
                }
                case ECLIPSE, VOID -> {
                    float y = power == Power.ECLIPSE ? 62 : 1.7F;
                    float formation = smooth((float) (clock - scene.start()) / (power == Power.ECLIPSE ? 70 : 14));
                    float size = (power == Power.ECLIPSE ? 24 : radius * .42F) * Math.max(.04F, formation) * fade;
                    if (power == Power.VOID) {
                        for (int i = 0; i < 6; i++) {
                            light.ring(y + i * .34F, size * (1.14F + i * .35F), .12F + i * .03F,
                                    time * (.056F + i * .012F), 112, 1 + i % 3, i % 2 == 0 ? 0xd43765 : 0x854dff, fade * .7F);
                            var brink = polar(0, y + 6 + i % 2, 0, size * 2.9, i * 2.399963 + time * .019);
                            arc(light, dark, brink, new Vec3(0,y,0), eye, time, 3, 2.8, i);
                        }
                        groundRing(light, at, radius, eye, fade * .38F);
                    } else {
                        for (int i = 0; i < 10; i++) {
                            var edge = polar(0,y+Math.sin(i*4)*8,0,size*1.49,i*2.399963+time*.014);
                            var lower = polar(0,3+i%4,0,radius*.37,i*2.399963-time*.006);
                            arc(light,dark,lower,edge,eye,time,2, 4.4,i);
                        }
                    }
                    pose.pushPose();
                    pose.translate(0, y, 0);
                    pose.mulPose(camera.rotation());
                    var shadow = mesh(false);
                    var corona = mesh(true);
                    for (int i = 0; i < 84; i++) {
                        double a = i * TURN / 84, b = (i + 1) * TURN / 84;
                        var p = new Point(size * (float)Math.cos(a), size * (float)Math.sin(a), 0);
                        var q = new Point(size * (float)Math.cos(b), size * (float)Math.sin(b), 0);
                        var o = new Point(0,0,0);
                        shadow.quad(o,p,q,o,0x08030e,fade);
                        corona.softQuad(p,q,new Point(q.x() * 1.18F,q.y() * 1.18F,0),
                                new Point(p.x() * 1.18F,p.y() * 1.18F,0),0xdb344a,fade * .76F);
                    }
                    glyph(corona,0,size * 1.26F,time * .007F,9,fade * .63F,true);
                    pose.popPose();
                    smoke(at.add(0,y,0),size*1.39F,size*1.28F,fade*.63F,.38F,.87F);
                    motes(light,count(96,HexConfig.DENSITY.get()),size*1.49F,y+3,time,fade,1);
                }
                case MIRROR -> {
                    for (int i = 0; i < 15; i++) {
                        double a = i * 2.399963 + time * .008;
                        var spot = polar(0, 1.5 + i % 6, 0, radius * (.45 + .07 * (i % 7)), a);
                        pose.pushPose();
                        pose.translate(spot.x, spot.y, spot.z);
                        pose.mulPose(Axis.YP.rotation((float) a));
                        float width = 1.4F + i % 4 * .31F;
                        mesh(false).softQuad(new Point(-width,-1.8F,0),new Point(width,-.7F,0),
                                new Point(width,1.6F,0),new Point(-width,2.1F,0),0x884875,fade*.55F);
                        glyph(mesh(true),0,width,time*.04F,4,fade*.72F,true);
                        pose.popPose();
                    }
                }
                case LIBRARY -> {
                    int books = 7;
                    for (int i = 0; i < books; i++) {
                        var point = polar(0, 2 + i % 4 * 1.5, 0, radius * .52, i * TURN / books + time * .013);
                        begin(at.add(point));
                        pose.translate(origin.x-at.x,origin.y-at.y,origin.z-at.z);
                        book(at.add(point),time + i * 7,.38F);
                        pose.popPose();
                        arc(light,dark,Vec3.ZERO,point,eye,time,2,1.24,i);
                    }
                    glyph(light, .3F, radius, time * .012F, 9, fade * .85F, false);
                    glyph(light, 8, radius * .78F, -time * .016F, 11, fade * .86F, false);
                    for (int i = 0; i < 8; i++) {
                        var column = polar(0,0,0,radius*.58,i*TURN/8);
                        dark.section((float)column.x, -.8F, (float)column.z, 7, .68F, .53F, 5, i,
                                0x48203b, 0x231325, fade * .74F);
                        light.section((float)column.x, 5.65F, (float)column.z, .29F, .68F, .59F, 5, i,
                                0xf75040, 0xa7467e, fade * .78F);
                    }
                    smoke(at.add(0,6,0),radius*.73F,6,fade*.48F,.62F,.44F);
                }
                case SCRIPTURE -> {
                    float flare = smooth((float) (clock-scene.start()) / 34);
                    glyph(light, 13, radius * .81F, -time * .018F, 12, fade * flare, false);
                    glyph(light, 19, radius * .54F, time * .012F, 8, fade * flare, false);
                    motes(light, count(165, HexConfig.DENSITY.get()), radius, 19, time, fade * flare, 1);
                    smoke(at.add(0,11,0),radius*.81F,9,fade*.53F,.73F,.42F);
                }
                case PRISON -> {
                    shell(light,dark,eye,radius*.52F,time,null);
                    glyph(light,.2F,radius*.7F,time*.023F,8,fade,true);
                    for (int i=0;i<8;i++) arc(light,dark,polar(0,0,0,radius*.52,i*TURN/8),
                            new Vec3(0,radius*.55,0),eye,time,2,.76,i);
                }
                default -> {
                    glyph(light,.22F,radius*.73F,time*.025F,7,fade,false);
                    arc(light,dark,from.lerp(Vec3.ZERO,.25),Vec3.ZERO,eye,time,strands(5),radius*.19,4);
                    motes(light,count(54,HexConfig.DENSITY.get()),radius,radius*.6F,time,fade,0);
                }
            }
        }
        pose.popPose();
    }

    private void book(Vec3 at, float clock, float size) {
        pose.pushPose();
        var eye = origin.subtract(at);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(eye.x, eye.z)));
        pose.mulPose(Axis.XP.rotationDegrees(-19));
        pose.scale(size, size, size);
        var light = mesh(true);
        var dark = mesh(false);
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(side * (17 + 7 * Mth.sin(clock * .074F))));
            var page = mesh(false);
            var trim = mesh(true);
            float outer = side * 1.01F;
            page.quad(new Point(0,-.7F,-.095F),new Point(outer,-.74F,-.095F),new Point(outer,.74F,-.095F),
                    new Point(0,.7F,-.095F),0x241619,.99F);
            for (int i = 0; i < 7; i++) {
                float depth = -.058F + i * .016F;
                float edge = side * (.88F + i % 3 * .025F);
                page.quad(new Point(0,-.62F,depth),new Point(edge,-.63F,depth),new Point(edge,.62F,depth),
                        new Point(0,.62F,depth),i % 2 == 0 ? 0x82614d : 0x5d423a,.91F);
                page.quad(new Point(edge,-.63F,depth),new Point(edge,-.63F,depth+.011F),new Point(edge,.62F,depth+.011F),
                        new Point(edge,.62F,depth),0xaf8070,.83F);
            }
            for (int row=0;row<8;row++) {
                float y = -.49F + row*.135F;
                for (int glyph=0;glyph<5;glyph++) {
                    float left = side * (.13F + glyph*.137F);
                    float width = side * (.048F + (glyph+row)%3*.016F);
                    trim.softQuad(new Point(left,y,.048F),new Point(left+width,y+.006F,.048F),
                            new Point(left+width,y+.039F,.048F),new Point(left,y+.033F,.048F),
                            row % 3 == 0 ? 0xf04940 : 0xa33430,.37F+.22F*Mth.sin(clock*.11F+row));
                }
            }
            pose.popPose();
        }
        float leaf = Mth.sin(clock*.17F);
        dark.softQuad(new Point(0,-.63F,.06F),new Point(leaf*.91F,-.63F,.61F),
                new Point(leaf*.91F,.63F,.61F),new Point(0,.63F,.06F),0xb27558,.71F);
        glyph(light,-.94F,1.24F,clock*.023F,7,.79F,false);
        motes(light,count(40,HexConfig.DENSITY.get()),1.27F,1.78F,time,.63F,0);
        smoke(at,1.15F*size,1.24F*size,.34F,.47F,.82F);
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
        var mask = handProjection == null ? null : handMask(pose);
        int cells = mask == null ? 1 : 6;
        for (int iy = 0; iy < cells; iy++) {
            for (int ix = 0; ix < cells; ix++) {
                for (int corner = 0; corner < 4; corner++) {
                    float u = (ix + (corner == 0 || corner == 3 ? 0 : 1)) / (float) cells;
                    float v = (iy + (corner < 2 ? 0 : 1)) / (float) cells;
                    var point = new Point((u * 2 - 1) * fog.width, (v * 2 - 1) * fog.height, 0);
                    float alpha = mask == null ? fog.alpha : fog.alpha * (float) mask.applyAsDouble(point);
                    out.addVertex(matrix, point.x(), point.y(), point.z())
                            .setColor(fog.heat, 0, (fog.seed % 1 + 1) % 1, alpha).setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
                }
            }
        }
    }
}
