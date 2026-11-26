"""
Builds the seven fantasy armour sets in Blender (in every metal of build_ingots.py: the models are the same, the
textures <metal>_<set>.png are painted in each metal's colours) - medieval fantasy armour as an isekai hero would wear it (the face
left free: circlets, headbands, open helms, hoods; capes and long coats; ornate pauldrons), worn over the player and
shown as the same 3D models in the inventory; one .blend each with a 128x128 texture:
  vanguard    Hero's Vanguard: a gold circlet with a blue gem and little silver wings at the temples; a gold-rimmed
              breastplate, layered pauldrons, a long red cape, gauntlets; tassets, knee cops, gold-trimmed boots
  swordsman   Black Swordsman: a headband with a steel forehead plate and two long tails; a long black coat with a high
              collar, silver edges and split tails to the knees, one pauldron (left), a chest guard and a strap; dark
              trousers, tall buckled boots
  paladin     Radiant Paladin: an open winged helm with a gold brow and crest; plate with a golden sun, great round
              pauldrons, a blue tabard and cape; plate legs and boots
  spellblade  Arcane Spellblade: a silver circlet with a glowing gem and blade-like fins; a midnight robe-coat with a high
              flaring collar, glowing runes down its front and on the bracers, layered shoulder plates; long robe
              panels over plate, pointed boots
  dragonknight Dragon Knight: a half helm shaped as a dragon's head over the brow, horns swept back; scale plate with a
              dragon's eye, pauldrons with rising spikes; scale tassets, spiked knees, clawed boots
  demonlord   Demon Lord's Regalia: a gold crown with great black horns; an armoured coat, a fur mantle, a long
              crimson cape, a red gem, spiked pauldrons; long coat skirts, spiked boots
  ranger      Guild Ranger: a green hood and a scarf; a leather jerkin with a light chest plate, a short hooded cape, a
              quiver and strap, bracers; belt pouches, knee guards, tall fur-cuffed boots
The capes are not in the models but real capes that swing as a player's does (client.ArmorCapeLayer): their textures
(blacksteel_<set>_cape.png, 32x32, laid out for a 10 x length x 1 box) are made here too - the outside in the set's
cloth with folds, an edging and an emblem, the inside a darker lining.
Each model has the player's parts (head, hat, body, arms, legs, at their pivots) and under them one part per armour
piece - helmet; chest, chest_l, chest_r; legs_b, legs_l, legs_r; boots_l, boots_r - which
client/BlacksteelArmorModels shows for the slot being drawn (on a player, or alone in the inventory).

  blender -b --python tools/models/build_blacksteel_armor.py -- <blend dir> <texture dir>

then tools/models/export_model.py makes client/Blacksteel<Set>ArmorModelData.java from each .blend.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcmodel import Tex, build_scene, faces, pack, save  # noqa: E402

TEX = 128
PLATE = ((16, 16, 20), (38, 38, 45), (76, 78, 88), (128, 132, 144))  # deep, base, light, shine
COMMON = {"leather": (70, 48, 32), "fur": (66, 58, 52), "gold": (214, 166, 56), "silver": (188, 194, 206), "horn": (30, 26, 28),
          "dark": (12, 12, 15)}
SETS = {
    # cloth, second cloth, gem / glow
    "vanguard": ((150, 24, 30), (90, 14, 18), (70, 150, 255)),
    "swordsman": ((24, 22, 28), (44, 42, 50), (210, 214, 230)),
    "paladin": ((40, 72, 164), (230, 226, 214), (255, 222, 120)),
    "spellblade": ((28, 34, 72), (44, 52, 100), (100, 190, 255)),
    "dragonknight": ((96, 16, 16), (60, 10, 10), (255, 70, 30)),
    "demonlord": ((112, 10, 18), (60, 6, 10), (255, 36, 44)),
    "ranger": ((48, 78, 42), (130, 104, 66), (190, 230, 150)),
}


class Armor:
    def __init__(self):
        self.parts = [("head", None, (0, 0, 0), (0, 0, 0)), ("hat", None, (0, 0, 0), (0, 0, 0)), ("body", None, (0, 0, 0), (0, 0, 0)),
                      ("right_arm", None, (-5, 2, 0), (0, 0, 0)), ("left_arm", None, (5, 2, 0), (0, 0, 0)),
                      ("right_leg", None, (-1.9, 12, 0), (0, 0, 0)), ("left_leg", None, (1.9, 12, 0), (0, 0, 0))]
        for sub, parent in (("helmet", "head"), ("chest", "body"), ("chest_l", "left_arm"), ("chest_r", "right_arm"),
                            ("legs_b", "body"), ("legs_l", "left_leg"), ("legs_r", "right_leg"),
                            ("boots_l", "left_leg"), ("boots_r", "right_leg")):
            self.parts.append((sub, parent, (0, 0, 0), (0, 0, 0)))
        self.cubes, self.kind, self.n = [], {}, 0

    def box(self, part, x0, y0, z0, x1, y1, z1, kind):
        self.n += 1
        name = f"{part}_{self.n}"
        self.cubes.append([name, part, (x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0), None])
        self.kind[name] = kind

    def deco(self, name, parent, pivot, rot):
        name = name.replace("-", "n").replace(".", "_")  # names become Java names
        self.parts.append((name, parent, pivot, rot))
        return name

    def sides(self, name, parent, pivot, rot, boxes):
        """A decoration on the left (x > 0) and its mirror on the right: boxes are (x0, y0, z0, x1, y1, z1, kind) as on the left."""
        for s, tag in ((1, "l"), (-1, "r")):
            part = self.deco(f"{name}_{tag}", parent, (s * pivot[0], pivot[1], pivot[2]), (rot[0], s * rot[1], s * rot[2]))
            for x0, y0, z0, x1, y1, z1, kind in boxes:
                if s > 0:
                    self.box(part, x0, y0, z0, x1, y1, z1, kind)
                else:
                    self.box(part, -x1, y0, z0, -x0, y1, z1, kind)
            yield part

    def ring(self, part, half, y0, y1, kind, t=0.6):
        """A band round the head (open in the middle, so it looks right on its own in the inventory too)."""
        self.box(part, -half, y0, -half, half, y1, -half + t, kind)
        self.box(part, -half, y0, half - t, half, y1, half, kind)
        self.box(part, -half, y0, -half + t, -half + t, y1, half - t, kind)
        self.box(part, half - t, y0, -half + t, half, y1, half - t, kind)

    def arm(self, x0, y0, z0, x1, y1, z1, kind, part="chest"):
        self.box(part + "_l", x0, y0, z0, x1, y1, z1, kind)
        self.box(part + "_r", -x1, y0, z0, -x0, y1, z1, kind)

    def deco_arm(self, name, pivot, rot, boxes, part="chest"):
        for s, tag in ((1, "l"), (-1, "r")):
            p = self.deco(f"{name}_{tag}", part + "_" + tag, (s * pivot[0], pivot[1], pivot[2]), (rot[0], s * rot[1], s * rot[2]))
            for x0, y0, z0, x1, y1, z1, kind in boxes:
                if s > 0:
                    self.box(p, x0, y0, z0, x1, y1, z1, kind)
                else:
                    self.box(p, -x1, y0, z0, -x0, y1, z1, kind)

    def leg(self, part, x0, y0, z0, x1, y1, z1, kind):
        self.box(part + "_l", x0, y0, z0, x1, y1, z1, kind)
        self.box(part + "_r", -x1, y0, z0, -x0, y1, z1, kind)

    def mirror(self, part, x0, y0, z0, x1, y1, z1, kind):
        self.box(part, x0, y0, z0, x1, y1, z1, kind)
        self.box(part, -x1, y0, z0, -x0, y1, z1, kind)


def wing(scale):
    s = scale
    return [(0, -3 * s, 0, 0.5, 0, 4 * s, "silver"), (0, -4.6 * s, 1 * s, 0.5, -3 * s, 4 * s, "silver"),
            (0, -5.8 * s, 2.4 * s, 0.5, -4.6 * s, 4 * s, "silver")]


def plate_boots(a, trim):
    a.leg("boots", -2.5, 7, -2.6, 2.5, 12.4, 2.6, "plate")
    a.leg("boots", -2.1, 10.6, -3.6, 2.1, 12.4, -2.6, "plate")
    a.leg("boots", -2.6, 7, -2.7, 2.6, 7.8, 2.7, trim)


def vanguard(a):
    h = "helmet"
    a.ring(h, 4.7, -6.6, -5.4, "gold")
    a.box(h, -0.8, -7.4, -5.0, 0.8, -5.5, -4.6, "gem")
    list(a.sides("wing", h, (4.7, -6.0, -0.5), (0, -25, -15), wing(1.0)))
    c = "chest"
    a.box(c, -4.5, -0.3, -2.7, 4.5, 8.5, 2.7, "plate")
    a.box(c, -4.6, -0.4, -2.8, 4.6, 0.4, 2.8, "gold")
    a.box(c, -1.0, 2.0, -2.95, 1.0, 4.5, -2.75, "gold")
    a.box(c, -0.5, 2.6, -3.05, 0.5, 3.9, -2.9, "gem")
    a.box(c, -4.4, 8.5, -2.6, 4.4, 10.5, 2.6, "leather")
    a.mirror(c, 3.0, -0.6, -2.95, 4.2, 0.8, -2.6, "gold")
    a.arm(-1.7, -3.0, -2.8, 3.7, 1.0, 2.8, "plate")
    a.arm(-1.2, -3.6, -2.2, 3.2, -2.8, 2.2, "plate")
    a.arm(-1.8, 0.6, -2.9, 3.8, 1.3, 2.9, "gold")
    a.arm(-1.4, 5.0, -2.4, 3.4, 10.2, 2.4, "plate")
    a.arm(-1.5, 5.0, -2.5, 3.5, 5.7, 2.5, "gold")
    b = "legs_b"
    a.box(b, -4.6, 9.4, -2.7, 4.6, 11.0, 2.7, "leather")
    a.box(b, -1.0, 9.3, -2.95, 1.0, 11.1, -2.7, "gold")
    a.mirror(b, 0.3, 10.8, -3.0, 4.4, 14.5, -2.6, "plate")
    a.mirror(b, 0.3, 14.0, -3.05, 4.4, 14.6, -2.95, "gold")
    a.leg("legs", -2.4, -0.1, -2.4, 2.4, 6.8, 2.4, "plate")
    a.leg("legs", -1.7, 5.0, -3.0, 1.7, 7.3, -2.4, "gold")
    plate_boots(a, "gold")


def swordsman(a):
    h = "helmet"
    a.ring(h, 4.65, -7.0, -5.8, "cloth")
    a.box(h, -2.6, -7.3, -4.95, 2.6, -5.5, -4.6, "silver")
    for i, x in enumerate((-0.8, 0.8)):
        t = a.deco(f"tail{i}", h, (x, -6.4, 4.6), (35, 0, (10 if x > 0 else -10)))
        a.box(t, -0.5, 0, -0.15, 0.5, 6.5 - i, 0.15, "cloth")
    c = "chest"
    a.box(c, -4.5, -0.4, -2.7, 4.5, 11.0, 2.7, "cloth")
    a.box(c, -4.4, 10.5, 2.4, 4.4, 20.0, 2.9, "cloth")
    a.mirror(c, 1.2, 10.5, -2.9, 4.4, 19.0, -2.4, "cloth")
    a.mirror(c, 4.2, 10.5, -2.4, 4.6, 19.5, 2.4, "cloth")
    a.mirror(c, 3.2, -2.2, -2.6, 3.8, 0.0, 2.4, "cloth")
    a.box(c, -3.8, -2.2, 2.4, 3.8, 0.0, 2.9, "cloth")
    a.mirror(c, 1.0, -0.4, -2.95, 1.3, 19.0, -2.7, "silver")
    a.box(c, -3.0, 1.0, -2.95, 3.0, 6.0, -2.75, "plate")
    s = a.deco("strap", c, (0, 0, -2.95), (0, 0, -35))
    a.box(s, -0.5, -1, -0.3, 0.5, 13, 0, "leather")
    a.box(s, -0.8, 5, -0.45, 0.8, 6.2, -0.2, "silver")
    a.arm(-1.25, -2.2, -2.25, 3.25, 8.0, 2.25, "cloth")
    a.box("chest_l", -1.8, -3.0, -2.8, 3.8, 0.8, 2.8, "plate")
    a.box("chest_l", -1.9, 0.4, -2.9, 3.9, 1.0, 2.9, "silver")
    a.arm(-1.35, 6.0, -2.35, 3.35, 10.2, 2.35, "plate")
    b = "legs_b"
    a.box(b, -4.55, 9.6, -2.65, 4.55, 11.0, 2.65, "leather")
    a.box(b, -0.8, 9.5, -2.85, 0.8, 11.1, -2.6, "silver")
    a.leg("legs", -2.25, -0.05, -2.25, 2.25, 8.0, 2.25, "cloth2")
    a.leg("legs", -2.3, 4.5, -2.35, 2.3, 5.2, 2.35, "leather")
    a.leg("boots", -2.4, 6.5, -2.5, 2.4, 12.3, 2.5, "leather")
    a.leg("boots", -2.45, 8.0, -2.55, 2.45, 8.6, 2.55, "silver")
    a.leg("boots", -2.45, 10.0, -2.55, 2.45, 10.6, 2.55, "silver")
    a.leg("boots", -2.2, 11.0, -3.0, 2.2, 12.3, -2.4, "leather")


def paladin(a):
    h = "helmet"
    a.box(h, -4.6, -8.7, -4.6, 4.6, -5.4, 4.6, "plate")
    a.mirror(h, 3.6, -5.4, -4.6, 4.6, -1.5, 2.0, "plate")
    a.box(h, -4.6, -5.4, 2.0, 4.6, -0.5, 4.6, "plate")
    a.ring(h, 4.75, -5.9, -5.2, "gold")
    a.box(h, -0.5, -10.0, -3.5, 0.5, -8.7, 3.5, "gold")
    list(a.sides("wing", h, (4.6, -7.0, 0.0), (0, -30, -20), wing(1.25)))
    c = "chest"
    a.box(c, -4.6, -0.4, -2.8, 4.6, 9.5, 2.8, "plate")
    a.box(c, -1.5, 2.0, -3.0, 1.5, 5.0, -2.8, "gold")
    a.box(c, -0.3, 0.8, -3.05, 0.3, 6.2, -2.95, "gold")
    a.box(c, -2.6, 3.2, -3.05, 2.6, 3.8, -2.95, "gold")
    a.box(c, -3.4, 8.0, -2.95, 3.4, 12.0, -2.7, "cloth")
    a.arm(-2.0, -3.4, -3.0, 4.0, 1.4, 3.0, "plate")
    a.arm(-1.8, 1.2, -2.8, 3.8, 2.4, 2.8, "plate")
    a.arm(-2.1, -0.2, -3.1, 4.1, 0.4, 3.1, "gold")
    a.arm(-1.4, 5.0, -2.4, 3.4, 10.3, 2.4, "plate")
    b = "legs_b"
    a.box(b, -4.7, 9.4, -2.8, 4.7, 11.0, 2.8, "gold")
    a.box(b, -3.4, 11.0, -3.0, 3.4, 17.0, -2.7, "cloth")
    a.leg("legs", -2.4, -0.2, -2.4, 2.4, 7.0, 2.4, "plate")
    a.leg("legs", -1.7, 5.0, -3.0, 1.7, 7.3, -2.4, "gold")
    plate_boots(a, "gold")


def spellblade(a):
    h = "helmet"
    a.ring(h, 4.65, -7.2, -6.4, "silver")
    a.box(h, -0.7, -8.2, -5.0, 0.7, -6.4, -4.6, "glow")
    list(a.sides("fin", h, (4.65, -6.8, -1.0), (0, -20, -30), [(0, -2.4, 0, 0.4, 0, 3.4, "silver"), (0, -3.4, 2.2, 0.4, -2.4, 3.4, "silver")]))
    c = "chest"
    a.box(c, -4.5, -0.4, -2.7, 4.5, 11.0, 2.7, "cloth")
    a.mirror(c, 3.4, -3.2, -2.6, 4.2, 0.0, 2.6, "cloth")
    a.box(c, -4.2, -3.2, 2.5, 4.2, 0.0, 3.1, "cloth")
    a.mirror(c, 3.45, -3.25, -2.65, 4.25, -2.8, 2.65, "glow")
    a.box(c, -0.4, 0.0, -2.95, 0.4, 11.0, -2.75, "glow")
    a.box(c, -3.4, 0.5, -2.95, 3.4, 5.5, -2.75, "plate")
    a.arm(-1.6, -2.8, -2.7, 3.6, 0.4, 2.7, "plate")
    a.arm(-1.4, 0.4, -2.5, 3.4, 1.6, 2.5, "plate")
    a.arm(-1.7, -0.2, -2.8, 3.7, 0.3, 2.8, "glow")
    a.arm(-1.25, 1.6, -2.25, 3.25, 8.0, 2.25, "cloth")
    a.arm(-1.4, 6.0, -2.4, 3.4, 10.2, 2.4, "plate")
    a.arm(-1.45, 7.8, -2.45, 3.45, 8.3, 2.45, "glow")
    b = "legs_b"
    a.box(b, -4.6, 9.4, -2.7, 4.6, 11.0, 2.7, "leather")
    a.box(b, -0.7, 9.6, -2.95, 0.7, 10.8, -2.7, "glow")
    a.box(b, -3.0, 10.5, -2.95, 3.0, 19.0, -2.6, "cloth2")
    a.box(b, -4.3, 10.5, 2.6, 4.3, 19.5, 2.95, "cloth2")
    a.box(b, -3.0, 18.4, -3.0, 3.0, 19.0, -2.9, "glow")
    a.leg("legs", -2.35, -0.1, -2.35, 2.35, 7.0, 2.35, "plate")
    a.leg("boots", -2.45, 7.0, -2.55, 2.45, 12.35, 2.55, "plate")
    a.leg("boots", -1.6, 11.2, -4.0, 1.6, 12.35, -2.55, "plate")
    a.leg("boots", -2.5, 7.0, -2.6, 2.5, 7.6, 2.6, "glow")


def dragonknight(a):
    h = "helmet"
    a.box(h, -4.6, -8.6, -4.6, 4.6, -5.6, 4.6, "scale")
    snout = a.deco("snout", h, (0, -7.2, -4.6), (12, 0, 0))
    a.box(snout, -1.8, -1.0, -3.0, 1.8, 1.0, 0, "scale")
    a.box(snout, -1.4, 1.0, -2.8, 1.4, 1.6, -0.4, "horn")
    a.mirror(snout, 0.5, -1.4, -2.6, 1.1, -1.0, -1.8, "gem")
    for i in range(3):
        a.box(h, -0.4, -10.2 + i * 0.4, -2.5 + i * 2.2, 0.4, -8.6, -1.5 + i * 2.2, "horn")
    for part in a.sides("horn", h, (3.4, -8.0, -1.0), (22, 12, 0), [(-0.8, -0.8, 0, 0.8, 0.8, 4.5, "horn")]):
        tip = a.deco(part + "_tip", part, (0, 0, 4.5), (25, 0, 0))
        a.box(tip, -0.5, -0.5, 0, 0.5, 0.5, 3.5, "horn")
    c = "chest"
    a.box(c, -4.6, -0.4, -2.8, 4.6, 10.0, 2.8, "scale")
    a.box(c, -1.0, 2.5, -3.0, 1.0, 4.0, -2.8, "gem")
    a.arm(-1.8, -3.0, -2.8, 3.8, 0.8, 2.8, "scale")
    a.deco_arm("spike", (2.6, -3.0, -1.4), (0, 0, -30), [(-0.4, -5.0, -0.4, 0.4, 0, 0.4, "horn")])
    a.deco_arm("spike2", (2.6, -3.0, 1.4), (0, 0, -45), [(-0.35, -4.0, -0.35, 0.35, 0, 0.35, "horn")])
    a.arm(-1.4, 5.0, -2.4, 3.4, 10.3, 2.4, "scale")
    b = "legs_b"
    a.box(b, -4.6, 9.4, -2.7, 4.6, 11.0, 2.7, "dark")
    a.mirror(b, 0.3, 10.5, -3.0, 4.4, 15.0, -2.6, "scale")
    a.leg("legs", -2.4, -0.2, -2.4, 2.4, 7.0, 2.4, "scale")
    a.leg("legs", -0.5, 5.0, -3.6, 0.5, 6.6, -2.4, "horn")
    a.leg("boots", -2.5, 7.0, -2.6, 2.5, 12.4, 2.6, "scale")
    for i in range(3):
        a.leg("boots", -2.0 + i * 1.6, 11.4, -4.4, -1.3 + i * 1.6, 12.4, -2.6, "horn")


def demonlord(a):
    h = "helmet"
    a.ring(h, 4.7, -8.2, -7.0, "gold")
    for x in (-2.4, 0, 2.4):
        a.box(h, x - 0.4, -9.6 - (0.8 if x == 0 else 0), -4.9, x + 0.4, -8.2, -4.5, "gold")
    a.box(h, -0.6, -8.0, -5.0, 0.6, -7.2, -4.6, "gem")
    for part in a.sides("horn", h, (4.2, -7.6, 0.0), (0, 0, 60), [(-1.2, -4.0, -1.2, 1.2, 0, 1.2, "horn")]):
        p2 = a.deco(part + "_b", part, (0, -4.0, 0), (0, 0, -35 if part.endswith("l") else 35))
        a.box(p2, -1.0, -3.6, -1.0, 1.0, 0, 1.0, "horn")
        p3 = a.deco(part + "_c", p2, (0, -3.6, 0), (0, 0, -35 if part.endswith("l") else 35))
        a.box(p3, -0.7, -3.0, -0.7, 0.7, 0, 0.7, "horn")
        a.box(p3, -0.4, -4.2, -0.4, 0.4, -3.0, 0.4, "gem")
    c = "chest"
    a.box(c, -4.5, -0.4, -2.7, 4.5, 10.5, 2.7, "plate")
    a.box(c, -5.3, -1.6, -3.2, 5.3, 1.6, 3.2, "fur")
    a.box(c, -0.9, 3.0, -3.0, 0.9, 4.8, -2.8, "gem")
    a.mirror(c, 0.9, 1.6, -2.95, 1.3, 10.5, -2.75, "gold")
    a.arm(-1.9, -3.2, -2.9, 3.9, 1.0, 2.9, "plate")
    a.arm(-2.0, 0.6, -3.0, 4.0, 1.2, 3.0, "gold")
    a.deco_arm("spike", (1.6, -3.2, -1.2), (0, 0, -20), [(-0.45, -3.5, -0.45, 0.45, 0, 0.45, "horn")])
    a.deco_arm("spikeb", (1.6, -3.2, 1.2), (0, 0, -20), [(-0.45, -3.5, -0.45, 0.45, 0, 0.45, "horn")])
    a.deco_arm("spikec", (3.2, -2.6, 0.0), (0, 0, -55), [(-0.4, -3.0, -0.4, 0.4, 0, 0.4, "horn")])
    a.arm(-1.4, 5.0, -2.4, 3.4, 10.3, 2.4, "plate")
    b = "legs_b"
    a.box(b, -4.7, 9.4, -2.8, 4.7, 11.0, 2.8, "gold")
    a.mirror(b, 0.5, 10.5, -3.0, 4.6, 18.0, -2.6, "cloth2")
    a.mirror(b, 4.3, 10.5, -2.6, 4.7, 18.0, 2.6, "cloth2")
    a.leg("legs", -2.4, -0.2, -2.4, 2.4, 7.0, 2.4, "plate")
    a.leg("boots", -2.5, 7.0, -2.6, 2.5, 12.4, 2.6, "plate")
    a.leg("boots", -1.8, 10.8, -4.2, 1.8, 12.4, -2.6, "plate")
    a.leg("boots", -0.4, 7.4, -3.8, 0.4, 9.2, -2.6, "horn")


def ranger(a):
    h = "helmet"
    a.box(h, -4.8, -9.0, -4.8, 4.8, -7.8, 4.8, "cloth")
    a.box(h, -4.8, -9.0, 3.6, 4.8, 0.5, 4.8, "cloth")
    a.mirror(h, 3.6, -9.0, -4.8, 4.8, 0.5, 3.6, "cloth")
    a.box(h, -4.8, -9.0, -5.2, 4.8, -7.4, -4.6, "cloth")
    a.ring(h, 4.9, -1.6, 0.5, "cloth2", 0.8)
    tip = a.deco("hood_tip", h, (0, -8.2, 4.6), (-35, 0, 0))
    a.box(tip, -2.0, -1.0, 0, 2.0, 1.0, 3.0, "cloth")
    c = "chest"
    a.box(c, -4.5, -0.3, -2.6, 4.5, 10.8, 2.6, "leather")
    a.box(c, -3.4, 0.6, -2.9, 3.4, 5.6, -2.6, "plate")
    a.box(c, -3.0, -1.2, 2.6, 3.0, 0.5, 3.8, "cloth")
    quiver = a.deco("quiver", c, (0, 1.0, 3.1), (0, 0, -25))
    a.box(quiver, -1.3, -1.0, 0, 1.3, 9.0, 2.2, "leather")
    a.box(quiver, -1.0, -2.8, 0.4, 1.0, -1.0, 1.8, "cloth2")
    s = a.deco("strap", c, (0, 0, -2.85), (0, 0, -35))
    a.box(s, -0.5, -1, -0.3, 0.5, 13, 0, "leather")
    a.box("chest_l", -1.5, -2.6, -2.6, 3.5, 0.6, 2.6, "plate")
    a.arm(-1.3, 5.0, -2.3, 3.3, 10.2, 2.3, "leather")
    a.arm(-1.4, 6.5, -2.4, 3.4, 7.2, 2.4, "plate")
    b = "legs_b"
    a.box(b, -4.6, 9.4, -2.6, 4.6, 11.2, 2.6, "leather")
    a.mirror(b, 4.3, 10.0, -1.2, 4.9, 12.0, 1.2, "leather")
    a.box(b, -1.5, 10.0, -2.95, -0.1, 11.8, -2.6, "leather")
    a.leg("legs", -2.3, -0.1, -2.3, 2.3, 8.0, 2.3, "cloth2")
    a.leg("legs", -1.7, 4.4, -2.7, 1.7, 6.4, -2.3, "plate")
    a.leg("boots", -2.4, 7.0, -2.5, 2.4, 12.3, 2.5, "leather")
    a.leg("boots", -2.7, 6.6, -2.8, 2.7, 7.8, 2.8, "fur")
    a.leg("boots", -2.45, 9.5, -2.55, 2.45, 10.1, 2.55, "dark")


BUILD = {"vanguard": vanguard, "swordsman": swordsman, "paladin": paladin, "spellblade": spellblade,
         "dragonknight": dragonknight, "demonlord": demonlord, "ranger": ranger}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- painting, in the Minecraft manner
# every material is a ramp of shades, dark to light; a pixel's shade comes from where it is on its face (lit from
# the top-left: light top and left edges, a shadow along the bottom and right, lighter towards the top), from the
# face (tops lighter, bottoms darker), a little dither and noise - and from the material's own marks
RAMPS = {
    "plate": [(8, 8, 11), (16, 16, 20), (26, 26, 32), (38, 38, 46), (52, 53, 63), (72, 74, 86), (104, 108, 122), (150, 154, 168)],
    "gold": [(60, 36, 8), (100, 62, 16), (142, 96, 26), (186, 136, 40), (218, 172, 62), (240, 206, 108), (252, 232, 160), (255, 248, 214)],
    "silver": [(50, 54, 64), (82, 88, 100), (118, 124, 138), (154, 160, 174), (186, 192, 206), (212, 218, 230), (236, 240, 248), (255, 255, 255)],
    "leather": [(24, 14, 8), (38, 24, 14), (54, 36, 22), (72, 50, 30), (92, 64, 40), (114, 82, 52), (138, 102, 68), (164, 126, 88)],
    "fur": [(22, 19, 17), (34, 29, 26), (48, 42, 37), (62, 55, 49), (80, 72, 64), (102, 92, 82), (128, 118, 106), (160, 150, 136)],
    "horn": [(6, 5, 6), (12, 10, 12), (20, 17, 19), (30, 26, 29), (42, 37, 41), (58, 52, 57), (80, 74, 79), (108, 102, 108)],
    "dark": [(4, 4, 5), (8, 8, 10), (12, 12, 15), (16, 16, 20), (22, 22, 27), (30, 30, 36), (40, 40, 48), (54, 54, 64)],
}


# the metals (as build_ingots.py): deep, base, light, shine - the plate ramp of each is made from these
METALS = {
    "blacksteel": ((16, 16, 20), (38, 38, 46), (76, 78, 88), (128, 132, 144)),
    "moonsilver": ((70, 82, 108), (138, 154, 186), (196, 210, 234), (244, 248, 255)),
    "crimsonite": ((44, 6, 10), (104, 18, 24), (156, 38, 44), (226, 108, 108)),
    "mithril": ((84, 102, 124), (162, 182, 202), (214, 228, 242), (255, 255, 255)),
    "etherite": ((36, 110, 128), (96, 196, 210), (160, 234, 240), (228, 255, 255)),
    "adamantite": ((10, 28, 20), (28, 66, 48), (54, 108, 80), (118, 186, 146)),
    "dragonsteel": ((26, 6, 6), (68, 16, 14), (114, 32, 24), (186, 80, 50)),
    "divinite": ((150, 112, 48), (222, 190, 110), (248, 230, 168), (255, 254, 236)),
    "valorium": ((14, 26, 80), (32, 58, 148), (66, 100, 202), (146, 178, 255)),
    "voidsteel": ((8, 3, 14), (26, 11, 44), (50, 26, 80), (120, 82, 180)),
}


def metal_ramp(metal):
    deep, base, light, shine = METALS[metal]

    def mix(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return [tuple(int(c * 0.5) for c in deep), deep, mix(deep, base, 0.5), base, mix(base, light, 0.5), light, mix(light, shine, 0.5), shine]


def ramp_of(base):
    out = []
    for k in (0.3, 0.45, 0.6, 0.78, 1.0, 1.2, 1.42, 1.7):
        out.append(tuple(max(0, min(255, int(c * k + (20 if k > 1.3 else 0)))) for c in base))
    return out


def shade(ramp, level):
    return ramp[max(0, min(len(ramp) - 1, int(round(level))))]


def paint(armor, name, tex, metal="blacksteel"):
    cloth, cloth2, gem = SETS[name]
    ramps = dict(RAMPS)
    ramps["plate"] = metal_ramp(metal)
    ramps["cloth"], ramps["cloth2"] = ramp_of(cloth), ramp_of(cloth2)
    ramps["gem"] = ramp_of(gem)
    ramps["glow"] = [lerp(gem, (255, 255, 255), t) for t in (0.0, 0.0, 0.05, 0.12, 0.22, 0.35, 0.55, 0.8)]
    scale_tint = ramp_of(cloth)
    rnd = random.Random(hash(name) & 0xFFFF)
    for cname, _, _, (w, h, d), (u, v) in armor.cubes:
        kind = armor.kind[cname]
        ramp = ramps["plate" if kind == "scale" else kind]
        fur_cols = [rnd.uniform(-1.2, 1.2) for _ in range(64)]
        for side, r in faces(u, v, w, h, d).items():
            x0, y0, fw, fh = (int(round(n)) for n in r)
            fw, fh = max(1, fw), max(1, fh)
            face = 1.3 if side == "top" else -1.6 if side == "bottom" else 0.3 if side in ("front", "left") else -0.3
            for yy in range(fh):
                for xx in range(fw):
                    n = rnd.random()
                    level = 3.6 + face
                    if fh > 1 and side not in ("top", "bottom"):
                        level += 0.9 - 1.8 * yy / (fh - 1)
                    if yy == 0:
                        level += 1.4
                    elif yy == fh - 1:
                        level -= 1.4
                    if xx == 0:
                        level += 0.7
                    elif xx == fw - 1:
                        level -= 0.7
                    level += (0.35 if (xx + yy) % 2 == 0 else -0.35) if n < 0.5 else 0
                    level += rnd.uniform(-0.45, 0.45)
                    inner = 0 < xx < fw - 1 and 0 < yy < fh - 1
                    c = None
                    if kind == "plate":
                        if fw >= 5 and fh >= 5 and (xx, yy) in ((1, 1), (fw - 2, 1), (1, fh - 2), (fw - 2, fh - 2)):
                            level = 6.5  # a rivet
                        elif fw >= 5 and fh >= 5 and (xx, yy) in ((2, 2), (fw - 1, 2), (2, fh - 1), (fw - 1, fh - 1)):
                            level -= 1.5  # its shadow
                        elif inner and n < 0.025:
                            level += 2.2  # a scratch
                    elif kind == "scale":
                        band = yy // 2
                        cell = (xx + (band % 2) * 2) % 4
                        if yy % 2 == 0:
                            level += 1.0 if cell in (1, 2) else -0.2
                        else:
                            level -= 0.8 if cell in (0, 3) else 0.0
                        if cell == 0:
                            level -= 1.0
                        c = lerp(shade(ramp, level), shade(scale_tint, level - 0.5), 0.35)
                    elif kind in ("cloth", "cloth2"):
                        if xx % 4 == 0:
                            level -= 1.0  # folds
                        elif xx % 4 == 2:
                            level += 0.5
                        if yy == fh - 1 and side not in ("top", "bottom"):
                            level -= 0.8  # the hem
                    elif kind == "leather":
                        if inner and (xx == 1 or yy == 1 or xx == fw - 2 or yy == fh - 2) and (xx + yy) % 2 == 0 and fw > 3 and fh > 3:
                            level = 7  # stitching
                        elif inner and n < 0.07:
                            level -= 1.6  # creases
                    elif kind in ("gold", "silver"):
                        if inner and (xx - yy) % 4 == 0:
                            level -= 1.2  # engraving
                        if yy == 0:
                            level += 1.0
                    elif kind == "fur":
                        level = 3.4 + face + fur_cols[xx % 64] + (0.8 if yy % 3 == 0 else -0.2) + rnd.uniform(-0.6, 0.6)
                    elif kind == "horn":
                        if yy % 3 == 0:
                            level -= 1.1  # ridges
                        level += (fh - 1 - yy) / max(1, fh - 1) * 0.0
                    elif kind in ("gem", "glow"):
                        cx, cy = (fw - 1) / 2, (fh - 1) / 2
                        dist = max(abs(xx - cx) / max(1, cx + 0.5), abs(yy - cy) / max(1, cy + 0.5))
                        level = 6.2 - dist * 3.5 + (0.8 if xx < cx and yy < cy else 0)
                    if c is None:
                        c = shade(ramp, level)
                    tex.set(x0 + xx, y0 + yy, c, 0.0)


# set -> cape length (pixels), edging material, emblem
CAPES = {"vanguard": (20, "gold", "crest"), "paladin": (20, "gold", "sun"), "dragonknight": (18, "dark", "dragon"),
         "demonlord": (21, "gold", "sigil"), "ranger": (11, "leather", "tatter")}


def cape(name, tex):
    length, edge, emblem = CAPES[name]
    cloth, cloth2, gem = SETS[name]
    ramp, lining = ramp_of(cloth), ramp_of(lerp(cloth, (0, 0, 0), 0.35))
    eramp = RAMPS[edge]
    rnd = random.Random(hash(name) & 0xFFF)
    w, d = 10, 1
    # faces of a 10 x length x 1 box at (0, 0): top (1,0), bottom (11,0), sides (0,1) and (11,1), outside (1,1), inside (12,1)
    for x in range(w):
        tex.set(1 + x, 0, shade(ramp, 5), 0.0)
        tex.set(11 + x, 0, shade(ramp, 2), 0.0)
    for y in range(length):
        tex.set(0, 1 + y, shade(ramp, 3 - y * 0.05), 0.0)
        tex.set(11, 1 + y, shade(ramp, 2.5 - y * 0.05), 0.0)
    for y in range(length):
        for x in range(w):
            level = 4.4 - y * 0.09 + (0.3 if (x + y) % 2 == 0 else -0.3) + rnd.uniform(-0.35, 0.35)
            if x % 3 == 0:
                level -= 0.9  # folds
            elif x % 3 == 1:
                level += 0.4
            c = shade(ramp, level)
            if y == 0 or x == 0 or x == w - 1:
                c = shade(eramp, 4.5 - (y == 0) * 0.0 + rnd.uniform(-0.4, 0.4) + (1 if y == 0 else 0))
            if y == length - 1:
                c = shade(eramp, 3 + rnd.uniform(-0.4, 0.4))
            if emblem == "tatter" and y >= length - 3 and (x * 7 + y) % 3 == 0:
                c = None  # a ragged hem
            if c is not None:
                tex.set(1 + x, 1 + y, c, 0.0)
            # the inside: a darker lining
            tex.set(12 + x, 1 + y, shade(lining, 3.5 - y * 0.05 + rnd.uniform(-0.3, 0.3) - (0.8 if x % 3 == 0 else 0)), 0.0)
    gold = RAMPS["gold"]
    cx, cy = 1 + 4.5, 1 + 6.5
    if emblem == "crest":  # a shield with a blue gem
        for y in range(4, 11):
            half = 3 if y < 8 else 3 - (y - 7)
            for x in range(5 - half, 5 + half):
                tex.set(1 + x, 1 + y, shade(gold, 5 if x < 5 else 3.5), 0.0)
        tex.set(1 + 4, 1 + 6, gem, 0.0)
        tex.set(1 + 5, 1 + 6, lerp(gem, (255, 255, 255), 0.4), 0.0)
    elif emblem == "sun":  # a golden sun
        for y in range(3, 11):
            for x in range(1, 9):
                dd = ((x + 0.5 - 5) ** 2 + (y + 0.5 - 7) ** 2) ** 0.5
                ray = (x == 4 or x == 5 or y == 6 or y == 7) and dd < 4.2
                if dd < 2.0 or ray:
                    tex.set(1 + x, 1 + y, shade(gold, 6 - dd), 0.0)
    elif emblem == "dragon":  # a black dragon rising
        for x, y in ((4, 3), (5, 3), (3, 4), (4, 4), (5, 4), (6, 4), (4, 5), (5, 5), (2, 6), (3, 6), (4, 6), (5, 6), (6, 6), (7, 6),
                     (1, 7), (4, 7), (5, 7), (8, 7), (4, 8), (5, 8), (3, 9), (6, 9), (3, 10), (6, 10)):
            tex.set(1 + x, 1 + y, (14, 10, 12), 0.0)
        tex.set(1 + 5, 1 + 4, gem, 0.0)
    elif emblem == "sigil":  # a demon's eye in a ring
        for y in range(3, 12):
            for x in range(1, 9):
                dd = ((x + 0.5 - 5) ** 2 + (y + 0.5 - 7.5) ** 2) ** 0.5
                if 3.0 < dd < 4.0:
                    tex.set(1 + x, 1 + y, shade(gold, 4.5), 0.0)
        for x, y in ((3, 7), (4, 7), (5, 7), (6, 7), (4, 6), (5, 6), (4, 8), (5, 8)):
            tex.set(1 + x, 1 + y, gem if (x, y) in ((4, 7), (5, 7)) else (10, 4, 6), 0.0)
    _ = cx, cy, cloth2


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    blends, textures = argv[0], argv[1]
    os.makedirs(textures, exist_ok=True)
    for name in CAPES:
        t = Tex(32, 32, seed=5)
        cape(name, t)
        t.save(os.path.join(textures, f"blacksteel_{name}_cape.png"))
    for name, build in BUILD.items():
        armor = Armor()
        build(armor)
        used = pack(armor.cubes, TEX)
        assert used <= TEX, f"{name}: texture too small: {used}"
        for metal in METALS:
            if metal == "blacksteel":
                continue
            t = Tex(TEX, TEX, seed=11)
            paint(armor, name, t, metal)
            t.save(os.path.join(textures, f"{metal}_{name}.png"))
        tex = Tex(TEX, TEX, seed=11)
        paint(armor, name, tex)
        tex_path = os.path.join(textures, f"blacksteel_{name}.png")
        tex.save(tex_path)
        scene, objs = build_scene(armor.parts, armor.cubes, tex_path, TEX, TEX, f"blacksteel_{name}.blend")
        scene["mc_anims"] = []
        scene["mc_preview_z"] = 16.0
        save(os.path.join(blends, f"blacksteel_{name}.blend"))
        print("saved", name, "rows", used, "parts", len(armor.parts))


main()
