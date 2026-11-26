package com.divinis.platform;

/**
 * Every class with Divinis' event listeners (@EventBusSubscriber): DivinisBus registers those meant for the side.
 * A new listener class must be added here.
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
            com.divinis.client.ArmorCapeLayer.class,
            com.divinis.client.BeamFx.class,
            com.divinis.client.BlacksteelArmorModels.class,
            com.divinis.client.BloomFx.class,
            com.divinis.client.BloomFx.Setup.class,
            com.divinis.client.BulletFx.class,
            com.divinis.client.CatchPopups.class,
            com.divinis.client.ClientModEvents.class,
            com.divinis.client.CreatorFx.class,
            com.divinis.client.CreatorFx.Setup.class,
            com.divinis.client.DashClient.class,
            com.divinis.client.FireFx.class,
            com.divinis.client.FishingFoam.class,
            com.divinis.client.FishingHud.class,
            com.divinis.client.FishingRodTooltip.class,
            com.divinis.client.RarityTooltip.class,
            com.divinis.client.ScreenFxClient.Camera3d.class,
            com.divinis.client.ScreenFxClient.Setup.class,
            com.divinis.client.SkillFx.class,
            com.divinis.client.SlashFx.class,
            com.divinis.client.TargetMarker.class,
            com.divinis.client.TelegraphRenderer.class,
            com.divinis.client.forge.AnvilScreenHooks.class,
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
