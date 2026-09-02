package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.LWNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** Player-facing Living World guide using the same card layout as fighter profiles. */
@OnlyIn(Dist.CLIENT)
public final class LivingWorldGuideScreen extends Screen implements LivingWorldScreenMarker {
    private record Chapter(String title, String subtitle, List<String> lines) {}
    private record VisualLine(FormattedCharSequence text, int color, int gapBefore) {}

    private static final List<Chapter> CHAPTERS = List.of(
            chapter("Getting Started", "The basics",
                    "Living World adds persistent Dragon Mine Z fighters who live, grow and remember what happens.",
                    "* L — open Living World.",
                    "* Fighter Interact + Right-click a Living World fighter — open their character panel.",
                    "* Talk, travel and shared experiences build personal relationships over time.",
                    "* Fighters can train, fight, join factions, form bonds and pursue their own goals."),
            chapter("People", "Characters you have met",
                    "* People shows fighters your character knows and the last information you learned about them.",
                    "* Fighter Interact + Right-click shows their identity, story, combat information and actions.",
                    "* Friends may travel, meditate or fight beside you depending on the situation and their personality.",
                    "* Remembered fighters can be used for Instant Transmission when your Dragon Mine Z skill can reach them."),
            chapter("Factions & Requests", "Work that exists for a reason",
                    "* Factions remember your reputation and may treat you differently from individual members.",
                    "* Requests appear only when a faction currently needs outside help.",
                    "* Factions → Active Quest always shows who issued your current request.",
                    "* The active quest HUD gives live direction, progress and the next objective.",
                    "* Supply requests name the exact receiver and exact items needed."),
            chapter("Travel", "Go with people",
                    "* Go Along lets you accompany something a fighter is already doing.",
                    "* Come Along asks a fighter to travel with you for a while.",
                    "* Travelling fighters use their real movement and flight abilities and can defend themselves naturally.",
                    "* L → Companion manages your current travelling companion."),
            chapter("Fusion & Meditation", "Shared Dragon Mine Z systems",
                    "* Fighter Interact + Right-click → Fusion starts a compatible Fusion Dance with that fighter.",
                    "* Normal Dragon Mine Z requirements, power limits and cooldowns still apply.",
                    "* M — start or stop meditation.",
                    "* Friendly fighters can sometimes meditate with you, and deeper meditation can provide configured training rewards."),
            chapter("Controls & Settings", "Quick reference",
                    "* L — Living World menu.",
                    "* Fighter Interact + Right-click — inspect a fighter (the modifier is configurable).",
                    "* M — meditation.",
                    "* ? — this guide.",
                    "* World Settings controls gameplay rules; Display settings control your own HUD and visual preferences.")
    );

    private final List<VisualLine> visualLines = new ArrayList<>();
    private int chapterIndex;
    private int scroll;
    private int maxScroll;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int navWidth;

