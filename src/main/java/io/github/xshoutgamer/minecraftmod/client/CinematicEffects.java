package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = MinecraftMod.MOD_ID, value = Dist.CLIENT)
public final class CinematicEffects {
    private static final Impact NONE = new Impact(0, 0, 0, 0, null);
    private record Impact(float exposure, float haze, float motion, float warp, Vec3 source) {}

    private CinematicEffects() {}

    static int quality() {
        return CelestiaConfig.REDUCED.get() ? 0 : CelestiaConfig.QUALITY.get().ordinal();
    }

    @SubscribeEvent
    public static void exposure(RenderGuiEvent.Pre event) {
        try {
            renderSceneEffects(event);
        } finally {
            CrimsonLayer.draw();
        }
    }

    private static void renderSceneEffects(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var view = mc.gameRenderer.getMainCamera();
        var effect = sample(view.getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float exposure = effect.exposure * CelestiaConfig.EXPOSURE.get().floatValue();
        var pulse = ImpactPulse.sample();
        float negative = pulse == null ? 0 : pulse.intensity();
        Vec3 focusSource = pulse == null ? effect.source : pulse.source();
        if ((quality() == 4 || negative > 0) && focusSource != null) {
            float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            var focus = screenFocus(view, focusSource, aspect);
            if (CelestialPost.render(mc, event.getPartialTick().getGameTimeDeltaTicks(), exposure,
                    effect.warp, effect.haze, effect.motion, negative, focus.x, focus.y)) return;
        }
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth(), height = gui.guiHeight();
        if (exposure > 0.003F) {
            gui.fillGradient(0, 0, width, height, color(exposure * 0.64F, 0xe4faff),
                    color(exposure * 0.78F, 0xc5f8ff));
        }
        if (effect.motion > 0.01F && quality() >= 2) {
            for (int i = 0; i < 8; i++) {
                float fraction = (i + 1) / 8F;
                int edge = (int) (height * fraction * 0.21F);
                int tint = color(effect.motion * 0.032F * (1 - fraction), i % 2 == 0 ? 0xffc37a : 0x86e8ff);
                gui.fill(0, edge, width, edge + 1, tint);
                gui.fill(0, height - edge - 1, width, height - edge, tint);
            }
        }
    }

    private static org.joml.Vector2f screenFocus(Camera camera, Vec3 world, float aspect) {
        var relative = world.subtract(camera.getPosition()).toVector3f();
        float forward = relative.dot(camera.getLookVector());
        if (forward < 0.1F) return new org.joml.Vector2f(0.5F, 0.45F);
        float x = .5F - relative.dot(camera.getLeftVector()) / (forward * aspect * 1.4F);
        float y = .5F + relative.dot(camera.getUpVector()) / (forward * 1.4F);
        return new org.joml.Vector2f(Mth.clamp(x, -0.35F, 1.35F), Mth.clamp(y, -0.35F, 1.35F));
    }

    @SubscribeEvent
    public static void atmosphere(ViewportEvent.ComputeFogColor event) {
        if (quality() == 0) return;
        var effect = sample(event.getCamera().getPosition(), (float) event.getPartialTick());
        if (Math.abs(effect.haze) < 0.004F) return;
        if (effect.haze < 0) {
            float tone = 1 + effect.haze;
            event.setRed(event.getRed() * tone);
            event.setGreen(event.getGreen() * tone);
            event.setBlue(event.getBlue() * (0.6F + 0.4F * tone));
        } else {
            event.setRed(Mth.lerp(effect.haze, event.getRed(), 0.44F));
            event.setGreen(Mth.lerp(effect.haze, event.getGreen(), 0.67F));
            event.setBlue(Mth.lerp(effect.haze, event.getBlue(), 0.91F));
        }
    }

    private static Impact sample(Vec3 viewpoint, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return NONE;
        float exposure = 0, haze = 0, motion = 0, warp = 0, nearest = Float.MAX_VALUE;
        Vec3 source = null;
        int range = Math.min(540, CelestiaConfig.VISUAL_RANGE.get());
        var area = new AABB(viewpoint, viewpoint).inflate(range);
        for (var nail : level.getEntitiesOfClass(CelestialNail.class, area)) {
            var center = Vec3.atBottomCenterOf(nail.target().above());
            float distance = (float) viewpoint.distanceTo(center);
            float falloff = Mth.clamp(1 - distance / range, 0, 1);
            if (falloff == 0) continue;
            if (distance < nearest) {
                nearest = distance;
                source = center;
            }
            float age = nail.surfaceAge() + partial;
            switch (nail.phase()) {
                case SUMMONING, LEVITATING, CHARGING -> {
                    float pressure = Math.min(1, (nail.age() + partial) / 35);
                    if (haze <= 0) haze = Math.min(haze, -falloff * pressure * 0.38F);
                }
                case DESCENDING -> {
                    motion = Math.max(motion, falloff * 0.76F);
                    haze = Math.max(haze, falloff * 0.32F);
                    warp = Math.max(warp, falloff * 0.29F);
                }
                case IMPACTING, EMBEDDED, SOLIDIFYING -> {
                    float transientLight = Math.max(0, 1 - age / 9F);
                    exposure = Math.max(exposure, falloff * falloff * transientLight * transientLight);
                    haze = Math.max(haze, falloff * (float) Math.exp(-age / 190F) * 0.56F);
                    motion = Math.max(motion, falloff * transientLight);
                    warp = Math.max(warp, falloff * Math.max(0, 1 - age / 32));
                }
            }
        }
        float flash = CelestiaConfig.REDUCED_FLASH.get() ? 0 : CelestiaConfig.FLASH.get().floatValue();
        float movement = CelestiaConfig.REDUCED_MOTION.get() ? 0 : 1;
        return new Impact(exposure * flash, haze * CelestiaConfig.ATMOSPHERE.get().floatValue(),
                motion * CelestiaConfig.MOTION.get().floatValue() * movement,
                warp * CelestiaConfig.DISTORTION.get().floatValue() * movement, source);
    }

    private static int color(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | rgb;
    }
}
