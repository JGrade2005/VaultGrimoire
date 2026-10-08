# Vault Spellbook

A Forge 1.18.2 addon for the Wold's Vaults modpack that bridges Vault Hunters and Ars Nouveau: the
Vault Spellbook holds only "Vault"-tier glyphs, and each Vault glyph casts a Vault Hunters / Wold's
Vaults ability.

By JGrade. Version 0.9.0 (see [CHANGELOG.md](CHANGELOG.md)).

The book is creative-only for now (Vault Spellbook creative tab); recipes come in a later version.
Ability glyphs come from the same tab and only show in the book once the ability is learned.

## Installing
Build the jar (below) and put `build/libs/vaultspellbook-0.9.0.jar` in the Wold's Vaults instance's `mods/`
folder. Players and the server both need it.

## Requirements
Minecraft 1.18.2, Forge 40.3.11, Ars Nouveau 2.9.0, Vault Hunters 1.18.2-3.21.6.6884, Wold's Vaults 0.34.1.

### After a pack update
mods.toml accepts later Ars Nouveau, Vault Hunters and Wold's Vaults versions, so the mod keeps loading after an update.
A few features lean on their internals; if one of those changed, the feature stops working with one `vaultspellbook`
error in the log (no crash), or quietly drifts from the native ability. After updating, check:
- Arcane and Arcane Rail with Size Up, Shotgun and Charge: these repeat VH's own Arcane / Rail rules (reach, Arcane
  Pierce etching, damage), so compare with a native cast.
- Vein Miner glyphs (VH's private `blockBreakHandler`), Homing / Orbit on Ball of Lightning (VH's private orb fields),
  Size Up / colour visuals (Minecraft's entity renderer map) and multi-ability spells in the book GUI (Ars Nouveau's
  `GuiSpellBook.spellValidator`).
- New or renamed abilities: regenerate the glyph list (see "Regenerating the glyph list" below).

## Dev environment setup
Vault Hunters and Wold's Vaults jars cannot be redistributed, so `libs/` is git-ignored and must be
filled locally from a Wold's Vaults 0.34.1 install (Prism instance `WV33`, `minecraft/mods/`):

| File in `libs/` | Source |
|---|---|
| `wolds-vaults-official-mod-0.34.1.jar` | the pack's `mods/` folder |
| `vhapi-5.8.1.jar` | the pack's `mods/` folder |
| `forgified-sgui-1.0.2.jar`, `vhroller-1.4.0.jar`, `black_market_tweaks-1.8.jar`, `vault_hunters_extra_commands-1.8.0.jar`, `vault_hunters_extra_rules-2.1.1.jar` | nested inside the WV jar: `unzip -j wolds-vaults-official-mod-0.34.1.jar 'META-INF/jarjar/*.jar' -d libs` |

Vault Hunters itself and the other dependencies come from CurseMaven automatically.

For pack-accurate abilities in dev, copy the pack's `minecraft/config` and `minecraft/defaultconfigs`
folders into `run/` (also git-ignored).

### Regenerating the glyph list
The 100 ability glyphs (the generated block in `glyph/VaultGlyphs.java`, their lang keys and item models) come from
the pack's ability config. After copying the pack's config into `run/` (above) and decompiling the VH and WV jars into
`libs/src/` (used to find abilities with an area of effect), run:
```bash
python tools/gen_glyphs.py
```
A new ability also needs a row in `tools/ability_types.csv` (its class from `abilities.json`, the VH base class it
extends). The script prints the glyph counts and any id in `color/AbilityVisuals.java` the pack no longer has.

