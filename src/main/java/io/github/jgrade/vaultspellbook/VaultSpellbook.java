package io.github.jgrade.vaultspellbook;

import com.mojang.logging.LogUtils;
import io.github.jgrade.vaultspellbook.ability.AbilityCastTracker;
import io.github.jgrade.vaultspellbook.augment.AugmentEvents;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookClientConfig;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.glyph.VaultGlyphs;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import io.github.jgrade.vaultspellbook.registry.ModEntities;
import io.github.jgrade.vaultspellbook.registry.ModItems;
import io.github.jgrade.vaultspellbook.registry.ModSounds;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VaultSpellbook.MOD_ID)
public class VaultSpellbook {
    public static final String MOD_ID = "vaultspellbook";
    // The mod's one logger (as in Wold's Vaults); messages say which feature they are about.
    public static final Logger LOGGER = LogUtils.getLogger();

    public VaultSpellbook() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VaultSpellbookConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VaultSpellbookClientConfig.SPEC);
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModEntities.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModSounds.SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        VaultGlyphs.registerAll();
        ModNetwork.register();
        AbilityCastTracker.register();
        AugmentEvents.register();
    }
}
