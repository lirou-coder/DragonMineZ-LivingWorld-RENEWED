package com.dmzlivingworld.world;

import java.util.Locale;

/** Mission-specific dialogue pools for faction requests. */
public final class FactionRequestDialogue {
    private FactionRequestDialogue() {}

    public static String briefing(String type, long seed) { return pick(type, "brief", seed); }
    public static String start(String type, long seed) { return pick(type, "start", seed); }
    public static String pressure(String type, long seed) { return pick(type, "pressure", seed); }
    public static String success(String type, long seed) { return pick(type, "success", seed); }
    public static String yield(String type, long seed) { return pick(type, "yield", seed); }

    private static String pick(String type, String pool, long seed) {
        String[] lines = lines(type == null ? "" : type.toUpperCase(Locale.ROOT), pool);
        if (lines.length == 0) return "Stay focused. This operation matters.";
        return lines[Math.floorMod(Long.hashCode(seed), lines.length)];
    }

    private static String[] lines(String type, String pool) {
        return switch (type) {
            case "PROVISIONS" -> logistics(pool,
                    new String[]{"Stores are running thin. We need food, not promises.","Our patrols are stretching rations. Bring what you can spare.","People are skipping meals so fighters can stay in the field."},
                    new String[]{"You made it. Let's get these provisions counted.","Set the food here; I'll make sure it reaches the right hands.","Good timing. The kitchens were about to start rationing again."},
                    new String[]{"Every crate buys us another day.","Supplies keep a faction standing longer than speeches do.","I'll log every item. Nothing gets wasted."},
                    new String[]{"This will keep people fed. That's real help.","The stores are breathing again. Thank you.","You bought us time, and time matters right now."});
            case "MATERIALS" -> logistics(pool,
                    new String[]{"Repairs are eating through our stockpile.","Armor, tools, fortifications—we're short on everything that keeps them working.","Bring usable material. We need substance, not decoration."},
                    new String[]{"Put the materials here. I'll inventory them myself.","That's the kind of load we've been waiting for.","Good. The workshops can finally stop cannibalizing old gear."},
                    new String[]{"Metal becomes walls, weapons, repairs—everything.","We're rebuilding faster than we're breaking now.","Keep it coming. This is making a difference."},
                    new String[]{"The workshops can work again.","That closes one of our worst shortages.","You gave the faction something concrete to build with."});
            case "REPARATIONS" -> logistics(pool,
                    new String[]{"Words won't repair what happened. Useful aid might.","If you're serious about making amends, prove it with something the faction needs.","Trust is low. Start with practical restitution."},
                    new String[]{"I'll accept the delivery. What happens after depends on what you do next.","Leave it here. This doesn't erase anything, but it counts.","We're recording this as restitution, not friendship."},
                    new String[]{"Don't mistake acceptance for forgiveness.","Keep your hands visible and finish the delivery.","This is a first step. Nothing more."},
                    new String[]{"The debt is smaller than it was.","You've given us a reason not to close the door completely.","This won't be forgotten—neither will what came before."});
            case "WAR_STOCKPILE" -> logistics(pool,
                    new String[]{"War burns through supplies faster than people expect.","We need food and material before the next push begins.","The front is consuming everything we send it."},
                    new String[]{"Unload fast. These supplies are going straight to the front.","You're just in time; the next column leaves soon.","Good. I'll split this between rations and field repair."},
                    new String[]{"Every bundle here means somebody stays combat-ready.","The front doesn't care where supplies came from, only that they arrive.","We're building a reserve instead of fighting empty-handed."},
                    new String[]{"The stockpile is finally fit for a real operation.","You may have changed how long we can hold the line.","This gives our people room to fight without starving the faction."});
            case "TRAIN_RECRUIT" -> training(pool,
                    new String[]{"We've got recruits who need experience that drills can't fake.","They know the basics. They need pressure now.","Teach them what a real opponent feels like without breaking them."},
                    new String[]{"I'm ready. Don't go easy enough to make this useless.","Show me where my guard fails.","I was told to learn from you. Let's make it worth the bruises."},
                    new String[]{"Again. I saw what I did wrong.","You're faster than the training yard.","I'm not done yet. One more exchange."},
                    new String[]{"That taught me more than another week of drills.","I know exactly what I need to work on now.","Next time I won't make those same mistakes."});
            case "TRAIN_OFFICER" -> training(pool,
                    new String[]{"Our officers need sharper decisions under pressure.","Rank doesn't make someone untouchable. Test them.","We need leadership that can still think while getting hit."},
                    new String[]{"No ceremony. Treat me like the field would.","I want mistakes exposed before an enemy finds them.","Let's see whether my instincts deserve this rank."},
                    new String[]{"Good. Force me to adapt.","You're disrupting my timing—keep doing it.","This is closer to the pressure I needed."},
                    new String[]{"I'll change how I lead after this.","That was worth more than another briefing.","You found weaknesses my own people were too polite to mention."});
            case "PATROL" -> patrol(pool, false);
            case "RECOVERY" -> patrol(pool, true);
            case "RECON" -> recon(pool);
            case "PROTECT" -> protect(pool);
            case "CAPTURE" -> capture(pool, false);
            case "ELITE_CAPTURE" -> capture(pool, true);
            case "MERCENARY_HUNT" -> hunt(pool);
            case "MERCENARY_EXTRACTION" -> extraction(pool);
            case "MERCENARY_INTEL" -> intel(pool);
            case "MERCENARY_SABOTAGE" -> sabotage(pool);
            case "FRONTLINE" -> frontline(pool);
            case "DEFEND" -> defend(pool);
            case "ASSAULT" -> assault(pool);
            case "RETALIATION" -> retaliation(pool);
            case "WAR_READINESS" -> warReadiness(pool);
            case "RECOVERY_LINE" -> recoveryLine(pool);
            case "RESCUE" -> rescue(pool);
            case "TRAINING" -> training(pool,
                    new String[]{"We need a proper training partner.","This is sanctioned practice, not a grudge match.","Help us test where our fighters really stand."},
                    new String[]{"Ready when you are.","Keep it clean and make it useful.","Let's learn something from this."},
                    new String[]{"I'm adjusting. Keep going.","That angle caught me.","Good pressure."},
                    new String[]{"Useful session.","I'll remember that exchange.","That's enough to work with."});
            default -> generic(pool);
        };
    }

