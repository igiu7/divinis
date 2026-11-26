package com.divinis.skill;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a player has an orb of the skill book in their fourth tree (SkillBooks' ids): on the server from their
 * character, on their own client from what the server told it (CLIENT_POINTS, CLIENT_BOOK - the client needs some,
 * e.g. how fast they mine or how long a bandage takes, to agree with the server).
 */
public final class Perks {
    private Perks() {}

    /** The client's copy of its own player's points (SkillTree order) and book; set by client.DashClient. */
    public static volatile int[] CLIENT_POINTS = new int[0];
    public static volatile String CLIENT_BOOK = "";

    public static boolean has(Player player, String orbId) {
        if (player == null) return false;
        String book;
        int points;
        if (player instanceof ServerPlayer sp) {
            CharacterData data = CharacterService.data(sp);
            book = data.book;
            points = data.points(SkillTree.BOOK);
        } else if (player.level().isClientSide() && player.isLocalPlayer()) { // the client knows only its own
            int[] p = CLIENT_POINTS;
            book = CLIENT_BOOK;
            points = p.length > SkillTree.BOOK.ordinal() ? p[SkillTree.BOOK.ordinal()] : 0;
        } else {
            return false;
        }
        return SkillBook.byId(book).map(b -> unlocked(b.orbs(), orbId, points)).orElse(false);
    }

    private static boolean unlocked(List<SkillOrb> orbs, String orbId, int points) {
        for (SkillOrb orb : orbs) if (orb.id().equals(orbId)) return points >= orb.points();
        return false;
    }

    /** Whether a buff of the book skills (SkillEffects) runs on the player now (server). */
    public static boolean buff(Player player, String id) {
        return player instanceof ServerPlayer sp && CharacterService.data(sp).buff(id);
    }

    /** The Hunter's animals: farm and wild animals, the bears. */
    public static boolean animal(Entity entity) {
        return entity instanceof Animal || com.divinis.Divinis.hooks().isAnimal(entity);
    }

    /** XP times this (the Scholar's book: Studious, Lecture, Study). */
    static double xpMultiplier(ServerPlayer player) {
        double m = 1;
        if (has(player, SkillBooks.STUDIOUS)) m += 0.10;
        CharacterData data = CharacterService.data(player);
        if (data.buff(SkillBooks.LECTURE)) m += 0.25;
        if (data.buff(SkillBooks.STUDY)) m += 0.50;
        return m;
    }
}
