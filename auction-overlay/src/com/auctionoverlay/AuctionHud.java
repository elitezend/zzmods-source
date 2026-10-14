package com.auctionoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// the small card shown in game while an auction runs and the panel is closed
public final class AuctionHud {
    private static final int W = 160;

    private AuctionHud() {}

    public static boolean visible() {
        AuctionState st = AuctionState.INSTANCE;
        if (Minecraft.getInstance().gui.screen() instanceof AuctionScreen || !st.hasAuction || !st.config.showCard) {
            return false;
        }
        return st.live || System.currentTimeMillis() - st.finishedAtMs <= st.config.resultSecs * 1000L;
    }

    private static float cardScale(AuctionState st) {
        int pct = st.config.cardSize;
        return pct <= 0 ? st.scale() : Math.max(0.5F, Math.min(3.0F, pct / 100.0F));
    }

    public static void render(GuiGraphicsExtractor g) {
        // the tour's cards (link your account, pick your key, tap your key...) in every edition - the tour links an
        // unlinked copy first, so they win over the "locked" card and the update card
        if (Tour.linking() || Tour.active()) {
            Tour.renderHud(g);
            if (Online.off() || Tour.linking()) return;
        }
        if (Online.off()) {
            if (Minecraft.getInstance().gui.screen() == null && Minecraft.getInstance().level != null) {
                String key = AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey);
                if (Online.blocked()) Online.renderBlocked(g, key);
                else Online.renderLocked(g, key, 6);
            }
            Online.renderToast(g); // "unlocked" / "linked" notes
            return;
        }
        if (!Tour.active()) {
            Online.renderToast(g);
        }
        if (!visible()) {
            return;
        }
        AuctionState st = AuctionState.INSTANCE;
        AuctionState.Config c = st.config;
        Font font = Minecraft.getInstance().font;
        float scale = cardScale(st);
        int alpha = c.opacity * 255 / 100;

        boolean contents = st.showContents() && c.cardContents;
        int worthStyle = c.worthOnCard ? st.worthStyle() : 3;
        String toast = st.sizeToast();
        boolean copied = !st.live && st.copiedTpa != null;
        boolean footer = toast != null || copied || c.cardHint;
        int lab = c.cardLabels ? 0 : 9; // no TIME LEFT / TOP BID labels: the numbers move up
        int h = st.instant() ? 52 + (footer ? 12 : 0) : 28 + (contents ? 10 : 0) + (worthStyle == 0 ? 18 : 0) + 44 - lab + (footer ? 12 : 0);

