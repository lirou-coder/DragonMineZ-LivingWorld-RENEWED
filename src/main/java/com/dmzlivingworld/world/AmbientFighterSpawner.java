package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.entity.LWEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** Lightweight Earth + Namek encounter population, not vanilla biome spawn injection. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AmbientFighterSpawner {
    private static final double DYNAMIC_ENCOUNTER_SHARE = 0.25D;
    private static final double FACTION_ENCOUNTER_SHARE = 0.38D;
    private static final double LOCAL_RADIUS = 160.0D;
    private static final int MIN_DISTANCE = 38;
    private static final int MAX_DISTANCE = 72;

    private AmbientFighterSpawner() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // The organization simulation is shared through overworld SavedData. Tick its
        // scheduler at most once per server tick (while a supported world is active).
        boolean organizationsTicked = false;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            if (!LivingWorldDimensions.isSupported(level)) continue;
            if (!organizationsTicked) {
                FactionWorldData.get(level).tickOrganizations(level);
                organizationsTicked = true;
            }
            if (player.isSpectator() || player.isCreative()) continue;
            // Active gravity chambers are deliberate training interiors. Ambient/faction population
            // waits outside instead of materializing an attacker in the player's sealed room.
            if (GravityChamberSafety.isPlayerInsideActiveChamber(player)) continue;

            int checkInterval = LivingWorldConfig.naturalCheckIntervalTicks();
            long gameTime = level.getGameTime();
            int stagger = Math.floorMod(player.getUUID().hashCode(), checkInterval);
            if (Math.floorMod(gameTime + stagger, checkInterval) != 0) continue;

            int attempts = LivingWorldConfig.naturalActivityAttempts();
            if (attempts <= 0) continue;
            RandomSource random = player.getRandom();
            for (int opportunity = 0; opportunity < attempts; opportunity++) {
                tryNaturalOpportunity(player, level, random);
            }
        }
    }

    /** One population opportunity. Re-counting each pass keeps high activity under the same hard caps. */
    private static void tryNaturalOpportunity(ServerPlayer player, ServerLevel level, RandomSource random) {
        int localCap = LivingWorldConfig.nearbyFighterCap();
        if (localCap <= 0) return;
        int localBadCap = LivingWorldConfig.nearbyHostileCap();
        // Horizontal population radius remains local, while a much larger Y envelope keeps
        // mountain/aerial fighters from becoming invisible to the cap simply because of altitude.
        List<AmbientFighterEntity> nearby = level.getEntitiesOfClass(
                AmbientFighterEntity.class,
                player.getBoundingBox().inflate(LOCAL_RADIUS, 512.0D, LOCAL_RADIUS)
        );
        if (nearby.size() >= localCap) return;

        int capacity = localCap - nearby.size();
        long badCount = nearby.stream().filter(f -> f.getAlignment() == FighterAlignment.BAD).count();
        int remainingBadSlots = localBadCap - (int) badCount;

        WorldFaction regionalFaction = FactionManager.factionsForRealm(level).stream()
                .filter(f -> {
                    double dx = f.roamX() - player.getX();
                    double dz = f.roamZ() - player.getZ();
                    return dx * dx + dz * dz <= (double) f.roamRadius() * f.roamRadius();
                })
                .min(java.util.Comparator.comparingDouble(f -> {
                    double dx = f.roamX() - player.getX();
                    double dz = f.roamZ() - player.getZ();
                    return dx * dx + dz * dz;
                })).orElse(null);
        if (LivingWorldConfig.factionEncounters() && regionalFaction != null && capacity >= 4
                && FactionEncounterManager.ensureRegionalPresence(player, regionalFaction, false, capacity)) {
            return;
        }

        int rememberedTarget = LivingWorldConfig.livingPresenceTarget();
        long loadedRemembered = nearby.stream().filter(f -> f.isRememberedFor(player)).count();
        if (LivingWorldConfig.recurringFighters() && capacity >= 1 && loadedRemembered < rememberedTarget) {
            loadedRemembered = level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(LivingWorldConfig.livingPresenceRadius(), 512.0D,
                            LivingWorldConfig.livingPresenceRadius()),
                    f -> f.isAlive() && f.isRememberedFor(player)).size();
            if (loadedRemembered < rememberedTarget && FighterMemoryManager.trySpawnRecurring(player, false)) return;
        }

        if (random.nextDouble() >= LivingWorldConfig.naturalSpawnRoll()) return;

        if (LivingWorldConfig.factionEncounters() && capacity >= 2 && random.nextDouble() < FACTION_ENCOUNTER_SHARE) {
            if (FactionEncounterManager.trySpawnNatural(player, capacity)) return;
        }

        double chaosSceneShare = Math.min(0.90D,
                DYNAMIC_ENCOUNTER_SHARE * Math.max(0.25D, LivingWorldConfig.npcChaosPercent() / 100.0D));
        if (LivingWorldConfig.dynamicEncounters() && capacity >= 2 && random.nextDouble() < chaosSceneShare) {
            if (DynamicEncounterManager.trySpawnNaturalEncounter(player, capacity, remainingBadSlots)) return;
        }

        FighterAlignment alignment = FighterAlignment.roll(random);
        if (alignment == FighterAlignment.BAD && remainingBadSlots <= 0) {
            alignment = random.nextBoolean() ? FighterAlignment.GOOD : FighterAlignment.NEUTRAL;
        }

        AmbientFighterEntity spawned = spawnNearPlayer(player, alignment, FighterRank.roll(random), false);
        if (spawned != null && alignment != FighterAlignment.BAD && random.nextFloat() < 0.20F) {
            spawned.setNonCombatant(true);
            if (spawned.getSpeech().isEmpty() && random.nextFloat() < 0.25F) spawned.speak("Just passing through.", 42);
        }
    }

    public static AmbientFighterEntity spawnNearPlayer(ServerPlayer player, FighterAlignment alignment,
                                                         FighterRank rank, boolean closeForDebug) {
        return spawnNearPlayer(player, alignment, rank, null, closeForDebug);
    }

    public static AmbientFighterEntity spawnNearPlayer(ServerPlayer player, FighterAlignment alignment,
                                                         FighterRank rank, FighterPersonality personality,
                                                         boolean closeForDebug) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return null;
        if (GravityChamberSafety.isPlayerInsideActiveChamber(player)) return null;
        int min = closeForDebug ? 7 : MIN_DISTANCE;
        int max = closeForDebug ? 13 : MAX_DISTANCE;
        BlockPos pos = closeForDebug
                ? findSafeGroundAround(level, player.blockPosition(), player.getRandom(), min, max, 18)
                : findSafeGroundAroundSeparated(level, player.blockPosition(), player.getRandom(), min, max, 28, 16.0D);
        if (pos == null) return null;
        return spawnAt(level, pos, alignment, rank, personality, player.getRandom());
    }

    public static AmbientFighterEntity spawnNearPlayer(ServerPlayer player, FighterAlignment alignment, boolean debug) {
        return spawnNearPlayer(player, alignment, FighterRank.roll(player.getRandom()), debug);
    }

    /** Finds a safe central point for a multi-fighter scene. */
    public static BlockPos findEncounterAnchor(ServerPlayer player, boolean debug) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return null;
        if (GravityChamberSafety.isPlayerInsideActiveChamber(player)) return null;
        int min = debug ? 9 : MIN_DISTANCE;
        int max = debug ? 15 : 68;
        return debug
                ? findSafeGroundAround(level, player.blockPosition(), player.getRandom(), min, max, 24)
                : findSafeGroundAroundSeparated(level, player.blockPosition(), player.getRandom(), min, max, 36, 26.0D);
    }

    /** Places a fighter a few blocks around an encounter anchor. */
    public static AmbientFighterEntity spawnAroundAnchor(ServerPlayer player, BlockPos anchor,
                                                          FighterAlignment alignment, FighterRank rank,
                                                          FighterPersonality personality, int minRadius, int maxRadius) {
        if (!(player.level() instanceof ServerLevel level) || anchor == null) return null;
        BlockPos pos = findSafeGroundAround(level, anchor, player.getRandom(), minRadius, maxRadius, 14);
        if (pos == null) pos = anchor;
        return spawnAt(level, pos, alignment, rank, personality, player.getRandom());
    }

    public static AmbientFighterEntity spawnAt(ServerLevel level, BlockPos pos, FighterAlignment alignment,
                                                 FighterRank rank, FighterPersonality personality,
                                                 RandomSource random) {
        return spawnAt(level, pos, alignment, rank, personality, null, null, random);
    }

    public static AmbientFighterEntity spawnAt(ServerLevel level, BlockPos pos, FighterAlignment alignment,
                                                 FighterRank rank, FighterPersonality personality,
                                                 FighterRace race, FighterArchetype archetype,
                                                 RandomSource random) {
        if (!level.getWorldBorder().isWithinBounds(pos) || !isUsableGround(level, pos)) return null;

        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        FighterPersonality resolvedPersonality = personality == null
                ? FighterPersonality.roll(random, alignment) : personality;
        FighterRace resolvedRace = race == null ? rollRaceForLevel(level, random) : race;
        FighterArchetype resolvedArchetype = archetype == null ? FighterArchetype.roll(random, rank) : archetype;
        fighter.initializeAs(alignment, rank, resolvedPersonality, resolvedRace, resolvedArchetype);
        if (!level.noCollision(fighter)) return null;

        level.addFreshEntity(fighter);
        return fighter;
    }


    public static AmbientFighterEntity spawnRemembered(ServerPlayer player, CompoundTag profile, UUID recordId,
                                                         int encounters, int relationship, boolean rescued,
                                                         boolean closeForDebug) {
        return spawnRememberedAt(player, profile, recordId, encounters, relationship, rescued, null, closeForDebug);
    }

    /**
     * Re-instantiates a remembered person near their simulated whereabouts. A debug summon may
     * deliberately ignore those whereabouts and place them close to the player instead.
     */
    public static AmbientFighterEntity spawnRememberedAt(ServerPlayer player, CompoundTag profile, UUID recordId,
                                                           int encounters, int relationship, boolean rescued,
                                                           BlockPos preferred, boolean closeForDebug) {
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) return null;
        // Dead-record tombstones are authoritative across every materialization path, including
        // debug recurrence and companion recovery. A historical person must never be recreated.
        if (recordId != null && FighterLegacyWorldData.get(level).isDeadRecord(recordId)) return null;
        int min = closeForDebug ? 8 : 0;
        int max = closeForDebug ? 14 : 18;
        BlockPos center = closeForDebug || preferred == null ? player.blockPosition() : preferred;
        BlockPos pos = closeForDebug
                ? findSafeGroundAround(level, center, player.getRandom(), min, max, 28)
                : PhysicalContinuityManager.chooseArrivalPoint(player, profile, preferred, recordId);
        if (pos == null && !closeForDebug) {
            // Keep a final safe fallback inside the player's loaded area, but still outside
            // the immediate interaction radius so a remembered person does not pop in beside you.
            pos = findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 72, 112, 28);
        }
        if (pos == null) return null;
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getRandom().nextFloat() * 360.0F, 0.0F);
        fighter.initializeFromMemory(profile);
        if (!closeForDebug && profile.getBoolean("FlightUnlocked")) {
            int terrain = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            double arrivalY = Math.max(pos.getY() + 10.0D, terrain + 12.0D);
            fighter.moveTo(pos.getX() + 0.5D, arrivalY, pos.getZ() + 0.5D, fighter.getYRot(), 0.0F);
        }
        fighter.bindMemory(player.getUUID(), recordId, encounters, relationship, rescued);
        if (!level.noCollision(fighter)) return null;
        level.addFreshEntity(fighter);
        return fighter;
    }


    /**
     * Explicit player action: instantiate the remembered identity at its simulated life location.
     * Unlike ordinary recurrence this does not route the fighter into the player's local arrival ring.
     */
    public static AmbientFighterEntity spawnRememberedSignalAt(ServerPlayer player, CompoundTag profile, UUID recordId,
                                                                int encounters, int relationship, boolean rescued,
                                                                BlockPos life) {
        if (player == null || life == null || !(player.level() instanceof ServerLevel level)
                || !LivingWorldDimensions.isSupported(level)) return null;
        if (recordId != null && FighterLegacyWorldData.get(level).isDeadRecord(recordId)) return null;
        // Instant Transmission is an intentional lock-on, so synchronously load only the destination
        // chunk required for that action. Ambient simulation still never force-loads remote chunks.
        level.getChunkAt(life);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, life.getX(), life.getZ());
        BlockPos center = new BlockPos(life.getX(), surfaceY, life.getZ());
        BlockPos pos = findSafeGroundAround(level, center, player.getRandom(), 0, 18, 48);
        if (pos == null) pos = center;
        if (!level.getWorldBorder().isWithinBounds(pos) || !isUsableGround(level, pos)) return null;

        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getRandom().nextFloat() * 360.0F, 0.0F);
        fighter.initializeFromMemory(profile);
        fighter.bindMemory(player.getUUID(), recordId, encounters, relationship, rescued);
        if (!level.noCollision(fighter)) return null;
        level.addFreshEntity(fighter);
        return fighter;
    }

    /** Recreates a non-remembered travelling companion from its persisted fighter profile. */
    public static AmbientFighterEntity spawnProfileNearPlayer(ServerPlayer player, CompoundTag profile) {
        if (player == null || profile == null || profile.isEmpty() || !(player.level() instanceof ServerLevel level)) return null;
        BlockPos pos = findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 5, 10, 40);
        if (pos == null) return null;
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                player.getRandom().nextFloat() * 360.0F, 0.0F);
        fighter.initializeFromMemory(profile);
        fighter.setPersistenceRequired();
        if (!level.noCollision(fighter)) return null;
        level.addFreshEntity(fighter);
        return fighter;
    }

    public static FighterRace rollRaceForLevel(ServerLevel level, RandomSource random) {
        if (LivingWorldDimensions.realm(level) == FactionRealm.NAMEK) {
            int roll = random.nextInt(100);
            if (roll < 58) return FighterRace.NAMEKIAN;
            if (roll < 70) return FighterRace.HUMAN;
            if (roll < 82) return FighterRace.SAIYAN;
            if (roll < 89) return FighterRace.MAJIN;
            if (roll < 95) return FighterRace.FROST_DEMON;
            return FighterRace.BIO_ANDROID;
        }
        return FighterRace.roll(random);
    }

    public static BlockPos findSafeGroundAround(ServerLevel level, BlockPos center, RandomSource random,
                                                  int minDistance, int maxDistance, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = Mth.nextInt(random, Math.max(0, minDistance), Math.max(minDistance, maxDistance));
            int x = Mth.floor(center.getX() + 0.5D + Math.cos(angle) * distance);
            int z = Mth.floor(center.getZ() + 0.5D + Math.sin(angle) * distance);

            BlockPos rough = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(rough)) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(spawnPos)) continue;
            if (!isUsableGround(level, spawnPos)) continue;
            return spawnPos;
        }
        return null;
    }

    /** Finds ordinary spawn ground while leaving visual breathing room from existing Living World people. */
    public static BlockPos findSafeGroundAroundSeparated(ServerLevel level, BlockPos center, RandomSource random,
                                                           int minDistance, int maxDistance, int attempts, double minSeparation) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = Mth.nextInt(random, Math.max(0, minDistance), Math.max(minDistance, maxDistance));
            int x = Mth.floor(center.getX() + 0.5D + Math.cos(angle) * distance);
            int z = Mth.floor(center.getZ() + 0.5D + Math.sin(angle) * distance);
            BlockPos rough = new BlockPos(x, center.getY(), z);
            if (!level.hasChunkAt(rough)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos spawnPos = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(spawnPos) || !isUsableGround(level, spawnPos)) continue;
            if (minSeparation > 0.0D && !level.getEntitiesOfClass(AmbientFighterEntity.class,
                    new AABB(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                            spawnPos.getX() + 1.0D, spawnPos.getY() + 2.0D, spawnPos.getZ() + 1.0D)
                            .inflate(minSeparation, 96.0D, minSeparation), AmbientFighterEntity::isAlive).isEmpty()) continue;
            return spawnPos;
        }
        return null;
    }

    public static boolean isUsableGround(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        if (GravityChamberSafety.isInsideActiveChamber(level, pos)) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }
}
