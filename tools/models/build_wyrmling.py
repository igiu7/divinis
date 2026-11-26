"""
Builds the Void Wyrmling in Blender: a small flying worm the Serpent of Infinity calls up in its second phase - an
armoured head with two pincers and glowing eyes, three body rings and a tail fin, bone-white plates with purple
accents like the serpent's second phase. Some two blocks long, it is drawn at model size (client/WyrmlingRenderer).

  blender -b --python tools/models/build_wyrmling.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/WyrmlingModelData.java.

Animations: fly 1.0 s loop (the body ripples from head to tail, the pincers snap),
            swoop 0.75 s (pincers spread wide)
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX = 64
PLATE, PLATE_D, PLATE_L = (214, 212, 224), (166, 162, 184), (246, 244, 252)
PURPLE, PURPLE_D, PURPLE_L = (74, 24, 112), (46, 14, 72), (118, 52, 170)
GLOW, GLOW_D = (176, 96, 255), (96, 40, 160)
MOUTH = (40, 8, 30)

# the head's pivot is in the middle of the head (model y 20); each ring hangs off the one before
PARTS = [
    ("head", None, (0, 20, 0), (0, 0, 0)),
    ("pincer_left", "head", (2.5, 1, -3), (0, -15, 0)),
    ("pincer_right", "head", (-2.5, 1, -3), (0, 15, 0)),
    ("ring1", "head", (0, 0, 3), (0, 0, 0)),
    ("ring2", "ring1", (0, 0, 5), (0, 0, 0)),
    ("ring3", "ring2", (0, 0, 5), (0, 0, 0)),
    ("tail", "ring3", (0, 0, 4.5), (0, 0, 0)),
]
CUBES = [[*c, None] for c in [
    ("skull", "head", (-3, -2.5, -3.5), (6, 5, 7)),
    ("crest", "head", (-0.5, -4, -2), (1, 2, 5)),
    ("eye_left", "head", (3, -1.5, -3), (0.5, 1, 2)),
    ("eye_right", "head", (-3.5, -1.5, -3), (0.5, 1, 2)),
    ("pincer_left", "pincer_left", (-0.75, -0.75, -4), (1.5, 1.5, 4)),
    ("pincer_left_tip", "pincer_left", (-1.75, -0.5, -5.5), (1.5, 1, 2)),
    ("pincer_right", "pincer_right", (-0.75, -0.75, -4), (1.5, 1.5, 4)),
    ("pincer_right_tip", "pincer_right", (0.25, -0.5, -5.5), (1.5, 1, 2)),
    ("ring1", "ring1", (-2.5, -2, 0), (5, 4, 5)),
    ("ring1_fin", "ring1", (-0.25, -3.5, 0.5), (0.5, 1.5, 4)),
    ("ring2", "ring2", (-2, -1.75, 0), (4, 3.5, 4.5)),
    ("ring2_fin", "ring2", (-0.25, -3, 0.5), (0.5, 1.25, 3.5)),
    ("ring3", "ring3", (-1.5, -1.5, 0), (3, 3, 4.5)),
    ("tail", "tail", (-1, -1, 0), (2, 2, 3)),
    ("tail_fin", "tail", (-0.25, -2.5, 1), (0.5, 5, 4)),
]]
ACCENT = ("crest", "pincer", "ring1_fin", "ring2_fin", "tail_fin")


def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


fly = []
for i, part in enumerate(("ring1", "ring2", "ring3", "tail")):
    amp = 10 + i * 4
    shift = i * 0.15
    for t in (0, 0.25, 0.5, 0.75, 1.0):
        phase = (t - shift) % 1.0
        val = amp if phase < 0.25 else 0 if phase < 0.5 else -amp if phase < 0.75 else 0
        fly.append(k(t, part, (0, val, 0)))
for t, ang in ((0, 0), (0.5, 22), (0.6, 0), (1.0, 0)):
    fly.append(k(t, "pincer_left", (0, -ang, 0)))
    fly.append(k(t, "pincer_right", (0, ang, 0)))
swoop = [k(0, "pincer_left", (0, 0, 0)), k(0.2, "pincer_left", (0, -40, 0)), k(0.75, "pincer_left", (0, -40, 0)),
         k(0, "pincer_right", (0, 0, 0)), k(0.2, "pincer_right", (0, 40, 0)), k(0.75, "pincer_right", (0, 40, 0))]
for part in ("ring1", "ring2", "ring3", "tail"):
    swoop += [k(0, part, (0, 0, 0)), k(0.75, part, (0, 0, 0))]
ANIMS = [("fly", 0, 1.0, True, fly), ("swoop", 40, 0.75, False, swoop)]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    tex_path, glow_path = os.path.join(textures, "void_wyrmling.png"), os.path.join(textures, "void_wyrmling_glow.png")
    used = pack(CUBES, TEX)
    assert used <= TEX, f"texture too small: {used}"
    tex, glow = Tex(TEX, TEX, seed=9), Tex(TEX, TEX, seed=9)
    rnd = random.Random(5)
    for name, _, _, (w, h, d), (u, v) in CUBES:
        accent = name.startswith(ACCENT)
        eye = name.startswith("eye")
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            for yy in range(max(1, fh)):
                for xx in range(max(1, fw)):
                    n = rnd.random()
                    if eye:
                        c = GLOW
                        glow.set(x0 + xx, y0 + yy, GLOW, 0.0)
                    elif accent:
                        c = PURPLE_L if yy == 0 else PURPLE_D if n < 0.15 else PURPLE
                        if yy == 0:
                            glow.set(x0 + xx, y0 + yy, GLOW_D, 0.0)
                    else:
                        c = PLATE_L if yy == 0 and side != "bottom" else PLATE_D if yy == fh - 1 or n < 0.06 else PLATE
                        if yy == fh - 1 and side in ("left", "right", "front", "back") and name.startswith("ring"):
                            c = PURPLE
                            glow.set(x0 + xx, y0 + yy, GLOW_D, 0.0)
                    tex.set(x0 + xx, y0 + yy, c, 0.03)
    # a dark mouth across the bottom of the skull's front
    skull = CUBES[0]
    x0, y0, fw, fh = (int(round(n)) for n in faces(*skull[4], *skull[3])["front"])
    for xx in range(1, fw - 1):
        tex.set(x0 + xx, y0 + fh - 2, MOUTH, 0.0)
    tex.save(tex_path)
    glow.save(glow_path)
    scene, objs = build_scene(PARTS, CUBES, tex_path, TEX, TEX, "void_wyrmling.blend")
    for name, start, seconds, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = 60
    scene.frame_set(0)
    save(os.path.join(blends, "void_wyrmling.blend"))
    print("saved rows", used)


main()
