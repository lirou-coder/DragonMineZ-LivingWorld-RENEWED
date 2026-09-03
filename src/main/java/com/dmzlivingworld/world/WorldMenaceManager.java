package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRace;
import com.dmzlivingworld.entity.FighterRank;
import com.dmzlivingworld.entity.LWEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Singleton recurring Herobrine easter-egg fighter and World Menace journal source. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldMenaceManager {
    public static final String HEROBRINE_TAG = "LWWorldMenaceHerobrine";
    private static final int MIN_RETURN = 48_000; // 2 Minecraft days
    private static final int RETURN_JITTER = 72_001; // +0..3 days
    private static final String MENACE_STATE = "LWHerobrinePresence";
    private static final String NEXT_WATCH = "LWHerobrineNextWatch";
    private static final String PLAYER_SPOTTED = "LWWorldMenaceSpotted";
    private static final String PLAYER_SPOTTED_PROFILE = "LWWorldMenaceSpottedProfile";
    private static final String PLAYER_SPOTTED_AT = "LWWorldMenaceSpottedAt";
    private static final String PLAYER_FIRST_SPOTTED_AT = "LWWorldMenaceFirstSpottedAt";
    private static final String RETALIATE_PLAYER = "LWHerobrineRetaliatePlayer";
    private static final String RETALIATE_UNTIL = "LWHerobrineRetaliateUntil";
    private static final String PLAYER_INSPECTIONS = "LWHerobrineInspections";
    private static final String PLAYER_SIGHTINGS = "LWHerobrineSightings";
    private static final String PLAYER_LAST_MENACE_X = "LWHerobrineLastX";
    private static final String PLAYER_LAST_MENACE_Y = "LWHerobrineLastY";
    private static final String PLAYER_LAST_MENACE_Z = "LWHerobrineLastZ";
    private static final String INSPECTION_RELOCATE_AT = "LWHerobrineInspectionRelocateAt";
    private static final String INSPECTION_RELOCATE_PLAYER = "LWHerobrineInspectionRelocatePlayer";
    private static final String DEBUG_HOLD_UNTIL = "LWHerobrineDebugObservationHoldUntil";
    private static final Map<UUID, WatchSession> WATCHES = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long lastSingletonRecoveryCheck = Long.MIN_VALUE;

    private static final class WatchSession {
        final UUID playerId;
        final long endsAt;
        final boolean shadowing;
        long noticedAt;
        long lastObservedAt;
        long lingerUntil;
        long nextShadowStep;
        int shadowMoves;
        Vec3 lastPlayerPos;
        boolean omenSent;
        boolean hadLineOfSight;
        long observedSince;
        long closeDisappearAt;
        long nextPositionAudit;
        WatchSession(UUID playerId, long endsAt, boolean shadowing, long now, Vec3 playerPos) {
            this.playerId = playerId; this.endsAt = endsAt; this.shadowing = shadowing;
            this.nextShadowStep = now + 75L; this.lastPlayerPos = playerPos;
            this.nextPositionAudit = now;
        }
    }

    private WorldMenaceManager() {}

    public static boolean isHerobrine(AmbientFighterEntity fighter) {
        return fighter != null && (fighter.getPersistentData().getBoolean(HEROBRINE_TAG)
                || "Herobrine".equals(fighter.getFighterName()));
    }

    /** Shared social/People/IT gate for every unique recurring World Menace. */
    public static boolean isWorldMenace(AmbientFighterEntity fighter) {
        return isHerobrine(fighter) || RedRibbonExperimentManager.isExperiment(fighter);
    }

    public static boolean isWorldMenaceProfile(CompoundTag profile) {
        if (profile == null) return false;
        return profile.getBoolean(HEROBRINE_TAG)
                || "Herobrine".equalsIgnoreCase(profile.getString("Name"))
                || RedRibbonExperimentManager.isExperimentProfile(profile);
    }

    public static void writeProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        if (isHerobrine(fighter)) profile.putBoolean(HEROBRINE_TAG, true);
        RedRibbonExperimentManager.writeProfile(fighter, profile);
    }

    public static void restoreProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        if (profile != null && profile.getBoolean(HEROBRINE_TAG)) {
            fighter.getPersistentData().putBoolean(HEROBRINE_TAG, true);
            fighter.configureHerobrineAppearance();
        }
        RedRibbonExperimentManager.restoreProfile(fighter, profile);
    }

    /** The World Menace dossier is intentionally personal knowledge, not omniscient world data. */
    public static boolean hasSpotted(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(PLAYER_SPOTTED);
    }

    public static CompoundTag spottedProfile(ServerPlayer player) {
        if (player == null || !hasSpotted(player)) return new CompoundTag();
        return player.getPersistentData().contains(PLAYER_SPOTTED_PROFILE, Tag.TAG_COMPOUND)
                ? player.getPersistentData().getCompound(PLAYER_SPOTTED_PROFILE).copy() : new CompoundTag();
    }

    public static UUID dossierRecordId() {
        return UUID.nameUUIDFromBytes("dmzlivingworld:world_menace:herobrine".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Best portrait/profile the player is entitled to know. Prefer their last confirmed sighting;
     * if an older save knows the menace but predates per-player portrait storage, fall back to the
     * persistent singleton snapshot rather than rendering a blank/error portrait.
     */
    public static CompoundTag knownProfile(ServerPlayer player) {
        CompoundTag seen = spottedProfile(player);
        if (!seen.isEmpty()) return seen;
        if (player == null || !hasSpotted(player)) return new CompoundTag();
        CompoundTag persistent = WorldMenaceData.get(player.serverLevel()).profile();
        if (!persistent.isEmpty()) persistent.putBoolean(HEROBRINE_TAG, true);
        return persistent;
    }

    /** Explicit self-defence bridge: menace presentation must never suppress DMZ combat retaliation. */
    public static void onAttacked(AmbientFighterEntity fighter, ServerPlayer attacker) {
        if (fighter == null || attacker == null || !isHerobrine(fighter) || fighter.level().isClientSide) return;
        long now = fighter.level().getGameTime();
        if (PlayerSpawnCombatSafety.isInsideProtectedArea(attacker)) {
            fighter.speak("I'll see you at another place", 60);
            disappear(fighter, attacker, now);
            return;
        }
        WATCHES.remove(fighter.getUUID());
        fighter.getPersistentData().putUUID(RETALIATE_PLAYER, attacker.getUUID());
        fighter.getPersistentData().putLong(RETALIATE_UNTIL, now + 20L * 45L);
        fighter.getPersistentData().putString(MENACE_STATE, "HUNTING");
        fighter.setTarget(attacker);
        markSpotted(attacker, fighter);
    }

    /** Handles ranged intimidation before any damage is applied. */
    public static boolean interceptRangedAttack(AmbientFighterEntity fighter, DamageSource source, ServerPlayer attacker) {
        if (fighter == null || source == null || attacker == null || !isHerobrine(fighter)
                || fighter.level().isClientSide || source.getDirectEntity() == attacker) return false;
        double playerPower = PlayerWorldManager.playerBattlePower(attacker);
        double menacePower = Math.max(1.0D, fighter.getBattlePower());
        markSpotted(attacker, fighter);
        if (playerPower < menacePower * 0.81D) {
            fighter.speak("You are not yet ready", 60);
            disappear(fighter, attacker, fighter.level().getGameTime());
            return true;
        }
        if (PlayerSpawnCombatSafety.isInsideProtectedArea(attacker)) {
            fighter.speak("I'll see you at another place", 60);
            disappear(fighter, attacker, fighter.level().getGameTime());
            return true;
        }
        teleportInFront(fighter, attacker);
        engage(fighter, attacker, fighter.level().getGameTime());
        return true;
    }


    public static void markSpotted(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !isHerobrine(fighter)) return;
        CompoundTag profile = fighter.writeMemoryProfile();
        profile.putBoolean(HEROBRINE_TAG, true);
        CompoundTag pd = player.getPersistentData();
        long now = player.serverLevel().getGameTime();
        long previous = pd.getLong(PLAYER_SPOTTED_AT);
        if (!pd.contains(PLAYER_FIRST_SPOTTED_AT, Tag.TAG_ANY_NUMERIC)) pd.putLong(PLAYER_FIRST_SPOTTED_AT, now);
        if (previous <= 0L || now - previous > 200L) pd.putInt(PLAYER_SIGHTINGS, Math.min(10_000, pd.getInt(PLAYER_SIGHTINGS) + 1));
        pd.putBoolean(PLAYER_SPOTTED, true);
        pd.put(PLAYER_SPOTTED_PROFILE, profile);
        pd.putLong(PLAYER_SPOTTED_AT, now);
        pd.putDouble(PLAYER_LAST_MENACE_X, fighter.getX());
        pd.putDouble(PLAYER_LAST_MENACE_Y, fighter.getY());
        pd.putDouble(PLAYER_LAST_MENACE_Z, fighter.getZ());
    }

    /**
     * Inspection is deliberately a knowledge event rather than a social interaction. Repeatedly
     * opening Herobrine's dossier can very rarely make the live menace change position shortly
     * afterwards, but combat always overrides this presentation hook.
     */
    public static void onInspected(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !isHerobrine(fighter) || fighter.level().isClientSide) return;
        markSpotted(player, fighter);
        CompoundTag pd = player.getPersistentData();
        int inspections = Math.min(10_000, pd.getInt(PLAYER_INSPECTIONS) + 1);
        pd.putInt(PLAYER_INSPECTIONS, inspections);
        // Inspection is knowledge only. R16 never teleports an already-present Herobrine as a reaction to being inspected.
    }

    /** Unstable dossier text. This never pretends Herobrine follows the ordinary daily planner. */
    public static List<String> dossierRoutineLines(ServerPlayer player, AmbientFighterEntity fighter, boolean rememberedSnapshot) {
        List<String> lines = new java.util.ArrayList<>();
        if (player == null || fighter == null || !isHerobrine(fighter)) return lines;
        String state = fighter.getPersistentData().getString(MENACE_STATE);
        lines.add("## Status");
        lines.add("* " + (state.isBlank() ? "Whereabouts unclear" : switch (state) {
            case "WATCHING" -> "Watching from a distance";
            case "SHADOWING" -> "Following from a distance";
            case "HUNTING" -> "Hostile";
            case "RETURNED" -> "Returned after defeat";
            default -> "Whereabouts unclear";
        }));
        lines.add("## Behavior");
        lines.add("* Usually keeps his distance outside combat.");
        lines.add("* Confirmed sightings: " + sightingCount(player));
        int returns = WorldMenaceData.get(player.serverLevel()).deaths();
        if (returns > 0) lines.add("* Confirmed returns after defeat: " + returns);
        return lines;
    }

    public static int inspectionCount(ServerPlayer player) {
        return player == null ? 0 : player.getPersistentData().getInt(PLAYER_INSPECTIONS);
    }

    public static int sightingCount(ServerPlayer player) {
        return player == null ? 0 : Math.max(0, player.getPersistentData().getInt(PLAYER_SIGHTINGS));
    }

    /** Encounter-driven Herobrine story. No ordinary biography, bond or training-history fiction. */
    public static List<String> dossierStoryLines(ServerPlayer player, AmbientFighterEntity fighter) {
        List<String> lines = new java.util.ArrayList<>();
        if (player == null || fighter == null || !isHerobrine(fighter)) return lines;
        CompoundTag pd = player.getPersistentData();
        int sightings = sightingCount(player);
        int returns = WorldMenaceData.get(player.serverLevel()).deaths();
        long first = pd.getLong(PLAYER_FIRST_SPOTTED_AT);
        long last = pd.getLong(PLAYER_SPOTTED_AT);
        lines.add("!! HEROBRINE • ENCOUNTER RECORD");
        if (sightings <= 0) {
            lines.add(". No confirmed sighting has been recorded.");
            return lines;
        }
        long now = player.serverLevel().getGameTime();
        lines.add("## Sightings");
        lines.add("* First confirmed " + ageLabel(now, first));
        lines.add("* Last confirmed " + ageLabel(now, last));
        lines.add("* Confirmed sightings: " + sightings);
        lines.add("## Observed Pattern");
        lines.add("* Watches from long range and withdraws when approached outside combat.");
        if (returns > 0) {
            lines.add("## Return Record");
            lines.add("* Confirmed returns after defeat: " + returns);
            lines.add("* Each return has shown a higher Power Level.");
        }
        lines.add("## Unknown");
        lines.add(". Origin • motive • destination");
        return lines;
    }

    /** Sparse evidence replaces ordinary NPC message history in the menace dossier. */
    public static List<String> dossierEvidenceLines(ServerPlayer player, AmbientFighterEntity fighter) {
        List<String> lines = new java.util.ArrayList<>();
        if (player == null || fighter == null || !isHerobrine(fighter)) return lines;
        CompoundTag pd = player.getPersistentData();
        lines.add("## Evidence");
        lines.add("* Confirmed sightings: " + sightingCount(player));
        if (pd.contains(PLAYER_LAST_MENACE_X, Tag.TAG_ANY_NUMERIC)) {
            lines.add("* Last confirmed area: approximately " + Math.round(pd.getDouble(PLAYER_LAST_MENACE_X))
                    + ", " + Math.round(pd.getDouble(PLAYER_LAST_MENACE_Z)));
        }
        int returns = WorldMenaceData.get(player.serverLevel()).deaths();
        if (returns > 0) lines.add("* Confirmed returns after defeat: " + returns);
        return lines;
    }

    private static String ageLabel(long now, long tick) {
        if (tick <= 0L) return "at an unknown time";
        long age = Math.max(0L, now - tick);
        if (age < 1200L) return "moments ago";
        if (age < 24000L) return "earlier today";
        long days = Math.max(1L, age / 24000L);
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 200L != 0L) return;
        ServerLevel level = server.overworld();
        WorldMenaceData data = WorldMenaceData.get(level);
        if (!data.initialized()) {
            ServerPlayer seedPlayer = server.getPlayerList().getPlayers().stream().filter(p -> !p.isSpectator()).findFirst().orElse(null);
            if (seedPlayer != null) data.scheduleFirst(now + 1200L + seedPlayer.getRandom().nextInt(4801));
            return;
        }

        AmbientFighterEntity loaded = findLoaded(server, data.entityId());
        if (loaded != null) {
            enforceStrength(loaded, data.deaths());
            data.markActive(loaded.getUUID(), loaded.writeMemoryProfile(), loaded.getX(), loaded.getY(), loaded.getZ());
            return;
        }
        // Active but unloaded normally means the singleton still exists in its saved chunk. R13
        // performs only a sparse, bounded verification load of that exact last-known chunk. If the
        // saved UUID is present, it remains the singleton. Only if the chunk has actually loaded and
        // that UUID is absent do we recover the same logical menace from the persisted profile.
        if (data.active()) {
            if (lastSingletonRecoveryCheck != Long.MIN_VALUE && now - lastSingletonRecoveryCheck < 2400L) return;
            lastSingletonRecoveryCheck = now;
            BlockPos last = BlockPos.containing(data.x(), data.y(), data.z());
            level.getChunkAt(last);
            loaded = findLoaded(server, data.entityId());
            if (loaded != null) {
                LOGGER.info("[LW MenaceRecovery] recovered existing Herobrine uuid={} at {}", loaded.getUUID(), loaded.blockPosition());
                enforceStrength(loaded, data.deaths());
                data.markActive(loaded.getUUID(), loaded.writeMemoryProfile(), loaded.getX(), loaded.getY(), loaded.getZ());
                return;
            }
            ServerPlayer recoveryAnchor = server.getPlayerList().getPlayers().stream()
                    .filter(player -> !player.isSpectator() && player.serverLevel() == level).findAny().orElse(null);
            if (recoveryAnchor != null) {
                UUID stale = data.entityId();
                AmbientFighterEntity recovered = spawn(level, recoveryAnchor, data, false);
                if (recovered != null) LOGGER.warn("[LW MenaceRecovery] saved Herobrine uuid={} was absent after loading last chunk {}; recovered logical singleton as uuid={}",
                        stale, last, recovered.getUUID());
            }
            return;
        }
        if (data.returnAt() > now) return;
        ServerPlayer anchor = server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isSpectator() && player.serverLevel() == level).findAny().orElse(null);
        if (anchor != null) spawn(level, anchor, data, false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        // Winning a hunt ends the current manifestation. Use the same disappearance path as
        // the observation encounters so state, return scheduling and the cave sound cannot drift.
        if (event.getEntity() instanceof ServerPlayer slain
                && event.getSource().getEntity() instanceof AmbientFighterEntity killer
                && isHerobrine(killer)) {
            disappear(killer, slain, killer.level().getGameTime());
            return;
        }
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !isHerobrine(fighter)
                || !(fighter.level() instanceof ServerLevel level)) return;
        WATCHES.remove(fighter.getUUID());
        WorldMenaceData data = WorldMenaceData.get(level);
        long now = level.getServer().overworld().getGameTime();
        long returnAt = now + MIN_RETURN + fighter.getRandom().nextInt(RETURN_JITTER);
        CompoundTag profile = fighter.writeMemoryProfile();
        profile.putBoolean(HEROBRINE_TAG, true);
        data.markDead(profile, returnAt, fighter.getX(), fighter.getY(), fighter.getZ());
        Component omen = Component.literal("The air goes still. Somewhere behind you, something feels unfinished.")
                .withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(fighter) <= 160.0D * 160.0D) player.displayClientMessage(omen, false);
        }
    }

    private static AmbientFighterEntity spawn(ServerLevel level, ServerPlayer anchor, WorldMenaceData data, boolean closeDebug) {
        int deaths = data.deaths();
        // Per-client entity render distance is not available to the logical server. Use the
        // requested deterministic fallback: a distant appearance approximately 100 blocks away.
        int minDistance = closeDebug ? 8 : 96;
        int maxDistance = closeDebug ? 18 : 104;
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAroundSeparated(level, anchor.blockPosition(), anchor.getRandom(),
                minDistance, maxDistance, Math.max(52, maxDistance / 2), closeDebug ? 22.0D : 44.0D);
        // A natural return is allowed to wait for a later tick instead of betraying the menace by
        // respawning right beside the player. Only the explicit debug spawn gets a close fallback.
        if (pos == null && closeDebug) pos = AmbientFighterSpawner.findSafeGroundAround(level, anchor.blockPosition(), anchor.getRandom(), 8, 32, 32);
        if (pos == null) return null;
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level);
        if (fighter == null) return null;
        CompoundTag profile = data.profile();
        if (!profile.isEmpty() && profile.contains("Name", Tag.TAG_STRING)) fighter.initializeFromMemory(profile);
        else fighter.initializeAs(FighterAlignment.NEUTRAL, FighterRank.VETERAN, FighterPersonality.CALM,
                FighterRace.HUMAN, FighterArchetype.SPEEDSTER);
        fighter.getPersistentData().putBoolean(HEROBRINE_TAG, true);
        fighter.configureHerobrineAppearance();
        fighter.setFlightUnlockedForDebug(true);
        fighter.setPersistenceRequired();
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, anchor.getYRot() + 180.0F, 0.0F);
        fighter.getPersistentData().putString(MENACE_STATE, deaths > 0 ? "RETURNED" : "UNREADABLE");
        fighter.getPersistentData().putLong(NEXT_WATCH, level.getGameTime() + 3600L + fighter.getRandom().nextInt(6401));
        enforceStrength(fighter, deaths);
        if (!level.addFreshEntity(fighter)) return null;
        data.markActive(fighter.getUUID(), fighter.writeMemoryProfile(), fighter.getX(), fighter.getY(), fighter.getZ());
        if (!closeDebug) {
            WATCHES.put(fighter.getUUID(), new WatchSession(anchor.getUUID(), level.getGameTime() + 12_000L,
                    false, level.getGameTime(), anchor.position()));
            fighter.getPersistentData().putString(MENACE_STATE, "WATCHING");
        }
        return fighter;
    }

    private static void enforceStrength(AmbientFighterEntity fighter, int deaths) {
        fighter.setFighterName("Herobrine");
        fighter.leaveFaction();
        // Migration from older builds: Herobrine is no longer a personal-memory/bond entity.
        // Sightings and evidence live in the World Menace dossier instead.
        fighter.detachMemory(null, null);
        fighter.getPersistentData().putBoolean(HEROBRINE_TAG, true);
        fighter.configureHerobrineAppearance();
        // Keep the easter egg visually literal instead of letting the ordinary Living Arsenal
        // dress the blocky Herobrine model in procedural DMZ armor/weapons.
        if (!fighter.getMainHandItem().isEmpty() || !fighter.getOffhandItem().isEmpty()
                || !fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                || !fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                || !fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
                || !fighter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()) {
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                fighter.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        fighter.setArsenalInitialized(true);
        fighter.setFlightUnlockedForDebug(true);
        fighter.setPersistenceRequired();
        int returns = Math.max(0, deaths);
        double powerScale = Math.min(24.0D, Math.pow(1.85D, Math.min(8, returns)));
        int minimumPower = (int)Math.min(12_000_000D, 250_000D * powerScale);
        if (fighter.getPermanentBattlePower() < minimumPower) fighter.setBattlePowerAndRefresh(minimumPower);
        // BP is the combat source of truth. Do not layer hidden HP/melee/Ki minimums on top of
        // the profile: the dossier must describe the same body that actually fights.
        fighter.refreshCombatStatsFromPower();
        applyMenaceMovementFloor(fighter, returns);
    }

    /** Compatibility call site after temporary power layers end; only menace locomotion is special. */
    public static void restoreCombatFloors(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !isHerobrine(fighter)
                || !(fighter.level() instanceof ServerLevel level)) return;
        applyMenaceMovementFloor(fighter, WorldMenaceData.get(level).deaths());
    }

    private static void applyMenaceMovementFloor(AmbientFighterEntity fighter, int deaths) {
        int returns = Math.max(0, deaths);
        double minimumSpeed = Math.min(0.48D, 0.36D + returns * 0.015D);
        if (fighter.getAttribute(Attributes.MOVEMENT_SPEED) != null && fighter.getAttributeValue(Attributes.MOVEMENT_SPEED) < minimumSpeed)
            fighter.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(minimumSpeed);
        if (fighter.getHealth() > fighter.getMaxHealth()) fighter.setHealth(fighter.getMaxHealth());
    }

    /**
     * Idle Herobrine behaviour: the already-existing physical entity occasionally enters a
     * distant watch state from wherever it genuinely is, watches silently, and backs away with
     * real movement if approached. Returning true means this short scene owns locomotion for the tick.
     */
    public static boolean tickFighter(AmbientFighterEntity fighter) {
        if (!isHerobrine(fighter) || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();

        // Keep the singleton recovery coordinate fresh while the exact entity is loaded. Most
        // movement is ordinary navigation rather than teleportation; a one-second snapshot makes
        // the bounded recovery probe extremely unlikely to look in a chunk Herobrine already left.
        // Explicit menace relocations also snapshot immediately below, so sudden cross-chunk moves
        // never wait for this cadence.
        if (Math.floorMod(now + fighter.getUUID().hashCode(), 20L) == 0L) {
            WorldMenaceData.get(level).updateSnapshot(fighter.writeMemoryProfile(),
                    fighter.getX(), fighter.getY(), fighter.getZ());
        }

        // A player who actually attacked Herobrine gets real DMZ retaliation. This check must run
        // before the uncanny keep-space/watch presentation, otherwise those idle presentation
        // systems can repeatedly steal locomotion before HurtByTargetGoal gets a clean combat tick.
        long retaliateUntil = fighter.getPersistentData().getLong(RETALIATE_UNTIL);
        if (retaliateUntil > now && fighter.getPersistentData().hasUUID(RETALIATE_PLAYER)) {
            ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(fighter.getPersistentData().getUUID(RETALIATE_PLAYER));
            if (attacker != null && attacker.isAlive() && !attacker.isCreative() && !attacker.isSpectator()
                    && attacker.serverLevel() == level && fighter.distanceToSqr(attacker) <= 128.0D * 128.0D) {
                WATCHES.remove(fighter.getUUID());
                fighter.getPersistentData().putString(MENACE_STATE, "HUNTING");
                fighter.setTarget(attacker);
                return false; // native DMZ combat owns the tick
            }
        } else if (retaliateUntil > 0L) {
            fighter.getPersistentData().remove(RETALIATE_PLAYER);
            fighter.getPersistentData().remove(RETALIATE_UNTIL);
        }

        if (fighter.getTarget() != null || fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.isTransforming() || FighterAmbientActivityManager.isActive(fighter)
                || LivingBondManager.isTravellingCompanion(fighter)) {
            if (fighter.getTarget() instanceof ServerPlayer targetPlayer) {
                markSpotted(targetPlayer, fighter);
                stareAt(fighter, targetPlayer);
            }
            WATCHES.remove(fighter.getUUID());
            fighter.getPersistentData().putString(MENACE_STATE, fighter.getTarget() != null ? "HUNTING" : "UNREADABLE");
            return false;
        }

        long debugHoldUntil = fighter.getPersistentData().getLong(DEBUG_HOLD_UNTIL);
        if (debugHoldUntil > now && fighter.getTarget() == null) {
            WATCHES.remove(fighter.getUUID());
            fighter.getNavigation().stop();
            fighter.setFlyingFast(false);
            fighter.setFlying(false);
            fighter.setDeltaMovement(Vec3.ZERO);
            fighter.getPersistentData().putString(MENACE_STATE, "OBSERVED");
            ServerPlayer closestViewer = level.players().stream()
                    .filter(p -> p.isAlive() && !p.isSpectator())
                    .min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
            if (closestViewer != null) stareAt(fighter, closestViewer);
            return true;
        } else if (debugHoldUntil > 0L) {
            fighter.getPersistentData().remove(DEBUG_HOLD_UNTIL);
        }

        fighter.getPersistentData().remove(INSPECTION_RELOCATE_AT);
        fighter.getPersistentData().remove(INSPECTION_RELOCATE_PLAYER);

        WatchSession session = WATCHES.get(fighter.getUUID());
        if (session != null) {
            ServerPlayer watched = level.getServer().getPlayerList().getPlayer(session.playerId);
            if (watched == null || watched.isSpectator() || watched.serverLevel() != level || now >= session.endsAt) {
                endWatch(fighter, now);
                return holdStill(fighter, null);
            }
            fighter.getPersistentData().putString(MENACE_STATE, "WATCHING");
            holdStill(fighter, watched);
            // Rotation remains continuous, while all positional/range decisions are explicitly
            // re-audited once per second so a stale session can never retain an old distance.
            if (now < session.nextPositionAudit) return true;
            session.nextPositionAudit = now + 20L;
            boolean observed = isClearlyObserved(watched, fighter);
            boolean lineOfSight = fighter.hasLineOfSight(watched);
            if (lineOfSight) session.hadLineOfSight = true;
            else if (session.hadLineOfSight) {
                disappear(fighter, watched, now);
                return true;
            }

            double playerPower = PlayerWorldManager.playerBattlePower(watched);
            double menacePower = Math.max(1.0D, fighter.getBattlePower());
            boolean playerReady = playerPower >= menacePower * 0.81D;
            double distance = fighter.distanceTo(watched);

            // Reaching Herobrine physically is an unconditional challenge, regardless of BP.
            if (distance < 4.0D) {
                engage(fighter, watched, now);
                return false;
            }

            if (distance <= 50.0D && playerReady) {
                teleportInFront(fighter, watched);
                engage(fighter, watched, now);
                return false;
            }

            if (!playerReady) {
                if (distance <= 50.0D) {
                    fighter.speak("You are not yet ready", 60);
                    disappear(fighter, watched, now);
                    return true;
                }
                if (observed) {
                    markSpotted(watched, fighter);
                    if (session.observedSince <= 0L) session.observedSince = now;
                    if (now - session.observedSince >= 200L) {
                        fighter.speak("You are not yet ready", 60);
                        disappear(fighter, watched, now);
                        return true;
                    }
                } else {
                    session.observedSince = 0L;
                }
            }

            // When the watched player leaves the intended sighting range, relocate instantly to
            // another safe point around 100 blocks away. Never fly or navigate to the new position.
            if (distance > 125.0D && relocateAround(fighter, watched)) {
                session.lastPlayerPos = watched.position();
            }
            return true;
        }

        long next = fighter.getPersistentData().getLong(NEXT_WATCH);
        if (next <= 0L) {
            fighter.getPersistentData().putLong(NEXT_WATCH, now + 7200L + fighter.getRandom().nextInt(7801));
            return holdStill(fighter, null);
        }
        if (now < next) return holdStill(fighter, null);

        List<ServerPlayer> candidates = level.players().stream()
                .filter(player -> !player.isSpectator() && player.isAlive())
                .toList();
        if (candidates.isEmpty()) {
            fighter.getPersistentData().putLong(NEXT_WATCH, now + 1200L);
            return holdStill(fighter, null);
        }
        ServerPlayer watched = candidates.get(fighter.getRandom().nextInt(candidates.size()));
        if (!relocateAround(fighter, watched)) {
            fighter.getPersistentData().putLong(NEXT_WATCH, now + 700L + fighter.getRandom().nextInt(1001));
            return holdStill(fighter, watched);
        }
        WATCHES.put(fighter.getUUID(), new WatchSession(watched.getUUID(), now + 12_000L, false, now, watched.position()));
        fighter.getPersistentData().putString(MENACE_STATE, "WATCHING");
        disturbNearbyFighters(fighter, watched);
        return true;
    }

    private static boolean holdStill(AmbientFighterEntity fighter, ServerPlayer watched) {
        fighter.getNavigation().stop();
        fighter.setFlyingFast(false);
        fighter.setFlying(false);
        fighter.setNoGravity(false);
        fighter.setDeltaMovement(Vec3.ZERO);
        if (watched != null) stareAt(fighter, watched);
        return true;
    }

    private static void engage(AmbientFighterEntity fighter, ServerPlayer player, long now) {
        if (PlayerSpawnCombatSafety.isInsideProtectedArea(player)) {
            fighter.speak("I'll see you at another place", 60);
            disappear(fighter, player, now);
            return;
        }
        WATCHES.remove(fighter.getUUID());
        fighter.getPersistentData().putUUID(RETALIATE_PLAYER, player.getUUID());
        fighter.getPersistentData().putLong(RETALIATE_UNTIL, now + 20L * 45L);
        fighter.getPersistentData().putString(MENACE_STATE, "HUNTING");
        fighter.setTarget(player);
        markSpotted(player, fighter);
    }

    private static void teleportInFront(AmbientFighterEntity fighter, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 0.001D) flat = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 destination = player.position().add(flat.normalize().scale(3.0D));
        fighter.teleportTo(destination.x, player.getY(), destination.z);
        fighter.setDeltaMovement(Vec3.ZERO);
        if (fighter.level() instanceof ServerLevel level)
            WorldMenaceData.get(level).updateSnapshot(fighter.writeMemoryProfile(),
                    fighter.getX(), fighter.getY(), fighter.getZ());
    }

    private static void disappear(AmbientFighterEntity fighter, ServerPlayer responsiblePlayer, long now) {
        if (!(fighter.level() instanceof ServerLevel level)) return;
        WATCHES.remove(fighter.getUUID());
        CompoundTag profile = fighter.writeMemoryProfile();
        profile.putBoolean(HEROBRINE_TAG, true);
        WorldMenaceData.get(level).markAbsent(profile, now + 2400L + fighter.getRandom().nextInt(3601),
                fighter.getX(), fighter.getY(), fighter.getZ());
        if (responsiblePlayer != null && responsiblePlayer.serverLevel() == level) {
            level.playSound(null, responsiblePlayer.getX(), responsiblePlayer.getY(), responsiblePlayer.getZ(),
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 1.0F);
        }
        fighter.discard();
    }

    private static boolean relocateAround(AmbientFighterEntity fighter, ServerPlayer player) {
        if (!(fighter.level() instanceof ServerLevel level) || player.serverLevel() != level) return false;
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAroundSeparated(level, player.blockPosition(),
                fighter.getRandom(), 96, 104, 64, 72.0D);
        if (pos == null) return false;
        fighter.getNavigation().stop();
        fighter.setFlyingFast(false);
        fighter.setFlying(false);
        fighter.setNoGravity(false);
        fighter.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, fighter.getYRot(), 0.0F);
        fighter.setDeltaMovement(Vec3.ZERO);
        stareAt(fighter, player);
        WorldMenaceData.get(level).updateSnapshot(fighter.writeMemoryProfile(), fighter.getX(), fighter.getY(), fighter.getZ());
        return true;
    }

    private static boolean keepSpaceFrom(AmbientFighterEntity fighter, ServerPlayer player) {
        double distance = fighter.distanceTo(player);
        if (distance >= 50.0D || distance < 0.01D) return false;
        fighter.getNavigation().stop();

        // R16: never teleport an already-present Herobrine just because the player closes the gap.
        // Distance is maintained with real movement while he keeps staring at the player.
        Vec3 away = fighter.position().subtract(player.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.001D) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize();

        // When the player closes a large portion of his preferred buffer, flight-capable Herobrine
        // uses a real retreat rather than allowing the idle scene to collapse into melee range.
        // This presentation owns no combat stats and disappears the moment native retaliation starts.
        boolean airborneRetreat = fighter.hasFlightUnlocked() && (fighter.isFlying() || distance < 34.0D);
        Vec3 current = fighter.getDeltaMovement();
        if (airborneRetreat) {
            fighter.setFlying(true);
            fighter.setFlyingFast(distance < 22.0D);
            double speed = distance < 18.0D ? 0.50D : distance < 28.0D ? 0.44D : 0.36D;
            fighter.steerAmbientFlightToward(fighter.position().add(away.scale(18.0D)).add(0.0D, 1.2D, 0.0D), speed);
        } else {
            fighter.setFlyingFast(false);
            double speed = distance < 28.0D ? 0.31D : 0.25D;
            fighter.setDeltaMovement(away.x * speed, current.y, away.z * speed);
        }
        stareAt(fighter, player);
        return true;
    }

    private static void stareAt(AmbientFighterEntity fighter, ServerPlayer player) {
        float yaw = (float)(Math.toDegrees(Math.atan2(player.getZ() - fighter.getZ(), player.getX() - fighter.getX())) - 90.0D);
        fighter.setYRot(yaw);
        fighter.yBodyRot = yaw;
        fighter.yHeadRot = yaw;
        fighter.getLookControl().setLookAt(player, 35.0F, 28.0F);
    }


    private static void disturbNearbyFighters(AmbientFighterEntity menace, ServerPlayer watched) {
        if (!(menace.level() instanceof ServerLevel level)) return;
        for (AmbientFighterEntity other : level.getEntitiesOfClass(AmbientFighterEntity.class,
                menace.getBoundingBox().inflate(18.0D), f -> f != menace && f.isAlive() && !isHerobrine(f))) {
            if (other.getTarget() != null || other.isSanctionedMatchParticipant()) continue;
            // Other fighters do not gain omniscient knowledge of Herobrine. They simply become
            // uneasy for a while, which can subtly bend their existing routine/social behavior.
            ReactiveWorldManager.react(other, ReactiveWorldManager.Mood.WARY, "something nearby they cannot place", 520 + other.getRandom().nextInt(381));
            if (other.getRandom().nextFloat() < 0.24F)
                ReactiveWorldManager.rememberEvent(other, "UNEASY", watched == null ? "the area" : watched.getGameProfile().getName(), "felt watched for no obvious reason");
        }
    }

    private static boolean isClearlyObserved(ServerPlayer player, AmbientFighterEntity fighter) {
        Vec3 to = fighter.position().add(0.0D, fighter.getBbHeight() * 0.65D, 0.0D).subtract(player.getEyePosition());
        double distance = to.length();
        if (distance < 0.01D || distance > 118.0D || !player.hasLineOfSight(fighter)) return false;
        double dot = player.getLookAngle().normalize().dot(to.normalize());
        return dot > 0.965D;
    }

    private static void endWatch(AmbientFighterEntity fighter, long now) {
        WATCHES.remove(fighter.getUUID());
        fighter.getPersistentData().putString(MENACE_STATE, "UNREADABLE");
        fighter.getPersistentData().putLong(NEXT_WATCH, now + 7600L + fighter.getRandom().nextInt(9001));
    }

    public static String moodSummary(AmbientFighterEntity fighter) {
        String state = fighter == null ? "UNREADABLE" : fighter.getPersistentData().getString(MENACE_STATE);
        if (fighter != null && fighter.getTarget() != null) state = "HUNTING";
        if (state.isBlank()) state = "UNREADABLE";
        return switch (state) {
            case "WATCHING" -> "Watching — attention fixed somewhere beyond you";
            case "SHADOWING" -> "Following — never quite where you expect him to be";
            case "HUNTING" -> "Hostile Silence — fully intent on the fight";
            case "RETURNED" -> "Restless — something about him feels different this time";
            default -> "Unreadable — no ordinary emotion is obvious";
        };
    }

    public static String talkLine(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        WorldMenaceData data = WorldMenaceData.get(player.serverLevel());
        int deaths = data.deaths();
        String state = fighter.getPersistentData().getString(MENACE_STATE);
        if ("WATCHING".equals(state) || "SHADOWING".equals(state)) return pick(fighter,
                "You weren't supposed to notice.", "You looked back.", "Keep walking.",
                "I was farther away when you last checked.", "Don't come closer.", "Pretend you didn't see me.",
                "I wanted to know if you'd turn around.", "You felt me looking, didn't you?",
                "You keep checking the edge of your vision.", "Go back to what you were doing.",
                "You looked at the right place too late.", "I can hear when you stop moving.",
                "You only see me when I let the distance become useful.",
                "You stopped. So did I.", "Keep moving. It makes this easier.",
                "You looked away at the wrong time.");
        int sightings = player.getPersistentData().getInt(PLAYER_SIGHTINGS);
        if (sightings >= 5 && fighter.getRandom().nextFloat() < Math.min(0.46F, 0.16F + sightings * 0.025F)) return pick(fighter,
                "You keep finding the same face in different places.",
                "How many sightings before you stop calling them accidents?",
                "You remember where I was. That isn't where I am.",
                sightings >= 10 ? "You've seen me enough times to know looking doesn't help." : "You are getting better at noticing too late.",
                "Every time you confirm I'm here, the useful part is already over.");
        int inspections = inspectionCount(player);
        if (inspections >= 2 && fighter.getRandom().nextFloat() < Math.min(0.42F, 0.12F + inspections * 0.035F)) return pick(fighter,
                "You keep opening that page.",
                "Did it say the same thing last time?",
                "You won't find a schedule for me.",
                "Close it. Then look again.",
                "The portrait isn't where I am.",
                inspections >= 5 ? "You've checked enough times to know the record is wrong." : "You trust records too easily.",
                "You keep trying to turn sightings into a pattern.");
        if (deaths > 0 && fighter.getRandom().nextFloat() < 0.44F) return pick(fighter,
                "That didn't keep me gone.",
                "You remember the last time. So do I.",
                deaths > 1 ? "You've done this before. It keeps changing me." : "I came back different.");
        if (player.serverLevel().isThundering() && fighter.getRandom().nextFloat() < 0.55F)
            return pick(fighter, "Thunder covers more than footsteps.", "It's easier to move when the sky is loud.");
        long day = player.serverLevel().getDayTime() % 24000L;
        if (day >= 13000L && day <= 23000L && fighter.getRandom().nextFloat() < 0.48F)
            return pick(fighter, "You check the dark more carefully now.", "Night makes people look behind themselves.", "I prefer when the world is quiet.");
        if (relationship >= 60) return pick(fighter, "You keep coming back to talk.", "You're less afraid of silence than most.", "I know your footsteps now.");
        if (relationship <= -15) return pick(fighter, "You keep finding me. Stop.", "We don't need words for this.", "You already decided what I am.");
        return pick(fighter,
                "I was here before you looked.",
                "Some places remember who passed through them.",
                "You shouldn't always know where I am.",
                "Keep going. I'll know where you went.",
                "Not every movement in the distance is an animal.",
                "You notice more than you used to.",
                "There are quieter ways to follow someone.",
                "I know which way you turn when you think you're alone.",
                "You've walked past me more times than you remember.",
                "Sometimes the footsteps stop when you stop listening.",
                "Don't mistake distance for absence.",
                "There are places in this world that don't like being watched back.",
                "You leave more of a trail than you think.",
                "I don't need to stand close to know you're there.",
                "Look at the horizon. Then ask yourself what moved.",
                "You've started looking over your shoulder sooner.",
                "I was on the other side of that hill a moment ago.",
                "The quiet parts of the world have good sightlines.",
                "I don't follow roads.",
                "You'll know I left when you stop being sure I was here.",
                "Some sightings only happen once.",
                "The place you last saw me is already empty.",
                "I don't need your name to know which footsteps are yours.",
                "You keep making maps for things that move when you aren't looking.",
                "Nothing is wrong with the world. That's why you noticed.",
                "You looked away first.",
                "Sometimes I wait where your camera won't.",
                "You walk differently when you think something is behind you.",
                "The world gets very quiet when only one of us is moving.",
                "I can stand still longer than you can keep checking.",
                "You keep using the same paths back.",
                "There was a moment you were sure I was closer.",
                "You don't have to see movement for something to have moved.",
                "The safest place to watch someone is where they already looked.",
                "You started turning around before the sound this time.");
    }

    public static String hostileLine(AmbientFighterEntity fighter) {
        return pick(fighter, "No.", "You've seen enough.", "Not this time.", "Keep your distance.",
                "You should have kept walking.", "Too close.", "I warned you without speaking.",
                "Don't make me stay here.", "You wanted proof. Here it is.", "Back away.");
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static AmbientFighterEntity findLoaded(MinecraftServer server, UUID entityId) {
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(entityId) instanceof AmbientFighterEntity fighter && isHerobrine(fighter)) return fighter;
        }
        return null;
    }

    public static int debugSpawn(ServerPlayer player) {
        if (player == null) return 0;
        WorldMenaceData data = WorldMenaceData.get(player.serverLevel());
        AmbientFighterEntity loaded = findLoaded(player.getServer(), data.entityId());
        if (loaded != null) return 1;
        if (data.active()) {
            // Load the singleton's last known chunk once; this is not a forced ticket.
            ServerLevel level = player.getServer().overworld();
            level.getChunk(BlockPos.containing(data.x(), data.y(), data.z()));
            loaded = findLoaded(player.getServer(), data.entityId());
            if (loaded != null) return 1;
            // The recorded UUID vanished from its saved chunk; recover the same logical menace from its profile.
        }
        return spawn(player.getServer().overworld(), player, data, true) != null ? 1 : 0;
    }

    public static int debugTeleport(ServerPlayer player) {
        if (player == null) return 0;
        WorldMenaceData data = WorldMenaceData.get(player.serverLevel());
        AmbientFighterEntity fighter = findLoaded(player.getServer(), data.entityId());
        if (fighter == null && data.active()) {
            ServerLevel level = player.getServer().overworld();
            level.getChunk(BlockPos.containing(data.x(), data.y(), data.z()));
            fighter = findLoaded(player.getServer(), data.entityId());
        }
        if (fighter == null) {
            player.displayClientMessage(Component.literal("[LW] Herobrine is currently absent. Use /lw menace spawn to force his return for testing."), false);
            return 0;
        }
        long now = fighter.level().getGameTime();
        WATCHES.remove(fighter.getUUID());
        fighter.getPersistentData().putLong(DEBUG_HOLD_UNTIL, now + 200L);
        fighter.getNavigation().stop();
        fighter.setFlyingFast(false);
        fighter.setFlying(false);
        fighter.setDeltaMovement(Vec3.ZERO);
        player.teleportTo((ServerLevel)fighter.level(), fighter.getX() + 8.0D, fighter.getY(), fighter.getZ() + 8.0D,
                player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.literal("[LW] Debug observation hold: Herobrine will remain here for about 10 seconds unless attacked."), false);
        return 1;
    }

    public static String status(ServerPlayer player) {
        WorldMenaceData data = WorldMenaceData.get(player.serverLevel());
        AmbientFighterEntity loaded = findLoaded(player.getServer(), data.entityId());
        if (loaded != null) return "Active • " + moodSummary(loaded).split(" — ")[0] + (data.deaths() > 0 ? " • returned " + data.deaths() + "x" : "");
        if (data.active()) return "Active • whereabouts unknown" + (data.deaths() > 0 ? " • returned " + data.deaths() + "x" : "");
        if (!data.initialized()) return "Not yet seen";
        long now = player.getServer().overworld().getGameTime();
        long ticks = Math.max(0L, data.returnAt() - now);
        return ticks <= 0 ? "Eligible to return" : "Absent • may return in about " + Math.max(1L, ticks / 24000L) + "d";
    }

    /** Watch scenes are process-local presentation state; the menace itself remains in SavedData. */
    public static void clearRuntime() { WATCHES.clear(); }
}
