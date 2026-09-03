package com.dmzlwfusion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Soft bridge into Living World.  The release intentionally does not compile
 * against one particular LW build: the user's active LW version remains the
 * authority, while this class reads the stable fighter/companion surface.
 */
public final class LivingWorldCompat {
    private static final String BOND_ROOT = "DMZLivingWorldBonds";

    private LivingWorldCompat() {}

    public static boolean isLivingWorldFighter(LivingEntity entity) {
        if (entity == null) return false;
        String className = entity.getClass().getName();
        if (className.equals("com.dmzlivingworld.entity.AmbientFighterEntity")) return true;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        // Newer Living World builds are free to rename the registered entity
        // path. The namespace plus the stable fighter surface is a stronger
        // compatibility signal than requiring "fighter" in the registry id.
        return id != null && "dmzlivingworld".equals(id.getNamespace())
                && hasMethod(entity, "getFighterName")
                && hasMethod(entity, "getBattlePower")
                && hasMethod(entity, "getRace");
    }

    public static UUID companionId(ServerPlayer player) {
        // Prefer LW's public API when it exists. This survives internal NBT changes.
        try {
            Class<?> manager = Class.forName("com.dmzlivingworld.world.LivingBondManager");
            Method method = manager.getMethod("companionId", ServerPlayer.class);
            Object value = method.invoke(null, player);
            if (value instanceof UUID uuid) return uuid;
        } catch (ReflectiveOperationException ignored) {
        }

        // Stable fallback used by the currently inspected LW implementation.
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(BOND_ROOT)) return null;
        CompoundTag root = persistent.getCompound(BOND_ROOT);
        return root.hasUUID("Companion") ? root.getUUID("Companion") : null;
    }

    public static String fighterName(LivingEntity fighter) {
        Object result = invoke(fighter, "getFighterName");
        if (result instanceof String name && !name.isBlank()) return name;
        return fighter.getName().getString();
    }

    public static String raceId(LivingEntity fighter) {
        Object race = invoke(fighter, "getRace");
        if (race == null) return "";
        Object dmz = invoke(race, "dmzId");
        if (dmz instanceof String id) return id.toLowerCase(Locale.ROOT);
        if (race instanceof String id) return id.toLowerCase(Locale.ROOT);
        return race.toString().toLowerCase(Locale.ROOT);
    }

    public static String archetype(LivingEntity fighter) {
        Object type = invoke(fighter, "getArchetype");
        if (type == null) return "MARTIAL_ARTIST";
        if (type instanceof Enum<?> e) return e.name();
        return type.toString().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static int battlePower(LivingEntity fighter) {
        // Fusion deliberately sees Living World's configured visual power. All non-fusion LW
        // systems continue reading their authoritative getBattlePower()/permanent BP directly.
        Object visual = invoke(fighter, "getVisualBattlePower");
        if (visual instanceof Number number) return Math.max(1, number.intValue());
        Object value = invoke(fighter, "getBattlePower");
        if (value instanceof Number number) return Math.max(1, number.intValue());
        return 1;
    }

    public static String bodyColor(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getBodyColor"), "FFFFFF");
    }

    public static String bodyColor2(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getBodyColor2"), bodyColor(fighter));
    }

    public static String bodyColor3(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getBodyColor3"), bodyColor(fighter));
    }

    public static String hairColor(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getHairColor"), "101010");
    }

    public static String eye1Color(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getEye1Color"), "202020");
    }

    public static String eye2Color(LivingEntity fighter) {
        return stringValue(invoke(fighter, "getEye2Color"), eye1Color(fighter));
    }

    public static String auraColor(LivingEntity fighter) {
        Object value = invoke(fighter, "getAuraColor");
        if (value instanceof Number number) return String.format(Locale.ROOT, "%06x", number.intValue() & 0xFFFFFF);
        if (value instanceof String s) return s;
        return hairColor(fighter);
    }


    public static boolean setFighterName(LivingEntity fighter, String name) {
        return invokeVoid(fighter, "setFighterName", new Class<?>[]{String.class}, name);
    }

    public static boolean setBattlePower(LivingEntity fighter, int battlePower) {
        int safe = Math.max(1, battlePower);
        if (invokeVoid(fighter, "setBattlePowerAndRefresh", new Class<?>[]{int.class}, safe)) return true;
        return invokeVoid(fighter, "setBattlePower", new Class<?>[]{int.class}, safe);
    }

    public static boolean setAuraColor(LivingEntity fighter, int rgb) {
        return invokeVoid(fighter, "setAuraColor", new Class<?>[]{int.class}, rgb & 0xFFFFFF);
    }

    /** Stops DMZ charge and Living World's transient aura before a fusion partner is hidden. */
    public static void suppressAura(LivingEntity fighter) {
        if (fighter == null) return;
        invokeVoid(fighter, "suppressActivityAura", new Class<?>[0]);
        invokeVoid(fighter, "setKiCharge", new Class<?>[]{boolean.class}, false);
    }

    public static boolean unavailableForFusion(LivingEntity fighter) {
        if (fighter == null || !fighter.isAlive()) return true;
        if (booleanValue(invoke(fighter, "isDefeated"))) return true;
        if (booleanValue(invoke(fighter, "isCaptive"))) return true;
        if (booleanValue(invoke(fighter, "isNonCombatant"))) return true;
        if (booleanValue(invoke(fighter, "isMeditating"))) return true;
        return fighter instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null;
    }

    public static boolean hasActiveForm(LivingEntity fighter) {
        return booleanValue(invoke(fighter, "isRacialFormActive"))
                || booleanValue(invoke(fighter, "isKaiokenActive"));
    }

    private static boolean hasMethod(Object target, String name) {
        try {
            target.getClass().getMethod(name);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeVoid(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        if (target == null) return false;
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            method.invoke(target, args);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean b && b;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String s && !s.isBlank()) return s;
        return fallback;
    }
}
