"""
Paints the Wildfire's fire textures in pixel art (used by client/FireFx and the Wildfire's renderers):
- effect/wildfire_flame.png: 8 frames (16 x 24 each, side by side) of a looping flame tongue - noise rising through
  a tapering shape, cut into five flat colours (pale yellow core, yellow, orange, red, a dark red rim), hard edges,
  transparent around it (drawn with alpha, full light);
- effect/wildfire_glow.png: a pixel glow (16 x 16) - three stepped rings, dithered at the edges, colours
  premultiplied for additive light;
- effect/serpent_flame.png, effect/serpent_glow.png: the same in purple, for the Serpent of Infinity;
- effect/ronin_aura.png: the same tongue as the Ronin's dark aura (client.RoninRenderer) - see-through: a black core
  and a dark crimson rim, the alpha rising towards the core, so it darkens what is behind it like a smoky flame.

  blender -b --python tools/models/build_fire_textures.py -- <texture dir>
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex  # noqa: E402

FRAMES, FW, FH = 8, 16, 24
RNG = np.random.default_rng(12)


def periodic_noise(gx, gy, w, h, ox=0.0, oy=0.0):
    """Value noise on a gx x gy lattice that wraps around, sampled on w x h pixels, shifted by (ox, oy) cells."""
    lattice = RNG.random((gy, gx))
    xs = (np.arange(w) / w * gx + ox)
    ys = (np.arange(h) / h * gy + oy)
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    fx = xs - x0
    fy = ys - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    x0 %= gx
    y0 %= gy
    x1 = (x0 + 1) % gx
    y1 = (y0 + 1) % gy
    a = lattice[np.ix_(y0, x0)]
    b = lattice[np.ix_(y0, x1)]
    c = lattice[np.ix_(y1, x0)]
    d = lattice[np.ix_(y1, x1)]
    top = a + (b - a) * fx[None, :]
    bottom = c + (d - c) * fx[None, :]
    return top + (bottom - top) * fy[:, None]


def flame_frames():
    # the same lattices for every frame, scrolled upwards by exactly one period over the loop
    octaves = [(4, 6, 0.6), (8, 12, 0.4)]
    lattices = []
    for gx, gy, amp in octaves:
        state = RNG.bit_generator.state
        lattices.append((gx, gy, amp, state))
        RNG.random((gy, gx))
    frames = []
    for f in range(FRAMES):
        t = f / FRAMES
        n = np.zeros((FH, FW))
        for gx, gy, amp, state in lattices:
            RNG.bit_generator.state = state
            n += amp * periodic_noise(gx, gy, FW, FH, 0.0, t * gy)
        frames.append(n)
    return frames


# the pixel palettes, from the rim inwards: the Wildfire's fire and the Serpent of Infinity's purple flames
PALETTE = [(0.18, (122, 22, 12)), (0.38, (214, 58, 18)), (0.6, (255, 128, 24)), (0.84, (255, 204, 60)), (1.01, (255, 246, 190))]
# the Ronin's aura: (top, colour, alpha) - see-through, black at the heart, crimson at the edge
AURA = [(0.18, (70, 8, 16), 80), (0.38, (34, 4, 12), 130), (0.6, (16, 2, 8), 165), (0.84, (8, 0, 4), 195), (1.01, (0, 0, 0), 215)]
PURPLE = [(0.18, (52, 10, 84)), (0.38, (104, 28, 170)), (0.6, (156, 66, 240)), (0.84, (204, 140, 255)), (1.01, (246, 226, 255))]


def pixel(i, palette=PALETTE):
    """Intensity 0..1 -> a palette colour with full alpha, or nothing."""
    if i < 0.07:
        return (0, 0, 0, 0)
    for entry in palette:
        if i < entry[0]:
            return entry[1] + ((entry[2],) if len(entry) > 2 else (255,))
    last = palette[-1]
    return last[1] + ((last[2],) if len(last) > 2 else (255,))


def main():
    out = sys.argv[sys.argv.index("--") + 1:][0]
    os.makedirs(out, exist_ok=True)

    frames = flame_frames()
    for palette, name in ((PALETTE, "wildfire_flame.png"), (PURPLE, "serpent_flame.png"), (AURA, "ronin_aura.png")):
        tex = Tex(FW * FRAMES, FH)
        for f, noise in enumerate(frames):
            for y in range(FH):
                v = 1.0 - (y + 0.5) / FH  # 0 at the root, 1 at the tip
                width = 0.92 * (1.0 - v) ** 0.55 + 0.04
                for x in range(FW):
                    u = (x + 0.5) / FW * 2 - 1
                    nz = noise[y, x]
                    bend = (nz - 0.5) * 0.9 * v  # the higher, the more it licks sideways
                    width_here = width * min(1.0, 0.55 + v * 3.5)  # a rounded root
                    mask = max(0.0, 1.0 - abs(u + bend) / width_here)
                    mask = mask ** 0.8
                    i = mask * (0.45 + 0.85 * nz) - v * 0.7 + (1 - v) * 0.05
                    i = max(0.0, min(1.0, i))
                    tex.px[y][f * FW + x] = pixel(i, palette)
        tex.save(os.path.join(out, name))

    for rings, name in (([(2.5, (255, 200, 110)), (4.5, (200, 110, 40)), (6.5, (120, 50, 16)), (8.0, (60, 22, 8))], "wildfire_glow.png"),
                        ([(2.5, (220, 170, 255)), (4.5, (140, 70, 220)), (6.5, (70, 26, 120)), (8.0, (34, 10, 60))], "serpent_glow.png")):
        glow = Tex(16, 16)
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                c = (0, 0, 0)
                for radius, col in rings:
                    if r < radius:
                        c = col
                        break
                if r >= 6.5 and r < 8.0 and (x + y) % 2 == 1:
                    c = (0, 0, 0)  # dithered edge
                glow.px[y][x] = c + (255,)
        glow.save(os.path.join(out, name))
    print("fire textures in", out)


main()
