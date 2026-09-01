package com.dmzlivingworld.world;

/** Dialogue/briefing flavor for faction missions. Kept separate so the request state machine stays readable. */
public final class FactionMissionFlavor {
    private FactionMissionFlavor() {}

    public record IntelScenario(String name, String brief, String[] arrival, String[] detected,
                                String[] searching, String[] observed, String[] reportClean, String[] reportBloody) {}

    private static final IntelScenario[] INTEL = new IntelScenario[]{
            new IntelScenario("Shift Change", "Observe a guard rotation and learn when the perimeter is thinnest.",
                    new String[]{"You're early. The next watch isn't here yet.", "Keep the east route covered until relief arrives.", "I hate shift changes. Everyone thinks someone else is watching.", "Count heads before you leave. Last time we were short two.", "Relief should be here any minute. Stay sharp until then.", "Don't wander. The handoff is when people slip through."},
                    new String[]{"Movement! Someone's watching the handoff!", "There! Behind the perimeter!", "That isn't one of ours. Move!", "Contact near the watch route!", "Eyes up! We have an intruder!", "Cut them off before they learn the schedule!"},
                    new String[]{"They were here. Check every blind corner.", "Spread out. They can't have gone far.", "Watch the rocks and tree line. Don't bunch up.", "No visual. Keep searching the route.", "They know our shift pattern now. Find them."},
                    new String[]{"Rotation complete. Same time tomorrow.", "Good. Perimeter is yours now.", "Nothing unusual. Let's move.", "Handoff done. Keep the route clean.", "All quiet. Finally."},
                    new String[]{"A full rotation schedule? That's useful.", "Good work. We can move around their weak watch windows now.", "This tells us exactly when their perimeter changes hands.", "Clean observation. Nobody knows we have their schedule.", "Excellent. Quiet information is usually the best information."},
                    new String[]{"The schedule helps, but the bodies will make them change it.", "Useful, but you turned a quiet watch into an incident.", "We'll use this quickly. After those casualties they'll revise the rotation.", "You got the information, but subtlety clearly wasn't part of the plan.", "Next time remember: dead guards teach the enemy too."}),

            new IntelScenario("Supply Audit", "Watch a guarded supply count and identify what the faction is short on or stockpiling.",
                    new String[]{"Count those crates again. Command wants exact numbers.", "Food on the left, gear on the right. Don't mix the tally.", "We're burning through reserves faster than expected.", "Seal that crate after you count it.", "If the ledger is wrong again, I'm not explaining it to the quartermaster.", "Mark the damaged stock separately."},
                    new String[]{"Spy near the stores! Secure the inventory!", "Someone's watching the count!", "Intruder by the supply line!", "Protect the ledger! Move!", "They're after our stock numbers!", "Cut them off from the depot!"},
                    new String[]{"Check behind the stacks and ridge.", "If they saw the ledger, command needs to know.", "Search the approaches. They came here for numbers.", "No visual. Keep the stores locked down.", "Find them before they report our reserves."},
                    new String[]{"Tally finished. Send the numbers up.", "That's the last crate.", "Inventory matches. Lock it down.", "We're done here. Move the ledger.", "Count complete. Nobody leaves stock unattended."},
                    new String[]{"Their supply picture is much clearer now.", "Good. This tells us what they can actually sustain.", "A clean inventory read is worth more than ten rumors.", "Now we know where their pressure points are.", "Perfect. Their logistics just became predictable."},
                    new String[]{"The numbers are useful, but they'll tighten the depot after the bloodshed.", "We know their reserves; unfortunately, they know somebody cared enough to kill for them.", "Useful intelligence, noisy acquisition.", "We'll act fast before they move the stock.", "Next time bring me numbers without a trail of bodies."}),

            new IntelScenario("Courier Handoff", "Identify a messenger route and learn who receives sensitive field reports.",
                    new String[]{"You're late. Do you have the packet?", "No names. You know the rule.", "Hand it over and take the southern route back.", "Command wants this delivered before the next patrol leaves.", "Check the seal before you pass it on.", "If anyone followed you, we abort the handoff."},
                    new String[]{"The handoff is compromised!", "Watcher! Protect the packet!", "They saw the courier! Get them!", "Break contact and secure the message!", "Intruder at the relay point!", "Don't let them leave with the route!"},
                    new String[]{"Sweep the courier trail.", "They may be following the relay chain.", "No visual. Protect the packet and search outward.", "Check the high ground. A watcher needs a view.", "Find them before they reach the next relay."},
                    new String[]{"Seal looks good. Move.", "Handoff complete. Change routes on the way back.", "Packet received. Don't linger.", "That's everything. Scatter.", "Relay done. Next contact in two hours."},
                    new String[]{"A courier chain gives us more than one target to watch.", "Excellent. We know how their field reports move now.", "That relay route will save us days of guessing.", "Clean work. Their messengers still think the route is secure.", "This is exactly the kind of pattern we needed."},
                    new String[]{"We learned the route, but the courier network will go dark after those deaths.", "Useful, but they will change the relay chain immediately.", "We'll exploit it once, maybe twice. You made them suspicious.", "The route is compromised on both sides now.", "Information secured; subtlety lost."}),

            new IntelScenario("Officer Briefing", "Listen in on a field officer assigning priorities to a patrol element.",
                    new String[]{"Listen carefully. I am not repeating the route.", "First unit takes the ridge. Second stays mobile.", "If contact happens, do not chase beyond the marker.", "Command expects pressure from the west.", "Keep reserve fighters close until I give the signal.", "Report anything unusual before you engage."},
                    new String[]{"Briefing compromised! Take them now!", "Spy! Nobody lets them leave!", "They heard the orders—move!", "Contact! Protect command information!", "Intruder at the briefing!", "Cut off their escape!"},
                    new String[]{"They know the assignment. Search every exit.", "Hold the area until we find them.", "No visual. Pairs, not alone.", "Check the rear approach and high ground.", "They were close enough to hear us. Keep looking."},
                    new String[]{"Orders understood. Move to positions.", "That's the brief. Execute it.", "No questions? Good. Move.", "Stick to the assignment and report changes.", "Briefing over. Stay alert."},
                    new String[]{"Their command priorities are exposed. Very useful.", "Good. We know where their officers expect pressure.", "That briefing tells us how they'll react before they react.", "Clean interception. Their plan is still intact—and known to us.", "Excellent. We can work around their reserve pattern now."},
                    new String[]{"The orders are useful, but they'll rewrite them after losing people at the briefing.", "You brought the plan, but also gave them a reason to change it.", "We'll use what remains valid. Expect their posture to harden.", "That's actionable, though far louder than I wanted.", "They'll know the briefing leaked. We have a narrow window."}),

            new IntelScenario("Training Rotation", "Observe a combat drill to estimate readiness, favored tactics, and weak links.",
                    new String[]{"Again. Faster this time.", "You're dropping your guard after the second strike.", "Rotate partners. I want fresh matchups.", "Don't burn all your Ki in the opening exchange.", "Reset positions. Same drill, cleaner execution.", "Watch the flanks even during training."},
                    new String[]{"Unknown observer! Drill is live—move!", "Spy on the perimeter!", "Training's over. Intruder!", "Contact! Box them in!", "Someone's been studying us!", "Take them before they report our drill!"},
                    new String[]{"Search in pairs. Treat this as part of the drill.", "They were watching technique, not supplies.", "Check the high ground and cover.", "No visual. Keep formation.", "If they got away, assume our habits are known."},
                    new String[]{"Better. Rotate again tomorrow.", "Drill complete. Fix what I called out.", "That's enough. Recover and reset.", "Good work. Keep those corrections.", "End exercise. Maintain readiness."},
                    new String[]{"Their readiness is easier to judge now.", "Good. Technique habits are intelligence too.", "That gives us a real picture of how they train and react.", "Clean observation. They'll keep practicing the same habits.", "Excellent. We know what their fighters are being taught."},
                    new String[]{"Their drill data helps, but survivors will train differently after this.", "You learned their habits by forcing them into a real fight. Useful, but messy.", "We'll account for the casualties when judging readiness.", "Actionable, though you've probably accelerated their training now.", "Next time observe the drill without becoming the drill."}),

            new IntelScenario("Perimeter Relay", "Map a short-range signal relay and identify the faction's alarm response pattern.",
                    new String[]{"Relay check. North post, answer.", "Signal is clean. Test the backup channel.", "If the first call fails, light the second relay immediately.", "Keep the response code short.", "West post was slow last time. Test them twice.", "No chatter on the relay unless it's urgent."},
                    new String[]{"Alarm! Intruder by the relay!", "Signal the next post—contact!", "They found the relay line! Move!", "Raise the alarm and cut them off!", "Watcher near the signal post!", "Contact! Send the warning now!"},
                    new String[]{"Keep the relay active while we search.", "Signal adjacent posts. They may be moving between them.", "No visual. Maintain the alarm.", "Search around the relay approaches.", "If they escape, assume they know our response chain."},
                    new String[]{"Relay check complete.", "All posts responding. Stand down.", "Signal chain is good.", "Backup channel confirmed.", "That's enough. Resume normal watch."},
                    new String[]{"Now we know how quickly their alarm travels.", "Excellent. Their response chain has a rhythm we can exploit.", "A clean relay map is exactly what I needed.", "Good. Their alarm network still thinks it's secure.", "This gives us timing, coverage, and likely blind spots."},
                    new String[]{"We mapped the relay, but the casualties guarantee they'll reinforce it.", "Useful timing data, though the alarm network is probably changing tonight.", "We'll use this before they redesign the response chain.", "You got the pattern and triggered the worst-case test yourself.", "The intelligence is sound; the network will not stay unchanged for long."})
    };

