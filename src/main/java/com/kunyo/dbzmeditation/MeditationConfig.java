package com.kunyo.dbzmeditation;

import net.minecraftforge.common.ForgeConfigSpec;

/** Integrated Living World meditation tuning. */
public final class MeditationConfig {
    private static volatile boolean FAST_TESTING = false;

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    private MeditationConfig() {}

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue tpRewardsEnabled;
        public final ForgeConfigSpec.IntValue tpRewardScalePercent;
        public final ForgeConfigSpec.IntValue rewardIntervalSeconds;
        public final ForgeConfigSpec.IntValue focusedSeconds;
        public final ForgeConfigSpec.IntValue centeredSeconds;
        public final ForgeConfigSpec.IntValue deepSeconds;
        public final ForgeConfigSpec.IntValue transcendentSeconds;
        /** Legacy compatibility cap; per-stage multipliers below are now authoritative. */
        public final ForgeConfigSpec.IntValue maxMultiplier;
        public final ForgeConfigSpec.IntValue calmMultiplier;
        public final ForgeConfigSpec.IntValue focusedMultiplier;
        public final ForgeConfigSpec.IntValue centeredMultiplier;
        public final ForgeConfigSpec.IntValue deepMultiplier;
        public final ForgeConfigSpec.IntValue transcendentMultiplier;

        public final ForgeConfigSpec.BooleanValue damageInterrupts;
        public final ForgeConfigSpec.IntValue damageCooldownSeconds;

        /** Group detection is presentation/integration only; it never changes rewards. */
        public final ForgeConfigSpec.BooleanValue groupMeditation;
        public final ForgeConfigSpec.IntValue groupMeditationRadius;
        public final ForgeConfigSpec.BooleanValue livingWorldNpcMeditation;

        public final ForgeConfigSpec.BooleanValue statBreakthroughEnabled;
        public final ForgeConfigSpec.DoubleValue statBreakthroughChancePercent;
        public final ForgeConfigSpec.IntValue statBreakthroughRollSeconds;
        public final ForgeConfigSpec.IntValue statBreakthroughPoints;

        public final ForgeConfigSpec.BooleanValue formMasteryEnabled;
        public final ForgeConfigSpec.DoubleValue deepFormMasteryPerMinute;
        public final ForgeConfigSpec.DoubleValue transcendentFormMasteryPerMinute;

