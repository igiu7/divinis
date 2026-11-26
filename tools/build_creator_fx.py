"""
The Nameless Creator's effect textures (textures/effect/creator_*.png), drawn by client.CreatorFx and client.BulletFx.
Those drawn added as light (RenderType.eyes) are black where there is nothing; the others have alpha.

  python tools/build_creator_fx.py <texture dir>

- creator_star      a star: a white-hot core, a four-pointed flare, a faint gold halo (light)
- creator_trail     a soft streak across (light): bright down the middle, fading to the sides
- creator_petal     a cherry petal, pink, its tip paler (alpha)
- creator_flower    a camellia from its crown: petals round a gold heart (alpha)
- creator_plane     the plane of a dimensional cut: white with a little violet, its edges eaten away (light)
- creator_decal     a circle of petals on the ground, where a flower will burst (light)
- creator_mark      a mark on the ground under a coming column: a ring, a cross, ticks (light)
- creator_column    a column of light: streaks running up it, brighter at its edges (light, tiles upwards)
- creator_plasma    the collapsing star: a ball of plasma cells, darker to its rim (light)
- creator_corona    the collapsing star's corona: rays all round, fading out (light)
"""
import math
import os
import random
import sys

from PIL import Image


def save(img, d, name):
    img.save(os.path.join(d, name + ".png"))


def light(w, h, fn):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b = fn(x, y)
            px[x, y] = (max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))), 255)
    return img


