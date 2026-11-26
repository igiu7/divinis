"""
Paints the skill books' item textures (skill.SkillBooks; item/skill_book_<id>.png) on the vanilla book: its cover
repainted in the book's colour (its own shades kept), a small emblem of the book on the cover. The game draws them
with the enchantment glint.

  python tools/build_skill_books.py [minecraft resources jar] [out dir]

The jar defaults to the one the build downloads, the out dir to the mod's textures/item.
"""
import glob
import os
import struct
import sys
import zipfile
import zlib

# the cover colour (dark, light), the emblem's colour and its 5x5 picture
BOOKS = {
    "fisher": (((24, 56, 96), (70, 140, 200)), (240, 240, 250), [
        ".....",
        ".#.#.",
        "#####",
        ".#.#.",
        "....."]),
    "miner": (((60, 60, 66), (140, 140, 150)), (110, 220, 255), [
        "###..",
        "..#..",
        "..#..",
        "..#..",
        "....."]),
    "lumberjack": (((70, 40, 20), (150, 96, 54)), (210, 210, 220), [
        "##...",
        "###..",
        "..#..",
        "..#..",
        "..#.."]),
    "farmer": (((70, 90, 20), (150, 180, 60)), (250, 220, 90), [
        "..#..",
        ".###.",
        "..#..",
        ".###.",
        "..#.."]),
    "herbalist": (((20, 80, 40), (70, 170, 90)), (240, 70, 70), [
        "..#..",
        "..#..",
        "#####",
        "..#..",
        "..#.."]),
    "wanderer": (((90, 70, 40), (190, 160, 110)), (250, 150, 50), [
        "..#..",
        ".#.#.",
        "#...#",
        ".#.#.",
        "..#.."]),
    "hunter": (((90, 30, 24), (180, 80, 60)), (240, 230, 210), [
        "#...#",
        ".#.#.",
        "..#..",
        ".#.#.",
        "#...#"]),
    "dungeon_scout": (((40, 24, 70), (120, 80, 180)), (255, 210, 80), [
        ".###.",
        "#...#",
        "#####",
        "#.#.#",
        "#####"]),
    "scholar": (((20, 30, 70), (80, 100, 190)), (255, 240, 180), [
        "..#..",
        ".###.",
        "#####",
        ".###.",
        "..#.."]),
}
EMBLEM_AT = (6, 5)  # its top-left on the cover


def decode(data):
    pos, idat, palette, alpha = 8, b"", None, None
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            alpha = list(body)
        elif tag == b"IDAT":
            idat += body
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ctype]
    stride = (w * depth + 7) // 8 if ctype == 3 else w * bpp  # a palette may pack 1, 2 or 4 bits a pixel
    raw, rows, prev, i = zlib.decompress(idat), [], bytearray(stride), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b, c = prev[x], (prev[x - bpp] if x >= bpp else 0)
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        row = []
        for x in range(w):
            if ctype == 6:
                row.append(tuple(line[x * 4:x * 4 + 4]))
            elif ctype == 2:
                row.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif ctype == 3:
                bit = x * depth
                k = (line[bit // 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1)
                row.append(palette[k] + ((alpha[k] if alpha and k < len(alpha) else 255),))
            elif ctype == 4:
                row.append((line[x * 2],) * 3 + (line[x * 2 + 1],))
            else:
                row.append((line[x],) * 3 + (255,))
        rows.append(row)
        prev = line
    return rows


def save(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def is_cover(rgb):
    """The vanilla book's cover is brown leather: red well over blue."""
    r, g, b = rgb
    return r > b + 18 and r >= g


def paint(book, cover, emblem_color, emblem):
    lum = [0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2] for row in book for p in row if p[3] and is_cover(p[:3])]
    lo, hi = min(lum), max(lum)
    dark, light = cover
    out = []
    for y, row in enumerate(book):
        line = []
        for x, p in enumerate(row):
            if p[3] and is_cover(p[:3]):
                t = (0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2] - lo) / max(1, hi - lo)
                p = tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(3)) + (p[3],)
            line.append(p)
        out.append(line)
    ex, ey = EMBLEM_AT
    for dy, r in enumerate(emblem):
        for dx, ch in enumerate(r):
            if ch == "#" and out[ey + dy][ex + dx][3] and is_cover(book[ey + dy][ex + dx][:3]):
                out[ey + dy][ex + dx] = emblem_color + (255,)
    return out


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    jar = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else glob.glob(os.path.join(root, "build", "moddev", "artifacts", "*minecraft-resources.jar"))[0]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(root, "src", "main", "resources", "assets", "divinis", "textures", "item")
    with zipfile.ZipFile(jar) as z:
        book = decode(z.read("assets/minecraft/textures/item/book.png"))
    for name, (cover, color, emblem) in BOOKS.items():
        save(os.path.join(out, "skill_book_" + name + ".png"), paint(book, cover, color, emblem))
    print("saved", len(BOOKS), "skill books to", os.path.normpath(out))


if __name__ == "__main__":
    main()
