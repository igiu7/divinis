package com.divinis.client;

import com.divinis.registry.ItemRarity;
import com.divinis.Divinis;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** An item's Divinis rarity (registry.ItemRarity): its name in the rarity's colour, and the rarity in bold at the end. */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class RarityTooltip {
    private RarityTooltip() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemRarity rarity = ItemRarity.of(event.getItemStack().getItem());
        if (rarity == null || event.getToolTip().isEmpty()) return;
        event.getToolTip().set(0, event.getToolTip().get(0).copy().withStyle(s -> s.withColor(rarity.color)));
        event.getToolTip().add(Component.literal(rarity.label).withStyle(s -> s.withColor(rarity.color).withBold(true)));
    }
}
