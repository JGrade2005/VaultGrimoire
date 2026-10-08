package io.github.jgrade.vaultspellbook.glyph;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.ability.KeyPressAbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.special.DrivenHoldAbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.special.VeinMinerAbilityAdapter;
import io.github.jgrade.vaultspellbook.augment.AugmentGlyph;
import io.github.jgrade.vaultspellbook.augment.AugmentType;
import io.github.jgrade.vaultspellbook.form.BundledSpellGlyph;
import iskallia.vault.skill.ability.effect.spi.core.InstantAbility;
import iskallia.vault.skill.ability.effect.spi.core.ToggleAbility;

import java.util.EnumSet;
import java.util.List;

import static io.github.jgrade.vaultspellbook.ability.AbilityTrait.AREA;
import static io.github.jgrade.vaultspellbook.ability.AbilityTrait.BEAM;
import static io.github.jgrade.vaultspellbook.ability.AbilityTrait.BOLT;
import static io.github.jgrade.vaultspellbook.ability.AbilityTrait.MANA;
import static io.github.jgrade.vaultspellbook.ability.AbilityTrait.PROJECTILE;

// The single list of Vault glyphs: one ability glyph per VH/WV specialization in the pack
// and the augment and form glyphs. The ability entries are generated from the pack's ability config by
// tools/gen_glyphs.py (with tools/ability_types.csv), together with lang keys and item models; see the
// README to regenerate.
public final class VaultGlyphs {
    // In the pack's skill screen (abilities_gui_styles.json), so learnable and shown.
    private static final boolean SHOWN = true;
    // Not in the skill screen: registered so existing items/spells stay valid, but hidden everywhere.
    private static final boolean HIDDEN = false;

