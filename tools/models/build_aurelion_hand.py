"""
Builds one of Aurelion's two floating hands in Blender: a dead human hand - pale grey skin with blue-grey veins,
darker joints and a few livid patches, long fingers with black, pointed nails, a thumb, and the wrist torn off.
Palm down, fingers to the front. The left hand is the same model mirrored (client/AurelionRenderer). 64x64.

  blender -b --python tools/models/build_aurelion_hand.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/AurelionHandModelData.java.

Animations: close 1.0 s (open to a fist; the renderer plays it to any point, as tight as the hand is gripping),
            twitch 3.0 s loop (the fingers twitch now and then)
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX = 64
SKIN, SKIN_D, SKIN_L = (190, 186, 178), (148, 144, 138), (210, 204, 194)
VEIN, LIVID, JOINT = (112, 122, 142), (150, 156, 132), (132, 112, 110)
NAIL, NAIL_L = (16, 14, 18), (64, 60, 70)
WOUND, WOUND_D = (92, 22, 24), (30, 8, 10)

FINGERS = [(-3.0, 5.8, 4.6), (-1.0, 7.0, 5.4), (1.0, 6.6, 5.1), (3.0, 5.2, 4.2)]  # x, first and second joint lengths

PARTS = [("hand", None, (0, 0, 0), (0, 0, 0))]
CUBES = [["palm", "hand", (-4, -1.5, -4), (8, 3, 8), None],
         ["wrist", "hand", (-3, -1.4, 4), (6, 2.8, 4), None],
         ["knuckles", "hand", (-4, -1.9, -4.2), (8, 0.6, 1.4), None]]
KIND = {"palm": "skin", "wrist": "wrist", "knuckles": "joint"}
for i, (x, l1, l2) in enumerate(FINGERS):
    a, b = f"finger{i}", f"finger{i}_tip"
    PARTS.append((a, "hand", (x, 0, -4), (0, (x / 3.0) * -4, 0)))
    PARTS.append((b, a, (0, 0, -l1), (0, 0, 0)))
    CUBES.append([a, a, (-0.9, -0.9, -l1), (1.8, 1.8, l1), None])
    CUBES.append([b, b, (-0.8, -0.8, -l2), (1.6, 1.6, l2), None])
    CUBES.append([b + "_nail", b, (-0.7, -1.05, -l2 - 2.2), (1.4, 0.6, 3.0), None])
    CUBES.append([b + "_point", b, (-0.35, -0.95, -l2 - 3.8), (0.7, 0.4, 1.6), None])
    KIND[a] = KIND[b] = "skin"
    KIND[b + "_nail"] = KIND[b + "_point"] = "nail"
PARTS.append(("thumb", "hand", (-4, 0.4, -1.2), (0, 38, 0)))
PARTS.append(("thumb_tip", "thumb", (0, 0, -3.6), (0, -10, 0)))
CUBES.append(["thumb", "thumb", (-1, -1, -3.6), (2, 2, 3.6), None])
CUBES.append(["thumb_tip", "thumb_tip", (-0.85, -0.85, -3), (1.7, 1.7, 3), None])
CUBES.append(["thumb_tip_nail", "thumb_tip", (-0.75, -1.1, -5), (1.5, 0.6, 2.8), None])
CUBES.append(["thumb_tip_point", "thumb_tip", (-0.35, -1.0, -6.4), (0.7, 0.4, 1.5), None])
KIND.update({"thumb": "skin", "thumb_tip": "skin", "thumb_tip_nail": "nail", "thumb_tip_point": "nail"})


def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


close = []
for i in range(4):
    close += [k(0, f"finger{i}", (0, 0, 0), linear=True), k(1.0, f"finger{i}", (72, 0, 0), linear=True),
              k(0, f"finger{i}_tip", (0, 0, 0), linear=True), k(1.0, f"finger{i}_tip", (85, 0, 0), linear=True)]
close += [k(0, "thumb", (0, 0, 0), linear=True), k(1.0, "thumb", (35, -30, 0), linear=True),
          k(0, "thumb_tip", (0, 0, 0), linear=True), k(1.0, "thumb_tip", (40, 0, 0), linear=True)]
twitch = []
rnd = random.Random(4)
for i in range(4):
    t0 = rnd.uniform(0.3, 2.4)
    twitch += [k(0, f"finger{i}_tip", (0, 0, 0)), k(t0, f"finger{i}_tip", (0, 0, 0)), k(t0 + 0.1, f"finger{i}_tip", (25, 0, 0)),
               k(t0 + 0.35, f"finger{i}_tip", (0, 0, 0)), k(3.0, f"finger{i}_tip", (0, 0, 0))]
ANIMS = [("close", 0, 1.0, False, close), ("twitch", 40, 3.0, True, twitch)]


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    tex_path = os.path.join(textures, "aurelion_hand.png")
    used = pack(CUBES, TEX)
    assert used <= TEX, f"texture too small: {used}"
    tex = Tex(TEX, TEX, seed=6)
    r = random.Random(8)
    for name, _, _, (w, h, d), (u, v) in CUBES:
        kind = KIND[name]
        for side, rect in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in rect)
            for yy in range(max(1, fh)):
                for xx in range(max(1, fw)):
                    n = r.random()
                    if kind == "nail":
                        c = NAIL_L if yy == 0 and n < 0.5 else NAIL
                    elif kind == "wrist" and side == "back":
                        c = WOUND_D if n < 0.45 else WOUND  # torn off
                    elif kind == "joint":
                        c = JOINT if n < 0.5 else SKIN_D
                    else:
                        c = SKIN_L if yy == 0 and side != "bottom" else SKIN
                        if n < 0.10:
                            c = SKIN_D
                        elif n < 0.14:
                            c = LIVID
                        if side == "top" and (xx + yy * 2) % 7 == 0 and n < 0.6:
                            c = VEIN  # veins over the back of the hand
                        if kind == "wrist" and yy >= fh - 2 and side != "top":
                            c = WOUND if n < 0.6 else JOINT
                    tex.set(x0 + xx, y0 + yy, c, 0.03)
    tex.save(tex_path)
    scene, objs = build_scene(PARTS, CUBES, tex_path, TEX, TEX, "aurelion_hand.blend")
    for name, start, seconds, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in ANIMS]
    scene["mc_preview_z"] = 24.0
    scene.frame_start = 0
    scene.frame_end = 100
    scene.frame_set(0)
    save(os.path.join(blends, "aurelion_hand.blend"))
    print("saved rows", used)


main()
