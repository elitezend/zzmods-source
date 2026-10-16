package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// In-range payments queue up in the order they came in; the next key takes the first one off
// (or everyone). Out-of-range payments go on a refund list the next key never touches.
public final class GambleState {
    public static final GambleState INSTANCE = new GambleState();

    public enum HudSpot {
        RIGHT("Right"), LEFT("Left"), OFF("Hidden");

        public final String label;

        HudSpot(String label) {
            this.label = label;
        }
    }

    public record Bet(String name, long amount, boolean inRange, boolean underMin) {}

    // test payments zzmods.com support sent to this linked copy, by identity -> show the TEST tag
    public final Map<Bet, Boolean> testBets = new IdentityHashMap<>();

    public static final int[] ROW_CHOICES = {3, 5, 8, 12};
    public static final int[] PAYOUT_CHOICES = {150, 200, 250, 300}; // percent of the bet
    private static final long TOAST_MS = 4000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String minText = "";
    public String maxText = "";
    public boolean tracking = true;
    public boolean sounds = true;
    public HudSpot hud = HudSpot.RIGHT;
    public int heightPct = 0; // 0 = top of the screen, 100 = bottom
    // the card sits right under the server's scoreboard (DonutSMP's stats on the right) until you move it yourself
    public boolean cardAuto = true;
    public boolean nextFirstOnly = true;
    public boolean showRefunds = true;
    public boolean copyPay = true;
    public boolean tutorialDone; // premium: the first-run tour was finished or skipped
    public int payoutPct = 200;
    public int rows = 5;
    // default look: Sleek with the Ice / White accents (Premium)
    public Look look = Look.PLAIN; // "Default"
    // Clean: the extra bits you can switch off (Essentials only = all of them off)
    public boolean panelHint = true;   // "Right Alt: close" at the top of the panel (messages still show)
    public boolean nextHint = true;    // "Next player: tap ..." under Tracking payments
    public boolean cardKeys = true;    // the key hints at the bottom of the card (messages still show)
    public boolean cardLink = true;    // the Discord link at the bottom of the card
    public boolean cardLabels = true;  // the small "outside range" label by REFUND
    public int accent = Edition.LOOKS ? 5 : 0;
    public int hueA = 270;
    public int hueB = 190;
    public int corners = 2;
    public boolean textShadow;
    public int titleColor;
    public String title = "";

    // Clean: every extra bit is off
    public boolean essentialsOnly() {
        return !(panelHint || nextHint || cardKeys || cardLink || cardLabels);
    }

    public void setEssentialsOnly(boolean on) {
        panelHint = nextHint = cardKeys = cardLink = cardLabels = !on;
    }
    public int opacity = 85;
    public float scale = 1.0F;
    // GLFW key codes, -1 = not set, MOUSE + n = mouse button n (2 = middle)
    public static final int MOUSE = 1000;
    public int openKey = 345;     // Right Ctrl: show / hide the card
    public int settingsKey = 346; // Right Alt: open / close the panel
    public int nextKey = -1;      // take the first player off the queue
    public int payKey = -1;       // off by default so nobody gets paid by accident
    public boolean payKeySends = true;

    public boolean active; // card shows once the panel has been opened
    public final List<Bet> bets = new ArrayList<>();
    private List<Bet> undo;
    private String undoWhat = "";
    // compared by identity, so the same player betting again counts as a new bet
    private Bet copiedFor;
    private Bet paidFor;
    public String lastUnmatched = "";
    private String toast;
    private int toastColor;
    private long toastUntil;

    private GambleState() {}

    public long min() {
        return PaymentParser.parseMoney(minText);
    }

    public long max() {
        return PaymentParser.parseMoney(maxText);
    }

    public String rangeText() {
        long min = min(), max = max();
        if (min <= 0 && max < 0) return "any amount";
        if (max < 0) return PaymentParser.money(min) + "+";
        if (min <= 0) return "up to " + PaymentParser.money(max);
        return PaymentParser.money(min) + " - " + PaymentParser.money(max);
    }

    public List<Bet> queue() {
        return bets.stream().filter(Bet::inRange).toList();
    }

    public List<Bet> refunds() {
        return bets.stream().filter(b -> !b.inRange()).toList();
    }