    private static String[] logistics(String pool, String[] brief, String[] start, String[] pressure, String[] success) {
        return switch (pool) { case "brief" -> brief; case "start" -> start; case "pressure" -> pressure; case "success" -> success; default -> new String[]{"Enough. I'm yielding."}; };
    }
    private static String[] training(String pool, String[] brief, String[] start, String[] pressure, String[] success) { return logistics(pool, brief, start, pressure, success); }

    private static String[] patrol(String pool, boolean recovery) {
        return switch (pool) {
            case "brief" -> recovery ? new String[]{"The faction's stretched thin. Walk with the recovery patrol.","We're rebuilding field presence one route at a time.","This patrol is about showing people the roads are ours again."}
                    : new String[]{"Walk the route with us. Extra eyes matter.","This isn't sightseeing. Patrols are how territory stays real.","We're checking movement, pressure, and who thinks we're not watching."};
            case "start" -> recovery ? new String[]{"Stay close. We're not looking for another disaster today.","We move steady and bring everyone back.","Watch the edges. Recovery patrols attract opportunists."}
                    : new String[]{"Formation up. We move on my mark.","Keep pace and watch the flanks.","If something feels wrong, say it before it becomes a fight."};
            case "pressure" -> recovery ? new String[]{"Don't chase. Protect the group.","Keep them off the weaker side!","Hold together—no heroics."}
                    : new String[]{"Contact! Pick a threat and commit!","They're testing the patrol. Answer them.","Don't let them split us up!"};
            case "success" -> recovery ? new String[]{"Everyone came back. That's the win we needed.","The route held and morale needed that.","Good work. This felt like a faction again."}
                    : new String[]{"Route clear. That's how a patrol should end.","They know we're still here.","Good walk. Good discipline. We're done."};
            default -> new String[]{"I'm done. I yield.","Enough—I'm backing off.","You win this exchange. I'm out."};
        };
    }

