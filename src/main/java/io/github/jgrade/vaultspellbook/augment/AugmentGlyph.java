package io.github.jgrade.vaultspellbook.augment;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphItem;
import io.github.jgrade.vaultspellbook.tier.VaultTier;

// A Vault-tier augment glyph. Like ability glyphs, it is only interpreted by the
// Vault Spellbook's own pipeline; Ars never applies it, so it has no Ars modifiers.
public class AugmentGlyph extends AbstractAugment {
    private final AugmentType type;

    public AugmentGlyph(AugmentType type, String displayName) {
        super(type.glyphId(), displayName);
        this.type = type;
    }

    public AugmentType getType() {
        return type;
    }

    @Override
    public SpellTier getTier() {
        return VaultTier.VAULT;
    }

    @Override
    public int getDefaultManaCost() {
        return 0;
    }

    @Override
    public boolean isRenderAsIcon() {
        return false; // draw the item model, which reuses an Ars augment icon by reference
    }

    @Override
    public Glyph getGlyph() {
        if (glyphItem == null) {
            glyphItem = new VaultGlyphItem(ArsNouveauAPI.getInstance().getSpellRegistryName(getId()), this, true);
        }
        return glyphItem;
    }
}
