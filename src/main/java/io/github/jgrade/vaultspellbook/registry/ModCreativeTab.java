package io.github.jgrade.vaultspellbook.registry;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphs;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

// The "Vault Spellbook" creative tab holding everything this mod adds, in a fixed order: the book, the
// Bundled Spell, the augment glyphs, then the ability glyphs in skill-screen order (hidden ones are not
// listed). Glyph items are registered by Ars from a hash map, so registry order would scramble them;
// JEI also sorts by this list.
public final class ModCreativeTab {
    public static final CreativeModeTab TAB = new CreativeModeTab(VaultSpellbook.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.VAULT_SPELLBOOK.get());
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            items.add(new ItemStack(ModItems.VAULT_SPELLBOOK.get()));
            items.add(new ItemStack(VaultGlyphs.BUNDLED_SPELL.getGlyph()));
            VaultGlyphs.AUGMENTS.forEach(augment -> items.add(new ItemStack(augment.getGlyph())));
            VaultGlyphs.ALL.stream()
                    .filter(glyph -> glyph.isInSkillScreen())
                    .forEach(glyph -> items.add(new ItemStack(glyph.getGlyph())));
        }
    };

    private ModCreativeTab() {
    }
}
