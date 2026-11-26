"""
Shared helpers for building Minecraft entity models in Blender (used by the build_*.py scripts).

Scene layout (1 Blender unit = 1 model pixel, model y = 24 lies on Z = 0, the model looks towards -Y):
- every model part is an Empty (custom property "mc_part") placed at its pivot, parented like the model parts,
  with its rest rotation in "mc_rest_rot" (degrees, model space) and its pivot in "mc_pivot";
- every cube is a mesh under its part, with its texture offset in the custom property "mc_tex";
- animations live on one timeline at 20 fps; scene property "mc_anims" lists them as name:start:end:loop|once;
- scene property "mc_tex_size" holds the texture size.
export_model.py turns such a .blend into Java, preview_model.py renders pictures of it.
"""
import math
import random
import struct
import zlib

import bpy
import bmesh

FPS = 20


# ---------------------------------------------------------------- coordinates
# model space (Minecraft): y grows downwards, the ground is y = 24, the front is -z
def m2b(x, y, z):
    return (x, z, -y)


def mc_rot_to_euler(deg):
    """Model rotation (degrees x, y, z; applied x, then y, then z) -> Blender euler in XZY order."""
    a, b, c = (math.radians(v) for v in deg)
    return (a, c, -b)


# ---------------------------------------------------------------- texture
def faces(u, v, w, h, d):
    """Texture rectangles (x, y, w, h) of a cube's faces, Minecraft box layout."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),         # -x side; left column = back
        "front": (u + d, v + d, w, h),     # -z side; left column = -x
        "left": (u + d + w, v + d, d, h),  # +x side; left column = front
        "back": (u + d + w + d, v + d, w, h),
    }


def pack(cubes, tex_w):
    """Gives every cube without a texture offset a free spot (shelf packing). cubes: [name, part, origin, size, uv]."""
    placed = []
    todo = sorted((c for c in cubes if c[4] is None),
                  key=lambda c: -(math.ceil(c[3][2]) + math.ceil(c[3][1])))
    x = y = shelf = 0
    for c in todo:
        w, h, d = (math.ceil(n) for n in c[3])
        bw, bh = 2 * (d + w), d + h
        if x + bw > tex_w:
            x, y, shelf = 0, y + shelf, 0
        c[4] = (x, y)
        placed.append(c)
        x += bw
        shelf = max(shelf, bh)
    return y + shelf


class Tex:
    def __init__(self, w, h, seed=7):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]
        self.rnd = random.Random(seed)

    def set(self, x, y, c, noise=0.06):
        if 0 <= x < self.w and 0 <= y < self.h:
            if c is None:
                self.px[y][x] = (0, 0, 0, 0)
                return
            k = 1.0 + self.rnd.uniform(-noise, noise)
            self.px[y][x] = tuple(max(0, min(255, int(ch * k))) for ch in c) + (255,)

    def fill(self, rect, c, noise=0.06):
        x0, y0, w, h = (int(round(n)) for n in rect)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, c, noise)

    def at(self, rect, col, row, c, noise=0.04):
        self.set(int(round(rect[0])) + col, int(round(rect[1])) + row, c, noise)

    def save(self, path):
        raw = b"".join(b"\x00" + bytes(ch for p in row for ch in p) for row in self.px)

        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        with open(path, "wb") as f:
            f.write(png)


# ---------------------------------------------------------------- scene
def _material(image, name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    texnode = nodes.new("ShaderNodeTexImage")
    texnode.image = image
    texnode.interpolation = "Closest"
    mat.node_tree.links.new(texnode.outputs["Color"], bsdf.inputs["Base Color"])
    mat.node_tree.links.new(texnode.outputs["Alpha"], bsdf.inputs["Alpha"])
    if hasattr(mat, "surface_render_method"):
        mat.surface_render_method = "DITHERED"
    return mat


def _cube_mesh(name, origin, size, uv, mat, tex_w, tex_h):
    """A box with Minecraft's UV layout; vertices in the part's local space (Blender axes)."""
    x0, y0, z0 = origin
    w, h, d = size
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    u, v = uv
    u0, u1, u2, u3, u4, u5 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    P = {
        7: (x0, y0, z0), 0: (x1, y0, z0), 1: (x1, y1, z0), 2: (x0, y1, z0),
        3: (x0, y0, z1), 4: (x1, y0, z1), 5: (x1, y1, z1), 6: (x0, y1, z1),
    }
    polys = [
        ((4, 3, 7, 0), (u1, v0, u2, v1)),
        ((1, 2, 6, 5), (u2, v1, u3, v0)),
        ((7, 3, 6, 2), (u0, v1, u1, v2)),
        ((0, 7, 2, 1), (u1, v1, u2, v2)),
        ((4, 0, 1, 5), (u2, v1, u4, v2)),
        ((3, 4, 5, 6), (u4, v1, u5, v2)),
    ]
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    verts = {k: bm.verts.new(m2b(*p)) for k, p in P.items()}
    for idx, (a, b, c, e) in polys:
        face = bm.faces.new([verts[i] for i in idx])
        corners = [(c, b), (a, b), (a, e), (c, e)]  # Minecraft Polygon: (u2,v1) (u1,v1) (u1,v2) (u2,v2)
        for loop, (uu, vv) in zip(face.loops, corners):
            loop[uvl].uv = (uu / tex_w, 1.0 - vv / tex_h)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    return me


def build_scene(parts_spec, cubes_spec, tex_path, tex_w, tex_h, name):
    """parts_spec: (name, parent, pivot, rest rotation); cubes_spec: (name, part, origin, size, uv)."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.fps = FPS
    scene["mc_tex_size"] = [tex_w, tex_h]

    image = bpy.data.images.load(tex_path)
    image.pack()
    mat = _material(image, name)

    root = bpy.data.objects.new("root", None)
    root.empty_display_size = 2
    root.location = (0, 0, 24)  # model y = 24 is the ground
    scene.collection.objects.link(root)

    parts = {}
    for pname, parent, pivot, rest in parts_spec:
        e = bpy.data.objects.new(pname, None)
        e.empty_display_type = "SPHERE"
        e.empty_display_size = 0.6
        e.rotation_mode = "XZY"
        e.location = m2b(*pivot)
        e.rotation_euler = mc_rot_to_euler(rest)
        e["mc_part"] = 1
        e["mc_rest_rot"] = list(rest)
        e["mc_pivot"] = list(pivot)
        e.parent = parts[parent] if parent else root
        scene.collection.objects.link(e)
        parts[pname] = e

    for cname, part, origin, size, uv in cubes_spec:
        ob = bpy.data.objects.new("cube_" + cname, _cube_mesh("cube_" + cname, origin, size, uv, mat, tex_w, tex_h))
        ob["mc_tex"] = list(uv)
        ob.parent = parts[part]
        scene.collection.objects.link(ob)
    return scene, parts


