"""
Builds the Golem boss in Blender: a hulking deepslate colossus - a small head sunk between huge mossy shoulders,
dark blue crystals growing out of its back, shoulders, forearms and head, a dark blue glowing core in the chest, long
arms that rest on boulder fists, short thick legs and a few rock shards floating around it. Quarter size (about 32
pixels tall); GolemRenderer draws it four times as big, about 8 blocks. Two 128x128 textures: the stone, and the glow
(eyes, core, runes, a faint light in the crystals) drawn on top unlit.

  blender -b --python tools/models/build_golem.py -- <out.blend> <texture.png> <glow.png>

then tools/models/export_model.py makes client/GolemModelData.java from the .blend (see mcmodel.py).

Animations (they must match the timings in boss/Golem.java):
  sleep  4.0 s loop   a heap of stones half sunk in the ground
  wake   4.0 s        the heap rumbles (0.4-1 s), the stones lift (1.2-2 s), float apart (2.6 s), slam together
                      (3.25 s); it rears up and drives its fists down (3.75 s)
  idle   4.0 s loop   heavy breathing, shards drifting
  walk   2.0 s loop   (driven by the walk speed) knuckle walking
  spin   2.6 s        arms swing out, the torso turns round twice with them (hits at 1.25 s and 2.0 s)
  throw  2.5 s        tears a boulder from the ground, lifts it overhead, throws it at 1.5 s
  spikes 2.0 s        raises both fists and drives them into the ground at 0.9 s
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 128, 128

# part: name, parent, pivot (relative to the parent's pivot, model space), rest rotation (degrees)
PARTS = [
    ("body", None, (0, 16, 0), (0, 0, 0)),
    ("torso", "body", (0, -3, 0), (8, 0, 0)),
    ("core", "torso", (0, -7, -5.5), (0, 0, 0)),
    ("head", "torso", (0, -11, -2), (-8, 0, 0)),
    ("arm_right", "torso", (-10, -8, 0), (-5, 0, 8)),
    ("forearm_right", "arm_right", (-1, 8, 0), (-12, 0, -4)),
    ("arm_left", "torso", (10, -8, 0), (-5, 0, -8)),
    ("forearm_left", "arm_left", (1, 8, 0), (-12, 0, 4)),
    ("leg_right", None, (-4.5, 16, 0), (0, 0, 0)),
    ("leg_left", None, (4.5, 16, 0), (0, 0, 0)),
    ("shard_a", "torso", (-6, -15, 5), (20, 30, 10)),
    ("shard_b", "torso", (7, -14, 4), (-15, -20, 25)),
    ("shard_c", "torso", (0, -16, 6), (35, 10, -20)),
    # dark blue crystals growing out of the stone
    ("crystal_back_a", "torso", (-4, -12, 3), (-30, 0, -22)),
    ("crystal_back_b", "torso", (3, -12, 2.5), (-25, 0, 25)),
    ("crystal_back_c", "torso", (0, -9, 5), (-60, 0, 0)),
    ("crystal_shoulder_r", "arm_right", (-2, -4, -1), (0, 0, 32)),
    ("crystal_shoulder_r2", "arm_right", (0, -4, 2.5), (-35, 0, 14)),
    ("crystal_shoulder_l", "arm_left", (2, -4, -1), (0, 0, -32)),
    ("crystal_shoulder_l2", "arm_left", (0, -4, 2.5), (-35, 0, -14)),
    ("crystal_forearm_r", "forearm_right", (-4, 4, 0), (0, 0, 70)),
    ("crystal_forearm_l", "forearm_left", (4, 4, 0), (0, 0, -70)),
    ("crystal_head", "head", (1.5, -5, 0), (-15, 0, -20)),
]

# cube: name, part, origin (relative to the pivot), size
CUBES = [[*c, None] for c in [
    ("pelvis", "body", (-6, -3, -4), (12, 4, 8)),
    ("chest", "torso", (-9, -11, -5.5), (18, 11, 11)),
    ("belly", "torso", (-6, -3, -5), (12, 4, 9)),
    ("back_rock", "torso", (-5, -13, 1), (10, 4, 5)),
    ("core", "core", (-2, -2, -1), (4, 4, 2)),
    ("head", "head", (-3.5, -5, -5), (7, 5, 7)),
    ("brow", "head", (-4, -5.5, -5.5), (8, 2, 2)),
    ("jaw", "head", (-3, -1, -5.5), (6, 2, 5)),
    ("shoulder_right", "arm_right", (-5, -4, -4.5), (8, 5, 9)),
    ("upper_arm_right", "arm_right", (-4, -1, -3.5), (6, 10, 7)),
    ("forearm_right", "forearm_right", (-4, 0, -4), (8, 9, 8)),
    ("fist_right", "forearm_right", (-4.5, 8, -4.5), (9, 3, 9)),
    ("shoulder_left", "arm_left", (-3, -4, -4.5), (8, 5, 9)),
    ("upper_arm_left", "arm_left", (-2, -1, -3.5), (6, 10, 7)),
    ("forearm_left", "forearm_left", (-4, 0, -4), (8, 9, 8)),
    ("fist_left", "forearm_left", (-4.5, 8, -4.5), (9, 3, 9)),
    ("leg_right", "leg_right", (-3, 0, -3.5), (6, 7, 7)),
    ("foot_right", "leg_right", (-3.5, 6, -4.5), (7, 2, 8)),
    ("leg_left", "leg_left", (-3, 0, -3.5), (6, 7, 7)),
    ("foot_left", "leg_left", (-3.5, 6, -4.5), (7, 2, 8)),
    ("shard_a", "shard_a", (-1.5, -1.5, -1.5), (3, 3, 3)),
    ("shard_b", "shard_b", (-1, -1.5, -1), (2, 3, 2)),
    ("shard_c", "shard_c", (-1, -1, -1), (2, 2, 2)),
    ("crystal_back_a", "crystal_back_a", (-1, -6, -1), (2, 6, 2)),
    ("crystal_back_a_tip", "crystal_back_a", (-0.5, -8, -0.5), (1, 2, 1)),
    ("crystal_back_b", "crystal_back_b", (-1.5, -8, -1.5), (3, 8, 3)),
    ("crystal_back_b_tip", "crystal_back_b", (-1, -10, -1), (2, 2, 2)),
    ("crystal_back_c", "crystal_back_c", (-1, -5, -1), (2, 5, 2)),
    ("crystal_back_c_tip", "crystal_back_c", (-0.5, -6.5, -0.5), (1, 1.5, 1)),
    ("crystal_shoulder_r", "crystal_shoulder_r", (-1, -5, -1), (2, 5, 2)),
    ("crystal_shoulder_r_tip", "crystal_shoulder_r", (-0.5, -6.5, -0.5), (1, 1.5, 1)),
    ("crystal_shoulder_r2", "crystal_shoulder_r2", (-1, -4, -1), (2, 4, 2)),
    ("crystal_shoulder_l", "crystal_shoulder_l", (-1, -5, -1), (2, 5, 2)),
    ("crystal_shoulder_l_tip", "crystal_shoulder_l", (-0.5, -6.5, -0.5), (1, 1.5, 1)),
    ("crystal_shoulder_l2", "crystal_shoulder_l2", (-1, -4, -1), (2, 4, 2)),
    ("crystal_forearm_r", "crystal_forearm_r", (-1, -4, -1), (2, 4, 2)),
    ("crystal_forearm_l", "crystal_forearm_l", (-1, -4, -1), (2, 4, 2)),
    ("crystal_head", "crystal_head", (-0.5, -3, -0.5), (1, 3, 1)),
]]

# ---------------------------------------------------------------- the textures
# deepslate: dark grey, a little blue, in thin layers
STONE = (76, 76, 84)
STONE_D = (56, 56, 63)
STONE_L = (100, 100, 108)
MOSS = (74, 100, 50)
MOSS_D = (54, 76, 38)
CRACK = (36, 36, 42)
GLOW = (40, 74, 210)    # dark blue light: eyes, core, runes
GLOW_L = (110, 150, 255)
CORE = (30, 56, 180)
DULL = (26, 36, 74)     # the runes and the core unlit, in the stone texture
DULL_L = (42, 58, 112)
CRYSTAL = (22, 36, 104)
CRYSTAL_L = (46, 70, 160)
CRYSTAL_D = (12, 18, 60)


def paint(tex, glow, cube):
    rnd = random.Random(5)

    def f(name):
        _, _, _, (w, h, d), (u, v) = cube[name]
        return faces(u, v, w, h, d)

    # stone everywhere: blotchy, lighter edges at the top, a few dark cracks
    for name, c in cube.items():
        for k, r in f(name).items():
            x0, y0, w, h = (int(round(n)) for n in r)
            for yy in range(h):
                for xx in range(w):
                    n = rnd.random()
                    col = STONE_D if n < 0.09 else STONE_L if n > 0.93 else STONE
                    if k in ("front", "back", "left", "right"):
                        if yy % 4 == 3 and n < 0.8:
                            col = STONE_D  # the layers of deepslate
                        if yy == 0:
                            col = STONE_L
                    tex.set(x0 + xx, y0 + yy, col, 0.07)
            # cracks: short jagged lines
            if w >= 4 and h >= 4:
                for _ in range(max(1, w * h // 40)):
                    x, y = rnd.randrange(w), rnd.randrange(h)
                    for _ in range(rnd.randint(2, 5)):
                        tex.set(x0 + x, y0 + y, CRACK, 0.05)
                        x = max(0, min(w - 1, x + rnd.choice((-1, 0, 1))))
                        y = max(0, min(h - 1, y + 1))

    # moss on top of the shoulders, head, back and the tops of the fists, dripping down the sides
    for name in ("shoulder_right", "shoulder_left", "head", "back_rock"):
        fc = f(name)
        tex.fill(fc["top"], MOSS, 0.15)
        for k in ("front", "back", "left", "right"):
            x0, y0, w, h = (int(round(n)) for n in fc[k])
            for xx in range(w):
                drip = rnd.choice((0, 0, 1, 1, 2))
                for yy in range(min(drip, h)):
                    tex.set(x0 + xx, y0 + yy, MOSS_D if yy else MOSS, 0.12)

    # the crystals: dark blue, lighter towards the tip and on one edge, a faint light of their own
    for name in cube:
        if not name.startswith("crystal"):
            continue
        tip = name.endswith("tip")
        for k, r in f(name).items():
            x0, y0, w, h = (int(round(n)) for n in r)
            for yy in range(h):
                for xx in range(w):
                    col = CRYSTAL_L if tip or (xx == 0 and k in ("front", "left")) else CRYSTAL_D if xx == w - 1 else CRYSTAL
                    if k in ("front", "back", "left", "right") and yy < h // 3:
                        col = CRYSTAL_L
                    tex.set(x0 + xx, y0 + yy, col, 0.06)
                    glow.set(x0 + xx, y0 + yy, (14, 22, 70) if not tip else (34, 52, 150), 0.0)

    # eyes: two glowing slits under the brow
    hd = f("head")["front"]
    for col in (1, 2, 4, 5):
        tex.at(hd, col, 1, DULL, 0.0)
        glow.at(hd, col, 1, GLOW, 0.0)
    # the core: a crystal set into the chest
    for k, r in f("core").items():
        tex.fill(r, DULL, 0.1)
        glow.fill(r, CORE, 0.1)
    fr = f("core")["front"]
    for (c, w) in ((1, 1), (2, 1), (1, 2)):
        tex.at(fr, c, w, DULL_L, 0.0)
        glow.at(fr, c, w, GLOW_L, 0.0)
    # runes: glowing lines on the forearms and across the chest
    for name in ("forearm_right", "forearm_left"):
        for k in ("front", "left", "right"):
            x0, y0, w, h = (int(round(n)) for n in f(name)[k])
            mid = w // 2
            for yy in range(1, h - 1):
                tex.set(x0 + mid, y0 + yy, DULL, 0.05)
                glow.set(x0 + mid, y0 + yy, GLOW, 0.05)
            for (xx, yy) in ((mid - 1, 2), (mid + 1, h - 3)):
                tex.set(x0 + xx, y0 + yy, DULL, 0.05)
                glow.set(x0 + xx, y0 + yy, GLOW, 0.05)
    ch = f("chest")["front"]
    x0, y0, w, h = (int(round(n)) for n in ch)
    for xx in range(2, w - 2):
        if xx in (w // 2 - 3, w // 2 + 2):
            continue
        yy = 2 + abs(xx - w // 2) // 3
        tex.set(x0 + xx, y0 + yy, DULL, 0.05)
        glow.set(x0 + xx, y0 + yy, GLOW, 0.05)


# ---------------------------------------------------------------- animations
def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


ALL = [p[0] for p in PARTS]
MOVING = ["body", "torso", "head", "arm_right", "forearm_right", "arm_left", "forearm_left", "leg_right", "leg_left",
          "shard_a", "shard_b", "shard_c", "core"]

# a heap of stones on the ground (offsets from the rest pose): the torso a big boulder half sunk in the ground, the
# head tipped face down on top of it, the arms lying out to the sides as long rocks with the fists curled in, the
# legs sunk to stubs at its foot, the shards fallen around
HEAP = {
    "body": ((0, 0, 0), (0, 11, 0)),
    "torso": ((20, 25, 10), (0, 4, 0)),
    "head": ((100, 150, 20), (3, 4, 2)),
    "arm_right": ((30, 0, 40), (2, 6, 0)),
    "forearm_right": ((-70, 0, 0), (0, 0, 0)),
    "arm_left": ((25, 0, -45), (-2, 6, 1)),
    "forearm_left": ((-60, 0, 10), (0, 0, 0)),
    "leg_right": ((20, 15, 0), (0, 7, 0)),
    "leg_left": ((-15, -20, 0), (0, 7, 0)),
    "shard_a": ((30, 0, 40), (-6, 13.5, 4)),
    "shard_b": ((0, 40, 20), (6, 13, -6)),
    "shard_c": ((50, 0, 0), (2, 14.5, 4)),
    "core": ((0, 0, 0), (0, 0, 0)),
}

# the stones floating apart in the air, about to come together
APART = {
    "body": ((0, 0, 0), (0, -2, 0)),
    "torso": ((0, 30, 0), (0, -3, 0)),
    "head": ((30, -60, 20), (0, -6, -3)),
    "arm_right": ((-20, 0, 50), (-6, -1, 0)),
    "forearm_right": ((10, 0, -30), (-2, 3, 0)),
    "arm_left": ((-20, 0, -50), (6, -1, 0)),
    "forearm_left": ((10, 0, 30), (2, 3, 0)),
    "leg_right": ((20, 0, 10), (-3, -4, 0)),
    "leg_left": ((-20, 0, -10), (3, -4, 0)),
    "shard_a": ((0, 180, 0), (-4, -6, 2)),
    "shard_b": ((90, -180, 0), (4, -6, 2)),
    "shard_c": ((0, 90, 180), (0, -8, 2)),
    "core": ((0, 0, 0), (0, 0, -3)),
}


def scaled(table, rot=1.0, pos=1.0, lift=0.0):
    return {p: (tuple(v * rot for v in r), (o[0] * pos, o[1] * pos - lift, o[2] * pos)) for p, (r, o) in table.items()}


def pose(t, table, parts=None, jitter=0.0, rnd=None, linear=False):
    out = []
    for part in parts or table:
        rot, pos = table[part]
        if jitter and rnd:
            rot = tuple(v + rnd.uniform(-jitter, jitter) for v in rot)
        out.append(k(t, part, rot, pos, linear))
    return out


def rest(t, parts, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


rnd = random.Random(9)
anim("sleep", 0, 4.0, True, pose(0, HEAP) + pose(4.0, HEAP))

# waking (4 s; boss/Golem.java plays its sounds to this): the heap rumbles, the stones lift off and float apart,
# drift into place, slam together at 3.25 s, the golem rears up and drives its fists down at 3.75 s
wake = pose(0, HEAP)
for t in (0.4, 0.6, 0.8, 1.0):
    wake += pose(t, HEAP, jitter=3.0, rnd=rnd)
wake += pose(1.2, HEAP)
heap_lifted = {p: (r, (o[0], o[1] - 7, o[2])) for p, (r, o) in HEAP.items()}
wake += pose(2.0, scaled(heap_lifted, rot=0.6, pos=1.0))
wake += pose(2.6, APART)
wake += pose(3.0, scaled(APART, rot=0.35, pos=0.35))
wake += rest(3.25, MOVING, linear=True)
wake += [k(3.5, "torso", (-15, 0, 0), (0, 0, 0)), k(3.5, "arm_right", (-150, 0, 30), (0, 0, 0)),
         k(3.5, "arm_left", (-150, 0, -30), (0, 0, 0)), k(3.5, "head", (-25, 0, 0), (0, 0, 0)),
         k(3.5, "forearm_right", (-20, 0, 0), (0, 0, 0)), k(3.5, "forearm_left", (-20, 0, 0), (0, 0, 0))]
wake += [k(3.75, "torso", (25, 0, 0), (0, 0, 0), True), k(3.75, "arm_right", (-40, 0, 10), (0, 0, 0), True),
         k(3.75, "arm_left", (-40, 0, -10), (0, 0, 0), True), k(3.75, "head", (15, 0, 0), (0, 0, 0), True),
         k(3.75, "body", (0, 0, 0), (0, 1, 0), True)]
wake += rest(4.0, MOVING)
anim("wake", 100, 4.0, False, wake)

anim("idle", 200, 4.0, True, [
    k(0, "torso", (0, 0, 0)), k(2, "torso", (2, 0, 0)), k(4, "torso", (0, 0, 0)),
    k(0, "head", (0, 0, 0)), k(2, "head", (-2, 4, 0)), k(4, "head", (0, 0, 0)),
    k(0, "arm_right", (0, 0, 0)), k(2, "arm_right", (-2, 0, 2)), k(4, "arm_right", (0, 0, 0)),
    k(0, "arm_left", (0, 0, 0)), k(2, "arm_left", (-2, 0, -2)), k(4, "arm_left", (0, 0, 0)),
    k(0, "shard_a", (0, 0, 0), (0, 0, 0)), k(1.3, "shard_a", (0, 40, 0), (0, -1.5, 0)), k(2.7, "shard_a", (0, 80, 0), (0, 0.5, 0)),
    k(4, "shard_a", (0, 120, 0), (0, 0, 0)),
    k(0, "shard_b", (0, 0, 0), (0, 0, 0)), k(2, "shard_b", (0, -60, 0), (0, -1.5, 0)), k(4, "shard_b", (0, -120, 0), (0, 0, 0)),
    k(0, "shard_c", (0, 0, 0), (0, 0, 0)), k(1, "shard_c", (20, 0, 0), (0, 1, 0)), k(3, "shard_c", (-20, 0, 0), (0, -1, 0)),
    k(4, "shard_c", (0, 0, 0), (0, 0, 0)),
])

anim("walk", 300, 2.0, True, [
    k(0, "leg_right", (20, 0, 0)), k(1, "leg_right", (-20, 0, 0)), k(2, "leg_right", (20, 0, 0)),
    k(0, "leg_left", (-20, 0, 0)), k(1, "leg_left", (20, 0, 0)), k(2, "leg_left", (-20, 0, 0)),
    k(0, "arm_right", (-18, 0, 0)), k(1, "arm_right", (14, 0, 0)), k(2, "arm_right", (-18, 0, 0)),
    k(0, "arm_left", (14, 0, 0)), k(1, "arm_left", (-18, 0, 0)), k(2, "arm_left", (14, 0, 0)),
    k(0, "torso", (0, 0, 4)), k(1, "torso", (0, 0, -4)), k(2, "torso", (0, 0, 4)),
    k(0, "body", None, (0, 0, 0)), k(0.5, "body", None, (0, -0.7, 0)), k(1, "body", None, (0, 0, 0)),
    k(1.5, "body", None, (0, -0.7, 0)), k(2, "body", None, (0, 0, 0)),
])

spin_parts = ["torso", "arm_right", "arm_left", "forearm_right", "forearm_left", "head", "body", "leg_right", "leg_left"]
spin = rest(0, spin_parts)
spin += [k(0.8, "arm_right", (0, 0, 85)), k(0.8, "arm_left", (0, 0, -85)),
         k(0.8, "forearm_right", (12, 0, 4)), k(0.8, "forearm_left", (12, 0, -4)),
         k(0.8, "torso", (-6, 25, 0)), k(1.0, "torso", (-6, 40, 0)),  # winds up the other way
         k(0.8, "body", (0, 0, 0), (0, 1, 0)), k(0.8, "leg_right", (0, 0, 0), (0, 1, 0)), k(0.8, "leg_left", (0, 0, 0), (0, 1, 0))]
for i, t in enumerate((1.25, 1.5, 1.75, 2.0)):  # two turns, fast
    spin.append(k(t, "torso", (-6, 40 - 180 * (i + 1), 0), linear=True))
spin += [k(2.2, "arm_right", (0, 0, 85)), k(2.2, "arm_left", (0, 0, -85)), k(2.2, "body", (0, 0, 0), (0, 1, 0)),
         k(2.2, "leg_right", (0, 0, 0), (0, 1, 0)), k(2.2, "leg_left", (0, 0, 0), (0, 1, 0)),
         k(2.6, "torso", (0, -720, 0))]
spin += rest(2.6, [p for p in spin_parts if p != "torso"])
anim("spin", 400, 2.6, False, spin)

throw_parts = ["torso", "arm_right", "arm_left", "forearm_right", "forearm_left", "head", "body"]
anim("throw", 500, 2.5, False, rest(0, throw_parts) + [
    # reaches down with both hands and tears up a boulder
    k(0.6, "torso", (35, 0, 0)), k(0.6, "arm_right", (-40, 0, -15)), k(0.6, "arm_left", (-40, 0, 15)),
    k(0.6, "body", (0, 0, 0), (0, 1.5, 0)), k(0.6, "head", (15, 0, 0)),
    # lifts it high over the head
    k(1.2, "torso", (-15, 0, 0)), k(1.2, "arm_right", (-170, 0, -20)), k(1.2, "arm_left", (-170, 0, 20)),
    k(1.2, "forearm_right", (-20, 0, 0)), k(1.2, "forearm_left", (-20, 0, 0)), k(1.2, "body", (0, 0, 0), (0, -1, 0)),
    k(1.2, "head", (-25, 0, 0)), k(1.35, "torso", (-20, 0, 0)), k(1.35, "arm_right", (-178, 0, -20)),
    k(1.35, "arm_left", (-178, 0, 20)),
    # hurls it
    k(1.5, "torso", (25, 0, 0), linear=True), k(1.5, "arm_right", (-60, 0, -10), linear=True),
    k(1.5, "arm_left", (-60, 0, 10), linear=True), k(1.5, "forearm_right", (0, 0, 0)), k(1.5, "forearm_left", (0, 0, 0)),
    k(1.5, "body", (0, 0, 0), (0, 1, 0)), k(1.5, "head", (10, 0, 0)),
    k(1.9, "torso", (20, 0, 0)), k(1.9, "arm_right", (-50, 0, -10)), k(1.9, "arm_left", (-50, 0, 10)),
] + rest(2.5, throw_parts))

slam_parts = ["torso", "arm_right", "arm_left", "forearm_right", "forearm_left", "head", "body", "leg_right", "leg_left"]
anim("spikes", 600, 2.0, False, rest(0, slam_parts) + [
    k(0.7, "torso", (-18, 0, 0)), k(0.7, "arm_right", (-165, 0, -10)), k(0.7, "arm_left", (-165, 0, 10)),
    k(0.7, "forearm_right", (-25, 0, 0)), k(0.7, "forearm_left", (-25, 0, 0)), k(0.7, "head", (-20, 0, 0)),
    k(0.7, "body", (0, 0, 0), (0, -1, 0)),
    k(0.9, "torso", (40, 0, 0), linear=True), k(0.9, "arm_right", (-45, 0, -8), linear=True),
    k(0.9, "arm_left", (-45, 0, 8), linear=True), k(0.9, "forearm_right", (-5, 0, 0), linear=True),
    k(0.9, "forearm_left", (-5, 0, 0), linear=True), k(0.9, "head", (25, 0, 0), linear=True),
    k(0.9, "body", (0, 0, 0), (0, 3, 0), linear=True),
    k(0.9, "leg_right", (-30, 0, 0), (0, 3, 0), linear=True), k(0.9, "leg_left", (-30, 0, 0), (0, 3, 0), linear=True),
    k(1.4, "torso", (40, 0, 0)), k(1.4, "arm_right", (-45, 0, -8)), k(1.4, "arm_left", (-45, 0, 8)),
    k(1.4, "body", (0, 0, 0), (0, 3, 0)), k(1.4, "leg_right", (-30, 0, 0), (0, 3, 0)), k(1.4, "leg_left", (-30, 0, 0), (0, 3, 0)),
] + rest(2.0, slam_parts))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path, glow_path = argv[0], argv[1], argv[2]
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows needed"
    tex = Tex(TEX_W, TEX_H, seed=21)
    glow = Tex(TEX_W, TEX_H, seed=21)
    paint(tex, glow, {c[0]: c for c in CUBES})
    tex.save(tex_path)
    glow.save(glow_path)
    scene, parts = build_scene(PARTS, CUBES, tex_path, TEX_W, TEX_H, "golem")
    for name, start, end, loop, keys in ANIMS:
        animation(parts, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved", blend_path, tex_path, glow_path, "texture rows used:", used)


main()
