package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterRace;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Makes Living World Namekians first-class targets of both assimilation implementations. */
public final class NamekAssimilationCompat {
    public static final String ASSIMILATED = "LWAssimilatedPermanently";
    private static final int STRONG_TRUST = 60;

    private NamekAssimilationCompat() {}

    public static boolean tryDmz(ServerPlayer player) {
        AmbientFighterEntity target = target(player);
        if (target == null || !isNamekian(target)) return false;
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
        if (data == null) return false;
        var race = ConfigManager.getRaceCharacter(data.getCharacter().getRaceName());
        if (race == null || !("namekian".equalsIgnoreCase(race.getRacialSkill())
                || "namekianrevamp".equalsIgnoreCase(race.getRacialSkill()))) return false;
        assimilateDmz(player, data, target);
        return true;
    }

    public static boolean tryRevamp(ServerPlayer player, StatsData data) {
        AmbientFighterEntity target = target(player);
        if (target == null || !isNamekian(target)) return false;
        assimilateRevamp(player, data, target);
        return true;
    }

    /** Exact-target fallback called from DMZ's private Namekian handler itself. */
    public static boolean tryDmzTarget(ServerPlayer player, StatsData data, LivingEntity target) {
        if (!(target instanceof AmbientFighterEntity fighter) || !isNamekian(fighter)) return false;
        assimilateDmz(player, data, fighter);
        return true;
    }

    public static boolean isNamekian(AmbientFighterEntity target) {
        return target.isAlive() && target.getRace() == FighterRace.NAMEKIAN;
    }

    private static boolean trusted(ServerPlayer player, AmbientFighterEntity target) {
        return target.isRememberedFor(player) && target.getMemoryRelationship() >= STRONG_TRUST;
    }

    private static boolean canOverpower(StatsData data, LivingEntity target) {
        double damage = Math.max(data.getMaxMeleeDamage(), Math.max(data.getMaxStrikeDamage(), data.getMaxKiDamage()));
        return target.getHealth() <= damage;
    }

    private static void assimilateDmz(ServerPlayer player, StatsData data, AmbientFighterEntity target) {
        var config = ConfigManager.getServerConfig().getRacialSkills();
        if (!config.getNamekianRacialSkill() || !config.getNamekianAssimilationOnNamekNpcs()) return;
        if (data.getResources().getRacialSkillCount() >= config.getNamekianAssimilationAmount()) {
            player.displayClientMessage(Component.translatable("message.dragonminez.racial.limit_reached"), true);
            return;
        }
        boolean consent = trusted(player, target);
        if (!consent && TargetHelper.getRelation(player, target) == TargetHelper.Relation.FRIENDLY) return;
        if (!consent && !player.isCreative() && !canOverpower(data, target)) {
            player.displayClientMessage(Component.translatable("message.dragonminez.racial.target_too_strong"), true);
            return;
        }
        double ratio = Math.max(0D, config.getNamekianAssimilationStatBoost()) * (consent ? 5D : 1D);
        int cap = ConfigManager.getServerConfig().getGameplay().getMaxValue();
        int use = data.getResources().getRacialSkillCount() + 1;
        for (String stat : config.getNamekianAssimilationBoosts()) {
            int current = stat(data, stat);
            int bonus = (int)Math.max(1, Math.min(cap, current * ratio));
            data.getBonusStats().addBonusSplit(stat, "Assimilation_" + use, "+", bonus, true);
        }
        if (config.getNamekianAssimilationHealthRegen() > 0)
            player.heal((float)(player.getMaxHealth() * config.getNamekianAssimilationHealthRegen()));
        finish(player, data, target, consent);
    }

