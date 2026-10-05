package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import com.dmzlivingworld.world.SairensRaceCompat;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.client.util.ArmorTextureResolver;
import com.dragonminez.client.render.util.ModRenderTypes;
import com.dragonminez.client.render.hair.SaiyanTailMeshBuilder;
import com.dragonminez.common.init.armor.DbzArmorItem;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
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
    private final SaiyanTailMeshBuilder tailMesh = new SaiyanTailMeshBuilder();

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
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity entity, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        tailMesh.capture(entity, bone, poseStack);
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
            case BIO_ANDROID -> {
                if (SairensRaceCompat.isBioAndroidHumanModel())
                    renderHumanSaiyan(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
                else renderBio(poseStack, entity, bakedModel, bufferSource, partialTick, packedLight, packedOverlay);
            }
            case ZAARAKIN -> renderSairensHumanRace(poseStack, entity, bakedModel, bufferSource, "zaarakin", 13, partialTick, packedLight, packedOverlay);
            case ANTORANIAN -> renderSairensHumanRace(poseStack, entity, bakedModel, bufferSource, "antoranian", 8, partialTick, packedLight, packedOverlay);
        }
    }

    private void renderHumanSaiyan(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                                   MultiBufferSource buffers, float pt, int light, int overlay) {
        // Never let a stale/incorrect dispatch paint a Human face over another race.
        if (e.getRace() != com.dmzlivingworld.entity.FighterRace.HUMAN
            && e.getRace() != com.dmzlivingworld.entity.FighterRace.SAIYAN
            && !(e.getRace() == com.dmzlivingworld.entity.FighterRace.BIO_ANDROID
            && SairensRaceCompat.isBioAndroidHumanModel())) return;
        float[] body = rgb(e.getBodyColor());
        float[] hair = rgb(e.getHairColor());
        float[] eye1 = rgb(e.getEye1Color());
        float[] eye2 = rgb(e.getEye2Color());
        String gender = e.isFemale() ? "female" : "male";

        var activeForm = e.getActiveRacialFormConfig();
        if (activeForm != null && "oozaru".equalsIgnoreCase(activeForm.modelKey())) {
            String root = "textures/entity/races/humansaiyan/oozaru_";
            layer(model, pose, buffers, e, dmz(root + "layer1.png"), rgb(e.getBodyColor2()), pt, light, overlay);
            layer(model, pose, buffers, e, dmz(root + "layer2.png"), body, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(root + "layer3.png"), WHITE, pt, light, overlay);
            return;
        }

        int bodyType = Math.max(1, Math.min(2, e.getBodyType()));
        String bodyBase = "textures/entity/races/humansaiyan/bodytype_" + gender + "_" + bodyType + "_";
        var raceConfig = ConfigManager.getRaceCharacter(e.getRace().dmzId());
        ResourceLocation slimLayer = dmz(bodyBase + "slim_layer1.png");
        if (raceConfig != null && raceConfig.isSlimBodyType(bodyType)
                && Minecraft.getInstance().getResourceManager().getResource(slimLayer).isPresent()) bodyBase += "slim_";
        bodyLayer(model, pose, buffers, e, dmz(bodyBase + "layer1.png"),
                ColorUtils.skinBaseTone(body), pt, light, overlay);
        ResourceLocation humanShadow = dmz(bodyBase + "layer2.png");
        if (textureExists(humanShadow)) {
            // Exact DMZ 2.2 pipeline: the main atlas uses skinBaseTone and the separate
            // translucent atlas uses skinShadowTone, both derived from BodyColor1.
            nativeShadowLayer(model, pose, buffers, e, humanShadow,
                    ColorUtils.skinShadowTone(body), pt, light, overlay);
        }
        if (e.getRace() == com.dmzlivingworld.entity.FighterRace.BIO_ANDROID && SairensRaceCompat.isBioAndroidHumanModel()) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/" + gender + "_android.png"), WHITE, pt, light, overlay);
        }

        if (e.getRace() == com.dmzlivingworld.entity.FighterRace.SAIYAN && e.shouldRenderSaiyanTail()) {
            float[] tailColor = rgb(e.getBodyColor2());
            if (tailMesh.hasCapture(e)) {
                RenderType tailType = RenderType.entityCutoutNoCull(SaiyanTailMeshBuilder.TEXTURE);
                tailMesh.emit(buffers.getBuffer(tailType), tailColor, light, overlay, 1.0F, true);
            } else {
                layer(model, pose, buffers, e, dmz("textures/entity/races/tail1.png"), tailColor,
                        pt, light, overlay);
            }
        }

        // DMZ's player skin layer paints this scalp texture independently from the
        // strand geometry. Preset id 5 is the native bald option and deliberately
        // suppresses both hair strands and HairBase.
        if (shouldRenderHumanSaiyanHairBase(e)) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/hair_base.png"),
                    hair, pt, light, overlay);
        }

        if (e.isSaiyanSsj4Form()) {
            var form = e.getActiveRacialFormConfig();
            boolean slim = !e.isFemale() && ConfigManager.getRaceCharacter(e.getRace().dmzId()) != null
                    && ConfigManager.getRaceCharacter(e.getRace().dmzId()).isSlimBodyType(e.getBodyType());
            String family = form != null && "ssj4gt".equalsIgnoreCase(form.modelKey()) ? "ssj4gt" : "ssj4d";
            String ssj4Layer = family + (slim ? "slim" : "") + "_layer1.png";
            float[] furColor = form != null && form.furUsesHairColor()
                    ? hair : rgb(e.getBodyColor2());
            layerWithNativeShadow(model, pose, buffers, e,
                    "textures/entity/races/humansaiyan/" + ssj4Layer, furColor, pt, light, overlay);
        }

        String eye = HUMAN_FACE + "humansaiyan_eye_" + e.getEyesType() + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), eye1, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), eye2, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "3.png"), hair, pt, light, overlay);
        renderFaceTexture(model, pose, buffers, e,
                HUMAN_FACE + "humansaiyan_nose_" + e.getNoseType() + ".png", body, pt, light, overlay);
        renderFaceTexture(model, pose, buffers, e,
                HUMAN_FACE + "humansaiyan_mouth_" + e.getMouthType() + ".png", body, pt, light, overlay);

        if (RedRibbonExperimentManager.isExperiment(e)) {
            // Use Dragon Mine Z's real Red Ribbon uniform overlay rather than mapping X-7 to an
            // unrelated human outfit slot. The experiment keeps the LW humanoid body/face below it.
            layer(model, pose, buffers, e, dmz("textures/entity/enemies/redribbon_outfit.png"), WHITE, pt, light, overlay);
        } else {
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
            String selected = root + "bodytype_" + bodyType + "_layer" + i + ".png";
            String fallback = root + "bodytype_0_layer" + i + ".png";
            layerWithShadow(model, pose, buffers, e, selected, fallback,
                    tints[i - 1], pt, light, overlay);
        }
        String face = root + "faces/";
        if (!renderBorrowedHumanEyes(model, pose, buffers, e, e.getEyesType(), 5, WHITE,
                rgb(e.getBodyColor()), pt, light, overlay)) {
            String eye = face + "namekian_eye_" + Math.floorMod(e.getEyesType(), 5) + "_";
            layer(model, pose, buffers, e, dmz(eye + "0.png"), WHITE, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
            layer(model, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
            layer(model, pose, buffers, e, dmz(eye + "3.png"), rgb(e.getBodyColor()), pt, light, overlay);
        }
        float[] namekFaceColor = bodyType == 1 || bodyType == 2
                ? rgb(e.getHairColor()) : rgb(e.getBodyColor());
        renderFaceFeature(model, pose, buffers, e, "nose", e.getNoseType(), 2,
                face + "namekian_nose_" + Math.floorMod(e.getNoseType(), 2) + ".png",
                namekFaceColor, pt, light, overlay);
        renderFaceFeature(model, pose, buffers, e, "mouth", e.getMouthType(), 2,
                face + "namekian_mouth_" + Math.floorMod(e.getMouthType(), 2) + ".png",
                namekFaceColor, pt, light, overlay);
        {
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
        int bodyType = Math.floorMod(e.getBodyType(), 3);
        String selected = root + "bodytype_" + gender + "_" + bodyType + "_layer1.png";
        String fallback = root + "bodytype_" + gender + "_0_layer1.png";
        layerWithShadow(majinModel, pose, buffers, e, selected, fallback, body, pt, light, overlay);
        optionalLayerWithShadow(majinModel, pose, buffers, e,
                root + "bodytype_" + gender + "_" + bodyType + "_layer2.png",
                rgb(e.getBodyColor2()), pt, light, overlay);
        optionalLayerWithShadow(majinModel, pose, buffers, e,
                root + "bodytype_" + gender + "_" + bodyType + "_layer3.png",
                rgb(e.getBodyColor3()), pt, light, overlay);
        var activeMajinForm = e.getActiveRacialForm();
        if (e.isFemale() && activeMajinForm != null
                && ("super".equals(activeMajinForm.id()) || "ultra".equals(activeMajinForm.id()))) {
            layer(majinModel, pose, buffers, e, dmz("textures/entity/races/tail1.png"), body,
                    pt, light, overlay);
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
        if (!renderBorrowedHumanEyes(majinModel, pose, buffers, e, e.getEyesType(), 3,
                MAJIN_DARK_GRAY, body, pt, light, overlay)) {
            layer(majinModel, pose, buffers, e, eye[0], eyeBackground, pt, light, overlay);
            layer(majinModel, pose, buffers, e, eye[1], eyeInner, pt, light, overlay);
            if (eye.length > 2) layer(majinModel, pose, buffers, e, eye[2], body, pt, light, overlay);
        }
        float[] faceColor = bodyType == 1 ? rgb(e.getBodyColor2()) : body;
        renderFaceFeature(majinModel, pose, buffers, e, "nose", e.getNoseType(), 2,
                MAJIN_NOSES[Math.floorMod(e.getNoseType(), MAJIN_NOSES.length)].getPath(), faceColor,
                pt, light, overlay);
        renderFaceFeature(majinModel, pose, buffers, e, "mouth", e.getMouthType(), 2,
                MAJIN_MOUTHS[Math.floorMod(e.getMouthType(), MAJIN_MOUTHS.length)].getPath(), faceColor,
                pt, light, overlay);
        renderOutfit(majinModel, pose, buffers, e, MAJIN_OUTFITS[Math.floorMod(e.getOutfit(), MAJIN_OUTFITS.length)], pt, light, overlay);
    }

    private void renderFrost(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                             MultiBufferSource buffers, float pt, int light, int overlay) {
        // GeckoLib's baked models are mutable and shared. Resolve the exact current Frost geo at
        // this render boundary; the argument can still refer to the preceding actor/form.
        BakedGeoModel frostModel = getGeoModel().getBakedModel(getGeoModel().getModelResource(e));
        if (frostModel == null) return;
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
        boolean bulky = activeForm == null || "second".equals(formId)
                || "frostdemon_second".equals(modelKey) || third;
        if (bulky) {
            String prefix = third ? "thirdform_bodytype_" : "bodytype_";
            String base = root + prefix + bodyType + "_layer";
            String fallback = root + prefix + "0_layer";
            layerWithShadow(frostModel, pose, buffers, e, base + "1.png", fallback + "1.png", body1, pt, light, overlay);
            layerWithShadow(frostModel, pose, buffers, e, base + "2.png", fallback + "2.png", body2, pt, light, overlay);
            layerWithShadow(frostModel, pose, buffers, e, base + "3.png", fallback + "3.png", body3, pt, light, overlay);
            layerWithShadow(frostModel, pose, buffers, e, base + "4.png", fallback + "4.png", hair, pt, light, overlay);
            if (bodyType == 0)
                layerWithShadow(frostModel, pose, buffers, e, base + "5.png", fallback + "5.png", rgb("#E67D40"), pt, light, overlay);
        } else {
            String prefix = fifth ? "fifth_bodytype_" : "finalform_bodytype_";
            String base = root + prefix + bodyType + "_layer";
            String fallback = root + prefix + "0_layer";
            layerWithShadow(frostModel, pose, buffers, e, base + "1.png", fallback + "1.png", body1, pt, light, overlay);
            layerWithShadow(frostModel, pose, buffers, e, base + "2.png", fallback + "2.png", bodyType == 1 ? body2 : hair, pt, light, overlay);
            if (bodyType == 1) {
                layerWithShadow(frostModel, pose, buffers, e, base + "3.png", fallback + "3.png", body3, pt, light, overlay);
                layerWithShadow(frostModel, pose, buffers, e, base + "4.png", fallback + "4.png", hair, pt, light, overlay);
            } else if (bodyType == 2) {
                layerWithShadow(frostModel, pose, buffers, e, base + "3.png", fallback + "3.png", hair, pt, light, overlay);
                // DMZ deliberately emits layer 2 again with bodyColor2 for body type 2.
                layerWithShadow(frostModel, pose, buffers, e, base + "2.png", fallback + "2.png", body2, pt, light, overlay);
            }
        }
        if ("frostdemon_mecha".equals(modelKey)) {
            layer(frostModel, pose, buffers, e, dmz(root + "mechaform_layer1.png"), body1,
                    pt, light, overlay);
        } else if ("frostdemon_metalcore".equals(modelKey)) {
            layer(frostModel, pose, buffers, e, dmz(root + "metalcore_layer1.png"), rgb("#9BA377"),
                    pt, light, overlay);
            layer(frostModel, pose, buffers, e, dmz(root + "metalcore_layer2.png"), rgb("#20211A"),
                    pt, light, overlay);
        }
        String face = root + "faces/";
        int eyeType = Math.floorMod(e.getEyesType(), 7);
        String eye = face + "frostdemon_eye_" + eyeType + "_";
        boolean primitiveFace = activeForm == null || "second".equals(formId) || "third".equals(formId);
        float[] detail = (primitiveFace || bodyType == 1) ? body2 : body1;
        if (!renderBorrowedHumanEyes(frostModel, pose, buffers, e, e.getEyesType(), 7,
                WHITE, detail, pt, light, overlay)) {
            float[] eyeBackground = fifth ? rgb("#D11A11")
                    : "frostdemon_metalcore".equals(modelKey) ? rgb("#242424") : rgb("#F2F2F2");
            layer(frostModel, pose, buffers, e, dmz(eye + "0.png"), eyeBackground, pt, light, overlay);
            // Eye preset 6 intentionally contains only its structural layer in DMZ 2.2.
            if (eyeType < 6) {
                layer(frostModel, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
                layer(frostModel, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
            }
        }
        if (fifth) {
            layer(frostModel, pose, buffers, e, dmz(face + "frostdemon_fifth_mouth.png"), body1, pt, light, overlay);
        } else {
            renderFaceFeature(frostModel, pose, buffers, e, "nose", e.getNoseType(), 2,
                    face + "frostdemon_nose_" + Math.floorMod(e.getNoseType(), 2) + ".png", detail,
                    pt, light, overlay);
            renderFaceFeature(frostModel, pose, buffers, e, "mouth", e.getMouthType(), 2,
                    face + "frostdemon_mouth_" + Math.floorMod(e.getMouthType(), 2) + ".png", detail,
                    pt, light, overlay);
        }
    }

    private void renderBio(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                           MultiBufferSource buffers, float pt, int light, int overlay) {
        if (e.getRace() != com.dmzlivingworld.entity.FighterRace.BIO_ANDROID) return;
        BakedGeoModel bioModel = getGeoModel().getBakedModel(getGeoModel().getModelResource(e));
        if (bioModel == null) return;
        String root = "textures/entity/races/bioandroid/";
        float[] main = rgb(e.getBodyColor());
        float[] second = rgb(e.getBodyColor2());
        float[] accent = rgb(e.getBodyColor3());
        float[] hair = rgb(e.getHairColor());
        // DMZ 2.2 ships six native Bio-Android body presets (0..5), not three.
        int bodyType = Math.floorMod(e.getBodyType(), 6);
        var activeForm = e.getActiveRacialForm();
        String formId = activeForm == null ? "" : activeForm.id();
        String modelKey = activeForm == null ? "" : activeForm.modelKey();
        String phase = "base";
        if ("semiperfect".equals(formId) || "bioandroid_semi".equals(modelKey)) phase = "semiperfect";
        else if (activeForm != null) phase = "perfect";
        float[][] tints = {main, second, accent, hair};
        for (int i = 1; i <= 4; i++) {
            layerWithFallback(bioModel, pose, buffers, e,
                    root + phase + "_" + bodyType + "_layer" + i + ".png",
                    root + phase + "_0_layer" + i + ".png", tints[i - 1], pt, light, overlay);
        }
        if (!"xenomax".equals(formId) && !"xenofp".equals(formId))
            layerWithFallback(bioModel, pose, buffers, e,
                    root + phase + "_" + bodyType + "_layer5.png",
                    root + phase + "_0_layer5.png", rgb("#EDD747"), pt, light, overlay);
        boolean xenoModel = "bioandroid_xeno".equals(modelKey) || "bioandroid_xenofp".equals(modelKey);
        if (xenoModel) {
            layer(bioModel, pose, buffers, e, dmz(root + "xenoform_layer1.png"), WHITE, pt, light, overlay);
            if (xenoModel)
                layer(bioModel, pose, buffers, e, dmz(root + "xenoform_layer2.png"), WHITE, pt, light, overlay);
        }
        if (!renderBorrowedHumanEyes(bioModel, pose, buffers, e, e.getEyesType(), 4, WHITE,
                second, pt, light, overlay)) {
            int nativeEye = Math.floorMod(e.getEyesType(), 4);
            String eyeSuffix = nativeEye == 0 ? "" : "_" + nativeEye;
            layer(bioModel, pose, buffers, e, dmz(root + "faces/" + phase + "_eye" + eyeSuffix + "_layer0.png"), rgb(e.getEye2Color()), pt, light, overlay);
            layer(bioModel, pose, buffers, e, dmz(root + "faces/" + phase + "_eye" + eyeSuffix + "_layer1.png"), rgb(e.getEye1Color()), pt, light, overlay);
        }
        renderFaceFeature(bioModel, pose, buffers, e, "nose", e.getNoseType(), 1, null,
                second, pt, light, overlay);
        renderFaceFeature(bioModel, pose, buffers, e, "mouth", e.getMouthType(), 1, null,
                second, pt, light, overlay);
    }

    private void renderSairensHumanRace(PoseStack pose, AmbientFighterEntity e, BakedGeoModel model,
                                        MultiBufferSource buffers, String raceRoot, int eyeCount,
                                        float pt, int light, int overlay) {
        if (!SairensRaceCompat.isLoaded()) return;
        String gender = e.isFemale() ? "female" : "male";
        int bodyType = Math.max(1, Math.min(2, e.getBodyType()));
        float[] body = rgb(e.getBodyColor());
        float[] body2 = rgb(e.getBodyColor2());
        float[] body3 = rgb(e.getBodyColor3());
        layer(model, pose, buffers, e, dmz("textures/entity/races/" + raceRoot + "/bodytype_" + gender + "_" + bodyType + ".png"), body, pt, light, overlay);
        if (shouldRenderHumanSaiyanHairBase(e)) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/hair_base.png"), rgb(e.getHairColor()), pt, light, overlay);
        }
        layer(model, pose, buffers, e, dmz("textures/entity/races/" + raceRoot + "/bodytype_" + gender + "_" + bodyType + "_layer2.png"), body2, pt, light, overlay);
        if ("zaarakin".equals(raceRoot)) {
            layer(model, pose, buffers, e, dmz("textures/entity/races/" + raceRoot + "/bodytype_" + gender + "_" + bodyType + "_layer3.png"), body3, pt, light, overlay);
        }
        String faceRoot = "textures/entity/races/" + raceRoot + "/faces/";
        if (!renderBorrowedHumanEyes(model, pose, buffers, e, e.getEyesType(), eyeCount,
                WHITE, body, pt, light, overlay)) {
            String face = faceRoot + raceRoot + "_eye_" + Math.floorMod(e.getEyesType(), eyeCount) + "_";
            layer(model, pose, buffers, e, dmz(face + "0.png"), WHITE, pt, light, overlay);
            layer(model, pose, buffers, e, dmz(face + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
            layer(model, pose, buffers, e, dmz(face + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
            layer(model, pose, buffers, e, dmz(face + "3.png"), rgb(e.getHairColor()), pt, light, overlay);
        }
        renderFaceFeature(model, pose, buffers, e, "nose", e.getNoseType(), 6,
                faceRoot + raceRoot + "_nose_" + Math.floorMod(e.getNoseType(), 6) + ".png",
                body, pt, light, overlay);
        renderFaceFeature(model, pose, buffers, e, "mouth", e.getMouthType(), 9,
                faceRoot + raceRoot + "_mouth_" + Math.floorMod(e.getMouthType(), 9) + ".png",
                body, pt, light, overlay);
        if (e.getOutfit() >= 0) {
            renderOutfit(model, pose, buffers, e, HUMAN_OUTFITS[Math.floorMod(e.getOutfit(), HUMAN_OUTFITS.length)], pt, light, overlay);
        }
    }

    private static boolean shouldRenderHumanSaiyanHairBase(AmbientFighterEntity e) {
        if (e == null || !e.getRace().usesHairBase()) return false;
        var form = e.getActiveRacialFormConfig();
        if (form != null && !form.forcedHairCode().isBlank()) return true;
        if (e.getHairId() == 5) return false;
        CustomHair hair = HairManager.getPresetStyle(e.getHairId(), com.dragonminez.common.hair.HairStyleSlot.BASE);
        return hair != null && hair.getVisibleStrandCount() > 0;
    }

    /** Renders the Human portion of DMZ 2.2's combined racial/Human face index range. */
    private boolean renderBorrowedHumanEyes(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                            AmbientFighterEntity e, int value, int humanStart,
                                            float[] sclera, float[] faceColor,
                                            float pt, int light, int overlay) {
        int humanEye = value - humanStart;
        if (humanEye < 0 || humanEye >= 13) return false;
        String eye = HUMAN_FACE + "humansaiyan_eye_" + humanEye + "_";
        layer(model, pose, buffers, e, dmz(eye + "0.png"), sclera, pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "1.png"), rgb(e.getEye1Color()), pt, light, overlay);
        layer(model, pose, buffers, e, dmz(eye + "2.png"), rgb(e.getEye2Color()), pt, light, overlay);
        float[] brow = e.getHairId() > 0 ? rgb(e.getHairColor()) : darken(faceColor, 0.65F);
        layer(model, pose, buffers, e, dmz(eye + "3.png"), brow, pt, light, overlay);
        return true;
    }

    private void renderFaceFeature(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                   AmbientFighterEntity e, String type, int value, int humanStart,
                                   String racialPath, float[] tint,
                                   float pt, int light, int overlay) {
        int humanValue = value - humanStart;
        String path = humanValue >= 0
                ? HUMAN_FACE + "humansaiyan_" + type + "_" + humanValue + ".png"
                : racialPath;
        if (path != null) renderFaceTexture(model, pose, buffers, e, path, tint, pt, light, overlay);
    }

    /** DMZ 2.2 gives nose/mouth atlases optional translucent shadow companions. */
    private void renderFaceTexture(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                   AmbientFighterEntity e, String path, float[] tint,
                                   float pt, int light, int overlay) {
        layer(model, pose, buffers, e, dmz(path), tint, pt, light, overlay);
        layerWithNativeShadowOnly(model, pose, buffers, e, path, tint, pt, light, overlay);
    }

    private static float[] darken(float[] color, float factor) {
        return new float[]{color[0] * factor, color[1] * factor, color[2] * factor};
    }

    private void renderOutfit(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                              AmbientFighterEntity e, String outfit, float pt, int light, int overlay) {
        ItemStack dmzArmor = equippedDmzArmor(e);
        if (!dmzArmor.isEmpty() && dmzArmor.getItem() instanceof DbzArmorItem armor
                && "dragonminez".equals(armor.getModId())) {
            layer(model, pose, buffers, e,
                    ArmorTextureResolver.resolve(armor.getModId(), armor.getItemId(), EquipmentSlot.CHEST, dmzArmor),
                    WHITE, pt, light, overlay);
            layer(model, pose, buffers, e,
                    ArmorTextureResolver.resolve(armor.getModId(), armor.getItemId(), EquipmentSlot.LEGS, dmzArmor),
                    WHITE, pt, light, overlay);
            return;
        }
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer1.png"), WHITE, pt, light, overlay);
        layer(model, pose, buffers, e, dmz("textures/armor/" + outfit + "_layer2.png"), WHITE, pt, light, overlay);
    }

    private static ItemStack equippedDmzArmor(AmbientFighterEntity fighter) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HEAD}) {
            ItemStack stack = fighter.getItemBySlot(slot);
            if (stack.getItem() instanceof DbzArmorItem armor && "dragonminez".equals(armor.getModId())) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void layer(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                       AmbientFighterEntity entity, ResourceLocation texture, float[] rgb,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!faceTextureAllowed(entity, texture)) return;
        RenderType type = ModRenderTypes.skinOverlayCutout(texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, pose, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, rgb[0], rgb[1], rgb[2], 1.0F);
    }

    /** Mirrors DMZ 2.2's native racial-texture fallback to body type zero. */
    private void layerWithFallback(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                   AmbientFighterEntity entity, String selectedPath, String fallbackPath,
                                   float[] color, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation selected = dmz(selectedPath);
        ResourceLocation texture = textureExists(selected) ? selected : dmz(fallbackPath);
        if (textureExists(texture)) bodyLayer(model, pose, buffers, entity, texture, color,
                partialTick, packedLight, packedOverlay);
    }

    /** Renders the extra per-body-layer shading introduced by DMZ 2.2. */
    private void layerWithShadow(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                 AmbientFighterEntity entity, String selectedPath, String fallbackPath,
                                 float[] color, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation selected = dmz(selectedPath);
        ResourceLocation texture = textureExists(selected) ? selected : dmz(fallbackPath);
        if (!textureExists(texture)) return;
        bodyLayer(model, pose, buffers, entity, texture, color, partialTick, packedLight, packedOverlay);
        String path = texture.getPath();
        ResourceLocation shadow = dmz(path.substring(0, path.length() - ".png".length()) + "_shadow.png");
        if (textureExists(shadow)) nativeShadowLayer(model, pose, buffers, entity, shadow,
                ColorUtils.skinShadowTone(color), partialTick, packedLight, packedOverlay);
    }

    private void layerWithNativeShadow(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                       AmbientFighterEntity entity, String path, float[] color,
                                       float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation texture = dmz(path);
        if (!textureExists(texture)) return;
        layer(model, pose, buffers, entity, texture, color, partialTick, packedLight, packedOverlay);
        layerWithNativeShadowOnly(model, pose, buffers, entity, path, color,
                partialTick, packedLight, packedOverlay);
    }

    private void layerWithNativeShadowOnly(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                           AmbientFighterEntity entity, String path, float[] color,
                                           float partialTick, int packedLight, int packedOverlay) {
        if (!path.endsWith(".png")) return;
        ResourceLocation shadow = dmz(path.substring(0, path.length() - 4) + "_shadow.png");
        if (textureExists(shadow)) nativeShadowLayer(model, pose, buffers, entity, shadow,
                ColorUtils.skinShadowTone(color), partialTick, packedLight, packedOverlay);
    }

    private void optionalLayerWithShadow(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                         AmbientFighterEntity entity, String path, float[] color,
                                         float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation texture = dmz(path);
        if (textureExists(texture)) layerWithShadow(model, pose, buffers, entity, path, path, color,
                partialTick, packedLight, packedOverlay);
    }

    private void bodyLayer(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                           AmbientFighterEntity entity, ResourceLocation texture, float[] color,
                           float partialTick, int packedLight, int packedOverlay) {
        RenderType type = RenderType.entityCutoutNoCull(texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, pose, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, color[0], color[1], color[2], 1.0F);
    }

    private void nativeShadowLayer(BakedGeoModel model, PoseStack pose, MultiBufferSource buffers,
                                   AmbientFighterEntity entity, ResourceLocation texture, float[] color,
                                   float partialTick, int packedLight, int packedOverlay) {
        RenderType type = RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        getRenderer().reRender(model, pose, buffers, entity, type, consumer,
                partialTick, packedLight, packedOverlay, color[0], color[1], color[2], 1.0F);
    }

    private static boolean textureExists(ResourceLocation texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    private static float[] rgb(String hex) { return ColorUtils.hexToRgb(hex); }
    private static ResourceLocation dmz(String path) { return ResourceLocation.fromNamespaceAndPath("dragonminez", path); }
    private static ResourceLocation majinFace(String file) {
        return dmz("textures/entity/races/majin/faces/" + file);
    }

    /** Majins may borrow Human faces in DMZ 2.2, but never Janemba's transformation-only face. */
    private static boolean faceTextureAllowed(AmbientFighterEntity entity, ResourceLocation texture) {
        if (entity == null || texture == null
                || entity.getRace() != com.dmzlivingworld.entity.FighterRace.MAJIN) return true;
        String path = texture.getPath();
        if (!path.contains("/faces/")) return true;
        return "dragonminez".equals(texture.getNamespace())
                && (path.startsWith(MAJIN_FACE_ROOT) || path.startsWith(HUMAN_FACE))
                && !path.contains("janemba");
    }
}
