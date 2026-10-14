package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// Shown instead of the panel when zzmods.com says this version has to be updated (staff "Required update" page):
// the mod stays off - no panel, keys or payment tracking - until the new version is installed.
public class UpdateRequiredScreen extends Screen {
    private static final int W = 260, PAD = 12;
    private float scale = 1;
    private int px, py, h;

    public UpdateRequiredScreen() {
        super(Component.literal("Auction Overlay update required"));
    }

    private List<String> lines() {
        String notes = Online.requiredNotes();
        String text = "This version (" + Online.version() + ") has been turned off. Install Auction Overlay "
                + Online.requiredVersion() + " to keep using it - Update now, or get it on zzmods.com." + (notes.isBlank() ? "" : " What's new: " + notes);
        return Tour.wrap(font, text, W - PAD * 2);
    }

    private void layout(int lineCount) {
        h = PAD * 2 + 24 + (lineCount + (Updater.message().isEmpty() ? 0 : 1)) * 10 + 28;
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
        List<String> lines = lines();
        layout(lines.size());
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, h, 250);
        Txt.draw(g, font, "UPDATE REQUIRED", px + PAD, py + PAD, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, "Auction Overlay " + Online.requiredVersion() + " is out", px + PAD, py + PAD + 11, Colors.TITLE, Style.shadow);
        int y = py + PAD + 26;
        if (!Updater.message().isEmpty()) {
            lines = new java.util.ArrayList<>(lines);
            lines.add(Updater.message());
        }
        for (String l : lines) {
            Txt.draw(g, font, l, px + PAD, y, Colors.TEXT, Style.shadow);
            y += 10;
        }
        int by = py + h - PAD - 18, bw = (W - PAD * 2 - 4) / 2;
        Updater.State us = Updater.state();
        String first = us == Updater.State.WORKING ? "Updating..." : us == Updater.State.READY ? "Restart Minecraft" : "Update now";
        Style.button(g, font, px + PAD, by, bw, 18, first, Colors.BTN_GO, us == Updater.State.IDLE || us == Updater.State.FAILED,
                inside(mx, my, px + PAD, by, bw, 18));
        Style.button(g, font, px + PAD + bw + 4, by, bw, 18, "Download page", Colors.BTN, true, inside(mx, my, px + PAD + bw + 4, by, bw, 18));
        g.pose().popMatrix();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) (event.x() / scale), my = (int) (event.y() / scale);
        int by = py + h - PAD - 18, bw = (W - PAD * 2 - 4) / 2;
        if (event.button() == 0 && inside(mx, my, px + PAD, by, bw, 18)) {
            AuctionState.click();
            Updater.start();
            return true;
        }
        if (event.button() == 0 && inside(mx, my, px + PAD + bw + 4, by, bw, 18)) {
            AuctionState.click();
            Online.openRequired();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            Updater.start();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
