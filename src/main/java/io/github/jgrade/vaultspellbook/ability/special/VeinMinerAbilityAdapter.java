package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.ability.AbstractAbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.CastResult;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.skill.ability.effect.spi.AbstractVeinMinerAbility;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

// Vein Miner glyphs are a switch: the first cast turns glyph vein mining on, the
// next turns it off. While on, VeinMinerBreakHandler vein-mines with the player's own Vein Miner
// ability whatever ability is selected, so other abilities can be used meanwhile. VH's own hold
// key is never pressed; Vein Miner has no mana cost and no cooldown in the pack (abilities.json).
public class VeinMinerAbilityAdapter extends AbstractAbilityAdapter {
    public VeinMinerAbilityAdapter(String specializationId) {
        super(specializationId);
    }

    @Override
    public CastResult cast(ServerPlayer player, @Nullable CastOrigin origin) { // switches act on the player; origin unused
        Lookup<AbstractVeinMinerAbility> lookup = lookup(VaultAbilities.getServerTree(player), AbstractVeinMinerAbility.class);
        if (lookup.failure() != null) {
            return lookup.failure();
        }
        boolean wasOn = VeinMinerSessions.get(player.getUUID()).filter(getSpecializationId()::equals).isPresent();
        if (wasOn) {
            VeinMinerSessions.stop(player.getUUID());
            return CastResult.STOPPED;
        }
        VeinMinerSessions.start(player.getUUID(), getSpecializationId());
        return CastResult.STARTED;
    }
}
