package com.divinis.neoforge.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.divinis.skill.PerkEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Less wear on a tool for the skill books' Strong Arm (pickaxes) and Sharp Axe (axes): skill.PerkEvents.wear. (NeoForge: any creature wears it.) */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int divinis$lighterWear(int amount, int same, ServerLevel level, net.minecraft.world.entity.LivingEntity user, Consumer<Item> onBreak) {
        try {
            return PerkEvents.wear((ItemStack) (Object) this, user, amount);
        } catch (Throwable t) {
            return amount;
        }
    }
}
