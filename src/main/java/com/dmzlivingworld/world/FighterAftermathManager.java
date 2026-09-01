package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;

/** Short physical aftermath for fights that have actually resolved. */
public final class FighterAftermathManager {
    private static final String ROOT = "LWAftermathV1";
    private static final String ROLE = "Role";
    private static final String START = "Start";
    private static final String UNTIL = "Until";
    private static final String OTHER = "Other";
    private static final String SPOKE = "Spoke";

    private FighterAftermathManager() {}

    private static CompoundTag data(AmbientFighterEntity fighter) {
        CompoundTag p = fighter.getPersistentData();
        if (!p.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) p.put(ROOT, new CompoundTag());
        return p.getCompound(ROOT);
    }

    public static void beginConcession(AmbientFighterEntity winner, AmbientFighterEntity loser) {
        if (winner == null || loser == null || !(winner.level() instanceof ServerLevel level)) return;
        if (!WorldMenaceManager.isWorldMenace(winner)) begin(winner, "WINNER", loser, 18L, 150L + winner.getRandom().nextInt(81));
        if (!WorldMenaceManager.isWorldMenace(loser)) begin(loser, "LOSER", winner, 170L, 180L + loser.getRandom().nextInt(81));
        inviteNearbyAllies(level, loser, winner);
    }

    public static void beginPractice(AmbientFighterEntity a, AmbientFighterEntity b, AmbientFighterEntity winner, boolean decisive) {
        if (a == null || b == null || !(a.level() instanceof ServerLevel level)) return;
        if (decisive && winner != null) {
            AmbientFighterEntity loser = winner == a ? b : a;
            begin(winner, "WINNER", loser, 15L, 120L + winner.getRandom().nextInt(61));
            begin(loser, "LOSER", winner, 15L, 150L + loser.getRandom().nextInt(71));
            inviteNearbyAllies(level, loser, winner);
        } else {
            begin(a, "DRAW", b, 10L, 100L + a.getRandom().nextInt(51));
            begin(b, "DRAW", a, 10L, 100L + b.getRandom().nextInt(51));
        }
    }

    public static void beginPlayerSpar(AmbientFighterEntity fighter, ServerPlayer player, boolean playerWon, boolean decisive) {
        if (fighter == null || player == null || !decisive || WorldMenaceManager.isWorldMenace(fighter)) return;
        begin(fighter, playerWon ? "LOSER" : "WINNER", player, 12L, 130L + fighter.getRandom().nextInt(71));
    }

    public static void beginLethalScene(AmbientFighterEntity fallen, Entity victor) {
        if (fallen == null || !(fallen.level() instanceof ServerLevel level)) return;
        if (victor instanceof AmbientFighterEntity fighterWinner && !WorldMenaceManager.isWorldMenace(fighterWinner))
            begin(fighterWinner, "WINNER", fallen, 10L, 130L + fighterWinner.getRandom().nextInt(61));
        inviteNearbyAllies(level, fallen, victor instanceof AmbientFighterEntity af ? af : null);
    }

    public static void beginLethalWitness(AmbientFighterEntity winner, AmbientFighterEntity fallen) {
        if (winner == null || fallen == null || !(winner.level() instanceof ServerLevel level)) return;
        if (!WorldMenaceManager.isWorldMenace(winner)) begin(winner, "WINNER", fallen, 10L, 130L + winner.getRandom().nextInt(61));
        inviteNearbyAllies(level, fallen, winner);
    }

