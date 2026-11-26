package com.divinis.skill;

import java.util.List;

/**
 * One orb on a skill tree's bar, reached at `points` points in that tree: a passive (its modifiers, lines shown in the
 * Modifiers window) or a skill (its skills, shown in the Skills window). `id` names what it does (Perks, SkillEffects;
 * "" for an orb with nothing in it yet).
 */
public record SkillOrb(int points, String name, boolean skill, List<String> modifiers, List<String> skills, String id) {

    /** An orb with nothing in it yet: a passive, or (every second orb) a skill. */
    static SkillOrb empty(int index) {
        boolean skill = index % 2 == 1;
        return new SkillOrb((index + 1) * SkillTree.POINTS_PER_ORB, skill ? "Skill " + roman(index / 2 + 1) : "Passive " + roman(index / 2 + 1),
                skill, List.of(), List.of(), "");
    }

    /** A passive at the index-th orb (0 = the first, at POINTS_PER_ORB points). */
    static SkillOrb passive(int index, String id, String name, String... modifiers) {
        return new SkillOrb((index + 1) * SkillTree.POINTS_PER_ORB, name, false, List.of(modifiers), List.of(), id);
    }

    /** A skill at the index-th orb. */
    static SkillOrb skill(int index, String id, String name, String... lines) {
        return new SkillOrb((index + 1) * SkillTree.POINTS_PER_ORB, name, true, List.of(), List.of(lines), id);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }
}
