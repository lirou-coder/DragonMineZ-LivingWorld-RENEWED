package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

/**
 * Turns Reactive World moods into actual player-facing boundaries and choices.
 * This deliberately owns consequences rather than visuals: refusing interaction, asking for
 * distance, walking/flying away, and (rarely) a short defensive outburst when repeatedly crowded.
 */
public final class ReactiveInteractionManager {
    private static final String PRESSURE_PLAYER = "LWReactivePressurePlayer";
    private static final String PRESSURE_TICKS = "LWReactivePressureTicks";
    private static final String PRESSURE_WARNED = "LWReactivePressureWarned";
    private static final String PRESSURE_COOLDOWN = "LWReactivePressureCooldown";
    private static final String AGGRO_PLAYER = "LWReactiveBoundaryAggroPlayer";
    private static final String AGGRO_UNTIL = "LWReactiveBoundaryAggroUntil";
    private static final String TALK_SPAM_PLAYER = "LWReactiveTalkSpamPlayer";
    private static final String TALK_SPAM_AT = "LWReactiveTalkSpamAt";
    private static final String TALK_SPAM_COUNT = "LWReactiveTalkSpamCount";
    private static final String WARY_STEP_AT = "LWReactiveWaryStepAt";

    private ReactiveInteractionManager() {}

    public static void tick(AmbientFighterEntity fighter, ServerLevel level) {
        if (fighter == null || level == null || WorldMenaceManager.isHerobrine(fighter)) return;
        long now = level.getGameTime();
        CompoundTag data = fighter.getPersistentData();
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) {
            // The request owns conduct while this real resident is on duty. Remove only generic boundary state;
            // never clear the current target here because mission combat/detection may legitimately own it.
            data.remove(AGGRO_PLAYER);
            data.remove(AGGRO_UNTIL);
            data.remove(PRESSURE_PLAYER);
            data.remove(PRESSURE_TICKS);
            data.remove(PRESSURE_WARNED);
            return;
        }

        // A boundary outburst is temporary by design. Once the message has been made, the fighter
        // must explicitly let the player go again instead of becoming a permanent accidental enemy.
        if (data.hasUUID(AGGRO_PLAYER)) {
            UUID playerId = data.getUUID(AGGRO_PLAYER);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            boolean expired = now >= data.getLong(AGGRO_UNTIL);
            boolean separated = player == null || player.serverLevel() != level || fighter.distanceToSqr(player) > 18.0D * 18.0D;
            boolean covertHold = player != null && FactionRequestManager.suppressAutomaticCovertAggression(player, fighter);
            if (expired || separated || covertHold || ReactiveWorldManager.mood(fighter) != ReactiveWorldManager.Mood.IRRITATED) {
                if (player != null && fighter.getTarget() == player) fighter.setTarget(null);
                fighter.setAttacking(false);
                data.remove(AGGRO_PLAYER);
                data.remove(AGGRO_UNTIL);
            } else if (!fighter.isSanctionedMatchParticipant() && fighter.getTarget() == null && player.isAlive() && !player.isSpectator()) {
                fighter.setTarget(player);
            }
        }

        if (fighter.getTarget() != null || fighter.isSanctionedMatchParticipant() || fighter.isDefeated()
                || fighter.isCaptive() || fighter.isMeditating() || fighter.isTransforming()
                || fighter.isSocialLifeActivity() || fighter.isSocialPlayerApproach() || fighter.isSocialPowerDisplay()) {
            softenPressure(data);
            return;
        }

        int strength = ReactiveWorldManager.moodStrength(fighter);
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        if (strength < 70 || (mood != ReactiveWorldManager.Mood.IRRITATED
                && mood != ReactiveWorldManager.Mood.SOMBER
                && mood != ReactiveWorldManager.Mood.WEARY
                && mood != ReactiveWorldManager.Mood.WARY)) {
            softenPressure(data);
            return;
        }
        if (now < data.getLong(PRESSURE_COOLDOWN)) return;

