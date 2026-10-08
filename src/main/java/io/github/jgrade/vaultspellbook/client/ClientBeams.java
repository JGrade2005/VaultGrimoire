package io.github.jgrade.vaultspellbook.client;

import com.hollingsworth.arsnouveau.client.particle.ColorParticleTypeData;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.augment.BeamGeometry;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.color.BeamStyle;
import io.github.jgrade.vaultspellbook.form.ShotgunPattern;
import io.github.jgrade.vaultspellbook.network.ChargePacket;
import io.github.jgrade.vaultspellbook.network.HoldBeamPacket;
import io.github.jgrade.vaultspellbook.network.RailBeamPacket;
import iskallia.vault.client.particles.ArcaneParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

// Coloured beams this client draws: held Arcane beams (kept alive by HoldBeamPacket, aimed
// with the caster's view every frame) and Arcane Rail bolts (RailBeamPacket, fading over
// 14 ticks, VH's own rail particles along the bolt are removed). Also spawns each
// style's particles at the caster's hand. BeamRenderer draws the beams themselves. Charges (the
// Charge glyph's wind-up) gather energy at the caster's hand until the beam fires.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, value = Dist.CLIENT)
public final class ClientBeams {
    public static final int RAIL_TICKS = 14;
    private static final Map<Integer, Hold> HOLDS = new HashMap<>();
    private static final List<Rail> RAILS = new ArrayList<>();
    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static List<Field> particleFields;

    private ClientBeams() {
    }

    // A held beam: its latest shape, when it stops being drawn, and how far each of its beams reached when
    // last measured (once per tick; frames in between reuse it).
    public static final class Hold {
        HoldBeamPacket shape;
        long expires;
        final long started;
        long measuredAt = Long.MIN_VALUE;
        double[] lengths = new double[0];
        boolean[] hits = new boolean[0];

        Hold(HoldBeamPacket shape, long now) {
            this.shape = shape;
            this.started = now;
        }

        public HoldBeamPacket shape() {
            return shape;
        }

        public long started() {
            return started;
        }
    }

    // A beam charging up at a caster's hand until it fires.
    public record Charge(ChargePacket packet, long started) {
        public long ends() {
            return started + packet.ticks();
        }

        // 0 when the charge starts, 1 when the beam fires.
        public float progress(double now) {
            return (float) Math.max(0.0, Math.min(1.0, (now - started) / Math.max(1, packet.ticks())));
        }
    }

    // A Rail bolt: a fixed line that fades.
    public record Rail(RailBeamPacket bolt, long fired) {
    }

    // One beam to draw this frame: from its start along a direction, and whether it ended on something.
    public record Segment(Vec3 start, Vec3 direction, double length, boolean hit) {
        public Vec3 end() {
            return start.add(direction.scale(length));
        }
    }

