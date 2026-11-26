package com.divinis.boss;

import com.divinis.Divinis;

import net.minecraft.world.entity.LivingEntity;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.LivingShieldBlockEvent;

/**
 * A raised shield stops only half of a blow from the Minotaur, or from someone striking with the Minotaur's Axe
 * (in melee: thrown or shot things are not affected), and nothing at all of the Golem's.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class ShieldPierce {
    private ShieldPierce() {}

    /** share of a blow a raised shield stops */
    private static final float SHIELD_BLOCKS = 0.5F;

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked()) return;
        var source = event.getDamageSource();
        if (source.getEntity() instanceof Golem) {
            event.setBlocked(false); // too heavy for any shield
            return;
        }
        boolean pierces = source.getEntity() instanceof Minotaur
                || source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                        && attacker.getMainHandItem().is(BossItems.MINOTAUR_AXE.get());
        if (pierces) event.setBlockedDamage(event.getOriginalBlockedDamage() * SHIELD_BLOCKS);
    }
}
