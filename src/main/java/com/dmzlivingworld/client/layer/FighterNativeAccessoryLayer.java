package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.FighterSpecialItemManager;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * DMZ accessory geometry attached from the live animated fighter-bone callback.
 * This mirrors the native DMZ race-parts renderer: the accessory is rendered while
 * GeckoLib is already positioned on the owning head/body/limb bone instead of trying
 * to make a second model catch up after the body has been animated.
 */
public final class FighterNativeAccessoryLayer extends GeoRenderLayer<AmbientFighterEntity> {
    private static final ResourceLocation SCOUTER_MODEL = dmz("geo/entity/scouter.geo.json");
    private static final ResourceLocation WEIGHTS_MODEL = dmz("geo/entity/races/weighted_items.geo.json");
    private static final ResourceLocation WEIGHTS_TEXTURE = dmz("textures/entity/races/weighted_items.png");

    public FighterNativeAccessoryLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity fighter, GeoBone playerBone,
                              RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if (WorldMenaceManager.isHerobrine(fighter)) return;
        int id = fighter.getCosmeticAccessoryId();
        if (id <= FighterSpecialItemManager.ACCESSORY_NONE) return;
        String anchor = playerBone.getName();

        if (id <= FighterSpecialItemManager.SCOUTER_PURPLE) {
            if (!"head".equals(anchor)) return;
            renderScouter(poseStack, fighter, buffers, partialTick, packedLight, id);
            buffers.getBuffer(renderType);
            return;
        }

        BakedGeoModel model = getGeoModel().getBakedModel(WEIGHTS_MODEL);
        if (model == null) return;
        RenderType type = RenderType.entityCutoutNoCull(WEIGHTS_TEXTURE);
        switch (id) {
            case FighterSpecialItemManager.WEIGHT_TURTLE -> {
                if ("body".equals(anchor)) renderWeightBone(model, "turtleweight", poseStack, fighter, buffers, type, partialTick, packedLight);
            }
            case FighterSpecialItemManager.WEIGHT_WORKOUT -> {
                switch (anchor) {
                    case "right_arm" -> renderWeightBone(model, "right_arm_glove", poseStack, fighter, buffers, type, partialTick, packedLight);
                    case "left_arm" -> renderWeightBone(model, "left_arm_glove", poseStack, fighter, buffers, type, partialTick, packedLight);
                    case "right_leg" -> renderWeightBone(model, "right_leg_glove", poseStack, fighter, buffers, type, partialTick, packedLight);
                    case "left_leg" -> renderWeightBone(model, "left_leg_glove", poseStack, fighter, buffers, type, partialTick, packedLight);
                    default -> { }
                }
            }
            case FighterSpecialItemManager.WEIGHT_PICCOLO -> {
                switch (anchor) {
                    case "body" -> {
                        GeoBone cape = model.getBone("cape").orElse(null);
                        boolean hidden = cape != null && cape.isHidden();
                        if (cape != null) cape.setHidden(true);
                        renderWeightBone(model, "piccoloweight_middle", poseStack, fighter, buffers, type, partialTick, packedLight);
                        if (cape != null) cape.setHidden(hidden);
                    }
                    case "right_arm" -> renderWeightBone(model, "piccoloweight_right", poseStack, fighter, buffers, type, partialTick, packedLight);
                    case "left_arm" -> renderWeightBone(model, "piccoloweight_left", poseStack, fighter, buffers, type, partialTick, packedLight);
                    default -> { }
                }
            }
            default -> { }
        }
        buffers.getBuffer(renderType);
    }

    private void renderScouter(PoseStack poseStack, AmbientFighterEntity fighter,
                               MultiBufferSource buffers, float partialTick, int packedLight, int id) {
        String color = switch (id) {
            case FighterSpecialItemManager.SCOUTER_RED -> "red";
            case FighterSpecialItemManager.SCOUTER_BLUE -> "blue";
            case FighterSpecialItemManager.SCOUTER_GREEN -> "green";
            case FighterSpecialItemManager.SCOUTER_PURPLE -> "purple";
            default -> null;
        };
        if (color == null) return;
        BakedGeoModel model = getGeoModel().getBakedModel(SCOUTER_MODEL);
        BakedGeoModel playerModel = getGeoModel().getBakedModel(getGeoModel().getModelResource(fighter));
        if (model == null || playerModel == null) return;
        RenderType type = ModRenderTypes.scouterLens(dmz("textures/entity/races/" + color + "_scouter.png"));
        model.getBone("radar").ifPresent(bone -> {
            // DMZ's native scouter has a matching hierarchy, so keep its internal head/root
            // transforms synced while the live head poseStack supplies this frame's attachment.
            syncTargetBoneAndParents(bone, playerModel);
            renderBone(bone, poseStack, fighter, buffers, type, partialTick, packedLight);
        });
    }

    private void renderWeightBone(BakedGeoModel weightModel, String boneName, PoseStack poseStack,
                                  AmbientFighterEntity fighter, MultiBufferSource buffers,
                                  RenderType type, float partialTick, int packedLight) {
        weightModel.getBone(boneName).ifPresent(bone ->
                renderBone(bone, poseStack, fighter, buffers, type, partialTick, packedLight));
    }

    private void renderBone(GeoBone bone, PoseStack poseStack, AmbientFighterEntity fighter,
                            MultiBufferSource buffers, RenderType type, float partialTick, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().renderRecursively(poseStack, fighter, bone, type, buffers, consumer, true,
                partialTick, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void syncTargetBoneAndParents(GeoBone destBone, BakedGeoModel sourceModel) {
        GeoBone current = destBone;
        while (current != null) {
            GeoBone target = current;
            sourceModel.getBone(target.getName()).ifPresent(source -> copyBoneData(source, target));
            current = current.getParent();
        }
    }

    private static void copyBoneData(GeoBone source, GeoBone dest) {
        dest.setRotX(source.getRotX()); dest.setRotY(source.getRotY()); dest.setRotZ(source.getRotZ());
        dest.setPosX(source.getPosX()); dest.setPosY(source.getPosY()); dest.setPosZ(source.getPosZ());
        dest.setPivotX(source.getPivotX()); dest.setPivotY(source.getPivotY()); dest.setPivotZ(source.getPivotZ());
        dest.setScaleX(source.getScaleX()); dest.setScaleY(source.getScaleY()); dest.setScaleZ(source.getScaleZ());
    }

    private static ResourceLocation dmz(String path) {
        return new ResourceLocation("dragonminez", path);
    }
}
