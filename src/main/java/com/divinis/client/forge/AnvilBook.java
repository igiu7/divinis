package com.divinis.client.forge;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationView;
import com.divinis.client.ClientForge;
import com.divinis.forge.ForgeShapes;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The book hanging beside the anvil: the open book of Hands-On Delight (ForgeBookModel and its paper; the way the pages
 * are laid on the open model, in units of about a GUI pixel, follows its RecipeBook - MIT, Copyright (c) 2026 Pocky).
 * The left page lists the recipes - the weapon parts, the same in every metal; the right page shows the recipe picked:
 * its outline, the ingots it takes, what the ingots on the anvil make of it, and "Forge".
 */
final class AnvilBook implements NodePainter {
    private static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/forge_book_paper.png");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/forge_white");
    /** The model turned to lie open, as Hands-On Delight lays it. */
    private static final Quaternionf LYING = new org.joml.Matrix3f(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F)
            .getNormalizedRotation(new Quaternionf());
    private static final float SIZE = 0.72F;
    static final float PAGE_WIDTH = 6.0F * SIZE;
    static final float PAGE_HEIGHT = 8.0F * SIZE;
    static final int UNITS_WIDE = 72;
    static final int UNITS_HIGH = 96;
    static final float UNIT = PAGE_WIDTH / UNITS_WIDE;
    private static final float OPEN = 1.17F;
    private static final float ANGLE = 1.25F * OPEN;
    private static final float PAGE_GAP = 0.09F;
    /** Height of the pages over the node, pixels: the plane the cursor is read on. */
    static final float SHEET_HEIGHT = (Mth.sin(ANGLE) + PAGE_GAP) * SIZE;
    private static final float ITEM_LIFT = 1.2F;
    private static final int FOLD = 0x285A3C18;
    private static final int FOLD_WIDTH = 5;
    private static final int INK = 0xFF4A3418;
    private static final int RED_INK = 0xFF9A2418;
    private static final int LIT = 12;

    // the layout, in units: the recipes on the left page, the one picked on the right one (past its fold), "Forge" under it
    static final int ROW_X = 5;
    static final int ROW_Y = 15;
    static final float ROW_PITCH = 8.5F;
    static final int ROW_WIDE = 61;
    private static final float OUTLINE_CELL = 3.0F;
    static final int BUTTON_X = 18;
    static final int BUTTON_Y = 84;
    static final int BUTTON_WIDE = 42;
    static final int BUTTON_HIGH = 10;

    private final AnvilBehavior anvil;
    private ForgeBookModel model;
    private int shade;
    private float appear = 1.0F;

    AnvilBook(AnvilBehavior anvil) {
        this.anvil = anvil;
    }

    void setAppear(float appear) {
        this.appear = appear;
    }

    /** The cursor on the book: {left page (1) or right (0), x, y in units}, or null off it. */
    static float[] pointer(StationView view) {
        Vector3f p = view.pointerOn("book_open", SHEET_HEIGHT).orElse(null);
        if (p == null) return null;
        float y = (PAGE_HEIGHT / 2.0F - p.z) / UNIT;
        if (y < 0 || y > UNITS_HIGH) return null;
        if (p.x >= 0 && p.x <= PAGE_WIDTH) return new float[] {1, (PAGE_WIDTH - p.x) / UNIT, y};
        if (p.x < 0 && p.x >= -PAGE_WIDTH) return new float[] {0, -p.x / UNIT, y};
        return null;
    }

    @Override
    public boolean smooth() {
        return true;
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (appear < 0.08F) return;
        shade = LightTexture.pack(Math.max(LightTexture.block(light), LIT), Math.max(LightTexture.sky(light), LIT));
        if (model == null) model = ForgeBookModel.bake();
        poseStack.pushPose();
        poseStack.scale(appear, appear, appear);
        poseStack.pushPose();
        poseStack.mulPose(LYING);
        poseStack.scale(16.0F * SIZE, 16.0F * SIZE, 16.0F * SIZE);
        model.setupAnim(ANGLE);
        model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(ForgeBookModel.TEXTURE)), shade, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        float tilt = Mth.HALF_PI - ANGLE;
        side(poseStack, buffers, true, tilt);
        side(poseStack, buffers, false, -tilt);
        poseStack.popPose();
    }

    /** One page, turned about the fold: units run right and down as on a screen. */
    private void side(PoseStack poseStack, MultiBufferSource buffers, boolean left, float turn) {
        poseStack.pushPose();
        poseStack.translate(0.0F, SHEET_HEIGHT, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationZ(turn));
        Matrix4f sheet = new Matrix4f(-UNIT, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -UNIT, 0.0F, 0.0F, UNIT, 0.0F, 0.0F,
                left ? PAGE_WIDTH : 0.0F, 0.0F, PAGE_HEIGHT / 2.0F, 1.0F);
        poseStack.last().pose().mul(sheet);
        Sheet canvas = new Sheet(poseStack, buffers);
        canvas.paper(left);
        canvas.rect(left ? UNITS_WIDE - FOLD_WIDTH : 0.0F, 0.0F, FOLD_WIDTH, UNITS_HIGH, 0.1F, FOLD);
        canvas.rect(left ? UNITS_WIDE - 2.0F : 0.0F, 0.0F, 2.0F, UNITS_HIGH, 0.15F, FOLD);
        if (left) list(canvas);
        else detail(canvas);
        poseStack.popPose();
    }

    /** The recipes: each part, drawn in blacksteel as the icon of the kind, and the ingots it takes. */
    private void list(Sheet c) {
        c.textCentered("Recipes", (UNITS_WIDE - FOLD_WIDTH) / 2.0F, 4.0F, 0.75F, INK);
        c.rect(ROW_X, 12.0F, ROW_WIDE, 0.6F, 0.1F, 0x405A3C18);
        List<String> parts = List.copyOf(ForgeShapes.all().keySet());
        for (int i = 0; i < parts.size(); i++) {
            String one = parts.get(i);
            float y = ROW_Y + i * ROW_PITCH;
            if (one.equals(anvil.part())) c.rect(ROW_X - 1, y - 0.5F, ROW_WIDE + 1, ROW_PITCH, 0.2F, 0x708A5A2B);
            else if (i == anvil.hoveredRow()) c.rect(ROW_X - 1, y - 0.5F, ROW_WIDE + 1, ROW_PITCH, 0.2F, 0x305A3C18);
            String metal = anvil.metal() != null ? anvil.metal() : "blacksteel";
            c.item(new ItemStack(ForgeShapes.part(metal, one)), ROW_X, y, 7.0F);
            c.text(title(one), ROW_X + 9.0F, y + 1.8F, 0.5F, INK);
            String cost = "x" + ForgeShapes.shape(one).ingots();
            c.text(cost, ROW_X + ROW_WIDE - 1.0F - Minecraft.getInstance().font.width(cost) * 0.5F, y + 1.8F, 0.5F, INK);
        }
    }

    /** The recipe picked: its outline, what it takes, what the anvil holds makes of it, and "Forge". */
    private void detail(Sheet c) {
        String part = anvil.part();
        float mid = FOLD_WIDTH + (UNITS_WIDE - FOLD_WIDTH) / 2.0F;
        if (part == null) {
            c.textCentered("Pick a recipe", mid, 40.0F, 0.7F, INK);
            c.textCentered("on the left page", mid, 47.0F, 0.6F, INK);
            return;
        }
        ForgeShapes.Shape shape = ForgeShapes.shape(part);
        c.textCentered(title(part), mid, 4.0F, 0.7F, INK);
        // the outline to cut along, as a pattern drawn in the book
        float ox = mid - ForgeShapes.SIZE * OUTLINE_CELL / 2.0F, oy = 12.0F;
        boolean[][] px = shape.pixels();
        c.rect(ox - 1, oy - 1, ForgeShapes.SIZE * OUTLINE_CELL + 2, ForgeShapes.SIZE * OUTLINE_CELL + 2, 0.1F, 0x185A3C18);
        float t = 0.6F;
        for (int y = 0; y < ForgeShapes.SIZE; y++) {
            for (int x = 0; x < ForgeShapes.SIZE; x++) {
                if (!px[y][x]) continue;
                float cx = ox + x * OUTLINE_CELL, cy = oy + y * OUTLINE_CELL;
                c.rect(cx, cy, OUTLINE_CELL, OUTLINE_CELL, 0.15F, 0x305A3C18);
                if (y == 0 || !px[y - 1][x]) c.rect(cx, cy, OUTLINE_CELL, t, 0.2F, INK);
                if (y == 15 || !px[y + 1][x]) c.rect(cx, cy + OUTLINE_CELL - t, OUTLINE_CELL, t, 0.2F, INK);
                if (x == 0 || !px[y][x - 1]) c.rect(cx, cy, t, OUTLINE_CELL, 0.2F, INK);
                if (x == 15 || !px[y][x + 1]) c.rect(cx + OUTLINE_CELL - t, cy, t, OUTLINE_CELL, 0.2F, INK);
            }
        }
        c.textCentered(shape.ingots() + (shape.ingots() == 1 ? " ingot" : " ingots") + ", hot, of any rare metal", mid, 63.0F, 0.5F, INK);
        String metal = anvil.metal();
        int laid = anvil.laidCount();
        boolean ready = false;
        if (metal == null) {
            c.textCentered("Lay hot ingots on the anvil", mid, 70.0F, 0.5F, RED_INK);
        } else {
            ItemStack result = new ItemStack(ForgeShapes.part(metal, part));
            c.item(result, mid - 22.0F, 68.5F, 7.0F);
            c.text(Component.translatable(result.getDescriptionId()).getString(), mid - 13.0F, 70.5F, 0.45F, AnvilBehavior.eraColour(metal));
            if (!AnvilBehavior.knows(metal)) {
                c.textCentered("Your city needs " + ClientForge.techName(ForgeShapes.tech(metal)), mid, 78.0F, 0.45F, RED_INK);
            } else if (laid < shape.ingots()) {
                c.textCentered((shape.ingots() - laid) + " more on the anvil", mid, 78.0F, 0.5F, RED_INK);
            } else {
                ready = true;
            }
        }
        int fill = !ready ? 0x60806850 : anvil.overButton() ? 0xFFB0702A : 0xFF8A5A2B;
        c.rect(BUTTON_X, BUTTON_Y, BUTTON_WIDE, BUTTON_HIGH, 0.2F, fill);
        c.rect(BUTTON_X, BUTTON_Y + BUTTON_HIGH - 1.0F, BUTTON_WIDE, 1.0F, 0.3F, 0x60301808);
        c.textCentered("Forge", BUTTON_X + BUTTON_WIDE / 2.0F, BUTTON_Y + 1.8F, 0.8F, ready ? 0xFFFFF0D8 : 0xFFE8DCC8);
    }

    /** "two_handed_axe_head" -> "Two-Handed Axe Head". */
    static String title(String part) {
        StringBuilder out = new StringBuilder();
        for (String word : part.replace("two_handed", "two-handed").split("_")) {
            if (!out.isEmpty()) out.append(' ');
            String[] bits = word.split("-");
            for (int i = 0; i < bits.length; i++) {
                if (i > 0) out.append('-');
                out.append(Character.toUpperCase(bits[i].charAt(0))).append(bits[i].substring(1));
            }
        }
        return out.toString();
    }

    /** Draws on a page: paper, flat colour, text and items, in units over the paper. */
    private final class Sheet {
        private final PoseStack poseStack;
        private final MultiBufferSource buffers;
        private final TextureAtlasSprite white;

        Sheet(PoseStack poseStack, MultiBufferSource buffers) {
            this.poseStack = poseStack;
            this.buffers = buffers;
            white = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE);
        }

        void paper(boolean mirrored) {
            float u1 = UNITS_WIDE / 128.0F, v1 = UNITS_HIGH / 128.0F;
            quad(buffers.getBuffer(RenderType.text(PAPER)), 0.0F, 0.0F, UNITS_WIDE, UNITS_HIGH, 0.0F, 0xFFFFFFFF,
                    mirrored ? u1 : 0.0F, mirrored ? 0.0F : u1, 0.0F, v1);
        }

        void rect(float x, float y, float width, float height, float lift, int argb) {
            quad(buffers.getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS)), x, y, x + width, y + height, lift, argb,
                    white.getU0(), white.getU1(), white.getV0(), white.getV1());
        }

        private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float lift, int argb, float u0, float u1, float v0, float v1) {
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
            consumer.addVertex(matrix, x0, y0, lift).setColor(r, g, b, a).setUv(u0, v0).setLight(shade);
            consumer.addVertex(matrix, x0, y1, lift).setColor(r, g, b, a).setUv(u0, v1).setLight(shade);
            consumer.addVertex(matrix, x1, y1, lift).setColor(r, g, b, a).setUv(u1, v1).setLight(shade);
            consumer.addVertex(matrix, x1, y0, lift).setColor(r, g, b, a).setUv(u1, v0).setLight(shade);
        }

        void item(ItemStack stack, float x, float y, float size) {
            if (stack.isEmpty()) return;
            poseStack.pushPose();
            poseStack.translate(x + size / 2.0F, y + size / 2.0F, ITEM_LIFT);
            var renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel baked = renderer.getModel(stack, Minecraft.getInstance().level, null, 0);
            poseStack.scale(size, -size, baked.isGui3d() ? 0.5F : 8.0F);
            renderer.render(stack, ItemDisplayContext.GUI, false, poseStack, DiegesisClient.items(buffers), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, baked);
            poseStack.popPose();
        }

        void text(String text, float x, float y, float scale, int colour) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, y, ITEM_LIFT).scale(scale, scale, 1.0F);
            Minecraft.getInstance().font.drawInBatch(text, 0.0F, 0.0F, colour, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }

        void textCentered(String text, float middle, float y, float scale, int colour) {
            float wide = Minecraft.getInstance().font.width(text) * scale;
            float room = UNITS_WIDE - FOLD_WIDTH - 4.0F;
            if (wide > room) {
                scale *= room / wide;
                wide = room;
            }
            text(text, middle - wide / 2.0F, y, scale, colour);
        }
    }
}
