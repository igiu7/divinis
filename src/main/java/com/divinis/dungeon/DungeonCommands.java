package com.divinis.dungeon;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.divinis.util.Msg;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 *   /divinis dungeon leave             give up the run and go home
 *   /divinis admin dungeon portal      open a dungeon portal in front of you (as the Guild Master does)
 *   /divinis admin dungeon endall      end every run, everyone inside goes home
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class DungeonCommands {
    private DungeonCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("divinis")
                .then(Commands.literal("dungeon").then(Commands.literal("leave").executes(DungeonCommands::leave)))
                .then(Commands.literal("admin").then(Commands.literal("dungeon").requires(s -> s.hasPermission(3))
                        .then(Commands.literal("portal").executes(DungeonCommands::portal))
                        .then(Commands.literal("endall").executes(DungeonCommands::endAll)))));
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (!DungeonService.leaveCommand(player)) {
            player.sendSystemMessage(Msg.text("You are not in a dungeon.", ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int portal(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (DungeonService.isDungeon(player.level())) {
            player.sendSystemMessage(Msg.text("Not inside a dungeon.", ChatFormatting.RED));
            return 0;
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 at = player.position().add(look.scale(4));
        boolean ok = DungeonService.open(player, (ServerLevel) player.level(), at, player.getYRot() + 180, null);
        return ok ? 1 : 0;
    }

    private static int endAll(CommandContext<CommandSourceStack> c) {
        int n = DungeonService.endAll(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " dungeon run(s)."), true);
        return n;
    }
}
