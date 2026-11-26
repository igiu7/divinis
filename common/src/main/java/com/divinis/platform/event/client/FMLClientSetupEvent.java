package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;

/** The client is set up (registries are filled): enqueueWork runs on the main thread. */
public abstract class FMLClientSetupEvent extends Event {
    public abstract void enqueueWork(Runnable work);
}
