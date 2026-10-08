package io.github.jgrade.vaultspellbook.config;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.augment.AugmentType;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphs;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Server config (saved per world in serverconfig/vaultspellbook-server.toml, synced to clients).
// The abilities' own mana costs and cooldowns are not configurable: every cast uses the VH
// ability's native values. Augment strengths and limits are.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VaultSpellbookConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_GLYPHS;
    public static final ForgeConfigSpec.BooleanValue WORKS_OUTSIDE_VAULTS;

    public static final ForgeConfigSpec.DoubleValue AMPLIFY_AREA_BONUS;
    public static final ForgeConfigSpec.DoubleValue QUICKEN_COOLDOWN_SKIP;
    public static final ForgeConfigSpec.DoubleValue DISCOUNT_REFUND;
    public static final ForgeConfigSpec.DoubleValue ECHO_DELAY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue STRENGTHEN_DAMAGE_BONUS;
    public static final ForgeConfigSpec.DoubleValue ACCELERATE_SPEED_BONUS;
    public static final ForgeConfigSpec.DoubleValue HOMING_RANGE;
    public static final ForgeConfigSpec.DoubleValue ORBIT_RADIUS;
    public static final ForgeConfigSpec.IntValue ORBIT_MAX_SECONDS;
    public static final ForgeConfigSpec.DoubleValue DELAY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SIZE_UP_BONUS;
    public static final ForgeConfigSpec.DoubleValue SIZE_DOWN_REDUCTION;
    public static final ForgeConfigSpec.IntValue ETHEREAL_MAX_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SHOTGUN_SPREAD_DEGREES;
    public static final ForgeConfigSpec.DoubleValue LOW_GRAVITY_REDUCTION;
    public static final ForgeConfigSpec.IntValue BUNDLE_MAX_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_BEAM_START;
    public static final ForgeConfigSpec.DoubleValue CHARGE_BEAM_STEP;
    public static final ForgeConfigSpec.DoubleValue CHARGE_BEAM_FLOOR;
    public static final ForgeConfigSpec.DoubleValue CHARGE_RAIL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ARCANE_RANGE;
    private static final Map<AugmentType, ForgeConfigSpec.IntValue> MAX_COUNTS = new EnumMap<>(AugmentType.class);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("glyphs");
        DISABLED_GLYPHS = builder
                .comment("Vault Hunters specialization ids whose glyphs cannot be cast with the Vault Spellbook,",
                        "e.g. [\"Dash_Base\", \"Heal_Group\"]. Ids are the ones in config/the_vault/abilities.json.")
                .defineListAllowEmpty(List.of("disabledGlyphs"), List::of, id -> id instanceof String);
        builder.pop();

        builder.push("book");
        WORKS_OUTSIDE_VAULTS = builder
                .comment("If false, the Vault Spellbook only casts inside vaults (the_vault dimensions).")
                .define("worksOutsideVaults", true);
        builder.pop();

        builder.comment("Augment glyphs modify the closest ability glyph before them. 'max' is per ability",
                "(per spell for Echo and Delay).").push("augments");
        builder.push("amplify_area");
        AMPLIFY_AREA_BONUS = builder.comment("Area of effect added per glyph (0.25 = +25%).")
                .defineInRange("bonusPerGlyph", 0.25, 0.0, 10.0);
        maxCount(builder, AugmentType.AMPLIFY_AREA, 4);
        builder.pop();
        builder.push("quicken");
        QUICKEN_COOLDOWN_SKIP = builder.comment("Share of the cooldown skipped per glyph right after the cast (0.1 = 10%).")
                .defineInRange("cooldownSkipPerGlyph", 0.10, 0.0, 1.0);
        maxCount(builder, AugmentType.QUICKEN, 5);
        builder.pop();
        builder.push("discount");
        DISCOUNT_REFUND = builder.comment("Share of the Vault mana paid that is refunded per glyph (0.1 = 10%).")
                .defineInRange("refundPerGlyph", 0.10, 0.0, 1.0);
        maxCount(builder, AugmentType.DISCOUNT, 5);
        builder.pop();
        builder.push("echo");
        ECHO_DELAY_SECONDS = builder.comment("Time between the cast and its echo. Mobs ignore new damage for 0.5 s after a hit",
                        "(Minecraft's hurt immunity), so a shorter echo deals no damage to mobs the first cast hit.")
                .defineInRange("delaySeconds", 0.5, 0.5, 10.0);
        maxCount(builder, AugmentType.ECHO, 1);
        builder.pop();
        builder.push("strengthen");
        STRENGTHEN_DAMAGE_BONUS = builder.comment("Damage added per glyph (0.2 = +20%).")
                .defineInRange("bonusPerGlyph", 0.20, 0.0, 10.0);
        maxCount(builder, AugmentType.STRENGTHEN, 4);
        builder.pop();
        builder.push("accelerate");
        ACCELERATE_SPEED_BONUS = builder.comment("Projectile speed added per glyph (0.25 = +25%).")
                .defineInRange("bonusPerGlyph", 0.25, 0.0, 10.0);
        maxCount(builder, AugmentType.ACCELERATE, 4);
        builder.pop();
        builder.push("homing");
        HOMING_RANGE = builder.comment("Blocks within which homing projectiles look for an enemy.")
                .defineInRange("range", 16.0, 1.0, 64.0);
        maxCount(builder, AugmentType.HOMING, 1);
        builder.pop();
        builder.push("orbit");
        ORBIT_RADIUS = builder.comment("Distance in blocks at which projectiles circle the player.")
                .defineInRange("radius", 2.5, 1.0, 8.0);
        ORBIT_MAX_SECONDS = builder.comment("Orbiting projectiles that hit nothing are removed after this many seconds.")
                .defineInRange("maxSeconds", 30, 1, 300);
        maxCount(builder, AugmentType.ORBIT, 1);
        builder.pop();
        builder.push("delay");
        DELAY_SECONDS = builder.comment("Pause per Delay glyph.")
                .defineInRange("secondsPerGlyph", 1.0, 0.05, 10.0);
        maxCount(builder, AugmentType.DELAY, 5);
        builder.pop();
        builder.push("loop");
        maxCount(builder, AugmentType.LOOP, 1);
        builder.pop();
        builder.push("size_up");
        SIZE_UP_BONUS = builder.comment("Projectile size (incl. hitbox) added per glyph, multiplicative (0.5 = +50%).",
                        "No glyph limit. On the Arcane beam the hit radius grows by (size - 1) blocks.")
                .defineInRange("bonusPerGlyph", 0.5, 0.0, 4.0);
        builder.pop();
        builder.push("size_down");
        SIZE_DOWN_REDUCTION = builder.comment("Projectile size (incl. hitbox) removed per glyph, multiplicative (0.25 = -25%).")
                .defineInRange("reductionPerGlyph", 0.25, 0.0, 0.9);
        maxCount(builder, AugmentType.SIZE_DOWN, 4);
        builder.pop();
        builder.push("ethereal");
        ETHEREAL_MAX_SECONDS = builder.comment("Ethereal ability projectiles are removed after this many seconds.")
                .defineInRange("maxSeconds", 10, 1, 120);
        maxCount(builder, AugmentType.ETHEREAL, 1);
        builder.pop();
        builder.push("shotgun");
        SHOTGUN_SPREAD_DEGREES = builder.comment("Angle between the two halves of each Shotgun split.")
                .defineInRange("spreadDegrees", 15.0, 1.0, 90.0);
        maxCount(builder, AugmentType.SHOTGUN, 4, 6); // each glyph doubles the copies: 6 = 64 per glyph
        builder.pop();
        builder.push("low_gravity");
        LOW_GRAVITY_REDUCTION = builder.comment("Share of a projectile's gravity removed per glyph (0.5: two glyphs = no drop).")
                .defineInRange("reductionPerGlyph", 0.5, 0.0, 1.0);
        maxCount(builder, AugmentType.LOW_GRAVITY, 2);
        builder.pop();
        builder.push("creative_loop");
        maxCount(builder, AugmentType.CREATIVE_LOOP, 1);
        builder.pop();
        builder.push("charge");
        CHARGE_SECONDS = builder.comment("Wind-up per Charge glyph before the Arcane beam or Rail fires.")
                .defineInRange("secondsPerGlyph", 1.0, 0.05, 10.0);
        CHARGE_BEAM_START = builder.comment("A charged Arcane beam's damage multiplier during its first second.")
                .defineInRange("beamStartMultiplier", 10.0, 1.0, 100.0);
        CHARGE_BEAM_STEP = builder.comment("How much that multiplier drops each second, down to 1x.")
                .defineInRange("beamStepPerSecond", 1.0, 0.01, 100.0);
        CHARGE_BEAM_FLOOR = builder.comment("The multiplier once it has passed 1x, for the rest of the hold.")
                .defineInRange("beamFloorMultiplier", 0.5, 0.0, 10.0);
        CHARGE_RAIL_MULTIPLIER = builder.comment("A charged Arcane Rail's damage multiplier.")
                .defineInRange("railMultiplier", 10.0, 1.0, 100.0);
        maxCount(builder, AugmentType.CHARGE, 5);
        builder.pop();
        builder.pop();

        builder.push("beam");
        ARCANE_RANGE = builder.comment("How far an Arcane beam cast with the Vault Spellbook hits, in blocks (VH's own range is",
                        "ignored for spellbook casts; native Arcane keeps it). Clients draw it out to their render distance.")
                .defineInRange("arcaneRange", 47.0, 4.0, 128.0);
        builder.pop();

        builder.push("bundled_spell");
        BUNDLE_MAX_SECONDS = builder.comment("Bundled Spells that hit nothing are removed after this many seconds.")
                .defineInRange("maxSeconds", 30, 1, 300);
        builder.pop();
        SPEC = builder.build();
    }

    private VaultSpellbookConfig() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        checkDisabledGlyphs(event.getConfig());
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        checkDisabledGlyphs(event.getConfig());
    }

    // A typo in disabledGlyphs would silently disable nothing, so unknown ids are logged.
    private static void checkDisabledGlyphs(ModConfig config) {
        if (config.getSpec() != SPEC) {
            return;
        }
        Set<String> known = VaultGlyphs.ALL.stream().map(glyph -> glyph.getAdapter().getSpecializationId()).collect(Collectors.toSet());
        for (String id : DISABLED_GLYPHS.get()) {
            if (!known.contains(id)) {
                VaultSpellbook.LOGGER.warn("glyphs.disabledGlyphs: '{}' is not a specialization id (see config/the_vault/abilities.json)",
                        id);
            }
        }
    }

    private static void maxCount(ForgeConfigSpec.Builder builder, AugmentType type, int defaultMax) {
        maxCount(builder, type, defaultMax, 10);
    }

    private static void maxCount(ForgeConfigSpec.Builder builder, AugmentType type, int defaultMax, int upperBound) {
        MAX_COUNTS.put(type, builder.comment("Most glyphs of this kind allowed.").defineInRange("max", defaultMax, 0, upperBound));
    }

    public static boolean isGlyphDisabled(String specializationId) {
        return DISABLED_GLYPHS.get().contains(specializationId);
    }

    // Most glyphs of this kind per ability; augments without a "max" key (Size Up) have no limit.
    public static int maxCount(AugmentType type) {
        ForgeConfigSpec.IntValue max = MAX_COUNTS.get(type);
        return max == null ? Integer.MAX_VALUE : max.get();
    }

    public static int secondsToTicks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20));
    }
}
