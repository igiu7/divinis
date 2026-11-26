"""
The metal parts of the rare-metal weapons (registry.ModWeapons): a blade, head or tip for each weapon in each metal -
"<metal>_<part>" (e.g. "mithril_dagger_blade"). Paints their 16x16 icons in the Minecraft manner (lying diagonally like
a vanilla sword, a dark outline, lit from the top-left, a bright edge, the metal's colours as build_ingots.py), writes
their item models and names, and the shapes the anvil forges them to (forge_shapes.json: each part's pixels, the same
in every metal - forge.ForgeShapes); in the Armory Table (armory_default.json) the weapon is made of its part and
sticks / leather (the parts themselves are forged on the anvil). The parts are unlocked with "<metal>_weapons"
(technology_default.json). Both files get a higher version, so the server's copies are replaced.
Can be run again.

  python tools/build_weapon_parts.py
"""
import json
import math
import random
import struct
import zlib

ASSETS = "src/main/resources/assets/divinis/"
RES = "src/main/resources/divinis/"
# deep, base, light, shine (as build_ingots.py / build_blacksteel_weapons.py)
METALS = {
    "blacksteel": ((16, 16, 20), (38, 38, 46), (76, 78, 88), (128, 132, 144)),
    "moonsilver": ((70, 82, 108), (138, 154, 186), (196, 210, 234), (244, 248, 255)),
    "crimsonite": ((44, 6, 10), (104, 18, 24), (156, 38, 44), (226, 108, 108)),
    "mithril": ((84, 102, 124), (162, 182, 202), (214, 228, 242), (255, 255, 255)),
    "etherite": ((36, 110, 128), (96, 196, 210), (160, 234, 240), (228, 255, 255)),
    "adamantite": ((10, 28, 20), (28, 66, 48), (54, 108, 80), (118, 186, 146)),
    "dragonsteel": ((26, 6, 6), (68, 16, 14), (114, 32, 24), (186, 80, 50)),
    "divinite": ((150, 112, 48), (222, 190, 110), (248, 230, 168), (255, 254, 236)),
    "valorium": ((14, 26, 80), (32, 58, 148), (66, 100, 202), (146, 178, 255)),
    "voidsteel": ((8, 3, 14), (26, 11, 44), (50, 26, 80), (120, 82, 180)),
}
# weapon -> part, its name, ingots it takes (what the weapon took before)
PARTS = {
    "sword": ("sword_blade", "Sword Blade", 3), "longsword": ("longsword_blade", "Longsword Blade", 4),
    "shortsword": ("shortsword_blade", "Shortsword Blade", 2), "dagger": ("dagger_blade", "Dagger Blade", 1),
    "sabre": ("sabre_blade", "Sabre Blade", 3), "spear": ("spear_head", "Spear Head", 2),
    "lance": ("lance_tip", "Lance Tip", 3), "halberd": ("halberd_head", "Halberd Head", 4),
    "two_handed_axe": ("two_handed_axe_head", "Two-Handed Axe Head", 5),
}
# besides its part, a weapon takes these
REST = {
    "sword": [("item", "minecraft:stick", 1), ("tag", "c:leathers", 1)],
    "longsword": [("item", "minecraft:stick", 1), ("tag", "c:leathers", 2)],
    "shortsword": [("tag", "c:leathers", 1)], "dagger": [("tag", "c:leathers", 1)], "sabre": [("tag", "c:leathers", 1)],
    "spear": [("item", "minecraft:stick", 4)], "lance": [("item", "minecraft:stick", 6), ("tag", "c:leathers", 1)],
    "halberd": [("item", "minecraft:stick", 4)], "two_handed_axe": [("item", "minecraft:stick", 3)],
}


# ---- the shapes: in the frame of the diagonal (u along it from the bottom-left, v across, + to the bottom-right),
# each returns the material at (u, v): "blade", "edge", "fuller", "socket" or None

def straight(tang0, blade0, blade1, tip1, half, leaf=0.0):
    def shape(u, v):
        if tang0 <= u < blade0:
            return "socket" if abs(v) < 0.75 else None
        if blade0 <= u < blade1:
            h = half + leaf * math.sin(math.pi * (u - blade0) / (blade1 - blade0))
        elif blade1 <= u < tip1:
            h = (half + 0.2) * (tip1 - u) / (tip1 - blade1)
        else:
            return None
        if abs(v) > h:
            return None
        if u < blade0 + 0.9 and abs(v) > 0.4:
            return "socket"  # the shoulders of the blade
        if abs(v) > h - 0.75:
            return "edge"
        return "fuller" if abs(v) < 0.45 and u < blade1 - 1 else "blade"
    return shape


