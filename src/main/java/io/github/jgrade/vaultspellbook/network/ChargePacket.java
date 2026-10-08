package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.ClientBeams;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: this player charges a beam for this many ticks before it fires.
public record ChargePacket(int casterId, AbilityVisuals.BeamKind kind, AbilityColor color, int ticks) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(casterId);
        buffer.writeEnum(kind);
        color.write(buffer);
        buffer.writeVarInt(ticks);
    }

    public static ChargePacket decode(FriendlyByteBuf buffer) {
        return new ChargePacket(buffer.readVarInt(), buffer.readEnum(AbilityVisuals.BeamKind.class), AbilityColor.read(buffer),
                buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBeams.charge(this)));
        context.get().setPacketHandled(true);
    }
}
