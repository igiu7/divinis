"""
Builds the Guild Master dwarf in Blender: Minecraft-style cubes, the 64x64 texture and two animations.

  blender -b --python tools/models/build_dwarf.py -- <out.blend> <texture.png>

then tools/models/export_model.py makes client/GuildMasterModelData.java from the .blend (see mcmodel.py).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, build_scene, faces, key, save  # noqa: E402

TEX_W, TEX_H = 64, 64


# ---------------------------------------------------------------- the model
# part: name, parent, pivot (relative to the parent's pivot, model space), rest rotation (degrees)
# cube: part, origin (relative to the pivot), size, texture offset
PARTS = [
    ("body", None, (0, 18, 0), (0, 0, 0)),
    ("head", "body", (0, -8, 0), (0, 0, 0)),
    ("beard", "head", (0, -3, -4), (0, 0, 0)),
    ("horn_left", "head", (4.5, -7, 0), (0, 0, 30)),
    ("horn_right", "head", (-4.5, -7, 0), (0, 0, -30)),
    ("arm_right", "body", (-6.5, -7, 0), (0, 0, 5)),
    ("arm_left", "body", (6.5, -7, 0), (0, 0, -5)),
    ("leg_right", None, (-2.5, 18, 0), (0, 0, 0)),
    ("leg_left", None, (2.5, 18, 0), (0, 0, 0)),
]

CUBES = [
    ("head", "head", (-4, -8, -4), (8, 8, 8), (0, 0)),
    ("helmet", "head", (-4.5, -9, -4.5), (9, 3, 9), (0, 16)),
    ("nose", "head", (-1, -5, -5), (2, 2, 1), (40, 16)),
    ("beard", "beard", (-4, 0, -1), (8, 7, 2), (32, 28)),
    ("horn_left", "horn_left", (0, -4, -1), (2, 4, 2), (48, 16)),
    ("horn_right", "horn_right", (-2, -4, -1), (2, 4, 2), (56, 16)),
    ("body", "body", (-5, -8, -3), (10, 8, 6), (0, 28)),
    ("arm_right", "arm_right", (-1.5, -1, -2), (3, 8, 4), (0, 42)),
    ("arm_left", "arm_left", (-1.5, -1, -2), (3, 8, 4), (14, 42)),
    ("leg_right", "leg_right", (-2, 0, -2.5), (4, 6, 5), (28, 42)),
    ("leg_left", "leg_left", (-2, 0, -2.5), (4, 6, 5), (46, 42)),
]


# ---------------------------------------------------------------- the texture
SKIN = (226, 170, 130)
SKIN_D = (196, 138, 102)
NOSE = (214, 136, 108)
BEARD = (190, 92, 40)
BEARD_D = (146, 64, 26)
BEARD_L = (216, 122, 62)
HELM = (140, 146, 156)
HELM_D = (92, 96, 106)
HELM_L = (182, 188, 196)
GOLD = (232, 186, 64)
GOLD_D = (172, 128, 36)
HORN = (232, 222, 196)
HORN_D = (184, 170, 144)
TUNIC = (46, 74, 140)
TUNIC_D = (30, 48, 98)
BELT = (92, 58, 30)
BELT_D = (62, 38, 20)
TROUSERS = (92, 78, 62)
BOOTS = (64, 42, 26)
BOOTS_L = (88, 60, 38)
SOLE = (38, 26, 16)
EYE_W = (238, 238, 232)
EYE = (44, 62, 112)
MOUTH = (70, 34, 22)


def paint(tex):
    cube = {c[0]: c for c in CUBES}

    def f(name):
        _, _, _, (w, h, d), (u, v) = cube[name]
        return faces(u, v, w, h, d)

    # head: skin face, ginger hair at the back, sideburns, brows, blue eyes
    hd = f("head")
    for k in hd:
        tex.fill(hd[k], SKIN)
    tex.fill(hd["top"], BEARD)
    tex.fill(hd["back"], BEARD)
    tex.fill(hd["bottom"], BEARD)
    for side, back_left in (("right", True), ("left", False)):
        r = hd[side]
        for row in range(8):
            for col in range(8):
                from_back = col if back_left else 7 - col
                if from_back < 5 or (row >= 4 and from_back >= 6):
                    tex.at(r, col, row, BEARD if from_back < 5 else BEARD_D)
        tex.at(r, 3 if back_left else 4, 4, SKIN_D)  # ear
    fr = hd["front"]
    for col in (1, 2, 5, 6):
        tex.at(fr, col, 2, BEARD_D)
    tex.at(fr, 1, 3, EYE_W)
    tex.at(fr, 2, 3, EYE)
    tex.at(fr, 5, 3, EYE)
    tex.at(fr, 6, 3, EYE_W)
    tex.at(fr, 1, 4, SKIN_D)
    tex.at(fr, 6, 4, SKIN_D)
    for row in (5, 6, 7):
        for col in range(8):
            tex.at(fr, col, row, BEARD)

    # helmet: steel, a gold band, rivets, a lighter top
    hm = f("helmet")
    for k in hm:
        tex.fill(hm[k], HELM)
    tex.fill(hm["top"], HELM_L)
    tex.fill(hm["bottom"], HELM_D)
    for k in ("front", "back", "left", "right"):
        r = hm[k]
        for col in range(int(r[2])):
            tex.at(r, col, 1, GOLD if col % 3 else GOLD_D)
            tex.at(r, col, 2, HELM_D)
        tex.at(r, int(r[2]) // 2, 0, HELM_L)
    tx, ty = hm["top"][0], hm["top"][1]
    tex.set(int(tx) + 4, int(ty) + 4, GOLD)

    # nose
    for r in f("nose").values():
        tex.fill(r, NOSE)

    # beard: strands, a moustache, an open mouth, a pointed tip
    bd = f("beard")
    for k in bd:
        tex.fill(bd[k], BEARD)
    for k in ("front", "back"):
        r = bd[k]
        for row in range(7):
            for col in range(8):
                tex.at(r, col, row, BEARD_D if (col + row // 2) % 3 == 0 else BEARD_L if col % 4 == 1 else BEARD)
        for col in (0, 1, 2, 5, 6, 7):
            tex.at(r, col, 0, BEARD_D)
        for (col, row) in ((0, 6), (1, 6), (6, 6), (7, 6), (0, 5), (7, 5)):
            tex.at(r, col, row, None)
    tex.at(bd["front"], 3, 1, MOUTH)
    tex.at(bd["front"], 4, 1, MOUTH)
    for k in ("left", "right"):
        r = bd[k]
        tex.at(r, 0, 5, None)
        tex.at(r, 1, 5, None)
        tex.at(r, 0, 6, None)
        tex.at(r, 1, 6, None)

    # horns: bone, darker tips
    for name in ("horn_left", "horn_right"):
        hr = f(name)
        for k in hr:
            tex.fill(hr[k], HORN)
        for k in ("front", "back", "left", "right"):
            for col in range(2):
                tex.at(hr[k], col, 0, HORN_D)
                tex.at(hr[k], col, 3, HORN_D if col else HORN)
        tex.fill(hr["top"], HORN_D)

    # body: blue guild tunic, gold collar, belt with a gold buckle
    bo = f("body")
    for k in bo:
        tex.fill(bo[k], TUNIC)
    for k in ("front", "back", "left", "right"):
        r = bo[k]
        w = int(r[2])
        for col in range(w):
            tex.at(r, col, 0, GOLD if k == "front" else TUNIC_D)
            tex.at(r, col, 5, BELT)
            tex.at(r, col, 6, BELT_D if col % 4 == 0 else BELT)
            tex.at(r, col, 7, TUNIC_D)
    r = bo["front"]
    for row in range(1, 5):
        tex.at(r, 4, row, GOLD_D)
        tex.at(r, 5, row, GOLD_D)
    for row in (5, 6):
        tex.at(r, 4, row, GOLD)
        tex.at(r, 5, row, GOLD)
    tex.at(r, 2, 6, (120, 80, 44))  # a pouch on the belt
    tex.at(r, 2, 7, (120, 80, 44))

    # arms: blue sleeves, gold cuffs, hands
    for name in ("arm_right", "arm_left"):
        ar = f(name)
        for k in ar:
            tex.fill(ar[k], TUNIC)
        tex.fill(ar["bottom"], SKIN)
        for k in ("front", "back", "left", "right"):
            r = ar[k]
            for col in range(int(r[2])):
                tex.at(r, col, 5, GOLD)
                tex.at(r, col, 6, SKIN)
                tex.at(r, col, 7, SKIN_D if col == 0 else SKIN)

    # legs: trousers and heavy boots
    for name in ("leg_right", "leg_left"):
        lg = f(name)
        for k in lg:
            tex.fill(lg[k], TROUSERS)
        tex.fill(lg["bottom"], SOLE)
        for k in ("front", "back", "left", "right"):
            r = lg[k]
            for col in range(int(r[2])):
                tex.at(r, col, 3, BOOTS_L)
                tex.at(r, col, 4, BOOTS)
                tex.at(r, col, 5, SOLE if k != "front" else BOOTS)


# ---------------------------------------------------------------- animations
def animate(scene, parts):
    # idle (frames 0-80, 4 s, looping): breathing, a slow look around, the beard sways
    s = 0
    for f_, body, hx, hy, arm, beard in (
        (0, 0, 0, 0, 0, 0),
        (20, 0.8, 1, -6, 1.5, -1.5),
        (40, 1.5, 2, 0, 3, -3),
        (60, 0.8, 1, 6, 1.5, -1.5),
        (80, 0, 0, 0, 0, 0),
    ):
        key(parts, s + f_, "body", rot=(body, 0, 0))
        key(parts, s + f_, "head", rot=(hx, hy, 0))
        key(parts, s + f_, "arm_right", rot=(-arm, 0, arm))
        key(parts, s + f_, "arm_left", rot=(-arm, 0, -arm))
        key(parts, s + f_, "beard", rot=(beard, 0, 0))

    # greet (frames 100-140, 2 s): turns a bit, raises the right hand and waves, nods and talks
    s = 100
    for f_, ax, az in ((0, 0, 0), (6, -150, -25), (11, -150, -42), (16, -150, -12), (21, -150, -42), (26, -150, -12),
                       (30, -150, -25), (40, 0, 0)):
        key(parts, s + f_, "arm_right", rot=(ax, 0, az))
    for f_, hx in ((0, 0), (8, 8), (16, -4), (24, 0), (40, 0)):
        key(parts, s + f_, "head", rot=(hx, 0, 0))
    for f_, bx in ((0, 0), (5, -8), (10, 0), (15, -8), (20, 0), (25, -6), (30, 0), (40, 0)):
        key(parts, s + f_, "beard", rot=(bx, 0, 0))
    for f_, by in ((0, 0), (8, -8), (30, -8), (40, 0)):
        key(parts, s + f_, "body", rot=(0, by, 0))
    for name in ("arm_left",):
        key(parts, s, name, rot=(0, 0, 0))
        key(parts, s + 40, name, rot=(0, 0, 0))

    scene["mc_anims"] = ["idle:0:80:loop", "greet:100:140:once"]
    scene.frame_start = 0
    scene.frame_end = 140
    scene.frame_set(0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(TEX_W, TEX_H)
    paint(tex)
    tex.save(tex_path)
    scene, parts = build_scene(PARTS, CUBES, tex_path, TEX_W, TEX_H, "guild_master")
    animate(scene, parts)
    save(blend_path)
    print("saved", blend_path, tex_path)


main()
