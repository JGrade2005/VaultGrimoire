package io.github.jgrade.vaultspellbook.cast;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.ability.AbilityAdapter;
import io.github.jgrade.vaultspellbook.ability.CastResult;
import io.github.jgrade.vaultspellbook.ability.VaultAbilities;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Loop: an ability glyph followed by Loop re-casts itself every time its cooldown ends, as a normal
// cast (mana and cooldown paid), while the player goes on casting other spells. Delay glyphs at the
// end of the spell add a wait before each repeat. Loops belong to the book's spell slot that started
// them; casting that slot again switches them off. They also end on death or logout, when the glyph
// can no longer be cast (not learned, disabled), or after 3 failed casts in a row
// (e.g. out of mana; a failed cast is retried after a second). Server-side, in memory.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID)
public final class AbilityLoops {
    private static final int MAX_FAILURES = 3;
    private static final int RETRY_TICKS = 20;
    // player -> spell slot -> looping glyphs of that spell.
    private static final Map<UUID, Map<Integer, List<Entry>>> LOOPS = new ConcurrentHashMap<>();

    private AbilityLoops() {
    }

    public static boolean isRunning(UUID player, int slot) {
        return !LOOPS.getOrDefault(player, Map.of()).getOrDefault(slot, List.of()).isEmpty();
    }

    // Loops for these glyphs of a spell slot; each starts once the spell's own cast reaches it.
    public static void start(ServerPlayer player, int slot, List<SpellPlan.Cast> casts, int waitTicks) {
        List<Entry> entries = new ArrayList<>();
        for (SpellPlan.Cast cast : casts) {
            entries.add(new Entry(cast, waitTicks));
        }
        LOOPS.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>()).put(slot, entries);
    }

    public static void stop(UUID player, int slot) {
        Map<Integer, List<Entry>> slots = LOOPS.get(player);
        if (slots != null) {
            slots.remove(slot);
            if (slots.isEmpty()) {
                LOOPS.remove(player);
            }
        }
    }

    // The spell's own cast reached a looping glyph: from now on it re-casts when off cooldown.
    static void arm(ServerPlayer player, SpellPlan.Cast cast) {
        for (List<Entry> entries : LOOPS.getOrDefault(player.getUUID(), Map.of()).values()) {
            for (Entry entry : entries) {
                if (entry.cast == cast) {
                    entry.armed = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Map<Integer, List<Entry>> slots = LOOPS.get(player.getUUID());
        if (slots == null) {
            return;
        }
        boolean allowedHere = VaultCaster.worksHere(player);
        for (Iterator<Map.Entry<Integer, List<Entry>>> slotIt = slots.entrySet().iterator(); slotIt.hasNext(); ) {
            List<Entry> entries = slotIt.next().getValue();
            entries.removeIf(entry -> entry.armed && allowedHere && !tick(player, entry));
            if (entries.isEmpty()) {
                slotIt.remove();
                VaultCaster.showMessage(player, new TranslatableComponent("vaultspellbook.cast.loop_off"));
            }
        }
        if (slots.isEmpty()) {
            LOOPS.remove(player.getUUID());
        }
    }

    // Advances one loop; false when it ends.
    private static boolean tick(ServerPlayer player, Entry entry) {
        if (entry.busy > 0) {
            entry.busy--;
            return true;
        }
        AbilityAdapter adapter = entry.cast.ability().getAdapter();
        if (adapter.getRemainingCooldownTicks(VaultAbilities.getServerTree(player)) > 0) {
            entry.wait = -1;
            return true;
        }
        if (entry.wait < 0) {
            entry.wait = entry.waitTicks;
        }
        if (entry.wait > 0) {
            entry.wait--;
            return true;
        }
        entry.wait = -1;
        VaultCaster.LoopCast cast = VaultCaster.castLooped(player, entry.cast);
        entry.busy = cast.busyTicks();
        return switch (cast.result()) {
            case SUCCESS, STARTED, ON_COOLDOWN -> {
                entry.failures = 0;
                yield true;
            }
            case FAILED, BLOCKED -> {
                entry.busy = Math.max(entry.busy, RETRY_TICKS);
                yield ++entry.failures < MAX_FAILURES;
            }
            default -> false; // not learned, disabled, unsupported, or a toggle switched off
        };
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LOOPS.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LOOPS.remove(event.getEntityLiving().getUUID());
    }

    private static final class Entry {
        final SpellPlan.Cast cast;
        final int waitTicks;
        boolean armed;
        int wait = -1;
        int busy;
        int failures;

        Entry(SpellPlan.Cast cast, int waitTicks) {
            this.cast = cast;
            this.waitTicks = waitTicks;
        }
    }
}
