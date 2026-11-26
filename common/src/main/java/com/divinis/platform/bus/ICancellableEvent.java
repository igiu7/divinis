package com.divinis.platform.bus;

/** An event a listener can cancel: what it stands for then does not happen. */
public interface ICancellableEvent {
    default void setCanceled(boolean canceled) {
        ((Event) this).setCanceledInternal(canceled);
    }

    default boolean isCanceled() {
        return ((Event) this).canceledInternal();
    }
}
