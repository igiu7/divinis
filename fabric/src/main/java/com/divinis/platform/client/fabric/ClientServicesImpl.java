package com.divinis.platform.client.fabric;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

public final class ClientServicesImpl {
    private ClientServicesImpl() {}

    public static BakedModel extraModel(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(id);
    }

    /** Fabric's frame buffers have no stencil. */
    public static boolean isStencilEnabled(RenderTarget target) {
        return false;
    }

    public static void enableStencil(RenderTarget target) {}
}
