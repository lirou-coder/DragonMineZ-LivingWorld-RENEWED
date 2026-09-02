package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Friendly-Fist mercy for genuinely hostile-on-sight Living World factions.
 *
 * The existing FriendlyFistCompat remains the sole damage-floor authority. This manager only
 * interprets reaching that floor as a social concession, keeps the spared fighter out of the
 * current local encounter, and revokes that mercy if the player kills one of their allies.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MercyManager {
    private static final String PENDING_PLAYER = "LWMercyPendingPlayer";
    private static final String PENDING_AT = "LWMercyPendingAt";
    private static final String SPARED_BY = "LWMercySparedBy";
    private static final String SPARED_FACTION = "LWMercySparedFaction";
    private static final String PEACE_UNTIL = "LWMercyPeaceUntil";
    private static final String BETRAY_TARGET = "LWMercyBetrayTarget";
    private static final String BETRAY_UNTIL = "LWMercyBetrayUntil";

    private static final String PLAYER_ROOT = "DMZLivingWorldMercy";
    private static final String ACTIVE_UNTIL = "ActiveUntil";
    private static final String BROKEN_UNTIL = "BrokenUntil";

    private static final long LOCAL_PEACE_GRACE = 240L;   // 12s after leaving the local scene.
    private static final long BETRAYAL_MEMORY = 1200L;    // one minute is plenty to wake/re-engage.
    private static final double LOCAL_ENCOUNTER_RADIUS = 96.0D;
    private static final double BETRAYAL_WAKE_RADIUS = 192.0D;

    private MercyManager() {}

    /** Narrow eligibility: Friendly Fist + a faction whose standing actually makes this NPC attack on sight. */
    public static boolean isHostileFactionMercyFight(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || fighter.level().isClientSide || !fighter.isAlive()) return false;
        if (!FriendlyFistCompat.friendlyFistEnabled(player) || !fighter.isFactionMember()) return false;
        if (fighter.isSanctionedMatchParticipant() || fighter.isCaptive() || fighter.isNonCombatant()) return false;
        if (WorldMenaceManager.isWorldMenace(fighter) || !(fighter.level() instanceof ServerLevel level)) return false;
        if (FactionRequestManager.isCaptureTarget(player, fighter)) return true;
        WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
        if (faction == null || FactionManager.getReputation(player, faction) > FactionManager.HOSTILE_REP) return false;
        if (isMercyPeaceActive(fighter, player)) return false;
        return fighter.getTarget() == player || FactionManager.shouldAttackPlayer(fighter, player);
    }

    public static boolean shouldQueueMercyDown(ServerPlayer player, AmbientFighterEntity fighter, float finalDamage) {
        if (!isHostileFactionMercyFight(player, fighter) || fighter.isRecovering()) return false;
        return fighter.getHealth() <= 1.001F || fighter.getHealth() - Math.max(0.0F, finalDamage) <= 1.001F;
    }

    public static void queueMercyDown(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return;
        CompoundTag data = fighter.getPersistentData();
        data.putUUID(PENDING_PLAYER, player.getUUID());
        data.putLong(PENDING_AT, fighter.level().getGameTime());
    }

    /** Called from the normal fighter AI tick, after the damage event has had time to settle health to the FF floor. */
    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        CompoundTag data = fighter.getPersistentData();
        long now = level.getGameTime();

        if (data.hasUUID(PENDING_PLAYER)) {
            UUID id = data.getUUID(PENDING_PLAYER);
            long queuedAt = data.getLong(PENDING_AT);
            data.remove(PENDING_PLAYER);
            data.remove(PENDING_AT);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && player.level() == level && now - queuedAt <= 20L
                    && fighter.getHealth() <= 1.05F && isHostileFactionMercyFight(player, fighter)) {
                fighter.enterMercyDowned(player);
                return;
            }
        }

        if (data.hasUUID(SPARED_BY)) {
            UUID playerId = data.getUUID(SPARED_BY);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            String factionId = data.getString(SPARED_FACTION);

            if (player != null && isPlayerMercyBroken(player, factionId, now)) {
                revokeForBetrayal(fighter, player, now, false);
            } else if (player != null && player.isAlive() && player.level() == level
                    && player.distanceToSqr(fighter) <= LOCAL_ENCOUNTER_RADIUS * LOCAL_ENCOUNTER_RADIUS) {
                // Staying near the confrontation keeps this exact encounter peaceful. Walking away
                // lets the grace expire, so meeting the hostile faction later is a new encounter.
                data.putLong(PEACE_UNTIL, now + LOCAL_PEACE_GRACE);
                keepPlayerEncounterActive(player, factionId, now + LOCAL_PEACE_GRACE);
                if (fighter.getTarget() == player) fighter.setTarget(null);
            } else if (data.getLong(PEACE_UNTIL) < now) {
                clearPeace(data);
            }
        }

        if (data.hasUUID(BETRAY_TARGET)) {
            if (data.getLong(BETRAY_UNTIL) < now) {
                data.remove(BETRAY_TARGET);
                data.remove(BETRAY_UNTIL);
            } else {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.getUUID(BETRAY_TARGET));
                if (player != null && player.isAlive() && player.level() == level
                        && !fighter.isDefeated() && !fighter.isRecovering() && fighter.canAttack(player)) {
                    FighterAmbientActivityManager.cancel(fighter);
                    fighter.setAggressive(true);
                    fighter.setTarget(player);
                    PeacekeeperManager.markNpcAggressor(player, fighter);
                    data.remove(BETRAY_TARGET);
                    data.remove(BETRAY_UNTIL);
                }
            }
        }
    }

    public static void onMercyDowned(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !(fighter.level() instanceof ServerLevel level) || !fighter.isFactionMember()) return;
        WorldFaction faction = FactionManager.byId(level, fighter.getFactionId());
        if (faction == null) return;
        if (FactionRequestManager.isCaptureTarget(player, fighter)) {
            fighter.setTarget(null); fighter.setAggressive(false);
            FactionRequestManager.onMercyDowned(player, fighter);
            return;
        }
        if (FactionManager.getReputation(player, faction) > FactionManager.HOSTILE_REP) return;

        long now = level.getGameTime();
        CompoundTag data = fighter.getPersistentData();
        data.putUUID(SPARED_BY, player.getUUID());
        data.putString(SPARED_FACTION, faction.id());
        data.putLong(PEACE_UNTIL, now + LOCAL_PEACE_GRACE);
        data.remove(BETRAY_TARGET);
        data.remove(BETRAY_UNTIL);
        fighter.setTarget(null);
        fighter.setAggressive(false);
        keepPlayerEncounterActive(player, faction.id(), now + LOCAL_PEACE_GRACE);

        // Mercy is meaningful personal history, but deliberately a small nudge rather than an
        // instant conversion. Faction leaders keep their established world-persistent memory rules.
        FighterMemoryManager.strengthenRelationship(player, fighter, 4,
                com.dmzlivingworld.world.FighterRelationshipManager.BondEvent.GENERIC,
                "Was spared after a hostile fight");
        ReactiveWorldManager.rememberEvent(fighter, "SHOWN_MERCY", player.getGameProfile().getName(),
                "was spared with Friendly Fist after yielding");
        FactionRequestManager.onMercyDowned(player, fighter);
        player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("You spare " + fighter.getFighterName()
                        + ". Their trust toward you improves slightly.").withStyle(ChatFormatting.GREEN)), false);
    }

    /** Mercy peace is pair-specific and also invalidated immediately by a broken faction encounter. */
    public static boolean isMercyPeaceActive(AmbientFighterEntity fighter, ServerPlayer player) {
        if (fighter == null || player == null || !(fighter.level() instanceof ServerLevel level)) return false;
        CompoundTag data = fighter.getPersistentData();
        if (!data.hasUUID(SPARED_BY) || !player.getUUID().equals(data.getUUID(SPARED_BY))) return false;
        if (data.getLong(PEACE_UNTIL) < level.getGameTime()) return false;
        String factionId = data.getString(SPARED_FACTION);
        return !isPlayerMercyBroken(player, factionId, level.getGameTime());
    }

    /** Prevents hitting a spared peaceful fighter repeatedly while Friendly Fist remains enabled. */
    public static boolean shouldIgnoreFriendlyFistHit(ServerPlayer player, AmbientFighterEntity fighter) {
        return player != null && fighter != null && FriendlyFistCompat.friendlyFistEnabled(player)
                && isMercyPeaceActive(fighter, player);
    }

    /** Per-hit faction hostility penalties would make merciful self-defense worse than killing; skip only this narrow case. */
    public static boolean shouldSuppressFactionHitPenalty(ServerPlayer player, AmbientFighterEntity fighter) {
        return isHostileFactionMercyFight(player, fighter);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFactionMemberKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity victim) || !victim.isFactionMember()
                || !(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer player = resolvePlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player == null) return;
        WorldFaction faction = FactionManager.byId(level, victim.getFactionId());
        if (faction == null) return;
        long now = level.getGameTime();
        if (!isPlayerMercyEncounterActive(player, faction.id(), now)) return;

        markPlayerMercyBroken(player, faction.id(), now + BETRAYAL_MEMORY);
        boolean spoke = false;
        for (AmbientFighterEntity other : level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(BETRAYAL_WAKE_RADIUS), f -> f != victim && f.isAlive()
                        && f.isFactionMember() && faction.id().equals(f.getFactionId()))) {
            CompoundTag data = other.getPersistentData();
            if (!data.hasUUID(SPARED_BY) || !player.getUUID().equals(data.getUUID(SPARED_BY))) continue;
            revokeForBetrayal(other, player, now, !spoke);
            spoke = true;
        }

        player.displayClientMessage(Component.literal("[Living World] Mercy broken • ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal("you killed a member of " + faction.name()
                        + "; spared allies will fight you again.").withStyle(ChatFormatting.RED)), false);
    }

    private static void revokeForBetrayal(AmbientFighterEntity fighter, ServerPlayer player, long now, boolean speak) {
        CompoundTag data = fighter.getPersistentData();
        clearPeace(data);
        data.putUUID(BETRAY_TARGET, player.getUUID());
        data.putLong(BETRAY_UNTIL, now + BETRAYAL_MEMORY);
        FighterMemoryManager.strengthenRelationship(player, fighter, -8,
                com.dmzlivingworld.world.FighterRelationshipManager.BondEvent.GENERIC,
                "Saw mercy broken by an ally's death");
        ReactiveWorldManager.rememberEvent(fighter, "MERCY_BROKEN", player.getGameProfile().getName(),
                "saw the player kill an ally after offering mercy");
        if (speak && fighter.getSpeech().isEmpty()) {
            fighter.speak("You spared us, then killed one of ours? We're not done.", 92);
        }
        if (!fighter.isDefeated() && !fighter.isRecovering() && fighter.canAttack(player)) {
            FighterAmbientActivityManager.cancel(fighter);
            fighter.setAggressive(true);
            fighter.setTarget(player);
            PeacekeeperManager.markNpcAggressor(player, fighter);
            data.remove(BETRAY_TARGET);
            data.remove(BETRAY_UNTIL);
        }
    }

    private static void clearPeace(CompoundTag data) {
        data.remove(SPARED_BY);
        data.remove(SPARED_FACTION);
        data.remove(PEACE_UNTIL);
    }

    private static CompoundTag playerRoot(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag root = persistent.contains(PLAYER_ROOT, Tag.TAG_COMPOUND)
                ? persistent.getCompound(PLAYER_ROOT) : new CompoundTag();
        persistent.put(PLAYER_ROOT, root);
        return root;
    }

    private static CompoundTag playerFactionState(ServerPlayer player, String factionId) {
        CompoundTag root = playerRoot(player);
        CompoundTag state = root.contains(factionId, Tag.TAG_COMPOUND) ? root.getCompound(factionId) : new CompoundTag();
        root.put(factionId, state);
        player.getPersistentData().put(PLAYER_ROOT, root);
        return state;
    }

    private static void keepPlayerEncounterActive(ServerPlayer player, String factionId, long until) {
        if (player == null || factionId == null || factionId.isBlank()) return;
        CompoundTag root = playerRoot(player);
        CompoundTag state = root.contains(factionId, Tag.TAG_COMPOUND) ? root.getCompound(factionId) : new CompoundTag();
        state.putLong(ACTIVE_UNTIL, Math.max(state.getLong(ACTIVE_UNTIL), until));
        root.put(factionId, state);
        player.getPersistentData().put(PLAYER_ROOT, root);
    }

    private static boolean isPlayerMercyEncounterActive(ServerPlayer player, String factionId, long now) {
        if (player == null || factionId == null || factionId.isBlank()) return false;
        CompoundTag root = playerRoot(player);
        if (!root.contains(factionId, Tag.TAG_COMPOUND)) return false;
        return root.getCompound(factionId).getLong(ACTIVE_UNTIL) >= now;
    }

    private static boolean isPlayerMercyBroken(ServerPlayer player, String factionId, long now) {
        if (player == null || factionId == null || factionId.isBlank()) return false;
        CompoundTag root = playerRoot(player);
        if (!root.contains(factionId, Tag.TAG_COMPOUND)) return false;
        return root.getCompound(factionId).getLong(BROKEN_UNTIL) >= now;
    }

    private static void markPlayerMercyBroken(ServerPlayer player, String factionId, long until) {
        CompoundTag root = playerRoot(player);
        CompoundTag state = root.contains(factionId, Tag.TAG_COMPOUND) ? root.getCompound(factionId) : new CompoundTag();
        state.putLong(BROKEN_UNTIL, until);
        state.putLong(ACTIVE_UNTIL, Math.max(state.getLong(ACTIVE_UNTIL), until));
        root.put(factionId, state);
        player.getPersistentData().put(PLAYER_ROOT, root);
    }

    private static ServerPlayer resolvePlayer(Entity causing, Entity direct) {
        if (causing instanceof ServerPlayer player) return player;
        if (direct instanceof AbstractKiProjectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }
}
