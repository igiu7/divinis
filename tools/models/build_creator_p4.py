"""
Builds the Nameless Creator's fourth phase, the True Form (what the world cannot render), in Blender: some two and a
half times a player's height, drawn out - a long thin body, arms far too long (the fingers hang past its knees), legs
that end in no feet but run out into a streak of light, floating. Pure white, unshaded, no detail, a hole cut in the
picture; its edges split into colours and break up into pixels, as if the world kept trying to voxelise it and kept
failing. Its face is a cube of the missing texture, 8 pixels on a side, made of eight pieces (so it can crack), on a
head of its own that the renderer turns to the player whatever the body does. Behind it, all that is left of its
servants: thin white outlines of Aurelion's wings and Nocthariel's horns, like the sketch it once drew them from.

  blender -b --python tools/models/build_creator_p4.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/CreatorP4ModelData.java. Textures: creator_p4.png (the white body),
creator_p4_glow.png (all of it full light), creator_p4_wire.png (the same parts as bare outlines - the renderer fades
from it to the white as the form fills with light when it comes). Drawn 1.5 times its pixels (creator_common.P4_SCALE).

Animations:
  reveal  4.0      only the face at first; then the body grows out of the middle - the chest, the hips (0.3-1.0),
                   the arms and legs out to the fingers and the streaks of light (1.0-2.5) - and last the outlines of
                   the wings and horns are drawn in behind it, stroke by stroke (2.6-3.6)
  idle    4.0 loop floating, the long arms swaying, the fingers curling, the streaks of its legs trailing
  speak   0.8 loop the face's pieces jump a pixel this way and that, out of step, like z-fighting (while it speaks)
  death   8.0      it rises, arms spread, arching back (0-1.0); the face cracks, its pieces jolting apart (1.0-2.5);
                   it goes out from the edges in: the outlines, the streaks, the fingers, the hands, the arms and
                   legs, the body last, to a point (2.0-5.4); the face's pieces hang alone a moment, then drop to the
                   ground (6.3-7.0) and burst one by one (7.2-8.0)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from creator_common import P4_FACE_Y, SIDES, Model, border, build, face_rects, k, paint_face, shade  # noqa: E402

WHITE = (255, 255, 255)
FRINGE = ((255, 186, 196), (186, 232, 255), (236, 236, 255))
OUTLINE = (232, 232, 240)
MISSING = (248, 0, 248)

M = Model("creator_p4")
M.part("body", None, (0, 1, 0))
M.box("body", (-2.5, 0, -1.5), (5, 3, 3), "white")
M.box("body", (-1.5, -4, -1), (3, 4, 2), "white")
M.part("chest", "body", (0, -4, 0))
M.box("chest", (-3.5, -10, -1.5), (7, 10, 3), "white")
M.box("chest", (-1, -14, -1), (2, 4, 2), "white")

# the face: its own head (turned to the player by the renderer), eight pieces
HEAD_Y = P4_FACE_Y + 5
M.part("head", None, (0, HEAD_Y, 0))
PIECES = []
for n in range(8):
    ix, iy, iz = n & 1, n >> 1 & 1, n >> 2 & 1
    p = M.part(f"face_{n}", "head", (2 if ix else -2, -5 + (2 if iy else -2), 2 if iz else -2))
    M.box(p, (-2, -2, -2), (4, 4, 4), "piece", ix=ix, iy=iy, iz=iz)
    PIECES.append((p, ix, iy, iz))

for s, t in SIDES:
    arm = M.part(f"arm_{t}", "chest", (4.5, -9, 0), (0, 0, -9), s=s)
    M.box(arm, (-1, 0, -1), (2, 14, 2), "white", s)
    fore = M.part(f"forearm_{t}", arm, (0, 14, 0), (-12, 0, 0), s=s)
    M.box(fore, (-1, 0, -1), (2, 13, 2), "white", s)
    hand = M.part(f"hand_{t}", fore, (0, 13, 0), (8, 0, 0), s=s)
    M.box(hand, (-1, 0, -0.5), (2, 3, 1), "white", s)
    for f, x in enumerate((-1.2, 0.0, 1.2)):
        prev = hand
        for j in range(2):
            fj = M.part(f"finger_{t}{f}{j}", prev, (x, 3, 0) if j == 0 else (0, 4, 0), (6 if j else 0, 0, x * 6 if j == 0 else 0), s=s)
            M.box(fj, (-0.5, 0, -0.5), (1, 4, 1), "white", s)
            prev = fj

    leg = M.part(f"leg_{t}", "body", (1.4, 3, 0), (6, 0, -3), s=s)
    M.box(leg, (-1, 0, -1), (2, 9, 2), "white", s)
    shin = M.part(f"shin_{t}", leg, (0, 9, 0), (6, 0, 0), s=s)
    M.box(shin, (-1, 0, -1), (2, 7, 2), "fade", s)
    streak = M.part(f"streak_{t}", shin, (0, 7, 0), (10, 0, 0), s=s)
    M.box(streak, (-0.5, 0, -0.5), (1, 10, 1), "streak", s)
    M.box(streak, (-1.5, 0, 0), (3, 10, 0), "streak", s)

    # what is left of its servants: the outlines of Aurelion's wings, of Nocthariel's horns
    wing = M.part(f"remnant_wing_{t}", "chest", (2, -8, 2.5), (0, -25, -30), s=s)
    M.box(wing, (0, 0, 0), (17, 1, 0), "outline", s)
    for j in range(5):
        fe = M.part(f"remnant_wing_{t}{j}", wing, (2.5 + j * 3.2, 1, 0), (0, 0, -(6 + j * 9)), s=s)
        M.box(fe, (-1.5, 0, 0), (3, 9 + j * 2, 0), "outline", s)
    h0 = M.part(f"remnant_horn_{t}0", "chest", (3, -13, 2), (-10, 0, 12), s=s)
    M.box(h0, (-1.5, -7, 0), (3, 7, 0), "outline", s)
    h1 = M.part(f"remnant_horn_{t}1", h0, (0, -7, 0), (-15, 0, 14), s=s)
    M.box(h1, (-1, -6, 0), (2, 6, 0), "outline", s)
    h2 = M.part(f"remnant_horn_{t}2", h1, (0, -6, 0), (-15, 0, 18), s=s)
    M.box(h2, (-0.5, -5, 0), (1, 5, 0), "outline", s)

REMNANTS = ([f"remnant_wing_{t}" for _, t in SIDES] + [f"remnant_wing_{t}{j}" for _, t in SIDES for j in range(5)]
            + [f"remnant_horn_{t}{j}" for _, t in SIDES for j in range(3)])


def wave(t, period, phase=0.0):
    return math.sin((t / period + phase) * math.tau)


def grow(part, t0, length=0.35, end=4.0, stretch=None):
    """A part growing out of nothing from t0 (stretch: only along y, as a streak running out)."""
    zero = (1, 0, 1) if stretch else (0, 0, 0)
    over = (1, 1.15, 1) if stretch else (1.12, 1.12, 1.12)
    return [k(0, part, scl=zero), k(t0, part, scl=zero, linear=True), k(t0 + length * 0.7, part, scl=over),
            k(t0 + length, part, scl=(1, 1, 1)), k(end, part, scl=(1, 1, 1))]


def reveal():
    keys = []
    keys += grow("body", 0.3, 0.5) + grow("chest", 0.45, 0.55)
    for _, t in SIDES:
        keys += grow(f"arm_{t}", 1.0) + grow(f"forearm_{t}", 1.3) + grow(f"hand_{t}", 1.6)
        for f in range(3):
            keys += grow(f"finger_{t}{f}0", 1.8 + f * 0.05, 0.25) + grow(f"finger_{t}{f}1", 2.0 + f * 0.05, 0.25)
        keys += grow(f"leg_{t}", 1.0) + grow(f"shin_{t}", 1.35) + grow(f"streak_{t}", 1.7, 0.7, stretch=True)
    for n, p in enumerate(REMNANTS):  # drawn in, stroke by stroke
        keys += grow(p, 2.6 + n * 0.06, 0.2)
    for t, sc in ((0, 1.0), (0.15, 1.2), (0.3, 1.0), (2.5, 1.0), (2.6, 1.15), (2.8, 1.0), (4.0, 1.0)):
        keys.append(k(t, "head", scl=(sc, sc, sc)))
    keys += [k(0, "body", pos=(0, 3, 0)), k(1.0, "body", pos=(0, 1, 0)), k(2.5, "body", pos=(0, 0, 0)), k(4.0, "body", pos=(0, 0, 0))]
    M.anim("reveal", 4.0, False, keys)


def idle():
    rnd = random.Random(53)
    keys = []
    for t in (0, 1, 2, 3, 4):
        bob = -1.0 * wave(t, 4)
        keys.append(k(t, "body", (1.5 * wave(t, 4, 0.25), 0, 0), (0, bob, 0)))
        keys.append(k(t, "head", pos=(0, bob, 0)))
        keys.append(k(t, "chest", (-1.5 * wave(t, 4, 0.4), 2 * wave(t, 4, 0.1), 0)))
        for s, side in SIDES:
            ph = 0.0 if s > 0 else 0.5
            keys.append(k(t, f"arm_{side}", (5 * wave(t, 4, ph), 0, -3 * wave(t, 4, ph + 0.25)), s=s))
            keys.append(k(t, f"forearm_{side}", (-6 * wave(t, 4, ph + 0.15), 0, 0), s=s))
            keys.append(k(t, f"hand_{side}", (8 * wave(t, 4, ph + 0.3), 0, 0), s=s))
            keys.append(k(t, f"leg_{side}", (4 + 3 * wave(t, 4, ph + 0.1), 0, 0), s=s))
            keys.append(k(t, f"shin_{side}", (3 * wave(t, 4, ph + 0.3), 0, 0), s=s))
            keys.append(k(t, f"streak_{side}", (8 + 6 * wave(t, 4, ph + 0.45), 0, 2 * wave(t, 2, ph)), s=s))
            keys.append(k(t, f"remnant_wing_{side}", (0, 0, 4 * wave(t, 4, 0.2)), s=s))
            for f in range(3):
                for j in range(2):
                    keys.append(k(t, f"finger_{side}{f}{j}", (12 * wave(t, 4, ph + f * 0.1 + j * 0.15), 0, 0), s=s))
    M.anim("idle", 4.0, True, keys)


def speak():
    rnd = random.Random(59)
    keys = []
    for p, *_ in PIECES:
        for n in range(9):
            if n in (0, 8):
                off = (0, 0, 0)
            else:
                off = tuple(rnd.choice((0, 0, 0.25, -0.25, 0.5, -0.5)) for _ in range(3))
            keys.append(k(n * 0.1, p, pos=off, linear=True))
    M.anim("speak", 0.8, True, keys)


def shrink(part, t0, length=0.3, base=None, s=1):
    """Goes out: from its size to nothing."""
    keys = [k(0, part, scl=(1, 1, 1)), k(t0, part, scl=(1, 1, 1)), k(t0 + length, part, scl=(0, 0, 0), linear=True),
            k(8.0, part, scl=(0, 0, 0))]
    return keys


def death():
    rnd = random.Random(61)
    keys = []
    # rising, arms spread, arching back
    keys += [k(0, "body", (0, 0, 0), (0, 0, 0)), k(1.0, "body", (0, 0, 0), (0, -4, 0)), k(5.4, "body", (0, 0, 0), (0, -6, 0)),
             k(8.0, "body", (0, 0, 0), (0, -6, 0))]
    keys += [k(0, "chest", (0, 0, 0)), k(1.0, "chest", (-14, 0, 0)), k(5.4, "chest", (-18, 0, 0))]
    keys += [k(0, "head", (0, 0, 0), (0, 0, 0)), k(1.0, "head", (-12, 0, 0), (0, -4, 0)), k(5.4, "head", (-14, 0, 0), (0, -6, 0)),
             k(6.3, "head", (-14, 0, 0), (0, -6, 0))]
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(1.0, f"arm_{side}", (0, 0, -78), s=s), k(4.0, f"arm_{side}", (-6, 0, -84), s=s)]
        keys += [k(0, f"forearm_{side}", (0, 0, 0), s=s), k(1.0, f"forearm_{side}", (12, 0, 0), s=s)]
        keys += [k(0, f"leg_{side}", (0, 0, 0), s=s), k(1.0, f"leg_{side}", (-6, 0, 3), s=s)]
    # the face cracking: its pieces jolting apart
    for p, ix, iy, iz in PIECES:
        d = ((1 if ix else -1), (1 if iy else -1), (1 if iz else -1))
        rot = (rnd.uniform(-10, 10), rnd.uniform(-10, 10), rnd.uniform(-10, 10))
        keys.append(k(0, p, (0, 0, 0), (0, 0, 0)))
        for t, amt in ((1.0, 0.0), (1.05, 0.25), (1.4, 0.25), (1.45, 0.5), (1.9, 0.5), (1.95, 0.8), (2.5, 0.9), (6.3, 1.0)):
            keys.append(k(t, p, tuple(r * amt for r in rot), tuple(x * amt for x in d), linear=True))
        # alone, they drop to the ground and burst one by one
        fall = 24 - P4_FACE_Y + 4 + rnd.uniform(-1, 1)
        t_land = 6.9 + rnd.random() * 0.2
        t_burst = 7.2 + rnd.random() * 0.7
        keys += [k(6.6, p, tuple(r * 1.5 for r in rot), (d[0] * 1.3, d[1] + fall * 0.25, d[2] * 1.3)),
                 k(t_land, p, tuple(r * 3 for r in rot), (d[0] * 2, fall, d[2] * 2), linear=True),
                 k(t_burst, p, tuple(r * 3 for r in rot), (d[0] * 2, fall, d[2] * 2), scl=(1, 1, 1), linear=True),
                 k(t_burst + 0.08, p, tuple(r * 3 for r in rot), (d[0] * 2, fall - 1, d[2] * 2), scl=(1.4, 1.4, 1.4), linear=True),
                 k(t_burst + 0.12, p, tuple(r * 3 for r in rot), (d[0] * 2, fall - 1, d[2] * 2), scl=(0, 0, 0), linear=True),
                 k(8.0, p, tuple(r * 3 for r in rot), (d[0] * 2, fall - 1, d[2] * 2), scl=(0, 0, 0))]
        keys.append(k(0, p, scl=(1, 1, 1)))
    # going out from the edges in
    for n, p in enumerate(REMNANTS):
        keys += shrink(p, 2.0 + n * 0.03, 0.2)
    for _, side in SIDES:
        keys += shrink(f"streak_{side}", 2.3, 0.35)
        for f in range(3):
            keys += shrink(f"finger_{side}{f}1", 2.6 + f * 0.05, 0.2) + shrink(f"finger_{side}{f}0", 2.8 + f * 0.05, 0.2)
        keys += shrink(f"hand_{side}", 3.0) + shrink(f"forearm_{side}", 3.3) + shrink(f"shin_{side}", 3.3)
        keys += shrink(f"arm_{side}", 3.8, 0.4) + shrink(f"leg_{side}", 3.8, 0.4)
    keys += shrink("chest", 4.3, 0.9) + shrink("body", 4.6, 0.8)
    M.anim("death", 8.0, False, keys)


# ---------------------------------------------------------------- the attacks (timed to boss/NamelessCreator.java)
def erase():
    """2.5 s: its right arm raised high (0-0.4) and drawn down across it - the white cut (0.5); held, let go."""
    keys = [k(0, "arm_r", (0, 0, 0), s=-1), k(0.4, "arm_r", (-170, 0, 30), s=-1), k(0.5, "arm_r", (-30, 0, -60), linear=True, s=-1),
            k(2.0, "arm_r", (-28, 0, -58), s=-1), k(2.5, "arm_r", (0, 0, 0), s=-1)]
    keys += [k(0, "chest", (0, 0, 0)), k(0.4, "chest", (-6, 20, 0)), k(0.5, "chest", (4, -25, 0), linear=True), k(2.5, "chest", (0, 0, 0))]
    for f in range(3):
        keys += [k(0, f"finger_r{f}0", (0, 0, 0), s=-1), k(0.4, f"finger_r{f}0", (-30, 0, 0), s=-1), k(0.5, f"finger_r{f}0", (10, 0, 0), s=-1),
                 k(2.5, f"finger_r{f}0", (0, 0, 0), s=-1)]
    M.anim("erase", 2.5, False, keys)


def checker():
    """8.5 s: arms held out low, palms down over the chequer; a jolt through it every second, as the squares move on."""
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(0.5, f"arm_{side}", (-30, 0, -40), s=s), k(8.0, f"arm_{side}", (-30, 0, -40), s=s),
                 k(8.5, f"arm_{side}", (0, 0, 0), s=s)]
        keys += [k(0, f"hand_{side}", (0, 0, 0), s=s), k(0.5, f"hand_{side}", (-60, 0, 0), s=s), k(8.0, f"hand_{side}", (-60, 0, 0), s=s),
                 k(8.5, f"hand_{side}", (0, 0, 0), s=s)]
    keys.append(k(0, "body", pos=(0, 0, 0)))
    for sec in range(1, 9):
        keys += [k(sec - 0.05, "body", pos=(0, 0, 0), linear=True), k(sec, "body", pos=(0.5 if sec % 2 else -0.5, -0.5, 0), linear=True),
                 k(sec + 0.1, "body", pos=(0, 0, 0), linear=True)]
    M.anim("checker", 8.5, False, keys)


def giga():
    """8.5 s: arms drawn back and spread, the chest arched back, gathering (0-3.0); thrust forward as it fires (3.0),
    held straining, trembling, as the beam turns (3.0-8.0); let go."""
    rnd = random.Random(71)
    keys = []
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(2.8, f"arm_{side}", (30, 0, -80), s=s), k(3.05, f"arm_{side}", (-70, 0, -50), linear=True, s=s)]
        t = 3.3
        while t < 8.0:
            keys.append(k(round(t, 2), f"arm_{side}", (-70 + rnd.uniform(-4, 4), 0, -50 + rnd.uniform(-4, 4)), s=s))
            t += 0.3
        keys += [k(8.0, f"arm_{side}", (-60, 0, -45), s=s), k(8.5, f"arm_{side}", (0, 0, 0), s=s)]
    keys += [k(0, "chest", (0, 0, 0)), k(2.8, "chest", (-22, 0, 0)), k(3.05, "chest", (12, 0, 0), linear=True), k(8.0, "chest", (10, 0, 0)),
             k(8.5, "chest", (0, 0, 0))]
    keys += [k(0, "body", pos=(0, 0, 0)), k(2.8, "body", pos=(0, -2, 1.5)), k(3.05, "body", pos=(0, -1, -1), linear=True),
             k(8.0, "body", pos=(0, -1, -1)), k(8.5, "body", pos=(0, 0, 0))]
    M.anim("giga", 8.5, False, keys)


def creation():
    """25.0 s: its face's cube unfolds like a flower - each piece swung out and laid open (0-2.0) - its arms raised over
    the world being made; it folds shut again at the end (23-25)."""
    keys = []
    for p, ix, iy, iz in PIECES:
        d = ((1 if ix else -1), (1 if iy else -1), (1 if iz else -1))
        out = (d[0] * 5, d[1] * 3 - 1, d[2] * 5)
        rot = (d[2] * 80, 0, -d[0] * 80)
        keys += [k(0, p, (0, 0, 0), (0, 0, 0)), k(2.0, p, rot, out), k(23.0, p, rot, out), k(25.0, p, (0, 0, 0), (0, 0, 0))]
    for s, side in SIDES:
        keys += [k(0, f"arm_{side}", (0, 0, 0), s=s), k(2.0, f"arm_{side}", (0, 0, -150), s=s), k(23.0, f"arm_{side}", (0, 0, -150), s=s),
                 k(25.0, f"arm_{side}", (0, 0, 0), s=s)]
    keys += [k(0, "body", pos=(0, 0, 0)), k(2.0, "body", pos=(0, -3, 0)), k(23.0, "body", pos=(0, -3, 0)), k(25.0, "body", pos=(0, 0, 0))]
    keys += [k(0, "head", pos=(0, 0, 0)), k(2.0, "head", pos=(0, -3, 0)), k(23.0, "head", pos=(0, -3, 0)), k(25.0, "head", pos=(0, 0, 0))]
    M.anim("creation", 25.0, False, keys)


# ---------------------------------------------------------------- painting
def paint(M, texs):
    tex, glow, wire = texs["creator_p4"], texs["creator_p4_glow"], texs["creator_p4_wire"]
    rnd = random.Random(67)
    for cube in M.cubes:
        meta = M.meta[cube[0]]
        kind = meta["kind"]
        for face, rect in face_rects(cube).items():
            def f(xx, yy, w, h, face=face):
                if kind == "piece":
                    ix, iy, iz = meta["ix"], meta["iy"], meta["iz"]
                    n = {"front": ix + iy, "back": ix + iy, "left": iy + iz, "right": iy + iz}.get(face, ix + iz)
                    return (MISSING, MISSING) if n % 2 == 0 else ((0, 0, 0), None)
                if kind == "outline":
                    return (OUTLINE, shade(OUTLINE, 0.7)) if border(xx, yy, w, h) or w <= 2 or h <= 2 else (None, None)
                if kind in ("white", "fade", "streak"):
                    fade = 0.0
                    if kind == "fade":
                        fade = (yy / max(1, h - 1)) * 0.35
                    elif kind == "streak":
                        fade = (yy / max(1, h - 1)) ** 1.2 * 0.9
                    if rnd.random() < fade:
                        return None, None
                    c = WHITE
                    if border(xx, yy, w, h) and w > 2 and h > 2:
                        r = rnd.random()
                        if r < 0.25:
                            return None, None  # breaking up into pixels
                        if r < 0.7:
                            c = FRINGE[0] if (xx == 0 or yy == 0) else FRINGE[1]
                    elif kind == "streak" and rnd.random() < 0.3:
                        c = FRINGE[rnd.randrange(3)]
                    return c, c
                return (255, 0, 255), None
            paint_face(tex, glow, rect, f, 0.0)

            def wf(xx, yy, w, h):  # the same part as a bare outline
                if kind == "piece":
                    return None, None
                return ((240, 240, 248) if border(xx, yy, w, h) or w <= 2 or h <= 2 else None), None
            paint_face(wire, None, rect, wf, 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    reveal()
    idle()
    speak()
    erase()
    checker()
    giga()
    creation()
    death()  # last: it ends with everything gone, which Blender would hold over the later ones
    build(M, 128, paint, ["creator_p4", "creator_p4_glow", "creator_p4_wire"], argv[0], argv[1], preview_z=24.0)


main()
