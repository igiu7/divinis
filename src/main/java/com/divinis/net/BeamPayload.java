package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: a beam from one point to another (client.BeamFx), `width` blocks wide, for `life` ticks: a red
 * LASER (a warning), a beam of LIGHT, or a wide soft SHAFT of light. Sending the same id again moves it (smoothly)
 * and keeps it on - or changes it, a laser into light.
 */
public record BeamPayload(int id, byte kind, double fx, double fy, double fz, double tx, double ty, double tz, float width, int life)
        implements CustomPacketPayload {
    public static final byte LASER = 0, LIGHT = 1, SHAFT = 2;

    public static final CustomPacketPayload.Type<BeamPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "beam"));

    public static final StreamCodec<ByteBuf, BeamPayload> CODEC = StreamCodec.of(
            (raw, p) -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                buf.writeVarInt(p.id());
                buf.writeByte(p.kind());
                buf.writeDouble(p.fx());
                buf.writeDouble(p.fy());
                buf.writeDouble(p.fz());
                buf.writeDouble(p.tx());
                buf.writeDouble(p.ty());
                buf.writeDouble(p.tz());
                buf.writeFloat(p.width());
                buf.writeVarInt(p.life());
            },
            raw -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                return new BeamPayload(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
