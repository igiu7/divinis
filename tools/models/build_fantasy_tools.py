"""
Builds the 16x16 item textures of the rare-metal tools (pickaxe, axe, shovel, hoe of each metal of build_ingots.py),
plain Python, pixel art in the vanilla layout - the handle running from the bottom-left corner up to the head at
the top-right - with something fantasy about each metal:
  blacksteel   plain dark steel on a dark wooden haft
  moonsilver   pale silver, a moonstone set where head meets haft, birch haft wrapped in silver
  crimsonite   red metal studded with blood-red crystals, black haft wrapped in red
  mithril      bright silver with a shine running along it, white haft wrapped in blue
  etherite     ghostly cyan with sparkles, a haft of pale crystal
  adamantite   dark green, hard-edged, a haft of dark leather
  dragonsteel  black-red with a gold spine down its back, a haft of bone
  divinite     white gold with a golden rim and a sun-stone, a haft of gold
  valorium     royal blue with gold trim and a sapphire, a haft wrapped in blue and gold
  voidsteel    black-violet full of stars, an amethyst, a haft of black

  python tools/models/build_fantasy_tools.py <textures/item dir> [preview.png]

makes <metal>_<tool>.png for every metal and tool (and, with a second path, all of them side by side, 8x as big).
"""
import math
import os
import random
import struct
import sys
import zlib

METALS = {
    # deep, base, light, shine; outline; gem; haft (dark, light); wrap
    "blacksteel": ((22, 22, 26), (52, 52, 60), (88, 90, 100), (150, 154, 166), (12, 12, 14), (120, 126, 140), ((44, 28, 18), (72, 48, 30)), (30, 30, 34)),
    "moonsilver": ((96, 110, 140), (156, 172, 204), (204, 218, 240), (246, 250, 255), (54, 62, 86), (190, 230, 255), ((150, 136, 110), (200, 188, 160)), (200, 210, 230)),
    "crimsonite": ((70, 10, 16), (128, 22, 30), (176, 44, 50), (236, 120, 120), (34, 4, 8), (255, 50, 70), ((24, 18, 20), (44, 34, 36)), (150, 20, 30)),
    "mithril": ((110, 132, 156), (178, 198, 216), (222, 234, 246), (255, 255, 255), (60, 74, 96), (140, 200, 255), ((196, 190, 178), (236, 232, 222)), (60, 110, 200)),
    "etherite": ((50, 140, 160), (100, 204, 218), (164, 238, 244), (232, 255, 255), (24, 80, 96), (230, 255, 255), ((120, 190, 200), (190, 240, 246)), (80, 170, 190)),
    "adamantite": ((14, 38, 28), (32, 76, 56), (60, 118, 88), (130, 196, 156), (6, 18, 12), (90, 220, 140), ((40, 30, 24), (66, 50, 38)), (24, 48, 36)),
    "dragonsteel": ((36, 8, 8), (82, 20, 18), (128, 36, 28), (196, 84, 54), (16, 4, 4), (255, 140, 40), ((200, 190, 166), (236, 228, 206)), (110, 24, 20)),
    "divinite": ((176, 136, 60), (232, 200, 116), (250, 234, 172), (255, 255, 240), (150, 100, 30), (255, 250, 200), ((180, 130, 40), (236, 196, 90)), (255, 240, 190)),
    "valorium": ((18, 32, 92), (34, 60, 152), (70, 104, 206), (150, 182, 255), (200, 150, 40), (80, 150, 255), ((30, 40, 90), (50, 64, 130)), (220, 170, 50)),
    "voidsteel": ((10, 4, 18), (30, 12, 48), (56, 28, 86), (130, 90, 190), (4, 0, 8), (190, 110, 255), ((14, 10, 18), (30, 24, 38)), (90, 50, 140)),
}
TOOLS = ("pickaxe", "axe", "shovel", "hoe")


def save_png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def head_mask(tool):
    """The metal of each tool, as a set of (x, y)."""
    m = set()
    for y in range(16):
        for x in range(16):
            if tool == "pickaxe":
                # a crescent round the top-right corner, thick in the middle, thin at the tips
                d = math.hypot(x - 2.5, y - 13.5)
                a = math.atan2(13.5 - y, x - 2.5)  # 0 = to the right, pi/2 = up
                if 0.05 < a < 1.52:
                    thick = 1.0 + 1.3 * math.sin((a - 0.05) / 1.47 * math.pi)
                    if 11.2 - thick <= d <= 11.8:
                        m.add((x, y))
            elif tool == "axe":
                # a blade up and to the left of the haft's top, its edge a curve
                u, v = x - 7.0, y - 4.5
                if math.hypot(u + 0.5, v) <= 4.6 and x <= 9 and x + y >= 7 and y <= 8:
                    m.add((x, y))
                if (x, y) in ((9, 5), (10, 5), (9, 6), (10, 4)):
                    m.add((x, y))
            elif tool == "shovel":
                # a spade at the top-right, along the haft's line
                u = (x - y) / 1.414 - (12 - 3.5) / 1.414
                v = (x + y) / 1.414 - (12 + 3.5) / 1.414
                if abs(u) <= 2.4 - max(0, v - 1.0) * 0.9 and -2.6 <= v <= 2.9:
                    m.add((x, y))
            else:
                # a hoe: a bar across the top, its blade hanging down at the left end
                if 3 <= y <= 4 and 5 <= x <= 12:
                    m.add((x, y))
                if 5 <= x <= 6 and 5 <= y <= 7:
                    m.add((x, y))
                if (x, y) in ((13, 4), (12, 5)):
                    m.add((x, y))
    return m


