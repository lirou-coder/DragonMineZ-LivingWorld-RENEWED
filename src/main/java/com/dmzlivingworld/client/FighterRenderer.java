package com.dmzlivingworld.client;

import com.dmzlivingworld.config.LivingWorldClientConfig;

import com.dmzlivingworld.client.layer.FighterAppearanceLayer;
import com.dmzlivingworld.client.layer.FighterHairLayer;
import com.dmzlivingworld.client.layer.FighterNativeAccessoryLayer;
import com.dmzlivingworld.client.layer.HerobrineEyesLayer;
import com.dmzlivingworld.client.layer.FighterIsolatedHeldItemLayer;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.WorldMenaceManager;
import com.dragonminez.client.init.entities.renderer.sagas.DBSagasRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.layer.DMZSagaArmorLayer;
import com.dragonminez.client.render.util.IrisCompat;
import com.dragonminez.client.render.util.PlayerEffectQueue;
import com.dragonminez.client.systems.kisense.KiSenseScan;
import com.dragonminez.client.systems.kisense.KiSenseState;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.mixin.client.GeoModelAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.lang.reflect.Method;

/**
 * Multi-race Living World fighter renderer.
 *
 * The body/appearance is LW's procedural composition of DMZ assets, while the
 * charging/transforming aura pass is delegated back to DragonMineZ's own
 * DBSagasRenderer implementation. This is important: DBSagasEntity.setKiCharge()
 * only changes entity state; the stock saga renderer is what actually draws DMZ's
 * aura shader, pulse aura and lightning. Our custom renderer previously skipped it.
 */
public final class FighterRenderer extends GeoEntityRenderer<AmbientFighterEntity> {
    private static final ResourceLocation FISHING_HOOK_TEXTURE = new ResourceLocation("minecraft", "textures/entity/fishing_hook.png");
    private static final RenderType FISHING_HOOK_RENDER = RenderType.entityCutoutNoCull(FISHING_HOOK_TEXTURE);
    private final DBSagasRenderer<AmbientFighterEntity> nativeSagaEffects;
    private final Method drawEffectsInline;
    private final Method drawEffectsDeferred;
    private boolean nativeEffectsUnavailable;

    public FighterRenderer(EntityRendererProvider.Context context) {
        super(context, new FighterModel());
        addRenderLayer(new FighterAppearanceLayer(this));
        addRenderLayer(new FighterHairLayer(this));
        addRenderLayer(new FighterNativeAccessoryLayer(this));
        addRenderLayer(new HerobrineEyesLayer(this));
        // Reuse DMZ 2.1.3's own saga equipment renderers. Living Arsenal stores
        // genuine ItemStacks in the entity slots; these layers are the native visual path.
        addRenderLayer(new DMZSagaArmorLayer<>(this));
        // R36: preserve DMZ's exact right_hand_item placement and normal ItemRenderer path,
        // but isolate mutable GeoItem renderer/model state from same-type weapons owned/rendered by the player.
        addRenderLayer(new FighterIsolatedHeldItemLayer(this));
        shadowRadius = 0.45F;

        // Targeted bridge for the exact DMZ 2.1.3 runtime this addon supports.
        // The methods are private in DMZ, so reflection lets us reuse the exact
        // native saga aura implementation without copying/reinventing its shader.
        DBSagasRenderer<AmbientFighterEntity> effects = null;
        Method inline = null;
        Method deferred = null;
        try {
            effects = new DBSagasRenderer<>(context);
            inline = DBSagasRenderer.class.getDeclaredMethod(
                    "drawEffectsInline", DBSagasEntity.class, PoseStack.class,
                    float.class, boolean.class, boolean.class);
            inline.setAccessible(true);
            deferred = DBSagasRenderer.class.getDeclaredMethod(
                    "drawEffects", DBSagasEntity.class, Matrix4f.class,
                    float.class, boolean.class, boolean.class);
            deferred.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            // If DMZ changes these internals in a future version, rendering the
            // fighter itself must remain safe. This addon targets DMZ 2.1.3.
        }
        nativeSagaEffects = effects;
        drawEffectsInline = inline;
        drawEffectsDeferred = deferred;
    }

