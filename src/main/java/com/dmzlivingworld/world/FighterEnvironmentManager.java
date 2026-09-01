package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;

/** Water safety, slow recovery, simple unsticking and sparse reactions to ordinary Minecraft life. */
public final class FighterEnvironmentManager {
    private FighterEnvironmentManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        tickWaterSafety(fighter);
        tickRecovery(fighter);
        if (fighter.tickCount % 40 == Math.floorMod(fighter.getUUID().hashCode(), 40)) tickStuckRecovery(fighter);
        if (fighter.tickCount % 700 == Math.floorMod(fighter.getUUID().hashCode(), 700)) maybeCommentOnAnimal(fighter);
    }

    private static void tickRecovery(AmbientFighterEntity fighter) {
        if (fighter.tickCount % 100 != Math.floorMod(fighter.getUUID().hashCode(), 100)) return;
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.isSanctionedMatchParticipant() || fighter.isKaiokenActive()) return;

        long now = fighter.level().getGameTime();
        long last = fighter.getPersistentData().getLong("LWLastDamageTime");
        LivingEntity target = fighter.getTarget();
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            fighter.setTarget(null);
            target = null;
        }
        // A stale distant target must not keep a survivor at effectively 0% health forever.
        // Only release it after the same ten seconds of genuine combat silence used by recovery.
        if (target != null && now - last >= 200L && fighter.distanceToSqr(target) > 2304.0D) {
            fighter.setTarget(null);
            target = null;
        }
        if (target != null || now - last < 200L || fighter.getHealth() >= fighter.getMaxHealth()) return;

        // Very large-health fighters can survive at one health, which rounds to 0% in the UI.
        // Give a genuinely out-of-combat survivor a small recovery floor, then let the established
        // passive-heal cadence take over on following recovery beats.
        if (fighter.getHealth() > 0.0F && fighter.getHealth() <= fighter.getMaxHealth() * 0.015F) {
            fighter.setHealth(Math.min(fighter.getMaxHealth(), Math.max(fighter.getHealth(), fighter.getMaxHealth() * 0.08F)));
            return;
        }
        fighter.heal(Math.max(0.5F, fighter.getMaxHealth() * 0.0125F));
    }

    private static void tickWaterSafety(AmbientFighterEntity fighter) {
        var data = fighter.getPersistentData();
        if (!fighter.isInWater()) {
            boolean wasEscaping = data.getBoolean("LWWaterEscape");
            data.putBoolean("LWWaterEscape", false);
            data.remove("LWWaterHasShore");
            data.remove("LWWaterShoreX"); data.remove("LWWaterShoreY"); data.remove("LWWaterShoreZ");
            if (fighter.isSwimming()) fighter.setSwimming(false);
            if (fighter.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !fighter.isSocialLifeActivity())
                fighter.setPose(net.minecraft.world.entity.Pose.STANDING);
            // A flyer that just escaped the water should resume useful movement quickly rather
            // than standing on the bank until the next long wander roll.
            if (wasEscaping && fighter.hasFlightUnlocked() && !fighter.isNonCombatant()) {
                long current = data.getLong("LWNextIdleWander");
                long soon = fighter.level().getGameTime() + 8L;
                data.putLong("LWNextIdleWander", current <= 0L ? soon : Math.min(current, soon));
            }
            return;
        }

        // A deliberate travel system already owns this movement. Flight-capable travellers must
        // be allowed to cross water instead of having WaterSafety cancel flight on one tick and
        // Travelling re-enable it on the next (the old bank/water/bank oscillation).
        boolean travelOwned = LivingBondManager.isTravellingCompanion(fighter)
                || FactionRequestMissionManager.isAssigned(fighter)
                || data.getBoolean("LWContinuityArriving") || data.getBoolean("LWContinuityDeparting");
        if (travelOwned && fighter.hasFlightUnlocked() && !fighter.isNonCombatant()) {
            data.putBoolean("LWWaterEscape", false);
            data.remove("LWWaterHasShore");
            if (fighter.isSwimming()) fighter.setSwimming(false);
            fighter.setCanFly(true);
            fighter.setFlying(true);
            fighter.setNoGravity(true);
            return;
        }

        // Ground travellers still use the real shore recovery, but their owning travel manager will
        // refuse wet waypoints afterward, so they cannot be sent straight back into the same water.
        data.putBoolean("LWWaterEscape", true);
        if (fighter.isAmbientFlightActivity()) FighterAmbientActivityManager.cancel(fighter);
        fighter.setAmbientFlightActivity(false);
        fighter.setFlying(false);
        fighter.setFlyingFast(false);
        fighter.setSprinting(false);
        fighter.setXRot(0.0F);
        fighter.xRotO = 0.0F;
        // Keep the real swimming flag for movement/pathfinding, but do not force the vanilla
        // SWIMMING presentation pose. Native DMZ combatants remain upright in water.
        fighter.setSwimming(true);
        FighterAmbientActivityManager.cancel(fighter);
        fighter.setSocialLifeActivity(false);

        BlockPos shore = null;
        boolean hasCached = data.getBoolean("LWWaterHasShore");
        if (hasCached) shore = new BlockPos(data.getInt("LWWaterShoreX"), data.getInt("LWWaterShoreY"), data.getInt("LWWaterShoreZ"));
        if (shore == null || fighter.tickCount % 20 == Math.floorMod(fighter.getUUID().hashCode(), 20)
                || !isDryStandingSpot(fighter, shore)) {
            shore = findShore(fighter);
            data.putBoolean("LWWaterHasShore", shore != null);
            if (shore != null) {
                data.putInt("LWWaterShoreX", shore.getX()); data.putInt("LWWaterShoreY", shore.getY()); data.putInt("LWWaterShoreZ", shore.getZ());
            }
        }

        Vec3 velocity = fighter.getDeltaMovement();
        if (shore != null) {
            Vec3 target = Vec3.atBottomCenterOf(shore).subtract(fighter.position());
            Vec3 horizontal = new Vec3(target.x, 0.0D, target.z);
            Vec3 push = horizontal.lengthSqr() > 1.0E-5D ? horizontal.normalize().scale(0.16D) : Vec3.ZERO;
            double rise = target.y > 0.5D || fighter.isEyeInFluid(FluidTags.WATER) ? 0.16D : 0.10D;
            fighter.setDeltaMovement(velocity.x * 0.55D + push.x, Math.max(velocity.y, rise), velocity.z * 0.55D + push.z);
            if (fighter.getNavigation().isDone() || fighter.tickCount % 20 == 0)
                fighter.getNavigation().moveTo(shore.getX() + 0.5D, shore.getY(), shore.getZ() + 0.5D, 1.28D);
            fighter.getLookControl().setLookAt(shore.getX() + 0.5D, shore.getY() + 0.8D, shore.getZ() + 0.5D, 25.0F, 25.0F);
        } else {
            // Deep/open water fallback: surface first. Once clear, normal idle flight can take over.
            fighter.setDeltaMovement(velocity.x * 0.72D, Math.max(velocity.y, 0.18D), velocity.z * 0.72D);
        }
    }

    public static boolean isEscapingWater(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getPersistentData().getBoolean("LWWaterEscape");
    }

    private static BlockPos findShore(AmbientFighterEntity fighter) {
        BlockPos origin = fighter.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (int r = 2; r <= 10; r += 2) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!isDryStandingSpot(fighter, pos)) continue;
                    double d = pos.distSqr(origin);
                    if (d < bestSq) { bestSq = d; best = pos.immutable(); }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean isDryStandingSpot(AmbientFighterEntity fighter, BlockPos pos) {
        if (fighter.level().getFluidState(pos).is(FluidTags.WATER) || fighter.level().getFluidState(pos.above()).is(FluidTags.WATER)) return false;
        BlockState feet = fighter.level().getBlockState(pos);
        BlockState head = fighter.level().getBlockState(pos.above());
        BlockState floor = fighter.level().getBlockState(pos.below());
        return feet.getCollisionShape(fighter.level(), pos).isEmpty()
                && head.getCollisionShape(fighter.level(), pos.above()).isEmpty()
                && !floor.getCollisionShape(fighter.level(), pos.below()).isEmpty();
    }

    private static void tickStuckRecovery(AmbientFighterEntity fighter) {
        boolean travelOwned = LivingBondManager.isTravellingCompanion(fighter)
                || FactionRequestMissionManager.isAssigned(fighter)
                || fighter.getPersistentData().getBoolean("LWContinuityArriving")
                || fighter.getPersistentData().getBoolean("LWContinuityDeparting");
        if (travelOwned || fighter.getNavigation().isDone() || fighter.getTarget() != null || fighter.isMeditating()
                || fighter.isSocialPlayerApproach() || fighter.isSocialPowerDisplay() || fighter.isDefeated() || fighter.isCaptive()) {
            fighter.getPersistentData().putInt("LWStuckTicks", 0);
            fighter.getPersistentData().putDouble("LWNavX", fighter.getX());
            fighter.getPersistentData().putDouble("LWNavZ", fighter.getZ());
            return;
        }
        double oldX = fighter.getPersistentData().contains("LWNavX") ? fighter.getPersistentData().getDouble("LWNavX") : fighter.getX();
        double oldZ = fighter.getPersistentData().contains("LWNavZ") ? fighter.getPersistentData().getDouble("LWNavZ") : fighter.getZ();
        double movedSq = (fighter.getX() - oldX) * (fighter.getX() - oldX) + (fighter.getZ() - oldZ) * (fighter.getZ() - oldZ);
        int stuck = movedSq < 0.20D ? fighter.getPersistentData().getInt("LWStuckTicks") + 40 : 0;
        fighter.getPersistentData().putInt("LWStuckTicks", stuck);
        fighter.getPersistentData().putDouble("LWNavX", fighter.getX());
        fighter.getPersistentData().putDouble("LWNavZ", fighter.getZ());
        if (stuck < 120) return;
        fighter.getNavigation().stop();
        fighter.getPersistentData().putInt("LWStuckTicks", 0);
        BlockPos base = fighter.blockPosition();
        for (int tries = 0; tries < 12; tries++) {
            int dx = fighter.getRandom().nextInt(17) - 8, dz = fighter.getRandom().nextInt(17) - 8;
            BlockPos pos = base.offset(dx, 0, dz);
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos candidate = pos.offset(0, dy, 0);
                if (isDryStandingSpot(fighter, candidate)) {
                    fighter.getNavigation().moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, 1.0D);
                    return;
                }
            }
        }
    }

    private static void maybeCommentOnAnimal(AmbientFighterEntity fighter) {
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        if (fighter.getTarget() != null || fighter.isMeditating() || fighter.isDefeated() || fighter.isCaptive()
                || fighter.isSocialLifeActivity() || !fighter.getSpeech().isEmpty() || fighter.getRandom().nextFloat() > 0.18F) return;
        Animal animal = fighter.level().getEntitiesOfClass(Animal.class, fighter.getBoundingBox().inflate(8.0D))
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (animal == null) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(animal.getType());
        String type = id == null ? "animal" : id.getPath();
        String line = switch (type) {
            case "pig" -> "You know, that pig looks completely unbothered by all this Ki.";
            case "cow" -> "At least the cows around here know how to stay out of a fight.";
            case "chicken" -> "That chicken has better survival instincts than some fighters I've met.";
            case "sheep" -> "Quiet place. The sheep seem to agree.";
            case "wolf" -> "Good instincts. That wolf's watching everything.";
            default -> "Funny how ordinary life keeps going around all this fighting.";
        };
        fighter.getLookControl().setLookAt(animal, 30.0F, 30.0F);
        fighter.speak(line, 74);
    }
}
