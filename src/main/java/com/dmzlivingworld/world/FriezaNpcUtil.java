package com.dmzlivingworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Spawns the actual DragonMineZ Frieza-soldier entity types; no replica entity. */
public final class FriezaNpcUtil {
    private FriezaNpcUtil() {}

    public static Mob spawnSoldier(ServerLevel level, BlockPos pos, LivingEntity initialTarget,
                                   RandomSource random, String path, String... tags) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", path));
        if (type == null) return null;
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) return null;
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) return null;
        for (String tag : tags) mob.addTag(tag);
        if (initialTarget != null) mob.setTarget(initialTarget);
        if (!level.addFreshEntity(mob)) return null;
        return mob;
    }

    public static UUID uuidOrNull(Mob mob) {
        return mob == null ? null : mob.getUUID();
    }
}
