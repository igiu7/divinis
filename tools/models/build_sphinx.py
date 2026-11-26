"""
Builds the Sphinx (boss.Sphinx) in Blender: a winged lion with a pharaoh's head, guardian of the desert.

  body        a lion's body: the deep chest and its shoulder muscles, the shoulder blades and hips, the ribs, the
              haunches; a cloth of lapis worked with gold over his back, fringed; the usekh collar of gold, lapis,
              turquoise and carnelian beads, its drops; under it a pectoral, a winged scarab under the sun; a
              cartouche of glyphs on each flank
  head        a man's face (the painted brows, kohl-lined eyes, the nose and its wings, the lips, the ears) under the
              striped nemes: its cap and crown, the inlaid brow band, the sides flaring in three steps, the two lappets
              edged with gold falling over the chest, the back gathered into the queue; the uraeus (a rearing cobra,
              its hood inlaid) on the brow, its body coiling back over the cap; the jaw (opens as he speaks: the teeth)
              with the chin, the plaited false beard on its gold holder, its straps up to the ears
  wings       falcon wings, folded along the back at rest: a gold arm, two rows of covert scales, the secondaries and
              (on the outer segment) seven fanned primaries, in bands of lapis, turquoise and carnelian with gold tips
  legs        the forelegs (shoulder, forearm, paw with four toes and their claws), the hind legs (the great thigh,
              the hock, the long paw); inlaid bands of gold round the upper arm, the wrist and the ankle
  tail        a lion's tail of four segments curling up, ringed with gold, a dark tuft at its end

One model, textures from one painting, at two texels to the model's pixel (the UVs in 512x128, the pictures
1024x256), every texel placed on the model so the patterns run on across the boxes: phase 1 (sphinx.png) tawny fur
in short strokes, the nemes' pleats of gold and lapis (each lit above, shadowed below, clouded, flecked with gold);
phase 2 (sphinx_p2.png) weathered sandstone in courses cracked through with burning turquoise, the nemes black and
gold, the wings dark; glows: sphinx_glow.png (the uraeus' eyes) and sphinx_p2_glow.png (the eyes, the cracks, the
glyphs, the turquoise inlays, the feathers' tips).

  blender -b --python tools/models/build_sphinx.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/SphinxModelData.java. Model space: the ground at y 24, the front -z, his
right -x; client.SphinxRenderer draws it 1.5 times its pixels (about 4 blocks to the top of the nemes).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 512, 128
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


# the wings' rest (folded): turned back along the body and rolled over, the feathers hanging down the flanks
WING_REST = (-10, 96, 120)


def xs(sx, a, b):
    """x from a to b on the side sx (-1 his right, 1 his left), as (min, max)."""
    return tuple(sorted((sx * a, sx * b)))


def model():
    part("body", None, (0, 6, 0))

    # ---- the lion's body
    box("body", (-7, -3, -16), (7, 9, -4), "fur")                      # the deep chest
    box("body", (-6.2, -2, -4), (6.2, 7.6, 8), "fur")                  # the ribs
    box("body", (-5.6, 7.6, -3), (5.6, 8.6, 7), "fur_light")           # the belly
    box("body", (-6.8, -2.6, 8), (6.8, 8, 17), "fur")                  # the haunches
    box("body", (-6.4, 8.6, -15), (6.4, 9.8, -6), "fur_light")         # under the chest
    for sx in (-1, 1):
        box("body", (xs(sx, 6.6, 7.8)[0], -1.5, -14.5), (xs(sx, 6.6, 7.8)[1], 6.5, -5.5), "fur")       # shoulder muscle
        box("body", (xs(sx, 6.2, 7.6)[0], -1.2, 9), (xs(sx, 6.2, 7.6)[1], 7.5, 16.5), "fur")             # haunch muscle
        box("body", (xs(sx, 6.0, 6.7)[0], 0, -2), (xs(sx, 6.0, 6.7)[1], 6, 6), "cartouche")              # a cartouche of glyphs
        box("body", (xs(sx, 1.6, 6.2)[0], -3.5, -15), (xs(sx, 1.6, 6.2)[1], -3, -9.4), "fur")            # the shoulder blade
        box("body", (xs(sx, 1.8, 6.2)[0], -3.4, 9.6), (xs(sx, 1.8, 6.2)[1], -2.6, 15.6), "fur")          # the hip
    # the cloth over his back, fringed before and behind
    box("body", (-5.4, -3.9, -9.2), (5.4, -3, 9.2), "cloth")
    box("body", (-5.4, -3.9, -9.6), (5.4, -1.6, -9.2), "fringe")
    box("body", (-5.4, -3.9, 9.2), (5.4, -1.6, 9.6), "fringe")
    # the usekh collar over chest and shoulders; under it the pectoral, a winged scarab under the sun
    box("body", (-7.6, -4.2, -17), (7.6, 2.5, -15.6), "collar")
    box("body", (-7.4, -4.4, -15.6), (7.4, -3, -9), "collar")
    for sx in (-1, 1):
        box("body", (xs(sx, 7, 8.2)[0], -3.6, -16.4), (xs(sx, 7, 8.2)[1], 1.6, -10), "collar")
    box("body", (-4.8, 2.4, -17.4), (4.8, 4.8, -16.8), "pectoral")
    box("body", (-1.2, 2.2, -18.1), (1.2, 5.2, -17.4), "scarab")
    box("body", (-0.9, 1.4, -17.8), (0.9, 2.4, -17.2), "sun")

    # ---- the neck and the head
    part("neck", "body", (0, -3, -13))
    box("neck", (-3.4, -8.5, -17), (3.4, -2.5, -11), "skin")
    part("head", "neck", (0, -8, -15))
    box("head", (-4, -15.5, -21), (4, -7.5, -13), "face")               # the face
    box("head", (-3.8, -14, -21.4), (3.8, -13.5, -21), "brow")          # the brow, its painted brows
    box("head", (-0.75, -13.5, -21.8), (0.75, -10.5, -21), "nose")      # the nose
    box("head", (-1.4, -11, -21.5), (1.4, -10.5, -21), "nostril")       # the wings of the nose
    box("head", (-1.75, -8, -21.3), (1.75, -7.5, -21), "lip_u")      # the upper lip
    box("head", (-2.2, -8.2, -20.5), (2.2, -7.3, -16), "mouth")         # in the mouth (seen when the jaw drops)
    for sx in (-1, 1):
        box("head", (xs(sx, 4, 4.9)[0], -13.6, -20.4), (xs(sx, 4, 4.9)[1], -10.4, -19.4), "ear")        # the ears
    # the nemes: the cap and its crown, the brow band, the sides flaring in three steps, the lappets, the queue
    box("head", (-5, -18, -21.4), (5, -15.5, -12), "nemes")
    box("head", (-4.4, -19, -20.4), (4.4, -18, -13), "nemes")
    box("head", (-3.4, -19.6, -19.6), (3.4, -19, -14), "nemes")
    box("head", (-4.8, -17.6, -12.4), (4.8, -9.2, -11.6), "nemes_h")   # gathered at the back
    box("head", (-5.2, -16, -21.8), (5.2, -15.1, -20.8), "band")        # the brow band
    for sx in (-1, 1):
        box("head", (xs(sx, 5, 5.3)[0], -16, -20.8), (xs(sx, 5, 5.3)[1], -15.1, -14), "band")
        box("head", (xs(sx, 4, 6.0)[0], -16, -19.6), (xs(sx, 4, 6.0)[1], -13.6, -12.2), "nemes_side")
        box("head", (xs(sx, 4, 6.9)[0], -13.6, -19.4), (xs(sx, 4, 6.9)[1], -11.4, -12.4), "nemes_side")
        box("head", (xs(sx, 4, 7.7)[0], -11.4, -19.2), (xs(sx, 4, 7.7)[1], -9.2, -12.6), "nemes_side")
        lap = f"lappet_{'r' if sx < 0 else 'l'}"
        part(lap, "head", (sx * 5.4, -9.5, -17))                                                         # the lappets, falling over the chest
        box(lap, (xs(sx, 4.2, 6.6)[0], -9.5, -18.6), (xs(sx, 4.2, 6.6)[1], 1.5, -15.8), "nemes_lap")
        box(lap, (xs(sx, 4.1, 6.7)[0], 1.5, -18.7), (xs(sx, 4.1, 6.7)[1], 2.5, -15.7), "band")
    part("queue", "head", (0, -12, -12))                                                                 # the queue down the nape
    box("queue", (-1.6, -12.5, -12.2), (1.6, -2, -10.6), "nemes_h")
    box("queue", (-1.8, -7, -12.3), (1.8, -6.2, -10.5), "band")
    box("queue", (-1.8, -2, -12.3), (1.8, -1, -10.5), "band")
    # the uraeus: a rearing cobra on the brow, its body coiling back over the cap
    box("head", (-0.6, -19, -22.6), (0.6, -16, -21.7), "cobra")
    box("head", (-1.5, -20.4, -23), (1.5, -18, -22.4), "cobra_hood")
    box("head", (-0.6, -21.2, -23.5), (0.6, -20.4, -22.5), "cobra_head")
    box("head", (-0.5, -18.6, -21.7), (0.5, -18, -20.4), "cobra")
    box("head", (-0.55, -19.6, -20.5), (0.55, -19, -19.5), "cobra")
    box("head", (-1.6, -20.2, -19.7), (1.6, -19.6, -18.9), "cobra")
    box("head", (-0.5, -20.2, -18.9), (0.5, -19.6, -16.5), "cobra")
    # the jaw: the lower lip, the chin; the plaited false beard under it on its gold holder
    part("jaw", "head", (0, -7.5, -15))
    box("jaw", (-3.4, -7.5, -21), (3.4, -5.8, -15), "face_jaw")
    box("jaw", (-1.5, -7.5, -21.3), (1.5, -7, -21), "lip_l")
    box("jaw", (-1.2, -6.9, -21.2), (1.2, -6, -21), "chin")
    part("beard", "jaw", (0, -5.8, -20), (14, 0, 0))
    lbox("beard", (-1.3, -0.5, -1.4), (2.6, 0.9, 2.2), "gold")
    lbox("beard", (-1, 0.4, -1.2), (2, 5.4, 1.8), "beard")
    lbox("beard", (-1.15, 5.8, -1.5), (2.3, 0.9, 2.4), "gold")

    # ---- the wings, folded along the back at rest
    for s, sx in (("r", -1), ("l", 1)):
        w1, w2 = f"wing_{s}", f"wing2_{s}"
        part(w1, "body", (sx * 7.2, -3.2, -10), (WING_REST[0], -sx * WING_REST[1], -sx * WING_REST[2]))
        lbox(w1, (min(0, sx * 16), -1, -1), (16, 1.8, 2.4), "wing_arm")             # the arm
        lbox(w1, (min(0, sx * 16), -0.5, 1.4), (16, 0.9, 3.6), "covert")            # the coverts, two rows of scales
        lbox(w1, (min(0, sx * 15.5), -0.2, 5), (15.5, 0.8, 3.2), "covert2")
        for i in range(8):                                                           # the secondaries
            x0 = sx * (i * 2) if sx > 0 else sx * (i * 2 + 2)
            lbox(w1, (x0, 0.1 + 0.05 * (i % 2), 7.6), (2, 0.6, 9 + i * 0.45), "feather_a" if i % 2 == 0 else "feather_b")
        part(w2, w1, (ABS[w1][0] + sx * 16, ABS[w1][1], ABS[w1][2]), (0, 0, 0))
        lbox(w2, (min(0, sx * 13), -0.9, -0.9), (13, 1.6, 2.2), "wing_arm")
        lbox(w2, (min(0, sx * 13), -0.4, 1.3), (13, 0.8, 3.4), "covert")
        for j in range(7):                                                           # the primaries, fanned
            f = f"primary_{s}{j}"
            px = ABS[w2][0] + sx * (j * 1.9 + 0.6)
            part(f, w2, (px, ABS[w2][1] + 0.15, ABS[w2][2] + 4.2), (0, -sx * (4 + j * 7.5), 0))
            lbox(f, (-1.1, -0.3, 0), (2.2, 0.6, 14 + j * 1.3), "feather_c" if j % 2 == 0 else "feather_a")

    # ---- the legs
    for s, sx in (("r", -1), ("l", 1)):
        lf, ff, pf = f"leg_f{s}", f"fore_f{s}", f"paw_f{s}"
        part(lf, "body", (sx * 4.6, 3, -10))
        box(lf, (xs(sx, 2, 7.4)[0], 1, -13.4), (xs(sx, 2, 7.4)[1], 13, -6.8), "fur")
        box(lf, (xs(sx, 1.9, 7.5)[0], 3, -13.5), (xs(sx, 1.9, 7.5)[1], 4.2, -6.7), "band")             # the armlet
        part(ff, lf, (sx * 4.7, 13, -10))
        box(ff, (xs(sx, 2.4, 7)[0], 13, -12.4), (xs(sx, 2.4, 7)[1], 21, -8.2), "fur")
        box(ff, (xs(sx, 2.2, 7.2)[0], 14, -12.6), (xs(sx, 2.2, 7.2)[1], 15.6, -8), "band")             # the bracelet
        part(pf, ff, (sx * 4.7, 21, -10))
        box(pf, (xs(sx, 1.8, 7.6)[0], 21, -15), (xs(sx, 1.8, 7.6)[1], 24, -7.8), "fur")
        for t in range(4):                                                                            # four toes, their claws
            x0 = 2.0 + t * 1.42
            box(pf, (xs(sx, x0, x0 + 1.3)[0], 21.6, -16.2), (xs(sx, x0, x0 + 1.3)[1], 24, -15), "toe")
            box(pf, (xs(sx, x0 + 0.3, x0 + 1.0)[0], 23.2, -17), (xs(sx, x0 + 0.3, x0 + 1.0)[1], 24, -16.2), "claw")

        lb, sb, pb = f"leg_b{s}", f"shin_b{s}", f"paw_b{s}"
        part(lb, "body", (sx * 4.6, 3, 12))
        box(lb, (xs(sx, 1.8, 7.8)[0], 1, 7.6), (xs(sx, 1.8, 7.8)[1], 13, 17.4), "fur")                # the great thigh
        part(sb, lb, (sx * 4.7, 13, 15))
        box(sb, (xs(sx, 2.6, 6.8)[0], 13, 13.2), (xs(sx, 2.6, 6.8)[1], 21, 17.4), "fur")              # the hock
        box(sb, (xs(sx, 2.4, 7.0)[0], 19, 13), (xs(sx, 2.4, 7.0)[1], 20.4, 17.6), "band")             # the anklet
        part(pb, sb, (sx * 4.7, 21, 15))
        box(pb, (xs(sx, 2.0, 7.4)[0], 21, 9.6), (xs(sx, 2.0, 7.4)[1], 24, 17.6), "fur")
        for t in range(4):
            x0 = 2.2 + t * 1.32
            box(pb, (xs(sx, x0, x0 + 1.2)[0], 21.8, 8.4), (xs(sx, x0, x0 + 1.2)[1], 24, 9.6), "toe")
            box(pb, (xs(sx, x0 + 0.3, x0 + 0.9)[0], 23.3, 7.7), (xs(sx, x0 + 0.3, x0 + 0.9)[1], 24, 8.4), "claw")

    # ---- the tail, curling up, ringed with gold, its tuft
    part("tail", "body", (0, -1, 16.6), (-30, 0, 0))
    lbox("tail", (-0.8, -0.8, 0), (1.6, 1.6, 7), "fur")
    lbox("tail", (-0.95, -0.95, 0.6), (1.9, 1.9, 1), "band")
    part("tail2", "tail", (0, -1, 16.6 + 7), (-25, 0, 0))   # a child's pivot: its parent's plus the offset in the parent's turn
    lbox("tail2", (-0.7, -0.7, 0), (1.4, 1.4, 6.5), "fur")
    part("tail3", "tail2", (0, -1, 16.6 + 13.5), (-25, 0, 0))
    lbox("tail3", (-0.6, -0.6, 0), (1.2, 1.2, 6), "fur")
    lbox("tail3", (-0.75, -0.75, 4.6), (1.5, 1.5, 0.8), "band")
    part("tuft", "tail3", (0, -1, 16.6 + 19.5), (10, 0, 0))
    lbox("tuft", (-1.3, -1.3, -0.5), (2.6, 2.6, 4), "tuft")
    lbox("tuft", (-0.9, -0.9, 3.5), (1.8, 1.8, 1.5), "tuft")


# ---------------------------------------------------------------- painting
# Painted at S texels to the model's pixel (the model's UVs stay in its 512x128 pixels: the picture is 1024x256).
S = 2
PALETTES = {
    1: {"fur": [(86, 52, 22), (116, 74, 32), (148, 102, 48), (178, 130, 66), (204, 158, 88), (226, 186, 118), (242, 212, 152)],
        "fur_light": [(150, 108, 58), (182, 140, 84), (208, 170, 112), (228, 198, 146), (244, 222, 176)],
        "skin": [(92, 50, 28), (120, 68, 38), (148, 90, 52), (174, 112, 68), (198, 136, 88), (218, 162, 112), (234, 186, 138)],
        "lip": [(92, 36, 26), (122, 52, 36), (150, 72, 52), (174, 96, 72)],
        "lapis": [(8, 14, 48), (14, 24, 74), (20, 36, 104), (28, 52, 138), (40, 72, 172), (60, 98, 202), (96, 134, 226)],
        "fleck": (236, 198, 92),
        "feather_a": [(12, 22, 66), (20, 36, 104), (32, 56, 146), (48, 82, 184), (76, 114, 214), (120, 152, 232)],
        "feather_b": [(10, 66, 70), (18, 100, 102), (30, 138, 136), (52, 174, 166), (96, 208, 194), (150, 232, 218)],
        "feather_c": [(84, 20, 14), (120, 34, 22), (158, 52, 32), (192, 76, 46), (218, 108, 72), (238, 148, 110)],
        "eye_white": (232, 224, 204), "iris": (96, 58, 26), "pupil": (16, 10, 8), "kohl": (20, 13, 9), "eye_glow": False,
        "crack": None},
    2: {"fur": [(50, 38, 28), (70, 54, 40), (94, 74, 56), (120, 98, 74), (146, 122, 94), (172, 148, 116), (196, 174, 140)],
        "fur_light": [(94, 74, 56), (120, 98, 74), (146, 122, 94), (172, 148, 116), (194, 172, 140)],
        "skin": [(50, 38, 28), (70, 54, 40), (94, 74, 56), (120, 98, 74), (146, 122, 94), (168, 144, 112), (188, 166, 132)],
        "lip": [(48, 36, 28), (64, 48, 38), (82, 64, 50), (100, 80, 64)],
        "lapis": [(4, 4, 6), (8, 8, 12), (14, 14, 20), (22, 22, 30), (32, 32, 44), (46, 46, 62), (66, 66, 86)],
        "fleck": (80, 240, 222),
        "feather_a": [(6, 6, 10), (12, 14, 22), (20, 24, 36), (30, 36, 52), (44, 52, 72), (62, 72, 96)],
        "feather_b": [(6, 18, 20), (10, 30, 32), (16, 44, 46), (24, 60, 62), (36, 80, 80), (52, 102, 100)],
        "feather_c": [(14, 10, 8), (24, 16, 14), (36, 26, 20), (50, 36, 28), (66, 48, 38), (84, 62, 50)],
        "eye_white": (150, 250, 238), "iris": (60, 235, 215), "pupil": (230, 255, 252), "kohl": (8, 6, 5), "eye_glow": True,
        "crack": [(30, 180, 172), (70, 232, 216), (170, 255, 244)]},
}
FIXED = {
    "gold": [(84, 52, 12), (122, 82, 20), (164, 116, 30), (204, 156, 48), (232, 192, 80), (250, 222, 132), (255, 244, 196)],
    "turquoise": [(10, 64, 66), (18, 98, 98), (30, 134, 130), (52, 172, 162), (96, 208, 192), (156, 234, 220)],
    "carnelian": [(78, 16, 10), (112, 28, 16), (150, 44, 26), (186, 66, 40), (214, 100, 64), (236, 142, 104)],
    "ivory": [(140, 128, 106), (176, 166, 144), (206, 198, 178), (230, 224, 206), (248, 244, 232)],
    "tuft": [(22, 12, 6), (36, 22, 10), (52, 32, 16), (70, 46, 24), (92, 62, 34)],
    "mouth": [(26, 6, 6), (46, 12, 10), (70, 22, 18), (120, 48, 42), (156, 72, 64)],
}
FACE_LIGHT = {"top": 1.0, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.35, "bottom": -1.0}
# a face's texture axes: (across, down) as the model's axes (0 x, 1 y, 2 z)
AXES = {"top": (0, 2), "bottom": (0, 2), "front": (0, 1), "back": (0, 1), "left": (2, 1), "right": (2, 1)}


def ramp(r, t):
    """The colour of a ramp at t (0 darkest, 1 lightest)."""
    return r[max(0, min(len(r) - 1, int(round(t * (len(r) - 1)))))]


def hsh(*a):
    n = 2166136261
    for v in a:
        n = ((n ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    n ^= n >> 15
    n = (n * 2246822519) & 0xFFFFFFFF
    n ^= n >> 13
    return (n & 0xFFFF) / 65535.0


def vnoise(x, y, z, seed=0):
    """Smooth value noise, about 1 a cell."""
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out += w * hsh(ix + dx, iy + dy, iz + dz, seed)
    return out


def point(face, o, size, fu, fv):
    """Where on the box (the part's model pixels) a texel lies: fu, fv its place across and down its face (0..1)."""
    (x0, y0, z0), (w, h, d) = o, size
    return {
        "top": (x0 + fu * w, y0, z0 + d - fv * d),
        "bottom": (x0 + fu * w, y0 + h, z0 + d - fv * d),
        "right": (x0, y0 + fv * h, z0 + d - fu * d),
        "front": (x0 + fu * w, y0 + fv * h, z0),
        "left": (x0 + w, y0 + fv * h, z0 + fu * d),
        "back": (x0 + w - fu * w, y0 + fv * h, z0 + d),
    }[face]


class Px:
    """One texel being painted: its cube, face, place on the face and on the model."""
    __slots__ = ("mat", "face", "x", "y", "w", "h", "fu", "fv", "p", "a", "b", "light", "phase", "pal", "size", "cube", "o")


def paint(tex, glow, phase, rnd):
    pal = PALETTES[phase]
    q = Px()
    q.phase, q.pal = phase, pal
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        piv = ABS[prt]
        o = tuple(origin[i] + piv[i] for i in range(3))
        q.mat, q.size, q.cube, q.o = mat, size, name, o
        for face, rect in faces(uv[0], uv[1], *size).items():
            x0, y0 = int(round(rect[0] * S)), int(round(rect[1] * S))
            x1, y1 = int(round((rect[0] + rect[2]) * S)), int(round((rect[1] + rect[3]) * S))
            fw, fh = max(1, x1 - x0), max(1, y1 - y0)
            q.face, q.w, q.h, q.light = face, fw, fh, FACE_LIGHT[face]
            ax_a, ax_b = AXES[face]
            for y in range(fh):
                for x in range(fw):
                    q.x, q.y = x, y
                    q.fu, q.fv = (x + 0.5) / fw, (y + 0.5) / fh
                    q.p = point(face, o, size, q.fu, q.fv)
                    q.a, q.b = math.floor(q.p[ax_a] * S), math.floor(q.p[ax_b] * S)
                    c, glows = paint_px(q, rnd)
                    tex.set(x0 + x, y0 + y, c, 0.0)
                    if glows:
                        glow.set(x0 + x, y0 + y, c, 0.0)
            # phase 2: the stone of his body cracked through with burning turquoise
            if pal["crack"] and mat in ("fur", "fur_light", "skin", "face", "face_jaw") and fw * fh >= 16:
                for _ in range(max(1, fw * fh // 90)):
                    cx, cy = rnd.randrange(fw), rnd.randrange(fh)
                    for step in range(rnd.randint(5, 13)):
                        c = pal["crack"][2 if step % 4 == 0 else rnd.randrange(2)]
                        tex.set(x0 + cx, y0 + cy, c, 0.0)
                        glow.set(x0 + cx, y0 + cy, c, 0.0)
                        cx = max(0, min(fw - 1, cx + rnd.choice((-1, 0, 1))))
                        cy = max(0, min(fh - 1, cy + rnd.choice((-1, 1, 1))))


GLYPHS = [
    [(0, 0), (1, 0), (2, 0), (1, 1), (1, 2)],            # a staff
    [(0, 1), (1, 0), (2, 1), (1, 2)],                    # an eye
    [(0, 0), (0, 1), (0, 2), (1, 2), (2, 2)],            # a foot
    [(1, 0), (0, 1), (2, 1), (1, 1), (1, 2)],            # an ankh
    [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)],            # water
    [(0, 0), (1, 0), (2, 0), (0, 2), (1, 2), (2, 2)],    # a reed mat
]


def edge(q):
    return min(q.x, q.w - 1 - q.x, q.y, q.h - 1 - q.y)


def bevel(q, k=0.14):
    """Lighter along a face's top and left edges, darker along its bottom and right: a raised, worked piece."""
    if q.w < 3 or q.h < 2:
        return 0.0
    if q.y == 0 or q.x == 0:
        return k
    if q.y == q.h - 1 or q.x == q.w - 1:
        return -k
    return 0.0


def gold(q, t=0.55, k=0.16):
    """Burnished gold: bevelled, a sheen running across it, worn a little."""
    t += q.light * 0.12 + bevel(q, k)
    t += 0.08 * math.sin((q.a + q.b) * 0.7) + (hsh(q.a, q.b, 3) - 0.5) * 0.08
    return ramp(FIXED["gold"], t)


def fur(q, r):
    """A lion's short fur: strokes of a few texels running down the flanks (back along the top), a little darker
    underneath; in phase 2 weathered sandstone in courses."""
    p = q.p
    if q.phase == 1:
        along, across = (q.b, q.a)
        off = hsh(across, 11) * 4
        seg = math.floor((along + off) / 4)
        pos = (along + off) % 4
        t = 0.56 + q.light * 0.16 + (hsh(across, seg, 7) - 0.5) * 0.13 - (0.07 if pos >= 3 else 0)
        t += (vnoise(p[0] * 0.3, p[1] * 0.3, p[2] * 0.3, 1) - 0.5) * 0.16
        if q.face not in ("top", "bottom"):
            t -= q.fv * 0.16
        if q.face != "top" and q.y == q.h - 1:
            t -= 0.08
        return ramp(r, t), False
    course = math.floor(p[1] / 3 + 100)
    joint = (q.b % 6 == 5) or ((q.a + course * 5) % 11 == 10)
    t = 0.55 + q.light * 0.16 + (vnoise(p[0] * 0.6, p[1] * 0.6, p[2] * 0.6, 2) - 0.5) * 0.3
    t += 0.05 * math.sin(p[1] * 4.1 + vnoise(p[0], 0, p[2], 3) * 3)
    if joint:
        t -= 0.22
    elif hsh(q.a, q.b, 9) > 0.93:
        t -= 0.14                                   # a pit worn in the stone
    return ramp(r, t), False


def face_front(q):
    """His face: kohl-lined eyes under painted brows, the cheekbones, the folds to the mouth."""
    pal, sk = q.pal, q.pal["skin"]
    X, Y = q.p[0], q.p[1]
    ax = abs(X)
    t = 0.62 - max(0.0, ax - 2.6) * 0.14 + (vnoise(X * 0.6, Y * 0.6, 0, 4) - 0.5) * 0.08
    row = math.floor((Y + 15.5) * S)
    glows = pal["eye_glow"]
    if row == 4 and ax > 1.0:
        return pal["kohl"], False                                   # the upper lid, drawn out to the temple
    if row == 5:
        if ax < 1.0:
            return ramp(sk, t - 0.1), False
        if ax < 1.5 or 3.0 < ax < 3.5:
            return pal["eye_white"], glows
        if 1.5 < ax < 2.0 or 2.5 < ax < 3.0:
            return pal["iris"], glows
        if 2.0 < ax < 2.5:
            return pal["pupil"], glows
        return pal["kohl"], False
    if row == 6:
        if 1.0 < ax < 3.5:
            return ramp(sk, 0.12) if q.phase == 1 else pal["kohl"], False   # the lower lid
        if ax > 3.5:
            return pal["kohl"], False                               # the kohl's tail
    if row == 7 and 1.0 < ax < 3.5:
        t -= 0.08
    if row in (8, 9) and 2.0 < ax < 3.5:
        t += 0.12                                                   # the cheekbones
    if row in (8, 9, 10) and 0.5 < ax < 1.5:
        t -= 0.1                                                    # beside the nose
    if row in (10, 11, 12) and abs(ax - (1.75 + (row - 10) * 0.25)) < 0.3:
        t -= 0.14                                                   # the folds to the mouth
    if row in (10, 11, 12) and ax < 0.5:
        t -= 0.06                                                   # the groove over the lip
    if row == 15 and 1.6 < ax < 2.1:
        return ramp(sk, 0.05), False                                # the corners of the mouth
    if row == 14 and ax < 1.75:
        t -= 0.05
    if row <= 2:
        t += 0.06
    return ramp(sk, t), False


def nemes(q, key):
    """The nemes: pleated bands of gold and lapis (black in phase 2) inlaid as on the king's mask: each band lit on
    its upper edge, shadowed under the next; folds across, the colour clouded, gold flecks in the lapis."""
    pal = q.pal
    m = key % 6
    p = q.p
    fold = 0.06 * math.sin((q.a if key == q.b else q.b) * 0.55 + vnoise(p[0] * 0.3, p[1] * 0.3, p[2] * 0.3, 5) * 4)
    shade = q.light * 0.12 + fold - (q.fv - 0.5) * 0.12
    if m < 2:
        t = (0.78 if m == 0 else 0.56) + shade + (hsh(q.a, q.b, 6) - 0.5) * 0.08
        return ramp(FIXED["gold"], t), False
    if hsh(q.a, q.b, 13) > (0.95 if q.phase == 1 else 0.975):
        return pal["fleck"], q.phase == 2
    cloud = (vnoise(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9, 8) - 0.5) * 0.3
    t = {2: 0.7, 3: 0.56, 4: 0.46, 5: 0.28}[m] + shade + cloud
    return ramp(pal["lapis"], t), False


def scales(q, r, centre=None):
    """Rows of rounded covert feathers like scales, staggered, edged dark; centre colours their hearts."""
    a, b = q.a, q.b
    row = b // 3
    cx, cy = (a + (row % 2) * 2) % 4, b % 3
    lvl = ("3442", "2331", "0110")[cy][cx]
    if centre and cy == 1 and cx in (1, 2):
        return ramp(centre, 0.55 + q.light * 0.1 + (0.12 if cx == 1 else 0)), False
    t = 0.2 + int(lvl) * 0.17 + q.light * 0.1
    return ramp(r, t), False


def feather(q, r, mark):
    """A long feather: gold at the quill, the vane barbed and shaded, a shaft down its middle, a band of mark across
    it, gold at the tip (burning turquoise in phase 2)."""
    lx, ly, lz = q.p
    o = q.o
    along = (lz - o[2]) / q.size[2]
    across = (lx - o[0]) / q.size[0]
    if along > 0.9:
        if q.phase == 2:
            return q.pal["crack"][1 if along < 0.96 else 2], True
        return ramp(FIXED["gold"], 0.75 - (along - 0.9) * 3), False
    if along < 0.1:
        return ramp(FIXED["gold"], 0.45 + q.light * 0.1), False
    if q.face in ("top", "bottom"):
        if q.x == 0 or q.x == q.w - 1:
            return ramp(r, 0.05), False                            # the edge between feathers
        if q.x == q.w // 2:
            return ramp(r, 0.85 - along * 0.3), False              # the shaft
        if 0.6 < along < 0.7:
            return ramp(mark, 0.6 + q.light * 0.1), q.phase == 2 and mark is FIXED["turquoise"]
        barb = (q.b + abs(q.x * 2 - q.w)) % 4 < 2
        t = 0.35 + along * 0.4 + q.light * 0.1 - (0.12 if barb else 0)
        return ramp(r, t), False
    return ramp(r, 0.3 + along * 0.3), False


def paint_px(q, rnd):
    mat, pal, face = q.mat, q.pal, q.face
    if mat in ("fur", "fur_light"):
        return fur(q, pal[mat])
    if mat == "toe":
        t = 0.55 + q.light * 0.15 - (0.25 if q.face in ("left", "right") and (q.x == 0 or q.x == q.w - 1) else 0)
        return ramp(pal["fur_light"], t), False
    if mat == "skin":
        return fur(q, pal["skin"]) if q.phase == 2 else (ramp(pal["skin"], 0.5 + q.light * 0.15 + (vnoise(*[c * 0.5 for c in q.p], 4) - 0.5) * 0.12), False)
    if mat == "face":
        if face == "front":
            return face_front(q)
        if face in ("left", "right") and q.p[1] > -11 and q.p[2] < -19.5:
            return ramp(pal["lapis"], 0.45), False                 # the strap of the beard
        return ramp(pal["skin"], 0.5 + q.light * 0.15), False
    if mat == "brow":
        ax = abs(q.p[0])
        if face == "front" and 1.0 < ax < 3.7:
            return pal["kohl"] if q.phase == 2 else (40, 26, 16), False    # the painted brows
        return ramp(pal["skin"], 0.6 + q.light * 0.2), False
    if mat == "nose":
        if face == "front":
            t = 0.72 if abs(q.p[0]) < 0.25 else 0.55
            return ramp(pal["skin"], t - (0.15 if q.y == q.h - 1 else 0)), False
        if face == "bottom":
            return ramp(pal["skin"], 0.05 if abs(q.p[0]) > 0.25 else 0.25), False   # the nostrils
        return ramp(pal["skin"], 0.42 + q.light * 0.2), False
    if mat == "nostril":
        return ramp(pal["skin"], 0.48 + q.light * 0.15 - (0.1 if abs(q.p[0]) > 1.0 else 0)), False
    if mat in ("lip_u", "lip_l"):
        r = pal["lip"]
        if face == "front":
            if mat == "lip_u":
                return ramp(r, 0.55 if abs(q.p[0]) < 0.6 else 0.35 - (0.2 if abs(q.p[0]) > 1.4 else 0)), False
            return ramp(r, 0.85 if abs(q.p[0]) < 0.6 else 0.6), False
        return ramp(r, 0.45 + q.light * 0.2), False
    if mat == "chin":
        return ramp(pal["skin"], 0.66 + q.light * 0.15 - (0.12 if q.y == q.h - 1 else 0)), False
    if mat == "ear":
        if face == "front":
            if q.x == (0 if q.p[0] > 0 else q.w - 1) or q.y in (0, q.h - 1):
                return ramp(pal["skin"], 0.62), False
            return ramp(pal["skin"], 0.18), False                  # the hollow of the ear
        return ramp(pal["skin"], 0.5 + q.light * 0.15), False
    if mat == "face_jaw":
        ax = abs(q.p[0])
        if face == "top":
            if q.p[2] < -20.4 and ax < 2.2:
                return ramp(FIXED["ivory"], 0.85 if q.a % 2 == 0 else 0.55), False   # the lower teeth
            return ramp(FIXED["mouth"], 0.7 if ax < 1.6 else 0.15), False
        if face in ("front", "left", "right"):
            if (face == "front" and ax > 3.0) or (face != "front" and -19.8 < q.p[2] < -19.0):
                return ramp(pal["lapis"], 0.45 + (0.15 if q.y == 0 else 0)), False   # the strap of the beard
            return ramp(pal["skin"], 0.56 + q.light * 0.12 - q.fv * 0.2), False
        return ramp(pal["skin"], 0.35 + q.light * 0.15), False
    if mat == "mouth":
        if face == "front" or (face == "bottom" and q.p[2] < -20):
            return ramp(FIXED["ivory"], 0.85 if q.a % 2 == 0 else 0.5), False         # the upper teeth
        return ramp(FIXED["mouth"], 0.25 + (0.2 if face == "bottom" else 0)), False
    if mat == "nemes":
        if face in ("left", "right"):
            return nemes(q, q.b)
        return nemes(q, q.a)
    if mat in ("nemes_side", "nemes_h", "nemes_lap"):
        if mat == "nemes_side" and face == "front" and abs(q.p[0]) < 4.5:
            return gold(q, 0.6, 0), False                          # the edge framing the face
        if mat == "nemes_lap" and face in ("front", "back") and (q.x == 0 or q.x == q.w - 1):
            return gold(q, 0.6, 0), False
        if face in ("top", "bottom"):
            return gold(q, 0.5), False
        return nemes(q, q.b)
    if mat in ("gold", "wing_arm"):
        if mat == "wing_arm" and face in ("top", "front", "back", "bottom") and q.h >= 3 and q.y == q.h // 2:
            return ramp(FIXED["gold"], 0.25), False                # a line chased along it
        return gold(q), False
    if mat == "band":
        # gold, inlaid round its middle with lapis, carnelian and turquoise in turn between gold cells
        mid = q.y in range(1, q.h - 1) if q.h >= 3 else q.y == q.h - 1
        if face in ("top", "bottom") or not mid:
            return gold(q, 0.6), False
        cell = q.a if face in ("front", "back", "top", "bottom") else q.a
        if cell % 3 == 2:
            return gold(q, 0.7, 0), False
        stone = (pal["lapis"], FIXED["carnelian"], FIXED["turquoise"])[(cell // 3) % 3]
        return ramp(stone, 0.55 + (0.2 if cell % 3 == 0 else 0)), q.phase == 2 and stone is FIXED["turquoise"]
    if mat == "collar":
        # the usekh: rows of beads - gold, lapis, turquoise, carnelian, turquoise, lapis - each bead rounded
        key, run = (q.b, q.a) if face != "top" else (q.b, q.a)
        row, ry = key // 2, key % 2
        kind = row % 6
        if face == "front" and q.y >= q.h - 3:
            # the drops along its lower edge
            if q.a % 3 == 2:
                return ramp(pal["fur"], 0.15), False
            stone = (FIXED["carnelian"], pal["lapis"], FIXED["turquoise"])[(q.a // 3) % 3]
            return ramp(stone, 0.7 - (q.y - (q.h - 3)) * 0.18 + (0.15 if q.a % 3 == 0 else 0)), False
        if kind == 0:
            return gold(q, 0.7 if ry == 0 else 0.45, 0), False
        stone = (None, pal["lapis"], FIXED["turquoise"], FIXED["carnelian"], FIXED["turquoise"], pal["lapis"])[kind]
        bead = run % 2
        t = 0.5 + (0.22 if (ry == 0 and bead == 0) else 0) - (0.22 if (ry == 1 and bead == 1) else 0) + q.light * 0.1
        return ramp(stone, t), q.phase == 2 and stone is FIXED["turquoise"] and ry == 0 and bead == 0
    if mat == "pectoral":
        # the scarab's wings: gold cells of lapis scales, then long turquoise and carnelian feathers
        if face != "front":
            return gold(q), False
        if q.y in (0, q.h - 1) or q.x in (0, q.w - 1) or q.a % 3 == 2:
            return gold(q, 0.66, 0), False
        if q.y == 1:
            return ramp(pal["lapis"], 0.6 if q.a % 3 == 0 else 0.4), False
        stone = FIXED["turquoise"] if (q.a // 3) % 2 == 0 else FIXED["carnelian"]
        return ramp(stone, 0.65 - q.fv * 0.3), q.phase == 2 and stone is FIXED["turquoise"]
    if mat == "scarab":
        if face != "front":
            return gold(q, 0.45), False
        if q.y == 0 or q.y == 1:
            return gold(q, 0.6, 0), False                         # its head
        if abs(q.p[0]) < 0.3:
            return ramp(FIXED["gold"], 0.3), False                # the line between its wing-cases
        t = 0.75 - q.fv * 0.4 - abs(q.p[0]) * 0.15
        return ramp(FIXED["turquoise"], t), q.phase == 2
    if mat == "sun":
        if face == "front" and edge(q) > 0:
            return ramp(FIXED["carnelian"], 0.7 if q.y == 1 else 0.5), q.phase == 2
        return gold(q, 0.6), False
    if mat == "cloth":
        # a cloth of lapis worked with a lattice of gold, bordered in carnelian, studded with turquoise
        if face != "top":
            return ramp(FIXED["carnelian"], 0.45 + q.light * 0.1), False
        e = edge(q)
        if e == 0:
            return gold(q, 0.55, 0), False
        if e <= 2:
            return ramp(FIXED["carnelian"], 0.55 if e == 1 else 0.4), False
        if e == 3:
            return gold(q, 0.65, 0), False
        u, v = q.a % 8, q.b % 8
        if (u == v) or (u == 7 - v):
            if (u, v) in ((0, 0), (0, 7), (7, 0), (7, 7), (3, 3), (4, 4), (3, 4), (4, 3)):
                return ramp(FIXED["turquoise"], 0.7), q.phase == 2
            return gold(q, 0.6, 0), False
        return ramp(pal["lapis"], 0.45 + (vnoise(q.p[0] * 0.5, 0, q.p[2] * 0.5, 9) - 0.5) * 0.3), False
    if mat == "fringe":
        # its fringe: tassels of gold and carnelian
        if face in ("top", "bottom"):
            return gold(q, 0.5), False
        if q.y < 2:
            return gold(q, 0.6, 0), False
        if q.a % 3 == 2:
            return ramp(pal["fur"], 0.1), False
        stone = FIXED["carnelian"] if (q.a // 3) % 2 else FIXED["gold"]
        return ramp(stone, 0.65 - q.fv * 0.35 + (0.12 if q.a % 3 == 0 else 0)), False
    if mat == "cartouche":
        if face in ("left", "right"):
            if edge(q) <= 1:
                return gold(q, 0.6, 0), False
            gx, gy = (q.a // 2) % 4, (q.b // 2) % 4
            g = GLYPHS[((q.a // 8) * 3 + q.b // 8) % len(GLYPHS)]
            if (gx, gy) in g:
                return ((80, 240, 220) if q.phase == 2 else (30, 50, 132)), q.phase == 2
            return ramp(pal["fur_light"], 0.7), False
        return fur(q, pal["fur"])
    if mat == "covert":
        if q.phase == 2:
            r = [(48, 34, 10), (72, 52, 14), (100, 74, 20), (132, 100, 30), (164, 128, 44), (196, 160, 70)]
            if face == "top" and hsh(q.a, q.b, 21) < 0.08:
                return pal["crack"][1], True
            return scales(q, r, FIXED["turquoise"])
        return scales(q, FIXED["gold"], FIXED["turquoise"])
    if mat == "covert2":
        return scales(q, pal["feather_a"], FIXED["gold"])
    if mat in ("feather_a", "feather_b", "feather_c"):
        mark = {"feather_a": FIXED["turquoise"], "feather_b": pal["feather_a"], "feather_c": FIXED["gold"]}[mat]
        return feather(q, pal[mat], mark)
    if mat == "cobra":
        sc = (q.a + q.b) % 3 == 0 or (q.a - q.b) % 3 == 0
        return gold(q, 0.5 - (0.15 if sc else 0), 0.1), False
    if mat == "cobra_hood":
        if face == "front":
            if edge(q) == 0:
                return gold(q, 0.6, 0), False
            stone = (pal["lapis"], FIXED["carnelian"], FIXED["turquoise"])[q.y % 3]
            return ramp(stone, 0.6 if q.a % 2 else 0.45), False
        return gold(q, 0.5), False
    if mat == "cobra_head":
        if face == "front" and q.y == 0 and q.x in (0, q.w - 1):
            return (pal["iris"] if q.phase == 2 else (230, 40, 30)), True     # its eyes
        return gold(q, 0.55), False
    if mat == "beard":
        # plaited: chevrons of lapis, a gold thread every few rows
        if q.b % 6 == 0:
            return gold(q, 0.55, 0), False
        chev = (q.b + abs(q.x * 2 - q.w + 1) // 2) % 3
        return ramp(pal["lapis"], 0.3 + chev * 0.18 + q.light * 0.08), False
    if mat == "claw":
        return ramp(FIXED["ivory"], 0.3 + q.fv * 0.6 + q.light * 0.1), False
    if mat == "tuft":
        return ramp(FIXED["tuft"], 0.5 + (hsh(q.a, 31) - 0.5) * 0.6 + q.light * 0.1 - q.fv * 0.2), False
    raise KeyError(mat)


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


LEGS = ["leg_fr", "fore_fr", "paw_fr", "leg_fl", "fore_fl", "paw_fl", "leg_br", "shin_br", "paw_br", "leg_bl", "shin_bl", "paw_bl"]
WINGS = ["wing_r", "wing2_r", "wing_l", "wing2_l"]
MOVING = ["body", "neck", "head", "jaw", "tail", "tail2", "tail3"] + LEGS + WINGS


def rest(t, parts=MOVING):
    return [k(t, p, (0, 0, 0), (0, 0, 0)) for p in parts]


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


def wing(t, s, sx, rot, rot2, lin=False):
    """Keys a wing to a pose given as its whole turn (x, yaw out from the body, roll up), from its folded rest."""
    a = (rot[0] - WING_REST[0], sx * (WING_REST[1] - rot[1]), sx * (WING_REST[2] - rot[2]))
    return [k(t, f"wing_{s}", a, linear=lin), k(t, f"wing2_{s}", (0, -sx * rot2[0], -sx * rot2[1]), linear=lin)]


def spread(t, up=0, beat=0, lin=False):
    """The wings opened out from the back: up lifts them (degrees), beat sweeps them down."""
    out = []
    for s, sx in (("r", -1), ("l", 1)):
        out += wing(t, s, sx, (-6 - beat * 0.3, 0, 20 + up - beat), (8, up * 0.3 - beat * 0.5), lin)
    return out


def half(t):
    """The wings half open, raised from the flanks."""
    out = []
    for s, sx in (("r", -1), ("l", 1)):
        out += wing(t, s, sx, (-10, 84, 70), (6, 0))
    return out


def folded(t):
    return [k(t, w, (0, 0, 0)) for w in WINGS]


def crouch(t, low=3):
    """Low on his legs, ready to spring."""
    return [k(t, "body", (4, 0, 0), (0, low, 0)), k(t, "leg_fr", (-30, 0, 0)), k(t, "fore_fr", (50, 0, 0)), k(t, "paw_fr", (-20, 0, 0)),
            k(t, "leg_fl", (-30, 0, 0)), k(t, "fore_fl", (50, 0, 0)), k(t, "paw_fl", (-20, 0, 0)),
            k(t, "leg_br", (25, 0, 0)), k(t, "shin_br", (-45, 0, 0)), k(t, "paw_br", (20, 0, 0)),
            k(t, "leg_bl", (25, 0, 0)), k(t, "shin_bl", (-45, 0, 0)), k(t, "paw_bl", (20, 0, 0)),
            k(t, "neck", (10, 0, 0)), k(t, "head", (-14, 0, 0))]


def stand(t):
    return [k(t, p, (0, 0, 0)) for p in LEGS] + [k(t, "body", (0, 0, 0), (0, 0, 0))]


def rear(t, high=1.0):
    """Up on his hind legs, the forepaws raised."""
    return [k(t, "body", (-38 * high, 0, 0), (0, -2 * high, 2 * high)),
            k(t, "leg_fr", (-60 * high, 0, 8)), k(t, "fore_fr", (40 * high, 0, 0)), k(t, "paw_fr", (30 * high, 0, 0)),
            k(t, "leg_fl", (-75 * high, 0, -8)), k(t, "fore_fl", (55 * high, 0, 0)), k(t, "paw_fl", (30 * high, 0, 0)),
            k(t, "leg_br", (38 * high, 0, 0)), k(t, "shin_br", (-10 * high, 0, 0)), k(t, "paw_br", (0, 0, 0)),
            k(t, "leg_bl", (38 * high, 0, 0)), k(t, "shin_bl", (-10 * high, 0, 0)), k(t, "paw_bl", (0, 0, 0)),
            k(t, "neck", (30 * high, 0, 0)), k(t, "head", (-6 * high, 0, 0))]


def sit(t):
    """Sat up on his haunches as the statues sit, the forelegs straight, the head high."""
    return [k(t, "body", (-24, 0, 0), (0, 3.5, 0)),
            k(t, "leg_fr", (24, 0, 0)), k(t, "fore_fr", (0, 0, 0)), k(t, "paw_fr", (0, 0, 0)),
            k(t, "leg_fl", (24, 0, 0)), k(t, "fore_fl", (0, 0, 0)), k(t, "paw_fl", (0, 0, 0)),
            k(t, "leg_br", (-60, 0, 0)), k(t, "shin_br", (60, 0, 0)), k(t, "paw_br", (24, 0, 0)),
            k(t, "leg_bl", (-60, 0, 0)), k(t, "shin_bl", (60, 0, 0)), k(t, "paw_bl", (24, 0, 0)),
            k(t, "neck", (20, 0, 0)), k(t, "head", (4, 0, 0)), k(t, "tail", (40, 0, 0)), k(t, "tail2", (10, 0, 0))]


def speak(t0, t1, step=0.25):
    out, t, open_ = [], t0, True
    while t <= t1 + 1e-6:
        out.append(k(round(t, 3), "jaw", (16 if open_ else 2, 0, 0)))
        open_ = not open_
        t += step
    out.append(k(round(t1 + step, 3), "jaw", (0, 0, 0)))
    return out


def animations():
    anim("idle", 0, 4.0, True, [
        k(0, "body", (0, 0, 0), (0, 0, 0)), k(2, "body", (-1, 0, 0), (0, -0.3, 0)), k(4, "body", (0, 0, 0), (0, 0, 0)),
        k(0, "head", (0, 0, 0)), k(1.5, "head", (-3, 6, 0)), k(3, "head", (-2, -5, 0)), k(4, "head", (0, 0, 0)),
        k(0, "tail", (0, 0, 0)), k(1, "tail", (0, 18, 0)), k(2, "tail", (6, 0, 0)), k(3, "tail", (0, -18, 0)), k(4, "tail", (0, 0, 0)),
        k(0, "tail2", (0, 0, 0)), k(1.3, "tail2", (0, 20, 0)), k(2.3, "tail2", (8, 0, 0)), k(3.3, "tail2", (0, -20, 0)), k(4, "tail2", (0, 0, 0)),
        k(0, "tail3", (0, 0, 0)), k(1.6, "tail3", (0, 25, 0)), k(3.6, "tail3", (0, -25, 0)), k(4, "tail3", (0, 0, 0)),
        k(0, "wing_r", (0, 0, 0)), k(2, "wing_r", (-2, 0, -2)), k(4, "wing_r", (0, 0, 0)),
        k(0, "wing_l", (0, 0, 0)), k(2, "wing_l", (-2, 0, 2)), k(4, "wing_l", (0, 0, 0)),
    ])
    # the walk (1.6 s): a lion's gait, the legs in diagonal pairs
    walk = []
    for t, a in ((0, 1), (0.8, -1), (1.6, 1)):
        walk += [k(t, "leg_fr", (22 * a, 0, 0)), k(t, "leg_bl", (22 * a, 0, 0)), k(t, "leg_fl", (-22 * a, 0, 0)), k(t, "leg_br", (-22 * a, 0, 0))]
    for t, a in ((0, 0), (0.4, 1), (0.8, 0), (1.2, 0), (1.6, 0)):
        walk += [k(t, "fore_fr", (30 * a, 0, 0)), k(t, "shin_bl", (-30 * a, 0, 0))]
    for t, a in ((0, 0), (0.8, 0), (1.2, 1), (1.6, 0)):
        walk += [k(t, "fore_fl", (30 * a, 0, 0)), k(t, "shin_br", (-30 * a, 0, 0))]
    walk += [k(0, "body", None, (0, 0, 0)), k(0.4, "body", None, (0, -0.6, 0)), k(0.8, "body", None, (0, 0, 0)),
             k(1.2, "body", None, (0, -0.6, 0)), k(1.6, "body", None, (0, 0, 0)),
             k(0, "head", (2, 0, 0)), k(0.4, "head", (-2, 0, 0)), k(0.8, "head", (2, 0, 0)), k(1.2, "head", (-2, 0, 0)), k(1.6, "head", (2, 0, 0)),
             k(0, "tail", (0, 14, 0)), k(0.8, "tail", (0, -14, 0)), k(1.6, "tail", (0, 14, 0))]
    anim("walk", 100, 1.6, True, walk)

    # the desert's call (2.0 s): rears up, wings wide, roars - and slams down at 1.0 s (tick 20): the tornadoes rise
    anim("summon", 200, 2.0, False, rest(0) + rear(0.6, 1.0) + spread(0.6, up=30) + [k(0.6, "jaw", (24, 0, 0))]
         + rear(0.85, 1.05) + spread(0.85, up=36) + [k(0.85, "jaw", (26, 0, 0))]
         + stand(1.0) + [k(1.0, "body", (8, 0, 0), (0, 1.5, 0), linear=True)] + spread(1.0, up=-10, beat=30, lin=True)
         + [k(1.0, "jaw", (20, 0, 0)), k(1.0, "neck", (-10, 0, 0)), k(1.0, "head", (-10, 0, 0))]
         + [k(1.4, "jaw", (0, 0, 0))] + half(1.5) + rest(2.0))
    # the lion's leap (2.6 s): crouches, tail lashing, a long moment; springs at 1.4 s, stretched out in the air,
    # lands at 2.0 s (tick 40)
    anim("pounce", 300, 2.6, False, rest(0) + crouch(0.5) + half(0.5) + crouch(1.3) + half(1.3) + [
        k(0.5, "tail", (20, 25, 0)), k(0.8, "tail", (20, -25, 0)), k(1.1, "tail", (20, 25, 0)), k(1.3, "tail", (20, 0, 0)),
        k(0.6, "jaw", (14, 0, 0)), k(1.2, "jaw", (4, 0, 0)),
        k(1.55, "body", (-14, 0, 0), (0, -3, 0)), k(1.55, "leg_fr", (-70, 0, 0)), k(1.55, "fore_fr", (20, 0, 0)), k(1.55, "leg_fl", (-70, 0, 0)),
        k(1.55, "fore_fl", (20, 0, 0)), k(1.55, "leg_br", (60, 0, 0)), k(1.55, "shin_br", (20, 0, 0)), k(1.55, "leg_bl", (60, 0, 0)),
        k(1.55, "shin_bl", (20, 0, 0)), k(1.55, "jaw", (24, 0, 0)), k(1.55, "tail", (30, 0, 0)),
        k(1.85, "body", (6, 0, 0), (0, -1, 0)), k(1.85, "leg_fr", (-55, 0, 0)), k(1.85, "leg_fl", (-55, 0, 0)),
    ] + spread(1.55, up=20) + spread(1.85, up=-10, beat=20) + crouch(2.0, 4) + [k(2.0, "jaw", (10, 0, 0)), k(2.0, "tail", (0, 0, 0))]
         + half(2.1) + rest(2.6))
    # the eye of Ra (3.2 s): he lifts his head, his eyes kindle (1.0 s), the gaze sweeps from his right to his left
    # (1.0-2.8 s), he lowers his head
    anim("gaze", 400, 3.2, False, rest(0) + [
        k(0.6, "neck", (24, 0, 0)), k(0.6, "head", (-14, 0, 0)), k(0.6, "body", (-6, 0, 0), (0, -1, 0)),
        k(1.0, "neck", (10, 0, 0)), k(1.0, "head", (4, 40, 0)), k(1.0, "jaw", (8, 0, 0)),
        k(1.9, "head", (4, 0, 0), linear=True), k(2.8, "head", (4, -40, 0), linear=True), k(2.8, "jaw", (8, 0, 0)),
    ] + half(0.6) + half(2.8) + rest(3.2))
    # the haboob (6.0 s): wings spread, he beats them again and again, the sand rising round him
    beat = rest(0) + rear(0.6, 0.35)
    for i in range(10):
        t = 0.6 + i * 0.5
        beat += spread(t, up=34) + spread(t + 0.25, up=-6, beat=36, lin=True)
    beat += rear(5.4, 0.35) + spread(5.4, up=20) + [k(0.8, "jaw", (22, 0, 0)), k(1.6, "jaw", (6, 0, 0))] + rest(6.0)
    anim("beat", 500, 6.0, False, beat)
    # the desert gale (2.4 s): three great beats of the wings, at 0.7, 1.3 and 1.9 s (ticks 14, 26, 38)
    gale = rest(0) + crouch(0.4, 1.5) + spread(0.4, up=40)
    for t in (0.7, 1.3, 1.9):
        gale += spread(t, up=-14, beat=44, lin=True) + spread(t + 0.3, up=40) + [k(t, "jaw", (20, 0, 0)), k(t + 0.25, "jaw", (4, 0, 0))]
    gale += rest(2.4)
    anim("gale", 700, 2.4, False, gale)
    # the sand vortex (5.0 s): sat up, wings wide, his head thrown back, a long roar
    anim("roar", 800, 5.0, False, rest(0) + sit(0.6) + spread(0.6, up=30) + [
        k(0.9, "neck", (30, 0, 0)), k(0.9, "head", (-30, 0, 0)), k(0.9, "jaw", (30, 0, 0)),
        k(4.2, "neck", (28, 0, 0)), k(4.2, "head", (-28, 0, 0)), k(4.2, "jaw", (28, 0, 0)),
    ] + sit(4.2) + spread(4.2, up=26) + rest(5.0))
    # the wrath of Ra (5.0 s): up into the sky on beating wings (0-1 s), hanging there, then diving at 4.4 s,
    # landing at 4.6 s (tick 92)
    judge = rest(0) + crouch(0.3, 3) + half(0.3)
    for i in range(8):
        t = 0.6 + i * 0.45
        judge += spread(t, up=40) + spread(t + 0.22, up=-10, beat=40, lin=True)
    judge += [k(0.8, p, r) for p, r in (("leg_fr", (-30, 0, 0)), ("fore_fr", (60, 0, 0)), ("leg_fl", (-30, 0, 0)), ("fore_fl", (60, 0, 0)),
                                        ("leg_br", (40, 0, 0)), ("leg_bl", (40, 0, 0)), ("tail", (40, 0, 0)))]
    judge += [k(4.2, "body", (-10, 0, 0)), k(4.4, "body", (30, 0, 0), linear=True), k(4.4, "neck", (-10, 0, 0))] + spread(4.4, up=60)
    judge += crouch(4.6, 4) + spread(4.6, up=-10, beat=30, lin=True) + [k(4.6, "jaw", (24, 0, 0)), k(4.9, "jaw", (0, 0, 0))] + rest(5.0)
    anim("judgement", 900, 5.0, False, judge)
    # the riddle (3.0 s, looped while it lasts): sat up as the statues sit, wings half open; speaks at the start
    anim("riddle", 1100, 3.0, True, sit(0) + half(0) + speak(0.2, 1.6) + sit(1.5) + [k(1.5, "head", (2, 0, 0))] + sit(3.0) + half(3.0))
    # the change (4.0 s): sinks to the ground, wings wrapped round him; at 2.0 s throws himself up roaring, wings wide
    anim("transform", 1200, 4.0, False, rest(0) + [
        k(0.8, "body", (6, 0, 0), (0, 7, 0)), k(0.8, "neck", (-15, 0, 0)), k(0.8, "head", (25, 0, 0)),
        k(0.8, "leg_fr", (-80, 0, 0)), k(0.8, "fore_fr", (80, 0, 0)), k(0.8, "leg_fl", (-80, 0, 0)), k(0.8, "fore_fl", (80, 0, 0)),
        k(0.8, "leg_br", (-70, 0, 0)), k(0.8, "shin_br", (100, 0, 0)), k(0.8, "leg_bl", (-70, 0, 0)), k(0.8, "shin_bl", (100, 0, 0)),
        k(1.9, "body", (6, 0, 0), (0, 7, 0)), k(1.9, "head", (25, 0, 0)), k(1.9, "neck", (-15, 0, 0)),
        k(1.9, "leg_fr", (-80, 0, 0)), k(1.9, "fore_fr", (80, 0, 0)), k(1.9, "leg_fl", (-80, 0, 0)), k(1.9, "fore_fl", (80, 0, 0)),
        k(1.9, "leg_br", (-70, 0, 0)), k(1.9, "shin_br", (100, 0, 0)), k(1.9, "leg_bl", (-70, 0, 0)), k(1.9, "shin_bl", (100, 0, 0)),
    ] + [k(t, f"wing_{s}", (-6, sx * 30, -sx * 50)) for t in (0.8, 1.9) for s, sx in (("r", -1), ("l", 1))]
         + rear(2.3, 1.1) + spread(2.3, up=50) + [k(2.3, "jaw", (30, 0, 0)), k(3.4, "jaw", (26, 0, 0))]
         + rear(3.4, 1.0) + spread(3.4, up=44) + rest(4.0))
    # his end (6.0 s): staggers, rears up screaming with wings wide (1.0-2.2 s), then turns, faster and faster
    # (client.SphinxRenderer winds him into the sand tornado he becomes and lets it blow away)
    death = rest(0) + [k(0.4, "body", (0, 0, 12), (0, 1, 0)), k(0.4, "head", (15, 0, -10)), k(0.8, "body", (0, 0, -10), (0, 1, 0)),
                       k(0.8, "head", (10, 0, 12))] + rear(1.4, 1.1) + spread(1.4, up=50) + [k(1.4, "jaw", (32, 0, 0))]
    death += rear(2.4, 1.1) + spread(2.4, up=56) + [k(2.4, "jaw", (32, 0, 0))]
    spin = 0
    for i, t in enumerate((2.4, 3.0, 3.5, 3.9, 4.2, 4.5, 4.8, 5.1, 5.4, 5.7, 6.0)):
        death.append(k(t, "body", (-38, spin, 0), (0, -2 - i * 0.6, 2), linear=True))
        spin += 60 + i * 25
    death += spread(4.0, up=70) + [k(4.0, "jaw", (32, 0, 0)), k(4.0, "neck", (40, 0, 0))]
    anim("death", 1300, 6.0, False, death)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    model()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    for phase in (1, 2):
        tex, glow = Tex(TEX_W * S, TEX_H * S, seed=5), Tex(TEX_W * S, TEX_H * S, seed=5)
        paint(tex, glow, phase, random.Random(31))
        name = "sphinx" if phase == 1 else "sphinx_p2"
        tex.save(os.path.join(tex_dir, name + ".png"))
        glow.save(os.path.join(tex_dir, name + "_glow.png"))
    rel = []
    for prt, parent, pivot, rot in PARTS:
        base = ABS[parent] if parent else (0, 0, 0)
        rel.append((prt, parent, tuple(pivot[i] - base[i] for i in range(3)), rot))
    scene, objs = build_scene(rel, CUBES, os.path.join(tex_dir, "sphinx.png"), TEX_W, TEX_H, "sphinx")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved sphinx parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
