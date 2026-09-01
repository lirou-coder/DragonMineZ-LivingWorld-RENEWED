package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.events.DMZEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Carries DMZ's per-hit Defense Penetration from DamageModifyEvent into LivingEntity#hurt. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcDefensePenetrationManager {
    private static final Map<AmbientFighterEntity, Pending> PENDING = new WeakHashMap<>();

    private NpcDefensePenetrationManager() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capture(DMZEvent.DamageModifyEvent event) {
        if (!(event.getVictim() instanceof AmbientFighterEntity fighter) || fighter.level().isClientSide()) return;
        double penetration = Math.max(0.0D, Math.min(1.0D, event.getDefensePenetration()));
        PENDING.put(fighter, new Pending(event.getAttacker() == null ? null : event.getAttacker().getUUID(),
                fighter.level().getGameTime(), penetration));
    }

    public static double consume(AmbientFighterEntity fighter, Entity attacker) {
        Pending pending = PENDING.remove(fighter);
        if (pending == null || pending.tick != fighter.level().getGameTime()) return 0.0D;
        if (pending.attacker != null && (attacker == null || !pending.attacker.equals(attacker.getUUID()))) return 0.0D;
        return pending.penetration;
    }

    private record Pending(UUID attacker, long tick, double penetration) {}
}
