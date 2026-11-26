package com.divinis.platform.net;

import net.minecraft.world.entity.player.Player;

/** Where a received payload came from: the player who sent it (server) or the local player (client). */
public interface IPayloadContext {
    Player player();

    /** Runs the work on the game thread (the payload arrives on the network thread). */
    void enqueueWork(Runnable work);
}
