package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight server-side anti-repeat memory shared by nearby Living World speakers.
 * It deliberately does not persist to disk: its job is to keep a play session sounding
 * varied without turning dialogue selection into save data.
 */
public final class DialogueLocalityManager {
    private static final int LOCAL_RADIUS = 72;
    private static final long LOCAL_REPEAT_TICKS = 20L * 150L;
    private static final long SELF_REPEAT_TICKS = 20L * 240L;
    private static final int MAX_RECENT = 160;

    private static final Map<ResourceKey<Level>, Deque<Spoken>> RECENT = new HashMap<>();

    private static final String[] WEATHER = {
            "The weather turned quickly.",
            "That sky looks heavier than it did a minute ago.",
            "Feels like the air is changing.",
            "I can smell rain before it reaches us.",
            "The clouds are moving fast today.",
            "Wind like this makes distance hard to judge.",
            "Storm weather always changes the sound of a place.",
            "The light looks strange under those clouds.",
            "I wonder how long this weather will hold.",
            "Even the animals get quieter before bad weather.",
            "The horizon's getting dark.",
            "Weather like this makes the whole world feel smaller.",
            "Those clouds have been building for a while.",
            "The wind keeps changing direction.",
            "Rain would cool this place down.",
            "The sky looked clearer when I started moving.",
            "Thunder carries a long way out here.",
            "The air feels charged today.",
            "That breeze came out of nowhere.",
            "I would not trust that horizon.",
            "Looks like the sun is losing the argument.",
            "The temperature dropped fast.",
            "I can feel a storm before I hear one.",
            "Cloud cover makes everything look unfamiliar.",
            "At least the wind is at our backs for now.",
            "The rain is making the ground slick.",
            "That thunder sounded closer than the last one.",
            "The weather has everyone moving differently.",
            "I miss clear skies on days like this.",
            "This kind of wind makes flying annoying.",
            "The clouds are breaking a little over there.",
            "It might clear before long.",
            "The air smells clean after rain.",
            "That storm left the whole place quieter.",
            "Sunlight finally found a way through.",
            "I should have brought something warmer.",
            "The sky is putting on a show today.",
            "Weather can change a route faster than a fight can.",
            "It is brighter toward the east.",
            "The wind is settling down.",
            "I would rather train in clear weather.",
            "Something about this sky makes me want to keep moving."
    };
    private static final String[] TRAINING = {
            "One more clean repetition.",
            "Power is useless if the movement is sloppy.",
            "Again. Cleaner this time.",
            "I'm trying to make the motion automatic.",
            "A little better every round.",
            "I can feel where the technique still breaks down.",
            "Speed after form. Not before it.",
            "The boring repetitions are the ones that stay with you.",
            "I need another set before I call it.",
            "Small corrections add up.",
            "No point swinging hard if I can't recover my stance.",
            "I'm working on control, not just force.",
            "My guard drops when I rush that combination.",
            "I need to turn the hip more on that strike.",
            "That one felt cleaner.",
            "Again until I stop thinking about it.",
            "I am trying to waste less movement.",
            "Power starts from the ground.",
            "My timing is still half a beat late.",
            "The recovery matters as much as the hit.",
            "I can make that faster without making it messy.",
            "That kick needs a better chamber.",
            "I am keeping the shoulders loose this time.",
            "There is always another flaw to work on.",
            "I want the next strike to land exactly where I mean it.",
            "Breathing right makes a bigger difference than people think.",
            "I am training the transition, not just the attack.",
            "If I get tired and the form falls apart, it was never solid.",
            "I need to stop telegraphing that first punch.",
            "That sequence is starting to feel natural.",
            "I can put more force into it once the balance is right.",
            "Footwork first. Everything else follows.",
            "I am trying to stay relaxed until the instant I strike.",
            "One bad habit can ruin a whole technique.",
            "I would rather do ten clean reps than fifty ugly ones.",
            "My stance keeps drifting wider than I want.",
            "I am shaving little pauses out of the combination.",
            "That was close to what I wanted.",
            "I need to keep my eyes up while I move.",
            "Training alone makes it easy to lie to yourself.",
            "The next step is doing this under pressure.",
            "I am not finished until I can repeat it tired."
    };
    private static final String[] TRAVEL = {
            "Still a fair way to go.",
            "This route is quieter than the last one.",
            "I prefer moving while there's still light.",
            "The road looks different every time I come through.",
            "Let's keep moving before we lose the good weather.",
            "I know a cleaner line through here.",
            "Terrain like this slows everyone down.",
            "I'll remember this route next time.",
            "We can make better time past this ridge.",
            "I don't mind the distance when there's somewhere to be.",
            "The air's clearer up ahead.",
            "I should have left a little earlier.",
            "This way avoids the roughest ground.",
            "I can see the next landmark from here.",
            "We are making decent time.",
            "I would rather go around than fight the terrain.",
            "The path opens up after this stretch.",
            "I have not come through this side before.",
            "There should be flatter ground ahead.",
            "I am keeping that ridge on my left.",
            "A straight line is not always the fastest line.",
            "The view is better from up there.",
            "I can cut some distance if the air stays clear.",
            "This is the kind of route you remember by feel.",
            "I think we are past the worst of it.",
            "The ground keeps forcing little detours.",
            "I would rather arrive late than exhausted.",
            "That hill makes a useful marker.",
            "We can pick up the pace after these trees.",
            "I am checking the terrain before I commit to the line.",
            "This route would be easier from the air.",
            "I am not dropping altitude until the ground clears.",
            "The wind is pushing me off the clean line.",
            "There is room to swing wider around that obstacle.",
            "I can see where the terrain levels out.",
            "We should be close enough to recognize the area soon.",
            "I like knowing where I am without needing a map.",
            "That shortcut was not as short as it looked.",
            "We are still heading the right way.",
            "I have a better angle now.",
            "No reason to rush the last stretch.",
            "I will know this route better on the way back."
    };
    private static final String[] REST = {
            "I'm taking a minute before I move again.",
            "A short rest beats pushing until you're useless.",
            "Just giving my legs a moment.",
            "It's quiet enough to sit for a while.",
            "I needed this more than I thought.",
            "I'll get moving again in a minute.",
            "No shame in catching your breath.",
            "This is a decent place to stop.",
            "I can finally hear myself think.",
            "A few minutes off my feet will do.",
            "I was starting to feel that last stretch.",
            "I'll be sharper after a short break.",
            "I am letting my breathing settle.",
            "My legs were asking for this stop.",
            "Just enough time to loosen up again.",
            "I would rather rest now than slow everyone down later.",
            "This ground is good enough for me.",
            "I can feel the tension leaving my shoulders.",
            "A little quiet does wonders after a fight.",
            "I am not sleeping, just resetting.",
            "Give me a minute and I will be ready.",
            "My energy is coming back.",
            "Sitting still feels strange after moving all day.",
            "I picked a surprisingly comfortable spot.",
            "I am keeping this break short.",
            "The body notices when you ignore it for too long.",
            "I can think more clearly when I stop moving.",
            "This is a good place to watch the road from.",
            "I needed to get the weight off my feet.",
            "I will stretch before I stand up again.",
            "The silence is doing half the work.",
            "I am taking the hint my muscles were giving me.",
            "A proper rest is part of training too.",
            "I can feel my pulse slowing down.",
            "I am staying here until my breathing is normal.",
            "This spot is better than it looked.",
            "I almost forgot what sitting down felt like.",
            "I am not in a hurry for the next minute.",
            "I will move when I feel steady again.",
            "The ground is cold, but I will take it.",
            "A calm minute can save a sloppy fight later.",
            "That is enough pushing for now."
    };
    private static final String[] NATURE = {
            "You notice more when you stop rushing past everything.",
            "This place has its own rhythm.",
            "The little details make a place easier to remember.",
            "I like finding things that weren't here last time.",
            "Someone could walk right past this and never notice it.",
            "There's more life around here than it looks like at first.",
            "Funny what catches your eye when you aren't fighting.",
            "This spot feels calmer than the road behind us.",
            "Even fighters need something ordinary to look at sometimes.",
            "I should remember where this was.",
            "It's nice when the world isn't asking anything from you.",
            "There's always something small changing out here.",
            "That tree has probably seen more fights than I have.",
            "The grass is thicker on this side.",
            "I did not expect flowers out here.",
            "The water is clearer than it looked from a distance.",
            "There are tracks here I do not recognize.",
            "This place feels alive even when it is quiet.",
            "I like the sound of the leaves when the wind catches them.",
            "That flower is brighter up close.",
            "Animals always know when something is wrong before we do.",
            "I wonder how old these trees are.",
            "The river makes the whole area feel cooler.",
            "Someone has been through here recently.",
            "Nature does not care how strong you are.",
            "This would be an easy place to lose track of time.",
            "There is a lot happening when you actually look down.",
            "That bird has been following the same path for a while.",
            "I like places where you can hear the water.",
            "The shade here is better than I expected.",
            "There is something peaceful about ordinary life carrying on.",
            "I should come back when I am not in a hurry.",
            "That patch of flowers survived somehow.",
            "The trees hide how open the land really is.",
            "I can hear something moving in the brush.",
            "The water level looks higher than usual.",
            "This place would look completely different at night.",
            "I never noticed that color in the leaves before.",
            "The animals around here seem calm.",
            "A place like this makes a good landmark.",
            "There is more variety here than I expected.",
            "For once, nothing here is trying to hit me."
    };
    private static final String[] SOCIAL = {
            "Good to see a familiar face.",
            "You doing alright?",
            "You look like you've been busy.",
            "Haven't seen you in a bit.",
            "Hope your day's treating you decently.",
            "You picked an interesting time to come by.",
            "I was wondering when I'd run into you again.",
            "Everything okay on your end?",
            "You seem different from the last time we talked.",
            "I recognize that walk now.",
            "Funny how often our paths cross.",
            "I was just thinking this place felt too quiet.",
            "Hey. Been keeping out of trouble?",
            "You made it back in one piece. Good.",
            "I was going to ask where you disappeared to.",
            "You look like you have a story to tell.",
            "I have seen you around enough that this is starting to feel normal.",
            "Need anything, or just passing through?",
            "You caught me at a quiet moment.",
            "How has the road been treating you?",
            "I heard enough noise earlier to wonder if that was you.",
            "You seem more focused today.",
            "I was not expecting company, but I do not mind it.",
            "Still chasing the next fight?",
            "You look less tired than last time.",
            "I am glad it is you and not someone looking for trouble.",
            "Anything interesting happen since we last crossed paths?",
            "I was starting to think you avoided this area.",
            "You have a habit of appearing at strange times.",
            "You carrying good news or bad news?",
            "I can never tell whether you are resting or planning something.",
            "You seem in a better mood today.",
            "I have been meaning to ask how things are going.",
            "You look like you have been training.",
            "I recognize that expression. Something happened, did it not?",
            "You are getting easier to spot from a distance.",
            "I will take familiar company over silence.",
            "It is good to see someone I actually know.",
            "You still owe me the story behind that last mess.",
            "I wondered who was coming this way.",
            "Try not to make me regret saying hello.",
            "You look ready for whatever comes next."
    };
    private static final String[] GENERIC = {
            "Hm. Something feels different today.",
            "I should probably keep moving.",
            "Quiet moment. I'll take it.",
            "There's always something happening somewhere.",
            "I wonder what the rest of the day has planned.",
            "Some days feel longer than others.",
            "I keep noticing things I used to ignore.",
            "This world never stays still for long.",
            "I should remember that for later.",
            "Not everything needs to turn into a fight.",
            "I can work with this.",
            "That gives me something to think about.",
            "I have had stranger days.",
            "Something about this place feels familiar.",
            "I should trust my instincts on this one.",
            "There is no reason to force the moment.",
            "I keep expecting something to happen.",
            "Maybe I am overthinking it.",
            "I will deal with the next problem when it gets here.",
            "The quiet never lasts forever.",
            "I have learned not to ignore small changes.",
            "That could matter later.",
            "I am keeping an eye on things.",
            "There are worse places to spend a few minutes.",
            "I wonder who else is nearby.",
            "I should check my gear before I move on.",
            "Today has been unpredictable enough already.",
            "I am trying not to make assumptions.",
            "There is always another side to a story.",
            "I have a feeling this is not the end of it.",
            "I will remember how this felt.",
            "Sometimes doing nothing is the right choice.",
            "I could use a simpler day.",
            "That was not what I expected.",
            "I am not complaining. Yet.",
            "I should probably stop tempting fate.",
            "I have seen enough to stay curious.",
            "Something tells me to pay attention.",
            "I will let that thought sit for a while.",
            "No point worrying before there is a problem.",
            "I am learning to appreciate uneventful moments.",
            "Whatever comes next, I will handle it."
    };

