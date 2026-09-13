package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.client.LWLang;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;

/** Sparse world-event reactions that give Reactive World dialogue concrete things to talk about. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReactiveWorldEventManager {
    private static final String NEXT_MOB_COMMENT = "LWReactiveNextMobComment";
    private static final String NEXT_WORLD_COMMENT = "LWReactiveNextWorldComment";
    private static final String MOB_COMMENTED_UNTIL = "LWReactiveMobCommentedUntil";

    private ReactiveWorldEventManager() {}

    /** Called from the fighter tick. Animal/villager comments are intentionally uncommon. */
    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)) return;
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        if (LivingWorldConfig.npcChatFrequencyScale() <= 0.0D) return;
        if (fighter.tickCount % 200 == Math.floorMod(fighter.getUUID().hashCode(), 200)) maybeCommentOnWeatherOrTime(fighter, level);
        if (fighter.tickCount % 240 != Math.floorMod(fighter.getUUID().hashCode(), 240)) return;
        if (fighter.getTarget() != null || fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.isTransforming() || !fighter.getSpeech().isEmpty()) return;
        long now = level.getGameTime();
        if (now < fighter.getPersistentData().getLong(NEXT_MOB_COMMENT)) return;
        if (fighter.getRandom().nextFloat() >= LivingWorldConfig.scaledNpcChatChance(0.16F)) return;

        LivingEntity nearby = level.getEntitiesOfClass(LivingEntity.class, fighter.getBoundingBox().inflate(12.0D), entity ->
                        entity != fighter && entity.isAlive() && isInterestingNearbyEntity(entity)
                                && now >= entity.getPersistentData().getLong(MOB_COMMENTED_UNTIL))
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (nearby == null) return;

        String subject = mobLabel(nearby);
        String line = mobLine(fighter, nearby, subject);
        if (line.isBlank()) return;
        fighter.speak(line, 78);
        // One observed mob gets one local conversation window. This prevents five nearby LW
        // fighters from all making the same cow/villager/weather-style observation at once.
        nearby.getPersistentData().putLong(MOB_COMMENTED_UNTIL, now + 1800L + fighter.getRandom().nextInt(2401));
        ReactiveWorldManager.rememberEvent(fighter, "MOB_SEEN", subject, "noticed a nearby " + subject.toLowerCase());
        fighter.getPersistentData().putLong(NEXT_MOB_COMMENT, now + LivingWorldConfig.scaledNpcChatDelay(2400L + fighter.getRandom().nextInt(2401)));
    }

    private static void maybeCommentOnWeatherOrTime(AmbientFighterEntity fighter, ServerLevel level) {
        if (FactionRequestMissionManager.isExclusiveFieldAssignment(fighter)) return;
        if (fighter.getTarget() != null || fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.isTransforming() || fighter.isSocialLifeActivity() || !fighter.getSpeech().isEmpty()) return;
        long now = level.getGameTime();
        if (now < fighter.getPersistentData().getLong(NEXT_WORLD_COMMENT)
                || fighter.getRandom().nextFloat() >= LivingWorldConfig.scaledNpcChatChance(0.09F)) return;
        long day = Math.floorMod(level.getDayTime(), 24000L);
        String line = "";
        String event = "";
        if (level.isThundering()) {
            line = fighter.getRandom().nextBoolean() ? LWLang.speechKey("dialogue.world_weather.thunder.0", "That thunder is getting close. Good weather for staying alert.") : LWLang.speechKey("dialogue.world_weather.thunder.1", "Storm's getting rough. I wouldn't want to fight blind in this.");
            event = "the thunderstorm";
        } else if (level.isRaining()) {
            line = fighter.getRandom().nextBoolean() ? LWLang.speechKey("dialogue.world_weather.rain.0", "Rain's really settled in.") : LWLang.speechKey("dialogue.world_weather.rain.1", "Everything smells different after the rain starts.");
            event = "the rain";
        } else if (day >= 22500L || day < 1000L) {
            line = LWLang.speechKey("dialogue.world_weather.sunrise", "Sun's coming up. Quietest part of the day."); event = "sunrise";
        } else if (day >= 11500L && day < 13000L) {
            line = LWLang.speechKey("dialogue.world_weather.sunset", "Getting late. We should decide where we're going before dark."); event = "sunset";
        } else if (day >= 13000L && day < 22500L) {
            line = fighter.getRandom().nextBoolean() ? LWLang.speechKey("dialogue.world_weather.night.0", "It's properly dark now. Harder to read the terrain.") : LWLang.speechKey("dialogue.world_weather.night.1", "Night's quiet around here. For now."); event = "nightfall";
        } else if (day >= 5000L && day < 9000L && fighter.getRandom().nextBoolean()) {
            line = LWLang.speechKey("dialogue.world_weather.clear_day", "Bright day. You can see trouble coming from a long way off."); event = "the clear day";
        }
        if (line.isBlank()) return;
        fighter.speak(line, 76);
        ReactiveWorldManager.rememberEvent(fighter, "WORLD_CONDITION", event, "noticed " + event);
        fighter.getPersistentData().putLong(NEXT_WORLD_COMMENT, now + LivingWorldConfig.scaledNpcChatDelay(4200L + fighter.getRandom().nextInt(3601)));
    }

    private static boolean isInterestingNearbyEntity(LivingEntity entity) {
        if (entity instanceof Animal || entity instanceof Villager || entity instanceof IronGolem) return true;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null || !"dragonminez".equals(id.getNamespace())) return false;
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("dino") || path.contains("robot") || path.contains("redribbon")
                || path.contains("red_ribbon") || path.contains("bandit") || path.contains("namek_frog")
                || path.contains("namek_trader") || path.contains("namek_warrior");
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fallen) || !(fallen.level() instanceof ServerLevel level)) return;
        for (AmbientFighterEntity observer : level.getEntitiesOfClass(AmbientFighterEntity.class,
                fallen.getBoundingBox().inflate(34.0D), other -> other != fallen && other.isAlive() && !other.isDefeated() && !other.isCaptive())) {
            boolean ally = isFellow(observer, fallen);
            boolean enemy = !ally && isEnemy(observer, fallen);
            if (!ally && !enemy) continue;

            float chance = ally ? 0.68F : 0.46F;
            if (observer.getRandom().nextFloat() >= chance) continue;
            if (ally) {
                ReactiveWorldManager.Mood mood = observer.getPersonality() == FighterPersonality.AGGRESSIVE
                        || observer.getPersonality() == FighterPersonality.PROUD
                        ? ReactiveWorldManager.Mood.IRRITATED : ReactiveWorldManager.Mood.SOMBER;
                ReactiveWorldManager.reactStrong(observer, mood, fallen.getFighterName() + " falling in battle", 1600);
                ReactiveWorldManager.rememberEvent(observer, "ALLY_DIED", fallen.getFighterName(), "saw an ally fall nearby");
                if (observer.getSpeech().isEmpty()) observer.speak(allyDeathLine(observer, fallen), 100);
            } else {
                ReactiveWorldManager.Mood mood = observer.getHealth() < observer.getMaxHealth() * 0.35F
                        ? ReactiveWorldManager.Mood.FOCUSED : ReactiveWorldManager.Mood.UPBEAT;
                ReactiveWorldManager.react(observer, mood, fallen.getFighterName() + " being defeated", 900);
                ReactiveWorldManager.rememberEvent(observer, "ENEMY_DIED", fallen.getFighterName(), "saw an enemy fall nearby");
                if (observer.getSpeech().isEmpty()) observer.speak(enemyDeathLine(observer, fallen), 82);
            }
        }
    }

    private static boolean isFellow(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a.sameParty(b) || FighterNpcSocialManager.bond(a, b) >= 6) return true;
        if (a.isFactionMember() && b.isFactionMember()) {
            if (a.getFactionId().equals(b.getFactionId())) return true;
            if (FactionManager.areAllies(a, b)) return true;
        }
        return false;
    }

    private static boolean isEnemy(AmbientFighterEntity a, AmbientFighterEntity b) {
        if (a.getTarget() == b || b.getTarget() == a) return true;
        if (!a.getRivalName().isBlank() && a.getRivalName().equals(b.getFighterName())) return true;
        return a.isFactionMember() && b.isFactionMember() && FactionManager.areEnemies(a, b);
    }

    private static String allyDeathLine(AmbientFighterEntity observer, AmbientFighterEntity fallen) {
        String name = fallen.getFighterName();
        return switch (ReactiveWorldManager.temperament(observer)) {
            case SUPPORTIVE, WARM -> LWLang.speechKey("dialogue.world_event.ally_death.supportive", "%s! No... stay with us!", name);
            case BULLY, BLUNT -> LWLang.speechKey("dialogue.world_event.ally_death.blunt", "%s is down. Nobody else falls!", name);
            case TEASING -> LWLang.speechKey("dialogue.world_event.ally_death.teasing", "Damn it, %s... this isn't funny.", name);
            case ALOOF -> LWLang.speechKey("dialogue.world_event.ally_death.aloof", "%s... understood. I'll finish this.", name);
        };
    }

    private static String enemyDeathLine(AmbientFighterEntity observer, AmbientFighterEntity fallen) {
        String name = fallen.getFighterName();
        return switch (observer.getAlignment()) {
            case GOOD -> LWLang.speechKey("dialogue.world_event.enemy_death.good", "%s is down. That's enough—keep moving.", name);
            case BAD -> LWLang.speechKey("dialogue.world_event.enemy_death.bad", "%s is finished. Who's next?", name);
            default -> LWLang.speechKey("dialogue.world_event.enemy_death.neutral", "%s is down. Stay sharp.", name);
        };
    }

    private static String mobLabel(LivingEntity entity) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id != null && "dragonminez".equals(id.getNamespace())) {
            String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("dino")) return "Dinosaur";
            if (path.contains("robot")) return "Red Ribbon robot";
            if (path.contains("redribbon") || path.contains("red_ribbon")) return "Red Ribbon soldier";
            if (path.contains("bandit")) return "Bandit";
            if (path.contains("namek_frog")) return "Namekian frog";
            if (path.contains("namek_trader")) return "Namekian trader";
            if (path.contains("namek_warrior")) return "Namekian warrior";
            return "Dragon Mine Z creature";
        }
        if (entity instanceof Cow) return "Cow";
        if (entity instanceof Pig) return "Pig";
        if (entity instanceof Sheep) return "Sheep";
        if (entity instanceof Chicken) return "Chicken";
        if (entity instanceof Wolf) return "Wolf";
        if (entity instanceof Villager) return "Villager";
        if (entity instanceof IronGolem) return "Iron Golem";
        return "Animal";
    }

    private static String mobLine(AmbientFighterEntity fighter, LivingEntity entity, String subject) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id != null && "dragonminez".equals(id.getNamespace())) {
            String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("dino")) return fighter.getRandom().nextBoolean() ? LWLang.speechKey("dialogue.world_event.mob.dinosaur.0", "That's a dinosaur. Give it room unless you want a very stupid fight.") : LWLang.speechKey("dialogue.world_event.mob.dinosaur.1", "Big dinosaur nearby. I'm keeping an eye on it.");
            if (path.contains("robot")) return LWLang.speechKey("dialogue.world_event.mob.robot", "Red Ribbon robot nearby. Those things never look friendly.");
            if (path.contains("redribbon") || path.contains("red_ribbon")) return LWLang.speechKey("dialogue.world_event.mob.red_ribbon", "Red Ribbon soldier. Keep your guard up until we know what they're doing.");
            if (path.contains("bandit")) return LWLang.speechKey("dialogue.world_event.mob.bandit", "Bandit nearby. Watch your pockets—and your back.");
            if (path.contains("namek_frog")) return LWLang.speechKey("dialogue.world_event.mob.namek_frog", "A Namekian frog. That's a long way from an ordinary pasture.");
            if (path.contains("namek_trader")) return LWLang.speechKey("dialogue.world_event.mob.namek_trader", "Namekian trader nearby. Probably knows more about this area than we do.");
            if (path.contains("namek_warrior")) return LWLang.speechKey("dialogue.world_event.mob.namek_warrior", "Namekian warrior nearby. They look ready for trouble.");
            return LWLang.speechKey("dialogue.world_event.mob.dmz_creature", "Something from the wider Dragon World is nearby. I'm watching it.");
        }
        if (entity instanceof Villager) {
            return switch (ReactiveWorldManager.temperament(fighter)) {
                case SUPPORTIVE, WARM -> LWLang.speechKey("dialogue.world_event.mob.villager.supportive", "That villager looks nervous. We should keep trouble away from here.");
                case BULLY -> LWLang.speechKey("dialogue.world_event.mob.villager.bully", "That villager keeps staring. Smart enough not to get involved.");
                default -> LWLang.speechKey("dialogue.world_event.mob.villager.default", "There's a villager nearby. Better not turn this place into a battlefield.");
            };
        }
        if (entity instanceof IronGolem) return LWLang.speechKey("dialogue.world_event.mob.iron_golem", "That iron golem hasn't taken its eyes off me.");
        if (entity instanceof Cow) return fighter.getRandom().nextBoolean() ? LWLang.speechKey("dialogue.world_event.mob.cow.0", "That cow has been staring at me for a while.") : LWLang.speechKey("dialogue.world_event.mob.cow.1", "Easy, cow. I'm just passing through.");
        if (entity instanceof Pig) return LWLang.speechKey("dialogue.world_event.mob.pig", "That pig looks completely unbothered by all of this.");
        if (entity instanceof Chicken) return LWLang.speechKey("dialogue.world_event.mob.chicken", "That chicken has better survival instincts than half the fighters I know.");
        if (entity instanceof Sheep) return LWLang.speechKey("dialogue.world_event.mob.sheep", "That sheep picked a surprisingly peaceful spot.");
        if (entity instanceof Wolf) return LWLang.speechKey("dialogue.world_event.mob.wolf", "There's a wolf nearby. It looks like it knows this area better than I do.");
        return LWLang.speechKey("dialogue.world_event.mob.wildlife", "There's wildlife nearby. Nice change from another fight.");
    }
}
