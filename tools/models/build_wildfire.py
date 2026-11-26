"""
Builds the Wildfire boss in Blender: a floating great helm (blackened steel, gold trim, a crest, swept-back wings,
eyes burning in the visor slit) with four ornate heater shields circling it - crimson enamel in a gold rim, a gold
cross, a glowing ember gem in the middle, three gold points on top, burning runes. Below the helm there is no
body, only fire (drawn by WildfireRenderer, not part of the model). 128x128 texture plus a glow texture.
Model drawn twice as big by WildfireRenderer: the helm about 1.3 blocks, the whole about 4 blocks across.

  blender -b --python tools/models/build_wildfire.py -- <out.blend> <texture.png> <glow.png>

then tools/models/export_model.py makes client/WildfireModelData.java from the .blend (see mcmodel.py).

Animations (they must match the timings in boss/Wildfire.java):
  sleep    4.0 s loop   a heap on the ground: the helm tipped over, the shields lying around and on it
  wake     3.5 s        the heap trembles, helm and shields rise, the shields swing up into their orbit, flare
  idle     4.0 s loop   floating, the shields circling once round
  fireball 1.4 s        rears back and hurls (release at 0.7 s)
  sweep    7.5 s        the shields fly far out (1.5 s) and stay out while the code wheels them round, come back
  inferno  2.0 s loop   rises, the shields spread wide and spin fast (the room-wide attack charging)
  stun     3.0 s loop   fallen to the ground, swaying, the shields gone
  regen    2.5 s        the shields come back from far below and spiral into their orbit as it rises again
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 128, 128
RADIUS = 13

PARTS = [
    ("float", None, (0, 8, 0), (0, 0, 0)),
    ("head", "float", (0, 0, 0), (0, 0, 0)),
    ("wing_left", "head", (5, -3, 1), (0, 25, -25)),
    ("wing_right", "head", (-5, -3, 1), (0, -25, 25)),
    ("ring", "float", (0, 0, 0), (0, 0, 0)),
]
for i in range(4):
    PARTS.append((f"arm{i}", "ring", (0, 0, 0), (0, 45 + i * 90, 0)))  # on the diagonals: the face stays free
    PARTS.append((f"shield{i}", f"arm{i}", (0, 0, -RADIUS), (0, 0, 0)))

HELM = [
    ("helm", "head", (-5, -6, -5), (10, 11, 10)),
    ("dome", "head", (-4, -7.5, -4), (8, 1.5, 8)),
    ("crest", "head", (-0.75, -10, -5), (1.5, 4, 11)),
    ("visor", "head", (-4.5, -3, -5.75), (9, 5, 1)),
    ("cheek_left", "head", (4.5, -1, -5.5), (1, 6, 6)),
    ("cheek_right", "head", (-5.5, -1, -5.5), (1, 6, 6)),
    ("neck", "head", (-5.5, 4, -4.5), (11, 2, 9.5)),
    ("wing_left", "wing_left", (0, -1, -0.5), (6, 2, 1)),
    ("wing_left_tip", "wing_left", (4, -4, -0.5), (2, 3, 1)),
    ("wing_right", "wing_right", (-6, -1, -0.5), (6, 2, 1)),
    ("wing_right_tip", "wing_right", (-6, -4, -0.5), (2, 3, 1)),
]
# one shield (shield0); the others share its texture
SHIELD = [
    ("plate_top", (-5, -7, -0.5), (10, 8, 1)),
    ("plate_mid", (-4, 1, -0.5), (8, 2, 1)),
    ("plate_low", (-3, 3, -0.5), (6, 2, 1)),
    ("plate_tip", (-1.5, 5, -0.5), (3, 2, 1)),
    ("rim_top", (-5.5, -7.5, -1), (11, 1, 1.5)),
    ("rim_left", (4.5, -7, -1), (1, 8.5, 1.5)),
    ("rim_right", (-5.5, -7, -1), (1, 8.5, 1.5)),
    ("cross_v", (-0.5, -6, -1.2), (1, 10, 0.7)),
    ("cross_h", (-4, -3, -1.2), (8, 1, 0.7)),
    ("gem", (-1.5, -4, -1.6), (3, 3, 1)),
    ("point_left", (-4, -9.5, -0.25), (1, 2, 0.5)),
    ("point_mid", (-0.5, -10.5, -0.25), (1, 3, 0.5)),
    ("point_right", (3, -9.5, -0.25), (1, 2, 0.5)),
]

CUBES = [[name, part, o, s, None] for name, part, o, s in HELM]
CUBES += [[f"s0_{name}", "shield0", o, s, None] for name, o, s in SHIELD]
SHARED = [[f"s{i}_{name}", f"shield{i}", o, s, f"s0_{name}"] for i in range(1, 4) for name, o, s in SHIELD]

# ---------------------------------------------------------------- the textures
STEEL = (58, 56, 62)
STEEL_D = (38, 36, 42)
STEEL_L = (92, 88, 96)
SOOT = (30, 26, 28)
GOLD = (222, 172, 58)
GOLD_D = (160, 110, 30)
GOLD_L = (255, 222, 120)
ENAMEL = (126, 22, 20)
ENAMEL_D = (86, 12, 12)
ENAMEL_L = (170, 40, 30)
EMBER = (255, 140, 30)
EMBER_L = (255, 230, 150)
EMBER_D = (180, 60, 10)


def paint(tex, glow, cube):
    rnd = random.Random(31)

    def f(name):
        _, _, _, (w, h, d), (u, v) = cube[name]
        return faces(u, v, w, h, d)

    def fill(name, base, dark, light, rate=0.12, noise=0.06):
        for k, r in f(name).items():
            x0, y0, w, h = (int(round(n)) for n in r)
            for yy in range(h):
                for xx in range(w):
                    n = rnd.random()
                    tex.set(x0 + xx, y0 + yy, dark if n < rate else light if n > 1 - rate / 2 else base, noise)

    # the helm: blackened steel, soot towards the bottom, gold trim
    for name in ("helm", "dome", "visor", "cheek_left", "cheek_right", "neck"):
        fill(name, STEEL, STEEL_D, STEEL_L)
    for k in ("front", "back", "left", "right"):
        x0, y0, w, h = (int(round(n)) for n in f("helm")[k])
        for xx in range(w):
            tex.set(x0 + xx, y0 + h - 1, SOOT, 0.05)
            tex.set(x0 + xx, y0 + h - 2, SOOT if rnd.random() < 0.6 else STEEL_D, 0.05)
            tex.set(x0 + xx, y0 + 1, GOLD, 0.05)  # a gold band round the top
    for name in ("crest", "wing_left", "wing_right", "wing_left_tip", "wing_right_tip"):
        fill(name, GOLD, GOLD_D, GOLD_L, 0.15, 0.05)
    for k in ("front", "back", "left", "right", "bottom"):
        x0, y0, w, h = (int(round(n)) for n in f("neck")[k])
        for xx in range(w):
            tex.set(x0 + xx, y0, GOLD_D, 0.05)
    # the visor: a burning slit for the eyes and breathing holes below
    x0, y0, w, h = (int(round(n)) for n in f("visor")["front"])
    for xx in range(1, w - 1):
        for t, c in ((tex, EMBER_D), (glow, EMBER)):
            t.set(x0 + xx, y0 + 1, c, 0.0)
    for (xx, c) in ((2, EMBER_L), (6, EMBER_L)):
        tex.set(x0 + xx, y0 + 1, EMBER, 0.0)
        glow.set(x0 + xx, y0 + 1, c, 0.0)
    for yy in (3, 4):
        for xx in range(2, w - 2, 2):
            tex.set(x0 + xx, y0 + yy, SOOT, 0.0)
            glow.set(x0 + xx, y0 + yy, EMBER_D, 0.0)
    # the helm's front around the visor: the same slit continues
    x0, y0, w, h = (int(round(n)) for n in f("helm")["front"])
    for xx in range(w):
        tex.set(x0 + xx, y0 + 4, SOOT, 0.0)

    # the shield: crimson enamel, a gold rim, a gold cross, burning runes, an ember gem
    for name in ("s0_plate_top", "s0_plate_mid", "s0_plate_low", "s0_plate_tip"):
        fill(name, ENAMEL, ENAMEL_D, ENAMEL_L, 0.1, 0.05)
        for k in ("front", "back"):
            x0, y0, w, h = (int(round(n)) for n in f(name)[k])
            for yy in range(h):  # gold edges down the sides of the lower plates
                tex.set(x0, y0 + yy, GOLD_D, 0.04)
                tex.set(x0 + w - 1, y0 + yy, GOLD_D, 0.04)
        for k in ("left", "right", "top", "bottom"):
            tex.fill(f(name)[k], GOLD_D, 0.05)
    x0, y0, w, h = (int(round(n)) for n in f("s0_plate_top")["front"])
    for (xx, yy) in ((1, 1), (2, 2), (1, 5), (2, 6), (8, 1), (7, 2), (8, 5), (7, 6)):  # runes in the corners
        tex.set(x0 + xx, y0 + yy, EMBER_D, 0.0)
        glow.set(x0 + xx, y0 + yy, EMBER, 0.0)
    for name in ("s0_rim_top", "s0_rim_left", "s0_rim_right", "s0_cross_v", "s0_cross_h", "s0_point_left", "s0_point_mid",
                 "s0_point_right"):
        fill(name, GOLD, GOLD_D, GOLD_L, 0.18, 0.05)
    for k, r in f("s0_gem").items():
        x0, y0, w, h = (int(round(n)) for n in r)
        for yy in range(h):
            for xx in range(w):
                c = EMBER_L if (xx, yy) == (1, 1) else EMBER
                tex.set(x0 + xx, y0 + yy, c, 0.03)
                glow.set(x0 + xx, y0 + yy, c, 0.0)
    for name in ("s0_point_left", "s0_point_mid", "s0_point_right"):
        tip = f(name)["top"]
        tex.fill(tip, GOLD_L, 0.0)


# ---------------------------------------------------------------- animations
def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


SHIELDS = [f"shield{i}" for i in range(4)]
CORE = ["float", "head", "ring", "wing_left", "wing_right"] + SHIELDS

# the heap: the helm tipped over half sunk, the shields lying flat around and one leaning on the helm
HEAP = {
    "float": ((0, 0, 0), (0, 14, 0)),
    "head": ((62, 35, 18), (0, 0, 0)),
    "ring": ((0, 20, 0), (0, 0, 0)),
    "wing_left": ((0, 0, 0), (0, 0, 0)),
    "wing_right": ((0, 0, 0), (0, 0, 0)),
    "shield0": ((85, 0, 8), (0, 1.5, 5)),
    "shield1": ((92, 15, -6), (0, 1.0, 4)),
    "shield2": ((55, 0, 10), (0, -1, 6)),
    "shield3": ((88, -10, 4), (0, 1.5, 3)),
}


def pose(t, table, jitter=0.0, rnd=None, linear=False):
    out = []
    for part, (rot, pos) in table.items():
        if jitter and rnd:
            rot = tuple(v + rnd.uniform(-jitter, jitter) for v in rot)
        out.append(k(t, part, rot, pos, linear))
    return out


def rest(t, parts, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


rnd = random.Random(3)
anim("sleep", 0, 4.0, True, pose(0, HEAP) + pose(4.0, HEAP))

wake = pose(0, HEAP)
for t in (0.4, 0.55, 0.7, 0.85, 1.0):
    wake += pose(t, HEAP, jitter=4.0, rnd=rnd)
half = {p: (tuple(v * 0.5 for v in r), (o[0], o[1] * 0.4 - 2, o[2] * 0.5)) for p, (r, o) in HEAP.items()}
wake += pose(1.8, half)
wake += [k(2.2, "float", (0, 0, 0), (0, -1, 0)), k(2.2, "head", (-10, 0, 0), (0, 0, 0)),
         k(2.2, "ring", (0, 120, 0), (0, 0, 0))]
wake += [k(2.2, s, (-30, 0, 0), (0, -2, 6)) for s in SHIELDS]
wake += [k(2.8, s, (0, 0, 0), (0, 0, -4)) for s in SHIELDS] + [k(2.8, "ring", (0, 300, 0), (0, 0, 0))]
wake += [k(3.1, "head", (-25, 0, 0), (0, 0, 0)), k(3.1, "float", (0, 0, 0), (0, -2, 0))]
wake += [k(3.1, s, (-15, 0, 0), (0, 0, -3)) for s in SHIELDS]
wake += rest(3.5, CORE) + [k(3.5, "ring", (0, 360, 0), (0, 0, 0))]
anim("wake", 100, 3.5, False, wake)

idle = [k(0, "ring", (0, 0, 0), linear=True), k(1, "ring", (0, 90, 0), linear=True), k(2, "ring", (0, 180, 0), linear=True),
        k(3, "ring", (0, 270, 0), linear=True), k(4, "ring", (0, 360, 0), linear=True),
        k(0, "float", None, (0, 0, 0)), k(1, "float", None, (0, -1, 0)), k(2, "float", None, (0, 0, 0)),
        k(3, "float", None, (0, -1, 0)), k(4, "float", None, (0, 0, 0)),
        k(0, "head", (0, 0, 0)), k(2, "head", (3, 6, 0)), k(4, "head", (0, 0, 0))]
for i, s in enumerate(SHIELDS):
    sign = 1 if i % 2 == 0 else -1
    idle += [k(0, s, (0, 0, 0), (0, 0, 0)), k(1, s, (6 * sign, 0, 0), (0, sign * -0.8, 0)), k(2, s, (0, 0, 0), (0, 0, 0)),
             k(3, s, (-6 * sign, 0, 0), (0, sign * 0.8, 0)), k(4, s, (0, 0, 0), (0, 0, 0))]
anim("idle", 200, 4.0, True, idle)

anim("fireball", 300, 1.4, False, rest(0, ["head", "float"] + SHIELDS) + [
    k(0.5, "head", (-25, 0, 0)), k(0.5, "float", (0, 0, 0), (0, -1, 2)),
    k(0.7, "head", (20, 0, 0), linear=True), k(0.7, "float", (0, 0, 0), (0, 0, -2), linear=True),
    k(1.0, "head", (12, 0, 0)), k(1.0, "float", (0, 0, 0), (0, 0, -1)),
] + [k(0.5, s, (0, 0, 0), (0, 0, -3)) for s in SHIELDS] + [k(0.7, s, (0, 0, 0), (0, 0, 0), True) for s in SHIELDS]
    + rest(1.4, ["head", "float"] + SHIELDS))

# the shields fly far out and stay there; turning them round is done in code (client/WildfireModel), to follow
# the turning fire beams of the attack exactly
sweep = rest(0, ["head", "float"] + SHIELDS)
sweep += [k(1.2, s, (-25, 0, 0), (0, 0, -20)) for s in SHIELDS] + [k(1.2, "head", (-20, 0, 0)), k(1.2, "float", (0, 0, 0), (0, -2, 0))]
sweep += [k(1.5, s, (-20, 0, 0), (0, 0, -18)) for s in SHIELDS]
sweep += [k(7.0, s, (-20, 0, 0), (0, 0, -18)) for s in SHIELDS] + [k(7.0, "head", (-20, 0, 0)), k(7.0, "float", (0, 0, 0), (0, -2, 0))]
sweep += rest(7.5, ["head", "float"] + SHIELDS)
anim("sweep", 400, 7.5, False, sweep)

inferno = [k(0, "float", (0, 0, 0), (0, -3, 0)), k(1, "float", (0, 0, 0), (0, -4, 0)), k(2, "float", (0, 0, 0), (0, -3, 0)),
           k(0, "head", (-25, 0, 0)), k(1, "head", (-30, 0, 0)), k(2, "head", (-25, 0, 0)),
           k(0, "ring", (0, 0, 0), linear=True), k(0.5, "ring", (0, 90, 0), linear=True), k(1, "ring", (0, 180, 0), linear=True),
           k(1.5, "ring", (0, 270, 0), linear=True), k(2, "ring", (0, 360, 0), linear=True)]
inferno += [k(t, s, (-35, 0, 0), (0, -1, -6)) for s in SHIELDS for t in (0, 2)]
anim("inferno", 600, 2.0, True, inferno)

anim("stun", 700, 3.0, True, [
    k(0, "float", (0, 0, 0), (0, 12, 0)), k(1.5, "float", (0, 0, 0), (0, 11.5, 0)), k(3, "float", (0, 0, 0), (0, 12, 0)),
    k(0, "head", (35, 0, 20)), k(1.5, "head", (40, 10, 10)), k(3, "head", (35, 0, 20)),
])

regen = [k(0, "float", (0, 0, 0), (0, 12, 0)), k(0, "head", (35, 0, 20)), k(0, "ring", (0, 0, 0))]
regen += [k(0, s, (90, 0, 0), (0, 14, -30)) for s in SHIELDS]
regen += [k(1.2, s, (40, 0, 0), (0, 6, -12)) for s in SHIELDS] + [k(1.2, "ring", (0, 270, 0)), k(1.2, "float", (0, 0, 0), (0, 5, 0)),
                                                                     k(1.2, "head", (10, 0, 5))]
regen += [k(2.0, s, (-10, 0, 0), (0, 0, -2)) for s in SHIELDS] + [k(2.0, "ring", (0, 480, 0)), k(2.0, "head", (-20, 0, 0)),
                                                                     k(2.0, "float", (0, 0, 0), (0, -1, 0))]
regen += rest(2.5, ["float", "head"] + SHIELDS) + [k(2.5, "ring", (0, 540, 0))]
anim("regen", 800, 2.5, False, regen)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path, glow_path = argv[0], argv[1], argv[2]
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows needed"
    by_name = {c[0]: c for c in CUBES}
    for c in SHARED:
        c[4] = by_name[c[4]][4]
    tex = Tex(TEX_W, TEX_H, seed=8)
    glow = Tex(TEX_W, TEX_H, seed=8)
    paint(tex, glow, by_name)
    tex.save(tex_path)
    glow.save(glow_path)
    scene, parts = build_scene(PARTS, CUBES + SHARED, tex_path, TEX_W, TEX_H, "wildfire")
    for name, start, end, loop, keys in ANIMS:
        animation(parts, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved", blend_path, tex_path, glow_path, "texture rows used:", used)


main()
