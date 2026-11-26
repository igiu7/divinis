"""
Painting the Nameless Creator's first two phases (the rig of creator_common.majesty): one painter for every kind of
cube, coloured by a style per side - phase 1 in every colour of the world, phase 2 gold and white on the left (the
side that became Aurelion) and black and violet on the right (Nocthariel's).
"""
import math

from creator_common import HALO_R, border, dims, face_rects, inner, lerp, paint_face, shade

INK = (36, 26, 30)
MISSING = (248, 0, 248)
GOLD5 = [(92, 58, 14), (142, 96, 22), (206, 152, 44), (246, 206, 106), (255, 244, 200)]
GREEN = (70, 128, 44)
# kinds cut flat out of paper: their thin sides are the paper's edge
FLAT = {"banner", "plate", "hand", "map", "flower", "hub", "clock", "compass", "lightfeather", "smoke"}


def metal(pal, xx, yy, w, h, rnd):
    """Polished metal of a 5-step palette (dark .. glint): lit from above, darker edges, a glint now and then."""
    t = yy / max(1, h - 1)
    c = lerp(pal[3], pal[2], t * 1.6) if t < 0.6 else lerp(pal[2], pal[1], (t - 0.6) / 0.4)
    if xx in (0, w - 1) and w > 2:
        c = lerp(c, pal[0], 0.35)
    if rnd.random() < 0.02:
        c = pal[4]
    return c


def banner_px(st, idx, xx, yy, w, h, rnd, front):
    base = st["banner"][idx % len(st["banner"])]
    fg = st["banner_fg"][idx % len(st["banner_fg"])]
    if yy == h - 1 and 1 <= xx <= w - 2 or yy == h - 2 and xx == w // 2:
        return None  # a swallowtail
    if st.get("ragged") and yy > h - 7 and rnd.random() < (yy - (h - 7)) * 0.13:
        return None
    if yy == 0:
        return shade(base, 0.7)
    c = shade(base, 1.06 if yy % 2 else 0.97)
    if front:
        p = idx % 6
        mid = w // 2
        on = (p == 0 and (xx in (0, w - 1) or yy in (1, h - 3))
              or p == 1 and (yy - abs(xx - mid)) % 5 == 0
              or p == 2 and yy % 4 == 1
              or p == 3 and (xx == mid or yy == 6)
              or p == 5 and abs(xx - mid) + abs(yy % 6 - 3) == 2)
        if p == 4:
            c = lerp(base, fg, yy / h)
        elif on:
            c = fg
        if p in (0, 2, 4) and 3 <= yy <= 5 and abs(xx - mid) <= 1 and (yy == 4 or xx == mid):
            c = st["banner_sun"]  # a little sun in the cloth
    return c


def flower_px(idx, xx, yy):
    petals = [((200, 30, 30), (40, 20, 10)), ((250, 214, 40), (220, 160, 20)), ((70, 100, 220), (40, 60, 160)),
              ((180, 90, 220), (140, 60, 180)), ((240, 240, 236), (240, 200, 40)), ((240, 120, 30), (200, 80, 20)),
              ((250, 250, 244), (210, 220, 200)), ((40, 170, 220), (20, 110, 170))][idx % 8]
    if yy == 0:
        return petals[0] if xx != 1 or idx % 2 else petals[1]
    if yy == 1:
        return petals[1] if xx == 1 and idx in (0, 4) else petals[0]
    if xx == 1:
        return GREEN
    if yy == 2 and xx == (0 if idx % 2 else 2):
        return shade(GREEN, 1.2)
    return None


def map_px(i, yy, w, h, rnd):
    """The face: a map of the arena seen from above, as a map item - its frame, grass round it, the stone floor."""
    if i == w - 1 or yy in (0, h - 1):
        return (150, 118, 70)
    if i == w - 2 or yy in (1, h - 2):
        return (222, 206, 160)
    d = math.hypot(i + 0.5, yy - 4)
    if d < 1.0:
        return (250, 250, 250)
    if d < 2.6:
        return (140, 140, 140) if (i + yy) % 2 else (120, 120, 124)
    if d < 3.2:
        return (78, 74, 72)
    if i >= 2 and yy >= 5:
        return (64, 96, 200)
    return (56, 98, 40) if rnd.random() < 0.18 else (100, 150, 60)


