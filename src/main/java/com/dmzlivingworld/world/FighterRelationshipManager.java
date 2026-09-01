package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

/**
 * Player/fighter relationship language shared by behavior, profile UI and world nametags.
 *
 * The underlying save value remains FighterMemoryManager's -100..100 Relationship field.
 * This class deliberately does not create a second friendship currency. It gives that one
 * value stable stages, player-specific disposition, and a small personality-aware response
 * to meaningful bond events so two fighters do not build trust in exactly the same way.
 */
public final class FighterRelationshipManager {
    private FighterRelationshipManager() {}

    public enum BondEvent {
        ENCOUNTER,
        RESCUE,
        DEFEAT,
        SPAR,
        PROTECTION,
        TRAVEL,
        MEDITATION,
        GIFT,
        TRAINING,
        CONVERSATION,
        GENERIC
    }

    /** How this fighter is likely to approach this exact player right now. */
    public enum Disposition {
        ALLY(0, "Trusted", "★", 0xFFFFE27A),
        FRIENDLY(1, "Friendly", "+", 0xFF72F28B),
        NEUTRAL(2, "Neutral", "=", 0xFFAAB4BE),
        WARY(3, "Wary", "!", 0xFFFFC15E),
        HOSTILE(4, "Hostile", "⚔", 0xFFFF6868);

        private final int id;
        private final String label;
        private final String icon;
        private final int color;

        Disposition(int id, String label, String icon, int color) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.color = color;
        }

        public int id() { return id; }
        public String label() { return label; }
        public String icon() { return icon; }
        /** Framed world badge stays readable even when color perception or scene lighting is poor. */
        public String worldBadge() { return "[" + icon + "]"; }
        public int color() { return color; }