    private LivingWorldGuideScreen() {
        super(Component.literal("Living World — Guide"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new LivingWorldGuideScreen());
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(620, Math.max(1, width - 8));
        panelHeight = Math.min(390, Math.max(1, height - 8));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        navWidth = panelWidth >= 430 ? Math.min(154, Math.max(122, panelWidth / 4)) : 0;
        rebuildLines();
    }

    private int headerBottom() { return panelTop + 45; }
    private int bodyTop() { return panelTop + 52; }
    private int footerY() { return panelTop + panelHeight - 27; }
    private int bodyBottom() { return footerY() - 5; }
    private int navX() { return panelLeft + 12; }
    private int navButtonWidth() { return navWidth - 14; }
    private int navButtonHeight() {
        int available = bodyBottom() - bodyTop() - 50;
        return Math.max(15, Math.min(20, (available - (CHAPTERS.size() - 1) * 4) / CHAPTERS.size()));
    }
    private int navButtonY(int index) { return bodyTop() + 37 + index * (navButtonHeight() + 4); }
    private int contentLeft() { return navWidth > 0 ? panelLeft + navWidth + 20 : panelLeft + 12; }
    private int contentRight() { return panelLeft + panelWidth - 12; }
    private int closeX() { return panelLeft + panelWidth - 40; }

    private static Chapter chapter(String title, String subtitle, String... lines) {
        return new Chapter(title, subtitle, List.of(lines));
    }

    private void selectChapter(int index) {
        chapterIndex = Math.max(0, Math.min(CHAPTERS.size() - 1, index));
        scroll = 0;
        rebuildLines();
    }

    private void rebuildLines() {
        visualLines.clear();
        Chapter chapter = CHAPTERS.get(chapterIndex);
        int wrap = Math.max(24, contentRight() - contentLeft() - 24);
        for (String raw : chapter.lines()) {
            if (raw == null) continue;
            String line = raw;
            int color = LivingWorldGuiStyle.TEXT;
            int gap = 0;
            if (line.startsWith("## ")) { line = line.substring(3); color = LivingWorldGuiStyle.GOLD; gap = 6; }
            else if (line.startsWith("* ")) { line = "• " + line.substring(2); color = 0xFFD7E3EC; }
            else if (line.startsWith(". ")) { line = line.substring(2); color = LivingWorldGuiStyle.MUTED; gap = 2; }
            else if (line.startsWith("~ ")) { line = line.substring(2); color = LivingWorldGuiStyle.BLUE; }
            List<FormattedCharSequence> wrapped = font.split(Component.literal(line), wrap);
            if (wrapped.isEmpty()) wrapped = List.of(Component.empty().getVisualOrderText());
            boolean first = true;
            for (FormattedCharSequence seq : wrapped) {
                visualLines.add(new VisualLine(seq, color, first ? gap : 0));
                first = false;
            }
        }
        int content = 0;
        for (VisualLine line : visualLines) content += 11 + line.gapBefore();
        int textTop = bodyTop() + 47;
        maxScroll = Math.max(0, content - Math.max(1, bodyBottom() - textTop - 8));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LivingWorldGuiStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        LivingWorldGuiStyle.drawHeaderDivider(graphics, panelLeft + 7, panelLeft + panelWidth - 7, headerBottom());

        boolean compact = panelWidth < 360;
        int titleW = Math.max(24, panelWidth - (compact ? 62 : 130));
        LivingWorldGuiStyle.drawFitted(graphics, font, "DRAGON MINE Z: LIVING WORLD", panelLeft + 13, panelTop + 10,
                titleW, 0xFFFFE29A);
        if (panelHeight >= 190) LivingWorldGuiStyle.drawFitted(graphics, font, "Player Guide", panelLeft + 13, panelTop + 26,
                titleW, LivingWorldGuiStyle.MUTED);
        if (!compact) LivingWorldGuiStyle.drawChip(graphics, font, "GUIDE", closeX() - 74, panelTop + 8, 66, 20, LivingWorldGuiStyle.BLUE);
        LivingWorldGuiStyle.drawButton(graphics, font, closeX(), panelTop + 8, 25, 20, "×",
                mouseX, mouseY, true, false, false);

        if (navWidth > 0) drawNavigation(graphics, mouseX, mouseY);
        else drawCompactNavigation(graphics, mouseX, mouseY);
        drawContent(graphics);

        int backW = panelWidth < 260 ? 46 : 64;
        LivingWorldGuiStyle.drawButton(graphics, font, panelLeft + panelWidth - 12 - backW, footerY(), backW, 18, "Back",
                mouseX, mouseY, true, false, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = bodyTop();
        int height = bodyBottom() - top;
        LivingWorldGuiStyle.drawInsetPanel(graphics, navX(), top, navWidth, height);
        LivingWorldGuiStyle.drawFitted(graphics, font, "CHAPTERS", navX() + 8, top + 9, navWidth - 16, LivingWorldGuiStyle.GOLD);
        LivingWorldGuiStyle.drawFitted(graphics, font, "Quick reference", navX() + 8, top + 22, navWidth - 16, LivingWorldGuiStyle.MUTED);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            LivingWorldGuiStyle.drawButton(graphics, font, navX() + 7, navButtonY(i), navButtonWidth(), navButtonHeight(),
                    CHAPTERS.get(i).title(), mouseX, mouseY, true, i == chapterIndex, false);
        }
    }

    private void drawCompactNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = footerY();
        int left = panelLeft + 12;
        int arrowW = 26;
        int backReserve = panelWidth < 260 ? 58 : 76;
        int labelX = left + arrowW + 4;
        int labelW = Math.max(24, panelWidth - 24 - arrowW * 2 - 8 - backReserve);
        LivingWorldGuiStyle.drawArrowButton(graphics, font, left, y, arrowW, 18, "<", mouseX, mouseY, chapterIndex > 0);
        LivingWorldGuiStyle.drawChip(graphics, font, CHAPTERS.get(chapterIndex).title(), labelX, y, labelW, 18, LivingWorldGuiStyle.BLUE);
        LivingWorldGuiStyle.drawArrowButton(graphics, font, labelX + labelW + 4, y, arrowW, 18, ">", mouseX, mouseY, chapterIndex + 1 < CHAPTERS.size());
    }

