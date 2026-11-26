package com.divinis.platform.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/** The client's per-loader parts (com.divinis.platform.fabric/neoforge.client.ClientServicesImpl). */
public final class ClientServices {
    private ClientServices() {}

    /** An extra model asked for with ModelEvent.RegisterAdditional (the missing model when it was not). */
    @ExpectPlatform
    public static BakedModel extraModel(ResourceLocation id) {
        throw new AssertionError();
    }

    /** Whether the frame buffer has a stencil (NeoForge turns one on when a mod asks; Fabric's never has). */
    @ExpectPlatform
    public static boolean isStencilEnabled(RenderTarget target) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void enableStencil(RenderTarget target) {
        throw new AssertionError();
    }
}
