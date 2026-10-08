package io.github.jgrade.vaultspellbook.ability;

import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.tree.AbilityTree;
import iskallia.vault.world.data.PlayerAbilitiesData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// Lookups into a player's Vault Hunters ability tree.
public final class VaultAbilities {
    private VaultAbilities() {
    }

    public static AbilityTree getServerTree(ServerPlayer player) {
        return PlayerAbilitiesData.get(player.getLevel()).getAbilities(player);
    }

    // Finds a specialization by id. Only the selected specialization of a group is ever learned:
    // VH moves the learned tiers when the player switches (SpecializedSkill.specialize).
    public static Optional<TieredSkill> findSpecialization(AbilityTree tree, String specializationId) {
        if (tree == null) {
            return Optional.empty();
        }
        return tree.getForId(specializationId)
                .filter(TieredSkill.class::isInstance)
                .map(TieredSkill.class::cast);
    }
}
