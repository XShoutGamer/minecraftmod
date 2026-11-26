package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.io.IOException;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

final class HexMaterials extends RenderStateShard {
    private static ShaderInstance smoke;
    private static final ShaderStateShard VOLUME = new ShaderStateShard(() -> smoke);
    private static final TextureStateShard ATLAS = new TextureStateShard(MinecraftMod.id("textures/entity/scarlet_filament.png"), true, false);

    static final RenderType FILAMENT = create("scarlet_filament", RENDERTYPE_ENTITY_TRANSLUCENT_SHADER, ADDITIVE_TRANSPARENCY, ATLAS);
    static final RenderType SHADE = create("scarlet_shadow", RENDERTYPE_ENTITY_TRANSLUCENT_SHADER, TRANSLUCENT_TRANSPARENCY, ATLAS);
    static final RenderType SMOKE = create("scarlet_volume", VOLUME, TRANSLUCENT_TRANSPARENCY, ATLAS);

    private HexMaterials() { super("scarlet", () -> {}, () -> {}); }

    static void load(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MinecraftMod.id("scarlet_volume"),
                    DefaultVertexFormat.NEW_ENTITY), loaded -> smoke = loaded);
        } catch (IOException | RuntimeException failure) {
            smoke = null;
            LogUtils.getLogger().warn("Scarlet smoke shader unavailable. Using layered wisps.", failure);
        }
    }

    static boolean volumeReady() { return smoke != null; }

    static void prepare(float time, int quality) {
        if (smoke == null) return;
        smoke.safeGetUniform("Time").set(time);
        smoke.safeGetUniform("Steps").set(quality >= 6 ? 32 : quality >= 4 ? 19 : quality >= 2 ? 12 : 7);
    }

    private static RenderType create(String name, ShaderStateShard shader, TransparencyStateShard blend, TextureStateShard texture) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 65536, false, true,
                RenderType.CompositeState.builder().setShaderState(shader).setTextureState(texture)
                        .setTransparencyState(blend).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                        .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
    }
}
