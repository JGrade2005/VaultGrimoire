package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.AbilityEntityRenderers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: draw this projectile at this scale (Size Up / Size Down).
public record ProjectileScalePacket(int entityId, float scale) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(scale);
    }

    public static ProjectileScalePacket decode(FriendlyByteBuf buffer) {
        return new ProjectileScalePacket(buffer.readVarInt(), buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AbilityEntityRenderers.setScale(entityId, scale)));
        context.get().setPacketHandled(true);
    }
}
