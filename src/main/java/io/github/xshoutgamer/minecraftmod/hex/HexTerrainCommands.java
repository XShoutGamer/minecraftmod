package io.github.xshoutgamer.minecraftmod.hex;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class HexTerrainCommands {
    private HexTerrainCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hexterrain")
                .requires(source -> source.getEntity() instanceof ServerPlayer player
                        && HexTerrain.canRestore(player))
                .then(Commands.literal("keep")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> keep(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), false))
                                .then(Commands.literal("confirm").executes(context ->
                                        keep(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), true))))
                        .then(Commands.literal("all")
                                .executes(context -> keep(context.getSource(), 0, false))
                                .then(Commands.literal("confirm").executes(context -> keep(context.getSource(), 0, true))))));
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