    private DialogueLocalityManager() {}

    public static String resolve(AmbientFighterEntity fighter, String proposed) {
        if (fighter == null || proposed == null || proposed.isBlank() || !(fighter.level() instanceof ServerLevel level)) return proposed;
        long now = level.getGameTime();
        Deque<Spoken> recent = RECENT.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        prune(recent, now);

        String normalized = normalize(proposed);
        String topic = topicOf(proposed);
        boolean localRepeat = recentlyUsed(recent, normalized, fighter, now, LOCAL_REPEAT_TICKS, false);
        boolean selfRepeat = recentlyUsed(recent, normalized, fighter, now, SELF_REPEAT_TICKS, true);
        // Weather/nature observations are especially noticeable when a whole crowd says them.
        // Give those ambient topics their own local cooldown in addition to exact-line memory.
        if (("WEATHER".equals(topic) || "NATURE".equals(topic))
                && recentlyTopicUsed(recent, topic, fighter, now, "WEATHER".equals(topic) ? 1200L : 700L)
                && fighter.getRandom().nextFloat() < 0.72F) return null;

        String chosen = proposed;
        if (localRepeat || selfRepeat) {
            String replacement = chooseReplacement(fighter, proposed, recent, now);
            if (replacement != null) chosen = replacement;
        }

        recent.addLast(new Spoken(fighter.getUUID(), fighter.getX(), fighter.getY(), fighter.getZ(), now, normalize(chosen), topicOf(chosen)));
        while (recent.size() > MAX_RECENT) recent.removeFirst();
        return chosen;
    }

