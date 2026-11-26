package com.divinis.platform.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.divinis.platform.Dist;

/** A class whose static @SubscribeEvent methods listen to Divinis' events (listed in Subscribers). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "divinis";

    /** The sides it is registered on (both by default). */
    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

    Bus bus() default Bus.GAME;

    enum Bus { GAME, MOD }
}
