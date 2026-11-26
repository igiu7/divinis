package com.divinis;

import org.slf4j.Logger;

import com.divinis.api.DivinisHooks;
import com.divinis.net.DivinisNet;
import com.divinis.platform.Services;
import com.divinis.platform.Subscribers;
import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.bus.IEventBus;
import com.divinis.registry.ModBlocks;
import com.divinis.skill.SkillAttachments;
import com.mojang.logging.LogUtils;

/**
 * Divinis: the RPG - the bosses and their effects, the dungeons and the Labyrinth, fishing, the character (levels,
 * skill trees, skill books, Dash, the camp), smithing (the Smelting Forge, the anvil, the cauldron), the rare metals with
 * their weapons, armour and tools, item rarity, the Guild Master and his quests.
 * The same code runs on NeoForge and Fabric: each loader's entry point calls init() and turns its events into
 * Divinis' (platform.event). Another mod can tie it into its own world through DivinisHooks.
 */
public final class Divinis {
    public static final String MODID = "divinis";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static DivinisHooks hooks = new DivinisHooks() {};

    private Divinis() {}

    /** The mod's start-up, on both sides. */
    public static void init() {
        IEventBus bus = IEventBus.MOD;
        Config.load();
        DivinisNet.register();
        SkillAttachments.register();
        com.divinis.registry.ModParticles.register(bus);
        com.divinis.registry.ModIngots.init();        // rare-metal ingots
        com.divinis.skill.SkillBook.init();            // skill books
        com.divinis.fishing.FishingRods.init();        // fishing rods
        com.divinis.registry.ModFantasyTools.init();   // the rare metals' tools
        com.divinis.registry.ModWeapons.init();        // and weapons
        com.divinis.registry.FantasyArmor.register(bus); // the fantasy armour sets, in every metal
        com.divinis.boss.BossItems.init(bus);          // boss drops, effect items
        com.divinis.dungeon.DungeonRegistry.register(bus); // reward chest, crystal, lock
        com.divinis.forge.SmeltingForge.register(bus);     // the smelting forge
        ModBlocks.register(bus);
        com.divinis.forge.AnvilForge.register();        // the anvil forges weapon parts by hand (Diegesis station)
        com.divinis.forge.SmeltingForge.registerStation(); // the smelting forge heats ingots (Diegesis station)
        com.divinis.forge.CauldronStation.register();   // the cauldron: pouring, filling, quenching (Diegesis station)
        com.divinis.registry.ModEntities.register(bus);
        com.divinis.registry.ModTabs.register(bus);
        com.divinis.platform.registry.DeferredRegister.registerQueued();
        com.divinis.registry.ModEntities.registerAttributes();
        DivinisBus.register(Services.dist(), Subscribers.ALL);
    }

    /** What the world Divinis runs in adds to it (DivinisHooks); without one, Divinis stands on its own. */
    public static DivinisHooks hooks() {
        return hooks;
    }

    public static void setHooks(DivinisHooks newHooks) {
        hooks = newHooks;
    }
}
