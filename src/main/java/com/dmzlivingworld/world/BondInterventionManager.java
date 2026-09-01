package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.UUID;

/** Nearby bonds can have a concrete consequence: a real ally may choose to step into a dangerous fight. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BondInterventionManager {
    private static final double SEARCH = 48.0D;
    private static final long HELPER_COOLDOWN = 2_400L;
    private static final long VICTIM_COOLDOWN = 1_200L;

    private BondInterventionManager() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) return;
        LivingEntity victim = event.getEntity();
        if (victim == attacker) return;
        if (victim.getHealth() - event.getAmount() > victim.getMaxHealth() * 0.32F) return;

        // Organized one-on-one combat stays one-on-one.
        if (victim instanceof AmbientFighterEntity fighterVictim) {
            if (fighterVictim.isSanctionedMatchParticipant() || fighterVictim.isDuelOpponent(attacker)) return;
        }
        if (attacker instanceof AmbientFighterEntity fighterAttacker && fighterAttacker.isSanctionedMatchParticipant()) return;

        if (victim instanceof ServerPlayer player) interveneForPlayer(player, attacker);
        else if (victim instanceof AmbientFighterEntity fighter) interveneForFighter(fighter, attacker);
    }

    private static void interveneForPlayer(ServerPlayer player, LivingEntity attacker) {
        if (!(player.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator()) return;
        long now = level.getServer().overworld().getGameTime();
        if (now < player.getPersistentData().getLong("LWNextBondRescue")) return;

        UUID companion = LivingBondManager.companionId(player);
        AmbientFighterEntity helper = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        player.getBoundingBox().inflate(SEARCH), f -> eligibleHelper(f, attacker, now) && playerBondScore(player, f, companion) >= 40)
                .stream().max(Comparator.<AmbientFighterEntity>comparingInt(f -> playerBondScore(player, f, companion))
                        .thenComparingDouble(f -> -f.distanceToSqr(player))).orElse(null);
        if (helper == null || !engage(helper, attacker)) return;

        helper.getPersistentData().putLong("LWNextBondIntervention", now + HELPER_COOLDOWN);
        player.getPersistentData().putLong("LWNextBondRescue", now + VICTIM_COOLDOWN);
        noteIntervention(helper, null, player.getGameProfile().getName());
        if (helper.isRememberedFor(player)) FighterMemoryManager.strengthenRelationship(player, helper, 1, FighterRelationshipManager.BondEvent.PROTECTION, "Intervened to protect you");
        helper.speak("Back off.", 44);
        player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(helper.getFighterName() + " intervened to protect you.").withStyle(ChatFormatting.GREEN)), false);
    }

    private static void interveneForFighter(AmbientFighterEntity victim, LivingEntity attacker) {
        if (!(victim.level() instanceof ServerLevel level) || victim.isDefeated() || victim.isRecovering()) return;
        long now = level.getServer().overworld().getGameTime();
        if (now < victim.getPersistentData().getLong("LWNextBondRescue")) return;

        AmbientFighterEntity helper = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        victim.getBoundingBox().inflate(SEARCH), f -> f != victim && eligibleHelper(f, attacker, now) && npcBondScore(f, victim) > 0)
                .stream().max(Comparator.<AmbientFighterEntity>comparingInt(f -> npcBondScore(f, victim))
                        .thenComparingDouble(f -> -f.distanceToSqr(victim))).orElse(null);
        if (helper == null || !engage(helper, attacker)) return;

        helper.getPersistentData().putLong("LWNextBondIntervention", now + HELPER_COOLDOWN);
        victim.getPersistentData().putLong("LWNextBondRescue", now + VICTIM_COOLDOWN);
        noteIntervention(helper, victim, victim.getFighterName());
        helper.speak("I've got you.", 44);
    }

    private static boolean eligibleHelper(AmbientFighterEntity helper, LivingEntity attacker, long now) {
        return helper != null && helper != attacker && helper.isAlive() && !helper.isDefeated() && !helper.isRecovering()
                && !helper.isCaptive() && !helper.isNonCombatant() && !helper.isMeditating()
                && !helper.isSanctionedMatchParticipant() && helper.getTarget() == null
                && now >= helper.getPersistentData().getLong("LWNextBondIntervention");
    }

    private static int playerBondScore(ServerPlayer player, AmbientFighterEntity helper, UUID companion) {
        int score = 0;
        if (companion != null && companion.equals(helper.getUUID())) score = 100;
        if (helper.isRememberedFor(player)) score = Math.max(score, helper.getMemoryRelationship());
        if (helper.isFactionMember() && FactionManager.getReputation(player, helper.getFactionId()) >= FactionManager.FRIENDLY_REP)
            score = Math.max(score, 52);
        return score;
    }

    private static int npcBondScore(AmbientFighterEntity helper, AmbientFighterEntity victim) {
        if (helper.isFactionMember() && victim.isFactionMember()) {
            if (helper.getFactionId().equals(victim.getFactionId())) return 90;
            if (FactionManager.areAllies(helper, victim)) return 70;
        }
        return 0;
    }

    private static boolean engage(AmbientFighterEntity helper, LivingEntity attacker) {
        if (attacker instanceof AmbientFighterEntity other) {
            if (helper.isFactionMember() && other.isFactionMember()
                    && (helper.getFactionId().equals(other.getFactionId()) || FactionManager.areAllies(helper, other))) return false;
            helper.startDuel(other);
            return true;
        }
        if (!helper.canAttack(attacker)) return false;
        helper.setTarget(attacker);
        return true;
    }

    private static void noteIntervention(AmbientFighterEntity helper, AmbientFighterEntity victim, String protectedName) {
        var legacy = helper.getLegacyData();
        int given = legacy.getInt("InterventionsGiven") + 1;
        legacy.putInt("InterventionsGiven", given);
        if (given == 1 || given == 5 || given == 10) helper.recordLegacyEvent("Intervened to protect " + protectedName);
        if (victim != null) {
            var v = victim.getLegacyData();
            v.putInt("InterventionsReceived", v.getInt("InterventionsReceived") + 1);
        }
    }
}
