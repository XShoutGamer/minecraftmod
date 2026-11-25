package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.io.IOException;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class CelestialRenderTypes extends RenderStateShard {
    private static ShaderInstance shader;
    private static final ShaderStateShard CELESTIAL_SHADER = new ShaderStateShard(
            () -> shader == null ? GameRenderer.getRendertypeEyesShader() : shader);

    static void load(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MinecraftMod.id("celestial_energy"),
                    DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
        } catch (IOException | RuntimeException failure) {
            shader = null;
            LogUtils.getLogger().warn("Celestia energy shader unavailable. Using the standard emissive pass.", failure);
        }
    }

    private CelestialRenderTypes() {
        super("celestia", () -> {}, () -> {});
    }

    static RenderType material(ResourceLocation texture) {
        return RenderType.create("celestia_material", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                16384, true, true, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false));
    }

    static RenderType energy(ResourceLocation texture) {
        return RenderType.create("celestia_energy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                16384, false, false, RenderType.CompositeState.builder()
                        .setShaderState(CELESTIAL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
