package com.divinis.platform.event.client;

import java.util.function.Function;
import java.util.function.Supplier;

import com.divinis.platform.bus.Event;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The time to give armour items their own worn model (drawn with the item's texture) and their own item renderer
 * (for items whose model is "builtin/entity").
 */
public abstract class RegisterArmorRenderersEvent extends Event {
    @FunctionalInterface
    public interface ArmorModel {
        HumanoidModel<?> model(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original);
    }

    public abstract void register(ArmorModel model, Function<ItemStack, ResourceLocation> texture,
                                  Supplier<BlockEntityWithoutLevelRenderer> itemRenderer, Item... items);
}
