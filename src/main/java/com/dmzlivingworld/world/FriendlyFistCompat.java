package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Extends Dragon Mine Z's Friendly Fist non-lethal intent to native saga NPCs.
 *
 * Living World fighters normally keep Friendly Fist compatibility too, but sanctioned LW spars
 * deliberately bypass this bridge. A sanctioned spar already owns its non-lethal floor/concession
 * logic; applying Friendly Fist to the same hit creates two independent damage authorities.
 * Outside sanctioned spars, Friendly Fist continues to protect LW/native saga NPCs as before.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FriendlyFistCompat {
    private FriendlyFistCompat() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        // Living World's bridge owns only Living World fighters. Native DMZ saga entities keep
        // DMZ's own Friendly Fist handling and can never be altered by this compatibility layer.
        if (!(event.getEntity() instanceof AmbientFighterEntity target) || target.level().isClientSide) return;
        // HOTFIX6: sanctioned LW spars already have a dedicated non-lethal arbiter. Do not let
        // Friendly Fist become a second finishing-damage authority for the same match.
        if (target.isSanctionedMatchParticipant()) return;
        if (event.getAmount() <= 0.0F) return;

        Player player = resolvePlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (player == null || !friendlyFistEnabled(player)) return;

        // Mercy is an additive LW interpretation of Friendly Fist's existing one-HP floor. The
        // damage cap below remains the only non-lethal damage authority; this only queues the
        // social/downed transition after the real damage event finishes.
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ) {
            // Once spared, Friendly Fist itself cannot be used to keep beating the peaceful
            // fighter or farm trust. This also covers player-owned Ki projectiles.
            if (MercyManager.shouldIgnoreFriendlyFistHit(serverPlayer, target)) {
                event.setAmount(0.0F);
                return;
            }
            if (MercyManager.shouldQueueMercyDown(serverPlayer, target, event.getAmount())) {
                MercyManager.queueMercyDown(serverPlayer, target);
            }
        }

        // DEV3: once Friendly Fist has reduced a saga/LW target to its one-HP floor, every
        // later Friendly-Fist hit must also be canceled. Returning early here used to allow
        // the next hit through and kill the supposedly protected target.
        if (target.getHealth() <= 1.0F) {
            event.setAmount(0.0F);
            return;
        }
        float maximumNonLethalDamage = Math.max(0.0F, target.getHealth() - 1.0F);
        if (event.getAmount() > maximumNonLethalDamage) event.setAmount(maximumNonLethalDamage);
    }

    private static Player resolvePlayer(Entity causing, Entity direct) {
        if (causing instanceof Player player) return player;
        if (direct instanceof AbstractKiProjectile projectile && projectile.getOwner() instanceof Player player) return player;
        return null;
    }

    public static boolean friendlyFistEnabled(Player player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(data -> data.getStatus().isFriendlyFistEnabled())
                .orElse(false);
    }
}
