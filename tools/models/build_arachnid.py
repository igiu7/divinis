"""
Builds the Arachnid (boss.Arachnid) in Blender: a great hunting spider, some five and a half blocks across the legs.

  cephalothorax  a low carapace in two tiers with a ridge down its middle, the sternum beneath
  head           raised at the front: eight eyes (two great principal eyes, two side eyes, four on top), the
                 chelicerae hanging below with their curved fangs, the pedipalps beside them
  abdomen        a big round abdomen (stacked boxes) tilted up behind, tufts of hair, the spinnerets at its tip
  legs           four pairs, each hip - coxa - femur - tibia - tarsus, joints thickened, spines along the femur and
                 tibia; the feet reach the ground (the tarsus' angle is worked out so they do)
  cocoon         the silk it sleeps wrapped in, hanging from the ceiling (shown only while asleep, client.ArachnidModel)

Textures (256x256, Minecraft-style pixel painting): phase 1 (arachnid.png) dark brown, banded legs, tan chevrons on
the abdomen; phase 2 (arachnid_p2.png) near black with blood-red markings, and its glow (arachnid_p2_glow.png): the
eyes and the markings; the molt it sheds (arachnid_molt.png): the phase 1 painting pale and see-through.

  blender -b --python tools/models/build_arachnid.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/ArachnidModelData.java. Model space: the ground at y 24, the front -z,
its right -x; client.ArachnidRenderer draws it twice its pixels (2.3 times in phase 2).

Animations (seconds; boss/Arachnid.java is timed to them):
  sleep     4.0 loop  wrapped in its cocoon, hanging head down, swaying
  wake      4.0       the cocoon shudders (0-1.2), it tears out (1.2), legs unfold, it is let down on its thread (the
                      server lowers it 1.6-2.8), turns level (2.4-2.9), lands, rears up and screams (3.1-4.0)
  idle      2.0 loop  breathing, palps and chelicerae twitching
  walk      1.0 loop  the alternating tetrapod gait (two sets of four legs)
  pounce    2.0       crouches (0-1.0), springs (1.0-1.4; the server flies it), lands at 1.4
  spit      1.6       rears its head up, fangs open, spits three webs at 0.9
  stomp     2.2       front legs up, slams them down at 0.9; tips forward, the hind legs stamp at 1.5
  climb     0.6       leaps up onto its thread, turning head down
  hang      2.0 loop  hanging head down, legs spread to grab
  fall      1.0       drops: turns level as it falls, lands at 0.3, settles
  lay       1.8       the abdomen rises and pumps, eggs flung out at 1.0
  shoot     1.6       the abdomen curls under it and fires a line of silk forward at 0.9
  rain      1.8       rears high, spits acid into the air at 0.9
  roar      1.5       rears up, front legs high and spread, fangs wide
  transform 5.0       climbs up onto its thread (0-0.5; the server lifts it), thrashes (0.6-2.0), splits its old skin (2.0), pulls its legs out (2.0-3.0),
                      lets itself down (3.5-4.3, turning level), lands at 4.3 and screams
  death     4.0       thrashes, rolls over onto its back (0.6-1.2), the legs curl up one after another (1.2-3.4)
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
    """pivot relative to the parent's pivot, in the parent's (rotated) frame"""
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat
    return name


# ---------------------------------------------------------------- the legs
SIDES = (("l", 1), ("r", -1))      # its left is +x
BODY_Y = 14                         # the body's pivot: 10 pixels above the ground
ATTACH = [(-4.6, 1.15), (-2.0, 1.0), (0.6, 1.0), (3.0, 1.1)]   # z on the thorax side, length factor
YAW = [50, 17, -15, -44]            # spread (degrees, + forward)
UP = [22, 52, -50]                  # coxa, femur, tibia: absolute angle above the horizontal
LENGTHS = [3.0, 10.5, 11.5, 10.0]   # coxa, femur, tibia, tarsus
THICK = [2.6, 2.2, 1.8, 1.2]
SEGS = ["coxa", "femur", "tibia", "tarsus"]
LEGS = []                           # (i, s, sx)
REST_UP = {}                        # (i, s) -> the four absolute angles


def leg(i, s, sx):
    z, f = ATTACH[i]
    lengths = [LENGTHS[0], LENGTHS[1] * f, LENGTHS[2] * f, LENGTHS[3] * f]
    # the tarsus' angle so the foot reaches the ground: the hip is 10 px up (the thorax side at the body's pivot)
    height = 24 - BODY_Y
    for a, L in zip(UP, lengths[:3]):
        height += L * math.sin(math.radians(a))
    down = max(-1.0, min(1.0, -(height - 0.4) / lengths[3]))
    angles = UP + [math.degrees(math.asin(down))]
    REST_UP[(i, s)] = angles
    hip = f"hip{i}{s}"
    part(hip, "thorax", (sx * 4.6, 0, z), (0, sx * YAW[i], 0))
    parent, prev = hip, 0.0
    for k, seg in enumerate(SEGS):
        name = f"{seg}{i}{s}"
        rel = angles[k] - prev
        prev = angles[k]
        pivot = (0, 0, 0) if k == 0 else (sx * lengths[k - 1], 0, 0)
        part(name, parent, pivot, (0, 0, -sx * rel))
        L, t = lengths[k], THICK[k]
        x0 = 0 if sx > 0 else -L
        box(name, (x0, -t / 2, -t / 2), (L, t, t), seg)
        if k in (1, 2):   # the joint at its end, thicker
            j = t + 0.6
            jx = L - 1.4 if sx > 0 else -L
            box(name, (jx, -j / 2, -j / 2), (1.4, j, j), "joint")
            # spines along the top, a pair at each third
            for n in (1, 2):
                px = L * n / 3
                sx0 = px - 0.3 if sx > 0 else -px - 0.3
                box(name, (sx0, -t / 2 - 1.4, -0.3), (0.6, 1.4, 0.6), "spine")
                box(name, (sx0, -0.3, t / 2), (0.6, 0.6, 1.1), "spine")
        if k == 3:   # the claw tuft at the foot
            cx = L - 0.6 if sx > 0 else -L - 0.4
            box(name, (cx, -0.9, -0.9), (1.0, 1.8, 1.8), "claw")
        parent = name
    LEGS.append((i, s, sx))


def model():
    part("body", None, (0, BODY_Y, 0))
    part("thorax", "body", (0, 0, 0))
    # the cephalothorax: two tiers and a ridge, the sternum beneath
    box("thorax", (-5, -2.5, -6.5), (10, 4.5, 11), "carapace")
    box("thorax", (-4, -3.6, -5.5), (8, 1.1, 9), "carapace_top")
    box("thorax", (-0.8, -4.1, -4.5), (1.6, 0.5, 7), "ridge")
    box("thorax", (-4, 2, -5), (8, 1.2, 8), "sternum")
    box("thorax", (-3, -2, 4.5), (6, 3.5, 1.5), "carapace")         # the pedicel to the abdomen
    # the head, raised at the front
    part("head", "thorax", (0, -1, -6.5), (-6, 0, 0))
    box("head", (-4, -3.2, -3.5), (8, 5.2, 4), "head")
    box("head", (-3, -3.9, -3.0), (6, 0.7, 3), "head_top")
    # eyes: two great principal eyes, two side eyes, four on top
    box("head", (-2.6, -2.4, -3.9), (1.8, 1.8, 0.6), "eye_big")
    box("head", (0.8, -2.4, -3.9), (1.8, 1.8, 0.6), "eye_big")
    box("head", (-3.9, -2.0, -3.7), (1.0, 1.0, 0.5), "eye")
    box("head", (2.9, -2.0, -3.7), (1.0, 1.0, 0.5), "eye")
    for ex, ez in ((-2.6, -2.8), (1.6, -2.8), (-1.6, -1.4), (0.6, -1.4)):
        box("head", (ex, -4.4, ez), (1.0, 0.6, 1.0), "eye")
    # the chelicerae and their fangs
    for s, sx in SIDES:
        ch = f"chel_{s}"
        part(ch, "head", (sx * 1.6, 1.6, -2.4), (-12, 0, 0))
        box(ch, (-1.3, 0, -1.3), (2.6, 4.2, 2.6), "chel")
        box(ch, (-0.9, -0.5, -1.5), (1.8, 1.2, 0.6), "chel_hair")
        fang = f"fang_{s}"
        part(fang, ch, (0, 4.0, -0.3), (-20, 0, sx * -30))
        box(fang, (-0.5, 0, -0.5), (1.0, 2.0, 1.0), "fang")
        box(fang, (-0.4 if sx > 0 else -0.4, 1.8, -1.2), (0.8, 0.8, 1.0), "fang_tip")
        # the pedipalps beside them
        palp = f"palp_{s}"
        part(palp, "head", (sx * 3.2, 1.2, -2.2), (-40, 0, sx * -12))
        box(palp, (-0.7, 0, -0.7), (1.4, 4.2, 1.4), "palp")
        palp2 = f"palp2_{s}"
        part(palp2, palp, (0, 4.0, 0), (50, 0, 0))
        box(palp2, (-0.8, 0, -0.8), (1.6, 3.0, 1.6), "palp_tip")
    # the abdomen: a round body of stacked boxes, raised behind
    part("abdomen", "thorax", (0, -1, 5.5), (-14, 0, 0))
    box("abdomen", (-6, -6, 0.5), (12, 12, 15), "abdomen")
    box("abdomen", (-7, -5, 2), (14, 10, 12), "abdomen_side")
    box("abdomen", (-5, -7, 2), (10, 14, 12), "abdomen_top")
    box("abdomen", (-4.5, -4.5, -0.3), (9, 9, 17.3), "abdomen_cap")
    box("abdomen", (-3, -3, 16.5), (6, 6, 1.2), "abdomen_cap")
    for (hx, hz) in ((-3.5, 4), (3, 6), (-2, 10), (2.5, 12), (0, 8)):
        box("abdomen", (hx - 0.4, -8.2, hz), (0.8, 1.2, 0.8), "tuft")
    part("spinnerets", "abdomen", (0, 1.5, 17.2), (25, 0, 0))
    box("spinnerets", (-1.6, -0.8, 0), (1.2, 1.4, 2.2), "spinneret")
    box("spinnerets", (0.4, -0.8, 0), (1.2, 1.4, 2.2), "spinneret")
    box("spinnerets", (-0.6, 0.2, 0), (1.2, 1.2, 1.6), "spinneret")
    for i in range(4):
        for s, sx in SIDES:
            leg(i, s, sx)
    # the cocoon it sleeps in (around the body, its long axis along the body; tied off where the thread starts)
    part("cocoon", "body", (0, 0, 0))
    box("cocoon", (-6.5, -7.5, -14), (13, 13, 38), "silk")
    box("cocoon", (-8, -6, -10), (16, 10, 30), "silk_wrap")
    box("cocoon", (-5, -9, -9), (10, 17, 28), "silk_wrap")
    box("cocoon", (-4, -5, -16), (8, 8, 2.5), "silk")
    box("cocoon", (-3.5, -6, 23.5), (7, 7, 2), "silk")
    box("cocoon", (-1.2, -3.7, 25.5), (2.4, 2.4, 2), "silk_knot")


# ---------------------------------------------------------------- painting
PALETTES = {
    1: {"chitin": [(22, 16, 14), (34, 25, 21), (48, 36, 29), (64, 49, 39), (84, 65, 51)],
        "hair": [(70, 54, 40), (98, 78, 56), (126, 102, 74), (156, 130, 96)],
        "band": [(110, 86, 60), (138, 110, 78), (168, 138, 100)],
        "mark": [(150, 120, 84), (186, 152, 108), (214, 182, 134)],
        "eye": (10, 8, 10), "eye_glint": (210, 214, 226), "eye_glow": False, "fang_tip": (120, 24, 20)},
    2: {"chitin": [(10, 8, 9), (17, 13, 14), (26, 19, 20), (36, 27, 28), (50, 38, 38)],
        "hair": [(40, 26, 26), (58, 36, 34), (78, 48, 44), (100, 62, 56)],
        "band": [(120, 14, 16), (164, 22, 22), (206, 40, 34)],
        "mark": [(150, 12, 14), (204, 26, 24), (246, 62, 48)],
        "eye": (230, 30, 30), "eye_glint": (255, 160, 140), "eye_glow": True, "fang_tip": (230, 40, 34)},
}
SILK = [(176, 170, 160), (200, 196, 188), (222, 220, 214), (240, 238, 234)]
FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.25, "bottom": -1.0}


def pick(r, level):
    return r[max(0, min(len(r) - 1, int(round(level))))]


def paint(tex, glow, phase, rnd):
    pal = PALETTES[phase]
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            fw, fh = max(1, fw), max(1, fh)
            light = FACE_LIGHT[face]
            for y in range(fh):
                for x in range(fw):
                    c, glows = paint_px(mat, pal, x, y, fw, fh, face, light, rnd, phase)
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if glows:
                        glow.set(x0 + x, y0 + y, c, 0.0)


def paint_px(mat, pal, x, y, w, h, face, light, rnd, phase):
    ch = pal["chitin"]
    noise = rnd.uniform(-0.35, 0.35)
    if mat in ("silk", "silk_wrap", "silk_knot"):
        # wound silk: strands slanting round the cocoon, a few dark gaps
        strand = (x + y * (2 if mat == "silk_wrap" else 1)) % 5
        level = 1.6 + light * 0.8 + (0.9 if strand == 0 else -0.4 if strand == 3 else 0) + noise
        return pick(SILK, level), False
    if mat in ("eye_big", "eye"):
        glint = face == "front" and x == 0 and y == 0 or face == "top" and x == 0 and y == 0
        c = pal["eye_glint"] if glint and mat == "eye_big" else pal["eye"]
        return c, pal["eye_glow"]
    if mat == "fang":
        return pick(ch, 0.5 + light + (1 if x == 0 else 0)), False
    if mat == "fang_tip":
        return pal["fang_tip"], phase == 2
    if mat in ("hair", "tuft", "chel_hair", "spine", "claw"):
        return pick(pal["hair"], 1.4 + light + noise), False
    if mat in ("coxa", "femur", "tibia", "tarsus", "palp", "palp_tip"):
        # long hairy segments: dark chitin, a light ring near each end (the bands of a hunting spider), hairs
        ring = face in ("front", "back", "top", "bottom") and w >= 4 and (x in (1, w - 2))
        if ring and mat in ("femur", "tibia", "tarsus"):
            return pick(pal["band"], 1 + light * 0.7 + noise * 0.5), phase == 2 and light > 0
        hairy = (x * 7 + y * 3) % 5 == 0
        if hairy:
            return pick(pal["hair"], 1.2 + light + noise), False
        return pick(ch, 2.0 + light + noise - (0.6 if y == h - 1 else 0)), False
    if mat == "joint":
        return pick(ch, 1.2 + light + noise), False
    if mat in ("carapace", "carapace_top", "ridge", "head", "head_top"):
        # chitin plates, lighter at their rims, radial grooves on the carapace
        rim = (x == 0 or x == w - 1 or y == 0) and w > 2 and h > 2
        groove = face == "top" and mat == "carapace" and (abs(x - w // 2) == abs(y - h // 2) or y == h // 2)
        level = 2.2 + light + noise + (0.7 if rim else 0) - (0.9 if groove else 0)
        if mat == "ridge":
            return pick(pal["mark"], 1 + light), phase == 2
        if phase == 2 and mat == "carapace_top" and face == "top" and abs(x - w / 2 + 0.5) < 1.0:
            return pick(pal["mark"], 2), True     # a red streak down the middle
        return pick(ch, level), False
    if mat == "sternum":
        return pick(ch, 1.4 + light + noise), False
    if mat == "spinneret":
        return pick(pal["hair"], 0.8 + light + noise), False
    if mat in ("abdomen", "abdomen_side", "abdomen_top", "abdomen_cap"):
        # hairy abdomen; on its back a row of chevrons (phase 1) or a blood-red hourglass and side bars (phase 2)
        level = 2.0 + light + noise + (0.5 if (x + y) % 4 == 0 else 0)
        c = pick(ch, level)
        if face == "top" and mat in ("abdomen_top", "abdomen"):
            cx = w / 2 - 0.5
            dx = abs(x - cx)
            if phase == 1:
                chevron = (y + int(dx)) % 5 == 0 and dx < w * 0.38 and 1 < y < h - 1
                if chevron:
                    return pick(pal["mark"], 1 + light * 0.5 + noise * 0.5), False
                if dx < 0.8 and y < h * 0.4:
                    return pick(pal["mark"], 0), False
            else:
                waist = abs(y - h / 2) / (h / 2)
                hour = dx <= 0.6 + waist * w * 0.28
                if hour and 1 <= y <= h - 2:
                    return pick(pal["mark"], 1.5 + noise * 0.5), True
        if face in ("left", "right") and phase == 2 and mat == "abdomen_side" and y == h // 2 and x % 4 != 3:
            return pick(pal["mark"], 1), True
        if face in ("left", "right") and phase == 1 and mat == "abdomen_side" and y == h // 2 and x % 3 == 0:
            return pick(pal["mark"], 0), False
        return c, False
    return pick(ch, 2 + light + noise), False


def molt(tex):
    """The shed skin: the phase 1 painting, pale and see-through."""
    out = Tex(TEX_W, TEX_H)
    for y in range(TEX_H):
        for x in range(TEX_W):
            p = tex.px[y][x]
            if p[3] == 0:
                continue
            lum = (p[0] * 0.3 + p[1] * 0.59 + p[2] * 0.11) / 255
            base = (214, 198, 162)
            c = tuple(int(base[i] * (0.62 + lum * 0.9)) for i in range(3))
            out.px[y][x] = tuple(min(255, v) for v in c) + (170,)
    return out


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


BODY_PARTS = ["body", "thorax", "head", "abdomen", "spinnerets", "chel_l", "chel_r", "fang_l", "fang_r", "palp_l",
              "palp_r", "palp2_l", "palp2_r"]


def leg_parts():
    return [f"{seg}{i}{s}" for i, s, sx in LEGS for seg in ["hip"] + SEGS]


def moving():
    return BODY_PARTS + leg_parts()


def rest(t, parts=None, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in (parts or moving())]


def lp(t, i, s, yaw=0.0, coxa=0.0, femur=0.0, tibia=0.0, tarsus=0.0, linear=False):
    """A leg's pose as offsets: yaw + forward, the others + up (each joint's own bend)."""
    sx = 1 if s == "l" else -1
    out = [k(t, f"hip{i}{s}", (0, sx * yaw, 0), (0, 0, 0), linear)]
    for seg, v in zip(SEGS, (coxa, femur, tibia, tarsus)):
        out.append(k(t, f"{seg}{i}{s}", (0, 0, -sx * v), (0, 0, 0), linear))
    return out


def legs(t, fn, linear=False):
    """fn(i, s) -> dict of lp's keywords for that leg."""
    out = []
    for i, s, sx in LEGS:
        out += lp(t, i, s, linear=linear, **fn(i, s))
    return out


