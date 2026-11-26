package com.divinis.net;

import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The character's skills in play (skill.Dash): the skill key, the points the client needs to know, the dash seen. */
public final class SkillPayloads {
    private SkillPayloads() {}

    /** Client -> server: the dash key was pressed. */
    public record Use() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Use> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "skill_dash"));
        public static final StreamCodec<ByteBuf, Use> CODEC = StreamCodec.unit(new Use());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the points in each skill tree (SkillTree order) and the book in the fourth, so the client knows its skills. */
    public record Points(List<Integer> points, String book) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Points> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "skill_points"));
        public static final StreamCodec<ByteBuf, Points> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Points::points,
                ByteBufCodecs.stringUtf8(64), Points::book,
                Points::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> the dasher and everyone who sees them: this player dashes now (client.DashClient plays it). */
    public record Dash(int entity) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Dash> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "skill_dash_play"));
        public static final StreamCodec<ByteBuf, Dash> CODEC = ByteBufCodecs.VAR_INT.map(Dash::new, Dash::entity);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
