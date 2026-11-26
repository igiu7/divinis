package com.divinis.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.RegisterShadersEvent;
import com.divinis.platform.event.client.RenderLevelStageEvent;

/**
 * Bloom: real light pouring out of a shape, done as a shader - not a texture. A renderer draws the part of its model
 * that shines a second time into capture() (only those quads: the Nameless Creator's white body, not its face). As
 * soon as the world is drawn (while its depth is still there) that shape is drawn white into a mask at a quarter of the
 * screen's size - hidden where the world stands in front of it; then, under the HUD (ScreenFxClient's layer, so it
 * works with any shader pack), blurred twice each way and added to the frame as light: brightest at the body, falling
 * off softly round it.
 */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class BloomFx {
    private BloomFx() {}

    private static final int DOWNSCALE = 4;

    @Nullable private static ShaderInstance shader;
    @Nullable private static TextureTarget mask, blurA, blurB;
    // the shapes drawn this frame (camera-relative corners, four to a quad)
    private static final List<float[]> quads = new ArrayList<>();
    private static float strength, doneStrength;
    private static int color = 0xFFFFFF, doneColor = 0xFFFFFF;
    private static boolean maskReady;
    @Nullable private static Matrix4f lastView, lastProj;

    @EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onShaders(RegisterShadersEvent event) throws IOException {
            event.register(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "bloom"), DefaultVertexFormat.POSITION_TEX, s -> shader = s);
        }
    }

    /**
     * Buffers that take only `shining` (the render type of the part that gives light) as the shape of the light, of
     * this colour and strength; everything else drawn into them goes nowhere.
     */
    public static MultiBufferSource capture(RenderType shining, int rgb, float power) {
        color = rgb;
        strength = Math.max(strength, power);
        return type -> type.equals(shining) ? INTO : NOWHERE;
    }

    @SubscribeEvent
    public static void onLevelDone(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        lastView = new Matrix4f(event.getModelViewMatrix());
        lastProj = new Matrix4f(event.getProjectionMatrix());
        maskReady = false;
        if (!quads.isEmpty() && shader != null && strength > 0) {
            drawMask();
            maskReady = true;
            doneStrength = strength;
            doneColor = color;
        }
        quads.clear();
        strength = 0;
    }

    /** The shape, white, where nothing in the world stands in front of it. */
    private static void drawMask() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = Math.max(1, main.width / DOWNSCALE), h = Math.max(1, main.height / DOWNSCALE);
        mask = sized(mask, w, h, true);
        if (com.divinis.platform.client.ClientServices.isStencilEnabled(main) && !com.divinis.platform.client.ClientServices.isStencilEnabled(mask)) com.divinis.platform.client.ClientServices.enableStencil(mask); // its depth stored as the screen's
        blurA = sized(blurA, w, h, false);
        blurB = sized(blurB, w, h, false);

        mask.setClearColor(0, 0, 0, 0);
        mask.clear(Minecraft.ON_OSX);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mask.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        mask.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(lastProj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(lastView);
        RenderSystem.applyModelViewMatrix();
        BufferBuilder shape = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (float[] v : quads) shape.addVertex(v[0], v[1], v[2]);
        MeshData mesh = shape.build();
        if (mesh != null) {
            RenderSystem.setShader(GameRenderer::getPositionShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-2.0F, -64.0F);
            BufferUploader.drawWithShader(mesh);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        main.bindWrite(true);
    }

    /** Adds this frame's light to the screen (from ScreenFxClient's layer, after the world, under the HUD). */
    static void render() {
        if (!maskReady || shader == null || mask == null || blurA == null || blurB == null) return;
        maskReady = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = mask.width, h = mask.height;
        // blurred twice each way, the second time wider
        RenderSystem.disableDepthTest();
        float reach = Math.max(1, main.height / 720.0F) * 10.0F;
        ShaderInstance sh = shader;
        sh.safeGetUniform("Mode").set(1.0F);
        blur(sh, mask, blurA, 1.0F / w, 0, reach);
        blur(sh, blurA, blurB, 0, 1.0F / h, reach);
        blur(sh, blurB, blurA, 1.0F / w, 0, reach * 2.2F);
        blur(sh, blurA, blurB, 0, 1.0F / h, reach * 2.2F);

        // and added to the frame as light
        main.bindWrite(true);
        sh.safeGetUniform("Mode").set(2.0F);
        sh.safeGetUniform("Strength").set(doneStrength);
        sh.safeGetUniform("Color").set((doneColor >> 16 & 255) / 255.0F, (doneColor >> 8 & 255) / 255.0F, (doneColor & 255) / 255.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        quad(sh, blurB);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private static void blur(ShaderInstance sh, TextureTarget from, TextureTarget into, float dx, float dy, float radius) {
        into.setClearColor(0, 0, 0, 0);
        into.clear(Minecraft.ON_OSX);
        into.bindWrite(true);
        sh.safeGetUniform("Direction").set(dx, dy);
        sh.safeGetUniform("Radius").set(radius);
        RenderSystem.disableBlend();
        quad(sh, from);
    }

    /** The whole target, through the shader, reading `from`. */
    private static void quad(ShaderInstance sh, TextureTarget from) {
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, from.getColorTextureId());
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-1, -1, 0).setUv(0, 0);
        b.addVertex(1, -1, 0).setUv(1, 0);
        b.addVertex(1, 1, 0).setUv(1, 1);
        b.addVertex(-1, 1, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
    }

    private static TextureTarget sized(@Nullable TextureTarget t, int w, int h, boolean depth) {
        if (t != null && t.width == w && t.height == h) return t;
        if (t != null) t.destroyBuffers();
        TextureTarget n = new TextureTarget(w, h, depth, Minecraft.ON_OSX);
        n.setFilterMode(GL11.GL_LINEAR);
        return n;
    }

    /** Takes the corners (already camera-relative) of what is drawn into it. */
    private static final VertexConsumer INTO = new Corners(true), NOWHERE = new Corners(false);

    private static final class Corners implements VertexConsumer {
        private final boolean keep;

        Corners(boolean keep) {
            this.keep = keep;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (keep && quads.size() < 40000) quads.add(new float[] {x, y, z});
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