Optional dev-only testing aid: `freecam-forge-1.3.5.jar` (from the pack's `mods/`) in `libs/` is loaded by
`runClient` only when present; it is not a dependency of the mod.

## Augment glyphs
Augments modify the closest ability glyph before them, e.g. `[Fireball][Homing][Strengthen][Delay][Nova][Echo]`.
Amplify Area, Quicken, Discount, Echo, Strengthen, Accelerate, Homing, Orbit, Delay (a pause in the spell), Loop,
Ethereal and Low Gravity, plus Creative Loop (creative mode: re-fires the spell every tick until cast again).
Echo casts pay the ability's mana again but start no extra cooldown. **Loop** re-casts the ability before it
every time its cooldown ends (a normal cast: mana and cooldown), so you can cast other spells while it keeps
going; cast the same spell again to stop it. Delay glyphs at the very end of the spell add a wait before each
repeat.

**Bundled Spell** (form): throws a projectile carrying the rest of the spell, cast where it lands, e.g.
`[Bundled Spell][Shotgun][Size Up][Nova][Fireball]`. Nova, Earthquake, Battle Cry, Implode and Life Tap
happen at the landing point; projectile abilities launch from it; other abilities act on you. Size Up/Down
(hitbox included), Shotgun (2^n copies at half damage each), Accelerate, Homing, Orbit and Ethereal work on
both Bundled Spells and projectile abilities, Ball of Lightning included. Size Up, Shotgun and Ethereal also
reshape the Arcane beam: wider (hit radius +(size - 1) blocks), split into 2^n beams at half damage each, or
through blocks. Size Up and Shotgun also widen and split Arcane Rail. Size Up has no glyph limit.

**Charge** (augment for Arcane and Arcane Rail): winds the ability up for 1 s per Charge glyph, energy gathering
at your hand with a charge sound, then fires it with a blast sound. A charged Arcane deals 10x damage in its
first second, 1x less each second after, and 0.5x once it has passed 1x; a charged Rail deals 10x. Spellbook
Arcane hits out to 47 blocks.

## Ability colours
Ars's colour bookmark in the Vault Spellbook opens the **Ability Colours** tab. Pick a spell, then one of its
abilities, and set a colour: **Filter** lays the colour over the ability's own look, **Recolor** turns it grey
first and then paints it, so it can take any colour. Strength blends between the two. The tab previews the
ability's projectile or summon in 3D, or its icon. Everyone nearby sees your colours.
Colourable: projectiles and what they spawn (Fireball, Javelin, Ice Bolt, Storm Arrow and Blizzard, Ball of
Lightning, grenades, Warp), Smite bolts, Wall of Fangs, Necromancy summons, Decoy, the Arcane beam and the
Bundled Spell. Abilities VH only draws with particles or effects (Dash, Nova, Mana Shield, ...) and totems
(blocks) show greyed out.
Coloured Arcane and Arcane Rail become a solid beam whose hue pulses: pick a style (Solid core, Spiral
ribbons, Shockwave rings, Mega cannon) and how far the hue pulses in the colour tab. Coloured beams are drawn
out to your render distance.

## Configuration
Server config, per world: `saves/<world>/serverconfig/vaultspellbook-server.toml` (synced to clients).
Mana costs and cooldowns are not configurable: every cast uses the ability's own Vault Hunters
mana cost and cooldown.

| Key | Default | Meaning |
|---|---|---|
| `glyphs.disabledGlyphs` | `[]` | Specialization ids (from `config/the_vault/abilities.json`, e.g. `"Dash_Base"`) whose glyphs cannot be cast with the book. |
| `book.worksOutsideVaults` | `true` | If `false`, the book only casts inside vaults. |
| `augments.amplify_area.bonusPerGlyph` / `max` | `0.25` / `4` | Area of effect added per Amplify Area glyph. |
| `augments.quicken.cooldownSkipPerGlyph` / `max` | `0.1` / `5` | Share of the cooldown skipped per Quicken glyph. |
| `augments.discount.refundPerGlyph` / `max` | `0.1` / `5` | Share of the Vault mana refunded per Discount glyph. |
| `augments.echo.delaySeconds` / `max` | `0.5` / `1` | Time before the echo cast (at least 0.5 s, Minecraft's hurt immunity); Echo glyphs per spell. |
| `augments.strengthen.bonusPerGlyph` / `max` | `0.2` / `4` | Damage added per Strengthen glyph. |
| `augments.accelerate.bonusPerGlyph` / `max` | `0.25` / `4` | Projectile speed added per Accelerate glyph. |
| `augments.homing.range` / `max` | `16` / `1` | Range in which homing projectiles look for enemies. |
| `augments.orbit.radius` / `maxSeconds` / `max` | `2.5` / `30` / `1` | Orbit distance, and when orbiting projectiles that hit nothing are removed. |
| `augments.delay.secondsPerGlyph` / `max` | `1.0` / `5` | Pause per Delay glyph; Delay glyphs per spell. |
| `augments.loop.max` | `1` | Loop glyphs per ability. |
| `augments.size_up.bonusPerGlyph` | `0.5` | Projectile size added per glyph (multiplicative); no glyph limit. |
| `augments.size_down.reductionPerGlyph` / `max` | `0.25` / `4` | Projectile size removed per glyph (multiplicative). |
| `augments.ethereal.maxSeconds` / `max` | `10` / `1` | Ethereal ability projectiles are removed after this time. |
| `augments.shotgun.spreadDegrees` / `max` | `15` / `4` | Angle between split halves; Shotgun glyphs per Bundled Spell or ability. |
| `augments.low_gravity.reductionPerGlyph` / `max` | `0.5` / `2` | Share of projectile gravity removed per glyph. |
| `augments.creative_loop.max` | `1` | Creative Loop glyphs per spell. |
| `augments.charge.secondsPerGlyph` / `max` | `1.0` / `5` | Wind-up per Charge glyph. |
| `augments.charge.beamStartMultiplier` / `beamStepPerSecond` / `beamFloorMultiplier` | `10` / `1` / `0.5` | Charged Arcane damage curve. |
| `augments.charge.railMultiplier` | `10` | Charged Arcane Rail damage. |
| `beam.arcaneRange` | `47` | Spellbook Arcane hit range in blocks. |
| `bundled_spell.maxSeconds` | `30` | Bundled Spells that hit nothing are removed after this time. |

Client config, per player: `config/vaultspellbook-client.toml`: `beams.firstPersonOpacity` (`0.3`) and
`beams.firstPersonMaxRadius` (`0.6` blocks) dim and narrow your own Arcane beam / Rail in first person so you
can see what you aim at; others see it in full.

With JEI installed, ability glyphs are hidden from its item list (they come from learning abilities).

Ars Nouveau also writes `config/ars_nouveau/vault_<id>.toml` for every glyph; Vault Spellbook ignores those files.

## Build
```bash
./gradlew build
```
The jar is `build/libs/vaultspellbook-<version>.jar`. `build` also runs the unit tests (`./gradlew test`). Run the
dev client with `./gradlew runClient` (or the IntelliJ run configs from `./gradlew genIntellijRuns`).

The dev server (`./gradlew runServer`, after accepting the EULA in `run/eula.txt`) does not start with the full Wold's
Vaults runtime set: Lightman's Currency and Fusion fail Forge's dev-only `DistExecutor` check on a dedicated server
(they load fine on real servers). To test a server, comment out their two `runtimeOnly` lines in `build.gradle` first.

## License
GPLv3, see [LICENSE](LICENSE). The arcane charge and blast sounds were supplied by JGrade, who may redistribute them. Vault Hunters, Wold's Vaults
and Ars Nouveau are separate mods under their own licences and are not included.
