"""Generate Vault Spellbook glyph boilerplate from the Wold's Vaults pack config.

Writes, for every VH/WV ability specialization in the pack:
  - the generated entry list in VaultGlyphs.java (between the GENERATED markers); specializations
    missing from the pack's skill screen (abilities_gui_styles.json) are emitted as HIDDEN
  - en_us.json glyph keys (item name, Ars glyph name, Ars glyph description)
  - assets/ars_nouveau/models/item/glyph_vault_<id>.json pointing at VH's own ability icon
  - the same lang keys and models for the augment glyphs (AUGMENTS)

It also warns about specialization ids hard-coded in color/AbilityVisuals.java that the pack no longer has.

Inputs:
  - run/config/the_vault/abilities.json and abilities_gui_styles.json: the pack's ability config (copy the pack's
    config folder into the git-ignored run/, see README "Dev environment setup")
  - tools/ability_types.csv: each specialization's VH/WV class, core type and class chain. A new ability needs a
    row here (its class is the one abilities.json names; the core type is the VH base class it extends).
  - libs/src/: decompiled VH and WV sources (git-ignored), scanned for area-of-effect use (AbilityTrait.AREA)
Usage: python tools/gen_glyphs.py [path to the repository]   (default: the repository this script is in)
"""
import csv
import json
import re
import sys
from pathlib import Path

REPO = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
# Decompiled VH / WV sources (git-ignored libs/src), scanned for area-of-effect use (AbilityTrait.AREA).
SOURCES = [REPO / "libs/src/the_vault-3.21.6.6884", REPO / "libs/src/woldsvaults-0.34.1"]
CORE_CLASSES = {"Ability", "InstantAbility", "InstantManaAbility", "ToggleAbility", "ToggleManaAbility",
                "HoldAbility", "HoldManaAbility", "AbstractAbility"}
# Abilities whose area is only computed later, over their duration: Amplify Area can't reach it (#38).
LATE_AREA = {"Hunter_Base"}
# Abilities that fire vanilla projectiles owned by the player (verified in research/30-augments.md).
PROJECTILE_SPECS = {"Javelin_Base", "Javelin_Piercing", "Javelin_Scatter", "Javelin_Sight", "Fireball_Base",
                    "Fireball_Volley", "Fireball_Fireshot", "Ice_Bolt_Base", "Ice_Bolt_Blast", "Shard_Blizzard",
                    "Storm_Arrow_Base", "Chain_Lightning_Orbs", "Grenade_Base", "Grenade_Sticky", "Toxic_Grenade"}
# Abilities that fire a hitscan beam each tick while held (Size Up / Shotgun / Ethereal reshape it, #41).
BEAM_SPECS = {"Arcane_Base"}
# Abilities that fire one instant hitscan bolt (Size Up widens it, Shotgun splits it, #44).
BOLT_SPECS = {"Arcane_Rail"}
CONFIG = REPO / "run/config/the_vault"
# Badge drawn under every ability glyph icon (user-made, assets/vaultspellbook/textures/item/glyph_background.png).
ABILITY_BACKGROUND = "vaultspellbook:item/glyph_background"
TYPES = REPO / "tools/ability_types.csv"
GLYPHS_JAVA = REPO / "src/main/java/io/github/jgrade/vaultspellbook/glyph/VaultGlyphs.java"
LANG = REPO / "src/main/resources/assets/vaultspellbook/lang/en_us.json"
MODELS = REPO / "src/main/resources/assets/ars_nouveau/models/item"
# Hand-written tables keyed by specialization id (which glyphs can be coloured and how they preview).
VISUALS_JAVA = REPO / "src/main/java/io/github/jgrade/vaultspellbook/color/AbilityVisuals.java"
BEGIN, END = "            // BEGIN GENERATED (tools/gen_glyphs.py)", "            // END GENERATED"

