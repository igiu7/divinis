package com.divinis.boss;

import java.util.concurrent.atomic.AtomicInteger;

import com.divinis.net.BeamPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Beams for bosses: shown by client.BeamFx (BeamPayload), hits worked out here. */
public final class Beams {
    private Beams() {}

    private static final AtomicInteger IDS = new AtomicInteger();

    public static int nextId() {
        return IDS.incrementAndGet();
    }

    /** Shows (or moves, or changes) beam `id` to everyone near it. */
    public static void send(ServerLevel level, int id, byte kind, Vec3 from, Vec3 to, float width, int life) {
        Vec3 mid = from.add(to).scale(0.5);
        double range = 96 + from.distanceTo(to) / 2;
        PacketDistributor.sendToPlayersNear(level, null, mid.x, mid.y, mid.z, range,
                new BeamPayload(id, kind, from.x, from.y, from.z, to.x, to.y, to.z, width, life));
    }

    /** Whether a beam from a to b, `radius` blocks round its axis, goes through a creature. */
    public static boolean hits(LivingEntity e, Vec3 a, Vec3 b, double radius) {
        Vec3 p = e.position().add(0, e.getBbHeight() / 2, 0);
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double reach = radius + e.getBbWidth() / 2;
        // the body is tall: measure to the nearest of its middle, its feet and its head
        double best = Double.MAX_VALUE;
        for (double dy : new double[] {-e.getBbHeight() / 2 + 0.1, 0, e.getBbHeight() / 2 - 0.1}) {
            Vec3 q = p.add(0, dy, 0);
            double tq = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, q.subtract(a).dot(ab) / len2));
            best = Math.min(best, q.distanceToSqr(a.add(ab.scale(tq))));
        }
        return best <= reach * reach;
    }
}
