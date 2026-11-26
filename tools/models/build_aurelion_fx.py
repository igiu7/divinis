"""
Builds the textures of Aurelion's effects (client/AurelionRenderer, client/BeamFx), plain Python:

  python tools/models/build_aurelion_fx.py <textures/effect dir>

- sky_crack.png (128 x 128): the tear in the sky - a jagged crack with branches, black inside with a faint violet
  depth and specks of stars, its alpha the crack's shape;
- sky_crack_glow.png: the light pouring round the crack's edges (white to gold, fading out), for additive drawing;
- black_hole.png (64 x 64): a black disc with a soft edge;
- photon_ring.png (64 x 64): a thin bright ring (light bent round the hole), additive;
- accretion.png (128 x 128): a glowing disc of swirling matter round a hole in the middle, additive;
- beam.png (16 x 16): a beam's cross-section along u - a white core fading to the sides (tinted when drawn);
- light_glow.png (32 x 32): a soft round glow, white (tinted when drawn);
- light_shield.png (32 x 48): a pane of light - a bright gold frame, a soft white middle with faint runes, for
  drawing additive (Aurelion plants them in the floor before its star collapses).
"""
import math
import os
import random
import struct
import sys
import zlib


def save_png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def clamp(v, a=0.0, b=1.0):
    return max(a, min(b, v))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / max(1e-6, dx * dx + dy * dy))
    return math.hypot(px - ax - t * dx, py - ay - t * dy), t


def crack(out):
    rnd = random.Random(21)
    n = 128
    # the main crack across, jagged, and branches off it: segments (a, b, width at a, width at b)
    segs = []
    pts = []
    x, y = 6.0, 64.0
    while x < 122:
        nx = x + rnd.uniform(5, 10)
        ny = clamp(y + rnd.uniform(-9, 9), 30, 98)
        pts.append((x, y, nx, ny))
        x, y = nx, ny
    for i, (ax, ay, bx, by) in enumerate(pts):
        k0, k1 = i / len(pts), (i + 1) / len(pts)
        w0, w1 = 1 + 7 * math.sin(k0 * math.pi), 1 + 7 * math.sin(k1 * math.pi)
        segs.append((ax, ay, bx, by, w0, w1))
        if rnd.random() < 0.55 and 0.15 < k0 < 0.85:
            bxx, byy = ax, ay
            ang = rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.3) + rnd.uniform(-0.3, 0.3)
            wb = w0 * 0.5
            for j in range(rnd.randint(2, 4)):
                nx = bxx + math.cos(ang) * rnd.uniform(5, 9)
                ny = byy + math.sin(ang) * rnd.uniform(5, 9)
                segs.append((bxx, byy, nx, ny, wb, wb * 0.6))
                bxx, byy, wb = nx, ny, wb * 0.6
                ang += rnd.uniform(-0.5, 0.5)
    void = [[(0, 0, 0, 0)] * n for _ in range(n)]
    glow = [[(0, 0, 0, 255)] * n for _ in range(n)]
    for py in range(n):
        for px in range(n):
            inside, edge = 0.0, 0.0
            for ax, ay, bx, by, w0, w1 in segs:
                d, t = seg_dist(px + 0.5, py + 0.5, ax, ay, bx, by)
                w = w0 + (w1 - w0) * t
                inside = max(inside, clamp((w - d) / 1.2))
                edge = max(edge, clamp(1 - (d - w * 0.6) / (w * 1.8 + 6)))
            if inside > 0:
                star = rnd.random() < 0.025
                c = (230, 220, 255) if star else (int(14 + 20 * rnd.random()), 6, int(26 + 24 * rnd.random()))
                void[py][px] = c + (int(inside * 255),)
            if edge > 0:
                e = edge ** 2.2
                r, g, b = 255 * e, (236 * e) * (0.7 + 0.3 * e), (170 * e) * e
                glow[py][px] = (int(r), int(g), int(b), 255)
    save_png(os.path.join(out, "sky_crack.png"), n, n, void)
    save_png(os.path.join(out, "sky_crack_glow.png"), n, n, glow)