KIND_BY_CORE = {
    "InstantManaAbility": "instant", "InstantAbility": "instant",
    "ToggleManaAbility": "toggle", "ToggleAbility": "toggle",
    "HoldManaAbility": "hold", "HoldAbility": "hold",
}
# Augment glyphs (decision #35): id -> (name, Ars item texture reused by reference, or a full
# resource location for our own texture, description).
AUGMENTS = {
    # id: (name, icon reused from Ars by reference, description)
    "amplify_area": ("Amplify Area", "aoe", "Increases the area of effect of the ability before it."),
    "quicken": ("Quicken", "duration_down", "Skips part of the cooldown the ability before it starts. Also works on toggles and holds when the glyph turns them off."),
    "discount": ("Discount", "dampen", "Refunds part of the Vault mana the ability before it costs, including the drain of toggles and holds while on."),
    "echo": ("Echo", "split", "Casts the ability before it a second time. Pays its mana again, no extra cooldown. Once per spell."),
    "strengthen": ("Strengthen", "amplify", "Increases the damage dealt by the ability before it and its projectiles."),
    "accelerate": ("Accelerate", "accelerate", "Makes the projectiles of the ability before it faster."),
    "homing": ("Homing", "homing", "Projectiles of the ability before it seek the nearest enemy."),
    "orbit": ("Orbit", "orbit", "Projectiles of the ability before it circle around you until they hit something."),
    "delay": ("Delay", "delay", "Pauses the spell before the glyphs that follow."),
    "loop": ("Loop", "extend_time", "Re-casts the ability before it every time its cooldown ends, paying mana and cooldown, while you cast other spells. Cast the spell again to stop. Delay glyphs at the end of the spell add a wait before each repeat."),
    "size_up": ("Size Up", "grow", "Makes the projectiles of the Bundled Spell or ability before it bigger, hitbox included, or the Arcane beam or Rail wider. No limit."),
    "size_down": ("Size Down", "crush", "Makes the projectiles of the Bundled Spell or ability before it smaller, hitbox included."),
    "ethereal": ("Ethereal", "intangible", "The projectiles of the Bundled Spell or ability before it, or the Arcane beam, pass through blocks but still hit enemies."),
    "shotgun": ("Shotgun", "vaultspellbook:item/glyph_shotgun", "Splits the projectiles of the Bundled Spell or ability before it, or the Arcane beam or Rail, in two, 15 degrees apart, each at half damage. Stack it for more."),
    "low_gravity": ("Low Gravity", "slowfall", "The projectiles of the ability before it drop less. Two remove the drop."),
    "charge": ("Charge", "channel", "Charges the Arcane beam or Rail before it, 1 s per glyph, then fires it. A charged Arcane starts at 10x damage, losing 1x each second, then 0.5x after 10 s; a charged Rail deals 10x."),
    "creative_loop": ("Creative Loop", "linger", "Creative mode only: casting the spell starts re-firing it every tick; cast it again to stop."),
    "bundled_spell": ("Bundled Spell", "projectile", "Throws a projectile carrying the rest of the spell, which is cast where it lands."),
}

# Groups the pack's abilities_gui_styles.json has no style for (Execute, Summon Eternal, Farmer);
# VH still ships their icons under the_vault:gui/abilities/.
ICON_FALLBACKS = {
    "Execute_Base": "the_vault:gui/abilities/execute",
    "Summon_Eternal_Base": "the_vault:gui/abilities/summon_eternal",
    "Farmer_Base": "the_vault:gui/abilities/farmer",
    "Farmer_Melon": "the_vault:gui/abilities/farmer_melon",
    "Farmer_Cactus": "the_vault:gui/abilities/farmer_cactus",
    "Farmer_Animal": "the_vault:gui/abilities/farmer_animal",
}
DESCRIPTION = {
    "instant": "Casts your {name} ability (Vault Hunters). Uses its Vault mana cost and cooldown.",
    "toggle": "Toggles your {name} ability on or off (Vault Hunters), like its ability key.",
    "hold": "Turns your {name} ability on or off (Vault Hunters). While on, it keeps going even when you "
            "select or use other abilities.",
    "veinMiner": "Turns your {name} on or off (Vault Hunters). While on, it keeps vein mining even when you "
                 "select or use other abilities.",
}
# Vein Miner glyphs are an on/off switch independent of the selected ability (decision #33).
VEIN_MINER_GROUP = "Vein_Miner"
MANA_DRAIN_NOTE = " Drains Vault mana while active."


def glyph_id(specialization: str) -> str:
    return "vault_" + specialization.lower()


def read_core_types() -> tuple[dict[str, str], dict[str, list[str]]]:
    """Core type per specialization, and its class chain (class + non-core parents) for scanning."""
    types, chains = {}, {}
    with TYPES.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            spec = row["specialization"]
            types[spec] = row["core_type"]
            chains[spec] = [row["class"]] + [c.strip() for c in row["extends_chain"].split(">")]
    return types, chains


def area_specs(chains: dict[str, list[str]]) -> set[str]:
    """Specializations whose own code (not VH core classes) uses AreaOfEffectHelper."""
    index = {p.stem: p for root in SOURCES for p in root.rglob("*.java")}
    found = set()
    for spec, classes in chains.items():
        for cls in classes:
            if cls not in CORE_CLASSES and cls in index and "AreaOfEffectHelper" in index[cls].read_text(
                    encoding="utf-8", errors="replace"):
                found.add(spec)
                break
    return found


def trait_args(spec: str, core: str, area: set[str]) -> str:
    traits = []
    if spec in area and spec not in LATE_AREA:
        traits.append("AREA")
    if spec in PROJECTILE_SPECS:
        traits.append("PROJECTILE")
    if spec in BEAM_SPECS:
        traits.append("BEAM")
    if spec in BOLT_SPECS:
        traits.append("BOLT")
    if "Mana" in core:
        traits.append("MANA")
    return "".join(", " + t for t in traits)


