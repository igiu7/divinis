package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client: a red slash through space (client.SlashFx) from one point to another, bowed sideways by bend
 * (blocks), width across, staying life ticks; warp: how hard it bends the picture round it (0 none, 1 as a black hole).
 */
public record SlashPayload(Vec3 from, Vec3 to, float bend, float width, int life, float warp) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlashPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "slash"));

    public static final StreamCodec<ByteBuf, SlashPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.from().x);
                buf.writeDouble(p.from().y);
                buf.writeDouble(p.from().z);
                buf.writeDouble(p.to().x);
                buf.writeDouble(p.to().y);
                buf.writeDouble(p.to().z);
                buf.writeFloat(p.bend());
                buf.writeFloat(p.width());
                buf.writeInt(p.life());
                buf.writeFloat(p.warp());
            },
            buf -> new SlashPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