        public final ForgeConfigSpec.DoubleValue levitationHeight;
        public final ForgeConfigSpec.BooleanValue particles;
        public final ForgeConfigSpec.IntValue particleDensityPercent;
        public final ForgeConfigSpec.BooleanValue milestoneSounds;

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Dragon Mine Z: Living World — integrated meditation settings.",
                    "Meditation is part of Living World; the old standalone Image Training/Image Spar feature is retired.")
                    .push("meditation");

            enabled = builder.comment("Master switch for player meditation. NPC ambient meditation can be controlled separately below.")
                    .define("enabled", true);

            builder.push("progression");
            tpRewardsEnabled = builder.comment("Allow meditation to award Dragon Mine Z TP.")
                    .define("tpRewardsEnabled", true);
            tpRewardScalePercent = builder.comment("Global percentage applied to passive meditation TP. At 100%, Calm uses the restrained baseline and deeper stages gradually multiply it.")
                    .defineInRange("tpRewardScalePercent", 100, 0, 5000);
            rewardIntervalSeconds = builder.comment("Base seconds between meditation TP reward pulses.")
                    .defineInRange("rewardIntervalSeconds", 5, 1, 120);
            focusedSeconds = builder.comment("Seconds required to reach Focused.")
                    .defineInRange("focusedSeconds", 60, 5, 3600);
            centeredSeconds = builder.comment("Seconds required to reach Centered. Keep this above Focused for natural progression.")
                    .defineInRange("centeredSeconds", 180, 10, 7200);
            deepSeconds = builder.comment("Seconds required to reach Deep Meditation.")
                    .defineInRange("deepSeconds", 300, 15, 10800);
            transcendentSeconds = builder.comment("Seconds required to reach Transcendent.")
                    .defineInRange("transcendentSeconds", 600, 30, 21600);
            maxMultiplier = builder.comment("Legacy compatibility cap from pre-1.9 configs. Kept readable, but per-stage multipliers below are authoritative.")
                    .defineInRange("maxMultiplier", 5, 1, 100);
            builder.push("stageMultipliers");
            calmMultiplier = builder.comment("TP multiplier while Calm.").defineInRange("calm", 1, 1, 100);
            focusedMultiplier = builder.comment("TP multiplier while Focused.").defineInRange("focused", 2, 1, 100);
            centeredMultiplier = builder.comment("TP multiplier while Centered.").defineInRange("centered", 3, 1, 100);
            deepMultiplier = builder.comment("TP multiplier while Deep.").defineInRange("deep", 4, 1, 100);
            transcendentMultiplier = builder.comment("TP multiplier while Transcendent.").defineInRange("transcendent", 5, 1, 100);
            builder.pop();
            builder.pop();

            builder.push("interruption");
            damageInterrupts = builder.comment("If true, taking real damage breaks meditation.")
                    .define("damageInterrupts", true);
            damageCooldownSeconds = builder.comment("Cooldown after damage breaks meditation.")
                    .defineInRange("damageCooldownSeconds", 8, 0, 120);
            builder.pop();

            builder.push("sharedMeditation");
            groupMeditation = builder.comment("Detect nearby meditators for synchronized visuals only. This never multiplies TP/mastery rewards.")
                    .define("enabled", true);
            groupMeditationRadius = builder.comment("Maximum distance in blocks for synchronized meditation visuals.")
                    .defineInRange("radius", 12, 2, 64);
            livingWorldNpcMeditation = builder.comment("Allow known Living World fighters and ambient faction residents to participate in meditation scenes.")
                    .define("livingWorldNpcMeditation", true);
            builder.pop();

            builder.push("breakthroughs");
            statBreakthroughEnabled = builder.comment("Allow rare Meditative Breakthroughs that permanently improve one base stat.")
                    .define("enabled", true);
            statBreakthroughChancePercent = builder.comment("Chance per completed roll interval to gain a base-stat breakthrough.")
                    .defineInRange("chancePercent", 1.0D, 0.0D, 100.0D);
            statBreakthroughRollSeconds = builder.comment("Meditation seconds between breakthrough rolls.")
                    .defineInRange("rollSeconds", 60, 10, 3600);
            statBreakthroughPoints = builder.comment("Percentage of the selected base stat gained from a successful breakthrough.")
                    .defineInRange("pointsPerBreakthrough", 1, 1, 100);
            builder.pop();

            builder.push("formMastery");
            formMasteryEnabled = builder.comment("Allow Deep/Transcendent meditation to slowly train the currently maintained DMZ form.")
                    .define("enabled", true);
            deepFormMasteryPerMinute = builder.comment("Native DMZ form mastery gained per minute while in Deep Meditation.")
                    .defineInRange("deepPerMinute", 0.050D, 0.0D, 5.0D);
            transcendentFormMasteryPerMinute = builder.comment("Native DMZ form mastery gained per minute while Transcendent.")
                    .defineInRange("transcendentPerMinute", 0.100D, 0.0D, 5.0D);
            builder.pop();

            builder.push("serverVisuals");
            levitationHeight = builder.comment("Maximum levitation height between Deep and Transcendent.")
                    .defineInRange("levitationHeight", 0.35D, 0.0D, 2.0D);
            particles = builder.comment("Allow meditation particle effects to be emitted by the server.")
                    .define("particles", true);
            particleDensityPercent = builder.comment("Server particle density percentage. 100 = normal; lower values reduce visual/network load.")
                    .defineInRange("particleDensityPercent", 100, 10, 200);
            milestoneSounds = builder.comment("Enable meditation stage transition sounds.")
                    .define("milestoneSounds", true);
            builder.pop();

            builder.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue focusHud;
        public final ForgeConfigSpec.BooleanValue sessionSummary;
        public final ForgeConfigSpec.IntValue sessionSummarySeconds;
        public final ForgeConfigSpec.BooleanValue nativeDmzMeditationAnimation;
        public final ForgeConfigSpec.IntValue hudTopOffset;
        public final ForgeConfigSpec.IntValue auraIntensityPercent;
        public final ForgeConfigSpec.BooleanValue groundFocusCircle;
        public final ForgeConfigSpec.BooleanValue focusSealEnabled;
        public final ForgeConfigSpec.IntValue focusSealIntensityPercent;
        public final ForgeConfigSpec.IntValue focusSealRadiusPercent;
        public final ForgeConfigSpec.BooleanValue firstPersonAura;
        public final ForgeConfigSpec.BooleanValue stageTransitionEffects;
        public final ForgeConfigSpec.BooleanValue npcMeditationEffects;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Dragon Mine Z: Living World — client-only meditation presentation.")
                    .push("meditationVisuals");
            focusHud = builder.comment("Show the meditation focus HUD.").define("focusHud", true);
            sessionSummary = builder.comment("Show a temporary non-chat summary after a meditation session.").define("sessionSummary", true);
            sessionSummarySeconds = builder.comment("Compatibility-only legacy value. The in-game summary now uses the established five-second duration.")
                    .defineInRange("sessionSummarySeconds", 5, 2, 15);
            nativeDmzMeditationAnimation = builder.comment("Use Dragon Mine Z's base.meditation animation when available.")
                    .define("nativeDmzMeditationAnimation", true);
            hudTopOffset = builder.comment("Vertical position of the meditation HUD from the top of the screen.")
                    .defineInRange("hudTopOffset", 12, 0, 240);
            auraIntensityPercent = builder.comment("Overall meditation effect intensity percentage. Visual only; this does not restore the removed extra aura layer.")
                    .defineInRange("auraIntensityPercent", 100, 0, 250);
            groundFocusCircle = builder.comment("Legacy compatibility toggle for the ground meditation effect; kept mapped to the Focus Seal.")
                    .define("groundFocusCircle", true);
            focusSealEnabled = builder.comment("Render the anime-style Focus Seal beneath the meditating character. It grows more elaborate with meditation depth.")
                    .define("focusSealEnabled", true);
            focusSealIntensityPercent = builder.comment("Brightness/density of the Focus Seal beneath the character.")
                    .defineInRange("focusSealIntensityPercent", 100, 25, 200);
            focusSealRadiusPercent = builder.comment("Size of the Focus Seal. 100 = normal.")
                    .defineInRange("focusSealRadiusPercent", 100, 60, 160);
            firstPersonAura = builder.comment("Show a restrained aura at the edges of first-person view.")
                    .define("firstPersonAura", true);
            stageTransitionEffects = builder.comment("Show stage-transition flashes/bursts when meditation deepens.")
                    .define("stageTransitionEffects", true);
            npcMeditationEffects = builder.comment("Show Living World NPC meditation seals, particles and shared-bond effects. Client only.")
                    .define("npcMeditationEffects", true);
            builder.pop();
        }
    }

    public static ServerSnapshot serverSnapshot() {
        return new ServerSnapshot(SERVER.enabled.get(), SERVER.tpRewardsEnabled.get(), SERVER.tpRewardScalePercent.get(),
                SERVER.rewardIntervalSeconds.get(), SERVER.focusedSeconds.get(), SERVER.centeredSeconds.get(),
                SERVER.deepSeconds.get(), SERVER.transcendentSeconds.get(), SERVER.calmMultiplier.get(),
                SERVER.focusedMultiplier.get(), SERVER.centeredMultiplier.get(), SERVER.deepMultiplier.get(),
                SERVER.transcendentMultiplier.get(), SERVER.damageInterrupts.get(), SERVER.damageCooldownSeconds.get(),
                SERVER.groupMeditation.get(), SERVER.groupMeditationRadius.get(), SERVER.livingWorldNpcMeditation.get(),
                SERVER.statBreakthroughEnabled.get(), SERVER.statBreakthroughChancePercent.get(),
                SERVER.statBreakthroughRollSeconds.get(), SERVER.statBreakthroughPoints.get(), SERVER.formMasteryEnabled.get(),
                SERVER.deepFormMasteryPerMinute.get(), SERVER.transcendentFormMasteryPerMinute.get(), SERVER.levitationHeight.get(),
                SERVER.particles.get(), SERVER.particleDensityPercent.get(), SERVER.milestoneSounds.get());
    }

    public static void applyServer(ServerSnapshot v) {
        if (v == null) return;
        SERVER.enabled.set(v.enabled());
        SERVER.tpRewardsEnabled.set(v.tpRewardsEnabled());
        SERVER.tpRewardScalePercent.set(clamp(v.tpRewardScalePercent(), 0, 5000));
        SERVER.rewardIntervalSeconds.set(clamp(v.rewardIntervalSeconds(), 1, 120));
        int focused = clamp(v.focusedSeconds(), 5, 3600);
        int centered = clamp(v.centeredSeconds(), Math.min(7200, focused + 1), 7200);
        int deep = clamp(v.deepSeconds(), Math.min(10800, centered + 1), 10800);
        int trans = clamp(v.transcendentSeconds(), Math.min(21600, deep + 1), 21600);
        SERVER.focusedSeconds.set(focused); SERVER.centeredSeconds.set(centered);
        SERVER.deepSeconds.set(deep); SERVER.transcendentSeconds.set(trans);
        SERVER.calmMultiplier.set(clamp(v.calmMultiplier(), 1, 100));
        SERVER.focusedMultiplier.set(clamp(v.focusedMultiplier(), 1, 100));
        SERVER.centeredMultiplier.set(clamp(v.centeredMultiplier(), 1, 100));
        SERVER.deepMultiplier.set(clamp(v.deepMultiplier(), 1, 100));
        SERVER.transcendentMultiplier.set(clamp(v.transcendentMultiplier(), 1, 100));
        SERVER.damageInterrupts.set(v.damageInterrupts());
        SERVER.damageCooldownSeconds.set(clamp(v.damageCooldownSeconds(), 0, 120));
        SERVER.groupMeditation.set(v.groupMeditation());
        SERVER.groupMeditationRadius.set(clamp(v.groupMeditationRadius(), 2, 64));
        SERVER.livingWorldNpcMeditation.set(v.livingWorldNpcMeditation());
        SERVER.statBreakthroughEnabled.set(v.statBreakthroughEnabled());
        SERVER.statBreakthroughChancePercent.set(Math.max(0.0D, Math.min(100.0D, v.statBreakthroughChancePercent())));
        SERVER.statBreakthroughRollSeconds.set(clamp(v.statBreakthroughRollSeconds(), 10, 3600));
        SERVER.statBreakthroughPoints.set(clamp(v.statBreakthroughPoints(), 1, 100));
        SERVER.formMasteryEnabled.set(v.formMasteryEnabled());
        SERVER.deepFormMasteryPerMinute.set(Math.max(0.0D, Math.min(5.0D, v.deepFormMasteryPerMinute())));
        SERVER.transcendentFormMasteryPerMinute.set(Math.max(0.0D, Math.min(5.0D, v.transcendentFormMasteryPerMinute())));
        SERVER.levitationHeight.set(Math.max(0.0D, Math.min(2.0D, v.levitationHeight())));
        SERVER.particles.set(v.particles());
        SERVER.particleDensityPercent.set(clamp(v.particleDensityPercent(), 10, 200));
        SERVER.milestoneSounds.set(v.milestoneSounds());
        SERVER.enabled.save();
    }

    public static ServerSnapshot serverDefaults() {
        return new ServerSnapshot(true, true, 100, 5, 60, 180, 300, 600,
                1, 2, 3, 4, 5, true, 8, true, 12, true, true, 1.0D, 60, 1,
                true, 0.050D, 0.100D, 0.35D, true, 100, true);
    }

    public static ClientSnapshot clientSnapshot() {
        return new ClientSnapshot(CLIENT.focusHud.get(), CLIENT.sessionSummary.get(), CLIENT.sessionSummarySeconds.get(),
                CLIENT.nativeDmzMeditationAnimation.get(), CLIENT.hudTopOffset.get(), CLIENT.auraIntensityPercent.get(),
                CLIENT.focusSealEnabled.get(), CLIENT.focusSealIntensityPercent.get(), CLIENT.focusSealRadiusPercent.get(),
                CLIENT.firstPersonAura.get(), CLIENT.stageTransitionEffects.get(), CLIENT.npcMeditationEffects.get());
    }

    public static void applyClient(ClientSnapshot v) {
        if (v == null) return;
        CLIENT.focusHud.set(v.focusHud()); CLIENT.sessionSummary.set(v.sessionSummary());
        // The summary duration is intentionally no longer exposed in the GUI. Keep the established
        // five-second behavior authoritative even for players carrying an older client config.
        CLIENT.sessionSummarySeconds.set(5);
        CLIENT.nativeDmzMeditationAnimation.set(v.nativeAnimation());
        CLIENT.hudTopOffset.set(clamp(v.hudTopOffset(), 0, 240));
        CLIENT.auraIntensityPercent.set(clamp(v.auraIntensityPercent(), 0, 250));
        CLIENT.focusSealEnabled.set(v.focusSealEnabled()); CLIENT.groundFocusCircle.set(v.focusSealEnabled());
        CLIENT.focusSealIntensityPercent.set(clamp(v.focusSealIntensityPercent(), 25, 200));
        CLIENT.focusSealRadiusPercent.set(clamp(v.focusSealRadiusPercent(), 60, 160));
        CLIENT.firstPersonAura.set(v.firstPersonAura()); CLIENT.stageTransitionEffects.set(v.stageTransitionEffects());
        CLIENT.npcMeditationEffects.set(v.npcMeditationEffects());
        CLIENT.focusHud.save();
    }

    public static ClientSnapshot clientDefaults() {
        return new ClientSnapshot(true, true, 5, true, 12, 100, true, 100, 100, true, true, true);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public record ServerSnapshot(boolean enabled, boolean tpRewardsEnabled, int tpRewardScalePercent,
                                 int rewardIntervalSeconds, int focusedSeconds, int centeredSeconds, int deepSeconds,
                                 int transcendentSeconds, int calmMultiplier, int focusedMultiplier, int centeredMultiplier,
                                 int deepMultiplier, int transcendentMultiplier, boolean damageInterrupts, int damageCooldownSeconds,
                                 boolean groupMeditation, int groupMeditationRadius, boolean livingWorldNpcMeditation,
                                 boolean statBreakthroughEnabled, double statBreakthroughChancePercent,
                                 int statBreakthroughRollSeconds, int statBreakthroughPoints, boolean formMasteryEnabled,
                                 double deepFormMasteryPerMinute, double transcendentFormMasteryPerMinute,
                                 double levitationHeight, boolean particles, int particleDensityPercent,
                                 boolean milestoneSounds) {}

    public record ClientSnapshot(boolean focusHud, boolean sessionSummary, int sessionSummarySeconds,
                                 boolean nativeAnimation, int hudTopOffset, int auraIntensityPercent,
                                 boolean focusSealEnabled, int focusSealIntensityPercent, int focusSealRadiusPercent,
                                 boolean firstPersonAura, boolean stageTransitionEffects, boolean npcMeditationEffects) {}

    public static void setFastTesting(boolean enabled) { FAST_TESTING = enabled; }
    public static boolean isFastTesting() { return FAST_TESTING; }
    public static int focusedTicks() { return (FAST_TESTING ? 20 : SERVER.focusedSeconds.get()) * 20; }
    public static int centeredTicks() { return (FAST_TESTING ? 45 : Math.max(SERVER.focusedSeconds.get() + 1, SERVER.centeredSeconds.get())) * 20; }
    public static int deepTicks() { return (FAST_TESTING ? 75 : Math.max(Math.max(SERVER.focusedSeconds.get(), SERVER.centeredSeconds.get()) + 1, SERVER.deepSeconds.get())) * 20; }
    public static int transcendentTicks() { return (FAST_TESTING ? 120 : Math.max(Math.max(SERVER.deepSeconds.get(), SERVER.centeredSeconds.get()) + 1, SERVER.transcendentSeconds.get())) * 20; }
}
