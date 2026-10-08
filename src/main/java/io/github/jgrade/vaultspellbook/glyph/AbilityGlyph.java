package io.github.jgrade.vaultspellbook.glyph;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import io.github.jgrade.vaultspellbook.ability.AbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.tier.VaultTier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

// A Vault-tier form glyph that casts one VH ability specialization. Vault spells are cast by the
// Vault Spellbook's own pipeline (VaultCaster), never by Ars's SpellResolver, so every Ars cast hook
// here is a no-op: a Vault glyph smuggled into another caster does nothing.
public class AbilityGlyph extends AbstractCastMethod {
    private final AbilityAdapter adapter;
    private final boolean inSkillScreen;
    private final Set<AbilityTrait> traits;

    //   abilityName    the name the VH skill screen shows for this specialization
    //   inSkillScreen  false for specializations the pack keeps out of the skill screen; their
    //                  glyphs stay registered but are hidden everywhere and cannot be inscribed
    //   traits         what the ability is and does, for augment compatibility
    public AbilityGlyph(AbilityAdapter adapter, String abilityName, boolean inSkillScreen, Set<AbilityTrait> traits) {
        super(glyphId(adapter.getSpecializationId()), abilityName);
        this.adapter = adapter;
        this.inSkillScreen = inSkillScreen;
        this.traits = traits.isEmpty() ? EnumSet.noneOf(AbilityTrait.class) : EnumSet.copyOf(traits);
    }

    // Glyph id for a VH specialization id, e.g. "Dash_Base" -> "vault_dash_base".
    public static String glyphId(String specializationId) {
        return "vault_" + specializationId.toLowerCase(Locale.ROOT);
    }

    public AbilityAdapter getAdapter() {
        return adapter;
    }

    public boolean isInSkillScreen() {
        return inSkillScreen;
    }

    public boolean has(AbilityTrait trait) {
        return traits.contains(trait);
    }

    @Override
    public SpellTier getTier() {
        return VaultTier.VAULT;
    }

    @Override
    public int getDefaultManaCost() {
        return 0; // VH mana pays for the ability
    }

    @Override
    public Set<AbstractAugment> getCompatibleAugments() {
        return Set.of(); // Vault augments are checked by VaultSpellValidator, not Ars's augment lists
    }

    @Override
    public boolean isRenderAsIcon() {
        return false; // draw the glyph item model, which points at VH's own ability icon
    }

    @Override
    public Glyph getGlyph() {
        if (glyphItem == null) {
            glyphItem = new VaultGlyphItem(ArsNouveauAPI.getInstance().getSpellRegistryName(getId()), this, inSkillScreen);
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
