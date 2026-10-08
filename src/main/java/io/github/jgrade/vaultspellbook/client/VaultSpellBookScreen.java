package io.github.jgrade.vaultspellbook.client;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.api.util.CasterUtil;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.cast.VaultSpellValidator;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphs;
import io.github.jgrade.vaultspellbook.tier.VaultTier;
import iskallia.vault.client.data.ClientAbilityData;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// Ars's spell crafting screen, limited to the Vault glyphs whose VH specialization the player has
// learned and selected plus the augment glyphs, and validated by VaultSpellValidator instead of Ars's
// one-form-per-spell rules.
public class VaultSpellBookScreen extends GuiSpellBook {
    // GuiSpellBook.spellValidator is package-private in Ars 2.9.0 (client/gui/book/GuiSpellBook.java:67).
    private static final Field VALIDATOR_FIELD = findValidatorField();
    private static final Field SLOT_FIELD = findSlotField();

    public VaultSpellBookScreen(ItemStack bookStack, List<AbstractSpellPart> glyphs) {
        super(bookStack, VaultTier.VAULT.value, glyphs);
        replaceValidator(new VaultSpellValidator());
    }

    public static void open(ItemStack bookStack) {
        AbilityTree tree = ClientAbilityData.getTree();
        List<AbstractSpellPart> learned = new ArrayList<>();
        for (AbilityGlyph glyph : VaultGlyphs.ALL) {
            if (glyph.isInSkillScreen() && glyph.getAdapter().isUnlocked(tree)) {
                learned.add(glyph);
            }
        }
        learned.add(VaultGlyphs.BUNDLED_SPELL);
        learned.addAll(VaultGlyphs.AUGMENTS);
        Minecraft.getInstance().setScreen(new VaultSpellBookScreen(bookStack, learned));
    }

    // Ars's colour bookmark opens the ability colour tab instead of Ars's spell colour screen.
    @Override
    public void onColorClick(Button button) {
        Minecraft.getInstance().setScreen(new VaultColorScreen(bookStack, selectedSlot()));
    }

    // The spell tab picked in this screen (GuiSpellBook.selected_cast_slot is private in Ars 2.9.0).
    private int selectedSlot() {
        if (SLOT_FIELD != null) {
            try {
                return Math.max(1, SLOT_FIELD.getInt(this));
            } catch (IllegalAccessException e) {
                VaultSpellbook.LOGGER.warn("Could not read the selected spell slot", e);
            }
        }
        return Math.max(1, CasterUtil.getCaster(bookStack).getCurrentSlot());
    }

    private void replaceValidator(ISpellValidator validator) {
        if (VALIDATOR_FIELD == null) {
            return;
        }
        try {
            VALIDATOR_FIELD.set(this, validator);
        } catch (IllegalAccessException e) {
            VaultSpellbook.LOGGER.error("Could not set the Vault spell validator; multi-glyph Vault spells will show as invalid", e);
        }
    }

    private static Field findSlotField() {
        try {
            Field field = GuiSpellBook.class.getDeclaredField("selected_cast_slot");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            VaultSpellbook.LOGGER.warn("Ars Nouveau's GuiSpellBook has no 'selected_cast_slot' field; the colour tab opens on the "
                    + "current slot", e);
            return null;
        }
    }

    private static Field findValidatorField() {
        try {
            Field field = GuiSpellBook.class.getDeclaredField("spellValidator");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            VaultSpellbook.LOGGER.error("Ars Nouveau's GuiSpellBook has no 'spellValidator' field; this Ars version is not supported", e);
            return null;
        }
    }
}