    private void drawContent(GuiGraphics graphics) {
        int left = contentLeft();
        int top = bodyTop();
        int right = contentRight();
        int bottom = bodyBottom();
        LivingWorldGuiStyle.drawInsetPanel(graphics, left, top, right - left, bottom - top);

        Chapter chapter = CHAPTERS.get(chapterIndex);
        LivingWorldGuiStyle.drawFitted(graphics, font, chapter.title(), left + 10, top + 10,
                right - left - 20, LivingWorldGuiStyle.GOLD);
        LivingWorldGuiStyle.drawFitted(graphics, font, chapter.subtitle(), left + 10, top + 24,
                right - left - 20, LivingWorldGuiStyle.MUTED);
        LivingWorldGuiStyle.drawHeaderDivider(graphics, left + 8, right - 8, top + 39);

        int clipLeft = left + 8;
        int clipTop = top + 47;
        int clipRight = right - 8;
        int clipBottom = bottom - 7;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        int y = clipTop - scroll;
        for (VisualLine line : visualLines) {
            y += line.gapBefore();
            if (y > clipTop - 14 && y < clipBottom + 4) {
                graphics.drawString(font, line.text(), clipLeft + 2, y, line.color(), false);
            }
            y += 11;
        }
        graphics.disableScissor();
        LivingWorldGuiStyle.drawScrollBar(graphics, right - 4, clipTop, clipBottom, scroll, maxScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (navWidth > 0) {
                for (int i = 0; i < CHAPTERS.size(); i++) {
                    if (LivingWorldGuiStyle.isInside(mouseX, mouseY, navX() + 7, navButtonY(i), navButtonWidth(), navButtonHeight())) {
                        selectChapter(i);
                        return true;
                    }
                }
            } else {
                int left = panelLeft + 12, y = footerY(), arrowW = 26;
                int backReserve = panelWidth < 260 ? 58 : 76;
                int labelW = Math.max(24, panelWidth - 24 - arrowW * 2 - 8 - backReserve);
                int nextX = left + arrowW + 4 + labelW + 4;
                if (chapterIndex > 0 && LivingWorldGuiStyle.isInside(mouseX, mouseY, left, y, arrowW, 18)) {
                    selectChapter(chapterIndex - 1); return true;
                }
                if (chapterIndex + 1 < CHAPTERS.size() && LivingWorldGuiStyle.isInside(mouseX, mouseY, nextX, y, arrowW, 18)) {
                    selectChapter(chapterIndex + 1); return true;
                }
            }
            int backW = panelWidth < 260 ? 46 : 64;
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, panelLeft + panelWidth - 12 - backW, footerY(), backW, 18)) {
                LWNetwork.requestMenu("world", 0);
                return true;
            }
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, closeX(), panelTop + 8, 25, 20)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, delta);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(delta * 26.0D)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
