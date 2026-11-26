"""
Renders preview pictures of a model .blend.

  blender -b tools/models/<model>.blend --python tools/models/preview_model.py -- <out_dir> <view size> frame [frame ...]

view size: model pixels the picture spans (about the model's height plus a margin).
"""
import math
import os
import sys

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
out_dir, size = argv[0], float(argv[1])
frames = [int(x) for x in argv[2:]] or [0]
base = os.path.splitext(os.path.basename(bpy.data.filepath))[0]

scene = bpy.context.scene
scene.render.engine = "BLENDER_WORKBENCH"
scene.display.shading.light = "STUDIO"
scene.display.shading.color_type = "TEXTURE"
scene.display.shading.show_backface_culling = False
scene.render.resolution_x = 480
scene.render.resolution_y = 480
scene.render.image_settings.file_format = "PNG"
scene.world = scene.world or bpy.data.worlds.new("w")
scene.world.color = (0.18, 0.2, 0.25)

# the ground (model y = 24), so parts sunk into it are hidden as in the game
bpy.ops.mesh.primitive_plane_add(size=400, location=(0, 0, 0))
ground_mat = bpy.data.materials.new("ground")
ground_mat.diffuse_color = (0.25, 0.22, 0.2, 1.0)
bpy.context.object.data.materials.append(ground_mat)

cam_data = bpy.data.cameras.new("cam")
cam_data.type = "ORTHO"
cam_data.ortho_scale = size
cam = bpy.data.objects.new("cam", cam_data)
scene.collection.objects.link(cam)
scene.camera = cam

views = {"front": -25, "side": -80, "back": 155}
target = Vector((0, 0, scene.get("mc_preview_z", size * 0.36)))  # models not standing on the ground set their middle
for frame in frames:
    scene.frame_set(frame)
    for name, yaw in views.items():
        a = math.radians(yaw)
        cam.location = target + Vector((math.sin(a) * size * 2, -math.cos(a) * size * 2, size * 0.5))
        cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
        scene.render.filepath = os.path.join(out_dir, f"{base}_{frame:03d}_{name}.png")
        bpy.ops.render.render(write_still=True)
print("previews in", out_dir)
