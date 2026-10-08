package io.github.jgrade.vaultspellbook.client;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellCaster;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.BookSlider;
import com.hollingsworth.arsnouveau.client.gui.book.slider.ANProgressOption;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import io.github.jgrade.vaultspellbook.VaultSpellbook;
import io.github.jgrade.vaultspellbook.color.AbilityColor;
import io.github.jgrade.vaultspellbook.color.AbilityVisuals;
import io.github.jgrade.vaultspellbook.color.BeamStyle;
import io.github.jgrade.vaultspellbook.color.SpellColors;
import io.github.jgrade.vaultspellbook.form.BundledSpellGlyph;
import io.github.jgrade.vaultspellbook.glyph.AbilityGlyph;
import io.github.jgrade.vaultspellbook.item.VaultBookCaster;
import io.github.jgrade.vaultspellbook.network.ModNetwork;
import io.github.jgrade.vaultspellbook.network.SetAbilityColorPacket;
import io.github.jgrade.vaultspellbook.tier.VaultTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

// The Vault Spellbook's colour tab, opened by Ars's colour bookmark. Left page: a spell
// slot picker and the ability glyphs (and Bundled Spells) of that spell. Right page: a preview of the
// selected one (its entity in 3D, turning, or its icon), Filter / Recolor, red/green/blue and strength
// sliders, Ars's colour presets, and Save / Reset. Beam abilities (Arcane, Arcane Rail) also pick a beam
// style and its hue pulse, previewed as an animated beam. Colours are saved on the book
// per slot and glyph position (SpellColors) through SetAbilityColorPacket. Escape goes back to the book.
public class VaultColorScreen extends BaseBook {
    private static final int TEXT = -8355712;
    private static final int TEXT_DIM = 0xFFB0A28A;
    private static final int TEXT_DARK = 0xFF3F2F1F;
    private static final int[] PRESETS = {0xFF19B4, 0x5019FF, 0x1E19FF, 0xFF1919, 0x19FF19, 0xFFFF19, 0xFFFFFF, 0xFF5A01, 0x19FFFF};
    private static final int ROWS = 6;
    private static final int ROW_HEIGHT = 17;
    private static final int LIST_TOP = 38;
    private static final int PREVIEW_X = 160;
    private static final int PREVIEW_Y = 20;
    private static final int PREVIEW_W = 110;
    private static final int PREVIEW_H = 34;
    private static final int SWATCH_X = 20;
    private static final int SWATCH_Y = 153;
    private static final int BUTTONS_Y = 168;
    // Beam glow layers for the preview: width share, saturation share, opacity (as BeamRenderer).

    private final ItemStack book;
    private final ISpellCaster caster;
    private int slot;
    private final List<Entry> entries = new ArrayList<>();
    private int scroll;
    @Nullable
    private Entry selected;

    private AbilityColor.Mode mode = AbilityColor.Mode.FILTER;
    private double red = 255;
    private double green = 255;
    private double blue = 255;
    private double strength = 100;
    private BeamStyle style = BeamStyle.SOLID_CORE;
    private double hueSwing = AbilityColor.DEFAULT_HUE_SWING;
    private BookSlider redSlider;
    private BookSlider greenSlider;
    private BookSlider blueSlider;
    private BookSlider strengthSlider;
    private BookSlider swingSlider;
    private FlatButton styleButton;
    private FlatButton filterButton;
    private FlatButton recolorButton;
    private FlatButton saveButton;
    private FlatButton resetButton;
    private Component status = TextComponent.EMPTY;

    private final Map<EntityType<?>, Entity> previews = new HashMap<>();
    private final Set<EntityType<?>> failedPreviews = new HashSet<>();

    public VaultColorScreen(ItemStack book, int slot) {
        this.book = book;
        this.caster = new VaultBookCaster(book);
        this.slot = slot;
    }

