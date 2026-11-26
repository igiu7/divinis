package com.divinis.skill;

import java.util.List;

import com.divinis.registry.ItemRarity;

/**
 * The skill books and their orbs: a passive at 5 points, a skill at 10, a passive at 15, a skill at 20. The orbs' ids
 * are what the game looks up: the passives' in Perks (and where it asks Perks.has), the skills' in SkillEffects.
 */
public final class SkillBooks {
    private SkillBooks() {}

    // the passives
    public static final String STEADY_LINE = "steady_line", CALM_HANDS = "calm_hands";
    public static final String STRONG_ARM = "strong_arm", RICH_SEAMS = "rich_seams";
    public static final String SHARP_AXE = "sharp_axe", FOREST_GIFT = "forest_gift";
    public static final String BOUNTIFUL = "bountiful", GREEN_THUMB = "green_thumb";
    public static final String SKILLED_HANDS = "skilled_hands", QUICK_DRESSING = "quick_dressing";
    public static final String THICK_SKIN = "thick_skin", LONG_STRIDE = "long_stride";
    public static final String PREDATOR = "predator", SILENT_STEP = "silent_step";
    public static final String LABYRINTH_SENSE = "labyrinth_sense", KEEN_EYE = "keen_eye";
    public static final String STUDIOUS = "studious", RESEARCHER = "researcher";
    // the skills
    public static final String CHUM = "chum", CALL_POOL = "call_of_the_pool";
    public static final String ORE_ECHO = "echo_of_stone", VEIN = "vein_breaker";
    public static final String TIMBER = "timber", GROWTH = "quick_growth";
    public static final String REAP = "reap", ABUNDANCE = "abundance";
    public static final String FIELD_POULTICE = "field_poultice", BREW = "herbal_brew";
    public static final String CAMP = "camp", SHORTCUT = "shortcut";
    public static final String TRACK = "track", SKINNING = "skinning";
    public static final String TREASURE_SENSE = "treasure_sense", ESCAPE = "escape";
    public static final String LECTURE = "lecture", STUDY = "study";

    private static SkillBook book(String id, String name, ItemRarity rarity, SkillOrb... orbs) {
        return new SkillBook(id, name, SkillBook.POINTS, List.of(orbs), rarity);
    }

