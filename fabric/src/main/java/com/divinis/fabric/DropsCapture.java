package com.divinis.fabric;

import java.util.List;

import net.minecraft.world.entity.item.ItemEntity;

/** A creature collecting its death drops (LivingEntityMixin) instead of dropping them one by one. */
public interface DropsCapture {
    List<ItemEntity> divinis$captured();
}
