package com.divinis.rules;

import com.divinis.Config;
import com.divinis.Divinis;

import net.minecraft.world.entity.animal.AbstractFish;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.LivingDropsEvent;

/**
 * Fish (the fishing itself, its catch and its XP: fishing.FishingGame):
 * - fish killed in the water (cod, salmon, tropical fish, pufferfish) drop their loot only now and then
 *   (food.fishMobDropChance), so the rod is the way to get fish.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class FishingRules {
    private FishingRules() {}

    @SubscribeEvent
    public static void onFishDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof AbstractFish)) return;
        double chance = Config.FISH_MOB_DROP_CHANCE.get();
        if (chance >= 1.0) return;
        if (event.getEntity().getRandom().nextDouble() >= chance) event.getDrops().clear();
    }
}
