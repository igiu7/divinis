"""
Paints the fishing rods (fishing.FishingRods) on the vanilla fishing rod: the same 16x16 shape, held and cast
(item/<id>.png, item/<id>_cast.png), each part repainted - the pole (four shades), the line (along the pole and hanging),
the reel, the hook - plus the rod's own marks on the pole (bands, a grip, nodes, a gem...).

  python tools/build_fishing_rods.py [minecraft resources jar] [out dir]

The jar defaults to the one the build downloads (build/moddev/artifacts/*-minecraft-resources.jar), the out dir to
the mod's textures/item. The Leviathan rod's Ancient Fish is the vanilla cod repainted (ancient_fish.png).
"""
import glob
import os
import struct
import sys
import zipfile
import zlib

# the vanilla texture's colours: the pole's four shades, the line's three greys, the reel's, the hook's white
POLE = {(40, 30, 11): 0, (73, 54, 21): 1, (104, 78, 30): 2, (137, 103, 39): 3}  # darkest .. lightest
LINE = {(68, 68, 68): 0, (100, 100, 100): 1, (116, 116, 116): 2}
REEL = {(53, 53, 53): 0, (150, 150, 150): 1, (180, 179, 179): 2}
HOOK = {(255, 255, 255): 1, (150, 150, 150): 0}
HOOK_AT = {(10, 13), (12, 13), (10, 14), (11, 14)}
REEL_AT = {(6, 11), (7, 11), (8, 11), (6, 12), (7, 12), (8, 12), (6, 13), (7, 13)}

