"""
Builds the Dash skill's animation (skill.Dash) in Blender: a player-shaped rig (the vanilla player model's six parts,
Steve's wide arms) dives forward, plants its hands, tucks into a forward roll over the right shoulder and comes up
running, standing again by the last frame - no pause on the ground.

  whole       the whole body, turning round its middle (the roll itself: 0 to 400 degrees, back to 360 as the
              chest rises over the knees); its height is set frame by frame so the lowest point of the body is
              on the ground
  head, body, right_arm, left_arm, right_leg, left_leg
              the vanilla parts, at their vanilla pivots, rotated on top of the whole
  eye         the first-person camera (1.62 blocks up at rest), on the head

  blender -b --python tools/models/build_dash.py -- <out.blend>

then tools/models/export_anim.py makes client/DashAnimData.java. 20 frames a second = one frame a game tick; 12 ticks.
"""
import os
import sys
import tempfile

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcmodel as mc  # noqa: E402

LENGTH = 12
WHOLE = (0.0, 8.0, -2.0)  # the roll's centre, model space (the middle of the curled-up body)

PARTS = [  # name, parent, pivot (absolute, model space), rest rotation
    ("whole", None, WHOLE, (0, 0, 0)),
    ("head", "whole", (0, 0, 0), (0, 0, 0)),
    ("body", "whole", (0, 0, 0), (0, 0, 0)),
    ("right_arm", "whole", (-5, 2, 0), (0, 0, 0)),
    ("left_arm", "whole", (5, 2, 0), (0, 0, 0)),
    ("right_leg", "whole", (-1.9, 12, 0), (0, 0, 0)),
    ("left_leg", "whole", (1.9, 12, 0), (0, 0, 0)),
]
CUBES = [  # name, part, origin (part space), size, texture offset (the vanilla skin layout)
    ("head", "head", (-4, -8, -4), (8, 8, 8), (0, 0)),
    ("body", "body", (-4, 0, -2), (8, 12, 4), (16, 16)),
    ("right_arm", "right_arm", (-3, -2, -2), (4, 12, 4), (40, 16)),
    ("left_arm", "left_arm", (-1, -2, -2), (4, 12, 4), (32, 48)),
    ("right_leg", "right_leg", (-2, 0, -2), (4, 12, 4), (0, 16)),
    ("left_leg", "left_leg", (-2, 0, -2), (4, 12, 4), (16, 48)),
]

# the roll, a key every tick: whole (x, y, z), head, arms and legs (x, y, z) in degrees, offsets from standing.
# x > 0 tips forward (the head goes down and to the front); the "world" angle of a limb is whole x + its own x,
# 0 hanging down, -90 straight forward.
KEYS = {
    #         whole            head            right_arm          left_arm          right_leg        left_leg
    0:  ((0, 0, 0),       (0, 0, 0),      (0, 0, 0),         (0, 0, 0),        (0, 0, 0),       (0, 0, 0)),
    1:  ((22, 0, 0),      (12, 0, 0),     (-55, 0, 6),       (-40, 0, -6),     (-28, 0, 2),     (26, 0, -2)),
    2:  ((58, -4, -3),    (34, 6, 0),     (-112, 6, 10),     (-100, -4, -8),   (-38, 0, 3),     (8, 0, -3)),
    3:  ((92, -8, -6),    (62, 14, 4),    (-96, 10, 12),     (-90, -6, -12),   (-50, 0, 4),     (-38, 0, -4)),
    4:  ((148, -10, -9),  (70, 18, 6),    (-118, 8, 8),      (-112, -4, -10),  (-74, 4, 6),     (-66, -4, -6)),
    5:  ((204, -10, -10), (66, 16, 6),    (-128, 4, 6),      (-122, -2, -8),   (-112, 6, 8),    (-104, -6, -8)),
    6:  ((260, -8, -8),   (60, 12, 4),    (-124, 2, 6),      (-118, 0, -6),    (-118, 4, 6),    (-110, -4, -6)),
    7:  ((316, -5, -5),   (44, 6, 2),     (-104, 0, 8),      (-96, 0, -8),     (-100, 2, 4),    (-92, -2, -4)),
    8:  ((366, -2, -2),   (22, 2, 0),     (-78, 0, 12),      (-70, 0, -12),    (-66, 0, 3),     (-58, 0, -3)),
    9:  ((398, 0, 0),     (-26, 0, 0),    (-62, 0, 10),      (-54, 0, -10),    (-42, 0, 2),     (-34, 0, -2)),
    10: ((384, 0, 0),     (-16, 0, 0),    (-34, 0, 6),       (-26, 0, -6),     (-16, 0, 1),     (-10, 0, -1)),
    11: ((366, 0, 0),     (-4, 0, 0),     (-9, 0, 2),        (-6, 0, -2),      (-3, 0, 0),      (-2, 0, 0)),
    12: ((360, 0, 0),     (0, 0, 0),      (0, 0, 0),         (0, 0, 0),        (0, 0, 0),       (0, 0, 0)),
}
LIMBS = ("whole", "head", "right_arm", "left_arm", "right_leg", "left_leg")


