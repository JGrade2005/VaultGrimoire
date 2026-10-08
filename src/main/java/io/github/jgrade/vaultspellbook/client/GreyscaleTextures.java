package io.github.jgrade.vaultspellbook.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Recolor support: a render type drawing an entity texture is swapped for the same kind
// of render type drawing a grey copy of that texture, so the ability colour (a vertex colour) paints it
// fully. Grey copies keep the texture's shading, stretched so its brightest pixel is white, and are
// made on first use as dynamic textures. The texture is read from the render type's private state by
// field type (no names, so no mappings needed); the render type is rebuilt only for Minecraft's own
// memoized entity render types (matched by identity). Anything else (an atlas, a custom render type)
// keeps its texture and the colour acts as a filter. Render thread only.
public final class GreyscaleTextures {
    private static final List<Function<ResourceLocation, RenderType>> FACTORIES = List.of(
            RenderType::entitySolid, RenderType::entityCutout, RenderType::entityCutoutNoCull,
            t -> RenderType.entityCutoutNoCull(t, false), RenderType::entityCutoutNoCullZOffset,
            RenderType::itemEntityTranslucentCull, RenderType::entityTranslucentCull, RenderType::entityTranslucent,
            t -> RenderType.entityTranslucent(t, false), RenderType::entitySmoothCutout, RenderType::entityDecal,
            RenderType::entityNoOutline, RenderType::eyes, RenderType::armorCutoutNoCull, RenderType::dragonExplosionAlpha,
            t -> RenderType.beaconBeam(t, false), t -> RenderType.beaconBeam(t, true));
    private static final Map<RenderType, RenderType> SWAPS = new HashMap<>();
    private static final Map<ResourceLocation, Optional<ResourceLocation>> GREY = new HashMap<>();
    private static boolean reflectionFailed;

    private GreyscaleTextures() {
    }

    // The same render type on a grey copy of its texture, or the type itself if that isn't possible.
    public static RenderType swap(RenderType type) {
        return SWAPS.computeIfAbsent(type, GreyscaleTextures::computeSwap);
    }

    // Forget all grey copies (resource reload).
    public static void clear() {
        GREY.values().forEach(grey -> grey.ifPresent(Minecraft.getInstance().getTextureManager()::release));
        GREY.clear();
        SWAPS.clear();
    }

    private static RenderType computeSwap(RenderType type) {
        ResourceLocation texture = textureOf(type);
        if (texture == null) {
            return type;
        }
        for (Function<ResourceLocation, RenderType> factory : FACTORIES) {
            if (factory.apply(texture) == type) {
                ResourceLocation grey = GREY.computeIfAbsent(texture, GreyscaleTextures::makeGrey).orElse(null);
                return grey == null ? type : factory.apply(grey);
            }
        }
        return type;
    }

    @Nullable
    private static ResourceLocation textureOf(RenderType type) {
        if (reflectionFailed) {
            return null;
        }
        try {
            Object state = fieldOfType(type, RenderType.CompositeState.class);
            if (state == null) {
                return null; // not a composite render type
            }
            for (Field field : state.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object shard = field.get(state);
                Optional<?> texture = shard instanceof RenderStateShard ? cutoutTexture(shard) : Optional.empty();
                if (texture.isPresent() && texture.get() instanceof ResourceLocation location) {
                    return location;
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            VaultSpellbook.LOGGER.error("Could not read render type textures; Recolor acts as a filter", e);
            return null;
        }
    }

    @Nullable
    private static Object fieldOfType(Object owner, Class<?> type) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(owner);
                }
            }
        }
        return null;
    }

    // The texture state shard's no-argument method returning Optional (cutoutTexture).
    private static Optional<?> cutoutTexture(Object shard) throws ReflectiveOperationException {
        for (Class<?> c = shard.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == Optional.class) {
                    method.setAccessible(true);
                    return (Optional<?>) method.invoke(shard);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> makeGrey(ResourceLocation texture) {
        Optional<Resource> resource;
        try {
            resource = Optional.of(Minecraft.getInstance().getResourceManager().getResource(texture));
        } catch (IOException e) {
            return Optional.empty(); // generated at runtime (an atlas, a player skin): no file to copy
        }
        try (Resource r = resource.get(); NativeImage source = NativeImage.read(r.getInputStream())) {
            NativeImage grey = new NativeImage(source.getWidth(), source.getHeight(), true);
            int brightest = 1;
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    if ((abgr >>> 24) > 0) {
                        brightest = Math.max(brightest, luminance(abgr));
                    }
                }
            }
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    int abgr = source.getPixelRGBA(x, y);
                    int value = Math.min(255, luminance(abgr) * 255 / brightest);
                    grey.setPixelRGBA(x, y, (abgr & 0xFF000000) | value << 16 | value << 8 | value);
                }
            }
            @SuppressWarnings("removal") // Forge 40 marks the two-arg constructor; it is still the standard way on 1.18.2
            ResourceLocation location = new ResourceLocation(VaultSpellbook.MOD_ID,
                    "grey/" + texture.getNamespace() + "/" + texture.getPath());
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(grey));
            return Optional.of(location);
        } catch (IOException | RuntimeException e) {
            VaultSpellbook.LOGGER.warn("Could not make a grey copy of {}; Recolor acts as a filter on it", texture, e);
            return Optional.empty();
        }
    }

    // NativeImage pixels are ABGR.
    private static int luminance(int abgr) {
        int r = abgr & 0xFF;
        int g = abgr >> 8 & 0xFF;
        int b = abgr >> 16 & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}
