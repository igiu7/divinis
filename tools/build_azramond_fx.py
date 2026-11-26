"""
Azramond's effects (boss.Azramond, client.AzramondParticles):
  textures/particle/necro_flame_0..7.png  a tongue of green soul fire (registry.ModParticles.NECRO_FLAME), 16x16:
                                          pale green at its heart, deep green at its edges, flickering out
  textures/particle/curse_0..7.png        a rune of his curse (CURSE), 16x16: a purple glyph that glows, turns and fades
  textures/particle/holy_light_0..7.png   a mote of the light the dead shun (HOLY_LIGHT), 16x16: a warm golden spark with
                                          a soft halo, dimming
  particles/*.json                        their sprite lists

  python tools/build_azramond_fx.py
"""
import json
import math
import random
import struct
import zlib

A = "src/main/resources/assets/divinis/"


def save(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def sprites(name, frames):
    with open(A + f"particles/{name}.json", "w") as f:
        json.dump({"textures": [f"divinis:{name}_{i}" for i in range(frames)]}, f, indent=2)


def blank():
    return [[(0, 0, 0, 0)] * 16 for _ in range(16)]


def put(px, x, y, c, a):
    if 0 <= x < 16 and 0 <= y < 16 and a > px[y][x][3]:
        px[y][x] = tuple(c) + (max(0, min(255, int(a))),)


def necro_flame():
    rnd = random.Random(2)
    cols = [(20, 90, 40), (40, 170, 70), (110, 240, 130), (220, 255, 220)]
    for i in range(8):
        px = blank()
        height, width = 13 - i * 1.2, 4.0 - i * 0.35
        sway = math.sin(i * 1.7) * 1.4
        for y in range(16):
            t = (15 - y) / max(1, height)
            if t > 1:
                continue
            half = width * (1 - t) ** 0.7 * (0.6 if t < 0.12 else 1)
            cx = 7.5 + sway * t * t
            for x in range(16):
                d = abs(x - cx) / max(0.4, half)
                if d > 1 or rnd.random() < 0.07 * i:
                    continue
                core = (1 - d) * (1 - t)
                put(px, x, y, cols[min(3, int(core * 4.2 + rnd.uniform(-0.3, 0.3)))], 255 * min(1, (1 - d) * 1.6) * (1 - i / 9))
        save(A + f"textures/particle/necro_flame_{i}.png", px)
    sprites("necro_flame", 8)


def curse():
    glyph = ["..#####..", ".#.....#.", "#..#.#..#", "#...#...#", "#..#.#..#", ".#.....#.", "..##.##..", "...#.#...", "..#...#.."]
    for i in range(8):
        px = blank()
        ang = i * 0.25
        a = 255 if i < 5 else 255 - (i - 4) * 55
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch != "#":
                    continue
                x0, y0 = gx - 4, gy - 4
                x = 7.5 + x0 * math.cos(ang) - y0 * math.sin(ang)
                y = 7.5 + x0 * math.sin(ang) + y0 * math.cos(ang)
                put(px, int(round(x)), int(round(y)), (200, 110, 255) if (gx + gy) % 3 else (250, 200, 255), a)
        save(A + f"textures/particle/curse_{i}.png", px)
    sprites("curse", 8)


def holy_light():
    for i in range(8):
        px = blank()
        r = 3.0 - i * 0.25
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < r * 0.5:
                    put(px, x, y, (255, 250, 220), 255 - i * 20)
                elif d < r:
                    put(px, x, y, (255, 214, 110), (200 - i * 22) * (1 - (d - r * 0.5) / (r * 0.5)))
                elif d < r * 2:
                    put(px, x, y, (255, 190, 80), (70 - i * 8) * (1 - (d - r) / r))
        save(A + f"textures/particle/holy_light_{i}.png", px)
    sprites("holy_light", 8)


necro_flame()
curse()
holy_light()
print("saved Azramond's effects")
