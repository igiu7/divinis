"""
Builds the Nameless Creator's first phase, Majesty (the god as mortals imagine it), in Blender: a vast, symmetrical,
"cathedral" figure cut out of paper and card and pinned together with brass brads and chains like a puppet - a robe of
five banners on each side hanging from its waist, a rose window behind it for a halo, a crown of flowers and antlers
of cherry branches in blossom, a great clock (left) and a compass (right) hanging on chains from the window's edge,
paper hands raised in blessing. Its face is a map of its arena (the renderer draws the live map over it). It is made of
two halves (creator_common.majesty), whole until the collage cracks down the middle into phase 2.

  blender -b --python tools/models/build_creator_p1.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/CreatorP1ModelData.java. 256 wide texture (creator_p1.png) and its glow
(creator_p1_glow.png: the light through the stained glass). Drawn three times its pixels (some nine blocks tall).

Parts the renderer drives: head_l / head_r (turn both alike to look), face_l / face_r (the map), clock_hour,
clock_minute (the time of day), compass_needle (north), string_* (the puppet strings - shown only while it comes).

Animations:
  spawn   8.0      it is let down out of the sky on its strings, folded flat and limp like a stage prop (0-2.6),
                   bobs as the strings catch it; then it opens like a pop-up book: the banners unroll one after
                   another (3.0-4.4), the rose window flips round from edge-on (3.8-4.5), the antlers spring up and
                   blossom (4.2-4.9), the clock and the compass drop and swing (4.6-6.4); the strings jerk taut (5.0):
                   the head snaps up, the arms are thrown up in blessing; the map that is its face unfolds (5.6-6.3)
                   and it settles (8.0)
  idle    4.0 loop the banners stir, the window rocks, the clock and compass swing on their chains, it floats
  crack   5.0      (into phase 2) it shudders, the map tearing (0-1.0); the collage splits down the middle (1.0-1.2),
                   the halves reel apart, arms flung out, heads thrown back (to 3.5); they settle a little way apart -
                   phase 2's rest pose (5.0)
  the attacks (boss/NamelessCreator.java is timed to them):
  starburst 2.5    arms up before it, the rose window spinning faster and faster; a thrust at each volley (0.75, 1.25, 1.75)
  slash     0.8    the right arm raised across it and swept down and out - the cut (0.6)
  pentagram 2.2    both arms high, drawing the star in the air; brought down and out - the five cuts (1.2)
  ring      3.2    arms spread wide, brought slowly together as the ring closes, clapped (2.6)
  bloom     1.6    the head bowed and shaken, the antlers shedding their flowers
  collapse  6.0    arms raised high holding the star, trembling, the window spinning (0.6-3.5); pulled down as it
                   collapses (3.5-3.9); braced, an arm over its face, when it goes off (4.5)
  chart     5.0    its right hand at its face, drawing on the map; a nod as each wave is marked (0, 1.5, 3.0)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from creator_common import BANNER_ANGLES, SIDES, Model, both, build, k, majesty  # noqa: E402
from creator_paint import GOLD5, paint_cubes  # noqa: E402

STYLE = dict(
    edge=(238, 230, 210), robe=(78, 36, 120), lattice=(214, 168, 60), plate=GOLD5, sun=(255, 246, 210), sun_glow=None,
    belt=(96, 56, 32), frame=GOLD5, pauldron=(34, 120, 72), sleeve=(36, 60, 150), cuff=(250, 248, 240),
    chain=[(92, 92, 104), (130, 130, 142)], skin=(240, 228, 212), skin_d=(196, 176, 160), hood=(150, 24, 40),
    wood=(108, 66, 64), wood_d=(70, 40, 44), blossom=[(244, 176, 204), (252, 206, 224), (222, 132, 172)],
    glass=[(190, 30, 40), (40, 70, 190), (40, 150, 70), (230, 160, 30), (140, 60, 170), (60, 150, 210), (220, 90, 120)],
    lead=(46, 40, 46), dial=(242, 234, 204), compass_frame=[(54, 54, 62), (92, 92, 102), (140, 140, 150), (186, 186, 196), (236, 236, 244)],
    compass_dial=(206, 204, 192),
    banner_sun=(255, 226, 120),
)
STYLE_L = dict(STYLE, banner=[(176, 46, 38), (254, 216, 61), (60, 68, 170), (94, 124, 22), (249, 128, 29)],
               banner_fg=[(254, 216, 61), (176, 46, 38), (249, 255, 254), (254, 216, 61), (60, 68, 170)])
STYLE_R = dict(STYLE, banner=[(22, 156, 156), (137, 50, 184), (249, 255, 254), (131, 84, 50), (58, 179, 218)],
               banner_fg=[(249, 255, 254), (254, 216, 61), (176, 46, 38), (254, 216, 61), (249, 255, 254)])

M = Model("creator_p1")
majesty(M)
BANNERS = [(s, t, i) for s, t in SIDES for i in range(len(BANNER_ANGLES))]


def wave(t, period, phase=0.0):
    return math.sin((t / period + phase) * math.tau)


# ---------------------------------------------------------------- idle
def idle():
    keys = []
    rnd = random.Random(3)
    for t in (0, 1, 2, 3, 4):
        keys.append(k(t, "body", (0, 0, 0), (0, -0.6 * wave(t, 4), 0)))
        for s, side in SIDES:  # the window rocks as one, the heads look about as one
            keys.append(k(t, f"halo_{side}", (0, 0, 2.5 * wave(t, 4, 0.1))))
            keys.append(k(t, f"head_{side}", (2 * wave(t, 4, 0.3), 4 * wave(t, 4, 0.6), 0)))
            keys.append(k(t, f"chain_{side}0", (2 * wave(t, 4, 0.2 if s > 0 else 0.7), 0, 5 * wave(t, 4, 0.25 if s > 0 else 0.75)), s=s))
            keys.append(k(t, f"chain_{side}2", (0, 0, 2 * wave(t, 4, 0.5 if s > 0 else 0.0)), s=s))
            keys.append(k(t, f"arm_{side}", (3 * wave(t, 4, 0.15), 0, 2 * wave(t, 4, 0.4)), s=s))
            keys.append(k(t, f"forearm_{side}", (-4 * wave(t, 4, 0.35), 0, 0), s=s))
    for s, side, i in BANNERS:
        ph = rnd.random()
        for t in (0, 1, 2, 3, 4):
            keys.append(k(t, f"banner_{side}{i}", (3 * wave(t, 4, ph), 0, 2 * wave(t, 4, ph + 0.3)), s=s))
    M.anim("idle", 4.0, True, keys)


# ---------------------------------------------------------------- spawn
def spawn():
    keys = []
    # let down out of the sky on its strings, swaying, then caught
    for t, y, z in ((0, -96, 0), (0.7, -70, 4), (1.4, -40, -4), (2.1, -12, 3), (2.6, 2, -1), (2.9, -1.5, 0), (3.2, 0, 0)):
        keys.append(k(t, "body", (0, 0, z), (0, y, 0)))
    keys += [k(5.0, "body", (0, 0, 0), (0, 0, 0)), k(5.15, "body", (0, 0, 0), (0, -3, 0), True), k(5.8, "body", (0, 0, 0), (0, 0, 0)),
             k(8.0, "body", (0, 0, 0), (0, 0, 0))]
    for s, side in SIDES:
        # limp: the head bowed, the arms hanging straight; the strings jerk taut at 5.0
        for t in (0, 5.0):
            keys.append(k(t, f"head_{side}", (48, 0, 6)))
        keys += [k(5.15, f"head_{side}", (-14, 0, 0), linear=True), k(5.7, f"head_{side}", (0, 0, 0)), k(8.0, f"head_{side}", (0, 0, 0))]
        for t in (0, 5.0):
            keys.append(k(t, f"arm_{side}", (0, 0, 12), s=s))
            keys.append(k(t, f"forearm_{side}", (35, 0, 0), s=s))
            keys.append(k(t, f"hand_{side}", (-35, 0, 0), s=s))
        keys += [k(5.2, f"arm_{side}", (-20, 0, -40), linear=True, s=s), k(5.7, f"arm_{side}", (-14, 0, -32), s=s),
                 k(7.2, f"arm_{side}", (-6, 0, -10), s=s), k(8.0, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(5.2, f"forearm_{side}", (-25, 0, 0), linear=True, s=s), k(6.0, f"forearm_{side}", (-12, 0, 0), s=s),
                 k(8.0, f"forearm_{side}", (0, 0, 0), s=s)]
        keys += [k(5.2, f"hand_{side}", (-10, 0, 0), s=s), k(8.0, f"hand_{side}", (0, 0, 0), s=s)]
        # the rose window edge-on, flipping round
        keys += [k(0, f"halo_{side}", (0, 90, 0)), k(3.8, f"halo_{side}", (0, 90, 0)), k(4.05, f"halo_{side}", (0, -16, 0), linear=True),
                 k(4.3, f"halo_{side}", (0, 6, 0)), k(4.5, f"halo_{side}", (0, 0, 0)), k(8.0, f"halo_{side}", (0, 0, 0))]
        # the antlers folded back, springing up; their blossoms coming out
        keys += [k(0, f"antler_{side}0", (110, 0, 0), scl=(0.4, 0.4, 0.4)), k(4.2, f"antler_{side}0", (110, 0, 0), scl=(0.4, 0.4, 0.4)),
                 k(4.45, f"antler_{side}0", (-12, 0, 0), True, (1.1, 1.1, 1.1)), k(4.7, f"antler_{side}0", (0, 0, 0), scl=(1, 1, 1)),
                 k(8.0, f"antler_{side}0", (0, 0, 0), scl=(1, 1, 1))]
        for i in range(4):
            t0 = 4.4 + i * 0.12 + (0.05 if s < 0 else 0)
            b = f"blossom_{side}{i}"
            keys += [k(0, b, scl=(0, 0, 0)), k(t0, b, scl=(0, 0, 0)), k(t0 + 0.12, b, scl=(1.35, 1.35, 1.35), linear=True),
                     k(t0 + 0.3, b, scl=(1, 1, 1)), k(8.0, b, scl=(1, 1, 1))]
        for i in range(4):
            t0 = 4.0 + i * 0.1
            f = f"flower_{side}{i}"
            keys += [k(0, f, scl=(0, 0, 0)), k(t0, f, scl=(0, 0, 0)), k(t0 + 0.12, f, scl=(1.3, 1.3, 1.3), linear=True),
                     k(t0 + 0.25, f, scl=(1, 1, 1)), k(8.0, f, scl=(1, 1, 1))]
        # the clock and the compass pulled up on their chains, dropped, swinging
        for t, a in ((0, -150), (4.6, -150), (4.95, 26), (5.4, -13), (5.9, 7), (6.4, 0), (8.0, 0)):
            keys.append(k(t, f"chain_{side}0", (0, 0, a), linear=t == 4.95, s=s))
        # the map folded shut, opening like a book
        keys += [k(0, f"face_{side}", (0, 90, 0), s=s), k(5.6, f"face_{side}", (0, 90, 0), s=s), k(6.05, f"face_{side}", (0, -8, 0), s=s),
                 k(6.3, f"face_{side}", (0, 0, 0), s=s), k(8.0, f"face_{side}", (0, 0, 0), s=s)]
    # the banners rolled up, unrolling one after another, front to back, left and right in turn
    for s, side, i in BANNERS:
        t0 = 3.0 + 0.14 * (i * 2 + (0 if s > 0 else 1))
        b = f"banner_{side}{i}"
        keys += [k(0, b, (0, 0, 0), scl=(1, 0.02, 1)), k(t0, b, (0, 0, 0), scl=(1, 0.02, 1)),
                 k(t0 + 0.18, b, (-14, 0, 0), linear=True, scl=(1, 1.12, 1), s=s), k(t0 + 0.4, b, (4, 0, 0), scl=(1, 1, 1), s=s),
                 k(t0 + 0.7, b, (0, 0, 0), scl=(1, 1, 1)), k(8.0, b, (0, 0, 0), scl=(1, 1, 1))]
    M.anim("spawn", 8.0, False, keys)


# ---------------------------------------------------------------- crack (into phase 2)
GAP = 1.5  # phase 2's halves: this far each way from the middle


def crack():
    keys = []
    for s, side in SIDES:
        half = f"half_{side}"
        # shuddering, faster and harder
        keys.append(k(0, half, (0, 0, 0), (0, 0, 0)))
        t, n = 0.1, 0
        while t < 1.0:
            a = (0.4 + t) * (1 if n % 2 else -1) * (1 if s > 0 else -1)
            keys.append(k(t, half, (0, 0, a), (0, 0, 0), linear=True))
            t += 0.08
            n += 1
        keys += [k(1.0, half, (0, 0, 0), (0, 0, 0), linear=True), k(1.18, half, (0, 10, 7), (2.6, -0.5, 0), True, s=s),
                 k(1.6, half, (0, 12, 9), (3.2, -1, 0), s=s), k(3.5, half, (0, 6, 4), (2.6, -0.5, 0), s=s),
                 k(4.4, half, (0, -1, -0.5), (GAP, 0, 0), s=s), k(5.0, half, (0, 0, 0), (GAP, 0, 0), s=s)]
        # the map tearing
        keys += [k(0, f"face_{side}", (0, 0, 0), (0, 0, 0)), k(1.0, f"face_{side}", (0, -6, 2), (0.6, 0, 0), s=s),
                 k(1.2, f"face_{side}", (0, -18, 6), (0.8, 0, -0.3), True, s=s), k(3.5, f"face_{side}", (0, -12, 4), (0.4, 0, 0), s=s),
                 k(5.0, f"face_{side}", (0, 0, 0), (0, 0, 0))]
        # flung apart: the arms out, the heads thrown back and apart, the window halves torn outwards
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(1.0, f"arm_{side}", (-6, 0, 4), s=s), k(1.25, f"arm_{side}", (-30, 0, -50), True, s=s),
                 k(3.5, f"arm_{side}", (-24, 0, -42), s=s), k(5.0, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(1.25, f"forearm_{side}", (-30, 0, 0), True, s=s),
                 k(3.5, f"forearm_{side}", (-20, 0, 0), s=s), k(5.0, f"forearm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"head_{side}", (0, 0, 0), s=s), k(1.0, f"head_{side}", (8, 0, 0), s=s), k(1.3, f"head_{side}", (-22, 0, 12), True, s=s),
                 k(3.5, f"head_{side}", (-16, 0, 9), s=s), k(5.0, f"head_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"halo_{side}", (0, 0, 0), s=s), k(1.2, f"halo_{side}", (0, 6, 10), True, s=s),
                 k(3.5, f"halo_{side}", (0, 4, 6), s=s), k(5.0, f"halo_{side}", (0, 0, 0), s=s)]
        for t, a in ((0, 0), (1.15, -30), (1.45, 20), (1.9, -12), (2.5, 6), (3.2, 0), (5.0, 0)):
            keys.append(k(t, f"chain_{side}0", (0, 0, a), s=s))
    for s, side, i in BANNERS:  # the robe whipping round as it splits
        b = f"banner_{side}{i}"
        keys += [k(0, b, (0, 0, 0)), k(1.0, b, (0, 0, 0)), k(1.25, b, (-26, 0, -6), True, s=s), k(1.7, b, (10, 0, 3), s=s),
                 k(2.4, b, (-4, 0, 0), s=s), k(3.2, b, (0, 0, 0)), k(5.0, b, (0, 0, 0))]
    M.anim("crack", 5.0, False, keys)


# ---------------------------------------------------------------- the attacks (timed to boss/NamelessCreator.java)
def starburst():
    """2.5 s: arms up before it, the window spinning ever faster as it charges; a thrust at each volley (0.75, 1.25, 1.75)."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.6, f"arm_{side}", (-72, 0, -22), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(0.6, f"forearm_{side}", (-12, 0, 0), s=s)]
        for t0 in (0.75, 1.25, 1.75):
            keys += [k(t0 - 0.05, f"arm_{side}", (-72, 0, -22), s=s), k(t0, f"arm_{side}", (-86, 0, -18), linear=True, s=s),
                     k(t0 + 0.2, f"arm_{side}", (-72, 0, -22), s=s)]
        keys += [k(2.0, f"arm_{side}", (-60, 0, -18), s=s), k(2.5, f"arm_{side}", (0, 0, 0), s=s), k(2.5, f"forearm_{side}", (0, 0, 0), s=s)]
        for t, a in ((0, 0), (0.6, 40), (0.75, 90), (1.25, 230), (1.75, 380), (2.5, 450)):
            keys.append(k(t, f"halo_{side}", (0, 0, a), linear=t > 0))
    for t, x in ((0, 0), (0.6, -4), (0.75, -7), (0.9, -4), (1.25, -7), (1.4, -4), (1.75, -7), (1.9, -4), (2.5, 0)):
        keys.append(k(t, "body", (x, 0, 0)))
    M.anim("starburst", 2.5, False, keys)


