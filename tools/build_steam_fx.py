"""
The Evil Brother's effects (boss.EvilBrother):
  textures/particle/steam_0..7.png   the steam particle (registry.ModParticles.STEAM), 16x16: a puff of steam in the
                                     Minecraft manner - a lumpy round cloud, white with grey shading below and a bright
                                     top-left, soft see-through edges - from a small dense puff (0) to a big thin wisp
                                     breaking up (7); the particle plays them over its life
  particles/steam.json               its sprite list
  the gear saw and the harpoon (item models shown by item displays): models/item/evil_brother_gear.json and
  evil_brother_harpoon.json, their textures textures/item/evil_brother_gear.png and evil_brother_harpoon.png

  python tools/build_steam_fx.py
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


def steam():
    for f in range(8):
        rnd = random.Random(40 + f)
        grow = f / 7
        # the cloud: a few overlapping blobs, more and wider as it spreads
        blobs = [(8 + rnd.uniform(-1, 1), 8 + rnd.uniform(-1, 1), 3.2 + grow * 3.2)]
        for _ in range(3 + f):
            a = rnd.uniform(0, math.tau)
            d = rnd.uniform(1.5, 2.5 + grow * 3)
            blobs.append((8 + math.cos(a) * d, 8 + math.sin(a) * d * 0.8, rnd.uniform(1.6, 2.6 + grow * 1.6)))
        px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                # how deep inside the cloud (0 at an edge); holes open up as it thins
                depth = max(r - math.hypot(x + 0.5 - bx, y + 0.5 - by) for bx, by, r in blobs)
                if depth <= 0:
                    continue
                if grow > 0.4 and rnd.random() < (grow - 0.4) * 0.5 and depth < 2:
                    continue
                shade = 236 - (y - 4) * 4 + (12 if x + y < 12 else 0) - int(depth < 1) * 18
                shade = max(150, min(255, shade + rnd.randint(-6, 6)))
                alpha = min(1.0, depth / 1.6) * (1 - grow * 0.55)
                px[y][x] = (shade, shade, min(255, shade + 6), int(255 * alpha))
        save(A + f"textures/particle/steam_{f}.png", 16, 16, px)
    json.dump({"textures": [f"divinis:steam_{f}" for f in range(8)]}, open(A + "particles/steam.json", "w"), indent=2)


def tex16(regions, painters):
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for name, (x0, y0, w, h) in regions.items():
        for y in range(h):
            for x in range(w):
                px[y0 + y][x0 + x] = painters[name](x, y, w, h) + (255,)
    return px


IRON = [(40, 42, 48), (62, 65, 72), (88, 92, 100), (118, 122, 130), (152, 156, 164), (196, 200, 206)]
BRASS = [(110, 74, 24), (156, 110, 40), (198, 150, 62), (230, 190, 100)]


def pick(r, l):
    return r[max(0, min(len(r) - 1, int(round(l))))]


def el(frm, to, uv, rot=None):
    e = {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = {"angle": rot[1], "axis": rot[0], "origin": [8, 8, 8]}
    return e


def gear():
    regions = {"blade": (0, 0, 8, 8), "teeth": (8, 0, 8, 8), "hub": (0, 8, 8, 8), "edge": (8, 8, 8, 8)}
    rnd = random.Random(3)
    painters = {
        "blade": lambda x, y, w, h: pick(IRON, 2.6 + (1 if x + y < 4 else 0) - (1 if x + y > 11 else 0) + rnd.uniform(-0.3, 0.3)),
        "teeth": lambda x, y, w, h: pick(IRON, 4.6 if (x + y) % 3 == 0 else 3.6),
        "hub": lambda x, y, w, h: pick(BRASS, 2 + (1 if x < 2 or y < 2 else 0) - (1 if x > 5 or y > 5 else 0)),
        "edge": lambda x, y, w, h: pick(IRON, 5 if y % 2 == 0 else 4),
    }
    save(A + "textures/item/evil_brother_gear.png", 16, 16, tex16(regions, painters))
    blade, teeth, hub = [0, 0, 4, 4], [4, 0, 8, 4], [0, 4, 4, 8]
    elements = [
        el([3, 3, 7.5], [13, 13, 8.5], blade), el([3, 3, 7.5], [13, 13, 8.5], blade, ("z", 22.5)), el([3, 3, 7.5], [13, 13, 8.5], blade, ("z", -22.5)),
        el([1, 6.5, 7.6], [15, 9.5, 8.4], teeth), el([6.5, 1, 7.6], [9.5, 15, 8.4], teeth),
        el([1, 6.5, 7.6], [15, 9.5, 8.4], teeth, ("z", 45)), el([1, 6.5, 7.6], [15, 9.5, 8.4], teeth, ("z", -45)),
        el([6, 6, 7], [10, 10, 9], hub),
    ]
    json.dump({"parent": "minecraft:block/block", "textures": {"0": "divinis:item/evil_brother_gear", "particle": "divinis:item/evil_brother_gear"},
               "elements": elements}, open(A + "models/item/evil_brother_gear.json", "w"), indent=2)


def harpoon():
    regions = {"shaft": (0, 0, 8, 8), "head": (8, 0, 8, 8), "ring": (0, 8, 8, 8)}
    rnd = random.Random(4)
    painters = {
        "shaft": lambda x, y, w, h: pick(IRON, 2 + (1 if x == 0 else -1 if x == 7 else 0) + rnd.uniform(-0.3, 0.3)),
        "head": lambda x, y, w, h: pick(IRON, 4 + (1 if x < 2 else 0) - (1 if x > 5 else 0)),
        "ring": lambda x, y, w, h: pick(BRASS, 2 + (1 if y == 0 else 0)),
    }
    save(A + "textures/item/evil_brother_harpoon.png", 16, 16, tex16(regions, painters))
    shaft, head, ring = [0, 0, 4, 4], [4, 0, 8, 4], [0, 4, 4, 8]
    # along z: the shaft behind, the barbed head in front (-z)
    elements = [
        el([7.3, 7.3, 4], [8.7, 8.7, 16], shaft),
        el([6.8, 6.8, 13], [9.2, 9.2, 14.5], ring),
        el([6.5, 6.5, 1], [9.5, 9.5, 4], head),
        el([7.5, 7.5, -1], [8.5, 8.5, 1], head),
        el([5, 7.6, 2.5], [11, 8.4, 3.5], head),
        el([7.6, 5, 2.5], [8.4, 11, 3.5], head),
    ]
    json.dump({"parent": "minecraft:block/block", "textures": {"0": "divinis:item/evil_brother_harpoon", "particle": "divinis:item/evil_brother_harpoon"},
               "elements": elements}, open(A + "models/item/evil_brother_harpoon.json", "w"), indent=2)


import os
os.makedirs(A + "textures/particle", exist_ok=True)
os.makedirs(A + "particles", exist_ok=True)
steam()
gear()
harpoon()
print("ok")
