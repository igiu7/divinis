package com.divinis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.fishing.FishingGame;
import com.divinis.net.FishingPayloads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fishing, Divinis' way (fishing.FishingGame): the wait for a fish is FishingGame's (slow for a plain rod, faster
 * with fishing speed), and a fish is caught only through the minigame - reeling in on a bite brings nothing.
 * The fish swimming to the bobber leaves foam on the water (client.FishingFoam) instead of the vanilla bubbles and
 * wake particles: where the particles would go, the place is sent to whoever sees the bobber.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Shadow
    private int nibble;
    @Shadow
    private int timeUntilLured;
    @Shadow
    private int timeUntilHooked;

    @Unique
    private int divinis$luredBefore;
    @Unique
    private int divinis$foamTick = -1;

    @Inject(method = "catchingFish", at = @At("HEAD"))
    private void divinis$beforeCatching(BlockPos pos, CallbackInfo ci) {
        divinis$luredBefore = timeUntilLured;
    }

    /** Vanilla has just drawn a new wait (100 - 600 ticks, less Lure's): ours instead. */
    @Inject(method = "catchingFish", at = @At("RETURN"))
    private void divinis$wait(BlockPos pos, CallbackInfo ci) {
        if (divinis$luredBefore <= 0 && timeUntilLured > 0 && nibble <= 0 && timeUntilHooked <= 0) {
            timeUntilLured = FishingGame.waitTicks((FishingHook) (Object) this);
        }
    }

    /** While the fish swims in (timeUntilHooked > 0) its bubbles and wake become foam; the bite's splash stays. */
    @Redirect(method = "catchingFish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private int divinis$foam(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
        if (timeUntilHooked <= 0 || (particle != ParticleTypes.BUBBLE && particle != ParticleTypes.FISHING)) {
            return level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
        }
        FishingHook self = (FishingHook) (Object) this;
        if (particle == ParticleTypes.FISHING && divinis$foamTick != self.tickCount) {
            divinis$foamTick = self.tickCount;
            boolean hound = com.divinis.fishing.FishingRods.gift(self) == com.divinis.fishing.FishingRods.Gift.HOUND;
            int color = hound ? com.divinis.fishing.FishingPool.nearestColor(self, 24) : 0;
            PacketDistributor.sendToPlayersTrackingEntity(self, new FishingPayloads.Foam(self.getId(), x, y, z, color, hound));
        }
        return 0;
    }

    @Inject(method = "retrieve", at = @At("HEAD"))
    private void divinis$onlyThroughTheGame(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        FishingGame.onRetrieve((FishingHook) (Object) this);
    }
}
