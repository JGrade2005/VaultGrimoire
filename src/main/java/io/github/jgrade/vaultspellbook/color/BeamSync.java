package io.github.jgrade.vaultspellbook.color;

import io.github.jgrade.vaultspellbook.augment.BeamGeometry;
import io.github.jgrade.vaultspellbook.network.BlastPacket;
import io.github.jgrade.vaultspellbook.network.ChargePacket;
import io.github.jgrade.vaultspellbook.network.HoldBeamPacket;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import io.github.jgrade.vaultspellbook.network.RailBeamPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Tells clients to draw coloured beams; damage stays on the server (ArcaneBeams, VH's
// Arcane Rail). A held Arcane beam is kept alive: the server resends its shape when it changes or every
// 3 ticks, and clients drop it 6 ticks after the last one, so a stopped beam fades out without a
// stop message. Clients aim the held beam with the caster's own view each frame, so it follows
// smoothly. A Rail bolt is sent once, along the line VH fired it.
public final class BeamSync {
    private static final int KEEP_ALIVE_TICKS = 3;
    private static final Map<UUID, Sent> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_BLAST = new ConcurrentHashMap<>();

    private BeamSync() {
    }

    public static void hold(ServerPlayer player, AbilityColor color, float radius, float range, boolean ethereal,
                            int splits, float spread, int pierce, float power) {
        HoldBeamPacket packet = new HoldBeamPacket(player.getId(), color, radius, range, ethereal, splits, spread, pierce, power);
        Sent last = LAST.get(player.getUUID());
        if (last == null || !last.packet().equals(packet) || player.tickCount - last.tick() >= KEEP_ALIVE_TICKS
                || player.tickCount < last.tick()) {
            LAST.put(player.getUUID(), new Sent(packet, player.tickCount));
            ModNetwork.sendToTrackingAndSelf(player, packet);
        }
    }

    // A coloured Arcane Rail just fired along this aim: send the bolt VH drew (from its start to the block
    // it hit), as wide as its hit area (rail-size etching, plus Size Up's extra radius).
    public static void rail(ServerPlayer player, AbilityColor color, Vec3 aim, double extraRadius, float power) {
        Vec3 start = BeamGeometry.start(player);
        Vec3 direction = aim.normalize();
        double length = BeamGeometry.railLength(player, start, direction);
        ModNetwork.sendToTrackingAndSelf(player, new RailBeamPacket(player.getId(), color, start, direction, (float) length,
                (float) BeamGeometry.railRadius(player, extraRadius), power));
    }

    // Charge on a beam glyph winds it up: clients show the charge at the
    // caster's hand and play the charge sound for that long. Uncoloured beams charge in their default
    // colour (a white full-strength filter leaves the default as is) and the Solid core style.
    public static void charge(ServerPlayer player, AbilityVisuals.BeamKind kind, @Nullable AbilityColor color, int ticks) {
        AbilityColor shown = color != null ? color : new AbilityColor(AbilityColor.Mode.FILTER, 0xFFFFFF, 1.0F);
        ModNetwork.sendToTrackingAndSelf(player, new ChargePacket(player.getId(), kind, shown, ticks));
    }

    // A charged beam fires: the blast sound plays (Rail) or keeps looping while these keep coming (Arcane).
    public static void blast(ServerPlayer player, AbilityVisuals.BeamKind kind) {
        if (kind == AbilityVisuals.BeamKind.ARCANE) {
            Integer last = LAST_BLAST.get(player.getUUID());
            if (last != null && player.tickCount - last < KEEP_ALIVE_TICKS && player.tickCount >= last) {
                return;
            }
            LAST_BLAST.put(player.getUUID(), player.tickCount);
        }
        ModNetwork.sendToTrackingAndSelf(player, new BlastPacket(player.getId(), kind));
    }

    private record Sent(HoldBeamPacket packet, int tick) {
    }
}
