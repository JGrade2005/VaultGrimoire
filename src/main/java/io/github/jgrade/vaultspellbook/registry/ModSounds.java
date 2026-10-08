package io.github.jgrade.vaultspellbook.registry;

import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// The beam charge-up sounds, cut from the author's arcanebeam.ogg (README: License) at page boundaries
// (no re-encoding): the build-up (0-16.3 s) plays while the Charge glyph winds a beam up, the sustained blast
// (19.4-35.6 s) loops while the charged beam fires. Stereo, so clients set their volume by distance.
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, VaultSpellbook.MOD_ID);

    public static final RegistryObject<SoundEvent> ARCANE_CHARGE = register("arcane_charge");
    public static final RegistryObject<SoundEvent> ARCANE_BLAST = register("arcane_blast");

    private ModSounds() {
    }

    @SuppressWarnings("removal") // Forge 40 marks the two-arg constructor; it is still the standard way on 1.18.2
    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> new SoundEvent(new ResourceLocation(VaultSpellbook.MOD_ID, name)));
    }
}
