package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Red Ribbon Prodigy / Experiment world menace.
 * Unlike Herobrine this menace deliberately behaves like an extremely capable ordinary LW fighter:
 * it trains, uses normal combat intelligence and can be watched living in the world. The menace
 * rules only remove social ownership/People/IT and give it a unique engineered growth curve.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RedRibbonExperimentManager {
    public static final String TAG = "LWWorldMenaceRedRibbonExperiment";
    private static final String PLAYER_SPOTTED = "LWRRExperimentSpotted";
    private static final String PLAYER_PROFILE = "LWRRExperimentProfile";
    private static final String PLAYER_SIGHTINGS = "LWRRExperimentSightings";
    private static final String REINFORCEMENTS_USED = "LWRRExperimentReinforcementsUsed";
    private static final String REINFORCEMENT_TARGET = "LWRRExperimentReinforcementTarget";
    private static final String NEXT_SNAPSHOT = "LWRRExperimentNextSnapshot";
    private static final String RETALIATE_PLAYER = "LWRRExperimentRetaliatePlayer";
    private static final String RETALIATE_UNTIL = "LWRRExperimentRetaliateUntil";
    private static final String NEXT_HUNT = "LWRRExperimentNextHunt";
    private static final String HUNT_REMAINING = "LWRRExperimentHuntRemaining";
    private static final String HUNT_TARGET = "LWRRExperimentHuntTarget";
    private static final String HUNT_NEXT_CHAIN = "LWRRExperimentHuntNextChain";
    private static final String HUNT_SPARE = "LWRRExperimentHuntSpare";
    private static final String HUNT_FORCED = "LWRRExperimentHuntForced";
    private static final int FIRST_MIN = 72_000;      // first appearance after roughly 3+ days
    private static final int FIRST_JITTER = 72_001;
    private static final int RETURN_MIN = 12_000;     // roughly 10 minutes minimum; the same trained body returns
    private static final int RETURN_JITTER = 18_001;   // up to roughly 25 minutes
    private static final UUID DOSSIER_ID = UUID.nameUUIDFromBytes("dmzlivingworld:world_menace:red_ribbon_experiment".getBytes(StandardCharsets.UTF_8));
    private RedRibbonExperimentManager() {}

    public static boolean isExperiment(AmbientFighterEntity fighter) {
        return fighter != null && (fighter.getPersistentData().getBoolean(TAG)
                || "Red Ribbon Experiment X-7".equals(fighter.getFighterName()));
    }

    public static boolean isExperimentProfile(CompoundTag profile) {
        if (profile == null) return false;
        String name = profile.getString("Name");
        return profile.getBoolean(TAG) || "Red Ribbon Experiment X-7".equalsIgnoreCase(name)
                || "X-7".equalsIgnoreCase(name) || name.toUpperCase(java.util.Locale.ROOT).endsWith(" X-7");
    }

    public static UUID dossierRecordId() { return DOSSIER_ID; }
    public static boolean hasSpotted(ServerPlayer player) { return player != null && player.getPersistentData().getBoolean(PLAYER_SPOTTED); }
    public static int sightings(ServerPlayer player) { return player == null ? 0 : player.getPersistentData().getInt(PLAYER_SIGHTINGS); }
    public static CompoundTag knownProfile(ServerPlayer player) {
        if (player == null || !hasSpotted(player)) return new CompoundTag();
        CompoundTag pd = player.getPersistentData();
        if (pd.contains(PLAYER_PROFILE, Tag.TAG_COMPOUND)) return pd.getCompound(PLAYER_PROFILE).copy();
        CompoundTag p = RedRibbonExperimentData.get(player.serverLevel()).profile(); if (!p.isEmpty()) p.putBoolean(TAG, true); return p;
    }

    public static void markSpotted(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !isExperiment(fighter)) return;
        CompoundTag pd = player.getPersistentData();
        if (!pd.getBoolean(PLAYER_SPOTTED)) pd.putInt(PLAYER_SIGHTINGS, 1);
        else pd.putInt(PLAYER_SIGHTINGS, Math.min(10_000, pd.getInt(PLAYER_SIGHTINGS) + 1));
        pd.putBoolean(PLAYER_SPOTTED, true);
        CompoundTag profile = fighter.writeMemoryProfile(); profile.putBoolean(TAG, true); pd.put(PLAYER_PROFILE, profile);
    }

    public static void writeProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        if (isExperiment(fighter)) profile.putBoolean(TAG, true);
    }
    public static void restoreProfile(AmbientFighterEntity fighter, CompoundTag profile) {
        if (fighter != null && profile != null && profile.getBoolean(TAG)) {
            fighter.getPersistentData().putBoolean(TAG, true);
            fighter.configureRedRibbonExperimentAppearance();
        }
    }

    /** Stronger than normal aptitude without copying the player's BP. */
    public static double trainingEfficiency(AmbientFighterEntity fighter) { return isExperiment(fighter) ? 2.65D : 1.0D; }
    public static double growthMultiplier(ServerLevel level, AmbientFighterEntity fighter) {
        if (!isExperiment(fighter)) return 1.0D;
        double player = WorldPowerScaler.activePlayerPowerPressure(level, fighter);
        double own = Math.max(1.0D, fighter.getPermanentBattlePower());
        if (player <= 0.0D) return 2.2D;
        double r = own / player;
        if (r <= 0.55D) return 7.0D;
        if (r <= 1.0D) return 5.2D;
        if (r <= 1.8D) return 3.4D;
        if (r <= 3.2D) return 2.1D;
        if (r <= 5.5D) return 1.25D;
        return 0.65D; // even far ahead, engineered biology loses much less efficiency than ordinary fighters
    }
    public static double progressionCeiling(ServerLevel level, AmbientFighterEntity fighter, double ordinaryCeiling) {
        if (!isExperiment(fighter)) return ordinaryCeiling;
        double player = WorldPowerScaler.activePlayerPowerPressure(level, fighter);
        double world = WorldPowerScaler.resolveWorldAnchor(level, fighter.blockPosition());
        return Math.max(ordinaryCeiling, Math.max(world * 11.0D, player > 0.0D ? player * 4.2D : 0.0D));
    }

    /** Explicit retaliation authority, including Peaceful where vanilla hostile target goals are suppressed. */
    public static void onAttacked(AmbientFighterEntity fighter, ServerPlayer attacker) {
        if (fighter == null || attacker == null || !isExperiment(fighter) || fighter.level().isClientSide) return;
        markSpotted(attacker, fighter);
        FighterAmbientActivityManager.cancel(fighter);
        FighterNpcSocialManager.cancelFor(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) fighter.stopMeditation(false);
        fighter.setNonCombatant(false);
        fighter.setAggressive(true);
        fighter.setTarget(attacker);
        fighter.getPersistentData().putUUID(RETALIATE_PLAYER, attacker.getUUID());
        fighter.getPersistentData().putLong(RETALIATE_UNTIL, fighter.level().getGameTime() + 1200L);
    }

    public static void enforceIdentity(AmbientFighterEntity fighter) {
        if (fighter == null || !isExperiment(fighter)) return;
        fighter.getPersistentData().putBoolean(TAG, true);
        fighter.detachMemory(null, null);
        fighter.leaveFaction();
        fighter.setFighterName("Red Ribbon Experiment X-7");
        fighter.configureRedRibbonExperimentAppearance();
        // X-7's Red Ribbon uniform is its dedicated render overlay. Clear only wearable armor so
        // no generated/persisted clothing clips underneath it; weapons and hand equipment survive.
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.item.ItemStack.EMPTY);
        fighter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
        fighter.setFlightUnlockedForDebug(true);
        fighter.setPersistenceRequired();
        fighter.setNonCombatant(false);
    }

    /** Called from the fighter tick; returns false because the experiment keeps ordinary LW AI ownership. */
    public static boolean tick(AmbientFighterEntity fighter) {
        if (!isExperiment(fighter) || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return false;
        enforceIdentity(fighter);
        long now = level.getGameTime();
        CompoundTag persistent = fighter.getPersistentData();
        // The dedicated X-7 spar is owned entirely by the sanctioned-match system. Hunts,
        // retaliation and reinforcement calls must not leak into the controlled bout.
        if (fighter.isSanctionedMatchParticipant()) {
            persistent.remove(RETALIATE_PLAYER);
            persistent.remove(RETALIATE_UNTIL);
            return false;
        }
        if (persistent.hasUUID(RETALIATE_PLAYER)) {
            UUID playerId = persistent.getUUID(RETALIATE_PLAYER);
            ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(playerId);
            long until = persistent.getLong(RETALIATE_UNTIL);
            if (attacker != null && attacker.isAlive() && attacker.level() == level && now <= until
                    && !attacker.isCreative() && !attacker.isSpectator()) {
                fighter.setNonCombatant(false);
                fighter.setAggressive(true);
                if (fighter.getTarget() != attacker) fighter.setTarget(attacker);
            } else if (now > until || attacker == null || !attacker.isAlive()) {
                persistent.remove(RETALIATE_PLAYER);
                persistent.remove(RETALIATE_UNTIL);
            }
        }
        if (now >= fighter.getPersistentData().getLong(NEXT_SNAPSHOT)) {
            fighter.getPersistentData().putLong(NEXT_SNAPSHOT, now + 40L);
            RedRibbonExperimentData.get(level).updateSnapshot(fighter.writeMemoryProfile(), fighter.getX(), fighter.getY(), fighter.getZ());
            // A normal-looking menace enters the dossier when the player genuinely gets close
            // enough to identify it; no omniscient menu reveal is used.
            level.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(fighter) <= 48.0D * 48.0D)
                    .forEach(p -> { if (!hasSpotted(p)) markSpotted(p, fighter); });
        }

        // X-7 is an engineered killing machine, not a civilian simulation participant. Natural R40
        // incidents deliberately stay single-victim and widely spaced; the operator debug path alone
        // keeps the old short multi-victim chain for repeatable QA. Training still owns X-7 until a
        // hunt begins, and ordinary dialogue/concession logic cannot silently cancel an active hunt.
        int remaining = persistent.getInt(HUNT_REMAINING);
        if (remaining > 0) {
            Entity current = fighter.getTarget();
            boolean valid = current instanceof AmbientFighterEntity victim && validSlaughterVictim(fighter, victim);
            if (!valid && now >= persistent.getLong(HUNT_NEXT_CHAIN)) {
                acquireSlaughterTarget(fighter, level);
            } else if (valid) {
                fighter.setNonCombatant(false);
                fighter.setAggressive(true);
                if (fighter.getTarget() != current) fighter.setTarget((net.minecraft.world.entity.LivingEntity) current);
            }
        } else if (fighter.getTarget() == null && !FighterAmbientActivityManager.isActive(fighter)
                && !fighter.isMeditating() && !fighter.isPreparingMeditation()
                && fighter.getHealth() >= fighter.getMaxHealth() * 0.45F
                && now >= persistent.getLong(NEXT_HUNT)) {
            beginHunt(fighter, false);
            acquireSlaughterTarget(fighter, level);
        }

        Entity target = fighter.getTarget();
        UUID targetId = target == null ? null : target.getUUID();
        CompoundTag pd = fighter.getPersistentData();
        if (targetId == null) {
            if (fighter.getHealth() >= fighter.getMaxHealth() * 0.82F) {
                pd.remove(REINFORCEMENTS_USED); pd.remove(REINFORCEMENT_TARGET);
            }
            return false;
        }
        if (fighter.tickCount % 10 == Math.floorMod(fighter.getUUID().hashCode(), 10)
                && target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
            maintainReinforcements(level, fighter, livingTarget);
        }
        if (!pd.hasUUID(REINFORCEMENT_TARGET) || !targetId.equals(pd.getUUID(REINFORCEMENT_TARGET))) {
            pd.putUUID(REINFORCEMENT_TARGET, targetId);
            // Do not re-arm the half-health call simply because a multi-victim hunt changed target.
            // It re-arms only after X-7 genuinely recovers above the reset threshold.
            if (!pd.contains(REINFORCEMENTS_USED)) pd.putBoolean(REINFORCEMENTS_USED, false);
        }
        if (!pd.getBoolean(REINFORCEMENTS_USED) && fighter.getHealth() <= fighter.getMaxHealth() * 0.50F) {
            trySpawnReinforcements(level, fighter, target);
        }
        return false;
    }


    private static void beginHunt(AmbientFighterEntity fighter, boolean forced) {
        CompoundTag pd = fighter.getPersistentData();
        int count = forced ? 3 : 1; // Natural X-7 incidents are now single-victim events; debug keeps the multi-victim QA path.
        pd.putInt(HUNT_REMAINING, count);
        pd.putLong(HUNT_NEXT_CHAIN, fighter.level().getGameTime());
        pd.remove(HUNT_TARGET);
        pd.remove(HUNT_SPARE);
        pd.putBoolean(HUNT_FORCED, forced);
        pd.putLong(NEXT_HUNT, fighter.level().getGameTime() + (forced ? 2400L : 12_000L + fighter.getRandom().nextInt(12_001)));
    }

    private static boolean validSlaughterVictim(AmbientFighterEntity fighter, AmbientFighterEntity other) {
        return other != null && other != fighter && other.isAlive() && !WorldMenaceManager.isWorldMenace(other)
                && !other.isDefeated() && !other.isCaptive() && !other.isSanctionedMatchParticipant()
                && fighter.canAttack(other);
    }

    private static boolean acquireSlaughterTarget(AmbientFighterEntity fighter, ServerLevel level) {
        CompoundTag pd = fighter.getPersistentData();
        if (pd.getInt(HUNT_REMAINING) <= 0) return false;
        UUID previous = pd.hasUUID(HUNT_TARGET) ? pd.getUUID(HUNT_TARGET) : null;
        AmbientFighterEntity victim = level.getEntitiesOfClass(AmbientFighterEntity.class, fighter.getBoundingBox().inflate(52.0D), other ->
                        validSlaughterVictim(fighter, other) && (previous == null || !previous.equals(other.getUUID())))
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (victim == null) {
            // No local victim: end this chain rather than making X-7 chase the whole loaded world.
            pd.putInt(HUNT_REMAINING, 0);
            pd.remove(HUNT_TARGET);
            pd.remove(HUNT_SPARE);
            return false;
        }
        FighterAmbientActivityManager.cancel(fighter);
        FighterNpcSocialManager.cancelFor(fighter);
        if (fighter.isMeditating() || fighter.isPreparingMeditation()) fighter.stopMeditation(false);
        fighter.setNonCombatant(false);
        fighter.setAggressive(true);
        fighter.setTarget(victim);
        pd.putUUID(HUNT_TARGET, victim.getUUID());
        // X-7 remains terrifying without acting like an ambient save-file exterminator: most natural
        // incidents end with the victim defeated/alive. Forced QA slaughter remains lethal.
        pd.putBoolean(HUNT_SPARE, !pd.getBoolean(HUNT_FORCED) && fighter.getRandom().nextFloat() < 0.60F);
        return true;
    }

    public static boolean isActiveSlaughterTarget(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (!isExperiment(attacker) || victim == null) return false;
        CompoundTag pd = attacker.getPersistentData();
        return pd.getInt(HUNT_REMAINING) > 0 && pd.hasUUID(HUNT_TARGET)
                && pd.getUUID(HUNT_TARGET).equals(victim.getUUID());
    }

    /** Finishing policy for X-7. Returns true when a natural incident chose a deliberate non-lethal finish. */
    public static boolean shouldSpareVictim(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        return isActiveSlaughterTarget(attacker, victim) && attacker.getPersistentData().getBoolean(HUNT_SPARE);
    }

    public static void onVictimSpared(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (!isActiveSlaughterTarget(attacker, victim)) return;
        finishHuntStep(attacker, victim, false);
        ReactiveWorldManager.rememberEvent(victim, "X7_SPARED", "Red Ribbon Experiment X-7",
                "X-7 defeated them and deliberately left them alive.");
    }

    /** Immediate local faction defense when X-7 attacks one of their people. */
    public static void onFactionMemberAttacked(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (attacker == null || victim == null || !isExperiment(attacker) || !victim.isFactionMember()
                || !(victim.level() instanceof ServerLevel level)) return;
        String factionId = victim.getFactionId();
        if (factionId == null || factionId.isBlank()) return;
        for (AmbientFighterEntity defender : level.getEntitiesOfClass(AmbientFighterEntity.class,
                victim.getBoundingBox().inflate(56.0D), other -> other.isAlive() && !other.isDefeated()
                        && !other.isCaptive() && !other.isNonCombatant() && !WorldMenaceManager.isWorldMenace(other)
                        && factionId.equals(other.getFactionId()))) {
            FighterAmbientActivityManager.cancel(defender);
            FighterNpcSocialManager.cancelFor(defender);
            if (defender.isMeditating() || defender.isPreparingMeditation()) defender.stopMeditation(false);
            defender.setNonCombatant(false);
            defender.setAggressive(true);
            // Duel-opponent is used only as the existing explicit-target permission gate here;
            // normal combat AI and non-lethal concession still own the actual defense fight.
            defender.startDuel(attacker);
            ReactiveWorldManager.rememberEvent(defender, "X7_ATTACK", victim.getFighterName(),
                    "X-7 attacked " + victim.getFighterName() + ", a member of our faction.");
        }
    }

    public static void onVictimKilled(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (attacker == null || victim == null || !isExperiment(attacker)) return;
        boolean wasHunt = isActiveSlaughterTarget(attacker, victim);
        if (wasHunt) finishHuntStep(attacker, victim, true);
        if (!(attacker.level() instanceof ServerLevel level)) return;
        if (victim.isFactionMember()) onFactionMemberAttacked(attacker, victim);

        // This is intentionally news, not omniscient dossier knowledge. The report reaches online
        // players without exposing X-7's hidden character sheet or marking it as physically seen.
        Component line = Component.literal("[Living World] Reports spread: Red Ribbon Experiment X-7 killed "
                + victim.getFighterName() + ".").withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!player.isSpectator()) player.displayClientMessage(line, false);
        }
        for (AmbientFighterEntity witness : level.getEntitiesOfClass(AmbientFighterEntity.class,
                victim.getBoundingBox().inflate(64.0D), other -> other.isAlive() && other != attacker && other != victim
                        && !WorldMenaceManager.isWorldMenace(other))) {
            ReactiveWorldManager.rememberEvent(witness, "X7_KILL", victim.getFighterName(),
                    "Red Ribbon Experiment X-7 killed " + victim.getFighterName() + ".");
        }
    }

    private static void finishHuntStep(AmbientFighterEntity attacker, AmbientFighterEntity victim, boolean killed) {
        CompoundTag pd = attacker.getPersistentData();
        int remaining = Math.max(0, pd.getInt(HUNT_REMAINING) - 1);
        pd.putInt(HUNT_REMAINING, remaining);
        pd.remove(HUNT_SPARE);
        attacker.setTarget(null);
        attacker.setAggressive(false);
        if (remaining > 0) {
            pd.putLong(HUNT_NEXT_CHAIN, attacker.level().getGameTime() + 55L + attacker.getRandom().nextInt(66));
        } else {
            pd.remove(HUNT_TARGET);
            pd.remove(HUNT_FORCED);
            pd.putLong(NEXT_HUNT, attacker.level().getGameTime() + 12_000L + attacker.getRandom().nextInt(12_001));
        }
    }

    /** Operator QA hook: deliberately preserves the old three-victim lethal chain for repeatable stress testing. */
    public static int debugSlaughter(ServerPlayer player) {
        if (player == null) return 0;
        AmbientFighterEntity fighter = findLoaded(player.getServer(), RedRibbonExperimentData.get(player.serverLevel()).entityId());
        if (fighter == null) return 0;
        beginHunt(fighter, true);
        return acquireSlaughterTarget(fighter, (ServerLevel) fighter.level()) ? 1 : 0;
    }

    /** Immediate half-health reinforcement check, invoked from incoming damage as well as the tick fallback. */
    public static void onIncomingDamage(AmbientFighterEntity fighter, net.minecraft.world.entity.LivingEntity attacker, float amount) {
        if (fighter == null || !isExperiment(fighter) || fighter.isSanctionedMatchParticipant()
                || !(fighter.level() instanceof ServerLevel level)) return;
        float projected = fighter.getHealth() - Math.max(0.0F, amount);
        if (projected > fighter.getMaxHealth() * 0.50F) return;
        Entity target = attacker != null ? attacker : fighter.getTarget();
        trySpawnReinforcements(level, fighter, target);
    }

    private static void trySpawnReinforcements(ServerLevel level, AmbientFighterEntity fighter, Entity target) {
        CompoundTag pd = fighter.getPersistentData();
        UUID targetId = target == null ? null : target.getUUID();
        if (targetId != null && (!pd.hasUUID(REINFORCEMENT_TARGET) || !targetId.equals(pd.getUUID(REINFORCEMENT_TARGET)))) {
            pd.putUUID(REINFORCEMENT_TARGET, targetId);
            if (!pd.contains(REINFORCEMENTS_USED)) pd.putBoolean(REINFORCEMENTS_USED, false);
        }
        if (pd.getBoolean(REINFORCEMENTS_USED)) return;
        int count = 2 + fighter.getRandom().nextInt(3);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawnRedRibbonSoldier(level, fighter, target instanceof net.minecraft.world.entity.LivingEntity living ? living : null, i)) spawned++;
        }
        if (spawned <= 0) return; // retry on a later tick/damage if native creation actually failed
        pd.putBoolean(REINFORCEMENTS_USED, true);
        if (target instanceof ServerPlayer player) {
            markSpotted(player, fighter);
            player.displayClientMessage(Component.literal("Red Ribbon reinforcements move in around X-7.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    private static void maintainReinforcements(ServerLevel level, AmbientFighterEntity experiment,
                                               net.minecraft.world.entity.LivingEntity target) {
        if (level == null || experiment == null || target == null || !target.isAlive()) return;
        for (Mob soldier : level.getEntitiesOfClass(Mob.class, experiment.getBoundingBox().inflate(96.0D), mob -> {
            CompoundTag pd = mob.getPersistentData();
            return mob.isAlive() && pd.getBoolean("LWX7Reinforcement") && pd.hasUUID("LWX7Owner")
                    && experiment.getUUID().equals(pd.getUUID("LWX7Owner"));
        })) {
            soldier.setPersistenceRequired();
            soldier.getPersistentData().putUUID("LWX7ReinforcementTarget", target.getUUID());
            if (soldier.getTarget() != target) soldier.setTarget(target);
            soldier.setAggressive(true);
            if (soldier.distanceToSqr(target) > 3.0D * 3.0D && !soldier.isNoAi())
                soldier.getNavigation().moveTo(target, 1.25D);
        }
    }

    private static boolean spawnRedRibbonSoldier(ServerLevel level, AmbientFighterEntity experiment,
                                                  net.minecraft.world.entity.LivingEntity target, int salt) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", "red_ribbon_soldier"));
        if (type == null) return false;
        Entity raw = type.create(level); if (!(raw instanceof Mob soldier)) return false;
        double angle = (Math.PI * 2.0D / 4.0D) * salt + experiment.getRandom().nextDouble() * 0.7D;
        BlockPos rough = BlockPos.containing(experiment.getX() + Math.cos(angle) * (3.0D + experiment.getRandom().nextDouble()*2.0D),
                experiment.getY(), experiment.getZ() + Math.sin(angle) * (3.0D + experiment.getRandom().nextDouble()*2.0D));
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, rough, experiment.getRandom(), 1, 6, 12);
        if (safe == null) safe = experiment.blockPosition();
        soldier.moveTo(safe.getX()+0.5D, safe.getY(), safe.getZ()+0.5D, experiment.getYRot(), 0.0F);
        try { soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(safe), MobSpawnType.EVENT, null, null); }
        catch (Throwable ignored) {}
        soldier.getPersistentData().putBoolean("LWX7Reinforcement", true);
        soldier.getPersistentData().putUUID("LWX7Owner", experiment.getUUID());
        if (target != null) soldier.getPersistentData().putUUID("LWX7ReinforcementTarget", target.getUUID());
        soldier.setPersistenceRequired();
        if (!level.addFreshEntity(soldier)) return false;
        if (target != null && target.isAlive()) {
            soldier.setTarget(target);
            soldier.setAggressive(true);
            soldier.getNavigation().moveTo(target, 1.25D);
        }
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer(); ServerLevel level = server.overworld(); long now = level.getGameTime();
        if (now % 80L != 0L) return;
        RedRibbonExperimentData data = RedRibbonExperimentData.get(level);
        if (!data.initialized()) { data.scheduleFirst(now + FIRST_MIN + java.util.concurrent.ThreadLocalRandom.current().nextInt(FIRST_JITTER)); return; }
        if (data.active()) {
            AmbientFighterEntity loaded = findLoaded(server, data.entityId());
            if (loaded != null) { enforceIdentity(loaded); return; }
            BlockPos last = BlockPos.containing(data.x(), data.y(), data.z());
            if (level.hasChunkAt(last)) {
                // If the recorded chunk is already loaded and the exact singleton is absent, recover the logical body.
                ServerPlayer anchor = closestPlayer(level, last);
                if (anchor != null) spawn(level, anchor, data, false);
            }
            return;
        }
        if (data.returnAt() > now) return;
        ServerPlayer anchor = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).findAny().orElse(null);
        if (anchor != null) spawn(level, anchor, data, false);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter) || !(fighter.level() instanceof ServerLevel level)) return;

        Entity killerEntity = event.getSource().getEntity();
        if (!isExperiment(fighter) && killerEntity instanceof AmbientFighterEntity killer && isExperiment(killer)) {
            onVictimKilled(killer, fighter);
            return;
        }
        if (!isExperiment(fighter)) return;

        long now = level.getServer().overworld().getGameTime();
        CompoundTag profile = fighter.writeMemoryProfile(); profile.putBoolean(TAG, true);
        // No death evolution: the exact earned/trained profile is what returns.
        RedRibbonExperimentData.get(level).markDefeated(profile, now + RETURN_MIN + fighter.getRandom().nextInt(RETURN_JITTER),
                fighter.getX(), fighter.getY(), fighter.getZ());
    }

    private static AmbientFighterEntity spawn(ServerLevel level, ServerPlayer anchor, RedRibbonExperimentData data, boolean debug) {
        BlockPos pos = AmbientFighterSpawner.findSafeGroundAroundSeparated(level, anchor.blockPosition(), anchor.getRandom(), debug?8:58, debug?20:112, debug?16:42, debug?16.0D:34.0D);
        if (pos == null && debug) pos = AmbientFighterSpawner.findSafeGroundAround(level, anchor.blockPosition(), anchor.getRandom(), 8, 28, 24);
        if (pos == null) return null;
        AmbientFighterEntity fighter = LWEntities.AMBIENT_FIGHTER.get().create(level); if (fighter == null) return null;
        CompoundTag profile = data.profile();
        if (!profile.isEmpty() && profile.contains("Name", Tag.TAG_STRING)) fighter.initializeFromMemory(profile);
        else {
            fighter.initializeAs(FighterAlignment.NEUTRAL, FighterRank.VETERAN, FighterPersonality.PROUD, FighterRace.HUMAN, FighterArchetype.MARTIAL_ARTIST);
            int start = (int)Math.min(Integer.MAX_VALUE-1L, Math.max(85_000D, Math.max(WorldPowerScaler.resolveWorldAnchor(level,pos)*5.5D, WorldPowerScaler.activePlayerPowerPressure(level)*1.35D)));
            fighter.setBattlePowerAndRefresh(start);
            fighter.getLegacyData().putDouble("LWPotentialV1", 1.68D);
        }
        fighter.getPersistentData().putBoolean(TAG, true); enforceIdentity(fighter);
        fighter.moveTo(pos.getX()+0.5D,pos.getY(),pos.getZ()+0.5D,anchor.getYRot()+180.0F,0.0F);
        if (!level.addFreshEntity(fighter)) return null;
        data.markActive(fighter.getUUID(), fighter.writeMemoryProfile(), fighter.getX(), fighter.getY(), fighter.getZ());
        return fighter;
    }

    private static AmbientFighterEntity findLoaded(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof AmbientFighterEntity f && isExperiment(f)) return f;
        return null;
    }
    private static ServerPlayer closestPlayer(ServerLevel level, BlockPos pos) {
        return level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).min(java.util.Comparator.comparingDouble(p->p.distanceToSqr(pos.getX()+0.5D,pos.getY(),pos.getZ()+0.5D))).orElse(null);
    }

    public static int debugSpawn(ServerPlayer player) {
        if (player == null) return 0;
        RedRibbonExperimentData data = RedRibbonExperimentData.get(player.serverLevel());
        AmbientFighterEntity loaded = findLoaded(player.getServer(), data.entityId());
        if (loaded != null) { markSpotted(player, loaded); return 1; }
        AmbientFighterEntity f = spawn(player.serverLevel(), player, data, true); if (f != null) { markSpotted(player,f); return 1; } return 0;
    }
    public static int debugTeleport(ServerPlayer player) {
        if (player == null) return 0; RedRibbonExperimentData data=RedRibbonExperimentData.get(player.serverLevel()); AmbientFighterEntity f=findLoaded(player.getServer(),data.entityId());
        if(f==null)return 0; BlockPos safe=AmbientFighterSpawner.findSafeGroundAround(player.serverLevel(),f.blockPosition(),player.getRandom(),2,6,10); if(safe==null)safe=f.blockPosition(); player.teleportTo(player.serverLevel(),safe.getX()+0.5D,safe.getY(),safe.getZ()+0.5D,player.getYRot(),player.getXRot()); markSpotted(player,f); return 1;
    }
    public static String status(ServerPlayer player) {
        if(player==null)return "Unknown"; RedRibbonExperimentData d=RedRibbonExperimentData.get(player.serverLevel()); if(d.active())return "Active • confirmed defeats: "+d.defeats(); if(d.returnAt()>player.serverLevel().getGameTime())return "Absent • expected to return"; return "Unconfirmed";
    }

    public static List<String> overviewLines(ServerPlayer player, AmbientFighterEntity fighter) {
        if (fighter == null || !isExperiment(fighter)) return List.of();
        return List.of("!! WORLD MENACE • RED RIBBON EXPERIMENT X-7",
                "~ Red Ribbon engineered combatant",
                "## Status",
                "* " + status(player),
                "## Field Pattern",
                "* Operates as a mobile combatant rather than remaining in one location.",
                "* Frequently trains between violent encounters.",
                "* Red Ribbon soldiers have been observed responding when X-7 is badly wounded.",
                "* X-7 has reappeared after confirmed defeats.");
    }
    public static List<String> storyLines(ServerPlayer player, AmbientFighterEntity fighter) {
        int seen = sightings(player); int defeats = fighter != null && fighter.level() instanceof ServerLevel l ? RedRibbonExperimentData.get(l).defeats() : 0;
        return List.of("## Project X-7",
                "* Classification: Red Ribbon enhancement experiment",
                "* Confirmed sightings: " + seen + " • confirmed defeats: " + defeats,
                "## Observed Record",
                ". Later sightings have shown increasingly dangerous combat ability.",
                ". Red Ribbon soldiers have answered X-7's calls under heavy pressure.",
                ". X-7 behaves as a physical Red Ribbon combatant, distinct from the Herobrine anomaly.");
    }
}
