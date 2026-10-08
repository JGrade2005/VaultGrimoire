package io.github.jgrade.vaultspellbook.client;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.augment.ProjectileSizing;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Draws ability entities at their Size Up / Size Down scale and in their ability colour.
// Both are kept here per client entity, set by ProjectileScalePacket and EntityColorPacket (the
// scale also goes in the entity's persistent data, which ProjectileSizing's size event reads), so
// drawing never decodes NBT. Minecraft has no render hook for non-living entities, so after the entity
// renderers are (re)built, the renderers of VH's and WV's non-living entities are wrapped in one that
// scales the pose and tints the buffers before delegating (it changes nothing for untagged entities).
// The renderer map (EntityRenderDispatcher.renderers) and the light lookups the wrapper forwards are
// not public; they are reached through Forge's ObfuscationReflectionHelper. Living summons (necromancy,
// decoy) are tinted through Forge's RenderLivingEvent instead.
// Other mods can notice two things: a wrapped renderer is no longer an instance of VH's renderer class,
// and a coloured summon fires RenderLivingEvent.Pre twice (the cancelled render and ours). Nothing in the
// pack minds today; re-check when a rendering mod is added.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AbilityEntityRenderers {
    private static final Set<String> WRAPPED_NAMESPACES = Set.of("the_vault", "woldsvaults");
    // Weak and by identity: an entry goes when its entity is gone.
    private static final Map<Entity, AbilityColor> COLORS = new MapMaker().weakKeys().makeMap();
    private static final Map<Entity, Float> SCALES = new MapMaker().weakKeys().makeMap();

    private AbilityEntityRenderers() {
    }

    public static void setScale(int entityId, float scale) {
        Entity entity = clientEntity(entityId);
        if (entity != null) {
            SCALES.put(entity, scale);
            entity.getPersistentData().putFloat(ProjectileSizing.SCALE_TAG, scale);
            entity.refreshDimensions(); // the client's copy of the hitbox matches (ProjectileSizing's size event)
        }
    }

    public static void setColor(int entityId, AbilityColor color) {
        Entity entity = clientEntity(entityId);
        if (entity != null) {
            tint(entity, color);
        }
    }

    // Draws this entity in the colour from now on (also the colour tab's preview entities).
    public static void tint(Entity entity, AbilityColor color) {
        COLORS.put(entity, color);
    }

    private static Entity clientEntity(int entityId) {
        return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(entityId);
    }

    // Runs after EntityRenderDispatcher built its renderers (also on every resource reload).
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        GreyscaleTextures.clear();
        try {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            Field field = ObfuscationReflectionHelper.findField(EntityRenderDispatcher.class, "f_114362_");
            @SuppressWarnings("unchecked")
            Map<EntityType<?>, EntityRenderer<?>> renderers = new HashMap<>((Map<EntityType<?>, EntityRenderer<?>>) field.get(dispatcher));
            EntityRendererProvider.Context context = new EntityRendererProvider.Context(dispatcher,
                    Minecraft.getInstance().getItemRenderer(), Minecraft.getInstance().getResourceManager(),
                    Minecraft.getInstance().getEntityModels(), Minecraft.getInstance().font);
            int wrapped = 0;
            for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
                ResourceLocation id = entry.getKey().getRegistryName();
                EntityRenderer<?> original = entry.getValue();
                if (id != null && WRAPPED_NAMESPACES.contains(id.getNamespace())
                        && !(original instanceof LivingEntityRenderer<?, ?>) && !(original instanceof Wrapped<?>)) {
                    entry.setValue(wrap(context, original));
                    wrapped++;
                }
            }
            field.set(dispatcher, renderers);
            VaultSpellbook.LOGGER.debug("Wrapped {} VH/WV entity renderers for ability size and colour", wrapped);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VaultSpellbook.LOGGER.error("Could not wrap VH entity renderers; Size Up/Down and ability colours don't show on them", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> Wrapped<T> wrap(EntityRendererProvider.Context context, EntityRenderer<?> original) {
        return new Wrapped<>(context, (EntityRenderer<T>) original);
    }

    // Scales the pose around the entity's centre and tints its buffers, then lets the original renderer draw.
    private static final class Wrapped<T extends Entity> extends EntityRenderer<T> {
        private static final Method BLOCK_LIGHT = findLightMethod("m_6086_");
        private static final Method SKY_LIGHT = findLightMethod("m_114508_");
        private final EntityRenderer<T> delegate;

        Wrapped(EntityRendererProvider.Context context, EntityRenderer<T> delegate) {
            super(context);
            this.delegate = delegate;
        }

        @Override
        public void render(T entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
            AbilityColor color = COLORS.get(entity);
            MultiBufferSource drawn = color == null ? buffers : new TintedBufferSource(buffers, color);
            float scale = SCALES.getOrDefault(entity, 1.0F);
            if (scale == 1.0F) {
                delegate.render(entity, yaw, partialTicks, pose, drawn, light);
                return;
            }
            float centre = entity.getBbHeight() / (2 * scale); // the hitbox is already scaled; centre of the original model
            pose.pushPose();
            pose.translate(0, centre, 0);
            pose.scale(scale, scale, scale);
            pose.translate(0, -centre, 0);
            delegate.render(entity, yaw, partialTicks, pose, drawn, light);
            pose.popPose();
        }

        @Override
        public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
            return delegate.shouldRender(entity, frustum, x, y, z);
        }

        @Override
        public Vec3 getRenderOffset(T entity, float partialTicks) {
            return delegate.getRenderOffset(entity, partialTicks);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return delegate.getTextureLocation(entity);
        }

        @Override
        protected int getBlockLightLevel(T entity, BlockPos pos) {
            return invokeLight(BLOCK_LIGHT, entity, pos, super.getBlockLightLevel(entity, pos));
        }

        @Override
        protected int getSkyLightLevel(T entity, BlockPos pos) {
            return invokeLight(SKY_LIGHT, entity, pos, super.getSkyLightLevel(entity, pos));
        }

        // Keeps the original renderer's lighting (e.g. full-bright fireballs).
        private int invokeLight(Method method, T entity, BlockPos pos, int fallback) {
            if (method == null) {
                return fallback;
            }
            try {
                return (int) method.invoke(delegate, entity, pos);
            } catch (ReflectiveOperationException e) {
                return fallback;
            }
        }

        private static Method findLightMethod(String srgName) {
            try {
                return ObfuscationReflectionHelper.findMethod(EntityRenderer.class, srgName, Entity.class, BlockPos.class);
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    // Coloured living summons: the event's render is cancelled and redone with tinted buffers.
    @Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, value = Dist.CLIENT)
    public static final class Living {
        private static boolean rendering;

        private Living() {
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity entity = event.getEntity();
            AbilityColor color = rendering ? null : COLORS.get(entity);
            if (color == null) {
                return;
            }
            event.setCanceled(true);
            rendering = true;
            try {
                float yaw = Mth.lerp(event.getPartialTick(), entity.yRotO, entity.getYRot());
                ((LivingEntityRenderer) event.getRenderer()).render(entity, yaw, event.getPartialTick(), event.getPoseStack(),
                        new TintedBufferSource(event.getMultiBufferSource(), color), event.getPackedLight());
            } finally {
                rendering = false;
            }
        }
    }
}
