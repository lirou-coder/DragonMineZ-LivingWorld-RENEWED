package com.kunyo.dbzmeditation;

import com.mojang.logging.LogUtils;
import com.dragonminez.server.util.GravityLogic;
import com.dragonminez.server.util.GravityStateSync;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.joml.Vector3f;

public final class DBZMeditation {
    public static final String MODID = "dbzmeditation";
    /** Forge owner mod id; assets/legacy NBT retain the historic dbzmeditation namespace. */
    public static final String OWNER_MODID = "dmzlivingworld";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String SEAT_NAME = "DBZMeditationSeat";

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MODID);

    public static final RegistryObject<SimpleParticleType> MEDITATION_GLYPH =
        PARTICLE_TYPES.register(
            "meditation_glyph",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> KI_MOTE =
        PARTICLE_TYPES.register(
            "ki_mote",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> KI_ABSORB =
        PARTICLE_TYPES.register(
            "ki_absorb",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> TRANSCENDENT_CORE =
        PARTICLE_TYPES.register(
            "transcendent_core",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> KI_WISP =
        PARTICLE_TYPES.register(
            "ki_wisp",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> GROUND_RUNE =
        PARTICLE_TYPES.register(
            "ground_rune",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> KI_BURST =
        PARTICLE_TYPES.register(
            "ki_burst",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> BREAKTHROUGH_CORE =
        PARTICLE_TYPES.register(
            "breakthrough_core",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> BREAKTHROUGH_SIGIL =
        PARTICLE_TYPES.register(
            "breakthrough_sigil",
            () -> new SimpleParticleType(false)
        );

    public static final RegistryObject<SimpleParticleType> BREAKTHROUGH_SHARD =
        PARTICLE_TYPES.register(
            "breakthrough_shard",
            () -> new SimpleParticleType(false)
        );

    private static final String ACTIVE = "dbzm_active";
    private static final String TICKS = "dbzm_ticks";
    private static final String TOTAL = "dbzm_total";
    private static final String REWARD_TIMER = "dbzm_reward_timer";
    private static final String LAST_STAGE = "dbzm_last_stage";
    private static final String TP_CARRY = "dbzm_tp_carry";
    private static final int[] TP_BRIDGE_DENOMINATIONS = {
        65536, 32768, 16384, 8192, 4096, 2048, 1024, 512,
        256, 128, 64, 32, 16, 8, 4, 2, 1
    };
    private static final String COOLDOWN = "dbzm_cooldown";
    private static final int MAX_SAFE_TP_AWARD = 100000;

    private static final String SESSION_PREVIOUS_LONGEST = "dbzm_session_previous_longest";
    private static final String SESSION_STAT_STR = "dbzm_session_stat_str";
    private static final String SESSION_STAT_SKP = "dbzm_session_stat_skp";
    private static final String SESSION_STAT_RES = "dbzm_session_stat_res";
    private static final String SESSION_STAT_VIT = "dbzm_session_stat_vit";
    private static final String SESSION_STAT_PWR = "dbzm_session_stat_pwr";
    private static final String SESSION_STAT_ENE = "dbzm_session_stat_ene";
    private static final String DEBUG_VIEW = "dbzm_debug_view";

    private static final String SESSION_MASTERY_GAIN = "dbzm_session_mastery_gain";
    private static final String SESSION_MASTERY_FORM = "dbzm_session_mastery_form";
    private static final String SESSION_KI_RECOVERED = "dbzm_session_ki_recovered";
    private static final String SESSION_STAMINA_RECOVERED = "dbzm_session_stamina_recovered";


    private static final String SEAT_UUID = "dbzm_seat_uuid";
    private static final String BASE_X = "dbzm_base_x";
    private static final String BASE_Y = "dbzm_base_y";
    private static final String BASE_Z = "dbzm_base_z";

    private static final String LIFETIME_TICKS = "dbzm_lifetime_ticks";
    private static final String LONGEST_TICKS = "dbzm_longest_ticks";
    private static final String LIFETIME_TP = "dbzm_lifetime_tp";
    private static final String SESSIONS = "dbzm_sessions";
    private static final String INTERRUPTIONS = "dbzm_interruptions";
    private static final String TRANSCENDENT_COUNT = "dbzm_transcendent_count";
    private static final String STAT_BREAKTHROUGHS = "dbzm_stat_breakthroughs";


    private static final double SEAT_Y_OFFSET = -1.18D;
    private static final double ENTRY_SETTLE_HEIGHT = 0.105D;

    /*
     * 2.8 visual anchor:
     * 2.7 particle rings were visibly below the DMZ meditation pose.
     * Every meditation ki effect now uses one central body lift so the
     * entire system can be tuned in one place.
     */
    private static final double KI_VISUAL_Y_LIFT = 0.82D;

    /** Initializes the former standalone meditation module inside Living World. */
    public static void init(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.SERVER, MeditationConfig.SERVER_SPEC, "dmzlivingworld-meditation-server.toml"
        );
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.CLIENT, MeditationConfig.CLIENT_SPEC, "dmzlivingworld-meditation-client.toml"
        );
        MeditationNetwork.register();
        MinecraftForge.EVENT_BUS.register(new DBZMeditation());
        PARTICLE_TYPES.register(modEventBus);
    }

    private DBZMeditation() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("meditate")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (isMeditating(player)) {
                        stopMeditation(player, "Meditation ended.", false, false);
                    } else {
                        startMeditation(player);
                    }
                    return 1;
                })
                .then(Commands.literal("start").executes(ctx -> {
                    startMeditation(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    stopMeditation(ctx.getSource().getPlayerOrException(), "Meditation ended.", false, false);
                    return 1;
                }))
                .then(Commands.literal("interrupt").executes(ctx -> {
                    stopMeditation(ctx.getSource().getPlayerOrException(), "Meditation interrupted.", true, false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    sendStatus(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("stats").executes(ctx -> {
                    sendStats(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("debug")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        CompoundTag data = player.getPersistentData();
                        boolean enabled = !data.getBoolean(DEBUG_VIEW);
                        data.putBoolean(DEBUG_VIEW, enabled);
                        player.sendSystemMessage(
                            Component.literal(
                                "Meditation debug overlay: " + (enabled ? "ON" : "OFF")
                            ).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        );
                        sendDebug(player);
                        syncMeditationState(player);
                        return 1;
                    })
                    .then(Commands.literal("fast").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        MeditationConfig.setFastTesting(true);
                        player.sendSystemMessage(
                            Component.literal(
                                "Fast meditation testing enabled: 0:20 / 0:45 / 1:15 / 2:00."
                            ).withStyle(ChatFormatting.YELLOW)
                        );
                        syncMeditationState(player);
                        return 1;
                    }))
                    .then(Commands.literal("normal").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        MeditationConfig.setFastTesting(false);
                        player.sendSystemMessage(
                            Component.literal(
                                "Fast meditation testing disabled. Normal configured timings restored."
                            ).withStyle(ChatFormatting.GREEN)
                        );
                        syncMeditationState(player);
                        return 1;
                    }))
                )
                .then(Commands.literal("info").executes(ctx -> {
                    sendInfo(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
        );
    }

    static boolean isMeditating(ServerPlayer player) {
        return player.getPersistentData().getBoolean(ACTIVE);
    }

    /** Public entry used only by the narrow MeditationIntegrationApi. */
    static void startMeditationForIntegration(ServerPlayer player) {
        startMeditation(player);
    }

    private static void startMeditation(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();

        if (!MeditationConfig.SERVER.enabled.get()) {
            player.sendSystemMessage(Component.literal("Meditation is disabled in Living World settings.").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (isMeditating(player)) {
            player.sendSystemMessage(Component.literal("You are already meditating."));
            return;
        }

        int cooldown = data.getInt(COOLDOWN);
        if (cooldown > 0) {
            int seconds = (cooldown + 19) / 20;
            player.sendSystemMessage(
                Component.literal("Your mind is still unsettled. Try again in " + seconds + "s.")
                    .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (player.isPassenger()) {
            player.sendSystemMessage(Component.literal("Dismount before meditating."));
            return;
        }

        removeSeat(player);

        data.putBoolean(ACTIVE, true);
        MeditationIntegrationApi.clearExternalMeditationPartners(player);
        data.putInt(TICKS, 0);
        data.putInt(TOTAL, 0);
        data.putInt(REWARD_TIMER, 0);
        data.putInt(LAST_STAGE, 0);
        data.putInt(TP_CARRY, 0);
        data.putInt(SESSION_PREVIOUS_LONGEST, data.getInt(LONGEST_TICKS));
        data.putDouble(SESSION_MASTERY_GAIN, 0.0D);
        data.putString(SESSION_MASTERY_FORM, "");
        data.putDouble(SESSION_KI_RECOVERED, 0.0D);
        data.putDouble(SESSION_STAMINA_RECOVERED, 0.0D);
        resetSessionStatGains(data);
        data.putDouble(BASE_X, player.getX());
        data.putDouble(BASE_Y, player.getY());
        data.putDouble(BASE_Z, player.getZ());
        data.putInt(SESSIONS, data.getInt(SESSIONS) + 1);

        ServerLevel level = player.serverLevel();

        ArmorStand seat = new ArmorStand(
            level,
            player.getX(),
            player.getY() + SEAT_Y_OFFSET + ENTRY_SETTLE_HEIGHT,
            player.getZ()
        );
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.setCustomName(Component.literal(SEAT_NAME));
        seat.setCustomNameVisible(false);
        seat.setYRot(player.getYRot());
        seat.setYBodyRot(player.getYRot());

        level.addFreshEntity(seat);

        if (!player.startRiding(seat, true)) {
            seat.discard();
            data.putBoolean(ACTIVE, false);
            player.sendSystemMessage(Component.literal("Could not begin meditation here."));
            return;
        }

        data.putUUID(SEAT_UUID, seat.getUUID());

        if (MeditationConfig.SERVER.particles.get()) {
            float[] rgb =
                DMZKiColorBridge.getEffectRgb(player);

            spawnCustomKiCluster(
                level,
                player,
                KI_BURST.get(),
                rgb,
                5,
                0.24D,
                0.18D,
                0.44D
            );
            spawnCustomKiRing(
                level,
                player,
                KI_MOTE.get(),
                rgb,
                0.50D,
                10,
                0.44D
            );
        }

        if (MeditationConfig.SERVER.milestoneSounds.get()) {
            level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.35F,
                0.75F
            );
        }

        syncMeditationState(player);
    }

    private static void stopMeditation(
        ServerPlayer player,
        String reason,
        boolean interrupted,
        boolean damageCooldown
    ) {
        if (!isMeditating(player)) {
            return;
        }

        CompoundTag data = player.getPersistentData();

        int total = data.getInt(TOTAL);
        int ticks = data.getInt(TICKS);
        String stage = getStageName(ticks);
        String statGains = formatSessionStatGains(data);
        String masteryForm = data.getString(SESSION_MASTERY_FORM);
        double masteryGain = data.getDouble(SESSION_MASTERY_GAIN);

        int previousLongest =
            data.getInt(SESSION_PREVIOUS_LONGEST);

        boolean newRecord =
            ticks > previousLongest;

        data.putBoolean(ACTIVE, false);
        MeditationIntegrationApi.clearExternalMeditationPartners(player);

        if (interrupted) {
            data.putInt(
                INTERRUPTIONS,
                data.getInt(INTERRUPTIONS) + 1
            );
        }

        if (damageCooldown) {
            data.putInt(
                COOLDOWN,
                MeditationConfig.SERVER
                    .damageCooldownSeconds
                    .get()
                    * 20
            );
        }

        if (ticks > data.getInt(LONGEST_TICKS)) {
            data.putInt(LONGEST_TICKS, ticks);
        }

        /*
         * The summary is deliberately client UI, not chat.
         * Very short accidental taps don't create a summary panel.
         */
        if (ticks >= 100) {
            MeditationNetwork.sendSummary(
                player,
                ticks,
                total,
                stage,
                statGains,
                masteryForm,
                masteryGain,
                newRecord,
                interrupted
            );
        }

        spawnMeditationRelease(player, interrupted);

        if (player.isPassenger()
            && isMeditationSeat(player.getVehicle())) {
            player.stopRiding();
        }

        removeSeat(player);
        clearRewardTags(player);

        DMZTrainingBridge.FormProgress finalForm =
            DMZTrainingBridge.getFormProgress(player);
        float[] finalResources =
            DMZTrainingBridge.getResourcePercents(player);

        MeditationNetwork.sendState(
            player,
            false,
            ticks,
            total,
            0,
            statGains,
            getStageIndex(ticks),
            getMultiplier(ticks),
            getStageProgress(ticks),
            MeditationConfig.isFastTesting(),
            data.getBoolean(DEBUG_VIEW),
            finalResources[0],
            finalResources[1],
            finalForm.active() ? prettyFormName(finalForm.form()) : "",
            finalForm.mastery(),
            finalForm.maxMastery(),
            data.getDouble(SESSION_MASTERY_GAIN)
        );
    }

    private static void silentCleanup(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TICKS) > data.getInt(LONGEST_TICKS)) {
            data.putInt(LONGEST_TICKS, data.getInt(TICKS));
        }
        data.putBoolean(ACTIVE, false);

        if (player.isPassenger() && isMeditationSeat(player.getVehicle())) {
            player.stopRiding();
        }
        removeSeat(player);
        clearRewardTags(player);
    }

    private static void removeSeat(
        ServerPlayer player
    ) {
        CompoundTag data =
            player.getPersistentData();

        if (!data.hasUUID(SEAT_UUID)) {
            return;
        }

        UUID uuid =
            data.getUUID(SEAT_UUID);

        Entity entity =
            player.serverLevel()
                .getEntity(uuid);

        if (entity == null
            && player.getServer() != null) {

            /*
             * Search the small set of loaded server levels before giving up so
             * abnormal dimension changes, disconnects or shutdown paths do not
             * leave an invisible meditation seat behind.
             */
            for (ServerLevel level :
                player.getServer()
                    .getAllLevels()) {

                entity =
                    level.getEntity(uuid);

                if (entity != null) {
                    break;
                }
            }
        }

        if (entity != null
            && isMeditationSeat(entity)) {

            entity.discard();
        }

        data.remove(SEAT_UUID);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        CompoundTag data = player.getPersistentData();

        int cooldown = data.getInt(COOLDOWN);
        if (cooldown > 0) {
            data.putInt(COOLDOWN, cooldown - 1);
        }

        if (!isMeditating(player)) return;

        // Damage interruption is handled by LivingHurtEvent. Turning that option off must
        // never stop the meditation clock/rewards themselves.

        Entity vehicle = player.getVehicle();

        if (!isMeditationSeat(vehicle)) {
            stopMeditation(
                player,
                "Meditation interrupted.",
                true,
                false
            );
            return;
        }

        int ticks = data.getInt(TICKS) + 1;
        data.putInt(TICKS, ticks);

        int lifetime = data.getInt(LIFETIME_TICKS) + 1;
        data.putInt(LIFETIME_TICKS, lifetime);

        if (ticks > data.getInt(LONGEST_TICKS)) {
            data.putInt(LONGEST_TICKS, ticks);
        }

        if (ticks % 20 == 0) syncMeditationState(player);

        int stage = getStageIndex(ticks);
        int previousStage = data.getInt(LAST_STAGE);

        if (stage > previousStage) {
            for (int reached = previousStage + 1; reached <= stage; reached++) {
                onStageReached(player, reached);
            }
            data.putInt(LAST_STAGE, stage);
        }

        int multiplier = getMultiplier(ticks);

        if (ticks % 200 == 0) {
            com.dmzlivingworld.world.DMZSkillProgressionCompat.onMeditationPulse(player, multiplier);
        }

        // Native DMZ recovery and form-control training are intentionally
        // processed once per second. They augment DMZ's own systems rather
        // than replacing them, and never touch health.
        if (ticks % 20 == 0) {
            applyNativeMeditationTraining(player, stage);
            double trainingGravity = 0.0D;
            try { trainingGravity = GravityLogic.getTrainingBonusGravity(player); } catch (Throwable ignored) {}
            DMZGravityGrowthBridge.pulse(player, trainingGravity, ticks);
        }

        animateSeat(player, vehicle, ticks);

        int rewardTimer = data.getInt(REWARD_TIMER) + 1;
        int rewardInterval = MeditationConfig.SERVER.rewardIntervalSeconds.get() * 20;

        if (rewardTimer >= rewardInterval) {
            rewardTimer = 0;
            awardMeditationTp(player, multiplier);
        }
        data.putInt(REWARD_TIMER, rewardTimer);

        /*
         * Rare meditative breakthrough.
         *
         * One roll per cumulative meditation interval (default 60 seconds),
         * independent of session boundaries. The default 1% chance therefore
         * averages one base-stat breakthrough per ~100 meditation minutes,
         * rather than accidentally rolling every 5-second TP payout.
         */
        int statRollTicks =
            MeditationConfig.SERVER
                .statBreakthroughRollSeconds
                .get() * 20;

        if (MeditationConfig.SERVER.statBreakthroughEnabled.get()
            && statRollTicks > 0
            && lifetime % statRollTicks == 0) {

            double chance =
                MeditationConfig.SERVER
                    .statBreakthroughChancePercent
                    .get() / 100.0D;

            if (chance > 0.0D
                && player.getRandom().nextDouble() < chance) {

                DMZStatBridge.StatGain gain = DMZStatBridge.tryGrantPercentRandomBaseStat(
                    player, MeditationConfig.SERVER.statBreakthroughPoints.get());

                if (gain != null) {
                    recordSessionStatGain(data, gain.stat(), gain.amount());

                    // A rare breakthrough is also a meaningful form-control insight when a form is active.
                    // Use DMZ's native mastery path/cap rather than writing mastery data directly.
                    DMZTrainingBridge.FormProgress breakthroughMastery =
                        DMZTrainingBridge.gainActiveFormMastery(
                            player,
                            Math.min(1.50D, 0.75D + gain.amount() * 0.01D)
                        );
                    if (breakthroughMastery.gained() > 0.0D) {
                        data.putDouble(
                            SESSION_MASTERY_GAIN,
                            data.getDouble(SESSION_MASTERY_GAIN) + breakthroughMastery.gained()
                        );
                        data.putString(
                            SESSION_MASTERY_FORM,
                            prettyFormName(breakthroughMastery.form())
                        );
                        DMZTrainingBridge.sync(player);
                    }

                    player.sendSystemMessage(
                        Component.literal(
                            "Meditative Breakthrough — +" + gain.amount() + " " + gain.stat()
                        ).withStyle(ChatFormatting.GOLD)
                    );

                    player.serverLevel().playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS,
                        0.30F,
                        1.35F
                    );
                }
            }
        }

        // One-hour lifetime advancement. Check only once per minute after the
        // threshold; grantAdvancement itself is idempotent and never runs a chat
        // command, so completed advancements cannot spam/error repeatedly.
        if (lifetime >= 72000 && lifetime % 1200 == 0) {
            grantAdvancement(player, "mind_without_limits");
        }
    }

    private static void applyNativeMeditationTraining(
        ServerPlayer player,
        int stage
    ) {
        /*
         * Release scope: Meditation no longer modifies Ki/Energy or Stamina.
         * Native DMZ training is limited to slow form-control mastery at
         * Deep/Transcendent.
         */
        if (!MeditationConfig.SERVER
                .formMasteryEnabled
                .get()
            || stage < 3) {

            return;
        }

        double perMinute =
            stage >= 4
                ? MeditationConfig.SERVER
                    .transcendentFormMasteryPerMinute
                    .get()
                : MeditationConfig.SERVER
                    .deepFormMasteryPerMinute
                    .get();

        // Preserve custom tuning, but migrate the exact pre-R26 defaults so existing worlds receive
        // the requested mastery buff without requiring players to delete their config file.
        if (stage >= 4 && Math.abs(perMinute - 0.040D) < 0.0000001D) perMinute = 0.100D;
        else if (stage == 3 && Math.abs(perMinute - 0.020D) < 0.0000001D) perMinute = 0.050D;
        if (perMinute <= 0.0D) {
            return;
        }

        DMZTrainingBridge.FormProgress progress =
            DMZTrainingBridge.gainActiveFormMastery(
                player,
                perMinute / 60.0D
            );

        if (progress.gained() <= 0.0D) {
            return;
        }

        CompoundTag data =
            player.getPersistentData();

        data.putDouble(
            SESSION_MASTERY_GAIN,
            data.getDouble(SESSION_MASTERY_GAIN)
                + progress.gained()
        );

        data.putString(
            SESSION_MASTERY_FORM,
            prettyFormName(progress.form())
        );

        DMZTrainingBridge.sync(player);
    }

    private static String prettyFormName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String normalized = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                result.append(c);
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return result.toString();
    }

    private static void onStageReached(ServerPlayer player, int stage) {
        CompoundTag data = player.getPersistentData();

        switch (stage) {
            case 1 -> {
                milestone(player, "Your breathing steadies. Focused state reached.", 0.90F);
                grantAdvancement(player, "clear_your_mind");
            }
            case 2 -> {
                milestone(player, "Body and mind move as one. Centered state reached.", 1.05F);
            }
            case 3 -> {
                milestone(player, "The outside world begins to fade. Deep Meditation reached.", 1.20F);
                grantAdvancement(player, "inner_peace");

            }
            case 4 -> {
                milestone(player, "Your ki becomes perfectly still. Transcendent Focus reached.", 1.40F);
                data.putInt(TRANSCENDENT_COUNT, data.getInt(TRANSCENDENT_COUNT) + 1);
                grantAdvancement(player, "still_as_stone");
            }
            default -> {
            }
        }
    }

    private static void sendActionBar(ServerPlayer player, int ticks, int multiplier) {
        int total = player.getPersistentData().getInt(TOTAL);
        double machineGravity = 0.0D;
        try { machineGravity = GravityLogic.getMachineGravity(player); } catch (Throwable ignored) {}
        String message = "Meditation  •  " + getStageName(ticks)
                + "  •  x" + multiplier
                + (machineGravity > 1.0001D ? "  •  Gravity x" + Math.round(machineGravity * 10.0D) / 10.0D : "")
                + "  •  " + formatTime(ticks)
                + "  •  " + total + " TP";
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.AQUA), true);
    }

    private static void animateSeat(ServerPlayer player, Entity seat, int ticks) {
        CompoundTag data = player.getPersistentData();

        double baseX = data.getDouble(BASE_X);
        double baseY = data.getDouble(BASE_Y);
        double baseZ = data.getDouble(BASE_Z);

        /*
         * Keep the entity that carries the player strictly monotonic. Tiny
         * reversing sine movements are quantized by entity tracking, so the
         * client receives them as irregular corrections and visibly jitters
         * both the rider and every rider-anchored effect. DragonMine Z's
         * model animation already supplies a smooth visual breathing motion.
         */
        double entryProgress = smoothstep(Math.min(1.0D, ticks / 32.0D));
        double settling =
            (1.0D - entryProgress) * ENTRY_SETTLE_HEIGHT;

        int deep = MeditationConfig.deepTicks();
        int transcendent = MeditationConfig.transcendentTicks();
        double levitationProgress =
            (ticks - deep) / (double)Math.max(1, transcendent - deep);
        double levitationBlend =
            smoothstep(Mth.clamp(levitationProgress, 0.0D, 1.0D));
        double machineGravity = 0.0D;
        double gravityReduction = 0.0D;
        try {
            machineGravity = GravityLogic.getMachineGravity(player);
            gravityReduction = GravityLogic.getStatReduction(player);
        } catch (Throwable ignored) {
            // DMZ owns gravity; meditation must remain safe if an older compatible DMZ build lacks a helper.
        }

        // The carrier is intentionally no-gravity for a stable seated pose, but it must not act as
        // a gravity-chamber exemption. Machine gravity suppresses the cosmetic levitation entirely
        // and visibly presses the seated pose lower in proportion to DMZ's real stat penalty.
        // DMZ's own player stat/gravity state remains authoritative and is re-synced below.
        double levitation =
            machineGravity > 1.0001D ? 0.0D :
            MeditationConfig.SERVER.levitationHeight.get() * levitationBlend;
        double gravityCompression = machineGravity > 1.0001D
                ? Math.min(0.16D, Math.max(0.0D, gravityReduction) * 0.16D)
                : 0.0D;

        seat.setPos(
            baseX,
            baseY
                + SEAT_Y_OFFSET
                + settling
                + levitation
                - gravityCompression,
            baseZ
        );
        seat.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (machineGravity > 1.0001D && ticks % 20 == 0) {
            try { GravityStateSync.sync(player); } catch (Throwable ignored) {}
        }
    }

    private static void spawnMeditationAura(
        ServerPlayer player,
        int ticks,
        int multiplier
    ) {
        if (!MeditationConfig.SERVER.particles.get()) {
            return;
        }

        ServerLevel level =
            player.serverLevel();

        int stage =
            getStageIndex(ticks);

        /*
         * RESTORED 2.7-STYLE ENTRY GATHERING.
         * The user explicitly preferred the old enchanting-table visual.
         * It contracts toward the meditating body during the first 3 seconds.
         */
        if (ticks <= 60 && ticks % 3 == 0) {
            double progress =
                ticks / 60.0D;

            double radius =
                1.18D
                    - 0.84D
                    * smoothstep(progress);

            spawnKiRing(
                level,
                player,
                ParticleTypes.ENCHANT,
                radius,
                12,
                0.48D + progress * 0.18D
            );
        }

        if (stage <= 0) {
            // Calm stays almost empty after the opening gather.
            if (ticks > 60 && ticks % 100 == 0) {
                level.sendParticles(
                    ParticleTypes.ENCHANT,
                    player.getX(),
                    kiY(player, 0.70D),
                    player.getZ(),
                    1,
                    0.10D, 0.12D, 0.10D,
                    0.0D
                );
            }
            return;
        }

        if (stage == 1) {
            // Focused: keep the enchanting identity, but do NOT flood the player.
            if (ticks % 56 == 0) {
                spawnEnchantingArc(
                    level,
                    player,
                    ticks,
                    3,
                    0.56D
                );
            }
            return;
        }

        if (stage == 2) {
            float progress =
                getStageProgress(ticks);

            /*
             * Centered remains quiet until the final fifth of the stage.
             * No ground ring spam and no smoke shell before Deep.
             */
            if (progress >= 0.80F
                && ticks % 8 == 0) {

                spawnEnchantingArc(
                    level,
                    player,
                    ticks,
                    2,
                    0.62D
                );
            }

            return;
        }

        if (stage == 3) {
            // Deep is now rendered client-side: a real model outline plus one
            // continuous inward ribbon. Do not cover the player in particles.
            return;
        }

        // Transcendent uses the same renderer with a steadier, slower cycle.
    }

    /**
     * Small violet/enchanting-table arc used in Calm/Focused/late Centered.
     * This deliberately restores the older visual language without making it
     * the dominant Deep-stage effect.
     */
    private static void spawnEnchantingArc(
        ServerLevel level,
        ServerPlayer player,
        int ticks,
        int points,
        double radius
    ) {
        for (int i = 0; i < points; i++) {
            double angle =
                ticks * 0.045D
                    + i * (Math.PI * 2.0D / points);

            level.sendParticles(
                ParticleTypes.ENCHANT,
                player.getX()
                    + Math.cos(angle) * radius,
                kiY(
                    player,
                    0.54D
                        + Math.sin(angle * 1.5D) * 0.12D
                ),
                player.getZ()
                    + Math.sin(angle) * radius,
                1,
                0.0D, 0.0D, 0.0D,
                0.0D
            );
        }
    }

    /**
     * Time-animated inward helix.
     *
     * Unlike the old spawnConvergingInflow(), this does NOT draw every point on
     * a path in one tick. Each arm owns a moving phase. As phase increases the
     * radius shrinks while the angle continues turning, creating an unmistakable
     * spiral that visibly terminates inside the upper torso.
     */
    private static void spawnContractingKiHelix(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        int ticks,
        int arms,
        double outerRadius,
        double outerHeight,
        double innerRadius
    ) {
        int period = 42;

        for (int arm = 0; arm < arms; arm++) {
            double phase =
                ((ticks + arm * (period / Math.max(1, arms))) % period)
                    / (double) period;

            double eased =
                smoothstep(phase);

            double radius =
                Mth.lerp(
                    eased,
                    outerRadius,
                    innerRadius
                );

            double angle =
                ticks * 0.13D
                    + arm * (Math.PI * 2.0D / arms)
                    + phase * Math.PI * 4.5D;

            double x =
                player.getX()
                    + Math.cos(angle) * radius;

            double z =
                player.getZ()
                    + Math.sin(angle) * radius;

            /*
             * Start above/lateral to the body and finish at chest level.
             * Raising KI_VISUAL_Y_LIFT in 3.4 fixes the previously-low Deep and
             * Transcendent visuals at the same time.
             */
            double y =
                kiY(
                    player,
                    Mth.lerp(
                        eased,
                        outerHeight,
                        0.72D
                    )
                    + Math.sin(angle * 0.7D)
                        * (1.0D - eased)
                        * 0.10D
                );

            level.sendParticles(
                particle,
                x,
                y,
                z,
                1,
                0.0D, 0.0D, 0.0D,
                0.0D
            );

            /*
             * Tiny absorption flash exactly as an arm reaches the torso.
             * This makes the inward destination legible instead of ambiguous.
             */
            if (phase > 0.92D) {
                level.sendParticles(
                    particle,
                    player.getX(),
                    kiY(player, 0.72D),
                    player.getZ(),
                    1,
                    0.035D, 0.035D, 0.035D,
                    0.0D
                );
            }
        }
    }

    private static DustParticleOptions dust(
        float[] rgb,
        float size
    ) {
        return new DustParticleOptions(
            new Vector3f(
                rgb[0],
                rgb[1],
                rgb[2]
            ),
            size
        );
    }

    private static double kiY(
        ServerPlayer player,
        double localOffset
    ) {
        return player.getY()
            + KI_VISUAL_Y_LIFT
            + localOffset;
    }

    /**
     * Stronger visual body contour for Deep/Transcendent stages.
     *
     * 2.9 was too subtle in practice. 3.0 deliberately overstates the silhouette
     * so the player can actually read it as an outline from third person.
     */
    private static void spawnKiSilhouette(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        double breath,
        boolean transcendent,
        float intensity
    ) {
        float clamped =
            Math.max(
                0.10F,
                Math.min(1.0F, intensity)
            );

        double pulse =
            transcendent
                ? 0.008D + 0.010D * breath * clamped
                : 0.016D + 0.024D * breath * clamped;

        int densityBoost =
            Math.round(8.0F * clamped);

        spawnKiRing(
            level,
            player,
            particle,
            0.37D + pulse,
            (transcendent ? 16 : 14) + densityBoost,
            0.10D
        );

        spawnKiRing(
            level,
            player,
            particle,
            0.27D + pulse,
            (transcendent ? 14 : 12) + densityBoost,
            0.46D
        );

        spawnKiRing(
            level,
            player,
            particle,
            0.33D + pulse,
            (transcendent ? 16 : 14) + densityBoost,
            0.82D
        );

        spawnKiRing(
            level,
            player,
            particle,
            0.22D + pulse * 0.6D,
            (transcendent ? 12 : 10) + densityBoost / 2,
            1.12D
        );

        spawnSideContour(
            level,
            player,
            particle,
            0.34D + pulse * 0.5D,
            0.11D,
            0.98D,
            8 + densityBoost / 2
        );
    }

    private static void spawnSideContour(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        double sideX,
        double startY,
        double endY,
        int steps
    ) {
        for (int sign : new int[] {-1, 1}) {
            for (int i = 0; i < steps; i++) {
                double t =
                    steps <= 1 ? 0.0D : i / (double)(steps - 1);

                double y =
                    Mth.lerp(t, startY, endY);

                double z =
                    Math.sin(t * Math.PI) * 0.16D;

                level.sendParticles(
                    particle,
                    player.getX() + sign * sideX,
                    kiY(player, y),
                    player.getZ() + z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );

                level.sendParticles(
                    particle,
                    player.getX() + sign * sideX,
                    kiY(player, y),
                    player.getZ() - z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );
            }
        }
    }

    /**
     * Visible inward-flowing ki streams aimed at the torso/head center.
     * This directly addresses the "I can't see anything going into the character"
     * feedback from 2.9.
     */
    private static void spawnConvergingInflow(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        int ticks,
        int lanes,
        boolean wide
    ) {
        double targetX =
            player.getX();

        double targetY =
            kiY(player, 0.76D);

        double targetZ =
            player.getZ();

        double baseRadius =
            wide ? 1.05D : 0.82D;

        for (int i = 0; i < lanes; i++) {
            double angle =
                ticks * 0.11D
                    + (Math.PI * 2.0D * i / lanes);

            double radius =
                baseRadius
                    + 0.10D * Math.sin(angle * 0.7D + i);

            double startX =
                player.getX() + Math.cos(angle) * radius;

            double startZ =
                player.getZ() + Math.sin(angle) * radius;

            double startY =
                kiY(
                    player,
                    0.24D + (i % 3) * 0.28D + 0.05D * Math.sin(angle)
                );

            for (int step = 0; step < 4; step++) {
                double t = step / 3.0D;
                double eased = smoothstep(t);

                double x = Mth.lerp(eased, startX, targetX);
                double y = Mth.lerp(eased, startY, targetY);
                double z = Mth.lerp(eased, startZ, targetZ);

                level.sendParticles(
                    particle,
                    x,
                    y,
                    z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                );
            }
        }
    }

    private static void spawnVerticalKiFlow(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        int ticks,
        int strands
    ) {
        for (int i = 0; i < strands; i++) {
            double phase =
                (ticks * 0.040D)
                    + i * (Math.PI * 2.0D / strands);

            double radius =
                0.18D + 0.04D * Math.sin(phase * 0.8D);

            double x =
                player.getX() + Math.cos(phase) * radius;

            double z =
                player.getZ() + Math.sin(phase) * radius;

            double rise =
                ((ticks + i * 11) % 28) / 28.0D;

            double y =
                kiY(player, 0.14D + rise * 1.02D);

            level.sendParticles(
                particle,
                x,
                y,
                z,
                1,
                0.0D,
                0.018D,
                0.0D,
                0.0D
            );
        }
    }

    private static void spawnCorePulse(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        int count
    ) {
        level.sendParticles(
            particle,
            player.getX(),
            kiY(player, 0.76D),
            player.getZ(),
            count,
            0.08D,
            0.10D,
            0.08D,
            0.002D
        );
    }

    private static void spawnHeadHalo(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        double radius,
        int points
    ) {
        spawnKiRing(
            level,
            player,
            particle,
            radius,
            points,
            1.18D
        );
    }

    private static void spawnGroundKiRing(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        double radius,
        int points
    ) {
        CompoundTag data =
            player.getPersistentData();

        double y =
            data.contains(BASE_Y)
                ? data.getDouble(BASE_Y) + 0.035D
                : player.getY() + 0.035D;

        for (int i = 0; i < points; i++) {
            double angle =
                Math.PI * 2.0D * i / points;

            level.sendParticles(
                particle,
                player.getX() + Math.cos(angle) * radius,
                y,
                player.getZ() + Math.sin(angle) * radius,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
            );
        }
    }

    private static double smoothstep(double value) {
        double t =
            Math.max(
                0.0D,
                Math.min(1.0D, value)
            );

        return t * t * (3.0D - 2.0D * t);
    }

    private static void spawnMeditationRelease(
        ServerPlayer player,
        boolean interrupted
    ) {
        if (!MeditationConfig.SERVER.particles.get()) {
            return;
        }

        ServerLevel level =
            player.serverLevel();

        float[] rgb =
            DMZKiColorBridge.getEffectRgb(player);

        if (interrupted) {
            spawnCustomKiCluster(
                level,
                player,
                KI_BURST.get(),
                rgb,
                6,
                0.30D,
                0.28D,
                0.65D
            );
            spawnCustomKiRing(
                level,
                player,
                KI_MOTE.get(),
                rgb,
                0.72D,
                14,
                0.34D
            );
        } else {
            spawnCustomKiRing(
                level,
                player,
                KI_MOTE.get(),
                rgb,
                0.82D,
                18,
                0.38D
            );
        }
    }

    private static int scaledParticleCount(int count) {
        if (count <= 0) return 0;
        int percent = MeditationConfig.SERVER.particleDensityPercent.get();
        return Math.max(1, (int)Math.round(count * (percent / 100.0D)));
    }

    private static void spawnCustomKiCluster(
        ServerLevel level,
        ServerPlayer player,
        SimpleParticleType type,
        float[] rgb,
        int count,
        double horizontalSpread,
        double verticalSpread,
        double yOffset
    ) {
        count = scaledParticleCount(count);
        for (int i = 0; i < count; i++) {
            double x =
                player.getX()
                    + (player.getRandom().nextDouble() * 2.0D - 1.0D)
                        * horizontalSpread;
            double y =
                kiY(player, yOffset)
                    + (player.getRandom().nextDouble() * 2.0D - 1.0D)
                        * verticalSpread;
            double z =
                player.getZ()
                    + (player.getRandom().nextDouble() * 2.0D - 1.0D)
                        * horizontalSpread;
            sendCustomKiParticle(level, type, x, y, z, rgb);
        }
    }

    private static void spawnCustomKiRing(
        ServerLevel level,
        ServerPlayer player,
        SimpleParticleType type,
        float[] rgb,
        double radius,
        int points,
        double yOffset
    ) {
        points = scaledParticleCount(points);
        double phase = player.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            sendCustomKiParticle(
                level,
                type,
                player.getX() + Math.cos(angle) * radius,
                kiY(player, yOffset),
                player.getZ() + Math.sin(angle) * radius,
                rgb
            );
        }
    }

    private static void sendCustomKiParticle(
        ServerLevel level,
        SimpleParticleType type,
        double x,
        double y,
        double z,
        float[] rgb
    ) {
        /*
         * A zero-count particle packet carries these three values directly to
         * the client provider, where the custom sprite interprets them as RGB.
         */
        level.sendParticles(
            type,
            x,
            y,
            z,
            0,
            Mth.clamp(rgb[0], 0.0F, 1.0F),
            Mth.clamp(rgb[1], 0.0F, 1.0F),
            Mth.clamp(rgb[2], 0.0F, 1.0F),
            1.0D
        );
    }

    private static void spawnOrbit(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        int ticks,
        int points,
        double radius,
        double height
    ) {
        points = scaledParticleCount(points);
        for (int i = 0; i < points; i++) {
            double angle =
                ticks * 0.026D
                    + (Math.PI * 2.0D * i / points);

            double x =
                player.getX()
                    + Math.cos(angle) * radius;

            double z =
                player.getZ()
                    + Math.sin(angle) * radius;

            double y =
                kiY(
                    player,
                    0.16D
                        + (i % 2) * height
                );

            level.sendParticles(
                particle,
                x, y, z,
                1,
                0.0D, 0.0D, 0.0D,
                0.0D
            );
        }
    }

    private static void spawnKiRing(
        ServerLevel level,
        ServerPlayer player,
        ParticleOptions particle,
        double radius,
        int points,
        double yOffset
    ) {
        points = scaledParticleCount(points);
        for (int i = 0; i < points; i++) {
            double angle =
                Math.PI * 2.0D * i / points;

            double x =
                player.getX()
                    + Math.cos(angle) * radius;

            double z =
                player.getZ()
                    + Math.sin(angle) * radius;

            level.sendParticles(
                particle,
                x,
                kiY(player, yOffset),
                z,
                1,
                0.0D, 0.0D, 0.0D,
                0.0D
            );
        }
    }

    private static void milestone(ServerPlayer player, String message, float pitch) {
        milestoneBurst(player, pitch);
        player.sendSystemMessage(
            Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE)
        );
    }

    private static void milestoneBurst(ServerPlayer player, float pitch) {
        ServerLevel level = player.serverLevel();

        if (MeditationConfig.SERVER.particles.get()) {
            float[] rgb =
                DMZKiColorBridge.getEffectRgb(player);

            spawnCustomKiCluster(
                level,
                player,
                KI_BURST.get(),
                rgb,
                8,
                0.34D,
                0.28D,
                0.62D
            );
            spawnCustomKiRing(
                level,
                player,
                KI_MOTE.get(),
                rgb,
                0.82D,
                18,
                0.40D
            );
        }

        if (MeditationConfig.SERVER.milestoneSounds.get()) {
            level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.55F,
                pitch
            );
        }
    }

    private static void awardMeditationTp(
        ServerPlayer player,
        int stageMultiplier
    ) {
        if (!MeditationConfig.SERVER.tpRewardsEnabled.get()) return;
        int scale = MeditationConfig.SERVER.tpRewardScalePercent.get();
        if (scale <= 0) return;
        CompoundTag data = player.getPersistentData();
        int progressionUnit = DMZTrainingBridge.getMeditationTpUnit(player);
        long raw = (long)stageMultiplier * progressionUnit;
        int actualGain = (int)Math.max(1L, Math.min(MAX_SAFE_TP_AWARD, Math.round(raw * scale / 100.0D)));
        data.putInt(TP_CARRY, 0);
        queueTpBridge(player, actualGain);
        addTpStats(player, actualGain);
        syncMeditationState(player);
    }

    private static void awardFixedTp(
        ServerPlayer player,
        int gain,
        String reason
    ) {
        if (gain <= 0) {
            return;
        }

        int safeGain =
            Math.min(
                MAX_SAFE_TP_AWARD,
                gain
            );

        if (safeGain != gain) {
            LOGGER.warn(
                "Clamped {} TP reward from {} to {} for player {}.",
                reason,
                gain,
                safeGain,
                player.getGameProfile().getName()
            );
        }

        queueTpBridge(
            player,
            safeGain
        );
        addTpStats(
            player,
            safeGain
        );
        syncMeditationState(player);
    }

    private static void addTpStats(
        ServerPlayer player,
        int gain
    ) {
        CompoundTag data =
            player.getPersistentData();

        data.putInt(
            TOTAL,
            saturatingAdd(
                data.getInt(TOTAL),
                gain
            )
        );

        data.putInt(
            LIFETIME_TP,
            saturatingAdd(
                data.getInt(LIFETIME_TP),
                gain
            )
        );
    }

    private static int saturatingAdd(
        int current,
        int gain
    ) {
        long result =
            (long)current + gain;

        return (int)Math.max(
            Integer.MIN_VALUE,
            Math.min(
                Integer.MAX_VALUE,
                result
            )
        );
    }

    private static void queueTpBridge(ServerPlayer player, int amount) {
        int remaining = Math.max(0, amount);

        // Entity tags are sets. Unique binary tags represent large rewards
        // exactly while keeping dmzpoints inside the proven mcfunction context.
        for (int denomination : TP_BRIDGE_DENOMINATIONS) {
            if (remaining >= denomination) {
                player.addTag("dbzm_tp_reward_" + denomination);
                remaining -= denomination;
            }
        }

        if (remaining > 0) {
            DBZMeditation.LOGGER.warn(
                "TP bridge amount {} exceeded supported one-tick range; {} TP could not be queued.",
                amount,
                remaining
            );
        }
    }

    private static void clearRewardTags(ServerPlayer player) {
        // Historic tags are cleared for upgrade safety.
        for (int i = 1; i <= 20; i++) {
            player.removeTag("dbzm_tp_reward_" + i);
        }
        for (int denomination : TP_BRIDGE_DENOMINATIONS) {
            player.removeTag("dbzm_tp_reward_" + denomination);
        }
    }

    private static int getMeditatingGroupCount(ServerPlayer player) {
        if (!MeditationConfig.SERVER.groupMeditation.get()) {
            return isMeditating(player) ? 1 : 0;
        }

        double radius =
            MeditationConfig.SERVER.groupMeditationRadius.get();
        int playerMeditators = player.serverLevel().getEntitiesOfClass(
            ServerPlayer.class,
            player.getBoundingBox().inflate(radius),
            other -> other.isAlive() && isMeditating(other)
        ).size();
        int externalPartners = MeditationIntegrationApi.getFreshExternalMeditationPartners(player);
        return Math.max(isMeditating(player) ? 1 : 0, Math.min(12, playerMeditators + externalPartners));
    }

    private static void sendStats(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();

        player.sendSystemMessage(
            Component.literal("Meditation Statistics").withStyle(ChatFormatting.AQUA)
        );

        player.sendSystemMessage(Component.literal(
            "Lifetime meditation: " + formatLongTime(data.getInt(LIFETIME_TICKS))
        ));
        player.sendSystemMessage(Component.literal(
            "Longest session: " + formatLongTime(data.getInt(LONGEST_TICKS))
        ));
        player.sendSystemMessage(Component.literal(
            "Sessions started: " + data.getInt(SESSIONS)
                + " | Interruptions: " + data.getInt(INTERRUPTIONS)
        ));
        player.sendSystemMessage(Component.literal(
            "Meditation TP earned: " + data.getInt(LIFETIME_TP)
        ));
        player.sendSystemMessage(Component.literal(
            "Transcendent reached: " + data.getInt(TRANSCENDENT_COUNT) + " times"
        ));
        player.sendSystemMessage(Component.literal(
            "Meditative breakthroughs: " + data.getInt(STAT_BREAKTHROUGHS)
        ));
    }

    private static void syncMeditationState(
        ServerPlayer player
    ) {
        CompoundTag data =
            player.getPersistentData();

        boolean active =
            isMeditating(player);

        int ticks =
            data.getInt(TICKS);


        float[] resources =
            data.getBoolean(DEBUG_VIEW)
                ? DMZTrainingBridge
                    .getResourcePercents(player)
                : new float[] {0.0F, 0.0F};

        DMZTrainingBridge.FormProgress formProgress =
            DMZTrainingBridge.getFormProgress(player);

        MeditationNetwork.sendState(
            player,
            active,
            ticks,
            data.getInt(TOTAL),
            active ? getMeditatingGroupCount(player) : 0,
            formatSessionStatGains(data),
            getStageIndex(ticks),
            getMultiplier(ticks),
            getStageProgress(ticks),
            MeditationConfig.isFastTesting(),
            data.getBoolean(DEBUG_VIEW),
            resources[0],
            resources[1],
            formProgress.active() ? prettyFormName(formProgress.form()) : "",
            formProgress.mastery(),
            formProgress.maxMastery(),
            data.getDouble(SESSION_MASTERY_GAIN)
        );
    }

    private static void sendDebug(
        ServerPlayer player
    ) {
        CompoundTag data =
            player.getPersistentData();

        int ticks =
            data.getInt(TICKS);

        float[] rgb =
            DMZKiColorBridge.getEffectRgb(player);

        player.sendSystemMessage(
            Component.literal(
                "Meditation Debug"
            ).withStyle(ChatFormatting.AQUA)
        );

        player.sendSystemMessage(
            Component.literal(
                "Active=" + isMeditating(player)
                    + "  ticks=" + ticks
                    + "  stage=" + getStageName(ticks)
                    + "  progress="
                    + Math.round(getStageProgress(ticks) * 100.0F)
                    + "%"
            )
        );

        player.sendSystemMessage(
            Component.literal(
                "Session TP=" + data.getInt(TOTAL)
                    + "  fastTest=" + MeditationConfig.isFastTesting()
            )
        );

        int statCost =
            DMZTrainingBridge
                .getProgressionSingleStatCost(player);

        long nativeLevelTp =
            Math.round(
                DMZTrainingBridge
                    .getNativeMinigameTpPerLevel(player)
            );

        int meditationUnit =
            DMZTrainingBridge
                .getMeditationTpUnit(player);

        int meditationMultiplier =
            getMultiplier(ticks);

        int meditationBonusPercent = 0;

        long currentCycleTp =
            Math.max(
                1L,
                Math.round(
                    meditationUnit
                        * (double)meditationMultiplier
                        * (100.0D
                            + meditationBonusPercent)
                        / 100.0D
                )
            );

        player.sendSystemMessage(
            Component.literal(
                "Progression: statCost="
                    + statCost
                    + "  nativeLevelTP="
                    + nativeLevelTp
                    + "  meditationUnit="
                    + meditationUnit
            ).withStyle(ChatFormatting.DARK_GRAY)
        );

        player.sendSystemMessage(
            Component.literal(
                "Rewards now: cycle≈"
                    + currentCycleTp
                    + " TP  (x"
                    + meditationMultiplier
                    + ", +"
                    + meditationBonusPercent
                    + "%)"
            ).withStyle(ChatFormatting.DARK_GRAY)
        );

        float[] resources =
            DMZTrainingBridge.getResourcePercents(player);
        DMZTrainingBridge.FormProgress form =
            DMZTrainingBridge.getFormProgress(player);

        player.sendSystemMessage(
            Component.literal(
                "Resources: Ki="
                    + Math.round(resources[0] * 100.0F)
                    + "%  Stamina="
                    + Math.round(resources[1] * 100.0F)
                    + "%"
            )
        );

        player.sendSystemMessage(
            Component.literal(
                "Form="
                    + (form.active() ? prettyFormName(form.form()) : "none")
                    + "  mastery="
                    + String.format(java.util.Locale.ROOT, "%.3f/%.3f", form.mastery(), form.maxMastery())
                    + "  session+="
                    + String.format(java.util.Locale.ROOT, "%.3f", data.getDouble(SESSION_MASTERY_GAIN))
            )
        );

        player.sendSystemMessage(
            Component.literal(
                String.format(
                    "Ki RGB=%.3f / %.3f / %.3f",
                    rgb[0],
                    rgb[1],
                    rgb[2]
                )
            )
        );

        player.sendSystemMessage(
            Component.literal(
                "Thresholds: "
                    + formatTime(MeditationConfig.focusedTicks())
                    + " / "
                    + formatTime(MeditationConfig.centeredTicks())
                    + " / "
                    + formatTime(MeditationConfig.deepTicks())
                    + " / "
                    + formatTime(MeditationConfig.transcendentTicks())
            )
        );
    }

    private static void sendStatus(ServerPlayer player) {
        if (!isMeditating(player)) {
            int cooldown = player.getPersistentData().getInt(COOLDOWN);
            String extra = cooldown > 0 ? " Cooldown: " + ((cooldown + 19) / 20) + "s." : "";
            player.sendSystemMessage(Component.literal("You are not meditating." + extra));
            return;
        }
        CompoundTag data = player.getPersistentData();
        int ticks = data.getInt(TICKS);
        player.sendSystemMessage(Component.literal(
                "Meditating | " + getStageName(ticks)
                        + " | " + formatTime(ticks)
                        + " | Focus x" + getMultiplier(ticks)
                        + " | Session TP: " + data.getInt(TOTAL)
        ).withStyle(ChatFormatting.AQUA));
    }

    private static void sendInfo(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Dragon Mine Z: Meditation").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal(
                "Stages: Calm x1 → Focused x2 → Centered x3 → Deep x4 → Transcendent x5."));
        player.sendSystemMessage(Component.literal(
                "Meditation earns TP; Deep/Transcendent can train active form mastery when enabled."));
        player.sendSystemMessage(Component.literal(
                "Rare Meditative Breakthroughs can permanently increase one base stat by the configured percentage."));
        player.sendSystemMessage(Component.literal(
                "Commands: /meditate, /meditate status, /meditate stats, /meditate info."));
    }

    private static void grantAdvancement(ServerPlayer player, String id) {
        Advancement advancement =
            player.getServer()
                .getAdvancements()
                .getAdvancement(
                    new ResourceLocation(MODID, id)
                );

        if (advancement == null) {
            return;
        }

        AdvancementProgress progress =
            player.getAdvancements()
                .getOrStartProgress(advancement);

        if (progress.isDone()) {
            return;
        }

        // Award only missing impossible criteria. No command feedback, no
        // repeated "couldn't grant" errors, and no duplicate toast.
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(
                advancement,
                criterion
            );
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isMeditating(player)) return;
        if (!MeditationConfig.SERVER.damageInterrupts.get()) return;
        stopMeditation(player, "Meditation broken by damage.", true, true);
    }

    /**
     * Clean shutdown safety for the active meditation session itself.
     * Removes temporary seats and clears reward/session tags without touching the
     * player's world position or creating any retired Image Training state.
     */
    @SubscribeEvent
    public void onServerStopping(
        ServerStoppingEvent event
    ) {
        for (ServerPlayer player :
            event.getServer()
                .getPlayerList()
                .getPlayers()) {


            if (isMeditating(player)) {
                silentCleanup(player);
            } else {
                removeSeat(player);
                clearRewardTags(player);
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isMeditating(player)) {
                silentCleanup(player);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(
        PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity()
            instanceof ServerPlayer player)) {

            return;
        }

        /*
         * Upgrade/reinstall scrub. Restore any interrupted body snapshot first,
         * then remove only transient session state.
         */
        MeditationLegacyMigration.scrubRetiredSessionState(player);

        CompoundTag data =
            player.getPersistentData();

        data.putBoolean(ACTIVE, false);
        MeditationIntegrationApi.clearExternalMeditationPartners(player);

        data.remove(SESSION_KI_RECOVERED);
        data.remove(SESSION_STAMINA_RECOVERED);

        removeSeat(player);
        clearRewardTags(player);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = player.getPersistentData();

        copyInt(oldData, newData, LIFETIME_TICKS);
        copyInt(oldData, newData, LONGEST_TICKS);
        copyInt(oldData, newData, LIFETIME_TP);
        copyInt(oldData, newData, SESSIONS);
        copyInt(oldData, newData, INTERRUPTIONS);
        copyInt(oldData, newData, TRANSCENDENT_COUNT);
        copyInt(oldData, newData, STAT_BREAKTHROUGHS);


        newData.putBoolean(ACTIVE, false);
    }

    private static void copyInt(CompoundTag oldData, CompoundTag newData, String key) {
        newData.putInt(key, oldData.getInt(key));
    }

    private static void copyBoolean(CompoundTag oldData, CompoundTag newData, String key) {
        newData.putBoolean(key, oldData.getBoolean(key));
    }

    private static void resetSessionStatGains(CompoundTag data) {
        data.putInt(SESSION_STAT_STR, 0);
        data.putInt(SESSION_STAT_SKP, 0);
        data.putInt(SESSION_STAT_RES, 0);
        data.putInt(SESSION_STAT_VIT, 0);
        data.putInt(SESSION_STAT_PWR, 0);
        data.putInt(SESSION_STAT_ENE, 0);
    }

    private static void recordSessionStatGain(
        CompoundTag data,
        String stat,
        int amount
    ) {
        String key = switch (stat) {
            case "STR" -> SESSION_STAT_STR;
            case "SKP" -> SESSION_STAT_SKP;
            case "RES" -> SESSION_STAT_RES;
            case "VIT" -> SESSION_STAT_VIT;
            case "PWR" -> SESSION_STAT_PWR;
            case "ENE" -> SESSION_STAT_ENE;
            default -> null;
        };

        if (key == null) {
            return;
        }

        int granted = Math.max(1, amount);
        data.putInt(key, data.getInt(key) + granted);
        data.putInt(
            STAT_BREAKTHROUGHS,
            data.getInt(STAT_BREAKTHROUGHS) + 1
        );
    }

    private static String formatSessionStatGains(CompoundTag data) {
        StringBuilder result = new StringBuilder();

        appendStatGain(result, "STR", data.getInt(SESSION_STAT_STR));
        appendStatGain(result, "SKP", data.getInt(SESSION_STAT_SKP));
        appendStatGain(result, "RES", data.getInt(SESSION_STAT_RES));
        appendStatGain(result, "VIT", data.getInt(SESSION_STAT_VIT));
        appendStatGain(result, "PWR", data.getInt(SESSION_STAT_PWR));
        appendStatGain(result, "ENE", data.getInt(SESSION_STAT_ENE));

        return result.toString();
    }

    private static void appendStatGain(
        StringBuilder result,
        String stat,
        int amount
    ) {
        if (amount <= 0) {
            return;
        }
        if (!result.isEmpty()) {
            result.append("  •  ");
        }
        result.append('+').append(amount).append(' ').append(stat);
    }

    public static boolean isMeditationSeat(Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        Component name = entity.getCustomName();
        return name != null && SEAT_NAME.equals(name.getString());
    }

    public static int getStageIndex(int ticks) {
        if (ticks >= MeditationConfig.transcendentTicks()) return 4;
        if (ticks >= MeditationConfig.deepTicks()) return 3;
        if (ticks >= MeditationConfig.centeredTicks()) return 2;
        if (ticks >= MeditationConfig.focusedTicks()) return 1;
        return 0;
    }

    public static int getMultiplier(int ticks) {
        return switch (getStageIndex(ticks)) {
            case 4 -> MeditationConfig.SERVER.transcendentMultiplier.get();
            case 3 -> MeditationConfig.SERVER.deepMultiplier.get();
            case 2 -> MeditationConfig.SERVER.centeredMultiplier.get();
            case 1 -> MeditationConfig.SERVER.focusedMultiplier.get();
            default -> MeditationConfig.SERVER.calmMultiplier.get();
        };
    }

    public static String getStageName(int ticks) {
        return switch (getStageIndex(ticks)) {
            case 4 -> "Transcendent";
            case 3 -> "Deep";
            case 2 -> "Centered";
            case 1 -> "Focused";
            default -> "Calm";
        };
    }

    public static int getStageStartTicks(int stage) {
        return switch (stage) {
            case 1 -> MeditationConfig.focusedTicks();
            case 2 -> MeditationConfig.centeredTicks();
            case 3 -> MeditationConfig.deepTicks();
            case 4 -> MeditationConfig.transcendentTicks();
            default -> 0;
        };
    }

    public static int getNextStageTicks(int ticks) {
        int stage = getStageIndex(ticks);

        return switch (stage) {
            case 0 -> MeditationConfig.focusedTicks();
            case 1 -> MeditationConfig.centeredTicks();
            case 2 -> MeditationConfig.deepTicks();
            case 3 -> MeditationConfig.transcendentTicks();
            default -> MeditationConfig.transcendentTicks();
        };
    }

    public static float getStageProgress(int ticks) {
        int stage = getStageIndex(ticks);
        if (stage >= 4) return 1.0F;

        int start = getStageStartTicks(stage);
        int next = getNextStageTicks(ticks);
        int span = Math.max(1, next - start);

        return Math.max(0.0F, Math.min(1.0F, (ticks - start) / (float) span));
    }

    public static float getSensoryProgress(int ticks) {
        int centered = MeditationConfig.centeredTicks();
        int deep = MeditationConfig.deepTicks();
        int transcendent = MeditationConfig.transcendentTicks();

        if (ticks < centered) {
            return 0.0F;
        }

        int deepToTranscendent =
            Math.max(1, transcendent - deep);

        int fullAt =
            deep + Math.round(deepToTranscendent * 0.35F);

        int span =
            Math.max(1, fullAt - centered);

        float raw =
            Math.max(
                0.0F,
                Math.min(
                    1.0F,
                    (ticks - centered) / (float) span
                )
            );

        // smootherstep
        return raw * raw * raw
            * (raw * (raw * 6.0F - 15.0F) + 10.0F);
    }

    private static String formatTime(int ticks) {
        int seconds = ticks / 20;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private static String formatLongTime(int ticks) {
        long seconds = ticks / 20L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
