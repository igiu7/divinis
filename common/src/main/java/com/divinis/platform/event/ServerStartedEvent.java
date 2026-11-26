package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

public class ServerStartedEvent extends Event {
    private final MinecraftServer server;

    public ServerStartedEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
