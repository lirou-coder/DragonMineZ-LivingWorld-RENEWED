package com.dmzlivingworld.entity;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.dragonminez.common.init.entities.IBattlePower;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.MainDamageTypes;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.sagas.helper.DBSagasAnimations;
import com.dragonminez.common.init.entities.sagas.helper.DBSagasAnimationHandler;
import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.character.Character;
import com.dmzlivingworld.world.AmbientFighterSpawner;
import com.dmzlivingworld.world.FighterCombatDirector;
import com.dmzlivingworld.world.FighterAmbientActivityManager;
import com.dmzlivingworld.world.FighterBattleGrowthManager;
import com.dmzlivingworld.world.FighterDefeatRewardManager;
import com.dmzlivingworld.world.DialogueLocalityManager;
import com.dmzlivingworld.world.FighterEnvironmentManager;
import com.dmzlivingworld.world.FighterSpecialItemManager;
import com.dmzlivingworld.world.FighterScientistManager;
import com.dmzlivingworld.world.RedRibbonExperimentManager;
import com.dmzlivingworld.world.FighterLifeNeedsManager;
import com.dmzlivingworld.world.FighterLivelinessManager;
import com.dmzlivingworld.world.FighterAftermathManager;
import com.dmzlivingworld.world.FighterPassiveSkillManager;
import com.dmzlivingworld.world.FighterFullPowerManager;
import com.dmzlivingworld.world.FighterMemoryManager;
import com.dmzlivingworld.world.FighterArsenalManager;
import com.dmzlivingworld.world.FighterLegacyManager;
import com.dmzlivingworld.world.FighterGoalManager;
import com.dmzlivingworld.world.FighterInspectionManager;
import com.dmzlivingworld.world.FighterIntentManager;
import com.dmzlivingworld.world.FighterNpcSocialManager;
import com.dmzlivingworld.world.FighterPromotionManager;
import com.dmzlivingworld.world.FighterTechniqueManager;
import com.dmzlivingworld.world.FactionManager;
import com.dmzlivingworld.world.FactionRequestManager;
import com.dmzlivingworld.world.FactionRole;
import com.dmzlivingworld.world.FactionStructure;
import com.dmzlivingworld.world.FactionWorldData;
import com.dmzlivingworld.world.WorldFaction;
import com.dmzlivingworld.world.WantedManager;
import com.dmzlivingworld.world.WorldPowerScaler;
import com.dmzlivingworld.world.FighterPowerStatScaler;
import com.dmzlivingworld.world.BattlePowerFormula;
import com.dmzlivingworld.world.NpcDefenseCalculator;
import com.dmzlivingworld.world.NpcDefensePenetrationManager;
import com.dmzlivingworld.world.NpcFormConfigBridge;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.ReactiveWorldEventManager;
import com.dmzlivingworld.world.ReactiveWorldManager;
import com.dmzlivingworld.world.ReactiveMoodBehaviorManager;
import com.dmzlivingworld.world.ReactiveInteractionManager;
import com.dmzlivingworld.world.SparManager;
import com.dmzlivingworld.world.LivingBondManager;
import com.dmzlivingworld.world.MercyManager;
import com.dmzlivingworld.world.PeacekeeperManager;
import com.dmzlivingworld.world.PlayerCreationSafety;
import com.dmzlivingworld.world.OrganicThreatManager;
import com.dmzlivingworld.world.ReactiveFusionManager;
import com.dmzlivingworld.world.SanctionedMatchGuard;
import com.dmzlivingworld.config.LivingWorldConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Generic Earth fighter driven by DragonMineZ's DBSagasEntity combat brain.
 *
 * Living World supplies identity and high-level intent only. Once combat starts,
 * chasing, melee, dashing, aerial pursuit and Ki execution stay native to DMZ.
 */
public final class AmbientFighterEntity extends DBSagasEntity {
    private static final int DATA_VERSION = 23;
    private static final int MAX_DIALOGUE_HISTORY = 50;
    private static final int MAX_DIALOGUE_LENGTH = 240;
    private static final int TARGET_SCAN_INTERVAL = 20;
    private static final int SPECTATE_SCAN_INTERVAL = 40;
    private static final int RECOVERY_GRACE_TICKS = 100;
    /**
     * The canonical, non-temporary BP for this fighter. Native DMZ BP is a synced display
     * value, so forms, fruit and social flares must never become the source of permanent growth.
     */
    private static final String PERMANENT_BATTLE_POWER = "LWPermanentBattlePower";
    /** Highest real BP reached through a permanent organic gain; faction maintenance respects it. */
    private static final String EARNED_BATTLE_POWER_FLOOR = "LWEarnedBattlePowerFloor";
    private static final String POWER_COMPARE_RESTORE_BP = "LWPowerCompareRestoreBP";
    private static final String POWER_COMPARE_RESTORE_AT = "LWPowerCompareRestoreAt";

    private static final EntityDataAccessor<Boolean> READY =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEAD_SOUL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ALIGNMENT =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PERSONALITY =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RACE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ARCHETYPE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CAPTIVE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> SPEECH =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DEFEATED =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> FIGHTER_NAME =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LEGACY_TITLE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> AWAKENED =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DISPLAY_SCALE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> FACTION_ID =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FACTION_LEADER =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FACTION_ROLE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> FACTION_NAME =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FACTION_TITLE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> AURA_FLARED =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NON_COMBATANT =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> WANTED_ID =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WANTED_LEVEL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> WANTED_CRIME =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> MEDITATING =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MEDITATION_BOND_TIER =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MEDITATION_PLAYER_ID =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> MEDITATION_CIRCLE_MEMBER =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> KAIOKEN_LEVEL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STORY_ROLE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLIGHT_UNLOCKED =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RACIAL_SKILL_LEVEL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_RACIAL_FORM_LEVEL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMBIENT_POSE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEAPON_TRAINING_STRIKE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    /** Weapon-profile choreography used for real armed melee; separate from unarmed DMZ combos. */
    private static final EntityDataAccessor<Integer> WEAPON_COMBAT_STRIKE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    /** Harmless targetless practice beat. Kept separate from DMZ combat combo state. */
    private static final EntityDataAccessor<Integer> UNARMED_TRAINING_STRIKE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COSMETIC_ACCESSORY =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FISHING_ACTIVITY =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FISHING_BOBBER_X =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FISHING_BOBBER_Y =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FISHING_BOBBER_Z =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Integer> GENDER =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY_TYPE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EYES_TYPE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NOSE_TYPE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOUTH_TYPE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HEAD_BONE =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HAIR_ID =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OUTFIT =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> BODY_COLOR =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BODY_COLOR2 =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BODY_COLOR3 =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HAIR_COLOR =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> EYE1_COLOR =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> EYE2_COLOR =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> REACTIVE_MOOD_VISUAL =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REACTIVE_MOOD_STRENGTH =
            SynchedEntityData.defineId(AmbientFighterEntity.class, EntityDataSerializers.INT);

    private static final String[] SKIN_COLORS = {
            "#F5C9A6", "#E9B18D", "#D99A72", "#C17B58", "#9C6045", "#754531"
    };
    private static final String[] HAIR_COLORS = {
            "#151515", "#2A1C17", "#4B2A1A", "#70482A", "#B77A31", "#D9B75A", "#243A67", "#542B57"
    };
    private static final String[] EYE_COLORS = {
            "#2B211B", "#503522", "#6B4A2B", "#3E5F43", "#355C7D", "#566B85", "#6B456E"
    };
    private static final String[] NAMEK_GREEN = {"#1FAA24", "#2FB43A", "#168B24", "#49C451"};
    private static final String[] NAMEK_LIGHT_GREEN = {"#8FEA72", "#A6F28A", "#72D968", "#B5F39A"};
    private static final String[] NAMEK_ACCENT = {"#BB2024", "#D13835", "#8F1A25", "#D85B42"};
    private static final String[] NAMEK_PINK = {"#FF86A6", "#EA6F96", "#FF9DB7"};
    private static final String[] MAJIN_EYE_RED = {"#D71920", "#FF3038", "#A80F18", "#E84B50"};
    private static final String[] FROST_MAIN = {"#FFFFFF", "#E9EEFF", "#ECE4F5", "#DDEEFF"};
    private static final String[] FROST_SECOND = {"#E8A2FF", "#B99CFF", "#88C8FF", "#F0B0E6"};
    private static final String[] FROST_ACCENT = {"#FF39A9", "#7E59FF", "#44BCEB", "#D92774"};
    private static final String[] BIO_MAIN = {"#187600", "#247E16", "#326B24", "#0D6446"};
    private static final String[] BIO_SECOND = {"#9FE321", "#7FCF27", "#B1D54E", "#5EBF69"};
    private static final String[] BIO_ACCENT = {"#FF7600", "#E84C20", "#E9BD25", "#C84639"};

    private boolean combatConfigured;
    private int defeatedTicks;
    private int recoveryGraceTicks;
    private int retreatTicks;
    private UUID retreatThreatId;
    private UUID duelOpponentId;
    // Sanctioned player spars are explicit, non-lethal and isolated from faction-war logic.
    private boolean sanctionedMatchParticipant;
    private UUID sanctionedOpponentId;
    private UUID postSparOpponentId;
    private int postSparPeaceTicks;
    private int postSparIncomingGraceTicks;
    private int speechTicks;
    private final List<String> dialogueHistory = new ArrayList<>(MAX_DIALOGUE_HISTORY);
    private int cinematicLaunchCooldown;
    private int awakeningTicks;
    private UUID memoryOwnerId;
    private UUID memoryRecordId;
    private int memoryEncounters;
    private int memoryRelationship;
    private boolean memoryRescued;
    private UUID partyId;
    private boolean partyCaptain;
    private long lastFactionRepHitTick = Long.MIN_VALUE;
    private int auraFlareTicks;
    private UUID lastAuraTargetId;
    private boolean angerAuraUsed;
    private int foodSupplies;
    private int senzuBeans;
    private int supplyCooldown;
    private boolean regionalPresence;
    private int factionMerit;
    private int meditationTicks;
    private int meditationSessionLength;
    private int meditationCooldown;
    private UUID meditationPartnerPlayer;
    // True only when a player joined an already-running solo meditation. In that case the
    // player's departure detaches the social partner but never owns/ends the NPC's session.
    private boolean meditationPlayerJoinedExistingSolo;
    private UUID meditationPartnerNpc;
    private UUID meditationApproachPlayer;
    private int meditationApproachTicks;
    private boolean meditationAnchorSet;
    private double meditationAnchorX;
    private double meditationAnchorZ;
    private boolean meditationCircleActive;
    private double meditationCircleX;
    private double meditationCircleY;
    private double meditationCircleZ;
    private long nextMeditationDialogueTick;
    private boolean socialLifeActivity;
    private boolean ambientFlightActivity;
    private Vec3 flightWaypoint = Vec3.ZERO;
    private Vec3 flightWaypointDestination = Vec3.ZERO;
    private int flightWaypointTicks;
    private Vec3 clientFishingBobberPrev = Vec3.ZERO;
    private Vec3 clientFishingBobberCurrent = Vec3.ZERO;
    private boolean clientFishingBobberInitialized;
    private boolean socialPowerDisplay;
    private boolean socialPlayerApproach;
    private int trainingSessions;
    private String rivalName = "";
    private boolean kaiokenPotential;
    private int kaiokenTicks;
    private int kaiokenBasePower;
    private double kaiokenBaseAttack;
    private double kaiokenBaseSpeed;
    private float kaiokenBaseKiDamage;
    private String kaiokenBaseAuraType = "";
    private int kaiokenBaseAuraColor = 0xFFFFFF;
    private int racialTrainingProgress;
    private boolean racialTransformPending;
    private int racialCalmTicks;
    private int racialBasePower;
    private double racialBaseAttack;
    private double racialBaseSpeed;
    private double racialBaseAttackSpeed;
    private float racialBaseKiDamage;
    private float racialBaseScale = 1.0F;
    private String racialBaseHairColor = "";
    private String racialBaseEye1Color = "";
    private String racialBaseEye2Color = "";
    private String racialBaseAuraType = "";
    private int racialBaseAuraColor = 0xFFFFFF;
    private boolean racialBaseLightning;
    // 0.8.0: persistent, event-derived character history and real DMZ equipment state.
    private CompoundTag legacyData = new CompoundTag();
    private boolean arsenalInitialized;
    private int arsenalWeaponCooldown;
    private int combatStatsPower = -1;

    public static final int STORY_NONE = 0;
    public static final int STORY_ALLY = 1;
    public static final int STORY_ENEMY = 2;
    public static final int STORY_CAPTIVE = 3;
    public static final int STORY_PEACEKEEPER = 4;
    private static final String WEAPON_TRAINING_CONTROLLER = "lw_weapon_training_controller";
    private static final String WEAPON_COMBAT_CONTROLLER = "lw_weapon_combat_controller";
    private static final String UNARMED_TRAINING_CONTROLLER = "lw_unarmed_training_controller";
    private int renderedWeaponTrainingStrike = Integer.MIN_VALUE;
    private int renderedWeaponCombatStrike = 0;
    /** Client-only window that keeps DMZ's inherited generic attack controller off the same bones. */
    private int clientWeaponAnimationWindow;
    private int renderedUnarmedTrainingStrike = Integer.MIN_VALUE;

    public AmbientFighterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setTransformationDisabled(true);
        setDBZStyle(0);

