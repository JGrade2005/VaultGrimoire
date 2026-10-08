package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import io.github.jgrade.vaultspellbook.network.OrbSteerPacket;
import iskallia.vault.entity.entity.LightningOrbEntity;
import iskallia.vault.util.EntityHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

// Homing and Orbit: steers projectiles a cast spawned by setting their motion each
// server tick. The projectile's own logic still moves it and handles hits, so a hit ends it as usual.
// Steering stops when the projectile is gone, stuck in a block, or its owner left; orbiting
// projectiles that hit nothing are removed after the configured time. Ball of Lightning ignores its
// motion and flies by its own direction, so it is steered through that instead (LightningOrbs).
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class ProjectileSteering {
    private static final double GOLDEN_ANGLE = 2.39996;
    private static final double ORBIT_HEIGHT = 1.2;
    private static final double HOMING_TURN_RATE = 0.2;
    // A homing projectile keeps its target and looks for a nearer one this often.
    private static final int RETARGET_TICKS = 5;
    // A steered Ball of Lightning's course is resent once it is 2 degrees off what clients have, or every 10 ticks.
    private static final double RESEND_COS = Math.cos(Math.toRadians(2.0));
    private static final int RESEND_TICKS = 10;
    private static final List<Steered> STEERED = new ArrayList<>();

    private ProjectileSteering() {
    }

    public static void orbit(Projectile projectile, ServerPlayer owner) {
        long orbiting = STEERED.stream().filter(s -> s.orbit && s.owner == owner).count();
        STEERED.add(new Steered(projectile, owner, true, orbiting * GOLDEN_ANGLE));
    }

    public static void home(Projectile projectile, ServerPlayer owner) {
        STEERED.add(new Steered(projectile, owner, false, 0));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int maxOrbitTicks = VaultSpellbookConfig.ORBIT_MAX_SECONDS.get() * 20;
        for (Iterator<Steered> it = STEERED.iterator(); it.hasNext(); ) {
            Steered steered = it.next();
            Projectile projectile = steered.projectile;
            if (projectile.isRemoved() || steered.owner.isRemoved() || steered.owner.level != projectile.level || steered.isStuck()) {
                it.remove();
                continue;
            }
            steered.age++;
            if (steered.orbit) {
                if (steered.age > maxOrbitTicks) {
                    projectile.discard();
                    it.remove();
                    continue;
                }
                steerOrbit(steered);
            } else {
                steerHoming(steered);
            }
            projectile.hurtMarked = true; // send the new motion to clients this tick
        }
    }

    // Holds entities (and so their level): a closed singleplayer world must not stay reachable through it.
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STEERED.clear();
    }

    private static void steerOrbit(Steered steered) {
        double radius = VaultSpellbookConfig.ORBIT_RADIUS.get();
        double angularSpeed = Math.max(0.15, Math.min(0.6, steered.speed / radius));
        double angle = steered.angleOffset + steered.age * angularSpeed;
        Vec3 center = steered.owner.position().add(0, ORBIT_HEIGHT, 0);
        Vec3 target = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        move(steered, target.subtract(steered.projectile.position()));
    }

    private static void steerHoming(Steered steered) {
        Projectile projectile = steered.projectile;
        AABB area = projectile.getBoundingBox().inflate(VaultSpellbookConfig.HOMING_RANGE.get());
        LivingEntity target = steered.target;
        if (steered.age % RETARGET_TICKS == 0 || target == null || target.level != projectile.level
                || !target.getBoundingBox().intersects(area) || !canTarget(projectile, steered.owner, target)) {
            target = findTarget(projectile, steered.owner, area).orElse(null);
            steered.target = target;
        }
        if (target == null) {
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        double speed = Math.max(motion.length(), steered.speed);
        Vec3 desired = target.getBoundingBox().getCenter().subtract(projectile.position()).normalize().scale(speed);
        Vec3 turned = motion.add(desired.subtract(motion).scale(HOMING_TURN_RATE));
        move(steered, turned.normalize().scale(speed));
    }

    private static void move(Steered steered, Vec3 motion) {
        steered.projectile.setDeltaMovement(motion);
        if (steered.projectile instanceof LightningOrbEntity orb && motion.lengthSqr() > 1.0E-8) {
            Vec3 direction = motion.normalize();
            float speed = (float) motion.length();
            LightningOrbs.steer(orb, direction, speed);
            if (steered.needsResend(direction, speed)) {
                ModNetwork.sendToTracking(orb, new OrbSteerPacket(orb.getId(), direction, speed));
            }
        }
    }

    private static Optional<LivingEntity> findTarget(Projectile projectile, ServerPlayer owner, AABB area) {
        return projectile.level.getEntitiesOfClass(LivingEntity.class, area, e -> canTarget(projectile, owner, e))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(projectile)));
    }

    private static boolean canTarget(Projectile projectile, ServerPlayer owner, LivingEntity e) {
        return e != owner && e.isAlive() && EntityHelper.VAULT_TARGET_SELECTOR.test(e) && owner.hasLineOfSight(e)
                && !(projectile instanceof LightningOrbEntity orb && LightningOrbs.alreadyHit(orb).contains(e.getId()));
    }

    private static final class Steered {
        final Projectile projectile;
        final ServerPlayer owner;
        final boolean orbit;
        final double angleOffset;
        final double speed;
        int age;
        Vec3 lastPosition;
        int stillTicks;
        LivingEntity target;
        Vec3 sentDirection;
        float sentSpeed;
        int sentAge;

        Steered(Projectile projectile, ServerPlayer owner, boolean orbit, double angleOffset) {
            this.projectile = projectile;
            this.owner = owner;
            this.orbit = orbit;
            this.angleOffset = angleOffset;
            this.speed = projectile.getDeltaMovement().length();
            this.lastPosition = projectile.position();
        }

        // Arrows stuck in a block stop moving; give up on them after two still ticks.
        boolean isStuck() {
            Vec3 position = projectile.position();
            stillTicks = position.distanceToSqr(lastPosition) < 1.0E-4 ? stillTicks + 1 : 0;
            lastPosition = position;
            return age > 2 && stillTicks >= 2;
        }

        boolean needsResend(Vec3 direction, float speed) {
            if (sentDirection != null && age - sentAge < RESEND_TICKS && direction.dot(sentDirection) > RESEND_COS
                    && Math.abs(speed - sentSpeed) <= sentSpeed * 0.05F) {
                return false;
            }
            sentDirection = direction;
            sentSpeed = speed;
            sentAge = age;
            return true;
        }
    }
}
