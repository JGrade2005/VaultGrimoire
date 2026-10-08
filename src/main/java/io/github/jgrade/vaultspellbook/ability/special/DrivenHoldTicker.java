package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import io.github.jgrade.vaultspellbook.augment.ArcaneBeams;
import io.github.jgrade.vaultspellbook.augment.CastModifiers;
import io.github.jgrade.vaultspellbook.augment.ChargeCurve;
import io.github.jgrade.vaultspellbook.augment.ManaRefunds;
import io.github.jgrade.vaultspellbook.cast.VaultCaster;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.color.BeamSync;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import iskallia.vault.mana.ManaPlayer;
import iskallia.vault.skill.ability.effect.ArcaneAbility;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import iskallia.vault.skill.ability.effect.spi.core.HoldAbility;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.source.SkillSource;
import iskallia.vault.skill.tree.AbilityTree;
import iskallia.vault.world.data.DownedPlayerManager;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

// Runs glyph-started holds every server tick, the way VH's Ability.onTick runs an
// active ability: doActiveTick does the effect and mana drain, and a COOLDOWN result ends it with
// the cooldown. Sessions end when the specialization is no longer learned/selected, when the
// ability goes on cooldown (e.g. Levitate out of mana), or when the player is downed.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class DrivenHoldTicker {
    private DrivenHoldTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        DrivenHoldSessions.get(player.getUUID()).forEach((specializationId, modifiers) -> tick(player, specializationId, modifiers));
    }

    private static void tick(ServerPlayer player, String specializationId, CastModifiers modifiers) {
        AbilityTree tree = VaultAbilities.getServerTree(player);
        Optional<HoldAbility> found = VaultAbilities.findSpecialization(tree, specializationId)
                .filter(TieredSkill::isUnlocked)
                .map(TieredSkill::getChild)
                .filter(HoldAbility.class::isInstance)
                .map(HoldAbility.class::cast);
        if (found.isEmpty() || DownedPlayerManager.isPlayerDowned(player) || found.get().isTreeOnCooldown()) {
            DrivenHoldSessions.stop(player.getUUID(), specializationId);
            return;
        }
        HoldAbility ability = found.get();
        if (ability.isActive() || !VaultCaster.isAllowedHere(player, specializationId)) {
            return; // VH is running it natively (key held), or the server config pauses it here
        }

        SkillContext context = SkillContext.of(player);
        Ability.TickResult result;
        Long chargedSince = DrivenHoldSessions.chargedSince(player.getUUID(), specializationId);
        if (ability instanceof ArcaneAbility arcane) {
            // Spellbook Arcane always fires our beam (47-block reach; augments; colour).
            // A source with the player's mana but no entity: VH drains the mana and skips its own ray.
            SkillContext manaOnly = SkillContext.of(player, SkillSource.empty().setMana((ManaPlayer) player));
            result = ManaRefunds.withRefund(player, modifiers.discountFraction(), () -> ability.doActiveTick(manaOnly));
            if (result != Ability.TickResult.COOLDOWN) {
                float power = chargedSince == null ? 1.0F : ChargeCurve.beamMultiplier(player.level.getGameTime() - chargedSince);
                ArcaneBeams.fire(player, arcane, modifiers, power);
            }
        } else {
            result = ManaRefunds.withRefund(player, modifiers.discountFraction(), () -> ability.doActiveTick(context));
        }
        ability.setActive(false); // Levitate's restart path can re-activate it; it stays driven here
        if (result != Ability.TickResult.COOLDOWN && chargedSince != null) {
            BeamSync.blast(player, AbilityVisuals.BeamKind.ARCANE); // keeps the charged beam's blast sound looping
        }
        if (result == Ability.TickResult.COOLDOWN) {
            DrivenHoldSessions.stop(player.getUUID(), specializationId);
            ability.putOnCooldown(context);
            tree.sync(context);
            VaultCaster.showMessage(player, new TranslatableComponent("vaultspellbook.cast.out_of_mana",
                    new TranslatableComponent("ars_nouveau.glyph_name." + AbilityGlyph.glyphId(specializationId))));
        }
    }
}
