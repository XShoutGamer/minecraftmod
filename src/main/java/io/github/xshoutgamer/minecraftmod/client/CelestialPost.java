package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

final class CelestialPost {
    private static PostChain effect;
    private static RenderTarget target;
    private static volatile boolean invalidated = true;
    private static boolean unavailable;
    private static boolean wasMonochrome;
    private static int width, height;

    private CelestialPost() {}

    static void invalidate() { invalidated = true; }

    static boolean render(Minecraft client, float delta, float exposure, float distortion, float haze,
                          float motion, float monochrome, float scarlet, float scarletWarp, float x, float y) {
        if (invalidated || target != client.getMainRenderTarget()) {
            if (effect != null) effect.close();
            effect = null;
            target = client.getMainRenderTarget();
            invalidated = false;
            unavailable = false;
            wasMonochrome = false;
        }
        if (monochrome < .003F) wasMonochrome = false;
        if (exposure < 0.003F && distortion < 0.003F && Math.abs(haze) < 0.003F && motion < 0.003F && monochrome < 0.003F && scarlet < 0.003F) return false;
        if (unavailable) return false;
        try {
            if (effect == null) {
                effect = new PostChain(client.getTextureManager(), client.getResourceManager(), target,
                        MinecraftMod.id("shaders/post/celestial_exposure.json"));
                width = 0;
                ImpactPulse.diagnostics("framebuffer shader initialized");
            }
            if (width != target.width || height != target.height) {
                width = target.width;
                height = target.height;
                effect.resize(width, height);
            }
            effect.setUniform("Exposure", exposure);
            effect.setUniform("Warp", distortion);
            effect.setUniform("Haze", haze);
            effect.setUniform("Motion", motion);
            effect.setUniform("Monochrome", monochrome);
            effect.setUniform("Scarlet", scarlet);
            effect.setUniform("ScarletWarp", scarletWarp);
            effect.setUniform("FocusX", x);
            effect.setUniform("FocusY", y);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            effect.process(delta);
            if (monochrome > .003F && !wasMonochrome) {
                ImpactPulse.diagnostics("framebuffer monochrome rendered");
                wasMonochrome = true;
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            unavailable = true;
            if (effect != null) effect.close();
            effect = null;
            LogUtils.getLogger().warn("Celestia scene shader unavailable. Using the exposure overlay.", failure);
            return false;
        } finally {
            target.bindWrite(true);
            RenderSystem.enableDepthTest();
        }
    }
}
