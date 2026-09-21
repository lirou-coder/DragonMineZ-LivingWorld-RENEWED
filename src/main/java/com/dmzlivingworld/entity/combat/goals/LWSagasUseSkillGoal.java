package com.dmzlivingworld.entity.combat.goals;

import com.dmzlivingworld.entity.combat.LivingWorldSagasEntity;
import com.dmzlivingworld.entity.combat.LivingWorldSagasEntity.AiTier;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class LWSagasUseSkillGoal extends Goal {
    private final LivingWorldSagasEntity entity;

    public LWSagasUseSkillGoal(LivingWorldSagasEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.entity.getAiTier() != AiTier.SIMPLE) {
            return false;
        }

        if (this.entity.getTarget() == null || this.entity.isCasting() || this.entity.isComboing() || this.entity.isEvading() || this.entity.isStunned()) {
            return false;
        }

        return this.entity.hasSkillReady();
    }

    @Override
    public boolean canContinueToUse() {
        return this.entity.isCasting();
    }

    @Override
    public void start() {
        this.entity.startFirstAvailableSkill();
    }
}

