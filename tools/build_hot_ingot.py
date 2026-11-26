"""
The hot ingot: one model for the hot ingots of every rare metal (registry.ModIngots, "<metal>_hot_ingot"), made from
the mithril ingot's (models/item/mithril_ingot.json, from Blender): the same shape, every element glowing at full
light (NeoForge's neoforge_data), its texture the mithril ingot's repainted in the colours of glowing metal - the
shades of the ingot mapped to a ramp from dark red to white-yellow, in four frames a little brighter and dimmer so it
shimmers (textures/item/hot_ingot.png + .mcmeta). Each metal's hot ingot model only points at it.

  python tools/build_hot_ingot.py
"""
import json
import struct
import zlib

A = "src/main/resources/assets/divinis/"
METALS = ["blacksteel", "moonsilver", "crimsonite", "mithril", "etherite", "adamantite", "dragonsteel", "divinite", "valorium", "voidsteel"]
HOT = [(52, 6, 4), (90, 14, 8), (130, 26, 10), (176, 48, 14), (214, 80, 18), (240, 122, 30), (252, 160, 46),
       (255, 196, 80), (255, 226, 140), (255, 248, 214)]


def load(path):
    data = open(path, "rb").read()
    pos, idat, w, h, ctype = 8, b"", 0, 0, 6
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
            ctype = body[9]
        if t == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    ch = 4 if ctype == 6 else 3
    stride = w * ch + 1
    rows, prev = [], [0] * (w * ch)
    for y in range(h):
        f = raw[y * stride]
        line = list(raw[y * stride + 1:(y + 1) * stride])
        for i in range(len(line)):
            a = line[i - ch] if i >= ch else 0
            b = prev[i]
            c = prev[i - ch] if i >= ch else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        rows.append([tuple(line[x * ch:x * ch + ch]) + ((255,) if ch == 3 else ()) for x in range(w)])
    return w, h, rows


def save(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def main():
    w, h, px = load(A + "textures/item/mithril_ingot.png")
    lum = [[0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2] for p in row] for row in px]
    seen = [lum[y][x] for y in range(h) for x in range(w) if px[y][x][3] > 0]
    lo, hi = min(seen), max(seen)
    frames = 4
    out = []
    for f in range(frames):
        shift = (f - 1.5) * 0.45   # the whole ingot a little brighter, then dimmer
        for y in range(h):
            row = []
            for x in range(w):
                if px[y][x][3] == 0:
                    row.append((0, 0, 0, 0))
                    continue
                t = (lum[y][x] - lo) / max(1.0, hi - lo)
                level = 2.0 + t * 7.0 + shift + (0.5 if (x * 3 + y * 5 + f) % 7 == 0 else 0)
                c = HOT[max(0, min(len(HOT) - 1, int(round(level))))]
                row.append(c + (px[y][x][3],))
            out.append(row)
    save(A + "textures/item/hot_ingot.png", w, h * frames, out)
    json.dump({"animation": {"frametime": 4, "interpolate": True}}, open(A + "textures/item/hot_ingot.png.mcmeta", "w"), indent=2)

    model = json.load(open(A + "models/item/mithril_ingot.json", encoding="utf-8"))
    model["credit"] = "Made by tools/build_hot_ingot.py from mithril_ingot.json"
    model["textures"] = {"0": "divinis:item/hot_ingot", "particle": "divinis:item/hot_ingot"}
    for e in model["elements"]:
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        e["shade"] = False
    json.dump(model, open(A + "models/item/hot_ingot.json", "w", encoding="utf-8"), indent=2)
    for m in METALS:
        json.dump({"parent": "divinis:item/hot_ingot"}, open(A + f"models/item/{m}_hot_ingot.json", "w"), indent=2)

    p = A + "lang/en_us.json"
    text = open(p, encoding="utf-8").read()
    lines = [ln for ln in text.split("\n") if "_hot_ingot\"" not in ln]
    text = "\n".join(lines)
    anchor = '  "item.divinis.voidsteel_ingot": "Voidsteel Ingot",'
    assert anchor in text
    text = text.replace(anchor, anchor + "".join(f'\n  "item.divinis.{m}_hot_ingot": "Hot {m.capitalize()} Ingot",' for m in METALS), 1)
    json.loads(text)
    open(p, "w", encoding="utf-8").write(text)
    print("hot ingot:", w, "x", h, "x", frames, "frames;", len(METALS), "items")


main()
