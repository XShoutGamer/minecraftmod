package io.github.xshoutgamer.minecraftmod.nail;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NailCooldown extends SavedData {
    private static final Factory<NailCooldown> FACTORY = new Factory<>(NailCooldown::new,
            (tag, registries) -> new NailCooldown(tag.getBoolean("Disabled")));

    private boolean disabled;

    private NailCooldown() {}

    private NailCooldown(boolean disabled) {
        this.disabled = disabled;
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("celestia")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cooldown")
                        .executes(context -> report(context.getSource(), false))
                        .then(Commands.literal("enable").executes(context -> set(context.getSource(), true)))
                        .then(Commands.literal("disable").executes(context -> set(context.getSource(), false)))));
    }

    static boolean enabled(MinecraftServer server) {
        return !get(server).disabled;
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        var cooldown = get(source.getServer());
        cooldown.disabled = !enabled;
        cooldown.setDirty();
        return report(source, true);
    }

    private static NailCooldown get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "minecraftmod_nail_cooldown");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Disabled", disabled);
        return tag;
    }

    private static int report(CommandSourceStack source, boolean broadcast) {
        boolean enabled = enabled(source.getServer());
        source.sendSuccess(() -> Component.literal("Celestial Nail cooldown is " + (enabled ? "enabled." : "disabled.")), broadcast);
        return broadcast || enabled ? 1 : 0;
    }
}
