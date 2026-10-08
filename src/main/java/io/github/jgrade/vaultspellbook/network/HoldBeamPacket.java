package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.ClientBeams;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: this player's coloured Arcane beam is firing with this shape. Clients
// keep drawing it for 6 ticks after the last one. range is how far it hits;
// clients draw it out to their render distance. power is a charged beam's damage multiplier.
public record HoldBeamPacket(int casterId, AbilityColor color, float radius, float range, boolean ethereal,
                             int splits, float spread, int pierce, float power) {
    public static final int TTL_TICKS = 6;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(casterId);
        color.write(buffer);
        buffer.writeFloat(radius);
        buffer.writeFloat(range);
        buffer.writeBoolean(ethereal);
        buffer.writeVarInt(splits);
        buffer.writeFloat(spread);
        buffer.writeVarInt(pierce);
        buffer.writeFloat(power);
    }

    public static HoldBeamPacket decode(FriendlyByteBuf buffer) {
        return new HoldBeamPacket(buffer.readVarInt(), AbilityColor.read(buffer), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(), buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBeams.hold(this)));
        context.get().setPacketHandled(true);
    }
}
