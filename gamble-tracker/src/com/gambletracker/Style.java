package com.gambletracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Style {
    // button labels that are drawn as little pixel icons
    public static final String ICON_GEAR = "@gear";
    public static final String ICON_HIDE = "@hide";
    public static final String ICON_CLOSE = "@close";

    private static final String[] GEAR = {
            "...###...",
            ".#.###.#.",
            "..#####..",
            "####.####",
            "###...###",
            "####.####",
            "..#####..",
            ".#.###.#.",
            "...###...",
    };
    private static final String[] HIDE = {
            ".....",
            ".....",
            ".....",
            "#####",
            ".....",
    };
    private static final String[] CLOSE = {
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#",
    };

    public static final String[] CORNER_NAMES = {"Sharp", "Subtle", "Normal", "Round", "Extra round"};
    private static final float[] CORNERS = {0F, 0.5F, 1F, 1.5F, 2F};

    // set by applyTheme()
    public static boolean shadow;
    private static float corners = 1F;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int ROSE_TOP = 0xFFF43F5E;
    private static final int ROSE_BOTTOM = 0xFF9F1239;

    private Style() {}

    // the scale for a pop-up window (w x h at scale 1): at most about shareW / shareH of the screen, snapped to whole screen
    // pixels so Minecraft's pixel font stays sharp, and always small enough to fit
    public static float popupScale(int width, int height, int w, int h, float shareW, float shareH) {
        float want = Math.min(1F, Math.min(width * shareW / w, height * shareH / h));
        int gs = Smooth.guiScale();
        float snapped = Math.min(1F, Math.max(1, Math.round(want * gs)) / (float) gs);
        float fit = Math.min((width - 8) / (float) w, (height - 8) / (float) h);
        return Math.max(0.3F, Math.min(snapped, fit));
    }

    public static Look look() {
        if (!Edition.LOOKS) {
            return Look.CLASSIC; // Free: always the Classic look
        }
        Look l = GambleState.INSTANCE.look;
        return l == null || l.retired() ? Look.PLAIN : l;
    }

    public static boolean modern() {
        return look() != Look.CLASSIC;
    }

    public static String title() {
        String custom = GambleState.INSTANCE.title;
        return custom == null || custom.isBlank() ? defaultTitle() : custom;
    }

    public static String defaultTitle() {
        return switch (look()) {
            case ROUNDED, NEON -> "GAMBLE QUEUE";
            default -> "Gamble Queue";
        };
    }

    public static void tune(int cornerChoice, boolean textShadow) {
        corners = CORNERS[Math.floorMod(cornerChoice, CORNERS.length)];
        shadow = textShadow;
    }

    private static int fade(int argb, int alpha) {
        int a = (argb >>> 24) * alpha / 255;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    private static int rowInset(int h, int r, int row) {
        if (row < r) {
            return inset(r, row);
        }
        if (row >= h - r) {
            return inset(r, h - 1 - row);
        }
        return 0;
    }

    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(0, Math.min(Math.round(r * corners), Math.min(w, h) / 2));
        if (top == bottom) {
            for (int i = 0; i < r; i++) {
                int in = inset(r, i);
                g.fill(x + in, y + i, x + w - in, y + i + 1, top);
                g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, top);
            }
            g.fill(x, y + r, x + w, y + h - r, top);
            return;
        }
        for (int row = 0; row < h; row++) {
            int in = rowInset(h, r, row);
            g.fill(x + in, y + row, x + w - in, y + row + 1, Colors.lerp(top, bottom, h <= 1 ? 0 : row / (float) (h - 1)));
        }
    }

    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        round(g, x, y, w, h, r, color, color);
    }

    public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.max(1, Math.min(Math.round(r * corners), Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int color = Colors.lerp(top, bottom, h <= 1 ? 0 : row / (float) (h - 1));
            int out = rowInset(h, r, row);
            if (row == 0 || row == h - 1) {
                g.fill(x + out, y + row, x + w - out, y + row + 1, color);
                continue;
            }
            int in = Math.max(1 + rowInset(h - 2, r - 1, row - 1), out + 1);
            g.fill(x + out, y + row, x + in, y + row + 1, color);
            g.fill(x + w - in, y + row, x + w - out, y + row + 1, color);
        }
    }

    public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        ring(g, x, y, w, h, r, color, color);
    }

    // a 1px line that is brightest in the middle and fades out at both ends
    private static void glint(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        int a = color >>> 24;
        for (int i = 0; i < w; i++) {
            double t = w <= 1 ? 0.5 : i / (double) (w - 1);
            g.fill(x + i, y, x + i + 1, y + 1, Colors.withAlpha(color, (int) (a * Math.sin(Math.PI * t))));
        }
    }

    // a pixel-art icon in the middle of its button, placed on whole screen pixels (so it's centred at any panel size)
    private static void icon(GuiGraphicsExtractor g, String[] bitmap, int x, int y, int w, int h, int color) {
        int ih = bitmap.length, iw = bitmap[0].length();
        Smooth.Spot b = Smooth.spot(g, x, y);
        int bw = Math.round(w * b.k()), bh = Math.round(h * b.k());
        int px = Math.max(1, Math.round(b.k())); // screen pixels per icon pixel
        int ox = b.x() + (bw - iw * px) / 2, oy = b.y() + (bh - ih * px) / 2;
        Smooth.begin(g);
        try {
            for (int r = 0; r < ih; r++) {
                for (int c = 0; c < iw; c++) {
                    if (bitmap[r].charAt(c) == '#') {
                        g.fill(ox + c * px, oy + r * px, ox + (c + 1) * px, oy + (r + 1) * px, color);
                    }
                }
            }
        } finally {
            Smooth.end(g);
        }
    }

    // Default (Plain): a flat box with a 1px line round it; its corners follow the Corners setting (square at "Sharp")
    private static void plainBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int line) {
        round(g, x, y, w, h, 2, line);
        round(g, x + 1, y + 1, w - 2, h - 2, 1, fill);
    }

    // the corner radius round() really uses for r at this size
    private static int scaledR(int r, int w, int h) {
        return Math.max(0, Math.min(Math.round(r * corners), Math.min(w, h) / 2));
    }

    // light text keeps Minecraft's drop shadow; dark text (on a light button) doesn't, or it looks doubled
    private static boolean light(int color) {
        int r = (color >> 16) & 255, gr = (color >> 8) & 255, b = color & 255;
        return r * 299 + gr * 587 + b * 114 > 100_000;
    }

    // black or white text, whichever reads on that fill
    private static int textOn(int fill) {
        int r = (fill >> 16) & 255, gr = (fill >> 8) & 255, b = fill & 255;
        return r * 299 + gr * 587 + b * 114 > 150_000 ? 0xFF111111 : WHITE;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        switch (look()) {
            case PLAIN -> { // flat, one accent line along the top; the opacity slider and Corners apply
                round(g, x - 1, y - 1, w + 2, h + 2, 4, fade(0xFF0C0C0C, alpha));
                round(g, x, y, w, h, 3, Colors.withAlpha(Colors.BG, alpha));
                int r = scaledR(3, w, h);
                for (int row = 0; row < 2; row++) {
                    int in = rowInset(h, r, row);
                    g.fill(x + in, y + row, x + w - in, y + row + 1, Colors.ACCENT_A);
                }
            }
            case SLEEK -> {
                Smooth.shadow(g, x, y, w, h, 10, 9, fade(0x8C000000, alpha));
                Smooth.rect(g, x, y, w, h, 10, Colors.withAlpha(Colors.BG, alpha * 15 / 16));
                Smooth.rect(g, x, y, w, 16, 10, fade(0x07FFFFFF, alpha));
                Smooth.ring(g, x, y, w, h, 10, 1, fade(0x24FFFFFF, alpha));
            }
            case CLASSIC -> {
                g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Colors.withAlpha(Colors.BORDER, alpha));
                g.fill(x, y, x + w, y + h, Colors.withAlpha(Colors.BG, alpha));
            }
            case ROUNDED -> {
                ring(g, x - 2, y - 2, w + 4, h + 4, 8, Colors.withAlpha(Colors.ACCENT_A, alpha / 4), Colors.withAlpha(Colors.ACCENT_B, alpha / 4));
                ring(g, x - 1, y - 1, w + 2, h + 2, 7, Colors.ACCENT_A, Colors.ACCENT_B);
                round(g, x, y, w, h, 6, Colors.withAlpha(0x0C0C12, alpha), Colors.withAlpha(0x020203, alpha));
            }
            case GLASS -> {
                round(g, x, y, w, h, 9, Colors.withAlpha(0x1E293B, alpha * 9 / 10), Colors.withAlpha(0x0B1120, alpha * 9 / 10));
                ring(g, x - 1, y - 1, w + 2, h + 2, 10, fade(0x90FFFFFF, alpha), fade(0x30FFFFFF, alpha));
                g.fill(x + 8, y + 1, x + w - 8, y + 2, fade(0x40FFFFFF, alpha));
            }
            case LIQUID -> {
                round(g, x - 2, y, w + 4, h + 3, 14, fade(0x38000000, alpha));
                round(g, x, y, w, h, 12, fade(0x9E1E2633, alpha), fade(0xB80B0F16, alpha));
                round(g, x + 1, y + 1, w - 2, Math.max(4, h / 3), 11, fade(0x2CFFFFFF, alpha), 0x00FFFFFF);
                ring(g, x - 1, y - 1, w + 2, h + 2, 13, Colors.withAlpha(Colors.ACCENT_A, alpha / 3), Colors.withAlpha(Colors.ACCENT_B, alpha / 3));
                ring(g, x, y, w, h, 12, fade(0xF0FFFFFF, alpha), fade(0x38FFFFFF, alpha));
                ring(g, x + 1, y + 1, w - 2, h - 2, 11, fade(0x48FFFFFF, alpha), 0x00FFFFFF);
                glint(g, x + 12, y + 1, w / 2 - 12, fade(0xB0FFFFFF, alpha));
            }
            case NEON -> {
                ring(g, x - 4, y - 4, w + 8, h + 8, 6, Colors.withAlpha(Colors.ACCENT_A, 0x18), Colors.withAlpha(Colors.ACCENT_B, 0x18));
                ring(g, x - 3, y - 3, w + 6, h + 6, 5, Colors.withAlpha(Colors.ACCENT_A, 0x40), Colors.withAlpha(Colors.ACCENT_B, 0x40));
                ring(g, x - 2, y - 2, w + 4, h + 4, 4, Colors.ACCENT_A, Colors.ACCENT_B);
                ring(g, x - 1, y - 1, w + 2, h + 2, 3, Colors.ACCENT_A, Colors.ACCENT_B);
                round(g, x, y, w, h, 2, Colors.withAlpha(0x000000, alpha));
            }
            case MINIMAL -> {
                round(g, x - 1, y - 1, w + 2, h + 2, 5, Colors.withAlpha(Colors.BORDER, alpha));
                round(g, x, y, w, h, 4, Colors.withAlpha(Colors.BG, alpha));
            }
        }
    }

    public static void divider(GuiGraphicsExtractor g, int x, int y, int w, int alpha) {
        switch (look()) {
            case SLEEK -> Smooth.hairline(g, x, y, w, fade(0x26FFFFFF, alpha), true);
            case CLASSIC, MINIMAL -> g.fill(x, y, x + w, y + 1, Colors.withAlpha(Colors.BORDER, alpha));
            case PLAIN -> g.fill(x, y, x + w, y + 1, Colors.BORDER);
            case GLASS -> g.fill(x, y, x + w, y + 1, fade(0x40FFFFFF, alpha));
            case LIQUID -> glint(g, x, y, w, fade(0x90FFFFFF, alpha));
            case ROUNDED, NEON -> {
                boolean neon = look() == Look.NEON;
                int steps = 24;
                for (int i = 0; i < steps; i++) {
                    int x1 = x + w * i / steps, x2 = x + w * (i + 1) / steps;
                    float t = i / (float) (steps - 1);
                    int a = neon ? 255 : (int) (200 * Math.min(1, Math.min(t, 1 - t) * 4));
                    g.fill(x1, y, x2, y + 1, Colors.withAlpha(Colors.lerp(Colors.ACCENT_A, Colors.ACCENT_B, t), a));
                }
            }
        }
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y, int s, int alpha, boolean selected, boolean hover) {
        switch (look()) {
            case PLAIN -> plainBox(g, x + 1, y + 1, s - 2, s - 2, hover && !selected ? 0xFF242424 : Colors.SLOT_BG,
                    selected ? Colors.ACCENT_A : hover ? 0xFF6A6A6A : 0xFF2E2E2E);
            case SLEEK -> {
                int bg = selected ? Colors.withAlpha(Colors.ACCENT_A, 0x2E) : hover ? 0x1CFFFFFF : 0x0DFFFFFF;
                Smooth.rect(g, x + 1, y + 1, s - 2, s - 2, 5, bg);
                if (selected) {
                    Smooth.ring(g, x + 1, y + 1, s - 2, s - 2, 5, 2, Colors.ACCENT_A);
                } else if (hover) {
                    Smooth.ring(g, x + 1, y + 1, s - 2, s - 2, 5, 1, 0x40FFFFFF);
                }
            }
            case CLASSIC -> {
                g.fill(x + 1, y + 1, x + s - 1, y + s - 1, Colors.withAlpha(Colors.SLOT_BG, alpha));
                g.fill(x + 1, y + 1, x + s - 1, y + 2, Colors.SLOT_DARK);
                g.fill(x + 1, y + 1, x + 2, y + s - 1, Colors.SLOT_DARK);
                g.fill(x + 1, y + s - 2, x + s - 1, y + s - 1, Colors.SLOT_LIGHT);
                g.fill(x + s - 2, y + 1, x + s - 1, y + s - 1, Colors.SLOT_LIGHT);
                if (selected) {
                    g.outline(x + 1, y + 1, s - 2, s - 2, Colors.GOLD);
                }
                if (hover) {
                    g.outline(x, y, s, s, WHITE);
                }
            }
            case ROUNDED -> {
                int bg = selected ? 0xFF1A1230 : hover ? 0xFF1C1C26 : Colors.SLOT_BG;
                round(g, x + 1, y + 1, s - 2, s - 2, 4, Colors.withAlpha(bg, Math.max(alpha, 200)));
                if (selected) {
                    ring(g, x + 1, y + 1, s - 2, s - 2, 4, Colors.ACCENT_A, Colors.ACCENT_B);
                } else if (hover) {
                    ring(g, x + 1, y + 1, s - 2, s - 2, 4, 0xFF5A5A70);
                }
            }
            case GLASS -> {
                round(g, x + 1, y + 1, s - 2, s - 2, 5, hover || selected ? 0x30FFFFFF : 0x16FFFFFF);
                if (selected) {
                    ring(g, x + 1, y + 1, s - 2, s - 2, 5, 0xD0FFFFFF);
                } else if (hover) {
                    ring(g, x + 1, y + 1, s - 2, s - 2, 5, 0x60FFFFFF);
                }
            }
            case LIQUID -> {
                round(g, x + 1, y + 1, s - 2, s - 2, 6, selected ? 0x40FFFFFF : hover ? 0x34FFFFFF : 0x1CFFFFFF,
                        selected ? 0x24FFFFFF : 0x0CFFFFFF);
                ring(g, x + 1, y + 1, s - 2, s - 2, 6, selected ? 0xF0FFFFFF : hover ? 0x90FFFFFF : 0x50FFFFFF,
                        selected ? 0x80FFFFFF : 0x14FFFFFF);
            }
            case NEON -> {
                round(g, x + 1, y + 1, s - 2, s - 2, 2, selected ? Colors.withAlpha(Colors.ACCENT_A, 0x30) : 0xFF050505);
                ring(g, x + 1, y + 1, s - 2, s - 2, 2, selected ? Colors.ACCENT_A : hover ? Colors.ACCENT_B : Colors.SLOT_LIGHT,
                        selected ? Colors.ACCENT_B : hover ? Colors.ACCENT_B : Colors.SLOT_LIGHT);
            }
            case MINIMAL -> {
                int bg = selected ? Colors.withAlpha(Colors.ACCENT_A, 0x50) : hover ? 0xFF2E2E33 : Colors.SLOT_BG;
                round(g, x + 1, y + 1, s - 2, s - 2, 3, bg);
                if (selected) {
                    ring(g, x + 1, y + 1, s - 2, s - 2, 3, Colors.ACCENT_A);
                }
            }
        }
    }

    public static void itemFrame(GuiGraphicsExtractor g, int x, int y, int s) {
        switch (look()) {
            case PLAIN -> plainBox(g, x, y, s, s, Colors.FIELD_BG, Colors.BORDER);
            case SLEEK -> {
                Smooth.rect(g, x, y, s, s, 9, 0x10FFFFFF);
                Smooth.ring(g, x, y, s, s, 9, 1, 0x26FFFFFF);
            }
            case CLASSIC -> {
                g.fill(x, y, x + s, y + s, Colors.GOLD);
                g.fill(x + 1, y + 1, x + s - 1, y + s - 1, Colors.SLOT_BG);
            }
            case ROUNDED -> {
                round(g, x + 1, y + 1, s - 2, s - 2, 6, 0xFF1A1A26, 0xFF101016);
                ring(g, x, y, s, s, 7, Colors.ACCENT_A, Colors.ACCENT_B);
            }
            case GLASS -> {
                round(g, x, y, s, s, 8, 0x26FFFFFF, 0x10FFFFFF);
                ring(g, x, y, s, s, 8, 0xA0FFFFFF, 0x40FFFFFF);
            }
            case LIQUID -> {
                round(g, x, y, s, s, 10, 0x30FFFFFF, 0x10FFFFFF);
                ring(g, x, y, s, s, 10, 0xE0FFFFFF, 0x30FFFFFF);
                glint(g, x + 8, y + 1, s - 16, 0xA0FFFFFF);
            }
            case NEON -> {
                round(g, x, y, s, s, 3, 0xFF000000);
                ring(g, x, y, s, s, 3, Colors.ACCENT_A, Colors.ACCENT_B);
                ring(g, x + 1, y + 1, s - 2, s - 2, 2, Colors.withAlpha(Colors.ACCENT_A, 0x60), Colors.withAlpha(Colors.ACCENT_B, 0x60));
            }
            case MINIMAL -> round(g, x, y, s, s, 5, Colors.SLOT_BG);
        }
    }

    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label,
                              int color, boolean enabled, boolean hover) {
        boolean go = color == Colors.BTN_GO, stop = color == Colors.BTN_STOP;
        int text = enabled ? WHITE : 0xFF666677;
        switch (look()) {
            case PLAIN -> {
                int fill = !enabled ? Colors.BTN_OFF : go ? Colors.ACCENT_A : stop ? 0xFFB8443A : Colors.BTN;
                if (enabled && hover) fill = Colors.lighten(fill);
                plainBox(g, x, y, w, h, fill, enabled && hover && !go && !stop ? 0xFF6A6A6A : 0xFF0C0C0C);
                text = !enabled ? 0xFF707070 : textOn(fill);
            }
            case SLEEK -> {
                float r = Math.min(h / 2.0F, 9);
                int fill;
                if (!enabled) {
                    fill = 0x08FFFFFF;
                    text = 0xFF5C6370;
                } else if (go) {
                    fill = hover ? Colors.lighten(Colors.ACCENT_A) : Colors.ACCENT_A;
                } else if (stop) {
                    fill = hover ? 0xFFF0646A : 0xFFE5484D;
                } else {
                    fill = hover ? 0x24FFFFFF : 0x12FFFFFF;
                }
                Smooth.rect(g, x, y, w, h, r, fill);
                if (enabled && !go && !stop) {
                    Smooth.ring(g, x, y, w, h, r, 1, hover ? 0x40FFFFFF : 0x1FFFFFFF);
                }
                switch (label) {
                    case ICON_GEAR -> Smooth.gearIn(g, x, y, w, h, 4.1F, text);
                    case ICON_HIDE -> Smooth.line(g, x + 3.5F, y + h / 2.0F, x + w - 3.5F, y + h / 2.0F, 1.2F, text);
                    case ICON_CLOSE -> {
                        Smooth.line(g, x + 3.6F, y + 3.6F, x + w - 3.6F, y + h - 3.6F, 1.2F, text);
                        Smooth.line(g, x + w - 3.6F, y + 3.6F, x + 3.6F, y + h - 3.6F, 1.2F, text);
                    }
                    default -> label(g, font, label, x, y, w, h, text);
                }
                return;
            }
            case CLASSIC -> {
                int base = enabled ? color : Colors.BTN_OFF;
                bevel(g, x, y, w, h, hover ? Colors.lighten(base) : base);
                if (!enabled) {
                    text = 0xFF777777;
                }
            }
            case ROUNDED -> {
                if (!enabled) {
                    round(g, x, y, w, h, 5, Colors.BTN_OFF);
                } else if (go || stop) {
                    int top = go ? Colors.ACCENT_A : ROSE_TOP;
                    int bottom = go ? Colors.darken(Colors.ACCENT_B, 0.3F) : ROSE_BOTTOM;
                    round(g, x, y, w, h, 5, hover ? Colors.lighten(top) : top, hover ? Colors.lighten(bottom) : bottom);
                } else {
                    round(g, x, y, w, h, 5, hover ? 0xFF262634 : 0xFF1C1C27, hover ? 0xFF1C1C28 : 0xFF14141C);
                    ring(g, x, y, w, h, 5, hover ? Colors.ACCENT_B : 0xFF34344A, hover ? Colors.ACCENT_A : 0xFF26263A);
                }
            }
            case GLASS -> {
                int top, bottom;
                if (!enabled) {
                    top = bottom = Colors.BTN_OFF;
                } else if (go) {
                    top = Colors.withAlpha(Colors.ACCENT_A, 0xC0);
                    bottom = Colors.withAlpha(Colors.ACCENT_B, 0xC0);
                } else if (stop) {
                    top = Colors.withAlpha(ROSE_TOP, 0xC0);
                    bottom = Colors.withAlpha(ROSE_BOTTOM, 0xC0);
                } else {
                    top = hover ? 0x40FFFFFF : 0x26FFFFFF;
                    bottom = hover ? 0x30FFFFFF : 0x14FFFFFF;
                }
                round(g, x, y, w, h, 6, top, bottom);
                ring(g, x, y, w, h, 6, hover ? 0xC0FFFFFF : 0x60FFFFFF, hover ? 0x80FFFFFF : 0x20FFFFFF);
            }
            case LIQUID -> {
                int r = Math.min(h / 2, 7);
                int top, bottom;
                if (!enabled) {
                    top = bottom = 0x14FFFFFF;
                } else if (go || stop) {
                    int a = go ? Colors.ACCENT_A : ROSE_TOP, b = go ? Colors.ACCENT_B : ROSE_BOTTOM;
                    top = Colors.withAlpha(a, hover ? 0xD0 : 0xA8);
                    bottom = Colors.withAlpha(b, hover ? 0xB0 : 0x88);
                } else {
                    top = hover ? 0x48FFFFFF : 0x30FFFFFF;
                    bottom = hover ? 0x24FFFFFF : 0x12FFFFFF;
                }
                round(g, x, y, w, h, r, top, bottom);
                ring(g, x, y, w, h, r, !enabled ? 0x40FFFFFF : hover ? WHITE : 0xC0FFFFFF, 0x20FFFFFF);
                if (h >= 9 && w > 2 * r) {
                    glint(g, x + r, y + 1, w - 2 * r, hover ? 0xC0FFFFFF : 0x80FFFFFF);
                }
            }
            case NEON -> {
                int line = !enabled ? 0xFF2A2A2A : go ? Colors.ACCENT_B : stop ? Colors.RED : hover ? Colors.ACCENT_A : 0xFF3A3A4A;
                round(g, x, y, w, h, 2, hover && enabled ? Colors.withAlpha(line, 0x30) : 0xFF000000);
                ring(g, x, y, w, h, 2, line);
                if (enabled && (go || stop)) {
                    text = line;
                }
            }
            case MINIMAL -> {
                int fill = !enabled ? Colors.BTN_OFF : go ? Colors.ACCENT_A : stop ? 0xFFDC2626 : Colors.BTN;
                round(g, x, y, w, h, 4, hover && enabled ? Colors.lighten(fill) : fill);
            }
        }
        switch (label) {
            case ICON_GEAR -> icon(g, GEAR, x, y, w, h, text);
            case ICON_HIDE -> icon(g, HIDE, x, y, w, h, text);
            case ICON_CLOSE -> icon(g, CLOSE, x, y, w, h, text);
            default -> label(g, font, label, x, y, w, h, text);
        }
    }

    // centred, and shrunk a little when it's wider than the button
    // set while a row of buttons is drawn that should share one text size (the settings tabs); 0 = each fits itself
    public static float labelScale;

    private static void label(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, int h, int color) {
        int tw = Txt.width(font, label);
        if (labelScale > 0 && labelScale < 1) {
            float s = labelScale;
            g.pose().pushMatrix();
            g.pose().translate(x + (w - tw * s) / 2, y + (h - 8 * s) / 2);
            g.pose().scale(s, s);
            Txt.draw(g, font, Txt.cut(font, label, (int) ((w - 4) / s)), 0, 0, color, shadow && light(color));
            g.pose().popMatrix();
            return;
        }
        if (tw <= w - 4) {
            Txt.draw(g, font, label, x + (w - tw) / 2, y + (h - 8) / 2, color, light(color));
            return;
        }
        float s = Math.max(0.6F, (float) Math.floor((w - 4) / (float) tw * 10) / 10); // 10% steps: fewer text sizes
        g.pose().pushMatrix();
        g.pose().translate(x + (w - tw * s) / 2, y + (h - 8 * s) / 2);
        g.pose().scale(s, s);
        Txt.draw(g, font, Txt.cut(font, label, (int) ((w - 4) / s)), 0, 0, color, shadow && light(color));
        g.pose().popMatrix();
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int base) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x, y, x + w - 1, y + h - 1, Colors.lighten(Colors.lighten(base)));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
    }

    public static void check(GuiGraphicsExtractor g, Font font, int x, int y, boolean checked, String label) {
        switch (look()) {
            case PLAIN -> {
                plainBox(g, x, y, 9, 9, Colors.FIELD_BG, checked ? Colors.ACCENT_A : Colors.FIELD_BORDER);
                if (checked) {
                    g.fill(x + 2, y + 2, x + 7, y + 7, Colors.ACCENT_A);
                }
            }
            case SLEEK -> {
                if (checked) {
                    Smooth.rect(g, x, y, 9, 9, 3, Colors.ACCENT_A);
                    Smooth.line(g, x + 2.3F, y + 4.6F, x + 3.9F, y + 6.2F, 1.3F, WHITE);
                    Smooth.line(g, x + 3.9F, y + 6.2F, x + 6.8F, y + 2.9F, 1.3F, WHITE);
                } else {
                    Smooth.rect(g, x, y, 9, 9, 3, 0x12FFFFFF);
                    Smooth.ring(g, x, y, 9, 9, 3, 1, 0x40FFFFFF);
                }
            }
            case CLASSIC -> {
                g.fill(x, y, x + 9, y + 9, Colors.FIELD_BORDER);
                g.fill(x + 1, y + 1, x + 8, y + 8, Colors.FIELD_BG);
                if (checked) {
                    g.fill(x + 2, y + 2, x + 7, y + 7, Colors.TEXT);
                }
            }
            case ROUNDED -> {
                if (checked) {
                    round(g, x, y, 9, 9, 3, Colors.ACCENT_A, Colors.ACCENT_B);
                    round(g, x + 3, y + 3, 3, 3, 1, WHITE);
                } else {
                    round(g, x, y, 9, 9, 3, Colors.FIELD_BG);
                    ring(g, x, y, 9, 9, 3, 0xFF3A3A50);
                }
            }
            case GLASS -> {
                round(g, x, y, 9, 9, 3, checked ? 0xE0FFFFFF : 0x26FFFFFF);
                ring(g, x, y, 9, 9, 3, 0x80FFFFFF);
                if (checked) {
                    round(g, x + 3, y + 3, 3, 3, 1, 0xFF0F172A);
                }
            }
            case LIQUID -> {
                round(g, x, y, 9, 9, 4, checked ? Colors.withAlpha(Colors.ACCENT_A, 0xD0) : 0x26FFFFFF,
                        checked ? Colors.withAlpha(Colors.ACCENT_B, 0xD0) : 0x10FFFFFF);
                ring(g, x, y, 9, 9, 4, 0xD0FFFFFF, 0x40FFFFFF);
                if (checked) {
                    round(g, x + 3, y + 3, 3, 3, 1, WHITE);
                }
            }
            case NEON -> {
                g.fill(x, y, x + 9, y + 9, 0xFF000000);
                ring(g, x, y, 9, 9, 1, checked ? Colors.ACCENT_B : 0xFF3A3A4A);
                if (checked) {
                    g.fill(x + 2, y + 2, x + 7, y + 7, Colors.ACCENT_B);
                }
            }
            case MINIMAL -> {
                round(g, x, y, 9, 9, 2, checked ? Colors.ACCENT_A : Colors.FIELD_BG);
                if (checked) {
                    g.fill(x + 3, y + 3, x + 6, y + 6, WHITE);
                }
            }
        }
        Txt.draw(g, font, label, x + 13, y + 1, Colors.TEXT, shadow);
    }

    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        switch (look()) {
            case PLAIN -> plainBox(g, x, y, w, h, Colors.FIELD_BG, 0xFF2E2E2E);
            case SLEEK -> {
                Smooth.rect(g, x, y, w, h, 7, 0x38000000);
                Smooth.ring(g, x, y, w, h, 7, 1, 0x17FFFFFF);
            }
            case CLASSIC -> {
                g.fill(x, y, x + w, y + h, Colors.SLOT_LIGHT);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Colors.FIELD_BG);
            }
            case ROUNDED -> {
                round(g, x, y, w, h, 5, 0xFF07070B);
                ring(g, x, y, w, h, 5, 0xFF24243A, 0xFF1A1A26);
            }
            case GLASS -> {
                round(g, x, y, w, h, 6, 0x90000000);
                ring(g, x, y, w, h, 6, 0x40FFFFFF, 0x18FFFFFF);
            }
            case LIQUID -> {
                round(g, x, y, w, h, 8, 0x58000000, 0x40000000);
                ring(g, x, y, w, h, 8, 0x50FFFFFF, 0x10FFFFFF);
            }
            case NEON -> {
                g.fill(x, y, x + w, y + h, 0xFF000000);
                ring(g, x, y, w, h, 2, Colors.SLOT_LIGHT);
            }
            case MINIMAL -> round(g, x, y, w, h, 4, 0xFF111113);
        }
    }

    // frame for the borderless EditBoxes used by every look except Classic
    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        switch (look()) {
            case PLAIN -> plainBox(g, x, y, w, h, Colors.FIELD_BG, focused ? Colors.ACCENT_A : Colors.FIELD_BORDER);
            case SLEEK -> {
                Smooth.rect(g, x, y, w, h, 6, focused ? 0x1AFFFFFF : 0x10FFFFFF);
                Smooth.ring(g, x, y, w, h, 6, 1, focused ? Colors.ACCENT_A : 0x24FFFFFF);
            }
            case GLASS -> {
                round(g, x, y, w, h, 5, 0x26FFFFFF);
                ring(g, x, y, w, h, 5, focused ? 0xD0FFFFFF : 0x50FFFFFF);
            }
            case LIQUID -> {
                round(g, x, y, w, h, 7, 0x2CFFFFFF, 0x14FFFFFF);
                ring(g, x, y, w, h, 7, focused ? 0xF0FFFFFF : 0x80FFFFFF, focused ? 0x90FFFFFF : 0x20FFFFFF);
            }
            case NEON -> {
                round(g, x, y, w, h, 2, 0xFF000000);
                ring(g, x, y, w, h, 2, focused ? Colors.ACCENT_B : 0xFF2A2A3A);
            }
            case MINIMAL -> {
                round(g, x, y, w, h, 4, Colors.FIELD_BG);
                if (focused) {
                    ring(g, x, y, w, h, 4, Colors.ACCENT_A);
                }
            }
            default -> {
                round(g, x, y, w, h, 4, Colors.FIELD_BG);
                if (focused) {
                    ring(g, x, y, w, h, 4, Colors.ACCENT_A, Colors.ACCENT_B);
                } else {
                    ring(g, x, y, w, h, 4, 0xFF2E2E42, 0xFF22222F);
                }
            }
        }
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int thumbTop, int thumbH) {
        switch (look()) {
            case PLAIN -> {
                g.fill(x, y, x + 4, y + h, Colors.FIELD_BG);
                g.fill(x, y + thumbTop, x + 4, y + thumbTop + thumbH, 0xFF5A5A5A);
            }
            case SLEEK -> {
                Smooth.rect(g, x + 1, y, 2, h, 1, 0x12FFFFFF);
                Smooth.rect(g, x + 0.5F, y + thumbTop, 3, thumbH, 1.5F, 0x8CFFFFFF);
            }
            case CLASSIC -> {
                g.fill(x, y, x + 4, y + h, Colors.FIELD_BG);
                g.fill(x, y + thumbTop, x + 4, y + thumbTop + thumbH, 0xFF8B8B8B);
            }
            case GLASS -> {
                round(g, x, y, 4, h, 2, 0x1AFFFFFF);
                round(g, x, y + thumbTop, 4, thumbH, 2, 0xB0FFFFFF);
            }
            case LIQUID -> {
                round(g, x, y, 4, h, 2, 0x18FFFFFF);
                round(g, x, y + thumbTop, 4, thumbH, 2, 0xD0FFFFFF, 0x90FFFFFF);
            }
            case MINIMAL -> {
                round(g, x, y, 4, h, 2, Colors.FIELD_BG);
                round(g, x, y + thumbTop, 4, thumbH, 2, 0xFF52525B);
            }
            default -> {
                round(g, x, y, 4, h, 2, look() == Look.NEON ? 0xFF0A0A0A : 0xFF121219);
                round(g, x, y + thumbTop, 4, thumbH, 2, Colors.ACCENT_A, Colors.ACCENT_B);
            }
        }
    }

    // 60px track, value 0..1
    public static void slider(GuiGraphicsExtractor g, int x, int y, float value) {
        int knob = x + Math.round(value * 56);
        switch (look()) {
            case PLAIN -> {
                g.fill(x, y + 3, x + 60, y + 5, Colors.FIELD_BG);
                g.fill(x, y + 3, knob + 2, y + 5, Colors.ACCENT_A);
                plainBox(g, knob, y, 5, 8, 0xFFD0D0D0, 0xFF0C0C0C);
            }
            case SLEEK -> {
                Smooth.rect(g, x, y + 3, 60, 2, 1, 0x24FFFFFF);
                Smooth.rect(g, x, y + 3, knob - x + 3, 2, 1, Colors.ACCENT_A);
                Smooth.shadow(g, knob - 1, y + 0.5F, 7, 7, 3.5F, 2, 0x60000000);
                Smooth.circle(g, knob + 2.5F, y + 4, 3.5F, WHITE);
            }
            case CLASSIC -> {
                g.fill(x, y + 2, x + 60, y + 5, Colors.FIELD_BG);
                bevel(g, knob, y, 5, 8, Colors.BTN);
            }
            case GLASS -> {
                round(g, x, y + 2, 60, 4, 2, 0x26FFFFFF);
                round(g, x, y + 2, knob - x + 3, 4, 2, 0xC0FFFFFF);
                round(g, knob - 1, y, 7, 8, 3, WHITE);
            }
            case LIQUID -> {
                round(g, x, y + 2, 60, 4, 2, 0x26FFFFFF);
                round(g, x, y + 2, knob - x + 3, 4, 2, 0xE0FFFFFF, 0xB0FFFFFF);
                round(g, knob - 2, y - 1, 9, 10, 4, WHITE, 0xFFDDE6F0);
                ring(g, knob - 2, y - 1, 9, 10, 4, 0x60000000);
            }
            case MINIMAL -> {
                round(g, x, y + 2, 60, 4, 2, Colors.FIELD_BG);
                round(g, x, y + 2, knob - x + 3, 4, 2, Colors.ACCENT_A);
                round(g, knob - 1, y, 7, 8, 3, WHITE);
            }
            default -> {
                round(g, x, y + 2, 60, 4, 2, 0xFF16161F);
                round(g, x, y + 2, knob - x + 3, 4, 2, Colors.ACCENT_A, Colors.ACCENT_B);
                round(g, knob - 1, y, 7, 8, 3, look() == Look.NEON ? Colors.ACCENT_B : WHITE);
            }
        }
    }

    public static int pill(GuiGraphicsExtractor g, Font font, int x, int y, String text, int color) {
        int w = Txt.width(font, text) + 6;
        switch (look()) {
            case PLAIN -> g.fill(x, y, x + w, y + 10, Colors.darken(color, 0.65F));
            case SLEEK -> {
                Smooth.rect(g, x, y, w, 10, 5, Colors.withAlpha(color, 0x2A));
                Smooth.ring(g, x, y, w, 10, 5, 1, Colors.withAlpha(color, 0x70));
            }
            case CLASSIC -> {
                g.fill(x, y, x + w, y + 10, Colors.darken(color, 0.55F));
                g.outline(x, y, w, 10, color);
            }
            case NEON -> {
                round(g, x, y, w, 10, 2, 0xFF000000);
                ring(g, x, y, w, 10, 2, color);
            }
            case LIQUID -> {
                round(g, x, y, w, 10, 5, Colors.withAlpha(color, 0x48), Colors.withAlpha(color, 0x20));
                ring(g, x, y, w, 10, 5, Colors.withAlpha(color, 0xC0), Colors.withAlpha(color, 0x40));
            }
            default -> round(g, x, y, w, 10, 4, Colors.withAlpha(color, 0x40));
        }
        Txt.draw(g, font, text, x + 3, y + 1, color, shadow);
        return w;
    }

    public static void highlight(GuiGraphicsExtractor g, int x, int y, int w, int h, int alpha) {
        switch (look()) {
            case PLAIN -> {
                g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
                g.fill(x, y, x + 2, y + h, Colors.ACCENT_A);
            }
            case SLEEK -> {
                Smooth.rect(g, x, y, w, h, 8, fade(0x12FFFFFF, alpha));
                Smooth.ring(g, x, y, w, h, 8, 1, Colors.withAlpha(Colors.ACCENT_A, Math.min(alpha, 0x90)));
            }
            case CLASSIC -> {
                g.fill(x, y, x + w, y + h, Colors.withAlpha(0x2E3A1E, alpha));
                g.fill(x, y, x + 2, y + h, Colors.GREEN);
            }
            case GLASS -> {
                round(g, x, y, w, h, 6, fade(0x22FFFFFF, alpha), fade(0x10FFFFFF, alpha));
                ring(g, x, y, w, h, 6, fade(0x70FFFFFF, alpha), fade(0x20FFFFFF, alpha));
            }
            case LIQUID -> {
                round(g, x, y, w, h, 8, fade(0x30FFFFFF, alpha), fade(0x12FFFFFF, alpha));
                ring(g, x, y, w, h, 8, fade(0xD0FFFFFF, alpha), fade(0x30FFFFFF, alpha));
                glint(g, x + 8, y + 1, w - 16, fade(0x90FFFFFF, alpha));
            }
            case NEON -> {
                round(g, x, y, w, h, 2, Colors.withAlpha(Colors.ACCENT_A, Math.min(alpha, 0x22)));
                ring(g, x, y, w, h, 2, Colors.ACCENT_A, Colors.ACCENT_B);
            }
            case MINIMAL -> {
                round(g, x, y, w, h, 4, Colors.withAlpha(0x27272A, alpha));
                g.fill(x, y + 3, x + 2, y + h - 3, Colors.ACCENT_A);
            }
            case ROUNDED -> {
                round(g, x, y, w, h, 5, Colors.withAlpha(0x151522, alpha), Colors.withAlpha(0x0C0C14, alpha));
                ring(g, x, y, w, h, 5, Colors.ACCENT_A, Colors.ACCENT_B);
            }
        }
    }

    // 60px rainbow track for picking a custom accent colour
    public static void hueSlider(GuiGraphicsExtractor g, int x, int y, int hue, boolean enabled) {
        for (int i = 0; i < 60; i++) {
            int c = Colors.hue(i * 6);
            g.fill(x + i, y + 2, x + i + 1, y + 6, enabled ? c : Colors.withAlpha(c, 0x50));
        }
        int knob = x + Math.round(Math.floorMod(hue, 360) / 360F * 56);
        if (look() == Look.SLEEK) {
            Smooth.circle(g, knob + 2.5F, y + 4, 4, 0xFF000000);
            Smooth.circle(g, knob + 2.5F, y + 4, 3, enabled ? Colors.hue(hue) : 0xFF555555);
            return;
        }
        g.fill(knob - 1, y - 1, knob + 6, y + 9, 0xFF000000);
        g.fill(knob, y, knob + 5, y + 8, enabled ? Colors.hue(hue) : 0xFF555555);
        g.fill(knob, y, knob + 5, y + 1, 0xA0FFFFFF);
    }
}