def skin(path):
    """A plain skin to see the rig by: skin, a shirt, trousers, shoes; eyes so the front shows."""
    t = mc.Tex(64, 64)
    skin_c, hair, shirt, trousers, shoe = (198, 150, 112), (70, 45, 30), (60, 150, 160), (60, 60, 140), (60, 50, 45)
    for name, part, origin, size, uv in CUBES:
        w, h, d = size
        for face, rect in mc.faces(uv[0], uv[1], w, h, d).items():
            c = {"head": skin_c, "body": shirt, "right_arm": shirt, "left_arm": shirt,
                 "right_leg": trousers, "left_leg": trousers}[name]
            t.fill(rect, c)
            if name in ("right_arm", "left_arm"):
                t.fill((rect[0], rect[1] + rect[3] - 4, rect[2], 4), skin_c)
            if name in ("right_leg", "left_leg"):
                t.fill((rect[0], rect[1] + rect[3] - 2, rect[2], 2), shoe)
            if name == "head" and face != "bottom":
                t.fill((rect[0], rect[1], rect[2], 2 if face != "top" else rect[3]), hair)
                if face == "front":
                    t.fill((rect[0] + 1, rect[1] + 4, 2, 1), (255, 255, 255))
                    t.fill((rect[0] + 5, rect[1] + 4, 2, 1), (255, 255, 255))
                    t.fill((rect[0] + 2, rect[1] + 4, 1, 1), (60, 60, 160))
                    t.fill((rect[0] + 5, rect[1] + 4, 1, 1), (60, 60, 160))
    t.save(path)


def main():
    out = sys.argv[sys.argv.index("--") + 1:][0]
    tex = os.path.join(tempfile.gettempdir(), "divinis_dash_skin.png")
    skin(tex)
    spec = []
    for name, parent, pivot, rest in PARTS:
        if parent:  # a child's pivot is relative to the whole's
            pivot = tuple(pivot[i] - WHOLE[i] for i in range(3))
        spec.append((name, parent, pivot, rest))
    scene, parts = mc.build_scene(spec, CUBES, tex, 64, 64, "dash")

    for frame, rots in KEYS.items():
        for part, rot in zip(LIMBS, rots):
            mc.key(parts, frame, part, rot=rot)
        mc.key(parts, frame, "body", rot=(0, 0, 0))

    # each frame, the whole goes up or down so the lowest corner of the body touches the ground
    meshes = [o for o in scene.objects if o.type == "MESH"]
    lows = {}
    for frame in KEYS:
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        lows[frame] = min((o.matrix_world @ Vector(c)).z for o in meshes for c in o.bound_box)
    for frame, low in lows.items():
        mc.key(parts, frame, "whole", pos=(0, low if abs(low) > 0.01 else 0.0, 0))

    eye = bpy.data.objects.new("eye", None)
    eye.parent = parts["head"]
    eye.location = mc.m2b(0, -1.92, 0)  # 1.62 blocks up: the vanilla eye height
    scene.collection.objects.link(eye)

    scene.frame_start, scene.frame_end = 0, LENGTH
    scene["mc_anims"] = [f"dash:0:{LENGTH}:once"]
    scene["mc_preview_z"] = 14
    mc.save(os.path.abspath(out))
    print("saved", out, "lowest corners", {f: round(v, 2) for f, v in lows.items()})


main()
