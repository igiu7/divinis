package com.divinis.platform;

/**
 * Every class with Divinis' event listeners (@EventBusSubscriber) that both sides have; the client's are in
 * client.ClientSubscribers (never loaded on a dedicated server). A new listener class must be added to one of them.
 */
public final class Subscribers {
    private Subscribers() {}

    public static final Class<?>[] ALL = {
            com.divinis.boss.ArachnidCocoon.class,
            com.divinis.boss.Azramond.class,
            com.divinis.boss.BossCommands.class,
            com.divinis.boss.Bullets.class,
            com.divinis.boss.MinotaurAxeItem.Charge.class,
            com.divinis.boss.ShieldPierce.class,
            com.divinis.boss.Telegraphs.class,
            com.divinis.boss.Wildfire.Crits.class,
            com.divinis.command.SkillCommands.class,
            com.divinis.dungeon.DungeonCommands.class,
            com.divinis.dungeon.DungeonService.class,
            com.divinis.fishing.FishingGame.class,
            com.divinis.registry.ModWeapons.class,
            com.divinis.rules.FishingRules.class,
            com.divinis.screenfx.ScreenFx.class,
            com.divinis.skill.Dash.class,
            com.divinis.skill.PerkEvents.class,
            com.divinis.skill.SkillEffects.class,
            com.divinis.skill.SkillEvents.class,
    };
}
