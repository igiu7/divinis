"""
Builds the 16x16 inventory icons of the fantasy armour sets (build_blacksteel_armor.py) in every metal, plain Python, in the
Minecraft manner: each icon is drawn as a map of materials (plate, gold, silver, leather, fur, horn, cloth, gem...),
then shaded with the same ramps as the armour textures - lit from the top-left (light upper and left edges, shadow on
the lower and right ones), a little dither - and given a dark outline. Each set has its own headpiece and its own
touches on the other three.

  python tools/models/build_armor_icons.py <textures/item dir> [preview.png]
"""
import os
import random
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

PLATE = [(8, 8, 11), (16, 16, 20), (26, 26, 32), (38, 38, 46), (52, 53, 63), (72, 74, 86), (104, 108, 122), (150, 154, 168)]
GOLD = [(60, 36, 8), (100, 62, 16), (142, 96, 26), (186, 136, 40), (218, 172, 62), (240, 206, 108), (252, 232, 160), (255, 248, 214)]
SILVER = [(50, 54, 64), (82, 88, 100), (118, 124, 138), (154, 160, 174), (186, 192, 206), (212, 218, 230), (236, 240, 248), (255, 255, 255)]
LEATHER = [(24, 14, 8), (38, 24, 14), (54, 36, 22), (72, 50, 30), (92, 64, 40), (114, 82, 52), (138, 102, 68), (164, 126, 88)]
FUR = [(22, 19, 17), (34, 29, 26), (48, 42, 37), (62, 55, 49), (80, 72, 64), (102, 92, 82), (128, 118, 106), (160, 150, 136)]
HORN = [(6, 5, 6), (12, 10, 12), (20, 17, 19), (30, 26, 29), (42, 37, 41), (58, 52, 57), (80, 74, 79), (108, 102, 108)]
SETS = {
    "vanguard": ((150, 24, 30), (90, 14, 18), (70, 150, 255)),
    "swordsman": ((24, 22, 28), (44, 42, 50), (210, 214, 230)),
    "paladin": ((40, 72, 164), (230, 226, 214), (255, 222, 120)),
    "spellblade": ((28, 34, 72), (44, 52, 100), (100, 190, 255)),
    "dragonknight": ((96, 16, 16), (60, 10, 10), (255, 70, 30)),
    "demonlord": ((112, 10, 18), (60, 6, 10), (255, 36, 44)),
    "ranger": ((48, 78, 42), (130, 104, 66), (190, 230, 150)),
}


# the metals (as build_ingots.py): deep, base, light, shine - the plate ramp of each is made from these
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


