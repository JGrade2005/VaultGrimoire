package io.github.jgrade.vaultspellbook.ability;

import iskallia.vault.core.event.CommonEvents;
import iskallia.vault.skill.ability.effect.spi.core.Ability;

import java.util.function.BooleanSupplier;

// Tells whether a specific ability instance actually cast during a call. VH fires
// CommonEvents.ABILITY_CAST only after a successful cast (InstantAbility.doActionPost,
// ToggleAbility.onAction), while Ability.onKeyUp returns true even when the cast itself failed.
// Server thread only.
public final class AbilityCastTracker {
    private static final Object LISTENER_OWNER = new Object();
    private static Ability watched;
    private static boolean watchedCast;

    private AbilityCastTracker() {
    }

    // Registers the permanent ABILITY_CAST listener. Call once during mod construction.
    public static void register() {
        CommonEvents.ABILITY_CAST.register(LISTENER_OWNER, data -> {
            if (data.getAbility() == watched) {
                watchedCast = true;
            }
        });
    }

    // Runs the action while watching the ability; returns the action's own result and records whether the ability cast.
    public static Outcome watch(Ability ability, BooleanSupplier action) {
        watched = ability;
        watchedCast = false;
        try {
            boolean accepted = action.getAsBoolean();
            return new Outcome(accepted, watchedCast);
        } finally {
            watched = null;
            watchedCast = false;
        }
    }

    // accepted: VH's key handler ran past its checks; cast: the ability fired ABILITY_CAST.
    public record Outcome(boolean accepted, boolean cast) {
    }
}
