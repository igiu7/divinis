"""
The Smelting Forge as a multiblock (forge.SmeltingForge): what the formed forge looks like, its textures, and the
blocks it is built of.

The player builds a 3x3x3 of Forge Bricks with the Smelting Forge block at the front of the bottom layer (the
firebox), the block above it (the charging mouth) and the middle of the cube left empty (the hearth). Formed, the blocks
are hidden and the controller's renderer draws this model: a bloomery stack - a stone plinth, walls of fired bricks
held by iron hoops (broken round the mouths), the firebox mouth at the bottom behind an iron lintel, the charging
mouth above it opening on the hearth where the ingots lie, the shoulders drawing in to a tall chimney with iron bands,
and the iron tuyere on its side where the bellows blows in (the bellows itself is drawn by the station, as the player
works it: client.forge.SmeltingForgeBehavior). Three fires: cold (the firebox dark), smoulder (the coal a bed of dull
embers) and lit (the coal glowing, the hearth's iron floor and back wall aglow).

Textures in the manner of today's Minecraft - 16 texels a block, clean shapes, few shades, a light top-left edge and a
dark bottom-right one on every brick:
  textures/block/smelting_forge_atlas.png  64x64: sixteen 16x16 tiles for the formed model (every face takes its
                                           texels from its place in the world, so bricks run on across elements)
  textures/block/forge_bricks.png          the Forge Bricks block
  textures/block/smelting_forge_front.png  the Smelting Forge block's front (an iron firebox door in the bricks)

Model space: the forge facing north, the controller block at x 0..16, z 0..16, y 0..16; the structure x -16..32,
y 0..48, z 0..48, the chimney and bellows past it. Elements are cut at every 16 px, and each block cell's elements go
into a model of their own, in that cell's coordinates (models/block/smelting_forge/<cell>[_smoulder|_lit].json; JSON models
must stay within -16..32), listed in src/main/resources/divinis/smelting_forge_parts.json for the renderer.
A .blend of the whole model is saved for previews.

  blender -b --python tools/models/build_smelting_forge.py -- <blend dir> <assets dir> <resources dir>
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemmodel import build, el  # noqa: E402
from mcmodel import Tex  # noqa: E402

TILES = ["bricks", "sooty", "stone", "iron", "grate", "coal", "ash", "glow", "leather", "wood", "cap", "hot_grate",
         "inner", "iron_dark", "embers", "hot_inner"]

BRICK = [(48, 30, 26), (66, 42, 34), (86, 54, 42), (104, 66, 50), (122, 80, 60), (142, 96, 72)]
SOOT = [(26, 22, 22), (36, 30, 29), (48, 40, 37), (62, 52, 47), (78, 66, 58)]
MORTAR = [(40, 36, 34), (56, 52, 48), (70, 66, 62)]
STONE = [(34, 32, 36), (46, 44, 49), (60, 58, 64), (76, 74, 80), (94, 92, 98)]
IRON = [(26, 27, 31), (42, 44, 50), (60, 63, 70), (82, 85, 94), (108, 112, 122), (138, 142, 152)]
COAL = [(16, 12, 12), (34, 22, 18), (120, 32, 10), (196, 72, 14), (244, 132, 30), (255, 200, 90), (255, 238, 170)]
ASH = [(40, 38, 37), (56, 54, 52), (74, 72, 70), (92, 90, 88)]
GLOW = [(110, 30, 12), (160, 52, 14), (206, 86, 20), (238, 128, 34), (252, 176, 70), (255, 220, 130)]
LEATHER = [(46, 28, 18), (66, 42, 26), (88, 58, 36), (110, 74, 46), (132, 92, 58)]
WOOD = [(52, 36, 22), (72, 50, 30), (94, 66, 40), (116, 84, 52), (138, 102, 64)]


def tile_painters(rnd):
    def bricks(ramp, x, y, soot=0.0):
        # bricks 8 by 4, each row shifted by 4; the mortar a single darker line
        row = y // 4
        bx = (x + (4 if row % 2 else 0)) % 8
        if y % 4 == 3 or bx == 7:
            return MORTAR[1] if not soot else SOOT[1]
        level = 2.6 + ((x // 8 + row * 3 + (1 if row % 2 and bx >= 4 else 0)) * 5 % 3 - 1) * 0.5
        if y % 4 == 0 or bx == 0:
            level += 1.2
        if y % 4 == 2 or bx == 6:
            level -= 0.8
        if rnd.random() < 0.06:
            level -= 1
        return ramp[max(0, min(len(ramp) - 1, int(round(level - soot))))]

    def pick(ramp, level):
        return ramp[max(0, min(len(ramp) - 1, int(round(level))))]

    def stone(x, y):
        # big dressed blocks, 8 by 8, a chiselled edge
        if x % 8 == 7 or y % 8 == 7:
            return STONE[0]
        level = 2.2 + (1.2 if x % 8 == 0 or y % 8 == 0 else 0) + (0.6 if (x // 8 + y // 8) % 2 else 0) - (0.9 if rnd.random() < 0.08 else 0)
        return pick(STONE, level)

    def iron(x, y, ramp=IRON, rivets=True):
        level = 2.6 + (1.4 if y == 0 or x == 0 else 0) - (1.4 if y == 15 or x == 15 else 0)
        if rivets and x in (2, 13) and y in (2, 13):
            return ramp[5] if (x, y) == (2, 2) else ramp[4]
        if rivets and x in (3, 14) and y in (3, 14):
            return ramp[0]
        return pick(ramp, level + (0.4 if (x * 3 + y) % 13 == 0 else 0))

    def grate(x, y, ramp=IRON):
        if x % 4 == 3:
            return ramp[0]
        return pick(ramp, 3 + (1 if x % 4 == 0 else 0) - (1 if y in (0, 15) else 0))

    def coal(x, y):
        lump = ((x // 4) * 7 + (y // 4) * 3) % 5
        edge = x % 4 == 0 or y % 4 == 0
        if edge:
            return pick(COAL, 4.4 + (1.2 if (x + y) % 3 == 0 else 0))
        return pick(COAL, 0.6 + lump * 0.35 + (2.6 if (x * 5 + y * 7) % 17 == 0 else 0))

    def ash(x, y):
        return pick(ASH, 1.5 + ((x * 7 + y * 13) % 5 - 2) * 0.5)

    def glow(x, y):
        return pick(GLOW, 2.6 + ((x * 3 + y * 5) % 7 - 3) * 0.4 + (1.2 if (x + y * 2) % 9 == 0 else 0))

    def leather(x, y):
        fold = y % 4
        return pick(LEATHER, 3.0 - fold * 0.7 + (0.6 if x == 0 else 0) - (0.6 if x == 15 else 0))

    def wood(x, y):
        if y % 8 == 7:
            return WOOD[0]
        return pick(WOOD, 2.6 + (1 if y % 8 == 0 else 0) + (-0.8 if (x * 5 + y) % 11 == 0 else 0))

    def cap(x, y):
        return iron(x, y, IRON, rivets=False)

    return {
        "bricks": lambda x, y: bricks(BRICK, x, y),
        "sooty": lambda x, y: bricks(BRICK, x, y, soot=1.6) if y > 3 else bricks(SOOT, x, y),
        "stone": stone,
        "iron": iron,
        "grate": grate,
        "coal": coal,
        "ash": ash,
        "glow": glow,
        "leather": leather,
        "wood": wood,
        "cap": cap,
        "hot_grate": lambda x, y: grate(x, y, [GLOW[0], GLOW[1], GLOW[2], GLOW[3], GLOW[4], GLOW[5]]),
        "inner": lambda x, y: bricks(SOOT, x, y),
        "iron_dark": lambda x, y: pick(IRON, 1.4 + (1 if y == 0 else 0) - (1 if y == 15 else 0)),
        "embers": lambda x, y: pick([(14, 10, 10), (26, 16, 14), (58, 16, 8), (96, 26, 10), (140, 42, 12)],
                                     0.6 + (2.6 if (x % 4 == 0 or y % 4 == 0) and (x * 3 + y) % 5 < 2 else (x * 7 + y * 5) % 3 * 0.4)),
        "hot_inner": lambda x, y: bricks(GLOW, x, y),
    }


def paint(assets):
    rnd = random.Random(21)
    painters = tile_painters(rnd)
    atlas = Tex(64, 64, seed=3)
    for i, name in enumerate(TILES):
        tx, ty = (i % 4) * 16, (i // 4) * 16
        for y in range(16):
            for x in range(16):
                atlas.set(tx + x, ty + y, painters[name](x, y), 0.0)
    atlas.save(assets + "textures/block/smelting_forge_atlas.png")
    rnd.seed(21)
    bricks = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            bricks.set(x, y, painters["bricks"](x, y), 0.0)
    bricks.save(assets + "textures/block/forge_bricks.png")
    # the controller's front: an iron firebox door set in the bricks
    front = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            c = painters["bricks"](x, y)
            if 3 <= x <= 12 and 5 <= y <= 14:
                c = painters["iron"](x - 3 + (3 if x > 7 else 0), y - 5 + 3)
                if y == 5 or x in (3, 12):
                    c = IRON[3]
                if 6 <= y <= 7 and 5 <= x <= 10 and x % 2 == 1:
                    c = COAL[1]           # the vent slits
                if x == 10 and y == 10:
                    c = IRON[5]           # the latch
            front.set(x, y, c, 0.0)
    front.save(assets + "textures/block/smelting_forge_front.png")


# ------------------------------------------------------------------ the formed model

def box(frm, to, tile, faces=None, glow=0):
    return {"frm": list(frm), "to": list(to), "tile": tile, "faces": faces or {}, "glow": glow}


FIRES = ("cold", "smoulder", "lit")


def model(fire):
    lit = fire == "lit"
    b, s, st, ir = "bricks", "sooty", "stone", "iron"
    inner = "hot_inner" if lit else "inner"
    out = [
        # the plinth
        box([-16, 0, 0], [32, 4, 48], st),
        # the body round the firebox and the hearth: left, right, back, the posts of the mouths, the hearth floor
        box([-15, 4, 1], [2, 40, 47], b, {"east": inner}),
        box([14, 4, 1], [31, 40, 47], b, {"west": inner}),
        box([2, 4, 30], [14, 40, 47], b, {"north": inner}),
        box([2, 4, 14], [14, 13, 30], b, {"north": inner}),
        box([2, 4, 1], [3, 13, 14], b, {"east": inner}),
        box([13, 4, 1], [14, 13, 14], b, {"west": inner}),
        box([2, 13, 1], [14, 16, 30], b, {"down": inner, "up": "hot_grate" if lit else "grate"}, glow=10 if lit else 0),
        box([2, 32, 1], [14, 40, 30], b, {"down": inner}),
        # the firebox floor
        box([3, 4, 1], [13, 4.5, 14], "ash"),
        # iron: the lintel over the firebox, the frame of the charging mouth, two hoops round the body
        box([1.5, 13, 0], [14.5, 15.5, 1], ir),
        box([1, 32, 0], [15, 34, 1], ir),
        box([1, 16, 0], [2, 32, 1], ir),
        box([14, 16, 0], [15, 32, 1], ir),
        box([-16, 9, 0], [2, 11, 1], ir), box([14, 9, 0], [32, 11, 1], ir), box([-16, 9, 47], [32, 11, 48], ir),
        box([-16, 9, 1], [-15, 11, 47], ir), box([31, 9, 1], [32, 11, 47], ir),
        box([-16, 28, 0], [1, 30, 1], ir), box([15, 28, 0], [32, 30, 1], ir), box([-16, 28, 47], [32, 30, 48], ir),
        box([-16, 28, 1], [-15, 30, 47], ir), box([31, 28, 1], [32, 30, 47], ir),
        # the shoulders drawing in, sooty as they rise
        box([-13, 40, 3], [29, 46, 45], s),
        box([-9, 46, 7], [25, 52, 41], s),
        box([-5, 52, 11], [21, 56, 37], s),
        # the chimney with its iron bands and cap
        box([-1, 56, 15], [17, 80, 33], s, {"up": "inner"}),
        box([-1.5, 62, 14.5], [17.5, 64, 33.5], "iron_dark"),
        box([-1.5, 72, 14.5], [17.5, 74, 33.5], "iron_dark"),
        box([-2.5, 80, 13.5], [18.5, 82, 34.5], "cap", {"up": "inner"}),
        # the tuyere on the side (the screen's right seen from the front: -x), where the bellows blows in
        box([-17, 4, 6.5], [-15, 6.5, 9.5], "iron_dark"),
    ]
    if fire == "smoulder":
        out += [
            box([3.5, 4.5, 6], [12.5, 5.5, 13.5], "embers", glow=7),
            box([4.5, 5.5, 7], [8, 6.5, 10.5], "embers", glow=7),
            box([8.5, 5.5, 8.5], [12, 6.2, 12.5], "embers", glow=7),
        ]
    if lit:
        out += [
            box([3.5, 4.5, 6], [12.5, 6, 13.5], "coal", glow=15),
            box([4.5, 6, 7], [8, 7.5, 10.5], "coal", glow=15),
            box([8.5, 6, 8], [12, 7.2, 12], "coal", glow=15),
            box([5.5, 6, 10], [9, 7, 13.5], "coal", glow=15),
            # the hearth's back wall aglow
            box([2.5, 16, 29.5], [13.5, 32, 30], "glow", glow=12),
        ]
    return out


def split(e):
    """Cuts an element at every 16 px, so each piece lies in one block cell."""
    pieces = [e]
    for axis in range(3):
        nxt = []
        for p in pieces:
            lo, hi = p["frm"][axis], p["to"][axis]
            cuts = [lo] + [c for c in range(int(math.floor(lo / 16)) * 16 + 16, int(math.ceil(hi)), 16) if lo < c < hi] + [hi]
            for a, z in zip(cuts, cuts[1:]):
                q = dict(p, frm=list(p["frm"]), to=list(p["to"]))
                q["frm"][axis], q["to"][axis] = a, z
                nxt.append(q)
        pieces = nxt
    return pieces


FACE_AXES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def face_uv(e, face, cell):
    """The face's texels from its tile: the texels its place in the world gives (so bricks run on), in the atlas's UVs."""
    tile = e["faces"].get(face, e["tile"])
    i = TILES.index(tile)
    tx, ty = (i % 4) * 16, (i // 4) * 16
    ua, va = FACE_AXES[face]
    u0, u1 = e["frm"][ua] - cell[ua] * 16, e["to"][ua] - cell[ua] * 16
    if va == 1:   # upright faces: the texture's top at the top
        v0, v1 = 16 - (e["to"][1] - cell[1] * 16), 16 - (e["frm"][1] - cell[1] * 16)
    else:
        v0, v1 = e["frm"][2] - cell[2] * 16, e["to"][2] - cell[2] * 16
    return [round((tx + u0) / 4, 4), round((ty + v0) / 4, 4), round((tx + u1) / 4, 4), round((ty + v1) / 4, 4)]


def cells(elements):
    out = {}
    for e in elements:
        for p in split(e):
            cell = tuple(int(math.floor(p["frm"][a] / 16)) for a in range(3))
            out.setdefault(cell, []).append(p)
    return out


def r(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def write_parts(assets, resources):
    names = {}
    for fire in FIRES:
        for cell, pieces in cells(model(fire)).items():
            name = "c%d_%d_%d" % cell
            elements = []
            for p in pieces:
                frm = [r(p["frm"][a] - cell[a] * 16) for a in range(3)]
                to = [r(p["to"][a] - cell[a] * 16) for a in range(3)]
                faces = {f: {"uv": face_uv(p, f, cell), "texture": "#0"} for f in FACE_AXES}
                element = {"from": frm, "to": to, "faces": faces}
                if p["glow"]:
                    element["neoforge_data"] = {"block_light": p["glow"], "sky_light": p["glow"]}
                elements.append(element)
            path = assets + "models/block/smelting_forge/" + name + ("" if fire == "cold" else "_" + fire) + ".json"
            os.makedirs(os.path.dirname(path), exist_ok=True)
            json.dump({"textures": {"0": "divinis:block/smelting_forge_atlas", "particle": "divinis:block/forge_bricks"},
                       "elements": elements}, open(path, "w"), indent=1)
            names.setdefault(name, {"cell": list(cell), "cold": False, "smoulder": False, "lit": False})
            names[name][fire] = True
    json.dump({"_readme": "Written by tools/models/build_smelting_forge.py: the formed Smelting Forge's models, one per block cell "
                          "(cell = its offset from the controller, in blocks, the forge facing north).",
               "parts": names}, open(resources + "divinis/smelting_forge_parts.json", "w"), indent=1)
    return len(names)


def preview(blends, assets):
    for fire in FIRES:
        els = []
        for i, p in enumerate(q for e in model(fire) for q in split(e)):
            cell = tuple(int(math.floor(p["frm"][a] / 16)) for a in range(3))
            els.append(el("e%03d" % i, p["frm"], p["to"], faces={f: face_uv(p, f, cell) for f in FACE_AXES}, glow=p["glow"]))
        name = "smelting_forge_formed" + ("" if fire == "cold" else "_" + fire)
        build(els, assets + "textures/block/smelting_forge_atlas.png", "divinis:block/smelting_forge_atlas",
              os.path.join(blends, name + ".blend"))


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, assets, resources = argv[0], argv[1], argv[2]
    paint(assets)
    n = write_parts(assets, resources)
    preview(blends, assets)
    print("smelting forge:", n, "cells")


main()
