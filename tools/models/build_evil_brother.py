"""
Builds the Evil Brother of the Guild Master's steam mech in Blender (boss.EvilBrother), in the manner of Create's
contraptions: a walking engine of andesite casing, riveted steel plate and polished brass, full of turning cogs. The
dwarf himself is the Guild Master's model, drawn in the cockpit's 'seat' by client.EvilBrotherRenderer.

  feet        three clawed toes tipped with steel, a heel spur, a ball ankle
  shins       andesite casing, a riveted greave, a hydraulic piston behind, an octagonal knee with a cog
  thighs      two side bars round a core, a piston in front, an armour plate, a turning cog on the hip
  pelvis      andesite, brass skirt plates, the hip axle
  hull        andesite casing with brass trim, a glowing grate under a headlamp, a flywheel cog turning on each side;
              the cockpit: a padded seat with a headrest, two levers, a control panel with two dials, a pressure
              gauge with its needle, a brass-framed windshield (phase 1)
  boiler      an octagonal copper drum with brass bands and rivets, a steam dome and whistle, a valve wheel, a gauge,
              the firebox door at the back, pipes; two octagonal chimneys with bands and flared crowns
  arm_r       an octagonal shoulder with a cog, layered pauldrons (phase 1), a frame of bars round a piston, a cog on
              the elbow, an armoured forearm, the steam hammer: an octagonal head with brass bands, studs on its face and
              the steam cylinder over it
  arm_l       the same arm with the harpoon cannon: an octagonal copper barrel banded in brass, a flared muzzle, the
              breech, a sight, the chain drum, the harpoon in its mouth

Phase 2 (its engine lit) sheds the windshield, pauldrons and greaves: the grate and firebox glow, the chimney crowns
are red-hot, steam vents stand on its shoulders. Textures (512x512, and a glow texture) are painted per material:
brass and copper lit from above with brushed streaks and glints, copper with rivets and patina, steel plate with
bevelled edges and rivets, andesite casing framed as Create's is, chrome rods with a bright stripe, stitched leather,
glass, dials.

  blender -b --python tools/models/build_evil_brother.py -- <out dir> <texture dir> <phase 1|2>

writes evil_brother(_p2).blend and textures/entity/evil_brother(_p2)(_glow).png; then tools/models/export_model.py
makes client/EvilBrotherModelData.java (EvilBrotherP2ModelData.java). Both phases share the parts and animations
(boss/EvilBrother.java plays its attacks to them). Model space: the ground at y 24, the front -z, its right -x.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

TEX_W, TEX_H = 512, 512

PARTS = []          # (name, parent, absolute pivot, rest rotation)
ABS = {}            # name -> absolute pivot
ROTATED = set()     # parts whose frame is turned (cubes given in their own frame)
CUBES = []          # [name, part, origin (relative to the pivot), size, uv]
MATERIAL = {}
SPINNERS = []       # (part, axis): cogs turning in the idle and walk animations
_n = [0]


def uid(prefix):
    _n[0] += 1
    return f"{prefix}_{_n[0]}"


def part(name, parent, pivot, rot=(0, 0, 0)):
    assert parent is None or parent in ABS, parent
    ABS[name] = tuple(pivot)
    PARTS.append((name, parent, tuple(pivot), tuple(rot)))
    if any(rot):
        ROTATED.add(name)


def box(prt, frm, to, mat, name=None):
    """A cube in absolute coordinates (its part not turned)."""
    assert prt not in ROTATED, prt
    px, py, pz = ABS[prt]
    name = name or uid(prt)
    CUBES.append([name, prt, (frm[0] - px, frm[1] - py, frm[2] - pz), tuple(round(to[i] - frm[i], 3) for i in range(3)), None])
    MATERIAL[name] = mat
    return name


def lbox(prt, origin, size, mat):
    """A cube in its part's own frame (origin relative to the pivot)."""
    name = uid(prt)
    CUBES.append([name, prt, tuple(origin), tuple(size), None])
    MATERIAL[name] = mat
    return name


def octa(prt, center, dia, length, axis, mat, ratio=0.84):
    """An octagonal cylinder: a box and a second one turned 45 degrees about the axis (a child part)."""
    cx, cy, cz = center
    h = dia / 2
    half = length / 2
    if axis == "y":
        box(prt, (cx - h, cy - half, cz - h), (cx + h, cy + half, cz + h), mat)
        rot, origin, size = (0, 45, 0), (-h * ratio, -half, -h * ratio), (dia * ratio, length, dia * ratio)
    elif axis == "z":
        box(prt, (cx - h, cy - h, cz - half), (cx + h, cy + h, cz + half), mat)
        rot, origin, size = (0, 0, 45), (-h * ratio, -h * ratio, -half), (dia * ratio, dia * ratio, length)
    else:
        box(prt, (cx - half, cy - h, cz - h), (cx + half, cy + h, cz + h), mat)
        rot, origin, size = (45, 0, 0), (-half, -h * ratio, -h * ratio), (length, dia * ratio, dia * ratio)
    sub = uid(prt + "_oct")
    part(sub, prt, center, rot)
    lbox(sub, origin, size, mat)


def cog(name, parent, center, r, thick, teeth, axis, mat="brass", hub="steel", spokes=3, spin=True):
    """A cog: a hub, spokes and a rim of teeth, each tooth and rim segment a part turned round the axle."""
    part(name, parent, center)
    if spin:
        SPINNERS.append((name, axis))
    chord = 2 * r * math.sin(math.pi / teeth) * 1.08
    hub_d = max(1.6, r * 0.55)
    if axis == "x":
        lbox(name, (-thick / 2 - 0.3, -hub_d / 2, -hub_d / 2), (thick + 0.6, hub_d, hub_d), hub)
    else:
        lbox(name, (-hub_d / 2, -hub_d / 2, -thick / 2 - 0.3), (hub_d, hub_d, thick + 0.6), hub)
    for i in range(teeth):
        seg = f"{name}_t{i}"
        a = i * 360 / teeth
        part(seg, name, center, (a, 0, 0) if axis == "x" else (0, 0, a))
        rim = min(1.4, r * 0.3)
        if axis == "x":
            lbox(seg, (-thick / 2, -r, -chord / 2), (thick, rim, chord), mat)
            lbox(seg, (-thick / 2 + 0.15, -r - 1.1, -chord * 0.28), (thick - 0.3, 1.1, chord * 0.56), "teeth")
        else:
            lbox(seg, (-chord / 2, -r, -thick / 2), (chord, rim, thick), mat)
            lbox(seg, (-chord * 0.28, -r - 1.1, -thick / 2 + 0.15), (chord * 0.56, 1.1, thick - 0.3), "teeth")
    for i in range(spokes):
        sp = f"{name}_s{i}"
        a = i * 180 / spokes
        part(sp, name, center, (a, 0, 0) if axis == "x" else (0, 0, a))
        if axis == "x":
            lbox(sp, (-thick / 2 + 0.2, -r + 0.6, -0.5), (thick - 0.4, 2 * r - 1.2, 1.0), "dark")
        else:
            lbox(sp, (-0.5, -r + 0.6, -thick / 2 + 0.2), (1.0, 2 * r - 1.2, thick - 0.4), "dark")


