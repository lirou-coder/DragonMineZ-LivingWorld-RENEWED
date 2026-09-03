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
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Prevents DMZ Namekian village warriors from treating every Living World fighter as a monster. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NamekianWarriorCompat {
    private static final String UNTIL = "LWNamekianAggressionUntil";
    private static final String X = "LWNamekianAggressionX", Y = "LWNamekianAggressionY", Z = "LWNamekianAggressionZ";
    private static final long MEMORY_TICKS = 1_200L;
    private static final double DEFENSE_RADIUS_SQR = 64.0D * 64.0D;
    private static volatile EntityType<?> warriorType;
    private static volatile boolean warriorTypeResolved;

    private NamekianWarriorCompat() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberNamekianAttack(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0.0F
                || !isDmzNamekian(event.getEntity())) return;
        AmbientFighterEntity fighter = resolveFighter(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (fighter == null) return;
        CompoundTag data = fighter.getPersistentData();
        data.putLong(UNTIL, level.getGameTime() + MEMORY_TICKS);
        data.putDouble(X, event.getEntity().getX());
        data.putDouble(Y, event.getEntity().getY());
        data.putDouble(Z, event.getEntity().getZ());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void filterTarget(LivingChangeTargetEvent event) {
        if (!isNamekianWarrior(event.getEntity())
                || !(event.getNewTarget() instanceof AmbientFighterEntity fighter)) return;
        if (!mayAttack(event.getEntity(), fighter)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void validateTarget(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob warrior) || warrior.tickCount % 10 != 0
                || !isNamekianWarrior(warrior)) return;
        if (warrior.getTarget() instanceof AmbientFighterEntity fighter && !mayAttack(warrior, fighter))
            warrior.setTarget(null);
    }

    private static boolean mayAttack(LivingEntity warrior, AmbientFighterEntity fighter) {
        if (fighter.getAlignment() == FighterAlignment.BAD) return true;
        if (fighter.getTarget() != null && isDmzNamekian(fighter.getTarget())) return true;
        if (!(warrior.level() instanceof ServerLevel level)) return false;
        CompoundTag data = fighter.getPersistentData();
        if (data.getLong(UNTIL) <= level.getGameTime()) {
            data.remove(UNTIL); data.remove(X); data.remove(Y); data.remove(Z);
            return false;
        }
        return warrior.distanceToSqr(data.getDouble(X), data.getDouble(Y), data.getDouble(Z)) <= DEFENSE_RADIUS_SQR;
    }

    private static AmbientFighterEntity resolveFighter(Entity causing, Entity direct) {
        if (causing instanceof AmbientFighterEntity fighter) return fighter;
        if (direct instanceof AmbientFighterEntity fighter) return fighter;
        if (direct instanceof AbstractKiProjectile projectile
                && projectile.getOwner() instanceof AmbientFighterEntity fighter) return fighter;
        return null;
    }

    private static boolean isNamekianWarrior(LivingEntity entity) {
        EntityType<?> expected = warriorType();
        return expected != null && entity.getType() == expected;
    }

    private static EntityType<?> warriorType() {
        if (!warriorTypeResolved) {
            synchronized (NamekianWarriorCompat.class) {
                if (!warriorTypeResolved) {
                    warriorType = ForgeRegistries.ENTITY_TYPES.getValue(
                            ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_warrior"));
                    warriorTypeResolved = true;
                }
            }
        }
        return warriorType;
    }

    private static boolean isDmzNamekian(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && "dragonminez".equals(id.getNamespace())
                && (id.getPath().startsWith("namek_") || "cc_namekian".equals(id.getPath()));
    }
}
