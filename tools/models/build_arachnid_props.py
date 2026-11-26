"""
Builds the Arachnid's two props in Blender, one model with two roots (client.ArachnidPropsModel shows one of them):
  egg     an egg sac of wound silk, the eggs dark through it, a cap that bulges when they are about to hatch
          (boss.ArachnidEgg)
  cocoon  the silk a player is wrapped in (boss.ArachnidCocoon), a little taller than a player, tied off at the top

Textures (128x128): arachnid_props.png (dense silk: the egg and the Arachnid's cocoon of the second phase) and
arachnid_web.png (the same, but a loose net of strands with holes, the web of the first phase: the player is seen
through it); and arachnid_silk.png, the thread the Arachnid hangs on.

  blender -b --python tools/models/build_arachnid_props.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/ArachnidPropsModelData.java.

Animations:
  pulse     1.0 loop  the egg sac breathing, its cap bulging
  hatch     1.0 loop  shaking hard (the last two seconds before it hatches)
  struggle  1.2 loop  the cocoon jerking as the one inside fights it
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 128, 128
PARTS = [
    ("egg", None, (0, 24, 0), (0, 0, 0)),
    ("egg_cap", "egg", (0, -10, 0), (0, 0, 0)),
    ("cocoon", None, (0, 24, 0), (0, 0, 0)),
    ("cocoon_top", "cocoon", (0, -26, 0), (0, 0, 0)),
]
CUBES = [[*c, None] for c in [
    ("egg_core", "egg", (-5, -10, -5), (10, 10, 10)),
    ("egg_side", "egg", (-6, -8.5, -4), (12, 7, 8)),
    ("egg_side2", "egg", (-4, -8.5, -6), (8, 7, 12)),
    ("egg_cap", "egg_cap", (-4, -2.5, -4), (8, 2.5, 8)),
    ("egg_knot", "egg_cap", (-1, -3.5, -1), (2, 1, 2)),
    ("coc_core", "cocoon", (-6, -26, -6), (12, 26, 12)),
    ("coc_side", "cocoon", (-7, -23, -5), (14, 20, 10)),
    ("coc_side2", "cocoon", (-5, -23, -7), (10, 20, 14)),
    ("coc_top", "cocoon_top", (-4.5, -4, -4.5), (9, 4, 9)),
    ("coc_knot", "cocoon_top", (-1.5, -6, -1.5), (3, 2, 3)),
]]
SILK = [(170, 164, 154), (196, 192, 184), (220, 218, 212), (240, 238, 234)]
EGG = [(92, 84, 62), (116, 106, 78)]


def paint(tex, web, rnd):
    for name, prt, origin, size, uv in CUBES:
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            for y in range(fh):
                for x in range(fw):
                    strand = (x + y * 2) % 5
                    level = 1.6 + (0.8 if face == "top" else -0.6 if face == "bottom" else 0) \
                        + (0.9 if strand == 0 else -0.5 if strand == 3 else 0) + rnd.uniform(-0.35, 0.35)
                    c = SILK[max(0, min(3, int(round(level))))]
                    if name.startswith("egg") and name not in ("egg_knot",) and (x * 3 + y * 5) % 7 == 0 and 0 < y < fh - 1:
                        c = EGG[rnd.randrange(2)]      # the eggs, dark through the silk
                    tex.set(x0 + x, y0 + y, c, 0.04)
                    # the web: only some strands, crossing; the rest is open
                    net = (x + y) % 3 == 0 or (x - y) % 4 == 0 or y == 0 or y == fh - 1
                    if net:
                        web.set(x0 + x, y0 + y, c, 0.04)
                    else:
                        web.set(x0 + x, y0 + y, None)


ANIMS = []


def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


def animations():
    anim("pulse", 0, 1.0, True, [
        k(0, "egg", (0, 0, 0), (0, 0, 0)), k(0.5, "egg", (2, 0, -2), (0, 0.3, 0)), k(1.0, "egg", (0, 0, 0), (0, 0, 0)),
        k(0, "egg_cap", (0, 0, 0), (0, 0, 0)), k(0.5, "egg_cap", (0, 20, 0), (0, 0.8, 0)), k(1.0, "egg_cap", (0, 0, 0), (0, 0, 0)),
    ])
    rnd = random.Random(4)
    hatch = [k(0, "egg", (0, 0, 0), (0, 0, 0)), k(0, "egg_cap", (0, 0, 0), (0, 0, 0))]
    for t in (0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9):
        hatch += [k(t, "egg", (rnd.uniform(-9, 9), rnd.uniform(-10, 10), rnd.uniform(-9, 9)), (0, 0, 0)),
                  k(t, "egg_cap", (rnd.uniform(-12, 12), 0, rnd.uniform(-12, 12)), (0, rnd.uniform(0.5, 1.5), 0))]
    hatch += [k(1.0, "egg", (0, 0, 0), (0, 0, 0)), k(1.0, "egg_cap", (0, 0, 0), (0, 0, 0))]
    anim("hatch", 40, 1.0, True, hatch)
    anim("struggle", 80, 1.2, True, [
        k(0, "cocoon", (0, 0, 0)), k(0.15, "cocoon", (4, 0, -3)), k(0.3, "cocoon", (-2, 0, 4)), k(0.45, "cocoon", (0, 0, 0)),
        k(0.8, "cocoon", (0, 0, 0)), k(0.9, "cocoon", (-3, 0, -2)), k(1.0, "cocoon", (2, 0, 2)), k(1.2, "cocoon", (0, 0, 0)),
        k(0, "cocoon_top", (0, 0, 0)), k(0.2, "cocoon_top", (-6, 0, 5)), k(0.4, "cocoon_top", (4, 0, -4)), k(0.6, "cocoon_top", (0, 0, 0)),
        k(1.2, "cocoon_top", (0, 0, 0)),
    ])


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, web = Tex(TEX_W, TEX_H, seed=3), Tex(TEX_W, TEX_H, seed=3)
    paint(tex, web, random.Random(8))
    tex.save(os.path.join(tex_dir, "arachnid_props.png"))
    web.save(os.path.join(tex_dir, "arachnid_web.png"))
    thread = Tex(4, 16, seed=6)   # the Arachnid's thread (client.ArachnidRenderer)
    for y in range(16):
        for x in range(4):
            thread.set(x, y, SILK[3 if x == 1 else 2 if x == 2 else 1], 0.05)
    thread.save(os.path.join(tex_dir, "arachnid_silk.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "arachnid_props.png"), TEX_W, TEX_H, "arachnid_props")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved arachnid props, rows", used)


main()
