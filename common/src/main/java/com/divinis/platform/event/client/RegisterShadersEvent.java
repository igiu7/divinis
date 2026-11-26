package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** The core shaders are (re)loaded: add one from assets/<namespace>/shaders/core/<path>.json. */
public abstract class RegisterShadersEvent extends Event {
    public abstract void register(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> loaded);
}
