package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dragonminez.Reference;
import com.dragonminez.client.render.hair.HairEntityState;
import com.dragonminez.client.render.hair.HairMeshBuilder;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairCodec;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.hair.HairStyleSlot;
import com.dragonminez.common.stats.character.Character;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

/** Delegates humanoid hair geometry entirely to DragonMineZ's HairRenderer. */
public final class FighterHairLayer extends GeoRenderLayer<AmbientFighterEntity> {
    private static final ResourceLocation HAIR_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Reference.MOD_ID, "textures/entity/races/hair.png");
    private final HairMeshBuilder meshBuilder = new HairMeshBuilder();
    private final HairEntityState hairState = new HairEntityState();

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
        String hairType = entity.getActiveRacialForm() == null ? "base" : entity.getActiveRacialForm().hairType();
        HairStyleSlot slot = hairSlot(hairType);
        String forcedHairCode = entity.getActiveRacialFormConfig() == null
                ? "" : entity.getActiveRacialFormConfig().forcedHairCode();
        CustomHair hair = decodeForcedHair(forcedHairCode, slot);
        if (hair == null || hair.isEmpty()) hair = HairManager.getPresetStyle(entity.getHairId(), slot);
        if (hair == null || hair.isEmpty()) return;
        int hairRgb = toRgb(character.getRgbHairColor());

        poseStack.pushPose();
        RenderUtils.translateToPivotPoint(poseStack, bone);
        hairState.updatePoses(hair, hair, 0.0F, slot, slot);
        VertexConsumer hairBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(HAIR_TEXTURE));
        var camera = Minecraft.getInstance().getCameraEntity();
        // DMZ 2.2 now selects the expensive pixel-detail mesh only at close range. Keep the
        // Fighter layer on the same 32-block threshold used by DMZHairLayer.
        boolean pixelDetail = camera == null || entity.distanceToSqr(camera) <= 32.0D * 32.0D;
        meshBuilder.emit(hairBuffer, poseStack.last().pose(), poseStack.last().normal(), hairState, false,
                hairRgb, hairRgb, false, false, packedLight, packedOverlay, 1.0F,
                pixelDetail, null, null, null);
        bufferSource.getBuffer(renderType);
        poseStack.popPose();
    }

    private static HairStyleSlot hairSlot(String hairType) {
        HairStyleSlot slot = HairStyleSlot.byHairType(hairType);
        return slot == null ? HairStyleSlot.BASE : slot;
    }

    private static CustomHair decodeForcedHair(String code, HairStyleSlot slot) {
        if (code == null || code.isBlank()) return null;
        try {
            var set = HairCodec.fromFullSetCode(code);
            if (set != null && set.get(slot) != null) return set.get(slot);
            return HairCodec.fromCode(code);
        } catch (RuntimeException ignored) {
            // A malformed live form config must not break rendering; fall back to preset hair.
            return null;
        }
    }

    private static int toRgb(float[] rgb) {
        if (rgb == null || rgb.length < 3) return 0xFFFFFF;
        int r = Math.round(Math.max(0.0F, Math.min(1.0F, rgb[0])) * 255.0F);
        int g = Math.round(Math.max(0.0F, Math.min(1.0F, rgb[1])) * 255.0F);
        int b = Math.round(Math.max(0.0F, Math.min(1.0F, rgb[2])) * 255.0F);
        return (r << 16) | (g << 8) | b;
    }
}
