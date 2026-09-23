package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.WorldMenaceFighterEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces the structural invariant introduced for World Menaces: Herobrine and X-7 may only
 * exist as {@code world_menace_fighter}. Older saves and stale profile materializers can still
 * try to load/create an {@code ambient_fighter} carrying the menace identity; canceling that join
 * leaves the dedicated singleton managers as the sole authority to recover the correct hostile body.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldMenaceEntityTypeGuard {
    private WorldMenaceEntityTypeGuard() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter)
                || fighter instanceof WorldMenaceFighterEntity) return;

        // Use the persistent identity flags, not display names, so an unrelated fighter merely
        // named "Herobrine" or "X-7" is not accidentally rejected. Both official menace profile
        // restore paths install these flags before addFreshEntity, and disk loads restore them too.
        boolean invalidHerobrineBody = fighter.getPersistentData().getBoolean(WorldMenaceManager.HEROBRINE_TAG);
        boolean invalidExperimentBody = fighter.getPersistentData().getBoolean(RedRibbonExperimentManager.TAG);
        if (invalidHerobrineBody || invalidExperimentBody) event.setCanceled(true);
    }
}
