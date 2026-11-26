"""
Turns a model .blend (made by a build_*.py script, maybe edited by hand since) into Java model code.

  blender -b tools/models/<model>.blend --python tools/models/export_model.py -- <Out.java> <texture path for the doc>

The class is named after the file. Read from the scene (see mcmodel.py):
- parts: Empties with "mc_part"; pivot = their location (or "mc_pivot" if the location is animated);
- cubes: meshes parented to a part; the box is their bounding box, the texture offset "mc_tex";
- animations: scene property "mc_anims"; keyframes = the keyed frames of each part inside the range, as offsets
  from the rest pose (rotation, position) or as factors (scale). A keyframe whose Blender interpolation is LINEAR
  is reached linearly, others smoothly.
"""
import math
import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import FPS, fcurves, mc_rot_to_euler  # noqa: E402


def b2m(x, y, z):
    return (x, -z, y)


def euler_to_mc(e):
    """Blender euler (XZY order) -> model rotation in radians (x, y, z)."""
    return (e[0], -e[2], e[1])


def f(v):
    v = round(v, 4)
    if v == 0:
        v = 0.0
    return f"{v}F"


JAVA_WORDS = {"float", "int", "double", "long", "short", "byte", "char", "boolean", "class", "new", "case", "default",
              "do", "if", "for", "while", "switch", "return", "this", "super", "static", "final", "void", "null", "true",
              "false", "public", "private", "protected", "package", "import", "interface", "enum", "extends", "throw",
              "try", "catch", "goto", "const", "native", "var", "record", "yield", "root", "mesh"}


def camel(name):
    head, *rest = name.split("_")
    word = head + "".join(p.capitalize() for p in rest)
    return word + "Part" if word in JAVA_WORDS else word  # a part may not be named like a Java word (or the locals)


def main():
    args = sys.argv[sys.argv.index("--") + 1:]
    out, tex_doc = args[0], args[1]
    cls = os.path.splitext(os.path.basename(out))[0]
    blend = os.path.basename(bpy.data.filepath)
    scene = bpy.context.scene
    tex_w, tex_h = scene.get("mc_tex_size", (64, 64))
    scene.frame_set(scene.frame_start)
    parts = [o for o in scene.objects if o.get("mc_part")]

    def parent_part(o):
        return o.parent if o.parent and o.parent.get("mc_part") else None

    ordered, seen = [], set()

    def visit(o):
        if o.name in seen:
            return
        p = parent_part(o)
        if p:
            visit(p)
        seen.add(o.name)
        ordered.append(o)

    for o in parts:
        visit(o)

    def pivot(o):
        animated = any(fc.data_path == "location" for fc in fcurves(o))
        if animated and "mc_pivot" in o:
            return tuple(o["mc_pivot"])
        return b2m(*o.location)

    lines = []
    for o in ordered:
        cubes = sorted((c for c in o.children if c.type == "MESH"), key=lambda c: c.name)
        builder = "CubeListBuilder.create()"
        for c in cubes:
            pts = [b2m(*v.co) for v in c.data.vertices]
            lo = [min(p[i] for p in pts) for i in range(3)]
            hi = [max(p[i] for p in pts) for i in range(3)]
            u, v = c.get("mc_tex", (0, 0))
            builder += (f"\n                .texOffs({int(u)}, {int(v)}).addBox({f(lo[0])}, {f(lo[1])}, {f(lo[2])}, "
                        f"{f(hi[0] - lo[0])}, {f(hi[1] - lo[1])}, {f(hi[2] - lo[2])})")
        px, py, pz = pivot(o)
        rx, ry, rz = (math.radians(d) for d in o.get("mc_rest_rot", (0, 0, 0)))
        parent = parent_part(o)
        pvar = camel(parent.name) if parent else "root"
        lines.append(f"        PartDefinition {camel(o.name)} = {pvar}.addOrReplaceChild(\"{o.name}\", {builder},\n"
                     f"                PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)}));")

    anims = []
    for spec in scene.get("mc_anims", []):
        name, start, end, mode = spec.split(":")
        start, end = int(start), int(end)
        length = (end - start) / FPS
        channels = []
        for o in ordered:
            curves = fcurves(o)
            rest_e = mc_rot_to_euler(o.get("mc_rest_rot", (0, 0, 0)))
            rest_p = pivot(o)
            for path, target in (("rotation_euler", "ROTATION"), ("location", "POSITION"), ("scale", "SCALE")):
                interp = {}
                for fc in curves:
                    if fc.data_path != path:
                        continue
                    for k in fc.keyframe_points:
                        if start <= k.co[0] <= end:
                            fr = round(k.co[0])
                            interp[fr] = interp.get(fr, True) and k.interpolation == "LINEAR"
                if not interp:
                    continue
                keys = []
                for fr in sorted(interp):
                    scene.frame_set(fr)
                    if path == "rotation_euler":
                        d = euler_to_mc([o.rotation_euler[i] - rest_e[i] for i in range(3)])
                        vec = "KeyframeAnimations.degreeVec({}, {}, {})".format(*(f(math.degrees(x)) for x in d))
                    elif path == "scale":
                        sc = (o.scale[0], o.scale[2], o.scale[1])  # Blender (x, y, z) -> model (x, z, y)
                        d = [sc[i] - 1 for i in range(3)]
                        vec = "KeyframeAnimations.scaleVec({}, {}, {})".format(*(f(x) for x in sc))
                    else:
                        cur = b2m(*o.location)
                        d = [cur[i] - rest_p[i] for i in range(3)]
                        vec = f"KeyframeAnimations.posVec({f(d[0])}, {f(-d[1])}, {f(d[2])})"  # posVec flips y back
                    keys.append((fr, d, vec, "LINEAR" if interp[fr] else "CATMULLROM"))
                if all(abs(x) < 1e-4 for _, d, _, _ in keys for x in d):
                    continue
                body = ",\n".join(f"                    new Keyframe({f((fr - start) / FPS)}, {vec}, "
                                  f"AnimationChannel.Interpolations.{ip})" for fr, _, vec, ip in keys)
                channels.append(f"            .addAnimation(\"{o.name}\", new AnimationChannel(AnimationChannel.Targets.{target},\n"
                                f"{body}))")
        loop = ".looping()" if mode == "loop" else ""
        # each in a method of its own: a class's static initialiser may not grow past 64 KB of code
        method = camel(name) + "Animation"
        anims.append(f"    public static final AnimationDefinition {name.upper()} = {method}();\n\n"
                     f"    private static AnimationDefinition {method}() {{\n"
                     f"        return AnimationDefinition.Builder.withLength({f(length)}){loop}\n"
                     + "\n".join(channels) + "\n            .build();\n    }")
    scene.frame_set(scene.frame_start)

    java = f"""package com.divinis.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/models/export_model.py from tools/models/{blend} - edit the .blend, then export again.
 * Geometry and animations (texture {tex_w}x{tex_h}, {tex_doc}).
 */
public final class {cls} {{
    private {cls}() {{}}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{chr(10).join(lines)}
        return LayerDefinition.create(mesh, {tex_w}, {tex_h});
    }}

{(chr(10) + chr(10)).join(anims)}
}}
"""
    with open(out, "w", newline="\n") as fh:
        fh.write(java)
    print("exported", out, "parts:", len(ordered), "animations:", len(anims))


main()
