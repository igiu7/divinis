package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** The time to give particle types their providers. */
public abstract class RegisterParticleProvidersEvent extends Event {
    public abstract <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type,
                                                                       ParticleEngine.SpriteParticleRegistration<T> registration);
}
