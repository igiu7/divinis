"""
Builds the Nameless Creator's third phase, the Skeleton (form without matter), in Blender: what was under the paper -
the model's own skeleton. Every part an untextured box drawn only as white outlines, like hitboxes on F3+B (the red
line of its eye height, the blue line of where it looks), joined by chain links: the torso, a stack of boxes for the
robe, the head, the arms, the rose window as a ring of boxes, the clock and the compass as empty frames on chains, the
antlers as sticks. Its hands are solid: huge, long, pale grey, two palm joints and five fingers of four joints each -
held over its face. Its face, behind them, is the missing texture (magenta and black, glowing), seen between the
fingers.

  blender -b --python tools/models/build_creator_p3.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/CreatorP3ModelData.java (creator_p3.png, creator_p3_glow.png: the
outlines and the face glow). Drawn three times its pixels, as phases 1 and 2.

Animations:
  emerge  4.0      (out of phase 2's ashes) the face hangs alone; then the parts load in one by one, as chunks do -
                   each blinking in at the wrong place and jumping to its own - the robe from the hem up, the torso,
                   the head, the arms, the hands last (0.3-3.4); it settles
  idle    4.0 loop the hands trembling over the face, the fingers twitching; now and then a part blinks out of place
                   for a moment (a chunk loaded wrong)
  spread  1.0 loop a pose: the hands drawn apart, the face between them (the boss plays it more and more as its
                   health goes: KeyframeAnimations.animate(model, SPREAD, 0, k, cache), k 0..1)
  unveil  5.0      (into phase 4) the hands shake harder and harder (0-1.2), are torn apart (1.2-1.8) and fall limp
                   to its sides (1.8-2.4); the face blazes (2.4-3.0); then everything but the face unloads, part after
                   part (3.0-4.3), and the face shrinks and sinks to where phase 4's face is (4.3-5.0)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from creator_common import SIDES, Model, border, build, face_rects, k, paint_face, shade  # noqa: E402
from creator_common import P3_SCALE, P4_FACE_Y, P4_SCALE  # noqa: E402

WIRE = (244, 244, 248)
EYE_LINE = (230, 40, 40)
LOOK_LINE = (60, 90, 255)
BONE = (200, 200, 206)
MISSING = (248, 0, 248)
FINGER_X = (-2.4, -1.2, 0.0, 1.2, 2.4)
FINGER_LEN = (3, 4, 5, 4, 3)  # pixels per joint: the middle three longer

M = Model("creator_p3")
M.part("body", None, (0, 4, 0))
M.part("torso", "body", (0, 0, 0))
M.box("torso", (-7, -14, -2.5), (14, 14, 5), "wire")
for i, (w, d) in enumerate(((12, 8), (14, 10), (16, 12))):
    M.part(f"robe{i}", "body", (0, i * 6, 0))
    M.box(f"robe{i}", (-w / 2, 0, -d / 2), (w, 6, d), "wire")
# the face, hung on the body (not in the head) so it can stay when everything else is gone
FACE_Y = -20
M.part("face", "body", (0, FACE_Y, 0))
M.box("face", (-4, -4, -4), (8, 8, 8), "missing")
M.part("head", "body", (0, -14, 0))
M.box("head", (-0.5, -2, -0.5), (1, 2, 1), "chain")
M.box("head", (-5, -11, -5), (10, 10, 10), "wire")
M.box("head", (-5.5, -7, -5.5), (11, 0, 11), "eyeline")
M.part("look", "head", (0, -7, -5.5))
M.box("look", (-0.5, -0.5, -14), (1, 1, 14), "lookline")
for s, t in SIDES:
    a0 = M.part(f"antler_{t}0", "head", (3, -11, 0), (0, 0, 25), s=s)
    M.box(a0, (-0.5, -8, -0.5), (1, 8, 1), "wire", s)
    a1 = M.part(f"antler_{t}1", a0, (0, -8, 0), (-10, 0, 25), s=s)
    M.box(a1, (-0.5, -6, -0.5), (1, 6, 1), "wire", s)
    a2 = M.part(f"antler_{t}2", a0, (0, -4.5, 0), (0, 0, -40), s=s)
    M.box(a2, (-0.5, -5, -0.5), (1, 5, 1), "wire", s)

    arm = M.part(f"arm_{t}", "body", (9, -11, 0), (-72, 0, 30), s=s)
    M.box(arm, (-0.5, -2, -0.5), (1, 2, 1), "chain", s)
    M.box(arm, (-1.5, 0, -1.5), (3, 9, 3), "wire", s)
    fore = M.part(f"forearm_{t}", arm, (0, 9, 0), (-112, 0, 0), s=s)
    M.box(fore, (-0.5, -1, -0.5), (1, 2, 1), "chain", s)
    M.box(fore, (-1.5, 0, -1.5), (3, 8, 3), "wire", s)
    p0 = M.part(f"palm_{t}0", fore, (0, 9, 0), (-8, 0, 42), s=s)
    M.box(p0, (-0.5, -1.5, -0.5), (1, 2, 1), "chain", s)
    M.box(p0, (-3, 0, -1), (6, 4, 2), "bone", s)
    p1 = M.part(f"palm_{t}1", p0, (0, 4.4, 0), (-6, 0, 0), s=s)
    M.box(p1, (-3, 0, -1), (6, 4, 2), "bone", s)
    for f, x in enumerate(FINGER_X):
        prev = p1
        for j in range(4):
            fj = M.part(f"finger_{t}{f}{j}", prev, (x, 4.3, 0) if j == 0 else (0, FINGER_LEN[f] + 0.3, 0),
                        (-14 if j else 0, 0, (x * 4 if j == 0 else 0)), s=s)
            M.box(fj, (-0.5, 0, -0.5), (1, FINGER_LEN[f], 1), "bone", s, joint=j)
            prev = fj

    prev = "body"
    for j in range(4):
        c = M.part(f"chain_{t}{j}", prev, (16.5, -20, 4) if j == 0 else (0, 2, 0), s=s)
        M.box(c, (-0.5, 0, -0.5), (1, 2, 1), "chain", s)
        prev = c
    obj = M.part(f"clock_{t}", prev, (0, 2, 0), s=s)
    M.box(obj, (-4.5, 0, -0.5), (9, 9, 1), "wire", s)

M.part("halo", "body", (0, -20, 6))
for i in range(12):
    seg = M.part(f"halo_seg{i}", "halo", (0, 0, 0), (0, 0, 15 + i * 30))
    M.box(seg, (-4.5, -18, -1), (9, 2, 2), "wire")
for i in range(4):
    sp = M.part(f"halo_spoke{i}", "halo", (0, 0, 0), (0, 0, i * 45))
    M.box(sp, (-0.5, -16, -0.5), (1, 32, 1), "wire")

FINGERS = [f"finger_{t}{f}{j}" for _, t in SIDES for f in range(5) for j in range(4)]
WIRES = (["robe2", "robe1", "robe0", "torso", "head", "look", "halo"] + [f"halo_seg{i}" for i in range(12)]
         + [f"halo_spoke{i}" for i in range(4)] + [f"{p}_{t}{j}" for _, t in SIDES for p in ("antler",) for j in range(3)]
         + [f"chain_{t}{j}" for _, t in SIDES for j in range(4)] + [f"clock_{t}" for _, t in SIDES])
HANDS = [f"{p}_{t}" for _, t in SIDES for p in ("arm", "forearm")] + [f"palm_{t}{j}" for _, t in SIDES for j in range(2)]


# ---------------------------------------------------------------- animations
def pop_in(part, t0, rnd, wrong=6.0, twice=False):
    """A part loading in: nothing, then blinking in at a wrong place, then jumping to its own."""
    off = (rnd.uniform(-wrong, wrong), rnd.uniform(-wrong, wrong), rnd.uniform(-wrong / 2, wrong / 2))
    keys = [k(0, part, pos=(0, 0, 0), scl=(0, 0, 0), linear=True), k(t0, part, pos=off, scl=(0, 0, 0), linear=True),
            k(t0 + 0.05, part, pos=off, scl=(1, 1, 1), linear=True)]
    t = t0 + 0.2
    if twice:
        off2 = (rnd.uniform(-wrong, wrong), rnd.uniform(-wrong, wrong), 0)
        keys += [k(t, part, pos=off, scl=(1, 1, 1), linear=True), k(t + 0.05, part, pos=off2, scl=(1, 1, 1), linear=True)]
        t += 0.2
    keys += [k(t, part, pos=off if not twice else off2, scl=(1, 1, 1), linear=True), k(t + 0.05, part, pos=(0, 0, 0), scl=(1, 1, 1), linear=True),
             k(4.0, part, pos=(0, 0, 0), scl=(1, 1, 1))]
    return keys


def emerge():
    rnd = random.Random(31)
    keys = []
    order = ["robe2", "robe1", "robe0", "torso", "head", "look", "halo", "arm_l", "arm_r", "forearm_l", "forearm_r",
             "chain_l0", "chain_r0", "palm_l0", "palm_r0"]
    t = 0.3
    for n, p in enumerate(order):
        keys += pop_in(p, t, rnd, twice=n % 4 == 1)
        t += 0.17
    for i in range(12):  # the window's ring, segment after segment
        keys += pop_in(f"halo_seg{i}", 1.6 + i * 0.06, rnd, 3.0)
    for _, side in SIDES:
        for j in range(3):
            keys += pop_in(f"antler_{side}{j}", 1.4 + j * 0.15, rnd, 3.0)
    for f in FINGERS:  # the fingers last, all at once nearly
        keys += pop_in(f, 2.9 + rnd.random() * 0.4, rnd, 2.0)
    for t2, sc in ((0, 1.0), (0.1, 1.25), (0.2, 1.0), (2.9, 1.0), (3.0, 1.3), (3.2, 1.0), (4.0, 1.0)):
        keys.append(k(t2, "face", scl=(sc, sc, sc), linear=True))
    M.anim("emerge", 4.0, False, keys)


def idle():
    rnd = random.Random(37)
    keys = []
    for t in (0, 1, 2, 3, 4):
        keys.append(k(t, "body", (0, 0, 0), (0, -0.7 * math.sin(t / 4 * math.tau), 0)))
        keys.append(k(t, "halo", (0, 0, 3 * math.sin(t / 4 * math.tau))))
    # the hands trembling (palms and arms, quick and small), the fingers twitching
    for s, side in SIDES:
        for p, amp in ((f"palm_{side}0", 2.0), (f"palm_{side}1", 2.5), (f"forearm_{side}", 0.8)):
            for n in range(41):
                end = n in (0, 40)
                keys.append(k(n / 10, p, (0 if end else rnd.uniform(-amp, amp), 0, 0 if end else rnd.uniform(-amp, amp) * 0.5),
                              linear=True, s=s))
        for f in range(5):
            for j in range(4):
                ph = rnd.random()
                for t in (0, 1, 2, 3, 4):
                    keys.append(k(t, f"finger_{side}{f}{j}", (4 * math.sin((t / 4 + ph) * math.tau), 0, 0), s=s))
    # a part now and then blinking out of place for a moment
    for p, t0, off in (("robe1", 0.9, (4, -1, 0)), ("halo_seg3", 1.5, (0, 3, 0)), ("forearm_r", 2.1, (0, 2, -3)),
                       ("robe2", 2.7, (-5, 0, 2)), ("head", 3.3, (2, -2, 0)), ("clock_l", 3.6, (0, -4, 0))):
        keys += [k(0, p, pos=(0, 0, 0)), k(t0, p, pos=(0, 0, 0), linear=True), k(t0 + 0.05, p, pos=off, linear=True),
                 k(t0 + 0.15, p, pos=off, linear=True), k(t0 + 0.2, p, pos=(0, 0, 0), linear=True), k(4.0, p, pos=(0, 0, 0))]
    M.anim("idle", 4.0, True, keys)


def spread():
    keys = []
    for s, side in SIDES:
        for t in (0, 1):
            keys += [k(t, f"arm_{side}", (0, 0, -24), s=s), k(t, f"forearm_{side}", (0, 0, 0), s=s),
                     k(t, f"palm_{side}0", (6, 0, -24), s=s)]
            for f in range(5):
                keys.append(k(t, f"finger_{side}{f}0", (0, 0, (FINGER_X[f]) * 5), s=s))
    M.anim("spread", 1.0, True, keys)


def unveil():
    rnd = random.Random(41)
    keys = []
    for s, side in SIDES:
        # shaking harder and harder
        t = 0.0
        while t < 1.2:
            a = (0.5 + t * 4) * (1 if int(t * 10) % 2 else -1)
            keys.append(k(round(t, 2), f"palm_{side}0", (a, 0, a * 0.6), linear=True, s=s))
            keys.append(k(round(t, 2), f"arm_{side}", (a * 0.3, 0, a * 0.2), linear=True, s=s))
            t += 0.1
        # torn apart, then falling limp to its sides
        keys += [k(1.2, f"arm_{side}", (0, 0, 0), linear=True, s=s), k(1.6, f"arm_{side}", (10, 0, -40), s=s),
                 k(1.8, f"arm_{side}", (8, 0, -36), s=s), k(2.2, f"arm_{side}", (78, 0, -18), linear=True, s=s),
                 k(2.45, f"arm_{side}", (66, 0, -20), s=s), k(5.0, f"arm_{side}", (70, 0, -20), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(1.8, f"forearm_{side}", (20, 0, 0), s=s),
                 k(2.2, f"forearm_{side}", (112, 0, 0), linear=True, s=s), k(2.4, f"forearm_{side}", (96, 0, 0), s=s),
                 k(5.0, f"forearm_{side}", (104, 0, 0), s=s)]
        keys += [k(1.2, f"palm_{side}0", (0, 0, 0), linear=True, s=s), k(1.6, f"palm_{side}0", (10, 0, -30), s=s),
                 k(2.3, f"palm_{side}0", (8, 0, -18), s=s), k(5.0, f"palm_{side}0", (8, 0, -18), s=s)]
        for f in range(5):
            for j in range(4):
                fj = f"finger_{side}{f}{j}"
                keys += [k(0, fj, (0, 0, 0), s=s), k(1.6, fj, (-10, 0, (FINGER_X[f] * 6 if j == 0 else 0)), s=s),
                         k(2.4, fj, (16 + rnd.uniform(-6, 6), 0, 0), s=s), k(5.0, fj, (12, 0, 0), s=s)]
    # the face blazing as it is uncovered
    keys += [k(0, "face", pos=(0, 0, 0), scl=(1, 1, 1)), k(2.4, "face", pos=(0, 0, 0), scl=(1, 1, 1)),
             k(2.6, "face", pos=(0, 0, -1), scl=(1.25, 1.25, 1.25)), k(3.0, "face", pos=(0, 0, 0), scl=(1, 1, 1))]
    # everything else unloading, part after part
    gone = WIRES + HANDS
    rnd.shuffle(gone)
    for n, p in enumerate(gone):
        t0 = 3.0 + n * (1.3 / len(gone))
        off = (rnd.uniform(-4, 4), rnd.uniform(-4, 4), 0)
        keys += [k(0, p, pos=(0, 0, 0), scl=(1, 1, 1)), k(t0, p, pos=(0, 0, 0), scl=(1, 1, 1), linear=True),
                 k(t0 + 0.05, p, pos=off, scl=(1, 1, 1), linear=True), k(t0 + 0.1, p, pos=off, scl=(0, 0, 0), linear=True),
                 k(5.0, p, pos=off, scl=(0, 0, 0))]
    # the face shrinks and sinks to where phase 4's face is (that model is drawn P4_SCALE / P3_SCALE as big)
    face_y = 4 + FACE_Y  # model y of the face's middle
    drop = ((24 - face_y) * P3_SCALE - (24 - P4_FACE_Y) * P4_SCALE) / P3_SCALE
    sc = P4_SCALE / P3_SCALE
    keys += [k(4.3, "face", pos=(0, 0, 0), scl=(1, 1, 1)), k(4.8, "face", pos=(0, drop * 0.9, 0), scl=(sc * 0.9, sc * 0.9, sc * 0.9)),
             k(5.0, "face", pos=(0, drop, 0), scl=(sc, sc, sc))]
    M.anim("unveil", 5.0, False, keys)


# ---------------------------------------------------------------- the attacks (timed to boss/NamelessCreator.java)
def lslash():
    """0.5 s: a hand torn from its face and swept out across - a cut of light (0.3) - and back."""
    keys = [k(0, "arm_r", (0, 0, 0), s=-1), k(0.2, "arm_r", (20, 0, 30), s=-1), k(0.3, "arm_r", (60, 0, -70), linear=True, s=-1),
            k(0.5, "arm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "forearm_r", (0, 0, 0), s=-1), k(0.3, "forearm_r", (90, 0, 0), linear=True, s=-1), k(0.5, "forearm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "torso", (0, 0, 0)), k(0.2, "torso", (0, 15, 0)), k(0.3, "torso", (0, -20, 0), linear=True), k(0.5, "torso", (0, 0, 0))]
    M.anim("lslash", 0.5, False, keys)


def chronos():
    """6.0 s: its left clock torn off its chain, grown huge and spinning (its hand becomes the beam, 0.5-4.5), the
    window turning ever faster; then still - and everything goes back (4.5-6.0)."""
    keys = [k(0, "chain_l0", (0, 0, 0), s=1), k(0.5, "chain_l0", (0, 0, -150), s=1), k(4.5, "chain_l0", (0, 0, -150), s=1),
            k(6.0, "chain_l0", (0, 0, 0), s=1)]
    keys += [k(0, "clock_l", (0, 0, 0), scl=(1, 1, 1)), k(0.5, "clock_l", (0, 0, 0), scl=(2.4, 2.4, 2.4))]
    for n, t in enumerate((1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5)):
        keys.append(k(t, "clock_l", (0, 0, -(n + 1) * (60 + n * 25)), scl=(2.4, 2.4, 2.4), linear=True))
    keys += [k(5.5, "clock_l", (0, 0, -360 * 4), scl=(2.4, 2.4, 2.4)), k(6.0, "clock_l", (0, 0, -360 * 4), scl=(1, 1, 1))]
    for t, a in ((0, 0), (1.0, 40), (2.5, 220), (4.5, 720), (6.0, 400)):
        keys.append(k(t, "halo", (0, 0, a), linear=0 < t <= 4.5))
    keys += [k(0, "head", (0, 0, 0)), k(0.5, "head", (-10, -20, 0)), k(4.5, "head", (-10, -20, 0)), k(6.0, "head", (0, 0, 0))]
    M.anim("chronos", 6.0, False, keys)


def cage():
    """2.5 s: the hands pushed outwards from the face, fingers spread, pulsing (0.3-1.5); snapped shut (1.5)."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"palm_{side}0", (0, 0, 0), s=s), k(0.3, f"palm_{side}0", (10, 0, -30), s=s), k(0.9, f"palm_{side}0", (6, 0, -26), s=s),
                 k(1.4, f"palm_{side}0", (12, 0, -34), s=s), k(1.5, f"palm_{side}0", (-6, 0, 6), linear=True, s=s), k(2.5, f"palm_{side}0", (0, 0, 0), s=s)]
        for f in range(5):
            keys += [k(0, f"finger_{side}{f}0", (0, 0, 0), s=s), k(0.3, f"finger_{side}{f}0", (-20, 0, FINGER_X[f] * 8), s=s),
                     k(1.4, f"finger_{side}{f}0", (-20, 0, FINGER_X[f] * 8), s=s), k(1.5, f"finger_{side}{f}0", (30, 0, 0), linear=True, s=s),
                     k(2.5, f"finger_{side}{f}0", (0, 0, 0), s=s)]
    M.anim("cage", 2.5, False, keys)


