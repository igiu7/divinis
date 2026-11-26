package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public abstract class PlayerInteractEvent extends Event implements ICancellableEvent {
    private final Player player;
    private final InteractionHand hand;
    private final BlockPos pos;
    private InteractionResult cancellationResult = InteractionResult.PASS;

    protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos) {
        this.player = player;
        this.hand = hand;
        this.pos = pos;
    }

    public Player getEntity() { return player; }
    public InteractionHand getHand() { return hand; }
    public BlockPos getPos() { return pos; }
    public Level getLevel() { return player.level(); }
    public ItemStack getItemStack() { return player.getItemInHand(hand); }
    public InteractionResult getCancellationResult() { return cancellationResult; }
    public void setCancellationResult(InteractionResult result) { this.cancellationResult = result; }

    /** A player right-clicks a block (cancelled: nothing more happens, the result is getCancellationResult). */
    public static class RightClickBlock extends PlayerInteractEvent {
        private final BlockHitResult hit;

        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hit) {
            super(player, hand, pos);
            this.hit = hit;
        }

        public BlockHitResult getHitVec() { return hit; }
    }

    /** A player uses the item in a hand (cancelled: the item is not used). */
    public static class RightClickItem extends PlayerInteractEvent {
        public RightClickItem(Player player, InteractionHand hand) {
            super(player, hand, player.blockPosition());
        }
    }
}
