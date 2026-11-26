package com.divinis.skill;

import java.util.Locale;
import java.util.Optional;

/**
 * Where the character's XP comes from (SkillService.addXp), each with its config multiplier. These were once
 * professions with their own levels (SkillData, kept only to bring that XP over); now all of it goes into the one
 * character level (CharacterService).
 */
public enum Skill {
    BLACKSMITH("Blacksmith", "Make tools, weapons and armor at the Armory Table or crafting table, smelt metal in the bloomery and steel furnace (better materials give more)"),
    FARMER("Farmer", "Harvest ripe crops, flax, melons, pumpkins, cane, berries and medicinal herbs, and breed animals"),
    MINER("Miner", "Mine stone and ores with a pickaxe (rarer ores and harder stone give more)"),
    HUNTER("Hunter", "Hunt wolf packs, bears and wild animals, and catch fish with a fishing rod (bears give the most)");

    public final String title;
    public final String description;

    Skill(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Skill> byId(String id) {
        for (Skill s : values()) {
            if (s.id().equalsIgnoreCase(id)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
