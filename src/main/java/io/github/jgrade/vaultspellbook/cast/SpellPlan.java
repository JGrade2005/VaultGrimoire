package io.github.jgrade.vaultspellbook.cast;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.BaseSpellValidationError;
import io.github.jgrade.vaultspellbook.augment.AugmentGlyph;
import io.github.jgrade.vaultspellbook.augment.AugmentType;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.form.BundledSpellGlyph;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import io.github.jgrade.vaultspellbook.tier.VaultTier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// A Vault spell read as a timeline: each ability glyph with the augments
// that follow it, pauses where Delay glyphs sit, and Bundled Spells whose payload is the rest of the
// spell. The same parse validates spells in the book GUI, when a spell is stored on the server, and
// before it is cast.
public final class SpellPlan {
    public static final String VAULT_ONLY = "vaultspellbook_vault_only";
    public static final String HIDDEN_GLYPH = "vaultspellbook_hidden_glyph";
    public static final String NO_ABILITY_BEFORE = "vaultspellbook_no_ability_before";
    public static final String INCOMPATIBLE = "vaultspellbook_incompatible";
    public static final String TOO_MANY = "vaultspellbook_too_many";
    public static final String ORBIT_AND_HOMING = "vaultspellbook_orbit_and_homing";

    private final List<Step> steps;
    private final List<SpellValidationError> errors;
    private final boolean creativeLoop;

    private SpellPlan(List<Step> steps, List<SpellValidationError> errors, boolean creativeLoop) {
        this.steps = steps;
        this.errors = errors;
        this.creativeLoop = creativeLoop;
    }

    public List<Step> steps() {
        return steps;
    }

