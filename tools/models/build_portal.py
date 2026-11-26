"""
Builds the dungeon portal in Blender: a round ring of 16 dark stone segments with four glowing rune stones on a
stone base, filled with three layers of swirling violet light (128x128 texture, about 3 blocks tall).

  blender -b --python tools/models/build_portal.py -- <out.blend> <texture.png>

then tools/models/export_model.py makes client/DungeonPortalModelData.java from the .blend (see mcmodel.py).
Animation: swirl 12 s loop - the light layers turn at different speeds, two of them against each other.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 128, 128
SEGMENTS = 16
OUTER = 24  # ring outer radius (pixels); the ring's centre is 24 pixels above the ground

PARTS = [("base", None, (0, 24, 0), (0, 0, 0)), ("frame", None, (0, 0, 0), (0, 0, 0))]
PARTS += [(f"seg{i}", "frame", (0, 0, 0), (0, 0, i * 360 / SEGMENTS)) for i in range(SEGMENTS)]
PARTS += [("swirl_a", "frame", (0, 0, 0), (0, 0, 0)), ("swirl_b", "frame", (0, 0, 0), (0, 0, 0)),
          ("swirl_c", "frame", (0, 0, 0), (0, 0, 0))]

CUBES = [
    ["base", "base", (-12, -4, -5), (24, 4, 10), None],
    ["base_step", "base", (-8, -6, -4), (16, 2, 8), None],
    ["swirl_a", "swirl_a", (-20, -20, -0.6), (40, 40, 0), None],
    ["swirl_b", "swirl_b", (-20, -20, 0), (40, 40, 0), None],
    ["seg", "seg0", (-5, -OUTER, -2), (10, 4, 4), None],
    ["rune", "seg0", (-3, -OUTER - 2, -3), (6, 7, 6), None],
]
# all segments share the first segment's texture, the top and side ones carry a rune stone; the third light layer
# shares the second one's texture
SHARED = [[f"seg_{i}", f"seg{i}", (-5, -OUTER, -2), (10, 4, 4), "seg"] for i in range(1, SEGMENTS)]
SHARED += [[f"rune_{i}", f"seg{i}", (-3, -OUTER - 2, -3), (6, 7, 6), "rune"] for i in (4, 12)]  # not at the bottom
SHARED += [["swirl_c", "swirl_c", (-20, -20, 0.6), (40, 40, 0), "swirl_b"]]

STONE = (64, 62, 78)
STONE_D = (42, 40, 54)
STONE_L = (92, 90, 108)
GLOW = (176, 110, 255)
OBSIDIAN = (34, 24, 52)
RUNE = (110, 230, 255)
BASE = (98, 96, 104)
BASE_D = (70, 68, 76)


def paint(tex, cube):
    def f(name):
        _, _, _, (w, h, d), (u, v) = cube[name]
        return faces(u, v, w, h, d)

    # ring segments: dark stone blocks with mortar lines, the inner face glows
    seg = f("seg")
    for k, r in seg.items():
        tex.fill(r, STONE, 0.1)
    for k in ("front", "back"):
        r = seg[k]
        for col in range(10):
            tex.at(r, col, 0, STONE_L)
            tex.at(r, col, 3, GLOW if col % 3 else STONE_D, 0.08)
        tex.at(r, 0, 1, STONE_D)
        tex.at(r, 0, 2, STONE_D)
    tex.fill(seg["bottom"], GLOW, 0.12)  # the face towards the centre
    tex.fill(seg["top"], STONE_D, 0.1)

    # rune stones: obsidian with a glowing rune
    rn = f("rune")
    for k, r in rn.items():
        tex.fill(r, OBSIDIAN, 0.12)
    for k in ("front", "back"):
        r = rn[k]
        for (c, w) in ((2, 1), (3, 1), (2, 2), (2, 3), (3, 3), (3, 4), (2, 5), (3, 5), (1, 3), (4, 3)):
            tex.at(r, c, w, RUNE, 0.05)
    for k in ("left", "right"):
        r = rn[k]
        for row in range(1, 6):
            tex.at(r, 2 if row % 2 else 3, row, RUNE, 0.05)

    # base: stone bricks
    for name in ("base", "base_step"):
        for k, r in f(name).items():
            tex.fill(r, BASE, 0.08)
            x0, y0, w, h = (int(n) for n in r)
            for yy in range(h):
                for xx in range(w):
                    if yy % 2 == 1 and (xx + (yy // 2) * 3) % 6 == 0:
                        tex.set(x0 + xx, y0 + yy, BASE_D)

    # light layers: a round swirl that is clear outside the ring
    for name, arms, phase in (("swirl_a", 3, 0.0), ("swirl_b", 2, 1.3)):
        r = f(name)
        for k in ("front", "back"):
            x0, y0, w, h = (int(n) for n in r[k])
            for yy in range(h):
                for xx in range(w):
                    dx, dy = xx - w / 2 + 0.5, yy - h / 2 + 0.5
                    dist = math.hypot(dx, dy)
                    if dist > 20.5:
                        tex.px[y0 + yy][x0 + xx] = (0, 0, 0, 0)
                        continue
                    ang = math.atan2(dy, dx)
                    wave = 0.5 + 0.5 * math.sin(arms * ang + dist * 0.45 + phase)
                    core = max(0.0, 1.0 - dist / 9)
                    t = min(1.0, wave * 0.8 + core)
                    rgb = (int(60 + 140 * t), int(20 + 120 * t * t), int(140 + 115 * t))
                    alpha = int(255 * min(1.0, 0.35 + 0.45 * wave + core * 0.4) * (1.0 if dist < 18 else (20.5 - dist) / 2.5))
                    tex.px[y0 + yy][x0 + xx] = rgb + (max(0, min(255, alpha)),)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_path = argv[0], argv[1]
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows needed"
    by_name = {c[0]: c for c in CUBES}
    for c in SHARED:
        c[4] = by_name[c[4]][4]
    tex = Tex(TEX_W, TEX_H, seed=5)
    paint(tex, by_name)
    tex.save(tex_path)
    scene, parts = build_scene(PARTS, CUBES + SHARED, tex_path, TEX_W, TEX_H, "dungeon_portal")

    # swirl: 12 s; layer a turns 3 times, b twice the other way, c once
    keys = []
    for part, turns in (("swirl_a", 3), ("swirl_b", -2), ("swirl_c", 1)):
        steps = abs(turns) * 4
        for i in range(steps + 1):
            t = 12.0 * i / steps
            keys.append((t, part, (0, 0, math.copysign(90 * i, turns)), None, True))
    animation(parts, 0, keys)
    scene["mc_anims"] = ["swirl:0:240:loop"]
    scene.frame_start = 0
    scene.frame_end = 240
    scene.frame_set(0)
    save(blend_path)
    print("saved", blend_path, tex_path, "texture rows used:", used)


main()
