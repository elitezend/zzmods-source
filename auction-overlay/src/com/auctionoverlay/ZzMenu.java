package com.auctionoverlay;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

// The zzmods part of the game menu (Esc): our logo (see-through) with the Announcements button under it, drawn exactly like
// Essential's buttons (black outline, light top/left edge, darker bottom, shadowed text). With Essential installed it
// lines up with Essential's own buttons (Invite, Social...) - read from the screen every frame, so it fits at every GUI
// scale and in Essential's compact layout - right above them with a line between (or below them when there's no room
// above). Without Essential: on the middle right. The click is caught straight from the mouse (mixin.MouseHandlerMixin),
// before Essential's menu layer, which otherwise swallows clicks in that corner.
public final class ZzMenu {
    public static final int W = 80, BTN_H = 20, LOGO_W = 64, LOGO_GAP = 4;
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("auctionoverlay", "textures/gui/zzmods_logo.png");
    private static final int LOGO_TEX_W = 256, LOGO_TEX_H = 141;
    // Essential's colours (measured from its game menu)
    private static final int FILL = 0xFF383838, FILL_HOVER = 0xFF444444, LIGHT = 0xFF505050, LIGHT_HOVER = 0xFF5E5E5E,
            DARK = 0xFF2A2A2A, DARK_HOVER = 0xFF333333, RIGHT = 0xFF2B2B2B, TEXT = 0xFFE5E5E5, SHADOW = 0xFF181818,
            RED = 0xFFE5484D;
    // the ids of Essential's buttons on the right of the game menu
    private static final Set<String> ESSENTIAL_COLUMN = Set.of("invite_host", "social", "wardrobe", "wardrobe_2", "pictures", "settings");

    // where the button was drawn last, and on which game menu
    private static Box lastBox;
    private static Screen lastScreen;

    private ZzMenu() {}

    static boolean essential() {
        return FabricLoader.getInstance().isModLoaded("essential");
    }

    // where everything goes: the button (x, y, w), the logo above or below it (0 high = no room for it), the line (-1 none)
    public record Box(int x, int w, int btnY, int logoX, int logoY, int logoW, int logoH, int lineY) {
        public boolean onButton(double mx, double my) {
            return mx >= x && mx < x + w && my >= btnY && my < btnY + BTN_H;
        }
    }

    public static Box layout(int width, int height, List<? extends GuiEventListener> children) {
        int[] ess = essentialColumn(width, children);
        if (ess != null) {
            int x = ess[0], w = ess[2] - ess[0];
            int logoW = Math.max(0, Math.min(LOGO_W, w - 16));
            int logoH = logoW * LOGO_TEX_H / LOGO_TEX_W;
            if (ess[1] - 9 - BTN_H >= 2) { // above Essential's buttons, with the line in between
                int btnY = ess[1] - 9 - BTN_H;
                int room = btnY - LOGO_GAP - 2;
                if (logoH > room) { // not much room above: a smaller logo, or none
                    logoH = Math.max(0, room);
                    logoW = logoH * LOGO_TEX_W / LOGO_TEX_H;
                }
                if (logoH < 12) logoH = 0;
                return new Box(x, w, btnY, x + (w - logoW) / 2, btnY - LOGO_GAP - logoH, logoW, logoH, ess[1] - 5);
            }
            // no room above (a big GUI scale): under them instead
            int btnY = ess[3] + 9;
            int logoY = btnY + BTN_H + LOGO_GAP;
            int room = height - 2 - logoY;
            if (logoH > room) {
                logoH = Math.max(0, room);
                logoW = logoH * LOGO_TEX_W / LOGO_TEX_H;
            }
            if (logoH < 12) logoH = 0;
            return new Box(x, w, btnY, x + (w - logoW) / 2, logoY, logoW, logoH, ess[3] + 4);
        }
        // no Essential: in the middle of the space right of the vanilla buttons
        int menuRight = width / 2 + 102; // the vanilla buttons are 204 wide
        int x = Math.max(menuRight + 4, Math.min((menuRight + width) / 2 - W / 2, width - W - 2));
        int logoH = LOGO_W * LOGO_TEX_H / LOGO_TEX_W;
        int btnY = Math.max(2 + logoH + LOGO_GAP, height / 2 - (logoH + LOGO_GAP + BTN_H) / 2 + logoH + LOGO_GAP);
        return new Box(x, W, btnY, x + (W - LOGO_W) / 2, btnY - LOGO_GAP - logoH, LOGO_W, logoH, -1);
    }

