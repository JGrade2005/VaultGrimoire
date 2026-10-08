package io.github.jgrade.vaultspellbook.glyph;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.common.items.Glyph;
import io.github.jgrade.vaultspellbook.registry.ModCreativeTab;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

// Glyph item for a Vault glyph (ability or augment). Ars creates glyph items with its own creative
// tab; this subclass shows it only in the Vault Spellbook tab, or nowhere for hidden
// ability glyphs. Using it does nothing: Vault glyphs are not unlocked through Ars's glyph knowledge.
public class VaultGlyphItem extends Glyph {
    private final boolean visible;

    public VaultGlyphItem(String registryName, AbstractSpellPart part, boolean visible) {
        super(registryName, part);
        this.visible = visible;
    }

    // The tab lists its own items (ModCreativeTab.fillItemList); naming it here as well keeps the item's answer the
    // same as getCreativeTabs for anything that asks, and the search tab finds visible glyphs.
    @Override
    protected boolean allowdedIn(CreativeModeTab tab) {
        return visible && (tab == ModCreativeTab.TAB || tab == CreativeModeTab.TAB_SEARCH);
    }

    @Override
    public Collection<CreativeModeTab> getCreativeTabs() {
        return visible ? List.of(ModCreativeTab.TAB) : List.of();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
