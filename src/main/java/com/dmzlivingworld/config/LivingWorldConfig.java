package com.dmzlivingworld.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

/** World-specific Living World settings. Registered as a Forge SERVER config. */
public final class LivingWorldConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue NEARBY_FIGHTER_CAP;
    public static final ForgeConfigSpec.IntValue NEARBY_HOSTILE_CAP;
    public static final ForgeConfigSpec.IntValue MAX_REMEMBERED_DEAD_FIGHTERS;
    public static final ForgeConfigSpec.BooleanValue FACTION_ENCOUNTERS;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_ENCOUNTERS;
    public static final ForgeConfigSpec.BooleanValue RECURRING_FIGHTERS;
    public static final ForgeConfigSpec.IntValue LIVING_PRESENCE_TARGET;
    public static final ForgeConfigSpec.IntValue LIVING_PRESENCE_RADIUS;
    public static final ForgeConfigSpec.IntValue NPC_DESPAWN_PROTECTION_RADIUS;
    public static final ForgeConfigSpec.IntValue FACTION_RESIDENT_CAP;
    public static final ForgeConfigSpec.BooleanValue AUTOMATIC_POWER_SENSING;
    public static final ForgeConfigSpec.BooleanValue WORLD_INCIDENTS;
    public static final ForgeConfigSpec.BooleanValue WORLD_EVENT_ALERTS;
    public static final ForgeConfigSpec.IntValue WORLD_EVENT_ALERT_RADIUS;
    public static final ForgeConfigSpec.BooleanValue SOCIAL_TALK;
    public static final ForgeConfigSpec.IntValue TALK_BASE_GAIN;
    public static final ForgeConfigSpec.IntValue TALK_RELATIONSHIP_CAP;
    public static final ForgeConfigSpec.IntValue TALK_COOLDOWN_MIN_SECONDS;
    public static final ForgeConfigSpec.IntValue TALK_COOLDOWN_MAX_SECONDS;
    public static final ForgeConfigSpec.BooleanValue NPC_SOCIALIZING;
    public static final ForgeConfigSpec.IntValue NPC_CHAT_FREQUENCY_PERCENT;
    public static final ForgeConfigSpec.IntValue NPC_CHAOS_PERCENT;
    public static final ForgeConfigSpec.IntValue NPC_STRENGTH_PERCENT;
    public static final ForgeConfigSpec.DoubleValue LEVEL_MULTIPLIER_PER_SAGA;
    public static final ForgeConfigSpec.DoubleValue MAX_DEFENSE_MITIGATION;
    public static final ForgeConfigSpec.DoubleValue BP_VISUAL_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue CAN_MEDITATION_PROC_SKILL_PROGRESSION;
    public static final ForgeConfigSpec.DoubleValue BRAWLER_MELEE_SHARE, BRAWLER_DEFENSE_SHARE, BRAWLER_KI_SHARE, BRAWLER_HEALTH_SHARE;
    public static final ForgeConfigSpec.DoubleValue MARTIAL_ARTIST_MELEE_SHARE, MARTIAL_ARTIST_DEFENSE_SHARE, MARTIAL_ARTIST_KI_SHARE, MARTIAL_ARTIST_HEALTH_SHARE;
    public static final ForgeConfigSpec.DoubleValue SPEED_FIGHTER_MELEE_SHARE, SPEED_FIGHTER_DEFENSE_SHARE, SPEED_FIGHTER_KI_SHARE, SPEED_FIGHTER_HEALTH_SHARE;
    public static final ForgeConfigSpec.DoubleValue GUARDIAN_MELEE_SHARE, GUARDIAN_DEFENSE_SHARE, GUARDIAN_KI_SHARE, GUARDIAN_HEALTH_SHARE;
    public static final ForgeConfigSpec.DoubleValue KI_SPECIALIST_MELEE_SHARE, KI_SPECIALIST_DEFENSE_SHARE, KI_SPECIALIST_KI_SHARE, KI_SPECIALIST_HEALTH_SHARE;
    public static final ForgeConfigSpec.IntValue NPC_GROWTH_PERCENT;
    public static final ForgeConfigSpec.BooleanValue ATTACK_MINECRAFT_MOBS;
    public static final ForgeConfigSpec.BooleanValue COMPANION_SAGA_HELP;
    public static final ForgeConfigSpec.IntValue EARTH_GUARDIAN_RESPONSE_PERCENT;
    public static final ForgeConfigSpec.IntValue NPC_KI_MODE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NPC_RACE_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue TREAT_RACE_BLACKLIST_AS_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CAN_USE_CLOTHES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue TREAT_DIMENSION_WHITELIST_AS_BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "Dragon Mine Z: Living World — per-world/server settings.",
                "Most important options are also available from L -> Settings. Advanced values remain editable here.")
                .push("world");

        builder.push("population");
        NPC_RACE_BLACKLIST = builder.comment("DMZ race ids excluded from natural Living World NPC generation.")
                .defineListAllowEmpty(List.of("npcRaceBlacklist"), List.of(), LivingWorldConfig::isNonBlankId);
        TREAT_RACE_BLACKLIST_AS_WHITELIST = builder.comment("When true, npcRaceBlacklist becomes the exclusive allowed-race list.")
                .define("treatBlacklistAsWhitelist", false);
        CAN_USE_CLOTHES = builder.comment("Race ids whose NPC appearance may use the shared 22-outfit pool.")
                .defineListAllowEmpty(List.of("canUseClothes"), List.of("human", "saiyan", "namekian", "majin"), LivingWorldConfig::isNonBlankId);
        DIMENSION_WHITELIST = builder.comment("Dimension ids where natural Living World spawning is allowed.")
                .defineListAllowEmpty(List.of("dimensionWhitelist"), List.of("minecraft:overworld", "dragonminez:namek"), LivingWorldConfig::isNonBlankId);
        TREAT_DIMENSION_WHITELIST_AS_BLACKLIST = builder.comment("When true, dimensionWhitelist is treated as a blacklist instead.")
                .define("treatDimensionWhitelistAsBlacklist", false);
        NEARBY_FIGHTER_CAP = builder.comment(
                        "Maximum Living World fighters naturally maintained near each player.",
                        "Large values increase active fighter AI cost; lowering this does not remove fighters who already exist.")
                .defineInRange("nearbyFighterCap", 20, 0, 4096);
        NEARBY_HOSTILE_CAP = builder.comment("Maximum naturally generated hostile Living World fighters near each player.")
                .defineInRange("nearbyHostileCap", 6, 0, 4096);
        MAX_REMEMBERED_DEAD_FIGHTERS = builder.comment(
                        "Maximum number of dead fighter profiles retained for afterlife spawning and Dragon Ball wishes.",
                        "When the limit is exceeded, the oldest retained death is forgotten first.")
                .defineInRange("maxRememberedDeadFighters", 20, 0, 4096);
        FACTION_RESIDENT_CAP = builder.comment("Maximum physical resident cell size that one faction territory can request. Still bounded by the local fighter cap.")
                .defineInRange("factionResidentCap", 15, 4, 512);
        builder.pop();

        builder.push("livingPresence");
        RECURRING_FIGHTERS = builder.comment(
                        "Allow persistent remembered fighters whose simulated lives are nearby to become real loaded NPCs.",
                        "No chunks are force-loaded and the normal local population cap still applies.")
                .define("enabled", true);
        LIVING_PRESENCE_TARGET = builder.comment(
                        "Preferred number of remembered people physically present near a player.",
                        "0 disables natural materialization without deleting memories.")
                .defineInRange("targetRememberedPeople", 2, 0, 128);
        LIVING_PRESENCE_RADIUS = builder.comment("Maximum approximate distance in blocks for a simulated remembered life to qualify for materialization.")
                .defineInRange("materializationRadius", 256, 96, 8192);
        NPC_DESPAWN_PROTECTION_RADIUS = builder.comment(
                        "Distance in blocks around a non-spectator player where loaded Living World fighters are protected from native despawn.",
                        "RC8 default is 288 blocks (3x the former hard-coded 96-block protection).")
                .defineInRange("npcDespawnProtectionRadius", 288, 96, 4096);
        builder.pop();

        builder.push("encounters");
        FACTION_ENCOUNTERS = builder.comment("Allow natural faction patrols/parties and regional faction presence.")
                .define("factionEncounters", true);
        DYNAMIC_ENCOUNTERS = builder.comment("Allow small scenes such as duels, clashes, rescues and ambushes.")
                .define("dynamicEncounters", true);
        WORLD_INCIDENTS = builder.comment("Allow slow history-driven incidents between fighters who already exist.")
                .define("worldIncidents", true);
        WORLD_EVENT_ALERTS = builder.comment("Notify nearby players when a noteworthy physical world event is happening so they can travel to its coordinates.")
                .define("eventAlerts", true);
        WORLD_EVENT_ALERT_RADIUS = builder.comment("Maximum distance in blocks for Living World coordinate/event alerts.")
                .defineInRange("eventAlertRadius", 1400, 128, 32768);
        builder.pop();

        builder.push("social");
        SOCIAL_TALK = builder.comment("Enable the low-pressure Talk interaction that lets strangers naturally become Familiar.")
                .define("talkEnabled", true);
        TALK_BASE_GAIN = builder.comment("Base relationship gain from a successful Talk before personality/social-nature adjustment.")
                .defineInRange("talkBaseGain", 2, 0, 50);
        TALK_RELATIONSHIP_CAP = builder.comment("Maximum personal relationship Talk alone can reach. Shared experiences are required above this value.")
                .defineInRange("talkRelationshipCap", 20, 0, 100);
        TALK_COOLDOWN_MIN_SECONDS = builder.comment("Minimum real gameplay seconds before the same persistent person can grant Talk progress again.")
                .defineInRange("talkCooldownMinSeconds", 120, 10, 86400);
        TALK_COOLDOWN_MAX_SECONDS = builder.comment("Maximum Talk cooldown. Values below the minimum are clamped at runtime.")
                .defineInRange("talkCooldownMaxSeconds", 240, 10, 86400);
        NPC_SOCIALIZING = builder.comment(
                        "Allow idle Living World fighters to have sparse, situation-aware conversations and build lightweight bonds with each other.",
                        "This also allows compatible NPC pairs to occasionally meditate together. It does not create chat spam or a second player relationship currency.")
                .define("npcSocializing", true);
        NPC_CHAT_FREQUENCY_PERCENT = builder.comment(
                        "How often autonomous Living World NPC conversations are attempted.",
                        "100 = the established normal cadence; 0 suppresses autonomous NPC chatter without disabling explicit Talk or authored scene dialogue; higher values make social chatter more frequent.")
                .defineInRange("npcChatFrequencyPercent", 100, 0, 500);
        builder.pop();

        builder.push("behavior");
        NPC_CHAOS_PERCENT = builder.comment(
                        "World activity: controls the pace of ambient NPC life and optional spontaneous fights.",
                        "100 = normal activity; lower values are calmer, higher values are busier.",
                        "Self-defense, explicit duels and intentional story/faction conflicts are unaffected.")
                .defineInRange("npcChaosPercent", 100, 0, 1000);
        NPC_STRENGTH_PERCENT = builder.comment(
                        "Living World fighter strength/difficulty baseline.",
                        "100 = normal current balance; lower values create a weaker population, higher values a stronger one.",
                        "This scales the world-era anchor used for NEW fighters and future earned progression ceilings.",
                        "Changing it does not rewrite the current BP of fighters who already exist and never directly matches NPC BP to the player.")
                .defineInRange("npcStrengthPercent", 100, 25, 1000);
        LEVEL_MULTIPLIER_PER_SAGA = builder.comment(
                        "Effective stat budget per required level of the final quest in the current saga.",
                        "Used from the Saiyan saga onward; default: required level x 5.")
                .defineInRange("levelMultiplierPerSaga", 5.0D, 0.1D, 1000.0D);
        MAX_DEFENSE_MITIGATION = builder.comment(
                        "Maximum fraction of one incoming hit that an NPC's flat Defense can remove.",
                        "0.7 means Defense can absorb at most 70%, so at least 30% always passes through.",
                        "Adaptive defense is not used by Living World NPCs.")
                .defineInRange("maxDefenseMitigation", 0.7D, 0.0D, 0.99D);
        BP_VISUAL_MULTIPLIER = builder.comment(
                        "Visual-only multiplier for Living World NPC Battle Power readings.",
                        "Affects scouters, Ki Sense, the fighter interaction menu and BP comparisons used by fusion.",
                        "Does not affect stats, effective/reference budgets, AI, training or real NPC BP.")
                .defineInRange("BpVisualMultiplier", 1.0D, 0.0D, 1_000_000.0D);
        CAN_MEDITATION_PROC_SKILL_PROGRESSION = builder.comment(
                        "Allow Living World's integrated player meditation to trigger DMZ Skill Progression's mob-defeat skill roll every 10 seconds.",
                        "Each meditation stage uses its configured TP multiplier as the number of reward-roll opportunities.")
                .define("canMeditationProcSkillProgression", true);
        builder.push("archetypeStatDistribution");
        builder.comment(
                "Fraction of the effective stat budget assigned to each real combat attribute.",
                "0.20 means 20%. Values are intentionally not normalized, so their sum may be below or above 1.0.",
                "Health share is added above the vanilla 20 HP baseline.");
        BRAWLER_MELEE_SHARE = share(builder, "brawlerMelee", .20D);
        BRAWLER_DEFENSE_SHARE = share(builder, "brawlerDefense", .10D);
        BRAWLER_KI_SHARE = share(builder, "brawlerKi", .06D);
        BRAWLER_HEALTH_SHARE = share(builder, "brawlerHealth", .64D);
        MARTIAL_ARTIST_MELEE_SHARE = share(builder, "martialArtistMelee", .17D);
        MARTIAL_ARTIST_DEFENSE_SHARE = share(builder, "martialArtistDefense", .14D);
        MARTIAL_ARTIST_KI_SHARE = share(builder, "martialArtistKi", .17D);
        MARTIAL_ARTIST_HEALTH_SHARE = share(builder, "martialArtistHealth", .52D);
        SPEED_FIGHTER_MELEE_SHARE = share(builder, "speedFighterMelee", .15D);
        SPEED_FIGHTER_DEFENSE_SHARE = share(builder, "speedFighterDefense", .08D);
        SPEED_FIGHTER_KI_SHARE = share(builder, "speedFighterKi", .15D);
        SPEED_FIGHTER_HEALTH_SHARE = share(builder, "speedFighterHealth", .62D);
        GUARDIAN_MELEE_SHARE = share(builder, "guardianMelee", .10D);
        GUARDIAN_DEFENSE_SHARE = share(builder, "guardianDefense", .20D);
        GUARDIAN_KI_SHARE = share(builder, "guardianKi", .10D);
        GUARDIAN_HEALTH_SHARE = share(builder, "guardianHealth", .60D);
        KI_SPECIALIST_MELEE_SHARE = share(builder, "kiSpecialistMelee", .06D);
        KI_SPECIALIST_DEFENSE_SHARE = share(builder, "kiSpecialistDefense", .10D);
        KI_SPECIALIST_KI_SHARE = share(builder, "kiSpecialistKi", .20D);
        KI_SPECIALIST_HEALTH_SHARE = share(builder, "kiSpecialistHealth", .64D);
        builder.pop();
        NPC_GROWTH_PERCENT = builder.comment(
                        "Living World fighter earned growth speed.",
                        "100 = normal progression; 0 freezes earned BP/legacy growth; values above 100 accelerate earned progression.",
                        "Applies to training, meditation, jogging and meaningful fights without directly matching NPC BP to the player.")
                .defineInRange("npcGrowthPercent", 100, 0, 1000);
        ATTACK_MINECRAFT_MOBS = builder.comment(
                        "Allow ordinary Living World fighters to initiate combat against Minecraft entities outside authored Living World conflicts.",
                        "Turning this off protects ordinary animals, pets, villagers and monsters from proactive LW combat. A registered companion can still defend its player from a Minecraft attacker; authored LW/faction/story conflicts remain intact.")
                .define("attackMinecraftMobs", true);
        COMPANION_SAGA_HELP = builder.comment(
                        "Allow a fighter travelling with the player to help against Dragon Mine Z saga enemies.",
                        "When disabled, companions still protect the player from Living World fighters and ordinary Minecraft threats.")
                .define("companionSagaHelp", true);
        EARTH_GUARDIAN_RESPONSE_PERCENT = builder.comment(
                        "How frequently the Earth Guardian Corps responds to violence, crime and player-targeted emergencies when its existing response rules otherwise qualify.",
                        "100 = the established normal response frequency; 0 disables optional Corps response rolls; higher values make eligible responses more likely without changing faction reputation requirements or authored conflicts.")
                .defineInRange("earthGuardianResponsePercent", 100, 0, 500);
        builder.pop();

        builder.push("awareness");
        AUTOMATIC_POWER_SENSING = builder.comment("Show automatic nearby-power sensing notifications. Manual /lw sense is unaffected.")
                .define("automaticPowerSensing", true);
        builder.pop();

        builder.push("kiSecurity");
        NPC_KI_MODE = builder.comment(
                        "How Living World NPC Ki attacks interact with blocks. Player damage is unchanged in every Ki-enabled mode.",
                        "GUI order: Normal -> Player Blocks -> Player + World -> Ki Off.",
                        "Stored values preserve older worlds: 0 = Normal; 1 = Player + World; 2 = Ki Off; 3 = Player Blocks.",
                        "Player Blocks protects tracked player-placed blocks but still allows natural terrain destruction.",
                        "Player + World protects all blocks while NPC Ki can still hurt players.")
                .defineInRange("npcKiMode", 1, 0, 3);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    private LivingWorldConfig() {}

    private static ForgeConfigSpec.DoubleValue share(ForgeConfigSpec.Builder builder, String name, double defaultValue) {
        return builder.defineInRange(name, defaultValue, 0.0D, 10.0D);
    }

    private static boolean isNonBlankId(Object value) { return value instanceof String s && !s.isBlank(); }
    public static List<String> npcRaceBlacklist() { return NPC_RACE_BLACKLIST.get().stream().map(String::valueOf).map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList(); }
    public static boolean treatRaceBlacklistAsWhitelist() { return TREAT_RACE_BLACKLIST_AS_WHITELIST.get(); }
    public static List<String> canUseClothes() { return CAN_USE_CLOTHES.get().stream().map(String::valueOf).map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList(); }
    public static List<String> dimensionWhitelist() { return DIMENSION_WHITELIST.get().stream().map(String::valueOf).map(s -> s.toLowerCase(java.util.Locale.ROOT)).toList(); }
    public static boolean treatDimensionWhitelistAsBlacklist() { return TREAT_DIMENSION_WHITELIST_AS_BLACKLIST.get(); }

    /** Retained in the network snapshot for compatibility with older 1.9 release candidates. */
    public static int activityPreset() { return 2; }
    public static int nearbyFighterCap() { return NEARBY_FIGHTER_CAP.get(); }
    public static int nearbyHostileCap() { return Math.min(NEARBY_HOSTILE_CAP.get(), nearbyFighterCap()); }
    public static boolean factionEncounters() { return FACTION_ENCOUNTERS.get(); }
    public static boolean dynamicEncounters() { return DYNAMIC_ENCOUNTERS.get(); }
    public static boolean recurringFighters() { return RECURRING_FIGHTERS.get(); }
    public static int livingPresenceTarget() {
        return LIVING_PRESENCE_TARGET.get();
    }
    public static int livingPresenceRadius() { return LIVING_PRESENCE_RADIUS.get(); }
    public static int npcDespawnProtectionRadius() { return NPC_DESPAWN_PROTECTION_RADIUS.get(); }
    public static int factionResidentCap() { return FACTION_RESIDENT_CAP.get(); }
    public static boolean automaticPowerSensing() { return AUTOMATIC_POWER_SENSING.get(); }
    public static boolean worldIncidents() { return WORLD_INCIDENTS.get(); }
    public static boolean worldEventAlerts() { return WORLD_EVENT_ALERTS.get(); }
    public static int worldEventAlertRadius() { return WORLD_EVENT_ALERT_RADIUS.get(); }
    public static boolean socialTalk() { return SOCIAL_TALK.get(); }
    public static int talkBaseGain() { return TALK_BASE_GAIN.get(); }
    public static int talkRelationshipCap() { return TALK_RELATIONSHIP_CAP.get(); }
    public static int talkCooldownMinSeconds() { return TALK_COOLDOWN_MIN_SECONDS.get(); }
    public static int talkCooldownMaxSeconds() { return Math.max(talkCooldownMinSeconds(), TALK_COOLDOWN_MAX_SECONDS.get()); }
    public static boolean npcSocializing() { return NPC_SOCIALIZING.get(); }
    public static int npcChatFrequencyPercent() { return NPC_CHAT_FREQUENCY_PERCENT.get(); }
    public static double npcChatFrequencyScale() { return npcChatFrequencyPercent() / 100.0D; }

    /**
     * Scale an autonomous chatter probability while preserving the established DEV4 cadence at 100%.
     * Authored/explicit dialogue does not call this helper.
     */
    public static float scaledNpcChatChance(float baseChance) {
        if (baseChance <= 0.0F) return 0.0F;
        double scale = npcChatFrequencyScale();
        if (scale <= 0.0D) return 0.0F;
        return (float)Math.min(1.0D, baseChance * scale);
    }

    /**
     * Scale autonomous chatter cooldowns inversely with the configured frequency.
     * 100% returns the exact pre-2.1 delay; 50% doubles it; 200% halves it.
     */
    public static long scaledNpcChatDelay(long baseDelayTicks) {
        if (baseDelayTicks <= 0L) return 0L;
        double scale = npcChatFrequencyScale();
        if (scale <= 0.0D) return Long.MAX_VALUE / 4L;
        return Math.max(1L, Math.round(baseDelayTicks / scale));
    }
    public static int npcChaosPercent() { return NPC_CHAOS_PERCENT.get(); }
    public static int npcStrengthPercent() { return NPC_STRENGTH_PERCENT.get(); }
    public static double levelMultiplierPerSaga() { return LEVEL_MULTIPLIER_PER_SAGA.get(); }
    public static double maxDefenseMitigation() { return MAX_DEFENSE_MITIGATION.get(); }
    public static double bpVisualMultiplier() { return BP_VISUAL_MULTIPLIER.get(); }
    public static int maxRememberedDeadFighters() { return MAX_REMEMBERED_DEAD_FIGHTERS.get(); }
    public static boolean canMeditationProcSkillProgression() { return CAN_MEDITATION_PROC_SKILL_PROGRESSION.get(); }
    public static double npcStrengthScale() { return npcStrengthPercent() / 100.0D; }
    public static int npcGrowthPercent() { return NPC_GROWTH_PERCENT.get(); }
    public static double npcGrowthScale() { return npcGrowthPercent() / 100.0D; }
    public static boolean attackMinecraftMobs() { return ATTACK_MINECRAFT_MOBS.get(); }
    public static boolean companionSagaHelp() { return COMPANION_SAGA_HELP.get(); }
    public static int earthGuardianResponsePercent() { return EARTH_GUARDIAN_RESPONSE_PERCENT.get(); }
    public static double earthGuardianResponseScale() { return earthGuardianResponsePercent() / 100.0D; }
    public static double ambientConflictRoll() {
        // 100% is the normal frequency. This multiplier gates optional free-roaming
        // aggression; explicit targets, self-defense and intentional conflicts bypass it.
        return 0.002D * (npcChaosPercent() / 100.0D);
    }
    public static int npcKiMode() { return NPC_KI_MODE.get(); }

    public static int naturalCheckIntervalTicks() {
        // Below 100%, fewer population opportunities are evaluated. At and above 100%,
        // keep a stable cadence and scale the NUMBER of opportunities instead of squeezing
        // one probability toward 100%; this makes 500% genuinely feel about five times busier.
        int chaos = npcChaosPercent();
        if (chaos <= 0) return 1200;
        if (chaos >= 100) return 400;
        return clamp((int)Math.round(400.0D * 100.0D / Math.max(10, chaos)), 400, 4000);
    }

    public static int naturalActivityAttempts() {
        int chaos = npcChaosPercent();
        if (chaos <= 0) return 0;
        return clamp((int)Math.ceil(chaos / 100.0D), 1, 10);
    }

    public static double naturalSpawnRoll() {
        int chaos = npcChaosPercent();
        if (chaos <= 0) return 0.0D;
        // Each opportunity uses the 100% baseline roll. Higher activity is represented by
        // additional opportunities rather than a saturated single roll.
        return 0.40D * Math.min(1.0D, chaos / 100.0D);
    }

    public static double ambientActivityStartChance() {
        int chaos = npcChaosPercent();
        if (chaos <= 0) return 0.0D;
        return Math.min(0.90D, 0.20D * Math.max(0.10D, chaos / 100.0D));
    }

    public static int ambientActivityDelay(int normalTicks) {
        int chaos = npcChaosPercent();
        if (chaos <= 0) return Math.max(normalTicks, 2400);
        double scale = Math.max(0.10D, chaos / 100.0D);
        return clamp((int)Math.round(normalTicks / scale), 120, 12000);
    }

    public static Snapshot snapshot() {
        return new Snapshot(activityPreset(), nearbyFighterCap(), nearbyHostileCap(), factionEncounters(),
                dynamicEncounters(), recurringFighters(), LIVING_PRESENCE_TARGET.get(), livingPresenceRadius(), factionResidentCap(),
                automaticPowerSensing(), worldIncidents(), worldEventAlerts(), worldEventAlertRadius(),
                socialTalk(), talkBaseGain(), talkRelationshipCap(), talkCooldownMinSeconds(), talkCooldownMaxSeconds(),
                npcSocializing(), npcChaosPercent(), companionSagaHelp(), npcKiMode(), npcStrengthPercent(), npcGrowthPercent(), attackMinecraftMobs(),
                npcChatFrequencyPercent(), earthGuardianResponsePercent(), maxRememberedDeadFighters(), npcDespawnProtectionRadius(),
                levelMultiplierPerSaga(), maxDefenseMitigation(), bpVisualMultiplier(), canMeditationProcSkillProgression(),
                npcRaceBlacklist(), treatRaceBlacklistAsWhitelist(), canUseClothes(), dimensionWhitelist(), treatDimensionWhitelistAsBlacklist(),
                archetypeShares());
    }

    public static void apply(Snapshot value) {
        if (value == null) return;
        int nearby = clamp(value.nearbyFighterCap(), 0, 4096);
        NEARBY_FIGHTER_CAP.set(nearby);
        NEARBY_HOSTILE_CAP.set(clamp(value.nearbyHostileCap(), 0, nearby));
        FACTION_ENCOUNTERS.set(value.factionEncounters());
        DYNAMIC_ENCOUNTERS.set(value.dynamicEncounters());
        RECURRING_FIGHTERS.set(value.recurringFighters());
        LIVING_PRESENCE_TARGET.set(clamp(value.livingPresenceTargetBase(), 0, 128));
        LIVING_PRESENCE_RADIUS.set(clamp(value.livingPresenceRadius(), 96, 8192));
        FACTION_RESIDENT_CAP.set(clamp(value.factionResidentCap(), 4, 512));
        AUTOMATIC_POWER_SENSING.set(value.automaticPowerSensing());
        WORLD_INCIDENTS.set(value.worldIncidents());
        WORLD_EVENT_ALERTS.set(value.worldEventAlerts());
        WORLD_EVENT_ALERT_RADIUS.set(clamp(value.worldEventAlertRadius(), 128, 32768));
        SOCIAL_TALK.set(value.socialTalk());
        TALK_BASE_GAIN.set(clamp(value.talkBaseGain(), 0, 50));
        TALK_RELATIONSHIP_CAP.set(clamp(value.talkRelationshipCap(), 0, 100));
        int min = clamp(value.talkCooldownMinSeconds(), 10, 86400);
        TALK_COOLDOWN_MIN_SECONDS.set(min);
        TALK_COOLDOWN_MAX_SECONDS.set(clamp(value.talkCooldownMaxSeconds(), min, 86400));
        NPC_SOCIALIZING.set(value.npcSocializing());
        NPC_CHAT_FREQUENCY_PERCENT.set(clamp(value.npcChatFrequencyPercent(), 0, 500));
        NPC_CHAOS_PERCENT.set(clamp(value.npcChaosPercent(), 0, 1000));
        NPC_STRENGTH_PERCENT.set(clamp(value.npcStrengthPercent(), 25, 1000));
        NPC_GROWTH_PERCENT.set(clamp(value.npcGrowthPercent(), 0, 1000));
        ATTACK_MINECRAFT_MOBS.set(value.attackMinecraftMobs());
        COMPANION_SAGA_HELP.set(value.companionSagaHelp());
        EARTH_GUARDIAN_RESPONSE_PERCENT.set(clamp(value.earthGuardianResponsePercent(), 0, 500));
        NPC_KI_MODE.set(clamp(value.npcKiMode(), 0, 3));
        MAX_REMEMBERED_DEAD_FIGHTERS.set(clamp(value.maxRememberedDeadFighters(), 0, 4096));
        NPC_DESPAWN_PROTECTION_RADIUS.set(clamp(value.npcDespawnProtectionRadius(), 96, 4096));
        LEVEL_MULTIPLIER_PER_SAGA.set(clamp(value.levelMultiplierPerSaga(), .1D, 1000D));
        MAX_DEFENSE_MITIGATION.set(clamp(value.maxDefenseMitigation(), 0D, .99D));
        BP_VISUAL_MULTIPLIER.set(clamp(value.bpVisualMultiplier(), 0D, 1_000_000D));
        CAN_MEDITATION_PROC_SKILL_PROGRESSION.set(value.canMeditationProcSkillProgression());
        NPC_RACE_BLACKLIST.set(List.copyOf(value.npcRaceBlacklist()));
        TREAT_RACE_BLACKLIST_AS_WHITELIST.set(value.treatRaceBlacklistAsWhitelist());
        CAN_USE_CLOTHES.set(List.copyOf(value.canUseClothes()));
        DIMENSION_WHITELIST.set(List.copyOf(value.dimensionWhitelist()));
        TREAT_DIMENSION_WHITELIST_AS_BLACKLIST.set(value.treatDimensionWhitelistAsBlacklist());
        applyArchetypeShares(value.archetypeShares());
        // ConfigValue#set updates the loaded Forge config in memory; explicitly save the
        // shared SERVER config after a GUI edit so world settings survive a restart.
        NEARBY_FIGHTER_CAP.save();
    }

    public static Snapshot defaults() {
        return new Snapshot(2, 20, 6, true, true, true, 2, 256, 15,
                true, true, true, 1400, true, 2, 20, 120, 240, true, 100, true, 1, 100, 100, true, 100, 100,
                20, 288, 5D, .7D, 1D, true, List.of(), false,
                List.of("human", "saiyan", "namekian", "majin"), List.of("minecraft:overworld", "dragonminez:namek"), false,
                List.of(.20,.10,.06,.64, .17,.14,.17,.52, .15,.08,.15,.62, .10,.20,.10,.60, .06,.10,.20,.64));
    }

    private static List<Double> archetypeShares() {
        return List.of(BRAWLER_MELEE_SHARE.get(), BRAWLER_DEFENSE_SHARE.get(), BRAWLER_KI_SHARE.get(), BRAWLER_HEALTH_SHARE.get(),
                MARTIAL_ARTIST_MELEE_SHARE.get(), MARTIAL_ARTIST_DEFENSE_SHARE.get(), MARTIAL_ARTIST_KI_SHARE.get(), MARTIAL_ARTIST_HEALTH_SHARE.get(),
                SPEED_FIGHTER_MELEE_SHARE.get(), SPEED_FIGHTER_DEFENSE_SHARE.get(), SPEED_FIGHTER_KI_SHARE.get(), SPEED_FIGHTER_HEALTH_SHARE.get(),
                GUARDIAN_MELEE_SHARE.get(), GUARDIAN_DEFENSE_SHARE.get(), GUARDIAN_KI_SHARE.get(), GUARDIAN_HEALTH_SHARE.get(),
                KI_SPECIALIST_MELEE_SHARE.get(), KI_SPECIALIST_DEFENSE_SHARE.get(), KI_SPECIALIST_KI_SHARE.get(), KI_SPECIALIST_HEALTH_SHARE.get());
    }

    private static void applyArchetypeShares(List<Double> values) {
        if (values == null || values.size() != 20) return;
        ForgeConfigSpec.DoubleValue[] targets = {BRAWLER_MELEE_SHARE,BRAWLER_DEFENSE_SHARE,BRAWLER_KI_SHARE,BRAWLER_HEALTH_SHARE,
                MARTIAL_ARTIST_MELEE_SHARE,MARTIAL_ARTIST_DEFENSE_SHARE,MARTIAL_ARTIST_KI_SHARE,MARTIAL_ARTIST_HEALTH_SHARE,
                SPEED_FIGHTER_MELEE_SHARE,SPEED_FIGHTER_DEFENSE_SHARE,SPEED_FIGHTER_KI_SHARE,SPEED_FIGHTER_HEALTH_SHARE,
                GUARDIAN_MELEE_SHARE,GUARDIAN_DEFENSE_SHARE,GUARDIAN_KI_SHARE,GUARDIAN_HEALTH_SHARE,
                KI_SPECIALIST_MELEE_SHARE,KI_SPECIALIST_DEFENSE_SHARE,KI_SPECIALIST_KI_SHARE,KI_SPECIALIST_HEALTH_SHARE};
        for (int i=0;i<targets.length;i++) targets[i].set(clamp(values.get(i), 0D, 10D));
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public record Snapshot(int activityPreset, int nearbyFighterCap, int nearbyHostileCap,
                           boolean factionEncounters, boolean dynamicEncounters, boolean recurringFighters,
                           int livingPresenceTargetBase, int livingPresenceRadius, int factionResidentCap,
                           boolean automaticPowerSensing, boolean worldIncidents,
                           boolean worldEventAlerts, int worldEventAlertRadius,
                           boolean socialTalk, int talkBaseGain, int talkRelationshipCap,
                           int talkCooldownMinSeconds, int talkCooldownMaxSeconds,
                           boolean npcSocializing, int npcChaosPercent, boolean companionSagaHelp, int npcKiMode,
                           int npcStrengthPercent, int npcGrowthPercent, boolean attackMinecraftMobs,
                           int npcChatFrequencyPercent, int earthGuardianResponsePercent,
                           int maxRememberedDeadFighters, int npcDespawnProtectionRadius,
                           double levelMultiplierPerSaga, double maxDefenseMitigation, double bpVisualMultiplier,
                           boolean canMeditationProcSkillProgression, List<String> npcRaceBlacklist,
                           boolean treatRaceBlacklistAsWhitelist, List<String> canUseClothes,
                           List<String> dimensionWhitelist, boolean treatDimensionWhitelistAsBlacklist,
                           List<Double> archetypeShares) {}
}
