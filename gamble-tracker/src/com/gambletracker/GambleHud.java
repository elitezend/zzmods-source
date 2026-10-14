package com.gambletracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.List;

// the queue card drawn over gameplay
public final class GambleHud {
    static final int W = 172;
    private static final int PAD = 6;
    private static final int GEAR = 11;

    private static float[] gearRect; // x, y, size in GUI coords; null while the card is hidden

    private GambleHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        GambleState st = GambleState.INSTANCE;
        GambleOnline.frame();
        // not linked yet: the tutorial (welcome, then linking) still runs - linking is its first step
        if (!GambleOnline.blocked() && "link".equals(GambleOnline.lockReason())) {
            GambleTour.frame();
            if (GambleTour.linking() || GambleTour.active() || mc.gui.screen() != null) {
                GambleTour.renderHud(g);
                return;
            }
        }
        if (GambleOnline.off()) {
            if (mc.gui.screen() == null && mc.level != null) {
                String key = GambleState.keyName(st.settingsKey);
                if (GambleOnline.blocked()) GambleOnline.renderBlocked(g, key);
                else GambleOnline.renderLocked(g, key, 40);
            }
            GambleOnline.renderToast(g);
            return;
        }
        GambleTour.frame();
        GambleTour.renderHud(g);
        GambleOnline.renderToast(g);
        gearRect = null;
        if (!st.active || st.hud == GambleState.HudSpot.OFF || mc.level == null) {
            return;
        }
        boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
        Font font = mc.font;
        float scale = st.scale();
        int alpha = st.opacity * 255 / 100;
        List<GambleState.Bet> queue = st.queue();
        List<GambleState.Bet> refunds = st.showRefunds ? st.refunds() : List.of();

        int waiting = Math.max(0, Math.min(queue.size() - 1, st.rows - 1));
        int more = Math.max(0, queue.size() - 1 - waiting);
        int refundRows = Math.min(refunds.size(), 3);
        int h = 64 + waiting * 11 + (more > 0 ? 10 : 0) + 4
                + (refunds.isEmpty() ? 0 : 16 + refundRows * 10 + (refunds.size() > refundRows ? 10 : 0))
                + 26;

