package com.divinis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.divinis.boss.LichShade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Draws a shade of a fallen one (boss.LichShade) as the player it copies - their own skin, wide or slim - black and
 * see-through, a little green.
 */
public class LichShadeRenderer extends LivingEntityRenderer<LichShade, PlayerModel<LichShade>> {
    private final PlayerModel<LichShade> wide, slim;

    public LichShadeRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
        this.wide = getModel();
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    private PlayerSkin skin(LichShade shade) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && shade.owner().isPresent()) {
            PlayerInfo info = connection.getPlayerInfo(shade.owner().get());
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(shade.owner().orElse(shade.getUUID()));
    }

    @Override
    public ResourceLocation getTextureLocation(LichShade shade) {
        return skin(shade).texture();
    }

    @Override
    public void render(LichShade shade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = skin(shade).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(shade, yaw, partialTick, pose, type -> new Tinted(buffers.getBuffer(type)), light);
    }

    @Override
    protected RenderType getRenderType(LichShade shade, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(shade));
    }

    @Override
    protected boolean shouldShowName(LichShade shade) {
        return false;
    }

    /** Every vertex it draws darkened, a little green, see-through. */
    private record Tinted(VertexConsumer to) implements VertexConsumer {
        private static final int R = 34, G = 52, B = 38, A = 150;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            to.setColor(r * R / 255, g * G / 255, b * B / 255, a * A / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            to.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }
}
