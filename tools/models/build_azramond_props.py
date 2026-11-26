"""
Builds Azramond's props in Blender, one model with four roots (client.AzramondRenderer shows one of them):
  throne  his throne of bones: a seat of stacked bones, a tall back of ribs and spines crowned with skulls, armrests
          ending in skulls, legs of thigh bones (boss.LichThrone; he sleeps on it, breaks it at half health)
  bones   a heap of bones with a skull on top (boss.LichBones: it rises as a skeleton unless broken)
  ghost   a hooded wraith, its face a void with two green eyes, its robe trailing into rags, its arms reaching
          (boss.LichGhost)
  spike   a spike of bone, jagged, three blocks tall (boss.LichSpike: the dance of bones)

Texture (128x128) azramond_props.png in the Minecraft style and its glow azramond_props_glow.png (the eyes of the
skulls and the ghost).

  blender -b --python tools/models/build_azramond_props.py -- <out.blend> <texture dir>

then tools/models/export_model.py makes client/AzramondPropsModelData.java.

Animations:
  rattle  1.0 loop  the heap shaking, the skull rocking (about to rise)
  float   2.0 loop  the ghost bobbing, its rags and arms swaying
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 128, 128
PARTS, CUBES, MATERIAL = [], [], {}
_n = [0]


def part(name, parent, pivot, rot=(0, 0, 0)):
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))


def box(prt, origin, size, mat):
    _n[0] += 1
    name = f"{prt}_{_n[0]}"
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat


def throne():
    part("throne", None, (0, 24, 0))
    box("throne", (-8, -11, -6), (16, 3, 13), "bones")                  # the seat
    box("throne", (-8.5, -8, -6.5), (17, 1, 13.5), "bone_dark")
    for x in (-7, 6):                                                     # legs of thigh bones
        for z in (-5.5, 5):
            box("throne", (x - 0.2, -8, z - 0.2), (1.4, 8, 1.4), "bone")
            box("throne", (x - 0.6, -1.2, z - 0.6), (2.2, 1.2, 2.2), "bone")
    box("throne", (-8, -34, 5), (16, 23, 3), "ribs")                     # the back
    for x in (-7.5, -2.5, 2.5, 7.5):                                     # its spines
        box("throne", (x - 0.8, -38, 5.6), (1.6, 27, 1.6), "bone")
    for x in (-6, 0, 6):                                                  # skulls along the top
        box("throne", (x - 2, -40.5, 4.0), (4, 4, 4), "skull")
    box("throne", (-2.5, -45, 3.6), (5, 5, 5), "skull")
    for sx in (-1, 1):                                                    # armrests ending in skulls
        box("throne", (sx * 8.5 - 1.5, -16, -5), (3, 1.6, 10), "bone")
        box("throne", (sx * 8.5 - 0.8, -14.5, -4), (1.6, 3.5, 1.6), "bone")
        box("throne", (sx * 8.5 - 1.8, -18, -7.6), (3.6, 3.6, 3.6), "skull")


def bones():
    part("bones", None, (0, 24, 0))
    for (x, z, ang, ln) in ((-2, -1, 20, 7), (1.5, 1, -35, 6), (0, -2, 75, 7), (-1, 2, -70, 6), (2, -1.5, 50, 5)):
        b = f"bone{_n[0]}"
        part(b, "bones", (x, -0.7, z), (0, ang, 0))
        box(b, (-ln / 2, -0.6, -0.6), (ln, 1.2, 1.2), "bone")
        box(b, (-ln / 2 - 0.5, -0.9, -0.9), (1.2, 1.8, 1.8), "bone")
        box(b, (ln / 2 - 0.7, -0.9, -0.9), (1.2, 1.8, 1.8), "bone")
    box("bones", (-2.6, -2.6, -2.2), (5.2, 2.2, 4.4), "ribs")            # a broken rib cage
    part("bones_skull", "bones", (0.5, -3.0, -0.5), (-10, 25, 8))
    box("bones_skull", (-1.8, -3.4, -1.8), (3.6, 3.4, 3.6), "skull")


def ghost():
    part("ghost", None, (0, 24, 0))
    part("ghost_body", "ghost", (0, -8, 0))
    box("ghost_body", (-3.5, -9, -2.5), (7, 9, 5), "shroud")
    box("ghost_body", (-3.0, -15, -3.0), (6, 6, 6), "hood")
    box("ghost_body", (-2.2, -13.6, -3.2), (4.4, 4.2, 0.4), "void")      # the face: nothing but two eyes
    part("ghost_rags", "ghost_body", (0, 0, 0))
    box("ghost_rags", (-3.0, 0, -2.0), (6, 4, 4), "rags")
    box("ghost_rags", (-2.0, 4, -1.4), (4, 3, 2.8), "rags")
    for s, sx in (("l", 1), ("r", -1)):
        a = f"ghost_arm_{s}"
        part(a, "ghost_body", (sx * 3.6, -8, 0), (-70, 0, sx * -10))
        box(a, (-1.0, 0, -1.0), (2, 6, 2), "shroud")
        box(a, (-0.6, 6, -0.6), (1.2, 2, 1.2), "bone")


def spike():
    part("spike", None, (0, 24, 0))
    box("spike", (-1.6, -10, -1.6), (3.2, 10, 3.2), "bone")
    box("spike", (-1.1, -17, -1.1), (2.2, 7.5, 2.2), "bone")
    box("spike", (-0.6, -22, -0.6), (1.2, 5.5, 1.2), "bone")
    box("spike", (-2.4, -1.5, -2.4), (4.8, 1.5, 4.8), "bone_dark")       # the broken ground at its foot
    for (x, y, z) in ((1.2, -7, 0), (-1.3, -12, 0.4), (0.4, -15, -1.1)):
        box("spike", (x - 0.4 + (0.9 if x > 0 else -0.9), y, z - 0.4), (0.8, 2.2, 0.8), "bone")


BONE = [(118, 110, 92), (150, 142, 118), (180, 172, 146), (206, 198, 172), (226, 220, 198)]
SHROUD = [(30, 40, 36), (44, 60, 52), (62, 84, 72), (90, 116, 100)]
GREEN = (90, 255, 120)
FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.25, "bottom": -1.0}


def pick(r, level):
    return r[max(0, min(len(r) - 1, int(round(level))))]


def paint(tex, glow, rnd):
    for name, prt, origin, size, uv in CUBES:
        mat = MATERIAL[name]
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            x0, y0, fw, fh = (int(round(v)) for v in rect)
            light = FACE_LIGHT[face]
            for y in range(fh):
                for x in range(fw):
                    n = rnd.uniform(-0.3, 0.3)
                    c, g = None, None
                    if mat == "skull":
                        if face == "front" and y in (1, 2) and x in (0, 1, fw - 2, fw - 1) and fw >= 4:
                            c, g = (10, 12, 10), (GREEN if y == 2 and x in (1, fw - 2) else None)
                        elif face == "front" and y == fh - 1 and x % 2 == 0:
                            c = (40, 34, 28)
                        else:
                            c = pick(BONE, 2.2 + light + n)
                    elif mat in ("bone", "bones"):
                        joint = mat == "bones" and (x + y * 2) % 4 == 0
                        c = pick(BONE, 2.0 + light + n - (1.2 if joint else 0))
                    elif mat == "bone_dark":
                        c = pick(BONE, 0.6 + light + n)
                    elif mat == "ribs":
                        c = (14, 12, 16) if face in ("front", "back") and y % 3 == 1 else pick(BONE, 1.9 + light + n)
                    elif mat in ("shroud", "hood"):
                        c = pick(SHROUD, 1.5 + light + n - (0.8 if x % 3 == 0 else 0))
                    elif mat == "void":
                        c = (4, 6, 5)
                        if y == 1 and x in (1, fw - 2):
                            c, g = GREEN, GREEN
                    elif mat == "rags":
                        if y >= fh - 2 and (x * 3 + y) % 3 < y - fh + 3:
                            c = None
                        else:
                            c = pick(SHROUD, 1.2 + light + n)
                    if c is None:
                        tex.set(x0 + x, y0 + y, None)
                    else:
                        tex.set(x0 + x, y0 + y, c, 0.0)
                    if g:
                        glow.set(x0 + x, y0 + y, g, 0.0)


ANIMS = []


def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


def animations():
    rnd = random.Random(3)
    rt = [k(0, "bones", (0, 0, 0), (0, 0, 0)), k(0, "bones_skull", (0, 0, 0), (0, 0, 0))]
    for t in (0.1, 0.25, 0.4, 0.55, 0.7, 0.85):
        rt += [k(t, "bones", (rnd.uniform(-4, 4), rnd.uniform(-6, 6), rnd.uniform(-4, 4)), (0, rnd.uniform(-0.4, 0), 0)),
               k(t, "bones_skull", (rnd.uniform(-15, 15), rnd.uniform(-20, 20), rnd.uniform(-10, 10)), (0, rnd.uniform(-0.8, 0), 0))]
    rt += [k(1.0, "bones", (0, 0, 0), (0, 0, 0)), k(1.0, "bones_skull", (0, 0, 0), (0, 0, 0))]
    anim("rattle", 0, 1.0, True, rt)
    fl = []
    for t, a in ((0, 0), (0.5, 1), (1.0, 0), (1.5, -1), (2.0, 0)):
        fl += [k(t, "ghost_body", (4 * a, 0, 3 * a), (0, -1.0 * abs(a), 0)), k(t, "ghost_rags", (14 + 8 * a, 0, 6 * a)),
               k(t, "ghost_arm_l", (-6 * a, 0, 4 * a)), k(t, "ghost_arm_r", (6 * a, 0, -4 * a))]
    anim("float", 40, 2.0, True, fl)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blend_path, tex_dir = argv[0], argv[1]
    throne()
    bones()
    ghost()
    spike()
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    tex, glow = Tex(TEX_W, TEX_H, seed=3), Tex(TEX_W, TEX_H, seed=3)
    paint(tex, glow, random.Random(8))
    tex.save(os.path.join(tex_dir, "azramond_props.png"))
    glow.save(os.path.join(tex_dir, "azramond_props_glow.png"))
    scene, objs = build_scene(PARTS, CUBES, os.path.join(tex_dir, "azramond_props.png"), TEX_W, TEX_H, "azramond_props")
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(blend_path)
    print("saved azramond props, rows", used)


main()
