package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;

/** A stage of drawing the world: the pose stack is in camera space, the matrices are the frame's. */
public class RenderLevelStageEvent extends Event {
    private final Stage stage;
    private final LevelRenderer levelRenderer;
    private final PoseStack poseStack;
    private final Matrix4f modelView;
    private final Matrix4f projection;
    private final int renderTick;
    private final DeltaTracker partialTick;
    private final Camera camera;
    private final Frustum frustum;

    public RenderLevelStageEvent(Stage stage, LevelRenderer levelRenderer, PoseStack poseStack, Matrix4f modelView,
                                 Matrix4f projection, int renderTick, DeltaTracker partialTick, Camera camera, Frustum frustum) {
        this.stage = stage;
        this.levelRenderer = levelRenderer;
        this.poseStack = poseStack;
        this.modelView = modelView;
        this.projection = projection;
        this.renderTick = renderTick;
        this.partialTick = partialTick;
        this.camera = camera;
        this.frustum = frustum;
    }

    public Stage getStage() { return stage; }
    public LevelRenderer getLevelRenderer() { return levelRenderer; }
    public PoseStack getPoseStack() { return poseStack; }
    public Matrix4f getModelViewMatrix() { return modelView; }
    public Matrix4f getProjectionMatrix() { return projection; }
    public int getRenderTick() { return renderTick; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public Camera getCamera() { return camera; }
    public Frustum getFrustum() { return frustum; }

    public enum Stage {
        AFTER_SKY,
        AFTER_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_LEVEL
    }
}
