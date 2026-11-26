"""
Builds the Minotaur's Axe item in Blender: the same great double axe the Minotaur carries (minotaur_axe.py), as a
Minecraft item model (JSON elements, 32x32 texture), held by the haft with the head up.

  blender -b --python tools/models/build_minotaur_axe.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/item/minotaur_axe.json from the .blend. Scene layout: see itemmodel.py.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402
from minotaur_axe import AXE, color  # noqa: E402

# texture regions (pixels of the 32x32 texture): x, y, w, h - the long side runs down
REGIONS = {
    "haft": (0, 0, 4, 32),
    "gold": (4, 0, 4, 8),
    "socket": (4, 8, 4, 8),
    "steel": (8, 0, 8, 16),
    "edge": (16, 0, 4, 16),
}

# held at the grip (axe z = 1 lands on y = 8); in the inventory it lies diagonally like a vanilla tool
DISPLAY = {
    "gui": {"rotation": [0, 0, -45], "translation": [-3, -3, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 0, -45], "translation": [-3, -3, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2, 0.5], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 2, 0.5], "scale": [0.75, 0.75, 0.75]},
    "firstperson_righthand": {"rotation": [0, 90, 15], "translation": [1, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, -90, -15], "translation": [1, 2, 0], "scale": [0.55, 0.55, 0.55]},
}


def to_item(origin, size):
    """Axe space -> item model space: blades along x, the head up (y), the thickness along z."""
    (ox, oy, oz), (sx, sy, sz) = origin, size
    return [8 + oy, 9 - (oz + sz), 8 + ox], [8 + oy + sy, 9 - oz, 8 + ox + sx]


def paint(tex):
    for material, (x0, y0, w, h) in REGIONS.items():
        for yy in range(h):
            for xx in range(w):
                # the texture has twice the boss model's pixels: draw the pattern at the same size
                rim = min(xx, w - 1 - xx) // 2
                tex.set(x0 + xx, y0 + yy, color(material, yy // 2, rim, h // 2), 0.05)


def elements():
    out = []
    for name, origin, size, material in AXE:
        x, y, w, h = REGIONS[material]
        uv = [x / 2, y / 2, (x + w) / 2, (y + h) / 2]  # model UVs run 0..16 over the 32 pixels
        end = [x / 2, y / 2, (x + w) / 2, y / 2 + 1]
        frm, to = to_item(origin, size)
        out.append(el(name, frm, to, uv=uv, faces={"up": end, "down": end}))
    return out


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(32, 32)
    paint(tex)
    tex.save(tex_path)
    build(elements(), tex_path, "divinis:item/minotaur_axe", blend_path, DISPLAY)
    print("saved", blend_path, tex_path)


main()
