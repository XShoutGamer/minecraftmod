package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.logging.LogUtils;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.nail.NailImpact;
import io.github.xshoutgamer.minecraftmod.nail.CelestialNail;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;

/** A short, deduplicated pulse whose clock starts on the first drawn frame. */
public final class ImpactPulse {
    private static final double NANOS_PER_TICK = 50_000_000;
    private static final long MAX_PENDING_NANOS = 1_250_000_000L;
    private static final LinkedHashSet<UUID> SEEN = new LinkedHashSet<>();
    private static ClientLevel world;
    private static Pulse active;

    record Sample(Vec3 source, float intensity) {}
    private record Pulse(Vec3 source, int entityId, float strength, int ticks, int recovery, long received, long started) {}

    private ImpactPulse() {}

    public static void receive(NailImpact packet) {
        var mc = Minecraft.getInstance();
        checkWorld(mc.level);
        if (world == null || !world.dimension().location().equals(packet.dimension())) return;
        if (!SEEN.add(packet.nail())) return;
        if (SEEN.size() > 64) SEEN.removeFirst();
        diagnostics("received impact " + packet.nail());
        if (!CelestiaConfig.INVERT.get() || CelestiaConfig.REDUCED_FLASH.get()) {
            diagnostics("inversion skipped by client accessibility settings");
            return;
        }
        if (active != null) return;
        Vec3 source = Vec3.atBottomCenterOf(packet.ground().above());
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(source);
        double fadeDistance = Math.min(540, CelestiaConfig.VISUAL_RANGE.get());
        float falloff = (float) Mth.clamp(1 - Math.max(0, distance - 120) / Math.max(1, fadeDistance - 120), 0, 1);
        float strength = falloff * CelestiaConfig.INVERT_INTENSITY.get().floatValue();
        if (strength < .01F) return;
        start(source, packet.entityId(), strength, CelestiaConfig.INVERT_TICKS.get(), CelestiaConfig.INVERT_RECOVERY.get());
    }

    static void preview() {
        var mc = Minecraft.getInstance();
        if (FMLLoader.isProduction() || mc.level == null || CelestiaConfig.REDUCED_FLASH.get()) return;
        checkWorld(mc.level);
        start(mc.gameRenderer.getMainCamera().getPosition().add(0, 0, 4), -1, .42F, 8, 5);
    }

    private static void start(Vec3 source, int entityId, float strength, int ticks, int recovery) {
        active = new Pulse(source, entityId, strength, ticks, Math.min(ticks, recovery), System.nanoTime(), 0);
        diagnostics("inversion queued for " + ticks + " ticks at " + strength);
    }

    static boolean pending() {
        if (active == null || CelestiaConfig.REDUCED_FLASH.get()) return false;
        long now = System.nanoTime();
        return active.started == 0 ? now - active.received < MAX_PENDING_NANOS
                : now - active.started < active.ticks * NANOS_PER_TICK;
    }

    static Sample sample() {
        checkWorld(Minecraft.getInstance().level);
        if (active == null) return null;
        long now = System.nanoTime();
        if (CelestiaConfig.REDUCED_FLASH.get() || (active.started == 0 && now - active.received > MAX_PENDING_NANOS)) {
            clear();
            return null;
        }
        if (active.started == 0) {
            if (active.entityId >= 0 && now - active.received < 100_000_000L
                    && world.getEntity(active.entityId) instanceof CelestialNail nail
                    && nail.phase().ordinal() < CelestialNail.Phase.IMPACTING.ordinal()) return null;
            active = new Pulse(active.source, active.entityId, active.strength, active.ticks, active.recovery, active.received, now);
            diagnostics("inversion began on rendered frame");
        }
        double elapsed = (now - active.started) / NANOS_PER_TICK;
        if (elapsed >= active.ticks) {
            clear();
            return null;
        }
        double t = Mth.clamp((elapsed - (active.ticks - active.recovery)) / active.recovery, 0, 1);
        return new Sample(active.source, active.strength * (float) (1 - t * t * (3 - 2 * t)));
    }

    static void checkWorld(ClientLevel current) {
        if (world == current) return;
        world = current;
        SEEN.clear();
        clear();
    }

    static void clear() {
        if (active != null) diagnostics("inversion cleared");
        active = null;
    }

    static void diagnostics(String detail) {
        if (!FMLLoader.isProduction() && Boolean.getBoolean("minecraftmod.cinematicDebug")) {
            LogUtils.getLogger().info("Celestia cinematic: {}", detail);
        }
    }
}
