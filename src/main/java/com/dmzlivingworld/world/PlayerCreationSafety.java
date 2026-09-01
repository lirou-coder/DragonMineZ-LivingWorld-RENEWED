package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Makes a player without a completed DMZ character nonexistent to Living World combat. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerCreationSafety {
    private PlayerCreationSafety() {}

    public static boolean isCreating(ServerPlayer player) {
        if (player == null) return false;
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return stats == null || !stats.getStatus().isHasCreatedCharacter();
    }

    public static boolean isLivingWorldAttacker(Entity entity) {
        if (entity instanceof AmbientFighterEntity) return true;
        if (entity == null) return false;
        var data = entity.getPersistentData();
        return data.getBoolean("LWScientistPersistentSpecimen") || data.getBoolean("LWX7Reinforcement");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void blockAnyLivingWorldAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isCreating(player)) return;
        if (isLivingWorldAttacker(event.getSource().getEntity())
                || isLivingWorldAttacker(event.getSource().getDirectEntity())) event.setCanceled(true);
    }
}
