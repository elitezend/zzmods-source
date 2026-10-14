package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// The small welcome card shown the first time (every edition) (after Auction Overlay's tour, if both are installed)
public class GambleTutorialScreen extends Screen {
    private static final int W = 240, H = 122, PAD = 12;
    private static final String[] POINTS = {"A queue of everyone who paid you", "Your bet range and refunds",
            Edition.PAYOUTS ? "Winner's pay command copied for you" : "Next player in one key press", "Keys so you never need the panel"};
    private float scale = 1;
    private int px, py;

    public GambleTutorialScreen() {
        super(Component.literal("Gamble Tracker tour"));
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
        Txt.draw(g, font, "WELCOME", px + PAD, py + PAD, Colors.ACCENT_B, Style.shadow);
        Txt.draw(g, font, "Gamble Tracker " + Edition.NAME, px + PAD, py + PAD + 11, Colors.TITLE, Style.shadow);
        int y = py + PAD + 27;
        for (String p : POINTS) {
            if (Style.look() == Look.SLEEK) {
                Smooth.circle(g, px + PAD + 1.5F, y + 3.5F, 2, Colors.ACCENT_A);
            } else {
                g.fill(px + PAD, y + 2, px + PAD + 3, y + 5, Colors.ACCENT_A);
            }
            Txt.draw(g, font, Txt.cut(font, p, W - 2 * PAD - 10), px + PAD + 9, y, Colors.TEXT, Style.shadow);
            y += 11;
        }
        int by = py + H - PAD - 18, half = (W - 2 * PAD - 4) / 2;
        Style.button(g, font, px + PAD, by, half + 20, 18, "Start the tour", Colors.BTN_GO, true, inside(mx, my, px + PAD, by, half + 20, 18));
        Style.button(g, font, px + PAD + half + 24, by, half - 20, 18, "Skip", Colors.BTN, true, inside(mx, my, px + PAD + half + 24, by, half - 20, 18));
        g.pose().popMatrix();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) (event.x() / scale), my = (int) (event.y() / scale);
        int by = py + H - PAD - 18, half = (W - 2 * PAD - 4) / 2;
        if (event.button() == 0 && inside(mx, my, px + PAD, by, half + 20, 18)) {
            GambleState.INSTANCE.click();
            onClose();
            GambleTour.start();
        } else if (event.button() == 0 && inside(mx, my, px + PAD + half + 24, by, half - 20, 18)) {
            GambleState.INSTANCE.click();
            onClose();
            GambleTour.skipButLink(); // the account still has to be linked
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            onClose();
            GambleTour.start();
            return true;
        }
        if (event.isEscape()) {
            onClose();
            GambleTour.skipButLink(); // the account still has to be linked
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
