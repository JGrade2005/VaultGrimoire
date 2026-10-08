package io.github.jgrade.vaultspellbook.cast;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Creative Loop: while on, the spell re-fires every server tick for a player in
// creative mode. Every repeat is an extra cast (no cooldown). Stops when the player leaves creative
// mode or logs out, or casts a Creative Loop spell again.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class CreativeLoops {
    private static final Map<UUID, SpellPlan> ACTIVE = new ConcurrentHashMap<>();

    private CreativeLoops() {
    }

    public static boolean isOn(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void start(ServerPlayer player, SpellPlan plan) {
        ACTIVE.put(player.getUUID(), plan);
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        SpellPlan plan = ACTIVE.get(player.getUUID());
        if (plan == null) {
            return;
        }
        if (!player.isCreative() || !player.isAlive()) {
            stop(player);
            return;
        }
        VaultCaster.runLoopIteration(player, plan);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getPlayer().getUUID());
    }
}
