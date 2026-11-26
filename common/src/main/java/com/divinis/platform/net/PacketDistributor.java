package com.divinis.platform.net;

import javax.annotation.Nullable;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Sends Divinis' payloads (registered with PayloadRegistrar), the same calls on every loader. */
public final class PacketDistributor {
    private PacketDistributor() {}

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkManager.sendToServer(payload);
    }

    /** To everyone in the level within radius blocks of the point, except excluded. */
    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z,
                                         double radius, CustomPacketPayload payload) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (p != excluded && p.distanceToSqr(x, y, z) <= r2) sendToPlayer(p, payload);
        }
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) sendToPlayer(p, payload);
    }

    /** To everyone who can see the entity (not the entity itself). */
    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false)) {
            if (p != entity) sendToPlayer(p, payload);
        }
    }

    /** To everyone who can see the entity, and to the entity when it is a player. */
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        sendToPlayersTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) sendToPlayer(self, payload);
    }

    public static void sendToAllPlayers(net.minecraft.server.MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendToPlayer(p, payload);
    }
}
