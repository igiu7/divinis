package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

/**
 * The time to add HUD layers: above a vanilla layer (VanillaGuiLayers), above everything or below everything.
 * The layers are kept in LAYERS; each loader draws them in place (NeoForge with its layers, Fabric from the HUD).
 */
public class RegisterGuiLayersEvent extends Event {
    public enum Where { BELOW_ALL, ABOVE, ABOVE_ALL }

    public record Entry(Where where, ResourceLocation other, ResourceLocation id, LayeredDraw.Layer layer) {}

    public static final List<Entry> LAYERS = new ArrayList<>();

    public void registerAbove(ResourceLocation other, ResourceLocation id, LayeredDraw.Layer layer) {
        LAYERS.add(new Entry(Where.ABOVE, other, id, layer));
    }

    public void registerAboveAll(ResourceLocation id, LayeredDraw.Layer layer) {
        LAYERS.add(new Entry(Where.ABOVE_ALL, null, id, layer));
    }

    public void registerBelowAll(ResourceLocation id, LayeredDraw.Layer layer) {
        LAYERS.add(new Entry(Where.BELOW_ALL, null, id, layer));
    }
}
