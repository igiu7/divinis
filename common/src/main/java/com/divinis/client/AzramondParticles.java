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
 * Azramond's particles (registry.ModParticles; textures from tools/build_azramond_fx.py), all glowing:
 * - NECRO_FLAME: a tongue of green soul fire licking upwards and flickering out;
 * - CURSE: a purple rune of his curse, turning slowly as it fades;
 * - HOLY_LIGHT: a golden mote of the light the dead shun, drifting up and dimming.
 */
public final class AzramondParticles {
    private AzramondParticles() {}

    private abstract static class Glowing extends TextureSheetParticle {
        final SpriteSet sprites;

        Glowing(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
            hasPhysics = false;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
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

    public static final class Flame extends Glowing {
        private final float startSize;

        Flame(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx + (random.nextDouble() - 0.5) * 0.01;
            yd = vy + 0.02 + random.nextDouble() * 0.02;
            zd = vz + (random.nextDouble() - 0.5) * 0.01;
            lifetime = 14 + random.nextInt(12);
            startSize = quadSize = 0.18F + random.nextFloat() * 0.16F;
            friction = 0.9F;
            gravity = -0.01F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float t = (float) age / lifetime;
            quadSize = startSize * (1 - t * 0.4F);
            alpha = (1 - t * t) * (0.85F + 0.15F * Mth.sin(age * 1.9F));
        }
    }

    public static final class Curse extends Glowing {
        Curse(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx;
            yd = vy + 0.01;
            zd = vz;
            lifetime = 24 + random.nextInt(10);
            quadSize = 0.2F + random.nextFloat() * 0.1F;
            friction = 0.9F;
            gravity = 0;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            oRoll = roll;
            roll += 0.05F;
        }
    }

    public static final class Light extends Glowing {
        Light(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx * 0.5;
            yd = vy + 0.02 + random.nextDouble() * 0.02;
            zd = vz * 0.5;
            lifetime = 20 + random.nextInt(20);
            quadSize = 0.12F + random.nextFloat() * 0.12F;
            friction = 0.94F;
            gravity = -0.004F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) alpha = 1 - (float) age / lifetime;
        }
    }

    public record FlameProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Flame(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record CurseProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Curse(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record LightProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Light(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