        int vw = (int) (g.guiWidth() / scale), vh = (int) (g.guiHeight() / scale);
        int x = st.hud == GambleState.HudSpot.LEFT ? 6 : vw - W - 6;
        int y = 6 + Math.max(0, vh - h - 12) * st.heightPct / 100;
        int gx = x + W - PAD - GEAR, gy = y + 4;
        gearRect = new float[]{gx * scale, gy * scale, GEAR * scale};
        double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        draw(g, font, st, x, y, h, alpha, queue, refunds, waiting, more, refundRows, chatOpen);
        Style.button(g, font, gx, gy, GEAR, GEAR, Style.ICON_GEAR, chatOpen ? Colors.BTN_GO : Colors.BTN, true,
                chatOpen && gearContains(mx, my));
        g.pose().popMatrix();
    }

    public static boolean gearContains(double mx, double my) {
        float[] r = gearRect;
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2];
    }

    private static void draw(GuiGraphicsExtractor g, Font font, GambleState st, int x, int y, int h, int alpha,
                             List<GambleState.Bet> queue, List<GambleState.Bet> refunds, int waiting, int more, int refundRows,
                             boolean chatOpen) {
        int x0 = x + PAD, cw = W - 2 * PAD;
        Style.panel(g, x, y, W, h, alpha);

        String pill = !st.tracking ? "PAUSED" : queue.size() + " IN";
        int pillColor = !st.tracking ? Colors.RED : queue.isEmpty() ? Colors.MUTED : Colors.GREEN;
        int pillX = x0 + cw - GEAR - 3 - (Txt.width(font, pill) + 6);
        Txt.draw(g, font, Txt.cut(font, Style.title(), pillX - x0 - 4), x0, y + 6, Colors.TITLE, Style.shadow);
        Style.pill(g, font, pillX, y + 5, pill, pillColor);
        Style.divider(g, x0, y + 18, cw, alpha);

        int ty = y + 22;
        Txt.draw(g, font, "Bets", x0, ty, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, st.rangeText(), x0 + Txt.width(font, "Bets") + 5, ty, Colors.GOLD, Style.shadow);
        ty += 12;

        Style.highlight(g, x0, ty, cw, 27, alpha);
        if (queue.isEmpty()) {
            Txt.center(g, font, "Waiting for bets...", x0 + cw / 2, ty + 10, Colors.MUTED);
        } else {
            GambleState.Bet first = queue.get(0);
            String amount = "paid " + PaymentParser.money(first.amount());
            boolean paid = st.isPaid(first);
            String win = (paid ? "PAID " : "win ") + PaymentParser.money(st.payout(first));
            Txt.draw(g, font, "UP NEXT", x0 + 6, ty + 4, Colors.GREEN, Style.shadow);
            if (st.testTagged(first)) {
                Txt.draw(g, font, "TEST", x0 + 10 + Txt.width(font, "UP NEXT"), ty + 4, Colors.AQUA, Style.shadow);
            }
            Txt.draw(g, font, amount, x0 + cw - 6 - Txt.width(font, amount), ty + 4, Colors.GREEN, Style.shadow);
            Txt.draw(g, font, win, x0 + cw - 6 - Txt.width(font, win), ty + 15, paid ? Colors.GREEN : Colors.GOLD, Style.shadow);
            big(g, font, first.name(), x0 + 6, ty + 14, Colors.TEXT, cw - 16 - Txt.width(font, win), 1.5F);
        }
        ty += 30;

        for (int i = 1; i <= waiting; i++) {
            GambleState.Bet b = queue.get(i);
            String amount = PaymentParser.money(b.amount());
            Txt.draw(g, font, (i + 1) + ".", x0 + 2, ty, Colors.MUTED, Style.shadow);
            String nm = Txt.cut(font, b.name(), cw - 24 - Txt.width(font, amount) - (st.testTagged(b) ? 26 : 0));
            Txt.draw(g, font, nm, x0 + 16, ty, Colors.TEXT, Style.shadow);
            if (st.testTagged(b)) {
                Txt.draw(g, font, "TEST", x0 + 20 + Txt.width(font, nm), ty, Colors.AQUA, Style.shadow);
            }
            Txt.draw(g, font, amount, x0 + cw - 2 - Txt.width(font, amount), ty, Colors.AQUA, Style.shadow);
            ty += 11;
        }
        if (more > 0) {
            Txt.draw(g, font, "+" + more + " more waiting", x0 + 16, ty, Colors.MUTED, Style.shadow);
            ty += 10;
        }
        ty += 4;

        if (!refunds.isEmpty()) {
            Style.divider(g, x0, ty, cw, alpha);
            ty += 4;
            Style.pill(g, font, x0, ty, "REFUND", Colors.RED);
            Txt.draw(g, font, "outside range", x0 + Txt.width(font, "REFUND") + 10, ty + 1, Colors.MUTED, Style.shadow);
            ty += 12;
            for (int i = 0; i < refundRows; i++) {
                GambleState.Bet b = refunds.get(i);
                String amount = PaymentParser.money(b.amount()) + (b.underMin() ? " low" : " high");
                Txt.draw(g, font, Txt.cut(font, b.name(), cw - 8 - Txt.width(font, amount)), x0 + 2, ty, Colors.RED, Style.shadow);
                Txt.draw(g, font, amount, x0 + cw - 2 - Txt.width(font, amount), ty, Colors.RED, Style.shadow);
                ty += 10;
            }
            if (refunds.size() > refundRows) {
                Txt.draw(g, font, "+" + (refunds.size() - refundRows) + " more", x0 + 2, ty, Colors.MUTED, Style.shadow);
                ty += 10;
            }
        }

        Style.divider(g, x0, ty, cw, alpha);
        String toast = st.toast();
        String footer;
        int color = Colors.MUTED;
        if (chatOpen) {
            footer = "Click the gear to open the panel";
            color = Colors.YELLOW;
        } else if (toast != null) {
            footer = toast;
            color = st.toastColor();
        } else if (st.nextKey >= 0) {
            footer = GambleState.keyName(st.nextKey) + ": next  " + GambleState.keyName(st.openKey) + ": hide  "
                    + GambleState.keyName(st.settingsKey) + ": panel";
        } else {
            footer = GambleState.keyName(st.openKey) + ": hide  |  " + GambleState.keyName(st.settingsKey) + ": panel";
        }
        Txt.draw(g, font, Txt.cut(font, footer, cw), x0, ty + 4, color, Style.shadow);
        Txt.draw(g, font, "discord.gg/B64z86e9ZY", x0, ty + 14, Colors.ACCENT_B, Style.shadow);
    }

    // text up to maxScale, shrunk to fit maxWidth but never below normal size
    static void big(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int maxWidth, float maxScale) {
        int width = Math.max(1, Txt.width(font, text));
        float s = Math.max(1.0F, Math.min(maxScale, (float) Math.floor(maxWidth / (float) width * 10) / 10)); // 10% steps: few text sizes
        if (s <= 1.0F) {
            Txt.draw(g, font, Txt.cut(font, text, maxWidth), x, y, color, Style.shadow);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y - (s - 1.0F) * 4);
        g.pose().scale(s, s);
        Txt.draw(g, font, text, 0, 0, color, Style.shadow);
        g.pose().popMatrix();
    }
}