    private static String[] recon(String pool) { return switch (pool) {
        case "brief" -> new String[]{"We need eyes on them, not a pile of bodies.","Read their movement and get out clean.","Observe first. Fighting is what happens when reconnaissance goes wrong."};
        case "start" -> new String[]{"Keep your profile low. Someone's watching the approaches.","That ridge gives a view if you don't silhouette yourself.","Slow down. Recon dies when impatience takes over."};
        case "pressure" -> new String[]{"I saw movement—check that cover!","Someone's out there. Spread the search.","Don't fire blind. Find them first."};
        case "success" -> new String[]{"That's enough. We have their pattern.","Clean observation. No alarm is the best result.","Good. We learned something without paying for it."};
        default -> new String[]{"I'm disengaging!","Enough. I'm breaking contact.","I yield—don't make this worse."};
    }; }

    private static String[] protect(String pool) { return switch (pool) {
        case "brief" -> new String[]{"One of our officers has to stay exposed to finish this work.","Protect the officer; losing them costs more than losing ground.","This assignment is about keeping one important person alive."};
        case "start" -> new String[]{"Stay near me. If I have to move, move with me.","I can finish the job if you keep pressure off me.","Eyes outward. I'll handle the objective."};
        case "pressure" -> new String[]{"They're trying to reach me!","Cut that angle off!","I need ten more seconds—hold them!"};
        case "success" -> new String[]{"I'm intact and the work is done.","You kept the operation from becoming a funeral.","That protection mattered. I won't forget it."};
        default -> new String[]{"I'm pulling out!","I can't hold this position—falling back!","Enough. I yield."};
    }; }

    private static String[] capture(String pool, boolean elite) { return switch (pool) {
        case "brief" -> elite ? new String[]{"We need their officer alive. Dead tells us nothing.","This target matters enough that killing them wastes the opportunity.","Bring the elite target down without finishing them."}
                : new String[]{"We want a prisoner, not another corpse.","Subdue the target and preserve them for questioning.","Use restraint. The value is in taking them alive."};
        case "start" -> elite ? new String[]{"You think you can take me alive? Try.","I'm not surrendering because someone put a mark on me.","Come close enough to capture me and find out."}
                : new String[]{"Back off. I'm not going with you.","Wrong person to try and drag away.","If you want me alive, you'll have to earn it."};
        case "pressure" -> new String[]{"You're trying not to kill me. I can use that.","Still holding back?","I know what that restraint means."};
        case "success" -> new String[]{"Fine. I know when the fight's over.","I'm done. Take me in.","Enough. I surrender."};
        default -> new String[]{"Stop. I yield.","That's enough—I surrender.","I'm beaten. Don't finish it."};
    }; }

    private static String[] hunt(String pool) { return switch (pool) {
        case "brief" -> new String[]{"The contract is for one real person. Confirm the target before you act.","No substitutes. Find the named target and finish the contract.","This is a targeted hit, not permission to massacre a faction."};
        case "start" -> new String[]{"So you're the contractor they sent.","I wondered who'd take the job.","You found me. Now decide whether this was worth the money."};
        case "pressure" -> new String[]{"You don't get paid if you can't finish it.","Come on, contractor—commit.","You're in too deep to pretend this isn't personal now."};
        case "success" -> new String[]{"Contract confirmed.","The target is neutralized. That's the job.","No replacement target. This one is over."};
        default -> new String[]{"Enough. I'll yield if your employer accepts it.","I'm beaten. Call off the contract.","Stop. I surrender."};
    }; }

