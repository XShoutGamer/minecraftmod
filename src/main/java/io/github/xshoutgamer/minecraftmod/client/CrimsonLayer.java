package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Composites the original crimson geometry after the grayscale pass, with world depth intact. */
@EventBusSubscriber(modid = MinecraftMod.MOD_ID, value = Dist.CLIENT)
public final class CrimsonLayer {
    private static final RenderType GLOW = CelestialRenderTypes.energy(MinecraftMod.id("textures/entity/celestial_energy.png"));
    private static final RenderType DARK = RenderType.entityTranslucent(MinecraftMod.id("textures/entity/celestial_energy.png"));
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static TextureTarget depth;
    private static Matrix4f worldProjection, worldModelView;
    private static VertexSorting worldSorting;
    private static boolean captured;
    private static boolean logged;

    private record Bolt(CelestialStorm.Frame frame, Matrix4f pose, float opacity) {}

    private CrimsonLayer() {}

    static boolean defer(CelestialStorm.Frame frame, PoseStack poses, float opacity) {
        if (!ImpactPulse.pending() || !frame.hasCrimson()) return false;
        if (BOLTS.isEmpty()) {
            worldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            worldModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            worldSorting = RenderSystem.getVertexSorting();
        }
        BOLTS.add(new Bolt(frame, new Matrix4f(poses.last().pose()), opacity));
        return true;
    }

    @SubscribeEvent
    public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || BOLTS.isEmpty()) return;
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (depth == null) depth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        if (depth.width != main.width || depth.height != main.height) depth.resize(main.width, main.height, Minecraft.ON_OSX);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
        captured = true;
    }

    static void draw() {
        if (!captured || BOLTS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        if (depth.width != main.width || depth.height != main.height) return;
        main.copyDepthFrom(depth);
        main.bindWrite(true);
        var guiProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var guiSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.set(worldModelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(worldProjection, worldSorting);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            var buffers = mc.renderBuffers().bufferSource();
            for (Bolt bolt : BOLTS) {
                var poses = new PoseStack();
                poses.mulPose(bolt.pose);
                bolt.frame.crimsonGlow(new NailMesh(poses, buffers.getBuffer(GLOW), true, bolt.opacity));
            }
            buffers.endBatch(GLOW);
            for (Bolt bolt : BOLTS) {
                var poses = new PoseStack();
                poses.mulPose(bolt.pose);
                bolt.frame.darkCenters(new NailMesh(poses, buffers.getBuffer(DARK), false, bolt.opacity));
            }
            buffers.endBatch(DARK);
            if (!logged) {
                ImpactPulse.diagnostics("crimson lightning drawn after scene processing with world depth");
                logged = true;
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(guiProjection, guiSorting);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            BOLTS.clear();
            captured = false;
        }
    }

    @SubscribeEvent
    public static void finish(RenderFrameEvent.Post event) {
        BOLTS.clear();
        captured = false;
        if (Minecraft.getInstance().level == null && depth != null) {
            depth.destroyBuffers();
            depth = null;
        }
    }
}
