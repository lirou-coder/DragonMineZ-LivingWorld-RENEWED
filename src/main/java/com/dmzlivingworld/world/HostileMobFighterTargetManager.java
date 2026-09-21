package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

/** Lets structurally hostile mobs recognize neutral-typed Living World fighters as targets. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HostileMobFighterTargetManager {
    private static final int TARGET_SCAN_INTERVAL = 20;
    private static final double MIN_SCAN_RANGE = 16.0D;
    private static final double MAX_SCAN_RANGE = 64.0D;

    private HostileMobFighterTargetManager() {}

    /** World Menaces are hostile entities, but other hostile mobs must never choose them as victims. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectWorldMenacesFromHostileTargets(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || (!(mob instanceof Enemy) && !(mob instanceof Monster))
                || !(event.getNewTarget() instanceof AmbientFighterEntity fighter)) {
            return;
        }

        // Creepers never target fighters.
        if (mob instanceof Creeper) {
            event.setCanceled(true);
            return;
        }

        // Endermen do not proactively target fighters, but may retaliate
        // against a fighter that attacked them.
        if (mob instanceof EnderMan && !isRetaliatingAgainst(mob, fighter)) {
            event.setCanceled(true);
            return;
        }

        // World Menaces are never victims of ordinary hostile mobs.
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void acquireFighterTarget(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || mob.level().isClientSide
                || (!(mob instanceof Enemy) && !(mob instanceof Monster))
                || !mob.isAlive()) {
            return;
        }

        // Repair invalid fighter targets assigned/restored outside the normal event.
        if (mob.getTarget() instanceof AmbientFighterEntity fighter) {
            boolean invalidTarget =
                    mob instanceof Creeper
                    || WorldMenaceManager.isWorldMenace(fighter)
                    || (mob instanceof EnderMan && !isRetaliatingAgainst(mob, fighter));

            if (invalidTarget) {
                mob.setTarget(null);
            }
        }

        if (mob.getTarget() != null) {
            return;
        }

        if (Math.floorMod(mob.tickCount + mob.getId(), TARGET_SCAN_INTERVAL) != 0) {
            return;
        }

        double range = Math.max(
                MIN_SCAN_RANGE,
                Math.min(MAX_SCAN_RANGE, mob.getAttributeValue(Attributes.FOLLOW_RANGE))
        );

        mob.level().getEntitiesOfClass(
                        AmbientFighterEntity.class,
                        mob.getBoundingBox().inflate(range),
                        fighter -> mayTarget(mob, fighter))
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .ifPresent(mob::setTarget);
    }

    private static boolean mayTarget(Mob mob, AmbientFighterEntity fighter) {
        // Never proactively acquire fighters for Creepers or Endermen.
        if (mob instanceof Creeper || mob instanceof EnderMan
                || !fighter.isAlive()
                || fighter.isDeadSoul()
                || fighter.isDefeated()
                || fighter.isCaptive()
                || fighter.isNonCombatant()
                || WorldMenaceManager.isWorldMenace(fighter)) {
            return false;
        }

        return !mob.isAlliedTo(fighter)
                && mob.canAttack((LivingEntity) fighter);
    }

    private static boolean isRetaliatingAgainst(Mob mob, AmbientFighterEntity fighter) {
        return mob.getLastHurtByMob() == fighter;
    }
}