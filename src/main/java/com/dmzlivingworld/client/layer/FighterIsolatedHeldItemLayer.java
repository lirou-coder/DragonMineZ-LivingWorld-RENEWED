package com.dmzlivingworld.client.layer;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlwfusion.mixin.GeoItemRendererAccessor;
import com.dragonminez.common.init.item.weapons.BraveSwordItem;
import com.dragonminez.common.init.item.weapons.DimensionalSwordItem;
import com.dragonminez.common.init.item.weapons.PowerPoleItem;
import com.dragonminez.common.init.item.weapons.YajirobeKatanaItem;
import com.dragonminez.common.init.item.weapons.ZSwordItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.GeckoLibException;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * R36: DMZ's GeoItem weapons use singleton Item objects whose custom renderer/model is shared by
 * every copy of that weapon. A player copy can therefore render through the same mutable GeckoLib
 * model graph later used for an LW fighter's copy. Stack GeckoLib IDs do not isolate that graph.
 *
 * Keep DMZ's exact native right_hand_item anchor and exact item transforms, and keep Minecraft's
 * normal ItemRenderer path. Only while rendering a supported LW-held DMZ GeoItem, temporarily swap
 * that weapon renderer's GeoModel to an LW-owned deep-cloned baked model. The swap is synchronous
 * on the render thread and is restored in finally, so player/inventory rendering remains untouched.
 */
public final class FighterIsolatedHeldItemLayer extends GeoRenderLayer<AmbientFighterEntity> {
    private static final ResourceLocation KATANA_MODEL = dmz("geo/weapons/yajirobe_katana.geo.json");
    private static final ResourceLocation KATANA_TEXTURE = dmz("textures/item/weapons/yajirobe_katana.png");
    private static final ResourceLocation Z_SWORD_MODEL = dmz("geo/weapons/z_sword.geo.json");
    private static final ResourceLocation Z_SWORD_TEXTURE = dmz("textures/item/weapons/z_sword.png");
    private static final ResourceLocation BRAVE_SWORD_MODEL = dmz("geo/weapons/brave_sword.geo.json");
    private static final ResourceLocation BRAVE_SWORD_TEXTURE = dmz("textures/item/weapons/brave_sword.png");
    private static final ResourceLocation DIMENSIONAL_SWORD_MODEL = dmz("geo/weapons/dimensional_sword.geo.json");
    private static final ResourceLocation DIMENSIONAL_SWORD_TEXTURE = dmz("textures/item/weapons/dimensional_sword.png");
    private static final ResourceLocation POWER_POLE_MODEL = dmz("geo/weapons/power_pole.geo.json");
    private static final ResourceLocation POWER_POLE_TEXTURE = dmz("textures/item/weapons/power_pole.png");

    private final GeoModel<YajirobeKatanaItem> katanaModel = new IsolatedStaticGeoItemModel<>(KATANA_MODEL, KATANA_TEXTURE);
    private final GeoModel<ZSwordItem> zSwordModel = new IsolatedStaticGeoItemModel<>(Z_SWORD_MODEL, Z_SWORD_TEXTURE);
    private final GeoModel<BraveSwordItem> braveSwordModel = new IsolatedStaticGeoItemModel<>(BRAVE_SWORD_MODEL, BRAVE_SWORD_TEXTURE);
    private final GeoModel<DimensionalSwordItem> dimensionalSwordModel = new IsolatedStaticGeoItemModel<>(DIMENSIONAL_SWORD_MODEL, DIMENSIONAL_SWORD_TEXTURE);
    private final GeoModel<PowerPoleItem> powerPoleModel = new IsolatedStaticGeoItemModel<>(POWER_POLE_MODEL, POWER_POLE_TEXTURE);

