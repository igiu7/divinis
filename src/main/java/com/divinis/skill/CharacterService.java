package com.divinis.skill;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.divinis.util.Errors;
import com.divinis.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The character: one level (SkillCurve) earned by all the work (SkillService.addXp), POINTS_PER_LEVEL skill points
 * a level, put by the player into the skill trees (SkillTree), an orb every SkillTree.POINTS_PER_ORB points. Points
 * stay where they were put; all of them come back with a reset, once every RESET_COOLDOWN (admins: forceReset).
 * The fourth tree takes a skill book from the inventory: its points come back when the book is taken out or
 * replaced.
 */
public final class CharacterService {
    public static final int POINTS_PER_LEVEL = 2;
    public static final long RESET_COOLDOWN = 24L * 60 * 60 * 1000;

    private CharacterService() {}

    public static CharacterData data(ServerPlayer player) {
        return player.getData(SkillAttachments.CHARACTER);
    }

    private static void save(ServerPlayer player, CharacterData data) {
        player.setData(SkillAttachments.CHARACTER, data);
        com.divinis.Divinis.hooks().characterSaved(player); // the world's own copy of it
        Dash.sync(player);
        com.divinis.net.DivinisNet.sendCharacter(player);
    }

    public static int totalPoints(int level) {
        return Math.max(0, level - 1) * POINTS_PER_LEVEL;
    }

    public static int freePoints(CharacterData data) {
        return totalPoints(data.level()) - data.spent();
    }