    private static final String[] PATROL_JOIN = {
            "There you are. Fall in and keep your eyes moving.",
            "Good timing. Stay close; this route has been restless lately.",
            "You made it. We move as a group from here.",
            "Join the line. If anything feels wrong, call it early.",
            "Glad you found the rendezvous. Keep pace and watch our blind side.",
            "You're with us for this circuit. Don't wander off chasing shadows.",
            "Right on time. The route looks quiet, which is when I trust it least.",
            "Take the outside position. You'll see trouble before the center does."
    };
    private static final String[] PATROL_TRAVEL = {
            "Keep the spacing. Next checkpoint is ahead.",
            "Eyes on the route. Don't drift off the line.",
            "Stay with the patrol. We clear this stretch before anything else.",
            "Watch the flanks while we move.",
            "No detours. Finish the circuit first.",
            "Keep pace. We're checking the whole route, not just the easy ground.",
            "Quiet stretch. Treat it like it isn't.",
            "Next checkpoint, same formation.",
            "Stay alert. A patrol only works if everyone keeps watching.",
            "Route first. Anything unrelated can wait until we're off duty."
    };
    private static final String[] RECOVERY_JOIN = {
            "Good. You came. We need steady eyes more than bravado today.",
            "Stay close. We're rebuilding confidence on this route one pass at a time.",
            "Thanks for showing. Keep this patrol calm and together.",
            "We took a beating out here recently. Help us make the road feel ours again.",
            "No heroics unless we need them. Today is about restoring the route.",
            "Fall in. Some of these fighters are still shaken, so keep the line tight."
    };
    private static final String[] PATROL_AMBUSH = {
            "Contact! Hold the line and don't get separated!",
            "Ambush! Stay with the patrol—don't let them split us!",
            "Movement on us! Defensive spacing, now!",
            "Here they come! Protect the route and each other!",
            "Hostiles! Keep them off the center of the patrol!",
            "We have company. Stay sharp and stay together!",
            "Break in the route! Turn and meet them!",
            "Contact front! Nobody chases alone!"
    };
    private static final String[] PATROL_RESUME = {
            "That's the immediate threat. Reform and move.",
            "They're broken. Check everyone, then we finish the circuit.",
            "Good hold. Back into formation—we still have a route to complete.",
            "Threat's gone for now. Don't relax until we're home.",
            "Count heads. Good. We continue.",
            "Nice recovery. Back to patrol pace.",
            "They wanted to stop the route. Let's make sure they failed.",
            "Clear enough. Reform on me."
    };
    private static final String[] PATROL_FINISH = {
            "Route is clear. Good work out there.",
            "Full circuit done. That's the kind of quiet result I like.",
            "Nothing got past us. You carried your share.",
            "Patrol complete. You fit into the line better than most outsiders.",
            "That's our route. Clean, complete, and everyone came back.",
            "Good circuit. We'll be less worried about this stretch tonight.",
            "We're done here. Thanks for treating patrol duty like real work.",
            "End of route. You kept pace and kept watch—good enough for me."
    };
    private static final String[] PATROL_HARD_FINISH = {
            "You held when it mattered. We'll remember that.",
            "That stopped being routine fast. You didn't fold.",
            "We came back with the route intact. You helped make that happen.",
            "They tested the patrol and got an answer. Good work.",
            "Rough circuit, but everyone who could move made it home. That's a win.",
            "You stayed with us instead of chasing glory. I noticed.",
            "That ambush could have broken the line. It didn't.",
            "Hard patrol. Solid company. We'll remember both."
    };