def metal_ramp(metal):
    deep, base, light, shine = METALS[metal]

    def mix(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return [tuple(int(c * 0.5) for c in deep), deep, mix(deep, base, 0.5), base, mix(base, light, 0.5), light, mix(light, shine, 0.5), shine]


def ramp_of(base):
    return [tuple(max(0, min(255, int(c * k + (20 if k > 1.3 else 0)))) for c in base) for k in (0.3, 0.45, 0.6, 0.78, 1.0, 1.2, 1.42, 1.7)]


# materials: P plate, X scale (plate tinged with the cloth), G gold, S silver, L leather, F fur, H horn, C cloth,
# K second cloth, E gem (shaded bright)
HELMETS = {
    "vanguard": [
        "................",
        "................",
        "S..............S",
        "SS............SS",
        "SSS..........SSS",
        ".SSSGGGGGGGGSSS.",
        "..GG........GG..",
        ".G............G.",
        ".G............G.",
        "..GG...EE...GG..",
        "....GGGEEGGG....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "swordsman": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "....KKKKKKKK....",
        "..KK........KK..",
        ".K............K.",
        ".K............K.",
        "..KK.SSSSSS.KK..",
        "....KSSSSSSKK...",
        "...........KK...",
        "............KK..",
        ".............KK.",
        "..............K.",
        "................",
    ],
    "paladin": [
        "................",
        ".......GG.......",
        "S.....PGGP.....S",
        "SS..PPPGGPPP..SS",
        "SSSPPPPPPPPPPSSS",
        ".SSPPPPPPPPPPSS.",
        "..PGGGGGGGGGGP..",
        "..PP........PP..",
        "..PP........PP..",
        "..PP........PP..",
        "..PPP......PPP..",
        "...PP......PP...",
        "................",
        "................",
        "................",
        "................",
    ],
    "spellblade": [
        "................",
        "................",
        "................",
        "S..............S",
        "SS..SSSSSSSS..SS",
        ".SSS...EE...SSS.",
        ".S.....EE.....S.",
        ".S............S.",
        "..SS........SS..",
        "....SSSSSSSS....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "dragonknight": [
        "................",
        "H..............H",
        "HH............HH",
        ".HH...HHHH...HH.",
        "..HXXXXXXXXXXH..",
        "..XXXXXXXXXXXX..",
        "..XXXXEXXEXXXX..",
        "..XXXXXXXXXXXX..",
        "..XX..XXXX..XX..",
        "..XX...HH...XX..",
        "..XXX......XXX..",
        "...XX......XX...",
        "................",
        "................",
        "................",
        "................",
    ],
    "demonlord": [
        "H..............H",
        "HH............HH",
        ".HH..........HH.",
        "..HH...G....HH..",
        "...HH.GGG..HH...",
        "...HGGGEGGGGH...",
        "....GGGGGGGG....",
        "...G........G...",
        "...G........G...",
        "....GGGGGGGG....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "ranger": [
        "................",
        "......CCCC......",
        "....CCCCCCCC....",
        "...CCCCCCCCCC...",
        "..CCCCCCCCCCCC..",
        "..CCC......CCC..",
        "..CC........CC..",
        "..CC........CC..",
        "..CC........CC..",
        "..CKK......KKC..",
        "..KKKKKKKKKKKK..",
        "...KKKKKKKKKK...",
        "................",
        "................",
        "................",
        "................",
    ],
}

CHEST = [
    "................",
    "..PPPP....PPPP..",
    ".PPPPPP..PPPPPP.",
    ".PPPPPPPPPPPPPP.",
    ".PPPPPPPPPPPPPP.",
    "..PPPPPPPPPPPP..",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "................",
    "................",
    "................",
]
LEGS = [
    "................",
    "...LLLLLLLLLL...",
    "...PPPPPPPPPP...",
    "...PPPPPPPPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...PPPP..PPPP...",
    "...GGGG..GGGG...",
    "...PPPP..PPPP...",
    "...PPPP..PPPP...",
    "..PPPPP..PPPPP..",
    ".PPPPPP..PPPPPP.",
    ".PPPPPP..PPPPPP.",
    "................",
    "................",
    "................",
    "................",
]


def grid(rows):
    return [list(r) for r in rows]


def put(g, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = ch


def fill(g, x0, y0, x1, y1, ch, only=None):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if only is None or g[y][x] in only:
                put(g, x, y, ch)


def chest(name):
    g = grid(CHEST)
    if name == "vanguard":
        fill(g, 6, 3, 9, 3, "G")
        fill(g, 7, 5, 8, 6, "G")
        put(g, 7, 5, "E")
        fill(g, 3, 11, 12, 12, "L", "P")
        for y in range(3, 15):
            put(g, 0, y, "C"), put(g, 15, y, "C")
        fill(g, 1, 2, 2, 2, "G"), fill(g, 13, 2, 14, 2, "G")
    elif name == "swordsman":
        fill(g, 0, 0, 15, 15, "K", "P")
        for y in range(3, 15):
            put(g, 6, y, "S"), put(g, 9, y, "S")
        fill(g, 3, 13, 5, 15, "K"), fill(g, 10, 13, 12, 15, "K")
        fill(g, 4, 5, 11, 7, "P", "K")
        fill(g, 1, 1, 6, 4, "P", "K")
        for i in range(8):
            put(g, 4 + i, 4 + i if 4 + i < 13 else 12, "L")
    elif name == "paladin":
        fill(g, 6, 5, 9, 7, "G")
        put(g, 7, 4, "G"), put(g, 8, 8, "G"), put(g, 5, 6, "G"), put(g, 10, 6, "G")
        fill(g, 5, 9, 10, 12, "C", "P")
        for y in range(3, 15):
            put(g, 0, y, "C"), put(g, 15, y, "C")
        fill(g, 1, 4, 14, 4, "G", "P")
    elif name == "spellblade":
        fill(g, 0, 0, 15, 15, "C", "P")
        fill(g, 4, 4, 11, 6, "P", "C")
        for y in range(3, 13):
            put(g, 7, y, "E"), put(g, 8, y, "E")
        for x, y in ((5, 0), (6, 0), (9, 0), (10, 0), (5, 1), (10, 1)):
            put(g, x, y, "C")
        fill(g, 1, 1, 6, 2, "P", "C"), fill(g, 9, 1, 14, 2, "P", "C")
    elif name == "dragonknight":
        fill(g, 0, 0, 15, 15, "X", "P")
        fill(g, 7, 5, 8, 6, "E")
        for x, y in ((1, 0), (2, 0), (13, 0), (14, 0)):
            put(g, x, y, "H")
    elif name == "demonlord":
        fill(g, 1, 1, 14, 2, "F")
        fill(g, 3, 3, 12, 3, "F", "P")
        fill(g, 7, 6, 8, 7, "E")
        for y in range(4, 13):
            put(g, 6, y, "G"), put(g, 9, y, "G")
        for y in range(3, 15):
            put(g, 0, y, "C"), put(g, 15, y, "C")
        put(g, 1, 0, "H"), put(g, 14, 0, "H")
    elif name == "ranger":
        fill(g, 0, 0, 15, 15, "L", "P")
        fill(g, 5, 4, 10, 6, "P", "L")
        fill(g, 1, 1, 6, 3, "C", "L"), fill(g, 9, 1, 14, 3, "C", "L")
        for i in range(9):
            put(g, 4 + i, 3 + i if 3 + i < 13 else 12, "K")
    return g


def legs(name):
    g = grid(LEGS)
    if name == "vanguard":
        fill(g, 3, 1, 12, 1, "L")
        put(g, 7, 1, "G"), put(g, 8, 1, "G")
        fill(g, 3, 8, 6, 8, "G", "P"), fill(g, 9, 8, 12, 8, "G", "P")
    elif name == "swordsman":
        fill(g, 0, 2, 15, 15, "K", "P")
        put(g, 7, 1, "S"), put(g, 8, 1, "S")
        fill(g, 3, 7, 6, 7, "L", "K"), fill(g, 9, 7, 12, 7, "L", "K")
    elif name == "paladin":
        fill(g, 3, 1, 12, 1, "G")
        fill(g, 5, 2, 10, 9, "C")
        fill(g, 3, 9, 6, 9, "G", "P"), fill(g, 9, 9, 12, 9, "G", "P")
    elif name == "spellblade":
        fill(g, 4, 2, 11, 13, "K")
        put(g, 7, 1, "E"), put(g, 8, 1, "E")
        fill(g, 4, 13, 11, 13, "E")
    elif name == "dragonknight":
        fill(g, 0, 2, 15, 15, "X", "P")
        fill(g, 3, 1, 12, 1, "P")
        put(g, 4, 8, "H"), put(g, 11, 8, "H")
    elif name == "demonlord":
        fill(g, 3, 1, 12, 1, "G")
        fill(g, 2, 2, 4, 13, "K"), fill(g, 11, 2, 13, 13, "K")
    elif name == "ranger":
        fill(g, 0, 2, 15, 15, "K", "P")
        put(g, 2, 2, "L"), put(g, 13, 2, "L"), put(g, 2, 3, "L"), put(g, 13, 3, "L")
        fill(g, 4, 7, 5, 8, "P", "K"), fill(g, 10, 7, 11, 8, "P", "K")
    return g


def boots(name):
    g = grid(BOOTS)
    cuff = {"vanguard": "G", "swordsman": "S", "paladin": "G", "spellblade": "E", "dragonknight": "X", "demonlord": "G", "ranger": "F"}[name]
    fill(g, 0, 6, 15, 6, cuff, "G")
    if name in ("swordsman", "ranger"):
        fill(g, 0, 0, 15, 15, "L", "P")
        if name == "swordsman":
            fill(g, 0, 8, 15, 8, "S", "L")
    if name == "dragonknight":
        fill(g, 0, 0, 15, 15, "X", "P")
        for x in (1, 3, 10, 12):
            put(g, x, 12, "H")
    if name == "demonlord":
        put(g, 2, 4, "H"), put(g, 13, 4, "H")
    if name == "spellblade":
        put(g, 0, 11, "P"), put(g, 15, 11, "P")
    return g


def render(name, g, metal="blacksteel"):
    cloth, cloth2, gem = SETS[name]
    PLATE = metal_ramp(metal)
    ramps = {"P": PLATE, "G": GOLD, "S": SILVER, "L": LEATHER, "F": FUR, "H": HORN, "C": ramp_of(cloth), "K": ramp_of(cloth2),
             "E": ramp_of(gem), "X": [tuple(int(a * 0.7 + b * 0.3) for a, b in zip(p, c)) for p, c in zip(PLATE, ramp_of(cloth))]}
    rnd = random.Random(hash(name) & 0xFFFF)
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    def at(x, y):
        return g[y][x] if 0 <= x < 16 and 0 <= y < 16 else "."

    for y in range(16):
        for x in range(16):
            m = g[y][x]
            if m == ".":
                continue
            level = 3.7 - y * 0.08
            if at(x, y - 1) != m:
                level += 1.6
            if at(x - 1, y) != m:
                level += 0.9
            if at(x, y + 1) != m:
                level -= 1.5
            if at(x + 1, y) != m:
                level -= 0.9
            level += (0.3 if (x + y) % 2 == 0 else -0.3) + rnd.uniform(-0.4, 0.4)
            if m == "E":
                level += 1.5
            if m == "F":
                level += rnd.uniform(-1.0, 1.0)
            if m == "X" and (x + (y // 2) % 2 * 2) % 4 == 0:
                level -= 1.0
            r = ramps[m]
            px[y][x] = r[max(0, min(7, int(round(level))))] + (255,)
    # a dark outline round it all
    out = [row[:] for row in px]
    for y in range(16):
        for x in range(16):
            if px[y][x][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16 and px[y + dy][x + dx][3]:
                    out[y][x] = (6, 6, 8, 255)
                    break
    return out


def save_png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    out = sys.argv[1]
    sheet = [[(60, 60, 66, 255)] * (64 * len(METALS)) for _ in range(16 * len(SETS))]
    for m, metal in enumerate(METALS):
        for r, name in enumerate(SETS):
            for c, (piece, g) in enumerate((("helmet", grid(HELMETS[name])), ("chestplate", chest(name)), ("leggings", legs(name)),
                                             ("boots", boots(name)))):
                px = render(name, g, metal)
                save_png(os.path.join(out, f"{metal}_{name}_{piece}.png"), 16, 16, px)
                for y in range(16):
                    for x in range(16):
                        if px[y][x][3]:
                            sheet[r * 16 + y][m * 64 + c * 16 + x] = px[y][x]
    if len(sys.argv) > 2:
        k = 8
        k = 4
        big = [[sheet[y // k][x // k] for x in range(len(sheet[0]) * k)] for y in range(len(sheet) * k)]
        save_png(sys.argv[2], len(big[0]), len(big), big)
    print("armour icons in", out)


main()