def sabre(u, v):
    if 3 <= u < 5.5:
        return "socket" if abs(v) < 0.75 else None
    if not 5.5 <= u < 18:
        return None
    c = 0.028 * (u - 5.5) ** 2  # it curves back (to -v), the edge on the outside (+v)
    w = v + c
    h = 1.35 if u < 15 else 1.35 * (18 - u) / 3
    if not -h <= w <= h:
        return None
    if u < 6.3 and abs(w) > 0.4:
        return "socket"
    return "edge" if w > h - 0.75 else "fuller" if -0.9 < w < -0.3 and u < 15 else "blade"


def spear(u, v):
    if 2 <= u < 7.5:
        return ("socket" if abs(v) < 1.0 else None) if u > 2.6 or abs(v) < 0.6 else None
    if not 7.5 <= u < 18:
        return None
    h = 2.7 * math.sin(math.pi * (u - 7.5) / 10.5) ** 0.8
    if abs(v) > h:
        return None
    if abs(v) < 0.45:
        return "fuller"
    return "edge" if abs(v) > h - 0.75 else "blade"


def lance(u, v):
    if 3 <= u < 4.6:
        return "socket" if abs(v) < 3.4 else None
    if not 4.6 <= u < 17.5:
        return None
    h = 3.1 * (17.5 - u) / 12.9 + 0.2
    if abs(v) > h:
        return None
    return "edge" if v < -h + 0.8 else "fuller" if v > h - 0.9 else "blade"


def halberd(u, v):
    if 1.5 <= u < 14 and abs(v) < 0.9:
        return "socket"
    if 14 <= u < 18.6:
        h = 1.0 * (18.6 - u) / 4.6
        return ("edge" if v < -h + 0.5 else "blade") if abs(v) <= h else None
    if 6.5 <= u < 13.5 and 0.9 <= v:  # the axe blade, its edge curving out
        outer = 4.6 - 0.09 * (u - 10) ** 2
        if v > outer:
            return None
        return "edge" if v > outer - 0.9 else "fuller" if v < 1.5 else "blade"
    if 9.5 <= u < 11.5 and v <= -0.9:  # the back spike
        if v < -0.9 - 3.4 * (1 - abs(u - 10.5)):
            return None
        return "blade"
    return None


def axe(u, v):
    if 3 <= u < 15 and abs(v) < 0.9:
        return "socket"
    if 0.9 <= v:  # the great crescent blade, bearded below
        lo, hi = 8.2 - 0.85 * (v - 0.9), 12.2 + 0.6 * (v - 0.9)
        outer = 6.4 - 0.05 * (u - 10) ** 2
        if not lo <= u < hi or v > outer:
            return None
        return "edge" if v > outer - 1.0 else "fuller" if v < 1.6 else "blade"
    if 9.2 <= u < 11.8 and v <= -0.9:
        if v < -0.9 - 2.6 * (1 - abs(u - 10.5) / 1.3):
            return None
        return "blade"
    return None


SHAPES = {
    "sword_blade": straight(1.5, 4.0, 15.8, 18.2, 1.5),
    "longsword_blade": straight(0.0, 2.6, 17.0, 19.0, 1.4),
    "shortsword_blade": straight(3.0, 5.4, 13.0, 16.0, 1.5, leaf=0.7),
    "dagger_blade": straight(4.5, 6.8, 12.4, 14.8, 1.2),
    "sabre_blade": sabre, "spear_head": spear, "lance_tip": lance, "halberd_head": halberd,
    "two_handed_axe_head": axe,
}


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(metal):
    deep, base, light, shine = METALS[metal]
    return [mix(deep, (0, 0, 0), 0.45), deep, mix(deep, base, 0.5), base, mix(base, light, 0.5), light,
            mix(light, shine, 0.5), shine, mix(shine, (255, 255, 255), 0.5)]


