package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional target-policy bridge for Guard Villagers; contains no hard reference to its classes. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuardVillagersCompat {
    private static final String AGGRESSION_UNTIL = "LWGuardVillagerAggressionUntil";
    private static final String AGGRESSION_DIMENSION = "LWGuardVillagerAggressionDimension";
    private static final String AGGRESSION_X = "LWGuardVillagerAggressionX";
    private static final String AGGRESSION_Y = "LWGuardVillagerAggressionY";
    private static final String AGGRESSION_Z = "LWGuardVillagerAggressionZ";
    private static final long AGGRESSION_MEMORY_TICKS = 1_200L;
    private static final double DEFENSE_RADIUS_SQR = 64.0D * 64.0D;
    private static volatile Boolean guardVillagersLoaded;
    private static volatile EntityType<?> guardType;
    private static volatile boolean guardTypeResolved;

    private GuardVillagersCompat() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberVillagerAttack(LivingHurtEvent event) {
        if (!loaded() || !(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level) || event.getAmount() <= 0.0F) return;
        AmbientFighterEntity fighter = resolveFighter(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (fighter == null) return;
        CompoundTag data = fighter.getPersistentData();
        data.putLong(AGGRESSION_UNTIL, level.getGameTime() + AGGRESSION_MEMORY_TICKS);
        data.putString(AGGRESSION_DIMENSION, level.dimension().location().toString());
        data.putDouble(AGGRESSION_X, villager.getX());
        data.putDouble(AGGRESSION_Y, villager.getY());
        data.putDouble(AGGRESSION_Z, villager.getZ());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void filterGuardTarget(LivingChangeTargetEvent event) {
        if (!loaded() || !isGuard(event.getEntity())
                || !(event.getNewTarget() instanceof AmbientFighterEntity fighter)) return;
        if (!mayAttack(event.getEntity(), fighter)) event.setCanceled(true);
    }

    /** Repairs targets restored from entity NBT or assigned by code paths predating the Forge change-target hook. */
    @SubscribeEvent
    public static void validateExistingTarget(LivingEvent.LivingTickEvent event) {
        if (!loaded() || !(event.getEntity() instanceof Mob guard) || guard.tickCount % 10 != 0 || !isGuard(guard)) return;
        if (guard.getTarget() instanceof AmbientFighterEntity fighter && !mayAttack(guard, fighter)) guard.setTarget(null);
    }

    private static boolean mayAttack(LivingEntity guard, AmbientFighterEntity fighter) {
        if (fighter.getAlignment() == FighterAlignment.BAD) return true;
        // Direct, current aggression against a villager always authorizes immediate defense.
        if (fighter.getTarget() instanceof Villager) return true;
        if (!(guard.level() instanceof ServerLevel level)) return false;
        CompoundTag data = fighter.getPersistentData();
        long until = data.getLong(AGGRESSION_UNTIL);
        if (until <= level.getGameTime()) {
            clearExpired(data);
            return false;
        }
        if (!level.dimension().location().toString().equals(data.getString(AGGRESSION_DIMENSION))) return false;
        return guard.distanceToSqr(data.getDouble(AGGRESSION_X), data.getDouble(AGGRESSION_Y),
                data.getDouble(AGGRESSION_Z)) <= DEFENSE_RADIUS_SQR;
    }

    private static AmbientFighterEntity resolveFighter(Entity causing, Entity direct) {
        if (causing instanceof AmbientFighterEntity fighter) return fighter;
        if (direct instanceof AmbientFighterEntity fighter) return fighter;
        if (direct instanceof AbstractKiProjectile projectile && projectile.getOwner() instanceof AmbientFighterEntity fighter)
            return fighter;
        return null;
    }

    private static boolean isGuard(LivingEntity entity) {
        EntityType<?> expected = guardType();
        return expected != null && entity.getType() == expected;
    }

    private static boolean loaded() {
        Boolean cached = guardVillagersLoaded;
        if (cached == null) {
            cached = ModList.get().isLoaded("guardvillagers");
            guardVillagersLoaded = cached;
        }
        return cached;
    }

    private static EntityType<?> guardType() {
        if (!guardTypeResolved) {
            synchronized (GuardVillagersCompat.class) {
                if (!guardTypeResolved) {
                    guardType = ForgeRegistries.ENTITY_TYPES.getValue(
                            ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard"));
                    guardTypeResolved = true;
                }
            }
        }
        return guardType;
    }

    private static void clearExpired(CompoundTag data) {
        data.remove(AGGRESSION_UNTIL); data.remove(AGGRESSION_DIMENSION);
        data.remove(AGGRESSION_X); data.remove(AGGRESSION_Y); data.remove(AGGRESSION_Z);
    }
}