def body(t, rot=(0, 0, 0), pos=(0, 0, 0), linear=False, **more):
    out = [k(t, "body", rot, pos, linear)]
    for prt, r in more.items():
        out.append(k(t, prt, r, (0, 0, 0), linear))
    return out


def folded(i, s):        # wrapped in the cocoon: legs folded along the body
    return dict(yaw=28 if i < 2 else -28, coxa=30, femur=30, tibia=-60, tarsus=-35)


def hanging(i, s):       # head down on the thread, legs spread to grab
    return dict(yaw=18 if i < 2 else 0, coxa=-5, femur=-20, tibia=35, tarsus=20)


def crouch(i, s):
    return dict(coxa=14, femur=6, tibia=-12, tarsus=-8)


def stretched(i, s):     # in the air, springing
    return dict(yaw=22 if i < 2 else -22, coxa=-6, femur=-10, tibia=38, tarsus=25)


def flat(i, s):
    return {}


def front_up(i, s):      # rearing: the first pair high, the second half up
    if i == 0:
        return dict(yaw=10, coxa=40, femur=32, tibia=-8, tarsus=-10)
    if i == 1:
        return dict(yaw=4, coxa=16, femur=10, tibia=-4)
    return dict(coxa=-4, tibia=6)


HEAD_DOWN = (82, 0, 0)


