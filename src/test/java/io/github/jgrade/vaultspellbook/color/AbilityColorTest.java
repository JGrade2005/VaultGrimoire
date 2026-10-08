package io.github.jgrade.vaultspellbook.color;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbilityColorTest {
    @Test
    void constructorKeepsValuesInRange() {
        AbilityColor color = new AbilityColor(AbilityColor.Mode.FILTER, 0x1FF0000, 2.0F, null, 99);
        assertEquals(0xFF0000, color.rgb());
        assertEquals(1.0F, color.strength());
        assertEquals(BeamStyle.SOLID_CORE, color.style());
        assertEquals(AbilityColor.MAX_HUE_SWING, color.hueSwing());
    }

    @Test
    void nonFiniteStrengthBecomesFull() {
        assertEquals(1.0F, new AbilityColor(AbilityColor.Mode.FILTER, 0, Float.NaN).strength());
        assertEquals(1.0F, new AbilityColor(AbilityColor.Mode.FILTER, 0, Float.POSITIVE_INFINITY).strength());
    }

    @Test
    void whiteFilterLeavesTheDefaultColour() {
        assertEquals(0x5FB216, new AbilityColor(AbilityColor.Mode.FILTER, 0xFFFFFF, 1.0F).flatten(0x5FB216));
    }

    @Test
    void fullRecolorIsTheChosenColour() {
        assertEquals(0x3366CC, new AbilityColor(AbilityColor.Mode.RECOLOR, 0x3366CC, 1.0F).flatten(0x5FB216));
    }

    @Test
    void zeroStrengthRecolorIsTheDefaultsGrey() {
        int grey = (int) (0.299 * 0xFF + 0.587 * 0x80 + 0.114 * 0x00);
        int flat = new AbilityColor(AbilityColor.Mode.RECOLOR, 0x3366CC, 0.0F).flatten(0xFF8000);
        assertEquals(grey << 16 | grey << 8 | grey, flat);
    }

    @Test
    void savesAndLoads() {
        AbilityColor color = new AbilityColor(AbilityColor.Mode.RECOLOR, 0x123456, 0.4F, BeamStyle.RINGS, 22);
        assertEquals(color, AbilityColor.load(color.save()));
    }

    @Test
    void unknownModeLoadsAsNoColour() {
        CompoundTag tag = new AbilityColor(AbilityColor.Mode.FILTER, 0, 1.0F).save();
        tag.putString("mode", "SPARKLE");
        assertNull(AbilityColor.load(tag));
    }
}
