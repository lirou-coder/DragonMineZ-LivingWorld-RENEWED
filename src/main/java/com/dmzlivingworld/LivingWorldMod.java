package com.dmzlivingworld;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.config.LivingWorldClientConfig;
import com.dmzlivingworld.entity.LWEntities;
import com.dmzlivingworld.entity.WorldMenaceFighterEntity;
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
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;

/**
 * DragonMine Z: Living World — roaming encounter branch.
 *
 * This branch deliberately contains no settlement/town systems. The world is made
 * livelier through DMZ-native roaming fighters, small dynamic encounters,
 * personality/power-aware decisions and the older Frieza hunt loop.
 */
@Mod(LivingWorldMod.MOD_ID)
@SuppressWarnings("removal")
public final class LivingWorldMod {
    public static final String MOD_ID = "dmzlivingworld";

    public LivingWorldMod() {
        verifyNeutralFighterHierarchy();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LWEntities.ENTITY_TYPES.register(modBus);
        LWKiTrainingParticles.TYPES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LivingWorldConfig.SPEC, MOD_ID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LivingWorldClientConfig.SPEC, MOD_ID + "-client.toml");
        DBZMeditation.init(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerAttributes);
    }

    private static void verifyNeutralFighterHierarchy() {
        if (Monster.class.isAssignableFrom(AmbientFighterEntity.class)
                || Enemy.class.isAssignableFrom(AmbientFighterEntity.class)) {
            throw new IllegalStateException("Ambient fighters must not inherit Monster or Enemy");
        }
        if (!Enemy.class.isAssignableFrom(WorldMenaceFighterEntity.class)) {
            throw new IllegalStateException("World Menaces must retain the Enemy classification");
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // These calls only register SimpleChannel messages. Keeping them inside an
        // enqueueWork future makes Forge's parallel mod-gather barrier wait for work
        // which does not require the main thread and used to be able to deadlock with
        // class/event-bus initialization. Register them directly during common setup.
        LWNetwork.register();
        FusionAnimationNetwork.register();
        DBZMeditation.commonSetup();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(LWEntities.AMBIENT_FIGHTER.get(), AmbientFighterEntity.createAttributes().build());
        event.put(LWEntities.WORLD_MENACE_FIGHTER.get(), AmbientFighterEntity.createAttributes().build());
    }
}
