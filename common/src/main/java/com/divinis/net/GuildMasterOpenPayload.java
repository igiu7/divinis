package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the player right-clicked the Guild Master, open his quests. Carries the Guild Master's entity id
 * (to show him on the screen).
 */
public record GuildMasterOpenPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GuildMasterOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "guild_master_open"));
    public static final StreamCodec<ByteBuf, GuildMasterOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuildMasterOpenPayload::entityId,
            GuildMasterOpenPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
