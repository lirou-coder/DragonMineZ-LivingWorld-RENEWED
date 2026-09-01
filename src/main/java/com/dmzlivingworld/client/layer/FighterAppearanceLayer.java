package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import com.dragonminez.client.util.ColorUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Race-aware texture composition using only DragonMineZ 2.1.3 artwork.
 * No Living World replacement skins are used.
 */
public final class FighterAppearanceLayer extends GeoRenderLayer<AmbientFighterEntity> {
    private static final float[] WHITE = {1F, 1F, 1F};
    private static final String HUMAN_FACE = "textures/entity/races/humansaiyan/faces/";

    private static final String[] HUMAN_OUTFITS = {
            "fighter", "capsule_corp", "orange_high", "mystic",
            "saiyaman_gi", "future_gohan", "trunks_gi", "videl",
            "yardrat_gi", "tenshinhan_armor", "pride_troper", "strongest",
            "invencible", "gilgamesh", "demon_gi_gohan", "trunks_armor",
            "kaioshin", "zamasu_gi", "fzamasu_gi", "blackgoku", "dragon_clan", "warrior_clan"
    };
    private static final String[] SAIYAN_OUTFITS = {
            "fighter", "capsule_corp", "orange_high", "mystic",
            "raditz", "turles_armor", "bardock_armor", "bardockdbs_armor",
            "vegetanamek_armor", "king_vegeta", "trunks_armor", "saiyaman_gi",
            "broly_dbz", "caulifla", "kaioshin", "zamasu_gi", "fzamasu_gi", "blackgoku",
            "dragon_clan", "warrior_clan", "mystic", "yardrat_gi"
    };
    private static final String[] MAJIN_OUTFITS = {
            "wonder_majin", "mighty_majin", "majinbuu_gi", "evil_buu", "super_buu", "majin21"
    };

    public FighterAppearanceLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, AmbientFighterEntity entity, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (WorldMenaceManager.isHerobrine(entity)) return;
        switch (entity.getRace()) {
            case HUMAN, SAIYAN -> renderHumanSaiyan(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
            case NAMEKIAN -> renderNamekian(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
            case MAJIN -> renderMajin(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
            case FROST_DEMON -> renderFrost(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
            case BIO_ANDROID -> renderBio(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
        }
    }

    private void renderHumanSaiyan(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                                   MultiBufferSource buffers, float pt, int light, int overlay) {
        float[] body = rgb(e.getBodyColor());
        float[] hair = rgb(e.getHairColor());
        float[] eye1 = rgb(e.getEye1Color());
        float[] eye2 = rgb(e.getEye2Color());
        String gender = e.isFemale() ? "female" : "male";

        layer(model, pose, buffers, e,
                dmz("textures/entity/races/humansaiyan/bodytype_" + gender + "_" + e.getBodyType() + ".png"),
                body, pt, light, overlay);

        if (e.getActiveRacialForm() != null && "supersaiyan4".equals(e.getActiveRacialForm().id())) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/humansaiyan/ssj4d_layer1.png"), WHITE, pt, light, overlay);
        }

        String eye = HUMAN_FACE + "humansaiyan_eye_" + e.getEyesType() + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), eye1, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), eye2, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "3.png"), hair, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(HUMAN_FACE + "humansaiyan_nose_" + e.getNoseType() + ".png"), body, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(HUMAN_FACE + "humansaiyan_mouth_" + e.getMouthType() + ".png"), body, pt, light, overlay);

