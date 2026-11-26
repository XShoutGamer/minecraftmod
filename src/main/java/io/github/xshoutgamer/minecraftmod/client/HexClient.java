package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexNetwork;
import io.github.xshoutgamer.minecraftmod.hex.HexSpell;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MinecraftMod.MOD_ID, value = Dist.CLIENT)
public final class HexClient {
    private record EffectKey(UUID owner, int kind, int variant, long instance) {}
    private static final KeyMapping DOMAIN = new KeyMapping("key.minecraftmod.hex", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.minecraftmod");
    private static final KeyMapping SPELL = new KeyMapping("key.minecraftmod.hex_cast", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.minecraftmod");
    private static final Map<UUID, HexNetwork.Scene> DOMAINS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_SEEN = new LinkedHashMap<>();
    private static final LinkedHashMap<EffectKey, HexNetwork.Scene> EFFECTS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static int held;
    private static boolean consumed, casting;
    static HexSpell selected = HexSpell.TELEKINESIS;

    private HexClient() {}

    static void keys(RegisterKeyMappingsEvent event) {
        event.register(DOMAIN);
        event.register(SPELL);
    }

    public static void receive(HexNetwork.Scene scene) {
        checkWorld();
        if (world == null || !world.dimension().location().equals(scene.dimension())) return;
        if (scene.kind() <= HexNetwork.COLLAPSING) {
            DOMAINS.put(scene.owner(), scene);
            LAST_SEEN.put(scene.owner(), world.getGameTime());
        } else {
            long instance = scene.kind() == HexNetwork.RUNE_FX || scene.kind() == HexNetwork.ORB_FX ? scene.start() : 0;
            EFFECTS.put(new EffectKey(scene.owner(), scene.kind(), scene.variant(), instance), scene);
            if (EFFECTS.size() > 960) EFFECTS.pollFirstEntry();
        }
    }

    private static void checkWorld() {
        var level = Minecraft.getInstance().level;
        if (world == level) return;
        world = level;
        DOMAINS.clear();
        LAST_SEEN.clear();
        EFFECTS.clear();
        held = 0;
        consumed = false;
        casting = false;
    }

    @SubscribeEvent
    public static void input(ClientTickEvent.Post event) {
        var game = Minecraft.getInstance();
        checkWorld();
        if (world == null || game.player == null) return;
        var now = world.getGameTime();
        DOMAINS.values().removeIf(scene -> scene.kind() == HexNetwork.COLLAPSING
                ? now >= scene.start() + scene.duration()
                : now - LAST_SEEN.getOrDefault(scene.owner(), now) > 45);
        LAST_SEEN.keySet().retainAll(DOMAINS.keySet());
        EFFECTS.values().removeIf(scene -> now >= scene.start() + scene.duration());
        var own = DOMAINS.get(game.player.getUUID());
        boolean domainDown = DOMAIN.isDown();
        boolean tap = DOMAIN.consumeClick();
        while (DOMAIN.consumeClick()) {}
        if (game.screen instanceof HexWheel wheel) {
            if (!wheel.keyHeld()) wheel.onClose();
        } else if (game.screen == null && (domainDown || tap) && !consumed) {
            held++;
            if (held >= 8 && own != null && own.kind() != HexNetwork.COLLAPSING) {
                consumed = true;
                game.setScreen(new HexWheel(DOMAIN));
            }
        } else if (game.screen == null && !domainDown && held > 0 && !consumed) {
            send(HexNetwork.TOGGLE);
            held = 0;
        } else if (game.screen != null && !consumed) {
            held = 0;
        }
        if (consumed && !domainDown && game.screen == null && !HexWheel.physicallyHeld(DOMAIN)) {
            held = 0;
            consumed = false;
        }

        boolean spellDown = SPELL.isDown();
        boolean spellTap = SPELL.consumeClick();
        while (SPELL.consumeClick()) {}
        if (casting && (!spellDown || game.screen != null)) {
            send(HexNetwork.RELEASE);
            casting = false;
        } else if (spellTap && !casting && game.screen == null) {
            send(HexNetwork.PRESS);
            casting = spellDown;
            if (!casting) send(HexNetwork.RELEASE);
        }
    }

    private static void send(int action) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(connection, HexNetwork.Input.TYPE.id())) return;
        var quality = CelestiaConfig.QUALITY.get();
        PacketDistributor.sendToServer(new HexNetwork.Input(action, selected.ordinal(),
                quality == CelestiaConfig.Quality.ULTRA_SCARLET || quality == CelestiaConfig.Quality.CINEMATIC_OVERDRIVE));
    }