# each rod: pole shades (dark .. light), line (dark, mid, light), reel (dark, mid, light), hook (shade, colour),
# and marks: [(where along the pole 0..1, colour ramp (dark, light))] painted across it
RODS = {
    "hazel_bronze_fishing_rod": dict(
        pole=[(46, 32, 16), (88, 60, 30), (126, 88, 46), (160, 116, 64)],
        line=[(84, 80, 70), (120, 114, 100), (140, 134, 120)],
        reel=[(92, 52, 22), (176, 104, 46), (220, 150, 84)],
        hook=[(150, 88, 40), (214, 144, 78)],
        marks=[(0.30, ((120, 66, 26), (204, 128, 60))), (0.78, ((120, 66, 26), (204, 128, 60)))]),
    "ash_iron_fishing_rod": dict(
        pole=[(70, 62, 48), (122, 110, 88), (166, 152, 124), (200, 188, 160)],
        line=[(80, 80, 80), (118, 118, 118), (140, 140, 140)],
        reel=[(70, 72, 76), (160, 164, 170), (214, 218, 222)],
        hook=[(150, 154, 160), (230, 232, 236)],
        marks=[(0.10, ((50, 30, 18), (92, 60, 36))), (0.18, ((50, 30, 18), (92, 60, 36))),
               (0.26, ((50, 30, 18), (92, 60, 36))), (0.72, ((96, 98, 104), (190, 194, 200)))]),
    "steel_fishing_rod": dict(
        pole=[(28, 18, 12), (56, 36, 22), (82, 54, 34), (108, 74, 48)],
        line=[(78, 82, 88), (112, 118, 126), (136, 142, 150)],
        reel=[(56, 64, 76), (136, 148, 164), (196, 208, 222)],
        hook=[(140, 150, 164), (220, 228, 238)],
        marks=[(0.34, ((150, 116, 40), (232, 196, 90))), (0.56, ((150, 116, 40), (232, 196, 90))),
               (0.80, ((150, 116, 40), (232, 196, 90)))]),
    "old_fisherman_rod": dict(
        pole=[(52, 46, 38), (92, 82, 66), (124, 112, 92), (150, 138, 116)],
        line=[(98, 92, 80), (132, 126, 110), (150, 144, 128)],
        reel=[(70, 40, 24), (132, 76, 40), (164, 104, 64)],
        hook=[(110, 70, 44), (170, 120, 80)],
        marks=[(0.22, ((140, 124, 88), (196, 180, 140))), (0.50, ((140, 124, 88), (196, 180, 140))),
               (0.66, ((60, 44, 30), (96, 74, 52)))]),
    "siren_rod": dict(
        pole=[(110, 30, 44), (176, 62, 78), (222, 106, 116), (250, 158, 160)],
        line=[(60, 140, 150), (100, 190, 196), (150, 226, 228)],
        reel=[(160, 150, 170), (226, 220, 236), (255, 252, 255)],
        hook=[(200, 190, 210), (255, 250, 255)],
        marks=[(0.40, ((220, 214, 230), (255, 255, 255))), (0.70, ((220, 214, 230), (255, 255, 255)))]),
    "staff_of_the_deep": dict(
        pole=[(110, 100, 80), (168, 156, 128), (212, 202, 174), (238, 232, 210)],
        line=[(24, 70, 74), (40, 104, 108), (62, 136, 138)],
        reel=[(16, 48, 54), (34, 96, 100), (70, 150, 150)],
        hook=[(200, 194, 170), (246, 242, 226)],
        marks=[(0.20, ((24, 70, 74), (40, 110, 112))), (0.48, ((24, 70, 74), (40, 110, 112))),
               (0.76, ((24, 70, 74), (40, 110, 112)))]),
    "gilded_shore_rod": dict(
        pole=[(110, 70, 10), (178, 128, 24), (226, 180, 50), (255, 228, 110)],
        line=[(160, 140, 90), (200, 180, 120), (226, 210, 150)],
        reel=[(130, 84, 14), (220, 170, 44), (255, 236, 130)],
        hook=[(200, 150, 40), (255, 230, 120)],
        marks=[(0.14, ((40, 120, 60), (90, 230, 130)))]),
    "stormcaller_rod": dict(
        pole=[(90, 44, 22), (150, 80, 44), (196, 116, 70), (226, 152, 100)],
        line=[(110, 170, 220), (170, 214, 255), (226, 242, 255)],
        reel=[(40, 110, 96), (74, 168, 146), (130, 214, 192)],
        hook=[(170, 214, 255), (240, 250, 255)],
        marks=[(0.36, ((40, 110, 96), (90, 190, 166))), (0.62, ((40, 110, 96), (90, 190, 166)))]),
    "patient_monk_rod": dict(
        pole=[(70, 88, 28), (116, 140, 50), (158, 182, 78), (196, 214, 120)],
        line=[(104, 96, 78), (140, 130, 108), (160, 150, 126)],
        reel=[(70, 50, 30), (128, 96, 60), (166, 130, 86)],
        hook=[(130, 120, 100), (190, 180, 160)],
        marks=[(0.16, ((50, 64, 20), (90, 110, 38))), (0.38, ((50, 64, 20), (90, 110, 38))),
               (0.60, ((50, 64, 20), (90, 110, 38))), (0.82, ((50, 64, 20), (90, 110, 38)))]),
    "pool_seeker_rod": dict(
        pole=[(70, 60, 50), (120, 108, 90), (156, 144, 122), (186, 176, 154)],
        line=[(40, 150, 170), (80, 210, 226), (150, 240, 250)],
        reel=[(90, 96, 104), (170, 178, 188), (226, 232, 240)],
        hook=[(80, 200, 220), (180, 250, 255)],
        marks=[(0.30, ((30, 110, 130), (70, 200, 220))), (0.64, ((110, 50, 160), (190, 120, 240)))]),
    "cursed_chain_rod": dict(
        pole=[(14, 10, 20), (36, 26, 46), (62, 44, 80), (96, 66, 120)],
        line=[(40, 40, 46), (120, 120, 130), (90, 90, 100)],
        reel=[(30, 26, 40), (90, 60, 120), (160, 100, 210)],
        hook=[(110, 60, 150), (190, 120, 240)],
        marks=[(0.12, ((90, 30, 130), (180, 80, 240))), (0.46, ((60, 60, 70), (140, 140, 150))),
               (0.74, ((60, 60, 70), (140, 140, 150)))]),
}


def ramp(dark, light, n):
    """n colours from dark to light."""
    return [tuple(int(round(dark[i] + (light[i] - dark[i]) * k / (n - 1))) for i in range(3)) for k in range(n)]


def rod(pole, line, reel, hook, marks=()):
    """A rod from (dark, light) pairs: the pole's four shades, the line's three, the reel's three, the hook's two."""
    return dict(pole=ramp(*pole, 4), line=ramp(*line, 3), reel=ramp(*reel, 3), hook=ramp(*hook, 2),
                marks=[(at, pair) for at, pair in marks])


