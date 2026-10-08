package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.AbilityEntityRenderers;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: draw this entity in this ability colour.
public record EntityColorPacket(int entityId, AbilityColor color) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        color.write(buffer);
    }

    public static EntityColorPacket decode(FriendlyByteBuf buffer) {
        return new EntityColorPacket(buffer.readVarInt(), AbilityColor.read(buffer));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityEntityRenderers.setColor(entityId, color)));
        context.get().setPacketHandled(true);
    }
}
