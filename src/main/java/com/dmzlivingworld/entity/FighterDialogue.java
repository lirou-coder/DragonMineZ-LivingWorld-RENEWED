package com.dmzlivingworld.entity;

import com.dmzlivingworld.world.FighterGoalManager;
import net.minecraft.util.RandomSource;

/** Sparse overhead dialogue tied to visible fight events, never normal chat spam. */
public final class FighterDialogue {
    private FighterDialogue() {}

    private static final String[] OPEN_GOOD = {"Back off.", "Leave them alone.", "I'm stopping this.", "You picked the wrong target.", "That's enough. Step away.", "You don't get to hurt people here.", "Try me instead.", "I'm not letting this continue.", "Put them down and walk away.", "You've done enough.", "Find someone else to threaten.", "I'm giving you one chance to stop.", "They're not fighting alone anymore.", "That's over. Right now.", "If you want a fight, you found one.", "Step away before this gets worse.", "Pick on someone who can answer back.", "You stop here.", "I saw enough. Back away.", "Not another step.", "Whatever this was, it ends with me.", "You can leave now or I can make you."};
    private static final String[] OPEN_NEUTRAL = {"All right.", "Show me your technique.", "Let's see what you've got.", "Come on, then.", "No excuses. Show me where you stand.", "Let's make this worth the energy.", "I want to see how you fight.", "Fine. We settle it here.", "Let's test each other properly.", "All right. No cheap shots.", "I've been wanting a real match.", "Let's see what your habits are.", "I won't learn anything if you hold back.", "Show me how you handle pressure.", "Let's settle this cleanly.", "I want a proper measure of your strength.", "Let’s see how you manage distance.", "Show me what you do under pressure.", "I want a clean exchange.", "No audience needed. Just fight.", "Let’s find the holes in each other’s style.", "Don’t waste the first opening."};
    private static final String[] OPEN_BAD = {"Wrong place to wander.", "You look easy.", "Try to keep up.", "This won't take long.", "You should have kept walking.", "Let's see what you're worth.", "I was getting bored anyway.", "Bad timing for you.", "You really came this close on purpose?", "I was hoping someone would try me.", "You're about to regret stopping here.", "Let's make this quick.", "You should've taken the long way around.", "I don't need another warning.", "Good. I needed something to hit.", "Let's find out how brave you actually are.", "I was hoping you’d resist.", "You wandered into my afternoon.", "Good. I wanted a reason.", "Don’t make this boring.", "You should’ve noticed me sooner.", "Let’s see how long that confidence lasts."};
    private static final String[] OPEN_REPLY = {"Enough talk.", "Then come on.", "We'll see.", "Don't blink.", "Show me.", "I'm ready.", "Make your move.", "Then stop talking and fight.", "I heard you.", "Then let's do this.", "No more speeches.", "Fine by me.", "I won't hesitate.", "I've been ready.", "Come find out.", "Words are finished.", "All right. First move is yours.", "You wanted my attention. You have it.", "Good. Keep that confidence.", "I’m done waiting.", "Then we settle it now.", "Move."};