    public long payout(Bet b) {
        try {
            return Math.multiplyExact(b.amount(), (long) payoutPct) / 100;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    public static String multiplierText(int pct) {
        return pct % 100 == 0 ? (pct / 100) + "x" : (pct / 100) + "." + (pct % 100 / 10) + "x";
    }

    // no slash: chat is opened with "/" already, a pasted slash would make it "//pay"
    public void copyPay(Bet b) {
        if (!Edition.PAYOUTS) {
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard("pay " + b.name() + " " + payout(b));
        toast("Copied pay " + b.name() + " " + PaymentParser.money(payout(b)), Colors.GOLD);
    }

    public boolean isPaid(Bet b) {
        return b != null && b == paidFor;
    }

    // the tour's example payments: added to the list like real ones, removed again by the tour (by identity)
    public Bet demoBet(String name, long amount) {
        long min = min(), max = max();
        boolean under = min >= 0 && amount < min;
        boolean over = max >= 0 && amount > max;
        Bet b = new Bet(name, amount, !under && !over, under);
        bets.add(b);
        return b;
    }

    public void demoRemove(List<Bet> demo) {
        bets.removeIf(b -> demo.stream().anyMatch(d -> d == b));
        saveBets();
    }

    public void payUpNext() {
        if (GambleOnline.off()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Edition.PAYOUTS) {
            return;
        }
        List<Bet> q = queue();
        if (q.isEmpty()) {
            toast("Nobody to pay - queue is empty", Colors.MUTED);
            return;
        }
        Bet first = q.get(0);
        if (!payKeySends) {
            copyPay(first);
            return;
        }
        if (first == paidFor) {
            toast("Already paid " + first.name() + (nextKey >= 0 ? " - " + keyName(nextKey) + " for next" : ""), Colors.YELLOW);
            return;
        }
        if (isTest(first)) {
            paidFor = first;
            toast("Test payment - nothing was sent to " + first.name(), Colors.AQUA);
            return;
        }
        long amount = payout(first);
        mc.player.connection.sendCommand("pay " + first.name() + " " + amount);
        paidFor = first;
        GambleOnline.countWin();
        log("PAID " + first.name() + " " + PaymentParser.full(amount) + " (pay key, " + multiplierText(payoutPct)
                + " of " + PaymentParser.full(first.amount()) + ")");
        toast("Paid " + first.name() + " " + PaymentParser.money(amount), Colors.GREEN);
        sound(true);
    }

    private void checkUpNext() {
        List<Bet> q = queue();
        Bet first = q.isEmpty() ? null : q.get(0);
        if (first == copiedFor) {
            return;
        }
        copiedFor = first;
        if (first != null && copyPay && Edition.PAYOUTS) {
            copyPay(first);
        }
    }

    // the card's size: starts at 70% (100% was too big)
    public static final float CARD_SCALE = 0.7F;

    public float scale() {
        return Math.max(0.5F, Math.min(2.0F, scale <= 0 ? CARD_SCALE : scale));
    }

    // every key, for the card and the Queue tab: "Next: Not set   Pay: Not set" / "Hide: Right Control   Panel: Right Alt"
    public String bindsLine1() {
        return "Next: " + keyName(nextKey) + (Edition.PAYOUTS ? "   Pay: " + keyName(payKey) : "");
    }

    public String bindsLine2() {
        return "Hide: " + keyName(openKey) + "   Panel: " + keyName(settingsKey);
    }

    // the panel's own size (the card keeps "Size"): starts at 60% like the Auction Overlay's panel
    public static final float PANEL_SCALE = 0.6F;
    public float panelScale = PANEL_SCALE;

    public float panelScale() {
        return Math.max(0.4F, Math.min(1.5F, panelScale <= 0 ? PANEL_SCALE : panelScale));
    }

    public void adjustPanelScale(int dir) {
        panelScale = dir == 0 ? PANEL_SCALE : Math.max(0.4F, Math.min(1.5F, Math.round((panelScale() + 0.1F * dir) * 10) / 10.0F));
        save();
    }

    public void adjustScale(int dir) {
        scale = dir == 0 ? CARD_SCALE : Math.max(0.5F, Math.min(2.0F, Math.round((scale() + 0.1F * dir) * 10) / 10.0F));
        save();
    }

    public void applyTheme() {
        Colors.apply(Style.look(), accent, hueA, hueB);
        if (Edition.LOOKS) {
            Colors.titleColor(titleColor);
        }
        Style.tune(Edition.LOOKS ? corners : 2, Edition.LOOKS && textShadow);
    }

    // ---- events

    public void onServerMessage(String message) {
        if (!tracking || GambleOnline.off()) { // blocked = a required update is waiting: the mod is off
            return;
        }
        PaymentParser.Payment p = PaymentParser.parse(message);
        if (p == null) {
            if (PaymentParser.looksLikePayment(message)) {
                lastUnmatched = PaymentParser.clean(message);
            }
            return;
        }
        addBet(p, null);
    }

    // a test payment from zzmods.com support: joins the queue like a real one (to check it, or for a screenshot),
    // but isn't counted in the stats, and the pay key never sends money for it
    public void testPayment(String name, String amountText, boolean tag) {
        long amount = PaymentParser.parseMoney(amountText);
        if (amount <= 0 || name == null || !name.matches("[.*]?[A-Za-z0-9_]{3,16}")) {
            return;
        }
        if (!tracking) {
            GambleOnline.note("Test payment: " + name + " paid " + PaymentParser.money(amount), "Tracking is paused, so it wasn't added");
            return;
        }
        addBet(new PaymentParser.Payment(name, amount), tag);
    }

    // staff's "clear test payments": takes every test payment out of the queue again, real ones stay
    public int clearTests() {
        int n = (int) bets.stream().filter(this::isTest).count();
        if (n == 0) {
            testBets.clear();
            return 0;
        }
        bets.removeIf(this::isTest);
        if (isTest(paidFor)) {
            paidFor = null;
        }
        testBets.clear();
        saveBets();
        return n;
    }

    public boolean isTest(Bet b) {
        return b != null && testBets.containsKey(b);
    }

    public boolean testTagged(Bet b) {
        return b != null && Boolean.TRUE.equals(testBets.get(b));
    }

    private void addBet(PaymentParser.Payment p, Boolean test) {
        long min = min(), max = max();
        boolean under = min >= 0 && p.amount() < min;
        boolean over = max >= 0 && p.amount() > max;
        Bet bet = new Bet(p.name(), p.amount(), !under && !over, under);
        bets.add(bet);
        if (test == null) {
            GambleOnline.countPayment(bet.inRange());
        } else {
            testBets.put(bet, test);
        }
        saveBets();
        log(p.name() + " paid " + PaymentParser.full(p.amount()) + "  "
                + (bet.inRange() ? "IN RANGE" : under ? "UNDER MIN" : "OVER MAX") + "  [range " + rangeText() + "]" + (test != null ? "  (TEST from support)" : ""));
        if (bet.inRange()) {
            int pos = queue().size();
            if (pos > 1 || !copyPay) {
                toast(p.name() + " joined (#" + pos + ")", Colors.GREEN);
            }
            sound(true);
        } else {
            toast(p.name() + " paid " + PaymentParser.money(p.amount()) + (under ? " - under min" : " - over max"), Colors.RED);
            sound(false);
        }
    }

    public void onNextKey() {
        if (GambleOnline.off()) {
            return;
        }
        if (!tracking) {
            toast("Tracking is paused", Colors.MUTED);
            return;
        }
        if (nextFirstOnly) {
            next("next key");
        } else {
            clearQueue("next key");
        }
    }

    public void next(String why) {
        List<Bet> q = queue();
        if (q.isEmpty()) {
            toast("Queue is empty", Colors.MUTED);
            return;
        }
        Bet first = q.get(0);
        remember("Removed " + first.name());
        bets.remove(first);
        saveBets();
        log("--- " + why + ": removed " + first.name() + " (" + PaymentParser.full(first.amount()) + ") ---");
        List<Bet> rest = queue();
        if (rest.isEmpty()) {
            toast("Done " + first.name() + " - queue empty", Colors.YELLOW);
        } else if (!copyPay) {
            toast("Done " + first.name() + " - next: " + rest.get(0).name(), Colors.YELLOW);
        }
        sound(false);
    }

    public void remove(Bet bet) {
        remember("Removed " + bet.name());
        bets.remove(bet);
        saveBets();
        log("--- removed " + bet.name() + " (" + PaymentParser.full(bet.amount()) + ") from panel ---");
    }

    public void clearQueue(String why) {
        int n = queue().size();
        if (n == 0) {
            toast("Queue is empty", Colors.MUTED);
            return;
        }
        remember("Cleared " + n);
        bets.removeIf(Bet::inRange);
        saveBets();
        log("--- " + why + ": cleared queue (" + n + ") ---");
        toast("Cleared " + n + (n == 1 ? " player" : " players"), Colors.YELLOW);
        sound(false);
    }

    public void clearRefunds() {
        int n = refunds().size();
        if (n == 0) {
            return;
        }
        remember("Cleared " + n + " refunds");
        bets.removeIf(b -> !b.inRange());
        saveBets();
        log("--- cleared refund list (" + n + ") ---");
    }

    private void remember(String what) {
        undo = new ArrayList<>(bets);
        undoWhat = what;
    }

    public boolean canUndo() {
        return undo != null;
    }

    public void undo() {
        if (undo == null) {
            return;
        }
        bets.clear();
        bets.addAll(undo);
        undo = null;
        saveBets();
        log("--- undo: " + undoWhat + " ---");
        toast("Undid: " + undoWhat, Colors.YELLOW);
    }

    // ---- toast / sound

    public void toast(String text, int color) {
        toast = text;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + TOAST_MS;
    }

    public String toast() {
        return System.currentTimeMillis() < toastUntil ? toast : null;
    }

    public int toastColor() {
        return toastColor;
    }

    public void click() {
        if (sounds) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void sound(boolean good) {
        if (sounds) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    good ? SoundEvents.NOTE_BLOCK_PLING : SoundEvents.NOTE_BLOCK_BASS, good ? 1.4F : 0.8F));
        }
    }

    public static boolean isMouse(int key) {
        return key >= MOUSE;
    }

    public static String keyName(int key) {
        if (key < 0) {
            return "Not set";
        }
        if (isMouse(key)) {
            int b = key - MOUSE;
            return b == 2 ? "Middle mouse" : "Mouse " + (b + 1);
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        if (name.startsWith("Right ")) return "R" + name.substring(6);
        if (name.startsWith("Left ")) return "L" + name.substring(5);
        return name;
    }

    // ---- files (<instance>/config)

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path logFile() {
        return dir().resolve("gamble_tracker_log.txt");
    }

    public void load() {
        Properties p = new Properties();
        Path file = dir().resolve("gamble_tracker.properties");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
            }
        }
        minText = p.getProperty("min", "");
        maxText = p.getProperty("max", "");
        tracking = bool(p, "tracking", true);
        sounds = bool(p, "sounds", true);
        String spot = p.getProperty("hud", "RIGHT");
        // old versions had fixed corner spots
        heightPct = spot.equals("RIGHT") && !p.containsKey("heightPct") ? 50 : 0;
        hud = switch (spot) {
            case "TOP_LEFT", "LEFT" -> HudSpot.LEFT;
            case "OFF" -> HudSpot.OFF;
            default -> HudSpot.RIGHT;
        };
        // was the note block / lever setting before the next key existed
        nextFirstOnly = Boolean.parseBoolean(p.getProperty("nextFirstOnly",
                p.getProperty("noteFirstOnly", p.getProperty("leverFirstOnly", "true"))));
        copyPay = bool(p, "copyPay", true);
        tutorialDone = bool(p, "tutorialDone", false);
        showRefunds = bool(p, "showRefunds", true);
        payKeySends = bool(p, "payKeySends", true);
        look = Look.parse(p.getProperty("look"), Look.PLAIN);
        panelHint = bool(p, "panelHint", true);
        nextHint = bool(p, "nextHint", true);
        cardKeys = bool(p, "cardKeys", true);
        cardLink = bool(p, "cardLink", true);
        cardLabels = bool(p, "cardLabels", true);
        title = p.getProperty("title", "");
        openKey = num(p, "openKey", 345);
        settingsKey = num(p, "settingsKeyCode", 346);
        nextKey = num(p, "nextKey", -1);
        payKey = num(p, "payKey", -1);
        rows = num(p, "rows", 5);
        payoutPct = Math.max(100, num(p, "payoutPct", 200));
        accent = num(p, "accent", Edition.LOOKS ? 5 : 0);
        hueA = num(p, "hueA", 270);
        hueB = num(p, "hueB", 190);
        corners = num(p, "corners", 2);
        titleColor = num(p, "titleColor", 0);
        textShadow = bool(p, "textShadow", false);
        opacity = Math.max(35, Math.min(100, num(p, "opacity", 85)));
        cardAuto = bool(p, "cardAuto", true);
        if (p.containsKey("heightPct")) {
            heightPct = Math.max(0, Math.min(100, num(p, "heightPct", 0)));
        }
        try {
            scale = Float.parseFloat(p.getProperty("scale", String.valueOf(CARD_SCALE)));
        } catch (NumberFormatException e) {
            scale = CARD_SCALE;
        }
        // 2.0.3: the card starts smaller - an untouched 100% (the old default) becomes 70%, once
        if (!p.containsKey("cardScaleV2") && scale == 1.0F) scale = CARD_SCALE;
        try {
            panelScale = Float.parseFloat(p.getProperty("panelScale", String.valueOf(PANEL_SCALE)));
        } catch (NumberFormatException e) {
            panelScale = PANEL_SCALE;
        }
        applyTheme();

        bets.clear();
        Path betFile = dir().resolve("gamble_tracker_bets.txt");
        if (Files.exists(betFile)) {
            try {
                for (String line : Files.readAllLines(betFile, StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t");
                    if (f.length == 4) {
                        bets.add(new Bet(f[0], Long.parseLong(f[1]), Boolean.parseBoolean(f[2]), Boolean.parseBoolean(f[3])));
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
            }
        }
        List<Bet> q = queue();
        copiedFor = q.isEmpty() ? null : q.get(0); // don't touch the clipboard just because the game started
    }

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }

    private static int num(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("min", minText);
        p.setProperty("max", maxText);
        p.setProperty("tracking", String.valueOf(tracking));
        p.setProperty("sounds", String.valueOf(sounds));
        p.setProperty("hud", hud.name());
        p.setProperty("heightPct", String.valueOf(heightPct));
        p.setProperty("cardAuto", String.valueOf(cardAuto));
        p.setProperty("nextFirstOnly", String.valueOf(nextFirstOnly));
        p.setProperty("copyPay", String.valueOf(copyPay));
        p.setProperty("tutorialDone", String.valueOf(tutorialDone));
        p.setProperty("payoutPct", String.valueOf(payoutPct));
        p.setProperty("showRefunds", String.valueOf(showRefunds));
        p.setProperty("rows", String.valueOf(rows));
        p.setProperty("look", look.name());
        p.setProperty("accent", String.valueOf(accent));
        p.setProperty("hueA", String.valueOf(hueA));
        p.setProperty("hueB", String.valueOf(hueB));
        p.setProperty("corners", String.valueOf(corners));
        p.setProperty("titleColor", String.valueOf(titleColor));
        p.setProperty("textShadow", String.valueOf(textShadow));
        p.setProperty("panelHint", String.valueOf(panelHint));
        p.setProperty("nextHint", String.valueOf(nextHint));
        p.setProperty("cardKeys", String.valueOf(cardKeys));
        p.setProperty("cardLink", String.valueOf(cardLink));
        p.setProperty("cardLabels", String.valueOf(cardLabels));
        p.setProperty("title", title == null ? "" : title);
        p.setProperty("opacity", String.valueOf(opacity));
        p.setProperty("scale", String.valueOf(scale()));
        p.setProperty("cardScaleV2", "true");
        p.setProperty("panelScale", String.valueOf(panelScale()));
        p.setProperty("openKey", String.valueOf(openKey));
        p.setProperty("settingsKeyCode", String.valueOf(settingsKey));
        p.setProperty("nextKey", String.valueOf(nextKey));
        p.setProperty("payKey", String.valueOf(payKey));
        p.setProperty("payKeySends", String.valueOf(payKeySends));
        try {
            Files.createDirectories(dir());
            try (Writer w = Files.newBufferedWriter(dir().resolve("gamble_tracker.properties"), StandardCharsets.UTF_8)) {
                p.store(w, "Gamble Tracker settings (keys are GLFW codes: 345 = Right Ctrl, 346 = Right Alt, -1 = not set)");
            }
        } catch (IOException ignored) {
        }
    }

    // the queue is saved on every change so it survives relogs and crashes
    private void saveBets() {
        checkUpNext();
        StringBuilder sb = new StringBuilder();
        for (Bet b : bets) {
            sb.append(b.name()).append('\t').append(b.amount()).append('\t')
                    .append(b.inRange()).append('\t').append(b.underMin()).append('\n');
        }
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("gamble_tracker_bets.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // a record of every payment / removal in case anyone disputes a bet
    private void log(String line) {
        try {
            Files.createDirectories(dir());
            Files.writeString(logFile(), LocalDateTime.now().format(TIME) + "  " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
