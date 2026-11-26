package com.divinis.platform.bus;

/** Base of Divinis' events; those that implement ICancellableEvent can be cancelled. */
public abstract class Event {
    private boolean canceled;

    boolean canceledInternal() {
        return canceled;
    }

    void setCanceledInternal(boolean canceled) {
        this.canceled = canceled;
    }
}
