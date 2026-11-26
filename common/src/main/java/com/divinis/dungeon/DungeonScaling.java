package com.divinis.dungeon;

import com.divinis.Divinis;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Party scaling of dungeon monsters: every player beyond the first adds 60% health and 15% damage. Bosses read
 * their damage from the ATTACK_DAMAGE attribute (see boss.Minotaur#damageScale), so the bonus reaches them too.
 */
public final class DungeonScaling {
    private DungeonScaling() {}

    static final double HEALTH_PER_PLAYER = 0.6;
    static final double DAMAGE_PER_PLAYER = 0.15;

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "dungeon_party");

    /** Scales the mob for a party of this size; its health keeps the same share of the maximum. */
    public static void apply(LivingEntity mob, int players) {
        float share = mob.getHealth() / mob.getMaxHealth();
        int extra = Math.max(0, players - 1);
        set(mob, Attributes.MAX_HEALTH, HEALTH_PER_PLAYER * extra);
        set(mob, Attributes.ATTACK_DAMAGE, DAMAGE_PER_PLAYER * extra);
        mob.setHealth(mob.getMaxHealth() * share);
    }

    private static void set(LivingEntity mob, Holder<Attribute> attribute, double bonus) {
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(ID);
        if (bonus > 0) inst.addPermanentModifier(new AttributeModifier(ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
