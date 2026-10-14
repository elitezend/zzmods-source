package com.gambletracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Talks to zzmods.com: "is there a newer version?" (shown in game) and, if "Share anonymous stats" is on,
// a few counts (minutes used, payments tracked, players queued, winners paid) under a random install id.
// No names, chat, amounts or servers. Settings live in config/gamble_tracker_online.properties.
public final class GambleOnline {
    private static final String SITE = "https://zzmods.netlify.app";
    private static final Gson GSON = new Gson();
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Gamble Tracker update check");
        t.setDaemon(true);
        return t;
    });

    public static boolean checkUpdates = true;
    public static boolean shareStats = true;
    private static String installId = "";
    // "Link account" (settings > ABOUT): the secret this copy uses with zzmods.com, and the masked email once linked
    public static String linkSecret, linkedEmail;
    // a required update zzmods.com asked for (kept so it still applies offline), and its note
    private static String requiredUpdate, requiredNotes;
    // paid editions: did the linked account own this edition at the last check, and when was that
    private static boolean licenseOk;
    private static boolean banned; // the linked zzmods account was banned by staff: the mod is off
    private static long licenseCheckedAt;
    private static String noteTitle, noteSub, candidate;
    private static long noteUntil, linkStartedMs, nextPoll;
    private static int linkFailures; // checks in a row that couldn't reach zzmods.com
    private static long minutes, sessions, payments, players, wins;
    private static boolean loaded;

    private static volatile String latestVersion;
    private static volatile String latestNotes = "";
    public static final String DISCORD = "https://discord.gg/B64z86e9ZY";
    // updates are downloaded from this mod's page on the website (the edition this copy is)
    public static final String DOWNLOAD = SITE + "/get.html?mod=gamble&edition=" + Edition.NAME.toLowerCase(Locale.ROOT);
    private static volatile String latestUrl = DOWNLOAD;
    private static volatile String latestId = "";
    private static String shownId = "";
    private static long nextCheck;
    private static boolean inWorld, checkedThisRun, toastShown;
    private static long worldMs, lastFrame, nextSend, toastUntil;

    private GambleOnline() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("gamble_tracker_online.properties");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file())) {
            p.load(r);
        } catch (IOException ignored) {
        }
        checkUpdates = !"false".equals(p.getProperty("checkUpdates"));
        shareStats = !"false".equals(p.getProperty("shareStats"));
        installId = p.getProperty("installId", "");
        linkSecret = p.getProperty("linkSecret", "").matches("[a-f0-9]{32}") ? p.getProperty("linkSecret") : null;
        requiredUpdate = p.getProperty("requiredUpdate", "").matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") ? p.getProperty("requiredUpdate") : null;
        requiredNotes = p.getProperty("requiredNotes", "");
        licenseOk = "true".equals(p.getProperty("licenseOk"));
        banned = "true".equals(p.getProperty("banned"));
        licenseCheckedAt = num(p, "licenseCheckedAt");
        linkedEmail = linkSecret != null && !p.getProperty("linkedEmail", "").isEmpty() ? p.getProperty("linkedEmail") : null;
        minutes = num(p, "minutes");
        sessions = num(p, "sessions");
        payments = num(p, "payments");
        players = num(p, "players");
        wins = num(p, "wins");
    }

    private static long num(Properties p, String k) {
        try {
            return Math.max(0, Long.parseLong(p.getProperty(k, "0")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void save() {
        load();
        Properties p = new Properties();
        p.setProperty("checkUpdates", String.valueOf(checkUpdates));
        p.setProperty("shareStats", String.valueOf(shareStats));
        p.setProperty("installId", installId);
        if (linkSecret != null) p.setProperty("linkSecret", linkSecret);
        if (requiredUpdate != null) p.setProperty("requiredUpdate", requiredUpdate);
        p.setProperty("licenseOk", String.valueOf(licenseOk));
        p.setProperty("banned", String.valueOf(banned));
        p.setProperty("licenseCheckedAt", String.valueOf(licenseCheckedAt));
        if (requiredUpdate != null && requiredNotes != null) p.setProperty("requiredNotes", requiredNotes);
        if (linkedEmail != null) p.setProperty("linkedEmail", linkedEmail);
        p.setProperty("minutes", String.valueOf(minutes));
        p.setProperty("sessions", String.valueOf(sessions));
        p.setProperty("payments", String.valueOf(payments));
        p.setProperty("players", String.valueOf(players));
        p.setProperty("wins", String.valueOf(wins));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Gamble Tracker: update check + anonymous usage counts");
        } catch (IOException ignored) {
        }
    }

    // ---- "Link account": lets zzmods.com support send this copy test payments (the player approves it on the site)

    public static void startLink() {
        load();
        if (linkSecret == null || linkedEmail != null) {
            linkSecret = UUID.randomUUID().toString().replace("-", "");
            linkedEmail = null;
        }
        linkStartedMs = System.currentTimeMillis();
        linkFailures = 0;
        nextPoll = 0;
        save();
        String url = SITE + "/account.html#link=" + linkSecret + "&mod=gamble";
        // Auction Overlay installed and not linked yet: link it with the same click
        String other = System.getProperty("zzmods.auctionCandidate");
        if (other != null && other.matches("[a-f0-9]{32}")) {
            url += "&also=" + other + "&alsomod=auction";
            System.setProperty("zzmods.auctionAdopt", other);
        }
        open(url);
    }

    public static boolean isLinked() {
        load();
        return linkSecret != null && linkedEmail != null;
    }

    public static boolean waitingForLink() {
        load();
        return linkSecret != null && linkedEmail == null;
    }

    public static int linkFailures() {
        return linkFailures;
    }

    // the other mod's "link me too" handshake (see startLink)
    private static void shareCandidate(long now) {
        if (linkSecret != null) {
            System.clearProperty("zzmods.gambleCandidate");
            candidate = null;
            return;
        }
        if (candidate == null) {
            candidate = UUID.randomUUID().toString().replace("-", "");
        }
        System.setProperty("zzmods.gambleCandidate", candidate);
        if (candidate.equals(System.getProperty("zzmods.gambleAdopt"))) {
            System.clearProperty("zzmods.gambleAdopt");
            linkSecret = candidate;
            linkedEmail = null;
            linkStartedMs = now;
            nextPoll = now + 3000;
            candidate = null;
            save();
        }
    }

    public static void unlink() {
        String secret = linkSecret;
        linkSecret = null;
        linkedEmail = null;
        save();
        if (secret != null) {
            JsonObject body = new JsonObject();
            body.addProperty("secret", secret);
            String json = GSON.toJson(body);
            NET.submit(() -> {
                try {
                    request("POST", SITE + "/api/modlink/unlink", json);
                } catch (IOException | RuntimeException ignored) {
                }
            });
        }
    }

    public static String linkState() {
        load();
        return linkSecret == null ? "Not linked to an account" : linkedEmail == null ? "Waiting - click Link on the site" : linkedEmail;
    }

    private static void pollLink() {
        JsonObject body = new JsonObject();
        body.addProperty("secret", linkSecret);
        body.addProperty("v", version());
        body.addProperty("ed", Edition.NAME.toLowerCase(Locale.ROOT));
        if (shareStats) {
            // linked copies show up with their Minecraft name and numbers on the staff Players page
            try {
                body.addProperty("player", Minecraft.getInstance().getUser().getName());
            } catch (RuntimeException ignored) {
            }
            JsonObject stats = new JsonObject();
            stats.addProperty("minutes", minutes);
            stats.addProperty("sessions", sessions);
            stats.addProperty("payments", payments);
            stats.addProperty("players", players);
            stats.addProperty("wins", wins);
            body.add("stats", stats);
        }
        String secret = linkSecret, json = GSON.toJson(body);
        NET.submit(() -> {
            try {
                JsonObject o = request("POST", SITE + "/api/modlink/poll", json);
                Minecraft.getInstance().execute(() -> {
                    if (o == null) {
                        linkFailures++;
                    } else {
                        linkFailures = 0;
                        readLink(secret, o);
                    }
                });
            } catch (IOException | RuntimeException e) {
                Minecraft.getInstance().execute(() -> linkFailures++);
            }
        });
    }

    private static void readLink(String secret, JsonObject o) {
        if (!secret.equals(linkSecret)) return; // unlinked or re-linked meanwhile
        boolean linked = o.has("linked") && o.get("linked").getAsBoolean();
        if (!linked) {
            if (linkedEmail != null) { // unlinked on the account page (or a newer copy took this one's place)
                linkSecret = null;
                linkedEmail = null;
                licenseOk = false;
                banned = false;
                save();
            }
            return;
        }
        if (linkedEmail == null) {
            linkedEmail = o.has("email") ? o.get("email").getAsString() : "your account";
            save();
            note("Linked to " + linkedEmail, "zzmods support can now send this copy test payments");
        }
        boolean ban = o.has("banned") && o.get("banned").getAsBoolean();
        if (ban != banned) {
            banned = ban;
            save();
        }
        if (o.has("licensed")) {
            // the server says whether the linked account owns this edition
            boolean lic = o.get("licensed").getAsBoolean();
            long t = System.currentTimeMillis();
            boolean changed = lic != licenseOk || t - licenseCheckedAt > 3600_000;
            boolean unlocked = lic && !licenseOk;
            licenseOk = lic;
            licenseCheckedAt = t;
            if (changed) save();
            if (unlocked && paid()) note("Gamble Tracker " + Edition.NAME + " unlocked", "Thanks for buying it - have fun!");
        }
        if (o.has("payments") && o.get("payments").isJsonArray()) {
            for (var e : o.getAsJsonArray("payments")) {
                if (!e.isJsonObject()) continue;
                JsonObject p = e.getAsJsonObject();
                String type = p.has("type") ? p.get("type").getAsString() : "pay";
                if (type.equals("msg")) {
                    note(text(p, "title", "Message from zzmods"), text(p, "text", ""), 12_000);
                } else if (type.equals("clear")) {
                    int n = GambleState.INSTANCE.clearTests();
                    note("Test payments cleared", n == 0 ? "There weren't any" : n + (n == 1 ? " test payment" : " test payments") + " removed", 6000);
                } else if (p.has("name") && p.has("amount")) {
                    GambleState.INSTANCE.testPayment(p.get("name").getAsString(), p.get("amount").getAsString(),
                            !p.has("tag") || p.get("tag").getAsBoolean());
                }
            }
        }
    }

    // a small card for a few seconds
    public static void note(String title, String sub) {
        note(title, sub, 6000);
    }

    public static void note(String title, String sub, long ms) {
        noteTitle = title;
        noteSub = sub;
        noteUntil = System.currentTimeMillis() + ms;
    }

    private static String text(JsonObject o, String key, String fallback) {
        String s = o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString().replaceAll("[\\p{Cntrl}§]", "").trim() : "";
        return s.isEmpty() ? fallback : s.length() > 120 ? s.substring(0, 120) : s;
    }

    public static void countPayment(boolean inRange) {
        load();
        payments++;
        if (inRange) players++;
    }

    public static void countWin() {
        load();
        wins++;
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("gambletracker")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    }

    public static String minecraft() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    // called every frame the HUD is drawn
    public static void frame() {
        load();
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.player == null) {
            inWorld = false;
            return;
        }
        if (!inWorld) {
            inWorld = true;
            lastFrame = now;
            sessions++;
            nextSend = now + 60_000;
            if (!checkedThisRun) { // always: a required update has to reach everyone (sends only the version)
                checkedThisRun = true;
                checkLatest();
            }
            nextCheck = now + 300_000;
        }
        if (now >= nextCheck) { // every 5 minutes, so a notice reaches people mid-game
            nextCheck = now + 300_000;
            checkLatest();
        }
        shareCandidate(now);
        if (linkSecret != null) {
            // waiting for the Link click: ask every 4 seconds for 10 minutes; once linked, every 30 seconds
            if (linkedEmail == null && linkStartedMs == 0) linkStartedMs = now; // restarted while waiting
            if (linkedEmail == null && now - linkStartedMs > 600_000) {
                linkSecret = null;
                save();
            } else if (now >= nextPoll) {
                nextPoll = now + (linkedEmail == null || locked() ? 4000 : 30_000);
                pollLink();
            }
        }
        worldMs += Math.min(1000, now - lastFrame); // a long pause (alt-tab, loading) doesn't count
        lastFrame = now;
        if (worldMs >= 60_000) {
            worldMs -= 60_000;
            minutes++;
            save();
        }
        if (shareStats && now >= nextSend) {
            nextSend = now + 600_000;
            sendStats();
        }
    }

    private static void checkLatest() {
        // announcements from the dashboard (shown from the game menu) - the same timing as the update check
        NET.submit(() -> {
            try {
                Announcements.read(request("GET", SITE + "/api/announcements?mod=gamble&ed=" + Edition.NAME.toLowerCase(Locale.ROOT) + "&v=" + version(), null));
            } catch (IOException | RuntimeException ignored) {
            }
        });
        NET.submit(() -> {
            try {
                JsonObject o = request("GET", SITE + "/api/latest?mod=gamble&v=" + version(), null);
                if (o != null) readLatest(o);
            } catch (IOException | RuntimeException ignored) {
            }
        });
    }

    private static void sendStats() {
        if (!installId.matches("[a-f0-9]{32}")) {
            installId = UUID.randomUUID().toString().replace("-", "");
            save();
        }
        JsonObject body = new JsonObject();
        body.addProperty("id", installId);
        body.addProperty("mod", "gamble");
        body.addProperty("v", version());
        body.addProperty("ed", Edition.NAME.toLowerCase(Locale.ROOT));
        body.addProperty("mc", minecraft());
        body.addProperty("minutes", minutes);
        body.addProperty("sessions", sessions);
        body.addProperty("payments", payments);
        body.addProperty("players", players);
        body.addProperty("wins", wins);
        String json = GSON.toJson(body);
        NET.submit(() -> {
            try {
                JsonObject o = request("POST", SITE + "/api/telemetry", json);
                if (o != null && o.has("latest") && o.get("latest").isJsonObject()) {
                    readLatest(o.getAsJsonObject("latest"));
                }
            } catch (IOException | RuntimeException ignored) {
            }
        });
    }

    private static void readLatest(JsonObject o) {
        if (o.has("required") && o.get("required").isJsonObject()) {
            JsonObject r = o.getAsJsonObject("required");
            String rv = r.has("version") ? r.get("version").getAsString() : "";
            if (rv.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") && !rv.equals(requiredUpdate)) {
                requiredUpdate = rv;
                requiredNotes = r.has("notes") ? r.get("notes").getAsString() : "";
                save();
            }
        } else if (requiredUpdate != null) {
            requiredUpdate = null; // lifted
            requiredNotes = null;
            save();
        }
        String v = o.has("version") ? o.get("version").getAsString() : null;
        if (v == null || !v.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) return;
        latestNotes = o.has("notes") ? o.get("notes").getAsString() : "";
        String url = o.has("url") ? o.get("url").getAsString() : "";
        if (url.startsWith("https://zzmods.")) {
            // the mod's download page, opened on this copy's edition
            latestUrl = url.contains("get.html") && !url.contains("edition=") ? url + (url.contains("?") ? "&" : "?") + "edition=" + Edition.NAME.toLowerCase(Locale.ROOT) : url;
        }
        latestId = o.has("id") ? o.get("id").getAsString() : v;
        latestVersion = v;
    }

    private static JsonObject request(String method, String url, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "GambleTracker/" + version());
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return GSON.fromJson(new String(in.readNBytes(20_000), StandardCharsets.UTF_8), JsonObject.class);
            }
        } finally {
            c.disconnect();
        }
    }

    public static String noticeId() {
        return latestId;
    }

    public static String notes() {
        return latestNotes;
    }

    // the newer version, or null when up to date / not known
    public static String update() {
        String v = latestVersion;
        return v != null && checkUpdates && newer(v, version()) ? v : null;
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("[.+-]"), y = b.split("[.+-]");
        for (int i = 0; i < 3; i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    // ---- required update (staff "Required update" page): this version stays off until the new one is installed

    // ---- paid editions only work while linked to the zzmods account that bought them (a shared jar is useless)

    private static final long LICENSE_GRACE = 7L * 24 * 3600_000; // offline: keeps working a week after the last check

    public static boolean paid() {
        return !Edition.NAME.equals("Free") && !Edition.NAME.equals("Personal");
    }

    // why this copy is locked ("link", "banned", "check", "buy", "offline"), or null when it may be used
    // every edition needs a linked zzmods account (Free too - unlinking switches it off); only paid ones also need to
    // own it. The Personal build (the owner's own copy) never locks.
    public static String lockReason() {
        load();
        if (Edition.NAME.equals("Personal")) return null;
        if (linkSecret == null || linkedEmail == null) return "link";
        if (banned) return "banned";
        if (!paid()) return null;
        if (!licenseOk) return licenseCheckedAt > 0 ? "buy" : "check";
        if (System.currentTimeMillis() - licenseCheckedAt > LICENSE_GRACE) return "offline";
        return null;
    }

    public static boolean locked() {
        return lockReason() != null;
    }

    // off = a required update is waiting, or the license is missing
    public static boolean off() {
        return blocked() || locked();
    }

    public static void openBuy() {
        open(SITE + "/get.html?mod=gamble&edition=" + Edition.NAME.toLowerCase(Locale.ROOT));
    }

    public static void relink() {
        unlink();
        startLink();
    }

    public static void checkNow() {
        if (linkSecret != null) pollLink();
    }

    // a small card while the copy is locked
    public static void renderLocked(GuiGraphicsExtractor g, String key, int y) {
        Font font = Minecraft.getInstance().font;
        String r = lockReason();
        String l1 = "Gamble Tracker " + Edition.NAME + " is locked";
        String l2 = ("banned".equals(r) ? "This zzmods account is banned" : "buy".equals(r) ? "This account doesn't own " + Edition.NAME : "offline".equals(r) ? "Couldn't check your license"
                : "check".equals(r) ? "Checking your license..." : "Link your zzmods account to use it") + " - press " + key;
        int w = Math.max(Txt.width(font, l1), Txt.width(font, l2)) + 20;
        Style.panel(g, 6, y, w, 30, 235);
        Txt.draw(g, font, l1, 16, y + 6, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, l2, 16, y + 17, Colors.MUTED, Style.shadow);
    }

    public static boolean blocked() {
        load();
        String v = requiredUpdate;
        return v != null && newer(v, version());
    }

    public static String requiredVersion() {
        return requiredUpdate == null ? "" : requiredUpdate;
    }

    public static String requiredNotes() {
        return requiredNotes == null ? "" : requiredNotes;
    }

    public static void openRequired() {
        open(DOWNLOAD);
    }

    // a small card while the mod is off
    public static void renderBlocked(GuiGraphicsExtractor g, String key) {
        Font font = Minecraft.getInstance().font;
        String l1 = "Gamble Tracker is off until you update to " + requiredVersion();
        String l2 = "Press " + key + " for details - get it on zzmods.com";
        int w = Math.max(Txt.width(font, l1), Txt.width(font, l2)) + 20;
        Style.panel(g, 6, 40, w, 30, 235);
        Txt.draw(g, font, l1, 16, 40 + 6, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, l2, 16, 40 + 17, Colors.MUTED, Style.shadow);
    }

    public static void openDownloads() {
        open(latestUrl);
    }

    // links from announcements: only ever our own site
    static void openUrl(String url) {
        if (url != null && url.startsWith("https://zzmods.")) open(url);
    }

    private static void open(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] cmd = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", url}
                    : os.contains("mac") ? new String[]{"open", url} : new String[]{"xdg-open", url};
            new ProcessBuilder(cmd).start();
        } catch (IOException ignored) {
        }
    }

    // a card at the top of the screen for a few seconds after joining, once per game start
    public static void renderToast(GuiGraphicsExtractor g) {
        if (noteTitle != null && System.currentTimeMillis() < noteUntil && Minecraft.getInstance().gui.screen() == null) {
            Font font = Minecraft.getInstance().font;
            int w = Math.min(g.guiWidth() - 20, Math.max(Txt.width(font, noteTitle), Txt.width(font, noteSub)) + 24);
            int x = (g.guiWidth() - w) / 2;
            Style.panel(g, x, 62, w, 34, 240);
            Txt.draw(g, font, Txt.cut(font, noteTitle, w - 24), x + 12, 70, Colors.AQUA, Style.shadow);
            Txt.draw(g, font, Txt.cut(font, noteSub, w - 24), x + 12, 82, Colors.MUTED, Style.shadow);
            return;
        }
        String v = update();
        if (v == null || Minecraft.getInstance().gui.screen() != null) return;
        long now = System.currentTimeMillis();
        if (!toastShown || !latestId.equals(shownId)) { // once per game start, and again for each new notice
            toastShown = true;
            shownId = latestId;
            toastUntil = now + 20_000;
        }
        if (now > toastUntil) return;
        Font font = Minecraft.getInstance().font;
        String title = "Gamble Tracker " + v + " is out!";
        String sub = latestNotes.isBlank() ? "You have " + version() + "." : latestNotes;
        String hint = Updater.ownJar() != null ? "Open the panel to update in one click" : "Get it on zzmods.com - the download page is in About";
        int w = Math.min(g.guiWidth() - 20, Math.max(Txt.width(font, title), Math.max(Txt.width(font, hint), Math.min(260, Txt.width(font, sub)))) + 24);
        int x = (g.guiWidth() - w) / 2, y = 62;
        Style.panel(g, x, y, w, 46, 240);
        Txt.draw(g, font, Txt.cut(font, title, w - 24), x + 12, y + 8, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, sub, w - 24), x + 12, y + 20, Colors.TEXT, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, hint, w - 24), x + 12, y + 32, Colors.MUTED, Style.shadow);
    }
}