    public static final List<AbilityGlyph> ALL = List.of(
            // BEGIN GENERATED (tools/gen_glyphs.py)
            toggle("UltimateShield_Base", "Ultimate Shield", SHOWN, MANA),
            instant("Fangs_Base", "Wall of Fangs", SHOWN, AREA, MANA),
            instant("Fangs_Maw", "Hungry Maw", SHOWN, AREA, MANA),
            instant("Expunge_Base", "Diffuse", SHOWN, AREA, MANA),
            instant("Concentrate_Base", "Concentrate", SHOWN, AREA, MANA),
            instant("Colossus_Base", "Colossus", SHOWN, MANA),
            instant("Sneaky_Getaway", "Sneaky Getaway", SHOWN, MANA),
            instant("Heal_Base", "Heal", SHOWN, MANA),
            instant("Heal_Group", "Group Heal", SHOWN, AREA, MANA),
            instant("Heal_Cleanse", "Cleanse", SHOWN, MANA),
            instant("Dash_Base", "Dash", SHOWN, MANA),
            instant("Dash_Damage", "Bullet", SHOWN, AREA, MANA),
            instant("Dash_Warp", "Warp", SHOWN, MANA),
            instant("Nova_Base", "Nova", SHOWN, AREA, MANA),
            instant("Nova_Slow", "Frost Nova", SHOWN, AREA, MANA),
            instant("Nova_Dot", "Poison Nova", SHOWN, AREA, MANA),
            instant("Execute_Base", "Execute", HIDDEN),
            instant("Ghost_Walk_Base", "Ghost Walk", SHOWN, MANA),
            instant("Ghost_Walk_Spirit", "Spirit Walk", SHOWN, MANA),
            instant("Mega_Jump_Base", "Mega Jump", SHOWN, AREA, MANA),
            instant("Mega_Jump_Break_Up", "Mega Jump: Drill", HIDDEN, AREA, MANA),
            instant("Mega_Jump_Break_Down", "Mega Dig", SHOWN, AREA, MANA),
            hold("Levitate", "Levitate", SHOWN, MANA),
            toggle("Rampage_Base", "Rampage", SHOWN, MANA),
            toggle("Rampage_Bloodlust", "Bloodlust", SHOWN, MANA),
            toggle("Rampage_Berserker", "Berserker", SHOWN, MANA),
            toggle("Rampage_Instinct", "Instinct", SHOWN, MANA),
            toggle("Rampage_Leech", "Vampiric", HIDDEN, MANA),
            toggle("Rampage_Chain", "Chaining", HIDDEN, MANA),
            toggle("Smite_Base", "Smite", SHOWN, AREA, MANA),
            toggle("Smite_Archon", "Archon", SHOWN, AREA, MANA),
            toggle("Smite_Blast_Wave", "Blast Wave", SHOWN, AREA, MANA),
            toggle("Smite_Thunderstorm", "Smite: Thunderstorm", HIDDEN, AREA, MANA),
            instant("Summon_Eternal_Base", "Summon Eternal", HIDDEN, MANA),
            toggle("Empower_Base", "Empower", SHOWN, AREA, MANA),
            toggle("Empower_Ice_Armor", "Ice Armor", SHOWN, MANA),
            toggle("Empower_Slowness_Aura", "Entropic Bind", SHOWN, AREA, MANA),
            veinMiner("Vein_Miner_Base", "Vein Miner", SHOWN, AREA),
            veinMiner("Vein_Miner_Fortune", "Vein Miner: Fortune", HIDDEN, AREA),
            veinMiner("Vein_Miner_Durability", "Finesse Miner", SHOWN, AREA),
            veinMiner("Vein_Miner_Void", "Void Miner", HIDDEN, AREA),
            veinMiner("Vein_Miner_Chain", "Chain Miner", SHOWN, AREA),
            instant("Hunter_Base", "Hunter", SHOWN, MANA),
            instant("Javelin_Sight", "Hunter: Javelin", SHOWN, AREA, PROJECTILE, MANA),
            toggle("Farmer_Base", "Farmer", HIDDEN, AREA, MANA),
            toggle("Farmer_Melon", "Farmer: Cultivator", HIDDEN, AREA, MANA),
            toggle("Farmer_Cactus", "Farmer: Gardener", HIDDEN, AREA, MANA),
            toggle("Farmer_Animal", "Farmer: Rancher", HIDDEN, AREA, MANA),
            instant("Mana_Shield_Base", "Mana Shield", SHOWN, MANA),
            instant("Mana_Barrier", "Mana Barrier", SHOWN, AREA, MANA),
            instant("Shield_Bash", "Shield Bash", SHOWN, AREA, MANA),
            instant("Shield_Bash_Earthshatter", "Bulwark", SHOWN, AREA, MANA),
            instant("Shield_Bash_Battering_Ram", "Battering Ram", SHOWN, AREA, MANA),
            instant("Mana_Shield_Retribution", "Retribution", HIDDEN, AREA, MANA),
            instant("Mana_Shield_Implode", "Implode", SHOWN, AREA, MANA),
            instant("Implode_Life_Tap", "Life Tap", SHOWN, AREA),
            instant("Taunt_Base", "Taunt", SHOWN, AREA, MANA),
            instant("Taunt_Repel", "Fear", SHOWN, AREA, MANA),
            instant("Taunt_Decoy", "Decoy", SHOWN, AREA, MANA),
            instant("Taunt_Charm", "Charm", HIDDEN, AREA, MANA),
            instant("Stonefall_Base", "Stonefall", SHOWN, AREA, MANA),
            instant("Stonefall_Snow", "Hero's Landing", SHOWN, AREA, MANA),
            instant("Stonefall_Cold", "Coldsnap", SHOWN, AREA, MANA),
            instant("Totem_Base", "Rejuvenation Totem", SHOWN, AREA, MANA),
            instant("Totem_Player_Damage", "Wrath Totem", SHOWN, AREA, MANA),
            instant("Totem_Mana_Regen", "Spirit Totem", SHOWN, AREA, MANA),
            instant("Totem_Mob_Damage", "Hatred Totem", SHOWN, AREA, MANA),
            instant("Javelin_Base", "Javelin", SHOWN, PROJECTILE, MANA),
            instant("Javelin_Piercing", "Piercing Javelin", SHOWN, PROJECTILE, MANA),
            instant("Javelin_Scatter", "Scatter Javelin", SHOWN, PROJECTILE, MANA),
            toggle("Shell_Base", "Shell", SHOWN, MANA),
            toggle("Shell_Porcupine", "Porcupine", SHOWN, MANA),
            toggle("Shell_Quill", "Quill", SHOWN, MANA),
            instant("Fireball_Base", "Fireball", SHOWN, PROJECTILE, MANA),
            instant("Fireball_Volley", "Fire Volley", SHOWN, PROJECTILE, MANA),
            instant("Fireball_Fireshot", "Fireshot", SHOWN, PROJECTILE, MANA),
            instant("Storm_Arrow_Base", "Storm Arrow", SHOWN, AREA, PROJECTILE, MANA),
            instant("Storm_Arrow_Blizzard", "Blizzard Arrow", HIDDEN, AREA, MANA),
            instant("Battle_Cry_Base", "Battle Cry", SHOWN, AREA, MANA),
            instant("Battle_Cry_Spectral_Strike", "Spectral Cry", SHOWN, AREA, MANA),
            instant("Battle_Cry_Lucky_Strike", "Lucky Cry", SHOWN, AREA, MANA),
            instant("Ice_Bolt_Base", "Ice Bolt", SHOWN, PROJECTILE, MANA),
            instant("Ice_Bolt_Blast", "Glacial Blast", SHOWN, AREA, PROJECTILE, MANA),
            instant("Shard_Blizzard", "Shard Blizzard", SHOWN, AREA, PROJECTILE, MANA),
            hold("Arcane_Base", "Arcane", SHOWN, BEAM, MANA),
            instant("Arcane_Rail", "Rail", SHOWN, BOLT, MANA),
            instant("Arcane_Prism", "Prism", SHOWN, MANA),
            instant("Earthquake_Base", "Earthquake", SHOWN, AREA, MANA),
            instant("Earthquake_Singularity", "Singularity", SHOWN, AREA, MANA),
            instant("Earthquake_Tremor", "Tremor", SHOWN, AREA, MANA),
            instant("Earthquake_Landmine", "Landmine", HIDDEN, AREA, MANA),
            instant("Chain_Lightning_Base", "Lightning Strike", SHOWN, MANA),
            instant("Chain_Lightning_Orbs", "Ball Lightning", SHOWN, AREA, PROJECTILE, MANA),
            instant("Chain_Lightning_Charged_Bolts", "Charged Bolts", SHOWN, MANA),
            instant("Necromancy_Base", "Necromancy", SHOWN, MANA),
            instant("Necromancy_Archer", "Necromancy: Archer", SHOWN, MANA),
            instant("Necromancy_Golem", "Necromancy: Golem", SHOWN, MANA),
            instant("Grenade_Base", "Chaos Cube", SHOWN, AREA, PROJECTILE, MANA),
            instant("Grenade_Sticky", "Sticky Cube", SHOWN, AREA, PROJECTILE, MANA),
            instant("Toxic_Grenade", "Toxic Vial", SHOWN, AREA, PROJECTILE, MANA)
            // END GENERATED
    );

