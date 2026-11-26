"""
Builds Aurelion, Who Was Before Light (the final boss) in Blender: a cluster of many eyes - one great eye in the
middle, a ring of seven round it and six small ones further out, each looking its own way - with three pairs of
angelic wings (a white, gold-trimmed arm and a fan of long feathers with gold tips) and, behind it all, a great
symbol of metal gold: a ring, eight rays (long and short) and a disc. Its two dead hands are a model of their own
(build_aurelion_hand.py). 256x256 texture and glow texture (the irises). Centred on the middle of the great eye;
client/AurelionRenderer draws it three times as big.

  blender -b --python tools/models/build_aurelion.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/AurelionModelData.java.

Animations: idle 4.0 s loop (wings beat slowly, the symbol turns once round, the eyes look about),
            summon 1.5 s (wings rise high and spread, the eyes roll up), gaze 1.0 s (every eye turns to the front,
            wings fold forward), descend 2.0 s loop (wings spread wide and beat slowly, for coming down from the sky)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX = 256

SCLERA, SCLERA_D, VEIN = (236, 230, 216), (200, 190, 176), (168, 60, 52)
IRIS, IRIS_D, IRIS_L = (232, 168, 40), (168, 96, 16), (255, 222, 120)
PUPIL = (10, 6, 8)
LID, LID_D = (214, 196, 178), (172, 150, 132)
FEATHER, FEATHER_D, FEATHER_L = (238, 234, 224), (196, 188, 176), (252, 250, 244)
GOLD_DEEP, GOLD_D, GOLD, GOLD_L, GOLD_SPEC = (92, 58, 14), (142, 96, 22), (206, 152, 44), (246, 206, 106), (255, 244, 200)
GLOW_IRIS, GLOW_CORE = (255, 210, 110), (255, 246, 210)


def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


PARTS = [("body", None, (0, 0, 0), (0, 0, 0))]
CUBES = []
KIND = {}  # cube name -> eye / feather / tip / arm / gold

# ---- eyes
EYES = [("eye_main", (0, 0, 0), 12, (0, 0, 0))]
for i in range(7):
    a = math.radians(i * 360 / 7 + 12)
    x, y = math.cos(a) * 12.5, math.sin(a) * 11.0
    EYES.append((f"eye_ring{i}", (x, y, 2.5), 8 if i % 2 == 0 else 6.5, (math.sin(a) * 18, -math.cos(a) * 22, 0)))
for i in range(6):
    a = math.radians(i * 60 + 40)
    x, y = math.cos(a) * 20.5, math.sin(a) * 17.0
    EYES.append((f"eye_small{i}", (x, y, 5), 4.5, (math.sin(a) * 28, -math.cos(a) * 30, 0)))
for name, pos, size, rot in EYES:
    PARTS.append((name, "body", pos, rot))
    CUBES.append([name, name, (-size / 2, -size / 2, -size / 2), (size, size, size), None])
    KIND[name] = "eye"

# ---- wings: three pairs, each an arm with a fan of feathers
WING_UP = [38, 6, -26]
FEATHERS = 9
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        PARTS.append((arm, "body", (s * 8, -5 + i * 5, 5), (0, -s * 22, -s * WING_UP[i])))
        length = 32 - i * 3
        CUBES.append([arm, arm, (0 if s > 0 else -length, -2, -2), (length, 4, 4), None])
        KIND[arm] = "arm"
        for j in range(FEATHERS):
            # the long flight feathers, side by side and overlapping, fanning outwards
            f = f"{arm}_f{j}"
            fl = 15 + j * 2.4 - i * 1.5
            PARTS.append((f, arm, (s * (2.5 + j * (length - 4) / (FEATHERS - 1)), 1.5, 0.5), (8, 0, -s * (4 + j * 5.5))))
            CUBES.append([f, f, (-2.5, 0, -0.5), (5, fl, 1), None])
            CUBES.append([f + "_tip", f, (-2, fl, -0.25), (4, 2.5, 0.5), None])
            KIND[f] = "feather"
            KIND[f + "_tip"] = "tip"
            # a short covert over its root, in front
            c = f"{arm}_c{j}"
            PARTS.append((c, arm, (s * (2.5 + j * (length - 4) / (FEATHERS - 1)), 1.5, -1.2), (4, 0, -s * (2 + j * 4))))
            CUBES.append([c, c, (-2.75, -0.5, -0.5), (5.5, 7 + j * 0.6, 1), None])
            KIND[c] = "covert"

# ---- the symbol of gold behind it
PARTS.append(("symbol", "body", (0, 0, 15), (0, 0, 0)))
for i in range(16):
    seg = f"ring{i}"
    PARTS.append((seg, "symbol", (0, 0, 0), (0, 0, i * 22.5)))
    CUBES.append([seg, seg, (-6.1, -32, -1.25), (12.2, 3.5, 2.5), None])
    KIND[seg] = "gold"
for i in range(12):
    seg = f"inner{i}"
    PARTS.append((seg, "symbol", (0, 0, 0), (0, 0, i * 30 + 15)))
    CUBES.append([seg, seg, (-4.6, -18, -0.75), (9.2, 2, 1.5), None])
    KIND[seg] = "gold"
for i in range(8):
    ray = f"ray{i}"
    long = i % 2 == 0
    PARTS.append((ray, "symbol", (0, 0, 0), (0, 0, i * 45 + 22.5)))
    CUBES.append([ray, ray, (-1.5 if long else -1.1, -48 if long else -40, -0.9), (3 if long else 2.2, 40 if long else 32, 1.8), None])
    CUBES.append([ray + "_tip", ray, (-0.6, -53 if long else -44, -0.6), (1.2, 5 if long else 4, 1.2), None])
    KIND[ray] = "gold"
    KIND[ray + "_tip"] = "gold"
for i in range(8):
    ray = f"thin{i}"
    PARTS.append((ray, "symbol", (0, 0, 0), (0, 0, i * 45)))
    CUBES.append([ray, ray, (-0.5, -34, -0.5), (1, 22, 1), None])
    KIND[ray] = "gold"
PARTS.append(("disc", "symbol", (0, 0, 0), (0, 0, 45)))
CUBES.append(["disc", "disc", (-6, -6, -1.5), (12, 12, 3), None])
CUBES.append(["disc_b", "symbol", (-6, -6, -1.25), (12, 12, 2.5), None])
KIND["disc"] = KIND["disc_b"] = "gold"

# ---------------------------------------------------------------- animations
ANIMS = []
idle = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        ph = i * 0.25
        for t in (0, 1, 2, 3, 4):
            beat = math.sin((t / 4 + ph) * math.pi * 2)
            idle.append(k(t, arm, (0, 0, -s * beat * 7)))
for t, a in ((0, 0), (1, 90), (2, 180), (3, 270), (4, 360)):
    idle.append(k(t, "symbol", (0, 0, a), linear=True))
for n, (name, _, _, _) in enumerate(EYES):
    rnd = random.Random(n * 7 + 1)
    pts = [(0, 0)] + [(rnd.uniform(-14, 14), rnd.uniform(-16, 16)) for _ in range(3)] + [(0, 0)]
    for t, (dx, dy) in zip((0, 1, 2, 3, 4), pts):
        idle.append(k(t, name, (dx, dy, 0)))
ANIMS.append(("idle", 0, 4.0, True, idle))

summon = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        summon += [k(0, arm, (0, 0, 0)), k(0.4, arm, (0, s * 14, -s * 26)), k(1.2, arm, (0, s * 14, -s * 26)), k(1.5, arm, (0, 0, 0))]
for name, _, _, _ in EYES:
    summon += [k(0, name, (0, 0, 0)), k(0.3, name, (-35, 0, 0)), k(1.2, name, (-35, 0, 0)), k(1.5, name, (0, 0, 0))]
ANIMS.append(("summon", 100, 1.5, False, summon))

gaze = []
for name, _, _, rot in EYES:
    gaze += [k(0, name, (0, 0, 0)), k(0.2, name, (-rot[0], -rot[1], 0)), k(0.8, name, (-rot[0], -rot[1], 0)), k(1.0, name, (0, 0, 0))]
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        gaze += [k(0, arm, (0, 0, 0)), k(0.25, arm, (0, s * 30, 0)), k(0.8, arm, (0, s * 30, 0)), k(1.0, arm, (0, 0, 0))]
ANIMS.append(("gaze", 140, 1.0, False, gaze))

descend = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        for t in (0, 1, 2):
            beat = math.cos(t / 2 * math.pi * 2)
            descend.append(k(t, arm, (0, s * 10, -s * (14 + beat * 10))))
ANIMS.append(("descend", 180, 2.0, True, descend))

# before its second phase: every eye rolls back, the wings fold round it, the symbol shudders and cracks
seal = []
for name, _, _, _ in EYES:
    seal += [k(0, name, (0, 0, 0)), k(0.5, name, (-82, 0, 0)), k(3.0, name, (-82, 0, 0))]
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        seal += [k(0, arm, (0, 0, 0)), k(0.8, arm, (0, s * 42, s * 10)), k(3.0, arm, (0, s * 46, s * 12))]
for n, t in enumerate([x * 0.1 for x in range(31)]):
    seal.append(k(t, "symbol", (0, 0, (0 if n == 0 else (4 if n % 2 else -4) * min(1.0, t / 1.5))), linear=True))
ANIMS.append(("seal", 230, 3.0, False, seal))


# ---------------------------------------------------------------- painting
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gold(tex, x0, y0, w, h, rnd):
    """Polished gold: lit from above, a dark lower edge, brushed streaks and a few bright glints."""
    streak = [rnd.random() for _ in range(max(1, w))]
    for yy in range(h):
        for xx in range(w):
            k_ = yy / max(1, h - 1)
            c = lerp(GOLD_L, GOLD, min(1, k_ * 1.6)) if k_ < 0.6 else lerp(GOLD, GOLD_D, (k_ - 0.6) / 0.4)
            if streak[xx] > 0.82:
                c = lerp(c, GOLD_L, 0.45)
            elif streak[xx] < 0.12:
                c = lerp(c, GOLD_D, 0.35)
            if xx == 0 or xx == w - 1:
                c = lerp(c, GOLD_DEEP, 0.35)
            if yy == h - 1 and h > 1:
                c = lerp(c, GOLD_DEEP, 0.5)
            if rnd.random() < 0.015:
                c = GOLD_SPEC
            tex.set(x0 + xx, y0 + yy, c, 0.02)


def eye(tex, glow, r, rnd, front):
    x0, y0, w, h = (int(round(n)) for n in r)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    rad = min(w, h) / 2
    for yy in range(h):
        for xx in range(w):
            d = math.hypot(xx - cx, yy - cy)
            c = SCLERA if rnd.random() > 0.12 else SCLERA_D
            if not front:
                # the eyeball's sides and back: sclera, red veins, a pale lid rim at the edge
                if xx == 0 or yy == 0 or xx == w - 1 or yy == h - 1:
                    c = LID_D if rnd.random() < 0.5 else LID
                elif rnd.random() < 0.03:
                    c = VEIN
                tex.set(x0 + xx, y0 + yy, c, 0.03)
                continue
            if d < rad * 0.62:
                c = IRIS_L if d > rad * 0.5 and yy < cy else IRIS_D if d > rad * 0.5 else IRIS
                if abs(xx - cx) < max(0.6, rad * 0.12) and abs(yy - cy) < rad * 0.5:
                    c = PUPIL
                else:
                    glow.set(x0 + xx, y0 + yy, GLOW_CORE if d < rad * 0.3 else GLOW_IRIS, 0.0)
            elif d > rad * 0.92 and rnd.random() < 0.12:
                c = VEIN
            tex.set(x0 + xx, y0 + yy, c, 0.03)


def paint(tex, glow):
    rnd = random.Random(11)
    for name, _, _, (w, h, d), (u, v) in CUBES:
        kind = KIND[name]
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            fw, fh = max(1, fw), max(1, fh)
            if kind == "eye":
                eye(tex, glow, (x0, y0, fw, fh), rnd, side == "front")
            elif kind in ("gold", "tip"):
                gold(tex, x0, y0, fw, fh, rnd)
            elif kind == "arm":
                for yy in range(fh):
                    for xx in range(fw):
                        c = FEATHER_L if yy == 0 else FEATHER if yy < fh - 1 else FEATHER_D
                        if yy == fh - 1 or yy == 0 and side in ("front", "back"):
                            c = GOLD
                        tex.set(x0 + xx, y0 + yy, c, 0.03)
            elif kind == "covert":  # small feathers over the roots: white, a gold edge at the bottom
                for yy in range(fh):
                    for xx in range(fw):
                        c = FEATHER_L if yy < fh - 1 else GOLD_L
                        if rnd.random() < 0.06:
                            c = FEATHER
                        tex.set(x0 + xx, y0 + yy, c, 0.02)
            else:  # a feather: cream, a darker shaft down the middle, soft barbs
                for yy in range(fh):
                    for xx in range(fw):
                        k_ = yy / max(1, fh - 1)
                        c = lerp(FEATHER_L, FEATHER_D, k_ * 0.55)
                        if fw > 2 and xx == fw // 2:
                            c = lerp(c, FEATHER_D, 0.5)
                        if rnd.random() < 0.08:
                            c = FEATHER_D
                        tex.set(x0 + xx, y0 + yy, c, 0.02)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    tex_path, glow_path = os.path.join(textures, "aurelion.png"), os.path.join(textures, "aurelion_glow.png")
    used = pack(CUBES, TEX)
    assert used <= TEX, f"texture too small: {used}"
    tex, glow = Tex(TEX, TEX, seed=3), Tex(TEX, TEX, seed=3)
    paint(tex, glow)
    tex.save(tex_path)
    glow.save(glow_path)
    scene, objs = build_scene(PARTS, CUBES, tex_path, TEX, TEX, "aurelion.blend")
    for name, start, seconds, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in ANIMS]
    scene["mc_preview_z"] = 24.0
    scene.frame_start = 0
    scene.frame_end = 300
    scene.frame_set(0)
    save(os.path.join(blends, "aurelion.blend"))
    print("saved rows", used, "parts", len(PARTS))


if __name__ == "__main__":
    main()
