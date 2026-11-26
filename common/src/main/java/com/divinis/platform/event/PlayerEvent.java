package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public abstract class PlayerEvent extends Event {
    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    /** The player is back in the world after dying (or coming back from the End: endConquered). */
    public static class PlayerRespawnEvent extends PlayerEvent {
        private final boolean endConquered;

        public PlayerRespawnEvent(Player player, boolean endConquered) {
            super(player);
            this.endConquered = endConquered;
        }

        public boolean isEndConquered() {
            return endConquered;
        }
    }

    /** How fast the player breaks a block (newSpeed starts as vanilla's). */
    public static class BreakSpeed extends PlayerEvent {
        private final BlockState state;
        private final float originalSpeed;
        private final BlockPos pos;
        private float newSpeed;

        public BreakSpeed(Player player, BlockState state, float original, BlockPos pos) {
            super(player);
            this.state = state;
            this.originalSpeed = original;
            this.newSpeed = original;
            this.pos = pos;
        }

        public BlockState getState() { return state; }
        public float getOriginalSpeed() { return originalSpeed; }
        public float getNewSpeed() { return newSpeed; }
        public void setNewSpeed(float speed) { this.newSpeed = speed; }
        public BlockPos getPos() { return pos; }
    }

    public static class ItemCraftedEvent extends PlayerEvent {
        private final ItemStack crafting;
        private final Container inventory;

        public ItemCraftedEvent(Player player, ItemStack crafting, Container inventory) {
            super(player);
            this.crafting = crafting;
            this.inventory = inventory;
        }

        public ItemStack getCrafting() { return crafting; }
        public Container getInventory() { return inventory; }
    }

    public static class ItemSmeltedEvent extends PlayerEvent {
        private final ItemStack smelting;

        public ItemSmeltedEvent(Player player, ItemStack smelting) {
            super(player);
            this.smelting = smelting;
        }

        public ItemStack getSmelting() { return smelting; }
    }
}
