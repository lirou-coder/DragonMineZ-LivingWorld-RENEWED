package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterDialogue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Recognizes useful items deliberately dropped near a fighter, without turning random junk into friendship. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterGiftPickupManager {
    public static final String GIVER = "LWGiftGiver";
    private FighterGiftPickupManager() {}

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        event.getEntity().getPersistentData().putUUID(GIVER, player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        if (now % 40L != 0L) return;
        // Player radii regularly overlap in multiplayer. Process each actor once so one nearby
        // Senzu stack cannot be consumed multiple times in the same server tick.
        Set<UUID> processed = new HashSet<>();
        for (ServerPlayer observer : event.getServer().getPlayerList().getPlayers()) {
            if (!(observer.level() instanceof ServerLevel level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class,
                    observer.getBoundingBox().inflate(48.0D), f -> canAccept(f))) {
                if (!processed.add(fighter.getUUID())) continue;
                trySenzu(fighter, level);
                tryFood(fighter, level);
            }
        }
    }

    private static boolean canAccept(AmbientFighterEntity fighter) {
        return fighter.isAlive() && !fighter.isCaptive() && !fighter.isDefeated() && !fighter.isRecovering()
                && !fighter.isMeditating() && !fighter.isTransforming() && !fighter.isKaiokenActive()
                && fighter.getTarget() == null && !fighter.isSocialLifeActivity() && !fighter.isSocialPlayerApproach()
                && !fighter.isSocialPowerDisplay() && !fighter.isSanctionedMatchParticipant();
    }

    private static void trySenzu(AmbientFighterEntity fighter, ServerLevel level) {
        ItemEntity item = level.getEntitiesOfClass(ItemEntity.class, fighter.getBoundingBox().inflate(8.0D),
                        e -> e.isAlive() && e.tickCount > 5 && e.getPersistentData().hasUUID(GIVER) && isSenzu(e))
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (item == null) return;
        ServerPlayer giver = level.getServer().getPlayerList().getPlayer(item.getPersistentData().getUUID(GIVER));
        if (giver == null || giver.level() != fighter.level() || giver.distanceToSqr(fighter) > 18.0D * 18.0D) return;

        if (fighter.distanceToSqr(item) > 2.25D * 2.25D) {
            fighter.getLookControl().setLookAt(item, 35.0F, 35.0F);
            fighter.getNavigation().moveTo(item, 1.08D);
            return;
        }
        boolean injured = fighter.getHealth() < fighter.getMaxHealth() - 0.5F;
        if (!injured && fighter.getSenzuBeans() >= 4) return;

        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.discard();
        else item.setItem(item.getItem().copy());

        if (injured) fighter.heal(fighter.getMaxHealth());
        else fighter.receiveSenzuBean();

        long now = level.getServer().overworld().getGameTime();
        String key = "LWDroppedGiftBond_" + giver.getUUID();
        long last = fighter.getLegacyData().getLong(key);
        if (last <= 0L || now - last >= 20L * 60L * 8L) {
            fighter.getLegacyData().putLong(key, now);
            FighterMemoryManager.strengthenRelationship(giver, fighter, injured ? 2 : 1,
                    FighterRelationshipManager.BondEvent.GIFT, injured ? "Gave them a Senzu Bean when they needed it" : "Gave them a Senzu Bean");
        }
        boolean close = fighter.isRememberedFor(giver) && fighter.getMemoryRelationship() >= 60;
        String thanksKey = "LWDroppedGiftThanks_" + giver.getUUID();
        long lastThanks = fighter.getLegacyData().getLong(thanksKey);
        boolean canThank = fighter.getSpeech().isEmpty() && (lastThanks <= 0L || now - lastThanks >= 20L * 60L * 2L);
        if (canThank && (injured || fighter.getRandom().nextFloat() < 0.65F)) {
            fighter.getLegacyData().putLong(thanksKey, now);
            fighter.speak(injured ? FighterDialogue.senzuThanks(fighter.getRandom(), fighter.getPersonality(), close)
                    : droppedGiftThanks(fighter), 84);
        }
        fighter.recordLegacyEvent("Accepted a Senzu Bean from " + giver.getGameProfile().getName());
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    private static void tryFood(AmbientFighterEntity fighter, ServerLevel level) {
        ItemEntity item = level.getEntitiesOfClass(ItemEntity.class, fighter.getBoundingBox().inflate(7.0D),
                        e -> e.isAlive() && e.tickCount > 5 && e.getPersistentData().hasUUID(GIVER)
                                && !isSenzu(e) && e.getItem().getItem().isEdible())
                .stream().min(Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (item == null) return;
        ServerPlayer giver = level.getServer().getPlayerList().getPlayer(item.getPersistentData().getUUID(GIVER));
        if (giver == null || giver.level() != fighter.level() || giver.distanceToSqr(fighter) > 18.0D * 18.0D) return;
        long now = level.getServer().overworld().getGameTime();
        String eatKey = "LWFoodGiftEat_" + giver.getUUID();
        if (now - fighter.getLegacyData().getLong(eatKey) < 20L * 45L) return;
        if (fighter.distanceToSqr(item) > 2.25D * 2.25D) {
            fighter.getLookControl().setLookAt(item, 35.0F, 35.0F);
            fighter.getNavigation().moveTo(item, 1.04D);
            return;
        }
        String foodName = item.getItem().getHoverName().getString();
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.discard(); else item.setItem(item.getItem().copy());
        fighter.getLegacyData().putLong(eatKey, now);
        fighter.heal(Math.max(1.0F, fighter.getMaxHealth() * 0.035F));
        fighter.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (fighter.getSpeech().isEmpty() && fighter.getRandom().nextFloat() < 0.72F) {
            fighter.speak(fighter.getRandom().nextBoolean() ? "Oh, thanks. I could actually eat." : "For me? Thanks.", 72);
        }
        String bondKey = "LWFoodGiftBond_" + giver.getUUID();
        if (now - fighter.getLegacyData().getLong(bondKey) >= 20L * 60L * 12L) {
            fighter.getLegacyData().putLong(bondKey, now);
            FighterMemoryManager.strengthenRelationship(giver, fighter, 1, FighterRelationshipManager.BondEvent.GIFT,
                    "Shared food with them");
        }
        fighter.recordLegacyEvent("Accepted " + foodName + " from " + giver.getGameProfile().getName());
        FighterMemoryManager.refreshLoadedProfile(fighter);
    }

    public static void thankForRecoveredGift(AmbientFighterEntity fighter, ItemEntity item) {
        if (fighter == null || item == null || !(fighter.level() instanceof ServerLevel level) || !item.getPersistentData().hasUUID(GIVER)) return;
        ServerPlayer giver = level.getServer().getPlayerList().getPlayer(item.getPersistentData().getUUID(GIVER));
        if (giver == null || giver.distanceToSqr(fighter) > 20.0D * 20.0D) return;
        if (fighter.getSpeech().isEmpty()) fighter.speak(droppedGiftThanks(fighter), 76);
        long now = level.getServer().overworld().getGameTime();
        String key = "LWEquipmentDropBond_" + giver.getUUID();
        if (fighter.getLegacyData().getLong(key) <= 0L || now - fighter.getLegacyData().getLong(key) > 20L * 60L * 10L) {
            fighter.getLegacyData().putLong(key, now);
            FighterMemoryManager.strengthenRelationship(giver, fighter, 1,
                    FighterRelationshipManager.BondEvent.GIFT, "Picked up useful equipment you left for them");
        }
    }

    private static String droppedGiftThanks(AmbientFighterEntity fighter) {
        return switch (fighter.getPersonality()) {
            case HEROIC -> "Hey, thanks. I'll make good use of it.";
            case CALM -> "Thanks. That's useful.";
            case CAUTIOUS -> "For me? Thanks. I'll keep it close.";
            case PROUD -> "...Useful. Thanks.";
            case AGGRESSIVE -> "Nice. Thanks!";
        };
    }

    private static boolean isSenzu(ItemEntity entity) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(entity.getItem().getItem());
        return id != null && "dragonminez".equals(id.getNamespace()) && id.getPath().toLowerCase(java.util.Locale.ROOT).contains("senzu");
    }
}
