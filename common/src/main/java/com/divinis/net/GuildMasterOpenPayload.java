package com.divinis.net;

import java.util.List;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the player right-clicked the Guild Master, open his menu. Carries the Guild Master's entity id
 * (for asking him to open a dungeon portal) and the ranking of the fastest dungeon clears.
 */
public record GuildMasterOpenPayload(int entityId, List<RankRow> ranking) implements CustomPacketPayload {
    /** One clear: the party, how long it took (ticks), how many players. */
    public record RankRow(String names, int ticks, int players) {
        public static final StreamCodec<ByteBuf, RankRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RankRow::names,
                ByteBufCodecs.VAR_INT, RankRow::ticks,
                ByteBufCodecs.VAR_INT, RankRow::players,
                RankRow::new);
    }

    public static final CustomPacketPayload.Type<GuildMasterOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "guild_master_open"));
    public static final StreamCodec<ByteBuf, GuildMasterOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuildMasterOpenPayload::entityId,
            RankRow.CODEC.apply(ByteBufCodecs.list(20)), GuildMasterOpenPayload::ranking,
            GuildMasterOpenPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
