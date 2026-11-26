"""
Shared parts of the Nameless Creator's four models (build_creator_p1.py .. build_creator_p4.py):
- Model: the part and cube lists, mirrored for the right side (s = -1: x, y- and z-rotations flipped);
- k(): a keyframe, mirrored the same way; anim(): animations laid one after another on the timeline;
- painting helpers (faces of a cube, mirrored "inner" columns, paper edges, wire outlines);
- majesty(): the rig of the first two phases - the god as two halves (half_l, half_r) that are one whole figure until
  the collage cracks down the middle (phase 2 is the same rig, the halves apart, restyled);
- build(): pack, paint, Blender scene, animations, save.

Model space as everywhere (mcmodel.py): pixels, y down, the ground at y 24, the front -z. The model's left (+x) is
the god's left.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, animation, build_scene, faces, pack, save  # noqa: E402

SIDES = ((1, "l"), (-1, "r"))
# how many times its pixels each phase is drawn (the renderer's scale), and where phase 4's face is (model y of its
# middle) - phase 3's face sinks there before the swap
P3_SCALE, P4_SCALE = 3.0, 1.5
P4_FACE_Y = -22


# ---------------------------------------------------------------- parts and cubes
class Model:
    def __init__(self, name):
        self.name = name
        self.parts = []   # (name, parent, pivot, rot)
        self.cubes = []   # [name, part, origin, size, uv]
        self.meta = {}    # cube name -> {"kind": ..., "s": side, ...}
        self.anims = []   # (name, start frame, seconds, loop, keys)
        self.pivots = {}
        self._n = 0
        self._start = 0

    def part(self, name, parent, pivot, rot=(0, 0, 0), s=1):
        pivot = (pivot[0] * s, pivot[1], pivot[2])
        rot = (rot[0], rot[1] * s, rot[2] * s)
        self.parts.append((name, parent, pivot, rot))
        self.pivots[name] = pivot
        return name

    def box(self, part, origin, size, kind, s=1, **meta):
        x0, y0, z0 = origin
        w = size[0]
        if s < 0:
            x0 = -(x0 + w)
        self._n += 1
        name = f"{part}_{self._n}"
        self.cubes.append([name, part, (x0, y0, z0), tuple(size), None])
        self.meta[name] = dict(kind=kind, s=s, **meta)
        return name

    def anim(self, name, seconds, loop, keys):
        self.anims.append((name, self._start, seconds, loop, keys))
        self._start += round(seconds * 20) + 20

    def has(self, part):
        return part in self.pivots


def k(t, part, rot=None, pos=None, linear=False, scl=None, s=1):
    """A keyframe (seconds, part, rotation offset in degrees, position offset in pixels, scale factor), mirrored for s = -1.
    k(t, part, rot, True[, scale]) - no position - is a linear key too."""
    if isinstance(pos, bool):
        if not isinstance(linear, bool):
            scl = linear
        pos, linear = None, pos
    if rot is not None:
        rot = (rot[0], rot[1] * s, rot[2] * s)
    if pos is not None:
        pos = (pos[0] * s, pos[1], pos[2])
    return (t, part, rot, pos, linear, scl)


def both(t, name, rot=None, pos=None, linear=False, scl=None):
    """The same key on the left and the right part (name has {} for l / r), mirrored."""
    return [k(t, name.format(side), rot, pos, linear, scl, s) for s, side in SIDES]


def hold(parts, t0, t1, **kw):
    """Keys the parts unchanged at t0 and t1 (rotation, position and, if given, scale), so a stretch holds still."""
    out = []
    for p in parts:
        for t in (t0, t1):
            out.append(k(t, p, kw.get("rot", (0, 0, 0)), kw.get("pos", (0, 0, 0)), False, kw.get("scl")))
    return out


# ---------------------------------------------------------------- painting
def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def dims(size):
    return tuple(max(0, int(math.ceil(v - 1e-6))) for v in size)


def face_rects(cube):
    """The texture rectangles (x, y, w, h) of a cube's faces that have any pixels."""
    _, _, _, size, uv = cube
    w, h, d = dims(size)
    out = {}
    for face, (x0, y0, fw, fh) in faces(uv[0], uv[1], w, h, d).items():
        if fw > 0 and fh > 0:
            out[face] = (int(x0), int(y0), int(fw), int(fh))
    return out


def inner(face, xx, w, s):
    """Columns counted from the middle line of the god (for the halves): 0 next to it."""
    if face == "front":
        return xx if s > 0 else w - 1 - xx
    if face == "back":
        return w - 1 - xx if s > 0 else xx
    return xx