def slash():
    """0.8 s: the right arm raised across it (0-0.5) and swept down and out - the cut (0.6)."""
    keys = [k(0, "arm_r", (0, 0, 0), s=-1), k(0.45, "arm_r", (-160, 0, 18), s=-1), k(0.6, "arm_r", (-50, 0, -75), linear=True, s=-1),
            k(0.8, "arm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "forearm_r", (0, 0, 0), s=-1), k(0.45, "forearm_r", (-20, 0, 0), s=-1), k(0.6, "forearm_r", (0, 0, 0), linear=True, s=-1),
             k(0.8, "forearm_r", (0, 0, 0), s=-1)]
    for side in ("l", "r"):
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.45, f"head_{side}", (-6, 18, 0)), k(0.6, f"head_{side}", (4, -16, 0), linear=True),
                 k(0.8, f"head_{side}", (0, 0, 0))]
    keys += [k(0, "body", (0, 0, 0)), k(0.45, "body", (0, 12, 0)), k(0.6, "body", (0, -10, 0), linear=True), k(0.8, "body", (0, 0, 0))]
    M.anim("slash", 0.8, False, keys)


def pentagram():
    """2.2 s: both arms high, drawing the star in the air (0-1.0); brought down and out - the five cuts (1.2)."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.35, f"arm_{side}", (-168, 0, -16), s=s)]
        for n, t in enumerate((0.5, 0.65, 0.8, 0.95)):
            keys.append(k(t, f"arm_{side}", (-168 + (8 if n % 2 else -6), 0, -16 + (12 if n % 2 else -8)), s=s))
        keys += [k(1.1, f"arm_{side}", (-170, 0, -12), s=s), k(1.25, f"arm_{side}", (-30, 0, -72), linear=True, s=s),
                 k(1.7, f"arm_{side}", (-24, 0, -64), s=s), k(2.2, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(0.35, f"forearm_{side}", (-22, 0, 0), s=s), k(1.1, f"forearm_{side}", (-22, 0, 0), s=s),
                 k(1.25, f"forearm_{side}", (0, 0, 0), linear=True, s=s), k(2.2, f"forearm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.4, f"head_{side}", (-18, 0, 0)), k(1.1, f"head_{side}", (-20, 0, 0)),
                 k(1.25, f"head_{side}", (10, 0, 0), linear=True), k(2.2, f"head_{side}", (0, 0, 0))]
        keys += [k(0, f"halo_{side}", (0, 0, 0)), k(1.1, f"halo_{side}", (0, 0, 10)), k(1.25, f"halo_{side}", (0, 0, 72), linear=True),
                 k(2.2, f"halo_{side}", (0, 0, 72))]
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(1.1, "body", (-5, 0, 0), (0, -2, 0)), k(1.25, "body", (6, 0, 0), (0, 1, 0), linear=True),
             k(2.2, "body", (0, 0, 0), (0, 0, 0))]
    M.anim("pentagram", 2.2, False, keys)


def ring():
    """3.2 s: arms spread wide (0-0.5), brought slowly together before it as the ring closes, clapped (2.6)."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.5, f"arm_{side}", (-10, 0, -88), s=s), k(0.6, f"arm_{side}", (-12, 0, -86), s=s),
                 k(2.5, f"arm_{side}", (-78, 0, -4), s=s), k(2.6, f"arm_{side}", (-82, 0, 6), linear=True, s=s),
                 k(2.8, f"arm_{side}", (-80, 0, 2), s=s), k(3.2, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"hand_{side}", (0, 0, 0), s=s), k(2.5, f"hand_{side}", (0, 0, 0), s=s), k(2.6, f"hand_{side}", (0, 40, 0), s=s),
                 k(3.2, f"hand_{side}", (0, 0, 0), s=s)]
        for t, a in ((0, 0), (0.6, 0), (2.6, -90), (3.2, -90)):
            keys.append(k(t, f"halo_{side}", (0, 0, a)))
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(0.6, "body", (-4, 0, 0), (0, -1.5, 0)), k(2.5, "body", (2, 0, 0), (0, -2, 0)),
             k(2.6, "body", (5, 0, 0), (0, -1, 0), linear=True), k(3.2, "body", (0, 0, 0), (0, 0, 0))]
    M.anim("ring", 3.2, False, keys)


