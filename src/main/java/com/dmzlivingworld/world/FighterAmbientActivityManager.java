package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.client.particle.LWKiTrainingParticles;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Small, visible pieces of ordinary life for idle fighters. Most activities are observational;
 * ordinary-life interactions reuse real world entities/items where appropriate. Food gathering hunts
 * a real animal and consumes one real dropped edible item; temporary presentation props are never
 * persisted as fighter equipment.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterAmbientActivityManager {
    private static final String TEMP_ITEM = "LWTemporaryActivityItem";
    private static final String TEMP_ITEM_VERSION = "LWTemporaryActivityItemVersion";
    private static final String STOWED_MAIN_HAND = "LWActivityStowedMainHand";
    private static final String NEXT_ACTIVITY = "LWNextAmbientActivity";
    private static final String LAST_ACTIVITY = "LWLastAmbientActivity";
    private static final String LAST_ACTIVITY_AT = "LWLastAmbientActivityAt";
    private static final String LAST_MEAL_AT = "LWLastMealAt";
    private static final String FORCED_DANCE_VARIANT = "LWForcedDanceVariant";
    private static final String FORCED_SIT_VARIANT = "LWForcedSitVariant";
    private static final String LAST_DANCE_VARIANT = "LWLastDanceVariant";
    private static final String DEBUG_SUBJECTS = "LWDebugActivitySubjects";
    public static final String POST_BATTLE_RECOVERY_PENDING = "LWPostBattleRecoveryPending";
    public static final String POST_BATTLE_RECOVERY_AT = "LWPostBattleRecoveryAt";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public enum Type {
        FISHING("Fishing"), REST("Resting"), SITTING("Sitting"), JOGGING("Jogging"), WALKING("Taking a walk"),
        TRAINING("Training"), STRENGTH_TRAINING("Training"), KI_TRAINING("Ki training"), NAP("Napping"),
        STUDYING("Studying notes"), SCIENTIST_RESEARCH("Improving Saibaman formula"), FLOWER("Inspecting a flower"), TREE("Taking an apple break"),
        FOOD_GATHERING("Gathering food"), STARGAZING("Stargazing"), EATING("Eating"), SCOUTING("Looking around"), RELAXED_FLIGHT("Flying"), DANCING("Dancing");
        private final String label;
        Type(String label) { this.label = label; }
        public String label() { return label; }
        public static Type from(String value) {
            if (value == null) return null;
            String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("FLY".equals(key) || "FLIGHT".equals(key)) key = "RELAXED_FLIGHT";
            if ("STARGAZE".equals(key)) key = "STARGAZING";
            if ("EAT".equals(key)) key = "EATING";
            if ("SIT".equals(key)) key = "SITTING";
            if ("JOG".equals(key) || "RUN".equals(key)) key = "JOGGING";
            if ("WALK".equals(key) || "STROLL".equals(key)) key = "WALKING";
            if ("STUDY".equals(key) || "NOTES".equals(key) || "READ".equals(key)) key = "STUDYING";
            if ("RESEARCH".equals(key) || "SAIBAMAN_RESEARCH".equals(key) || "FORMULA".equals(key)) key = "SCIENTIST_RESEARCH";
            if ("TRAIN".equals(key)) key = "TRAINING";
            if ("STRENGTH".equals(key) || "STRENGTH_TRAIN".equals(key) || "STRENGTHTRAINING".equals(key) || "STRENGTH_TRAINING".equals(key)) key = "TRAINING";
            if ("KI".equals(key) || "KI_TRAIN".equals(key) || "KITRAINING".equals(key)) key = "KI_TRAINING";
            if ("SLEEP".equals(key) || "NAPPING".equals(key)) key = "NAP";
            if ("APPLE".equals(key) || "TREE_APPLE".equals(key)) key = "TREE";
            if ("FOOD".equals(key) || "HUNT".equals(key) || "HUNTING".equals(key) || "FORAGING".equals(key)) key = "FOOD_GATHERING";
            if ("SCOUT".equals(key)) key = "SCOUTING";
            try { return Type.valueOf(key); } catch (IllegalArgumentException ignored) { return null; }
        }
    }

    private static final class Session {
        final UUID fighterId;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final Type type;
        final BlockPos stand;
        final BlockPos focus;
        final long started;
        long expires;
        long minimumUntil;
        boolean settled;
        long settledAt;
        boolean actualStarted;
        long actualStartedAt;
        boolean lying;
        long nextBeat;
        // Empty-hand training deliberately crosses a real STOP -> START boundary between
        // native DMZ combo animations. Restarting BASIC in the same tick as interruptCombo()
        // was the root cause of the intermittent torso-only "training" state.
        boolean unarmedStrikePending;
        long unarmedStrikeAt;
        Vec3 flightTarget;
        long nextFlightWaypoint;
        boolean landing;
        Vec3 landingTarget;
        long castStart;
        int danceVariant;
        long nextDanceSwitch;
        Vec3 mobileTarget;
        Vec3 routeDirection;
        int routeLegs;
        int activityPhase;
        long phaseUntil;
        boolean flowerTaken;
        boolean flowerActionDone;
        boolean flowerRecovered;
        long flowerActionAt;
        long flowerRecoverAt;
        final String flowerName;
        int sitVariant;
        long stretchUntil;
        int stretchVariant;
        boolean restSitting;
        int strengthVariant;
        int kiVariant;
        int scientistResearchVariant;
        long nextProximityWarning;
        long nextTrainingAccident;
        long nextGrowthPulse;
        int treeStrikes;
        boolean treeAppleReady;
        UUID treeAppleEntityId;
        long treeAppleDropAt;
        long flightTakeoffStarted;
        UUID foodTargetId;
        UUID foodKilledTargetId;
        UUID foodDropId;
        BlockPos foodDeathPos;
        long foodDeathAt;
        int foodSearchStage;
        long foodNextSearch;
        long foodEatAt;
        ItemStack foodCarried = ItemStack.EMPTY;
        boolean foodConsumed;
        boolean foodPrefersFlight;
        boolean foodPrefersRun;
        double settledAnchorX;
        double settledAnchorZ;
        Vec3 kiCore;
        Session(AmbientFighterEntity fighter, Type type, BlockPos stand, BlockPos focus, long now) {
            this.fighterId = fighter.getUUID();
            this.dimension = fighter.level().dimension();
            this.type = type;
            this.stand = stand == null ? fighter.blockPosition() : stand.immutable();
            this.focus = focus == null ? this.stand : focus.immutable();
            this.started = now;
            // Ordinary-life activities should read as actual activities rather than brief emotes.
            // Keep short observational interactions short, while training/rest/scouting can occupy
            // a believable stretch of an NPC's day. Mood still multiplies these established ranges.
            // R10+: a Minecraft day is divided into sixteen intent slots. Activities therefore
            // need human-scale durations: hobbies/social beats can be brief, while training/rest
            // still last long enough to read as meaningful without consuming half the day.
            long baseDuration = switch (type) {
                case FISHING -> 700L + fighter.getRandom().nextInt(751);        // ~35-72 sec
                case STARGAZING -> 750L + fighter.getRandom().nextInt(751);    // ~37-75 sec
                case RELAXED_FLIGHT -> 550L + fighter.getRandom().nextInt(651);
                case DANCING -> 120L + fighter.getRandom().nextInt(181);       // ~6-15 sec interlude/event
                case JOGGING -> 650L + fighter.getRandom().nextInt(701);
                case WALKING -> 450L + fighter.getRandom().nextInt(601);       // ~22-52 sec
                case STUDYING -> 160L + fighter.getRandom().nextInt(241);
                case SCIENTIST_RESEARCH -> 360L + fighter.getRandom().nextInt(401);      // ~8-20 sec notes check
                case TRAINING -> 850L + fighter.getRandom().nextInt(701);      // ~42-77 sec
                case STRENGTH_TRAINING -> 720L + fighter.getRandom().nextInt(581); // ~36-65 sec
                case KI_TRAINING -> 720L + fighter.getRandom().nextInt(581);       // ~36-65 sec
                case NAP -> 360L + fighter.getRandom().nextInt(361);               // ~18-36 sec
                case FLOWER -> 100L + fighter.getRandom().nextInt(101);        // ~5-10 sec stop
                case TREE -> 160L + fighter.getRandom().nextInt(161);          // ~8-16 sec snack stop
                case FOOD_GATHERING -> 900L + fighter.getRandom().nextInt(601); // ~45-75 sec real hunt/meal
                case SITTING, REST -> 650L + fighter.getRandom().nextInt(751);
                case EATING -> 120L + fighter.getRandom().nextInt(121);        // ~6-12 sec meal beat
                case SCOUTING -> 180L + fighter.getRandom().nextInt(241);      // ~9-21 sec situational check
            };
            this.expires = now + Math.max(120L, Math.round(baseDuration * ReactiveWorldManager.activityDurationMultiplier(fighter, type)));
            this.minimumUntil = now + switch (type) {
                case FLOWER -> 70L;
                case DANCING -> 90L;
                case EATING -> 80L;
                case TREE -> 100L;
                case FOOD_GATHERING -> 240L;
                case JOGGING, TRAINING, STRENGTH_TRAINING, KI_TRAINING, SITTING -> 220L;
                case NAP -> 180L;
                case WALKING -> 180L;
                case STUDYING, SCIENTIST_RESEARCH, SCOUTING -> 100L;
                default -> 160L;
            };
            this.nextBeat = now + 60L;
            this.flightTarget = null;
            this.nextFlightWaypoint = now;
            this.mobileTarget = Vec3.atBottomCenterOf(this.stand);
            int requestedDance = fighter.getPersistentData().contains(FORCED_DANCE_VARIANT)
                    ? fighter.getPersistentData().getInt(FORCED_DANCE_VARIANT) : -1;
            int chosenDance = 0;
            if (type == Type.DANCING) {
                if (requestedDance >= 0) {
                    chosenDance = Math.max(0, Math.min(1, requestedDance));
                } else {
                    chosenDance = fighter.getRandom().nextInt(2);
                    if (fighter.getPersistentData().contains(LAST_DANCE_VARIANT)
                            && chosenDance == fighter.getPersistentData().getInt(LAST_DANCE_VARIANT)) {
                        chosenDance = 1 - chosenDance;
                    }
                }
                fighter.getPersistentData().putInt(LAST_DANCE_VARIANT, chosenDance);
            }
            this.danceVariant = chosenDance;
            this.nextDanceSwitch = now + 180L + fighter.getRandom().nextInt(161);
            int requestedSit = fighter.getPersistentData().contains(FORCED_SIT_VARIANT)
                    ? fighter.getPersistentData().getInt(FORCED_SIT_VARIANT) : -1;
            this.sitVariant = requestedSit >= 0 ? Math.max(0, Math.min(1, requestedSit)) : fighter.getRandom().nextInt(2);
            this.stretchUntil = 0L;
            this.stretchVariant = fighter.getRandom().nextInt(2);
            this.restSitting = type == Type.SITTING || (type == Type.REST && fighter.getRandom().nextFloat() < 0.08F);
            this.strengthVariant = 2; // push-ups only
            this.kiVariant = type == Type.KI_TRAINING ? fighter.getRandom().nextInt(2) : 0;
            this.scientistResearchVariant = type == Type.SCIENTIST_RESEARCH ? fighter.getRandom().nextInt(2) : 0;
            this.flowerName = type == Type.FLOWER && fighter.level() instanceof ServerLevel level
                    ? friendlyBlockName(level.getBlockState(this.focus)) : "flower";
            this.nextProximityWarning = now;
            this.nextTrainingAccident = now + 45L;
            this.nextGrowthPulse = now + 200L;
            this.flightTakeoffStarted = now;
            this.foodNextSearch = now;
            this.foodPrefersFlight = type == Type.FOOD_GATHERING && fighter.hasFlightUnlocked() && fighter.getRandom().nextFloat() < 0.42F;
            this.foodPrefersRun = type == Type.FOOD_GATHERING && fighter.getRandom().nextFloat() < 0.78F;
            fighter.getPersistentData().remove(FORCED_DANCE_VARIANT);
            fighter.getPersistentData().remove(FORCED_SIT_VARIANT);
        }
    }

    private FighterAmbientActivityManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (!SESSIONS.isEmpty()) tickSessions(server, now);
        if (now % 40L == 0L) tryStartNearby(server, now);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AmbientFighterEntity fighter && !fighter.level().isClientSide
                && SESSIONS.containsKey(fighter.getUUID())) {
            // Remove visual-only props before vanilla/Forge death-drop handling can see equipment.
            finish(fighter);
            return;
        }
        if (event.getEntity().level().isClientSide) return;
        UUID deadId = event.getEntity().getUUID();
        for (Session session : SESSIONS.values()) {
            if (session.type == Type.FOOD_GATHERING && deadId.equals(session.foodTargetId)) {
                session.foodKilledTargetId = deadId;
                session.foodDropId = null;
                session.foodDeathPos = event.getEntity().blockPosition().immutable();
                session.foodDeathAt = event.getEntity().level().getGameTime();
                session.foodTargetId = null;
                session.foodNextSearch = session.foodDeathAt + 2L;
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        UUID deadId = event.getEntity().getUUID();
        for (Session session : SESSIONS.values()) {
            if (session.type != Type.FOOD_GATHERING) continue;
            if (!deadId.equals(session.foodTargetId) && !deadId.equals(session.foodKilledTargetId)) continue;
            ItemEntity exact = event.getDrops().stream()
                    .filter(item -> item != null && !item.getItem().isEmpty() && isMeatLike(item.getItem()))
                    .sorted(java.util.Comparator.comparingDouble(item -> item.distanceToSqr(event.getEntity())))
                    .findFirst().orElse(null);
            if (exact != null) session.foodDropId = exact.getUUID();
        }
    }

    /**
     * Commits an activity only when it is physically underway. Approach/navigation is preparation,
     * not an activity start. The planned duration is shifted forward so a long walk to a fishing
     * spot or training place cannot consume the activity before it actually begins.
     */
    private static void markActualStart(AmbientFighterEntity fighter, Session session, long now) {
        if (fighter == null || session == null || session.actualStarted) return;
        long plannedDuration = Math.max(120L, session.expires - session.started);
        long plannedMinimum = Math.max(1L, session.minimumUntil - session.started);
        session.actualStarted = true;
        session.actualStartedAt = now;
        session.expires = now + plannedDuration;
        session.minimumUntil = now + plannedMinimum;
        FighterDailyRoutineManager.recordActivityStart(fighter, session.type.label());
    }

    private static void tickSessions(MinecraftServer server, long now) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerLevel level = server.getLevel(session.dimension);
            AmbientFighterEntity fighter = level != null && level.getEntity(session.fighterId) instanceof AmbientFighterEntity f ? f : null;
            if (fighter == null || !fighter.isAlive()) { SESSIONS.remove(session.fighterId); continue; }
            if (!validDuringActivity(fighter)) { finish(fighter); continue; }

            // R9: visible progression is earned during the activity too, not only as a single
            // end-of-session jump. The completion methods consume these advances so total growth
            // stays on the established R8.1 budget rather than becoming a hidden buff.
            if (now >= session.nextGrowthPulse) {
                if (session.type == Type.JOGGING && now - session.started >= 200L) {
                    FighterBattleGrowthManager.onJoggingPulse(fighter, 200);
                    session.nextGrowthPulse = now + 200L;
                } else if ((session.type == Type.STRENGTH_TRAINING || session.type == Type.KI_TRAINING)
                        && session.settled && now - session.settledAt >= 200L) {
                    FighterBattleGrowthManager.onTrainingPulse(fighter, 200, false);
                    session.nextGrowthPulse = now + 200L;
                }
                // TRAINING deliberately leaves the pulse due until the next real strike beat.
            }

            if (session.type == Type.FOOD_GATHERING) {
                if (now >= session.expires) { finish(fighter); continue; }
                tickFoodGathering(fighter, session, now);
                continue;
            }
            if (session.type == Type.RELAXED_FLIGHT) {
                if (now >= session.expires) session.landing = true;
                if (now >= session.expires + 160L) { finish(fighter); continue; }
                tickRelaxedFlight(fighter, session, now);
                continue;
            }
            if (session.type == Type.JOGGING) {
                if (now >= session.expires) { finish(fighter); continue; }
                tickJogging(fighter, session, now);
                continue;
            }
            if (session.type == Type.WALKING) {
                if (now >= session.expires) { finish(fighter); continue; }
                tickWalking(fighter, session, now);
                continue;
            }
            // Stationary sessions do not spend their activity duration while merely walking to
            // the selected spot. Their expiry is re-anchored by markActualStart() after settle().
            if (session.actualStarted && now >= session.expires) { finish(fighter); continue; }

            Vec3 standCenter = Vec3.atBottomCenterOf(session.stand);
            if (!session.settled) {
                if (fighter.position().distanceToSqr(standCenter) > 1.55D * 1.55D) {
                    if (now - session.started > 260L && now >= session.minimumUntil) { finish(fighter); continue; }
                    if (fighter.getNavigation().isDone() || now % 20L == 0L)
                        fighter.getNavigation().moveTo(standCenter.x, standCenter.y, standCenter.z, 0.90D);
                    look(fighter, session.focus, session.type == Type.STARGAZING ? 5.0D : 0.4D);
                    continue;
                }
                settle(fighter, session, now);
                // settle() may abort the session when a required activity prop cannot be equipped.
                // Never keep ticking a stale Session object after finish() removed it.
                if (!SESSIONS.containsKey(fighter.getUUID())) continue;
            }

            // Training is a grounded activity. Never let a knock, stale flight flag or terrain
            // transition leave a fighter performing the practice controller in mid-air.
            if (session.type == Type.TRAINING
                    && (!fighter.onGround() || fighter.isFlying() || fighter.isInWaterOrBubble())) {
                finish(fighter);
                continue;
            }
            fighter.getNavigation().stop();
            fighter.setDeltaMovement(0.0D, fighter.getDeltaMovement().y, 0.0D);
            tickSettled(level, fighter, session, now);
        }
    }

    private static void settle(AmbientFighterEntity fighter, Session session, long now) {
        // Do not mark the session settled/start its journal entry until every activity-specific
        // prerequisite succeeds. Failed prop acquisition or unsafe terrain is merely an aborted
        // attempt, never a zero-tick completed activity.
        fighter.getNavigation().stop();
        fighter.setDeltaMovement(0.0D, fighter.getDeltaMovement().y, 0.0D);
        switch (session.type) {
            case FISHING -> {
                if (!ensureTemporaryItem(fighter, Items.FISHING_ROD)) { finish(fighter); return; }
                fighter.setFishingActivity(true);
                fighter.swing(InteractionHand.MAIN_HAND, true);
                faceStable(fighter, session.focus);
                castFishingHook(fighter, session, now);
            }
            case EATING -> {
                if (!ensureTemporaryItem(fighter, Items.BREAD)) { finish(fighter); return; }
                fighter.setAmbientPose(13);
            }
            case REST, SITTING -> {
                // Sitting remains available, but natural recovery is mostly standing/breathing
                // now. The explicit SITTING compatibility/debug path still guarantees the pose.
                fighter.setAmbientPose(session.restSitting ? 7 + session.sitVariant : 0);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case NAP -> {
                if (!(fighter.level() instanceof ServerLevel napLevel) || !validNapFeet(napLevel, fighter.blockPosition())) {
                    // The world may change after the destination is selected. If the support is
                    // broken or water reaches the resting footprint, end the nap immediately
                    // instead of anchoring a lying fighter over the hazard.
                    finish(fighter);
                    return;
                }
                fighter.setAmbientPose(22);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case TREE -> {
                fighter.setPose(Pose.STANDING);
                // DEV3: earn the apple visually. The fighter first strikes the real tree a few
                // times with block-hit feedback, then receives the temporary snack prop.
                session.treeStrikes = 0;
                session.treeAppleReady = false;
            }
            case STUDYING -> {
                if (!ensureTemporaryItem(fighter, Items.BOOK)) { finish(fighter); return; }
                // R11: studying is now a real synced reading activity rather than a generic sit
                // with a book merely occupying the hand slot.
                fighter.setAmbientPose(15);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                session.activityPhase = 0;
                session.phaseUntil = now + 90L + fighter.getRandom().nextInt(91);
            }
            case SCIENTIST_RESEARCH -> {
                if (!FighterScientistManager.isScientist(fighter)) { finish(fighter); return; }
                // Scientist life has two distinct work routines: written formula refinement and
                // scouter-based combat-data review. Both advance the same bounded research record.
                if (session.scientistResearchVariant == 0 && !ensureTemporaryItem(fighter, Items.BOOK)) { finish(fighter); return; }
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(session.scientistResearchVariant == 0 ? 15 : 16);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case TRAINING -> {
                if (!fighter.onGround() || fighter.isFlying() || fighter.isInWaterOrBubble()) {
                    finish(fighter);
                    return;
                }
                fighter.suppressActivityAura();
                // Empty-handed practice is targetless by design. DMZ startCombo() rejects a null
                // combat target, so R41 gives practice its own controller while reusing DMZ's native
                // attack clips. This removes the torso-only failure without inventing a fake target.
                boolean armed = !fighter.getMainHandItem().isEmpty();
                fighter.setAmbientPose(armed ? 14 : 11);
                if (armed) fighter.performWeaponTrainingStrike();
                else fighter.performUnarmedTrainingStrike();
            }
            case STRENGTH_TRAINING -> {
                fighter.suppressActivityAura();
                session.strengthVariant = 2;
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(25);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case KI_TRAINING -> {
                fighter.suppressActivityAura();
                Vec3 look = fighter.getLookAngle();
                Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
                if (horizontal.lengthSqr() < 1.0E-4D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
                horizontal = horizontal.normalize();
                session.kiCore = fighter.position()
                        .add(0.0D, Math.max(0.95D, fighter.getBbHeight() * 0.59D), 0.0D)
                        .add(horizontal.scale(session.kiVariant == 0 ? 0.96D : 0.90D));
                lockKiFocus(fighter, session.kiCore);
                fighter.setAmbientPose(session.kiVariant == 0 ? 23 : 24);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                fighter.setKiCharge(false);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case FLOWER -> {
                // R14: absolutely no flower bend/crouch animation. The fighter stays upright,
                // looks at the real flower, then makes only a small native hand swing/reach.
                fighter.setAmbientPose(0);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                session.flowerActionAt = now + 28L + fighter.getRandom().nextInt(19);
                session.flowerRecoverAt = Long.MAX_VALUE;
                session.flowerActionDone = false;
                session.flowerRecovered = false;
            }
            case SCOUTING -> {
                if (!ensureTemporaryItem(fighter, Items.SPYGLASS)) { finish(fighter); return; }
                fighter.startUsingItem(InteractionHand.MAIN_HAND);
                fighter.setAmbientPose(16);
                session.activityPhase = 0;
                session.phaseUntil = now + 100L + fighter.getRandom().nextInt(81);
            }
            case DANCING -> {
                fighter.setAmbientPose(3 + session.danceVariant);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            case STARGAZING -> {
                session.lying = ReactiveWorldManager.mood(fighter) != ReactiveWorldManager.Mood.SOMBER
                        && ReactiveWorldManager.mood(fighter) != ReactiveWorldManager.Mood.WEARY
                        && fighter.getRandom().nextFloat() < 0.62F && fighter.onGround();
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(session.lying ? 2 : 1);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                fighter.setXRot(session.lying ? 0.0F : 85.0F);
                fighter.xRotO = fighter.getXRot();
            }
            default -> { }
        }
        if (!SESSIONS.containsKey(fighter.getUUID())) return;
        session.settled = true;
        session.settledAt = now;
        session.nextGrowthPulse = now + 200L;
        session.settledAnchorX = fighter.getX();
        session.settledAnchorZ = fighter.getZ();
        markActualStart(fighter, session, now);
        if (session.type == Type.FLOWER) maybeSpeakFlower(fighter, session, true);
        else maybeSpeak(fighter, session.type, true);
        session.nextBeat = now + 55L + fighter.getRandom().nextInt(70);
    }

    private static void tickSettled(ServerLevel level, AmbientFighterEntity fighter, Session session, long now) {
        switch (session.type) {
            case FISHING -> {
                if (!ensureTemporaryItem(fighter, Items.FISHING_ROD)) { finish(fighter); return; }
                // Stable gaze: do not make LookControl and native head interpolation fight every tick.
                if ((now - session.started) % 20L == 0L) faceStable(fighter, session.focus);
                tickFishingHook(level, fighter, session, now);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 55L + fighter.getRandom().nextInt(75);
                    double x = session.focus.getX() + 0.5D, y = session.focus.getY() + 0.92D, z = session.focus.getZ() + 0.5D;
                    level.sendParticles(ParticleTypes.SPLASH, x, y, z, 5, 0.22D, 0.04D, 0.22D, 0.05D);
                    level.playSound(null, x, y, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.42F,
                            0.90F + fighter.getRandom().nextFloat() * 0.18F);
                    if (fighter.getRandom().nextFloat() < 0.20F) fighter.swing(InteractionHand.MAIN_HAND);
                    if (fighter.getRandom().nextFloat() < 0.08F) maybeSpeak(fighter, session.type, false);
                }
            }
            case REST, SITTING -> {
                // R14 sharply reduces both sitting and stretches. Natural Rest usually stays
                // standing; explicit SITTING/debug remains intact, and either presentation may
                // only very rarely transition through one of the existing stretch animations.
                boolean stretching = now < session.stretchUntil;
                fighter.setAmbientPose(stretching ? 9 + session.stretchVariant
                        : session.restSitting ? 7 + session.sitVariant : 0);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 220L + fighter.getRandom().nextInt(320);
                    float stretchChance = session.restSitting ? 0.06F : 0.035F;
                    if (!stretching && now + 100L < session.expires && fighter.getRandom().nextFloat() < stretchChance) {
                        session.stretchVariant = fighter.getRandom().nextInt(2);
                        session.stretchUntil = now + 52L + fighter.getRandom().nextInt(42);
                        fighter.setAmbientPose(9 + session.stretchVariant);
                    } else if (fighter.getRandom().nextFloat() < 0.40F) {
                        maybeSpeak(fighter, Type.REST, false);
                    }
                }
            }
            case NAP -> {
                fighter.setAmbientPose(22);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                // Anchor the settled nap exactly where it began. Native mob movement/collision
                // can otherwise advance a lying fighter a few pixels per tick even with navigation stopped.
                if (Math.abs(fighter.getX() - session.settledAnchorX) > 0.003D
                        || Math.abs(fighter.getZ() - session.settledAnchorZ) > 0.003D)
                    fighter.setPos(session.settledAnchorX, fighter.getY(), session.settledAnchorZ);
                fighter.setDeltaMovement(0.0D, fighter.getDeltaMovement().y, 0.0D);
            }
            case TRAINING -> {
                fighter.suppressActivityAura();
                boolean armed = !fighter.getMainHandItem().isEmpty();
                fighter.setAmbientPose(armed ? 14 : 11);

                if (armed && session.unarmedStrikePending) {
                    // Equipment changed during the activity; do not let an old queued fist strike
                    // fire through the newly equipped weapon animation.
                    session.unarmedStrikePending = false;
                }

                boolean realStrike = false;
                if (!armed && session.unarmedStrikePending && now >= session.unarmedStrikeAt) {
                    realStrike = emitQueuedUnarmedTrainingStrike(fighter, session, now);
                    if (realStrike) {
                        int burst = fighter.getPersistentData().getInt("LWTrainingBurst");
                        if (burst <= 0) burst = 2 + fighter.getRandom().nextInt(3);
                        burst--;
                        fighter.getPersistentData().putInt("LWTrainingBurst", burst);
                        session.nextBeat = now + (burst > 0 ? 18L + fighter.getRandom().nextInt(13)
                                : 58L + fighter.getRandom().nextInt(68));
                    }
                } else if (now >= session.nextBeat && !session.unarmedStrikePending) {
                    if (armed) {
                        // This advances the exact attack animation/sound declared by the equipped
                        // weapon, while remaining a harmless practice strike with no target.
                        fighter.performWeaponTrainingStrike();
                        session.nextBeat = now + fighter.getWeaponTrainingCadenceTicks() + fighter.getRandom().nextInt(7);
                        realStrike = true;
                    } else {
                        // Queue the next harmless controller beat. No fake combat target and no
                        // DMZ combo state are created for practice.
                        queueUnarmedTrainingStrike(fighter, session, now);
                    }
                }

                if (realStrike) {
                    // Training progression is paid on a real practice strike, never on a body-only
                    // animation tick. This keeps the visible BP trickle synchronized to punches/swings.
                    if (now >= session.nextGrowthPulse) {
                        FighterBattleGrowthManager.onTrainingPulse(fighter, 200, false);
                        session.nextGrowthPulse = now + 200L;
                    }
                    if (fighter.getRandom().nextFloat() < 0.05F) maybeSpeak(fighter, session.type, false);
                    warnAndBumpNearbyPlayers(level, fighter, session, now);
                }
            }
            case STRENGTH_TRAINING -> {
                // R16.1: Strength Training is non-combat push-ups only. No stray-hit logic.
                fighter.suppressActivityAura();
                session.strengthVariant = 2;
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(25);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                fighter.setDeltaMovement(0.0D, fighter.getDeltaMovement().y, 0.0D);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 240L + fighter.getRandom().nextInt(121);
                    if (fighter.getRandom().nextFloat() < 0.20F) maybeSpeak(fighter, session.type, false);
                }
            }
            case KI_TRAINING -> {
                fighter.suppressActivityAura();
                fighter.setPose(Pose.STANDING);
                long cycle = session.kiVariant == 0 ? 140L : 360L;
                long phase = Math.floorMod(now - session.started, cycle);
                boolean charging = session.kiVariant == 0 ? phase < 95L : phase < 330L;
                fighter.setAmbientPose(session.kiVariant == 0 ? 23 : 24);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setSprinting(false);
                fighter.setKiCharge(false);
                if (session.kiCore == null) {
                    Vec3 horizontal = fighter.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                    if (horizontal.lengthSqr() < 1.0E-4D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
                    session.kiCore = fighter.position().add(0.0D, Math.max(0.95D, fighter.getBbHeight() * 0.59D), 0.0D)
                            .add(horizontal.normalize().scale(session.kiVariant == 0 ? 0.96D : 0.90D));
                }
                lockKiFocus(fighter, session.kiCore);
                if (session.kiVariant == 0) {
                    tickKiTrainingFocus(level, fighter, phase, session.kiCore);
                } else {
                    tickKiTrainingControlOrb(level, fighter, phase, session.kiCore);
                }
                fighter.setDeltaMovement(0.0D, fighter.getDeltaMovement().y, 0.0D);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 180L + fighter.getRandom().nextInt(181);
                }
            }
            case FLOWER -> {
                look(fighter, session.focus, 0.7D);
                if (!session.flowerActionDone) {
                    fighter.setAmbientPose(0);
                    if (now >= session.flowerActionAt) {
                        performFlowerPickup(level, fighter, session);
                        session.flowerActionDone = true;
                        session.flowerRecoverAt = now + 24L + fighter.getRandom().nextInt(17);
                    }
                } else if (!session.flowerRecovered && now >= session.flowerRecoverAt) {
                    session.flowerRecovered = true;
                    fighter.setAmbientPose(0);
                }
                if (session.flowerRecovered) fighter.setAmbientPose(0);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 90L + fighter.getRandom().nextInt(100);
                    maybeSpeakFlower(fighter, session, false);
                }
            }
            case TREE -> {
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(session.treeAppleReady ? 13 : 0);

                if (!session.treeAppleReady && session.treeAppleEntityId != null) {
                    ItemEntity apple = level.getEntity(session.treeAppleEntityId) instanceof ItemEntity item ? item : null;
                    if (apple == null || !apple.isAlive() || apple.getItem().isEmpty()) {
                        // Someone else may have taken the real drop. Do not creatively replace it.
                        session.treeAppleEntityId = null;
                        if (now - session.treeAppleDropAt > 80L) { finish(fighter); return; }
                    } else {
                        fighter.getLookControl().setLookAt(apple, 24.0F, 20.0F);
                        double appleDistance = fighter.distanceToSqr(apple);
                        if (appleDistance > 1.85D * 1.85D) {
                            fighter.setSprinting(false);
                            fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
                            fighter.getNavigation().moveTo(apple, 0.86D * ReactiveWorldManager.movementPace(fighter));
                            return;
                        }
                        fighter.getNavigation().stop();
                        if (apple.tickCount < 8) return; // let the visible ItemEntity actually fall first
                        ItemStack source = apple.getItem();
                        if (!source.is(Items.APPLE)) { finish(fighter); return; }
                        ItemStack picked = source.copy();
                        picked.setCount(1);
                        picked.getOrCreateTag().putBoolean("LWTemporaryActivityProp", true);
                        fighter.take(apple, 1); // vanilla pickup animation/packet from the real drop
                        source.shrink(1);
                        if (source.isEmpty()) apple.discard(); else apple.setItem(source);
                        stowMainHand(fighter);
                        fighter.setItemInHand(InteractionHand.MAIN_HAND, picked);
                        fighter.getPersistentData().putBoolean(TEMP_ITEM, true);
                        fighter.getPersistentData().putInt(TEMP_ITEM_VERSION, 2);
                        session.treeAppleReady = true;
                        fighter.setAmbientPose(13);
                        session.nextBeat = now + 42L;
                        return;
                    }
                }

                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                look(fighter, session.focus, 1.6D);
                if (now >= session.nextBeat) {
                    if (!session.treeAppleReady && session.treeStrikes < 3) {
                        session.nextBeat = now + 18L + fighter.getRandom().nextInt(18);
                        fighter.swing(InteractionHand.MAIN_HAND, true);
                        BlockState tree = level.getBlockState(session.focus);
                        if (!tree.isAir()) {
                            level.playSound(null, session.focus, tree.getSoundType(level, session.focus, fighter).getHitSound(),
                                    SoundSource.BLOCKS, 0.72F, 0.88F + fighter.getRandom().nextFloat() * 0.18F);
                        }
                        session.treeStrikes++;
                        if (session.treeStrikes >= 3) {
                            ItemEntity apple = spawnFallingTreeApple(level, fighter, session.focus);
                            if (apple == null) { finish(fighter); return; }
                            session.treeAppleEntityId = apple.getUUID();
                            session.treeAppleDropAt = now;
                            session.nextBeat = now + 20L;
                        }
                    } else if (session.treeAppleReady) {
                        session.nextBeat = now + 90L + fighter.getRandom().nextInt(80);
                        level.playSound(null, fighter.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.42F, 0.96F);
                        maybeSpeak(fighter, session.type, false);
                    }
                }
            }
            case STARGAZING -> {
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(session.lying ? 2 : 1);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                // Do not hand the head back to LookControl here. The renderer/model own a fixed skyward
                // stargazing pose, which avoids the previous left/right pitch wobble.
                fighter.setXRot(session.lying ? 0.0F : 85.0F);
                fighter.xRotO = fighter.getXRot();
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 170L + fighter.getRandom().nextInt(220);
                    maybeSpeak(fighter, session.type, false);
                }
            }
            case EATING -> {
                if (!ensureTemporaryItem(fighter, Items.BREAD)) { finish(fighter); return; }
                fighter.setAmbientPose(13);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 90L + fighter.getRandom().nextInt(80);
                    level.playSound(null, fighter.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.45F,
                            0.95F + fighter.getRandom().nextFloat() * 0.12F);
                    maybeSpeak(fighter, session.type, false);
                }
            }
            case DANCING -> {
                if (now >= session.nextDanceSwitch && now + 100L < session.expires) {
                    int next = 1 - session.danceVariant;
                    session.danceVariant = next;
                    fighter.getPersistentData().putInt(LAST_DANCE_VARIANT, next);
                    session.nextDanceSwitch = now + 180L + fighter.getRandom().nextInt(181);
                }
                fighter.setAmbientPose(3 + session.danceVariant);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 150L + fighter.getRandom().nextInt(180);
                    maybeSpeak(fighter, session.type, false);
                }
            }
            case STUDYING -> {
                if (!ensureTemporaryItem(fighter, Items.BOOK)) { finish(fighter); return; }
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                // R22: reading alternates with short thinking/look-away beats. It remains one Study
                // session and therefore preserves the passive-skill Study accounting exactly.
                if (now >= session.phaseUntil) {
                    session.activityPhase = 1 - session.activityPhase;
                    session.phaseUntil = now + (session.activityPhase == 0
                            ? 90L + fighter.getRandom().nextInt(111)
                            : 28L + fighter.getRandom().nextInt(43));
                    if (session.activityPhase == 0) {
                        fighter.setAmbientPose(15);
                        level.playSound(null, fighter.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                                SoundSource.NEUTRAL, 0.32F, 0.94F + fighter.getRandom().nextFloat() * 0.14F);
                    } else {
                        fighter.setAmbientPose(0);
                        double a = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
                        fighter.getLookControl().setLookAt(fighter.getX() + Math.cos(a) * 5.0D,
                                fighter.getEyeY(), fighter.getZ() + Math.sin(a) * 5.0D, 18.0F, 14.0F);
                    }
                } else fighter.setAmbientPose(session.activityPhase == 0 ? 15 : 0);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 150L + fighter.getRandom().nextInt(170);
                    if (fighter.getRandom().nextFloat() < 0.34F) maybeSpeak(fighter, session.type, false);
                }
            }
            case SCIENTIST_RESEARCH -> {
                if (!FighterScientistManager.isScientist(fighter)) { finish(fighter); return; }
                if (session.scientistResearchVariant == 0 && !ensureTemporaryItem(fighter, Items.BOOK)) { finish(fighter); return; }
                fighter.setPose(Pose.STANDING);
                fighter.setAmbientPose(session.scientistResearchVariant == 0 ? 15 : 16);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 85L + fighter.getRandom().nextInt(86);
                    if (session.scientistResearchVariant == 0) {
                        if (fighter.getRandom().nextBoolean()) fighter.swing(InteractionHand.MAIN_HAND, true);
                        if (fighter.getRandom().nextFloat() < 0.18F) fighter.speak(pick(fighter,
                                "Growth medium is stable. Now the temperament problem.",
                                "That batch adapted faster than the last one.",
                                "If I adjust the cultivation ratio, the next specimen should hold more power.",
                                "Strength is easy. Control is the difficult part.",
                                "The growth medium is saturating too early. That explains the plateau.",
                                "Phenotype stable, Ki leakage acceptable... aggression response still ugly.",
                                "I should normalize this against the master's permanent power, not the temporary reading."), 58);
                    } else {
                        double angle = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
                        fighter.getLookControl().setLookAt(fighter.getX() + Math.cos(angle) * 10.0D,
                                fighter.getEyeY() + fighter.getRandom().nextDouble() * 2.0D - 1.0D,
                                fighter.getZ() + Math.sin(angle) * 10.0D, 28.0F, 24.0F);
                        if (fighter.getRandom().nextFloat() < 0.16F) fighter.speak(pick(fighter,
                                "Deployment readings are cleaner than the last batch.",
                                "Reaction time improved. Stability still needs work.",
                                "I'm comparing their combat response against the cultivation notes.",
                                "The scouter data says the formula is helping. Slowly.",
                                "Power spike confirmed. Now I need to know whether it was adaptation or panic.",
                                "Telemetry looks better than the specimen did. That's exactly why I keep both records.",
                                "Damage tolerance improved, but the reaction curve is still too noisy."), 58);
                    }
                }
            }
            case SCOUTING -> {
                fighter.setAmbientPose(16);
                if (!fighter.isUsingItem()) fighter.startUsingItem(InteractionHand.MAIN_HAND);
                // Hold each scan direction for a while instead of continuously rotating like a turret.
                if (now >= session.phaseUntil) {
                    session.activityPhase = (session.activityPhase + 1 + fighter.getRandom().nextInt(2)) % 6;
                    session.phaseUntil = now + 75L + fighter.getRandom().nextInt(76);
                }
                double angle = (Math.PI * 2.0D / 6.0D) * session.activityPhase
                        + (Math.floorMod(fighter.getUUID().hashCode(), 31) / 31.0D) * 0.45D;
                fighter.getLookControl().setLookAt(fighter.getX() + Math.cos(angle) * 24.0D,
                        fighter.getEyeY() + 1.5D + Math.sin(angle * 0.5D) * 2.0D,
                        fighter.getZ() + Math.sin(angle) * 24.0D, 18.0F, 18.0F);
                if (now >= session.nextBeat) {
                    session.nextBeat = now + 170L + fighter.getRandom().nextInt(200);
                    maybeSpeak(fighter, session.type, false);
                }
            }
            default -> { }
        }
    }


    private static final int[] FOOD_SEARCH_RADII = {22, 38, 54, 70, 86, 102};

    private static boolean isFoodGatheringTime(ServerLevel level) {
        long day = Math.floorMod(level.getDayTime(), 24_000L);
        return day < 12_000L;
    }

    private static void tickFoodGathering(AmbientFighterEntity fighter, Session session, long now) {
        if (!(fighter.level() instanceof ServerLevel level)) { finish(fighter); return; }

        if (!session.foodCarried.isEmpty()) {
            stopHuntTravel(fighter);
            fighter.getNavigation().stop();
            fighter.setAggressive(false);
            fighter.setAmbientPose(13);
            if (session.foodEatAt <= 0L) session.foodEatAt = now + 38L;
            if (now >= session.foodEatAt) {
                level.playSound(null, fighter.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL,
                        0.55F, 0.94F + fighter.getRandom().nextFloat() * 0.12F);
                fighter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                session.foodCarried = ItemStack.EMPTY;
                session.foodConsumed = true;
                fighter.getPersistentData().putLong(LAST_MEAL_AT, now);
                maybeSpeak(fighter, Type.EATING, false);
                finish(fighter);
            }
            return;
        }

        if (session.foodDeathPos != null) {
            // The kill must visibly enter Minecraft's real drop pipeline before Living World even
            // considers collecting it. This prevents the old kill->instant-hand illusion.
            if (session.foodDeathAt > 0L && now - session.foodDeathAt < 12L) {
                stopHuntTravel(fighter);
                return;
            }
            ItemEntity drop = session.foodDropId != null && level.getEntity(session.foodDropId) instanceof ItemEntity exactDrop
                    && exactDrop.isAlive() && !exactDrop.getItem().isEmpty() ? exactDrop : null;
            // Compatibility fallback: normal Forge/vanilla deaths bind the exact drop UUID above.
            // Some modded prey can bypass LivingDropsEvent, so only then use a real nearby edible
            // ItemEntity. Either path consumes an entity that genuinely exists in the world.
            if (drop == null && session.foodDropId == null) drop = nearestFoodDrop(level, session.foodDeathPos, fighter);
            if (drop == null) {
                if (now >= session.foodNextSearch) {
                    session.foodNextSearch = now + 20L;
                    // If the exact drop was taken by something else, or the prey produced no edible
                    // drop, resume hunting instead of creatively granting replacement food.
                    if (session.foodDeathAt > 0L && now - session.foodDeathAt > 80L && now + 120L < session.expires) {
                        session.foodDeathPos = null;
                        session.foodDeathAt = 0L;
                        session.foodDropId = null;
                        session.foodKilledTargetId = null;
                    }
                }
                return;
            }
            fighter.getLookControl().setLookAt(drop, 24.0F, 20.0F);
            double dropDistance = fighter.distanceToSqr(drop);
            if (dropDistance > 2.1D * 2.1D) {
                moveHuntToward(fighter, drop.position(), dropDistance, session, now);
                return;
            }
            stopHuntTravel(fighter);
            fighter.getNavigation().stop();
            if (drop.tickCount < 8) return;
            ItemStack source = drop.getItem();
            if (source.isEmpty() || !isMeatLike(source)) { session.foodDeathPos = null; session.foodDropId = null; return; }
            ItemStack picked = source.copy();
            picked.setCount(1);
            fighter.take(drop, 1); // native pickup animation from the real ItemEntity
            source.shrink(1);
            if (source.isEmpty()) drop.discard(); else drop.setItem(source);
            stowMainHand(fighter);
            fighter.setItemInHand(InteractionHand.MAIN_HAND, picked);
            session.foodCarried = picked.copy();
            session.foodEatAt = now + 38L;
            fighter.setAmbientPose(13);
            return;
        }

        LivingEntity prey = session.foodTargetId == null ? null
                : level.getEntity(session.foodTargetId) instanceof LivingEntity living ? living : null;
        if (prey != null && (!prey.isAlive() || !isFoodPrey(prey))) {
            session.foodDeathPos = prey.blockPosition().immutable();
            session.foodDeathAt = now;
            session.foodTargetId = null;
            prey = null;
        }
        if (prey == null) {
            if (now < session.foodNextSearch) return;
            int radius = FOOD_SEARCH_RADII[Math.min(session.foodSearchStage, FOOD_SEARCH_RADII.length - 1)];
            prey = nearestFoodPrey(level, fighter, radius);
            session.foodNextSearch = now + 70L;
            if (prey == null) {
                stopHuntTravel(fighter);
                if (session.foodSearchStage < FOOD_SEARCH_RADII.length - 1) session.foodSearchStage++;
                else if (now + 120L >= session.expires) finish(fighter);
                return;
            }
            session.foodTargetId = prey.getUUID();
        }

        fighter.setAggressive(false); // manual real melee only; do not hand ownership to generic combat AI
        fighter.getLookControl().setLookAt(prey, 28.0F, 24.0F);
        double distance = fighter.distanceToSqr(prey);
        if (distance > 3.10D * 3.10D) {
            double dist = Math.sqrt(distance);
            Vec3 preyMotion = prey.getDeltaMovement();
            double horizontalSpeedSq = preyMotion.x * preyMotion.x + preyMotion.z * preyMotion.z;
            // Rabbits and other fleeing prey invalidate long vanilla paths constantly. Lead the
            // moving animal farther, refresh the short intercept aggressively and force the hunt
            // into run mode while the prey is actually escaping.
            boolean fleeing = horizontalSpeedSq > 0.006D;
            if (fleeing) session.foodPrefersRun = true;
            double flightThreshold = session.foodPrefersFlight ? 8.0D : 18.0D;
            boolean shouldFlyIntercept = fighter.hasFlightUnlocked() && !fighter.isInWaterOrBubble() && dist > flightThreshold;
            if (fleeing && !shouldFlyIntercept) {
                // Entity-targeted navigation is much better for rabbits than chasing yesterday's
                // predicted coordinate: every tick the path follows the actual moving mob. Keep
                // the hunter in a real RUN and let vanilla navigation continually re-anchor to it.
                if (fighter.isAmbientFlightActivity()) {
                    fighter.setFlyingFast(false);
                    fighter.setFlying(false);
                    fighter.setNoGravity(false);
                    fighter.setAmbientFlightActivity(false);
                }
                fighter.setSprinting(true);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
                fighter.getNavigation().moveTo(prey, 2.35D * ReactiveWorldManager.movementPace(fighter));
                return;
            }
            double leadTicks = Math.min(24.0D, Math.max(4.0D, dist * (fleeing ? 0.95D : 0.58D)));
            Vec3 lead = prey.position().add(preyMotion.scale(leadTicks));
            // Keep ground pursuit on the prey's current footing; predicted rabbit Y values during
            // a hop can otherwise make navigation aim at empty air. Flight can use the real Y.
            if (!fighter.hasFlightUnlocked()) lead = new Vec3(lead.x, prey.getY(), lead.z);
            moveHuntToward(fighter, lead, distance, session, now);
            return;
        }
        fighter.setSprinting(false);
        fighter.getNavigation().stop();
        if (now >= session.nextBeat) {
            session.nextBeat = now + 18L + fighter.getRandom().nextInt(9);
            fighter.swing(InteractionHand.MAIN_HAND, true);
            fighter.doHurtTarget(prey); // exact established Living World melee damage pipeline
            if (!prey.isAlive()) {
                session.foodKilledTargetId = prey.getUUID();
                session.foodDeathPos = prey.blockPosition().immutable();
                session.foodDeathAt = now;
                session.foodTargetId = null;
                session.foodNextSearch = now + 12L;
            }
        }
    }

    private static void moveHuntToward(AmbientFighterEntity fighter, Vec3 target, double distanceSq, Session session, long now) {
        double distance = Math.sqrt(Math.max(0.0D, distanceSq));
        boolean fly = fighter.hasFlightUnlocked() && !fighter.isInWaterOrBubble()
                && distance > (session.foodPrefersFlight ? 8.0D : 18.0D);
        if (fly) {
            fighter.getNavigation().stop();
            fighter.setSprinting(false);
            fighter.setAmbientFlightActivity(true);
            fighter.setFlying(true);
            fighter.setFlyingFast(distance > 24.0D);
            fighter.steerAmbientFlightToward(target, distance > 24.0D ? 0.78D : 0.62D);
            fighter.getLookControl().setLookAt(target.x, target.y, target.z, 26.0F, 22.0F);
            return;
        }
        if (fighter.isAmbientFlightActivity()) {
            fighter.setFlyingFast(false);
            fighter.setFlying(false);
            fighter.setNoGravity(false);
            fighter.setAmbientFlightActivity(false);
        }
        boolean run = distance > 3.5D && (session.foodPrefersRun || distance > 7.0D);
        fighter.setSprinting(run);
        fighter.setLocomotionMode(run ? DBSagasEntity.LocomotionMode.RUN : DBSagasEntity.LocomotionMode.WALK);
        if (fighter.getNavigation().isDone() || now % 2L == Math.floorMod(fighter.getId(), 2))
            fighter.getNavigation().moveTo(target.x, target.y, target.z,
                    (run ? 2.05D : 1.05D) * ReactiveWorldManager.movementPace(fighter));
    }

    private static void stopHuntTravel(AmbientFighterEntity fighter) {
        fighter.setSprinting(false);
        fighter.setFlyingFast(false);
        if (fighter.isAmbientFlightActivity()) {
            fighter.setFlying(false);
            fighter.setNoGravity(false);
            fighter.setAmbientFlightActivity(false);
        }
        if (fighter.getTarget() == null && !fighter.isFlying()) fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
    }

    private static LivingEntity nearestFoodPrey(ServerLevel level, AmbientFighterEntity fighter, int radius) {
        return level.getEntitiesOfClass(LivingEntity.class, fighter.getBoundingBox().inflate(radius, Math.min(24, radius), radius),
                        entity -> entity != fighter && entity.isAlive() && isFoodPrey(entity))
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
    }

    private static boolean isFoodPrey(LivingEntity entity) {
        if (entity == null || entity instanceof AmbientFighterEntity) return false;
        if (entity instanceof AgeableMob ageable && ageable.isBaby()) return false;
        if (entity instanceof Cow || entity instanceof Pig || entity instanceof Chicken
                || entity instanceof Sheep || entity instanceof Rabbit) return true;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !"dragonminez".equals(id.getNamespace())) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("baby")) return false;
        return path.equals("dinosaur") || path.equals("large_dinosaur") || path.equals("flying_dinosaur")
                || path.equals("sabertooth_tiger") || path.equals("saber_tooth_tiger")
                || (path.contains("dinosaur") && !path.contains("baby"))
                || (path.contains("saber") && path.contains("tiger"));
    }

    private static ItemEntity nearestFoodDrop(ServerLevel level, BlockPos center, AmbientFighterEntity fighter) {
        AABB area = new AABB(center).inflate(6.0D, 4.0D, 6.0D);
        return level.getEntitiesOfClass(ItemEntity.class, area,
                        item -> item.isAlive() && !item.getItem().isEmpty() && isMeatLike(item.getItem()))
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
    }

    private static boolean isEdibleDrop(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.isEdible() || isMeatLike(stack));
    }

    private static boolean isMeatLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("beef") || path.contains("pork") || path.contains("chicken")
                || path.contains("mutton") || path.contains("rabbit") || path.contains("meat")
                || path.contains("dino");
    }

    private static void tickJogging(AmbientFighterEntity fighter, Session session, long now) {
        if (!(fighter.level() instanceof ServerLevel level)) { finish(fighter); return; }
        fighter.setFlying(false);
        fighter.setFlyingFast(false);
        fighter.setNoGravity(false);
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
        fighter.setSprinting(true);
        fighter.setAggressive(true);
        Vec3 target = session.mobileTarget == null ? Vec3.atBottomCenterOf(session.stand) : session.mobileTarget;
        if (fighter.position().distanceToSqr(target) < 2.3D * 2.3D || fighter.getNavigation().isDone()) {
            target = nextCoherentGroundTarget(level, fighter, session, true);
            session.mobileTarget = target;
        }
        if (now % 16L == Math.floorMod(fighter.getId(), 16))
            fighter.getNavigation().moveTo(target.x, target.y, target.z, 1.28D * ReactiveWorldManager.movementPace(fighter));
        fighter.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z, 22.0F, 18.0F);
        if (now >= session.nextBeat) {
            session.nextBeat = now + 190L + fighter.getRandom().nextInt(220);
            maybeSpeak(fighter, session.type, false);
        }
    }

    private static void tickWalking(AmbientFighterEntity fighter, Session session, long now) {
        if (!(fighter.level() instanceof ServerLevel level)) { finish(fighter); return; }
        fighter.setFlying(false);
        fighter.setFlyingFast(false);
        fighter.setNoGravity(false);
        fighter.setAggressive(false);
        fighter.setSprinting(false);
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
        Vec3 target = session.mobileTarget == null ? Vec3.atBottomCenterOf(session.stand) : session.mobileTarget;
        if (fighter.position().distanceToSqr(target) < 2.0D * 2.0D || fighter.getNavigation().isDone()) {
            target = nextCoherentGroundTarget(level, fighter, session, false);
            session.mobileTarget = target;
        }
        if (now % 20L == Math.floorMod(fighter.getId(), 20))
            fighter.getNavigation().moveTo(target.x, target.y, target.z, 0.72D * ReactiveWorldManager.movementPace(fighter));
        fighter.getLookControl().setLookAt(target.x, target.y + 1.0D, target.z, 16.0F, 14.0F);
        if (now >= session.nextBeat) {
            session.nextBeat = now + 230L + fighter.getRandom().nextInt(280);
            maybeSpeak(fighter, session.type, false);
        }
    }

    /** R22: walking/jogging keep a heading for several legs instead of choosing unrelated endpoints. */
    private static Vec3 nextCoherentGroundTarget(ServerLevel level, AmbientFighterEntity fighter, Session session, boolean jogging) {
        if (session.routeDirection == null || session.routeDirection.lengthSqr() < 0.01D || session.routeLegs >= (jogging ? 5 : 4)) {
            double a = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
            session.routeDirection = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            session.routeLegs = 0;
        } else {
            double turn = (fighter.getRandom().nextDouble() - 0.5D) * (jogging ? 0.55D : 0.75D);
            double cos = Math.cos(turn), sin = Math.sin(turn);
            Vec3 d = session.routeDirection;
            session.routeDirection = new Vec3(d.x * cos - d.z * sin, 0.0D, d.x * sin + d.z * cos).normalize();
        }
        session.routeLegs++;
        double length = jogging ? 12.0D + fighter.getRandom().nextDouble() * 11.0D : 8.0D + fighter.getRandom().nextDouble() * 9.0D;
        Vec3 candidate = fighter.position().add(session.routeDirection.scale(length));
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, BlockPos.containing(candidate), fighter.getRandom(), 0, jogging ? 5 : 4, 14);
        if (safe == null) {
            session.routeDirection = session.routeDirection.scale(-1.0D);
            candidate = fighter.position().add(session.routeDirection.scale(Math.max(6.0D, length * 0.55D)));
            safe = AmbientFighterSpawner.findSafeGroundAround(level, BlockPos.containing(candidate), fighter.getRandom(), 0, 4, 12);
        }
        return safe == null ? fighter.position() : Vec3.atBottomCenterOf(safe);
    }

    private static void tickRelaxedFlight(AmbientFighterEntity fighter, Session session, long now) {
        if (!fighter.hasFlightUnlocked() || fighter.isInWaterOrBubble()) { finish(fighter); return; }
        fighter.setAmbientFlightActivity(true);
        fighter.setFlying(true); // genuine DMZ flight state; LW supplies only a leisure route
        fighter.getNavigation().stop();
        if (!session.landing && fighter.onGround()) {
            if (now - session.flightTakeoffStarted > 42L) {
                // Do not leave a fighter twitching on the floor while its status says Flying.
                finish(fighter);
                return;
            }
            Vec3 d = fighter.getDeltaMovement();
            fighter.setDeltaMovement(d.x, Math.max(0.31D, d.y), d.z);
        }

        if (session.landing) {
            if (session.landingTarget == null) {
                ServerLevel level = (ServerLevel) fighter.level();
                BlockPos ground = AmbientFighterSpawner.findSafeGroundAround(level, fighter.blockPosition(), fighter.getRandom(), 0, 18, 28);
                if (ground == null) ground = session.stand;
                session.landingTarget = new Vec3(ground.getX() + 0.5D, ground.getY() + 0.35D, ground.getZ() + 0.5D);
            }
            Vec3 towardGround = session.landingTarget.subtract(fighter.position());
            double distance = towardGround.length();
            fighter.setFlyingFast(false);
            if (fighter.onGround() || distance < 1.35D) {
                fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
                fighter.setFlying(false);
                fighter.setNoGravity(false);
                finish(fighter);
                return;
            }
            if (towardGround.lengthSqr() > 0.001D) {
                Vec3 wanted = towardGround.normalize().scale(0.24D);
                Vec3 delta = fighter.getDeltaMovement().scale(0.68D).add(wanted.scale(0.32D));
                if (delta.length() > 0.29D) delta = delta.normalize().scale(0.29D);
                fighter.setDeltaMovement(delta);
            }
            fighter.getLookControl().setLookAt(session.landingTarget.x, session.landingTarget.y, session.landingTarget.z, 12.0F, 10.0F);
            return;
        }
        if (!session.settled) {
            session.settled = true;
            // Lift cleanly before committing to a long route instead of snapping horizontal.
            fighter.setDeltaMovement(fighter.getDeltaMovement().scale(0.45D).add(0.0D, 0.22D, 0.0D));
            maybeSpeak(fighter, session.type, true);
        }

        if (session.flightTarget == null || now >= session.nextFlightWaypoint
                || fighter.position().distanceToSqr(session.flightTarget) < 6.0D * 6.0D) {
            session.flightTarget = chooseFlightWaypoint(fighter, session);
            session.nextFlightWaypoint = now + 180L + fighter.getRandom().nextInt(261);
        }

        Vec3 desired = session.flightTarget;
        Vec3 toward = desired.subtract(fighter.position());
        double distance = Math.sqrt(toward.lengthSqr());
        boolean longLeg = distance > 72.0D;
        fighter.setFlyingFast(longLeg);
        if (toward.lengthSqr() > 0.001D)
            fighter.steerAmbientFlightToward(desired, longLeg ? 0.54D : 0.38D);
        fighter.getLookControl().setLookAt(desired.x, desired.y, desired.z, 10.0F, 9.0F);
        if (now >= session.nextBeat) {
            session.nextBeat = now + 260L + fighter.getRandom().nextInt(360);
            maybeSpeak(fighter, session.type, false);
        }
    }

    private static Vec3 chooseFlightWaypoint(AmbientFighterEntity fighter, Session session) {
        ServerLevel level = (ServerLevel) fighter.level();
        Vec3 current = fighter.position();
        Vec3 anchor = Vec3.atCenterOf(session.stand);
        for (int attempt = 0; attempt < 14; attempt++) {
            double angle = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 48.0D + fighter.getRandom().nextDouble() * 72.0D;
            double x = current.x + Math.cos(angle) * distance;
            double z = current.z + Math.sin(angle) * distance;
            Vec3 horizontal = new Vec3(x, anchor.y, z).subtract(anchor);
            double horizontalLength = Math.sqrt(horizontal.x * horizontal.x + horizontal.z * horizontal.z);
            if (horizontalLength > 160.0D) {
                Vec3 clamped = horizontal.scale(160.0D / horizontalLength);
                x = anchor.x + clamped.x;
                z = anchor.z + clamped.z;
            }
            double hardMinY = level.getMinBuildHeight() + 6.0D;
            double hardMaxY = level.getMaxBuildHeight() - 10.0D;
            double routeMinY = Math.min(hardMaxY, Math.max(hardMinY, anchor.y + 10.0D));
            double routeMaxY = Math.max(routeMinY, Math.min(hardMaxY, anchor.y + 48.0D));
            double y = Math.max(routeMinY, Math.min(routeMaxY,
                    current.y + fighter.getRandom().nextDouble() * 28.0D - 12.0D));
            BlockPos targetBlock = BlockPos.containing(x, y, z);
            // Never force-load a chunk merely for leisure flight.
            if (level.hasChunkAt(targetBlock)) return new Vec3(x, y, z);
        }
        double fallbackY = Math.min(level.getMaxBuildHeight() - 10.0D,
                Math.max(level.getMinBuildHeight() + 6.0D, Math.max(current.y, anchor.y + 10.0D)));
        return new Vec3(current.x, fallbackY, current.z);
    }

    private static void tryStartNearby(MinecraftServer server, long now) {
        Set<UUID> checked = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(54.0D, 192.0D, 54.0D), AmbientFighterEntity::isAlive)) {
                // This piggybacks on the existing nearby-activity scan: a visibly stronger
                // real player can make a fighter choose training more often without a new
                // per-tick player search or any forced BP assignment.
                WorldPowerScaler.observeNearbyPlayerPressure(fighter, player);
                FighterDailyRoutineManager.ensurePlan(fighter);
                // Every nearby player contributes to the local pressure reading, but only one
                // existing activity pass may clean/start work for the same fighter this tick.
                if (!checked.add(fighter.getUUID())) continue;
                cleanupOrphanedItem(fighter);
                if (SESSIONS.containsKey(fighter.getUUID()) || !canStart(fighter)) continue;
                // Battle recovery is not a second parallel state. Once the established defeat/grace
                // window has ended, a one-shot battle marker may deliberately enter the existing
                // Rest/Nap activity pipeline before ordinary daily scheduling resumes.
                if (tryStartPostBattleRecovery(fighter, now)) continue;
                long next = fighter.getPersistentData().getLong(NEXT_ACTIVITY);
                if (next <= 0L) {
                    int initial = LivingWorldConfig.ambientActivityDelay(120 + fighter.getRandom().nextInt(361));
                    fighter.getPersistentData().putLong(NEXT_ACTIVITY, now + initial);
                    continue;
                }
                if (now < next) continue;
                // R10: the planner now has sixteen ~75-second intent blocks. Wake often enough to
                // actually follow those smaller beats instead of sleeping through two or three slots.
                // Existing config scaling still applies and active sessions still own the fighter until done.
                int nextDelay = LivingWorldConfig.ambientActivityDelay(360 + fighter.getRandom().nextInt(401));
                fighter.getPersistentData().putLong(NEXT_ACTIVITY, now + nextDelay);

                // R12: short life beats are interludes, not 75-second "main jobs". Try the
                // deterministic meal/brief-note/hobby beat first once per segment, then return to
                // the segment's primary intent as soon as that short beat finishes.
                if (FighterDailyRoutineManager.mayStartPlannedInterlude(fighter)) {
                    FighterDailyRoutineManager.Activity interlude = FighterDailyRoutineManager.currentInterlude(fighter);
                    FighterAmbientActivityManager.Type interludeType = FighterDailyRoutineManager.ambientType(interlude, fighter);
                    boolean interludeStarted = interludeType != null && start(fighter, interludeType, now, false);
                    FighterDailyRoutineManager.notePlannedInterludeAttempted(fighter);
                    if (interludeStarted) continue;
                }

                // A daily plan is actual intent, not merely another random weight. Once the normal
                // low-frequency activity scheduler wakes for this fighter, the current plan gets a
                // direct first attempt once per time segment. Existing eligibility, environment and
                // executor safeguards still decide whether it is physically possible.
                if (FighterDailyRoutineManager.mayStartPlannedActivity(fighter)) {
                    FighterDailyRoutineManager.Activity planned = FighterDailyRoutineManager.currentActivity(fighter);
                    boolean plannedStarted = false;
                    if (planned == FighterDailyRoutineManager.Activity.MEDITATION) {
                        int plannedMinimum = Math.max(700, FighterDailyRoutineManager.plannedMeditationMinimumTicks(fighter));
                        plannedStarted = fighter.beginMeditation(AmbientFighterEntity.naturalMeditationDuration(fighter.getRandom(), plannedMinimum));
                    } else if (planned == FighterDailyRoutineManager.Activity.SPARRING) {
                        plannedStarted = FighterPracticeSparManager.tryPlanned(fighter, now);
                    } else if (planned == FighterDailyRoutineManager.Activity.SOCIALIZING) {
                        plannedStarted = FighterNpcSocialManager.tryPlanned(fighter, false);
                    } else if (planned == FighterDailyRoutineManager.Activity.HANGING_OUT) {
                        plannedStarted = FighterNpcSocialManager.tryPlanned(fighter, true);
                    } else if (planned == FighterDailyRoutineManager.Activity.WALK_TOGETHER) {
                        plannedStarted = FighterNpcSocialManager.tryPlannedWalk(fighter);
                    } else if (planned == FighterDailyRoutineManager.Activity.MEETING_UP) {
                        plannedStarted = FighterNpcSocialManager.tryPlannedMeetUp(fighter);
                    } else {
                        Type plannedType = FighterDailyRoutineManager.ambientType(planned, fighter);
                        plannedStarted = plannedType != null && start(fighter, plannedType, now, false);
                    }
                    if (plannedStarted) {
                        FighterDailyRoutineManager.notePlannedActivityStarted(fighter, planned);
                        // Ambient sessions journal themselves only when physically underway.
                        // These non-ambient systems already returned success from their real start API.
                        if (FighterDailyRoutineManager.ambientType(planned, fighter) == null)
                            FighterDailyRoutineManager.recordActivityStart(fighter, planned.label());
                        continue;
                    }
                }

                // If a plan cannot execute (no fishing water, no spar partner, interruption, etc.),
                // preserve R7's ambient-life path as the fallback rather than leaving the NPC inert.
                float moodDrive = switch (ReactiveWorldManager.mood(fighter)) {
                    case UPBEAT -> 1.16F;
                    case FOCUSED, WARY -> 1.04F;
                    case IRRITATED -> 0.92F;
                    case SOMBER -> 0.74F;
                    case WEARY -> 0.64F;
                    case CONTENT -> 1.0F;
                };
                if (fighter.getRandom().nextFloat() > Math.min(0.95F, LivingWorldConfig.ambientActivityStartChance() * moodDrive)) continue;
                Type picked = chooseActivity(fighter, level);
                if (picked != null) start(fighter, picked, now, false);
            }
        }
    }

    private static boolean tryStartPostBattleRecovery(AmbientFighterEntity fighter, long now) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.getBoolean(POST_BATTLE_RECOVERY_PENDING)) return false;
        long due = legacy.getLong(POST_BATTLE_RECOVERY_AT);
        long battle = legacy.getLong("LastBattle");
        if (battle <= 0L || now - battle > 1600L) {
            legacy.remove(POST_BATTLE_RECOVERY_PENDING);
            legacy.remove(POST_BATTLE_RECOVERY_AT);
            return false;
        }
        if (now < due) return false;
        double health = fighter.getMaxHealth() <= 0.0F ? 1.0D : fighter.getHealth() / fighter.getMaxHealth();
        boolean lost = legacy.getString("LastResult").startsWith("Defeat");
        // A barely-touched winner does not need a theatrical recovery scene. Losing or taking
        // meaningful damage does, and uses the same Rest/Nap systems the daily routine already owns.
        if (!lost && health >= 0.88D) {
            legacy.remove(POST_BATTLE_RECOVERY_PENDING);
            legacy.remove(POST_BATTLE_RECOVERY_AT);
            return false;
        }
        Type recovery = health < 0.48D && fighter.getRandom().nextFloat() < 0.58F ? Type.NAP : Type.REST;
        boolean started = start(fighter, recovery, now, false);
        if (started) {
            legacy.remove(POST_BATTLE_RECOVERY_PENDING);
            legacy.remove(POST_BATTLE_RECOVERY_AT);
            ReactiveWorldManager.rememberEvent(fighter, "BATTLE_RECOVERY", legacy.getString("LastOpponent"),
                    recovery == Type.NAP ? "needed a nap after the fight" : "took time to recover after the fight");
        }
        return started;
    }

    private static Type chooseActivity(AmbientFighterEntity fighter, ServerLevel level) {
        FighterHobby hobby = FighterHobby.of(fighter);
        boolean freeHand = true; // activity props safely stow and restore real equipment
        List<Type> bag = new ArrayList<>();

        // Fallback life is deliberately grounded. The daily planner owns the broad structure;
        // this bag exists for missed/blocked plans and spontaneous moments, so novelty actions
        // must be rare and situational rather than becoming somebody's whole lifestyle.
        bag.add(Type.REST);
        bag.add(Type.JOGGING);
        bag.add(Type.WALKING);
        bag.add(Type.WALKING); // ordinary movement should beat novelty filler in fallback life

        int pressure = WorldPowerScaler.trainingPressure(fighter);
        if (!fighter.isNonCombatant() && pressure >= 0) bag.add(Type.TRAINING);
        if (!fighter.isNonCombatant() && pressure > 0) bag.add(Type.TRAINING);
        if (!fighter.isNonCombatant() && pressure > 1) bag.add(Type.TRAINING);
        if (!fighter.isNonCombatant() && pressure > 2) bag.add(Type.TRAINING);

        // Even the spontaneous fallback respects combat identity. The deterministic daily plan
        // remains the main schedule, but a blocked plan should not turn every specialist into the
        // same generic punch-training NPC. Cross-training stays possible rather than hard-locked.
        if (!fighter.isNonCombatant()) {
            switch (fighter.getArchetype()) {
                case KI_SPECIALIST -> { bag.add(Type.KI_TRAINING); bag.add(Type.KI_TRAINING); if (pressure > 0) bag.add(Type.KI_TRAINING); }
                case BRAWLER -> { bag.add(Type.TRAINING); bag.add(Type.TRAINING); if (pressure > 0) bag.add(Type.TRAINING); }
                case MARTIAL_ARTIST -> { bag.add(Type.TRAINING); bag.add(Type.TRAINING); }
                case SPEEDSTER -> { bag.add(Type.JOGGING); bag.add(Type.JOGGING); if (pressure > 0) bag.add(Type.TRAINING); }
                case GUARDIAN -> { bag.add(Type.TRAINING); bag.add(Type.TRAINING); }
            }
        }

        FighterPersonality personality = fighter.getPersonality();
        Type recent = Type.from(fighter.getPersistentData().getString(LAST_ACTIVITY));
        boolean recentStrenuous = recent == Type.TRAINING || recent == Type.STRENGTH_TRAINING
                || recent == Type.KI_TRAINING || recent == Type.JOGGING || recent == Type.RELAXED_FLIGHT;

        // Eating is recovery/meal punctuation, never a generic independent pastime.
        if (recentStrenuous && fighter.getRandom().nextFloat() < 0.12F) bag.add(Type.EATING);

        // Small novelty beats are intentionally uncommon unless they fit an established hobby.
        if (ReactiveWorldManager.mood(fighter) == ReactiveWorldManager.Mood.UPBEAT
                && fighter.getRandom().nextFloat() < 0.08F) bag.add(Type.DANCING);
        if (hobby == FighterHobby.MARTIAL_NOTES && fighter.getRandom().nextFloat() < 0.16F) bag.add(Type.STUDYING);
        else if (FighterPassiveSkillManager.hasUnstudiedSkill(fighter) && fighter.getRandom().nextFloat() < 0.055F) bag.add(Type.STUDYING);
        if ((hobby == FighterHobby.MAPMAKING || hobby == FighterHobby.MECHANICS || personality == FighterPersonality.CAUTIOUS)
                && fighter.getRandom().nextFloat() < 0.12F) bag.add(Type.SCOUTING);

        if (findFishingSpot(level, fighter.blockPosition()) != null) bag.add(Type.FISHING);
        if (hobby == FighterHobby.GARDENING && fighter.getRandom().nextFloat() < 0.22F
                && findFlower(level, fighter.blockPosition()) != null) bag.add(Type.FLOWER);
        if ((hobby == FighterHobby.COOKING || hobby == FighterHobby.CAMPING) && fighter.getRandom().nextFloat() < 0.08F
                && findTree(level, fighter.blockPosition()) != null) bag.add(Type.TREE);
        if (isStargazingTime(level) && findSkySpot(level, fighter.blockPosition()) != null) bag.add(Type.STARGAZING);
        if (fighter.hasFlightUnlocked() && !fighter.isNonCombatant() && ReactiveWorldManager.allowsLeisureFlight(fighter))
            bag.add(Type.RELAXED_FLIGHT);

        if (hobby == FighterHobby.FISHING && bag.contains(Type.FISHING)) {
            bag.add(Type.FISHING); bag.add(Type.FISHING); bag.add(Type.FISHING);
        }
        if (hobby == FighterHobby.STARGAZING && bag.contains(Type.STARGAZING)) {
            bag.add(Type.STARGAZING); bag.add(Type.STARGAZING); bag.add(Type.STARGAZING);
        }
        if (hobby == FighterHobby.CAMPING || hobby == FighterHobby.TEA || hobby == FighterHobby.COOKING) bag.add(Type.REST);

        boolean canFish = bag.contains(Type.FISHING), canStargaze = bag.contains(Type.STARGAZING), canFly = bag.contains(Type.RELAXED_FLIGHT);
        ReactiveWorldManager.addActivityPreferences(fighter, bag, freeHand, canFish, canStargaze, canFly);
        FighterIntentManager.addActivityPreferences(fighter, bag);

        String last = fighter.getPersistentData().getString(LAST_ACTIVITY);
        if (bag.size() > 1 && !last.isBlank()) {
            Type lastType = Type.from(last);
            if (lastType != null && fighter.level().getGameTime() - fighter.getPersistentData().getLong(LAST_ACTIVITY_AT) < 6000L) bag.remove(lastType);
        }
        return bag.isEmpty() ? null : bag.get(fighter.getRandom().nextInt(bag.size()));
    }

    public static boolean start(AmbientFighterEntity fighter, Type type, long now, boolean forced) {
        if (FactionRequestMissionManager.isAssigned(fighter)) return false;
        if (fighter == null || type == null || !(fighter.level() instanceof ServerLevel level)) return false;
        if (type == Type.STRENGTH_TRAINING) type = Type.TRAINING; // migrate old/debug callers
        if (!forced && !canStart(fighter)) return false;
        if (forced && (!fighter.isAlive() || fighter.isCaptive() || fighter.isDefeated())) return false;
        if (!forced && type == Type.EATING) {
            long lastMeal = fighter.getPersistentData().getLong(LAST_MEAL_AT);
            if (lastMeal > 0L && now - lastMeal < 2_400L) return false;
        }
        if (type == Type.FOOD_GATHERING && !forced && !isFoodGatheringTime(level)) return false;
        // Ground practice is intentionally prohibited in flight/water. The training animation
        // stack is grounded and becomes visually invalid in mid-air.
        if (type == Type.TRAINING
                && (!fighter.onGround() || fighter.isFlying() || fighter.isInWaterOrBubble())) return false;

        // Establish the fighter's real equipment before an activity ever displays a temporary
        // prop. A fishing rod, bread or spyglass must never become their remembered weapon.
        FighterArsenalManager.initializeNaturalLoadout(fighter);

        finishIfPresent(fighter);
        BlockPos stand = fighter.blockPosition();
        BlockPos focus = stand;
        if (type == Type.FISHING) {
            FishingSpot spot = findFishingSpot(level, stand);
            if (spot == null) return false;
            stand = spot.stand(); focus = spot.water();
        } else if (type == Type.REST) {
            BlockPos campfire = findCampfire(level, stand);
            if (campfire != null) {
                BlockPos resting = adjacentSafeFeet(level, campfire);
                if (resting != null) stand = resting;
                focus = campfire;
            } else {
                BlockPos quiet = findNearbySafeFeet(level, stand);
                if (quiet != null) stand = quiet;
            }
        } else if (type == Type.NAP) {
            // A lying fighter visually occupies far more space than a standing one. Generic
            // "safe feet" could still choose the final solid block beside a cliff or water.
            // Require a broad, dry resting patch instead.
            BlockPos napSpot = findNearbySafeNapFeet(level, stand);
            if (napSpot == null) return false;
            stand = napSpot;
            focus = napSpot;
        } else if (type == Type.FLOWER) {
            BlockPos flower = findFlower(level, stand);
            if (flower == null) return false;
            BlockPos near = adjacentSafeFeet(level, flower);
            if (near == null) return false;
            stand = near; focus = flower;
        } else if (type == Type.TREE) {
            BlockPos tree = findTree(level, stand);
            if (tree == null) return false;
            BlockPos near = adjacentSafeFeet(level, tree);
            if (near == null) return false;
            stand = near; focus = tree;
        } else if (type == Type.FOOD_GATHERING) {
            BlockPos safe = findNearbySafeFeet(level, stand);
            if (safe != null) stand = safe;
        } else if (type == Type.JOGGING) {
            BlockPos target = AmbientFighterSpawner.findSafeGroundAround(level, stand, fighter.getRandom(), 10, 28, 34);
            if (target != null) stand = target;
        } else if (type == Type.WALKING) {
            BlockPos target = AmbientFighterSpawner.findSafeGroundAround(level, stand, fighter.getRandom(), 6, 18, 28);
            if (target != null) stand = target;
        } else if (type == Type.STARGAZING) {
            if (!forced && !isStargazingTime(level)) return false;
            BlockPos sky = findSkySpot(level, stand);
            if (sky == null) return false;
            stand = sky; focus = sky.above(10);
        } else if (type == Type.RELAXED_FLIGHT && !fighter.hasFlightUnlocked()) return false;
        else {
            BlockPos favorite = FighterLivelinessManager.favoriteSpot(fighter, type);
            if (favorite != null && fighter.getRandom().nextFloat() < 0.58F) stand = favorite;
            else {
                BlockPos safe = findNearbySafeFeet(level, stand);
                if (safe != null) stand = safe;
            }
        }
        if (needsFreeHand(type)) stowMainHand(fighter);
        fighter.setSocialLifeActivity(true);
        Session session = new Session(fighter, type, stand, focus, now);
        SESSIONS.put(fighter.getUUID(), session);
        // Movement is the activity for these four types, so they become real immediately. All
        // stationary activities are recorded only after settle() validates and begins them.
        if (type == Type.FOOD_GATHERING || type == Type.JOGGING || type == Type.WALKING || type == Type.RELAXED_FLIGHT)
            markActualStart(fighter, session, now);
        if (type == Type.FOOD_GATHERING) fighter.getNavigation().stop();
        else if (type == Type.JOGGING) fighter.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 1.28D);
        else if (type == Type.WALKING) fighter.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0.72D);
        else if (type != Type.RELAXED_FLIGHT) fighter.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0.90D);
        return true;
    }

    /** Forces one activity on a nearby idle fighter for quick in-game checking. */
    public static int forceNearest(ServerPlayer player, String requested) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        Type type = Type.from(requested);
        if (type == null) return 0;
        Set<UUID> used = new HashSet<>();
        AmbientFighterEntity fighter = debugCandidate(player, type, used, false);
        if (fighter == null) fighter = debugCandidate(player, type, used, true);
        if (fighter == null) return 0;
        if (type == Type.RELAXED_FLIGHT && !fighter.hasFlightUnlocked()) fighter.setFlightUnlockedForDebug(true);
        boolean started = start(fighter, type, level.getServer().overworld().getGameTime(), true);
        if (started) recordDebugSubject(player, fighter);
        return started ? 1 : 0;
    }

    /** Forces one of the two synced ground-sitting poses for visual QA. */
    public static int forceSitVariant(ServerPlayer player, int variant) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        Set<UUID> used = new HashSet<>();
        AmbientFighterEntity fighter = debugCandidate(player, Type.SITTING, used, false);
        if (fighter == null) fighter = debugCandidate(player, Type.SITTING, used, true);
        if (fighter == null) return 0;
        fighter.getPersistentData().putInt(FORCED_SIT_VARIANT, Math.max(0, Math.min(1, variant)));
        boolean started = start(fighter, Type.SITTING, level.getServer().overworld().getGameTime(), true);
        if (started) recordDebugSubject(player, fighter);
        return started ? 1 : 0;
    }

    /** Forces one of the two synced dance variants on the nearest usable fighter. */
    public static int forceDanceVariant(ServerPlayer player, String requested) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        int variant = switch (requested == null ? "random" : requested.toLowerCase(Locale.ROOT)) {
            case "groove", "bounce", "0" -> 0;
            case "disco", "point", "1" -> 1;
            default -> -1;
        };
        Set<UUID> used = new HashSet<>();
        AmbientFighterEntity fighter = debugCandidate(player, Type.DANCING, used, false);
        if (fighter == null) fighter = debugCandidate(player, Type.DANCING, used, true);
        if (fighter == null) return 0;
        if (variant >= 0) fighter.getPersistentData().putInt(FORCED_DANCE_VARIANT, variant);
        boolean started = start(fighter, Type.DANCING, level.getServer().overworld().getGameTime(), true);
        if (started) recordDebugSubject(player, fighter);
        return started ? 1 : 0;
    }

    /** Puts both current dance variants next to each other for visual comparison. */
    public static int forceDanceShowcase(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        int started = 0;
        long now = level.getServer().overworld().getGameTime();
        Set<UUID> used = new HashSet<>();
        for (int variant = 0; variant < 2; variant++) {
            AmbientFighterEntity fighter = debugCandidate(player, Type.DANCING, used, false);
            if (fighter == null) fighter = debugCandidate(player, Type.DANCING, used, true);
            if (fighter == null) continue;
            fighter.getPersistentData().putInt(FORCED_DANCE_VARIANT, variant);
            if (start(fighter, Type.DANCING, now, true)) { used.add(fighter.getUUID()); recordDebugSubject(player, fighter); started++; }
        }
        return started;
    }

    /** Forces one of the two normal idle-stretch animations for visual QA. */
    public static int forceIdleVariant(ServerPlayer player, int variant) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        Set<UUID> used = new HashSet<>();
        AmbientFighterEntity fighter = debugCandidate(player, Type.REST, used, false);
        if (fighter == null) fighter = debugCandidate(player, Type.REST, used, true);
        if (fighter == null) return 0;
        finishIfPresent(fighter);
        fighter.getNavigation().stop();
        fighter.setAmbientPose(9 + Math.max(0, Math.min(1, variant)));
        fighter.getPersistentData().putLong("LWIdleStretchUntil", level.getGameTime() + 62L);
        fighter.speak(pick(fighter, "Needed that stretch.", "Loosen up a little.", "Back was getting stiff.", "Just stretching out."), 58);
        recordDebugSubject(player, fighter);
        return 1;
    }

    /** Shows both idle-stretch variants at once. */
    public static int forceIdleShowcase(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        int started = 0;
        Set<UUID> used = new HashSet<>();
        for (int variant = 0; variant < 2; variant++) {
            AmbientFighterEntity fighter = debugCandidate(player, Type.REST, used, false);
            if (fighter == null) fighter = debugCandidate(player, Type.REST, used, true);
            if (fighter == null) continue;
            finishIfPresent(fighter);
            fighter.getNavigation().stop();
            fighter.setAmbientPose(9 + variant);
            fighter.getPersistentData().putLong("LWIdleStretchUntil", level.getGameTime() + 62L);
            fighter.speak(pick(fighter, "Needed that stretch.", "Loosen up a little.", "Back was getting stiff.", "Just stretching out."), 58);
            recordDebugSubject(player, fighter);
            used.add(fighter.getUUID());
            started++;
        }
        return started;
    }

    /** Starts each available activity on a different nearby fighter; missing test actors are spawned. */
    public static int forceShowcase(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        clearDebugSubjects(player);
        int started = 0;
        long now = level.getServer().overworld().getGameTime();
        Set<UUID> used = new HashSet<>();
        for (Type type : Type.values()) {
            AmbientFighterEntity fighter = debugCandidate(player, type, used, false);
            if (fighter == null) fighter = debugCandidate(player, type, used, true);
            if (fighter == null) continue;
            if (type == Type.RELAXED_FLIGHT && !fighter.hasFlightUnlocked()) fighter.setFlightUnlockedForDebug(true);
            if (start(fighter, type, now, true)) {
                used.add(fighter.getUUID());
                recordDebugSubject(player, fighter);
                started++;
            }
        }
        return started;
    }

    private static void clearDebugSubjects(ServerPlayer player) {
        if (player != null) player.getPersistentData().remove(DEBUG_SUBJECTS);
    }

    public static void clearDebugSubjectsForQA(ServerPlayer player) { clearDebugSubjects(player); }

    private static void recordDebugSubject(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return;
        String existing = player.getPersistentData().getString(DEBUG_SUBJECTS);
        String name = fighter.getFighterName();
        if (name == null || name.isBlank()) name = fighter.getUUID().toString();
        player.getPersistentData().putString(DEBUG_SUBJECTS, existing.isBlank() ? name : existing + ", " + name);
    }

    public static String debugSubjects(ServerPlayer player) {
        if (player == null) return "";
        return player.getPersistentData().getString(DEBUG_SUBJECTS);
    }

    public static void recordDebugSubjectForQA(ServerPlayer player, AmbientFighterEntity fighter) {
        recordDebugSubject(player, fighter);
    }

    public static String debugActivityLabel(String requested) {
        Type type = Type.from(requested);
        return type == null ? (requested == null ? "" : requested) : type.label();
    }

    private static AmbientFighterEntity debugCandidate(ServerPlayer player, Type type, Set<UUID> used, boolean spawnIfNeeded) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        List<AmbientFighterEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(52.0D), f -> debugUsable(f, type) && !used.contains(f.getUUID())));
        nearby.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        if (!nearby.isEmpty()) {
            AmbientFighterEntity fighter = nearby.get(0);
            FighterArsenalManager.initializeNaturalLoadout(fighter);
            prepareDebugSubject(fighter);
            return fighter;
        }
        if (!spawnIfNeeded) return null;

        // Spawn a disposable test actor only when there is no safe/applicable existing fighter.
        for (int attempt = 0; attempt < 8; attempt++) {
            AmbientFighterEntity spawned = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.NEUTRAL, FighterRank.TRAINED, true);
            if (spawned == null) continue;
            FighterArsenalManager.initializeNaturalLoadout(spawned);
            if (type == Type.RELAXED_FLIGHT) spawned.setFlightUnlockedForDebug(true);
            if (type == Type.SCIENTIST_RESEARCH) FighterScientistManager.forceScientist(spawned);
            prepareDebugSubject(spawned);
            return spawned;
        }
        return null;
    }

    /**
     * Debug owns the physically nearest safe/applicable fighter. Ordinary life states are
     * interruptible for QA; combat, sanctioned matches, transformation and travelling companions
     * are not. This prevents a busy NPC five blocks away being skipped for an idle one 40 blocks away.
     */
    private static boolean debugUsable(AmbientFighterEntity fighter, Type type) {
        if (fighter == null || com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(fighter) || WorldMenaceManager.isHerobrine(fighter) || !fighter.isAlive() || fighter.isCaptive()
                || fighter.isDefeated() || fighter.isRecovering() || fighter.isTransforming() || fighter.isKaiokenActive()
                || fighter.getTarget() != null || fighter.isSanctionedMatchParticipant() || LivingBondManager.isTravellingCompanion(fighter)) return false;
        if (type == Type.SCIENTIST_RESEARCH && !FighterScientistManager.isScientist(fighter)) return false;
        return true;
    }

    private static void prepareDebugSubject(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        finishIfPresent(fighter);
        FighterNpcSocialManager.cancelForDebug(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) fighter.stopMeditation(false);
        fighter.getNavigation().stop();
        fighter.setSocialLifeActivity(false);
        fighter.setAmbientPose(0);
    }

    public static String currentActivity(AmbientFighterEntity fighter) {
        Session session = fighter == null ? null : SESSIONS.get(fighter.getUUID());
        if (session == null) return "";
        if (!session.actualStarted) return "Heading to " + session.type.label();
        if (session.type == Type.DANCING) return session.danceVariant == 1 ? "Dancing • Disco" : "Dancing • Groove";
        if (session.type == Type.SCIENTIST_RESEARCH) return session.scientistResearchVariant == 0
                ? "Improving Saibaman formula" : "Analyzing Saibaman combat data";
        return session.type.label();
    }

    public static String recentActivity(AmbientFighterEntity fighter) {
        if (fighter == null) return "";
        String current = currentActivity(fighter);
        if (!current.isBlank()) return current;
        long when = fighter.getPersistentData().getLong(LAST_ACTIVITY_AT);
        if (when <= 0L || fighter.level().getGameTime() - when > 6000L) return "";
        Type type = Type.from(fighter.getPersistentData().getString(LAST_ACTIVITY));
        return type == null ? "" : type.label();
    }

    private static boolean canStart(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isHerobrine(fighter)) return false;
        return fighter.isAlive() && !fighter.isCaptive() && !fighter.isDefeated() && !fighter.isRecovering()
                && !fighter.isMeditating() && !fighter.isPreparingMeditation() && !fighter.isTransforming() && !fighter.isKaiokenActive()
                && fighter.getTarget() == null && !fighter.isSocialLifeActivity() && !fighter.isSocialPlayerApproach()
                && !fighter.isSocialPowerDisplay() && !fighter.isSanctionedMatchParticipant()
                && !LivingBondManager.isTravellingCompanion(fighter);
    }

    private static boolean needsFreeHand(Type type) {
        return type == Type.FISHING || type == Type.EATING || type == Type.SCOUTING || type == Type.STUDYING || type == Type.SCIENTIST_RESEARCH || type == Type.FLOWER || type == Type.TREE;
    }

    private static boolean validDuringActivity(AmbientFighterEntity fighter) {
        if (!fighter.isAlive() || fighter.isCaptive() || fighter.isDefeated() || fighter.isMeditating()
                || fighter.isPreparingMeditation() || fighter.isTransforming() || LivingBondManager.isTravellingCompanion(fighter)) return false;
        long lastDamage = fighter.getPersistentData().getLong("LWLastDamageTime");
        if (lastDamage > 0L && fighter.level().getGameTime() - lastDamage <= 45L) return false;
        // The native saga AI can perform a one-tick target acquisition before LW's ownership tick.
        // Clear harmless intent instead of treating it as a reason to abandon a committed activity.
        if (fighter.getTarget() != null) {
            fighter.setTarget(null);
            fighter.setAttacking(false);
            fighter.setKiCharge(false);
        }
        return true;
    }

    private static boolean isShortLifeBeat(Type type) {
        return type == Type.EATING || type == Type.STUDYING || type == Type.SCOUTING || type == Type.FLOWER
                || type == Type.TREE || type == Type.DANCING;
    }

    private static boolean isStargazingTime(ServerLevel level) {
        long day = level.getDayTime() % 24000L;
        return day >= 12800L && day <= 22800L && !level.isRaining();
    }

    private record FishingSpot(BlockPos stand, BlockPos water) {}

    private static FishingSpot findFishingSpot(ServerLevel level, BlockPos origin) {
        FishingSpot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            if (dx * dx + dz * dz < 9 || dx * dx + dz * dz > 100) continue;
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos water = origin.offset(dx, dy, dz);
                if (!level.getFluidState(water).is(FluidTags.WATER) || !level.getFluidState(water.above()).isEmpty()) continue;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos near = water.relative(direction);
                    BlockPos feet = validFeet(level, near) ? near : validFeet(level, near.above()) ? near.above() : validFeet(level, near.below()) ? near.below() : null;
                    if (feet == null) continue;
                    double d = origin.distSqr(feet);
                    if (d < bestDistance) { bestDistance = d; best = new FishingSpot(feet.immutable(), water.immutable()); }
                }
            }
        }
        return best;
    }


    private static BlockPos findFlower(ServerLevel level, BlockPos origin) {
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx=-9; dx<=9; dx++) for (int dz=-9; dz<=9; dz++) for (int dy=-2; dy<=2; dy++) {
            BlockPos p = origin.offset(dx,dy,dz);
            if (!level.getBlockState(p).is(BlockTags.SMALL_FLOWERS)) continue;
            if (adjacentSafeFeet(level, p) == null) continue;
            double d=origin.distSqr(p); if (d<bestD) { bestD=d; best=p.immutable(); }
        }
        return best;
    }

    private static BlockPos findTree(ServerLevel level, BlockPos origin) {
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx=-14; dx<=14; dx++) for (int dz=-14; dz<=14; dz++) for (int dy=-3; dy<=5; dy++) {
            BlockPos p = origin.offset(dx,dy,dz);
            if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
            if (adjacentSafeFeet(level, p) == null) continue;
            double d=origin.distSqr(p); if (d<bestD) { bestD=d; best=p.immutable(); }
        }
        return best;
    }

    private static ItemEntity spawnFallingTreeApple(ServerLevel level, AmbientFighterEntity fighter, BlockPos tree) {
        if (level == null || fighter == null || tree == null) return null;
        BlockPos top = tree;
        for (int dy = 1; dy <= 7; dy++) {
            BlockPos candidate = tree.above(dy);
            BlockState state = level.getBlockState(candidate);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) top = candidate;
        }
        ItemEntity apple = new ItemEntity(level, top.getX() + 0.5D, top.getY() + 1.15D, top.getZ() + 0.5D,
                new ItemStack(Items.APPLE));
        double angle = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
        apple.setDeltaMovement(Math.cos(angle) * 0.075D, 0.08D, Math.sin(angle) * 0.075D);
        apple.setDefaultPickUpDelay();
        apple.getPersistentData().putUUID("LWTreeActivityOwner", fighter.getUUID());
        return level.addFreshEntity(apple) ? apple : null;
    }

    private static BlockPos findCampfire(ServerLevel level, BlockPos origin) {
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx=-10; dx<=10; dx++) for (int dz=-10; dz<=10; dz++) for (int dy=-3; dy<=3; dy++) {
            BlockPos p = origin.offset(dx,dy,dz);
            if (!level.getBlockState(p).is(Blocks.CAMPFIRE) && !level.getBlockState(p).is(Blocks.SOUL_CAMPFIRE)) continue;
            double d=origin.distSqr(p); if (d<bestD) { bestD=d; best=p.immutable(); }
        }
        return best;
    }

    private static BlockPos findSkySpot(ServerLevel level, BlockPos origin) {
        for (int r=0; r<=8; r++) for (int dx=-r; dx<=r; dx++) for (int dz=-r; dz<=r; dz++) {
            if (r>0 && Math.abs(dx)!=r && Math.abs(dz)!=r) continue;
            for (int dy=-2; dy<=2; dy++) {
                BlockPos p=origin.offset(dx,dy,dz);
                if (validFeet(level,p) && level.canSeeSky(p.above())) return p.immutable();
            }
        }
        return null;
    }

    private static BlockPos findNearbySafeFeet(ServerLevel level, BlockPos origin) {
        if (validFeet(level, origin)) return origin.immutable();
        for (int r=1;r<=6;r++) for (int dx=-r;dx<=r;dx++) for (int dz=-r;dz<=r;dz++) {
            if (Math.abs(dx)!=r && Math.abs(dz)!=r) continue;
            for (int dy=-2;dy<=2;dy++) { BlockPos p=origin.offset(dx,dy,dz); if(validFeet(level,p)) return p.immutable(); }
        }
        return null;
    }

    /**
     * Nap-specific placement. A normal standing NPC only needs one supported column; the lying
     * animation reads as roughly two blocks long, so insist on a full 3x3 dry, supported patch.
     * This rejects ledges, bridge edges, tiny islands and shoreline blocks.
     */
    private static BlockPos findNearbySafeNapFeet(ServerLevel level, BlockPos origin) {
        if (validNapFeet(level, origin)) return origin.immutable();
        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (validNapFeet(level, p)) return p.immutable();
                }
            }
        }
        return null;
    }

    private static boolean validNapFeet(ServerLevel level, BlockPos center) {
        if (!validFeet(level, center)) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos feet = center.offset(dx, 0, dz);
            BlockPos floorPos = feet.below();
            BlockState floor = level.getBlockState(floorPos);
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                    || !level.getFluidState(floorPos).isEmpty()) return false;
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) return false;
            if (!floor.isFaceSturdy(level, floorPos, Direction.UP)) return false;
        }
        return true;
    }

    private static BlockPos adjacentSafeFeet(ServerLevel level, BlockPos center) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p=center.relative(d);
            if (validFeet(level,p)) return p.immutable();
            if (validFeet(level,p.above())) return p.above().immutable();
        }
        return null;
    }

    private static boolean validFeet(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet), headState = level.getBlockState(feet.above());
        BlockPos floorPos = feet.below(); BlockState floor = level.getBlockState(floorPos);
        return feetState.isAir() && headState.isAir() && floor.isFaceSturdy(level, floorPos, Direction.UP);
    }

    private static void castFishingHook(AmbientFighterEntity fighter, Session session, long now) {
        session.castStart = now;
        Vec3 start = new Vec3(fighter.getX(), fighter.getEyeY() - 0.18D, fighter.getZ());
        fighter.setFishingActivity(true);
        fighter.setFishingBobberPosition(start);
        fighter.swing(InteractionHand.MAIN_HAND, true);
        if (fighter.level() instanceof ServerLevel level) {
            level.playSound(null, fighter.blockPosition(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.NEUTRAL, 0.45F, 0.92F + fighter.getRandom().nextFloat() * 0.16F);
        }
    }

    private static void tickFishingHook(ServerLevel level, AmbientFighterEntity fighter, Session session, long now) {
        if (!fighter.isFishingActivity()) castFishingHook(fighter, session, now);
        Vec3 start = new Vec3(fighter.getX(), fighter.getEyeY() - 0.18D, fighter.getZ());
        Vec3 end = new Vec3(session.focus.getX() + 0.5D, session.focus.getY() + 0.28D, session.focus.getZ() + 0.5D);
        double t = Math.min(1.0D, Math.max(0.0D, (now - session.castStart) / 12.0D));
        Vec3 pos;
        if (t < 1.0D) {
            double arc = Math.sin(t * Math.PI) * 0.90D;
            pos = start.lerp(end, t).add(0.0D, arc, 0.0D);
        } else {
            // Once the cast lands, keep a subtle vanilla-like float and occasional bite dip.
            long waterTicks = now - session.castStart - 12L;
            double idleBob = Math.sin(waterTicks * 0.12D) * 0.025D;
            long cycle = Math.floorMod(waterTicks, 220L);
            double bite = cycle >= 186L && cycle <= 198L ? -0.13D * Math.sin((cycle - 186L) / 12.0D * Math.PI) : 0.0D;
            pos = end.add(0.0D, idleBob + bite, 0.0D);
            if (cycle == 186L) {
                level.sendParticles(ParticleTypes.FISHING, end.x, end.y + 0.05D, end.z, 8, 0.42D, 0.02D, 0.42D, 0.02D);
                level.playSound(null, end.x, end.y, end.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.34F,
                        0.92F + fighter.getRandom().nextFloat() * 0.14F);
            }
            // Reel and cast again occasionally so fishing reads as an actual repeated activity
            // rather than a static rod prop. No loot is fabricated.
            if (waterTicks >= 300L) {
                fighter.swing(InteractionHand.MAIN_HAND, true);
                level.playSound(null, fighter.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                        SoundSource.NEUTRAL, 0.32F, 0.95F + fighter.getRandom().nextFloat() * 0.10F);
                castFishingHook(fighter, session, now + 6L);
                return;
            }
        }
        fighter.setFishingBobberPosition(pos);
    }

    private static void removeFishingHook(AmbientFighterEntity fighter) {
        if (fighter != null) fighter.setFishingActivity(false);
    }

    private static void faceStable(AmbientFighterEntity fighter, BlockPos target) {
        if (fighter == null || target == null) return;
        double dx = target.getX() + 0.5D - fighter.getX();
        double dz = target.getZ() + 0.5D - fighter.getZ();
        if (dx * dx + dz * dz < 0.001D) return;
        float yaw = (float)(Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        fighter.setYRot(yaw);
        fighter.yBodyRot = yaw;
        fighter.setYHeadRot(yaw);
        fighter.setXRot(10.0F);
        fighter.xRotO = 10.0F;
    }

    private static void look(AmbientFighterEntity fighter, BlockPos focus, double yOffset) {
        fighter.getLookControl().setLookAt(focus.getX()+0.5D, focus.getY()+yOffset, focus.getZ()+0.5D, 25.0F, 25.0F);
    }

    private static void performFlowerPickup(ServerLevel level, AmbientFighterEntity fighter, Session session) {
        if (level == null || fighter == null || session == null) return;
        // R13 simplification: flowers are an observation/hobby beat, not an automatic harvest.
        // Keep the established reach/swing animation and flower-specific dialogue while leaving
        // the real block exactly where it is.
        fighter.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean pickupDroppedTemporaryItem(AmbientFighterEntity fighter, net.minecraft.world.item.Item item) {
        if (fighter == null || item == null || !fighter.getMainHandItem().isEmpty()) return false;
        ItemEntity drop = fighter.level().getEntitiesOfClass(ItemEntity.class, fighter.getBoundingBox().inflate(3.25D),
                entity -> entity.isAlive() && !entity.getItem().isEmpty() && entity.getItem().is(item))
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (drop == null) return false;
        ItemStack source = drop.getItem();
        ItemStack picked = source.copy();
        picked.setCount(1);
        picked.getOrCreateTag().putBoolean("LWTemporaryActivityProp", true);
        source.shrink(1);
        if (source.isEmpty()) drop.discard(); else drop.setItem(source);
        fighter.setItemInHand(InteractionHand.MAIN_HAND, picked);
        fighter.getPersistentData().putBoolean(TEMP_ITEM, true);
        fighter.getPersistentData().putInt(TEMP_ITEM_VERSION, 2);
        return true;
    }

    private static boolean ensureTemporaryItem(AmbientFighterEntity fighter, net.minecraft.world.item.Item item) {
        ItemStack held = fighter.getMainHandItem();
        if (held.is(item)) return true;
        if (!held.isEmpty()) return false;
        ItemStack temporary = new ItemStack(item);
        temporary.getOrCreateTag().putBoolean("LWTemporaryActivityProp", true);
        fighter.setItemInHand(InteractionHand.MAIN_HAND, temporary);
        fighter.getPersistentData().putBoolean(TEMP_ITEM, true);
        fighter.getPersistentData().putInt(TEMP_ITEM_VERSION, 2);
        return true;
    }

    private static boolean isKnownTemporaryProp(ItemStack stack) {
        return stack.is(Items.FISHING_ROD) || stack.is(Items.BREAD) || stack.is(Items.SPYGLASS) || stack.is(Items.APPLE);
    }

    private static void clearTemporaryItem(AmbientFighterEntity fighter) {
        if (!fighter.getPersistentData().getBoolean(TEMP_ITEM)) return;
        ItemStack held = fighter.getMainHandItem();
        boolean markedProp = !held.isEmpty() && held.hasTag() && held.getTag().getBoolean("LWTemporaryActivityProp");
        int markerVersion = fighter.getPersistentData().getInt(TEMP_ITEM_VERSION);
        boolean ownsHeld = held.isEmpty() || markedProp
                || (markerVersion <= 0 && isKnownTemporaryProp(held)); // migration for pre-RC6 transient state
        if (ownsHeld && !held.isEmpty()) fighter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        fighter.getPersistentData().remove(TEMP_ITEM);
        fighter.getPersistentData().remove(TEMP_ITEM_VERSION);
    }

    private static void stowMainHand(AmbientFighterEntity fighter) {
        if (fighter.getPersistentData().contains(STOWED_MAIN_HAND)) return;
        ItemStack held = fighter.getMainHandItem();
        if (held.isEmpty()) return;
        CompoundTag saved = new CompoundTag();
        held.save(saved);
        fighter.getPersistentData().put(STOWED_MAIN_HAND, saved);
        fighter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void restoreMainHand(AmbientFighterEntity fighter) {
        if (!fighter.getPersistentData().contains(STOWED_MAIN_HAND, net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        CompoundTag saved = fighter.getPersistentData().getCompound(STOWED_MAIN_HAND);
        fighter.getPersistentData().remove(STOWED_MAIN_HAND);
        if (!fighter.getMainHandItem().isEmpty()) return;
        ItemStack restored = ItemStack.of(saved);
        if (!restored.isEmpty()) fighter.setItemInHand(InteractionHand.MAIN_HAND, restored);
    }

    private static void warnAndBumpNearbyPlayers(ServerLevel level, AmbientFighterEntity fighter, Session session, long now) {
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, fighter.getBoundingBox().inflate(3.0D), p -> !p.isCreative() && !p.isSpectator());
        if (near.isEmpty()) return;
        ServerPlayer closest = near.stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (closest == null) return;
        if (now >= session.nextProximityWarning) {
            fighter.speak(pick(fighter, "Careful—I'm training here.", "Heads up. You're inside my training range.", "Give me a little room—these strikes are fast.",
                    "Watch the reach on this one.", "Give me two steps of space, please.", "You're close enough to catch a stray hit."), 52);
            session.nextProximityWarning = now + 180L;
        }
        if (fighter.distanceToSqr(closest) <= 2.05D * 2.05D && now >= session.nextTrainingAccident) {
            // This method is called on the actual training strike beat, so a player standing in
            // punching range can genuinely get clipped instead of relying on an invisible 16% roll.
            session.nextTrainingAccident = now + 70L;
            closest.hurt(level.damageSources().generic(), 1.0F);
            double dx = closest.getX() - fighter.getX(), dz = closest.getZ() - fighter.getZ();
            double len = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
            closest.push(dx / len * 0.35D, 0.12D, dz / len * 0.35D);
            fighter.speak(pick(fighter, "Whoa—careful!", "Sorry! I warned you.", "You okay? That's why I need space.", "Sorry—that one reached farther than I thought.", "My fault. Step back a little."), 48);
        }
    }

    private static String friendlyBlockName(BlockState state) {
        if (state == null || state.isAir()) return "flower";
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path == null || path.isBlank()) return "flower";
        String[] parts = path.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? "flower" : out.toString();
    }

    private static void maybeSpeakFlower(AmbientFighterEntity fighter, Session session, boolean opening) {
        if (fighter.getSpeech() != null && !fighter.getSpeech().isEmpty()) return;
        if (!opening && fighter.getRandom().nextFloat() > 0.48F) return;
        String flower = session == null || session.flowerName == null || session.flowerName.isBlank() ? "flower" : session.flowerName;
        String lower = flower.toLowerCase(Locale.ROOT);
        String line;
        if (lower.contains("dandelion")) {
            line = pick(fighter, "A dandelion. Tough little thing.", "These grow anywhere they get the chance.",
                    "Yellow really stands out after staring at dirt and stone all day.");
        } else if (lower.contains("poppy")) {
            line = pick(fighter, "That poppy is ridiculously bright.", "A red poppy in the middle of all this. Nice.",
                    "I'd probably have crushed that poppy if I wasn't looking down.");
        } else if (lower.contains("blue orchid")) {
            line = pick(fighter, "A blue orchid... you don't see that everywhere.", "That blue orchid almost looks unreal.",
                    "Okay, this one's worth stopping for.");
        } else if (lower.contains("tulip")) {
            line = pick(fighter, "A " + flower + ". Clean shape. I like it.", "That " + flower + " looks almost planted on purpose.",
                    "Somebody would probably put this " + flower + " in a vase. I'd rather leave it here.");
        } else if (lower.contains("lily")) {
            line = pick(fighter, "A " + flower + ". Pretty calm-looking for this world.", "This " + flower + " picked a peaceful spot.",
                    "I'd hate to step on this one by accident.");
        } else if (lower.contains("cornflower")) {
            line = pick(fighter, "That cornflower is a good shade of blue.", "A cornflower. Small, but hard to miss once you notice it.",
                    "I almost walked straight past that blue.");
        } else if (lower.contains("allium")) {
            line = pick(fighter, "An allium. Looks like a tiny purple explosion.", "That allium has more personality than some fighters I know.",
                    "Purple suits this place better than I expected.");
        } else {
            line = pick(fighter,
                    "A " + flower + ". Didn't expect to find one here.",
                    "Almost walked right past this " + flower + ".",
                    "You ever actually stop and look at a " + flower + "?",
                    "Not bad. This " + flower + " picked a good spot.",
                    "Even fighters can appreciate a decent " + flower + ".",
                    "Funny how a little " + flower + " can survive out here.",
                    "I know power levels better than plants, but this " + flower + " is pretty nice.",
                    session != null && session.flowerTaken ? "Hope this " + flower + " survives the trip." : "I'll leave this " + flower + " where it is.",
                    fighter.level().isNight() ? "A " + flower + " under the night sky. Not bad." : "The light catches this " + flower + " nicely.");
        }
        fighter.speak(line, 70);
    }

    private static void queueUnarmedTrainingStrike(AmbientFighterEntity fighter, Session session, long now) {
        if (fighter == null || session == null) return;
        session.unarmedStrikePending = true;
        session.unarmedStrikeAt = now + 1L;
    }

    private static boolean emitQueuedUnarmedTrainingStrike(AmbientFighterEntity fighter, Session session, long now) {
        if (fighter == null || session == null || !session.unarmedStrikePending || now < session.unarmedStrikeAt) return false;
        session.unarmedStrikePending = false;
        fighter.performUnarmedTrainingStrike();
        return true;
    }

    private static void spawnInwardKiTrail(ServerLevel level, Vec3 start, Vec3 core, int segments) {
        if (level == null || start == null || core == null) return;
        // Three deterministic samples form a clean converging streak. Never spawn beyond the
        // destination: the old 1.04 sample was literally on the far side of the orb, and the old
        // velocity could not cover the remaining distance within the particle lifetime.
        Vec3 delta = core.subtract(start);
        double[] samples = {0.0D, 0.32D, 0.64D};
        for (double t : samples) {
            Vec3 point = start.add(delta.scale(t));
            // DRAW lives for ten client ticks in R42; ~0.105 reaches/crosses the core just as the
            // streak fades instead of leaving particles stranded halfway through the gather.
            Vec3 velocity = core.subtract(point).scale(0.105D);
            level.sendParticles(LWKiTrainingParticles.DRAW.get(), point.x, point.y, point.z, 0,
                    velocity.x, velocity.y, velocity.z, 1.0D);
        }
    }

    private static void lockKiFocus(AmbientFighterEntity fighter, Vec3 core) {
        if (fighter == null || core == null) return;
        double dx = core.x - fighter.getX();
        double dz = core.z - fighter.getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        fighter.setYRot(yaw);
        fighter.yBodyRot = yaw;
        fighter.yBodyRotO = yaw;
        fighter.setYHeadRot(yaw);
        fighter.yHeadRotO = yaw;
        fighter.getLookControl().setLookAt(core.x, core.y, core.z, 30.0F, 30.0F);
    }

    private static int strengthPoseId(int variant) { return 25; }

    /** Slow Ki-control variant: build and hold one dense ball instead of firing it. */
    private static void tickKiTrainingControlOrb(ServerLevel level, AmbientFighterEntity fighter, long phase, Vec3 core) {
        Vec3 forward = new Vec3(core.x - fighter.getX(), 0.0D, core.z - fighter.getZ());
        if (forward.lengthSqr() < 1.0E-4D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double progress = phase < 260L ? Math.min(1.0D, phase / 260.0D) : 1.0D;
        double settle = phase > 330L ? Math.max(0.0D, 1.0D - (phase - 330L) / 30.0D) : 1.0D;
        double size = progress * settle;
        if (phase % 4L == 0L && size > 0.04D) {
            // R40: keep the established moving ring/shell and add a separately rendered,
            // full-bright centered mass. The size signal follows the same accumulation curve.
            level.sendParticles(LWKiTrainingParticles.CORE.get(), core.x, core.y, core.z, 0,
                    Math.max(0.08D, size), 0.90D, 1.0D, 1.0D);
            // Two moving samples are enough to communicate a growing spherical orb. Radius grows
            // geometrically, while particle count stays constant regardless of orb size.
            double radius = 0.09D + 0.66D * size;
            for (int i = 0; i < 2; i++) {
                double theta = phase * 0.11D + i * Math.PI;
                double y = Math.sin(theta * 1.7D) * radius * 0.70D;
                double planar = Math.sqrt(Math.max(0.0D, radius * radius - y * y));
                Vec3 shell = core.add(right.scale(Math.cos(theta) * planar))
                        .add(forward.scale(Math.sin(theta) * planar)).add(0.0D, y, 0.0D);
                level.sendParticles(LWKiTrainingParticles.FOCUS.get(), shell.x, shell.y, shell.z, 0,
                        0.42D, 0.78D, 1.0D, 1.0D);
            }
        }
        if (phase % 4L == 0L && phase < 320L) {
            double a = phase * 0.19D;
            for (int i = 0; i < 3; i++) {
                double ring = 0.85D + 0.55D * (1.0D - progress);
                double ang = a + i * (Math.PI * 2.0D / 3.0D);
                Vec3 start = core.add(right.scale(Math.cos(ang) * ring)).add(0.0D, Math.sin(ang) * 0.58D, 0.0D)
                        .add(forward.scale(-0.15D - 0.25D * Math.sin(ang * 0.7D)));
                spawnInwardKiTrail(level, start, core, 5);
            }
        }
        // At full control the ball pulses in place rather than being released.
        if (phase >= 250L && phase < 330L && phase % 20L == 0L)
            level.sendParticles(LWKiTrainingParticles.PULSE.get(), core.x, core.y, core.z, 0, 0.36D + 0.12D * size, 0.72D, 1.0D, 1.0D);
        if (phase == 330L)
            for (int i=0;i<3;i++) level.sendParticles(LWKiTrainingParticles.PULSE.get(), core.x, core.y, core.z, 0, 0.24D, 0.60D, 1.0D, 1.0D);
    }

    /** Harmless visual focus drill for Ki Training; never damages entities or spawns a projectile. */
    private static void tickKiTrainingFocus(ServerLevel level, AmbientFighterEntity fighter, long phase, Vec3 core) {
        Vec3 horizontal = new Vec3(core.x - fighter.getX(), 0.0D, core.z - fighter.getZ());
        if (horizontal.lengthSqr() < 1.0E-4D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        horizontal = horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);

        // Living World-owned anime visuals: luminous compression core + sharp Ki draw-lines.
        // These are deliberately not vanilla END_ROD/GLOW and are not meditation's particles.
        if (phase < 95L) {
            double progress = Math.min(1.0D, phase / 78.0D);
            if (phase % 4L == 0L) {
                level.sendParticles(LWKiTrainingParticles.CORE.get(), core.x, core.y, core.z, 0,
                        Math.max(0.08D, progress * 0.92D), 0.90D, 1.0D, 1.0D);
                double radius = 0.07D + progress * 0.46D;
                for (int i = 0; i < 2; i++) {
                    double theta = phase * 0.14D + i * Math.PI;
                    double y = Math.sin(theta * 1.55D) * radius * 0.68D;
                    double planar = Math.sqrt(Math.max(0.0D, radius * radius - y * y));
                    Vec3 shell = core.add(right.scale(Math.cos(theta) * planar))
                            .add(horizontal.scale(Math.sin(theta) * planar)).add(0.0D, y, 0.0D);
                    level.sendParticles(LWKiTrainingParticles.FOCUS.get(), shell.x, shell.y, shell.z, 0,
                            0.48D, 0.84D, 1.0D, 1.0D);
                }
            }
            if (phase % 5L == 0L) {
                double angle = phase * 0.41D;
                for (int sign : new int[]{-1, 1}) {
                    Vec3 start = fighter.position().add(0.0D, fighter.getBbHeight() * (0.38D + 0.16D * ((phase / 5L) & 1L)), 0.0D)
                            .add(right.scale(sign * (0.75D + 0.18D * Math.cos(angle))))
                            .add(horizontal.scale(-0.05D + 0.12D * Math.sin(angle)));
                    spawnInwardKiTrail(level, start, core, 5);
                }
            }
            return;
        }

        // Harmless release drill: expanding anime flare plus a short luminous control lane.
        if (phase == 95L) {
            for (int i = 0; i < 5; i++)
                level.sendParticles(LWKiTrainingParticles.PULSE.get(), core.x, core.y, core.z, 0, 0.48D, 0.84D, 1.0D, 1.0D);
            for (double distance = 0.45D; distance <= 3.2D; distance += 0.48D) {
                Vec3 point = core.add(horizontal.scale(distance));
                level.sendParticles(LWKiTrainingParticles.FOCUS.get(), point.x, point.y, point.z, 0, 0.38D, 0.74D, 1.0D, 1.0D);
            }
            Vec3 tip = core.add(horizontal.scale(3.35D));
            level.sendParticles(LWKiTrainingParticles.PULSE.get(), tip.x, tip.y, tip.z, 0, 0.60D, 0.90D, 1.0D, 1.0D);
        } else if (phase < 112L && (phase & 1L) == 0L) {
            double distance = 0.9D + (phase - 95L) * 0.15D;
            Vec3 point = core.add(horizontal.scale(distance));
            level.sendParticles(LWKiTrainingParticles.FOCUS.get(), point.x, point.y, point.z, 0, 0.34D, 0.72D, 1.0D, 1.0D);
        }
    }

    private static void maybeSpeak(AmbientFighterEntity fighter, Type type, boolean opening) {
        if (!fighter.getSpeech().isEmpty() || (!opening && fighter.getRandom().nextFloat() > 0.30F)) return;
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        boolean strong = ReactiveWorldManager.moodStrength(fighter) >= 58;
        String line = null;
        if (strong) {
            line = switch (mood) {
                case UPBEAT -> switch (type) {
                    case DANCING -> pick(fighter, "Okay, this is actually fun.", "I needed a good moment like this.", "Yeah, I needed this.", "I'm keeping this mood while it lasts.");
                    case EATING -> "Good food, good mood. Hard to argue with that.";
                    case RELAXED_FLIGHT -> "The air feels great today.";
                    default -> null;
                };
                case FOCUSED -> switch (type) {
                    case SCOUTING -> pick(fighter, "I'm keeping my attention on the area. No distractions.", "Eyes up. I'm checking every angle.", "I want a clean read on this place.");
                    case REST -> pick(fighter, "Just a short reset. Then I'm back to it.", "A minute down, then I keep moving.", "Rest is part of the training too.");
                    default -> null;
                };
                case WARY -> switch (type) {
                    case SCOUTING -> pick(fighter, "I'm checking twice. Something still feels off.", "I don't like the quiet around here.", "I'm not convinced we're alone.");
                    case REST -> pick(fighter, "I'm resting, not dropping my guard.", "Taking a break doesn't mean I'm not watching.", "I'll rest. My eyes stay open.");
                    default -> null;
                };
                case IRRITATED -> switch (type) {
                    case SCOUTING -> pick(fighter, "I need a little space. I'm clearing my head.", "I'm walking this off before I say something stupid.", "Just let me check the perimeter alone.");
                    case REST -> pick(fighter, "I'm staying here until I cool off.", "Give me a minute before I get moving again.", "I'm stopping here before this mood gets worse.");
                    default -> null;
                };
                case SOMBER -> switch (type) {
                    case STARGAZING -> pick(fighter, "Quiet helps right now.", "The sky doesn't ask questions.", "I can think better looking up there.");
                    case REST -> pick(fighter, "I don't feel like doing much. Just give me a minute.", "I'm not ready to move yet.", "I need the world to be quiet for a bit.");
                    case FISHING -> pick(fighter, "This is easier than talking right now.", "The water's good company today.", "I'd rather listen to the river for a while.");
                    default -> null;
                };
                case WEARY -> switch (type) {
                    case REST -> pick(fighter, "I really needed to stop for a while.", "My body was asking for this break.", "I'm running on fumes. This helps.");
                    case EATING -> pick(fighter, "Maybe some food will put me back together.", "I need fuel more than motivation right now.", "Food first. Everything else after.");
                    default -> null;
                };
                case CONTENT -> null;
            };
        }
        if (line == null) line = switch (type) {
            case FISHING -> pick(fighter, "Quiet water, quiet mind.", "If this fish can sense Ki, I'm in trouble.", "I could stay here a while.", "No rush. That's the best part.", "I'm trying not to scare everything in the water with my Ki.", "This is a surprisingly good way to reset.", "Maybe patience counts as training.", "I wonder if fish can tell when someone's staring at the float.");
            case REST -> pick(fighter, "A short break won't hurt.", "Even fighters are allowed to slow down sometimes.", "Nice to have five minutes without an explosion.", "I'm letting the day slow down for a minute.", "I can train harder after I actually recover.", "No reason to burn myself out.", "I'm not quitting. I'm resting.", "A quiet minute can do more than another hundred punches.");
            case NAP -> pick(fighter, "I'm closing my eyes for a bit.", "Wake me when something explodes.", "A short nap should put me back together.", "I'm more tired than I thought.", "Just twenty minutes. Probably.");
            case SITTING -> pick(fighter, "Sometimes I just want to sit here.", "No training plan. No mission. Just sitting.", "The ground is surprisingly comfortable.", "I could get used to a quiet minute like this.", "I'm staying put for a little while.", "Nothing wrong with doing absolutely nothing for a minute.", "Good spot to let the muscles settle.", "I didn't realize how much I needed to sit down.");
            case JOGGING -> pick(fighter, "Just loosening up.", "A light run clears my head.", "Not every workout needs to shake the planet.", "Keeping the legs moving.", "Easy pace. I'm building the engine.", "Footwork starts before the fight does.", "A few more laps and I'll call it.", "Keeping my breathing steady.");
            case TRAINING -> pick(fighter, "One more set.", "If I stop improving, somebody else won't.", "I'm working on the basics until they stop feeling basic.", "Power means nothing if I can't control it.", "Again. Cleaner this time.", "Hands back to guard after every strike.", "Speed comes after the motion is right.", "I can feel where the last punch went wrong.", "No wasted movement. That's the goal.", "Again. Full extension, clean recovery.");
            case STRENGTH_TRAINING -> pick(fighter, "Keep the body straight. One more rep.", "Chest down. Drive back up.", "No rushing the bottom of the rep.", "One more set before I stop.", "Core tight. Keep the form clean.");
            case KI_TRAINING -> pick(fighter, "Hold it steady. Don't waste the energy.", "More control, less flare.", "Build it up. Let it settle. Again.", "Ki gets sloppy when you rush it.", "I'm trying to make every bit of energy count.");
            case WALKING -> pick(fighter, "A walk clears my head.", "No rush. Just moving for a bit.", "I needed to get out and stretch my legs.", "Sometimes it's better to move without training for something.");
            case SCIENTIST_RESEARCH -> pick(fighter, "A better formula means a better specimen.", "I need cleaner data from the last deployment.", "Cultivation variables first. Combat testing later.", "Small changes make dangerous differences.",
                    "Ki density is up, but so is metabolic drift. That's not a free improvement.", "Control group stable. Combat batch... less stable.",
                    "I need to separate inherited power from cultivation gain or the whole dataset is garbage.", "Reaction latency fell three ticks. Finally, a variable moving in the right direction.",
                    "If the survival curve collapses again, I'm lowering the aggression stimulus instead of the power target.");
            case STUDYING -> pick(fighter, "I keep notes so I don't repeat the same mistakes.", "There's always something I missed the first time.", "Technique is easier to fix when you actually think about it.", "I'm reviewing what worked and what didn't.");
            case FLOWER -> pick(fighter, "That one stands out.", "Not every interesting thing has a power level.", "Good spot for a flower.", "Funny what you notice when you stop rushing.", "That color really catches the eye.", "I almost missed this one.");
            case FOOD_GATHERING -> pick(fighter, "I should find something real to eat.", "Food first. Then I can get back to the day.", "I saw tracks around here. Might as well look.", "Better to hunt nearby than burn through supplies.");
            case TREE -> pick(fighter, "An apple break sounded good.", "Training fuel.", "Hard to beat something simple.", "Found a decent spot for a snack.", "A few strikes and lunch sorts itself out.", "Fresh apple beats carrying rations.", "This tree picked the wrong day to look useful.", "I earned this snack.");
            case STARGAZING -> pick(fighter, "You forget how big the sky is.", "That's a good view.", "Imagine trying to count all of those. No thanks.", "Somewhere up there, somebody is probably training too hard.", "Makes this whole planet feel small.", "I wonder how many worlds are looking back.", "Hard to think about power levels under a sky like that.", "I could stay out here until sunrise.");
            case EATING -> pick(fighter, "I needed that.", "Training on an empty stomach is a terrible idea.", "Finally, something that isn't a Senzu Bean.", "That hit the spot.", "I was getting way too hungry to focus.", "Food tastes better after training.");
            case SCOUTING -> pick(fighter, "Nothing strange so far.", "Good view from here.", "I'm checking the area. And definitely not being nosy.", "I'm learning the terrain while it's quiet.", "Better to know the exits before you need them.", "No trouble in sight. That's usually when I get suspicious.");
            case RELAXED_FLIGHT -> pick(fighter, "Sometimes flying is the whole point.", "No destination. That's nice for once.", "Walking feels optional when you can do this.", "The view's better when you're not in a hurry.", "I forgot how good the air feels up here.", "No mission. Just a little sky.");
            case DANCING -> pick(fighter, "Don't judge me. The rhythm won.", "A little music would improve this.", "Sometimes you just move.", "This counts as footwork practice if anyone asks.", "I refuse to explain myself.", "Okay, one more round and I'm done.");
        };
        fighter.speak(line, 70);
    }

    private static String pick(AmbientFighterEntity fighter, String... values) { return values[fighter.getRandom().nextInt(values.length)]; }

    /** Immediate lifecycle cleanup used by death/archive handling. */
    public static void cancelFor(AmbientFighterEntity fighter) {
        if (fighter != null && SESSIONS.containsKey(fighter.getUUID())) finish(fighter);
    }

    private static void finishIfPresent(AmbientFighterEntity fighter) { if (fighter != null && SESSIONS.containsKey(fighter.getUUID())) finish(fighter); }

    private static void finish(AmbientFighterEntity fighter) {
        Session session = SESSIONS.remove(fighter.getUUID());
        if (session != null) {
            removeFishingHook(fighter);
            if (session.actualStarted) {
                fighter.getPersistentData().putString(LAST_ACTIVITY, session.type.name());
                fighter.getPersistentData().putLong(LAST_ACTIVITY_AT, fighter.level().getGameTime());
            }
            if (session.type == Type.RELAXED_FLIGHT) {
                fighter.setFlyingFast(false);
                // Activity ownership ends here. Never leave the native DMZ flying flag latched on
                // with the final route velocity after a mood/activity transition.
                fighter.setDeltaMovement(fighter.getDeltaMovement().scale(0.28D));
                fighter.setFlying(false);
                fighter.setNoGravity(false);
            }
            if (session.actualStarted && session.type == Type.JOGGING && fighter.getTarget() == null) {
                fighter.setAggressive(false);
                int effort = (int)Math.min(Integer.MAX_VALUE, Math.max(0L, fighter.level().getGameTime() - session.started));
                if (effort >= 180) FighterBattleGrowthManager.onJogging(fighter, effort);
                else FighterBattleGrowthManager.clearProgressiveAdvance(fighter, FighterBattleGrowthManager.Source.JOGGING);
            }
            if (session.type == Type.KI_TRAINING) fighter.setKiCharge(false);
            if (session.actualStarted && session.type == Type.SCIENTIST_RESEARCH && session.settledAt > 0L
                    && fighter.level().getGameTime() - session.settledAt >= 100L)
                FighterScientistManager.completeResearchSession(fighter);
            if (session.actualStarted && (session.type == Type.TRAINING || session.type == Type.STRENGTH_TRAINING || session.type == Type.KI_TRAINING)) {
                // Reward only time actually spent settled and working. Interrupted sessions keep
                // proportional credit; approach time and instant start/cancel attempts earn nothing.
                int effort = session.settledAt <= 0L ? 0 : (int)Math.min(Integer.MAX_VALUE,
                        Math.max(0L, fighter.level().getGameTime() - session.settledAt));
                if (effort >= 120) {
                    fighter.applyTrainingGrowth(effort, false);
                    FighterPassiveSkillManager.onPracticeCompleted(fighter, session.type, effort);
                } else FighterBattleGrowthManager.clearProgressiveAdvance(fighter, FighterBattleGrowthManager.Source.TRAINING);
            }
            if (session.actualStarted && session.type == Type.STUDYING && session.settledAt > 0L) {
                int studyEffort = (int)Math.min(Integer.MAX_VALUE, Math.max(0L, fighter.level().getGameTime() - session.settledAt));
                FighterPassiveSkillManager.onStudyCompleted(fighter, studyEffort);
            }
            long livedTicks = session.actualStarted
                    ? Math.max(0L, fighter.level().getGameTime() - session.actualStartedAt) : 0L;
            if (session.actualStarted) {
                if (fighter.getTarget() == null && fighter.isAlive() && !fighter.isDefeated())
                    ReactiveWorldManager.activityCompleted(fighter, session.type);
                FighterLifeNeedsManager.onActivityCompleted(fighter, session.type,
                        (int)Math.min(Integer.MAX_VALUE, livedTicks));
                FighterLivelinessManager.onActivityFinished(fighter, session.type, session.stand, livedTicks);
            }
            // A short interlude punctuates the day's main activity; it should not consume the
            // scheduler's normal 30-70 second wake gap before the planned primary activity resumes.
            if (isShortLifeBeat(session.type) && FighterDailyRoutineManager.mayStartPlannedActivity(fighter)) {
                fighter.getPersistentData().putLong(NEXT_ACTIVITY,
                        fighter.level().getGameTime() + 20L + fighter.getRandom().nextInt(21));
            }
        }
        if (session != null && session.actualStarted && session.type == Type.EATING) fighter.getPersistentData().putLong(LAST_MEAL_AT, fighter.level().getGameTime());
        if (session != null && session.type == Type.TREE && !session.treeAppleReady && session.treeAppleEntityId != null
                && fighter.level() instanceof ServerLevel treeLevel
                && treeLevel.getEntity(session.treeAppleEntityId) instanceof ItemEntity apple
                && apple.getPersistentData().hasUUID("LWTreeActivityOwner")
                && fighter.getUUID().equals(apple.getPersistentData().getUUID("LWTreeActivityOwner"))) {
            apple.discard(); // activity-owned visual drop must not accumulate if the scene is interrupted
        }
        if (session != null && session.type == Type.FOOD_GATHERING && !session.foodConsumed && !session.foodCarried.isEmpty()) {
            ItemStack held = fighter.getMainHandItem();
            ItemStack dropStack = !held.isEmpty() ? held.copy() : session.foodCarried.copy();
            if (!dropStack.isEmpty() && fighter.level() instanceof ServerLevel level) {
                ItemEntity returned = new ItemEntity(level, fighter.getX(), fighter.getY() + 0.35D, fighter.getZ(), dropStack);
                returned.setDefaultPickUpDelay();
                level.addFreshEntity(returned);
            }
            fighter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            session.foodCarried = ItemStack.EMPTY;
        }
        fighter.getNavigation().stop();
        fighter.stopUsingItem();
        fighter.setPose(Pose.STANDING);
        fighter.setAmbientPose(0);
        fighter.setFishingActivity(false);
        fighter.setXRot(0.0F);
        fighter.xRotO = 0.0F;
        fighter.setAmbientFlightActivity(false);
        fighter.setSprinting(false);
        if (fighter.getTarget() == null && !fighter.isFlying()) fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.WALK);
        clearTemporaryItem(fighter);
        restoreMainHand(fighter);
        fighter.setSocialLifeActivity(false);
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    private static void cleanupOrphanedItem(AmbientFighterEntity fighter) {
        if ((fighter.getPersistentData().getBoolean(TEMP_ITEM) || fighter.getPersistentData().contains(STOWED_MAIN_HAND)) && !SESSIONS.containsKey(fighter.getUUID())) {
            fighter.stopUsingItem();
            clearTemporaryItem(fighter);
            restoreMainHand(fighter);
            fighter.setPose(Pose.STANDING);
            fighter.setAmbientPose(0);
            fighter.setFishingActivity(false);
            fighter.setXRot(0.0F);
            fighter.xRotO = 0.0F;
            fighter.setAmbientFlightActivity(false);
            if (fighter.isSocialLifeActivity()) fighter.setSocialLifeActivity(false);
        }
    }

    /** Strong mood/event changes can make the next ordinary-life choice happen soon enough to be visible. */
    public static void nudgeSoon(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || SESSIONS.containsKey(fighter.getUUID())) return;
        long now = fighter.level().getGameTime();
        long current = fighter.getPersistentData().getLong(NEXT_ACTIVITY);
        long soon = now + 55L + fighter.getRandom().nextInt(86);
        if (current <= 0L || current > soon) fighter.getPersistentData().putLong(NEXT_ACTIVITY, soon);
    }

    public static boolean isActive(AmbientFighterEntity fighter) {
        return fighter != null && SESSIONS.containsKey(fighter.getUUID());
    }

    /** Clears a temporary prop left behind if an entity unloaded while its activity session vanished. */
    public static void recoverTransientState(AmbientFighterEntity fighter) {
        if (fighter != null) cleanupOrphanedItem(fighter);
    }

    public static void cancel(AmbientFighterEntity fighter) {
        if (fighter != null && SESSIONS.containsKey(fighter.getUUID())) finish(fighter);
    }

    public static int runtimeEntries() { return SESSIONS.size(); }

    public static void clearRuntime(MinecraftServer server) {
        if (server != null) {
            for (Session session : List.copyOf(SESSIONS.values())) {
                ServerLevel level = server.getLevel(session.dimension);
                if (level != null && level.getEntity(session.fighterId) instanceof AmbientFighterEntity fighter) finish(fighter);
            }
        }
        SESSIONS.clear();
    }

    public static void clearRuntime() { SESSIONS.clear(); }
}
