package com.divinis.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Whom a boss goes for (one per boss, server side):
 * - a player who hits it three times in a row (nobody else hitting in between) draws it onto themselves; it stays
 *   on them until they have not hit it for 15 seconds (every hit of theirs starts the 15 seconds again), or until
 *   another player hits it three times in a row;
 * - otherwise (nobody holding it - also when nobody has hit it for the last 15 seconds) it goes for the player with
 *   the least health; when several have the same, one of them at random (and it stays on that one while they are
 *   still among the lowest);
 * - 15 seconds without any hit also clears a half-made run of hits, so the next three are counted from nothing.
 * The bosses show whom they go for to every player (Marked, client.TargetMarker draws an arrow over the head).
 */
public final class BossTargeting {
    /** A boss that shows its target: the target's entity id (synced), or -1. */
    public interface Marked {
        int markedTargetId();
    }

    private static final int STREAK = 3;
    private static final int HOLD_TICKS = 15 * 20;

    @Nullable private UUID streakPlayer;
    private int streak;
    @Nullable private UUID locked;
    private long lockedUntil;
    private long lastHit = Long.MIN_VALUE / 2;

    /** Call when the boss was hurt (the hit went through). */
    public void onHurt(DamageSource source, long now) {
        if (!(source.getEntity() instanceof Player p)) return;
        UUID id = p.getUUID();
        if (now - lastHit > HOLD_TICKS) streakPlayer = null; // nobody hit it for 15 s: start counting again
        lastHit = now;
        if (id.equals(streakPlayer)) streak++;
        else {
            streakPlayer = id;
            streak = 1;
        }
        if (streak >= STREAK || id.equals(locked) && now < lockedUntil) { // three in a row, or the holder hitting again
            locked = id;
            lockedUntil = now + HOLD_TICKS;
        }
    }

    /**
     * The target now: the player holding it, or the one with the least health, among players alive, not in creative
     * or spectator, within range of `center`; null if there are none.
     */
    @Nullable
    public Player pick(Mob boss, Vec3 center, double range, @Nullable LivingEntity current) {
        long now = boss.level().getGameTime();
        List<Player> players = new ArrayList<>();
        for (Player p : boss.level().players()) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(center) < range * range) players.add(p);
        }
        if (players.isEmpty()) return null;
        if (locked != null && now < lockedUntil) {
            for (Player p : players) if (p.getUUID().equals(locked)) return p;
        }
        float least = Float.MAX_VALUE;
        for (Player p : players) least = Math.min(least, p.getHealth());
        List<Player> lowest = new ArrayList<>();
        for (Player p : players) if (p.getHealth() - least < 0.01F) lowest.add(p);
        if (current instanceof Player cp && lowest.contains(cp)) return cp;
        return lowest.get(boss.getRandom().nextInt(lowest.size()));
    }
}
