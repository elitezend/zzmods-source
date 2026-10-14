package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// The hands-on Gamble Tracker tour (every edition; the text follows what the edition has).
// 1-3: pick the keys (panel, card, next player) right in game. Then the panel opens by itself and the tour points at
// the real thing: the bet range, a few example payments landing in the queue, the Next button (you press it), and each
// settings tab (Look, Card, Keys, About), which opens by itself. Cards slide in, text types out, a pointer bounces at the target.
// If Auction Overlay is installed too, its tour runs first and hands over to this one ("zzmods.startGambleTour").
public final class GambleTour {
    // the tour's highlight colour: the accent, or a bright blue when the accent is near-white (Ice / White) so the
    // ring and the pointer line still stand out
    static int glow() {
        int c = Colors.ACCENT_B, r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return Math.max(r, Math.max(gg, b)) - Math.min(r, Math.min(gg, b)) < 60 ? 0xFF3EC6FF : c;
    }

    enum Step {
        KEY_PANEL(null, "Your panel key", ""),
        KEY_CARD(null, "Your card key", ""),
        KEY_NEXT(null, "Your next-player key", ""),
        BETS("bets", "Your bet range", "Type the Min bet and Max bet here, like 1m and 20m. Only payments inside the range join "
                + "the queue."),
        PAYMENTS("queue", "Payments land here", "Here's what it looks like when people /pay you (these are examples). In range = "
                + "in the queue, in order. Too low or too high = the Refund list, so you know who to pay back."),
        NEXT("nextBtn", "Next player", "After a game, take the first player off: click Next now. In game your {next} key does "
                + "the same without opening the panel."),
        LOOK("tab:look", "Make it yours", Edition.LOOKS ? "The settings are in tabs up top. Look: the Look (try Sleek), accent "
                + "colours, the title, corners, text shadow and how see-through the panel is."
                : "The settings are in tabs up top. Look: the accent colour and how see-through the panel is. Premium adds more "
                + "looks, like Sleek."),
        CARD("tab:card", "The card", "Card: which side of the screen the queue card sits on, up / down, its size, how many "
                + "players it shows, refunds on it and sounds."),
        KEYS("tab:keys", Edition.PAYOUTS ? "Keys & pay" : "Keys", Edition.PAYOUTS
                ? "Keys: every key you just picked can be changed here, plus the Pay winner key, auto-copy of the pay command and "
                + "what the winner gets (1.5x - 3x)."
                : "Keys: every key you just picked can be changed here, and whether Next takes off the first player or everyone."),
        OTHER("tab:about", "About", "About: updates, anonymous stats, your zzmods account and this tour. Click a tab (or press "
                + "Tab) to switch any time."),
        DONE(null, "You're all set!", "{card} shows the queue card while you play, {panel} (or F9) opens this panel, {next} "
                + "takes the next player. Have fun!");

        final String target, title, text;

        Step(String target, String title, String text) {
            this.target = target;
            this.title = title;
            this.text = text;
        }

        boolean bind() {
            return ordinal() <= KEY_NEXT.ordinal();
        }
    }

    public static final int SAFETY_KEY = 298; // F9 always opens the gamble panel
    private static final int CARD_W = 158;
    private static boolean active;
    private static Step step = Step.KEY_PANEL;
    private static long stepAt, inWorldSince, savedAt;
    private static String bindMsg = "", savedText = "";
    private static final List<int[]> HITS = new ArrayList<>();
    private static final List<GambleState.Bet> demo = new ArrayList<>();
    private static GambleState.Bet firstAtNext;
    private static float[][] confetti;

    private GambleTour() {}

    public static boolean active() {
        return active;
    }

    static String targetName() {
        return active ? step.target : null;
    }

    // the panel tab the tour is showing right now ("queue", "look", "card", "keys", "about"), or null
    static String wantTab() {
        if (!active || step.target == null) return null;
        return step.target.startsWith("tab:") ? step.target.substring(4) : "queue";
    }

    // ---- linking comes first: the tour (and skipping it) needs the mod linked to the player's zzmods account
    private static boolean linking;
    private static Runnable afterLink;
    private static long linkOpenedAt;

    public static boolean linking() {
        return linking;
    }

