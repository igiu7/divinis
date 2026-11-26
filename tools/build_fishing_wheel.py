"""
Paints the fishing minigame's wheel (client.FishingHud, fishing.FishingGame): a 64x64 pixel-art dial the HUD draws
two GUI pixels a texel. Its static parts only - the catch zone and the needle are drawn over it in the same grid at
run time (they move):

  rim         dark slate, bevelled (lit from the top left), a black outline
  track       r 21-27.5: deep water, rippled, notches every 30 degrees (bigger at the four quarters) - the zone
              lights up on it
  face        a dark navy disc with a faint ring
  hub         a bevelled steel cap round a brass rivet; a little fish on the face above it

  python tools/build_fishing_wheel.py [out.png]   (default: the mod's textures/gui/fishing_wheel.png)
"""
import math
import os
import struct
import sys
import zlib

N = 64
C = N / 2
TRACK_IN, TRACK_OUT = 21.0, 27.5  # client.FishingHud draws the zone between these radii


# a little fish, 9x5, its nose to the right; (x, y) offsets from the face's mark at (28, 14)
FISH = {(x, y) for y, row in enumerate([
    ".  ###  .",
    "# ##### .",
    "#########",
    "# ##### .",
    ".  ###  .",
]) for x, ch in enumerate(row) if ch == "#"}


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hsh(x, y):
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def pixel(x, y):
    dx, dy = x + 0.5 - C, y + 0.5 - C
    r = math.hypot(dx, dy)
    ang = math.degrees(math.atan2(dx, -dy)) % 360  # 0 at the top, clockwise
    n = hsh(x, y)
    if r > 31.5:
        return None
    if r > 30.6:
        return (13, 16, 22)
    if r > 28.0:  # the rim: lit from the top left
        light = (-dx - dy) / (r * math.sqrt(2))
        base = mix((34, 39, 50), (78, 88, 106), max(0.0, light)) if light > 0 else mix((34, 39, 50), (21, 24, 31), -light)
        if r > 30.0 and light > 0.35:
            base = mix(base, (120, 132, 152), 0.6)  # the bright edge on top
        return mix(base, (0, 0, 0), 0.08 * n)
    if r > TRACK_OUT:
        return (16, 20, 28)
    if r > TRACK_IN:  # the water
        depth = (r - TRACK_IN) / (TRACK_OUT - TRACK_IN)
        c = mix((22, 64, 104), (38, 104, 160), depth)
        # glints on the water: short dashes along the track here and there
        cell = int(ang / 9)
        if hsh(cell, int(r)) > 0.72 and abs(ang - cell * 9 - 4.5) < 2.2 and 22.5 < r < 26.5:
            c = mix(c, (110, 180, 228), 0.5)
        c = mix(c, (10, 30, 56), 0.12 * n)
        step = 360 / (2 * math.pi * r)  # degrees a pixel spans here
        for k in range(12):
            d = abs((ang - k * 30 + 180) % 360 - 180)
            big = k % 3 == 0
            if d < step * 0.6 and r > (TRACK_IN + 1.2 if big else TRACK_OUT - 2.6):
                c = (196, 226, 248) if big else (126, 176, 216)
        return c
    if r > TRACK_IN - 1.0:
        return (12, 16, 24)
    if r > 6.6:  # the face
        c = mix((24, 34, 50), (17, 24, 36), (r - 6.6) / 14)
        if 12.6 < r < 13.4:
            c = mix(c, (44, 62, 86), 0.7)
        if (x - 28, y - 14) in FISH:  # the maker's mark: a little fish on the face
            c = (52, 84, 118)
        return mix(c, (8, 12, 20), 0.15 * n)
    if r > 5.6:
        return (12, 14, 18)
    # the hub: a bevelled steel cap round a brass rivet
    if r < 2.2:
        return (255, 222, 130) if dx < 0 and dy < 0 else (206, 156, 60)
    if r < 2.9:
        return (70, 52, 24)
    light = (-dx - dy) / (r * math.sqrt(2))
    return mix((96, 106, 120), (190, 200, 214), (light + 1) / 2)


def save(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p + (255,)) if p else b"\x00\x00\x00\x00" for p in row) for row in px)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        here, "..", "src", "main", "resources", "assets", "divinis", "textures", "gui", "fishing_wheel.png")
    save(out, [[pixel(x, y) for x in range(N)] for y in range(N)])
    print("saved", os.path.normpath(out))


if __name__ == "__main__":
    main()
