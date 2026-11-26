"""
Builds the Hydra (boss.Hydra) in Blender: a great swamp beast, its heavy body sunk to the waist in the bog, heads on
long necks rising from its shoulders.

  body       a broad scaled body with plates down the back and a row of spines, a pale belly, four short clawed legs
             (under the water), a long tail of five segments ending in a spiked club
  necks      ten neck slots, each a base on the shoulders, four segments, a head and its jaw, and a stump (the cut end,
             shown when the head is cut off - client.HydraModel shows each slot as the boss says):
               0 fire     (its left, the first three heads: fire, acid, ice)   red scales, two horns curving back
               1 acid     (the middle, lower)                                  green scales, fins down the cheeks
               2 ice      (its right)                                          pale blue scales, crystal spikes
               3, 4       two fire heads where 0 grew back double
               5, 6       two acid heads where 1 grew back double
               7, 8       two ice heads where 2 grew back double
               9 the great head (the second phase): on a thick neck behind the others, black scales, a crown of
                 golden horns

One 256x256 texture in the Minecraft style (hydra.png) and its glow (hydra_glow.png): the eyes, the fire in the fire
heads' throats, the acid heads' glands, the ice heads' crystals, the great head's eyes and throat.

  blender -b --python tools/models/build_hydra.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/HydraModelData.java. Model space: the ground at y 24, the front -z, its
right -x; client.HydraRenderer draws it twice its pixels.

Animations (seconds; boss/Hydra.java is timed to them):
  sleep      4.0 loop  sunk: only the back above the water, the necks under it
  wake       5.0       the body rises (0-1.5), the heads come up one after another (fire 1.5, acid 2.3, ice 3.1),
                       all of them roar (4-5)
  idle       3.0 loop  the necks swaying, each in its own time, jaws snapping, the tail stirring
  walk       1.6 loop
  fire       2.0       the fire heads rear back (0-0.7), lunge and breathe at 0.8
  acid       1.6       the acid heads spit at 0.5, 0.8 and 1.1
  ice        1.8       the ice heads rear, breathe a line of frost at 0.9
  regrow_0   1.0       heads 3 and 4 burst out of the stump of 0 (likewise regrow_1: 5, 6 - regrow_2: 7, 8)
  flinch     0.8       the body recoils (a head cut off)
  transform  6.0       every head roars (0-1.5), it sinks under (1.5-2.5), comes up (4-5) with the great head, roars
  dive       1.0       it sinks under the water
  emerge     1.2       it bursts up, every head striking down at 0.6
  tail       1.6       raises the tail (0-0.8) and slams it down at 0.9 (the wave)
  breaths    3.0       fire, acid and ice breathe at once, turning (the body turned by the boss)
  maw        4.0       the great head opens wide and draws in (0.4-3.0), bites at 3.2
  roar       1.5
  death      5.0       the heads fall one after another (0-2.5), the body sinks (2.5-5)
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

# slot: element, yaw, base x, base z, thickness, segment length, head size (w, h, l)
SLOTS = [
    ("fire", 58, -8.5, -9.0),
    ("acid", 0, 0, -12.5),
    ("ice", -58, 8.5, -9.0),
    ("fire", 76, -9.5, -6.5), ("fire", 40, -6.5, -11.0),
    ("acid", 20, -3.2, -12.5), ("acid", -20, 3.2, -12.5),
    ("ice", -40, 6.5, -11.0), ("ice", -76, 9.5, -6.5),
    ("great", 0, 0, -5.0),
]
NECK_TILT = [48, -8, -22, -24]   # the four segments' rest tilts (an S, the head level at the top)


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def body():
    part("body", None, (0, 15, 0))
    box("body", (-10, -6, -13), (20, 11, 26), "scales")
    box("body", (-8.5, -7.5, -11), (17, 1.6, 22), "plates")
    box("body", (-9, 4.5, -11), (18, 1.6, 22), "belly")
    box("body", (-11, -4, -10), (1.2, 7, 20), "scales")
    box("body", (9.8, -4, -10), (1.2, 7, 20), "scales")
    box("body", (-7, -5, -15), (14, 8, 2.2), "scales")                       # the chest under the necks
    for i, z in enumerate((-9, -4.5, 0, 4.5, 9)):
        h = 3.2 if i in (1, 2, 3) else 2.4
        box("body", (-0.8, -7.5 - h, z - 0.8), (1.6, h, 1.6), "spine")
    for s, sx in (("l", 1), ("r", -1)):
        for f, z in (("f", -8), ("b", 8)):
            lg = f"leg_{f}{s}"
            part(lg, "body", (sx * 9.5, 3, z), (0, 0, sx * -25))
            box(lg, (-2, 0, -2.2), (4, 6, 4.4), "scales")
            box(lg, (-2.4, 5.4, -3.4), (4.8, 1.4, 5.4), "claw")
    # the tail: five segments, the last a spiked club
    parent, z = "body", 12.5
    for i, (w, ln) in enumerate(((7.5, 7), (6, 7), (4.8, 6.5), (3.6, 6), (5, 4))):
        tl = f"tail{i}"
        part(tl, parent, (0, -0.5 if i == 0 else 0, z), (6 if i == 0 else -4, 0, 0))
        box(tl, (-w / 2, -w / 2 * 0.85, 0), (w, w * 0.85, ln), "scales" if i < 4 else "club")
        if i < 4:
            box(tl, (-0.6, -w / 2 * 0.85 - 1.4, ln * 0.3), (1.2, 1.4, 1.2), "spine")
        else:
            for sx in (-1, 1):
                box(tl, (sx * 2.5 - 0.6, -0.6, 1.0), (1.2, 1.2, 1.2), "spine")
            box(tl, (-0.6, -3.2, 1.0), (1.2, 1.4, 1.2), "spine")
        parent, z = tl, ln - 0.3


def neck(i):
    el, yaw, bx, bz = SLOTS[i]
    great = el == "great"
    t = 6.6 if great else 4.8           # thickness
    ln = 6.5 if great else 6.0          # segment length
    base = f"nb{i}"
    part(base, "body", (bx, -5.5 if not great else -7, bz), (0, yaw, 0))
    parent = base
    tilts = NECK_TILT if not great else [24, -6, -12, -12]
    for k in range(4):
        seg = f"n{i}_{k}"
        part(seg, parent, (0, 0, 0) if k == 0 else (0, -ln, 0), (tilts[k], 0, 0))
        box(seg, (-t / 2, -ln - 0.3, -t / 2), (t, ln + 0.6, t), "neck_" + el)
        box(seg, (-t / 2 + 0.6, -ln + 0.6, -t / 2 - 0.4), (t - 1.2, ln - 1.2, 0.5), "throat_" + el)   # the paler throat
        box(seg, (-0.6, -ln + 1.0, t / 2 - 0.1), (1.2, 2.0, 1.0), "spine")
        if k == 0:   # the stump: the cut end of the neck, shown when the head is gone
            st = f"st{i}"
            part(st, seg, (0, -ln, 0))
            box(st, (-t / 2 - 0.3, -1.2, -t / 2 - 0.3), (t + 0.6, 1.6, t + 0.6), "stump")
        parent = seg
    # the head (pointing forward), the jaw under it, its element's ornaments
    hd, jw = f"h{i}", f"j{i}"
    s = 1.7 if great else 1.2
    part(hd, parent, (0, -ln, 0), (sum(-a for a in tilts) + 18, 0, 0))
    box(hd, (-3.5 * s, -3.2 * s, -4 * s), (7 * s, 4.4 * s, 6 * s), "head_" + el)          # the skull
    box(hd, (-2.6 * s, -2.6 * s, -9.6 * s), (5.2 * s, 3.2 * s, 5.8 * s), "head_" + el)    # the snout
    box(hd, (-2.0 * s, -3.0 * s, -10 * s), (4 * s, 0.6 * s, 1.2 * s), "nostril_" + el)
    box(hd, (-3.6 * s, -2.8 * s, -3.4 * s), (7.2 * s, 1.2 * s, 1.0 * s), "eye_" + el)     # the eyes across the brow
    part(jw, hd, (0, 1.0 * s, -1.5 * s), (8, 0, 0))
    box(jw, (-2.4 * s, 0, -7.8 * s), (4.8 * s, 1.4 * s, 7.6 * s), "jaw_" + el)
    box(jw, (-2.2 * s, -0.8 * s, -7.4 * s), (4.4 * s, 0.8 * s, 0.8 * s), "teeth")
    box(hd, (-2.2 * s, 0.1 * s, -9.4 * s), (4.4 * s, 0.6 * s, 0.6 * s), "teeth")
    if el == "fire":
        for sx in (-1, 1):
            hn = f"horn{i}{'l' if sx > 0 else 'r'}"
            part(hn, hd, (sx * 2.4, -3.0, 0.5), (-40, 0, sx * -15))
            box(hn, (-0.7, -4.5, -0.7), (1.4, 4.5, 1.4), "horn_fire")
            box(hn, (-0.5, -6.0, -0.2), (1.0, 1.6, 1.4), "horn_fire")
    elif el == "acid":
        for sx in (-1, 1):
            box(hd, (sx * 3.4 - (0.3 if sx > 0 else 0.0), -1.5, -2.0), (0.3, 3.4, 4.6), "fin")
            box(hd, (-0.3, -4.6, -3.0), (0.6, 1.6, 5.0), "fin")
    elif el == "ice":
        for (cx, cz, h) in ((-1.6, -1.5, 2.8), (1.6, -1.5, 2.8), (0, 0.4, 3.6), (0, -3.2, 2.2)):
            box(hd, (cx - 0.6, -3.2 - h, cz - 0.6), (1.2, h, 1.2), "crystal")
    else:
        for sx in (-1, 1):
            for j, (hx, hz, rot) in enumerate(((3.4, 0, -35), (4.8, -2.5, -15), (2.2, 1.6, -60))):
                hn = f"crown{j}{'l' if sx > 0 else 'r'}"
                part(hn, hd, (sx * hx, -4.6, hz), (rot, 0, sx * -20))
                box(hn, (-0.9, -6.0 + j, -0.9), (1.8, 6.0 - j, 1.8), "horn_great")


def model():
    body()
    for i in range(len(SLOTS)):
        neck(i)


# ---------------------------------------------------------------- painting
PAL = {
    "fire": ([(70, 16, 12), (104, 26, 18), (140, 40, 24), (176, 62, 32), (208, 92, 44)], [(196, 120, 48), (224, 156, 68), (244, 196, 104)]),
    "acid": ([(26, 54, 22), (38, 78, 30), (54, 104, 38), (74, 132, 46), (100, 160, 58)], [(150, 170, 70), (186, 200, 90), (216, 226, 124)]),
    "ice": ([(88, 120, 150), (116, 152, 184), (150, 184, 212), (184, 212, 232), (216, 236, 248)], [(200, 220, 236), (226, 238, 248), (244, 250, 255)]),
    "great": ([(14, 12, 20), (24, 20, 32), (36, 30, 46), (50, 42, 62), (68, 58, 82)], [(70, 50, 40), (96, 70, 52), (124, 92, 66)]),
    "body": ([(30, 44, 28), (42, 60, 36), (56, 78, 44), (72, 96, 54), (90, 116, 64)], [(126, 132, 82), (152, 158, 100), (178, 182, 120)]),
}
GLOW = {"fire": (255, 150, 40), "acid": (150, 255, 60), "ice": (150, 230, 255), "great": (255, 60, 40)}
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
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if g:
                        glow.set(x0 + x, y0 + y, g if isinstance(g, tuple) else c, 0.0)


def scale_px(pal, x, y, light, n, offset=0):
    """overlapping scales: rows of arcs, each scale lit at its top"""
    row = y // 2
    col = (x + (row % 2)) // 2
    top = y % 2 == 0
    edge = (x + (row % 2)) % 2 == 1 and not top
    level = 2.0 + light + n + (0.7 if top else 0) - (0.9 if edge else 0) + offset + ((col * 7 + row * 3) % 5 == 0) * 0.4
    return pick(pal, level)


def paint_px(mat, x, y, w, h, face, light, rnd):
    n = rnd.uniform(-0.3, 0.3)
    el = mat.split("_")[-1] if "_" in mat else None
    if mat in ("scales", "plates", "club"):
        pal, belly = PAL["body"]
        if mat == "plates" and face == "top":
            band = x % 4 == 0 or y % 5 == 0
            return pick(pal, 2.8 + n - (1.2 if band else 0)), False
        if mat == "club":
            return pick(pal, 1.2 + light + n), False
        if face == "bottom":
            return pick(belly, 1 + n), False
        # mossy streaks of the bog on the sides
        if face in ("left", "right", "front", "back") and y > h * 0.55 and (x * 3 + y) % 7 < 2:
            return (52, 66, 30), False
        return scale_px(pal, x, y, light, n), False
    if mat == "belly":
        return pick(PAL["body"][1], 1 + light * 0.5 + n + (0.6 if y % 3 == 0 else 0)), False
    if mat == "spine":
        return pick([(70, 60, 44), (110, 96, 70), (160, 146, 112), (200, 188, 150)], 1.8 + light + n), False
    if mat in ("claw", "teeth"):
        return pick([(150, 140, 112), (196, 188, 160), (230, 226, 206)], 1.2 + light + n), False
    if mat == "stump":
        # the cut: raw flesh in the middle, the scales round its edge
        if face == "top":
            cx, cy = w / 2 - 0.5, h / 2 - 0.5
            if math.hypot(x - cx, y - cy) < min(w, h) / 2 - 0.6:
                return pick([(90, 10, 14), (130, 20, 24), (170, 40, 40)], 1 + n * 2), False
            return pick([(40, 30, 30), (60, 46, 44)], 1), False
        return pick(PAL["body"][0], 1.4 + light + n), False
    if mat == "fin":
        return pick(PAL["acid"][1], 1 + light + n - (0.8 if x % 2 else 0)), (y % 3 == 1 and x % 2 == 0) and GLOW["acid"]
    if mat == "crystal":
        top = y < 2
        c = pick(PAL["ice"][1], 2 if top else 1)
        return c, (150, 230, 255) if top or x == 0 else False
    if mat in ("horn_fire", "horn_great"):
        r = [(60, 44, 30), (96, 72, 46), (140, 110, 70), (186, 156, 104)] if mat == "horn_fire" else \
            [(120, 84, 26), (170, 124, 42), (214, 170, 72), (244, 212, 130)]
        return pick(r, 1.4 + light + n + (y / max(1, h)) * -0.8), False
    if el in PAL:
        pal, belly = PAL[el]
        if mat.startswith("throat"):
            return pick(belly, 1 + light * 0.5 + n + (0.6 if y % 3 == 0 else 0)), False
        if mat.startswith("eye"):
            if face == "front" and y == 0 and x in (1, 2, w - 3, w - 2):
                return GLOW[el], True
            return pick(pal, 1.0 + light + n), False
        if mat.startswith("nostril"):
            if face == "front" and x in (0, w - 1):
                return (20, 12, 12), False
            return pick(pal, 1.8 + light + n), False
        if mat.startswith("jaw"):
            if face == "top":   # inside the mouth: the throat glowing with its element
                inner = 0 < x < w - 1 and y > 1
                if inner:
                    return pick([(80, 20, 24), (120, 30, 34)], 1), GLOW[el] if (x + y) % 3 == 0 else False
                return pick([(80, 20, 24), (120, 30, 34)], 0), False
            return scale_px(pal, x, y, light, n, -0.3), False
        if mat.startswith("head"):
            if face == "bottom":
                return pick([(80, 20, 24), (120, 30, 34)], 1), GLOW[el] if (x * 2 + y) % 4 == 0 else False
            return scale_px(pal, x, y, light, n, 0.2), False
        if mat.startswith("neck"):
            return scale_px(pal, x, y, light, n), False
    return pick(PAL["body"][0], 2 + light + n), False


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


SLOTS_OF = {"fire": [0, 3, 4], "acid": [1, 5, 6], "ice": [2, 7, 8], "great": [9]}
SMALL = list(range(9))


def neck_parts(i):
    return [f"nb{i}", f"n{i}_0", f"n{i}_1", f"n{i}_2", f"n{i}_3", f"h{i}", f"j{i}"]


BODY_PARTS = ["body", "leg_fl", "leg_fr", "leg_bl", "leg_br", "tail0", "tail1", "tail2", "tail3", "tail4"]
ALL = BODY_PARTS + [p for i in range(10) for p in neck_parts(i)]


def rest(t, parts=ALL, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


def neckpose(t, i, base=(0, 0, 0), segs=(0, 0, 0, 0), head=(0, 0, 0), jaw=0, pos=(0, 0, 0), linear=False):
    """a neck's pose: base turn, the four segments' extra tilt, the head's, the jaw open"""
    out = [k(t, f"nb{i}", base, pos, linear)]
    for kk in range(4):
        out.append(k(t, f"n{i}_{kk}", (segs[kk], 0, 0), None, linear))
    out.append(k(t, f"h{i}", head, None, linear))
    out.append(k(t, f"j{i}", (jaw, 0, 0), None, linear))
    return out


