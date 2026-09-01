package com.dmzlwfusion;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Soft bridge for dmzrevamp's fusion bonuses and partner-scale addition. */
final class DmzRevampFusionCompat {
    private static final String SCALE_TAG = "DmzRevampFusionScale";

    private DmzRevampFusionCompat() {}

    static boolean applyIfEnabled(StatsData player, LWFusionProfile npc, int playerTotal) {
        if (!ModList.get().isLoaded("dmzrevamp") || player == null || npc == null) return false;
        try {
            Class<?> configClass = Class.forName("com.dmzrevamp.config.FusionsRevampedConfig");
            if (!Boolean.TRUE.equals(configClass.getMethod("isRevampedEnabled").invoke(null))) return false;
            Object root = configClass.getMethod("get").invoke(null);
            Object fusion = field(root, "fusionRevamped");
            Set<String> boosts = new HashSet<>();
            Object rawBoosts = field(fusion, "fusionBoosts");
            if (rawBoosts instanceof String[] values) Arrays.stream(values)
                    .map(v -> v.toUpperCase(Locale.ROOT)).forEach(boosts::add);

            double min = number(fusion, "metamoruMinBonus");
            double max = number(fusion, "metamoruMaxBonus");
            double similarity = Math.min(Math.max(1, playerTotal), npc.totalStats())
                    / (double)Math.max(Math.max(1, playerTotal), npc.totalStats());
            double ratio = min + similarity * (max - min);

            player.getBonusStats().removeAllBonuses("Fusion");
            for (String stat : boosts) {
                int value = npc.stat(stat);
                if (value > 0) player.getBonusStats().addBonusSplit(stat, "Fusion", "+", value * ratio, true);
            }
            if (Boolean.TRUE.equals(field(fusion, "scaleAddition"))) {
                CompoundTag scales = new CompoundTag();
                for (String stat : boosts) scales.putDouble(stat, npc.scale(stat));
                CompoundTag appearance = player.getStatus().getOriginalAppearance();
                if (appearance == null) {
                    appearance = new CompoundTag();
                    player.getStatus().setOriginalAppearance(appearance);
                }
                appearance.put(SCALE_TAG, scales);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    static void clear(StatsData data) {
        if (!ModList.get().isLoaded("dmzrevamp") || data == null) return;
        try {
            Class<?> logic = Class.forName("com.dmzrevamp.revamp.fusion.FusionRevampLogic");
            Method clear = logic.getMethod("clearFusionBonuses", StatsData.class);
            clear.invoke(null, data);
        } catch (ReflectiveOperationException ignored) {}
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getField(name);
        return field.get(owner);
    }

    private static double number(Object owner, String name) throws ReflectiveOperationException {
        return ((Number)field(owner, name)).doubleValue();
    }
}
