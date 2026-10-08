package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.ability.AbstractAbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.CastResult;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.skill.ability.effect.spi.core.HoldAbility;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

// Arcane and Levitate glyphs: cast to switch on, cast again to switch off, and it
// keeps going whatever ability is selected. Starting runs the native key-down checks and begin
// action (VH mana check, curses, downed), then DrivenHoldTicker runs the ability's own per-tick
// effect and mana drain. VH's "active" flag stays off so VH's selection changes (which release the
// selected hold) cannot stop it and VH never ticks it a second time. Stopping is the native key-up
// (end action and cooldown).
public class DrivenHoldAbilityAdapter extends AbstractAbilityAdapter {
    public DrivenHoldAbilityAdapter(String specializationId) {
        super(specializationId);
    }

    @Override
    public CastResult cast(ServerPlayer player, @Nullable CastOrigin origin) { // switches act on the player; origin unused
        AbilityTree tree = VaultAbilities.getServerTree(player);
        Lookup<HoldAbility> lookup = lookup(tree, HoldAbility.class);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        HoldAbility ability = lookup.ability();
        SkillContext context = SkillContext.of(player);

        if (DrivenHoldSessions.isOn(player.getUUID(), getSpecializationId())) {
            DrivenHoldSessions.stop(player.getUUID(), getSpecializationId());
            release(ability, context);
            tree.sync(context);
            return CastResult.STOPPED;
        }
        if (ability.isTreeOnCooldown()) {
            return CastResult.ON_COOLDOWN;
        }
        boolean accepted = ability.onKeyDown(context);
        boolean began = ability.isActive();
        ability.setActive(false); // driven by DrivenHoldTicker instead of VH's own ticking
        if (!accepted) {
            return CastResult.BLOCKED;
        }
        if (!began) {
            return CastResult.FAILED; // e.g. not enough mana to begin
        }
        DrivenHoldSessions.start(player.getUUID(), getSpecializationId());
        return CastResult.STARTED;
    }

    // The native key-up: HoldAbility.onKeyUp only ends a hold that is active, so mark it active for
    // that call. It runs the end action and the cooldown; Levitate's override also removes the float.
    // Skipped when the player is holding VH's own key for it (natively active): VH ends that one.
    private static void release(HoldAbility ability, SkillContext context) {
        if (ability.isActive()) {
            return;
        }
        ability.setActive(true);
        ability.onKeyUp(context);
        ability.setActive(false);
    }
}
