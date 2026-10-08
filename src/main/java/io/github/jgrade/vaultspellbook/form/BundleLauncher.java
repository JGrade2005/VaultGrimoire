package io.github.jgrade.vaultspellbook.form;

import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import io.github.jgrade.vaultspellbook.augment.AugmentType;
import io.github.jgrade.vaultspellbook.augment.ProjectileSteering;
import io.github.jgrade.vaultspellbook.cast.CastContext;
import io.github.jgrade.vaultspellbook.cast.SpellPlan;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// Throws a Bundled Spell from the player, or from a landing point for a bundle inside
// another bundle's payload. Shotgun copies share one landing tracker, so only the first landing of
// each ability casts natively; each copy's payload deals 0.5x damage per split.
public final class BundleLauncher {
    private static final double BASE_SPEED = 1.0;
    // Ars's default projectile colour (ColoredProjectile.defineSynchedData: 255, 25, 180).
    private static final int DEFAULT_RGB = 0xFF19B4;

    private BundleLauncher() {
    }

    public static void launch(ServerPlayer player, SpellPlan.Bundle bundle, CastContext context) {
        Vec3 start;
        Vec3 direction;
        if (context.origin() != null) {
            start = context.origin().position();
            direction = context.origin().direction().lengthSqr() > 1.0E-6 ? context.origin().direction() : player.getLookAngle();
        } else {
            start = player.getEyePosition().subtract(0, 0.1, 0);
            direction = player.getLookAngle();
        }
        int splits = bundle.count(AugmentType.SHOTGUN);
        float scale = (float) (Math.pow(1 + VaultSpellbookConfig.SIZE_UP_BONUS.get(), bundle.count(AugmentType.SIZE_UP))
                * Math.pow(1 - VaultSpellbookConfig.SIZE_DOWN_REDUCTION.get(), bundle.count(AugmentType.SIZE_DOWN)));
        double speed = BASE_SPEED * (1 + bundle.count(AugmentType.ACCELERATE) * VaultSpellbookConfig.ACCELERATE_SPEED_BONUS.get());
        boolean ethereal = bundle.count(AugmentType.ETHEREAL) > 0;
        CastContext.Landings landings = context.landings() != null ? context.landings() : CastContext.Landings.firstNative();
        CastContext payloadContext = new CastContext(null, landings, (float) (context.damageMultiplier() * Math.pow(0.5, splits)));

        for (Vec3 heading : ShotgunPattern.directions(direction, splits, VaultSpellbookConfig.SHOTGUN_SPREAD_DEGREES.get())) {
            BundleProjectile projectile = new BundleProjectile(player.level, player, bundle.payload(), payloadContext, scale, ethereal);
            if (bundle.color() != null) {
                int rgb = bundle.color().flatten(DEFAULT_RGB); // its colour is Ars's synced particle colour
                projectile.setColor(new ParticleColor.IntWrapper(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF));
            }
            projectile.setPos(start);
            projectile.setDeltaMovement(heading.scale(speed));
            player.level.addFreshEntity(projectile);
            if (bundle.count(AugmentType.ORBIT) > 0) {
                ProjectileSteering.orbit(projectile, player);
            } else if (bundle.count(AugmentType.HOMING) > 0) {
                ProjectileSteering.home(projectile, player);
            }
        }
    }
}
