package com.auctionoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionState {
    public static final AuctionState INSTANCE = new AuctionState();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern AMOUNT = Pattern.compile(
            "^\\s*([^0-9a-zA-Z\\s]*)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbKMB])?(?![a-zA-Z])\\s*(.*)$");

    // config/auction_overlay.json
    public static final class Config {
        public String item = "";
        public int amount = 1;
        public int durMin = 1;
        public int durSec = 0;
        public String minPayText = "1";
        public String worthText = "";
        public int raiseBy = 1;
        public boolean antiSnipe = true;
        public boolean watchPayments = true;
        public int opacity = 85;
        public int panelX = -1;
        public int panelY = -1;
        public float scale = DEFAULT_SCALE; // new installs start smaller; Ctrl + / - changes it
        public boolean roundedLook = true; // before 1.0.5 this was the only look switch
        // default look: Sleek with the Ice / White accents (Basic and Premium - Basic copies that never picked a look get it too)
        public String look = Edition.SLEEK ? "SLEEK" : null;
        public int accent = Edition.LOOKS ? 5 : 0;
        public String title = "";
        public int toggleKey = Edition.DEFAULT_TOGGLE_KEY;
        public int defaultToggleKey; // edition default when last saved (0 = older than 1.0.7)
        public int antiSnipeSecs = 10;
        public int extendSecs = 30;
        public boolean showCard = true;
        public int resultSecs = 15;
        public boolean sounds = true;
        public boolean obsFile = true;
        public boolean copyTpa = true;
        // custom lot tab
        public boolean custom;
        public String customName = "";
        public String customIcon = "";
        public List<LotItem> customItems = new ArrayList<>();
        public List<Preset> presets = new ArrayList<>(); // saved custom lots = kits (KitsScreen)
        public boolean kitsSeeded; // the example "Neth kit" was added once
        // look
        public int hueA = 270;
        public int hueB = 190;
        public int corners = 2;
        public boolean textShadow;
        public int titleColor;
        // worth
        public int worthStyle;          // 0 banner, 1 badge, 2 next to LIVE, 3 hidden
        public int worthColor;
        public String worthLabel = "WORTH";
        public boolean worthOnCard = true;
        public boolean worthInObs = true;
        // in-game card
        public int cardSpot;            // 0 = where the panel is, 1-4 = screen corners
        public int cardSize;            // percent, 0 = same as the panel
        public boolean cardNext = true;
        public boolean cardBidder = true;
        public boolean cardContents = true;
        public boolean cardHint = true;
        public boolean cardLabels = true;   // the small TIME LEFT / TOP BID labels
        // Clean tab: the extras around the auction (Essentials only switches them all off)
        public boolean panelHint = true;     // "Right Shift: hide" at the top of the panel
        public boolean liveRules = true;     // "Min 1m | Raise 100k | Anti-snipe" under the item
        public boolean watchingText = true;  // "watching chat" next to LIVE
        public boolean panelContents = true; // the "Includes: ..." line on the panel
        public boolean footerLink = true;    // the Discord link at the bottom (an update notice still shows)
        public boolean blink = true;         // blinking LIVE dot and flashing timer
        // auction
        public int warnSecs = 10;
        public boolean shortNumbers = true;
        public int historyRows = 4;
        public int volume = 100;
        // first to pay
        public boolean ftpTab;
        // Premium giveaway setup: entry price, prize, how long entries stay open, and whether paying more gives more entries
        public boolean gwTab;
        public String gwEntryText = "1m";
        public String gwPrizeText = "10m";
        public int gwMin = 2;
        public int gwSec = 0;
        public boolean gwMulti = true;
        public String ftpPayText = "5m";
        public String ftpPrizeText = "10m";
        public int ftpMin = 0;
        public int ftpSec = 10;
        public boolean ftpExact;
        public boolean ftpCopyPay = true;
        // premium: reserve, buy now, several winners, dutch auctions, queue
        public String reserveText = "";
        public String buyNowText = "";
        public int winners = 1;
        public boolean dutch;
        public String dutchStartText = "10m";
        public String dutchDropText = "500k";
        public int dutchEverySecs = 5;
        public String dutchFloorText = "1m";
        public List<Lot> queue = new ArrayList<>();
        public int queueBreakSecs = 15;
        // premium: in-game keys (-1 = not set)
        public int keyExtend = -1;
        public int keySell = -1;
        public int keyCancel = -1;
        public int keyAnnounce = -1;
        // premium: stream
        public boolean overlay = true;
        public String webhook = "";
        public boolean hookStart = true;
        public boolean hookBids;
        public boolean hookSold = true;
        public String announceText = DEFAULT_ANNOUNCE;
        public boolean tickSounds = true;
        // premium: bidders
        public String blockList = "";
        public boolean allowOnly;
        public boolean customFormat;
        public String payFormat = "{name} paid you {amount}";
        public boolean priceGuide = true;
        public boolean tutorialDone; // the first-join walk-through was finished or skipped
        public String jarId; // the jar file this ran from last time (name, size, dates) - a new file = added (back) by hand
        public String jarVersion;
        public String selfUpdateJar;
        public java.util.List<String> annSeen = new java.util.ArrayList<>(); // announcements already read (by id) // the jar the in-game updater is putting in (its own update doesn't replay the tutorial)
        // zzmods.com: update notice + anonymous usage counts (Settings > About)
        public boolean checkUpdates = true;
        public boolean shareStats = true;
        public String installId = "";
        // "Link account" (About tab): the secret this copy uses with zzmods.com, and the masked email once linked
        public String linkSecret;
        // a required update zzmods.com asked for (kept so it still applies offline), and its note
        public String requiredUpdate, requiredNotes;
        // paid editions: did the linked account own this edition at the last check, and when was that
        public boolean licenseOk;
        public boolean banned; // the linked zzmods account was banned by staff: the mod is off
        public long licenseCheckedAt;
        public String linkedEmail;
        public Online.Usage usage = new Online.Usage();
    }

    public static final String DEFAULT_ANNOUNCE = "AUCTION: {item} | Top bid {bid} ({bidder}) | {time} left | Pay {next}+ to bid!";

    // a lot ready to run: what's sold plus its rules (queued lots are saved like this)
    public static final class Lot {
        public boolean custom;
        public String name = "";
        public String icon = "";
        public List<LotItem> items = new ArrayList<>();
        public int durSecs = 60;
        public String minPayText = "1";
        public int raiseBy = 1;
        public String worthText = "";
        public String reserveText = "";
        public String buyNowText = "";
        public int winners = 1;
        public boolean antiSnipe = true;
        public boolean watchPayments = true;
        public boolean dutch;
        public String dutchStartText = "";
        public String dutchDropText = "";
        public int dutchEverySecs = 5;
        public String dutchFloorText = "";

        public String label() {
            if (custom && name != null && !name.isBlank()) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            for (LotItem li : items) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(custom && li.count > 1 ? li.count + " " : "").append(Lots.name(li.id)).append(custom ? "" : " x" + li.count);
            }
            return sb.toString();
        }
    }

    public static final String[] WORTH_STYLES = {"Banner", "Badge", "Next to LIVE", "Hidden"};
    public static final String[] WORTH_COLOR_NAMES = {"Gold", "Green", "Aqua", "Accent", "White", "Red", "Yellow"};
    public static final String[] CARD_SPOTS = {"With panel", "Top left", "Top right", "Bottom left", "Bottom right"};

    public static final class LotItem {
        public String id;
        public int count;

        public LotItem() {}

        public LotItem(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    public static final class Preset {
        public String name;
        public String icon;
        public List<LotItem> items = new ArrayList<>();
    }

    // blocked = a payment from someone on the block list: it never counts and has to be refunded
    public record Bid(String name, long amount, boolean paid, boolean blocked) {}

    // a payment during a first-to-pay game, with why it did / didn't count
    public record FtpPay(String name, long amount, String note, boolean won) {}

    // "5 diamonds" -> 5 + "diamonds", "10k" -> 10000, "$50" -> "$" + 50
    public record Amount(long value, String prefix, String unit, boolean hasNumber) {}

    public Config config = new Config();

    public boolean live;
    public boolean hasAuction;
    // first to pay: the countdown runs until endTimeMs, then it's open (ftpGo) until someone pays
    public boolean ftp;
    public boolean ftpGo;
    // giveaway: everyone who pays the entry joins; when the timer ends a random entry wins (more entries = better odds)
    public boolean gw;
    public long gwEntry;
    public boolean gwMulti;
    private final Map<String, Integer> gwTickets = new LinkedHashMap<>(); // lower-case name -> entries
    private final Map<String, String> gwNames = new java.util.HashMap<>();
    private static final java.security.SecureRandom DRAW = new java.security.SecureRandom();
    public long ftpNeed;
    public boolean ftpExact;
    public String ftpPrize = "";
    public long ftpPrizeValue;
    public final List<FtpPay> ftpPays = new ArrayList<>();
    public boolean customLot;
    public String lotName = "";
    public String icon = "";
    public final List<LotItem> lot = new ArrayList<>();
    public long minPay;
    public long raiseBy;
    public String payPrefix = "";
    public String payUnit = "";
    public String worthText = "";
    public String copiedTpa;
    public boolean antiSnipe;
    public boolean watchPayments;
    public Bid leader;
    public String liveMsg = "";
    public int liveMsgColor = Colors.RED;
    public long endTimeMs;
    public long finishedAtMs;
    public final List<Bid> bids = new ArrayList<>();
    // test payments staff sent to this linked copy, by identity -> show the TEST tag
    public final Map<Bid, Boolean> testBids = new IdentityHashMap<>();
    public boolean testing;
    public String result = "";
    public int resultColor;
    // premium
    public long reserve;
    public long buyNow;
    public int winnersN = 1;
    public boolean dutch;
    public long dutchPrice, dutchDrop, dutchFloor;
    public long dutchEveryMs;
    public boolean queuePaused;
    public long nextQueueAt;
    public Lot lastLot;
    private final List<Bid> soldTo = new ArrayList<>();
    private String toastText;
    private long toastUntilMs;
    private int lastTickSecs = -1;

    private String lastObsText;
    private String lastObsResult = "No auction running";

    private AuctionState() {}

    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public void load() {
        try {
            Path path = configDir().resolve("auction_overlay.json");
            if (Files.exists(path)) {
                Config loaded = GSON.fromJson(Files.readString(path), Config.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        Config c = config;
        if (Edition.CUSTOM_LOTS && !c.kitsSeeded) {
            // an example kit, so the Kits button shows what it's for (delete it any time)
            c.kitsSeeded = true;
            if (c.presets.isEmpty()) {
                Preset neth = new Preset();
                neth.name = "Neth kit";
                neth.icon = "netherite_chestplate";
                for (String id : new String[]{"netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                        "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel"}) {
                    neth.items.add(new LotItem(id, 1)); // (vanilla items in every version this is built for)
                }
                if (!neth.items.isEmpty()) c.presets.add(neth);
            }
        }
        if (c.minPayText == null) c.minPayText = "1";
        if (c.worthText == null) c.worthText = "";
        if (c.customName == null) c.customName = "";
        if (c.customIcon == null) c.customIcon = "";
        if (c.customItems == null) c.customItems = new ArrayList<>();
        if (c.presets == null) c.presets = new ArrayList<>();
        if (c.worthLabel == null) c.worthLabel = "WORTH";
        if (c.ftpPayText == null) c.ftpPayText = "5m";
        if (c.ftpPrizeText == null) c.ftpPrizeText = "10m";
        c.customItems.removeIf(li -> li == null || li.id == null);
        c.custom &= Edition.CUSTOM_LOTS;
        c.ftpTab &= Edition.FIRST_TO_PAY;
        c.gwTab &= Edition.PRO;
        if (c.gwEntryText == null) c.gwEntryText = "1m";
        if (c.gwPrizeText == null) c.gwPrizeText = "10m";
        if (c.reserveText == null) c.reserveText = "";
        if (c.buyNowText == null) c.buyNowText = "";
        if (c.dutchStartText == null) c.dutchStartText = "10m";
        if (c.dutchDropText == null) c.dutchDropText = "500k";
        if (c.dutchFloorText == null) c.dutchFloorText = "1m";
        if (c.queue == null) c.queue = new ArrayList<>();
        c.queue.removeIf(l -> l == null || l.items == null);
        if (c.webhook == null) c.webhook = "";
        if (c.announceText == null || c.announceText.isBlank()) c.announceText = DEFAULT_ANNOUNCE;
        if (c.blockList == null) c.blockList = "";
        if (c.payFormat == null) c.payFormat = "{name} paid you {amount}";
        if (c.usage == null) c.usage = new Online.Usage();
        if (c.installId == null) c.installId = "";
        c.winners = Math.max(1, Math.min(9, c.winners));
        c.dutch &= Edition.PRO;
        if (!Edition.PRO) {
            c.queue.clear();
        }
        Sales.load();
        int oldDefault = c.defaultToggleKey > 0 ? c.defaultToggleKey : InputConstants.KEY_RSHIFT;
        if (c.toggleKey <= 0 || c.toggleKey == oldDefault) {
            c.toggleKey = Edition.DEFAULT_TOGGLE_KEY; // never rebound, so follow the edition default
        }
        c.defaultToggleKey = Edition.DEFAULT_TOGGLE_KEY;
        applyTheme();
        writeObsText();
    }

    public void applyTheme() {
        Colors.apply(Style.look(), config.accent, config.hueA, config.hueB);
        if (Edition.LOOKS) {
            Colors.titleColor(config.titleColor);
        }
        Style.tune(Edition.LOOKS ? config.corners : 2, Edition.LOOKS && config.textShadow);
    }

    public int worthColor() {
        return switch (Edition.LOOKS ? Math.floorMod(config.worthColor, WORTH_COLOR_NAMES.length) : 0) {
            case 1 -> Colors.GREEN;
            case 2 -> Colors.AQUA;
            case 3 -> Colors.ACCENT_A;
            case 4 -> 0xFFFFFFFF;
            case 5 -> Colors.RED;
            case 6 -> Colors.YELLOW;
            default -> Colors.GOLD;
        };
    }

    public String worthLabel() {
        String label = config.worthLabel == null || !Edition.LOOKS ? "" : config.worthLabel.trim();
        return label.isEmpty() ? "WORTH" : label;
    }

    // which worth style to draw right now (Hidden when there's no worth set)
    public int worthStyle() {
        return !hasWorth() ? 3 : Edition.LOOKS ? Math.floorMod(config.worthStyle, WORTH_STYLES.length) : 0;
    }

    // the half-second on/off for blinking text; always "on" (steady) when blinking is switched off
    public static boolean blink() {
        return !INSTANCE.config.blink || (System.currentTimeMillis() / 500) % 2 == 0;
    }

    // Clean tab: every extra off = only the essentials (item, LIVE, time left, top bid and who has it)
    public boolean essentialsOnly() {
        Config c = config;
        return !(c.panelHint || c.liveRules || c.watchingText || c.panelContents || c.footerLink || c.blink
                || c.cardLabels || c.cardNext || c.cardContents || c.cardHint);
    }

    public void setEssentialsOnly(boolean on) {
        Config c = config;
        c.panelHint = c.liveRules = c.watchingText = c.panelContents = c.footerLink = c.blink = !on;
        c.cardLabels = c.cardNext = c.cardContents = c.cardHint = !on;
        save();
    }

    public void save() {
        try {
            Files.createDirectories(configDir());
            Files.writeString(configDir().resolve("auction_overlay.json"), GSON.toJson(config));
        } catch (IOException ignored) {
        }
    }

    public int remainingSeconds() {
        return (int) Math.ceil(Math.max(0, endTimeMs - System.currentTimeMillis()) / 1000.0);
    }

    public long nextMin() {
        if (winnersN > 1) {
            List<Bid> in = winningBids();
            return in.size() < winnersN ? minPay : in.get(in.size() - 1).amount() + raiseBy;
        }
        return leader == null ? minPay : leader.amount() + raiseBy;
    }

    // first to pay and dutch auctions: the first good payment wins, nothing to outbid
    public boolean instant() {
        return ftp || dutch || gw;
    }

    // with several winners: each bidder's best bid, highest first, as many as there are winners
    public List<Bid> winningBids() {
        if (winnersN <= 1) {
            return leader == null ? List.of() : List.of(leader);
        }
        java.util.Map<String, Bid> best = new java.util.LinkedHashMap<>();
        for (Bid b : bids) {
            if (b.blocked() || b.amount() < minPay) {
                continue;
            }
            String key = b.name().toLowerCase(Locale.ROOT);
            Bid cur = best.get(key);
            if (cur == null || b.amount() > cur.amount()) {
                best.put(key, b);
            }
        }
        List<Bid> list = new ArrayList<>(best.values());
        list.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        return list.size() > winnersN ? new ArrayList<>(list.subList(0, winnersN)) : list;
    }

    // who won the last auction (empty while it runs)
    public List<Bid> soldTo() {
        return soldTo;
    }

    // payments that have to go back once an auction is over: everything paid except the winning payments
    public List<Bid> refunds() {
        List<Bid> out = new ArrayList<>();
        if (live || instant()) {
            return out;
        }
        for (Bid b : bids) {
            boolean won = false;
            for (Bid w : soldTo) {
                won |= w == b;
            }
            if (b.paid() && !won && !isTest(b)) {
                out.add(b);
            }
        }
        return out;
    }

    public boolean blocked(String name) {
        if (!Edition.PRO || config.blockList == null || config.blockList.isBlank()) {
            return false;
        }
        boolean listed = false;
        for (String n : config.blockList.split("[,\\s]+")) {
            listed |= !n.isBlank() && n.trim().equalsIgnoreCase(name.trim());
        }
        return config.allowOnly != listed;
    }

    public void toast(String text) {
        toastText = text;
        toastUntilMs = System.currentTimeMillis() + 2500;
    }

    public Bid topBid() {
        return leader;
    }

    // --- what's being sold

    public String displayName() {
        if (ftp) {
            return "First to pay";
        }
        if (gw) {
            return "Giveaway";
        }
        if (!customLot) {
            return lot.isEmpty() ? "" : Lots.name(lot.get(0).id) + " x" + lot.get(0).count;
        }
        return lotName.isBlank() ? contents() : lotName;
    }

    public String contents() {
        StringBuilder sb = new StringBuilder();
        for (LotItem li : lot) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(li.count > 1 ? li.count + " " : "").append(Lots.name(li.id));
        }
        return sb.toString();
    }

    // custom lots with a name show what's inside on a line of their own
    public boolean showContents() {
        return !ftp && customLot && !lotName.isBlank() && !lot.isEmpty();
    }

    // returns an error, or null once the auction is running
    public String start(Lots.Entry picked) {
        String[] err = new String[1];
        Lot l = fromSetup(picked, err);
        if (l == null) {
            if (picked == null && !config.custom && Edition.PRO && !config.queue.isEmpty()) {
                queuePaused = false;
                return runQueued(); // nothing picked but lots are queued: start the queue
            }
            return err[0];
        }
        queuePaused = false;
        return run(l);
    }

    // the setup screen as a Lot, or null with the reason in err[0]
    public Lot fromSetup(Lots.Entry picked, String[] err) {
        Lot l = new Lot();
        l.custom = config.custom;
        if (config.custom) {
            for (LotItem li : config.customItems) {
                if (Lots.get(li.id) != null && li.count > 0) {
                    l.items.add(new LotItem(li.id, li.count));
                }
            }
            l.name = config.customName.trim();
            l.icon = Lots.get(config.customIcon) != null ? config.customIcon : l.items.isEmpty() ? "" : l.items.get(0).id;
            if (l.name.isEmpty() && l.items.isEmpty()) {
                err[0] = "Name the lot or add some items.";
                return null;
            }
            if (l.icon.isEmpty()) {
                err[0] = "Pick a logo (Logo, then click an item).";
                return null;
            }
        } else {
            if (picked == null) {
                err[0] = "Pick an item first.";
                return null;
            }
            l.icon = picked.id();
            l.items.add(new LotItem(l.icon, Math.max(1, config.amount)));
            config.item = l.icon;
        }
        l.durSecs = config.durMin * 60 + config.durSec;
        l.minPayText = config.minPayText == null ? "" : config.minPayText;
        l.raiseBy = Math.max(1, config.raiseBy);
        l.worthText = config.worthText == null ? "" : config.worthText.trim();
        l.antiSnipe = config.antiSnipe;
        l.watchPayments = config.watchPayments;
        if (Edition.PRO) {
            l.reserveText = config.reserveText.trim();
            l.buyNowText = config.buyNowText.trim();
            l.winners = Math.max(1, Math.min(9, config.winners));
            l.dutch = config.dutch;
            l.dutchStartText = config.dutchStartText;
            l.dutchDropText = config.dutchDropText;
            l.dutchEverySecs = Math.max(1, config.dutchEverySecs);
            l.dutchFloorText = config.dutchFloorText;
        }
        String bad = check(l);
        if (bad != null) {
            err[0] = bad;
            return null;
        }
        return l;
    }

    private static String check(Lot l) {
        if (l.dutch) {
            Amount start = parseAmount(l.dutchStartText), drop = parseAmount(l.dutchDropText), floor = parseAmount(l.dutchFloorText);
            if (!start.hasNumber() || start.value() <= 0) return "Type the starting price.";
            if (!drop.hasNumber() || drop.value() <= 0) return "Type how much the price drops.";
            if (!floor.hasNumber() || floor.value() <= 0) return "Type the lowest price.";
            if (floor.value() >= start.value()) return "The lowest price has to be under the start.";
            return null;
        }
        if (l.durSecs <= 0) return "Set a duration above 0 seconds.";
        if (!l.buyNowText.isEmpty() && !parseAmount(l.buyNowText).hasNumber()) return "Buy now has to be an amount (or empty).";
        if (!l.reserveText.isEmpty() && !parseAmount(l.reserveText).hasNumber()) return "Reserve has to be an amount (or empty).";
        return null;
    }

    // premium: line the setup up to run after the current auction
    public String enqueue(Lots.Entry picked) {
        String[] err = new String[1];
        Lot l = fromSetup(picked, err);
        if (l == null) {
            return err[0];
        }
        config.queue.add(l);
        save();
        Online.count("queued");
        return null;
    }

    public String runQueued() {
        if (config.queue.isEmpty()) {
            return "The queue is empty.";
        }
        Lot l = config.queue.remove(0);
        save();
        return run(l);
    }

    public void clearQueue() {
        config.queue.clear();
        nextQueueAt = 0;
        save();
    }

    // seconds until the next queued lot starts, or -1
    public int queueCountdown() {
        if (live || nextQueueAt <= 0 || queuePaused || config.queue.isEmpty()) {
            return -1;
        }
        return (int) Math.ceil(Math.max(0, nextQueueAt - System.currentTimeMillis()) / 1000.0);
    }

    public String runAgain() {
        queuePaused = true;
        return gw ? startGiveaway() : ftp ? startFtp() : lastLot == null ? "Nothing to run again." : run(lastLot);
    }

    // --- giveaway (Premium)

    public String startGiveaway() {
        Amount entry = parseAmount(config.gwEntryText == null ? "" : config.gwEntryText);
        if (!entry.hasNumber() || entry.value() <= 0) {
            return "Type how much it costs to enter.";
        }
        Amount prize = parseAmount(config.gwPrizeText == null ? "" : config.gwPrizeText);
        if (!prize.hasNumber() || prize.value() <= 0) {
            return "Type what the winner gets.";
        }
        int secs = config.gwMin * 60 + config.gwSec;
        if (secs <= 0) {
            return "Set how long entries stay open.";
        }
        gw = true;
        ftp = false;
        ftpGo = false;
        dutch = false;
        reserve = buyNow = 0;
        winnersN = 1;
        soldTo.clear();
        nextQueueAt = 0;
        lastTickSecs = -1;
        gwEntry = entry.value();
        gwMulti = config.gwMulti;
        payPrefix = entry.prefix();
        payUnit = entry.unit();
        boolean own = !prize.prefix().isEmpty() || !prize.unit().isEmpty();
        ftpPrizeValue = prize.value();
        ftpPrize = (own ? prize.prefix() : payPrefix) + num(prize.value())
                + ((own ? prize.unit() : payUnit).isEmpty() ? "" : " " + (own ? prize.unit() : payUnit));
        customLot = false;
        lotName = "";
        icon = "";
        lot.clear();
        worthText = "";
        minPay = gwEntry;
        raiseBy = 1;
        antiSnipe = false;
        watchPayments = true;
        ftpPays.clear();
        bids.clear();
        testBids.clear();
        gwTickets.clear();
        gwNames.clear();
        leader = null;
        copiedTpa = null;
        liveMsg = "";
        result = "";
        endTimeMs = System.currentTimeMillis() + secs * 1000L;
        live = true;
        hasAuction = true;
        save();
        playSound(false);
        writeObsText();
        Stream.onStart(this);
        return null;
    }

    public int gwEntries() {
        int n = 0;
        for (int t : gwTickets.values()) n += t;
        return n;
    }

    public int gwPlayers() {
        return gwTickets.size();
    }

    public String gwRule() {
        return "Pay " + money(gwEntry) + " to enter" + (gwMulti ? " (more = more entries)" : "") + " - win " + ftpPrize;
    }

    private void gwPayment(String name, long paid) {
        if (blocked(name)) {
            ftpPays.add(new FtpPay(name, paid, "blocked", false));
            liveMsg = name + " is blocked - doesn't count";
            liveMsgColor = Colors.RED;
            writeObsText();
            return;
        }
        if (paid < gwEntry) {
            ftpPays.add(new FtpPay(name, paid, "too low", false));
            liveMsg = name + " paid " + money(paid) + " - the entry is " + money(gwEntry);
            liveMsgColor = Colors.RED;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        int have = gwTickets.getOrDefault(key, 0);
        if (!gwMulti && have > 0) {
            ftpPays.add(new FtpPay(name, paid, "already in", false));
            liveMsg = name + " is already entered - refund " + money(paid);
            liveMsgColor = Colors.GOLD;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        int add = gwMulti ? (int) Math.max(1, Math.min(100, paid / gwEntry)) : 1;
        gwTickets.put(key, have + add);
        gwNames.put(key, name);
        ftpPays.add(new FtpPay(name, paid, add == 1 ? "entered" : "+" + add + " entries", true));
        liveMsg = name + " entered" + (add > 1 ? " x" + add : "") + "! " + gwEntries() + " entries";
        liveMsgColor = Colors.GREEN;
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP);
        writeObsText();
    }

    // the draw: a random entry wins, and "pay <winner> <prize>" is copied so it can be paid right away
    private void finishGiveaway(boolean cancelled) {
        live = false;
        finishedAtMs = System.currentTimeMillis();
        soldTo.clear();
        if (cancelled) {
            result = "CANCELLED";
            resultColor = Colors.MUTED;
            lastObsResult = "Giveaway cancelled";
        } else if (gwTickets.isEmpty()) {
            result = "No entries";
            resultColor = Colors.MUTED;
            lastObsResult = "Giveaway ended with no entries";
        } else {
            int pick = DRAW.nextInt(gwEntries());
            String winKey = null;
            for (Map.Entry<String, Integer> e : gwTickets.entrySet()) {
                pick -= e.getValue();
                if (pick < 0) {
                    winKey = e.getKey();
                    break;
                }
            }
            String winner = gwNames.get(winKey);
            leader = new Bid(winner, ftpPrizeValue, true, false);
            soldTo.add(leader);
            result = "WINNER: " + winner;
            resultColor = Colors.GOLD;
            copyPrize();
            lastObsResult = "GIVEAWAY WINNER: " + winner + " wins " + ftpPrize;
        }
        playSound(true);
        writeObsText();
        Stream.onResult(this);
    }

    // --- first to pay

    public String startFtp() {
        Amount need = parseAmount(config.ftpPayText == null ? "" : config.ftpPayText);
        if (!need.hasNumber() || need.value() <= 0) {
            return "Type how much they have to pay.";
        }
        Amount prize = parseAmount(config.ftpPrizeText == null ? "" : config.ftpPrizeText);
        if (!prize.hasNumber() || prize.value() <= 0) {
            return "Type the prize.";
        }
        int secs = config.ftpMin * 60 + config.ftpSec;
        if (secs <= 0) {
            return "Set a countdown above 0 seconds.";
        }
        ftp = true;
        ftpGo = false;
        dutch = false;
        gw = false;
        reserve = buyNow = 0;
        winnersN = 1;
        soldTo.clear();
        nextQueueAt = 0;
        lastTickSecs = -1;
        ftpNeed = need.value();
        ftpExact = config.ftpExact;
        payPrefix = need.prefix();
        payUnit = need.unit();
        boolean own = !prize.prefix().isEmpty() || !prize.unit().isEmpty();
        ftpPrizeValue = prize.value();
        ftpPrize = (own ? prize.prefix() : payPrefix) + num(prize.value())
                + ((own ? prize.unit() : payUnit).isEmpty() ? "" : " " + (own ? prize.unit() : payUnit));
        customLot = false;
        lotName = "";
        icon = "";
        lot.clear();
        worthText = "";
        minPay = ftpNeed;
        raiseBy = 1;
        antiSnipe = false;
        watchPayments = true;
        ftpPays.clear();
        bids.clear();
        testBids.clear();
        leader = null;
        copiedTpa = null;
        liveMsg = "";
        result = "";
        endTimeMs = System.currentTimeMillis() + secs * 1000L;
        live = true;
        hasAuction = true;
        save();
        playSound(false);
        writeObsText();
        Stream.onStart(this);
        Online.count("ftp");
        return null;
    }

    public String ftpRule() {
        return "First to pay " + (ftpExact ? "exactly " : "") + money(ftpNeed) + " wins " + ftpPrize;
    }

    private void ftpPayment(String name, long paid) {
        if (blocked(name)) {
            ftpPays.add(new FtpPay(name, paid, "blocked", false));
            liveMsg = name + " is blocked - doesn't count";
            liveMsgColor = Colors.RED;
            writeObsText();
            return;
        }
        if (!ftpGo) {
            ftpPays.add(new FtpPay(name, paid, "early", false));
            liveMsg = name + " paid too early - doesn't count";
            liveMsgColor = Colors.RED;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        boolean enough = ftpExact ? paid == ftpNeed : paid >= ftpNeed;
        if (!enough) {
            ftpPays.add(new FtpPay(name, paid, paid < ftpNeed ? "too low" : "not exact", false));
            liveMsg = name + " paid " + money(paid) + " - " + (paid < ftpNeed ? "not enough" : "not the exact amount");
            liveMsgColor = Colors.RED;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        ftpPays.add(new FtpPay(name, paid, "WINNER", true));
        leader = new Bid(name, paid, true, false);
        soldTo.add(leader);
        live = false;
        finishedAtMs = System.currentTimeMillis();
        result = "WINNER: " + name;
        resultColor = Colors.GOLD;
        liveMsg = "";
        if (config.ftpCopyPay && !testing) {
            copyPrize();
        }
        lastObsResult = "FIRST TO PAY WINNER: " + name + " wins " + ftpPrize;
        playSound(true);
        writeObsText();
        Stream.onResult(this);
    }

    // people who pay right after somebody already won, so they can be refunded
    public void ftpLate(String name, long paid) {
        ftpPays.add(new FtpPay(name, paid, "late", false));
    }

    public boolean ftpListening() {
        return instant() && !live && hasAuction && leader != null && System.currentTimeMillis() - finishedAtMs < 60_000;
    }

    // --- dutch auction: the price drops until someone pays it

    private String startDutch() {
        Amount start = parseAmount(lastLot.dutchStartText);
        dutch = true;
        dutchPrice = start.value();
        dutchDrop = parseAmount(lastLot.dutchDropText).value();
        dutchFloor = parseAmount(lastLot.dutchFloorText).value();
        dutchEveryMs = Math.max(1, lastLot.dutchEverySecs) * 1000L;
        payPrefix = start.prefix();
        payUnit = start.unit();
        minPay = dutchFloor;
        watchPayments = true;
        antiSnipe = false;
        endTimeMs = System.currentTimeMillis() + dutchEveryMs;
        return null;
    }

    private void dutchPayment(String name, long paid) {
        if (blocked(name)) {
            ftpPays.add(new FtpPay(name, paid, "blocked", false));
            liveMsg = name + " is blocked - doesn't count";
            liveMsgColor = Colors.RED;
            writeObsText();
            return;
        }
        if (paid < dutchPrice) {
            ftpPays.add(new FtpPay(name, paid, "too low", false));
            liveMsg = name + " paid " + money(paid) + " - the price is " + money(dutchPrice);
            liveMsgColor = Colors.RED;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        ftpPays.add(new FtpPay(name, paid, "WINNER", true));
        leader = new Bid(name, paid, true, false);
        bids.add(leader);
        soldTo.add(leader);
        live = false;
        finishedAtMs = System.currentTimeMillis();
        result = "SOLD to " + name + "!";
        resultColor = Colors.GOLD;
        liveMsg = "";
        if (Edition.COPY_TPA && config.copyTpa) {
            copyTpa();
        }
        lastObsResult = "SOLD: " + displayName() + " to " + name + " for " + money(paid);
        Sales.record(this, leader, "dutch");
        Online.count("sold");
        playSound(true);
        writeObsText();
        Stream.onResult(this);
        queueNext(false);
    }

    private void dutchTick() {
        long now = System.currentTimeMillis();
        if (now < endTimeMs) {
            return;
        }
        if (dutchPrice <= dutchFloor) {
            finish(false); // nobody paid even the lowest price
            return;
        }
        dutchPrice = Math.max(dutchFloor, dutchPrice - dutchDrop);
        endTimeMs = now + dutchEveryMs;
        liveMsg = "Price dropped to " + money(dutchPrice);
        liveMsgColor = Colors.AQUA;
        if (config.tickSounds) {
            sound(SoundEvents.NOTE_BLOCK_HAT, 1.4F);
        }
        writeObsText();
    }

    // "pay <winner> <prize>" with no slash, same as the tpa copy
    public boolean copyPrize() {
        String name = winnerName();
        if (name == null) {
            return false;
        }
        copiedTpa = "pay " + name + " " + ftpPrizeValue;
        Minecraft.getInstance().keyboardHandler.setClipboard(copiedTpa);
        return true;
    }

    public String run(Lot l) {
        String bad = check(l);
        if (bad != null) {
            return bad;
        }
        lastLot = l;
        ftp = false;
        ftpGo = false;
        dutch = false;
        gw = false;
        customLot = l.custom;
        lotName = l.name == null ? "" : l.name;
        icon = l.icon;
        lot.clear();
        for (LotItem li : l.items) {
            lot.add(new LotItem(li.id, li.count));
        }
        Amount pay = parseAmount(l.minPayText == null ? "" : l.minPayText);
        minPay = pay.value();
        payPrefix = pay.prefix();
        payUnit = pay.unit();
        raiseBy = Math.max(1, l.raiseBy);
        worthText = l.worthText == null ? "" : l.worthText.trim();
        reserve = l.reserveText == null || l.reserveText.isEmpty() ? 0 : parseAmount(l.reserveText).value();
        winnersN = Edition.PRO ? Math.max(1, Math.min(9, l.winners)) : 1;
        buyNow = winnersN > 1 || l.buyNowText == null || l.buyNowText.isEmpty() ? 0 : parseAmount(l.buyNowText).value();
        copiedTpa = null;
        antiSnipe = l.antiSnipe;
        watchPayments = l.watchPayments;
        bids.clear();
        testBids.clear();
        ftpPays.clear();
        soldTo.clear();
        leader = null;
        liveMsg = "";
        result = "";
        nextQueueAt = 0;
        lastTickSecs = -1;
        endTimeMs = System.currentTimeMillis() + l.durSecs * 1000L;
        if (Edition.PRO && l.dutch) {
            startDutch();
        }
        live = true;
        hasAuction = true;
        save();
        playSound(false);
        writeObsText();
        Stream.onStart(this);
        Online.count(dutch ? "dutch" : "auction");
        return null;
    }

    // --- saved custom lots

    public Preset findPreset(String name) {
        for (Preset p : config.presets) {
            if (p.name.equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }

    public void savePreset() {
        String name = config.customName.trim();
        Preset p = findPreset(name);
        if (p == null) {
            p = new Preset();
            config.presets.add(p);
        }
        p.name = name;
        p.icon = config.customIcon;
        p.items = new ArrayList<>();
        for (LotItem li : config.customItems) {
            p.items.add(new LotItem(li.id, li.count));
        }
        save();
    }

    public void loadPreset(Preset p) {
        config.customName = p.name;
        config.customIcon = p.icon == null ? "" : p.icon;
        config.customItems = new ArrayList<>();
        for (LotItem li : p.items) {
            config.customItems.add(new LotItem(li.id, li.count));
        }
        save();
    }

    public void deletePreset(Preset p) {
        config.presets.remove(p);
        save();
    }

    // --- bids

    public String placeBid(String name, long bid, boolean[] ok) {
        ok[0] = false;
        if (!live) {
            return "No auction running.";
        }
        long need = nextMin();
        if (bid < need) {
            return "Bid must be at least " + moneyExact(need) + ".";
        }
        String who = name.isBlank() ? "Viewer" : name.trim();
        Bid entry = new Bid(who, bid, false, false);
        bids.add(entry);
        Online.count("bid");
        if (leader == null || bid > leader.amount()) {
            leader = entry;
        }
        ok[0] = true;
        if (buyNow > 0 && bid >= buyNow) {
            boughtNow();
            return who + " bought it now!";
        }
        String msg = antiSnipe(who + " bid " + money(bid) + "!");
        writeObsText();
        Stream.onBid(this);
        return msg;
    }

    // a test payment from zzmods.com support (this copy was linked on the buyer's account): it goes through like a
    // real payment so it can be checked or screenshotted, but it's never saved as a sale, counted in the stats or posted
    public void testPayment(String name, String amountText, boolean tag) {
        Amount a = parseAmount(amountText);
        if (!a.hasNumber() || a.value() <= 0 || name == null || !name.matches("[.*]?[A-Za-z0-9_]{3,16}")) {
            return;
        }
        if (!live || !watchPayments) {
            Online.note("Test payment: " + name + " paid " + money(a.value()), "Start an auction to see it come in as a bid");
            return;
        }
        int before = bids.size(), soldBefore = soldTo.size();
        Bid lead = leader;
        testing = true;
        try {
            addPayment(name, a.value());
        } finally {
            testing = false;
        }
        for (int i = before; i < bids.size(); i++) testBids.put(bids.get(i), tag);
        for (int i = soldBefore; i < soldTo.size(); i++) testBids.put(soldTo.get(i), tag);
        if (leader != null && leader != lead) testBids.put(leader, tag);
    }

    // staff's "clear test payments": takes every test payment out again, real ones stay
    public int clearTests() {
        int n = (int) bids.stream().filter(this::isTest).count();
        if (testBids.isEmpty()) {
            return 0;
        }
        bids.removeIf(this::isTest);
        soldTo.removeIf(this::isTest);
        if (isTest(leader)) {
            leader = null;
            for (Bid b : bids) {
                if (b.paid() && !b.blocked() && b.amount() >= minPay && (leader == null || b.amount() > leader.amount())) {
                    leader = b;
                }
            }
        }
        testBids.clear();
        writeObsText();
        return n;
    }

    public boolean isTest(Bid b) {
        return b != null && testBids.containsKey(b);
    }

    public boolean testTagged(Bid b) {
        return b != null && Boolean.TRUE.equals(testBids.get(b));
    }

    // payments don't add up: each one is its own bid and the biggest single one leads
    public void addPayment(String name, long paid) {
        if (gw) {
            gwPayment(name, paid);
            return;
        }
        if (ftp) {
            ftpPayment(name, paid);
            return;
        }
        if (dutch) {
            dutchPayment(name, paid);
            return;
        }
        if (blocked(name)) {
            bids.add(new Bid(name, paid, true, true));
            liveMsg = name + " paid " + money(paid) + " but can't bid - refund them";
            liveMsgColor = Colors.RED;
            sound(SoundEvents.NOTE_BLOCK_BASS);
            writeObsText();
            return;
        }
        long need = nextMin();
        Bid entry = new Bid(name, paid, true, false);
        bids.add(entry);
        Online.count("bid");
        String msg = name + " paid " + money(paid) + "!";
        boolean counts = winnersN > 1 ? paid >= need : paid >= minPay && (leader == null || paid > leader.amount());
        if (counts) {
            if (leader == null || paid > leader.amount()) {
                leader = entry;
            }
            if (buyNow > 0 && paid >= buyNow) {
                liveMsg = msg;
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP);
                boughtNow();
                return;
            }
            msg = antiSnipe(msg);
            liveMsgColor = Colors.GREEN;
            Stream.onBid(this);
        } else {
            msg += paid < minPay ? " Below min pay." : winnersN > 1 ? " Not enough to get in." : " Not the top.";
            liveMsgColor = Colors.GOLD;
        }
        liveMsg = msg;
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP);
        writeObsText();
    }

    private void boughtNow() {
        finish(false);
        if (!soldTo.isEmpty()) {
            result = "BOUGHT NOW by " + soldTo.get(0).name() + "!";
        }
    }

    private String antiSnipe(String msg) {
        long remaining = endTimeMs - System.currentTimeMillis();
        int resetSecs = Math.max(1, config.antiSnipeSecs);
        if (antiSnipe && remaining < resetSecs * 1000L) {
            endTimeMs = System.currentTimeMillis() + resetSecs * 1000L;
            msg += " Timer reset to " + resetSecs + "s.";
        }
        return msg;
    }

    public void extend(int seconds) {
        if (live) {
            endTimeMs += seconds * 1000L;
        }
    }

    public void finish(boolean cancelled) {
        if (!live) {
            return;
        }
        if (gw) {
            finishGiveaway(cancelled); // Draw now / Cancel
            return;
        }
        live = false;
        finishedAtMs = System.currentTimeMillis();
        String what = displayName();
        Bid top = topBid();
        if (ftp) {
            result = cancelled ? "CANCELLED" : "No winner";
            resultColor = Colors.MUTED;
            lastObsResult = "First to pay " + (cancelled ? "cancelled" : "ended with no winner");
            playSound(true);
            writeObsText();
            Stream.onResult(this);
            return;
        }
        soldTo.clear();
        if (dutch) {
            result = cancelled ? "CANCELLED" : "No buyer - unsold";
            resultColor = Colors.MUTED;
            lastObsResult = cancelled ? "Auction cancelled" : "No buyer for " + what;
            playSound(true);
            writeObsText();
            Stream.onResult(this);
            queueNext(cancelled);
            return;
        }
        if (!cancelled) {
            for (Bid b : winningBids()) {
                if (reserve <= 0 || b.amount() >= reserve) {
                    soldTo.add(b);
                }
            }
        }
        if (cancelled) {
            result = "CANCELLED";
            resultColor = Colors.MUTED;
        } else if (!soldTo.isEmpty()) {
            result = soldTo.size() == 1 ? "SOLD to " + soldTo.get(0).name() + "!"
                    : "SOLD to " + soldTo.get(0).name() + " +" + (soldTo.size() - 1) + " more";
            resultColor = Colors.GOLD;
            if (Edition.COPY_TPA && config.copyTpa) {
                copyTpa();
            }
            for (Bid b : soldTo) {
                if (!isTest(b)) Sales.record(this, b, winnersN > 1 ? "multi" : "auction");
            }
        } else if (top != null && reserve > 0) {
            result = "Reserve not met - unsold";
            resultColor = Colors.MUTED;
        } else {
            result = "No bids - unsold";
            resultColor = Colors.MUTED;
        }
        lastObsResult = cancelled ? "Auction cancelled"
                : !soldTo.isEmpty() ? "SOLD: " + what + " to " + soldNames() + " for " + money(soldTo.get(0).amount())
                : top != null && reserve > 0 ? "Reserve not met on " + what
                : "No bids on " + what;
        playSound(true);
        writeObsText();
        Stream.onResult(this);
        queueNext(cancelled);
        Online.count(cancelled ? "cancelled" : !soldTo.isEmpty() && !soldTo.stream().allMatch(this::isTest) ? "sold" : "");
    }

    public String soldNames() {
        StringBuilder sb = new StringBuilder();
        for (Bid b : soldTo) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(b.name());
        }
        return sb.toString();
    }

    // after a lot ends the next queued one starts by itself; cancelling pauses the queue
    private void queueNext(boolean cancelled) {
        if (!Edition.PRO || config.queue.isEmpty()) {
            return;
        }
        if (cancelled) {
            queuePaused = true;
        } else if (!queuePaused) {
            nextQueueAt = System.currentTimeMillis() + Math.max(0, config.queueBreakSecs) * 1000L;
        }
    }

    public void resumeQueue() {
        queuePaused = false;
        nextQueueAt = System.currentTimeMillis();
    }

    // null for a typed bid with no name ("Viewer") - there's nobody to tpa to
    public String winnerName() {
        Bid top = !soldTo.isEmpty() ? soldTo.get(0) : live || !ftp ? null : topBid();
        if (top == null || top.name().isBlank() || (!top.paid() && top.name().equals("Viewer"))) {
            return null;
        }
        return top.name().trim().split("\\s+")[0];
    }

    // no slash on purpose: you open chat with "/" so pasting a slash would give "//tpa"
    public boolean copyTpa() {
        String name = winnerName();
        if (name == null || !Edition.COPY_TPA) {
            return false;
        }
        copiedTpa = "tpa " + name;
        Minecraft.getInstance().keyboardHandler.setClipboard(copiedTpa);
        return true;
    }

    public boolean hasWorth() {
        return !worthText.isEmpty();
    }

    public String worth() {
        Amount w = parseAmount(worthText);
        if (!w.hasNumber()) {
            return worthText;
        }
        boolean own = !w.prefix().isEmpty() || !w.unit().isEmpty();
        String prefix = own ? w.prefix() : payPrefix;
        String unit = own ? w.unit() : payUnit;
        return prefix + num(w.value()) + (unit.isEmpty() ? "" : " " + unit);
    }

    public void tick() {
        if (live && Edition.PRO && config.tickSounds && !dutch && !(ftp && ftpGo)) {
            // "going once, going twice": a bell on each of the last three seconds
            int secs = remainingSeconds();
            if (secs > 3) {
                lastTickSecs = -1;
            } else if (secs >= 1 && secs != lastTickSecs) {
                lastTickSecs = secs;
                sound(SoundEvents.NOTE_BLOCK_BELL, secs == 1 ? 1.5F : secs == 2 ? 1.2F : 1.0F);
            }
        }
        if (live && ftp) {
            if (!ftpGo && System.currentTimeMillis() >= endTimeMs) {
                ftpGo = true;
                liveMsg = "GO! First to pay " + money(ftpNeed) + " wins";
                liveMsgColor = Colors.GREEN;
                sound(SoundEvents.PLAYER_LEVELUP);
            }
        } else if (live && dutch) {
            dutchTick();
        } else if (live && gw) {
            if (System.currentTimeMillis() >= endTimeMs) {
                finishGiveaway(false);
            }
        } else if (live && System.currentTimeMillis() >= endTimeMs) {
            finish(false);
        } else if (!live && Edition.PRO && nextQueueAt > 0 && System.currentTimeMillis() >= nextQueueAt) {
            nextQueueAt = 0;
            if (!queuePaused && !config.queue.isEmpty()) {
                String err = runQueued();
                if (err != null) {
                    toast("Queue: " + err);
                    queuePaused = true;
                }
            }
        }
        writeObsText();
        Stream.tick(this);
    }

    // --- premium in-game keys

    public void key(int which) {
        if (Online.off()) {
            return;
        }
        switch (which) {
            case 1 -> {
                if (live && !dutch && !(ftp && ftpGo)) {
                    extend(config.extendSecs);
                    toast("+" + config.extendSecs + "s");
                }
            }
            case 2 -> {
                if (live && !instant()) {
                    finish(false);
                    toast(result);
                }
            }
            case 3 -> {
                if (live) {
                    finish(true);
                    toast("Cancelled");
                }
            }
            case 4 -> copyAnnouncement();
            default -> {
            }
        }
    }

    public String announcement() {
        if (!live) {
            return hasAuction ? result : "";
        }
        if (ftp) {
            return ftpGo ? "GO! " + ftpRule() + " - pay now!" : ftpRule() + " - starts in " + formatTime(remainingSeconds());
        }
        if (dutch) {
            return "DUTCH AUCTION: " + displayName() + " - now " + money(dutchPrice) + ", first to pay it wins! Drops in "
                    + remainingSeconds() + "s";
        }
        Bid top = topBid();
        String text = config.announceText == null || config.announceText.isBlank() ? DEFAULT_ANNOUNCE : config.announceText;
        return text.replace("{item}", displayName())
                .replace("{bid}", top == null ? "none yet" : money(top.amount()))
                .replace("{bidder}", top == null ? "-" : top.name())
                .replace("{time}", formatTime(remainingSeconds()))
                .replace("{next}", moneyNumber(nextMin()))
                .replace("{worth}", hasWorth() ? worth() : "")
                .replace("{buynow}", buyNow > 0 ? money(buyNow) : "");
    }

    public void copyAnnouncement() {
        String text = announcement();
        if (text.isEmpty()) {
            toast("No auction to announce");
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(text.length() > 250 ? text.substring(0, 250) : text);
        toast("Announcement copied - open chat and paste");
    }

    // config/auction_live.txt, for an OBS text source set to "read from file"
    private void writeObsText() {
        if (!config.obsFile) {
            return;
        }
        String text = lastObsResult;
        if (live && gw) {
            text = "GIVEAWAY\n" + gwRule() + "\nEntries: " + gwEntries() + "\nEnds in: " + formatTime(remainingSeconds());
        } else if (live && ftp) {
            text = ftpGo ? "FIRST TO PAY - GO!\n" + ftpRule()
                    : "FIRST TO PAY\n" + ftpRule() + "\nStarts in: " + formatTime(remainingSeconds());
        } else if (live && dutch) {
            text = "DUTCH AUCTION: " + displayName() + "\n"
                    + (showContents() ? "Includes: " + contents() + "\n" : "")
                    + "Price now: " + money(dutchPrice) + "\n"
                    + "Next drop in: " + formatTime(remainingSeconds()) + "\n"
                    + "First to pay the price wins";
        } else if (live) {
            Bid top = topBid();
            text = "AUCTION: " + displayName() + "\n"
                    + (showContents() ? "Includes: " + contents() + "\n" : "")
                    + (hasWorth() && config.worthInObs ? "Worth: " + worth() + "\n" : "")
                    + "Time left: " + formatTime(remainingSeconds()) + "\n"
                    + "Top bid: " + (top == null ? "no bids yet" : money(top.amount()) + " by " + top.name()) + "\n"
                    + "Next bid: " + moneyExact(nextMin()) + "+"
                    + (winnersN > 1 ? "\n" + winnersN + " winners" : "")
                    + (buyNow > 0 ? "\nBuy now: " + money(buyNow) : "")
                    + (reserve > 0 && (top == null || top.amount() < reserve) ? "\nReserve not met yet" : "");
        }
        if (text.equals(lastObsText)) {
            return;
        }
        lastObsText = text;
        try {
            Files.createDirectories(configDir());
            Files.writeString(configDir().resolve("auction_live.txt"), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // --- numbers

    public static Amount parseAmount(String text) {
        Matcher m = AMOUNT.matcher(text.trim());
        if (!m.matches()) {
            return new Amount(0, "", text.trim(), false);
        }
        BigDecimal value = new BigDecimal(m.group(2).replace(",", ""));
        int zeros = switch (m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT)) {
            case "k" -> 3;
            case "m" -> 6;
            case "b" -> 9;
            default -> 0;
        };
        long v = value.movePointRight(zeros).setScale(0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Long.MAX_VALUE / 4)).longValue();
        return new Amount(v, m.group(1), m.group(4).trim(), true);
    }

    public static String full(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    // 12,005 / 125.5k / 1.25m / 2.5b - rounded down so it never shows more than was paid
    public static String compact(long v) {
        if (v >= 1_000_000_000L) return shorten(v, 9, 2) + "b";
        if (v >= 1_000_000L) return shorten(v, 6, 2) + "m";
        if (v >= 100_000L) return shorten(v, 3, 1) + "k";
        return full(v);
    }

    private static String shorten(long v, int places, int decimals) {
        return BigDecimal.valueOf(v).movePointLeft(places).setScale(decimals, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString();
    }

    public String money(long v) {
        return moneyNumber(v) + (payUnit.isEmpty() ? "" : " " + payUnit);
    }

    public String moneyNumber(long v) {
        return payPrefix + num(v);
    }

    private String num(long v) {
        return config.shortNumbers ? compact(v) : full(v);
    }

    public String moneyExact(long v) {
        return payPrefix + full(v) + (payUnit.isEmpty() ? "" : " " + payUnit);
    }

    // --- size

    public float maxScale() {
        Minecraft mc = Minecraft.getInstance();
        float byW = mc.getWindow().getGuiScaledWidth() / (float) (AuctionScreen.W + 4);
        float byH = mc.getWindow().getGuiScaledHeight() / 272.0F;
        return Math.max(0.5F, Math.min(3.0F, Math.min(byW, byH)));
    }

    public static final float DEFAULT_SCALE = 0.6F; // the panel's size on a fresh install (and after Ctrl+0)

    public float scale() {
        return Math.max(0.5F, Math.min(maxScale(), config.scale <= 0 ? DEFAULT_SCALE : config.scale));
    }

    // +1 bigger, -1 smaller, 0 reset
    public void adjustScale(int dir) {
        float next = dir == 0 ? DEFAULT_SCALE : Math.round((scale() + 0.1F * dir) * 10) / 10.0F;
        config.scale = Math.max(0.5F, Math.min(maxScale(), next));
        toast("Size " + Math.round(config.scale * 100) + "%");
        save();
    }

    // a short message on the panel title / card footer (size changes, keys, copies)
    public String sizeToast() {
        return toastText != null && System.currentTimeMillis() < toastUntilMs ? toastText : null;
    }

    public static String formatTime(int secs) {
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    // --- sounds

    public static void playSound(boolean finished) {
        if (finished) {
            sound(SoundEvents.PLAYER_LEVELUP);
        } else {
            sound(SoundEvents.NOTE_BLOCK_PLING);
        }
    }

    public static void click() {
        sound(SoundEvents.UI_BUTTON_CLICK);
    }

    private static void sound(SoundEvent event) {
        sound(event, 1.0F);
    }

    private static void sound(SoundEvent event, float pitch) {
        Config c = INSTANCE.config;
        if (c.sounds && c.volume > 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, c.volume / 100.0F));
        }
    }

    private static void sound(Holder<SoundEvent> event) {
        sound(event.value());
    }

    private static void sound(Holder<SoundEvent> event, float pitch) {
        sound(event.value(), pitch);
    }

    // "Right Shift" -> "RShift"
    public static String keyName(int key) {
        if (key < 0) {
            return "Not set";
        }
        String name = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        if (name.startsWith("Right ")) return "R" + name.substring(6);
        if (name.startsWith("Left ")) return "L" + name.substring(5);
        return name;
    }
}