def animations():
    rnd = random.Random(11)
    allp = moving()

    # sleep: hanging head down in the cocoon, swaying a little
    sl = []
    for t, sway in ((0, -3), (2, 3), (4, -3)):
        sl += body(t, (HEAD_DOWN[0], 0, sway), (0, 0, 0)) + legs(t, folded)
    anim("sleep", 0, 4.0, True, sl)

    # wake
    wk = body(0, HEAD_DOWN) + legs(0, folded)
    for t in (0.2, 0.4, 0.6, 0.8, 1.0):
        wk += body(t, (HEAD_DOWN[0] + rnd.uniform(-6, 6), rnd.uniform(-8, 8), rnd.uniform(-10, 10)))
    wk += body(1.2, HEAD_DOWN) + legs(1.2, folded)
    wk += legs(1.45, hanging, linear=True) + body(1.45, (HEAD_DOWN[0] - 10, 0, 0), linear=True)
    wk += legs(2.4, hanging) + body(2.4, HEAD_DOWN)
    wk += legs(2.9, flat) + body(2.9, (0, 0, 0), (0, 0, 0))
    wk += [k(2.9, p, (0, 0, 0)) for p in ("head", "abdomen", "chel_l", "chel_r", "fang_l", "fang_r")]
    wk += legs(3.3, front_up) + body(3.3, (-24, 0, 0), (0, -2, 0), head=(-14, 0, 0), abdomen=(10, 0, 0),
                                       chel_l=(-30, 0, -14), chel_r=(-30, 0, 14), fang_l=(-40, 0, 0), fang_r=(-40, 0, 0))
    wk += legs(3.75, front_up) + body(3.75, (-26, 0, 0), (0, -2, 0), head=(-16, 0, 0), abdomen=(12, 0, 0),
                                        chel_l=(-34, 0, -18), chel_r=(-34, 0, 18), fang_l=(-45, 0, 0), fang_r=(-45, 0, 0))
    wk += rest(4.0, allp)
    anim("wake", 120, 4.0, False, wk)

    # idle
    idle = rest(0, allp)
    idle += [k(1.0, "abdomen", (3, 0, 0), (0, -0.4, 0)), k(1.0, "thorax", (0, 0, 0), (0, -0.2, 0)),
             k(0.5, "palp_l", (-14, 0, 0)), k(1.3, "palp_r", (-14, 0, 0)), k(1.0, "palp_l", (0, 0, 0)),
             k(1.8, "palp_r", (0, 0, 0)), k(0.7, "chel_l", (-6, 0, 0)), k(0.9, "chel_l", (0, 0, 0)),
             k(1.5, "chel_r", (-6, 0, 0)), k(1.7, "chel_r", (0, 0, 0)), k(1.2, "head", (-2, 4, 0)),
             k(1.2, "hip0l", (0, 4, 0)), k(1.6, "hip0r", (0, -4, 0))]
    idle += rest(2.0, allp)
    anim("idle", 240, 2.0, True, idle)

    # walk: legs 0l 1r 2l 3r step together, then 0r 1l 2r 3l
    A, LIFT = 13, 20
    wl = []
    for i, s, sx in LEGS:
        group = (i + (0 if s == "l" else 1)) % 2
        for t in (0, 0.25, 0.5, 0.75, 1.0):
            ph = (t + 0.5 * group) % 1.0
            # stance 0..0.5: forward to back; swing 0.5..1: back to forward, lifted
            if ph <= 0.5:
                yaw, lift = A - 4 * A * ph, 0
            else:
                yaw, lift = -A + 4 * A * (ph - 0.5), LIFT * math.sin((ph - 0.5) * 2 * math.pi)
            wl += lp(t, i, s, yaw=yaw, coxa=lift, tibia=lift * 0.6)
    for t in (0, 0.25, 0.5, 0.75, 1.0):
        wl += [k(t, "body", (0, 0, 2 if t in (0.25,) else -2 if t == 0.75 else 0), (0, -0.5 if t in (0.25, 0.75) else 0, 0))]
        wl += [k(t, "abdomen", (0, 3 if t == 0.25 else -3 if t == 0.75 else 0, 0))]
    anim("walk", 360, 1.0, True, wl)

    # pounce
    pc = rest(0, allp)
    pc += legs(0.8, crouch) + body(0.8, (6, 0, 0), (0, 3, 0), abdomen=(8, 0, 0), head=(-8, 0, 0),
                                    chel_l=(-20, 0, -10), chel_r=(-20, 0, 10), fang_l=(-30, 0, 0), fang_r=(-30, 0, 0))
    pc += legs(1.0, crouch) + body(1.0, (8, 0, 0), (0, 3.5, 0))
    pc += legs(1.15, stretched, linear=True) + body(1.15, (-14, 0, 0), (0, -2, 0), linear=True)
    pc += legs(1.4, flat, linear=True) + body(1.4, (6, 0, 0), (0, 2, 0), linear=True, fang_l=(20, 0, 0), fang_r=(20, 0, 0),
                                               chel_l=(10, 0, 6), chel_r=(10, 0, -6))
    pc += legs(1.6, crouch) + body(1.6, (3, 0, 0), (0, 1.5, 0))
    pc += rest(2.0, allp)
    anim("pounce", 480, 2.0, False, pc)

    # spit: three webs
    sp = rest(0, allp)
    sp += legs(0.6, front_up) + body(0.6, (-18, 0, 0), (0, -1.5, 0), head=(-12, 0, 0), chel_l=(-30, 0, -16),
                                       chel_r=(-30, 0, 16), fang_l=(-35, 0, 0), fang_r=(-35, 0, 0))
    sp += body(0.9, (-4, 0, 0), (0, 0.5, 0), linear=True, head=(14, 0, 0), chel_l=(10, 0, 6), chel_r=(10, 0, -6))
    sp += legs(0.9, flat, linear=True)
    sp += body(1.1, (-6, 0, 0), (0, 0, 0), head=(8, 0, 0))
    sp += rest(1.6, allp)
    anim("spit", 600, 1.6, False, sp)

    # stomp: front legs, then the hind legs
    st = rest(0, allp)
    st += legs(0.7, front_up) + body(0.7, (-22, 0, 0), (0, -2, 0), abdomen=(12, 0, 0), head=(-10, 0, 0))
    st += lp(0.9, 0, "l", yaw=6, coxa=-14, femur=-10, tibia=24, linear=True) + lp(0.9, 0, "r", yaw=6, coxa=-14, femur=-10, tibia=24, linear=True)
    st += lp(0.9, 1, "l", coxa=-6, tibia=10, linear=True) + lp(0.9, 1, "r", coxa=-6, tibia=10, linear=True)
    st += body(0.9, (8, 0, 0), (0, 1.5, 0), linear=True, abdomen=(-4, 0, 0), head=(6, 0, 0))
    st += body(1.2, (12, 0, 0), (0, 0, 0), abdomen=(-18, 0, 0))
    st += lp(1.2, 3, "l", yaw=-10, coxa=30, femur=20) + lp(1.2, 3, "r", yaw=-10, coxa=30, femur=20)
    st += lp(1.2, 2, "l", coxa=14) + lp(1.2, 2, "r", coxa=14)
    st += lp(1.5, 3, "l", yaw=-4, coxa=-12, tibia=16, linear=True) + lp(1.5, 3, "r", yaw=-4, coxa=-12, tibia=16, linear=True)
    st += lp(1.5, 2, "l", coxa=-6, tibia=8, linear=True) + lp(1.5, 2, "r", coxa=-6, tibia=8, linear=True)
    st += body(1.5, (-6, 0, 0), (0, 1, 0), linear=True, abdomen=(6, 0, 0))
    st += lp(1.5, 0, "l") + lp(1.5, 0, "r") + lp(1.5, 1, "l") + lp(1.5, 1, "r")
    st += rest(2.2, allp)
    anim("stomp", 720, 2.2, False, st)

    # climb onto the thread, hang, fall
    cl = rest(0, allp) + legs(0.2, crouch) + body(0.2, (6, 0, 0), (0, 2.5, 0))
    cl += legs(0.6, hanging) + body(0.6, HEAD_DOWN, (0, 0, 0))
    anim("climb", 840, 0.6, False, cl)
    hg = []
    for t, sway in ((0, -4), (1, 4), (2, -4)):
        hg += legs(t, hanging) + body(t, (HEAD_DOWN[0], 0, sway), (0, 0, 0))
    hg += [k(0, "chel_l", (0, 0, 0)), k(1, "chel_l", (-10, 0, -6)), k(2, "chel_l", (0, 0, 0)),
           k(0, "chel_r", (0, 0, 0)), k(1, "chel_r", (-10, 0, 6)), k(2, "chel_r", (0, 0, 0))]
    anim("hang", 960, 2.0, True, hg)
    fl = legs(0, hanging) + body(0, HEAD_DOWN)
    fl += legs(0.25, stretched, linear=True) + body(0.25, (10, 0, 0), (0, -1, 0), linear=True)
    fl += legs(0.3, crouch, linear=True) + body(0.3, (4, 0, 0), (0, 4, 0), linear=True)
    fl += legs(0.55, crouch) + body(0.55, (2, 0, 0), (0, 2.5, 0))
    fl += rest(1.0, allp)
    anim("fall", 1080, 1.0, False, fl)

    # lay eggs: the abdomen rises and pumps
    ly = rest(0, allp)
    for t, a in ((0.4, -34), (0.55, -26), (0.7, -38), (0.85, -28), (1.0, -44), (1.3, -30)):
        ly += [k(t, "abdomen", (a, 0, 0), (0, -1 if a < -35 else 0, 0)), k(t, "spinnerets", (a / 2, 0, 0))]
    ly += legs(0.5, crouch) + body(0.5, (8, 0, 0), (0, 1.5, 0))
    ly += body(1.0, (12, 0, 0), (0, 2, 0), linear=True)
    ly += rest(1.8, allp)
    anim("lay", 1200, 1.8, False, ly)

    # shoot: the abdomen curls under and forward
    sh = rest(0, allp)
    sh += legs(0.6, crouch) + body(0.6, (-14, 0, 0), (0, -2, 0), abdomen=(60, 0, 0), spinnerets=(40, 0, 0))
    sh += body(0.9, (-10, 0, 0), (0, -2, 0), linear=True, abdomen=(78, 0, 0), spinnerets=(55, 0, 0))
    sh += body(1.1, (-8, 0, 0), (0, -1, 0), abdomen=(70, 0, 0))
    sh += rest(1.6, allp)
    anim("shoot", 1320, 1.6, False, sh)

    # acid rain: rears high, spits up
    rn = rest(0, allp)
    rn += legs(0.6, front_up) + body(0.6, (-34, 0, 0), (0, -3, 0), head=(-20, 0, 0), abdomen=(18, 0, 0),
                                       chel_l=(-30, 0, -14), chel_r=(-30, 0, 14), fang_l=(-40, 0, 0), fang_r=(-40, 0, 0))
    rn += body(0.9, (-40, 0, 0), (0, -3.5, 0), linear=True, head=(-30, 0, 0), chel_l=(-45, 0, -22), chel_r=(-45, 0, 22))
    rn += body(1.2, (-30, 0, 0), (0, -3, 0), head=(-20, 0, 0))
    rn += legs(1.2, front_up)
    rn += rest(1.8, allp)
    anim("rain", 1440, 1.8, False, rn)

    # roar
    ro = rest(0, allp)
    ro += legs(0.4, front_up) + body(0.4, (-26, 0, 0), (0, -2, 0), head=(-16, 0, 0), abdomen=(12, 0, 0),
                                       chel_l=(-36, 0, -20), chel_r=(-36, 0, 20), fang_l=(-50, 0, 0), fang_r=(-50, 0, 0))
    for t in (0.55, 0.7, 0.85, 1.0):
        ro += body(t, (-26 + rnd.uniform(-3, 3), rnd.uniform(-4, 4), rnd.uniform(-3, 3)), (0, -2, 0))
    ro += legs(1.1, front_up) + body(1.1, (-22, 0, 0), (0, -2, 0))
    ro += rest(1.5, allp)
    anim("roar", 1560, 1.5, False, ro)

    # transform: hanging, it thrashes, splits its skin, pulls free, lets itself down and screams
    tf = rest(0, allp) + legs(0.5, hanging) + body(0.5, HEAD_DOWN)
    for t in (0.7, 0.85, 1.0, 1.15, 1.3, 1.45, 1.6, 1.75, 1.9):
        tf += body(t, (HEAD_DOWN[0] + rnd.uniform(-12, 12), rnd.uniform(-14, 14), rnd.uniform(-16, 16)),
                   abdomen=(rnd.uniform(-25, 5), 0, 0))
        tf += legs(t, lambda i, s: dict(yaw=rnd.uniform(-20, 20), coxa=rnd.uniform(-10, 30), femur=rnd.uniform(-20, 30),
                                        tibia=rnd.uniform(-60, 20)))
    tf += legs(2.0, folded) + body(2.0, (HEAD_DOWN[0] - 20, 0, 0), abdomen=(-30, 0, 0), head=(20, 0, 0))
    tf += legs(2.6, folded) + body(2.6, (HEAD_DOWN[0] + 10, 0, 0), abdomen=(5, 0, 0), head=(-10, 0, 0))
    tf += legs(3.0, hanging) + body(3.0, HEAD_DOWN, abdomen=(0, 0, 0), head=(0, 0, 0))
    tf += legs(3.5, hanging) + body(3.5, HEAD_DOWN)
    tf += legs(4.3, flat) + body(4.3, (0, 0, 0), (0, 0, 0))
    tf += legs(4.55, front_up) + body(4.55, (-26, 0, 0), (0, -2, 0), head=(-16, 0, 0), chel_l=(-36, 0, -20),
                                        chel_r=(-36, 0, 20), fang_l=(-50, 0, 0), fang_r=(-50, 0, 0))
    tf += rest(5.0, allp)
    anim("transform", 1680, 5.0, False, tf)

    # death: thrashes, rolls onto its back, the legs curl up one after another
    de = rest(0, allp)
    de += legs(0.3, front_up) + body(0.3, (-20, 0, 8), (0, -1, 0))
    de += body(0.6, (-10, 0, -20), (0, -2, 0))
    de += legs(0.6, lambda i, s: dict(coxa=20, femur=20, tibia=-20))
    de += body(0.9, (0, 0, 100), (0, -6, 0), linear=True)
    de += body(1.2, (0, 0, 180), (0, 1.5, 0))
    order = [(0, "l"), (3, "r"), (1, "r"), (2, "l"), (0, "r"), (3, "l"), (1, "l"), (2, "r")]
    for n, (i, s) in enumerate(order):
        t0 = 1.2 + n * 0.27
        de += lp(t0, i, s, coxa=10, femur=10, tibia=-10)
        de += lp(t0 + 0.35, i, s, coxa=-30, femur=-55, tibia=-95, tarsus=-60)
        de += lp(4.0, i, s, coxa=-30, femur=-55, tibia=-95, tarsus=-60)
    de += body(4.0, (0, 0, 180), (0, 1.5, 0), head=(10, 0, 0), chel_l=(20, 0, 10), chel_r=(20, 0, -10))
    de += body(3.4, (0, 0, 180), (0, 1.5, 0), head=(10, 0, 0))
    anim("death", 1800, 4.0, False, de)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    for phase in (1, 2):
        tex, glow = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
        paint(tex, glow, phase, random.Random(23))
        if phase == 1:
            tex.save(os.path.join(tex_dir, "arachnid.png"))
            molt(tex).save(os.path.join(tex_dir, "arachnid_molt.png"))
        else:
            tex.save(os.path.join(tex_dir, "arachnid_p2.png"))
            glow.save(os.path.join(tex_dir, "arachnid_p2_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "arachnid.png"), TEX_W, TEX_H, "arachnid")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp_ else 'once'}" for n, s, e, lp_, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved arachnid parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
