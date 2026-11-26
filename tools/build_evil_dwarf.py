"""
The Evil Brother of the Guild Master: the Guild Master's own dwarf (textures/entity/guild_master.png, painted by
tools/models/build_dwarf.py) repainted - every pixel taken to the nearest colour of the Guild Master's palette and
given the brother's instead, its light kept: a blood-red tunic, a beard gone black and grey, a darker helmet, tarnished
brass for gold, red eyes; then brass goggles with red lenses over the eyes and a scar across the right cheek.

  python tools/build_evil_dwarf.py
"""
import struct
import zlib

T = "src/main/resources/assets/divinis/textures/entity/"

# the Guild Master's colour -> the brother's
REPAINT = {
    (226, 170, 130): (206, 150, 116), (196, 138, 102): (170, 112, 84), (214, 136, 108): (196, 118, 96),  # skin, nose
    (190, 92, 40): (58, 54, 52), (146, 64, 26): (34, 32, 32), (216, 122, 62): (92, 88, 84),               # beard
    (140, 146, 156): (74, 76, 84), (92, 96, 106): (46, 48, 54), (182, 188, 196): (104, 108, 116),        # helmet
    (232, 186, 64): (168, 120, 46), (172, 128, 36): (118, 82, 30),                                      # gold
    (232, 222, 196): (210, 196, 160), (184, 170, 144): (150, 132, 100),                                 # horns
    (46, 74, 140): (118, 22, 26), (30, 48, 98): (74, 12, 16),                                           # tunic
    (92, 58, 30): (52, 30, 18), (62, 38, 20): (34, 20, 12),                                             # belt
    (92, 78, 62): (52, 46, 40), (64, 42, 26): (40, 28, 18), (88, 60, 38): (60, 42, 28), (38, 26, 16): (24, 16, 10),
    (238, 238, 232): (238, 226, 210), (44, 62, 112): (200, 30, 24), (70, 34, 22): (40, 12, 10),
}


def load(path):
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        if t == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride = w * 4 + 1
    rows, prev = [], [0] * (w * 4)
    for y in range(h):
        f = raw[y * stride]
        line = list(raw[y * stride + 1:(y + 1) * stride])
        for i in range(len(line)):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        rows.append([list(line[x * 4:x * 4 + 4]) for x in range(w)])
    return w, h, rows


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def main():
    w, h, px = load(T + "guild_master.png")
    for row in px:
        for p in row:
            if p[3] == 0:
                continue
            src = min(REPAINT, key=lambda c: sum((c[i] - p[i]) ** 2 for i in range(3)))
            if sum((src[i] - p[i]) ** 2 for i in range(3)) > 60 ** 2:
                continue
            light = (sum(p[:3]) + 1) / (sum(src) + 1)
            dst = REPAINT[src]
            p[0], p[1], p[2] = (max(0, min(255, int(v * light))) for v in dst)
    # the head's front (the box layout: head 8x8x8 at 0,0 -> front at 8,8): brass goggles, red lenses, a scar
    fx, fy = 8, 8
    brass, brass_d, lens, lens_l = [176, 128, 52, 255], [116, 80, 30, 255], [150, 20, 16, 255], [236, 90, 70, 255]
    for col in range(8):
        px[fy + 2][fx + col] = brass_d if col in (0, 3, 4, 7) else brass
    for col in (1, 2, 5, 6):
        px[fy + 3][fx + col] = lens if col in (2, 5) else lens_l
    for col in (0, 3, 4, 7):
        px[fy + 3][fx + col] = brass
    for row in (2, 3, 4):
        px[fy + row][fx + 6 if row == 4 else fx + 7] = [140, 60, 52, 255]   # the scar
    # the goggles' strap round the sides of the head (right at 0,8, left at 16,8)
    for sx in (0, 16):
        for col in range(8):
            px[fy + 2][sx + col] = brass_d
    save(T + "evil_brother_dwarf.png", w, h, px)
    print("evil dwarf", w, "x", h)


main()
