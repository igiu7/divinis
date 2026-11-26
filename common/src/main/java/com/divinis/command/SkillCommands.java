package com.divinis.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.divinis.skill.CharacterService;
import com.divinis.skill.SkillCurve;
import com.divinis.skill.SkillTree;
import com.divinis.util.Errors;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /divinis skills                               - your level and skill points
 * /divinis skills assign <tree> [amount]        - put skill points into str, dex, int or book
 * /divinis skills book insert <slot>            - put the skill book from that inventory slot into the fourth tree
 * /divinis skills book remove                   - take it out (its points come back)
 * /divinis skills use <skill>                   - use a skill of the skill book in the fourth tree (skill.SkillEffects)
 * /divinis skills reset                         - all points back, once every 24 hours
 * /divinis skills admin reset <player>          - admins (permission level 2): reset without the 24 hours (nor starting them)
 * /divinis skills admin cooldown <player>       - the player may reset again right away
 * /divinis skills admin level <player> <level>  - set the character level
 * /divinis skills admin xp <player> <amount>    - give XP
 * The skill screen (client.gui.SkillTreeScreen) runs these.
 */
public final class SkillCommands {
    private SkillCommands() {}

    @com.divinis.platform.bus.SubscribeEvent
    public static void register(com.divinis.platform.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("divinis").then(build()));
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("skills")
                .executes(c -> CommandUtil.run(c, CharacterService::info))
                .then(Commands.literal("assign")
                        .then(Commands.argument("tree", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(SkillTree.values()).map(SkillTree::id), b))
                                .executes(c -> assign(c, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, SkillTree.MAX_POINTS))
                                        .executes(c -> assign(c, IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("book")
                        .then(Commands.literal("insert")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(c -> CommandUtil.run(c, p -> CharacterService.insertBook(p,
                                                IntegerArgumentType.getInteger(c, "slot"))))))
                        .then(Commands.literal("remove").executes(c -> CommandUtil.run(c, CharacterService::removeBook))))
                .then(Commands.literal("use")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(com.divinis.skill.SkillEffects.ids(), b))
                                .executes(c -> CommandUtil.run(c, p -> com.divinis.skill.SkillEffects.use(p,
                                        StringArgumentType.getString(c, "skill"))))))
                .then(Commands.literal("reset").executes(c -> CommandUtil.run(c, p -> CharacterService.reset(p, false))))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> {
                                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                            CharacterService.reset(target, true);
                                            return done(c, target.getGameProfile().getName() + ": skill points reset.");
                                        })))
                        .then(Commands.literal("cooldown")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> {
                                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                            CharacterService.clearCooldown(target);
                                            return done(c, target.getGameProfile().getName() + " may reset their points again.");
                                        })))
                        .then(Commands.literal("level")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, SkillCurve.MAX_LEVEL))
                                                .executes(c -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                                    int level = IntegerArgumentType.getInteger(c, "level");
                                                    CharacterService.setLevel(target, level);
                                                    return done(c, target.getGameProfile().getName() + " is now level " + level + ".");
                                                }))))
                        .then(Commands.literal("xp")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(c -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                                                    double xp = DoubleArgumentType.getDouble(c, "amount");
                                                    CharacterService.giveXp(target, xp);
                                                    return done(c, target.getGameProfile().getName() + " got " + xp + " XP.");
                                                })))));
    }

    private static int assign(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "tree");
        SkillTree tree = SkillTree.byId(id).orElseThrow(() -> Errors.of("Unknown skill tree: " + id));
        return CommandUtil.run(c, p -> CharacterService.assign(p, tree, amount));
    }

    private static int done(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}
