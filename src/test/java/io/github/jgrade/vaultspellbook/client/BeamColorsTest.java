package io.github.jgrade.vaultspellbook.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamColorsTest {
    @Test
    void redIsHueZeroFullySaturatedAndBright() {
        assertArrayEquals(new float[]{0.0F, 1.0F, 1.0F}, BeamColors.toHsv(0xFF0000), 1.0E-6F);
    }

    @Test
    void noShiftKeepsTheColour() {
        assertClose(0x5FB216, BeamColors.shiftHue(0x5FB216, 0.0F));
    }

    @Test
    void shiftingByAThirdTurnsRedGreen() {
        assertClose(0x00FF00, BeamColors.shiftHue(0xFF0000, 120.0F));
    }

    @Test
    void pulseStaysWithinTheSwing() {
        for (double seconds = 0; seconds < 2; seconds += 0.05) {
            float hue = BeamColors.toHsv(BeamColors.pulse(0xFF0000, 20, seconds))[0] * 360.0F;
            float fromRed = Math.min(hue, 360.0F - hue);
            assertTrue(fromRed <= 20.5F, "hue " + hue + " at " + seconds + " s");
        }
    }

    // HSV round trips may be one off per channel.
    private static void assertClose(int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            assertEquals(expected >> shift & 0xFF, actual >> shift & 0xFF, 1, "channel at bit " + shift);
        }
    }
}