        if (RedRibbonExperimentManager.isExperiment(e)) {
            // Use Dragon Mine Z's real Red Ribbon uniform overlay rather than mapping X-7 to an
            // unrelated human outfit slot. The experiment keeps the LW humanoid body/face below it.
            layer(model, pose, buffers, e, dmz("textures/entity/enemies/redribbon_outfit.png"), WHITE, pt, light, overlay);
        } else if (!hasReplacementArmor(e)) {
            String[] pool = e.getRace() == com.dmzlivingworld.entity.FighterRace.SAIYAN ? SAIYAN_OUTFITS : HUMAN_OUTFITS;
            renderOutfit(model, pose, buffers, e, pool[Math.floorMod(e.getOutfit(), pool.length)], pt, light, overlay);
        }
    }

    private void renderNamekian(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                                MultiBufferSource buffers, float pt, int light, int overlay) {
        String family = e.getOutfit() % 2 == 0 ? "namek_warrior_" : "namek_trader_";
        layer(model, pose, buffers, e,
                dmz("textures/entity/enemies/" + family + Math.floorMod(e.getBodyType(), 4) + ".png"),
                WHITE, pt, light, overlay);
    }

    private void renderMajin(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                             MultiBufferSource buffers, float pt, int light, int overlay) {
        float[] body = rgb(e.getBodyColor());
        float[] eye1 = rgb(e.getEye1Color());
        float[] eye2 = rgb(e.getEye2Color());
        String gender = e.isFemale() ? "female" : "male";
        String root = "textures/entity/races/majin/";
        layer(model, pose, buffers, e, dmz(root + "bodytype_" + gender + "_" + e.getBodyType() + "_layer1.png"), body, pt, light, overlay);
        if (e.getBodyType() == 1) {
            layer(model, pose, buffers, e, dmz(root + "bodytype_" + gender + "_1_layer2.png"), WHITE, pt, light, overlay);
        }
        String face = root + "faces/";
        String eye = face + "majin_eye_" + e.getEyesType() + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), eye1, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), eye2, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "majin_nose_" + e.getNoseType() + ".png"), body, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "majin_mouth_" + e.getMouthType() + ".png"), body, pt, light, overlay);
        if (!hasReplacementArmor(e))
            renderOutfit(model, pose, buffers, e, MAJIN_OUTFITS[Math.floorMod(e.getOutfit(), MAJIN_OUTFITS.length)], pt, light, overlay);
    }

    private void renderFrost(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                             MultiBufferSource buffers, float pt, int light, int overlay) {
        String root = "textures/entity/races/frostdemon/";
        float[][] tints = {rgb(e.getBodyColor()), rgb(e.getBodyColor2()), rgb(e.getBodyColor3()), WHITE};
        for (int i = 1; i <= 4; i++) {
            layer(model, pose, buffers, e, dmz(root + "bodytype_" + e.getBodyType() + "_layer" + i + ".png"), tints[i - 1], pt, light, overlay);
        }
        String face = root + "faces/";
        String eye = face + "frostdemon_eye_" + e.getEyesType() + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "frostdemon_nose_" + e.getNoseType() + ".png"), rgb(e.getBodyColor()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "frostdemon_mouth_" + e.getMouthType() + ".png"), rgb(e.getBodyColor()), pt, light, overlay);
    }

    private void renderBio(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                           MultiBufferSource buffers, float pt, int light, int overlay) {
        String root = "textures/entity/races/bioandroid/";
        float[] main = rgb(e.getBodyColor());
        float[] second = rgb(e.getBodyColor2());
        float[] accent = rgb(e.getBodyColor3());
        float[][] tints = {main, second, accent, WHITE, WHITE};
        for (int i = 1; i <= 5; i++) {
            layer(model, pose, buffers, e, dmz(root + "base_" + e.getBodyType() + "_layer" + i + ".png"), tints[i - 1], pt, light, overlay);
        }
        String form = switch (e.getBodyType()) {
            case 1 -> "semiperfect";
            case 2 -> "perfect";
            default -> "base";
        };
        layer(model, pose, buffers, e, dmz(root + "faces/" + form + "_eye_layer0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(root + "faces/" + form + "_eye_layer1.png"), rgb(e.getEye1Color()), pt, light, overlay);
    }

    /**
     * LW's procedural outfit is the fighter's default clothing. A genuine equipped chest/leg
     * armor set is a replacement visual rendered by DMZ's native DMZSagaArmorLayer, not a second
     * costume to paint over the default one. Hiding the default only while replacement armor is
     * actually equipped prevents two complete clothing textures from z-fighting/overlapping.
     */
    private static boolean hasReplacementArmor(AmbientFighterEntity e) {
        return e != null && (!e.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !e.getItemBySlot(EquipmentSlot.LEGS).isEmpty());
    }

    private void renderOutfit(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                              AmbientFighterEntity e, String outfit, float pt, int light, int overlay) {
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer1.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer2.png"), WHITE, pt, light, overlay);
    }

    private void layer(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                       AmbientFighterEntity entity, ResourceLocation texture, float[] rgb,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType type = RenderType.entityCutoutNoCull(texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, pose, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, rgb[0], rgb[1], rgb[2], 1.0F);
    }

    private static float[] rgb(String hex) { return ColorUtils.hexToRgb(hex); }
    private static ResourceLocation dmz(String path) { return new ResourceLocation("dragonminez", path); }
}
