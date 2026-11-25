package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
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
    private static final int RANGE = 260;
    private static final Impact NONE = new Impact(0, 0, 0);
    private record Impact(float exposure, float haze, float motion) {}

    private CinematicEffects() {}

    static int quality() {
        return CelestiaConfig.REDUCED.get() ? 0 : CelestiaConfig.QUALITY.get().ordinal();
    }

    @SubscribeEvent
    public static void exposure(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var effect = sample(mc.gameRenderer.getMainCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth(), height = gui.guiHeight();
        if (effect.exposure > 0.003F) {
            gui.fillGradient(0, 0, width, height, color(effect.exposure * 0.72F, 0xe4faff),
                    color(effect.exposure, 0xc5f8ff));
        }
        if (effect.motion > 0.01F && quality() >= 2) {
            for (int i = 0; i < 8; i++) {
                float fraction = (i + 1) / 8F;
                int edge = (int) (height * fraction * 0.21F);
                int tint = color(effect.motion * 0.038F * (1 - fraction), i % 2 == 0 ? 0xffc37a : 0x86e8ff);
                gui.fill(0, edge, width, edge + 1, tint);
                gui.fill(0, height - edge - 1, width, height - edge, tint);
            }
        }
    }

    @SubscribeEvent
    public static void atmosphere(ViewportEvent.ComputeFogColor event) {
        if (quality() == 0) return;
        var effect = sample(event.getCamera().getPosition(), (float) event.getPartialTick());
        if (effect.haze < 0.004F) return;
        event.setRed(Mth.lerp(effect.haze, event.getRed(), 0.32F));
        event.setGreen(Mth.lerp(effect.haze, event.getGreen(), 0.53F));
        event.setBlue(Mth.lerp(effect.haze, event.getBlue(), 0.80F));
    }

    private static Impact sample(Vec3 viewpoint, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return NONE;
        float exposure = 0, haze = 0, motion = 0;
        var area = new AABB(viewpoint, viewpoint).inflate(RANGE);
        for (var nail : level.getEntitiesOfClass(CelestialNail.class, area)) {
            float distance = (float) viewpoint.distanceTo(Vec3.atCenterOf(nail.target()));
            float falloff = Mth.clamp(1 - distance / RANGE, 0, 1);
            if (falloff == 0) continue;
            switch (nail.phase()) {
                case SUMMONING, LEVITATING, CHARGING -> haze = Math.max(haze, falloff * 0.25F);
                case DESCENDING -> {
                    motion = Math.max(motion, falloff * 0.65F);
                    haze = Math.max(haze, falloff * 0.28F);
                }
                case IMPACTING -> {
                    float age = nail.surfaceAge() + partial;
                    float transientLight = Math.max(0, 1 - age / 8F);
                    exposure = Math.max(exposure, falloff * falloff * transientLight * transientLight);
                    haze = Math.max(haze, falloff * (float) Math.exp(-age / 33F) * 0.46F);
                    motion = Math.max(motion, falloff * transientLight);
                }
                default -> {}
            }
        }
        float flashes = CelestiaConfig.FLASH.get().floatValue();
        float streaks = CelestiaConfig.MOTION.get().floatValue();
        return new Impact(exposure * flashes, haze, motion * streaks);
    }

    private static int color(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | rgb;
    }
}
