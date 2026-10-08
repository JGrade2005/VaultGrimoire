package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.color.BeamSync;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.form.ShotgunPattern;
import iskallia.vault.core.vault.VaultUtils;
import iskallia.vault.entity.IPlayerAttackIgnore;
import iskallia.vault.entity.entity.PetEntity;
import iskallia.vault.event.ActiveFlags;
import iskallia.vault.gear.etching.EtchingGearAttributes;
import iskallia.vault.gear.etching.EtchingHelper;
import iskallia.vault.init.ModParticles;
import iskallia.vault.skill.ability.effect.ArcaneAbility;
import iskallia.vault.util.calc.AbilityPowerHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The Arcane beam of every spellbook Arcane, fired once per tick in place of VH's
// ArcaneAbility.fireRay (private: one thin ray with VH's own reach). It reaches beam.arcaneRange
// (47 blocks), Charge multiplies its damage (ChargeCurve), and otherwise it follows VH's
// rules: ability power x percentAbilityPowerDealt per hit, as many targets per beam as the Arcane
// Pierce etching allows (1 without), nearest first, players only in PvP vaults, same flags, particle
// and sound. Size Up widens the hit radius by (size - 1) blocks, Shotgun fires 2^n beams in the
// Shotgun pattern at 0.5^n damage each (in the modifiers' damage multiplier), Ethereal ignores blocks.
// A target is hit at most once per tick, whatever the number of beams. A coloured Arcane glyph sends
// no particles: clients draw it as a solid beam in its colour and style (BeamSync).
public final class ArcaneBeams {
    private static final double PARTICLE_STEP = 0.4;

    private ArcaneBeams() {
    }

    // power: a charged beam's current damage multiplier (ChargeCurve), 1 otherwise.
    public static void fire(ServerPlayer player, ArcaneAbility ability, CastModifiers modifiers, float power) {
        float range = VaultSpellbookConfig.ARCANE_RANGE.get().floatValue();
        Vec3 start = BeamGeometry.start(player);
        double radius = BeamGeometry.BASE_RADIUS + Math.max(0.0, modifiers.scale() - 1.0);
        float damage = AbilityPowerHelper.getAbilityPower(player) * ability.getPercentAbilityPowerDealt()
                * modifiers.damageMultiplier() * power;
        int pierce = EtchingHelper.getEtchings(player, EtchingGearAttributes.ARCANE_PIERCE).stream().findFirst()
                .map(instance -> instance.getValue()).orElse(1);
        double spread = VaultSpellbookConfig.SHOTGUN_SPREAD_DEGREES.get();
        List<Beam> beams = new ArrayList<>();
        AABB area = null;
        for (Vec3 direction : ShotgunPattern.directions(player.getLookAngle(), modifiers.shotgunSplits(), spread)) {
            Vec3 unit = direction.normalize();
            Beam beam = new Beam(unit, length(player, start, unit, range, modifiers.ethereal()));
            AABB box = new AABB(start, start.add(beam.direction().scale(beam.length())));
            area = area == null ? box : area.minmax(box);
            beams.add(beam);
        }
        // One entity query for all of the tick's beams; each beam then picks its own targets from it.
        List<LivingEntity> candidates = player.level.getEntitiesOfClass(LivingEntity.class, area.inflate(radius + 1.0),
                e -> e != player && e.isPickable() && e.isAlive() && !(e instanceof PetEntity)
                        && !IPlayerAttackIgnore.isIgnoredByPlayerAttack(player, e));
        boolean pvp = VaultUtils.getVault(player.level).map(VaultUtils::isPvPVault).orElse(false);
        Set<LivingEntity> hitThisTick = new HashSet<>();
        ParticleOptions particle = modifiers.color() == null ? (SimpleParticleType) ModParticles.ARCANE.get() : null;
        for (Beam beam : beams) {
            fireBeam(player, start, beam, radius, candidates, pvp, pierce, damage, hitThisTick, particle);
        }
        if (modifiers.color() != null) {
            BeamSync.hold(player, modifiers.color(), (float) radius, range, modifiers.ethereal(),
                    modifiers.shotgunSplits(), (float) spread, pierce, power);
        }
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                0.5F, 2.0F);
    }

    // How far a beam goes: to the first block it hits, or its full range when ethereal.
    private static double length(ServerPlayer player, Vec3 start, Vec3 direction, double range, boolean ethereal) {
        if (!ethereal) {
            HitResult blockHit = player.level.clip(new ClipContext(start, start.add(direction.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS) {
                return blockHit.getLocation().distanceTo(start);
            }
        }
        return range;
    }

    private static void fireBeam(ServerPlayer player, Vec3 start, Beam beam, double radius, List<LivingEntity> candidates,
                                 boolean pvp, int pierce, float damage, Set<LivingEntity> hitThisTick,
                                 @Nullable ParticleOptions particle) {
        Vec3 direction = beam.direction();
        double length = beam.length();
        List<LivingEntity> targets = candidates.stream()
                .filter(e -> BeamGeometry.touches(e, start, direction, length, radius))
                .sorted(Comparator.comparingDouble(e -> BeamGeometry.along(e, start, direction)))
                .toList();

        int hits = 0;
        for (LivingEntity target : targets) {
            if (hits >= pierce) {
                break;
            }
            if (target instanceof ServerPlayer && !pvp) {
                break; // VH's beam stops at a player outside PvP vaults
            }
            hits++;
            if (hitThisTick.add(target)) {
                DamageSource source = DamageSource.playerAttack(player);
                ActiveFlags.IS_AP_ATTACKING.runIfNotSet(
                        () -> ActiveFlags.IS_EFFECT_ATTACKING.runIfNotSet(() -> target.hurt(source, damage)));
            }
        }
        if (particle != null) {
            spawnParticles((ServerLevel) player.level, particle, start, direction, length, radius);
        }
    }

    private record Beam(Vec3 direction, double length) {
    }

    // VH's particle line; a wider beam spreads more particles over its width.
    private static void spawnParticles(ServerLevel level, ParticleOptions particle, Vec3 start, Vec3 direction, double length,
                                       double radius) {
        double spread = radius > BeamGeometry.BASE_RADIUS ? radius * 0.5 : 0.1;
        int count = radius > BeamGeometry.BASE_RADIUS ? Math.min(4, 1 + (int) radius) : 1;
        double step = Math.max(PARTICLE_STEP, length / 48); // at most ~48 particle packets per beam per tick
        for (double d = 0.0; d < length; d += step) {
            Vec3 pos = start.add(direction.scale(d));
            level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.0);
        }
        Vec3 end = start.add(direction.scale(length));
        level.sendParticles(particle, end.x, end.y, end.z, 3, 0.1, 0.1, 0.1, 0.0);
    }
}
