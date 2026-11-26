package com.divinis.net;

import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: what a book skill shows only to its user (skill.SkillEffects, client.SkillFx), for `ticks`:
 * ORES - these blocks outlined through walls, each in its colour (ints); GLOW - these entities (ints: their ids)
 * glowing; POINTER - the golden arrow pointing at the first block.
 */
public record SkillFxPayload(int kind, List<BlockPos> blocks, List<Integer> ints, int ticks) implements CustomPacketPayload {
    public static final int ORES = 0, GLOW = 1, POINTER = 2;

    public static final CustomPacketPayload.Type<SkillFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "skill_fx"));
    public static final StreamCodec<ByteBuf, SkillFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillFxPayload::kind,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(512)), SkillFxPayload::blocks,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(512)), SkillFxPayload::ints,
            ByteBufCodecs.VAR_INT, SkillFxPayload::ticks,
            SkillFxPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
