package com.divinis.client;

import com.divinis.registry.ModParticles;

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
 * The Arachnid's particles (registry.ModParticles; textures from tools/build_arachnid_fx.py):
 * - SILK: a wisp of torn web, drifting down slowly, swaying and turning, thinning to a few threads;
 * - ACID: a drop of acid falling (the speed it is sent with, then gravity), glowing; where it lands it bursts into
 *   ACID_SPLASH;
 * - ACID_SPLASH: a ring of green droplets spreading out from where a drop landed, glowing, quick;
 * - VENOM: a puff of poison mist, rising a little, spreading and thinning.
 */
public final class ArachnidParticles {
    private ArachnidParticles() {}

    private abstract static class Base extends TextureSheetParticle {
        protected final SpriteSet sprites;

        Base(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.sprites = sprites;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Silk extends Base {
        private final float sway = random.nextFloat() * Mth.TWO_PI, spin = (random.nextFloat() - 0.5F) * 0.12F;

        Silk(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx + (random.nextDouble() - 0.5) * 0.04;
            yd = vy + random.nextDouble() * 0.02;
            zd = vz + (random.nextDouble() - 0.5) * 0.04;
            lifetime = 40 + random.nextInt(40);
            quadSize = 0.18F + random.nextFloat() * 0.18F;
            friction = 0.92F;
            gravity = 0.012F;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            alpha = 0.95F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            oRoll = roll;
            roll += spin;
            xd += Mth.sin(age * 0.2F + sway) * 0.003;
            float t = (float) age / lifetime;
            alpha = 0.95F * (1 - t * t);
        }
    }

    public static final class Acid extends Base {
        Acid(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 80;
            quadSize = 0.14F + random.nextFloat() * 0.06F;
            gravity = 0.9F;
            friction = 1.0F;
            hasPhysics = true;
            setSprite(sprites.get(0, 3));
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSprite(sprites.get(Math.min(3, age / 3), 3));
            if (onGround) {
                level.addParticle(ModParticles.ACID_SPLASH.get(), x, y + 0.05, z, 0, 0, 0);
                if (random.nextBoolean()) level.addParticle(ModParticles.VENOM.get(), x, y + 0.1, z, 0, 0.01, 0);
                remove();
            }
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    public static final class AcidSplash extends Base {
        AcidSplash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = yd = zd = 0;
            lifetime = 10 + random.nextInt(5);
            quadSize = 0.35F + random.nextFloat() * 0.2F;
            gravity = 0;
            hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) setSpriteFromAge(sprites);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }
    }

    public static final class Venom extends Base {
        private final float startSize;

        Venom(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = vx + (random.nextDouble() - 0.5) * 0.02;
            yd = vy + random.nextDouble() * 0.01;
            zd = vz + (random.nextDouble() - 0.5) * 0.02;
            lifetime = 30 + random.nextInt(30);
            startSize = quadSize = 0.25F + random.nextFloat() * 0.3F;
            friction = 0.9F;
            gravity = -0.002F;
            hasPhysics = false;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            alpha = 0.85F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            quadSize = startSize * (1 + 1.8F * Mth.sqrt(t));
            alpha = 0.85F * (1 - t * t);
        }
    }

    public record SilkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Silk(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record AcidProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Acid(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record SplashProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new AcidSplash(level, x, y, z, sprites);
        }
    }

    public record VenomProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Venom(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
