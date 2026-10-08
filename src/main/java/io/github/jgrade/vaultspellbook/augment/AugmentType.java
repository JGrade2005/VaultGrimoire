package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;

// The Vault augment glyphs. Most modify the closest ability glyph or Bundled
// Spell before them; Delay and Creative Loop act on the spell itself.
public enum AugmentType {
    AMPLIFY_AREA("amplify_area"),
    QUICKEN("quicken"),
    DISCOUNT("discount"),
    ECHO("echo"),
    STRENGTHEN("strengthen"),
    ACCELERATE("accelerate"),
    HOMING("homing"),
    ORBIT("orbit"),
    DELAY("delay"),
    LOOP("loop"),
    SIZE_UP("size_up"),
    SIZE_DOWN("size_down"),
    ETHEREAL("ethereal"),
    SHOTGUN("shotgun"),
    LOW_GRAVITY("low_gravity"),
    CREATIVE_LOOP("creative_loop"),
    CHARGE("charge");

    private final String name;

    AugmentType(String name) {
        this.name = name;
    }

    // Ars glyph id, e.g. "vault_amplify_area" (item ars_nouveau:glyph_vault_amplify_area).
    public String glyphId() {
        return "vault_" + name;
    }

    // Whether this augment can modify the given ability glyph.
    public boolean canModify(AbilityGlyph ability) {
        boolean instant = ability.has(AbilityTrait.INSTANT);
        boolean projectile = ability.has(AbilityTrait.PROJECTILE);
        return switch (this) {
            case AMPLIFY_AREA -> instant && (ability.has(AbilityTrait.AREA) || projectile);
            case QUICKEN -> true;
            case DISCOUNT -> ability.has(AbilityTrait.MANA);
            case ECHO, STRENGTHEN, LOOP -> instant;
            case SIZE_UP, SHOTGUN -> (instant && projectile) || ability.has(AbilityTrait.BEAM) || ability.has(AbilityTrait.BOLT);
            case ETHEREAL -> (instant && projectile) || ability.has(AbilityTrait.BEAM);
            case ACCELERATE, HOMING, ORBIT, LOW_GRAVITY, SIZE_DOWN -> instant && projectile;
            case CHARGE -> ability.has(AbilityTrait.BEAM) || ability.has(AbilityTrait.BOLT);
            case DELAY, CREATIVE_LOOP -> false;
        };
    }

    // Whether this augment can modify a Bundled Spell projectile.
    public boolean canModifyBundle() {
        return switch (this) {
            case ACCELERATE, HOMING, ORBIT, SIZE_UP, SIZE_DOWN, ETHEREAL, SHOTGUN -> true;
            default -> false;
        };
    }
}
