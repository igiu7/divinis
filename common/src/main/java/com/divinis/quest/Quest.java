package com.divinis.quest;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One of the Guild Master's quests: bring him `count` items (DELIVER, counted in the inventory and taken on turn-in) or
 * slay `count` mobs (SLAY, counted as the player kills them). Every quest can be done again once turned in.
 *
 * @param target what the objective names ("raw iron", "zombies")
 * @param item   DELIVER: which stacks count
 * @param mob    SLAY: which mobs count
 */
public record Quest(String id, String name, String story, Kind kind, Supplier<Item> icon, String target, int count,
                    Predicate<ItemStack> item, Supplier<EntityType<?>> mob, int emeralds, int xp) {

    public enum Kind { DELIVER, SLAY }

    static Quest deliver(String id, String name, String story, Supplier<Item> icon, String target, int count,
                         Predicate<ItemStack> item, int emeralds, int xp) {
        return new Quest(id, name, story, Kind.DELIVER, icon, target, count, item, () -> null, emeralds, xp);
    }

    static Quest slay(String id, String name, String story, Supplier<Item> icon, String target, int count,
                      Supplier<EntityType<?>> mob, int emeralds, int xp) {
        return new Quest(id, name, story, Kind.SLAY, icon, target, count, s -> false, mob, emeralds, xp);
    }

    /** "Bring 32 raw iron", "Slay 10 zombies". */
    public String task() {
        return (kind == Kind.DELIVER ? "Bring " : "Slay ") + count + " " + target;
    }
}