    @Override
    public void render(AmbientFighterEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity == null) {
            super.render(null, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // DMZ applies this exact GeckoLib cache boundary before every player render. LW fighters
        // reuse the same DMZ race geometry/animation ecosystem, so they need the same isolation:
        // always force this FighterModel to evaluate the current NPC instead of accepting a baked
        // pose that was last evaluated for another actor (most visibly the local player's arms).
        ((GeoModelAccessor) (Object) getGeoModel()).dmz$setLastRenderedInstance(-1L);

        float scale = entity.getDisplayScale();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        // Stargazing has its own synced pose instead of abusing SWIMMING. Using the vanilla swim
        // pose made the body inherit swimming rotations and could leave the fighter lying sideways.
        if (entity.isStargazingLying() || entity.isNappingPose() || entity.isStrengthTrainingPose()) {
            if (entity.isStrengthTrainingPose()) {
                float rep = Math.floorMod(entity.tickCount, 120) / 120.0F;
                float depth = rep < 0.16F ? 0.0F
                        : rep < 0.39F ? smooth((rep - 0.16F) / 0.23F)
                        : rep < 0.61F ? 1.0F
                        : rep < 0.84F ? 1.0F - smooth((rep - 0.61F) / 0.23F) : 0.0F;
                poseStack.translate(0.0D, 0.15D - depth * 0.095D, 0.30D);
            } else {
                poseStack.translate(0.0D, entity.isNappingPose() ? 0.13D : 0.16D, 0.28D);
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        // The character-panel portrait deliberately reuses this real renderer. Keep the body,
        // appearance and equipment, but do not leak world-space aura/labels/dialogue into GUI.
        if (!FighterPortraitRenderState.isActive()) {
            // DMZ's native saga renderer draws aura whenever a saga NPC is charging or
            // transforming, plus lightning independently. Mirror that exact trigger.
            renderNativeSagaEffects(entity, poseStack, partialTick);
        }
        poseStack.popPose();

        if (!FighterPortraitRenderState.isActive()) {
            renderFishingVisuals(entity, poseStack, bufferSource, packedLight, partialTick);
            renderIdentity(entity, poseStack, bufferSource, packedLight);
            renderSpeech(entity, poseStack, bufferSource, packedLight);
        }
    }

    /**
     * Vanilla-style fishing presentation for NPCs. Vanilla FishingHook itself hard-requires a Player owner,
     * so LW renders the normal bobber texture/string while the server drives the cast/bite timing.
     */
    private void renderFishingVisuals(AmbientFighterEntity entity, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight, float partialTick) {
        if (!entity.isFishingActivity()) return;
        Vec3 bobber = entity.getFishingBobberPosition(partialTick);
        if (bobber.lengthSqr() < 0.01D) return;

        double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yo, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
        Vec3 origin = new Vec3(ex, ey, ez);

        // Anchor the line to the rod side and project it out to the visible tip. Using body yaw
        // rather than view pitch keeps the string attached while the NPC looks around.
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        double yaw = Math.toRadians(bodyYaw);
        Vec3 flat = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);
        double scale = entity.getDisplayScale();
        Vec3 rodTipWorld = origin.add(0.0D, entity.getEyeHeight() - 0.40D, 0.0D)
                .add(right.scale(0.31D * scale))
                .add(flat.scale(0.62D * scale))
                .add(0.0D, 0.08D * scale, 0.0D);
        Vec3 start = rodTipWorld.subtract(origin);
        Vec3 end = bobber.subtract(origin);

        VertexConsumer line = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        Vec3 previous = start;
        final int segments = 16;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float)segments;
            Vec3 current = start.lerp(end, t).add(0.0D, -0.16D * Math.sin(Math.PI * t), 0.0D);
            addLine(line, pose, previous, current);
            previous = current;
        }

        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.50F, 0.50F, 0.50F);
        VertexConsumer quad = buffers.getBuffer(FISHING_HOOK_RENDER);
        PoseStack.Pose q = poseStack.last();
        bobberVertex(quad, q, packedLight, -0.5F, -0.5F, 0.0F, 1.0F);
        bobberVertex(quad, q, packedLight,  0.5F, -0.5F, 1.0F, 1.0F);
        bobberVertex(quad, q, packedLight,  0.5F,  0.5F, 1.0F, 0.0F);
        bobberVertex(quad, q, packedLight, -0.5F,  0.5F, 0.0F, 0.0F);
        poseStack.popPose();
    }

    private static void addLine(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-8D) return;
        Vec3 n = d.normalize();
        consumer.vertex(pose.pose(), (float)a.x, (float)a.y, (float)a.z)
                .color(28, 24, 20, 255).normal(pose.normal(), (float)n.x, (float)n.y, (float)n.z).endVertex();
        consumer.vertex(pose.pose(), (float)b.x, (float)b.y, (float)b.z)
                .color(28, 24, 20, 255).normal(pose.normal(), (float)n.x, (float)n.y, (float)n.z).endVertex();
    }

    private static void bobberVertex(VertexConsumer consumer, PoseStack.Pose pose, int light,
                                     float x, float y, float u, float v) {
        consumer.vertex(pose.pose(), x, y, 0.0F).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    private void renderNativeSagaEffects(AmbientFighterEntity entity, PoseStack poseStack, float partialTick) {
        if (nativeEffectsUnavailable || nativeSagaEffects == null || drawEffectsInline == null) return;

        boolean aura = entity.isTransforming() || entity.isCharge() || entity.isAuraFlared() || entity.isKaiokenAuraPulse();
        boolean lightning = entity.isLightning();
        if (!aura && !lightning) return;

        try {
            if (IrisCompat.isShaderPackInUse() && drawEffectsDeferred != null) {
                Matrix4f capturedMatrix = new Matrix4f(poseStack.last().pose());
                PlayerEffectQueue.addEntityEffect(() -> {
                    try {
                        drawEffectsDeferred.invoke(nativeSagaEffects, entity, capturedMatrix,
                                partialTick, aura, lightning);
                    } catch (ReflectiveOperationException ignored) {
                        nativeEffectsUnavailable = true;
                    }
                });
            } else {
                drawEffectsInline.invoke(nativeSagaEffects, entity, poseStack,
                        partialTick, aura, lightning);
            }
        } catch (ReflectiveOperationException ignored) {
            nativeEffectsUnavailable = true;
        }
    }

    private void renderIdentity(AmbientFighterEntity entity, PoseStack poseStack,
                                MultiBufferSource buffers, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        // Fusion and other native effects may intentionally hide a fighter. Never
        // render LW's custom floating identity independently of the hidden body.
        if (entity.isInvisible() || (minecraft.player != null && entity.isInvisibleTo(minecraft.player))) return;
        if (minecraft.player == null) return;
        int configuredNameDistance = LivingWorldClientConfig.nameplateDistance();
        int nameDistance = WorldMenaceManager.isHerobrine(entity) ? Math.min(18, configuredNameDistance)
                : entity.isWanted() || entity.isFactionLeader()
                ? Math.max(64, configuredNameDistance) : configuredNameDistance;
        double max = (double) nameDistance * nameDistance;
        if (minecraft.player.distanceToSqr(entity) > max) return;

        Font font = minecraft.font;
        String title = entity.isNonCombatant() ? "Resident" : entity.getFactionTitle();
        String top = entity.getFighterName();
        if (!title.isBlank()) top = title + " " + top;
        String legacyTitle = entity.getLegacyTitle();
        if (!legacyTitle.isBlank()) top = legacyTitle + " " + top;
        if (entity.isWanted()) top = "WANTED " + "★".repeat(Math.max(1, entity.getWantedLevel())) + " " + top;
        String story = switch (entity.getStoryRole()) {
            case AmbientFighterEntity.STORY_ALLY -> "ALLY • ";
            case AmbientFighterEntity.STORY_ENEMY -> "ENEMY • ";
            case AmbientFighterEntity.STORY_CAPTIVE -> "CAPTIVE • ";
            case AmbientFighterEntity.STORY_PEACEKEEPER -> "PEACEKEEPER • ";
            default -> "";
        };
        top = story + top;
        Component name = Component.literal(top);
        FighterDispositionClientState.View dispositionView = FighterDispositionClientState.get(entity.getId());
        String indicator = LivingWorldClientConfig.showDispositionIcon() && dispositionView != null
                ? dispositionView.disposition().worldBadge() + " " : "";
        float indicatorWidth = indicator.isBlank() ? 0.0F : font.width(indicator);
        float nameWidth = font.width(name);
        float totalWidth = indicatorWidth + nameWidth;

        poseStack.pushPose();
        boolean kiSenseOverlay = KiSenseState.isCombat() && KiSenseScan.getCombatEntities().contains(entity.getId());
        double labelHeight = entity.getBbHeight() + (kiSenseOverlay ? 1.82D : 1.16D) + LivingWorldClientConfig.verticalOffset()
                + Math.max(0.0F, entity.getDisplayScale() - 1.0F) * 0.46D;
        poseStack.translate(0.0D, labelHeight, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        float labelScale = 0.028F * LivingWorldClientConfig.nameplateScale();
        poseStack.scale(-labelScale, -labelScale, labelScale);
        int color = WorldMenaceManager.isHerobrine(entity) ? 0xFFB51F2E
                : entity.getStoryRole() == AmbientFighterEntity.STORY_ALLY ? 0xFF7DFF9A
                : entity.getStoryRole() == AmbientFighterEntity.STORY_ENEMY ? 0xFFFF6969
                : entity.getStoryRole() == AmbientFighterEntity.STORY_CAPTIVE ? 0xFFFFD166
                : entity.getStoryRole() == AmbientFighterEntity.STORY_PEACEKEEPER ? 0xFF76D7FF
                : entity.isWanted() ? 0xFFFF6B6B
                : entity.isFactionLeader() ? 0xFFFFD76A
                : entity.getFactionRole() == com.dmzlivingworld.world.FactionRole.LIEUTENANT ? 0xFF7FDBFF
                : entity.getFactionRole() == com.dmzlivingworld.world.FactionRole.ENFORCER ? 0xFFE3B8FF
                : entity.getFactionRole() == com.dmzlivingworld.world.FactionRole.RECRUIT ? 0xFFC4C4C4
                : entity.isNonCombatant() ? 0xFFD2D2D2 : 0xFFFFFFFF;
        float nameX = -totalWidth / 2.0F;
        if (!indicator.isBlank() && dispositionView != null) {
            Component icon = Component.literal(indicator);
            font.drawInBatch(icon, nameX, 0.0F, dispositionView.disposition().color(), false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x00000000, packedLight);
            nameX += indicatorWidth;
        }
        font.drawInBatch(name, nameX, 0.0F, color, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x00000000, packedLight);

        String factionName = entity.getFactionDisplayName();
        int factionDistance = LivingWorldClientConfig.factionLabelDistance();
        if (LivingWorldClientConfig.showFactionLabel() && !factionName.isBlank()
                && minecraft.player.distanceToSqr(entity) <= (double)factionDistance * factionDistance) {
            Component faction = Component.literal(factionName);
            float fw = font.width(faction);
            font.drawInBatch(faction, -fw / 2.0F, 11.8F, 0xFFB8B8B8, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x00000000, packedLight);
        }
        poseStack.popPose();
    }

    private void renderSpeech(AmbientFighterEntity entity, PoseStack poseStack,
                              MultiBufferSource buffers, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity.isInvisible() || (minecraft.player != null && entity.isInvisibleTo(minecraft.player))) return;
        if (!LivingWorldClientConfig.showDialogue()) return;
        String speech = entity.getSpeech();
        if (speech == null || speech.isBlank()) return;
        int dialogueDistance = LivingWorldClientConfig.dialogueDistance();
        if (minecraft.player == null || minecraft.player.distanceToSqr(entity) > (double)dialogueDistance * dialogueDistance) return;

        Font font = minecraft.font;
        Component text = Component.literal(speech);
        float width = font.width(text);

        poseStack.pushPose();
        double speechHeight = entity.getBbHeight() + 1.92D + LivingWorldClientConfig.verticalOffset()
                + Math.max(0.0F, entity.getDisplayScale() - 1.0F) * 0.50D;
        poseStack.translate(0.0D, speechHeight, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        float dialogueScale = 0.025F * LivingWorldClientConfig.dialogueScale();
        poseStack.scale(-dialogueScale, -dialogueScale, dialogueScale);
        font.drawInBatch(text, -width / 2.0F, 0.0F, 0xFFFFFFFF, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x66000000, packedLight);
        poseStack.popPose();
    }    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }


}
