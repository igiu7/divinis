"""
Builds the three Labyrinth keys in Blender (Minecraft item models, JSON elements, 16x16 textures) and paints the
matching lock textures:
- Bronze Key   (opens the bronze vault)  - plain bow, two teeth
- Silver Key   (opens the silver vault)  - a blue gem in the bow, a long and a short tooth
- Minotaur Key (opens the Minotaur's hall) - gold, horns on the bow, three teeth

  blender -b --python tools/models/build_keys.py -- <blend dir> <item texture dir> <block texture dir>

then tools/models/export_item.py makes models/item/labyrinth_key_<kind>.json from each .blend.
Scene layout: see itemmodel.py.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

KINDS = {
    #          base            light           dark            accent
    "bronze": ((176, 112, 52), (222, 160, 92), (112, 66, 30), (222, 160, 92)),
    "silver": ((176, 182, 192), (230, 234, 240), (108, 114, 126), (70, 150, 235)),
    "gold": ((226, 178, 56), (252, 226, 120), (150, 104, 24), (236, 230, 210)),
}

# texture regions (UV units 0..16): metal, its light side, dark side, accent (gem / horn)
METAL, LIGHT, DARK, ACCENT = [0, 0, 8, 16], [8, 0, 12, 16], [12, 0, 16, 8], [12, 8, 16, 16]

DISPLAY = {
    "gui": {"rotation": [0, 0, -45], "scale": [1.0, 1.0, 1.0]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1.0, 1.0, 1.0]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def flat(name, x0, y0, x1, y1, uv=METAL, thick=0.5, **kw):
    return el(name, [x0, y0, 8 - thick], [x1, y1, 8 + thick], uv=uv,
              faces={"east": LIGHT, "up": LIGHT, "west": DARK, "down": DARK}, **kw)


def key_elements(kind):
    out = [
        flat("bow_top", 6, 14, 10, 15),
        flat("bow_bottom", 6, 10, 10, 11),
        flat("bow_left", 5, 11, 6, 14),
        flat("bow_right", 10, 11, 11, 14),
        flat("collar", 7, 9, 9, 10, thick=0.75),
        flat("shaft", 7.5, 2, 8.5, 9),
    ]
    if kind == "bronze":
        out += [flat("tooth_1", 8.5, 2, 10.5, 3), flat("tooth_2", 8.5, 4, 9.5, 5)]
    elif kind == "silver":
        out += [flat("tooth_1", 8.5, 2, 11, 3), flat("tooth_2", 8.5, 3.5, 9.5, 4.5),
                flat("gem", 7, 11.5, 9, 13.5, uv=ACCENT, thick=0.3)]
    else:
        out += [flat("tooth_1", 8.5, 2, 10.5, 3), flat("tooth_2", 8.5, 3.5, 10, 4.5), flat("tooth_3", 8.5, 5, 9.5, 6),
                flat("horn_left", 3.5, 14, 5, 16.5, uv=ACCENT, axis="z", angle=22.5, origin=[5, 14, 8]),
                flat("horn_right", 11, 14, 12.5, 16.5, uv=ACCENT, axis="z", angle=-22.5, origin=[11, 14, 8])]
    return out


def paint_key(tex, kind):
    base, light, dark, accent = KINDS[kind]
    for y in range(16):
        for x in range(16):
            if x < 8:
                c = light if x in (2, 3) else dark if x == 7 else base
            elif x < 12:
                c = light
            elif y < 8:
                c = dark
            else:
                c = accent
            tex.set(x, y, c, 0.06)


def paint_lock(tex, kind):
    base, light, dark, _ = KINDS[kind]
    frame = (54, 52, 60)
    hole = (18, 14, 20)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            c = frame if edge else dark if inner else base
            if not edge and not inner and (x == 2 or y == 2):
                c = light
            tex.set(x, y, c, 0.07)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        tex.set(x, y, light, 0.0)  # rivets
    # the keyhole: a round top and a slot below it
    for (x, y) in ((7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8),
                   (7, 9), (8, 9), (7, 10), (8, 10), (6, 11), (7, 11), (8, 11), (9, 11)):
        tex.set(x, y, hole, 0.0)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_dir, item_dir, block_dir = argv[0], argv[1], argv[2]
    for kind in KINDS:
        lock = Tex(16, 16, seed=17)
        paint_lock(lock, kind)
        lock.save(os.path.join(block_dir, f"labyrinth_lock_{kind}.png"))
        tex = Tex(16, 16, seed=13)
        paint_key(tex, kind)
        tex_path = os.path.join(item_dir, f"labyrinth_key_{kind}.png")
        tex.save(tex_path)
        blend = os.path.join(blend_dir, f"labyrinth_key_{kind}.blend")
        build(key_elements(kind), tex_path, f"divinis:item/labyrinth_key_{kind}", blend, DISPLAY)
        print("saved", blend)


main()
