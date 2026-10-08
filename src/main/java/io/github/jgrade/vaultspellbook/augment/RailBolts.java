package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import iskallia.vault.core.vault.VaultUtils;
import iskallia.vault.entity.IPlayerAttackIgnore;
import iskallia.vault.entity.entity.PetEntity;
import iskallia.vault.event.ActiveFlags;
import iskallia.vault.gear.etching.EtchingGearAttributes;
import iskallia.vault.gear.etching.EtchingHelper;
import iskallia.vault.skill.ability.effect.ArcaneRailAbility;
import iskallia.vault.util.calc.AbilityPowerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

// Size Up on Arcane Rail. VH fires the rail natively (mana, cooldown, its own thin hit);
// right after, this widens it: VH's rail rules (ability power x a roll between its min and max percent,
// as many targets as the Rail Pierce etching allows, nearest first, players only in PvP vaults, the same
// flags) applied to every mob within the wider radius of the same line. Mobs the native bolt just hit
// are immune for half a second, so they are skipped rather than hit twice. Shotgun on Rail needs nothing
// here: each Shotgun direction is a native extra cast (VaultCaster).
public final class RailBolts {
    // LivingEntity.hurt ignores new damage while invulnerableTime is above 10 (just hit).
    private static final int JUST_HIT = 10;

    private RailBolts() {
    }

    // Extra hit radius from Size Up, in blocks (as the Arcane beam: size - 1).
    public static double extraRadius(CastModifiers modifiers) {
        return Math.max(0.0, modifiers.scale() - 1.0);
    }

    public static void widen(ServerPlayer player, AbilityGlyph glyph, CastModifiers modifiers, Vec3 aim, double extraRadius) {
        if (!(glyph.getAdapter().findAbility(player).orElse(null) instanceof ArcaneRailAbility rail)) {
            return;
        }
        Vec3 start = BeamGeometry.start(player);
        Vec3 direction = aim.normalize();
        double length = BeamGeometry.railLength(player, start, direction);
        double radius = BeamGeometry.railRadius(player, extraRadius);
        int pierce = EtchingHelper.getEtchings(player, EtchingGearAttributes.RAIL_PIERCE).stream().findFirst()
                .map(instance -> instance.getValue()).orElse(1);
        boolean pvp = VaultUtils.getVault(player.level).map(VaultUtils::isPvPVault).orElse(false);

        Vec3 end = start.add(direction.scale(length));
        List<LivingEntity> targets = player.level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius + 1.0),
                        e -> e != player && e.isPickable() && e.isAlive() && !(e instanceof PetEntity)
                                && !IPlayerAttackIgnore.isIgnoredByPlayerAttack(player, e)
                                && BeamGeometry.touches(e, start, direction, length, radius))
                .stream()
                .sorted(Comparator.comparingDouble(e -> BeamGeometry.along(e, start, direction)))
                .toList();
        int hits = 0;
        for (LivingEntity target : targets) {
            if (hits >= pierce || (target instanceof ServerPlayer && !pvp)) {
                break;
            }
            hits++;
            if (target.invulnerableTime > JUST_HIT) {
                continue; // the native bolt (or something else) just hit it
            }
            float percent = Mth.lerp(player.getRandom().nextFloat(), rail.getPercentAbilityPowerDealtMin(),
                    rail.getPercentAbilityPowerDealtMax());
            float damage = AbilityPowerHelper.getAbilityPower(player) * percent * modifiers.damageMultiplier();
            ActiveFlags.IS_AP_ATTACKING.runIfNotSet(() -> ActiveFlags.IS_ARCANE_RAIL_ATTACKING
                    .runIfNotSet(() -> target.hurt(DamageSource.playerAttack(player), damage)));
        }
    }
}
