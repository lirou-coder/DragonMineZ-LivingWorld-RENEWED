package com.dmzlwfusion;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceStatsConfig;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Virtual player-stat profile used only by fusion math; it never mutates NPC combat stats. */
public final class LWFusionProfile {
    private static final String[] STATS = {"STR", "SKP", "STM", "DEF", "VIT", "PWR", "ENE"};
    private final int totalStats;
    private final Map<String, Integer> stats;
    private final Map<String, Double> scales;

    private LWFusionProfile(int totalStats, Map<String, Integer> stats, Map<String, Double> scales) {
        this.totalStats = totalStats;
        this.stats = stats;
        this.scales = scales;
    }

    public int totalStats() { return totalStats; }
    public int stat(String name) { return Math.max(0, stats.getOrDefault(name, 0)); }
    public double scale(String name) { return Math.max(0.0001D, scales.getOrDefault(name, 1.0D)); }

    public static LWFusionProfile from(LivingEntity fighter) {
        Map<String, Double> race = raceScales(LivingWorldCompat.raceId(fighter));
        double[] archetype = archetypeScales(LivingWorldCompat.archetype(fighter));
        Map<String, Double> finalScales = new LinkedHashMap<>();
        for (int i = 0; i < STATS.length; i++) finalScales.put(STATS[i], race.getOrDefault(STATS[i], 1.0D) + archetype[i]);

        double melee = fighter instanceof AmbientFighterEntity npc
                ? npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 1.0D;
        double defense = fighter instanceof AmbientFighterEntity npc ? npc.getDefenseStat() : 1.0D;
        double health = Math.max(0.0D, fighter.getMaxHealth() - 20.0D);
        double ki = fighter instanceof AmbientFighterEntity npc ? npc.getKiBlastDamage() : 1.0D;

        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("STR", equivalent(melee, finalScales.get("STR")));
        values.put("SKP", equivalent(melee, finalScales.get("STR")));
        values.put("STM", equivalent(defense, finalScales.get("DEF")));
        values.put("DEF", equivalent(defense, finalScales.get("DEF")));
        values.put("VIT", equivalent(health, finalScales.get("VIT")));
        values.put("PWR", equivalent(ki, finalScales.get("PWR")));
        values.put("ENE", equivalent(ki, finalScales.get("PWR")));

        // DMZ totalStats has one shared resistance stat. Count DEF once; STM is its second
        // fusion-facing alias and must not inflate similarity/equality.
        int total = values.get("STR") + values.get("SKP") + values.get("DEF")
                + values.get("VIT") + values.get("PWR") + values.get("ENE");
        return new LWFusionProfile(Math.max(1, total), values, finalScales);
    }

    private static int equivalent(double output, double scale) {
        return Math.max(0, (int)Math.round(Math.max(0.0D, output) / Math.max(0.0001D, scale)));
    }

    private static Map<String, Double> raceScales(String raceId) {
        Map<String, Double> sum = new LinkedHashMap<>();
        for (String stat : STATS) sum.put(stat, 0.0D);
        RaceStatsConfig config = ConfigManager.getRaceStats(raceId);
        if (config == null || config.getClasses() == null || config.getClasses().isEmpty()) {
            for (String stat : STATS) sum.put(stat, 1.0D);
            return sum;
        }
        int count = 0;
        for (RaceStatsConfig.ClassStats classStats : config.getClasses().values()) {
            RaceStatsConfig.StatScaling scaling = classStats == null ? null : classStats.getStatScaling();
            if (scaling == null) continue;
            count++;
            add(sum, "STR", scaling.getStrengthScaling());
            add(sum, "SKP", scaling.getStrikePowerScaling());
            add(sum, "STM", scaling.getStaminaScaling());
            add(sum, "DEF", scaling.getDefenseScaling());
            add(sum, "VIT", scaling.getVitalityScaling());
            add(sum, "PWR", scaling.getKiPowerScaling());
            add(sum, "ENE", scaling.getEnergyScaling());
        }
        if (count <= 0) count = 1;
        for (String stat : STATS) sum.put(stat, sum.get(stat) / count);
        return sum;
    }

    private static void add(Map<String, Double> values, String stat, Double value) {
        values.put(stat, values.get(stat) + (value != null && Double.isFinite(value) ? value : 1.0D));
    }

    private static double[] archetypeScales(String archetype) {
        return switch (archetype) {
            case "BRAWLER" -> new double[]{1.0, .7, .6, .4, .6, .3, 1.0};
            case "SPEEDSTER", "SPEED_FIGHTER" -> new double[]{.7, 1.0, .8, .4, .6, .6, 1.6};
            case "GUARDIAN" -> new double[]{1.0, .5, .9, .9, .8, .7, 1.6};
            case "KI_SPECIALIST" -> new double[]{.2, .6, .4, .5, .6, 1.0, 2.0};
            default -> new double[]{1.0, .8, .7, .7, .2, 1.0, 1.2};
        };
    }
}
