package com.divinis.net;

import java.util.ArrayList;
import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Nocthariel's trial of the shape: the server asks a player to draw a shape, the player sends back what they drew. */
public final class DrawPayloads {
    private DrawPayloads() {}

    private static final int MAX_POINTS = 4000;

    /** Server -> client: draw shape `shape` (boss.DrawShapes) within `ticks`, for boss `boss`. */
    public record Open(int boss, int shape, int ticks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Open> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "draw_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.boss());
                    buf.writeVarInt(p.shape());
                    buf.writeVarInt(p.ticks());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    return new Open(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the drawing - strokes, each x, y, x, y... (0..1 across the canvas). */
    public record Submit(int boss, List<float[]> strokes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Submit> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "draw_submit"));
        public static final StreamCodec<ByteBuf, Submit> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.boss());
                    int budget = MAX_POINTS;
                    List<float[]> out = new ArrayList<>();
                    for (float[] s : p.strokes()) {
                        if (budget <= 0) break;
                        int n = Math.min(s.length / 2, budget);
                        budget -= n;
                        float[] c = new float[n * 2];
                        System.arraycopy(s, 0, c, 0, n * 2);
                        out.add(c);
                    }
                    buf.writeVarInt(out.size());
                    for (float[] s : out) {
                        buf.writeVarInt(s.length / 2);
                        for (float f : s) buf.writeFloat(f);
                    }
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    int boss = buf.readVarInt();
                    int strokes = Math.min(buf.readVarInt(), 400);
                    List<float[]> out = new ArrayList<>();
                    int budget = MAX_POINTS;
                    for (int i = 0; i < strokes; i++) {
                        int n = buf.readVarInt();
                        if (n < 0 || n > budget) throw new IllegalArgumentException("too many points");
                        budget -= n;
                        float[] s = new float[n * 2];
                        for (int j = 0; j < s.length; j++) s[j] = buf.readFloat();
                        out.add(s);
                    }
                    return new Submit(boss, out);
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
