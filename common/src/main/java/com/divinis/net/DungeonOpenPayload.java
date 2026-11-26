package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: ask the Guild Master (entity id) to open the portal to the Labyrinth. */
public record DungeonOpenPayload(int guildMasterId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DungeonOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "dungeon_open"));
    public static final StreamCodec<ByteBuf, DungeonOpenPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(DungeonOpenPayload::new, DungeonOpenPayload::guildMasterId);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
