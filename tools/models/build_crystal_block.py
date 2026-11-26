"""
Builds the Deepslate Crystal block in Blender: a low bed of deepslate with a cluster of dark blue crystals (the
Golem's crystals) growing out of it - a tall one in the middle and smaller ones leaning out around it. A block model
(JSON elements, one 32x32 texture); the crystals glow faintly (NeoForge's emissive elements).

  blender -b --python tools/models/build_crystal_block.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/block/deepslate_crystal.json from the .blend. Scene layout: see
itemmodel.py.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

# the Golem's palette (build_golem.py)
STONE = (76, 76, 84)
STONE_D = (56, 56, 63)
STONE_L = (100, 100, 108)
CRACK = (36, 36, 42)
CRYSTAL = (22, 36, 104)
CRYSTAL_L = (46, 70, 160)
CRYSTAL_D = (12, 18, 60)
CRYSTAL_TIP = (70, 100, 200)

# texture regions in model UV units (0..16 over the 32 pixels)
SIDE = [0, 0, 8, 8]        # deepslate, in layers
TOP = [8, 0, 16, 8]        # deepslate seen from above
CRYS = [0, 8, 4, 16]       # crystal, dark to light upwards
CRYS_EDGE = [4, 8, 8, 16]  # crystal, the lit edge
TIP = [8, 8, 12, 12]       # crystal tips

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
}


def stone(name, frm, to):
    return el(name, frm, to, uv=SIDE, faces={"up": TOP, "down": TOP})


def crystal(name, frm, to, axis=None, angle=0.0, origin=None, glow=6):
    return el(name, frm, to, axis, angle, origin, uv=CRYS, faces={"east": CRYS_EDGE, "north": CRYS_EDGE, "up": TIP},
              glow=glow)


ELEMENTS = [
    stone("bed", [0, 0, 0], [16, 5, 16]),
    stone("rubble_a", [1, 5, 2], [7, 7, 8]),
    stone("rubble_b", [10, 5, 9], [15, 6, 15]),
    crystal("main", [6.5, 5, 6.5], [9.5, 14, 9.5], "y", 45, [8, 8, 8]),
    crystal("main_tip", [7.25, 14, 7.25], [8.75, 16, 8.75], "y", 45, [8, 8, 8], glow=8),
    crystal("west", [2.5, 5, 8], [5, 12, 10.5], "z", 22.5, [4, 5, 9]),
    crystal("west_tip", [3, 12, 8.5], [4.5, 13.5, 10], "z", 22.5, [4, 5, 9], glow=8),
    crystal("east", [11, 5, 3], [13.5, 11, 5.5], "z", -22.5, [12, 5, 4]),
    crystal("south", [9, 5, 10.5], [11, 10, 12.5], "x", 22.5, [10, 5, 11.5]),
    crystal("north", [4, 6, 2.5], [6, 10, 4.5], "x", -22.5, [5, 6, 3.5]),
    crystal("small", [12, 5, 11], [13.5, 8, 12.5], "y", 45, [12.75, 6, 11.75]),
]


def paint(tex):
    rnd = random.Random(4)

    def region(uv):
        return [int(n * 2) for n in uv]  # UV units -> pixels of the 32x32 texture

    # deepslate sides: layers, cracks
    x0, y0, x1, y1 = region(SIDE)
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.random()
            c = STONE_D if y % 4 == 3 and n < 0.8 else STONE_D if n < 0.08 else STONE_L if n > 0.93 else STONE
            tex.set(x, y, c, 0.06)
    for _ in range(4):
        x, y = rnd.randrange(x0, x1), rnd.randrange(y0, y1)
        for _ in range(4):
            tex.set(x, y, CRACK, 0.0)
            x, y = min(x1 - 1, max(x0, x + rnd.choice((-1, 0, 1)))), min(y1 - 1, y + 1)
    # deepslate top: speckled, a few dark rings
    x0, y0, x1, y1 = region(TOP)
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.random()
            tex.set(x, y, STONE_D if n < 0.15 else STONE_L if n > 0.9 else STONE, 0.06)
    # crystal: dark at the root, lighter upwards
    for uv, lit in ((CRYS, False), (CRYS_EDGE, True)):
        x0, y0, x1, y1 = region(uv)
        for y in range(y0, y1):
            up = (y1 - y) / (y1 - y0)  # 1 at the top
            for x in range(x0, x1):
                c = CRYSTAL_L if lit and x == x0 else CRYSTAL_L if up > 0.75 else CRYSTAL if up > 0.3 else CRYSTAL_D
                if x == x1 - 1 and not lit:
                    c = CRYSTAL_D
                tex.set(x, y, c, 0.06)
    x0, y0, x1, y1 = region(TIP)
    for y in range(y0, y1):
        for x in range(x0, x1):
            tex.set(x, y, CRYSTAL_TIP, 0.08)
    for _ in range(3):  # a few sparks
        x0, y0, x1, y1 = region(CRYS)
        tex.set(rnd.randrange(x0, x1), rnd.randrange(y0, y0 + 6), (150, 180, 255), 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(32, 32)
    paint(tex)
    tex.save(tex_path)
    build(ELEMENTS, tex_path, "divinis:block/deepslate_crystal", blend_path, DISPLAY)
    print("saved", blend_path, tex_path)


main()
