package com.divinis;

import org.slf4j.Logger;

import com.divinis.api.DivinisHooks;
import com.divinis.net.DivinisNet;
import com.divinis.registry.ModBlocks;
import com.divinis.skill.SkillAttachments;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Divinis: the RPG - the bosses and their effects, the dungeons and the Labyrinth, fishing, the character (levels,
 * skill trees, skill books, Dash, the camp), smithing (the Smelting Forge, the anvil, the cauldron), the rare metals with
 * their weapons, armour and tools, item rarity, the Guild Master and his quests.
 * Another mod can tie it into its own world through DivinisHooks.
 */
@Mod(Divinis.MODID)
public class Divinis {
    public static final String MODID = "divinis";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static DivinisHooks hooks = new DivinisHooks() {};

    public Divinis(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(DivinisNet::register);
        SkillAttachments.register(modEventBus);
        com.divinis.registry.ModParticles.register(modEventBus);
        com.divinis.registry.ModIngots.init();        // rare-metal ingots
        com.divinis.skill.SkillBook.init();            // skill books
        com.divinis.fishing.FishingRods.init();        // fishing rods
        com.divinis.registry.ModFantasyTools.init();   // the rare metals' tools
        com.divinis.registry.ModWeapons.init();        // and weapons
        com.divinis.registry.FantasyArmor.register(modEventBus); // the fantasy armour sets, in every metal
        com.divinis.boss.BossItems.init(modEventBus);  // boss drops, effect items
        com.divinis.dungeon.DungeonRegistry.register(modEventBus); // reward chest, crystal, lock
        com.divinis.forge.SmeltingForge.register(modEventBus);     // the smelting forge
        ModBlocks.register(modEventBus);
        com.divinis.forge.AnvilForge.register();        // the anvil forges weapon parts by hand (Diegesis station)
        com.divinis.forge.SmeltingForge.registerStation(); // the smelting forge heats ingots (Diegesis station)
        com.divinis.forge.CauldronStation.register();   // the cauldron: pouring, filling, quenching (Diegesis station)
        com.divinis.registry.ModEntities.register(modEventBus);
        com.divinis.registry.ModTabs.register(modEventBus);
    }

    /** What the world Divinis runs in adds to it (DivinisHooks); without one, Divinis stands on its own. */
    public static DivinisHooks hooks() {
        return hooks;
    }

    public static void setHooks(DivinisHooks newHooks) {
        hooks = newHooks;
    }
}
