"""
Builds Aurelion's second phase in Blender - the same body (build_aurelion.py) after the veil is torn: its eyes swollen
and bulging out of it, bloodshot, their irises gone red, blood running down from under every one of them (drops
sliding down and falling, over and over); its wings soaked with blood from the tips up; the golden symbol gone
(shattered into the shards that fly round it, build_aurelion_shard.py). 256x256 texture and glow texture.

  blender -b --python tools/models/build_aurelion_p2.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/AurelionP2ModelData.java.

Animations: idle 2.0 s loop (wings tremble, eyes twitch), bleed 1.5 s loop (the blood running down),
            scream 3.0 s (wings thrown wide and shaking, every eye stretched out), cast 1.5 s (wings raised high),
            wrap 1.5 s (wings fold round it), reach 1.0 s (wings swept back, eyes forward), hold 2.0 s loop (wings
            high and shaking - holding something great over it)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_aurelion as base  # noqa: E402
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

k = base.k
BLOOD, BLOOD_D, BLOOD_L = (128, 10, 14), (70, 4, 8), (176, 26, 26)

# ---- the body, without its symbol; eyes swollen and pushed out
PARTS, CUBES, KIND = [], [], {}
symbol_parts = {"symbol"}
for name, parent, pivot, rot in base.PARTS:
    if parent in symbol_parts or name in symbol_parts:
        symbol_parts.add(name)
        continue
    PARTS.append((name, parent, pivot, rot))
EYES = []
for name, pos, size, rot in base.EYES:
    big = size * 1.35
    EYES.append((name, pos, big, rot))
for c in base.CUBES:
    name, part = c[0], c[1]
    if part in symbol_parts:
        continue
    if base.KIND[name] == "eye":
        big = c[3][0] * 1.35
        CUBES.append([name, part, (-big / 2, -big / 2, -big / 2 - big * 0.3), (big, big, big), None])
    else:
        CUBES.append([name, part, c[2], c[3], None])
    KIND[name] = base.KIND[name]
# blood running down under each eye: a drip that slides down (its part moves, see bleed)
DRIPS = []
for name, pos, size, rot in EYES:
    d = f"{name}_drip"
    PARTS.append((d, name, (size * 0.12, size / 2, -size * 0.55), (0, 0, 0)))
    length = 2 + size * 0.35
    CUBES.append([d, d, (-0.5, 0, -0.5), (1, length, 1), None])
    CUBES.append([d + "_drop", d, (-0.75, length, -0.75), (1.5, 1.5, 1.5), None])
    KIND[d] = KIND[d + "_drop"] = "blood"
    DRIPS.append((d, size))

# ---------------------------------------------------------------- animations
ANIMS = []
idle = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        for n in range(9):
            t = n * 0.25
            idle.append(k(t, arm, (0, 0, -s * (math.sin(t * math.pi * 2 + i) * 6 + (3 if n % 2 else -3)))))
for n, (name, _, _, _) in enumerate(EYES):
    rnd = random.Random(n * 13 + 5)
    for m in range(9):
        t = m * 0.25
        idle.append(k(t, name, (0, 0, 0) if m in (0, 8) else (rnd.uniform(-20, 20), rnd.uniform(-24, 24), 0)))
ANIMS.append(("idle", 0, 2.0, True, idle))

bleed = []
for n, (d, size) in enumerate(DRIPS):
    off = (n * 0.37) % 1.5
    fall = 4 + size * 0.5
    # slides down, falls away, starts again (linear, so the jump back is instant)
    for t, y in ((0, 0), (off, 0), (off + 0.001, 0), (min(1.5, off + 1.0), fall)):
        bleed.append(k(t, d, None, (0, y, 0), linear=True))
    bleed.append(k(1.5, d, None, (0, fall if off + 1.0 > 1.5 else 0, 0), linear=True))
ANIMS.append(("bleed", 50, 1.5, True, bleed))

scream = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        scream.append(k(0, arm, (0, 0, 0)))
        for n in range(1, 30):
            t = n * 0.1
            shake = (5 if n % 2 else -5)
            scream.append(k(t, arm, (shake, -s * 28, -s * (30 + shake))))
        scream.append(k(3.0, arm, (0, 0, 0)))
for n, (name, _, _, rot) in enumerate(EYES):
    scream += [k(0, name, (0, 0, 0)), k(0.3, name, (-rot[0] * 1.6, -rot[1] * 1.6, 0))]
    for m in range(4, 28, 3):
        scream.append(k(m * 0.1, name, (-rot[0] * 1.6 + (6 if m % 2 else -6), -rot[1] * 1.6, 0)))
    scream.append(k(3.0, name, (0, 0, 0)))
ANIMS.append(("scream", 100, 3.0, False, scream))

cast = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        cast += [k(0, arm, (0, 0, 0)), k(0.35, arm, (0, s * 14, -s * 34)), k(1.2, arm, (0, s * 14, -s * 34)), k(1.5, arm, (0, 0, 0))]
for name, _, _, _ in EYES:
    cast += [k(0, name, (0, 0, 0)), k(0.3, name, (-40, 0, 0)), k(1.2, name, (-40, 0, 0)), k(1.5, name, (0, 0, 0))]
ANIMS.append(("cast", 170, 1.5, False, cast))

wrap = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        wrap += [k(0, arm, (0, 0, 0)), k(0.4, arm, (0, s * 70, s * 8)), k(1.1, arm, (0, s * 70, s * 8)), k(1.5, arm, (0, 0, 0))]
ANIMS.append(("wrap", 210, 1.5, False, wrap))

reach = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        reach += [k(0, arm, (0, 0, 0)), k(0.25, arm, (0, -s * 30, 0)), k(0.8, arm, (0, -s * 30, 0)), k(1.0, arm, (0, 0, 0))]
for name, _, _, rot in EYES:
    reach += [k(0, name, (0, 0, 0)), k(0.2, name, (-rot[0], -rot[1], 0)), k(0.8, name, (-rot[0], -rot[1], 0)), k(1.0, name, (0, 0, 0))]
ANIMS.append(("reach", 250, 1.0, False, reach))

hold = []
for side, s in (("left", 1), ("right", -1)):
    for i in range(3):
        arm = f"wing_{side}{i}"
        for n in range(9):
            t = n * 0.25
            shake = 3 if n % 2 else -3
            hold.append(k(t, arm, (shake, s * 10, -s * (40 + shake))))
ANIMS.append(("hold", 280, 2.0, True, hold))


# ---------------------------------------------------------------- painting: the same, soaked in blood
def bloody(tex, glow):
    base.SCLERA, base.SCLERA_D = (222, 196, 184), (196, 140, 130)
    base.IRIS, base.IRIS_D, base.IRIS_L = (196, 30, 24), (120, 10, 12), (240, 90, 60)
    base.GLOW_IRIS, base.GLOW_CORE = (255, 70, 40), (255, 190, 150)
    base.VEIN = (176, 24, 24)
    base.GOLD, base.GOLD_D, base.GOLD_L = (168, 112, 36), (104, 60, 18), (206, 156, 70)
    saved = base.CUBES
    base.CUBES = [c for c in CUBES if KIND[c[0]] != "blood"]
    base.KIND.update(KIND)
    base.paint(tex, glow)
    base.CUBES = saved
    rnd = random.Random(31)
    for name, _, _, (w, h, d), (u, v) in CUBES:
        kind = KIND[name]
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            fw, fh = max(1, fw), max(1, fh)
            for yy in range(fh):
                for xx in range(fw):
                    n = rnd.random()
                    if kind == "blood":
                        tex.set(x0 + xx, y0 + yy, BLOOD_L if yy == 0 and n < 0.5 else BLOOD if n < 0.7 else BLOOD_D, 0.03)
                    elif kind in ("feather", "tip", "covert"):
                        # soaked from the tips up, in streaks
                        k_ = yy / max(1, fh - 1)
                        streak = (xx * 7 + hash(name) % 5) % 3 == 0
                        if kind == "tip" or k_ > 0.55 + (0.2 if streak else 0) * rnd.random() or n < 0.04:
                            tex.set(x0 + xx, y0 + yy, BLOOD if n < 0.7 else BLOOD_D, 0.04)
                    elif kind == "eye" and side != "front":
                        # blood welling round the swollen eyes
                        if yy >= fh - 2 or n < 0.12:
                            tex.set(x0 + xx, y0 + yy, BLOOD if n < 0.6 else BLOOD_D, 0.04)
                    elif kind == "arm" and n < 0.3:
                        tex.set(x0 + xx, y0 + yy, BLOOD_D, 0.04)
                    elif kind == "eye" and side == "front" and yy >= fh - 1 and n < 0.6:
                        tex.set(x0 + xx, y0 + yy, BLOOD, 0.04)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    tex_path, glow_path = os.path.join(textures, "aurelion_p2.png"), os.path.join(textures, "aurelion_p2_glow.png")
    used = pack(CUBES, base.TEX)
    assert used <= base.TEX, f"texture too small: {used}"
    tex, glow = Tex(base.TEX, base.TEX, seed=3), Tex(base.TEX, base.TEX, seed=3)
    bloody(tex, glow)
    tex.save(tex_path)
    glow.save(glow_path)
    scene, objs = build_scene(PARTS, CUBES, tex_path, base.TEX, base.TEX, "aurelion_p2.blend")
    for name, start, seconds, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in ANIMS]
    scene["mc_preview_z"] = 24.0
    scene.frame_start = 0
    scene.frame_end = 330
    scene.frame_set(0)
    save(os.path.join(blends, "aurelion_p2.blend"))
    print("saved rows", used, "parts", len(PARTS))


main()
