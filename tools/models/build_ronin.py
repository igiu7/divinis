"""
Builds the Ronin (boss.Ronin) in Blender: a masterless samurai in laced, lacquered armour under a wide straw jingasa,
his face wrapped all round in a ninja's mask, a katana in his right hand and the daisho's scabbards through his belt.

  head        wrapped in the mask (a band round it, the topknot's bump, the cloth tucked at the throat, its two ties
              hanging behind), only a slit for the eyes and the skin round them
  hat         the jingasa: a wide, shallow cone of eight tiers of woven straw, a knob on top, a cord under the chin
  torso       the do of kozane lames laced with odoshi cords, its gold-rimmed munaita and breast plate, the watagami
              over the shoulders, the nodowa at the throat, the obi and its knot, the waist lames
  arms        sleeves of mail, the great sode on the shoulders (a crown plate and six lames, each hung a little further
              out), plated tsutsu-gote, tekko over the hands
  katana      a wrapped tsuka with its kashira, a round tsuba, the habaki, a curved blade (three segments and the
              kissaki, each turned a little more) with its hamon
  body        seven kusazuri panels hanging from the waist and flaring out; the katana's saya and the wakizashi
  legs        pleated hakama, haidate of small plates over the thighs, splinted suneate with knee guards, tabi, waraji

One model, three textures from one painting (256x256): phase 1 red lacquer laced in gold, a straw hat; phase 2
(ronin_p2.png) black lacquer laced in red, a black hat; its glow (ronin_p2_glow.png): the eyes and the blade's edge.

  blender -b --python tools/models/build_ronin.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/RoninModelData.java. Model space: the ground at y 24, the front -z, his
right -x; client.RoninRenderer draws it 1.25 times its pixels (about 2.6 blocks to the top of the hat).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 256, 256
PARTS, ABS, ROTATED, CUBES, MATERIAL = [], {}, set(), [], {}
_n = [0]


def uid(prefix):
    _n[0] += 1
    return f"{prefix}_{_n[0]}"


def part(name, parent, pivot, rot=(0, 0, 0)):
    ABS[name] = tuple(pivot)
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))
    if any(rot):
        ROTATED.add(name)


def box(prt, frm, to, mat):
    assert prt not in ROTATED, prt
    px, py, pz = ABS[prt]
    name = uid(prt)
    CUBES.append([name, prt, (frm[0] - px, frm[1] - py, frm[2] - pz), tuple(round(to[i] - frm[i], 3) for i in range(3)), None])
    MATERIAL[name] = mat


def lbox(prt, origin, size, mat):
    name = uid(prt)
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def octa(prt, center, dia, length, mat, ratio=0.84):
    """An octagonal disc or column standing on y."""
    cx, cy, cz = center
    h, half = dia / 2, length / 2
    box(prt, (cx - h, cy - half, cz - h), (cx + h, cy + half, cz + h), mat)
    sub = uid(prt + "_oct")
    part(sub, prt, center, (0, 45, 0))
    lbox(sub, (-h * ratio, -half, -h * ratio), (dia * ratio, length, dia * ratio), mat)


def model():
    part("body", None, (0, 12, 0))
    part("torso", "body", (0, 12, 0))
    part("head", "torso", (0, 0, 0))
    part("hat", "head", (0, -7, 0))

    # ---- the head: wrapped all round in a ninja's mask, only a slit for the eyes; its ties hang behind
    box("head", (-3.5, -7, -3.5), (3.5, 0, 3.5), "mask")
    box("head", (-3.65, -6.2, -3.65), (3.65, -5.4, 3.65), "mask_band")
    box("head", (-1.2, -7.4, -0.8), (1.2, -6.9, 1.2), "mask")          # the topknot's bump under the cloth
    box("head", (-2.2, 0, -3.4), (2.2, 0.8, -2.6), "mask")             # the cloth tucked at the throat
    # ---- the jingasa: a wide, shallow cone of woven straw, a knob on its top, a cord under the chin
    y = -7.3
    for dia in (20, 17.6, 15.2, 12.8, 10.4, 8.0, 5.6, 3.2):
        octa("hat", (0, y, 0), dia, 0.62, "straw")
        y -= 0.55
    octa("hat", (0, y - 0.2, 0), 1.6, 0.8, "straw_dark")
    box("hat", (-3.75, -7.1, -0.35), (-3.45, -0.6, 0.35), "cord")
    box("hat", (3.45, -7.1, -0.35), (3.75, -0.6, 0.35), "cord")
    box("hat", (-3.75, -0.6, -0.35), (3.75, -0.2, 0.35), "cord")
    box("hat", (-0.5, -0.5, -0.9), (0.5, 0.6, -0.4), "cord")           # the knot

    # ---- the torso: the do, its top plate, the shoulder straps, the throat guard, the belt, the two scabbards
    box("torso", (-4.5, 1.2, -2.9), (4.5, 8.4, 2.9), "lame")
    box("torso", (-4.2, 0.4, -3.35), (4.2, 1.9, -2.85), "plate")       # the munaita, gold-rimmed
    box("torso", (-4.2, 0.4, 2.85), (4.2, 1.9, 3.35), "plate")
    box("torso", (-4.7, 1.6, -3.15), (4.7, 5.0, -2.85), "plate")       # the breast plate
    box("torso", (-3.7, -0.2, -3.0), (-2.2, 1.0, 3.0), "plate")        # the watagami over the shoulders
    box("torso", (2.2, -0.2, -3.0), (3.7, 1.0, 3.0), "plate")
    box("torso", (-2.2, -0.6, -3.6), (2.2, 0.5, -3.0), "lame")         # the nodowa at the throat
    box("torso", (-4.4, 8.4, -2.9), (4.4, 10.2, 2.9), "obi")
    box("torso", (-1.5, 8.6, -3.6), (1.5, 10.0, -2.9), "obi_knot")
    box("torso", (-4.3, 10.2, -2.8), (4.3, 11.6, 2.8), "lame")         # the waist lames
    part("scarf", "torso", (0, -5.5, 3.4))                             # the mask's ties, hanging from the back of the head
    box("scarf", (-1.3, -5.5, 3.4), (-0.2, 1.5, 3.8), "mask")
    box("scarf", (0.3, -5.5, 3.4), (1.4, 0.5, 3.8), "mask")

    # ---- the arms: sleeves of the hitatare, mail and plated kote, hand guards, the great sode
    for s, sx in (("r", -1), ("l", 1)):
        arm, fore = f"arm_{s}", f"forearm_{s}"
        part(arm, "torso", (sx * 6, 1.5, 0))
        part(fore, arm, (sx * 6, 7, 0))
        xa, xb = sorted((sx * 4.3, sx * 7.7))
        box(arm, (xa, 1, -2.1), (xb, 7, 2.1), "mail")
        sode = f"sode_{s}"
        part(sode, arm, (sx * 6.3, 0.2, 0), (0, 0, -sx * 9))
        lbox(sode, (-2.6, -0.4, -3.2), (5.2, 1.0, 6.4), "plate")        # the kanmuri-ita
        for i in range(6):                                              # six lames, each hung a little further out
            lbox(sode, (-2.5 - sx * 0.12 * i, 0.6 + i * 1.05, -3.1 - 0.05 * i), (5.0, 1.15, 6.2 + 0.1 * i), "lame_row")
        xa, xb = sorted((sx * 4.2, sx * 7.8))
        box(fore, (xa, 7, -2.0), (xb, 11.6, 2.0), "kote")
        box(fore, (xa + 0.1, 11.4, -1.9), (xb - 0.1, 12.4, 1.9), "plate")   # the tekko over the back of the hand
        box(fore, (xa + 0.3, 12.4, -1.5), (xb - 0.3, 13.7, 1.5), "skin")

    # ---- the katana in the right hand: a wrapped tsuka with its kashira, a round tsuba, the habaki, a curved blade
    part("katana", "forearm_r", (-6, 12.6, 0))
    box("katana", (-6.5, 12.1, -2.6), (-5.5, 13.1, 4.0), "tsuka")
    box("katana", (-6.6, 12.0, 4.0), (-5.4, 13.2, 4.6), "gold")        # the kashira, the pommel cap
    tsuba = uid("katana_tsuba")
    part(tsuba, "katana", (-6, 12.6, -2.9), (0, 0, 45))
    lbox(tsuba, (-1.15, -1.15, -0.25), (2.3, 2.3, 0.5), "tsuba")
    box("katana", (-7.15, 11.45, -3.15), (-4.85, 13.75, -2.65), "tsuba")
    box("katana", (-6.35, 12.05, -3.8), (-5.65, 13.15, -3.15), "gold")  # the habaki
    z = -3.8
    parent = "katana"
    for i, length in enumerate((8, 8, 7)):
        seg = f"blade{i}"
        part(seg, parent, (-6, 12.6, z), (-3 if i else 0, 0, 0))
        lbox(seg, (-0.22, -0.62, -length), (0.44, 1.24, length), "blade")
        parent, z = seg, z - length
    part("blade_tip", parent, (-6, 12.6, z), (-14, 0, 0))
    lbox("blade_tip", (-0.18, -0.5, -2.2), (0.36, 1.0, 2.2), "blade")

    # ---- the kusazuri: seven panels of lames hanging from the waist, flaring out
    for name, yaw, off in (("kusazuri_f", 0, -3.0), ("kusazuri_fl", 38, -3.0), ("kusazuri_fr", -38, -3.0), ("kusazuri_l", 90, -3.0),
                           ("kusazuri_r", -90, -3.0), ("kusazuri_bl", 145, -3.0), ("kusazuri_br", -145, -3.0)):
        piv = f"{name}_hang"
        part(piv, "body", (0, 11.2, 0), (0, yaw, 0))
        part(name, piv, (0, 11.2, 0), (-12, 0, 0))
        lbox(name, (-1.6, 0, off - 0.7), (3.2, 6.2, 0.8), "lame_row")

    # ---- the daisho's scabbards tucked through the belt on the left
    part("saya", "body", (5.0, 9.6, -2.8), (-20, 6, 0))
    lbox("saya", (-0.8, -0.8, 0), (1.6, 1.6, 21), "saya")
    lbox("saya", (-0.95, -0.95, -0.3), (1.9, 1.9, 0.6), "gold")       # the koiguchi
    lbox("saya", (-0.9, -0.9, 20.6), (1.8, 1.8, 0.8), "gold")         # the kojiri
    part("wakizashi", "body", (4.3, 9.4, -3.0), (-28, 14, 0))
    lbox("wakizashi", (-0.6, -0.6, -4.5), (1.2, 1.2, 4.5), "tsuka")
    lbox("wakizashi", (-0.95, -0.95, -0.25), (1.9, 1.9, 0.5), "tsuba")
    lbox("wakizashi", (-0.7, -0.7, 0.25), (1.4, 1.4, 13), "saya")

    # ---- the legs: pleated hakama, the haidate over the thighs, splinted suneate with knee guards, tabi, waraji
    for s, sx in (("r", -1), ("l", 1)):
        leg, shin = f"leg_{s}", f"shin_{s}"
        part(leg, "body", (sx * 2.4, 12, 0))
        part(shin, leg, (sx * 2.4, 18.5, 0))
        xa, xb = sorted((sx * 0.1, sx * 5.0))
        box(leg, (xa, 12, -2.6), (xb, 19, 2.6), "hakama")
        xa, xb = sorted((sx * -0.1, sx * 5.2))
        box(leg, (xa, 12.3, -3.2), (xb, 16.8, -2.6), "haidate")
        xa, xb = sorted((sx * 0.2, sx * 5.4))
        box(shin, (xa, 18.5, -2.7), (xb, 22.6, 2.7), "hakama")
        xa, xb = sorted((sx * 0.6, sx * 4.8))
        box(shin, (xa, 17.6, -3.2), (xb, 19.4, -2.6), "plate")          # the knee guard
        for j in range(3):                                              # the splints
            x0 = sx * (1.0 + j * 1.3)
            xa, xb = sorted((x0, x0 + sx * 1.0))
            box(shin, (xa, 19.2, -3.1), (xb, 22.4, -2.6), "splint")
        xa, xb = sorted((sx * 0.5, sx * 4.9))
        box(shin, (xa, 22.6, -3.4), (xb, 23.4, 2.4), "tabi")
        xa, xb = sorted((sx * 0.3, sx * 5.1))
        box(shin, (xa, 23.4, -3.8), (xb, 24, 2.8), "waraji")
        box(shin, (xa + 1.0 if sx > 0 else xa + 2.4, 22.9, -3.85), (xa + 2.4 if sx > 0 else xa + 3.8, 23.4, -3.4), "cord")   # the thong


# ---------------------------------------------------------------- painting
PALETTES = {
    1: {"lacquer": [(70, 10, 12), (110, 16, 18), (150, 26, 26), (186, 42, 36), (220, 70, 58)],
        "lace": (232, 186, 70), "lace_d": (168, 120, 40),
        "straw": [(124, 96, 54), (158, 126, 74), (190, 158, 100), (214, 186, 130), (234, 210, 160)],
        "hakama": [(30, 32, 52), (40, 44, 68), (54, 58, 86), (70, 76, 108)],
        "eye": (34, 24, 20), "edge": (230, 236, 240)},
    2: {"lacquer": [(10, 10, 12), (20, 20, 24), (32, 32, 38), (46, 46, 54), (66, 66, 76)],
        "lace": (196, 24, 30), "lace_d": (124, 12, 18),
        "straw": [(10, 10, 12), (18, 18, 22), (28, 28, 34), (40, 40, 48), (56, 56, 66)],
        "hakama": [(14, 14, 18), (20, 20, 26), (28, 28, 36), (38, 38, 48)],
        "eye": (255, 40, 40), "edge": (255, 90, 90)},
}
FIXED = {
    "mask": [(16, 16, 20), (24, 24, 30), (34, 34, 42), (46, 46, 56)],
    "mask_band": [(28, 28, 34), (40, 40, 48), (54, 54, 64)],
    "skin": [(196, 156, 124), (222, 184, 150), (240, 206, 172), (250, 222, 192)],
    "cord": [(150, 30, 28), (196, 48, 40), (226, 84, 70)],
    "obi": [(30, 32, 54), (42, 46, 74), (58, 62, 96)],
    "obi_knot": [(150, 110, 40), (200, 156, 64), (236, 196, 108)],
    "gold": [(120, 84, 26), (170, 124, 42), (214, 170, 72), (244, 212, 130)],
    "tsuka": [(14, 14, 16), (28, 26, 30), (236, 228, 212)],
    "tsuba": [(28, 28, 32), (46, 46, 52), (70, 70, 78), (100, 100, 110)],
    "blade": [(120, 126, 136), (160, 166, 176), (200, 206, 214), (236, 240, 244), (255, 255, 255)],
    "saya": [(12, 12, 14), (22, 20, 24), (36, 32, 36), (54, 48, 52)],
    "tabi": [(206, 202, 192), (228, 224, 214), (246, 244, 238)],
    "waraji": [(124, 96, 54), (158, 126, 74), (190, 158, 100)],
    "mail": [(20, 22, 30), (34, 36, 46), (96, 100, 110), (136, 140, 150)],
    "splint": [(40, 42, 48), (64, 68, 76), (92, 96, 106), (124, 128, 138)],
    "straw_dark": [(100, 76, 40), (130, 100, 60), (160, 128, 80)],
}
FACE_LIGHT = {"top": 0.8, "front": 0.2, "left": 0.0, "right": 0.0, "back": -0.3, "bottom": -0.9}


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
                    c, glows = paint_px(mat, pal, x, y, fw, fh, face, light, rnd)
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if glows:
                        glow.set(x0 + x, y0 + y, c, 0.0)


def paint_px(mat, pal, x, y, w, h, face, light, rnd):
    lac = pal["lacquer"]
    if mat in ("lame", "lame_row"):
        # kozane lames laced together: rows two pixels high with a gloss on top, the odoshi cords running down
        band = y % 3
        cord = x % 3 == 1 and face in ("front", "back", "left", "right")
        if band == 2 and h > 2:
            return lac[0], False
        if cord:
            return (pal["lace"] if band == 0 else pal["lace_d"]), False
        level = 2.4 + light + (1.3 if band == 0 else 0) + rnd.uniform(-0.15, 0.15)
        return pick(lac, level), False
    if mat == "plate":
        # a lacquered plate, gold-rimmed (fukurin)
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            return pick(FIXED["gold"], 2 + (1 if y == 0 else 0)), False
        level = 2.8 + light - y / max(1, h) + (1.4 if (x + y) % 7 == 0 and y < h / 2 else 0)
        return pick(lac, level), False
    if mat == "haidate":
        # rows of small plates sewn on cloth
        if y % 2 == 1 or x % 3 == 2:
            return pick(pal["hakama"], 0.5), False
        return pick(lac, 2.6 + light + (1 if y % 4 == 0 else 0)), False
    if mat == "straw":
        # woven straw: rings of plaits round the cone, the strands slanting
        r = pal["straw"]
        plait = (x + y * 2) % 4 < 2
        ring = y % 3 == 2
        return pick(r, 2 + light + (0.9 if plait else -0.5) - (1.0 if ring else 0) + rnd.uniform(-0.25, 0.25)), False
    if mat == "hakama":
        r = pal["hakama"]
        pleat = x % 3 == 0
        return pick(r, 1.6 + light - (0.9 if pleat else 0) + (0.5 if y == 0 else 0)), False
    if mat == "kote":
        # tsutsu-gote: long iron splints over mail
        if x % 2 == 0:
            return pick(lac, 2.6 + light + (1 if y == 0 else 0)), False
        return pick(FIXED["mail"], 2 if (x + y) % 2 == 0 else 0), False
    if mat == "mail":
        r = FIXED["mail"]
        ring = (x % 2 == 0) != (y % 2 == 0)
        return (pick(r, 3) if ring else pick(r, 0 + (0.5 if light > 0 else 0))), False
    if mat == "mask":
        r = FIXED["mask"]
        if face == "front" and h == 7 and w == 7 and y == 3 and 1 <= x <= 5:
            # the slit: skin round two eyes
            if x in (2, 4):
                return pal["eye"], pal["eye"] == (255, 40, 40)
            if x in (1, 5):
                return (236, 230, 222) if pal["eye"] != (255, 40, 40) else (140, 20, 20), pal["eye"] == (255, 40, 40)
            return pick(FIXED["skin"], 2), False
        if face == "front" and h == 7 and w == 7 and y in (2, 4) and 1 <= x <= 5:
            return pick(FIXED["skin"], 1 if y == 2 else 0), False
        fold = (x + y * 2) % 5 == 0
        return pick(r, 1.6 + light - (0.8 if fold else 0) + rnd.uniform(-0.2, 0.2)), False
    if mat == "tsuka":
        r = FIXED["tsuka"]
        return (r[2] if (x + y) % 3 == 0 else r[0]), False
    if mat == "blade":
        r = FIXED["blade"]
        if face in ("left", "right"):
            edge = y == h - 1
            hamon = y == h - 2 and x % 3 != 0
            c = pal["edge"] if edge else (pick(r, 4) if hamon else pick(r, 2 + light))
            return c, edge and pal["edge"] != (230, 236, 240)
        return pick(r, 3 + light), False
    r = FIXED[mat]
    level = (len(r) - 1) / 2 + light + rnd.uniform(-0.2, 0.2)
    if (y == 0 or x == 0) and w > 2 and h > 2:
        level += 0.6
    if (y == h - 1 or x == w - 1) and w > 2 and h > 2:
        level -= 0.6
    return pick(r, level), False


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


MOVING = ["body", "torso", "head", "arm_r", "forearm_r", "katana", "arm_l", "forearm_l", "leg_r", "shin_r", "leg_l", "shin_l",
          "scarf", "saya"]


def rest(t, parts=MOVING):
    return [k(t, p, (0, 0, 0), (0, 0, 0)) for p in parts]


READY = [("arm_r", (-28, 0, 4)), ("forearm_r", (-12, 0, 0)), ("arm_l", (-10, 0, -6))]


def ready(t):
    return [k(t, p, r) for p, r in READY]


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


def kneel(t, low=8):
    return [k(t, "body", (0, 0, 0), (0, low, 0)), k(t, "leg_r", (-90, 0, 0)), k(t, "shin_r", (95, 0, 0)),
            k(t, "leg_l", (-90, 0, 0)), k(t, "shin_l", (95, 0, 0))]


def animations():
    rnd = random.Random(3)
    anim("idle", 0, 3.0, True, [
        k(0, "torso", (0, 0, 0)), k(1.5, "torso", (2, 0, 0)), k(3, "torso", (0, 0, 0)),
        k(0, "arm_r", (-28, 0, 4)), k(1.5, "arm_r", (-30, 0, 5)), k(3, "arm_r", (-28, 0, 4)),
        k(0, "forearm_r", (-12, 0, 0)), k(3, "forearm_r", (-12, 0, 0)),
        k(0, "arm_l", (-10, 0, -6)), k(1.5, "arm_l", (-12, 0, -7)), k(3, "arm_l", (-10, 0, -6)),
        k(0, "head", (0, 0, 0)), k(1.5, "head", (-2, 4, 0)), k(3, "head", (0, 0, 0)),
        k(0, "scarf", (6, 0, 0)), k(1, "scarf", (14, 0, 3)), k(2, "scarf", (8, 0, -3)), k(3, "scarf", (6, 0, 0)),
    ])
    anim("walk", 100, 1.2, True, [
        k(0, "leg_r", (28, 0, 0)), k(0.6, "leg_r", (-28, 0, 0)), k(1.2, "leg_r", (28, 0, 0)),
        k(0, "shin_r", (0, 0, 0)), k(0.3, "shin_r", (30, 0, 0)), k(0.6, "shin_r", (0, 0, 0)), k(1.2, "shin_r", (0, 0, 0)),
        k(0, "leg_l", (-28, 0, 0)), k(0.6, "leg_l", (28, 0, 0)), k(1.2, "leg_l", (-28, 0, 0)),
        k(0, "shin_l", (0, 0, 0)), k(0.6, "shin_l", (0, 0, 0)), k(0.9, "shin_l", (30, 0, 0)), k(1.2, "shin_l", (0, 0, 0)),
        k(0, "arm_l", (16, 0, -6)), k(0.6, "arm_l", (-16, 0, -6)), k(1.2, "arm_l", (16, 0, -6)),
        k(0, "arm_r", (-28, 0, 4)), k(0.6, "arm_r", (-34, 0, 4)), k(1.2, "arm_r", (-28, 0, 4)),
        k(0, "body", None, (0, 0, 0)), k(0.3, "body", None, (0, -0.6, 0)), k(0.6, "body", None, (0, 0, 0)),
        k(0.9, "body", None, (0, -0.6, 0)), k(1.2, "body", None, (0, 0, 0)),
        k(0, "scarf", (20, 0, 0)), k(0.6, "scarf", (30, 0, 4)), k(1.2, "scarf", (20, 0, 0)),
    ])
    # the rift slash (1.6 s): wound back, cuts across at 0.75 s (tick 15)
    anim("slash", 200, 1.6, False, rest(0) + ready(0) + [
        k(0.4, "torso", (6, 60, 0)), k(0.4, "arm_r", (-90, 0, -70)), k(0.4, "forearm_r", (-20, 0, 0)), k(0.4, "arm_l", (-30, 0, 20)),
        k(0.4, "body", None, (0, 1.5, 0)), k(0.4, "leg_r", (-25, 0, 0)), k(0.4, "leg_l", (20, 0, 0)), k(0.4, "shin_r", (30, 0, 0)),
        k(0.75, "torso", (6, -70, 0), linear=True), k(0.75, "arm_r", (-90, 0, 70), linear=True), k(0.75, "forearm_r", (0, 0, 0), linear=True),
        k(1.0, "torso", (8, -80, 0)), k(1.0, "arm_r", (-80, 0, 80)), k(1.0, "body", None, (0, 1.5, 0)),
        k(1.0, "scarf", (60, 0, 20)),
    ] + rest(1.6) + ready(1.6))
    # a flicker strike (0.5 s): appears with the blade high, cuts down at 0.15 s (tick 3)
    anim("flicker", 300, 0.5, False, [
        k(0, "arm_r", (-160, 0, -25)), k(0, "forearm_r", (-20, 0, 0)), k(0, "torso", (-10, 25, 0)), k(0, "scarf", (70, 0, 0)),
        k(0.15, "arm_r", (-35, 0, 25), linear=True), k(0.15, "forearm_r", (0, 0, 0), linear=True), k(0.15, "torso", (18, -30, 0), linear=True),
        k(0.3, "scarf", (40, 0, 10)), k(0.5, "arm_r", (-28, 0, 4)), k(0.5, "forearm_r", (-12, 0, 0)), k(0.5, "torso", (0, 0, 0)),
        k(0.5, "scarf", (6, 0, 0)),
    ])
    # the drawing cut (2.2 s): the blade back by the scabbard, crouched, still; draws and cuts round at 1.3 s (tick 26)
    anim("iai", 400, 2.2, False, rest(0) + ready(0) + [
        k(0.5, "body", None, (0, 2.5, 0)), k(0.5, "leg_r", (-35, 0, 0)), k(0.5, "shin_r", (45, 0, 0)), k(0.5, "leg_l", (25, 0, 0)),
        k(0.5, "shin_l", (20, 0, 0)), k(0.5, "torso", (15, 30, 0)), k(0.5, "arm_r", (-35, 0, 50)), k(0.5, "forearm_r", (-70, 0, 0)),
        k(0.5, "katana", (40, 160, 0)), k(0.5, "arm_l", (-25, 0, -20)), k(0.5, "head", (10, -20, 0)),
        k(1.2, "body", None, (0, 2.5, 0)), k(1.2, "torso", (15, 32, 0)), k(1.2, "arm_r", (-35, 0, 50)), k(1.2, "katana", (40, 160, 0)),
        k(1.3, "torso", (5, -70, 0), linear=True), k(1.3, "arm_r", (-90, 0, -85), linear=True), k(1.3, "forearm_r", (0, 0, 0), linear=True),
        k(1.3, "katana", (0, 0, 0), linear=True), k(1.3, "scarf", (70, 0, -20), linear=True),
        k(1.6, "torso", (5, -90, 0)), k(1.6, "arm_r", (-80, 0, -95)), k(1.6, "body", None, (0, 2, 0)),
    ] + rest(2.2) + ready(2.2))
    # three quick cuts from behind (0.6 s): at 0.2, 0.35 and 0.5 s (ticks 4, 7, 10)
    anim("backstep", 500, 1.2, False, [
        k(0, "arm_r", (-60, 0, -20)), k(0, "torso", (0, 0, 0)), k(0, "scarf", (60, 0, 0)),
        k(0.35, "arm_r", (-135, 0, -40)), k(0.35, "torso", (-5, 30, 0)), k(0.35, "body", None, (0, 1, 0)),
        k(0.45, "arm_r", (-40, 0, 40), linear=True), k(0.45, "torso", (10, -30, 0), linear=True),
        k(0.65, "arm_r", (-140, 0, 30)), k(0.65, "torso", (0, -20, 0)),
        k(0.75, "arm_r", (-40, 0, -40), linear=True), k(0.75, "torso", (10, 30, 0), linear=True),
        k(0.95, "arm_r", (-170, 0, 0)), k(0.95, "torso", (-10, 0, 0)),
        k(1.05, "arm_r", (-20, 0, 0), linear=True), k(1.05, "torso", (25, 0, 0), linear=True), k(1.05, "body", None, (0, 1, 0)),
        k(1.2, "arm_r", (-28, 0, 4)), k(1.2, "torso", (0, 0, 0)), k(1.2, "scarf", (10, 0, 0)), k(1.2, "body", None, (0, 0, 0)),
    ])
    # stunned (2 s): he staggers, slumps, the blade's tip on the ground, his head reeling
    stun = rest(0) + ready(0) + [
        k(0.3, "body", None, (0, 1.5, 0)), k(0.3, "torso", (28, 0, 10)), k(0.3, "arm_r", (10, 0, 12)), k(0.3, "katana", (45, 0, 0)),
        k(0.3, "arm_l", (8, 0, -14)), k(0.3, "leg_r", (-20, 0, 0)), k(0.3, "shin_r", (25, 0, 0)), k(0.3, "leg_l", (12, 0, 0)),
        k(1.8, "body", None, (0, 1.5, 0)), k(1.8, "torso", (26, 0, 8)), k(1.8, "arm_r", (10, 0, 12)), k(1.8, "katana", (45, 0, 0)),
        k(1.8, "arm_l", (8, 0, -14)), k(1.8, "leg_r", (-20, 0, 0)), k(1.8, "shin_r", (25, 0, 0)), k(1.8, "leg_l", (12, 0, 0)),
    ]
    for i_, t_ in enumerate((0.3, 0.55, 0.8, 1.05, 1.3, 1.55, 1.8)):
        stun.append(k(t_, "head", (20, 18 if i_ % 2 else -18, 8 if i_ % 2 else -8)))
    anim("stun", 1200, 2.0, False, stun + rest(2.0) + ready(2.0))
    # the rift cross (1.8 s): two diagonal cuts, at 0.7 and 1.1 s (ticks 14, 22)
    anim("cross", 600, 1.8, False, rest(0) + ready(0) + [
        k(0.5, "arm_r", (-165, 0, -35)), k(0.5, "torso", (-8, 25, 0)), k(0.5, "arm_l", (-60, 0, -20)),
        k(0.7, "arm_r", (-25, 0, 45), linear=True), k(0.7, "torso", (15, -30, 0), linear=True),
        k(0.9, "arm_r", (-165, 0, 40)), k(0.9, "torso", (-8, -25, 0)),
        k(1.1, "arm_r", (-25, 0, -45), linear=True), k(1.1, "torso", (15, 30, 0), linear=True), k(1.1, "scarf", (60, 0, 0)),
        k(1.4, "arm_r", (-30, 0, -40)), k(1.4, "torso", (12, 25, 0)),
    ] + rest(1.8) + ready(1.8))
    # the thousand cuts' leap (1.0 s): crouches, springs up, the blade overhead
    anim("leap", 700, 1.0, False, rest(0) + ready(0) + [
        k(0.3, "body", None, (0, 3, 0)), k(0.3, "leg_r", (-40, 0, 0)), k(0.3, "shin_r", (60, 0, 0)), k(0.3, "leg_l", (-40, 0, 0)),
        k(0.3, "shin_l", (60, 0, 0)), k(0.3, "arm_r", (-20, 0, 10)),
        k(0.5, "body", None, (0, 0, 0)), k(0.5, "leg_r", (-70, 0, 0)), k(0.5, "shin_r", (110, 0, 0)), k(0.5, "leg_l", (-50, 0, 0)),
        k(0.5, "shin_l", (90, 0, 0)), k(0.5, "arm_r", (-170, 0, -10)), k(0.5, "arm_l", (-160, 0, 10)), k(0.5, "scarf", (40, 0, 0)),
        k(1.0, "leg_r", (-70, 0, 0)), k(1.0, "shin_r", (110, 0, 0)), k(1.0, "leg_l", (-50, 0, 0)), k(1.0, "shin_l", (90, 0, 0)),
        k(1.0, "arm_r", (-170, 0, -10)), k(1.0, "arm_l", (-160, 0, 10)),
    ])
    # its plunge (1.0 s): the blade driven down at 0.5 s (tick 10)
    anim("plunge", 800, 1.0, False, [
        k(0, "arm_r", (-175, 0, -5)), k(0, "arm_l", (-170, 0, 5)), k(0, "leg_r", (-60, 0, 0)), k(0, "shin_r", (90, 0, 0)),
        k(0, "leg_l", (-40, 0, 0)), k(0, "shin_l", (70, 0, 0)), k(0, "scarf", (80, 0, 0)),
        k(0.5, "arm_r", (-40, 0, 0), linear=True), k(0.5, "arm_l", (-50, 0, 0), linear=True), k(0.5, "torso", (35, 0, 0), linear=True),
        k(0.5, "body", None, (0, 4, 0), linear=True), k(0.5, "leg_r", (-70, 0, 0), linear=True), k(0.5, "shin_r", (100, 0, 0), linear=True),
        k(0.5, "leg_l", (20, 0, 0), linear=True), k(0.5, "shin_l", (60, 0, 0), linear=True),
        k(0.8, "torso", (35, 0, 0)), k(0.8, "body", None, (0, 4, 0)),
    ] + rest(1.0) + ready(1.0))
    # the change (4 s): sinks to one knee, head bowed; at 2 s the head snaps up; rises at 3 s with a flick of the blade
    anim("transform", 900, 4.0, False, rest(0) + ready(0) + [
        k(0.6, "body", None, (0, 5, 0)), k(0.6, "leg_r", (-90, 0, 0)), k(0.6, "shin_r", (90, 0, 0)), k(0.6, "leg_l", (10, 0, 0)),
        k(0.6, "shin_l", (80, 0, 0)), k(0.6, "torso", (20, 0, 0)), k(0.6, "head", (35, 0, 0)), k(0.6, "arm_r", (-10, 0, 15)),
        k(0.6, "katana", (60, 0, 0)), k(1.9, "head", (38, 0, 0)), k(1.9, "torso", (22, 0, 0)), k(1.9, "body", None, (0, 5, 0)),
        k(2.0, "head", (-25, 0, 0), linear=True), k(2.0, "torso", (5, 0, 0), linear=True),
        k(2.9, "body", None, (0, 5, 0)), k(2.9, "leg_r", (-90, 0, 0)), k(2.9, "shin_r", (90, 0, 0)), k(2.9, "head", (-20, 0, 0)),
        k(3.2, "body", None, (0, 0, 0)), k(3.2, "leg_r", (0, 0, 0)), k(3.2, "shin_r", (0, 0, 0)), k(3.2, "leg_l", (0, 0, 0)),
        k(3.2, "shin_l", (0, 0, 0)), k(3.2, "arm_r", (-100, 0, 60)), k(3.2, "katana", (0, 0, 0)), k(3.2, "torso", (0, -30, 0)),
        k(3.5, "arm_r", (-30, 0, -20), linear=True), k(3.5, "torso", (0, 10, 0), linear=True), k(3.5, "scarf", (60, 0, 0)),
    ] + rest(4.0) + ready(4.0))
    # seppuku (6 s): kneels, turns the blade on himself, drives it in at 2.5 s, draws it across, bows, falls forward
    death = rest(0) + ready(0) + kneel(0.8) + [k(0.8, "torso", (5, 0, 0)), k(0.8, "head", (10, 0, 0)), k(0.8, "arm_r", (-30, 0, 10))]
    death += kneel(1.6) + [
        k(1.6, "arm_r", (-70, 0, 25)), k(1.6, "forearm_r", (-60, 0, 0)), k(1.6, "arm_l", (-70, 0, -25)), k(1.6, "forearm_l", (-60, 0, 0)),
        k(1.6, "katana", (-50, 0, 0), (0, 0, 0)), k(1.6, "head", (25, 0, 0)), k(1.6, "torso", (8, 0, 0)),
        k(2.3, "katana", (-55, 0, 0), (0, 0, 3)), k(2.3, "forearm_r", (-55, 0, 0)), k(2.3, "forearm_l", (-55, 0, 0)),
    ] + kneel(2.5) + [
        k(2.5, "katana", (-25, 0, 0), (0, 0, -4), linear=True), k(2.5, "forearm_r", (-90, 0, 0), linear=True),
        k(2.5, "forearm_l", (-90, 0, 0), linear=True), k(2.5, "torso", (24, 0, 0), linear=True), k(2.5, "head", (40, 0, 0), linear=True),
        k(3.4, "katana", (-25, -30, 0), (2, 0, -4)), k(3.4, "arm_r", (-60, 0, 45)), k(3.4, "arm_l", (-60, 0, -5)), k(3.4, "torso", (28, 0, 0)),
    ] + kneel(4.2) + [
        k(4.2, "torso", (35, 0, 0)), k(4.2, "head", (45, 0, 0)), k(4.2, "katana", (-25, -30, 0), (2, 0, -4)),
    ] + kneel(5.0, 9) + [
        k(5.0, "torso", (80, 0, 0)), k(5.0, "head", (20, 0, 0)), k(5.0, "arm_r", (-10, 0, 30)), k(5.0, "arm_l", (-10, 0, -30)),
        k(5.0, "forearm_r", (0, 0, 0)), k(5.0, "forearm_l", (0, 0, 0)), k(5.0, "katana", (-170, -30, 0), (2, 0, -4)), k(5.0, "scarf", (100, 0, 0)),
        k(6.0, "torso", (80, 0, 0)), k(6.0, "head", (20, 0, 0)), k(6.0, "scarf", (100, 0, 0)),
    ] + kneel(6.0, 9)
    anim("death", 1000, 6.0, False, death)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    for phase in (1, 2):
        tex, glow = Tex(TEX_W, TEX_H, seed=5), Tex(TEX_W, TEX_H, seed=5)
        paint(tex, glow, phase, random.Random(31))
        name = "ronin" if phase == 1 else "ronin_p2"
        tex.save(os.path.join(tex_dir, name + ".png"))
        if phase == 2:
            glow.save(os.path.join(tex_dir, "ronin_p2_glow.png"))
    rel = []
    for prt, parent, pivot, rot in PARTS:
        base = ABS[parent] if parent else (0, 0, 0)
        rel.append((prt, parent, tuple(pivot[i] - base[i] for i in range(3)), rot))
    scene, objs = build_scene(rel, CUBES, os.path.join(tex_dir, "ronin.png"), TEX_W, TEX_H, "ronin")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved ronin parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