def chunks():
    """5.0 s: the whole of it glitching - parts blinking out of place again and again, as its floor unloads."""
    rnd = random.Random(43)
    keys = []
    for p in ["robe0", "robe1", "robe2", "torso", "head", "halo", "arm_l", "arm_r", "clock_l", "clock_r"]:
        keys.append(k(0, p, pos=(0, 0, 0)))
        t = rnd.uniform(0.2, 0.8)
        while t < 4.6:
            off = (rnd.uniform(-4, 4), rnd.uniform(-3, 3), rnd.uniform(-2, 2))
            keys += [k(round(t, 2), p, pos=(0, 0, 0), linear=True), k(round(t + 0.05, 2), p, pos=off, linear=True),
                     k(round(t + 0.15, 2), p, pos=off, linear=True), k(round(t + 0.2, 2), p, pos=(0, 0, 0), linear=True)]
            t += rnd.uniform(0.6, 1.3)
        keys.append(k(5.0, p, pos=(0, 0, 0)))
    keys += [k(0, "body", (0, 0, 0)), k(1.0, "body", (0, 0, 6)), k(2.5, "body", (0, 0, -6)), k(4.0, "body", (0, 0, 4)), k(5.0, "body", (0, 0, 0))]
    M.anim("chunks", 5.0, False, keys)


