package com.dmzlivingworld.client;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.LWEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_LIVING_WORLD = new KeyMapping(
            "key.dmzlivingworld.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L,
            "key.categories.dmzlivingworld");
    public static final KeyMapping TRACK_LAST_WORLD_EVENT = new KeyMapping(
            "key.dmzlivingworld.track_last_event", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            "key.categories.dmzlivingworld");
    public static final KeyMapping FIGHTER_INTERACT = new KeyMapping(
            "key.dmzlivingworld.fighter_interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.dmzlivingworld");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LWEntities.AMBIENT_FIGHTER.get(), FighterRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_LIVING_WORLD);
        event.register(TRACK_LAST_WORLD_EVENT);
        event.register(FIGHTER_INTERACT);
    }
}
