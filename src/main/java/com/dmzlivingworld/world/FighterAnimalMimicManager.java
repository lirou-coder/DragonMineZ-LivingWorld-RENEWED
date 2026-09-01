package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Rare, cue-driven animal imitation. NPCs answer an animal with a playful line instead of duplicating its audio. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterAnimalMimicManager {
    private static final String DEBUG_SUBJECT = "LWDebugAnimalMimicSubject";
    private static final String NEXT_MIMIC = "LWNextAnimalMimic";
    private static final List<Pending> PENDING = new ArrayList<>();

    private enum AnimalKind { COW, PIG, SHEEP, CHICKEN, WOLF, CAT, HORSE, DONKEY, MULE, GOAT, RABBIT, FOX, PANDA, LLAMA, PARROT }
    private record Pending(UUID fighterId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                           AnimalKind kind, long at) {}
    private FighterAnimalMimicManager() {}

    @SubscribeEvent
    public static void onAnimalSound(PlayLevelSoundEvent.AtEntity event) {
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide()
                || !(animal.level() instanceof ServerLevel level)) return;
        AnimalKind kind = kindOf(animal);
        if (kind == null) return;
        long now = level.getServer().overworld().getGameTime();
        List<AmbientFighterEntity> fighters = level.getEntitiesOfClass(AmbientFighterEntity.class,
                animal.getBoundingBox().inflate(11.0D), f -> eligible(f, now));
        if (fighters.isEmpty()) return;
        AmbientFighterEntity fighter = fighters.get(animal.getRandom().nextInt(fighters.size()));
        if (fighter.getRandom().nextFloat() > 0.004F) return;
        schedule(fighter, kind, now + 8L + fighter.getRandom().nextInt(18));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        long now = event.getServer().overworld().getGameTime();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (now < pending.at()) continue;
            it.remove();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null || !(level.getEntity(pending.fighterId()) instanceof AmbientFighterEntity fighter) || !fighter.isAlive()) continue;
            fighter.speak(line(fighter, pending.kind()), 66);
            fighter.getPersistentData().putLong(NEXT_MIMIC, now + 12_000L + fighter.getRandom().nextInt(12_001));
            ReactiveWorldManager.rememberEvent(fighter, "ANIMAL_MIMIC", pending.kind().name().toLowerCase(java.util.Locale.ROOT),
                    "answered a nearby animal with a playful imitation");
        }
    }

    private static String line(AmbientFighterEntity fighter, AnimalKind kind) {
        return switch (kind) {
            case COW -> pick(fighter, "Moooo!! ...Okay, that one was pretty good.", "Moo. Hey, don't look at me like that.", "That cow makes it sound easy. Moooo!");
            case PIG -> pick(fighter, "Oink oink! ...No? Tough crowd.", "Oink! I think the pig approves.", "I can do that too. Oink!");
            case SHEEP -> pick(fighter, "Baa-aa! Hah, that one is fun.", "Baaa. Pretty convincing, right?", "That sheep has range. Baa!");
            case CHICKEN -> pick(fighter, "Bawk bawk! ...I regret nothing.", "Bawk! Don't tell anyone I did that.", "That chicken sounded confident. Bawk bawk!");
            case WOLF -> pick(fighter, "Awoooo! ...Okay, maybe not as intimidating.", "Awoo! I'm not starting a pack.", "That howl needed an answer. Awooo!");
            case CAT -> pick(fighter, "Mrow. That cat definitely judged me.", "Meow! ...It looked better when the cat did it.", "Mrrp. Yeah, I know. Terrible impression.");
            case HORSE -> pick(fighter, "Neeeigh! ...That was awful.", "Neigh! The horse is pretending it didn't hear me.");
            case DONKEY -> pick(fighter, "Hee-haw! Hah!", "Hee-haw! Okay, that one actually hurt my throat.");
            case MULE -> pick(fighter, "Hee-haw! Close enough?", "That mule has a very specific voice. Hee-haw!");
            case GOAT -> pick(fighter, "Maa-aa! Don't headbutt me for that.", "Maa! ...We're friends now, right?");
            case RABBIT -> pick(fighter, "...Do rabbits even make that much noise?", "Tiny thing, big attitude.");
            case FOX -> pick(fighter, "Yip! Yeah, I heard you.", "Yip yip! That fox is absolutely laughing at me.");
            case PANDA -> pick(fighter, "Hrrm! Living the easy life, huh?", "That panda has the right idea: eat, sit, repeat.");
            case LLAMA -> pick(fighter, "Hrrr! Please don't spit at me.", "I can imitate you, but I'm skipping the spitting part.");
            case PARROT -> pick(fighter, "Squawk! Two can play that game.", "Squawk! ...Wait, is it going to copy me now?");
        };
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static boolean eligible(AmbientFighterEntity fighter, long now) {
        return fighter.isAlive() && !fighter.isDefeated() && !fighter.isCaptive() && !fighter.isMeditating()
                && fighter.getTarget() == null && !WorldMenaceManager.isHerobrine(fighter)
                && now >= fighter.getPersistentData().getLong(NEXT_MIMIC);
    }

    private static void schedule(AmbientFighterEntity fighter, AnimalKind kind, long at) {
        fighter.getPersistentData().putLong(NEXT_MIMIC, Math.max(fighter.level().getGameTime() + 200L, at));
        PENDING.add(new Pending(fighter.getUUID(), fighter.level().dimension(), kind, at));
    }

    private static AnimalKind kindOf(Animal animal) {
        if (animal instanceof Cow) return AnimalKind.COW;
        if (animal instanceof Pig) return AnimalKind.PIG;
        if (animal instanceof Sheep) return AnimalKind.SHEEP;
        if (animal instanceof Chicken) return AnimalKind.CHICKEN;
        if (animal instanceof Wolf) return AnimalKind.WOLF;
        if (animal instanceof Cat) return AnimalKind.CAT;
        if (animal instanceof Horse) return AnimalKind.HORSE;
        if (animal instanceof Donkey) return AnimalKind.DONKEY;
        if (animal instanceof Mule) return AnimalKind.MULE;
        if (animal instanceof Goat) return AnimalKind.GOAT;
        if (animal instanceof Rabbit) return AnimalKind.RABBIT;
        if (animal instanceof Fox) return AnimalKind.FOX;
        if (animal instanceof Panda) return AnimalKind.PANDA;
        if (animal instanceof Llama) return AnimalKind.LLAMA;
        if (animal instanceof Parrot) return AnimalKind.PARROT;
        return null;
    }

    private static SoundEvent cueFor(AnimalKind kind) {
        return switch (kind) {
            case COW -> SoundEvents.COW_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            case SHEEP -> SoundEvents.SHEEP_AMBIENT;
            case CHICKEN -> SoundEvents.CHICKEN_AMBIENT;
            case WOLF -> SoundEvents.WOLF_AMBIENT;
            case CAT -> SoundEvents.CAT_AMBIENT;
            case HORSE -> SoundEvents.HORSE_AMBIENT;
            case DONKEY -> SoundEvents.DONKEY_AMBIENT;
            case MULE -> SoundEvents.MULE_AMBIENT;
            case GOAT -> SoundEvents.GOAT_AMBIENT;
            case RABBIT -> SoundEvents.RABBIT_AMBIENT;
            case FOX -> SoundEvents.FOX_AMBIENT;
            case PANDA -> SoundEvents.PANDA_AMBIENT;
            case LLAMA -> SoundEvents.LLAMA_AMBIENT;
            case PARROT -> SoundEvents.PARROT_AMBIENT;
        };
    }

    /** Deterministic proof: a real animal cue plays first, then the fighter responds in dialogue. */
    public static int debug(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        AmbientFighterEntity fighter = level.getEntitiesOfClass(AmbientFighterEntity.class,
                player.getBoundingBox().inflate(32.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f) && !WorldMenaceManager.isHerobrine(f))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (fighter == null) fighter = AmbientFighterSpawner.spawnNearPlayer(player, com.dmzlivingworld.entity.FighterAlignment.NEUTRAL,
                com.dmzlivingworld.entity.FighterRank.TRAINED, true);
        if (fighter == null) return 0;
        Animal animal = level.getEntitiesOfClass(Animal.class, fighter.getBoundingBox().inflate(10.0D), a -> kindOf(a) != null)
                .stream().findFirst().orElse(null);
        if (animal == null) {
            Cow cow = EntityType.COW.create(level);
            if (cow == null) return 0;
            cow.moveTo(fighter.getX() + 3.0D, fighter.getY(), fighter.getZ(), 0.0F, 0.0F);
            if (!level.addFreshEntity(cow)) return 0;
            animal = cow;
        }
        AnimalKind kind = kindOf(animal);
        if (kind == null) return 0;
        level.playSound(null, animal.blockPosition(), cueFor(kind), SoundSource.NEUTRAL, 0.85F, 1.0F);
        schedule(fighter, kind, level.getServer().overworld().getGameTime() + 14L);
        player.getPersistentData().putString(DEBUG_SUBJECT, fighter.getFighterName());
        return 1;
    }

    public static String debugSubject(ServerPlayer player) {
        return player == null ? "" : player.getPersistentData().getString(DEBUG_SUBJECT);
    }

    public static void clearRuntime() { PENDING.clear(); }
}