    static void requireLink(Runnable then) {
        if (GambleOnline.isLinked()) {
            then.run();
            return;
        }
        linking = true;
        afterLink = then;
        bindMsg = "";
        linkOpenedAt = GambleOnline.waitingForLink() ? System.currentTimeMillis() : 0;
        stepAt = System.currentTimeMillis();
    }

    private static void linkDone() {
        linking = false;
        Runnable then = afterLink;
        afterLink = null;
        GambleState.INSTANCE.click();
        if (then != null) {
            then.run();
        }
    }

    private static void linkKey(int key) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            GambleOnline.startLink();
            linkOpenedAt = System.currentTimeMillis();
            bindMsg = "";
            GambleState.INSTANCE.click();
        } else if (key == InputConstants.KEY_DELETE && GambleOnline.linkFailures() >= 3) {
            linkDone(); // zzmods.com can't be reached: don't lock anyone out
        } else if (key == InputConstants.KEY_BACKSPACE) {
            bindMsg = "Linking is needed first - press Enter, then click Link";
        }
    }

    // after the welcome's Skip: only the link step, then done
    static void skipButLink() {
        requireLink(GambleTour::finish);
    }

    public static void start() {
        requireLink(GambleTour::begin);
    }

    private static void begin() {
        active = true;
        bindMsg = "";
        confetti = null;
        go(Step.KEY_PANEL);
    }

    public static void skip() {
        finish();
    }

    private static void finish() {
        active = false;
        clearDemo();
        GambleState st = GambleState.INSTANCE;
        st.tutorialDone = true;
        st.save();
    }

    static void cancel() {
        active = false;
        clearDemo();
    }

    private static void go(Step s) {
        step = s;
        stepAt = System.currentTimeMillis();
        GambleState st = GambleState.INSTANCE;
        if (s == Step.BETS) {
            Minecraft.getInstance().gui.setScreen(new GambleScreen()); // the panel opens by itself after the keys
            st.active = true;
        } else if (s == Step.PAYMENTS && demo.isEmpty()) {
            addDemo(st);
        } else if (s == Step.NEXT) {
            List<GambleState.Bet> q = st.queue();
            firstAtNext = q.isEmpty() ? null : q.get(0);
        } else if (s == Step.LOOK) {
            clearDemo();
        } else if (s == Step.DONE) {
            confetti = new float[60][];
        }
    }

    private static void next() {
        if (step == Step.DONE) {
            finish();
            return;
        }
        GambleState.INSTANCE.click();
        go(Step.values()[step.ordinal() + 1]);
    }

    // a few made-up payments so the queue and refund list have something to show
    private static void addDemo(GambleState st) {
        long min = st.min(), max = st.max();
        long lo = min > 0 ? min : 1_000_000L, hi = max > 0 ? max : lo * 20;
        long[] amounts = {Math.max(1, (lo + hi) / 2), lo, Math.max(1, hi / 3), min > 1 ? min / 2 : -1};
        String[] names = {"Demo_Steve", "Demo_Alex", "Demo_Kai", "Demo_Low"};
        for (int i = 0; i < amounts.length; i++) {
            if (amounts[i] > 0) demo.add(st.demoBet(names[i], amounts[i]));
        }
    }

    private static void clearDemo() {
        if (!demo.isEmpty()) {
            GambleState.INSTANCE.demoRemove(demo);
            demo.clear();
        }
    }

    // ---- every frame (from the HUD): first-run welcome, hand-over from the auction tour, auto-advancing

    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        GambleState st = GambleState.INSTANCE;
        if (mc.level == null || mc.player == null) {
            inWorldSince = 0;
            return;
        }
        if (inWorldSince == 0) {
            inWorldSince = System.currentTimeMillis();
        }
        if (GambleOnline.locked() && mc.gui.screen() instanceof GambleScreen && !active && !linking) {
            mc.gui.setScreen(new LicenseScreen());
        }
        if (linking && GambleOnline.isLinked()) {
            GambleOnline.note("Linked to " + GambleOnline.linkedEmail, "Thanks! On to the tour");
            linkDone();
        }
        if (active && step == Step.NEXT) {
            List<GambleState.Bet> q = st.queue();
            if (q.isEmpty() || q.get(0) != firstAtNext) {
                next(); // they pressed Next (or the key)
            }
        }
        boolean waiting = !st.tutorialDone && !active && !linking;
        if (waiting && "now".equals(System.getProperty("zzmods.startGambleTour")) && mc.gui.screen() == null) {
            System.clearProperty("zzmods.startGambleTour"); // the auction tour just finished: carry straight on
            start();
            return;
        }
        boolean auctionBusy = "busy".equals(System.getProperty("zzmods.auctionTutorial"));
        if (waiting && !auctionBusy && mc.gui.screen() == null && System.currentTimeMillis() - inWorldSince > 2500) {
            mc.gui.setScreen(new GambleTutorialScreen());
        }
    }

    // ---- picking the keys (in game, before Minecraft's own keys see them)

    public static boolean capturingKey() {
        return linking || (active && step.bind());
    }

    public static void keyPressed(int key) {
        if (linking) {
            linkKey(key);
            return;
        }
        GambleState st = GambleState.INSTANCE;
        if (key == InputConstants.KEY_BACKSPACE) {
            skip();
            return;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            return;
        }
        boolean keep = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (!keep) {
            String reason = taken(key, st);
            if (reason != null) {
                bindMsg = reason;
                return;
            }
        }
        switch (step) {
            case KEY_PANEL -> {
                if (!keep) st.settingsKey = key;
                savedText = "Panel: " + GambleState.keyName(st.settingsKey);
            }
            case KEY_CARD -> {
                if (!keep) st.openKey = key;
                savedText = "Card: " + GambleState.keyName(st.openKey);
            }
            default -> {
                if (!keep) st.nextKey = key;
                savedText = "Next player: " + (st.nextKey < 0 ? "none" : GambleState.keyName(st.nextKey));
            }
        }
        st.save();
        bindMsg = "";
        savedAt = System.currentTimeMillis();
        next();
    }

    // why a key can't be used, or null
    private static String taken(int key, GambleState st) {
        String name = GambleState.keyName(key);
        if (step != Step.KEY_PANEL && key == st.settingsKey) return name + " already opens the panel - pick another one";
        if (step != Step.KEY_CARD && key == st.openKey) return name + " already shows the card - pick another one";
        if (step != Step.KEY_NEXT && key == st.nextKey) return name + " is already the next-player key - pick another one";
        if (key == SAFETY_KEY) return "F9 always opens the panel already - pick another one";
        String auction = System.getProperty("zzmods.auctionKey");
        if (auction != null && (auction.equals(String.valueOf(key)) || key == 296)) return name + " opens Auction Overlay - pick another one";
        if (GambleState.isMouse(key)) return null;
        String id = InputConstants.Type.KEYSYM.getOrCreate(key).getName();
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (km.saveString().equals(id)) {
                return name + " is Minecraft's \"" + Component.translatable(km.getName()).getString() + "\" key - pick another one";
            }
        }
        return null;
    }

    private static String text(Step s) {
        GambleState st = GambleState.INSTANCE;
        return s.text.replace("{card}", GambleState.keyName(st.openKey)).replace("{panel}", GambleState.keyName(st.settingsKey))
                .replace("{next}", st.nextKey < 0 ? "Next player" : GambleState.keyName(st.nextKey));
    }

    // the "link your account" card, sliding down from the top
    private static void renderLink(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();
        boolean waiting = linkOpenedAt > 0;
        boolean offline = GambleOnline.linkFailures() >= 3;
        String title = "GAMBLE TRACKER  ·  LINK YOUR ACCOUNT";
        String l1 = "First, link this mod to your zzmods account.";
        String l2 = "It shares your Minecraft name + play stats with your account.";
        String hint = !bindMsg.isEmpty() ? bindMsg
                : offline ? "Can't reach zzmods.com - Enter tries again, Delete carries on for now"
                : waiting ? "Sign in and click Link on the page that opened - waiting..." : "Press Enter to open zzmods.com";
        int w = Math.max(Math.max(Txt.width(font, title), Txt.width(font, l1)), Math.max(Txt.width(font, l2), Txt.width(font, hint))) + 28;
        int x = (g.guiWidth() - w) / 2;
        int y = Math.round(12 - 30 * (1 - ease(now - stepAt, 350)));
        Style.panel(g, x, y, w, 88, 240);
        Txt.center(g, font, title, x + w / 2, y + 8, glow());
        Txt.center(g, font, l1, x + w / 2, y + 20, Colors.TEXT);
        Txt.center(g, font, l2, x + w / 2, y + 31, Colors.MUTED);
        keycap(g, font, "Enter", x + w / 2, y + 44, waiting ? 0 : now - stepAt);
        Txt.center(g, font, hint, x + w / 2, y + 72, !bindMsg.isEmpty() || offline ? Colors.RED : waiting ? Colors.YELLOW : Colors.MUTED);
    }

    private static float ease(long ms, int duration) {
        float t = Math.max(0, Math.min(1, ms / (float) duration));
        return 1 - (1 - t) * (1 - t) * (1 - t); // ease out
    }

    // ---- in game: the key-picking card, sliding down from the top

    public static void renderHud(GuiGraphicsExtractor g) {
        if (linking && Minecraft.getInstance().gui.screen() == null) {
            renderLink(g);
            return;
        }
        if (!active || Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        if (!step.bind()) {
            // the panel was closed mid-tour: say how to get back
            String l1 = "Gamble Tracker tour - tap " + GambleState.keyName(GambleState.INSTANCE.settingsKey) + " (or F9) to carry on";
            String l2 = "Backspace skips the tour";
            int w = Math.max(Txt.width(font, l1), Txt.width(font, l2)) + 24, x = (g.guiWidth() - w) / 2;
            Style.panel(g, x, 12, w, 34, 230);
            Txt.center(g, font, l1, x + w / 2, 19, Colors.TEXT);
            Txt.center(g, font, l2, x + w / 2, 31, Colors.MUTED);
            return;
        }
        GambleState st = GambleState.INSTANCE;
        long now = System.currentTimeMillis();
        String title = "GAMBLE TRACKER TOUR  ·  KEY " + (step.ordinal() + 1) + " OF 3";
        String line, current;
        switch (step) {
            case KEY_PANEL -> {
                line = "Which key opens the panel (queue + every setting)?";
                current = GambleState.keyName(st.settingsKey);
            }
            case KEY_CARD -> {
                line = "Which key shows the queue card while you play?";
                current = GambleState.keyName(st.openKey);
            }
            default -> {
                line = "Which key takes the next player after a game?";
                current = st.nextKey < 0 ? "none" : GambleState.keyName(st.nextKey);
            }
        }
        String hint = !bindMsg.isEmpty() ? bindMsg : "Press it now, or Enter to keep " + current
                + (step == Step.KEY_PANEL ? "  (F9 always works too)" : "");
        String skip = "Backspace skips the tour";
        int w = Math.max(Math.max(Txt.width(font, title), Txt.width(font, line)), Math.max(Txt.width(font, hint), Txt.width(font, skip))) + 28;
        int x = (g.guiWidth() - w) / 2;
        int y = Math.round(12 - 30 * (1 - ease(now - stepAt, 350)));
        Style.panel(g, x, y, w, 96, 240);
        Txt.center(g, font, title, x + w / 2, y + 8, glow());
        Txt.center(g, font, line, x + w / 2, y + 20, Colors.TEXT);
        keycap(g, font, "?", x + w / 2, y + 34, now - stepAt);
        Txt.center(g, font, hint, x + w / 2, y + 62, bindMsg.isEmpty() ? Colors.MUTED : Colors.RED);
        if (now - savedAt < 1600 && !savedText.isEmpty()) {
            Txt.center(g, font, "Saved - " + savedText, x + w / 2, y + 73, Colors.GREEN);
        }
        Txt.center(g, font, skip, x + w / 2, y + 84, Colors.MUTED);
        dots(g, x + w / 2, y + 96 + 4, step.ordinal(), 3);
    }

    // a key that pushes down and up, with rings rippling out in a few fixed steps
    private static void keycap(GuiGraphicsExtractor g, Font font, String label, int cx, int y, long age) {
        int w = Math.max(30, Txt.width(font, label) + 14), h = 18;
        int x = cx - w / 2;
        long t = System.currentTimeMillis() % 1400;
        int down = t > 900 && t < 1150 ? 2 : 0;
        if (Style.look() == Look.SLEEK) {
            int stepN = (int) (t / 350);
            float p = (stepN + 1) / 4F;
            Smooth.ring(g, x - p * 6, y - p * 6, w + p * 12, h + p * 12, 5 + p * 6, 1.5F, Colors.withAlpha(Colors.ACCENT_A, (int) (90 * (1 - p) + 10)));
            Smooth.rect(g, x, y + 3, w, h, 5, 0xFF05060A);
            Smooth.rect(g, x, y + down, w, h, 5, down > 0 ? Colors.ACCENT_A : 0xFF2A2E38);
            Smooth.ring(g, x, y + down, w, h, 5, 1, 0x40FFFFFF);
        } else {
            g.fill(x, y + 3, x + w, y + h + 3, 0xFF05060A);
            g.fill(x, y + down, x + w, y + h + down, down > 0 ? Colors.ACCENT_A : 0xFF3A3E48);
            g.fill(x, y + down, x + w, y + down + 1, 0x50FFFFFF);
        }
        Txt.center(g, font, label, cx, y + down + 5, 0xFFFFFFFF);
    }

    // progress dots under a card; the current one is wider
    private static void dots(GuiGraphicsExtractor g, int cx, int y, int current, int count) {
        int total = count * 8 + 6;
        int x = cx - total / 2;
        for (int i = 0; i < count; i++) {
            int w = i == current ? 12 : 5;
            int color = i < current ? Colors.withAlpha(Colors.ACCENT_A, 0xA0) : i == current ? glow() : 0x50FFFFFF;
            if (Style.look() == Look.SLEEK) {
                Smooth.rect(g, x, y, w, 5, 2.5F, color);
            } else {
                g.fill(x, y, x + w, y + 3, color);
            }
            x += w + 3;
        }
    }

    // ---- inside the panel: dim all but the target, a glowing outline, a bouncing pointer and a sliding card

    public static void renderPanel(GuiGraphicsExtractor g, Font font, int[] target, int px, int py, int pw, int ph,
                                   int vw, int vh, int mouseX, int mouseY) {
        HITS.clear();
        if (!active || step.bind()) {
            return;
        }
        long age = System.currentTimeMillis() - stepAt;
        if (target != null) {
            int tx = target[0] - 3, ty = target[1] - 3, tw = target[2] + 6, th = target[3] + 6;
            int dim = Colors.withAlpha(0x000000, (int) (0x8C * ease(age, 300)));
            g.fill(0, 0, vw, ty, dim);
            g.fill(0, ty + th, vw, vh, dim);
            g.fill(0, ty, tx, ty + th, dim);
            g.fill(tx + tw, ty, vw, ty + th, dim);
            float pulse = (float) (0.5 + 0.5 * Math.sin(age / 220.0));
            int ring = Colors.withAlpha(glow(), 140 + (int) (115 * pulse));
            if (Style.look() == Look.SLEEK) {
                Smooth.ring(g, tx - 3, ty - 3, tw + 6, th + 6, 8, 1.5F, Colors.withAlpha(Colors.ACCENT_A, (int) (70 * (1 - pulse))));
                Smooth.ring(g, tx, ty, tw, th, 5, 2, ring);
            } else {
                g.outline(tx, ty, tw, th, ring);
                g.outline(tx - 1, ty - 1, tw + 2, th + 2, Colors.withAlpha(ring, 70));
            }
        }

        List<String> lines = GambleTour.wrap(font, text(step), CARD_W - 16);
        int ch = 32 + lines.size() * 10 + 30;
        boolean right = px + pw + 10 + CARD_W <= vw;
        int cx = right ? px + pw + 10 : px - 10 - CARD_W >= 0 ? px - 10 - CARD_W : Math.max(2, Math.min(vw - CARD_W - 2, px + pw - CARD_W - 6));
        int focusY = target != null ? target[1] + target[3] / 2 : py + ph / 2;
        int cy = Math.max(2, Math.min(vh - ch - 2, focusY - ch / 2));
        float in = ease(age, 320);
        cx += Math.round((right ? 26 : -26) * (1 - in)); // slides in from the side
        Style.panel(g, cx, cy, CARD_W, ch, (int) (250 * in));

        int n = step.ordinal() - Step.KEY_NEXT.ordinal(), total = Step.values().length - Step.KEY_NEXT.ordinal() - 1;
        Txt.draw(g, font, "STEP " + n + " / " + total, cx + 8, cy + 7, glow(), Style.shadow);
        Txt.draw(g, font, Txt.cut(font, step.title, CARD_W - 16), cx + 8, cy + 18, Colors.TITLE, Style.shadow);
        int chars = (int) Math.max(0, (age - 150) / 12); // types itself out
        int y = cy + 32;
        for (String l : lines) {
            if (chars <= 0) break;
            Txt.draw(g, font, l.length() > chars ? l.substring(0, chars) : l, cx + 8, y, Colors.TEXT, Style.shadow);
            chars -= l.length() + 1;
            y += 10;
        }
        dots(g, cx + CARD_W / 2, cy + ch - 27, n - 1, total);
        int by = cy + ch - 17;
        button(g, font, cx + 8, by, 54, "Skip tour", Colors.BTN, 0, mouseX, mouseY);
        if (step == Step.NEXT) {
            String hint = "click Next >>";
            Txt.draw(g, font, hint, cx + CARD_W - 8 - Txt.width(font, hint), by + 3, Colors.MUTED, Style.shadow);
        } else {
            button(g, font, cx + CARD_W - 8 - 58, by, 58, step == Step.DONE ? "Finish" : "Next", Colors.BTN_GO, 1, mouseX, mouseY);
        }

        // a pointer between the card and the target, bouncing toward it
        if (target != null) {
            int tipX = right ? target[0] + target[2] + 5 : target[0] - 5;
            int bounce = (int) Math.round(3 * Math.sin(age / 160.0));
            int ax = tipX + (right ? 6 + bounce : -6 - bounce);
            int ay = Math.max(target[1] + 2, Math.min(target[1] + target[3] - 2, focusY));
            int dir = right ? -1 : 1;
            if (Style.look() == Look.SLEEK) {
                Smooth.line(g, ax, ay, ax - dir * 5, ay - 4, 1.6F, glow());
                Smooth.line(g, ax, ay, ax - dir * 5, ay + 4, 1.6F, glow());
            } else {
                for (int i = 0; i < 4; i++) {
                    g.fill(ax - dir * i, ay - i, ax - dir * i + 1, ay + i + 1, glow());
                }
            }
        }
        if (step == Step.DONE) {
            confetti(g, vw, vh, age);
        }
    }

    // a little burst of coloured squares falling down the screen when the tour is finished
    private static void confetti(GuiGraphicsExtractor g, int vw, int vh, long age) {
        if (confetti == null || age > 4000) return;
        int[] colors = {Colors.ACCENT_A, Colors.ACCENT_B, Colors.GOLD, Colors.GREEN, 0xFFFF6FC8};
        for (int i = 0; i < confetti.length; i++) {
            if (confetti[i] == null) {
                double r = Math.random();
                confetti[i] = new float[]{(float) (vw * Math.random()), (float) (-20 - 60 * Math.random()), (float) (0.3 + r * 0.6), (float) (Math.random() * 6)};
            }
            float[] c = confetti[i];
            float t = age / 16F;
            float x = c[0] + (float) Math.sin((t + c[3] * 10) / 12) * 8;
            float y = c[1] + t * c[2] * 2.2F;
            if (y < vh) {
                int s = 2 + (i % 3);
                g.fill((int) x, (int) y, (int) x + s, (int) y + s, colors[i % colors.length]);
            }
        }
    }

    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, int color, int action, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        Style.button(g, font, x, y, w, 13, label, color, true, hover);
        HITS.add(new int[]{x, y, w, 13, action});
    }

    public static boolean click(double mx, double my) {
        for (int[] h : new ArrayList<>(HITS)) {
            if (mx >= h[0] && mx < h[0] + h[2] && my >= h[1] && my < h[1] + h[3]) {
                GambleState.INSTANCE.click();
                if (h[4] == 0) skip();
                else next();
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
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
