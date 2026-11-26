package com.divinis.boss;

import com.divinis.screenfx.ScreenEffect;
import com.divinis.screenfx.ScreenEffect.Anchor;
import com.divinis.screenfx.ScreenFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for the bosses' deaths: each boss plays its own death over several seconds in its tickDeath (counting its
 * own ticks, not vanilla's deathTime, so the body neither tips over nor reddens while its death animation plays) and
 * ends with a burst for the eyes only - nothing is broken, nobody hurt.
 */
public final class BossDeath {
    private BossDeath() {}

    /** How far the bosses' deaths are seen and felt (blocks). */
    public static final double REACH = 56;

    public static void shake(Entity boss, int ticks, float strength) {
        if (boss.level() instanceof ServerLevel level) ScreenFx.shake(level, boss.position(), REACH, ticks, strength);
    }

    /** A whole-screen effect for everyone near. */
    public static void screen(Entity boss, ScreenEffect effect, int ticks, float strength, int color) {
        if (boss.level() instanceof ServerLevel level) ScreenFx.playNear(level, boss.position(), REACH, effect, ticks, strength, color);
    }

    /** An effect on (or round, or everywhere but) the boss, for everyone near. */
    public static void on(Entity boss, Anchor anchor, float area, ScreenEffect effect, int ticks, float strength, int color) {
        ScreenFx.playOnNear(boss, REACH, anchor, area, effect, ticks, strength, color);
    }

    public static void sound(Entity boss, SoundEvent sound, float volume, float pitch) {
        boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** A cloud of particles round a point. */
    public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Particles thrown out from a point every way. */
    public static void sphere(ServerLevel level, ParticleOptions p, Vec3 at, int count, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(speed * (0.6 + r.nextDouble() * 0.6));
            level.sendParticles(p, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
        }
    }

    /** Particles drawn in to a point from round it. */
    public static void implode(ServerLevel level, ParticleOptions p, Vec3 at, double radius, int count, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vec3 from = at.add(d.scale(radius));
            Vec3 v = d.scale(-speed);
            level.sendParticles(p, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
        }
    }

    /** A ring rolling out over the ground. */
    public static void ring(ServerLevel level, ParticleOptions p, Vec3 at, double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.sendParticles(p, at.x + cx * radius, at.y + 0.2, at.z + cz * radius, 0, cx * speed, 0.02, cz * speed, 1.0);
        }
    }

    /** Dust of the ground under a point. */
    public static void dust(ServerLevel level, Vec3 at, int count, double spread) {
        BlockState under = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!under.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.2, at.z, count, spread, 0.3, spread, 0.2);
        }
    }
}