def bloom():
    """1.6 s: the head bowed and shaken, the antlers shedding their flowers."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.4, f"head_{side}", (24, 0, 0))]
        for n, t in enumerate((0.5, 0.6, 0.7, 0.8, 0.9)):
            keys.append(k(t, f"head_{side}", (24, 9 if n % 2 else -9, 0), linear=True))
        keys += [k(1.0, f"head_{side}", (22, 0, 0)), k(1.6, f"head_{side}", (0, 0, 0))]
        keys += [k(0, f"antler_{side}0", (0, 0, 0), s=s), k(0.5, f"antler_{side}0", (0, 0, 8), s=s), k(0.6, f"antler_{side}0", (0, 0, -8), s=s),
                 k(0.7, f"antler_{side}0", (0, 0, 6), s=s), k(0.8, f"antler_{side}0", (0, 0, -4), s=s), k(1.0, f"antler_{side}0", (0, 0, 0), s=s)]
        for i in range(4):
            b = f"blossom_{side}{i}"
            keys += [k(0, b, scl=(1, 1, 1)), k(0.45 + i * 0.05, b, scl=(1.4, 1.4, 1.4)), k(0.6 + i * 0.05, b, scl=(0.7, 0.7, 0.7)),
                     k(1.4, b, scl=(1, 1, 1))]
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.4, f"arm_{side}", (-20, 0, -30), s=s), k(1.6, f"arm_{side}", (0, 0, 0), s=s)]
    M.anim("bloom", 1.6, False, keys)


def collapse():
    """6.0 s: arms raised high over it, holding the star, trembling (0.6-3.5), the window spinning; pulled down as it
    collapses (3.5-3.9); braced, an arm over its face, when it goes off (4.5)."""
    keys = []
    rnd = random.Random(71)
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.6, f"arm_{side}", (-172, 0, -24), s=s)]
        t = 0.85
        while t < 3.5:
            keys.append(k(t, f"arm_{side}", (-172 + rnd.uniform(-3, 3), 0, -24 + rnd.uniform(-3, 3)), s=s))
            t += 0.25
        keys += [k(3.5, f"arm_{side}", (-174, 0, -22), s=s), k(3.9, f"arm_{side}", (-24, 0, -42), linear=True, s=s),
                 k(4.4, f"arm_{side}", (-95, 0, 22), s=s), k(4.5, f"arm_{side}", (-80, 0, 30), linear=True, s=s),
                 k(5.2, f"arm_{side}", (-70, 0, 20), s=s), k(6.0, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(0.6, f"forearm_{side}", (-10, 0, 0), s=s), k(3.5, f"forearm_{side}", (-10, 0, 0), s=s),
                 k(4.4, f"forearm_{side}", (-70, 0, 0), s=s), k(5.2, f"forearm_{side}", (-60, 0, 0), s=s), k(6.0, f"forearm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"head_{side}", (0, 0, 0)), k(0.6, f"head_{side}", (-24, 0, 0)), k(3.5, f"head_{side}", (-26, 0, 0)),
                 k(4.4, f"head_{side}", (18, 0, 0)), k(4.5, f"head_{side}", (-10, 0, 0), linear=True), k(6.0, f"head_{side}", (0, 0, 0))]
        for t, a in ((0, 0), (0.6, 30), (3.5, 720), (3.9, 760), (6.0, 760)):
            keys.append(k(t, f"halo_{side}", (0, 0, a), linear=0 < t <= 3.5))
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(0.6, "body", (-6, 0, 0), (0, -2, 0)), k(3.5, "body", (-8, 0, 0), (0, -3, 0)),
             k(4.4, "body", (8, 0, 0), (0, 0, 0)), k(4.5, "body", (-14, 0, 0), (0, -1, 2), linear=True), k(6.0, "body", (0, 0, 0), (0, 0, 0))]
    M.anim("collapse", 6.0, False, keys)


def chart():
    """5.0 s: its right hand at its face, drawing on the map with quick small strokes; a nod as each wave is marked
    (0, 1.5, 3.0)."""
    keys = [k(0, "arm_r", (0, 0, 0), s=-1), k(0.4, "arm_r", (-118, 0, 38), s=-1), k(4.6, "arm_r", (-118, 0, 38), s=-1),
            k(5.0, "arm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "forearm_r", (0, 0, 0), s=-1), k(0.4, "forearm_r", (-62, 0, 0), s=-1), k(4.6, "forearm_r", (-62, 0, 0), s=-1),
             k(5.0, "forearm_r", (0, 0, 0), s=-1)]
    t, n = 0.5, 0
    while t < 4.6:
        keys.append(k(round(t, 2), "hand_r", (8 if n % 2 else -8, 0, 14 if n % 3 == 0 else -12), linear=True, s=-1))
        t += 0.15
        n += 1
    keys += [k(0, "hand_r", (0, 0, 0), s=-1), k(5.0, "hand_r", (0, 0, 0), s=-1)]
    for side in ("l", "r"):
        keys.append(k(0, f"head_{side}", (0, 0, 0)))
        for w in (0.0, 1.5, 3.0):
            keys += [k(w + 0.1, f"head_{side}", (6, -10, 0)), k(w + 0.25, f"head_{side}", (14, -10, 0)), k(w + 0.5, f"head_{side}", (6, -10, 0))]
        keys.append(k(5.0, f"head_{side}", (0, 0, 0)))
    M.anim("chart", 5.0, False, keys)


def paint(M, texs):
    paint_cubes(M, texs["creator_p1"], texs["creator_p1_glow"], {1: STYLE_L, -1: STYLE_R}, random.Random(17))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    spawn()
    idle()
    crack()
    starburst()
    slash()
    pentagram()
    ring()
    bloom()
    collapse()
    chart()
    build(M, 256, paint, ["creator_p1", "creator_p1_glow"], argv[0], argv[1], preview_z=26.0)


main()
