package io.github.jgrade.vaultspellbook.color;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

// Ability colours stored on the Vault Spellbook: per spell slot, per glyph position in
// that spell, with the glyph's id so a colour no longer applies once that cell holds another glyph.
// Layout: {vaultspellbook_colors: {"<slot>": {"<position>": {glyph, mode, rgb, strength}}}}.
public final class SpellColors {
    private static final String TAG = "vaultspellbook_colors";

    private SpellColors() {
    }

    // The colour of the glyph at this position, if one is set and that glyph is still there.
    @Nullable
    public static AbilityColor get(ItemStack book, int slot, int position, AbstractSpellPart part) {
        CompoundTag colors = book.getTagElement(TAG);
        if (colors == null || part == null) {
            return null;
        }
        CompoundTag entry = colors.getCompound(Integer.toString(slot)).getCompound(Integer.toString(position));
        return part.getId().equals(entry.getString("glyph")) ? AbilityColor.load(entry) : null;
    }

    // Colours of a whole recipe, by position (null where none is set).
    public static AbilityColor[] forRecipe(ItemStack book, int slot, List<AbstractSpellPart> recipe) {
        AbilityColor[] result = new AbilityColor[recipe.size()];
        for (int position = 0; position < recipe.size(); position++) {
            result[position] = get(book, slot, position, recipe.get(position));
        }
        return result;
    }

    // Sets, or with null clears, the colour of the glyph at this position.
    public static void set(ItemStack book, int slot, int position, String glyphId, @Nullable AbilityColor color) {
        CompoundTag colors = book.getOrCreateTagElement(TAG);
        String slotKey = Integer.toString(slot);
        CompoundTag slotTag = colors.getCompound(slotKey);
        if (color == null) {
            slotTag.remove(Integer.toString(position));
        } else {
            CompoundTag entry = color.save();
            entry.putString("glyph", glyphId);
            slotTag.put(Integer.toString(position), entry);
        }
        if (slotTag.isEmpty()) {
            colors.remove(slotKey);
        } else {
            colors.put(slotKey, slotTag);
        }
    }
}
