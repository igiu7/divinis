"""
Builds the Minotaur boss in Blender: Minecraft-style cubes, the 128x128 texture and its animations.
The model is half size (about 31 pixels tall); MinotaurRenderer draws it twice as big, about 4 blocks.

  blender -b --python tools/models/build_minotaur.py -- <out.blend> <texture.png>

then tools/models/export_model.py makes client/MinotaurModelData.java from the .blend (see mcmodel.py).

Animations (they must match the timings in boss/Minotaur.java):
  sleep  4.0 s loop  sitting on the ground, asleep
  wake   3.0 s       stands up and roars
  idle   3.0 s loop  breathing
  walk   1.6 s loop  (driven by the walk speed)
  cleave 2.0 s       two-handed overhead axe blow in front, hits at 1.2 s
  stomp  2.25 s      jumps and slams the ground around him, hits at 1.5 s
  charge 3.0 s       paws the ground until 1.5 s, runs head down until 2.25 s
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402
from minotaur_axe import AXE, color as axe_color  # noqa: E402

TEX_W, TEX_H = 128, 128

# part: name, parent, pivot (relative to the parent's pivot, model space), rest rotation (degrees)
PARTS = [
    ("body", None, (0, 14, 0), (0, 0, 0)),
    ("tail", "body", (0, -2, 3.5), (20, 0, 0)),
    ("chest", "body", (0, -4, 0), (0, 0, 0)),
    ("head", "chest", (0, -8, -4), (0, 0, 0)),
    ("jaw", "head", (0, 1, -6), (0, 0, 0)),
    ("horn_right", "head", (-3.5, -4, -3.5), (0, -20, 0)),
    ("horn_left", "head", (3.5, -4, -3.5), (0, 20, 0)),
    ("ear_right", "head", (-3.5, -2, -2.5), (0, 0, -20)),
    ("ear_left", "head", (3.5, -2, -2.5), (0, 0, 20)),
    ("arm_right", "chest", (-9, -7, 0), (-10, 0, 10)),
    ("forearm_right", "arm_right", (-1.5, 7.5, 0), (-35, 0, 0)),
    ("axe", "forearm_right", (0, 6.5, 0), (0, 0, 0)),
    ("arm_left", "chest", (9, -7, 0), (-5, 0, -10)),
    ("forearm_left", "arm_left", (1.5, 7.5, 0), (-20, 0, 0)),
    ("leg_right", None, (-4, 14, 0), (0, 0, 0)),
    ("leg_left", None, (4, 14, 0), (0, 0, 0)),
]

# cube: name, part, origin (relative to the pivot), size, texture offset (None = packed automatically)
CUBES = [[*c, None] for c in [
    ("waist", "body", (-5, -4, -3.5), (10, 4, 7)),
    ("cloth_front", "body", (-3.5, -0.5, -4), (7, 7, 1)),
    ("cloth_back", "body", (-3.5, -0.5, 3), (7, 6, 1)),
    ("tail", "tail", (-0.5, 0, 0), (1, 7, 1)),
    ("tail_tuft", "tail", (-1, 7, -1), (2, 3, 2)),
    ("chest", "chest", (-8, -9, -5), (16, 9, 10)),
    ("mane", "chest", (-6, -11, -3), (12, 3, 7)),
    ("skull", "head", (-3.5, -6, -6), (7, 7, 7)),
    ("snout", "head", (-2.5, -3, -9), (5, 4, 3)),
    ("ring", "head", (-1.5, -0.5, -9.5), (3, 2, 1)),
    ("jaw", "jaw", (-2.5, 0, -3), (5, 2, 4)),
    ("horn_right", "horn_right", (-4, -1, -1), (4, 2, 2)),
    ("horn_right_tip", "horn_right", (-5, -5, -1), (2, 4, 2)),
    ("horn_left", "horn_left", (0, -1, -1), (4, 2, 2)),
    ("horn_left_tip", "horn_left", (3, -5, -1), (2, 4, 2)),
    ("ear_right", "ear_right", (-2, -0.5, -1), (2, 1, 2)),
    ("ear_left", "ear_left", (0, -0.5, -1), (2, 1, 2)),
    ("arm_right", "arm_right", (-4, -1, -2.5), (5, 9, 5)),
    ("pad_right", "arm_right", (-4.5, -2, -3), (6, 4, 6)),
    ("forearm_right", "forearm_right", (-3, 0, -3), (6, 8, 6)),
    ("arm_left", "arm_left", (-1, -1, -2.5), (5, 9, 5)),
    ("pad_left", "arm_left", (-1.5, -2, -3), (6, 4, 6)),
    ("forearm_left", "forearm_left", (-3, 0, -3), (6, 8, 6)),
    ("thigh_right", "leg_right", (-3, 0, -3), (6, 6, 6)),
    ("shin_right", "leg_right", (-2.5, 5, -2.5), (5, 5, 5)),
    ("thigh_left", "leg_left", (-3, 0, -3), (6, 6, 6)),
    ("shin_left", "leg_left", (-2.5, 5, -2.5), (5, 5, 5)),
]] + [["axe_" + name, "axe", origin, size, None] for name, origin, size, _ in AXE]

# ---------------------------------------------------------------- the texture
FUR = (92, 58, 38)
FUR_D = (62, 39, 26)
FUR_L = (124, 84, 56)
MUZZLE = (150, 112, 96)
MUZZLE_D = (112, 80, 68)
NOSTRIL = (46, 30, 28)
MOUTH = (110, 34, 34)
EYE = (236, 44, 30)
HORN = (228, 216, 184)
HORN_D = (168, 152, 122)
HORN_TIP = (82, 70, 58)
HOOF = (42, 34, 30)
METAL = (118, 120, 128)
METAL_D = (78, 80, 88)
METAL_L = (172, 174, 182)
LEATHER = (112, 46, 32)
LEATHER_D = (76, 30, 22)
GOLD = (228, 182, 62)


def paint(tex, cube):
    def f(name):
        _, _, _, (w, h, d), (u, v) = cube[name]
        return faces(u, v, w, h, d)

    def all_faces(name, color, noise=0.08):
        for r in f(name).values():
            tex.fill(r, color, noise)

    sides = ("front", "back", "left", "right")

    # fur everywhere first
    for name in ("waist", "tail", "chest", "skull", "ear_right", "ear_left", "arm_right", "arm_left",
                 "forearm_right", "forearm_left", "thigh_right", "thigh_left", "shin_right", "shin_left"):
        all_faces(name, FUR, 0.12)
    all_faces("mane", FUR_D, 0.18)
    all_faces("tail_tuft", FUR_D, 0.15)

    # chest: lighter breast, a leather strap across it with a gold buckle, a dark stripe down the back
    ch = f("chest")
    fr = ch["front"]
    for row in range(9):
        for col in range(16):
            if 3 <= col <= 12 and row >= 2 and abs(col - 7.5) < 6 - row * 0.3:
                tex.at(fr, col, row, FUR_L, 0.1)
    for row in range(9):
        col = 2 + int(row * 1.4)
        for dc in range(2):
            tex.at(fr, col + dc, row, LEATHER if dc else LEATHER_D)
    tex.at(fr, 7, 4, GOLD)
    tex.at(fr, 8, 4, GOLD)
    bk = ch["back"]
    for row in range(9):
        tex.at(bk, 7, row, FUR_D)
        tex.at(bk, 8, row, FUR_D)

    # waist: a wide belt
    for k in sides:
        r = f("waist")[k]
        for col in range(int(r[2])):
            tex.at(r, col, 1, LEATHER)
            tex.at(r, col, 2, LEATHER_D if col % 3 == 0 else LEATHER)
    tex.at(f("waist")["front"], 4, 1, GOLD)
    tex.at(f("waist")["front"], 5, 1, GOLD)
    tex.at(f("waist")["front"], 4, 2, GOLD)
    tex.at(f("waist")["front"], 5, 2, GOLD)

    # loincloth
    for name in ("cloth_front", "cloth_back"):
        all_faces(name, LEATHER, 0.08)
        for k in ("front", "back"):
            r = f(name)[k]
            for col in range(int(r[2])):
                tex.at(r, col, int(r[3]) - 1, GOLD if col % 2 == 0 else LEATHER_D)

    # head: dark brow, glowing red eyes, a lighter tuft on the forehead
    sk = f("skull")
    tex.fill(sk["top"], FUR_D, 0.15)
    fr = sk["front"]
    for col in range(7):
        tex.at(fr, col, 1, FUR_D)
    tex.at(fr, 3, 0, FUR_L)
    tex.at(fr, 2, 0, FUR_L)
    tex.at(fr, 4, 0, FUR_L)
    tex.at(fr, 1, 2, EYE, 0.0)
    tex.at(fr, 5, 2, EYE, 0.0)
    tex.at(fr, 0, 2, NOSTRIL)
    tex.at(fr, 6, 2, NOSTRIL)
    for k in ("left", "right"):
        r = sk[k]
        tex.at(r, 6 if k == "right" else 0, 2, EYE, 0.0)

    all_faces("snout", MUZZLE, 0.06)
    sn = f("snout")
    tex.at(sn["front"], 1, 2, NOSTRIL)
    tex.at(sn["front"], 3, 2, NOSTRIL)
    tex.at(sn["front"], 1, 1, MUZZLE_D)
    tex.at(sn["front"], 3, 1, MUZZLE_D)
    tex.fill(sn["bottom"], MOUTH)
    all_faces("jaw", MUZZLE_D, 0.06)
    tex.fill(f("jaw")["top"], MOUTH)
    all_faces("ring", GOLD, 0.05)

    for side in ("right", "left"):
        all_faces("horn_" + side, HORN, 0.05)
        all_faces("horn_" + side + "_tip", HORN, 0.05)
        tip = f("horn_" + side + "_tip")
        tex.fill(tip["top"], HORN_TIP)
        for k in sides:
            for col in range(2):
                tex.at(tip[k], col, 0, HORN_TIP)
                tex.at(tip[k], col, 1, HORN_D)
        tex.fill(f("ear_" + side)["bottom"], MUZZLE)

    # arms: steel shoulder pads with rivets, leather bracers, dark knuckles
    for side in ("right", "left"):
        pad = f("pad_" + side)
        for k, r in pad.items():
            tex.fill(r, METAL, 0.05)
        tex.fill(pad["top"], METAL_L, 0.05)
        for k in sides:
            r = pad[k]
            for col in range(6):
                tex.at(r, col, 3, METAL_D)
            tex.at(r, 1, 1, METAL_L)
            tex.at(r, 4, 1, METAL_L)
        fa = f("forearm_" + side)
        for k in sides:
            r = fa[k]
            for col in range(6):
                for row in (2, 3, 4, 5):
                    tex.at(r, col, row, LEATHER if row in (3, 4) else LEATHER_D)
                tex.at(r, col, 7, FUR_D)
            tex.at(r, 2, 3, METAL_L)
            tex.at(r, 3, 4, METAL_L)
        tex.fill(fa["bottom"], FUR_D)

    # legs: hooves
    for side in ("right", "left"):
        sh = f("shin_" + side)
        for k in sides:
            r = sh[k]
            for col in range(5):
                tex.at(r, col, 3, HOOF)
                tex.at(r, col, 4, HOOF)
                tex.at(r, col, 2, FUR_D)
        tex.fill(sh["bottom"], HOOF)
        tex.fill(f("thigh_" + side)["front"], FUR_L, 0.12)

    # the great axe (minotaur_axe.py): along each face's long side, rims darker
    for name, _, _, material in AXE:
        for r in f("axe_" + name).values():
            x0, y0, w, h = (int(round(n)) for n in r)
            for yy in range(h):
                for xx in range(w):
                    along, n_along, across, n_across = (xx, w, yy, h) if w >= h else (yy, h, xx, w)
                    rim = min(across, n_across - 1 - across)
                    tex.set(x0 + xx, y0 + yy, axe_color(material, along, rim, n_along), 0.05)


# ---------------------------------------------------------------- animations
def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


SLEEP_POSE = {
    "body": ((0, 0, 0), (0, 7, 0)),
    "leg_right": ((-85, 12, 0), (0, 7, 0)),
    "leg_left": ((-85, -12, 0), (0, 7, 0)),
    "chest": ((30, 0, 0), None),
    "head": ((20, 0, 0), None),
    "arm_right": ((-25, 0, 15), None),
    "arm_left": ((-25, 0, -15), None),
    "forearm_right": ((-20, 0, 0), None),
    "forearm_left": ((-20, 0, 0), None),
    "tail": ((55, 0, 0), None),
}


def sleep_keys(t, breath=0.0):
    out = []
    for part, (rot, pos) in SLEEP_POSE.items():
        r = list(rot)
        if part == "chest":
            r[0] += breath
        if part == "head":
            r[0] += breath * 1.5
        out.append(k(t, part, tuple(r), pos))
    return out


def rest_keys(t, parts, linear=False):
    out = []
    for part in parts:
        pos = (0, 0, 0) if part in ("body", "leg_right", "leg_left") else None
        out.append(k(t, part, (0, 0, 0), pos, linear))
    return out


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


anim("sleep", 0, 4.0, True, sleep_keys(0) + sleep_keys(2.0, 3) + sleep_keys(4.0))

wake_parts = list(SLEEP_POSE) + ["jaw"]
anim("wake", 100, 3.0, False, sleep_keys(0) + [k(0, "jaw", (0, 0, 0))] + [
    k(0.6, "chest", (22, 0, 0)), k(0.6, "head", (-10, 0, 0)),
    k(1.2, "body", (0, 0, 0), (0, 3, 0)), k(1.2, "leg_right", (-40, 6, 0), (0, 3, 0)),
    k(1.2, "leg_left", (-40, -6, 0), (0, 3, 0)), k(1.2, "chest", (15, 0, 0)),
    k(1.2, "arm_right", (-10, 0, 20)), k(1.2, "arm_left", (-10, 0, -20)),
    k(1.2, "forearm_right", (-10, 0, 0)), k(1.2, "forearm_left", (-10, 0, 0)), k(1.2, "tail", (20, 0, 0)),
    k(1.6, "body", (0, 0, 0), (0, 0, 0)), k(1.6, "leg_right", (0, 0, 0), (0, 0, 0)),
    k(1.6, "leg_left", (0, 0, 0), (0, 0, 0)), k(1.6, "chest", (-12, 0, 0)), k(1.6, "head", (-35, 0, 0)),
    k(1.6, "jaw", (35, 0, 0)), k(1.6, "arm_right", (-60, 0, 50)), k(1.6, "arm_left", (-60, 0, -50)),
    k(1.6, "forearm_right", (-30, 0, 0)), k(1.6, "forearm_left", (-30, 0, 0)), k(1.6, "tail", (-10, 0, 0)),
    k(2.0, "head", (-30, 6, 0)), k(2.2, "head", (-34, -6, 0)), k(2.4, "head", (-30, 4, 0)),
    k(2.4, "jaw", (35, 0, 0)), k(2.4, "chest", (-12, 0, 0)),
    k(2.4, "arm_right", (-60, 0, 50)), k(2.4, "arm_left", (-60, 0, -50)),
] + rest_keys(3.0, wake_parts))

anim("idle", 200, 3.0, True, [
    k(0, "chest", (0, 0, 0)), k(1.5, "chest", (2, 0, 0)), k(3.0, "chest", (0, 0, 0)),
    k(0, "head", (0, 0, 0)), k(1.5, "head", (3, 0, 0)), k(3.0, "head", (0, 0, 0)),
    k(0, "arm_right", (0, 0, 0)), k(1.5, "arm_right", (0, 0, 3)), k(3.0, "arm_right", (0, 0, 0)),
    k(0, "arm_left", (0, 0, 0)), k(1.5, "arm_left", (0, 0, -3)), k(3.0, "arm_left", (0, 0, 0)),
    k(0, "tail", (0, -12, 0)), k(1.5, "tail", (0, 12, 0)), k(3.0, "tail", (0, -12, 0)),
])

anim("walk", 300, 1.6, True, [
    k(0, "leg_right", (28, 0, 0)), k(0.8, "leg_right", (-28, 0, 0)), k(1.6, "leg_right", (28, 0, 0)),
    k(0, "leg_left", (-28, 0, 0)), k(0.8, "leg_left", (28, 0, 0)), k(1.6, "leg_left", (-28, 0, 0)),
    k(0, "arm_right", (-14, 0, 0)), k(0.8, "arm_right", (14, 0, 0)), k(1.6, "arm_right", (-14, 0, 0)),
    k(0, "arm_left", (14, 0, 0)), k(0.8, "arm_left", (-14, 0, 0)), k(1.6, "arm_left", (14, 0, 0)),
    k(0, "body", None, (0, 0, 0)), k(0.4, "body", None, (0, -1, 0)), k(0.8, "body", None, (0, 0, 0)),
    k(1.2, "body", None, (0, -1, 0)), k(1.6, "body", None, (0, 0, 0)),
    k(0, "chest", (0, 0, 3)), k(0.8, "chest", (0, 0, -3)), k(1.6, "chest", (0, 0, 3)),
])

cleave_parts = ["arm_right", "forearm_right", "axe", "arm_left", "forearm_left", "chest", "head", "leg_right", "leg_left",
                "body"]
anim("cleave", 400, 2.0, False, rest_keys(0, cleave_parts) + [
    k(0.9, "arm_right", (-160, 0, -10)), k(1.0, "arm_right", (-170, 0, -10)),
    k(1.2, "arm_right", (-35, 0, 0), linear=True), k(1.6, "arm_right", (-35, 0, 0)),
    k(0.9, "forearm_right", (-20, 0, 0)), k(1.2, "forearm_right", (15, 0, 0), linear=True),
    k(1.6, "forearm_right", (15, 0, 0)),
    k(1.0, "axe", (0, 0, 0)), k(1.2, "axe", (75, 0, 0), linear=True), k(1.6, "axe", (75, 0, 0)),
    k(0.9, "arm_left", (-150, 0, 10)), k(1.0, "arm_left", (-160, 0, 10)),
    k(1.2, "arm_left", (-40, 0, 0), linear=True), k(1.6, "arm_left", (-40, 0, 0)),
    k(0.9, "forearm_left", (-20, 0, 0)), k(1.2, "forearm_left", (5, 0, 0), linear=True),
    k(1.6, "forearm_left", (5, 0, 0)),
    k(0.9, "chest", (-15, 0, 0)), k(1.0, "chest", (-18, 0, 0)), k(1.2, "chest", (25, 0, 0), linear=True),
    k(1.6, "chest", (25, 0, 0)),
    k(1.0, "head", (-10, 0, 0)), k(1.2, "head", (15, 0, 0), linear=True), k(1.6, "head", (15, 0, 0)),
    k(1.0, "leg_right", (0, 0, 0)), k(1.2, "leg_right", (-25, 0, 0), linear=True), k(1.6, "leg_right", (-25, 0, 0)),
    k(1.0, "leg_left", (0, 0, 0)), k(1.2, "leg_left", (10, 0, 0), linear=True), k(1.6, "leg_left", (10, 0, 0)),
    k(1.0, "body", (0, 0, 0), (0, 0, 0)), k(1.2, "body", (0, 0, 0), (0, 1.5, 0), linear=True),
    k(1.6, "body", (0, 0, 0), (0, 1.5, 0)),
] + rest_keys(2.0, cleave_parts))

stomp_parts = ["arm_right", "arm_left", "forearm_right", "forearm_left", "chest", "head", "jaw", "body",
               "leg_right", "leg_left"]
anim("stomp", 500, 2.25, False, rest_keys(0, stomp_parts) + [
    k(1.2, "arm_right", (-170, 0, -20)), k(1.3, "arm_right", (-172, 0, -20)),
    k(1.5, "arm_right", (-70, 0, 25), linear=True), k(1.9, "arm_right", (-70, 0, 25)),
    k(1.2, "arm_left", (-170, 0, 20)), k(1.3, "arm_left", (-172, 0, 20)),
    k(1.5, "arm_left", (-70, 0, -25), linear=True), k(1.9, "arm_left", (-70, 0, -25)),
    k(1.3, "forearm_right", (0, 0, 0)), k(1.5, "forearm_right", (-10, 0, 0), linear=True),
    k(1.9, "forearm_right", (-10, 0, 0)),
    k(1.3, "forearm_left", (0, 0, 0)), k(1.5, "forearm_left", (-10, 0, 0), linear=True),
    k(1.9, "forearm_left", (-10, 0, 0)),
    k(1.2, "chest", (-20, 0, 0)), k(1.3, "chest", (-22, 0, 0)), k(1.5, "chest", (40, 0, 0), linear=True),
    k(1.9, "chest", (40, 0, 0)),
    k(1.2, "head", (-25, 0, 0)), k(1.5, "head", (20, 0, 0), linear=True), k(1.9, "head", (20, 0, 0)),
    k(1.2, "jaw", (25, 0, 0)), k(1.5, "jaw", (10, 0, 0)), k(1.9, "jaw", (0, 0, 0)),
    k(1.2, "body", (0, 0, 0), (0, -2, 0)), k(1.3, "body", (0, 0, 0), (0, -2.5, 0)),
    k(1.5, "body", (0, 0, 0), (0, 4, 0), linear=True), k(1.9, "body", (0, 0, 0), (0, 4, 0)),
    k(1.2, "leg_right", (0, 0, 0), (0, -2, 0)), k(1.3, "leg_right", (0, 0, 0), (0, -2.5, 0)),
    k(1.5, "leg_right", (-40, 0, 0), (0, 4, 0), linear=True), k(1.9, "leg_right", (-40, 0, 0), (0, 4, 0)),
    k(1.2, "leg_left", (0, 0, 0), (0, -2, 0)), k(1.3, "leg_left", (0, 0, 0), (0, -2.5, 0)),
    k(1.5, "leg_left", (-40, 0, 0), (0, 4, 0), linear=True), k(1.9, "leg_left", (-40, 0, 0), (0, 4, 0)),
] + rest_keys(2.25, stomp_parts))

charge_parts = ["chest", "head", "arm_right", "arm_left", "leg_right", "leg_left", "tail", "jaw"]
run_r = [(1.5, 0), (1.65, 45), (1.8, -45), (1.95, 45), (2.1, -45), (2.25, 0)]
paw = [(0.3, -35), (0.5, 10), (0.7, -35), (0.9, 10), (1.1, -35), (1.3, 10), (1.5, 0)]
anim("charge", 600, 3.0, False, rest_keys(0, charge_parts) + [
    k(0.5, "chest", (30, 0, 0)), k(1.5, "chest", (30, 0, 0)), k(1.6, "chest", (40, 0, 0)), k(2.25, "chest", (40, 0, 0)),
    k(0.5, "head", (30, 0, 0)), k(1.5, "head", (30, 0, 0)), k(1.6, "head", (25, 0, 0)), k(2.25, "head", (25, 0, 0)),
    k(0.5, "arm_right", (40, 0, 15)), k(1.5, "arm_right", (40, 0, 15)), k(1.6, "arm_right", (60, 0, 20)),
    k(2.25, "arm_right", (60, 0, 20)),
    k(0.5, "arm_left", (40, 0, -15)), k(1.5, "arm_left", (40, 0, -15)), k(1.6, "arm_left", (60, 0, -20)),
    k(2.25, "arm_left", (60, 0, -20)),
    k(1.4, "jaw", (15, 0, 0)), k(1.5, "jaw", (0, 0, 0)),
    k(1.6, "tail", (70, 0, 0)), k(2.25, "tail", (70, 0, 0)),
    k(1.5, "leg_left", (0, 0, 0)),
] + [k(t, "leg_right", (a, 0, 0), linear=a == 10) for t, a in paw]
  + [k(t, "leg_right", (a, 0, 0), linear=True) for t, a in run_r]
  + [k(t, "leg_left", (-a, 0, 0), linear=True) for t, a in run_r]
  + rest_keys(3.0, charge_parts))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows needed"
    tex = Tex(TEX_W, TEX_H, seed=11)
    paint(tex, {c[0]: c for c in CUBES})
    tex.save(tex_path)
    scene, parts = build_scene(PARTS, CUBES, tex_path, TEX_W, TEX_H, "minotaur")
    for name, start, end, loop, keys in ANIMS:
        animation(parts, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved", blend_path, tex_path, "texture rows used:", used)


main()
