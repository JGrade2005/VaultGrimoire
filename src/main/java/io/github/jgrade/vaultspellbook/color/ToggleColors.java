package io.github.jgrade.vaultspellbook.color;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import iskallia.vault.init.ModEntities;
import iskallia.vault.skill.ability.effect.spi.core.Ability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Colours for what a toggle spawns while it runs: Smite's bolts are spawned by VH's
// toggle ticks at the struck mob, long after the glyph's cast. A coloured Smite glyph that switches
// Smite on records its colour here; a Smite bolt that appears within 48 blocks of that player
// while their Smite is still on takes it.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class ToggleColors {
    private static final double RANGE = 48.0;
    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    private ToggleColors() {
    }

    // A toggle the glyph switched on: remember its colour if it spawns entities while on.
    public static void start(ServerPlayer player, AbilityGlyph glyph, @Nullable AbilityColor color) {
        if (color != null && AbilityVisuals.spawnsWhileOn(glyph)) {
            ACTIVE.put(player.getUUID(), new Session(player, glyph, color));
        }
    }

    @Nullable
    static AbilityColor colorFor(Entity entity) {
        if (ACTIVE.isEmpty() || entity.getType() != ModEntities.SMITE_ABILITY_BOLT) {
            return null;
        }
        Session nearest = null;
        double best = RANGE * RANGE;
        for (Session session : ACTIVE.values()) {
            if (!session.isRunning()) {
                ACTIVE.remove(session.player.getUUID());
            } else if (session.player.level == entity.level && session.player.distanceToSqr(entity) <= best) {
                best = session.player.distanceToSqr(entity);
                nearest = session;
            }
        }
        return nearest == null ? null : nearest.color;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getPlayer().getUUID());
    }

    private record Session(ServerPlayer player, AbilityGlyph glyph, AbilityColor color) {
        boolean isRunning() {
            return !player.isRemoved() && glyph.getAdapter().findAbility(player).filter(Ability::isActive).isPresent();
        }
    }
}