def necks(t, slots, linear=False, **kw):
    out = []
    for i in slots:
        out += neckpose(t, i, linear=linear, **kw)
    return out


SUNK = 10            # pixels the body sinks asleep
UNDER = dict(segs=(70, 60, 30, 0), head=(20, 0, 0), pos=(0, 6, 0))      # a neck curled down under the water
REAR = dict(segs=(-25, -10, -5, 0), head=(-20, 0, 0), jaw=10)           # drawn back to strike
LUNGE = dict(segs=(30, 20, 10, 0), head=(30, 0, 0), jaw=40)             # struck forward, the jaw wide
ROARP = dict(segs=(-15, -5, -10, 0), head=(-40, 0, 0), jaw=45)          # roaring at the sky


def animations():
    rnd = random.Random(21)

    # sleep
    sl = [k(0, "body", (0, 0, 0), (0, SUNK, 0)), k(4, "body", (0, 0, 0), (0, SUNK, 0)), k(2, "body", (1.5, 0, 0), (0, SUNK + 0.4, 0))]
    sl += necks(0, range(10), **UNDER) + necks(4.0, range(10), **UNDER)
    sl += [k(0, "tail0", (10, 0, 0)), k(2, "tail0", (10, 6, 0)), k(4, "tail0", (10, 0, 0))]
    anim("sleep", 0, 4.0, True, sl)

    # wake
    wk = [k(0, "body", (0, 0, 0), (0, SUNK, 0)), k(1.5, "body", (0, 0, 0), (0, 0, 0))]
    wk += necks(0, range(10), **UNDER)
    for i, t0 in ((0, 1.5), (1, 2.3), (2, 3.1)):
        wk += neckpose(t0, i, **UNDER) + neckpose(t0 + 0.4, i, segs=(-10, -15, -10, 0), head=(-30, 0, 0), jaw=30)
        wk += neckpose(t0 + 0.8, i)
    wk += necks(4.0, SMALL) + necks(4.4, SMALL, **ROARP) + necks(4.6, SMALL, **ROARP) + necks(5.0, SMALL)
    wk += necks(5.0, [9], **UNDER)
    wk += [k(4.4, "tail0", (-20, 0, 0)), k(5.0, "tail0", (0, 0, 0))]
    anim("wake", 120, 5.0, False, wk)

    # idle: every neck swaying in its own time
    idl = rest(0)
    for i in range(10):
        ph = rnd.uniform(0, 3)
        for t in (0.75, 1.5, 2.25):
            a = math.sin((t + ph) * 2.1)
            idl += neckpose(t, i, base=(0, a * 6, 0), segs=(a * 4, -a * 4, a * 5, 0), head=(-a * 6, a * 8, 0),
                            jaw=12 if (i + int(t * 4)) % 5 == 0 else 0)
    idl += [k(1.5, "body", (1.0, 0, 0), (0, 0.4, 0)), k(1.5, "tail0", (0, 10, 0)), k(1.5, "tail2", (0, 12, 0))]
    idl += rest(3.0)
    anim("idle", 240, 3.0, True, idl)

    # walk: wading
    wl = []
    for t, a in ((0, 1), (0.4, 0), (0.8, -1), (1.2, 0), (1.6, 1)):
        wl += [k(t, "leg_fl", (30 * a, 0, 0)), k(t, "leg_br", (30 * a, 0, 0)), k(t, "leg_fr", (-30 * a, 0, 0)),
               k(t, "leg_bl", (-30 * a, 0, 0)), k(t, "body", (0, 0, 2 * a), (0, -0.6 if a == 0 else 0, 0)),
               k(t, "tail0", (0, 12 * a, 0)), k(t, "tail1", (0, 10 * a, 0)), k(t, "tail2", (0, 8 * a, 0))]
    anim("walk", 360, 1.6, True, wl)

    # fire: rear back, breathe
    fi = rest(0) + necks(0.7, SLOTS_OF["fire"], **REAR) + necks(0.8, SLOTS_OF["fire"], linear=True, **LUNGE)
    fi += necks(1.5, SLOTS_OF["fire"], **LUNGE) + rest(2.0)
    anim("fire", 480, 2.0, False, fi)

    # acid: three spits
    ac = rest(0)
    for t in (0.5, 0.8, 1.1):
        ac += necks(t - 0.15, SLOTS_OF["acid"], **REAR) + necks(t, SLOTS_OF["acid"], linear=True,
                                                               segs=(15, 10, 5, 0), head=(15, 0, 0), jaw=35)
    ac += rest(1.6)
    anim("acid", 600, 1.6, False, ac)

    # ice
    ic = rest(0) + necks(0.8, SLOTS_OF["ice"], **REAR) + necks(0.9, SLOTS_OF["ice"], linear=True, **LUNGE)
    ic += necks(1.4, SLOTS_OF["ice"], **LUNGE) + rest(1.8)
    anim("ice", 720, 1.8, False, ic)

    # regrowth: the new heads burst out
    for g, (a, b) in enumerate(((3, 4), (5, 6), (7, 8))):
        rg = necks(0, [a, b], segs=(100, 90, 60, 0), head=(40, 0, 0), pos=(0, 8, 0))
        rg += necks(0.5, [a, b], segs=(-20, -10, -10, 0), head=(-40, 0, 0), jaw=40, linear=True)
        rg += necks(1.0, [a, b])
        anim(f"regrow_{g}", 840 + g * 30, 1.0, False, rg)

    # flinch: a head cut off
    fl = rest(0, BODY_PARTS) + [k(0.2, "body", (-6, 0, 3), (0, -1, 1)), k(0.8, "body", (0, 0, 0), (0, 0, 0))]
    anim("flinch", 960, 0.8, False, fl)

    # transform
    tf = rest(0) + necks(0.6, SMALL, **ROARP) + necks(1.3, SMALL, **ROARP) + necks(1.5, SMALL)
    tf += necks(0, [9], **UNDER) + necks(1.5, [9], **UNDER)
    tf += [k(1.5, "body", (0, 0, 0), (0, 0, 0)), k(2.5, "body", (0, 0, 0), (0, 22, 0)), k(4.0, "body", (0, 0, 0), (0, 22, 0)),
           k(5.0, "body", (0, 0, 0), (0, 0, 0))]
    tf += necks(2.5, range(10), **UNDER) + necks(4.0, range(10), **UNDER)
    tf += necks(4.8, range(10), segs=(-10, -15, -10, 0), head=(-30, 0, 0), jaw=30) + necks(5.3, range(10), **ROARP)
    tf += necks(5.6, range(10), **ROARP) + necks(6.0, range(10))
    anim("transform", 1000, 6.0, False, tf)

    # dive and emerge
    dv = rest(0) + [k(1.0, "body", (8, 0, 0), (0, 24, 0))] + necks(1.0, range(10), **UNDER)
    anim("dive", 1130, 1.0, False, dv)
    em = [k(0, "body", (0, 0, 0), (0, 24, 0)), k(0.4, "body", (-8, 0, 0), (0, -2, 0), True), k(0.8, "body", (0, 0, 0), (0, 0, 0))]
    em += necks(0, range(10), **UNDER) + necks(0.45, range(10), **REAR) + necks(0.6, range(10), linear=True, **LUNGE)
    em += necks(1.2, range(10))
    anim("emerge", 1160, 1.2, False, em)

    # tail slam
    tl = rest(0) + [k(0.7, "tail0", (-35, 0, 0)), k(0.7, "tail1", (-25, 0, 0)), k(0.7, "tail2", (-20, 0, 0)), k(0.7, "tail3", (-15, 0, 0)),
                    k(0.7, "body", (6, 0, 0), (0, -1, 0)),
                    k(0.9, "tail0", (25, 0, 0), None, True), k(0.9, "tail1", (15, 0, 0), None, True), k(0.9, "tail2", (10, 0, 0), None, True),
                    k(0.9, "tail3", (10, 0, 0), None, True), k(0.9, "body", (-4, 0, 0), (0, 0.5, 0), True)]
    tl += rest(1.6)
    anim("tail", 1200, 1.6, False, tl)

    # three breaths at once
    br = rest(0) + necks(0.5, SMALL, **REAR) + necks(0.7, SMALL, linear=True, **LUNGE)
    for t in (1.2, 1.7, 2.2, 2.7):
        br += necks(t, SMALL, segs=(30, 20, 10, 0), head=(30, rnd.uniform(-6, 6), 0), jaw=40)
    br += rest(3.0)
    anim("breaths", 1240, 3.0, False, br)

    # the maw: the great head draws in, then bites
    mw = rest(0) + neckpose(0.4, 9, segs=(-10, -10, -5, 0), head=(-15, 0, 0), jaw=55)
    for t in (1.0, 1.6, 2.2, 2.8):
        mw += neckpose(t, 9, segs=(-12, -10, -5, 0), head=(-15, rnd.uniform(-4, 4), 0), jaw=58 + rnd.uniform(-4, 4))
    mw += neckpose(3.0, 9, **REAR) + neckpose(3.2, 9, linear=True, segs=(35, 25, 10, 0), head=(25, 0, 0), jaw=0)
    mw += neckpose(3.6, 9, segs=(30, 20, 10, 0), head=(20, 0, 0)) + rest(4.0)
    anim("maw", 1310, 4.0, False, mw)

    # roar
    ro = rest(0) + necks(0.4, range(10), **ROARP)
    for t in (0.7, 1.0):
        ro += necks(t, range(10), segs=(-15, -5, -10, 0), head=(-40, rnd.uniform(-8, 8), 0), jaw=40 + rnd.uniform(-6, 6))
    ro += rest(1.5)
    anim("roar", 1400, 1.5, False, ro)

    # death: the heads fall one after another, the body sinks
    de = rest(0) + necks(0.3, range(10), **ROARP)
    for n, i in enumerate((0, 3, 5, 1, 7, 4, 6, 2, 8, 9)):
        t0 = 0.5 + n * 0.22
        de += neckpose(t0, i, **ROARP) + neckpose(t0 + 0.45, i, segs=(60, 40, 30, 20), head=(40, 0, 0), jaw=30, base=(0, 0, 0))
        de += neckpose(5.0, i, segs=(60, 40, 30, 20), head=(40, 0, 0), jaw=30, pos=(0, 12, 0))
    de += [k(2.6, "body", (0, 0, 0), (0, 0, 0)), k(3.2, "body", (4, 0, 8), (0, 4, 0)), k(5.0, "body", (10, 0, 14), (0, 22, 0))]
    anim("death", 1440, 5.0, False, de)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, glow = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
    paint(tex, glow, random.Random(37))
    tex.save(os.path.join(tex_dir, "hydra.png"))
    glow.save(os.path.join(tex_dir, "hydra_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "hydra.png"), TEX_W, TEX_H, "hydra")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved hydra parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
