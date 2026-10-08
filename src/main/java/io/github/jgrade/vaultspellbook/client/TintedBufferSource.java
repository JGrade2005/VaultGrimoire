package io.github.jgrade.vaultspellbook.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// Draws an entity in an ability colour: every vertex colour is multiplied by the
// colour's per-channel factor, which the shader multiplies with the texture. Recolor first swaps the
// render type's texture for a grey copy (GreyscaleTextures); where that isn't possible it is a filter.
public record TintedBufferSource(MultiBufferSource delegate, AbilityColor color) implements MultiBufferSource {
    @Override
    public VertexConsumer getBuffer(RenderType type) {
        RenderType used = color.mode() == AbilityColor.Mode.RECOLOR ? GreyscaleTextures.swap(type) : type;
        return new Tinting(delegate.getBuffer(used), color.factor(color.red()), color.factor(color.green()), color.factor(color.blue()));
    }

    private record Tinting(VertexConsumer delegate, float red, float green, float blue) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color((int) (r * red), (int) (g * green), (int) (b * blue), a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor((int) (r * red), (int) (g * green), (int) (b * blue), a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
