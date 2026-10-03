package com.dmzlivingworld.world;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.compat.WorldGuardCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.init.item.WeightItem;
import com.dragonminez.server.util.GravityDeviceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** DMZ-compatible training-weight/gravity rules for Living World fighters. */
public final class FighterWeightGravityManager {
    private static final String WEIGHT_STACK = "LWTrainingWeight";
    private static final String LAST_REJECTION = "LWWeightRejectionTick";
    private static final UUID MOVE_PENALTY = UUID.fromString("63ed10a4-6317-4d33-bb88-f3fca1bad164");
    private static final UUID ATTACK_SPEED_PENALTY = UUID.fromString("160fbbb0-4c0a-4f71-8a29-130188eef815");
    private static final UUID ATTACK_PENALTY = UUID.fromString("019dffd1-a80e-7cf2-b0ad-5a270327f265");
    private static final UUID HEALTH_PENALTY = UUID.fromString("019dffd1-d1e4-7dcb-80f6-fe3fe9e07a7f");
    private static final Map<AmbientFighterEntity, KiPenaltyState> KI_PENALTIES = new WeakHashMap<>();
    private record KiPenaltyState(float base, float applied) {}

    private FighterWeightGravityManager() {}

    public static ItemStack equippedWeight(AmbientFighterEntity fighter) {
        if (fighter == null || !fighter.getLegacyData().contains(WEIGHT_STACK, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        ItemStack stack = ItemStack.of(fighter.getLegacyData().getCompound(WEIGHT_STACK));
        return stack.getItem() instanceof WeightItem ? stack : ItemStack.EMPTY;
    }

    public static void setEquippedWeight(AmbientFighterEntity fighter, ItemStack stack) {
        if (fighter == null) return;
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof WeightItem)) {
            fighter.getLegacyData().remove(WEIGHT_STACK);
            syncWeightAppearance(fighter, ItemStack.EMPTY);
            return;
        }
        ItemStack one = stack.copy();
        one.setCount(1);
        fighter.getLegacyData().put(WEIGHT_STACK, one.save(new CompoundTag()));
        syncWeightAppearance(fighter, one);
    }

    public static int idealWeight(AmbientFighterEntity fighter) {
        if (fighter == null) return 10;
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        double capacity = Math.max(0.0D, fighter.getUnpenalizedDefenseStat()) / Math.max(0.0001D, cfg.getTpIdealBaseDivisor());
        return Math.max(10, (int)Math.round(capacity));
    }

    public static int rawWeight(AmbientFighterEntity fighter) {
        ItemStack stack = equippedWeight(fighter);
        return stack.isEmpty() ? 0 : Math.max(0, WeightItem.getWeight(stack));
    }

    public static int effectiveWeight(AmbientFighterEntity fighter) {
        double gravity = gravityMultiplier(fighter);
        double sensitivity = ConfigManager.getServerConfig().getGravity().getGravitySensitivity();
        double loadFactor = Math.max(0.0001D, 1.0D + (gravity - 1.0D) * sensitivity);
        return (int)Math.round(rawWeight(fighter) * loadFactor);
    }

    public static boolean weightIsSuitable(AmbientFighterEntity fighter, ItemStack stack) {
        if (fighter == null || stack == null || !(stack.getItem() instanceof WeightItem)) return false;
        int ideal = idealWeight(fighter);
        int weight = effectiveWeightOf(fighter, stack);
        return weight >= Math.ceil(ideal * 0.75D) && weight <= Math.floor(ideal * 1.25D);
    }

    private static int effectiveWeightOf(AmbientFighterEntity fighter, ItemStack stack) {
        double gravity = gravityMultiplier(fighter);
        double sensitivity = ConfigManager.getServerConfig().getGravity().getGravitySensitivity();
        return (int)Math.round(Math.max(0, WeightItem.getWeight(stack))
                * Math.max(0.0001D, 1.0D + (gravity - 1.0D) * sensitivity));
    }