# ---------------------------------------------------------------- painting
def paint(M, texs):
    tex, glow = texs["creator_p3"], texs["creator_p3_glow"]
    for cube in M.cubes:
        meta = M.meta[cube[0]]
        kind = meta["kind"]
        for face, rect in face_rects(cube).items():
            def f(xx, yy, w, h, face=face):
                if kind == "wire":
                    on = border(xx, yy, w, h) or w <= 2 or h <= 2
                    return (WIRE, shade(WIRE, 0.55)) if on else (None, None)
                if kind == "eyeline":
                    return (EYE_LINE, shade(EYE_LINE, 0.6)) if border(xx, yy, w, h) else (None, None)
                if kind == "lookline":
                    return LOOK_LINE, shade(LOOK_LINE, 0.6)
                if kind == "chain":
                    c = (150, 150, 160) if yy % 2 == 0 else (96, 96, 108)
                    return c, None
                if kind == "bone":  # untextured: flat grey, lit from above, the joints darker
                    c = {"top": shade(BONE, 1.12), "bottom": shade(BONE, 0.7)}.get(face, BONE)
                    if face in ("left", "right", "back"):
                        c = shade(c, 0.88)
                    if yy == 0 and face not in ("top", "bottom"):
                        c = shade(c, 0.8)
                    return c, None
                if kind == "missing":  # a 2x2 checker on every face, as the missing texture
                    on = (xx * 2 // w + yy * 2 // h) % 2 == 0
                    return (MISSING, MISSING) if on else ((0, 0, 0), None)
                return (255, 0, 255), None
            paint_face(tex, glow, rect, f, 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    emerge()
    idle()
    spread()
    lslash()
    chronos()
    cage()
    chunks()
    unveil()  # last: it ends with everything gone but the face, which Blender would hold over the later ones
    build(M, 256, paint, ["creator_p3", "creator_p3_glow"], argv[0], argv[1], preview_z=26.0)


main()