    public static final List<AugmentGlyph> AUGMENTS = List.of(
            new AugmentGlyph(AugmentType.AMPLIFY_AREA, "Amplify Area"),
            new AugmentGlyph(AugmentType.QUICKEN, "Quicken"),
            new AugmentGlyph(AugmentType.DISCOUNT, "Discount"),
            new AugmentGlyph(AugmentType.ECHO, "Echo"),
            new AugmentGlyph(AugmentType.STRENGTHEN, "Strengthen"),
            new AugmentGlyph(AugmentType.ACCELERATE, "Accelerate"),
            new AugmentGlyph(AugmentType.HOMING, "Homing"),
            new AugmentGlyph(AugmentType.ORBIT, "Orbit"),
            new AugmentGlyph(AugmentType.DELAY, "Delay"),
            new AugmentGlyph(AugmentType.LOOP, "Loop"),
            new AugmentGlyph(AugmentType.SIZE_UP, "Size Up"),
            new AugmentGlyph(AugmentType.SIZE_DOWN, "Size Down"),
            new AugmentGlyph(AugmentType.ETHEREAL, "Ethereal"),
            new AugmentGlyph(AugmentType.SHOTGUN, "Shotgun"),
            new AugmentGlyph(AugmentType.LOW_GRAVITY, "Low Gravity"),
            new AugmentGlyph(AugmentType.CREATIVE_LOOP, "Creative Loop"),
            new AugmentGlyph(AugmentType.CHARGE, "Charge")
    );

    // Vault form glyphs.
    public static final BundledSpellGlyph BUNDLED_SPELL = new BundledSpellGlyph();

    private VaultGlyphs() {
    }

    // Must run during mod construction: Ars turns registered glyphs into items in its Item registry event.
    public static void registerAll() {
        ArsNouveauAPI api = ArsNouveauAPI.getInstance();
        ALL.forEach(api::registerSpell);
        AUGMENTS.forEach(api::registerSpell);
        api.registerSpell(BUNDLED_SPELL);
    }

    private static AbilityGlyph instant(String specializationId, String name, boolean inSkillScreen,
                                        AbilityTrait... traits) {
        KeyPressAbilityAdapter adapter = new KeyPressAbilityAdapter(specializationId, InstantAbility.class);
        return new AbilityGlyph(adapter, name, inSkillScreen, traits(AbilityTrait.INSTANT, traits));
    }

    private static AbilityGlyph toggle(String specializationId, String name, boolean inSkillScreen,
                                       AbilityTrait... traits) {
        KeyPressAbilityAdapter adapter = new KeyPressAbilityAdapter(specializationId, ToggleAbility.class);
        return new AbilityGlyph(adapter, name, inSkillScreen, traits(AbilityTrait.TOGGLE, traits));
    }

    // Arcane/Levitate glyphs switch the hold on/off; it keeps going whatever ability is selected.
    private static AbilityGlyph hold(String specializationId, String name, boolean inSkillScreen,
                                     AbilityTrait... traits) {
        DrivenHoldAbilityAdapter adapter = new DrivenHoldAbilityAdapter(specializationId);
        return new AbilityGlyph(adapter, name, inSkillScreen, traits(AbilityTrait.HOLD, traits));
    }

    // Vein Miner glyphs are an on/off switch that keeps working whatever ability is selected.
    private static AbilityGlyph veinMiner(String specializationId, String name, boolean inSkillScreen,
                                          AbilityTrait... traits) {
        VeinMinerAbilityAdapter adapter = new VeinMinerAbilityAdapter(specializationId);
        return new AbilityGlyph(adapter, name, inSkillScreen, traits(AbilityTrait.HOLD, traits));
    }

    private static EnumSet<AbilityTrait> traits(AbilityTrait kind, AbilityTrait... more) {
        EnumSet<AbilityTrait> set = EnumSet.of(kind);
        set.addAll(List.of(more));
        return set;
    }
}
