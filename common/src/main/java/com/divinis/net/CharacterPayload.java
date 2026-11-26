package com.divinis.net;

import com.divinis.Divinis;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player's character (skill.CharacterService), for the skill trees and the HUD. */
public record CharacterPayload(int level, int levelXp, int levelNeed, int freePoints, int[] treePoints, String skillBook,
                               int resetSeconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CharacterPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "character"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.level);
                buf.writeVarInt(p.levelXp);
                buf.writeVarInt(p.levelNeed);
                buf.writeVarInt(p.freePoints);
                buf.writeVarIntArray(p.treePoints);
                buf.writeUtf(p.skillBook);
                buf.writeVarInt(p.resetSeconds);
            },
            buf -> new CharacterPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(), buf.readUtf(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
