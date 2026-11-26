package com.divinis.skill;

/**
 * The character's levels, 1..60 (CharacterData). XP needed to go from level L to L+1 is round(60 * L^1.6) + 60:
 * level 2 needs 120 XP, level 10 about 8,500 XP in total, level 20 about 53,000, level 30 about 155,000, level 45
 * about 450,000 and level 60 about 970,000. All the XP of the work (XpRules: smithing, farming, mining, hunting) goes
 * into the one level, roughly 2,500-4,000 XP an hour of focused work.
 * The old professions (SkillData) used the same curve; version 1 of it was round(50 * L^1.5) + 50, XP saved with it
 * is converted keeping the level.
 */
public final class SkillCurve {
    public static final int MAX_LEVEL = 60;
    public static final int VERSION = 2;
    /** TOTAL[L] = total XP needed to reach level L (TOTAL[1] = 0). */
    private static final long[] TOTAL = new long[MAX_LEVEL + 1];

    static {
        long sum = 0;
        TOTAL[1] = 0;
        for (int level = 1; level < MAX_LEVEL; level++) {
            sum += needFor(level);
            TOTAL[level + 1] = sum;
        }
    }

    private SkillCurve() {}

    /** XP needed to advance from this level to the next one. */
    public static long needFor(int level) {
        return Math.round(60 * Math.pow(level, 1.6)) + 60;
    }

    private static long oldNeedFor(int level) {
        return Math.round(50 * Math.pow(level, 1.5)) + 50;
    }

    /** XP of curve version 1 converted to this curve: the same level and the same share of the next one. */
    public static double fromVersion1(double xp) {
        long total = 0;
        for (int level = 1; level < MAX_LEVEL; level++) {
            long need = oldNeedFor(level);
            if (xp < total + need) return TOTAL[level] + (xp - total) / need * needFor(level);
            total += need;
        }
        return TOTAL[MAX_LEVEL];
    }

    public static long totalFor(int level) {
        return TOTAL[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    public static int levelFor(double xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= TOTAL[level + 1]) level++;
        return level;
    }

    /** XP gathered inside the current level (0 at max level). */
    public static long progressInLevel(double xp) {
        int level = levelFor(xp);
        if (level >= MAX_LEVEL) return 0;
        return (long) Math.floor(xp - TOTAL[level]);
    }
}
