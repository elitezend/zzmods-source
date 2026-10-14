package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// Premium: the welcome card shown the first time you join a world. A small auction plays on the left so you can see
// what the mod does; Start tour begins the hands-on Tour, Skip never shows it again.
public class TutorialScreen extends Screen {
    private static final int W = 300, H = 150, PAD = 12;
    private static final String[] BIDDERS = {"z4v0", ".PRO8201", "xaigngster", "Arimann", "z4v0"};
    private static final long[] AMOUNTS = {500_000, 1_200_000, 2_000_000, 2_600_000, 3_500_000};

    private final AuctionState st = AuctionState.INSTANCE;
    private float scale = 1;
    private int px, py;
    private final long opened = System.currentTimeMillis();

    public TutorialScreen() {
        super(Component.literal("Auction Overlay tour"));
    }

    private void layout() {
        scale = Style.popupScale(width, height, W, H, 0.55F, 0.62F); // not bigger than about half the screen
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - H) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, H, 248);
        demo(g, px + PAD, py + PAD);

        int tx = px + PAD + 128, tw = W - PAD * 2 - 128;
        Txt.draw(g, font, "WELCOME", tx, py + PAD, Colors.ACCENT_B, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, "Auction Overlay " + Edition.NAME, tw), tx, py + PAD + 11, Colors.TITLE, Style.shadow);
        int y = py + PAD + 27;
        String[] points = {"Players bid by paying you", "Timer, top bid & winner tracked",
                Edition.PRO ? "Refunds, queue, Dutch & more" : Edition.CUSTOM_LOTS ? "Custom lots + the winner's tpa copied" : "Any item, with a live timer",
                Online.isLinked() ? (Edition.PRO ? "OBS overlay + Discord posts" : "A small card while you play") : "First: link your zzmods account"};
        for (String p : points) {
            dot(g, tx + 2, y + 3);
            Txt.draw(g, font, Txt.cut(font, p, tw - 10), tx + 9, y, Colors.TEXT, Style.shadow);
            y += 11;
        }
        for (String l : Tour.wrap(font, "A 1-minute hands-on tour with a test auction (not saved or posted).", tw)) {
            Txt.draw(g, font, l, tx, y + 4, Colors.MUTED, Style.shadow);
            y += 10;
        }
        int by = py + H - PAD - 18;
        Style.button(g, font, tx, by, 92, 18, "Start the tour", Colors.BTN_GO, true, inside(mx, my, tx, by, 92, 18));
        Style.button(g, font, tx + 96, by, tw - 96, 18, "Skip", Colors.BTN, true, inside(mx, my, tx + 96, by, tw - 96, 18));
        g.pose().popMatrix();
    }

    private void dot(GuiGraphicsExtractor g, int x, int y) {
        if (Style.look() == Look.SLEEK) {
            Smooth.circle(g, x + 1.5F, y + 1.5F, 2, Colors.ACCENT_A);
        } else {
            g.fill(x, y, x + 3, y + 3, Colors.ACCENT_A);
        }
    }

    // a tiny auction that loops every 10 seconds: bids come in, the timer runs down, it sells
    private void demo(GuiGraphicsExtractor g, int x, int y) {
        int w = 118, h = H - PAD * 2;
        Style.box(g, x, y, w, h);
        long t = (System.currentTimeMillis() - opened) % 10_000;
        int bidCount = (int) Math.min(BIDDERS.length, t / 1500);
        boolean sold = t >= 8000;
        Style.itemFrame(g, x + 6, y + 6, 22);
        g.pose().pushMatrix();
        g.pose().translate(x + 9, y + 9);
        Lots.draw(g, "netherite_ingot", 0, 0);
        g.pose().popMatrix();
        Txt.draw(g, font, "Netherite x3", x + 32, y + 7, Colors.ITEM_NAME, Style.shadow);
        boolean blink = (t / 500) % 2 == 0;
        Txt.draw(g, font, sold ? "SOLD" : (blink ? "●" : "○") + " LIVE", x + 32, y + 18, sold ? Colors.GOLD : Colors.RED, Style.shadow);

        int secs = sold ? 0 : (int) Math.max(0, 8 - t / 1000);
        Txt.draw(g, font, "TIME", x + 6, y + 34, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "TOP BID", x + 60, y + 34, Colors.MUTED, Style.shadow);
        AuctionHud.big(g, font, AuctionState.formatTime(secs), x + 6, y + 43, secs <= 3 && !sold ? (blink ? Colors.RED : Colors.YELLOW) : Colors.TEXT, 50);
        String top = bidCount == 0 ? "-" : "$" + AuctionState.compact(AMOUNTS[bidCount - 1]);
        long since = bidCount == 0 ? 9999 : t - bidCount * 1500L;
        int topColor = since < 300 ? Colors.YELLOW : Colors.GREEN;
        AuctionHud.big(g, font, top, x + 60, y + 43, bidCount == 0 ? Colors.MUTED : topColor, w - 66);

        int ry = y + 66;
        for (int i = 0; i < 3 && i < bidCount; i++) {
            int idx = bidCount - 1 - i;
            int c = i == 0 ? Colors.GREEN : Colors.MUTED;
            String amt = "$" + AuctionState.compact(AMOUNTS[idx]);
            Txt.draw(g, font, Txt.cut(font, BIDDERS[idx], w - 50), x + 6, ry + i * 10, c, Style.shadow);
            Txt.draw(g, font, amt, x + w - 6 - Txt.width(font, amt), ry + i * 10, c, Style.shadow);
        }
        if (sold) {
            Style.highlight(g, x + 4, y + h - 22, w - 8, 18, 255);
            Txt.center(g, font, "SOLD to z4v0!", x + w / 2, y + h - 17, Colors.GOLD);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() / scale, my = event.y() / scale;
        int tx = px + PAD + 128, tw = W - PAD * 2 - 128, by = py + H - PAD - 18;
        if (event.button() == 0 && inside((int) mx, (int) my, tx, by, 92, 18)) {
            AuctionState.click();
            startTour();
            return true;
        }
        if (event.button() == 0 && inside((int) mx, (int) my, tx + 96, by, tw - 96, 18)) {
            AuctionState.click();
            skip();
            return true;
        }
        return true;
    }

    private void startTour() {
        onClose();
        Tour.start(); // links the account first if needed, then: pick the key that opens the panel (F7 always works too)
    }

    // skipping the tour still needs the account linked
    private void skip() {
        onClose();
        Tour.skipButLink();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            startTour();
            return true;
        }
        if (event.isEscape()) {
            skip();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