    private static boolean recentlyUsed(Deque<Spoken> recent, String normalized, AmbientFighterEntity fighter,
                                        long now, long age, boolean sameSpeakerOnly) {
        double radiusSq = LOCAL_RADIUS * LOCAL_RADIUS;
        for (Spoken s : recent) {
            if (!s.normalized.equals(normalized) || now - s.tick > age) continue;
            if (sameSpeakerOnly && !s.speaker.equals(fighter.getUUID())) continue;
            double dx = s.x - fighter.getX(), dy = s.y - fighter.getY(), dz = s.z - fighter.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) return true;
        }
        return false;
    }

    private static boolean recentlyTopicUsed(Deque<Spoken> recent, String topic, AmbientFighterEntity fighter, long now, long age) {
        double radiusSq = LOCAL_RADIUS * LOCAL_RADIUS;
        for (Spoken s : recent) {
            if (!topic.equals(s.topic) || now - s.tick > age || s.speaker.equals(fighter.getUUID())) continue;
            double dx = s.x - fighter.getX(), dy = s.y - fighter.getY(), dz = s.z - fighter.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq) return true;
        }
        return false;
    }

    private static String topicOf(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "rain", "storm", "thunder", "weather", "cloud", "sky", "wind")) return "WEATHER";
        if (containsAny(lower, "flower", "tree", "grass", "animal", "nature", "river", "water")) return "NATURE";
        if (containsAny(lower, "train", "power", "strong", "technique", "practice", "punch", "kick")) return "TRAINING";
        if (containsAny(lower, "road", "travel", "walk", "route", "fly", "flying", "distance")) return "TRAVEL";
        if (containsAny(lower, "rest", "sit", "tired", "breath", "quiet")) return "REST";
        if (containsAny(lower, "see you", "friend", "talk", "hello", "hey", "good to see", "how are")) return "SOCIAL";
        return "GENERIC";
    }

    private static String chooseReplacement(AmbientFighterEntity fighter, String original, Deque<Spoken> recent, long now) {
        String lower = original.toLowerCase(Locale.ROOT);
        String[] pool;
        if (containsAny(lower, "rain", "storm", "thunder", "weather", "cloud", "sky", "wind")) pool = WEATHER;
        else if (containsAny(lower, "train", "power", "strong", "technique", "practice", "punch", "kick")) pool = TRAINING;
        else if (containsAny(lower, "road", "travel", "walk", "route", "fly", "flying", "distance")) pool = TRAVEL;
        else if (containsAny(lower, "rest", "sit", "tired", "breath", "quiet")) pool = REST;
        else if (containsAny(lower, "flower", "tree", "grass", "animal", "nature", "river", "water")) pool = NATURE;
        else if (containsAny(lower, "see you", "friend", "talk", "hello", "hey", "good to see", "how are")) pool = SOCIAL;
        else pool = GENERIC;

        List<String> available = new ArrayList<>();
        for (String candidate : pool) {
            if (!recentlyUsed(recent, normalize(candidate), fighter, now, LOCAL_REPEAT_TICKS, false)) available.add(candidate);
        }
        if (available.isEmpty()) return null;
        return available.get(fighter.getRandom().nextInt(available.size()));
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']+", " ").trim();
    }

    private static void prune(Deque<Spoken> recent, long now) {
        while (!recent.isEmpty() && now - recent.peekFirst().tick > SELF_REPEAT_TICKS) recent.removeFirst();
    }

    /** Session-only dialogue memory must never leak into the next integrated/dedicated world. */
    public static void clearRuntime() { RECENT.clear(); }

    private static final class Spoken {
        private final UUID speaker;
        private final double x, y, z;
        private final long tick;
        private final String normalized;
        private final String topic;
        private Spoken(UUID speaker, double x, double y, double z, long tick, String normalized, String topic) {
            this.speaker = speaker; this.x = x; this.y = y; this.z = z; this.tick = tick; this.normalized = normalized; this.topic = topic;
        }
    }
}