    private static final String[] CHARGE_REACT = {"Charging now?", "I won't give you time!", "You're wide open.", "Bad idea.", "I can feel your Ki rising.", "You really think I'll just watch?", "That opening is mine.", "Powering up in front of me? Bold.", "You're really giving me that opening?", "Your Ki's climbing fast.", "I know what comes after that.", "I can feel the pressure changing.", "Don't expect me to stand still.", "That's a lot of power to announce.", "So you're raising the stakes.", "I see what you're doing.", "Your guard dropped the second you started charging.", "That Ki spike is impossible to miss.", "I’m not giving you a free charge.", "You’re committing hard to that power-up.", "The pressure just changed completely.", "I felt that from here."};
    private static final String[] CHARGE_REACT_PROUD = {"Go ahead.", "Power up. I don't care.", "Show me everything.", "Don't hold back.", "Good. Make yourself worth beating.", "Higher. I know you have more.", "That's still not enough.", "Now show me the part you were hiding."};
    private static final String[] CHARGE_REACT_CALM = {"So that's your answer.", "I see.", "Then I'll respond.", "Take your time.", "Your breathing changed.", "The pressure is building.", "I'll account for it.", "Finish. Then we continue."};
    private static final String[] MIRROR_CHARGE = {"Then I'll raise mine too.", "Fine. Together, then.", "Let's see whose Ki breaks first.", "I can do that too.", "If you're raising the stakes, so am I.", "All right. No more holding back.", "Then feel mine rise too.", "We'll both show our hands.", "Then I stop suppressing mine.", "You raise yours, I raise mine.", "Fine. We'll meet at full power.", "Let's make the air shake together.", "I was waiting for a reason.", "Then neither of us holds back.", "I'll answer that pressure with my own.", "Good. This is the level I wanted.", "Then I’ll stop hiding mine.", "You want full pressure? Fine.", "I was holding this back for a reason.", "Let’s see which one of us controls it better.", "No suppression, then.", "Good. Now it’s a real comparison."};
    private static final String[] LET_CHARGE = {"Go on. Power up.", "I'll wait.", "Show me your full strength.", "Don't disappoint me.", "Take the time. I want the real fight.", "Raise it as high as you can.", "I'm not interested in beating half your power.", "Finish charging. Then we start properly.", "Take another second. You'll need it.", "Finish what you started.", "I want to know your real ceiling.", "No shortcuts. Full power.", "I'll give you the chance.", "Show me what all that Ki is for.", "Get ready properly.", "I'd rather beat your best.", "Take your time. I want an honest result.", "Finish charging. I won’t interrupt it.", "Show me what you were saving.", "I’d rather know exactly where your limit is.", "Get all of it out first.", "Good. Bring the power up properly."};

    private static final String[] CAST_REACT = {"Too obvious!", "I see it!", "Not that easy.", "Here it comes...", "I felt that technique start!", "There—your hands gave it away!", "I know that setup.", "I'm moving!"};
    private static final String[] CAST_REACT_AGGRESSIVE = {"Too slow!", "I'll break through it!", "Try it!", "You're mine!", "I'll hit you before it fires!", "Not a chance!", "You won't finish that!", "Try casting through this!"};
    private static final String[] CAST_REACT_CALM = {"There it is.", "I was waiting for that.", "Predictable.", "Now.", "The release is coming.", "I know the timing.", "You telegraphed it.", "That's the cue."};

    private static final String[] MIDFIGHT = {"Not bad.", "You're tougher than you look.", "Good hit.", "Now we're fighting.", "You adjusted fast.", "That nearly caught me.", "Good. Keep that pressure.", "You're making me work for this.", "Nice recovery.", "You changed the angle.", "That was cleaner.", "You're reading me too.", "I felt that one.", "Okay, that was smart.", "You're still getting faster.", "This is getting interesting.", "You’re changing the tempo.", "That guard is tighter now.", "You learned from the last exchange.", "Good feint.", "You keep finding a way back in.", "That footwork is getting annoying—in a good way."};
    private static final String[] MIDFIGHT_PROUD = {"Finally. Something interesting.", "That's more like it.", "You might entertain me yet.", "Don't slow down now.", "Better. Keep earning my attention.", "There you are.", "Now you're starting to look dangerous.", "Don't waste that momentum."};
    private static final String[] MIDFIGHT_CALM = {"Your rhythm changed.", "I understand your style now.", "You're adapting.", "Good.", "Your spacing improved.", "You're hiding the next move better.", "That adjustment worked.", "I won't give you that angle again."};
    private static final String[] LOW_HEALTH = {"I'm not done yet.", "Still standing.", "One more round.", "Tch...", "I can keep going.", "Not finished.", "My legs still work. That's enough.", "I've got more in me.", "I can still move.", "I've fought through worse.", "I'm not dropping here.", "Just need one opening.", "My body can complain later.", "I still have enough.", "Not yet.", "Keep coming.", "Breathing hurts. Still fighting.", "I only need one clean counter.", "I can work with this.", "Everything aches. Keep moving.", "Not enough left for mistakes.", "I’m still in this."};
    private static final String[] LOW_HEALTH_PROUD = {"I refuse to fall here.", "You haven't won anything yet.", "I'm still standing!", "Again!", "You think this is where I break?", "I haven't given you the win.", "Pain doesn't decide this.", "I'm getting back up every time."};
    private static final String[] LOW_HEALTH_CAUTIOUS = {"This is getting bad...", "I need an opening.", "Too much power...", "I can't trade hits like this.", "I need to stop trading blows.", "One mistake and I'm done.", "I have to change the pace.", "I need space before the next exchange."};

