package com.divinis.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The Barrow Chieftain's particles (registry.ModParticles; textures from tools/build_barrow_fx.py):
 * - GHOST_FLAME: a tongue of pale soul fire, glowing, licking upwards (with the speed it is sent with) and flickering out;
 * - BONE_DUST: flecks of grave dust and crumbled bone, thrown out, then drifting slowly down and thinning.
 */
public final class BarrowParticles {
    private BarrowParticles() {}

    public static final class GhostFlame extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float startSize;

        GhostFlame(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            xd = vx + (random.nextDouble() - 0.5) * 0.01;
            yd = vy + 0.02 + random.nextDouble() * 0.02;
            zd = vz + (random.nextDouble() - 0.5) * 0.01;
            lifetime = 14 + random.nextInt(12);
            startSize = quadSize = 0.2F + random.nextFloat() * 0.18F;
            friction = 0.9F;
            gravity = -0.01F;
            hasPhysics = false;
            alpha = 0.9F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            quadSize = startSize * (1 - t * 0.4F);
            alpha = 0.9F * (1 - t * t) * (0.85F + 0.15F * Mth.sin(age * 1.7F));
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class BoneDust extends TextureSheetParticle {
        private final SpriteSet sprites;

        BoneDust(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            xd = vx + (random.nextDouble() - 0.5) * 0.05;
            yd = vy + random.nextDouble() * 0.04;
            zd = vz + (random.nextDouble() - 0.5) * 0.05;
            lifetime = 30 + random.nextInt(30);
            quadSize = 0.12F + random.nextFloat() * 0.14F;
            friction = 0.9F;
            gravity = 0.05F;
            hasPhysics = true;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            alpha = 0.95F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            alpha = 0.95F * (1 - t * t);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public record GhostFlameProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GhostFlame(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record BoneDustProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BoneDust(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
