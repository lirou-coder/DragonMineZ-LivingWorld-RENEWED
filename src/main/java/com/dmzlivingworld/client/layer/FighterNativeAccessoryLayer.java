package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.FighterSpecialItemManager;
import com.dmzlivingworld.world.FighterAfterlifeManager;
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
    private static final ResourceLocation RACE_PARTS_MODEL = dmz("geo/entity/raceparts.geo.json");
    private static final ResourceLocation RACE_PARTS_TEXTURE = dmz("textures/entity/races/raceparts.png");

    public FighterNativeAccessoryLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity fighter, GeoBone playerBone,
                              RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if (WorldMenaceManager.isHerobrine(fighter)) return;
        if ("head".equals(playerBone.getName()) && fighter.isDeadSoul()) {
            renderHalo(poseStack, fighter, buffers, partialTick, packedLight);
            buffers.getBuffer(renderType);
        }
        if ("head".equals(playerBone.getName())
                && fighter.getRace() == com.dmzlivingworld.entity.FighterRace.NAMEKIAN) {
            renderNamekianHeadParts(poseStack, fighter, buffers, partialTick, packedLight);
            buffers.getBuffer(renderType);
        }
        if ("head".equals(playerBone.getName())
                && fighter.getRace() == com.dmzlivingworld.entity.FighterRace.MAJIN) {
            renderMajinHeadParts(poseStack, fighter, buffers, partialTick, packedLight);
            buffers.getBuffer(renderType);
        }
        if ("head".equals(playerBone.getName())
                && fighter.getRace() == com.dmzlivingworld.entity.FighterRace.FROST_DEMON) {
            renderFrostDemonHorns(poseStack, fighter, buffers, partialTick, packedLight);
            buffers.getBuffer(renderType);
        }
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

    /** Uses the same raceparts model, halo bone, energy render type, tint and alpha as DMZ players. */
    private void renderHalo(PoseStack poseStack, AmbientFighterEntity fighter,
                            MultiBufferSource buffers, float partialTick, int packedLight) {
        BakedGeoModel parts = getGeoModel().getBakedModel(RACE_PARTS_MODEL);
        BakedGeoModel body = getGeoModel().getBakedModel(getGeoModel().getModelResource(fighter));
        if (parts == null || body == null) return;
        parts.getBone("halo").ifPresent(halo -> {
            syncTargetBoneAndParents(halo, body);
            VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.energy(RACE_PARTS_TEXTURE));
            getRenderer().renderRecursively(poseStack, fighter, halo, ModRenderTypes.energy(RACE_PARTS_TEXTURE),
                    buffers, consumer, true, partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 0.9569F, 0.3804F, 0.75F);
        });
    }

    private void renderNamekianHeadParts(PoseStack poseStack, AmbientFighterEntity fighter,
                                         MultiBufferSource buffers, float partialTick, int packedLight) {
        BakedGeoModel parts = getGeoModel().getBakedModel(RACE_PARTS_MODEL);
        BakedGeoModel body = getGeoModel().getBakedModel(getGeoModel().getModelResource(fighter));
        if (parts == null || body == null) return;
        float[] color = com.dragonminez.client.util.ColorUtils.hexToRgb(fighter.getBodyColor());
        renderNamekianPart(parts, body, "ears" + (Math.floorMod(fighter.getHeadBone(), 3) + 1),
                poseStack, fighter, buffers, partialTick, packedLight, color);
        renderNamekianPart(parts, body, "antennas" + (Math.floorMod(fighter.getHeadBone(), 2) + 1),
                poseStack, fighter, buffers, partialTick, packedLight, color);
    }

    private void renderMajinHeadParts(PoseStack poseStack, AmbientFighterEntity fighter,
                                      MultiBufferSource buffers, float partialTick, int packedLight) {
        BakedGeoModel parts = getGeoModel().getBakedModel(RACE_PARTS_MODEL);
        BakedGeoModel body = getGeoModel().getBakedModel(getGeoModel().getModelResource(fighter));
        if (parts == null || body == null) return;
        float[] color = com.dragonminez.client.util.ColorUtils.hexToRgb(fighter.getBodyColor());
        // DMZPlayer's race-parts layer always adds ears3 to Majins. Male Majins
        // additionally use the selected majin1/majin2/majin3 cranial head bone.
        renderNamekianPart(parts, body, "ears3", poseStack, fighter, buffers, partialTick, packedLight, color);
        if (!fighter.isFemale()) {
            renderNamekianPart(parts, body, "majin" + (Math.floorMod(fighter.getHeadBone(), 3) + 1),
                    poseStack, fighter, buffers, partialTick, packedLight, color);
        }
    }

    /** Frost Demon horns are race-parts, not bones embedded in the racial body model. */
    private void renderFrostDemonHorns(PoseStack poseStack, AmbientFighterEntity fighter,
                                       MultiBufferSource buffers, float partialTick, int packedLight) {
        BakedGeoModel parts = getGeoModel().getBakedModel(RACE_PARTS_MODEL);
        BakedGeoModel body = getGeoModel().getBakedModel(getGeoModel().getModelResource(fighter));
        if (parts == null || body == null) return;
        // This is the exact fixed tint used by DMZPlayer's DMZRacePartsLayer.
        float[] hornColor = com.dragonminez.client.util.ColorUtils.hexToRgb("#1A1A1A");
        renderNamekianPart(parts, body, "horns" + (Math.floorMod(fighter.getHeadBone(), 5) + 1),
                poseStack, fighter, buffers, partialTick, packedLight, hornColor);
    }

    private void renderNamekianPart(BakedGeoModel parts, BakedGeoModel body, String boneName,
                                    PoseStack poseStack, AmbientFighterEntity fighter,
                                    MultiBufferSource buffers, float partialTick, int packedLight,
                                    float[] color) {
        parts.getBone(boneName).ifPresent(part -> {
            syncTargetBoneAndParents(part, body);
            RenderType type = RenderType.entityCutoutNoCull(RACE_PARTS_TEXTURE);
            VertexConsumer consumer = buffers.getBuffer(type);
            getRenderer().renderRecursively(poseStack, fighter, part, type, buffers, consumer, true,
                    partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                    color[0], color[1], color[2], 1.0F);
        });
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
