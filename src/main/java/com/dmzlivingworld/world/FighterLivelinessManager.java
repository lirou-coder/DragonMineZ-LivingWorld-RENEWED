package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Presentation-only consciousness layer. It never owns combat, routine, travel, meditation or a
 * real social session. R22 makes attention persist long enough to be visible and gives ordinary
 * idle time a local anchor so "Wandering" reads as occupying a place, not indecisive zig-zagging.
 */
public final class FighterLivelinessManager {
    private static final String ROOT = "LWLivelinessV1";
    private static final String NEXT_REACTION = "NextReaction";
    private static final String DOWNTIME_UNTIL = "DowntimeUntil";
    private static final String POST_ACTIVITY_UNTIL = "PostActivityUntil";
    private static final String FAVORITES = "FavoriteSpots";
    private static final String LAST_GREETING = "LastGreeting";
    private static final String ATTENTION_ID = "AttentionId";
    private static final String ATTENTION_UNTIL = "AttentionUntil";
    private static final String ATTENTION_REASON = "AttentionReason";
    private static final String IDLE_ANCHOR = "IdleAnchor";
    private static final String IDLE_ANCHOR_UNTIL = "IdleAnchorUntil";

    private FighterLivelinessManager() {}

    private static CompoundTag data(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.contains(ROOT)) legacy.put(ROOT, new CompoundTag());
        return legacy.getCompound(ROOT);
    }

    /** Never blocks authoritative systems. Attention survives for a few seconds instead of one tick. */
    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)
                || WorldMenaceManager.isWorldMenace(fighter) || !fighter.isAlive() || fighter.isDefeated()
                || fighter.isCaptive() || fighter.getTarget() != null || fighter.isSanctionedMatchParticipant()
                || FighterAmbientActivityManager.isActive(fighter) || fighter.isMeditating() || fighter.isPreparingMeditation()
                || fighter.isSocialLifeActivity() || fighter.isSocialPlayerApproach() || fighter.isSocialPowerDisplay()
                || LivingBondManager.isTravellingCompanion(fighter)) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;

        long now = level.getGameTime();
        CompoundTag d = data(fighter);
        if (tickAttention(fighter, level, d, now)) return;

        if (now < d.getLong(POST_ACTIVITY_UNTIL) || now < d.getLong(DOWNTIME_UNTIL)) {
            lookAtInterestingNearby(fighter, level, 12.0D);
            return;
        }
        if (now < d.getLong(NEXT_REACTION)) return;
        d.putLong(NEXT_REACTION, now + 35L + fighter.getRandom().nextInt(66));

        Interest interest = chooseInterest(fighter, level);
        if (interest != null) {
            long duration = switch (interest.reason) {
                case "FIGHT" -> 70L + fighter.getRandom().nextInt(71);      // 3.5-7 s
                case "RIVAL" -> 50L + fighter.getRandom().nextInt(51);      // 2.5-5 s
                case "FRIEND" -> 38L + fighter.getRandom().nextInt(43);
                default -> 24L + fighter.getRandom().nextInt(33);
            };
            beginAttention(fighter, interest.entity, interest.reason, now + duration);
            if ("FIGHT".equals(interest.reason)) fighter.getNavigation().stop();
            maybeAcknowledge(fighter, interest.entity, interest.reason, now);
            return;
        }

        // Genuine nothing. The idle wanderer sees this via isHoldingIdle() and stays in the area.
        if (fighter.getRandom().nextFloat() < downtimeChance(fighter)) {
            fighter.getNavigation().stop();
            d.putLong(DOWNTIME_UNTIL, now + 80L + fighter.getRandom().nextInt(161));
        }
    }

    private record Interest(Entity entity, String reason) {}

    private static Interest chooseInterest(AmbientFighterEntity fighter, ServerLevel level) {
        List<AmbientFighterEntity> fighting = level.getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(20.0D, 10.0D, 20.0D), other -> other != fighter && other.isAlive()
                        && !other.isDefeated() && other.getTarget() != null);
        AmbientFighterEntity combatant = fighting.stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (combatant != null && fighter.getRandom().nextFloat() < 0.72F) return new Interest(combatant, "FIGHT");

        List<AmbientFighterEntity> people = level.getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(11.0D), other -> other != fighter && other.isAlive()
                        && !other.isDefeated() && !WorldMenaceManager.isWorldMenace(other));
        AmbientFighterEntity known = people.stream().filter(other -> FighterNpcSocialManager.bond(fighter, other) >= 4
                        || sameFaction(fighter, other) || other.getFighterName().equals(fighter.getRivalName())
                        || FactionManager.areRivals(fighter, other))
                .min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (known != null) {
            boolean rival = known.getFighterName().equals(fighter.getRivalName()) || FactionManager.areRivals(fighter, known);
            return new Interest(known, rival ? "RIVAL" : sameFaction(fighter, known) ? "FACTION" : "FRIEND");
        }

        ServerPlayer player = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()
                        && p.distanceToSqr(fighter) <= 10.0D * 10.0D)
                .min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        return player == null ? null : new Interest(player, "PLAYER");
    }


    private static boolean sameFaction(AmbientFighterEntity a, AmbientFighterEntity b) {
        return a != null && b != null && a.isFactionMember() && b.isFactionMember()
                && a.getFactionId() != null && a.getFactionId().equals(b.getFactionId());
    }

    private static void beginAttention(AmbientFighterEntity fighter, Entity entity, String reason, long until) {
        if (fighter == null || entity == null) return;
        CompoundTag d = data(fighter);
        d.putUUID(ATTENTION_ID, entity.getUUID());
        d.putLong(ATTENTION_UNTIL, until);
        d.putString(ATTENTION_REASON, reason == null ? "LOOK" : reason);
        fighter.getLookControl().setLookAt(entity, 28.0F, 24.0F);
    }

    private static boolean tickAttention(AmbientFighterEntity fighter, ServerLevel level, CompoundTag d, long now) {
        if (!d.hasUUID(ATTENTION_ID) || now >= d.getLong(ATTENTION_UNTIL)) {
            d.remove(ATTENTION_ID); d.remove(ATTENTION_UNTIL); d.remove(ATTENTION_REASON);
            return false;
        }
        Entity target = level.getEntity(d.getUUID(ATTENTION_ID));
        if (target == null || !target.isAlive() || fighter.distanceToSqr(target) > 26.0D * 26.0D) {
            d.remove(ATTENTION_ID); d.remove(ATTENTION_UNTIL); d.remove(ATTENTION_REASON);
            return false;
        }
        String reason = d.getString(ATTENTION_REASON);
        float yaw = "FIGHT".equals(reason) || "RIVAL".equals(reason) ? 34.0F : 24.0F;
        fighter.getLookControl().setLookAt(target, yaw, 22.0F);
        if ("FIGHT".equals(reason)) fighter.getNavigation().stop();
        return true;
    }

    private static void lookAtInterestingNearby(AmbientFighterEntity fighter, ServerLevel level, double range) {
        Entity e = level.getEntities(fighter, fighter.getBoundingBox().inflate(range), other -> other.isAlive())
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (e != null) fighter.getLookControl().setLookAt(e, 22.0F, 18.0F);
    }

    private static void maybeAcknowledge(AmbientFighterEntity fighter, Entity entity, String reason, long now) {
        CompoundTag d = data(fighter);
        if (now - d.getLong(LAST_GREETING) < 900L || !fighter.getSpeech().isEmpty()) return;
        if (entity instanceof AmbientFighterEntity other) {
            int bond = FighterNpcSocialManager.bond(fighter, other);
            boolean rival = "RIVAL".equals(reason);
            if (rival && fighter.getRandom().nextFloat() < 0.14F) {
                fighter.speak(fighter.getRandom().nextBoolean() ? "Still training, I see." : "Don't fall behind.", 38);
                d.putLong(LAST_GREETING, now);
            } else if (bond >= 6 && fighter.getRandom().nextFloat() < 0.10F) {
                fighter.speak(fighter.getRandom().nextBoolean() ? "Hey." : "Good to see you.", 34);
                d.putLong(LAST_GREETING, now);
            } else if ("FACTION".equals(reason) && fighter.getRandom().nextFloat() < 0.055F) {
                fighter.speak(fighter.getRandom().nextBoolean() ? "Hey." : "All good?", 30);
                d.putLong(LAST_GREETING, now);
            }
        } else if (entity instanceof ServerPlayer player) {
            int relationship = FighterRelationshipManager.relationshipOrUnknown(player, fighter);
            if (relationship >= 35 && relationship <= 100 && fighter.getRandom().nextFloat() < 0.075F) {
                fighter.speak(fighter.getRandom().nextBoolean() ? "Hey, you're back." : "Good timing.", 34);
                d.putLong(LAST_GREETING, now);
            }
        }
    }

    private static float downtimeChance(AmbientFighterEntity fighter) {
        FighterPersonality p = fighter.getPersonality();
        if (p == FighterPersonality.CALM) return 0.19F;
        if (p == FighterPersonality.CAUTIOUS) return 0.13F;
        if (p == FighterPersonality.PROUD) return 0.10F;
        return 0.115F;
    }

    /** Adds a physical ending and makes a successful place the short-term local idle anchor. */
    public static void onActivityFinished(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type,
                                          BlockPos successfulSpot, long settledTicks) {
        if (fighter == null || type == null || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag d = data(fighter);
        long now = fighter.level().getGameTime();
        if (settledTicks >= 100L) {
            d.putLong(POST_ACTIVITY_UNTIL, now + 30L + fighter.getRandom().nextInt(51));
            rememberFavorite(fighter, type, successfulSpot);
            if (successfulSpot != null) {
                d.putLong(IDLE_ANCHOR, successfulSpot.asLong());
                d.putLong(IDLE_ANCHOR_UNTIL, now + 2400L + fighter.getRandom().nextInt(2401));
            }
        }
    }

    public static boolean isHoldingIdle(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return false;
        long now = fighter.level().getGameTime();
        CompoundTag d = data(fighter);
        boolean watching = d.hasUUID(ATTENTION_ID) && now < d.getLong(ATTENTION_UNTIL)
                && "FIGHT".equals(d.getString(ATTENTION_REASON));
        return watching || now < d.getLong(DOWNTIME_UNTIL) || now < d.getLong(POST_ACTIVITY_UNTIL);
    }

    /** Local center for ordinary idle movement; expires naturally so long-term travel is unaffected. */
    public static BlockPos idleAnchor(AmbientFighterEntity fighter) {
        if (fighter == null || !(fighter.level() instanceof ServerLevel level)) return null;
        CompoundTag d = data(fighter);
        long now = level.getGameTime();
        if (!d.contains(IDLE_ANCHOR) || now >= d.getLong(IDLE_ANCHOR_UNTIL)) return null;
        BlockPos anchor = BlockPos.of(d.getLong(IDLE_ANCHOR));
        if (anchor.distSqr(fighter.blockPosition()) > 48.0D * 48.0D || !level.hasChunkAt(anchor)) return null;
        return anchor;
    }

    private static void rememberFavorite(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type, BlockPos spot) {
        if (spot == null || !(type == FighterAmbientActivityManager.Type.TRAINING
                || type == FighterAmbientActivityManager.Type.KI_TRAINING
                || type == FighterAmbientActivityManager.Type.STUDYING
                || type == FighterAmbientActivityManager.Type.REST
                || type == FighterAmbientActivityManager.Type.STARGAZING)) return;
        CompoundTag d = data(fighter);
        CompoundTag favorites = d.contains(FAVORITES) ? d.getCompound(FAVORITES) : new CompoundTag();
        favorites.putLong(type.name(), spot.asLong());
        d.put(FAVORITES, favorites);
    }

    /** Returns a remembered successful place only when it is still local and can be safely re-resolved. */
    public static BlockPos favoriteSpot(AmbientFighterEntity fighter, FighterAmbientActivityManager.Type type) {
        if (fighter == null || type == null || !(fighter.level() instanceof ServerLevel level)) return null;
        CompoundTag d = data(fighter);
        if (!d.contains(FAVORITES)) return null;
        CompoundTag favorites = d.getCompound(FAVORITES);
        if (!favorites.contains(type.name())) return null;
        BlockPos remembered = BlockPos.of(favorites.getLong(type.name()));
        if (remembered.distSqr(fighter.blockPosition()) > 64.0D * 64.0D || !level.hasChunkAt(remembered)) return null;
        return AmbientFighterSpawner.findSafeGroundAround(level, remembered, fighter.getRandom(), 0, 3, 8);
    }
}
