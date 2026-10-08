# Changelog

## 0.9.0 — 2026-10-07
First release (private / pack only). The Vault Spellbook is creative-only for now; recipes come later.

### Added
- **Vault Spellbook**: an Ars Nouveau spellbook that looks like the Creative Spell Book and holds only
  Vault-tier glyphs. Normal Ars books reject Vault glyphs. Own creative tab.
- **Ability glyphs** for every ability in the Wold's Vaults skill screen (one per specialization, named as
  the skill screen names them), drawn on the Vault glyph badge. A glyph casts the player's own learned ability
  with its native mana cost and cooldown; failed glyphs are skipped with a message, and a live cooldown
  countdown shows on the action bar.
- Toggles switch on/off; Vein Miner, Arcane and Levitate keep running while other abilities are used
  (spellbook casts only).
- **Augment glyphs**: Amplify Area, Quicken, Discount, Echo, Strengthen, Accelerate, Homing, Orbit, Delay,
  Loop, Size Up (no limit), Size Down, Ethereal, Shotgun (own icon), Low Gravity, Creative Loop and Charge.
- **Bundled Spell** form: throws a projectile carrying the rest of the spell, cast where it lands.
- Projectile augments on Vault Hunters projectiles (hitbox included for Size), Homing/Orbit on Ball of
  Lightning; Size Up / Shotgun / Ethereal reshape the Arcane beam; Size Up / Shotgun on Arcane Rail.
- **Charge**: winds Arcane or Arcane Rail up (1 s per glyph, with charge sound), then fires it charged:
  Arcane 10x damage decaying 1x per second to 0.5x, Rail 10x.
- Spellbook Arcane hits out to 47 blocks.
- **Ability colours** tab (Ars's colour bookmark): per-ability Filter / Recolor with strength, 3D previews,
  synced to everyone; coloured Arcane and Arcane Rail become solid hue-pulsing beams in four styles
  (Solid core, Spiral ribbons, Shockwave rings, Mega cannon).
- Server config for every augment strength and limit, disabled glyphs and vault-only casting.
- Loop re-casts its ability whenever its cooldown ends until the spell is cast again; Delay at the end of the
  spell times the repeats.
- JEI: ability glyphs hidden; Vault glyphs listed in a fixed order after Ars Nouveau's.
- Client config: your own beam is dimmed and narrowed in first person.

### Fixed
- Server tick crash when an Ethereal javelin (e.g. with Homing) touched its owner.
- Discount on toggles no longer refunds ticks the Ethereal talent already made free.
- Coloured entities: another player's Storm Arrow storms and projectiles nearby keep their own look.
- The cooldown countdown stops when another action-bar message arrives instead of hiding it.
- Entities no longer get an empty `ForgeData` tag saved with them.
- A failed 3D preview in the colour tab only falls back to the icon for that entity.

### Changed
- Augment `max` settings accept at most 10 (Shotgun 6: 64 copies); larger values reset to the default.
- Colour tab RGB sliders go down to 0.
- Hidden ability glyphs show their own message ("not in Wold's Vaults' skill screen").
- Unknown ids in `glyphs.disabledGlyphs` are logged when the config loads.
- Lighter on the server and client with many Shotgun copies: Arcane beams, coloured beam drawing, homing and enlarged
  projectiles do less work per tick or frame.
