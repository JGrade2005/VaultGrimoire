package io.github.jgrade.vaultspellbook.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.color.BeamStyle;
import io.github.jgrade.vaultspellbook.config.VaultSpellbookClientConfig;
import io.github.jgrade.vaultspellbook.network.ChargePacket;
import io.github.jgrade.vaultspellbook.network.HoldBeamPacket;
import io.github.jgrade.vaultspellbook.network.RailBeamPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Random;

// Draws coloured Arcane beams and Arcane Rail bolts in the world, after particles, as
// additive coloured quads (BeamRenderTypes.BEAM): a solid beam of glow layers around a white core whose
// hue pulses by the colour's hue swing, plus each style's extras: spiral ribbons and lightning (Spiral),
// shockwave rings (Rings), a taper with a spiky burst and lightning veins (Cannon). A flare at the hand
// and a burst where the beam hits. Quads face the camera around the beam's axis.
@Mod.EventBusSubscriber(modid = VaultSpellbook.MOD_ID, value = Dist.CLIENT)
public final class BeamRenderer {
    // Per glow layer, outer to core: width share, saturation share, opacity. The colour tab's preview uses it too.
    static final float[][] LAYERS = {{1.0F, 1.0F, 0.30F}, {0.72F, 0.85F, 0.55F}, {0.42F, 0.45F, 0.80F}, {0.18F, 0.0F, 0.95F}};
    private static final double RING_SPACING = 1.6;
    private static final double RING_SPEED = 14.0;

