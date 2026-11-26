package com.divinis.client;

/** The client's event listener classes (see platform.Subscribers): registered by each loader's client start-up. */
public final class ClientSubscribers {
    private ClientSubscribers() {}

    public static final Class<?>[] ALL = {
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
    };

    /** The client's start-up (after Divinis.init). */
    public static void init() {
        com.divinis.platform.bus.DivinisBus.register(com.divinis.platform.Dist.CLIENT, ALL);
    }
}
