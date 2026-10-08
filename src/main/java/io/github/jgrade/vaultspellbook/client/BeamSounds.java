package io.github.jgrade.vaultspellbook.client;

import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.network.BlastPacket;
import io.github.jgrade.vaultspellbook.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

// The beam charge-up sounds. The files are stereo, which Minecraft plays without 3D
// placement, so each sound sets its own volume from the listener's distance to the caster (silent from
// 32 blocks). The charge plays for the charge's length; an Arcane blast loops while the
// server keeps it alive; a Rail blast plays briefly. All fade out instead of cutting off.
public final class BeamSounds {
    private static final double RANGE = 32.0;
    private static final float VOLUME = 0.9F;
    private static final int FADE_TICKS = 6;
    private static final int RAIL_BLAST_TICKS = 30;
    private static final Map<Integer, CasterSound> BLASTS = new HashMap<>();
    private static final Map<Integer, CasterSound> CHARGES = new HashMap<>();

    private BeamSounds() {
    }

    public static void charge(int casterId, int ticks) {
        CasterSound previous = CHARGES.remove(casterId);
        if (previous != null) {
            previous.fadeOut();
        }
        start(CHARGES, casterId, ModSounds.ARCANE_CHARGE.get(), false, ticks);
    }

    public static void blast(int casterId, AbilityVisuals.BeamKind kind) {
        CasterSound charge = CHARGES.remove(casterId);
        if (charge != null) {
            charge.fadeOut();
        }
        CasterSound playing = BLASTS.get(casterId);
        if (kind == AbilityVisuals.BeamKind.ARCANE && playing != null && !playing.isStopped()) {
            playing.keepFor(BlastPacket.TTL_TICKS);
            return;
        }
        boolean loop = kind == AbilityVisuals.BeamKind.ARCANE;
        start(BLASTS, casterId, ModSounds.ARCANE_BLAST.get(), loop, loop ? BlastPacket.TTL_TICKS : RAIL_BLAST_TICKS);
    }

    private static void start(Map<Integer, CasterSound> sounds, int casterId, SoundEvent event, boolean loop, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(casterId) == null) {
            return;
        }
        sounds.values().removeIf(CasterSound::isStopped);
        CasterSound sound = new CasterSound(event, casterId, loop, ticks);
        sounds.put(casterId, sound);
        minecraft.getSoundManager().play(sound);
    }

    // A sound tied to a caster: plays for a set time (extendable), follows them for its volume, then fades.
    private static final class CasterSound extends AbstractTickableSoundInstance {
        private final int casterId;
        private int remaining;
        private int fade = -1;

        CasterSound(SoundEvent event, int casterId, boolean loop, int ticks) {
            super(event, SoundSource.PLAYERS);
            this.casterId = casterId;
            this.remaining = ticks;
            this.looping = loop;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = false;
            this.volume = distanceVolume();
        }

        // Far listeners start at volume 0 and fade in as they come closer.
        @Override
        public boolean canStartSilent() {
            return true;
        }

        void keepFor(int ticks) {
            remaining = Math.max(remaining, ticks);
            fade = -1;
        }

        void fadeOut() {
            if (fade < 0) {
                fade = FADE_TICKS;
            }
        }

        @Override
        public void tick() {
            Entity caster = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(casterId);
            if (caster == null) {
                stop();
                return;
            }
            x = caster.getX();
            y = caster.getY();
            z = caster.getZ();
            if (fade < 0 && --remaining <= 0) {
                fade = FADE_TICKS;
            }
            float share = fade < 0 ? 1.0F : fade / (float) FADE_TICKS;
            volume = distanceVolume() * share;
            if (fade >= 0 && fade-- <= 0) {
                stop();
            }
        }

        private float distanceVolume() {
            Minecraft minecraft = Minecraft.getInstance();
            Entity caster = minecraft.level == null ? null : minecraft.level.getEntity(casterId);
            Entity listener = minecraft.getCameraEntity();
            if (caster == null || listener == null) {
                return 0.0F;
            }
            double distance = listener.distanceTo(caster);
            return (float) Math.max(0.0, 1.0 - distance / RANGE) * VOLUME;
        }
    }
}
