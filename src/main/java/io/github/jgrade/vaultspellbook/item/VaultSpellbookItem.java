package io.github.jgrade.vaultspellbook.item;

import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.client.keybindings.ModKeyBindings;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import io.github.jgrade.vaultspellbook.client.VaultSpellBookScreen;
import io.github.jgrade.vaultspellbook.registry.ModCreativeTab;
import io.github.jgrade.vaultspellbook.tier.VaultTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

// The Vault Spellbook: an Ars spellbook of tier VAULT that holds only Vault glyphs. It keeps Ars's
// renderer (tier-3 geo, like the Creative book), HUD, radial menu and keybinds, and casts through
// VaultBookCaster.
public class VaultSpellbookItem extends SpellBook {
    public VaultSpellbookItem() {
        super(new Item.Properties().stacksTo(1).tab(ModCreativeTab.TAB), VaultTier.VAULT);
    }

    // Skips Ars's book-tier bump: SpellBook.use raises the player's Ars book tier to this book's tier,
    // and Ars grants 50 max mana per tier (ManaUtil), so tier 100 would add 5000 max mana.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return getSpellCaster(stack).castSpell(level, player, hand, new TranslatableComponent("ars_nouveau.invalid_spell"));
    }

    // Overrides the interface default that Wold's Vaults disables inside vaults; the book works everywhere.
    @Override
    public void onQuickCast(ItemStack stack, ServerPlayer player, InteractionHand hand, int slot) {
        new VaultBookCaster(stack).castSlot(player.level, player, hand, slot);
    }

    @NotNull
    @Override
    public ISpellCaster getSpellCaster(ItemStack stack) {
        return new VaultBookCaster(stack);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void onOpenBookMenuKeyPressed(ItemStack stack, Player player) {
        VaultSpellBookScreen.open(stack);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(new TranslatableComponent("tooltip.vaultspellbook.vault_spellbook.accepts").withStyle(ChatFormatting.GOLD));
        tooltip.add(new TranslatableComponent("tooltip.vaultspellbook.vault_spellbook.casts").withStyle(ChatFormatting.GRAY));
        tooltip.add(new TranslatableComponent("ars_nouveau.spell_book.select",
                KeyMapping.createNameSupplier(ModKeyBindings.OPEN_RADIAL_HUD.getName()).get()));
        tooltip.add(new TranslatableComponent("ars_nouveau.spell_book.craft",
                KeyMapping.createNameSupplier(ModKeyBindings.OPEN_BOOK.getName()).get()));
    }
}
