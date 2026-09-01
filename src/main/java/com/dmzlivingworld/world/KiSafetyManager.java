package com.dmzlivingworld.world;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Player-facing Ki Security for Living World-owned DMZ projectiles only.
 *
 * Player-facing order: Normal -> Player Blocks -> Player + World -> Ki Off.
 * Stored numeric values preserve 1.8.2 compatibility:
 * 0 Normal; 1 Player + World (legacy Safe); 2 Ki Off (legacy Off); 3 Player Blocks.
 *
 * None of these modes reduce damage to players. Player-fired and native DMZ Ki are untouched.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KiSafetyManager {
    private static final String TAG_LW_OWNED = "dmzlivingworld_owned_ki";
    private static final String TAG_NATIVE_BLOCK_DESTRUCTION = "dmzlivingworld_native_block_destruction";
    private static int lastMode = -1;
    private static final ThreadLocal<AbstractKiProjectile> ACTIVE_LW_EXPLOSION = new ThreadLocal<>();

    private KiSafetyManager() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractKiProjectile projectile)) return;
        if (!markAndCheckLivingWorldOwned(projectile)) return;
        rememberNativeDestruction(projectile);
        apply(projectile);
        if (LivingWorldConfig.npcKiMode() == 2) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        int mode = LivingWorldConfig.npcKiMode();
        if (mode != lastMode) {
            lastMode = mode;
            reconfigureLoadedFighters(event);
            reconfigureLoadedProjectiles(event);
        }
        // New projectiles are configured at EntityJoinLevelEvent. Re-scan only for the two
        // strict modes as a small safety net against projectiles spawned by unusual DMZ paths.
        if (now % 5L != 0L || (mode != 1 && mode != 2)) return;
        reconfigureLoadedProjectiles(event);
    }

    /**
     * Explosion-based Ki (notably DMZ laser impacts) bypasses per-block direct-destruction calls.
     * Filter only the affected block list; the affected entity list is deliberately untouched so
     * player damage remains native in every Ki-enabled mode.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AbstractKiProjectile projectile = ACTIVE_LW_EXPLOSION.get();
        boolean livingWorldKi = projectile != null && projectile.level() == level && markAndCheckLivingWorldOwned(projectile);

        if (livingWorldKi) {
            int mode = LivingWorldConfig.npcKiMode();
            if (mode == 1 || mode == 2) {
                event.getAffectedBlocks().clear();
                return;
            }
            if (mode == 3) {
                event.getAffectedBlocks().removeIf(pos -> PlayerPlacedBlockData.get(level).isPlayerPlaced(level, pos));
            }
        }

        // Any blocks still in Forge's final affected list are about to be destroyed. Forget
        // tracked placements even for TNT/creepers so stale coordinates do not become immortal.
        PlayerPlacedBlockData placed = PlayerPlacedBlockData.get(level);
        for (BlockPos pos : event.getAffectedBlocks()) placed.forget(pos);
    }

    private static void reconfigureLoadedProjectiles(TickEvent.ServerTickEvent event) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            AABB box = player.getBoundingBox().inflate(256.0D);
            for (AbstractKiProjectile projectile : level.getEntitiesOfClass(AbstractKiProjectile.class, box)) {
                if (!seen.add(projectile.getUUID()) || !markAndCheckLivingWorldOwned(projectile)) continue;
                rememberNativeDestruction(projectile);
                apply(projectile);
            }
        }
    }

    private static void reconfigureLoadedFighters(TickEvent.ServerTickEvent event) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            for (AmbientFighterEntity fighter : level.getEntitiesOfClass(AmbientFighterEntity.class, player.getBoundingBox().inflate(512.0D))) {
                if (seen.add(fighter.getUUID())) FighterCombatDirector.configure(fighter);
            }
        }
    }

    private static void apply(AbstractKiProjectile projectile) {
        int mode = LivingWorldConfig.npcKiMode();
        if (mode == 2) {
            projectile.discard();
            return;
        }
        if (mode == 1) {
            projectile.setBlockDestructionEnabled(false);
            return;
        }
        restoreNativeDestruction(projectile);
    }

    /** Called by the DMZ projectile mixin before DMZ decides whether one block may be destroyed. */
    public static boolean shouldProtectBlock(AbstractKiProjectile projectile, BlockPos pos) {
        if (projectile == null || pos == null || !markAndCheckLivingWorldOwned(projectile)) return false;
        int mode = LivingWorldConfig.npcKiMode();
        if (mode == 1 || mode == 2) return true;
        if (mode != 3 || projectile instanceof KiLaserEntity || !(projectile.level() instanceof ServerLevel level)) return false;
        return PlayerPlacedBlockData.get(level).isPlayerPlaced(level, pos);
    }

    /** Called after a native direct Ki block-removal succeeds, preventing stale ownership markers. */
    public static void onDirectKiBlockDestroyed(AbstractKiProjectile projectile, BlockPos pos) {
        if (projectile == null || pos == null || !markAndCheckLivingWorldOwned(projectile)) return;
        if (projectile.level() instanceof ServerLevel level) PlayerPlacedBlockData.get(level).forget(pos);
    }


    /** Brackets DMZ laser explosion creation so Forge's Detonate event can identify this LW source without altering DMZ damage. */
    public static void beginLaserExplosion(AbstractKiProjectile projectile) {
        if (projectile != null && markAndCheckLivingWorldOwned(projectile)) ACTIVE_LW_EXPLOSION.set(projectile);
    }

    public static void endLaserExplosion(AbstractKiProjectile projectile) {
        if (ACTIVE_LW_EXPLOSION.get() == projectile) ACTIVE_LW_EXPLOSION.remove();
    }

    private static boolean markAndCheckLivingWorldOwned(AbstractKiProjectile projectile) {
        CompoundTag data = projectile.getPersistentData();
        if (data.getBoolean(TAG_LW_OWNED)) return true;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof AmbientFighterEntity)) return false;
        data.putBoolean(TAG_LW_OWNED, true);
        return true;
    }

    private static void rememberNativeDestruction(AbstractKiProjectile projectile) {
        CompoundTag data = projectile.getPersistentData();
        if (!data.contains(TAG_NATIVE_BLOCK_DESTRUCTION)) {
            data.putBoolean(TAG_NATIVE_BLOCK_DESTRUCTION, projectile.isBlockDestructionEnabled());
        }
    }

    private static void restoreNativeDestruction(AbstractKiProjectile projectile) {
        CompoundTag data = projectile.getPersistentData();
        if (data.contains(TAG_NATIVE_BLOCK_DESTRUCTION)) {
            projectile.setBlockDestructionEnabled(data.getBoolean(TAG_NATIVE_BLOCK_DESTRUCTION));
        }
    }
}
