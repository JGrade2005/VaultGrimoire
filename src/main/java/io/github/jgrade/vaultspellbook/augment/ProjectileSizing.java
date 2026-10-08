package io.github.jgrade.vaultspellbook.augment;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import io.github.jgrade.vaultspellbook.network.ProjectileScalePacket;
import iskallia.vault.entity.entity.LightningOrbEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Size Up / Size Down on VH ability projectiles. The hitbox follows Forge's
// EntityEvent.Size; clients learn the scale when they start tracking the projectile and draw it
// scaled (client.AbilityEntityRenderers). Vanilla projectiles find entities with a thin ray, not
// their hitbox, so an enlarged projectile also hits any entity touching its enlarged box: the same
// impact event and onHit the projectile's own collision uses (Projectile.onHit / canHitEntity are
// protected; read once through Forge's ObfuscationReflectionHelper). The box swept since the last tick
// counts, so a fast projectile can't skip past an entity between two ticks. Clients get the scale too,
// so their copy of the hitbox (F3+B) matches. VH's Ball of Lightning damages by its own synced radius
// instead of its hitbox, so its radius is scaled (which also resizes its box).
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class ProjectileSizing {
    public static final String SCALE_TAG = VaultSpellbook.MOD_ID + ":scale";
    private static final Method ON_HIT = findMethod("m_6532_", HitResult.class);
    private static final Method CAN_HIT_ENTITY = findMethod("m_5603_", Entity.class);
    // Enlarged projectiles and the entities each one already hit.
    private static final Map<Projectile, Set<Entity>> ENLARGED = new WeakHashMap<>();

    private ProjectileSizing() {
    }

    public static void apply(Projectile projectile, float scale) {
        projectile.getPersistentData().putFloat(SCALE_TAG, scale);
        if (projectile instanceof LightningOrbEntity orb) {
            orb.setRadius(orb.getRadius() * scale); // its damage reach and box both follow the radius
            return;
        }
        projectile.refreshDimensions();
        if (scale > 1.0F) {
            ENLARGED.put(projectile, new HashSet<>());
        }
    }

    public static float scaleOf(Entity entity) {
        return entity.getPersistentData().contains(SCALE_TAG) ? entity.getPersistentData().getFloat(SCALE_TAG) : 1.0F;
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Projectile && !(entity instanceof LightningOrbEntity) && entity.getPersistentData().contains(SCALE_TAG)) {
            event.setNewSize(event.getNewSize().scale(scaleOf(entity)), true);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (target instanceof Projectile && target.getPersistentData().contains(SCALE_TAG)
                && event.getPlayer() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new ProjectileScalePacket(target.getId(), scaleOf(target)));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENLARGED.isEmpty() || ON_HIT == null || CAN_HIT_ENTITY == null) {
            return;
        }
        for (Iterator<Map.Entry<Projectile, Set<Entity>>> it = ENLARGED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Projectile, Set<Entity>> entry = it.next();
            Projectile projectile = entry.getKey();
            if (projectile.isRemoved()) {
                it.remove();
            } else if (projectile.getDeltaMovement().lengthSqr() > 1.0E-4) { // still flying, not stuck in a block
                hitTouchingEntities(projectile, entry.getValue());
            }
        }
    }

    private static void hitTouchingEntities(Projectile projectile, Set<Entity> alreadyHit) {
        AABB box = projectile.getBoundingBox();
        AABB swept = box.minmax(box.move(projectile.xo - projectile.getX(), projectile.yo - projectile.getY(),
                projectile.zo - projectile.getZ()));
        // Vanilla's canHitEntity rejects spectators, dead and non-pickable entities (items, XP) first; doing that
        // here keeps the reflective call for entities the projectile can really hit.
        for (Entity target : projectile.level.getEntities(projectile, swept,
                e -> !e.isSpectator() && e.isAlive() && e.isPickable() && !alreadyHit.contains(e))) {
            if (projectile.isRemoved()) {
                return;
            }
            if (!invokeBoolean(CAN_HIT_ENTITY, projectile, target)) {
                continue;
            }
            alreadyHit.add(target);
            EntityHitResult hit = new EntityHitResult(target);
            if (!ForgeEventFactory.onProjectileImpact(projectile, hit)) {
                invoke(ON_HIT, projectile, hit);
            }
        }
    }

    private static Method findMethod(String srgName, Class<?> parameter) {
        try {
            return ObfuscationReflectionHelper.findMethod(Projectile.class, srgName, parameter);
        } catch (RuntimeException e) {
            VaultSpellbook.LOGGER.error("Projectile.{} not found; enlarged projectiles only hit through their own collision", srgName, e);
            return null;
        }
    }

    private static boolean invokeBoolean(Method method, Projectile projectile, Object argument) {
        Object result = invoke(method, projectile, argument);
        return result instanceof Boolean value && value;
    }

    private static Object invoke(Method method, Projectile projectile, Object argument) {
        try {
            return method.invoke(projectile, argument);
        } catch (ReflectiveOperationException e) {
            VaultSpellbook.LOGGER.error("Could not call {} on {}", method.getName(), projectile, e);
            return null;
        }
    }
}
