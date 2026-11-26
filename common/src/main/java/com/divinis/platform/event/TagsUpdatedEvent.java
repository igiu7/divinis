package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import net.minecraft.core.RegistryAccess;

/** The tags were (re)loaded: on the server after a reload, on the client when it gets them. */
public class TagsUpdatedEvent extends Event {
    private final RegistryAccess registries;
    private final boolean client;

    public TagsUpdatedEvent(RegistryAccess registries, boolean client) {
        this.registries = registries;
        this.client = client;
    }

    public RegistryAccess getRegistryAccess() { return registries; }
    public boolean isClient() { return client; }
}
