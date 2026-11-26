"""
Builds the Serpent of Infinity in Blender - three models, one .blend each: its head, a body segment (drawn some 20
times along its length) and its tail. A cosmic worm-serpent of dark grey armour plates with dark purple accents:
- head: a long armoured skull, two horns swept back, a crest of spines, glowing purple eyes, a jaw full of teeth
  that drops open, and two great pincer-mandibles at the sides of the mouth;
- body: a ring of plates with a raised ridge, side plates, a belly plate and a purple fin along the back;
- tail: a tapering segment ending in a three-part stinger with fins.
128x128 textures and glow textures, and a second set of textures for its second phase (serpent_*_white.png): the
plates bone-white, the purple accents and glow the same. The models are centred on each segment (not standing on the ground); the
renderer (client/SerpentRenderer) draws them twice as big, turned along the serpent's body.

  blender -b --python tools/models/build_serpent.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/Serpent{Head,Body,Tail}ModelData.java from the three .blend files.

Animations:
  head: idle 2.0 s loop (jaw and mandibles twitch), bite 1.0 s (gapes, snaps shut at 0.5 s),
        roar 2.0 s (gapes wide, mandibles spread, shaking)
  body: pulse 2.0 s loop (the fin rises and falls)
  tail: wag 2.0 s loop (the stinger sways)
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX = 128

PLATE = (50, 50, 57)
PLATE_D = (32, 32, 38)
PLATE_L = (78, 78, 88)
SEAM = (22, 20, 28)
PURPLE = (74, 24, 112)
PURPLE_D = (46, 14, 72)
PURPLE_L = (118, 52, 170)
GLOW = (176, 96, 255)
GLOW_D = (96, 40, 160)
BONE = (196, 186, 170)
DARK_PLATES = (PLATE, PLATE_D, PLATE_L)
WHITE_PLATES = ((214, 212, 224), (166, 162, 184), (246, 244, 252))
MOUTH = (40, 8, 30)


def k(t, part, rot=None, pos=None, linear=False):
    return (t, part, rot, pos, linear)


# ---------------------------------------------------------------- head
HEAD_PARTS = [
    ("head", None, (0, 0, 0), (0, 0, 0)),
    ("jaw", "head", (0, 3, -3), (0, 0, 0)),
    ("mandible_left", "head", (8, 2, -10), (0, -18, 0)),
    ("mandible_right", "head", (-8, 2, -10), (0, 18, 0)),
    ("horn_left", "head", (5, -7, 0), (-22, 25, 12)),
    ("horn_right", "head", (-5, -7, 0), (-22, -25, -12)),
    ("frill", "head", (0, -2, 5), (-12, 0, 0)),
]
HEAD_CUBES = [[*c, None] for c in [
    ("skull", "head", (-7, -6, -8), (14, 10, 14)),
    ("forehead", "head", (-8, -8, -11), (16, 3, 13)),
    ("snout", "head", (-6, -5, -14), (12, 6, 6)),
    ("snout_tip", "head", (-4.5, -4, -19), (9, 4.5, 5)),
    ("nose_ridge", "head", (-1, -5.5, -18), (2, 1.5, 9)),
    ("cheek_left", "head", (7, -4, -10), (2, 7, 10)),
    ("cheek_right", "head", (-9, -4, -10), (2, 7, 10)),
    ("eye_left", "head", (7.5, -5, -9), (1.5, 2, 4)),
    ("eye_right", "head", (-9, -5, -9), (1.5, 2, 4)),
    ("crest_a", "head", (-0.75, -11, -8), (1.5, 4, 4)),
    ("crest_b", "head", (-0.75, -13, -3), (1.5, 6, 4)),
    ("crest_c", "head", (-0.75, -12, 2), (1.5, 5, 3)),
    ("jaw", "jaw", (-5.5, 0, -14), (11, 3, 15)),
    ("chin", "jaw", (-1, 2.5, -14), (2, 2, 3)),
    ("mandible_left", "mandible_left", (-1.5, -1.5, -12), (3, 3, 12)),
    ("mandible_left_tip", "mandible_left", (-3.5, -1.25, -16), (3, 2.5, 5)),
    ("mandible_right", "mandible_right", (-1.5, -1.5, -12), (3, 3, 12)),
    ("mandible_right_tip", "mandible_right", (0.5, -1.25, -16), (3, 2.5, 5)),
    ("horn_left", "horn_left", (-1.5, -1.5, 0), (3, 3, 10)),
    ("horn_left_mid", "horn_left", (-1, -1, 10), (2, 2, 6)),
    ("horn_left_tip", "horn_left", (-0.5, -0.5, 16), (1, 1, 5)),
    ("horn_right", "horn_right", (-1.5, -1.5, 0), (3, 3, 10)),
    ("horn_right_mid", "horn_right", (-1, -1, 10), (2, 2, 6)),
    ("horn_right_tip", "horn_right", (-0.5, -0.5, 16), (1, 1, 5)),
    ("frill", "frill", (-11, -9, 0), (22, 15, 2)),
    ("frill_rim", "frill", (-12, -10, 0.5), (24, 1.5, 1)),
    ("frill_spike_left", "frill", (10, -12, 0.5), (2, 4, 1)),
    ("frill_spike_right", "frill", (-12, -12, 0.5), (2, 4, 1)),
    ("frill_spike_mid", "frill", (-1, -14, 0.5), (2, 5, 1)),
]]

HEAD_ANIMS = []
HEAD_ANIMS.append(("idle", 0, 2.0, True, [
    k(0, "jaw", (0, 0, 0)), k(1, "jaw", (5, 0, 0)), k(2, "jaw", (0, 0, 0)),
    k(0, "mandible_left", (0, 0, 0)), k(0.5, "mandible_left", (0, -6, 0)), k(1.0, "mandible_left", (0, 0, 0)),
    k(1.5, "mandible_left", (0, -4, 0)), k(2, "mandible_left", (0, 0, 0)),
    k(0, "mandible_right", (0, 0, 0)), k(0.5, "mandible_right", (0, 6, 0)), k(1.0, "mandible_right", (0, 0, 0)),
    k(1.5, "mandible_right", (0, 4, 0)), k(2, "mandible_right", (0, 0, 0)),
]))
HEAD_ANIMS.append(("bite", 100, 1.0, False, [
    k(0, "jaw", (0, 0, 0)), k(0.3, "jaw", (48, 0, 0)), k(0.5, "jaw", (0, 0, 0), linear=True), k(1.0, "jaw", (0, 0, 0)),
    k(0, "mandible_left", (0, 0, 0)), k(0.3, "mandible_left", (0, -35, 0)), k(0.5, "mandible_left", (0, 12, 0), linear=True),
    k(1.0, "mandible_left", (0, 0, 0)),
    k(0, "mandible_right", (0, 0, 0)), k(0.3, "mandible_right", (0, 35, 0)), k(0.5, "mandible_right", (0, -12, 0), linear=True),
    k(1.0, "mandible_right", (0, 0, 0)),
    k(0, "head", (0, 0, 0)), k(0.3, "head", (-12, 0, 0)), k(0.5, "head", (10, 0, 0), linear=True), k(1.0, "head", (0, 0, 0)),
]))
roar = [k(0, "jaw", (0, 0, 0)), k(0.3, "jaw", (52, 0, 0)), k(1.6, "jaw", (52, 0, 0)), k(2.0, "jaw", (0, 0, 0)),
        k(0, "mandible_left", (0, 0, 0)), k(0.3, "mandible_left", (0, -38, 0)), k(1.6, "mandible_left", (0, -38, 0)),
        k(2.0, "mandible_left", (0, 0, 0)),
        k(0, "mandible_right", (0, 0, 0)), k(0.3, "mandible_right", (0, 38, 0)), k(1.6, "mandible_right", (0, 38, 0)),
        k(2.0, "mandible_right", (0, 0, 0)),
        k(0, "head", (0, 0, 0)), k(2.0, "head", (0, 0, 0))]
for i, t in enumerate((0.4, 0.6, 0.8, 1.0, 1.2, 1.4)):
    roar.append(k(t, "head", (-10 + (3 if i % 2 else -3), 4 if i % 2 else -4, 0)))
HEAD_ANIMS.append(("roar", 200, 2.0, False, roar))

# ---------------------------------------------------------------- body
BODY_PARTS = [("segment", None, (0, 0, 0), (0, 0, 0)), ("fin", "segment", (0, -8, -1), (0, 0, 0))]
# three overlapping rings of plates, like scales, each a little smaller than the one before
BODY_CUBES = [[*c, None] for c in [
    ("core", "segment", (-6, -5, -7), (12, 10, 14)),
    ("ring_a", "segment", (-8, -7, -8), (16, 14, 5)),
    ("ring_b", "segment", (-7.5, -6.5, -3), (15, 13, 5)),
    ("ring_c", "segment", (-7, -6, 2), (14, 12, 5)),
    ("belly", "segment", (-5, 6, -6), (10, 2, 12)),
    ("spike_left", "segment", (8, -2, -7), (4, 2, 2)),
    ("spike_right", "segment", (-12, -2, -7), (4, 2, 2)),
    ("fin", "fin", (-0.5, -8, -6), (1, 8, 12)),
    ("fin_ray", "fin", (-1, -9, -5), (2, 2, 3)),
]]
BODY_ANIMS = [("pulse", 0, 2.0, True, [
    k(0, "fin", (-8, 0, 0)), k(1, "fin", (10, 0, 0)), k(2, "fin", (-8, 0, 0)),
])]

# ---------------------------------------------------------------- tail
TAIL_PARTS = [("tail", None, (0, 0, 0), (0, 0, 0)), ("stinger", "tail", (0, 0, 7), (0, 0, 0))]
TAIL_CUBES = [[*c, None] for c in [
    ("core", "tail", (-5, -4, -7), (10, 8, 14)),
    ("ring_a", "tail", (-6.5, -5.5, -8), (13, 11, 5)),
    ("ring_b", "tail", (-6, -5, -2), (12, 10, 5)),
    ("sting_a", "stinger", (-3, -3, 0), (6, 6, 8)),
    ("sting_b", "stinger", (-2, -2, 8), (4, 4, 7)),
    ("sting_c", "stinger", (-1, -1, 15), (2, 2, 6)),
    ("sting_d", "stinger", (-0.5, -0.5, 21), (1, 1, 4)),
    ("sting_fin", "stinger", (-0.5, -7, 1), (1, 5, 10)),
    ("sting_fin_low", "stinger", (-0.5, 3, 3), (1, 3, 7)),
]]
TAIL_ANIMS = [("wag", 0, 2.0, True, [
    k(0, "stinger", (0, -16, 0)), k(1, "stinger", (0, 16, 0)), k(2, "stinger", (0, -16, 0)),
])]


# ---------------------------------------------------------------- painting
def painter(cubes, purple_parts, glow_parts):
    by = {c[0]: c for c in cubes}
    rnd = random.Random(17)

    def f(name):
        _, _, _, (w, h, d), (u, v) = by[name]
        return faces(u, v, w, h, d)

    def paint(tex, glow):
        for name in by:
            purple = any(name.startswith(pp) for pp in purple_parts)
            glowing = any(name.startswith(gp) for gp in glow_parts)
            for side, r in f(name).items():
                x0, y0, w, h = (int(round(n)) for n in r)
                for yy in range(h):
                    for xx in range(w):
                        n = rnd.random()
                        if purple:
                            # a smooth gradient, light on top
                            k = yy / max(1, h - 1)
                            c = PURPLE_L if k < 0.25 else PURPLE if k < 0.7 else PURPLE_D
                            if n < 0.06:
                                c = PURPLE_D
                        else:
                            # a bevelled plate: light upper edge, dark lower edge, darker sides
                            c = PLATE
                            if yy == 0 and side != "bottom":
                                c = PLATE_L
                            elif yy == h - 1 or xx == 0 or xx == w - 1:
                                c = PLATE_D
                            elif n < 0.05:
                                c = PLATE_D
                            elif n > 0.97:
                                c = PLATE_L
                        tex.set(x0 + xx, y0 + yy, c, 0.03)
                        # glowing seams: the rims of plates and purple parts
                        if glowing and ((not purple and yy == h - 1 and side in ("front", "back", "left", "right"))
                                        or (purple and yy < max(1, h // 3))):
                            glow.set(x0 + xx, y0 + yy, GLOW if purple else GLOW_D, 0.0)
                            if not purple:
                                tex.set(x0 + xx, y0 + yy, PURPLE, 0.0)
    return paint, f


def head_details(tex, glow, f):
    # eyes: glowing purple all over, a bright pupil
    for name in ("eye_left", "eye_right"):
        for r in f(name).values():
            tex.fill(r, GLOW, 0.0)
            glow.fill(r, GLOW, 0.0)
        x0, y0, w, h = (int(round(n)) for n in f(name)["left" if name == "eye_left" else "right"])
        glow.set(x0 + w // 2, y0, (240, 210, 255), 0.0)
        tex.set(x0 + w // 2, y0, (240, 210, 255), 0.0)
    # teeth along the jaw and under the snout, the mouth dark
    tex.fill(f("jaw")["top"], MOUTH, 0.05)
    tex.fill(f("snout")["bottom"], MOUTH, 0.05)
    for name, face in (("jaw", "top"), ("snout", "bottom")):
        x0, y0, w, h = (int(round(n)) for n in f(name)[face])
        for xx in range(0, w, 2):
            tex.set(x0 + xx, y0, BONE, 0.04)
            tex.set(x0 + xx, y0 + h - 1, BONE, 0.04)
        for yy in range(0, h, 2):
            tex.set(x0, y0 + yy, BONE, 0.04)
            tex.set(x0 + w - 1, y0 + yy, BONE, 0.04)
    for name in ("mandible_left_tip", "mandible_right_tip", "horn_left_tip", "horn_right_tip", "horn_left_mid", "horn_right_mid"):
        for r in f(name).values():
            tex.fill(r, BONE if "horn" in name else PURPLE_L, 0.05)


def build_one(blend, tex_path, glow_path, parts, cubes, anims, purple, glows, details=None):
    global PLATE, PLATE_D, PLATE_L
    used = pack(cubes, TEX)
    assert used <= TEX, f"texture too small: {used}"
    for plates, path in ((WHITE_PLATES, tex_path.replace(".png", "_white.png")), (DARK_PLATES, tex_path)):
        PLATE, PLATE_D, PLATE_L = plates
        tex, glow = Tex(TEX, TEX, seed=4), Tex(TEX, TEX, seed=4)
        paint, f = painter(cubes, purple, glows)
        paint(tex, glow)
        if details:
            details(tex, glow, f)
        tex.save(path)
    glow.save(glow_path)
    scene, objs = build_scene(parts, cubes, tex_path, TEX, TEX, os.path.basename(blend))
    for name, start, seconds, loop, keys in anims:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{s + round(sec * 20)}:{'loop' if lp else 'once'}" for n, s, sec, lp, _ in anims]
    scene["mc_preview_z"] = 24.0  # centred models: the middle is at model y 0
    scene.frame_start = 0
    scene.frame_end = max(s + round(sec * 20) for _, s, sec, _, _ in anims)
    scene.frame_set(0)
    save(blend)
    print("saved", blend, "rows", used)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]

    def t(n):
        return os.path.join(textures, n)

    build_one(os.path.join(blends, "serpent_head.blend"), t("serpent_head.png"), t("serpent_head_glow.png"),
              HEAD_PARTS, HEAD_CUBES, HEAD_ANIMS,
              {"crest", "mandible", "frill_rim", "frill_spike"},
              {"crest", "mandible", "frill", "forehead", "cheek"}, head_details)
    build_one(os.path.join(blends, "serpent_body.blend"), t("serpent_body.png"), t("serpent_body_glow.png"),
              BODY_PARTS, BODY_CUBES, BODY_ANIMS, {"fin", "spike"}, {"fin", "spike", "ring"})
    build_one(os.path.join(blends, "serpent_tail.blend"), t("serpent_tail.png"), t("serpent_tail_glow.png"),
              TAIL_PARTS, TAIL_CUBES, TAIL_ANIMS, {"sting_d", "sting_fin"}, {"sting_fin", "sting_d", "ring"})


main()
