package com.divinis.net;

import com.divinis.Divinis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> client: one of the Nameless Creator's effects in the world (client.CreatorFx), from `a` (to `b`), with up
 * to four numbers of its own; `delay` ticks until it strikes, `life` ticks in all:
 * - TRAJ:   a line a star will fly along (a to b)
 * - CUT:    a dimensional cut along a to b, f0 high: a thin plane, then (at delay) it opens and cuts
 * - GAP:    the golden light in the gap of a ring of stars round a: f0 radius, f1 angle, f2 turn a tick, f3 radius a tick
 * - FLOWER: a flower falling from a (its crown) onto b (delay ticks), growing there until it bursts (life)
 * - STAR:   the collapsing star at a, f0 its full radius (timed as boss.NamelessCreator's COLLAPSE)
 * - MARK:   a place the map marked: a mark on the ground at a, the column of light (f0 across) falling at delay
 * - SHOCK:  a shock wave from a over the picture, out to f0 blocks; f1 how hard it blurs towards it at first
 * - CRACK:  the shining crack down the middle of the god, from a (its feet) to b (over its head)
 * - TIMESTOP: time stands still for `life` ticks (the particles hang still)
 * - FIST:   a fist (f0: 0 gold, 1 violet) gathering high over b, falling on it at delay
 * - GWAVE:  a wave rolling out over the ground from a, to f0 blocks, f1 its colour (0 gold, 1 violet)
 * - VOIDZONE: a pool of black ooze at a, f0 across
 * - SWORD:  a sword of stars at its hilt a, f0 long: drawn star by star (delay ticks), then swung flat round from the
 *           heading f1 (radians) through f2 (timed as boss.NamelessCreator's SWORD_*)
 * - SWORD_V: the sword raised over its hilt a and brought down along the heading f1 at delay, f0 long
 * - ZONES:  the arena round a split into light and shadow: squares f0 across turned by f1, f2 1 a chequer (0 stripes),
 *           f3 the arena's radius; they change places every `delay` ticks
 * - CRUSH:  two hands crushing the hunger bar on the HUD
 * - LSLASH: a cut of light from a to b, striking at delay
 * - GAMEFLASH: one frame of the game itself over the screen (f0: 0 the menu panorama, 1 building terrain, 2 old dirt)
 * - CHRONOS: the clock's hand as a beam from a, f0 long, turning (boss.NamelessCreator.beamAngle of f1 its start, f2
 *           its turn a tick, f3 how much faster each tick) from delay on
 * - CAGE:   the outline of a box from a to b, shutting at delay
 * - CHUNK:  the chunk of the floor from a (its corner) flickering, gone at delay, back at the end
 * - CHUNKGRID: the chunk borders over the arena round a (f0 its radius)
 * - GAZE:   to one player: how long they have looked at its face (f0, 0..1)
 * - ERASE:  a cut of pure white from a to b, f0 wide: the floor under it white at delay, gone (life)
 * - ERASE_RING: the floor between f0 and f1 blocks round a white at delay, gone (life)
 * - CHECKER: the floor round a a chequer of the missing texture, squares f0 across, out to f1, moving on a square every
 *           `delay` ticks
 * - GIGA:   the great beam from a (before its face), f0 long: gathering until delay, then turning from the heading f1
 *           (radians) through f2 (boss.NamelessCreator.GIGA_*)
 * - CREATION: the world made again round a, from the height f0 up to f1 (boss.NamelessCreator.CREATION_*)
 */
public record CreatorFxPayload(byte kind, Vec3 a, Vec3 b, float f0, float f1, float f2, float f3, int delay, int life)
        implements CustomPacketPayload {
    public static final byte TRAJ = 0, CUT = 1, GAP = 2, FLOWER = 3, STAR = 4, MARK = 5, SHOCK = 6, CRACK = 7, TIMESTOP = 8, FIST = 9,
            GWAVE = 10, VOIDZONE = 11, SWORD = 12, SWORD_V = 13, ZONES = 14, CRUSH = 15, LSLASH = 16, GAMEFLASH = 17, CHRONOS = 18, CAGE = 19,
            CHUNK = 20, CHUNKGRID = 21, GAZE = 22, ERASE = 23, ERASE_RING = 24, CHECKER = 25, GIGA = 26, CREATION = 27;

    public static final CustomPacketPayload.Type<CreatorFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "creator_fx"));

    public static final StreamCodec<ByteBuf, CreatorFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind());
                for (Vec3 v : new Vec3[] {p.a(), p.b()}) {
                    buf.writeDouble(v.x);
                    buf.writeDouble(v.y);
                    buf.writeDouble(v.z);
                }
                buf.writeFloat(p.f0());
                buf.writeFloat(p.f1());
                buf.writeFloat(p.f2());
                buf.writeFloat(p.f3());
                buf.writeInt(p.delay());
                buf.writeInt(p.life());
            },
            buf -> new CreatorFxPayload(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readInt(), buf.readInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
