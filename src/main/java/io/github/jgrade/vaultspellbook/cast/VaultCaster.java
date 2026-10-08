package io.github.jgrade.vaultspellbook.cast;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import io.github.jgrade.vaultspellbook.ability.AbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.AbilityTrait;
import io.github.jgrade.vaultspellbook.ability.CastResult;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import io.github.jgrade.vaultspellbook.ability.special.DrivenHoldSessions;
import io.github.jgrade.vaultspellbook.augment.AugmentType;
import io.github.jgrade.vaultspellbook.augment.CastModifiers;
import io.github.jgrade.vaultspellbook.augment.CastScope;
import io.github.jgrade.vaultspellbook.augment.ManaRefunds;
import io.github.jgrade.vaultspellbook.augment.RailBolts;
import io.github.jgrade.vaultspellbook.augment.ToggleDiscounts;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.color.BeamSync;
import io.github.jgrade.vaultspellbook.color.ToggleColors;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.form.BundleLauncher;
import io.github.jgrade.vaultspellbook.form.ShotgunPattern;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import io.github.jgrade.vaultspellbook.network.CooldownCountdownPacket;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

// The Vault Spellbook's cast pipeline: runs the spell's timeline, casting
// each ability glyph with its augments in order, pausing at Delay glyphs and throwing Bundled Spells
// whose payload runs where they land. A glyph that cannot cast is skipped with an action-bar message
// and the rest still cast; a glyph on cooldown gets a live countdown.
public final class VaultCaster {
    private VaultCaster() {
    }

    // Casts the spell in a book slot; colors are the book's ability colours by glyph position. If that
    // slot has loops running, casting it switches them off instead.
    public static void cast(ServerPlayer player, Spell spell, AbilityColor[] colors, int slot) {
        if (AbilityLoops.isRunning(player.getUUID(), slot)) {
            AbilityLoops.stop(player.getUUID(), slot);
            showMessage(player, new TranslatableComponent("vaultspellbook.cast.loop_off"));
            return;
        }
        SpellPlan plan = spell == null ? null : SpellPlan.parse(spell.recipe, colors);
        if (plan == null || plan.steps().isEmpty() || !plan.isValid()) {
            showMessage(player, new TranslatableComponent("vaultspellbook.cast.invalid_spell"));
            return;
        }
        if (!worksHere(player)) {
            showMessage(player, new TranslatableComponent("vaultspellbook.cast.vaults_only"));
            return;
        }
        if (plan.isCreativeLoop()) {
            if (CreativeLoops.isOn(player)) {
                CreativeLoops.stop(player);
                showMessage(player, new TranslatableComponent("vaultspellbook.cast.creative_loop_off"));
                return;
            }
            if (!player.isCreative()) {
                showMessage(player, new TranslatableComponent("vaultspellbook.cast.creative_loop_creative_only"));
                return;
            }
            CreativeLoops.start(player, plan);
            showMessage(player, new TranslatableComponent("vaultspellbook.cast.creative_loop_on"));
        }
        List<SpellPlan.Cast> looping = plan.steps().stream()
                .filter(step -> step instanceof SpellPlan.Cast cast && cast.count(AugmentType.LOOP) > 0)
                .map(SpellPlan.Cast.class::cast)
                .toList();
        if (!looping.isEmpty() && !plan.isCreativeLoop()) {
            int wait = plan.steps().get(plan.steps().size() - 1) instanceof SpellPlan.Pause pause ? pause.ticks() : 0;
            AbilityLoops.start(player, slot, looping, wait); // Delay glyphs at the end time the repeats
            showMessage(player, new TranslatableComponent("vaultspellbook.cast.loop_on"));
        }
        run(player, plan.steps(), 0, CastContext.DIRECT);
    }

    // How long a glyph's Charge winds it up: the configured time per Charge glyph.
    private static int chargeTicks(SpellPlan.Cast cast) {
        return VaultSpellbookConfig.secondsToTicks(VaultSpellbookConfig.CHARGE_SECONDS.get() * cast.count(AugmentType.CHARGE));
    }

    // One loop repeat of a looping glyph: a normal cast, wound up first if it has Charge.
    static LoopCast castLooped(ServerPlayer player, SpellPlan.Cast cast) {
        if (cast.count(AugmentType.CHARGE) > 0) {
            int ticks = chargeTicks(cast);
            if (startCharge(player, cast, ticks)) {
                SpellScheduler.schedule(player, ticks, later -> castWithAugments(later, cast, CastContext.DIRECT, true));
                return new LoopCast(CastResult.SUCCESS, ticks + 2);
            }
        }
        return new LoopCast(castWithAugments(player, cast, CastContext.DIRECT, false), 0);
    }