def paint(part, metal):
    r = ramp(metal)
    rnd = random.Random(hash((part, metal)) & 0xffff)
    shape = SHAPES[part]
    mat = [[None] * 16 for _ in range(16)]
    vv = [[0.0] * 16 for _ in range(16)]
    s = 1 / math.sqrt(2)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - 1.5, y + 0.5 - 14.5
            u, v = (px - py) * s, (px + py) * s
            mat[y][x] = shape(u, v)
            vv[y][x] = v
    img = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    level = {"blade": 4.2, "edge": 6.6, "fuller": 2.6, "socket": 2.8}
    for y in range(16):
        for x in range(16):
            m = mat[y][x]
            if m is None:
                continue
            lit = 0.0
            # lit from the top-left: brighter where the pixel above / to the left is empty
            if y == 0 or mat[y - 1][x] is None or x == 0 or mat[y][x - 1] is None:
                lit += 0.9
            if y == 15 or mat[y + 1][x] is None or x == 15 or mat[y][x + 1] is None:
                lit -= 1.2
            n = level[m] + lit + rnd.uniform(-0.45, 0.45)
            if m == "blade" and rnd.random() < 0.06:
                n += 1.5  # a glint
            img[y][x] = r[max(0, min(len(r) - 1, int(round(n))))] + (255,)
    # the dark outline round it, as vanilla items have
    out = [row[:] for row in img]
    for y in range(16):
        for x in range(16):
            if mat[y][x] is not None:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and mat[y + dy][x + dx] is not None
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = r[0] + (255,)
    return out


def save_png(path, img):
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in img)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    h, w = len(img), len(img[0])
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def main():
    shapes = {}
    for weapon, (part, _, ingots) in PARTS.items():
        img = paint(part, "blacksteel")
        shapes[part] = {"weapon": weapon, "ingots": ingots, "rows": ["".join("#" if img[y][x][3] else "." for x in range(16)) for y in range(16)]}
    json.dump(shapes, open(RES + "forge_shapes.json", "w", encoding="utf-8"), indent=2)
    names = {}
    for metal in METALS:
        for weapon, (part, title, _) in PARTS.items():
            pid = f"{metal}_{part}"
            save_png(ASSETS + f"textures/item/{pid}.png", paint(part, metal))
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"divinis:item/{pid}"}},
                      open(ASSETS + f"models/item/{pid}.json", "w", encoding="utf-8"), indent=2)
            names[f"item.divinis.{pid}"] = f"{metal.capitalize()} {title}"

    p = ASSETS + "lang/en_us.json"
    text = open(p, encoding="utf-8").read()
    lines = [ln for ln in text.split("\n") if not any(f'"{k}"' in ln for k in names)]
    text = "\n".join(lines)
    anchor = '  "item.divinis.voidsteel_ingot": "Voidsteel Ingot",'
    assert anchor in text
    text = text.replace(anchor, anchor + "".join(f'\n  "{k}": "{v}",' for k, v in names.items()), 1)
    open(p, "w", encoding="utf-8").write(text)
    json.loads(text)

    armory = json.load(open(RES + "armory_default.json", encoding="utf-8"))
    part_ids = {f"{m}_{p}" for m in METALS for p, _, _ in PARTS.values()}
    entries = [e for e in armory["entries"] if e["id"] not in part_ids]
    by_id = {e["id"]: e for e in entries}
    for m in METALS:
        for weapon, (part, _, ingots) in PARTS.items():
            by_id[f"{m}_{weapon}"]["cost"] = [{"item": f"divinis:{m}_{part}", "count": 1}] + [
                {kind: id_, "count": c} for kind, id_, c in REST[weapon]]
    armory["entries"] = entries
    armory["version"] += 1
    json.dump(armory, open(RES + "armory_default.json", "w", encoding="utf-8"), indent=2, ensure_ascii=False)

    tree = json.load(open(RES + "technology_default.json", encoding="utf-8"))
    for n in tree["nodes"]:
        if n["era"] in METALS and n["id"] == f"{n['era']}_weapons":
            m = n["era"]
            n["unlocks"] = [x for x in n["unlocks"] if not any(x.endswith(f"{m}_{p}") for p, _, _ in PARTS.values())]
            n["unlocks"] += [f"item:divinis:{m}_{p}" for p, _, _ in PARTS.values()]
            if "Weapon parts: blades, heads and tips" not in n["features"]:
                n["features"].insert(0, "Weapon parts: blades, heads and tips")
    tree["version"] += 1
    json.dump(tree, open(RES + "technology_default.json", "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print(len(names), "parts; armory", armory["version"], "technology", tree["version"])


main()
