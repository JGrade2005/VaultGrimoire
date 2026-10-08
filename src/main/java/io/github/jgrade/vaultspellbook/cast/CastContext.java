package io.github.jgrade.vaultspellbook.cast;

import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

// How one run of a spell timeline casts.
//   origin            null: from the player; otherwise a Bundled Spell's landing point
//   landings          null: every cast is native; otherwise shared by all landings of one throw
//                     (shotgun copies, Creative Loop) so only the first cast of each ability is native
//   damageMultiplier  extra damage factor, e.g. 0.5 per Shotgun split
public record CastContext(@Nullable CastOrigin origin, @Nullable Landings landings, float damageMultiplier) {
    public static final CastContext DIRECT = new CastContext(null, null, 1.0F);

    public CastContext withOrigin(CastOrigin newOrigin) {
        return new CastContext(newOrigin, landings, damageMultiplier);
    }

    // True if this ability should be cast natively (mana + cooldown), false for an Echo-like extra cast.
    public boolean claimNative(AbilityGlyph glyph) {
        return landings == null || landings.claimNative(glyph);
    }

    // Tracks which abilities one throw (or loop) has already cast natively. Server thread only.
    public static final class Landings {
        private final Set<AbilityGlyph> castNatively = new HashSet<>();
        private final boolean allExtra;

        private Landings(boolean allExtra) {
            this.allExtra = allExtra;
        }

        // A Bundled Spell throw: the first landing of each ability is native.
        public static Landings firstNative() {
            return new Landings(false);
        }

        // Creative Loop repeats: every cast is an extra cast.
        public static Landings allExtra() {
            return new Landings(true);
        }

        boolean claimNative(AbilityGlyph glyph) {
            return !allExtra && castNatively.add(glyph);
        }
    }
}
