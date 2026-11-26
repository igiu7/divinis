"""
The Barrow Chieftain's effects (boss.BarrowChieftain, client.BarrowParticles):
  textures/particle/ghost_flame_0..7.png  a tongue of pale soul fire (registry.ModParticles.GHOST_FLAME), 16x16: white
                                          at its heart, teal at its edges, flickering up and dying to a wisp
  textures/particle/bone_dust_0..7.png    a fleck of grave dust and crumbled bone (BONE_DUST), 16x16: a few grey-white
                                          grains, scattering and thinning
  particles/*.json                        their sprite lists

  python tools/build_barrow_fx.py
"""
import json
import math
import random
import struct
import zlib

A = "src/main/resources/assets/divinis/"


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def sprites(name, frames):
    with open(A + f"particles/{name}.json", "w") as f:
        json.dump({"textures": [f"divinis:{name}_{i}" for i in range(frames)]}, f, indent=2)


FLAME = [(30, 110, 110), (60, 190, 180), (130, 240, 226), (220, 255, 250)]


def ghost_flame():
    rnd = random.Random(7)
    for i in range(8):
        px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        height = 13 - i * 1.2
        width = 4.2 - i * 0.35
        sway = math.sin(i * 1.3) * 1.2
        for y in range(16):
            t = (15 - y) / max(1, height)       # 0 at the bottom .. 1 at the tip
            if t > 1:
                continue
            half = width * (1 - t) ** 0.7 * (0.6 if t < 0.12 else 1)
            cx = 7.5 + sway * t * t
            for x in range(16):
                d = abs(x - cx) / max(0.4, half)
                if d > 1:
                    continue
                core = (1 - d) * (1 - t)
                c = FLAME[min(3, int(core * 4.2 + rnd.uniform(-0.3, 0.3)))]
                a = int(255 * min(1, (1 - d) * 1.6) * (1 - i / 9))
                if rnd.random() < 0.08 * i:
                    continue
                px[y][x] = c + (max(0, a),)
        save(A + f"textures/particle/ghost_flame_{i}.png", 16, 16, px)
    sprites("ghost_flame", 8)


DUST = [(96, 92, 86), (132, 126, 116), (170, 164, 152), (210, 206, 196)]


def bone_dust():
    rnd = random.Random(3)
    for i in range(8):
        px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        grains = 9 - i
        spread = 2 + i * 0.7
        for _ in range(grains):
            x = int(7.5 + rnd.uniform(-spread, spread))
            y = int(7.5 + rnd.uniform(-spread, spread))
            c = DUST[rnd.randrange(4)]
            a = 255 - i * 22
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1))[:2 if i > 3 else 4]:
                if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                    px[y + dy][x + dx] = c + (a,)
        save(A + f"textures/particle/bone_dust_{i}.png", 16, 16, px)
    sprites("bone_dust", 8)


ghost_flame()
bone_dust()
print("saved the Barrow Chieftain's effects")