    // What a loop repeat did, and how long the loop should wait before checking again (a wind-up).
    record LoopCast(CastResult result, int busyTicks) {
    }

    // One Creative Loop repeat: every cast is an extra cast (no cooldown).
    static void runLoopIteration(ServerPlayer player, SpellPlan plan) {
        run(player, plan.steps(), 0, new CastContext(null, CastContext.Landings.allExtra(), 1.0F));
    }

    // A Bundled Spell landed: run its payload at the landing point.
    public static void runPayload(ServerPlayer player, List<SpellPlan.Step> payload, CastContext context) {
        run(player, payload, 0, context);
    }

    // Runs steps from the given index; a pause schedules the rest of the timeline.
    private static void run(ServerPlayer player, List<SpellPlan.Step> steps, int from, CastContext context) {
        run(player, steps, from, context, false);
    }

    // As above; charged: the first step is a beam glyph whose Charge wind-up just ended.
    private static void run(ServerPlayer player, List<SpellPlan.Step> steps, int from, CastContext context, boolean charged) {
        for (int i = from; i < steps.size(); i++) {
            SpellPlan.Step step = steps.get(i);
            if (step instanceof SpellPlan.Pause pause) {
                int next = i + 1;
                SpellScheduler.schedule(player, pause.ticks(), later -> run(later, steps, next, context));
                return;
            }
            if (step instanceof SpellPlan.Bundle bundle) {
                if (SpellPlan.containsCast(bundle.payload())) {
                    BundleLauncher.launch(player, bundle, context);
                } else {
                    showMessage(player, new TranslatableComponent("vaultspellbook.cast.empty_bundle"));
                }
                return; // everything after a Bundled Spell is its payload
            }
            SpellPlan.Cast cast = (SpellPlan.Cast) step;
            boolean woundUp = charged && i == from;
            if (!woundUp && cast.count(AugmentType.CHARGE) > 0) {
                int ticks = chargeTicks(cast);
                if (startCharge(player, cast, ticks)) {
                    int index = i;
                    SpellScheduler.schedule(player, ticks, later -> run(later, steps, index, context, true));
                    return; // the rest of the spell waits for the wind-up, like a Delay
                }
            }
            castWithAugments(player, cast, context, woundUp);
            if (cast.count(AugmentType.LOOP) > 0) {
                AbilityLoops.arm(player, cast);
            }
        }
    }

    // Charge on an Arcane or Arcane Rail glyph winds it up before it fires: clients show
    // the charge at the hand and play the charge sound for the wind-up. Not when the glyph can't fire
    // then (disabled, not learned, on cooldown) or would switch a running Arcane off: it casts at once.
    private static boolean startCharge(ServerPlayer player, SpellPlan.Cast cast, int ticks) {
        AbilityVisuals.BeamKind kind = AbilityVisuals.beamKind(cast.ability());
        AbilityAdapter adapter = cast.ability().getAdapter();
        String id = adapter.getSpecializationId();
        if (kind == null || VaultSpellbookConfig.isGlyphDisabled(id) || DrivenHoldSessions.isOn(player.getUUID(), id)) {
            return false;
        }
        AbilityTree tree = VaultAbilities.getServerTree(player);
        if (!adapter.isUnlocked(tree) || adapter.getRemainingCooldownTicks(tree) > 0) {
            return false;
        }
        BeamSync.charge(player, kind, cast.color(), ticks);
        return true;
    }