    public static String intelCasualty(String scenarioName, long salt) {
        String key = scenarioName == null ? "" : scenarioName;
        String[] pool = switch (key) {
            case "Shift Change" -> new String[]{"Guard down! Lock the rotation and seal the route!", "They killed one of ours—no more routine patrol pattern!", "Casualty on the watch! Double every post!", "Forget the schedule. Hunt whoever did this!"};
            case "Supply Audit" -> new String[]{"Fighter down by the stores! Lock every crate!", "Casualty at the depot! Move the ledger and seal the stock!", "They killed a guard for these numbers—change the inventory route!", "Secure the supplies! Nobody leaves alone now!"};
            case "Courier Handoff" -> new String[]{"Courier detail down! Burn the route and change contacts!", "They killed one of us—this relay is compromised!", "Casualty at the handoff! Move the packet now!", "Drop the normal route. We switch relays immediately!"};
            case "Officer Briefing" -> new String[]{"Fighter down! Orders are compromised—rewrite the deployment!", "They killed someone at the brief! Assume every order leaked!", "Casualty! Change positions and close every exit!", "No more fixed plan. Hunt the observer and move command!"};
            case "Training Rotation" -> new String[]{"This isn't a drill anymore! Fighter down!", "Casualty! Break exercise formation and engage for real!", "They were studying us and now one of ours is dead—adapt!", "Training over! Live contact, live threat!"};
            case "Perimeter Relay" -> new String[]{"Post down! Raise the full alarm and reroute the signal!", "Casualty at the relay! Every neighboring post goes active!", "They killed a relay guard—change codes and spread the warning!", "Full alert! This signal line is compromised!"};
            default -> new String[]{"Fighter down! Tighten the search!", "Casualty! Nobody assumes this is a quiet watch anymore!", "One of ours is down—close the area!", "They crossed the line. Full alert!"};
        };
        return pick(pool, salt);
    }

    public static String patrolJoin(boolean recovery, long salt) { return pick(recovery ? RECOVERY_JOIN : PATROL_JOIN, salt); }
    public static String patrolTravel(long salt) { return pick(PATROL_TRAVEL, salt); }
    public static String patrolAmbush(long salt) { return pick(PATROL_AMBUSH, salt); }
    public static String patrolResume(long salt) { return pick(PATROL_RESUME, salt); }
    public static String patrolFinish(boolean ambushed, long salt) { return pick(ambushed ? PATROL_HARD_FINISH : PATROL_FINISH, salt); }

    public static IntelScenario intelScenario(int seed, int point) {
        int idx = Math.floorMod(seed + Math.max(0, point - 1) * 5, INTEL.length);
        return INTEL[idx];
    }

    public static String pick(String[] pool, long salt) {
        if (pool == null || pool.length == 0) return "";
        long mixed = salt ^ (salt >>> 33) ^ 0x9E3779B97F4A7C15L;
        return pool[Math.floorMod((int)(mixed ^ (mixed >>> 32)), pool.length)];
    }
}
