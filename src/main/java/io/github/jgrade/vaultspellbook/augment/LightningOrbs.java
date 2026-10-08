package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import iskallia.vault.entity.entity.LightningOrbEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Set;

// Homing and Orbit on VH's Ball of Lightning. The orb sets its own motion every tick
// from a private direction and its synced SPEED, so changing its motion does nothing; steering sets
// that direction (and, for Orbit, the speed) instead, on the server and on tracking clients (which
// move their copy the same way). The fields are VH's own (not obfuscated); on failure the orb flies
// straight as before.
public final class LightningOrbs {
    private static final Field DIRECTION = field("direction");
    private static final Field HIT_ENTITIES = field("hitEntities");
    private static final EntityDataAccessor<Float> SPEED = speedAccessor();

    private LightningOrbs() {
    }

    public static boolean canSteer() {
        return DIRECTION != null && SPEED != null;
    }

    public static void steer(LightningOrbEntity orb, Vec3 direction, float speed) {
        if (!canSteer() || direction.lengthSqr() < 1.0E-8) {
            return;
        }
        try {
            DIRECTION.set(orb, direction.normalize());
            orb.getEntityData().set(SPEED, speed);
        } catch (IllegalAccessException e) {
            VaultSpellbook.LOGGER.error("Could not steer {}", orb, e);
        }
    }

    // Entity ids the orb already damaged; it never hits them again, so Homing skips them.
    @SuppressWarnings("unchecked")
    public static Set<Integer> alreadyHit(LightningOrbEntity orb) {
        try {
            return HIT_ENTITIES == null ? Set.of() : (Set<Integer>) HIT_ENTITIES.get(orb);
        } catch (IllegalAccessException e) {
            return Set.of();
        }
    }

    private static Field field(String name) {
        try {
            Field field = LightningOrbEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VaultSpellbook.LOGGER.error("LightningOrbEntity.{} not found; Homing/Orbit can't steer Ball of Lightning", name, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Float> speedAccessor() {
        Field field = field("SPEED");
        try {
            return field == null ? null : (EntityDataAccessor<Float>) field.get(null);
        } catch (IllegalAccessException e) {
            VaultSpellbook.LOGGER.error("LightningOrbEntity.SPEED not readable", e);
            return null;
        }
    }
}
