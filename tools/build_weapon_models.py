"""
Makes the rare-metal weapons' item models for every metal from the blacksteel ones (models/item/blacksteel_<weapon>.json,
exported from Blender by tools/models/build_blacksteel_weapons.py): the same elements, the metal's own parts texture
(textures/item/<metal>_weapon_parts.png).

  python tools/build_weapon_models.py
"""
import json

ITEMS = "src/main/resources/assets/divinis/models/item/"
METALS = ["moonsilver", "crimsonite", "mithril", "etherite", "adamantite", "dragonsteel", "divinite", "valorium", "voidsteel"]
WEAPONS = ["sword", "longsword", "shortsword", "dagger", "sabre", "spear", "lance", "halberd", "two_handed_axe"]

for w in WEAPONS:
    model = json.load(open(ITEMS + f"blacksteel_{w}.json", encoding="utf-8"))
    for m in METALS:
        out = json.loads(json.dumps(model))
        out["textures"] = {k: v.replace("blacksteel_", m + "_") for k, v in model["textures"].items()}
        out["credit"] = f"Made by tools/build_weapon_models.py from blacksteel_{w}.json"
        json.dump(out, open(ITEMS + f"{m}_{w}.json", "w", encoding="utf-8"), indent=2)
print(len(WEAPONS) * len(METALS), "weapon models")