    private BeamRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || (ClientBeams.holds().isEmpty() && ClientBeams.rails().isEmpty() && ClientBeams.charges().isEmpty())) {
            return;
        }
        float partialTicks = event.getPartialTick();
        double now = level.getGameTime() + partialTicks;
        double seconds = now / 20.0;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Drawer draw = new Drawer(buffers.getBuffer(BeamRenderTypes.BEAM), event.getPoseStack().last().pose(), event.getCamera());

        for (Map.Entry<Integer, ClientBeams.Hold> entry : ClientBeams.holds().entrySet()) {
            Entity caster = level.getEntity(entry.getKey());
            if (caster == null) {
                continue;
            }
            HoldBeamPacket shape = entry.getValue().shape();
            float alpha = (float) Math.min(1.0, (now - entry.getValue().started()) / 3.0);
            int rgb = BeamColors.pulse(ClientBeams.baseRgb(shape.color(), AbilityVisuals.BeamKind.ARCANE), shape.color().hueSwing(),
                    seconds);
            BeamStyle style = shape.color().style();
            int index = 0;
            float half = shape.radius() * widthFor(shape.power());
            float glow = alpha * glowFor(shape.power());
            if (isOwnFirstPerson(minecraft, caster)) {
                half = Math.min(half, VaultSpellbookClientConfig.FIRST_PERSON_BEAM_MAX_RADIUS.get().floatValue());
                glow *= VaultSpellbookClientConfig.FIRST_PERSON_BEAM_OPACITY.get().floatValue();
            }
            for (ClientBeams.Segment segment : ClientBeams.segments(caster, entry.getValue(), partialTicks)) {
                beam(draw, style, segment, half, rgb, shape.color().hueSwing(), glow, seconds, entry.getKey() * 31L + index++);
            }
            Vec3 origin = ClientBeams.handOrigin(caster, partialTicks);
            hand(draw, style, origin, caster.getViewVector(partialTicks), half, rgb, shape.color().hueSwing(), glow, seconds);
        }

        for (ClientBeams.Rail rail : ClientBeams.rails()) {
            RailBeamPacket bolt = rail.bolt();
            double age = now - rail.fired();
            float life = (float) Math.max(0.0, 1.0 - age / ClientBeams.RAIL_TICKS);
            int rgb = BeamColors.pulse(ClientBeams.baseRgb(bolt.color(), AbilityVisuals.BeamKind.RAIL), bolt.color().hueSwing(), seconds);
            ClientBeams.Segment segment = new ClientBeams.Segment(bolt.start(), bolt.direction().normalize(), bolt.length(), true);
            float half = bolt.width() * (0.6F + 0.4F * life) * widthFor(bolt.power());
            float glow = life * glowFor(bolt.power());
            if (isOwnFirstPerson(minecraft, level.getEntity(bolt.casterId()))) {
                half = Math.min(half, VaultSpellbookClientConfig.FIRST_PERSON_BEAM_MAX_RADIUS.get().floatValue());
                glow *= VaultSpellbookClientConfig.FIRST_PERSON_BEAM_OPACITY.get().floatValue();
            }
            beam(draw, bolt.color().style(), segment, half, rgb, bolt.color().hueSwing(), glow, seconds,
                    bolt.casterId() * 17L + rail.fired());
            if (age < 6) {
                hand(draw, bolt.color().style(), bolt.start(), segment.direction(), half, rgb, bolt.color().hueSwing(), glow, seconds);
            }
        }
        for (Map.Entry<Integer, ClientBeams.Charge> entry : ClientBeams.charges().entrySet()) {
            Entity caster = level.getEntity(entry.getKey());
            if (caster != null) {
                double scale = isOwnFirstPerson(minecraft, caster) ? 0.55 : 1.0;
                charge(draw, entry.getValue(), ClientBeams.handOrigin(caster, partialTicks), scale, now, seconds);
            }
        }
        buffers.endBatch(BeamRenderTypes.BEAM);
    }

    // A charge at the hand: a core that swells, spikes that lengthen and spin faster,
    // rings closing in on the hand (Rings: more of them; Spiral: two arms winding in; Cannon: the spiky
    // burst growing), and a flash just before the beam fires.
    private static void charge(Drawer draw, ClientBeams.Charge charge, Vec3 origin, double scale, double now, double seconds) {
        ChargePacket packet = charge.packet();
        float progress = charge.progress(now);
        int swing = packet.color().hueSwing();
        int rgb = BeamColors.pulse(ClientBeams.baseRgb(packet.color(), packet.kind()), swing, seconds);
        BeamStyle style = packet.color().style();
        double half = 0.3 * scale; // smaller in first person, where the hand is just in front of the eyes
        double pulse = 1 + 0.12 * Math.sin(seconds * (10 + 20 * progress));
        double core = half * (0.4 + 1.6 * progress) * pulse;
        draw.disc(origin, core, BeamColors.layer(rgb, 1.0F), 0.35F);
        draw.disc(origin, core * 0.6, BeamColors.layer(rgb, 0.5F), 0.7F);
        draw.disc(origin, core * 0.3, 0xFFFFFF, 0.95F);
        Basis facing = draw.cameraBasis();
        int rings = style == BeamStyle.RINGS ? 4 : 2;
        int ringColor = BeamColors.layer(BeamColors.shiftHue(rgb, swing * 0.5F), 0.3F);
        for (int k = 0; k < rings; k++) {
            double phase = (seconds * (0.8 + progress) + k / (double) rings) % 1.0;
            double radius = (1.0 - phase) * (0.5 + 1.2 * (1.0 - progress * 0.5)) * scale;
            draw.ring(origin, facing, radius, 0.02 + 0.02 * progress, ringColor, (float) (phase * 0.9));
        }
        if (style == BeamStyle.SPIRAL) {
            int arm = BeamColors.layer(BeamColors.shiftHue(rgb, -swing * 0.6F), 1.0F);
            for (double offset : new double[]{0, Math.PI}) {
                Vec3 previous = null;
                for (int i = 0; i <= 24; i++) {
                    double t = i / 24.0;
                    double radius = (1.0 - t) * 1.1 * scale;
                    double angle = t * 7 + seconds * (4 + 8 * progress) + offset;
                    Vec3 point = origin.add(facing.u.scale(Math.cos(angle) * radius)).add(facing.v.scale(Math.sin(angle) * radius));
                    if (previous != null) {
                        draw.ribbon(previous, point, 0.025, 0.025, arm, 0.7F * (float) t);
                    }
                    previous = point;
                }
            }
        }
        if (style == BeamStyle.CANNON) {
            draw.burst(origin, 22, half * (0.4 + progress), rgb, swing, 0.4F + 0.6F * progress, seconds);
        } else {
            draw.spikes(origin, 8 + (int) (progress * 10), half * (0.8 + 2.4 * progress), BeamColors.layer(rgb, 0.6F),
                    0.4F + 0.5F * progress, seconds * (1 + 4 * progress), 11);
        }
        if (progress > 0.85F) {
            draw.disc(origin, half * 3.0 * (progress - 0.85) / 0.15, 0xFFFFFF, 0.5F * (progress - 0.85F) / 0.15F);
        }
    }

    private static void beam(Drawer draw, BeamStyle style, ClientBeams.Segment segment, float half, int rgb, int swing,
                             float alpha, double seconds, long seed) {
        Taper taper = style == BeamStyle.CANNON ? t -> 0.25 + 1.35 * Math.pow(t, 0.6) : t -> 1.0;
        double length = segment.length();
        int steps = Math.max(4, Math.min(96, (int) (length / 0.4)));
        Vec3 start = segment.start();
        Vec3 dir = segment.direction();
        for (int layer = 0; layer < LAYERS.length; layer++) {
            float[] spec = LAYERS[layer];
            int color = layer == 1 ? BeamColors.layer(BeamColors.shiftHue(rgb, swing * 0.4F), spec[1]) : BeamColors.layer(rgb, spec[1]);
            Vec3 previous = start;
            double previousHalf = 0;
            for (int i = 0; i <= steps; i++) {
                double s = length * i / steps;
                double wobble = 1 + 0.12 * Math.sin(s * 2.2 - seconds * 22 + layer) + 0.06 * Math.sin(s * 7 + seconds * 31);
                double h = half * spec[0] * taper.at(s / length) * near(s) * wobble;
                Vec3 point = start.add(dir.scale(s));
                if (i > 0) {
                    draw.ribbon(previous, point, previousHalf, h, color, spec[2] * alpha);
                }
                previous = point;
                previousHalf = h;
            }
        }
        Basis basis = Basis.around(dir);
        if (style == BeamStyle.SPIRAL) {
            int ribbon = BeamColors.layer(BeamColors.shiftHue(rgb, -swing * 0.6F), 1.0F);
            for (double phase : new double[]{0, Math.PI}) {
                Vec3 previous = null;
                for (int i = 0; i <= steps * 2; i++) {
                    double s = length * i / (steps * 2);
                    double angle = s * 1.4 - seconds * 9 + phase;
                    double radius = half * 1.7 * near(s);
                    Vec3 point = start.add(dir.scale(s)).add(basis.u.scale(Math.cos(angle) * radius))
                            .add(basis.v.scale(Math.sin(angle) * radius));
                    if (previous != null) {
                        draw.ribbon(previous, point, 0.035 + half * 0.08, 0.035 + half * 0.08, ribbon, 0.75F * alpha);
                    }
                    previous = point;
                }
            }
        }
        if (style == BeamStyle.SPIRAL || style == BeamStyle.CANNON) {
            int arc = BeamColors.layer(BeamColors.shiftHue(rgb, 20), 0.3F);
            lightning(draw, start, dir, basis, length, half * (style == BeamStyle.CANNON ? 1.6 : 1.1), arc, alpha, seconds, seed);
        }
        if (style == BeamStyle.RINGS) {
            int ring = BeamColors.layer(BeamColors.shiftHue(rgb, swing * 0.5F), 0.3F);
            for (double s = (seconds * RING_SPEED) % RING_SPACING; s < length; s += RING_SPACING) {
                double progress = s / length;
                draw.ring(start.add(dir.scale(s)), basis, half * (1.4 + 0.8 * progress) * near(s), 0.03, ring,
                        (float) (0.9 * (1 - 0.6 * progress)) * alpha);
            }
        }
        if (segment.hit()) {
            Vec3 end = segment.end();
            double size = half * (style == BeamStyle.CANNON ? 3.2 : 2.0);
            draw.disc(end, size * 0.45 * (1 + 0.2 * Math.sin(seconds * 17)), BeamColors.layer(rgb, 0.2F), 0.9F * alpha);
            draw.spikes(end, 10, size, BeamColors.layer(rgb, 0.5F), 0.75F * alpha, seconds, seed);
        }
    }

    // The flare at the hand: glowing discs and spikes; Cannon has a wide spiky burst instead of thin spikes.
    private static void hand(Drawer draw, BeamStyle style, Vec3 origin, Vec3 look, float half, int rgb, int swing, float alpha,
                             double seconds) {
        double radius = half * (style == BeamStyle.RINGS ? 2.4 : style == BeamStyle.SPIRAL ? 2.0 : 1.8);
        double pulse = 1 + 0.15 * Math.sin(seconds * 14);
        draw.disc(origin, radius * 0.55 * pulse, BeamColors.layer(rgb, 1.0F), 0.35F * alpha);
        draw.disc(origin, radius * 0.33 * pulse, BeamColors.layer(rgb, 0.5F), 0.7F * alpha);
        draw.disc(origin, radius * 0.17 * pulse, 0xFFFFFF, alpha);
        switch (style) {
            case SOLID_CORE -> draw.spikes(origin, 10, radius, BeamColors.layer(rgb, 0.6F), 0.7F * alpha, seconds, 3);
            case SPIRAL -> draw.spikes(origin, 14, radius, BeamColors.layer(rgb, 0.6F), 0.7F * alpha, seconds, 5);
            case RINGS -> draw.spikes(origin, 16, radius, BeamColors.layer(BeamColors.shiftHue(rgb, 20), 0.4F), 0.7F * alpha, seconds, 7);
            case CANNON -> draw.burst(origin, 22, half, rgb, swing, alpha, seconds);
        }
    }

    private static void lightning(Drawer draw, Vec3 start, Vec3 dir, Basis basis, double length, double spread, int color,
                                  float alpha, double seconds, long seed) {
        Random random = new Random(seed * 7919L + (long) (seconds * 15));
        for (int arc = 0; arc < 3; arc++) {
            double from = 0.5 + random.nextDouble() * Math.max(0.5, length - 2.0);
            double offsetU = (random.nextDouble() - 0.5) * 2 * spread;
            double offsetV = (random.nextDouble() - 0.5) * 2 * spread;
            Vec3 previous = null;
            for (double s = from; s < Math.min(length, from + 1.6); s += 0.25) {
                offsetU = Math.max(-spread * 1.6, Math.min(spread * 1.6, offsetU + (random.nextDouble() - 0.5) * spread));
                offsetV = Math.max(-spread * 1.6, Math.min(spread * 1.6, offsetV + (random.nextDouble() - 0.5) * spread));
                Vec3 point = start.add(dir.scale(s)).add(basis.u.scale(offsetU)).add(basis.v.scale(offsetV));
                if (previous != null) {
                    draw.ribbon(previous, point, 0.015, 0.015, color, 0.85F * alpha);
                }
                previous = point;
            }
        }
    }

    // A charged beam shows its damage multiplier: about twice as wide at 10x, thinner at 0.5x.
    private static float widthFor(float power) {
        return power >= 1.0F ? 1.0F + 0.12F * (power - 1.0F) : 0.75F;
    }

    // ... and brighter at high multipliers, dimmer below 1x.
    private static float glowFor(float power) {
        return power >= 1.0F ? Math.min(1.35F, 1.0F + 0.04F * (power - 1.0F)) : 0.7F;
    }

    // The viewer's own beam seen in first person: dimmed and capped in width (client config) so they can
    // still see what they aim at.
    private static boolean isOwnFirstPerson(Minecraft minecraft, Entity caster) {
        return caster != null && caster == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
    }

    // Fades a beam in over its first block, so it doesn't fill the screen in first person.
    private static double near(double s) {
        return Math.min(1.0, 0.35 + s / 1.2);
    }

    @FunctionalInterface
    private interface Taper {
        double at(double t);
    }

    // Two unit vectors at right angles to a beam's direction.
    private record Basis(Vec3 u, Vec3 v) {
        static Basis around(Vec3 dir) {
            Vec3 helper = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = dir.cross(helper).normalize();
            return new Basis(u, dir.cross(u).normalize());
        }
    }

    // Emits coloured quads in camera-relative coordinates.
    private static final class Drawer {
        private final VertexConsumer consumer;
        private final Matrix4f matrix;
        private final Vec3 camera;
        private final Vec3 cameraLeft;
        private final Vec3 cameraUp;

        Drawer(VertexConsumer consumer, Matrix4f matrix, Camera camera) {
            this.consumer = consumer;
            this.matrix = matrix;
            this.camera = camera.getPosition();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            this.cameraLeft = new Vec3(left.x(), left.y(), left.z());
            this.cameraUp = new Vec3(up.x(), up.y(), up.z());
        }

        // A strip from a to b, widest across the line of sight.
        void ribbon(Vec3 a, Vec3 b, double halfA, double halfB, int rgb, float alpha) {
            Vec3 axis = b.subtract(a);
            Vec3 side = axis.cross(camera.subtract(a.add(b).scale(0.5)));
            if (side.lengthSqr() < 1.0E-10) {
                side = cameraUp;
            }
            side = side.normalize();
            quad(a.subtract(side.scale(halfA)), a.add(side.scale(halfA)), b.add(side.scale(halfB)), b.subtract(side.scale(halfB)),
                    rgb, alpha);
        }

        // A ring around a beam, in the plane at right angles to it.
        void ring(Vec3 center, Basis basis, double radius, double thickness, int rgb, float alpha) {
            Vec3 previous = null;
            for (int i = 0; i <= 20; i++) {
                double angle = i * Math.PI * 2 / 20;
                Vec3 point = center.add(basis.u.scale(Math.cos(angle) * radius)).add(basis.v.scale(Math.sin(angle) * radius));
                if (previous != null) {
                    ribbon(previous, point, thickness, thickness, rgb, alpha);
                }
                previous = point;
            }
        }

        // A disc facing the camera.
        void disc(Vec3 center, double radius, int rgb, float alpha) {
            Vec3 previous = null;
            for (int i = 0; i <= 14; i++) {
                double angle = i * Math.PI * 2 / 14;
                Vec3 point = center.add(cameraLeft.scale(Math.cos(angle) * radius)).add(cameraUp.scale(Math.sin(angle) * radius));
                if (previous != null) {
                    quad(center, center, previous, point, rgb, alpha);
                }
                previous = point;
            }
        }

        // Thin rays facing the camera, turning slowly and flickering in length.
        void spikes(Vec3 center, int count, double length, int rgb, float alpha, double seconds, long seed) {
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count + seconds * 1.5;
                double reach = length * (0.7 + 0.5 * Math.abs(Math.sin(i * 2.3 + seed + seconds * 9)));
                Vec3 out = direction(angle);
                Vec3 across = direction(angle + Math.PI / 2).scale(length * 0.03);
                quad(center.add(across), center.subtract(across), center.add(out.scale(reach)), center.add(out.scale(reach)), rgb, alpha);
            }
        }

        // Cannon's burst: wide alternating-hue spikes.
        void burst(Vec3 center, int count, double half, int rgb, int swing, float alpha, double seconds) {
            for (int i = 0; i < count; i++) {
                double angle = i * Math.PI * 2 / count;
                double reach = half * (1.6 + 1.6 * Math.abs(Math.sin(i * 1.9 + seconds * 12)));
                int color = BeamColors.layer(BeamColors.shiftHue(rgb, (i % 2 == 0 ? swing : -swing) * 0.5F), 1.0F);
                Vec3 baseA = center.add(direction(angle - 0.12).scale(half * 0.4));
                Vec3 baseB = center.add(direction(angle + 0.12).scale(half * 0.4));
                Vec3 tip = center.add(direction(angle).scale(reach));
                quad(baseA, baseB, tip, tip, color, 0.75F * alpha);
            }
        }

        // The camera's left and up vectors: draws flat shapes facing the viewer.
        Basis cameraBasis() {
            return new Basis(cameraLeft, cameraUp);
        }

        private Vec3 direction(double angle) {
            return cameraLeft.scale(Math.cos(angle)).add(cameraUp.scale(Math.sin(angle)));
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
            int red = rgb >> 16 & 0xFF;
            int green = rgb >> 8 & 0xFF;
            int blue = rgb & 0xFF;
            int a8 = Math.max(0, Math.min(255, (int) (alpha * 255)));
            for (Vec3 p : new Vec3[]{a, b, c, d}) {
                consumer.vertex(matrix, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                        .color(red, green, blue, a8).endVertex();
            }
        }
    }
}
