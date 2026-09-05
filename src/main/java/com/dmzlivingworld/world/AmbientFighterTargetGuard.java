package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents protective golems from treating peaceful Living World NPCs as hostile monsters. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AmbientFighterTargetGuard {
    private AmbientFighterTargetGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void filterIronGolemTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)
                || !(event.getNewTarget() instanceof AmbientFighterEntity fighter)) return;
        if (fighter.getTarget() == golem || fighter.getTarget() instanceof Villager) return;
        event.setCanceled(true);
    }
}