package com.dmzlivingworld.entity;

import com.dmzlivingworld.entity.combat.LivingWorldSagasEntity;
import com.dmzlivingworld.world.WorldMenaceSummonManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/** Hostile structural variant used exclusively by Herobrine and Experiment X-7. */
public final class WorldMenaceFighterEntity extends AmbientFighterEntity implements Enemy {
    public WorldMenaceFighterEntity(EntityType<? extends LivingWorldSagasEntity> type, Level level) {
        super(type, level);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (reason == MobSpawnType.COMMAND && level instanceof ServerLevel serverLevel) {
            WorldMenaceSummonManager.initializeCommandSpawn(this, serverLevel);
        }
        return result;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            // `/summon ... {nbt}` skips finalizeSpawn; repair that path before ordinary Fighter AI runs.
            WorldMenaceSummonManager.initializeCommandSpawn(this, serverLevel);
        }
        super.tick();
    }
}
