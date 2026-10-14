package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// Shown instead of the panel while a paid copy is locked: it only works when linked to the zzmods account that bought
// that edition (so a shared jar is useless). Explains why and has the button to fix it.
public class LicenseScreen extends Screen {
    private static final int W = 270, PAD = 12;
    private float scale = 1;
    private int px, py, h;

    public LicenseScreen() {
        super(Component.literal("Auction Overlay license"));
    }

    private String edition() {
        return "Auction Overlay " + Edition.NAME;
    }

    private String title() {
        String r = Online.lockReason();
        if (r == null) return "UNLOCKED";
        return switch (r) {
            case "link" -> Online.waitingForLink() ? "WAITING FOR YOU TO CLICK LINK" : "LINK YOUR ACCOUNT";
            case "banned" -> "THIS ACCOUNT IS BANNED";
            case "buy" -> Edition.NAME.toUpperCase() + " ISN'T ON THIS ACCOUNT";
            case "offline" -> "COULDN'T CHECK YOUR LICENSE";
            default -> "CHECKING YOUR LICENSE...";
        };
    }

    private String text() {
        String r = Online.lockReason();
        if (r == null) return edition() + " is unlocked - have fun!";
        String email = AuctionState.INSTANCE.config.linkedEmail;
        return switch (r) {
            case "link" -> Online.waitingForLink()
                    ? "Sign in on the page that opened and click Link. This screen unlocks by itself a few seconds later."
                    : Online.paid() ? edition() + " only works when it's linked to the zzmods account you bought it with, so copies can't be shared. Press Link account, sign in on the website and click Link."
                    : edition() + " is free, but it only works while it's linked to a zzmods account. Press Link account, sign in (or make a free account) on the website and click Link.";
            case "banned" -> "The zzmods account this copy is linked to (" + email + ") has been banned, so " + edition() + " is switched off. If you think this is a mistake, contact zzmods support on our Discord.";
            case "buy" -> "This copy is linked to " + email + ", which doesn't own " + edition() + ". Buy it on that account, or link the account you bought it with.";
            case "offline" -> "We couldn't reach zzmods.com for a week. Connect to the internet and press Try again - it unlocks as soon as it can check.";
            default -> "Linked to " + email + " - checking that this account owns " + edition() + "...";
        };
    }

    private String[] buttons() {
        String r = Online.lockReason();
        if (r == null) return new String[]{"Open the panel", "Close"};
        return switch (r) {
            case "link" -> Online.waitingForLink() ? new String[]{"Open the page again", "Cancel"} : new String[]{"Link account", "Close"};
            case "buy" -> new String[]{"Buy " + Edition.NAME, "Link another account"};
            case "offline" -> new String[]{"Try again", "Close"};
            default -> new String[]{"Check again", "Close"};
        };
    }

    private void press(int which) {
        String r = Online.lockReason();
        AuctionState.click();
        if (r == null) {
            onClose();
            if (which == 0) minecraft.gui.setScreen(new AuctionScreen());
            return;
        }
        switch (r) {
            case "link" -> {
                if (which == 0) Online.startLink();
                else if (Online.waitingForLink()) Online.unlink();
                else onClose();
            }
            case "buy" -> {
                if (which == 0) Online.openBuy();
                else Online.relink();
            }
            default -> {
                if (which == 0) Online.checkNow();
                else onClose();
            }
        }
    }

    private void layout(int lines) {
        h = PAD * 2 + 26 + lines * 10 + 28;
        scale = Style.popupScale(width, height, W, h, 0.55F, 0.7F); // not bigger than about half the screen
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - h) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        List<String> lines = Tour.wrap(font, text(), W - PAD * 2);
        layout(lines.size());
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, h, 250);
        boolean ok = Online.lockReason() == null;
        Txt.draw(g, font, Txt.cut(font, title(), W - PAD * 2), px + PAD, py + PAD, ok ? Colors.GREEN : Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, edition(), px + PAD, py + PAD + 11, Colors.TITLE, Style.shadow);
        int y = py + PAD + 27;
        for (String l : lines) {
            Txt.draw(g, font, l, px + PAD, y, Colors.TEXT, Style.shadow);
            y += 10;
        }
        String[] b = buttons();
        int by = py + h - PAD - 18, bw = (W - PAD * 2 - 4) / 2;
        Style.button(g, font, px + PAD, by, bw, 18, b[0], Colors.BTN_GO, true, inside(mx, my, px + PAD, by, bw, 18));
        Style.button(g, font, px + PAD + bw + 4, by, bw, 18, b[1], Colors.BTN, true, inside(mx, my, px + PAD + bw + 4, by, bw, 18));
        g.pose().popMatrix();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        int mx = (int) (event.x() / scale), my = (int) (event.y() / scale);
        int by = py + h - PAD - 18, bw = (W - PAD * 2 - 4) / 2;
        if (inside(mx, my, px + PAD, by, bw, 18)) press(0);
        else if (inside(mx, my, px + PAD + bw + 4, by, bw, 18)) press(1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            press(0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
