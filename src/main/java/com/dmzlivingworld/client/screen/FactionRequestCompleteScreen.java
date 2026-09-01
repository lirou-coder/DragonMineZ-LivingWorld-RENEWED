package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.FactionRequestCompletePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** One-shot request completion receipt: outcome, exact payout and simulation consequence. */
@OnlyIn(Dist.CLIENT)
public final class FactionRequestCompleteScreen extends Screen implements LivingWorldScreenMarker {
    private final FactionRequestCompletePacket data;
    private int left, top, panelWidth, panelHeight;

    private FactionRequestCompleteScreen(FactionRequestCompletePacket data) {
        super(Component.literal("Faction Request Complete"));
        this.data = data;
    }

    public static void open(FactionRequestCompletePacket packet) {
        Minecraft.getInstance().setScreen(new FactionRequestCompleteScreen(packet));
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(520, Math.max(330, width - 24));
        panelHeight = Math.min(330, Math.max(270, height - 24));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LivingWorldGuiStyle.drawPanel(g, left, top, panelWidth, panelHeight);
        LivingWorldGuiStyle.drawSectionHeader(g, font, "REQUEST COMPLETE", left + 14, top + 13, panelWidth - 28);
        LivingWorldGuiStyle.drawFitted(g, font, data.title(), left + 18, top + 34, panelWidth - 36, LivingWorldGuiStyle.GOLD);
        if (!data.factionName().isBlank())
            LivingWorldGuiStyle.drawFitted(g, font, data.factionName(), left + 18, top + 47, panelWidth - 36, LivingWorldGuiStyle.MUTED);

        int y = top + 66;
        y = section(g, "OUTCOME", data.summary(), y);
        y = section(g, "REWARDS", data.rewards().isBlank() ? "No direct payout." : data.rewards(), y + 4);
        section(g, "WORLD IMPACT", data.worldImpact().isBlank() ? "The faction records the completed operation." : data.worldImpact(), y + 4);
        int bw = 128, bh = 24;
        LivingWorldGuiStyle.drawButton(g, font, left + panelWidth - bw - 16, top + panelHeight - bh - 14,
                bw, bh, "Continue", mouseX, mouseY, true, false, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int bw = 128, bh = 24;
            int bx = left + panelWidth - bw - 16, by = top + panelHeight - bh - 14;
            if (LivingWorldGuiStyle.isInside(mouseX, mouseY, bx, by, bw, bh)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int section(GuiGraphics g, String title, String body, int y) {
        int x = left + 18, w = panelWidth - 36;
        LivingWorldGuiStyle.drawSectionHeader(g, font, title, x, y, w);
        y += 17;
        List<FormattedCharSequence> lines = font.split(Component.literal(body), w - 10);
        int max = Math.min(4, lines.size());
        for (int i = 0; i < max; i++) {
            g.drawString(font, lines.get(i), x + 5, y, LivingWorldGuiStyle.TEXT, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
