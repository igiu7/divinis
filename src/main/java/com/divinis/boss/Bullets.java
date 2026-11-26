package com.divinis.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.divinis.net.BulletPayload;
import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Boss bullets - many small shots on fixed paths, worked out the same on the server (who they hit) and on the client
 * (client.BulletFx draws them): no entities, one packet a volley. A bullet waits `delay` ticks where it starts, then
 * flies for `life` ticks along its path:
 * - LINE:  from `origin` along (a, b, c) (a unit direction), speed + accel per tick;
 * - POLAR: round `origin` on the level ground: at radius a + speed*t + accel*t*t/2 (never below 0), angle b + c*t
 *          (radians) - a ring closing in (speed < 0) or a spiral going out;
 * - ARC:   thrown from `origin` with the velocity (a, b, c) a tick, falling by accel a tick each tick (a firework's spark).
 * Time can stop for a boss's bullets (pause): they hang where they are, on the server and on the clients alike - or
 * run backwards (rewind): they fly back the way they came.
 * A bullet that touches a player hurts them (once) and is gone; the rest break up when their life runs out.
 * `link`: bullets with the same non-zero link and indices one apart are drawn joined by a thread of light (a ring).
 */
@EventBusSubscriber(modid = Divinis.MODID)
public final class Bullets {
    private Bullets() {}

    public static final byte STAR = 0, PETAL = 1, SPARK = 2, VOIDSTAR = 3, EMBER = 4, PRISM = 5;
    public static final byte LINE = 0, POLAR = 1, ARC = 2;
    private static final double VIEW = 96;
    private static final AtomicInteger IDS = new AtomicInteger();

    public record Spec(int id, byte kind, byte motion, double x, double y, double z, float a, float b, float c, float speed, float accel,
            int delay, int life, float size, int link, int index) {

        /** Where it is `age` ticks after it was fired (its delay included). */
        public Vec3 at(float age) {
            float t = Math.max(0, age - delay);
            if (motion == ARC) return new Vec3(x + a * t, y + b * t + accel * t * t / 2, z + c * t);
            float s = speed * t + accel * t * t / 2;
            if (motion == LINE) return new Vec3(x + a * s, y + b * s, z + c * s);
            float r = Math.max(0, a + s);
            float ang = b + c * t;
            return new Vec3(x + Math.cos(ang) * r, y, z + Math.sin(ang) * r);
        }

        public static final StreamCodec<ByteBuf, Spec> CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeInt(s.id);
                    buf.writeByte(s.kind);
                    buf.writeByte(s.motion);
                    buf.writeDouble(s.x);
                    buf.writeDouble(s.y);
                    buf.writeDouble(s.z);
                    buf.writeFloat(s.a);
                    buf.writeFloat(s.b);
                    buf.writeFloat(s.c);
                    buf.writeFloat(s.speed);
                    buf.writeFloat(s.accel);
                    buf.writeShort(s.delay);
                    buf.writeShort(s.life);
                    buf.writeFloat(s.size);
                    buf.writeInt(s.link);
                    buf.writeShort(s.index);
                },
                buf -> new Spec(buf.readInt(), buf.readByte(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readShort(), buf.readShort(),
                        buf.readFloat(), buf.readInt(), buf.readShort()));
    }

    /** A straight shot along a direction. */
    public static Spec line(byte kind, Vec3 from, Vec3 dir, float speed, float accel, int delay, int life, float size) {
        Vec3 d = dir.normalize();
        return new Spec(IDS.incrementAndGet(), kind, LINE, from.x, from.y, from.z, (float) d.x, (float) d.y, (float) d.z, speed, accel, delay,
                life, size, 0, 0);
    }

    /** A shot round a centre: radius r0 at angle a0 (radians), radius changing by speed/accel, turning by spin a tick. */
    public static Spec polar(byte kind, Vec3 centre, float r0, float a0, float spin, float speed, float accel, int delay, int life, float size,
            int link, int index) {
        return new Spec(IDS.incrementAndGet(), kind, POLAR, centre.x, centre.y, centre.z, r0, a0, spin, speed, accel, delay, life, size, link,
                index);
    }

    /** A spark thrown with a velocity (blocks a tick), falling by gravity (blocks a tick, a tick). */
    public static Spec arc(byte kind, Vec3 from, Vec3 velocity, float gravity, int delay, int life, float size) {
        return new Spec(IDS.incrementAndGet(), kind, ARC, from.x, from.y, from.z, (float) velocity.x, (float) velocity.y, (float) velocity.z, 0,
                -gravity, delay, life, size, 0, 0);
    }

    /** How long after its start an ARC falls to the height y (its life to the ground), or -1 if it never does. */
    public static int ticksToFall(Vec3 from, Vec3 velocity, float gravity, double y) {
        double a = -gravity / 2, b = velocity.y, c = from.y - y;
        double disc = b * b - 4 * a * c;
        if (disc < 0) return -1;
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return t > 0 ? (int) Math.ceil(t) : -1;
    }

    // ------------------------------------------------------------------ time stopping

    /** Times (game time, from - to, 0 still / 1 backwards) when an owner's bullets hang still or go back. */
    private static final java.util.Map<Mob, List<long[]>> PAUSES = new java.util.WeakHashMap<>();

    /** Stops time for an owner's bullets from now for `ticks` ticks. */
    public static void pause(ServerLevel level, Mob owner, int ticks) {
        long now = level.getGameTime();
        PAUSES.computeIfAbsent(owner, o -> new ArrayList<>()).add(new long[] {now, now + ticks, 0});
        Vec3 o = owner.position();
        PacketDistributor.sendToPlayersNear(level, null, o.x, o.y, o.z, VIEW, new BulletPayload(now, List.of(), List.of(), ticks));
    }

    /** Runs time backwards for an owner's bullets from `from` (game time) for `ticks` ticks: they fly back along their paths. */
    public static void rewind(ServerLevel level, Mob owner, long from, int ticks) {
        PAUSES.computeIfAbsent(owner, o -> new ArrayList<>()).add(new long[] {from, from + ticks, 1});
        Vec3 o = owner.position();
        PacketDistributor.sendToPlayersNear(level, null, o.x, o.y, o.z, VIEW, new BulletPayload(from, List.of(), List.of(), -ticks));
    }

    /** How long a bullet fired at `start` has flown by `now`: the times stopped left out, the times backwards taken off. */
    public static float age(long start, float now, List<long[]> pauses) {
        float age = now - start;
        for (long[] p : pauses) {
            float from = Math.max(p[0], start), to = Math.min(p[1], now);
            if (to > from) age -= (to - from) * (p[2] == 1 ? 2 : 1);
        }
        return age;
    }

    public static int nextLink() {
        return IDS.incrementAndGet();
    }

    private static final class Active {
        final ServerLevel level;
        final Spec spec;
        final Mob owner;
        final float damage;
        final long start;

        Active(ServerLevel level, Spec spec, Mob owner, float damage, long start) {
            this.level = level;
            this.spec = spec;
            this.owner = owner;
            this.damage = damage;
            this.start = start;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    /** Fires a volley: every bullet hurts by `damage` (the owner's attack). */
    public static void fire(ServerLevel level, Mob owner, List<Spec> specs, float damage) {
        if (specs.isEmpty()) return;
        long now = level.getGameTime();
        for (Spec s : specs) ACTIVE.add(new Active(level, s, owner, damage, now));
        Vec3 o = owner.position();
        for (int i = 0; i < specs.size(); i += 400) {
            PacketDistributor.sendToPlayersNear(level, null, o.x, o.y, o.z, VIEW,
                    new BulletPayload(now, specs.subList(i, Math.min(specs.size(), i + 400)), List.of(), 0));
        }
    }

    /** Calls off every bullet of this owner (it died, changed...). */
    public static void clear(Mob owner) {
        List<Integer> gone = new ArrayList<>();
        ServerLevel level = null;
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            if (a.owner != owner) continue;
            gone.add(a.spec.id());
            level = a.level;
            it.remove();
        }
        if (level != null) send(level, owner.position(), gone);
    }

    private static void send(ServerLevel level, Vec3 near, List<Integer> gone) {
        PacketDistributor.sendToPlayersNear(level, null, near.x, near.y, near.z, VIEW, new BulletPayload(level.getGameTime(), List.of(), gone, 0));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        List<Active> hit = new ArrayList<>();
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            Spec s = a.spec;
            float age = age(a.start, a.level.getGameTime(), PAUSES.getOrDefault(a.owner, List.of()));
            if (age > s.delay() + s.life() || !a.owner.isAlive()) {
                it.remove();
                continue;
            }
            if (age < s.delay()) continue;
            Vec3 p = s.at(age);
            Player victim = touching(a, p);
            if (victim != null) {
                victim.hurt(a.owner.damageSources().mobAttack(a.owner), a.damage);
                hit.add(a);
                it.remove();
            }
        }
        if (hit.isEmpty()) return;
        List<Integer> gone = new ArrayList<>();
        for (Active a : hit) gone.add(a.spec.id());
        send(hit.get(0).level, hit.get(0).owner.position(), gone);
    }

    @Nullable
    private static Player touching(Active a, Vec3 p) {
        double r = a.spec.size() * 0.5;
        for (Player pl : a.level.players()) {
            if (pl.isCreative() || pl.isSpectator() || !pl.isAlive()) continue;
            if (pl.getBoundingBox().inflate(r).contains(p)) return pl;
        }
        return null;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        PAUSES.clear();
    }
}
