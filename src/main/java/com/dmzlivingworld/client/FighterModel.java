package com.dmzlivingworld.client;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.world.WorldMenaceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

/** Uses DragonMineZ's own race geometry with its native saga animation library. */
public final class FighterModel extends GeoModel<AmbientFighterEntity> {
    private static final Set<AmbientFighterEntity> DANCING_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> MOOD_POSE_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> HORN_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> GROUND_SIT_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> LIFE_POSE_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> MEDITATION_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> STARGAZING_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> WEAPON_TRAINING_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<AmbientFighterEntity, float[]> WEAPON_TRAINING_ITEM_GRIP = new WeakHashMap<>();
    private static final Set<AmbientFighterEntity> EATING_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> STUDY_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> SCOUT_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> SOCIAL_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> FLOWER_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> STRENGTH_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> NAP_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<AmbientFighterEntity> KI_TRAIN_LAST_FRAME = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<AmbientFighterEntity, Integer> IDLE_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> SIT_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> EATING_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> STUDY_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> SCOUT_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> SOCIAL_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> FLOWER_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> STRENGTH_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> NAP_POSE_ENTER = new WeakHashMap<>();
    private static final Map<AmbientFighterEntity, Integer> KI_TRAIN_POSE_ENTER = new WeakHashMap<>();
    private static final ResourceLocation HEROBRINE = lw("geo/entity/herobrine.geo.json");
    private static final ResourceLocation HEROBRINE_TEXTURE = new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    private static final ResourceLocation HUMAN = dmz("geo/entity/races/human.geo.json");
    private static final ResourceLocation HUMAN_SLIM = dmz("geo/entity/races/human_slim.geo.json");
    // DragonMineZ's actual female/buffed Human-Saiyan geometry contains the native
    // "boobas" chest bone. human_slim.geo.json is only a slim-arm model and does
    // not contain the female chest geometry, which made LW women read as slim men.
    private static final ResourceLocation HUMAN_FEMALE = dmz("geo/entity/races/hbuffed_fem.geo.json");
    private static final ResourceLocation FROST = dmz("geo/entity/races/frostdemon.geo.json");
    private static final ResourceLocation MAJIN = dmz("geo/entity/races/majin.geo.json");
    private static final ResourceLocation MAJIN_SLIM = dmz("geo/entity/races/majin_slim.geo.json");
    private static final ResourceLocation BIO = dmz("geo/entity/races/bioandroid.geo.json");
    private static final ResourceLocation BUFFED = dmz("geo/entity/races/hbuffed.geo.json");
    private static final ResourceLocation BUFFED_FEMALE = dmz("geo/entity/races/hbuffed_fem.geo.json");
    private static final ResourceLocation FROST_SECOND = dmz("geo/entity/races/frostdemon_second.geo.json");
    private static final ResourceLocation FROST_THIRD = dmz("geo/entity/races/frostdemon_third.geo.json");
    private static final ResourceLocation FROST_FP = dmz("geo/entity/races/frostdemon_fp.geo.json");
    private static final ResourceLocation FROST_FIFTH = dmz("geo/entity/races/frostdemon_fifth.geo.json");
    private static final ResourceLocation BIO_SEMI = dmz("geo/entity/races/bioandroid_semi.geo.json");
    private static final ResourceLocation BIO_PERFECT = dmz("geo/entity/races/bioandroid_perfect.geo.json");
    private static final ResourceLocation BIO_ULTRA = dmz("geo/entity/races/bioandroid_ultra.geo.json");
    private static final ResourceLocation BLANK = dmz("textures/armor/blank.png");
    private static final ResourceLocation SAGA_ANIMATIONS = dmz("animations/entity/sagas/saga_base.animation.json");
    // WeaponRegistry attack profiles in DMZ 2.1.3 reference the race-combat animation library.
    // Keep the saga library primary for all established LW locomotion/poses and expose combat only
    // as a GeckoLib fallback so equipped weapon clips can resolve without replacing old animations.
    private static final ResourceLocation COMBAT_ANIMATIONS = dmz("animations/entity/races/combat.animation.json");

    private static final String[] ARMOR_LAYER_BONES = {
            "hat_layer", "armorHead", "armorBody", "armorLeggingsBody", "body_layer",
            "armorRightArm", "right_arm_layer", "armorLeftArm", "left_arm_layer",
            "armorRightLeg", "armorRightBoot", "right_leg_layer",
            "armorLeftLeg", "armorLeftBoot", "left_leg_layer"
    };
    private static final String[] TAIL_BONES = {
            "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail9",
            "tail1m", "tail2m", "tail3m", "tail4m", "tail5m", "tail6m", "tail7m", "tail8m", "tail9m"
    };

    @Override
    public ResourceLocation getModelResource(AmbientFighterEntity entity) {
        if (WorldMenaceManager.isHerobrine(entity)) return HEROBRINE;
        var form = entity.getActiveRacialForm();
        if (form != null) {
            String model = form.modelKey();
            if ("buffed".equals(model)) return entity.isFemale() ? BUFFED_FEMALE : BUFFED;
            if ("frostdemon_second".equals(model)) return FROST_SECOND;
            if ("frostdemon_third".equals(model)) return FROST_THIRD;
            if ("frostdemon_fp".equals(model)) return FROST_FP;
            if ("frostdemon_fifth".equals(model)) return FROST_FIFTH;
            if ("bioandroid_semi".equals(model)) return BIO_SEMI;
            if ("bioandroid_perfect".equals(model)) return BIO_PERFECT;
            if ("bioandroid_ultra".equals(model)) return BIO_ULTRA;
            // DMZ resolves several player-only aliases (SSJ4/Namek/Majin) through its
            // player form renderer. Living World keeps native base geometry rather than
            // inventing a replacement model for those aliases.
        }
        return switch (entity.getRace()) {
            case HUMAN, SAIYAN -> entity.isFemale() ? HUMAN_FEMALE : HUMAN;
            case NAMEKIAN -> HUMAN;
            case MAJIN -> entity.isFemale() ? MAJIN_SLIM : MAJIN;
            case FROST_DEMON -> FROST;
            case BIO_ANDROID -> BIO;
        };
    }

    @Override
    public ResourceLocation getTextureResource(AmbientFighterEntity entity) {
        return WorldMenaceManager.isHerobrine(entity) ? HEROBRINE_TEXTURE : BLANK;
    }

    @Override
    public ResourceLocation getAnimationResource(AmbientFighterEntity entity) {
        return SAGA_ANIMATIONS;
    }

    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(AmbientFighterEntity entity) {
        return new ResourceLocation[]{COMBAT_ANIMATIONS};
    }

