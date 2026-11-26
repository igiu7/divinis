"""
Builds Ignaroth (boss.Ignaroth) in Blender: a great red dragon, some twelve blocks from snout to tail.

  body     a deep scaled chest and belly, plates down the belly, a row of spines down the back
  neck     four segments rising from the chest, spines along it
  head     a long horned skull - two great horns swept back and smaller ones along the jaw - a hinged lower jaw with
           teeth, eyes with lids (shut asleep; the left one opens first), nostrils
  wings    each an arm of three bones (shoulder, forearm, the long fingers) and three sheets of membrane between them
  legs     four, each thigh, shin and a clawed foot
  tail     six segments tapering to a spade of bone

One 256x256 texture in the Minecraft style (ignaroth.png) and its glow (ignaroth_glow.png): the eyes, the fire in
his throat and between his belly plates, the veins of his wings.

  blender -b --python tools/models/build_ignaroth.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/IgnarothModelData.java. Model space: the ground at y 24, the front -z;
client.IgnarothRenderer draws it 2.2 times its pixels.

Animations (seconds; boss/Ignaroth.java is timed to them):
  sleep      4.0 loop  curled on top of his hoard (boss.DragonHoard): the tail wrapped round, the head on the forepaws, the wings folded, breathing
  wake       5.0       the left eye opens (0.5), then the right (1.5), he rises (1.6-3), rears and roars (3.4-4.8)
  idle       3.0 loop  breathing, the tail stirring, smoke from the nostrils (the boss)
  walk       1.6 loop
  breath     3.0       draws breath (0-0.8), breathes fire before him at 0.8 and sweeps the head round (1.2-2.6)
  tail       1.6       swings the tail round behind him at 0.9
  wings      1.8       rears up and beats his wings at 1.0
  transform  6.0       crouches (0-0.8), springs up (1.0) and climbs on beating wings (1-4), roars in the air (4.5-5.5)
  fly        1.2 loop  in the air, wings beating
  bomb       2.0       head down, breathing fire on the ground beneath him (0.3-1.8)
  meteors    2.0       rears in the air, roars at the sky at 1.0
  dive       1.6       folds his wings and dives (0-0.8, the ground hit at 0.8), beats back up (0.9-1.6)
  fall       1.0       struck down: tumbling, crashing to the ground at 0.6
  stunned    2.0 loop  sprawled on the ground, wings spread out, the head down
  recover    1.4       heaves himself up and springs into the air at 0.8
  roar       1.5
  death      6.0       falling, crashing at 1.0, writhing (1-3), one last breath of fire into the sky (3.5-4.5), the
                       head falls (5)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 256, 256
PARTS, CUBES, MATERIAL = [], [], {}
_n = [0]


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def model():
    part("body", None, (0, 11, 0))
    box("body", (-6, -5, -12), (12, 10, 20), "scales")
    box("body", (-5, 4.6, -11), (10, 1.6, 17), "belly")
    box("body", (-6.6, -3.5, -10), (1.0, 7, 15), "scales")
    box("body", (5.6, -3.5, -10), (1.0, 7, 15), "scales")
    box("body", (-5, -6, -13.5), (10, 9, 2), "chest")
    for i, z in enumerate((-10, -6, -2, 2, 6)):
        h = 3.6 if i in (1, 2) else 2.8
        box("body", (-0.8, -5 - h, z - 0.9), (1.6, h, 1.8), "spine")
    # the neck
    parent, piv = "body", (0, -3, -12.5)
    for k, tilt in enumerate((-35, -10, 5, 10)):
        nk = f"neck{k}"
        part(nk, parent, piv, (tilt, 0, 0))
        w = 5.4 - k * 0.4
        box(nk, (-w / 2, -w / 2, -5.6), (w, w, 6.0), "scales")
        box(nk, (-w / 2 + 0.6, w / 2 - 0.4, -5.4), (w - 1.2, 0.8, 5.6), "belly")
        box(nk, (-0.6, -w / 2 - 2.0, -3.6), (1.2, 2.0, 1.4), "spine")
        parent, piv = nk, (0, 0, -5.3)
    # the head
    part("head", parent, piv, (32, 0, 0))
    box("head", (-3.6, -3.2, -7.5), (7.2, 5.4, 8), "head")
    box("head", (-2.7, -2.4, -13.5), (5.4, 3.6, 6.2), "head")               # the snout
    box("head", (-2.2, -2.9, -13.8), (4.4, 0.8, 1.6), "nostrils")
    box("head", (-3.8, -3.6, -6.8), (7.6, 1.0, 4.0), "brow")
    box("head", (-2.4, 0.4, -13.2), (4.8, 0.7, 6.0), "teeth")
    for s, sx in (("l", 1), ("r", -1)):
        box("head", (sx * 3.4 - (0.5 if sx > 0 else 0.3), -2.4, -6.0), (0.8, 1.3, 1.8), "eye")
        lid = f"lid_{s}"
        part(lid, "head", (sx * 3.6, -2.6, -5.1))
        box(lid, (-0.6 if sx > 0 else -0.4, 0, -1.1), (1.0, 1.6, 2.2), "scales")
        horn, horn2 = f"horn_{s}", f"horn2_{s}"
        part(horn, "head", (sx * 2.4, -3.0, -1.0), (-35, sx * 15, sx * -15))
        box(horn, (-0.9, -0.9, 0), (1.8, 1.8, 6.5), "horn")
        part(horn2, horn, (0, 0, 6.2), (20, 0, 0))
        box(horn2, (-0.6, -0.6, 0), (1.2, 1.2, 4.5), "horn")
        for j in range(3):
            box("head", (sx * 3.7 - 0.3, -0.2 - j * 0.1, -2.5 - j * 2.2), (0.6, 1.2, 0.8), "horn")
    part("jaw", "head", (0, 1.6, -2.0), (6, 0, 0))
    box("jaw", (-2.6, 0, -11.2), (5.2, 1.8, 11), "jaw")
    box("jaw", (-2.2, -0.6, -10.8), (4.4, 0.6, 9.4), "teeth")
    # the wings: shoulder, forearm, the long fingers, in a line at rest; one sheet of membrane behind them
    for s, sx in (("l", 1), ("r", -1)):
        w1, w2, w3 = f"wing_{s}", f"wing2_{s}", f"wing3_{s}"
        part(w1, "body", (sx * 5.0, -4.5, -7.0), (0, 0, sx * -10))
        x0 = 0 if sx > 0 else -12
        box(w1, (x0, -1.0, -1.0), (12, 2.0, 2.0), "wingbone")
        box(w1, (x0, -0.25, 1.0), (12, 0.5, 15), "membrane")
        part(w2, w1, (sx * 12, 0, 0))
        x0 = 0 if sx > 0 else -14
        box(w2, (x0, -0.8, -0.8), (14, 1.6, 1.6), "wingbone")
        box(w2, (x0, -0.25, 0.8), (14, 0.5, 17), "membrane")
        box(w2, ((-0.7 if sx > 0 else -0.7), -1.4, -2.2), (1.4, 1.4, 1.8), "horn")   # the claw at the joint
        part(w3, w2, (sx * 14, 0, 0))
        x0 = 0 if sx > 0 else -12
        box(w3, (x0, -0.6, -0.6), (12, 1.2, 1.2), "wingbone")
        box(w3, (x0, -0.25, 0.6), (12, 0.5, 13), "membrane")
        box(w3, ((11 if sx > 0 else -12.4), -0.25, 0.6), (1.4, 0.5, 6), "membrane")

    # the legs
    for s, sx in (("l", 1), ("r", -1)):
        for f, (z, thigh) in (("f", (-8.5, 6.5)), ("b", (5.5, 6.0))):
            up, low, foot = f"leg{f}_{s}", f"shin{f}_{s}", f"foot{f}_{s}"
            part(up, "body", (sx * 5.8, 1.5, z), (-10 if f == "f" else 20, 0, 0))
            box(up, (-2.0, -1, -2.3), (4.0, thigh + 1, 4.6), "scales")
            part(low, up, (0, thigh, 0), (25 if f == "f" else -35, 0, 0))
            box(low, (-1.5, 0, -1.6), (3.0, 5.5, 3.2), "scales")
            part(foot, low, (0, 5.2, 0), (-15 if f == "f" else 15, 0, 0))
            box(foot, (-2.0, 0, -3.6), (4.0, 1.6, 5.0), "scales")
            for c in (-1.4, 0, 1.4):
                box(foot, (c - 0.4, 0.4, -4.8), (0.8, 1.0, 1.4), "claw")
    # the tail
    parent, piv = "body", (0, -2, 7.5)
    for k in range(6):
        tl = f"tail{k}"
        part(tl, parent, piv, (12 if k == 0 else -3, 0, 0))
        w = 7.0 - k * 1.0
        box(tl, (-w / 2, -w / 2, 0), (w, w, 6.0), "scales")
        box(tl, (-0.5, -w / 2 - 1.6, 2), (1.0, 1.6, 1.4), "spine")
        parent, piv = tl, (0, 0, 5.8)
    part("tailtip", parent, piv)
    box("tailtip", (-2.6, -0.4, 0), (5.2, 0.8, 3.6), "horn")
    box("tailtip", (-1.4, -0.4, 3.4), (2.8, 0.8, 2.0), "horn")


# ---------------------------------------------------------------- painting
RED = [(70, 12, 10), (104, 20, 16), (140, 30, 22), (176, 46, 30), (206, 72, 40)]
BELLY = [(164, 110, 52), (196, 140, 66), (224, 172, 88)]
HORN = [(52, 40, 34), (84, 68, 56), (122, 104, 86), (166, 148, 120)]
MEMBRANE = [(70, 16, 18), (100, 26, 24), (130, 40, 30)]
FIRE = (255, 170, 40)
FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.25, "bottom": -1.0}


def pick(r, level):
    return r[max(0, min(len(r) - 1, int(round(level))))]


def paint(tex, glow, rnd):
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            fw, fh = max(1, fw), max(1, fh)
            light = FACE_LIGHT[face]
            for y in range(fh):
                for x in range(fw):
                    c, g = paint_px(mat, x, y, fw, fh, face, light, rnd)
                    if c is None:
                        tex.set(x0 + x, y0 + y, None)
                    else:
                        tex.set(x0 + x, y0 + y, c, 0.0)
                    if g:
                        glow.set(x0 + x, y0 + y, g, 0.0)


def scales(x, y, light, n, offset=0):
    row = y // 2
    top = y % 2 == 0
    edge = (x + row % 2) % 2 == 1 and not top
    return pick(RED, 2.0 + light + n + (0.7 if top else 0) - (0.9 if edge else 0) + offset)


def paint_px(mat, x, y, w, h, face, light, rnd):
    n = rnd.uniform(-0.3, 0.3)
    if mat in ("scales", "head", "brow"):
        if face == "bottom" and mat == "scales":
            return pick(BELLY, 1 + n), None
        return scales(x, y, light, n, 0.2 if mat == "head" else 0), None
    if mat in ("belly", "chest"):
        plate = y % 3 == 2
        if plate and face in ("bottom", "front"):
            return (60, 20, 8), (FIRE if (x + y) % 4 == 0 else None)      # fire glowing between the plates
        return pick(BELLY, 1.2 + light * 0.5 + n), None
    if mat == "spine":
        return pick(HORN, 1.8 + light + n - y / max(1, h)), None
    if mat in ("horn", "claw"):
        return pick(HORN, 1.6 + light + n + (0.8 if y == 0 else 0)), None
    if mat == "teeth":
        return ((236, 228, 206) if x % 2 == 0 else (60, 20, 12)), None
    if mat == "jaw":
        if face == "top":   # inside the mouth: his fire
            inner = 0 < x < w - 1
            return ((120, 30, 10) if inner else (80, 20, 10)), (FIRE if inner and (x + y) % 3 == 0 else None)
        return scales(x, y, light, n, -0.2), None
    if mat == "nostrils":
        return ((30, 10, 6) if x in (0, w - 1) else pick(RED, 2 + light)), ((255, 120, 20) if x in (0, w - 1) else None)
    if mat == "eye":
        c = (255, 200, 40) if y == 0 or x == 0 else (230, 120, 20)
        if (x + y) % 3 == 1:
            c = (20, 6, 4)   # the slit
        return c, c
    if mat == "wingbone":
        return pick(RED, 1.4 + light + n), None
    if mat == "membrane":
        # thin membrane: holes near the trailing edge, darker veins with a faint glow
        if face in ("top", "bottom"):
            if y > h - 3 and (x * 3 + y) % 5 < y - h + 4:
                return None, None
            vein = x % 5 == 0 or y % 7 == 0
            if vein:
                return pick(RED, 1 + n), ((140, 40, 20) if (x + y) % 2 == 0 else None)
            return pick(MEMBRANE, 1 + n * 2 + light * 0.3), None
        return pick(MEMBRANE, 0.6), None
    return pick(RED, 2 + light + n), None


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


NECK = ["neck0", "neck1", "neck2", "neck3", "head", "jaw"]
TAIL = [f"tail{i}" for i in range(6)] + ["tailtip"]
WINGS = [f"{w}_{s}" for s in ("l", "r") for w in ("wing", "wing2", "wing3")]
LEGS = [f"{p}{f}_{s}" for s in ("l", "r") for f in ("f", "b") for p in ("leg", "shin", "foot")]
LIDS = ["lid_l", "lid_r"]
ALL = ["body"] + NECK + TAIL + WINGS + LEGS + LIDS


def rest(t, parts=ALL, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


def pose(t, d, linear=False):
    out = []
    for p, v in d.items():
        if isinstance(v[0], (tuple, list)):
            out.append(k(t, p, tuple(v[0]), tuple(v[1]), linear))
        else:
            out.append(k(t, p, tuple(v), (0, 0, 0), linear))
    return out


def side(d, flip=True):
    """a pose given for the left side, mirrored onto the right"""
    out = dict(d)
    for p, v in d.items():
        if p.endswith("_l"):
            r = p[:-2] + "_r"
            if isinstance(v[0], (tuple, list)):
                (a, b, c), pos = v
                out[r] = ((a, -b, -c), (-pos[0], pos[1], pos[2]))
            else:
                a, b, c = v
                out[r] = (a, -b, -c)
    return out


FOLDED = side({"wing_l": (0, -65, 15), "wing2_l": (0, 155, 0), "wing3_l": (0, -150, 0)})
HALF = side({"wing_l": (0, -40, 5), "wing2_l": (0, 110, 0), "wing3_l": (0, -100, 0)})
SPREAD = side({"wing_l": (0, -10, -10), "wing2_l": (0, 8, 0), "wing3_l": (0, -12, 0)})
UP = side({"wing_l": (0, -10, -55), "wing2_l": (0, 8, 25), "wing3_l": (0, -12, 20)})
DOWN = side({"wing_l": (0, -10, 35), "wing2_l": (0, 8, -15), "wing3_l": (0, -12, -15)})
SHUT = {"lid_l": ((0, 0, 0), (0, 0, 0)), "lid_r": ((0, 0, 0), (0, 0, 0))}
OPEN_L = {"lid_l": ((0, 0, 0), (0, -1.6, 0))}
OPEN_R = {"lid_r": ((0, 0, 0), (0, -1.6, 0))}
# curled asleep on the hoard
CURLED = {"body": ((0, 0, 0), (0, -5.5, 0)), "neck0": (10, 50, 0), "neck1": (10, 30, 0), "neck2": (10, 25, 0), "neck3": (5, 20, 0),
          "head": (-25, 25, 10), "jaw": (0, 0, 0),
          "tail0": (-12, -40, 0), "tail1": (3, -35, 0), "tail2": (3, -40, 0), "tail3": (3, -40, 0), "tail4": (3, -35, 0), "tail5": (3, -30, 0)}
CURLED.update(side({"legf_l": (-50, 0, 0), "shinf_l": (60, 0, 0), "footf_l": (-10, 0, 0),
                    "legb_l": (-60, 0, 0), "shinb_l": (80, 0, 0), "footb_l": (-20, 0, 0)}))
AIR = side({"legf_l": (40, 0, 0), "shinf_l": (30, 0, 0), "legb_l": (50, 0, 0), "shinb_l": (40, 0, 0)})
AIR.update({"tail0": (-8, 0, 0), "tail1": (3, 0, 0), "neck0": (15, 0, 0), "head": (-10, 0, 0)})


def flap(t0, period, beats):
    """wing beats from t0: up, down, ..."""
    out = []
    for i in range(beats):
        out += pose(t0 + i * period, UP) + pose(t0 + i * period + period / 2, DOWN)
    return out


def animations():
    rnd = random.Random(19)

    sl = pose(0, CURLED) + pose(0, FOLDED) + pose(0, SHUT)
    sl += pose(2.0, {"body": ((0, 0, 0), (0, -5.9, 0)), "neck3": (5, 22, 0)}) + pose(4.0, CURLED) + pose(4.0, FOLDED) + pose(4.0, SHUT)
    anim("sleep", 0, 4.0, True, sl)

    wk = pose(0, CURLED) + pose(0, FOLDED) + pose(0, SHUT)
    wk += pose(0.5, OPEN_L, linear=True) + pose(0.5, {"lid_r": ((0, 0, 0), (0, 0, 0))})
    wk += pose(1.5, OPEN_R) + pose(1.6, CURLED)
    wk += pose(3.0, {"body": ((0, 0, 0), (0, 0, 0))}) + rest(3.0, LEGS + TAIL + ["neck0", "neck1", "neck2", "neck3", "head"])
    wk += pose(3.0, HALF)
    rear = {"body": ((-25, 0, 0), (0, -3, 2)), "neck0": (-30, 0, 0), "neck1": (-10, 0, 0), "head": (-30, 0, 0), "jaw": (40, 0, 0)}
    rear.update(side({"legf_l": (-50, 0, 0), "shinf_l": (40, 0, 0), "legb_l": (20, 0, 0), "shinb_l": (-10, 0, 0)}))
    wk += pose(3.6, rear) + pose(3.6, SPREAD) + pose(4.4, rear) + pose(4.4, UP)
    wk += pose(4.8, {"jaw": (0, 0, 0)}) + rest(5.0, ["body", "neck0", "neck1", "head", "jaw"] + LEGS) + pose(5.0, HALF)
    wk += pose(5.0, OPEN_L) + pose(5.0, OPEN_R)
    anim("wake", 120, 5.0, False, wk)

    idl = rest(0) + pose(0, HALF) + pose(0, OPEN_L) + pose(0, OPEN_R)
    idl += [k(1.5, "body", (1.5, 0, 0), (0, -0.4, 0)), k(1.5, "neck0", (-4, 4, 0)), k(1.5, "head", (4, -8, 0)),
            k(1.0, "jaw", (8, 0, 0)), k(1.4, "jaw", (0, 0, 0)), k(1.5, "tail2", (0, 12, 0)), k(1.5, "tail4", (0, 15, 0))]
    idl += pose(1.5, side({"wing_l": (0, -36, 2)}))
    idl += rest(3.0) + pose(3.0, HALF) + pose(3.0, OPEN_L) + pose(3.0, OPEN_R)
    anim("idle", 240, 3.0, True, idl)

    wl = []
    for t, a in ((0, 1), (0.4, 0), (0.8, -1), (1.2, 0), (1.6, 1)):
        wl += pose(t, side({"legf_l": (25 * a, 0, 0), "legb_l": (-25 * a, 0, 0)}))
        wl += [k(t, "legf_r", (-25 * a, 0, 0)), k(t, "legb_r", (25 * a, 0, 0)), k(t, "body", (0, 0, 2 * a), (0, -0.6 if a == 0 else 0, 0)),
               k(t, "tail1", (0, 10 * a, 0)), k(t, "tail3", (0, 12 * a, 0)), k(t, "neck1", (0, -5 * a, 0))]
    anim("walk", 360, 1.6, True, wl)

    br = rest(0) + pose(0, HALF)
    br += pose(0.7, {"body": ((-10, 0, 0), (0, -1, 2)), "neck0": (-30, 0, 0), "neck1": (-15, 0, 0), "head": (-20, 0, 0), "jaw": (10, 0, 0)})
    br += pose(0.85, {"body": ((5, 0, 0), (0, 0, -1)), "neck0": (10, 0, 0), "neck1": (5, 0, 0), "head": (10, 0, 0), "jaw": (45, 0, 0)}, linear=True)
    for t, yaw in ((1.2, 0), (1.6, -30), (2.1, 30), (2.6, 30)):
        br += pose(t, {"neck0": (10, yaw * 0.4, 0), "neck1": (5, yaw * 0.3, 0), "neck2": (0, yaw * 0.3, 0), "head": (10, 0, 0), "jaw": (45, 0, 0)})
    br += rest(3.0, NECK + ["body"])
    anim("breath", 480, 3.0, False, br)

    tl = rest(0) + pose(0, HALF)
    tl += pose(0.6, {"body": ((0, 25, 0), (0, 0, 0)), "tail0": (0, -30, 0), "tail1": (0, -25, 0), "tail2": (0, -20, 0), "tail3": (0, -20, 0)})
    tl += pose(0.9, {"body": ((0, -35, 0), (0, 0, 0)), "tail0": (0, 40, 0), "tail1": (0, 35, 0), "tail2": (0, 30, 0), "tail3": (0, 30, 0),
                     "tail4": (0, 25, 0)}, linear=True)
    tl += pose(1.2, {"body": ((0, -30, 0), (0, 0, 0)), "tail2": (0, 20, 0)}) + rest(1.6, ["body"] + TAIL)
    anim("tail", 600, 1.6, False, tl)

    wg = rest(0) + pose(0, HALF)
    rear2 = {"body": ((-30, 0, 0), (0, -3, 2)), "neck0": (-20, 0, 0), "head": (-10, 0, 0)}
    rear2.update(side({"legf_l": (-60, 0, 0), "shinf_l": (50, 0, 0), "legb_l": (25, 0, 0), "shinb_l": (-15, 0, 0)}))
    wg += pose(0.7, rear2) + pose(0.7, UP) + pose(1.0, DOWN, linear=True) + pose(1.0, {"body": ((-20, 0, 0), (0, -2, 1))})
    wg += pose(1.3, SPREAD) + rest(1.8, ["body", "neck0", "head"] + LEGS) + pose(1.8, HALF)
    anim("wings", 720, 1.8, False, wg)

    tf = rest(0) + pose(0, HALF)
    crouch = {"body": ((5, 0, 0), (0, 3, 0))}
    crouch.update(side({"legf_l": (-30, 0, 0), "shinf_l": (50, 0, 0), "legb_l": (-30, 0, 0), "shinb_l": (50, 0, 0)}))
    tf += pose(0.8, crouch) + pose(0.8, UP)
    tf += pose(1.0, {"body": ((-15, 0, 0), (0, -4, 0))}, linear=True) + pose(1.0, DOWN, linear=True) + pose(1.0, AIR)
    tf += flap(1.2, 0.6, 5)
    tf += pose(4.4, {"body": ((-20, 0, 0), (0, 0, 0)), "neck0": (-35, 0, 0), "neck1": (-15, 0, 0), "head": (-30, 0, 0), "jaw": (45, 0, 0)})
    tf += pose(4.4, UP) + pose(4.8, DOWN) + pose(5.2, UP) + pose(5.6, DOWN)
    tf += pose(5.5, {"jaw": (45, 0, 0)}) + pose(6.0, {"body": ((0, 0, 0), (0, 0, 0)), "neck0": (15, 0, 0), "neck1": (0, 0, 0), "head": (-10, 0, 0),
                                                       "jaw": (0, 0, 0)}) + pose(6.0, AIR) + pose(6.0, SPREAD)
    anim("transform", 840, 6.0, False, tf)

    fl = pose(0, AIR) + pose(0, UP) + pose(0.6, DOWN) + pose(1.2, UP) + pose(1.2, AIR)
    fl += [k(0.3, "body", (0, 0, 0), (0, 0.8, 0)), k(0.9, "body", (0, 0, 0), (0, -0.8, 0)), k(0, "body", (0, 0, 0), (0, 0, 0)),
           k(1.2, "body", (0, 0, 0), (0, 0, 0)), k(0.6, "tail3", (0, 8, 0)), k(1.2, "tail3", (0, 0, 0)), k(0, "tail3", (0, 0, 0))]
    anim("fly", 980, 1.2, True, fl)

    bm = pose(0, AIR) + pose(0, SPREAD)
    bm += pose(0.3, {"neck0": (50, 0, 0), "neck1": (20, 0, 0), "head": (30, 0, 0), "jaw": (45, 0, 0)})
    bm += pose(1.8, {"neck0": (50, 0, 0), "neck1": (20, 0, 0), "head": (30, 0, 0), "jaw": (45, 0, 0)})
    bm += pose(1.0, side({"wing_l": (0, -10, -20)})) + pose(2.0, AIR) + pose(2.0, SPREAD) + rest(2.0, ["jaw", "neck1"])
    anim("bomb", 1010, 2.0, False, bm)

    mt = pose(0, AIR) + pose(0.6, {"body": ((-30, 0, 0), (0, 0, 0)), "neck0": (-40, 0, 0), "neck1": (-20, 0, 0), "head": (-30, 0, 0)})
    mt += pose(0.6, UP) + pose(1.0, {"jaw": (50, 0, 0), "head": (-40, 0, 0)}, linear=True) + pose(1.0, DOWN)
    mt += pose(1.5, {"jaw": (45, 0, 0)}) + pose(1.5, UP) + pose(2.0, AIR) + pose(2.0, DOWN) + rest(2.0, ["jaw"])
    mt += pose(2.0, {"body": ((0, 0, 0), (0, 0, 0))})
    anim("meteors", 1060, 2.0, False, mt)

    dv = pose(0, AIR) + pose(0, SPREAD)
    dive = {"body": ((50, 0, 0), (0, 0, 0)), "neck0": (20, 0, 0), "head": (10, 0, 0), "jaw": (40, 0, 0)}
    dv += pose(0.4, dive) + pose(0.4, FOLDED) + pose(0.8, dive) + pose(0.8, side({"legf_l": (-60, 0, 0), "legb_l": (-40, 0, 0)}))
    dv += pose(0.9, {"body": ((-20, 0, 0), (0, 0, 0)), "jaw": (0, 0, 0)}, linear=True) + pose(0.9, UP, linear=True)
    dv += pose(1.2, DOWN) + pose(1.6, UP) + pose(1.6, AIR) + pose(1.6, {"body": ((0, 0, 0), (0, 0, 0)), "neck0": (15, 0, 0), "head": (-10, 0, 0)})
    anim("dive", 1110, 1.6, False, dv)

    sprawl = {"body": ((5, 0, 12), (0, 4, 0)), "neck0": (30, 20, 0), "neck1": (20, 15, 0), "head": (20, 20, 25), "jaw": (15, 0, 0),
              "tail0": (-10, -20, 0), "tail2": (0, -15, 0)}
    sprawl.update(side({"wing_l": (0, -15, 30), "wing2_l": (0, 20, -10), "wing3_l": (0, -20, 0), "legf_l": (-70, 0, 20), "legb_l": (-60, 0, 20)}))
    fa = pose(0, AIR) + pose(0, SPREAD) + pose(0.3, {"body": ((-30, 0, 40), (0, -2, 0))}) + pose(0.3, UP)
    fa += pose(0.6, sprawl, linear=True) + pose(1.0, sprawl)
    anim("fall", 1160, 1.0, False, fa)

    st = pose(0, sprawl) + pose(1.0, dict(sprawl, head=(18, 24, 25), body=((5, 0, 12), (0, 4.4, 0)))) + pose(2.0, sprawl)
    st += [k(0.5, "jaw", (20, 0, 0)), k(1.5, "jaw", (10, 0, 0))]
    anim("stunned", 1190, 2.0, True, st)

    rc = pose(0, sprawl) + pose(0.5, crouch) + pose(0.5, HALF) + rest(0.5, ["neck0", "neck1", "head", "jaw", "tail0", "tail2"])
    rc += pose(0.8, {"body": ((-15, 0, 0), (0, -4, 0))}, linear=True) + pose(0.8, UP) + pose(1.1, DOWN) + pose(1.4, AIR) + pose(1.4, UP)
    rc += pose(1.4, {"body": ((0, 0, 0), (0, 0, 0))})
    anim("recover", 1240, 1.4, False, rc)

    ro = rest(0) + pose(0, HALF) + pose(0.4, rear) + pose(0.4, SPREAD)
    for t in (0.7, 1.0):
        ro += pose(t, {"head": (-30 + rnd.uniform(-5, 5), rnd.uniform(-6, 6), 0), "jaw": (40 + rnd.uniform(-4, 4), 0, 0)})
    ro += rest(1.5, ["body", "neck0", "neck1", "head", "jaw"] + LEGS) + pose(1.5, HALF)
    anim("roar", 1280, 1.5, False, ro)

    de = pose(0, AIR) + pose(0, SPREAD) + pose(0.5, {"body": ((-40, 0, 30), (0, -2, 0))}) + pose(0.5, UP)
    de += pose(1.0, sprawl, linear=True)
    for t in (1.4, 1.8, 2.2, 2.6, 3.0):
        de += pose(t, {"body": ((5 + rnd.uniform(-5, 5), 0, 12 + rnd.uniform(-8, 8)), (0, 4, 0)), "neck0": (rnd.uniform(-20, 30), rnd.uniform(-20, 20), 0),
                       "head": (rnd.uniform(-20, 20), rnd.uniform(-20, 20), 0), "jaw": (rnd.uniform(10, 40), 0, 0),
                       "tail1": (0, rnd.uniform(-30, 30), 0), "tail3": (0, rnd.uniform(-30, 30), 0)})
    de += pose(3.5, {"neck0": (-60, 0, 0), "neck1": (-30, 0, 0), "neck2": (-20, 0, 0), "head": (-40, 0, 0), "jaw": (50, 0, 0)})
    de += pose(4.5, {"neck0": (-55, 0, 0), "neck1": (-28, 0, 0), "neck2": (-18, 0, 0), "head": (-38, 0, 0), "jaw": (45, 0, 0)})
    de += pose(5.2, {"neck0": (40, 30, 0), "neck1": (30, 20, 0), "neck2": (20, 0, 0), "head": (30, 20, 30), "jaw": (20, 0, 0)}, linear=True)
    de += pose(6.0, {"neck0": (40, 30, 0), "neck1": (30, 20, 0), "neck2": (20, 0, 0), "head": (30, 20, 30), "jaw": (20, 0, 0),
                     "body": ((5, 0, 14), (0, 4.5, 0))}) + pose(6.0, side({"wing_l": (0, -15, 35), "wing2_l": (0, 20, -10)}))
    de += pose(6.0, {"lid_l": ((0, 0, 0), (0, 0, 0)), "lid_r": ((0, 0, 0), (0, 0, 0))}) + pose(5.4, OPEN_L) + pose(5.4, OPEN_R)
    anim("death", 1300, 6.0, False, de)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, glow = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
    paint(tex, glow, random.Random(43))
    tex.save(os.path.join(tex_dir, "ignaroth.png"))
    glow.save(os.path.join(tex_dir, "ignaroth_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "ignaroth.png"), TEX_W, TEX_H, "ignaroth")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved ignaroth parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
