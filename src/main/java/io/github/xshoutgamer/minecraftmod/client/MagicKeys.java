package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.xshoutgamer.minecraftmod.hex.HexNetwork;
import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic;
import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic.Power;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.lwjgl.glfw.GLFW;

final class MagicKeys {
    private static final KeyMapping QUICK = key("disintegrate", GLFW.GLFW_KEY_X);
    private static final KeyMapping MENU = key("chaos_menu", GLFW.GLFW_KEY_R);
    private static final KeyMapping CAST = key("chaos_cast", GLFW.GLFW_KEY_C);
    private static final KeyMapping BOOK = key("darkhold", GLFW.GLFW_KEY_J);
    private static final KeyMapping SECONDARY = key("darkhold_secondary", GLFW.GLFW_KEY_U);
    private static final KeyMapping DARK = key("darkhold_cast", GLFW.GLFW_KEY_K);
    private static final java.util.Set<KeyMapping> HELD = new java.util.HashSet<>();
    private static Power personal = Power.PROJECTION, dark = Power.ECLIPSE;
    private static int bookTicks;
    private static boolean opened, charging;

    private MagicKeys() {}

    static boolean reserved(KeyMapping mapping) { return mapping.same(MENU) || mapping.same(CAST); }

    static void register(RegisterKeyMappingsEvent event) {
        for (var key : new KeyMapping[]{QUICK, MENU, CAST, BOOK, DARK, SECONDARY}) event.register(key);
    }

    static void select(boolean grimoire, Power power) { if (grimoire) dark = power; else personal = power; }
    static Power selection(boolean grimoire) { return grimoire ? dark : personal; }

    static void tick(boolean summoned) {
        var game = Minecraft.getInstance();
        boolean menu = pressed(MENU), quick = pressed(QUICK), cast = pressed(CAST), scripture = pressed(DARK);
        boolean secondary = pressed(SECONDARY);
        boolean book = pressed(BOOK) || BOOK.isDown();
        if (!book && !HexWheel.physicallyHeld(BOOK)) {
            if (game.screen == null && !opened && bookTicks > 0) send(PersonalMagic.BOOK, dark);
            bookTicks = 0;
            opened = false;
        }
        if (charging && (!DARK.isDown() || game.screen != null || !summoned)) {
            if (summoned) send(PersonalMagic.GRIMOIRE_RELEASE, dark);
            charging = false;
        }
        if (game.screen != null || game.player == null) return;
        if (book && !opened && ++bookTicks >= 8 && summoned) {
            opened = true;
            game.setScreen(new GrimoireScreen(true));
        } else if (menu) {
            game.setScreen(new GrimoireScreen(false));
        } else if (quick) {
            send(PersonalMagic.QUICK, personal);
        } else if (cast) {
            send(PersonalMagic.PERSONAL, personal);
        } else if (secondary && summoned) {
            send(PersonalMagic.SECONDARY, dark);
        } else if (scripture && summoned) {
            send(PersonalMagic.GRIMOIRE, dark);
            charging = DARK.isDown();
            if (!charging) send(PersonalMagic.GRIMOIRE_RELEASE, dark);
        }
    }

    private static boolean pressed(KeyMapping key) {
        if (!key.isDown()) HELD.remove(key);
        boolean pressed = key.consumeClick();
        while (key.consumeClick()) {}
        return pressed && (!key.isDown() || HELD.add(key));
    }

    private static KeyMapping key(String name, int value) {
        return new KeyMapping("key.minecraftmod." + name, InputConstants.Type.KEYSYM, value, "key.categories.minecraftmod");
    }

    static void attune(PersonalMagic.Attunement attunement) { send(PersonalMagic.LIBRARY_SELECT, attunement.ordinal()); }

    private static void send(int action, Power power) { send(action, power.ordinal()); }

    private static void send(int action, int spell) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && NetworkRegistry.hasChannel(connection, HexNetwork.Input.TYPE.id())) {
            PacketDistributor.sendToServer(new HexNetwork.Input(action, spell, false));
        }
    }
}
