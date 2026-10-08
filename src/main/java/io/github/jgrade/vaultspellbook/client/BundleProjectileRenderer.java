package io.github.jgrade.vaultspellbook.client;

import io.github.jgrade.vaultspellbook.form.BundleProjectile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

// Draws nothing itself: like Ars's projectile form, a Bundled Spell is seen through its glow particles.
public class BundleProjectileRenderer extends EntityRenderer<BundleProjectile> {
    public BundleProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @SuppressWarnings("deprecation") // the vanilla block atlas constant; nothing is drawn with it
    public ResourceLocation getTextureLocation(BundleProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
