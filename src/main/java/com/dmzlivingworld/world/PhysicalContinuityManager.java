package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges Persistent Lives' coarse off-screen simulation to physical Minecraft entities.
 * Important remembered fighters enter/leave through visible movement instead of appearing or
 * disappearing beside the player. No chunks are force-loaded and no second progression model
 * is introduced here; the manager only controls the hand-off between abstract and loaded life.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhysicalContinuityManager {
    private static final String ARRIVING = "LWContinuityArriving";
    private static final String DEPARTING = "LWContinuityDeparting";
    private static final String TARGET_X = "LWContinuityTargetX";
    private static final String TARGET_Y = "LWContinuityTargetY";
    private static final String TARGET_Z = "LWContinuityTargetZ";
    private static final String DEPART_AT = "LWContinuityDepartAt";
    private static final long MIN_VISIBLE_STAY = 3_600L; // 3 minutes
    private static final int EXTRA_VISIBLE_STAY = 3_601;  // up to roughly 6 minutes
    private static long lastTick = Long.MIN_VALUE;

    private PhysicalContinuityManager() {}

    public static BlockPos chooseArrivalPoint(ServerPlayer player, CompoundTag profile, BlockPos preferred, UUID recordId) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        BlockPos origin = player.blockPosition();
        BlockPos target = preferred == null ? origin : preferred;
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        long seed = recordId == null ? 0L : recordId.getMostSignificantBits() ^ recordId.getLeastSignificantBits();
        double angle;
        if (len >= 32.0D) angle = Math.atan2(dz, dx);
        else angle = ((seed & 0xFFFFL) / 65535.0D) * Math.PI * 2.0D;
        int radius = 104 + (int)Math.floorMod(seed, 33L);
        int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
        int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos candidate = new BlockPos(x, y, z);
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, candidate, player.getRandom(), 0, 18, 32);
        return safe;
    }

    public static void beginArrival(ServerPlayer player, AmbientFighterEntity fighter, CompoundTag record,
                                    BlockPos preferred, boolean forced) {
        if (player == null || fighter == null || forced) return;
        CompoundTag data = fighter.getPersistentData();
        long now = player.getServer().overworld().getGameTime();
        BlockPos target = preferred == null ? player.blockPosition() : preferred;
        String destination = record == null ? "" : record.getString("LifeDestination");
        boolean seekingPlayer = destination.contains("you")
                || (record != null && (record.getInt("Relationship") >= 55
                || (record.getInt("Relationship") <= -35 && record.getInt("BattlesVsPlayer") >= 2)));
        long seed = fighter.getUUID().getMostSignificantBits() ^ fighter.getUUID().getLeastSignificantBits();
        BlockPos playerPos = player.blockPosition();
        double routeDx = target.getX() - playerPos.getX();
        double routeDz = target.getZ() - playerPos.getZ();
        double routeLen = Math.sqrt(routeDx * routeDx + routeDz * routeDz);
        double angle = routeLen > 1.0D ? Math.atan2(routeDz, routeDx)
                : ((seed & 0xFFFFL) / 65535.0D) * Math.PI * 2.0D;
        // A normal recurring encounter represents two routes crossing: the fighter completes the
        // last leg into the player's local world rather than "arriving" 150 blocks away. Close
        // allies/nemeses intentionally come closer; ordinary crossings remain less intrusive.
        int radius = seekingPlayer ? 12 + (int)Math.floorMod(seed, 9L)
                : 34 + (int)Math.floorMod(seed >>> 8, 19L);
        int x = playerPos.getX() + (int)Math.round(Math.cos(angle) * radius);
        int z = playerPos.getZ() + (int)Math.round(Math.sin(angle) * radius);
        int y = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos roughTarget = new BlockPos(x, y, z);
        BlockPos safeTarget = AmbientFighterSpawner.findSafeGroundAround(player.serverLevel(), roughTarget,
                player.getRandom(), 0, 12, 28);
        if (safeTarget == null) safeTarget = AmbientFighterSpawner.findSafeGroundAround(player.serverLevel(),
                playerPos, player.getRandom(), seekingPlayer ? 10 : 28, seekingPlayer ? 26 : 64, 48);
        if (safeTarget == null) {
            // Defer this crossing rather than manufacture a water-surface arrival target.
            data.putBoolean(ARRIVING, false);
            data.putBoolean(DEPARTING, false);
            data.putLong(DEPART_AT, now + 1200L);
            return;
        }
        target = safeTarget;
        clearTravelRecovery(data);
        data.putBoolean(ARRIVING, true);
        data.putBoolean(DEPARTING, false);
        putTarget(data, target);
        data.putLong(DEPART_AT, now + MIN_VISIBLE_STAY + fighter.getRandom().nextInt(EXTRA_VISIBLE_STAY));
        fighter.setPersistenceRequired();
        fighter.setTarget(null);
        FighterCombatDirector.reset(fighter);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        // Ten ticks keeps route correction visually responsive while halving the largest
        // repeated 480-block remembered-fighter query in the mod.
        if (lastTick == now || now % 10L != 0L) return;
        lastTick = now;
        Set<UUID> processed = new HashSet<>();

        for (ServerPlayer owner : event.getServer().getPlayerList().getPlayers()) {
            if (!(owner.level() instanceof ServerLevel level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    owner.getBoundingBox().inflate(240.0D), f -> f.isAlive() && f.isRememberedFor(owner))) {
                if (!processed.add(fighter.getUUID())) continue;
                tickRemembered(owner, fighter, now);
            }
        }
    }

    private static void tickRemembered(ServerPlayer owner, AmbientFighterEntity fighter, long now) {
        if (fighter.isSanctionedMatchParticipant() || SparManager.isFighterInSpar(fighter) || fighter.isCaptive() || fighter.isDefeated()) return;
        CompoundTag data = fighter.getPersistentData();
        if (data.getBoolean(ARRIVING)) {
            tickArrival(owner, fighter, data, now);
            return;
        }
        if (data.getBoolean(DEPARTING)) {
            tickDeparture(owner, fighter, data);
            return;
        }

        // Existing loaded goal logic remains authoritative (rival pursuit, training meditation,
        // equipment pickup, etc.). Continuity only decides when the physical body should leave.
        if (fighter.getTarget() != null || fighter.isMeditating() || fighter.isAwakening() || fighter.isRetreating()) return;
        long departAt = data.getLong(DEPART_AT);
        if (departAt <= 0L) {
            data.putLong(DEPART_AT, now + MIN_VISIBLE_STAY + fighter.getRandom().nextInt(EXTRA_VISIBLE_STAY));
            return;
        }
        if (now < departAt || owner.distanceToSqr(fighter) < 48.0D * 48.0D || isObservedByAnyPlayer(owner.serverLevel(), fighter)) return;
        startDeparture(owner, fighter, data);
    }

    private static void tickArrival(ServerPlayer owner, AmbientFighterEntity fighter, CompoundTag data, long now) {
        BlockPos target = target(data, owner.blockPosition());
        double remaining = travelToward(fighter, target, true);
        if (remaining > 6.0D) {
            if (data.getInt("LWContinuityStuck") >= 12 && owner.distanceToSqr(fighter) > 72.0D * 72.0D
                    && !isObservedByAnyPlayer(owner.serverLevel(), fighter)) {
                // A ground route that cannot physically reach the local scene is abandoned quietly;
                // Persistent Lives can try a different crossing later instead of leaving "Travelling"
                // stuck forever.
                FighterMemoryManager.notePhysicalDeparture(owner, fighter, target);
                clearTravelRecovery(data);
                fighter.discard();
            }
            return;
        }

        data.putBoolean(ARRIVING, false);
        clearTravelRecovery(data);
        fighter.setDeltaMovement(0.0D, 0.0D, 0.0D);
        fighter.setFlying(false);
        fighter.setFlyingFast(false);
        fighter.setNoGravity(false);
        fighter.setCanFly(fighter.hasFlightUnlocked() && !fighter.isNonCombatant());
        data.putLong(DEPART_AT, now + MIN_VISIBLE_STAY + fighter.getRandom().nextInt(EXTRA_VISIBLE_STAY));
        FighterMemoryManager.notePhysicalArrival(owner, fighter);

        if (fighter.wasRescuedByMemoryOwner()) fighter.speak("I remember you. You saved me.", 78);
        else if (fighter.getMemoryRelationship() <= -20) fighter.speak("You again. Good.", 78);
        else fighter.speak("We meet again.", 78);

        if (fighter.getMemoryRelationship() <= -25 && fighter.getAlignment() == FighterAlignment.BAD) fighter.setTarget(owner);
    }

    private static void startDeparture(ServerPlayer owner, AmbientFighterEntity fighter, CompoundTag data) {
        BlockPos target = FighterMemoryManager.continuityTarget(owner, fighter);
        if (target == null || target.distSqr(fighter.blockPosition()) < 24.0D * 24.0D) {
            double dx = fighter.getX() - owner.getX();
            double dz = fighter.getZ() - owner.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0D) {
                double angle = fighter.getRandom().nextDouble() * Math.PI * 2.0D;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                len = 1.0D;
            }
            int x = fighter.blockPosition().getX() + (int)Math.round(dx / len * 180.0D);
            int z = fighter.blockPosition().getZ() + (int)Math.round(dz / len * 180.0D);
            int y = owner.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos rough = new BlockPos(x, y, z);
            BlockPos dry = AmbientFighterSpawner.findSafeGroundAround(owner.serverLevel(), rough,
                    fighter.getRandom(), 0, 18, 36);
            target = dry == null ? fighter.blockPosition() : dry;
        } else {
            BlockPos dry = AmbientFighterSpawner.findSafeGroundAround(owner.serverLevel(), target,
                    fighter.getRandom(), 0, 8, 24);
            if (dry != null) target = dry;
        }
        clearTravelRecovery(data);
        data.putBoolean(DEPARTING, true);
        data.putBoolean(ARRIVING, false);
        putTarget(data, target);
        fighter.setTarget(null);
        FighterCombatDirector.reset(fighter);
    }

    private static void tickDeparture(ServerPlayer owner, AmbientFighterEntity fighter, CompoundTag data) {
        if (fighter.getTarget() != null) {
            data.putBoolean(DEPARTING, false);
            return;
        }
        BlockPos target = target(data, fighter.blockPosition());
        travelToward(fighter, target, false);

        double ownerDist = owner.distanceToSqr(fighter);
        int stall = data.getInt("LWContinuityStuck");
        if (stall >= 12 && ownerDist > 72.0D * 72.0D && !isObservedByAnyPlayer(owner.serverLevel(), fighter)) {
            // Final invisible fail-safe for pathological terrain/chunk borders. The physical body
            // has already left observation range, so hand off to Persistent Lives rather than
            // leave a forever-stuck entity consuming a loaded slot. No visible teleport occurs.
            FighterMemoryManager.notePhysicalDeparture(owner, fighter, target);
            clearTravelRecovery(data);
            fighter.discard();
            return;
        }
        if (ownerDist <= 112.0D * 112.0D || isObservedByAnyPlayer(owner.serverLevel(), fighter)) return;

        // Handoff point: the player can no longer see the body, so exact position/profile are
        // saved and the lightweight Persistent Lives simulation resumes from here.
        FighterMemoryManager.notePhysicalDeparture(owner, fighter, target);
        fighter.discard();
    }

    private static double travelToward(AmbientFighterEntity fighter, BlockPos target, boolean arrival) {
        double tx = target.getX() + 0.5D, tz = target.getZ() + 0.5D;
        double ty = target.getY() + (fighter.hasFlightUnlocked() ? (arrival ? 2.0D : 10.0D) : 0.0D);
        Vec3 finalTarget = new Vec3(tx, ty, tz);
        Vec3 delta = finalTarget.subtract(fighter.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double distance = delta.length();
        if (distance < 0.001D) {
            clearTravelRecovery(fighter.getPersistentData());
            return 0.0D;
        }

        fighter.setTarget(null);
        CompoundTag data = fighter.getPersistentData();
        ServerLevel level = (ServerLevel) fighter.level();
        long now = level.getGameTime();
        int stall = updateTravelProgress(data, distance, horizontal, now);

        // Continuity travel now moves in short physical legs instead of asking vanilla navigation
        // to solve an entire 100-180 block route in one path.  This is especially important around
        // chunk edges: a local route can be recomputed as terrain becomes available, while a failed
        // long-distance Path object otherwise leaves the fighter standing forever.
        Vec3 waypoint = localTravelWaypoint(fighter, level, finalTarget, stall, now);
        boolean canFly = fighter.hasFlightUnlocked() && !fighter.isNonCombatant();
        boolean forceFlightRecovery = canFly && stall >= 4;
        boolean cruiseFlight = data.getBoolean("LWContinuityCruiseFlight");
        boolean usefulFlight = canFly && (forceFlightRecovery || horizontal > 18.0D
                || Math.abs(finalTarget.y - fighter.getY()) > 4.0D || (cruiseFlight && horizontal > 6.0D));

        if (usefulFlight) {
            data.putBoolean("LWContinuityCruiseFlight", true);
            fighter.getNavigation().stop();
            fighter.setCanFly(true);
            fighter.setFlying(true);
            fighter.setNoGravity(true);
            fighter.setFlyingFast(distance > 34.0D || stall >= 3);
            fighter.setSprinting(false);

            Vec3 flightTarget = waypoint;
            if (stall >= 3) {
                // Hold one escape leg long enough to actually fly through it. Rebuilding the target
                // from the fighter's new position every tick made the target climb/move with them and
                // caused the visible vertical oscillation reported on continuity travel.
                flightTarget = stableFlightEscapeWaypoint(fighter, data, finalTarget, stall, now);
            } else {
                clearFlightEscape(data);
            }

            double speed = stall >= 5 ? 0.82D : distance > 40.0D ? 0.72D : 0.48D;
            fighter.steerAmbientFlightToward(flightTarget, speed);
            fighter.getLookControl().setLookAt(flightTarget.x, flightTarget.y + 0.7D, flightTarget.z, 70.0F, 70.0F);
        } else {
            data.putBoolean("LWContinuityCruiseFlight", false);
            clearFlightEscape(data);
            if (fighter.isFlying()) fighter.setFlying(false);
            fighter.setFlyingFast(false);
            fighter.setNoGravity(false);
            fighter.setCanFly(fighter.hasFlightUnlocked());
            fighter.setSprinting(horizontal > 12.0D);
            fighter.setLocomotionMode(horizontal > 8.0D ? DBSagasEntity.LocomotionMode.RUN : DBSagasEntity.LocomotionMode.WALK);

            // Repath frequently to the short leg. A stall forces a fresh lateral candidate instead
            // of repeatedly submitting the same dead Path to vanilla navigation.
            if (fighter.getNavigation().isDone() || fighter.tickCount % 8 == 0 || stall >= 2) {
                fighter.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z,
                        stall >= 3 ? 1.32D : horizontal > 12.0D ? 1.22D : 1.08D);
            }

            if (fighter.onGround() && stall >= 2) {
                Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
                if (flat.lengthSqr() > 0.01D) {
                    Vec3 probe = fighter.position().add(flat.normalize().scale(1.15D));
                    BlockPos ahead = BlockPos.containing(probe.x, fighter.getY() + 0.2D, probe.z);
                    if (stall >= 3 || travelBlocked(level, ahead)
                            || travelBlocked(level, ahead.above())) fighter.getJumpControl().jump();
                }
            }
        }

        fighter.getLookControl().setLookAt(waypoint.x, waypoint.y + 1.0D, waypoint.z, 70.0F, 70.0F);
        return distance;
    }

    /** Tracks both route distance and horizontal progress so climbing over terrain is not mistaken for a stall. */
    private static int updateTravelProgress(CompoundTag data, double distance, double horizontal, long now) {
        if (!data.contains("LWContinuityProgressDistance")) {
            data.putDouble("LWContinuityProgressDistance", distance);
            data.putDouble("LWContinuityProgressHorizontal", horizontal);
            data.putLong("LWContinuityProgressAt", now);
            data.putInt("LWContinuityStuck", 0);
            return 0;
        }
        long sampledAt = data.getLong("LWContinuityProgressAt");
        int stall = data.getInt("LWContinuityStuck");
        if (now - sampledAt < 20L) return stall;

        double previous = data.getDouble("LWContinuityProgressDistance");
        double previousHorizontal = data.contains("LWContinuityProgressHorizontal")
                ? data.getDouble("LWContinuityProgressHorizontal") : horizontal;
        double improvement = previous - distance;
        double horizontalImprovement = previousHorizontal - horizontal;
        if (horizontal <= 4.0D || improvement >= 0.75D || horizontalImprovement >= 0.55D) {
            stall = Math.max(0, stall - 2);
        } else if (improvement >= 0.22D || horizontalImprovement >= 0.18D) {
            stall = Math.max(0, stall - 1);
        } else {
            stall = Math.min(20, stall + 1);
        }

        data.putDouble("LWContinuityProgressDistance", distance);
        data.putDouble("LWContinuityProgressHorizontal", horizontal);
        data.putLong("LWContinuityProgressAt", now);
        data.putInt("LWContinuityStuck", stall);
        if (stall < 2) clearFlightEscape(data);
        return stall;
    }

    private static Vec3 stableFlightEscapeWaypoint(AmbientFighterEntity fighter, CompoundTag data,
                                                     Vec3 finalTarget, int stall, long now) {
        if (data.contains("LWContinuityEscapeX") && now < data.getLong("LWContinuityEscapeUntil")) {
            Vec3 held = new Vec3(data.getDouble("LWContinuityEscapeX"), data.getDouble("LWContinuityEscapeY"),
                    data.getDouble("LWContinuityEscapeZ"));
            if (fighter.position().distanceToSqr(held) > 2.25D * 2.25D) return held;
        }

        Vec3 toward = finalTarget.subtract(fighter.position());
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        Vec3 dir = flat.lengthSqr() > 0.01D ? flat.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        double sign = ((fighter.getUUID().hashCode() + stall) & 1) == 0 ? 1.0D : -1.0D;
        Vec3 lateral = new Vec3(-dir.z, 0.0D, dir.x).scale((2.5D + Math.min(4, stall) * 0.7D) * sign);
        double escapeY = Math.max(fighter.getY() + 3.25D, Math.min(finalTarget.y + 4.0D, fighter.getY() + 6.0D));
        Vec3 held = fighter.position().add(dir.scale(5.0D + Math.min(4, stall))).add(lateral);
        held = new Vec3(held.x, escapeY, held.z);
        data.putDouble("LWContinuityEscapeX", held.x);
        data.putDouble("LWContinuityEscapeY", held.y);
        data.putDouble("LWContinuityEscapeZ", held.z);
        data.putLong("LWContinuityEscapeUntil", now + 36L);
        return held;
    }

    private static void clearFlightEscape(CompoundTag data) {
        if (data == null) return;
        data.remove("LWContinuityEscapeX");
        data.remove("LWContinuityEscapeY");
        data.remove("LWContinuityEscapeZ");
        data.remove("LWContinuityEscapeUntil");
    }

    /**
     * Returns a nearby leg of the route that vanilla pathfinding can realistically solve with the
     * chunks currently loaded. Stalls add a deterministic lateral offset so the next leg is not the
     * same blocked line again.
     */
    private static Vec3 localTravelWaypoint(AmbientFighterEntity fighter, ServerLevel level, Vec3 finalTarget,
                                            int stall, long now) {
        CompoundTag data = fighter.getPersistentData();
        int waypointStall = data.getInt("LWContinuityWaypointStall");
        boolean refresh = !data.contains("LWContinuityWaypointX")
                || now >= data.getLong("LWContinuityWaypointUntil") || (stall >= 2 && stall != waypointStall);
        if (!refresh) return new Vec3(data.getDouble("LWContinuityWaypointX"),
                data.getDouble("LWContinuityWaypointY"), data.getDouble("LWContinuityWaypointZ"));

        Vec3 toward = finalTarget.subtract(fighter.position());
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        double horizontal = Math.sqrt(flat.lengthSqr());
        if (horizontal <= 6.0D) return finalTarget;

        Vec3 dir = flat.normalize();
        double leg = Math.min(horizontal, stall >= 2 ? 6.0D : 10.0D);
        Vec3 candidate = fighter.position().add(dir.scale(leg));
        if (stall >= 2) {
            double sign = ((fighter.getUUID().hashCode() + stall) & 1) == 0 ? 1.0D : -1.0D;
            double side = 2.2D + Math.min(5, stall) * 0.65D;
            candidate = candidate.add(new Vec3(-dir.z, 0.0D, dir.x).scale(side * sign));
        }

        BlockPos rough = BlockPos.containing(candidate.x, fighter.getY(), candidate.z);
        if (!level.hasChunkAt(rough)) {
            // Never turn continuity recovery into chunk forcing. If the next leg crosses the
            // currently loaded edge, shorten it and let the next tick retry as chunks become live.
            candidate = fighter.position().add(dir.scale(Math.min(3.5D, leg)));
            rough = BlockPos.containing(candidate.x, fighter.getY(), candidate.z);
        }
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, rough,
                fighter.getRandom(), 0, stall >= 2 ? 4 : 2, stall >= 2 ? 18 : 10);
        boolean canFly = fighter.hasFlightUnlocked() && !fighter.isNonCombatant();
        Vec3 waypoint;
        if (safe != null) waypoint = Vec3.atBottomCenterOf(safe);
        else if (canFly) {
            // Flyers do not need a dry surface for an intermediate leg.
            waypoint = candidate;
        } else {
            // No dry intermediate step exists. A ground traveller holds position rather than
            // walking onto a water heightmap fallback and bouncing back to shore forever.
            waypoint = fighter.position();
        }

        // Flying fighters can keep a little altitude on each local leg; ground fighters use the
        // safe surface Y. The final ground block is retained by the caller for actual completion.
        if (canFly) {
            waypoint = new Vec3(waypoint.x, Math.max(waypoint.y + 2.0D,
                    Math.min(finalTarget.y, fighter.getY() + 5.0D)), waypoint.z);
        }
        data.putDouble("LWContinuityWaypointX", waypoint.x);
        data.putDouble("LWContinuityWaypointY", waypoint.y);
        data.putDouble("LWContinuityWaypointZ", waypoint.z);
        data.putLong("LWContinuityWaypointUntil", now + (stall >= 2 ? 16L : 32L));
        data.putInt("LWContinuityWaypointStall", stall);
        return waypoint;
    }

    private static boolean travelBlocked(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void clearTravelRecovery(CompoundTag data) {
        if (data == null) return;
        data.remove("LWContinuityProgressDistance");
        data.remove("LWContinuityProgressHorizontal");
        data.remove("LWContinuityProgressAt");
        data.remove("LWContinuityStuck");
        data.remove("LWContinuityWaypointX");
        data.remove("LWContinuityWaypointY");
        data.remove("LWContinuityWaypointZ");
        data.remove("LWContinuityWaypointUntil");
        data.remove("LWContinuityWaypointStall");
        data.remove("LWContinuityGroundFallbackUntil");
        data.remove("LWContinuityProgressX");
        data.remove("LWContinuityProgressZ");
        data.remove("LWContinuityCruiseFlight");
        clearFlightEscape(data);
    }

    private static boolean isObservedByAnyPlayer(ServerLevel level, AmbientFighterEntity fighter) {
        for (ServerPlayer player : level.players()) {
            double d2 = player.distanceToSqr(fighter);
            if (d2 <= 64.0D * 64.0D) return true;
            if (d2 <= 160.0D * 160.0D && player.hasLineOfSight(fighter)) return true;
        }
        return false;
    }

    private static void putTarget(CompoundTag data, BlockPos target) {
        data.putInt(TARGET_X, target.getX());
        data.putInt(TARGET_Y, target.getY());
        data.putInt(TARGET_Z, target.getZ());
    }

    private static BlockPos target(CompoundTag data, BlockPos fallback) {
        if (!data.contains(TARGET_X) || !data.contains(TARGET_Z)) return fallback;
        return new BlockPos(data.getInt(TARGET_X), data.getInt(TARGET_Y), data.getInt(TARGET_Z));
    }

    public static boolean isTransitioning(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        CompoundTag data = fighter.getPersistentData();
        return data.getBoolean(ARRIVING) || data.getBoolean(DEPARTING);
    }

    public static void clearRuntime() { lastTick = Long.MIN_VALUE; }
}
