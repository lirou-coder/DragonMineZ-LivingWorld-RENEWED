package com.dmzlwfusion;

import com.dragonminez.common.stats.StatsData;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    static boolean allowsDifferentRaceMetamoru() {
        if (!ModList.get().isLoaded("dmzrevamp")) return false;
        try {
            Class<?> config = Class.forName("com.dmzrevamp.config.FusionsRevampedConfig");
            return Boolean.TRUE.equals(config.getMethod("canUseDifferentRaceMetamoru").invoke(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** The revamp cosmetic rows are player-only; use its exact Metamoru armor on a fused NPC. */
    static void applyNpcMetamoruClothes(LivingEntity fused) {
        if (fused == null || !ModList.get().isLoaded("dmzrevamp")) return;
        try {
            Class<?> config = Class.forName("com.dmzrevamp.config.FusionsRevampedConfig");
            if (!Boolean.TRUE.equals(config.getMethod("isRevampedEnabled").invoke(null))) return;
            fused.setItemSlot(EquipmentSlot.FEET, item("gogeta_armor_boots"));
            fused.setItemSlot(EquipmentSlot.LEGS, item("gogeta_armor_leggings"));
            fused.setItemSlot(EquipmentSlot.CHEST, item("gogeta_armor_chestplate"));
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }

    private static ItemStack item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("dragonminez", path));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

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
