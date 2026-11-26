package com.divinis.fabric.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.BlockDropsEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A broken block's drops go through BlockDropsEvent first (made as vanilla's popResource makes them). */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void divinis$drops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                      CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || !DivinisBus.hasListeners(BlockDropsEvent.class)) return;
        ci.cancel();
        List<ItemEntity> drops = new ArrayList<>();
        if (server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            double h = EntityType.ITEM.getHeight() / 2.0;
            for (ItemStack stack : Block.getDrops(state, server, pos, blockEntity, breaker, tool)) {
                if (stack.isEmpty()) continue;
                ItemEntity item = new ItemEntity(server, pos.getX() + 0.5 + Mth.nextDouble(server.random, -0.25, 0.25),
                        pos.getY() + 0.5 + Mth.nextDouble(server.random, -0.25, 0.25) - h,
                        pos.getZ() + 0.5 + Mth.nextDouble(server.random, -0.25, 0.25), stack);
                item.setDefaultPickUpDelay();
                drops.add(item);
            }
        }
        BlockDropsEvent e = DivinisBus.post(new BlockDropsEvent(server, pos, state, blockEntity, drops, breaker, tool));
        if (!e.isCanceled()) for (ItemEntity item : e.getDrops()) server.addFreshEntity(item);
        state.spawnAfterBreak(server, pos, tool, true);
    }
}