    public List<SpellValidationError> errors() {
        return errors;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    // The spell contains Creative Loop: casting it toggles continuous re-firing (creative mode only).
    public boolean isCreativeLoop() {
        return creativeLoop;
    }

    // Parses a recipe; null entries (empty cells in the Ars crafting screen) are skipped.
    public static SpellPlan parse(List<AbstractSpellPart> recipe) {
        return parse(recipe, new AbilityColor[0]);
    }

    // Parses a recipe with the book's ability colours, by glyph position.
    public static SpellPlan parse(List<AbstractSpellPart> recipe, AbilityColor[] colors) {
        Parser parser = new Parser(colors);
        for (int position = 0; position < recipe.size(); position++) {
            AbstractSpellPart part = recipe.get(position);
            if (part != null) {
                parser.accept(position, part);
            }
        }
        return new SpellPlan(Collections.unmodifiableList(parser.root), Collections.unmodifiableList(parser.errors),
                parser.creativeLoops > 0);
    }

    // Walks the recipe once; steps go into the current sink, which a Bundled Spell redirects into its payload.
    private static final class Parser {
        final AbilityColor[] colors;
        final List<Step> root = new ArrayList<>();
        final List<SpellValidationError> errors = new ArrayList<>();
        List<Step> sink = root;
        // The glyph the next augment modifies: a Cast or a Bundle.
        Step target;
        int delays;
        int echoes;
        int creativeLoops;

        Parser(AbilityColor[] colors) {
            this.colors = colors;
        }

        @Nullable
        AbilityColor colorAt(int position) {
            return position < colors.length ? colors[position] : null;
        }

        void accept(int position, AbstractSpellPart part) {
            if (part instanceof AbilityGlyph ability && VaultTier.isVault(part) && ability.isInSkillScreen()) {
                Cast cast = new Cast(ability, new EnumMap<>(AugmentType.class), position, colorAt(position));
                sink.add(cast);
                target = cast;
            } else if (part instanceof BundledSpellGlyph) {
                Bundle bundle = new Bundle(new EnumMap<>(AugmentType.class), new ArrayList<>(), position, colorAt(position));
                sink.add(bundle);
                sink = bundle.payload();
                target = bundle;
            } else if (part instanceof AugmentGlyph augment && VaultTier.isVault(part)) {
                acceptAugment(position, part, augment.getType());
            } else if (part instanceof AbilityGlyph && VaultTier.isVault(part)) {
                errors.add(error(position, part, HIDDEN_GLYPH)); // a Vault ability the pack's skill screen doesn't show
            } else {
                errors.add(error(position, part, VAULT_ONLY));
            }
        }

        void acceptAugment(int position, AbstractSpellPart part, AugmentType type) {
            if (type == AugmentType.CREATIVE_LOOP) {
                if (++creativeLoops > VaultSpellbookConfig.maxCount(type)) {
                    errors.add(error(position, part, TOO_MANY));
                }
                return;
            }
            if (target == null) {
                errors.add(error(position, part, NO_ABILITY_BEFORE));
                return;
            }
            if (type == AugmentType.DELAY) {
                if (++delays > VaultSpellbookConfig.maxCount(type)) {
                    errors.add(error(position, part, TOO_MANY));
                } else {
                    addPause(VaultSpellbookConfig.secondsToTicks(VaultSpellbookConfig.DELAY_SECONDS.get()));
                }
                return;
            }
            Map<AugmentType, Integer> augments;
            if (target instanceof Cast cast) {
                if (!type.canModify(cast.ability())) {
                    errors.add(error(position, part, INCOMPATIBLE));
                    return;
                }
                augments = cast.augments();
            } else {
                if (!type.canModifyBundle()) {
                    errors.add(error(position, part, INCOMPATIBLE));
                    return;
                }
                augments = ((Bundle) target).augments();
            }
            int count = augments.merge(type, 1, Integer::sum);
            boolean tooMany = type == AugmentType.ECHO
                    ? ++echoes > VaultSpellbookConfig.maxCount(type)
                    : count > VaultSpellbookConfig.maxCount(type);
            if (tooMany) {
                errors.add(error(position, part, TOO_MANY));
            } else if (augments.containsKey(AugmentType.ORBIT) && augments.containsKey(AugmentType.HOMING)) {
                errors.add(error(position, part, ORBIT_AND_HOMING));
            }
        }

        // Consecutive Delay glyphs add up to one longer pause.
        void addPause(int ticks) {
            if (!sink.isEmpty() && sink.get(sink.size() - 1) instanceof Pause pause) {
                sink.set(sink.size() - 1, new Pause(pause.ticks() + ticks));
            } else {
                sink.add(new Pause(ticks));
            }
        }

    }

    // Whether a payload casts anything. Not a validation rule: the Ars screen greys out glyphs whose
    // addition fails validation, and a Bundled Spell is always empty right after it is added.
    public static boolean containsCast(List<Step> steps) {
        for (Step step : steps) {
            if (step instanceof Cast || step instanceof Bundle bundle && containsCast(bundle.payload())) {
                return true;
            }
        }
        return false;
    }

    private static SpellValidationError error(int position, AbstractSpellPart part, String code) {
        return new BaseSpellValidationError(position, part, code);
    }

    public sealed interface Step permits Cast, Pause, Bundle {
    }

    // Casts one ability glyph with the augments that follow it, in its colour if the book sets one.
    public record Cast(AbilityGlyph ability, Map<AugmentType, Integer> augments, int position,
                       @Nullable AbilityColor color) implements Step {
        public int count(AugmentType type) {
            return augments.getOrDefault(type, 0);
        }
    }

    // Waits before the rest of the timeline (Delay glyphs).
    public record Pause(int ticks) implements Step {
    }

    // Throws a Bundled Spell projectile; its payload (the rest of the spell) runs where it lands.
    public record Bundle(Map<AugmentType, Integer> augments, List<Step> payload, int position,
                         @Nullable AbilityColor color) implements Step {
        public int count(AugmentType type) {
            return augments.getOrDefault(type, 0);
        }
    }
}