    // Essential's buttons on the right ({left, top, right, bottom}), or null when Essential's menu isn't there. Essential
    // puts an invisible vanilla button (gg.essential.gui.proxies.MenuButtonProxy) in the game menu exactly where each of
    // its buttons is drawn, so their positions are read from those.
    private static int[] essentialColumn(int width, List<? extends GuiEventListener> children) {
        if (!essential() || children == null) return null;
        int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
        for (GuiEventListener e : children) {
            if (!(e instanceof AbstractWidget w) || w.getWidth() <= 0 || w.getHeight() <= 0) continue;
            if (!e.getClass().getName().startsWith("gg.essential.gui.proxies.MenuButtonProxy")) continue;
            if (w.getX() + w.getWidth() / 2 < width / 2) continue; // the right-hand column only (there's a Wardrobe button on the left too)
            String id = essentialId(e);
            if (id != null && !ESSENTIAL_COLUMN.contains(id)) continue;
            l = Math.min(l, w.getX());
            t = Math.min(t, w.getY());
            r = Math.max(r, w.getX() + w.getWidth());
            b = Math.max(b, w.getY() + w.getHeight());
        }
        return l == Integer.MAX_VALUE || r - l < 16 ? null : new int[]{l, t, r, b};
    }

    private static Method idMethod;
    private static boolean idMissing;

    private static String essentialId(Object proxy) {
        if (idMissing) return null;
        try {
            if (idMethod == null) idMethod = proxy.getClass().getMethod("getEssentialId");
            Object id = idMethod.invoke(proxy);
            return id instanceof String s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            idMissing = true;
            return null;
        }
    }

