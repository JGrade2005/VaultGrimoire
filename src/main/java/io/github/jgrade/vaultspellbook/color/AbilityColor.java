package io.github.jgrade.vaultspellbook.color;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

// A colour chosen for one ability glyph in one spell. Filter lays the colour over the
// entity's own texture; Recolor first turns the texture grey, then paints the colour (where the texture
// can be swapped; otherwise it falls back to a filter). Strength blends from no change (0) to the full
// colour (1). Beam abilities (Arcane, Arcane Rail) also have a beam style and a hue swing: how many
// degrees the beam's hue pulses either way.
public record AbilityColor(Mode mode, int rgb, float strength, BeamStyle style, int hueSwing) {
    public static final int DEFAULT_HUE_SWING = 14;
    public static final int MAX_HUE_SWING = 40;

    public enum Mode {
        FILTER,
        RECOLOR
    }

    public AbilityColor {
        rgb &= 0xFFFFFF;
        strength = Float.isFinite(strength) ? Mth.clamp(strength, 0.0F, 1.0F) : 1.0F; // NaN would pass clamp
        style = style == null ? BeamStyle.SOLID_CORE : style;
        hueSwing = Mth.clamp(hueSwing, 0, MAX_HUE_SWING);
    }

    public AbilityColor(Mode mode, int rgb, float strength) {
        this(mode, rgb, strength, BeamStyle.SOLID_CORE, DEFAULT_HUE_SWING);
    }

    public int red() {
        return rgb >> 16 & 0xFF;
    }

    public int green() {
        return rgb >> 8 & 0xFF;
    }

    public int blue() {
        return rgb & 0xFF;
    }

    // Multiplier for one vertex colour channel (0-255 target): 1 at strength 0, target/255 at strength 1.
    public float factor(int channel) {
        return 1.0F + (channel / 255.0F - 1.0F) * strength;
    }

    // A flat colour for things drawn in a single colour (particles, the Arcane beam, the Bundled Spell):
    // Filter tints the default colour, Recolor blends from the default's grey to the chosen colour.
    public int flatten(int defaultRgb) {
        int r = defaultRgb >> 16 & 0xFF;
        int g = defaultRgb >> 8 & 0xFF;
        int b = defaultRgb & 0xFF;
        if (mode == Mode.RECOLOR) {
            int grey = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            return lerp(grey, red()) << 16 | lerp(grey, green()) << 8 | lerp(grey, blue());
        }
        return (int) (r * factor(red())) << 16 | (int) (g * factor(green())) << 8 | (int) (b * factor(blue()));
    }

    private int lerp(int from, int to) {
        return Mth.clamp(Math.round(from + (to - from) * strength), 0, 255);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putInt("rgb", rgb);
        tag.putFloat("strength", strength);
        tag.putString("style", style.name());
        tag.putInt("hueSwing", hueSwing);
        return tag;
    }

    @Nullable
    public static AbilityColor load(CompoundTag tag) {
        try {
            BeamStyle style = tag.contains("style") ? BeamStyle.valueOf(tag.getString("style")) : BeamStyle.SOLID_CORE;
            int swing = tag.contains("hueSwing") ? tag.getInt("hueSwing") : DEFAULT_HUE_SWING;
            return new AbilityColor(Mode.valueOf(tag.getString("mode")), tag.getInt("rgb"), tag.getFloat("strength"), style, swing);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        buffer.writeInt(rgb);
        buffer.writeFloat(strength);
        buffer.writeEnum(style);
        buffer.writeVarInt(hueSwing);
    }

    public static AbilityColor read(FriendlyByteBuf buffer) {
        return new AbilityColor(buffer.readEnum(Mode.class), buffer.readInt(), buffer.readFloat(), buffer.readEnum(BeamStyle.class),
                buffer.readVarInt());
    }
}
