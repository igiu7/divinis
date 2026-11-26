package com.divinis.net;

import java.util.ArrayList;
import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The fishing minigame (fishing.FishingGame, client.FishingHud) and the foam of a fish swimming in (client.FishingFoam). */
public final class FishingPayloads {
    private FishingPayloads() {}

    public static final int CANCELLED = 0, CAUGHT = 1, ESCAPED = 2;

    /**
     * Server -> client: a fish bites on hook `hook`. The needle starts at `start` (degrees, 0 at the top, clockwise)
     * and turns `speed` degrees a tick; each zone is `half` degrees either side of its middle (`zones`). One zone hit
     * catches the fish - or, `sequence`, all of them in their order; after `turns` turns the fish is gone. `title`
     * over the wheel ("" for the usual).
     */
    public record Start(int hook, float start, float speed, List<Float> zones, float half, int turns, boolean sequence,
                        String title) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Start> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing_start"));
        public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.hook());
                    buf.writeFloat(p.start());
                    buf.writeFloat(p.speed());
                    buf.writeVarInt(p.zones().size());
                    for (float z : p.zones()) buf.writeFloat(z);
                    buf.writeFloat(p.half());
                    buf.writeVarInt(p.turns());
                    buf.writeBoolean(p.sequence());
                    buf.writeUtf(p.title(), 64);
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    int hook = buf.readVarInt();
                    float start = buf.readFloat(), speed = buf.readFloat();
                    int n = Math.min(buf.readVarInt(), 8);
                    List<Float> zones = new ArrayList<>();
                    for (int i = 0; i < n; i++) zones.add(buf.readFloat());
                    return new Start(hook, start, speed, zones, buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64));
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: space was pressed - the zone was hit (`perfect`: in its very middle) or not. */
    public record Press(int hook, boolean hit, boolean perfect) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Press> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing_press"));
        public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.hook());
                    buf.writeBoolean(p.hit());
                    buf.writeBoolean(p.perfect());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    return new Press(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server -> client: the fish swimming to hook `hook` is here now (on top of the water block below y); `color` tints
     * its foam (0: white), `strong` foam lasts longer and spreads wider (the hound's rod).
     */
    public record Foam(int hook, double x, double y, double z, int color, boolean strong) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Foam> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing_foam"));
        public static final StreamCodec<ByteBuf, Foam> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.hook());
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    buf.writeInt(p.color());
                    buf.writeBoolean(p.strong());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    return new Foam(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readBoolean());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: what was caught (client.CatchPopups shows it, "+1" and the item, bottom left). */
    public record Caught(List<ItemStack> stacks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Caught> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing_caught"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Caught> CODEC =
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(16)).map(Caught::new, Caught::stacks);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the round on hook `hook` is over: CANCELLED, CAUGHT or ESCAPED. */
    public record End(int hook, int result) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<End> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "fishing_end"));
        public static final StreamCodec<ByteBuf, End> CODEC = StreamCodec.of(
                (raw, p) -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    buf.writeVarInt(p.hook());
                    buf.writeVarInt(p.result());
                },
                raw -> {
                    FriendlyByteBuf buf = new FriendlyByteBuf(raw);
                    return new End(buf.readVarInt(), buf.readVarInt());
                });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
