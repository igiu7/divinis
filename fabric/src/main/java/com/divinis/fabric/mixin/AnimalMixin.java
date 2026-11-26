package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.BabyEntitySpawnEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

/** Two animals are about to have a baby (cancelled: their love ends and none is born). */
@Mixin(Animal.class)
public abstract class AnimalMixin {
    @Inject(method = "spawnChildFromBreeding", at = @At("HEAD"), cancellable = true)
    private void divinis$baby(ServerLevel level, Animal partner, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        var cause = self.getLoveCause() != null ? self.getLoveCause() : partner.getLoveCause();
        if (DivinisBus.post(new BabyEntitySpawnEvent(self, partner, null, cause)).isCanceled()) {
            self.resetLove();
            partner.resetLove();
            ci.cancel();
        }
    }
}
