package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dragonminez.client.render.hair.HairRenderer;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Delegates Human/Saiyan hair geometry entirely to DragonMineZ's HairRenderer. */
public final class FighterHairLayer extends GeoRenderLayer<AmbientFighterEntity> {
    public FighterHairLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity entity, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (WorldMenaceManager.isHerobrine(entity)) return;
        if (!"head".equals(bone.getName()) || !entity.getRace().usesHair()) return;

        Character character = entity.getDMZCharacter();
        String hairType = entity.getActiveRacialForm() == null ? "base" : entity.getActiveRacialForm().hairType();
        CustomHair hair = switch (hairType) {
            case "ssj" -> HairManager.getPresetHairSSJ(entity.getHairId(), entity.getRace().dmzId());
            case "ssj2" -> HairManager.getPresetHairSSJ2(entity.getHairId(), entity.getRace().dmzId());
            case "ssj3" -> HairManager.getPresetHairSSJ3(entity.getHairId(), entity.getRace().dmzId());
            default -> HairManager.getPresetHair(entity.getHairId(), entity.getRace().dmzId());
        };
        if (hair == null || hair.isEmpty()) {
            hair = switch (hairType) {
                case "ssj" -> HairManager.getPresetHairSSJ(entity.getHairId(), "human");
                case "ssj2" -> HairManager.getPresetHairSSJ2(entity.getHairId(), "human");
                case "ssj3" -> HairManager.getPresetHairSSJ3(entity.getHairId(), "human");
                default -> HairManager.getPresetHair(entity.getHairId(), "human");
            };
        }
        if (hair == null || hair.isEmpty()) return;
        float[] hairRgb = character.getRgbHairColor();

        poseStack.pushPose();
        RenderUtils.translateToPivotPoint(poseStack, bone);
        HairRenderer.render(
                poseStack, bufferSource, hair, hair, 1.0F, character,
                null, null, hairRgb, hairRgb,
                false, false, partialTick, packedLight, packedOverlay,
                1.0F, 1.0F, 0.0F
        );
        bufferSource.getBuffer(renderType);
        poseStack.popPose();
    }
}