    /** Returns true only while the short aftermath owns idle locomotion. */
    public static boolean tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !fighter.isAlive() || fighter.isDefeated()
                || fighter.isCaptive() || WorldMenaceManager.isWorldMenace(fighter)) return false;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) { clear(fighter); return false; }
        if (!fighter.getPersistentData().contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) return false;
        CompoundTag d = fighter.getPersistentData().getCompound(ROOT);
        long now = fighter.level().getGameTime();
        if (d.getLong(UNTIL) <= now) { clear(fighter); return false; }
        if (now < d.getLong(START)) return false;
        if (fighter.getTarget() != null || fighter.isSanctionedMatchParticipant() || fighter.isMeditating()
                || fighter.isPreparingMeditation() || LivingBondManager.isTravellingCompanion(fighter)
                || FighterAmbientActivityManager.isActive(fighter) || fighter.isSocialLifeActivity()
                || fighter.isSocialPlayerApproach() || fighter.isSocialPowerDisplay()) {
            clear(fighter); return false;
        }

        Entity other = resolveOther(fighter, d);
        fighter.setKiCharge(false);
        fighter.setAttacking(false);
        fighter.getNavigation().stop();
        if (other != null && !other.isRemoved()) fighter.getLookControl().setLookAt(other, 30.0F, 24.0F);

        String role = d.getString(ROLE);
        if ("ALLY".equals(role) && other instanceof AmbientFighterEntity hurt && hurt.isAlive()) {
            double d2 = fighter.distanceToSqr(hurt);
            if (d2 > 16.0D && d2 < 144.0D) fighter.getNavigation().moveTo(hurt, 0.72D);
        }
        if (!d.getBoolean(SPOKE) && now - d.getLong(START) > 35L && fighter.getSpeech().isEmpty()) {
            if ("ALLY".equals(role) && fighter.getRandom().nextFloat() < 0.40F) fighter.speak("You all right?", 42);
            else if ("WINNER".equals(role) && fighter.getRandom().nextFloat() < 0.22F) fighter.speak("...Good fight.", 40);
            else if ("LOSER".equals(role) && fighter.getRandom().nextFloat() < 0.22F) fighter.speak("I need to remember that.", 46);
            d.putBoolean(SPOKE, true);
            fighter.getPersistentData().put(ROOT, d);
        }
        return true;
    }

    public static boolean isActive(AmbientFighterEntity fighter) {
        if (fighter == null || !fighter.getPersistentData().contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) return false;
        CompoundTag d = fighter.getPersistentData().getCompound(ROOT);
        long now = fighter.level().getGameTime();
        return now >= d.getLong(START) && now < d.getLong(UNTIL);
    }

    private static void inviteNearbyAllies(ServerLevel level, AmbientFighterEntity loser, AmbientFighterEntity winner) {
        List<AmbientFighterEntity> allies = level.getEntitiesOfClass(AmbientFighterEntity.class,
                loser.getBoundingBox().inflate(16.0D), other -> other != loser && other != winner && other.isAlive()
                        && !other.isDefeated() && !other.isCaptive() && other.getTarget() == null
                        && !WorldMenaceManager.isWorldMenace(other)
                        && ((loser.isFactionMember() && other.isFactionMember() && loser.getFactionId().equals(other.getFactionId()))
                            || FighterNpcSocialManager.bond(other, loser) >= 5));
        int count = 0;
        for (AmbientFighterEntity ally : allies) {
            begin(ally, "ALLY", loser, 35L + ally.getRandom().nextInt(30), 130L + ally.getRandom().nextInt(81));
            if (++count >= 2) break;
        }
    }

    private static void begin(AmbientFighterEntity fighter, String role, Entity other, long delay, long duration) {
        long now = fighter.level().getGameTime();
        CompoundTag d = data(fighter);
        d.putString(ROLE, role);
        d.putLong(START, now + Math.max(0L, delay));
        d.putLong(UNTIL, now + Math.max(40L, delay + duration));
        if (other != null) d.putUUID(OTHER, other.getUUID()); else d.remove(OTHER);
        d.putBoolean(SPOKE, false);
        fighter.getPersistentData().put(ROOT, d);
    }

    private static Entity resolveOther(AmbientFighterEntity fighter, CompoundTag d) {
        if (!d.hasUUID(OTHER) || !(fighter.level() instanceof ServerLevel level)) return null;
        UUID id = d.getUUID(OTHER);
        return level.getEntity(id);
    }

    private static void clear(AmbientFighterEntity fighter) {
        fighter.getPersistentData().remove(ROOT);
    }
}
