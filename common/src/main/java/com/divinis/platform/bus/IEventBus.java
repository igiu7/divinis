package com.divinis.platform.bus;

/** The mod's start-up bus (what NeoForge hands a mod); registers take it, Divinis' events go through DivinisBus. */
public interface IEventBus {
    IEventBus MOD = new IEventBus() {};
}
