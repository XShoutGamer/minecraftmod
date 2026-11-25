package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class CelestialRenderTypes extends RenderStateShard {
    private CelestialRenderTypes() {
        super("celestia", () -> {}, () -> {});
    }

    static RenderType energy(ResourceLocation texture) {
        return RenderType.create("celestia_energy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                16384, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
