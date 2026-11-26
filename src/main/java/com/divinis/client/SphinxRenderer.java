package com.divinis.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.divinis.boss.Sphinx;
import com.divinis.Divinis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Sphinx 1.5 times his model's pixels: golden fur and a nemes of gold and lapis in phase 1, weathered
 * sandstone cracked through with burning turquoise in phase 2 (sphinx_p2.png); the glow of each at full light (the
 * uraeus' eyes, and in phase 2 his eyes, the cracks, the glyphs, the feathers' edges). While his eye of Ra burns his
 * eyes blaze gold (found through the model's parts, so they follow his head).
 * Dying he becomes a tornado of sand (SandFx): from Sphinx.DEATH_SPIN he is wound thin and tall into it as he spins,
 * the funnel thickening round him; then it widens and blows away to nothing.
 */
public class SphinxRenderer extends MobRenderer<Sphinx, SphinxModel> {
    private static final ResourceLocation TEXTURE = tex("sphinx"), TEXTURE_P2 = tex("sphinx_p2");
    private static final RenderType GLOW = RenderType.eyes(tex("sphinx_glow")), GLOW_P2 = RenderType.eyes(tex("sphinx_p2_glow"));
    private static final float SCALE = 1.5F;
    /** Where the eyes are on the head (model pixels from the head's pivot). */
    private static final float EYE_X = 2.25F, EYE_Y = -4.75F, EYE_Z = -6.1F;
    /** Past this in his death he is gone into the tornado. */
    private static final int DEATH_GONE = 90;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "textures/entity/" + name + ".png");
    }

    public SphinxRenderer(EntityRendererProvider.Context context) {
        super(context, new SphinxModel(context.bakeLayer(SphinxModel.LAYER)), 1.6F);
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Sphinx boss) {
        return boss.phase() == 2 ? TEXTURE_P2 : TEXTURE;
    }

    @Override
    protected void scale(Sphinx boss, PoseStack pose, float partialTick) {
        float k = boss.isDeadOrDying() ? Mth.clamp((boss.dying + partialTick - Sphinx.DEATH_SPIN) / 40.0F, 0, 1) : 0;
        pose.scale(SCALE * (1 - 0.88F * k), SCALE * (1 + 0.35F * k), SCALE * (1 - 0.88F * k));
    }

    @Override
    public void render(Sphinx boss, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float d = boss.dying + partialTick;
        if (!boss.isDeadOrDying() || d < DEATH_GONE) super.render(boss, yaw, partialTick, pose, buffers, light);
        if (boss.isDeadOrDying() && d > 36) {
            // the tornado he becomes: thickening round him, then widening, rising and blowing away
            float grow = Mth.clamp((d - 36) / 30.0F, 0, 1), gone = Mth.clamp((d - 96) / 22.0F, 0, 1);
            float height = 6.5F + 3.5F * grow + 4 * gone;
            SandFx.tornado(pose, buffers, height, 0.7F + gone * 2, 3.0F + 1.2F * grow + gone * 4, boss.tickCount + partialTick, 1.2F,
                    grow * (1 - gone), boss.getId(), light);
        }
    }

    /** The glow at full light; his eyes blazing while he gazes. */
    private static final class Glow extends RenderLayer<Sphinx, SphinxModel> {
        Glow(RenderLayerParent<Sphinx, SphinxModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Sphinx boss, float limbSwing, float limbSwingAmount,
                float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            SphinxModel model = getParentModel();
            model.renderToBuffer(pose, buffers.getBuffer(boss.phase() == 2 ? GLOW_P2 : GLOW), light, OverlayTexture.NO_OVERLAY, -1);
            if (!boss.gazing()) return;
            // the eyes' places, camera-relative, through the parts down to the head
            pose.pushPose();
            model.root().translateAndRotate(pose);
            model.body.translateAndRotate(pose);
            model.neck.translateAndRotate(pose);
            model.head.translateAndRotate(pose);
            Vector3f l = pose.last().pose().transformPosition(new Vector3f(EYE_X / 16, EYE_Y / 16, EYE_Z / 16));
            Vector3f r = pose.last().pose().transformPosition(new Vector3f(-EYE_X / 16, EYE_Y / 16, EYE_Z / 16));
            pose.popPose();
            float pulse = 0.85F + 0.15F * Mth.sin(ageInTicks * 0.9F);
            PoseStack world = new PoseStack();   // camera-relative: the camera at the origin
            BeamFx.glow(world, buffers, Vec3.ZERO, new Vec3(l.x, l.y, l.z), 1.1F * pulse, 1.0F, 0.8F, 0.35F);
            BeamFx.glow(world, buffers, Vec3.ZERO, new Vec3(r.x, r.y, r.z), 1.1F * pulse, 1.0F, 0.8F, 0.35F);
        }
    }
}
