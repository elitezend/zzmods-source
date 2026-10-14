package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// "Gamble Tracker 1.3.16 is out - update now?" Shown once per update notice when the panel is opened. Update now
// downloads and installs it (Updater); it takes over at the next start. Not now opens the panel as usual.
public class UpdatePromptScreen extends Screen {
    private static final int W = 260, PAD = 12;
    private static String shownFor = "";
    private final boolean thenPanel;
    private float scale = 1;
    private int px, py, h;

    public UpdatePromptScreen(boolean thenPanel) {
        super(Component.literal("Gamble Tracker update"));
        this.thenPanel = thenPanel;
    }

    // open the prompt instead of the panel? (once per notice, while there's something to install)
    static boolean wanted() {
        String v = GambleOnline.update();
        if (v == null || Updater.state() == Updater.State.READY || shownFor.equals(GambleOnline.noticeId())) return false;
        shownFor = GambleOnline.noticeId();
        return true;
    }

    private List<String> lines() {
        String notes = GambleOnline.notes();
        String text = Updater.state() == Updater.State.IDLE && Updater.message().isEmpty()
                ? "You have " + GambleOnline.version() + ". Update now installs it for you - it takes over the next time you start Minecraft."
                + (notes.isBlank() ? "" : " What's new: " + notes)
                : Updater.message();
        return GambleTour.wrap(font, text, W - PAD * 2);
    }

    private void layout(int lineCount) {
        h = PAD * 2 + 24 + lineCount * 10 + 28;
        scale = Style.popupScale(width, height, W, h, 0.55F, 0.7F); // not bigger than about half the screen
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - h) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x90000000);
    }

    private String[] labels() {
        return switch (Updater.state()) {
            case WORKING -> new String[]{"Updating...", "Hide"};
            case READY -> new String[]{"Got it", null};
            case FAILED -> new String[]{"Try again", "Download page"};
            default -> new String[]{"Update now", "Not now"};
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        List<String> lines = lines();
        layout(lines.size());
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, h, 250);
        Updater.State s = Updater.state();
        Txt.draw(g, font, s == Updater.State.READY ? "UPDATED" : s == Updater.State.FAILED ? "UPDATE FAILED" : "UPDATE AVAILABLE", px + PAD, py + PAD,
                s == Updater.State.FAILED ? Colors.RED : s == Updater.State.READY ? Colors.GREEN : GambleTour.glow(), Style.shadow);
        String v = GambleOnline.update();
        Txt.draw(g, font, "Gamble Tracker " + (v == null ? "" : v + " is out"), px + PAD, py + PAD + 11, Colors.TITLE, Style.shadow);
        int y = py + PAD + 26;
        for (String l : lines) {
            Txt.draw(g, font, l, px + PAD, y, s == Updater.State.FAILED ? Colors.RED : Colors.TEXT, Style.shadow);
            y += 10;
        }
        String[] b = labels();
        int by = py + h - PAD - 18, bw = (W - PAD * 2 - 4) / 2;
        if (b[1] == null) {
            Style.button(g, font, px + PAD, by, W - PAD * 2, 18, b[0], Colors.BTN_GO, true, inside(mx, my, px + PAD, by, W - PAD * 2, 18));
        } else {
            Style.button(g, font, px + PAD, by, bw, 18, b[0], Colors.BTN_GO, s != Updater.State.WORKING, inside(mx, my, px + PAD, by, bw, 18));
            Style.button(g, font, px + PAD + bw + 4, by, bw, 18, b[1], Colors.BTN, true, inside(mx, my, px + PAD + bw + 4, by, bw, 18));
        }
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
        String[] b = labels();
        boolean first = b[1] == null ? inside(mx, my, px + PAD, by, W - PAD * 2, 18) : inside(mx, my, px + PAD, by, bw, 18);
        boolean second = b[1] != null && inside(mx, my, px + PAD + bw + 4, by, bw, 18);
        if (first) {
            GambleState.INSTANCE.click();
            switch (Updater.state()) {
                case IDLE, FAILED -> Updater.start();
                case READY -> close();
                default -> {
                }
            }
        } else if (second) {
            GambleState.INSTANCE.click();
            if (Updater.state() == Updater.State.FAILED) GambleOnline.openRequired();
            close();
        }
        return true;
    }

    private void close() {
        onClose();
        if (thenPanel && Updater.state() != Updater.State.WORKING && Minecraft.getInstance().level != null && !GambleOnline.off()) {
            GambleMod.openPanel();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (Updater.state() == Updater.State.IDLE || Updater.state() == Updater.State.FAILED) Updater.start();
            else if (Updater.state() == Updater.State.READY) close();
            return true;
        }
        if (event.isEscape()) {
            close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
