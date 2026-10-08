package io.github.jgrade.vaultspellbook.ability;

import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import iskallia.vault.skill.base.SkillContext;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.source.SkillSource;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;

// Lookups shared by all adapters: the specialization, its learned tier and its group cooldown.
public abstract class AbstractAbilityAdapter implements AbilityAdapter {
    private final String specializationId;

    protected AbstractAbilityAdapter(String specializationId) {
        this.specializationId = specializationId;
    }

    @Override
    public String getSpecializationId() {
        return specializationId;
    }

    @Override
    public boolean isUnlocked(AbilityTree tree) {
        return findSpecialization(tree).map(TieredSkill::isUnlocked).orElse(false);
    }

    @Override
    public int getRemainingCooldownTicks(AbilityTree tree) {
        return findSpecialization(tree)
                .flatMap(TieredSkill::getTreeCooldown)
                .map(cooldown -> cooldown.getRemainingDelayTicks() + cooldown.getRemainingTicks())
                .orElse(0);
    }

    @Override
    public Optional<Ability> findAbility(ServerPlayer player) {
        return findSpecialization(VaultAbilities.getServerTree(player))
                .filter(TieredSkill::isUnlocked)
                .map(TieredSkill::getChild)
                .filter(Ability.class::isInstance)
                .map(Ability.class::cast);
    }

    // The skill context for a cast. With an origin, VH's skill source carries that position, which the
    // abilities that read SkillSource.getPos (Nova, Earthquake, Battle Cry, Implode...) use.
    protected static SkillContext contextFor(ServerPlayer player, @Nullable CastOrigin origin) {
        return origin == null
                ? SkillContext.of(player)
                : SkillContext.of(player, SkillSource.of(player).setPos(origin.position()));
    }

    protected Optional<TieredSkill> findSpecialization(AbilityTree tree) {
        return VaultAbilities.findSpecialization(tree, specializationId);
    }

    // Resolves the player's own ability instance of the expected kind, or explains why there is none.
    // Exactly one of the two results is present.
    protected <T extends Ability> Lookup<T> lookup(AbilityTree tree, Class<T> type) {
        Optional<TieredSkill> specialization = findSpecialization(tree);
        if (specialization.isEmpty()) {
            return Lookup.failed(CastResult.UNSUPPORTED);
        }
        if (!specialization.get().isUnlocked()) {
            return Lookup.failed(CastResult.NOT_LEARNED);
        }
        if (!type.isInstance(specialization.get().getChild())) {
            return Lookup.failed(CastResult.UNSUPPORTED);
        }
        return new Lookup<>(type.cast(specialization.get().getChild()), null);
    }

    protected record Lookup<T>(T ability, CastResult failure) {
        static <T> Lookup<T> failed(CastResult failure) {
            return new Lookup<>(null, failure);
        }
    }
}
