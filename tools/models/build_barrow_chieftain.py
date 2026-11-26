"""
Builds the Barrow Chieftain (boss.BarrowChieftain) in Blender: an undead bronze-age war chief, three blocks tall, and
the stone war chariot he rides in his second phase, drawn by two horse skeletons.

  chief      a skull with a hinged jaw under a crested bronze helmet (cheek guards, nasal, a red horsehair crest),
             a bronze cuirass over the ribs, a leather belt and kilt of strips, a fur collar and a torn red cloak,
             bronze pauldrons, bone arms in bronze bracers, bone legs in bronze greaves, sandals; a long spear with a
             leaf blade in his right hand, a round bronze shield on his left arm
  bier       the stone bier he sleeps on (shown only while he sleeps and wakes; it sinks away as he rises)
  chariot    a stone car with bronze fittings, two eight-spoked wheels, the pole running forward to the yoke, and two
             horse skeletons in bronze harness (shown from his change on, client.BarrowChieftainModel)

One 256x256 texture in the Minecraft style (barrow_chieftain.png) and its glow (barrow_chieftain_glow.png): the soul
fire in his and the horses' eye sockets, the runes on the chariot.

  blender -b --python tools/models/build_barrow_chieftain.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/BarrowChieftainModelData.java. Model space: the ground at y 24, the
front -z, his right -x; client.BarrowChieftainRenderer draws it 1.5 times its pixels (three blocks to the crest).
In the chariot he stands RIDE pixels higher (client.BarrowChieftainModel lifts him outside the animations).

Animations (seconds; boss/BarrowChieftain.java is timed to them):
  sleep     4.0 loop  lying on his bier, spear on his chest, the helmet beside his head
  wake      5.0       soul fire in his eyes (0.5), sits up (1-2), stands (2-3, the bier sinking), puts on his helmet
                      (3-3.6), strikes the spear on his shield at 3.8 and 4.2
  idle      3.0 loop  swaying, the jaw working, the cloak stirring
  walk      1.2 loop
  thrust    1.6       draws back, lunges with the spear at 0.9
  guard     4.6       raises the shield before him (0-0.4), holds it, lowers it (4.2-4.6)
  bash      0.9       a counter with the shield's rim at 0.5
  throw     2.0       draws back, throws the spear at 0.8, catches it as it flies back at 1.6
  transform 6.0       drives the spear into the ground (0-1), raises his arms (1-2), the horses climb out of the
                      ground (2-4), the chariot rises with them (3-4.5), he springs up into it (4.5-5.2), raises the spear
  ride      2.0 loop  in the chariot: the horses shifting, tossing their skulls
  gallop    0.6 loop  the horses galloping, the chariot jolting, the wheels are turned by the model
  charge    1.2       the horses rear, he levels the spear
  summon    1.5       raises the spear and strikes the butt down at 1.0
  rain      1.5       points the spear to the sky (0.4-1.0), sweeps it down at 1.2
  stun      2.0 loop  the horses stumbled to their knees, he slumped over the rail
  roar      1.5       the horses rear, he raises spear and shield, the jaw wide
  death     5.0       the chariot breaks apart (0.3-1.2: wheels off, horses collapse), he climbs down, kneels (2-3)
                      and sinks into dust (3-5)
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
RIDE = -9.0   # how much higher he stands in the chariot (pixels, model y)
HS = 1.25     # the horses' size


def part(name, parent, pivot, rot=(0, 0, 0)):
    """pivot relative to the parent's pivot, in the parent's (rotated) frame"""
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def octagon(prt, name, pivot, origin, size, mat, axis):
    """a box and the same box turned 45 degrees about axis (x or z) under a sub part: an eight-sided disc"""
    box(prt, origin, size, mat)
    part(name, prt, pivot, (45, 0, 0) if axis == "x" else (0, 0, 45))
    o = tuple(origin[i] - pivot[i] for i in range(3))
    box(name, o, size, mat)


def chief():
    part("body", None, (0, 13, 0))
    part("torso", "body", (0, 0, 0))
    # the cuirass over the ribs, the belt, the kilt of leather strips
    box("torso", (-4.5, -11, -2.6), (9, 9.2, 5.2), "cuirass")
    box("torso", (-4.2, -11.6, -2.3), (8.4, 0.8, 4.6), "bronze_edge")
    box("torso", (-2.0, -5.0, -2.9), (4, 2.4, 0.4), "bronze_edge")          # a boss on the belly plate
    box("torso", (-4.7, -2.2, -2.8), (9.4, 2.2, 5.6), "leather")
    box("torso", (-1.0, -2.4, -3.1), (2, 2.6, 0.5), "bronze_edge")          # the buckle
    box("torso", (-4.6, 0, -2.9), (9.2, 4.2, 5.8), "kilt")
    box("torso", (-1.0, -12.6, -1.0), (2, 1.8, 2), "bone")                  # the neck
    # the fur collar and the torn red cloak
    part("cloak", "torso", (0, -10.6, 2.6), (6, 0, 0))
    box("cloak", (-5.2, -1.4, -1.0), (10.4, 2.8, 3.0), "fur")
    box("cloak", (-4.6, 0.6, 0.6), (9.2, 15.5, 0.8), "cloak")
    # the head: a skull with its jaw; the helmet
    part("head", "torso", (0, -12.5, 0))
    box("head", (-3.4, -7, -3.4), (6.8, 6.2, 6.8), "skull")
    part("jaw", "head", (0, -1.4, 1.2))
    box("jaw", (-2.8, 0, -4.8), (5.6, 1.6, 4.2), "jaw")
    part("helmet", "head", (0, -4, 0))
    box("helmet", (-3.9, -4.4, -3.9), (7.8, 4.4, 7.8), "helmet")
    box("helmet", (-4.1, -0.6, -4.1), (8.2, 1.0, 8.2), "bronze_edge")       # the rim
    box("helmet", (-4.2, -0.2, -3.6), (0.8, 4.4, 3.4), "helmet")            # cheek guards
    box("helmet", (3.4, -0.2, -3.6), (0.8, 4.4, 3.4), "helmet")
    box("helmet", (-0.45, -0.4, -4.4), (0.9, 3.2, 0.6), "bronze_edge")      # the nasal
    box("helmet", (-0.6, -6.2, -3.0), (1.2, 1.9, 6.6), "bronze_edge")       # the crest's holder
    box("helmet", (-0.7, -8.6, -4.0), (1.4, 2.6, 9.6), "crest")             # the horsehair crest
    box("helmet", (-0.6, -6.6, 5.4), (1.2, 4.2, 1.6), "crest")              # its tail hanging down behind
    # the arms
    for s, sx in (("r", -1), ("l", 1)):
        arm, fore = f"arm_{s}", f"forearm_{s}"
        part(arm, "torso", (sx * 6, -10, 0))
        box(arm, (-2.6, -1.6, -3.0), (5.2, 3.2, 6.0), "pauldron")
        box(arm, (-0.9, 1.0, -0.9), (1.8, 5.6, 1.8), "bone")
        part(fore, arm, (0, 6.2, 0))
        box(fore, (-1.5, 0, -1.5), (3.0, 3.8, 3.0), "bracer")
        box(fore, (-0.8, 3.6, -0.8), (1.6, 1.4, 1.6), "bone")
        box(fore, (-1.1, 4.8, -1.2), (2.2, 1.6, 2.4), "bone")               # the hand
    # the spear in the right hand: shaft, bronze collar, a leaf blade, the butt spike
    part("spear", "forearm_r", (0, 5.6, -0.2))
    box("spear", (-0.45, -21, -0.45), (0.9, 31, 0.9), "shaft")
    box("spear", (-0.7, -21.8, -0.7), (1.4, 1.6, 1.4), "bronze_edge")
    box("spear", (-0.35, -27.6, -1.3), (0.7, 6.2, 2.6), "blade")
    box("spear", (-0.3, -29.2, -0.7), (0.6, 1.8, 1.4), "blade")
    box("spear", (-0.5, 10, -0.5), (1.0, 1.0, 1.0), "bronze_edge")
    box("spear", (-0.3, 11, -0.3), (0.6, 1.8, 0.6), "bronze_edge")
    for y in (-14, -6, 2):                                                   # leather bindings
        box("spear", (-0.55, y, -0.55), (1.1, 0.8, 1.1), "leather")
    # the round shield on the left arm, facing forward
    part("shield", "forearm_l", (0.6, 2.6, -2.6))
    octagon("shield", "shield_oct", (0, 0, 0), (-6.2, -6.2, -1.2), (12.4, 12.4, 1.2), "shield", "z")
    box("shield", (-1.6, -1.6, -2.0), (3.2, 3.2, 0.8), "bronze_edge")       # the boss
    box("shield", (-0.9, -0.9, -2.4), (1.8, 1.8, 0.4), "bronze_edge")
    # the legs
    for s, sx in (("r", -1), ("l", 1)):
        leg, shin = f"leg_{s}", f"shin_{s}"
        part(leg, "body", (sx * 2.3, 0, 0))
        box(leg, (-0.9, 0, -0.9), (1.8, 6.4, 1.8), "bone")
        box(leg, (-1.3, 5.6, -1.4), (2.6, 1.4, 2.6), "bone")                 # the knee
        part(shin, leg, (0, 6.5, 0))
        box(shin, (-1.6, 0.2, -1.8), (3.2, 4.6, 3.4), "greave")
        box(shin, (-0.7, 4.8, -0.7), (1.4, 0.9, 1.4), "bone")
        box(shin, (-1.5, 5.6, -2.8), (3.0, 0.9, 4.6), "sandal")


def bier():
    part("bier", None, (0, 24, 0))
    box("bier", (-6.5, -5.5, -15), (13, 5.5, 36), "stone")
    box("bier", (-7.2, -1.2, -15.8), (14.4, 1.2, 37.6), "stone_dark")
    box("bier", (-6.8, -6.2, -15.4), (13.6, 0.8, 36.8), "stone_top")
    box("bier", (-5, -8, 15.5), (10, 2.4, 4), "stone_top")                   # the head rest


def chariot():
    part("chariot", None, (0, 24, 0))
    part("car", "chariot", (0, -9, 0))
    box("car", (-6.5, 0, -6.5), (13, 1.6, 13), "stone_top")                  # the floor
    box("car", (-6.5, -8.5, -7.5), (13, 8.5, 1.6), "car_front")             # the breastwork
    box("car", (-7, -9.4, -8.2), (14, 1.0, 2.6), "bronze_edge")              # its rail
    box("car", (-7.2, -5.5, -6.5), (1.4, 5.5, 10), "car_side")
    box("car", (5.8, -5.5, -6.5), (1.4, 5.5, 10), "car_side")
    box("car", (-7.4, -6.2, -6.5), (1.8, 0.8, 10), "bronze_edge")
    box("car", (5.6, -6.2, -6.5), (1.8, 0.8, 10), "bronze_edge")
    box("car", (-11.5, 1.2, -0.75), (23, 1.5, 1.5), "bronze_edge")           # the axle
    for s, sx in (("l", 1), ("r", -1)):
        w = f"wheel_{s}"
        part(w, "car", (sx * 10.6, 1.9, 0))
        # the rim: four bars and four more turned 45 degrees; the spokes; the hub
        for (o, sz) in (((-0.8, -7.2, -3.0), (1.6, 1.5, 6.0)), ((-0.8, 5.7, -3.0), (1.6, 1.5, 6.0)),
                        ((-0.8, -3.0, -7.2), (1.6, 6.0, 1.5)), ((-0.8, -3.0, 5.7), (1.6, 6.0, 1.5))):
            box(w, o, sz, "wheel")
        wo = f"{w}_oct"
        part(wo, w, (0, 0, 0), (45, 0, 0))
        for (o, sz) in (((-0.8, -7.2, -3.0), (1.6, 1.5, 6.0)), ((-0.8, 5.7, -3.0), (1.6, 1.5, 6.0)),
                        ((-0.8, -3.0, -7.2), (1.6, 6.0, 1.5)), ((-0.8, -3.0, 5.7), (1.6, 6.0, 1.5))):
            box(wo, o, sz, "wheel")
        box(w, (-0.4, -6, -0.4), (0.8, 12, 0.8), "spoke")
        box(w, (-0.4, -0.4, -6), (0.8, 0.8, 12), "spoke")
        box(wo, (-0.4, -6, -0.4), (0.8, 12, 0.8), "spoke")
        box(wo, (-0.4, -0.4, -6), (0.8, 0.8, 12), "spoke")
        box(w, (-1.4, -1.4, -1.4), (2.8, 2.8, 2.8), "bronze_edge")
    # the pole to the yoke
    part("pole", "car", (0, -2.5, -7.5), (-17, 0, 0))
    box("pole", (-0.7, -0.7, -30), (1.4, 1.4, 30), "wood")
    box("pole", (-10, -1.6, -29.2), (20, 1.4, 1.8), "wood")                  # the yoke
    for x in (-6.5, 6.5):
        box("pole", (x - 1.6, -2.8, -29.6), (3.2, 1.4, 2.6), "bronze_edge")
    # the horses (drawn 1.25 times the sizes below)
    def hpart(name, parent, pivot, rot=(0, 0, 0)):
        part(name, parent, pivot if parent == "chariot" else tuple(v * HS for v in pivot), rot)

    def hbox(prt, origin, size, mat):
        box(prt, tuple(v * HS for v in origin), tuple(v * HS for v in size), mat)

    for s, sx in (("l", 1), ("r", -1)):
        h = f"horse_{s}"
        hpart(h, "chariot", (sx * 6.5, -18, -27))
        hbox(h, (-2.6, -3.2, -7.5), (5.2, 6.0, 14), "ribs")                  # the rib cage and spine
        hbox(h, (-0.8, -4.0, -8.0), (1.6, 1.0, 16.5), "bone")                # the spine on top
        hbox(h, (-2.9, -3.6, 5.5), (5.8, 4.2, 3.6), "bone")                  # the pelvis
        hbox(h, (-2.9, -3.6, -9.5), (5.8, 4.4, 3.0), "bone")                 # the shoulders
        hbox(h, (-3.0, -1.8, -8.0), (6.0, 2.0, 1.4), "harness")              # the breast strap
        hbox(h, (-3.0, -3.9, -2.0), (6.0, 1.0, 2.6), "harness")              # the girth
        nk = f"neck_{s}"
        hpart(nk, h, (0, -2.5, -8.5), (40, 0, 0))
        hbox(nk, (-1.2, -9.5, -1.4), (2.4, 9.5, 2.8), "spine")
        hbox(nk, (-0.4, -9.0, 1.2), (0.8, 8.0, 1.0), "mane")                 # a few strands of mane left
        sk = f"skull_{s}"
        hpart(sk, nk, (0, -9.4, 0), (-60, 0, 0))
        hbox(sk, (-1.9, -2.6, -2.4), (3.8, 3.6, 4.2), "skull")
        hbox(sk, (-1.4, -1.8, -8.0), (2.8, 2.6, 5.8), "skull")                # the long face
        hpart(f"hjaw_{s}", sk, (0, 0.8, -1.2), (0, 0, 0))
        hbox(f"hjaw_{s}", (-1.2, 0, -6.4), (2.4, 1.0, 6.2), "jaw")
        hbox(sk, (-2.1, -1.5, -5.0), (4.2, 0.8, 1.0), "harness")             # the bridle
        tl = f"tail_{s}"
        hpart(tl, h, (0, -3.0, 8.5), (-30, 0, 0))
        hbox(tl, (-0.5, 0, -0.5), (1.0, 7, 1.0), "spine")
        for leg, (lx, lz) in (("fl", (1.6, -7.0)), ("fr", (-1.6, -7.0)), ("bl", (1.6, 6.5)), ("br", (-1.6, 6.5))):
            up, low = f"h{leg}_{s}", f"h{leg}2_{s}"
            hpart(up, h, (lx, 2.4, lz))
            hbox(up, (-0.85, 0, -0.85), (1.7, 6.0, 1.7), "bone")
            hpart(low, up, (0, 6.0, 0))
            hbox(low, (-0.7, 0, -0.7), (1.4, 5.4, 1.4), "bone")
            hbox(low, (-1.0, 4.6, -1.2), (2.0, 1.0, 2.2), "hoof")


def model():
    chief()
    bier()
    chariot()


# ---------------------------------------------------------------- painting
BONE = [(126, 118, 98), (156, 148, 124), (184, 176, 150), (208, 200, 176), (226, 220, 198)]
BRONZE = [(92, 58, 26), (128, 84, 36), (164, 112, 50), (196, 144, 70), (226, 182, 104)]
VERDIGRIS = [(56, 110, 92), (78, 140, 116), (104, 168, 140)]
LEATHER = [(56, 36, 22), (76, 50, 30), (98, 66, 40), (120, 84, 52)]
CLOAK = [(70, 18, 18), (96, 26, 24), (124, 36, 30), (150, 50, 40)]
CREST = [(110, 20, 20), (150, 30, 26), (190, 44, 34), (220, 70, 54)]
FUR = [(60, 48, 38), (84, 68, 52), (110, 92, 70), (136, 118, 92)]
STONE = [(72, 72, 76), (92, 92, 96), (112, 112, 116), (134, 134, 138), (156, 156, 160)]
WOOD = [(64, 44, 28), (84, 60, 38), (106, 78, 50)]
SOUL = (90, 230, 220)
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
                    c, glows = paint_px(mat, x, y, fw, fh, face, light, rnd)
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if glows:
                        glow.set(x0 + x, y0 + y, glows if isinstance(glows, tuple) else c, 0.0)


def bronze(x, y, w, h, light, rnd):
    level = 2.2 + light + rnd.uniform(-0.25, 0.25) + (0.8 if y == 0 else 0) - (0.7 if y == h - 1 else 0)
    if (x == 0 or y == h - 1) and rnd.random() < 0.18:   # verdigris along the edges and seams
        return pick(VERDIGRIS, 1 + light * 0.5)
    return pick(BRONZE, level)


def paint_px(mat, x, y, w, h, face, light, rnd):
    n = rnd.uniform(-0.35, 0.35)
    if mat == "skull":
        if face == "front" and w >= 6 and h >= 6:
            # two dark eye sockets with soul fire deep in them, the nose hole, a row of teeth
            if y in (2, 3) and x in (1, 2, w - 3, w - 2):
                if y == 3 and x in (2, w - 3) or y == 2 and x in (1, w - 2):
                    return SOUL, True
                return (22, 18, 16), False
            if y == 4 and x in (w // 2 - 1, w // 2):
                return (34, 28, 24), False
            if y == h - 1 and x % 2 == 0 and 0 < x < w - 1:
                return (40, 34, 28), False
        if face == "front" and w == 4 and h == 4 and y == 1 and x in (0, 3):   # a horse's eye sockets
            return SOUL, True
        crack = (x * 5 + y * 3) % 11 == 0
        return pick(BONE, 2.2 + light + n - (1.2 if crack else 0)), False
    if mat in ("bone", "jaw", "spine"):
        joint = mat == "spine" and y % 3 == 2
        return pick(BONE, 2.0 + light + n - (1.0 if joint else 0) - (0.5 if mat == "jaw" else 0)), False
    if mat == "ribs":
        # ribs: pale bars round the cage, dark gaps between them
        if face in ("left", "right", "bottom") and x % 3 == 2 and 0 < y < h - 1:
            return (26, 22, 20), False
        if face == "top":
            return pick(BONE, 2.6 + n), False
        return pick(BONE, 1.8 + light + n), False
    if mat in ("cuirass", "pauldron", "bracer", "greave", "helmet", "bronze_edge"):
        c = bronze(x, y, w, h, light, rnd)
        if mat == "cuirass" and face == "front":
            # the muscled breastplate: two arcs of the chest, a line down the middle
            if (y == 3 and 1 <= x <= w - 2 and x != w // 2) or (x == w // 2 and 4 <= y <= h - 2):
                return pick(BRONZE, 0.8), False
            if y == 2 and x in (2, w - 3):
                return pick(BRONZE, 4), False
        if mat == "helmet" and face in ("left", "right", "front", "back") and y == h - 2:
            return pick(BRONZE, 4), False
        return c, False
    if mat == "shield":
        # a round bronze shield: rings out from the middle, a spiral of dots, verdigris at the rim
        cx, cy = w / 2 - 0.5, h / 2 - 0.5
        r = math.hypot(x - cx, y - cy)
        if face in ("front", "back"):
            if r > w / 2 - 1.2:
                return pick(VERDIGRIS, 1 + n), False
            if abs(r - w * 0.33) < 0.5 or abs(r - w * 0.18) < 0.5:
                return pick(BRONZE, 3.6), False
            ang = math.atan2(y - cy, x - cx)
            if abs(((ang + r * 0.6) % (math.pi / 2)) - 0.3) < 0.12 and r > 2:
                return pick(BRONZE, 0.6), False
        return bronze(x, y, w, h, light, rnd), False
    if mat == "blade":
        return pick(BRONZE, 3.2 + light + (0.6 if x == 0 else 0)), False
    if mat == "shaft" or mat == "wood":
        grain = (y + x * 3) % 5 == 0
        return pick(WOOD, 1.2 + light * 0.6 + n - (0.8 if grain else 0)), False
    if mat in ("leather", "sandal", "harness"):
        stitch = mat == "harness" and x % 3 == 1 and y == 0
        if stitch:
            return pick(BRONZE, 3), False
        return pick(LEATHER, 1.5 + light + n), False
    if mat == "kilt":
        # strips of leather, each with a bronze stud
        if x % 3 == 2:
            return (30, 20, 12), False
        if y == 1 and x % 3 == 0:
            return pick(BRONZE, 3), False
        return pick(LEATHER, 1.6 + light + n - y * 0.15), False
    if mat == "cloak":
        # red wool, faded, torn at the hem (the bottom rows ragged: holes)
        if y >= h - 3 and (x * 7 + y * 3) % 5 < y - (h - 4):
            return None, False
        fold = x % 4 == 0
        return pick(CLOAK, 1.6 + light + n - (0.8 if fold else 0) - y * 0.04), False
    if mat == "fur":
        return pick(FUR, 1.4 + light + rnd.uniform(-0.8, 0.8)), False
    if mat == "crest":
        strand = x % 2 == 0
        return pick(CREST, 1.6 + light + (0.6 if strand else -0.4) + n), False
    if mat == "mane":
        return pick(FUR, 0.8 + n), False
    if mat == "hoof":
        return pick(BONE, 0.6 + light), False
    if mat in ("stone", "stone_dark", "stone_top", "car_side", "car_front"):
        level = 2.0 + light + n - (0.8 if mat == "stone_dark" else 0)
        if (x % 6 == 5 or y % 4 == 3) and face not in ("top", "bottom"):
            level -= 1.0   # the joints between the blocks
        if mat in ("car_front", "car_side") and face in ("front", "left", "right", "back"):
            # runes cut into the stone, glowing with soul fire
            rune = (y == h // 2 and x % 4 != 3) or (x % 4 == 1 and abs(y - h // 2) == 1)
            if rune and 1 < x < w - 2:
                return (40, 120, 112), SOUL
        return pick(STONE, level), False
    if mat == "wheel":
        return pick(STONE, 1.6 + light + n), False
    if mat == "spoke":
        return pick(WOOD, 1.4 + light + n), False
    return pick(STONE, 2 + light + n), False


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


CHIEF = ["body", "torso", "head", "jaw", "helmet", "cloak", "arm_r", "forearm_r", "spear", "arm_l", "forearm_l", "shield",
         "leg_r", "shin_r", "leg_l", "shin_l"]
HORSE = [f"{p}_{s}" for s in ("l", "r") for p in ("horse", "neck", "skull", "hjaw", "tail", "hfl", "hfl2", "hfr", "hfr2",
                                                   "hbl", "hbl2", "hbr", "hbr2")]
CAR = ["chariot", "car", "pole", "wheel_l", "wheel_r"]
ALL = CHIEF + HORSE + CAR + ["bier"]


def rest(t, parts=ALL, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


def pose(t, d, linear=False):
    """d: part -> rot or (rot, pos)"""
    out = []
    for p, v in d.items():
        if isinstance(v[0], (tuple, list)):
            out.append(k(t, p, tuple(v[0]), tuple(v[1]), linear))
        else:
            out.append(k(t, p, tuple(v), (0, 0, 0), linear))
    return out


READY = {"arm_r": (-20, 0, 6), "forearm_r": (-30, 0, 0), "spear": (20, 0, 0), "arm_l": (-24, 0, -14), "forearm_l": (-40, 0, 0)}

# lying on the bier: on his back, arms over the chest holding the spear along the body, the helmet beside the head
LYING = {
    "body": ((-90, 0, 0), (0, 2.2, 1.0)),
    "head": (8, 0, 0), "jaw": (6, 0, 0),
    "helmet": ((70, 0, 20), (6.5, 2.5, 1.0)),
    "arm_r": (-30, 0, 30), "forearm_r": (-95, 0, 0), "spear": ((-5, 0, -10), (0, 0, 0)),
    "arm_l": (-30, 0, -30), "forearm_l": (-100, 0, 0),
    "leg_r": (0, 0, 0), "leg_l": (0, 0, 0), "cloak": (-10, 0, 0),
}
HIDE_CAR = {p: ((0, 0, 0), (0, 40, 0)) for p in ("chariot",)}


def horse_legs(t, s, fl=0, fr=0, bl=0, br=0, knee=0, linear=False):
    out = []
    for leg, a in (("fl", fl), ("fr", fr), ("bl", bl), ("br", br)):
        out.append(k(t, f"h{leg}_{s}", (a, 0, 0), (0, 0, 0), linear))
        bend = knee if leg[0] == "f" else -knee
        out.append(k(t, f"h{leg}2_{s}", (max(0, -a) * 0.6 + bend if leg[0] == "f" else -max(0, a) * 0.6 + bend, 0, 0), (0, 0, 0), linear))
    return out


def rear(t, s, linear=False):   # a horse up on its hind legs
    return [k(t, f"horse_{s}", (-28, 0, 0), (0, -2.0, 1.5), linear), k(t, f"neck_{s}", (-15, 0, 0), None, linear),
            k(t, f"skull_{s}", (-10, 0, 0), None, linear), k(t, f"hjaw_{s}", (25, 0, 0), None, linear)] + \
        horse_legs(t, s, fl=-70, fr=-50, bl=28, br=28, knee=40, linear=linear)


def animations():
    rnd = random.Random(17)

    # sleep: lying on the bier
    sl = pose(0, LYING) + pose(4.0, LYING)
    sl += [k(2.0, "jaw", (10, 0, 0)), k(2.0, "cloak", (-12, 0, 0))]
    anim("sleep", 0, 4.0, True, sl)

    # wake
    wk = pose(0, LYING)
    wk += pose(0.5, {"jaw": (25, 0, 0), "head": (14, 0, 0)}) + pose(0.8, {"jaw": (6, 0, 0)})
    sit = {"body": ((-10, 0, 0), (0, -2.0, 1.0)), "head": (-6, 0, 0), "arm_r": (-20, 0, 25), "forearm_r": (-40, 0, 0),
           "spear": ((10, 0, -10), (0, 0, 0)), "arm_l": (20, 0, -30), "forearm_l": (-30, 0, 0),
           "leg_r": (-90, 0, 4), "leg_l": (-90, 0, -4), "shin_r": (0, 0, 0), "shin_l": (0, 0, 0)}
    wk += pose(1.0, {"body": ((-80, 0, 0), (0, -2.0, 1.0))}) + pose(2.0, sit)
    wk += pose(2.0, {"helmet": ((70, 0, 20), (6.5, 2.5, 1.0))})
    swing = dict(sit)
    swing.update({"body": ((-10, -70, 0), (0, -2.0, 1.0)), "leg_r": (-80, 0, 0), "leg_l": (-75, 0, 0), "shin_r": (60, 0, 0),
                  "shin_l": (55, 0, 0)})
    wk += pose(2.4, swing)
    stand = {"body": ((0, 0, 0), (0, 0, 0)), "leg_r": (0, 0, 0), "leg_l": (0, 0, 0), "shin_r": (0, 0, 0), "shin_l": (0, 0, 0),
             "head": (10, 0, 0), "arm_r": (-10, 0, 6), "forearm_r": (-20, 0, 0), "spear": (0, 0, 0),
             "arm_l": (-60, 0, -10), "forearm_l": (-70, 0, 0)}
    wk += pose(3.0, stand) + pose(3.0, {"helmet": ((60, 0, 0), (2.0, 5.0, -3.0)), "bier": ((0, 0, 0), (0, 0, 0))})
    wk += pose(3.4, {"helmet": ((-10, 0, 0), (0, -3.0, 0)), "arm_l": (-150, 0, -10), "forearm_l": (-40, 0, 0), "head": (-5, 0, 0)})
    wk += pose(3.6, {"helmet": ((0, 0, 0), (0, 0, 0)), "arm_l": (-40, 0, -14), "forearm_l": (-50, 0, 0)})
    wk += pose(3.6, {"bier": ((0, 0, 0), (0, 12, 0))})
    for t, hit in ((3.7, False), (3.8, True), (4.0, False), (4.2, True)):
        wk += pose(t, {"arm_r": (-70, 0, 30) if not hit else (-30, 0, 6), "forearm_r": (-60, 0, 0) if not hit else (-50, 0, 0),
                       "spear": (40, 0, 0) if not hit else (0, 0, 0), "arm_l": (-50, 0, -20), "forearm_l": (-60, 0, 0),
                       "head": (-8, 0, 0), "jaw": (18 if hit else 4, 0, 0)}, linear=hit)
    wk += pose(5.0, READY) + rest(5.0, ["body", "torso", "head", "jaw", "helmet", "leg_r", "leg_l", "shin_r", "shin_l"])
    wk += pose(5.0, {"bier": ((0, 0, 0), (0, 12, 0))})
    anim("wake", 120, 5.0, False, wk)

    # idle
    idl = pose(0, READY) + rest(0, ["torso", "head", "jaw", "cloak"])
    idl += [k(1.5, "torso", (2, 3, 0)), k(1.5, "head", (-3, -8, 0)), k(1.0, "jaw", (12, 0, 0)), k(1.4, "jaw", (0, 0, 0)),
            k(1.5, "cloak", (8, 0, 2)), k(1.5, "arm_r", (-22, 0, 8))]
    idl += pose(3.0, READY) + rest(3.0, ["torso", "head", "jaw", "cloak"])
    anim("idle", 240, 3.0, True, idl)

    # walk
    wl = []
    for t, a in ((0, 1), (0.3, 0), (0.6, -1), (0.9, 0), (1.2, 1)):
        wl += [k(t, "leg_r", (28 * a, 0, 0)), k(t, "leg_l", (-28 * a, 0, 0)), k(t, "shin_r", (max(0, -a) * 30, 0, 0)),
               k(t, "shin_l", (max(0, a) * 30, 0, 0)), k(t, "body", (0, 0, 0), (0, -0.6 if a == 0 else 0, 0)),
               k(t, "arm_l", (-24 - 8 * a, 0, -14)), k(t, "cloak", (10 + 6 * abs(a), 0, 0)), k(t, "torso", (0, 4 * a, 0))]
    anim("walk", 360, 1.2, True, wl)

    # thrust
    th = pose(0, READY)
    th += pose(0.6, {"torso": (0, -28, 0), "arm_r": (-70, -30, 40), "forearm_r": (-30, 0, 0), "spear": (70, 0, 0),
                     "leg_r": (24, 0, 0), "leg_l": (-20, 0, 0), "body": ((0, 0, 0), (0, 0.8, 1.5)), "head": (0, 20, 0)})
    th += pose(0.9, {"torso": (10, 22, 0), "arm_r": (-90, 0, 0), "forearm_r": (0, 0, 0), "spear": (90, 0, 0),
                     "leg_r": (-30, 0, 0), "leg_l": (25, 0, 0), "body": ((0, 0, 0), (0, 0.5, -3.5)), "head": (-6, -15, 0),
                     "jaw": (20, 0, 0)}, linear=True)
    th += pose(1.15, {"torso": (8, 20, 0), "arm_r": (-88, 0, 0), "spear": (90, 0, 0), "body": ((0, 0, 0), (0, 0.5, -3.5))})
    th += pose(1.6, READY) + rest(1.6, ["torso", "leg_r", "leg_l", "body", "head", "jaw"])
    anim("thrust", 480, 1.6, False, th)

    # guard
    guard = {"arm_l": (-70, 34, -10), "forearm_l": (-50, 0, 0), "torso": (6, 10, 0), "leg_r": (16, 0, 0), "leg_l": (-14, 0, 0),
             "body": ((0, 0, 0), (0, 1.0, 0)), "arm_r": (-40, 0, 20), "forearm_r": (-60, 0, 0), "spear": (60, 0, 0),
             "head": (8, 0, 0)}
    gd = pose(0, READY) + pose(0.4, guard)
    for t in (1.4, 2.4, 3.4):
        gd += pose(t, {"torso": (6, 12, 0), "head": (10, 4, 0)})
        gd += pose(t + 0.5, {"torso": (6, 8, 0), "head": (6, -4, 0)})
    gd += pose(4.2, guard) + pose(4.6, READY) + rest(4.6, ["torso", "leg_r", "leg_l", "body", "head"])
    anim("guard", 600, 4.6, False, gd)

    # bash (played over the guard)
    bs = pose(0, guard) + pose(0.3, {"arm_l": (-60, 50, -30), "forearm_l": (-40, 0, 0), "torso": (0, 30, 0)})
    bs += pose(0.5, {"arm_l": (-85, 0, -6), "forearm_l": (-30, 0, 0), "torso": (12, -18, 0), "body": ((0, 0, 0), (0, 1.0, -2.5))},
               linear=True)
    bs += pose(0.9, guard)
    anim("bash", 720, 0.9, False, bs)

    # throw
    tw = pose(0, READY)
    tw += pose(0.6, {"torso": (-8, -35, 0), "arm_r": (-170, 0, 30), "forearm_r": (-20, 0, 0), "spear": (90, 0, 0),
                     "arm_l": (-70, -20, -20), "leg_r": (25, 0, 0), "leg_l": (-15, 0, 0), "head": (-5, 25, 0)})
    tw += pose(0.8, {"torso": (12, 25, 0), "arm_r": (-60, 0, 10), "forearm_r": (-10, 0, 0), "spear": (90, 0, 0),
                     "leg_r": (-20, 0, 0), "leg_l": (20, 0, 0), "head": (5, -10, 0), "jaw": (20, 0, 0)}, linear=True)
    tw += pose(1.3, {"torso": (4, 8, 0), "arm_r": (-110, 0, 20), "forearm_r": (-30, 0, 0), "jaw": (0, 0, 0)})
    tw += pose(1.6, {"arm_r": (-120, 0, 15), "forearm_r": (-20, 0, 0), "spear": (90, 0, 0)}, linear=True)
    tw += pose(2.0, READY) + rest(2.0, ["torso", "leg_r", "leg_l", "head", "jaw"])
    anim("throw", 840, 2.0, False, tw)

    # transform
    tf = pose(0, READY) + pose(0, {"chariot": ((0, 0, 0), (0, 40, 0))})
    tf += pose(0.6, {"arm_r": (-150, 0, 10), "forearm_r": (-10, 0, 0), "spear": (0, 0, 0), "torso": (-10, 0, 0), "head": (-15, 0, 0)})
    tf += pose(1.0, {"arm_r": (-40, 0, 10), "forearm_r": (-30, 0, 0), "spear": ((-20, 0, 0), (0, 4, -4)), "torso": (25, 0, 0),
                     "head": (20, 0, 0), "body": ((0, 0, 0), (0, 1.5, 0))}, linear=True)
    arms_up = {"arm_r": (-160, 0, 30), "arm_l": (-160, 0, -30), "forearm_r": (-10, 0, 0), "forearm_l": (-10, 0, 0),
               "torso": (-12, 0, 0), "head": (-30, 0, 0), "jaw": (30, 0, 0), "body": ((0, 0, 0), (0, 0, 0)),
               "spear": ((-20, 0, 0), (0, 4, -4))}
    tf += pose(1.6, arms_up) + pose(4.0, arms_up)
    tf += pose(2.0, {"chariot": ((0, 0, 0), (0, 40, 0))})
    # the horses claw out of the ground, the car rising with them
    tf += pose(2.6, {"chariot": ((10, 0, 0), (0, 22, 0))}) + rear(2.6, "l") + rear(2.6, "r")
    tf += pose(3.4, {"chariot": ((-8, 0, 0), (0, 8, 0))}) + rear(3.4, "l") + rear(3.4, "r")
    tf += pose(4.5, {"chariot": ((0, 0, 0), (0, 0, 0))}) + rest(4.5, HORSE)
    tf += pose(4.5, {"arm_r": (-30, 0, 10), "arm_l": (-30, 0, -10), "torso": (20, 0, 0), "head": (10, 0, 0), "jaw": (0, 0, 0),
                     "body": ((0, 0, 0), (0, 2, 0)), "leg_r": (-30, 0, 0), "leg_l": (-30, 0, 0), "shin_r": (60, 0, 0),
                     "shin_l": (60, 0, 0), "spear": ((0, 0, 0), (0, 0, 0)), "forearm_r": (-40, 0, 0)})
    tf += pose(4.85, {"body": ((0, 0, 0), (0, RIDE - 4, -2)), "leg_r": (-60, 0, 0), "leg_l": (-60, 0, 0), "shin_r": (80, 0, 0),
                      "shin_l": (80, 0, 0)}, linear=True)
    tf += pose(5.2, {"body": ((0, 0, 0), (0, RIDE, 0)), "leg_r": (0, 0, 0), "leg_l": (0, 0, 0), "shin_r": (0, 0, 0), "shin_l": (0, 0, 0),
                     "torso": (5, 0, 0)})
    tf += pose(5.6, {"arm_r": (-170, 0, 20), "forearm_r": (0, 0, 0), "spear": (0, 0, 0), "head": (-20, 0, 0), "jaw": (30, 0, 0),
                     "torso": (-8, 0, 0), "body": ((0, 0, 0), (0, RIDE, 0))}) + rear(5.6, "l") + rear(5.6, "r")
    tf += pose(6.0, READY) + pose(6.0, {"body": ((0, 0, 0), (0, RIDE, 0))}) + rest(6.0, HORSE + ["torso", "head", "jaw", "leg_r", "leg_l", "shin_r", "shin_l"])
    anim("transform", 960, 6.0, False, tf)

    # in the chariot (he is lifted by RIDE in Java)
    rd = rest(0, HORSE) + pose(0, READY)
    rd += [k(1.0, "neck_l", (-8, 0, 0)), k(1.0, "skull_l", (10, 0, 0)), k(0.6, "neck_r", (6, 4, 0)), k(1.4, "neck_r", (-4, 0, 0)),
           k(1.2, "hjaw_l", (20, 0, 0)), k(1.5, "hjaw_l", (0, 0, 0)), k(1.0, "tail_l", (10, 0, 10)), k(1.0, "tail_r", (10, 0, -10)),
           k(1.0, "hfl_r", (-20, 0, 0)), k(1.3, "hfl_r", (0, 0, 0)), k(1.0, "torso", (0, 4, 0))]
    rd += rest(2.0, HORSE) + pose(2.0, READY) + rest(2.0, ["torso"])
    anim("ride", 1100, 2.0, True, rd)

    # gallop
    gp = []
    for t, ph in ((0, 0), (0.15, 1), (0.3, 2), (0.45, 3), (0.6, 0)):
        for s, off in (("l", 0), ("r", 1)):
            q = (ph + off) % 4
            a = [35, 0, -35, 0][q]
            b = [-35, 0, 35, 0][q]
            gp += horse_legs(t, s, fl=a, fr=a * 0.8, bl=b, br=b * 0.8, knee=20 if q in (1, 3) else 0)
            gp += [k(t, f"horse_{s}", (3 if q in (0, 2) else -3, 0, 0), (0, -1.0 if q in (1, 3) else 0, 0)),
                   k(t, f"neck_{s}", (-8 if q == 1 else 6 if q == 3 else 0, 0, 0))]
        gp += [k(t, "car", (0, 0, 2 if ph == 1 else -2 if ph == 3 else 0), (0, -0.6 if ph in (1, 3) else 0, 0)),
               k(t, "cloak", (30 + (6 if ph % 2 else 0), 0, 0)), k(t, "torso", (6, 0, 0))]
    gp += pose(0, READY) + pose(0.6, READY)
    anim("gallop", 1200, 0.6, True, gp)

    # charge wind-up
    ch = pose(0, READY) + rest(0, HORSE)
    ch += rear(0.5, "l") + rear(0.5, "r") + pose(0.5, {"arm_r": (-150, 0, 20), "forearm_r": (-10, 0, 0), "jaw": (30, 0, 0),
                                                       "head": (-20, 0, 0)})
    ch += rest(1.0, HORSE) + pose(1.0, {"arm_r": (-85, 0, 0), "forearm_r": (0, 0, 0), "spear": (90, 0, 0), "torso": (15, 0, 0),
                                       "head": (0, 0, 0), "jaw": (10, 0, 0)})
    ch += pose(1.2, {"arm_r": (-85, 0, 0), "spear": (90, 0, 0), "torso": (15, 0, 0)})
    anim("charge", 1300, 1.2, False, ch)

    # summon
    sm = pose(0, READY) + pose(0.6, {"arm_r": (-170, 0, 10), "forearm_r": (0, 0, 0), "spear": (0, 0, 0), "head": (-25, 0, 0),
                                    "jaw": (30, 0, 0), "torso": (-10, 0, 0)})
    sm += pose(1.0, {"arm_r": (-40, 0, 20), "forearm_r": (-20, 0, 0), "spear": ((-10, 0, 0), (0, 3, 0)), "torso": (20, 0, 0),
                     "head": (15, 0, 0), "jaw": (5, 0, 0)}, linear=True)
    sm += pose(1.5, READY) + rest(1.5, ["torso", "head", "jaw"])
    anim("summon", 1400, 1.5, False, sm)

    # spear rain
    rn = pose(0, READY) + pose(0.4, {"arm_r": (-175, 0, 0), "forearm_r": (0, 0, 0), "spear": (0, 0, 0), "head": (-35, 0, 0),
                                    "jaw": (25, 0, 0), "torso": (-12, 0, 0)})
    rn += pose(1.0, {"arm_r": (-178, 0, 0), "head": (-38, 0, 0), "torso": (-14, 0, 0)})
    rn += pose(1.2, {"arm_r": (-80, 0, -10), "forearm_r": (-10, 0, 0), "spear": (90, 0, 0), "head": (0, 0, 0), "torso": (12, 0, 0)},
               linear=True)
    rn += pose(1.5, READY) + rest(1.5, ["torso", "head", "jaw"])
    anim("rain", 1500, 1.5, False, rn)

    # stun
    st = []
    for t, sw in ((0, -1), (1.0, 1), (2.0, -1)):
        for s in ("l", "r"):
            st += [k(t, f"horse_{s}", (14, 0, 6 * sw if s == "l" else -6 * sw), (0, 4.5, 0)), k(t, f"neck_{s}", (30, 0, 0)),
                   k(t, f"skull_{s}", (20, 0, 0))] + horse_legs(t, s, fl=-80, fr=-80, bl=10, br=10, knee=90)
        st += pose(t, {"torso": (40, 0, 4 * sw), "head": (30, 0, 0), "arm_r": (-20, 0, 30), "forearm_r": (-20, 0, 0),
                       "arm_l": (-50, 0, -10), "spear": (30, 0, 0), "jaw": (20, 0, 0)})
        st += [k(t, "car", (8, 0, 3 * sw), (0, 1.5, 0))]
    anim("stun", 1600, 2.0, True, st)

    # roar
    ro = pose(0, READY) + rest(0, HORSE)
    ro += rear(0.4, "l") + rear(0.4, "r") + pose(0.4, {"arm_r": (-165, 0, 25), "arm_l": (-140, 0, -40), "forearm_l": (-20, 0, 0),
                                                       "head": (-30, 0, 0), "jaw": (35, 0, 0), "torso": (-10, 0, 0)})
    for t in (0.6, 0.8, 1.0):
        ro += pose(t, {"head": (-30 + rnd.uniform(-5, 5), rnd.uniform(-8, 8), 0), "jaw": (30 + rnd.uniform(-6, 6), 0, 0)})
    ro += rear(1.1, "l") + rear(1.1, "r")
    ro += pose(1.5, READY) + rest(1.5, HORSE + ["head", "jaw", "torso"])
    anim("roar", 1700, 1.5, False, ro)

    # death: the chariot breaks apart, he climbs down, kneels and sinks into dust
    de = pose(0, READY) + rest(0, HORSE + CAR) + pose(0, {"body": ((0, 0, 0), (0, RIDE, 0))})
    de += pose(0.3, {"head": (-30, 0, 0), "jaw": (35, 0, 0), "arm_r": (-120, 0, 30)}) + rear(0.3, "l") + rear(0.3, "r")
    de += pose(0.8, {"wheel_l": ((0, 0, 40), (8, 6, 4)), "wheel_r": ((0, 0, -55), (-8, 6, -3)), "car": ((10, 0, -12), (0, 3, 0)),
                     "pole": ((25, 0, 0), (0, 4, 0))})
    for s, sx in (("l", 1), ("r", -1)):
        de += [k(0.9, f"horse_{s}", (20, 0, sx * 40), (sx * 3, 8, 0)), k(0.9, f"neck_{s}", (60, 0, 0)),
               k(0.9, f"skull_{s}", (40, 0, 0))] + horse_legs(0.9, s, fl=-90, fr=-80, bl=60, br=70, knee=110)
        de += [k(1.4, f"horse_{s}", (25, 0, sx * 70), (sx * 5, 13, 0)), k(1.4, f"neck_{s}", (80, 0, 0))]
        de += [k(5.0, f"horse_{s}", (25, 0, sx * 70), (sx * 5, 26, 0))]
    de += pose(1.2, {"wheel_l": ((0, 0, 85), (12, 10, 6)), "wheel_r": ((0, 0, -88), (-12, 10, -4)), "car": ((14, 0, -16), (0, 6, 0)),
                     "pole": ((32, 0, 0), (0, 8, 0))})
    de += pose(1.2, {"body": ((0, 0, 0), (0, RIDE + 3, -3)), "torso": (25, 0, 0), "head": (20, 0, 0), "jaw": (10, 0, 0)})
    de += pose(1.7, {"body": ((0, 0, 0), (0, 0, -7)), "leg_r": (-30, 0, 0), "leg_l": (10, 0, 0), "torso": (10, 0, 0)})
    kneel = {"body": ((0, 0, 0), (0, 5.5, -7)), "leg_r": (-80, 0, 0), "shin_r": (80, 0, 0), "leg_l": (10, 0, 0),
             "shin_l": (90, 0, 0), "torso": (20, 0, 0), "head": (30, 0, 0), "jaw": (20, 0, 0), "arm_r": (-30, 0, 20),
             "forearm_r": (-20, 0, 0), "spear": (-40, 0, 0), "arm_l": (-10, 0, -20), "forearm_l": (-30, 0, 0)}
    de += pose(2.4, kneel) + pose(3.0, kneel)
    sunk = dict(kneel)
    sunk["body"] = ((0, 0, 0), (0, 30, -7))
    sunk["torso"] = (35, 0, 0)
    de += pose(5.0, sunk)
    de += pose(5.0, {"wheel_l": ((0, 0, 85), (12, 26, 6)), "wheel_r": ((0, 0, -88), (-12, 26, -4)), "car": ((14, 0, -16), (0, 22, 0)),
                     "pole": ((32, 0, 0), (0, 22, 0))})
    anim("death", 1800, 5.0, False, de)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, glow = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
    paint(tex, glow, random.Random(29))
    tex.save(os.path.join(tex_dir, "barrow_chieftain.png"))
    glow.save(os.path.join(tex_dir, "barrow_chieftain_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "barrow_chieftain.png"), TEX_W, TEX_H, "barrow_chieftain")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved barrow chieftain parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
