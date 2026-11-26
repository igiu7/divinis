"""
Builds the arrow of the Dungeon Scout's Treasure Sense (skill.SkillEffects, client.SkillFx) in Blender: a golden
arrow pointing north (-z) - a faceted head in three steps, a shaft with a ring, cyan fletching in a cross - glowing.
A block model (JSON elements, 16x16 texture); the client turns it towards the chest and floats it before the player.

  blender -b --python tools/models/build_treasure_arrow.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/block/skill/treasure_arrow.json. Scene layout: see itemmodel.py.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

GOLD = [0, 0, 8, 4]
GOLD_LIGHT = [0, 4, 8, 8]
GEM = [8, 0, 12, 4]
FEATHER = [0, 8, 8, 16]
RING = [8, 4, 12, 8]

ELEMENTS = [
    el("head_tip", [7.5, 7.4, 0.6], [8.5, 8.6, 1.6], uv=GOLD_LIGHT, glow=12),
    el("head_mid", [7.0, 7.3, 1.6], [9.0, 8.7, 2.8], uv=GOLD_LIGHT, glow=12),
    el("head_base", [6.3, 7.2, 2.8], [9.7, 8.8, 4.2], uv=GOLD, faces={"up": GOLD_LIGHT}, glow=12),
    el("gem", [7.6, 8.8, 2.9], [8.4, 9.1, 3.7], uv=GEM, glow=15),
    el("shaft", [7.6, 7.6, 4.2], [8.4, 8.4, 13.0], uv=GOLD, glow=10),
    el("ring", [7.3, 7.3, 6.0], [8.7, 8.7, 6.8], uv=RING, glow=12),
    el("fletch_h", [6.2, 7.85, 11.0], [9.8, 8.15, 14.6], uv=FEATHER, glow=10),
    el("fletch_v", [7.85, 6.2, 11.0], [8.15, 9.8, 14.6], uv=FEATHER, glow=10),
    el("nock", [7.5, 7.5, 14.6], [8.5, 8.5, 15.2], uv=GOLD, glow=10),
]


def paint(tex):
    for y in range(4):
        for x in range(8):
            tex.set(x, y, (222, 160, 40) if y < 3 else (186, 124, 24), 0.03)
    for y in range(4, 8):
        for x in range(8):
            tex.set(x, y, (255, 226, 120) if y < 6 else (246, 196, 70), 0.03)
    for y in range(4):
        for x in range(8, 12):
            tex.set(x, y, (120, 240, 255) if (x + y) % 2 else (60, 190, 230), 0.0)
    for y in range(4, 8):
        for x in range(8, 12):
            tex.set(x, y, (255, 240, 180), 0.0)
    for y in range(8, 16):
        for x in range(8):
            t = (y - 8) / 7
            tex.set(x, y, (int(130 - 80 * t), int(236 - 60 * t), int(250 - 30 * t)), 0.02)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(16, 16)
    paint(tex)
    tex.save(tex_path)
    build(ELEMENTS, tex_path, "divinis:block/skill/treasure_arrow", blend_path)
    print("saved", blend_path, tex_path)


main()