    private static final String[] RETREAT = {"This isn't worth dying for.", "I'm out.", "Another time.", "I know when I'm beaten.", "I'm leaving before this turns stupid.", "You've got this one.", "I need distance. Now.", "I'll survive first and argue later.", "I'm choosing tomorrow over pride.", "This fight is finished for me.", "I'm not dying to prove a point.", "I need to get out of here.", "Next time I come prepared.", "You've made your point.", "I'm breaking off.", "Live first. Settle it later.", "I’m not handing you my life for a rematch.", "I’ll remember this and come back smarter.", "That’s enough damage for one day.", "I’m cutting my losses.", "I’ve learned what I needed. I’m gone.", "This ends before it becomes a funeral."};
    private static final String[] DEFEAT = {"I yield.", "That's enough...", "You win.", "Damn...", "All right. I'm done.", "I can't keep this up.", "Fine... that's yours.", "Stop. I concede.", "I can't continue.", "That's it. You have it.", "I know when the fight is over.", "Enough. I surrender.", "My body's done.", "You've beaten me.", "I won't pretend I can keep going.", "All right... stop.", "I’m finished.", "No more. You’ve got it.", "I can’t answer another hit.", "That’s the fight.", "Enough—I know the result.", "I’m conceding before this gets stupid."};
    private static final String[] MAJOR = {"Take this!", "Try this one!", "Don't blink!", "Now!", "Here it comes!", "Eat this!", "This is the opening!", "Right there!"};
    private static final String[] MAJOR_PROUD = {"Survive this.", "Let's end the warm-up.", "Take my full power!", "Don't look away.", "Remember this attack.", "Now you see the difference.", "I'll finish this properly.", "This is what I was saving."};
    private static final String[] MAJOR_CALM = {"This decides it.", "Opening found.", "Now.", "Your guard is gone.", "The timing is right.", "This is the clean line.", "You can't cover that opening.", "I have you."};

    private static final String[] POWER_UP = {"HAAAAA!", "I'm raising my Ki!", "Then let's get serious.", "No more holding back.", "Feel that? I'm not done climbing.", "All right—full power!", "I've got more than this.", "Then I'll show you what I was saving.", "My Ki can still go higher!", "Enough restraint.", "I'm done conserving energy.", "Let's bring the real pressure.", "You wanted more—here.", "I'm opening everything up.", "This is where the warm-up ends.", "Feel the difference now."};
    private static final String[] POWER_UP_PROUD = {"Watch closely.", "This is my real power.", "Try not to collapse.", "I'll show you the difference.", "Now pay attention.", "This is the power you came for.", "Remember how this feels.", "Try to stand against this."};
    private static final String[] POWER_UP_REPLY = {"So you're finally serious.", "Good. Keep going.", "I can feel it rising...", "Now this is interesting.", "That pressure changed fast.", "There it is. That's what I was waiting for.", "Your Ki just jumped.", "Okay. Now I have your real measure.", "That changed the whole fight.", "So that's what you were hiding.", "Your pressure just doubled.", "I can feel that from here.", "Good. No more pretending.", "Now I know the real problem.", "That's a different fighter.", "There. That's your serious face."};
    private static final String[] POWER_READY = {"Come on!", "Now!", "Your turn.", "Let's continue.", "I'm ready now.", "Come test it.", "Let's finish the round.", "Continue."};

