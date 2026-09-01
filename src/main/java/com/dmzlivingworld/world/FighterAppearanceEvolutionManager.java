package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterRace;
import com.dragonminez.common.hair.HairManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Very rare persistent cosmetic life changes using only existing DMZ appearance presets. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterAppearanceEvolutionManager {
    private static final String NEXT_CHANGE = "LWNextAppearanceChange";
    private static final long DAY = 24000L;

    private FighterAppearanceEvolutionManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 1200L != 0L) return;
        Set<UUID> checked = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    player.getBoundingBox().inflate(112.0D), AmbientFighterEntity::isAlive)) {
                if (!checked.add(fighter.getUUID()) || WorldMenaceManager.isHerobrine(fighter)) continue;
                long next = fighter.getPersistentData().getLong(NEXT_CHANGE);
                if (next <= 0L) {
                    schedule(fighter, now);
                    continue;
                }
                if (now < next) continue;
                change(fighter, false);
                schedule(fighter, now);
            }
        }
    }

    private static void schedule(AmbientFighterEntity fighter, long now) {
        // Multiple in-game days between rolls; intentionally much rarer than ordinary activities.
        long delay = DAY * (4L + fighter.getRandom().nextInt(10));
        fighter.getPersistentData().putLong(NEXT_CHANGE, now + delay);
    }

    public static boolean change(AmbientFighterEntity fighter, boolean debug) {
        if (fighter == null || !fighter.isAlive() || fighter.level().isClientSide || WorldMenaceManager.isHerobrine(fighter)) return false;
        FighterRace race = fighter.getRace();
        boolean canHair = race == FighterRace.HUMAN || race == FighterRace.SAIYAN;
        boolean canOutfit = race != FighterRace.FROST_DEMON && race != FighterRace.BIO_ANDROID && !fighter.isFactionMember();
        if (!canHair && !canOutfit) return false;

        boolean hair = canHair && (!canOutfit || fighter.getRandom().nextFloat() < 0.46F);
        if (hair) {
            int max = Math.max(1, HairManager.getPresetCount());
            int old = fighter.getHairId();
            int next = 1 + fighter.getRandom().nextInt(max);
            if (max > 1 && next == old) next = (next % max) + 1;
            fighter.setHairIdForLivingWorld(next);
            fighter.recordLegacyEvent("Changed hairstyle");
            ReactiveWorldManager.rememberEvent(fighter, "APPEARANCE", fighter.getFighterName(), "changed hairstyle");
            if (debug) fighter.speak("Trying something different with my hair.", 78);
        } else {
            int variants = switch (race) {
                case HUMAN, SAIYAN -> 22;
                case NAMEKIAN -> 2;
                case MAJIN -> 6;
                default -> 1;
            };
            if (variants <= 1) return false;
            int old = fighter.getOutfit();
            int next = fighter.getRandom().nextInt(variants);
            if (variants > 1 && next == old) next = (next + 1 + fighter.getRandom().nextInt(variants - 1)) % variants;
            fighter.setOutfitForLivingWorld(next);
            fighter.recordLegacyEvent("Changed outfit");
            ReactiveWorldManager.rememberEvent(fighter, "APPEARANCE", fighter.getFighterName(), "changed outfit");
            if (debug) fighter.speak("Felt like changing the outfit for once.", 78);
        }
        FighterMemoryManager.refreshLoadedProfile(fighter);
        return true;
    }

    public static int debugNearest(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        AmbientFighterEntity fighter = level.getEntitiesOfClass(AmbientFighterEntity.class,
                        player.getBoundingBox().inflate(48.0D), f -> f.isAlive() && !com.dmzlwfusion.NpcFusionManager.isHiddenFusionPartner(f) && !WorldMenaceManager.isHerobrine(f))
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (fighter == null) {
            fighter = AmbientFighterSpawner.spawnNearPlayer(player, com.dmzlivingworld.entity.FighterAlignment.NEUTRAL,
                    com.dmzlivingworld.entity.FighterRank.TRAINED, true);
        }
        if (fighter != null && change(fighter, true)) {
            player.displayClientMessage(Component.literal("[Living World] Forced a valid persistent appearance change on " + fighter.getFighterName() + "."), false);
            return 1;
        }
        player.displayClientMessage(Component.literal("[Living World] No nearby fighter had an applicable cosmetic change."), false);
        return 0;
    }
}