        // DBSagasEntity normally auto-acquires players/villagers/golems. Remove only
        // those target goals; DMZ movement, melee, dash, flight and HurtByTargetGoal stay.
        removeAutomaticHostilityGoals();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return DBSagasEntity.createAttributes();
    }

    /**
     * Armed practice uses the exact DMZ attack selected by the equipped weapon's profile.  The
     * model neutralizes only the player-only hand-item locator afterward, so the native torso,
     * shoulder and arm choreography remains intact while the held weapon cannot snap away.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        // DMZ's stock attack_controller owns vanilla attack-timer body swings. Replace only that
        // controller with an LW-aware wrapper: weapon choreography suppresses it, while every
        // non-weapon state delegates to DMZ's exact original predicate.
        controllers.remove("attack_controller");
        controllers.add(new AnimationController<>(this, "attack_controller", 0,
                this::livingWorldAttackPredicate));
        controllers.add(new AnimationController<>(this, WEAPON_TRAINING_CONTROLLER, 0,
                this::weaponTrainingPredicate));
        controllers.add(new AnimationController<>(this, WEAPON_COMBAT_CONTROLLER, 0,
                this::weaponCombatPredicate));
        controllers.add(new AnimationController<>(this, UNARMED_TRAINING_CONTROLLER, 0,
                this::unarmedTrainingPredicate));
    }

    private PlayState livingWorldAttackPredicate(AnimationState<AmbientFighterEntity> state) {
        boolean weaponOwnsBody = hasWeaponAnimationProfile()
                && (isArmedTrainingPose() || clientWeaponAnimationWindow > 0
                    || entityData.get(WEAPON_COMBAT_STRIKE) != renderedWeaponCombatStrike);
        if (weaponOwnsBody) {
            if (state.getController().getAnimationState() != AnimationController.State.STOPPED)
                state.getController().forceAnimationReset();
            return PlayState.STOP;
        }
        return DBSagasAnimationHandler.attackPredicate(state);
    }

    private PlayState unarmedTrainingPredicate(AnimationState<AmbientFighterEntity> state) {
        if (!isTrainingPose() || !getMainHandItem().isEmpty()) {
            if (renderedUnarmedTrainingStrike != Integer.MIN_VALUE) {
                state.getController().forceAnimationReset();
                renderedUnarmedTrainingStrike = Integer.MIN_VALUE;
            }
            return PlayState.STOP;
        }
        int strike = entityData.get(UNARMED_TRAINING_STRIKE);
        if (strike != renderedUnarmedTrainingStrike) {
            renderedUnarmedTrainingStrike = strike;
            state.getController().forceAnimationReset();
            state.getController().setAnimation(resolveUnarmedTrainingAnimation(strike));
            state.getController().setAnimationSpeed(0.92D);
            return PlayState.CONTINUE;
        }
        state.getController().setAnimationSpeed(0.92D);
        return state.getController().getAnimationState() == AnimationController.State.STOPPED
                ? PlayState.STOP : PlayState.CONTINUE;
    }

    private RawAnimation resolveUnarmedTrainingAnimation(int strike) {
        int variant = Math.floorMod(strike, 3);
        int style = getDBZStyle();
        if (style == 1) return variant == 0 ? DBSagasAnimations.ANIM_ATTACK1_2 : variant == 1 ? DBSagasAnimations.ANIM_ATTACK2_2 : DBSagasAnimations.ANIM_ATTACK3_2;
        if (style == 2) return variant == 0 ? DBSagasAnimations.ANIM_ATTACK1_3 : variant == 1 ? DBSagasAnimations.ANIM_ATTACK2_3 : DBSagasAnimations.ANIM_ATTACK3_3;
        if (style == 3) return variant == 0 ? DBSagasAnimations.ANIM_ATTACK1_4 : variant == 1 ? DBSagasAnimations.ANIM_ATTACK2_4 : DBSagasAnimations.ANIM_ATTACK3_4;
        if (style == 4) return variant == 0 ? DBSagasAnimations.ANIM_ATTACK1_5 : variant == 1 ? DBSagasAnimations.ANIM_ATTACK2_5 : DBSagasAnimations.ANIM_ATTACK3_5;
        return variant == 0 ? DBSagasAnimations.ANIM_ATTACK1 : variant == 1 ? DBSagasAnimations.ANIM_ATTACK2 : DBSagasAnimations.ANIM_ATTACK3;
    }

    private PlayState weaponTrainingPredicate(AnimationState<AmbientFighterEntity> state) {
        if (!isArmedTrainingPose() || getMainHandItem().isEmpty()) {
            if (renderedWeaponTrainingStrike != Integer.MIN_VALUE) {
                state.getController().forceAnimationReset();
                renderedWeaponTrainingStrike = Integer.MIN_VALUE;
            }
            return PlayState.STOP;
        }
        int strike = entityData.get(WEAPON_TRAINING_STRIKE);
        if (strike != renderedWeaponTrainingStrike) {
            RawAnimation next = RawAnimation.begin().thenPlay(resolveWeaponTrainingAnimation(strike));
            renderedWeaponTrainingStrike = strike;
            // Different weapon clips already cause GeckoLib to load a fresh animation queue. Forcing
            // a reset first briefly returns every animated bone to its neutral pose, which was the
            // visible weapon twist between slash 1 / slash 2 / stab. Only force a reload when a
            // one-attack profile is intentionally replaying the exact same RawAnimation.
            if (next.equals(state.getController().getCurrentRawAnimation()))
                state.getController().forceAnimationReset();
            state.getController().setAnimation(next);
            state.getController().setAnimationSpeed(0.82D);
            return PlayState.CONTINUE;
        }
        state.getController().setAnimationSpeed(0.82D);
        // A one-shot weapon drill must release the controller when its clip ends. Holding the
        // final animated frame was the tiny end-of-swing weapon wiggle seen after practice strikes.
        if (state.getController().getAnimationState() == AnimationController.State.STOPPED) return PlayState.STOP;
        return PlayState.CONTINUE;
    }

    /**
     * Real armed melee must use the equipped weapon profile rather than DMZ's unarmed/Oozaru
     * combo clips. The held item layer is already parented to the hand bone; keeping the body on
     * the matching weapon animation prevents the weapon from hanging/twitching through fist combos.
     */
    private PlayState weaponCombatPredicate(AnimationState<AmbientFighterEntity> state) {
        if (getMainHandItem().isEmpty()) {
            if (renderedWeaponCombatStrike != 0) {
                state.getController().forceAnimationReset();
                renderedWeaponCombatStrike = 0;
            }
            return PlayState.STOP;
        }
        int strike = entityData.get(WEAPON_COMBAT_STRIKE);
        if (strike != renderedWeaponCombatStrike) {
            renderedWeaponCombatStrike = strike;
            clientWeaponAnimationWindow = 14;
            state.getController().forceAnimationReset();
            state.getController().setAnimation(RawAnimation.begin().thenPlay(resolveWeaponTrainingAnimation(strike)));
            state.getController().setAnimationSpeed(0.96D);
            return PlayState.CONTINUE;
        }
        return state.getController().getAnimationState() == AnimationController.State.STOPPED
                ? PlayState.STOP : PlayState.CONTINUE;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(READY, false);
        entityData.define(DEAD_SOUL, false);
        entityData.define(ALIGNMENT, FighterAlignment.NEUTRAL.id());
        entityData.define(RANK, FighterRank.ROOKIE.id());
        entityData.define(PERSONALITY, FighterPersonality.CALM.id());
        entityData.define(RACE, FighterRace.HUMAN.id());
        entityData.define(ARCHETYPE, FighterArchetype.MARTIAL_ARTIST.id());
        entityData.define(CAPTIVE, false);
        entityData.define(SPEECH, "");
        entityData.define(DEFEATED, false);
        entityData.define(FIGHTER_NAME, "Fighter");
        entityData.define(LEGACY_TITLE, "");
        entityData.define(AWAKENED, false);
        entityData.define(DISPLAY_SCALE, 1.0F);
        entityData.define(FACTION_ID, "");
        entityData.define(FACTION_LEADER, false);
        entityData.define(FACTION_ROLE, FactionRole.MEMBER.id());
        entityData.define(FACTION_NAME, "");
        entityData.define(FACTION_TITLE, "");
        entityData.define(AURA_FLARED, false);
        entityData.define(NON_COMBATANT, false);
        entityData.define(WANTED_ID, "");
        entityData.define(WANTED_LEVEL, 0);
        entityData.define(WANTED_CRIME, "");
        entityData.define(MEDITATING, false);
        entityData.define(MEDITATION_BOND_TIER, 0);
        entityData.define(MEDITATION_PLAYER_ID, "");
        entityData.define(MEDITATION_CIRCLE_MEMBER, false);
        entityData.define(KAIOKEN_LEVEL, 0);
        entityData.define(STORY_ROLE, STORY_NONE);
        entityData.define(FLIGHT_UNLOCKED, false);
        entityData.define(RACIAL_SKILL_LEVEL, 0);
        entityData.define(ACTIVE_RACIAL_FORM_LEVEL, 0);
        entityData.define(AMBIENT_POSE, 0);
        entityData.define(WEAPON_TRAINING_STRIKE, 0);
        entityData.define(WEAPON_COMBAT_STRIKE, 0);
        entityData.define(UNARMED_TRAINING_STRIKE, 0);
        entityData.define(COSMETIC_ACCESSORY, 0);
        entityData.define(FISHING_ACTIVITY, false);
        entityData.define(FISHING_BOBBER_X, 0.0F);
        entityData.define(FISHING_BOBBER_Y, 0.0F);
        entityData.define(FISHING_BOBBER_Z, 0.0F);

        entityData.define(GENDER, 0);
        entityData.define(BODY_TYPE, 1);
        entityData.define(EYES_TYPE, 0);
        entityData.define(NOSE_TYPE, 0);
        entityData.define(MOUTH_TYPE, 0);
        entityData.define(HEAD_BONE, 0);
        entityData.define(HAIR_ID, 1);
        entityData.define(OUTFIT, 0);
        entityData.define(BODY_COLOR, "#E9B18D");
        entityData.define(BODY_COLOR2, "#E9B18D");
        entityData.define(BODY_COLOR3, "#E9B18D");
        entityData.define(HAIR_COLOR, "#151515");
        entityData.define(EYE1_COLOR, "#503522");
        entityData.define(EYE2_COLOR, "#2B211B");
        entityData.define(REACTIVE_MOOD_VISUAL, 1); // CONTENT
        entityData.define(REACTIVE_MOOD_STRENGTH, 0);
    }

    /** Called by the encounter manager or debug command for a freshly created fighter. */
    public void initializeAs(FighterAlignment alignment, FighterRank rank) {
        RandomSource random = getRandom();
        initializeAs(alignment, rank, FighterPersonality.roll(random, alignment),
                FighterRace.roll(random), FighterArchetype.roll(random, rank));
    }

    public void initializeAs(FighterAlignment alignment, FighterRank rank, FighterPersonality personality) {
        RandomSource random = getRandom();
        initializeAs(alignment, rank, personality, FighterRace.roll(random), FighterArchetype.roll(random, rank));
    }

    public void initializeAs(FighterAlignment alignment, FighterRank rank, FighterPersonality personality,
                             FighterRace race, FighterArchetype archetype) {
        RandomSource random = getRandom();
        entityData.set(ALIGNMENT, alignment.id());
        entityData.set(RANK, rank.id());
        entityData.set(PERSONALITY, personality.id());
        entityData.set(RACE, race.id());
        entityData.set(ARCHETYPE, archetype.id());
        entityData.set(CAPTIVE, false);
        entityData.set(SPEECH, "");
        entityData.set(DEFEATED, false);
        entityData.set(AWAKENED, false);
        randomizeNativeAppearance(random);
        entityData.set(FIGHTER_NAME, FighterNames.rollUnique(this, random, race, isFemale()));
        if (level() instanceof ServerLevel server) {
            double effective = WorldPowerScaler.rollEffectiveStats(server, rank, random);
            FighterPowerStatScaler.setEffectiveStatBudget(this, effective);
            setBattlePower((int)Math.min(Integer.MAX_VALUE - 1L,
                    Math.round(FighterPowerStatScaler.battlePowerForEffectiveBudget(this, effective))));
        } else {
            setBattlePower(rank.rollBattlePower(random));
        }
        entityData.set(READY, true);
        defeatedTicks = 0;
        recoveryGraceTicks = 0;
        retreatTicks = 0;
        retreatThreatId = null;
        duelOpponentId = null;
        sanctionedMatchParticipant = false;
        sanctionedOpponentId = null;
        postSparOpponentId = null;
        postSparPeaceTicks = 0;
        postSparIncomingGraceTicks = 0;
        speechTicks = 0;
        cinematicLaunchCooldown = 0;
        awakeningTicks = 0;
        memoryOwnerId = null;
        memoryRecordId = null;
        memoryEncounters = 0;
        memoryRelationship = 0;
        memoryRescued = false;
        entityData.set(FACTION_ID, "");
        entityData.set(FACTION_LEADER, false);
        entityData.set(FACTION_ROLE, FactionRole.MEMBER.id());
        entityData.set(FACTION_NAME, "");
        entityData.set(FACTION_TITLE, "");
        entityData.set(AURA_FLARED, false);
        entityData.set(NON_COMBATANT, false);
        entityData.set(WANTED_ID, "");
        entityData.set(WANTED_LEVEL, 0);
        entityData.set(WANTED_CRIME, "");
        entityData.set(MEDITATING, false);
        entityData.set(KAIOKEN_LEVEL, 0);
        entityData.set(STORY_ROLE, STORY_NONE);
        entityData.set(FLIGHT_UNLOCKED, rollInitialFlight(random, rank));
        entityData.set(RACIAL_SKILL_LEVEL, rollInitialRacialSkill(random, rank, race));
        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
        entityData.set(AMBIENT_POSE, 0);
        entityData.set(WEAPON_TRAINING_STRIKE, 0);
        entityData.set(WEAPON_COMBAT_STRIKE, 0);
        entityData.set(UNARMED_TRAINING_STRIKE, 0);
        entityData.set(COSMETIC_ACCESSORY, 0);
        entityData.set(FISHING_ACTIVITY, false);
        entityData.set(FISHING_BOBBER_X, 0.0F);
        entityData.set(FISHING_BOBBER_Y, 0.0F);
        entityData.set(FISHING_BOBBER_Z, 0.0F);
        racialTrainingProgress = 0;
        racialTransformPending = false;
        racialCalmTicks = 0;
        auraFlareTicks = 0;
        lastAuraTargetId = null;
        angerAuraUsed = false;
        foodSupplies = 0;
        senzuBeans = 0;
        supplyCooldown = 0;
        regionalPresence = false;
        factionMerit = 0;
        meditationTicks = 0;
        meditationSessionLength = 0;
        meditationCooldown = 100 + random.nextInt(301);
        meditationPartnerPlayer = null;
        meditationPlayerJoinedExistingSolo = false;
        meditationPartnerNpc = null;
        meditationApproachPlayer = null;
        meditationApproachTicks = 0;
        meditationAnchorSet = false;
        meditationAnchorX = meditationAnchorZ = 0.0D;
        nextMeditationDialogueTick = 0L;
        entityData.set(MEDITATION_BOND_TIER, 0);
        entityData.set(MEDITATION_PLAYER_ID, "");
        entityData.set(MEDITATION_CIRCLE_MEMBER, false);
        socialLifeActivity = false;
        ambientFlightActivity = false;
        socialPowerDisplay = false;
        socialPlayerApproach = false;
        trainingSessions = 0;
        rivalName = "";
        kaiokenPotential = rollKaiokenPotential(random);
        kaiokenTicks = 0;
        kaiokenBasePower = 0;
        partyId = null;
        partyCaptain = false;
        lastFactionRepHitTick = Long.MIN_VALUE;
        legacyData = new CompoundTag();
        // Establish a canonical permanent BP only after the fresh legacy container exists.
        // The current DMZ field above is still needed during construction, but it is not an
        // authoritative source once temporary power layers are possible.
        legacyData.putInt(PERMANENT_BATTLE_POWER, Math.max(1, getBattlePower()));
        entityData.set(LEGACY_TITLE, "");
        arsenalInitialized = false;
        arsenalWeaponCooldown = 0;
        FighterSpecialItemManager.initialize(this);
        FighterScientistManager.initialize(this);
        setTransforming(false);
        setLightning(false);
        combatConfigured = false;
        configureCombatProfile(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && clientWeaponAnimationWindow > 0) clientWeaponAnimationWindow--;
        if (!level().isClientSide && isAlive() && postSparPeaceTicks > 0) enforcePostSparPeace();
        if (!level().isClientSide && isAlive()) enforcePlayerAlignmentCombatRules();
    }

    private void enforcePlayerAlignmentCombatRules() {
        if (!(getTarget() instanceof ServerPlayer player) || isSanctionedMatchParticipant()
                || getStoryRole() == STORY_ENEMY || WorldMenaceManager.isWorldMenace(this)) return;
        int moral = com.dmzlivingworld.world.PlayerAlignmentBridge.alignment(player);
        if (getAlignment() == FighterAlignment.GOOD && moral <= 32 && !isDefendingAgainstEvilPlayer(player)) {
            setTarget(null);
            setAggressive(false);
            return;
        }
        if ((getAlignment() == FighterAlignment.BAD && moral <= 32)
                || (getAlignment() == FighterAlignment.GOOD && moral >= 33 && moral <= 66)) {
            setTarget(null);
            setAggressive(false);
            return;
        }
        if (getAlignment() == FighterAlignment.GOOD && moral <= 32
                && player.getHealth() <= player.getMaxHealth() * 0.20F
                && getHealth() > getMaxHealth() * 0.30F) {
            setTarget(null);
            setAggressive(false);
            if (getPersistentData().getLong("LWAlignmentWarningAt") + 200L <= level().getGameTime()) {
                String warning = switch (getPersonality()) {
                    case HEROIC -> "You are not welcome here! Leave, and do not hurt anyone else!";
                    case CALM -> "You are not welcome here. Go away.";
                    case PROUD -> "I've made my point. Leave this place!";
                    case AGGRESSIVE -> "Get out before I change my mind!";
                    case CAUTIOUS -> "Stay down, then leave. You are not welcome here.";
                };
                speak(warning, 100);
                getPersistentData().putLong("LWAlignmentWarningAt", level().getGameTime());
            }
        }
    }

    private boolean isDefendingAgainstEvilPlayer(ServerPlayer player) {
        if (getLastHurtByMob() == player) return true;
        LivingEntity victim = player.getLastHurtMob();
        if (victim == null || !victim.isAlive() || victim.distanceToSqr(this) > 40.0D * 40.0D) return false;
        if (victim == this || victim instanceof net.minecraft.world.entity.npc.Villager) return true;
        return victim instanceof AmbientFighterEntity other && other.getAlignment() != FighterAlignment.BAD;
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide && postSparPeaceTicks > 0) enforcePostSparPeace();
        // Clear intent before native AI gets another chance to act while conceding.
        if (!level().isClientSide && (isDefeated() || isCaptive())) {
            suppressCombatIntent();
        }

        super.aiStep();
        if (!level().isClientSide && postSparPeaceTicks > 0) {
            enforcePostSparPeace();
            if (postSparIncomingGraceTicks > 0) postSparIncomingGraceTicks--;
            if (--postSparPeaceTicks <= 0) {
                postSparOpponentId = null;
                postSparIncomingGraceTicks = 0;
            }
        }
        if (level().isClientSide) {
            tickClientFishingBobber();
            return;
        }

        // Once vanilla has entered the real death lifecycle, LW must relinquish every behavior
        // owner. Without this gate a zero-health fighter could keep navigation/social/activity
        // logic alive underneath the death pose, producing the red, prone "walking corpse" bug.
        if (!isAlive() || getHealth() <= 0.0F || deathTime > 0) {
            quiesceForVanillaDeath();
            return;
        }

        // During player/NPC fusion the partner remains in the level solely as an exact persistent
        // state container for later separation. Do not let any LW life/social/debug-adjacent logic
        // run on that invisible passenger. Fusion restoration still owns the saved state.
        if (com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(this)) {
            if (!getSpeech().isEmpty()) { entityData.set(SPEECH, ""); speechTicks = 0; }
            getNavigation().stop();
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setKiCharge(false);
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        com.dmzlivingworld.world.FighterAmbientActivityManager.recoverTransientState(this);
        // R27: a faction-request assignment temporarily owns this real resident's life schedule.
        // It suppresses daily/social activity state while leaving the normal DMZ combat stack intact.
        com.dmzlivingworld.world.FactionRequestMissionManager.enforceMissionState(this);

        if (!entityData.get(READY)) {
            FighterAlignment alignment = FighterAlignment.roll(getRandom());
            FighterRank rank = FighterRank.roll(getRandom());
            initializeAs(alignment, rank, FighterPersonality.roll(getRandom(), alignment), FighterRace.roll(getRandom()), FighterArchetype.roll(getRandom(), rank));
        } else if (!combatConfigured) {
            configureCombatProfile(false);
        }

        // BP can be changed by encounters, factions, debug tools and compatibility modules.
        // Keep a cheap self-healing guard in addition to the explicit refresh API so no future
        // caller can silently recreate a displayed-BP/real-stat mismatch.
        if (entityData.get(READY) && combatStatsPower != getBattlePower()
                && !hasTemporaryPowerLayer()) {
            // Compatibility modules can still use DMZ's direct setter. Outside a known
            // temporary state, treat that as a permanent authoritative adjustment and rebuild
            // the physical profile instead of allowing display BP to drift from real stats.
            if (getBattlePower() != getPermanentBattlePower())
                legacyData.putInt(PERMANENT_BATTLE_POWER, Math.max(1, getBattlePower()));
            refreshCombatStatsFromPower();
        }
        // custom_BP.json can be reloaded without touching this entity. Reproject the synced
        // number from the live attributes/formula so scouters and the Combat tab never retain
        // the divisor/exponent that happened to be active when the NPC spawned.
        if (entityData.get(READY) && !hasTemporaryPowerLayer()) {
            int configuredPower = getPermanentBattlePower();
            if (getBattlePower() != configuredPower) {
                setBattlePower(configuredPower);
                combatStatsPower = configuredPower;
            }
        }

        if (!arsenalInitialized) FighterArsenalManager.initializeNaturalLoadout(this);

        if (speechTicks > 0 && --speechTicks <= 0) entityData.set(SPEECH, "");
        FighterEnvironmentManager.tick(this);
        reconcileFlightOwnership();
        FighterSpecialItemManager.tick(this);
        FighterScientistManager.tick(this);
        FighterLifeNeedsManager.tick(this);
        RedRibbonExperimentManager.tick(this);
        MercyManager.tick(this);
        recoverExpiredSocialPowerDisplay();
        // Friend-requested full-power display owns peaceful locomotion but deliberately uses the
        // same real racial-form transformation method as combat. Advance transformation frames
        // here, then let the manager hold/release the scene.
        if (FighterFullPowerManager.isActive(this)) {
            if (awakeningTicks > 0 || isTransforming()) tickAwakening();
            if (FighterFullPowerManager.tick(this)) return;
        }
        // Temporary forms must continue to age out even when an activity/social scene owns
        // locomotion later in this method. Otherwise a peaceful activity can freeze a form.
        if (isKaiokenActive()) tickKaioken();
        if (isRacialFormActive()) tickRacialForm();
        com.dmzlivingworld.world.ReactiveWorldManager.tick(this);
        FighterIntentManager.tick(this);
        long debugStopCharge = getPersistentData().getLong("LWDebugStopChargeAt");
        if (debugStopCharge > 0L && level().getGameTime() >= debugStopCharge) {
            setKiCharge(false);
            getPersistentData().remove("LWDebugStopChargeAt");
        }
        if (level() instanceof ServerLevel reactiveLevel) ReactiveInteractionManager.tick(this, reactiveLevel);
        ReactiveWorldEventManager.tick(this);
        com.dmzlivingworld.world.FactionHornManager.tick(this);
        enforceMinecraftMobCombatGate();
        FighterBattleGrowthManager.tickCombat(this);
        if (FighterEnvironmentManager.isEscapingWater(this)) {
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setKiCharge(false);
            return;
        }

        // Herobrine's rare distant-observer scenes deliberately own only his idle locomotion.
        // Combat, water escape and continuity always outrank the easter-egg behavior.
        if (WorldMenaceManager.tickFighter(this)) return;

        // Physical Continuity owns locomotion while a remembered fighter is visibly entering
        // or leaving the loaded world. Keep DMZ movement physics, but suppress unrelated goals,
        // target scans and casting so the traveller does not start a random fight mid-journey.
        if (getPersistentData().getBoolean("LWContinuityArriving")
                || getPersistentData().getBoolean("LWContinuityDeparting")) {
            if (isMeditating()) stopMeditation(false);
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setKiCharge(false);
            faceFlightMovement();
            return;
        }

        if (meditationApproachPlayer != null) {
            tickMeditationApproach();
            return;
        }

        if (socialPlayerApproach) {
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setKiCharge(false);
            setFlying(false);
            setFlyingFast(false);
            setNoGravity(false);
            return;
        }

        if (socialPowerDisplay) {
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setFlying(false);
            setFlyingFast(false);
            setNoGravity(false);
            getNavigation().stop();
            if (awakeningTicks > 0 || isTransforming()) {
                tickAwakening();
            } else {
                setKiCharge(true);
            }
            return;
        }

        if (socialLifeActivity) {
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setKiCharge(false);
            if (!ambientFlightActivity) { setFlying(false); setFlyingFast(false); setNoGravity(false); }
            else faceFlightMovement();
            return;
        }

        FighterLegacyManager.tickFusionObservation(this);
        FighterGoalManager.tick(this);
        FighterPromotionManager.tick(this);
        OrganicThreatManager.tick(this);
        if (tickCount % 60 == Math.floorMod(getUUID().hashCode(), 60)) FighterArsenalManager.tryPickupNearby(this);

        if (arsenalWeaponCooldown > 0) arsenalWeaponCooldown--;
        if (recoveryGraceTicks > 0) recoveryGraceTicks--;
        if (cinematicLaunchCooldown > 0) cinematicLaunchCooldown--;
        if (supplyCooldown > 0) supplyCooldown--;
        if (meditationCooldown > 0) meditationCooldown--;
        if (isCaptive()) {
            suppressCombatIntent();
            if (tickCount % 180 == 0 && getSpeech().isEmpty()) speak(FighterDialogue.captive(getRandom()), 60);
            return;
        }

        if (isMeditating()) {
            tickMeditation();
            return;
        }

        if (isDefeated()) {
            tickDefeated();
            return;
        }

        if (awakeningTicks > 0 || isTransforming()) {
            tickAwakening();
            return;
        }

        if (retreatTicks > 0) {
            tickRetreat();
            return;
        }

        if (FighterAftermathManager.tick(this)) return;

        FighterNpcSocialManager.tick(this);
        FighterNpcSocialManager.tickPendingReply(this);

        if (isNonCombatant()) {
            // Residents/supporters are real Living World people, not secretly combat mobs.
            // They can be hurt/killed and participate in faction simulation, but do not
            // proactively fight until another system explicitly changes their occupation.
            suppressCombatIntent();
            tickAuraBehavior();
            tickFactionSupplies();
            FactionManager.tickMember(this);
            if (!isFactionMember() && tickCount % 1200 == Math.floorMod(getUUID().hashCode(), 1200)) FactionManager.tryRecruitWanderer(this);
            FighterLivelinessManager.tick(this);
            tickIdleWandering();
            faceFlightMovement();
            return;
        }

        if (tickCount % TARGET_SCAN_INTERVAL == 0) {
            maintainOrAcquireTarget();
        }

        if (getAlignment() == FighterAlignment.NEUTRAL
                && getTarget() == null
                && tickCount % SPECTATE_SCAN_INTERVAL == 0) {
            spectateNearbyFight();
        }

        tickAuraBehavior();
        tickFactionSupplies();
        tryStartKaioken();
        if (tickCount % 4 == Math.floorMod(getUUID().hashCode(), 4)) FighterArsenalManager.tickCombatEquipment(this);
        ReactiveFusionManager.tick(this);
        FighterCombatDirector.tick(this);
        FactionManager.tickMember(this);
        if (!isFactionMember() && tickCount % 1200 == Math.floorMod(getUUID().hashCode(), 1200)) FactionManager.tryRecruitWanderer(this);
        if (tickCount % 200 == 0) {
            WantedManager.evaluate(this);
            if (isWanted()) WantedManager.update(this);
        }
        FighterLivelinessManager.tick(this);
        tickIdleWandering();
        faceFlightMovement();
    }

    /**
     * Last-resort ownership reconciliation for DMZ's flying/noGravity pair. Individual systems still
     * own their normal landing logic; this only cleans a state that nobody can currently justify.
     * It specifically prevents a stale noGravity bit from surviving a successful setFlying(false).
     */
    private void reconcileFlightOwnership() {
        if (level().isClientSide || !isAlive()) return;
        boolean ownsFlight = getTarget() != null || isRetreating() || isAwakening()
                || isMeditating() || isPreparingMeditation()
                || isAmbientFlightActivity() || isIdleFlightTravelling()
                || isSocialLifeActivity() || isSocialPlayerApproach() || isSocialPowerDisplay()
                || getPersistentData().getBoolean("LWContinuityArriving")
                || getPersistentData().getBoolean("LWContinuityDeparting")
                || LivingBondManager.isTravellingCompanion(this)
                || com.dmzlivingworld.world.FactionRequestMissionManager.isAssigned(this)
                || FighterEnvironmentManager.isEscapingWater(this)
                || WorldMenaceManager.isHerobrine(this);

        if (!isFlying() && isNoGravity() && !ownsFlight) {
            setNoGravity(false);
            getPersistentData().remove("LWUnownedFlightTicks");
            return;
        }
        if (!isFlying() && !isNoGravity()) {
            getPersistentData().remove("LWUnownedFlightTicks");
            return;
        }
        if (ownsFlight) {
            getPersistentData().remove("LWUnownedFlightTicks");
            return;
        }
        int unowned = getPersistentData().getInt("LWUnownedFlightTicks") + 1;
        getPersistentData().putInt("LWUnownedFlightTicks", unowned);
        // Grounded stale flight is always invalid. Airborne state gets a short grace so a legitimate
        // owner handoff can occur without snapping a fighter out of the air between managers.
        if ((onGround() && unowned >= 2) || unowned >= 50) {
            setFlyingFast(false);
            setFlying(false);
            setNoGravity(false);
            getPersistentData().remove("LWUnownedFlightTicks");
            setDeltaMovement(getDeltaMovement().multiply(0.65D, 0.35D, 0.65D));
        }
    }

    private void faceFlightMovement() {
        if (level().isClientSide || !isFlying() || getTarget() != null) return;
        Vec3 motion = getDeltaMovement();
        double horizontal = motion.x * motion.x + motion.z * motion.z;
        if (horizontal < 0.0025D) return;
        float yaw = (float)(Math.toDegrees(Math.atan2(motion.z, motion.x)) - 90.0D);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        yRotO = yaw;
        yBodyRotO = yaw;
        yHeadRotO = yaw;
    }

    /**
     * DMZ's native aerial pursuit points velocity directly at the target.  That is fast in an
     * empty arena but has no obstacle routing, so a flying fighter can remain pressed against a
     * wall, cliff or roof indefinitely.  Preserve DMZ's combat state machine while replacing only
     * that direct steering step with a small, bounded three-dimensional detour search.
     */
    @Override
    public void moveTowardsTargetInAir(LivingEntity target) {
        if (target == null || isCasting() || isComboing() || isEvading() || isZanzoken() || isStunned()) return;

        double distance = distanceTo(target);
        if (distance > 15.0D) setFlyingFast(true);
        else if (distance < 7.0D) setFlyingFast(false);

        double speed = Math.max(0.18D, getFlySpeed()) * (isFlyingFast() ? 2.0D : 1.0D);
        Vec3 destination = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.62D, target.getZ());
        steerFlightToward(destination, speed);
        rotateBodyToTarget(target);
    }

    private void steerFlightToward(Vec3 destination, double speed) {
        if (destination == null || level().isClientSide) return;
        setNoGravity(true);

        Vec3 rayStart = position().add(0.0D, getBbHeight() * 0.55D, 0.0D);
        boolean directClear = hasClearFlightLine(rayStart, destination);
        boolean waypointBlocked = !flightWaypoint.equals(Vec3.ZERO) && !hasClearFlightLine(rayStart, flightWaypoint);
        if (horizontalCollision || verticalCollision || waypointBlocked) flightWaypointTicks = 0;
        if (waypointBlocked) flightWaypoint = Vec3.ZERO;
        boolean destinationChanged = flightWaypointDestination.equals(Vec3.ZERO)
                || flightWaypointDestination.distanceToSqr(destination) > 6.0D * 6.0D;
        if (directClear) {
            flightWaypoint = Vec3.ZERO;
            flightWaypointDestination = destination;
            flightWaypointTicks = 0;
        } else if (destinationChanged || flightWaypointTicks-- <= 0 || flightWaypoint.equals(Vec3.ZERO)
                || position().distanceToSqr(flightWaypoint) < 3.0D) {
            flightWaypoint = chooseFlightWaypoint(rayStart, destination);
            flightWaypointDestination = destination;
            flightWaypointTicks = 10;
        }

        // A detour is only useful while it still advances toward the current destination. Rare
        // overshoot used to leave a now-behind waypoint alive for up to ten ticks, producing the
        // visible "fly backwards a few blocks, then turn around" hiccup. Drop it immediately.
        if (!flightWaypoint.equals(Vec3.ZERO)) {
            Vec3 toDestination = destination.subtract(position());
            Vec3 toWaypoint = flightWaypoint.subtract(position());
            if (toDestination.lengthSqr() > 1.0D && toWaypoint.lengthSqr() > 0.25D
                    && toWaypoint.dot(toDestination) <= 0.0D) {
                flightWaypoint = Vec3.ZERO;
                flightWaypointTicks = 0;
            }
        }

        Vec3 steeringTarget = flightWaypoint.equals(Vec3.ZERO) ? destination : flightWaypoint;
        Vec3 delta = steeringTarget.subtract(position());
        double distance = delta.length();
        if (distance < 0.001D) return;

        double resolvedSpeed = Math.max(0.12D, speed);
        if (distance < 4.0D) resolvedSpeed *= Math.max(0.42D, distance / 4.0D);
        Vec3 wanted = delta.scale(resolvedSpeed / distance);
        if (onGround()) wanted = new Vec3(wanted.x, Math.max(0.32D, wanted.y), wanted.z);

        // A collision from the previous movement tick immediately biases the next sample upward
        // and forces a new detour. This prevents long wall-scraping loops when terrain changes.
        if (horizontalCollision) {
            flightWaypointTicks = 0;
            wanted = wanted.add(0.0D, 0.22D, 0.0D);
        }
        if (verticalCollision && wanted.y > 0.0D) {
            flightWaypointTicks = 0;
            Vec3 lateral = perpendicularTowardOpenSide(delta);
            wanted = wanted.add(lateral.scale(0.20D));
        }

        Vec3 next = getDeltaMovement().scale(0.42D).add(wanted.scale(0.58D));
        double maximum = resolvedSpeed * 1.12D;
        if (next.lengthSqr() > maximum * maximum) next = next.normalize().scale(maximum);
        setDeltaMovement(next);
        hasImpulse = true;
        getLookControl().setLookAt(steeringTarget.x, steeringTarget.y, steeringTarget.z, 18.0F, 15.0F);
    }

    /**
     * Gives non-combat Living World activities the same collision-aware aerial steering used by
     * pursuit and ordinary travel. Activity managers still own their route and pace; the entity
     * owns the safe three-dimensional movement needed to reach each point.
     */
    public void steerAmbientFlightToward(Vec3 destination, double speed) {
        steerFlightToward(destination, speed);
    }

    private Vec3 chooseFlightWaypoint(Vec3 from, Vec3 destination) {
        HitResult hit = level().clip(new ClipContext(from, destination,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return Vec3.ZERO;

        Vec3 obstruction = hit.getLocation();
        Vec3 route = destination.subtract(from);
        Vec3 flat = new Vec3(route.x, 0.0D, route.z);
        if (flat.lengthSqr() < 0.001D) flat = new Vec3(1.0D, 0.0D, 0.0D);
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        double clearance = Math.max(3.25D, getBbWidth() * 2.0D + 1.5D);
        double above = obstruction.y + Math.max(3.5D, getBbHeight() + 2.0D);

        Vec3[] candidates = {
                new Vec3(obstruction.x, above, obstruction.z),
                obstruction.add(side.scale(clearance)).add(0.0D, 1.25D, 0.0D),
                obstruction.add(side.scale(-clearance)).add(0.0D, 1.25D, 0.0D),
                obstruction.add(side.scale(clearance * 1.55D)).add(0.0D, 2.5D, 0.0D),
                obstruction.add(side.scale(-clearance * 1.55D)).add(0.0D, 2.5D, 0.0D)
        };

        Vec3 best = Vec3.ZERO;
        double bestScore = Double.MAX_VALUE;
        for (Vec3 candidate : candidates) {
            BlockPos pos = BlockPos.containing(candidate);
            if (!level().hasChunkAt(pos)) continue;
            Vec3 move = candidate.subtract(position());
            Vec3 remaining = destination.subtract(position());
            // Never choose a fresh obstacle waypoint behind the fighter's current forward progress.
            if (remaining.lengthSqr() > 1.0D && move.dot(remaining) <= 0.0D) continue;
            if (!level().noCollision(this, getBoundingBox().move(move))) continue;
            if (!hasClearFlightLine(from, candidate)) continue;
            double score = from.distanceTo(candidate) + candidate.distanceTo(destination);
            if (hasClearFlightLine(candidate, destination)) score -= 8.0D;
            score += Math.max(0.0D, candidate.y - destination.y) * 0.08D;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        // If both side routes are cramped, climbing above the first obstruction is still safer
        // than continuing to accelerate into it. Collision feedback will resample on the next tick.
        if (!best.equals(Vec3.ZERO)) return best;
        Vec3 climb = new Vec3(obstruction.x, above, obstruction.z);
        Vec3 forward = destination.subtract(position());
        // Even the emergency climb fallback must not send the fighter backwards after an overshoot.
        if (forward.lengthSqr() > 1.0D && climb.subtract(position()).dot(forward) <= 0.0D) {
            Vec3 dir = forward.normalize();
            climb = position().add(dir.scale(Math.max(3.0D, getBbWidth() * 2.0D)))
                    .add(0.0D, Math.max(2.5D, getBbHeight()), 0.0D);
        }
        return climb;
    }

    private boolean hasClearFlightLine(Vec3 from, Vec3 to) {
        if (from == null || to == null || from.distanceToSqr(to) < 0.25D) return true;
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private Vec3 perpendicularTowardOpenSide(Vec3 route) {
        Vec3 flat = new Vec3(route.x, 0.0D, route.z);
        if (flat.lengthSqr() < 0.001D) return new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x).normalize();
        Vec3 from = position().add(0.0D, getBbHeight() * 0.55D, 0.0D);
        boolean left = hasClearFlightLine(from, from.add(side.scale(3.0D)));
        boolean right = hasClearFlightLine(from, from.add(side.scale(-3.0D)));
        if (left != right) return left ? side : side.scale(-1.0D);
        return getRandom().nextBoolean() ? side : side.scale(-1.0D);
    }

    /**
     * Gives the literal inspection state "Wandering" real locomotion. This only runs when no
     * higher-level Living World system currently owns the fighter's movement.
     */
    private void tickIdleWandering() {
        if (com.dmzlivingworld.world.FactionRequestMissionManager.isAssigned(this)) return;
        if (FighterLivelinessManager.isHoldingIdle(this)) { getNavigation().stop(); return; }
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel)) return;
        if (getTarget() != null || isMeditating() || socialLifeActivity || socialPlayerApproach
                || socialPowerDisplay || isDefeated() || isCaptive() || isTransforming()
                || getPersistentData().getBoolean("LWContinuityArriving")
                || getPersistentData().getBoolean("LWContinuityDeparting")) {
            clearIdleFlightTravel(false);
            return;
        }
        // A strong low-energy mood is allowed to terminate an old ordinary-flight route instead
        // of waiting for that route to finish before the new emotion can become readable.
        if (isIdleFlightTravelling() && !getPersistentData().getBoolean("LWReactiveEscapeFlight")
                && ReactiveWorldManager.moodStrength(this) >= 60) {
            switch (ReactiveWorldManager.mood(this)) {
                case IRRITATED, SOMBER, WEARY -> clearIdleFlightTravel(true);
                default -> { }
            }
        }
        CompoundTag data = getPersistentData();
        long now = serverLevel.getGameTime();
        if (isIdleStretching()) {
            if (now < data.getLong("LWIdleStretchUntil")) {
                getNavigation().stop();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                return;
            }
            setAmbientPose(0);
            data.remove("LWIdleStretchUntil");
        }
        if (tickIdleFlightTravel(serverLevel)) return;
        if (ReactiveMoodBehaviorManager.tickIdle(this, serverLevel)) return;
        if (!getNavigation().isDone()) return;
        if (isFlying() && onGround() && !ambientFlightActivity) { setFlyingFast(false); setFlying(false); }
        if (getAmbientPose() == 0 && tickCount > 80 && Math.floorMod(tickCount + getUUID().hashCode(), 900) == 0
                && getRandom().nextFloat() < 0.10F) {
            setAmbientPose(9 + getRandom().nextInt(2));
            data.putLong("LWIdleStretchUntil", now + 40L + getRandom().nextInt(16));
            getNavigation().stop();
            if (getSpeech().isEmpty()) {
                String[] stretchLines = {"Needed that stretch.", "Loosen up before moving again.", "Back was getting stiff.", "Just stretching out a little.", "Good. Much better."};
                speak(stretchLines[getRandom().nextInt(stretchLines.length)], 40);
            }
            return;
        }
        long next = data.getLong("LWNextIdleWander");
        if (next <= 0L) {
            data.putLong("LWNextIdleWander", now + 30L + getRandom().nextInt(70));
            return;
        }
        if (now < next) return;

        // R22: idle means occupying a place, not choosing another unrelated 20-block destination
        // every few seconds. A recently successful activity becomes a temporary local anchor.
        BlockPos anchor = FighterLivelinessManager.idleAnchor(this);
        if (anchor == null) anchor = blockPosition();
        boolean returningToAnchor = anchor.distSqr(blockPosition()) > 12.0D * 12.0D;
        int minWander = returningToAnchor ? 0 : 2, maxWander = returningToAnchor ? 5 : 7;
        if (!returningToAnchor && ReactiveWorldManager.moodStrength(this) >= 55) {
            switch (ReactiveWorldManager.mood(this)) {
                case UPBEAT -> { minWander = 3; maxWander = 9; }
                case CONTENT -> { minWander = 2; maxWander = 7; }
                case FOCUSED, WARY -> { minWander = 2; maxWander = 6; }
                case IRRITATED -> { minWander = 4; maxWander = 10; }
                case SOMBER -> { minWander = 1; maxWander = 5; }
                case WEARY -> { minWander = 1; maxWander = 4; }
            }
        }
        BlockPos destination = AmbientFighterSpawner.findSafeGroundAround(serverLevel, anchor,
                getRandom(), minWander, maxWander, 18);
        int wanderDelay = LivingWorldConfig.ambientActivityDelay(180 + getRandom().nextInt(260));
        data.putLong("LWNextIdleWander", now + Math.max(40L, Math.round(wanderDelay * ReactiveWorldManager.idlePauseMultiplier(this))));
        if (destination == null) return;

        double dx = destination.getX() + 0.5D - getX();
        double dz = destination.getZ() + 0.5D - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = Math.abs(destination.getY() - getY());
        float flightChance = Math.min(0.52F, 0.22F * ReactiveWorldManager.flightDrive(this));
        boolean terrainNeedsFlight = vertical >= 5.0D;
        boolean distanceJustifiesFlight = horizontal >= 20.0D && getRandom().nextFloat() < flightChance;
        boolean usefulFlight = hasFlightUnlocked() && !isNonCombatant() && !isInWaterOrBubble()
                && (terrainNeedsFlight || distanceJustifiesFlight);
        if (usefulFlight) {
            data.putBoolean("LWIdleFlightTravel", true);
            data.putDouble("LWIdleFlightX", destination.getX() + 0.5D);
            data.putDouble("LWIdleFlightY", destination.getY() + 1.3D);
            data.putDouble("LWIdleFlightZ", destination.getZ() + 0.5D);
            data.putLong("LWIdleFlightUntil", now + 260L);
            data.putLong("LWIdleFlightStartedAt", now);
            data.putDouble("LWIdleFlightStartY", getY());
            getNavigation().stop();
            setFlying(true);
            setNoGravity(true);
            setFlyingFast(horizontal > 21.0D);
            return;
        }

        if (isFlying()) setFlying(false);
        setFlyingFast(false);
        setNoGravity(false);
        setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
        double moodPace = ReactiveWorldManager.movementPace(this);
        getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                (isNonCombatant() ? 0.78D : 0.88D) * moodPace);
    }

    /** Purposeful short-range flight used by ordinary wandering when terrain/distance makes walking silly. */
    private boolean tickIdleFlightTravel(ServerLevel level) {
        CompoundTag data = getPersistentData();
        if (!data.getBoolean("LWIdleFlightTravel")) return false;
        if (!hasFlightUnlocked() || isNonCombatant() || isInWaterOrBubble()) {
            clearIdleFlightTravel(true);
            return false;
        }
        long now = level.getGameTime();
        Vec3 target = new Vec3(data.getDouble("LWIdleFlightX"), data.getDouble("LWIdleFlightY"), data.getDouble("LWIdleFlightZ"));
        Vec3 deltaTo = target.subtract(position());
        double dist = deltaTo.length();
        long startedAt = data.getLong("LWIdleFlightStartedAt");
        if (onGround() && startedAt > 0L && now - startedAt > 36L) {
            // Native flight failed to leave the ground: relinquish the flight state and walk
            // toward the same destination rather than vibrating in place forever.
            BlockPos feet = BlockPos.containing(target.x, target.y - 1.3D, target.z);
            clearIdleFlightTravel(true);
            setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
            getNavigation().moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 1.04D);
            return false;
        }
        if (now >= data.getLong("LWIdleFlightUntil") || dist < 2.8D) {
            clearIdleFlightTravel(false);
            setFlyingFast(false);
            setDeltaMovement(getDeltaMovement().scale(0.45D));
            setFlying(false);
            BlockPos feet = BlockPos.containing(target.x, target.y - 1.3D, target.z);
            getNavigation().moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.82D);
            return false;
        }
        getNavigation().stop();
        setFlying(true);
        setNoGravity(true);
        boolean fast = dist > 15.0D;
        setFlyingFast(fast);
        double speed = fast ? 0.48D : 0.34D;
        steerFlightToward(target, speed);
        getLookControl().setLookAt(target.x, target.y, target.z, 14.0F, 12.0F);
        return true;
    }

    private void clearIdleFlightTravel(boolean forceGroundState) {
        CompoundTag data = getPersistentData();
        data.remove("LWIdleFlightTravel");
        data.remove("LWReactiveEscapeFlight");
        data.remove("LWIdleFlightX"); data.remove("LWIdleFlightY"); data.remove("LWIdleFlightZ"); data.remove("LWIdleFlightUntil");
        data.remove("LWIdleFlightStartedAt"); data.remove("LWIdleFlightStartY");
        flightWaypoint = Vec3.ZERO;
        flightWaypointDestination = Vec3.ZERO;
        flightWaypointTicks = 0;
        setFlyingFast(false);
        // Relinquish ordinary travel flight when no other system owns it. Combat is allowed to keep
        // native flight, but a mood/social/activity handoff must not leave stale flight latched on.
        if (forceGroundState || isInWaterOrBubble() || (getTarget() == null && !ambientFlightActivity)) {
            setFlying(false);
            setNoGravity(false);
        }
    }

    public boolean isIdleFlightTravelling() { return getPersistentData().getBoolean("LWIdleFlightTravel"); }

    /** Short purposeful escape used when a strong mood makes a fighter enforce personal space. */
    public boolean beginReactiveEscapeFrom(ServerPlayer player) {
        if (player == null || !hasFlightUnlocked() || isNonCombatant() || isInWaterOrBubble() || getTarget() != null) return false;
        Vec3 away = position().subtract(player.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(getRandom().nextDouble() - 0.5D, 0.0D, getRandom().nextDouble() - 0.5D);
        away = away.normalize().scale(26.0D + getRandom().nextDouble() * 18.0D);
        Vec3 target = position().add(away).add(0.0D, 6.0D + getRandom().nextDouble() * 7.0D, 0.0D);
        BlockPos targetPos = BlockPos.containing(target);
        if (!(level() instanceof ServerLevel serverLevel) || !serverLevel.hasChunkAt(targetPos)) return false;
        CompoundTag data = getPersistentData();
        data.putBoolean("LWIdleFlightTravel", true);
        data.putBoolean("LWReactiveEscapeFlight", true);
        data.putDouble("LWIdleFlightX", target.x);
        data.putDouble("LWIdleFlightY", target.y);
        data.putDouble("LWIdleFlightZ", target.z);
        data.putLong("LWIdleFlightUntil", serverLevel.getGameTime() + 180L);
        data.putLong("LWIdleFlightStartedAt", serverLevel.getGameTime());
        data.putDouble("LWIdleFlightStartY", getY());
        getNavigation().stop();
        setFlying(true);
        setNoGravity(true);
        setFlyingFast(true);
        return true;
    }

    private void configureCombatProfile(boolean refillHealth) {
        FighterRank rank = getRank();
        setAiTierById(rank.aiTier());
        setCanFly(hasFlightUnlocked() && !isNonCombatant());
        if (!hasFlightUnlocked() || isNonCombatant()) { setFlying(false); setFlyingFast(false); setNoGravity(false); }

        applyPowerScaledCoreAttributes();
        var range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) range.setBaseValue(rank == FighterRank.VETERAN ? 72.0D : 56.0D);

        FighterCombatDirector.configure(this);
        if (isAwakened() && !isRacialFormActive()) applyAwakenedCombatBoost();

        if (refillHealth) setHealth(getMaxHealth());
        else if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        combatStatsPower = getBattlePower();
        combatConfigured = true;
    }

    /** Re-evaluate physical combat stats after permanent BP changes while preserving current health percentage. */
    public void refreshCombatStatsFromPower() {
        if (isRacialFormActive() || isKaiokenActive()) return;
        float oldMax = Math.max(1.0F, getMaxHealth());
        float ratio = Math.max(0.0F, Math.min(1.0F, getHealth() / oldMax));
        applyPowerScaledCoreAttributes();
        FighterCombatDirector.configurePowerDamage(this);
        if (isAwakened()) applyAwakenedCombatBoost();
        // A few intentionally special fighters add their own combat floors on top of the
        // ordinary BP profile. Restore those additive floors after every BP rebuild so a
        // fruit/form reconciliation can never quietly weaken an existing encounter.
        WorldMenaceManager.restoreCombatFloors(this);
        combatStatsPower = getBattlePower();
        combatConfigured = true;
        if (isAlive()) setHealth(Math.max(1.0F, getMaxHealth() * ratio));
    }

    /**
     * Returns the authoritative, non-temporary BP. Forms, Kaioken, Might Fruit and a social
     * comparison can change native DMZ's visible BP, but none is allowed to overwrite this base.
     */
    public int getPermanentBattlePower() {
        double effective = legacyData.getDouble(FighterPowerStatScaler.EFFECTIVE_STATS);
        if (effective > 0.0D && Double.isFinite(effective)) {
            double totalStats = entityData.get(READY) && getAttribute(Attributes.ATTACK_DAMAGE) != null
                    ? FighterPowerStatScaler.currentTotalStats(this) : effective;
            return (int)Math.min(Integer.MAX_VALUE - 1L,
                    Math.max(1L, Math.round(FighterPowerStatScaler.battlePowerForStats(this, totalStats))));
        }
        int stored = legacyData.getInt(PERMANENT_BATTLE_POWER);
        return Math.max(1, stored > 0 ? stored : getBattlePower());
    }

    /** Presentation/fusion reading; never use this for Living World progression or stat scaling. */
    public int getVisualBattlePower() {
        return com.dmzlivingworld.world.FighterVisualPower.of(this);
    }

    /**
     * A permanent adjustment made by setup/faction/debug systems. It does not qualify as
     * personal progression for faction re-anchoring, but it always updates the canonical base.
     */
    public void setBattlePowerAndRefresh(int battlePower) {
        setPermanentBattlePowerAndRefresh(battlePower, false);
    }

    /**
     * Records real earned BP (training, meditation, jogging, sparring, battle or awakening).
     * The gain survives any temporary form and immediately rebuilds HP/melee/Ki whenever the
     * fighter is not temporarily powered up.
     */
    public void setEarnedBattlePowerAndRefresh(int battlePower) {
        setPermanentBattlePowerAndRefresh(battlePower, true);
    }

    private void setPermanentBattlePowerAndRefresh(int battlePower, boolean earned) {
        int previous = getPermanentBattlePower();
        int permanent = Math.max(1, battlePower);
        FighterPowerStatScaler.setEffectiveStatBudget(this,
                FighterPowerStatScaler.effectiveForPowerChange(this, previous, permanent));
        legacyData.putInt(PERMANENT_BATTLE_POWER, permanent);
        if (earned && permanent > previous) {
            legacyData.putInt(EARNED_BATTLE_POWER_FLOOR,
                    Math.max(permanent, legacyData.getInt(EARNED_BATTLE_POWER_FLOOR)));
        }

        // Keep the visible temporary layer coherent without allowing its multiplier to be
        // written back as permanent progress. Active forms retain their established modifiers
        // while their BP-backed base is safely rebuilt underneath.
        setBattlePower(projectedBattlePower());
        if (!level().isClientSide && entityData.get(READY) && (isRacialFormActive() || isKaiokenActive())) {
            refreshActiveCombatPowerLayer();
        } else if (!level().isClientSide && entityData.get(READY) && !blocksPowerProfileRefresh()) {
            refreshCombatStatsFromPower();
        } else if (!level().isClientSide) {
            // The base is already persisted; mark the temporary physical profile stale so its
            // normal exit path reconstructs HP/melee/Ki from the earned value.
            combatStatsPower = -1;
        }
    }

    /**
     * Learnt personal progression cannot be erased by a later faction/world maintenance nudge.
     * Existing R5 saves are migrated lazily: any fighter with recorded training or lived combat
     * gets their current canonical BP protected the first time the floor is consulted.
     */
    public int getEarnedBattlePowerFloor() {
        int floor = legacyData.getInt(EARNED_BATTLE_POWER_FLOOR);
        if (floor <= 0 && (trainingSessions > 0 || legacyData.getDouble("LWCombatGrowth") > 0.0D)) {
            floor = getPermanentBattlePower();
            legacyData.putInt(EARNED_BATTLE_POWER_FLOOR, floor);
        }
        return Math.max(0, floor);
    }

    /** Reprojects native DMZ's visible BP from the permanent base after a temporary layer changes. */
    public void refreshTemporaryPowerProjection() {
        setBattlePower(projectedBattlePower());
        if (!level().isClientSide && entityData.get(READY) && (isRacialFormActive() || isKaiokenActive())) {
            // Fruit and an organic gain can begin/end during a form. Rebuild the normal
            // BP-backed layer first, then put the existing form/Kaioken modifiers back on
            // top so visible temporary BP never outruns actual HP, melee or Ki.
            refreshActiveCombatPowerLayer();
        } else if (!level().isClientSide && entityData.get(READY) && !blocksPowerProfileRefresh()) {
            refreshCombatStatsFromPower();
        } else if (!level().isClientSide) {
            combatStatsPower = -1;
        }
    }

    /**
     * Reapplies the existing racial/Kaioken combat layer after a permanent base or temporary
     * fruit layer changes. This is deliberately a compositor, not a replacement for either
     * form system: the established attack/speed/Ki multipliers, cosmetics and timers survive.
     */
    private void refreshActiveCombatPowerLayer() {
        boolean racial = isRacialFormActive();
        boolean kaioken = isKaiokenActive();
        if (!racial && !kaioken) {
            refreshCombatStatsFromPower();
            return;
        }

        int racialLevel = getActiveRacialFormLevel();
        int kaiokenLevel = getKaiokenLevel();
        NpcFormConfigBridge.Form form = racial ? NpcFormConfigBridge.form(getRace(), racialLevel) : null;
        double kaiokenPowerMultiplier = kaioken ? kaiokenMultiplier(kaiokenLevel) : 1.0D;

        // Temporarily expose only canonical BP plus any live fruit. The normal refresh owns
        // health/condition scaling, then the same established form modifiers are reapplied.
        // Forms are mutually exclusive in normal play; clearing both also hardens old/corrupt
        // saves without baking either temporary multiplier into the base.
        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
        entityData.set(KAIOKEN_LEVEL, 0);
        setBattlePower(projectedBattlePower());
        refreshCombatStatsFromPower();

        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (racial && form != null) {
            racialBasePower = getPermanentBattlePower();
            racialBaseAttack = attack == null ? getRank().attackDamage() : attack.getBaseValue();
            racialBaseSpeed = speed == null ? getRank().speed() : speed.getBaseValue();
            racialBaseAttackSpeed = getDefaultAttackSpeed();
            racialBaseKiDamage = getKiBlastDamage();
            entityData.set(ACTIVE_RACIAL_FORM_LEVEL, racialLevel);
            if (attack != null) attack.setBaseValue(racialBaseAttack * form.melee());
            if (speed != null) speed.setBaseValue(racialBaseSpeed * form.speed());
            setDefaultAttackSpeed(racialBaseAttackSpeed * form.attackSpeed());
            setKiBlastDamage((float)(racialBaseKiDamage * form.ki()));
        }
        if (kaioken) {
            // Kaioken and racial form are normally mutually exclusive. Preserve the direct
            // Kaioken path as-is while making its base reflect any live fruit/permanent gain.
            kaiokenBasePower = getPermanentBattlePower();
            kaiokenBaseAttack = attack == null ? getRank().attackDamage() : attack.getBaseValue();
            kaiokenBaseSpeed = speed == null ? getRank().speed() : speed.getBaseValue();
            kaiokenBaseKiDamage = getKiBlastDamage();
            entityData.set(KAIOKEN_LEVEL, kaiokenLevel);
            if (attack != null) attack.setBaseValue(kaiokenBaseAttack * kaiokenPowerMultiplier);
            if (speed != null) speed.setBaseValue(kaiokenBaseSpeed * kaiokenPowerMultiplier);
            setKiBlastDamage((float)(kaiokenBaseKiDamage * kaiokenPowerMultiplier));
        }
        setBattlePower(projectedBattlePower());
        combatStatsPower = getBattlePower();
    }

    private int projectedBattlePower() {
        double projected = getPermanentBattlePower();
        if (FighterSpecialItemManager.hasActiveMightFruit(this)) {
            projected *= FighterSpecialItemManager.mightFruitMultiplier(this);
        }
        NpcFormConfigBridge.Form form = isRacialFormActive()
                ? NpcFormConfigBridge.form(getRace(), getActiveRacialFormLevel()) : null;
        if (form != null) projected = FighterPowerStatScaler.transformedBattlePower(this,
                form.melee(), form.defense(), form.vitality(), form.ki());
        if (isKaiokenActive()) {
            NpcFormConfigBridge.Form kaioken = NpcFormConfigBridge.kaioken(getKaiokenLevel());
            if (kaioken != null) projected = FighterPowerStatScaler.transformedBattlePower(this,
                    kaioken.melee(), kaioken.defense(), kaioken.vitality(), kaioken.ki());
            else projected *= kaiokenMultiplier(getKaiokenLevel());
        }
        return (int)Math.min(Integer.MAX_VALUE - 1L, Math.max(1L, Math.round(projected)));
    }

    private boolean hasTemporaryPowerLayer() {
        return isRacialFormActive() || isKaiokenActive() || isTransforming() || socialPowerDisplay
                || FighterSpecialItemManager.hasActiveMightFruit(this);
    }

    /** Fruit is a real one-minute combat boost and may refresh stats; forms/social displays are not. */
    private boolean blocksPowerProfileRefresh() {
        return isRacialFormActive() || isKaiokenActive() || isTransforming() || socialPowerDisplay;
    }

    private void applyPowerScaledCoreAttributes() {
        FighterRank rank = getRank();
        double livedMultiplier = FighterBattleGrowthManager.combatMultiplier(this);
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(FighterPowerStatScaler.baseHealth(this, livedMultiplier)
                * FighterPassiveSkillManager.healthMultiplier(this));
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(rank.speed() * Math.min(1.10D, 1.0D + (livedMultiplier - 1.0D) * 0.22D)
                * FighterPassiveSkillManager.speedMultiplier(this));
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(FighterPowerStatScaler.baseAttack(this, livedMultiplier)
                * FighterPassiveSkillManager.meleeMultiplier(this));
    }

    public double getDefenseStat() {
        double defense = FighterPowerStatScaler.baseDefense(this);
        NpcFormConfigBridge.Form form = isRacialFormActive()
                ? NpcFormConfigBridge.form(getRace(), getActiveRacialFormLevel()) : null;
        if (form != null) defense *= form.defense();
        NpcFormConfigBridge.Form kaioken = isKaiokenActive()
                ? NpcFormConfigBridge.kaioken(getKaiokenLevel()) : null;
        if (kaioken != null) defense *= kaioken.defense();
        return defense;
    }

    private void maintainOrAcquireTarget() {
        LivingEntity current = getTarget();
        if (current != null && (!current.isAlive() || !canAttack(current) || distanceToSqr(current) > 10000.0D)) {
            setTarget(null);
            current = null;
        }

        if (current != null) {
            if (!isDuelOpponent(current) && shouldRetreatFrom(current)) {
                beginRetreat(current);
            }
            return;
        }

        // Neutral fighters do not start trouble. HurtByTargetGoal still makes them
        // defend themselves, while scripted duels explicitly set an opponent.
        // Unaffiliated neutral fighters stay reactive. Affiliated neutral fighters must
        // still scan because generated faction relationships/reputation can make them hostile.
        if (getAlignment() == FighterAlignment.NEUTRAL && !isFactionMember()) return;

        // Optional ambient aggression is deliberately sparse. Explicit duels, encounters,
        // faction scenes and HurtByTarget self-defense bypass this gate because they set targets directly.
        double conflictRoll = LivingWorldConfig.ambientConflictRoll();
        if (conflictRoll <= 0.0D || getRandom().nextDouble() > conflictRoll) return;

        List<LivingEntity> nearby = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(58.0D, 24.0D, 58.0D),
                this::isProactiveTarget
        );

        nearby.stream()
                .min(Comparator.comparingDouble(this::targetPriorityScore))
                .ifPresent(target -> {
                    if (target instanceof ServerPlayer player && isFactionMember()
                            && FactionManager.shouldAttackPlayer(this, player)) {
                        PeacekeeperManager.markNpcAggressor(player, this);
                    }
                    setTarget(target);
                });
    }

    private double targetPriorityScore(LivingEntity target) {
        double distance = distanceToSqr(target);
        if (getAlignment() == FighterAlignment.GOOD && target instanceof AmbientFighterEntity bad
                && bad.getAlignment() == FighterAlignment.BAD && bad.getTarget() != null) {
            // A visible aggressor gets intervention priority over an idle threat.
            return distance * 0.28D;
        }
        return distance;
    }

    private boolean isProactiveTarget(LivingEntity target) {
        if (target == this || !target.isAlive() || !canAttack(target)) return false;
        if (target instanceof AmbientFighterEntity fighter && fighter.isRecovering()) return false;
        if (!isPowerAcceptableForProactiveFight(target)) return false;

        // A genuine personal friendship is behavior, not just a profile label. Even a BAD or
        // faction-hostile fighter will not proactively jump their own friend. Authored story
        // enemies remain explicit exceptions.
        if (target instanceof ServerPlayer player && getStoryRole() != STORY_ENEMY
                && isRememberedFor(player) && getMemoryRelationship() >= 35) {
            return false;
        }

        // Affiliated fighters obey their generated social map before broad alignment.
        if (isFactionMember() && target instanceof ServerPlayer player) {
            return FactionManager.shouldAttackPlayer(this, player);
        }
        if (isFactionMember() && target instanceof AmbientFighterEntity other && other.isFactionMember()) {
            if (FactionManager.areAllies(this, other)) return false;
            if (FactionManager.areEnemies(this, other)) return true;
            if (FactionManager.areRivals(this, other)) return isDuelOpponent(other);
        }

        return switch (getAlignment()) {
            case GOOD -> isThreatToEarth(target);
            case NEUTRAL -> false;
            case BAD -> isBadFighterVictim(target);
        };
    }

    private boolean isBadFighterVictim(LivingEntity target) {
        if (target instanceof Player player) return !player.isCreative() && !player.isSpectator();
        if (target instanceof Villager || target instanceof WanderingTrader || target instanceof IronGolem) return true;
        return target instanceof AmbientFighterEntity other && other.getAlignment() != FighterAlignment.BAD;
    }

    private boolean isThreatToEarth(LivingEntity target) {
        if (target instanceof AmbientFighterEntity other) return other.getAlignment() == FighterAlignment.BAD;

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (key == null) return false;

        // Vanilla monsters are safe to interpret as local threats. For DMZ itself,
        // stay conservative and target only known hostile troop families rather than
        // accidentally treating every saga character as evil.
        if (target instanceof Monster && "minecraft".equals(key.getNamespace())) return LivingWorldConfig.attackMinecraftMobs();
        if (!"dragonminez".equals(key.getNamespace())) return false;

        String path = key.getPath();
        return path.contains("friezasoldier")
                || path.contains("frieza_soldier")
                || path.contains("redribbon")
                || path.contains("red_ribbon")
                || path.contains("saibaman");
    }

    private boolean isPowerAcceptableForProactiveFight(LivingEntity target) {
        // Civilians and ordinary vanilla threats don't need a scouter calculation.
        if (!(target instanceof Player) && !(target instanceof DBSagasEntity) && !(target instanceof IBattlePower)) return true;

        double targetPower = estimateBattlePower(target);
        if (targetPower <= 0.0D) return true;
        return targetPower <= Math.max(1.0D, getBattlePower()) * getPersonality().overwhelmingPowerRatio();
    }

    private boolean shouldRetreatFrom(LivingEntity threat) {
        if (isDuelOpponent(threat) || (sanctionedMatchParticipant && isSanctionedOpponent(threat))) return false;

        float healthRatio = getMaxHealth() <= 0.0F ? 1.0F : getHealth() / getMaxHealth();
        float retreatMood = switch (ReactiveWorldManager.mood(this)) {
            case WEARY -> 1.55F;
            case WARY -> 1.30F;
            case SOMBER -> 1.15F;
            case IRRITATED -> 0.78F;
            case FOCUSED -> 0.90F;
            case UPBEAT -> 0.95F;
            case CONTENT -> 1.0F;
        };
        float moodBlend = ReactiveWorldManager.moodStrength(this) / 100.0F;
        float effectiveRetreatRatio = getPersonality().retreatHealthRatio() * (1.0F + (retreatMood - 1.0F) * moodBlend);
        if (healthRatio <= effectiveRetreatRatio) return true;

        double threatPower = estimateBattlePower(threat);
        if (threatPower <= 0.0D) return false;
        double dangerMood = switch (ReactiveWorldManager.mood(this)) {
            case WEARY -> 0.82D;
            case WARY -> 0.90D;
            case SOMBER -> 0.95D;
            case IRRITATED -> 1.12D;
            case FOCUSED -> 1.06D;
            default -> 1.0D;
        };
        return threatPower > Math.max(1.0D, getBattlePower()) * getPersonality().overwhelmingPowerRatio() * dangerMood;
    }

    private double estimateBattlePower(LivingEntity target) {
        if (target instanceof DBSagasEntity sagaEntity) {
            return Math.max(1, sagaEntity.getBattlePower());
        }

        if (target instanceof IBattlePower battlePowerEntity) {
            return Math.max(1, battlePowerEntity.getBattlePower());
        }

        if (target instanceof Player player) {
            final double[] result = {0.0D};
            player.getCapability(StatsCapability.INSTANCE).ifPresent(stats -> result[0] = stats.getBattlePowerExact());
            if (result[0] > 0.0D) return result[0];
        }

        // Conservative fallback for entities without DMZ battle-power data.
        return Math.max(100.0D, target.getMaxHealth() * 45.0D);
    }

    private void beginRetreat(LivingEntity threat) {
        if (threat instanceof net.minecraft.server.level.ServerPlayer player) {
            FighterMemoryManager.rememberEscape(player, this);
        }
        if (getSpeech().isEmpty()) speak(FighterDialogue.retreat(getRandom()), 55);
        ReactiveWorldManager.react(this, getHealth() < getMaxHealth() * 0.28F
                ? ReactiveWorldManager.Mood.WEARY : ReactiveWorldManager.Mood.WARY,
                "being pushed out of a fight", 900);
        FighterCombatDirector.reset(this);
        retreatTicks = getPersonality() == FighterPersonality.CAUTIOUS ? 180 : 120;
        retreatThreatId = threat.getUUID();
        setTarget(null);
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setFlying(false);
        getNavigation().stop();
    }

    private void tickRetreat() {
        setTarget(null);
        if (retreatTicks-- <= 0) {
            retreatThreatId = null;
            return;
        }

        LivingEntity threat = findLivingByUuid(retreatThreatId, 80.0D);
        if (threat == null || !threat.isAlive()) {
            retreatTicks = 0;
            retreatThreatId = null;
            return;
        }

        if (tickCount % 12 == 0) {
            double dx = getX() - threat.getX();
            double dz = getZ() - threat.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.01D) {
                dx = getRandom().nextDouble() - 0.5D;
                dz = getRandom().nextDouble() - 0.5D;
                length = Math.sqrt(dx * dx + dz * dz);
            }
            double scale = 20.0D / Math.max(0.01D, length);
            getNavigation().moveTo(getX() + dx * scale, getY(), getZ() + dz * scale, 1.28D);
        }
    }

    private LivingEntity findLivingByUuid(UUID uuid, double radius) {
        if (uuid == null) return null;
        List<LivingEntity> nearby = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(radius),
                entity -> uuid.equals(entity.getUUID())
        );
        return nearby.isEmpty() ? null : nearby.get(0);
    }

    private void spectateNearbyFight() {
        List<AmbientFighterEntity> fighters = level().getEntitiesOfClass(
                AmbientFighterEntity.class,
                getBoundingBox().inflate(24.0D, 12.0D, 24.0D),
                fighter -> fighter != this && fighter.isAlive() && fighter.getTarget() != null && !fighter.isDefeated()
        );
        fighters.stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .ifPresent(fighter -> getLookControl().setLookAt(fighter, 35.0F, 35.0F));
    }

    /**
     * Non-lethal Earth-fighter combat plus exaggerated impact spacing. The damage is
     * still DMZ's damage; Living World only adds a cinematic launch after selected
     * native combo hits so combat does not remain glued to one two-block square.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attackerEntity = source.getEntity();
        if (!level().isClientSide && SanctionedMatchGuard.isPostSparInvulnerable(this)) return false;
        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player
                && PlayerCreationSafety.isCreating(player)) return false;
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            if (attackerEntity instanceof ServerPlayer player && source.getDirectEntity() == player) {
                var stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
                if (stats != null) amount = (float)Math.max(amount, stats.getMeleeDamage());
            }
            amount = NpcDefenseCalculator.mitigate(amount, getDefenseStat(),
                    NpcDefensePenetrationManager.consume(this, attackerEntity));
            if (amount <= 0.0F) return false;
        }

        if (!level().isClientSide && isPostSparIncomingGrace(attackerEntity)) {
            SanctionedMatchGuard.notePostSparCleanup(this, "FORMER_OPPONENT_GRACE_HIT_BLOCKED");
            enforcePostSparPeace();
            return false;
        }

        // Player spars use one non-lethal damage authority. Third-party damage is ignored while the spar is active.
        if (!level().isClientSide && sanctionedMatchParticipant) {
            if (!isSanctionedOpponent(attackerEntity)) return false;
            if (isDefeated() || recoveryGraceTicks > 0) return false;
            if (SanctionedMatchGuard.interceptFinalNpcSparDamage(this, source, amount)) return true;
            // Legitimate non-finishing spar damage uses DMZ's real damage path, but bypasses
            // crime/faction-war logic and ordinary NPC concession/death handling below.
            return super.hurt(source, amount);
        }

        // A fighter who has just yielded to Friendly-Fist mercy cannot be juggled at one HP for
        // repeated trust/reputation gains. Friendly Fist off still restores ordinary lethal rules.
        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player
                && MercyManager.shouldIgnoreFriendlyFistHit(player, this)) {
            return false;
        }

        // Combat swings aimed through a travelling companion must never count as betrayal.
        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player
                && LivingBondManager.isCombatProtectedCompanion(player, this)) {
            return false;
        }

        // Travelling companions get a short accidental-friendly-fire grace sequence. This must
        // run before damage, meditation interruption, retaliation, faction reputation or crime
        // handling so an accidental click cannot silently ruin a long-running relationship.
        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player
                && LivingBondManager.protectCompanionFromFriendlyFire(player, this)) {
            return false;
        }

        // X-7's half-health squad call is evaluated from the incoming hit rather than waiting for
        // another AI tick. A large strike that crosses 50% can therefore never skip the trigger.
        if (!level().isClientSide && RedRibbonExperimentManager.isExperiment(this)
                && attackerEntity instanceof LivingEntity livingAttacker) {
            RedRibbonExperimentManager.onIncomingDamage(this, livingAttacker, amount);
        }

        if (!level().isClientSide) getPersistentData().putLong("LWLastDamageTime", level().getGameTime());
        if (!level().isClientSide && isMeditating()) stopMeditation(false);

        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player) {
            PeacekeeperManager.onPlayerAggression(player, this, source);
            if (WorldMenaceManager.isHerobrine(this)) WorldMenaceManager.onAttacked(this, player);
            else if (RedRibbonExperimentManager.isExperiment(this)) RedRibbonExperimentManager.onAttacked(this, player);
        }
        if (!level().isClientSide && attackerEntity instanceof ServerPlayer player && isFactionMember()) {
            long now = level().getGameTime();
            if (lastFactionRepHitTick == Long.MIN_VALUE || now - lastFactionRepHitTick >= 100L) {
                FactionManager.onPlayerHitMember(player, this);
                lastFactionRepHitTick = now;
            }
        }
        if (attackerEntity instanceof AmbientFighterEntity attacker) {
            if (!level().isClientSide && RedRibbonExperimentManager.isExperiment(attacker) && isFactionMember()) {
                RedRibbonExperimentManager.onFactionMemberAttacked(attacker, this);
            }
            if (isDefeated() || recoveryGraceTicks > 0) return false;

            // The spectacle branch intentionally gives procedural NPC-vs-NPC fights
            // more room to breathe. Player damage is untouched; only Living World
            // fighters trade reduced effective damage with one another.
            amount *= 1.04F;
            float defeatFloor = Math.max(1.0F, getMaxHealth() * 0.08F);
            if (getHealth() - amount <= defeatFloor) {
                // X-7 is a World Menace, not a factionless sparring partner. Its active slaughter
                // target follows an explicit kill/spare policy before ordinary LW concession logic.
                if (RedRibbonExperimentManager.isActiveSlaughterTarget(attacker, this)) {
                    if (RedRibbonExperimentManager.shouldSpareVictim(attacker, this)) {
                        enterDefeated(attacker);
                        RedRibbonExperimentManager.onVictimSpared(attacker, this);
                        return true;
                    }
                    return super.hurt(source, Math.max(amount, getHealth() + 1.0F));
                }

                // Rivalries/training remain non-lethal. Actual enemy organizations can
                // occasionally create real casualties so faction momentum, recruitment
                // and leadership succession have something meaningful to respond to.
                boolean sanctionedBout = sanctionedMatchParticipant && isSanctionedOpponent(attacker);
                boolean seriousFactionWar = !sanctionedBout && isFactionMember() && attacker.isFactionMember()
                        && FactionManager.areEnemies(attacker, this);
                float lethalChance = getFactionRole() == FactionRole.LEADER ? 0.22F
                        : getFactionRole() == FactionRole.LIEUTENANT ? 0.30F : 0.38F;
                if (!seriousFactionWar || getRandom().nextFloat() >= lethalChance) {
                    enterDefeated(attacker);
                    return true;
                }
                // Let DMZ/Minecraft process the real finishing hit. No resurrection is
                // performed for this individual; the organization recruits replacements.
                return super.hurt(source, Math.max(amount, getHealth() + 1.0F));
            }

            boolean damaged = super.hurt(source, amount);
            if (damaged) maybeApplyCinematicLaunch(attacker);
            return damaged;
        }

        // Ordinary environmental fire/lava can hurt and pressure Living World fighters, but cannot
        // delete a persistent person while no sanctioned spar owns the damage. Leave them at one HP
        // and extinguish the continuing burn; spar damage keeps its existing dedicated authority above.
        if (!level().isClientSide && source.is(DamageTypeTags.IS_FIRE)) {
            float safe = Math.max(0.0F, getHealth() - 1.0F);
            if (amount >= safe) {
                if (safe > 0.0F) super.hurt(source, safe);
                clearFire();
                return safe > 0.0F;
            }
        }
        return super.hurt(source, amount);
    }

    private void maybeApplyCinematicLaunch(AmbientFighterEntity attacker) {
        if (cinematicLaunchCooldown > 0 || attacker == null || !attacker.isComboing()) return;

        int combo = attacker.getComboId();
        boolean meteor = combo == DBSagasEntity.ComboType.METEOR_COMBINATION.getId();
        boolean air = combo == DBSagasEntity.ComboType.AIR.getId();
        boolean rapid = combo == DBSagasEntity.ComboType.RAPID_KICKS.getId();
        if (!meteor && !air && !(rapid && attacker.getRandom().nextFloat() < 0.28F)) return;

        // 0.6.10 keeps the stronger impact spacing while reducing repetition. Major native
        // combo hits should visibly relocate the fight instead of keeping both actors
        // glued to the same block.
        double horizontal = meteor ? 2.22D : air ? 1.76D : 1.08D;
        double vertical = meteor ? 1.02D : air ? 1.18D : 0.48D;
        if (attacker.getRank() == FighterRank.VETERAN) {
            horizontal *= 1.22D;
            vertical *= 1.18D;
        }

        double dx = getX() - attacker.getX();
        double dz = getZ() - attacker.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01D) length = 1.0D;
        setDeltaMovement(getDeltaMovement().add(dx / length * horizontal, vertical, dz / length * horizontal));
        cinematicLaunchCooldown = meteor ? 52 : air ? 42 : 34;
        FighterCombatDirector.onCinematicLaunch(attacker, this);
    }

    private void enterDefeated(AmbientFighterEntity victor) {
        if (sanctionedMatchParticipant && victor != null && isSanctionedOpponent(victor)) {
            enterSanctionedDefeated();
            return;
        }
        entityData.set(DEFEATED, true);
        speak(FighterDialogue.defeat(getRandom()), 65);
        FighterCombatDirector.reset(this);
        defeatedTicks = 160 + getRandom().nextInt(101); // 8-13 seconds.
        setHealth(Math.max(1.0F, getMaxHealth() * 0.08F));
        suppressCombatIntent();

        if (victor != null) {
            FighterLegacyManager.recordConcession(victor, this);
            FighterBattleGrowthManager.onConcession(victor, this);
            FighterDefeatRewardManager.onDefeated(this, victor);
            FactionManager.onConcessionVictory(victor, this);
            // A final launch makes the concession read as an actual finish rather than
            // a health-threshold toggle, then the victor deliberately disengages.
            double dx = getX() - victor.getX();
            double dz = getZ() - victor.getZ();
            double length = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
            setDeltaMovement(getDeltaMovement().add(dx / length * 1.35D, 0.58D, dz / length * 1.35D));
            FighterCombatDirector.onVictory(victor, this);
            FighterAftermathManager.beginConcession(victor, this);
            victor.setTarget(null);
            if (victor.isDuelOpponent(this)) victor.clearDuelOpponent();
        }
        clearDuelOpponent();
    }

    /** Friendly-Fist mercy uses the established defeated presentation without awarding a combat victory. */
    public void enterMercyDowned(ServerPlayer player) {
        if (level().isClientSide || player == null || isDefeated()) return;
        deathTime = 0;
        setPose(Pose.STANDING);
        entityData.set(DEFEATED, true);
        speak(getRandom().nextBoolean() ? "...You spared me." : "I yield. You could've finished that.", 72);
        FighterCombatDirector.reset(this);
        defeatedTicks = 180 + getRandom().nextInt(81); // 9-13 seconds, same readable concession family.
        setHealth(Math.max(1.0F, getMaxHealth() * 0.08F));
        suppressCombatIntent();
        clearDuelOpponent();
        MercyManager.onMercyDowned(player, this);
    }

    private void enterSanctionedDefeated() {
        // Health/pose are repaired BEFORE the custom defeated state is exposed. This avoids
        // the vanilla dying animation soft-lock after extreme one-shot DMZ damage.
        deathTime = 0;
        setPose(Pose.STANDING);
        setHealth(Math.max(1.0F, getMaxHealth() * 0.30F));
        entityData.set(DEFEATED, true);
        FighterCombatDirector.reset(this);
        defeatedTicks = 200;
        setFlying(false);
        setTarget(null);
        suppressCombatIntent();
        clearDuelOpponent();
    }

    private void tickDefeated() {
        suppressCombatIntent();
        if (defeatedTicks-- > 0) return;

        entityData.set(DEFEATED, false);
        recoveryGraceTicks = RECOVERY_GRACE_TICKS;
        setHealth(Math.max(1.0F, getMaxHealth() * 0.38F));
        setCanFly(getRank().canFly());
    }

    private void suppressCombatIntent() {
        setTarget(null);
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setKiCharge(false);
        setFlying(false);
        getNavigation().stop();
    }

    public void startDuel(AmbientFighterEntity opponent) {
        if (opponent == null || opponent == this) return;
        duelOpponentId = opponent.getUUID();
        setTarget(opponent);
    }

    public void clearDuelOpponent() {
        duelOpponentId = null;
        if (getTarget() instanceof AmbientFighterEntity other && other.getAlignment() == getAlignment()) {
            setTarget(null);
        }
    }

    public boolean isDuelOpponent(Entity entity) {
        return entity != null && duelOpponentId != null && duelOpponentId.equals(entity.getUUID());
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (!level().isClientSide && target instanceof ServerPlayer player
                && PlayerCreationSafety.isCreating(player)) {
            super.setTarget(null);
            return;
        }
        if (!level().isClientSide && target != null && !LivingWorldConfig.attackMinecraftMobs()
                && isVanillaMinecraftMob(target) && !isCompanionDefenseTarget(target)) {
            // Enforce the setting at the assignment boundary as well as in canAttack(). Native
            // HurtByTargetGoal and other mod AI can call setTarget directly, so a canAttack-only
            // gate was not strong enough to guarantee the OFF state.
            super.setTarget(null);
            return;
        }
        if (!level().isClientSide && target != null && isPostSparOpponent(target)) {
            SanctionedMatchGuard.notePostSparCleanup(this, "SET_TARGET_BLOCKED");
            super.setTarget(null);
            return;
        }
        if (!level().isClientSide && target != null
                && !com.dmzlivingworld.world.FactionRequestMissionManager.allowsMissionTarget(this, target)) {
            super.setTarget(null);
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!level().isClientSide && target instanceof ServerPlayer player
                && PlayerCreationSafety.isCreating(player)) return false;
        if (!level().isClientSide && target instanceof LivingEntity living
                && !LivingWorldConfig.attackMinecraftMobs() && isVanillaMinecraftMob(living)
                && !isCompanionDefenseTarget(living)) {
            // Final melee boundary: even if an external goal somehow retains a stale vanilla target
            // for the current tick, OFF means LW does not land the hit.
            return false;
        }
        if (isPostSparOpponent(target)) {
            SanctionedMatchGuard.notePostSparCleanup(this, "MELEE_REATTACK_BLOCKED");
            enforcePostSparPeace();
            return false;
        }
        // Vanilla mob_attack damage is difficulty-scaled by ServerPlayer and becomes zero on
        // Peaceful. Dragon Mine Z already ships a physical strike damage type whose data definition
        // uses scaling=never, so use that native source only for this otherwise-valid Peaceful hit.
        // Every other difficulty remains on R12.2's exact known-good super.doHurtTarget path.
        if (!level().isClientSide && level().getDifficulty() == Difficulty.PEACEFUL && target instanceof ServerPlayer player) {
            float damage = (float)Math.max(0.0D, getAttributeValue(Attributes.ATTACK_DAMAGE));
            if (damage <= 0.0F) return false;
            return player.hurt(MainDamageTypes.strikeAttack(level(), this, "generic"), damage);
        }
        return super.doHurtTarget(target);
    }

    /**
     * Debug-only direct melee probe. Temporarily suppresses only this fighter/player pair's
     * post-spar peace marker for the synchronous native hit call, then restores the exact peace
     * state immediately. Normal AI/combat never calls this method, so the real post-spar safeguard
     * remains unchanged.
     */
    public boolean debugForceMeleeHit(ServerPlayer player) {
        if (player == null || level().isClientSide) return false;
        UUID savedOpponent = postSparOpponentId;
        int savedPeace = postSparPeaceTicks;
        int savedGrace = postSparIncomingGraceTicks;
        boolean bypassedPeace = isPostSparOpponent(player);
        try {
            if (bypassedPeace) {
                postSparOpponentId = null;
                postSparPeaceTicks = 0;
                postSparIncomingGraceTicks = 0;
            }
            return super.doHurtTarget(player);
        } finally {
            if (bypassedPeace) {
                postSparOpponentId = savedOpponent;
                postSparPeaceTicks = savedPeace;
                postSparIncomingGraceTicks = savedGrace;
                enforcePostSparPeace();
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof ServerPlayer player && PlayerCreationSafety.isCreating(player)) return false;
        if (!com.dmzlivingworld.world.FactionRequestMissionManager.allowsMissionTarget(this, target)) return false;
        if (isPostSparOpponent(target)) return false;
        if (isDefeated() || isCaptive() || isNonCombatant() || isMeditating()) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        // Global "Attack Minecraft mobs" means exactly that: when disabled, LW fighters do not
        // proactively or reactively fight vanilla non-player entities. The one intentional exception
        // is a travelling companion protecting its owner from the vanilla mob that attacked them.
        if (!LivingWorldConfig.attackMinecraftMobs() && isVanillaMinecraftMob(target) && !isCompanionDefenseTarget(target)) return false;
        if (sanctionedMatchParticipant && !isSanctionedOpponent(target)) return false;
        if (target instanceof AmbientFighterEntity other && other.isSanctionedMatchParticipant()
                && !other.isSanctionedOpponent(this)) return false;
        // An explicit request roster is a temporary operational relationship. Once the shared
        // mission gate above approves this exact target, do not let long-term Rival/Neutral
        // diplomacy veto the battle that the request actually committed both residents to fight.
        if (com.dmzlivingworld.world.FactionRequestMissionManager.isAssigned(this)) return super.canAttack(target);
        if (target instanceof AmbientFighterEntity other) {
            if (other.isDefeated() || other.isRecovering()) return false;
            // Peacekeeper interventions are an explicit third-party law-enforcement scene.
            // They must be allowed to engage BAD fighters even when the factions' long-term
            // diplomatic relation is only Rival/Neutral, and BAD fighters may fight back.
            if ((getStoryRole() == STORY_PEACEKEEPER && other.getAlignment() == FighterAlignment.BAD)
                    || (other.getStoryRole() == STORY_PEACEKEEPER && getAlignment() == FighterAlignment.BAD))
                return super.canAttack(target);
            if (isFactionMember() && other.isFactionMember()) {
                if (getFactionId().equals(other.getFactionId()) && !isDuelOpponent(other)) return false;
                if (FactionManager.areAllies(this, other) && !isDuelOpponent(other)) return false;
                if (FactionManager.areEnemies(this, other) || isDuelOpponent(other)) return super.canAttack(target);
                // Two affiliated groups with no hostile relationship do not start accidental infighting.
                return false;
            }
            if (other.getAlignment() == getAlignment() && !isDuelOpponent(other)) return false;
        }
        return super.canAttack(target);
    }

    private void enforceMinecraftMobCombatGate() {
        if (LivingWorldConfig.attackMinecraftMobs()) return;
        LivingEntity target = getTarget();
        if (target == null || !isVanillaMinecraftMob(target) || isCompanionDefenseTarget(target)) return;
        setTarget(null);
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setKiCharge(false);
        setAggressive(false);
    }

    private static boolean isVanillaMinecraftMob(LivingEntity target) {
        if (target == null || target instanceof Player) return false;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key != null && "minecraft".equals(key.getNamespace());
    }

    private boolean isCompanionDefenseTarget(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) return false;
        if (!getPersistentData().hasUUID("LWCompanionOwner")) return false;
        UUID ownerId = getPersistentData().getUUID("LWCompanionOwner");
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return false;
        UUID companionId = LivingBondManager.companionId(owner);
        if (companionId == null || !companionId.equals(getUUID())) return false;
        if (owner.getLastHurtByMob() == target || owner.getLastHurtMob() == target) return true;
        return target instanceof Mob mob && mob.getTarget() == owner;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof AmbientFighterEntity other) {
            if (isFactionMember() && other.isFactionMember()) {
                if (getFactionId().equals(other.getFactionId())) return !isDuelOpponent(other);
                return FactionManager.areAllies(this, other) && !isDuelOpponent(other);
            }
            if (other.getAlignment() == getAlignment()) return !isDuelOpponent(other);
        }
        return super.isAlliedTo(entity);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);

        // An active fusion partner is physically retained only so its exact persistent LW state can
        // be restored later. It is not an interactable world resident while fused.
        if (com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(this)) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Shift remains the default modifier and therefore also supports vanilla's normal entity
        // interaction packet. Other configured modifiers arrive through FighterInteractPacket.
        if (!player.isShiftKeyDown()) return super.mobInteract(player, hand);
        return performFighterInteraction(player, hand);
    }

    public InteractionResult performFighterInteraction(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(this)) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        ItemStack held = player.getItemInHand(hand);

        if (isSanctionedMatchParticipant() || getTarget() != null) {
            if (getTarget() != player && !level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.literal(
                        "The NPC is fighting! You can't interact right now!").withStyle(ChatFormatting.RED), false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // World Menace inspection is deliberately forgiving: sneak + right-click opens the
        // dossier even with a held item. Menaces never fall through to ordinary bonds/gifts/social actions.
        if (WorldMenaceManager.isWorldMenace(this)) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (WorldMenaceManager.isHerobrine(this)) WorldMenaceManager.markSpotted(serverPlayer, this);
                else if (RedRibbonExperimentManager.isExperiment(this)) RedRibbonExperimentManager.markSpotted(serverPlayer, this);
                FighterInspectionManager.inspect(serverPlayer, this, FighterInspectionManager.isScouter(held) || FighterInspectionManager.hasWornScouter(serverPlayer));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // A request participant stays inspectable, but the request owns every unrelated sneak action.
        // This must precede gifts/Senzu/bond handling so an assigned receiver cannot accidentally turn
        // requested supplies into a social interaction and a Patrol member cannot be pulled off duty.
        if (com.dmzlivingworld.world.FactionRequestMissionManager.isRequestActionLocked(this)) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                FighterInspectionManager.inspect(serverPlayer, this,
                        FighterInspectionManager.isScouter(held) || FighterInspectionManager.hasWornScouter(serverPlayer));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Existing companion/bond actions keep priority on sneak + empty hand.
        // If there is no pending bond action, the same deliberate interaction opens the profile.
        if (held.isEmpty()) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer
                    && LivingBondManager.tryHandleInteraction(serverPlayer, this)) {
                return InteractionResult.CONSUME;
            }
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                FighterInspectionManager.inspect(serverPlayer, this, FighterInspectionManager.hasWornScouter(serverPlayer));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Sneak + scouter opens the detailed profile while normal scouter use remains DMZ-native.
        if (FighterInspectionManager.isScouter(held)) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                FighterInspectionManager.inspect(serverPlayer, this, true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // Senzu is a meaningful care interaction, not equipment. Injured fighters accept one,
        // recover, thank the player in-character and remember the help. A short bond cooldown
        // prevents Senzu farming from becoming the fastest friendship mechanic.
        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(held.getItem());
        boolean senzu = heldId != null && "dragonminez".equals(heldId.getNamespace())
                && heldId.getPath().toLowerCase(java.util.Locale.ROOT).contains("senzu");
        if (senzu) {
            if (level().isClientSide) return InteractionResult.sidedSuccess(true);
            if (player instanceof ServerPlayer serverPlayer) {
                if (getHealth() >= getMaxHealth() - 0.5F) {
                    speak("I'm okay. Save it for when someone needs it.", 72);
                    return InteractionResult.CONSUME;
                }
                if (!serverPlayer.getAbilities().instabuild) held.shrink(1);
                heal(getMaxHealth());
                long now = serverPlayer.serverLevel().getGameTime();
                String key = "LWSenzuBond_" + serverPlayer.getUUID();
                long last = getLegacyData().getLong(key);
                boolean bondReward = last <= 0L || now - last >= 20L * 60L * 10L;
                if (bondReward) {
                    getLegacyData().putLong(key, now);
                    FighterMemoryManager.strengthenRelationship(serverPlayer, this, 3,
                            com.dmzlivingworld.world.FighterRelationshipManager.BondEvent.GIFT, "Shared a Senzu Bean");
                }
                boolean close = isRememberedFor(serverPlayer) && getMemoryRelationship() >= 60;
                speak(FighterDialogue.senzuThanks(getRandom(), getPersonality(), close), 84);
                return InteractionResult.CONSUME;
            }
        }

        // Sneak + supported equipment is an intentional gift.
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer
                && FighterArsenalManager.tryGift(serverPlayer, this, held, false)) {
            serverPlayer.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(getFighterName() + " accepted the equipment. Equipment: "
                            + FighterArsenalManager.summary(this)).withStyle(ChatFormatting.GRAY)), false);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private void randomizeNativeAppearance(RandomSource random) {
        FighterRace race = getRace();
        // Keep applicable races visibly mixed rather than letting a small test sample
        // accidentally look single-gender. 46% female is close to even while still random.
        entityData.set(GENDER, race.gendered() && random.nextFloat() < 0.46F ? 1 : 0);
        float scale = rollNaturalScale(random, race, isFemale());
        entityData.set(DISPLAY_SCALE, scale);
        setScaleVal(scale);
        entityData.set(HAIR_COLOR, pick(random, HAIR_COLORS));
        String primaryEye = pick(random, EYE_COLORS);
        entityData.set(EYE1_COLOR, primaryEye);
        // Heterochromia is a distinctive trait, not the default. Roughly four percent of
        // naturally generated fighters receive a genuinely different second eye colour.
        String secondaryEye = primaryEye;
        if (random.nextFloat() < 0.04F && EYE_COLORS.length > 1) {
            for (int tries = 0; tries < 8 && secondaryEye.equals(primaryEye); tries++) secondaryEye = pick(random, EYE_COLORS);
        }
        entityData.set(EYE2_COLOR, secondaryEye);

        switch (race) {
            case HUMAN, SAIYAN -> {
                entityData.set(BODY_TYPE, 1 + random.nextInt(2));
                entityData.set(EYES_TYPE, random.nextInt(13));
                entityData.set(NOSE_TYPE, random.nextInt(6));
                entityData.set(MOUTH_TYPE, random.nextInt(9));
                entityData.set(HAIR_ID, 1 + random.nextInt(Math.max(1, HairManager.getPresetCount())));
                entityData.set(OUTFIT, LivingWorldConfig.canUseClothes().contains(race.dmzId()) ? random.nextInt(22) : 0);
                String skin = pick(random, SKIN_COLORS);
                entityData.set(BODY_COLOR, skin);
                entityData.set(BODY_COLOR2, race == FighterRace.SAIYAN ? "#572117" : skin);
                entityData.set(BODY_COLOR3, skin);
            }
            case NAMEKIAN -> {
                int bodyType = random.nextInt(3);
                entityData.set(BODY_TYPE, bodyType);
                entityData.set(EYES_TYPE, random.nextInt(5));
                entityData.set(NOSE_TYPE, random.nextInt(2));
                entityData.set(MOUTH_TYPE, random.nextInt(2));
                entityData.set(HEAD_BONE, random.nextFloat() < 0.50F ? 0 : 1 + random.nextInt(2));
                entityData.set(HAIR_ID, 0);
                entityData.set(HAIR_COLOR, pick(random, NAMEK_LIGHT_GREEN));
                entityData.set(OUTFIT, LivingWorldConfig.canUseClothes().contains(race.dmzId()) ? random.nextInt(44) : 0);
                String green = pick(random, NAMEK_GREEN), red = pick(random, NAMEK_ACCENT), pink = pick(random, NAMEK_PINK);
                entityData.set(BODY_COLOR, green);
                entityData.set(BODY_COLOR2, red);
                entityData.set(BODY_COLOR3, pink);
            }
            case MAJIN -> {
                entityData.set(BODY_TYPE, random.nextInt(3));
                entityData.set(EYES_TYPE, random.nextInt(3));
                entityData.set(NOSE_TYPE, random.nextInt(2));
                entityData.set(MOUTH_TYPE, random.nextInt(2));
                entityData.set(HEAD_BONE, isFemale() ? 0 : random.nextInt(3));
                entityData.set(OUTFIT, LivingWorldConfig.canUseClothes().contains(race.dmzId()) ? random.nextInt(22) : 0);
                int majinColorRoll = random.nextInt(100);
                boolean pinkVariant = majinColorRoll < 70;
                String main = pinkVariant ? colorVariant(random, 0xFFA4FF, 30)
                        : majinColorRoll < 90 ? colorVariant(random, 0x9CFF9C, 38)
                        : randomColor(random);
                String secondary = pinkVariant ? main
                        : shadeColor(main, random.nextBoolean() ? 0.72D : 1.28D);
                entityData.set(HAIR_ID, isFemale() ? 1 + random.nextInt(Math.max(1, HairManager.getPresetCount())) : 0);
                entityData.set(HAIR_COLOR, main);
                entityData.set(BODY_COLOR, main);
                entityData.set(BODY_COLOR2, secondary);
                entityData.set(BODY_COLOR3, main);
                String redEye = pick(random, MAJIN_EYE_RED);
                entityData.set(EYE1_COLOR, redEye);
                entityData.set(EYE2_COLOR, redEye);
            }
            case FROST_DEMON -> {
                entityData.set(BODY_TYPE, random.nextInt(3));
                entityData.set(EYES_TYPE, random.nextInt(6));
                entityData.set(NOSE_TYPE, random.nextInt(2));
                entityData.set(MOUTH_TYPE, random.nextInt(2));
                entityData.set(HAIR_ID, 0);
                entityData.set(OUTFIT, 0);
                entityData.set(BODY_COLOR, pick(random, FROST_MAIN));
                entityData.set(BODY_COLOR2, pick(random, FROST_SECOND));
                entityData.set(BODY_COLOR3, pick(random, FROST_ACCENT));
            }
            case BIO_ANDROID -> {
                entityData.set(BODY_TYPE, random.nextInt(3));
                entityData.set(EYES_TYPE, 0);
                entityData.set(NOSE_TYPE, 0);
                entityData.set(MOUTH_TYPE, 0);
                entityData.set(HAIR_ID, 0);
                entityData.set(OUTFIT, 0);
                entityData.set(BODY_COLOR, pick(random, BIO_MAIN));
                entityData.set(BODY_COLOR2, pick(random, BIO_SECOND));
                entityData.set(BODY_COLOR3, pick(random, BIO_ACCENT));
            }
        }
    }

    private static float rollNaturalScale(RandomSource random, FighterRace race, boolean female) {
        // Wider natural range than the old near-uniform silhouettes. Extremes are intentionally
        // uncommon because the triangular roll below still clusters most fighters near average.
        float min = switch (race) {
            case HUMAN, SAIYAN -> female ? 0.72F : 0.74F;
            case NAMEKIAN -> 0.76F;
            case MAJIN -> female ? 0.70F : 0.74F;
            case FROST_DEMON -> 0.73F;
            case BIO_ANDROID -> 0.76F;
        };
        float max = switch (race) {
            case HUMAN, SAIYAN -> female ? 1.24F : 1.34F;
            case NAMEKIAN -> 1.39F;
            case MAJIN -> female ? 1.27F : 1.38F;
            case FROST_DEMON -> 1.31F;
            case BIO_ANDROID -> 1.39F;
        };
        // Triangular distribution: extremes exist, but most people stay around average.
        float t = (random.nextFloat() + random.nextFloat()) * 0.5F;
        return min + (max - min) * t;
    }

    private static String pick(RandomSource random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static String randomColor(RandomSource random) {
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    private static String colorVariant(RandomSource random, int rgb, int variation) {
        int red = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + random.nextInt(variation * 2 + 1) - variation));
        int green = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + random.nextInt(variation * 2 + 1) - variation));
        int blue = Math.max(0, Math.min(255, (rgb & 255) + random.nextInt(variation * 2 + 1) - variation));
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    /** Range produced by colorVariant(random, 0xFFA4FF, 30), including channel clamping. */
    private static boolean isMajinPinkVariant(String color) {
        try {
            String value = color != null && color.startsWith("#") ? color.substring(1) : color;
            int rgb = Integer.parseInt(value, 16);
            int red = (rgb >> 16) & 255, green = (rgb >> 8) & 255, blue = rgb & 255;
            return red >= 225 && green >= 134 && green <= 194 && blue >= 225;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String shadeColor(String color, double factor) {
        try {
            String value = color != null && color.startsWith("#") ? color.substring(1) : color;
            int rgb = Integer.parseInt(value, 16);
            int red = Math.max(0, Math.min(255, (int)Math.round(((rgb >> 16) & 255) * factor)));
            int green = Math.max(0, Math.min(255, (int)Math.round(((rgb >> 8) & 255) * factor)));
            int blue = Math.max(0, Math.min(255, (int)Math.round((rgb & 255) * factor)));
            return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", red, green, blue);
        } catch (RuntimeException ignored) {
            return color;
        }
    }

    public FighterAlignment getAlignment() { return FighterAlignment.byId(entityData.get(ALIGNMENT)); }
    public FighterRank getRank() { return FighterRank.byId(entityData.get(RANK)); }

    /** Promote through lived experience without changing battle power or identity. */
    public boolean promoteTo(FighterRank next) {
        if (next == null || level().isClientSide || next.id() <= getRank().id()) return false;
        FighterRank previous = getRank();
        entityData.set(RANK, next.id());
        combatConfigured = false;
        configureCombatProfile(false);
        recordLegacyEvent("Promoted from " + previous.displayName() + " to " + next.displayName());
        if (getSpeech().isEmpty()) speak(next == FighterRank.VETERAN ? "I've come a long way." : "I'm getting stronger.", 64);
        return true;
    }
    public FighterPersonality getPersonality() { return FighterPersonality.byId(entityData.get(PERSONALITY)); }
    public FighterRace getRace() { return FighterRace.byId(entityData.get(RACE)); }
    public boolean isDeadSoul() { return entityData.get(DEAD_SOUL); }
    public void setDeadSoul(boolean dead) {
        entityData.set(DEAD_SOUL, dead);
        if (dead) getPersistentData().putBoolean("LWDeadSoul", true);
        else getPersistentData().remove("LWDeadSoul");
    }
    public FighterArchetype getArchetype() { return FighterArchetype.byId(entityData.get(ARCHETYPE)); }
    public String getFighterName() { return entityData.get(FIGHTER_NAME); }
    @Override
    public Component getName() {
        String name = getFighterName();
        return name == null || name.isBlank() ? super.getName() : Component.literal(name);
    }

    @Override
    public void checkDespawn() {
        // Normal spectators remain ignored, but the explicit LW debug-spectate subject is protected
        // so attaching the camera can never be the reason an NPC suddenly despawns.
        if (!level().isClientSide && (com.dmzlivingworld.world.FighterDebugSpectateManager.isSpectated(this)
                || !level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(LivingWorldConfig.npcDespawnProtectionRadius()), p -> !p.isSpectator()).isEmpty())) return;
        super.checkDespawn();
    }

    private void quiesceForVanillaDeath() {
        getNavigation().stop();
        setTarget(null);
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setKiCharge(false);
        setFlying(false);
        setFlyingFast(false);
        setNoGravity(false);
        setSprinting(false);
        socialPlayerApproach = false;
        socialPowerDisplay = false;
        socialLifeActivity = false;
        ambientFlightActivity = false;
        setAmbientPose(0);
        entityData.set(SPEECH, "");
        speechTicks = 0;
        FighterAmbientActivityManager.cancelFor(this);
        FighterNpcSocialManager.cancelFor(this);
    }

    public boolean isSanctionedMatchParticipant() { return sanctionedMatchParticipant; }
    public boolean hasSanctionedOpponent() { return sanctionedMatchParticipant && sanctionedOpponentId != null; }
    public boolean isSanctionedOpponent(Entity entity) {
        return entity != null && sanctionedOpponentId != null && sanctionedOpponentId.equals(entity.getUUID());
    }

    public void beginSanctionedMatch(Entity opponent) {
        if (opponent == null || opponent == this) return;
        FighterAmbientActivityManager.cancel(this);
        socialPlayerApproach = false;
        socialPowerDisplay = false;
        socialLifeActivity = false;
        ambientFlightActivity = false;
        retreatTicks = 0;
        retreatThreatId = null;
        if (isMeditating() || isPreparingMeditation()) stopMeditation(false);
        getNavigation().stop();
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setAggressive(false);
        setKiCharge(false);
        setFlyingFast(false);
        sanctionedMatchParticipant = true;
        sanctionedOpponentId = opponent.getUUID();
        recoveryGraceTicks = 0;
        entityData.set(DEFEATED, false);
        defeatedTicks = 0;
        restoreSanctionedLivingState(false);
        if (opponent instanceof AmbientFighterEntity fighter) duelOpponentId = fighter.getUUID();
        setTarget(opponent instanceof LivingEntity living ? living : null);
    }

    public boolean maintainSanctionedMatch(LivingEntity opponent) {
        if (!sanctionedMatchParticipant || sanctionedOpponentId == null || opponent == null
                || !sanctionedOpponentId.equals(opponent.getUUID()) || !opponent.isAlive()
                || isDefeated() || recoveryGraceTicks > 0) return false;
        FighterAmbientActivityManager.cancel(this);
        socialPlayerApproach = false; socialPowerDisplay = false; socialLifeActivity = false; ambientFlightActivity = false;
        retreatTicks = 0; retreatThreatId = null;
        if (isMeditating() || isPreparingMeditation()) stopMeditation(false);
        if (getTarget() == opponent) return false;
        FighterCombatDirector.reset(this);
        setTarget(opponent);
        return true;
    }

    public void endSanctionedMatch() {
        // Sanctioned combat is non-lethal by contract. Clear vanilla lingering fire at the
        // central NPC cleanup boundary so every existing spar/practice exit path inherits it.
        clearFire();
        sanctionedMatchParticipant = false;
        sanctionedOpponentId = null;
        clearDuelOpponent();
        FighterCombatDirector.reset(this);
        entityData.set(DEFEATED, false);
        defeatedTicks = 0; recoveryGraceTicks = 0;
        setTarget(null);
        restoreSanctionedLivingState(false);
    }

    public void beginPostSparPeace(Entity opponent, int ticks) {
        if (opponent == null || opponent == this) return;
        postSparOpponentId = opponent.getUUID();
        postSparPeaceTicks = Math.max(postSparPeaceTicks, Math.max(1, ticks));
        postSparIncomingGraceTicks = Math.max(postSparIncomingGraceTicks, Math.min(20, postSparPeaceTicks));
        hardResetPostSparState();
        enforcePostSparPeace();
    }

    public boolean isPostSparOpponent(Entity entity) {
        return postSparPeaceTicks > 0 && entity != null && postSparOpponentId != null && postSparOpponentId.equals(entity.getUUID());
    }
    public boolean isPostSparIncomingGrace(Entity entity) { return postSparIncomingGraceTicks > 0 && isPostSparOpponent(entity); }
    public int getPostSparPeaceTicks() { return Math.max(0, postSparPeaceTicks); }
    public int getPostSparIncomingGraceTicks() { return Math.max(0, postSparIncomingGraceTicks); }
    public UUID getPostSparOpponentId() { return postSparOpponentId; }

    public void prepareDebugSpar(ServerPlayer player) {
        if (player == null || level().isClientSide) return;
        postSparOpponentId = null; postSparPeaceTicks = 0; postSparIncomingGraceTicks = 0;
        sanctionedMatchParticipant = false; sanctionedOpponentId = null; clearDuelOpponent();
        entityData.set(DEFEATED, false); defeatedTicks = 0; recoveryGraceTicks = 0; retreatTicks = 0; retreatThreatId = null;
        FighterAmbientActivityManager.cancel(this);
        socialPlayerApproach = false; socialPowerDisplay = false; socialLifeActivity = false; ambientFlightActivity = false;
        if (isMeditating() || isPreparingMeditation()) stopMeditation(false);
        setLastHurtByMob(null); setLastHurtMob(null); super.setTarget(null);
        hardResetPostSparState(); restoreSanctionedLivingState(true);
        getLegacyData().remove("LWSparCooldown_" + player.getUUID());
    }

    private void hardResetPostSparState() {
        FighterCombatDirector.reset(this); interruptCombo(); stopCasting(); setAttacking(false); setAggressive(false);
        setKiCharge(false); setZanzokenState(false); setEvading(false); setFlyingFast(false);
        setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE); getNavigation().stop(); setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private void enforcePostSparPeace() {
        if (postSparPeaceTicks <= 0 || postSparOpponentId == null) return;
        LivingEntity target = getTarget();
        if (target != null && postSparOpponentId.equals(target.getUUID())) {
            SanctionedMatchGuard.notePostSparCleanup(this, "TARGET_REACQUIRED");
            hardResetPostSparState(); super.setTarget(null);
        }
        LivingEntity lastHurtBy = getLastHurtByMob();
        if (lastHurtBy != null && postSparOpponentId.equals(lastHurtBy.getUUID())) {
            SanctionedMatchGuard.notePostSparCleanup(this, "REVENGE_REACQUIRED"); setLastHurtByMob(null);
        }
        LivingEntity lastHurt = getLastHurtMob();
        if (lastHurt != null && postSparOpponentId.equals(lastHurt.getUUID())) {
            SanctionedMatchGuard.notePostSparCleanup(this, "ATTACK_MEMORY_REACQUIRED"); setLastHurtMob(null);
        }
    }

    public void concedeSanctionedMatch() {
        if (!sanctionedMatchParticipant) return;
        enterSanctionedDefeated();
    }

    public void restoreSanctionedLivingState(boolean fullHeal) {
        deathTime = 0;
        setPose(Pose.STANDING);
        stopUsingItem();
        if (fullHeal) setHealth(getMaxHealth());
        else if (getHealth() <= 0.0F) setHealth(Math.max(1.0F, getMaxHealth() * 0.30F));
    }

    public boolean isDefeated() { return entityData.get(DEFEATED); }
    public boolean isRecovering() { return recoveryGraceTicks > 0 || isDefeated(); }
    public boolean isCaptive() { return entityData.get(CAPTIVE); }
    public String getSpeech() { return entityData.get(SPEECH); }
    public boolean isRetreating() { return retreatTicks > 0; }
    public boolean isAwakened() { return entityData.get(AWAKENED); }
    public boolean isAwakening() { return awakeningTicks > 0 || isTransforming(); }
    public float getDisplayScale() { return entityData.get(DISPLAY_SCALE); }
    public String genderLabel() { return getRace().gendered() ? (isFemale() ? "Female" : "Male") : "—"; }

    public void setPersonality(FighterPersonality personality) {
        entityData.set(PERSONALITY, personality.id());
    }

    public boolean isFemale() { return entityData.get(GENDER) == 1; }
    public void forceGender(boolean female) { if (getRace().gendered()) entityData.set(GENDER, female ? 1 : 0); }
    public int getBodyType() { return entityData.get(BODY_TYPE); }
    public int getEyesType() { return entityData.get(EYES_TYPE); }
    public int getNoseType() { return entityData.get(NOSE_TYPE); }
    public int getMouthType() { return entityData.get(MOUTH_TYPE); }
    public int getHairId() { return entityData.get(HAIR_ID); }
    public int getHeadBone() { return Math.floorMod(entityData.get(HEAD_BONE), 3); }
    public int getOutfit() { return entityData.get(OUTFIT); }
    /** Persistent Living World cosmetic evolution; bounded to native DMZ preset hairs. */
    public void setHairIdForLivingWorld(int hairId) {
        int max = Math.max(1, HairManager.getPresetCount());
        entityData.set(HAIR_ID, Math.max(0, Math.min(max, hairId)));
    }
    public String getBodyColor() { return entityData.get(BODY_COLOR); }
    public String getBodyColor2() { return entityData.get(BODY_COLOR2); }
    public String getBodyColor3() { return entityData.get(BODY_COLOR3); }
    public String getHairColor() { return entityData.get(HAIR_COLOR); }
    public String getEye1Color() { return entityData.get(EYE1_COLOR); }
    public String getEye2Color() { return entityData.get(EYE2_COLOR); }
    public int getReactiveMoodVisual() { return entityData.get(REACTIVE_MOOD_VISUAL); }
    public int getReactiveMoodStrength() { return entityData.get(REACTIVE_MOOD_STRENGTH); }
    public void setReactiveMoodVisual(int moodId, int strength) {
        entityData.set(REACTIVE_MOOD_VISUAL, Math.max(0, Math.min(6, moodId)));
        entityData.set(REACTIVE_MOOD_STRENGTH, Math.max(0, Math.min(100, strength)));
    }
    public String getFactionId() { return entityData.get(FACTION_ID); }
    public boolean isFactionMember() { return !getFactionId().isBlank(); }
    public boolean isFactionLeader() { return entityData.get(FACTION_LEADER); }
    public FactionRole getFactionRole() { return FactionRole.byId(entityData.get(FACTION_ROLE)); }
    public String getFactionDisplayName() { return entityData.get(FACTION_NAME); }
    public String getFactionTitle() { return entityData.get(FACTION_TITLE); }
    public boolean isAuraFlared() { return entityData.get(AURA_FLARED); }
    public boolean isNonCombatant() { return entityData.get(NON_COMBATANT); }
    public String getWantedId() { return entityData.get(WANTED_ID); }
    public int getWantedLevel() { return entityData.get(WANTED_LEVEL); }
    public boolean isWanted() { return getWantedLevel() > 0; }
    public String getWantedCrime() { return entityData.get(WANTED_CRIME); }
    public boolean isRegionalPresence() { return regionalPresence; }
    public int getFactionMerit() { return factionMerit; }
    public int getFoodSupplies() { return foodSupplies; }
    public int getSenzuBeans() { return senzuBeans; }
    public void receiveSenzuBean() { senzuBeans = Math.min(4, senzuBeans + 1); }
    public void setFlightUnlockedForDebug(boolean unlocked) { entityData.set(FLIGHT_UNLOCKED, unlocked); setCanFly(unlocked && !isNonCombatant()); }
    public boolean hasParty() { return partyId != null; }
    public boolean isPartyCaptain() { return partyCaptain; }
    public UUID getPartyId() { return partyId; }
    public boolean sameParty(AmbientFighterEntity other) {
        return other != null && partyId != null && partyId.equals(other.partyId);
    }

    public void setNonCombatant(boolean value) {
        entityData.set(NON_COMBATANT, value);
        if (value) {
            suppressCombatIntent();
            setCanFly(false);
        } else {
            setCanFly(hasFlightUnlocked());
        }
    }

    public void markWanted(String wantedId, int level, String crime) {
        entityData.set(WANTED_ID, wantedId == null ? "" : wantedId);
        entityData.set(WANTED_LEVEL, Math.max(0, Math.min(5, level)));
        entityData.set(WANTED_CRIME, crime == null ? "" : crime);
        if (level > 0) setPersistenceRequired();
    }

    public void setFighterName(String name) {
        if (name != null && !name.isBlank()) entityData.set(FIGHTER_NAME, name);
    }

    public void addFactionMerit(int amount) { factionMerit = Math.max(0, factionMerit + Math.max(0, amount)); }

    public void setFactionRole(FactionRole role) {
        if (role == null || !isFactionMember()) return;
        entityData.set(FACTION_ROLE, role.id());
        entityData.set(FACTION_LEADER, role == FactionRole.LEADER);
        if (role.ordinal() >= FactionRole.LIEUTENANT.ordinal()) setPersistenceRequired();
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            WorldFaction faction = FactionManager.byId(serverLevel, getFactionId());
            if (faction != null) {
                entityData.set(FACTION_NAME, faction.name());
                entityData.set(FACTION_TITLE, faction.roleTitle(role));
                applyFactionUniform(faction, role);
            }
        }
    }

    /** Keep ordinary/strength/Ki practice visually aura-free; custom LW Ki particles are independent. */
    public void suppressActivityAura() {
        if (level().isClientSide) return;
        setKiCharge(false);
        auraFlareTicks = 0;
        entityData.set(AURA_FLARED, false);
    }

    public void flareAura(int ticks) {
        if (level().isClientSide) return;
        boolean starting = !entityData.get(AURA_FLARED) && !isCharge() && !isTransforming();
        auraFlareTicks = Math.max(auraFlareTicks, Math.max(20, ticks));
        entityData.set(AURA_FLARED, true);
        if (starting) {
            // Use DMZ's own aura-start sound and DMZ's saga aura rendering pass.
            level().playSound(null, blockPosition(), MainSounds.AURA_START.get(), SoundSource.HOSTILE, 0.85F, 0.96F + getRandom().nextFloat() * 0.08F);
        }
    }

    public void assignFaction(WorldFaction faction, boolean leader, UUID newPartyId, boolean captain) {
        assignFaction(faction, leader ? FactionRole.LEADER : FactionRole.MEMBER, newPartyId, captain, false);
    }

    public void assignFaction(WorldFaction faction, FactionRole role, UUID newPartyId, boolean captain, boolean regional) {
        if (faction == null) return;
        entityData.set(FACTION_ID, faction.id());
        entityData.set(FACTION_ROLE, role.id());
        entityData.set(FACTION_LEADER, role == FactionRole.LEADER);
        entityData.set(FACTION_NAME, faction.name());
        entityData.set(FACTION_TITLE, faction.roleTitle(role));
        partyId = newPartyId;
        partyCaptain = captain;
        regionalPresence = regional;
        applyFactionUniform(faction, role);
        rollFactionSupplies(role);
        if (role == FactionRole.LEADER && level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            FactionWorldData data = FactionWorldData.get(serverLevel);
            entityData.set(FIGHTER_NAME, data.currentLeaderName(faction));
            if (getRace().gendered()) entityData.set(GENDER, data.currentLeaderFemale(faction) ? 1 : 0);
        }
        // A regional cell is an actual resident presence, not a temporary locator hint.
        // Once a player discovers/loads it, its members are saved with that region.
        if (regional || role.ordinal() >= FactionRole.LIEUTENANT.ordinal()) setPersistenceRequired();
    }

    public void leaveFaction() {
        entityData.set(FACTION_ID, "");
        entityData.set(FACTION_LEADER, false);
        entityData.set(FACTION_ROLE, FactionRole.MEMBER.id());
        entityData.set(FACTION_NAME, "");
        entityData.set(FACTION_TITLE, "");
        partyId = null;
        partyCaptain = false;
        regionalPresence = false;
    }

    public void assignParty(UUID newPartyId, boolean captain) {
        partyId = newPartyId;
        partyCaptain = captain;
    }

    private void rollFactionSupplies(FactionRole role) {
        RandomSource random = getRandom();
        int foodBase = switch (role) {
            case RECRUIT -> random.nextInt(2);
            case MEMBER -> 1 + random.nextInt(2);
            case ENFORCER -> 1 + random.nextInt(3);
            case LIEUTENANT -> 2 + random.nextInt(3);
            case LEADER -> 3 + random.nextInt(3);
        };
        foodSupplies = Math.max(foodSupplies, foodBase);
        float senzuChance = switch (role) {
            case RECRUIT -> 0.01F; case MEMBER -> 0.06F; case ENFORCER -> 0.16F;
            case LIEUTENANT -> 0.38F; case LEADER -> 0.72F;
        };
        if (random.nextFloat() < senzuChance) senzuBeans = Math.max(senzuBeans, role == FactionRole.LEADER && random.nextFloat() < 0.35F ? 2 : 1);
    }

    private void tickFactionSupplies() {
        if (!isFactionMember() || supplyCooldown > 0 || isDefeated() || isCaptive() || isAwakening()) return;
        LivingEntity target = getTarget();
        float hp = getHealth() / Math.max(1.0F, getMaxHealth());

        // Senzu is rare and valuable: an NPC only risks using it when badly hurt and
        // it has enough separation to plausibly get the bean down.
        if (senzuBeans > 0 && hp < 0.28F && target != null && distanceToSqr(target) >= 49.0D
                && !isCasting() && !isComboing()) {
            senzuBeans--;
            supplyCooldown = 520;
            setHealth(getMaxHealth());
            level().playSound(null, blockPosition(), MainSounds.SENZU_BEAN.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            speak("Senzu!", 36);
            flareAura(55);
            return;
        }

        // Ordinary provisions represent food/rest without inventing an inventory UI.
        // They are only used outside active combat and heal modestly.
        if (foodSupplies > 0 && target == null && hp < 0.72F
                && tickCount % 100 == Math.floorMod(getUUID().hashCode(), 100)) {
            foodSupplies--;
            supplyCooldown = 300;
            setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * 0.22F));
            // Routine eating remains background behavior; no speech bubble for every snack.
        }
    }

    private void tickAuraBehavior() {
        if (auraFlareTicks > 0 && --auraFlareTicks <= 0 && !isCharge() && !isTransforming()) {
            entityData.set(AURA_FLARED, false);
        }

        LivingEntity target = getTarget();
        UUID currentTarget = target == null ? null : target.getUUID();
        if ((currentTarget == null && lastAuraTargetId != null)
                || (currentTarget != null && !currentTarget.equals(lastAuraTargetId))) {
            if (target == null) {
                angerAuraUsed = false;
            } else {
                float chance = switch (getPersonality()) {
                    case AGGRESSIVE -> 0.42F; case PROUD -> 0.38F; case HEROIC -> 0.31F;
                    case CALM -> 0.18F; case CAUTIOUS -> 0.15F;
                };
                if (isFactionMember() && getFactionRole().ordinal() >= FactionRole.ENFORCER.ordinal()) chance += 0.06F;
                if (getRandom().nextFloat() < Math.min(0.92F, chance)) {
                    flareAura(32 + getRandom().nextInt(46));
                }
            }
            lastAuraTargetId = currentTarget;
        }

        if (target != null && !angerAuraUsed && getHealth() < getMaxHealth() * 0.52F) {
            angerAuraUsed = true;
            float chance = getPersonality() == FighterPersonality.CALM ? 0.30F : 0.58F;
            if (getFactionRole().ordinal() >= FactionRole.LIEUTENANT.ordinal()) chance += 0.12F;
            if (getRandom().nextFloat() < Math.min(0.90F, chance)) {
                flareAura(90 + getRandom().nextInt(111));
                if (getSpeech().isEmpty() && getRandom().nextFloat() < 0.45F) speak("Enough.", 38);
            }
        }

        // Some experienced fighters deliberately keep their aura up after the fight has
        // escalated. It is a stable-ish personality tell, not a permanent effect on everyone.
        if (target != null && isAwakened() && !isAuraFlared() && !isCharge() && !isTransforming()
                && tickCount % 140 == Math.floorMod(getUUID().hashCode(), 140)
                && getRandom().nextFloat() < 0.42F) {
            flareAura(120 + getRandom().nextInt(161));
        }
    }

    public void varyFactionUniform(WorldFaction faction, int salt) {
        if (faction != null) applyFactionUniform(faction, getFactionRole(), salt);
    }

    private void applyFactionUniform(WorldFaction faction, FactionRole role) { applyFactionUniform(faction, role, 0); }

    private void applyFactionUniform(WorldFaction faction, FactionRole role, int salt) {
        // Same faction = recognizable visual family; same faction != clone army.
        // The seed chooses a stable family while UUID/rank chooses an individual variant.
        long identity = getUUID().getMostSignificantBits() ^ Long.rotateLeft(getUUID().getLeastSignificantBits(), 17);
        int variant = Math.floorMod((int)(identity ^ faction.seed()) + salt * 3, 11);
        int theme = faction.uniformTheme();
        int outfit = switch (getRace()) {
            case HUMAN, SAIYAN -> {
                int[] pool = switch (faction.structure()) {
                    case CULT -> new int[]{16,17,18,19,20,21};
                    case ORDER -> new int[]{16,20,21,3,8};
                    case CLAN -> new int[]{3,8,20,21,11};
                    case SCHOOL -> new int[]{0,3,4,8,9,14};
                    case GUARD -> new int[]{9,10,15,1,21};
                    case GANG, SYNDICATE -> new int[]{1,2,6,7,13,15,19};
                    case CREW -> new int[]{0,1,5,6,8,10,13};
                };
                int rankBias = role.ordinal() >= FactionRole.LIEUTENANT.ordinal() ? 2 : 0;
                yield pool[Math.floorMod(theme + variant + rankBias, pool.length)];
            }
            case NAMEKIAN -> Math.floorMod(theme + variant + role.id(), 2);
            case MAJIN -> Math.floorMod(theme + variant + role.id(), 6);
            case FROST_DEMON, BIO_ANDROID -> 0;
        };
        entityData.set(OUTFIT, outfit);

        // Cults/orders share a recognizable aura family even when individual clothing,
        // hair and faces differ. This uses DMZ's existing aura renderer/state.
        if (faction.structure() == FactionStructure.CULT || faction.structure() == FactionStructure.ORDER) {
            int[] ritualPalette = {0xB05CFF, 0xE75B8D, 0x5DE3D6, 0xF2C14E, 0x7F8CFF, 0xD9E4FF};
            int aura = ritualPalette[Math.floorMod(faction.uniformTheme(), ritualPalette.length)];
            setAuraColor(aura);
            setLightningColor(aura);
        }
    }


    /** Living World-only appearance hook for special independent fighters. */
    public void setOutfitForLivingWorld(int outfit) { entityData.set(OUTFIT, Math.max(0, outfit)); }

    /** Fixed classic-inspired appearance for the unique World Menace easter egg. */
    public void configureHerobrineAppearance() {
        entityData.set(FIGHTER_NAME, "Herobrine");
        entityData.set(RACE, FighterRace.HUMAN.id());
        entityData.set(DISPLAY_SCALE, 1.0F);
        setScaleVal(1.0F);
        entityData.set(GENDER, 0);
        entityData.set(BODY_TYPE, 1);
        entityData.set(EYES_TYPE, 0);
        entityData.set(NOSE_TYPE, 0);
        entityData.set(MOUTH_TYPE, 0);
        entityData.set(HAIR_ID, 1);
        entityData.set(OUTFIT, 1); // Capsule-Corp blue is the closest native DMZ casual silhouette to classic Steve colors.
        entityData.set(BODY_COLOR, "#B77A58");
        entityData.set(BODY_COLOR2, "#B77A58");
        entityData.set(BODY_COLOR3, "#B77A58");
        entityData.set(HAIR_COLOR, "#3B281C");
        entityData.set(EYE1_COLOR, "#FFFFFF");
        entityData.set(EYE2_COLOR, "#FFFFFF");
        // One deliberate menace palette: dark blood-red name/ki rather than randomly alternating
        // between black and red. Red remains readable at night while preserving the ominous tone.
        setAuraColor(0x8B0F17);
        setLightningColor(0x8B0F17);
        entityData.set(FACTION_ID, "");
        entityData.set(FACTION_LEADER, false);
        entityData.set(FACTION_NAME, "");
        entityData.set(FACTION_TITLE, "");
    }

    /** Engineered-human appearance for the persistent Red Ribbon X-7 World Menace. */
    public void configureRedRibbonExperimentAppearance() {
        entityData.set(FIGHTER_NAME, "Red Ribbon Experiment X-7");
        entityData.set(RACE, FighterRace.HUMAN.id());
        entityData.set(DISPLAY_SCALE, 1.02F);
        setScaleVal(1.02F);
        entityData.set(GENDER, 0);
        entityData.set(BODY_TYPE, 1);
        entityData.set(HAIR_ID, 7);
        // Native DMZ uniform preset with a military/Red-Ribbon silhouette; no custom armor dependency.
        entityData.set(OUTFIT, 0); // renderer applies DMZ native Red Ribbon uniform overlay
        entityData.set(BODY_COLOR, "#C89272");
        entityData.set(BODY_COLOR2, "#B98265");
        entityData.set(BODY_COLOR3, "#8F5A49");
        entityData.set(HAIR_COLOR, "#141414");
        entityData.set(EYE1_COLOR, "#8E111B");
        entityData.set(EYE2_COLOR, "#8E111B");
        setAuraColor(0xC41F2A);
        setLightningColor(0xE7E7E7);
        entityData.set(FACTION_ID, "");
        entityData.set(FACTION_LEADER, false);
        entityData.set(FACTION_NAME, "Red Ribbon Army");
        entityData.set(FACTION_TITLE, "");
    }

    /** Compact identity snapshot used by the selective recurring-fighter system. */
    public CompoundTag writeMemoryProfile() {
        // Ensure ordinary-life identity is frozen into the same remembered snapshot as combat identity.
        com.dmzlivingworld.world.FighterHobby.of(this);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Alignment", getAlignment().id());
        tag.putInt("Rank", getRank().id());
        tag.putInt("Personality", getPersonality().id());
        tag.putInt("Race", getRace().id());
        tag.putInt("Archetype", getArchetype().id());
        tag.putString("Name", getFighterName());
        // A memory is a person's permanent history, never the momentary reading from a form,
        // fruit or social flare. The visible current reading remains available in live play.
        tag.putInt("BattlePower", getPermanentBattlePower());
        tag.putInt("PermanentBattlePower", getPermanentBattlePower());
        // Keep one stable person-id across physical unload/re-materialization. NPC social bonds already
        // use this key; memory/Instant Transmission now relies on the same identity instead of names.
        if (!legacyData.hasUUID("NpcSocialIdentity")) legacyData.putUUID("NpcSocialIdentity", getUUID());
        tag.put("Legacy", legacyData.copy());
        tag.putString("LegacyTitle", getLegacyTitle());
        FighterArsenalManager.writeProfile(this, tag);
        tag.putString("RivalName", rivalName);
        tag.putInt("TrainingSessions", trainingSessions);
        tag.putBoolean("FlightUnlocked", hasFlightUnlocked());
        tag.putInt("RacialSkillLevel", getRacialSkillLevel());
        tag.putInt("RacialTrainingProgress", racialTrainingProgress);
        tag.putBoolean("Awakened", isAwakened());
        tag.putFloat("DisplayScale", getDisplayScale());
        tag.putInt("Gender", entityData.get(GENDER));
        tag.putInt("BodyType", getBodyType());
        tag.putInt("EyesType", getEyesType());
        tag.putInt("NoseType", getNoseType());
        tag.putInt("MouthType", getMouthType());
        tag.putInt("HairId", getHairId());
        tag.putInt("Outfit", getOutfit());
        tag.putString("BodyColor", getBodyColor());
        tag.putString("BodyColor2", getBodyColor2());
        tag.putString("BodyColor3", getBodyColor3());
        tag.putString("HairColor", getHairColor());
        tag.putString("Eye1Color", getEye1Color());
        tag.putString("Eye2Color", getEye2Color());
        if (isFactionMember()) tag.putString("FactionId", getFactionId());
        tag.putString("FactionName", getFactionDisplayName());
        tag.putString("FactionTitle", getFactionTitle());
        tag.putBoolean("FactionLeader", isFactionLeader());
        tag.putInt("FactionRole", getFactionRole().id());
        if (isWanted()) {
            tag.putString("WantedId", getWantedId());
            tag.putInt("WantedLevel", getWantedLevel());
            tag.putString("WantedCrime", getWantedCrime());
        }
        writeDialogueHistory(tag, "DialogueHistory");
        com.dmzlivingworld.world.ReactiveWorldManager.writeProfile(this, tag);
        com.dmzlivingworld.world.WorldMenaceManager.writeProfile(this, tag);
        return tag;
    }

    /**
     * Client-safe appearance-only reconstruction used by the People journal portrait.
     * It deliberately does not configure combat, goals, AI or live world state: the menu
     * should show exactly the person the player last remembers, not instantiate a second actor.
     */
    public void initializePortraitFromMemory(CompoundTag profile) {
        if (profile == null) profile = new CompoundTag();
        entityData.set(ALIGNMENT, FighterAlignment.byId(profile.getInt("Alignment")).id());
        entityData.set(RANK, FighterRank.byId(profile.getInt("Rank")).id());
        entityData.set(PERSONALITY, FighterPersonality.byId(profile.getInt("Personality")).id());
        entityData.set(RACE, FighterRace.byId(profile.getInt("Race")).id());
        entityData.set(ARCHETYPE, FighterArchetype.byId(profile.getInt("Archetype")).id());
        if (profile.contains("Name")) entityData.set(FIGHTER_NAME, profile.getString("Name"));
        if (profile.contains("PermanentBattlePower")) setBattlePower(Math.max(1, profile.getInt("PermanentBattlePower")));
        else if (profile.contains("BattlePower")) setBattlePower(Math.max(1, profile.getInt("BattlePower")));

        float scale = profile.contains("DisplayScale") ? profile.getFloat("DisplayScale") : 1.0F;
        entityData.set(DISPLAY_SCALE, Math.max(0.45F, Math.min(2.4F, scale)));
        setScaleVal(entityData.get(DISPLAY_SCALE));
        if (profile.contains("Gender")) entityData.set(GENDER, profile.getInt("Gender"));
        if (profile.contains("BodyType")) entityData.set(BODY_TYPE, profile.getInt("BodyType"));
        if (profile.contains("EyesType")) entityData.set(EYES_TYPE, profile.getInt("EyesType"));
        if (profile.contains("NoseType")) entityData.set(NOSE_TYPE, profile.getInt("NoseType"));
        if (profile.contains("MouthType")) entityData.set(MOUTH_TYPE, profile.getInt("MouthType"));
        if (profile.contains("HairId")) entityData.set(HAIR_ID, profile.getInt("HairId"));
        if (profile.contains("Outfit")) entityData.set(OUTFIT, profile.getInt("Outfit"));
        if (profile.contains("BodyColor")) entityData.set(BODY_COLOR, profile.getString("BodyColor"));
        if (profile.contains("BodyColor2")) entityData.set(BODY_COLOR2, profile.getString("BodyColor2"));
        if (profile.contains("BodyColor3")) entityData.set(BODY_COLOR3, profile.getString("BodyColor3"));
        if (profile.contains("HairColor")) entityData.set(HAIR_COLOR, profile.getString("HairColor"));
        if (profile.contains("Eye1Color")) entityData.set(EYE1_COLOR, profile.getString("Eye1Color"));
        if (profile.contains("Eye2Color")) entityData.set(EYE2_COLOR, profile.getString("Eye2Color"));

        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
        entityData.set(KAIOKEN_LEVEL, 0);
        entityData.set(MEDITATING, false);
        entityData.set(DEFEATED, false);
        entityData.set(CAPTIVE, false);
        entityData.set(AWAKENED, profile.getBoolean("Awakened"));
        entityData.set(READY, true);
        FighterArsenalManager.readProfile(this, profile);
        com.dmzlivingworld.world.ReactiveWorldManager.restore(this, profile);
        com.dmzlivingworld.world.WorldMenaceManager.restoreProfile(this, profile);
    }

    /** Marks a detached GUI clone as a historical, non-living portrait. */
    public void configureArchivedPortrait() {
        getPersistentData().putBoolean("LWArchivedPortrait", true);
        setAmbientPose(0);
        setFishingActivity(false);
        setFlying(false);
        setFlyingFast(false);
        setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
        setPose(Pose.STANDING);
        setDeltaMovement(Vec3.ZERO);
        setTarget(null);
        setAttacking(false);
    }

    public boolean isArchivedPortraitPreview() {
        return getPersistentData().getBoolean("LWArchivedPortrait");
    }

    /** Recreates the same procedural person while allowing their PL to evolve between meetings. */
    public void initializeFromMemory(CompoundTag profile) {
        FighterAlignment alignment = FighterAlignment.byId(profile.getInt("Alignment"));
        FighterRank rank = FighterRank.byId(profile.getInt("Rank"));
        FighterPersonality personality = FighterPersonality.byId(profile.getInt("Personality"));
        FighterRace race = FighterRace.byId(profile.getInt("Race"));
        FighterArchetype archetype = FighterArchetype.byId(profile.getInt("Archetype"));
        initializeAs(alignment, rank, personality, race, archetype);
        readDialogueHistory(profile, "DialogueHistory");
        com.dmzlivingworld.world.ReactiveWorldManager.restore(this, profile);
        com.dmzlivingworld.world.WorldMenaceManager.restoreProfile(this, profile);

        if (profile.contains("Name")) entityData.set(FIGHTER_NAME, profile.getString("Name"));
        int rememberedPower = profile.contains("PermanentBattlePower") ? profile.getInt("PermanentBattlePower")
                : (profile.contains("BattlePower") ? profile.getInt("BattlePower") : getBattlePower());
        legacyData = sanitizeLegacyData(profile.contains("Legacy", Tag.TAG_COMPOUND) ? profile.getCompound("Legacy").copy() : new CompoundTag());
        int canonicalPower = legacyData.getInt(PERMANENT_BATTLE_POWER);
        if (canonicalPower <= 0) canonicalPower = Math.max(1, rememberedPower);
        legacyData.putInt(PERMANENT_BATTLE_POWER, canonicalPower);
        setBattlePower(canonicalPower);
        syncLegacyTitle();
        FighterArsenalManager.readProfile(this, profile);
        // initializeAs() creates a fresh body and may roll fresh cosmetics; remembered people
        // must keep their stored accessory identity instead. Re-sync after the memory profile/equipment loads.
        entityData.set(COSMETIC_ACCESSORY, legacyData.getInt("CosmeticAccessoryId"));
        FighterSpecialItemManager.initialize(this);
        FighterScientistManager.initialize(this);
        // A recalled fighter may still be within a live Might Fruit window. Rebuild the
        // temporary projection from the remembered permanent base so the valid one-minute
        // effect survives the continuity hand-off without becoming permanent power.
        refreshTemporaryPowerProjection();
        rivalName = profile.getString("RivalName");
        trainingSessions = profile.contains("TrainingSessions") ? profile.getInt("TrainingSessions") : trainingSessions;
        if (profile.contains("FlightUnlocked")) entityData.set(FLIGHT_UNLOCKED, profile.getBoolean("FlightUnlocked"));
        if (profile.contains("RacialSkillLevel")) entityData.set(RACIAL_SKILL_LEVEL, profile.getInt("RacialSkillLevel"));
        racialTrainingProgress = profile.contains("RacialTrainingProgress") ? profile.getInt("RacialTrainingProgress") : 0;
        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
        float scale = profile.contains("DisplayScale") ? profile.getFloat("DisplayScale") : getDisplayScale();
        entityData.set(DISPLAY_SCALE, scale);
        setScaleVal(scale);
        if (profile.contains("Gender")) entityData.set(GENDER, profile.getInt("Gender"));
        if (profile.contains("BodyType")) entityData.set(BODY_TYPE, profile.getInt("BodyType"));
        if (profile.contains("EyesType")) entityData.set(EYES_TYPE, profile.getInt("EyesType"));
        if (profile.contains("NoseType")) entityData.set(NOSE_TYPE, profile.getInt("NoseType"));
        if (profile.contains("MouthType")) entityData.set(MOUTH_TYPE, profile.getInt("MouthType"));
        if (profile.contains("HairId")) entityData.set(HAIR_ID, profile.getInt("HairId"));
        if (profile.contains("Outfit")) entityData.set(OUTFIT, profile.getInt("Outfit"));
        if (profile.contains("BodyColor")) entityData.set(BODY_COLOR, profile.getString("BodyColor"));
        if (profile.contains("BodyColor2")) entityData.set(BODY_COLOR2, profile.getString("BodyColor2"));
        if (profile.contains("BodyColor3")) entityData.set(BODY_COLOR3, profile.getString("BodyColor3"));
        if (profile.contains("HairColor")) entityData.set(HAIR_COLOR, profile.getString("HairColor"));
        if (profile.contains("Eye1Color")) entityData.set(EYE1_COLOR, profile.getString("Eye1Color"));
        if (profile.contains("Eye2Color")) entityData.set(EYE2_COLOR, profile.getString("Eye2Color"));
        if (profile.contains("FactionId")) entityData.set(FACTION_ID, profile.getString("FactionId"));
        if (profile.contains("FactionName")) entityData.set(FACTION_NAME, profile.getString("FactionName"));
        if (profile.contains("FactionTitle")) entityData.set(FACTION_TITLE, profile.getString("FactionTitle"));
        entityData.set(FACTION_LEADER, false); // leaders are world-persistent and never recreated by memory.
        entityData.set(FACTION_ROLE, profile.contains("FactionRole")
                ? Math.min(FactionRole.LIEUTENANT.id(), profile.getInt("FactionRole")) : FactionRole.MEMBER.id());
        if (profile.contains("WantedLevel") && profile.getInt("WantedLevel") > 0) {
            markWanted(profile.getString("WantedId"), profile.getInt("WantedLevel"), profile.getString("WantedCrime"));
        }
        boolean rememberedAwakened = profile.contains("Awakened") && profile.getBoolean("Awakened");
        entityData.set(AWAKENED, rememberedAwakened);
        setLightning(rememberedAwakened);
        combatConfigured = false;
        configureCombatProfile(true);
    }

    public void bindMemory(UUID ownerId, UUID recordId, int encounters, int relationship, boolean rescued) {
        this.memoryOwnerId = ownerId;
        this.memoryRecordId = recordId;
        this.memoryEncounters = Math.max(1, encounters);
        this.memoryRelationship = relationship;
        this.memoryRescued = rescued;
        // Remembered people are explicitly managed by PhysicalContinuityManager; do not let
        // vanilla mob despawn silently erase them while the player is watching/interacting.
        setPersistenceRequired();
    }

    public boolean isRemembered() { return memoryRecordId != null; }
    public UUID getMemoryRecordId() { return memoryRecordId; }
    public UUID getMemoryOwnerId() { return memoryOwnerId; }
    public void detachMemory(UUID ownerId, UUID recordId) {
        if (ownerId != null && memoryOwnerId != null && !ownerId.equals(memoryOwnerId)) return;
        if (recordId != null && memoryRecordId != null && !recordId.equals(memoryRecordId)) return;
        memoryOwnerId = null;
        memoryRecordId = null;
        memoryEncounters = 0;
        memoryRelationship = 0;
        memoryRescued = false;
    }
    public boolean isRememberedFor(Player player) {
        return player != null && memoryOwnerId != null && memoryOwnerId.equals(player.getUUID());
    }
    public int getMemoryEncounters() { return memoryEncounters; }
    public int getMemoryRelationship() { return memoryRelationship; }
    public boolean wasRescuedByMemoryOwner() { return memoryRescued; }


    /**
     * A pressured fighter either uses a race-appropriate form already learned through
     * training, or falls back to the older generic Awakening if they have no form skill.
     * The form path mirrors DMZ 2.1.3 main-tree unlock levels; legendary branches remain
     * out of procedural progression.
     */
    public boolean beginAwakening() {
        if (level().isClientSide || isAwakening() || isRacialFormActive() || isKaiokenActive()
                || getRank() == FighterRank.ROOKIE) return false;
        if (isAwakened() && getRacialSkillLevel() <= 0) return false;
        racialTransformPending = getRacialSkillLevel() > 0;
        awakeningTicks = getRank() == FighterRank.VETERAN ? 76 : 64;
        setTransforming(true);
        setKiCharge(true);
        RacialFormProfile next = racialTransformPending ? RacialFormProfile.forSkill(getRace(), getRacialSkillLevel()) : null;
        setLightning(next != null ? next.lightning() : (getRank() == FighterRank.VETERAN || getRandom().nextBoolean()));
        if (next != null && next.auraColor() != 0xFFFFFF) {
            setAuraType("kakarot");
            setAuraColor(next.auraColor());
        }
        interruptCombo();
        stopCasting();
        setAttacking(false);
        getNavigation().stop();
        level().playSound(null, blockPosition(), MainSounds.TRANSFORM_ON.get(), SoundSource.HOSTILE, 1.35F, 0.92F + getRandom().nextFloat() * 0.12F);
        return true;
    }

    private void tickAwakening() {
        LivingEntity target = getTarget();
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setFlying(false);
        setKiCharge(true);
        getNavigation().stop();
        if (target != null && target.isAlive()) rotateBodyToTarget(target);

        if (awakeningTicks > 0) awakeningTicks--;
        if (awakeningTicks > 0) return;

        setTransforming(false);
        setKiCharge(false);
        if (racialTransformPending) {
            racialTransformPending = false;
            activateRacialForm();
            return;
        }

        entityData.set(AWAKENED, true);
        double multiplier = getRank() == FighterRank.VETERAN ? 1.72D : 1.42D;
        setEarnedBattlePowerAndRefresh((int)Math.min(Integer.MAX_VALUE - 1L,
                Math.round(Math.max(1, getPermanentBattlePower()) * multiplier)));
        applyAwakenedCombatBoost();
        setHealth(Math.min(getMaxHealth(), getHealth() + getMaxHealth() * (getRank() == FighterRank.VETERAN ? 0.26F : 0.18F)));

        if (getRace() == FighterRace.SAIYAN) {
            entityData.set(HAIR_COLOR, "#F2D35A");
            entityData.set(EYE1_COLOR, "#58D7D1");
            entityData.set(EYE2_COLOR, "#2F9F9D");
        }
        setLightning(true);
        if (getRandom().nextFloat() < 0.72F) flareAura(140 + getRandom().nextInt(121));
        level().playSound(null, blockPosition(), MainSounds.TRANSFORM_OFF.get(), SoundSource.HOSTILE, 1.25F, 1.0F);
        speak(getPersonality() == FighterPersonality.PROUD ? "Now we can begin." : "My power just changed.", 52);
    }

    private void applyAwakenedCombatBoost() {
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(FighterPowerStatScaler.baseAttack(this, FighterBattleGrowthManager.combatMultiplier(this)) * (getRank() == FighterRank.VETERAN ? 1.32D : 1.20D));
        setKiBlastDamage(FighterCombatDirector.baseKiDamage(this) * (getRank() == FighterRank.VETERAN ? 1.34F : 1.20F));
    }

    private void activateRacialForm() {
        NpcFormConfigBridge.Form configured = NpcFormConfigBridge.form(getRace(), getRacialSkillLevel());
        RacialFormProfile form = RacialFormProfile.forSkill(getRace(), getRacialSkillLevel());
        if (configured == null || isRacialFormActive()) return;
        racialBasePower = getPermanentBattlePower();
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        racialBaseAttack = attack == null ? getRank().attackDamage() : attack.getBaseValue();
        racialBaseSpeed = speed == null ? getRank().speed() : speed.getBaseValue();
        racialBaseAttackSpeed = getDefaultAttackSpeed();
        racialBaseKiDamage = getKiBlastDamage();
        racialBaseScale = getDisplayScale();
        racialBaseHairColor = getHairColor();
        racialBaseEye1Color = getEye1Color();
        racialBaseEye2Color = getEye2Color();
        racialBaseAuraType = getAuraType() == null ? "" : getAuraType();
        racialBaseAuraColor = getAuraColor();
        racialBaseLightning = isLightning();

        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, configured.skillLevel());
        refreshTemporaryPowerProjection();
        if (attack != null) attack.setBaseValue(racialBaseAttack * configured.melee());
        if (speed != null) speed.setBaseValue(racialBaseSpeed * configured.speed());
        setDefaultAttackSpeed(racialBaseAttackSpeed * configured.attackSpeed());
        setKiBlastDamage((float)(racialBaseKiDamage * configured.ki()));
        float oldMax = Math.max(1.0F, getMaxHealth());
        float healthRatio = getHealth() / oldMax;
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(health.getBaseValue() * configured.vitality());
        setHealth(Math.max(1.0F, getMaxHealth() * healthRatio));
        entityData.set(DISPLAY_SCALE, racialBaseScale * configured.scale());
        setScaleVal(getDisplayScale());
        if (!configured.hairColor().isBlank()) entityData.set(HAIR_COLOR, configured.hairColor());
        if (!configured.eyeColor().isBlank()) { entityData.set(EYE1_COLOR, configured.eyeColor()); entityData.set(EYE2_COLOR, configured.eyeColor()); }
        setAuraType("kakarot");
        if (configured.auraColor() != 0xFFFFFF) setAuraColor(configured.auraColor());
        setLightning(configured.lightning());
        racialCalmTicks = 0;
        flareAura(80);
        level().playSound(null, blockPosition(), MainSounds.TRANSFORM_OFF.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        speak((configured.name() == null ? configured.id() : configured.name()) + "!", 64);
    }

    private void tickRacialForm() {
        if (!isRacialFormActive()) return;
        if (isCaptive() || isDefeated()) { stopRacialForm(); return; }
        if (FighterFullPowerManager.isActive(this)) { racialCalmTicks = 0; return; }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) racialCalmTicks = 0;
        else if (++racialCalmTicks > 360) stopRacialForm();
    }

    public void stopRacialForm() {
        if (!isRacialFormActive()) return;
        FighterPowerStatScaler.preserveCurrentMaxHealth(this);
        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
        refreshTemporaryPowerProjection();
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attack != null && racialBaseAttack > 0.0D) attack.setBaseValue(racialBaseAttack);
        if (speed != null && racialBaseSpeed > 0.0D) speed.setBaseValue(racialBaseSpeed);
        if (racialBaseAttackSpeed > 0.0D) setDefaultAttackSpeed(racialBaseAttackSpeed);
        if (racialBaseKiDamage > 0.0F) setKiBlastDamage(racialBaseKiDamage);
        if (racialBaseScale > 0.0F) { entityData.set(DISPLAY_SCALE, racialBaseScale); setScaleVal(racialBaseScale); }
        if (racialBaseHairColor != null && !racialBaseHairColor.isBlank()) entityData.set(HAIR_COLOR, racialBaseHairColor);
        if (racialBaseEye1Color != null && !racialBaseEye1Color.isBlank()) entityData.set(EYE1_COLOR, racialBaseEye1Color);
        if (racialBaseEye2Color != null && !racialBaseEye2Color.isBlank()) entityData.set(EYE2_COLOR, racialBaseEye2Color);
        setAuraType(racialBaseAuraType == null ? "" : racialBaseAuraType);
        setAuraColor(racialBaseAuraColor);
        setLightning(racialBaseLightning);
        racialCalmTicks = 0;
        // Rebuild from canonical BP now that the temporary form multiplier has ended. This is
        // what makes a training gain earned during the form real in HP, melee and Ki as well.
        if (!isKaiokenActive()) refreshCombatStatsFromPower();
    }

    public void debugUnlockFlight() {
        entityData.set(FLIGHT_UNLOCKED, true);
        if (!isNonCombatant()) setCanFly(true);
    }

    public boolean debugSetRacialSkill(int requestedLevel) {
        int level = Math.max(0, Math.min(NpcFormConfigBridge.maxSkillLevel(getRace()), requestedLevel));
        NpcFormConfigBridge.Form form = level <= 0 ? null : NpcFormConfigBridge.form(getRace(), level);
        if (form == null && level > 0) return false;
        if (isRacialFormActive()) stopRacialForm();
        entityData.set(RACIAL_SKILL_LEVEL, level);
        racialTrainingProgress = 0;
        return true;
    }

    public boolean debugTransformRacial() {
        if (getRacialSkillLevel() <= 0 || isRacialFormActive() || isKaiokenActive() || isMeditating()) return false;
        racialTransformPending = true;
        awakeningTicks = 1;
        setTransforming(true);
        tickAwakening();
        return isRacialFormActive();
    }

    public String getRivalName() { return rivalName; }
    public void setRivalName(String name) {
        String next = name == null ? "" : name;
        if (!next.isBlank() && !next.equals(rivalName)) recordLegacyEvent("Formed a rivalry with " + next);
        rivalName = next;
    }

    public boolean isMeditating() { return entityData.get(MEDITATING); }
    /** Elapsed time in the current meditation session, persisted across save/reload in 2.1+. */
    public int getMeditationElapsedTicks() {
        return isMeditating() ? Math.max(0, meditationSessionLength - Math.max(0, meditationTicks)) : 0;
    }
    public int getMeditationRemainingTicks() { return isMeditating() ? Math.max(0, meditationTicks) : 0; }
    public boolean isPreparingMeditation() { return meditationApproachPlayer != null; }
    public int getMeditationBondTier() { return entityData.get(MEDITATION_BOND_TIER); }
    public boolean isSharedMeditatingWithPlayer(UUID playerId) {
        return playerId != null && playerId.toString().equals(entityData.get(MEDITATION_PLAYER_ID));
    }
    public boolean isSocialLifeActivity() { return socialLifeActivity; }

    /**
     * 0 = none, 1 = standing stargaze, 2 = lying-on-back stargaze,
     * 3..5 = synced dances, 6 = horn, 7..8 = sitting variants,
     * 9..10 = idle stretches, 11..12 = legacy unarmed training combinations,
     * 13 = eating, 14 = native weapon-profile training,
     * 15 = reading/studying, 16 = spyglass scouting,
     * 17 = social speaker gesture, 18 = social listener gesture,
     * 19 = legacy flower/gardening inspection (kept upright for old transient state),
     * 20..21 = strength-training variants, 22 = nap.
     */
    public int getAmbientPose() { return entityData.get(AMBIENT_POSE); }
    public boolean isStargazing() { return getAmbientPose() == 1 || getAmbientPose() == 2; }
    public boolean isStargazingLying() { return getAmbientPose() == 2; }
    public boolean isDancing() { return getAmbientPose() >= 3 && getAmbientPose() <= 4; }
    public int getDanceVariant() { return isDancing() ? getAmbientPose() - 3 : 0; }
    public boolean isHornRallyPose() { return getAmbientPose() == 6; }
    public boolean isGroundSitting() { return getAmbientPose() == 7 || getAmbientPose() == 8; }
    public int getGroundSitVariant() { return getAmbientPose() == 8 ? 1 : 0; }
    public boolean isIdleStretching() { return getAmbientPose() == 9 || getAmbientPose() == 10; }
    public int getIdleStretchVariant() { return getAmbientPose() == 10 ? 1 : 0; }
    public boolean isTrainingPose() { return getAmbientPose() == 11 || getAmbientPose() == 12; }
    public int getTrainingPoseVariant() { return getAmbientPose() == 12 ? 1 : 0; }
    public boolean isEatingPose() { return getAmbientPose() == 13; }
    public boolean isArmedTrainingPose() { return getAmbientPose() == 14; }
    public boolean isStudyingPose() { return getAmbientPose() == 15; }
    public boolean isScoutingPose() { return getAmbientPose() == 16; }
    public boolean isSocialSpeakerPose() { return getAmbientPose() == 17; }
    public boolean isSocialListenerPose() { return getAmbientPose() == 18; }
    public boolean isSocialGesturePose() { return isSocialSpeakerPose() || isSocialListenerPose(); }
    public boolean isFlowerInspectPose() { return getAmbientPose() == 19; }
    public boolean isStrengthTrainingPose() { return getAmbientPose() == 25; }
    public int getStrengthTrainingVariant() { return 2; }
    public boolean isNappingPose() { return getAmbientPose() == 22; }
    public boolean isKiTrainingPose() { return getAmbientPose() == 23 || getAmbientPose() == 24; }
    public int getKiTrainingVariant() { return getAmbientPose() == 24 ? 1 : 0; }
    public boolean isMeditationCircleMember() { return entityData.get(MEDITATION_CIRCLE_MEMBER); }
    public void setAmbientPose(int pose) { entityData.set(AMBIENT_POSE, Math.max(0, Math.min(26, pose))); }
    /** Incremented for each harmless drill beat, triggering the equipped weapon's DMZ attack clip. */
    public int getWeaponTrainingStrike() { return entityData.get(WEAPON_TRAINING_STRIKE); }
    public int getWeaponCombatStrike() { return entityData.get(WEAPON_COMBAT_STRIKE); }
    public int getUnarmedTrainingStrike() { return entityData.get(UNARMED_TRAINING_STRIKE); }

    /**
     * Emits a targetless practice punch directly through a dedicated GeckoLib controller. DMZ's
     * startCombo() intentionally refuses to begin without a combat target, so training must never
     * use that API: doing so produced the long-standing torso-only "punch" state.
     */
    public void performUnarmedTrainingStrike() {
        if (level().isClientSide || !getMainHandItem().isEmpty()) return;
        entityData.set(UNARMED_TRAINING_STRIKE, entityData.get(UNARMED_TRAINING_STRIKE) + 1);
        level().playSound(null, getX(), getY() + getBbHeight() * 0.55D, getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.48F,
                0.92F + getRandom().nextFloat() * 0.16F);
    }

    /** Starts the next non-damaging drill strike without invoking the saga entity's fist swing. */
    public void performWeaponTrainingStrike() {
        if (level().isClientSide || getMainHandItem().isEmpty()) return;
        int strike = entityData.get(WEAPON_TRAINING_STRIKE) + 1;
        entityData.set(WEAPON_TRAINING_STRIKE, strike);
        playWeaponProfileSwingSound(strike);
    }

    /** Emits one real-combat weapon-profile animation beat. Damage ownership stays with combat AI. */
    public void performWeaponCombatStrikeAnimation() {
        if (level().isClientSide || getMainHandItem().isEmpty()) return;
        int strike = entityData.get(WEAPON_COMBAT_STRIKE) + 1;
        entityData.set(WEAPON_COMBAT_STRIKE, strike);
        playWeaponProfileSwingSound(strike);
    }

    private void playWeaponProfileSwingSound(int strike) {
        WeaponAttributes.Attack attack = resolveWeaponTrainingAttack(strike);
        WeaponAttributes.Sound sound = attack == null ? null : attack.swingSound();
        SoundEvent event = sound == null || sound.id() == null || sound.id().isBlank()
                ? SoundEvents.PLAYER_ATTACK_SWEEP
                : ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.tryParse(sound.id()));
        if (event == null) event = SoundEvents.PLAYER_ATTACK_SWEEP;
        float volume = sound != null && sound.volume() > 0.0F ? sound.volume() : 0.62F;
        float basePitch = sound != null && sound.pitch() > 0.0F ? sound.pitch() : 1.0F;
        float variation = sound == null ? 0.08F : Math.max(0.0F, sound.randomness());
        float pitch = basePitch + (getRandom().nextFloat() - 0.5F) * variation;
        level().playSound(null, getX(), getY() + getBbHeight() * 0.55D, getZ(), event,
                SoundSource.NEUTRAL, Math.min(1.0F, volume), pitch);
    }

    /** True when the held item has Living World's weapon choreography path. */
    private boolean hasWeaponAnimationProfile() {
        ItemStack held = getMainHandItem();
        return !held.isEmpty() && (FighterArsenalManager.isSword(held) || WeaponRegistry.getAttributes(held) != null);
    }

    /**
     * Native DMZ can independently request newer unarmed combos (including large-form combo IDs)
     * even while Living World considers this fighter armed. Intercept only that incompatible case:
     * the normal weapon combat director will own damage, while this controller owns choreography.
     */
    @Override
    public void startCombo(int comboId) {
        boolean weaponProfile = hasWeaponAnimationProfile();
        if (!level().isClientSide && weaponProfile && getTarget() != null && !isArmedTrainingPose()) {
            // Native saga brain sometimes tries to start an unarmed/Oozaru combo on an armed LW fighter.
            // Reject that incompatible animation owner here; FighterCombatDirector remains the single owner
            // of armed strike timing, animation and damage so we never emit a weapon swing with no hit.
            return;
        }
        super.startCombo(comboId);
    }

    private String resolveWeaponTrainingAnimation(int strike) {
        WeaponAttributes.Attack attack = resolveWeaponTrainingAttack(strike);
        if (attack != null && attack.animation() != null && !attack.animation().isBlank()) {
            String animation = attack.animation().trim();
            // Use the exact attack clip declared by DMZ's WeaponRegistry profile. A second fuzzy
            // lookup here can turn a valid sword/stab/slash declaration back into a nearby generic
            // melee clip, defeating the purpose of profile-driven weapon choreography.
            return animation.contains(".") ? animation : "combat." + animation;
        }
        // A third-party melee item with no usable DMZ profile still receives a weapon cut, never a fist.
        return "combat.one_handed_slash_horizontal_right_1";
    }

    private WeaponAttributes.Attack resolveWeaponTrainingAttack(int strike) {
        WeaponAttributes attributes = WeaponRegistry.getAttributes(getMainHandItem());
        if (attributes == null || attributes.attacks() == null || attributes.attacks().length == 0) return null;
        WeaponAttributes.Attack[] attacks = attributes.attacks();
        // Strike counters are one-based because zero means "no controller beat yet". Map strike 1
        // to profile attack 0 so a three-hit sword begins slash 1 -> slash 2 -> stab, not hit #2.
        int start = Math.floorMod(strike - 1, attacks.length);
        for (int offset = 0; offset < attacks.length; offset++) {
            WeaponAttributes.Attack candidate = attacks[(start + offset) % attacks.length];
            if (candidate != null && candidate.animation() != null && !candidate.animation().isBlank()) return candidate;
        }
        return null;
    }

    /** Profile-aware harmless drill cadence; longer weapon wind-ups naturally create slower practice beats. */
    public int getWeaponTrainingCadenceTicks() {
        WeaponAttributes.Attack attack = resolveWeaponTrainingAttack(entityData.get(WEAPON_TRAINING_STRIKE) + 1);
        if (attack == null) return 34;
        double upswing = Math.max(0.0D, Math.min(1.5D, attack.upswing()));
        return Math.max(20, Math.min(56, 20 + (int)Math.round(upswing * 24.0D)));
    }

    /** Cosmetic DMZ accessory id; rendered natively by LW without applying Curios/weight penalties. */
    public int getCosmeticAccessoryId() { return entityData.get(COSMETIC_ACCESSORY); }
    public void setCosmeticAccessoryId(int id) { entityData.set(COSMETIC_ACCESSORY, Math.max(0, Math.min(7, id))); }

    public boolean isFishingActivity() { return entityData.get(FISHING_ACTIVITY); }
    public void setFishingActivity(boolean active) {
        entityData.set(FISHING_ACTIVITY, active);
        if (!active) {
            entityData.set(FISHING_BOBBER_X, 0.0F);
            entityData.set(FISHING_BOBBER_Y, 0.0F);
            entityData.set(FISHING_BOBBER_Z, 0.0F);
        }
    }
    public void setFishingBobberPosition(Vec3 pos) {
        if (pos == null) return;
        entityData.set(FISHING_BOBBER_X, (float)pos.x);
        entityData.set(FISHING_BOBBER_Y, (float)pos.y);
        entityData.set(FISHING_BOBBER_Z, (float)pos.z);
    }
    public Vec3 getFishingBobberPosition() {
        return new Vec3(entityData.get(FISHING_BOBBER_X), entityData.get(FISHING_BOBBER_Y), entityData.get(FISHING_BOBBER_Z));
    }

    /** Smooth the network-synced bobber between client ticks so high-FPS rendering does not step at 20 Hz. */
    public Vec3 getFishingBobberPosition(float partialTick) {
        if (!level().isClientSide || !clientFishingBobberInitialized) return getFishingBobberPosition();
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        return clientFishingBobberPrev.lerp(clientFishingBobberCurrent, t);
    }

    private void tickClientFishingBobber() {
        Vec3 synced = getFishingBobberPosition();
        if (!isFishingActivity() || synced.lengthSqr() < 0.01D) {
            clientFishingBobberInitialized = false;
            clientFishingBobberPrev = Vec3.ZERO;
            clientFishingBobberCurrent = Vec3.ZERO;
            return;
        }
        if (!clientFishingBobberInitialized) {
            clientFishingBobberPrev = synced;
            clientFishingBobberCurrent = synced;
            clientFishingBobberInitialized = true;
            return;
        }
        clientFishingBobberPrev = clientFishingBobberCurrent;
        clientFishingBobberCurrent = synced;
    }

    public boolean isAmbientFlightActivity() { return ambientFlightActivity; }
    public void setAmbientFlightActivity(boolean active) { ambientFlightActivity = active; }
    public boolean isSocialPowerDisplay() { return socialPowerDisplay; }
    public void setSocialPowerDisplay(boolean active) {
        socialPowerDisplay = active;
        if (!active) setKiCharge(false);
        else {
            suppressCombatIntent();
            setKiCharge(true);
            if (!level().isClientSide) level().playSound(null, blockPosition(), MainSounds.KI_CHARGE_LOOP.get(), SoundSource.HOSTILE, 0.55F, 1.0F);
        }
    }

    /**
     * A power-comparison flare is presentation only. Persist a rollback marker before changing
     * native DMZ's visible reading so chunk unload/server shutdown cannot convert it into power.
     */
    public void beginSocialPowerDisplayBoost(int displayedPower, long restoreAt) {
        if (level().isClientSide) return;
        if (!legacyData.contains(POWER_COMPARE_RESTORE_BP)) {
            legacyData.putInt(POWER_COMPARE_RESTORE_BP, getPermanentBattlePower());
        }
        legacyData.putLong(POWER_COMPARE_RESTORE_AT, Math.max(level().getGameTime() + 1L, restoreAt));
        setBattlePower(Math.max(1, displayedPower));
    }

    /** Clears a transient social reading and reconstructs the real combat profile. */
    public void endSocialPowerDisplayBoost() {
        if (!level().isClientSide) {
            legacyData.remove(POWER_COMPARE_RESTORE_BP);
            legacyData.remove(POWER_COMPARE_RESTORE_AT);
        }
        refreshTemporaryPowerProjection();
    }

    /** Handles an orphaned comparison after its process-local Session was lost. */
    public void recoverExpiredSocialPowerDisplay() {
        if (level().isClientSide || !legacyData.contains(POWER_COMPARE_RESTORE_BP)) return;
        long restoreAt = legacyData.getLong(POWER_COMPARE_RESTORE_AT);
        if (restoreAt > level().getGameTime() && socialPowerDisplay) return;
        setSocialPowerDisplay(false);
        endSocialPowerDisplayBoost();
    }
    public boolean isSocialPlayerApproach() { return socialPlayerApproach; }
    public void setSocialPlayerApproach(boolean active) {
        socialPlayerApproach = active;
        if (active) {
            suppressCombatIntent();
            setFlying(false);
        } else {
            getNavigation().stop();
            setCanFly(hasFlightUnlocked() && !isNonCombatant());
        }
    }
    public void setSocialLifeActivity(boolean active) {
        socialLifeActivity = active;
        if (!active) ambientFlightActivity = false;
        if (active) {
            // Social scenes must own a clean standing state. A pending/native meditation pose
            // could otherwise leak into Meeting Up/Talking even when the meditation never began.
            if (isMeditating() || isPreparingMeditation()) stopMeditation(false);
            setPose(Pose.STANDING);
            setAmbientPose(0);
            setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
            setTarget(null);
            interruptCombo();
            stopCasting();
            setAttacking(false);
            setFlying(false);
        } else {
            setCanFly(hasFlightUnlocked() && !isNonCombatant());
        }
    }
    public int getTrainingSessions() { return trainingSessions; }
    public int getKaiokenLevel() { return entityData.get(KAIOKEN_LEVEL); }
    public boolean isKaiokenActive() { return getKaiokenLevel() > 0; }
    public boolean hasKaiokenPotential() { return kaiokenPotential; }
    public void setKaiokenPotential(boolean value) { kaiokenPotential = value; }
    public int getStoryRole() { return entityData.get(STORY_ROLE); }
    public void setStoryRole(int role) { entityData.set(STORY_ROLE, Math.max(STORY_NONE, Math.min(STORY_PEACEKEEPER, role))); }
    public boolean hasFlightUnlocked() { return entityData.get(FLIGHT_UNLOCKED); }
    public int getRacialSkillLevel() { return entityData.get(RACIAL_SKILL_LEVEL); }
    public int getActiveRacialFormLevel() { return entityData.get(ACTIVE_RACIAL_FORM_LEVEL); }
    public boolean isRacialFormActive() { return getActiveRacialFormLevel() > 0; }
    public RacialFormProfile getActiveRacialForm() { return isRacialFormActive() ? RacialFormProfile.forSkill(getRace(), getActiveRacialFormLevel()) : null; }
    public String getRacialFormName() { RacialFormProfile form = getActiveRacialForm(); return form == null ? "" : form.displayName(); }
    public boolean isKaiokenAuraPulse() { return isKaiokenActive() && (kaiokenTicks > 0) && (kaiokenTicks % 100 > 92); }

    /**
     * Natural NPC meditation uses a long-tail duration instead of one short canned timer. Common
     * sessions last minutes, disciplined fighters sometimes remain for tens of minutes, and a
     * deliberately tiny tail can meditate for literal in-game hours. Callers may pass a minimum
     * to preserve the purpose/length of an authored scene. Debug/forced callers can still supply
     * their own explicit short duration instead of using this helper.
     */
    public static int naturalMeditationDuration(RandomSource random, int minimumTicks) {
        if (random == null) return Math.max(240, minimumTicks);
        final int duration;
        float roll = random.nextFloat();
        if (roll < 0.0025F) { // ~0.25%: 1-3 real hours
            duration = 20 * 60 * (60 + random.nextInt(121));
        } else if (roll < 0.025F) { // ~2.25%: 30-60 minutes
            duration = 20 * 60 * (30 + random.nextInt(31));
        } else if (roll < 0.15F) { // ~12.5%: 10-30 minutes
            duration = 20 * 60 * (10 + random.nextInt(21));
        } else { // ordinary deliberate session: 2-8 minutes
            duration = 20 * 60 * (2 + random.nextInt(7));
        }
        return Math.max(Math.max(240, minimumTicks), duration);
    }

    /** A real social/training state: no combat, no pathfinding, gradual progression. */
    public boolean beginMeditation(int ticks) { return beginMeditation(ticks, false); }

    public boolean beginMeditation(int ticks, boolean force) {
        if (!force && !com.dmzlivingworld.compat.MeditationCompat.isNpcMeditationEnabled()) return false;
        if (level().isClientSide || isCaptive() || isDefeated() || isTransforming() || isKaiokenActive()
                || getTarget() != null || (!force && meditationCooldown > 0)) return false;
        // Ordinary-life activities and meditation are mutually exclusive. Cancel the visible
        // activity first so temporary props/poses cannot survive into meditation.
        com.dmzlivingworld.world.FighterAmbientActivityManager.cancel(this);
        meditationSessionLength = Math.max(240, ticks);
        meditationTicks = meditationSessionLength;
        entityData.set(MEDITATING, true);
        meditationPlayerJoinedExistingSolo = false;
        entityData.set(MEDITATION_BOND_TIER, 0);
        entityData.set(MEDITATION_PLAYER_ID, "");
        meditationAnchorSet = true;
        meditationAnchorX = getX();
        meditationAnchorZ = getZ();
        meditationCircleActive = false;
        entityData.set(MEDITATION_CIRCLE_MEMBER, false);
        nextMeditationDialogueTick = level().getGameTime() + 600L + getRandom().nextInt(1201);
        suppressCombatIntent();
        setFlying(false);
        getNavigation().stop();
        com.dmzlivingworld.compat.MeditationCompat.spawnNpcMeditationStartVisual(this);
        return true;
    }

    /**
     * Starts deliberate player/NPC co-meditation. The fighter first walks into a readable
     * position a few blocks beside/in front of the player, then settles and faces them. This
     * keeps shared meditation feeling like a physical scene rather than a remote status flag.
     */
    public boolean beginSharedMeditation(ServerPlayer player) {
        if (player == null || !com.dmzlivingworld.compat.MeditationCompat.isPlayerMeditating(player)
                || meditationPartnerNpc != null || isCaptive() || isDefeated() || isTransforming() || isKaiokenActive()) return false;
        com.dmzlivingworld.world.FighterAmbientActivityManager.cancel(this);
        if (isMeditating()) {
            if (meditationPartnerPlayer != null) {
                if (!meditationPartnerPlayer.equals(player.getUUID())) return false;
                // Re-opening/re-clicking the same already-shared session must not change who owns
                // its lifecycle. In particular, a player-started shared session stays player-started.
                faceMeditationPartner(player);
                return true;
            }
            // Joining an NPC who was already meditating is a guest attachment, not ownership of
            // that meditation. Preserve the exact remaining duration and anchor no matter where
            // inside the panel's valid interaction range the player joined from.
            meditationPartnerPlayer = player.getUUID();
            meditationPlayerJoinedExistingSolo = true;
            entityData.set(MEDITATION_PLAYER_ID, player.getUUID().toString());
            entityData.set(MEDITATION_BOND_TIER, meditationBondTier(player));
            faceMeditationPartner(player);
            return true;
        }
        if (meditationApproachPlayer != null && !meditationApproachPlayer.equals(player.getUUID())) return false;

        meditationApproachPlayer = player.getUUID();
        meditationApproachTicks = 0;
        // The destination follows the player instead of targeting one exact precomputed block.
        // This makes shared meditation robust around stairs, ledges and furniture where a precise
        // diagonal point may be unreachable even though the fighter can plainly walk up to them.
        suppressCombatIntent();
        setFlying(false);
        moveTowardMeditationRing(player, 1.05D);
        return true;
    }

    /**
     * Tries several close, ordinary walkable approaches around the player before falling back to
     * following the player entity itself. PathNavigation is allowed to reject blocked candidates,
     * which avoids one decorative block or stair making shared meditation look broken.
     */
    private void moveTowardMeditationRing(ServerPlayer player, double speed) {
        if (player == null) return;
        Vec3 fromPlayer = position().subtract(player.position());
        Vec3 planar = new Vec3(fromPlayer.x, 0.0D, fromPlayer.z);
        if (planar.lengthSqr() < 1.0E-4D) {
            Vec3 look = player.getLookAngle();
            planar = new Vec3(-look.x, 0.0D, -look.z);
        }
        if (planar.lengthSqr() < 1.0E-4D) planar = new Vec3(1.0D, 0.0D, 0.0D);
        planar = planar.normalize();
        Vec3 side = new Vec3(-planar.z, 0.0D, planar.x);
        Vec3[] directions = new Vec3[] {
                planar,
                planar.add(side.scale(0.65D)).normalize(),
                planar.subtract(side.scale(0.65D)).normalize(),
                side, side.scale(-1.0D),
                planar.scale(-1.0D)
        };
        for (Vec3 direction : directions) {
            Vec3 candidate = player.position().add(direction.scale(2.05D));
            if (getNavigation().moveTo(candidate.x, player.getY(), candidate.z, speed)) return;
        }
        getNavigation().moveTo(player, speed);
    }

    private void tickMeditationApproach() {
        if (!level().isClientSide && !com.dmzlivingworld.compat.MeditationCompat.isNpcMeditationEnabled()) {
            clearMeditationApproach();
            return;
        }
        suppressCombatIntent();
        setFlying(false);
        if (!(level() instanceof ServerLevel server) || meditationApproachPlayer == null) {
            clearMeditationApproach();
            return;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(meditationApproachPlayer);
        if (player == null || !player.isAlive() || !com.dmzlivingworld.compat.MeditationCompat.isPlayerMeditating(player)
                || distanceToSqr(player) > 16.0D * 16.0D || hurtTime > 0) {
            clearMeditationApproach();
            return;
        }
        meditationApproachTicks++;
        getLookControl().setLookAt(player, 35.0F, 35.0F);
        double distance = Math.sqrt(distanceToSqr(player));

        // Aim for a close but readable ring around the player. Several ordinary path targets are
        // tried so one blocked side does not stall the invitation; following the player is only the
        // fallback. Once inside the ring we freeze the fighter where pathfinding actually put it.
        boolean comfortablyClose = distance >= 1.55D && distance <= 2.55D;
        if (!comfortablyClose && (meditationApproachTicks % 15 == 1 || getNavigation().isDone())) {
            if (distance > 2.55D) {
                moveTowardMeditationRing(player, 1.05D);
            } else if (distance < 1.55D) {
                Vec3 away = position().subtract(player.position());
                away = new Vec3(away.x, 0.0D, away.z);
                if (away.lengthSqr() < 1.0E-4D) {
                    Vec3 look = player.getLookAngle();
                    away = new Vec3(-look.x, 0.0D, -look.z);
                }
                if (away.lengthSqr() < 1.0E-4D) away = new Vec3(1.0D, 0.0D, 0.0D);
                Vec3 target = player.position().add(away.normalize().scale(2.05D));
                getNavigation().moveTo(target.x, player.getY(), target.z, 0.95D);
            }
        }

        // If terrain leaves us a little farther away, accept a reachable nearby position instead
        // of jittering forever. Never teleport or stack the NPC on top of the player.
        boolean fallbackSettled = meditationApproachTicks >= 100 && distance >= 1.35D && distance <= 4.25D;
        if (comfortablyClose || fallbackSettled) {
            UUID partner = meditationApproachPlayer;
            clearMeditationApproach();
            if (beginMeditation(20 * 60 * 30, true)) {
                meditationPartnerPlayer = partner;
                meditationPlayerJoinedExistingSolo = false;
                entityData.set(MEDITATION_PLAYER_ID, player.getUUID().toString());
                entityData.set(MEDITATION_BOND_TIER, meditationBondTier(player));
                meditationAnchorSet = true;
                meditationAnchorX = getX();
                meditationAnchorZ = getZ();
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                faceMeditationPartner(player);
            }
        } else if (meditationApproachTicks >= 180) {
            // Give up cleanly if there is genuinely no walkable way close enough.
            clearMeditationApproach();
        }
    }

    private void clearMeditationApproach() {
        meditationApproachPlayer = null;
        meditationApproachTicks = 0;
        getNavigation().stop();
    }

    public boolean isMeditatingWith(ServerPlayer player) {
        return player != null && isMeditating() && player.getUUID().equals(meditationPartnerPlayer);
    }

    /** Starts a real NPC/NPC shared meditation session. Both fighters keep their own native growth. */
    public boolean beginSharedMeditation(AmbientFighterEntity other, int ticks) {
        if (other == null || other == this || other.level() != level() || level().isClientSide
                || meditationPartnerPlayer != null || other.meditationPartnerPlayer != null) return false;
        if (isMeditating() || other.isMeditating()) return false;
        int session = Math.max(240, ticks);
        if (!beginMeditation(session, true)) return false;
        if (!other.beginMeditation(session, true)) {
            stopMeditation(false);
            return false;
        }
        meditationPartnerNpc = other.getUUID();
        other.meditationPartnerNpc = getUUID();
        int bond = com.dmzlivingworld.world.FighterNpcSocialManager.bond(this, other);
        int tier = bond >= 9 ? 3 : bond >= 6 ? 2 : bond >= 4 ? 1 : 0;
        entityData.set(MEDITATION_BOND_TIER, tier);
        other.entityData.set(MEDITATION_BOND_TIER, tier);
        return true;
    }

    public boolean isMeditatingWith(AmbientFighterEntity other) {
        return other != null && isMeditating() && other.getUUID().equals(meditationPartnerNpc);
    }

    /** Joins a real NPC meditation circle. No teleporting: callers only invite already-near fighters. */
    public boolean beginMeditationCircle(Vec3 center, int ticks) {
        if (center == null || isMeditating()) return false;
        if (!beginMeditation(Math.max(240, ticks), true)) return false;
        setMeditationCircleCenter(center);
        return true;
    }

    /** Converts an existing pair/solo meditation into a shared visual center without replacing its lifecycle. */
    public void setMeditationCircleCenter(Vec3 center) {
        if (center == null || !isMeditating()) return;
        meditationCircleActive = true;
        entityData.set(MEDITATION_CIRCLE_MEMBER, true);
        meditationCircleX = center.x;
        meditationCircleY = center.y;
        meditationCircleZ = center.z;
        faceMeditationPoint(center);
    }

    private void faceMeditationPoint(Vec3 point) {
        if (point == null) return;
        double dx = point.x - getX();
        double dz = point.z - getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float)(net.minecraft.util.Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
        getLookControl().setLookAt(point.x, point.y + 0.4D, point.z, 40.0F, 35.0F);
    }

    private int meditationBondTier(ServerPlayer player) {
        int relationship = com.dmzlivingworld.world.FighterRelationshipManager.relationshipOrUnknown(player, this);
        if (relationship >= 85 && relationship <= 100) return 3;
        if (relationship >= 60 && relationship <= 100) return 2;
        if (relationship >= 35 && relationship <= 100) return 1;
        return 0;
    }

    private void faceMeditationPartner(Entity partner) {
        if (partner == null) return;
        double dx = partner.getX() - getX();
        double dz = partner.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float)(net.minecraft.util.Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
        getLookControl().setLookAt(partner, 40.0F, 40.0F);
    }

    public void stopMeditation(boolean completed) {
        if (meditationApproachPlayer != null) clearMeditationApproach();
        if (!isMeditating()) return;
        entityData.set(MEDITATING, false);
        entityData.set(MEDITATION_BOND_TIER, 0);
        entityData.set(MEDITATION_PLAYER_ID, "");
        meditationAnchorSet = false;
        int elapsed = Math.max(0, meditationSessionLength - meditationTicks);
        meditationCooldown = 600 + getRandom().nextInt(1201);
        meditationTicks = 0;
        meditationSessionLength = 0;
        meditationPartnerPlayer = null;
        meditationPlayerJoinedExistingSolo = false;
        meditationPartnerNpc = null;
        meditationCircleActive = false;
        entityData.set(MEDITATION_CIRCLE_MEMBER, false);
        nextMeditationDialogueTick = 0L;
        if (elapsed >= 120) {
            // A real interrupted session still happened. Keep proportional credit rather than
            // deleting minutes of meditation because combat/player movement ended it early.
            applyTrainingGrowth(elapsed, true);
            FighterLifeNeedsManager.onMeditationCompleted(this, elapsed);
        }
        // Meditation temporarily suppresses combat locomotion; explicitly restore the
        // learned movement capability so being interrupted cannot leave a caster rooted.
        setCanFly(hasFlightUnlocked() && !isNonCombatant());
        setFlying(false);
        setFlyingFast(false);
        setNoGravity(false);
        // Meditation owns a DMZ-native full-body movement state. Hand control back explicitly so
        // the next Meeting/Walk/ambient activity can never inherit a stale meditation/cross-leg clip.
        setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
        setPose(Pose.STANDING);
        setAmbientPose(0);
        setDeltaMovement(getDeltaMovement().multiply(0.45D, 1.0D, 0.45D));
    }

    private void tickMeditation() {
        // World Settings should take effect immediately. If NPC Meditation is switched off
        // while a fighter is already meditating, end the NPC session cleanly on the server.
        if (!level().isClientSide && !com.dmzlivingworld.compat.MeditationCompat.isNpcMeditationEnabled()) {
            stopMeditation(false);
            return;
        }
        suppressCombatIntent();
        getNavigation().stop();
        // Meditation is an anchored state. Horizontal mob/pathing drift made partners visibly
        // shuffle a few pixels every tick, so pin X/Z while still allowing normal vertical physics.
        if (!meditationAnchorSet) {
            meditationAnchorSet = true;
            meditationAnchorX = getX();
            meditationAnchorZ = getZ();
        }
        if (Math.abs(getX() - meditationAnchorX) > 0.005D || Math.abs(getZ() - meditationAnchorZ) > 0.005D)
            setPos(meditationAnchorX, getY(), meditationAnchorZ);
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        if (meditationPartnerNpc != null && level() instanceof ServerLevel server) {
            Entity found = server.getEntity(meditationPartnerNpc);
            if (!(found instanceof AmbientFighterEntity partner) || !partner.isMeditatingWith(this)
                    || partner.distanceToSqr(this) > 14.0D * 14.0D) {
                stopMeditation(true);
                return;
            }
            faceMeditationPartner(partner);
        }
        if (meditationPartnerPlayer != null && level() instanceof ServerLevel server) {
            ServerPlayer partner = server.getServer().getPlayerList().getPlayer(meditationPartnerPlayer);
            if (partner == null || !com.dmzlivingworld.compat.MeditationCompat.isPlayerMeditating(partner)
                    || partner.distanceToSqr(this) > 14.0D * 14.0D) {
                if (meditationPlayerJoinedExistingSolo) {
                    // The player merely joined an already-running NPC session. Leaving must only
                    // detach that guest; the NPC keeps its original ticks/anchor and stops naturally.
                    meditationPartnerPlayer = null;
                    meditationPlayerJoinedExistingSolo = false;
                    entityData.set(MEDITATION_PLAYER_ID, "");
                    entityData.set(MEDITATION_BOND_TIER, 0);
                } else {
                    stopMeditation(true);
                    return;
                }
            } else {
                faceMeditationPartner(partner);
            }
        }
        if (meditationCircleActive) faceMeditationPoint(new Vec3(meditationCircleX, meditationCircleY, meditationCircleZ));
        // Native saga AI can briefly acquire a target during super.aiStep() even while LW owns
        // this meditation scene. Only real recent damage interrupts the committed activity; a
        // harmless transient target is cleared instead of making a several-minute meditation
        // collapse after a few seconds.
        long lastDamage = getPersistentData().getLong("LWLastDamageTime");
        boolean genuinelyInterrupted = hurtTime > 0 && lastDamage > 0L && level().getGameTime() - lastDamage <= 45L;
        if (genuinelyInterrupted) {
            stopMeditation(false);
            return;
        }
        if (getTarget() != null) suppressCombatIntent();
        if (!level().isClientSide && nextMeditationDialogueTick > 0L && level().getGameTime() >= nextMeditationDialogueTick) {
            nextMeditationDialogueTick = level().getGameTime() + 800L + getRandom().nextInt(1401);
            if (getSpeech().isEmpty() && getRandom().nextFloat() < 0.38F) {
                boolean shared = meditationPartnerPlayer != null || meditationPartnerNpc != null;
                speak(FighterDialogue.meditationWisdom(getRandom(), getPersonality(), shared), 92);
            }
        }
        if (!level().isClientSide && tickCount % 100 == Math.floorMod(getId(), 100)) {
            FighterBattleGrowthManager.onMeditationPulse(this);
        }
        if (meditationTicks > 0) meditationTicks--;
        if (level() instanceof ServerLevel server && tickCount % 20 == Math.floorMod(getId(), 20)) {
            List<ServerPlayer> meditators = server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12.0D),
                    com.dmzlivingworld.compat.MeditationCompat::isPlayerMeditating);
            for (ServerPlayer player : meditators) {
                int partners = server.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(12.0D),
                        fighter -> fighter.isAlive() && fighter.isMeditating()).size();
                com.dmzlivingworld.compat.MeditationCompat.updateExternalMeditationPartners(player, partners);
            }
        }
        if (tickCount % 18 == Math.floorMod(getId(), 18)) {
            boolean shared = false;
            if (level() instanceof ServerLevel server) {
                shared = !server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12.0D),
                        com.dmzlivingworld.compat.MeditationCompat::isPlayerMeditating).isEmpty();
            }
            com.dmzlivingworld.compat.MeditationCompat.spawnNpcMeditationVisual(this, shared);
        }
        if (meditationTicks <= 0) {
            stopMeditation(true);
        }
    }

    /** Used by sparring/meditation scenes and recurring-character progression. */
    public void applyTrainingGrowth(int effortTicks, boolean meditation) {
        if (level().isClientSide || effortTicks < 100) return;
        int powerBeforeTraining = getPermanentBattlePower();
        double deferredBeforeTraining = FighterBattleGrowthManager.deferredBattlePower(this);
        trainingSessions++;
        if (trainingSessions == 5 || trainingSessions == 15 || trainingSessions == 30)
            recordLegacyEvent("Completed " + trainingSessions + " training sessions");
        double base = meditation ? 0.0052D : 0.0070D;
        double diminishing = 1.0D / (1.0D + trainingSessions * 0.035D);
        double gain = base * diminishing * Math.min(1.7D, Math.max(0.12D, effortTicks / 1200.0D));
        gain *= RedRibbonExperimentManager.trainingEfficiency(this);
        if (getRank() == FighterRank.ROOKIE) gain *= 1.18D;
        gain *= LivingWorldConfig.npcGrowthScale();
        if (level() instanceof net.minecraft.server.level.ServerLevel server)
            gain *= WorldPowerScaler.earnedGrowthMultiplier(server, this);
        // R9 pays a visible part of ordinary training during the session. Reconcile that exact
        // advance here so the end-of-training result remains on the established R8.1 budget.
        if (!meditation)
            gain = FighterBattleGrowthManager.remainingAdjustedFraction(this, FighterBattleGrowthManager.Source.TRAINING, gain);
        double earnedCeiling = level() instanceof net.minecraft.server.level.ServerLevel server
                ? WorldPowerScaler.earnedProgressionCeiling(server, blockPosition(), getRank(), this) : getPermanentBattlePower();
        // Conditioning is part of the real physical profile. Apply it before rebuilding attributes
        // so the same completed session updates BP, health, melee and Ki as one atomic progression.
        FighterBattleGrowthManager.onTraining(this, effortTicks, meditation);
        // R14 keeps the exact established completion budget but makes every training/meditation
        // finish readable over ~5 seconds instead of presenting it as a lump or the combat queue's
        // ~30-second tail. Spar/battle settlement remains on its separate R13 cadence.
        FighterBattleGrowthManager.queueFastAdjustedFraction(this, gain, earnedCeiling);
        // Make the completion visibly begin paying immediately. Remaining BP still settles over
        // the established fast queue rather than becoming a one-tick lump.
        FighterBattleGrowthManager.settleFastDeferredGrowthNow(this);
        factionMerit += meditation ? 1 : 2;
        if (getPermanentBattlePower() > powerBeforeTraining
                || FighterBattleGrowthManager.deferredBattlePower(this) > deferredBeforeTraining + 0.000001D) {
            ReactiveWorldManager.rememberEvent(this, "TRAINING_GROWTH", getFighterName(), meditation
                    ? "felt their Ki settle into something stronger after meditating"
                    : "made real progress in training");
        }

        // DMZ flight is a learned all-race skill. Training can permanently unlock it.
        if (!hasFlightUnlocked() && !isNonCombatant()) {
            int flightThreshold = 3 + Math.floorMod(getUUID().hashCode(), 5);
            if (trainingSessions >= flightThreshold) {
                entityData.set(FLIGHT_UNLOCKED, true);
                setCanFly(true);
                recordLegacyEvent("Learned flight through training");
                if (getSpeech().isEmpty()) speak("I think I've got it... I can fly.", 64);
            }
        }

        // Racial forms follow DMZ 2.1.3's normal main tree. Growth is deliberately
        // slow; legendary/alternate trees are not handed out by procedural training.
        if (getRank() != FighterRank.ROOKIE && getRacialSkillLevel() < NpcFormConfigBridge.maxSkillLevel(getRace())) {
            int racialEffort = (int)Math.floor(Math.max(0.0D, effortTicks / (meditation ? 120.0D : 90.0D)) * LivingWorldConfig.npcGrowthScale());
            racialTrainingProgress += Math.max(0, racialEffort);
            int next = NpcFormConfigBridge.nextUnlockLevel(getRace(), getRacialSkillLevel());
            int threshold = 95 + next * 42 + Math.floorMod(getUUID().hashCode(), 45);
            if (next > getRacialSkillLevel() && racialTrainingProgress >= threshold) {
                racialTrainingProgress -= threshold;
                entityData.set(RACIAL_SKILL_LEVEL, next);
                RacialFormProfile unlocked = RacialFormProfile.forSkill(getRace(), next);
                if (unlocked != null) {
                    recordLegacyEvent("Unlocked " + unlocked.displayName() + " through training");
                    FighterGoalManager.onRacialAdvanced(this);
                    if (getSpeech().isEmpty()) speak("I finally understand " + unlocked.displayName() + ".", 72);
                }
            }
        }

        FighterGoalManager.onTraining(this);
        FighterPromotionManager.evaluate(this);
    }

    private static boolean rollInitialFlight(RandomSource random, FighterRank rank) {
        return switch (rank) {
            case ROOKIE -> random.nextFloat() < 0.16F;
            case TRAINED -> random.nextFloat() < 0.70F;
            case VETERAN -> random.nextFloat() < 0.96F;
        };
    }

    private static int rollInitialRacialSkill(RandomSource random, FighterRank rank, FighterRace race) {
        if (rank == FighterRank.ROOKIE) return 0;
        int max = NpcFormConfigBridge.maxSkillLevel(race);
        if (rank == FighterRank.TRAINED) return random.nextFloat() < 0.14F ? Math.min(1, max) : 0;
        float roll = random.nextFloat();
        if (roll < 0.34F) return 0;
        if (roll < 0.76F) return Math.min(1, max);
        return Math.min(NpcFormConfigBridge.nextUnlockLevel(race, 1), max);
    }

    private boolean rollKaiokenPotential(RandomSource random) {
        if (getRank() == FighterRank.ROOKIE) return false;
        if (!(getArchetype() == FighterArchetype.MARTIAL_ARTIST || getArchetype() == FighterArchetype.BRAWLER
                || getArchetype() == FighterArchetype.SPEEDSTER || getArchetype() == FighterArchetype.GUARDIAN)) return false;
        float chance = getRank() == FighterRank.VETERAN ? 0.19F : 0.055F;
        if (getPersonality() == FighterPersonality.CALM) chance += 0.025F;
        if (getPersonality() == FighterPersonality.CAUTIOUS) chance -= 0.02F;
        return random.nextFloat() < chance;
    }

    private void tryStartKaioken() {
        if (!kaiokenPotential || isKaiokenActive() || isAwakened() || isRacialFormActive() || isTransforming() || isMeditating()
                || getTarget() == null || !getTarget().isAlive() || tickCount < 120) return;
        float hp = getHealth() / Math.max(1.0F, getMaxHealth());
        if (hp > 0.62F && tickCount % 200 != 0) return;
        double targetPower = estimateBattlePower(getTarget());
        boolean pressured = hp < 0.48F || targetPower > Math.max(1, getBattlePower()) * 1.18D;
        if (!pressured) return;
        long stable = FactionWorldData.mix(getUUID().getMostSignificantBits() ^ getTarget().getUUID().getLeastSignificantBits());
        if (Math.floorMod(stable, 100L) >= (getRank() == FighterRank.VETERAN ? 58L : 28L)) return;
        int level = 2;
        int roll = getRandom().nextInt(100);
        if (getRank() == FighterRank.VETERAN && roll < 7) level = 10;
        else if (roll < 28) level = 4;
        else if (roll < 57) level = 3;
        startKaioken(level);
    }

    public boolean startKaioken(int level) {
        if (isKaiokenActive() || isRacialFormActive() || isMeditating() || isTransforming()) return false;
        if (level != 2 && level != 3 && level != 4 && level != 10) level = 2;
        NpcFormConfigBridge.Form configured = NpcFormConfigBridge.kaioken(level);
        double multiplier = configured == null ? kaiokenMultiplier(level) : configured.melee();
        kaiokenBasePower = getPermanentBattlePower();
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        kaiokenBaseAttack = attack == null ? getRank().attackDamage() : attack.getBaseValue();
        kaiokenBaseSpeed = speed == null ? getRank().speed() : speed.getBaseValue();
        kaiokenBaseKiDamage = getKiBlastDamage();
        kaiokenBaseAuraType = getAuraType() == null ? "" : getAuraType();
        kaiokenBaseAuraColor = getAuraColor();
        entityData.set(KAIOKEN_LEVEL, level);
        kaiokenTicks = 260 + getRandom().nextInt(getRank() == FighterRank.VETERAN ? 441 : 241);
        refreshTemporaryPowerProjection();
        if (attack != null) attack.setBaseValue(kaiokenBaseAttack * multiplier);
        if (speed != null) speed.setBaseValue(kaiokenBaseSpeed * (configured == null ? multiplier : configured.speed()));
        setKiBlastDamage((float)(kaiokenBaseKiDamage * (configured == null ? multiplier : configured.ki())));
        setAuraType("kakarot");
        setAuraColor(0xDB182C);
        setLightning(false);
        setTransforming(true);
        // A short ignition burst, then only restrained periodic aura pulses. Rendering
        // Kaioken as a permanent saga-effect pass produced far too many sparks.
        flareAura(22);
        level().playSound(null, blockPosition(), MainSounds.TRANSFORM_ON.get(), SoundSource.HOSTILE, 1.2F, 1.05F);
        String formSuffix = isRacialFormActive() && getActiveRacialForm() != null ? " with " + getActiveRacialForm().displayName() : "";
        String line = switch (getRandom().nextInt(4)) {
            case 0 -> "Kaioken times " + level + formSuffix + "!";
            case 1 -> "Let's raise it—Kaioken times " + level + formSuffix + "!";
            case 2 -> "Here goes... Kaioken times " + level + formSuffix + "!";
            default -> "Kaioken, times " + level + formSuffix + "!";
        };
        speak(line, 68);
        return true;
    }

    private void tickKaioken() {
        if (kaiokenTicks > 0) kaiokenTicks--;
        if (isTransforming() && kaiokenTicks < 235) setTransforming(false);
        if (tickCount % 20 == 0) {
            double drain = switch (getKaiokenLevel()) {
                case 3 -> 0.06D; case 4 -> 0.095D; case 10 -> 0.16D; default -> 0.03D;
            };
            // DMZ config healthDrain is preserved as the relative severity; the NPC
            // drain is paced to make the form risky without deleting an NPC in seconds.
            float amount = (float)(getMaxHealth() * drain * 0.075D);
            setHealth(Math.max(1.0F, getHealth() - amount));
            if (getHealth() <= getMaxHealth() * 0.10F) kaiokenTicks = 0;
        }
        if (kaiokenTicks <= 0 || isDefeated() || isCaptive()) stopKaioken();
    }

    private void stopKaioken() {
        if (!isKaiokenActive()) return;
        FighterPowerStatScaler.preserveCurrentMaxHealth(this);
        entityData.set(KAIOKEN_LEVEL, 0);
        setTransforming(false);
        refreshTemporaryPowerProjection();
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attack != null && kaiokenBaseAttack > 0) attack.setBaseValue(kaiokenBaseAttack);
        if (speed != null && kaiokenBaseSpeed > 0) speed.setBaseValue(kaiokenBaseSpeed);
        if (kaiokenBaseKiDamage > 0) setKiBlastDamage(kaiokenBaseKiDamage);
        setAuraType(kaiokenBaseAuraType == null ? "" : kaiokenBaseAuraType);
        setAuraColor(kaiokenBaseAuraColor);
        auraFlareTicks = 0;
        entityData.set(AURA_FLARED, false);
        setLightning(false);
        setKiCharge(false);
        kaiokenTicks = 0;
        if (!isRacialFormActive()) refreshCombatStatsFromPower();
        level().playSound(null, blockPosition(), MainSounds.TRANSFORM_OFF.get(), SoundSource.HOSTILE, 0.8F, 0.95F);
    }

    private static double kaiokenMultiplier(int level) {
        return switch (level) { case 3 -> 1.20D; case 4 -> 1.35D; case 10 -> 1.50D; default -> 1.10D; };
    }

    public void speak(String text, int ticks) {
        if (level().isClientSide || text == null || text.isBlank()) return;
        // The hidden NPC inside an active player/NPC fusion is persistence state, not a world
        // speaker. This central gate prevents every ambient/reactive/debug system from making the
        // invisible passenger talk even if a future caller forgets to filter its entity query.
        if (com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(this)) return;
        String clean = sanitizeDialogue(text);
        if (clean.isBlank()) return;
        clean = DialogueLocalityManager.resolve(this, clean);
        if (clean == null || clean.isBlank()) return;
        entityData.set(SPEECH, clean);
        speechTicks = Math.max(20, ticks);
        rememberDialogue(clean);
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ChatFormatting nameColor = WorldMenaceManager.isHerobrine(this)
                    ? ChatFormatting.RED : ChatFormatting.AQUA;
            Component chat = Component.literal(getFighterName()).withStyle(nameColor)
                    .append(Component.literal(": " + clean).withStyle(ChatFormatting.WHITE));
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(15.0D),
                    p -> !p.isSpectator() && p.distanceToSqr(this) <= 225.0D)) {
                player.sendSystemMessage(chat);
            }
        }
    }

    public List<String> getDialogueHistory() { return List.copyOf(dialogueHistory); }

    /** User-facing dossier cleanup only; current speech and every other fighter memory remain intact. */
    public void clearDialogueHistory() { dialogueHistory.clear(); }

    private void rememberDialogue(String text) {
        if (text == null || text.isBlank()) return;
        while (dialogueHistory.size() >= MAX_DIALOGUE_HISTORY) dialogueHistory.remove(0);
        dialogueHistory.add(sanitizeDialogue(text));
    }

    private static String sanitizeDialogue(String text) {
        if (text == null) return "";
        String clean = text.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= MAX_DIALOGUE_LENGTH ? clean : clean.substring(0, MAX_DIALOGUE_LENGTH);
    }

    private void writeDialogueHistory(CompoundTag tag, String key) {
        ListTag list = new ListTag();
        int start = Math.max(0, dialogueHistory.size() - MAX_DIALOGUE_HISTORY);
        for (int i = start; i < dialogueHistory.size(); i++) list.add(StringTag.valueOf(sanitizeDialogue(dialogueHistory.get(i))));
        tag.put(key, list);
    }

    private void readDialogueHistory(CompoundTag tag, String key) {
        dialogueHistory.clear();
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        int start = Math.max(0, list.size() - MAX_DIALOGUE_HISTORY);
        for (int i = start; i < list.size(); i++) {
            String clean = sanitizeDialogue(list.getString(i));
            if (!clean.isBlank()) dialogueHistory.add(clean);
        }
    }

    public void setCaptive(boolean captive) {
        entityData.set(CAPTIVE, captive);
        setInvulnerable(captive);
        setNoAi(captive);
        if (captive) {
            suppressCombatIntent();
            speak(FighterDialogue.captive(getRandom()), 70);
        } else {
            recoveryGraceTicks = Math.max(recoveryGraceTicks, 80);
        }
    }

    /**
     * R29 faction-request cleanup boundary. Mission AI can leave a fighter airborne, in combat locomotion,
     * yielded, or with navigation deliberately stopped. Returning them to normal Living World life must clear
     * those transient ownership states immediately instead of waiting for a later flight/routine watchdog.
     */
    public void finishFactionRequestAssignment() {
        setTarget(null);
        setLastHurtByMob(null);
        setLastHurtMob(null);
        getNavigation().stop();
        interruptCombo();
        stopCasting();
        setAttacking(false);
        setAggressive(false);
        setKiCharge(false);
        setZanzokenState(false);
        setEvading(false);
        setFlyingFast(false);
        if (!isCaptive()) {
            setNoAi(false);
            setInvulnerable(false);
            setFlying(false);
            setNoGravity(false);
            setStoryRole(STORY_NONE);
            setDeltaMovement(getDeltaMovement().multiply(0.55D, 0.35D, 0.55D));
            recoveryGraceTicks = Math.max(recoveryGraceTicks, 20);
        } else {
            // A genuine prisoner intentionally remains immobile/invulnerable until the prisoner system releases them.
            setNoAi(true);
            setInvulnerable(true);
            setStoryRole(STORY_CAPTIVE);
        }
        setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
        socialPlayerApproach = false;
        socialPowerDisplay = false;
        socialLifeActivity = false;
        ambientFlightActivity = false;
        getPersistentData().remove("LWUnownedFlightTicks");
    }

    /** Character object expected by DragonMineZ's own HairRenderer. */
    public Character getDMZCharacter() {
        Character character = new Character();
        character.setRace(getRace().dmzId());
        character.setGender(isFemale() ? Character.GENDER_FEMALE : Character.GENDER_MALE);
        character.setCharacterClass(Character.CLASS_WARRIOR);
        character.setBodyType(getBodyType());
        character.setEyesType(getEyesType());
        character.setNoseType(getNoseType());
        character.setMouthType(getMouthType());
        character.setHairId(getHairId());
        character.setRenderHairBase(getRace().usesHair());
        character.setBodyColor(getBodyColor());
        character.setBodyColor2(getBodyColor2());
        character.setBodyColor3(getBodyColor3());
        character.setHairColor(getHairColor());
        character.setEye1Color(getEye1Color());
        character.setEye2Color(getEye2Color());
        character.setAuraColor("#FFFFFF");
        // Do not synthesize a Saiyan tail here. DMZ's real player tail is rendered by
        // its separate race-parts pipeline (tailenrolled + character state), not this model.
        character.setHasSaiyanTail(false);
        if (getRace().usesHair()) {
            CustomHair hair = HairManager.getPresetHair(getHairId(), getRace().dmzId());
            if (hair == null || hair.isEmpty()) hair = HairManager.getPresetHair(getHairId(), "human");
            character.setHairBase(hair);
        }
        return character;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LWDataVersion", DATA_VERSION);
        tag.put("LWLegacy", legacyData.copy());
        writeDialogueHistory(tag, "LWDialogueHistory");
        tag.putBoolean("LWArsenalInitialized", arsenalInitialized);
        tag.putBoolean("LWReady", entityData.get(READY));
        tag.putBoolean("LWDeadSoul", isDeadSoul());
        tag.putInt("LWAlignment", getAlignment().id());
        tag.putInt("LWRank", getRank().id());
        tag.putInt("LWPersonality", getPersonality().id());
        tag.putInt("LWRace", getRace().id());
        tag.putInt("LWArchetype", getArchetype().id());
        tag.putBoolean("LWCaptive", isCaptive());
        tag.putBoolean("LWDefeated", isDefeated());
        tag.putInt("LWDefeatedTicks", defeatedTicks);
        tag.putInt("LWRecoveryGrace", recoveryGraceTicks);
        tag.putInt("LWCinematicLaunchCooldown", cinematicLaunchCooldown);
        tag.putInt("LWRetreatTicks", retreatTicks);
        if (retreatThreatId != null) tag.putUUID("LWRetreatThreat", retreatThreatId);
        if (duelOpponentId != null) tag.putUUID("LWDuelOpponent", duelOpponentId);
        tag.putString("LWFighterName", getFighterName());
        tag.putInt("LWBattlePower", getBattlePower());
        tag.putBoolean("LWAwakened", isAwakened());
        tag.putInt("LWAwakeningTicks", awakeningTicks);
        tag.putFloat("LWDisplayScale", getDisplayScale());
        if (memoryOwnerId != null) tag.putUUID("LWMemoryOwner", memoryOwnerId);
        if (memoryRecordId != null) tag.putUUID("LWMemoryRecord", memoryRecordId);
        tag.putInt("LWMemoryEncounters", memoryEncounters);
        tag.putInt("LWMemoryRelationship", memoryRelationship);
        tag.putBoolean("LWMemoryRescued", memoryRescued);
        tag.putString("LWFactionId", getFactionId());
        tag.putBoolean("LWFactionLeader", isFactionLeader());
        tag.putInt("LWFactionRole", getFactionRole().id());
        tag.putString("LWFactionName", getFactionDisplayName());
        tag.putString("LWFactionTitle", getFactionTitle());
        tag.putBoolean("LWAuraFlared", isAuraFlared());
        tag.putInt("LWAuraFlareTicks", auraFlareTicks);
        tag.putBoolean("LWAngerAuraUsed", angerAuraUsed);
        tag.putInt("LWFoodSupplies", foodSupplies);
        tag.putInt("LWSenzuBeans", senzuBeans);
        tag.putInt("LWSupplyCooldown", supplyCooldown);
        tag.putBoolean("LWRegionalPresence", regionalPresence);
        tag.putBoolean("LWNonCombatant", isNonCombatant());
        tag.putString("LWWantedId", getWantedId());
        tag.putInt("LWWantedLevel", getWantedLevel());
        tag.putString("LWWantedCrime", getWantedCrime());
        tag.putInt("LWFactionMerit", factionMerit);
        tag.putBoolean("LWMeditating", isMeditating());
        tag.putInt("LWMeditationTicks", meditationTicks);
        tag.putInt("LWMeditationLength", meditationSessionLength);
        tag.putInt("LWMeditationCooldown", meditationCooldown);
        if (meditationPartnerPlayer != null) tag.putUUID("LWMeditationPartner", meditationPartnerPlayer);
        tag.putBoolean("LWMeditationPlayerJoinedExistingSolo", meditationPlayerJoinedExistingSolo);
        if (meditationPartnerNpc != null) tag.putUUID("LWMeditationNpcPartner", meditationPartnerNpc);
        tag.putBoolean("LWMeditationCircle", meditationCircleActive);
        if (meditationCircleActive) {
            tag.putDouble("LWMeditationCircleX", meditationCircleX);
            tag.putDouble("LWMeditationCircleY", meditationCircleY);
            tag.putDouble("LWMeditationCircleZ", meditationCircleZ);
        }
        tag.putInt("LWTrainingSessions", trainingSessions);
        tag.putBoolean("LWFlightUnlocked", hasFlightUnlocked());
        tag.putInt("LWRacialSkillLevel", getRacialSkillLevel());
        tag.putInt("LWRacialTrainingProgress", racialTrainingProgress);
        tag.putInt("LWActiveRacialForm", getActiveRacialFormLevel());
        tag.putInt("LWCosmeticAccessory", getCosmeticAccessoryId());
        tag.putInt("LWRacialBasePower", racialBasePower);
        tag.putDouble("LWRacialBaseAttack", racialBaseAttack);
        tag.putDouble("LWRacialBaseSpeed", racialBaseSpeed);
        tag.putDouble("LWRacialBaseAttackSpeed", racialBaseAttackSpeed);
        tag.putFloat("LWRacialBaseKiDamage", racialBaseKiDamage);
        tag.putFloat("LWRacialBaseScale", racialBaseScale);
        tag.putString("LWRacialBaseHair", racialBaseHairColor == null ? "" : racialBaseHairColor);
        tag.putString("LWRacialBaseEye1", racialBaseEye1Color == null ? "" : racialBaseEye1Color);
        tag.putString("LWRacialBaseEye2", racialBaseEye2Color == null ? "" : racialBaseEye2Color);
        tag.putString("LWRacialBaseAuraType", racialBaseAuraType == null ? "" : racialBaseAuraType);
        tag.putInt("LWRacialBaseAuraColor", racialBaseAuraColor);
        tag.putBoolean("LWRacialBaseLightning", racialBaseLightning);
        tag.putInt("LWStoryRole", getStoryRole());
        tag.remove("LWMentorName");
        tag.putString("LWRivalName", rivalName);
        tag.putBoolean("LWKaiokenPotential", kaiokenPotential);
        tag.putInt("LWKaiokenLevel", getKaiokenLevel());
        tag.putInt("LWKaiokenTicks", kaiokenTicks);
        tag.putInt("LWKaiokenBasePower", kaiokenBasePower);
        tag.putDouble("LWKaiokenBaseAttack", kaiokenBaseAttack);
        tag.putDouble("LWKaiokenBaseSpeed", kaiokenBaseSpeed);
        tag.putFloat("LWKaiokenBaseKiDamage", kaiokenBaseKiDamage);
        tag.putString("LWKaiokenBaseAuraType", kaiokenBaseAuraType == null ? "" : kaiokenBaseAuraType);
        tag.putInt("LWKaiokenBaseAuraColor", kaiokenBaseAuraColor);
        if (partyId != null) tag.putUUID("LWPartyId", partyId);
        tag.putBoolean("LWPartyCaptain", partyCaptain);
        tag.putInt("LWGender", entityData.get(GENDER));
        tag.putInt("LWBodyType", getBodyType());
        tag.putInt("LWEyesType", getEyesType());
        tag.putInt("LWNoseType", getNoseType());
        tag.putInt("LWMouthType", getMouthType());
        tag.putInt("LWHeadBone", getHeadBone());
        tag.putInt("LWHairId", getHairId());
        tag.putInt("LWOutfit", getOutfit());
        tag.putString("LWBodyColor", getBodyColor());
        tag.putString("LWBodyColor2", getBodyColor2());
        tag.putString("LWBodyColor3", getBodyColor3());
        tag.putString("LWHairColor", getHairColor());
        tag.putString("LWEye1Color", getEye1Color());
        tag.putString("LWEye2Color", getEye2Color());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        legacyData = sanitizeLegacyData(tag.contains("LWLegacy", Tag.TAG_COMPOUND) ? tag.getCompound("LWLegacy").copy() : new CompoundTag());
        readDialogueHistory(tag, "LWDialogueHistory");
        arsenalInitialized = tag.contains("LWArsenalInitialized") && tag.getBoolean("LWArsenalInitialized");
        if (tag.contains("LWReady")) entityData.set(READY, tag.getBoolean("LWReady"));
        setDeadSoul(tag.getBoolean("LWDeadSoul") || getPersistentData().getBoolean("LWDeadSoul"));
        if (tag.contains("LWAlignment")) entityData.set(ALIGNMENT, tag.getInt("LWAlignment"));
        if (tag.contains("LWRank")) entityData.set(RANK, tag.getInt("LWRank"));
        if (tag.contains("LWPersonality")) entityData.set(PERSONALITY, tag.getInt("LWPersonality"));
        else entityData.set(PERSONALITY, FighterPersonality.roll(getRandom(), getAlignment()).id());
        if (tag.contains("LWRace")) entityData.set(RACE, tag.getInt("LWRace"));
        else entityData.set(RACE, FighterRace.HUMAN.id());
        if (tag.contains("LWArchetype")) entityData.set(ARCHETYPE, tag.getInt("LWArchetype"));
        else entityData.set(ARCHETYPE, FighterArchetype.roll(getRandom(), getRank()).id());
        if (tag.contains("LWCaptive")) entityData.set(CAPTIVE, tag.getBoolean("LWCaptive"));
        if (tag.contains("LWDefeated")) entityData.set(DEFEATED, tag.getBoolean("LWDefeated"));
        defeatedTicks = tag.contains("LWDefeatedTicks") ? tag.getInt("LWDefeatedTicks") : 0;
        recoveryGraceTicks = tag.contains("LWRecoveryGrace") ? tag.getInt("LWRecoveryGrace") : 0;
        cinematicLaunchCooldown = tag.contains("LWCinematicLaunchCooldown") ? tag.getInt("LWCinematicLaunchCooldown") : 0;
        retreatTicks = tag.contains("LWRetreatTicks") ? tag.getInt("LWRetreatTicks") : 0;
        retreatThreatId = tag.hasUUID("LWRetreatThreat") ? tag.getUUID("LWRetreatThreat") : null;
        duelOpponentId = tag.hasUUID("LWDuelOpponent") ? tag.getUUID("LWDuelOpponent") : null;
        sanctionedMatchParticipant = false;
        sanctionedOpponentId = null;
        if (tag.contains("LWFighterName")) entityData.set(FIGHTER_NAME, tag.getString("LWFighterName"));
        int serializedBattlePower = tag.contains("LWBattlePower") ? Math.max(1, tag.getInt("LWBattlePower")) : Math.max(1, getBattlePower());
        if (tag.contains("LWBattlePower")) setBattlePower(serializedBattlePower);
        if (tag.contains("LWAwakened")) entityData.set(AWAKENED, tag.getBoolean("LWAwakened"));
        awakeningTicks = tag.contains("LWAwakeningTicks") ? tag.getInt("LWAwakeningTicks") : 0;
        if (awakeningTicks > 0) setTransforming(true);
        float restoredScale = tag.contains("LWDisplayScale") ? tag.getFloat("LWDisplayScale") : 1.0F;
        entityData.set(DISPLAY_SCALE, restoredScale);
        setScaleVal(restoredScale);
        memoryOwnerId = tag.hasUUID("LWMemoryOwner") ? tag.getUUID("LWMemoryOwner") : null;
        memoryRecordId = tag.hasUUID("LWMemoryRecord") ? tag.getUUID("LWMemoryRecord") : null;
        memoryEncounters = tag.contains("LWMemoryEncounters") ? tag.getInt("LWMemoryEncounters") : 0;
        memoryRelationship = tag.contains("LWMemoryRelationship") ? tag.getInt("LWMemoryRelationship") : 0;
        memoryRescued = tag.contains("LWMemoryRescued") && tag.getBoolean("LWMemoryRescued");
        if (memoryRecordId != null) setPersistenceRequired();
        entityData.set(FACTION_ID, tag.contains("LWFactionId") ? tag.getString("LWFactionId") : "");
        entityData.set(FACTION_LEADER, tag.contains("LWFactionLeader") && tag.getBoolean("LWFactionLeader"));
        entityData.set(FACTION_ROLE, tag.contains("LWFactionRole") ? tag.getInt("LWFactionRole")
                : (entityData.get(FACTION_LEADER) ? FactionRole.LEADER.id() : FactionRole.MEMBER.id()));
        entityData.set(FACTION_NAME, tag.contains("LWFactionName") ? tag.getString("LWFactionName") : "");
        entityData.set(FACTION_TITLE, tag.contains("LWFactionTitle") ? tag.getString("LWFactionTitle") : "");
        entityData.set(AURA_FLARED, tag.contains("LWAuraFlared") && tag.getBoolean("LWAuraFlared"));
        auraFlareTicks = tag.contains("LWAuraFlareTicks") ? tag.getInt("LWAuraFlareTicks") : 0;
        angerAuraUsed = tag.contains("LWAngerAuraUsed") && tag.getBoolean("LWAngerAuraUsed");
        foodSupplies = tag.contains("LWFoodSupplies") ? tag.getInt("LWFoodSupplies") : 0;
        senzuBeans = tag.contains("LWSenzuBeans") ? tag.getInt("LWSenzuBeans") : 0;
        supplyCooldown = tag.contains("LWSupplyCooldown") ? tag.getInt("LWSupplyCooldown") : 0;
        regionalPresence = tag.contains("LWRegionalPresence") && tag.getBoolean("LWRegionalPresence");
        entityData.set(NON_COMBATANT, tag.contains("LWNonCombatant") && tag.getBoolean("LWNonCombatant"));
        entityData.set(WANTED_ID, tag.contains("LWWantedId") ? tag.getString("LWWantedId") : "");
        entityData.set(WANTED_LEVEL, tag.contains("LWWantedLevel") ? tag.getInt("LWWantedLevel") : 0);
        entityData.set(WANTED_CRIME, tag.contains("LWWantedCrime") ? tag.getString("LWWantedCrime") : "");
        factionMerit = tag.contains("LWFactionMerit") ? tag.getInt("LWFactionMerit") : 0;
        entityData.set(MEDITATING, tag.contains("LWMeditating") && tag.getBoolean("LWMeditating"));
        meditationTicks = tag.contains("LWMeditationTicks") ? tag.getInt("LWMeditationTicks") : 0;
        meditationSessionLength = tag.contains("LWMeditationLength") ? tag.getInt("LWMeditationLength") : 0;
        meditationCooldown = tag.contains("LWMeditationCooldown") ? tag.getInt("LWMeditationCooldown") : 200;
        meditationPartnerPlayer = tag.hasUUID("LWMeditationPartner") ? tag.getUUID("LWMeditationPartner") : null;
        meditationPlayerJoinedExistingSolo = meditationPartnerPlayer != null
                && tag.contains("LWMeditationPlayerJoinedExistingSolo")
                && tag.getBoolean("LWMeditationPlayerJoinedExistingSolo");
        meditationPartnerNpc = tag.hasUUID("LWMeditationNpcPartner") ? tag.getUUID("LWMeditationNpcPartner") : null;
        meditationCircleActive = tag.contains("LWMeditationCircle") && tag.getBoolean("LWMeditationCircle");
        entityData.set(MEDITATION_CIRCLE_MEMBER, meditationCircleActive && isMeditating());
        meditationCircleX = tag.contains("LWMeditationCircleX") ? tag.getDouble("LWMeditationCircleX") : getX();
        meditationCircleY = tag.contains("LWMeditationCircleY") ? tag.getDouble("LWMeditationCircleY") : getY();
        meditationCircleZ = tag.contains("LWMeditationCircleZ") ? tag.getDouble("LWMeditationCircleZ") : getZ();
        entityData.set(MEDITATION_BOND_TIER, 0);
        entityData.set(MEDITATION_PLAYER_ID, meditationPartnerPlayer == null ? "" : meditationPartnerPlayer.toString());
        meditationAnchorSet = entityData.get(MEDITATING);
        meditationAnchorX = getX();
        meditationAnchorZ = getZ();
        trainingSessions = tag.contains("LWTrainingSessions") ? tag.getInt("LWTrainingSessions") : 0;
        entityData.set(FLIGHT_UNLOCKED, tag.contains("LWFlightUnlocked") ? tag.getBoolean("LWFlightUnlocked") : getRank().canFly());
        entityData.set(RACIAL_SKILL_LEVEL, tag.contains("LWRacialSkillLevel") ? tag.getInt("LWRacialSkillLevel") : 0);
        racialTrainingProgress = tag.contains("LWRacialTrainingProgress") ? tag.getInt("LWRacialTrainingProgress") : 0;
        entityData.set(ACTIVE_RACIAL_FORM_LEVEL, tag.contains("LWActiveRacialForm") ? tag.getInt("LWActiveRacialForm") : 0);
        entityData.set(COSMETIC_ACCESSORY, tag.contains("LWCosmeticAccessory") ? tag.getInt("LWCosmeticAccessory") : legacyData.getInt("CosmeticAccessoryId"));
        entityData.set(AMBIENT_POSE, 0);
        entityData.set(WEAPON_TRAINING_STRIKE, 0);
        entityData.set(FISHING_ACTIVITY, false);
        racialBasePower = tag.contains("LWRacialBasePower") ? tag.getInt("LWRacialBasePower") : 0;
        racialBaseAttack = tag.contains("LWRacialBaseAttack") ? tag.getDouble("LWRacialBaseAttack") : 0.0D;
        racialBaseSpeed = tag.contains("LWRacialBaseSpeed") ? tag.getDouble("LWRacialBaseSpeed") : 0.0D;
        racialBaseAttackSpeed = tag.contains("LWRacialBaseAttackSpeed") ? tag.getDouble("LWRacialBaseAttackSpeed") : getDefaultAttackSpeed();
        racialBaseKiDamage = tag.contains("LWRacialBaseKiDamage") ? tag.getFloat("LWRacialBaseKiDamage") : 0.0F;
        racialBaseScale = tag.contains("LWRacialBaseScale") ? tag.getFloat("LWRacialBaseScale") : getDisplayScale();
        racialBaseHairColor = tag.contains("LWRacialBaseHair") ? tag.getString("LWRacialBaseHair") : getHairColor();
        racialBaseEye1Color = tag.contains("LWRacialBaseEye1") ? tag.getString("LWRacialBaseEye1") : getEye1Color();
        racialBaseEye2Color = tag.contains("LWRacialBaseEye2") ? tag.getString("LWRacialBaseEye2") : getEye2Color();
        racialBaseAuraType = tag.contains("LWRacialBaseAuraType") ? tag.getString("LWRacialBaseAuraType") : getAuraType();
        racialBaseAuraColor = tag.contains("LWRacialBaseAuraColor") ? tag.getInt("LWRacialBaseAuraColor") : getAuraColor();
        racialBaseLightning = tag.contains("LWRacialBaseLightning") && tag.getBoolean("LWRacialBaseLightning");
        entityData.set(STORY_ROLE, tag.contains("LWStoryRole") ? tag.getInt("LWStoryRole") : STORY_NONE);
        legacyData.remove("MentorStudentLocked");
        legacyData.remove("NativeMentor");
        rivalName = tag.getString("LWRivalName");
        kaiokenPotential = tag.contains("LWKaiokenPotential") ? tag.getBoolean("LWKaiokenPotential") : rollKaiokenPotential(getRandom());
        entityData.set(KAIOKEN_LEVEL, tag.contains("LWKaiokenLevel") ? tag.getInt("LWKaiokenLevel") : 0);
        kaiokenTicks = tag.contains("LWKaiokenTicks") ? tag.getInt("LWKaiokenTicks") : 0;
        kaiokenBasePower = tag.contains("LWKaiokenBasePower") ? tag.getInt("LWKaiokenBasePower") : 0;
        kaiokenBaseAttack = tag.contains("LWKaiokenBaseAttack") ? tag.getDouble("LWKaiokenBaseAttack") : 0.0D;
        kaiokenBaseSpeed = tag.contains("LWKaiokenBaseSpeed") ? tag.getDouble("LWKaiokenBaseSpeed") : 0.0D;
        kaiokenBaseKiDamage = tag.contains("LWKaiokenBaseKiDamage") ? tag.getFloat("LWKaiokenBaseKiDamage") : 0.0F;
        kaiokenBaseAuraType = tag.contains("LWKaiokenBaseAuraType") ? tag.getString("LWKaiokenBaseAuraType") : "";
        kaiokenBaseAuraColor = tag.contains("LWKaiokenBaseAuraColor") ? tag.getInt("LWKaiokenBaseAuraColor") : 0xFFFFFF;
        // Never preserve a temporary Kaioken multiplier across save/reload. Restore the
        // pre-Kaioken combat profile instead of silently turning a temporary form permanent.
        boolean restoreKaioken = getKaiokenLevel() > 0;
        if (restoreKaioken) {
            entityData.set(KAIOKEN_LEVEL, 0);
            kaiokenTicks = 0;
            setTransforming(false);
            if (kaiokenBasePower > 0) setBattlePower(kaiokenBasePower);
            var attack = getAttribute(Attributes.ATTACK_DAMAGE);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (attack != null && kaiokenBaseAttack > 0.0D) attack.setBaseValue(kaiokenBaseAttack);
            if (speed != null && kaiokenBaseSpeed > 0.0D) speed.setBaseValue(kaiokenBaseSpeed);
            if (kaiokenBaseKiDamage > 0.0F) setKiBlastDamage(kaiokenBaseKiDamage);
            setAuraType(kaiokenBaseAuraType == null ? "" : kaiokenBaseAuraType);
            setAuraColor(kaiokenBaseAuraColor);
        }
        // Active race forms are combat state, not a permanent stat multiplier. Restore
        // the exact pre-form profile on load while keeping the learned skill level.
        boolean restoreRacialAppearance = getActiveRacialFormLevel() > 0;
        if (restoreRacialAppearance) {
            entityData.set(ACTIVE_RACIAL_FORM_LEVEL, 0);
            setTransforming(false);
            if (racialBasePower > 0) setBattlePower(racialBasePower);
            var attack = getAttribute(Attributes.ATTACK_DAMAGE);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (attack != null && racialBaseAttack > 0.0D) attack.setBaseValue(racialBaseAttack);
            if (speed != null && racialBaseSpeed > 0.0D) speed.setBaseValue(racialBaseSpeed);
            if (racialBaseAttackSpeed > 0.0D) setDefaultAttackSpeed(racialBaseAttackSpeed);
            if (racialBaseKiDamage > 0.0F) setKiBlastDamage(racialBaseKiDamage);
            if (racialBaseScale > 0.0F) { entityData.set(DISPLAY_SCALE, racialBaseScale); setScaleVal(racialBaseScale); }
            if (!racialBaseHairColor.isBlank()) entityData.set(HAIR_COLOR, racialBaseHairColor);
            if (!racialBaseEye1Color.isBlank()) entityData.set(EYE1_COLOR, racialBaseEye1Color);
            if (!racialBaseEye2Color.isBlank()) entityData.set(EYE2_COLOR, racialBaseEye2Color);
            setAuraType(racialBaseAuraType == null ? "" : racialBaseAuraType);
            setAuraColor(racialBaseAuraColor);
            setLightning(racialBaseLightning);
        }
        partyId = tag.hasUUID("LWPartyId") ? tag.getUUID("LWPartyId") : null;
        partyCaptain = tag.contains("LWPartyCaptain") && tag.getBoolean("LWPartyCaptain");
        if (tag.contains("LWGender")) entityData.set(GENDER, tag.getInt("LWGender"));
        if (tag.contains("LWBodyType")) entityData.set(BODY_TYPE, tag.getInt("LWBodyType"));
        if (tag.contains("LWEyesType")) entityData.set(EYES_TYPE, tag.getInt("LWEyesType"));
        if (tag.contains("LWNoseType")) entityData.set(NOSE_TYPE, tag.getInt("LWNoseType"));
        if (tag.contains("LWMouthType")) entityData.set(MOUTH_TYPE, tag.getInt("LWMouthType"));
        if (tag.contains("LWHeadBone")) entityData.set(HEAD_BONE, tag.getInt("LWHeadBone"));
        else if (getRace() == FighterRace.NAMEKIAN)
            entityData.set(HEAD_BONE, Math.floorMod(getUUID().hashCode(), 2) == 0 ? 0 : 1 + Math.floorMod(getUUID().hashCode() / 2, 2));
        if (tag.contains("LWHairId")) entityData.set(HAIR_ID, tag.getInt("LWHairId"));
        if (tag.contains("LWOutfit")) entityData.set(OUTFIT, tag.getInt("LWOutfit"));
        if (tag.contains("LWBodyColor")) entityData.set(BODY_COLOR, tag.getString("LWBodyColor"));
        if (tag.contains("LWBodyColor2")) entityData.set(BODY_COLOR2, tag.getString("LWBodyColor2"));
        else entityData.set(BODY_COLOR2, getBodyColor());
        if (tag.contains("LWBodyColor3")) entityData.set(BODY_COLOR3, tag.getString("LWBodyColor3"));
        else entityData.set(BODY_COLOR3, getBodyColor());
        if (getRace() == FighterRace.NAMEKIAN) {
            int migratedBodyType = Math.floorMod(getBodyType(), 3);
            entityData.set(BODY_TYPE, migratedBodyType);
            // Older renewed builds incorrectly selected the primary colour from the body type.
            // Rebuild all three independent Namekian layer palettes deterministically so already
            // existing fighters are migrated as well as newly generated ones.
            int appearanceSeed = getUUID().hashCode();
            entityData.set(BODY_COLOR, NAMEK_GREEN[Math.floorMod(appearanceSeed, NAMEK_GREEN.length)]);
            entityData.set(BODY_COLOR2, NAMEK_ACCENT[Math.floorMod(appearanceSeed / 7, NAMEK_ACCENT.length)]);
            entityData.set(BODY_COLOR3, NAMEK_PINK[Math.floorMod(appearanceSeed / 17, NAMEK_PINK.length)]);
            entityData.set(HAIR_COLOR, NAMEK_LIGHT_GREEN[Math.floorMod(appearanceSeed / 29, NAMEK_LIGHT_GREEN.length)]);
        }
        if (tag.contains("LWHairColor")) entityData.set(HAIR_COLOR, tag.getString("LWHairColor"));
        // Migrate female Majins created by older builds, where the race-level hair gate
        // forced an otherwise valid generated HairId to remain invisible (or zero).
        if (getRace() == FighterRace.MAJIN && isFemale()) {
            int presets = Math.max(1, HairManager.getPresetCount());
            if (getHairId() <= 0) entityData.set(HAIR_ID, 1 + Math.floorMod(getUUID().hashCode(), presets));
            entityData.set(HAIR_COLOR, getBodyColor());
        }
        if (getRace() == FighterRace.MAJIN) {
            int appearanceSeed = getUUID().hashCode();
            entityData.set(BODY_TYPE, Math.floorMod(getBodyType(), 3));
            entityData.set(EYES_TYPE, Math.floorMod(getEyesType(), 3));
            entityData.set(NOSE_TYPE, Math.floorMod(getNoseType(), 2));
            entityData.set(MOUTH_TYPE, Math.floorMod(getMouthType(), 2));
            if (!isFemale()) entityData.set(HEAD_BONE, Math.floorMod(appearanceSeed, 3));
            if (isMajinPinkVariant(getBodyColor()))
                entityData.set(BODY_COLOR2, getBodyColor());
            else if (getBodyColor2().equalsIgnoreCase(getBodyColor()))
                entityData.set(BODY_COLOR2, shadeColor(getBodyColor(), (appearanceSeed & 1) == 0 ? 0.72D : 1.28D));
            String redEye = MAJIN_EYE_RED[Math.floorMod(appearanceSeed / 11, MAJIN_EYE_RED.length)];
            entityData.set(EYE1_COLOR, redEye);
            entityData.set(EYE2_COLOR, redEye);
            if (isFemale()) entityData.set(HAIR_COLOR, getBodyColor());
        }
        if (tag.contains("LWEye1Color")) entityData.set(EYE1_COLOR, tag.getString("LWEye1Color"));
        if (tag.contains("LWEye2Color")) entityData.set(EYE2_COLOR, tag.getString("LWEye2Color"));
        // The generic appearance NBT above contains the *currently rendered* form hair/eyes.
        // If a save happened mid-transformation, restore the pre-form appearance one final time
        // after reading those fields so temporary form cosmetics cannot leak across reloads.
        if (restoreRacialAppearance) {
            if (!racialBaseHairColor.isBlank()) entityData.set(HAIR_COLOR, racialBaseHairColor);
            if (!racialBaseEye1Color.isBlank()) entityData.set(EYE1_COLOR, racialBaseEye1Color);
            if (!racialBaseEye2Color.isBlank()) entityData.set(EYE2_COLOR, racialBaseEye2Color);
        }
        // Appearance NBT from older builds may contain the former body-coloured Majin eyes.
        // Normalize the base eye colour after every generic/form restoration has completed.
        if (getRace() == FighterRace.MAJIN) {
            String redEye = MAJIN_EYE_RED[Math.floorMod(getUUID().hashCode() / 11, MAJIN_EYE_RED.length)];
            entityData.set(EYE1_COLOR, redEye);
            entityData.set(EYE2_COLOR, redEye);
        }
        entityData.set(SPEECH, "");
        speechTicks = 0;
        combatConfigured = false;
        FighterSpecialItemManager.initialize(this);
        FighterScientistManager.initialize(this);
        // R6 persists a canonical BP separately from native DMZ's synced/current number. For
        // older saves, take the smallest valid pre-layer snapshot so an active form/fruit cannot
        // become permanent simply because the chunk was saved mid-effect.
        int canonicalPower = legacyData.getInt(PERMANENT_BATTLE_POWER);
        if (canonicalPower <= 0) {
            canonicalPower = serializedBattlePower;
            if (restoreKaioken && kaiokenBasePower > 0) canonicalPower = Math.min(canonicalPower, kaiokenBasePower);
            if (restoreRacialAppearance && racialBasePower > 0) canonicalPower = Math.min(canonicalPower, racialBasePower);
            int fruitBase = legacyData.getInt("LWMightFruitBaseBP");
            if (fruitBase > 0 && FighterSpecialItemManager.hasActiveMightFruit(this))
                canonicalPower = Math.min(canonicalPower, fruitBase);
            legacyData.putInt(PERMANENT_BATTLE_POWER, Math.max(1, canonicalPower));
        }
        // A comparison session cannot resume across chunk/server reload. The marker is written
        // before any visual flare, so it guarantees that temporary display BP never survives as
        // real stats or as a remembered character value.
        if (legacyData.contains(POWER_COMPARE_RESTORE_BP)) {
            legacyData.remove(POWER_COMPARE_RESTORE_BP);
            legacyData.remove(POWER_COMPARE_RESTORE_AT);
        }
        refreshTemporaryPowerProjection();
        syncLegacyTitle();
        // 2.2 save migration: existing high-BP fighters must receive the same BP-backed
        // physical profile as newly created/recalled fighters. Preserve their health
        // percentage so loading an older save does not make them appear nearly dead.
        if (entityData.get(READY)) refreshCombatStatsFromPower();
        // R35 save migration: vanilla entity equipment NBT is restored by super before this
        // method. Give every live NPC-owned GeoItem a fresh per-stack GeckoLib render id so
        // it cannot alias a matching weapon in the player's inventory or another fighter.
        FighterArsenalManager.refreshEquippedGeoItemIdentities(this, true);
    }

    private static CompoundTag sanitizeLegacyData(CompoundTag input) {
        CompoundTag out = input == null ? new CompoundTag() : input.copy();
        String[] nonNegative = {"Fights", "Wins", "Losses", "Kills", "Deaths", "PlayerWins", "PlayerLosses",
                "StrongestWinPower", "Fusions", "PlayerRivalBattles", "PlayerRivalWins", "GoalsCompleted",
                "InterventionsGiven", "InterventionsReceived",
                "ThreatPeakScore"};
        for (String key : nonNegative) {
            if (out.contains(key, Tag.TAG_ANY_NUMERIC)) out.putInt(key, Math.max(0, out.getInt(key)));
        }
        // Save migration: keep actual rivalry history, discard the retired stage ladder.
        if (!out.contains("PlayerRivalBattles") && out.contains("NemesisBattles")) out.putInt("PlayerRivalBattles", Math.max(0, out.getInt("NemesisBattles")));
        if (!out.contains("PlayerRivalWins") && out.contains("NemesisWins")) out.putInt("PlayerRivalWins", Math.max(0, out.getInt("NemesisWins")));
        if (!out.contains("PlayerRivalName") && out.contains("NemesisName")) out.putString("PlayerRivalName", out.getString("NemesisName"));
        if (!out.contains("PlayerRival") && out.hasUUID("NemesisPlayer")) out.putUUID("PlayerRival", out.getUUID("NemesisPlayer"));
        out.remove("NemesisStage");
        out.remove("NemesisBattles");
        out.remove("NemesisWins");
        out.remove("NemesisName");
        out.remove("NemesisPlayer");
        if (out.contains("Events", Tag.TAG_LIST)) {
            ListTag source = out.getList("Events", Tag.TAG_STRING);
            ListTag clean = new ListTag();
            int start = Math.max(0, source.size() - 40);
            for (int i = start; i < source.size(); i++) {
                String event = source.getString(i);
                if (event == null || event.isBlank()) continue;
                clean.add(StringTag.valueOf(event.length() > 160 ? event.substring(0, 160) : event));
            }
            out.put("Events", clean);
        } else {
            out.remove("Events");
        }
        if (out.contains("Timeline", Tag.TAG_LIST)) {
            ListTag source = out.getList("Timeline", Tag.TAG_COMPOUND);
            ListTag clean = new ListTag();
            int start = Math.max(0, source.size() - 48);
            for (int i = start; i < source.size(); i++) {
                CompoundTag row = source.getCompound(i);
                String text = row.getString("Text");
                if (text == null || text.isBlank()) continue;
                CompoundTag safe = new CompoundTag();
                safe.putLong("Tick", Math.max(0L, row.getLong("Tick")));
                safe.putString("Text", text.length() > 160 ? text.substring(0, 160) : text);
                clean.add(safe);
            }
            out.put("Timeline", clean);
        } else {
            out.remove("Timeline");
        }
        trimString(out, "LastOpponent", 64);
        trimString(out, "LastResult", 64);
        trimString(out, "StrongestWinName", 64);
        trimString(out, "LastFusionPartner", 64);
        trimString(out, "PlayerRivalName", 64);
        trimString(out, "GoalType", 32);
        trimString(out, "GoalTarget", 64);
        trimString(out, "LastGoalType", 32);
        trimString(out, "LastGoalResult", 120);
        return out;
    }

    private static void trimString(CompoundTag tag, String key, int max) {
        if (!tag.contains(key, Tag.TAG_STRING)) return;
        String value = tag.getString(key);
        if (value.length() > max) tag.putString(key, value.substring(0, max));
    }

    public CompoundTag getLegacyData() { return legacyData; }

    public boolean isArsenalInitialized() { return arsenalInitialized; }
    public void setArsenalInitialized(boolean value) { arsenalInitialized = value; }
    public int getArsenalWeaponCooldown() { return arsenalWeaponCooldown; }
    public void setArsenalWeaponCooldown(int ticks) { arsenalWeaponCooldown = Math.max(0, ticks); }

    public void recordLegacyBattle(String opponentName, int opponentPower, boolean won, boolean lethal, boolean playerOpponent) {
        if (level().isClientSide) return;
        String resolvedName = opponentName == null || opponentName.isBlank() ? "Unknown" : opponentName;
        int previousStrongest = Math.max(0, legacyData.getInt("StrongestWinPower"));
        incrementLegacy("Fights");
        incrementLegacy(won ? "Wins" : "Losses");
        if (lethal) incrementLegacy(won ? "Kills" : "Deaths");
        if (playerOpponent) incrementLegacy(won ? "PlayerWins" : "PlayerLosses");
        legacyData.putString("LastOpponent", resolvedName);
        legacyData.putString("LastResult", won ? (lethal ? "Victory (lethal)" : "Victory") : (lethal ? "Defeat (lethal)" : "Defeat"));
        long battleTime = level().getGameTime();
        legacyData.putLong("LastBattle", battleTime);
        // R14 merges post-battle recovery into the existing Rest/Nap activity system. This is a
        // one-shot intent marker, not a second recovery state; defeated/grace handling still owns
        // immediate combat recovery and the ordinary activity manager takes over only afterwards.
        if (!lethal || won) {
            legacyData.putBoolean(FighterAmbientActivityManager.POST_BATTLE_RECOVERY_PENDING, true);
            legacyData.putLong(FighterAmbientActivityManager.POST_BATTLE_RECOVERY_AT, battleTime + (won ? 20L : 100L));
        }
        boolean newStrongest = won && opponentPower > previousStrongest;
        if (newStrongest) {
            legacyData.putInt("StrongestWinPower", Math.max(0, opponentPower));
            legacyData.putString("StrongestWinName", resolvedName);
        }

        // Counters remember every fight; the visible timeline only remembers fights
        // that changed this person's story. This keeps the profile from becoming a
        // kill-feed disguised as character history.
        boolean relationshipFight = resolvedName.equals(getRivalName());
        boolean upset = won && opponentPower > Math.max(1L, Math.round(getBattlePower() * 1.25D));
        if (lethal || newStrongest || relationshipFight || upset || (playerOpponent && getPlayerRivalBattles() > 0)) {
            String reason = lethal ? " in a lethal fight" : upset ? " against a stronger opponent" : "";
            recordLegacyEvent((won ? "Defeated " : "Lost to ") + resolvedName + reason);
        }
        syncLegacyTitle();
    }

    public void recordFusion(String partnerName) {
        incrementLegacy("Fusions");
        legacyData.putString("LastFusionPartner", partnerName == null ? "Unknown" : partnerName);
        recordLegacyEvent("Fused with " + (partnerName == null ? "Unknown" : partnerName));
        FighterGoalManager.onFusion(this);
        syncLegacyTitle();
    }

    private void incrementLegacy(String key) {
        int value = Math.max(0, legacyData.getInt(key));
        if (value < Integer.MAX_VALUE) legacyData.putInt(key, value + 1);
    }

    public void recordLegacyEvent(String event) {
        if (event == null || event.isBlank()) return;
        String safeText = event.length() > 160 ? event.substring(0, 160) : event;
        ListTag events = legacyData.getList("Events", Tag.TAG_STRING);
        if (!events.isEmpty() && events.getString(events.size() - 1).equals(safeText)) return;
        events.add(StringTag.valueOf(safeText));
        while (events.size() > 40) events.remove(0);
        legacyData.put("Events", events);

        // 1.4 keeps a timestamped biography without breaking the older string-event list.
        ListTag timeline = legacyData.getList("Timeline", Tag.TAG_COMPOUND);
        CompoundTag row = new CompoundTag();
        long tick = level() instanceof ServerLevel level ? level.getServer().overworld().getGameTime() : 0L;
        row.putLong("Tick", Math.max(0L, tick));
        row.putString("Text", safeText);
        timeline.add(row);
        while (timeline.size() > 48) timeline.remove(0);
        legacyData.put("Timeline", timeline);
        syncLegacyTitle();
    }

    public void setPlayerRivalState(UUID playerId, String playerName, int battles, int winsVsPlayer) {
        if (playerId != null) legacyData.putUUID("PlayerRival", playerId);
        legacyData.putString("PlayerRivalName", playerName == null ? "" : playerName);
        legacyData.putInt("PlayerRivalBattles", Math.max(0, battles));
        legacyData.putInt("PlayerRivalWins", Math.max(0, winsVsPlayer));
        // 1.8.5 retires the artificial five-stage rivalry ladder. Old fields are removed
        // when a fighter is loaded/sanitized; the real battle history remains.
        legacyData.remove("NemesisStage");
        legacyData.remove("NemesisBattles");
        legacyData.remove("NemesisWins");
        legacyData.remove("NemesisPlayer");
        legacyData.remove("NemesisName");
        syncLegacyTitle();
    }

    public int getPlayerRivalBattles() { return Math.max(0, legacyData.getInt("PlayerRivalBattles")); }
    public int getPlayerRivalWins() { return Math.max(0, legacyData.getInt("PlayerRivalWins")); }

    public String getLegacyTitle() {
        if (level().isClientSide) return entityData.get(LEGACY_TITLE);
        String title = computeLegacyTitle();
        if (!title.equals(entityData.get(LEGACY_TITLE))) entityData.set(LEGACY_TITLE, title);
        return title;
    }

    private String computeLegacyTitle() {
        if (WorldMenaceManager.isWorldMenace(this)) return "";
        if (legacyData.getBoolean("AntagonistRecognized") && !legacyData.getString("AntagonistEpithet").isBlank())
            return legacyData.getString("AntagonistEpithet");
        if (getMemoryRelationship() <= -70 && getPlayerRivalBattles() >= 3) return "Archrival";
        if (legacyData.getBoolean("ThreatRecognized")) return "World Threat";
        if (legacyData.getInt("Fusions") >= 3) return "Fusion Veteran";
        if (legacyData.getInt("Wins") >= 12) return "Battle-Hardened";
        int strongest = legacyData.getInt("StrongestWinPower");
        if (strongest > Math.max(1, getBattlePower()) * 2L) return "Giant Killer";
        if (legacyData.getInt("Wins") >= 6) return "Proven Fighter";
        return "";
    }

    private void syncLegacyTitle() {
        if (level().isClientSide) return;
        String next = computeLegacyTitle();
        if (!next.equals(entityData.get(LEGACY_TITLE))) {
            entityData.set(LEGACY_TITLE, next);
            // Titles carry a small earned combat multiplier. A title transition therefore has to
            // invalidate the real profile even though the displayed BP itself did not change.
            combatStatsPower = -1;
        }
    }

    public String getLegacySummary() {
        int fights = legacyData.getInt("Fights");
        if (fights <= 0 && legacyData.getInt("Fusions") <= 0 && getPlayerRivalBattles() <= 0) return "No major history yet";
        StringBuilder out = new StringBuilder();
        if (fights > 0) out.append(legacyData.getInt("Wins")).append(" wins / ").append(legacyData.getInt("Losses")).append(" losses");
        if (legacyData.getInt("StrongestWinPower") > 0) out.append(" • best: ").append(legacyData.getString("StrongestWinName"))
                .append(" (PL ").append(legacyData.getInt("StrongestWinPower")).append(')');
        if (legacyData.getInt("Fusions") > 0) out.append(" • fusions ").append(legacyData.getInt("Fusions"));
        if (getPlayerRivalBattles() > 0) out.append(" • rivalry ").append(getPlayerRivalBattles()).append(" fights");
        if (legacyData.getBoolean("AntagonistRecognized")) out.append(" • antagonist ").append(legacyData.getString("AntagonistRole").toLowerCase(java.util.Locale.ROOT));
        return out.toString();
    }

    /**
     * Remove DBSagasEntity's default NearestAttackableTargetGoal wrappers while
     * retaining HurtByTargetGoal and the rest of DMZ's native goal set.
     * This is the proven 0.6.2 technique, narrowly scoped to target acquisition.
     */
    private void removeAutomaticHostilityGoals() {
        try {
            Class<?> type = getClass();
            while (type != null) {
                for (Field field : type.getDeclaredFields()) {
                    if (!"net.minecraft.world.entity.ai.goal.GoalSelector".equals(field.getType().getName())) continue;
                    field.setAccessible(true);
                    Object selector = field.get(this);
                    if (selector != null) removeNearestTargetWrappers(selector);
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
            // If Forge/DMZ internals change, failing closed here is preferable to a crash.
            // The debug status command makes unexpected hostility visible during testing.
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeNearestTargetWrappers(Object selector) throws IllegalAccessException {
        Class<?> type = selector.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(selector);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(value instanceof Collection collection)) continue;
                try {
                    collection.removeIf(AmbientFighterEntity::wrapperContainsNearestTargetGoal);
                } catch (UnsupportedOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static boolean wrapperContainsNearestTargetGoal(Object wrapper) {
        if (wrapper == null) return false;
        if (wrapper.getClass().getName().contains("NearestAttackableTargetGoal")) return true;

        Class<?> type = wrapper.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object nested = field.get(wrapper);
                    if (nested != null && nested.getClass().getName().contains("NearestAttackableTargetGoal")) return true;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