    @Override
    public void init() {
        super.init();
        int right = bookLeft + PREVIEW_X;
        filterButton = addRenderableWidget(new FlatButton(right, bookTop + 57, 52, 12,
                new TranslatableComponent("vaultspellbook.color_gui.filter"), b -> setMode(AbilityColor.Mode.FILTER)));
        recolorButton = addRenderableWidget(new FlatButton(right + 56, bookTop + 57, 52, 12,
                new TranslatableComponent("vaultspellbook.color_gui.recolor"), b -> setMode(AbilityColor.Mode.RECOLOR)));
        styleButton = addRenderableWidget(new FlatButton(right, bookTop + 71, 108, 12, TextComponent.EMPTY, b -> setStyle(style.next())));
        redSlider = addRenderableWidget(slider("vaultspellbook.color_gui.red", 0, 255, () -> red, v -> red = v, right + 3, 89));
        greenSlider = addRenderableWidget(slider("vaultspellbook.color_gui.green", 0, 255, () -> green, v -> green = v, right + 3, 108));
        blueSlider = addRenderableWidget(slider("vaultspellbook.color_gui.blue", 0, 255, () -> blue, v -> blue = v, right + 3, 127));
        strengthSlider = addRenderableWidget(slider("vaultspellbook.color_gui.strength", 0, 100, () -> strength, v -> strength = v,
                right + 3, 146));
        swingSlider = addRenderableWidget(slider("vaultspellbook.color_gui.hue_swing", 0, AbilityColor.MAX_HUE_SWING,
                () -> hueSwing, v -> hueSwing = v, right + 3, 165));
        saveButton = addRenderableWidget(new FlatButton(bookLeft + SWATCH_X, bookTop + BUTTONS_Y, 52, 12,
                new TranslatableComponent("vaultspellbook.color_gui.save"), b -> save(false)));
        resetButton = addRenderableWidget(new FlatButton(bookLeft + SWATCH_X + 56, bookTop + BUTTONS_Y, 52, 12,
                new TranslatableComponent("vaultspellbook.color_gui.reset"), b -> save(true)));
        loadSlot();
    }

    private BookSlider slider(String key, double min, double max, DoubleSupplier get, DoubleConsumer set, int x, int y) {
        String label = new TranslatableComponent(key).getString();
        return new ANProgressOption(label, min, max, 1.0F, o -> get.getAsDouble(), (o, v) -> set.accept(v),
                (o, option) -> new TextComponent(label + (int) get.getAsDouble())).createButton(minecraft.options, x, bookTop + y, 100);
    }

    // Lists the selected slot's ability glyphs and Bundled Spells, and selects the first colourable one.
    private void loadSlot() {
        entries.clear();
        scroll = 0;
        List<AbstractSpellPart> recipe = caster.getSpell(slot).recipe;
        for (int position = 0; position < recipe.size(); position++) {
            AbstractSpellPart part = recipe.get(position);
            boolean ability = part instanceof AbilityGlyph glyph && VaultTier.isVault(glyph) && glyph.isInSkillScreen();
            if (ability || part instanceof BundledSpellGlyph) {
                entries.add(new Entry(position, part, AbilityVisuals.isColorable(part)));
            }
        }
        select(entries.stream().filter(Entry::colorable).findFirst().orElse(null));
    }

    private void select(@Nullable Entry entry) {
        selected = entry;
        status = TextComponent.EMPTY;
        AbilityColor color = entry == null ? null : SpellColors.get(book, slot, entry.position(), entry.part());
        setMode(color == null ? AbilityColor.Mode.FILTER : color.mode());
        redSlider.setInitVal((color == null ? 255 : color.red()) / 255.0);
        greenSlider.setInitVal((color == null ? 255 : color.green()) / 255.0);
        blueSlider.setInitVal((color == null ? 255 : color.blue()) / 255.0);
        strengthSlider.setInitVal(color == null ? 1.0 : color.strength());
        setStyle(color == null ? BeamStyle.SOLID_CORE : color.style());
        swingSlider.setInitVal((color == null ? AbilityColor.DEFAULT_HUE_SWING : color.hueSwing()) / (double) AbilityColor.MAX_HUE_SWING);
        boolean editable = entry != null;
        List<AbstractWidget> editors = List.of(redSlider, greenSlider, blueSlider, strengthSlider, filterButton, recolorButton,
                saveButton, resetButton);
        for (AbstractWidget widget : editors) {
            widget.visible = editable;
        }
        boolean beam = editable && AbilityVisuals.beamKind(entry.part()) != null;
        styleButton.visible = beam;
        swingSlider.visible = beam;
    }

