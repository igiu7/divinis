package com.divinis.platform;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/** A tool tier from its numbers (what NeoForge's SimpleTier is). */
public final class SimpleTier implements Tier {
    private final TagKey<Block> incorrect;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantability;
    private final Supplier<Ingredient> repair;

    public SimpleTier(TagKey<Block> incorrect, int uses, float speed, float damage, int enchantability, Supplier<Ingredient> repair) {
        this.incorrect = incorrect;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.enchantability = enchantability;
        this.repair = Suppliers.memoize(repair::get);
    }

    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return damage; }
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return incorrect; }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public Ingredient getRepairIngredient() { return repair.get(); }
}