def paint_face(tex, glow, rect, fn, noise=0.03):
    """fn(x, y, w, h) -> (colour or None for see-through, glow colour or None)."""
    x0, y0, w, h = rect
    for yy in range(h):
        for xx in range(w):
            c, g = fn(xx, yy, w, h)
            tex.set(x0 + xx, y0 + yy, c, noise)
            if g is not None and glow is not None:
                glow.set(x0 + xx, y0 + yy, g, 0.0)


def border(xx, yy, w, h):
    return xx == 0 or yy == 0 or xx == w - 1 or yy == h - 1


# ---------------------------------------------------------------- the first two phases' rig
BANNER_ANGLES = (18, 54, 90, 126, 162)
HALO_R = 17


def majesty(M, gap=0.0, antlers="lr"):
    """
    The god as a cathedral of paper, in two halves joined at the middle (gap: how far apart, pixels each way).
    Per half: the torso, a pauldron, an arm (upper arm, forearm, a paper hand, a puppet string), the head (a hood, the
    map that is its face, a crown of flowers, an antler of cherry branches in blossom, a puppet string), half the rose
    window behind it, five banners hanging from the waist as its robe, and on a chain from the window's edge the clock
    (left) or the compass (right). antlers: the sides that have one.
    """
    M.part("body", None, (0, 4, 0))
    for s, t in SIDES:
        half = M.part(f"half_{t}", "body", (gap, 0, 0), s=s)
        M.box(half, (0, -14, -2), (7, 14, 4), "robe", s)
        M.box(half, (0, -13, -3), (6, 9, 1), "plate", s)
        M.box(half, (0, -2, -3), (8, 2, 6), "belt", s)
        M.box(half, (0, -15, -2.5), (4, 1, 5), "trim", s)

        sh = M.part(f"shoulder_{t}", half, (7, -12, 0), s=s)
        M.box(sh, (-1, -2, -3), (5, 4, 6), "pauldron", s)
        M.box(sh, (1.5, -0.5, -3.5), (1, 1, 1), "brad", s)

        arm = M.part(f"arm_{t}", half, (9, -11, 0), (0, 0, -12), s=s)
        M.box(arm, (-1.5, 0, -1.5), (3, 9, 3), "sleeve", s)
        M.box(arm, (-0.5, -0.5, -2), (1, 1, 1), "brad", s)
        fore = M.part(f"forearm_{t}", arm, (0, 9, 0), (-35, 0, 0), s=s)
        M.box(fore, (-1.5, 0, -1.5), (3, 7, 3), "sleeve", s)
        M.box(fore, (-2, 5, -2), (4, 2, 4), "cuff", s)
        M.box(fore, (-0.5, -1, -0.5), (1, 2, 1), "chain", s)
        hand = M.part(f"hand_{t}", fore, (0, 7, 0), (35, 0, 0), s=s)
        M.box(hand, (-2.5, 0, -0.5), (5, 6, 1), "hand", s)
        M.box(hand, (-0.5, -1, -0.5), (1, 1, 1), "chain", s)
        st = M.part(f"string_hand_{t}", hand, (0, 2, 0), s=s)
        M.box(st, (-0.25, -64, -0.25), (0.5, 64, 0.5), "string", s)

        head = M.part(f"head_{t}", half, (0, -14, 0))
        M.box(head, (0, -2, -1.5), (2, 2, 3), "skin", s)
        M.box(head, (0, -13, -4), (6, 11, 8), "hood", s)
        face = M.part(f"face_{t}", head, (0, -7.5, -4), s=s)
        M.box(face, (0, -4.5, -1), (5, 9, 1), "map", s)
        for i, a in enumerate((12, 50, 92, 134)):
            r = math.radians(a)
            fl = M.part(f"flower_{t}{i}", head, (math.sin(r) * 5.6, -13, -math.cos(r) * 4.6), (0, -a, 0), s=s)
            M.box(fl, (-1.5, -4, -0.5), (3, 4, 1), "flower", s, idx=i + (0 if s > 0 else 4))
        if t in antlers:
            a0 = M.part(f"antler_{t}0", head, (3.5, -13, 0.5), (0, 0, 25), s=s)
            M.box(a0, (-0.5, -8, -0.5), (1, 8, 1), "wood", s)
            a1 = M.part(f"antler_{t}1", a0, (0, -8, 0), (-10, 0, 22), s=s)
            M.box(a1, (-0.5, -6, -0.5), (1, 6, 1), "wood", s)
            a2 = M.part(f"antler_{t}2", a0, (0, -4.5, 0), (0, 0, -40), s=s)
            M.box(a2, (-0.5, -5, -0.5), (1, 5, 1), "wood", s)
            a3 = M.part(f"antler_{t}3", a1, (0, -3, 0), (-25, 0, 35), s=s)
            M.box(a3, (-0.5, -4, -0.5), (1, 4, 1), "wood", s)
            for i, (par, y) in enumerate(((a1, -6), (a2, -5), (a3, -4), (a0, -6))):
                b = M.part(f"blossom_{t}{i}", par, (0, y, 0), s=s)
                M.box(b, (-1.5, -1.5, -1.5), (3, 3, 3), "blossom", s, idx=i)
        st = M.part(f"string_head_{t}", head, (2, -13, 0), s=s)
        M.box(st, (-0.25, -64, -0.25), (0.5, 64, 0.5), "string", s)

        halo = M.part(f"halo_{t}", half, (0, -20, 6), s=s)
        for i in range(8):
            phi = 11.25 + 22.5 * i
            seg = M.part(f"halo_{t}_seg{i}", halo, (0, 0, 0), (0, 0, phi), s=s)
            M.box(seg, (-3.5, -HALO_R - 1, -1), (7, 2, 2), "frame", s)
            pane = M.part(f"halo_{t}_pane{i}", halo, (0, 0, 0), (0, 0, phi), s=s)
            M.box(pane, (-3.5, -HALO_R + 1, 0), (7, HALO_R - 5, 1), "glass", s, idx=i)
        M.box(halo, (0, -4, -1), (4, 8, 1), "hub", s)

        for i, th in enumerate(BANNER_ANGLES):
            r = math.radians(th)
            b = M.part(f"banner_{t}{i}", half, (math.sin(r) * 6.5, 0, -math.cos(r) * 6.5), (-8, -th, 0), s=s)
            M.box(b, (-2.5, 0, -0.5), (5, 18, 1), "banner", s, idx=i)

        prev = half
        for j in range(4):
            c = M.part(f"chain_{t}{j}", prev, (HALO_R - 0.5, -20, 4) if j == 0 else (0, 2, 0), s=s)
            M.box(c, (-0.5, 0, -0.5), (1, 2, 1), "chain", s, idx=j)
            prev = c
        obj = M.part(f"clock_{t}", prev, (0, 2, 0), s=s)
        M.box(obj, (-4.5, 0, -0.5), (9, 9, 1), "clock" if s > 0 else "compass", s)
        if s > 0:  # the clock's hands (the renderer turns them to the time of day)
            hh = M.part("clock_hour", obj, (0, 4.5, -0.5))
            M.box(hh, (-0.5, -2.5, -1), (1, 3, 1), "hand_dark", s)
            mh = M.part("clock_minute", obj, (0, 4.5, -0.5))
            M.box(mh, (-0.5, -3.5, -1.5), (1, 4, 1), "hand_dark", s)
        else:  # the compass needle (turned to the north by the renderer)
            nd = M.part("compass_needle", obj, (0, 4.5, -0.5))
            M.box(nd, (-0.5, -3.5, -1), (1, 7, 1), "needle", s)


