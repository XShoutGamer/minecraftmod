package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import io.github.xshoutgamer.minecraftmod.nail.NailCast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping CAST = new KeyMapping("key.minecraftmod.cast", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.minecraftmod");
    private static boolean held;

    private ClientEvents() {}

    @EventBusSubscriber(modid = MinecraftMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void materials(ModelEvent.ModifyBakingResult event) { FlowingBlockModel.wrap(event); }

        @SubscribeEvent
        public static void shaders(RegisterShadersEvent event) { CelestialRenderTypes.load(event); }

        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> CelestialPost.invalidate());
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(CAST); }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(MinecraftMod.NAIL.get(), NailRenderer::new);
        }
    }

    @EventBusSubscriber(modid = MinecraftMod.MOD_ID, value = Dist.CLIENT)
    public static final class Gameplay {
        @SubscribeEvent
        public static void input(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean down = CAST.isDown();
            boolean pressed = CAST.consumeClick();
            if ((down || pressed) && !held && mc.screen == null && mc.player != null && mc.getConnection() != null
                    && NetworkRegistry.hasChannel(mc.getConnection(), NailCast.INSTANCE.type().id())) {
                PacketDistributor.sendToServer(NailCast.INSTANCE);
            }
            held = down;
            while (CAST.consumeClick()) {
                // Discard key-repeat events. The held state is the only cast trigger.
            }
        }

        @SubscribeEvent
        public static void shake(ViewportEvent.ComputeCameraAngles event) {
        if (CelestiaConfig.REDUCED_MOTION.get()) return;
            var level = Minecraft.getInstance().level;
            double scale = CelestiaConfig.SHAKE.get() * (CelestiaConfig.REDUCED.get() ? 0.55 : 1);
            if (level == null || scale == 0) return;
            var camera = event.getCamera().getPosition();
            double strength = 0, clock = level.getGameTime() + event.getPartialTick();
            for (var nail : level.getEntitiesOfClass(CelestialNail.class, new AABB(camera, camera).inflate(360))) {
                double distance = camera.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(nail.target()));
                double falloff = Math.max(0, 1 - distance / 350);
                double pulse = switch (nail.phase()) {
                    case CHARGING -> 0.12;
                    case DESCENDING -> 0.35;
                    case IMPACTING -> Math.exp(-nail.age() * 0.14) * 3.0;
                    default -> 0;
                };
                strength = Math.max(strength, falloff * falloff * pulse);
            }
            float tremor = (float) (strength * scale);
            event.setPitch(event.getPitch() + tremor * (float) Math.sin(clock * 1.9));
            event.setRoll(event.getRoll() + tremor * 0.65F * (float) Math.cos(clock * 2.2));
        }
    }
}