        double radius = switch (mood) {
            case IRRITATED -> 5.0D;
            case SOMBER -> 4.4D;
            case WEARY -> 3.8D;
            case WARY -> 6.2D;
            default -> 4.0D;
        };
        ServerPlayer nearest = level.getEntitiesOfClass(ServerPlayer.class, fighter.getBoundingBox().inflate(radius), p ->
                        p.isAlive() && !p.isSpectator() && !p.isCreative())
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (nearest == null) {
            softenPressure(data);
            return;
        }
        // Covert quest patrols must not bypass their own LOS/suspicion mechanic through ordinary
        // reactive-boundary aggression. Once the mission detects the player this exemption ends.
        if (FactionRequestManager.suppressAutomaticCovertAggression(nearest, fighter)) {
            softenPressure(data);
            return;
        }

        if (!data.hasUUID(PRESSURE_PLAYER) || !nearest.getUUID().equals(data.getUUID(PRESSURE_PLAYER))) {
            data.putUUID(PRESSURE_PLAYER, nearest.getUUID());
            data.putInt(PRESSURE_TICKS, 0);
            data.putBoolean(PRESSURE_WARNED, false);
        }
        int pressure = Math.min(200, data.getInt(PRESSURE_TICKS) + 1);
        data.putInt(PRESSURE_TICKS, pressure);
        int relationship = fighter.isRememberedFor(nearest) ? fighter.getMemoryRelationship() : 0;

        // Wary means active spacing, not just a label. The fighter gives ground as the player
        // closes in, while the stronger warning/escape system below still handles prolonged crowding.
        if (mood == ReactiveWorldManager.Mood.WARY && strength >= 58
                && fighter.distanceToSqr(nearest) < 5.1D * 5.1D && now >= data.getLong(WARY_STEP_AT)) {
            moveAwayOnGround(fighter, nearest, level);
            data.putLong(WARY_STEP_AT, now + 32L);
            fighter.getLookControl().setLookAt(nearest, 34.0F, 30.0F);
        }

        if (pressure >= 28 && !data.getBoolean(PRESSURE_WARNED) && fighter.getSpeech().isEmpty()) {
            fighter.speak(boundaryWarning(fighter, mood, relationship), 72);
            data.putBoolean(PRESSURE_WARNED, true);
        }
        if (pressure < 78) return;

        boolean volatileType = fighter.getPersonality() == FighterPersonality.AGGRESSIVE
                || fighter.getPersonality() == FighterPersonality.PROUD
                || ReactiveWorldManager.temperament(fighter) == ReactiveWorldManager.Temperament.BULLY
                || ReactiveWorldManager.temperament(fighter) == ReactiveWorldManager.Temperament.BLUNT;
        float attackChance = switch (mood) {
            case IRRITATED -> volatileType && relationship < 55 ? 0.38F : relationship < 10 ? 0.12F : 0.0F;
            case SOMBER -> volatileType && relationship < 10 ? 0.10F : 0.0F;
            case WARY -> volatileType && relationship < -10 ? 0.14F : 0.0F;
            case WEARY -> fighter.getPersonality() == FighterPersonality.AGGRESSIVE && relationship < -15 ? 0.06F : 0.0F;
            default -> 0.0F;
        };

