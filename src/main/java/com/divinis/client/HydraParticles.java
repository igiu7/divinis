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
 * The Hydra's particles (registry.ModParticles; textures from tools/build_hydra_fx.py):
 * - FROST: a glittering shard of frost, flying with the speed it is sent with, slowing, glinting out (it glows a little);
 * - HYDRA_BLOOD: a gout of its dark blood, thrown out, falling, splattering on what it lands on;
 * - BOG_SPLASH: bog water thrown up, falling back in drops.
 */
public final class HydraParticles {
    private HydraParticles() {}

    private abstract static class Base extends TextureSheetParticle {
        final SpriteSet sprites;

        Base(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            xd = vx;
            yd = vy;
            zd = vz;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Frost extends Base {
        Frost(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx + (Math.random() - 0.5) * 0.02, vy, vz + (Math.random() - 0.5) * 0.02, sprites);
            lifetime = 16 + random.nextInt(14);
            quadSize = 0.12F + random.nextFloat() * 0.12F;
            friction = 0.88F;
            gravity = 0.01F;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            oRoll = roll;
            roll += 0.08F;
            alpha = 1 - (float) age / lifetime * 0.6F;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.pack(12, 15);
        }
    }

    public static final class Blood extends Base {
        private boolean landed;

        Blood(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites);
            lifetime = 40 + random.nextInt(30);
            quadSize = 0.1F + random.nextFloat() * 0.1F;
            friction = 0.96F;
            gravity = 0.8F;
            hasPhysics = true;
            setSprite(sprites.get(0, 7));
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            if (onGround && !landed) {
                landed = true;
                age = Math.max(age, lifetime - 24);
                quadSize *= 1.6F;
            }
            setSprite(sprites.get(landed ? Math.min(7, 3 + (age - (lifetime - 24)) / 6) : Math.min(2, age / 6), 7));
            if (landed) alpha = Math.min(1, (lifetime - age) / 12F);
        }
    }

    public static final class Splash extends Base {
        Splash(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx + (Math.random() - 0.5) * 0.08, vy + 0.08 + Math.random() * 0.1, vz + (Math.random() - 0.5) * 0.08, sprites);
            lifetime = 14 + random.nextInt(12);
            quadSize = 0.2F + random.nextFloat() * 0.25F;
            friction = 0.95F;
            gravity = 0.7F;
            hasPhysics = true;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            alpha = 1 - (float) age / lifetime * 0.7F;
            if (onGround) remove();
        }
    }

    public record FrostProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Frost(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record BloodProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Blood(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record SplashProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Splash(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
