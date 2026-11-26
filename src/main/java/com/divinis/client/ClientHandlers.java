package com.divinis.client;

import com.divinis.net.DrawPayloads;
import com.divinis.net.ForgeKnownPayload;
import com.divinis.net.GuildMasterOpenPayload;
import com.divinis.net.RiddlePayloads;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side handlers of the packets that open screens. */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onForgeKnown(ForgeKnownPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientForge.setKnown(payload.knownTechs(), payload.all(), payload.techNames()));
    }

    public static void onGuildMasterOpen(GuildMasterOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> new com.divinis.client.gui.GuildMasterScreen(payload).openGui());
    }

    public static void onRiddleOpen(RiddlePayloads.Open payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance()
                .setScreen(new RiddleScreen(payload.boss(), payload.question(), payload.options(), payload.ticks())));
    }

    public static void onRiddleClose(RiddlePayloads.Close payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof RiddleScreen screen) screen.close();
        });
    }

    public static void onDrawOpen(DrawPayloads.Open payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance()
                .setScreen(new DrawingScreen(payload.boss(), payload.shape(), payload.ticks())));
    }
}
