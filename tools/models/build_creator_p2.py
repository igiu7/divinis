"""
Builds the Nameless Creator's second phase, Two Hands (the truth about its servants), in Blender: phase 1's collage
torn down the middle, the halves a little apart and stitched together by a seam of chain links. The left half has
become Aurelion - gold and white, the banners white and gold, the window's glass gold and white light, the antler
gilded in white blossom, a golden eye, a wing of light (an arm of gold and seven glowing feathers). The right half has
become Nocthariel - black and violet, the banners ragged, the glass violet and black, a horn where the antler was, a
mouth, a ragged wing of smoke. Nothing else keeps its colour: the map halves of its face are grey, drifting apart, and
in the gap between them, now and then, the missing texture shows (glimpse).

  blender -b --python tools/models/build_creator_p2.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/CreatorP2ModelData.java (creator_p2.png, creator_p2_glow.png). Drawn
three times its pixels, as phase 1. Its rest pose is where phase 1's crack ends.

Animations:
  idle   4.0 loop  as phase 1's, the halves breathing apart and together, the map halves drifting apart (the gap
                   opening on the glimpse) and back, the wing of light beating, the smoke wing rippling
  burn   5.0       (into phase 3) it arches back, screaming (0-0.8); then it burns away like paper, from the bottom up:
                   the banners curl and crumble (0.8-1.8), the clock and compass fall, the wings (1.6-2.6), the arms and
                   the window (2.2-3.0), the heads (3.0-3.6), the halves and the seam (3.6-4.4) - until only the
                   glimpse of the missing texture hangs in the air (4.4-5.0)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from creator_common import BANNER_ANGLES, SIDES, Model, build, k, majesty  # noqa: E402
from creator_paint import GOLD5, paint_cubes  # noqa: E402

VIOLET5 = [(10, 6, 14), (34, 18, 50), (74, 34, 116), (124, 62, 186), (206, 156, 255)]
STYLE_L = dict(
    edge=(246, 240, 226), robe=(236, 232, 222), lattice=(220, 176, 70), plate=GOLD5, sun=(255, 250, 230), sun_glow=(255, 230, 160),
    belt=(200, 160, 70), frame=GOLD5, pauldron=(242, 238, 228), sleeve=(236, 232, 220), cuff=(232, 192, 92),
    chain=[(206, 152, 44), (124, 62, 186)], skin=(250, 242, 226), skin_d=(210, 186, 140), hood=(240, 236, 226),
    wood=(220, 176, 70), wood_d=(150, 104, 30), blossom=[(255, 255, 250), (250, 240, 210), (255, 230, 160)], blossom_glow=True,
    glass=[(255, 240, 200), (240, 200, 90), (255, 252, 240), (222, 162, 52)], glass_glow=1.0, lead=(140, 96, 22),
    dial=(250, 244, 220), compass_frame=VIOLET5, compass_dial=(60, 50, 80),
    banner=[(244, 242, 236), (220, 176, 70), (236, 228, 206), (250, 250, 246), (206, 152, 44)],
    banner_fg=[(206, 152, 44), (250, 248, 240), (206, 152, 44), (220, 176, 70), (250, 248, 240)],
    banner_sun=(255, 226, 120), map_tint=(230, 190, 90), flower_tint=(250, 240, 200),
)
STYLE_R = dict(
    edge=(44, 32, 56), robe=(24, 16, 32), lattice=(120, 50, 190), plate=VIOLET5, sun=(190, 110, 255), sun_glow=(150, 60, 230),
    belt=(30, 22, 36), frame=VIOLET5, pauldron=(28, 20, 36), sleeve=(26, 18, 34), cuff=(110, 50, 170),
    chain=[(124, 62, 186), (206, 152, 44)], skin=(64, 54, 74), skin_d=(30, 24, 36), hood=(20, 14, 26),
    wood=(40, 30, 46), wood_d=(20, 14, 24), blossom=[(150, 70, 220)],
    glass=[(90, 30, 150), (20, 10, 30), (140, 60, 220), (50, 20, 80)], glass_glow=0.9, lead=(14, 10, 18),
    dial=(60, 50, 80), compass_frame=VIOLET5, compass_dial=(52, 40, 70),
    banner=[(26, 22, 30), (100, 40, 160), (18, 14, 22), (60, 24, 96), (26, 22, 30)],
    banner_fg=[(124, 62, 186), (26, 22, 30), (124, 62, 186), (190, 120, 255), (124, 62, 186)],
    banner_sun=(190, 120, 255), ragged=True, map_tint=(140, 80, 220), flower_tint=(120, 60, 190),
)

GAP = 1.5
M = Model("creator_p2")
majesty(M, gap=GAP, antlers="l")
BANNERS = [(s, t, i) for s, t in SIDES for i in range(len(BANNER_ANGLES))]

# the seam of chain down the middle, and what shows through the gap in its face
M.part("seam", "body", (0, 0, 0))
for n, y in enumerate(range(-32, 18, 2)):
    M.box("seam", (-0.5, y, -0.5), (1, 2, 1), "chain", idx=n)
M.part("glimpse", "body", (0, -21.5, -4.5))
M.box("glimpse", (-1, -3, -0.5), (2, 6, 1), "missing")

# the left: Aurelion's - a wing of light, a golden eye
M.part("wing_l", "half_l", (6, -12, 3), (0, -20, -32))
M.box("wing_l", (0, -1, -1), (22, 2, 2), "lightbone")
for j in range(7):
    f = M.part(f"wing_l_f{j}", "wing_l", (3 + j * 3, 1, 0.4 + (j % 2) * 0.6), (8, 0, -(4 + j * 6)))
    M.box(f, (-2, 0, -0.5), (4, 13 + round(j * 1.5), 1), "lightfeather")
M.box("half_l", (1.5, -8.5, -4), (3, 3, 1), "eye")
# the right: Nocthariel's - a horn, a ragged wing of smoke, a mouth
h0 = M.part("horn_r0", "head_r", (3.5, -12, 0), (-15, 0, 28), s=-1)
M.box(h0, (-1, -5, -1), (2, 5, 2), "horn", -1)
h1 = M.part("horn_r1", h0, (0, -5, 0), (-25, 0, 18), s=-1)
M.box(h1, (-1, -4, -1), (2, 4, 2), "horn", -1)
h2 = M.part("horn_r2", h1, (0, -4, 0), (-30, 0, 14), s=-1)
M.box(h2, (-0.5, -4, -0.5), (1, 4, 1), "horn", -1)
M.part("wing_r", "half_r", (6, -12, 3), (0, -20, -32), s=-1)
M.box("wing_r", (0, -1, -1), (20, 2, 2), "horn", -1)
for j in range(6):
    f = M.part(f"wing_r_s{j}", "wing_r", (3 + j * 3.2, 1, 0.4 + (j % 2) * 0.6), (8, 0, -(4 + j * 7)), s=-1)
    M.box(f, (-2, 0, -0.5), (4, 12 + j * 2, 1), "smoke", -1)
M.box("half_r", (1, -8.5, -4), (4, 3, 1), "mouth", -1)


def wave(t, period, phase=0.0):
    return math.sin((t / period + phase) * math.tau)


def idle():
    keys = []
    rnd = random.Random(5)
    for t in (0, 1, 2, 3, 4):
        keys.append(k(t, "body", (0, 0, 0), (0, -0.6 * wave(t, 4), 0)))
        keys.append(k(t, "wing_l", (0, 0, 8 * wave(t, 4))))
        keys.append(k(t, "wing_r", (3 * wave(t, 4, 0.2), 0, 6 * wave(t, 4, 0.4)), s=-1))
        for s, side in SIDES:
            keys.append(k(t, f"half_{side}", (0, 0, 0.8 * wave(t, 4, 0.25)), (0.4 * (1 + wave(t, 4, 0.75)), 0, 0), s=s))
            keys.append(k(t, f"halo_{side}", (0, 0, 2.5 * wave(t, 4, 0.1 if s > 0 else 0.6)), s=s))
            keys.append(k(t, f"head_{side}", (2 * wave(t, 4, 0.3), 4 * wave(t, 4, 0.6), 0)))
            keys.append(k(t, f"chain_{side}0", (2 * wave(t, 4, 0.2 if s > 0 else 0.7), 0, 5 * wave(t, 4, 0.25 if s > 0 else 0.75)), s=s))
            keys.append(k(t, f"arm_{side}", (3 * wave(t, 4, 0.15), 0, 2 * wave(t, 4, 0.4)), s=s))
            keys.append(k(t, f"forearm_{side}", (-4 * wave(t, 4, 0.35), 0, 0), s=s))
    for s, side in SIDES:  # the map halves drifting apart on the gap, and back
        for t, x in ((0, 0), (1.3, 1.6), (2.4, 1.6), (4, 0)):
            keys.append(k(t, f"face_{side}", (0, -6 * x / 1.6, 0), (x, 0, 0), s=s))
    for t, sc in ((0, 0.8), (1.3, 1.0), (1.35, 0.6), (1.4, 1.0), (2.4, 1.0), (2.45, 1.3), (2.5, 1.0), (4, 0.8)):
        keys.append(k(t, "glimpse", scl=(sc, sc, 1), linear=True))
    for s, side, i in BANNERS:
        ph = rnd.random()
        for t in (0, 1, 2, 3, 4):
            keys.append(k(t, f"banner_{side}{i}", (3 * wave(t, 4, ph), 0, 2 * wave(t, 4, ph + 0.3)), s=s))
    for j in range(7):
        for t in (0, 2, 4):
            keys.append(k(t, f"wing_l_f{j}", (3 * wave(t, 4, j * 0.1), 0, 0)))
    for j in range(6):
        ph = rnd.random()
        for t in (0, 1, 2, 3, 4):
            keys.append(k(t, f"wing_r_s{j}", (10 * wave(t, 2, ph), 0, 4 * wave(t, 4, ph)), s=-1))
    M.anim("idle", 4.0, True, keys)


def burn_away(part, t0, length=0.5, curl=(-50, 0, 0), s=1, base=(0, 0, 0)):
    """A part catches, curls and crumbles to nothing from t0 (base: its turn then, already mirrored)."""
    mid = tuple(base[i] + (curl[i] * (s if i else 1) - base[i]) * 0.5 for i in range(3))
    return [k(0, part, scl=(1, 1, 1)), k(t0, part, base, scl=(1, 1, 1)), k(t0 + length * 0.4, part, mid, scl=(0.9, 0.8, 0.9)),
            k(t0 + length, part, curl, scl=(0, 0, 0), linear=True, s=s), k(5.0, part, curl, scl=(0, 0, 0), s=s)]


def burn():
    keys = []
    # it arches back and screams
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(0.6, "body", (-10, 0, 0), (0, -1, 0)), k(3.6, "body", (-6, 0, 0), (0, 2, 0)),
             k(5.0, "body", (-6, 0, 0), (0, 2, 0))]
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.6, f"arm_{side}", (-30, 0, -45), s=s), k(1.6, f"arm_{side}", (-26, 0, -38), s=s)]
        keys += [k(0, f"head_{side}", (0, 0, 0), s=s), k(0.6, f"head_{side}", (-30, 0, 8), s=s), k(2.6, f"head_{side}", (-24, 0, 6), s=s)]
        keys += [k(0, f"face_{side}", (0, 0, 0), (0, 0, 0)), k(0.8, f"face_{side}", (0, -20, 0), (2, 0, 0), s=s)]
    rnd = random.Random(9)
    # the banners burn from the hem up, the front first
    for s, side, i in BANNERS:
        keys += burn_away(f"banner_{side}{i}", 0.8 + i * 0.2 + rnd.random() * 0.1, 0.6, (-60, 0, rnd.uniform(-20, 20)), s)
    for s, side in SIDES:
        keys += burn_away(f"chain_{side}0", 1.4 + (0.1 if s < 0 else 0), 0.5, (0, 0, -40), s)
        keys += burn_away(f"forearm_{side}", 2.2, 0.5, (-40, 0, 0), s)
        keys += burn_away(f"arm_{side}", 2.6, 0.4, (-26, 0, -60), s, (-26, 0, -38 * s))
        keys += burn_away(f"halo_{side}", 2.4 + (0.15 if s < 0 else 0), 0.6, (0, 30, 20), s)
        keys += burn_away(f"shoulder_{side}", 2.8, 0.4, (0, 0, 20), s)
        keys += burn_away(f"head_{side}", 3.1 + (0.1 if s > 0 else 0), 0.5, (40, 0, 30), s, (-24, 0, 6 * s))
        keys += burn_away(f"half_{side}", 3.6 + (0.2 if s < 0 else 0), 0.6, (-20, 0, 15), s)
    for j in range(7):
        keys += burn_away(f"wing_l_f{6 - j}", 1.6 + j * 0.12, 0.4, (-40, 0, 0))
    for j in range(6):
        keys += burn_away(f"wing_r_s{5 - j}", 1.7 + j * 0.13, 0.4, (-40, 0, 0), -1)
    keys += burn_away("wing_l", 2.5, 0.4, (0, 0, 30))
    keys += burn_away("wing_r", 2.6, 0.4, (0, 0, 30), -1)
    keys += burn_away("horn_r0", 2.9, 0.5, (-30, 0, 0), -1)
    keys += [k(0, "seam", scl=(1, 1, 1)), k(3.9, "seam", scl=(1, 1, 1)), k(4.4, "seam", scl=(1, 0, 1)), k(5.0, "seam", scl=(1, 0, 1))]
    # the glimpse alone at the end, flickering
    for t, sc in ((0, 1), (4.4, 1), (4.5, 1.4), (4.55, 0.7), (4.7, 1.2), (4.75, 1.0), (5.0, 1.0)):
        keys.append(k(t, "glimpse", scl=(sc, sc, 1), linear=True))
    M.anim("burn", 5.0, False, keys)


# ---------------------------------------------------------------- the attacks (timed to boss/NamelessCreator.java)
def both_arms(keys, t, arm, fore=(0, 0, 0), hand=None):
    for s, side in SIDES:
        keys.append(k(t, f"arm_{side}", arm, s=s))
        keys.append(k(t, f"forearm_{side}", fore, s=s))
        if hand is not None:
            keys.append(k(t, f"hand_{side}", hand, s=s))


def crush():
    """3.0 s: both hands reached out to the players (0-0.6), closed (0.9), squeezing, shaking (1.2-1.6), let go (2.2-3.0)."""
    keys = []
    both_arms(keys, 0, (0, 0, 0))
    both_arms(keys, 0.6, (-80, 0, 8), (-10, 0, 0), (0, 0, 0))
    both_arms(keys, 0.9, (-84, 0, 2), (-30, 0, 0), (40, 0, 0))
    for n, t in enumerate((1.2, 1.3, 1.4, 1.5, 1.6)):
        both_arms(keys, t, (-86 + (3 if n % 2 else -3), 0, 2), (-34, 0, 0), (55, 0, 0))
    both_arms(keys, 2.2, (-60, 0, -10), (-10, 0, 0), (0, 0, 0))
    both_arms(keys, 3.0, (0, 0, 0), (0, 0, 0), (0, 0, 0))
    for s, side in SIDES:
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.9, f"head_{side}", (10, 0, 0)), k(1.6, f"head_{side}", (14, 0, 0)), k(3.0, f"head_{side}", (0, 0, 0))]
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(0.9, "body", (8, 0, 0), (0, 0, -2)), k(1.6, "body", (10, 0, 0), (0, 0, -2)),
             k(3.0, "body", (0, 0, 0), (0, 0, 0))]
    M.anim("crush", 3.0, False, keys)


def judgement():
    """2.25 s: both hands raised (0-0.4) and held while time stands still (to 1.5); brought down - every cut at once (1.5)."""
    keys = []
    both_arms(keys, 0, (0, 0, 0))
    both_arms(keys, 0.4, (-165, 0, -30), (-15, 0, 0))
    both_arms(keys, 1.45, (-168, 0, -28), (-18, 0, 0))
    both_arms(keys, 1.55, (-20, 0, -60), (0, 0, 0))
    both_arms(keys, 2.25, (0, 0, 0), (0, 0, 0))
    for s, side in SIDES:
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.4, f"head_{side}", (-20, 0, 0)), k(1.45, f"head_{side}", (-22, 0, 0)),
                 k(1.55, f"head_{side}", (12, 0, 0)), k(2.25, f"head_{side}", (0, 0, 0))]
        keys += [k(0, f"half_{side}", (0, 0, 0), (0, 0, 0), s=s), k(1.45, f"half_{side}", (0, 0, 0), (0, 0, 0), s=s),
                 k(1.55, f"half_{side}", (0, 0, -3), (0.8, 0, 0), s=s), k(2.25, f"half_{side}", (0, 0, 0), (0, 0, 0), s=s)]
    keys += [k(0, "wing_l", (0, 0, 0)), k(0.4, "wing_l", (0, 0, -25)), k(1.55, "wing_l", (0, 0, 20)), k(2.25, "wing_l", (0, 0, 0))]
    keys += [k(0, "wing_r", (0, 0, 0), s=-1), k(0.4, "wing_r", (0, 0, -25), s=-1), k(1.55, "wing_r", (0, 0, 20), s=-1),
             k(2.25, "wing_r", (0, 0, 0), s=-1)]
    M.anim("judgement", 2.25, False, keys)


def punch(side, s):
    """1.3 s: one arm (gold left, violet right) drawn back while its fist gathers (0-0.9), thrust down - the blow (1.0)."""
    keys = [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.9, f"arm_{side}", (40, 0, -30), s=s), k(1.0, f"arm_{side}", (-90, 0, 10), linear=True, s=s),
            k(1.3, f"arm_{side}", (0, 0, 0), s=s)]
    keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(0.9, f"forearm_{side}", (-70, 0, 0), s=s),
             k(1.0, f"forearm_{side}", (0, 0, 0), linear=True, s=s), k(1.3, f"forearm_{side}", (0, 0, 0), s=s)]
    keys += [k(0, f"hand_{side}", (0, 0, 0), s=s), k(0.5, f"hand_{side}", (50, 0, 0), s=s), k(1.3, f"hand_{side}", (0, 0, 0), s=s)]
    keys += [k(0, "body", (0, 0, 0)), k(0.9, "body", (0, 14 * s, 0)), k(1.0, "body", (6, -10 * s, 0), linear=True), k(1.3, "body", (0, 0, 0))]
    M.anim(f"punch_{side}", 1.3, False, keys)


def sparks():
    """4.0 s: the gold left arm raised high, the wing of light spread and beating fast; the right half turned away."""
    keys = [k(0, "arm_l", (0, 0, 0)), k(0.4, "arm_l", (-160, 0, -20))]
    for n, t in enumerate((0.8, 1.4, 2.0, 2.6, 3.2)):
        keys.append(k(t, "arm_l", (-160 + (6 if n % 2 else -6), 0, -20 + (8 if n % 2 else 0))))
    keys += [k(3.6, "arm_l", (-150, 0, -20)), k(4.0, "arm_l", (0, 0, 0))]
    keys += [k(0, "wing_l", (0, 0, 0))]
    t = 0.3
    n = 0
    while t < 3.8:
        keys.append(k(round(t, 2), "wing_l", (0, -10, -30 if n % 2 else 15)))
        t += 0.2
        n += 1
    keys += [k(4.0, "wing_l", (0, 0, 0))]
    keys += [k(0, "body", (0, 0, 0)), k(0.4, "body", (-6, -15, 4)), k(3.6, "body", (-6, -15, 4)), k(4.0, "body", (0, 0, 0))]
    for side in ("l", "r"):
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.4, f"head_{side}", (-25, 10, 0)), k(3.6, f"head_{side}", (-25, 10, 0)),
                 k(4.0, f"head_{side}", (0, 0, 0))]
    M.anim("sparks", 4.0, False, keys)


def supernova():
    """3.0 s: the violet right arm circling the black stars round the arena (0-2.0), its fist closed to draw them in
    (2.0), braced as they meet (3.0)."""
    keys = [k(0, "arm_r", (0, 0, 0), s=-1)]
    for n in range(9):
        t = 0.25 * n
        a = n * 45
        keys.append(k(t, "arm_r", (-90 + 30 * math.sin(math.radians(a)), 0, -60 + 30 * math.cos(math.radians(a))), s=-1))
    keys += [k(2.0, "arm_r", (-80, 0, 0), s=-1), k(2.15, "arm_r", (-40, 0, 20), linear=True, s=-1), k(3.0, "arm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "hand_r", (0, 0, 0), s=-1), k(2.0, "hand_r", (0, 0, 0), s=-1), k(2.15, "hand_r", (60, 0, 0), s=-1), k(3.0, "hand_r", (0, 0, 0), s=-1)]
    keys += [k(0, "wing_r", (0, 0, 0), s=-1), k(0.5, "wing_r", (0, -20, -35), s=-1), k(2.0, "wing_r", (0, -20, -35), s=-1),
             k(2.15, "wing_r", (0, 10, 20), s=-1), k(3.0, "wing_r", (0, 0, 0), s=-1)]
    keys += [k(0, "body", (0, 0, 0)), k(0.5, "body", (0, 18, -3)), k(2.0, "body", (0, 18, -3)), k(2.15, "body", (6, -6, 0)), k(2.9, "body", (-8, 0, 0)),
             k(3.0, "body", (0, 0, 0))]
    M.anim("supernova", 3.0, False, keys)


def sword():
    """5.75 s: both hands high together, holding the hilt while the sword is drawn (0-2.0); lowered to its side (2.0-2.3);
    the whole body turned round with the swing (2.3-3.3); raised over its head (4.0-4.9) and brought down (4.9-5.15)."""
    keys = []
    both_arms(keys, 0, (0, 0, 0))
    both_arms(keys, 0.5, (-175, 0, 10), (-10, 0, 0))
    both_arms(keys, 2.0, (-178, 0, 12), (-10, 0, 0))
    both_arms(keys, 2.3, (-60, 0, -40), (-20, 0, 0))
    both_arms(keys, 3.3, (-50, 0, -50), (-10, 0, 0))
    both_arms(keys, 4.0, (-20, 0, -10), (0, 0, 0))
    both_arms(keys, 4.9, (-175, 0, 8), (-15, 0, 0))
    both_arms(keys, 5.15, (-35, 0, 10), (0, 0, 0))
    both_arms(keys, 5.75, (0, 0, 0), (0, 0, 0))
    keys += [k(0, "body", (0, 0, 0)), k(2.3, "body", (0, 0, 0)), k(2.8, "body", (0, -135, 0), linear=True), k(3.3, "body", (0, -270, 0), linear=True),
             k(3.31, "body", (0, 90, 0), linear=True), k(4.0, "body", (0, 0, 0)), k(4.9, "body", (-8, 0, 0)), k(5.15, "body", (10, 0, 0), linear=True),
             k(5.75, "body", (0, 0, 0))]
    for side in ("l", "r"):
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.5, f"head_{side}", (-22, 0, 0)), k(2.0, f"head_{side}", (-22, 0, 0)),
                 k(2.3, f"head_{side}", (0, 0, 0)), k(4.9, f"head_{side}", (-10, 0, 0)), k(5.15, f"head_{side}", (12, 0, 0)), k(5.75, f"head_{side}", (0, 0, 0))]
    M.anim("sword", 5.75, False, keys)


def echo():
    """8.0 s: arms spread out to either side, the wing of light open to the left and the smoke wing to the right,
    swaying slowly as the zones change places (every 2 s)."""
    keys = []
    for t in (0, 0.6, 2.0, 4.0, 6.0, 7.4, 8.0):
        out = 0 if t in (0, 8.0) else 1
        sway = 0 if t in (0, 0.6, 7.4, 8.0) else (6 if int(t) % 4 == 2 else -6)
        both_arms(keys, t, (-10 * out, 0, (-80 + sway) * out), (0, 0, 0))
        keys.append(k(t, "wing_l", (0, -15 * out, -30 * out)))
        keys.append(k(t, "wing_r", (0, -15 * out, -30 * out), s=-1))
        keys.append(k(t, "body", (0, sway * 0.5, 0), (0, -1.5 * out, 0)))
    M.anim("echo", 8.0, False, keys)


def paint(M, texs):
    paint_cubes(M, texs["creator_p2"], texs["creator_p2_glow"], {1: STYLE_L, -1: STYLE_R}, random.Random(23))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    idle()
    crush()
    judgement()
    punch("l", 1)
    punch("r", -1)
    sparks()
    supernova()
    sword()
    echo()
    burn()  # last: it ends with every part gone (scale 0), which Blender would hold over the later ones
    build(M, 256, paint, ["creator_p2", "creator_p2_glow"], argv[0], argv[1], preview_z=26.0)


main()
