package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

public abstract class ServerTickEvent extends Event {
    private final MinecraftServer server;

    protected ServerTickEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public static class Pre extends ServerTickEvent {
        public Pre(MinecraftServer server) {
            super(server);
        }
    }

    public static class Post extends ServerTickEvent {
        public Post(MinecraftServer server) {
            super(server);
        }
    }
}
