package com.divinis.platform;

/** The physical side the game runs on. */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }
}
