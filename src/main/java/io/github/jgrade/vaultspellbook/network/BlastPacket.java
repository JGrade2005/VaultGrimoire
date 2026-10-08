package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.BeamSounds;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: a charged beam is firing. For Arcane it is a keep-alive: the blast
// sound loops while these keep coming; for Rail it plays the blast once.
public record BlastPacket(int casterId, AbilityVisuals.BeamKind kind) {
    public static final int TTL_TICKS = 8;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(casterId);
        buffer.writeEnum(kind);
    }

    public static BlastPacket decode(FriendlyByteBuf buffer) {
        return new BlastPacket(buffer.readVarInt(), buffer.readEnum(AbilityVisuals.BeamKind.class));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BeamSounds.blast(casterId, kind)));
        context.get().setPacketHandled(true);
    }
}
