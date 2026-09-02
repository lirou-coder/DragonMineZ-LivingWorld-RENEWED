package com.dmzlivingworld.network;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.world.InteractionHand;
import com.dmzlivingworld.world.FighterLifeJoinManager;
import com.dmzlivingworld.world.LivingBondManager;
import com.dmzlivingworld.world.FighterSocialManager;
import com.dmzlivingworld.world.FighterFullPowerManager;
import com.dmzlivingworld.world.FactionRequestManager;
import com.dmzlivingworld.world.FactionRequestMissionManager;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dmzlivingworld.world.ReactiveInteractionManager;
import com.dmzlivingworld.world.SparManager;
import com.dmzlwfusion.LWFusionManager;
import com.dragonminez.common.stats.StatsCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server-authoritative action selected from an inspected fighter profile. */
public record FighterActionPacket(String action, UUID fighterId) {
    public static void encode(FighterActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action == null ? "" : msg.action, 24);
        buf.writeUUID(msg.fighterId == null ? new UUID(0L, 0L) : msg.fighterId);
    }

    public static FighterActionPacket decode(FriendlyByteBuf buf) {
        return new FighterActionPacket(buf.readUtf(24), buf.readUUID());
    }

    public static void handle(FighterActionPacket msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            ctx.enqueueWork(() -> {
                if (msg.fighterId == null) return;
                var entity = sender.serverLevel().getEntity(msg.fighterId);
                if (!(entity instanceof AmbientFighterEntity fighter) || sender.distanceToSqr(fighter) > 12.0D * 12.0D) return;
                if (fighter.isSanctionedMatchParticipant() || fighter.getTarget() != null) {
                    sender.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "The NPC is fighting! You can't interact right now!")
                            .withStyle(net.minecraft.ChatFormatting.RED), false);
                    return;
                }
                if ("deliver".equals(msg.action)) {
                    if (!FactionRequestManager.deliverToReceiver(sender, fighter)) {
                        sender.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "[Living World] This fighter is not the assigned receiver for your active supply request."), false);
                    }
                    return;
                }
                if ("clearmessages".equals(msg.action)) {
                    fighter.clearDialogueHistory();
                    sender.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "[Living World] Cleared recent messages for " + fighter.getFighterName() + "."), false);
                    return;
                }
                // An accepted faction request owns this participant until it releases them. Delivery is
                // handled above because that is the request action itself; all unrelated social/bond/combat
                // requests are rejected server-side even if a stale/modified client tries to send one.
                if (FactionRequestMissionManager.isRequestActionLocked(fighter)) {
                    sender.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "[Living World] " + fighter.getFighterName() + " is busy with an active faction request."), false);
                    return;
                }
                // World Menaces expose no social/action surface. Keep the obsolete spar_x7 packet
                // value rejected here too so an old client or manual packet cannot bypass the rule.
                if (WorldMenaceManager.isWorldMenace(fighter)) {
                    sender.displayClientMessage(net.minecraft.network.chat.Component.literal("[Living World] World Menaces cannot be used for social actions."), false);
                    return;
                }
                if (!"talk".equals(msg.action) && !"spar".equals(msg.action) && !"fullpower".equals(msg.action)) {
                    int relationship = fighter.isRememberedFor(sender) ? fighter.getMemoryRelationship() : 0;
                    String refusal = ReactiveInteractionManager.otherActionRefusal(sender, fighter, msg.action, relationship);
                    if (refusal != null) {
                        fighter.speak(refusal, 90);
                        sender.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "[Living World] " + fighter.getFighterName() + " doesn't want to do that right now."), false);
                        return;
                    }
                }
                if ("talk".equals(msg.action)) FighterSocialManager.talk(sender, fighter);
                else if ("spar".equals(msg.action)) SparManager.request(sender, fighter);
                else if ("join".equals(msg.action)) FighterLifeJoinManager.request(sender, fighter);
                else if ("companion".equals(msg.action)) LivingBondManager.requestCompanion(sender, fighter);
                else if ("fusion".equals(msg.action)) {
                    var stats = sender.getCapability(StatsCapability.INSTANCE).orElse(null);
                    if (stats != null) LWFusionManager.tryMetamoruWithPartner(sender, stats, fighter);
                } else if ("meditate".equals(msg.action)) LivingBondManager.requestSharedMeditation(sender, fighter);
                else if ("fullpower".equals(msg.action)) FighterFullPowerManager.request(sender, fighter);
            });
        }
        ctx.setPacketHandled(true);
    }
}
