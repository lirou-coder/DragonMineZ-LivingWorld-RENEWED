package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Records and maintains player block provenance for selective Living World Ki protection. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerPlacedBlockTracker {
    private PlayerPlacedBlockTracker() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                PlayerPlacedBlockData.get(level).remember(snapshot.getPos());
            }
        } else {
            PlayerPlacedBlockData.get(level).remember(event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        PlayerPlacedBlockData.get(level).forget(event.getPos());
    }

    /** Fluid replacement means the original player-placed block no longer exists. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidReplace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        PlayerPlacedBlockData.get(level).forget(event.getPos());
    }

    /**
     * Pistons physically move block identity. Transfer provenance with every pushed/pulled
     * player block instead of leaving an immortal marker at the old coordinate.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPistonMove(PistonEvent.Pre event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;

        PlayerPlacedBlockData data = PlayerPlacedBlockData.get(level);
        Set<BlockPos> trackedMoved = new HashSet<>();
        for (BlockPos source : resolver.getToPush()) {
            if (data.isPlayerPlaced(source)) trackedMoved.add(new BlockPos(source.getX(), source.getY(), source.getZ()));
        }

        // Clear the old positions as one batch before adding destinations. This matters when
        // a line of adjacent tracked blocks is pushed: one source can be another's destination.
        for (BlockPos source : resolver.getToPush()) data.forget(source);
        for (BlockPos destroyed : resolver.getToDestroy()) data.forget(destroyed);

        Direction movement = event.getPistonMoveType().isExtend
                ? event.getDirection() : event.getDirection().getOpposite();
        for (BlockPos source : trackedMoved) data.remember(source.relative(movement));
    }
}