def hole(out):
    n = 64
    disc, ring = [], []
    for y in range(n):
        drow, rrow = [], []
        for x in range(n):
            d = math.hypot(x - 31.5, y - 31.5) / 31.5
            drow.append((0, 0, 0, int(clamp((1 - d) / 0.12) * 255)))
            r = math.exp(-((d - 0.86) / 0.05) ** 2) + 0.35 * math.exp(-((d - 0.93) / 0.12) ** 2)
            v = clamp(r)
            rrow.append((int(255 * v), int(238 * v), int(200 * v), 255))
        disc.append(drow)
        ring.append(rrow)
    save_png(os.path.join(out, "black_hole.png"), n, n, disc)
    save_png(os.path.join(out, "photon_ring.png"), n, n, ring)

    n = 128
    rnd = random.Random(5)
    streaks = [(rnd.uniform(0, math.tau), rnd.uniform(0.45, 0.98), rnd.uniform(0.4, 1.0)) for _ in range(90)]
    acc = []
    for y in range(n):
        row = []
        for x in range(n):
            dx, dy = (x - 63.5) / 63.5, (y - 63.5) / 63.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            if d < 0.38 or d > 1.0:
                row.append((0, 0, 0, 255))
                continue
            base = math.sin((d - 0.38) / 0.62 * math.pi) ** 1.5
            swirl = 0.0
            for sa, sr, sk in streaks:
                # streaks spiralling in: the angle a streak sits at shifts with the radius
                da = (a - sa - (1 - d) * 3.0) % math.tau
                da = min(da, math.tau - da)
                swirl += sk * math.exp(-(da / 0.08) ** 2) * math.exp(-((d - sr) / 0.12) ** 2)
            v = clamp(base * (0.45 + 0.9 * swirl))
            hot = clamp(1 - (d - 0.38) / 0.35)  # white-hot inside, gold, then deep orange outside
            r = 255 * v
            g = (150 + 100 * hot) * v
            b = (40 + 190 * hot ** 2) * v
            row.append((int(r), int(g), int(b), 255))
        acc.append(row)
    save_png(os.path.join(out, "accretion.png"), n, n, acc)


def beam_and_glow(out):
    beam = []
    for y in range(16):
        row = []
        for x in range(16):
            u = abs((x + 0.5) / 16 - 0.5) * 2  # 0 in the middle, 1 at the sides
            core = clamp(1 - u / 0.35)
            halo = clamp(1 - u) ** 2
            v = clamp(core + halo * 0.55)
            row.append((int(255 * v), int(255 * v), int(255 * v), 255))
        beam.append(row)
    save_png(os.path.join(out, "beam.png"), 16, 16, beam)
    glow = []
    for y in range(32):
        row = []
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 15.5
            v = clamp(1 - d) ** 2.2
            row.append((int(255 * v), int(255 * v), int(255 * v), 255))
        glow.append(row)
    save_png(os.path.join(out, "light_glow.png"), 32, 32, glow)


def shield(out):
    rnd = random.Random(9)
    w, h = 32, 48
    px = []
    for y in range(h):
        row = []
        for x in range(w):
            ex = min(x, w - 1 - x) / (w / 2)
            ey = min(y, h - 1 - y) / (h / 2)
            e = min(ex, ey)
            frame = clamp(1 - e / 0.12)
            body = 0.28 + 0.12 * math.sin(y * 0.4) * math.sin(x * 0.5)
            rune = 0.35 if (x % 7 == 3 and 8 < y < 40 and rnd.random() < 0.7) or (y % 9 == 4 and 6 < x < 26 and rnd.random() < 0.5) else 0
            v = clamp(body + rune)
            r = 255 * clamp(frame + v)
            g = 255 * clamp(frame * 0.82 + v * 0.95)
            b = 255 * clamp(frame * 0.4 + v * 0.8)
            row.append((int(r), int(g), int(b), 255))
        px.append(row)
    save_png(os.path.join(out, "light_shield.png"), w, h, px)


def main():
    out = sys.argv[1]
    shield(out)
    os.makedirs(out, exist_ok=True)
    crack(out)
    hole(out)
    beam_and_glow(out)
    print("aurelion fx textures in", out)


main()
