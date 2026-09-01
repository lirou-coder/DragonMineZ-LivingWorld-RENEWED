package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;

/**
 * Friend-requested cinematic full-power display.
 *
 * This never invents a transformation or temporary BP multiplier. A fighter with a learned
 * racial form uses AmbientFighterEntity's existing real transformation path; a fighter without
 * one simply demonstrates their real base power/aura. The voluntary display lasts 20 seconds.
 */
public final class FighterFullPowerManager {
    private static final String START = "LWFullPowerStart";
    private static final String UNTIL = "LWFullPowerUntil";
    private static final String FORM_REQUESTED = "LWFullPowerFormRequested";
    private static final long DURATION = 400L;
    private FighterFullPowerManager() {}

    public static boolean isActive(AmbientFighterEntity fighter) {
        return fighter != null && fighter.getPersistentData().getLong(UNTIL) > fighter.level().getGameTime();
    }

    public static boolean request(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || fighter.level().isClientSide || WorldMenaceManager.isWorldMenace(fighter)) return false;
        int relationship = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        if (relationship < 35) {
            player.displayClientMessage(Component.literal("[Living World] You need to be friends before asking for that."), false);
            return false;
        }
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isRecovering() || fighter.isSanctionedMatchParticipant()
                || fighter.getTarget() != null || fighter.isKaiokenActive() || fighter.isAwakening() || fighter.isRacialFormActive()) {
            player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " can't demonstrate full power right now."), false);
            return false;
        }

        FighterAmbientActivityManager.cancel(fighter);
        FighterNpcSocialManager.cancelFor(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) fighter.stopMeditation(false);
        fighter.setSocialLifeActivity(false);
        fighter.getNavigation().stop();
        fighter.setTarget(null);
        fighter.setAggressive(false);
        fighter.setFlying(false);
        fighter.setSprinting(false);
        fighter.setPose(Pose.STANDING);
        fighter.setAmbientPose(0);
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);

        long now = fighter.level().getGameTime();
        fighter.getPersistentData().putLong(START, now);
        fighter.getPersistentData().putLong(UNTIL, now + DURATION);
        fighter.getPersistentData().putBoolean(FORM_REQUESTED, false);
        fighter.setKiCharge(true);
        fighter.flareAura((int) DURATION);
        player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName() + " begins bringing out their full power."), false);
        return true;
    }

    /** Returns true while this voluntary display owns ordinary AI for the tick. */
    public static boolean tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return false;
        var data = fighter.getPersistentData();
        long until = data.getLong(UNTIL);
        if (until <= 0L) return false;
        long now = fighter.level().getGameTime();

        // A genuine fight outranks the showcase. Leave any real form active and hand ownership
        // straight back to normal combat, where its normal lifecycle/stat rules continue.
        if (fighter.getTarget() != null && fighter.getTarget().isAlive()) {
            clearMarkers(fighter);
            fighter.setKiCharge(false);
            return false;
        }

        if (now >= until) {
            if (fighter.isRacialFormActive()) fighter.stopRacialForm();
            fighter.setKiCharge(false);
            fighter.suppressActivityAura();
            fighter.setAmbientPose(0);
            fighter.setPose(Pose.STANDING);
            fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
            clearMarkers(fighter);
            return false;
        }

        long started = data.getLong(START);
        long elapsed = Math.max(0L, now - started);
        fighter.getNavigation().stop();
        fighter.setTarget(null);
        fighter.setAggressive(false);
        fighter.setFlying(false);
        fighter.setSprinting(false);
        fighter.setPose(Pose.STANDING);
        fighter.setAmbientPose(0);
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);

        // The first few seconds visibly charge. After that the form/aura itself carries the scene.
        if (elapsed < 70L && !fighter.isRacialFormActive()) fighter.setKiCharge(true);
        else fighter.setKiCharge(false);

        // Highest learned racial skill is exactly the form AmbientFighterEntity's established
        // combat transformation path resolves. No skill means no fabricated transformation.
        if (elapsed >= 45L && fighter.getRacialSkillLevel() > 0
                && !data.getBoolean(FORM_REQUESTED) && !fighter.isAwakening() && !fighter.isRacialFormActive()) {
            data.putBoolean(FORM_REQUESTED, true);
            fighter.beginAwakening();
        }
        return true;
    }

    private static void clearMarkers(AmbientFighterEntity fighter) {
        fighter.getPersistentData().remove(START);
        fighter.getPersistentData().remove(UNTIL);
        fighter.getPersistentData().remove(FORM_REQUESTED);
    }
}
