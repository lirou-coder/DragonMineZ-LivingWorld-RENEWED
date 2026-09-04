package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents Living World actors from being dragged through unrelated DMZ dimensions/afterlife travel. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LivingWorldDimensionGuard {
    private LivingWorldDimensionGuard() {}

    @SubscribeEvent
    public static void onEntityTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter)) return;
        if (FighterAfterlifeManager.canUseNetherHell(fighter, event.getDimension())) return;
        if (!LivingWorldDimensions.isSupported(event.getDimension())) event.setCanceled(true);
    }
}
