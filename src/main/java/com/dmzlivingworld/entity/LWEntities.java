package com.dmzlivingworld.entity;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class LWEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LivingWorldMod.MOD_ID);

    public static final RegistryObject<EntityType<AmbientFighterEntity>> AMBIENT_FIGHTER = ENTITY_TYPES.register(
            "ambient_fighter",
            () -> EntityType.Builder.of(AmbientFighterEntity::new, MobCategory.CREATURE)
                    .sized(0.62F, 1.86F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(LivingWorldMod.MOD_ID + ":ambient_fighter")
    );
    private LWEntities() {}
}
