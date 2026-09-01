package com.dmzlivingworld;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.config.LivingWorldClientConfig;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.network.LWNetwork;
import com.dmzlivingworld.client.particle.LWKiTrainingParticles;
import com.dmzlwfusion.network.FusionAnimationNetwork;
import com.kunyo.dbzmeditation.DBZMeditation;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * DragonMine Z: Living World — roaming encounter branch.
 *
 * This branch deliberately contains no settlement/town systems. The world is made
 * livelier through DMZ-native roaming fighters, small dynamic encounters,
 * personality/power-aware decisions and the older Frieza hunt loop.
 */
@Mod(LivingWorldMod.MOD_ID)
public final class LivingWorldMod {
    public static final String MOD_ID = "dmzlivingworld";

    public LivingWorldMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LWEntities.ENTITY_TYPES.register(modBus);
        LWKiTrainingParticles.TYPES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LivingWorldConfig.SPEC, MOD_ID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LivingWorldClientConfig.SPEC, MOD_ID + "-client.toml");
        LWNetwork.register();
        FusionAnimationNetwork.register();
        DBZMeditation.init(modBus);
        modBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(LWEntities.AMBIENT_FIGHTER.get(), AmbientFighterEntity.createAttributes().build());
    }
}
