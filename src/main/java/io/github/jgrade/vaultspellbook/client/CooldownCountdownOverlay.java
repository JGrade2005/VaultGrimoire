package io.github.jgrade.vaultspellbook.client;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

// Action-bar countdown "Vault: Dash on cooldown for 3.21s". The server sends the remaining ticks
// once; the client counts game ticks (so it pauses with the game) and redraws every frame, using
// the partial tick for hundredths. It stops at 0, or when the server sends a stop packet.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, value = Dist.CLIENT)
public final class CooldownCountdownOverlay {
    private static Component glyphName;
    private static int remainingTicks;

    private CooldownCountdownOverlay() {
    }

    public static void start(String glyphNameKey, int ticks) {
        if (ticks <= 0) {
            stop(false);
            return;
        }
        glyphName = new TranslatableComponent(glyphNameKey);
        remainingTicks = ticks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || remainingTicks <= 0 || Minecraft.getInstance().isPaused()) {
            return;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            stop(true);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.START || remainingTicks <= 0 || minecraft.player == null) {
            return;
        }
        float partialTick = minecraft.isPaused() ? 0.0F : event.renderTickTime;
        float seconds = Math.max(0.0F, remainingTicks - partialTick) / 20.0F;
        minecraft.gui.setOverlayMessage(new TranslatableComponent("vaultspellbook.cast.on_cooldown_timer",
                glyphName, String.format(Locale.ROOT, "%.2f", seconds)), false);
    }

    // Another action-bar message (VH's, another mod's, or ours) takes the bar; the countdown stops instead of
    // overwriting it every frame.
    @SubscribeEvent
    public static void onActionBarMessage(ClientChatReceivedEvent event) {
        if (event.getType() == ChatType.GAME_INFO && remainingTicks > 0) {
            stop(false);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        stop(false);
    }

    private static void stop(boolean clearActionBar) {
        remainingTicks = 0;
        glyphName = null;
        if (clearActionBar && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().gui.setOverlayMessage(TextComponent.EMPTY, false);
        }
    }
}
