package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Earth Guardian Corps response to Living World violence only. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PeacekeeperManager {
    private static final String PLAYER_ROOT = "DMZLivingWorldPeacekeeper";
    private static final long PLAYER_RESPONSE_COOLDOWN = 2400L;
    private static final String RESPONSIBILITY_PLAYER = "LWAggressionPlayer";
    private static final String RESPONSIBILITY_UNTIL = "LWAggressionUntil";
    private static final String RESPONSIBILITY_NPC_STARTED = "LWAggressionNpcStarted";
    private static final long RESPONSIBILITY_TICKS = 1200L; // one minute; refreshed by continuing violence

    private PeacekeeperManager() {}

    public static void maybeInterveneInClash(ServerPlayer player, WorldFaction first, WorldFaction second,
                                             List<AmbientFighterEntity> firstSide,
                                             List<AmbientFighterEntity> secondSide,
                                             boolean debug) {
        if (debug || player == null || !(player.level() instanceof ServerLevel level)) return;
        if (LivingWorldDimensions.realm(level) != FactionRealm.EARTH) return;
        if (isPlayerBusyWithNativeSaga(player)) return; // LW does not police DMZ saga fights.
        if (first == null || second == null || firstSide == null || secondSide == null) return;

        WorldFaction guardians = FactionWorldData.get(level).earthGuardians();
        if (guardians == null || first.id().equals(guardians.id()) || second.id().equals(guardians.id())) return;
        if (!hasBadSide(first, second) || hasPeacekeepersNearby(level, player.blockPosition(), 150.0D)) return;

        long now = level.getServer().overworld().getGameTime();
        boolean war = FactionWorldData.get(level).isAtWar(first, second, now);
        if (!war && FactionManager.relation(level, first, second) != FactionRelation.ENEMY) return;
        float chance = war ? 0.12F : 0.045F;
        if (player.getRandom().nextFloat() >= scaledResponseChance(chance)) return;

        spawnAgainstSides(player, guardians, first, second, firstSide, secondSide, false);
    }

    /** Reputation-based rescue response when a Living World mugger directly targets the player. */
    public static void maybeAidMuggedPlayer(ServerPlayer player, AmbientFighterEntity mugger) {
        if (player == null || mugger == null || !mugger.isAlive() || !(player.level() instanceof ServerLevel level)) return;
        markNpcAggressor(player, mugger);
        if (LivingWorldDimensions.realm(level) != FactionRealm.EARTH || isPlayerBusyWithNativeSaga(player)) return;
        WorldFaction guardians = FactionWorldData.get(level).earthGuardians();
        if (guardians == null) return;
        int rep = FactionManager.getReputation(player, guardians);
        // Friendly standing still receives the established high-probability response. Neutral Earth
        // visitors can now receive help too, while clearly hostile reputation does not call the Corps.
        if (rep <= FactionManager.HOSTILE_REP) return;
        float chance = rep >= FactionManager.FRIENDLY_REP
                ? Math.min(0.92F, 0.48F + Math.max(0, rep - FactionManager.FRIENDLY_REP) * 0.006F)
                : Math.min(0.34F, 0.16F + Math.max(0, rep) * 0.003F);
        if (player.getRandom().nextFloat() > scaledResponseChance(chance)) return;

        long now = level.getServer().overworld().getGameTime();
        List<AmbientFighterEntity> aggressors = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(96.0D), f -> f.isAlive() && isNpcAggressorFor(f, player, now));
        if (!aggressors.contains(mugger)) aggressors.add(0, mugger);
        List<AmbientFighterEntity> nearby = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(92.0D), f -> f.isAlive() && guardians.id().equals(f.getFactionId())
                        && !f.isCaptive() && !f.isDefeated() && !aggressors.contains(f));
        nearby.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        int helpers = 0;
        AmbientFighterEntity firstHelper = null;
        for (AmbientFighterEntity guard : nearby) {
            if (helpers >= 2) break;
            AmbientFighterEntity target = aggressors.get(helpers % aggressors.size());
            if (guard.getTarget() != null && !aggressors.contains(guard.getTarget())) continue;
            FighterAmbientActivityManager.cancel(guard);
            guard.setSocialLifeActivity(false);
            guard.setStoryRole(AmbientFighterEntity.STORY_PEACEKEEPER);
            guard.setTarget(target);
            if (firstHelper == null) firstHelper = guard;
            helpers++;
        }
        if (helpers > 0) {
            firstHelper.speak("Earth Guardian Corps! Get away from them.", 78);
            player.displayClientMessage(Component.literal("Living World • Earth Guardian Corps responding to the attack"), true);
            FactionActivityRegistry.acquire(level, guardians, 1200L);
            return;
        }

        BlockPos anchor = AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 18, 32, 28);
        if (anchor == null) return;
        int count = rep >= 70 && player.getRandom().nextFloat() < 0.55F ? 2 : 1;
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> response = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FactionRole role = i == 0 ? FactionRole.ENFORCER : FactionRole.MEMBER;
            AmbientFighterEntity guard = FactionEncounterManager.spawnMember(player, guardians, anchor, party,
                    i == 0, role, i == 0 ? FighterRank.VETERAN : FighterRank.TRAINED, false);
            if (guard == null) continue;
            guard.setStoryRole(AmbientFighterEntity.STORY_PEACEKEEPER);
            guard.setTarget(aggressors.get(i % aggressors.size()));
            response.add(guard);
        }
        if (!response.isEmpty()) {
            response.get(0).speak("Earth Guardian Corps! Step away from them.", 78);
            player.displayClientMessage(Component.literal("Living World • Guardian response team incoming"), true);
            FactionActivityRegistry.acquire(level, guardians, 1400L);
        }
    }

    /** Records the first actual Living World fighter hit against a player, including fighter-owned Ki. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onNpcHurtsPlayer(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        AmbientFighterEntity attacker = responsibleFighter(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || attacker.isPostSparOpponent(player) || attacker.isSanctionedMatchParticipant()) return;
        noteNpcAttack(player, attacker);
    }

    /** Scripted/declared hostile intent can register the NPC as initiator before the player retaliates. */
    public static void markNpcAggressor(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        writeFirstAggression(fighter, player, true, now);
    }

    private static void noteNpcAttack(ServerPlayer player, AmbientFighterEntity fighter) {
        long now = player.serverLevel().getServer().overworld().getGameTime();
        // Never flip an already-live player-started record merely because the NPC retaliated.
        writeFirstAggression(fighter, player, true, now);
    }

    private static void notePlayerAttack(ServerPlayer player, AmbientFighterEntity fighter, long now) {
        // Likewise, retaliation cannot overwrite an NPC-started record.
        writeFirstAggression(fighter, player, false, now);
    }

    private static void writeFirstAggression(AmbientFighterEntity fighter, ServerPlayer player,
                                             boolean npcStarted, long now) {
        CompoundTag data = fighter.getPersistentData();
        boolean samePlayer = data.hasUUID(RESPONSIBILITY_PLAYER)
                && player.getUUID().equals(data.getUUID(RESPONSIBILITY_PLAYER));
        boolean active = samePlayer && data.getLong(RESPONSIBILITY_UNTIL) >= now;
        if (active) {
            // First aggressor is immutable for the lifetime of the encounter. Scripted intent is
            // registered before targeting, so it wins legitimately without ever overwriting a
            // genuinely earlier player-started fight.
            data.putLong(RESPONSIBILITY_UNTIL, now + RESPONSIBILITY_TICKS);
            return;
        }
        data.putUUID(RESPONSIBILITY_PLAYER, player.getUUID());
        data.putBoolean(RESPONSIBILITY_NPC_STARTED, npcStarted);
        data.putLong(RESPONSIBILITY_UNTIL, now + RESPONSIBILITY_TICKS);
    }

    public static boolean isNpcAggressorFor(AmbientFighterEntity fighter, ServerPlayer player, long now) {
        if (fighter == null || player == null) return false;
        CompoundTag data = fighter.getPersistentData();
        if (!data.hasUUID(RESPONSIBILITY_PLAYER) || !player.getUUID().equals(data.getUUID(RESPONSIBILITY_PLAYER))
                || data.getLong(RESPONSIBILITY_UNTIL) < now) return false;
        return data.getBoolean(RESPONSIBILITY_NPC_STARTED);
    }

    private static AmbientFighterEntity responsibleFighter(Entity sourceEntity, Entity directEntity) {
        if (sourceEntity instanceof AmbientFighterEntity fighter) return fighter;
        if (directEntity instanceof AbstractKiProjectile projectile && projectile.getOwner() instanceof AmbientFighterEntity fighter) return fighter;
        return null;
    }

    /** Called exclusively from AmbientFighterEntity.hurt: native saga NPC damage never reaches this path. */
    public static void onPlayerAggression(ServerPlayer player, AmbientFighterEntity victim, DamageSource source) {
        if (player == null || victim == null || source == null || !(player.level() instanceof ServerLevel level)) return;
        if (LivingWorldDimensions.realm(level) != FactionRealm.EARTH) return;
        if (victim.getPersistentData().contains("DMZLWNpcFusionTemp")) return;
        // World Menaces are authored global threats, not civilian assaults. Keep this generic so
        // every current/future menace added to WorldMenaceManager inherits the exemption.
        if (WorldMenaceManager.isWorldMenace(victim)) return;
        // Friendly-Fist mercy against a faction already hostile-on-sight is explicit self-defense.
        if (MercyManager.isHostileFactionMercyFight(player, victim)) {
            markNpcAggressor(player, victim);
            return;
        }

        long now = level.getServer().overworld().getGameTime();
        if (isNpcAggressorFor(victim, player, now)) {
            // Exact pair-specific self-defense: the Corps may still decide not to intervene in the
            // underlying feud, but it will not reinterpret this retaliation as a fresh assault.
            victim.getPersistentData().putLong(RESPONSIBILITY_UNTIL, now + RESPONSIBILITY_TICKS);
            return;
        }
        notePlayerAttack(player, victim, now);
        if (victim.getAlignment() == FighterAlignment.BAD && !victim.isNonCombatant()) return;
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_ROOT);
        long last = root.getLong("LastResponse");
        int incidents = root.getInt("Incidents");
        long lastIncident = root.getLong("LastIncident");
        if (now - lastIncident > 1200L) incidents = 0;

        boolean directMelee = source.getDirectEntity() == player;
        // Projectile/AOE collateral is intentionally less incriminating. A single stray
        // blast while fighting something else should not summon Living World police.
        int add = directMelee ? 2 : 1;
        incidents = Math.min(12, incidents + add);
        root.putInt("Incidents", incidents);
        root.putLong("LastIncident", now);
        player.getPersistentData().put(PLAYER_ROOT, root);

        if (root.getLong("PendingDue") > now || now - last < PLAYER_RESPONSE_COOLDOWN) return;
        boolean serious = victim.isNonCombatant() ? (directMelee || incidents >= 3) : incidents >= 5;
        if (!serious) return;

        float chance = victim.isNonCombatant() ? (directMelee ? 0.62F : 0.34F) : 0.28F;
        if (player.getRandom().nextFloat() >= scaledResponseChance(chance)) return;
        if (hasPeacekeepersNearby(level, player.blockPosition(), 150.0D)) return;

        long delay = 100L + player.getRandom().nextInt(61); // 5-8 seconds warning.
        root.putLong("PendingDue", now + delay);
        root.putInt("PendingCount", 2 + (victim.isNonCombatant() ? 1 : 0));
        root.putString("PendingVictim", victim.getFighterName());
        player.getPersistentData().put(PLAYER_ROOT, root);
        player.displayClientMessage(Component.literal("Living World • Earth Guardian Corps alerted — response inbound"), true);
    }

    /** Lightweight delayed-response processor, called from the existing player tick. */
    public static void tickPlayer(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        if (player.tickCount % 10 != Math.floorMod(player.getUUID().hashCode(), 10)) return;
        CompoundTag root = player.getPersistentData().getCompound(PLAYER_ROOT);
        long due = root.getLong("PendingDue");
        if (due <= 0L) return;
        long now = level.getServer().overworld().getGameTime();
        if (LivingWorldDimensions.realm(level) != FactionRealm.EARTH || now > due + 600L) {
            clearPending(player, root);
            return;
        }
        if (now < due) return;

        WorldFaction guardians = FactionWorldData.get(level).earthGuardians();
        int spawned = guardians == null ? 0 : spawnAgainstPlayer(player, guardians, Math.max(2, root.getInt("PendingCount")), false);
        if (spawned > 0) {
            root.putLong("LastResponse", now);
            root.putInt("Incidents", 0);
            player.displayClientMessage(Component.literal("Living World • Earth Guardian Corps arriving"), true);
        }
        clearPending(player, root);
    }

    private static void clearPending(ServerPlayer player, CompoundTag root) {
        root.remove("PendingDue");
        root.remove("PendingCount");
        root.remove("PendingVictim");
        player.getPersistentData().put(PLAYER_ROOT, root);
    }

    /** Operator/debug path: immediate response, deliberately exempt from the natural delay. */
    public static int debugSpawn(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)
                || LivingWorldDimensions.realm(level) != FactionRealm.EARTH) return 0;
        WorldFaction guardians = FactionWorldData.get(level).earthGuardians();
        if (guardians == null) return 0;
        return spawnAgainstPlayer(player, guardians, 3, true);
    }

    private static int spawnAgainstPlayer(ServerPlayer player, WorldFaction guardians, int count, boolean debug) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        if (hasPeacekeepersNearby(level, player.blockPosition(), 110.0D)) return 0;
        BlockPos anchor = debug
                ? AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 10, 16, 24)
                : AmbientFighterSpawner.findSafeGroundAround(level, player.blockPosition(), player.getRandom(), 36, 58, 34);
        if (anchor == null) anchor = player.blockPosition().offset(debug ? 10 : 38, 0, 0);
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FactionRole role = i == 0 ? FactionRole.ENFORCER : FactionRole.MEMBER;
            AmbientFighterEntity guard = FactionEncounterManager.spawnMember(player, guardians, anchor, party,
                    i == 0, role, i == 0 ? FighterRank.VETERAN : FighterRank.TRAINED, false);
            if (guard == null) continue;
            guard.setStoryRole(AmbientFighterEntity.STORY_PEACEKEEPER);
            guard.setTarget(player);
            spawned.add(guard);
        }
        if (!spawned.isEmpty()) {
            spawned.get(0).speak("Earth Guardian Corps. Stand down.", 72);
            FactionActivityRegistry.acquire(level, guardians, 1200L);
        }
        return spawned.size();
    }

    private static void spawnAgainstSides(ServerPlayer player, WorldFaction guardians,
                                          WorldFaction first, WorldFaction second,
                                          List<AmbientFighterEntity> firstSide,
                                          List<AmbientFighterEntity> secondSide,
                                          boolean forced) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos center = midpoint(firstSide, secondSide, player.blockPosition());
        BlockPos anchor = AmbientFighterSpawner.findSafeGroundAround(level, center, player.getRandom(), 26, 44, 28);
        if (anchor == null) anchor = center.offset(28, 0, 0);
        UUID party = UUID.randomUUID();
        List<AmbientFighterEntity> badTargets = new ArrayList<>();
        if (first.alignment() == FighterAlignment.BAD) badTargets.addAll(firstSide);
        if (second.alignment() == FighterAlignment.BAD) badTargets.addAll(secondSide);
        if (badTargets.isEmpty()) return;

        int count = forced ? 3 : 2 + (player.getRandom().nextFloat() < 0.35F ? 1 : 0);
        List<AmbientFighterEntity> guards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FactionRole role = i == 0 ? FactionRole.ENFORCER : FactionRole.MEMBER;
            AmbientFighterEntity guard = FactionEncounterManager.spawnMember(player, guardians, anchor, party,
                    i == 0, role, i == 0 ? FighterRank.VETERAN : FighterRank.TRAINED, false);
            if (guard == null) continue;
            guard.setStoryRole(AmbientFighterEntity.STORY_PEACEKEEPER);
            AmbientFighterEntity target = badTargets.get(i % badTargets.size());
            if (target != null && target.isAlive()) guard.setTarget(target);
            guards.add(guard);
        }
        if (!guards.isEmpty()) {
            guards.get(0).speak("Earth Guardian Corps! Break it up!", 72);
            player.displayClientMessage(Component.literal("Living World • Guardian signatures approaching the conflict"), true);
            FactionActivityRegistry.acquire(level, guardians, 1600L);
        }
    }

    private static float scaledResponseChance(float establishedChance) {
        double scale = LivingWorldConfig.earthGuardianResponseScale();
        if (scale <= 0.0D) return 0.0F;
        return (float)Math.max(0.0D, Math.min(0.98D, establishedChance * scale));
    }

    private static boolean isPlayerBusyWithNativeSaga(ServerPlayer player) {
        LivingEntity lastHit = player.getLastHurtMob();
        if (lastHit instanceof DBSagasEntity && !(lastHit instanceof AmbientFighterEntity) && lastHit.isAlive()) return true;
        LivingEntity lastAttacker = player.getLastHurtByMob();
        return lastAttacker instanceof DBSagasEntity && !(lastAttacker instanceof AmbientFighterEntity) && lastAttacker.isAlive();
    }

    private static boolean hasBadSide(WorldFaction first, WorldFaction second) {
        return first.alignment() == FighterAlignment.BAD || second.alignment() == FighterAlignment.BAD;
    }

    private static boolean hasPeacekeepersNearby(ServerLevel level, BlockPos center, double radius) {
        WorldFaction guardians = FactionWorldData.get(level).earthGuardians();
        if (guardians == null) return false;
        return !level.getEntitiesOfClass(AmbientFighterEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius),
                f -> f.isAlive() && guardians.id().equals(f.getFactionId())
                        && f.getStoryRole() == AmbientFighterEntity.STORY_PEACEKEEPER).isEmpty();
    }

    private static BlockPos midpoint(List<AmbientFighterEntity> first, List<AmbientFighterEntity> second, BlockPos fallback) {
        if (!first.isEmpty() && !second.isEmpty()) {
            AmbientFighterEntity a = first.get(0), b = second.get(0);
            return BlockPos.containing((a.getX() + b.getX()) * 0.5D, (a.getY() + b.getY()) * 0.5D,
                    (a.getZ() + b.getZ()) * 0.5D);
        }
        return fallback;
    }
}
