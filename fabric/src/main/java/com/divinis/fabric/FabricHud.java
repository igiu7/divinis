package com.divinis.fabric;

import com.divinis.platform.bus.DivinisBus;
import com.divinis.platform.event.client.RegisterGuiLayersEvent;
import com.divinis.platform.event.client.RenderGuiLayerEvent;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The HUD on Fabric (called from mixin.client.GuiMixin): vanilla's layers can be cancelled with RenderGuiLayerEvent.Pre,
 * Divinis' layers (RegisterGuiLayersEvent) are drawn below everything, above a vanilla layer, or above everything.
 */
public final class FabricHud {
    private FabricHud() {}

    public static DeltaTracker delta() {
        return Minecraft.getInstance().getTimer();
    }

    /** Whether the vanilla layer is drawn. */
    public static boolean pre(ResourceLocation layer, GuiGraphics g) {
        return !DivinisBus.hasListeners(RenderGuiLayerEvent.Pre.class)
                || !DivinisBus.post(new RenderGuiLayerEvent.Pre(layer, g, delta())).isCanceled();
    }

    public static void draw(RegisterGuiLayersEvent.Where where, ResourceLocation other, GuiGraphics g, DeltaTracker delta) {
        for (RegisterGuiLayersEvent.Entry l : RegisterGuiLayersEvent.LAYERS) {
            if (l.where() != where || (other != null && !other.equals(l.other()))) continue;
            if (!pre(l.id(), g)) continue;
            g.pose().pushPose();
            l.layer().render(g, delta);
            g.pose().popPose();
        }
    }
}
