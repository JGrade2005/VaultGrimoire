package io.github.jgrade.vaultspellbook.ability;

import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import iskallia.vault.skill.tree.AbilityTree;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;

// Bridge from a Vault glyph to one Vault Hunters ability specialization. Only adapters touch
// Vault Hunters skill classes.
public interface AbilityAdapter {
    // The VH specialization id, e.g. "Dash_Base" (abilities.json).
    String getSpecializationId();

    // True when the specialization is learned and selected in the given tree (client or server copy).
    boolean isUnlocked(AbilityTree tree);

    // Ticks until the ability's group cooldown ends (delay included), or 0 when it is ready.
    int getRemainingCooldownTicks(AbilityTree tree);

    // Casts the ability for the player through its native checks, mana and cooldown. Server only.
    // origin: null to cast from the player; a Bundled Spell's landing point otherwise.
    CastResult cast(ServerPlayer player, @Nullable CastOrigin origin);

    // One extra cast (Echo, Shotgun copies, repeat Bundled Spell landings): pays the ability's mana
    // again but starts no cooldown. Only instant abilities support it. Server only.
    default CastResult castExtra(ServerPlayer player, @Nullable CastOrigin origin) {
        return CastResult.UNSUPPORTED;
    }

    // The player's own ability instance while the specialization is learned and selected. Server only.
    Optional<Ability> findAbility(ServerPlayer player);
}
