package io.github.jgrade.vaultspellbook.augment;

import iskallia.vault.gear.etching.EtchingGearAttributes;
import iskallia.vault.gear.etching.EtchingHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Line maths shared by the Arcane beam and the Rail's Size Up hits (server) and the drawn beams (client), and
// the Arcane Rail values VH uses, so a change to VH's beams is made here once.
public final class BeamGeometry {
    // VH's Arcane Rail: 128 blocks from eye height - 0.5, stopped by block outlines (ArcaneRailAbility.fireBolt).
    public static final double RAIL_RANGE = 128.0;
    // About what VH's thin rays reach (ProjectileUtil inflates target boxes by 0.3); the Rail's width at size 1.
    public static final double BASE_RADIUS = 0.3;

    private BeamGeometry() {
    }

    // Where VH's Arcane beam and Rail start: half a block below the eyes.
    public static Vec3 start(LivingEntity caster) {
        return caster.getEyePosition().add(0.0, -0.5, 0.0);
    }

    // How far along the line the entity's centre is (negative: behind the start).
    public static double along(Entity entity, Vec3 start, Vec3 direction) {
        return entity.getBoundingBox().getCenter().subtract(start).dot(direction);
    }

    // Whether a beam of this radius and length touches the entity: its centre is within the radius plus half
    // its width of the beam's segment.
    public static boolean touches(Entity entity, Vec3 start, Vec3 direction, double length, double radius) {
        Vec3 center = entity.getBoundingBox().getCenter();
        double along = Mth.clamp(center.subtract(start).dot(direction), 0.0, length);
        return center.distanceTo(start.add(direction.scale(along))) <= radius + entity.getBbWidth() * 0.5;
    }

    // How far the Rail reaches along this direction: to the first block outline, or its full range.
    public static double railLength(LivingEntity caster, Vec3 start, Vec3 direction) {
        HitResult hit = caster.level.clip(new ClipContext(start, start.add(direction.scale(RAIL_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? RAIL_RANGE : hit.getLocation().distanceTo(start);
    }

    // The Rail's hit radius: the base times the rail-size etching (at least 1), plus Size Up's extra radius.
    public static double railRadius(LivingEntity caster, double extraRadius) {
        float size = EtchingHelper.getEtchings(caster, EtchingGearAttributes.ARCANE_RAIL_SIZE).stream().findFirst()
                .map(instance -> instance.getValue()).orElse(1.0F);
        return BASE_RADIUS * Math.max(1.0F, size) + extraRadius;
    }
}
