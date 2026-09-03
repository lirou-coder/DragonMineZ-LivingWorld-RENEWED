package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/** Optional bridge that feeds Living World outcomes through Skill Progression's own kill routine. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DMZSkillProgressionCompat {
    private static Method deathHandler;
    private static Method setDebugForceRoll;
    private static Method isDebugForceRoll;
    private static boolean lookupAttempted;

    private DMZSkillProgressionCompat() {}

    public static void onFighterDefeated(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded("dmzskillprogression")) return;
        try {
            Method handler = handler();
            if (handler == null) return;
            LivingEntity proxy = dmzKillProxy(player);
            if (proxy == null) return;
            handler.invoke(null, new LivingDeathEvent(proxy, player.damageSources().playerAttack(player)));
        } catch (ReflectiveOperationException ignored) {
            // An optional integration must never prevent the encounter itself from completing.
        }
    }

    /** One Skill Meditation roll with its saga-enemy chance multiplied by the meditation TP stage. */
    public static void onMeditationPulse(ServerPlayer player, int stageTpMultiplier) {
        if (player == null
                || !LivingWorldConfig.canMeditationProcSkillProgression()
                || !ModList.get().isLoaded("dmzskillprogression")) return;
        try {
            Method handler = handler();
            LivingEntity proxy = dmzKillProxy(player);
            if (handler == null || proxy == null || setDebugForceRoll == null || isDebugForceRoll == null) return;
            boolean externallyForced = Boolean.TRUE.equals(isDebugForceRoll.invoke(null));
            double chance = Math.min(1.0D, 0.02D * Math.max(1, stageTpMultiplier));
            if (!externallyForced && ThreadLocalRandom.current().nextDouble() >= chance) return;
            try {
                if (!externallyForced) setDebugForceRoll.invoke(null, true);
                handler.invoke(null, new LivingDeathEvent(proxy, player.damageSources().playerAttack(player)));
            } finally {
                if (!externallyForced) setDebugForceRoll.invoke(null, false);
            }
        } catch (ReflectiveOperationException ignored) {}
    }

    private static LivingEntity dmzKillProxy(ServerPlayer player) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman1"));
        Entity entity = type == null ? null : type.create(player.serverLevel());
        return entity instanceof LivingEntity living ? living : null;
    }

    private static Method handler() {
        if (lookupAttempted) return deathHandler;
        lookupAttempted = true;
        try {
            Class<?> type = Class.forName("com.lcd.dmzskillprogression.event.SkillMeditationRollHandler");
            deathHandler = type.getMethod("onLivingDeath", LivingDeathEvent.class);
            setDebugForceRoll = type.getMethod("setDebugForceRoll", boolean.class);
            isDebugForceRoll = type.getMethod("isDebugForceRoll");
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