def side(fn):
    """Builds a right-side thing (x negative) and its mirror on the left: fn(s, m, n) gets the side letter, a function
    mirroring x and a name helper."""
    for s, sign in (("r", 1), ("l", -1)):
        fn(s, lambda x, sg=sign: x * sg, lambda base, s_=s: f"{base}_{s_}")


def model(phase):
    p1 = phase == 1

    part("body", None, (0, -5, 0))
    part("hull", "body", (0, -6, 0))

    # ---------------------------------------------------------------- the legs
    def leg(s, m, n):
        part(n("leg"), "body", (m(-8.5), -4, 0))
        part(n("shin"), n("leg"), (m(-8.5), 7, 0))
        part(n("foot"), n("shin"), (m(-8.5), 21, 0))

        def b(prt, x0, y0, z0, x1, y1, z1, mat):
            xa, xb = min(m(x0), m(x1)), max(m(x0), m(x1))
            box(prt, (xa, y0, z0), (xb, y1, z1), mat)

        # thigh: side bars round a core, a piston in front, an armour plate, a cog on the hip
        b(n("leg"), -12, -3, -2.5, -11, 7, 2.5, "plate")
        b(n("leg"), -6, -3, -2.5, -5, 7, 2.5, "plate")
        b(n("leg"), -11, -2, -1.5, -6, 6, 1.5, "dark")
        b(n("leg"), -9.6, -2, -4, -7.4, 3.5, -2, "brass")
        b(n("leg"), -9, 3.5, -3.6, -8, 7.5, -2.4, "chrome")
        if p1:
            b(n("leg"), -12.5, -3.5, -4.6, -4.5, 3, -3.6, "plate")
        cog(n("hipgear"), n("leg"), (m(-13), -3.5, 0), 3.6, 1.4, 10, "x")
        # knee: an octagonal joint, a cog
        octa(n("shin"), (m(-8.5), 7, 0), 6.2, 7.4, "x", "dark")
        cog(n("kneegear"), n("shin"), (m(-12.6), 7, 0), 2.8, 1.0, 8, "x")
        # shin: casing, a narrower lower part, a greave (phase 1) or a bare piston, a hydraulic piston behind
        b(n("shin"), -11.5, 9, -3.5, -5.5, 15, 3.5, "andesite")
        b(n("shin"), -11, 15, -3, -6, 19.5, 3, "plate")
        if p1:
            b(n("shin"), -12, 8.5, -4.6, -5, 17, -3.5, "plate")
            b(n("shin"), -11.5, 17, -4.2, -5.5, 17.6, -3.2, "brass")
        else:
            b(n("shin"), -9.6, 9, -4.6, -7.4, 15, -3.4, "brass")
            b(n("shin"), -9, 15, -4.4, -8, 19, -3.6, "chrome")
        b(n("shin"), -9.6, 9, 3.4, -7.4, 15, 5.6, "brass")
        b(n("shin"), -9, 15, 3.9, -8, 19.5, 5, "chrome")
        # foot: ankle ball, base, sole, three toes turned down, a heel spur
        octa(n("foot"), (m(-8.5), 20.5, 0), 4.2, 4.4, "x", "dark")
        b(n("foot"), -12.5, 20.6, -6, -4.5, 23, 5, "plate")
        b(n("foot"), -13, 23, -7, -4, 24, 6, "rubber")
        b(n("foot"), -10.5, 21.2, 5, -6.5, 23.6, 8, "brass")
        for i, x in enumerate((-11.5, -8.5, -5.5)):
            t = n(f"toe{i}")
            part(t, n("foot"), (m(x), 21.4, -6), (14, 0, 0))
            lbox(t, (-1.3, 0, -5), (2.6, 2.4, 5), "brass")
            lbox(t, (-0.9, 1.1, -6.6), (1.8, 1.4, 1.8), "chrome")

    side(leg)

    # ---------------------------------------------------------------- the pelvis
    box("body", (-9, -9, -5), (9, -3, 5), "andesite")
    box("body", (-12, -6, -1.5), (12, -2, 1.5), "dark")
    box("body", (-7, -6, -6.4), (7, -1, -5.4), "brass")
    box("body", (-8, -7, 5), (8, -2, 6), "brass")
    for x0 in (-10, 9):
        box("body", (x0, -7.5, -4), (x0 + 1, -2, 4), "brass")
    box("body", (-1, -8, -6.6), (1, -2, -6.2), "dark")

    # ---------------------------------------------------------------- the hull and cockpit
    box("hull", (-12, -12, -8), (12, -6, 7), "andesite")
    box("hull", (-10, -6, -7), (10, -5, 6), "dark")
    for (a, b_) in (((-12.6, -12.6, -8.6), (12.6, -11.6, -7.6)), ((-12.6, -12.6, 6.6), (12.6, -11.6, 7.6)),
                    ((-12.6, -12.6, -7.6), (-11.6, -11.6, 6.6)), ((11.6, -12.6, -7.6), (12.6, -11.6, 6.6))):
        box("hull", a, b_, "brass")
    box("hull", (-6, -7.6, -8.6), (6, -6.2, -8), "hot_grate" if not p1 else "grate")
    part("headlamp", "hull", (0, -10, -9))
    octa("headlamp", (0, -10, -9.2), 4.2, 2.2, "z", "brass")
    box("headlamp", (-1.5, -11.5, -10.45), (1.5, -8.5, -10.3), "lamp")
    box("headlamp", (-2.6, -12.4, -11), (2.6, -11.8, -8.2), "brass")
    # the cockpit walls
    box("hull", (-12, -21, -1), (12, -12, 7), "plate")
    box("hull", (-12, -19, -8), (-7, -12, -1), "plate")
    box("hull", (7, -19, -8), (12, -12, -1), "plate")
    box("hull", (-12.5, -19.6, -8.5), (-6.5, -19, -0.5), "brass")
    box("hull", (6.5, -19.6, -8.5), (12.5, -19, -0.5), "brass")
    box("hull", (-12.5, -21.6, -1.5), (12.5, -21, 7.5), "brass")
    # the seat, the panel, the levers, the dials
    box("hull", (-5, -13.5, -6), (5, -12, -1), "leather")
    box("hull", (-5, -20, -2), (5, -13.5, -1), "leather")
    box("hull", (-5.5, -20.6, -2.2), (5.5, -20, -0.8), "brass")
    box("hull", (-3, -23, -1.8), (3, -20.6, -0.8), "leather")
    box("hull", (-7, -14.6, -8), (7, -12, -6.8), "brass")
    box("hull", (-5.4, -14.3, -8.25), (-3, -12.6, -8), "gauge")
    box("hull", (3, -14.3, -8.25), (5.4, -12.6, -8), "gauge")
    for s, x in (("r", -3.5), ("l", 3.5)):
        lv = f"lever_{s}"
        part(lv, "hull", (x, -13, -6.5))
        box(lv, (x - 0.45, -17, -6.95), (x + 0.45, -13, -6.05), "chrome")
        box(lv, (x - 0.9, -18, -7.4), (x + 0.9, -17, -5.6), "red")
    part("gauge", "hull", (9.5, -15.5, -8.6))
    box("gauge", (8, -17, -8.8), (11, -14, -8.3), "gauge")
    box("gauge", (7.7, -17.3, -8.6), (11.3, -13.7, -8.25), "brass")
    part("gauge_needle", "gauge", (9.5, -15.5, -9))
    box("gauge_needle", (9.38, -16.7, -9.05), (9.62, -15.5, -8.85), "red")
    part("seat", "hull", (0, -12, -4))
    if p1:
        box("hull", (-7, -17.2, -9.1), (7, -16.5, -8.4), "brass")
        box("hull", (-7, -17.2, -9.1), (-6.3, -14.5, -8.4), "brass")
        box("hull", (6.3, -17.2, -9.1), (7, -14.5, -8.4), "brass")
        box("hull", (-6.3, -16.5, -8.9), (6.3, -14.6, -8.6), "glass")
    # the flywheels turning on its sides, the shoulder axles
    side(lambda s, m, n: cog(n("flywheel"), "hull", (m(-12.9), -14.5, 2.5), 5.6, 1.4, 12, "x"))
    box("hull", (-16, -18.5, -2), (-12, -14.5, 2), "dark")
    box("hull", (12, -18.5, -2), (16, -14.5, 2), "dark")

    # ---------------------------------------------------------------- the boiler and chimneys
    part("boiler", "hull", (0, -8, 12))
    octa("boiler", (0, -20, 12.5), 16, 22, "y", "copper")
    for y in (-28, -21, -14):
        octa("boiler", (0, y, 12.5), 16.8, 1.3, "y", "brass")
    octa("boiler", (0, -32, 12.5), 8, 2.4, "y", "brass")
    octa("boiler", (0, -34, 12.5), 5, 1.8, "y", "copper")
    box("boiler", (-0.6, -37.5, 12), (0.6, -35, 13), "brass")
    box("boiler", (-4, -15.5, 20.4), (4, -9.5, 21.2), "hot" if not p1 else "dark")
    box("boiler", (-4.6, -16.1, 20.3), (4.6, -15.5, 21.4), "brass")
    box("boiler", (-4.6, -9.5, 20.3), (4.6, -8.9, 21.4), "brass")
    cog("valve_r", "boiler", (-8.9, -23, 12.5), 2.6, 0.7, 6, "x", mat="red", hub="brass", spokes=3, spin=False)
    box("boiler", (8.3, -26, 10.5), (9.1, -22.5, 14), "gauge")
    box("boiler", (8.2, -26.4, 10.1), (8.9, -22.1, 14.4), "brass")
    # pipes down to the shoulders and the legs
    for x in (-8.5, 7.5):
        box("boiler", (x, -26, 5.5), (x + 1.2, -24.8, 9), "copper")
        box("boiler", (x, -24.8, 5.5), (x + 1.2, -12, 6.7), "copper")
    for s, x in (("r", -6.5), ("l", 6.5)):
        ch = f"chimney_{s}"
        part(ch, "boiler", (x, -31, 14))
        octa(ch, (x, -38, 14), 4.6, 14, "y", "dark")
        octa(ch, (x, -39, 14), 5.3, 1.1, "y", "brass")
        octa(ch, (x, -34, 14), 5.3, 1.1, "y", "brass")
        octa(ch, (x, -45.6, 14), 6.4, 1.6, "y", "hot" if not p1 else "brass")
    for s, x in (("r", -12), ("l", 12)):
        v = f"vent_{s}"
        part(v, "hull", (x, -21, 3))
        if p1:
            box(v, (x - 1, -22.6, 2), (x + 1, -21, 4), "brass")
        else:
            box(v, (x - 1, -28, 2), (x + 1, -21, 4), "copper")
            octa(v, (x, -28.6, 3), 3.4, 1.2, "y", "hot")
            box(v, (x - 0.5, -26, 1.5), (x + 0.5, -24, 2), "brass")

    # ---------------------------------------------------------------- the arms
    def arm(s, m, n):
        part(n("arm"), "hull", (m(-20), -17, 0))
        part(n("forearm"), n("arm"), (m(-20), -5, 0))

        def b(prt, x0, y0, z0, x1, y1, z1, mat):
            xa, xb = min(m(x0), m(x1)), max(m(x0), m(x1))
            box(prt, (xa, y0, z0), (xb, y1, z1), mat)

        octa(n("arm"), (m(-20.5), -17, 0), 8, 7, "x", "brass")
        cog(n("shouldergear"), n("arm"), (m(-24.4), -17, 0), 3.6, 1.2, 10, "x")
        if p1:
            b(n("arm"), -25.5, -22.8, -5.6, -15, -19.2, 5.6, "plate")
            b(n("arm"), -26, -20.2, -6.1, -16, -17.6, 6.1, "plate")
            b(n("arm"), -26.2, -18.2, -6.3, -16.5, -17.4, 6.3, "brass")
        else:
            b(n("arm"), -21, -23, -1, -19, -19, 1, "brass")
            b(n("arm"), -20.5, -25, -0.5, -19.5, -23, 0.5, "chrome")
        b(n("arm"), -22.6, -15, -3, -21.6, -5, 3, "andesite")
        b(n("arm"), -18.4, -15, -3, -17.4, -5, 3, "andesite")
        b(n("arm"), -21.6, -14, -2, -18.4, -6, 2, "dark")
        b(n("arm"), -21, -14, -3.9, -19, -9, -2.9, "brass")
        b(n("arm"), -20.5, -9, -3.6, -19.5, -6, -3.0, "chrome")
        octa(n("forearm"), (m(-20), -5, 0), 6, 6.6, "x", "dark")
        cog(n("elbowgear"), n("forearm"), (m(-23.6), -5, 0), 2.6, 1.0, 8, "x")
        b(n("forearm"), -23, -4, -3.5, -17, 5, 3.5, "andesite")
        b(n("forearm"), -23.5, -3, -4.6, -16.5, 3.5, -3.5, "plate")
        b(n("forearm"), -24, -3, -1, -23, 4, 1, "brass")
        b(n("forearm"), -17, -3, -1, -16, 4, 1, "brass")

    side(arm)

    # the steam hammer
    part("hammer", "forearm_r", (-20, 5, 0))
    octa("hammer", (-20, 6, 0), 7, 2.2, "y", "brass")
    box("hammer", (-21.6, 2.6, -1.6), (-18.4, 6, 1.6), "brass")
    octa("hammer", (-20, 11, 0), 12, 8, "y", "plate", ratio=0.86)
    for y in (8.6, 12.6):
        octa("hammer", (-20, y, 0), 12.8, 1.0, "y", "brass", ratio=0.86)
    box("hammer", (-25, 15, -5), (-15, 15.8, 5), "chrome")
    for dx in (-23.2, -18.6):
        for dz in (-3.2, 1.4):
            box("hammer", (dx, 15.8, dz), (dx + 1.8, 16.6, dz + 1.8), "steel")

    # the harpoon cannon
    part("cannon", "forearm_l", (20, 3, 0))
    octa("cannon", (20.5, 4.5, -6), 6, 22, "z", "copper")
    for z in (-14, -6, 2):
        octa("cannon", (20.5, 4.5, z), 6.8, 1.2, "z", "brass")
    octa("cannon", (20.5, 4.5, -17.5), 8, 1.6, "z", "steel")
    box("cannon", (17.5, 1.5, 3), (23.5, 7.5, 7), "plate")
    box("cannon", (20, 0.6, -11), (21, 1.6, -8), "brass")
    cog("drum", "cannon", (24.6, 4.5, -3), 3.2, 2.0, 8, "x", mat="chain", hub="brass", spokes=2, spin=False)
    part("harpoon", "cannon", (20.5, 4.5, -17))
    box("harpoon", (19.6, 3.6, -22), (21.4, 5.4, -17), "chrome")
    box("harpoon", (18.6, 4.1, -20.2), (22.4, 4.9, -19.4), "chrome")
    box("harpoon", (20.1, 4.1, -23.6), (20.9, 4.9, -22), "chrome")