GOLD, SILVER, IRON, BRASS = ((150, 104, 20), (255, 222, 110)), ((110, 116, 128), (236, 240, 248)), \
    ((70, 72, 76), (200, 204, 210)), ((120, 90, 30), (226, 190, 90))
RODS.update({
    "dawn_rod": rod(((90, 50, 40), (250, 180, 120)), ((170, 130, 130), (250, 214, 210)), GOLD, GOLD,
                    [(0.3, GOLD), (0.7, GOLD)]),
    "drowned_lantern_rod": rod(((30, 40, 36), (110, 130, 110)), ((30, 60, 44), (80, 120, 90)), BRASS, BRASS,
                               [(0.15, ((180, 110, 20), (255, 200, 80))), (0.85, ((180, 110, 20), (255, 200, 80)))]),
    "ice_hole_rod": rod(((60, 90, 120), (200, 235, 250)), ((190, 210, 230), (255, 255, 255)), ((70, 100, 140), (180, 210, 240)),
                        ((200, 220, 240), (255, 255, 255)), [(0.25, ((200, 230, 250), (255, 255, 255))), (0.6, ((200, 230, 250), (255, 255, 255)))]),
    "oasis_rod": rod(((110, 80, 40), (230, 200, 130)), ((40, 160, 150), (110, 230, 210)), ((40, 140, 130), (230, 200, 90)), GOLD,
                     [(0.4, ((30, 130, 120), (90, 220, 200))), (0.75, ((30, 130, 120), (90, 220, 200)))]),
    "rhythm_rod": rod(((60, 20, 20), (190, 80, 60)), ((170, 130, 40), (240, 210, 110)), ((20, 16, 14), (230, 190, 80)), GOLD,
                      [(0.2, GOLD), (0.4, GOLD), (0.6, GOLD), (0.8, GOLD)]),
    "twin_moons_rod": rod(((20, 24, 60), (110, 120, 200)), SILVER, SILVER, SILVER, [(0.35, SILVER), (0.7, SILVER)]),
    "quick_hand_rod": rod(((120, 110, 60), (240, 230, 170)), ((160, 30, 30), (240, 80, 70)), ((140, 20, 20), (240, 90, 80)), IRON,
                          [(0.5, ((150, 20, 20), (230, 70, 60)))]),
    "second_chance_rod": rod(((20, 70, 70), (100, 190, 170)), ((190, 200, 200), (255, 255, 255)), ((160, 80, 20), (250, 160, 60)),
                             ((200, 210, 210), (255, 255, 255)), [(0.3, ((170, 80, 20), (250, 150, 60))), (0.65, ((170, 80, 20), (250, 150, 60)))]),
    "golden_mean_rod": rod(((120, 110, 90), (250, 245, 225)), GOLD, GOLD, GOLD, [(0.5, GOLD)]),
    "water_charmer_rod": rod(((10, 40, 90), (80, 160, 230)), ((80, 210, 230), (180, 250, 255)), ((180, 190, 210), (250, 250, 255)),
                             ((80, 210, 230), (180, 250, 255)), [(0.25, ((40, 170, 200), (150, 240, 255))), (0.55, ((40, 170, 200), (150, 240, 255))),
                                                                (0.85, ((40, 170, 200), (150, 240, 255)))]),
    "devourer_rod": rod(((50, 10, 30), (170, 50, 90)), ((90, 10, 20), (170, 40, 50)), ((14, 10, 12), (70, 50, 60)),
                        ((210, 200, 180), (250, 245, 230)), [(0.2, ((210, 200, 180), (250, 245, 230))), (0.5, ((210, 200, 180), (250, 245, 230)))]),
    "pool_collector_rod": rod(((60, 30, 20), (170, 100, 70)), ((60, 180, 200), (160, 240, 250)), ((120, 60, 200), (250, 220, 90)), GOLD,
                              [(0.2, ((40, 170, 40), (120, 255, 120))), (0.4, ((40, 100, 200), (110, 170, 255))),
                               (0.6, ((120, 50, 200), (190, 120, 255))), (0.8, GOLD)]),
    "whirlpool_rod": rod(((40, 60, 80), (150, 190, 210)), ((100, 200, 230), (180, 250, 255)), ((150, 220, 240), (255, 255, 255)),
                         ((100, 200, 230), (180, 250, 255)), [(0.3, ((70, 190, 220), (160, 245, 255))), (0.5, ((70, 190, 220), (160, 245, 255))),
                                                            (0.7, ((70, 190, 220), (160, 245, 255)))]),
    "river_smith_rod": rod(((30, 28, 26), (110, 100, 90)), IRON, ((60, 40, 30), (255, 190, 90)), IRON, [(0.25, IRON), (0.75, IRON)]),
    "tanner_rod": rod(((60, 36, 20), (170, 120, 80)), ((140, 110, 70), (210, 180, 130)), ((180, 170, 140), (240, 232, 210)),
                      ((180, 170, 140), (240, 232, 210)), [(0.12, ((40, 24, 12), (90, 60, 36))), (0.2, ((40, 24, 12), (90, 60, 36)))]),
    "cook_rod": rod(((70, 60, 30), (180, 160, 90)), ((190, 190, 190), (255, 255, 255)), ((120, 60, 30), (230, 140, 80)),
                    ((120, 60, 30), (230, 140, 80)), [(0.6, ((150, 20, 20), (230, 70, 50)))]),
    "scholar_rod": rod(((20, 40, 40), (90, 140, 130)), ((190, 170, 130), (240, 225, 190)), GOLD, GOLD,
                       [(0.3, ((190, 170, 130), (240, 225, 190))), (0.7, GOLD)]),
    "treasurer_rod": rod(((20, 16, 12), (90, 70, 50)), GOLD, GOLD, GOLD, [(0.15, ((160, 20, 40), (255, 90, 100))), (0.5, GOLD)]),
    "hound_rod": rod(((50, 50, 52), (170, 170, 175)), ((90, 60, 36), (160, 120, 80)), ((70, 44, 26), (150, 100, 60)),
                     ((200, 190, 170), (245, 240, 225)), [(0.3, ((200, 200, 200), (250, 250, 250)))]),
    "harpooner_rod": rod(((70, 46, 24), (190, 140, 80)), ((140, 110, 70), (220, 190, 140)), IRON, IRON,
                         [(0.4, ((140, 110, 70), (220, 190, 140))), (0.7, ((140, 110, 70), (220, 190, 140)))]),
    "anchor_rod": rod(((20, 28, 50), (80, 100, 150)), ((140, 120, 90), (220, 200, 160)), IRON, IRON,
                      [(0.3, ((150, 20, 20), (230, 60, 60))), (0.6, ((200, 200, 200), (255, 255, 255)))]),
    "wanderer_rod": rod(((50, 34, 20), (150, 110, 70)), ((50, 110, 50), (120, 190, 110)), BRASS, BRASS,
                        [(0.25, ((40, 90, 40), (100, 170, 90)))]),
    "bloody_hook_rod": rod(((16, 10, 10), (80, 40, 40)), ((90, 10, 10), (200, 40, 40)), ((14, 10, 10), (200, 30, 30)),
                           ((200, 30, 30), (255, 80, 80)), [(0.5, ((140, 10, 10), (230, 40, 40))), (0.8, ((140, 10, 10), (230, 40, 40)))]),
    "gambler_rod": rod(((20, 60, 30), (80, 170, 100)), GOLD, ((180, 20, 30), (255, 255, 255)), GOLD,
                       [(0.3, GOLD), (0.6, ((170, 20, 30), (240, 60, 70)))]),
    "glutton_rod": rod(((100, 60, 30), (230, 170, 100)), ((170, 140, 100), (230, 210, 170)), ((90, 50, 26), (170, 110, 60)), IRON,
                       [(0.4, ((160, 30, 30), (230, 80, 60))), (0.7, ((50, 120, 40), (120, 200, 90)))]),
    "eternal_patience_rod": rod(((80, 80, 90), (220, 220, 230)), ((140, 120, 180), (210, 196, 240)), ((110, 90, 150), (220, 210, 250)),
                                SILVER, [(0.5, GOLD)]),
    "leviathan_rod": rod(((8, 24, 30), (50, 110, 120)), ((20, 60, 70), (60, 130, 140)), ((190, 184, 160), (240, 236, 220)),
                         ((190, 184, 160), (240, 236, 220)), [(0.2, GOLD), (0.5, ((20, 100, 110), (80, 200, 200))),
                                                             (0.8, ((20, 100, 110), (80, 200, 200)))]),
    "starlight_rod": rod(((10, 10, 30), (60, 60, 130)), ((200, 210, 240), (255, 255, 255)), SILVER, ((220, 230, 255), (255, 255, 255)),
                         [(0.2, ((220, 220, 255), (255, 255, 255))), (0.45, ((200, 180, 60), (255, 240, 140))),
                          (0.7, ((220, 220, 255), (255, 255, 255))), (0.9, ((200, 180, 60), (255, 240, 140)))]),
    "royal_angler_rod": rod(((40, 10, 60), (150, 70, 190)), GOLD, GOLD, GOLD,
                            [(0.15, GOLD), (0.5, ((220, 220, 220), (255, 255, 255))), (0.85, GOLD)]),
})
CHAIN_ROD = "cursed_chain_rod"  # its line is a chain: links of light and dark
# the Ancient Fish, the vanilla cod repainted: abyssal teal, its brightest scales gold
ANCIENT_FISH = [(6, 20, 28), (14, 52, 62), (26, 92, 100), (56, 150, 150), (120, 210, 200), (255, 214, 110)]


