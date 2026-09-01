package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Short, observable micro-behaviours that make strong moods readable between the larger ambient
 * activities. They never replace combat AI: this manager is called only from ordinary idle life.
 */
public final class ReactiveMoodBehaviorManager {
    private static final String NEXT = "LWReactiveMoodMicroNext";
    private static final String KIND = "LWReactiveMoodMicroKind";
    private static final String UNTIL = "LWReactiveMoodMicroUntil";

    private ReactiveMoodBehaviorManager() {}

    public static void reset(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        CompoundTag data = fighter.getPersistentData();
        data.remove(KIND); data.remove(UNTIL); data.remove(NEXT);
    }

    /** @return true when a short mood scene owns this tick's idle locomotion. */
    public static boolean tickIdle(AmbientFighterEntity fighter, ServerLevel level) {
        if (fighter == null || level == null || fighter.getTarget() != null || fighter.isFlying()
                || fighter.isSocialLifeActivity() || fighter.isSocialPlayerApproach()
                || fighter.isSocialPowerDisplay() || LivingBondManager.isTravellingCompanion(fighter)
                || WorldMenaceManager.isHerobrine(fighter)) return false;

        int strength = ReactiveWorldManager.moodStrength(fighter);
        if (strength < 60) {
            clearScene(fighter, false);
            return false;
        }

        long now = level.getGameTime();
        CompoundTag data = fighter.getPersistentData();
        String kind = data.getString(KIND);
        long until = data.getLong(UNTIL);
        if (!kind.isBlank() && until > now) return tickScene(fighter, kind, now);
        if (!kind.isBlank()) clearScene(fighter, false);

        long next = data.getLong(NEXT);
        if (next <= 0L) {
            data.putLong(NEXT, now + 45L + fighter.getRandom().nextInt(85));
            return false;
        }
        if (now < next) return false;

        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        switch (mood) {
            case WEARY -> {
                // Weary people visibly stop and recover their breath instead of endlessly pathing.
                if (fighter.getRandom().nextFloat() < 0.78F) {
                    startScene(fighter, "WEARY_PAUSE", now, 75, 155, 135, 260);
                    return true;
                }
                FighterAmbientActivityManager.nudgeSoon(fighter);
                data.putLong(NEXT, now + 120L + fighter.getRandom().nextInt(180));
            }
            case WARY -> {
                // Wary is active vigilance: stop, scan the horizon, then continue patrolling.
                startScene(fighter, "WARY_SCAN", now, 45, 90, 70, 145);
                return true;
            }
            case SOMBER -> {
                // Somber is withdrawn, not "lie sideways on the floor". Long quiet pauses sell it.
                if (fighter.getRandom().nextFloat() < 0.68F) {
                    startScene(fighter, "SOMBER_PAUSE", now, 80, 170, 150, 310);
                    return true;
                }
                data.putLong(NEXT, now + 140L + fighter.getRandom().nextInt(200));
            }
            case IRRITATED -> {
                // Personal space is a behavioural consequence: nearby people make them physically step away.
                if (startTakeSpace(fighter, level, now)) return true;
                startScene(fighter, "IRRITATED_PAUSE", now, 35, 70, 70, 145);
                return true;
            }
            case FOCUSED -> {
                if (fighter.getRandom().nextFloat() < 0.48F) {
                    startScene(fighter, "FOCUSED_SCAN", now, 28, 55, 85, 160);
                    return true;
                }
                data.putLong(NEXT, now + 90L + fighter.getRandom().nextInt(150));
            }
            case UPBEAT, CONTENT -> data.putLong(NEXT, now + 180L + fighter.getRandom().nextInt(260));
        }
        return false;
    }

