package com.dmzlivingworld.world;

import com.dmzlivingworld.client.LWLang;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

/** Natural social contact: conversation introduces people, while shared experiences build real trust. */
public final class FighterSocialManager {
    private FighterSocialManager() {}

    private record Conversation(String line, String memory) {}

    public static void talk(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return;
        if (FactionRequestMissionManager.isRequestActionLocked(fighter)) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.busy_request", fighter.getFighterName()), false);
            return;
        }
        if (!LivingWorldConfig.socialTalk()) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.disabled"), false);
            return;
        }
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating() || fighter.isSanctionedMatchParticipant()) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.bad_moment"), false);
            return;
        }
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.menace_refusal").withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }

        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
        int relationship = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        boolean trustedDespiteAlignment = fighter.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.GOOD
                && PlayerAlignmentBridge.alignment(player) <= 32 && relationship >= 45;
        if (fighter.getTarget() == player || (disposition == FighterRelationshipManager.Disposition.HOSTILE && !trustedDespiteAlignment) || relationship <= -35) {
            fighter.speak(hostileLine(fighter), 66);
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.unwilling", fighter.getFighterName()).withStyle(ChatFormatting.RED), false);
            return;
        }
        // Cooldown is a hard availability gate, not another social attempt. Check it before
        // the anti-pestering tracker so harmless clicks during cooldown cannot manufacture irritation.
        long talkCooldownRemaining = FighterMemoryManager.socialContactCooldownRemaining(player, fighter);
        if (talkCooldownRemaining > 0L) {
            long seconds = Math.max(1L, talkCooldownRemaining / 20L);
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.cooldown", fighter.getFighterName(), seconds).withStyle(ChatFormatting.GRAY), false);
            return;
        }

        String refusal = ReactiveInteractionManager.talkRefusal(player, fighter, relationship);
        if (refusal != null) {
            fighter.speak(refusal, 90);
            ReactiveWorldManager.rememberEvent(fighter, "DECLINED_TALK", player.getGameProfile().getName(),
                    "did not want to talk while " + ReactiveWorldManager.mood(fighter).label().toLowerCase(java.util.Locale.ROOT));
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.refused", fighter.getFighterName()).withStyle(ChatFormatting.GRAY), false);
            return;
        }

        Conversation conversation = freshConversation(fighter, conversation(player, fighter, relationship));
        int minSeconds = LivingWorldConfig.talkCooldownMinSeconds();
        int maxSeconds = LivingWorldConfig.talkCooldownMaxSeconds();
        long cooldown = (long)(minSeconds + player.getRandom().nextInt(Math.max(1, maxSeconds - minSeconds + 1))) * 20L;
        FighterMemoryManager.SocialContactResult result = FighterMemoryManager.trySocialContact(
                player, fighter, LivingWorldConfig.talkBaseGain(), cooldown, conversation.memory());
        if (!result.accepted()) {
            fighter.speak(hostileLine(fighter), 60);
            return;
        }

        // Cooldown is a hard interaction gate. R16.1 generated/spoke the new conversation first
        // and only then reported that the contact was cooling down, which made the NPC visibly
        // answer despite the rejection. Do not create any new speech/reaction while cooling down.
        if (result.coolingDown()) {
            long seconds = Math.max(1L, result.remainingTicks() / 20L);
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.cooldown", fighter.getFighterName(), seconds).withStyle(ChatFormatting.GRAY), false);
            return;
        }

        fighter.speak(conversation.line(), 88);
        ReactiveWorldManager.onPlayerTalk(fighter, result.relationship());

        String stage = FighterRelationshipManager.relationshipStage(result.relationship());
        int before = result.relationship() - result.gained();
        String previousStage = FighterRelationshipManager.relationshipStage(before);
        if (result.gained() > 0 && !stage.equals(previousStage)) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.new_stage", fighter.getFighterName(), relationshipLabel(stage)).withStyle(ChatFormatting.GREEN), false);
        } else if (result.gained() > 0) {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.went_well", relationshipLabel(stage)).withStyle(ChatFormatting.GRAY), false);
        } else {
            player.displayClientMessage(Component.translatable("dmzlivingworld.message.social.trust_requires_action", fighter.getFighterName()).withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static Component relationshipLabel(String stage) {
        String slug = stage.toLowerCase(Locale.ROOT).replace(' ', '_');
        return Component.translatableWithFallback("dmzlivingworld.label.relationship." + slug, stage);
    }

    /** Uses only state the fighter actually owns; no invented player history or random exposition. */
    private static Conversation conversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        CompoundTag legacy = fighter.getLegacyData();
        UUID companion = LivingBondManager.companionId(player);

        // First answer the thing that is literally happening now/recently; only then fall back to
        // emotional state and long-term biography. This keeps Talk grounded in the live world.
        Conversation situation = situationConversation(player, fighter, relationship);
        if (situation != null) return situation;

        Conversation reunion = reunionConversation(player, fighter, relationship);
        if (reunion != null) return reunion;

        Conversation science = scientistConversation(fighter);
        if (science != null) return science;

        // Calm/positive fighters may naturally bring up somebody nearby. Distressed moods retain
        // priority so an irritated, somber, weary or wary person never jumps into casual gossip.
        ReactiveWorldManager.Mood currentMood = ReactiveWorldManager.mood(fighter);
        if (currentMood == ReactiveWorldManager.Mood.UPBEAT || currentMood == ReactiveWorldManager.Mood.CONTENT
                || (currentMood == ReactiveWorldManager.Mood.FOCUSED && ReactiveWorldManager.moodStrength(fighter) < 58)) {
            Conversation otherPerson = otherNpcConversation(fighter);
            if (otherPerson != null) return otherPerson;
        }

        // The explicit Talk button is emotional first after immediate context. A fighter who is
        // irritated/somber/weary should never answer with generic hobby chatter as if the mood did
        // not exist. Even Content/Upbeat get wording that matches the state.
        Conversation moodConversation = moodConversation(player, fighter, relationship,
                companion != null && companion.equals(fighter.getUUID()));
        if (moodConversation != null) return moodConversation;

        if (companion != null && companion.equals(fighter.getUUID())) {
            return switch (FighterRelationshipManager.socialStyle(fighter)) {
                case PROTECTIVE -> conversationKey("companion.protective", "Stay close if things get ugly. I'll cover you.", "Caught up while travelling together");
                case LOYAL -> conversationKey("companion.loyal", "I'm still with you. Where are we heading next?", "Caught up while travelling together");
                case DISCIPLINED -> conversationKey("companion.disciplined", "We should train again when we have a quiet moment.", "Discussed training while travelling");
                case RESPECT_DRIVEN -> conversationKey("companion.respect", "You've been keeping pace. Good.", "Discussed progress while travelling");
                case COMPETITIVE -> conversationKey("companion.competitive", "Don't think travelling together means I'll go easy on you next time.", "Teased each other while travelling");
                case OPEN -> conversationKey("companion.open", "It's nice not having to travel alone.", "Caught up while travelling together");
                case GUARDED -> conversationKey("companion.guarded", "I don't travel with just anyone. Remember that.", "Acknowledged their travelling trust");
                case PRAGMATIC -> conversationKey("companion.pragmatic", "We work well together. That's worth keeping.", "Discussed working together");
            };
        }

        if (fighter.getHealth() < fighter.getMaxHealth() * 0.45F) {
            return switch (fighter.getPersonality()) {
                case HEROIC -> conversationKey("recovery.heroic", "I'm all right. I just need a little time to recover.", "Checked on their recovery");
                case CALM -> conversationKey("recovery.calm", "Nothing serious. I'm letting my body catch up.", "Checked on their recovery");
                case PROUD -> conversationKey("recovery.proud", "I've had worse. Don't make a big deal out of it.", "Checked on their recovery");
                case AGGRESSIVE -> conversationKey("recovery.aggressive", "I'm fine. I just need another minute.", "Checked on their recovery");
                case CAUTIOUS -> conversationKey("recovery.cautious", "I'm taking it easy until I'm back at full strength.", "Checked on their recovery");
            };
        }

        if (fighter.wasRescuedByMemoryOwner() && relationship < 35) {
            return conversationKey("rescue_remembered", "I haven't forgotten that you helped me when I needed it.", "Talked about the earlier rescue");
        }

        long lastBattle = legacy.getLong("LastBattle");
        String lastOpponent = legacy.getString("LastOpponent");
        if (!lastOpponent.isBlank() && lastBattle > 0L && fighter.level().getGameTime() - lastBattle < 12000L) {
            return switch (fighter.getPersonality()) {
                case HEROIC -> conversationKey("recent_fight.heroic", "That fight with %s is still on my mind. I learned from it.", "Reflected on the recent fight with " + lastOpponent, lastOpponent);
                case CALM -> conversationKey("recent_fight.calm", "I've been thinking through my fight with %s. There were mistakes I can fix.", "Reflected on the recent fight with " + lastOpponent, lastOpponent);
                case PROUD -> conversationKey("recent_fight.proud", "Next time I face %s, it'll go differently.", "Reflected on the recent fight with " + lastOpponent, lastOpponent);
                case AGGRESSIVE -> conversationKey("recent_fight.aggressive", "I'd fight %s again right now.", "Reflected on the recent fight with " + lastOpponent, lastOpponent);
                case CAUTIOUS -> conversationKey("recent_fight.cautious", "That fight with %s showed me what I need to watch for.", "Reflected on the recent fight with " + lastOpponent, lastOpponent);
            };
        }

        String goal = FighterGoalManager.summary(fighter);
        if (!"none".equals(goal)) {
            if (goal.startsWith("Learn ") || goal.startsWith("Complete ") || goal.startsWith("Advance ")) {
                return conversationKey("goal.training", "I've been focused on training. I want the next step to be earned.", "Talked about current training goals");
            }
            if (goal.startsWith("Acquire ")) {
                return conversationKey("goal.equipment", "I'm still looking for equipment that actually suits how I fight.", "Talked about finding better equipment");
            }
            if (goal.startsWith("Defeat ") || goal.startsWith("Win ")) {
                return conversationKey("goal.challenge", "I need a serious fight soon. That's the only way I'll know if I've improved.", "Talked about seeking a serious challenge");
            }
        }
        if (!fighter.getRivalName().isBlank()) {
            return conversationKey("rival", "I haven't lost track of %s. That rivalry keeps me moving.", "Talked about rival " + fighter.getRivalName(), fighter.getRivalName());
        }
        if (fighter.isFactionMember()) {
            String faction = fighter.getFactionDisplayName().isBlank() ? "my faction" : fighter.getFactionDisplayName();
            return switch (fighter.getPersonality()) {
                case HEROIC -> conversationKey("faction.heroic", "I still have responsibilities with %s. People depend on us.", "Talked about responsibilities in " + faction, faction);
                case CALM -> conversationKey("faction.calm", "Things with %s have been steady lately.", "Talked about life in " + faction, faction);
                case PROUD -> conversationKey("faction.proud", "%s expects strength. I intend to represent it properly.", "Talked about standing in " + faction, faction);
                case AGGRESSIVE -> conversationKey("faction.aggressive", "If %s needs a fighter, they know where to find me.", "Talked about fighting for " + faction, faction);
                case CAUTIOUS -> conversationKey("faction.cautious", "I'm keeping an eye on things around %s. Quiet doesn't always mean safe.", "Talked about security in " + faction, faction);
            };
        }

        if (relationship >= 15) {
            return switch (FighterRelationshipManager.socialStyle(fighter)) {
                case PROTECTIVE -> conversationKey("familiar.protective", "Watch yourself out there. Trouble travels fast.", "Checked in on each other");
                case LOYAL -> conversationKey("familiar.loyal", "It's good seeing a familiar face again.", "Caught up as familiar faces");
                case DISCIPLINED -> conversationKey("familiar.disciplined", "Consistency matters more than one good day.", "Talked about consistency");
                case RESPECT_DRIVEN -> conversationKey("familiar.respect", "Keep getting stronger. I notice effort.", "Talked about progress");
                case COMPETITIVE -> conversationKey("familiar.competitive", "Next time, let's see who improved more.", "Compared recent progress");
                case OPEN -> conversationKey("familiar.open", "I'm glad we actually get to talk sometimes.", "Caught up personally");
                case GUARDED -> conversationKey("familiar.guarded", "You're more reliable than I first thought.", "Acknowledged growing trust");
                case PRAGMATIC -> conversationKey("familiar.pragmatic", "You seem useful to have around. That's a compliment.", "Talked about working well together");
            };
        }
        return switch (fighter.getPersonality()) {
            case HEROIC -> conversationKey("intro.heroic", "Take care out there. Not everyone will warn you first.", "Had an introductory conversation");
            case CALM -> conversationKey("intro.calm", "Sometimes talking tells you more than fighting.", "Had an introductory conversation");
            case PROUD -> conversationKey("intro.proud", "If you're serious about getting stronger, prove it over time.", "Talked about proving yourself over time");
            case AGGRESSIVE -> conversationKey("intro.aggressive", "I'm not much for talking. Fight me sometime.", "Talked about sparring sometime");
            case CAUTIOUS -> conversationKey("intro.cautious", "I don't know you well yet. Give it time.", "Had a cautious introductory conversation");
        };
    }

    private static Conversation conversationKey(String key, String fallback, String memory, Object... args) {
        return new Conversation(LWLang.speechKey("dialogue.social." + key, fallback, args), memory);
    }

    private static Conversation scientistConversation(AmbientFighterEntity fighter) {
        if (!FighterScientistManager.isScientist(fighter) || fighter.getRandom().nextFloat() >= 0.82F) return null;
        int formula = FighterScientistManager.formulaProgress(fighter);
        int seeds = FighterScientistManager.availableSeeds(fighter);
        int maxSeeds = FighterScientistManager.maxSeeds(fighter);
        String[] lines = speechPool("scientist", new String[]{
                "The latest Saibaman batch is stronger, but I'm more interested in whether the growth curve stays stable.",
                "I've been comparing combat data against the cultivation ratio. Tiny changes are producing annoyingly large behavioral differences.",
                LWLang.speechKey("dialogue.social.scientist.2." + (seeds == 1 ? "one" : "many"),
                        seeds == 1
                                ? "I have %s viable seed out of %s. I'd rather grow fewer useful specimens than waste a whole batch."
                                : "I have %s viable seeds out of %s. I'd rather grow fewer useful specimens than waste a whole batch.",
                        seeds, maxSeeds),
                LWLang.speechKey("dialogue.social.scientist.3",
                        "Formula refinement is at %s out of six. Past this point, raw power matters less than consistency.", formula),
                "People call them disposable. That's exactly why most researchers never learn anything from them.",
                "A stronger master changes the entire baseline. If I don't recalibrate, yesterday's successful formula becomes today's weak specimen.",
                "The scouter data is useful, but it lies by omission. Power level doesn't tell me why one specimen hesitates and another attacks immediately.",
                "I'm trying to make the next batch scale cleanly without turning the growth medium into sludge again.",
                "Power is the easy variable. Metabolic stability, aggression latency and tissue recovery are the parts that keep ruining my models.",
                "A Saibaman isn't just a number on a scouter. I track response time, survival curve, Ki leakage and whether the thing follows an instruction before biting somebody.",
                "The latest culture has a cleaner Ki-density curve. If the variance stays under control, I can increase output without shortening lifespan.",
                "I need another real combat sample. Controlled sparring contaminates the data; everyone knows they're supposed to stop.",
                "I'm correlating permanent battle power against specimen yield. Temporary transformations are statistical noise unless I label them separately.",
                "You'd be amazed how often 'make it stronger' produces a specimen that's technically powerful and practically useless."
        });
        String line = lines[fighter.getRandom().nextInt(lines.length)];
        return new Conversation(line, "Talked about Saibaman research and experimental data");
    }

    private static Conversation reunionConversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        if (relationship < 15) return null;
        FighterMemoryManager.ReunionInfo info = FighterMemoryManager.reunionInfo(player, fighter);
        if (!info.qualifies()) return null;
        double current = PlayerWorldManager.playerBattlePower(player);
        boolean stronger = info.oldPlayerPower() > 0.0D && current >= info.oldPlayerPower() * 1.25D;
        String[] lines = stronger ? speechPool("reunion.stronger", new String[]{
                "Good to see you again. Your Ki's changed—you've gotten stronger.",
                "There you are. I almost didn't recognize that power. You've been training.",
                "It's been a while. That increase in your Ki isn't subtle.",
                "Good seeing you again. You've definitely grown since last time."
        }) : speechPool("reunion.normal", new String[]{
                "Good to see you again. It's been a while.",
                "There you are. I was wondering when we'd cross paths again.",
                "Been a while. Glad to see you're still moving.",
                "Hey. Good timing—it's actually nice seeing a familiar face again."
        });
        return new Conversation(lines[fighter.getRandom().nextInt(lines.length)], stronger
                ? "Reunited after time apart and noticed your growth" : "Reunited after time apart");
    }

    /** Occasionally lets Talk expose that NPCs notice one another as people, not scenery. */
    private static Conversation otherNpcConversation(AmbientFighterEntity fighter) {
        if (!(fighter.level() instanceof ServerLevel level) || fighter.getRandom().nextFloat() >= 0.18F) return null;
        java.util.List<AmbientFighterEntity> others = level.getEntitiesOfClass(AmbientFighterEntity.class,
                fighter.getBoundingBox().inflate(22.0D), other -> other != fighter && other.isAlive() && !WorldMenaceManager.isWorldMenace(other));
        if (others.isEmpty()) return null;
        AmbientFighterEntity other = others.get(fighter.getRandom().nextInt(others.size()));
        String name = other.getFighterName();
        String activity = FighterAmbientActivityManager.currentActivity(other);
        int bond = FighterNpcSocialManager.bond(fighter, other);
        if (!activity.isBlank()) return conversationKey("other.activity", "%s has been %s lately. I notice what people around me are doing.", "Commented about " + name, name, activityArgument(activity));
        if (bond >= 6) return conversationKey("other.bonded", "%s and I have gotten to know each other a little. They're good company.", "Commented warmly about " + name, name);
        if (other.getBattlePower() > fighter.getBattlePower() * 1.6D) return conversationKey("other.strong", "%s is strong. You can feel it before they even start fighting.", "Commented on " + name + "'s strength", name);
        return conversationKey("other.seen", "I've seen %s around here. Everybody has their own routine if you pay attention.", "Commented about " + name, name);
    }

    /** Avoid exact Talk-line loops even when the same contextual branch wins repeatedly. */
    private static Conversation freshConversation(AmbientFighterEntity fighter, Conversation selected) {
        if (selected == null) return conversationKey("fallback", "Not much to say right now.", "Talked briefly");
        CompoundTag data = fighter.getPersistentData();
        String line = selected.line();
        boolean repeated = false;
        for (int i = 0; i < 4; i++) if (line.equals(data.getString("LWRecentTalk" + i))) repeated = true;
        if (repeated) {
            String[] alternatives = freshAlternatives(fighter);
            for (int tries = 0; tries < alternatives.length * 2; tries++) {
                String candidate = alternatives[fighter.getRandom().nextInt(alternatives.length)];
                boolean recent = false;
                for (int i = 0; i < 4; i++) if (candidate.equals(data.getString("LWRecentTalk" + i))) recent = true;
                if (!recent) { line = candidate; break; }
            }
        }
        for (int i = 3; i > 0; i--) data.putString("LWRecentTalk" + i, data.getString("LWRecentTalk" + (i - 1)));
        data.putString("LWRecentTalk0", line);
        return new Conversation(line, selected.memory());
    }

    /** Mood-safe fallback pools keep anti-repeat logic from producing emotionally contradictory chatter. */
    private static String[] freshAlternatives(AmbientFighterEntity fighter) {
        return switch (ReactiveWorldManager.mood(fighter)) {
            case UPBEAT -> speechPool("mood.upbeat", new String[]{
                    "Things are actually going pretty well today.",
                    "I've got more energy than usual. Might as well use it.",
                    "Good day to be out doing something instead of standing around.",
                    "I don't know what changed, but I'm enjoying the day.",
                    "Feels like one of those days where training might actually be fun.",
                    "I'm in a good rhythm today. I want to keep it going.",
                    "Nothing's dragging me down right now. That's nice for a change.",
                    "I've been noticing the little things today. The world's not all fights and disasters."
            });
            case CONTENT -> speechPool("mood.content", new String[]{
                    "Nothing dramatic to report. I'm fine with that.",
                    "It's been calm enough to actually think for once.",
                    "I'm taking things one day at a time. It works.",
                    "A quiet stretch isn't wasted time.",
                    "I'm doing all right. No need to complicate it.",
                    "I've been enjoying not having somewhere urgent to be.",
                    "Sometimes ordinary is exactly what I want.",
                    "I'm just letting the day happen instead of forcing something out of it."
            });
            case FOCUSED -> speechPool("mood.focused", new String[]{
                    "I've got something I'm working toward. I don't want to lose the thread.",
                    "My head's on training right now. Everything else can wait a little.",
                    "I'm trying to turn what I noticed into something I can actually use.",
                    "I finally know what I need to improve next.",
                    "I'm keeping my attention narrow today. It helps.",
                    "There's a difference between being busy and actually making progress.",
                    "I've been going over the same weakness until I understand it.",
                    "I'm not chasing every distraction. One thing at a time."
            });
            case WARY -> speechPool("mood.wary", new String[]{
                    "I'm still watching the area. Something doesn't sit right with me.",
                    "Keep your senses open. I'm not convinced we're alone here.",
                    "I keep checking the same direction for a reason.",
                    "Maybe it's nothing, but I'd rather notice too much than too little.",
                    "I'm listening more than talking right now.",
                    "Don't mind me looking around. I'm keeping track of who comes close.",
                    "I'm not panicking. I'm paying attention.",
                    "Something has me on edge, so I'm keeping some distance until it passes."
            });
            case IRRITATED -> speechPool("mood.irritated", new String[]{
                    "I'm still annoyed. I'd rather not pretend otherwise.",
                    "My patience is thin right now, so keep it simple.",
                    "I need a little space before I say something I don't mean.",
                    "I'm trying to cool off. Talking isn't helping much yet.",
                    "Today has been getting on my nerves one thing at a time.",
                    "I'm not looking for an argument. That's why I'm keeping this short.",
                    "Give me time and I'll settle down. Right now I'm still wound up.",
                    "I'm handling it. I just don't want anyone pushing me while I do."
            });
            case SOMBER -> speechPool("mood.somber", new String[]{
                    "I've got a lot on my mind. Quiet feels easier right now.",
                    "I'm here. I'm just not feeling very talkative.",
                    "Some things take longer to shake than a bad fight.",
                    "I don't really want to fake being cheerful today.",
                    "I'm taking the day slowly. That's about all I can manage right now.",
                    "I keep drifting back into the same thoughts.",
                    "I'll be all right. I just need some time with my own head.",
                    "Not every bad feeling needs a fight to solve it."
            });
            case WEARY -> speechPool("mood.weary", new String[]{
                    "I'm running low. I need a proper rest more than another challenge.",
                    "Everything feels heavier when you're this tired.",
                    "I'm trying not to spend energy I don't have.",
                    "I could sleep for a week. Maybe two days if we're being realistic.",
                    "My body is telling me to stop, and for once I'm listening.",
                    "I'm still moving, just not quickly.",
                    "I need food, rest, and about half as much excitement as usual.",
                    "I'll have more to say when I don't feel like my Ki is running on fumes."
            });
        };
    }

    private static String[] speechPool(String group, String[] fallbacks) {
        String[] result = new String[fallbacks.length];
        for (int i = 0; i < fallbacks.length; i++)
            result[i] = LWLang.isSpeechKey(fallbacks[i]) ? fallbacks[i]
                    : LWLang.speechKey("dialogue.social." + group + "." + i, fallbacks[i]);
        return result;
    }

    private static Conversation situationConversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        String activity = FighterAmbientActivityManager.currentActivity(fighter);
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        if (!activity.isBlank()) {
            if (activity.startsWith("Dancing")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.dancing." + (mood == ReactiveWorldManager.Mood.UPBEAT ? "upbeat" : "normal")),
                    "Talked during " + activity);
            if (activity.equals("Resting")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.resting." + (mood == ReactiveWorldManager.Mood.WEARY ? "weary" : mood == ReactiveWorldManager.Mood.SOMBER ? "somber" : "normal")),
                    "Talked while resting");
            if (activity.equals("Sitting")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.sitting." + fighter.getRandom().nextInt(2)),
                    "Talked while sitting");
            if (activity.equals("Jogging")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.jogging." + fighter.getRandom().nextInt(2)),
                    "Talked during a jog");
            if (activity.equals("Training")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.training." + fighter.getRandom().nextInt(2)),
                    "Talked during real training");
            if (activity.equals("Inspecting a flower")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.flower." + fighter.getRandom().nextInt(2)),
                    "Talked while inspecting a flower");
            if (activity.equals("Taking an apple break")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.apple." + fighter.getRandom().nextInt(2)),
                    "Talked during an apple break");
            if (activity.equals("Looking around")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.looking." + (mood == ReactiveWorldManager.Mood.WARY ? "wary" : "normal")),
                    "Talked while scouting");
            if (activity.equals("Fishing")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.fishing." + (mood == ReactiveWorldManager.Mood.IRRITATED ? "irritated" : "normal")),
                    "Talked while fishing");
            if (activity.equals("Stargazing")) return new Conversation(
                    LWLang.speechKey("dialogue.social.activity.stargazing." + (mood == ReactiveWorldManager.Mood.SOMBER ? "somber" : "normal")),
                    "Talked while stargazing");
            if (activity.equals("Flying")) return conversationKey("activity.flying", "I'm moving right now. Catch me when I'm back on the ground.", "Talked during a flight");
        }

        String event = ReactiveWorldManager.recentEventType(fighter, 2600L);
        String subject = ReactiveWorldManager.recentEventSubject(fighter, 2600L);
        if (event.isBlank()) return null;
        return switch (event) {
            case "SPAR_LOSS" -> new Conversation(
                    LWLang.speechKey("dialogue.social.event.spar_loss." + (mood == ReactiveWorldManager.Mood.IRRITATED ? "irritated" : "normal"),
                            mood == ReactiveWorldManager.Mood.IRRITATED
                                    ? "Yeah, I lost that spar to %s. I'm still annoyed about it, so don't rub it in."
                                    : "I'm still replaying that spar with %s in my head. I know where I slipped.",
                            subject.isBlank() ? player.getGameProfile().getName() : subject),
                    "Talked about the recent spar loss");
            case "SPAR_WIN" -> new Conversation(
                    LWLang.speechKey("dialogue.social.event.spar_win."
                            + (fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.PROUD ? "proud" : "normal")),
                    "Talked about the recent spar win");
            case "ALLY_DIED" -> new Conversation(
                    subject.isBlank() ? LWLang.speechKey("dialogue.social.event.ally_died.unknown")
                            : LWLang.speechKey("dialogue.social.event.ally_died.named",
                            "I'm still thinking about %s. Seeing them fall doesn't just disappear because the fight ended.", subject),
                    "Talked about a fallen ally");
            case "ENEMY_DIED" -> new Conversation(
                    subject.isBlank() ? LWLang.speechKey("dialogue.social.event.enemy_died.unknown")
                            : LWLang.speechKey("dialogue.social.event.enemy_died.named",
                            "%s is down. I'm still watching in case that wasn't the end of it.", subject),
                    "Talked about a defeated enemy");
            case "HORN_RALLY" -> conversationKey("event.horn_rally", "That horn wasn't for show. When it sounds, everyone is supposed to move together.", "Talked about the faction rally");
            case "BOUNDARY_BROKEN" -> conversationKey("event.boundary_broken", "I asked for space and you kept pushing. I'm still irritated about that.", "Talked after a boundary was ignored");
            case "TOOK_SPACE" -> conversationKey("event.took_space", "I moved because I needed distance. I meant it.", "Talked after taking space");
            case "MOB_SEEN" -> new Conversation(subject.isBlank() ? LWLang.speechKey("dialogue.social.event.mob_seen.unknown")
                    : LWLang.speechKey("dialogue.social.event.mob_seen.named", "I noticed that %s nearby. I'm keeping it in mind.", subject), "Talked about something nearby");
            case "WORLD_CONDITION" -> new Conversation(subject.isBlank() ? LWLang.speechKey("dialogue.social.event.world_condition.unknown")
                    : LWLang.speechKey("dialogue.social.event.world_condition.named", "I'm paying attention to %s. It changes how I move around here.", subject), "Talked about the current conditions");
            default -> null;
        };
    }

    private static Conversation moodConversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship, boolean companion) {
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        int strength = ReactiveWorldManager.moodStrength(fighter);
        String cause = speechCause(ReactiveWorldManager.moodCause(fighter));
        String goal = FighterGoalManager.summary(fighter);
        boolean close = relationship >= 60;
        boolean familiar = relationship >= 35;
        boolean hurt = fighter.getHealth() < fighter.getMaxHealth() * 0.55F;
        String eventType = ReactiveWorldManager.recentEventType(fighter, 3200L);
        String eventSubject = ReactiveWorldManager.recentEventSubject(fighter, 3200L);

        // Mood is always acknowledged by Talk. Low-strength Content is the only state allowed to
        // fall through to the richer legacy/goal/faction small-talk pool below.
        if (mood == ReactiveWorldManager.Mood.CONTENT && strength < 38) return null;

        return switch (mood) {
            case UPBEAT -> {
                String state = companion ? "companion" : close ? "close" : "normal";
                String line = LWLang.speechKey("dialogue.social.context.upbeat." + state);
                yield new Conversation(line, "Talked while feeling upbeat");
            }
            case CONTENT -> new Conversation(
                    LWLang.speechKey("dialogue.social.context.content." + (familiar ? "familiar" : "normal")),
                    "Talked during a calm stretch");
            case FOCUSED -> {
                String line;
                if (!"none".equals(goal)) line = LWLang.speechKey("dialogue.social.context.focused.goal",
                        "I'm trying to stay focused on %s. I don't want to lose that momentum.", goalArgument(fighter));
                else line = LWLang.speechKey("dialogue.social.context.focused.cause",
                        "I'm focused right now because of %s. That's what has my attention.", cause);
                yield new Conversation(line, "Talked while focused");
            }
            case WARY -> {
                String line;
                if (!eventSubject.isBlank()) line = LWLang.speechKey("dialogue.social.context.wary.subject." + (close ? "close" : "normal"),
                        close ? "Stay near me, but keep your eyes open. What happened around %s still doesn't feel settled."
                                : "I'm watching the area after what happened around %s. I'd rather look paranoid than get surprised.", eventSubject);
                else if (fighter.level().isThundering()) line = LWLang.speechKey("dialogue.social.context.wary.storm");
                else if (!familiar) line = LWLang.speechKey("dialogue.social.context.wary.unfamiliar");
                else line = LWLang.speechKey("dialogue.social.context.wary.cause", "Something about %s has me checking the same angles twice. Stay alert.", cause);
                yield new Conversation(line, "Talked while on guard");
            }
            case IRRITATED -> {
                String line;
                String state = close ? "close" : companion ? "companion" : "normal";
                line = LWLang.speechKey("dialogue.social.context.irritated." + state,
                        state.equals("close") ? "I'm irritated about %s. I'm not angry at you; I just don't have much patience right now."
                                : state.equals("companion") ? "I'm irritated about %s. Give me a little space and I'll be fine."
                                : "I'm irritated because of %s. I'm really not in the mood for small talk.", cause);
                yield new Conversation(line, "Talked while irritated");
            }
            case SOMBER -> {
                String subject = "ALLY_DIED".equals(eventType) && !eventSubject.isBlank() ? eventSubject : "";
                String line;
                if (!subject.isBlank()) line = LWLang.speechKey("dialogue.social.context.somber.subject." + (close ? "close" : "normal"),
                        close ? "I'm still thinking about %s. Seeing them fall hit me harder than I expected."
                                : "I'm not very talkative. %s is still on my mind.", subject);
                else line = LWLang.speechKey("dialogue.social.context.somber.cause." + (close ? "close" : "normal"),
                        close ? "I'm glad it's you. I've been quiet because of %s. I don't really want to pretend I'm fine."
                                : "I'm not very talkative right now because of %s. It's still weighing on me.", cause);
                yield new Conversation(line, "Talked while somber");
            }
            case WEARY -> {
                String lastOpponent = fighter.getLegacyData().getString("LastOpponent");
                String line;
                if (hurt) line = LWLang.speechKey("dialogue.social.context.weary.hurt");
                else if (!lastOpponent.isBlank() && fighter.level().getGameTime() - fighter.getLegacyData().getLong("LastBattle") < 9000L)
                    line = LWLang.speechKey("dialogue.social.context.weary.opponent", "That fight with %s took more out of me than I expected. I need to actually recover.", lastOpponent);
                else if (close) line = LWLang.speechKey("dialogue.social.context.weary.close");
                else line = LWLang.speechKey("dialogue.social.context.weary.normal");
                yield new Conversation(line, "Talked while weary");
            }
        };
    }

    private static String speechCause(String raw) {
        String key = raw == null || raw.isBlank() || "recent events".equals(raw) ? "recent_events"
                : "a quiet stretch".equals(raw) ? "quiet"
                : "debug mood test".equals(raw) ? "current_feeling"
                : "their injuries".equals(raw) ? "injuries"
                : "their faction".equals(raw) ? "faction"
                : "the fight in front of them".equals(raw) ? "fight"
                : "their head".equals(raw) ? "thoughts" : null;
        return key == null ? raw : LWLang.speechKey("dialogue.social.cause." + key);
    }

    private static String activityArgument(String activity) {
        if (activity == null || activity.isBlank()) return "";
        String key = activity.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        return LWLang.speechKey("dialogue.social.activity_name." + key);
    }

    private static String goalArgument(AmbientFighterEntity fighter) {
        String type = FighterGoalManager.currentType(fighter).toLowerCase(java.util.Locale.ROOT);
        return LWLang.speechKey("dialogue.social.goal_name." + (type.isBlank() ? "training" : type));
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isBlank()) return "what I was doing";
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String hostileLine(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isWorldMenace(fighter)) return WorldMenaceManager.hostileLine(fighter);
        return switch (fighter.getPersonality()) {
            case HEROIC -> LWLang.speechKey("dialogue.social.hostile.heroic");
            case CALM -> LWLang.speechKey("dialogue.social.hostile.calm");
            case PROUD -> LWLang.speechKey("dialogue.social.hostile.proud");
            case AGGRESSIVE -> LWLang.speechKey("dialogue.social.hostile.aggressive");
            case CAUTIOUS -> LWLang.speechKey("dialogue.social.hostile.cautious");
        };
    }
}