        public static Disposition byId(int id) {
            for (Disposition value : values()) if (value.id == id) return value;
            return NEUTRAL;
        }
    }

    /** A deterministic social tendency; it is functional, not a second personality stat. */
    public enum SocialStyle {
        PROTECTIVE("Protective", "loyalty and protecting people", BondEvent.PROTECTION, BondEvent.RESCUE),
        LOYAL("Loyal", "shared battles and travelling together", BondEvent.TRAVEL, BondEvent.PROTECTION),
        DISCIPLINED("Disciplined", "training and quiet focus", BondEvent.TRAINING, BondEvent.MEDITATION),
        RESPECT_DRIVEN("Respect-driven", "strength, disciplined training and earned respect", BondEvent.TRAINING, BondEvent.DEFEAT),
        COMPETITIVE("Competitive", "proving yourself and shared battles", BondEvent.DEFEAT, BondEvent.TRAINING),
        OPEN("Open", "time together and thoughtful gifts", BondEvent.TRAVEL, BondEvent.GIFT),
        GUARDED("Guarded", "consistency and acts of protection", BondEvent.RESCUE, BondEvent.PROTECTION),
        PRAGMATIC("Pragmatic", "useful help and capable allies", BondEvent.GIFT, BondEvent.PROTECTION);

        private final String label;
        private final String connection;
        private final BondEvent primary;
        private final BondEvent secondary;

        SocialStyle(String label, String connection, BondEvent primary, BondEvent secondary) {
            this.label = label;
            this.connection = connection;
            this.primary = primary;
            this.secondary = secondary;
        }

        public String label() { return label; }
        public String connection() { return connection; }
        public boolean values(BondEvent event) { return event == primary || event == secondary; }
    }

    public static SocialStyle socialStyle(AmbientFighterEntity fighter) {
        if (fighter == null) return SocialStyle.OPEN;
        // Persist one tiny identity seed inside the fighter's existing Legacy compound. Recurring
        // fighters are physically re-instantiated and may receive a new entity UUID, so deriving
        // social nature directly from the live UUID would make the same person change personality.
        long seed;
        if (fighter.getLegacyData().contains("SocialSeed")) {
            seed = fighter.getLegacyData().getLong("SocialSeed");
        } else {
            UUID identityId = fighter.getMemoryRecordId() != null ? fighter.getMemoryRecordId() : fighter.getUUID();
            seed = identityId.getMostSignificantBits()
                    ^ Long.rotateLeft(identityId.getLeastSignificantBits(), 23);
            if (!fighter.level().isClientSide) fighter.getLegacyData().putLong("SocialSeed", seed);
        }
        int variant = Math.floorMod((int)(seed ^ (seed >>> 32)), 2);
        return switch (fighter.getPersonality()) {
            case HEROIC -> variant == 0 ? SocialStyle.PROTECTIVE : SocialStyle.LOYAL;
            case CALM -> variant == 0 ? SocialStyle.DISCIPLINED : SocialStyle.OPEN;
            case PROUD -> variant == 0 ? SocialStyle.RESPECT_DRIVEN : SocialStyle.COMPETITIVE;
            case AGGRESSIVE -> variant == 0 ? SocialStyle.COMPETITIVE : SocialStyle.PRAGMATIC;
            case CAUTIOUS -> variant == 0 ? SocialStyle.GUARDED : SocialStyle.LOYAL;
        };
    }

    /**
     * Keeps the old balance recognizable: personality normally changes a positive event by
     * only one point. Large story events such as rescues therefore stay large and meaningful.
     */
    public static int adjustedDelta(AmbientFighterEntity fighter, int baseDelta, BondEvent event) {
        if (fighter == null || baseDelta == 0) return baseDelta;
        if (WorldMenaceManager.isWorldMenace(fighter)) return 0; // anomaly evidence never becomes friendship/hostility currency
        if (baseDelta < 0) return baseDelta; // hostility/history should never be softened by a social label.

        SocialStyle style = socialStyle(fighter);
        int bonus = style.values(event) ? 1 : 0;

        // A guarded fighter warms more slowly to generic social contact, while a heroic one
        // responds especially well to rescue/protection. These are deliberately small nudges.
        if (style == SocialStyle.GUARDED && (event == BondEvent.GENERIC || event == BondEvent.GIFT)) bonus--;
        // Conversation is deliberately an early-bond tool rather than a friendship vending machine.
        // Open people warm faster; guarded/aggressive people usually need shared experiences instead.
        if (event == BondEvent.CONVERSATION) {
            if (style == SocialStyle.OPEN) bonus++;
            if (style == SocialStyle.GUARDED) bonus--;
            if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE) bonus--;
        }
        if (fighter.getPersonality() == FighterPersonality.HEROIC
                && (event == BondEvent.PROTECTION || event == BondEvent.RESCUE)) bonus++;
        if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE && event == BondEvent.MEDITATION) bonus--;

        return Math.max(0, baseDelta + Math.max(-1, Math.min(2, bonus)));
    }

    public static String relationshipStage(int relationship) {
        int rel = clamp(relationship, -100, 100);
        if (rel <= -70) return "Nemesis";
        if (rel <= -35) return "Enemy";
        if (rel <= -15) return "Tense";
        if (rel < 15) return "Neutral";
        if (rel < 35) return "Familiar";
        if (rel < 60) return "Friend";
        if (rel < 85) return "Close Friend";
        return "Trusted Ally";
    }

    /** Next positive stage; empty when already at the top. */
    public static String nextPositiveStage(int relationship) {
        int rel = clamp(relationship, -100, 100);
        if (rel < 15) return "Familiar";
        if (rel < 35) return "Friend";
        if (rel < 60) return "Close Friend";
        if (rel < 85) return "Trusted Ally";
        return "";
    }

    public static int nextPositiveThreshold(int relationship) {
        int rel = clamp(relationship, -100, 100);
        if (rel < 15) return 15;
        if (rel < 35) return 35;
        if (rel < 60) return 60;
        if (rel < 85) return 85;
        return 100;
    }

    public static Disposition disposition(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return Disposition.NEUTRAL;
        if (WorldMenaceManager.isWorldMenace(fighter)) return fighter.getTarget() == player ? Disposition.HOSTILE : Disposition.WARY;
        if (fighter.getTarget() == player || fighter.getStoryRole() == AmbientFighterEntity.STORY_ENEMY) return Disposition.HOSTILE;

        UUID companion = LivingBondManager.companionId(player);
        if (companion != null && companion.equals(fighter.getUUID())) return Disposition.ALLY;
        if (fighter.getStoryRole() == AmbientFighterEntity.STORY_ALLY) return Disposition.ALLY;

        int playerAlignment = PlayerAlignmentBridge.alignment(player);
        // DMZ moral alignment changes the NPC's first-principles reading of the player.
        // Explicit story roles, an active attack and world menaces above remain authoritative.
        if (fighter.getAlignment() == FighterAlignment.GOOD) {
            if (playerAlignment <= 32) return Disposition.HOSTILE;
            if (playerAlignment <= 66) return Disposition.NEUTRAL;
        } else if (fighter.getAlignment() == FighterAlignment.BAD && playerAlignment <= 32) {
            return Disposition.NEUTRAL;
        }

        if (fighter.isRememberedFor(player)) {
            int rel = fighter.getMemoryRelationship();
            if (rel <= -35) return PlayerWorldManager.shouldFearPlayer(fighter, player) ? Disposition.WARY : Disposition.HOSTILE;
            if (rel <= -15) return Disposition.WARY;
            if (rel >= 85) return Disposition.ALLY;
            if (rel >= 35) return Disposition.FRIENDLY;
        }

        if (fighter.isFactionMember()) {
            int rep = FactionManager.getReputation(player, fighter.getFactionId());
            if (rep <= FactionManager.HOSTILE_REP) {
                return PlayerWorldManager.shouldFearPlayer(fighter, player) ? Disposition.WARY : Disposition.HOSTILE;
            }
            if (rep >= FactionManager.FRIENDLY_REP) return Disposition.FRIENDLY;
        }

        // For affiliated fighters, faction policy already decided whether they are hostile.
        // An unaffiliated BAD fighter's native LW behavior proactively targets players, so the
        // world indicator must say HOSTILE before the first punch instead of merely implying evil.
        if (fighter.getAlignment() == FighterAlignment.BAD) {
            return fighter.isFactionMember() ? Disposition.WARY : Disposition.HOSTILE;
        }
        // Baseline alignment must remain readable before a personal bond exists.
        // GOOD fighters are peaceful/protective toward unfamiliar non-hostile players,
        // while NEUTRAL fighters are genuinely neutral. Personal history and faction
        // reputation above still override this baseline whenever appropriate.
        if (fighter.getAlignment() == FighterAlignment.GOOD) {
            return Disposition.FRIENDLY;
        }
        return Disposition.NEUTRAL;
    }

    public static String attitudeReason(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return "No personal history yet.";
        if (WorldMenaceManager.isWorldMenace(fighter)) return "No personal bond exists; only sightings are recorded.";
        Disposition disposition = disposition(player, fighter);
        if (fighter.getTarget() == player) return "Currently engaged with you.";
        if (fighter.isRememberedFor(player)) {
            int rel = fighter.getMemoryRelationship();
            String stage = relationshipStage(rel);
            if (rel >= 35 || rel <= -15) return stage + " from your shared history.";
            return "Remembers you.";
        }
        if (fighter.isFactionMember()) {
            int rep = FactionManager.getReputation(player, fighter.getFactionId());
            if (rep <= FactionManager.HOSTILE_REP || rep >= FactionManager.FRIENDLY_REP)
                return "Faction standing: " + FactionManager.reputationLabel(rep).toLowerCase(Locale.ROOT) + ".";
        }
        if (disposition == Disposition.HOSTILE && fighter.getAlignment() == FighterAlignment.BAD && !fighter.isFactionMember()) {
            return "Openly aggressive.";
        }
        if (disposition == Disposition.WARY) return "Keeping their distance.";
        if (disposition == Disposition.FRIENDLY) return "Friendly first impression.";
        return "No shared history yet.";
    }

    public static int relationshipOrUnknown(ServerPlayer player, AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isWorldMenace(fighter)) return 101;
        return fighter != null && player != null && fighter.isRememberedFor(player)
                ? clamp(fighter.getMemoryRelationship(), -100, 100) : 101;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
