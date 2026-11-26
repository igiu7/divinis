package com.divinis.screenfx;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.divinis.net.ScreenFxPayload;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.Divinis;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.RegisterCommandsEvent;
import com.divinis.platform.net.PacketDistributor;

/**
 * Putting screen effects (ScreenEffect) on players' screens - for bosses, and the /screenfx command to try them
 * (operators only):
 *   /screenfx <effect> [seconds] [strength] [players] [color]                  on the whole screen
 *   /screenfx area <thing> <radius> <effect> [seconds] [strength] [players] [color]   in a circle round a thing
 *   /screenfx entity <thing> <effect> [seconds] [strength] [players] [color]   only on the thing (outline: its outline)
 *   /screenfx except <thing> <effect> [seconds] [strength] [players] [color]   everywhere but on the thing
 *   /screenfx text <seconds> <players> <words...>
 *   /screenfx clear [players]
 * e.g. /screenfx flash 1.5 1 @a ff4040, /screenfx except @e[type=divinis:serpent_of_infinity,limit=1] invert 5
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class ScreenFx {
    private ScreenFx() {}

    public static void play(ServerPlayer player, ScreenEffect effect, int ticks, float strength) {
        play(player, effect, ticks, strength, -1, "");
    }

    public static void play(ServerPlayer player, ScreenEffect effect, int ticks, float strength, int color, String text) {
        PacketDistributor.sendToPlayer(player, new ScreenFxPayload(effect.ordinal(), ticks, strength, color, text, Anchor.SCREEN.ordinal(), -1, 0));
    }

    /** On (or round, or everywhere but) a thing: anchor AREA takes a radius in blocks. */
    public static void playOn(ServerPlayer player, Entity on, Anchor anchor, float radius, ScreenEffect effect, int ticks, float strength, int color) {
        PacketDistributor.sendToPlayer(player, new ScreenFxPayload(effect.ordinal(), ticks, strength, color, "", anchor.ordinal(), on.getId(), radius));
    }

    /** For every player within radius of a place (a boss's room). */
    public static void playNear(ServerLevel level, Vec3 at, double radius, ScreenEffect effect, int ticks, float strength, int color) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) < radius * radius) play(p, effect, ticks, strength, color, "");
        }
    }

    /** The ground shakes: every player within radius of a place, less the further away. */
    public static void shake(ServerLevel level, Vec3 at, double radius, int ticks, float strength) {
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(at));
            if (d < radius && !p.isSpectator()) play(p, ScreenEffect.SHAKE, ticks, strength * (float) (1 - 0.6 * d / radius));
        }
    }

    /** On a thing, for every player within radius of it. */
    public static void playOnNear(Entity on, double radius, Anchor anchor, float areaRadius, ScreenEffect effect, int ticks, float strength,
            int color) {
        if (!(on.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(on) < radius * radius) playOn(p, on, anchor, areaRadius, effect, ticks, strength, color);
        }
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ScreenFxPayload(ScreenFxPayload.CLEAR, 0, 0, -1, "", 0, -1, 0));
    }

    // ------------------------------------------------------------------ /screenfx

    private static final SuggestionProvider<CommandSourceStack> EFFECTS = (c, b) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(ScreenEffect.values()).map(ScreenEffect::id), b);

    /** Where a command puts its effect. */
    private interface Where {
        @Nullable Entity entity(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        Where screen = c -> null;
        Where thing = c -> EntityArgument.getEntity(c, "thing");
        event.getDispatcher().register(Commands.literal("screenfx").requires(s -> s.hasPermission(2))
                .then(Commands.literal("clear")
                        .executes(c -> clear(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> clear(c, EntityArgument.getPlayers(c, "players")))))
                .then(Commands.literal("text")
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.05F, 600))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("words", StringArgumentType.greedyString())
                                                .executes(c -> send(c, ScreenEffect.TEXT, FloatArgumentType.getFloat(c, "seconds"), 1,
                                                        EntityArgument.getPlayers(c, "players"), -1,
                                                        StringArgumentType.getString(c, "words"), Anchor.SCREEN, null, 0))))))
                .then(Commands.literal("area").then(Commands.argument("thing", EntityArgument.entity())
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5F, 128))
                                .then(effectArgs(Anchor.AREA, thing)))))
                .then(Commands.literal("entity").then(Commands.argument("thing", EntityArgument.entity()).then(effectArgs(Anchor.ENTITY, thing))))
                .then(Commands.literal("except").then(Commands.argument("thing", EntityArgument.entity()).then(effectArgs(Anchor.EXCEPT, thing))))
                .then(effectArgs(Anchor.SCREEN, screen)));
    }

    /** effect [seconds] [strength] [players] [color] */
    private static RequiredArgumentBuilder<CommandSourceStack, String> effectArgs(Anchor anchor, Where where) {
        return Commands.argument("effect", StringArgumentType.word()).suggests(EFFECTS)
                .executes(c -> run(c, anchor, where, 3, 1, null, null))
                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.05F, 600))
                        .executes(c -> run(c, anchor, where, FloatArgumentType.getFloat(c, "seconds"), 1, null, null))
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0, 3))
                                .executes(c -> run(c, anchor, where, FloatArgumentType.getFloat(c, "seconds"),
                                        FloatArgumentType.getFloat(c, "strength"), null, null))
                                .then(withPlayers(anchor, where))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> withPlayers(Anchor anchor, Where where) {
        return Commands.argument("players", EntityArgument.players())
                .executes(c -> run(c, anchor, where, FloatArgumentType.getFloat(c, "seconds"), FloatArgumentType.getFloat(c, "strength"),
                        EntityArgument.getPlayers(c, "players"), null))
                .then(Commands.argument("color", StringArgumentType.word())
                        .executes(c -> run(c, anchor, where, FloatArgumentType.getFloat(c, "seconds"), FloatArgumentType.getFloat(c, "strength"),
                                EntityArgument.getPlayers(c, "players"), StringArgumentType.getString(c, "color"))));
    }

    private static int run(CommandContext<CommandSourceStack> c, Anchor anchor, Where where, float seconds, float strength,
            @Nullable Collection<ServerPlayer> players, @Nullable String color) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "effect");
        ScreenEffect effect = ScreenEffect.byId(id);
        if (effect == null) {
            c.getSource().sendFailure(Component.literal("Unknown effect: " + id));
            return 0;
        }
        int rgb = -1;
        if (color != null) {
            try {
                rgb = Integer.parseInt(color.startsWith("#") ? color.substring(1) : color, 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                c.getSource().sendFailure(Component.literal("Colour as hex, e.g. ff4040"));
                return 0;
            }
        }
        float radius = anchor == Anchor.AREA ? FloatArgumentType.getFloat(c, "radius") : 0;
        return send(c, effect, seconds, strength, players != null ? players : List.of(c.getSource().getPlayerOrException()), rgb, "",
                anchor, where.entity(c), radius);
    }

    private static int send(CommandContext<CommandSourceStack> c, ScreenEffect effect, float seconds, float strength,
            Collection<ServerPlayer> players, int color, String text, Anchor anchor, @Nullable Entity on, float radius) {
        int ticks = Math.max(1, Math.round(seconds * 20));
        for (ServerPlayer p : players) {
            if (on == null) play(p, effect, ticks, strength, color, text);
            else playOn(p, on, anchor, radius, effect, ticks, strength, color);
        }
        String where = on == null ? "" : " (" + anchor.name().toLowerCase(java.util.Locale.ROOT) + " " + on.getName().getString() + ")";
        c.getSource().sendSuccess(() -> Component.literal("Screen effect " + effect.id() + where + " for " + seconds + " s on "
                + players.size() + " player(s)").withStyle(ChatFormatting.GRAY), false);
        return players.size();
    }

    private static int clear(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) clear(p);
        c.getSource().sendSuccess(() -> Component.literal("Screen effects cleared").withStyle(ChatFormatting.GRAY), false);
        return players.size();
    }
}
