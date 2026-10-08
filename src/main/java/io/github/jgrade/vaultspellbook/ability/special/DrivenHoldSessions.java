package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.augment.CastModifiers;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Hold abilities (Arcane, Levitate) a player switched on with a glyph. Server-side,
// in memory; they end on logout and on death, like a released key.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class DrivenHoldSessions {
    // player -> (specialization id -> augments of the cast that switched it on: Discount, beam shape)
    private static final Map<UUID, Map<String, CastModifiers>> ACTIVE = new ConcurrentHashMap<>();
    // player -> (specialization id -> game time it fired) for holds started charged: blast sound, damage curve.
    private static final Map<UUID, Map<String, Long>> CHARGED = new ConcurrentHashMap<>();

    private DrivenHoldSessions() {
    }

    public static boolean isOn(UUID player, String specializationId) {
        return ACTIVE.getOrDefault(player, Map.of()).containsKey(specializationId);
    }

    public static void start(UUID player, String specializationId) {
        ACTIVE.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(specializationId, CastModifiers.NONE);
    }

    // Augments on a running session (Discount, beam shape); no effect if it is not running.
    public static void setModifiers(UUID player, String specializationId, CastModifiers modifiers) {
        Map<String, CastModifiers> sessions = ACTIVE.get(player);
        if (sessions != null) {
            sessions.computeIfPresent(specializationId, (id, old) -> modifiers);
        }
    }

    public static void markCharged(UUID player, String specializationId, long gameTime) {
        if (isOn(player, specializationId)) {
            CHARGED.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).put(specializationId, gameTime);
        }
    }

    // When a charged hold fired, or null if it wasn't charged.
    @Nullable
    public static Long chargedSince(UUID player, String specializationId) {
        return CHARGED.getOrDefault(player, Map.of()).get(specializationId);
    }

    public static void stop(UUID player, String specializationId) {
        Map<String, Long> charged = CHARGED.get(player);
        if (charged != null) {
            charged.remove(specializationId);
        }
        Map<String, CastModifiers> sessions = ACTIVE.get(player);
        if (sessions != null) {
            sessions.remove(specializationId);
            if (sessions.isEmpty()) {
                ACTIVE.remove(player);
            }
        }
    }

    // A snapshot (specialization id -> augments), so the ticker can stop sessions while iterating.
    public static Map<String, CastModifiers> get(UUID player) {
        return Map.copyOf(ACTIVE.getOrDefault(player, Map.of()));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getPlayer().getUUID());
        CHARGED.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        ACTIVE.remove(event.getEntityLiving().getUUID());
        CHARGED.remove(event.getEntityLiving().getUUID());
    }
}
