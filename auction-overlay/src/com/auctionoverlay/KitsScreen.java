package com.auctionoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Presets: saved custom lots ("Neth kit", "Shulker of god apples"...) to pick in one click. Saved from the Custom lot tab
// (name the lot, add items, Save). Clicking one loads it into a custom lot ready to start.
public class KitsScreen extends Screen {
    private static final int W = 300, PAD = 12, CARD_H = 38, COLS = 2;

    private final List<int[]> hits = new ArrayList<>(); // x, y, w, h, kit index, action (0 load, 1 delete)
    private float scale = 1;
    private int px, py, h, first;
    private int confirmDelete = -1;

    public KitsScreen() {
        super(Component.literal("Presets"));
    }

    private List<AuctionState.Preset> kits() {
        return AuctionState.INSTANCE.config.presets;
    }

    private void layout() {
        h = Math.min(250, Math.max(140, height - 16));
        scale = Math.max(0.5F, Math.min(1.0F, Math.min((width - 8) / (float) W, (height - 8) / (float) h)));
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - h) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        hits.clear();
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, h, 248);
        Txt.draw(g, font, "PRESETS", px + PAD, py + PAD, Tour.glow(), Style.shadow);
        String hint = "Click one to auction it  ·  Esc to go back";
        Txt.draw(g, font, Txt.cut(font, hint, W - PAD * 2 - 40), px + W - PAD - Math.min(Txt.width(font, hint), W - PAD * 2 - 40), py + PAD, Colors.MUTED, Style.shadow);
        List<AuctionState.Preset> list = kits();
        int top = py + PAD + 16, bottom = py + h - PAD;
        if (list.isEmpty()) {
            Txt.center(g, font, "No presets saved yet.", px + W / 2, top + 30, Colors.MUTED);
            Txt.center(g, font, "Custom lot tab: name it, add items, press Save.", px + W / 2, top + 42, Colors.MUTED);
        }
        int cw = (W - PAD * 2 - 6) / COLS;
        int rowsFit = Math.max(1, (bottom - top) / (CARD_H + 6));
        first = Math.max(0, Math.min(first, Math.max(0, (list.size() + COLS - 1) / COLS - rowsFit)));
        for (int i = first * COLS; i < list.size(); i++) {
            int row = i / COLS - first, col = i % COLS;
            if (row >= rowsFit) break;
            AuctionState.Preset p = list.get(i);
            int x = px + PAD + col * (cw + 6), y = top + row * (CARD_H + 6);
            boolean hover = mx >= x && mx < x + cw && my >= y && my < y + CARD_H;
            if (hover) Style.highlight(g, x, y, cw, CARD_H, 255);
            else Style.box(g, x, y, cw, CARD_H);
            String logo = p.icon != null && !p.icon.isEmpty() ? p.icon : p.items.isEmpty() ? "" : p.items.get(0).id;
            if (!logo.isEmpty()) Lots.draw(g, logo, x + 5, y + 5);
            Txt.draw(g, font, Txt.cut(font, p.name, cw - 40), x + 25, y + 5, Colors.ITEM_NAME, Style.shadow);
            int n = 0;
            for (AuctionState.LotItem li : p.items) n += Math.max(1, li.count);
            Txt.draw(g, font, p.items.size() + (p.items.size() == 1 ? " item" : " items") + " · " + n + " total", x + 25, y + 15, Colors.MUTED, Style.shadow);
            // a row of the first items, small
            int ix = x + 5;
            for (int k = 0; k < p.items.size() && ix + 10 < x + cw - 4; k++) {
                g.pose().pushMatrix();
                g.pose().translate(ix, y + 26);
                g.pose().scale(0.6F, 0.6F);
                Lots.draw(g, p.items.get(k).id, 0, 0);
                g.pose().popMatrix();
                ix += 11;
            }
            hits.add(new int[]{x, y, cw - 16, CARD_H, i, 0});
            String del = confirmDelete == i ? "Sure?" : "x";
            int dw = confirmDelete == i ? 32 : 12;
            Style.button(g, font, x + cw - dw - 3, y + 3, dw, 12, del, Colors.BTN, true, mx >= x + cw - dw - 3 && mx < x + cw - 3 && my >= y + 3 && my < y + 15);
            hits.add(new int[]{x + cw - dw - 3, y + 3, dw, 12, i, 1});
        }
        if (first > 0) Txt.center(g, font, "▲", px + W / 2, top - 9, Colors.MUTED);
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() / scale, my = event.y() / scale;
        if (event.button() != 0) return true;
        // the delete buttons are added after the cards, so check them first
        for (int j = hits.size() - 1; j >= 0; j--) {
            int[] b = hits.get(j);
            if (mx < b[0] || mx >= b[0] + b[2] || my < b[1] || my >= b[1] + b[3]) continue;
            AuctionState st = AuctionState.INSTANCE;
            AuctionState.Preset p = kits().get(b[4]);
            AuctionState.click();
            if (b[5] == 1) {
                if (confirmDelete == b[4]) {
                    st.deletePreset(p);
                    confirmDelete = -1;
                } else {
                    confirmDelete = b[4];
                }
                return true;
            }
            st.loadPreset(p);
            st.config.custom = true;
            st.config.ftpTab = false;
            st.config.gwTab = false;
            st.save();
            AuctionScreen.openSetup();
            Minecraft.getInstance().gui.setScreen(new AuctionScreen());
            return true;
        }
        confirmDelete = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double scrollX, double scrollY) {
        first = Math.max(0, first - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            Minecraft.getInstance().gui.setScreen(new AuctionScreen());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
