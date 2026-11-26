package com.divinis.net;

import java.util.ArrayList;
import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The Sphinx's riddle (boss.Sphinx): the server asks it, a player answers, the server closes it for everyone. */
public final class RiddlePayloads {
    private RiddlePayloads() {}

    /** Server -> client: answer this within ticks (client.RiddleScreen), for boss `boss`. */
    public record Open(int boss, String question, List<String> options, int ticks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Open> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "riddle_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.boss());
                    buf.writeUtf(p.question(), 512);
                    buf.writeVarInt(p.options().size());
                    for (String o : p.options()) buf.writeUtf(o, 128);
                    buf.writeVarInt(p.ticks());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    int boss = buf.readVarInt();
                    String question = buf.readUtf(512);
                    int n = Math.min(buf.readVarInt(), 8);
                    List<String> options = new ArrayList<>();
                    for (int i = 0; i < n; i++) options.add(buf.readUtf(128));
                    return new Open(boss, question, options, buf.readVarInt());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: the answer chosen (its index among the options). */
    public record Answer(int boss, int choice) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Answer> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "riddle_answer"));
        public static final StreamCodec<ByteBuf, Answer> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.boss());
                    buf.writeVarInt(p.choice());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    return new Answer(buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the riddle is over (answered, failed, or this player's answer was wrong) - close it. */
    public record Close(int boss) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Close> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "riddle_close"));
        public static final StreamCodec<ByteBuf, Close> CODEC = StreamCodec.of(
                (raw, p) -> new FriendlyByteBuf(raw).writeVarInt(p.boss()),
                raw -> new Close(new FriendlyByteBuf(raw).readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
