package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Cooldowns;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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

    /**
     * DMZ has already resolved defense, blocking, Ki Protection and racial mitigation by the
     * LivingDamage phase. A good fighter therefore judges lethality from this final amount and
     * applies the same knocked-down state used by a player's Friendly Fist.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void keepGoodFighterAttacksNonLethal(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide
                || event.getAmount() <= 0.0F) return;
        AmbientFighterEntity attacker = responsibleFighter(
                event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || attacker.getAlignment() != FighterAlignment.GOOD
                || attacker.isSanctionedMatchParticipant()
                || player.getHealth() - event.getAmount() > 0.0F) return;

        // LivingDamageEvent is the final damage phase (after mitigation, blocking and
        // penetration). Only preserve the player when that resolved damage is lethal.
        float safeHealth = Math.max(1.0F, player.getMaxHealth() * 0.05F);
        float nonLethalDamage = Math.max(0.0F, player.getHealth() - safeHealth);
        if (nonLethalDamage <= 0.0F) event.setCanceled(true);
        else event.setAmount(nonLethalDamage);

        // If the player was already at or below the safety floor, cancellation must
        // still leave exactly 5% health rather than allowing a zero-health transition.
        if (event.isCanceled()) player.setHealth(safeHealth);

        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            int knockdownTicks = 5 * 20;
            stats.getStatus().setKnockedDown(true);
            stats.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_DURATION, knockdownTicks);
            stats.getCooldowns().setCooldown(Cooldowns.KNOCKDOWN_INVULN, knockdownTicks);
            stats.getCharacter().clearActiveForm();
            stats.getCharacter().clearActiveStackForm();
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        });

        attacker.setTarget(null);
        attacker.setLastHurtByMob(null);
        attacker.setLastHurtMob(null);
        attacker.setAggressive(false);
        attacker.interruptCombo();
        attacker.stopCasting();
        attacker.setAttacking(false);
        FighterCombatDirector.reset(attacker);
        attacker.getNavigation().stop();
        attacker.speakKey(knockoutWarning(attacker.getPersonality()), 90);
    }

    private static AmbientFighterEntity responsibleFighter(Entity source, Entity direct) {
        if (source instanceof AmbientFighterEntity fighter) return fighter;
        if (direct instanceof AbstractKiProjectile projectile
                && projectile.getOwner() instanceof AmbientFighterEntity fighter) return fighter;
        return null;
    }

    private static String knockoutWarning(FighterPersonality personality) {
        return switch (personality) {
            case PROUD -> "dialogue.combat.player_knockout.proud";
            case AGGRESSIVE -> "dialogue.combat.player_knockout.aggressive";
            case CALM -> "dialogue.combat.player_knockout.calm";
            case CAUTIOUS -> "dialogue.combat.player_knockout.cautious";
            case HEROIC -> "dialogue.combat.player_knockout.heroic";
        };
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
