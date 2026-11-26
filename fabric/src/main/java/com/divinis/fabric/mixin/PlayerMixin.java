package com.divinis.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.CriticalHitEvent;
import com.divinis.platform.event.PlayerEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** How fast a player mines (BreakSpeed) and their critical hits. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void divinis$breakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (!DivinisBus.hasListeners(PlayerEvent.BreakSpeed.class)) return;
        cir.setReturnValue(DivinisBus.post(new PlayerEvent.BreakSpeed((Player) (Object) this, state, cir.getReturnValue(), null)).getNewSpeed());
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;crit(Lnet/minecraft/world/entity/Entity;)V"))
    private void divinis$crit(Entity target, CallbackInfo ci) {
        DivinisBus.post(new CriticalHitEvent((Player) (Object) this, target, true));
    }
}
