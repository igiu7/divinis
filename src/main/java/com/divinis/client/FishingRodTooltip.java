package com.divinis.client;

import com.divinis.fishing.FishingGame;
import com.divinis.fishing.FishingRods;
import com.divinis.Divinis;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * A fishing rod's fishing speed and luck in its tooltip (fishing.FishingGame): the rod's own (fishing.FishingRods; the
 * vanilla one has none - slow, and common catches mostly) with Lure's and Luck of the Sea's, and a special rod's gift.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class FishingRodTooltip {
    private FishingRodTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof FishingRodItem)) return;
        int lure = 0, luck = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : event.getItemStack().getEnchantments().entrySet()) {
            if (e.getKey().is(Enchantments.LURE)) lure = e.getIntValue();
            if (e.getKey().is(Enchantments.LUCK_OF_THE_SEA)) luck = e.getIntValue();
        }
        FishingRods.Stats stats = FishingRods.stats(event.getItemStack());
        int speed = stats.speed() + lure * FishingGame.SPEED_PER_LURE;
        luck += stats.luck();
        int at = Math.min(event.getToolTip().size(), 1);
        event.getToolTip().add(at, Component.literal("Fishing Speed: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("+" + speed + "%").withStyle(speed > 0 ? ChatFormatting.AQUA : ChatFormatting.WHITE)));
        event.getToolTip().add(at + 1, Component.literal("Luck: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(luck)).withStyle(luck > 0 ? ChatFormatting.GREEN : ChatFormatting.WHITE)));
        int next = at + 2;
        if (stats.gift() != FishingRods.Gift.NONE) {
            event.getToolTip().add(next++, Component.literal(stats.gift().text).withStyle(ChatFormatting.GOLD));
        }
        if (stats.gift() == FishingRods.Gift.COLLECTOR && event.getEntity() != null) {
            int[] kept = FishingGame.kept(event.getItemStack(), event.getEntity().level().getGameTime());
            if (kept != null) {
                String bonus = kept[0] > 0 ? "+" + kept[0] + " Luck" : "+" + kept[1] + "% Fishing Speed";
                event.getToolTip().add(next++, Component.literal("Kept: " + bonus + " for " + (kept[2] / 20) + " s")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        event.getToolTip().add(next, Component.literal("Fish in a pool on the water for more").withStyle(ChatFormatting.DARK_GRAY));
    }
}
