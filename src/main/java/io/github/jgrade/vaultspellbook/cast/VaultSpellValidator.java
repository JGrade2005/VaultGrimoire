package io.github.jgrade.vaultspellbook.cast;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;

import java.util.List;

// Rules for Vault spells, used by the book GUI and by the server when a spell is stored (SpellPlan):
// only Vault ability glyphs from the skill screen and Vault augments, each augment after an ability
// it can modify and within its limits. Replaces Ars's standard crafting validator, whose
// one-form-per-spell rule would forbid multi-ability spells.
public class VaultSpellValidator implements ISpellValidator {
    @Override
    public List<SpellValidationError> validate(List<AbstractSpellPart> recipe) {
        return SpellPlan.parse(recipe).errors();
    }

    // Server-side check for spells sent by a client; an empty spell (clearing a slot) is allowed.
    public static boolean isStorable(Spell spell) {
        return spell != null && SpellPlan.parse(spell.recipe).isValid();
    }
}
