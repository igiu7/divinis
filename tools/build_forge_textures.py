"""
Paints the textures of the anvil's work (client.forge.AnvilBehavior), in the Minecraft manner (ramps of shades, lit from
the top-left, a little noise):
  textures/block/forge_hot.png     hot metal: 16 heats across (dark red to white-yellow), 16 variants down, 4 frames
                                   (animated, .mcmeta) so the metal shimmers; a pixel of the metal takes one texel
  textures/block/forge_hammer.png  the smith's hammer, 32x32: the iron head's sides (8x14, upright), its striking face
                                   (8x8), the iron band (8x4), the wooden haft and the leather grip (4x32 each, along
                                   their length)

  python tools/build_forge_textures.py
"""
import json
import random
import struct
import zlib

OUT = "src/main/resources/assets/divinis/textures/block/"

HOT = [(52, 6, 4), (74, 10, 6), (98, 16, 8), (124, 24, 10), (150, 34, 12), (176, 48, 14), (200, 66, 16), (220, 88, 20),
       (236, 112, 26), (246, 136, 34), (252, 160, 46), (255, 184, 64), (255, 204, 90), (255, 222, 124), (255, 238, 170),
       (255, 250, 220)]
IRON = [(22, 22, 26), (36, 37, 42), (52, 54, 60), (70, 72, 80), (92, 95, 104), (118, 121, 130), (146, 149, 158), (178, 181, 190), (210, 212, 220)]
WOOD = [(34, 22, 12), (50, 33, 18), (68, 46, 26), (88, 61, 35), (108, 76, 44), (130, 93, 55), (152, 111, 68)]
LEATHER = [(28, 16, 10), (44, 26, 16), (62, 38, 24), (82, 52, 32), (102, 66, 42), (122, 82, 54)]


def pick(ramp, level):
    return ramp[max(0, min(len(ramp) - 1, int(round(level))))]


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) + b"\xff" for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def hot():
    rnd = random.Random(11)
    frames = 4
    px = [[(0, 0, 0)] * 16 for _ in range(16 * frames)]
    base = [[rnd.uniform(-0.9, 0.9) for _ in range(16)] for _ in range(16)]
    for f in range(frames):
        for v in range(16):
            for heat in range(16):
                # each variant its own speckle; each frame a little brighter or dimmer, so the metal shimmers
                level = heat + base[v][heat] + 0.6 * ((f + v) % frames - 1.5) / 1.5 + (1.4 if rnd.random() < 0.04 else 0)
                px[f * 16 + v][heat] = pick(HOT, level)
    save(OUT + "forge_hot.png", 16, 16 * frames, px)
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, open(OUT + "forge_hot.png.mcmeta", "w"), indent=2)


def hammer():
    rnd = random.Random(5)
    px = [[(0, 0, 0)] * 32 for _ in range(32)]

    def region(x0, y0, w, h, f):
        for y in range(h):
            for x in range(w):
                px[y0 + y][x0 + x] = f(x, y, w, h)

    def iron_side(x, y, w, h):
        level = 4.2 + rnd.uniform(-0.6, 0.6)
        if y == 0 or x == 0:
            level += 2.0          # the lit top and left edges
        if y == h - 1 or x == w - 1:
            level -= 2.2          # the shaded ones
        if (x, y) in ((3, 2), (4, 2), (10, 5), (11, 5), (7, 3)):
            level -= 1.4          # dents of the work
        if x in (2, w - 3) and 0 < y < h - 1:
            level -= 0.8          # the swell of the cheeks
        return pick(IRON, level)

    def iron_face(x, y, w, h):
        d = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2))
        level = 7.0 - d * 0.9 + rnd.uniform(-0.5, 0.5)   # bright, polished by the blows
        if d >= 3:
            level = 3.0 + rnd.uniform(-0.4, 0.4)
        return pick(IRON, level)

    def wood(x, y, w, h):
        level = 3.6 + (0.9 if x == 0 else -0.9 if x == w - 1 else 0) + rnd.uniform(-0.4, 0.4)
        if (y * 3 + x * 5) % 11 == 0:
            level -= 1.2          # the grain
        return pick(WOOD, level)

    def leather(x, y, w, h):
        band = (y + x) % 4
        level = 3.8 - band * 0.9 + (0.6 if x == 0 else -0.6 if x == w - 1 else 0) + rnd.uniform(-0.3, 0.3)
        return pick(LEATHER, level)

    def band(x, y, w, h):
        level = 3.5 + (1.6 if y == 0 else -1.6 if y == h - 1 else 0) + rnd.uniform(-0.4, 0.4)
        if x % 4 == 1 and y == 1:
            level += 2.0          # rivets
        return pick(IRON, level)

    region(0, 0, 8, 14, iron_side)
    region(8, 0, 8, 8, iron_face)
    region(8, 8, 8, 4, band)
    region(16, 0, 4, 32, wood)
    region(20, 0, 4, 32, leather)
    save(OUT + "forge_hammer.png", 32, 32, px)


hot()
hammer()
print("ok")
