package com.auctionoverlay;

public final class Colors {
    public static final String[] ACCENT_NAMES = {
            "Violet / Cyan", "Crimson / Orange", "Emerald / Lime", "Pink / Purple", "Gold / Amber", "Ice / White", "Blood", "Custom"};
    public static final int CUSTOM_ACCENT = 7;
    // Free edition: one solid colour, no gradients. 0 keeps the plain Minecraft colours.
    public static final String[] SOLID_NAMES = {"Minecraft", "Red", "Orange", "Gold", "Green", "Aqua", "Blue", "Purple", "Pink", "White"};
    private static final int[] SOLID = {0xFFFFAA00, 0xFFFF5555, 0xFFFF9F43, 0xFFFFD34D, 0xFF55FF55, 0xFF55FFFF, 0xFF5B8CFF, 0xFFB46CFF, 0xFFFF6FC8, 0xFFFFFFFF};
    private static final int[][] ACCENTS = {
            {0xFF8B5CF6, 0xFF22D3EE},
            {0xFFF43F5E, 0xFFFB923C},
            {0xFF10B981, 0xFFA3E635},
            {0xFFEC4899, 0xFFA855F7},
            {0xFFFACC15, 0xFFF97316},
            {0xFF38BDF8, 0xFFF1F5F9},
            {0xFFDC2626, 0xFF7F1D1D},
    };

    public static int BG, BORDER, TEXT, MUTED, GREEN, GOLD, YELLOW, RED, AQUA;
    public static int FIELD_BG, FIELD_BORDER, BTN, BTN_OFF, SLOT_BG, SLOT_LIGHT, TITLE, ITEM_NAME;
    public static int ACCENT_A, ACCENT_B;
    public static final int SLOT_DARK = 0xFF1F1F1F;
    // not drawn as-is; Style looks at these to know a button is a go / stop button
    public static final int BTN_GO = 0xFF3F8F3F;
    public static final int BTN_STOP = 0xFFA23B3B;

    static {
        apply(Look.PLAIN, 0, 270, 190);
    }

    private Colors() {}

    // hueA / hueB (0-359) are only used by the "Custom" accent
    public static String[] accentNames() {
        return Edition.LOOKS ? ACCENT_NAMES : SOLID_NAMES;
    }

    public static void apply(Look look, int accent, int hueA, int hueB) {
        if (!Edition.LOOKS) { // Free: the Classic look with one solid accent colour
            classic();
            ACCENT_A = ACCENT_B = SOLID[Math.floorMod(accent, SOLID.length)];
            return;
        }
        int i = Math.floorMod(accent, ACCENT_NAMES.length);
        if (i == CUSTOM_ACCENT) {
            ACCENT_A = hue(hueA);
            ACCENT_B = hue(hueB);
        } else {
            ACCENT_A = ACCENTS[i][0];
            ACCENT_B = ACCENTS[i][1];
        }
        switch (look) {
            case ROUNDED -> rounded();
            case CLASSIC -> classic();
            case GLASS -> glass();
            case LIQUID -> liquid();
            case NEON -> neon();
            case MINIMAL -> minimal();
            case SLEEK -> sleek();
            case PLAIN -> plain();
        }
    }

    private static void set(int bg, int border, int text, int muted, int green, int gold, int yellow, int red, int aqua,
                            int fieldBg, int fieldBorder, int btn, int btnOff, int slotBg, int slotLight, int title, int itemName) {
        BG = bg;
        BORDER = border;
        TEXT = text;
        MUTED = muted;
        GREEN = green;
        GOLD = gold;
        YELLOW = yellow;
        RED = red;
        AQUA = aqua;
        FIELD_BG = fieldBg;
        FIELD_BORDER = fieldBorder;
        BTN = btn;
        BTN_OFF = btnOff;
        SLOT_BG = slotBg;
        SLOT_LIGHT = slotLight;
        TITLE = title;
        ITEM_NAME = itemName;
    }

    private static void classic() {
        set(0xFF1B1B1B, 0xFF5A5A5A, 0xFFE6E6E6, 0xFF9A9A9A, 0xFF55FF55, 0xFFFFAA00, 0xFFFFFF55, 0xFFFF5555, 0xFF55FFFF,
                0xFF000000, 0xFFA0A0A0, 0xFF6F6F6F, 0xFF3D3D3D, 0xFF3A3A3A, 0xFF5C5C5C, 0xFF55FF55, 0xFFFFFF55);
    }

    private static void solid(int i) {
        classic();
        ACCENT_A = ACCENT_B = SOLID[i];
        if (i > 0) {
            BORDER = SOLID[i];
            TITLE = SOLID[i];
            BTN = lerp(BTN, SOLID[i], 0.4F);
        }
    }

