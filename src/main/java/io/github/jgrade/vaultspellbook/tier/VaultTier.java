package io.github.jgrade.vaultspellbook.tier;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.resources.ResourceLocation;

// The "Vault" glyph tier. Its value sits above Ars's Creative tier (99), so every normal Ars book
// rejects Vault glyphs through Ars's own GlyphMaxTierValidator.
public final class VaultTier {
    @SuppressWarnings("removal") // Forge 40 marks the two-arg constructor; it is still the standard way on 1.18.2
    public static final SpellTier VAULT = new SpellTier(new ResourceLocation(VaultSpellbook.MOD_ID, "vault"), 100);

    private VaultTier() {
    }

    // The single authoritative check for "is this a Vault glyph".
    public static boolean isVault(AbstractSpellPart part) {
        return part != null && part.getTier() == VAULT;
    }
}
