package io.github.jgrade.vaultspellbook.ability;

import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import iskallia.vault.skill.ability.effect.spi.core.InstantAbility;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

// Adapter for VH instant and toggle abilities. A cast is one native key press
// on the player's own ability instance: onKeyDown, then the real onKeyUp (so overrides such as
// Shield Bash's hit refund still run; a toggle flips on/off), then the tree sync AbilityTree.onKeyUp
// does. Success is read from VH's ABILITY_CAST event, because onKeyUp returns true even when the
// cast itself fails (no mana, cast-failure chance).
public class KeyPressAbilityAdapter extends AbstractAbilityAdapter {
    private final Class<? extends Ability> abilityType;

    public KeyPressAbilityAdapter(String specializationId, Class<? extends Ability> abilityType) {
        super(specializationId);
        this.abilityType = abilityType;
    }

    @Override
    public CastResult cast(ServerPlayer player, @Nullable CastOrigin origin) {
        AbilityTree tree = VaultAbilities.getServerTree(player);
        Lookup<? extends Ability> lookup = lookup(tree, abilityType);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        Ability ability = lookup.ability();
        if (ability.isTreeOnCooldown()) {
            return CastResult.ON_COOLDOWN;
        }

        SkillContext context = contextFor(player, origin);
        ability.onKeyDown(context);
        AbilityCastTracker.Outcome outcome = AbilityCastTracker.watch(ability, () -> ability.onKeyUp(context));
        if (!outcome.accepted()) {
            return CastResult.BLOCKED;
        }
        tree.sync(context);
        return outcome.cast() ? CastResult.SUCCESS : CastResult.FAILED;
    }

    // Extra cast (Echo, Shotgun copies): runs the instant's action (mana check, cast-failure chance, effect, mana
    // payment, ABILITY_CAST) without the key-press cooldown, as VH's CastAbilityBottleEffect does.
    @Override
    public CastResult castExtra(ServerPlayer player, @Nullable CastOrigin origin) {
        AbilityTree tree = VaultAbilities.getServerTree(player);
        Lookup<InstantAbility> lookup = lookup(tree, InstantAbility.class);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        InstantAbility ability = lookup.ability();
        SkillContext context = contextFor(player, origin);
        AbilityCastTracker.Outcome outcome = AbilityCastTracker.watch(ability, () -> {
            ability.onAction(context);
            return true;
        });
        tree.sync(context);
        return outcome.cast() ? CastResult.SUCCESS : CastResult.FAILED;
    }
}
