package com.dmzlivingworld.client.particle;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Living World-owned anime Ki-training visuals; deliberately separate from meditation particles. */
public final class LWKiTrainingParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LivingWorldMod.MOD_ID);
    public static final RegistryObject<SimpleParticleType> FOCUS = TYPES.register("ki_training_focus", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CORE = TYPES.register("ki_training_core", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DRAW = TYPES.register("ki_training_draw", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> PULSE = TYPES.register("ki_training_pulse", () -> new SimpleParticleType(false));
    private LWKiTrainingParticles() {}
}
