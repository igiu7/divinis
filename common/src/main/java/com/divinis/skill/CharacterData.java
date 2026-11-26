package com.divinis.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A player's character, a NeoForge data attachment (kept on death): the XP of their level (SkillCurve), the skill
 * points put into each tree (SkillTree order), the skill book in the fourth tree ("" for none), when they last reset
 * their points (epoch millis), and whether the XP of the old professions has been brought over (CharacterService);
 * the book skills' cooldowns and the buffs they give (skill id -> epoch millis it ends at, SkillEffects).
 */
public final class CharacterData {
    public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(d -> d.xp),
            Codec.INT.listOf().optionalFieldOf("points", List.of()).forGetter(d -> {
                List<Integer> out = new ArrayList<>();
                for (int p : d.points) out.add(p);
                return out;
            }),
            Codec.STRING.optionalFieldOf("book", "").forGetter(d -> d.book),
            Codec.LONG.optionalFieldOf("lastReset", 0L).forGetter(d -> d.lastReset),
            Codec.BOOL.optionalFieldOf("migrated", false).forGetter(d -> d.migrated),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("buffs", Map.of()).forGetter(d -> d.buffs)
    ).apply(i, CharacterData::new));

    public double xp;
    public final int[] points = new int[SkillTree.values().length];
    public String book = "";
    public long lastReset;
    public boolean migrated;
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Long> buffs = new HashMap<>();

    public CharacterData() {}

    private CharacterData(double xp, List<Integer> points, String book, long lastReset, boolean migrated,
                          Map<String, Long> cooldowns, Map<String, Long> buffs) {
        this.xp = xp;
        for (int i = 0; i < Math.min(points.size(), this.points.length); i++) this.points[i] = Math.max(0, points.get(i));
        this.book = book;
        this.lastReset = lastReset;
        this.migrated = migrated;
        long now = System.currentTimeMillis();
        cooldowns.forEach((k, v) -> { if (v > now) this.cooldowns.put(k, v); });
        buffs.forEach((k, v) -> { if (v > now) this.buffs.put(k, v); });
    }

    /** Whether the buff runs now. */
    public boolean buff(String id) {
        Long until = buffs.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public int level() {
        return SkillCurve.levelFor(xp);
    }

    public int spent() {
        int sum = 0;
        for (int p : points) sum += p;
        return sum;
    }

    public int points(SkillTree tree) {
        return points[tree.ordinal()];
    }
}
