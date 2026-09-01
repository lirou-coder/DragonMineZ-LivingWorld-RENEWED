package com.dmzlwfusion;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainSounds;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Living World NPC <-> NPC Metamoran fusion with debug bindings and an immersive interaction path.
 * Two original fighters remain the authoritative persistent characters. A
 * temporary fighter of the same LW entity type exists only for the fusion span.
 */
public final class NpcFusionManager {
    private static final String DEBUG_ROOT = "DMZLWFusionNpcDebug";
    private static final String FIRST = "First";
    private static final String SECOND = "Second";
    private static final String TEMP_ROOT = "DMZLWNpcFusionTemp";
    private static final String PARTNER_ROOT = "DMZLWFusionPartnerState";

    private static final Map<UUID, PendingDance> PENDING = new HashMap<>();
    private static final Map<UUID, UUID> NATURAL_FIRST = new HashMap<>();
    private static final Set<UUID> ACTIVE_FUSED = new HashSet<>();
    private static final Set<UUID> ORPHAN_TEMP = new HashSet<>();

    private NpcFusionManager() {}

    public static UUID firstId(ServerPlayer player) {
        CompoundTag root = debugRoot(player);
        return root.hasUUID(FIRST) ? root.getUUID(FIRST) : null;
    }

    public static UUID secondId(ServerPlayer player) {
        CompoundTag root = debugRoot(player);
        return root.hasUUID(SECOND) ? root.getUUID(SECOND) : null;
    }

    public static void clearBindings(ServerPlayer player) {
        player.getPersistentData().remove(DEBUG_ROOT);
    }

    public static boolean bindNearest(ServerPlayer player, boolean first) {
        LivingEntity nearest = LWFusionManager.nearestLivingWorldFighter(player, 12.0D);
        if (nearest == null) return false;
        UUID other = first ? secondId(player) : firstId(player);
        if (other != null && other.equals(nearest.getUUID())) return false;
        CompoundTag root = debugRoot(player);
        root.putUUID(first ? FIRST : SECOND, nearest.getUUID());
        player.getPersistentData().put(DEBUG_ROOT, root);
        return true;
    }

    public static LivingEntity boundFirst(ServerPlayer player) {
        return LWFusionManager.findPartner(player.getServer(), firstId(player));
    }

    public static LivingEntity boundSecond(ServerPlayer player) {
        return LWFusionManager.findPartner(player.getServer(), secondId(player));
    }

    public static boolean isPending(ServerPlayer player) {
        return player != null && PENDING.containsKey(player.getUUID());
    }

    public static int pendingTicks(ServerPlayer player) {
        PendingDance dance = player == null ? null : PENDING.get(player.getUUID());
        return dance == null ? 0 : dance.ticksLeft;
    }

    public static boolean startBoundFusion(ServerPlayer initiator, boolean instant) {
        if (initiator == null) return false;
        return startPairFusion(initiator, boundFirst(initiator), boundSecond(initiator), true, instant, 16.0D);
    }

