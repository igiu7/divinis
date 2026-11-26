"""
The Arachnid's effects (boss.Arachnid, client.ArachnidParticles, client.ScreenFxClient):
  textures/particle/silk_0..7.png        a wisp of torn web (registry.ModParticles.SILK), 16x16: a tangle of a few
                                         strands, white and grey, from a thick knot (0) to two or three thin threads (7)
  textures/particle/acid_0..3.png        a falling drop of acid (ACID), 16x16: bright green, a light glint, a little
                                         longer each frame (it stretches as it falls)
  textures/particle/acid_splash_0..7.png where a drop lands (ACID_SPLASH), 16x16: a spray of green droplets in a ring,
                                         spreading out and thinning
  textures/particle/venom_0..7.png       a puff of poison (VENOM), 16x16: dark green and sickly yellow mist, from a dense
                                         puff to a thin haze
  particles/*.json                       their sprite lists
  textures/screenfx/web.png              a web over the screen (client.ScreenFxClient, WEB), 256x256: threads running in
                                         from the edges and corners, rings of sticky thread between them, the middle open

  python tools/build_arachnid_fx.py
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


def blank(w, h):
    return [[(0, 0, 0, 0)] * w for _ in range(h)]


def put(px, x, y, c, a):
    h, w = len(px), len(px[0])
    if 0 <= x < w and 0 <= y < h and a > px[y][x][3]:
        px[y][x] = (c[0], c[1], c[2], max(0, min(255, int(a))))


def line(px, x0, y0, x1, y1, c, a):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / n
        put(px, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c, a)


def sprites(name, frames):
    with open(A + f"particles/{name}.json", "w") as f:
        json.dump({"textures": [f"divinis:{name}_{i}" for i in range(frames)]}, f, indent=2)


def silk():
    rnd = random.Random(3)
    for i in range(8):
        px = blank(16, 16)
        strands = 6 - i * 5 // 8
        alpha = 240 - i * 18
        for s in range(max(2, strands)):
            ang = rnd.uniform(0, math.pi)
            r = 5 + rnd.uniform(0, 2.5)
            cx, cy = 8 + rnd.uniform(-1.5, 1.5), 8 + rnd.uniform(-1.5, 1.5)
            c = (246, 244, 238) if s % 2 == 0 else (206, 202, 194)
            # a strand, sagging a little in the middle
            pts = []
            for k in range(9):
                t = k / 8 * 2 - 1
                sag = (1 - t * t) * 1.2
                pts.append((cx + math.cos(ang) * r * t - math.sin(ang) * sag, cy + math.sin(ang) * r * t + math.cos(ang) * sag))
            for a, b in zip(pts, pts[1:]):
                line(px, a[0], a[1], b[0], b[1], c, alpha)
        if i < 3:   # the knot in the middle
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1))[:6 - i * 2]:
                put(px, 8 + dx, 8 + dy, (255, 255, 255), alpha)
        save(A + f"textures/particle/silk_{i}.png", 16, 16, px)
    sprites("silk", 8)


GREEN = [(40, 120, 20), (90, 190, 30), (150, 236, 60), (214, 255, 140)]


def acid():
    for i in range(4):
        px = blank(16, 16)
        length = 4 + i
        for y in range(length):
            half = 2 if y >= length - 3 else 1 if y >= length - 5 else 0
            for x in range(8 - half, 8 + half + 1 if half else 9):
                edge = x in (8 - half, 8 + half)
                put(px, x, 4 + y, GREEN[1] if edge else GREEN[2], 255)
        put(px, 7, 4 + length - 3, GREEN[3], 255)   # the glint
        put(px, 8, 4 + length - 1, GREEN[0], 255)
        save(A + f"textures/particle/acid_{i}.png", 16, 16, px)
    sprites("acid", 4)


def acid_splash():
    rnd = random.Random(9)
    for i in range(8):
        px = blank(16, 16)
        r = 1.5 + i * 0.85
        alpha = 255 - i * 28
        for k in range(14):
            ang = k / 14 * math.tau + rnd.uniform(-0.15, 0.15)
            rr = r * rnd.uniform(0.8, 1.1)
            x, y = 8 + math.cos(ang) * rr, 8 + math.sin(ang) * rr * 0.75
            put(px, int(round(x)), int(round(y)), GREEN[2] if k % 3 else GREEN[3], alpha)
            if i < 4:
                put(px, int(round(8 + math.cos(ang) * rr * 0.6)), int(round(8 + math.sin(ang) * rr * 0.45)), GREEN[1], alpha * 0.8)
        if i < 2:
            for dx in (-1, 0, 1):
                put(px, 8 + dx, 8, GREEN[2], 255)
        save(A + f"textures/particle/acid_splash_{i}.png", 16, 16, px)
    sprites("acid_splash", 8)


def venom():
    rnd = random.Random(5)
    cols = [(34, 60, 18), (60, 96, 26), (110, 140, 40), (170, 180, 70)]
    for i in range(8):
        px = blank(16, 16)
        spread = 3.0 + i * 0.6
        density = 0.95 - i * 0.1
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5) / spread
                if d > 1.0:
                    continue
                n = rnd.random()
                if n > density * (1 - d * d) + 0.1:
                    continue
                c = cols[min(3, int((1 - d) * 2 + rnd.random() * 2))]
                put(px, x, y, c, (1 - d) * 230 * (1 - i / 10))
        save(A + f"textures/particle/venom_{i}.png", 16, 16, px)
    sprites("venom", 8)


def web():
    W = 256
    px = blank(W, W)
    rnd = random.Random(12)
    silk_c = (240, 238, 232)

    def thread(x0, y0, x1, y1, a):
        line(px, x0, y0, x1, y1, silk_c, a)

    # threads from the corners and edges in towards the middle (stopping short of it)
    anchors = []
    for cx, cy in ((0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)):
        for k in range(7):
            ang = math.atan2(W / 2 - cy, W / 2 - cx) + (k - 3) * 0.17
            length = rnd.uniform(95, 135)
            ex, ey = cx + math.cos(ang) * length, cy + math.sin(ang) * length
            thread(cx, cy, ex, ey, 230)
            anchors.append((cx, cy, ang, length))
    for side in range(4):
        for k in range(3):
            p = rnd.uniform(60, 196)
            x, y = [(p, 0), (W - 1, p), (p, W - 1), (0, p)][side]
            ang = math.atan2(W / 2 - y, W / 2 - x) + rnd.uniform(-0.25, 0.25)
            length = rnd.uniform(50, 80)
            thread(x, y, x + math.cos(ang) * length, y + math.sin(ang) * length, 200)
    # sticky rings across each corner's threads, sagging between them
    for cx, cy in ((0, 0), (W - 1, 0), (0, W - 1), (W - 1, W - 1)):
        rays = [a for a in anchors if a[0] == cx and a[1] == cy]
        rays.sort(key=lambda a: a[2])
        for ring in range(9):
            r = 18 + ring * 11
            for (_, _, a0, l0), (_, _, a1, l1) in zip(rays, rays[1:]):
                if r > min(l0, l1):
                    continue
                x0, y0 = cx + math.cos(a0) * r, cy + math.sin(a0) * r
                x1, y1 = cx + math.cos(a1) * r, cy + math.sin(a1) * r
                mx, my = (x0 + x1) / 2, (y0 + y1) / 2
                sag = 3
                dx, dy = W / 2 - cx, W / 2 - cy
                dl = math.hypot(dx, dy)
                mx += dx / dl * sag
                my += dy / dl * sag
                thread(x0, y0, mx, my, 170 - ring * 8)
                thread(mx, my, x1, y1, 170 - ring * 8)
                if rnd.random() < 0.3:   # a drop of glue
                    put(px, int(mx), int(my), (255, 255, 255), 255)
    save(A + "textures/screenfx/web.png", W, W, px)


silk()
acid()
acid_splash()
venom()
web()
print("saved the Arachnid's effects")