    private static CastResult castWithAugments(ServerPlayer player, SpellPlan.Cast cast, CastContext context, boolean charged) {
        AbilityGlyph glyph = cast.ability();
        AbilityAdapter adapter = glyph.getAdapter();
        if (VaultSpellbookConfig.isGlyphDisabled(adapter.getSpecializationId())) {
            report(player, glyph, CastResult.DISABLED);
            return CastResult.DISABLED;
        }
        CastModifiers base = CastModifiers.of(cast);
        CastModifiers modifiers = base.withDamageFactor((float) (context.damageMultiplier() * Math.pow(0.5, base.shotgunSplits())));
        CastOrigin origin = context.origin();
        if (!context.claimNative(glyph)) {
            castExtra(player, glyph, modifiers, origin); // a repeat landing (Shotgun copy, Creative Loop)
            return CastResult.SUCCESS;
        }
        List<Vec3> aims = shotgunAims(player, origin, modifiers);
        CastResult result = ManaRefunds.withRefund(player, modifiers.discountFraction(),
                () -> aimed(player, aims.get(0), () -> CastScope.call(player, modifiers, aimedOrigin(origin, aims.get(0)),
                        () -> adapter.cast(player, aimedOrigin(origin, aims.get(0))))));
        afterRail(player, glyph, modifiers, aims.get(0), result);
        if (result == CastResult.SUCCESS) {
            castShotgunCopies(player, glyph, modifiers, origin, aims);
        }
        report(player, glyph, result);
        if (result != CastResult.SUCCESS && result != CastResult.STARTED && result != CastResult.STOPPED) {
            return result;
        }

        applyQuicken(player, adapter, modifiers);
        if (charged) {
            blastAfterCharge(player, glyph, result);
        }
        if (glyph.has(AbilityTrait.TOGGLE) && adapter.findAbility(player).filter(Ability::isActive).isPresent()) {
            ToggleColors.start(player, glyph, modifiers.color()); // what the toggle spawns while on
        }
        if (glyph.has(AbilityTrait.HOLD) && result == CastResult.STARTED) {
            DrivenHoldSessions.setModifiers(player.getUUID(), adapter.getSpecializationId(), modifiers);
        }
        if (modifiers.discountFraction() > 0) {
            continueDiscount(player, glyph, modifiers.discountFraction());
        }
        if (modifiers.echo()) {
            // Not in the same tick: the first cast's hits make mobs ignore new damage for 10 ticks (LivingEntity.hurt).
            int echoDelay = VaultSpellbookConfig.secondsToTicks(VaultSpellbookConfig.ECHO_DELAY_SECONDS.get());
            SpellScheduler.schedule(player, echoDelay, later -> castExtra(later, glyph, modifiers, origin));
        }
        return result;
    }

    // Echo and repeat landings: an extra cast that pays mana again but starts no cooldown,
    // as a full Shotgun volley if the ability has Shotgun. Toggles and holds have no extra cast; a
    // repeat simply doesn't happen for them.
    private static void castExtra(ServerPlayer player, AbilityGlyph glyph, CastModifiers modifiers, @Nullable CastOrigin origin) {
        List<Vec3> aims = shotgunAims(player, origin, modifiers);
        CastResult result = castExtraAimed(player, glyph, modifiers, origin, aims.get(0), modifiers.discountFraction());
        if (result == CastResult.SUCCESS) {
            castShotgunCopies(player, glyph, modifiers, origin, aims);
        } else if (result != CastResult.UNSUPPORTED) {
            report(player, glyph, result);
        }
    }

    // Shotgun on an ability projectile: each further direction is an extra cast with the
    // player's aim turned that way. A split is free, so its mana is refunded in full; every copy deals
    // 0.5x damage per split (already in the modifiers).
    private static void castShotgunCopies(ServerPlayer player, AbilityGlyph glyph, CastModifiers modifiers,
                                          @Nullable CastOrigin origin, List<Vec3> aims) {
        for (int i = 1; i < aims.size(); i++) {
            castExtraAimed(player, glyph, modifiers, origin, aims.get(i), 1.0F);
        }
    }

    private static CastResult castExtraAimed(ServerPlayer player, AbilityGlyph glyph, CastModifiers modifiers,
                                             @Nullable CastOrigin origin, Vec3 aim, float refundFraction) {
        CastOrigin aimedOrigin = aimedOrigin(origin, aim);
        CastResult result = ManaRefunds.withRefund(player, refundFraction, () -> aimed(player, aim,
                () -> CastScope.call(player, modifiers, aimedOrigin, () -> glyph.getAdapter().castExtra(player, aimedOrigin))));
        afterRail(player, glyph, modifiers, aim, result);
        return result;
    }

    // After an Arcane Rail fired: Size Up's extra hits on mobs within the wider radius, and a coloured
    // Rail is drawn by clients as a beam in its colour and style.
    private static void afterRail(ServerPlayer player, AbilityGlyph glyph, CastModifiers modifiers, Vec3 aim, CastResult result) {
        if (result != CastResult.SUCCESS || AbilityVisuals.beamKind(glyph) != AbilityVisuals.BeamKind.RAIL) {
            return;
        }
        double extraRadius = RailBolts.extraRadius(modifiers);
        if (extraRadius > 0) {
            RailBolts.widen(player, glyph, modifiers, aim, extraRadius);
        }
        if (modifiers.color() != null) {
            float power = modifiers.charged() ? VaultSpellbookConfig.CHARGE_RAIL_MULTIPLIER.get().floatValue() : 1.0F;
            BeamSync.rail(player, modifiers.color(), aim, extraRadius, power);
        }
    }