    public static void hold(HoldBeamPacket shape) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        Hold hold = HOLDS.computeIfAbsent(shape.casterId(), id -> new Hold(shape, now));
        if (!shape.equals(hold.shape)) {
            hold.measuredAt = Long.MIN_VALUE;
        }
        hold.shape = shape;
        hold.expires = now + HoldBeamPacket.TTL_TICKS;
    }

    public static void charge(ChargePacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        CHARGES.put(packet.casterId(), new Charge(packet, level.getGameTime()));
        BeamSounds.charge(packet.casterId(), packet.ticks());
    }

    public static Map<Integer, Charge> charges() {
        return CHARGES;
    }

    public static void rail(RailBeamPacket bolt) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RAILS.add(new Rail(bolt, level.getGameTime()));
        removeVaultRailParticles(bolt);
        int rgb = baseRgb(bolt.color(), AbilityVisuals.BeamKind.RAIL);
        Vec3 end = bolt.start().add(bolt.direction().scale(bolt.length()));
        burst(level, bolt.start(), bolt.direction().scale(-1), rgb, 10, 0.18);
        burst(level, end, bolt.direction().scale(-1), rgb, 8, 0.12);
    }

    public static Map<Integer, Hold> holds() {
        return HOLDS;
    }

    public static List<Rail> rails() {
        return RAILS;
    }

    // The beam colour before the hue pulse: the glyph's colour applied to the ability's default colour.
    public static int baseRgb(AbilityColor color, AbilityVisuals.BeamKind kind) {
        return color.flatten(kind.defaultRgb);
    }

    // Where a caster's beam starts: in front of the hand in first person, at the arm otherwise.
    public static Vec3 handOrigin(Entity caster, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = caster.getEyePosition(partialTicks);
        Vec3 look = caster.getViewVector(partialTicks);
        Vec3 up = caster.getUpVector(partialTicks);
        Vec3 right = look.cross(up).normalize();
        boolean firstPerson = caster == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
        if (firstPerson) {
            return eye.add(look.scale(0.9)).add(right.scale(0.28)).add(up.scale(-0.26));
        }
        return eye.add(look.scale(0.45)).add(right.scale(0.32)).add(up.scale(-0.42));
    }

    // The beams of a held shape this frame: Shotgun directions around the caster's view this frame, with the
    // lengths measured this tick (see measure).
    public static List<Segment> segments(Entity caster, Hold hold, float partialTicks) {
        HoldBeamPacket shape = hold.shape;
        List<Vec3> directions = ShotgunPattern.directions(caster.getViewVector(partialTicks), shape.splits(), shape.spread());
        long now = caster.level.getGameTime();
        if (hold.measuredAt != now || hold.lengths.length != directions.size()) {
            measure(caster, hold);
            hold.measuredAt = now;
        }
        Vec3 origin = handOrigin(caster, partialTicks);
        List<Segment> result = new ArrayList<>(directions.size());
        for (int i = 0; i < directions.size(); i++) {
            result.add(new Segment(origin, directions.get(i).normalize(), hold.lengths[i], hold.hits[i]));
        }
        return result;
    }

    // How far each beam of a held shape reaches, from the caster's view at the tick: out to this client's
    // render distance (or the hit range if longer), cut by blocks and by the mob where its pierce runs out
    // (only within the hit range, where it really hits). Raycasts are too costly to repeat every frame.
    private static void measure(Entity caster, Hold hold) {
        HoldBeamPacket shape = hold.shape;
        Vec3 origin = handOrigin(caster, 1.0F);
        List<Vec3> directions = ShotgunPattern.directions(caster.getViewVector(1.0F), shape.splits(), shape.spread());
        hold.lengths = new double[directions.size()];
        hold.hits = new boolean[directions.size()];
        for (int i = 0; i < directions.size(); i++) {
            Vec3 dir = directions.get(i).normalize();
            double length = Math.max(shape.range(), Minecraft.getInstance().options.renderDistance * 16.0);
            boolean hit = false;
            if (!shape.ethereal()) {
                HitResult block = caster.level.clip(new ClipContext(origin, origin.add(dir.scale(length)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (block.getType() != HitResult.Type.MISS) {
                    length = block.getLocation().distanceTo(origin);
                    hit = true;
                }
            }
            double target = pierceStop(caster, origin, dir, Math.min(length, shape.range()), shape.radius(), shape.pierce());
            if (target < length) {
                length = target;
                hit = true;
            }
            hold.lengths[i] = Math.max(0.5, length);
            hold.hits[i] = hit;
        }
    }

    // Distance to the mob where the beam stops (its pierce count reached within length), or infinity if none does.
    private static double pierceStop(Entity caster, Vec3 origin, Vec3 dir, double length, double radius, int pierce) {
        Vec3 end = origin.add(dir.scale(length));
        List<Double> distances = new ArrayList<>();
        for (LivingEntity target : caster.level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, end).inflate(radius + 1.0),
                e -> e != caster && e.isAlive() && e.isPickable())) {
            double along = BeamGeometry.along(target, origin, dir);
            if (along > 0 && along < length && BeamGeometry.touches(target, origin, dir, length, radius)) {
                distances.add(along);
            }
        }
        if (distances.size() < Math.max(1, pierce)) {
            return Double.POSITIVE_INFINITY; // no stop: the beam goes on to the block or render distance
        }
        distances.sort(Double::compare);
        return distances.get(Math.max(1, pierce) - 1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (level == null) {
            HOLDS.clear();
            RAILS.clear();
            CHARGES.clear();
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Hold>> it = HOLDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Hold> entry = it.next();
            Entity caster = level.getEntity(entry.getKey());
            if (caster == null || now > entry.getValue().expires || now < entry.getValue().started) {
                it.remove();
            } else if (!Minecraft.getInstance().isPaused()) {
                emit(level, caster, entry.getValue(), now);
            }
        }
        RAILS.removeIf(rail -> now - rail.fired() > RAIL_TICKS || now < rail.fired());
        for (Iterator<Map.Entry<Integer, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Charge> entry = it.next();
            Entity caster = level.getEntity(entry.getKey());
            if (caster == null || now > entry.getValue().ends() || now < entry.getValue().started()) {
                it.remove();
            } else if (!Minecraft.getInstance().isPaused()) {
                emitCharge(level, caster, entry.getValue(), now);
            }
        }
    }

    // Each style's particles at the hand: sparks blown back, energy drawn in (Rings), smoke (Cannon).
    private static void emit(ClientLevel level, Entity caster, Hold hold, long now) {
        HoldBeamPacket shape = hold.shape;
        int rgb = BeamColors.pulse(baseRgb(shape.color(), AbilityVisuals.BeamKind.ARCANE), shape.color().hueSwing(), now / 20.0);
        Vec3 origin = handOrigin(caster, 1.0F);
        Vec3 look = caster.getViewVector(1.0F);
        BeamStyle style = shape.color().style();
        burst(level, origin, look.scale(-1), rgb, style == BeamStyle.CANNON ? 4 : 2, 0.12);
        if (style == BeamStyle.RINGS) {
            for (int i = 0; i < 2; i++) {
                Vec3 offset = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(1.2);
                Vec3 from = origin.add(offset);
                Vec3 velocity = offset.scale(-0.15);
                level.addParticle(glow(rgb, 0.12F, 8), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
            }
        }
        if (style == BeamStyle.CANNON && now % 2 == 0) {
            level.addParticle(ParticleTypes.SMOKE, origin.x, origin.y, origin.z,
                    RANDOM.nextGaussian() * 0.02, 0.04, RANDOM.nextGaussian() * 0.02);
        }
        for (Segment segment : segments(caster, hold, 1.0F)) {
            Vec3 along = segment.start().add(segment.direction().scale(RANDOM.nextDouble() * segment.length()));
            level.addParticle(glow(rgb, 0.08F, 6), along.x, along.y, along.z, 0, 0, 0);
            if (segment.hit()) {
                burst(level, segment.end(), segment.direction().scale(-1), rgb, 2, 0.1);
            }
        }
    }

    // Energy drawn into the hand, more of it as the charge builds; Cannon also smokes.
    private static void emitCharge(ClientLevel level, Entity caster, Charge charge, long now) {
        ChargePacket packet = charge.packet();
        int rgb = BeamColors.pulse(baseRgb(packet.color(), packet.kind()), packet.color().hueSwing(), now / 20.0);
        float progress = charge.progress(now);
        Vec3 origin = handOrigin(caster, 1.0F);
        int count = 1 + (int) (progress * 4);
        for (int i = 0; i < count; i++) {
            Vec3 offset = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize()
                    .scale(1.0 + RANDOM.nextDouble() * (1.2 - progress * 0.6));
            Vec3 from = origin.add(offset);
            Vec3 velocity = offset.scale(-1.0 / 8);
            level.addParticle(glow(rgb, 0.08F + progress * 0.08F, 8), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
        if (packet.color().style() == BeamStyle.CANNON && now % 3 == 0) {
            level.addParticle(ParticleTypes.SMOKE, origin.x, origin.y, origin.z,
                    RANDOM.nextGaussian() * 0.02, 0.03, RANDOM.nextGaussian() * 0.02);
        }
    }

    // Sparks flying out of a point, mostly along a direction.
    private static void burst(ClientLevel level, Vec3 at, Vec3 direction, int rgb, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3 velocity = direction.normalize().scale(speed)
                    .add(RANDOM.nextGaussian() * speed * 0.6, RANDOM.nextGaussian() * speed * 0.6, RANDOM.nextGaussian() * speed * 0.6);
            level.addParticle(glow(rgb, 0.1F, 10 + RANDOM.nextInt(6)), at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static ColorParticleTypeData glow(int rgb, float size, int age) {
        return new ColorParticleTypeData(new ParticleColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF), false, size, 0.9F, age);
    }

    // VH spawns the Rail's particles inside its cast; a coloured Rail is drawn by BeamRenderer instead, so
    // the arcane particles lying on its line are removed. ParticleEngine's particle queues are private;
    // they are found by type (no names, so no mappings), and on failure the VH particles simply stay.
    private static void removeVaultRailParticles(RailBeamPacket bolt) {
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        Vec3 start = bolt.start();
        Vec3 dir = bolt.direction().normalize();
        double reach = bolt.width() + 0.5;
        for (Collection<?> queue : particleQueues(engine)) {
            for (Object element : queue) {
                if (element instanceof ArcaneParticle particle && particle.isAlive()) {
                    Vec3 center = particle.getBoundingBox().getCenter();
                    double along = center.subtract(start).dot(dir);
                    if (along >= -1.0 && along <= bolt.length() + 1.0 && center.distanceTo(start.add(dir.scale(along))) <= reach) {
                        particle.remove();
                    }
                }
            }
        }
    }

    private static List<Collection<?>> particleQueues(ParticleEngine engine) {
        List<Collection<?>> queues = new ArrayList<>();
        try {
            if (particleFields == null) {
                particleFields = new ArrayList<>();
                for (Field field : ParticleEngine.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType()) || Queue.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        particleFields.add(field);
                    }
                }
            }
            for (Field field : particleFields) {
                Object value = field.get(engine);
                if (value instanceof Queue<?> queue) {
                    queues.add(queue);
                } else if (value instanceof Map<?, ?> map) {
                    for (Object inner : map.values()) {
                        if (inner instanceof Queue<?> queue) {
                            queues.add(queue);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VaultSpellbook.LOGGER.warn("Could not read the particle engine; VH's rail particles stay under coloured rails", e);
            particleFields = List.of();
        }
        return queues;
    }
}