    /**
     * Immersive NPC pairing: while the player has DMZ's Fusion action selected,
     * empty-hand interact with two LW fighters. The first click marks the left
     * participant; the second click performs strict native-style eligibility and
     * begins the actual dance. No debug command is involved.
     *
     * @return true when the interaction was consumed by the fusion selector.
     */
    public static boolean selectNaturalParticipant(ServerPlayer player, LivingEntity fighter) {
        if (player == null || fighter == null || !LivingWorldCompat.isLivingWorldFighter(fighter)) return false;
        if (PENDING.containsKey(player.getUUID())) {
            fail(player, "An NPC fusion dance you started is already in progress.");
            return true;
        }
        UUID current = NATURAL_FIRST.get(player.getUUID());
        if (current == null) {
            NATURAL_FIRST.put(player.getUUID(), fighter.getUUID());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[LW Fusion] " + LivingWorldCompat.fighterName(fighter) + " readies the LEFT side. Select a second fighter.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
        if (current.equals(fighter.getUUID())) {
            NATURAL_FIRST.remove(player.getUUID());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[LW Fusion] NPC fusion selection cleared.").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        LivingEntity first = LWFusionManager.findPartner(player.getServer(), current);
        NATURAL_FIRST.remove(player.getUUID());
        startPairFusion(player, first, fighter, false, false, 5.0D);
        return true;
    }

    public static void clearNaturalSelection(ServerPlayer player) {
        if (player != null) NATURAL_FIRST.remove(player.getUUID());
    }

    /** Starts the existing full NPC Fusion Dance from normal Living World combat AI. */
    public static boolean startAutonomousFusion(AmbientFighterEntity first, AmbientFighterEntity second) {
        if (first == null || second == null || !(first.level() instanceof ServerLevel level) || first.level() != second.level()) return false;
        ServerPlayer observer = level.players().stream()
                .filter(player -> !player.isSpectator() && player.distanceToSqr(first) <= 96.0D * 96.0D)
                .min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(first))).orElse(null);
        if (observer == null || PENDING.containsKey(observer.getUUID())) return false;
        LivingEntity inheritedTarget = first.getTarget() != null ? first.getTarget() : second.getTarget();
        if (inheritedTarget != null) {
            first.getPersistentData().putUUID("LWAutonomousFusionTarget", inheritedTarget.getUUID());
            second.getPersistentData().putUUID("LWAutonomousFusionTarget", inheritedTarget.getUUID());
        }
        first.getPersistentData().putBoolean("LWAutonomousFusion", true);
        second.getPersistentData().putBoolean("LWAutonomousFusion", true);
        LivingEntity firstTarget = first.getTarget();
        LivingEntity secondTarget = second.getTarget();
        first.setTarget(null);
        second.setTarget(null); // strict native eligibility expects idle targets; the target is restored onto the fusion.
        boolean started = startPairFusion(observer, first, second, false, false, 5.0D, true);
        if (!started) {
            first.setTarget(firstTarget);
            second.setTarget(secondTarget);
            clearAutonomousTags(first, second);
        }
        return started;
    }

    private static boolean startPairFusion(ServerPlayer initiator, LivingEntity first, LivingEntity second,
                                           boolean debugForce, boolean instant, double maxRange) {
        return startPairFusion(initiator, first, second, debugForce, instant, maxRange, false);
    }

