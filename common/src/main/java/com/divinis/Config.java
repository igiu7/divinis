package com.divinis;

import java.util.List;

import com.divinis.platform.config.ModConfigSpec;

/** Divinis' settings (config/divinis-common.toml). */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- the character's XP

    public static final ModConfigSpec.DoubleValue XP_MULT_BLACKSMITH = BUILDER
            .comment("Skills: multiplier for all Blacksmith XP (1.0 = default pace, level 30 after ~2 weeks of play)")
            .defineInRange("skills.blacksmithXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_FARMER = BUILDER
            .comment("Skills: multiplier for all Farmer XP")
            .defineInRange("skills.farmerXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_MINER = BUILDER
            .comment("Skills: multiplier for all Miner XP")
            .defineInRange("skills.minerXpMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue XP_MULT_HUNTER = BUILDER
            .comment("Skills: multiplier for all Hunter XP")
            .defineInRange("skills.hunterXpMultiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue SPAWNER_MOB_MULTIPLIER = BUILDER
            .comment("Skills: Hunter XP multiplier for mobs that came from a spawner")
            .defineInRange("skills.spawnerMobMultiplier", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PLACED_MEMORY_MINUTES = BUILDER
            .comment("Skills: blocks placed by players give no Miner/Farmer XP when broken within this many minutes")
            .defineInRange("skills.placedBlockMemoryMinutes", 120, 0, 10080);
    public static final ModConfigSpec.BooleanValue SHOW_XP_GAIN = BUILDER
            .comment("Skills: show '+XP' messages above the hotbar")
            .define("skills.showXpGain", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKSMITH_NAMESPACES = BUILDER
            .comment("Skills: mod ids whose items always count as Blacksmith gear when crafted on a crafting table")
            .defineListAllowEmpty("skills.blacksmithModIds", List.of("magistuarmory", "magistuarmoryaddon",
                    "antiquelegacy", "darkagesarmory", "siegemachines"), () -> "", o -> o instanceof String);

    // ---------------------------------------------------------------- fishing

    public static final ModConfigSpec.DoubleValue FISH_MOB_DROP_CHANCE = BUILDER
            .comment("Chance that a fish killed in the water (cod, salmon, tropical fish, pufferfish) drops anything;",
                    "fish are caught with a fishing rod (which also gives Hunter XP). 1 = always, as in vanilla")
            .defineInRange("fishing.fishMobDropChance", 0.25, 0.0, 1.0);

    // ---------------------------------------------------------------- the starting city

    public static final ModConfigSpec.BooleanValue CITY_BUILD = BUILDER
            .comment("Build the starting city (a walled medieval town) near the world spawn when a new world starts. It is",
                    "built a little every tick and takes about a minute; /divinis admin city status shows how far it is.",
                    "In a world already played it is not built by itself: /divinis admin city here builds it round you")
            .define("city.build", true);
    public static final ModConfigSpec.BooleanValue CITY_RESPAWN = BUILDER
            .comment("Players always respawn in the city's square (beds are ignored), and join there the first time")
            .define("city.respawnInCity", true);
    public static final ModConfigSpec.BooleanValue CITY_SAFE = BUILDER
            .comment("Hostile mobs do not spawn naturally inside the city's walls")
            .define("city.noHostileSpawns", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** Reads config/divinis-common.toml (and writes it back complete). */
    static void load() {
        SPEC.load(com.divinis.platform.Services.configDir().resolve(Divinis.MODID + "-common.toml"));
    }
}