def decode(data):
    pos, idat = 8, b""
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif tag == b"IDAT":
            idat += body
    raw, stride, rows, prev, i = zlib.decompress(idat), w * 4, [], bytearray(w * 4), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b, c = prev[x], (prev[x - 4] if x >= 4 else 0)
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
        prev = line
    return rows


def save(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def along(x, y):
    """Where a pole pixel is along the pole: 0 at the butt (bottom left), 1 at the tip (top right)."""
    return max(0.0, min(1.0, ((x - y) + 12) / 24))


def paint(src, rod, cast, rod_id):
    out = []
    for y, row in enumerate(src):
        line = []
        for x, p in enumerate(row):
            rgb, a = p[:3], p[3]
            if a == 0:
                line.append((0, 0, 0, 0))
                continue
            if (x, y) in HOOK_AT and not cast and rgb in HOOK:
                c = rod["hook"][HOOK[rgb]]
            elif (x, y) in REEL_AT and rgb in REEL:
                c = rod["reel"][REEL[rgb]]
            elif rgb in POLE:
                shade = POLE[rgb]
                c = rod["pole"][shade]
                t = along(x, y)
                for at, (dark, light) in rod["marks"]:
                    if abs(t - at) < 0.03:
                        c = light if shade >= 2 else dark
            elif rgb in LINE:
                c = rod["line"][LINE[rgb]]
                if rod_id == CHAIN_ROD:
                    c = rod["line"][1] if (x + y) % 2 == 0 else rod["line"][0]
            else:
                c = rgb
            line.append(tuple(c) + (a,))
        out.append(line)
    return out


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.join(here, "..")
    jar = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else glob.glob(os.path.join(root, "build", "moddev", "artifacts", "*minecraft-resources.jar"))[0]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(root, "src", "main", "resources", "assets", "divinis", "textures", "item")
    with zipfile.ZipFile(jar) as z:
        held = decode(z.read("assets/minecraft/textures/item/fishing_rod.png"))
        cast = decode(z.read("assets/minecraft/textures/item/fishing_rod_cast.png"))
        cod = decode(z.read("assets/minecraft/textures/item/cod.png"))
    for rod_id, spec in RODS.items():
        save(os.path.join(out, rod_id + ".png"), paint(held, spec, False, rod_id))
        save(os.path.join(out, rod_id + "_cast.png"), paint(cast, spec, True, rod_id))
    save(os.path.join(out, "ancient_fish.png"), ancient(cod))
    print("saved", len(RODS), "rods and the Ancient Fish to", os.path.normpath(out))


def ancient(cod):
    """The cod's shades, darkest to lightest, onto ANCIENT_FISH."""
    shades = sorted({p[:3] for row in cod for p in row if p[3]}, key=lambda c: c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11)
    pick = {c: ANCIENT_FISH[min(len(ANCIENT_FISH) - 1, i * len(ANCIENT_FISH) // len(shades))] for i, c in enumerate(shades)}
    return [[pick[p[:3]] + (p[3],) if p[3] else (0, 0, 0, 0) for p in row] for row in cod]


if __name__ == "__main__":
    main()