        int vw = (int) (g.guiWidth() / scale);
        int vh = (int) (g.guiHeight() / scale);
        int x, y;
        switch (Math.floorMod(c.cardSpot, AuctionState.CARD_SPOTS.length)) {
            case 1 -> { x = 4; y = 4; }
            case 2 -> { x = vw - W - 4; y = 4; }
            case 3 -> { x = 4; y = vh - h - 4; }
            case 4 -> { x = vw - W - 4; y = vh - h - 4; }
            default -> {
                x = (int) ((c.panelX < 0 ? 10 : c.panelX) / scale);
                y = (int) ((c.panelY < 0 ? 10 : c.panelY) / scale);
            }
        }
        x = Math.max(0, Math.min(vw - W, x));
        y = Math.max(0, Math.min(vh - h, y));

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, x, y, W, h, alpha);
        if (st.gw) {
            gwCard(g, font, st, x, y, toast, copied);
            g.pose().popMatrix();
            return;
        }
        if (st.ftp) {
            ftpCard(g, font, st, x, y, alpha, toast, copied);
            g.pose().popMatrix();
            return;
        }
        if (st.dutch) {
            dutchCard(g, font, st, x, y, toast, copied);
            g.pose().popMatrix();
            return;
        }
        Style.slot(g, x + 5, y + 5, 20, alpha, false, false);
        Lots.draw(g, st.icon, x + 7, y + 7);
        Txt.draw(g, font, Txt.cut(font, st.displayName(), W - 36), x + 30, y + 6, Colors.ITEM_NAME, Style.shadow);
        if (st.live) {
            boolean on = AuctionState.blink();
            String liveText = (on ? "●" : "○") + (worthStyle == 1 || worthStyle == 2 ? " LIVE" : " LIVE AUCTION");
            Txt.draw(g, font, liveText, x + 30, y + 16, Colors.RED, Style.shadow);
            int after = x + 30 + Txt.width(font, liveText);
            if (worthStyle == 1) {
                worthBadge(g, font, st, after + 3, y + 15, x + W - 4 - after - 3);
            } else if (worthStyle == 2) {
                Txt.draw(g, font, Txt.cut(font, "  " + st.worthLabel().toLowerCase() + " " + st.worth(), x + W - 4 - after),
                        after, y + 16, st.worthColor(), Style.shadow);
            }
        } else {
            Txt.draw(g, font, Txt.cut(font, st.result, W - 36), x + 30, y + 16, st.resultColor, Style.shadow);
        }

        int ty = y + 28;
        if (contents) {
            Txt.draw(g, font, Txt.cut(font, st.contents(), W - 10), x + 5, ty, Colors.AQUA, Style.shadow);
            ty += 10;
        }
        if (worthStyle == 0) {
            worthBanner(g, font, st.worthLabel(), st.worth(), st.worthColor(), x + 5, ty, W - 10, alpha);
            ty += 18;
        }

        if (c.cardLabels) {
            Txt.draw(g, font, "TIME LEFT", x + 5, ty + 3, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "TOP BID", x + 82, ty + 3, Colors.MUTED, Style.shadow);
        }
        ty -= lab;
        int secs = st.live ? st.remainingSeconds() : 0;
        int timerColor = !st.live ? Colors.MUTED
                : secs > c.warnSecs ? Colors.TEXT
                : AuctionState.blink() ? Colors.RED : Colors.YELLOW;
        big(g, font, AuctionState.formatTime(secs), x + 5, ty + 13, timerColor, Integer.MAX_VALUE);
        AuctionState.Bid top = st.topBid();
        String topText = top == null ? "-" : st.moneyNumber(top.amount());
        int topW = big(g, font, topText, x + 82, ty + 13, top == null ? Colors.MUTED : Colors.GREEN, W - 86);
        int unitX = x + 82 + topW + 3;
        if (top != null && !st.payUnit.isEmpty() && unitX < x + W - 10) {
            Txt.draw(g, font, Txt.cut(font, st.payUnit, x + W - 4 - unitX), unitX, ty + 22, Colors.GREEN, Style.shadow);
        }
        if (c.cardBidder) {
            Txt.draw(g, font, Txt.cut(font, top == null ? "no bids yet" : "by " + top.name(), W - 86), x + 82, ty + 32, Colors.AQUA, Style.shadow);
        }
        if (st.live && c.cardNext) {
            Txt.draw(g, font, nextText(font, st, 74), x + 5, ty + 32, Colors.MUTED, Style.shadow);
        }
        ty += 44;

        if (toast != null) {
            Txt.draw(g, font, Txt.cut(font, toast, W - 10), x + 5, ty, Colors.YELLOW, Style.shadow);
        } else if (copied) {
            Txt.draw(g, font, Txt.cut(font, "Copied: " + st.copiedTpa, W - 10), x + 5, ty, Colors.GOLD, Style.shadow);
        } else if (c.cardHint) {
            Txt.draw(g, font, AuctionState.keyName(c.toggleKey) + ": open panel", x + 5, ty, Colors.MUTED, Style.shadow);
        }
        g.pose().popMatrix();
    }

    private static void ftpCard(GuiGraphicsExtractor g, Font font, AuctionState st, int x, int y, int alpha, String toast, boolean copied) {
        boolean blink = AuctionState.blink();
        String prize = "win " + st.ftpPrize;
        Txt.draw(g, font, "FIRST TO PAY", x + 5, y + 6, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, prize, W - 80), x + W - 5 - Txt.width(font, Txt.cut(font, prize, W - 80)), y + 6, Colors.GREEN, Style.shadow);
        String need = (st.ftpExact ? "Pay exactly " : "Pay ") + st.money(st.ftpNeed);
        if (st.live && !st.ftpGo) {
            int secs = st.remainingSeconds();
            Txt.draw(g, font, Txt.cut(font, need + "  -  starts in", W - 10), x + 5, y + 18, Colors.TEXT, Style.shadow);
            int color = secs > st.config.warnSecs ? Colors.TEXT : blink ? Colors.RED : Colors.YELLOW;
            big(g, font, AuctionState.formatTime(secs), x + 5, y + 29, color, W - 10);
        } else if (st.live) {
            Txt.draw(g, font, Txt.cut(font, need + " now!", W - 10), x + 5, y + 18, Colors.TEXT, Style.shadow);
            Style.highlight(g, x + 4, y + 28, W - 8, 20, alpha);
            big(g, font, "GO! PAY NOW", x + 8, y + 30, blink ? Colors.GREEN : Colors.YELLOW, W - 16);
        } else if (st.winnerName() != null) {
            Txt.draw(g, font, "WINNER", x + 5, y + 18, Colors.MUTED, Style.shadow);
            big(g, font, st.leader.name(), x + 5, y + 29, Colors.GOLD, W - 10);
        } else {
            big(g, font, st.result, x + 5, y + 25, st.resultColor, W - 10);
        }
        int fy = y + 52;
        if (toast != null) {
            Txt.draw(g, font, Txt.cut(font, toast, W - 10), x + 5, fy, Colors.YELLOW, Style.shadow);
        } else if (copied) {
            Txt.draw(g, font, Txt.cut(font, "Copied: " + st.copiedTpa, W - 10), x + 5, fy, Colors.GOLD, Style.shadow);
        } else if (st.config.cardHint) {
            Txt.draw(g, font, AuctionState.keyName(st.config.toggleKey) + ": open panel", x + 5, fy, Colors.MUTED, Style.shadow);
        }
    }

    private static void gwCard(GuiGraphicsExtractor g, Font font, AuctionState st, int x, int y, String toast, boolean copied) {
        boolean blink = AuctionState.blink();
        String prize = "win " + st.ftpPrize;
        Txt.draw(g, font, "GIVEAWAY", x + 5, y + 6, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, prize, W - 70), x + W - 5 - Txt.width(font, Txt.cut(font, prize, W - 70)), y + 6, Colors.GREEN, Style.shadow);
        if (st.live) {
            int secs = st.remainingSeconds();
            Txt.draw(g, font, Txt.cut(font, "Pay " + st.money(st.gwEntry) + " - " + st.gwEntries() + " entries - draw in", W - 10), x + 5, y + 18, Colors.TEXT, Style.shadow);
            int color = secs > st.config.warnSecs ? Colors.TEXT : blink ? Colors.RED : Colors.YELLOW;
            big(g, font, AuctionState.formatTime(secs), x + 5, y + 29, color, W - 10);
        } else if (st.winnerName() != null) {
            Txt.draw(g, font, "WINNER", x + 5, y + 18, Colors.MUTED, Style.shadow);
            big(g, font, st.leader.name(), x + 5, y + 29, Colors.GOLD, W - 10);
        } else {
            big(g, font, st.result, x + 5, y + 25, st.resultColor, W - 10);
        }
        int fy = y + 52;
        if (toast != null) {
            Txt.draw(g, font, Txt.cut(font, toast, W - 10), x + 5, fy, Colors.YELLOW, Style.shadow);
        } else if (copied) {
            Txt.draw(g, font, Txt.cut(font, "Copied: " + st.copiedTpa, W - 10), x + 5, fy, Colors.GOLD, Style.shadow);
        } else if (st.config.cardHint) {
            Txt.draw(g, font, AuctionState.keyName(st.config.toggleKey) + ": open panel", x + 5, fy, Colors.MUTED, Style.shadow);
        }
    }

    private static void dutchCard(GuiGraphicsExtractor g, Font font, AuctionState st, int x, int y, String toast, boolean copied) {
        boolean blink = AuctionState.blink();
        Style.slot(g, x + 5, y + 5, 20, 255, false, false);
        Lots.draw(g, st.icon, x + 7, y + 7);
        Txt.draw(g, font, Txt.cut(font, st.displayName(), W - 36), x + 30, y + 6, Colors.ITEM_NAME, Style.shadow);
        if (st.live) {
            Txt.draw(g, font, (blink ? "●" : "○") + " DUTCH - next drop " + st.remainingSeconds() + "s", x + 30, y + 16, Colors.RED, Style.shadow);
            big(g, font, st.money(st.dutchPrice), x + 5, y + 29, Colors.GREEN, W - 10);
        } else {
            Txt.draw(g, font, Txt.cut(font, st.result, W - 36), x + 30, y + 16, st.resultColor, Style.shadow);
            big(g, font, st.leader != null ? st.money(st.leader.amount()) : "-", x + 5, y + 29, Colors.GOLD, W - 10);
        }
        int fy = y + 52;
        if (toast != null) {
            Txt.draw(g, font, Txt.cut(font, toast, W - 10), x + 5, fy, Colors.YELLOW, Style.shadow);
        } else if (copied) {
            Txt.draw(g, font, Txt.cut(font, "Copied: " + st.copiedTpa, W - 10), x + 5, fy, Colors.GOLD, Style.shadow);
        } else if (st.config.cardHint) {
            Txt.draw(g, font, AuctionState.keyName(st.config.toggleKey) + ": open panel", x + 5, fy, Colors.MUTED, Style.shadow);
        }
    }

    // full-width strip: small label, then the worth in large text
    static void worthBanner(GuiGraphicsExtractor g, Font font, String label, String worth, int color, int x, int y, int w, int alpha) {
        Style.highlight(g, x, y, w, 16, alpha);
        String tag = label.toUpperCase();
        Txt.draw(g, font, tag, x + 6, y + 4, color, Style.shadow);
        int ax = x + 12 + Txt.width(font, tag);
        int room = x + w - 6 - ax;
        float s = Math.max(1.0F, Math.min(1.5F, room / (float) Math.max(1, Txt.width(font, worth))));
        g.pose().pushMatrix();
        g.pose().translate(ax, y + 4 - (s - 1.0F) * 5);
        g.pose().scale(s, s);
        Txt.draw(g, font, s <= 1.0F ? Txt.cut(font, worth, room) : worth, 0, 0, color, Style.shadow);
        g.pose().popMatrix();
    }

    static void worthBadge(GuiGraphicsExtractor g, Font font, AuctionState st, int x, int y, int maxWidth) {
        String text = Txt.cut(font, st.worthLabel().toUpperCase() + " " + st.worth(), Math.max(0, maxWidth - 6));
        if (!text.isEmpty()) {
            Style.pill(g, font, x, y, text, st.worthColor());
        }
    }

    // settings preview with a made-up amount when no worth is set yet
    static void worthPreview(GuiGraphicsExtractor g, Font font, AuctionState st, int x, int y, int w) {
        String worth = st.config.worthText == null || st.config.worthText.isBlank() ? "$5m" : st.config.worthText.trim();
        int color = st.worthColor();
        int alpha = st.config.opacity * 255 / 100;
        switch (Math.floorMod(st.config.worthStyle, AuctionState.WORTH_STYLES.length)) {
            case 0 -> worthBanner(g, font, st.worthLabel(), worth, color, x, y, w, alpha);
            case 1 -> {
                Txt.draw(g, font, "● LIVE", x, y + 4, Colors.RED, Style.shadow);
                Style.pill(g, font, x + Txt.width(font, "● LIVE") + 4, y + 3, st.worthLabel().toUpperCase() + " " + worth, color);
            }
            case 2 -> {
                Txt.draw(g, font, "● LIVE", x, y + 4, Colors.RED, Style.shadow);
                Txt.draw(g, font, "  " + st.worthLabel().toLowerCase() + " " + worth, x + Txt.width(font, "● LIVE"), y + 4, color, Style.shadow);
            }
            default -> Txt.draw(g, font, "Worth isn't shown", x, y + 4, Colors.MUTED, Style.shadow);
        }
    }

    // "Next: $1,253,900+" or "Next: $1.25m+" when the exact one doesn't fit
    static String nextText(Font font, AuctionState st, int maxWidth) {
        String exact = "Next: " + st.payPrefix + AuctionState.full(st.nextMin()) + "+";
        if (Txt.width(font, exact) <= maxWidth) {
            return exact;
        }
        return Txt.cut(font, "Next: " + st.moneyNumber(st.nextMin()) + "+", maxWidth);
    }

    // double-size text, shrunk (bottom-aligned) to fit maxWidth; returns the drawn width
    static int big(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int maxWidth) {
        int width = Math.max(1, Txt.width(font, text));
        float s = Math.max(1.0F, Math.min(2.0F, (float) Math.floor(maxWidth / (float) width * 10) / 10)); // 10% steps
        g.pose().pushMatrix();
        g.pose().translate(x, y + (2.0F - s) * 8);
        g.pose().scale(s, s);
        Txt.draw(g, font, text, 0, 0, color, Style.shadow);
        g.pose().popMatrix();
        return Math.round(width * s);
    }
}
