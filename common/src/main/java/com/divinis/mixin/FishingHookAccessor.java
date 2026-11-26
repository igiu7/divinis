package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;

/** The bite of a fishing hook, held while the fishing minigame runs (fishing.FishingGame). */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("nibble")
    int divinis$getNibble();

    @Accessor("nibble")
    void divinis$setNibble(int nibble);

    @Accessor("luck")
    int divinis$getLuck();

    /** Ticks Lure takes off the wait in vanilla (100 a level). */
    @Accessor("lureSpeed")
    int divinis$getLureSpeed();

    @Accessor("timeUntilLured")
    int divinis$getTimeUntilLured();

    @Accessor("timeUntilLured")
    void divinis$setTimeUntilLured(int ticks);

    @Accessor("timeUntilHooked")
    void divinis$setTimeUntilHooked(int ticks);

    @Accessor("DATA_BITING")
    static EntityDataAccessor<Boolean> divinis$dataBiting() {
        throw new AssertionError();
    }
}
