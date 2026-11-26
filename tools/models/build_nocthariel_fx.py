"""
Builds the textures of Nocthariel's effects, plain Python:

  python tools/models/build_nocthariel_fx.py <assets/divinis/textures dir>

- effect/maw.png (64 x 64): a mouth opening in the floor, seen from above - a black rim, a ring of white teeth
  pointing in, a red throat going down to black;
- effect/maw_jaw.png (32 x 32): a jaw seen from the side - a row of long pointed teeth on black gums, for the
  bite rising out of the floor (drawn as crossed panes);
- screenfx/mouth.png (128 x 16): a mouth in four frames side by side - shut, parting, open with teeth, screaming
  (the screen effect MOUTHS);
- entity/nocthariel_hand.png: Aurelion's dead hand made a shadow - every colour of entity/aurelion_hand.png
  turned to near-black by its brightness, the nails left black-red, the torn wrist glowing red.
"""
import math
import os
import struct
import sys
import zlib


def save_png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def load_png(path):
    """Reads the RGBA PNGs our own tools write (one IDAT, filter 0 on every row)."""
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    px, stride = [], w * 4 + 1
    for y in range(h):
        row = raw[y * stride + 1:(y + 1) * stride]
        px.append([tuple(row[x * 4:x * 4 + 4]) for x in range(w)])
    return w, h, px


def clamp(v, a=0.0, b=1.0):
    return max(a, min(b, v))


def maw(out):
    n = 64
    px = []
    for y in range(n):
        row = []
        for x in range(n):
            dx, dy = x - 31.5, y - 31.5
            d = math.hypot(dx, dy) / 31.5
            a = math.atan2(dy, dx)
            if d > 1:
                row.append((0, 0, 0, 0))
                continue
            # teeth: triangles round the rim pointing in
            tooth = (a / (2 * math.pi) * 18) % 1.0
            tip = 0.62 + 0.26 * abs(tooth - 0.5) * 2
            if d > 0.9:
                c = (8, 6, 10, 255)
            elif d > tip:
                c = (228, 220, 204, 255) if d > tip + 0.02 else (170, 160, 146, 255)
            else:
                k = d / 0.62
                c = (int(150 * k), int(4 * k), int(12 * k), 255)
            row.append(c)
        px.append(row)
    save_png(os.path.join(out, "effect", "maw.png"), n, n, px)

    n = 32
    jaw = []
    for y in range(n):
        row = []
        for x in range(n):
            t = (x % 8) / 8
            height = 1 - abs(t - 0.5) * 2  # each tooth a spike
            if y >= 26:
                row.append((10, 6, 10, 255))
            elif y > 26 - height * 24:
                edge = y < 26 - height * 24 + 2
                row.append((246, 240, 226, 255) if edge else (214, 204, 188, 255))
            else:
                row.append((0, 0, 0, 0))
        jaw.append(row)
    save_png(os.path.join(out, "effect", "maw_jaw.png"), n, n, jaw)


def mouths(out):
    w, h = 32, 16
    sheet = [[(0, 0, 0, 0)] * (w * 4) for _ in range(h)]
    for f, open_ in enumerate((0.0, 0.35, 0.8, 1.0)):
        for y in range(h):
            for x in range(w):
                u = (x - 15.5) / 15
                if abs(u) > 1:
                    continue
                lip = (1 - u * u) ** 0.7 * 7.0
                dy = y - 7.5
                if abs(dy) > lip + 1:
                    continue
                gap = lip * open_
                if abs(dy) <= gap:
                    # inside: teeth along the lips, the throat
                    from_lip = gap - abs(dy)
                    # pointed teeth: long in the middle of each, nothing at its sides
                    tooth = from_lip < (3.2 * open_ + 0.6) * (1 - abs((x % 4) - 1.5) / 2.0)
                    c = (230, 222, 206, 255) if tooth else ((40, 0, 4, 255) if f < 3 else (110, 0, 10, 255))
                else:
                    c = (6, 4, 8, 255) if abs(abs(dy) - gap) > 1 else (60, 8, 14, 255)
                sheet[y][f * w + x] = c
    save_png(os.path.join(out, "screenfx", "mouth.png"), w * 4, h, sheet)


def shadow_hand(out):
    w, h, px = load_png(os.path.join(out, "entity", "aurelion_hand.png"))
    res = []
    for row in px:
        r2 = []
        for r, g, b, a in row:
            if a == 0:
                r2.append((0, 0, 0, 0))
                continue
            lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255
            if r > 80 and g < 40:  # the torn wrist: glowing red
                r2.append((170, 10, 16, 255))
            elif lum < 0.12:  # the nails
                r2.append((40, 4, 8, 255))
            else:
                v = int(6 + lum * 22)
                r2.append((v, v - 2 if v > 2 else 0, v + 2, 255))
        res.append(r2)
    save_png(os.path.join(out, "entity", "nocthariel_hand.png"), w, h, res)


def main():
    out = sys.argv[1]
    maw(out)
    mouths(out)
    shadow_hand(out)
    print("nocthariel fx textures in", out)


main()
