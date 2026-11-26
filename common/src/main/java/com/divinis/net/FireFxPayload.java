package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: a fire effect of a boss at a place (client.FireFx):
 * - VOID_BURST: a burst of purple flame, a = size;
 * - VOID_LATTICE: a grid of purple flames, joined along its lines (a = radius, b = line spacing), rising after delay
 *   ticks and burning for duration ticks;
 * - RIFT: a swirling rift of purple fire, a = radius, for duration ticks, collapsing at the end;
 * - VOID_LINE: a line of purple flames from the place, a = its yaw, b = its length, after delay ticks for duration;
 * - VOID_WALL: a wall of purple flames across the room (the place is the room's middle), a = the yaw it travels
 *   along, b = where its gap is (sideways from the middle); it stands at the room's edge for a moment, then travels
 *   across for duration ticks (boss.SerpentOfInfinity.WALL_*);
 * - METEOR: a purple star falling onto the place for delay ticks, then bursting;
 * - VOID_RING: a ring of purple flames flaring up at radius a after delay ticks;
 * - VOID_SHOCK: a shock wave of purple flames rolling out from the place after delay ticks, for duration ticks
 *   (boss.SerpentOfInfinity.shockAt);
 * - WORLD_EYE: a giant eye opening in the air at the place, a = its width, for duration ticks - its pupil flares red
 *   before it fires (Aurelion's eclipse);
 * - LIGHT_SHIELD: a pane of light standing in the floor at the place, a = the way it faces (yaw), b = its width,
 *   for duration ticks (Aurelion's shields before its star collapses);
 * - MAW: a mouth opening in the floor at the place, a = its size; it bites after delay ticks (jaws of teeth rising)
 *   and is gone after duration (Nocthariel);
 * - DARK_SHOCK: a black-red shock wave rolling out from the place after delay ticks, for duration ticks
 *   (boss.Nocthariel.shockAt).
 */
public record FireFxPayload(byte kind, double x, double y, double z, float a, float b, int delay, int duration)
        implements CustomPacketPayload {
    public static final byte VOID_BURST = 0, VOID_LATTICE = 1, RIFT = 2, VOID_LINE = 3, VOID_WALL = 4, METEOR = 5, VOID_RING = 6, VOID_SHOCK = 7,
            WORLD_EYE = 8, LIGHT_SHIELD = 9, MAW = 10, DARK_SHOCK = 11;

    public static final CustomPacketPayload.Type<FireFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fire_fx"));

    public static final StreamCodec<ByteBuf, FireFxPayload> CODEC = StreamCodec.of(
            (raw, p) -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                buf.writeByte(p.kind());
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.a());
                buf.writeFloat(p.b());
                buf.writeVarInt(p.delay());
                buf.writeVarInt(p.duration());
            },
            raw -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                return new FireFxPayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                        buf.readFloat(), buf.readVarInt(), buf.readVarInt());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
