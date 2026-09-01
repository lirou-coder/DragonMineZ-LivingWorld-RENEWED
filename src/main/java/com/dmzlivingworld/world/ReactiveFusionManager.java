package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlwfusion.NpcFusionManager;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;

/** Rare autonomous faction fusion using the existing full NPC Fusion Dance implementation. */
public final class ReactiveFusionManager {
    private static final String NEXT_TRY = "LWReactiveFusionNextTry";
    private static final String LAST_TARGET = "LWReactiveFusionLastTarget";

    private ReactiveFusionManager() {}

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        if (WorldMenaceManager.isWorldMenace(fighter) || fighter.isDefeated() || fighter.isCaptive()) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        long now = level.getGameTime();
        if (now < fighter.getPersistentData().getLong(NEXT_TRY)) return;

        if (fighter.getTarget() != null) {
            if (!fighter.isFactionMember() || fighter.tickCount % 80 != Math.floorMod(fighter.getUUID().hashCode(), 80)) return;
            AmbientFighterEntity partner = level.getEntitiesOfClass(AmbientFighterEntity.class,
                            fighter.getBoundingBox().inflate(5.0D), other -> other != fighter && other.isAlive()
                                    && other.isFactionMember() && fighter.getFactionId().equals(other.getFactionId())
                                    && !WorldMenaceManager.isWorldMenace(other) && !other.isDefeated() && !other.isCaptive()
                                    && !other.isMeditating() && !other.isTransforming() && !other.isKaiokenActive()
                                    && other.getRace() == fighter.getRace() && other.getTarget() == fighter.getTarget())
                    .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
            if (partner == null) { fighter.getPersistentData().putLong(NEXT_TRY, now + 400L); return; }
            if (fighter.getUUID().compareTo(partner.getUUID()) > 0) return;

            String target = fighter.getTarget().getUUID().toString();
            boolean battleOpening = !target.equals(fighter.getPersistentData().getString(LAST_TARGET));
            fighter.getPersistentData().putString(LAST_TARGET, target);
            partner.getPersistentData().putString(LAST_TARGET, target);
            boolean desperate = fighter.getHealth() <= fighter.getMaxHealth() * 0.35F
                    || partner.getHealth() <= partner.getMaxHealth() * 0.35F;
            // R15: autonomous fusion should be a memorable sight, not a routine combat answer.
            float chance = desperate ? 0.04F : battleOpening ? 0.009F : 0.0015F;
            if (FighterNpcSocialManager.bond(fighter, partner) >= 6) chance = Math.min(0.06F, chance + 0.008F);
            if (fighter.getRandom().nextFloat() >= chance) {
                long retry = now + (desperate ? 900L : 2400L);
                fighter.getPersistentData().putLong(NEXT_TRY, retry); partner.getPersistentData().putLong(NEXT_TRY, retry);
                return;
            }
            boolean started = NpcFusionManager.startAutonomousFusion(fighter, partner);
            long next = now + (started ? 48_000L + fighter.getRandom().nextInt(72_001) : 2400L);
            fighter.getPersistentData().putLong(NEXT_TRY, next); partner.getPersistentData().putLong(NEXT_TRY, next);
            if (started) {
                fighter.recordLegacyEvent("Attempted fusion with " + partner.getFighterName() + " in battle");
                partner.recordLegacyEvent("Attempted fusion with " + fighter.getFighterName() + " in battle");
            }
            return;
        }

        // Very rare peaceful fusion opportunity. It only exists where the normal NPC Fusion
        // manager says a nearby same-race pair is eligible, and a player must actually be nearby
        // to observe the full dance. A bond or explicit fusion goal is required.
        if (fighter.tickCount % 400 != Math.floorMod(fighter.getUUID().hashCode(), 400)) return;
        if (fighter.isMeditating() || fighter.isTransforming() || fighter.isKaiokenActive()
                || fighter.isSocialLifeActivity() || fighter.isSanctionedMatchParticipant()) return;
        AmbientFighterEntity partner = level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(4.5D), other ->
                        other != fighter && other.isAlive() && !WorldMenaceManager.isWorldMenace(other) && !other.isDefeated() && !other.isCaptive()
                                && other.getTarget() == null && !other.isMeditating() && !other.isTransforming() && !other.isKaiokenActive()
                                && !other.isSocialLifeActivity() && !other.isSanctionedMatchParticipant() && other.getRace() == fighter.getRace())
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (partner == null || fighter.getUUID().compareTo(partner.getUUID()) > 0) return;
        int bond = FighterNpcSocialManager.bond(fighter, partner);
        boolean fusionGoal = "FUSION".equals(FighterGoalManager.currentType(fighter)) || "FUSION".equals(FighterGoalManager.currentType(partner));
        if (bond < 7 && !fusionGoal) { fighter.getPersistentData().putLong(NEXT_TRY, now + 6000L); return; }
        float peacefulChance = fusionGoal ? 0.006F : 0.002F;
        if (fighter.getRandom().nextFloat() >= peacefulChance) {
            long retry = now + 6000L + fighter.getRandom().nextInt(6001);
            fighter.getPersistentData().putLong(NEXT_TRY, retry); partner.getPersistentData().putLong(NEXT_TRY, retry);
            return;
        }
        boolean started = NpcFusionManager.startAutonomousFusion(fighter, partner);
        long next = now + (started ? 72_000L + fighter.getRandom().nextInt(72_001) : 6000L);
        fighter.getPersistentData().putLong(NEXT_TRY, next); partner.getPersistentData().putLong(NEXT_TRY, next);
        if (started) {
            fighter.recordLegacyEvent("Rarely fused with " + partner.getFighterName());
            partner.recordLegacyEvent("Rarely fused with " + fighter.getFighterName());
        }
    }

}
