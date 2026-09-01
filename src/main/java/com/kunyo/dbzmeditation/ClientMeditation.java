package com.kunyo.dbzmeditation;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(
    modid = DBZMeditation.OWNER_MODID,
    value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class ClientMeditation {
    private static final ResourceLocation ANIME_KI_MOTE_TEXTURE =
        new ResourceLocation(
            DBZMeditation.MODID,
            "textures/particle/anime_ki_mote.png"
        );
    private static final ResourceLocation ANIME_KI_WISP_TEXTURE =
        new ResourceLocation(
            DBZMeditation.MODID,
            "textures/particle/anime_ki_wisp.png"
        );
    private static final ResourceLocation ANIME_GROUND_FLARE_TEXTURE =
        new ResourceLocation(
            DBZMeditation.MODID,
            "textures/particle/anime_ground_flare.png"
        );
    private static final ResourceLocation KI_GLOW_TEXTURE =
        new ResourceLocation(
            DBZMeditation.MODID,
            "textures/particle/ki_glow.png"
        );

    public static final KeyMapping MEDITATE_KEY =
        new KeyMapping(
            "key.dbzmeditation.meditate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.dmzlivingworld"
        );

    private ClientMeditation() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(
        RegisterKeyMappingsEvent event
    ) {
        event.register(MEDITATE_KEY);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(
        RegisterParticleProvidersEvent event
    ) {
        event.registerSpriteSet(
            DBZMeditation.MEDITATION_GLYPH.get(),
            MeditationGlyphParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.KI_MOTE.get(),
            KiGlowParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.KI_ABSORB.get(),
            AbsorptionKiParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.TRANSCENDENT_CORE.get(),
            TranscendentCoreParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.KI_WISP.get(),
            KiAuraWispParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.GROUND_RUNE.get(),
            GroundRuneParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.KI_BURST.get(),
            AnimeKiBurstParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.BREAKTHROUGH_CORE.get(),
            BreakthroughCoreParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.BREAKTHROUGH_SIGIL.get(),
            BreakthroughSigilParticle.Provider::new
        );
        event.registerSpriteSet(
            DBZMeditation.BREAKTHROUGH_SHARD.get(),
            BreakthroughShardParticle.Provider::new
        );
    }

    public static void handleStatePacket(
        MeditationNetwork.MeditationStatePacket msg
    ) {
        boolean hadState =
            ClientForgeEvents.stateInitialized;

        ClientForgeEvents.serverMeditating =
            msg.active();

        ClientForgeEvents.meditationTicks =
            msg.ticks();

        ClientForgeEvents.sessionTp =
            msg.sessionTp();

        ClientForgeEvents.groupCount =
            msg.groupCount();

        String previousStatGains =
            ClientForgeEvents.sessionStatGains;
        String updatedStatGains =
            msg.sessionStatGains();
        String newBreakthrough =
            ClientForgeEvents.findNewStatGain(
                previousStatGains,
                updatedStatGains
            );

        ClientForgeEvents.sessionStatGains =
            updatedStatGains;

        if (hadState
            && msg.active()
            && !newBreakthrough.isEmpty()) {
            ClientForgeEvents.breakthroughStat =
                newBreakthrough;
            ClientForgeEvents.breakthroughAmount = Math.max(1,
                ClientForgeEvents.statAmount(updatedStatGains, newBreakthrough)
                    - ClientForgeEvents.statAmount(previousStatGains, newBreakthrough));
            ClientForgeEvents.breakthroughPresentationTicks =
                92;
        }
        if (msg.active()
            && ClientForgeEvents.serverMeditating
            && MeditationConfig.CLIENT.stageTransitionEffects.get()
            && msg.stage() > ClientForgeEvents.serverStage) {

            ClientForgeEvents.stageTransitionStage =
                msg.stage();

            ClientForgeEvents.stageTransitionTicks =
                50;
        }

        ClientForgeEvents.serverStage =
            msg.stage();

        ClientForgeEvents.serverMultiplier =
            msg.multiplier();

        ClientForgeEvents.serverStageProgress =
            msg.stageProgress();

        ClientForgeEvents.fastTesting =
            msg.fastTesting();

        MeditationConfig.setFastTesting(
            msg.fastTesting()
        );

        ClientForgeEvents.debugView =
            msg.debugView();

        ClientForgeEvents.energyPercent =
            msg.energyPercent();

        ClientForgeEvents.staminaPercent =
            msg.staminaPercent();

        ClientForgeEvents.activeForm =
            msg.activeForm();

        ClientForgeEvents.formMastery =
            msg.formMastery();

        ClientForgeEvents.formMasteryMax =
            msg.formMasteryMax();

        ClientForgeEvents.sessionMasteryGain =
            msg.sessionMasteryGain();

        ClientForgeEvents.stateInitialized = true;
    }

    public static void handleSummaryPacket(
        MeditationNetwork.MeditationSummaryPacket msg
    ) {
        if (!MeditationConfig.CLIENT.sessionSummary.get()) {
            ClientForgeEvents.summary = null;
            ClientForgeEvents.summaryTicksRemaining = 0;
            ClientForgeEvents.summaryTotalTicks = 0;
            return;
        }
        ClientForgeEvents.summary = msg;
        ClientForgeEvents.summaryTicksRemaining = 5 * 20;
        ClientForgeEvents.summaryTotalTicks = ClientForgeEvents.summaryTicksRemaining;
    }

    @Mod.EventBusSubscriber(
        modid = DBZMeditation.OWNER_MODID,
        value = Dist.CLIENT
    )
    public static final class ClientForgeEvents {
        private static int meditationTicks = 0;
        private static int sessionTp = 0;
        private static int groupCount = 0;
        private static String sessionStatGains = "";
        private static int serverStage = 0;
        private static int serverMultiplier = 1;
        private static float serverStageProgress = 0.0F;
        private static boolean serverMeditating = false;
        private static boolean sentCancel = false;
        // Physical key-edge guard. KeyMapping click counts can repeat while a key is held on
        // some client/input setups; meditation must toggle exactly once per deliberate press.
        private static boolean meditateKeyWasDown = false;
        private static boolean stateInitialized = false;

        private static boolean fastTesting = false;
        private static boolean debugView = false;

        private static float energyPercent = 0.0F;
        private static float staminaPercent = 0.0F;
        private static String activeForm = "";
        private static double formMastery = 0.0D;
        private static double formMasteryMax = 0.0D;
        private static double sessionMasteryGain = 0.0D;


        private static float hudPresence = 0.0F;
        private static float visualStage = 0.0F;
        private static float visualPresence = 0.0F;
        private static float glyphSpawnBudget = 0.0F;
        private static int glyphSequence = 0;
        private static double kiSpiralPhase = 0.0D;
        private static float kiMoteSpawnBudget = 0.0F;
        private static float kiSpiralSecondaryBudget = 0.0F;
        private static int kiMoteSequence = 0;
        private static float kiWispSpawnBudget = 0.0F;
        private static float groundRuneSpawnBudget = 0.0F;
        private static int groundRuneSequence = 0;
        private static float transcendentCoreSpawnBudget = 0.0F;
        private static float absorptionSpawnBudget = 0.0F;
        private static int absorptionSequence = 0;
        private static int phenomenonTicks = 0;
        private static int phenomenonType = -1;
        private static int nextPhenomenonTick = 0;
        private static int breakthroughPresentationTicks = 0;
        private static String breakthroughStat = "";
        private static int breakthroughAmount = 0;

        private static int stageTransitionTicks = 0;
        private static int stageTransitionStage = 0;

        private static MeditationNetwork.MeditationSummaryPacket summary;
        private static int summaryTicksRemaining = 0;
        private static int summaryTotalTicks = 0;
        private static final Set<Integer> npcMeditatorsLastTick = new HashSet<>();

        private ClientForgeEvents() {}

        @SubscribeEvent
        public static void onClientTick(
            TickEvent.ClientTickEvent event
        ) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft mc =
                Minecraft.getInstance();

            if (mc.player == null
                || mc.level == null) {

                resetClientState();
                return;
            }

            /*
             * PAUSE-SAFE TIMER FIX:
             *
             * In an integrated singleplayer world the server stops ticking
             * while Minecraft is paused. The old client smoothing continued
             * incrementing meditationTicks, so the HUD timer drifted ahead.
             *
             * Freeze ALL meditation presentation clocks while paused. The
             * next authoritative server packet re-locks the HUD after resume.
             */
            if (mc.isPaused()) {
                return;
            }

            boolean meditateKeyDown = MEDITATE_KEY.isDown();
            // Drain Forge's queued click count, but use a real rising edge as the authority.
            // Explicit start/stop commands are also server-idempotent: even if a duplicate packet
            // ever arrives, holding M cannot repeatedly leave/re-enter the seated state.
            while (MEDITATE_KEY.consumeClick()) { /* drained intentionally */ }
            if (meditateKeyDown && !meditateKeyWasDown) {
                boolean currentlyMeditating = serverMeditating || isLocallyMeditating(mc);
                mc.player.connection.sendCommand(currentlyMeditating ? "meditate stop" : "meditate start");
            }
            meditateKeyWasDown = meditateKeyDown;

            /*
             * Keep DragonMine Z's own base.meditation animation running.
             */
            DMZAnimationBridge.tick(mc);
            tickNpcMeditationVisuals(mc);

            boolean meditating =
                serverMeditating
                    || isLocallyMeditating(mc);

            float visualTarget =
                meditating
                    ? serverStage
                    : 0.0F;

            visualStage +=
                (visualTarget - visualStage)
                    * (meditating ? 0.065F : 0.12F);

            visualPresence +=
                ((meditating ? 1.0F : 0.0F) - visualPresence)
                    * (meditating ? 0.11F : 0.085F);

            if (Math.abs(visualTarget - visualStage) < 0.001F) {
                visualStage = visualTarget;
            }

            if (meditating) {
                float normalizedStage =
                    smoothUnit(Mth.clamp(visualStage, 0.0F, 4.0F) / 4.0F);
                double spiralCycle = 54.0D - normalizedStage * 12.0D;
                kiSpiralPhase = positiveModulo(
                    kiSpiralPhase + 1.0D / spiralCycle,
                    1.0D
                );
                tickMeditationPhenomena(mc);
                tickStageTransitionWorldFx(mc);
                tickBreakthroughWorldFx(mc);
                tickMeditationGlyphs(mc);
                tickKiMotes(mc);
                tickAbsorptionStreams(mc);
                tickGroundRunes(mc);
                tickSharedMeditationMotes(mc);
                tickTranscendentKiCore(mc);
                tickTranscendentBreathPulse(mc);
            } else {
                glyphSpawnBudget = 0.0F;
                kiSpiralPhase = 0.0D;
                kiMoteSpawnBudget = 0.0F;
                kiSpiralSecondaryBudget = 0.0F;
                kiWispSpawnBudget = 0.0F;
                groundRuneSpawnBudget = 0.0F;
                groundRuneSequence = 0;
                transcendentCoreSpawnBudget = 0.0F;
                absorptionSpawnBudget = 0.0F;
                absorptionSequence = 0;
                phenomenonTicks = 0;
                phenomenonType = -1;
                nextPhenomenonTick = 0;
                breakthroughPresentationTicks = 0;
                breakthroughStat = "";
                breakthroughAmount = 0;
            }

            if (meditating) {
                /*
                 * Server sync arrives once per second and after TP rewards.
                 * Increment locally between packets so the clock remains smooth.
                 */
                meditationTicks++;

                boolean breaksFocus =
                    mc.options.keyUp.isDown()
                        || mc.options.keyDown.isDown()
                        || mc.options.keyLeft.isDown()
                        || mc.options.keyRight.isDown()
                        || mc.options.keyJump.isDown()
                        || mc.options.keyShift.isDown()
                        || mc.options.keyAttack.isDown()
                        || mc.options.keyUse.isDown();

                if (breaksFocus && !sentCancel) {

                    sentCancel = true;

                    mc.player.connection.sendCommand(
                        "meditate interrupt"
                    );
                } else if (!breaksFocus) {
                    sentCancel = false;
                }
            } else {
                sentCancel = false;
            }

            float hudTarget =
                meditating
                    ? 1.0F
                    : 0.0F;

            hudPresence +=
                (hudTarget - hudPresence)
                    * 0.18F;

            if (stageTransitionTicks > 0) {
                stageTransitionTicks--;
            }

            if (breakthroughPresentationTicks > 0) {
                breakthroughPresentationTicks--;
                if (breakthroughPresentationTicks <= 0) {
                    breakthroughStat = "";
                    breakthroughAmount = 0;
                }
            }

            if (summaryTicksRemaining > 0) {
                summaryTicksRemaining--;

                if (summaryTicksRemaining <= 0) {
                    summary = null;
                }
            }
        }

        private static void resetClientState() {
            meditationTicks = 0;
            sessionTp = 0;
            groupCount = 0;
            sessionStatGains = "";
            serverStage = 0;
            serverMultiplier = 1;
            serverStageProgress = 0.0F;
            serverMeditating = false;
            sentCancel = false;
            meditateKeyWasDown = false;
            stateInitialized = false;
            fastTesting = false;
            debugView = false;
            energyPercent = 0.0F;
            staminaPercent = 0.0F;
            activeForm = "";
            formMastery = 0.0D;
            formMasteryMax = 0.0D;
            sessionMasteryGain = 0.0D;
            hudPresence = 0.0F;
            visualStage = 0.0F;
            visualPresence = 0.0F;
            glyphSpawnBudget = 0.0F;
            glyphSequence = 0;
            kiSpiralPhase = 0.0D;
            kiMoteSpawnBudget = 0.0F;
            kiSpiralSecondaryBudget = 0.0F;
            kiMoteSequence = 0;
            kiWispSpawnBudget = 0.0F;
            groundRuneSpawnBudget = 0.0F;
            groundRuneSequence = 0;
            transcendentCoreSpawnBudget = 0.0F;
            absorptionSpawnBudget = 0.0F;
            absorptionSequence = 0;
            phenomenonTicks = 0;
            phenomenonType = -1;
            nextPhenomenonTick = 0;
            breakthroughPresentationTicks = 0;
            breakthroughStat = "";
            breakthroughAmount = 0;
            stageTransitionTicks = 0;
            stageTransitionStage = 0;
            summary = null;
            summaryTicksRemaining = 0;
            summaryTotalTicks = 0;
            npcMeditatorsLastTick.clear();
            DMZAnimationBridge.clearClientState();
            DMZClientAuraColor.clearClientState();
        }

        /** Client-owned NPC Meditation FX so every player can independently disable them. */
        private static void tickNpcMeditationVisuals(Minecraft mc) {
            if (!MeditationConfig.CLIENT.npcMeditationEffects.get()) {
                npcMeditatorsLastTick.clear();
                return;
            }
            Set<Integer> current = new HashSet<>();
            java.util.List<AmbientFighterEntity> fighters = mc.level.getEntitiesOfClass(
                    AmbientFighterEntity.class, mc.player.getBoundingBox().inflate(48.0D),
                    fighter -> fighter.isAlive() && fighter.isMeditating());
            boolean playerMeditating = serverMeditating || isLocallyMeditating(mc);
            double sharedRadius = MeditationConfig.SERVER.groupMeditationRadius.get();

            for (AmbientFighterEntity fighter : fighters) {
                current.add(fighter.getId());
                int color = fighter.getAuraColor();
                float red = ((color >> 16) & 0xFF) / 255.0F;
                float green = ((color >> 8) & 0xFF) / 255.0F;
                float blue = (color & 0xFF) / 255.0F;
                if (color == 0) { red = green = blue = 1.0F; }

                if (!npcMeditatorsLastTick.contains(fighter.getId())) {
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI * 0.25D;
                        mc.level.addParticle(DBZMeditation.KI_BURST.get(),
                                fighter.getX() + Math.cos(angle) * 0.55D,
                                fighter.getY() + 0.45D + (i & 1) * 0.18D,
                                fighter.getZ() + Math.sin(angle) * 0.55D, red, green, blue);
                    }
                }

                if ((mc.level.getGameTime() + fighter.getId()) % 4L == 0L) {
                    double phase = (mc.level.getGameTime() * 0.055D + fighter.getId() * 0.71D);
                    double radius = 0.48D * Math.max(0.82D, fighter.getDisplayScale());
                    for (int mote = 0; mote < 2; mote++) {
                        double a = phase + mote * Math.PI;
                        mc.level.addParticle(DBZMeditation.KI_MOTE.get(),
                                fighter.getX() + Math.cos(a) * radius,
                                fighter.getY() + 0.35D + (0.35D + 0.15D * Math.sin(a * 0.7D)) * fighter.getBbHeight(),
                                fighter.getZ() + Math.sin(a) * radius, red, green, blue);
                    }
                }

                // A restrained ground ring makes NPC meditation readable without copying the
                // player's full Focus Seal progression. GroundRuneParticle itself is horizontal.
                if ((mc.level.getGameTime() + fighter.getId()) % 9L == 0L) {
                    double angle = (mc.level.getGameTime() * 0.035D + fighter.getId()) % (Math.PI * 2.0D);
                    double radius = 0.62D * Math.max(0.82D, fighter.getDisplayScale());
                    mc.level.addParticle(DBZMeditation.GROUND_RUNE.get(),
                            fighter.getX() + Math.cos(angle) * radius, fighter.getY() + 0.075D,
                            fighter.getZ() + Math.sin(angle) * radius, red, green, blue);
                }

                // Shared player/NPC meditation is reciprocal. AbsorptionKiParticle's three data
                // channels carry a target delta; the particle resolves its color from the body it leaves.
                // Launch one stream each way so both meditators visibly contribute their own Ki.
                if (playerMeditating && fighter.isSharedMeditatingWithPlayer(mc.player.getUUID())
                        && fighter.distanceToSqr(mc.player) <= sharedRadius * sharedRadius) {
                    int tier = Math.max(0, Math.min(3, fighter.getMeditationBondTier()));
                    int interval = switch (tier) { case 3 -> 5; case 2 -> 7; case 1 -> 9; default -> 12; };
                    if ((mc.level.getGameTime() + fighter.getId()) % interval == 0L) {
                        Vec3 npcAnchor = fighter.position().add(0.0D, fighter.getBbHeight() * 0.55D, 0.0D);
                        Vec3 playerAnchor = mc.player.position().add(0.0D, mc.player.getBbHeight() * 0.52D, 0.0D);
                        Vec3 npcToPlayer = playerAnchor.subtract(npcAnchor);
                        Vec3 playerToNpc = npcAnchor.subtract(playerAnchor);
                        mc.level.addParticle(DBZMeditation.KI_ABSORB.get(), npcAnchor.x, npcAnchor.y, npcAnchor.z,
                                npcToPlayer.x, npcToPlayer.y, npcToPlayer.z);
                        mc.level.addParticle(DBZMeditation.KI_ABSORB.get(), playerAnchor.x, playerAnchor.y, playerAnchor.z,
                                playerToNpc.x, playerToNpc.y, playerToNpc.z);
                        // Keep the NPC's own aura color especially readable at the stream origin.
                        // The travelling fragment now inherits the color of whichever body emitted it.
                        mc.level.addParticle(DBZMeditation.KI_MOTE.get(), npcAnchor.x, npcAnchor.y, npcAnchor.z, red, green, blue);
                        if (tier >= 3 && (mc.level.getGameTime() + fighter.getId()) % 20L == 0L) {
                            Vec3 mid = npcAnchor.lerp(playerAnchor, 0.5D);
                            mc.level.addParticle(DBZMeditation.KI_BURST.get(), mid.x, mid.y, mid.z, red, green, blue);
                        }
                    }
                }
            }

            // NPC-only meditation circles use the same reciprocal absorption visual language as
            // player/NPC meditation. Circle membership is synced by Living World so nearby solo
            // meditators are never accidentally connected. Spawn each pair once (lower entity id).
            long groupTime = mc.level.getGameTime();
            for (int i = 0; i < fighters.size(); i++) {
                AmbientFighterEntity a = fighters.get(i);
                if (!a.isMeditationCircleMember()) continue;
                for (int j = i + 1; j < fighters.size(); j++) {
                    AmbientFighterEntity b = fighters.get(j);
                    if (!b.isMeditationCircleMember() || a.distanceToSqr(b) > 11.0D * 11.0D) continue;
                    int interval = 4;
                    if ((groupTime + a.getId() + b.getId()) % interval != 0L) continue;
                    Vec3 aAnchor = a.position().add(0.0D, a.getBbHeight() * 0.55D, 0.0D);
                    Vec3 bAnchor = b.position().add(0.0D, b.getBbHeight() * 0.55D, 0.0D);
                    Vec3 aToB = bAnchor.subtract(aAnchor);
                    Vec3 bToA = aAnchor.subtract(bAnchor);
                    mc.level.addParticle(DBZMeditation.KI_ABSORB.get(), aAnchor.x, aAnchor.y, aAnchor.z,
                            aToB.x, aToB.y, aToB.z);
                    mc.level.addParticle(DBZMeditation.KI_ABSORB.get(), bAnchor.x, bAnchor.y, bAnchor.z,
                            bToA.x, bToA.y, bToA.z);

                    // A visible travelling mote guarantees there is something readable BETWEEN the
                    // NPCs even on clients where the absorb fragment itself is especially subtle.
                    double progress = Math.floorMod(groupTime / interval + a.getId() * 3L + b.getId(), 24L) / 23.0D;
                    Vec3 thread = aAnchor.lerp(bAnchor, progress)
                            .add(0.0D, Math.sin(Math.PI * progress) * 0.12D, 0.0D);
                    int colorA = a.getAuraColor();
                    int colorB = b.getAuraColor();
                    float redA = colorA == 0 ? 1.0F : ((colorA >> 16) & 0xFF) / 255.0F;
                    float greenA = colorA == 0 ? 1.0F : ((colorA >> 8) & 0xFF) / 255.0F;
                    float blueA = colorA == 0 ? 1.0F : (colorA & 0xFF) / 255.0F;
                    float redB = colorB == 0 ? 1.0F : ((colorB >> 16) & 0xFF) / 255.0F;
                    float greenB = colorB == 0 ? 1.0F : ((colorB >> 8) & 0xFF) / 255.0F;
                    float blueB = colorB == 0 ? 1.0F : (colorB & 0xFF) / 255.0F;
                    float red = (redA + redB) * 0.5F;
                    float green = (greenA + greenB) * 0.5F;
                    float blue = (blueA + blueB) * 0.5F;
                    mc.level.addParticle(DBZMeditation.KI_MOTE.get(), thread.x, thread.y, thread.z, red, green, blue);

                    long linkPhase = groupTime + a.getId() + b.getId();
                    if (linkPhase % 24L == 0L) {
                        Vec3 mid = aAnchor.lerp(bAnchor, 0.5D);
                        mc.level.addParticle(DBZMeditation.KI_BURST.get(), mid.x, mid.y, mid.z, red, green, blue);
                    }
                    if (linkPhase % 40L == 0L) {
                        mc.level.addParticle(DBZMeditation.MEDITATION_GLYPH.get(),
                                aAnchor.x, aAnchor.y, aAnchor.z, aToB.x, aToB.y, aToB.z);
                    }
                }
            }
            npcMeditatorsLastTick.clear();
            npcMeditatorsLastTick.addAll(current);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderAuraUnderlay(
            RenderGuiEvent.Pre event
        ) {
            // Living World 1.9 keeps Meditation's original presentation: no extra
            // screen-edge/body aura layer. The old config key remains readable for
            // compatibility with existing client TOMLs, but it intentionally renders nothing.
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderOverlay(
            RenderGuiEvent.Post event
        ) {
            Minecraft mc =
                Minecraft.getInstance();

            if (mc.player == null) {
                return;
            }

            GuiGraphics graphics =
                event.getGuiGraphics();

            int width =
                mc.getWindow()
                    .getGuiScaledWidth();

            int height =
                mc.getWindow()
                    .getGuiScaledHeight();

            if (!mc.options.hideGui
                && MeditationConfig.CLIENT
                    .focusHud
                    .get()
                && hudPresence > 0.01F) {

                renderUnifiedFocusHud(
                    mc,
                    graphics,
                    width
                );
            }

            if (!mc.options.hideGui
                && MeditationConfig.CLIENT.stageTransitionEffects.get()
                && stageTransitionTicks > 0) {

                renderStageTransition(
                    mc,
                    graphics,
                    width,
                    height
                );
            }

            if (!mc.options.hideGui
                && breakthroughPresentationTicks > 0
                && !breakthroughStat.isEmpty()) {

                renderBreakthroughPresentation(
                    mc,
                    graphics,
                    width,
                    height
                );
            }

            if (!mc.options.hideGui
                && debugView) {

                renderDebugOverlay(
                    mc,
                    graphics,
                    width,
                    height
                );
            }

            if (!mc.options.hideGui
                && MeditationConfig.CLIENT
                    .sessionSummary
                    .get()
                && summary != null
                && summaryTicksRemaining > 0) {

                renderSessionSummary(
                    mc,
                    graphics,
                    width,
                    height
                );
            }
        }

        private static void renderFirstPersonAura(
            Minecraft mc,
            GuiGraphics graphics,
            int width,
            int height
        ) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            float reveal = Mth.clamp((stage + 0.18F) / 1.15F, 0.0F, 1.0F);
            reveal *= visualPresence;
            float intensity =
                MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;
            float pulse =
                0.90F
                    + 0.10F * breathInhale(stage);
            int auraRgb = auraUiRgb(mc);
            float red = ((auraRgb >> 16) & 0xFF) / 255.0F;
            float green = ((auraRgb >> 8) & 0xFF) / 255.0F;
            float blue = (auraRgb & 0xFF) / 255.0F;
            float opacity = Mth.clamp(
                (0.065F + stage * 0.018F)
                    * reveal
                    * pulse
                    * intensity,
                0.0F,
                0.18F
            );
            if (opacity <= 0.003F) {
                return;
            }

            int baseSize = Mth.clamp(height / 3, 48, 96);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                for (int i = 0; i < 3; i++) {
                    float phase =
                        meditationTicks * 0.050F + i * 2.13F;
                    int size = Math.round(
                        baseSize
                            * (0.78F
                                + i * 0.10F
                                + Mth.sin(phase) * 0.045F)
                    );
                    int bob = Math.round(Mth.sin(phase * 0.73F) * 5.0F);
                    int y = height - size + bob + i * 5;
                    float layerAlpha = opacity * (1.0F - i * 0.16F);

                    drawTintedSprite(
                        graphics,
                        ANIME_KI_WISP_TEXTURE,
                        -size / 3 + i * 2,
                        y,
                        size,
                        red,
                        green,
                        blue,
                        layerAlpha
                    );
                    drawTintedSprite(
                        graphics,
                        ANIME_KI_WISP_TEXTURE,
                        width - size * 2 / 3 - i * 2,
                        y,
                        size,
                        red,
                        green,
                        blue,
                        layerAlpha
                    );
                }

                for (int i = 0; i < 3; i++) {
                    float phase =
                        meditationTicks * 0.041F + i * 2.39F;
                    int size = Math.round(
                        baseSize
                            * (0.42F + Mth.sin(phase) * 0.035F)
                    );
                    int x =
                        Math.round(width * (0.18F + i * 0.32F))
                            - size / 2;
                    int y =
                        height - size / 2
                            + Math.round(Mth.sin(phase * 0.81F) * 3.0F);
                    drawTintedSprite(
                        graphics,
                        ANIME_KI_MOTE_TEXTURE,
                        x,
                        y,
                        size,
                        red,
                        green,
                        blue,
                        opacity * 0.44F
                    );
                }
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
            }
        }

        private static void drawTintedSprite(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int size,
            float red,
            float green,
            float blue,
            float alpha
        ) {
            RenderSystem.setShaderColor(
                Mth.clamp(red, 0.0F, 1.0F),
                Mth.clamp(green, 0.0F, 1.0F),
                Mth.clamp(blue, 0.0F, 1.0F),
                Mth.clamp(alpha, 0.0F, 1.0F)
            );
            graphics.blit(
                texture,
                x,
                y,
                0.0F,
                0.0F,
                size,
                size,
                64,
                64
            );
        }

        /**
         * One compact source of truth:
         *
         * DEEP x4 • 6:42 • +187 TP
         * Focus 42% -> Transcendent
         * [================------]
         */
        private static void renderUnifiedFocusHud(
            Minecraft mc,
            GuiGraphics graphics,
            int width
        ) {
            String stageName = stageName(serverStage);
            String title =
                stageName
                    + "  •  x" + serverMultiplier + " TP"
                    + "  •  " + formatTime(meditationTicks)
                    + "  •  +" + sessionTp + " TP";

            String subtitle;
            if (serverStage >= 4) {
                subtitle = "Maximum Focus";
            } else {
                subtitle =
                    "Focus "
                        + Math.round(serverStageProgress * 100.0F)
                        + "% → "
                        + stageName(serverStage + 1);
            }

            String formLine = "";
            if (!activeForm.isEmpty()) {
                formLine =
                    activeForm
                        + "  •  Form mastery "
                        + formatMastery(formMastery)
                        + "/"
                        + formatMastery(formMasteryMax);

                if (sessionMasteryGain > 0.000001D) {
                    formLine += "  •  +" + formatMastery(sessionMasteryGain) + " this session";
                }
            }

            String statLine =
                sessionStatGains.isEmpty()
                    ? ""
                    : "Breakthrough  " + sessionStatGains;

            String[] detailLines =
                new String[] {
                    formLine,
                    statLine
                };

            int alpha = Mth.clamp(Math.round(255.0F * hudPresence), 0, 255);
            int auraRgb = auraUiRgb(mc);
            int white = (alpha << 24) | 0x00FFFFFF;
            int aura = (alpha << 24) | auraRgb;
            int gold = (alpha << 24) | 0x00FFD36A;

            int barWidth = Math.min(190, Math.max(120, width / 4));
            int barHeight = 5;
            int x = (width - barWidth) / 2;

            int top =
                MeditationConfig.CLIENT.hudTopOffset.get()
                    - Math.round((1.0F - hudPresence) * 8.0F);

            int visibleDetails = 0;
            int contentWidth = Math.max(mc.font.width(title), mc.font.width(subtitle));
            for (String line : detailLines) {
                if (!line.isEmpty()) {
                    visibleDetails++;
                    contentWidth = Math.max(contentWidth, mc.font.width(line));
                }
            }

            int panelWidth = Math.max(barWidth + 12, contentWidth + 12);
            int panelHeight = 31 + visibleDetails * 11;
            int panelX = (width - panelWidth) / 2;
            int panelAlpha = Math.round(120.0F * hudPresence);

            graphics.fill(
                panelX,
                top,
                panelX + panelWidth,
                top + panelHeight,
                panelAlpha << 24
            );

            graphics.drawCenteredString(mc.font, title, width / 2, top + 4, white);
            graphics.drawCenteredString(mc.font, subtitle, width / 2, top + 15, aura);

            int lineY = top + 25;
            if (!formLine.isEmpty()) {
                graphics.drawCenteredString(
                    mc.font,
                    formLine,
                    width / 2,
                    lineY,
                    aura
                );
                lineY += 11;
            }
            if (!statLine.isEmpty()) {
                graphics.drawCenteredString(
                    mc.font,
                    statLine,
                    width / 2,
                    lineY,
                    gold
                );
                lineY += 11;
            }

            int barY = lineY;
            graphics.fill(
                x,
                barY,
                x + barWidth,
                barY + barHeight,
                Math.round(90.0F * hudPresence) << 24
            );

            int fill =
                serverStage >= 4
                    ? barWidth
                    : Math.round(barWidth * serverStageProgress);

            graphics.fill(
                x,
                barY,
                x + fill,
                barY + barHeight,
                (Math.round(220.0F * hudPresence) << 24) | auraRgb
            );
        }

        private static String formatMastery(double value) {
            if (!Double.isFinite(value)) {
                return "0";
            }
            double rounded = Math.rint(value);
            if (Math.abs(value - rounded) < 0.0005D) {
                return Long.toString(Math.round(rounded));
            }
            return String.format(java.util.Locale.ROOT, "%.3f", value);
        }

        private static void renderStageTransition(
            Minecraft mc,
            GuiGraphics graphics,
            int width,
            int height
        ) {
            float life =
                stageTransitionTicks / 50.0F;

            float opacity =
                life > 0.65F
                    ? (1.0F - life) / 0.35F
                    : life / 0.65F;

            opacity =
                Math.max(
                    0.0F,
                    Math.min(1.0F, opacity)
                );

            int alpha =
                Math.round(
                    255.0F * opacity
                );

            int auraRgb = auraUiRgb(mc);

            String name =
                switch (stageTransitionStage) {
                    case 4 -> "TRANSCENDENT FOCUS";
                    case 3 -> "DEEP MEDITATION";
                    case 2 -> "CENTERED";
                    case 1 -> "FOCUSED";
                    default -> "";
                };

            if (!name.isEmpty()
                && alpha > 0) {

                int y =
                    Math.min(
                        height - 24,
                        MeditationConfig.CLIENT
                            .hudTopOffset
                            .get()
                            + 42
                    );

                float red = ((auraRgb >> 16) & 0xFF) / 255.0F;
                float green = ((auraRgb >> 8) & 0xFF) / 255.0F;
                float blue = (auraRgb & 0xFF) / 255.0F;
                int glowSize = 112 + Math.round(22.0F * opacity);
                int flareSize = 72 + Math.round(20.0F * opacity);
                int titleHalfWidth = mc.font.width(name) / 2;

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                try {
                    drawTintedSprite(
                        graphics,
                        KI_GLOW_TEXTURE,
                        width / 2 - glowSize / 2,
                        y + 4 - glowSize / 2,
                        glowSize,
                        red,
                        green,
                        blue,
                        opacity * 0.12F
                    );
                    drawTintedSprite(
                        graphics,
                        ANIME_GROUND_FLARE_TEXTURE,
                        width / 2 - flareSize / 2,
                        y + 4 - flareSize / 2,
                        flareSize,
                        red,
                        green,
                        blue,
                        opacity * 0.25F
                    );
                    int moteSize = 25 + Math.round(opacity * 6.0F);
                    drawTintedSprite(
                        graphics,
                        ANIME_KI_MOTE_TEXTURE,
                        width / 2 - titleHalfWidth - moteSize - 8,
                        y - moteSize / 2 + 4,
                        moteSize,
                        red,
                        green,
                        blue,
                        opacity * 0.32F
                    );
                    drawTintedSprite(
                        graphics,
                        ANIME_KI_MOTE_TEXTURE,
                        width / 2 + titleHalfWidth + 8,
                        y - moteSize / 2 + 4,
                        moteSize,
                        red,
                        green,
                        blue,
                        opacity * 0.32F
                    );
                } finally {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                }

                graphics.drawCenteredString(
                    mc.font,
                    name,
                    width / 2,
                    y,
                    (alpha << 24)
                        | auraRgb
                );
            }
        }

        private static void renderBreakthroughPresentation(
            Minecraft mc,
            GuiGraphics graphics,
            int width,
            int height
        ) {
            float elapsed = 92.0F - breakthroughPresentationTicks;
            float fadeIn = smoothUnit(elapsed / 12.0F);
            float fadeOut = smoothUnit(breakthroughPresentationTicks / 24.0F);
            float opacity = Mth.clamp(fadeIn * fadeOut, 0.0F, 1.0F);
            if (opacity <= 0.001F) {
                return;
            }

            int auraRgb = auraUiRgb(mc);
            float red = ((auraRgb >> 16) & 0xFF) / 255.0F;
            float green = ((auraRgb >> 8) & 0xFF) / 255.0F;
            float blue = (auraRgb & 0xFF) / 255.0F;
            int y = Math.max(38, height / 2 - 58);
            int glowSize = 154 + Math.round(18.0F * opacity);
            int flareSize = 88 + Math.round(12.0F * opacity);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                drawTintedSprite(
                    graphics,
                    KI_GLOW_TEXTURE,
                    width / 2 - glowSize / 2,
                    y - glowSize / 2 + 5,
                    glowSize,
                    red,
                    green,
                    blue,
                    opacity * 0.16F
                );
                drawTintedSprite(
                    graphics,
                    ANIME_GROUND_FLARE_TEXTURE,
                    width / 2 - flareSize / 2,
                    y - flareSize / 2 + 4,
                    flareSize,
                    red,
                    green,
                    blue,
                    opacity * 0.34F
                );
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
            }

            int alpha = Math.round(255.0F * opacity);
            graphics.drawCenteredString(
                mc.font,
                "MEDITATIVE BREAKTHROUGH",
                width / 2,
                y - 6,
                (alpha << 24) | 0x00FFD978
            );
            graphics.drawCenteredString(
                mc.font,
                "+" + Math.max(1, breakthroughAmount) + " " + statDisplayName(breakthroughStat),
                width / 2,
                y + 7,
                (alpha << 24) | auraRgb
            );
        }

        private static void renderDebugOverlay(
            Minecraft mc,
            GuiGraphics graphics,
            int width,
            int height
        ) {
            int auraRgb = auraUiRgb(mc);

            String[] lines =
                new String[] {
                    "Meditation Debug",
                    "paused=" + mc.isPaused() + "  fast=" + fastTesting,
                    "ticks=" + meditationTicks + "  stage=" + stageName(serverStage),
                    "stageProgress="
                        + Math.round(serverStageProgress * 100.0F)
                        + "%",
                    "sessionTP="
                        + sessionTp
                        + "  shared="
                        + Math.max(0, groupCount - 1),
                    "resources=Ki "
                        + Math.round(Mth.clamp(energyPercent, 0.0F, 1.0F) * 100.0F)
                        + "% / STM "
                        + Math.round(Mth.clamp(staminaPercent, 0.0F, 1.0F) * 100.0F)
                        + "%",
                    "form="
                        + (activeForm.isEmpty() ? "none" : activeForm)
                        + "  mastery="
                        + formatMastery(formMastery)
                        + "/"
                        + formatMastery(formMasteryMax)
                        + "  session+="
                        + formatMastery(sessionMasteryGain),
                    String.format(java.util.Locale.ROOT, "ki=#%06X", auraRgb),
                    "kiSource=" + DMZClientAuraColor.getLastSource()
                        + "  formOutline="
                        + (DMZKiColorBridge.hasActiveFormOutline(mc.player)
                            ? "native"
                            : "none"),
                    "outlineSource="
                        + (serverStage >= 3
                            ? "dmz:transformation-mask"
                            : "inactive"),
                    "breath="
                        + Math.round(breathInhale(visualStage) * 100.0F)
                        + "%  quiet="
                        + Math.round(ambientQuietFactor() * 100.0F)
                        + "%",
                    "phenomenon="
                        + phenomenonType
                        + "/"
                        + phenomenonTicks
                        + "  breakthrough="
                        + (breakthroughStat.isEmpty()
                            ? "none"
                            : breakthroughStat),
                    "sessionStats="
                        + (sessionStatGains.isEmpty()
                            ? "none"
                            : sessionStatGains)
                };

            int x = 8;
            int y = 42;
            int lineHeight = 10;

            int maxWidth = 0;

            for (String line : lines) {
                maxWidth =
                    Math.max(
                        maxWidth,
                        mc.font.width(line)
                    );
            }

            graphics.fill(
                x - 4,
                y - 4,
                x + maxWidth + 5,
                y + lines.length * lineHeight + 3,
                0x99000000
            );

            for (int i = 0; i < lines.length; i++) {
                int color =
                    i == 0
                        ? 0xFFFFFFFF
                        : (0xFF000000 | auraRgb);

                graphics.drawString(
                    mc.font,
                    lines[i],
                    x,
                    y + i * lineHeight,
                    color,
                    true
                );
            }
        }

        private static void renderSessionSummary(
            Minecraft mc,
            GuiGraphics graphics,
            int width,
            int height
        ) {
            if (summary == null || summaryTotalTicks <= 0) {
                return;
            }

            int elapsed = summaryTotalTicks - summaryTicksRemaining;
            float fadeIn = Math.min(1.0F, elapsed / 12.0F);
            float fadeOut = Math.min(1.0F, summaryTicksRemaining / 24.0F);
            float opacity = Math.min(fadeIn, fadeOut);
            int alpha = Mth.clamp(Math.round(255.0F * opacity), 0, 255);

            if (alpha <= 0) {
                return;
            }

            String header;
            if (summary.interrupted()) {
                header = "MEDITATION INTERRUPTED";
            } else if ("Transcendent".equals(summary.stageName())) {
                header = "TRANSCENDENT MEDITATION";
            } else if ("Deep".equals(summary.stageName())) {
                header = "DEEP FOCUS COMPLETE";
            } else {
                header = "MEDITATION COMPLETE";
            }

            String stageLine =
                formatTime(summary.durationTicks())
                    + "  •  "
                    + summary.stageName();

            String rewardLine = "+" + summary.totalTp() + " TP";

            String masteryLine = "";
            if (summary.masteryGain() > 0.000001D) {
                masteryLine =
                    (summary.masteryForm().isEmpty()
                        ? "Form mastery"
                        : summary.masteryForm())
                        + "  •  +"
                        + formatMastery(summary.masteryGain())
                        + " form mastery";
            }

            String statLine =
                summary.statGains().isEmpty()
                    ? ""
                    : "Breakthrough  " + summary.statGains();

            String recordLine =
                summary.newRecord()
                    ? "NEW LONGEST SESSION"
                    : "";

            String[] lines =
                new String[] {
                    stageLine,
                    rewardLine,
                    masteryLine,
                    statLine,
                    recordLine
                };

            int contentWidth = mc.font.width(header);
            int visibleLines = 0;
            for (String line : lines) {
                if (!line.isEmpty()) {
                    contentWidth = Math.max(contentWidth, mc.font.width(line));
                    visibleLines++;
                }
            }

            int panelWidth =
                Math.min(
                    300,
                    Math.max(215, contentWidth + 34)
                );
            int panelHeight = 18 + visibleLines * 14;
            int x = (width - panelWidth) / 2;
            int y =
                Math.max(
                    36,
                    height / 2 - panelHeight / 2
                );

            int panelAlpha = Math.round(190.0F * opacity);
            int borderAlpha = Math.round(95.0F * opacity);
            int auraRgb = auraUiRgb(mc);
            int white = (alpha << 24) | 0x00FFFFFF;
            int aura = (alpha << 24) | auraRgb;
            int gold = (alpha << 24) | 0x00FFD36A;
            int interruptedColor = (alpha << 24) | 0x00FF9D9D;

            graphics.fill(
                x,
                y,
                x + panelWidth,
                y + panelHeight,
                panelAlpha << 24
            );
            graphics.fill(
                x,
                y,
                x + panelWidth,
                y + 1,
                (borderAlpha << 24) | auraRgb
            );
            graphics.fill(
                x,
                y + panelHeight - 1,
                x + panelWidth,
                y + panelHeight,
                (borderAlpha << 24) | auraRgb
            );

            graphics.drawCenteredString(
                mc.font,
                header,
                width / 2,
                y + 7,
                summary.interrupted()
                    ? interruptedColor
                    : aura
            );

            int lineY = y + 22;
            for (String line : lines) {
                if (line.isEmpty()) {
                    continue;
                }

                int color = white;
                if (line == rewardLine || line == masteryLine) {
                    color = aura;
                } else if (line == statLine || line == recordLine) {
                    color = gold;
                }

                graphics.drawCenteredString(
                    mc.font,
                    line,
                    width / 2,
                    lineY,
                    color
                );
                lineY += 14;
            }
        }

        static boolean isMeditatingForRenderer() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null
                && mc.level != null
                && (serverMeditating
                    || isLocallyMeditating(mc)
                    || visualPresence > 0.025F);
        }

        static int rendererStage() {
            return serverStage;
        }

        static float rendererStageProgress() {
            return serverStageProgress;
        }

        static float rendererVisualStage() {
            return visualStage;
        }

        static float rendererVisualPresence() {
            return visualPresence;
        }

        static float rendererBreath() {
            return breathInhale(visualStage);
        }

        /**
         * 4.0 presentation rhythm. The particles are still Codex's custom
         * anime-KI family; this layer only choreographs when and how they move.
         */
        private static int breathCycleTicks(float stage) {
            if (stage >= 3.55F) {
                return 168;
            }
            if (stage >= 2.55F) {
                return 132;
            }
            if (stage >= 1.55F) {
                return 116;
            }
            return 100;
        }

        private static float breathInhale(float stage) {
            int cycle = breathCycleTicks(stage);
            float phase =
                (float)positiveModulo(meditationTicks, cycle)
                    / (float)cycle;
            return 0.5F - 0.5F * Mth.cos(phase * Mth.TWO_PI);
        }

        private static float breathExhale(float stage) {
            return 1.0F - breathInhale(stage);
        }

        private static float ambientQuietFactor() {
            float factor = 1.0F;

            // Stage changes begin with a small visual inhale instead of stacking
            // ordinary ambient particles under the transition choreography.
            if (stageTransitionTicks > 34) {
                factor *= 0.48F;
            }

            // A breakthrough is a rare focal event. Let it own the composition.
            if (breakthroughPresentationTicks > 42) {
                factor *= 0.18F;
            }

            // "Perfect stillness" phenomenon: near-empty frame, then one pulse.
            if (phenomenonType == 2 && phenomenonTicks > 28) {
                factor *= 0.16F;
            }

            return Mth.clamp(factor, 0.12F, 1.0F);
        }

                private static void tickAbsorptionStreams(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            if (stage < 1.42F) {
                absorptionSpawnBudget = 0.0F;
                return;
            }

            float rate;
            if (stage < 2.0F) {
                rate = Mth.lerp((stage - 1.42F) / 0.58F, 0.25F, 1.15F);
            } else if (stage < 3.0F) {
                rate = Mth.lerp(stage - 2.0F, 1.15F, 4.70F);
            } else if (stage < 4.0F) {
                rate = Mth.lerp(stage - 3.0F, 4.70F, 3.35F);
            } else {
                rate = 3.35F;
            }

            float inhale = breathInhale(stage);
            float intensity =
                MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;
            rate *=
                intensity
                    * ambientQuietFactor()
                    * (0.60F + inhale * 0.92F);

            absorptionSpawnBudget += rate / 20.0F;
            while (absorptionSpawnBudget >= 1.0F) {
                absorptionSpawnBudget -= 1.0F;
                spawnAbsorptionMote(mc, stage, false);
            }

            // Deep gets one unmistakable intake on each breathing crest. This
            // is intentionally several moving fragments, not a static ring.
            if (stage >= 2.80F && stage < 3.72F) {
                int cycle = breathCycleTicks(stage);
                int pulse = meditationTicks % cycle;
                if (pulse == cycle / 2) {
                    spawnConvergenceWave(mc, 5, 0.96D);
                }
            }
        }

        private static void spawnAbsorptionMote(
            Minecraft mc,
            float stage,
            boolean wide
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            int sequence = absorptionSequence++;
            double normalized = smoothUnit(stage / 4.0F);
            int arms =
                stage >= 3.70F
                    ? 2
                    : (stage >= 2.65F ? 3 : 1);
            int arm = Math.floorMod(sequence, arms);
            double angle =
                meditationYawRadians(mc)
                    + meditationTicks * (stage >= 3.70F ? 0.020D : 0.031D)
                    + Mth.TWO_PI * arm / arms;
            double radius =
                (wide ? 1.12D : 0.78D + 0.18D * normalized)
                    * anchor.horizontalScale();
            double heightPattern =
                Math.sin(
                    meditationTicks * 0.075D
                        + arm * Mth.TWO_PI / arms
                        + (sequence / Math.max(1, arms)) * 0.24D
                );
            double startY =
                anchor.torso().y
                    + heightPattern
                        * (0.34D + 0.10D * normalized)
                        * anchor.verticalScale();

            Vec3 start = new Vec3(
                anchor.torso().x + Math.cos(angle) * radius,
                startY,
                anchor.torso().z + Math.sin(angle) * radius
            );
            Vec3 target =
                anchor.torso().add(
                    0.0D,
                    (0.015D + 0.025D * breathInhale(stage))
                        * anchor.verticalScale(),
                    0.0D
                );
            Vec3 delta = target.subtract(start);

            mc.level.addParticle(
                DBZMeditation.KI_ABSORB.get(),
                start.x,
                start.y,
                start.z,
                delta.x,
                delta.y,
                delta.z
            );
        }

        private static void spawnConvergenceWave(
            Minecraft mc,
            int count,
            double radius
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            double yaw = meditationYawRadians(mc);
            Vec3 target =
                anchor.torso().add(0.0D, 0.025D * anchor.verticalScale(), 0.0D);

            for (int i = 0; i < count; i++) {
                double angle = yaw + Mth.TWO_PI * i / count;
                double yOffset =
                    (-0.26D + 0.52D * (i / (double)Math.max(1, count - 1)))
                        * anchor.verticalScale();
                Vec3 start = new Vec3(
                    anchor.torso().x
                        + Math.cos(angle) * radius * anchor.horizontalScale(),
                    anchor.torso().y + yOffset,
                    anchor.torso().z
                        + Math.sin(angle) * radius * anchor.horizontalScale()
                );
                Vec3 delta = target.subtract(start);
                mc.level.addParticle(
                    DBZMeditation.KI_ABSORB.get(),
                    start.x,
                    start.y,
                    start.z,
                    delta.x,
                    delta.y,
                    delta.z
                );
            }
        }

        private static void tickStageTransitionWorldFx(Minecraft mc) {
            if (stageTransitionTicks <= 0) {
                return;
            }

            int stage = stageTransitionStage;

            if (stage == 1 && stageTransitionTicks == 42) {
                spawnAuraBurst(mc, 1);
            } else if (stage == 2 && stageTransitionTicks == 42) {
                spawnConvergenceWave(mc, 4, 0.82D);
            } else if (stage == 3) {
                if (stageTransitionTicks == 46) {
                    spawnConvergenceWave(mc, 8, 1.04D);
                } else if (stageTransitionTicks == 29) {
                    spawnAuraBurst(mc, 2);
                    spawnMoteRing(mc, 0.58D, 12, 0.02D);
                }
            } else if (stage == 4) {
                if (stageTransitionTicks == 48) {
                    spawnConvergenceWave(mc, 10, 1.16D);
                } else if (stageTransitionTicks == 31) {
                    spawnTranscendentCore(mc);
                    spawnTranscendentCore(mc);
                } else if (stageTransitionTicks == 20) {
                    spawnAuraBurst(mc, 2);
                    spawnRearCoreHalo(mc, 12, 0.48D);
                }
            }
        }

        private static void tickBreakthroughWorldFx(Minecraft mc) {
            if (breakthroughPresentationTicks <= 0
                || mc.player == null
                || mc.level == null) {

                return;
            }

            /*
             * A breakthrough is not just a louder meditation pulse.
             *
             * 1) ambient ki snaps inward,
             * 2) a dedicated realization sigil opens behind the torso,
             * 3) a sharp custom core/star ignites,
             * 4) custom shards release outward,
             * 5) the body answers with a brief rising ki column,
             * 6) one final core flash seals the permanent +1.
             */
            if (breakthroughPresentationTicks == 89) {
                spawnConvergenceWave(mc, 12, 1.28D);
            } else if (breakthroughPresentationTicks == 80) {
                spawnBreakthroughSigil(mc, 0.20D);
            } else if (breakthroughPresentationTicks == 70) {
                spawnBreakthroughCore(mc);
                spawnBreakthroughShards(mc, 16);

                mc.level.playLocalSound(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.45F,
                    1.38F,
                    false
                );
            } else if (breakthroughPresentationTicks == 59) {
                spawnBreakthroughSigil(mc, 0.10D);
                spawnMoteRing(mc, 0.66D, 12, 0.04D);
            } else if (breakthroughPresentationTicks == 50) {
                MeditationVisualAnchor.Anchor anchor =
                    MeditationVisualAnchor.resolve(
                        mc.player,
                        1.0F
                    );

                float[] rgb =
                    DMZClientAuraColor.getRgb(
                        mc.player,
                        1.0F
                    );

                for (int i = 0; i < 9; i++) {
                    Vec3 p =
                        anchor.torso().add(
                            0.0D,
                            (-0.34D + i * 0.095D)
                                * anchor.verticalScale(),
                            0.0D
                        );

                    mc.level.addParticle(
                        DBZMeditation.KI_WISP.get(),
                        p.x,
                        p.y,
                        p.z,
                        rgb[0],
                        rgb[1],
                        rgb[2]
                    );
                }
            } else if (breakthroughPresentationTicks == 40) {
                spawnBreakthroughCore(mc);
                spawnBreakthroughShards(mc, 8);

                mc.level.playLocalSound(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.55F,
                    1.70F,
                    false
                );
            }
        }

        private static void spawnBreakthroughCore(Minecraft mc) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(
                    mc.player,
                    1.0F
                );

            float[] rgb =
                DMZClientAuraColor.getRgb(
                    mc.player,
                    1.0F
                );

            Vec3 center =
                anchor.torso().add(
                    0.0D,
                    0.035D * anchor.verticalScale(),
                    0.0D
                );

            mc.level.addParticle(
                DBZMeditation.BREAKTHROUGH_CORE.get(),
                center.x,
                center.y,
                center.z,
                rgb[0],
                rgb[1],
                rgb[2]
            );
        }

        private static void spawnBreakthroughSigil(
            Minecraft mc,
            double rearOffset
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(
                    mc.player,
                    1.0F
                );

            float[] rgb =
                DMZClientAuraColor.getRgb(
                    mc.player,
                    1.0F
                );

            double yaw =
                meditationYawRadians(mc);

            Vec3 forward =
                new Vec3(
                    -Math.sin(yaw),
                    0.0D,
                    Math.cos(yaw)
                );

            Vec3 center =
                anchor.torso()
                    .add(
                        forward.scale(
                            -rearOffset
                                * anchor.horizontalScale()
                        )
                    )
                    .add(
                        0.0D,
                        0.04D * anchor.verticalScale(),
                        0.0D
                    );

            mc.level.addParticle(
                DBZMeditation.BREAKTHROUGH_SIGIL.get(),
                center.x,
                center.y,
                center.z,
                rgb[0],
                rgb[1],
                rgb[2]
            );
        }

        private static void spawnBreakthroughShards(
            Minecraft mc,
            int count
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(
                    mc.player,
                    1.0F
                );

            float[] rgb =
                DMZClientAuraColor.getRgb(
                    mc.player,
                    1.0F
                );

            for (int i = 0; i < count; i++) {
                double angle =
                    Mth.TWO_PI
                        * i
                        / Math.max(1, count);

                double radius =
                    0.05D
                        + (i % 3) * 0.018D;

                Vec3 p =
                    anchor.torso().add(
                        Math.cos(angle)
                            * radius
                            * anchor.horizontalScale(),
                        ((i % 5) - 2)
                            * 0.025D
                            * anchor.verticalScale(),
                        Math.sin(angle)
                            * radius
                            * anchor.horizontalScale()
                    );

                mc.level.addParticle(
                    DBZMeditation.BREAKTHROUGH_SHARD.get(),
                    p.x,
                    p.y,
                    p.z,
                    rgb[0],
                    rgb[1],
                    rgb[2]
                );
            }
        }

        /**
         * Rare presentation-only events keep a long meditation from becoming a
         * looping screensaver. They never alter stats, TP or stages.
         */
        private static void tickMeditationPhenomena(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            if (stage < 2.90F) {
                phenomenonTicks = 0;
                phenomenonType = -1;
                nextPhenomenonTick = 0;
                return;
            }

            if (phenomenonTicks > 0) {
                tickActivePhenomenon(mc);
                phenomenonTicks--;
                if (phenomenonTicks <= 0) {
                    phenomenonType = -1;
                    nextPhenomenonTick = 0;
                }
                return;
            }

            if (nextPhenomenonTick <= 0) {
                int delay = fastTesting
                    ? 260 + mc.level.random.nextInt(181)
                    : 1400 + mc.level.random.nextInt(1601);
                nextPhenomenonTick = meditationTicks + delay;
                return;
            }

            if (meditationTicks >= nextPhenomenonTick) {
                phenomenonType = mc.level.random.nextInt(3);
                phenomenonTicks = 64;
            }
        }

        private static void tickActivePhenomenon(Minecraft mc) {
            switch (phenomenonType) {
                case 0 -> {
                    // Slow spiritual-sigil procession around the upper body.
                    if (phenomenonTicks % 8 == 0) {
                        spawnPhenomenonGlyph(mc, phenomenonTicks / 8);
                    }
                }
                case 1 -> {
                    // A rare, denser drawing-in of ambient ki.
                    if (phenomenonTicks > 24 && phenomenonTicks % 5 == 0) {
                        spawnConvergenceWave(mc, 2, 1.18D);
                    }
                    if (phenomenonTicks == 24) {
                        spawnAuraBurst(mc, 2);
                    }
                }
                case 2 -> {
                    // Deliberate near-stillness followed by one clean pressure pulse.
                    if (phenomenonTicks == 27) {
                        spawnAuraBurst(mc, 1);
                        spawnMoteRing(mc, 0.88D, 18, 0.02D);
                        if (visualStage >= 3.65F) {
                            spawnRearCoreHalo(mc, 14, 0.56D);
                        }
                    }
                }
                default -> {
                }
            }
        }

        private static void spawnPhenomenonGlyph(Minecraft mc, int index) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            double yaw = meditationYawRadians(mc);
            double angle = yaw + index * 0.86D;
            double radius = 0.92D * anchor.horizontalScale();
            Vec3 start = new Vec3(
                anchor.torso().x + Math.cos(angle) * radius,
                anchor.torso().y
                    + (0.44D - (index % 3) * 0.22D)
                        * anchor.verticalScale(),
                anchor.torso().z + Math.sin(angle) * radius
            );
            Vec3 end =
                anchor.torso().add(
                    -Math.cos(angle) * 0.36D * anchor.horizontalScale(),
                    0.08D * anchor.verticalScale(),
                    -Math.sin(angle) * 0.36D * anchor.horizontalScale()
                );
            Vec3 delta = end.subtract(start);
            mc.level.addParticle(
                DBZMeditation.MEDITATION_GLYPH.get(),
                start.x,
                start.y,
                start.z,
                delta.x,
                delta.y,
                delta.z
            );
        }

        private static void tickTranscendentBreathPulse(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            if (stage < 3.72F) {
                return;
            }

            int cycle = breathCycleTicks(stage);
            int tick = meditationTicks % cycle;

            // Once per slow Transcendent breath: ki is drawn into the body,
            // then the rear core answers with one controlled outward halo.
            if (tick == cycle / 2) {
                spawnConvergenceWave(mc, 6, 1.06D);
            } else if (tick == cycle / 2 + 8) {
                spawnAuraBurst(mc, 1);
                spawnRearCoreHalo(mc, 12, 0.50D);
            }
        }

        private static void spawnAuraBurst(Minecraft mc, int count) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            for (int i = 0; i < count; i++) {
                double angle = i * Mth.TWO_PI / Math.max(1, count);
                Vec3 p = anchor.torso().add(
                    Math.cos(angle) * 0.045D * anchor.horizontalScale(),
                    0.015D * i * anchor.verticalScale(),
                    Math.sin(angle) * 0.045D * anchor.horizontalScale()
                );
                mc.level.addParticle(
                    DBZMeditation.KI_BURST.get(),
                    p.x,
                    p.y,
                    p.z,
                    rgb[0],
                    rgb[1],
                    rgb[2]
                );
            }
        }

        private static void spawnMoteRing(
            Minecraft mc,
            double radius,
            int count,
            double yOffset
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            double phase = meditationYawRadians(mc);
            for (int i = 0; i < count; i++) {
                double angle = phase + Mth.TWO_PI * i / count;
                Vec3 p = anchor.torso().add(
                    Math.cos(angle) * radius * anchor.horizontalScale(),
                    yOffset * anchor.verticalScale(),
                    Math.sin(angle) * radius * anchor.horizontalScale()
                );
                spawnKiMoteAt(mc, p, rgb);
            }
        }

        private static void spawnRearCoreHalo(
            Minecraft mc,
            int count,
            double radius
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            double yaw = meditationYawRadians(mc);
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
            Vec3 center = transcendentCenter(anchor, forward);

            for (int i = 0; i < count; i++) {
                double angle = Mth.TWO_PI * i / count;
                Vec3 p = center
                    .add(right.scale(Math.cos(angle) * radius * anchor.horizontalScale()))
                    .add(0.0D, Math.sin(angle) * radius * anchor.verticalScale(), 0.0D);
                spawnKiMoteAt(mc, p, rgb);
            }
        }

        private static String findNewStatGain(
            String previous,
            String current
        ) {
            if (current == null || current.isEmpty() || current.equals(previous)) {
                return "";
            }

            String[] stats = {"STR", "SKP", "RES", "VIT", "PWR", "ENE"};
            for (String stat : stats) {
                if (statAmount(current, stat) > statAmount(previous, stat)) {
                    return stat;
                }
            }
            return "";
        }

        private static int statAmount(String summary, String stat) {
            if (summary == null || summary.isEmpty()) {
                return 0;
            }

            String[] entries = summary.split("\\s*•\\s*");
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (!trimmed.endsWith(" " + stat)) {
                    continue;
                }
                int space = trimmed.indexOf(' ');
                if (space <= 1 || trimmed.charAt(0) != '+') {
                    continue;
                }
                try {
                    return Integer.parseInt(trimmed.substring(1, space));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        }

        private static String statDisplayName(String stat) {
            return switch (stat) {
                case "STR" -> "Strength";
                case "SKP" -> "Strike Power";
                case "RES" -> "Resistance";
                case "VIT" -> "Vitality";
                case "PWR" -> "Ki Power";
                case "ENE" -> "Energy";
                default -> stat;
            };
        }

        private static void tickMeditationGlyphs(Minecraft mc) {
            float ratePerSecond = glyphRateForVisualStage(visualStage);

            // The opening retains the symbol-gathering identity from 3.9, but
            // the symbols then settle into the stage-specific choreography.
            if (meditationTicks < 60) {
                ratePerSecond +=
                    0.78F
                        * (1.0F - meditationTicks / 60.0F);
            }

            ratePerSecond *=
                ambientQuietFactor()
                    * MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;

            glyphSpawnBudget += ratePerSecond / 20.0F;

            while (glyphSpawnBudget >= 1.0F) {
                glyphSpawnBudget -= 1.0F;
                spawnMeditationGlyph(mc);
            }
        }

        private static float glyphRateForVisualStage(float stage) {
            float clamped = Mth.clamp(stage, 0.0F, 4.0F);
            int lower = Mth.floor(clamped);
            int upper = Math.min(4, lower + 1);
            float blend = clamped - lower;

            float[] rates = {
                0.30F,
                0.58F,
                0.82F,
                0.60F,
                0.38F
            };

            return Mth.lerp(blend, rates[lower], rates[upper]);
        }

        private static void spawnMeditationGlyph(Minecraft mc) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);

            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            double yaw = meditationYawRadians(mc);
            Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));

            int direction = (glyphSequence++ & 1) == 0 ? 1 : -1;
            double width =
                (0.54D + stage * 0.10D)
                    * anchor.horizontalScale();
            double height =
                (0.34D + stage * 0.075D)
                    * anchor.verticalScale();

            Vec3 start =
                anchor.torso()
                    .add(right.scale(-direction * width))
                    .add(forward.scale(0.16D * direction))
                    .add(0.0D, -height, 0.0D);

            boolean converge =
                stage >= 1.45F
                    && glyphSequence % 3 == 0;

            Vec3 end =
                converge
                    ? anchor.torso().add(0.0D, 0.04D, 0.0D)
                    : anchor.torso()
                        .add(right.scale(direction * width * 0.82D))
                        .add(forward.scale(-0.10D * direction))
                        .add(0.0D, height * 0.82D, 0.0D);

            Vec3 delta = end.subtract(start);
            mc.level.addParticle(
                DBZMeditation.MEDITATION_GLYPH.get(),
                start.x,
                start.y,
                start.z,
                delta.x,
                delta.y,
                delta.z
            );
        }

        private static void tickKiMotes(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            float[] rates = {2.4F, 4.2F, 6.4F, 8.2F, 6.2F};
            int lower = Mth.floor(stage);
            int upper = Math.min(4, lower + 1);
            float rate = Mth.lerp(stage - lower, rates[lower], rates[upper]);
            float intensity =
                MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;
            float breath = breathInhale(stage);
            rate *=
                intensity
                    * ambientQuietFactor()
                    * (0.88F + 0.16F * breath);

            kiMoteSpawnBudget += rate / 20.0F;
            while (kiMoteSpawnBudget >= 1.0F) {
                kiMoteSpawnBudget -= 1.0F;
                spawnRisingKiMote(mc, stage, 0);
            }

            /*
             * The second arm waits until Centered is established. Deep is the
             * richest helix; Transcendent deliberately settles back down.
             */
            float secondArm =
                smoothUnit((stage - 1.55F) / 1.15F);
            kiSpiralSecondaryBudget +=
                rate * 0.48F * secondArm / 20.0F;
            while (kiSpiralSecondaryBudget >= 1.0F) {
                kiSpiralSecondaryBudget -= 1.0F;
                spawnRisingKiMote(mc, stage, 1);
            }
        }

        private static void spawnRisingKiMote(
            Minecraft mc,
            float stage,
            int arm
        ) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            int sequence = kiMoteSequence++;
            double yaw = meditationYawRadians(mc);
            double normalizedStage = smoothUnit(stage / 4.0F);
            double clock =
                mc.level.getGameTime()
                    + (sequence & 3) * 0.19D;
            double cycle = 54.0D - normalizedStage * 12.0D;
            double progress = positiveModulo(
                kiSpiralPhase
                    + (sequence & 3) * 0.19D / cycle
                    + arm * 0.47D,
                1.0D
            );
            double detachment =
                sequence % 9 == 0
                    ? (((sequence / 9) & 1) == 0 ? 1.0D : -1.0D)
                        * (0.060D + 0.025D * normalizedStage)
                    : 0.0D;

            Vec3 position = risingSpiralPosition(
                anchor,
                yaw,
                clock,
                stage,
                arm,
                progress,
                detachment
            );
            spawnKiMoteAt(mc, position, rgb);
        }

        private static Vec3 risingSpiralPosition(
            MeditationVisualAnchor.Anchor anchor,
            double yaw,
            double clock,
            float stage,
            int arm,
            double progress,
            double detachment
        ) {
            double normalizedStage = smoothUnit(stage / 4.0F);
            double noise =
                0.65D * Math.sin((clock + arm * 29.0D) * 0.61D)
                    + 0.35D
                        * Math.sin((clock + arm * 47.0D) * 0.173D);
            double turns = 1.15D + 0.70D * normalizedStage;
            double angle =
                yaw
                    + progress * turns * Math.PI * 2.0D
                    + arm * Math.PI
                    + 0.075D * noise;
            double height =
                (-0.64D
                    + 1.40D * progress
                    + 0.022D
                        * Math.sin((clock + arm * 31.0D) * 0.43D))
                    * anchor.verticalScale();
            double radius =
                ((0.44D + 0.10D * normalizedStage)
                    * (1.0D - 0.17D * progress)
                    + 0.035D * Math.sin(Math.PI * progress)
                    + 0.022D
                        * Math.sin(
                            (clock + arm * 17.0D) * 0.37D + 1.7D
                        )
                    + detachment);

            // A tiny whole-helix inhale keeps the animation alive without
            // turning the meditation into a constant power-up pulse.
            radius *=
                1.0D
                    - 0.045D
                        * breathInhale(stage)
                        * smoothUnit((stage - 0.70F) / 1.10F);

            double transcendent =
                smoothUnit((stage - 3.10F) / 0.90F);
            double topFocus =
                smoothUnit((float)((progress - 0.78D) / 0.22D));
            radius *= 1.0D - 0.42D * transcendent * topFocus;
            radius *= anchor.horizontalScale();

            Vec3 position = anchor.torso().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius
            );

            /*
             * At Transcendent, the top of each rising arm bends behind the
             * body and meets the upper edge of the rear ki bloom.
             */
            double feed = transcendent * topFocus;
            if (feed > 0.0D) {
                Vec3 forward =
                    new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
                position = position
                    .add(
                        forward.scale(
                            -0.48D * feed * anchor.horizontalScale()
                        )
                    )
                    .add(
                        0.0D,
                        -0.30D * feed * anchor.verticalScale(),
                        0.0D
                    );
            }

            return position;
        }

        private static void tickAuraWisps(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            float[] rates = {0.0F, 0.42F, 1.12F, 2.55F, 1.72F};
            int lower = Mth.floor(stage);
            int upper = Math.min(4, lower + 1);
            float rate = Mth.lerp(stage - lower, rates[lower], rates[upper]);
            rate *=
                MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;
            rate *=
                ambientQuietFactor()
                    * (0.58F + 0.72F * breathExhale(stage));

            kiWispSpawnBudget += rate / 20.0F;
            while (kiWispSpawnBudget >= 1.0F) {
                kiWispSpawnBudget -= 1.0F;
                spawnAuraWisp(mc, stage);
            }
        }

        private static void spawnAuraWisp(Minecraft mc, float stage) {
            MeditationVisualAnchor.Anchor anchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            int sequence = kiMoteSequence++;
            double clock =
                mc.level.getGameTime()
                    + (sequence % 5) * 0.13D;
            double progress = positiveModulo(
                clock * 0.017D
                    + sequence * 0.2718281828459045D,
                0.58D
            );
            float secondArm =
                smoothUnit((stage - 1.55F) / 1.15F);
            float selector = ((sequence * 37) % 100) / 100.0F;
            int arm = selector < secondArm ? 1 : 0;
            double detachment =
                0.028D * Math.sin(sequence * 1.731D);
            Vec3 position = risingSpiralPosition(
                anchor,
                meditationYawRadians(mc),
                clock,
                stage,
                arm,
                progress,
                detachment
            );

            mc.level.addParticle(
                DBZMeditation.KI_WISP.get(),
                position.x,
                position.y,
                position.z,
                rgb[0],
                rgb[1],
                rgb[2]
            );
        }

        /**
         * Anime-style Focus Seal beneath the meditator. It deliberately becomes more
         * structured with depth instead of simply spawning more aura around the body:
         * Focused = one ring, Centered = double ring, Deep = ring + four-point seal,
         * Transcendent = triple breathing seal with a bright inner pulse.
         */
        private static void tickGroundRunes(Minecraft mc) {
            if (!MeditationConfig.CLIENT.groundFocusCircle.get()
                    || !MeditationConfig.CLIENT.focusSealEnabled.get()) {
                groundRuneSpawnBudget = 0.0F;
                groundRuneSequence = 0;
                return;
            }

            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            if (stage < 0.72F) {
                groundRuneSpawnBudget = 0.0F;
                return;
            }

            float depth = Math.max(1.0F, stage);
            float rate = (1.15F + depth * 0.68F)
                    * MeditationConfig.CLIENT.focusSealIntensityPercent.get() / 100.0F
                    * MeditationConfig.CLIENT.auraIntensityPercent.get() / 100.0F
                    * ambientQuietFactor();
            groundRuneSpawnBudget += rate / 20.0F;

            while (groundRuneSpawnBudget >= 1.0F) {
                groundRuneSpawnBudget -= 1.0F;
                spawnFocusSealStep(mc, stage);
            }
        }

        private static void spawnFocusSealStep(Minecraft mc, float stage) {
            MeditationVisualAnchor.Anchor anchor = MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            int sequence = groundRuneSequence++;
            double clock = mc.level.getGameTime();
            double size = MeditationConfig.CLIENT.focusSealRadiusPercent.get() / 100.0D;
            double breath = 1.0D - 0.045D * breathInhale(stage);

            // Outer ring — present from the first meaningful meditation stage.
            int outerPoints = stage >= 3.0F ? 20 : stage >= 2.0F ? 18 : 14;
            double outerAngle = clock * 0.008D + (sequence % outerPoints) * Math.PI * 2.0D / outerPoints;
            double outerRadius = (0.60D + stage * 0.028D) * size * breath * anchor.horizontalScale();
            spawnGroundRuneAt(mc, anchor, outerAngle, outerRadius, rgb);
            spawnGroundRuneAt(mc, anchor, outerAngle + Math.PI, outerRadius, rgb);

            // Centered: a counter-rotating inner ring makes the seal read as intentional,
            // not just a generic particle circle.
            if (stage >= 1.75F) {
                int innerPoints = 12;
                double innerAngle = -clock * 0.011D + (sequence % innerPoints) * Math.PI * 2.0D / innerPoints;
                double innerRadius = outerRadius * (stage >= 3.75F ? 0.50D : 0.62D);
                spawnGroundRuneAt(mc, anchor, innerAngle, innerRadius, rgb);
            }

            // Deep: four stable cardinal/diagonal anchors pulse like an anime ki seal.
            if (stage >= 2.75F && (sequence & 1) == 0) {
                double rotation = clock * 0.004D;
                double spokeRadius = outerRadius * 0.82D;
                int spoke = (sequence / 2) & 3;
                double angle = rotation + spoke * Math.PI * 0.5D;
                spawnGroundRuneAt(mc, anchor, angle, spokeRadius, rgb);
                spawnGroundRuneAt(mc, anchor, angle + Math.PI, spokeRadius, rgb);
            }

            // Transcendent: a restrained inner pulse instead of flooding the whole body.
            if (stage >= 3.75F && sequence % 3 == 0) {
                double pulseAngle = clock * 0.018D + sequence * 0.61D;
                spawnGroundRuneAt(mc, anchor, pulseAngle, outerRadius * 0.24D, rgb);
                spawnGroundRuneAt(mc, anchor, pulseAngle + Math.PI, outerRadius * 0.24D, rgb);
            }
        }

        private static void spawnGroundRuneAt(
            Minecraft mc,
            MeditationVisualAnchor.Anchor anchor,
            double angle,
            double radius,
            float[] rgb
        ) {
            double x = anchor.torso().x + Math.cos(angle) * radius;
            double z = anchor.torso().z + Math.sin(angle) * radius;
            double fallbackY = mc.player.getY() - 0.30D * anchor.verticalScale();
            double y = findGroundSurfaceY(mc, x, z, fallbackY);

            mc.level.addParticle(
                DBZMeditation.GROUND_RUNE.get(),
                x, y, z,
                rgb[0], rgb[1], rgb[2]
            );
        }

        private static double findGroundSurfaceY(
            Minecraft mc,
            double x,
            double z,
            double fallbackY
        ) {
            BlockPos.MutableBlockPos position =
                new BlockPos.MutableBlockPos();
            CollisionContext context = CollisionContext.of(mc.player);
            int top = Mth.floor(mc.player.getY());
            int bottom = Math.max(mc.level.getMinBuildHeight(), top - 6);

            for (int y = top; y >= bottom; y--) {
                position.set(Mth.floor(x), y, Mth.floor(z));
                VoxelShape shape =
                    mc.level
                        .getBlockState(position)
                        .getCollisionShape(mc.level, position, context);
                if (!shape.isEmpty()) {
                    return y + shape.max(Direction.Axis.Y) + 0.075D;
                }
            }

            return fallbackY;
        }

        private static void tickSharedMeditationMotes(Minecraft mc) {
            if (groupCount <= 1
                || !MeditationConfig.SERVER.groupMeditation.get()
                || meditationTicks % 4 != 0) {
                return;
            }

            double radius =
                MeditationConfig.SERVER.groupMeditationRadius.get();
            MeditationVisualAnchor.Anchor localAnchor =
                MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] localRgb =
                DMZClientAuraColor.getRgb(mc.player, 1.0F);

            // External partners (such as a Living World fighter) are not client-side
            // players, so give the local meditator a restrained synchronized pulse
            // as well as any direct player-to-player links below. Visual only.
            if (meditationTicks % 40 == 0) {
                spawnConvergenceWave(mc, Math.min(4, Math.max(2, groupCount)), 0.95D);
            }
            long step = mc.level.getGameTime() / 4L;
            int linkIndex = 0;

            for (var other : mc.level.players()) {
                if (other == mc.player
                    || !DBZMeditation.isMeditationSeat(other.getVehicle())
                    || other.distanceToSqr(mc.player) > radius * radius) {
                    continue;
                }

                MeditationVisualAnchor.Anchor otherAnchor =
                    MeditationVisualAnchor.resolve(other, 1.0F);
                float[] otherRgb =
                    DMZClientAuraColor.getRgb(other, 1.0F);
                float progress =
                    ((step + linkIndex * 5L) % 20L) / 19.0F;
                Vec3 start =
                    localAnchor.torso().add(0.0D, -0.34D, 0.0D);
                Vec3 end =
                    otherAnchor.torso().add(0.0D, -0.34D, 0.0D);
                Vec3 position = start
                    .add(end.subtract(start).scale(progress))
                    .add(
                        0.0D,
                        Math.sin(Math.PI * progress) * 0.13D,
                        0.0D
                    );
                float[] mixedRgb = {
                    (localRgb[0] + otherRgb[0]) * 0.5F,
                    (localRgb[1] + otherRgb[1]) * 0.5F,
                    (localRgb[2] + otherRgb[2]) * 0.5F
                };
                spawnKiMoteAt(mc, position, mixedRgb);

                /*
                 * Shared meditation already has the travelling ki-thread. Add one
                 * restrained synchronized anime pulse per link roughly every
                 * three seconds so group meditation feels intentionally shared
                 * without increasing ambient particle density.
                 */
                if ((meditationTicks
                    + linkIndex * 13) % 60 == 0) {

                    Vec3 midpoint =
                        start.lerp(end, 0.5D)
                            .add(
                                0.0D,
                                0.10D,
                                0.0D
                            );

                    mc.level.addParticle(
                        DBZMeditation.KI_BURST.get(),
                        midpoint.x,
                        midpoint.y,
                        midpoint.z,
                        mixedRgb[0],
                        mixedRgb[1],
                        mixedRgb[2]
                    );

                    mc.level.addParticle(
                        DBZMeditation.MEDITATION_GLYPH.get(),
                        start.x,
                        start.y,
                        start.z,
                        end.x - start.x,
                        end.y - start.y,
                        end.z - start.z
                    );
                }

                linkIndex++;
            }
        }

        private static void tickTranscendentKiCore(Minecraft mc) {
            float stage = Mth.clamp(visualStage, 0.0F, 4.0F);
            float reveal =
                smoothUnit((stage - 3.34F) / 0.66F);
            if (reveal <= 0.001F) {
                transcendentCoreSpawnBudget = 0.0F;
                return;
            }

            float intensity =
                MeditationConfig.CLIENT.auraIntensityPercent.get()
                    / 100.0F;
            float inhale = breathInhale(stage);
            float rate =
                (1.95F + 1.25F * inhale)
                    * reveal
                    * intensity
                    * ambientQuietFactor();

            transcendentCoreSpawnBudget += rate / 20.0F;
            while (transcendentCoreSpawnBudget >= 1.0F) {
                transcendentCoreSpawnBudget -= 1.0F;
                spawnTranscendentCore(mc);
            }
        }

        private static Vec3 transcendentCenter(
            MeditationVisualAnchor.Anchor anchor,
            Vec3 forward
        ) {
            return anchor.torso()
                .add(forward.scale(-0.66D * anchor.horizontalScale()))
                .add(0.0D, 0.10D * anchor.verticalScale(), 0.0D);
        }

        private static void spawnKiMoteAt(
            Minecraft mc,
            Vec3 position,
            float[] rgb
        ) {
            mc.level.addParticle(
                DBZMeditation.KI_MOTE.get(),
                position.x,
                position.y,
                position.z,
                Mth.clamp(rgb[0], 0.0F, 1.0F),
                Mth.clamp(rgb[1], 0.0F, 1.0F),
                Mth.clamp(rgb[2], 0.0F, 1.0F)
            );
        }

        private static void spawnTranscendentCore(Minecraft mc) {
            MeditationVisualAnchor.Anchor anchor = MeditationVisualAnchor.resolve(mc.player, 1.0F);
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            double yaw = meditationYawRadians(mc);
            Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
            Vec3 right = new Vec3(forward.z, 0.0D, -forward.x).normalize();
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 center = transcendentCenter(anchor, forward);
            double breath = 0.40D * anchor.horizontalScale()
                    * (0.92D + 0.08D * Math.sin(mc.level.getGameTime() * 0.10D));

            // Never render a large camera-facing filled sprite here. A sparse 3-D mote halo
            // preserves the deepest-stage pulse from above, side and below without turning into
            // a solid circle when viewed edge-on.
            for (int i = 0; i < 6; i++) {
                double angle = i * (Math.PI * 2.0D / 6.0D) + mc.level.getGameTime() * 0.035D;
                Vec3 p = center.add(right.scale(Math.cos(angle) * breath))
                        .add(up.scale(Math.sin(angle) * breath));
                spawnKiMoteAt(mc, p, rgb);
            }
            for (int i = 0; i < 3; i++) {
                double angle = i * (Math.PI * 2.0D / 3.0D) - mc.level.getGameTime() * 0.028D;
                Vec3 p = center.add(forward.scale(Math.cos(angle) * breath * 0.72D))
                        .add(up.scale(Math.sin(angle) * breath * 0.72D));
                spawnKiMoteAt(mc, p, rgb);
            }
        }

        private static float smoothUnit(float value) {
            float t = Mth.clamp(value, 0.0F, 1.0F);
            return t * t * (3.0F - 2.0F * t);
        }

        private static double meditationYawRadians(Minecraft mc) {
            Entity vehicle = mc.player.getVehicle();
            float yaw =
                DBZMeditation.isMeditationSeat(vehicle)
                    ? vehicle.getYRot()
                    : mc.player.getYRot();
            return Math.toRadians(yaw);
        }

        private static double positiveModulo(
            double value,
            double divisor
        ) {
            double result = value % divisor;
            return result < 0.0D ? result + divisor : result;
        }

        private static int auraUiRgb(Minecraft mc) {
            return DMZClientAuraColor.getReadableRgbInt(
                mc.player,
                mc.getFrameTime()
            );
        }

        @SubscribeEvent
        public static void onRenderHand(
            RenderHandEvent event
        ) {
            Minecraft mc =
                Minecraft.getInstance();

            if (mc.player != null
                && (serverMeditating
                    || isLocallyMeditating(mc))) {

                event.setCanceled(true);
            }
        }

        private static boolean isLocallyMeditating(
            Minecraft mc
        ) {
            if (mc.player == null) {
                return false;
            }

            Entity vehicle =
                mc.player.getVehicle();

            return DBZMeditation
                .isMeditationSeat(vehicle);
        }

        private static String stageName(
            int stage
        ) {
            return switch (stage) {
                case 4 -> "Transcendent";
                case 3 -> "Deep";
                case 2 -> "Centered";
                case 1 -> "Focused";
                default -> "Calm";
            };
        }

        private static String formatTime(
            int ticks
        ) {
            int seconds =
                ticks / 20;

            return (seconds / 60)
                + ":"
                + String.format(
                    "%02d",
                    seconds % 60
                );
        }
    }
}
