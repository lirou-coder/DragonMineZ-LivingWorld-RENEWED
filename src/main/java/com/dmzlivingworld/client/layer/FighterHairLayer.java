package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.SairensRaceCompat;
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

/** Delegates humanoid hair geometry entirely to DragonMineZ's HairRenderer. */
public final class FighterHairLayer extends GeoRenderLayer<AmbientFighterEntity> {
    public FighterHairLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity entity, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (WorldMenaceManager.isHerobrine(entity)) return;
        if (!"head".equals(bone.getName()) || !entity.getRace().usesHair()
                || (entity.getRace() == com.dmzlivingworld.entity.FighterRace.MAJIN && !entity.isFemale())) return;

        Character character = entity.getDMZCharacter();
        String hairRace = entity.getRace() == com.dmzlivingworld.entity.FighterRace.BIO_ANDROID
                || entity.getRace().isSairensRace() ? "human" : entity.getRace().dmzId();
        String hairType = entity.getActiveRacialForm() == null ? "base" : entity.getActiveRacialForm().hairType();
        CustomHair hair = switch (hairType) {
            case "ssj" -> HairManager.getPresetHairSSJ(entity.getHairId(), hairRace);
            case "ssj2" -> HairManager.getPresetHairSSJ2(entity.getHairId(), hairRace);
            case "ssj3" -> HairManager.getPresetHairSSJ3(entity.getHairId(), hairRace);
            default -> HairManager.getPresetHair(entity.getHairId(), hairRace);
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
