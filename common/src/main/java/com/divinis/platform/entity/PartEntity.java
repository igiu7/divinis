package com.divinis.platform.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/**
 * A part of a big creature (MultipartEntity): an entity of its own size the parent moves every tick. It is never
 * saved nor sent to clients (each side makes the parts with the parent, their ids right after its id); hits on it
 * go to the parent. PartRegistry makes the level's searches find it.
 */
public abstract class PartEntity<T extends Entity> extends Entity {
    private final T parent;

    protected PartEntity(T parent) {
        super(parent.getType(), parent.level());
        this.parent = parent;
    }

    public T getParent() {
        return parent;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException("A part is never sent");
    }
}
