package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;

/** The local player's movement input was read from the keys (it can be changed). */
public class MovementInputUpdateEvent extends Event {
    private final Player player;
    private final Input input;

    public MovementInputUpdateEvent(Player player, Input input) {
        this.player = player;
        this.input = input;
    }

    public Player getEntity() { return player; }
    public Input getInput() { return input; }
}
