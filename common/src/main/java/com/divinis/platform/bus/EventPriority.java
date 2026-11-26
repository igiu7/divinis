package com.divinis.platform.bus;

/** Order of the listeners of one event: HIGHEST first. */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
