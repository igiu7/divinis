"""
Ignaroth's effects (boss.Ignaroth, client.IgnarothParticles, client.IgnarothRenderer):
  textures/particle/ember_0..7.png        a glowing ember (registry.ModParticles.EMBER), 16x16: a white-hot spark with an
                                          orange glow round it, cooling to a dull red speck
  textures/particle/dragon_fire_0..7.png  a billow of dragon fire (DRAGON_FIRE), 16x16: a dense ball of yellow-white fire
                                          with orange and red edges, breaking up into ragged tongues and smoke
  textures/particle/ash_0..7.png          a flake of ash (ASH), 16x16: grey, curling, its edge still glowing at first
  particles/*.json                        their sprite lists
  textures/entity/ignaroth_lava.png       the lava that rises round the crater (32x32, seamless): bright molten rivers
                                          between dark crusts of cooling rock

  python tools/build_ignaroth_fx.py
"""
import json
import math
import random
import struct
import zlib

A = "src/main/resources/assets/divinis/"


def save(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def sprites(name, frames):
    with open(A + f"particles/{name}.json", "w") as f:
        json.dump({"textures": [f"divinis:{name}_{i}" for i in range(frames)]}, f, indent=2)


def blank(n=16):
    return [[(0, 0, 0, 0)] * n for _ in range(n)]


def put(px, x, y, c, a):
    if 0 <= x < len(px[0]) and 0 <= y < len(px) and a > px[y][x][3]:
        px[y][x] = tuple(int(v) for v in c) + (max(0, min(255, int(a))),)


def ember():
    for i in range(8):
        px = blank()
        r = 2.6 - i * 0.22
        core = [(255, 255, 220), (255, 230, 140), (255, 180, 70), (240, 130, 40), (200, 80, 30), (160, 50, 20), (120, 36, 16), (90, 26, 12)][i]
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < r * 0.45:
                    put(px, x, y, core, 255)
                elif d < r:
                    put(px, x, y, (255, 140, 40), 220 * (1 - i / 9))
                elif d < r * 2.2:
                    put(px, x, y, (255, 100, 20), 90 * (1 - (d - r) / (r * 1.2)) * (1 - i / 8))
        save(A + f"textures/particle/ember_{i}.png", px)
    sprites("ember", 8)


def dragon_fire():
    rnd = random.Random(4)
    cols = [(120, 30, 10), (200, 60, 16), (250, 130, 30), (255, 200, 80), (255, 245, 200)]
    for i in range(8):
        px = blank()
        r = 4.5 + i * 0.4
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, (y - 8) * 1.1) / r
                if d > 1:
                    continue
                ragged = rnd.random() < 0.1 * i * d
                if ragged:
                    continue
                heat = (1 - d) * (1 - i / 9)
                if i >= 6 and heat < 0.15:
                    put(px, x, y, (60, 50, 46), 160)   # smoke at the end
                    continue
                c = cols[min(4, int(heat * 5.5 + rnd.uniform(-0.4, 0.4)))]
                put(px, x, y, c, 255 * min(1, (1 - d) * 2.2) * (1 - i / 10))
        save(A + f"textures/particle/dragon_fire_{i}.png", px)
    sprites("dragon_fire", 8)


def ash():
    rnd = random.Random(7)
    for i in range(8):
        px = blank()
        n = 6 - i // 2
        for _ in range(n):
            x, y = int(7.5 + rnd.uniform(-2.5, 2.5)), int(7.5 + rnd.uniform(-2.5, 2.5))
            c = (60 + rnd.randrange(30),) * 3
            put(px, x, y, c, 230 - i * 20)
            put(px, x + 1, y, c, 200 - i * 20)
            if i < 3:
                put(px, x, y + 1, (255, 120, 40), 200 - i * 50)   # the glowing edge
        save(A + f"textures/particle/ash_{i}.png", px)
    sprites("ash", 8)


def lava():
    n = 32
    rnd = random.Random(9)
    seeds = [(rnd.uniform(0, n), rnd.uniform(0, n)) for _ in range(9)]
    px = [[(0, 0, 0, 255)] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            # distance to the nearest crust centre (wrapping): crusts round them, molten rivers between
            ds = sorted(min(math.hypot(min(abs(x - sx), n - abs(x - sx)), min(abs(y - sy), n - abs(y - sy))), 99) for sx, sy in seeds)
            edge = ds[1] - ds[0]
            if edge < 1.2:
                c = (255, 230, 120) if edge < 0.5 else (255, 160, 40)
            elif edge < 2.6:
                c = (230, 90, 20)
            else:
                k = rnd.random()
                c = (60, 22, 14) if k < 0.5 else (84, 32, 18) if k < 0.85 else (130, 50, 20)
            px[y][x] = c + (255,)
    save(A + "textures/entity/ignaroth_lava.png", px)


ember()
dragon_fire()
ash()
lava()
print("saved Ignaroth's effects")
