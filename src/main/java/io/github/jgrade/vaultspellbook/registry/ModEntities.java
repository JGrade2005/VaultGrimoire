package io.github.jgrade.vaultspellbook.registry;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.form.BundleProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, VaultSpellbook.MOD_ID);

    public static final RegistryObject<EntityType<BundleProjectile>> BUNDLED_SPELL = ENTITIES.register("bundled_spell",
            () -> EntityType.Builder.<BundleProjectile>of(BundleProjectile::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("bundled_spell"));

    private ModEntities() {
    }
}