    private static final String[] AFTER_LAUNCH = {"Get up.", "We're not finished.", "Come back here!", "Too slow.", "Don't stay down there.", "I'm coming after you.", "That won't end it.", "Recover if you can."};
    private static final String[] AFTER_LAUNCH_PROUD = {"Get up. I'm not finished with you.", "That can't be all.", "Stand up.", "Don't disappoint me now.", "I expect you back on your feet.", "Don't make that the ending.", "You wanted my best. Stand up.", "We're not done until I say."};
    private static final String[] LAUNCH_REPLY = {"Still here.", "That hurt...", "Again!", "Not enough.", "I'm getting back up.", "You caught me once.", "Still breathing.", "That won't happen again."};
    private static final String[] LAUNCH_REPLY_CALM = {"I see the timing now.", "I won't take that twice.", "Understood.", "So that's the angle.", "I know where I lost balance.", "That setup won't work twice.", "I understand the follow-up now.", "I saw the mistake."};

    private static final String[] VICTORY_GOOD = {"That's enough.", "Stay down. It's over.", "We're done here.", "No more.", "It's finished. Don't force another round.", "I don't want to hurt you more than this.", "End it here.", "Walk away when you can.", "It's over. Stay safe and stay down.", "We don't need another hit.", "That's the end of it.", "Save your strength. We're finished.", "I'm stopping here.", "Don't turn this into something worse.", "Take the loss and recover.", "No one needs to die over this.", "That’s enough damage. Stay down and breathe.", "I’m not taking this any further.", "Recover first. Whatever comes next can wait.", "You’ve lost the fight, not your life.", "Don’t make me hit you again.", "It ends here. Get some rest."};
    private static final String[] VICTORY_NEUTRAL = {"Good fight.", "That's enough for me.", "You fought well.", "We'll stop here.", "Solid round. We're done.", "You made that worthwhile.", "I got what I needed from that fight.", "Call it there. Good effort.", "That was a useful fight.", "You gave me plenty to think about.", "Good work. Let's leave it there.", "That was worth the bruises.", "I learned something from that.", "Respect. We're done.", "Good pace. Good pressure.", "We'll compare notes another day.", "You corrected fast. Keep that.", "That was closer than it looked.", "Your timing gave me trouble.", "Good exchange. Recover well.", "You made me change plans twice.", "That was useful for both of us."};
    private static final String[] VICTORY_BAD = {"Pathetic.", "That's all?", "Stay down.", "You never had a chance.", "That was disappointing.", "Know your limit next time.", "I expected more resistance.", "You should have run when you had the chance.", "Remember where you belong.", "That went exactly how I expected.", "Next time don't waste my time.", "You were never in control.", "Stay out of my way.", "That's what happens when you challenge me.", "I barely needed the effort.", "Learn something from the floor.", "You made that easier than it needed to be.", "Next time, know what you’re walking into.", "That confidence didn’t survive long.", "You were outmatched from the start.", "Stay down before I lose patience.", "You should remember this result."};

    private static final String[] RESCUED = {"You came... thank you.", "I thought I was finished.", "You got me out. Thanks.", "I owe you one.", "I didn't think anyone was coming. Thank you.", "That was close. I won't forget this.", "You actually came back for me.", "I can breathe again. Thanks.", "You got here in time.", "I seriously owe you for that.", "I thought nobody saw what happened.", "Thanks. I wasn't getting out alone.", "That could've ended badly.", "You saved me a lot worse than a bruise.", "I won't forget who showed up.", "Give me a second. Then thank you properly."};
    private static final String[] CAPTIVE = {"Help!", "Frieza's soldiers have me pinned!", "Over here!", "Get these soldiers off me!", "I need help over here!", "They've got me surrounded!", "Hey! I could use a hand!", "Break their line and I can move!"};

    public static String opening(RandomSource random, FighterAlignment alignment) {
        return opening(random, alignment, FighterPersonality.CALM);
    }

