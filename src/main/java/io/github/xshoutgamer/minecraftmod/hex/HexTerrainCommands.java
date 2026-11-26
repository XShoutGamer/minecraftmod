package io.github.xshoutgamer.minecraftmod.hex;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class HexTerrainCommands {
    private HexTerrainCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hexterrain")
                .requires(source -> source.getEntity() instanceof ServerPlayer player
                        && HexTerrain.canRestore(player))
                .then(Commands.literal("events").executes(context -> events(context.getSource())))
                .then(Commands.literal("restore-event")
                        .then(Commands.argument("event", UuidArgument.uuid())
                                .executes(context -> restoreEvent(context.getSource(), UuidArgument.getUuid(context, "event")))))
                .then(Commands.literal("keep")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> keep(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), false))
                                .then(Commands.literal("confirm").executes(context ->
                                        keep(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), true))))
                        .then(Commands.literal("all")
                                .executes(context -> keep(context.getSource(), 0, false))
                                .then(Commands.literal("confirm").executes(context -> keep(context.getSource(), 0, true))))));
    }

    private static int events(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var events = HexTerrain.get(player.serverLevel()).sovereigntyEvents(player.getUUID());
        if (events.isEmpty()) source.sendSuccess(() -> Component.literal("No Sovereignty events in this dimension."), false);
        int shown = 0;
        for (var entry : events.entrySet()) {
            if (shown++ >= 20) break;
            var id = entry.getKey();
            var line = Component.literal(id + " · " + entry.getValue() + " undo records")
                    .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/hexterrain restore-event " + id)));
            source.sendSuccess(() -> line, false);
        }
        return events.size();
    }

    private static int restoreEvent(CommandSourceStack source, UUID id) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        int count = HexTerrain.get(player.serverLevel()).expireEvent(player.getUUID(), id,
                Math.max(1, player.level().getGameTime())).size();
        if (count > 0) source.sendSuccess(() -> Component.literal("Scheduled " + count + " undo records for restoration as chunks are available."), false);
        else source.sendFailure(Component.literal("You have no records for that event in this dimension."));
        return count;
    }

    private static int keep(CommandSourceStack source, int radius, boolean confirm) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var journal = HexTerrain.get(player.serverLevel());
        int count = journal.keep(player.getUUID(), player.blockPosition(), radius, confirm);
        if (count == 0) {
            source.sendFailure(Component.literal("You have no terrain undo records in that area."));
        } else if (confirm) {
            source.sendSuccess(() -> Component.literal("Kept your terrain changes and freed " + count + " undo records."), false);
        } else {
            String selection = radius == 0 ? " in this dimension" : " within " + radius + " blocks of you";
            String argument = radius == 0 ? "all" : Integer.toString(radius);
            source.sendSuccess(() -> Component.literal("Found " + count + " of your undo records" + selection
                    + ". Run /hexterrain keep " + argument + " confirm to keep those changes permanently."), false);
        }
        return count;
    }
}
