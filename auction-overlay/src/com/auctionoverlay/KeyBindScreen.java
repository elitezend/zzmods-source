package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// "Which key should open the auction panel?" - the first thing after linking, whether they start the tour or skip it.
// Its own screen, so the pause menu (after linking in the browser) or a stray click can't hide it, and it can only be
// left by picking a key (or keeping the one shown). F7 always opens the panel too.
public class KeyBindScreen extends Screen {
    private static final int W = 250, H = 112, PAD = 12;

    private final Runnable then;
    private String msg = "";
    private float scale = 1;
    private int px, py;

    KeyBindScreen(Runnable then) {
        super(Component.literal("Pick your key"));
        this.then = then;
    }

    private void layout() {
        scale = Style.popupScale(width, height, W, H, 0.55F, 0.7F); // not bigger than about half the screen
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - H) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        Style.panel(g, px, py, W, H, 248);
        int cx = px + W / 2, tw = W - PAD * 2;
        Txt.center(g, font, "PICK YOUR KEY", cx, py + PAD, Tour.glow());
        Txt.center(g, font, Txt.cut(font, "Which key should open the auction panel?", tw), cx, py + PAD + 14, Colors.TITLE);
        Txt.center(g, font, Txt.cut(font, "Press it now.", tw), cx, py + PAD + 28, Colors.TEXT);
        String hint = msg.isEmpty() ? "F7 always opens the panel too" : msg;
        Txt.center(g, font, Txt.cut(font, hint, tw), cx, py + PAD + 44, msg.isEmpty() ? Colors.MUTED : Colors.YELLOW);
        String keep = "Keep " + AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey);
        int bw = Math.min(tw, Txt.width(font, keep) + 24), bx = cx - bw / 2, by = py + H - PAD - 18;
        Style.button(g, font, bx, by, bw, 18, keep, Colors.BTN, true, inside(mx, my, bx, by, bw, 18));
        g.pose().popMatrix();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void pick(int key, boolean pressed) {
        AuctionState st = AuctionState.INSTANCE;
        st.config.toggleKey = key;
        st.save();
        AuctionState.click();
        // picked by pressing it: letting go of it would "tap" it and close the panel the tour just opened
        if (pressed) AuctionMod.consumeKey(key);
        Minecraft.getInstance().gui.setScreen(null);
        then.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (event.isEscape()) {
            msg = "Press the key you want, or click Keep";
            return true;
        }
        if (key == InputConstants.UNKNOWN.getValue() || key < 0) {
            return true;
        }
        String used = Tour.minecraftUse(key);
        if (used != null) {
            msg = AuctionState.keyName(key) + " is Minecraft's \"" + used + "\" key - pick another one";
            return true;
        }
        pick(key, true);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() / scale, my = event.y() / scale;
        int cx = px + W / 2, tw = W - PAD * 2;
        String keep = "Keep " + AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey);
        int bw = Math.min(tw, Txt.width(font, keep) + 24), bx = cx - bw / 2, by = py + H - PAD - 18;
        if (event.button() == 0 && inside((int) mx, (int) my, bx, by, bw, 18)) {
            pick(AuctionState.INSTANCE.config.toggleKey, false);
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