    public static String opening(RandomSource random, FighterAlignment alignment, FighterPersonality personality) {
        if (personality == FighterPersonality.PROUD && random.nextBoolean()) {
            return pick(random, new String[]{"You can make the first move.", "Try to impress me.", "I hope you're worth this.", "Show me."});
        }
        if (personality == FighterPersonality.AGGRESSIVE && random.nextBoolean()) {
            return pick(random, new String[]{"I'm done talking!", "Come here!", "You're finished!", "Let's go!"});
        }
        return pick(random, switch (alignment) {
            case GOOD -> OPEN_GOOD;
            case NEUTRAL -> OPEN_NEUTRAL;
            case BAD -> OPEN_BAD;
        });
    }

    public static String openingReply(RandomSource random, FighterAlignment alignment, FighterPersonality personality) {
        if (personality == FighterPersonality.PROUD) return pick(random, new String[]{"You first.", "Don't regret saying that.", "Good.", "Then begin."});
        if (personality == FighterPersonality.AGGRESSIVE) return pick(random, new String[]{"Gladly!", "Enough!", "Here I come!", "Too late!"});
        return pick(random, OPEN_REPLY);
    }

    public static String chargeReaction(RandomSource random) { return pick(random, CHARGE_REACT); }
    public static String chargeReaction(RandomSource random, FighterPersonality personality) {
        return pick(random, switch (personality) {
            case PROUD -> CHARGE_REACT_PROUD;
            case CALM -> CHARGE_REACT_CALM;
            default -> CHARGE_REACT;
        });
    }
    public static String mirrorCharge(RandomSource random) { return pick(random, MIRROR_CHARGE); }
    public static String letThemCharge(RandomSource random) { return pick(random, LET_CHARGE); }

    public static String castReaction(RandomSource random) { return pick(random, CAST_REACT); }
    public static String castReaction(RandomSource random, FighterPersonality personality) {
        return pick(random, switch (personality) {
            case AGGRESSIVE -> CAST_REACT_AGGRESSIVE;
            case CALM -> CAST_REACT_CALM;
            default -> CAST_REACT;
        });
    }

    public static String midfight(RandomSource random) { return pick(random, MIDFIGHT); }
    public static String midfight(RandomSource random, FighterPersonality personality) {
        return pick(random, switch (personality) {
            case PROUD -> MIDFIGHT_PROUD;
            case CALM -> MIDFIGHT_CALM;
            default -> MIDFIGHT;
        });
    }

    public static String lowHealth(RandomSource random) { return pick(random, LOW_HEALTH); }
    public static String lowHealth(RandomSource random, FighterPersonality personality) {
        return pick(random, switch (personality) {
            case PROUD, AGGRESSIVE -> LOW_HEALTH_PROUD;
            case CAUTIOUS -> LOW_HEALTH_CAUTIOUS;
            default -> LOW_HEALTH;
        });
    }

    public static String retreat(RandomSource random) { return pick(random, RETREAT); }
    public static String defeat(RandomSource random) { return pick(random, DEFEAT); }

    public static String major(RandomSource random) { return pick(random, MAJOR); }
    public static String major(RandomSource random, FighterPersonality personality) {
        return pick(random, switch (personality) {
            case PROUD, AGGRESSIVE -> MAJOR_PROUD;
            case CALM -> MAJOR_CALM;
            default -> MAJOR;
        });
    }

    public static String powerUp(RandomSource random, FighterPersonality personality) {
        return pick(random, personality == FighterPersonality.PROUD ? POWER_UP_PROUD : POWER_UP);
    }
    public static String powerUpReply(RandomSource random, FighterPersonality personality) {
        if (personality == FighterPersonality.AGGRESSIVE) return pick(random, new String[]{"I won't let you!", "Too slow!", "I'm coming in!", "Not happening!"});
        if (personality == FighterPersonality.PROUD) return pick(random, new String[]{"Keep going.", "More.", "Is that all?", "Good. Don't stop."});
        return pick(random, POWER_UP_REPLY);
    }
    public static String powerReady(RandomSource random, FighterPersonality personality) { return pick(random, POWER_READY); }