        if (fighter.getRandom().nextFloat() < attackChance) {
            FighterAmbientActivityManager.cancel(fighter);
            PeacekeeperManager.markNpcAggressor(nearest, fighter);
            fighter.setTarget(nearest);
            data.putUUID(AGGRO_PLAYER, nearest.getUUID());
            data.putLong(AGGRO_UNTIL, now + 100L + fighter.getRandom().nextInt(81));
            ReactiveWorldManager.reactStrong(fighter, ReactiveWorldManager.Mood.IRRITATED,
                    "being crowded after asking for space", 1000);
            ReactiveWorldManager.rememberEvent(fighter, "BOUNDARY_BROKEN", nearest.getGameProfile().getName(),
                    "the player ignored repeated requests for distance");
            FighterMemoryManager.strengthenRelationship(nearest, fighter, -2,
                    FighterRelationshipManager.BondEvent.GENERIC, "Ignored their request for space");
            if (fighter.getSpeech().isEmpty()) fighter.speak("I told you to back off!", 82);
        } else {
            boolean flew = fighter.beginReactiveEscapeFrom(nearest);
            if (!flew) moveAwayOnGround(fighter, nearest, level);
            ReactiveWorldManager.rememberEvent(fighter, "TOOK_SPACE", nearest.getGameProfile().getName(),
                    "moved away after feeling crowded");
            if (fighter.getSpeech().isEmpty()) fighter.speak(escapeLine(mood), 80);
        }
        data.putLong(PRESSURE_COOLDOWN, now + 260L + fighter.getRandom().nextInt(221));
        data.remove(PRESSURE_PLAYER);
        data.remove(PRESSURE_TICKS);
        data.remove(PRESSURE_WARNED);
    }

    /** Null means the current mood still permits a normal conversation. */
    public static String talkRefusal(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        if (player == null || fighter == null) return null;
        CompoundTag data = fighter.getPersistentData();
        long now = fighter.level().getGameTime();
        int spam = 1;
        if (data.hasUUID(TALK_SPAM_PLAYER) && player.getUUID().equals(data.getUUID(TALK_SPAM_PLAYER))
                && now - data.getLong(TALK_SPAM_AT) <= 100L) spam = data.getInt(TALK_SPAM_COUNT) + 1;
        data.putUUID(TALK_SPAM_PLAYER, player.getUUID());
        data.putLong(TALK_SPAM_AT, now);
        data.putInt(TALK_SPAM_COUNT, Math.min(12, spam));
        if (spam >= 3) {
            ReactiveWorldManager.reactStrong(fighter, ReactiveWorldManager.Mood.IRRITATED,
                    "being repeatedly pestered for conversation", 850 + Math.min(500, spam * 70));
            ReactiveWorldManager.rememberEvent(fighter, "TALK_SPAM", player.getGameProfile().getName(), "kept trying to talk without giving space");
            if (fighter.level() instanceof ServerLevel level) moveAwayOnGround(fighter, player, level);
            return spam >= 5
                    ? pick(fighter, "I heard you. Stop asking.", "Enough. I don't want to talk.", "You're making me want to leave. Give it a rest.", "No. Repeating yourself isn't changing my answer.")
                    : pick(fighter, "You just asked me. Give me a second.", "Seriously? Let me breathe for a minute.", "I don't have a different answer because you asked again.", "Give me some space before you try that again.");
        }
        int strength = ReactiveWorldManager.moodStrength(fighter);
        if (strength < 66) return null;
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        // Mood can still close someone off, but ordinary good/neutral interactions should win
        // most of the time. Repeated pestering above remains the intentionally strong refusal path.
        float chance = switch (mood) {
            case IRRITATED -> relationship >= 60 ? 0.18F : 0.42F;
            case SOMBER -> relationship >= 60 ? 0.10F : 0.25F;
            case WEARY -> relationship >= 60 ? 0.05F : 0.16F;
            case WARY -> relationship >= 45 ? 0.05F : 0.15F;
            case FOCUSED -> strength >= 85 && relationship < 35 ? 0.12F : 0.03F;
            case UPBEAT, CONTENT -> 0.0F;
        };
        if (fighter.getRandom().nextFloat() >= chance) return null;
        return switch (mood) {
            case IRRITATED -> relationship >= 60
                    ? pick(fighter, "Not right now. I'm wound up and I don't want to take it out on you.", "I like you. That's exactly why I need a minute before we talk.", "Give me a little time to cool down first.")
                    : pick(fighter, "I said I need space. I don't want to talk.", "Not now. I'm already irritated.", "Please don't push me into a conversation right now.", "I need distance, not small talk.");
            case SOMBER -> relationship >= 60
                    ? pick(fighter, "Can we just sit with the quiet for a bit? I don't have much to say.", "I'm glad you're here. I just don't have the words right now.", "Maybe later. Quiet is easier at the moment.")
                    : pick(fighter, "Sorry. I really don't want to talk right now.", "I want to be alone for a bit.", "Not today. I don't have much in me for conversation.");
            case WEARY -> pick(fighter, "I'm exhausted. Give me a little time before we talk.", "Can this wait? I'm running on fumes.", "I need to recover before I can be good company.", "Give me a minute. Even talking feels like work right now.");
            case WARY -> relationship >= 45
                    ? pick(fighter, "Give me a minute. I'm trying to listen to what's around us.", "Stay nearby if you want, but let me focus on the area.", "Something has my attention. We'll talk after I settle.")
                    : pick(fighter, "Not now. I'm keeping my attention on the area.", "Don't distract me. I'm watching what's around us.", "Later. Something feels off and I'm not ignoring it.");
            case FOCUSED -> pick(fighter, "Later. I'm trying not to break my focus.", "Give me a little longer. I'm in the middle of something mentally.", "Not yet. I finally have my thoughts lined up.");
            case UPBEAT, CONTENT -> null;
        };
    }

    /**
     * Strong moods can also close off non-combat social commitments. This keeps the profile buttons
     * from behaving like a vending machine when the fighter is clearly withdrawn, exhausted or angry.
     * Null means the action still makes emotional sense.
     */
    public static String otherActionRefusal(ServerPlayer player, AmbientFighterEntity fighter, String action, int relationship) {
        if (player == null || fighter == null || action == null) return null;
        int strength = ReactiveWorldManager.moodStrength(fighter);
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        String key = action.toLowerCase(java.util.Locale.ROOT);
        boolean close = relationship >= 60;

        if ("meditate".equals(key)) {
            // Shared meditation is deliberately a *rare* emotional rejection. Even withdrawn
            // fighters often accept quiet company; mood/personality only nudge a small chance.
            float chance = switch (mood) {
                case IRRITATED -> strength >= 82 ? (close ? 0.018F : 0.055F) : 0.015F;
                case WARY -> strength >= 84 ? (close ? 0.012F : 0.040F) : 0.010F;
                case WEARY -> 0.012F;
                case SOMBER -> 0.009F;
                case FOCUSED -> 0.014F;
                case UPBEAT -> 0.002F;
                case CONTENT -> 0.003F;
            };
            chance *= switch (fighter.getPersonality()) {
                case CALM -> 0.60F;
                case HEROIC -> 0.78F;
                case CAUTIOUS -> 0.95F;
                case PROUD -> 1.08F;
                case AGGRESSIVE -> 1.18F;
            };
            if (relationship >= 85) chance *= 0.45F;
            else if (relationship >= 60) chance *= 0.65F;
            if (fighter.getRandom().nextFloat() >= chance) return null;
            return switch (mood) {
                case IRRITATED -> "Not this time. I need a little space first.";
                case WARY -> "Not right now. I want to keep my senses on the area.";
                case WEARY -> "Maybe later. I need to rest on my own for a bit.";
                case SOMBER -> "I'd rather have the quiet to myself this time.";
                case FOCUSED -> "Not yet. I want to finish this focus alone.";
                case UPBEAT, CONTENT -> "Maybe another time. I want a solo session right now.";
            };
        }

        if (strength < 68) return null;
        if ("fusion".equals(key)) {
            return switch (mood) {
                case WEARY -> strength >= 72 ? "I'm too drained to fuse safely right now." : null;
                case SOMBER -> strength >= 78 ? "No. My head isn't in the right place for that." : null;
                case IRRITATED -> strength >= 76 ? "Absolutely not while I'm this wound up." : null;
                case WARY -> strength >= 84 && !close ? "Not until I trust what's happening around us." : null;
                default -> null;
            };
        }
        if ("join".equals(key) || "companion".equals(key)) {
            return switch (mood) {
                case IRRITATED -> strength >= 74 && !close ? "I'm trying to get away from people right now, not travel with them." : null;
                case SOMBER -> strength >= 76 && !close ? "I need some time on my own before I commit to anything." : null;
                case WEARY -> strength >= 82 && !close ? "I need to recover before I start going anywhere with someone." : null;
                case WARY -> strength >= 86 && relationship < 45 ? "I don't trust the situation enough to go with you right now." : null;
                default -> null;
            };
        }
        return null;
    }

    /** Null means the fighter is emotionally willing to consider a friendly spar. */
    public static String sparRefusal(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        if (player == null || fighter == null) return null;
        int strength = ReactiveWorldManager.moodStrength(fighter);
        if (strength < 58) return null;
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        boolean volatileType = fighter.getPersonality() == FighterPersonality.AGGRESSIVE || fighter.getPersonality() == FighterPersonality.PROUD;
        return switch (mood) {
            case WEARY -> strength >= 66 ? "No spar. I'm worn out and I need to recover." : null;
            case SOMBER -> strength >= 70 && fighter.getRandom().nextFloat() < (relationship >= 60 ? 0.42F : 0.78F)
                    ? "Not today. My head isn't in a fight." : null;
            case WARY -> strength >= 75 && relationship < 50 && fighter.getRandom().nextFloat() < 0.68F
                    ? "No. I'm not spending energy on a spar while something feels off." : null;
            case IRRITATED -> strength >= 76 && (!volatileType || fighter.getRandom().nextFloat() < 0.66F)
                    ? (volatileType ? "No. Not while I'm this angry. Ask me when I can keep it friendly."
                    : "I'm too irritated for a friendly spar. It wouldn't stay friendly.") : null;
            case FOCUSED -> strength >= 88 && relationship < 35 ? "Not now. I'm working on something specific." : null;
            case UPBEAT, CONTENT -> null;
        };
    }

    public static String sparOutcome(AmbientFighterEntity fighter, ServerPlayer player, boolean playerWon, boolean decisive) {
        if (fighter == null || player == null) return "Good round.";
        if (!decisive) {
            ReactiveWorldManager.react(fighter, ReactiveWorldManager.Mood.WEARY, "a long spar without a clean finish", 850);
            ReactiveWorldManager.rememberEvent(fighter, "SPAR_DRAW", player.getGameProfile().getName(), "the spar ended without a winner");
            return pick(fighter, "Enough. We're both just burning energy now.", "Call it there. Neither of us is getting a clean finish.", "That was a marathon, not a spar. Let's stop before we get sloppy.", "Draw. I need a drink before we turn this into an endurance contest.");
        }
        if (playerWon) {
            ReactiveWorldManager.rememberEvent(fighter, "SPAR_LOSS", player.getGameProfile().getName(), "lost a friendly spar to the player");
            boolean pride = fighter.getPersonality() == FighterPersonality.PROUD || fighter.getPersonality() == FighterPersonality.AGGRESSIVE
                    || ReactiveWorldManager.temperament(fighter) == ReactiveWorldManager.Temperament.BULLY
                    || ReactiveWorldManager.temperament(fighter) == ReactiveWorldManager.Temperament.BLUNT;
            if (pride) {
                ReactiveWorldManager.reactStrong(fighter, ReactiveWorldManager.Mood.IRRITATED,
                        "losing the spar to " + player.getGameProfile().getName(), 1500);
                fighter.flareAura(38);
                return switch (fighter.getPersonality()) {
                    case PROUD -> pick(fighter, "Tch... don't look so pleased. Next round is mine.", "Enjoy that win. I'm already thinking about the rematch.", "You caught me. It won't happen the same way twice.", "Fine. You earned that one. I'm still taking the next.");
                    case AGGRESSIVE -> pick(fighter, "Damn it! Again. I know I can beat you.", "No way I'm ending on that. I want another round later.", "You got me and I hate how clean that was.", "Fine! You won. Now I know exactly what I want to fix.");
                    default -> pick(fighter, "Yeah, yeah. You won. Don't make a thing out of it.", "All right, that's yours. Don't get smug.", "You got the better round. Leave it there.", "Fine. Score one for you.");
                };
            }
            ReactiveWorldManager.react(fighter, fighter.getHealth() < fighter.getMaxHealth() * 0.45F
                            ? ReactiveWorldManager.Mood.WEARY : ReactiveWorldManager.Mood.FOCUSED,
                    "thinking about what went wrong in the spar", 1200);
            return fighter.getPersonality() == FighterPersonality.HEROIC
                    ? pick(fighter, "Good hit. I lost that one fair. I know what I need to work on.", "That was clean. You earned it—now I know where I slipped.", "Nice round. I learned more from losing that than from an easy win.", "You got me fair. Next session I want to work on that opening.")
                    : pick(fighter, "You got me. Give me a minute—I want to think through that round.", "Good fight. I can already see two mistakes I made.", "That's your round. I need to replay the last exchange in my head.", "Fair win. I know exactly which moment turned it.");
        }

        ReactiveWorldManager.rememberEvent(fighter, "SPAR_WIN", player.getGameProfile().getName(), "won a friendly spar against the player");
        ReactiveWorldManager.react(fighter,
                fighter.getPersonality() == FighterPersonality.PROUD || fighter.getPersonality() == FighterPersonality.AGGRESSIVE
                        ? ReactiveWorldManager.Mood.UPBEAT : ReactiveWorldManager.Mood.CONTENT,
                "winning the spar", 1050);
        return switch (fighter.getPersonality()) {
            case PROUD -> pick(fighter, "That's more like it. Come back when you've closed the gap.", "Good effort. Next time make me work even harder for it.", "I had control of that round, but you made me pay attention.", "Keep improving. I want the rematch to be closer.");
            case AGGRESSIVE -> pick(fighter, "Ha! That's the pace I wanted. Again when you're ready.", "Good! You kept it interesting. Let's do that again sometime.", "That's a proper warm-up. Next time push harder.", "Now that was fun. Get stronger and come find me again.");
            case HEROIC -> pick(fighter, "Good round. You pushed me harder than I expected.", "You made me earn it. That's exactly what a spar should do.", "Nice work. I saw a couple of moments where you almost turned it.", "Good fight. Keep building on what you did right.");
            case CALM -> pick(fighter, "Good spar. There were a few moments where you nearly had me.", "Clean round. Your timing is getting better.", "That was useful. You forced me to adjust more than once.", "Good session. Neither of us wasted the time.");
            case CAUTIOUS -> pick(fighter, "Good round. I had to stay careful the whole time.", "You kept giving me reasons not to relax. Good spar.", "I won, but there were enough close moments to remember.", "That was controlled and useful. I'd do that again.");
        };
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static String boundaryWarning(AmbientFighterEntity fighter, ReactiveWorldManager.Mood mood, int relationship) {
        return switch (mood) {
            case IRRITATED -> relationship >= 60 ? "I like you, but I need some space right now." : "Back up. I'm not in the mood to be crowded.";
            case SOMBER -> relationship >= 60 ? "Can you give me a minute alone?" : "Please... I want to be by myself right now.";
            case WEARY -> "You're a little close. I just need room to breathe.";
            case WARY -> "Don't crowd me. I need to see what's around us.";
            default -> "Give me a little room.";
        };
    }

    private static String escapeLine(ReactiveWorldManager.Mood mood) {
        return switch (mood) {
            case IRRITATED -> "Fine. If you won't give me space, I'll take it myself.";
            case SOMBER -> "I'm going somewhere quieter.";
            case WEARY -> "I need somewhere I can actually rest.";
            case WARY -> "I'm changing position. This is too exposed.";
            default -> "I'm moving on.";
        };
    }

    private static void moveAwayOnGround(AmbientFighterEntity fighter, ServerPlayer player, ServerLevel level) {
        Vec3 away = fighter.position().subtract(player.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(fighter.getRandom().nextDouble() - 0.5D, 0.0D, fighter.getRandom().nextDouble() - 0.5D);
        away = away.normalize().scale(12.0D + fighter.getRandom().nextDouble() * 10.0D);
        BlockPos rough = BlockPos.containing(fighter.position().add(away));
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, rough, fighter.getRandom(), 2, 9, 22);
        if (safe != null) fighter.getNavigation().moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                1.02D * ReactiveWorldManager.movementPace(fighter));
    }

    private static void softenPressure(CompoundTag data) {
        int pressure = data.getInt(PRESSURE_TICKS);
        if (pressure <= 0) {
            data.remove(PRESSURE_PLAYER);
            data.remove(PRESSURE_WARNED);
            return;
        }
        pressure = Math.max(0, pressure - 3);
        data.putInt(PRESSURE_TICKS, pressure);
        if (pressure == 0) {
            data.remove(PRESSURE_PLAYER);
            data.remove(PRESSURE_WARNED);
        }
    }
}