def parts_like(M, prefix):
    return [p[0] for p in M.parts if p[0].startswith(prefix)]


# ---------------------------------------------------------------- the build
def build(M, tex_w, paint, textures, blend_dir, tex_dir, preview_z=None):
    """
    paint(M, texs) paints every texture (texs: name -> Tex, the first is the model's own); saves them as
    <tex_dir>/<name>.png, builds the scene, the animations and saves <blend_dir>/<M.name>.blend.
    """
    used = pack(M.cubes, tex_w)
    tex_h = 64
    while tex_h < used:
        tex_h *= 2
    texs = {n: Tex(tex_w, tex_h, seed=7) for n in textures}
    paint(M, texs)
    paths = {}
    for n, t in texs.items():
        paths[n] = os.path.join(tex_dir, n + ".png")
        t.save(paths[n])
    scene, objs = build_scene(M.parts, M.cubes, paths[textures[0]], tex_w, tex_h, M.name)
    for name, start, seconds, loop, keys in M.anims:
        animation(objs, start, keys)
    scene["mc_anims"] = [f"{n}:{st}:{st + round(sec * 20)}:{'loop' if lp else 'once'}" for n, st, sec, lp, _ in M.anims]
    if preview_z is not None:
        scene["mc_preview_z"] = preview_z
    scene.frame_start = 0
    scene.frame_end = max(st + round(sec * 20) for _, st, sec, _, _ in M.anims)
    scene.frame_set(0)
    save(os.path.join(blend_dir, M.name + ".blend"))
    print(M.name, "parts", len(M.parts), "cubes", len(M.cubes), "texture", tex_w, "x", tex_h,
          "animations", [(n, st) for n, st, _, _, _ in M.anims])