    public static String afterLaunch(RandomSource random, FighterPersonality personality) {
        return pick(random, personality == FighterPersonality.PROUD ? AFTER_LAUNCH_PROUD : AFTER_LAUNCH);
    }
    public static String launchReply(RandomSource random, FighterPersonality personality) {
        return pick(random, personality == FighterPersonality.CALM ? LAUNCH_REPLY_CALM : LAUNCH_REPLY);
    }

    public static String victory(RandomSource random, FighterAlignment alignment, FighterPersonality personality) {
        return pick(random, switch (alignment) {
            case GOOD -> VICTORY_GOOD;
            case NEUTRAL -> VICTORY_NEUTRAL;
            case BAD -> VICTORY_BAD;
        });
    }


    /** Quiet NPC-to-NPC conversation. Lines are selected from facts that are actually true for the pair. */
    public static String npcSocialOpening(RandomSource random, AmbientFighterEntity speaker, AmbientFighterEntity other, int bond) {
        if (speaker == null || other == null) return "Good to see you.";
        if (bond >= 3 && random.nextFloat() < 0.075F) {
            return pick(random, new String[]{
                    "You know, for people who can fly, we spend a lot of time walking.",
                    "Quiet day. Suspiciously few craters.",
                    "I tried counting how many fights I've been in. I got bored and stopped.",
                    "If anyone asks, that crater was already there.",
                    "I heard someone say 'five minutes of training.' Funniest thing I've heard all week.",
                    "I swear every quiet walk eventually turns into somebody sensing a power level.",
                    "We should try having one normal afternoon. For research.",
                    "I found a perfectly good hill with no crater in it. Rare sight."
            });
        }
        if (speaker.isFactionMember() && other.isFactionMember() && speaker.getFactionId().equals(other.getFactionId())) {
            if (bond >= 6) return pick(random, new String[]{"Good to see you again.", "Everything quiet on your side?", "How's training been?"});
            return pick(random, new String[]{"Anything unusual nearby?", "Keep an eye on the area.", "You doing all right?"});
        }
        if (bond >= 4 && random.nextFloat() < 0.20F) {
            com.dmzlivingworld.world.FighterHobby hobby = com.dmzlivingworld.world.FighterHobby.of(speaker);
            return switch (hobby) {
                case COOKING -> "I tried cooking something new. It survived, so that's progress.";
                case STARGAZING -> "Sky should be clear tonight. Good night for stargazing.";
                case FISHING -> "I found a quiet fishing spot. Don't tell every fighter you meet.";
                case MUSIC -> "I've been listening for new rhythms between training sessions.";
                case MECHANICS -> "I fixed a piece of gear earlier. Took longer than the fight that broke it.";
                case MAPMAKING -> "I've added a few places to my maps. This world keeps changing.";
                case GARDENING -> "My plants are somehow still alive. I'm counting that as a win.";
                case TEA -> "I found a tea that actually helps after training.";
                case ROCK_COLLECTING -> "Found an unusual stone earlier. Completely ordinary. I like it anyway.";
                case CLOUD_WATCHING -> "I spent ten minutes watching clouds today. Highly recommended.";
                case MARTIAL_NOTES -> "I've been writing down a few things I noticed in fights.";
                case CARD_GAMES -> "We should play cards sometime. No Ki attacks over a bad hand.";
                case CAMPING -> "I could use a quiet campfire after all this.";
                case FASHION -> "I'm thinking of changing my outfit. We can't all dress like we're permanently mid-fight.";
                case BULGARIAN_FOLKLORE -> "I was reading Bulgarian mountain legends again. Those people knew how to tell a story.";
            };
        }
        String goal = FighterGoalManager.summary(speaker);
        if (goal.startsWith("Learn ") || goal.startsWith("Complete ") || goal.startsWith("Advance ")) {
            return pick(random, new String[]{"I've been putting more time into training.", "Still working toward my next step.", "Progress is slow, but it's progress."});
        }
        if (goal.startsWith("Acquire ")) return pick(random, new String[]{"I'm still looking for better equipment.", "Seen any useful gear around?", "I need to improve my equipment."});
        if (goal.startsWith("Defeat ") || goal.startsWith("Win ")) return pick(random, new String[]{"I've got another fight on my mind.", "I need a real test soon.", "I'm looking for a stronger challenge."});
        if (bond >= 8) return pick(random, new String[]{"It's been a while.", "Good timing. I was about to take a break.", "Nice seeing a familiar face."});
        return switch (speaker.getPersonality()) {
            case HEROIC -> pick(random, new String[]{"Everything okay around here?", "Need a hand with anything?", "Stay alert out there."});
            case CALM -> pick(random, new String[]{"Quiet day.", "Good time to clear your head.", "How have things been?"});
            case PROUD -> pick(random, new String[]{"Still training?", "You'd better not be getting rusty.", "Have you improved since last time?"});
            case AGGRESSIVE -> pick(random, new String[]{"Been in any good fights lately?", "I'm getting restless.", "Tell me something interesting happened."});
            case CAUTIOUS -> pick(random, new String[]{"Area seems safe enough.", "Anything I should know about?", "You've been okay?"});
        };
    }

