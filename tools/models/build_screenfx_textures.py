"""
Builds the screen effects' textures (client/ScreenFxClient), plain Python, no Blender:

  python tools/models/build_screenfx_textures.py <textures/screenfx dir>

- eye.png: a pixel-art eye in four frames side by side (32 x 16 each): shut, half open, open, open and glowing -
  a pale almond with a dark rim, a golden iris and a black slit pupil;
- vignette.png: 256 x 256, white with alpha rising towards the edges (tinted when drawn);
- star.png: 8 x 8, a small white four-pointed star with a soft glow.
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

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


CLEAR = (0, 0, 0, 0)
RIM = (24, 10, 18, 255)
LID = (70, 40, 52, 255)
SCLERA = (232, 226, 214, 255)
SCLERA_D = (190, 180, 170, 255)
IRIS = (236, 176, 52, 255)
IRIS_D = (186, 108, 26, 255)
IRIS_L = (255, 226, 120, 255)
PUPIL = (12, 6, 10, 255)
GLOW = (255, 250, 220, 255)


def eye_frame(openness, glow):
    w, h = 32, 16
    px = [[CLEAR] * w for _ in range(h)]
    cx, cy = 15.5, 7.5
    half_w = 14.5
    for y in range(h):
        for x in range(w):
            u = (x - cx) / half_w
            if abs(u) > 1:
                continue
            lid = (1 - u * u) ** 0.8 * 7.0  # the almond's half height
            dy = y - cy
            if abs(dy) > lid + 1.0:
                continue
            opening = lid * openness
            if abs(dy) <= opening:
                # inside: sclera, iris, slit pupil
                r = math.hypot((x - cx) / 1.0, dy)
                if abs(x - cx) < 1.0 and abs(dy) < min(5.5, opening):
                    c = PUPIL
                elif r < 5.5:
                    c = IRIS_L if r > 4.6 and dy < 0 else IRIS_D if r > 4.6 else IRIS
                    if glow and r < 3.5:
                        c = GLOW
                else:
                    c = SCLERA_D if abs(dy) > opening - 1.2 else SCLERA
                px[y][x] = c
            elif abs(dy) <= lid + 1.0:
                px[y][x] = RIM if abs(abs(dy) - opening) < 1.2 or abs(dy) > lid else LID
    return px


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    frames = [eye_frame(0.0, False), eye_frame(0.45, False), eye_frame(1.0, False), eye_frame(1.0, True)]
    sheet = [[CLEAR] * (32 * 4) for _ in range(16)]
    for f, px in enumerate(frames):
        for y in range(16):
            for x in range(32):
                sheet[y][f * 32 + x] = px[y][x]
    save_png(os.path.join(out, "eye.png"), 128, 16, sheet)

    vig = []
    for y in range(256):
        row = []
        for x in range(256):
            d = math.hypot((x - 127.5) / 127.5, (y - 127.5) / 127.5) / math.sqrt(2)
            a = max(0.0, (d - 0.3) / 0.7) ** 1.6
            row.append((255, 255, 255, int(min(1.0, a * 1.6) * 255)))
        vig.append(row)
    save_png(os.path.join(out, "vignette.png"), 256, 256, vig)

    star = [[CLEAR] * 8 for _ in range(8)]
    for y in range(8):
        for x in range(8):
            dx, dy = abs(x - 3.5), abs(y - 3.5)
            a = 0
            if dx < 1 and dy < 4 or dy < 1 and dx < 4:
                a = 255 - int(max(dx, dy) * 40)
            elif dx < 2 and dy < 2:
                a = 120
            star[y][x] = (255, 255, 255, max(0, a)) if a else CLEAR
    save_png(os.path.join(out, "star.png"), 8, 8, star)
    print("screen fx textures in", out)


main()
