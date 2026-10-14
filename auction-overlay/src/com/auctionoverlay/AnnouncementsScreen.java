package com.auctionoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// The "Announcements" page from the game menu: updates, discount offers and news staff post on the dashboard.
// Codes copy with one click, links open zzmods.com. Opening it marks everything as read.
public class AnnouncementsScreen extends Screen {
    private static final int W = 240, PAD = 8;
    // Essential's flat dark look (same as the ZZMODS section in the game menu)
    private static final int BG = 0xFF181818, CARD = 0xFF232323, LINE = 0xFF3A3A3A, HEAD = 0xFF111111, TEXT = 0xFFE4E4E4,
            MUTED = 0xFF8E8E8E, BRAND = 0xFF2E9BFF, BLUE = 0xFF2A5DA8,
            BLUE_HOVER = 0xFF3970C2, RED = 0xFFE5484D, GOLD = 0xFFF2B33D, GREEN = 0xFF46C46B;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final Set<String> fresh = new HashSet<>(); // unread when the page was opened: marked NEW
    private final List<int[]> hits = new ArrayList<>(); // x, y, w, h, item index, action (0 copy code, 1 open link)
    private float scale = 1;
    private int px, py, h, first;
    private String copied = "";
    private long copiedAt;

    public AnnouncementsScreen(Screen parent) {
        super(Component.literal("Announcements"));
        this.parent = parent;
        List<String> seen = AuctionState.INSTANCE.config.annSeen;
        for (Announcements.Item i : Announcements.all()) if (seen == null || !seen.contains(i.id())) fresh.add(i.id());
        Announcements.markRead();
    }

