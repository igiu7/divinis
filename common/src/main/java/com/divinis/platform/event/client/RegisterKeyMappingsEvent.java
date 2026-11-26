package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import net.minecraft.client.KeyMapping;

/** The time to add key mappings (each loader adds them its way). */
public abstract class RegisterKeyMappingsEvent extends Event {
    public abstract void register(KeyMapping key);
}
