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

/** Prevents DMZ Overhaul's generic mob defense from stacking with Living World's defense. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzRevampMobDefenseCompat {
    private static final ResourceLocation MOB_DEFENSE =
            ResourceLocation.fromNamespaceAndPath("dmzrevamp", "mob_defense");
    private static Attribute resolvedAttribute;

    private DmzRevampMobDefenseCompat() {}

    public static void neutralize(AmbientFighterEntity fighter) {
        if (fighter == null || !ModList.get().isLoaded("dmzrevamp")) return;
        Attribute attribute = mobDefenseAttribute();
        if (attribute == null) return;
        AttributeInstance instance = fighter.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != 0.0D) instance.setBaseValue(0.0D);
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
