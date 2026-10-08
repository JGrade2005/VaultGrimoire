package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.CooldownCountdownOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: show a live "on cooldown" countdown for a glyph in the action bar.
// remainingTicks 0 stops any running countdown.
public record CooldownCountdownPacket(String glyphNameKey, int remainingTicks) {
    public static CooldownCountdownPacket stop() {
        return new CooldownCountdownPacket("", 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(glyphNameKey);
        buffer.writeVarInt(remainingTicks);
    }

    public static CooldownCountdownPacket decode(FriendlyByteBuf buffer) {
        return new CooldownCountdownPacket(buffer.readUtf(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CooldownCountdownOverlay.start(glyphNameKey, remainingTicks)));
        context.get().setPacketHandled(true);
    }
}