    public static String npcSocialReply(RandomSource random, AmbientFighterEntity speaker, AmbientFighterEntity other, int bond) {
        if (speaker == null || other == null) return "Yeah.";
        if (bond >= 3 && random.nextFloat() < 0.07F) {
            return pick(random, new String[]{
                    "Walking builds character. Apparently.",
                    "Give it time. Someone will make a crater.",
                    "That sounds like a problem for your future self.",
                    "Sure. And the mountain moved itself.",
                    "Five minutes? That's how it starts.",
                    "A normal afternoon? I don't trust it.",
                    "Give the hill a day. Someone will crater it.",
                    "Research sounds suspiciously like avoiding training."
            });
        }
        if (bond >= 6) {
            return switch (speaker.getPersonality()) {
                case HEROIC -> "Good. Let's keep it that way.";
                case CALM -> "Yeah. It's nice when things slow down.";
                case PROUD -> "Of course. I haven't stopped improving.";
                case AGGRESSIVE -> "Not enough. I could use a real fight.";
                case CAUTIOUS -> "So far. I'm still keeping watch.";
            };
        }
        return switch (speaker.getPersonality()) {
            case HEROIC -> pick(random, new String[]{"I'm fine. You?", "All good here.", "Nothing I can't handle."});
            case CALM -> pick(random, new String[]{"Can't complain.", "Quiet is fine by me.", "I'm doing well."});
            case PROUD -> pick(random, new String[]{"Better than ever.", "Still sharp.", "Worry about yourself."});
            case AGGRESSIVE -> pick(random, new String[]{"Too quiet.", "I need something to happen.", "I've had worse days."});
            case CAUTIOUS -> pick(random, new String[]{"For now.", "Nothing strange yet.", "I'm keeping my eyes open."});
        };
    }

    public static String npcMeditationInvite(RandomSource random, FighterPersonality personality) {
        return switch (personality) {
            case CALM -> pick(random, new String[]{"Want to meditate for a while?", "Let's clear our heads for a bit.", "A short meditation?"});
            case HEROIC -> pick(random, new String[]{"Let's take a moment to focus.", "We should reset before moving on.", "Meditate with me for a bit?"});
            case CAUTIOUS -> pick(random, new String[]{"It's quiet enough to meditate.", "We have a safe moment. Let's use it.", "Let's focus while we can."});
            case PROUD -> pick(random, new String[]{"Try to keep your focus beside mine.", "Let's see how disciplined you are.", "Meditate. Don't fall behind."});
            case AGGRESSIVE -> pick(random, new String[]{"Fine. I need to cool off anyway.", "Let's focus before I get restless again.", "A minute. Then I need to move."});
        };
    }