    private static String[] extraction(String pool) { return switch (pool) {
        case "brief" -> new String[]{"Our operative is a real person stuck behind their lines.","Bring our contact home. Everything else is secondary.","This is extraction, not extermination. Keep the operative alive."};
        case "start" -> new String[]{"You actually came. Good—let's move.","No long reunion. Get me out before they notice.","I can move. Just don't leave me behind."};
        case "pressure" -> new String[]{"They're closing in—keep moving!","Don't stop for every fight!","Home first. Revenge later."};
        case "success" -> new String[]{"I'm home. That's all I wanted to hear.","Made it. I owe you one.","Extraction complete. I thought I was staying there forever."};
        default -> new String[]{"I'm breaking off!","Enough. I'm not dying here.","I yield—let the operative go."};
    }; }

    private static String[] intel(String pool) { return switch (pool) {
        case "brief" -> new String[]{"Information is only useful if they don't know what you learned.","Watch patterns, people, handoffs. Don't turn this into a raid.","We need a picture of how they operate, not a body count."};
        case "start" -> new String[]{"Keep the perimeter clean. Something feels off.","Check the route before the handoff starts.","Eyes up. We don't get surprised twice."};
        case "pressure" -> new String[]{"There! I saw someone!","Search that cover—now!","Intruder! Lock down the observation area!"};
        case "success" -> new String[]{"Report received. That's actionable intelligence.","Good work. They still don't know how much we learned.","This gives us choices we didn't have before."};
        default -> new String[]{"I'm hit—pulling back!","Enough. I'm yielding!","I surrender. Don't turn surveillance into an execution."};
    }; }

    private static String[] sabotage(String pool) { return switch (pool) {
        case "brief" -> new String[]{"Hit the supply movement, not everyone wearing their colors.","Disrupt the route and get out before the response boxes you in.","The objective is logistics. Casualties are optional and costly."};
        case "start" -> new String[]{"Something's wrong with the route. Check the loads.","Nobody leaves until we know what was touched.","Secure the movement corridor. Now."};
        case "pressure" -> new String[]{"Saboteur! Cut off the escape!","They're hitting the supplies—move!","Protect the route, don't chase blindly!"};
        case "success" -> new String[]{"The route is broken. Leave before they reorganize.","Supply flow disrupted. That's enough damage.","Objective done. Don't overstay it."};
        default -> new String[]{"I'm done—falling back!","Enough. The route isn't worth dying for.","I yield. Take the supplies and go."};
    }; }

    private static String[] rescue(String pool) { return switch (pool) {
        case "brief" -> new String[]{"One of ours is actually missing. Bring them back.","This isn't abstract. A real faction member is in enemy hands.","Find the captive and get them home alive."};
        case "start" -> new String[]{"I knew someone would come.","Please tell me you're here to get me out.","Keep your voice down. The guards are close."};
        case "pressure" -> new String[]{"They're trying to free the prisoner!","Hold the captive area!","Don't let them reach the prisoner!"};
        case "success" -> new String[]{"I'm free. I thought I'd disappear in there.","You came back for me. Thank you.","Home. Just get me home."};
        default -> new String[]{"I yield—take the prisoner and go.","Enough! I'm standing down.","Stop. This guard post isn't worth my life."};
    }; }


