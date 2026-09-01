package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.MainItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Rare native DMZ capsule rewards from meaningful Living World victories. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterDefeatRewardManager {
    private static final String LAST_DROP = "LWLastCapsuleReward";
    private FighterDefeatRewardManager() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity defeated)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity victor)) return;
        onDefeated(defeated, victor);
    }

    public static void onDefeated(AmbientFighterEntity defeated, LivingEntity victor) {
        if (defeated == null || victor == null || defeated.level().isClientSide) return;
        long now = defeated.level().getGameTime();
        long lastDrop = defeated.getLegacyData().getLong(LAST_DROP);
        if (lastDrop > 0L && now - lastDrop < 20L * 60L * 20L) return;
        double anchor = defeated.level() instanceof net.minecraft.server.level.ServerLevel level
                ? WorldPowerScaler.resolveWorldAnchor(level, defeated.blockPosition()) : defeated.getBattlePower();
        double strength = Math.max(0.35D, Math.min(3.0D, defeated.getBattlePower() / Math.max(1.0D, anchor)));
        double chance = switch (defeated.getRank()) {
            case ROOKIE -> 0.004D;
            case TRAINED -> 0.012D;
            case VETERAN -> 0.026D;
        };
        chance *= Math.min(2.2D, 0.65D + strength * 0.45D);
        if (defeated.getRandom().nextDouble() >= chance) return;

        List<Item> normal = List.of(MainItems.RED_CAPSULE.get(), MainItems.PURPLE_CAPSULE.get(), MainItems.YELLOW_CAPSULE.get(),
                MainItems.GREEN_CAPSULE.get(), MainItems.ORANGE_CAPSULE.get(), MainItems.BLUE_CAPSULE.get());
        List<Item> gete = List.of(MainItems.GETE_RED_CAPSULE.get(), MainItems.GETE_PURPLE_CAPSULE.get(), MainItems.GETE_YELLOW_CAPSULE.get(),
                MainItems.GETE_GREEN_CAPSULE.get(), MainItems.GETE_ORANGE_CAPSULE.get(), MainItems.GETE_BLUE_CAPSULE.get());
        boolean rareTier = defeated.getRank() == FighterRank.VETERAN && strength >= 1.35D && defeated.getRandom().nextFloat() < 0.14F;
        List<Item> pool = rareTier ? gete : normal;
        defeated.spawnAtLocation(new ItemStack(pool.get(defeated.getRandom().nextInt(pool.size()))));
        defeated.getLegacyData().putLong(LAST_DROP, now);
    }
}
