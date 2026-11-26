"""
Builds the Labyrinth Monster Crystal item in Blender: one long blue crystal, pointed at both ends, with a
diamond-shaped cross-section, as a Minecraft item model (JSON elements, 16x16 texture).

  blender -b --python tools/models/build_crystal.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/item/labyrinth_crystal.json from the .blend.

Scene layout: see itemmodel.py.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el as element  # noqa: E402
from mcmodel import Tex  # noqa: E402


# texture: columns 0-3 deep crystal, 4-7 bright crystal, 8-11 pale crystal, 12-15 glowing core
CRYSTAL_DEEP = [0, 0, 4, 16]
CRYSTAL = [4, 0, 8, 16]
CRYSTAL_PALE = [8, 0, 12, 16]
TIP = [8, 0, 12, 4]


def el(name, frm, to, axis=None, angle=0.0, origin=None, uv=CRYSTAL, top=None):
    return element(name, frm, to, axis, angle, origin, uv, {"up": top} if top else None)


# a single crystal turned 45 degrees (so it shows an edge to the front), tapering to a point at both ends
ELEMENTS = [
    el("body", [5.5, 4.5, 5.5], [10.5, 10, 10.5], "y", 45, [8, 8, 8], uv=CRYSTAL, top=TIP),
    el("upper", [6.25, 10, 6.25], [9.75, 12, 9.75], "y", 45, [8, 8, 8], uv=CRYSTAL, top=TIP),
    el("upper_2", [7, 12, 7], [9, 14, 9], "y", 45, [8, 8, 8], uv=CRYSTAL_PALE),
    el("tip", [7.6, 14, 7.6], [8.4, 15.5, 8.4], "y", 45, [8, 8, 8], uv=TIP),
    el("lower", [6.25, 2.5, 6.25], [9.75, 4.5, 9.75], "y", 45, [8, 8, 8], uv=CRYSTAL_DEEP),
    el("lower_2", [7, 1, 7], [9, 2.5, 9], "y", 45, [8, 8, 8], uv=CRYSTAL_DEEP),
    el("bottom", [7.6, 0, 7.6], [8.4, 1, 8.4], "y", 45, [8, 8, 8], uv=CRYSTAL_DEEP),
]


def paint(tex):
    rnd = random.Random(3)
    for y in range(16):
        for x in range(16):
            band = min(x // 4, 3)  # 0 deep, 1 bright, 2 pale, 3 core
            base = [(24, 70, 190), (46, 130, 240), (140, 210, 255), (200, 240, 255)][band]
            shade = 1.15 - y / 16 * 0.45  # lighter towards the top
            if x % 4 == 0:
                shade *= 0.8  # darker edge
            if x % 4 == 2 and band < 2:
                shade *= 1.25  # shine line
            tex.set(x, y, tuple(min(255, int(ch * shade)) for ch in base), 0.05)
    # sparkles
    for _ in range(6):
        tex.set(rnd.randrange(4, 12), rnd.randrange(0, 12), (230, 250, 255), 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(16, 16)
    paint(tex)
    tex.save(tex_path)

    build(ELEMENTS, tex_path, "divinis:item/labyrinth_crystal", blend_path)
    print("saved", blend_path, tex_path)


main()