# ---------------------------------------------------------------- painting
RAMP = {
    "brass": [(70, 46, 14), (112, 76, 24), (156, 112, 38), (198, 152, 60), (230, 190, 98), (250, 224, 150), (255, 244, 206)],
    "copper": [(66, 30, 18), (104, 50, 28), (146, 76, 44), (184, 104, 62), (214, 136, 88), (236, 170, 124), (250, 206, 170)],
    "plate": [(28, 30, 36), (42, 45, 52), (58, 62, 70), (76, 80, 90), (98, 103, 114), (124, 129, 140), (156, 160, 170)],
    "dark": [(16, 16, 20), (24, 25, 30), (34, 35, 41), (46, 48, 55), (60, 62, 70), (78, 80, 90)],
    "chrome": [(80, 84, 92), (112, 118, 128), (150, 156, 166), (188, 194, 202), (222, 226, 232), (248, 250, 252)],
    "steel": [(54, 58, 66), (80, 85, 94), (110, 116, 126), (142, 148, 158), (176, 182, 190)],
    "andesite": [(70, 72, 72), (92, 95, 95), (112, 116, 116), (132, 136, 136), (152, 156, 156)],
    "leather": [(36, 20, 12), (54, 32, 20), (74, 46, 28), (94, 60, 38), (116, 78, 50)],
    "red": [(70, 10, 10), (112, 18, 16), (158, 30, 26), (200, 52, 42), (234, 96, 80)],
    "rubber": [(14, 13, 12), (22, 21, 20), (32, 30, 28), (44, 42, 40)],
    "teeth": [(84, 56, 18), (124, 86, 30), (166, 120, 44), (206, 160, 70)],
    "chain": [(26, 26, 30), (46, 47, 54), (72, 74, 82), (104, 106, 114)],
    "hot": [(110, 24, 8), (176, 54, 12), (228, 100, 22), (250, 156, 46), (255, 210, 110), (255, 240, 190)],
    "lamp": [(210, 160, 50), (244, 206, 90), (255, 236, 160), (255, 250, 228)],
}
GLOWING = {"hot", "lamp"}