    // The Shotgun directions (just the current aim without Shotgun), around the bundle's direction for a landing.
    private static List<Vec3> shotgunAims(ServerPlayer player, @Nullable CastOrigin origin, CastModifiers modifiers) {
        Vec3 base = origin != null && origin.direction().lengthSqr() > 1.0E-6 ? origin.direction() : player.getLookAngle();
        return ShotgunPattern.directions(base, modifiers.shotgunSplits(), VaultSpellbookConfig.SHOTGUN_SPREAD_DEGREES.get());
    }

    private static <T> T aimed(ServerPlayer player, Vec3 aim, Supplier<T> action) {
        return AimOverride.call(player, aim, action);
    }

    @Nullable
    private static CastOrigin aimedOrigin(@Nullable CastOrigin origin, Vec3 aim) {
        return origin == null ? null : new CastOrigin(origin.position(), aim);
    }

    // Quicken: skip a share of the cooldown this cast just started (none for a toggle switched on).
    private static void applyQuicken(ServerPlayer player, AbilityAdapter adapter, CastModifiers modifiers) {
        if (modifiers.quickenFraction() <= 0) {
            return;
        }
        adapter.findAbility(player).ifPresent(ability -> ability.getCooldown().ifPresent(cooldown -> {
            ability.reduceCooldownBy(Math.round(cooldown.getMaxTicks() * modifiers.quickenFraction()));
            VaultAbilities.getServerTree(player).sync(SkillContext.of(player));
        }));
    }

    // A charged beam fired: its blast sound plays (Rail) or loops while the Arcane hold runs, and a
    // charged Arcane's damage follows ChargeCurve from now.
    private static void blastAfterCharge(ServerPlayer player, AbilityGlyph glyph, CastResult result) {
        AbilityVisuals.BeamKind kind = AbilityVisuals.beamKind(glyph);
        if (kind == AbilityVisuals.BeamKind.ARCANE && result == CastResult.STARTED) {
            DrivenHoldSessions.markCharged(player.getUUID(), glyph.getAdapter().getSpecializationId(), player.level.getGameTime());
        } else if (kind == AbilityVisuals.BeamKind.RAIL && result == CastResult.SUCCESS) {
            BeamSync.blast(player, kind);
        }
    }

    // Discount on a toggle the cast switched on (a driven hold keeps its augments in its session).
    private static void continueDiscount(ServerPlayer player, AbilityGlyph glyph, float fraction) {
        String specializationId = glyph.getAdapter().getSpecializationId();
        if (glyph.has(AbilityTrait.TOGGLE) && glyph.getAdapter().findAbility(player).filter(Ability::isActive).isPresent()) {
            ToggleDiscounts.start(player, specializationId, fraction);
        }
    }

    private static void report(ServerPlayer player, AbilityGlyph glyph, CastResult result) {
        if (result == CastResult.ON_COOLDOWN) {
            int remainingTicks = glyph.getAdapter().getRemainingCooldownTicks(VaultAbilities.getServerTree(player));
            ModNetwork.sendTo(player, new CooldownCountdownPacket(glyph.getLocalizationKey(), remainingTicks));
        } else if (result.getMessageKey() != null) {
            showMessage(player, new TranslatableComponent(result.getMessageKey(),
                    new TranslatableComponent(glyph.getLocalizationKey())));
        }
    }

    // Whether the book casts here at all (server config: vaults only, or everywhere).
    public static boolean worksHere(ServerPlayer player) {
        return VaultSpellbookConfig.WORKS_OUTSIDE_VAULTS.get() || isInVault(player);
    }

    // Whether a glyph's ability may run here now: not disabled in the config, and the book works here.
    public static boolean isAllowedHere(ServerPlayer player, String specializationId) {
        return !VaultSpellbookConfig.isGlyphDisabled(specializationId) && worksHere(player);
    }

    // Vault dimensions live in the the_vault namespace; Wold's Vaults uses the same check (MixinISpellHotkeyListener).
    public static boolean isInVault(ServerPlayer player) {
        return player.getLevel().dimension().location().getNamespace().equals("the_vault");
    }

    // Shows a static message, replacing any running countdown so the two don't fight over the action bar.
    public static void showMessage(ServerPlayer player, TranslatableComponent message) {
        ModNetwork.sendTo(player, CooldownCountdownPacket.stop());
        player.displayClientMessage(message, true);
    }
}