    // a mouse button from the game (MouseHandler.onButton, before any screen or Essential sees it): true = it was ours
    public static boolean onMouse(int button, int action) {
        if (button != 0 || action != 1 || lastBox == null) return false;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null || screen != lastScreen) return false;
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
        if (!lastBox.onButton(mx, my)) return false;
        click();
        mc.gui.setScreen(new AnnouncementsScreen(screen));
        return true;
    }

    public static void click() {
        AuctionState.click();
    }

    public static boolean show() {
        return AuctionMod.announcementsButton();
    }

    // draws it (on the game menu `screen`) and remembers where it went for the click
    public static Box render(GuiGraphicsExtractor g, Font font, Screen screen, int mouseX, int mouseY) {
        Box p = layout(screen.width, screen.height, screen.children());
        lastBox = p;
        lastScreen = screen;
        int x = p.x(), w = p.w(), by = p.btnY();
        // our logo, so it's clear this part is zzmods (with a soft shadow, like the text)
        if (p.logoH() > 0) {
            logo(g, p.logoX() + 1, p.logoY() + 1, p.logoW(), p.logoH(), 0x99000000);
            logo(g, p.logoX(), p.logoY(), p.logoW(), p.logoH(), 0xFFFFFFFF);
        }
        // the button
        boolean hover = p.onButton(mouseX, mouseY);
        button(g, x, by, w, BTN_H, hover);
        String label = "Announcements";
        int lw = font.width(label);
        int tc = hover ? 0xFFFFFFFF : TEXT;
        if (6 + lw + 18 <= w) { // room for the bell, like Essential's icons
            shadowed(g, font, label, x + 6, by + 6, tc);
            bell(g, x + w - 13, by + 5, SHADOW, 1);
            bell(g, x + w - 13, by + 5, tc, 0);
        } else if (lw + 8 <= w) {
            shadowed(g, font, label, x + (w - lw) / 2, by + 6, tc);
        } else if (lw * 0.8F + 8 <= w) { // a wide font: squeeze it a little to fit the button
            float k = (w - 8) / (float) lw;
            g.pose().pushMatrix();
            g.pose().translate(x + 4, by + 6);
            g.pose().scale(k, 1);
            shadowed(g, font, label, 0, 0, tc);
            g.pose().popMatrix();
        } else { // Essential's compact (icons only) layout: just the bell
            bell(g, x + (w - 8) / 2, by + 5, SHADOW, 1);
            bell(g, x + (w - 8) / 2, by + 5, tc, 0);
        }
        int n = Announcements.unread();
        if (n > 0) { // a red count, like a notification badge
            String c = n > 9 ? "9+" : String.valueOf(n);
            int bw = Math.max(9, font.width(c) + 4), bx = x + w - bw + 4, byy = by - 4;
            g.fill(bx - 1, byy - 1, bx + bw + 1, byy + 10, 0xFF000000);
            g.fill(bx, byy, bx + bw, byy + 9, RED);
            g.text(font, c, bx + (bw - font.width(c)) / 2 + 1, byy + 1, 0xFFFFFFFF, false);
        }
        if (p.lineY() >= 0) { // the line between zzmods and Essential
            g.fill(x, p.lineY(), x + w, p.lineY() + 1, 0xFF000000);
            g.fill(x + 1, p.lineY() + 1, x + w + 1, p.lineY() + 2, 0x55000000);
        }
        return p;
    }

    // The logo is read straight from our own jar, not from Minecraft's resources: without Fabric API those never load
    // and it showed as the pink and black "missing texture" square. It's shrunk (averaging, like a picture viewer) to the
    // exact pixels it's shown at, so it stays smooth without needing a blurry texture setting.
    private static final Identifier LOGO_BAKED = Identifier.fromNamespaceAndPath("auctionoverlay", "zzmods_logo_baked");
    private static NativeImage logoSource;
    private static boolean logoFailed;
    private static int logoPW, logoPH;

    private static boolean bakeLogo(int pw, int ph) {
        if (logoPW == pw && logoPH == ph) return true;
        if (logoFailed) return false;
        try {
            if (logoSource == null) {
                try (InputStream in = ZzMenu.class.getResourceAsStream("/assets/auctionoverlay/textures/gui/zzmods_logo.png")) {
                    if (in == null) {
                        logoFailed = true;
                        return false;
                    }
                    logoSource = NativeImage.read(in);
                }
            }
            int sw = logoSource.getWidth(), sh = logoSource.getHeight();
            NativeImage out = new NativeImage(pw, ph, true);
            for (int y = 0; y < ph; y++) {
                int y0 = y * sh / ph, y1 = Math.max(y0 + 1, (y + 1) * sh / ph);
                for (int x = 0; x < pw; x++) {
                    int x0 = x * sw / pw, x1 = Math.max(x0 + 1, (x + 1) * sw / pw);
                    long a = 0, r = 0, gr = 0, b = 0, n = 0;
                    for (int yy = y0; yy < y1; yy++) {
                        for (int xx = x0; xx < x1; xx++) {
                            int c = logoSource.getPixel(xx, yy), ca = c >>> 24;
                            a += ca;
                            r += ((c >> 16) & 255) * ca;
                            gr += ((c >> 8) & 255) * ca;
                            b += (c & 255) * ca;
                            n++;
                        }
                    }
                    int oa = (int) (a / n);
                    int c = a == 0 ? 0 : oa << 24 | (int) (r / a) << 16 | (int) (gr / a) << 8 | (int) (b / a);
                    out.setPixel(x, y, c);
                }
            }
            // registering under the same name replaces (and closes) the previous size
            Minecraft.getInstance().getTextureManager().register(LOGO_BAKED, new DynamicTexture(() -> "zzmods logo", out));
            logoPW = pw;
            logoPH = ph;
            return true;
        } catch (Exception e) {
            logoFailed = true;
            return false;
        }
    }

    private static void logo(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        int pw = Math.max(1, (int) Math.round(w * gs)), ph = Math.max(1, (int) Math.round(h * gs));
        if (!bakeLogo(pw, ph)) return;
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO_BAKED, x, y, 0.0F, 0.0F, w, h, pw, ph, pw, ph, color);
    }

    // Essential's button: black outline, light top and left edge, darker right edge and bottom
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? FILL_HOVER : FILL);
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, hover ? DARK_HOVER : DARK);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, hover ? LIGHT_HOVER : LIGHT);
        g.fill(x + 1, y + 2, x + 2, y + h - 3, hover ? LIGHT_HOVER : LIGHT);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, hover ? DARK_HOVER : RIGHT);
    }

    // text with Essential's dark drop shadow
    public static void shadowed(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x + 1, y + 1, SHADOW, false);
        g.text(font, s, x, y, color, false);
    }

    // a small bell, like Essential's icons on the right of its buttons
    private static void bell(GuiGraphicsExtractor g, int x, int y, int c, int o) {
        x += o;
        y += o;
        g.fill(x + 3, y, x + 5, y + 1, c);
        g.fill(x + 2, y + 1, x + 6, y + 2, c);
        g.fill(x + 1, y + 2, x + 7, y + 7, c);
        g.fill(x, y + 7, x + 8, y + 8, c);
        g.fill(x + 3, y + 9, x + 5, y + 10, c);
    }
}
