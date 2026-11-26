package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An item's tooltip was built (the lines can be changed). */
public class ItemTooltipEvent extends Event {
    private final ItemStack stack;
    private final Player player;
    private final List<Component> tooltip;
    private final TooltipFlag flags;
    private final Item.TooltipContext context;

    public ItemTooltipEvent(ItemStack stack, @Nullable Player player, List<Component> tooltip, TooltipFlag flags,
                            Item.TooltipContext context) {
        this.stack = stack;
        this.player = player;
        this.tooltip = tooltip;
        this.flags = flags;
        this.context = context;
    }

    public ItemStack getItemStack() { return stack; }
    @Nullable
    public Player getEntity() { return player; }
    public List<Component> getToolTip() { return tooltip; }
    public TooltipFlag getFlags() { return flags; }
    public Item.TooltipContext getContext() { return context; }
}
