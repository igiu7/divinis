package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public abstract class RenderGuiLayerEvent extends Event {
    private final ResourceLocation name;
    private final GuiGraphics graphics;
    private final DeltaTracker partialTick;

    protected RenderGuiLayerEvent(ResourceLocation name, GuiGraphics graphics, DeltaTracker partialTick) {
        this.name = name;
        this.graphics = graphics;
        this.partialTick = partialTick;
    }

    /** The layer (VanillaGuiLayers or one of Divinis'). */
    public ResourceLocation getName() { return name; }
    public GuiGraphics getGuiGraphics() { return graphics; }
    public DeltaTracker getPartialTick() { return partialTick; }

    /** A HUD layer is about to be drawn (cancelled: it is not). */
    public static class Pre extends RenderGuiLayerEvent implements ICancellableEvent {
        public Pre(ResourceLocation name, GuiGraphics graphics, DeltaTracker partialTick) {
            super(name, graphics, partialTick);
        }
    }
}
