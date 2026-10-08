package io.github.jgrade.vaultspellbook.registry;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.item.VaultSpellbookItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, VaultSpellbook.MOD_ID);

    public static final RegistryObject<VaultSpellbookItem> VAULT_SPELLBOOK = ITEMS.register("vault_spellbook", VaultSpellbookItem::new);

    private ModItems() {
    }
}
