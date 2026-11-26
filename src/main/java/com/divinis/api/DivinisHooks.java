package com.divinis.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the world Divinis runs in adds to it (set with Divinis.setHooks). Every method has a default that lets Divinis
 * stand on its own; a mod overrides what its world has: its creatures and plants, its technologies, its
 * records of the players.
 */
public interface DivinisHooks {
    /** XP for killing a creature (the world's own creatures), or null for Divinis' own rules. */
    @Nullable
    default Double hunterXp(LivingEntity victim) {
        return null;
    }

    /** Whether a creature counts as an animal for the skill books' perks, beside vanilla's animals. */
    default boolean isAnimal(Entity entity) {
        return false;
    }

    /** XP for picking a plant by hand (the world's own bushes), or null for Divinis' own rules. */
    @Nullable
    default Double pickXp(BlockState state) {
        return null;
    }

    /** Whether the herbalist's growing skill grows this plant too (the world's own bushes), beside crops and berries. */
    default boolean growable(BlockState state) {
        return false;
    }

    /**
     * The technologies a player knows, for the forge's rare-metal parts (ids as the world names them; a part of a
     * metal needs "<metal>_weapons"); null: the world has no technologies, every part may be forged.
     */
    @Nullable
    default List<String> knownTechs(ServerPlayer player) {
        return null;
    }

    /** The technologies' names, by id. */
    default Map<String, String> techNames() {
        return Map.of();
    }

    /** Why a player may not make an item (what their world has not unlocked yet), or empty. */
    default Optional<String> craftBlocked(ServerPlayer player, ItemStack result) {
        return Optional.empty();
    }

    /** The player's character was saved (XP, points, book): the world may keep its own copy. */
    default void characterSaved(ServerPlayer player) {}

    /** The player's character level changed. */
    default void levelChanged(ServerPlayer player) {}

    /** Tools (and the like) of the world's own age for a dungeon's vault. */
    default void vaultTools(RandomSource random, List<ItemStack> out) {
        if (random.nextFloat() < 0.4F) {
            ItemStack[] tools = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL),
                    new ItemStack(Items.IRON_HOE)};
            out.add(tools[random.nextInt(tools.length)]);
        }
        if (random.nextFloat() < 0.25F) out.add(new ItemStack(Items.BUCKET));
    }
}
