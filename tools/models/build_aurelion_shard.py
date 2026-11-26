"""
Builds a shard of Aurelion's shattered golden symbol in Blender: a jagged, broken-off piece of the ring with a bit
of a ray still on it, the break glowing. Its second phase has eight of them flying round it
(client/AurelionRenderer). 64x64 texture and glow texture.

  blender -b --python tools/models/build_aurelion_shard.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/AurelionShardModelData.java. No animations: the renderer spins it.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_aurelion as base  # noqa: E402
from mcmodel import Tex, build_scene, faces, pack, save  # noqa: E402

TEX = 64
PARTS = [("shard", None, (0, 0, 0), (0, 0, 0)), ("ray", "shard", (0, -2, 0), (0, 0, 24)), ("chip", "shard", (3, 2, 0), (0, 0, -35))]
CUBES = [
    ["arc", "shard", (-5, -2, -1.25), (10, 4, 2.5), None],
    ["arc_end", "shard", (4.5, -1.5, -1), (3, 3, 2), None],
    ["arc_break", "shard", (-6.5, -1.2, -1), (2, 2.6, 2), None],
    ["ray", "ray", (-1.2, -12, -0.9), (2.4, 11, 1.8), None],
    ["ray_tip", "ray", (-0.5, -15, -0.5), (1, 3.5, 1), None],
    ["chip", "chip", (-1.2, 0, -0.8), (2.4, 3.5, 1.6), None],
]
GLOWING = {"arc_break", "ray_tip"}


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    tex_path, glow_path = os.path.join(textures, "aurelion_shard.png"), os.path.join(textures, "aurelion_shard_glow.png")
    used = pack(CUBES, TEX)
    assert used <= TEX
    tex, glow = Tex(TEX, TEX, seed=2), Tex(TEX, TEX, seed=2)
    rnd = random.Random(3)
    for name, _, _, (w, h, d), (u, v) in CUBES:
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            fw, fh = max(1, fw), max(1, fh)
            base.gold(tex, x0, y0, fw, fh, rnd)
            if name in GLOWING:
                for yy in range(fh):
                    for xx in range(fw):
                        glow.set(x0 + xx, y0 + yy, (255, 220, 140), 0.0)
                        tex.set(x0 + xx, y0 + yy, (255, 236, 180), 0.02)
    tex.save(tex_path)
    glow.save(glow_path)
    scene, objs = build_scene(PARTS, CUBES, tex_path, TEX, TEX, "aurelion_shard.blend")
    scene["mc_anims"] = []
    scene["mc_preview_z"] = 24.0
    save(os.path.join(blends, "aurelion_shard.blend"))
    print("saved rows", used)


main()
