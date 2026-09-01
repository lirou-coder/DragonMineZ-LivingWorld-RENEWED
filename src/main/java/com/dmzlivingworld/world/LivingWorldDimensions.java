package com.dmzlivingworld.world;

import com.dmzlivingworld.config.LivingWorldConfig;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Exact DMZ 2.1.3 world keys Living World intentionally supports. */
public final class LivingWorldDimensions {
    public static final ResourceKey<Level> NAMEK = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation("dragonminez", "namek"));

    private LivingWorldDimensions() {}

    public static boolean isSupported(ServerLevel level) {
        return level != null && isSupported(level.dimension());
    }

    public static boolean isSupported(ResourceKey<Level> dimension) {
        if (dimension == null) return false;
        String id = dimension.location().toString().toLowerCase(java.util.Locale.ROOT);
        boolean listed = LivingWorldConfig.dimensionWhitelist().contains(id);
        return LivingWorldConfig.treatDimensionWhitelistAsBlacklist() ? !listed : listed;
    }

    public static FactionRealm realm(ServerLevel level) {
        return level.dimension().equals(NAMEK) ? FactionRealm.NAMEK : FactionRealm.EARTH;
    }

    public static ServerLevel levelFor(MinecraftServer server, FactionRealm realm) {
        return realm == FactionRealm.NAMEK ? server.getLevel(NAMEK) : server.overworld();
    }
}