    private void setStyle(BeamStyle style) {
        this.style = style;
        styleButton.setMessage(new TranslatableComponent("vaultspellbook.color_gui.style",
                new TranslatableComponent(style.translationKey())));
    }

    private void setMode(AbilityColor.Mode mode) {
        this.mode = mode;
        filterButton.selected = mode == AbilityColor.Mode.FILTER;
        recolorButton.selected = mode == AbilityColor.Mode.RECOLOR;
    }

    private void setRgb(int rgb) {
        redSlider.setInitVal((rgb >> 16 & 0xFF) / 255.0);
        greenSlider.setInitVal((rgb >> 8 & 0xFF) / 255.0);
        blueSlider.setInitVal((rgb & 0xFF) / 255.0);
    }

    private AbilityColor editingColor() {
        int rgb = (int) red << 16 | (int) green << 8 | (int) blue;
        return new AbilityColor(mode, rgb, (float) (strength / 100.0), style, (int) Math.round(hueSwing));
    }

    private void save(boolean reset) {
        if (selected == null) {
            return;
        }
        AbilityColor color = reset ? null : editingColor();
        SpellColors.set(book, slot, selected.position(), selected.part().getId(), color); // this screen's copy; the server saves its own
        ModNetwork.sendToServer(new SetAbilityColorPacket(slot, selected.position(), selected.part().getId(), color));
        if (reset) {
            select(selected);
        }
        status = new TranslatableComponent(reset ? "vaultspellbook.color_gui.reset_done" : "vaultspellbook.color_gui.saved");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - bookLeft;
        int y = (int) mouseY - bookTop;
        if (y >= 22 && y < 34) {
            if (x >= 18 && x < 30) {
                changeSlot(-1);
                return true;
            }
            if (x >= 120 && x < 132) {
                changeSlot(1);
                return true;
            }
        }
        int row = (y - LIST_TOP) / ROW_HEIGHT;
        if (x >= 18 && x < 134 && y >= LIST_TOP && row < ROWS && row + scroll < entries.size()) {
            Entry entry = entries.get(row + scroll);
            if (entry.colorable()) {
                select(entry);
            }
            return true;
        }
        if (selected != null && y >= SWATCH_Y && y < SWATCH_Y + 9) {
            int index = (x - SWATCH_X) / 12;
            if (x >= SWATCH_X && index < PRESETS.length && (x - SWATCH_X) % 12 < 9) {
                setRgb(PRESETS[index]);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, entries.size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    private void changeSlot(int step) {
        int max = caster.getMaxSlots();
        slot = (slot - 1 + step + max) % max + 1;
        loadSlot();
    }

    @Override
    public void onClose() {
        VaultSpellBookScreen.open(book);
    }

    @Override
    public void drawBackgroundElements(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        super.drawBackgroundElements(stack, mouseX, mouseY, partialTicks);
        int mx = mouseX - bookLeft;
        int my = mouseY - bookTop;
        drawLeftPage(stack, mx, my);
        drawRightPage(stack, partialTicks);
    }

    private void drawLeftPage(PoseStack stack, int mx, int my) {
        drawCentered(stack, new TranslatableComponent("vaultspellbook.color_gui.title"), 76, 10, TEXT);
        String name = caster.getSpellName(slot);
        Component slotLabel = name.isEmpty() ? new TranslatableComponent("vaultspellbook.color_gui.spell", slot)
                : new TextComponent(slot + ": " + name);
        font.draw(stack, "<", 21, 24, TEXT);
        font.draw(stack, ">", 124, 24, TEXT);
        drawCentered(stack, new TextComponent(font.plainSubstrByWidth(slotLabel.getString(), 88)), 76, 24, TEXT);
        if (selected != null) {
            for (int i = 0; i < PRESETS.length; i++) {
                int x = SWATCH_X + i * 12;
                fill(stack, x, SWATCH_Y, x + 9, SWATCH_Y + 9, 0xFF3F2F1F);
                fill(stack, x + 1, SWATCH_Y + 1, x + 8, SWATCH_Y + 8, 0xFF000000 | PRESETS[i]);
            }
        }
        if (entries.isEmpty()) {
            drawCentered(stack, new TranslatableComponent("vaultspellbook.color_gui.empty"), 76, LIST_TOP + 4, TEXT_DIM);
            return;
        }
        for (int row = 0; row < ROWS && row + scroll < entries.size(); row++) {
            Entry entry = entries.get(row + scroll);
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (entry == selected) {
                fill(stack, 18, y - 1, 134, y + ROW_HEIGHT - 1, 0x40000000);
            }
            renderIcon(entry.part(), bookLeft + 20, bookTop + y, 1.0F, null);
            String label = font.plainSubstrByWidth(entry.part().getLocaleName(), 84);
            font.draw(stack, label, 38, y + 4, entry.colorable() ? TEXT_DARK : TEXT_DIM);
            AbilityColor color = SpellColors.get(book, slot, entry.position(), entry.part());
            if (color != null) {
                fill(stack, 125, y + 4, 132, y + 11, 0xFF000000);
                fill(stack, 126, y + 5, 131, y + 10, 0xFF000000 | color.rgb());
            }
            if (!entry.colorable() && mx >= 18 && mx < 134 && my >= y && my < y + ROW_HEIGHT) {
                tooltip = List.of(new TranslatableComponent("vaultspellbook.color_gui.not_colorable"));
            }
        }
        if (entries.size() > ROWS) {
            String range = (scroll + 1) + "-" + Math.min(entries.size(), scroll + ROWS) + "/" + entries.size();
            font.draw(stack, range, 132 - font.width(range), LIST_TOP + ROWS * ROW_HEIGHT + 1, TEXT_DIM);
        }
    }

    private void drawRightPage(PoseStack stack, float partialTicks) {
        if (selected == null) {
            drawCentered(stack, new TranslatableComponent("vaultspellbook.color_gui.select"), 215, 80, TEXT_DIM);
            return;
        }
        Component title = status.getString().isEmpty()
                ? new TextComponent(font.plainSubstrByWidth(selected.part().getLocaleName(), PREVIEW_W)) : status;
        drawCentered(stack, title, 215, 10, status.getString().isEmpty() ? TEXT_DARK : TEXT);
        fill(stack, PREVIEW_X - 1, PREVIEW_Y - 1, PREVIEW_X + PREVIEW_W + 1, PREVIEW_Y + PREVIEW_H + 1, 0xFF3F2F1F);
        fill(stack, PREVIEW_X, PREVIEW_Y, PREVIEW_X + PREVIEW_W, PREVIEW_Y + PREVIEW_H, 0xFF1E1A16);
        AbilityColor color = editingColor();
        AbilityVisuals.BeamKind kind = AbilityVisuals.beamKind(selected.part());
        if (kind != null) {
            drawBeamPreview(stack, color, kind, partialTicks);
            return;
        }
        if (!renderEntityPreview(selected.part(), color, partialTicks)) {
            renderIcon(selected.part(), bookLeft + PREVIEW_X + PREVIEW_W / 2 - 16, bookTop + PREVIEW_Y + 4, 2.0F, color);
        }
        int swatch = 0xFF000000 | color.flatten(0xFFFFFF);
        fill(stack, PREVIEW_X + PREVIEW_W - 12, PREVIEW_Y + 2, PREVIEW_X + PREVIEW_W - 2, PREVIEW_Y + 12, swatch);
    }

    // A flat, animated sketch of the beam in the preview box, like the in-game beam (BeamRenderer): glow
    // layers around a white core with the hue pulse, and the style's extras.
    private void drawBeamPreview(PoseStack stack, AbilityColor color, AbilityVisuals.BeamKind kind, float partialTicks) {
        double seconds = ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTicks) / 20.0;
        int rgb = BeamColors.pulse(color.flatten(kind.defaultRgb), color.hueSwing(), seconds);
        int hand = PREVIEW_X + 12;
        int end = PREVIEW_X + PREVIEW_W - 8;
        int cy = PREVIEW_Y + PREVIEW_H / 2;
        double half = 5.0;
        for (int layer = 0; layer < BeamRenderer.LAYERS.length; layer++) {
            float[] spec = BeamRenderer.LAYERS[layer];
            int layerColor = layer == 1 ? BeamColors.layer(BeamColors.shiftHue(rgb, color.hueSwing() * 0.4F), spec[1])
                    : BeamColors.layer(rgb, spec[1]);
            for (int x = hand; x <= end; x++) {
                double t = (x - hand) / (double) (end - hand);
                double taper = style == BeamStyle.CANNON ? 0.25 + 1.35 * Math.pow(t, 0.6) : 1.0;
                double wobble = 1 + 0.12 * Math.sin(x * 0.35 - seconds * 22 + layer) + 0.06 * Math.sin(x * 1.3 + seconds * 31);
                int h = (int) Math.round(half * spec[0] * taper * Math.min(1.0, 0.35 + (x - hand) / 12.0) * wobble);
                fill(stack, x, cy - h, x + 1, cy + h + 1, argb(layerColor, spec[2]));
            }
        }
        if (style == BeamStyle.SPIRAL) {
            int ribbon = BeamColors.layer(BeamColors.shiftHue(rgb, -color.hueSwing() * 0.6F), 1.0F);
            for (double phase : new double[]{0, Math.PI}) {
                for (int x = hand; x <= end; x++) {
                    int y = (int) Math.round(cy + Math.sin((x - hand) * 0.09 - seconds * 9 + phase) * half * 1.7);
                    fill(stack, x, y, x + 1, y + 2, argb(ribbon, 0.8F));
                }
            }
        }
        if (style == BeamStyle.SPIRAL || style == BeamStyle.CANNON) {
            Random random = new Random((long) (seconds * 15));
            int arc = BeamColors.layer(BeamColors.shiftHue(rgb, 20), 0.3F);
            for (int n = 0; n < 3; n++) {
                int x0 = hand + 6 + random.nextInt(Math.max(1, end - hand - 24));
                double y = cy + (random.nextDouble() - 0.5) * half * 2;
                for (int x = x0; x < x0 + 18; x++) {
                    y = Math.max(cy - half * 1.8, Math.min(cy + half * 1.8, y + (random.nextDouble() - 0.5) * 3));
                    fill(stack, x, (int) y, x + 1, (int) y + 1, argb(arc, 0.9F));
                }
            }
        }
        if (style == BeamStyle.RINGS) {
            int ring = BeamColors.layer(BeamColors.shiftHue(rgb, color.hueSwing() * 0.5F), 0.3F);
            for (double s = (seconds * 40) % 14; hand + s < end; s += 14) {
                double progress = s / (end - hand);
                int ry = (int) Math.round(half * (1.4 + 0.8 * progress));
                int x = (int) (hand + s);
                fill(stack, x, cy - ry, x + 1, cy + ry + 1, argb(ring, (float) (0.9 * (1 - 0.6 * progress))));
                fill(stack, x - 1, cy - ry, x + 2, cy - ry + 1, argb(ring, (float) (0.9 * (1 - 0.6 * progress))));
                fill(stack, x - 1, cy + ry, x + 2, cy + ry + 1, argb(ring, (float) (0.9 * (1 - 0.6 * progress))));
            }
        }
        int rays = switch (style) {
            case SOLID_CORE -> 10;
            case SPIRAL -> 14;
            case RINGS -> 16;
            case CANNON -> 22;
        };
        double rayLength = style == BeamStyle.CANNON ? half * 2.6 : half * 1.8;
        for (int i = 0; i < rays; i++) {
            double angle = i * Math.PI * 2 / rays + seconds * 1.5;
            double reach = rayLength * (0.7 + 0.5 * Math.abs(Math.sin(i * 2.3 + seconds * 9)));
            int rayBase = style == BeamStyle.CANNON && i % 2 == 0 ? BeamColors.shiftHue(rgb, color.hueSwing() * 0.5F) : rgb;
            int rayColor = BeamColors.layer(rayBase, 0.6F);
            for (double r = 2; r < reach; r += 1) {
                int x = (int) Math.round(hand + Math.cos(angle) * r);
                int y = (int) Math.round(cy + Math.sin(angle) * r);
                fill(stack, x, y, x + 1, y + 1, argb(rayColor, 0.75F));
            }
        }
        blob(stack, hand, cy, half * 1.0 * (1 + 0.15 * Math.sin(seconds * 14)), BeamColors.layer(rgb, 1.0F), 0.45F);
        blob(stack, hand, cy, half * 0.6, BeamColors.layer(rgb, 0.5F), 0.8F);
        blob(stack, hand, cy, half * 0.3, 0xFFFFFF, 1.0F);
        double impact = style == BeamStyle.CANNON ? half * 1.7 : half * 1.1;
        blob(stack, end, cy, impact * (1 + 0.2 * Math.sin(seconds * 17)), BeamColors.layer(rgb, 0.4F), 0.6F);
        blob(stack, end, cy, impact * 0.5, 0xFFFFFF, 0.9F);
    }

    private void blob(PoseStack stack, int cx, int cy, double radius, int rgb, float alpha) {
        int r = (int) Math.round(radius);
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            fill(stack, cx - w, cy + dy, cx + w + 1, cy + dy + 1, argb(rgb, alpha));
        }
    }

    private static int argb(int rgb, float alpha) {
        return Math.max(0, Math.min(255, (int) (alpha * 255))) << 24 | rgb & 0xFFFFFF;
    }

    // The selected ability's entity, tagged with the colour being edited so the normal tinting draws it.
    private boolean renderEntityPreview(AbstractSpellPart part, AbilityColor color, float partialTicks) {
        EntityType<?> type = AbilityVisuals.previewEntity(part);
        if (type == null || failedPreviews.contains(type) || minecraft.level == null) {
            return false;
        }
        Entity entity = previews.computeIfAbsent(type, t -> t.create(minecraft.level));
        if (entity == null) {
            return false;
        }
        AbilityEntityRenderers.tint(entity, color);
        float size = Math.max(0.3F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
        float scale = Math.min(PREVIEW_W, PREVIEW_H) * 0.6F / size;
        float spin = (minecraft.level.getGameTime() + partialTicks) * 3.0F;

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(bookLeft + PREVIEW_X + PREVIEW_W / 2.0,
                bookTop + PREVIEW_Y + PREVIEW_H / 2.0 + entity.getBbHeight() * scale / 2, 1050.0);
        modelView.scale(1.0F, 1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();
        PoseStack pose = new PoseStack();
        pose.translate(0.0, 0.0, 1000.0);
        pose.scale(scale, scale, scale);
        Quaternion flip = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion tilt = Vector3f.XP.rotationDegrees(-15.0F);
        flip.mul(tilt);
        pose.mulPose(flip);
        pose.mulPose(Vector3f.YP.rotationDegrees(spin));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        tilt.conj();
        dispatcher.overrideCameraOrientation(tilt);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean ok = true;
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, partialTicks, pose, buffers, 0xF000F0));
        } catch (RuntimeException e) {
            VaultSpellbook.LOGGER.warn("Could not preview {}; showing its icon instead", type.getRegistryName(), e);
            failedPreviews.add(type);
            ok = false;
        } finally {
            buffers.endBatch();
            dispatcher.setRenderShadow(true);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
        }
        return ok;
    }