    private void layout() {
        // just as tall as what's in it (up to 210)
        int need = 25 + PAD;
        if (Announcements.all().isEmpty()) need += 52;
        for (Announcements.Item it : Announcements.all()) need += itemHeight(it, W - PAD * 2 - 12);
        h = Math.max(90, Math.min(Math.min(210, height - 16), need));
        scale = Style.popupScale(width, height, W, h, 0.5F, 0.8F);
        px = ((int) (width / scale) - W) / 2;
        py = ((int) (height / scale) - h) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    private List<String> lines(String body, int w) {
        List<String> out = new ArrayList<>();
        for (String para : body.split("\n")) {
            if (para.isBlank()) continue;
            out.addAll(wrap(para.trim(), w));
        }
        return out;
    }

    private int itemHeight(Announcements.Item it, int tw) {
        int n = lines(it.body(), tw).size();
        return 12 + 12 + n * 10 + (it.code().isEmpty() && it.url().isEmpty() ? 0 : 22) + 10;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        hits.clear();
        int mx = (int) (realMouseX / scale), my = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.fill(px + 2, py + 2, px + W + 2, py + h + 2, 0x66000000); // drop shadow
        g.fill(px - 1, py - 1, px + W + 1, py + h + 1, 0xFF000000); // black outline, like Essential's buttons
        g.fill(px, py, px + W, py + h, BG);
        // header bar: ZZMODS | Announcements
        g.fill(px + 1, py + 1, px + W - 1, py + 24, HEAD);
        g.fill(px + 1, py + 24, px + W - 1, py + 25, LINE);
        g.fill(px + 1, py + 1, px + 3, py + 24, BRAND);
        ZzMenu.shadowed(g, font, "ZZMODS", px + PAD, py + 9, BRAND);
        g.text(font, "ZZMODS", px + PAD + 1, py + 9, BRAND, false);
        int sx = px + PAD + font.width("ZZMODS") + 8;
        g.fill(sx, py + 7, sx + 1, py + 18, LINE);
        ZzMenu.shadowed(g, font, "Announcements", sx + 8, py + 9, TEXT);
        int tw = W - PAD * 2;
        String back = "Esc to go back";
        if (sx + 8 + font.width("Announcements") + 10 + font.width(back) <= px + W - PAD) {
            g.text(font, back, px + W - PAD - font.width(back), py + 9, MUTED, false);
        }
        List<Announcements.Item> all = Announcements.all();
        int top = py + 25 + 6, bottom = py + h - PAD + 6;
        if (all.isEmpty()) {
            String a = "No announcements right now.";
            ZzMenu.shadowed(g, font, cut(a, W - 8), px + Math.max(4, (W - font.width(a)) / 2), top + 12, TEXT);
            int ey = top + 26;
            for (String l : wrap("Updates and offers from zzmods show up here.", W - 16)) {
                g.text(font, l, px + (W - font.width(l)) / 2, ey, MUTED, false);
                ey += 10;
            }
        }
        first = Math.max(0, Math.min(first, Math.max(0, all.size() - 1)));
        int y = top;
        for (int i = first; i < all.size(); i++) {
            Announcements.Item it = all.get(i);
            int ih = itemHeight(it, tw - 12);
            if (y + ih > bottom && i > first) break;
            g.fill(px + PAD, y, px + PAD + tw, y + ih - 6, CARD);
            g.outline(px + PAD, y, tw, ih - 6, LINE);
            int x = px + PAD + 6, w = tw - 12, yy = y + 5;
            String kind = it.kind().equals("offer") ? "OFFER" : it.kind().equals("update") ? "UPDATE" : "NEWS";
            int kc = it.kind().equals("offer") ? GOLD : it.kind().equals("update") ? GREEN : BRAND;
            g.text(font, kind, x, yy, kc, false);
            int kx = x + font.width(kind) + 6;
            if (fresh.contains(it.id())) { // a small red NEW tag
                g.fill(kx - 2, yy - 2, kx + font.width("NEW") + 2, yy + 9, RED);
                g.text(font, "NEW", kx, yy, 0xFFFFFFFF, false);
            }
            if (it.at() > 0) {
                String d = DAY.format(Instant.ofEpochMilli(it.at()));
                g.text(font, d, x + w - font.width(d), yy, MUTED, false);
            }
            yy += 12;
            g.text(font, cut(it.title(), w), x, yy, 0xFFFFFFFF, false);
            yy += 12;
            for (String l : lines(it.body(), w)) {
                g.text(font, l, x, yy, TEXT, false);
                yy += 10;
            }
            if (!it.code().isEmpty() || !it.url().isEmpty()) {
                int bx = x;
                if (!it.code().isEmpty()) {
                    String label = copied.equals(it.code()) && System.currentTimeMillis() - copiedAt < 2500 ? "Copied!" : "Code: " + it.code() + "  (copy)";
                    int bw = Math.min(w / 2, font.width(label) + 16);
                    boolean hover = mx >= bx && mx < bx + bw && my >= yy + 2 && my < yy + 20;
                    flatButton(g, bx, yy + 2, bw, 18, label, hover, false);
                    hits.add(new int[]{bx, yy + 2, bw, 18, i, 0});
                    bx += bw + 6;
                }
                if (!it.url().isEmpty()) {
                    String label = it.button().isEmpty() ? "Open" : it.button();
                    int bw = Math.min(x + w - bx, font.width(label) + 20);
                    boolean hover = mx >= bx && mx < bx + bw && my >= yy + 2 && my < yy + 20;
                    flatButton(g, bx, yy + 2, bw, 18, label, hover, true);
                    hits.add(new int[]{bx, yy + 2, bw, 18, i, 1});
                }
            }
            y += ih;
        }
        if (first > 0) {
            String up = "▲ scroll up";
            g.text(font, up, px + (W - font.width(up)) / 2, top - 8, MUTED, false);
        }
        g.pose().popMatrix();
    }

    // everything here is drawn in Minecraft's font, so it's measured with it too (not the Sleek look's smooth font)
    private String cut(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, maxWidth);
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > width && !line.isEmpty()) {
                out.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) out.add(line);
        List<String> fit = new ArrayList<>(); // a single word longer than the line is cut
        for (String l : out) fit.add(font.width(l) > width ? cut(l, width) : l);
        return fit;
    }

    // Essential's buttons: gray, or blue for links (like its "Invite to Game")
    private void flatButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean hover, boolean blue) {
        if (blue) {
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? BLUE_HOVER : BLUE);
            g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, hover ? 0xFF2A63B8 : 0xFF1C4A8C);
            g.fill(x + 1, y + 1, x + w - 2, y + 2, hover ? 0xFF6C9FE6 : 0xFF4A7FC9);
            g.fill(x + 1, y + 2, x + 2, y + h - 3, hover ? 0xFF6C9FE6 : 0xFF4A7FC9);
        } else {
            ZzMenu.button(g, x, y, w, h, hover);
        }
        String t = cut(label, w - 6);
        ZzMenu.shadowed(g, font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() / scale, my = event.y() / scale;
        for (int[] b : hits) {
            if (event.button() == 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                Announcements.Item it = Announcements.all().get(b[4]);
                AuctionState.click();
                if (b[5] == 0) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(it.code());
                    copied = it.code();
                    copiedAt = System.currentTimeMillis();
                } else {
                    Online.open(it.url());
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double scrollX, double scrollY) {
        first = Math.max(0, Math.min(Announcements.all().size() - 1, first - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
