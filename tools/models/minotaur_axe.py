"""
The Minotaur's great axe, shared by the boss model (build_minotaur.py, in his right fist) and the item
(build_minotaur_axe.py), so both look the same.

Axe space: model pixels around the fist that holds it; x = thickness, y = the way the blades point, z = along the
haft (the head is at -z).
"""

# name, origin, size, material
AXE = [
    ("haft", (-1, -1, -14), (2, 2, 18), "haft"),
    ("pommel", (-1.5, -1.5, 4), (3, 3, 2), "gold"),
    ("socket", (-1.5, -2, -18), (3, 4, 4), "socket"),
    ("spike", (-0.5, -0.5, -21), (1, 1, 3), "steel"),
    ("blade_up_neck", (-0.75, -4, -17.5), (1.5, 2, 3), "steel"),
    ("blade_up", (-0.75, -7, -19), (1.5, 3, 6), "steel"),
    ("blade_up_edge", (-0.5, -10, -20), (1, 3, 8), "edge"),
    ("blade_down_neck", (-0.75, 2, -17.5), (1.5, 2, 3), "steel"),
    ("blade_down", (-0.75, 4, -19), (1.5, 3, 6), "steel"),
    ("blade_down_edge", (-0.5, 7, -20), (1, 3, 8), "edge"),
]

WOOD = (106, 70, 40)
WOOD_D = (74, 48, 28)
LEATHER = (76, 30, 22)
GOLD = (228, 182, 62)
GOLD_D = (170, 126, 36)
METAL_D = (78, 80, 88)
STEEL = (170, 174, 182)
STEEL_D = (118, 122, 130)
STEEL_L = (226, 230, 236)


def color(material, along, across, length):
    """The colour of one texel: along = position along the part's long side (0..length-1), across = across it."""
    if material == "haft":
        if along % 4 == 0 or along >= length - 4:  # leather wraps, and the grip near the pommel
            return LEATHER
        return WOOD_D if across == 0 else WOOD
    if material == "gold":
        return GOLD_D if across == 0 else GOLD
    if material == "socket":
        return GOLD if along == length // 2 else METAL_D
    if material == "edge":
        return STEEL_L if across == 0 else STEEL
    # steel: darker rim, one gold rune in the middle
    if across == 0:
        return STEEL_D
    if along == length // 2 and across == 1:
        return GOLD
    return STEEL
