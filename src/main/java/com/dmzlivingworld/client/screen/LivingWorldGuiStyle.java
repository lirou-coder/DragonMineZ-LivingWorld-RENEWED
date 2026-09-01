package com.dmzlivingworld.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Shared visual language for every Living World screen and control. */
public final class LivingWorldGuiStyle {
    // Contrast Preview palette: same layout, stronger hierarchy and richer DMZ-style accents.
    public static final int GOLD = 0xFFFFD85A;
    public static final int GOLD_DARK = 0xFFA47724;
    public static final int TEXT = 0xFFF2F5F8;
    public static final int MUTED = 0xFFB0BBC7;
    public static final int GREEN = 0xFF70E98B;
    public static final int BLUE = 0xFF63C9FF;
    public static final int BLUE_DARK = 0xFF315E7A;
    public static final int ORANGE = 0xFFFFA956;
    public static final int RED = 0xFFFF6666;
    public static final int NEUTRAL = 0xFFA9B3BE;
    public static final int PANEL = 0xF20A1018;
    public static final int PANEL_INNER = 0xFF111A24;
    public static final int CARD = 0xFF0E1720;
    public static final int CARD_ALT = 0xFF121E29;
    public static final int CARD_BORDER = 0xFF3F5B73;
    public static final int CONTROL = 0xFF182635;
    public static final int CONTROL_HOVER = 0xFF24445D;
    public static final int CONTROL_BORDER = 0xFF4D6B83;
    public static final int CONTROL_DISABLED = 0xFF10171F;
    public static final int CONTROL_SELECTED = 0xFF493815;
    public static final int DIVIDER = 0xFF52718B;
    public static final int HEADER_FILL = 0xFF152839;
    public static final int HOVER_ROW = 0x88416986;


    private LivingWorldGuiStyle() { }

    /** Strong shared outer frame used by all Living World screens. */
    public static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;

