"""
The Hydra's effects (boss.Hydra, client.HydraParticles):
  textures/particle/frost_0..7.png        a glittering shard of frost (registry.ModParticles.FROST), 16x16: a pale blue
                                          star of ice with a white heart, shrinking to a glint
  textures/particle/hydra_blood_0..7.png  a gout of the Hydra's blood (HYDRA_BLOOD), 16x16: dark red, nearly black,
                                          a drop that splatters and thins
  textures/particle/bog_splash_0..7.png   bog water thrown up (BOG_SPLASH), 16x16: murky green-brown water with flecks
                                          of weed and foam, from a dense spray to a few drops
  particles/*.json                        their sprite lists

  python tools/build_hydra_fx.py
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


def blank():
    return [[(0, 0, 0, 0)] * 16 for _ in range(16)]


def put(px, x, y, c, a):
    if 0 <= x < 16 and 0 <= y < 16 and a > px[y][x][3]:
        px[y][x] = tuple(c) + (max(0, min(255, int(a))),)


def frost():
    ice = [(120, 180, 230), (170, 214, 245), (215, 238, 255), (255, 255, 255)]
    for i in range(8):
        px = blank()
        r = 6 - i * 0.6
        a = 255 - i * 20
        for k in range(6):   # a six-armed star
            ang = k * math.pi / 3 + 0.3
            for d in range(int(r) + 1):
                x, y = int(round(7.5 + math.cos(ang) * d)), int(round(7.5 + math.sin(ang) * d))
                put(px, x, y, ice[2 if d < r / 2 else 1], a)
                if d == int(r * 0.6) and i < 4:   # its little side branches
                    for s in (-1, 1):
                        put(px, x + int(round(math.cos(ang + s * 1.0))), y + int(round(math.sin(ang + s * 1.0))), ice[1], a * 0.8)
        put(px, 7, 7, ice[3], 255)
        put(px, 8, 8, ice[3], 255)
        save(A + f"textures/particle/frost_{i}.png", 16, 16, px)
    sprites("frost", 8)


def hydra_blood():
    rnd = random.Random(4)
    red = [(40, 4, 6), (70, 8, 10), (104, 14, 16), (140, 26, 24)]
    for i in range(8):
        px = blank()
        if i < 3:   # a drop
            r = 3.2 - i * 0.4
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - 7.5, (y - 8.5) * (1.0 + 0.15 * (y < 8))) / r
                    if d <= 1:
                        put(px, x, y, red[3 if d < 0.4 and x < 8 and y < 8 else 2 if d < 0.7 else 1], 255)
        else:       # splattered
            for _ in range(14 - i):
                ang = rnd.uniform(0, math.tau)
                d = rnd.uniform(1, 2 + i * 0.7)
                x, y = int(7.5 + math.cos(ang) * d), int(7.5 + math.sin(ang) * d)
                put(px, x, y, red[rnd.randrange(1, 4)], 255 - (i - 3) * 30)
                if rnd.random() < 0.5:
                    put(px, x + 1, y, red[1], 200 - (i - 3) * 30)
        save(A + f"textures/particle/hydra_blood_{i}.png", 16, 16, px)
    sprites("hydra_blood", 8)


def bog_splash():
    rnd = random.Random(6)
    water = [(48, 62, 40), (70, 88, 54), (96, 116, 72), (140, 158, 112), (210, 220, 196)]
    for i in range(8):
        px = blank()
        n = 26 - i * 3
        spread = 2 + i * 0.7
        for _ in range(n):
            ang = rnd.uniform(0, math.tau)
            d = abs(rnd.gauss(0, spread))
            x, y = int(7.5 + math.cos(ang) * d), int(7.5 + math.sin(ang) * d * 0.9)
            c = water[4] if rnd.random() < 0.12 else water[rnd.randrange(0, 4)]   # foam now and then
            put(px, x, y, c, 235 - i * 25)
            if i < 4 and rnd.random() < 0.6:
                put(px, x, y + 1, water[1], 200 - i * 25)
        save(A + f"textures/particle/bog_splash_{i}.png", 16, 16, px)
    sprites("bog_splash", 8)


frost()
hydra_blood()
bog_splash()
print("saved the Hydra's effects")
