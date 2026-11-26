package com.divinis.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/** The "cast" item property of the mod's fishing rods (fishing.FishingRods), as vanilla's rod has it: 1 while its line is out. */
final class FishingRodCast {
    private FishingRodCast() {}

    static float cast(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        if (entity == null) return 0;
        boolean main = entity.getMainHandItem() == stack;
        boolean off = entity.getOffhandItem() == stack;
        if (entity.getMainHandItem().getItem() instanceof FishingRodItem) off = false;
        return (main || off) && entity instanceof Player player && player.fishing != null ? 1 : 0;
    }
}
