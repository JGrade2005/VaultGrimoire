package io.github.jgrade.vaultspellbook.compat.jei;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphs;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// JEI integration (optional: JEI loads this class only when it is installed). Hides every ability glyph
// from the ingredient list: there is one per Vault Hunters specialization and they come from learning
// the ability, not from recipes. The augment and form glyphs stay listed, after Ars Nouveau's own
// glyphs (JEI sorts them by the Vault Spellbook creative tab's order, ModCreativeTab).
@JeiPlugin
public class VaultSpellbookJeiPlugin implements IModPlugin {
    @SuppressWarnings("removal") // Forge 40 marks the two-arg constructor; it is still the standard way on 1.18.2
    private static final ResourceLocation UID = new ResourceLocation(VaultSpellbook.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> abilityGlyphs = VaultGlyphs.ALL.stream().map(glyph -> new ItemStack(glyph.getGlyph())).toList();
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, abilityGlyphs);
    }
}
