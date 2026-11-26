"""
Builds the Golem's crystal spike in Blender: a cluster of dark blue crystals with no stone under them, which the
Golem's crystal spikes attack shows (as item displays, three blocks tall, bursting up out of the ground). An item
model (JSON elements, 16x16 texture); the crystals glow faintly.

  blender -b --python tools/models/build_crystal_spike.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/item/golem_crystal_spike.json from the .blend. Scene layout: see
itemmodel.py.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

# the Golem's crystal palette (build_golem.py)
CRYSTAL = (22, 36, 104)
CRYSTAL_L = (46, 70, 160)
CRYSTAL_D = (12, 18, 60)
CRYSTAL_TIP = (70, 100, 200)

BODY = [0, 0, 8, 16]    # dark at the root, lighter upwards
EDGE = [8, 0, 12, 16]   # the lit edge
TIP = [12, 0, 16, 4]


def crystal(name, frm, to, axis=None, angle=0.0, origin=None, glow=6, tip=False):
    if tip:
        return el(name, frm, to, axis, angle, origin, uv=TIP, glow=9)
    return el(name, frm, to, axis, angle, origin, uv=BODY, faces={"east": EDGE, "north": EDGE, "up": TIP}, glow=glow)


ELEMENTS = [
    crystal("main", [6.5, 0, 6.5], [9.5, 13, 9.5], "y", 45, [8, 8, 8]),
    crystal("main_tip", [7.25, 13, 7.25], [8.75, 16, 8.75], "y", 45, [8, 8, 8], tip=True),
    crystal("west", [3, 0, 7], [5, 8, 9], "z", 22.5, [5, 0, 8]),
    crystal("west_tip", [3.5, 8, 7.5], [4.5, 10, 8.5], "z", 22.5, [5, 0, 8], tip=True),
    crystal("east", [11, 0, 6.5], [13, 7, 8.5], "z", -22.5, [11, 0, 7.5]),
    crystal("east_tip", [11.5, 7, 7], [12.5, 8.5, 8], "z", -22.5, [11, 0, 7.5], tip=True),
    crystal("south", [7, 0, 10.5], [9, 6, 12.5], "x", 22.5, [8, 0, 11]),
    crystal("north", [7.25, 0, 3.5], [8.75, 5, 5], "x", -22.5, [8, 0, 5]),
]


def paint(tex):
    rnd = random.Random(6)
    for y in range(16):
        up = (16 - y) / 16
        for x in range(16):
            if x < 8:
                c = CRYSTAL_L if up > 0.75 else CRYSTAL if up > 0.3 else CRYSTAL_D
                if x == 7:
                    c = CRYSTAL_D
            elif x < 12:
                c = CRYSTAL_L if x == 8 else CRYSTAL
            else:
                c = CRYSTAL_TIP
            tex.set(x, y, c, 0.06)
    for _ in range(3):
        tex.set(rnd.randrange(0, 8), rnd.randrange(0, 6), (150, 180, 255), 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(16, 16)
    paint(tex)
    tex.save(tex_path)
    build(ELEMENTS, tex_path, "divinis:item/golem_crystal_spike", blend_path)
    print("saved", blend_path, tex_path)


main()
