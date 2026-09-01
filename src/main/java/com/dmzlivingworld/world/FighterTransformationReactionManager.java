package com.dmzlivingworld.world;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sparse, contextual reactions when a nearby player visibly enters a DMZ form. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterTransformationReactionManager {
    private static final Map<UUID, String> LAST_FORM = new HashMap<>();
    private static final Map<UUID, Long> NEXT_REACTION = new HashMap<>();

    private FighterTransformationReactionManager() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 5L != 0L) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || !LivingWorldDimensions.isSupported(level)) continue;
            StatsData stats = player.getCapability(StatsCapability.INSTANCE).orElse(null);
            if (stats == null) continue;
            String current = currentFormKey(stats);
            String previous = LAST_FORM.put(player.getUUID(), current);
            if (previous == null || current.equals(previous) || current.isBlank()) continue;
            if (now < NEXT_REACTION.getOrDefault(player.getUUID(), 0L)) continue;
            react(level, player, now);
        }
    }

    private static String currentFormKey(StatsData stats) {
        if (stats == null || stats.getCharacter() == null) return "";
        String form = safe(stats.getCharacter().getActiveForm());
        String stack = safe(stats.getCharacter().getActiveStackForm());
        if (form.isBlank() && stack.isBlank()) return "";
        return form + "|" + stack;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static void react(ServerLevel level, ServerPlayer player, long now) {
        List<AmbientFighterEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(
                AmbientFighterEntity.class,
                player.getBoundingBox().inflate(26.0D, 14.0D, 26.0D),
                fighter -> eligible(player, fighter)));
        if (nearby.isEmpty()) return;
        nearby.sort(Comparator.comparingDouble(player::distanceToSqr));

        int spoken = 0;
        for (AmbientFighterEntity fighter : nearby) {
            float chance = reactionChance(player, fighter);
            if (fighter.getRandom().nextFloat() > chance) continue;
            fighter.getLookControl().setLookAt(player, 35.0F, 35.0F);
            fighter.speak(line(player, fighter), 78);
            FighterMemoryManager.refreshLoadedProfile(fighter);
            spoken++;
            if (spoken >= 2 || (spoken >= 1 && player.getRandom().nextFloat() < 0.78F)) break;
        }
        if (spoken > 0) NEXT_REACTION.put(player.getUUID(), now + 500L);
    }

    private static boolean eligible(ServerPlayer player, AmbientFighterEntity fighter) {
        if (fighter == null || !fighter.isAlive() || fighter.isCaptive() || fighter.isDefeated()
                || fighter.isMeditating() || fighter.isTransforming() || fighter.getTarget() != null
                || fighter.isSocialLifeActivity() || fighter.isSocialPlayerApproach()
                || !fighter.getSpeech().isEmpty()) return false;
        FighterRelationshipManager.Disposition disposition = FighterRelationshipManager.disposition(player, fighter);
        if (disposition == FighterRelationshipManager.Disposition.HOSTILE) return false;
        return fighter.getAlignment() != FighterAlignment.BAD || fighter.isRememberedFor(player);
    }

    private static float reactionChance(ServerPlayer player, AmbientFighterEntity fighter) {
        int relationship = FighterRelationshipManager.relationshipOrUnknown(player, fighter);
        float chance = relationship >= 85 ? 0.72F : relationship >= 60 ? 0.60F : relationship >= 35 ? 0.46F
                : relationship >= 15 ? 0.34F : 0.22F;
        return switch (fighter.getPersonality()) {
            case PROUD, AGGRESSIVE -> Math.min(0.82F, chance + 0.10F);
            case CAUTIOUS -> Math.min(0.80F, chance + 0.06F);
            default -> chance;
        };
    }

    private static String line(ServerPlayer player, AmbientFighterEntity fighter) {
        int relationship = FighterRelationshipManager.relationshipOrUnknown(player, fighter);
        if (relationship >= 60 && fighter.getRandom().nextFloat() < 0.55F) {
            return switch (fighter.getPersonality()) {
                case PROUD -> "There it is. I knew you were still holding something back.";
                case HEROIC -> "You've come a long way. That power feels completely different.";
                case CALM -> "I can feel how much steadier your power is now.";
                case CAUTIOUS -> "I know it's you, and that still made me take a step back.";
                case AGGRESSIVE -> "Now that's more like it. I was waiting to see that again.";
            };
        }
        FighterPersonality personality = fighter.getPersonality();
        return switch (personality) {
            case PROUD -> "So that's the power you've been holding back.";
            case HEROIC -> "That jump in power... impressive.";
            case CALM -> "Your energy changed completely.";
            case CAUTIOUS -> "I felt that from here. That's a lot of power.";
            case AGGRESSIVE -> "Heh. Now that looks interesting.";
        };
    }

    public static void clearRuntime() {
        LAST_FORM.clear();
        NEXT_REACTION.clear();
    }

    public static void clearRuntime(UUID playerId) {
        if (playerId == null) return;
        LAST_FORM.remove(playerId);
        NEXT_REACTION.remove(playerId);
    }
}
