package io.github.jgrade.vaultspellbook.item;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import io.github.jgrade.vaultspellbook.cast.VaultCaster;
import io.github.jgrade.vaultspellbook.cast.VaultSpellValidator;
import io.github.jgrade.vaultspellbook.color.SpellColors;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// The Vault Spellbook's spell storage. Server-side gate: Ars stores whatever spell a client sends
// (PacketUpdateCaster), so spells that break the Vault rules are refused here. Casting goes to
// our own pipeline instead of Ars's SpellResolver.
public class VaultBookCaster extends SpellBook.BookCaster {
    private final ItemStack book;
    // The slot being cast (quick cast picks one); the current slot otherwise.
    private int castingSlot = -1;

    public VaultBookCaster(ItemStack stack) {
        super(stack);
        this.book = stack;
    }

    // Casts the spell in a given slot (quick cast), with that slot's ability colours.
    public InteractionResultHolder<ItemStack> castSlot(Level level, Player player, InteractionHand hand, int slot) {
        castingSlot = slot;
        try {
            return castSpell(level, player, hand, null, getSpell(slot));
        } finally {
            castingSlot = -1;
        }
    }

    @Override
    public void setSpell(Spell spell, int slot) {
        if (VaultSpellValidator.isStorable(spell)) {
            super.setSpell(spell, slot);
        }
    }

    @Override
    public void setSpell(Spell spell) {
        if (VaultSpellValidator.isStorable(spell)) {
            super.setSpell(spell);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> castSpell(Level level, Player player, InteractionHand hand,
                                                        @Nullable TranslatableComponent invalidMessage, @Nonnull Spell spell) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        int slot = castingSlot >= 0 ? castingSlot : getCurrentSlot();
        VaultCaster.cast(serverPlayer, spell, SpellColors.forRecipe(book, slot, spell.recipe), slot);
        return InteractionResultHolder.success(stack);
    }
}
