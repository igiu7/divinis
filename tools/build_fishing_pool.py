"""
Paints the fishing pools (fishing.FishingPool, client.FishingPoolRenderer): white with alpha, tinted to the pool's
tier colour when drawn (unlit: its colour is the same by day, by night and with night vision), 64x64 over the pool's 4 blocks (16 texels a block, as everywhere in Minecraft).

  fishing_pool.png         the pool: a bright rim (two rings), a see-through middle that gets stronger towards the
                           rim, a dotted ring inside, a few glints
  fishing_pool_ripple.png  a thin ring, drawn growing and fading out of the middle

  python tools/build_fishing_pool.py [out dir]   (default: the mod's textures/entity)
"""
import math
import os
import struct
import sys
import zlib

N = 64
C = N / 2


def hsh(x, y):
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def pool(x, y):
    dx, dy = x + 0.5 - C, y + 0.5 - C
    r = math.hypot(dx, dy)
    ang = math.atan2(dx, -dy)
    if r > 31.5:
        return None
    if r > 30.2:
        return (255, 255, 255, 255)
    if r > 29.2:
        return (205, 205, 205, 190)
    if r > 28.2:
        return (255, 255, 255, 245)
    v = 230
    a = int(80 + 100 * (r / 28.2) ** 2)
    if 19.4 < r < 20.6 and int((ang + math.pi) / (2 * math.pi) * 24) % 2 == 0:
        v, a = 255, 215
    if 10.4 < r < 11.4:
        a += 40
    if hsh(x, y) > 0.988 and r < 27:
        v, a = 255, 230
    return (v, v, v, min(255, a))


def ripple(x, y):
    r = math.hypot(x + 0.5 - C, y + 0.5 - C)
    if 29.6 < r <= 31.4:
        return (255, 255, 255, 255)
    if 28.6 < r <= 29.6:
        return (255, 255, 255, 140)
    return None


def save(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) if p else b"\x00\x00\x00\x00" for p in row) for row in px)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "src", "main", "resources", "assets", "divinis",
                                                             "textures", "entity")
    for name, paint in (("fishing_pool", pool), ("fishing_pool_ripple", ripple)):
        path = os.path.join(out, name + ".png")
        save(path, [[paint(x, y) for x in range(N)] for y in range(N)])
        print("saved", os.path.normpath(path))


if __name__ == "__main__":
    main()
