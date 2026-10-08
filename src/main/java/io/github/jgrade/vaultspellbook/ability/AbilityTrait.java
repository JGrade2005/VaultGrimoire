package io.github.jgrade.vaultspellbook.ability;

// What an ability glyph is, used to decide which augments can follow it.
public enum AbilityTrait {
    INSTANT,
    TOGGLE,
    HOLD,
    // Measures an area of effect when cast (uses VH's AreaOfEffectHelper).
    AREA,
    // Fires vanilla projectile entities owned by the player.
    PROJECTILE,
    // Fires a hitscan beam every tick while held (Arcane); Size Up, Shotgun and Ethereal reshape it.
    BEAM,
    // Fires one instant hitscan bolt (Arcane Rail); Size Up widens it and Shotgun splits it.
    BOLT,
    // Costs Vault mana (instant cost or drain while active).
    MANA
}