    private static void rounded() {
        set(0xFF050507, 0xFF2A2A3A, 0xFFECECF4, 0xFF8A8AA0, 0xFF4ADE80, 0xFFFBBF24, 0xFFFDE68A, 0xFFFB7185, 0xFF67E8F9,
                0xFF0A0A0F, 0xFF2A2A3A, 0xFF14141C, 0xFF121218, 0xFF101016, 0xFF2A2A3A, ACCENT_B, 0xFFFFFFFF);
    }

    private static void glass() {
        set(0xFF0F172A, 0x60FFFFFF, 0xFFF8FAFC, 0xFFA5B4C8, 0xFF86EFAC, 0xFFFCD34D, 0xFFFEF08A, 0xFFFCA5A5, 0xFFA5F3FC,
                0x26FFFFFF, 0x50FFFFFF, 0x26FFFFFF, 0x14FFFFFF, 0x1AFFFFFF, 0x40FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    private static void liquid() {
        set(0xFF161C26, 0x80FFFFFF, 0xFFFFFFFF, 0xFFC9D3E0, 0xFF86F2B4, 0xFFFFD98A, 0xFFFFF1A8, 0xFFFF9EAE, 0xFFB8F1FF,
                0x2EFFFFFF, 0x70FFFFFF, 0x2EFFFFFF, 0x16FFFFFF, 0x20FFFFFF, 0x50FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    private static void neon() {
        set(0xFF000000, ACCENT_A, 0xFFFFFFFF, 0xFF7A7A8C, 0xFF39FF14, 0xFFFFD000, 0xFFFFF200, 0xFFFF2E63, ACCENT_B,
                0xFF000000, 0xFF2A2A3A, 0xFF000000, 0xFF0A0A0A, 0xFF0A0A0A, 0xFF1E1E26, ACCENT_B, 0xFFFFFFFF);
    }

    private static void minimal() {
        set(0xFF18181B, 0xFF27272A, 0xFFF4F4F5, 0xFF8B8B94, 0xFF22C55E, 0xFFEAB308, 0xFFFACC15, 0xFFEF4444, 0xFF38BDF8,
                0xFF27272A, 0xFF3F3F46, 0xFF27272A, 0xFF1F1F22, 0xFF232326, 0xFF3F3F46, 0xFFF4F4F5, 0xFFFFFFFF);
    }

    // flat and opaque: dark grey, one solid accent colour (the first of the pair), nothing see-through
    private static void plain() {
        ACCENT_B = ACCENT_A;
        set(0xFF202020, 0xFF3C3C3C, 0xFFE8E8E8, 0xFF9C9C9C, 0xFF6CC24A, 0xFFE0B040, 0xFFF0D060, 0xFFE05A4F, 0xFF5FB8D0,
                0xFF151515, 0xFF4A4A4A, 0xFF333333, 0xFF2A2A2A, 0xFF181818, 0xFF3C3C3C, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    // graphite glass, hairline borders, soft colours
    private static void sleek() {
        set(0xFF101217, 0x1FFFFFFF, 0xFFF3F5F8, 0xFF8C94A3, 0xFF3DDC97, 0xFFF6C453, 0xFFFDE68A, 0xFFF87171, 0xFF7DD3FC,
                0x12FFFFFF, 0x24FFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x0EFFFFFF, 0x1AFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    // fully bright colour at that hue, saturation 0.72
    public static int hue(int degrees) {
        float h = Math.floorMod(degrees, 360) / 60.0F;
        float s = 0.72F;
        float f = h - (int) h;
        int p = Math.round(255 * (1 - s)), q = Math.round(255 * (1 - s * f)), t = Math.round(255 * (1 - s * (1 - f)));
        int r, g, b;
        switch ((int) h) {
            case 0 -> { r = 255; g = t; b = p; }
            case 1 -> { r = q; g = 255; b = p; }
            case 2 -> { r = p; g = 255; b = t; }
            case 3 -> { r = p; g = q; b = 255; }
            case 4 -> { r = t; g = p; b = 255; }
            default -> { r = 255; g = p; b = q; }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static final String[] TITLE_COLOR_NAMES = {"Look default", "White", "Gold", "Accent 1", "Accent 2", "Aqua"};

    public static void titleColor(int choice) {
        switch (choice) {
            case 1 -> TITLE = 0xFFFFFFFF;
            case 2 -> TITLE = GOLD;
            case 3 -> TITLE = ACCENT_A;
            case 4 -> TITLE = ACCENT_B;
            case 5 -> TITLE = AQUA;
            default -> {
            }
        }
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static int lighten(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r += (int) ((255 - r) * 0.18);
        g += (int) ((255 - g) * 0.18);
        b += (int) ((255 - b) * 0.18);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int argb, float amount) {
        return lerp(argb, 0xFF000000, amount);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= (Math.round(ca + (cb - ca) * t) & 255) << shift;
        }
        return out;
    }
}
