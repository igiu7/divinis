package com.divinis.net;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.divinis.Divinis;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the player opened the anvil (forge.AnvilForge): the technologies they know (all: the world has no
 * technologies, every part may be forged), and their names.
 */
public record ForgeKnownPayload(List<String> knownTechs, boolean all, Map<String, String> techNames) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ForgeKnownPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "forge_known"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeKnownPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeCollection(p.knownTechs(), (b, s) -> b.writeUtf(s));
                buf.writeBoolean(p.all());
                buf.writeVarInt(p.techNames().size());
                p.techNames().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeUtf(v);
                });
            },
            buf -> {
                List<String> known = buf.readList(b -> b.readUtf());
                boolean all = buf.readBoolean();
                int n = buf.readVarInt();
                Map<String, String> names = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) names.put(buf.readUtf(), buf.readUtf());
                return new ForgeKnownPayload(known, all, names);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
