"""
Builds Nocthariel, Who Devoured the Dawn in Blender - pure black, a figure that light falls into: a tall, thin
body (chest, ribbed belly, a robe of tattered strips trailing below instead of legs), long arms ending in clawed
hands, black jagged wings, and no face - a ring of mouths round its head (and three more on top), each with lips,
a jaw that opens and rows of pointed teeth, a red glow down its throat; a crown of thorns. Its second phase grows
more: a second pair of arms, three pairs of wings, two great mouths on its chest, more thorns.

  blender -b --python tools/models/build_nocthariel.py -- <blend dir> <texture dir> [p2]

makes nocthariel(.blend/.png/_glow.png/_light.png) - or nocthariel_p2 with p2 - then tools/models/export_model.py
makes client/NoctharielModelData.java / NoctharielP2ModelData.java. The _light glow texture is the same body with
cracks of golden light all over it (when it is full of the sun it ate). Centred on its chest; client/NoctharielRenderer
draws it three times as big.

Animations (both): idle 3.0 s loop (it hovers, wings beat, every mouth chatters, the strips sway), devour 2.0 s
(arms reach up and drag down to its head, every mouth gaping), cast 1.5 s (arms spread, wings raised), scream 2.0 s
(every mouth wide, head shaking, arms thrown out), slam 1.0 s (arms up, then down hard), chant 2.0 s loop (arms
raised, mouths opening slowly), writhe 3.0 s (arms clutching its head, shaking, wings folded - the first phase,
before it changes); p2 also: emerge 3.0 s (the new arms and wings unfold out of it).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX = 256
BLACK, BLACK_L, BLACK_D = (10, 8, 12), (22, 18, 26), (4, 3, 5)
TOOTH, TOOTH_D = (224, 216, 200), (170, 160, 146)
THROAT, THROAT_GLOW = (110, 0, 10), (210, 24, 30)
LIGHT_CRACK = (255, 200, 90)


def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


def build(p2):
    parts = [("body", None, (0, 0, 0), (0, 0, 0))]
    cubes, kind = [], {}

    def cube(name, part, origin, size, what):
        cubes.append([name, part, origin, size, None])
        kind[name] = what

    # ---- body
    cube("chest", "body", (-5, -8, -3), (10, 10, 6), "body")
    cube("belly", "body", (-4, 2, -2.5), (8, 6, 5), "body")
    for i in range(3):
        cube(f"rib{i}", "body", (-4.6, -5 + i * 2.6, -3.3), (9.2, 1, 0.6), "body")
    cube("neck", "body", (-1.5, -11, -1.5), (3, 3.5, 3), "body")
    strips = []
    rnd = random.Random(7)
    for i in range(9):
        x = -4 + i * 1.0
        z = rnd.uniform(-2, 2)
        name = f"strip{i}"
        parts.append((name, "body", (x, 7.5, z), (rnd.uniform(-8, 8), 0, rnd.uniform(-6, 6))))
        cube(name, name, (-0.9, 0, -0.4), (1.8, rnd.uniform(10, 22), 0.8), "body")
        strips.append(name)

    # ---- head: no face, a ring of mouths
    parts.append(("head", "body", (0, -11, 0), (0, 0, 0)))
    cube("skull", "head", (-5.5, -11, -5.5), (11, 11, 11), "body")
    mouths = []

    def mouth(name, parent, pivot, rot, scale):
        parts.append((name, parent, pivot, rot))
        w = 4.8 * scale
        cube(name + "_lip", name, (-w / 2, -1.8 * scale, -1.2 * scale), (w, 1.3 * scale, 1.6 * scale), "body")
        cube(name + "_throat", name, (-w / 2 + 0.4, -0.6 * scale, -0.4 * scale), (w - 0.8, 1.6 * scale, 0.5), "throat")
        jaw = name + "_jaw"
        parts.append((jaw, name, (0, 0.3 * scale, 0), (0, 0, 0)))
        cube(jaw, jaw, (-w / 2, 0, -1.2 * scale), (w, 1.3 * scale, 1.6 * scale), "body")
        n = 5 if scale < 1.25 else 7
        for t in range(n):
            x = -w / 2 + 0.5 + t * (w - 1.0) / (n - 1)
            cube(f"{name}_tu{t}", name, (x - 0.25 * scale, -0.6 * scale, -1.1 * scale), (0.5 * scale, 1.3 * scale, 0.5 * scale), "tooth")
            cube(f"{name}_tl{t}", jaw, (x - 0.25 * scale, -1.1 * scale, -1.1 * scale), (0.5 * scale, 1.3 * scale, 0.5 * scale), "tooth")
        mouths.append((name, jaw))

    for i in range(6):
        a = math.radians(i * 60)
        mouth(f"mouth{i}", "head", (math.sin(a) * 5.6, -5.0, -math.cos(a) * 5.6), (0, -i * 60, 0), 1.3)
    for i in range(3):
        a = math.radians(i * 120 + 30)
        mouth(f"crownmouth{i}", "head", (math.sin(a) * 3.2, -10.6, -math.cos(a) * 3.2), (-55, -(i * 120 + 30), 0), 0.85)
    thorns = 7 if not p2 else 11
    for i in range(thorns):
        a = math.radians(i * 360 / thorns)
        name = f"thorn{i}"
        parts.append((name, "head", (math.sin(a) * 4.8, -10.5, -math.cos(a) * 4.8), (-math.cos(a) * 25, 0, math.sin(a) * 25)))
        cube(name, name, (-0.5, -6 - (i % 2) * 2, -0.5), (1, 6 + (i % 2) * 2, 1), "body")
    if p2:
        for side, s in (("left", 1), ("right", -1)):
            mouth(f"chestmouth_{side}", "body", (s * 2.6, -3, -3.1), (0, 0, s * 8), 1.1)

    # ---- arms, ending in claws
    arms = []

    def arm(name, pivot, rot, scale):
        parts.append((name, "body", pivot, rot))
        cube(name, name, (-1.2 * scale, 0, -1.2 * scale), (2.4 * scale, 12, 2.4 * scale), "body")
        fore = name + "_fore"
        parts.append((fore, name, (0, 12, 0), (-15, 0, 0)))
        cube(fore, fore, (-1 * scale, 0, -1 * scale), (2 * scale, 12, 2 * scale), "body")
        hand = name + "_hand"
        parts.append((hand, fore, (0, 12, 0), (0, 0, 0)))
        cube(hand, hand, (-1.6, 0, -0.9), (3.2, 2.5, 1.8), "body")
        for c, x in enumerate((-1.4, -0.5, 0.4, 1.3)):
            cube(f"{hand}_claw{c}", hand, (x - 0.3, 2.5, -0.6), (0.6, 7 - abs(c - 1.5), 0.6), "body")
        arms.append((name, fore, hand))

    for side, s in (("left", 1), ("right", -1)):
        arm(f"arm_{side}", (s * 5.5, -7, 0), (0, 0, -s * 18), 1.0)
        if p2:
            arm(f"arm2_{side}", (s * 5, -1, 0.5), (20, 0, -s * 42), 0.85)

    # ---- wings, black and jagged
    wings = []
    pairs = 3 if p2 else 1
    ups = [40, 8, -24] if p2 else [24]
    for side, s in (("left", 1), ("right", -1)):
        for i in range(pairs):
            name = f"wing_{side}{i}"
            parts.append((name, "body", (s * 3.5, -6 + i * 4, 3.5 + i * 0.5), (0, -s * 25, -s * ups[i])))
            length = 30 - i * 3
            cube(name, name, (0 if s > 0 else -length, -1.5, -1.5), (length, 3, 3), "wing")
            for j in range(8):
                f = f"{name}_f{j}"
                fl = 14 + j * 2.6 - i * 1.5
                parts.append((f, name, (s * (2 + j * (length - 3) / 7), 1, 0.5), (8, 0, -s * (4 + j * 6))))
                cube(f, f, (-2.2, 0, -0.5), (4.4, fl, 1), "wing")
                cube(f + "_tip", f, (-1, fl, -0.3), (1.6, 3.5, 0.6), "wing")
            wings.append((name, s, i))

    # ---------------------------------------------------------------- animations
    anims = []

    def mouths_chatter(keys, length, amp, speed, seed):
        r = random.Random(seed)
        for name, jaw in mouths:
            ph = r.random()
            steps = int(length * 4)
            for n in range(steps + 1):
                t = n * length / steps
                keys.append(k(t, jaw, (amp * (0.5 + 0.5 * math.sin((t * speed + ph) * math.pi * 2)), 0, 0)))

    idle = []
    for name, s, i in wings:
        for t in (0, 1, 2, 3):
            idle.append(k(t, name, (0, 0, -s * math.sin(t / 3 * math.pi * 2 + i) * 9)))
    for t in (0, 1.5, 3):
        idle.append(k(t, "head", (math.sin(t / 3 * math.pi * 2) * 4, 0, 0)))
    for n, st in enumerate(strips):
        for t in (0, 1, 2, 3):
            idle.append(k(t, st, (math.sin(t / 3 * math.pi * 2 + n) * 8, 0, math.cos(t / 3 * math.pi * 2 + n) * 5)))
    for name, fore, hand in arms:
        for t in (0, 1.5, 3):
            idle.append(k(t, name, (math.sin(t / 3 * math.pi * 2) * 6, 0, 0)))
    mouths_chatter(idle, 3.0, 22, 1.0, 1)
    anims.append(("idle", 0, 3.0, True, idle))

    devour = []
    for name, fore, hand in arms:
        devour += [k(0, name, (0, 0, 0)), k(0.5, name, (-170, 0, 0)), k(1.3, name, (-150, 0, 0)), k(1.6, name, (-120, 0, 0)), k(2.0, name, (0, 0, 0)),
                   k(0, fore, (0, 0, 0)), k(0.5, fore, (0, 0, 0)), k(1.3, fore, (-60, 0, 0)), k(2.0, fore, (0, 0, 0))]
    for name, jaw in mouths:
        devour += [k(0, jaw, (0, 0, 0)), k(0.4, jaw, (55, 0, 0)), k(1.5, jaw, (55, 0, 0)), k(1.65, jaw, (0, 0, 0)), k(2.0, jaw, (0, 0, 0))]
    anims.append(("devour", 70, 2.0, False, devour))

    cast = []
    for name, fore, hand in arms:
        out = -1 if name.endswith("left") else 1
        cast += [k(0, name, (0, 0, 0)), k(0.35, name, (-20, 0, out * 60)), k(1.2, name, (-20, 0, out * 60)), k(1.5, name, (0, 0, 0))]
    for name, s, i in wings:
        cast += [k(0, name, (0, 0, 0)), k(0.35, name, (0, s * 12, -s * 30)), k(1.2, name, (0, s * 12, -s * 30)), k(1.5, name, (0, 0, 0))]
    anims.append(("cast", 120, 1.5, False, cast))

    scream = []
    for name, jaw in mouths:
        scream += [k(0, jaw, (0, 0, 0)), k(0.2, jaw, (60, 0, 0)), k(1.7, jaw, (60, 0, 0)), k(2.0, jaw, (0, 0, 0))]
    scream.append(k(0, "head", (0, 0, 0)))
    for n in range(1, 18):
        scream.append(k(n * 0.1, "head", (-12 + (4 if n % 2 else -4), 4 if n % 2 else -4, 0)))
    scream.append(k(2.0, "head", (0, 0, 0)))
    for name, fore, hand in arms:
        out = -1 if name.endswith("left") else 1
        scream += [k(0, name, (0, 0, 0)), k(0.25, name, (-40, 0, out * 70)), k(1.7, name, (-40, 0, out * 75)), k(2.0, name, (0, 0, 0))]
    for name, s, i in wings:
        scream += [k(0, name, (0, 0, 0)), k(0.25, name, (0, s * 20, -s * 25)), k(1.7, name, (0, s * 20, -s * 25)), k(2.0, name, (0, 0, 0))]
    anims.append(("scream", 160, 2.0, False, scream))

    slam = []
    for name, fore, hand in arms:
        slam += [k(0, name, (0, 0, 0)), k(0.4, name, (-160, 0, 0)), k(0.55, name, (-30, 0, 0), linear=True), k(0.6, name, (10, 0, 0)), k(1.0, name, (0, 0, 0))]
    for name, jaw in mouths:
        slam += [k(0, jaw, (0, 0, 0)), k(0.55, jaw, (45, 0, 0)), k(1.0, jaw, (0, 0, 0))]
    anims.append(("slam", 210, 1.0, False, slam))

    chant = []
    for name, fore, hand in arms:
        out = -1 if name.endswith("left") else 1
        for t in (0, 1, 2):
            chant.append(k(t, name, (-130 + math.sin(t / 2 * math.pi * 2) * 8, 0, out * 20)))
    mouths_chatter(chant, 2.0, 40, 0.5, 3)
    chant += [k(0, "head", (-18, 0, 0)), k(2, "head", (-18, 0, 0))]
    anims.append(("chant", 240, 2.0, True, chant))

    writhe = []
    for name, fore, hand in arms:
        writhe += [k(0, name, (0, 0, 0)), k(0.5, name, (-150, 0, 0)), k(3.0, name, (-150, 0, 0)),
                   k(0, fore, (0, 0, 0)), k(0.5, fore, (-90, 0, 0)), k(3.0, fore, (-95, 0, 0))]
    for name, s, i in wings:
        writhe += [k(0, name, (0, 0, 0)), k(0.8, name, (0, s * 50, s * 10)), k(3.0, name, (0, s * 55, s * 12))]
    writhe.append(k(0, "body", (0, 0, 0)))
    for n in range(1, 30):
        writhe.append(k(n * 0.1, "body", (8 + (3 if n % 2 else -3), (3 if n % 3 else -3), (2 if n % 2 else -2))))
    writhe.append(k(3.0, "body", (8, 0, 0)))
    anims.append(("writhe", 290, 3.0, False, writhe))

    if p2:
        emerge = []
        new_arms = [a for a in arms if a[0].startswith("arm2")]
        for name, fore, hand in new_arms:
            emerge += [k(0, name, (-30, 0, 0), (0, -2, 4)), k(1.2, name, (-30, 0, 0), (0, -2, 4)), k(2.2, name, (20, 0, 0), (0, 0, 0)),
                       k(3.0, name, (0, 0, 0), (0, 0, 0)),
                       k(0, fore, (-150, 0, 0)), k(1.4, fore, (-150, 0, 0)), k(2.4, fore, (20, 0, 0)), k(3.0, fore, (0, 0, 0))]
        for name, s, i in wings:
            if i == 0:
                continue
            emerge += [k(0, name, (0, s * 60, s * 20), (0, 0, -2)), k(0.6 * i, name, (0, s * 60, s * 20), (0, 0, -2)),
                       k(0.6 * i + 1.4, name, (0, -s * 8, -s * 10), (0, 0, 0)), k(3.0, name, (0, 0, 0), (0, 0, 0))]
        for side in ("left", "right"):
            m = f"chestmouth_{side}"
            emerge += [k(0, m + "_jaw", (0, 0, 0)), k(2.0, m + "_jaw", (0, 0, 0)), k(2.3, m + "_jaw", (70, 0, 0)), k(3.0, m + "_jaw", (20, 0, 0))]
        anims.append(("emerge", 360, 3.0, False, emerge))
    return parts, cubes, kind, anims


def paint(cubes, kind, tex, glow, light):
    rnd = random.Random(19)
    for name, _, _, (w, h, d), (u, v) in cubes:
        what = kind[name]
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            fw, fh = max(1, fw), max(1, fh)
            for yy in range(fh):
                for xx in range(fw):
                    n = rnd.random()
                    if what == "tooth":
                        c = TOOTH if yy < fh - 1 or n < 0.5 else TOOTH_D
                    elif what == "throat":
                        c = THROAT
                        glow.set(x0 + xx, y0 + yy, THROAT_GLOW, 0.0)
                        light.set(x0 + xx, y0 + yy, THROAT_GLOW, 0.0)
                    else:
                        # pure black: only the faintest sheen on edges, so it reads as a hole in the world
                        c = BLACK_L if (yy == 0 and n < 0.4) else BLACK_D if n < 0.3 else BLACK
                        # cracks of light (seen when it has eaten the sun)
                        if what != "wing" and (xx * 3 + yy * 5 + hash(name) % 7) % 11 == 0 and n < 0.55:
                            light.set(x0 + xx, y0 + yy, LIGHT_CRACK, 0.0)
                        elif what == "wing" and n < 0.06:
                            light.set(x0 + xx, y0 + yy, LIGHT_CRACK, 0.0)
                    tex.set(x0 + xx, y0 + yy, c, 0.02)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    p2 = len(argv) > 2 and argv[2] == "p2"
    name = "nocthariel_p2" if p2 else "nocthariel"
    parts, cubes, kind, anims = build(p2)
    used = pack(cubes, TEX)
    assert used <= TEX, f"texture too small: {used}"
    tex, glow, light = Tex(TEX, TEX, seed=5), Tex(TEX, TEX, seed=5), Tex(TEX, TEX, seed=5)
    paint(cubes, kind, tex, glow, light)
    tex_path = os.path.join(textures, name + ".png")
    tex.save(tex_path)
    glow.save(os.path.join(textures, name + "_glow.png"))
    light.save(os.path.join(textures, name + "_light.png"))
    scene, objs = build_scene(parts, cubes, tex_path, TEX, TEX, name + ".blend")
    for an, start, seconds, loop, keys in anims:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in anims]
    scene["mc_preview_z"] = 24.0
    scene.frame_start = 0
    scene.frame_end = 430
    scene.frame_set(0)
    save(os.path.join(blends, name + ".blend"))
    print("saved", name, "rows", used, "parts", len(parts))


main()
