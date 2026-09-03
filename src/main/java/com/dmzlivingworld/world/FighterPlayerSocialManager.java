package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sparse, grounded NPC-initiated social moments. A peaceful/friendly fighter may physically
 * walk over and comment on something that is actually true in the current world: a recent
 * incident, saga-derived world progression, the player's shared history, or the fighter's hobby.
 * These moments deliberately grant no relationship points; they make existing bonds feel alive
 * without becoming a passive friendship farm.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterPlayerSocialManager {
    private static final String NEXT = "LWNextPlayerSocial";
    private static final String NEXT_REMARK = "LWNextPlayerRemark";
    private static final Map<UUID, Approach> ACTIVE = new HashMap<>();

    private static final class Approach {
        final UUID playerId;
        final UUID fighterId;
        final long expires;
        Approach(UUID playerId, UUID fighterId, long expires) {
            this.playerId = playerId; this.fighterId = fighterId; this.expires = expires;
        }
    }

    private FighterPlayerSocialManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 10L != 0L) return;

        for (Approach approach : List.copyOf(ACTIVE.values())) tickApproach(server, approach, now);
        // A config toggle controls new social approaches, never abandons someone midway.
        if (!LivingWorldConfig.npcSocializing()) return;
        if (now % 100L != 0L) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            maybeStart(player, now);
            maybeRemark(player, now);
        }
    }

    private static void maybeStart(ServerPlayer player, long now) {
        if (LivingWorldConfig.npcChatFrequencyScale() <= 0.0D) return;
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)
                || ACTIVE.containsKey(player.getUUID()) || now < player.getPersistentData().getLong(NEXT)) return;

        // Check infrequently. Close friends are more likely to initiate, but strangers with a GOOD
        // first impression can occasionally say something too.
        List<AmbientFighterEntity> candidates = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(22.0D), f -> available(player, f));
        if (candidates.isEmpty()) {
            player.getPersistentData().putLong(NEXT, now + LivingWorldConfig.scaledNpcChatDelay(1200L + player.getRandom().nextInt(1201)));
            return;
        }
        candidates.sort((a, b) -> Integer.compare(score(player, b), score(player, a)));
        AmbientFighterEntity fighter = candidates.get(0);
        int score = score(player, fighter);
        float chance = score >= 5 ? 0.55F : score >= 3 ? 0.32F : 0.14F;
        chance *= ReactiveWorldManager.socialDrive(fighter);
        chance = LivingWorldConfig.scaledNpcChatChance(chance);
        player.getPersistentData().putLong(NEXT, now + LivingWorldConfig.scaledNpcChatDelay(3600L + player.getRandom().nextInt(5401)));
        if (player.getRandom().nextFloat() > Math.min(0.90F, chance)) return;

        fighter.setSocialPlayerApproach(true);
        fighter.getNavigation().moveTo(player, 1.02D * ReactiveWorldManager.movementPace(fighter));
        ACTIVE.put(player.getUUID(), new Approach(player.getUUID(), fighter.getUUID(), now + 420L));
    }

    private static void maybeRemark(ServerPlayer player, long now) {
        if (LivingWorldConfig.npcChatFrequencyScale() <= 0.0D) return;
        if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)
                || now < player.getPersistentData().getLong(NEXT_REMARK)) return;
        player.getPersistentData().putLong(NEXT_REMARK, now + LivingWorldConfig.scaledNpcChatDelay(3200L + player.getRandom().nextInt(6401)));
        if (player.getRandom().nextFloat() > LivingWorldConfig.scaledNpcChatChance(0.34F)) return;
        List<AmbientFighterEntity> nearby = level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(16.0D),
                f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f) && !f.isDefeated() && !f.isCaptive() && !f.isMeditating() && !f.isTransforming()
                        && f.getTarget() == null && !f.isSocialLifeActivity() && !f.isSocialPlayerApproach()
                        && !WorldMenaceManager.isWorldMenace(f));
        if (nearby.isEmpty()) return;
        AmbientFighterEntity fighter = nearby.get(player.getRandom().nextInt(nearby.size()));
        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
        String line;
        if (fighter.getAlignment() == FighterAlignment.GOOD || disposition == FighterRelationshipManager.Disposition.FRIENDLY) {
            line = pick(fighter, "Hey. Good to see you.", "You doing all right?", "There you are. Been keeping busy?",
                    "Nice running into you again.", "Hope things are going okay on your side.", "You look like you've had a long day.");
        } else if (fighter.getAlignment() == FighterAlignment.BAD || disposition == FighterRelationshipManager.Disposition.HOSTILE) {
            line = pick(fighter, "Keep moving.", "Don't mistake quiet for friendly.", "You again.",
                    "Try not to get in my way.", "I noticed you. That's enough.", "We're not friends just because I'm not fighting you.");
        } else {
            line = pick(fighter, "Hm. We keep crossing paths.", "You headed somewhere?", "Busy day out here.",
                    "Looks like you're doing your own thing too.", "Careful out there.", "You notice how strange this place gets sometimes?");
        }
        fighter.getLookControl().setLookAt(player, 24.0F, 20.0F);
        fighter.speak(line, 92);
    }

    private static void tickApproach(MinecraftServer server, Approach approach, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(approach.playerId);
        AmbientFighterEntity fighter = find(server, approach.fighterId);
        if (player == null || fighter == null || !fighter.isAlive() || now > approach.expires
                || player.level() != fighter.level() || fighter.hurtTime > 0 || fighter.isDefeated()
                || fighter.isCaptive() || fighter.getTarget() != null || player.distanceToSqr(fighter) > 30.0D * 30.0D) {
            finish(approach, fighter);
            return;
        }
        fighter.setTarget(null);
        fighter.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (player.distanceToSqr(fighter) > 4.2D * 4.2D) {
            if (now % 20L == 0L || fighter.getNavigation().isDone()) fighter.getNavigation().moveTo(player, 1.02D * ReactiveWorldManager.movementPace(fighter));
            return;
        }
        fighter.getNavigation().stop();
        fighter.speak(contextLine(player, fighter), 116);
        FighterMemoryManager.refreshLoadedProfile(fighter);
        finish(approach, fighter);
    }

    private static boolean available(ServerPlayer player, AmbientFighterEntity fighter) {
        if (fighter == null || com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(fighter) || !fighter.isAlive() || fighter.isCaptive() || fighter.isDefeated()
                || fighter.isRecovering() || fighter.isMeditating() || fighter.isTransforming()
                || fighter.isKaiokenActive() || fighter.getTarget() != null || fighter.isSocialLifeActivity()
                || fighter.isSocialPlayerApproach() || fighter.isSanctionedMatchParticipant()
                || LivingBondManager.isTravellingCompanion(fighter)) return false;
        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
        if (disposition == FighterRelationshipManager.Disposition.HOSTILE || disposition == FighterRelationshipManager.Disposition.WARY) return false;
        if (fighter.getAlignment() == FighterAlignment.BAD) return false;
        int rel = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        if (ReactiveWorldManager.moodStrength(fighter) >= 60) {
            switch (ReactiveWorldManager.mood(fighter)) {
                case IRRITATED -> { if (rel < 60) return false; }
                case SOMBER -> { if (rel < 35) return false; }
                case WEARY, FOCUSED -> { if (rel < 15) return false; }
                default -> { }
            }
        }
        return true;
    }

    private static int score(ServerPlayer player, AmbientFighterEntity fighter) {
        int score = fighter.getAlignment() == FighterAlignment.GOOD ? 1 : 0;
        if (fighter.isRememberedFor(player)) {
            int rel = fighter.getMemoryRelationship();
            if (rel >= 85) score += 6;
            else if (rel >= 60) score += 5;
            else if (rel >= 35) score += 4;
            else if (rel >= 15) score += 2;
        }
        if (LivingBondManager.companionId(player) != null && LivingBondManager.companionId(player).equals(fighter.getUUID())) score += 4;
        return score;
    }

    private static String contextLine(ServerPlayer player, AmbientFighterEntity fighter) {
        if (ReactiveWorldManager.moodStrength(fighter) >= 60) {
            String cause = ReactiveWorldManager.moodCause(fighter);
            return switch (ReactiveWorldManager.mood(fighter)) {
                case UPBEAT -> "Good timing. I'm actually feeling pretty good right now.";
                case CONTENT -> "Things are calm for once. I'm trying not to ruin that.";
                case FOCUSED -> "I'm keeping my head on straight. " + cause + " has my attention.";
                case WARY -> "Keep your eyes open. " + cause + " has me on edge.";
                case IRRITATED -> "I'm not great company right now. " + cause + " has been getting on my nerves.";
                case SOMBER -> "I'm a little quiet right now. " + cause + " is still on my mind.";
                case WEARY -> "I'm worn out. " + cause + " took more out of me than I expected.";
            };
        }

        // Scientists still preserve strong mood/context reactions first. Outside those moments,
        // their ordinary player-facing small talk is much more technical and research-minded.
        if (FighterScientistManager.isScientist(fighter) && fighter.getRandom().nextFloat() < 0.68F) {
            int seeds = FighterScientistManager.availableSeeds(fighter);
            return pick(fighter,
                    "Hold still a second. Your Ki reading is a useful reference point... no, I'm not harvesting anything.",
                    "I have " + seeds + " viable specimen" + (seeds == 1 ? "" : "s") + " in reserve. Real combat data is the limiting reagent now.",
                    "Do you know how hard it is to keep one biological variable constant around people who transform mid-fight?",
                    "I've been recalibrating the cultivation model. Temporary power spikes make terrible baseline data.",
                    "If a specimen dies, I want the cause, not a dramatic speech. Failure mode, attacker, elapsed time, tissue response.",
                    "The formula isn't 'grow Saibaman, hope for the best.' At least, it's not supposed to be.");
        }

        // Extremely rare idle wish thought. It is intentionally far rarer than normal small talk
        // so Dragon Balls remain a surprising character glimpse instead of a recurring topic.
        if (fighter.getRandom().nextInt(1800) == 0) {
            return switch (fighter.getAlignment()) {
                case GOOD -> pick(fighter, "If I ever found all seven Dragon Balls... I'd probably use the wish for someone who couldn't help themselves.",
                        "Seven Dragon Balls, one wish. I'd be terrified of wasting it on myself.",
                        "Sometimes I wonder who I'd bring back if Shenron actually stood in front of me.");
                case BAD -> pick(fighter, "If I had the Dragon Balls? Heh. Better if I keep that wish to myself.",
                        "One wish from Shenron could change a lot. Maybe too much.",
                        "I'd know exactly what to ask the Dragon Balls for. You wouldn't like it.");
                case NEUTRAL -> pick(fighter, "I wonder what I'd actually wish for if I found all seven Dragon Balls.",
                        "Seven Dragon Balls sounds simple until you have to choose one wish.",
                        "I'd probably spend so long deciding on a Dragon Ball wish that Shenron would get annoyed.");
            };
        }

        // A deliberately rare Bulgaria easter egg. Bulgarian-folklore hobby makes it a little
        // more likely, but it is never the default topic.
        boolean bulgarianHobby = FighterHobby.of(fighter) == FighterHobby.BULGARIAN_FOLKLORE;
        int easterRoll = fighter.getRandom().nextInt(bulgarianHobby ? 18 : 140);
        if (easterRoll == 0) {
            return fighter.getPersonality() == FighterPersonality.PROUD
                    ? "I heard the mountains in Bulgaria are good for training. RAHHH. I'd test that."
                    : "Ever heard of Bulgaria? Mountains, good food, stubborn people. RAHHH.";
        }

        // A couple of much rarer Minecraft-world jokes. They stay sparse so ordinary dialogue
        // remains about the actual fighter/world instead of becoming a meme feed.
        int oddWorldRoll = fighter.getRandom().nextInt(260);
        if (oddWorldRoll == 0)
            return "Someone told me they train by punching trees with their bare hands. I'm not testing that.";
        if (oddWorldRoll == 1)
            return "A green thing hissed at me near a cave. I call leaving immediately tactical awareness.";

        if (fighter.isRememberedFor(player) && fighter.getMemoryRelationship() >= 60 && fighter.getRandom().nextFloat() < 0.30F) {
            return switch (fighter.getPersonality()) {
                case PROUD -> "You holding up all right? Don't make me start worrying about you.";
                case HEROIC -> "Hey. How've you been? You doing okay?";
                case CALM -> "It's been a while. How have you been doing?";
                case CAUTIOUS -> "You seem all right. Everything been okay lately?";
                case AGGRESSIVE -> "There you are. You good? You look like you've been busy.";
            };
        }

        // Friends sometimes initiate a real next step instead of only making small talk.
        // The invitation is sourced from a Go Along opportunity that already exists in their life;
        // if nothing real is happening, no activity is invented for the sake of content.
        if (fighter.isRememberedFor(player) && fighter.getMemoryRelationship() >= 35
                && fighter.getRandom().nextFloat() < (fighter.getMemoryRelationship() >= 60 ? 0.34F : 0.18F)) {
            String life = FighterLifeJoinManager.currentLifeHint(fighter);
            if (!life.isBlank()) return life + " You can come with me if you want.";
        }

        List<String> incidents = WorldIncidentData.get(player.serverLevel()).recent(1);
        if (!incidents.isEmpty() && fighter.getRandom().nextFloat() < 0.42F) {
            String natural = naturalizeIncident(incidents.get(0));
            if (!natural.isBlank()) return natural;
        }

        if (fighter.isRememberedFor(player) && fighter.getMemoryRelationship() >= 35) {
            String last = FighterMemoryManager.lastBondEvent(player, fighter);
            if (last != null && !last.isBlank() && fighter.getRandom().nextFloat() < 0.30F)
                return "I was thinking about when we " + lowerEvent(last) + ". Funny how much has happened since.";
        }

        int era = WorldEraData.get(player.serverLevel()).eraNumber();
        if (era > 0 && fighter.getRandom().nextFloat() < 0.55F) {
            return "Things have changed since the latest saga ended. Even ordinary fighters are training more seriously.";
        }

        return switch (FighterHobby.of(fighter)) {
            case COOKING -> "I've been trying a new recipe between training sessions. Turns out timing matters there too.";
            case STARGAZING -> "The sky's been clear lately. I might go stargazing when things calm down.";
            case FISHING -> "I found a quiet fishing spot nearby. No shouting, no Ki blasts. Almost suspicious.";
            case MUSIC -> "I've had a melody stuck in my head all day. Better than a battle cry, I suppose.";
            case MECHANICS -> "I've been tinkering with some gear. Tiny adjustments are weirdly satisfying.";
            case MAPMAKING -> "I've been updating my maps. This world never stays as familiar as you think.";
            case GARDENING -> "My plants survived another round of fighters blasting the countryside. That's a victory.";
            case TEA -> "I was about to make tea. Training can wait five minutes sometimes.";
            case ROCK_COLLECTING -> "I found a strange rock earlier. No power in it. I checked. Twice.";
            case CLOUD_WATCHING -> "Those clouds look calm. Nice change from watching for incoming Ki attacks.";
            case MARTIAL_NOTES -> "I've been writing down what works in fights instead of trusting memory. It helps.";
            case CARD_GAMES -> "I could use a card game that doesn't end with someone challenging the loser to a duel.";
            case CAMPING -> "I miss a quiet campfire sometimes. Simple nights are underrated.";
            case FASHION -> "I've been thinking about changing my outfit. Fighting isn't an excuse to look identical forever.";
            case BULGARIAN_FOLKLORE -> "I found some Bulgarian folk music. The rhythm has more fight in it than half the warriors I've met.";
        };
    }

    private static String naturalizeIncident(String line) {
        if (line == null || line.isBlank()) return "";
        if (line.contains(" resolved: ") && line.contains(" defeated ")) {
            String body = line.substring(line.indexOf(" resolved: ") + 11);
            return "Did you hear? " + body + ".";
        }
        int colon = line.indexOf(':');
        if (colon > 0 && line.substring(colon + 1).contains(" vs ")) {
            String type = line.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
            String pair = line.substring(colon + 1).trim().replace(" vs ", " and ");
            return "Looks like " + pair + " started an " + type + ".";
        }
        return "I heard about what happened: " + line + ".";
    }

    private static String lowerEvent(String event) {
        String s = event.trim();
        if (s.isEmpty()) return "spent time together";
        return java.lang.Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static void finish(Approach approach, AmbientFighterEntity fighter) {
        ACTIVE.remove(approach.playerId);
        if (fighter != null) fighter.setSocialPlayerApproach(false);
    }

    private static AmbientFighterEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof AmbientFighterEntity fighter) return fighter;
        }
        return null;
    }

    public static int runtimeEntries() { return ACTIVE.size(); }

    public static void clearRuntime(MinecraftServer server) {
        if (server != null) {
            for (Approach approach : List.copyOf(ACTIVE.values())) finish(approach, find(server, approach.fighterId));
        }
        ACTIVE.clear();
    }

    public static void clearRuntime() { ACTIVE.clear(); }
}
