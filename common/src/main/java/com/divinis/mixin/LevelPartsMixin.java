package com.divinis.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.divinis.platform.entity.PartEntity;
import com.divinis.platform.entity.PartRegistry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** The level's entity searches also find the parts of Divinis' multipart creatures (as vanilla's find the dragon's). */
@Mixin(Level.class)
public abstract class LevelPartsMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"))
    private void divinis$parts(Entity except, AABB area, Predicate<? super Entity> predicate, CallbackInfoReturnable<List<Entity>> cir) {
        for (PartEntity<?> p : PartRegistry.all((Level) (Object) this)) {
            if (p != except && p.getParent() != except && p.getBoundingBox().intersects(area) && (predicate == null || predicate.test(p))) {
                cir.getReturnValue().add(p);
            }
        }
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("TAIL"))
    private <T extends Entity> void divinis$typedParts(EntityTypeTest<Entity, T> test, AABB area, Predicate<? super T> predicate,
                                                       List<? super T> output, int max, CallbackInfo ci) {
        for (PartEntity<?> p : PartRegistry.all((Level) (Object) this)) {
            if (output.size() >= max) return;
            T t = test.tryCast(p);
            if (t != null && p.getBoundingBox().intersects(area) && predicate.test(t)) output.add(t);
        }
    }
}
