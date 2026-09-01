package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** White-eye overlay over Minecraft's own Steve texture; the rest of Herobrine uses vanilla art. */
public final class HerobrineEyesLayer extends GeoRenderLayer<AmbientFighterEntity> {
    private static final ResourceLocation EYES = new ResourceLocation("dmzlivingworld", "textures/entity/herobrine_eyes.png");

    public HerobrineEyesLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, AmbientFighterEntity entity, BakedGeoModel model,
                       RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!WorldMenaceManager.isHerobrine(entity)) return;
        // Use normal lit cutout rendering. RenderType.eyes is emissive and was the reason the eyes glowed in darkness.
        RenderType type = RenderType.entityCutoutNoCull(EYES);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, poseStack, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