        graphics.fill(left - 4, top - 4, right + 4, bottom + 4, 0x99000000);
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, GOLD_DARK);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, GOLD);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF5A4818);
        graphics.fill(left, top, right, bottom, PANEL);
        // Cool inner edge breaks up the previous wall of charcoal without fighting the gold frame.
        graphics.fill(left, top, right, top + 1, BLUE_DARK);
        graphics.fill(left, top, left + 1, bottom, 0xFF233A4D);

        graphics.fill(left - 3, top - 3, left + 12, top - 1, GOLD);
        graphics.fill(left - 3, top - 3, left - 1, top + 12, GOLD);
        graphics.fill(right - 12, top - 3, right + 3, top - 1, GOLD);
        graphics.fill(right + 1, top - 3, right + 3, top + 12, GOLD);
        graphics.fill(left - 3, bottom + 1, left + 12, bottom + 3, GOLD_DARK);
        graphics.fill(left - 3, bottom - 12, left - 1, bottom + 3, GOLD_DARK);
        graphics.fill(right - 12, bottom + 1, right + 3, bottom + 3, GOLD_DARK);
        graphics.fill(right + 1, bottom - 12, right + 3, bottom + 3, GOLD_DARK);
    }

    /** Character-panel style inset card. */
    public static void drawInsetPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        drawInsetPanel(graphics, left, top, width, height, CARD_BORDER);
    }

    public static void drawInsetPanel(GuiGraphics graphics, int left, int top, int width, int height, int accent) {
        int right = left + width;
        int bottom = top + height;
        graphics.fill(left, top, right, bottom, accent);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, CARD);
        graphics.fill(left + 2, top + 1, right - 2, top + 2, 0xFF1D3040);
    }

    public static void drawHeaderDivider(GuiGraphics graphics, int left, int right, int y) {
        graphics.fill(left, y, right, y + 1, DIVIDER);
    }

    /** Compact status/page chip matching the disposition chip on fighter profiles. */
    public static void drawChip(GuiGraphics graphics, Font font, String label,
                                int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_INNER);
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, color);
        graphics.fill(x + 4, y + 1, x + width - 1, y + 2, 0xFF263B4D);
        drawCentered(graphics, font, label, x + 3, y, Math.max(1, width - 3), height, color);
    }

    /** Section title bar used inside content cards. */
    public static void drawSectionHeader(GuiGraphics graphics, Font font, String title,
                                         int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 13, HEADER_FILL);
        graphics.fill(x, y, x + 3, y + 13, BLUE);
        graphics.fill(x + 3, y + 12, x + width, y + 13, GOLD_DARK);
        drawFitted(graphics, font, title, x + 7, y + 3, Math.max(8, width - 11), GOLD);
    }

    public static void drawScrollBar(GuiGraphics graphics, int x, int top, int bottom, int scroll, int maxScroll) {
        if (maxScroll <= 0 || bottom <= top) return;
        int trackHeight = bottom - top;
        int thumb = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight + maxScroll, 1));
        int thumbY = top + (trackHeight - thumb) * Math.max(0, Math.min(scroll, maxScroll)) / Math.max(maxScroll, 1);
        graphics.fill(x, top, x + 2, bottom, 0x443E4650);
        graphics.fill(x, thumbY, x + 2, thumbY + thumb, BLUE);
    }

    public static void drawButton(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height, String label,
                                  int mouseX, int mouseY,
                                  boolean enabled, boolean selected, boolean primary) {
        boolean hover = enabled && isInside(mouseX, mouseY, x, y, width, height);
        boolean pressed = hover && isLeftMouseDown();
        int border = enabled
                ? ((selected || primary) ? GOLD_DARK : CONTROL_BORDER)
                : 0xFF333940;
        int fill = enabled
                ? (pressed ? 0xFF17212B : (hover ? CONTROL_HOVER : (selected ? CONTROL_SELECTED : CONTROL)))
                : CONTROL_DISABLED;
        int textColor = enabled
                ? ((selected || primary) ? GOLD : TEXT)
                : 0xFF626B74;

        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        if (selected || primary) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, GOLD_DARK);
            graphics.fill(x + 1, y + 2, x + 5, y + height - 1, GOLD);
        }
        if (hover) {
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1,
                    selected || primary ? GOLD : 0xFF6A7D8F);
        }
        drawCentered(graphics, font, label, x, y + (pressed ? 1 : 0), width, height, textColor);
    }

    /** Destructive action button: unmistakably clickable without making it look like ordinary body text. */
    public static void drawDangerButton(GuiGraphics graphics, Font font,
                                        int x, int y, int width, int height, String label,
                                        int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && isInside(mouseX, mouseY, x, y, width, height);
        boolean pressed = hover && isLeftMouseDown();
        int border = enabled ? 0xFFB54848 : 0xFF333940;
        int fill = enabled ? (pressed ? 0xFF351719 : (hover ? 0xFF482326 : 0xFF2B1B20)) : CONTROL_DISABLED;
        int text = enabled ? (hover ? 0xFFFFB0B0 : RED) : 0xFF626B74;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 1, y + 1, x + 5, y + height - 1, enabled ? RED : 0xFF555B62);
        if (hover) graphics.fill(x + 5, y + height - 2, x + width - 2, y + height - 1, RED);
        drawCentered(graphics, font, label, x + 3, y + (pressed ? 1 : 0), Math.max(1, width - 3), height, text);
    }

    /** Draws a crisp pixel chevron fully inside the control border at every GUI scale. */
    public static void drawArrowButton(GuiGraphics graphics, Font font,
                                       int x, int y, int width, int height, String label,
                                       int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && isInside(mouseX, mouseY, x, y, width, height);
        boolean pressed = hover && isLeftMouseDown();
        int border = enabled ? GOLD_DARK : 0xFF333940;
        int fill = enabled ? (pressed ? 0xFF17212B : (hover ? CONTROL_HOVER : CONTROL)) : CONTROL_DISABLED;
        int color = enabled ? GOLD : 0xFF626B74;

        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        if (hover) graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, GOLD);

        boolean left = "<".equals(label);
        boolean right = ">".equals(label);
        if (!left && !right) {
            drawCentered(graphics, font, label, x + 2, y + 2, Math.max(1, width - 4), Math.max(1, height - 4), color);
            return;
        }

        int cx = x + width / 2;
        int cy = y + height / 2 + (pressed ? 1 : 0);
        int dir = left ? 1 : -1;
        // Five 2x2 steps form a compact chevron with at least a 3 px inset from normal borders.
        for (int i = -2; i <= 2; i++) {
            int px = cx + dir * Math.abs(i) * 2 - 1;
            int py = cy + i * 2 - 1;
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }

    public static String fitText(Font font, String label, int maxWidth) {
        if (label == null || label.isEmpty() || maxWidth <= 0) return "";
        if (font.width(label) <= maxWidth) return label;
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) return "";
        String trimmed = font.plainSubstrByWidth(label, Math.max(0, maxWidth - ellipsisWidth));
        return trimmed + ellipsis;
    }

    public static void drawFitted(GuiGraphics graphics, Font font, String label,
                                  int x, int y, int maxWidth, int color) {
        graphics.drawString(font, fitText(font, label, maxWidth), x, y, color, false);
    }

    public static void drawCentered(GuiGraphics graphics, Font font, String label,
                                    int x, int y, int width, int height, int color) {
        String fitted = fitText(font, label, Math.max(0, width - 8));
        int textX = x + Math.max(0, (width - font.width(fitted)) / 2);
        int textY = y + Math.max(0, (height - font.lineHeight) / 2) + 1;
        graphics.drawString(font, fitted, textX, textY, color, false);
    }

    private static boolean isLeftMouseDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