# ---------------------------------------------------------------- animation
def fcurves(ob):
    ad = ob.animation_data
    if not ad or not ad.action:
        return []
    try:
        from bpy_extras import anim_utils
        bag = anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot)
        return list(bag.fcurves) if bag else []
    except Exception:
        return list(getattr(ad.action, "fcurves", []))


def key(parts, frame, part, rot=None, pos=None, linear=False, scale=None):
    """Keys a part at a frame: rotation (degrees) and position (pixels, model space) as offsets from its rest pose,
    scale (x, y, z in model space) as a factor."""
    e = parts[part]
    paths = []
    if scale is not None:
        e.scale = (scale[0], scale[2], scale[1])
        e.keyframe_insert("scale", frame=frame)
        paths.append("scale")
    if rot is not None:
        rest = e["mc_rest_rot"]
        e.rotation_euler = mc_rot_to_euler([rest[i] + rot[i] for i in range(3)])
        e.keyframe_insert("rotation_euler", frame=frame)
        paths.append("rotation_euler")
    if pos is not None:
        base = e["mc_pivot"]
        e.location = m2b(base[0] + pos[0], base[1] + pos[1], base[2] + pos[2])
        e.keyframe_insert("location", frame=frame)
        paths.append("location")
    interp = "LINEAR" if linear else "BEZIER"
    for fc in fcurves(e):
        if fc.data_path in paths:
            for k in fc.keyframe_points:
                if abs(k.co[0] - frame) < 0.01:
                    k.interpolation = interp


def animation(parts, start, keys):
    """keys: list of (seconds, part, rot, pos, linear[, scale]) - rot/pos/scale may be None."""
    for t, part, rot, pos, linear, *more in keys:
        key(parts, start + round(t * FPS), part, rot, pos, linear, more[0] if more else None)


def save(path):
    bpy.ops.wm.save_as_mainfile(filepath=path)
