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
 * A puff of steam (registry.ModParticles.STEAM): it leaves with the speed it was given, slows in the air, rises,
 * swells to several times its size and thins away, its frames going from a dense puff to a wisp breaking up.
 */
public class SteamParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected SteamParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.xd = vx + (random.nextDouble() - 0.5) * 0.02;
        this.yd = vy + 0.01 + random.nextDouble() * 0.02;
        this.zd = vz + (random.nextDouble() - 0.5) * 0.02;
        this.lifetime = 28 + random.nextInt(24);
        this.startSize = 0.35F + random.nextFloat() * 0.3F;
        this.quadSize = startSize;
        this.friction = 0.9F;
        this.gravity = -0.025F;
        this.hasPhysics = false;
        float shade = 0.88F + random.nextFloat() * 0.12F;
        setColor(shade, shade, Math.min(1.0F, shade + 0.03F));
        this.alpha = 0.85F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float t = (float) age / lifetime;
        quadSize = startSize * (1.0F + 3.0F * Mth.sqrt(t));
        alpha = 0.85F * (1.0F - t * t);
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
            return new SteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
