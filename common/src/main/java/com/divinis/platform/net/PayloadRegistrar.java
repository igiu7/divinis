package com.divinis.platform.net;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Registers Divinis' payloads with Architectury's NetworkManager (each loader's own networking underneath). */
public final class PayloadRegistrar {
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, type, codec, (payload, ctx) -> handler.handle(payload, wrap(ctx)));
        return this;
    }

    /** handler runs on the client only; on a dedicated server the type is just made known. */
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, codec, (payload, ctx) -> handler.handle(payload, wrap(ctx)));
        } else {
            NetworkManager.registerS2CPayloadType(type, codec);
        }
        return this;
    }

    private static IPayloadContext wrap(NetworkManager.PacketContext ctx) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                return ctx.getPlayer();
            }

            @Override
            public void enqueueWork(Runnable work) {
                ctx.queue(work);
            }
        };
    }
}
