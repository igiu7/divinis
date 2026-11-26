"""
Builds Ignaroth's props in Blender, one model with three roots (client.IgnarothRenderer shows one of them):
  hoard    his hoard: heaps of gold coins in layers, cups, a crown, gems, a sword stuck in it (boss.DragonHoard)
  ballista a ballista on a turning post: a heavy frame, the stock with its rail, two bow arms, the string drawn back,
           the winch; a bolt lies on the rail when it is loaded (boss.Ballista)
  bolt     a ballista bolt: a long shaft, an iron head, three fins (boss.BallistaBolt; also the one on the rail)

Texture (256x256) ignaroth_props.png in the Minecraft style.

  blender -b --python tools/models/build_ignaroth_props.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/IgnarothPropsModelData.java.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 256, 256
PARTS, CUBES, MATERIAL = [], [], {}
_n = [0]


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def hoard():
    part("hoard", None, (0, 24, 0))
    for (w, h, d, y) in ((30, 2.5, 26, -2.5), (24, 2.5, 21, -5), (18, 2.5, 16, -7.5), (11, 2, 10, -9.5)):
        box("hoard", (-w / 2, y, -d / 2), (w, h, d), "coins")
    rnd = random.Random(5)
    for i in range(14):   # coins and gems scattered over its sides
        x, z = rnd.uniform(-13, 13), rnd.uniform(-11, 11)
        box("hoard", (x - 1, -3.2 - rnd.uniform(0, 2), z - 1), (2, 0.8, 2), "gem" if i % 4 == 0 else "coins")
    for (x, z) in ((-8, -5), (7, 4), (2, -7)):   # cups
        box("hoard", (x - 1.2, -9.5, z - 1.2), (2.4, 1, 2.4), "gold")
        box("hoard", (x - 0.4, -11, z - 0.4), (0.8, 1.6, 0.8), "gold")
        box("hoard", (x - 1.6, -13.4, z - 1.6), (3.2, 2.4, 3.2), "gold")
    box("hoard", (-2, -12.5, 1), (4, 1.4, 4), "gold")        # a crown on top
    for (x, z) in ((-2, 1), (1.4, 1), (-2, 4.4), (1.4, 4.4)):
        box("hoard", (x, -14, z), (0.6, 1.5, 0.6), "gold")
    part("hoard_sword", "hoard", (6, -8, -2), (0, 30, 20))
    box("hoard_sword", (-0.5, -12, -0.2), (1, 10, 0.4), "steel")
    box("hoard_sword", (-2, -2.4, -0.5), (4, 0.8, 1), "gold")
    box("hoard_sword", (-0.4, -1.6, -0.4), (0.8, 3, 0.8), "wood")


def ballista():
    part("ballista", None, (0, 24, 0))
    box("ballista", (-6, -2, -6), (12, 2, 12), "wood")                 # the base
    box("ballista", (-1.5, -9, -1.5), (3, 7, 3), "wood")               # the post
    part("ballista_top", "ballista", (0, -10, 0))                       # it turns
    box("ballista_top", (-1.5, -1.5, -11), (3, 2.5, 20), "wood")       # the stock
    box("ballista_top", (-0.5, -2, -11), (1, 0.6, 20), "iron")         # the rail
    box("ballista_top", (-3.5, -2.5, -9), (7, 3.5, 3), "wood")         # the bow's housing
    for sx in (-1, 1):
        arm = "ballista_arm_l" if sx > 0 else "ballista_arm_r"
        part(arm, "ballista_top", (sx * 3.5, -1.0, -7.5), (0, sx * -25, 0))
        box(arm, (0 if sx > 0 else -9, -0.8, -0.8), (9, 1.6, 1.6), "wood")
        box(arm, ((8 if sx > 0 else -9), -1.0, -1.0), (1, 2, 2), "iron")
    box("ballista_top", (-7.6, -0.6, 2.0), (15.2, 0.4, 0.4), "string")  # the string, drawn back
    box("ballista_top", (-3, -1, 6), (6, 2.5, 2.5), "iron")             # the winch
    box("ballista_top", (-4, -0.5, 6.5), (8, 1.5, 1.5), "wood")
    part("ballista_bolt", "ballista_top", (0, -2.6, -1))                # the bolt on the rail (when loaded)
    bolt_boxes("ballista_bolt")


def bolt_boxes(prt):
    box(prt, (-0.4, -0.4, -10), (0.8, 0.8, 17), "wood")
    box(prt, (-0.8, -0.8, -13), (1.6, 1.6, 3), "iron")
    box(prt, (-0.4, -0.4, -14.5), (0.8, 0.8, 1.5), "iron")
    box(prt, (-0.1, -1.8, 4.5), (0.2, 1.4, 2.5), "fin")
    box(prt, (-1.6, -0.1, 4.5), (1.4, 0.2, 2.5), "fin")
    box(prt, (0.2, -0.1, 4.5), (1.4, 0.2, 2.5), "fin")


def bolt():
    part("bolt", None, (0, 24, 0))
    part("bolt_body", "bolt", (0, -1, 0))
    bolt_boxes("bolt_body")


COINS = [(150, 104, 30), (196, 150, 46), (232, 192, 72), (252, 226, 120)]
WOOD = [(70, 48, 28), (96, 68, 40), (122, 90, 56)]
IRON = [(60, 62, 68), (96, 98, 106), (140, 142, 150)]
FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.25, "bottom": -1.0}


def pick(r, level):
    return r[max(0, min(len(r) - 1, int(round(level))))]


def paint(tex, rnd):
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            light = FACE_LIGHT[face]
            for y in range(fh):
                for x in range(fw):
                    n = rnd.uniform(-0.35, 0.35)
                    if mat == "coins":
                        c = pick(COINS, 1.6 + light + n + (1.2 if (x * 3 + y * 5) % 7 == 0 else 0) - (0.8 if (x + y * 2) % 5 == 0 else 0))
                        if (x * 7 + y * 3) % 23 == 0:
                            c = (200, 40, 50) if (x + y) % 2 else (40, 160, 210)   # a gem among the coins
                    elif mat == "gold":
                        c = pick(COINS, 2 + light + n)
                    elif mat == "gem":
                        c = (220, 50, 60) if (x + y) % 2 else (255, 120, 120)
                    elif mat == "steel":
                        c = pick(IRON, 2 + light + n)
                    elif mat == "wood":
                        c = pick(WOOD, 1.2 + light * 0.6 + n - (0.8 if (y + x * 2) % 6 == 0 else 0))
                    elif mat == "iron":
                        c = pick(IRON, 1.2 + light + n + (0.8 if y == 0 else 0))
                    elif mat == "string":
                        c = (210, 200, 176)
                    else:
                        c = (170, 40, 30) if mat == "fin" else pick(WOOD, 1)
                    tex.set(x0 + x, y0 + y, c, 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    hoard()
    ballista()
    bolt()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex = Tex(TEX_W, TEX_H, seed=3)
    paint(tex, random.Random(8))
    tex.save(os.path.join(tex_dir, "ignaroth_props.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "ignaroth_props.png"), TEX_W, TEX_H, "ignaroth_props")
    scene["mc_anims"] = []
    save(blend_path)
    print("saved ignaroth props, rows", used)


main()
