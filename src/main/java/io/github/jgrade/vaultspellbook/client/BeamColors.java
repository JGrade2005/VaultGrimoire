package io.github.jgrade.vaultspellbook.client;

import net.minecraft.util.Mth;

// Beam colour maths: the hue pulse and the colours of a beam's glow layers, from the
// beam's base colour. Colours are 0xRRGGBB.
public final class BeamColors {
    // Pulse speed: about 0.8 pulses per second, as in the mock-ups.
    private static final double PULSE_RATE = 5.2;

    private BeamColors() {
    }

    // The base colour with its hue shifted by up to swingDegrees either way, over time.
    public static int pulse(int rgb, int swingDegrees, double seconds) {
        return shiftHue(rgb, (float) (swingDegrees * Math.sin(seconds * PULSE_RATE)));
    }

    public static int shiftHue(int rgb, float degrees) {
        float[] hsv = toHsv(rgb);
        float hue = hsv[0] + degrees / 360.0F;
        return Mth.hsvToRgb(hue - (float) Math.floor(hue), hsv[1], hsv[2]) & 0xFFFFFF;
    }

    // The same hue at a given share of its saturation and full brightness (outer glow -> white core).
    public static int layer(int rgb, float saturationShare) {
        float[] hsv = toHsv(rgb);
        return Mth.hsvToRgb(hsv[0], hsv[1] * saturationShare, Math.max(0.6F, hsv[2])) & 0xFFFFFF;
    }

    public static float[] toHsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0.0F;
        if (delta > 0) {
            if (max == r) {
                hue = ((g - b) / delta) % 6.0F;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0F;
            } else {
                hue = (r - g) / delta + 4.0F;
            }
            hue /= 6.0F;
            if (hue < 0) {
                hue += 1.0F;
            }
        }
        return new float[]{hue, max == 0 ? 0 : delta / max, max};
    }
}
