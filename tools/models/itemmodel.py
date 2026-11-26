"""
Shared helpers for building Minecraft item models (JSON elements) in Blender (used by build_crystal.py,
build_minotaur_axe.py; export_item.py writes the JSON).

Scene layout: model units (y up) = Blender units, the model's north (-z) looks towards -Y.
Each element is a mesh object placed at its rotation origin, rotated about one axis by a multiple of 22.5 degrees
(as Minecraft allows), with its face UVs (0..16 whatever the texture size) in the custom property "mc_uv"
(north, south, east, west, up, down), and its light in "mc_glow" (0..15, NeoForge's emissive elements). Scene
properties: "mc_texture" (texture id), "mc_display" (JSON, optional).
"""
import json
import math

import bmesh
import bpy

FACES = ("north", "south", "east", "west", "up", "down")


def j2b(x, y, z):
    """JSON model space -> Blender (a rotation, not a mirror; north = -Y)."""
    return (-(x - 8), (z - 8), y)


def el(name, frm, to, axis=None, angle=0.0, origin=None, uv=(0, 0, 16, 16), faces=None, glow=0):
    """An element; uv is used for every face unless faces (face -> uv) says otherwise; glow: block light 0..15 the
    element is drawn with (NeoForge's emissive elements)."""
    origin = origin or [(frm[i] + to[i]) / 2 for i in range(3)]
    uvs = {f: list(uv) for f in FACES}
    for f, u in (faces or {}).items():
        uvs[f] = list(u)
    return dict(name=name, frm=list(frm), to=list(to), axis=axis, angle=angle, origin=list(origin), uv=uvs, glow=glow)


def mesh_for(e, mat):
    """Box between from/to, vertices relative to the rotation origin, with Minecraft face UVs."""
    (x0, y0, z0), (x1, y1, z1) = e["frm"], e["to"]
    o = e["origin"]
    corners = {}
    for xi, x in enumerate((x0, x1)):
        for yi, y in enumerate((y0, y1)):
            for zi, z in enumerate((z0, z1)):
                p = j2b(x, y, z)
                q = j2b(*o)
                corners[(xi, yi, zi)] = (p[0] - q[0], p[1] - q[1], p[2] - q[2])
    # each face: corners in the order top-left, top-right, bottom-right, bottom-left as seen from outside
    quads = {
        "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
        "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
        "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
        "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
        "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
        "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
    }
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    for face, idx in quads.items():
        vs = [bm.verts.new(corners[i]) for i in idx]
        bf = bm.faces.new(vs)
        u1, v1, u2, v2 = e["uv"][face]
        for loop, (uu, vv) in zip(bf.loops, [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]):
            loop[uvl].uv = (uu / 16, 1 - vv / 16)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(e["name"])
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    return me


def build(elements, tex_path, texture_id, blend_path, display=None):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    image = bpy.data.images.load(tex_path)
    image.pack()
    mat = bpy.data.materials.new(texture_id.split("/")[-1])
    mat.use_nodes = True
    node = mat.node_tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    node.interpolation = "Closest"
    mat.node_tree.links.new(node.outputs["Color"], mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"])
    mat.node_tree.links.new(node.outputs["Alpha"], mat.node_tree.nodes["Principled BSDF"].inputs["Alpha"])

    for e in elements:
        ob = bpy.data.objects.new(e["name"], mesh_for(e, mat))
        ob.location = j2b(*e["origin"])
        if e["axis"]:
            # Blender axis for each model axis: x -> -X, y -> Z, z -> Y
            axis_index, sign = {"x": (0, -1), "y": (2, 1), "z": (1, 1)}[e["axis"]]
            rot = [0.0, 0.0, 0.0]
            rot[axis_index] = math.radians(e["angle"]) * sign
            ob.rotation_euler = rot
        ob["mc_el"] = 1
        ob["mc_axis"] = e["axis"] or ""
        ob["mc_uv"] = [v for f in FACES for v in e["uv"][f]]
        ob["mc_glow"] = e.get("glow", 0)
        scene.collection.objects.link(ob)
    scene["mc_texture"] = texture_id
    if display:
        scene["mc_display"] = json.dumps(display)
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)