    /** Milliseconds until the player may reset their points again (0 when they may). */
    public static long resetWait(CharacterData data) {
        return Math.max(0, data.lastReset + RESET_COOLDOWN - System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ XP

    /** Adds XP to the character (already scaled) and announces a new level; source names where it came from. */
    static void addXp(ServerPlayer player, double amount, String source) {
        CharacterData data = data(player);
        int before = data.level();
        if (before >= SkillCurve.MAX_LEVEL) return;
        data.xp = Math.min(data.xp + amount, SkillCurve.totalFor(SkillCurve.MAX_LEVEL));
        save(player, data);
        int after = data.level();
        if (after > before) {
            levelUp(player, before, after);
        } else if (com.divinis.Config.SHOW_XP_GAIN.get()) {
            String gain = amount >= 10 ? String.valueOf(Math.round(amount)) : String.format("%.1f", amount);
            player.displayClientMessage(Component.literal("+" + gain + " XP (" + source + ")  ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Lv " + after + "  " + SkillCurve.progressInLevel(data.xp) + " / "
                            + SkillCurve.needFor(after)).withStyle(ChatFormatting.GRAY)), true);
        }
    }

    private static void levelUp(ServerPlayer player, int before, int after) {
        com.divinis.Divinis.hooks().levelChanged(player);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
        int points = totalPoints(after) - totalPoints(before);
        player.sendSystemMessage(Msg.text("Level up! You are now level " + after + " (+" + points + " skill points"
                + " - open your skills to spend them).", ChatFormatting.GOLD));
        if (after == SkillCurve.MAX_LEVEL) {
            Msg.broadcast(player.getServer(), Msg.text(player.getGameProfile().getName() + " has reached the highest level ("
                    + SkillCurve.MAX_LEVEL + ")!", ChatFormatting.GOLD));
        }
    }

    /** Brings the XP of the old professions (SkillData) into the character once: all of it, added up. */
    public static void migrate(ServerPlayer player) {
        CharacterData data = data(player);
        if (data.migrated) return;
        SkillData old = player.getData(SkillAttachments.SKILLS);
        double sum = 0;
        for (Skill s : Skill.values()) sum += old.xp(s);
        data.xp = Math.min(Math.max(data.xp, sum), SkillCurve.totalFor(SkillCurve.MAX_LEVEL));
        data.migrated = true;
        save(player, data);
        com.divinis.Divinis.hooks().levelChanged(player);
        if (sum > 0) {
            player.sendSystemMessage(Msg.text("Your profession XP became your character's: you are level " + data.level()
                    + " with " + freePoints(data) + " skill points to spend.", ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ points

    public static void assign(ServerPlayer player, SkillTree tree, int amount) throws CommandSyntaxException {
        CharacterData data = data(player);
        if (amount <= 0) throw Errors.of("Assign at least one point.");
        if (tree == SkillTree.BOOK && SkillBook.byId(data.book).isEmpty()) throw Errors.of("Put a skill book into the fourth tree first.");
        int free = freePoints(data);
        if (free <= 0) throw Errors.of("You have no skill points left.");
        int have = data.points(tree), max = tree.maxPoints(data.book);
        if (have >= max) throw Errors.of("The " + tree.title + " tree is full.");
        int add = Math.min(amount, Math.min(free, max - have));
        data.points[tree.ordinal()] = have + add;
        save(player, data);
        for (SkillOrb orb : tree.orbs(data.book)) {
            if (orb.points() > have && orb.points() <= have + add) {
                player.sendSystemMessage(Msg.text("Unlocked " + orb.name() + " of " + tree.title + ".", ChatFormatting.AQUA));
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
    }

    /** Takes back every point; once every RESET_COOLDOWN unless forced (admins). */
    public static void reset(ServerPlayer player, boolean force) throws CommandSyntaxException {
        CharacterData data = data(player);
        if (data.spent() == 0) throw Errors.of(force ? "No points are assigned." : "You have no points assigned.");
        long wait = resetWait(data);
        if (!force && wait > 0) throw Errors.of("You can reset your points again in " + com.divinis.util.Names.duration((int) (wait / 1000)) + ".");
        java.util.Arrays.fill(data.points, 0);
        if (!force) data.lastReset = System.currentTimeMillis();
        save(player, data);
        player.sendSystemMessage(Msg.text("Your skill points were reset: " + freePoints(data) + " to spend.", ChatFormatting.GOLD));
    }

    /** Admin: the player may reset their points again right away. */
    public static void clearCooldown(ServerPlayer player) {
        CharacterData data = data(player);
        data.lastReset = 0;
        save(player, data);
    }

    // ------------------------------------------------------------------ the skill book

    /** Puts the skill book from the player's inventory slot into the fourth tree; a book already there comes back. */
    public static void insertBook(ServerPlayer player, int slot) throws CommandSyntaxException {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) throw Errors.of("No such slot.");
        ItemStack stack = player.getInventory().getItem(slot);
        SkillBook book = SkillBook.of(stack).orElseThrow(() -> Errors.of("That is not a skill book."));
        CharacterData data = data(player);
        stack.shrink(1);
        player.getInventory().setChanged();
        String old = data.book;
        int refunded = data.points(SkillTree.BOOK);
        data.book = book.id();
        data.points[SkillTree.BOOK.ordinal()] = 0;
        save(player, data);
        SkillBook.byId(old).ifPresent(b -> player.getInventory().placeItemBackInInventory(b.stack()));
        player.sendSystemMessage(Msg.text("You opened the skill book " + book.name() + "."
                + (refunded > 0 ? " The " + refunded + " points of the old book came back." : ""), ChatFormatting.AQUA));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Takes the skill book out of the fourth tree back into the inventory; its points come back. */
    public static void removeBook(ServerPlayer player) throws CommandSyntaxException {
        CharacterData data = data(player);
        SkillBook book = SkillBook.byId(data.book).orElseThrow(() -> Errors.of("There is no skill book in your fourth tree."));
        int refunded = data.points(SkillTree.BOOK);
        data.book = "";
        data.points[SkillTree.BOOK.ordinal()] = 0;
        save(player, data);
        player.getInventory().placeItemBackInInventory(book.stack());
        player.sendSystemMessage(Msg.text("You closed the skill book " + book.name() + "."
                + (refunded > 0 ? " Its " + refunded + " points came back." : ""), ChatFormatting.AQUA));
    }

    // ------------------------------------------------------------------ info, admin

    public static void info(ServerPlayer player) {
        CharacterData data = data(player);
        int level = data.level();
        StringBuilder sb = new StringBuilder("Level " + level);
        if (level < SkillCurve.MAX_LEVEL) {
            sb.append(" (").append(SkillCurve.progressInLevel(data.xp)).append(" / ").append(SkillCurve.needFor(level)).append(" XP)");
        } else {
            sb.append(" (max)");
        }
        sb.append("\nSkill points: ").append(freePoints(data)).append(" free of ").append(totalPoints(level));
        for (SkillTree t : SkillTree.values()) {
            if (t == SkillTree.BOOK && SkillBook.byId(data.book).isEmpty()) continue;
            sb.append("\n - ").append(t.abbr).append(t == SkillTree.BOOK ? " (" + SkillBook.byId(data.book).get().name() + ")" : "")
                    .append(": ").append(data.points(t)).append(" / ").append(t.maxPoints(data.book));
        }
        Msg.info(player, sb.toString());
    }

    /** Admin: the player at the start of a level; points over what the level gives come back. */
    public static void setLevel(ServerPlayer target, int level) {
        CharacterData data = data(target);
        data.xp = SkillCurve.totalFor(level);
        if (freePoints(data) < 0) java.util.Arrays.fill(data.points, 0);
        save(target, data);
        com.divinis.Divinis.hooks().levelChanged(target);
    }

    /** Admin: XP straight to the character (no multiplier). */
    public static void giveXp(ServerPlayer target, double amount) {
        addXp(target, amount, "admin");
    }
}
