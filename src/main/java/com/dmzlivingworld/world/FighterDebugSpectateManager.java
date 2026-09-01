package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Admin-only camera helper for observing an NPC without influencing its path by following it. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterDebugSpectateManager {
    private static final Map<UUID, ReturnState> ACTIVE = new HashMap<>();

    private record ReturnState(ResourceKey<Level> dimension, double x, double y, double z,
                               float yaw, float pitch, GameType gameType, UUID fighterId) {}

    private FighterDebugSpectateManager() {}

    public static int start(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null || !fighter.isAlive() || !(fighter.level() instanceof ServerLevel)) return 0;
        stop(player, false);
        GameType previous = player.gameMode.getGameModeForPlayer();
        ACTIVE.put(player.getUUID(), new ReturnState(player.serverLevel().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), previous, fighter.getUUID()));
        // Move the real spectator body into the fighter's loaded area before switching cameras.
        // LW despawn protection historically ignored spectators; keeping the body/chunks beside the
        // subject also prevents debug spectate from becoming an accidental lifecycle event.
        ServerLevel targetLevel = (ServerLevel) fighter.level();
        player.teleportTo(targetLevel, fighter.getX(), fighter.getY() + 1.0D, fighter.getZ(), player.getYRot(), player.getXRot());
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(fighter);
        player.displayClientMessage(Component.literal("[Living World] Spectating " + fighter.getFighterName()
                + " • use /lw fighter spectate stop to return.").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    public static int stop(ServerPlayer player) { return stop(player, true); }

    private static int stop(ServerPlayer player, boolean message) {
        if (player == null) return 0;
        ReturnState state = ACTIVE.remove(player.getUUID());
        player.setCamera(player);
        if (state == null) {
            if (message) player.displayClientMessage(Component.literal("[Living World] No Living World spectate session is active.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ServerLevel returnLevel = player.getServer().getLevel(state.dimension());
        if (returnLevel != null) player.teleportTo(returnLevel, state.x(), state.y(), state.z(), state.yaw(), state.pitch());
        player.setGameMode(state.gameType());
        if (message) player.displayClientMessage(Component.literal("[Living World] Returned from NPC spectate.").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ReturnState state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        AmbientFighterEntity found = null;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.getEntity(state.fighterId()) instanceof AmbientFighterEntity fighter && fighter.isAlive()) {
                found = fighter;
                break;
            }
        }
        if (found == null) {
            stop(player, true);
            return;
        }
        // Keep the spectator's actual server-side body close to the camera subject. This does not
        // touch the fighter's AI, health, persistence or navigation, and it keeps its chunks/ticks
        // loaded even while the player camera is attached to the entity.
        if (player.tickCount % 5 == 0 && (player.serverLevel() != found.level() || player.distanceToSqr(found) > 64.0D)) {
            ServerLevel targetLevel = (ServerLevel) found.level();
            player.teleportTo(targetLevel, found.getX(), found.getY() + 1.0D, found.getZ(), player.getYRot(), player.getXRot());
            player.setCamera(found);
        }
    }

    /** True only for the NPC currently owned by this explicit admin spectate session. */
    public static boolean isSpectated(AmbientFighterEntity fighter) {
        if (fighter == null) return false;
        UUID id = fighter.getUUID();
        return ACTIVE.values().stream().anyMatch(state -> id.equals(state.fighterId()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    public static void clearRuntime() { ACTIVE.clear(); }
}
