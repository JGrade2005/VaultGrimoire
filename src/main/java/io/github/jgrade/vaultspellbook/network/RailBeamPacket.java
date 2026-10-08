package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.client.ClientBeams;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Server -> client: a coloured Arcane Rail bolt was fired along this line, power: charged.
public record RailBeamPacket(int casterId, AbilityColor color, Vec3 start, Vec3 direction, float length, float width, float power) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(casterId);
        color.write(buffer);
        writeVec(buffer, start);
        writeVec(buffer, direction);
        buffer.writeFloat(length);
        buffer.writeFloat(width);
        buffer.writeFloat(power);
    }

    public static RailBeamPacket decode(FriendlyByteBuf buffer) {
        return new RailBeamPacket(buffer.readVarInt(), AbilityColor.read(buffer), readVec(buffer), readVec(buffer),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBeams.rail(this)));
        context.get().setPacketHandled(true);
    }

    private static void writeVec(FriendlyByteBuf buffer, Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
