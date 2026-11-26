package com.divinis.platform.client.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

public final class ClientServicesImpl {
    private ClientServicesImpl() {}

    public static BakedModel extraModel(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(id));
    }

    public static boolean isStencilEnabled(RenderTarget target) {
        return target.isStencilEnabled();
    }

    public static void enableStencil(RenderTarget target) {
        target.enableStencil();
    }
}
