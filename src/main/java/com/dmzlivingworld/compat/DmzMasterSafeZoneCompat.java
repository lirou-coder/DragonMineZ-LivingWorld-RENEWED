package com.dmzlivingworld.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;

/**
 * Compatibility boundary for the protected areas around Dragon Mine Z master NPCs.
 * DMZ does not expose a stable public API for this across its 2.2 builds, so the
 * check deliberately uses the registered master base class when available.
 */
public final class DmzMasterSafeZoneCompat {
    private static final double MASTER_SAFE_RADIUS = 32.0D;
    private static volatile Class<?> mastersClass;
    private static volatile boolean resolved;

    private DmzMasterSafeZoneCompat() {}

    public static boolean isInsideMasterSafeZone(LivingEntity entity) {
        return entity != null && entity.level() instanceof ServerLevel level
                && isInsideMasterSafeZone(level, entity.getX(), entity.getY(), entity.getZ());
    }

    public static boolean isInsideMasterSafeZone(ServerLevel level, double x, double y, double z) {
        if (!ModList.get().isLoaded("dragonminez")) return false;
        Class<?> type = resolveMastersClass();
        if (type == null) return false;
        AABB box = new AABB(x, y, z, x, y, z).inflate(MASTER_SAFE_RADIUS, 64.0D, MASTER_SAFE_RADIUS);
        for (LivingEntity master : level.getEntitiesOfClass(LivingEntity.class, box, e -> type.isInstance(e))) {
            double dx = master.getX() - x;
            double dz = master.getZ() - z;
            if (dx * dx + dz * dz <= MASTER_SAFE_RADIUS * MASTER_SAFE_RADIUS) return true;
        }
        return false;
    }

    private static Class<?> resolveMastersClass() {
        if (resolved) return mastersClass;
        synchronized (DmzMasterSafeZoneCompat.class) {
            if (resolved) return mastersClass;
            try {
                mastersClass = Class.forName("com.dragonminez.common.init.entities.MastersEntity");
            } catch (Throwable ignored) {
                mastersClass = null;
            }
            resolved = true;
            return mastersClass;
        }
    }
}
