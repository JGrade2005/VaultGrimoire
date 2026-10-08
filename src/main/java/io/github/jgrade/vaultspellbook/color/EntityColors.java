package io.github.jgrade.vaultspellbook.color;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.augment.CastScope;
import io.github.jgrade.vaultspellbook.network.EntityColorPacket;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import iskallia.vault.entity.entity.VaultStormEntity;
import iskallia.vault.init.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Colours the entities an ability glyph's cast spawns, for everyone who sees them:
// the colour goes into the entity's persistent data (saved with it) and to every client that starts
// tracking it, where client.AbilityEntityRenderers draws it tinted.
// - Entities that join during the glyph's cast (CastScope) get its colour.
// - Non-living VH/WV entities that a coloured entity spawns later (a Storm Arrow's storm and
//   shards, Ball of Lightning's bolts, a necromancy summon's bolts) join within 6 blocks of it
//   and inherit its colour, as does a Decoy from its projectile. Mobs never inherit. Where VH records
//   both owners, they must match, so another player's entities nearby keep their own look.
// - Toggles spawn while they run (Smite's bolts): ToggleColors gives them the colour of the
//   glyph that switched the toggle on.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class EntityColors {
    public static final String COLOR_TAG = VaultSpellbook.MOD_ID + ":color";
    private static final double CHILD_RADIUS = 6.0;
    private static final Set<String> CHILD_NAMESPACES = Set.of("the_vault", "woldsvaults");
    // Live coloured entities that may still spawn children.
    private static final List<Entity> PARENTS = new ArrayList<>();

    private EntityColors() {
    }

    public static void apply(Entity entity, AbilityColor color) {
        entity.getPersistentData().put(COLOR_TAG, color.save());
        PARENTS.add(entity);
    }

    @Nullable
    public static AbilityColor colorOf(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(COLOR_TAG) ? AbilityColor.load(data.getCompound(COLOR_TAG)) : null;
    }

    // After AugmentEvents (which may move the entity to a Bundled Spell's landing point).
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        // Only VH/WV entities are drawn coloured (client.AbilityEntityRenderers), and reading any other entity's
        // persistent data would create an empty ForgeData tag that is then saved with it.
        if (event.getWorld().isClientSide() || event.loadedFromDisk() || !canBeColored(entity) || !isVaultEntity(entity)) {
            return;
        }
        AbilityColor color;
        if (CastScope.current().isPresent()) {
            color = CastScope.current().get().modifiers().color(); // another glyph's cast: its colour or none
        } else if (!(entity instanceof LivingEntity) || entity.getType() == ModEntities.DECOY) {
            color = parentColor(entity);
            if (color == null) {
                color = ToggleColors.colorFor(entity);
            }
        } else {
            color = null;
        }
        if (color != null && colorOf(entity) == null) {
            apply(entity, color);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        AbilityColor color = isVaultEntity(target) ? colorOf(target) : null;
        if (color != null && event.getPlayer() instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new EntityColorPacket(target.getId(), color));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !PARENTS.isEmpty()) {
            PARENTS.removeIf(Entity::isRemoved);
        }
    }

    // Holds entities (and so their level): a closed singleplayer world must not stay reachable through it.
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PARENTS.clear();
    }

    @Nullable
    private static AbilityColor parentColor(Entity child) {
        Entity nearest = null;
        double best = CHILD_RADIUS * CHILD_RADIUS;
        for (Entity parent : PARENTS) {
            if (parent.level == child.level && !parent.isRemoved() && sameOwner(parent, child)) {
                double distance = parent.distanceToSqr(child);
                if (distance <= best) {
                    best = distance;
                    nearest = parent;
                }
            }
        }
        return nearest == null ? null : colorOf(nearest);
    }

    // True unless both owners are known and differ. VH records owners on projectiles and its storm cloud;
    // others (e.g. Smite bolts) have none, and only distance decides for them.
    private static boolean sameOwner(Entity parent, Entity child) {
        Entity parentOwner = ownerOf(parent);
        Entity childOwner = ownerOf(child);
        return parentOwner == null || childOwner == null || parentOwner == childOwner;
    }

    @Nullable
    private static Entity ownerOf(Entity entity) {
        if (entity instanceof Projectile projectile) {
            return projectile.getOwner();
        }
        return entity instanceof VaultStormEntity storm ? storm.getOwner() : null;
    }

    private static boolean canBeColored(Entity entity) {
        return !(entity instanceof Player) && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb);
    }

    private static boolean isVaultEntity(Entity entity) {
        ResourceLocation id = entity.getType().getRegistryName();
        return id != null && CHILD_NAMESPACES.contains(id.getNamespace());
    }
}