    @SuppressWarnings("unchecked")
    private static void assimilateRevamp(ServerPlayer player, StatsData data, AmbientFighterEntity target) {
        try {
            Class<?> configs = Class.forName("com.dmzrevamp.config.racial.DmzRevampRacialConfigs");
            Object config = configs.getMethod("namekianRevamp").invoke(null);
            if (!bool(config, "enabled") || !bool(config, "allowNamekianNpcs")) return;
            Class<?> skill = Class.forName("com.dmzrevamp.racial.impl.NamekianRevampRacialSkill");
            String cooldownKey = (String)skill.getField("COOLDOWN_KEY").get(null);
            String lastUseTag = (String)skill.getField("LAST_USE_TAG").get(null);
            int cooldownSeconds = ((Number)field(config, "cooldownSeconds")).intValue();
            Class<?> cooldown = Class.forName("com.dmzrevamp.racial.PersistentRacialCooldown");
            Method active = cooldown.getMethod("isActive", ServerPlayer.class, StatsData.class, String.class, String.class, int.class);
            if ((Boolean)active.invoke(null, player, data, cooldownKey, lastUseTag, cooldownSeconds)) return;
            int uses = player.getPersistentData().getInt((String)skill.getField("USES_TAG").get(null));
            double decay = Math.max(0D, number(config, "effectDecayPerUse"));
            double efficiency = Math.max(0D, 1D - uses * decay);
            if (efficiency <= 0D) {
                player.displayClientMessage(Component.translatable("message.dragonminez.racial.limit_reached"), true);
                return;
            }
            boolean consent = trusted(player, target);
            if (!consent && !player.isCreative() && !canOverpower(data, target)) {
                player.displayClientMessage(Component.translatable("message.dragonminez.racial.target_too_strong"), true);
                return;
            }
            double ratio = Math.max(0D, number(config, "statBoostRatio")) * efficiency * (consent ? 5D : 1D);
            double configuredCap = Math.max(0D, number(config, "maxBonusCurrentStatRatio"));
            double capRatio = decay <= 0D ? Double.POSITIVE_INFINITY : configuredCap / decay;
            Class<?> helper = Class.forName("com.dmzrevamp.racial.PermanentRacialBonusHelper");
            Method base = helper.getMethod("getBaseStatValueForRacialBonusCap", StatsData.class, String.class);
            Method add = helper.getMethod("addOrAccumulateBaseCappedStat", StatsData.class, String.class,
                    String.class, double.class, double.class, boolean.class);
            boolean changed = false;
            for (String raw : (List<String>)field(config, "boostedStats")) {
                String stat = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
                int current = ((Number)base.invoke(null, data, stat)).intValue();
                changed |= (Boolean)add.invoke(null, data, stat, "Assimilation", current * ratio, capRatio, true);
            }
            if (!changed) return;
            double heal = number(config, "healthRegenRatio");
            if (heal > 0D) player.heal((float)(player.getMaxHealth() * heal));
            player.getPersistentData().putInt((String)skill.getField("USES_TAG").get(null), uses + 1);
            cooldown.getMethod("markUsed", ServerPlayer.class, StatsData.class, String.class, String.class, int.class)
                    .invoke(null, player, data, cooldownKey, lastUseTag, cooldownSeconds);
            finish(player, data, target, consent);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            player.displayClientMessage(Component.literal("[Living World] Revamp assimilation compatibility failed safely."), true);
        }
    }

    private static void finish(ServerPlayer player, StatsData data, AmbientFighterEntity target, boolean consent) {
        if (!consent && target.getAlignment() != FighterAlignment.BAD) {
            // DMZ's ordinary villainous-kill path removes five moral-alignment points.
            // Assimilation discards rather than kills, so reproduce that consequence explicitly.
            data.getResources().removeAlignment(5);
            WantedManager.recordAssimilationCrime(player, target);
        }
        target.getPersistentData().putBoolean(ASSIMILATED, true);
        FighterAfterlifeManager.markAssimilated(target);
        UUID identity = target.getMemoryRecordId() == null ? target.getUUID() : target.getMemoryRecordId();
        FighterLegacyWorldData.get(player.serverLevel()).markDeadRecord(identity);
        if (!identity.equals(target.getUUID())) FighterLegacyWorldData.get(player.serverLevel()).markDeadRecord(target.getUUID());
        target.discard();
        data.getResources().addRacialSkillCount(1);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        player.displayClientMessage(Component.translatable("message.dragonminez.racial.namek.success"), true);
    }

    private static AmbientFighterEntity target(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(3D));
        AABB search = player.getBoundingBox().expandTowards(look.scale(3D)).inflate(1D);
        for (Entity entity : player.level().getEntities(player, search, e -> e instanceof AmbientFighterEntity && e.isAlive())) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (box.contains(start) || box.clip(start, end).isPresent()) return (AmbientFighterEntity)entity;
        }
        return null;
    }

    private static int stat(StatsData data, String name) {
        return switch (name) {
            case "STR" -> data.getStats().getStrength(); case "SKP" -> data.getStats().getStrikePower();
            case "RES", "DEF", "STM" -> data.getStats().getResistance(); case "VIT" -> data.getStats().getVitality();
            case "PWR" -> data.getStats().getKiPower(); case "ENE" -> data.getStats().getEnergy(); default -> 0;
        };
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException { Field f=object.getClass().getField(name); return f.get(object); }
    private static boolean bool(Object object, String name) throws ReflectiveOperationException { return (Boolean)field(object,name); }
    private static double number(Object object, String name) throws ReflectiveOperationException { return ((Number)field(object,name)).doubleValue(); }
}