def read_specializations() -> list[tuple[str, str, str]]:
    """(group id, specialization id, display name) in pack order."""
    tree = json.loads((CONFIG / "abilities.json").read_text(encoding="utf-8"))["tree"]["skills"]
    return [(group["id"], spec["id"], spec["name"])
            for group in tree for spec in group.get("specializations", [group])]


def read_styles() -> dict:
    return json.loads((CONFIG / "abilities_gui_styles.json").read_text(encoding="utf-8"))["styles"]


def read_skill_screen(styles: dict) -> set[str]:
    """Specializations the pack's ability skill screen shows; only these are learnable (decision #30)."""
    return {spec for style in styles.values() for spec in style.get("specializationStyles", {})}


def read_icons(styles: dict) -> dict[str, str]:
    icons = {}
    for group_id, style in styles.items():
        spec_styles = style.get("specializationStyles", {})
        group_icon = next((s["icon"] for s in spec_styles.values() if "icon" in s), None)
        icons[group_id] = group_icon
        icons.update({spec: s["icon"] for spec, s in spec_styles.items() if "icon" in s})
    return icons


def main() -> None:
    styles = read_styles()
    (types, chains), icons, screen = read_core_types(), read_icons(styles), read_skill_screen(styles)
    area = area_specs(chains)
    specs = read_specializations()
    entries, lang, missing_icons = [], {}, []
    MODELS.mkdir(parents=True, exist_ok=True)
    for old in MODELS.glob("glyph_vault_*.json"):
        old.unlink()

    for group, spec, name in specs:
        kind = "veinMiner" if group == VEIN_MINER_GROUP else KIND_BY_CORE[types[spec]]
        visibility = "SHOWN" if spec in screen else "HIDDEN"
        entries.append(f'            {kind}("{spec}", "{name}", {visibility}{trait_args(spec, types[spec], area)}),')
        gid = glyph_id(spec)
        display = name  # exactly as the VH skill screen shows it (decision #30)
        lang[f"item.ars_nouveau.glyph_{gid}"] = display
        lang[f"ars_nouveau.glyph_name.{gid}"] = display
        description = DESCRIPTION[kind].format(name=name)
        if types[spec] == "HoldManaAbility":
            description += MANA_DRAIN_NOTE
        lang[f"ars_nouveau.glyph_desc.{gid}"] = description
        icon = icons.get(spec) or ICON_FALLBACKS.get(spec) or icons.get(group)
        if icon is None:
            missing_icons.append(spec)
            icon = "the_vault:gui/abilities/dash"
        # The user's glyph badge under VH's icon, both by reference (no VH art copied); augments keep Ars icons.
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": ABILITY_BACKGROUND, "layer1": icon}}
        (MODELS / f"glyph_{gid}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")

    for key, (name, icon, description) in AUGMENTS.items():
        gid = f"vault_{key}"
        lang[f"item.ars_nouveau.glyph_{gid}"] = name
        lang[f"ars_nouveau.glyph_name.{gid}"] = name
        lang[f"ars_nouveau.glyph_desc.{gid}"] = description
        texture = icon if ":" in icon else f"ars_nouveau:items/{icon}"
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}
        (MODELS / f"glyph_{gid}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")

    entries[-1] = entries[-1].rstrip(",")
    java = GLYPHS_JAVA.read_text(encoding="utf-8")
    start, end = java.index(BEGIN) + len(BEGIN), java.index(END)
    GLYPHS_JAVA.write_text(java[:start] + "\n" + "\n".join(entries) + "\n" + java[end:], encoding="utf-8", newline="\n")

    current = json.loads(LANG.read_text(encoding="utf-8"))
    kept = {k: v for k, v in current.items()
            if not re.match(r"(item\.ars_nouveau\.glyph_|ars_nouveau\.glyph_(name|desc)\.)vault_", k)}
    LANG.write_text(json.dumps(kept | lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    counts = {k: sum(1 for e in entries if e.strip().startswith(k + "(")) for k in DESCRIPTION}
    hidden = [spec for _, spec, _ in specs if spec not in screen]
    print(f"{len(entries)} glyphs {counts}; shown {len(entries) - len(hidden)}, hidden {len(hidden)}: {hidden}")
    print(f"icons missing (group fallback used): {missing_icons or 'none'}")
    known = {spec for _, spec, _ in specs}
    stale = sorted(set(re.findall(r'"([A-Z][A-Za-z]*_[A-Za-z_]+)"', VISUALS_JAVA.read_text(encoding="utf-8"))) - known)
    print(f"AbilityVisuals ids the pack doesn't have: {stale or 'none'}")


if __name__ == "__main__":
    main()