    private static String[] frontline(String pool) { return switch (pool) {
        case "brief" -> new String[]{"The front needs bodies who are already ours, not hired shadows.","A real field section is committing here. If they break, the line moves.","This is the current front. What happens to these fighters changes tomorrow's map.","We're not feeding waves into this. One roster, one decision."};
        case "start" -> new String[]{"Frontline set. Keep our people connected.","This is the line—don't let them split it.","Names checked. Everyone here is actually committed.","No second roster behind us. Fight like it."};
        case "pressure" -> new String[]{"Their center is bending—don't overextend!","Our left is taking pressure; close the gap!","Keep the line coherent!","They're trying to pull us apart—stay paired!"};
        case "success" -> new String[]{"The line held. Count who made it back.","They've broken contact. This ground is ours for now.","Front secured. Survivors rotate home.","That's a real shift in the front, not a timer expiring."};
        default -> new String[]{"I'm off the line—I yield!","We're broken here. I'm withdrawing!","Enough. I'm not dying for this patch of ground.","I surrender. The line is lost."};
    }; }

    private static String[] defend(String pool) { return switch (pool) {
        case "brief" -> new String[]{"They're coming for something we actually need to keep.","This is a defensive call. Preserve the people and the position.","If this post falls, our momentum falls with it.","A fixed attack group is moving in. Break that group, not an imaginary timer."};
        case "start" -> new String[]{"Defenders in place. Make them come through us.","Hold the approaches and protect each other.","No chasing beyond the position. This is a defense.","They've arrived. Anchor here."};
        case "pressure" -> new String[]{"They're pushing the entrance!","Don't let them isolate anyone!","Hold your ground—make them spend morale!","They're wavering. Keep the position intact!"};
        case "success" -> new String[]{"Attack broken. The position stays ours.","They withdrew before we did. That's the defense won.","We're still here, and so is the post.","Defense complete. Check the wounded before celebrating."};
        default -> new String[]{"I can't hold this—I'm yielding!","Defense is broken. I'm pulling back!","Enough! I'm out of the fight.","I surrender. Don't finish me here."};
    }; }

    private static String[] assault(String pool) { return switch (pool) {
        case "brief" -> new String[]{"War Strike means committing a real team into enemy ground.","Hit one position hard enough to change their confidence and supplies.","We are not asking you to farm bodies. Break the position and get out.","One strike roster. No replacement wave if people fall."};
        case "start" -> new String[]{"Strike team moving. Keep momentum forward.","We hit fast, we stay together, we stop when they break.","Objective ahead. Don't turn this into a scattered brawl.","This is the whole strike force. Make the opening count."};
        case "pressure" -> new String[]{"Their defense is cracking—press the objective!","Don't chase runners; break the position!","Keep pressure on their strongest holdout!","They're losing cohesion. Stay aggressive, not reckless!"};
        case "success" -> new String[]{"Position broken. Pull the survivors out.","Strike complete. Their momentum will feel this.","That's enough damage. We leave before victory becomes waste.","Objective collapsed. Count our people and move."};
        default -> new String[]{"The position's lost—I yield!","We're done here. I'm withdrawing!","Enough. You broke the defense.","I surrender. The strike has its result."};
    }; }

    private static String[] retaliation(String pool) { return switch (pool) {
        case "brief" -> new String[]{"They hit us first. Retaliation is about restoring deterrence, not erasing them.","We need an answer strong enough that the last attack has a cost.","A counterstrike is forming from real residents who remember what happened.","Hit back, break the committed opposition, then stop."};
        case "start" -> new String[]{"Counterstrike moving. Keep the purpose clear.","We answer the attack and come home.","No wandering vendetta—focus on the committed force.","They wanted a response. Here it is."};
        case "pressure" -> new String[]{"Push them back, then hold discipline!","Don't let anger split the team!","Their resolve is going—finish the counterstrike cleanly!","Remember why we're here. Break the force, not everyone nearby!"};
        case "success" -> new String[]{"Message delivered. We stop here.","Counterstrike complete. Their pressure should ease.","They broke first. That's enough retaliation.","We're done. Bring our people back."};
        default -> new String[]{"Enough! The answer was made.","I yield. This retaliation has gone far enough.","We're backing off—stop pushing.","I surrender. The counterstrike won."};
    }; }

