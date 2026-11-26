package com.divinis.net;

import java.util.Map;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player's quests (quest.QuestData), on login and whenever they change. */
public record QuestPayload(Map<String, Integer> active, Map<String, Integer> done) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "quests"));
    public static final StreamCodec<ByteBuf, QuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(java.util.LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT, 64), QuestPayload::active,
            ByteBufCodecs.map(java.util.LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT, 256), QuestPayload::done,
            QuestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
