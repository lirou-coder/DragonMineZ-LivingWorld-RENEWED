package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.combat.LivingWorldSagasEntity;
import com.dragonminez.common.combat.clash.BeamClashManager;
import com.dragonminez.common.combat.clash.StrikeClash;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.server.events.players.combat.StrikeAttackHandler;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Adapter that lets Living World fighters participate in DMZ's native StrikeClash. */
public final class StrikeClashCompat {
    private static final double TRIGGER_GAP = 4.5D;
    private static final Set<UUID> RUSHING = new HashSet<>();
    private static final Method START;

    static {
        Method method = null;
        try {
            method = StrikeClash.class.getDeclaredMethod("start", ServerLevel.class, LivingEntity.class, LivingEntity.class);
            method.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        START = method;
    }

    private StrikeClashCompat() {}

    public static void begin(LivingWorldSagasEntity fighter) {
        if (fighter.level().isClientSide) return;
        RUSHING.add(fighter.getUUID());
        tryStart(fighter);
    }

    public static void end(LivingWorldSagasEntity fighter) {
        RUSHING.remove(fighter.getUUID());
    }

    public static boolean isRushing(LivingWorldSagasEntity fighter) {
        return RUSHING.contains(fighter.getUUID()) && fighter.isAlive();
    }

    /** Compatibility entry point used by Overhaul's late-input grace mixin. */
    public static boolean isStrikeRushing(LivingWorldSagasEntity fighter) {
        return isRushing(fighter);
    }

    public static boolean isClashing(LivingWorldSagasEntity fighter) {
        return BeamClashManager.isClashing(fighter.getUUID());
    }

    /** Called from StrikeClash's tryStart hook for player -> Living World clashes. */
    public static boolean tryStart(LivingEntity self) {
        if (!(self.level() instanceof ServerLevel level) || !self.isAlive()) return false;
        if (self instanceof net.minecraft.server.level.ServerPlayer player
                && !StrikeAttackHandler.isRushing(player)) return false;
        // Living World and Overhaul may call this method during the configured
        // late-input grace window after the combo flag has already been cleared.
        // The caller is authoritative; the opponent predicate below still
        // requires an active rush/grace state.
        if (self instanceof DBSagasEntity saga && (!saga.isStrikeRushing() || saga.isStrikeClashing())) return false;
        AABB area = self.getBoundingBox().inflate(TRIGGER_GAP + 2.0D);
        LivingEntity opponent = findBestOpponent(self, level, area);
        if (opponent == null || START == null) return false;
        try {
            START.invoke(null, level, self, opponent);
            markNpcAnimation(self);
            markNpcAnimation(opponent);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void markNpcAnimation(LivingEntity entity) {
        if (entity instanceof LivingWorldSagasEntity fighter) {
            fighter.markStrikeClashAnimation();
            NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(entity.getUUID(),
                    TriggerAnimationS2C.AnimationType.KI_ANIMATION, 0, -1, "skp.strike_clash"), entity);
        }
    }

    private static LivingEntity findBestOpponent(LivingEntity self, ServerLevel level, AABB area) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != self && isStrikeRushing(e) && !BeamClashManager.isClashing(e.getUUID())
                        && !BeamClashManager.isClashing(self.getUUID()))) {
            if (self.isAlliedTo(candidate) || candidate.isAlliedTo(self)) continue;
            double distance = self.getBoundingBox().getCenter().distanceTo(candidate.getBoundingBox().getCenter());
            if (distance <= TRIGGER_GAP + self.getBbWidth() + candidate.getBbWidth() && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isStrikeRushing(LivingEntity entity) {
        if (entity instanceof LivingWorldSagasEntity fighter) return isRushing(fighter);
        return entity instanceof DBSagasEntity saga && saga.isStrikeRushing() && !saga.isStrikeClashing();
    }
}
