package com.divinis.dungeon;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A key won from a puzzle of the Labyrinth. It opens one kind of lock (LabyrinthLockBlock), only in the run it was
 * found in, and is taken away when its holder leaves the Labyrinth.
 */
public class LabyrinthKeyItem extends Item {
    final KeyType type;

    public LabyrinthKeyItem(KeyType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Opens " + type.opens).withStyle(type.color));
    }
}
