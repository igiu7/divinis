"""
Builds the ten rare-metal ingots in Blender as Minecraft item models (JSON elements, 16x16 textures): a classic ingot
- a wide bar with a narrower, bevelled bar on top - in each metal's own colours, and each with something of its own:
  blacksteel   near-black steel, cold grey edges
  moonsilver   pale blue-silver, a little crescent moon raised on top (glowing)
  crimsonite   deep red metal with blood-red crystals growing out of it (glowing)
  mithril      bright silver with a blue sheen and a shine line
  etherite     pale, ghostly cyan, glowing all over
  adamantite   dark green-black, a hard faceted ridge along the top
  dragonsteel  black-red with a row of gold scale ridges down the top
  divinite     white gold, glowing brightly
  valorium     royal blue banded with gold
  voidsteel    black-violet, specks of stars in it (faintly glowing)

  blender -b --python tools/models/build_ingots.py -- <blend dir> <texture dir>

makes <blend dir>/<metal>_ingot.blend and <texture dir>/<metal>_ingot.png; then tools/models/export_item.py makes
models/item/<metal>_ingot.json from each .blend.

Texture layout: rows 0-7 the lit tops, rows 8-13 the sides, columns 0-7 of rows 14-15 the ends; the corner square
(8-11, 12-15) crystal, (12-15, 12-15) gold or ornament.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

TOP = [0, 0, 16, 8]
SIDE = [0, 8, 16, 14]
END = [0, 14, 8, 16]
CRYSTAL = [8, 12, 12, 16]
ORNAMENT = [12, 12, 16, 16]

# metal: (deep, base, light, shine), ornament colour, glow of the whole bar, of the ornament
METALS = {
    "blacksteel": ((18, 18, 22), (40, 40, 46), (70, 72, 80), (130, 134, 146), (90, 92, 100), 0, 0),
    "moonsilver": ((96, 112, 140), (160, 176, 204), (206, 220, 240), (246, 250, 255), (230, 240, 255), 0, 12),
    "crimsonite": ((60, 8, 14), (120, 20, 28), (170, 40, 46), (230, 110, 110), (220, 30, 50), 0, 10),
    "mithril": ((110, 130, 150), (176, 196, 214), (220, 232, 244), (255, 255, 255), (150, 200, 255), 0, 0),
    "etherite": ((60, 150, 170), (110, 210, 222), (170, 240, 244), (230, 255, 255), (200, 255, 255), 9, 12),
    "adamantite": ((14, 34, 26), (30, 70, 52), (56, 112, 84), (120, 190, 150), (80, 160, 120), 0, 0),
    "dragonsteel": ((34, 8, 8), (76, 18, 16), (120, 34, 26), (190, 80, 50), (230, 170, 60), 0, 4),
    "divinite": ((190, 150, 70), (236, 204, 120), (252, 236, 176), (255, 255, 240), (255, 250, 220), 11, 15),
    "valorium": ((20, 34, 96), (36, 62, 156), (70, 104, 206), (150, 180, 255), (232, 180, 60), 2, 4),
    "voidsteel": ((10, 4, 18), (28, 12, 46), (52, 26, 80), (120, 80, 180), (210, 180, 255), 3, 10),
}


def elements(metal, glow, oglow):
    els = [
        el("base", [2, 0, 5], [14, 2, 11], uv=SIDE, faces={"up": TOP, "down": SIDE, "east": END, "west": END}, glow=glow),
        el("top", [3, 2, 6], [13, 3.5, 10], uv=SIDE, faces={"up": TOP, "east": END, "west": END}, glow=glow),
    ]
    if metal == "moonsilver":
        els += [el("moon_a", [6, 3.5, 6.75], [7, 4.2, 9.25], uv=ORNAMENT, glow=oglow),
                el("moon_b", [7, 3.5, 6.5], [8.5, 4.2, 7.25], uv=ORNAMENT, glow=oglow),
                el("moon_c", [7, 3.5, 8.75], [8.5, 4.2, 9.5], uv=ORNAMENT, glow=oglow)]
    elif metal == "crimsonite":
        els += [el("shard_a", [9, 3, 7], [10.5, 6.5, 8.5], "y", 22.5, uv=CRYSTAL, glow=oglow),
                el("shard_b", [5.5, 3, 7.5], [6.5, 5, 8.5], "z", 22.5, uv=CRYSTAL, glow=oglow),
                el("shard_c", [11.5, 1.5, 9.5], [12.5, 3.5, 10.5], "x", -22.5, uv=CRYSTAL, glow=oglow)]
    elif metal == "adamantite":
        els.append(el("ridge", [4, 3.5, 7.25], [12, 4.25, 8.75], uv=SIDE, faces={"up": TOP}))
    elif metal == "dragonsteel":
        for i in range(4):
            els.append(el(f"scale{i}", [4 + i * 2, 3.5, 7.25], [5.25 + i * 2, 4.4, 8.75], "z", -22.5, uv=ORNAMENT, glow=oglow))
    elif metal == "valorium":
        els.append(el("band", [7, -0.01, 4.8], [9, 3.7, 11.2], uv=ORNAMENT, glow=oglow))
    elif metal == "mithril":
        els.append(el("shine", [4, 3.5, 7.75], [12, 3.6, 8.25], uv=ORNAMENT))
    elif metal == "divinite":
        els.append(el("seal", [7, 3.5, 7], [9, 4, 9], "y", 45, uv=ORNAMENT, glow=oglow))
    return els


def paint(metal, tex):
    deep, base, light, shine, orn, _, _ = METALS[metal]
    rnd = random.Random(hash(metal) & 0xFFFF)

    def lerp(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

    for y in range(16):
        for x in range(16):
            if y < 8:  # the tops: lit, a bright edge at the back, a shine streak
                c = lerp(light, base, y / 8)
                if y == 0 or x == 0 or x == 15:
                    c = lerp(c, shine, 0.5)
                if (x + y * 2) % 11 == 0:
                    c = lerp(c, shine, 0.6)
            elif y < 14:  # the sides
                c = lerp(base, deep, (y - 8) / 6)
                if y == 8:
                    c = lerp(c, light, 0.6)
            elif x < 8:  # the ends
                c = lerp(base, deep, 0.5)
            else:
                c = base
            if rnd.random() < 0.08:
                c = lerp(c, deep, 0.4)
            tex.set(x, y, c, 0.03)
    for y in range(12, 16):
        for x in range(8, 12):  # crystal: dark at the root, bright at the tip
            c = lerp(orn, (255, 200, 200), (15 - y) / 4) if metal == "crimsonite" else lerp(light, shine, (15 - y) / 4)
            tex.set(x, y, c, 0.02)
        for x in range(12, 16):  # ornament
            c = lerp(orn, (255, 255, 255), 0.25) if (x + y) % 3 == 0 and metal != "dragonsteel" else lerp(orn, deep, (y - 12) / 8)
            tex.set(x, y, c, 0.03)
    if metal == "voidsteel":  # specks of stars
        for _ in range(9):
            tex.set(rnd.randrange(0, 16), rnd.randrange(0, 14), (220, 200, 255), 0.0)
    if metal == "dragonsteel":  # scales on the sides
        for y in range(9, 14):
            for x in range(0, 16):
                if (x + (y % 2) * 2) % 4 == 0:
                    tex.set(x, y, lerp(base, (200, 140, 50), 0.35), 0.02)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    only = argv[2] if len(argv) > 2 else None
    for metal, spec in METALS.items():
        if only and metal != only:
            continue
        name = metal + "_ingot"
        tex_path = os.path.join(textures, name + ".png")
        tex = Tex(16, 16, seed=7)
        paint(metal, tex)
        tex.save(tex_path)
        build(elements(metal, spec[5], spec[6]), tex_path, "divinis:item/" + name, os.path.join(blends, name + ".blend"))
        print("saved", name)


main()
