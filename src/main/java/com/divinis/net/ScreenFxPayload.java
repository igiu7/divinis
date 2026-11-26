package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: put a screen effect (screenfx.ScreenEffect, by its index) on the screen for `ticks` at
 * `strength`, with a colour (0xRRGGBB, -1 for its own) and words (for TEXT); where (anchor: ScreenEffect.Anchor's
 * index, on entity `entity`, radius in blocks for AREA); effect CLEAR takes all of them off.
 */
public record ScreenFxPayload(int effect, int ticks, float strength, int color, String text, int anchor, int entity, float radius)
        implements CustomPacketPayload {
    public static final int CLEAR = 255;

    public static final CustomPacketPayload.Type<ScreenFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "screen_fx"));

    public static final StreamCodec<ByteBuf, ScreenFxPayload> CODEC = StreamCodec.of(
            (raw, p) -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                buf.writeByte(p.effect());
                buf.writeVarInt(p.ticks());
                buf.writeFloat(p.strength());
                buf.writeInt(p.color());
                buf.writeUtf(p.text(), 256);
                buf.writeByte(p.anchor());
                buf.writeVarInt(p.entity());
                buf.writeFloat(p.radius());
            },
            raw -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                return new ScreenFxPayload(buf.readUnsignedByte(), buf.readVarInt(), buf.readFloat(), buf.readInt(), buf.readUtf(256),
                        buf.readUnsignedByte(), buf.readVarInt(), buf.readFloat());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