    /**
     * R33 render-boundary isolation. Dragon Mine Z players and Living World fighters intentionally
     * reuse the same DMZ race geometry resources, and GeckoLib's baked GeoBone objects are mutable.
     * A player combat frame can therefore leave arm/hand transforms on the shared baked model when
     * the following fighter frame does not animate every one of those axes.
     *
     * This hook runs from GeckoLib immediately before AnimationProcessor.tickAnimation. Restore the
     * currently registered model to its baked initial snapshot first, then clear the mutation flags so
     * only THIS fighter's controllers are considered changes for the upcoming frame. Do not touch
     * visibility here; FighterModel owns that later in setCustomAnimations.
     */
    @Override
    public void applyMolangQueries(AmbientFighterEntity entity, double animTime) {
        for (var bone : getAnimationProcessor().getRegisteredBones()) {
            var initial = bone.getInitialSnapshot();
            if (initial == null) continue;

            bone.setRotX(initial.getRotX());
            bone.setRotY(initial.getRotY());
            bone.setRotZ(initial.getRotZ());
            bone.setPosX(initial.getOffsetX());
            bone.setPosY(initial.getOffsetY());
            bone.setPosZ(initial.getOffsetZ());
            bone.setScaleX(initial.getScaleX());
            bone.setScaleY(initial.getScaleY());
            bone.setScaleZ(initial.getScaleZ());
            bone.resetStateChanges();
        }

        super.applyMolangQueries(entity, animTime);
    }

    @Override
    public void setCustomAnimations(AmbientFighterEntity entity, long instanceId,
                                    AnimationState<AmbientFighterEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);

        boolean hasArmor = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        for (String boneName : ARMOR_LAYER_BONES) setHiddenIfPresent(boneName, !hasArmor);

        // Living World no longer synthesizes a Saiyan tail from the body model.
        // DMZ's actual Saiyan tail is a separate race-parts render (tailenrolled +
        // character/status state), so procedural Saiyans stay tailless until that
        // exact native pipeline can be reused safely. Frost/Bio retain their race geometry.
        boolean hideTail = entity.getRace() == FighterRace.HUMAN
                || entity.getRace() == FighterRace.SAIYAN
                || entity.getRace() == FighterRace.MAJIN
                || entity.getRace() == FighterRace.NAMEKIAN;
        for (String boneName : TAIL_BONES) setHiddenIfPresent(boneName, hideTail);
        // DMZ names these configured Frost Demon options horns1..horns5. Every Frost model,
        // including transformed models, carries the same selectable top-level horn set.
        for (int i = 1; i <= 5; i++)
            setHiddenIfPresent("horns" + i, !entity.isFrostDemonPrimitive() || i != entity.getHeadBone() + 1);

        resetDancePoseAfterExit(entity);
        resetHornPoseAfterExit(entity);
        resetGroundSitPoseAfterExit(entity);
        resetLifePoseAfterExit(entity);
        resetMeditationPoseAfterExit(entity);
        resetStargazingPoseAfterExit(entity);
        resetWeaponTrainingPoseAfterExit(entity);
        resetEatingPoseAfterExit(entity);
        resetStudyPoseAfterExit(entity);
        resetScoutPoseAfterExit(entity);
        resetSocialPoseAfterExit(entity);
        resetFlowerPoseAfterExit(entity);
        resetStrengthPoseAfterExit(entity);
        resetNapPoseAfterExit(entity);
        resetKiTrainingPoseAfterExit(entity);
        if (FighterPortraitRenderState.isActive() && entity.isArchivedPortraitPreview()) {
            // Fallen People records are historical snapshots, not live actors. Neutralize every
            // inherited Gecko/ambient pose so the portrait remains still and unmistakably archived.
            resetFullBodyPose();
            return;
        }
        if (entity.isMeditating()) {
            MEDITATION_LAST_FRAME.add(entity);
            applyMeditationPose(entity);
        }
        if (entity.isStargazing()) {
            STARGAZING_LAST_FRAME.add(entity);
            applyStargazingPose(entity);
        }

