package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Natural social contact: conversation introduces people, while shared experiences build real trust. */
public final class FighterSocialManager {
    private FighterSocialManager() {}

    private record Conversation(String line, String memory) {}

    public static void talk(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive()) return;
        if (FactionRequestMissionManager.isRequestActionLocked(fighter)) {
            player.displayClientMessage(Component.literal("[Living World] " + fighter.getFighterName()
                    + " is busy with an active faction request."), false);
            return;
        }
        if (!LivingWorldConfig.socialTalk()) {
            player.displayClientMessage(Component.literal("[Living World] Talk interactions are disabled by the world configuration."), false);
            return;
        }
        if (fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating() || fighter.isSanctionedMatchParticipant()) {
            player.displayClientMessage(Component.literal("[Living World] This is not a good moment to talk."), false);
            return;
        }
        if (WorldMenaceManager.isWorldMenace(fighter)) {
            player.displayClientMessage(Component.literal("[Living World] World Menaces do not answer social prompts.").withStyle(ChatFormatting.DARK_GRAY), false);
            return;
        }

        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
        int relationship = fighter.isRememberedFor(player) ? fighter.getMemoryRelationship() : 0;
        boolean trustedDespiteAlignment = fighter.getAlignment() == com.dmzlivingworld.entity.FighterAlignment.GOOD
                && PlayerAlignmentBridge.alignment(player) <= 32 && relationship >= 45;
        if (fighter.getTarget() == player || (disposition == FighterRelationshipManager.Disposition.HOSTILE && !trustedDespiteAlignment) || relationship <= -35) {
            fighter.speak(hostileLine(fighter), 66);
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(fighter.getFighterName() + " isn't willing to talk to you.").withStyle(ChatFormatting.RED)), false);
            return;
        }
        // Cooldown is a hard availability gate, not another social attempt. Check it before
        // the anti-pestering tracker so harmless clicks during cooldown cannot manufacture irritation.
        long talkCooldownRemaining = FighterMemoryManager.socialContactCooldownRemaining(player, fighter);
        if (talkCooldownRemaining > 0L) {
            long seconds = Math.max(1L, talkCooldownRemaining / 20L);
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You've already caught up with " + fighter.getFighterName()
                            + " recently • another meaningful conversation in about " + seconds + "s.").withStyle(ChatFormatting.GRAY)), false);
            return;
        }

        String refusal = ReactiveInteractionManager.talkRefusal(player, fighter, relationship);
        if (refusal != null) {
            fighter.speak(refusal, 90);
            ReactiveWorldManager.rememberEvent(fighter, "DECLINED_TALK", player.getGameProfile().getName(),
                    "did not want to talk while " + ReactiveWorldManager.mood(fighter).label().toLowerCase(java.util.Locale.ROOT));
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(fighter.getFighterName() + " doesn't want to talk right now.").withStyle(ChatFormatting.GRAY)), false);
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
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You've already caught up with " + fighter.getFighterName()
                            + " recently • another meaningful conversation in about " + seconds + "s.").withStyle(ChatFormatting.GRAY)), false);
            return;
        }

        fighter.speak(conversation.line(), 88);
        ReactiveWorldManager.onPlayerTalk(fighter, result.relationship());

        String stage = FighterRelationshipManager.relationshipStage(result.relationship());
        int before = result.relationship() - result.gained();
        String previousStage = FighterRelationshipManager.relationshipStage(before);
        if (result.gained() > 0 && !stage.equals(previousStage)) {
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(fighter.getFighterName() + " now sees you as " + stage + ".")
                            .withStyle(ChatFormatting.GREEN)), false);
        } else if (result.gained() > 0) {
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("The conversation goes well • " + stage + ".")
                            .withStyle(ChatFormatting.GRAY)), false);
        } else {
            player.displayClientMessage(Component.literal("[Living World] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Talking keeps the connection alive, but deeper trust with " + fighter.getFighterName()
                            + " now depends on what you actually do together.").withStyle(ChatFormatting.GRAY)), false);
        }
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
                case PROTECTIVE -> new Conversation("Stay close if things get ugly. I'll cover you.", "Caught up while travelling together");
                case LOYAL -> new Conversation("I'm still with you. Where are we heading next?", "Caught up while travelling together");
                case DISCIPLINED -> new Conversation("We should train again when we have a quiet moment.", "Discussed training while travelling");
                case RESPECT_DRIVEN -> new Conversation("You've been keeping pace. Good.", "Discussed progress while travelling");
                case COMPETITIVE -> new Conversation("Don't think travelling together means I'll go easy on you next time.", "Teased each other while travelling");
                case OPEN -> new Conversation("It's nice not having to travel alone.", "Caught up while travelling together");
                case GUARDED -> new Conversation("I don't travel with just anyone. Remember that.", "Acknowledged their travelling trust");
                case PRAGMATIC -> new Conversation("We work well together. That's worth keeping.", "Discussed working together");
            };
        }

        if (fighter.getHealth() < fighter.getMaxHealth() * 0.45F) {
            return switch (fighter.getPersonality()) {
                case HEROIC -> new Conversation("I'm all right. I just need a little time to recover.", "Checked on their recovery");
                case CALM -> new Conversation("Nothing serious. I'm letting my body catch up.", "Checked on their recovery");
                case PROUD -> new Conversation("I've had worse. Don't make a big deal out of it.", "Checked on their recovery");
                case AGGRESSIVE -> new Conversation("I'm fine. I just need another minute.", "Checked on their recovery");
                case CAUTIOUS -> new Conversation("I'm taking it easy until I'm back at full strength.", "Checked on their recovery");
            };
        }

        if (fighter.wasRescuedByMemoryOwner() && relationship < 35) {
            return new Conversation("I haven't forgotten that you helped me when I needed it.", "Talked about the earlier rescue");
        }

        long lastBattle = legacy.getLong("LastBattle");
        String lastOpponent = legacy.getString("LastOpponent");
        if (!lastOpponent.isBlank() && lastBattle > 0L && fighter.level().getGameTime() - lastBattle < 12000L) {
            return switch (fighter.getPersonality()) {
                case HEROIC -> new Conversation("That fight with " + lastOpponent + " is still on my mind. I learned from it.", "Reflected on the recent fight with " + lastOpponent);
                case CALM -> new Conversation("I've been thinking through my fight with " + lastOpponent + ". There were mistakes I can fix.", "Reflected on the recent fight with " + lastOpponent);
                case PROUD -> new Conversation("Next time I face " + lastOpponent + ", it'll go differently.", "Reflected on the recent fight with " + lastOpponent);
                case AGGRESSIVE -> new Conversation("I'd fight " + lastOpponent + " again right now.", "Reflected on the recent fight with " + lastOpponent);
                case CAUTIOUS -> new Conversation("That fight with " + lastOpponent + " showed me what I need to watch for.", "Reflected on the recent fight with " + lastOpponent);
            };
        }

        String goal = FighterGoalManager.summary(fighter);
        if (!"none".equals(goal)) {
            if (goal.startsWith("Learn ") || goal.startsWith("Complete ") || goal.startsWith("Advance ")) {
                return new Conversation("I've been focused on training. I want the next step to be earned.", "Talked about current training goals");
            }
            if (goal.startsWith("Acquire ")) {
                return new Conversation("I'm still looking for equipment that actually suits how I fight.", "Talked about finding better equipment");
            }
            if (goal.startsWith("Defeat ") || goal.startsWith("Win ")) {
                return new Conversation("I need a serious fight soon. That's the only way I'll know if I've improved.", "Talked about seeking a serious challenge");
            }
        }
        if (!fighter.getRivalName().isBlank()) {
            return new Conversation("I haven't lost track of " + fighter.getRivalName() + ". That rivalry keeps me moving.", "Talked about rival " + fighter.getRivalName());
        }
        if (fighter.isFactionMember()) {
            String faction = fighter.getFactionDisplayName().isBlank() ? "my faction" : fighter.getFactionDisplayName();
            return switch (fighter.getPersonality()) {
                case HEROIC -> new Conversation("I still have responsibilities with " + faction + ". People depend on us.", "Talked about responsibilities in " + faction);
                case CALM -> new Conversation("Things with " + faction + " have been steady lately.", "Talked about life in " + faction);
                case PROUD -> new Conversation(faction + " expects strength. I intend to represent it properly.", "Talked about standing in " + faction);
                case AGGRESSIVE -> new Conversation("If " + faction + " needs a fighter, they know where to find me.", "Talked about fighting for " + faction);
                case CAUTIOUS -> new Conversation("I'm keeping an eye on things around " + faction + ". Quiet doesn't always mean safe.", "Talked about security in " + faction);
            };
        }

        if (relationship >= 15) {
            return switch (FighterRelationshipManager.socialStyle(fighter)) {
                case PROTECTIVE -> new Conversation("Watch yourself out there. Trouble travels fast.", "Checked in on each other");
                case LOYAL -> new Conversation("It's good seeing a familiar face again.", "Caught up as familiar faces");
                case DISCIPLINED -> new Conversation("Consistency matters more than one good day.", "Talked about consistency");
                case RESPECT_DRIVEN -> new Conversation("Keep getting stronger. I notice effort.", "Talked about progress");
                case COMPETITIVE -> new Conversation("Next time, let's see who improved more.", "Compared recent progress");
                case OPEN -> new Conversation("I'm glad we actually get to talk sometimes.", "Caught up personally");
                case GUARDED -> new Conversation("You're more reliable than I first thought.", "Acknowledged growing trust");
                case PRAGMATIC -> new Conversation("You seem useful to have around. That's a compliment.", "Talked about working well together");
            };
        }
        return switch (fighter.getPersonality()) {
            case HEROIC -> new Conversation("Take care out there. Not everyone will warn you first.", "Had an introductory conversation");
            case CALM -> new Conversation("Sometimes talking tells you more than fighting.", "Had an introductory conversation");
            case PROUD -> new Conversation("If you're serious about getting stronger, prove it over time.", "Talked about proving yourself over time");
            case AGGRESSIVE -> new Conversation("I'm not much for talking. Fight me sometime.", "Talked about sparring sometime");
            case CAUTIOUS -> new Conversation("I don't know you well yet. Give it time.", "Had a cautious introductory conversation");
        };
    }

    private static Conversation scientistConversation(AmbientFighterEntity fighter) {
        if (!FighterScientistManager.isScientist(fighter) || fighter.getRandom().nextFloat() >= 0.82F) return null;
        int formula = FighterScientistManager.formulaProgress(fighter);
        int seeds = FighterScientistManager.availableSeeds(fighter);
        int maxSeeds = FighterScientistManager.maxSeeds(fighter);
        String[] lines = {
                "The latest Saibaman batch is stronger, but I'm more interested in whether the growth curve stays stable.",
                "I've been comparing combat data against the cultivation ratio. Tiny changes are producing annoyingly large behavioral differences.",
                "I have " + seeds + " viable seed" + (seeds == 1 ? "" : "s") + " out of " + maxSeeds + ". I'd rather grow fewer useful specimens than waste a whole batch.",
                "Formula refinement is at " + formula + " out of six. Past this point, raw power matters less than consistency.",
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
        };
        String line = lines[fighter.getRandom().nextInt(lines.length)];
        return new Conversation(line, "Talked about Saibaman research and experimental data");
    }

    private static Conversation reunionConversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        if (relationship < 15) return null;
        FighterMemoryManager.ReunionInfo info = FighterMemoryManager.reunionInfo(player, fighter);
        if (!info.qualifies()) return null;
        double current = PlayerWorldManager.playerBattlePower(player);
        boolean stronger = info.oldPlayerPower() > 0.0D && current >= info.oldPlayerPower() * 1.25D;
        String[] lines = stronger ? new String[]{
                "Good to see you again. Your Ki's changed—you've gotten stronger.",
                "There you are. I almost didn't recognize that power. You've been training.",
                "It's been a while. That increase in your Ki isn't subtle.",
                "Good seeing you again. You've definitely grown since last time."
        } : new String[]{
                "Good to see you again. It's been a while.",
                "There you are. I was wondering when we'd cross paths again.",
                "Been a while. Glad to see you're still moving.",
                "Hey. Good timing—it's actually nice seeing a familiar face again."
        };
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
        if (!activity.isBlank()) return new Conversation(name + " has been " + activity.toLowerCase(java.util.Locale.ROOT)
                + " lately. I notice what people around me are doing.", "Commented about " + name);
        if (bond >= 6) return new Conversation(name + " and I have gotten to know each other a little. They're good company.",
                "Commented warmly about " + name);
        if (other.getBattlePower() > fighter.getBattlePower() * 1.6D) return new Conversation(name
                + " is strong. You can feel it before they even start fighting.", "Commented on " + name + "'s strength");
        return new Conversation("I've seen " + name + " around here. Everybody has their own routine if you pay attention.",
                "Commented about " + name);
    }

    /** Avoid exact Talk-line loops even when the same contextual branch wins repeatedly. */
    private static Conversation freshConversation(AmbientFighterEntity fighter, Conversation selected) {
        if (selected == null) return new Conversation("Not much to say right now.", "Talked briefly");
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
            case UPBEAT -> new String[]{
                    "Things are actually going pretty well today.",
                    "I've got more energy than usual. Might as well use it.",
                    "Good day to be out doing something instead of standing around.",
                    "I don't know what changed, but I'm enjoying the day.",
                    "Feels like one of those days where training might actually be fun.",
                    "I'm in a good rhythm today. I want to keep it going.",
                    "Nothing's dragging me down right now. That's nice for a change.",
                    "I've been noticing the little things today. The world's not all fights and disasters."
            };
            case CONTENT -> new String[]{
                    "Nothing dramatic to report. I'm fine with that.",
                    "It's been calm enough to actually think for once.",
                    "I'm taking things one day at a time. It works.",
                    "A quiet stretch isn't wasted time.",
                    "I'm doing all right. No need to complicate it.",
                    "I've been enjoying not having somewhere urgent to be.",
                    "Sometimes ordinary is exactly what I want.",
                    "I'm just letting the day happen instead of forcing something out of it."
            };
            case FOCUSED -> new String[]{
                    "I've got something I'm working toward. I don't want to lose the thread.",
                    "My head's on training right now. Everything else can wait a little.",
                    "I'm trying to turn what I noticed into something I can actually use.",
                    "I finally know what I need to improve next.",
                    "I'm keeping my attention narrow today. It helps.",
                    "There's a difference between being busy and actually making progress.",
                    "I've been going over the same weakness until I understand it.",
                    "I'm not chasing every distraction. One thing at a time."
            };
            case WARY -> new String[]{
                    "I'm still watching the area. Something doesn't sit right with me.",
                    "Keep your senses open. I'm not convinced we're alone here.",
                    "I keep checking the same direction for a reason.",
                    "Maybe it's nothing, but I'd rather notice too much than too little.",
                    "I'm listening more than talking right now.",
                    "Don't mind me looking around. I'm keeping track of who comes close.",
                    "I'm not panicking. I'm paying attention.",
                    "Something has me on edge, so I'm keeping some distance until it passes."
            };
            case IRRITATED -> new String[]{
                    "I'm still annoyed. I'd rather not pretend otherwise.",
                    "My patience is thin right now, so keep it simple.",
                    "I need a little space before I say something I don't mean.",
                    "I'm trying to cool off. Talking isn't helping much yet.",
                    "Today has been getting on my nerves one thing at a time.",
                    "I'm not looking for an argument. That's why I'm keeping this short.",
                    "Give me time and I'll settle down. Right now I'm still wound up.",
                    "I'm handling it. I just don't want anyone pushing me while I do."
            };
            case SOMBER -> new String[]{
                    "I've got a lot on my mind. Quiet feels easier right now.",
                    "I'm here. I'm just not feeling very talkative.",
                    "Some things take longer to shake than a bad fight.",
                    "I don't really want to fake being cheerful today.",
                    "I'm taking the day slowly. That's about all I can manage right now.",
                    "I keep drifting back into the same thoughts.",
                    "I'll be all right. I just need some time with my own head.",
                    "Not every bad feeling needs a fight to solve it."
            };
            case WEARY -> new String[]{
                    "I'm running low. I need a proper rest more than another challenge.",
                    "Everything feels heavier when you're this tired.",
                    "I'm trying not to spend energy I don't have.",
                    "I could sleep for a week. Maybe two days if we're being realistic.",
                    "My body is telling me to stop, and for once I'm listening.",
                    "I'm still moving, just not quickly.",
                    "I need food, rest, and about half as much excitement as usual.",
                    "I'll have more to say when I don't feel like my Ki is running on fumes."
            };
        };
    }

    private static Conversation situationConversation(ServerPlayer player, AmbientFighterEntity fighter, int relationship) {
        String activity = FighterAmbientActivityManager.currentActivity(fighter);
        ReactiveWorldManager.Mood mood = ReactiveWorldManager.mood(fighter);
        if (!activity.isBlank()) {
            if (activity.startsWith("Dancing")) return new Conversation(
                    mood == ReactiveWorldManager.Mood.UPBEAT ? "Caught me at a good moment. I'm just enjoying myself."
                            : "Yeah, I'm taking a minute to move around and clear my head.",
                    "Talked during " + activity);
            if (activity.equals("Resting")) return new Conversation(
                    mood == ReactiveWorldManager.Mood.WEARY ? "I'm resting because I actually need it. Give me a minute to get my energy back."
                            : mood == ReactiveWorldManager.Mood.SOMBER ? "I'm just sitting quietly for a bit. That's what I need right now."
                            : "I'm taking a short break before I move again.",
                    "Talked while resting");
            if (activity.equals("Sitting")) return new Conversation(
                    fighter.getRandom().nextBoolean() ? "I'm just sitting for a bit. No big reason—I felt like stopping."
                            : "Sometimes it's nice to stay in one place without calling it training or recovery.",
                    "Talked while sitting");
            if (activity.equals("Jogging")) return new Conversation(
                    fighter.getRandom().nextBoolean() ? "Just a light run. I don't need to turn every workout into a crater."
                            : "I'm keeping moving. A jog is good when I want to clear my head.",
                    "Talked during a jog");
            if (activity.equals("Training")) return new Conversation(
                    fighter.getRandom().nextBoolean() ? "I'm actually training right now. I want the next improvement to be earned."
                            : "Working the basics again. Real power comes from doing this when nobody's watching too.",
                    "Talked during real training");
            if (activity.equals("Inspecting a flower")) return new Conversation(
                    fighter.getRandom().nextBoolean() ? "I saw this and got curious. Not everything interesting has a battle power."
                            : "You spend enough time looking for opponents and you start missing little things like this.",
                    "Talked while inspecting a flower");
            if (activity.equals("Taking an apple break")) return new Conversation(
                    fighter.getRandom().nextBoolean() ? "Found a tree and decided an apple sounded better than another Senzu."
                            : "Just grabbing something to eat before I keep moving.",
                    "Talked during an apple break");
            if (activity.equals("Looking around")) return new Conversation(
                    mood == ReactiveWorldManager.Mood.WARY ? "I'm checking the area. Something still feels wrong, so keep your eyes open."
                            : "I'm checking the area before I settle down. I'd rather know what's nearby.",
                    "Talked while scouting");
            if (activity.equals("Fishing")) return new Conversation(
                    mood == ReactiveWorldManager.Mood.IRRITATED ? "I'm trying to fish because I need some quiet. What is it?"
                            : "I'm fishing for a bit. It's easier to think when nobody is throwing Ki blasts around.",
                    "Talked while fishing");
            if (activity.equals("Stargazing")) return new Conversation(
                    mood == ReactiveWorldManager.Mood.SOMBER ? "I'm looking at the sky because I don't really want noise around me right now."
                            : "I'm just watching the sky for a while. It's peaceful up there.",
                    "Talked while stargazing");
            if (activity.equals("Flying")) return new Conversation("I'm moving right now. Catch me when I'm back on the ground.", "Talked during a flight");
        }

        String event = ReactiveWorldManager.recentEventType(fighter, 2600L);
        String subject = ReactiveWorldManager.recentEventSubject(fighter, 2600L);
        if (event.isBlank()) return null;
        return switch (event) {
            case "SPAR_LOSS" -> new Conversation(
                    mood == ReactiveWorldManager.Mood.IRRITATED
                            ? "Yeah, I lost that spar to " + (subject.isBlank() ? "you" : subject) + ". I'm still annoyed about it, so don't rub it in."
                            : "I'm still replaying that spar with " + (subject.isBlank() ? "you" : subject) + " in my head. I know where I slipped.",
                    "Talked about the recent spar loss");
            case "SPAR_WIN" -> new Conversation(
                    fighter.getPersonality() == com.dmzlivingworld.entity.FighterPersonality.PROUD
                            ? "That spar went the way I expected. Next time, make me work harder."
                            : "That was a good spar. Winning doesn't mean there wasn't anything to learn from it.",
                    "Talked about the recent spar win");
            case "ALLY_DIED" -> new Conversation(
                    subject.isBlank() ? "Someone close to us went down. I'm still processing it."
                            : "I'm still thinking about " + subject + ". Seeing them fall doesn't just disappear because the fight ended.",
                    "Talked about a fallen ally");
            case "ENEMY_DIED" -> new Conversation(
                    subject.isBlank() ? "The enemy is down, but I'm not dropping my guard yet."
                            : subject + " is down. I'm still watching in case that wasn't the end of it.",
                    "Talked about a defeated enemy");
            case "HORN_RALLY" -> new Conversation("That horn wasn't for show. When it sounds, everyone is supposed to move together.", "Talked about the faction rally");
            case "BOUNDARY_BROKEN" -> new Conversation("I asked for space and you kept pushing. I'm still irritated about that.", "Talked after a boundary was ignored");
            case "TOOK_SPACE" -> new Conversation("I moved because I needed distance. I meant it.", "Talked after taking space");
            case "MOB_SEEN" -> new Conversation(subject.isBlank() ? "I'm keeping an eye on what wandered into the area."
                    : "I noticed that " + subject.toLowerCase(java.util.Locale.ROOT) + " nearby. I'm keeping it in mind.", "Talked about something nearby");
            case "WORLD_CONDITION" -> new Conversation(subject.isBlank() ? "The conditions around here changed. I'm adjusting to it."
                    : "I'm paying attention to " + subject + ". It changes how I move around here.", "Talked about the current conditions");
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
                String line = companion
                        ? "I'm in a good mood today. Travelling with you is actually helping."
                        : close ? "Good timing. I'm feeling good, and it's nice seeing you."
                        : "I'm in a good mood today. Might as well enjoy it while it lasts.";
                yield new Conversation(line, "Talked while feeling upbeat");
            }
            case CONTENT -> new Conversation(
                    familiar ? "I'm doing all right. Things feel calm for once, and I'm happy to keep it that way."
                            : "I'm doing fine. Nothing is pressing on me right now.",
                    "Talked during a calm stretch");
            case FOCUSED -> {
                String line;
                if (!"none".equals(goal)) line = "I'm trying to stay focused on " + lowerFirst(goal) + ". I don't want to lose that momentum.";
                else line = "I'm focused right now because of " + cause + ". That's what has my attention.";
                yield new Conversation(line, "Talked while focused");
            }
            case WARY -> {
                String line;
                if (!eventSubject.isBlank()) line = close
                        ? "Stay near me, but keep your eyes open. What happened around " + eventSubject + " still doesn't feel settled."
                        : "I'm watching the area after what happened around " + eventSubject + ". I'd rather look paranoid than get surprised.";
                else if (fighter.level().isThundering()) line = "I don't like this storm. Too much noise, too many places for something to move unnoticed.";
                else if (!familiar) line = "Nothing personal, but I'm keeping track of everyone around me right now. You included.";
                else line = "Something about " + cause + " has me checking the same angles twice. Stay alert.";
                yield new Conversation(line, "Talked while on guard");
            }
            case IRRITATED -> {
                String line;
                if (close) line = "I'm irritated about " + cause + ". I'm not angry at you; I just don't have much patience right now.";
                else if (companion) line = "I'm irritated about " + cause + ". Give me a little space and I'll be fine.";
                else line = "I'm irritated because of " + cause + ". I'm really not in the mood for small talk.";
                yield new Conversation(line, "Talked while irritated");
            }
            case SOMBER -> {
                String subject = "ALLY_DIED".equals(eventType) && !eventSubject.isBlank() ? eventSubject : "";
                String line;
                if (!subject.isBlank()) line = close
                        ? "I'm still thinking about " + subject + ". Seeing them fall hit me harder than I expected."
                        : "I'm not very talkative. " + subject + " is still on my mind.";
                else line = close
                        ? "I'm glad it's you. I've been quiet because of " + cause + ". I don't really want to pretend I'm fine."
                        : "I'm not very talkative right now because of " + cause + ". It's still weighing on me.";
                yield new Conversation(line, "Talked while somber");
            }
            case WEARY -> {
                String lastOpponent = fighter.getLegacyData().getString("LastOpponent");
                String line;
                if (hurt) line = "I'm exhausted and I'm still hurting. I'm done pretending another fight right now would be smart.";
                else if (!lastOpponent.isBlank() && fighter.level().getGameTime() - fighter.getLegacyData().getLong("LastBattle") < 9000L)
                    line = "That fight with " + lastOpponent + " took more out of me than I expected. I need to actually recover.";
                else if (close) line = "I'm worn out. I can talk, but if I start slowing down, that's why. I need a proper rest soon.";
                else line = "I'm low on energy. I'm keeping things short until I've had time to recover.";
                yield new Conversation(line, "Talked while weary");
            }
        };
    }

    private static String speechCause(String raw) {
        if (raw == null || raw.isBlank() || "recent events".equals(raw)) return "what's been happening lately";
        if ("a quiet stretch".equals(raw)) return "things being quiet lately";
        if ("debug mood test".equals(raw)) return "the way I'm feeling right now";
        return raw.replace("their injuries", "my injuries")
                .replace("their faction", "my faction")
                .replace("the fight in front of them", "the fight in front of me")
                .replace("their head", "my head");
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isBlank()) return "what I was doing";
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String hostileLine(AmbientFighterEntity fighter) {
        if (WorldMenaceManager.isWorldMenace(fighter)) return WorldMenaceManager.hostileLine(fighter);
        return switch (fighter.getPersonality()) {
            case HEROIC -> "Not while we're on opposite sides.";
            case CALM -> "There isn't anything to discuss right now.";
            case PROUD -> "You've lost the right to small talk.";
            case AGGRESSIVE -> "Talk? Try surviving first.";
            case CAUTIOUS -> "Keep your distance.";
        };
    }
}
