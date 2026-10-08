package io.github.jgrade.vaultspellbook.network;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    // Bump whenever a packet is added, removed or changes its encoding: client and server must match exactly.
    private static final String PROTOCOL_VERSION = "7";

    @SuppressWarnings("removal") // Forge 40 marks the two-arg constructor; it is still the standard way on 1.18.2
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VaultSpellbook.MOD_ID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, CooldownCountdownPacket.class, CooldownCountdownPacket::encode,
                CooldownCountdownPacket::decode, CooldownCountdownPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ProjectileScalePacket.class, ProjectileScalePacket::encode,
                ProjectileScalePacket::decode, ProjectileScalePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OrbSteerPacket.class, OrbSteerPacket::encode,
                OrbSteerPacket::decode, OrbSteerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, EntityColorPacket.class, EntityColorPacket::encode,
                EntityColorPacket::decode, EntityColorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, SetAbilityColorPacket.class, SetAbilityColorPacket::encode,
                SetAbilityColorPacket::decode, SetAbilityColorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, HoldBeamPacket.class, HoldBeamPacket::encode,
                HoldBeamPacket::decode, HoldBeamPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, RailBeamPacket.class, RailBeamPacket::encode,
                RailBeamPacket::decode, RailBeamPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, ChargePacket.class, ChargePacket::encode,
                ChargePacket::decode, ChargePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, BlastPacket.class, BlastPacket::encode,
                BlastPacket::decode, BlastPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }
}
