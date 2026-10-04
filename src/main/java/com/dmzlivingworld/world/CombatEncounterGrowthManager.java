package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative reward lifecycle for non-spar Living World battles. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEncounterGrowthManager {
    private static final long DISENGAGE_TICKS = 20L * 20L;
    private static final Map<PlayerKey, PlayerFight> PLAYER_FIGHTS = new HashMap<>();
    private static final Map<NpcKey, NpcFight> NPC_FIGHTS = new HashMap<>();

    private CombatEncounterGrowthManager() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0.0F) return;
        LivingEntity attacker = livingAttacker(event.getSource().getEntity(), event.getSource().getDirectEntity());
        LivingEntity victim = event.getEntity();
        long now = victim.level().getGameTime();

        if (attacker instanceof ServerPlayer player && victim instanceof AmbientFighterEntity fighter) {
            notePlayerFight(player, fighter, now, false);
        } else if (attacker instanceof AmbientFighterEntity fighter && victim instanceof ServerPlayer player) {
            notePlayerFight(player, fighter, now, true);
        } else if (attacker instanceof AmbientFighterEntity first && victim instanceof AmbientFighterEntity second) {
            if (!eligible(first) || !eligible(second) || first == second) return;
            NpcKey key = NpcKey.of(first.getUUID(), second.getUUID());
            NPC_FIGHTS.compute(key, (ignored, fight) -> fight == null
                    ? new NpcFight(key.first, key.second, now) : fight.hit(now));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        UUID dead = event.getEntity().getUUID();
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) return;

        for (PlayerFight fight : PLAYER_FIGHTS.values().stream().filter(f -> f.involves(dead)).toList()) {
            boolean playerDied = fight.playerId.equals(dead);
            boolean fighterDied = fight.fighterId.equals(dead);
            finishPlayerFight(server, fight, !playerDied, !fighterDied);
        }
        for (NpcFight fight : NPC_FIGHTS.values().stream().filter(f -> f.involves(dead)).toList())
            finishNpcFight(server, fight, !fight.firstId.equals(dead), !fight.secondId.equals(dead));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.overworld().getGameTime();

        for (PlayerFight fight : PLAYER_FIGHTS.values().stream().toList()) {
            if (now - fight.lastFighterDamageAt >= DISENGAGE_TICKS)
                finishPlayerFight(server, fight, true, true);
        }
        for (NpcFight fight : NPC_FIGHTS.values().stream().toList()) {
            if (now - fight.lastDamageAt >= DISENGAGE_TICKS)
                finishNpcFight(server, fight, true, true);
        }
    }

    public static void onFighterSpared(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return;
        PlayerFight fight = PLAYER_FIGHTS.get(new PlayerKey(player.getUUID(), fighter.getUUID()));
        if (fight != null) finishPlayerFight(player.server, fight, true, true);
    }

    public static void onNpcConcession(AmbientFighterEntity victor, AmbientFighterEntity defeated) {
        if (victor == null || defeated == null || victor.getServer() == null) return;
        NpcFight fight = NPC_FIGHTS.get(NpcKey.of(victor.getUUID(), defeated.getUUID()));
        if (fight != null) finishNpcFight(victor.getServer(), fight, true, true);
    }

    public static void onNpcCombatContact(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (!eligible(attacker) || !eligible(victim) || attacker == victim) return;
        long now = victim.level().getGameTime();
        NpcKey key = NpcKey.of(attacker.getUUID(), victim.getUUID());
        NPC_FIGHTS.compute(key, (ignored, fight) -> fight == null
                ? new NpcFight(key.first, key.second, now) : fight.hit(now));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PLAYER_FIGHTS.clear();
        NPC_FIGHTS.clear();
    }

    private static void notePlayerFight(ServerPlayer player, AmbientFighterEntity fighter, long now,
                                        boolean fighterDamagedPlayer) {
        if (player == null || !eligible(fighter)) return;
        PlayerKey key = new PlayerKey(player.getUUID(), fighter.getUUID());
        PLAYER_FIGHTS.compute(key, (ignored, fight) -> {
            if (fight == null) return new PlayerFight(key.playerId, key.fighterId, now);
            return fighterDamagedPlayer ? fight.fighterHit(now) : fight;
        });
    }

    private static boolean eligible(AmbientFighterEntity fighter) {
        return fighter != null && !fighter.level().isClientSide && fighter.isAlive()
                && !fighter.isSanctionedMatchParticipant() && !fighter.isNonCombatant();
    }

    private static void finishPlayerFight(MinecraftServer server, PlayerFight fight,
                                          boolean rewardPlayer, boolean rewardFighter) {
        if (PLAYER_FIGHTS.remove(new PlayerKey(fight.playerId, fight.fighterId)) == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(fight.playerId);
        AmbientFighterEntity fighter = findFighter(server, fight.fighterId);
        if (player == null || fighter == null) return;
        SparManager.grantMatchedBattleRewards(player, fighter, 0.5D,
                rewardPlayer && player.isAlive(), rewardFighter && fighter.isAlive());
    }

    private static void finishNpcFight(MinecraftServer server, NpcFight fight,
                                       boolean rewardFirst, boolean rewardSecond) {
        if (NPC_FIGHTS.remove(NpcKey.of(fight.firstId, fight.secondId)) == null) return;
        AmbientFighterEntity first = findFighter(server, fight.firstId);
        AmbientFighterEntity second = findFighter(server, fight.secondId);
        if (first == null || second == null) return;
        double factor = SparManager.matchedPowerFactor(first.getBattlePower(), second.getBattlePower());
        if (rewardFirst && first.isAlive()) SparManager.grantMatchedFighterGrowth(first, factor, 1.0D);
        if (rewardSecond && second.isAlive()) SparManager.grantMatchedFighterGrowth(second, factor, 1.0D);
    }

    private static AmbientFighterEntity findFighter(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof AmbientFighterEntity fighter) return fighter;
        }
        return null;
    }

    private static LivingEntity livingAttacker(Entity owner, Entity direct) {
        if (owner instanceof LivingEntity living) return living;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity living) return living;
        return direct instanceof LivingEntity living ? living : null;
    }

    private record PlayerKey(UUID playerId, UUID fighterId) {}
    private record PlayerFight(UUID playerId, UUID fighterId, long lastFighterDamageAt) {
        PlayerFight fighterHit(long now) { return new PlayerFight(playerId, fighterId, now); }
        boolean involves(UUID id) { return playerId.equals(id) || fighterId.equals(id); }
    }
    private record NpcKey(UUID first, UUID second) {
        static NpcKey of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new NpcKey(a, b) : new NpcKey(b, a);
        }
    }
    private record NpcFight(UUID firstId, UUID secondId, long lastDamageAt) {
        NpcFight hit(long now) { return new NpcFight(firstId, secondId, now); }
        boolean involves(UUID id) { return firstId.equals(id) || secondId.equals(id); }
    }
}