    private static String[] warReadiness(String pool) { return switch (pool) {
        case "brief" -> new String[]{"Readiness is supplies, information, then a real mobilization test.","We don't know if we're ready until the same faction systems are stressed together.","First stock us, then read their deployment, then test our actual field roster.","This request is preparation for war, not a single errand."};
        case "start" -> new String[]{"Mobilization stage. Everyone knows their assignment.","This is the readiness test—keep formation and watch morale.","No reserves are being conjured behind us. This roster is the test.","Supplies counted, scouts read. Now prove the force can function."};
        case "pressure" -> new String[]{"This is where readiness shows—adapt!","Keep command intact and don't chase!","They're testing our weak side. Reinforce it!","If our morale breaks here, we weren't ready."};
        case "success" -> new String[]{"Readiness confirmed. We know what this faction can actually field.","The mobilization held. That's useful truth.","Preparation paid off. Survivors return with experience.","We're ready enough to make choices now."};
        default -> new String[]{"Mobilization's broken. I'm withdrawing!","We're not ready for this—I yield!","Enough. This test exposed the weakness.","I surrender. The readiness line failed."};
    }; }

    private static String[] recoveryLine(String pool) { return switch (pool) {
        case "brief" -> new String[]{"Recovery means getting real people moving again, not waiting for a number to rise.","The faction needs supplies and a safe regrouping route.","We're rebuilding confidence checkpoint by checkpoint.","Help one real recovery team prove the roads can be used again."};
        case "start" -> new String[]{"Recovery team moving. Nobody gets left behind.","First checkpoint ahead. Keep the group intact.","Slow and steady—we're rebuilding, not charging a front.","Same team all the way home. Watch the stragglers."};
        case "pressure" -> new String[]{"Close up! Recovery fails if we scatter!","Keep the route calm and the team together!","Don't abandon the slower fighters!","We're rebuilding confidence—stay disciplined!"};
        case "success" -> new String[]{"Three checkpoints secure. This route feels usable again.","The team made it through. Recovery is real now.","People will trust this corridor again.","Regroup complete. Survivors can return to normal duty."};
        default -> new String[]{"I can't continue—I'm dropping out!","Recovery team is broken. I'm withdrawing.","Enough. I yield and fall back.","I'm done. The route beat us today."};
    }; }
    private static String[] battle(String pool, String noun, String order, String done) { return switch (pool) {
        case "brief" -> new String[]{"This " + noun + " uses real people. Every casualty changes what comes after.","We're committing an actual field roster to this " + noun + ".","No endless waves. Break their force or force a withdrawal."};
        case "start" -> new String[]{order + ". Stay with the committed force.","Roster locked. Nobody else is coming to replace us.","This is the force we have. Make it count."};
        case "pressure" -> new String[]{"Their line is bending—keep pressure!","Don't scatter! Fight as a unit!","They're losing morale. Stay disciplined!"};
        case "success" -> new String[]{done + ".","Their remaining force is withdrawing.","Operation complete. Count who made it back."};
        default -> new String[]{"I'm yielding!","Enough—I'm out of this fight.","I surrender. Don't make this a death."};
    }; }

    private static String[] generic(String pool) { return switch (pool) {
        case "brief" -> new String[]{"This request exists because the faction actually needs something done.","You're working with real faction members. Treat the outcome like it matters.","No disposable actors here. These people go back to their lives afterward."};
        case "start" -> new String[]{"We're committed. Let's do this properly.","Stay on the objective.","Everyone here has something to lose."};
        case "pressure" -> new String[]{"Focus on the objective!","Don't let the situation run away from us.","Keep moving. We can still finish this cleanly."};
        case "success" -> new String[]{"That's done. We can go home.","Objective complete.","Good. This actually changed something."};
        default -> new String[]{"I yield.","Enough. I'm done fighting.","I'm backing out."};
    }; }
}
