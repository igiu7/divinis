package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import net.minecraft.resources.ResourceLocation;

public abstract class ModelEvent extends Event {
    /** The time to ask for extra models (loaded and baked though no block or item uses them; see ExtraModels). */
    public abstract static class RegisterAdditional extends ModelEvent {
        public abstract void register(ResourceLocation model);
    }
}