def haft(tool):
    """The haft: a two-pixel stair from the bottom-left corner up to the head."""
    top = {"pickaxe": 7, "axe": 9, "shovel": 9, "hoe": 10}[tool]
    out = []
    for i in range(top + 1):
        x, y = i, 15 - i
        if y < 0:
            break
        out.append((x, y))
    return out


def texture(metal, tool):
    deep, base, light, shine, outline, gem, (hdark, hlight), wrap = METALS[metal]
    rnd = random.Random(hash((metal, tool)) & 0xFFFF)
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    head = head_mask(tool)
    h = haft(tool)

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = tuple(max(0, min(255, int(v))) for v in c) + (255,)

    # haft first (the head goes over it)
    for n, (x, y) in enumerate(h):
        put(x, y, hlight)
        put(x + 1, y, hdark)
        if n % 3 == 1 and n > 1:
            put(x, y, wrap)  # wrapped
    put(0, 15, gem)  # a pommel stone
    # head: lit from the top-left - light where the pixel above-left is open, dark where below-right is
    for (x, y) in head:
        open_ul = (x - 1, y) not in head or (x, y - 1) not in head
        open_dr = (x + 1, y) not in head or (x, y + 1) not in head
        c = light if open_ul and not open_dr else deep if open_dr and not open_ul else base
        if open_ul and open_dr:
            c = light if (x + y) % 2 else base
        put(x, y, c)
    # a shine along the head
    for (x, y) in head:
        if (x - 1, y - 1) not in head and (x + 1, y + 1) in head and rnd.random() < 0.45:
            put(x, y, shine)
    # an outline round the head
    for (x, y) in list(head):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in head and 0 <= q[0] < 16 and 0 <= q[1] < 16 and px[q[1]][q[0]][3] == 0:
                put(q[0], q[1], outline)
    # where head meets haft: a stone
    joint = h[-1]
    put(joint[0] + 1, joint[1] - 1, gem)
    put(joint[0], joint[1], wrap)

    # each metal's own touch
    edge = [(x, y) for (x, y) in head if any((x + dx, y + dy) not in head for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    if metal == "crimsonite":
        for i, (x, y) in enumerate(sorted(edge)):
            if i % 3 == 0:
                put(x, y, (255, 60, 80) if rnd.random() < 0.5 else (200, 20, 40))
    elif metal == "voidsteel":
        for (x, y) in head:
            if rnd.random() < 0.14:
                put(x, y, (220, 200, 255))
    elif metal == "etherite":
        for (x, y) in head:
            if rnd.random() < 0.1:
                put(x, y, (255, 255, 255))
    elif metal == "dragonsteel":
        for i, (x, y) in enumerate(sorted(edge, key=lambda p: (p[0] - p[1]))):
            if i % 2 == 0 and (x - 1, y - 1) not in head:
                put(x, y, (230, 170, 60))
    elif metal == "mithril":
        for (x, y) in head:
            if (x - y) % 5 == 0 and rnd.random() < 0.7:
                put(x, y, (200, 230, 255))
    elif metal == "divinite":
        for (x, y) in edge:
            if (x + y) % 2 == 0:
                put(x, y, (255, 250, 220))
    elif metal == "adamantite":
        for (x, y) in head:
            if (x + 2 * y) % 4 == 0:
                put(x, y, deep)
    return px


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    sheet = [[(40, 40, 46, 255)] * (16 * len(TOOLS)) for _ in range(16 * len(METALS))]
    for r, metal in enumerate(METALS):
        for c, tool in enumerate(TOOLS):
            px = texture(metal, tool)
            save_png(os.path.join(out, f"{metal}_{tool}.png"), 16, 16, px)
            for y in range(16):
                for x in range(16):
                    if px[y][x][3]:
                        sheet[r * 16 + y][c * 16 + x] = px[y][x]
    if len(sys.argv) > 2:
        k = 8
        big = [[sheet[y // k][x // k] for x in range(len(sheet[0]) * k)] for y in range(len(sheet) * k)]
        save_png(sys.argv[2], len(big[0]), len(big), big)
    print("tool textures in", out)


main()
