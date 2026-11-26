package com.divinis.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.divinis.net.TelegraphPayload;
import com.divinis.Divinis;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.ServerStoppedEvent;
import com.divinis.platform.event.ServerTickEvent;
import com.divinis.platform.net.PacketDistributor;

/**
 * Telegraphed boss attacks, for any boss: a red area appears on the ground (client.TelegraphRenderer) and fills up;
 * when it is full, every living thing still standing inside is hit (onHit). Bosses only pick the shape, the timing
 * and what a hit does:
 *
 *   Telegraphs.show(level, Telegraph.circle(boss.position(), 6), 30, 6, boss, e -> e.hurt(...));
 *
 * A sweep instead burns for a while once it is full, turning round its origin; whoever is inside is hit again and
 * again (no more often than every hitEvery ticks) - a beam of fire wheeling round a boss.
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class Telegraphs {
    private Telegraphs() {}

    /** how far (blocks, beyond the shape) players see the warning */
    private static final double VIEW_RANGE = 64;
    /** heights above / below the shape that still count as inside it */
    private static final double BELOW = 1.5, ABOVE = 2.5;

    private static final class Active {
        final int id;
        final ServerLevel level;
        final Telegraph shape;
        final int warmup, linger, active, hitEvery;
        final float spin;
        @Nullable final Entity owner;
        @Nullable final Consumer<LivingEntity> onHit;
        final java.util.Map<java.util.UUID, Integer> lastHit = new java.util.HashMap<>();
        int age;

        Active(int id, ServerLevel level, Telegraph shape, int warmup, int linger, int active, float spin, int hitEvery,
                @Nullable Entity owner, @Nullable Consumer<LivingEntity> onHit) {
            this.id = id;
            this.level = level;
            this.shape = shape;
            this.warmup = warmup;
            this.linger = linger;
            this.active = active;
            this.spin = spin;
            this.hitEvery = hitEvery;
            this.owner = owner;
            this.onHit = onHit;
        }

        /** The shape now: turned as far as it has burned. */
        Telegraph now() {
            return shape.turned(spin * Math.max(0, Math.min(age - warmup, active)));
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static int nextId;

    /**
     * Shows a warning for warmup ticks; then onHit (if any) runs for everything inside and the impact flash stays
     * linger ticks. The owner is never hit by its own attack. Returns the warning's id.
     */
    public static int show(ServerLevel level, Telegraph shape, int warmup, int linger, @Nullable Entity owner,
            @Nullable Consumer<LivingEntity> onHit) {
        return add(new Active(++nextId, level, shape, Math.max(1, warmup), Math.max(0, linger), 0, 0, 0, owner, onHit));
    }

    /**
     * A sweep: warns for warmup ticks, then burns for active ticks, turning spin degrees a tick round its origin;
     * everything inside is hit (onHit), each no more often than every hitEvery ticks.
     */
    public static int sweep(ServerLevel level, Telegraph shape, int warmup, int active, float spin, int hitEvery, @Nullable Entity owner,
            Consumer<LivingEntity> onHit) {
        return add(new Active(++nextId, level, shape, Math.max(1, warmup), 4, Math.max(1, active), spin, Math.max(1, hitEvery), owner, onHit));
    }

    private static int add(Active a) {
        ACTIVE.add(a);
        Telegraph shape = a.shape;
        PacketDistributor.sendToPlayersNear(a.level, null, shape.x(), shape.y(), shape.z(), VIEW_RANGE + shape.reach(),
                new TelegraphPayload(a.id, shape, a.warmup, a.linger, a.active, a.spin));
        return a.id;
    }

    /** Calls off all warnings of this owner that have not hit yet (it died, was stunned...). */
    public static void cancel(Entity owner) {
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (a.owner != owner || a.age >= a.warmup + a.active) continue;
            it.remove();
            PacketDistributor.sendToPlayersNear(a.level, null, a.shape.x(), a.shape.y(), a.shape.z(),
                    VIEW_RANGE + a.shape.reach(), TelegraphPayload.cancel(a.id));
        }
    }

    /** Living things standing inside the shape now (players in creative or spectator mode left out). */
    public static List<LivingEntity> inside(ServerLevel level, Telegraph shape, @Nullable Entity except) {
        double r = shape.reach() + 2;
        AABB box = new AABB(shape.x() - r, shape.y() - BELOW - 2, shape.z() - r, shape.x() + r, shape.y() + ABOVE, shape.z() + r);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != except && e.isAlive()
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                && e.getY() >= shape.y() - BELOW && e.getY() <= shape.y() + ABOVE
                && shape.contains(e.getX(), e.getZ(), e.getBbWidth() / 2));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        List<Active> hits = new ArrayList<>();
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            a.age++;
            boolean burning = a.active > 0 ? a.age >= a.warmup && a.age < a.warmup + a.active : a.age == a.warmup;
            if (burning && a.onHit != null) hits.add(a);
            if (a.age >= a.warmup + a.active + a.linger) it.remove();
        }
        // after the loop: an onHit may start new warnings
        for (Active a : hits) {
            if (a.owner != null && !a.owner.isAlive()) continue;
            for (LivingEntity e : inside(a.level, a.now(), a.owner)) {
                if (a.active > 0) {
                    Integer last = a.lastHit.get(e.getUUID());
                    if (last != null && a.age - last < a.hitEvery) continue;
                    a.lastHit.put(e.getUUID(), a.age);
                }
                a.onHit.accept(e);
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
