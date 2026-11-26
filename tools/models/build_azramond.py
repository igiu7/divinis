"""
Builds Azramond, the Lich King (boss.Azramond) in Blender: a skeleton king floating over the floor in tattered royal
robes, a tall golden crown set with green stones, a bone staff topped with a skull and a green crystal; in his second
phase great wings of shadow.

  skull       with a hinged jaw; the crown (a band, eight points, a green stone at the front)
  torso       the bare ribcage and spine between the open sides of a purple robe trimmed with gold, a high stiff
              collar standing behind the head, pauldrons of bone
  arms        bone, ragged sleeves at the forearms, rings on the fingers; the staff in the right hand
  skirt       the robe below the waist, hanging in tatters - no legs, he floats
  wings       two wings of three joints each, bone struts and torn membranes of shadow (shown in phase 2)

One 256x256 texture in the Minecraft style (azramond.png) and its glow (azramond_glow.png): the eyes, the crown's
stones, the staff's crystal; in phase 2 (azramond_p2_glow.png) the crown burns with green fire too.

  blender -b --python tools/models/build_azramond.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/AzramondModelData.java. Model space: the ground at y 24, the front -z;
client.AzramondRenderer draws it 1.6 times its pixels; the boss floats a block above the floor.

Animations (seconds; boss/Azramond.java is timed to them):
  sleep     4.0 loop  slumped on his throne, the crown askew, the staff leaning on his shoulder
  wake      5.0       his eyes kindle (0.5), the head rises, he sets the crown straight (1.6-2.2), stands up from the
                      throne (2.2-3.2), lifts the staff high (3.5-4.2)
  idle      3.0 loop  floating, the robe stirring, the wings (when there) breathing
  drift     2.0 loop  floating along, leaning into it, the robe trailing
  fire      1.6       draws back and thrusts the staff forward at 0.8 (soul fire)
  raise     2.0       raises both hands and brings them down, palms to the floor, at 1.0 (the dead rise)
  frost     1.8       swings the staff round overhead and drives it down at 1.2 (a ring of ice)
  dance     1.8       strikes the staff's butt down at 0.9 (spikes of bone)
  lands     2.0       arms spread wide, head thrown back at 1.0 (the land of the dead)
  shades    2.0       reaches out with both hands at 1.0 (the shades of the fallen)
  curse     1.4       points at a foe with his left hand at 0.8
  transform 6.0       rises, staff high, smashes it down at 1.5 (the throne breaks), writhes (2-3.5), the wings burst
                      out (3.5-4.2), the crown kindles (4.2), screams (4.5-5.5)
  roar      1.5
  death     5.0       staggers, sinks (0-1.2), the staff falls (1.2-1.8), the crown falls from his head at 1.6 and
                      rolls away over the floor (to 3.2), he crumbles into his robe (2.5-4.5)
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
    part("body", None, (0, 13, 0))
    # the skirt of the robe, in tatters: three layers, each wider and more torn
    part("skirt", "body", (0, 0, 0))
    box("skirt", (-4.6, -0.5, -3.0), (9.2, 4.5, 6.0), "robe")
    box("skirt", (-5.2, 4.0, -3.4), (10.4, 4.0, 6.8), "robe")
    box("skirt", (-5.8, 8.0, -3.8), (11.6, 3.0, 7.6), "robe_tatter")
    box("skirt", (-5.3, -0.8, -3.2), (10.6, 1.2, 6.4), "gold")          # the girdle
    box("skirt", (-1.0, 0.4, -3.7), (2.0, 7.5, 0.6), "gold")            # the front panel's trim
    # the torso: ribs and spine between the robe's open sides; the high collar; the pauldrons
    part("torso", "body", (0, 0, 0))
    box("torso", (-3.4, -10, -2.0), (6.8, 7.5, 4.0), "ribs")
    box("torso", (-0.8, -11, 1.2), (1.6, 11, 1.4), "bone")              # the spine
    box("torso", (-2.6, -2.6, -1.6), (5.2, 2.0, 3.2), "bone")           # the pelvis' top
    box("torso", (-4.6, -11, -2.4), (1.6, 10.5, 5.0), "robe")
    box("torso", (3.0, -11, -2.4), (1.6, 10.5, 5.0), "robe")
    box("torso", (-4.6, -11, 2.0), (9.2, 10.5, 1.0), "robe")
    box("torso", (-4.8, -11.2, -2.6), (0.6, 10.8, 0.6), "gold")         # the robe's edges
    box("torso", (4.2, -11.2, -2.6), (0.6, 10.8, 0.6), "gold")
    box("torso", (-5.4, -15, 0.6), (10.8, 4.4, 1.4), "collar")          # the standing collar
    box("torso", (-5.6, -15.4, 0.4), (11.2, 0.8, 1.8), "gold")
    # the head
    part("head", "torso", (0, -11.6, 0))
    box("head", (-3.0, -6.4, -3.0), (6, 6, 6), "skull")
    box("head", (-2.2, -1.0, -3.2), (4.4, 1.0, 2.0), "skull")            # the upper teeth
    part("jaw", "head", (0, -0.6, 1.0))
    box("jaw", (-2.4, 0, -4.2), (4.8, 1.4, 4.0), "jaw")
    part("crown", "head", (0, -6.2, 0))
    box("crown", (-3.4, -1.8, -3.4), (6.8, 1.8, 6.8), "crown")
    for (x, z, h) in ((-3.0, -3.0, 2.6), (3.0, -3.0, 2.6), (-3.0, 3.0, 2.6), (3.0, 3.0, 2.6),
                      (0, -3.2, 3.6), (0, 3.2, 3.0), (-3.2, 0, 3.0), (3.2, 0, 3.0)):
        box("crown", (x - 0.5, -1.8 - h, z - 0.5), (1.0, h, 1.0), "crown_point")
    box("crown", (-0.7, -1.5, -3.8), (1.4, 1.2, 0.6), "gem")
    box("crown", (-2.6, -1.3, -3.7), (0.8, 0.8, 0.4), "gem")
    box("crown", (1.8, -1.3, -3.7), (0.8, 0.8, 0.4), "gem")
    # the arms
    for s, sx in (("r", -1), ("l", 1)):
        arm, fore = f"arm_{s}", f"forearm_{s}"
        part(arm, "torso", (sx * 5.4, -10.2, 0))
        box(arm, (-2.2, -1.8, -2.6), (4.4, 2.8, 5.2), "pauldron")
        box(arm, (-1.6, -2.6, -1.0), (3.2, 1.0, 2.0), "pauldron")        # a ridge of bone over it
        box(arm, (-0.7, 0.6, -0.7), (1.4, 5.6, 1.4), "bone")
        part(fore, arm, (0, 6.0, 0))
        box(fore, (-1.7, 0, -1.7), (3.4, 3.2, 3.4), "robe_tatter")       # the ragged sleeve
        box(fore, (-0.6, 2.6, -0.6), (1.2, 3.0, 1.2), "bone")
        box(fore, (-1.0, 5.4, -1.0), (2.0, 1.6, 2.0), "bone")            # the hand
        box(fore, (-1.1, 6.6, -1.1), (2.2, 0.5, 2.2), "gold")            # rings
        box(fore, (-0.8, 7.0, -0.6), (1.6, 1.2, 1.2), "bone")            # the fingers
    # the staff in the right hand: a shaft of old bone, a skull at its head, a green crystal held in bone claws
    part("staff", "forearm_r", (0, 6.4, -0.2))
    box("staff", (-0.5, -19, -0.5), (1.0, 30, 1.0), "shaft")
    for y in (-12, -4, 4):
        box("staff", (-0.65, y, -0.65), (1.3, 0.8, 1.3), "gold")
    box("staff", (-1.7, -22.4, -1.7), (3.4, 3.4, 3.4), "skull")
    box("staff", (-1.0, -25.2, -1.0), (2.0, 2.8, 2.0), "gem")
    for (x, z) in ((-1.4, 0), (1.4, 0), (0, -1.4), (0, 1.4)):
        box("staff", (x - 0.3, -26.0, z - 0.3), (0.6, 3.8, 0.6), "bone")
    # the wings of shadow (phase 2): three joints each
    for s, sx in (("l", 1), ("r", -1)):
        w1, w2, w3 = f"wing_{s}", f"wing2_{s}", f"wing3_{s}"
        part(w1, "torso", (sx * 2.2, -9.0, 2.6), (10, sx * 25, sx * -25))
        x0 = 0 if sx > 0 else -9
        box(w1, (x0, -0.6, -0.6), (9, 1.2, 1.2), "bone")
        box(w1, (x0, 0.4, -0.2), (9, 10, 0.4), "wing")
        part(w2, w1, (sx * 9, 0, 0), (0, sx * 15, sx * 30))
        x0 = 0 if sx > 0 else -8
        box(w2, (x0, -0.5, -0.5), (8, 1.0, 1.0), "bone")
        box(w2, (x0, 0.4, -0.2), (8, 13, 0.4), "wing")
        part(w3, w2, (sx * 8, 0, 0), (0, sx * 10, sx * 35))
        x0 = 0 if sx > 0 else -7
        box(w3, (x0, -0.4, -0.4), (7, 0.8, 0.8), "bone")
        box(w3, (x0, 0.3, -0.2), (7, 10, 0.4), "wing")


# ---------------------------------------------------------------- painting
BONE = [(118, 110, 92), (150, 142, 118), (180, 172, 146), (206, 198, 172), (226, 220, 198)]
GOLD = [(120, 84, 26), (170, 124, 42), (214, 170, 72), (244, 212, 130)]
ROBE = [(26, 14, 34), (40, 22, 52), (58, 32, 74), (78, 46, 98)]
GREEN = (90, 255, 120)
SHADOW = [(10, 8, 14), (20, 14, 28), (34, 24, 46)]
FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.25, "bottom": -1.0}


def pick(r, level):
    return r[max(0, min(len(r) - 1, int(round(level))))]


def paint(tex, glow, glow2, rnd):
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            fw, fh = max(1, fw), max(1, fh)
            light = FACE_LIGHT[face]
            for y in range(fh):
                for x in range(fw):
                    c, g, g2 = paint_px(mat, x, y, fw, fh, face, light, rnd)
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if g:
                        glow.set(x0 + x, y0 + y, g, 0.0)
                    if g2 or g:
                        glow2.set(x0 + x, y0 + y, g2 or g, 0.0)


def paint_px(mat, x, y, w, h, face, light, rnd):
    n = rnd.uniform(-0.3, 0.3)
    if mat == "skull":
        if face == "front" and w == 6 and h == 6:
            if y in (2, 3) and x in (1, 2, 3, 4):   # the eye sockets, green fire deep in them
                lit = y == 3 and x in (1, 4) or y == 2 and x in (2, 3)
                return (8, 10, 8), (GREEN if lit else (30, 110, 50)), None
            if y == 4 and x in (2, 3):
                return (30, 26, 22), None, None
        if face == "front" and w == 4 and h == 1:   # the teeth
            return (BONE[4] if x % 2 == 0 else (40, 34, 28)), None, None
        crack = (x * 5 + y * 3) % 13 == 0
        return pick(BONE, 2.2 + light + n - (1.4 if crack else 0)), None, None
    if mat == "jaw":
        if face == "top" and y == 0:
            return (BONE[4] if x % 2 == 0 else (40, 34, 28)), None, None
        return pick(BONE, 1.8 + light + n), None, None
    if mat == "bone":
        joint = h > 4 and y % 5 == 4
        return pick(BONE, 2.0 + light + n - (1.0 if joint else 0)), None, None
    if mat == "ribs":
        if face in ("front", "back"):
            if y % 2 == 1:
                return (12, 10, 14), None, None          # the dark between the ribs
            if x == w // 2:
                return pick(BONE, 3), None, None          # the sternum
        return pick(BONE, 2.0 + light + n), None, None
    if mat == "pauldron":
        return pick(BONE, 1.8 + light + n + (0.8 if y == 0 else 0)), None, None
    if mat in ("robe", "robe_tatter", "collar"):
        if mat == "robe_tatter" and y >= h - 2 and (x * 5 + y) % 4 < (y - h + 3):
            return None, None, None                       # torn
        if mat == "collar" and face == "front" and (y == 0 or (x % 4 == 2 and y == 2)):
            return pick(GOLD, 2), None, None
        fold = x % 3 == 0
        return pick(ROBE, 1.6 + light + n - (0.8 if fold else 0)), None, None
    if mat == "gold":
        return pick(GOLD, 1.8 + light + n + (0.6 if y == 0 else 0)), None, None
    if mat == "crown":
        if face in ("front", "back", "left", "right") and y == 1 and x % 3 == 1:
            return (40, 200, 90), (60, 255, 110), None   # small stones round the band
        return pick(GOLD, 2.0 + light + n + (0.8 if y == 0 else 0)), None, (120, 255, 140) if y == h - 1 else None
    if mat == "crown_point":
        return pick(GOLD, 2.2 + light + n), None, (110, 255, 130)   # phase 2: the crown burning
    if mat == "gem":
        top = y == 0 or x == 0
        c = (160, 255, 170) if top else (50, 210, 90)
        return c, c, c
    if mat == "shaft":
        return pick([(52, 44, 36), (74, 62, 50), (96, 84, 68)], 1.2 + light * 0.6 + n - (0.8 if y % 4 == 0 else 0)), None, None
    if mat == "wing":
        # a membrane of shadow, torn: holes and ragged edges, faint green veins
        if (x * 7 + y * 11) % 17 == 0 or (y >= h - 3 and (x * 3 + y) % 5 < y - h + 4):
            return None, None, None
        if face in ("front", "back") and (x + y) % 9 == 0:
            return (30, 70, 40), None, (40, 160, 70)
        return pick(SHADOW, 1 + n * 2), None, None
    return pick(BONE, 2 + light + n), None, None


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


ALL = ["body", "skirt", "torso", "head", "jaw", "crown", "arm_r", "forearm_r", "staff", "arm_l", "forearm_l",
       "wing_l", "wing2_l", "wing3_l", "wing_r", "wing2_r", "wing3_r"]


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


READY = {"arm_r": (-20, 0, 8), "forearm_r": (-30, 0, 0), "staff": (25, 0, 0), "arm_l": (-15, 0, -10), "forearm_l": (-35, 0, 0)}
# slumped on the throne: the skirt forward over the seat, the head bowed, the crown askew, the staff on the shoulder
SEATED = {
    "body": ((0, 0, 0), (0, 1.0, 1.5)), "skirt": (-78, 0, 0), "torso": (14, 0, 0), "head": (24, 0, 14), "jaw": (14, 0, 0),
    "crown": ((8, 0, 22), (1.2, 0.6, 0)),
    "arm_r": (-30, 0, 18), "forearm_r": (-50, 0, 0), "staff": ((55, 0, -20), (0, 0, 0)),
    "arm_l": (-35, 0, -14), "forearm_l": (-40, 0, 0),
}
FOLDED = {"wing_l": ((40, 70, -60), (-2, 2, -2)), "wing2_l": (0, 60, -140), "wing3_l": (0, 30, -140),
          "wing_r": ((40, -70, 60), (2, 2, -2)), "wing2_r": (0, -60, 140), "wing3_r": (0, -30, 140)}
SPREAD = {"wing_l": (0, -10, 10), "wing2_l": (0, 0, -10), "wing3_l": (0, 0, -10),
          "wing_r": (0, 10, -10), "wing2_r": (0, 0, 10), "wing3_r": (0, 0, 10)}


def wings(t, d, linear=False):
    return pose(t, d, linear)


def animations():
    rnd = random.Random(31)

    anim("sleep", 0, 4.0, True, pose(0, SEATED) + pose(2.0, {"torso": (16, 0, 0), "head": (26, 0, 14)}) + pose(4.0, SEATED)
         + wings(0, FOLDED) + wings(4.0, FOLDED))

    wk = pose(0, SEATED) + wings(0, FOLDED)
    wk += pose(0.5, {"jaw": (25, 0, 0)}) + pose(0.8, {"jaw": (6, 0, 0)})
    wk += pose(1.6, {"head": (0, 0, 14), "torso": (4, 0, 0), "crown": ((8, 0, 22), (1.2, 0.6, 0))})
    wk += pose(1.9, {"arm_l": (-160, 20, -30), "forearm_l": (-60, 0, 0), "head": (0, 0, 8)})
    wk += pose(2.2, {"crown": ((0, 0, 0), (0, 0, 0)), "head": (-5, 0, 0), "arm_l": (-150, 20, -30)})
    stand = dict(READY)
    stand.update({"body": ((0, 0, 0), (0, -1.0, 0)), "skirt": (0, 0, 0), "torso": (0, 0, 0), "head": (0, 0, 0), "jaw": (0, 0, 0)})
    wk += pose(3.2, stand)
    wk += pose(3.6, {"arm_r": (-150, 0, 20), "forearm_r": (-10, 0, 0), "staff": (0, 0, 0), "head": (-25, 0, 0), "jaw": (20, 0, 0)})
    wk += pose(4.2, {"arm_r": (-175, 0, 10), "forearm_r": (0, 0, 0), "head": (-30, 0, 0), "jaw": (35, 0, 0),
                     "arm_l": (-60, 0, -40)}, linear=True)
    wk += pose(5.0, READY) + rest(5.0, ["body", "skirt", "torso", "head", "jaw", "crown"]) + wings(5.0, FOLDED)
    anim("wake", 120, 5.0, False, wk)

    idl = pose(0, READY) + rest(0, ["body", "skirt", "torso", "head", "jaw"]) + wings(0, SPREAD)
    idl += [k(1.5, "body", (0, 0, 0), (0, -0.8, 0)), k(1.5, "skirt", (6, 0, 3)), k(1.5, "torso", (-2, 3, 0)),
            k(1.5, "head", (-3, -6, 0)), k(1.0, "jaw", (10, 0, 0)), k(1.3, "jaw", (0, 0, 0)), k(1.5, "arm_l", (-20, 0, -14))]
    idl += wings(1.5, {"wing_l": (0, -16, 16), "wing2_l": (0, 0, -16), "wing_r": (0, 16, -16), "wing2_r": (0, 0, 16)})
    idl += pose(3.0, READY) + rest(3.0, ["body", "skirt", "torso", "head", "jaw"]) + wings(3.0, SPREAD)
    anim("idle", 240, 3.0, True, idl)

    dr = []
    for t, a in ((0, 0), (1.0, 1), (2.0, 0)):
        dr += pose(t, {"body": ((8, 0, 0), (0, -0.5 * a, 0)), "skirt": (30 + 6 * a, 0, 0), "torso": (6, 0, 0)})
        dr += pose(t, READY)
        dr += wings(t, {"wing_l": (0, -25 + 8 * a, 25), "wing2_l": (0, 0, -20), "wing_r": (0, 25 - 8 * a, -25), "wing2_r": (0, 0, 20)})
    anim("drift", 360, 2.0, True, dr)

    fi = pose(0, READY) + pose(0.6, {"arm_r": (-120, -20, 30), "forearm_r": (-30, 0, 0), "staff": (60, 0, 0), "torso": (0, -25, 0),
                                    "head": (0, 15, 0)})
    fi += pose(0.8, {"arm_r": (-90, 0, 0), "forearm_r": (0, 0, 0), "staff": (90, 0, 0), "torso": (8, 18, 0), "head": (0, -10, 0),
                     "jaw": (25, 0, 0)}, linear=True)
    fi += pose(1.2, {"arm_r": (-88, 0, 0), "staff": (90, 0, 0)}) + pose(1.6, READY) + rest(1.6, ["torso", "head", "jaw"])
    anim("fire", 480, 1.6, False, fi)

    ra = pose(0, READY) + pose(0.6, {"arm_r": (-160, 0, 30), "arm_l": (-160, 0, -30), "forearm_r": (-10, 0, 0), "forearm_l": (-10, 0, 0),
                                    "staff": (0, 0, 0), "head": (-25, 0, 0), "jaw": (25, 0, 0), "torso": (-8, 0, 0)})
    ra += pose(1.0, {"arm_r": (-60, 0, 40), "arm_l": (-60, 0, -40), "forearm_r": (-20, 0, 0), "forearm_l": (-20, 0, 0),
                     "head": (20, 0, 0), "torso": (15, 0, 0), "jaw": (5, 0, 0)}, linear=True)
    ra += pose(1.5, {"arm_r": (-60, 0, 40), "arm_l": (-60, 0, -40)}) + pose(2.0, READY) + rest(2.0, ["torso", "head", "jaw"])
    anim("raise", 600, 2.0, False, ra)

    fr = pose(0, READY)
    for i, t in enumerate((0.3, 0.55, 0.8, 1.0)):
        fr += pose(t, {"arm_r": (-170, i * 90 - 135, 10), "forearm_r": (0, 0, 0), "staff": (0, 0, 0), "head": (-15, 0, 0)})
    fr += pose(1.2, {"arm_r": (-40, 0, 10), "forearm_r": (-30, 0, 0), "staff": ((-20, 0, 0), (0, 3, 0)), "torso": (20, 0, 0),
                     "head": (15, 0, 0), "body": ((0, 0, 0), (0, 1.5, 0))}, linear=True)
    fr += pose(1.8, READY) + rest(1.8, ["torso", "head", "body"])
    anim("frost", 720, 1.8, False, fr)

    da = pose(0, READY) + pose(0.6, {"arm_r": (-150, 0, 10), "forearm_r": (-20, 0, 0), "staff": (-10, 0, 0), "head": (-15, 0, 0)})
    da += pose(0.9, {"arm_r": (-50, 0, 10), "forearm_r": (-30, 0, 0), "staff": ((-30, 0, 0), (0, 4, 0)), "torso": (22, 0, 0),
                     "head": (20, 0, 0), "jaw": (20, 0, 0)}, linear=True)
    da += pose(1.4, {"torso": (18, 0, 0)}) + pose(1.8, READY) + rest(1.8, ["torso", "head", "jaw"])
    anim("dance", 840, 1.8, False, da)

    la = pose(0, READY) + pose(1.0, {"arm_r": (-90, 0, 80), "arm_l": (-90, 0, -80), "forearm_r": (0, 0, 0), "forearm_l": (0, 0, 0),
                                    "staff": (0, 0, 0), "head": (-40, 0, 0), "jaw": (35, 0, 0), "torso": (-15, 0, 0),
                                    "body": ((0, 0, 0), (0, -2, 0))})
    la += pose(1.5, {"head": (-42, 0, 0)}) + pose(2.0, READY) + rest(2.0, ["torso", "head", "jaw", "body"])
    la += wings(1.0, {"wing_l": (0, -40, 50), "wing2_l": (0, 0, -30), "wing_r": (0, 40, -50), "wing2_r": (0, 0, 30)}) + wings(2.0, SPREAD)
    anim("lands", 960, 2.0, False, la)

    sh = pose(0, READY) + pose(1.0, {"arm_r": (-95, 20, 10), "arm_l": (-95, -20, -10), "forearm_r": (0, 0, 0), "forearm_l": (0, 0, 0),
                                    "staff": (80, 0, 0), "head": (5, 0, 0), "jaw": (25, 0, 0), "torso": (10, 0, 0)})
    sh += pose(1.5, {"arm_l": (-100, -30, -10)}) + pose(2.0, READY) + rest(2.0, ["torso", "head", "jaw"])
    anim("shades", 1080, 2.0, False, sh)

    cu = pose(0, READY) + pose(0.5, {"arm_l": (-60, 0, -50), "forearm_l": (-60, 0, 0), "torso": (0, -20, 0)})
    cu += pose(0.8, {"arm_l": (-95, 0, 0), "forearm_l": (0, 0, 0), "torso": (5, 15, 0), "head": (5, 10, 0), "jaw": (20, 0, 0)}, linear=True)
    cu += pose(1.4, READY) + rest(1.4, ["torso", "head", "jaw"])
    anim("curse", 1200, 1.4, False, cu)

    tf = pose(0, READY) + wings(0, FOLDED)
    tf += pose(0.8, {"arm_r": (-178, 0, 0), "forearm_r": (0, 0, 0), "staff": (0, 0, 0), "head": (-30, 0, 0), "jaw": (25, 0, 0),
                     "body": ((0, 0, 0), (0, -6, 0)), "arm_l": (-120, 0, -40)})
    tf += pose(1.5, {"arm_r": (-40, 0, 10), "forearm_r": (-20, 0, 0), "staff": ((-30, 0, 0), (0, 4, 0)), "torso": (30, 0, 0),
                     "head": (25, 0, 0), "body": ((0, 0, 0), (0, 0, 0))}, linear=True)
    for t in (2.0, 2.3, 2.6, 2.9, 3.2, 3.5):
        tf += pose(t, {"torso": (rnd.uniform(-20, 30), rnd.uniform(-20, 20), rnd.uniform(-15, 15)),
                       "head": (rnd.uniform(-30, 20), rnd.uniform(-25, 25), rnd.uniform(-20, 20)), "jaw": (rnd.uniform(5, 35), 0, 0),
                       "arm_l": (rnd.uniform(-150, -40), 0, rnd.uniform(-60, -10)), "arm_r": (rnd.uniform(-150, -40), 0, rnd.uniform(10, 60))})
    tf += wings(3.5, FOLDED)
    tf += wings(4.2, {"wing_l": (0, -45, 55), "wing2_l": (0, 0, -35), "wing3_l": (0, 0, -25),
                      "wing_r": (0, 45, -55), "wing2_r": (0, 0, 35), "wing3_r": (0, 0, 25)}, linear=True)
    tf += pose(4.2, {"torso": (-20, 0, 0), "head": (-35, 0, 0), "jaw": (40, 0, 0), "arm_l": (-100, 0, -70), "arm_r": (-100, 0, 70),
                     "body": ((0, 0, 0), (0, -3, 0))}, linear=True)
    tf += pose(5.5, {"torso": (-15, 0, 0), "head": (-30, 0, 0), "jaw": (35, 0, 0)})
    tf += pose(6.0, READY) + rest(6.0, ["torso", "head", "jaw", "body"]) + wings(6.0, SPREAD)
    anim("transform", 1320, 6.0, False, tf)

    ro = pose(0, READY) + pose(0.4, {"arm_r": (-170, 0, 20), "arm_l": (-140, 0, -40), "head": (-35, 0, 0), "jaw": (40, 0, 0),
                                    "torso": (-12, 0, 0)}) + wings(0.4, {"wing_l": (0, -45, 55), "wing_r": (0, 45, -55)})
    for t in (0.7, 1.0):
        ro += pose(t, {"head": (-35 + rnd.uniform(-5, 5), rnd.uniform(-8, 8), 0), "jaw": (36 + rnd.uniform(-5, 5), 0, 0)})
    ro += pose(1.5, READY) + rest(1.5, ["torso", "head", "jaw"]) + wings(1.5, SPREAD)
    anim("roar", 1460, 1.5, False, ro)

    # death: sinks, the staff falls, the crown falls and rolls away, he crumbles into his robe
    de = pose(0, READY) + wings(0, SPREAD)
    de += pose(0.4, {"torso": (-15, 0, 10), "head": (-30, 10, 0), "jaw": (40, 0, 0), "arm_r": (-120, 0, 40), "arm_l": (-120, 0, -40)})
    de += pose(1.2, {"body": ((0, 0, 0), (0, 5, 0)), "torso": (25, 0, 0), "head": (20, 0, 0), "jaw": (20, 0, 0),
                     "arm_r": (-30, 0, 30), "arm_l": (-30, 0, -30), "skirt": (0, 0, 0)})
    de += [k(1.2, "staff", (25, 0, 0), (0, 0, 0)), k(1.8, "staff", (25, 0, 100), (4, 6, 0), True), k(5.0, "staff", (25, 0, 100), (4, 6, 0))]
    # the crown: off the head and rolling away over the floor (the floor is some 20 pixels below his head)
    de += [k(1.5, "crown", (0, 0, 0), (0, 0, 0)), k(1.8, "crown", (-30, 0, 20), (0, 6, -4)),
           k(2.2, "crown", (-90, 0, 90), (0, 19, -8), True), k(2.6, "crown", (-90, 0, 270), (0, 19, -13)),
           k(3.0, "crown", (-90, 0, 420), (0, 19, -17)), k(3.2, "crown", (-90, 0, 460), (0, 19.5, -18)),
           k(5.0, "crown", (-90, 0, 460), (0, 19.5, -18))]
    de += pose(2.5, {"body": ((0, 0, 0), (0, 5, 0)), "torso": (30, 0, 0), "head": (25, 0, 0)})
    de += pose(4.5, {"body": ((0, 0, 0), (0, 13, 0)), "torso": (60, 0, 0), "head": (50, 0, 0), "arm_r": (10, 0, 40), "arm_l": (10, 0, -40),
                     "skirt": (-20, 0, 0)})
    de += wings(2.0, {"wing_l": (40, 40, -60), "wing2_l": (0, 30, -80), "wing_r": (40, -40, 60), "wing2_r": (0, -30, 80)})
    de += wings(4.5, FOLDED)
    de += pose(5.0, {"body": ((0, 0, 0), (0, 14, 0))})
    anim("death", 1500, 5.0, False, de)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, glow, glow2 = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
    paint(tex, glow, glow2, random.Random(41))
    tex.save(os.path.join(tex_dir, "azramond.png"))
    glow.save(os.path.join(tex_dir, "azramond_glow.png"))
    glow2.save(os.path.join(tex_dir, "azramond_p2_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "azramond.png"), TEX_W, TEX_H, "azramond")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved azramond parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
