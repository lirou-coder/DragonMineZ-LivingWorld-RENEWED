package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
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
    private static final float[] MAJIN_DARK_GRAY = ColorUtils.hexToRgb("#242424");
    private static final String HUMAN_FACE = "textures/entity/races/humansaiyan/faces/";
    private static final String MAJIN_FACE_ROOT = "textures/entity/races/majin/faces/majin_";
    private static final ResourceLocation[][] MAJIN_EYES = {
            {majinFace("majin_eye_0_0.png"), majinFace("majin_eye_0_1.png"), majinFace("majin_eye_0_2.png")},
            {majinFace("majin_eye_1_0.png"), majinFace("majin_eye_1_1.png"), majinFace("majin_eye_1_2.png")},
            {majinFace("majin_eye_2_0.png"), majinFace("majin_eye_2_1.png")}
    };
    private static final ResourceLocation[] MAJIN_NOSES = {
            majinFace("majin_nose_0.png"), majinFace("majin_nose_1.png")
    };
    private static final ResourceLocation[] MAJIN_MOUTHS = {
            majinFace("majin_mouth_0.png"), majinFace("majin_mouth_1.png")
    };

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
        // Never let a stale/incorrect dispatch paint a Human face over another race.
        if (e.getRace() != com.dmzlivingworld.entity.FighterRace.HUMAN
                && e.getRace() != com.dmzlivingworld.entity.FighterRace.SAIYAN) return;
        float[] body = rgb(e.getBodyColor());
        float[] hair = rgb(e.getHairColor());
        float[] eye1 = rgb(e.getEye1Color());
        float[] eye2 = rgb(e.getEye2Color());
        String gender = e.isFemale() ? "female" : "male";

        layer(model, pose, buffers, e,
                dmz("textures/entity/races/humansaiyan/bodytype_" + gender + "_" + e.getBodyType() + ".png"),
                body, pt, light, overlay);

        // DMZ's player skin layer paints this scalp texture independently from the
        // strand geometry. Preset id 5 is the native bald option and deliberately
        // suppresses both hair strands and HairBase.
        if (shouldRenderHumanSaiyanHairBase(e)) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/hair_base.png"),
                    hair, pt, light, overlay);
        }

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
        String root = "textures/entity/races/namekian/";
        int bodyType = Math.floorMod(e.getBodyType(), 3);
        float[][] tints = {rgb(e.getBodyColor()), rgb(e.getBodyColor2()), rgb(e.getBodyColor3()), rgb(e.getHairColor())};
        int layers = bodyType == 0 ? 3 : 4;
        for (int i = 1; i <= layers; i++) {
            layer(model, pose, buffers, e, dmz(root + "bodytype_" + bodyType + "_layer" + i + ".png"),
                    tints[i - 1], pt, light, overlay);
        }
        String face = root + "faces/";
        String eye = face + "namekian_eye_" + Math.floorMod(e.getEyesType(), 5) + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "3.png"), rgb(e.getBodyColor()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "namekian_nose_" + Math.floorMod(e.getNoseType(), 2) + ".png"),
                rgb(e.getBodyColor()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(face + "namekian_mouth_" + Math.floorMod(e.getMouthType(), 2) + ".png"),
                rgb(e.getBodyColor()), pt, light, overlay);
        if (!hasReplacementArmor(e)) {
            int outfit = Math.floorMod(e.getOutfit(), HUMAN_OUTFITS.length + SAIYAN_OUTFITS.length);
            String id = outfit < HUMAN_OUTFITS.length ? HUMAN_OUTFITS[outfit]
                    : SAIYAN_OUTFITS[outfit - HUMAN_OUTFITS.length];
            renderOutfit(model, pose, buffers, e, id, pt, light, overlay);
        }
    }

    private void renderMajin(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                             MultiBufferSource buffers, float pt, int light, int overlay) {
        if (e.getRace() != com.dmzlivingworld.entity.FighterRace.MAJIN) return;
        // Resolve the racial model for this pass instead of trusting the model retained by the
        // preceding entity render. This prevents a Human/Saiyan baked model from leaking into a
        // Majin face when GeckoLib reuses one renderer for consecutive fighters.
        BakedGeoModel majinModel = getGeoModel().getBakedModel(getGeoModel().getModelResource(e));
        if (majinModel == null) return;
        float[] body = rgb(e.getBodyColor());
        float[] eye1 = rgb(e.getEye1Color());
        String gender = e.isFemale() ? "female" : "male";
        String root = "textures/entity/races/majin/";
        layer(majinModel, pose, buffers, e, dmz(root + "bodytype_" + gender + "_" + e.getBodyType() + "_layer1.png"), body, pt, light, overlay);
        if (e.getBodyType() == 1) {
            layer(majinModel, pose, buffers, e, dmz(root + "bodytype_" + gender + "_1_layer2.png"), rgb(e.getBodyColor2()), pt, light, overlay);
        }
        int eyeType = Math.floorMod(e.getEyesType(), MAJIN_EYES.length);
        ResourceLocation[] eye = MAJIN_EYES[eyeType];
        // DMZ does not give Majins the white sclera used by Human/Saiyan faces.
        // Preset 0 is fully eye-coloured; the other presets use the native dark
        // background and body-coloured inner layer from DMZSkinLayer.
        // Exact DMZSkinLayer Majin mapping: preset 0 is body-coloured; presets 1/2
        // use the dark structural layer and Eye1Color for the coloured eye pixels.
        float[] eyeBackground = eyeType == 0 ? body : MAJIN_DARK_GRAY;
        float[] eyeInner = eyeType == 0 ? body : eye1;
        layer(majinModel, pose, buffers, e, eye[0], eyeBackground, pt, light, overlay);
        layer(majinModel, pose, buffers, e, eye[1], eyeInner, pt, light, overlay);
        if (eye.length > 2) layer(majinModel, pose, buffers, e, eye[2], body, pt, light, overlay);
        float[] faceColor = e.getBodyType() == 1 ? rgb(e.getBodyColor2()) : body;
        layer(majinModel, pose, buffers, e, MAJIN_NOSES[Math.floorMod(e.getNoseType(), MAJIN_NOSES.length)],
                faceColor, pt, light, overlay);
        layer(majinModel, pose, buffers, e, MAJIN_MOUTHS[Math.floorMod(e.getMouthType(), MAJIN_MOUTHS.length)],
                faceColor, pt, light, overlay);
        if (!hasReplacementArmor(e))
            renderOutfit(majinModel, pose, buffers, e, MAJIN_OUTFITS[Math.floorMod(e.getOutfit(), MAJIN_OUTFITS.length)], pt, light, overlay);
    }

    private void renderFrost(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                             MultiBufferSource buffers, float pt, int light, int overlay) {
        String root = "textures/entity/races/frostdemon/";
        int bodyType = Math.floorMod(e.getBodyType(), 3);
        var activeForm = e.getActiveRacialForm();
        String modelKey = activeForm == null ? "" : activeForm.modelKey();
        String formId = activeForm == null ? "" : activeForm.id();
        float[] body1 = rgb(e.getBodyColor());
        float[] body2 = rgb(e.getBodyColor2());
        float[] body3 = rgb(e.getBodyColor3());
        float[] hair = rgb(e.getHairColor());

        // Mirror DMZ SkinGathererProvider.resolveBodyFrostDemon exactly. Second form uses
        // the normal body textures; Third has its own bulky atlas; Final/Full Power and
        // Fifth use their respective slim atlases with body-type-specific layer colours.
        boolean third = "frostdemon_third".equals(modelKey);
        boolean fifth = "fifth".equals(formId) || "frostdemon_fifth".equals(modelKey);
        boolean finalFamily = "final".equals(formId) || "fullpower".equals(formId)
                || "frostdemon_fp".equals(modelKey) || fifth;
        if (!finalFamily) {
            String prefix = third ? "thirdform_bodytype_" : "bodytype_";
            String base = root + prefix + bodyType + "_layer";
            layer(model, pose, buffers, e, dmz(base + "1.png"), body1, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(base + "2.png"), body2, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(base + "3.png"), body3, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(base + "4.png"), hair, pt, light, overlay);
            if (bodyType == 0)
                layer(model, pose, buffers, e, dmz(base + "5.png"), new float[]{1.0F, 0.6471F, 0.0F}, pt, light, overlay);
        } else {
            String prefix = fifth ? "fifth_bodytype_" : "finalform_bodytype_";
            String base = root + prefix + bodyType + "_layer";
            layer(model, pose, buffers, e, dmz(base + "1.png"), body1, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(base + "2.png"), bodyType == 1 ? body2 : hair, pt, light, overlay);
            if (bodyType == 1) {
                layer(model, pose, buffers, e, dmz(base + "3.png"), body3, pt, light, overlay);
                layer(model, pose, buffers, e, dmz(base + "4.png"), hair, pt, light, overlay);
            } else if (bodyType == 2) {
                layer(model, pose, buffers, e, dmz(base + "3.png"), hair, pt, light, overlay);
                // DMZ deliberately emits layer 2 again with bodyColor2 for body type 2.
                layer(model, pose, buffers, e, dmz(base + "2.png"), body2, pt, light, overlay);
            }
        }
        String face = root + "faces/";
        int eyeType = Math.floorMod(e.getEyesType(), 6);
        String eye = face + "frostdemon_eye_" + eyeType + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), fifth ? new float[]{0.8196F, 0.102F, 0.0667F} : new float[]{0.949F, 0.949F, 0.949F}, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
        if (fifth) {
            layer(model, pose, buffers, e, dmz(face + "frostdemon_fifth_mouth.png"), body1, pt, light, overlay);
        } else {
            float[] detail = (!finalFamily || bodyType == 1) ? body2 : body1;
            layer(model, pose, buffers, e, dmz(face + "frostdemon_nose_" + Math.floorMod(e.getNoseType(), 2) + ".png"), detail, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(face + "frostdemon_mouth_" + Math.floorMod(e.getMouthType(), 2) + ".png"), detail, pt, light, overlay);
        }
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

    private static boolean shouldRenderHumanSaiyanHairBase(AmbientFighterEntity e) {
        if (e == null || (e.getRace() != com.dmzlivingworld.entity.FighterRace.HUMAN
                && e.getRace() != com.dmzlivingworld.entity.FighterRace.SAIYAN)) return false;
        if (e.getHairId() == 5) return false;
        CustomHair hair = HairManager.getPresetHair(e.getHairId(), e.getRace().dmzId());
        if (hair == null || hair.isEmpty()) hair = HairManager.getPresetHair(e.getHairId(), "human");
        return hair != null && hair.getVisibleStrandCount() > 0;
    }

    private void renderOutfit(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                              AmbientFighterEntity e, String outfit, float pt, int light, int overlay) {
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer1.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer2.png"), WHITE, pt, light, overlay);
    }

    private void layer(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                       AmbientFighterEntity entity, ResourceLocation texture, float[] rgb,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!faceTextureAllowed(entity, texture)) return;
        RenderType type = RenderType.entityCutoutNoCull(texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, pose, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, rgb[0], rgb[1], rgb[2], 1.0F);
    }

    private static float[] rgb(String hex) { return ColorUtils.hexToRgb(hex); }
    private static ResourceLocation dmz(String path) { return new ResourceLocation("dragonminez", path); }
    private static ResourceLocation majinFace(String file) {
        return dmz("textures/entity/races/majin/faces/" + file);
    }

    /**
     * Final renderer boundary: a Majin can never consume a face asset belonging to Human/Saiyan,
     * Namekian, Frost Demon, Bio-Android or Janemba. Keeping this check in the common texture
     * emission path also protects existing saved fighters and any future caller added to this layer.
     */
    private static boolean faceTextureAllowed(AmbientFighterEntity entity, ResourceLocation texture) {
        if (entity == null || texture == null
                || entity.getRace() != com.dmzlivingworld.entity.FighterRace.MAJIN) return true;
        String path = texture.getPath();
        if (!path.contains("/faces/")) return true;
        return "dragonminez".equals(texture.getNamespace()) && path.startsWith(MAJIN_FACE_ROOT)
                && !path.contains("janemba");
    }
}
