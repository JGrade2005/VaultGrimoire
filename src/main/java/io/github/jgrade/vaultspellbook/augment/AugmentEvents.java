package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import iskallia.vault.core.event.CommonEvents;
import iskallia.vault.util.calc.PlayerStat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

// Applies augments to what VH does during and after a cast, without
// changing VH: Amplify Area through VH's own PLAYER_STAT event (during the cast, and from a tagged
// projectile's landing until it is gone), projectile augments by tagging projectiles the cast
// spawns, and Strengthen on damage events (same windows). Projectiles spawned for a Bundled Spell landing are moved there.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class AugmentEvents {
    // Damage multiplier stored on projectiles spawned by a Strengthened (or Shotgun-split) cast.
    public static final String DAMAGE_TAG = VaultSpellbook.MOD_ID + ":damage_multiplier";
    // Area bonus stored on projectiles of an Amplify Area cast, applied when they land.
    public static final String AREA_TAG = VaultSpellbook.MOD_ID + ":area_bonus";
    private static final Object LISTENER_OWNER = new Object();

    // Landed tagged projectiles; VH can resolve a landing a few ticks later (Fireball explodes 2 ticks after impact).
    private static final List<Landing> LANDINGS = new ArrayList<>();
    private static final int MAX_LANDING_TICKS = 40;

    private AugmentEvents() {
    }

    // Registers the VH PLAYER_STAT listener. Call once during mod construction.
    public static void register() {
        CommonEvents.PLAYER_STAT.register(LISTENER_OWNER, data -> {
            if (data.getStat() != PlayerStat.AREA_OF_EFFECT || data.getEntity() == null) {
                return;
            }
            float bonus = CastScope.current()
                    .filter(scope -> scope.player() == data.getEntity())
                    .map(scope -> scope.modifiers().areaBonus())
                    .orElseGet(() -> landingBonus(data.getEntity(), Landing::areaBonus, 0.0F));
            if (bonus > 0) {
                data.setValue(data.getValue() + bonus);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        CastScope.current()
                .filter(scope -> projectile.getOwner() == scope.player())
                .filter(scope -> scope.origin() != null || scope.modifiers().affectsProjectiles())
                .ifPresent(scope -> applyToProjectile(projectile, scope));
    }

    private static void applyToProjectile(Projectile projectile, CastScope.Active scope) {
        CastModifiers modifiers = scope.modifiers();
        if (scope.origin() != null) {
            relaunchFrom(projectile, scope.origin());
        }
        if (modifiers.speedMultiplier() != 1.0F) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(modifiers.speedMultiplier()));
        }
        if (modifiers.damageMultiplier() != 1.0F) {
            projectile.getPersistentData().putFloat(DAMAGE_TAG, modifiers.damageMultiplier());
        }
        if (modifiers.areaBonus() > 0) {
            projectile.getPersistentData().putFloat(AREA_TAG, modifiers.areaBonus());
        }
        if (modifiers.scale() != 1.0F) {
            ProjectileSizing.apply(projectile, modifiers.scale());
        }
        if (modifiers.gravityReduction() > 0 || modifiers.ethereal()) {
            ProjectilePhysics.track(projectile, modifiers.gravityReduction(), modifiers.ethereal());
        }
        if (modifiers.orbit()) {
            ProjectileSteering.orbit(projectile, scope.player());
        } else if (modifiers.homing()) {
            ProjectileSteering.home(projectile, scope.player());
        }
    }

    // Bundled Spell landing: the projectile starts at the landing point, flying on in the bundle's direction.
    private static void relaunchFrom(Projectile projectile, CastOrigin origin) {
        double speed = projectile.getDeltaMovement().length();
        Vec3 direction = origin.direction().lengthSqr() > 1.0E-6
                ? origin.direction().normalize() : projectile.getDeltaMovement().normalize();
        projectile.setPos(origin.position());
        projectile.setDeltaMovement(direction.scale(speed));
    }

    // Ethereal block hits are ignored; an area-tagged projectile's landing opens the impact area scope.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level.isClientSide()) {
            return;
        }
        if (ProjectilePhysics.ignoresBlockHit(projectile, event.getRayTraceResult())) {
            event.setCanceled(true);
            return;
        }
        boolean tagged = projectile.getPersistentData().contains(AREA_TAG) || projectile.getPersistentData().contains(DAMAGE_TAG);
        if (tagged && projectile.getOwner() != null && LANDINGS.stream().noneMatch(l -> l.projectile == projectile)) {
            LANDINGS.add(new Landing(projectile, projectile.getOwner(),
                    projectile.getPersistentData().getFloat(AREA_TAG),
                    projectile.getPersistentData().contains(DAMAGE_TAG) ? projectile.getPersistentData().getFloat(DAMAGE_TAG) : 1.0F));
        }
    }

    // A landing stays open until its projectile is gone (or 2 s): VH computes the landing's area and
    // damage in the projectile's later ticks, crediting the player (Fireball: explodeDelayed, then
    // DamageSource.playerAttack(player), VaultFireball.java:520-583). Other area/damage of the same
    // player during that short window is boosted too.
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !LANDINGS.isEmpty()) {
            LANDINGS.removeIf(landing -> ++landing.age > MAX_LANDING_TICKS || landing.projectile.isRemoved());
        }
    }

    // Holds entities (and so their level): a closed singleplayer world must not stay reachable through it.
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LANDINGS.clear();
    }

    private static float landingBonus(Entity owner, ToDoubleFunction<Landing> value, float none) {
        double best = none;
        for (Landing landing : LANDINGS) {
            if (landing.owner == owner) {
                best = Math.max(best, value.applyAsDouble(landing));
            }
        }
        return (float) best;
    }

    // Strengthen: damage from a tagged projectile, or dealt by the player during a Strengthened cast.
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile && direct.getPersistentData().contains(DAMAGE_TAG)) { // only projectiles get the tag
            event.setAmount(event.getAmount() * direct.getPersistentData().getFloat(DAMAGE_TAG));
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }
        float multiplier = CastScope.current()
                .filter(scope -> scope.player() == attacker)
                .map(scope -> scope.modifiers().damageMultiplier())
                .orElseGet(() -> landingBonus(attacker, Landing::damageMultiplier, 1.0F));
        if (multiplier != 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    private static final class Landing {
        final Projectile projectile;
        final Entity owner;
        final float areaBonus;
        final float damageMultiplier;
        int age;

        Landing(Projectile projectile, Entity owner, float areaBonus, float damageMultiplier) {
            this.projectile = projectile;
            this.owner = owner;
            this.areaBonus = areaBonus;
            this.damageMultiplier = damageMultiplier;
        }

        float areaBonus() {
            return areaBonus;
        }

        float damageMultiplier() {
            return damageMultiplier;
        }
    }
}
