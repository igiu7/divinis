"""
Builds the rare-metal weapons in Blender as 3D item models (JSON elements) - the models once (blacksteel), the
32x32 texture of their parts for every metal (<metal>_weapon_parts.png: blade, edge and fittings in the metal's
colours, the gem and the lance's spiral in its accent); the other metals' item models are the blacksteel ones with
their texture swapped (tools/build_weapon_models.py). Each texture is painted in
the Minecraft manner (ramps of shades, lit from the top-left): blades of dark blacksteel with a bright silver edge and a
dark fuller, guards and pommels of blacksteel set with a red gem, grips wrapped in leather, hafts of dark wood, the
lance's shaft painted in black and red.
  sword            a knightly sword: crossguard with a gem, a fuller, a pointed blade
  longsword        a long two-handed grip, a wide crossguard with a gem, a long blade
  shortsword       a short, broad leaf-shaped blade
  dagger           a small guard, a narrow blade
  sabre            a curved blade and a knuckle guard round the grip
  spear            a long haft, a socket and a leaf-shaped head
  lance            a jousting lance: a great round vamplate over the grip, a shaft tapering to a point
  halberd          an axe blade, a back spike and a top spike on a long haft, metal langets down it
  two_handed_axe   a great crescent blade with a bearded lower edge and a back spike, on a long haft

  blender -b --python tools/models/build_blacksteel_weapons.py -- <blend dir> <texture.png>

then tools/models/export_item.py makes models/item/blacksteel_<weapon>.json from each .blend.
Item space: the weapon stands up along y, the blade's flat facing z, the grip about the middle of the hand at y ~ 3.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

# texture regions (pixels of the 32x32 texture): x, y, w, h - long parts run down
REGIONS = {
    "blade": (0, 0, 6, 32), "edge": (6, 0, 3, 32), "fuller": (9, 0, 2, 32), "guard": (11, 0, 5, 12), "gem": (11, 12, 5, 4),
    "pommel": (11, 16, 5, 6), "grip": (16, 0, 4, 32), "wood": (20, 0, 4, 32), "lance": (24, 0, 6, 32), "langet": (11, 22, 5, 10),
}
# the metals (as build_ingots.py): deep, base, light, shine; and an accent (gems, the lance's spiral)
METALS = {
    "blacksteel": (((16, 16, 20), (38, 38, 46), (76, 78, 88), (128, 132, 144)), (200, 30, 40)),
    "moonsilver": (((70, 82, 108), (138, 154, 186), (196, 210, 234), (244, 248, 255)), (150, 200, 255)),
    "crimsonite": (((44, 6, 10), (104, 18, 24), (156, 38, 44), (226, 108, 108)), (255, 40, 70)),
    "mithril": (((84, 102, 124), (162, 182, 202), (214, 228, 242), (255, 255, 255)), (90, 170, 255)),
    "etherite": (((36, 110, 128), (96, 196, 210), (160, 234, 240), (228, 255, 255)), (220, 255, 255)),
    "adamantite": (((10, 28, 20), (28, 66, 48), (54, 108, 80), (118, 186, 146)), (60, 230, 130)),
    "dragonsteel": (((26, 6, 6), (68, 16, 14), (114, 32, 24), (186, 80, 50)), (255, 140, 30)),
    "divinite": (((150, 112, 48), (222, 190, 110), (248, 230, 168), (255, 254, 236)), (255, 250, 200)),
    "valorium": (((14, 26, 80), (32, 58, 148), (66, 100, 202), (146, 178, 255)), (230, 180, 60)),
    "voidsteel": (((8, 3, 14), (26, 11, 44), (50, 26, 80), (120, 82, 180)), (190, 110, 255)),
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def metal_ramps(metal):
    (deep, base, light, shine), accent = METALS[metal]
    steel = [tuple(int(c * 0.5) for c in deep), deep, mix(deep, base, 0.5), base, mix(base, light, 0.5), light, mix(light, shine, 0.5), shine]
    edge = [mix(shine, (0, 0, 0), t) for t in (0.6, 0.45, 0.32, 0.2, 0.1, 0.0, 0.0, 0.0)]
    edge[6] = mix(shine, (255, 255, 255), 0.4)
    edge[7] = (255, 255, 255)
    gem = [mix(accent, (0, 0, 0), t) for t in (0.75, 0.62, 0.48, 0.32, 0.15, 0.0, 0.0, 0.0)]
    gem[6], gem[7] = mix(accent, (255, 255, 255), 0.4), mix(accent, (255, 255, 255), 0.75)
    red = [mix(accent, (0, 0, 0), t) for t in (0.85, 0.78, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2)]
    return {"steel": steel, "silver": edge, "gem": gem, "red": red}


RAMPS = {
    "steel": [(8, 8, 11), (16, 16, 20), (26, 26, 32), (38, 38, 46), (52, 53, 63), (72, 74, 86), (104, 108, 122), (150, 154, 168)],
    "silver": [(60, 64, 74), (96, 102, 114), (134, 140, 154), (170, 176, 190), (200, 206, 218), (226, 230, 240), (244, 246, 252), (255, 255, 255)],
    "leather": [(20, 12, 8), (32, 20, 12), (46, 30, 18), (62, 42, 26), (80, 56, 34), (100, 72, 46), (124, 92, 60), (150, 114, 78)],
    "wood": [(18, 12, 8), (28, 19, 12), (40, 28, 18), (54, 38, 24), (70, 50, 32), (88, 64, 42), (108, 80, 54), (130, 98, 68)],
    "gem": [(60, 4, 8), (96, 8, 14), (136, 14, 22), (176, 24, 32), (214, 44, 50), (240, 90, 92), (255, 150, 150), (255, 210, 210)],
    "red": [(40, 4, 6), (64, 8, 10), (90, 12, 16), (118, 18, 22), (146, 26, 30), (172, 40, 42), (198, 64, 64), (220, 96, 96)],
}


def shade(ramp, level):
    return RAMPS[ramp][max(0, min(7, int(round(level))))]


def paint(tex, metal="blacksteel"):
    RAMPS.update(metal_ramps(metal))
    rnd = random.Random(7)
    for name, (x0, y0, w, h) in REGIONS.items():
        for yy in range(h):
            for xx in range(w):
                n = rnd.random()
                base = 3.6 + (0.9 if xx == 0 else -0.9 if xx == w - 1 else 0) + (0.3 if (xx + yy) % 2 else -0.3) + rnd.uniform(-0.4, 0.4)
                if name == "blade":
                    c = shade("steel", base + (1.2 if xx == 1 else 0) + (2.0 if n < 0.03 else 0))
                elif name == "edge":
                    c = shade("silver", 4.6 + (1.5 if xx == 0 else -0.5 * xx) + rnd.uniform(-0.4, 0.4))
                elif name == "fuller":
                    c = shade("steel", 1.4 + xx * 0.6 + rnd.uniform(-0.3, 0.3))
                elif name in ("guard", "pommel", "langet"):
                    c = shade("steel", base + (1.4 if yy % 6 == 0 else 0))
                    if (xx == 0 or yy == 0) and n < 0.6:
                        c = shade("silver", 3.5)
                elif name == "gem":
                    cx, cy = (w - 1) / 2, (h - 1) / 2
                    d = max(abs(xx - cx) / (cx + 0.5), abs(yy - cy) / (cy + 0.5))
                    c = shade("gem", 6.4 - d * 4 + (0.8 if xx < cx and yy < cy else 0))
                elif name == "grip":
                    # leather wrapped round and round: diagonal bands
                    band = (xx + yy) % 3
                    c = shade("leather", 4.4 - band * 1.1 + rnd.uniform(-0.3, 0.3))
                elif name == "wood":
                    c = shade("wood", 3.8 + (0.8 if xx == 1 else -0.6 if xx == 3 else 0) + (-1.2 if n < 0.08 else 0) + rnd.uniform(-0.4, 0.4))
                else:  # the lance: black and red in a spiral
                    red = ((xx + yy // 2) // 3) % 2 == 0
                    c = shade("red" if red else "steel", base + 0.4)
                tex.set(x0 + xx, y0 + yy, c, 0.0)


def uv(region, along=None):
    """Model UVs (0..16) of a texture region; `along` limits the long side to that many model units."""
    x, y, w, h = REGIONS[region]
    hh = h if along is None else min(h, along * 2)
    return [x / 2, y / 2, (x + w) / 2, (y + hh) / 2]


def part(name, frm, to, region, axis=None, angle=0.0, origin=None):
    length = to[1] - frm[1]
    end = [REGIONS[region][0] / 2, REGIONS[region][1] / 2, (REGIONS[region][0] + REGIONS[region][2]) / 2, REGIONS[region][1] / 2 + 0.5]
    return el(name, frm, to, axis, angle, origin, uv(region, length), {"up": end, "down": end})


def hilt(grip_len, guard_half, gem=True, pommel=True):
    """Grip from y 0, a guard on it, a pommel under it."""
    p = [part("grip", [7.25, 0, 7.25], [8.75, grip_len, 8.75], "grip"),
         part("guard", [8 - guard_half, grip_len, 7.0], [8 + guard_half, grip_len + 1.25, 9.0], "guard")]
    if gem:
        p.append(part("gem", [7.4, grip_len + 0.1, 6.8], [8.6, grip_len + 1.15, 9.2], "gem"))
    if pommel:
        p.append(part("pommel", [6.9, -1.6, 6.9], [9.1, 0, 9.1], "pommel"))
    return p


def blade(y0, length, half, tip):
    """A straight blade from y0: flat along z, a silver edge on each side, a fuller down the middle, a point."""
    y1 = y0 + length
    return [part("blade", [8 - half + 0.5, y0, 7.5], [8 + half - 0.5, y1, 8.5], "blade"),
            part("edge_l", [8 - half, y0, 7.6], [8 - half + 0.5, y1, 8.4], "edge"),
            part("edge_r", [8 + half - 0.5, y0, 7.6], [8 + half, y1, 8.4], "edge"),
            part("fuller", [7.75, y0 + 1, 7.42], [8.25, y1 - 2, 8.58], "fuller"),
            part("tip", [8 - half * 0.6, y1, 7.55], [8 + half * 0.6, y1 + tip * 0.6, 8.45], "edge"),
            part("tip2", [8 - half * 0.25, y1 + tip * 0.6, 7.6], [8 + half * 0.25, y1 + tip, 8.4], "edge")]


def haft(y0, y1):
    return [part("haft", [7.4, y0, 7.4], [8.6, y1, 8.6], "wood"),
            part("butt", [7.2, y0 - 1, 7.2], [8.8, y0, 8.8], "pommel")]


WEAPONS = {
    "sword": lambda: hilt(5.5, 3.6) + blade(6.75, 14, 1.25, 3),
    "longsword": lambda: hilt(8, 4.8) + blade(9.25, 19, 1.15, 3.5)
    + [part("ricasso", [7.2, 9.25, 7.45], [8.8, 11, 8.55], "guard")],
    "shortsword": lambda: hilt(4.5, 2.8) + blade(5.75, 8, 1.5, 3) + [part("leaf", [6.2, 10, 7.55], [9.8, 12.5, 8.45], "blade")],
    "dagger": lambda: hilt(4, 2.0, gem=False) + blade(5.25, 5.5, 0.9, 2.2),
    "sabre": lambda: hilt(5, 3.0, gem=False) + [
        part("knuckle_bow", [10.6, -0.4, 7.6], [11.4, 5, 8.4], "guard"),
        part("knuckle_foot", [8.6, -0.9, 7.6], [11.4, -0.2, 8.4], "guard"),
        part("blade", [7.0, 6.25, 7.5], [9.0, 13, 8.5], "blade"),
        part("edge", [9.0, 6.25, 7.6], [9.5, 13, 8.4], "edge"),
        part("blade_2", [7.2, 13, 7.5], [9.2, 18, 8.5], "blade", "z", -22.5, [8.2, 13, 8]),
        part("edge_2", [9.2, 13, 7.6], [9.7, 18, 8.4], "edge", "z", -22.5, [8.2, 13, 8]),
        part("blade_3", [9.0, 17.2, 7.55], [10.6, 21.6, 8.45], "blade", "z", -45, [9.8, 17.4, 8]),
        part("tip", [9.6, 21.4, 7.6], [10.4, 23.4, 8.4], "edge", "z", -45, [9.8, 17.4, 8]),
    ],
    "spear": lambda: haft(-14, 22) + [
        part("socket", [7.0, 21, 7.0], [9.0, 24, 9.0], "langet"),
        part("head", [6.4, 24, 7.5], [9.6, 29, 8.5], "blade"),
        part("head_edge_l", [6.0, 24.5, 7.6], [6.4, 28.5, 8.4], "edge"),
        part("head_edge_r", [9.6, 24.5, 7.6], [10.0, 28.5, 8.4], "edge"),
        part("rib", [7.75, 24, 7.35], [8.25, 30, 8.65], "guard"),
        part("tip", [7.2, 29, 7.6], [8.8, 31, 8.4], "edge"),
        part("tip2", [7.7, 31, 7.7], [8.3, 32, 8.3], "edge"),
    ],
    "lance": lambda: [
        part("butt", [6.8, -10, 6.8], [9.2, -7, 9.2], "pommel"),
        part("grip", [7.25, -7, 7.25], [8.75, 1.5, 8.75], "grip"),
        part("vamplate", [3.0, 1.5, 3.0], [13.0, 3.0, 13.0], "guard"),
        part("vamplate_rim", [3.5, 3.0, 3.5], [12.5, 3.6, 12.5], "langet"),
        part("shaft_1", [6.0, 3.6, 6.0], [10.0, 11, 10.0], "lance"),
        part("shaft_2", [6.5, 11, 6.5], [9.5, 18, 9.5], "lance"),
        part("shaft_3", [7.0, 18, 7.0], [9.0, 24, 9.0], "lance"),
        part("shaft_4", [7.4, 24, 7.4], [8.6, 28.5, 8.6], "lance"),
        part("coronel", [7.2, 28.5, 7.2], [8.8, 30, 8.8], "langet"),
        part("point", [7.6, 30, 7.6], [8.4, 32, 8.4], "edge"),
    ],
    "halberd": lambda: haft(-14, 24) + [
        part("langet_1", [7.25, 14, 7.25], [8.75, 18, 8.75], "langet"),
        part("socket", [7.0, 18, 7.0], [9.0, 24.5, 9.0], "langet"),
        part("blade", [9.0, 18.5, 7.5], [13.0, 24, 8.5], "blade"),
        part("blade_edge", [13.0, 17.5, 7.6], [14.0, 25, 8.4], "edge"),
        part("beard", [9.0, 17.5, 7.6], [11.5, 18.5, 8.4], "blade"),
        part("back_spike", [4.5, 20.5, 7.6], [7.0, 21.75, 8.4], "langet"),
        part("back_tip", [3.5, 20.8, 7.7], [4.5, 21.45, 8.3], "edge"),
        part("top_spike", [7.4, 24.5, 7.5], [8.6, 30, 8.5], "blade"),
        part("top_tip", [7.7, 30, 7.65], [8.3, 32, 8.35], "edge"),
        part("gem", [7.3, 21, 6.85], [8.7, 22.4, 9.15], "gem"),
    ],
    "two_handed_axe": lambda: haft(-12, 20) + [
        part("socket", [7.0, 12, 7.0], [9.0, 21.5, 9.0], "langet"),
        part("blade", [9.0, 12.5, 7.5], [14.0, 21, 8.5], "blade"),
        part("blade_top", [9.0, 21, 7.55], [13.0, 22.5, 8.45], "blade"),
        part("beard", [9.0, 10.5, 7.55], [12.5, 12.5, 8.45], "blade"),
        part("edge", [14.0, 11.5, 7.6], [15.2, 22, 8.4], "edge"),
        part("edge_top", [13.0, 22, 7.6], [14.2, 23.2, 8.4], "edge"),
        part("edge_low", [12.5, 9.8, 7.6], [13.7, 11.5, 8.4], "edge"),
        part("back_spike", [4.8, 15.5, 7.6], [7.0, 17.5, 8.4], "langet"),
        part("back_tip", [3.8, 16.0, 7.7], [4.8, 17.0, 8.3], "edge"),
        part("top", [7.4, 21.5, 7.4], [8.6, 23.5, 8.6], "pommel"),
        part("gem", [7.3, 16, 6.85], [8.7, 17.4, 9.15], "gem"),
    ],
}


def display(elements):
    """Held at the grip (y ~ 3); in the inventory lying diagonally, as big as fits."""
    ys = [e["frm"][1] for e in elements] + [e["to"][1] for e in elements]
    lo, hi = min(ys), max(ys)
    length, mid = hi - lo, (lo + hi) / 2
    s = min(1.0, 21.0 / length)
    d = (mid - 8) * s
    hand = (8 - 3) * 0.85
    return {
        "gui": {"rotation": [0, 0, -45], "translation": [round(-0.707 * d, 2), round(-0.707 * d, 2), 0], "scale": [s, s, s]},
        "fixed": {"rotation": [0, 0, -45], "translation": [round(-0.707 * d, 2), round(-0.707 * d, 2), 0], "scale": [s, s, s]},
        "ground": {"translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, round(hand, 2), 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, round(hand, 2), 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, 90, 20], "translation": [1, round(hand * 0.8, 2), 0], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, -90, -20], "translation": [1, round(hand * 0.8, 2), 0], "scale": [0.68, 0.68, 0.68]},
    }


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, tex_path = argv[0], argv[1]
    for metal in METALS:
        t = Tex(32, 32, seed=3)
        paint(t, metal)
        t.save(tex_path.replace("blacksteel_", metal + "_"))
    tex = Tex(32, 32, seed=3)
    paint(tex)
    tex.save(tex_path)
    for name, make in WEAPONS.items():
        elements = make()
        build(elements, tex_path, "divinis:item/blacksteel_weapon_parts", os.path.join(blends, f"blacksteel_{name}.blend"), display(elements))
        print("saved", name, len(elements))


main()
