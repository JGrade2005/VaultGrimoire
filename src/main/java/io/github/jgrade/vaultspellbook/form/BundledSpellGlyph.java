package io.github.jgrade.vaultspellbook.form;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphItem;
import io.github.jgrade.vaultspellbook.tier.VaultTier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Set;

// Bundled Spell: a Vault form that throws a projectile carrying the rest of the
// spell, cast where it lands. Interpreted only by the Vault Spellbook's pipeline (VaultCaster), so
// the Ars cast hooks are no-ops.
public class BundledSpellGlyph extends AbstractCastMethod {
    public static final String ID = "vault_bundled_spell";

    public BundledSpellGlyph() {
        super(ID, "Bundled Spell");
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
    public Set<AbstractAugment> getCompatibleAugments() {
        return Set.of();
    }

    @Override
    public boolean isRenderAsIcon() {
        return false;
    }

    @Override
    public Glyph getGlyph() {
        if (glyphItem == null) {
            glyphItem = new VaultGlyphItem(ArsNouveauAPI.getInstance().getSpellRegistryName(getId()), this, true);
        }
        return glyphItem;
    }

    @Override
    public void onCast(@Nullable ItemStack stack, LivingEntity caster, Level level, SpellStats stats,
                       SpellContext context, SpellResolver resolver) {
    }

    @Override
    public void onCastOnBlock(UseOnContext context, SpellStats stats, SpellContext spellContext, SpellResolver resolver) {
    }

    @Override
    public void onCastOnBlock(BlockHitResult hit, LivingEntity caster, SpellStats stats, SpellContext context,
                              SpellResolver resolver) {
    }

    @Override
    public void onCastOnEntity(@Nullable ItemStack stack, LivingEntity caster, Entity target, InteractionHand hand,
                               SpellStats stats, SpellContext context, SpellResolver resolver) {
    }

    @SuppressWarnings("removal") // abstract in Ars 2.9.0, so it must be implemented
    @Override
    public boolean wouldCastSuccessfully(@Nullable ItemStack stack, LivingEntity caster, Level level, SpellStats stats,
                                         SpellResolver resolver) {
        return false;
    }

    @SuppressWarnings("removal") // abstract in Ars 2.9.0, so it must be implemented
    @Override
    public boolean wouldCastOnBlockSuccessfully(UseOnContext context, SpellStats stats, SpellResolver resolver) {
        return false;
    }

    @SuppressWarnings("removal") // abstract in Ars 2.9.0, so it must be implemented
    @Override
    public boolean wouldCastOnBlockSuccessfully(BlockHitResult hit, LivingEntity caster, SpellStats stats,
                                                SpellResolver resolver) {
        return false;
    }

    @SuppressWarnings("removal") // abstract in Ars 2.9.0, so it must be implemented
    @Override
    public boolean wouldCastOnEntitySuccessfully(@Nullable ItemStack stack, LivingEntity caster, Entity target,
                                                 InteractionHand hand, SpellStats stats, SpellResolver resolver) {
        return false;
    }
}