    // The glyph's icon at a GUI scale, tinted like a filter when a colour is given.
    private void renderIcon(AbstractSpellPart part, int x, int y, float scale, @Nullable AbilityColor color) {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(x, y, 0.0);
        modelView.scale(scale, scale, 1.0F);
        RenderSystem.applyModelViewMatrix();
        if (color != null) {
            int rgb = color.flatten(0xFFFFFF);
            RenderSystem.setShaderColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
        }
        itemRenderer.renderGuiItem(new ItemStack(part.getGlyph()), 0, 0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private void drawCentered(PoseStack stack, Component text, int centerX, int y, int color) {
        font.draw(stack, text, centerX - font.width(text) / 2.0F, y, color);
    }

    private record Entry(int position, AbstractSpellPart part, boolean colorable) {
    }

    // A flat parchment-style button; selected ones are drawn pressed.
    private static final class FlatButton extends Button {
        boolean selected;

        FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void renderButton(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            int border = 0xFF3F2F1F;
            int body = selected ? 0xFF8A6A44 : isHoveredOrFocused() ? 0xFFE3D3AE : 0xFFD2BE94;
            fill(stack, x, y, x + width, y + height, border);
            fill(stack, x + 1, y + 1, x + width - 1, y + height - 1, body);
            Minecraft mc = Minecraft.getInstance();
            int textColor = selected ? 0xFFF4E9D0 : 0xFF3F2F1F;
            mc.font.draw(stack, getMessage(), x + (width - mc.font.width(getMessage())) / 2.0F, y + (height - 8) / 2.0F, textColor);
        }
    }
}
