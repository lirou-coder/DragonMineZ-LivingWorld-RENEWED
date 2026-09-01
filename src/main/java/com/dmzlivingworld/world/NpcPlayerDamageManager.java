package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.MainEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Read-only diagnostics for Living World attacks against players. Damage is deliberately left
 * entirely to DMZ so defense, blocking and defense penetration can never be overridden here.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcPlayerDamageManager {
    private NpcPlayerDamageManager() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void observeNpcAttackGate(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof AmbientFighterEntity attacker)) return;
        CompoundTag data = player.getPersistentData();
        data.putLong("LWLastNpcAttackGateTick", player.level().getGameTime());
        data.putString("LWLastNpcAttackGateName", attacker.getFighterName());
        data.putDouble("LWLastNpcAttackGateAmount", finiteNonNegative(event.getAmount()));
        data.putBoolean("LWLastNpcAttackGateCanceled", event.isCanceled());
        data.putBoolean("LWLastNpcAttackGateStunned", attacker.hasEffect(MainEffects.STUN.get()));
        data.putBoolean("LWLastNpcAttackGatePostSpar", attacker.isPostSparOpponent(player));
        data.putInt("LWLastNpcAttackGateInvuln", Math.max(0, player.invulnerableTime));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    public static String debugStatus(ServerPlayer player, AmbientFighterEntity fighter) {
        if (player == null || fighter == null) return "No player or Living World fighter available.";
        CompoundTag data = player.getPersistentData();
        long gateAge = data.contains("LWLastNpcAttackGateTick")
                ? Math.max(0L, player.level().getGameTime() - data.getLong("LWLastNpcAttackGateTick")) : -1L;
        String gate = gateAge < 0 ? "no LivingAttack gate observed"
                : String.format(java.util.Locale.ROOT,
                "attack gate %dt ago: canceled %s / stunned %s / post-spar %s / prior invuln %d / amount %.2f",
                gateAge, data.getBoolean("LWLastNpcAttackGateCanceled"), data.getBoolean("LWLastNpcAttackGateStunned"),
                data.getBoolean("LWLastNpcAttackGatePostSpar"), data.getInt("LWLastNpcAttackGateInvuln"),
                data.getDouble("LWLastNpcAttackGateAmount"));
        String peace = fighter.isPostSparOpponent(player)
                ? String.format(java.util.Locale.ROOT, "post-spar peace ACTIVE %dt (%.1fs)",
                fighter.getPostSparPeaceTicks(), fighter.getPostSparPeaceTicks() / 20.0D)
                : "post-spar peace inactive";
        return String.format(java.util.Locale.ROOT,
                "%s - BP %d - ATTACK_DAMAGE %.2f - no LW damage floor/penetration override - %s - %s",
                fighter.getFighterName(), fighter.getBattlePower(),
                fighter.getAttributeValue(Attributes.ATTACK_DAMAGE), gate, peace);
    }
}
