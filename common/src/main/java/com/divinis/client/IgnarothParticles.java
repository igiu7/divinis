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
 * Ignaroth's particles (registry.ModParticles; textures from tools/build_ignaroth_fx.py):
 * - EMBER: a glowing ember, rising and drifting, cooling from white-hot to a dull red speck;
 * - DRAGON_FIRE: a billow of his fire, flying with the speed it is sent with, swelling, breaking up into smoke (glowing);
 * - ASH: a flake of ash, its edge still glowing, drifting slowly down, turning.
 */
public final class IgnarothParticles {
    private IgnarothParticles() {}

    private abstract static class Base extends TextureSheetParticle {
        final SpriteSet sprites;

        Base(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) setSpriteFromAge(sprites);
        }
    }

    public static final class Ember extends Base {
        Ember(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx + (random.nextDouble() - 0.5) * 0.04;
            yd = vy + 0.03 + random.nextDouble() * 0.04;
            zd = vz + (random.nextDouble() - 0.5) * 0.04;
            lifetime = 20 + random.nextInt(30);
            quadSize = 0.06F + random.nextFloat() * 0.08F;
            friction = 0.94F;
            gravity = -0.01F;
            hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            xd += Mth.sin(age * 0.3F) * 0.003;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    public static final class Fire extends Base {
        private final float startSize;

        Fire(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 14 + random.nextInt(10);
            startSize = quadSize = 0.4F + random.nextFloat() * 0.35F;
            friction = 0.9F;
            gravity = -0.02F;
            hasPhysics = true;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float t = (float) age / lifetime;
            quadSize = startSize * (1 + 1.6F * t);
            alpha = 1 - t * t;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    public static final class Ash extends Base {
        Ash(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx + (random.nextDouble() - 0.5) * 0.05;
            yd = vy + random.nextDouble() * 0.05;
            zd = vz + (random.nextDouble() - 0.5) * 0.05;
            lifetime = 50 + random.nextInt(40);
            quadSize = 0.1F + random.nextFloat() * 0.1F;
            friction = 0.92F;
            gravity = 0.02F;
            hasPhysics = true;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            oRoll = roll;
            roll += 0.06F;
            alpha = 1 - (float) age / lifetime;
        }
    }

    public record EmberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Ember(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record FireProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Fire(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record AshProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Ash(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
