package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.FinalizeSpawnEvent;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/** A mob is set up after spawning (and how it spawned). */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void divinis$finalize(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data,
                                  CallbackInfoReturnable<SpawnGroupData> cir) {
        DivinisBus.post(new FinalizeSpawnEvent((Mob) (Object) this, level, type));
    }
}