def pick(ramp, level):
    return ramp[max(0, min(len(ramp) - 1, int(round(level))))]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


FACE_LIGHT = {"top": 0.9, "front": 0.25, "left": 0.0, "right": 0.0, "back": -0.35, "bottom": -1.0}


def paint_face(tex, glow, rect, face, mat, rnd):
    x0, y0, w, h = (int(round(v)) for v in rect)
    w, h = max(1, w), max(1, h)
    light = FACE_LIGHT[face]

    def put(x, y, c, glows=False):
        tex.set(x0 + x, y0 + y, c, 0.0)
        if glows:
            glow.set(x0 + x, y0 + y, c, 0.0)

    if mat in ("brass", "copper", "teeth"):
        ramp = RAMP[mat]
        top = len(ramp) - 2
        streaks = [rnd.random() for _ in range(w)]
        for y in range(h):
            for x in range(w):
                k = y / max(1, h - 1)
                level = top - k * 2.6 + light
                if streaks[x] > 0.84:
                    level += 0.8
                elif streaks[x] < 0.12:
                    level -= 0.6
                if y == 0:
                    level += 1.0
                if y == h - 1 and h > 2:
                    level -= 1.4
                if (x == 0 or x == w - 1) and w > 2:
                    level -= 0.7
                c = pick(ramp, level)
                if mat == "copper":
                    if h > 4 and w > 4 and (y == 1 or y == h - 2) and x % 3 == 1:
                        c = pick(ramp, level + 1.6)          # rivets
                    elif h > 4 and (y >= h - 2) and rnd.random() < 0.12:
                        c = mix(c, (70, 140, 118), 0.6)      # patina in the lower seams
                if rnd.random() < 0.012:
                    c = ramp[-1]                              # a glint
                put(x, y, c)
    elif mat == "plate":
        ramp = RAMP[mat]
        for y in range(h):
            for x in range(w):
                level = 3.2 + light - (y / max(1, h - 1)) * 0.8 + rnd.uniform(-0.15, 0.15)
                if y == 0 or x == 0:
                    level += 1.4                              # bevel lit
                if y == h - 1 or x == w - 1:
                    level -= 1.6                              # bevel shaded
                corner = (x in (1, w - 2)) and (y in (1, h - 2)) and w > 3 and h > 3
                edge_rivet = w > 6 and y in (1, h - 2) and x % 4 == 2 and 1 < x < w - 2
                if corner or edge_rivet:
                    put(x, y, pick(ramp, 5.5))
                    continue
                if h > 9 and y == h // 2:
                    level -= 1.2                              # a seam between two plates
                if rnd.random() < 0.02:
                    level += 1.2                              # a scratch
                put(x, y, pick(ramp, level))
    elif mat == "andesite":
        # Create's andesite casing: speckled grey stone-metal in an andesite-alloy frame
        ramp = RAMP[mat]
        frame = [(64, 60, 52), (92, 86, 74), (122, 114, 98)]
        for y in range(h):
            for x in range(w):
                border = x == 0 or y == 0 or x == w - 1 or y == h - 1
                inner = x == 1 or y == 1 or x == w - 2 or y == h - 2
                if border and w > 2 and h > 2:
                    c = frame[2] if (y == 0 or x == 0) else frame[0]
                elif inner and w > 4 and h > 4:
                    c = frame[1]
                else:
                    c = pick(ramp, 2.2 + light + rnd.choice((-1, 0, 0, 0, 1)) * 0.8)
                put(x, y, c)
    elif mat == "chrome":
        ramp = RAMP[mat]
        for y in range(h):
            for x in range(w):
                band = x / max(1, w - 1)
                level = 2.5 + light + (2.2 if abs(band - 0.3) < 0.2 else 0) - (1.2 if band > 0.8 else 0)
                put(x, y, pick(ramp, level))
    elif mat == "leather":
        ramp = RAMP[mat]
        for y in range(h):
            for x in range(w):
                level = 2.4 + light + rnd.uniform(-0.3, 0.3)
                stitch = (x == 1 or y == 1 or x == w - 2 or y == h - 2) and (x + y) % 2 == 0 and w > 3 and h > 3
                if stitch:
                    put(x, y, (176, 140, 96))
                    continue
                if h > 4 and y % 3 == 0:
                    level -= 0.5                              # padding tucks
                put(x, y, pick(ramp, level))
    elif mat == "glass":
        for y in range(h):
            for x in range(w):
                c = (150, 196, 210)
                if (x - y) % 7 in (0, 1):
                    c = (226, 244, 250)
                if y == 0:
                    c = (188, 222, 232)
                put(x, y, c)
    elif mat in ("grate", "hot_grate"):
        for y in range(h):
            for x in range(w):
                slot = y % 2 == 1 and 0 < x < w - 1
                if slot:
                    c = pick(RAMP["hot"], 3 + (x % 3 == 0)) if mat == "hot_grate" else (10, 10, 12)
                    put(x, y, c, mat == "hot_grate")
                else:
                    put(x, y, pick(RAMP["dark"], 3 + light))
    elif mat == "gauge":
        cx, cy = (w - 1) / 2, (h - 1) / 2
        rad = min(w, h) / 2
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                c = (236, 226, 196)
                if d > rad - 0.8:
                    c = (60, 44, 24)
                elif abs(d - (rad - 1.6)) < 0.5 and (x + y) % 2 == 0:
                    c = (80, 70, 52)                          # the ticks
                if face != "front":
                    c = pick(RAMP["brass"], 3)
                put(x, y, c)
    elif mat == "chain":
        for y in range(h):
            for x in range(w):
                link = ((x // 2) + (y // 2)) % 2
                put(x, y, pick(RAMP["chain"], 2.5 + light + (0.8 if link else -0.8)))
    elif mat in GLOWING:
        ramp = RAMP[mat]
        for y in range(h):
            for x in range(w):
                level = len(ramp) - 2.5 + ((x * 3 + y * 5) % 4 - 1.5) * 0.5 + rnd.uniform(-0.4, 0.4)
                if mat == "lamp":
                    d = math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / max(1, min(w, h) / 2)
                    level = len(ramp) - 1 - d * 2
                put(x, y, pick(ramp, level), True)
    else:
        ramp = RAMP[mat]
        for y in range(h):
            for x in range(w):
                level = len(ramp) / 2 + light - (y / max(1, h - 1)) * 0.8 + rnd.uniform(-0.2, 0.2)
                if (y == 0 or x == 0) and w > 2 and h > 2:
                    level += 0.8
                if (y == h - 1 or x == w - 1) and w > 2 and h > 2:
                    level -= 0.8
                put(x, y, pick(ramp, level))


def paint(tex, glow):
    rnd = random.Random(23)
    for name, prt, origin, size, uv in CUBES:
        w, h, d = (max(1, int(math.ceil(v - 1e-6))) for v in size)
        for face, rect in faces(uv[0], uv[1], w, h, d).items():
            paint_face(tex, glow, rect, face, MATERIAL[name], rnd)


# ---------------------------------------------------------------- animations
def k(t, prt, rot=(0, 0, 0), pos=None, linear=False):
    return (t, prt, rot, pos, linear)


MOVING = ["body", "hull", "boiler", "chimney_r", "chimney_l", "arm_r", "forearm_r", "hammer", "arm_l", "forearm_l",
          "cannon", "harpoon", "leg_r", "shin_r", "foot_r", "leg_l", "shin_l", "foot_l", "gauge_needle", "lever_r", "lever_l",
          "vent_r", "vent_l"]


def rest(t, parts=MOVING, linear=False):
    return [k(t, p, (0, 0, 0), (0, 0, 0), linear) for p in parts]


def spin(seconds, turns=1):
    """The cogs turning round, evenly, over the animation (whole turns: it loops without a jump)."""
    out = []
    for prt, axis in SPINNERS:
        sign = -1 if prt.endswith("_l") else 1
        for i in range(5):
            a = sign * 360 * turns * i / 4
            out.append(k(seconds * i / 4, prt, (a, 0, 0) if axis == "x" else (0, 0, a), linear=True))
    return out


def kneel(t):
    return [k(t, "body", (0, 0, 0), (0, 7, 0)), k(t, "leg_r", (-70, 0, 0)), k(t, "shin_r", (100, 0, 0)), k(t, "foot_r", (-30, 0, 0)),
            k(t, "leg_l", (-70, 0, 0)), k(t, "shin_l", (100, 0, 0)), k(t, "foot_l", (-30, 0, 0))]


ANIMS = []


def anim(name, start, seconds, loop, keys):
    ANIMS.append((name, start, start + round(seconds * 20), loop, keys))


def animations():
    rnd = random.Random(5)
    anim("idle", 0, 3.0, True, spin(3.0) + [
        k(0, "hull", (0, 0, 0)), k(1.5, "hull", (1.5, 0, 0)), k(3, "hull", (0, 0, 0)),
        k(0, "body", None, (0, 0, 0)), k(1.5, "body", None, (0, 0.6, 0)), k(3, "body", None, (0, 0, 0)),
        k(0, "arm_r", (0, 0, 0)), k(1.5, "arm_r", (-3, 0, 2)), k(3, "arm_r", (0, 0, 0)),
        k(0, "arm_l", (0, 0, 0)), k(1.5, "arm_l", (-3, 0, -2)), k(3, "arm_l", (0, 0, 0)),
        k(0, "gauge_needle", (0, 0, 0)), k(1, "gauge_needle", (0, 0, 30)), k(2, "gauge_needle", (0, 0, 10)), k(3, "gauge_needle", (0, 0, 0)),
        k(0, "chimney_r", (0, 0, 0)), k(0.75, "chimney_r", (1, 0, 1)), k(1.5, "chimney_r", (0, 0, 0)), k(2.25, "chimney_r", (-1, 0, -1)), k(3, "chimney_r", (0, 0, 0)),
        k(0, "chimney_l", (0, 0, 0)), k(0.75, "chimney_l", (-1, 0, -1)), k(1.5, "chimney_l", (0, 0, 0)), k(2.25, "chimney_l", (1, 0, 1)), k(3, "chimney_l", (0, 0, 0)),
    ])
    anim("walk", 100, 2.0, True, [
        k(0, "leg_r", (25, 0, 0)), k(1, "leg_r", (-25, 0, 0)), k(2, "leg_r", (25, 0, 0)),
        k(0, "shin_r", (0, 0, 0)), k(0.5, "shin_r", (30, 0, 0)), k(1, "shin_r", (5, 0, 0)), k(2, "shin_r", (0, 0, 0)),
        k(0, "leg_l", (-25, 0, 0)), k(1, "leg_l", (25, 0, 0)), k(2, "leg_l", (-25, 0, 0)),
        k(0, "shin_l", (5, 0, 0)), k(1, "shin_l", (0, 0, 0)), k(1.5, "shin_l", (30, 0, 0)), k(2, "shin_l", (5, 0, 0)),
        k(0, "body", None, (0, 0, 0)), k(0.5, "body", None, (0, -1.2, 0)), k(1, "body", None, (0, 0, 0)),
        k(1.5, "body", None, (0, -1.2, 0)), k(2, "body", None, (0, 0, 0)),
        k(0, "hull", (0, 0, 3)), k(1, "hull", (0, 0, -3)), k(2, "hull", (0, 0, 3)),
        k(0, "arm_r", (-12, 0, 0)), k(1, "arm_r", (10, 0, 0)), k(2, "arm_r", (-12, 0, 0)),
        k(0, "arm_l", (10, 0, 0)), k(1, "arm_l", (-12, 0, 0)), k(2, "arm_l", (10, 0, 0)),
    ])
    # the rivet spiral: the hammer up, down into the ground at 0.9 s (tick 18)
    anim("pound", 200, 2.2, False, rest(0) + [
        k(0.7, "arm_r", (-155, 0, -15)), k(0.7, "forearm_r", (-35, 0, 0)), k(0.7, "hull", (-10, 18, 0)), k(0.7, "body", None, (0, -1, 0)),
        k(0.7, "lever_r", (-40, 0, 0)),
        k(0.9, "arm_r", (-20, 0, -5), linear=True), k(0.9, "forearm_r", (-10, 0, 0), linear=True), k(0.9, "hull", (22, -12, 0), linear=True),
        k(0.9, "body", None, (0, 3, 0), linear=True), k(0.9, "leg_r", (-25, 0, 0), linear=True), k(0.9, "shin_r", (35, 0, 0), linear=True),
        k(0.9, "leg_l", (-25, 0, 0), linear=True), k(0.9, "shin_l", (35, 0, 0), linear=True),
        k(1.4, "arm_r", (-20, 0, -5)), k(1.4, "hull", (22, -12, 0)), k(1.4, "body", None, (0, 3, 0)),
        k(1.4, "leg_r", (-25, 0, 0)), k(1.4, "shin_r", (35, 0, 0)), k(1.4, "leg_l", (-25, 0, 0)), k(1.4, "shin_l", (35, 0, 0)),
    ] + rest(2.2))
    # the harpoon: raises the cannon level, fires at 0.6 s (tick 12), reels in till 1.6 s, the hammer at 2.0 s (tick 40)
    anim("harpoon", 300, 2.6, False, rest(0) + [
        k(0.4, "arm_l", (-30, 0, 0)), k(0.4, "forearm_l", (25, 0, 0)), k(0.4, "hull", (0, -18, 0)), k(0.4, "lever_l", (-40, 0, 0)),
        k(0.6, "arm_l", (-24, 0, 0), linear=True), k(0.6, "cannon", None, (0, 0, 2), linear=True), k(0.6, "harpoon", None, (0, 0, 7), linear=True),
        k(0.8, "cannon", None, (0, 0, 0)), k(1.5, "arm_l", (-26, 0, 0)), k(1.5, "forearm_l", (25, 0, 0)), k(1.5, "hull", (0, -10, 0)),
        k(1.5, "harpoon", None, (0, 0, 7)), k(1.6, "harpoon", None, (0, 0, 0), linear=True),
        k(1.75, "arm_r", (-160, 0, -10)), k(1.75, "forearm_r", (-30, 0, 0)), k(1.75, "hull", (-12, 10, 0)), k(1.75, "arm_l", (-10, 0, 0)),
        k(1.75, "forearm_l", (0, 0, 0)),
        k(2.0, "arm_r", (-25, 0, 0), linear=True), k(2.0, "forearm_r", (-10, 0, 0), linear=True), k(2.0, "hull", (25, 0, 0), linear=True),
        k(2.0, "body", None, (0, 3, 0), linear=True),
        k(2.3, "arm_r", (-25, 0, 0)), k(2.3, "hull", (25, 0, 0)), k(2.3, "body", None, (0, 3, 0)),
    ] + rest(2.6))
    # the gear saws: winds up and flings them at 0.7 s (tick 14)
    anim("gears", 400, 2.0, False, rest(0) + [
        k(0.5, "hull", (0, 45, 0)), k(0.5, "arm_r", (-50, 0, -70)), k(0.5, "arm_l", (-50, 0, 70)), k(0.5, "boiler", None, (0, 0, 1)),
        k(0.7, "hull", (0, -40, 0), linear=True), k(0.7, "arm_r", (-80, 0, 40), linear=True), k(0.7, "arm_l", (-80, 0, -40), linear=True),
        k(0.7, "boiler", None, (0, -1, 0), linear=True),
        k(1.1, "hull", (0, -30, 0)), k(1.1, "arm_r", (-60, 0, 20)), k(1.1, "arm_l", (-60, 0, -20)),
    ] + rest(2.0))
    # phase 2 begins (6 s): it kneels, the boiler shudders, the chimneys blow out at 3.2 s, it rears up roaring
    transform = rest(0) + kneel(0.6) + [k(0.6, "hull", (28, 0, 0)), k(0.6, "arm_r", (15, 0, -10)), k(0.6, "arm_l", (15, 0, 10)),
                                        k(0.6, "lever_r", (-50, 0, 0)), k(0.6, "lever_l", (-50, 0, 0))]
    t = 1.4
    while t < 3.1:
        a = 2 + (t - 1.4) * 3
        transform += [k(t, "hull", (28 + rnd.uniform(-a, a), rnd.uniform(-a, a), rnd.uniform(-a, a))),
                      k(t, "boiler", None, (rnd.uniform(-0.5, 0.5) * a / 3, 0, rnd.uniform(-0.5, 0.5) * a / 3)),
                      k(t, "chimney_r", (rnd.uniform(-a, a), 0, rnd.uniform(-a, a))),
                      k(t, "chimney_l", (rnd.uniform(-a, a), 0, rnd.uniform(-a, a))),
                      k(t, "gauge_needle", (0, 0, 60 + 40 * (t - 1.4)))]
        t += 0.1
    transform += kneel(3.1) + [
        k(3.2, "chimney_r", (0, 0, -10), (0, -4, 0), linear=True), k(3.2, "chimney_l", (0, 0, 10), (0, -4, 0), linear=True),
        k(3.2, "gauge_needle", (0, 0, 200), linear=True),
        k(3.6, "chimney_r", (0, 0, 0), (0, 0, 0)), k(3.6, "chimney_l", (0, 0, 0), (0, 0, 0)),
        k(4.1, "body", (0, 0, 0), (0, -2, 0)), k(4.1, "leg_r", (0, 0, 0)), k(4.1, "shin_r", (0, 0, 0)), k(4.1, "foot_r", (0, 0, 0)),
        k(4.1, "leg_l", (0, 0, 0)), k(4.1, "shin_l", (0, 0, 0)), k(4.1, "foot_l", (0, 0, 0)),
        k(4.1, "hull", (-22, 0, 0)), k(4.1, "arm_r", (-150, 0, -40)), k(4.1, "arm_l", (-150, 0, 40)),
        k(5.2, "hull", (-18, 0, 0)), k(5.2, "arm_r", (-140, 0, -45)), k(5.2, "arm_l", (-140, 0, 45)),
    ] + rest(6.0)
    anim("transform", 500, 6.0, False, transform)
    # scalding geysers: both fists into the ground at 0.8 s (tick 16)
    anim("geysers", 700, 2.4, False, rest(0) + [
        k(0.6, "arm_r", (-165, 0, -20)), k(0.6, "arm_l", (-165, 0, 20)), k(0.6, "hull", (-15, 0, 0)), k(0.6, "body", None, (0, -1, 0)),
        k(0.8, "arm_r", (-15, 0, -10), linear=True), k(0.8, "arm_l", (-15, 0, 10), linear=True), k(0.8, "hull", (28, 0, 0), linear=True),
        k(0.8, "body", None, (0, 4, 0), linear=True), k(0.8, "leg_r", (-30, 0, 0), linear=True), k(0.8, "shin_r", (40, 0, 0), linear=True),
        k(0.8, "leg_l", (-30, 0, 0), linear=True), k(0.8, "shin_l", (40, 0, 0), linear=True),
        k(1.5, "arm_r", (-15, 0, -10)), k(1.5, "arm_l", (-15, 0, 10)), k(1.5, "hull", (28, 0, 0)), k(1.5, "body", None, (0, 4, 0)),
        k(1.5, "leg_r", (-30, 0, 0)), k(1.5, "shin_r", (40, 0, 0)), k(1.5, "leg_l", (-30, 0, 0)), k(1.5, "shin_l", (40, 0, 0)),
    ] + rest(2.4))
    # the pressure release (5.5 s): arms spread, venting from 1.25 s to 5.25 s while the whole mech turns round
    # (EvilBrother turns the entity itself, so the jet, its warning and the model always agree)
    sweep = rest(0) + [k(0.8, "arm_r", (-60, 0, -75)), k(0.8, "arm_l", (-60, 0, 75)), k(0.8, "hull", (8, 0, 0)),
                       k(1.25, "hull", (8, 0, 0)), k(1.25, "vent_r", (0, 0, 0)), k(1.25, "vent_l", (0, 0, 0)),
                       k(5.25, "hull", (8, 0, 0))]
    sweep += [k(1.25 + i * 0.5, v, (0, 0, (8 if i % 2 else -8) * (1 if v == "vent_r" else -1))) for i in range(1, 8) for v in ("vent_r", "vent_l")]
    sweep += [k(5.25, "arm_r", (-60, 0, -75)), k(5.25, "arm_l", (-60, 0, 75)), k(5.5, "arm_r", (0, 0, 0)), k(5.5, "arm_l", (0, 0, 0)),
              k(5.5, "hull", (0, 0, 0))]
    anim("sweep", 900, 5.5, False, sweep)
    # a rush (1.3 s): crouches, leans hard forward, the arms thrown back
    anim("rush", 1100, 1.3, False, rest(0) + [
        k(0.3, "body", None, (0, 2, 0)), k(0.3, "hull", (30, 0, 0)), k(0.3, "arm_r", (45, 0, -20)), k(0.3, "arm_l", (45, 0, 20)),
        k(0.3, "leg_r", (-35, 0, 0)), k(0.3, "shin_r", (40, 0, 0)), k(0.3, "leg_l", (20, 0, 0)), k(0.3, "shin_l", (20, 0, 0)),
        k(1.0, "body", None, (0, 2, 0)), k(1.0, "hull", (34, 0, 0)), k(1.0, "arm_r", (50, 0, -20)), k(1.0, "arm_l", (50, 0, 20)),
        k(1.0, "leg_r", (-35, 0, 0)), k(1.0, "shin_r", (40, 0, 0)), k(1.0, "leg_l", (20, 0, 0)), k(1.0, "shin_l", (20, 0, 0)),
    ] + rest(1.3))
    # death (5 s): staggers, kneels, shakes ever harder as the boiler overheats, rears back as it bursts
    death = rest(0) + [k(0.4, "hull", (15, 0, 12)), k(0.4, "arm_r", (20, 0, -20)), k(0.4, "arm_l", (-30, 0, 15))]
    death += kneel(1.0) + [k(1.0, "hull", (25, 0, -6)), k(1.0, "arm_r", (25, 0, -15)), k(1.0, "arm_l", (25, 0, 15))]
    t = 1.2
    while t < 4.4:
        a = 1 + (t - 1.2) * 4
        death += [k(t, "hull", (25 + rnd.uniform(-a, a), rnd.uniform(-a, a), rnd.uniform(-a, a))),
                  k(t, "boiler", None, (rnd.uniform(-0.3, 0.3) * a, rnd.uniform(-0.2, 0.2) * a, rnd.uniform(-0.3, 0.3) * a)),
                  k(t, "chimney_r", (rnd.uniform(-a, a) * 2, 0, rnd.uniform(-a, a) * 2)),
                  k(t, "chimney_l", (rnd.uniform(-a, a) * 2, 0, rnd.uniform(-a, a) * 2)),
                  k(t, "gauge_needle", (0, 0, 90 + 400 * (t - 1.2)))]
        t += 0.08
    death += kneel(4.4) + [k(4.6, "hull", (-30, 0, 0), linear=True), k(4.6, "arm_r", (-120, 0, -60), linear=True),
                           k(4.6, "arm_l", (-120, 0, 60), linear=True), k(4.6, "boiler", None, (0, -2, 1), linear=True),
                           k(4.6, "chimney_r", (0, 0, -40), (0, -6, 0), linear=True), k(4.6, "chimney_l", (0, 0, 40), (0, -6, 0), linear=True),
                           k(5.0, "hull", (-30, 0, 0)), k(5.0, "chimney_r", (0, 0, -40), (0, -6, 0)), k(5.0, "chimney_l", (0, 0, 40), (0, -6, 0))]
    anim("death", 1200, 5.0, False, death)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    out_dir, tex_dir, phase = argv[0], argv[1], int(argv[2])
    model(phase)
    used = pack(CUBES, TEX_W)
    assert used <= TEX_H, f"texture too small: {used} rows"
    name = "evil_brother" + ("_p2" if phase == 2 else "")
    tex = Tex(TEX_W, TEX_H, seed=3)
    glow = Tex(TEX_W, TEX_H, seed=3)
    paint(tex, glow)
    tex_path = os.path.join(tex_dir, name + ".png")
    tex.save(tex_path)
    glow.save(os.path.join(tex_dir, name + "_glow.png"))
    rel = []
    for prt, parent, pivot, rot in PARTS:
        base = ABS[parent] if parent else (0, 0, 0)
        rel.append((prt, parent, tuple(pivot[i] - base[i] for i in range(3)), rot))
    scene, objs = build_scene(rel, CUBES, tex_path, TEX_W, TEX_H, name)
    animations()
    for n, start, end, loop, keys in ANIMS:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{s}:{e}:{'loop' if lp else 'once'}" for n, s, e, lp, _ in ANIMS]
    scene.frame_start = 0
    scene.frame_end = max(a[2] for a in ANIMS)
    scene.frame_set(0)
    save(os.path.join(out_dir, name + ".blend"))
    print("saved", name, "parts", len(PARTS), "cubes", len(CUBES), "rows", used)


main()