        EntityModelData modelData = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (modelData != null && !entity.isStargazing()) {
            var head = this.getAnimationProcessor().getBone("head");
            if (head != null) {
                head.setRotX(modelData.headPitch() * ((float)Math.PI / 180F));
                head.setRotY(modelData.netHeadYaw() * ((float)Math.PI / 180F));
            }
        }
        if (entity.isStrengthTrainingPose()) {
            resetMoodPoseAfterExit(entity);
            STRENGTH_LAST_FRAME.add(entity);
            applyStrengthTrainingPose(entity);
        } else if (entity.isKiTrainingPose()) {
            resetMoodPoseAfterExit(entity);
            KI_TRAIN_LAST_FRAME.add(entity);
            applyKiTrainingPose(entity);
        } else if (entity.isNappingPose()) {
            resetMoodPoseAfterExit(entity);
            NAP_LAST_FRAME.add(entity);
            applyNapPose(entity);
        } else if (entity.isGroundSitting()) {
            resetMoodPoseAfterExit(entity);
            GROUND_SIT_LAST_FRAME.add(entity);
            applyGroundSitPose(entity);
        } else if (entity.isHornRallyPose()) {
            resetMoodPoseAfterExit(entity);
            HORN_LAST_FRAME.add(entity);
            applyHornRallyPose(entity);
        } else if (entity.isDancing()) {
            resetMoodPoseAfterExit(entity);
            DANCING_LAST_FRAME.add(entity);
            applyDancePose(entity);
        } else if (entity.isArmedTrainingPose()) {
            resetMoodPoseAfterExit(entity);
            WEAPON_TRAINING_LAST_FRAME.add(entity);
            stabilizeWeaponTrainingItemGrip(entity);
        } else if (entity.isTrainingPose()) {
            // Empty-handed practice is owned by AmbientFighterEntity's targetless training
            // controller, which reuses DMZ's native attack clips without creating fake combat.
            // Model-side life poses must stay out of the arm/torso bones while that clip runs.
            resetMoodPoseAfterExit(entity);
        } else if (entity.isEatingPose()) {
            resetMoodPoseAfterExit(entity);
            EATING_LAST_FRAME.add(entity);
            applyEatingPose(entity);
        } else if (entity.isStudyingPose()) {
            resetMoodPoseAfterExit(entity);
            STUDY_LAST_FRAME.add(entity);
            applyStudyingPose(entity);
        } else if (entity.isScoutingPose()) {
            resetMoodPoseAfterExit(entity);
            SCOUT_LAST_FRAME.add(entity);
            applyScoutingPose(entity);
        } else if (entity.isSocialGesturePose()) {
            resetMoodPoseAfterExit(entity);
            SOCIAL_LAST_FRAME.add(entity);
            applySocialGesturePose(entity);
        } else if (entity.isFlowerInspectPose()) {
            resetMoodPoseAfterExit(entity);
            FLOWER_LAST_FRAME.add(entity);
            applyFlowerInspectPose(entity);
        } else if (entity.isIdleStretching()) {
            resetMoodPoseAfterExit(entity);
            LIFE_POSE_LAST_FRAME.add(entity);
            applyIdleStretchPose(entity);
        } else if (!entity.isMeditating() && !entity.isStargazing() && !entity.isGroundSitting() && !entity.isTransforming()) {
            if (applyStrongMoodPose(entity)) MOOD_POSE_LAST_FRAME.add(entity);
            else resetMoodPoseAfterExit(entity);
        } else {
            resetMoodPoseAfterExit(entity);
        }
    }

    /**
     * Custom dance poses touch axes that the native idle animation does not always rewrite.
     * Reset those exact axes once when dancing ends so arms/legs can never remain frozen in
     * the final dance frame. The next normal GeckoLib frame owns them again immediately.
     */
    private void resetDancePoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isDancing() || !DANCING_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetMeditationPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isMeditating() || !MEDITATION_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetStargazingPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isStargazing() || !STARGAZING_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetWeaponTrainingPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isArmedTrainingPose()) return;
        WEAPON_TRAINING_ITEM_GRIP.remove(entity);
        if (!WEAPON_TRAINING_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    /**
     * DMZ weapon clips animate the item locator as well as the arm. GeckoLib resets that locator to
     * its zero pose for the one-shot controller's exit/start frame, so the blade visibly twists even
     * though the arm followed the correct slash. Preserve only the last meaningful item-locator
     * transform across those neutral reset frames. All arm/body choreography remains the exact DMZ
     * clip, and the cached grip is discarded immediately when armed Training ends.
     */
    private void stabilizeWeaponTrainingItemGrip(AmbientFighterEntity entity) {
        var bone = this.getAnimationProcessor().getBone("right_hand_item");
        if (bone == null) return;
        float px = bone.getPosX(), py = bone.getPosY(), pz = bone.getPosZ();
        float rx = bone.getRotX(), ry = bone.getRotY(), rz = bone.getRotZ();
        boolean neutralReset = Math.abs(px) < 0.0001F && Math.abs(py) < 0.0001F && Math.abs(pz) < 0.0001F
                && Math.abs(rx) < 0.0001F && Math.abs(ry) < 0.0001F && Math.abs(rz) < 0.0001F;
        float[] last = WEAPON_TRAINING_ITEM_GRIP.get(entity);
        if (neutralReset && last != null) {
            bone.setPosX(last[0]); bone.setPosY(last[1]); bone.setPosZ(last[2]);
            bone.setRotX(last[3]); bone.setRotY(last[4]); bone.setRotZ(last[5]);
        } else if (!neutralReset) {
            WEAPON_TRAINING_ITEM_GRIP.put(entity, new float[]{px, py, pz, rx, ry, rz});
        }
    }

    private void resetEatingPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isEatingPose()) return;
        EATING_POSE_ENTER.remove(entity);
        if (!EATING_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetStudyPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isStudyingPose()) return;
        STUDY_POSE_ENTER.remove(entity);
        if (!STUDY_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetScoutPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isScoutingPose()) return;
        SCOUT_POSE_ENTER.remove(entity);
        if (!SCOUT_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetSocialPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isSocialGesturePose()) return;
        SOCIAL_POSE_ENTER.remove(entity);
        if (!SOCIAL_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetFlowerPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isFlowerInspectPose()) return;
        FLOWER_POSE_ENTER.remove(entity);
        if (!FLOWER_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetStrengthPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isStrengthTrainingPose()) return;
        STRENGTH_POSE_ENTER.remove(entity);
        if (!STRENGTH_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetNapPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isNappingPose()) return;
        NAP_POSE_ENTER.remove(entity);
        if (!NAP_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void resetKiTrainingPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isKiTrainingPose()) return;
        KI_TRAIN_POSE_ENTER.remove(entity);
        if (!KI_TRAIN_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    /** Clears every transform owned by the long-form life poses on their first exit frame. */
    private void resetFullBodyPose() {
        String[] bones = {
                "root", "waist", "body", "chest", "head",
                "right_shoulder", "left_shoulder", "right_arm", "left_arm",
                "right_forearm", "left_forearm",
                "right_leg", "left_leg", "right_underleg", "left_underleg",
                "right_feet", "left_feet"
        };
        for (String boneName : bones) {
            var bone = this.getAnimationProcessor().getBone(boneName);
            if (bone == null) continue;
            bone.setPosX(0.0F); bone.setPosY(0.0F); bone.setPosZ(0.0F);
            bone.setRotX(0.0F); bone.setRotY(0.0F); bone.setRotZ(0.0F);
            bone.setScaleX(1.0F); bone.setScaleY(1.0F); bone.setScaleZ(1.0F);
        }
    }


    private void resetGroundSitPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isGroundSitting()) return;
        SIT_POSE_ENTER.remove(entity);
        if (!GROUND_SIT_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }


    /** Two ordinary-life sitting variants: a narrow relaxed sit and a visibly crossed-leg sit. */
    private void applyGroundSitPose(AmbientFighterEntity entity) {
        float blend = poseBlend(SIT_POSE_ENTER, entity, 14);
        float breathe = (float)Math.sin(entity.tickCount * 0.055F) * blend;
        var root = this.getAnimationProcessor().getBone("root");
        var waist = this.getAnimationProcessor().getBone("waist");
        var body = this.getAnimationProcessor().getBone("body");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (entity.getGroundSitVariant() == 1) {
            // True cross-legged sit: each leg crosses through the body's centre line rather than
            // being translated outward. The root is also lower so the body meets the block.
            if (root != null) { root.setPosY(-5.28F * blend); root.setPosZ(0.42F * blend); }
            if (waist != null) { waist.setRotX(rad(-5.0F * blend)); waist.setRotY(0.0F); }
            if (body != null) { body.setRotX(rad((2.5F + breathe * 0.6F) * blend)); body.setRotZ(rad(-0.8F * blend)); }
            if (rightArm != null) { rightArm.setRotX(rad(27.0F * blend)); rightArm.setRotY(rad(-8.0F * blend)); rightArm.setRotZ(rad(-12.0F * blend)); }
            if (leftArm != null) { leftArm.setRotX(rad(27.0F * blend)); leftArm.setRotY(rad(8.0F * blend)); leftArm.setRotZ(rad(12.0F * blend)); }
            if (rightLeg != null) { rightLeg.setRotX(rad(70.0F * blend)); rightLeg.setRotY(rad(-42.0F * blend)); rightLeg.setRotZ(rad(-52.0F * blend)); rightLeg.setPosX(-1.22F * blend); rightLeg.setPosY(1.34F * blend); rightLeg.setPosZ(1.02F * blend); }
            if (leftLeg != null) { leftLeg.setRotX(rad(70.0F * blend)); leftLeg.setRotY(rad(42.0F * blend)); leftLeg.setRotZ(rad(52.0F * blend)); leftLeg.setPosX(1.22F * blend); leftLeg.setPosY(1.34F * blend); leftLeg.setPosZ(1.02F * blend); }
        } else {
            if (root != null) { root.setPosY(-4.92F * blend); root.setPosZ(0.66F * blend); }
            if (waist != null) { waist.setRotX(rad(-7.0F * blend)); waist.setRotY(0.0F); }
            if (body != null) body.setRotX(rad((4.0F + breathe * 0.6F) * blend));
            if (rightArm != null) { rightArm.setRotX(rad(18.0F * blend)); rightArm.setRotY(rad(-5.0F * blend)); rightArm.setRotZ(rad(-9.0F * blend)); }
            if (leftArm != null) { leftArm.setRotX(rad(18.0F * blend)); leftArm.setRotY(rad(5.0F * blend)); leftArm.setRotZ(rad(9.0F * blend)); }
            if (rightLeg != null) { rightLeg.setRotX(rad(78.0F * blend)); rightLeg.setRotY(rad(-8.0F * blend)); rightLeg.setRotZ(rad(-6.0F * blend)); rightLeg.setPosX(-0.22F * blend); rightLeg.setPosY(1.20F * blend); rightLeg.setPosZ(1.00F * blend); }
            if (leftLeg != null) { leftLeg.setRotX(rad(78.0F * blend)); leftLeg.setRotY(rad(8.0F * blend)); leftLeg.setRotZ(rad(6.0F * blend)); leftLeg.setPosX(0.22F * blend); leftLeg.setPosY(1.20F * blend); leftLeg.setPosZ(1.00F * blend); }
        }
    }

    private void resetLifePoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isIdleStretching()) return;
        IDLE_POSE_ENTER.remove(entity);
        if (!LIFE_POSE_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    private void applyIdleStretchPose(AmbientFighterEntity entity) {
        // Entry takes roughly one second, avoiding the old instant snap. The pose itself stays
        // simple and anatomical so the native idle can take control cleanly afterwards.
        float blend = poseBlend(IDLE_POSE_ENTER, entity, 20);
        float breathe = (float)Math.sin(entity.tickCount * 0.065F);
        var waist = this.getAnimationProcessor().getBone("waist");
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (entity.getIdleStretchVariant() == 0) {
            // Whole-body overhead stretch. Positive torso X is the correct backward opening for this model.
            float arm = (151.0F + breathe * 3.0F) * blend;
            if (waist != null) waist.setRotX(rad((4.5F + breathe * 0.4F) * blend));
            if (body != null) body.setRotX(rad((9.0F + breathe * 0.7F) * blend));
            if (head != null) head.setRotX(rad((-5.0F - breathe * 0.5F) * blend));
            if (rightArm != null) { rightArm.setRotX(rad(arm)); rightArm.setRotY(rad(-4.0F * blend)); rightArm.setRotZ(rad(-5.0F * blend)); }
            if (leftArm != null) { leftArm.setRotX(rad(arm)); leftArm.setRotY(rad(4.0F * blend)); leftArm.setRotZ(rad(5.0F * blend)); }
            if (rightLeg != null) { rightLeg.setRotX(rad((-3.0F - breathe * 0.3F) * blend)); rightLeg.setRotZ(rad(-2.0F * blend)); }
            if (leftLeg != null) { leftLeg.setRotX(rad((-3.0F - breathe * 0.3F) * blend)); leftLeg.setRotZ(rad(2.0F * blend)); }
        } else {
            // Lateral stretch includes hip/leg counter-motion so the whole body bends as one chain.
            float lean = (-15.0F + breathe * 0.7F) * blend;
            if (waist != null) waist.setRotZ(rad(lean * 0.55F));
            if (body != null) { body.setRotZ(rad(lean)); body.setRotX(rad(1.5F * blend)); }
            if (head != null) head.setRotZ(rad(-lean * 0.28F));
            if (rightArm != null) { rightArm.setRotX(rad((151.0F + breathe * 2.5F) * blend)); rightArm.setRotY(rad(-5.0F * blend)); rightArm.setRotZ(rad(-12.0F * blend)); }
            if (leftArm != null) { leftArm.setRotX(rad(14.0F * blend)); leftArm.setRotY(rad(6.0F * blend)); leftArm.setRotZ(rad(24.0F * blend)); }
            if (rightLeg != null) { rightLeg.setRotZ(rad(-lean * 0.20F)); rightLeg.setRotX(rad(-2.0F * blend)); }
            if (leftLeg != null) { leftLeg.setRotZ(rad(lean * 0.12F)); leftLeg.setRotX(rad(2.0F * blend)); }
        }
    }

    private static float poseBlend(Map<AmbientFighterEntity, Integer> starts, AmbientFighterEntity entity, int ticks) {
        int start = starts.computeIfAbsent(entity, ignored -> entity.tickCount);
        float t = Math.max(0.0F, Math.min(1.0F, (entity.tickCount - start) / (float)Math.max(1, ticks)));
        return t * t * (3.0F - 2.0F * t);
    }

    private void resetHornPoseAfterExit(AmbientFighterEntity entity) {
        if (entity.isHornRallyPose() || !HORN_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    /** Holds the real off-hand Goat Horn up to the face while the rally is sounding. */
    private void applyHornRallyPose(AmbientFighterEntity entity) {
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        if (body != null) { body.setRotX(rad(-4.0F)); body.setRotY(rad(-8.0F)); body.setRotZ(rad(2.0F)); }
        if (head != null) { head.setRotX(rad(-8.0F)); head.setRotY(rad(7.0F)); head.setRotZ(rad(-3.0F)); }
        // Off-hand is the left arm: lift it hard toward the mouth so the rendered item follows it.
        if (leftArm != null) { leftArm.setRotX(rad(138.0F)); leftArm.setRotY(rad(18.0F)); leftArm.setRotZ(rad(32.0F)); }
        if (rightArm != null) { rightArm.setRotX(rad(22.0F)); rightArm.setRotY(rad(-8.0F)); rightArm.setRotZ(rad(-34.0F)); }
    }

    private void resetMoodPoseAfterExit(AmbientFighterEntity entity) {
        if (!MOOD_POSE_LAST_FRAME.remove(entity)) return;
        resetFullBodyPose();
    }

    /**
     * Reactive World moods use bounded absolute bone poses. RC2 added offsets to the
     * previous GeckoLib pose every render, which could accumulate and spin the torso.
     * These poses intentionally avoid body/waist accumulation and only take ownership
     * while the fighter is otherwise idle enough for an emote to make sense.
     */
    private boolean applyStrongMoodPose(AmbientFighterEntity entity) {
        if (entity.getTarget() != null || entity.isSocialPowerDisplay() || entity.isKaiokenActive() || entity.isFlying() || entity.isSprinting() || entity.getLocomotionMode() == com.dragonminez.common.init.entities.sagas.DBSagasEntity.LocomotionMode.RUN) return false;
        float strength = entity.getReactiveMoodStrength() / 100.0F;
        if (strength < 0.48F) return false;

        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");

        if (WorldMenaceManager.isHerobrine(entity)) {
            // Herobrine does not share the ordinary seven-emotion pantomime. A very still, slightly
            // lowered stare reads better than visibly acting "sad" or "upbeat".
            if (head != null) { head.setRotX(rad(5.0F)); head.setRotY(rad((float)Math.sin(entity.tickCount * 0.025F) * 2.5F)); }
            if (body != null) body.setRotX(rad(1.5F));
            return true;
        }

        int mood = entity.getReactiveMoodVisual();
        float t = entity.tickCount;
        float headX = 0.0F, headY = 0.0F, headZ = 0.0F;
        float bodyX = 0.0F, bodyY = 0.0F, bodyZ = 0.0F;
        float rightX = 0.0F, rightZ = 0.0F, leftX = 0.0F, leftZ = 0.0F;
        switch (mood) {
            case 0 -> { // Upbeat: open/alert, not a full emote.
                headX = -2.0F; headZ = -2.5F; bodyX = -1.0F; rightZ = -4.0F; leftZ = 4.0F;
            }
            case 1 -> { // Content
                headZ = 1.0F;
            }
            case 2 -> { // Focused: slight forward intent.
                headX = -3.0F; bodyX = -2.5F; rightX = -2.0F; leftX = -2.0F;
            }
            case 3 -> { // Wary: visibly scans rather than freezing in one awkward pose.
                headX = 2.0F; headY = (float)Math.sin(t * 0.055F) * 22.0F; bodyY = headY * 0.16F;
                rightZ = -2.5F; leftZ = 2.5F;
            }
            case 4 -> { // Irritated: tense shoulders + turned head; hands stay out of the "pockets" pose.
                headX = 2.5F; headY = -9.0F + (float)Math.sin(t * 0.035F) * 3.0F; headZ = -4.0F;
                bodyY = 5.0F; bodyX = -1.5F; rightX = -5.0F; leftX = -5.0F;
                rightZ = -3.0F; leftZ = 3.0F;
            }
            case 5 -> { // Somber: withdrawn and lowered, but never a forced lying pose.
                headX = 13.0F; headZ = 1.5F; bodyX = 3.0F; rightX = 2.0F; leftX = 2.0F;
            }
            case 6 -> { // Weary: drooped head/shoulders and a slow breathing sway.
                float breathe = (float)Math.sin(t * 0.055F);
                headX = 17.0F + breathe * 2.0F; headZ = -1.5F; bodyX = 5.0F + breathe * 0.8F;
                rightX = 7.0F; leftX = 7.0F; rightZ = 3.0F; leftZ = -3.0F;
            }
            default -> { return false; }
        }
        if (head != null) { head.setRotX(rad(headX * strength)); head.setRotY(rad(headY * strength)); head.setRotZ(rad(headZ * strength)); }
        if (body != null) { body.setRotX(rad(bodyX * strength)); body.setRotY(rad(bodyY * strength)); body.setRotZ(rad(bodyZ * strength)); }
        if (rightArm != null) { rightArm.setRotX(rad(rightX * strength)); rightArm.setRotZ(rad(rightZ * strength)); }
        if (leftArm != null) { leftArm.setRotX(rad(leftX * strength)); leftArm.setRotZ(rad(leftZ * strength)); }
        return true;
    }

    /**
     * RC5 dance: a planted-feet groove/emote rather than alternating walking limbs. The bounce,
     * shoulder sway, arm raises and brief inward "clap" accents make it read as dancing while
     * both legs bend together like a small knee bounce instead of taking steps.
     */
    private void applyDancePose(AmbientFighterEntity entity) {
        switch (entity.getDanceVariant()) {
            case 1 -> applyDiscoDance(entity);
            default -> applyGrooveDance(entity);
        }
    }

    /** Planted-feet bounce/groove retained from RC5 as variant one. */
    private void applyGrooveDance(AmbientFighterEntity entity) {
        float phase = entity.tickCount * 0.27F;
        float sway = (float)Math.sin(phase);
        float beat = Math.abs((float)Math.sin(phase * 1.5F));
        float halfBeat = (float)Math.sin(phase * 0.5F);
        float clap = (float)Math.pow(Math.max(0.0F, Math.sin(phase * 0.75F)), 6.0D);
        var root = this.getAnimationProcessor().getBone("root");
        var head = this.getAnimationProcessor().getBone("head");
        var body = this.getAnimationProcessor().getBone("body");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (root != null) { root.setPosY(0.10F + beat * 0.68F); root.setPosX(sway * 0.22F); }
        if (head != null) { head.setRotX(rad(-4.0F + beat * 7.0F)); head.setRotZ(rad(-sway * 9.0F)); }
        if (body != null) { body.setRotY(rad(sway * 15.0F)); body.setRotZ(rad(sway * 7.0F)); }
        float armLift = 58.0F + beat * 34.0F;
        float sideOpen = 48.0F - clap * 34.0F;
        if (rightArm != null) { rightArm.setRotX(rad(armLift + halfBeat * 12.0F)); rightArm.setRotY(rad(-12.0F - clap * 18.0F)); rightArm.setRotZ(rad(-sideOpen - sway * 10.0F)); }
        if (leftArm != null) { leftArm.setRotX(rad(armLift - halfBeat * 12.0F)); leftArm.setRotY(rad(12.0F + clap * 18.0F)); leftArm.setRotZ(rad(sideOpen - sway * 10.0F)); }
        float kneeBounce = 5.0F + beat * 12.0F;
        if (rightLeg != null) { rightLeg.setRotX(rad(kneeBounce)); rightLeg.setRotY(0.0F); rightLeg.setRotZ(rad(-3.0F - sway * 2.0F)); }
        if (leftLeg != null) { leftLeg.setRotX(rad(kneeBounce)); leftLeg.setRotY(0.0F); leftLeg.setRotZ(rad(3.0F - sway * 2.0F)); }
    }

    /** Disco: four-beat diagonal point pattern with deliberate hips/shoulders and planted bounce. */
    private void applyDiscoDance(AmbientFighterEntity entity) {
        float phase = entity.tickCount * 0.19F;
        float sway = (float)Math.sin(phase);
        float sway2 = (float)Math.sin(phase * 0.5F + 1.2F);
        float bounce = 0.5F + 0.5F * (float)Math.sin(phase * 2.0F - 0.8F);
        float point = (float)Math.sin(phase * 0.72F);
        var root = this.getAnimationProcessor().getBone("root");
        var head = this.getAnimationProcessor().getBone("head");
        var body = this.getAnimationProcessor().getBone("body");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (root != null) {
            root.setPosY(0.08F + bounce * 0.42F);
            root.setPosX(sway * 0.30F);
            root.setRotY(rad(sway * 7.0F));
        }
        if (body != null) { body.setRotY(rad(sway * 17.0F)); body.setRotZ(rad(-sway * 7.0F)); body.setRotX(rad(-2.0F + sway2 * 2.0F)); }
        if (head != null) { head.setRotY(rad(-sway * 11.0F)); head.setRotZ(rad(sway2 * 4.0F)); }
        // Continuous diagonal pointing: the arms trade emphasis through sine interpolation,
        // never snapping between hard sixteen-tick poses.
        float rightLift = 102.0F + point * 68.0F;
        float leftLift = 102.0F - point * 68.0F;
        float rightOpen = -42.0F - point * 20.0F;
        float leftOpen = 42.0F - point * 20.0F;
        if (rightArm != null) { rightArm.setRotX(rad(rightLift)); rightArm.setRotY(rad(-17.0F - point * 8.0F)); rightArm.setRotZ(rad(rightOpen)); }
        if (leftArm != null) { leftArm.setRotX(rad(leftLift)); leftArm.setRotY(rad(17.0F - point * 8.0F)); leftArm.setRotZ(rad(leftOpen)); }
        float knee = 7.0F + bounce * 13.0F;
        // Feet remain planted; both knees bounce together while the hips/shoulders carry the motion.
        if (rightLeg != null) { rightLeg.setRotX(rad(knee)); rightLeg.setRotY(rad(-2.0F)); rightLeg.setRotZ(rad(-4.0F - sway * 2.0F)); }
        if (leftLeg != null) { leftLeg.setRotX(rad(knee)); leftLeg.setRotY(rad(2.0F)); leftLeg.setRotZ(rad(4.0F - sway * 2.0F)); }
    }

    /**
     * Holds the real food prop at the mouth using DMZ's own base.eat arm/head keyframes. The
     * previous hand-authored 132-degree lift overshot the native hand pivot on several race models.
     */
    private void applyEatingPose(AmbientFighterEntity entity) {
        float blend = poseBlend(EATING_POSE_ENTER, entity, 9);
        float bite = 0.5F + 0.5F * (float)Math.sin(entity.tickCount * 0.50F);
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        // Bedrock X/Y rotations are sign-converted by GeckoLib. These are the converted values
        // from DMZ 2.1.3's two alternating base.eat frames, smoothly interpolated for chewing.
        if (head != null) {
            head.setRotX(rad((-2.5F - bite * 2.5F) * blend));
            head.setRotY(0.0F);
            head.setRotZ(0.0F);
        }
        if (rightArm != null) {
            rightArm.setRotX(rad((74.4869F + bite * 2.7240F) * blend));
            rightArm.setRotY(rad((16.0996F + bite * 1.2341F) * blend));
            rightArm.setRotZ(rad((3.3532F + bite * 5.5713F) * blend));
        }
        if (leftArm != null) {
            leftArm.setRotX(0.0F);
            leftArm.setRotY(0.0F);
            leftArm.setRotZ(0.0F);
        }
    }



    /**
     * A real seated reading pose for the STUDYING activity. The right-hand book remains a real
     * temporary item; this pose merely brings it into a believable reading position and adds slow
     * page-reading movement instead of leaving the NPC in a generic sitting pose.
     */
    private void applyStudyingPose(AmbientFighterEntity entity) {
        float blend = poseBlend(STUDY_POSE_ENTER, entity, 12);
        float page = (float)Math.sin(entity.tickCount * 0.085F);
        float breathe = (float)Math.sin(entity.tickCount * 0.045F);
        var root = this.getAnimationProcessor().getBone("root");
        var waist = this.getAnimationProcessor().getBone("waist");
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (root != null) { root.setPosY(-4.88F * blend); root.setPosZ(0.62F * blend); }
        if (waist != null) { waist.setRotX(rad(-8.0F * blend)); waist.setRotY(0.0F); waist.setRotZ(0.0F); }
        if (body != null) { body.setRotX(rad((10.0F + breathe * 0.8F) * blend)); body.setRotY(0.0F); body.setRotZ(0.0F); }
        if (head != null) { head.setRotX(rad((24.0F + page * 1.5F) * blend)); head.setRotZ(rad(page * 1.2F * blend)); }
        if (rightArm != null) {
            rightArm.setRotX(rad((54.0F + page * 2.5F) * blend));
            rightArm.setRotY(rad(-20.0F * blend));
            rightArm.setRotZ(rad(-31.0F * blend));
        }
        if (leftArm != null) {
            leftArm.setRotX(rad((49.0F - page * 2.0F) * blend));
            leftArm.setRotY(rad(19.0F * blend));
            leftArm.setRotZ(rad(29.0F * blend));
        }
        if (rightLeg != null) {
            rightLeg.setRotX(rad(78.0F * blend)); rightLeg.setRotY(rad(-7.0F * blend)); rightLeg.setRotZ(rad(-5.0F * blend));
            rightLeg.setPosX(-0.20F * blend); rightLeg.setPosY(1.18F * blend); rightLeg.setPosZ(1.0F * blend);
        }
        if (leftLeg != null) {
            leftLeg.setRotX(rad(78.0F * blend)); leftLeg.setRotY(rad(7.0F * blend)); leftLeg.setRotZ(rad(5.0F * blend));
            leftLeg.setPosX(0.20F * blend); leftLeg.setPosY(1.18F * blend); leftLeg.setPosZ(1.0F * blend);
        }
    }

    /** Real spyglass posture for SCOUTING; server LookControl still owns the direction being scanned. */
    private void applyScoutingPose(AmbientFighterEntity entity) {
        float blend = poseBlend(SCOUT_POSE_ENTER, entity, 9);
        float steady = (float)Math.sin(entity.tickCount * 0.045F);
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        if (body != null) { body.setRotX(rad(-3.0F * blend)); body.setRotY(0.0F); body.setRotZ(rad(steady * 0.7F * blend)); }
        if (head != null) head.setRotX(rad((-6.0F + steady * 0.8F) * blend));
        if (rightArm != null) {
            rightArm.setRotX(rad(132.0F * blend)); rightArm.setRotY(rad(10.0F * blend)); rightArm.setRotZ(rad(23.0F * blend));
        }
        if (leftArm != null) {
            leftArm.setRotX(rad(104.0F * blend)); leftArm.setRotY(rad(-18.0F * blend)); leftArm.setRotZ(rad(-29.0F * blend));
        }
    }

    /**
     * Small conversational gestures. The social manager swaps speaker/listener state on actual
     * dialogue beats, so this animation corresponds to a real conversation rather than an idle emote.
     */
    private void applySocialGesturePose(AmbientFighterEntity entity) {
        // Social scenes own the whole body. Reset first so a previous cross-legged/meditation
        // frame can never survive into Meeting Up/Talking through a bone Gecko's idle clip omits.
        resetFullBodyPose();
        float blend = poseBlend(SOCIAL_POSE_ENTER, entity, 7);
        float talk = (float)Math.sin(entity.tickCount * 0.18F);
        float slow = (float)Math.sin(entity.tickCount * 0.055F);
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        if (entity.isSocialSpeakerPose()) {
            if (body != null) { body.setRotX(rad(-2.0F * blend)); body.setRotY(0.0F); body.setRotZ(rad(slow * 1.5F * blend)); }
            if (head != null) head.setRotX(rad((-2.0F + talk * 1.4F) * blend));
            if (rightArm != null) {
                rightArm.setRotX(rad((25.0F + talk * 9.0F) * blend));
                rightArm.setRotY(rad(-8.0F * blend)); rightArm.setRotZ(rad((-29.0F - talk * 7.0F) * blend));
            }
            if (leftArm != null) {
                leftArm.setRotX(rad((8.0F - talk * 3.0F) * blend));
                leftArm.setRotY(rad(4.0F * blend)); leftArm.setRotZ(rad((8.0F + talk * 2.0F) * blend));
            }
        } else {
            float nod = Math.max(0.0F, (float)Math.sin(entity.tickCount * 0.10F));
            if (body != null) { body.setRotX(rad(1.5F * blend)); body.setRotY(0.0F); body.setRotZ(0.0F); }
            if (head != null) { head.setRotX(rad((3.0F + nod * 3.0F) * blend)); head.setRotZ(rad(slow * 0.8F * blend)); }
            if (rightArm != null) { rightArm.setRotX(rad(7.0F * blend)); rightArm.setRotY(0.0F); rightArm.setRotZ(rad(-5.0F * blend)); }
            if (leftArm != null) { leftArm.setRotX(rad(18.0F * blend)); leftArm.setRotY(rad(4.0F * blend)); leftArm.setRotZ(rad(14.0F * blend)); }
        }
    }

    /** Upright flower inspection. R14 deliberately removes the old bend/crouch completely. */
    private void applyFlowerInspectPose(AmbientFighterEntity entity) {
        float blend = poseBlend(FLOWER_POSE_ENTER, entity, 8);
        float inspect = (float)Math.sin(entity.tickCount * 0.07F);
        var root = this.getAnimationProcessor().getBone("root");
        var waist = this.getAnimationProcessor().getBone("waist");
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (root != null) { root.setPosY(0.0F); root.setRotX(0.0F); root.setRotY(0.0F); root.setRotZ(0.0F); }
        if (waist != null) { waist.setRotX(0.0F); waist.setRotY(0.0F); waist.setRotZ(0.0F); }
        if (body != null) { body.setRotX(0.0F); body.setRotY(0.0F); body.setRotZ(0.0F); }
        if (head != null) head.setRotX(rad((8.0F + inspect * 1.2F) * blend));
        if (rightArm != null) { rightArm.setRotX(rad((16.0F + inspect * 2.0F) * blend)); rightArm.setRotY(0.0F); rightArm.setRotZ(rad(-8.0F * blend)); }
        if (leftArm != null) { leftArm.setRotX(rad(5.0F * blend)); leftArm.setRotY(0.0F); leftArm.setRotZ(rad(4.0F * blend)); }
        if (rightLeg != null) { rightLeg.setRotX(0.0F); rightLeg.setRotY(0.0F); rightLeg.setRotZ(0.0F); }
        if (leftLeg != null) { leftLeg.setRotX(0.0F); leftLeg.setRotY(0.0F); leftLeg.setRotZ(0.0F); }
    }

    /** Push-up-only strength drill. No squat/lunge/sit-up variants remain in active presentation. */
    private void applyStrengthTrainingPose(AmbientFighterEntity entity) {
        float blend = poseBlend(STRENGTH_POSE_ENTER, entity, 10);
        float rep = Math.floorMod(entity.tickCount, 120) / 120.0F;
        float depth;
        if (rep < 0.16F) depth = 0.0F;
        else if (rep < 0.39F) { float t=(rep-0.16F)/0.23F; depth=t*t*(3.0F-2.0F*t); }
        else if (rep < 0.61F) depth = 1.0F;
        else if (rep < 0.84F) { float t=(rep-0.61F)/0.23F; float e=t*t*(3.0F-2.0F*t); depth=1.0F-e; }
        else depth = 0.0F;

        var root = this.getAnimationProcessor().getBone("root");
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightFore = this.getAnimationProcessor().getBone("right_forearm");
        var leftFore = this.getAnimationProcessor().getBone("left_forearm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        var rightUnder = this.getAnimationProcessor().getBone("right_underleg");
        var leftUnder = this.getAnimationProcessor().getBone("left_underleg");
        resetFullBodyPose();

        // Keep the whole body in one rigid push-up line while the elbows own the repetition.
        // This avoids every previous lower-body/marching artifact from the removed standing drills.
        float d = depth * blend;
        if (root != null) { root.setPosY(0.0F); root.setPosZ(0.0F); root.setRotX(0.0F); root.setRotY(0.0F); root.setRotZ(0.0F); }
        if (body != null) body.setRotX(rad((-2.0F-2.0F*d)*blend));
        if (head != null) head.setRotX(rad((-8.0F+3.0F*d)*blend));
        if (rightArm != null) { rightArm.setRotX(rad((76.0F+13.0F*d)*blend)); rightArm.setRotY(rad(-4.0F*blend)); rightArm.setRotZ(rad(-20.0F*blend)); }
        if (leftArm != null) { leftArm.setRotX(rad((76.0F+13.0F*d)*blend)); leftArm.setRotY(rad(4.0F*blend)); leftArm.setRotZ(rad(20.0F*blend)); }
        if (rightFore != null) rightFore.setRotX(rad((-6.0F-70.0F*d)*blend));
        if (leftFore != null) leftFore.setRotX(rad((-6.0F-70.0F*d)*blend));
        if (rightLeg != null) { rightLeg.setRotX(rad(-5.0F*blend)); rightLeg.setRotZ(rad(-3.0F*blend)); }
        if (leftLeg != null) { leftLeg.setRotX(rad(-5.0F*blend)); leftLeg.setRotZ(rad(3.0F*blend)); }
        if (rightUnder != null) rightUnder.setRotX(0.0F);
        if (leftUnder != null) leftUnder.setRotX(0.0F);
    }

    /** Controlled Ki drill: gather at the core, compress, then release/shape the energy. */
    private void applyKiTrainingPose(AmbientFighterEntity entity) {
        if (entity.getKiTrainingVariant() == 1) { applyKiControlOrbPose(entity); return; }
        float blend = poseBlend(KI_TRAIN_POSE_ENTER, entity, 9);
        float phase = Math.floorMod(entity.tickCount, 140) / 140.0F;
        float charge = phase < (95.0F / 140.0F) ? Math.min(1.0F, phase / 0.25F) : Math.max(0.0F, 1.0F - (phase - 95.0F / 140.0F) / 0.32F);
        float pulse = (float)Math.sin(entity.tickCount * 0.11F);
        boolean release = phase >= (95.0F / 140.0F);
        var root = this.getAnimationProcessor().getBone("root");
        var waist = this.getAnimationProcessor().getBone("waist");
        var body = this.getAnimationProcessor().getBone("body");
        var head = this.getAnimationProcessor().getBone("head");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (root != null) { root.setPosX(0.0F); root.setPosY(-0.45F * charge * blend); root.setPosZ(0.0F); root.setRotX(0.0F); root.setRotY(0.0F); root.setRotZ(0.0F); }
        if (waist != null) { waist.setRotX(rad(-4.0F * charge * blend)); waist.setRotY(0.0F); waist.setRotZ(0.0F); }
        if (body != null) { body.setRotX(rad((4.0F + pulse * 1.2F) * charge * blend)); body.setRotY(0.0F); body.setRotZ(0.0F); }
        if (head != null) { head.setRotX(rad((-4.0F + pulse * 1.0F) * charge * blend)); head.setRotY(0.0F); head.setRotZ(0.0F); }
        float armX = release ? 76.0F : 42.0F + 18.0F * charge;
        float armY = release ? 16.0F : 28.0F * charge;
        float armZ = release ? 10.0F : 30.0F - 10.0F * charge;
        if (rightArm != null) { rightArm.setPosX(0.0F); rightArm.setPosY(0.0F); rightArm.setPosZ(0.0F); rightArm.setRotX(rad(armX * blend)); rightArm.setRotY(rad(-armY * blend)); rightArm.setRotZ(rad(-armZ * blend)); }
        if (leftArm != null) { leftArm.setPosX(0.0F); leftArm.setPosY(0.0F); leftArm.setPosZ(0.0F); leftArm.setRotX(rad(armX * blend)); leftArm.setRotY(rad(armY * blend)); leftArm.setRotZ(rad(armZ * blend)); }
        // Stable planted stance so no walk cycle leaks through while the aura controller runs.
        if (rightLeg != null) { rightLeg.setPosX(-0.26F * blend); rightLeg.setPosY(0.0F); rightLeg.setPosZ(0.0F); rightLeg.setRotX(rad(8.0F * charge * blend)); rightLeg.setRotY(0.0F); rightLeg.setRotZ(rad(-5.0F * blend)); }
        if (leftLeg != null) { leftLeg.setPosX(0.26F * blend); leftLeg.setPosY(0.0F); leftLeg.setPosZ(0.0F); leftLeg.setRotX(rad(8.0F * charge * blend)); leftLeg.setRotY(0.0F); leftLeg.setRotZ(rad(5.0F * blend)); }
    }

    /** Slow Ki-control posture: hands cup one growing ball and never release it as an attack. */
    private void applyKiControlOrbPose(AmbientFighterEntity entity) {
        float blend = poseBlend(KI_TRAIN_POSE_ENTER, entity, 9);
        float phase = Math.floorMod(entity.tickCount, 360) / 360.0F;
        float gather = phase < 0.72F ? Math.min(1.0F, phase / 0.72F) : phase < 0.92F ? 1.0F : Math.max(0.0F, 1.0F-(phase-0.92F)/0.08F);
        float breathe=(float)Math.sin(entity.tickCount*0.055F);
        var root=this.getAnimationProcessor().getBone("root"); var waist=this.getAnimationProcessor().getBone("waist");
        var body=this.getAnimationProcessor().getBone("body"); var head=this.getAnimationProcessor().getBone("head");
        var ra=this.getAnimationProcessor().getBone("right_arm"); var la=this.getAnimationProcessor().getBone("left_arm");
        var rf=this.getAnimationProcessor().getBone("right_forearm"); var lf=this.getAnimationProcessor().getBone("left_forearm");
        var rl=this.getAnimationProcessor().getBone("right_leg"); var ll=this.getAnimationProcessor().getBone("left_leg");
        resetFullBodyPose();
        if(root!=null) root.setPosY((-0.25F-0.30F*gather)*blend);
        if(waist!=null) waist.setRotX(rad(-3.0F*gather*blend));
        if(body!=null) body.setRotX(rad((3.0F+breathe*0.6F)*blend));
        if(head!=null) head.setRotX(rad((-6.0F-2.0F*gather)*blend));
        if(ra!=null){ra.setRotX(rad((48.0F+8.0F*gather)*blend));ra.setRotY(rad(-32.0F*blend));ra.setRotZ(rad(-26.0F*blend));}
        if(la!=null){la.setRotX(rad((48.0F+8.0F*gather)*blend));la.setRotY(rad(32.0F*blend));la.setRotZ(rad(26.0F*blend));}
        if(rf!=null) rf.setRotX(rad((-54.0F+8.0F*gather)*blend));
        if(lf!=null) lf.setRotX(rad((-54.0F+8.0F*gather)*blend));
        if(rl!=null){rl.setPosX(-0.28F*blend);rl.setRotZ(rad(-5.0F*blend));}
        if(ll!=null){ll.setPosX(0.28F*blend);ll.setRotZ(rad(5.0F*blend));}
    }

    /** Relaxed lying pose; FighterRenderer rotates the whole fighter onto their back. */
    private void applyNapPose(AmbientFighterEntity entity) {
        float blend = poseBlend(NAP_POSE_ENTER, entity, 10);
        float breathe = (float)Math.sin(entity.tickCount * 0.055F);
        var head = this.getAnimationProcessor().getBone("head");
        var body = this.getAnimationProcessor().getBone("body");
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (head != null) { head.setRotX(rad(2.0F * blend)); head.setRotY(rad(-3.0F * blend)); head.setRotZ(0.0F); }
        if (body != null) body.setRotX(rad(breathe * 0.8F * blend));
        if (rightArm != null) { rightArm.setRotX(rad(12.0F * blend)); rightArm.setRotY(rad(-4.0F * blend)); rightArm.setRotZ(rad(-15.0F * blend)); }
        if (leftArm != null) { leftArm.setRotX(rad(16.0F * blend)); leftArm.setRotY(rad(5.0F * blend)); leftArm.setRotZ(rad(17.0F * blend)); }
        if (rightLeg != null) { rightLeg.setRotX(rad(7.0F * blend)); rightLeg.setRotZ(rad(-4.0F * blend)); }
        if (leftLeg != null) { leftLeg.setRotX(rad(18.0F * blend)); leftLeg.setRotZ(rad(5.0F * blend)); }
    }


    /** Stable skyward head/body pose. A dedicated state prevents LookControl interpolation from wobbling the head. */
    private void applyStargazingPose(AmbientFighterEntity entity) {
        var head = this.getAnimationProcessor().getBone("head");
        if (head != null) {
            head.setRotY(0.0F);
            head.setRotZ(0.0F);
            // The whole model is rotated onto its back by FighterRenderer when lying, so neutral head = skyward.
            head.setRotX(entity.isStargazingLying() ? 0.0F : rad(85.0F));
        }
        if (entity.isStargazingLying()) {
            var waist = this.getAnimationProcessor().getBone("waist");
            if (waist != null) waist.setRotX(0.0F);
            var rightArm = this.getAnimationProcessor().getBone("right_arm");
            if (rightArm != null) { rightArm.setRotX(rad(7.0F)); rightArm.setRotY(0.0F); rightArm.setRotZ(rad(-7.0F)); }
            var leftArm = this.getAnimationProcessor().getBone("left_arm");
            if (leftArm != null) { leftArm.setRotX(rad(7.0F)); leftArm.setRotY(0.0F); leftArm.setRotZ(rad(7.0F)); }
            var rightLeg = this.getAnimationProcessor().getBone("right_leg");
            if (rightLeg != null) { rightLeg.setRotX(0.0F); rightLeg.setRotY(0.0F); rightLeg.setRotZ(rad(-3.0F)); }
            var leftLeg = this.getAnimationProcessor().getBone("left_leg");
            if (leftLeg != null) { leftLeg.setRotX(0.0F); leftLeg.setRotY(0.0F); leftLeg.setRotZ(rad(3.0F)); }
        }
    }

    /** Exact pose values from DMZ 2.1.3's base.meditation animation, converted the same way GeckoLib converts Bedrock rotation channels. */
    private void applyMeditationPose(AmbientFighterEntity entity) {
        // base.meditation uses sin/cos(query.anim_time * 90). Molang trig is degree based,
        // so this is the same four-second cycle driven by entity time rather than wall-clock time.
        float t = entity.tickCount / 20.0F;
        float cycle = (float)Math.cos(t * Math.PI * 0.5D);
        var root = this.getAnimationProcessor().getBone("root");
        if (root != null) root.setPosY((float)Math.sin(t * Math.PI * 0.5D) * 0.7F - 0.4F);
        var waist = this.getAnimationProcessor().getBone("waist");
        if (waist != null) waist.setRotX(rad(-5.0F));
        var rightArm = this.getAnimationProcessor().getBone("right_arm");
        if (rightArm != null) { rightArm.setRotX(rad(40.76924F)); rightArm.setRotY(rad(29.98661F)); rightArm.setRotZ(rad(-23.4933F - 0.8F * cycle)); }
        var leftArm = this.getAnimationProcessor().getBone("left_arm");
        if (leftArm != null) { leftArm.setRotX(rad(33.64517F)); leftArm.setRotY(rad(-35.69025F)); leftArm.setRotZ(rad(23.28706F + 0.8F * cycle)); }
        var rightLeg = this.getAnimationProcessor().getBone("right_leg");
        if (rightLeg != null) {
            rightLeg.setRotX(rad(60.63603F)); rightLeg.setRotY(rad(31.4031F)); rightLeg.setRotZ(rad(-21.10915F));
            rightLeg.setPosX(-3.0F); rightLeg.setPosY(1.25F); rightLeg.setPosZ(0.5F);
        }
        var leftLeg = this.getAnimationProcessor().getBone("left_leg");
        if (leftLeg != null) {
            leftLeg.setRotX(rad(56.52347F)); leftLeg.setRotY(rad(-34.8921F)); leftLeg.setRotZ(rad(26.16981F));
            leftLeg.setPosX(3.25F); leftLeg.setPosY(0.75F); leftLeg.setPosZ(1.0F);
        }
    }

    private static float rad(float degrees) { return degrees * ((float)Math.PI / 180.0F); }

    private void setHiddenIfPresent(String boneName, boolean hidden) {
        var bone = this.getAnimationProcessor().getBone(boneName);
        if (bone != null) bone.setHidden(hidden);
    }

    private static ResourceLocation dmz(String path) {
        return new ResourceLocation("dragonminez", path);
    }

    private static ResourceLocation lw(String path) {
        return new ResourceLocation("dmzlivingworld", path);
    }
}
