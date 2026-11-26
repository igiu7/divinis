package com.divinis.platform.event.client;

import com.divinis.platform.bus.Event;
import com.divinis.platform.bus.ICancellableEvent;
import com.mojang.blaze3d.shaders.FogShape;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;

public abstract class ViewportEvent extends Event {
    private final Camera camera;
    private final double partialTick;

    protected ViewportEvent(Camera camera, double partialTick) {
        this.camera = camera;
        this.partialTick = partialTick;
    }

    public Camera getCamera() { return camera; }
    public double getPartialTick() { return partialTick; }

    /** The camera's angles for this frame (can be changed). */
    public static class ComputeCameraAngles extends ViewportEvent {
        private float yaw, pitch, roll;

        public ComputeCameraAngles(Camera camera, double partialTick, float yaw, float pitch, float roll) {
            super(camera, partialTick);
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        public float getYaw() { return yaw; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public float getPitch() { return pitch; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public float getRoll() { return roll; }
        public void setRoll(float roll) { this.roll = roll; }
    }

    /** The field of view for this frame (can be changed). */
    public static class ComputeFov extends ViewportEvent {
        private final boolean usedConfiguredFov;
        private double fov;

        public ComputeFov(Camera camera, double partialTick, double fov, boolean usedConfiguredFov) {
            super(camera, partialTick);
            this.fov = fov;
            this.usedConfiguredFov = usedConfiguredFov;
        }

        public double getFOV() { return fov; }
        public void setFOV(double fov) { this.fov = fov; }
        public boolean usedConfiguredFov() { return usedConfiguredFov; }
    }

    /** The fog's colour (can be changed). */
    public static class ComputeFogColor extends ViewportEvent {
        private float red, green, blue;

        public ComputeFogColor(Camera camera, float partialTick, float red, float green, float blue) {
            super(camera, partialTick);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public float getRed() { return red; }
        public void setRed(float red) { this.red = red; }
        public float getGreen() { return green; }
        public void setGreen(float green) { this.green = green; }
        public float getBlue() { return blue; }
        public void setBlue(float blue) { this.blue = blue; }
    }

    /** The fog is set up: cancelled, its distances become these. */
    public static class RenderFog extends ViewportEvent implements ICancellableEvent {
        private final FogRenderer.FogMode mode;
        private float farPlaneDistance, nearPlaneDistance;
        private FogShape fogShape;

        public RenderFog(FogRenderer.FogMode mode, Camera camera, float partialTick, float nearPlaneDistance,
                         float farPlaneDistance, FogShape fogShape) {
            super(camera, partialTick);
            this.mode = mode;
            this.nearPlaneDistance = nearPlaneDistance;
            this.farPlaneDistance = farPlaneDistance;
            this.fogShape = fogShape;
        }

        public FogRenderer.FogMode getMode() { return mode; }
        public float getFarPlaneDistance() { return farPlaneDistance; }
        public void setFarPlaneDistance(float distance) { this.farPlaneDistance = distance; }
        public float getNearPlaneDistance() { return nearPlaneDistance; }
        public void setNearPlaneDistance(float distance) { this.nearPlaneDistance = distance; }
        public FogShape getFogShape() { return fogShape; }
        public void setFogShape(FogShape shape) { this.fogShape = shape; }
    }
}
