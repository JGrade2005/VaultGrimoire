package io.github.jgrade.vaultspellbook.config;

import net.minecraftforge.common.ForgeConfigSpec;

// Per-player display settings (config/vaultspellbook-client.toml). Your own beams in first person are
// dimmed and capped in width so you can still see what you aim at; other players see them in full.
public final class VaultSpellbookClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue FIRST_PERSON_BEAM_OPACITY;
    public static final ForgeConfigSpec.DoubleValue FIRST_PERSON_BEAM_MAX_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("beams");
        FIRST_PERSON_BEAM_OPACITY = builder
                .comment("Brightness/opacity of your own Arcane beam and Rail in first person (1 = as others see it).")
                .defineInRange("firstPersonOpacity", 0.3, 0.0, 1.0);
        FIRST_PERSON_BEAM_MAX_RADIUS = builder
                .comment("Widest your own beam is drawn in first person, in blocks (Size Up can make it much wider).")
                .defineInRange("firstPersonMaxRadius", 0.6, 0.05, 16.0);
        builder.pop();
        SPEC = builder.build();
    }

    private VaultSpellbookClientConfig() {
    }
}