def noise2(w, h, cells, seed):
    """Smooth tiling value noise in 0..1."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / w * cells, y / h * cells
        x0, y0 = int(fx) % cells, int(fy) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return a * (1 - ty) + b * ty
    return at


def main():
    d = sys.argv[1]
    os.makedirs(d, exist_ok=True)

    def star(x, y):
        cx = cy = 15.5
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        core = max(0, 1 - r / 4.5) ** 1.5
        flare = max(0, 1 - abs(dx) / 1.4) * max(0, 1 - abs(dy) / 15) + max(0, 1 - abs(dy) / 1.4) * max(0, 1 - abs(dx) / 15)
        diag = (max(0, 1 - abs(dx - dy) / 1.2) + max(0, 1 - abs(dx + dy) / 1.2)) * max(0, 1 - r / 9) * 0.35
        halo = max(0, 1 - r / 15) ** 2 * 0.45
        v = core + flare * 0.8 + diag
        return 255 * min(1, v + halo), 255 * min(1, v + halo * 0.8), 255 * min(1, v * 0.9 + halo * 0.4)
    save(light(32, 32, star), d, "creator_star")

    save(light(16, 16, lambda x, y: (lambda k: (255 * k, 246 * k, 220 * k))(max(0, 1 - abs(x - 7.5) / 8) ** 2)), d, "creator_trail")

    petal = Image.new("RGBA", (16, 16))
    p = petal.load()
    for y in range(16):
        for x in range(16):
            # a teardrop pointing up, a notch at its tip
            t = y / 15
            half = 6.5 * math.sin(math.pi * (0.15 + 0.85 * t)) * (0.55 + 0.45 * t)
            if abs(x - 7.5) <= half and not (y > 13 and abs(x - 7.5) < 1.2):
                pale = 1 - t
                p[x, y] = (int(244 + 10 * pale), int(150 + 80 * pale), int(186 + 50 * pale), 255)
    save(petal, d, "creator_petal")

    flower = Image.new("RGBA", (32, 32))
    f = flower.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            edge = 11 + 3 * math.cos(a * 6) + 1.5 * math.cos(a * 12 + 1)
            if r < 3.5:
                f[x, y] = (250, 210, 80, 255) if (x + y) % 3 else (220, 150, 40, 255)
            elif r < edge:
                k = r / edge
                ring = math.cos(a * 6) * 0.5 + 0.5
                f[x, y] = (int(200 + 50 * k), int(30 + 60 * k * ring), int(60 + 70 * k), 255)
    save(flower, d, "creator_flower")

    nz = noise2(64, 16, 8, 3)

    def plane(x, y):
        edge = min(y, 15 - y) / 7.5
        n = nz(x, y)
        k = max(0, min(1, (edge * 1.4 - (0.55 - n * 0.5)) * 2.2))
        core = max(0, 1 - abs(y - 7.5) / 2.0)
        v = k * 0.75 + core * 0.6
        return 255 * min(1, v), 240 * min(1, v), 255 * min(1, v * 1.05)
    save(light(64, 16, plane), d, "creator_plane")

    def decal(x, y):
        dx, dy = x - 31.5, y - 31.5
        r = math.hypot(dx, dy) / 31.5
        a = math.atan2(dy, dx)
        ring = max(0, 1 - abs(r - 0.93) / 0.05)
        petals = max(0, math.cos(a * 8)) ** 3 * max(0, 1 - abs(r - 0.6) / 0.25) * 0.8
        inner = max(0, 1 - abs(r - 0.3) / 0.04) * 0.7
        fill = 0.12 if r < 0.95 else 0
        v = min(1, ring + petals + inner + fill)
        return 255 * v, 170 * v, 205 * v
    save(light(64, 64, decal), d, "creator_decal")

    def mark(x, y):
        dx, dy = x - 31.5, y - 31.5
        r = math.hypot(dx, dy) / 31.5
        a = math.atan2(dy, dx)
        ring = max(0, 1 - abs(r - 0.9) / 0.06)
        inner = max(0, 1 - abs(r - 0.45) / 0.04)
        cross = (max(0, 1 - abs(dx) / 1.2) + max(0, 1 - abs(dy) / 1.2)) * (1 if r < 0.8 else 0) * 0.7
        ticks = (1 if abs(math.cos(a * 4)) > 0.985 and 0.72 < r < 1 else 0)
        fill = 0.15 if r < 0.9 else 0
        v = min(1, ring + inner + cross + ticks + fill)
        return 255 * v, 210 * v, 120 * v
    save(light(64, 64, mark), d, "creator_mark")

    cz = noise2(32, 64, 8, 7)

    def column(x, y):
        streak = cz(x, y) ** 2
        fres = (abs(x - 15.5) / 15.5) ** 3
        v = 0.25 + streak * 0.55 + fres * 0.5
        return 255 * min(1, v), 246 * min(1, v), 214 * min(1, v * 0.95)
    save(light(32, 64, column), d, "creator_column")

    pz, pz2 = noise2(64, 64, 6, 11), noise2(64, 64, 12, 13)

    def plasma(x, y):
        # a ball: cells of plasma, darker towards its rim, nothing outside it
        r = math.hypot(x - 31.5, y - 31.5) / 31.5
        if r > 1:
            return 0, 0, 0
        n = pz(x, y) * 0.65 + pz2(x, y) * 0.35
        cell = 1 - abs(n - 0.5) * 2
        limb = math.sqrt(max(0, 1 - r * r)) * 0.6 + 0.4
        edge = min(1, (1 - r) * 12)
        v = (0.45 + cell ** 3 * 0.6) * limb * edge
        return 255 * min(1, v * 1.1), 200 * min(1, v), 120 * min(1, v * 0.8)
    save(light(64, 64, plasma), d, "creator_plasma")

    rnd = random.Random(17)
    rays = [rnd.random() for _ in range(72)]

    def corona(x, y):
        dx, dy = x - 63.5, y - 63.5
        r = math.hypot(dx, dy) / 63.5
        a = (math.atan2(dy, dx) / math.tau + 1) % 1 * 72
        i = int(a)
        t = a - i
        ray = rays[i] * (1 - t) + rays[(i + 1) % 72] * t
        v = max(0, 1 - r) ** 1.5 * (0.35 + ray ** 2 * 1.2) if r > 0.25 else 1.0
        return 255 * min(1, v), 220 * min(1, v), 150 * min(1, v * 0.8)
    save(light(128, 128, corona), d, "creator_corona")

    # a black star: a black core in a violet corona with rays (alpha)
    vs = Image.new("RGBA", (32, 32))
    v = vs.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy) / 15.5
            a = math.atan2(dy, dx)
            if r < 0.3:
                v[x, y] = (6, 2, 10, 255)
            elif r < 1:
                rays = 0.6 + 0.4 * abs(math.cos(a * 4))
                k = max(0, 1 - (r - 0.3) / 0.7) ** 1.6 * rays
                rim = max(0, 1 - abs(r - 0.33) / 0.06)
                v[x, y] = (int(150 + 100 * rim), int(60 + 120 * rim), 255, int(255 * min(1, k + rim)))
    save(vs, d, "creator_voidstar")

    # a soft round blob, white (tinted when drawn: light or goo) (alpha)
    blob = Image.new("RGBA", (32, 32))
    bp = blob.load()
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5) / 15.5
            bp[x, y] = (255, 255, 255, int(255 * max(0, 1 - r * r) ** 1.5))
    save(blob, d, "creator_blob")

    # a square of a zone on the floor, its edges feathered (alpha)
    zone = Image.new("RGBA", (32, 32))
    zp = zone.load()
    for y in range(32):
        for x in range(32):
            e = min(x + 0.5, y + 0.5, 31.5 - x, 31.5 - y) / 6
            zp[x, y] = (255, 255, 255, int(255 * min(1, e) ** 1.5))
    save(zone, d, "creator_zone")

    # the hands that crush the hunger bar (on the HUD): open, and a fist; reaching to the right, white (tinted)
    hands = Image.new("RGBA", (64, 32))
    hp = hands.load()

    def fill(ox, rects):
        for x0, y0, x1, y1 in rects:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    hp[ox + x, y] = (236, 232, 224, 255)

    fill(0, [(3, 10, 15, 23), (15, 10, 28, 12), (15, 13, 30, 15), (15, 16, 29, 18), (15, 19, 26, 21), (9, 5, 12, 10), (11, 3, 16, 6),
             (0, 13, 4, 20)])
    fill(32, [(5, 9, 19, 24), (19, 10, 23, 14), (19, 14, 24, 18), (19, 18, 23, 22), (10, 6, 20, 9), (0, 12, 6, 21)])
    for y in range(32):  # dark edges, and a crease between the fingers
        for x in range(64):
            if hp[x, y][3] == 0:
                continue
            edge = any(not (0 <= x + dx < 64 and 0 <= y + dy < 32) or hp[x + dx, y + dy][3] == 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            crease = x >= 32 and x - 32 in (19, 20) and y in (14, 18)
            if edge or crease:
                hp[x, y] = (90, 84, 80, 255)
    save(hands, d, "creator_crush")
    print("creator effect textures in", d)


main()
