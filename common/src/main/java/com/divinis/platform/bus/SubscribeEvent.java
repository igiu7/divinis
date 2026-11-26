package com.divinis.platform.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A static method that listens to the event of its only parameter (see DivinisBus). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;

    /** Also called for an event an earlier listener cancelled. */
    boolean receiveCanceled() default false;
}
