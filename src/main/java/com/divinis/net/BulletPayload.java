package com.divinis.net;

import java.util.ArrayList;
import java.util.List;

import com.divinis.boss.Bullets;
import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: boss bullets (boss.Bullets) - new ones (their paths, from the game time `start`), the ids of those
 * that hit something and are gone, or time standing still for them from `start` for `pause` ticks (pause < 0: time
 * running backwards) (client.BulletFx).
 */
public record BulletPayload(long start, List<Bullets.Spec> spawn, List<Integer> gone, int pause) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BulletPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "bullets"));

    public static final StreamCodec<ByteBuf, BulletPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.start());
                buf.writeShort(p.spawn().size());
                for (Bullets.Spec s : p.spawn()) Bullets.Spec.CODEC.encode(buf, s);
                buf.writeShort(p.gone().size());
                for (int id : p.gone()) buf.writeInt(id);
                buf.writeInt(p.pause());
            },
            buf -> {
                long start = buf.readLong();
                int n = buf.readShort();
                List<Bullets.Spec> spawn = new ArrayList<>(n);
                for (int i = 0; i < n; i++) spawn.add(Bullets.Spec.CODEC.decode(buf));
                int m = buf.readShort();
                List<Integer> gone = new ArrayList<>(m);
                for (int i = 0; i < m; i++) gone.add(buf.readInt());
                return new BulletPayload(start, spawn, gone, buf.readInt());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
