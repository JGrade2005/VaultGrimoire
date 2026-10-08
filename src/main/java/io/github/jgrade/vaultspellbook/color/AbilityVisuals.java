package io.github.jgrade.vaultspellbook.color;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.form.BundledSpellGlyph;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import iskallia.vault.init.ModEntities;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// Which glyphs can be coloured and what the colour tab previews for them. Abilities
// whose cast spawns an entity preview that entity (research/40-ability-visuals.md, checked against the
// VH/WV sources); the Arcane beam and Arcane Rail are drawn by BeamRenderer and the Bundled Spell
// as particles; those preview their icon or beam.
// Everything else only shows particles or effects VH draws itself and cannot be coloured. Totems are
// blocks, and Taunt/Heal/Nova clouds have no model, so they are not listed.
// This is the one hand-written table of specialization ids; tools/gen_glyphs.py warns about any id here
// that the pack no longer has.
public final class AbilityVisuals {
    // Specialization id -> entity its cast spawns (types are only read once registries are filled).
    private static final Map<String, Supplier<EntityType<?>>> PREVIEWS = Map.ofEntries(
            Map.entry("Dash_Warp", () -> ModEntities.WARP_ARROW),
            Map.entry("Javelin_Base", () -> ModEntities.THROWN_JAVELIN),
            Map.entry("Javelin_Piercing", () -> ModEntities.THROWN_JAVELIN),
            Map.entry("Javelin_Scatter", () -> ModEntities.THROWN_JAVELIN),
            Map.entry("Javelin_Sight", () -> ModEntities.THROWN_JAVELIN),
            Map.entry("Fireball_Base", () -> ModEntities.FIREBALL),
            Map.entry("Fireball_Volley", () -> ModEntities.FIREBALL),
            Map.entry("Fireball_Fireshot", () -> ModEntities.FIREBALL),
            Map.entry("Storm_Arrow_Base", () -> ModEntities.STORM_ARROW),
            Map.entry("Shard_Blizzard", () -> ModEntities.STORM_ARROW),
            Map.entry("Ice_Bolt_Base", () -> ModEntities.ICE_BOLT),
            Map.entry("Ice_Bolt_Blast", () -> ModEntities.ICE_BOLT),
            Map.entry("Chain_Lightning_Orbs", () -> ModEntities.LIGHTNING_ORB),
            Map.entry("Necromancy_Base", () -> ModEntities.NECROMANCY_SKELETON),
            Map.entry("Necromancy_Archer", () -> ModEntities.NECROMANCY_SKELETON),
            Map.entry("Necromancy_Golem", () -> ModEntities.NECROMANCY_GOLEM),
            Map.entry("Taunt_Decoy", () -> ModEntities.DECOY),
            Map.entry("Grenade_Base", () -> ModEntities.GRENADE),
            Map.entry("Grenade_Sticky", () -> ModEntities.GRENADE),
            Map.entry("Toxic_Grenade", () -> ModEntities.TOXIC_GRENADE));

    // Coloured entities without a useful 3D preview: Smite's bolt is a sky-high lightning strike and
    // Wall of Fangs only draws mid-attack, so these preview their icon.
    private static final Set<String> ICON_ONLY = Set.of("Fangs_Base", "Fangs_Maw", "Smite_Base", "Smite_Archon", "Smite_Blast_Wave");

    // Toggles whose ticks spawn entities long after the cast (Smite's bolts); ToggleColors colours those.
    private static final Set<String> TOGGLE_SPAWNS = Set.of("Smite_Base", "Smite_Archon", "Smite_Blast_Wave");

    // Abilities drawn as a beam by BeamRenderer when coloured.
    public enum BeamKind {
        // Arcane: a held beam, kept alive by the server while it fires. Default colour: VH's arcane particle.
        ARCANE(0xEB69FD),
        // Arcane Rail: a one-shot bolt that fades. Default colour: VH's arcane rail particle.
        RAIL(0x5FB216);

        public final int defaultRgb;

        BeamKind(int defaultRgb) {
            this.defaultRgb = defaultRgb;
        }
    }

    private AbilityVisuals() {
    }

    public static boolean isColorable(AbstractSpellPart part) {
        if (part instanceof BundledSpellGlyph) {
            return true;
        }
        return part instanceof AbilityGlyph glyph
                && (PREVIEWS.containsKey(glyph.getAdapter().getSpecializationId())
                || ICON_ONLY.contains(glyph.getAdapter().getSpecializationId()) || beamKind(part) != null);
    }

    // The beam this glyph draws when coloured, or null if it isn't a beam ability.
    @Nullable
    public static BeamKind beamKind(AbstractSpellPart part) {
        if (!(part instanceof AbilityGlyph glyph)) {
            return null;
        }
        if (glyph.has(AbilityTrait.BEAM)) {
            return BeamKind.ARCANE;
        }
        return glyph.has(AbilityTrait.BOLT) ? BeamKind.RAIL : null;
    }

    // Whether the toggle this glyph switches on keeps spawning entities while it runs.
    public static boolean spawnsWhileOn(AbilityGlyph glyph) {
        return TOGGLE_SPAWNS.contains(glyph.getAdapter().getSpecializationId());
    }

    // The entity to show in the colour tab's 3D preview, or null for a 2D icon preview.
    @Nullable
    public static EntityType<?> previewEntity(AbstractSpellPart part) {
        if (part instanceof AbilityGlyph glyph) {
            Supplier<EntityType<?>> type = PREVIEWS.get(glyph.getAdapter().getSpecializationId());
            return type == null ? null : type.get();
        }
        return null;
    }
}
