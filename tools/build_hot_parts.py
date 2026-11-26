"""
The hot weapon parts: for each part (sword blade, spear head, ...) one texture of glowing metal, the same for every
rare metal ("<metal>_hot_<part>", registry.ModWeapons.HOT_PARTS: red-hot metal looks alike whatever it is). Each is
the part's icon (tools/build_weapon_parts.py: the same shape, outline and shading) painted in the colours of glowing
metal, in four frames a little brighter and dimmer so it shimmers (textures/item/hot_<part>.png + .mcmeta); one model
per part (models/item/hot_<part>.json), which every metal's hot part points at. Writes their names too.

  python tools/build_hot_parts.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
_src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "build_weapon_parts.py"), encoding="utf-8").read()
exec(_src[:_src.rindex("\nmain()")])  # the parts' shapes, painting and PNG writer, without running that script

# glowing metal: deep, base, light, shine (as METALS of build_weapon_parts.py)
METALS["hot"] = ((96, 22, 8), (206, 72, 16), (250, 150, 40), (255, 236, 170))
FRAMES = 4


def brighter(c, k):
    return tuple(min(255, int(v * k)) for v in c[:3]) + (c[3],)


def main():
    names = {}
    for weapon, (part, title, _) in PARTS.items():
        img = paint(part, "hot")
        frames = []
        for f in range(FRAMES):
            k = 0.9 + 0.08 * f if f < 3 else 1.02
            for row in img:
                frames.append([brighter(c, k) if c[3] else c for c in row])
        save_png(ASSETS + f"textures/item/hot_{part}.png", frames)
        json.dump({"animation": {"frametime": 4, "interpolate": True}}, open(ASSETS + f"textures/item/hot_{part}.png.mcmeta", "w"), indent=2)
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"divinis:item/hot_{part}"}},
                  open(ASSETS + f"models/item/hot_{part}.json", "w"), indent=2)
        for metal in METALS:
            if metal == "hot":
                continue
            json.dump({"parent": f"divinis:item/hot_{part}"}, open(ASSETS + f"models/item/{metal}_hot_{part}.json", "w"), indent=2)
            names[f"item.divinis.{metal}_hot_{part}"] = f"Hot {metal.capitalize()} {title}"
    p = ASSETS + "lang/en_us.json"
    text = open(p, encoding="utf-8").read()
    text = "\n".join(ln for ln in text.split("\n") if not any(f'"{k}"' in ln for k in names))
    anchor = '  "item.divinis.voidsteel_ingot": "Voidsteel Ingot",'
    assert anchor in text
    text = text.replace(anchor, anchor + "".join(f'\n  "{k}": "{v}",' for k, v in names.items()), 1)
    json.loads(text)
    open(p, "w", encoding="utf-8").write(text)
    print(len(PARTS), "hot part textures,", len(names), "items")


main()
