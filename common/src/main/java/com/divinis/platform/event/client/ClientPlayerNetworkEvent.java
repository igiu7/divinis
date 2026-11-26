package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import javax.annotation.Nullable;

import net.minecraft.client.player.LocalPlayer;

public abstract class ClientPlayerNetworkEvent extends Event {
    private final LocalPlayer player;

    protected ClientPlayerNetworkEvent(@Nullable LocalPlayer player) {
        this.player = player;
    }

    @Nullable
    public LocalPlayer getPlayer() { return player; }

    public static class LoggingIn extends ClientPlayerNetworkEvent {
        public LoggingIn(LocalPlayer player) {
            super(player);
        }
    }

    /** The client leaves the world (also when it never quite joined). */
    public static class LoggingOut extends ClientPlayerNetworkEvent {
        public LoggingOut(@Nullable LocalPlayer player) {
            super(player);
        }
    }
}
