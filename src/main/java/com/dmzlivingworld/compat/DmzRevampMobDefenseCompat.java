package com.dmzlivingworld.compat;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.dmzlivingworld.world.FighterWeightGravityManager;

/** Accesses DMZ Overhaul's authoritative mob_defense attribute when present. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzRevampMobDefenseCompat {
    private static final ResourceLocation MOB_DEFENSE =
            ResourceLocation.fromNamespaceAndPath("dmzrevamp", "mob_defense");
    private static Attribute resolvedAttribute;

    private DmzRevampMobDefenseCompat() {}

    public static void neutralize(AmbientFighterEntity fighter) {
        if (!installed() || fighter == null) return;
        Attribute attribute = mobDefenseAttribute();
        AttributeInstance instance = attribute == null ? null : fighter.getAttribute(attribute);
        if (instance != null) {
            double value = Math.max(0.0D, fighter.getUnpenalizedDefenseStat()
                    * FighterWeightGravityManager.statMultiplier(fighter));
            instance.setBaseValue(value);
        }
    }

    public static boolean installed() { return ModList.get().isLoaded("dmzrevamp"); }

    public static double defense(AmbientFighterEntity fighter) {
        if (fighter == null || !installed()) return -1.0D;
        Attribute attribute = mobDefenseAttribute();
        AttributeInstance instance = attribute == null ? null : fighter.getAttribute(attribute);
        return instance == null ? -1.0D : Math.max(0.0D, instance.getValue());
    }

    /** Runs before Overhaul's LOWEST handler, closing any window caused by another mod changing the base value. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeDamage(LivingHurtEvent event) {
        if (event.getEntity() instanceof AmbientFighterEntity fighter) neutralize(fighter);
    }

    private static Attribute mobDefenseAttribute() {
        if (resolvedAttribute == null) resolvedAttribute = ForgeRegistries.ATTRIBUTES.getValue(MOB_DEFENSE);
        return resolvedAttribute;
    }
}
