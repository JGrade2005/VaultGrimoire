package io.github.jgrade.vaultspellbook.ability.special;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Which players have glyph vein mining switched on, and for which Vein Miner specialization.
// Server-side, in memory: it ends when the player logs out.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class VeinMinerSessions {
    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

    private VeinMinerSessions() {
    }

    public static Optional<String> get(UUID player) {
        return Optional.ofNullable(ACTIVE.get(player));
    }

    public static void start(UUID player, String specializationId) {
        ACTIVE.put(player, specializationId);
    }

    public static void stop(UUID player) {
        ACTIVE.remove(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        stop(event.getPlayer().getUUID());
    }
}
