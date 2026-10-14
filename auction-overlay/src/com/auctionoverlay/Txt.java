package com.auctionoverlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// All panel and card text goes through here: smooth text for the Sleek look, Minecraft's font for the rest.
public final class Txt {
    private Txt() {}

    static boolean smooth() {
        return Style.look() == Look.SLEEK && SmoothFont.ready();
    }

    public static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        if (smooth()) {
            try {
                SmoothFont.draw(g, text, x, y, color);
                return;
            } catch (RuntimeException | LinkageError e) {
                SmoothFont.disable();
            }
        }
        g.text(font, text, x, y, color, shadow);
    }

    public static void center(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        if (smooth()) {
            try {
                SmoothFont.draw(g, text, cx - SmoothFont.widthF(text) / 2, y, color);
                return;
            } catch (RuntimeException | LinkageError e) {
                SmoothFont.disable();
            }
        }
        g.centeredText(font, text, cx, y, color);
    }

    public static int width(Font font, String text) {
        return smooth() ? SmoothFont.width(text) : font.width(text);
    }

    public static String cut(Font font, String text, int maxWidth) {
        return smooth() ? SmoothFont.cut(text, maxWidth) : font.plainSubstrByWidth(text, maxWidth);
    }
}
