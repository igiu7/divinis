package com.divinis.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A puff of blown sand (registry.ModParticles.SAND): it flies with the speed it was given, slows in the air, sinks a
 * little, spreads to a few times its size and thins away, its frames going from a dense puff to a thin haze.
 */
public class SandParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected SandParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.xd = vx + (random.nextDouble() - 0.5) * 0.03;
        this.yd = vy + (random.nextDouble() - 0.5) * 0.02;
        this.zd = vz + (random.nextDouble() - 0.5) * 0.03;
        this.lifetime = 24 + random.nextInt(26);
        this.startSize = 0.3F + random.nextFloat() * 0.35F;
        this.quadSize = startSize;
        this.friction = 0.93F;
        this.gravity = 0.004F;
        this.hasPhysics = false;
        float shade = 0.9F + random.nextFloat() * 0.1F;
        setColor(shade, shade * 0.97F, shade * 0.92F);
        this.alpha = 0.9F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float t = (float) age / lifetime;
        quadSize = startSize * (1.0F + 2.5F * Mth.sqrt(t));
        alpha = 0.9F * (1.0F - t * t);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SandParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
