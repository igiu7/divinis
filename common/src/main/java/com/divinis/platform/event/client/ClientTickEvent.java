package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;

public abstract class ClientTickEvent extends Event {
    public static class Pre extends ClientTickEvent {}

    public static class Post extends ClientTickEvent {}
}
