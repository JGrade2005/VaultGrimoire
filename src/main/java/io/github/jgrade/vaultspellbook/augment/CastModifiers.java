package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.cast.SpellPlan;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;

import javax.annotation.Nullable;

// The combined effect of the augments on one ability glyph, with the server config's strengths applied, and its colour.
public record CastModifiers(float areaBonus, float quickenFraction, float discountFraction, boolean echo,
                            float damageMultiplier, float speedMultiplier, boolean homing, boolean orbit,
                            float gravityReduction, boolean ethereal, int shotgunSplits, float scale,
                            @Nullable AbilityColor color, int chargeGlyphs) {

    public static final CastModifiers NONE = new CastModifiers(0, 0, 0, false, 1, 1, false, false, 0, false, 0, 1, null, 0);

    public static CastModifiers of(SpellPlan.Cast cast) {
        return new CastModifiers(
                (float) (cast.count(AugmentType.AMPLIFY_AREA) * VaultSpellbookConfig.AMPLIFY_AREA_BONUS.get()),
                (float) Math.min(1.0, cast.count(AugmentType.QUICKEN) * VaultSpellbookConfig.QUICKEN_COOLDOWN_SKIP.get()),
                (float) Math.min(1.0, cast.count(AugmentType.DISCOUNT) * VaultSpellbookConfig.DISCOUNT_REFUND.get()),
                cast.count(AugmentType.ECHO) > 0,
                (float) ((1.0 + cast.count(AugmentType.STRENGTHEN) * VaultSpellbookConfig.STRENGTHEN_DAMAGE_BONUS.get())
                        * railCharge(cast)),
                (float) (1.0 + cast.count(AugmentType.ACCELERATE) * VaultSpellbookConfig.ACCELERATE_SPEED_BONUS.get()),
                cast.count(AugmentType.HOMING) > 0,
                cast.count(AugmentType.ORBIT) > 0,
                (float) Math.min(1.0, cast.count(AugmentType.LOW_GRAVITY) * VaultSpellbookConfig.LOW_GRAVITY_REDUCTION.get()),
                cast.count(AugmentType.ETHEREAL) > 0,
                cast.count(AugmentType.SHOTGUN),
                (float) (Math.pow(1 + VaultSpellbookConfig.SIZE_UP_BONUS.get(), cast.count(AugmentType.SIZE_UP))
                        * Math.pow(1 - VaultSpellbookConfig.SIZE_DOWN_REDUCTION.get(), cast.count(AugmentType.SIZE_DOWN))),
                cast.color(),
                cast.count(AugmentType.CHARGE));
    }

    // A charged Rail deals a flat multiple; a charged Arcane's multiplier changes over the hold (ChargeCurve).
    private static double railCharge(SpellPlan.Cast cast) {
        return cast.count(AugmentType.CHARGE) > 0 && cast.ability().has(AbilityTrait.BOLT)
                ? VaultSpellbookConfig.CHARGE_RAIL_MULTIPLIER.get() : 1.0;
    }

    // Whether the glyph has Charge: it winds up first and fires charged.
    public boolean charged() {
        return chargeGlyphs > 0;
    }

    // The same modifiers with an extra damage factor (Shotgun splits halve each copy's damage).
    public CastModifiers withDamageFactor(float factor) {
        return new CastModifiers(areaBonus, quickenFraction, discountFraction, echo, damageMultiplier * factor,
                speedMultiplier, homing, orbit, gravityReduction, ethereal, shotgunSplits, scale, color, chargeGlyphs);
    }

    // Whether projectiles spawned by the cast need to be tagged or steered.
    public boolean affectsProjectiles() {
        return damageMultiplier != 1.0F || speedMultiplier != 1.0F || homing || orbit || areaBonus > 0
                || gravityReduction > 0 || ethereal || scale != 1.0F;
    }
}