    static boolean canCast() {
        var game = Minecraft.getInstance();
        var scene = game.player == null ? null : DOMAINS.get(game.player.getUUID());
        return scene != null && (scene.kind() == HexNetwork.ACTIVE || scene.kind() == HexNetwork.ENHANCED);
    }

    static HexNetwork.Scene own() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : DOMAINS.get(player.getUUID());
    }

    static double clock(float partial) {
        return world == null ? 0 : world.getGameTime() + partial;
    }

    static float reach(HexNetwork.Scene scene, double time) {
        float progress = Mth.clamp((float) ((time - scene.start()) / Math.max(1, scene.duration())), 0, 1);
        return scene.radius() * switch (scene.kind()) {
            case HexNetwork.AWAKENING -> 0;
            case HexNetwork.EXPANDING -> progress * progress * (3 - 2 * progress);
            case HexNetwork.COLLAPSING -> scene.variant() / 1000F * (1 - progress);
            default -> 1;
        };
    }

    static float atmosphere(Vec3 camera, double now) {
        float intensity = 0;
        for (var scene : DOMAINS.values()) {
            float radius = reach(scene, now);
            if (radius <= .1F) continue;
            double distance = camera.subtract(scene.origin()).horizontalDistance();
            float inside = Mth.clamp((radius - (float) distance) / 13, 0, 1);
            intensity = Math.max(intensity, inside * (scene.kind() == HexNetwork.ENHANCED ? 1 : .75F));
        }
        return intensity * HexConfig.FOG.get().floatValue();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && (!DOMAINS.isEmpty() || !EFFECTS.isEmpty())) {
            HexRenderer.world(event, DOMAINS.values(), EFFECTS.values());
        }
    }

    @SubscribeEvent
    public static void hands(RenderHandEvent event) {
        var scene = own();
        if (scene != null && scene.kind() != HexNetwork.COLLAPSING) HexRenderer.hands(event, casting);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor event) {
        float tint = atmosphere(event.getCamera().getPosition(), clock((float) event.getPartialTick()));
        if (tint <= 0) return;
        event.setRed(Mth.lerp(tint, event.getRed(), .55F));
        event.setGreen(Mth.lerp(tint, event.getGreen(), .021F));
        event.setBlue(Mth.lerp(tint, event.getBlue(), .072F));
    }

    @SubscribeEvent
    public static void depth(ViewportEvent.RenderFog event) {
        float tint = atmosphere(event.getCamera().getPosition(), clock((float) event.getPartialTick()));
        if (tint <= 0 || event.getType() != net.minecraft.world.level.material.FogType.NONE) return;
        event.scaleNearPlaneDistance(1 - .7F * tint);
        event.scaleFarPlaneDistance(1 - .39F * tint);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void lens(ViewportEvent.ComputeCameraAngles event) {
        if (CelestiaConfig.REDUCED_MOTION.get() || !HexConfig.POST_EFFECTS.get()) return;
        float tint = atmosphere(event.getCamera().getPosition(), clock((float) event.getPartialTick()));
        if (tint > 0) event.setRoll(event.getRoll() + tint * .65F * Mth.sin((float) (clock(0) % 3600) * .09F));
    }

    static void selected(HexSpell spell) {
        selected = spell;
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(spell.title), true);
    }
}
