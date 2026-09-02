package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/** Optional bridge that feeds Living World outcomes through Skill Progression's own kill routine. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DMZSkillProgressionCompat {
    private static Method deathHandler;
    private static boolean lookupAttempted;

    private DMZSkillProgressionCompat() {}

    public static void onFighterDefeated(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded("dmzskillprogression")) return;
        try {
            Method handler = handler();
            if (handler == null) return;
            // Skill Progression's public entrypoint requires a Monster death. This detached proxy
            // is never spawned or posted to Forge; it solely invokes that mod's exact reward logic.
            Zombie proxy = new Zombie(player.serverLevel());
            handler.invoke(null, new LivingDeathEvent(proxy, player.damageSources().playerAttack(player)));
        } catch (ReflectiveOperationException ignored) {
            // An optional integration must never prevent the encounter itself from completing.
        }
    }

    private static Method handler() {
        if (lookupAttempted) return deathHandler;
        lookupAttempted = true;
        try {
            Class<?> type = Class.forName("com.lcd.dmzskillprogression.event.BattleXpHandler");
            deathHandler = type.getMethod("onLivingDeath", LivingDeathEvent.class);
        } catch (ReflectiveOperationException ignored) {
            deathHandler = null;
        }
        return deathHandler;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingWorldFighterDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity)) return;
        ServerPlayer player = playerResponsible(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player != null) onFighterDefeated(player);
    }

    private static ServerPlayer playerResponsible(Entity causing, Entity direct) {
        if (causing instanceof ServerPlayer player) return player;
        if (direct instanceof AbstractKiProjectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }
}
