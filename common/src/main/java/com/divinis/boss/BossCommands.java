package com.divinis.boss;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.divinis.Divinis;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.RegisterCommandsEvent;

/**
 * For trying bosses' attacks (operators only): /bossattack <boss> <attack> - it starts that attack at once.
 * Nocthariel: devour, draw, maws, black_sun, chorus, arms, feast (it has to be awake).
 * Sphinx: tornadoes, pounce, gaze, haboob, gale, vortex, wrath, riddle.
 * Arachnid: pounce, web, stomp, molt (the change to its second phase), drop, brood, cocoon, rain.
 * Barrow Chieftain: thrust, guard, throw, chariot (the change to his second phase), charge, circle, guardians, spears.
 * Hydra: fire, acid, ice, tail, cut0..cut8 (cuts that head off), sink (the change to its second phase), dive, breaths, maw.
 * Azramond: soulfire, raise, frost, wings (the change to his second phase), lands, shades, curse, dance.
 * Ignaroth: breath, tail, wings, takeoff (the change to his second phase), bomb, meteors, dive, knockdown.
 * The Nameless Creator: starburst, slashes, ring, bloom, collapse, chart, crack (the change to its second phase);
 * crush, judgement, punches, sparks, supernova, sword, echo, burn (the change to its third phase);
 * lslash, chronos, cages, chunks, unveil (the change to its true form); erase, checker, gigabeam, creation.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class BossCommands {
    private BossCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bossattack").requires(s -> s.hasPermission(2))
                .then(Commands.argument("boss", EntityArgument.entity())
                        .then(Commands.argument("attack", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[] {"devour", "draw", "maws", "black_sun", "chorus", "arms", "feast",
                                                "pound", "harpoon", "gears", "geysers", "sweep", "rush", "whiteout",
                                                "rift", "flicker", "iai", "backstep", "cross", "flurry", "cuts",
                                                "tornadoes", "pounce", "gaze", "haboob", "gale", "vortex", "wrath", "riddle",
                                                "web", "stomp", "molt", "drop", "brood", "cocoon", "rain",
                                                "thrust", "guard", "throw", "chariot", "charge", "circle", "guardians", "spears",
                                                "fire", "acid", "ice", "tail", "cut0", "cut1", "cut2", "sink", "dive", "breaths", "maw",
                                                "soulfire", "raise", "frost", "wings", "lands", "shades", "curse", "dance",
                                                "breath", "takeoff", "bomb", "meteors", "knockdown",
                                                "starburst", "slashes", "ring", "bloom", "collapse", "chart", "crack",
                                                "crush", "judgement", "punches", "sparks", "supernova", "sword", "echo", "burn",
                                                "lslash", "chronos", "cages", "chunks", "unveil",
                                                "erase", "checker", "gigabeam", "creation"}, b))
                                .executes(BossCommands::run))));
    }

    private static int run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity boss = EntityArgument.getEntity(c, "boss");
        String attack = StringArgumentType.getString(c, "attack");
        boolean ok = boss instanceof Nocthariel n && n.forceAttack(attack) || boss instanceof EvilBrother eb && eb.forceAttack(attack)
                || boss instanceof Ronin r && r.forceAttack(attack) || boss instanceof Sphinx s && s.forceAttack(attack)
                || boss instanceof Arachnid a && a.forceAttack(attack) || boss instanceof BarrowChieftain bc && bc.forceAttack(attack)
                || boss instanceof Hydra h && h.forceAttack(attack) || boss instanceof Azramond az && az.forceAttack(attack)
                || boss instanceof Ignaroth ig && ig.forceAttack(attack) || boss instanceof NamelessCreator nc && nc.forceAttack(attack);
        if (ok) c.getSource().sendSuccess(() -> Component.literal("Attack " + attack + " started"), false);
        else c.getSource().sendFailure(Component.literal("That boss can't start " + attack + " now (is it awake?)"));
        return ok ? 1 : 0;
    }
}
