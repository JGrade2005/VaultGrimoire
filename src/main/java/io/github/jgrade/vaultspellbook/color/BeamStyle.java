package io.github.jgrade.vaultspellbook.color;

import java.util.Locale;

// How a coloured Arcane beam or Arcane Rail is drawn, picked in the colour tab. All are a
// solid beam whose hue pulses by the colour's hue swing, with effects at the caster's hand.
public enum BeamStyle {
    // A white-hot core in coloured layers, a flare at the hand, sparks blowing back.
    SOLID_CORE,
    // Solid core with two ribbons spiralling around it and lightning crackles.
    SPIRAL,
    // Solid core with shockwave rings travelling down it and energy drawn into the hand.
    RINGS,
    // Narrow at the hand and widening to the target, a spiky burst at the hand, lightning veins, smoke.
    CANNON;

    public String translationKey() {
        return "vaultspellbook.beam_style." + name().toLowerCase(Locale.ROOT);
    }

    public BeamStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