    private static boolean startPairFusion(ServerPlayer initiator, LivingEntity first, LivingEntity second,
                                           boolean debugForce, boolean instant, double maxRange, boolean autonomous) {
        if (initiator == null || PENDING.containsKey(initiator.getUUID())) return false;
        if (!eligiblePair(initiator, first, second, debugForce, maxRange)) return false;

        double midX = (first.getX() + second.getX()) * 0.5D;
        double midY = Math.max(first.getY(), second.getY());
        double midZ = (first.getZ() + second.getZ()) * 0.5D;
        float yaw = first.getUUID().compareTo(second.getUUID()) <= 0 ? first.getYRot() : second.getYRot();

        storeNpcBackup(first, initiator.getUUID(), second.getUUID());
        storeNpcBackup(second, initiator.getUUID(), first.getUUID());
        if (instant) {
            return createFusedEntity(initiator, first, second, midX, midY, midZ, yaw) != null;
        }

        freezeForDance(first);
        freezeForDance(second);
        positionDancePair(first, second, midX, midY, midZ, yaw);
        FusionAnimations.trigger(first, true);
        FusionAnimations.trigger(second, false);
        PENDING.put(initiator.getUUID(), new PendingDance(
                initiator.getUUID(), first.getUUID(), second.getUUID(),
                FusionAnimations.DANCE_TICKS, midX, midY, midZ, yaw));
        if (!autonomous) initiator.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "[LW Fusion] NPC Fusion Dance started: " + LivingWorldCompat.fighterName(first)
                        + " + " + LivingWorldCompat.fighterName(second))
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickPending(server);
        tickActive(server);
        tickOrphans(server);
    }

    private static void tickPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (UUID initiatorId : new HashSet<>(PENDING.keySet())) {
            PendingDance dance = PENDING.get(initiatorId);
            if (dance == null) continue;
            ServerPlayer initiator = server.getPlayerList().getPlayer(initiatorId);
            LivingEntity first = LWFusionManager.findPartner(server, dance.first);
            LivingEntity second = LWFusionManager.findPartner(server, dance.second);
            if (initiator == null || first == null || second == null || !first.isAlive() || !second.isAlive()) {
                cancelPending(server, initiatorId, true);
                continue;
            }
            if (first.level() != second.level()) {
                cancelPending(server, initiatorId, true);
                continue;
            }

            freezeForDance(first);
            freezeForDance(second);
            positionDancePair(first, second, dance.midX, dance.midY, dance.midZ, dance.yaw);
            dance.ticksLeft--;
            if (dance.ticksLeft <= 0) {
                PENDING.remove(initiatorId);
                FusionAnimations.stop(first);
                FusionAnimations.stop(second);
                createFusedEntity(initiator, first, second, dance.midX, dance.midY, dance.midZ, dance.yaw);
            }
        }
    }

    private static void tickActive(MinecraftServer server) {
        if (ACTIVE_FUSED.isEmpty()) return;
        for (UUID fusedId : new HashSet<>(ACTIVE_FUSED)) {
            LivingEntity fused = LWFusionManager.findAnyLiving(server, fusedId);
            if (fused == null || !isTemporaryFused(fused)) {
                ACTIVE_FUSED.remove(fusedId);
                continue;
            }
            CompoundTag root = fused.getPersistentData().getCompound(TEMP_ROOT);
            int timer = root.getInt("Timer") - 1;
            root.putInt("Timer", timer);
            fused.getPersistentData().put(TEMP_ROOT, root);
            if (timer <= 0 || !fused.isAlive()) finishFusion(server, fused, true);
        }
    }

    private static void tickOrphans(MinecraftServer server) {
        if (ORPHAN_TEMP.isEmpty()) return;
        for (UUID id : new HashSet<>(ORPHAN_TEMP)) {
            LivingEntity fused = LWFusionManager.findAnyLiving(server, id);
            if (fused != null && isTemporaryFused(fused) && !ACTIVE_FUSED.contains(id)) {
                finishFusion(server, fused, false);
            }
            ORPHAN_TEMP.remove(id);
        }
    }

    public static void cancelForInitiator(ServerPlayer player) {
        if (player == null) return;
        cancelPending(player.getServer(), player.getUUID(), true);
        endForInitiator(player.getServer(), player.getUUID());
    }

    public static void cancelPending(MinecraftServer server, UUID initiatorId, boolean restore) {
        PendingDance dance = PENDING.remove(initiatorId);
        if (dance == null || server == null) return;
        LivingEntity first = LWFusionManager.findPartner(server, dance.first);
        LivingEntity second = LWFusionManager.findPartner(server, dance.second);
        clearAutonomousTags(first, second);
        if (first != null) {
            FusionAnimations.stop(first);
            if (restore) LWFusionManager.restoreOrphanPartner(first);
        }
        if (second != null) {
            FusionAnimations.stop(second);
            if (restore) LWFusionManager.restoreOrphanPartner(second);
        }
    }

    public static int endForInitiator(MinecraftServer server, UUID initiatorId) {
        if (server == null || initiatorId == null) return 0;
        int ended = 0;
        for (UUID fusedId : new HashSet<>(ACTIVE_FUSED)) {
            LivingEntity fused = LWFusionManager.findAnyLiving(server, fusedId);
            if (fused == null || !isTemporaryFused(fused)) continue;
            CompoundTag root = fused.getPersistentData().getCompound(TEMP_ROOT);
            if (root.hasUUID("Initiator") && initiatorId.equals(root.getUUID("Initiator"))) {
                finishFusion(server, fused, true);
                ended++;
            }
        }
        return ended;
    }

    public static int restoreNearby(ServerPlayer player, double radius) {
        if (player == null) return 0;
        int restored = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius))) {
            if (isTemporaryFused(entity)) {
                finishFusion(player.getServer(), entity, false);
                restored++;
            } else if (LivingWorldCompat.isLivingWorldFighter(entity)
                    && hasNpcBackup(entity)
                    && !partnerBelongsToLiveNpcSession(entity, player.getServer())) {
                LWFusionManager.restoreOrphanPartner(entity);
                restored++;
            }
        }
        return restored;
    }

    public static void onEntityJoin(LivingEntity entity) {
        if (entity != null && isTemporaryFused(entity) && !ACTIVE_FUSED.contains(entity.getUUID())) {
            ORPHAN_TEMP.add(entity.getUUID());
        }
    }

    public static void onTemporaryDeath(LivingEntity entity) {
        if (entity != null && isTemporaryFused(entity)) {
            finishFusion(entity.level().getServer(), entity, true);
        }
    }

    public static void endAll(MinecraftServer server) {
        for (UUID initiator : new HashSet<>(PENDING.keySet())) cancelPending(server, initiator, true);
        for (UUID fusedId : new HashSet<>(ACTIVE_FUSED)) {
            LivingEntity fused = LWFusionManager.findAnyLiving(server, fusedId);
            if (fused != null && isTemporaryFused(fused)) finishFusion(server, fused, true);
        }
        PENDING.clear();
        NATURAL_FIRST.clear();
        ACTIVE_FUSED.clear();
        ORPHAN_TEMP.clear();
    }

    public static boolean partnerBelongsToLiveNpcSession(LivingEntity partner, MinecraftServer server) {
        if (partner == null || server == null || !hasNpcBackup(partner)) return false;
        CompoundTag backup = partner.getPersistentData().getCompound(PARTNER_ROOT);
        if (backup.hasUUID("Initiator")) {
            PendingDance dance = PENDING.get(backup.getUUID("Initiator"));
            if (dance != null && (partner.getUUID().equals(dance.first) || partner.getUUID().equals(dance.second))) return true;
        }
        if (backup.hasUUID("Fused")) {
            UUID fusedId = backup.getUUID("Fused");
            if (!ACTIVE_FUSED.contains(fusedId)) return false;
            LivingEntity fused = LWFusionManager.findAnyLiving(server, fusedId);
            return fused != null && isTemporaryFused(fused);
        }
        return false;
    }

    public static boolean isTemporaryFused(LivingEntity entity) {
        return entity != null && entity.getPersistentData().contains(TEMP_ROOT)
                && entity.getPersistentData().getCompound(TEMP_ROOT).getBoolean("Active");
    }

    public static int activeCount() {
        return ACTIVE_FUSED.size();
    }

    private static LivingEntity createFusedEntity(ServerPlayer initiator, LivingEntity first, LivingEntity second,
                                                   double x, double y, double z, float yaw) {
        boolean autonomous = first != null && first.getPersistentData().getBoolean("LWAutonomousFusion")
                || second != null && second.getPersistentData().getBoolean("LWAutonomousFusion");
        UUID inheritedTargetId = first != null && first.getPersistentData().hasUUID("LWAutonomousFusionTarget")
                ? first.getPersistentData().getUUID("LWAutonomousFusionTarget")
                : second != null && second.getPersistentData().hasUUID("LWAutonomousFusionTarget")
                ? second.getPersistentData().getUUID("LWAutonomousFusionTarget") : null;
        clearAutonomousTags(first, second);
        if (first == null || second == null || first.level() != second.level() || !(first.level() instanceof ServerLevel level)) {
            restorePair(first, second, x, y, z, yaw);
            return null;
        }
        Entity raw = first.getType().create(level);
        if (!(raw instanceof LivingEntity fused) || !LivingWorldCompat.isLivingWorldFighter(fused)) {
            restorePair(first, second, x, y, z, yaw);
            if (initiator != null) initiator.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[LW Fusion] Could not create a temporary fighter using the current Living World entity type.")
                    .withStyle(ChatFormatting.RED), false);
            return null;
        }

        String fusionName = LWFusionManager.buildFusionName(
                LivingWorldCompat.fighterName(first), LivingWorldCompat.fighterName(second), "METAMORU");
        int fusedPower = fusedBattlePower(first, second);
        String body = LWFusionManager.mixHex(LivingWorldCompat.bodyColor(first), LivingWorldCompat.bodyColor(second));
        String body2 = LWFusionManager.mixHex(LivingWorldCompat.bodyColor2(first), LivingWorldCompat.bodyColor2(second));
        String body3 = LWFusionManager.mixHex(LivingWorldCompat.bodyColor3(first), LivingWorldCompat.bodyColor3(second));
        String hair = LWFusionManager.mixHex(LivingWorldCompat.hairColor(first), LivingWorldCompat.hairColor(second));
        String eye1 = LWFusionManager.mixHex(LivingWorldCompat.eye1Color(first), LivingWorldCompat.eye1Color(second));
        String eye2 = LWFusionManager.mixHex(LivingWorldCompat.eye2Color(first), LivingWorldCompat.eye2Color(second));
        int aura = parseHex(LWFusionManager.mixHex(LivingWorldCompat.auraColor(first), LivingWorldCompat.auraColor(second)), 0xFFFFFF);

        try {
            CompoundTag template = new CompoundTag();
            first.saveWithoutId(template);
            template.remove("UUID");
            template.remove("Pos");
            template.remove("Motion");
            template.remove("Rotation");
            template.remove("Passengers");
            template.remove("Leash");
            // Never copy Forge persistent-data/capability state from an original
            // fighter into the temporary fusion (especially our own backup tag).
            template.remove("ForgeData");
            template.remove("ForgeCaps");
            template.putString("LWFighterName", fusionName);
            template.putInt("LWBattlePower", fusedPower);
            template.putBoolean("LWDefeated", false);
            template.putBoolean("LWCaptive", false);
            template.putBoolean("LWMeditating", false);
            template.putInt("LWActiveRacialForm", 0);
            template.putInt("LWKaiokenLevel", 0);
            template.putString("LWBodyColor", body);
            template.putString("LWBodyColor2", body2);
            template.putString("LWBodyColor3", body3);
            template.putString("LWHairColor", hair);
            template.putString("LWEye1Color", eye1);
            template.putString("LWEye2Color", eye2);
            // A fusion is not a third permanent citizen/faction member.
            template.remove("LWMemoryOwner");
            template.remove("LWMemoryRecord");
            template.putInt("LWMemoryEncounters", 0);
            template.putInt("LWMemoryRelationship", 0);
            template.putBoolean("LWMemoryRescued", false);
            template.putString("LWFactionId", "");
            template.putBoolean("LWFactionLeader", false);
            template.putString("LWFactionName", "");
            template.putString("LWFactionTitle", "");
            template.remove("LWPartyId");
            template.putBoolean("LWPartyCaptain", false);
            template.putString("LWWantedId", "");
            template.putInt("LWWantedLevel", 0);
            template.putString("LWWantedCrime", "");
            template.putInt("LWStoryRole", 0);
            fused.load(template);
            // The temporary fusion remains the same race, so preserve the strongest racial
            // progression learned by either original. This lets a fused Saiyan/Namekian/etc.
            // still escalate naturally in combat instead of being stuck at the first partner's
            // progression snapshot. Kaioken potential is likewise inherited if either knew it.
            if (fused instanceof AmbientFighterEntity fusedFighter
                    && first instanceof AmbientFighterEntity firstFighter
                    && second instanceof AmbientFighterEntity secondFighter) {
                fusedFighter.debugSetRacialSkill(Math.max(firstFighter.getRacialSkillLevel(), secondFighter.getRacialSkillLevel()));
                fusedFighter.setKaiokenPotential(firstFighter.hasKaiokenPotential() || secondFighter.hasKaiokenPotential());
            }
        } catch (RuntimeException ex) {
            restorePair(first, second, x, y, z, yaw);
            if (initiator != null) initiator.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[LW Fusion] Temporary fighter cloning failed safely: " + ex.getClass().getSimpleName())
                    .withStyle(ChatFormatting.RED), false);
            return null;
        }

        fused.moveTo(x, y, z, yaw, 0.0F);
        LivingWorldCompat.setFighterName(fused, fusionName);
        LivingWorldCompat.setBattlePower(fused, fusedPower);
        LivingWorldCompat.setAuraColor(fused, aura);
        fused.setInvisible(false);
        fused.setInvulnerable(false);
        fused.setSilent(false);
        if (fused instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setPersistenceRequired();
        }
        fused.setHealth(fused.getMaxHealth());

        CompoundTag root = new CompoundTag();
        root.putBoolean("Active", true);
        root.putUUID("First", first.getUUID());
        root.putUUID("Second", second.getUUID());
        if (initiator != null) root.putUUID("Initiator", initiator.getUUID());
        root.putString("FusionName", fusionName);
        int duration = Math.max(20, ConfigManager.getServerConfig().getGameplay().getFusionDurationSeconds() * 20);
        root.putInt("Timer", duration);
        fused.getPersistentData().put(TEMP_ROOT, root);

        if (!level.addFreshEntity(fused)) {
            restorePair(first, second, x, y, z, yaw);
            return null;
        }
        if (autonomous && inheritedTargetId != null && fused instanceof Mob mob
                && level.getEntity(inheritedTargetId) instanceof LivingEntity inheritedTarget && inheritedTarget.isAlive()) {
            mob.setTarget(inheritedTarget);
        }

        updateNpcBackupFused(first, fused.getUUID());
        updateNpcBackupFused(second, fused.getUUID());
        hideOriginalOnFusion(first, fused);
        hideOriginalOnFusion(second, fused);
        ACTIVE_FUSED.add(fused.getUUID());

        level.playSound(null, x, y, z, MainSounds.FUSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (initiator != null && !autonomous) initiator.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "[LW Fusion] " + LivingWorldCompat.fighterName(first) + " + "
                        + LivingWorldCompat.fighterName(second) + " -> " + fusionName
                        + " (BP " + fusedPower + ")")
                .withStyle(ChatFormatting.GREEN), false);
        return fused;
    }

    private static void clearAutonomousTags(LivingEntity first, LivingEntity second) {
        if (first != null) {
            first.getPersistentData().remove("LWAutonomousFusion");
            first.getPersistentData().remove("LWAutonomousFusionTarget");
        }
        if (second != null) {
            second.getPersistentData().remove("LWAutonomousFusion");
            second.getPersistentData().remove("LWAutonomousFusionTarget");
        }
    }

    private static boolean eligiblePair(ServerPlayer initiator, LivingEntity first, LivingEntity second,
                                        boolean debugForce, double maxRange) {
        if (first == null || second == null || first == second) {
            fail(initiator, "Bind two different loaded Living World fighters first.");
            return false;
        }
        if (!LivingWorldCompat.isLivingWorldFighter(first) || !LivingWorldCompat.isLivingWorldFighter(second)) {
            fail(initiator, "Both selections must be real Living World fighters.");
            return false;
        }
        if (first.level() != second.level()) {
            fail(initiator, "The two fighters are in different dimensions.");
            return false;
        }
        if (first.distanceToSqr(second) > maxRange * maxRange) {
            fail(initiator, "Bring the two fighters within " + (int)maxRange + " blocks.");
            return false;
        }
        if (hasNpcBackup(first) || hasNpcBackup(second) || isTemporaryFused(first) || isTemporaryFused(second)) {
            fail(initiator, "One fighter is already reserved by another fusion session.");
            return false;
        }
        if (LivingWorldCompat.unavailableForFusion(first) || LivingWorldCompat.unavailableForFusion(second)) {
            fail(initiator, "Both fighters must be alive, idle, combat-capable and available.");
            return false;
        }
        if (LivingWorldCompat.hasActiveForm(first) || LivingWorldCompat.hasActiveForm(second)) {
            fail(initiator, "Both fighters must return to base form first.");
            return false;
        }
        String race1 = LivingWorldCompat.raceId(first);
        String race2 = LivingWorldCompat.raceId(second);
        if (race1.isBlank() || !race1.equalsIgnoreCase(race2)) {
            fail(initiator, "Metamoran fusion requires the same race. Got " + race1 + " + " + race2 + ".");
            return false;
        }
        if (!debugForce) {
            LWFusionProfile one = LWFusionProfile.from(first);
            LWFusionProfile two = LWFusionProfile.from(second);
            double threshold = ConfigManager.getServerConfig().getGameplay().getMetamoruFusionThreshold();
            double gap = Math.abs(one.totalStats() - two.totalStats()) / (double)Math.max(one.totalStats(), two.totalStats());
            if (threshold > 0.0D && gap > threshold && !powerSyncEligible(first, second)) {
                fail(initiator, "Fusion partners must match their stats or bring current power levels within 35% so the stronger fighter can power down.");
                return false;
            }
        }
        return true;
    }

    public static String pairStatus(ServerPlayer player) {
        LivingEntity first = boundFirst(player);
        LivingEntity second = boundSecond(player);
        if (first == null && second == null) return "first=none, second=none";
        String a = first == null ? "none" : LivingWorldCompat.fighterName(first) + "/" + LivingWorldCompat.raceId(first) + "/BP " + LivingWorldCompat.battlePower(first);
        String b = second == null ? "none" : LivingWorldCompat.fighterName(second) + "/" + LivingWorldCompat.raceId(second) + "/BP " + LivingWorldCompat.battlePower(second);
        return "first=" + a + ", second=" + b;
    }

    public static boolean strictPairEligible(ServerPlayer player) {
        LivingEntity first = boundFirst(player);
        LivingEntity second = boundSecond(player);
        if (first == null || second == null || first == second) return false;
        if (LivingWorldCompat.unavailableForFusion(first) || LivingWorldCompat.unavailableForFusion(second)) return false;
        if (LivingWorldCompat.hasActiveForm(first) || LivingWorldCompat.hasActiveForm(second)) return false;
        if (!LivingWorldCompat.raceId(first).equalsIgnoreCase(LivingWorldCompat.raceId(second))) return false;
        LWFusionProfile one = LWFusionProfile.from(first);
        LWFusionProfile two = LWFusionProfile.from(second);
        double threshold = ConfigManager.getServerConfig().getGameplay().getMetamoruFusionThreshold();
        double gap = Math.abs(one.totalStats() - two.totalStats()) / (double)Math.max(one.totalStats(), two.totalStats());
        return threshold <= 0.0D || gap <= threshold || powerSyncEligible(first, second);
    }

    /** Dragon Ball-style secondary route: the stronger fighter can deliberately lower current power
     * to meet a partner that is already reasonably close. This never mutates either persistent BP. */
    private static boolean powerSyncEligible(LivingEntity first, LivingEntity second) {
        double a = Math.max(1.0D, LivingWorldCompat.battlePower(first));
        double b = Math.max(1.0D, LivingWorldCompat.battlePower(second));
        double gap = Math.abs(a - b) / Math.max(a, b);
        return gap <= 0.35D;
    }

    private static boolean usesPowerSynchronization(LivingEntity first, LivingEntity second) {
        LWFusionProfile one = LWFusionProfile.from(first);
        LWFusionProfile two = LWFusionProfile.from(second);
        double threshold = ConfigManager.getServerConfig().getGameplay().getMetamoruFusionThreshold();
        double gap = Math.abs(one.totalStats() - two.totalStats()) / (double)Math.max(one.totalStats(), two.totalStats());
        return threshold > 0.0D && gap > threshold && powerSyncEligible(first, second);
    }

    private static int fusedBattlePower(LivingEntity first, LivingEntity second) {
        LWFusionProfile leader = LWFusionProfile.from(first);
        LWFusionProfile partner = LWFusionProfile.from(second);
        double leaderStats = leader.totalStats();
        double partnerStats = partner.totalStats();
        if (usesPowerSynchronization(first, second)) {
            double firstBp = Math.max(1.0D, LivingWorldCompat.battlePower(first));
            double secondBp = Math.max(1.0D, LivingWorldCompat.battlePower(second));
            if (firstBp > secondBp) leaderStats *= secondBp / firstBp;
            else if (secondBp > firstBp) partnerStats *= firstBp / secondBp;
        }
        double ratio = Math.min(leaderStats, partnerStats) / Math.max(leaderStats, partnerStats);
        double multiplier = 1.25D + ratio * 0.75D;
        double fusedTotal = leaderStats + partnerStats * multiplier;
        double effective = fusedTotal / 1.2D;
        double bp = 1200.0D * Math.pow(Math.max(0.01D, effective / 100.0D), 1.2D);
        return (int)Math.max(1.0D, Math.min(Integer.MAX_VALUE - 1.0D, Math.round(bp)));
    }

    private static void positionDancePair(LivingEntity first, LivingEntity second,
                                          double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double sideX = -Math.cos(radians);
        double sideZ = -Math.sin(radians);
        double half = 1.15D;
        first.teleportTo(x + sideX * half, y, z + sideZ * half);
        second.teleportTo(x - sideX * half, y, z - sideZ * half);
        faceDance(first, yaw);
        faceDance(second, yaw);
    }

    private static void faceDance(LivingEntity fighter, float yaw) {
        fighter.setYRot(yaw);
        fighter.setYHeadRot(yaw);
        fighter.setXRot(0.0F);
        fighter.setDeltaMovement(Vec3.ZERO);
    }

    private static void freezeForDance(LivingEntity fighter) {
        fighter.setDeltaMovement(Vec3.ZERO);
        if (fighter instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
    }

    private static void hideOriginalOnFusion(LivingEntity original, LivingEntity fused) {
        original.stopRiding();
        original.setInvisible(true);
        if (LivingWorldCompat.isLivingWorldFighter(original)) LivingWorldCompat.setFighterName(original, "");
        original.setInvulnerable(true);
        original.setSilent(true);
        original.setDeltaMovement(Vec3.ZERO);
        if (original instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
        original.teleportTo(fused.getX(), fused.getY(), fused.getZ());
        original.startRiding(fused, true);
    }

    private static void finishFusion(MinecraftServer server, LivingEntity fused, boolean placeNearFused) {
        if (server == null || fused == null || !isTemporaryFused(fused)) return;
        CompoundTag root = fused.getPersistentData().getCompound(TEMP_ROOT);
        LivingEntity first = root.hasUUID("First") ? LWFusionManager.findPartner(server, root.getUUID("First")) : null;
        LivingEntity second = root.hasUUID("Second") ? LWFusionManager.findPartner(server, root.getUUID("Second")) : null;
        double x = fused.getX();
        double y = fused.getY();
        double z = fused.getZ();
        float yaw = fused.getYRot();
        ACTIVE_FUSED.remove(fused.getUUID());
        fused.getPersistentData().remove(TEMP_ROOT);
        fused.stopRiding();
        fused.ejectPassengers();
        if (first != null) LWFusionManager.restoreOrphanPartner(first);
        if (second != null) LWFusionManager.restoreOrphanPartner(second);
        if (placeNearFused) positionRestoredPair(first, second, x, y, z, yaw);
        fused.discard();
    }

    private static void restorePair(LivingEntity first, LivingEntity second, double x, double y, double z, float yaw) {
        if (first != null && hasNpcBackup(first)) LWFusionManager.restoreOrphanPartner(first);
        if (second != null && hasNpcBackup(second)) LWFusionManager.restoreOrphanPartner(second);
        positionRestoredPair(first, second, x, y, z, yaw);
    }

    private static void positionRestoredPair(LivingEntity first, LivingEntity second,
                                             double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double sideX = -Math.cos(radians);
        double sideZ = -Math.sin(radians);
        if (first != null) {
            first.teleportTo(x + sideX * 1.4D, y, z + sideZ * 1.4D);
            first.setDeltaMovement(Vec3.ZERO);
        }
        if (second != null) {
            second.teleportTo(x - sideX * 1.4D, y, z - sideZ * 1.4D);
            second.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void storeNpcBackup(LivingEntity fighter, UUID initiator, UUID other) {
        CompoundTag backup = new CompoundTag();
        backup.putBoolean("Active", true);
        backup.putBoolean("NpcFusion", true);
        backup.putUUID("Initiator", initiator);
        backup.putUUID("PairOther", other);
        backup.putBoolean("PartnerInvisible", fighter.isInvisible());
        backup.putBoolean("PartnerInvulnerable", fighter.isInvulnerable());
        backup.putBoolean("PartnerSilent", fighter.isSilent());
        if (LivingWorldCompat.isLivingWorldFighter(fighter)) {
            backup.putString("PartnerFighterName", LivingWorldCompat.fighterName(fighter));
        }
        if (fighter instanceof Mob mob) backup.putBoolean("PartnerNoAI", mob.isNoAi());
        fighter.getPersistentData().put(PARTNER_ROOT, backup);
    }

    private static void updateNpcBackupFused(LivingEntity fighter, UUID fused) {
        if (fighter == null || !fighter.getPersistentData().contains(PARTNER_ROOT)) return;
        CompoundTag backup = fighter.getPersistentData().getCompound(PARTNER_ROOT);
        backup.putUUID("Fused", fused);
        fighter.getPersistentData().put(PARTNER_ROOT, backup);
    }

    /** True while this NPC is the hidden passenger inside an active player/NPC fusion.
     *  Central LW selectors use this instead of accidentally treating the invisible passenger
     *  as the nearest ordinary fighter. */
    public static boolean isHiddenFusionPartner(net.minecraft.world.entity.Entity entity) {
        if (entity == null || !entity.getPersistentData().contains(PARTNER_ROOT)) return false;
        CompoundTag root = entity.getPersistentData().getCompound(PARTNER_ROOT);
        return root.getBoolean("Active") && root.getBoolean("NpcFusion");
    }

    private static boolean hasNpcBackup(LivingEntity fighter) {
        if (fighter == null || !fighter.getPersistentData().contains(PARTNER_ROOT)) return false;
        CompoundTag root = fighter.getPersistentData().getCompound(PARTNER_ROOT);
        return root.getBoolean("Active") && root.getBoolean("NpcFusion");
    }

    private static CompoundTag debugRoot(ServerPlayer player) {
        return player.getPersistentData().getCompound(DEBUG_ROOT);
    }

    private static void fail(ServerPlayer player, String message) {
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("[LW Fusion] " + message)
                .withStyle(ChatFormatting.RED), false);
    }

    private static int parseHex(String value, int fallback) {
        try {
            String s = value == null ? "" : value.trim();
            if (s.startsWith("#")) s = s.substring(1);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static final class PendingDance {
        final UUID initiator;
        final UUID first;
        final UUID second;
        int ticksLeft;
        final double midX;
        final double midY;
        final double midZ;
        final float yaw;

        PendingDance(UUID initiator, UUID first, UUID second, int ticksLeft,
                     double midX, double midY, double midZ, float yaw) {
            this.initiator = initiator;
            this.first = first;
            this.second = second;
            this.ticksLeft = ticksLeft;
            this.midX = midX;
            this.midY = midY;
            this.midZ = midZ;
            this.yaw = yaw;
        }
    }
}
