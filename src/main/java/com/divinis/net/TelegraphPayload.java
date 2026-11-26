package com.divinis.net;

import com.divinis.boss.Telegraph;
import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: show a boss attack warning on the ground (boss.Telegraphs) for warmup ticks, then (if active > 0)
 * the area burning for active ticks, turning by spin degrees a tick, then its flash for linger ticks. warmup < 0
 * removes the warning with this id (the attack was called off).
 */
public record TelegraphPayload(int id, Telegraph telegraph, int warmup, int linger, int active, float spin) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TelegraphPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "telegraph"));

    public static final StreamCodec<ByteBuf, TelegraphPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelegraphPayload::id,
            Telegraph.CODEC, TelegraphPayload::telegraph,
            ByteBufCodecs.VAR_INT, TelegraphPayload::warmup,
            ByteBufCodecs.VAR_INT, TelegraphPayload::linger,
            ByteBufCodecs.VAR_INT, TelegraphPayload::active,
            ByteBufCodecs.FLOAT, TelegraphPayload::spin,
            TelegraphPayload::new);

    public static TelegraphPayload cancel(int id) {
        return new TelegraphPayload(id, Telegraph.circle(net.minecraft.world.phys.Vec3.ZERO, 0), -1, 0, 0, 0);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
