package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;

// A charged Arcane beam's damage multiplier over its hold: 10x during the first second,
// 1x less each second after (9x, 8x, ... 1x in the tenth second), then 0.5x for the rest of the hold.
// Server config: start, step per second and floor.
public final class ChargeCurve {
    private ChargeCurve() {
    }

    public static float beamMultiplier(long heldTicks) {
        return beamMultiplier(heldTicks, VaultSpellbookConfig.CHARGE_BEAM_START.get(), VaultSpellbookConfig.CHARGE_BEAM_STEP.get(),
                VaultSpellbookConfig.CHARGE_BEAM_FLOOR.get());
    }

    static float beamMultiplier(long heldTicks, double start, double step, double floor) {
        double value = start - Math.floor(heldTicks / 20.0) * step;
        return (float) (value >= 1.0 ? value : floor);
    }
}
