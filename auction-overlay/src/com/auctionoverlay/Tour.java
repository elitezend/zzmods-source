package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// The hands-on tour (every edition; steps for things an edition doesn't have are left out). Each step points at a
// real part of the panel, dims the rest, and moves on by itself when you do the thing (open the panel, pick an item,
// start, bid, sell, open settings). The test auction it runs isn't saved to sales and isn't posted to Discord.
public final class Tour {
    // the tour's highlight colour: the accent, or a bright blue when the accent is near-white (Ice / White) so the
    // ring and the pointer line still stand out
    static int glow() {
        int c = Colors.ACCENT_B, r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return Math.max(r, Math.max(gg, b)) - Math.min(r, Math.min(gg, b)) < 60 ? 0xFF3EC6FF : c;
    }

    enum Step {
        BIND(null, "Pick your key", "Press the key you want to open the auction panel with.", false),
        OPEN(null, "Open the panel", "Tap {key} (or F7) to open the auction panel.", false),
        PICK("grid", "Pick something to sell", "Click any item in the grid. Scroll for more, or type in the search box.", false),
        RULES("rules", "Set the rules", "Time is minutes : seconds. Min pay is the lowest first bid, Raise is how much each bid "
                + "has to beat the last one. " + (Edition.PRO ? "Reserve, Buy now and Winners are here too. " : "")
                + "The defaults are fine for a test.", true),
        START("start", "Start it", Edition.PRO ? "Press Start auction. (+ Queue would line it up to run after the current one.)"
                : "Press Start auction.", false),
        BID("bidRow", "Take a bid", "Real bids come in by themselves when players /pay you. Try one by hand: type any name "
                + "and an amount, then press Bid.", false),
        MANAGE("liveButtons", "Run it", "+30s adds time, Cancel stops with no winner. Press Stop & sell to end this test now.", false),
        RESULT("result", "Sold!", (Edition.COPY_TPA ? "The winner's \"tpa name\" is copied: press /, then Ctrl+V. "
                : "The winner and what they paid show here - send them the item. ")
                + (Edition.PRO ? "Anyone else who paid shows in a refund list - click a name to copy their pay back. " : "")
                + "Run again repeats the lot.", true),
        GEAR("gear", "Settings", "Click the gear to open the settings.", false),
        LOOK("settings:Look", "Settings: Look", Edition.LOOKS ? "Look changes the whole style - try Sleek for smooth and rounded, "
                + "or Classic for Minecraft's own look. Accent colours, corners, the title and its colour are here too."
                : "Pick the accent colour here. Basic and Premium add more looks, like Sleek and Classic.", true),
        CARD("settings:Card", "Settings: in-game card", "The small card that shows the timer and top bid while the panel is "
                + "closed. Turn it on or off, put it in a corner, change its size and what it shows.", true),
        AUCTION("settings:Auction", "Settings: Auction", Edition.PRO ? "Panel size (or Ctrl + / -), how long a late bid resets "
                + "the timer, the +time button, sounds and the 3-2-1 bells, the queue break, and this tutorial."
                : "Your open key, panel size (or Ctrl + / -), how long a late bid resets the timer, the +time button, sounds, "
                + "the OBS text file for your stream, and this tutorial.", true),
        KEYS("settings:Keys", "Settings: Keys", "Keys for add time, stop & sell, cancel and copying a chat announcement. "
                + "They work while you play, without opening the panel. Backspace clears one.", true),
        STREAM("settings:Stream", "Settings: Stream", "OBS overlay: press Folder and add overlay.html to OBS as a Browser "
                + "source. Paste a Discord webhook to post your auctions. Bidders and Sales are next to it.", true),
        DONE(null, "You're all set!", "Close the panel with {key}; while an auction runs, a small card keeps the timer and "
                + "top bid on screen. Replay this tour from Settings > Auction > Tutorial.", true);

        final String target, title, text;
        final boolean manual; // needs a Next press instead of finishing by itself

        Step(String target, String title, String text, boolean manual) {
            this.target = target;
            this.title = title;
            this.text = text;
            this.manual = manual;
        }
    }