def painter(kind, face, s, st, meta, rnd):
    """The pixel painter of one face of one cube (st: the side's style): fn(x, y, w, h) -> (colour, glow)."""
    idx = meta.get("idx", 0)
    front = face in ("front", "back")

    def f(xx, yy, w, h):
        i = inner(face, xx, w, s)
        if kind == "robe":
            on = (i + yy) % 4 == 0 or (i - yy) % 4 == 0
            return (shade(st["lattice"], 0.9 + 0.2 * rnd.random()) if on else shade(st["robe"], 0.92 + 0.12 * rnd.random())), None
        if kind == "plate":
            d = math.hypot(i + 0.5, yy - 4)
            if d < 1.6:
                return st["sun"], st.get("sun_glow")
            ang = math.atan2(yy - 4, i + 0.5)
            if d < 4.2 and int((ang + 4) * 8 / math.pi) % 2 == 0:
                return st["plate"][3], None
            if i == w - 1 or yy in (0, h - 1):
                return INK, None
            return metal(st["plate"], xx, yy, w, h, rnd), None
        if kind == "belt":
            if face == "front" and i <= 1:
                return metal(st["frame"], xx, yy, w, h, rnd), None
            return shade(st["belt"], 1.1 if yy == 0 else 0.9), None
        if kind in ("trim", "frame", "brad", "lightbone"):
            return metal(st["frame"], xx, yy, w, h, rnd), None
        if kind == "pauldron":
            if border(xx, yy, w, h):
                return metal(st["frame"], xx, yy, w, h, rnd), None
            return shade(st["pauldron"], 0.92 + 0.1 * ((xx + yy) % 2)), None
        if kind == "sleeve":
            if yy == h - 2:
                return metal(st["frame"], xx, yy, w, h, rnd), None
            return (shade(st["sleeve"], 1.25) if (xx + yy) % 5 == 0 else st["sleeve"]), None
        if kind == "cuff":
            return (None if (xx * 3 + yy) % 4 == 0 else st["cuff"]), None
        if kind == "chain":
            c = st["chain"][(idx + yy // 2) % len(st["chain"])]
            return (shade(c, 1.3) if yy % 2 == 0 and xx == 0 else c), None
        if kind == "hand":
            if yy >= 4 and xx % 2 == 1:
                return None, None  # between the fingers
            if border(xx, yy, w, h) or yy == 3 and xx % 2 == 1:
                return st["skin_d"], None
            return st["skin"], None
        if kind == "string":
            return (240, 236, 224), None
        if kind == "skin":
            return st["skin"], None
        if kind == "hood":
            if face == "front" and border(xx, yy, w, h):
                return metal(st["frame"], xx, yy, w, h, rnd), None
            return shade(st["hood"], 0.85 if xx % 3 == 0 else 1.0), None
        if kind == "map":
            if face == "back":
                return (222, 206, 160), None
            c = map_px(i, yy, w, h, rnd)
            if st.get("map_tint"):
                g = sum(c) // 3
                c = lerp((g, g, g), st["map_tint"], 0.4)
            return c, None
        if kind == "flower":
            c = flower_px(idx, xx, yy)
            if c is not None and st.get("flower_tint"):
                c = lerp(c, st["flower_tint"], 0.75)
            return c, None
        if kind == "wood":
            return (st["wood_d"] if yy % 3 == 0 else st["wood"]), None
        if kind == "blossom":
            if rnd.random() < 0.28:
                return None, None
            c = st["blossom"][rnd.randrange(len(st["blossom"]))]
            return c, (shade(c, 0.6) if st.get("blossom_glow") else None)
        if kind == "glass":
            r = HALO_R - 1.5 - yy
            hw = r * math.tan(math.radians(11.25)) + 0.35
            dx = abs(xx - 3)
            if dx > hw:
                return None, None
            if dx > hw - 1 or yy in (0, 4, 8) or yy == h - 1:
                return st["lead"], None
            c = st["glass"][(yy // 4 + idx + (xx > 3)) % len(st["glass"])]
            return c, shade(c, st.get("glass_glow", 0.8))
        if kind == "hub":
            d = math.hypot(i + 0.5, yy - 3.5)
            if d > 4.1:
                return None, None
            if d > 3.1:
                return metal(st["frame"], xx, yy, w, h, rnd), None
            if d > 1.4:
                a = math.atan2(yy - 3.5, i + 0.5)
                c = st["glass"][int((a + 4) * 3) % len(st["glass"])]
                return c, shade(c, 0.8)
            return st["sun"], st["sun"]
        if kind == "banner":
            return banner_px(st, idx, i if face == "front" else xx, yy, w, h, rnd, face == "front"), None
        if kind in ("clock", "compass"):
            frame = st["frame"] if kind == "clock" else st["compass_frame"]
            d = math.hypot(xx - 4, yy - 4)
            if d > 4.6:
                return None, None
            if d > 3.5 or face == "back":
                return metal(frame, xx, yy, w, h, rnd), None
            if (xx, yy) in ((4, 1), (7, 4), (4, 7), (1, 4)):
                return ((200, 30, 30) if kind == "compass" and yy == 1 else INK), None
            return st["dial" if kind == "clock" else "compass_dial"], None
        if kind == "hand_dark":
            return INK, None
        if kind == "needle":
            return ((210, 30, 30) if yy < h // 2 else (236, 236, 236)), None
        # the second phase's own parts
        if kind == "lightfeather":  # a feather of light: white, gold at the edges, glowing
            if yy == h - 1 and xx in (0, w - 1):
                return None, None
            c = (255, 252, 240) if 0 < xx < w - 1 else GOLD5[3]
            return c, c
        if kind == "smoke":  # a ragged strip of smoke: black, holes eaten through it, violet embers in it
            if rnd.random() < 0.12 + 0.5 * (yy / h) ** 2 * (1 if xx in (0, w - 1) else 0.4):
                return None, None
            if rnd.random() < 0.08:
                return (170, 80, 255), (150, 60, 230)
            return shade((26, 16, 36), 0.8 + 0.4 * rnd.random()), None
        if kind == "horn":
            ridge = yy % 2 == 0 and xx == 0
            return ((120, 50, 190) if ridge else shade((22, 14, 28), 0.9 + 0.2 * rnd.random())), ((110, 40, 180) if ridge else None)
        if kind == "eye":  # Aurelion's: a golden iris, glowing
            if face != "front":
                return (236, 230, 216), None
            if xx == 1 and yy == 1:
                return (10, 6, 8), None
            if border(xx, yy, w, h):
                return (236, 230, 216), None
            return (232, 168, 40), (255, 210, 110)
        if kind == "mouth":  # Nocthariel's: black lips, teeth, the red of its throat
            if face != "front":
                return (20, 12, 24), None
            if yy == 1 and 0 < xx < w - 1:
                return (150, 10, 20), (200, 20, 30)
            return ((230, 226, 214) if xx % 2 == 0 else (20, 12, 24)), None
        if kind == "missing":  # the missing texture
            c = MISSING if (xx + yy) % 2 == 0 else (0, 0, 0)
            return c, (c if c == MISSING else None)
        return (255, 0, 255), None

    return f


def paint_cubes(M, tex, glow, styles, rnd):
    """Paints every cube in its side's style; the thin sides of FLAT kinds as the paper's edge."""
    for cube in M.cubes:
        meta = M.meta[cube[0]]
        kind, s = meta["kind"], meta["s"]
        st = styles[s]
        d = dims(cube[3])[2]
        for face, rect in face_rects(cube).items():
            if kind in FLAT and d <= 1 and face not in ("front", "back"):
                paint_face(tex, None, rect, lambda xx, yy, w, h: (st["edge"], None))
                continue
            paint_face(tex, glow, rect, painter(kind, face, s, st, meta, rnd))
