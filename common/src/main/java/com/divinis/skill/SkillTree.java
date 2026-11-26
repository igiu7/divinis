package com.divinis.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The character's four skill trees, each a bar the player puts skill points into (CharacterService), an orb every
 * POINTS_PER_ORB points: strength, dexterity, intelligence, and the fourth whose orbs come from the skill book put into
 * it (SkillBook). Dexterity's first skill is Dash. Order matters: it is the order of CharacterData's points and of the GUI packet.
 */
public enum SkillTree {
    STR("STR", "Strength", 0xe0564f),
    DEX("DEX", "Dexterity", 0x58c47a),
    INT("INT", "Intelligence", 0x5aa2ff),
    BOOK("BOOK", "Skill Book", 0xf2c14e);

    public static final int POINTS_PER_ORB = 5;
    /** Points a tree takes at most (a book may take fewer: SkillBook.maxPoints). */
    public static final int MAX_POINTS = 50;

    public final String abbr;
    public final String title;
    public final int color;
    private final List<SkillOrb> orbs;

    SkillTree(String abbr, String title, int color) {
        this.abbr = abbr;
        this.title = title;
        this.color = color;
        List<SkillOrb> orbs = new ArrayList<>(emptyOrbs(MAX_POINTS));
        if (abbr.equals("DEX")) orbs.set(Dash.ORB_INDEX, Dash.orb());
        this.orbs = Collections.unmodifiableList(orbs);
    }

    /** Orbs with nothing in them yet, one every POINTS_PER_ORB points up to max. */
    static List<SkillOrb> emptyOrbs(int max) {
        List<SkillOrb> out = new ArrayList<>();
        for (int i = 0; i < max / POINTS_PER_ORB; i++) out.add(SkillOrb.empty(i));
        return Collections.unmodifiableList(out);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The orbs of this tree; for BOOK those of the book in it (none without one). */
    public List<SkillOrb> orbs(String book) {
        if (this != BOOK) return orbs;
        return SkillBook.byId(book).map(SkillBook::orbs).orElse(List.of());
    }

    /** Points this tree takes at most; for BOOK the book's (0 without one). */
    public int maxPoints(String book) {
        if (this != BOOK) return MAX_POINTS;
        return SkillBook.byId(book).map(SkillBook::maxPoints).orElse(0);
    }

    public static Optional<SkillTree> byId(String id) {
        for (SkillTree t : values()) {
            if (t.id().equalsIgnoreCase(id) || t.abbr.equalsIgnoreCase(id)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