    public FighterIsolatedHeldItemLayer(GeoRenderer<AmbientFighterEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, AmbientFighterEntity fighter, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if (!"right_hand_item".equals(bone.getName())) return;

        ItemStack stack = fighter.getItemBySlot(EquipmentSlot.MAINHAND);
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        try {
            // Byte-for-byte equivalent transform sequence to DMZ 2.1.3's DMZSagaItemInHandLayer.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
            poseStack.translate(0.4D, 0.1D, 0.73D);

            if (!renderWithIsolatedGeoModel(stack, fighter, poseStack, bufferSource, packedLight, packedOverlay)) {
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        bufferSource,
                        fighter.level(),
                        fighter.getId());
            }

            // DMZ's native layer does this after custom item rendering because the shared
            // BufferBuilder may have switched render types during the nested item pass.
            bufferSource.getBuffer(renderType);
        } finally {
            poseStack.popPose();
        }
    }

    private boolean renderWithIsolatedGeoModel(ItemStack stack, AmbientFighterEntity fighter,
                                               PoseStack poseStack, MultiBufferSource bufferSource,
                                               int packedLight, int packedOverlay) {
        GeoModel<?> isolated = isolatedModelFor(stack.getItem());
        if (isolated == null || !(stack.getItem() instanceof GeoItem)) return false;

        BlockEntityWithoutLevelRenderer customRenderer = IClientItemExtensions.of(stack).getCustomRenderer();
        if (!(customRenderer instanceof GeoItemRenderer<?>)) return false;
        if (!(customRenderer instanceof GeoItemRendererAccessor accessor)) return false;

        GeoModel<?> original = accessor.dmzlivingworld$getModel();
        accessor.dmzlivingworld$setModel(isolated);
        try {
            // Intentionally keep the vanilla/Forge ItemRenderer call. It owns the display-context
            // transform pipeline and then invokes DMZ's same concrete custom renderer, including
            // its Brave Sword / Power Pole sheath-hiding behavior. Only the model graph is isolated.
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    fighter.level(),
                    fighter.getId());
            return true;
        } finally {
            accessor.dmzlivingworld$setModel(original);
        }
    }

    private GeoModel<?> isolatedModelFor(Item item) {
        if (item instanceof YajirobeKatanaItem) return katanaModel;
        if (item instanceof ZSwordItem) return zSwordModel;
        if (item instanceof BraveSwordItem) return braveSwordModel;
        if (item instanceof DimensionalSwordItem) return dimensionalSwordModel;
        if (item instanceof PowerPoleItem) return powerPoleModel;
        return null;
    }

    private static ResourceLocation dmz(String path) {
        return new ResourceLocation("dragonminez", path);
    }

    /**
     * A static GeoItem model that never consults the singleton item's AnimatableInstanceCache and
     * never returns GeckoLibCache's shared mutable GeoBone graph. Geometry data is cloned in memory;
     * no DragonMineZ asset is copied into the Living World jar.
     */
    private static final class IsolatedStaticGeoItemModel<T extends Item & GeoAnimatable> extends GeoModel<T> {
        private final ResourceLocation modelResource;
        private final ResourceLocation textureResource;
        private final Map<ResourceLocation, BakedGeoModel> isolatedBakedModels = new HashMap<>();
        private BakedGeoModel activeIsolatedModel;

        private IsolatedStaticGeoItemModel(ResourceLocation modelResource, ResourceLocation textureResource) {
            this.modelResource = modelResource;
            this.textureResource = textureResource;
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return modelResource;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return textureResource;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return null;
        }

        @Override
        public BakedGeoModel getBakedModel(ResourceLocation location) {
            BakedGeoModel isolated = isolatedBakedModels.computeIfAbsent(location, this::cloneBakedModel);
            if (isolated != activeIsolatedModel) {
                getAnimationProcessor().setActiveModel(isolated);
                activeIsolatedModel = isolated;
            }
            return isolated;
        }

        @Override
        public void handleAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
            // These exact DMZ weapons register no GeckoLib controllers and return no animation
            // resource. Do not touch the singleton GeoItem animation cache at all for NPC renders.
        }

        private BakedGeoModel cloneBakedModel(ResourceLocation location) {
            BakedGeoModel source = GeckoLibCache.getBakedModels().get(location);
            if (source == null) throw new GeckoLibException(location, "Unable to find model");

            ArrayList<GeoBone> roots = new ArrayList<>(source.topLevelBones().size());
            for (GeoBone root : source.topLevelBones()) roots.add(cloneBone(root, null));
            return new BakedGeoModel(roots, source.properties());
        }

        private GeoBone cloneBone(GeoBone source, GeoBone parent) {
            GeoBone clone = new GeoBone(
                    parent,
                    source.getName(),
                    source.getMirror(),
                    source.getInflate(),
                    source.shouldNeverRender(),
                    source.getReset());

            clone.setPivotX(source.getPivotX());
            clone.setPivotY(source.getPivotY());
            clone.setPivotZ(source.getPivotZ());

            var initial = source.getInitialSnapshot();
            if (initial != null) {
                clone.setRotX(initial.getRotX());
                clone.setRotY(initial.getRotY());
                clone.setRotZ(initial.getRotZ());
                clone.setPosX(initial.getOffsetX());
                clone.setPosY(initial.getOffsetY());
                clone.setPosZ(initial.getOffsetZ());
                clone.setScaleX(initial.getScaleX());
                clone.setScaleY(initial.getScaleY());
                clone.setScaleZ(initial.getScaleZ());
            } else {
                clone.setRotX(source.getRotX());
                clone.setRotY(source.getRotY());
                clone.setRotZ(source.getRotZ());
                clone.setPosX(source.getPosX());
                clone.setPosY(source.getPosY());
                clone.setPosZ(source.getPosZ());
                clone.setScaleX(source.getScaleX());
                clone.setScaleY(source.getScaleY());
                clone.setScaleZ(source.getScaleZ());
            }

            clone.getCubes().addAll(source.getCubes());
            for (GeoBone child : source.getChildBones()) clone.getChildBones().add(cloneBone(child, clone));
            clone.resetStateChanges();
            return clone;
        }
    }
}
