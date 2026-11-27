package io.github.xshoutgamer.minecraftmod.hex;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ScarletAccess extends SavedData {
    private static final Factory<ScarletAccess> FACTORY = new Factory<>(ScarletAccess::new, ScarletAccess::load);
    private final Set<UUID> empowered = new HashSet<>();

    public static boolean hasPowers(MinecraftServer server, UUID player) {
        return get(server).empowered.contains(player);
    }

    public static boolean requirePowers(ServerPlayer player) {
        if (hasPowers(player.serverLevel().getServer(), player.getUUID())) return true;
        player.displayClientMessage(Component.literal("A server operator must grant you these powers."), true);
        return false;
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("scarletwitch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("empower").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(context -> update(context, true))))
                .then(Commands.literal("revoke").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(context -> update(context, false)))));
    }

    private static int update(CommandContext<CommandSourceStack> context, boolean grant) throws CommandSyntaxException {
        var source = context.getSource();
        var server = source.getServer();
        var access = get(server);
        int count = 0;
        for (var profile : GameProfileArgument.getGameProfiles(context, "player")) {
            var id = profile.getId();
            boolean changed = grant ? access.empowered.add(id) : access.empowered.remove(id);
            if (!changed) {
                source.sendFailure(Component.literal(profile.getName() + (grant ? " already has these powers." : " already lacks these powers.")));
                continue;
            }
            access.setDirty();
            if (!grant) Heavenfall.revoke(server, id);
            var online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                if (!grant) {
                    HexDomains.revoke(online);
                    PersonalMagic.revoke(online);
                    ScarletSovereignty.clear(online);
                }
                online.sendSystemMessage(Component.literal(grant
                        ? "You can now use Scarlet Witch powers and the Celestial Nail."
                        : "Your Scarlet Witch powers and Celestial Nail access have been revoked."));
            }
            source.sendSuccess(() -> Component.literal((grant ? "Granted powers to " : "Revoked powers from ") + profile.getName()), true);
            count++;
        }
        return count;
    }

    private static ScarletAccess get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "minecraftmod_scarlet_access");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new ListTag();
        for (var id : empowered) players.add(NbtUtils.createUUID(id));
        tag.put("Players", players);
        return tag;
    }

    private static ScarletAccess load(CompoundTag tag, HolderLookup.Provider registries) {
        var access = new ScarletAccess();
        for (var id : tag.getList("Players", Tag.TAG_INT_ARRAY)) {
            if (id instanceof net.minecraft.nbt.IntArrayTag array && array.size() == 4) {
                access.empowered.add(NbtUtils.loadUUID(id));
            }
        }
        return access;
    }
}
