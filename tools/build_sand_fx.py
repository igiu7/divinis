"""
The Sphinx's sand (boss.Sphinx, boss.SandTornado):
  textures/particle/sand_0..7.png   the sand particle (registry.ModParticles.SAND), 16x16: a puff of blown sand - a
                                    cloud of fine dust, gold above and brown below, flecked with grains - from a small
                                    dense puff (0) to a wide thin haze (7); the particle plays them over its life
  particles/sand.json               its sprite list
  textures/effect/sand_tornado.png  the wall of a sand tornado (client.SandFx), 64x64, seamless both ways: streaks of
                                    sand slanting round it, thick and thin, see-through between them, grains in them
  textures/screenfx/sandstorm.png   the sandstorm over the screen (client.ScreenFxClient, SANDSTORM), 256x128, seamless
                                    both ways: long wind-blown streaks and drifts of dust, mostly see-through

  python tools/build_sand_fx.py
"""
import json
import math
import random
import struct
import zlib

A = "src/main/resources/assets/divinis/"
SAND = [(120, 84, 44), (156, 114, 62), (190, 148, 86), (216, 180, 116), (238, 210, 150), (250, 232, 186)]


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def periodic_noise(w, h, cells_x, cells_y, seed):
    """Smooth value noise repeating every w x h pixels."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells_x)] for _ in range(cells_y)]

    def at(x, y):
        fx, fy = x / w * cells_x, y / h * cells_y
        x0, y0 = int(fx) % cells_x, int(fy) % cells_y
        x1, y1 = (x0 + 1) % cells_x, (y0 + 1) % cells_y
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return a * (1 - ty) + b * ty
    return at


def shade(level):
    level = max(0.0, min(len(SAND) - 1.001, level))
    i = int(level)
    t = level - i
    a, b = SAND[i], SAND[i + 1]
    return tuple(int(a[c] + (b[c] - a[c]) * t) for c in range(3))


def particles():
    for f in range(8):
        rnd = random.Random(70 + f)
        grow = f / 7
        blobs = [(8 + rnd.uniform(-1, 1), 8 + rnd.uniform(-1, 1), 3.0 + grow * 3.4)]
        for _ in range(3 + f):
            a = rnd.uniform(0, math.tau)
            d = rnd.uniform(1.5, 2.5 + grow * 3)
            blobs.append((8 + math.cos(a) * d, 8 + math.sin(a) * d * 0.7, rnd.uniform(1.4, 2.4 + grow * 1.6)))
        px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                depth = max(r - math.hypot(x + 0.5 - bx, y + 0.5 - by) for bx, by, r in blobs)
                if depth <= 0:
                    continue
                if grow > 0.3 and rnd.random() < (grow - 0.3) * 0.6 and depth < 2.2:
                    continue
                level = 3.4 - (y - 5) * 0.16 + (0.5 if x + y < 12 else 0) - (0.7 if depth < 1 else 0) + rnd.uniform(-0.4, 0.4)
                grain = rnd.random() < 0.12
                c = shade(level + (1.2 if grain else 0))
                alpha = min(1.0, depth / 1.5) * (1 - grow * 0.5) * (1.0 if grain else 0.88)
                px[y][x] = c + (int(255 * alpha),)
        save(A + f"textures/particle/sand_{f}.png", 16, 16, px)
    json.dump({"textures": [f"divinis:sand_{f}" for f in range(8)]}, open(A + "particles/sand.json", "w"), indent=2)


def tornado():
    w = h = 64
    rnd = random.Random(5)
    n1 = periodic_noise(w, h, 4, 16, 1)
    n2 = periodic_noise(w, h, 8, 32, 2)
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            # the streaks slant: sample along a sheared line (one turn of the shear over the tile, so it repeats)
            sx = (x + y) % w
            v = n1(sx, y) * 0.65 + n2(sx, y) * 0.35
            streak = max(0.0, (v - 0.38) / 0.62)
            if streak <= 0.02:
                continue
            grain = rnd.random() < 0.08
            c = shade(1.6 + streak * 2.6 + (1.0 if grain else 0) + rnd.uniform(-0.25, 0.25))
            alpha = min(1.0, streak * 1.5) * (1.0 if grain else 0.9)
            px[y][x] = c + (int(255 * alpha),)
    save(A + "textures/effect/sand_tornado.png", w, h, px)


def sandstorm():
    w, h = 256, 128
    rnd = random.Random(9)
    n1 = periodic_noise(w, h, 4, 24, 3)
    n2 = periodic_noise(w, h, 16, 48, 4)
    n3 = periodic_noise(w, h, 3, 4, 5)
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            v = n1(x, y) * 0.6 + n2(x, y) * 0.4
            drift = n3(x, y)
            streak = max(0.0, (v - 0.45) / 0.55) * (0.5 + drift)
            haze = drift * 0.25
            a = min(1.0, streak * 1.3 + haze)
            if a < 0.03:
                continue
            grain = rnd.random() < 0.04 * streak
            c = shade(2.4 + streak * 2.0 + (1.2 if grain else 0) + rnd.uniform(-0.2, 0.2))
            px[y][x] = c + (int(255 * (1.0 if grain else a)),)
    save(A + "textures/screenfx/sandstorm.png", w, h, px)


particles()
tornado()
sandstorm()
print("sand fx done")
