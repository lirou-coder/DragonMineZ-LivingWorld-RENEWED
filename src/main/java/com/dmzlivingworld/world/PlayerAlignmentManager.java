package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies Living World moral acts through DMZ's authoritative Resources alignment API. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerAlignmentManager {
    private static final String FRACTION = "DMZLivingWorldAlignmentFraction";
    private static final double NORMAL_KILL_LOSS = 5.0D;
    private static final double SELF_DEFENSE_LOSS = NORMAL_KILL_LOSS * 0.10D;
    private static final double GOOD_ACT_GAIN = 2.0D;

    private PlayerAlignmentManager() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFighterKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof AmbientFighterEntity fighter)
                || fighter.level().isClientSide || WorldMenaceManager.isWorldMenace(fighter)
                || fighter.getPersistentData().contains("DMZLWNpcFusionTemp")) return;
        ServerPlayer player = responsiblePlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player == null) return;
        long now = player.serverLevel().getServer().overworld().getGameTime();
        adjust(player, PeacekeeperManager.isNpcAggressorFor(fighter, player, now)
                ? -SELF_DEFENSE_LOSS : -NORMAL_KILL_LOSS);
    }

    public static void rewardGoodAct(ServerPlayer player) {
        adjust(player, GOOD_ACT_GAIN);
    }

    /** DMZ stores integral alignment, so fractional self-defense loss is retained between acts. */
    private static void adjust(ServerPlayer player, double amount) {
        if (player == null || amount == 0.0D) return;
        CompoundTag persistent = player.getPersistentData();
        double accumulated = persistent.getDouble(FRACTION) + amount;
        // Floor negative fractions as well: the first 0.5 self-defense penalty becomes visible
        // immediately, while the +0.5 remainder makes the following identical act cost zero.
        // Across two acts this remains exactly 90% below DMZ's normal 5-point penalty.
        int whole = (int)Math.floor(accumulated);
        persistent.putDouble(FRACTION, accumulated - whole);
        if (whole == 0) return;
        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            int before = data.getResources().getAlignment();
            if (whole > 0) data.getResources().addAlignment(whole);
            else data.getResources().removeAlignment(-whole);
            if (data.getResources().getAlignment() != before) {
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            }
        });
    }

    private static ServerPlayer responsiblePlayer(Entity source, Entity direct) {
        if (source instanceof ServerPlayer player) return player;
        // DMZ attributes Ki kills through the projectile owner, not through the projectile entity.
        if (direct instanceof AbstractKiProjectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) return player;
        if (source instanceof AbstractKiProjectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) return player;
        return direct instanceof ServerPlayer player ? player : null;
    }
}