    private static final int CARD_W = 150;
    private static boolean active;
    private static Step step = Step.OPEN;
    private static long stepAt;
    private static String bindMsg = "";
    public static final int SAFETY_KEY = 296; // F7 always opens the panel, whatever key was picked
    private static final List<int[]> HITS = new ArrayList<>(); // x, y, w, h, action (0 skip, 1 next, 2 finish)

    private Tour() {}

    public static boolean active() {
        return active;
    }

    static String targetName() {
        return active ? step.target : null;
    }

    // ---- linking comes first: the tour (and skipping it) needs the mod linked to the player's zzmods account
    private static boolean linking;
    private static Runnable afterLink;
    private static long linkOpenedAt;

    public static boolean linking() {
        return linking;
    }

    // runs then() once this copy is linked; asks for it first if it isn't
    static void requireLink(Runnable then) {
        if (Online.isLinked()) {
            then.run();
            return;
        }
        linking = true;
        afterLink = then;
        bindMsg = "";
        linkOpenedAt = Online.waitingForLink() ? System.currentTimeMillis() : 0;
    }

    private static void linkDone() {
        linking = false;
        Runnable then = afterLink;
        afterLink = null;
        AuctionState.click();
        if (then != null) {
            then.run();
        }
    }

    private static void linkKey(int key) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            Online.startLink();
            linkOpenedAt = System.currentTimeMillis();
            bindMsg = "";
            AuctionState.click();
        } else if (key == InputConstants.KEY_DELETE && Online.linkFailures() >= 3) {
            linkDone(); // zzmods.com can't be reached: don't lock anyone out
        } else if (key == InputConstants.KEY_BACKSPACE) {
            bindMsg = "Linking is needed first - press Enter, then click Link";
        }
    }

    public static void start() {
        requireLink(Tour::begin);
    }

    // after the welcome's Skip: link, pick the key that opens the panel, done
    static void skipButLink() {
        requireLink(() -> Minecraft.getInstance().gui.setScreen(new KeyBindScreen(() -> {
            end();
            Online.note("Open the panel with " + AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey), "F7 opens it too");
        })));
    }

    private static void begin() {
        active = true;
        bindMsg = "";
        go(Step.BIND);
        AuctionState st = AuctionState.INSTANCE;
        if (st.live) {
            st.finish(true);
        }
        st.config.custom = false;
        st.config.ftpTab = false;
        st.config.dutch = false;
        AuctionScreen.prepareForTour();
        // straight after linking: the key question as its own screen (replaces a pause menu left from the browser)
        Minecraft.getInstance().gui.setScreen(new KeyBindScreen(() -> {
            AuctionScreen.prepareForTour();
            Minecraft.getInstance().gui.setScreen(new AuctionScreen());
            go(Step.PICK);
        }));
    }

    // the first step listens for the key to use (handled before Minecraft's own keys see it)
    public static boolean capturingKey() {
        return linking || (active && step == Step.BIND);
    }

    public static void keyPressed(int key) {
        if (linking) {
            linkKey(key);
            return;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            return;
        }
        // (the key screen was closed somehow: the card at the top asks instead - Backspace picks Backspace here, it
        // doesn't skip, so nobody ends up without a key)
        String used = minecraftUse(key);
        if (used != null) {
            bindMsg = AuctionState.keyName(key) + " is Minecraft's \"" + used + "\" key - pick another one";
            return;
        }
        AuctionState st = AuctionState.INSTANCE;
        st.config.toggleKey = key;
        st.save();
        AuctionMod.consumeKey(key);
        bindMsg = "";
        AuctionState.click();
        AuctionScreen.prepareForTour();
        Minecraft.getInstance().gui.setScreen(new AuctionScreen());
        go(Step.PICK);
    }

    // which of Minecraft's controls already uses this key (so nobody binds W and can't walk)
    static String minecraftUse(int key) {
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getName();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (km.saveString().equals(name)) {
                return Component.translatable(km.getName()).getString();
            }
        }
        return null;
    }

    public static void skip() {
        end();
    }

    // stop without marking it done (used by the reset button)
    static void cancel() {
        active = false;
    }

    private static void end() {
        active = false;
        AuctionState st = AuctionState.INSTANCE;
        st.config.tutorialDone = true;
        st.save();
    }

    private static void go(Step next) {
        step = next;
        stepAt = System.currentTimeMillis();
    }

    private static void next() {
        if (step == Step.DONE) {
            end();
            Minecraft.getInstance().gui.setScreen(null);
            System.setProperty("zzmods.startGambleTour", "now");
            return;
        }
        Step n = Step.values()[step.ordinal() + 1];
        while (!has(n)) {
            n = Step.values()[n.ordinal() + 1];
        }
        go(n);
    }

    // steps about parts this edition doesn't have are skipped (Keys and Stream are Premium settings tabs)
    private static boolean has(Step s) {
        return Edition.PRO || (s != Step.KEYS && s != Step.STREAM);
    }

    private static String text(Step s) {
        if (!onTab(s)) {
            return !AuctionScreen.settingsShown() ? "Click the gear at the top right of the panel to open the settings."
                    : "Click the " + s.target.substring(9) + " tab.";
        }
        return s.text.replace("{key}", AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey));
    }

    // settings steps first need the right tab open; everything else is always "there"
    private static boolean onTab(Step s) {
        if (s.target == null || !s.target.startsWith("settings:")) {
            return true;
        }
        return AuctionScreen.settingsShown() && s.target.substring(9).equals(AuctionScreen.currentTab());
    }

    // checks whether the current step was done
    public static void tick() {
        if (linking && Online.isLinked()) {
            Online.note("Linked to " + AuctionState.INSTANCE.config.linkedEmail, "Thanks! On to the tour");
            linkDone();
            return;
        }
        if (!active) {
            return;
        }
        AuctionState st = AuctionState.INSTANCE;
        boolean panel = Minecraft.getInstance().gui.screen() instanceof AuctionScreen;
        boolean done = switch (step) {
            case OPEN -> panel;
            case PICK -> AuctionScreen.hasSelection();
            case START -> st.live;
            case BID -> !st.bids.isEmpty() || !st.live;
            case MANAGE -> !st.live;
            case GEAR -> AuctionScreen.settingsShown();
            default -> false;
        };
        if (done) {
            AuctionState.click();
            next();
        }
    }

    // ---- while the panel is closed: a card at the top of the screen with a pressable key

    public static void renderHud(GuiGraphicsExtractor g) {
        if (linking && Minecraft.getInstance().gui.screen() == null) {
            renderLink(g);
            return;
        }
        if (!active || Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String key = AuctionState.keyName(AuctionState.INSTANCE.config.toggleKey);
        String title = "AUCTION OVERLAY TOUR";
        boolean bind = step == Step.BIND;
        String line = bind ? "Which key should open the auction panel?"
                : step == Step.OPEN ? "Tap your key to open the auction panel" : "Tap your key to reopen the panel and keep going";
        String hint = bind ? (bindMsg.isEmpty() ? "Press it now - F7 always opens it too" : bindMsg) : "F7 opens it too";
        String skip = bind ? "You can change it later in Settings > Auction" : "Backspace skips the tour";
        int w = Math.max(Math.max(Txt.width(font, title), Txt.width(font, line)), Math.max(Txt.width(font, hint), Txt.width(font, skip))) + 24;
        int x = (g.guiWidth() - w) / 2, y = 12;
        Style.panel(g, x, y, w, 88, 240);
        Txt.center(g, font, title, x + w / 2, y + 8, glow());
        Txt.center(g, font, line, x + w / 2, y + 20, Colors.TEXT);
        keycap(g, font, bind ? "?" : key, x + w / 2, y + 33, true);
        Txt.center(g, font, hint, x + w / 2, y + 62, bindMsg.isEmpty() ? Colors.MUTED : Colors.RED);
        Txt.center(g, font, skip, x + w / 2, y + 74, Colors.MUTED);
    }

    // the "link your account" card at the top of the screen
    private static void renderLink(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        boolean waiting = linkOpenedAt > 0;
        boolean offline = Online.linkFailures() >= 3;
        String title = "AUCTION OVERLAY  ·  LINK YOUR ACCOUNT";
        String l1 = "First, link this mod to your zzmods account.";
        String l2 = "It shares your Minecraft name + play stats with your account.";
        String hint = !bindMsg.isEmpty() ? bindMsg
                : offline ? "Can't reach zzmods.com - Enter tries again, Delete carries on for now"
                : waiting ? "Sign in and click Link on the page that opened - waiting..." : "Press Enter to open zzmods.com";
        int w = Math.max(Math.max(Txt.width(font, title), Txt.width(font, l1)), Math.max(Txt.width(font, l2), Txt.width(font, hint))) + 24;
        int x = (g.guiWidth() - w) / 2, y = 12;
        Style.panel(g, x, y, w, 88, 240);
        Txt.center(g, font, title, x + w / 2, y + 8, glow());
        Txt.center(g, font, l1, x + w / 2, y + 20, Colors.TEXT);
        Txt.center(g, font, l2, x + w / 2, y + 31, Colors.MUTED);
        keycap(g, font, "Enter", x + w / 2, y + 44, !waiting);
        Txt.center(g, font, hint, x + w / 2, y + 72, !bindMsg.isEmpty() || offline ? Colors.RED : waiting ? Colors.YELLOW : Colors.MUTED);
    }

    // a keyboard key that pushes down and up
    static void keycap(GuiGraphicsExtractor g, Font font, String label, int cx, int y, boolean animate) {
        int w = Math.max(30, Txt.width(font, label) + 14), h = 18;
        int x = cx - w / 2;
        long t = System.currentTimeMillis() % 1400;
        int down = animate && t > 900 && t < 1150 ? 2 : 0;
        if (Style.look() == Look.SLEEK) {
            Smooth.rect(g, x, y + 3, w, h, 5, 0xFF05060A);
            Smooth.rect(g, x, y + down, w, h, 5, down > 0 ? Colors.ACCENT_A : 0xFF2A2E38);
            Smooth.ring(g, x, y + down, w, h, 5, 1, 0x40FFFFFF);
        } else {
            g.fill(x, y + 3, x + w, y + h + 3, 0xFF05060A);
            g.fill(x, y + down, x + w, y + h + down, down > 0 ? Colors.ACCENT_A : 0xFF3A3E48);
            g.fill(x, y + down, x + w, y + down + 1, 0x50FFFFFF);
        }
        Txt.center(g, font, label, cx, y + down + 5, 0xFFFFFFFF);
        if (animate && down == 0 && Style.look() == Look.SLEEK) {
            // ripple in 4 fixed steps so each ring shape is made once and reused
            int step = (int) ((t % 1400) / 350);
            float p = (step + 1) / 4F;
            Smooth.ring(g, x - p * 6, y - p * 6, w + p * 12, h + p * 12, 5 + p * 6, 1.5F, Colors.withAlpha(Colors.ACCENT_A, (int) (90 * (1 - p) + 10)));
        }
    }

    // ---- inside the panel: dim everything but the target, ring it, and explain it next to the panel

    public static void renderPanel(GuiGraphicsExtractor g, Font font, int[] target, int px, int py, int pw, int ph,
                                   int vw, int vh, int mouseX, int mouseY) {
        HITS.clear();
        if (!active || step == Step.OPEN || step == Step.BIND) {
            return;
        }
        if (target != null && step.target != null) {
            int tx = target[0] - 3, ty = target[1] - 3, tw = target[2] + 6, th = target[3] + 6;
            int dim = 0x8C000000;
            g.fill(0, 0, vw, ty, dim);
            g.fill(0, ty + th, vw, vh, dim);
            g.fill(0, ty, tx, ty + th, dim);
            g.fill(tx + tw, ty, vw, ty + th, dim);
            float pulse = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() - stepAt) / 220.0));
            int ring = Colors.withAlpha(glow(), 150 + (int) (105 * pulse));
            if (Style.look() == Look.SLEEK) {
                // fixed shapes, only the brightness pulses (a new size every frame would be re-drawn from scratch)
                Smooth.ring(g, tx, ty, tw, th, 5, 2, ring);
                Smooth.ring(g, tx - 3, ty - 3, tw + 6, th + 6, 7, 1.5F, Colors.withAlpha(glow(), (int) (90 * (1 - pulse))));
            } else {
                g.outline(tx, ty, tw, th, ring);
                g.outline(tx - 1, ty - 1, tw + 2, th + 2, Colors.withAlpha(ring, 90));
            }
        }

        List<String> lines = wrap(font, text(step), CARD_W - 16);
        int ch = 30 + lines.size() * 10 + 24;
        int cx;
        if (px + pw + 8 + CARD_W <= vw) {
            cx = px + pw + 8;
        } else if (px - 8 - CARD_W >= 0) {
            cx = px - 8 - CARD_W;
        } else {
            cx = Math.max(2, Math.min(vw - CARD_W - 2, px + pw - CARD_W - 6));
        }
        int focusY = target != null ? target[1] + target[3] / 2 : py + ph / 2;
        int cy = Math.max(2, Math.min(vh - ch - 2, focusY - ch / 2));
        long age = System.currentTimeMillis() - stepAt;
        float t = Math.min(1, age / 320F), in = 1 - (1 - t) * (1 - t) * (1 - t);
        cx += Math.round((cx > px ? 26 : -26) * (1 - in));
        Style.panel(g, cx, cy, CARD_W, ch, (int) (250 * in));
        int n = step.ordinal() - Step.OPEN.ordinal(), total = Step.values().length - 1 - Step.OPEN.ordinal();
        String count = "STEP " + n + " / " + total;
        Txt.draw(g, font, count, cx + 8, cy + 7, glow(), Style.shadow);
        Txt.draw(g, font, Txt.cut(font, step.title, CARD_W - 16), cx + 8, cy + 18, Colors.TITLE, Style.shadow);
        int y = cy + 31;
        int chars = (int) Math.max(0, (age - 150) / 12);
        for (String l : lines) {
            if (chars <= 0) break;
            Txt.draw(g, font, l.length() > chars ? l.substring(0, chars) : l, cx + 8, y, Colors.TEXT, Style.shadow);
            chars -= l.length() + 1;
            y += 10;
        }
        // progress bar
        int barW = CARD_W - 16;
        g.fill(cx + 8, cy + ch - 21, cx + 8 + barW, cy + ch - 20, 0x30FFFFFF);
        g.fill(cx + 8, cy + ch - 21, cx + 8 + barW * n / total, cy + ch - 20, Colors.ACCENT_A);
        int by = cy + ch - 16;
        button(g, font, cx + 8, by, 52, "Skip tour", Colors.BTN, 0, mouseX, mouseY);
        if (step == Step.DONE) {
            button(g, font, cx + CARD_W - 8 - 62, by, 62, "Finish", Colors.BTN_GO, 2, mouseX, mouseY);
        } else if (step.manual && onTab(step)) {
            button(g, font, cx + CARD_W - 8 - 50, by, 50, "Next", Colors.BTN_GO, 1, mouseX, mouseY);
        } else {
            String hint = "do it to continue";
            Txt.draw(g, font, hint, cx + CARD_W - 8 - Txt.width(font, hint), by + 3, Colors.MUTED, Style.shadow);
        }
        // a line from the card to what it's about
        if (target != null && step.target != null) {
            int fromX = cx > px ? cx : cx + CARD_W;
            int toX = cx > px ? target[0] + target[2] + 3 : target[0] - 3;
            int ly = Math.max(cy + 6, Math.min(cy + ch - 6, focusY));
            if (Style.look() == Look.SLEEK) {
                // a soft glow under a bright line, and a dot at the end
                Smooth.line(g, fromX, ly, toX, focusY, 4F, Colors.withAlpha(glow(), 55));
                Smooth.line(g, fromX, ly, toX, focusY, 1.5F, glow());
                Smooth.circle(g, toX, focusY, 4, Colors.withAlpha(glow(), 70));
                Smooth.circle(g, toX, focusY, 2.2F, glow());
            } else if (ly == focusY) {
                g.fill(Math.min(fromX, toX), ly - 1, Math.max(fromX, toX), ly + 2, Colors.withAlpha(glow(), 60));
                g.fill(Math.min(fromX, toX), ly, Math.max(fromX, toX), ly + 1, glow());
            }
        }
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int color, int action, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        Style.button(g, font, x, y, w, 13, label, color, true, hover);
        HITS.add(new int[]{x, y, w, 13, action});
    }

    // true when the click was on the tour card
    public static boolean click(double mx, double my) {
        for (int[] h : new ArrayList<>(HITS)) {
            if (mx >= h[0] && mx < h[0] + h[2] && my >= h[1] && my < h[1] + h[3]) {
                AuctionState.click();
                if (h[4] == 0 || h[4] == 2) {
                    end();
                } else {
                    next();
                }
                return true;
            }
        }
        return false;
    }

    static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (Txt.width(font, next) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
