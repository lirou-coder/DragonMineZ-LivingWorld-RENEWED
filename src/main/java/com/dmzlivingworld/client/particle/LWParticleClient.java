package com.dmzlivingworld.client.particle;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LWParticleClient {
    private LWParticleClient() {}
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event){
        event.registerSpriteSet(LWKiTrainingParticles.FOCUS.get(), KiTrainingFocusParticle.Provider::new);
        event.registerSpriteSet(LWKiTrainingParticles.CORE.get(), KiTrainingCoreParticle.Provider::new);
        event.registerSpriteSet(LWKiTrainingParticles.DRAW.get(), KiTrainingDrawParticle.Provider::new);
        event.registerSpriteSet(LWKiTrainingParticles.PULSE.get(), KiTrainingPulseParticle.Provider::new);
    }
}
