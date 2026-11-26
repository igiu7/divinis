"""
Paints the foam a fish leaves on the water as it swims to the bobber (client.FishingFoam): white with alpha, lit like
the world when drawn.

  fishing_foam.png       32x64, the wake: a band of foam (bubbles crowding at their rims, see-through hollows) fraying
                         at both sides; it tiles along its length (v), so the trail is one long strip of it
  fishing_foam_head.png  32x32, the churned patch where the fish is now: thick foam in the middle, broken at the edge

  python tools/build_fishing_foam.py [out dir]   (default: the mod's textures/entity)
"""
import math
import os
import random
import struct
import sys
import zlib


def cells(w, h, count, seed):
    """Points for a cell noise that wraps round both edges."""
    rnd = random.Random(seed)
    return [(rnd.uniform(0, w), rnd.uniform(0, h), rnd.uniform(0.7, 1.3)) for _ in range(count)]


def worley(x, y, pts, w, h):
    """Distances to the nearest and the second nearest point (wrapping)."""
    d1 = d2 = 1e9
    for px, py, s in pts:
        dx = min(abs(x - px), w - abs(x - px))
        dy = min(abs(y - py), h - abs(y - py))
        d = math.hypot(dx, dy) * s
        if d < d1:
            d1, d2 = d, d1
        elif d < d2:
            d2 = d
    return d1, d2


def hsh(x, y):
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def foam_pixel(d1, d2, edge, n):
    """edge: 1 in the thick of it, 0 where it ends. Rims of bubbles white, hollows faint."""
    rim = d2 - d1
    if rim < 1.1:
        v, a = 250, 235
    elif rim < 2.2:
        v, a = 236, 170
    else:
        v, a = 214, 70 + int(40 * n)
    a = int(a * edge)
    if a < 18:
        return None
    return (v, min(255, v + 4), 255, a)


def wavy(t, period, seed, octaves=3):
    """Smooth noise along t that repeats every period (a sum of sines with random phases)."""
    rnd = random.Random(seed)
    v = 0.0
    for k in range(1, octaves + 1):
        v += math.sin(2 * math.pi * (t / period) * k * rnd.choice((1, 2)) + rnd.uniform(0, 6.3)) / k
    return v / 1.8


def wake(x, y, pts, w, h):
    d1, d2 = worley(x + 0.5, y + 0.5, pts, w, h)
    n = hsh(x, y)
    side = x + 0.5 - w / 2
    across = abs(side) / (w / 2)                                         # 0 in the middle, 1 at the sides
    ragged = 0.22 * wavy(y, h, 3 if side < 0 else 4) + 0.08 * (n - 0.5)  # each side frays its own way
    edge = max(0.0, min(1.0, (1.0 - across + ragged) * 2.0 - 0.45))
    edge *= 0.55 + 0.45 * max(0.0, min(1.0, 0.6 + wavy(y, h, 9, 2)))     # thicker and thinner stretches
    return foam_pixel(d1, d2, edge, n)


def head(x, y, pts, w, h):
    d1, d2 = worley(x + 0.5, y + 0.5, pts, w, h)
    n = hsh(x + 7, y + 3)
    r = math.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2) / (w / 2)
    a = math.atan2(y + 0.5 - h / 2, x + 0.5 - w / 2)
    ragged = 0.2 * wavy(a, 2 * math.pi, 17) + 0.08 * (n - 0.5)
    edge = max(0.0, min(1.0, (1.0 - r + ragged) * 2.6 - 0.3))
    px = foam_pixel(d1 * 0.8, d2 * 0.8, edge, n)
    if px and r < 0.35:
        px = (250, 254, 255, min(255, px[3] + 50))  # the thick of it
    return px


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) if p else b"\x00\x00\x00\x00" for p in row) for row in px)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "src", "main", "resources", "assets", "divinis",
                                                             "textures", "entity")
    w, h = 32, 64
    pts = cells(w, h, 38, 11)
    save(os.path.join(out, "fishing_foam.png"), w, h, [[wake(x, y, pts, w, h) for x in range(w)] for y in range(h)])
    pts = cells(32, 32, 22, 5)
    save(os.path.join(out, "fishing_foam_head.png"), 32, 32, [[head(x, y, pts, 32, 32) for x in range(32)] for y in range(32)])
    print("saved", os.path.normpath(out))


if __name__ == "__main__":
    main()
