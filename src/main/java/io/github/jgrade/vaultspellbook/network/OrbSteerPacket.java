package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.ClientOrbSteering;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: a steered Ball of Lightning's new direction and speed (Homing / Orbit).
public record OrbSteerPacket(int entityId, Vec3 direction, float speed) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeDouble(direction.x);
        buffer.writeDouble(direction.y);
        buffer.writeDouble(direction.z);
        buffer.writeFloat(speed);
    }

    public static OrbSteerPacket decode(FriendlyByteBuf buffer) {
        return new OrbSteerPacket(buffer.readVarInt(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientOrbSteering.apply(entityId, direction, speed)));
        context.get().setPacketHandled(true);
    }
}
