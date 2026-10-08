package io.github.jgrade.vaultspellbook.form;

import com.hollingsworth.arsnouveau.client.particle.GlowParticleData;
import com.hollingsworth.arsnouveau.common.entity.ColoredProjectile;
import io.github.jgrade.vaultspellbook.cast.CastContext;
import io.github.jgrade.vaultspellbook.cast.CastOrigin;
import io.github.jgrade.vaultspellbook.cast.SpellPlan;
import io.github.jgrade.vaultspellbook.cast.VaultCaster;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookConfig;
import io.github.jgrade.vaultspellbook.registry.ModEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.List;

// The Bundled Spell projectile: flies straight like Ars's projectile form and, on
// hitting an entity or block, casts its payload at the landing point. Size (incl. hitbox) is synced
// to clients; an ethereal bundle passes through blocks and only lands on entities. It is never saved
// with the world: its payload lives only in memory.
public class BundleProjectile extends ColoredProjectile {
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(BundleProjectile.class, EntityDataSerializers.FLOAT);

    private List<SpellPlan.Step> payload;
    private CastContext context;
    private boolean ethereal;
    private int age;

    public BundleProjectile(EntityType<? extends BundleProjectile> type, Level level) {
        super(type, level);
    }

    public BundleProjectile(Level level, ServerPlayer owner, List<SpellPlan.Step> payload, CastContext context,
                            float scale, boolean ethereal) {
        this(ModEntities.BUNDLED_SPELL.get(), level);
        setOwner(owner);
        this.payload = payload;
        this.context = context;
        this.ethereal = ethereal;
        this.entityData.set(SCALE, scale);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SCALE, 1.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SCALE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(this.entityData.get(SCALE));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level.isClientSide && (payload == null || ++age > VaultSpellbookConfig.BUNDLE_MAX_SECONDS.get() * 20)) {
            discard();
            return;
        }
        Vec3 motion = getDeltaMovement();
        if (!level.isClientSide) {
            HitResult hit = ethereal ? findEntityHit(motion) : ProjectileUtil.getHitResult(this, this::canHitEntity);
            if (hit != null && hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                onHit(hit);
                if (isRemoved()) {
                    return;
                }
            }
        }
        setPos(position().add(motion));
        ProjectileUtil.rotateTowardsMovement(this, 0.2F);
        if (level.isClientSide) {
            spawnTrail();
        }
    }

    private HitResult findEntityHit(Vec3 motion) {
        Vec3 start = position();
        AABB swept = getBoundingBox().expandTowards(motion).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(level, this, start, start.add(motion), swept, this::canHitEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        land(hit.getEntity().getBoundingBox().getCenter());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        // Step back off the block face so point abilities don't start inside it.
        Vec3 face = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.25);
        land(hit.getLocation().add(face));
    }

    private void land(Vec3 position) {
        if (getOwner() instanceof ServerPlayer owner && owner.isAlive() && payload != null) {
            VaultCaster.runPayload(owner, payload, context.withOrigin(new CastOrigin(position, getDeltaMovement())));
        }
        discard();
    }

    // Ars's glow particles, like its own projectile form; more of them for bigger bundles.
    private void spawnTrail() {
        float scale = this.entityData.get(SCALE);
        int count = Math.max(1, Math.round(3 * scale));
        double spread = 0.1 * scale;
        for (int i = 0; i < count; i++) {
            level.addParticle(GlowParticleData.createData(getParticleColor()),
                    getX() + (random.nextDouble() - 0.5) * spread,
                    getY() + getBbHeight() / 2 + (random.nextDouble() - 0.5) * spread,
                    getZ() + (random.nextDouble() - 0.5) * spread, 0, 0, 0);
        }
    }
}