    private static boolean tickScene(AmbientFighterEntity fighter, String kind, long now) {
        switch (kind) {
            case "WARY_SCAN" -> {
                fighter.getNavigation().stop();
                stopHorizontal(fighter);
                double angle = Math.toRadians(fighter.yBodyRot + Math.sin(now * 0.085D) * 78.0D);
                fighter.getLookControl().setLookAt(fighter.getX() - Math.sin(angle) * 13.0D,
                        fighter.getEyeY() + 0.45D, fighter.getZ() + Math.cos(angle) * 13.0D, 24.0F, 24.0F);
                return true;
            }
            case "FOCUSED_SCAN" -> {
                fighter.getNavigation().stop();
                stopHorizontal(fighter);
                double angle = Math.toRadians(fighter.yBodyRot + Math.sin(now * 0.055D) * 32.0D);
                fighter.getLookControl().setLookAt(fighter.getX() - Math.sin(angle) * 15.0D,
                        fighter.getEyeY() + 0.15D, fighter.getZ() + Math.cos(angle) * 15.0D, 16.0F, 14.0F);
                return true;
            }
            case "WEARY_PAUSE" -> {
                fighter.getNavigation().stop();
                stopHorizontal(fighter);
                double yaw = Math.toRadians(fighter.yBodyRot);
                fighter.getLookControl().setLookAt(fighter.getX() - Math.sin(yaw) * 3.0D,
                        fighter.getEyeY() - 0.85D, fighter.getZ() + Math.cos(yaw) * 3.0D, 8.0F, 7.0F);
                return true;
            }
            case "SOMBER_PAUSE" -> {
                fighter.getNavigation().stop();
                stopHorizontal(fighter);
                double yaw = Math.toRadians(fighter.yBodyRot + 18.0D);
                fighter.getLookControl().setLookAt(fighter.getX() - Math.sin(yaw) * 4.0D,
                        fighter.getEyeY() - 0.55D, fighter.getZ() + Math.cos(yaw) * 4.0D, 7.0F, 6.0F);
                return true;
            }
            case "IRRITATED_PAUSE" -> {
                fighter.getNavigation().stop();
                stopHorizontal(fighter);
                return true;
            }
            case "IRRITATED_SPACE" -> {
                if (fighter.getNavigation().isDone()) {
                    clearScene(fighter, false);
                    return false;
                }
                return true;
            }
            default -> {
                clearScene(fighter, false);
                return false;
            }
        }
    }

    private static boolean startTakeSpace(AmbientFighterEntity fighter, ServerLevel level, long now) {
        LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class, fighter.getBoundingBox().inflate(9.0D), other ->
                        other != fighter && other.isAlive() && (other instanceof AmbientFighterEntity || other instanceof ServerPlayer))
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (nearest == null) return false;

        Vec3 away = fighter.position().subtract(nearest.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(fighter.getRandom().nextDouble() - 0.5D, 0.0D, fighter.getRandom().nextDouble() - 0.5D);
        away = away.normalize().scale(9.0D + fighter.getRandom().nextDouble() * 6.0D);
        BlockPos rough = BlockPos.containing(fighter.position().add(away));
        BlockPos destination = AmbientFighterSpawner.findSafeGroundAround(level, rough, fighter.getRandom(), 2, 8, 18);
        if (destination == null) return false;

        fighter.setFlyingFast(false);
        fighter.setFlying(false);
        fighter.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                0.98D * ReactiveWorldManager.movementPace(fighter));
        CompoundTag data = fighter.getPersistentData();
        data.putString(KIND, "IRRITATED_SPACE");
        data.putLong(UNTIL, now + 90L + fighter.getRandom().nextInt(60));
        data.putLong(NEXT, now + 130L + fighter.getRandom().nextInt(150));
        return true;
    }

    private static void startScene(AmbientFighterEntity fighter, String kind, long now,
                                   int minDuration, int durationJitter, int minNext, int nextJitter) {
        CompoundTag data = fighter.getPersistentData();
        data.putString(KIND, kind);
        data.putLong(UNTIL, now + minDuration + fighter.getRandom().nextInt(Math.max(1, durationJitter + 1)));
        data.putLong(NEXT, now + minNext + fighter.getRandom().nextInt(Math.max(1, nextJitter + 1)));
        fighter.setFlyingFast(false);
        fighter.setFlying(false);
        fighter.getNavigation().stop();
        stopHorizontal(fighter);
    }

    private static void clearScene(AmbientFighterEntity fighter, boolean clearNext) {
        CompoundTag data = fighter.getPersistentData();
        data.remove(KIND); data.remove(UNTIL);
        if (clearNext) data.remove(NEXT);
    }

    private static void stopHorizontal(AmbientFighterEntity fighter) {
        Vec3 motion = fighter.getDeltaMovement();
        fighter.setDeltaMovement(0.0D, motion.y, 0.0D);
    }
}
