package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.network.FactionRequestTrackerPacket;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Persistent request HUD. R28 deliberately mirrors the server's exact objective radius/action/timer
 * so navigation never says "HERE" while the mission still expects hidden movement or waiting.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FactionRequestTrackerOverlay {
    private static volatile FactionRequestTrackerPacket current = FactionRequestTrackerPacket.clear();
    private static volatile String cachedLiveProgress = "";
    private static long cachedLiveProgressTick = Long.MIN_VALUE;
    private FactionRequestTrackerOverlay() { }

    public static void update(FactionRequestTrackerPacket packet) {
        current = packet == null ? FactionRequestTrackerPacket.clear() : packet;
        refreshLiveProgress(true);
    }
    public static void clear() {
        current = FactionRequestTrackerPacket.clear();
        cachedLiveProgress = "";
        cachedLiveProgressTick = Long.MIN_VALUE;
    }

    private static void refreshLiveProgress(boolean force) {
        FactionRequestTrackerPacket data = current;
        Minecraft mc = Minecraft.getInstance();
        if (data == null || !data.active() || mc.player == null) {
            cachedLiveProgress = data == null ? "" : data.progress();
            return;
        }
        long tick = mc.level == null ? 0L : mc.level.getGameTime();
        if (!force && tick - cachedLiveProgressTick < 5L) return;
        cachedLiveProgress = SupplyInventoryClient.withLiveCounts(data.progress(), data.supplyItems());
        cachedLiveProgressTick = tick;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        refreshLiveProgress(false);
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        FactionRequestTrackerPacket data = current;
        if (mc.player == null || mc.options.hideGui || data == null || !data.active()) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = 390, h = 120, x = screenW - w - 10;
        int totalH = h + (data.stepTotal() > 0 ? 70 : 0);
        // R38: active quest HUD lives in the bottom-right, above the screen edge as one compact block.
        int y = Math.max(8, screenH - totalH - 10);
        int compassArea = 70, textWidth = w - compassArea - 22;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, LivingWorldGuiStyle.GOLD_DARK);
        g.fill(x, y, x + w, y + h, 0xE80A1018);
        g.fill(x, y, x + 4, y + h, LivingWorldGuiStyle.GOLD);

        g.drawString(mc.font, "FACTION REQUEST", x + 10, y + 7, LivingWorldGuiStyle.GOLD, false);
        drawWrappedLimited(g, mc, data.title(), x + 10, y + 19, textWidth, LivingWorldGuiStyle.TEXT, 2);

        g.drawString(mc.font, "CURRENT OBJECTIVE", x + 10, y + 43, LivingWorldGuiStyle.BLUE, false);
        drawWrappedLimited(g, mc, data.stepLabel(), x + 10, y + 55, textWidth, LivingWorldGuiStyle.TEXT, 2);

        String liveProgress = cachedLiveProgress;
        if (!liveProgress.isBlank())
            drawWrappedLimited(g, mc, liveProgress, x + 10, y + 79, textWidth, LivingWorldGuiStyle.MUTED, 2);

        double dx = data.targetX() - mc.player.getX(), dz = data.targetZ() - mc.player.getZ();
        int liveDistance = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
        int radius = Math.max(1, data.arrivalRadius());
        boolean arrived = liveDistance <= radius;
        String liveDirection = arrived ? "" : cardinal(dx, dz);

        String nav;
        if (arrived) {
            if (data.secondsRemaining() >= 0)
                nav = "IN POSITION • " + data.secondsRemaining() + "s remaining";
            else if (!data.actionPrompt().isBlank())
                nav = "IN POSITION • " + data.actionPrompt();
            else nav = "IN POSITION • objective ready";
        } else {
            nav = liveDirection + " • " + liveDistance + " blocks";
            if (radius <= 6) nav += " • get within " + radius;
            else nav += " • objective range " + radius;
        }
        drawWrappedLimited(g, mc, nav, x + 10, y + h - 14, textWidth, arrived ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.BLUE, 1);

        drawCompass(g, mc, x + w - 35, y + 53, dx, dz, liveDistance, radius);
        if (data.stepTotal() > 0) drawMissionSteps(g, mc, data, x, y + h, w);
    }

    private static void drawMissionSteps(GuiGraphics g, Minecraft mc, FactionRequestTrackerPacket data, int x, int y, int w) {
        int h = 70;
        g.fill(x - 1, y, x + w + 1, y + h + 1, LivingWorldGuiStyle.GOLD_DARK);
        g.fill(x, y, x + w, y + h, 0xE80A1018);
        g.fill(x, y, x + 4, y + h, LivingWorldGuiStyle.BLUE);
        g.drawString(mc.font, "MISSION PROGRESS", x + 10, y + 6, LivingWorldGuiStyle.GOLD, false);
        String counter = data.stepIndex() + " / " + data.stepTotal();
        g.drawString(mc.font, counter, x + w - 10 - mc.font.width(counter), y + 6, LivingWorldGuiStyle.BLUE, false);

        int ladderX = x + 11, ladderY = y + 23;
        int total = Math.max(1, data.stepTotal());
        int usable = Math.min(130, w / 2 - 20);
        int gap = total <= 1 ? 0 : Math.max(8, usable / (total - 1));
        for (int i = 1; i <= total; i++) {
            int px = ladderX + (i - 1) * gap;
            if (i < total) g.fill(px + 5, ladderY + 3, px + gap, ladderY + 5,
                    i < data.stepIndex() ? LivingWorldGuiStyle.GREEN : LivingWorldGuiStyle.CONTROL_BORDER);
            int col = i < data.stepIndex() ? LivingWorldGuiStyle.GREEN
                    : i == data.stepIndex() ? LivingWorldGuiStyle.GOLD : LivingWorldGuiStyle.MUTED;
            g.fill(px, ladderY, px + 7, ladderY + 8, col);
            g.fill(px + 2, ladderY + 2, px + 5, ladderY + 6, 0xE80A1018);
        }

        int textX = x + Math.min(155, w / 2);
        int textW = w - (textX - x) - 10;
        String currentAction = data.actionPrompt().isBlank() ? data.stepLabel() : data.actionPrompt();
        drawWrappedLimited(g, mc, currentAction, textX, y + 20, textW, LivingWorldGuiStyle.TEXT, 2);
        if (!data.nextStep().isBlank())
            drawWrappedLimited(g, mc, data.nextStep(), textX, y + 45, textW, LivingWorldGuiStyle.MUTED, 2);
    }

    private static void drawWrappedLimited(GuiGraphics g, Minecraft mc, String text, int x, int y, int width, int color, int maxLines) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                mc.font.split(net.minecraft.network.chat.Component.literal(text == null ? "" : text), Math.max(24, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++)
            g.drawString(mc.font, lines.get(i), x, y + i * 11, color, false);
    }

    /** R27 source-contract overload retained; R28 callers supply the real objective radius. */
    private static void drawCompass(GuiGraphics g, Minecraft mc, int cx, int cy,
                                    double dx, double dz, int distance) {
        drawCompass(g, mc, cx, cy, dx, dz, distance, 4);
    }

    private static void drawCompass(GuiGraphics g, Minecraft mc, int cx, int cy,
                                    double dx, double dz, int distance, int arrivalRadius) {
        int r = 24;
        g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, 0xB5090E15);
        g.fill(cx - r, cy - r, cx + r + 1, cy - r + 1, LivingWorldGuiStyle.GOLD_DARK);
        g.fill(cx - r, cy + r, cx + r + 1, cy + r + 1, LivingWorldGuiStyle.GOLD_DARK);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r + 1, LivingWorldGuiStyle.GOLD_DARK);
        g.fill(cx + r, cy - r, cx + r + 1, cy + r + 1, LivingWorldGuiStyle.GOLD_DARK);

        float playerBearing = Mth.wrapDegrees(mc.player.getYRot() + 180.0F);
        drawCardinal(g, mc, cx, cy, r - 7, "N", -playerBearing, LivingWorldGuiStyle.GOLD);
        drawCardinal(g, mc, cx, cy, r - 7, "E", 90.0F - playerBearing, LivingWorldGuiStyle.MUTED);
        drawCardinal(g, mc, cx, cy, r - 7, "S", 180.0F - playerBearing, LivingWorldGuiStyle.MUTED);
        drawCardinal(g, mc, cx, cy, r - 7, "W", 270.0F - playerBearing, LivingWorldGuiStyle.MUTED);

        if (distance <= Math.max(1, arrivalRadius)) {
            g.fill(cx - 5, cy - 5, cx + 6, cy + 6, LivingWorldGuiStyle.GREEN);
            g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xE80A1018);
            g.drawString(mc.font, "READY", cx - 15, cy + 11, LivingWorldGuiStyle.GREEN, false);
            return;
        }

        float targetBearing = (float)Math.toDegrees(Math.atan2(dx, -dz));
        float relative = Mth.wrapDegrees(targetBearing - playerBearing);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(relative));
        // R41: slimmer tapered compass needle. It is intentionally smaller than R40 and reads
        // like an instrument pointer instead of a chunky quest arrow: gold points to the target,
        // blue is the counterweight, and a dark outline keeps the shape legible over N/E/S/W.
        int outline = 0xFF05080D;
        g.fill(-1, -13, 2, -5, outline);
        g.fill(-2, -10, 3, -6, outline);
        g.fill(-3, -7, 4, -5, outline);
        g.fill(-1, -5, 2, 8, outline);
        g.fill(-2, 6, 3, 9, outline);
        g.fill(0, -12, 1, -5, LivingWorldGuiStyle.GOLD);
        g.fill(-1, -9, 2, -6, LivingWorldGuiStyle.GOLD);
        g.fill(-2, -6, 3, -5, LivingWorldGuiStyle.GOLD);
        g.fill(0, -4, 1, 7, LivingWorldGuiStyle.BLUE);
        g.fill(-1, 7, 2, 8, LivingWorldGuiStyle.TEXT);
        g.pose().popPose();
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, LivingWorldGuiStyle.GOLD);
    }

    private static void drawCardinal(GuiGraphics g, Minecraft mc, int cx, int cy, int radius,
                                     String label, float degrees, int color) {
        double rad = Math.toRadians(degrees);
        int px = cx + (int)Math.round(Math.sin(rad) * radius) - mc.font.width(label) / 2;
        int py = cy - (int)Math.round(Math.cos(rad) * radius) - 4;
        g.drawString(mc.font, label, px, py, color, false);
    }

    private static String cardinal(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        if (a < 0.0D) a += 360.0D;
        int sector = Math.floorMod((int)Math.round(a / 45.0D), 8);
        return switch (sector) {
            case 0 -> "N"; case 1 -> "NE"; case 2 -> "E"; case 3 -> "SE";
            case 4 -> "S"; case 5 -> "SW"; case 6 -> "W"; default -> "NW";
        };
    }
}
