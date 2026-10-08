package io.github.jgrade.vaultspellbook.client;

import io.github.jgrade.vaultspellbook.augment.LightningOrbs;
import iskallia.vault.entity.entity.LightningOrbEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Applies OrbSteerPacket to the client's copy of a Ball of Lightning, so it moves where the server's does.
public final class ClientOrbSteering {
    private ClientOrbSteering() {
    }

    public static void apply(int entityId, Vec3 direction, float speed) {
        Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof LightningOrbEntity orb) {
            LightningOrbs.steer(orb, direction, speed);
        }
    }
}
