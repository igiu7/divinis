"""
Builds the Camp of the Wanderer's skill book (skill.SkillEffects, client.SkillCampRenderer) in Blender: a ring of
stones round a bed of ash and embers, three crossed logs, flames (three crossed glowing planes), a pot hanging from
a bar between two forked posts, a rolled bedroll tied with straps on one side and a log to sit on on the other.
A block model (JSON elements, 32x32 texture) the renderer draws twice its size: about two blocks across.

  blender -b --python tools/models/build_skill_camp.py -- <out.blend> <texture.png>

then tools/models/export_item.py makes models/block/skill/camp.json. Scene layout: see itemmodel.py.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402


def px(x0, y0, x1, y1):
    """A rectangle of the 32x32 texture as model UVs (0..16)."""
    return [x0 / 2, y0 / 2, x1 / 2, y1 / 2]


STONE, STONE_TOP = px(0, 0, 8, 6), px(0, 6, 8, 8)
BARK, LOG_END = px(8, 0, 16, 8), px(16, 0, 20, 4)
ASH = px(20, 0, 24, 4)
STICK = px(24, 0, 26, 8)
EMBER = px(28, 0, 30, 2)
NONE = px(30, 6, 32, 8)  # left clear
FLAME = px(0, 8, 8, 16)
CLOTH, CLOTH_END = px(8, 8, 16, 14), px(8, 14, 12, 16)
STRAP = px(16, 8, 18, 14)
IRON, IRON_TOP = px(20, 8, 24, 12), px(20, 12, 24, 14)
FIRE_C = (8, 9)  # the fire's middle (x, z)


def stones():
    out = []
    for i in range(9):
        a = i / 9 * math.tau + 0.3
        x, z = FIRE_C[0] + math.cos(a) * 3.6, FIRE_C[1] + math.sin(a) * 3.6
        s = 1.5 if i % 3 else 1.9
        h = 1.1 if i % 2 else 1.4
        out.append(el(f"stone_{i}", [x - s / 2, 0, z - s / 2], [x + s / 2, h, z + s / 2], "y", [0, 22.5, -22.5][i % 3],
                      uv=STONE, faces={"up": STONE_TOP, "down": STONE_TOP}))
    return out


def log(name, frm, to, axis=None, angle=0.0, origin=None, along="x"):
    ends = {"east": LOG_END, "west": LOG_END} if along == "x" else {"north": LOG_END, "south": LOG_END}
    return el(name, frm, to, axis, angle, origin, uv=BARK, faces=ends)


def flame(name, angle):
    hidden = {f: NONE for f in ("east", "west", "up", "down")}
    return el(name, [5.6, 0.4, 8.95], [10.4, 5.2, 9.05], "y" if angle else None, angle, [8, 2, 9], uv=FLAME,
              faces=hidden, glow=15)


ELEMENTS = stones() + [
    el("ash", [5.2, 0, 6.2], [10.8, 0.25, 11.8], uv=ASH),
    el("ember_a", [6.2, 0.2, 7.4], [7.2, 0.6, 8.4], uv=EMBER, glow=15),
    el("ember_b", [8.8, 0.2, 9.8], [9.8, 0.6, 10.8], uv=EMBER, glow=15),
    el("ember_c", [7.6, 0.2, 10.4], [8.4, 0.5, 11.2], uv=EMBER, glow=15),
    log("log_a", [5.0, 0.3, 8.4], [11.0, 1.5, 9.6], "y", 22.5, [8, 0.9, 9]),
    log("log_b", [5.0, 1.2, 8.4], [11.0, 2.4, 9.6], "y", -22.5, [8, 1.8, 9]),
    log("log_c", [7.4, 0.3, 6.0], [8.6, 1.5, 12.0], along="z"),
    flame("flame_a", 0),
    flame("flame_b", 45),
    flame("flame_c", -45),
    # the pot on its bar between two forked posts
    el("post_w", [3.6, 0, 8.7], [4.2, 9.4, 9.3], uv=STICK),
    el("post_e", [11.8, 0, 8.7], [12.4, 9.4, 9.3], uv=STICK),
    el("fork_w", [3.2, 8.8, 8.75], [3.6, 10.0, 9.25], "z", 22.5, [3.6, 8.8, 9], uv=STICK),
    el("fork_e", [12.4, 8.8, 8.75], [12.8, 10.0, 9.25], "z", -22.5, [12.4, 8.8, 9], uv=STICK),
    el("bar", [3.4, 9.1, 8.75], [12.6, 9.6, 9.25], uv=STICK),
    el("chain", [7.85, 7.6, 8.85], [8.15, 9.1, 9.15], uv=IRON),
    el("pot", [6.9, 5.8, 7.9], [9.1, 7.6, 10.1], uv=IRON, faces={"up": IRON_TOP}),
    el("pot_rim", [6.7, 7.6, 7.7], [9.3, 7.9, 10.3], uv=IRON, faces={"up": IRON_TOP}),
    # the bedroll, east, and the log to sit on, south-west
    el("bedroll", [12.6, 0, 3.0], [15.4, 2.2, 14.0], uv=CLOTH, faces={"north": CLOTH_END, "south": CLOTH_END}),
    el("strap_n", [12.5, 0, 5.0], [15.5, 2.3, 5.6], uv=STRAP),
    el("strap_s", [12.5, 0, 11.4], [15.5, 2.3, 12.0], uv=STRAP),
    el("pillow", [12.9, 0, 0.8], [15.1, 1.4, 3.0], uv=CLOTH, faces={"north": CLOTH_END}),
    log("seat", [1.0, 0, 13.4], [8.0, 2.2, 15.6]),
]


def paint(tex):
    rnd = random.Random(4)

    def fill(x0, y0, x1, y1, pick):
        for y in range(y0, y1):
            for x in range(x0, x1):
                tex.set(x, y, pick(x - x0, y - y0), 0.0)

    fill(0, 0, 8, 6, lambda x, y: rnd.choice([(118, 118, 120), (104, 104, 108), (132, 130, 128), (92, 92, 96)]))
    fill(0, 6, 8, 8, lambda x, y: rnd.choice([(140, 140, 142), (126, 126, 130)]))
    fill(8, 0, 16, 8, lambda x, y: (78, 56, 32) if x % 3 == 0 else rnd.choice([(102, 74, 44), (110, 80, 48), (94, 68, 40)]))
    fill(16, 0, 20, 4, lambda x, y: (176, 136, 82) if 0 < x < 3 and 0 < y < 3 else (96, 70, 40))
    tex.set(17, 1, (150, 112, 66), 0.0)
    fill(20, 0, 24, 4, lambda x, y: rnd.choice([(46, 42, 40), (58, 54, 50), (36, 32, 30), (120, 44, 20)]))
    fill(24, 0, 26, 8, lambda x, y: (128, 96, 58) if x == 0 else (104, 76, 44))
    fill(28, 0, 30, 2, lambda x, y: (255, 150, 40) if (x + y) % 2 else (255, 200, 80))
    # the flame: a tongue of fire, yellow at its heart, red at its edge, clear round it
    for y in range(8, 16):
        for x in range(8):
            h = (16 - y) / 8            # 0 at the bottom, 1 at the top
            half = 3.6 * (1 - h) ** 0.8 + 0.4 * math.sin(x * 2.1 + y)
            d = abs(x + 0.5 - 4) / max(0.3, half)
            if d > 1 or h > 0.95:
                tex.set(x, y, None)
                continue
            c = (255, 236, 140) if d < 0.35 and h < 0.6 else (255, 172, 40) if d < 0.7 else (226, 84, 24)
            tex.set(x, y, c, 0.0)
    fill(8, 8, 16, 14, lambda x, y: (150, 40, 36) if y % 3 else (118, 28, 26))
    fill(8, 14, 12, 16, lambda x, y: (176, 56, 46) if (x + y) % 2 else (130, 36, 30))
    fill(16, 8, 18, 14, lambda x, y: (84, 56, 32))
    fill(20, 8, 24, 12, lambda x, y: rnd.choice([(46, 46, 50), (56, 56, 62), (40, 40, 44)]))
    fill(20, 12, 24, 14, lambda x, y: (70, 110, 60) if 0 < x < 3 else (40, 40, 44))  # a stew in the pot


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    tex = Tex(32, 32)
    paint(tex)
    tex.save(tex_path)
    build(ELEMENTS, tex_path, "divinis:block/skill/camp", blend_path)
    print("saved", blend_path, tex_path)


main()
