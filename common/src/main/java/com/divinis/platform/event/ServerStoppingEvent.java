package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

public class ServerStoppingEvent extends Event {
    private final MinecraftServer server;

    public ServerStoppingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