    /** Handles a dropped weight before the ordinary weapon/armor arsenal scanner. */
    public static boolean inspectNearbyWeight(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || !(fighter.level() instanceof ServerLevel level)
                || fighter.isDefeated() || fighter.isCaptive() || fighter.isMeditating()
                || fighter.getTarget() != null) return false;
        ItemEntity closest = level.getEntitiesOfClass(ItemEntity.class, fighter.getBoundingBox().inflate(9.0D),
                        e -> e.isAlive() && e.tickCount > 5 && e.getItem().getItem() instanceof WeightItem)
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (closest == null) return false;
        if (fighter.distanceToSqr(closest) > 2.35D * 2.35D) {
            fighter.getLookControl().setLookAt(closest, 35.0F, 35.0F);
            fighter.getNavigation().moveTo(closest, 1.16D);
            return true;
        }
        ItemStack ground = closest.getItem();
        if (!weightIsSuitable(fighter, ground)) {
            long now = level.getGameTime();
            if (now - fighter.getLegacyData().getLong(LAST_REJECTION) >= 100L) {
                fighter.getLegacyData().putLong(LAST_REJECTION, now);
                boolean heavy = effectiveWeightOf(fighter, ground) > idealWeight(fighter);
                fighter.speakKey("dialogue.weight." + (heavy ? "too_heavy." : "too_light.")
                        + fighter.getPersonality().name().toLowerCase(java.util.Locale.ROOT), 70);
                for (var player : level.players()) if (player.distanceToSqr(fighter) <= 24.0D * 24.0D)
                    player.displayClientMessage(Component.translatable("dmzlivingworld.message.weight.rejected",
                            fighter.getFighterName(), idealWeight(fighter)), false);
            }
            return true;
        }
        ItemStack old = equippedWeight(fighter);
        ItemStack accepted = ground.copy(); accepted.setCount(1);
        setEquippedWeight(fighter, accepted);
        ground.shrink(1);
        if (ground.isEmpty()) closest.discard(); else closest.setItem(ground.copy());
        if (!old.isEmpty()) fighter.spawnAtLocation(old);
        fighter.speakKey("dialogue.weight.accepted", 70);
        FighterMemoryManager.refreshLoadedProfile(fighter);
        return true;
    }

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide) return;
        ItemStack weight = equippedWeight(fighter);
        if (!weight.isEmpty() && !weightIsSuitable(fighter, weight)) {
            fighter.spawnAtLocation(weight.copy());
            setEquippedWeight(fighter, ItemStack.EMPTY);
            fighter.speakKey("dialogue.weight.outgrown", 70);
        } else syncWeightAppearance(fighter, weight);
        applyTemporaryPenalties(fighter);
    }

    public static double trainingMultiplier(AmbientFighterEntity fighter) {
        if (fighter == null) return 1.0D;
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        double multiplier = 1.0D;
        if (rawWeight(fighter) > 0 && weightIsSuitable(fighter, equippedWeight(fighter)))
            multiplier *= Math.max(1.0D, cfg.getTpPeakMultiplier());
        double gravityBonus = trainingBonusGravity(fighter);
        multiplier *= Math.max(1.0D, 1.0D + gravityBonus * cfg.getTpGravityBonusPerGravity());
        return multiplier;
    }

    public static double statMultiplier(AmbientFighterEntity fighter) {
        return 1.0D - statReduction(fighter);
    }

    public static double gravityMultiplier(AmbientFighterEntity fighter) {
        if (fighter == null) return 1.0D;
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        if (!cfg.isEnabled()) return 1.0D;
        double ambient = cfg.getWorldGravity(fighter.level().dimension().location().toString());
        ambient = Math.max(ambient, WorldGuardCompat.getGravity(fighter.level(), fighter.blockPosition(), fighter));
        return Math.max(0.0D, ambient + Math.max(0.0D, machineGravity(fighter) - 1.0D));
    }

    private static double gravityResistance(AmbientFighterEntity fighter) {
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        double max = Math.max(1.0D, ConfigManager.getServerConfig().getGameplay().getMaxValue());
        double divisor = Math.max(1.0D, max * cfg.getResistanceStatDivisorRatio());
        return (Math.max(0.0D, fighter.getUnpenalizedDefenseStat()) / divisor) * cfg.getResistanceScale();
    }

    private static double netGravity(AmbientFighterEntity fighter) {
        double gravity = gravityMultiplier(fighter);
        return gravity <= 1.0D ? 0.0D : Math.max(0.0D, gravity - gravityResistance(fighter));
    }

    private static double trainingBonusGravity(AmbientFighterEntity fighter) {
        double net = netGravity(fighter);
        return net >= ConfigManager.getServerConfig().getGravity().getHardStopThreshold() ? 0.0D : net;
    }

    private static double statReduction(AmbientFighterEntity fighter) {
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        if (!cfg.getStatReductionEnabled()) return 0.0D;
        double net = netGravity(fighter);
        double reduction = net <= 0.0D ? 0.0D : Math.max(cfg.getMinStatReduction(),
                Math.min(cfg.getMaxStatReduction(), net * cfg.getStatReductionPerGravity()));
        return Math.min(cfg.getMaxStatReduction(), reduction + weightPenalty(fighter));
    }

    private static double weightPenalty(AmbientFighterEntity fighter) {
        if (rawWeight(fighter) <= 0) return 0.0D;
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        double ratio = effectiveWeight(fighter) / (double)Math.max(1, idealWeight(fighter));
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Class<?> type = Class.forName("com.dmzrevamp.config.WeightMovementPenaltyConfig");
                Field cachedField = type.getDeclaredField("cached"); cachedField.setAccessible(true);
                Object cached = cachedField.get(null);
                Method method = type.getDeclaredMethod("penaltyForRatio", double.class,
                        GeneralServerConfig.GravityConfig.class, cached.getClass());
                method.setAccessible(true);
                return Math.max(0.0D, Math.min(0.99D, ((Number)method.invoke(null, ratio, cfg, cached)).doubleValue()));
            } catch (ReflectiveOperationException ignored) { }
        }
        if (ratio <= cfg.getTpIdealRatioHigh()) return 0.0D;
        double range = Math.max(0.0001D, cfg.getTpOverloadHardRatio() - cfg.getTpIdealRatioHigh());
        return Math.min(cfg.getMaxWeightPenalty(), cfg.getMaxWeightPenalty()
                * (ratio - cfg.getTpIdealRatioHigh()) / range);
    }

    private static void applyTemporaryPenalties(AmbientFighterEntity fighter) {
        GeneralServerConfig.GravityConfig cfg = ConfigManager.getServerConfig().getGravity();
        double net = netGravity(fighter);
        double weight = weightPenalty(fighter);
        double physical = net <= 0.0D ? 0.0D : Math.sqrt(net / 100.0D);
        double move = Math.min(cfg.getMaxMovementPenalty(), physical + weight);
        double attackSpeed = Math.min(cfg.getMaxAttackPenalty(), physical + weight);
        double stats = statReduction(fighter);
        apply(fighter.getAttribute(Attributes.MOVEMENT_SPEED), MOVE_PENALTY, "Living World gravity movement", move);
        apply(fighter.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_PENALTY, "Living World gravity attack speed", attackSpeed);
        apply(fighter.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_PENALTY, "Living World gravity attack", stats);
        apply(fighter.getAttribute(Attributes.MAX_HEALTH), HEALTH_PENALTY, "Living World gravity health", stats);
        applyKiPenalty(fighter, stats);
        if (fighter.getHealth() > fighter.getMaxHealth()) fighter.setHealth(fighter.getMaxHealth());
    }

    private static void applyKiPenalty(AmbientFighterEntity fighter, double penalty) {
        float current = fighter.getKiBlastDamage();
        KiPenaltyState previous = KI_PENALTIES.get(fighter);
        float base = previous != null && Math.abs(current - previous.applied) < 0.001F ? previous.base : current;
        float applied = (float)Math.max(0.0D, base * (1.0D - Math.min(0.99D, penalty)));
        if (Math.abs(current - applied) > 0.001F) fighter.setKiBlastDamage(applied);
        if (penalty <= 0.000001D) KI_PENALTIES.remove(fighter);
        else KI_PENALTIES.put(fighter, new KiPenaltyState(base, applied));
    }

    private static void apply(AttributeInstance attribute, UUID id, String name, double penalty) {
        if (attribute == null) return;
        attribute.removeModifier(id);
        if (penalty > 0.000001D) attribute.addTransientModifier(new AttributeModifier(id, name,
                -Math.min(0.99D, penalty), AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SuppressWarnings("unchecked")
    private static double machineGravity(AmbientFighterEntity fighter) {
        try {
            Field zonesField = GravityDeviceManager.class.getDeclaredField("ZONES"); zonesField.setAccessible(true);
            Object raw = zonesField.get(null);
            if (!(raw instanceof Map<?, ?> zones)) return 0.0D;
            double result = 0.0D;
            String dimension = fighter.level().dimension().location().toString();
            for (Object zone : zones.values()) {
                Method dim = zone.getClass().getDeclaredMethod("dimension"); dim.setAccessible(true);
                Method bounds = zone.getClass().getDeclaredMethod("bounds"); bounds.setAccessible(true);
                Method gravity = zone.getClass().getDeclaredMethod("gravity"); gravity.setAccessible(true);
                if (dimension.equals(dim.invoke(zone)) && ((AABB)bounds.invoke(zone)).contains(fighter.position()))
                    result = Math.max(result, ((Number)gravity.invoke(zone)).doubleValue());
            }
            return result;
        } catch (ReflectiveOperationException ignored) { return 0.0D; }
    }

    private static void syncWeightAppearance(AmbientFighterEntity fighter, ItemStack stack) {
        int id = FighterSpecialItemManager.ACCESSORY_NONE;
        if (stack != null && stack.getItem() instanceof WeightItem item) id = switch (item.getWeightType()) {
            case TURTLE_SHELL -> FighterSpecialItemManager.WEIGHT_TURTLE;
            case WORKOUT_WEIGHTS -> FighterSpecialItemManager.WEIGHT_WORKOUT;
            case PICCOLO_CAPE -> FighterSpecialItemManager.WEIGHT_PICCOLO;
        };
        int current = fighter.getCosmeticAccessoryId();
        if (current >= FighterSpecialItemManager.WEIGHT_TURTLE || id > 0) fighter.setCosmeticAccessoryId(id);
        fighter.getLegacyData().putInt("CosmeticAccessoryId", fighter.getCosmeticAccessoryId());
    }
}
