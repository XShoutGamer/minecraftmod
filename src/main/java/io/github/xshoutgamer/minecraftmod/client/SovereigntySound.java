package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.MinecraftMod;
import io.github.xshoutgamer.minecraftmod.hex.HexConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexNetwork;
import io.github.xshoutgamer.minecraftmod.hex.ScarletSovereignty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class SovereigntySound extends AbstractTickableSoundInstance {
    private static final Map<UUID, SovereigntySound> ACTIVE = new HashMap<>();
    private final HexNetwork.Scene scene;

    private SovereigntySound(HexNetwork.Scene scene) {
        super(scene.variant() == 0 ? MinecraftMod.SOVEREIGN_TRANSFORMATION.get() : MinecraftMod.SOVEREIGN_QUICK.get(), SoundSource.PLAYERS,
                RandomSource.create());
        this.scene = scene;
        x = scene.target().x;
        y = scene.target().y;
        z = scene.target().z;
        volume = HexConfig.SOVEREIGN_AUDIO.get().floatValue();
        pitch = scene.variant() == 0 ? 1 : 120F / scene.duration();
        relative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(scene.owner());
        if (relative) {
            x = y = z = 0;
            attenuation = Attenuation.NONE;
        }
    }

    static void receive(HexNetwork.Scene scene) {
        var current = ACTIVE.get(scene.owner());
        if (current != null && current.scene.start() == scene.start()) return;
        if (HexConfig.SOVEREIGN_AUDIO.get() <= 0 || HexClient.clock(0) - scene.start() > 12) return;
        stop(scene.owner());
        var sound = new SovereigntySound(scene);
        ACTIVE.put(scene.owner(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    static void stop(UUID owner) {
        var sound = ACTIVE.remove(owner);
        if (sound != null) {
            sound.stop();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    static void clear() {
        for (var sound : ACTIVE.values()) sound.stop();
        ACTIVE.clear();
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().level == null || HexClient.clock(0) >= scene.start() + scene.duration()) {
            ACTIVE.remove(scene.owner(), this);
            stop();
        }
    }
}