    static List<SkillBook> all() {
        return List.of(
                book("fisher", "Fisher", ItemRarity.RARE,
                        SkillOrb.passive(0, STEADY_LINE, "Steady Line", "+15% Fishing Speed", "+1 Fishing Luck"),
                        SkillOrb.skill(1, CHUM, "Chum", "Fish bite twice as fast for 60 s", "Cooldown: 5 min"),
                        SkillOrb.passive(2, CALM_HANDS, "Calm Hands", "The fishing minigame's zone is 20% wider"),
                        SkillOrb.skill(3, CALL_POOL, "Call of the Pool", "A Rare pool comes up round your bobber", "Cooldown: 10 min")),
                book("miner", "Miner", ItemRarity.RARE,
                        SkillOrb.passive(0, STRONG_ARM, "Strong Arm", "+10% Mining Speed with a pickaxe", "Pickaxes wear 20% slower"),
                        SkillOrb.skill(1, ORE_ECHO, "Echo of Stone", "Ores within 8 blocks show through the stone for 15 s", "Cooldown: 60 s"),
                        SkillOrb.passive(2, RICH_SEAMS, "Rich Seams", "15% chance an ore drops twice"),
                        SkillOrb.skill(3, VEIN, "Vein Breaker", "The next ore you break within 30 s takes its whole vein with it",
                                "Cooldown: 2 min")),
                book("lumberjack", "Lumberjack", ItemRarity.RARE,
                        SkillOrb.passive(0, SHARP_AXE, "Sharp Axe", "+15% Chopping Speed with an axe", "Axes wear 20% slower"),
                        SkillOrb.skill(1, TIMBER, "Timber!", "The next tree you cut within 30 s falls whole", "Cooldown: 60 s"),
                        SkillOrb.passive(2, FOREST_GIFT, "Forest's Gift", "Leaves near you drop more saplings, sticks and apples"),
                        SkillOrb.skill(3, GROWTH, "Quick Growth", "Saplings within 6 blocks grow into trees at once", "Cooldown: 3 min")),
                book("farmer", "Farmer", ItemRarity.RARE,
                        SkillOrb.passive(0, BOUNTIFUL, "Bountiful Harvest", "15% chance a ripe crop drops twice"),
                        SkillOrb.skill(1, REAP, "Reap", "Harvests and replants the ripe crops 5x5 round you", "Cooldown: 30 s"),
                        SkillOrb.passive(2, GREEN_THUMB, "Green Thumb", "Crops within 8 blocks of you grow 25% faster"),
                        SkillOrb.skill(3, ABUNDANCE, "Abundance", "Crops within 4 blocks grow two stages", "Cooldown: 3 min")),
                book("herbalist", "Herbalist", ItemRarity.RARE,
                        SkillOrb.passive(0, SKILLED_HANDS, "Skilled Hands", "Healing items heal 25% more"),
                        SkillOrb.skill(1, FIELD_POULTICE, "Field Poultice", "Heals you and the players within 4 blocks 3 hearts",
                                "Cooldown: 3 min"),
                        SkillOrb.passive(2, QUICK_DRESSING, "Quick Dressing", "Healing items take 30% less time",
                                "Medicinal herb bushes give one more herb half the time"),
                        SkillOrb.skill(3, BREW, "Herbal Brew", "Regeneration for 20 s", "Cooldown: 5 min")),
                book("wanderer", "Wanderer", ItemRarity.RARE,
                        SkillOrb.passive(0, THICK_SKIN, "Thick Skin", "Cold and heat get to you 30% slower"),
                        SkillOrb.skill(1, CAMP, "Camp", "Sets up a camp for 60 s: by its fire you heal and stay warm",
                                "Cooldown: 5 min"),
                        SkillOrb.passive(2, LONG_STRIDE, "Long Stride", "Sprinting makes you 25% less hungry", "-30% fall damage"),
                        SkillOrb.skill(3, SHORTCUT, "Shortcut", "Speed II and Jump Boost II for 20 s", "Cooldown: 2 min")),
                book("hunter", "Hunter", ItemRarity.RARE,
                        SkillOrb.passive(0, PREDATOR, "Predator", "+20% damage to animals", "An animal you hit is slowed for 3 s"),
                        SkillOrb.skill(1, TRACK, "Track", "Animals within 24 blocks glow for you for 20 s", "Cooldown: 60 s"),
                        SkillOrb.passive(2, SILENT_STEP, "Silent Step", "Animals notice you from half as far"),
                        SkillOrb.skill(3, SKINNING, "Skinning", "The next animal you kill within 60 s drops twice", "Cooldown: 3 min")),
                book("dungeon_scout", "Dungeon Scout", ItemRarity.EPIC,
                        SkillOrb.passive(0, LABYRINTH_SENSE, "Labyrinth Sense", "-15% damage from monsters in the dungeons"),
                        SkillOrb.skill(1, TREASURE_SENSE, "Treasure Sense", "A golden arrow points to the nearest reward chest you have not opened",
                                "Lasts 20 s - cooldown: 60 s"),
                        SkillOrb.passive(2, KEEN_EYE, "Keen Eye", "+15% loot from the dungeons' reward chests"),
                        SkillOrb.skill(3, ESCAPE, "Escape", "Takes you back to the start of the Labyrinth", "Once a run")),
                book("scholar", "Scholar", ItemRarity.EPIC,
                        SkillOrb.passive(0, STUDIOUS, "Studious", "+10% XP from everything"),
                        SkillOrb.skill(1, LECTURE, "Lecture", "Your party within 8 blocks gets +25% XP for 5 min", "Cooldown: 30 min"),
                        SkillOrb.passive(2, RESEARCHER, "Researcher", "Research you start takes 10% less time"),
                        SkillOrb.skill(3, STUDY, "Study", "+50% XP for you for 10 min", "Cooldown: 30 min")));
    }
}