    public static String senzuThanks(RandomSource random, FighterPersonality personality, boolean closeFriend) {
        if (closeFriend) return pick(random, new String[]{"Thanks. I needed that.", "You always come prepared. Thanks.", "I owe you one."});
        return switch (personality) {
            case HEROIC -> pick(random, new String[]{"Thank you. I'll put it to good use.", "Thanks. That helps a lot.", "I appreciate it."});
            case CALM -> pick(random, new String[]{"Thank you.", "I appreciate that.", "Good timing. Thanks."});
            case PROUD -> pick(random, new String[]{"...Thanks. I did need it.", "I'll accept it. Thank you.", "Don't make a habit of saving me. Thanks."});
            case AGGRESSIVE -> pick(random, new String[]{"Perfect. Thanks.", "Good. I can keep moving now.", "Thanks. That's exactly what I needed."});
            case CAUTIOUS -> pick(random, new String[]{"Thank you. I'll remember that.", "That's generous. Thanks.", "I appreciate it."});
        };
    }

    public static String npcMeditationReply(RandomSource random, FighterPersonality personality) {
        return switch (personality) {
            case CALM, HEROIC -> pick(random, new String[]{"All right.", "Let's do it.", "Good idea."});
            case CAUTIOUS -> pick(random, new String[]{"For a little while.", "All right. Stay alert afterward.", "Okay."});
            case PROUD -> pick(random, new String[]{"Don't distract me.", "Fine.", "Keep up."});
            case AGGRESSIVE -> pick(random, new String[]{"Yeah, fine.", "For a minute.", "All right."});
        };
    }

    /** Rare reflective lines used while the fighter is actually meditating. */
    public static String meditationWisdom(RandomSource random, FighterPersonality personality, boolean shared) {
        if (shared && random.nextFloat() < 0.42F) {
            return pick(random, new String[]{
                    "Don't match my breathing. Find your own rhythm.",
                    "Two people can share silence without thinking the same thoughts.",
                    "A calm mind makes another calm mind easier to find.",
                    "You can feel someone's Ki better when neither of you is forcing it.",
                    "Match the quiet, not my Ki. Let your own rhythm settle.",
                    "We don't have to speak for this to count as training."
            });
        }
        return switch (personality) {
            case CALM -> pick(random, new String[]{
                    "A quiet mind notices what power usually hides.",
                    "Breath first. Power follows.",
                    "Stillness is not doing nothing. It's learning what moves you.",
                    "If you chase every thought, none of them ever pass.",
                    "Breathe. Let the noise pass without chasing it."
            });
            case HEROIC -> pick(random, new String[]{
                    "Strength means more when you remember what you're protecting.",
                    "Power without purpose gets heavy fast.",
                    "A clear mind makes it easier to choose when not to fight.",
                    "Rest is part of protecting people too.",
                    "Strength is easier to use when your mind isn't fighting itself."
            });
            case PROUD -> pick(random, new String[]{
                    "Discipline is the part of strength nobody can give you.",
                    "Control proves more than noise ever will.",
                    "If I cannot command my own Ki, I have no right to boast about it.",
                    "Mastery starts where showing off stops.",
                    "Control first. Power leaking everywhere is just waste."
            });
            case AGGRESSIVE -> pick(random, new String[]{
                    "Stillness is harder than fighting. That's why I practice it.",
                    "Even a fighter needs to know when not to swing.",
                    "Anger is useful. Letting it choose for you isn't.",
                    "The hardest opponent to rush is your own impulse.",
                    "I hate sitting still... but I can feel the difference."
            });
            case CAUTIOUS -> pick(random, new String[]{
                    "Listen long enough and danger stops feeling so sudden.",
                    "A quiet moment tells you what panic was hiding.",
                    "You notice more when you stop expecting the worst for a minute.",
                    "Calm doesn't mean careless.",
                    "Listen past the silence. You notice more when you stop forcing it."
            });
        };
    }

    public static String rescued(RandomSource random) { return pick(random, RESCUED); }
    public static String captive(RandomSource random) { return pick(random, CAPTIVE); }

    private static String pick(RandomSource random, String[] pool) {
        return pool[random.nextInt(pool.length)];
    }
}
