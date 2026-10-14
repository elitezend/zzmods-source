package com.auctionoverlay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Talks to zzmods.com: "is there a newer version?" (shown in game) and, if "Share anonymous stats" is on,
// a few usage counts (minutes used, auctions run / sold, ...) under a random install id. No names, chat or servers.
public final class Online {
    private static final String SITE = "https://zzmods.netlify.app";
    private static final Gson GSON = new Gson();
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Auction Overlay update check");
        t.setDaemon(true);
        return t;
    });

    // counts kept in config/auction_overlay.json (config.usage)
    public static final class Usage {
        public long minutes, sessions, auctions, sold, cancelled, ftp, dutch, bids, queued;
    }

    private static volatile String latestVersion;
    private static volatile String latestNotes = "";
    public static final String DISCORD = "https://discord.gg/B64z86e9ZY";
    // updates are downloaded from this mod's page on the website (the edition this copy is)
    public static final String DOWNLOAD = SITE + "/get.html?mod=auction&edition=" + Edition.NAME.toLowerCase(Locale.ROOT);
    private static volatile String latestUrl = DOWNLOAD;
    private static volatile String latestId = "";
    private static String shownId = "";
    private static long inWorldTicks;
    private static boolean joinedThisWorld, checkedThisRun, toastShown;
    private static long toastUntil, toastAt;

    private static String noteTitle, noteSub, candidate;
    private static long noteUntil, noteAt, linkStartedMs;
    private static int linkFailures; // checks in a row that couldn't reach zzmods.com

    private Online() {}

    // ---- "Link account": lets zzmods.com support send this copy test payments (the player approves it on the site)

    public static void startLink() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (c.linkSecret == null || c.linkedEmail != null) {
            c.linkSecret = UUID.randomUUID().toString().replace("-", "");
            c.linkedEmail = null;
        }
        linkStartedMs = System.currentTimeMillis();
        linkFailures = 0;
        AuctionState.INSTANCE.save();
        String url = SITE + "/account.html#link=" + c.linkSecret + "&mod=auction";
        // Gamble Tracker installed and not linked yet: link it with the same click
        String other = System.getProperty("zzmods.gambleCandidate");
        if (other != null && other.matches("[a-f0-9]{32}")) {
            url += "&also=" + other + "&alsomod=gamble";
            System.setProperty("zzmods.gambleAdopt", other);
        }
        open(url);
    }

    public static boolean isLinked() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        return c.linkSecret != null && c.linkedEmail != null;
    }

    public static boolean waitingForLink() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        return c.linkSecret != null && c.linkedEmail == null;
    }

    public static int linkFailures() {
        return linkFailures;
    }

    // the other mod's "link me too" handshake (see startLink)
    private static void shareCandidate() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (c.linkSecret != null) {
            System.clearProperty("zzmods.auctionCandidate");
            candidate = null;
            return;
        }
        if (candidate == null) {
            candidate = UUID.randomUUID().toString().replace("-", "");
        }
        System.setProperty("zzmods.auctionCandidate", candidate);
        if (candidate.equals(System.getProperty("zzmods.auctionAdopt"))) {
            System.clearProperty("zzmods.auctionAdopt");
            c.linkSecret = candidate;
            c.linkedEmail = null;
            linkStartedMs = System.currentTimeMillis();
            candidate = null;
            AuctionState.INSTANCE.save();
        }
    }

    public static void unlink() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        String secret = c.linkSecret;
        c.linkSecret = null;
        c.linkedEmail = null;
        AuctionState.INSTANCE.save();
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
        AuctionState.Config c = AuctionState.INSTANCE.config;
        return c.linkSecret == null ? "Not linked to an account" : c.linkedEmail == null ? "Waiting - click Link on the site" : c.linkedEmail;
    }

    private static void pollLink() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        JsonObject body = new JsonObject();
        body.addProperty("secret", c.linkSecret);
        body.addProperty("v", version());
        body.addProperty("ed", Edition.NAME.toLowerCase(Locale.ROOT));
        if (c.shareStats) {
            // linked copies show up with their Minecraft name and numbers on the staff Players page
            try {
                body.addProperty("player", Minecraft.getInstance().getUser().getName());
            } catch (RuntimeException ignored) {
            }
            body.add("stats", GSON.toJsonTree(c.usage));
        }
        String secret = c.linkSecret, json = GSON.toJson(body);
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
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (!secret.equals(c.linkSecret)) return; // unlinked or re-linked meanwhile
        boolean linked = o.has("linked") && o.get("linked").getAsBoolean();
        if (!linked) {
            if (c.linkedEmail != null) { // unlinked on the account page (or a newer copy took this one's place)
                c.linkSecret = null;
                c.linkedEmail = null;
                c.licenseOk = false;
                c.banned = false;
                AuctionState.INSTANCE.save();
            }
            return;
        }
        if (c.linkedEmail == null) {
            c.linkedEmail = o.has("email") ? o.get("email").getAsString() : "your account";
            AuctionState.INSTANCE.save();
            note("Linked to " + c.linkedEmail, "zzmods support can now send this copy test payments");
        }
        boolean banned = o.has("banned") && o.get("banned").getAsBoolean();
        if (banned != c.banned) {
            c.banned = banned;
            AuctionState.INSTANCE.save();
        }
        if (o.has("licensed")) {
            // the server says whether the linked account owns this edition
            boolean lic = o.get("licensed").getAsBoolean();
            long t = System.currentTimeMillis();
            boolean changed = lic != AuctionState.INSTANCE.config.licenseOk || t - AuctionState.INSTANCE.config.licenseCheckedAt > 3600_000;
            boolean unlocked = lic && !AuctionState.INSTANCE.config.licenseOk;
            AuctionState.INSTANCE.config.licenseOk = lic;
            AuctionState.INSTANCE.config.licenseCheckedAt = t;
            if (changed) AuctionState.INSTANCE.save();
            if (unlocked && paid()) note("Auction Overlay " + Edition.NAME + " unlocked", "Thanks for buying it - have fun!");
        }
        if (o.has("payments") && o.get("payments").isJsonArray()) {
            for (var e : o.getAsJsonArray("payments")) {
                if (!e.isJsonObject()) continue;
                JsonObject p = e.getAsJsonObject();
                String type = p.has("type") ? p.get("type").getAsString() : "pay";
                if (type.equals("msg")) {
                    note(text(p, "title", "Message from zzmods"), text(p, "text", ""), 12_000);
                } else if (type.equals("clear")) {
                    int n = AuctionState.INSTANCE.clearTests();
                    note("Test payments cleared", n == 0 ? "There weren't any" : n + (n == 1 ? " test payment" : " test payments") + " removed", 6000);
                } else if (p.has("name") && p.has("amount")) {
                    AuctionState.INSTANCE.testPayment(p.get("name").getAsString(), p.get("amount").getAsString(),
                            !p.has("tag") || p.get("tag").getAsBoolean());
                }
            }
        }
    }

    // a small card at the top of the screen for a few seconds
    public static void note(String title, String sub) {
        note(title, sub, 6000);
    }

    public static void note(String title, String sub, long ms) {
        noteTitle = title;
        noteSub = sub;
        noteAt = System.currentTimeMillis();
        noteUntil = noteAt + ms;
        AuctionState.playSound(false);
    }

    private static String text(JsonObject o, String key, String fallback) {
        String s = o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString().replaceAll("[\\p{Cntrl}§]", "").trim() : "";
        return s.isEmpty() ? fallback : s.length() > 120 ? s.substring(0, 120) : s;
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("auctionoverlay")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    }

    public static String minecraft() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    // ---- counting (the tour's test auction doesn't count)

    public static void count(String what) {
        if (Tour.active()) {
            return;
        }
        Usage u = AuctionState.INSTANCE.config.usage;
        switch (what) {
            case "auction" -> u.auctions++;
            case "sold" -> u.sold++;
            case "cancelled" -> u.cancelled++;
            case "ftp" -> u.ftp++;
            case "dutch" -> u.dutch++;
            case "bid" -> u.bids++;
            case "queued" -> u.queued++;
            default -> {
            }
        }
    }

    // called every client tick
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (mc.level == null || mc.player == null) {
            joinedThisWorld = false;
            return;
        }
        if (!joinedThisWorld) {
            joinedThisWorld = true;
            c.usage.sessions++;
            if (!checkedThisRun) { // always: a required update has to reach everyone (sends only the version)
                checkedThisRun = true;
                checkLatest();
            }
        }
        inWorldTicks++;
        shareCandidate();
        if (c.linkSecret != null) {
            // waiting for the Link click: ask every 4 seconds for 10 minutes; once linked, every 30 seconds
            if (c.linkedEmail == null && linkStartedMs == 0) linkStartedMs = System.currentTimeMillis(); // restarted while waiting
            if (c.linkedEmail == null && System.currentTimeMillis() - linkStartedMs > 600_000) {
                c.linkSecret = null;
                AuctionState.INSTANCE.save();
            } else if (inWorldTicks % (c.linkedEmail == null || locked() ? 80 : 600) == 0) {
                pollLink();
            }
        }
        if (inWorldTicks % 6000 == 0) { // every 5 minutes, so a notice reaches people mid-game
            checkLatest();
        }
        if (inWorldTicks % 1200 == 0) { // a minute in game
            c.usage.minutes++;
            AuctionState.INSTANCE.save();
        }
        if (c.shareStats && (inWorldTicks == 1200 || inWorldTicks % 12000 == 0)) { // after a minute, then every 10
            sendStats();
        }
    }

    private static void checkLatest() {
        checkAnnouncements();
        NET.submit(() -> {
            try {
                JsonObject o = request("GET", SITE + "/api/latest?mod=auction&v=" + version(), null);
                if (o != null) {
                    readLatest(o);
                }
            } catch (IOException | RuntimeException ignored) {
            }
        });
    }

    // announcements from the dashboard (shown from the game menu) - the same timing as the update check
    private static void checkAnnouncements() {
        NET.submit(() -> {
            try {
                Announcements.read(request("GET", SITE + "/api/announcements?mod=auction&ed=" + Edition.NAME.toLowerCase(Locale.ROOT) + "&v=" + version(), null));
            } catch (IOException | RuntimeException ignored) {
            }
        });
    }

    private static void sendStats() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (c.installId == null || !c.installId.matches("[a-f0-9]{32}")) {
            c.installId = UUID.randomUUID().toString().replace("-", "");
            AuctionState.INSTANCE.save();
        }
        JsonObject body = GSON.toJsonTree(c.usage).getAsJsonObject();
        body.addProperty("id", c.installId);
        body.addProperty("mod", "auction");
        body.addProperty("v", version());
        body.addProperty("ed", Edition.NAME.toLowerCase(Locale.ROOT));
        body.addProperty("mc", minecraft());
        body.addProperty("look", Style.look().name());
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
            if (rv.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") && !rv.equals(AuctionState.INSTANCE.config.requiredUpdate)) {
                AuctionState.INSTANCE.config.requiredUpdate = rv;
                AuctionState.INSTANCE.config.requiredNotes = r.has("notes") ? r.get("notes").getAsString() : "";
                AuctionState.INSTANCE.save();
            }
        } else if (AuctionState.INSTANCE.config.requiredUpdate != null) {
            AuctionState.INSTANCE.config.requiredUpdate = null; // lifted
            AuctionState.INSTANCE.config.requiredNotes = null;
            AuctionState.INSTANCE.save();
        }
        String v = o.has("version") ? o.get("version").getAsString() : null;
        if (v == null || !v.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            return;
        }
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
            c.setRequestProperty("User-Agent", "AuctionOverlay/" + version());
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (c.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                String text = new String(in.readNBytes(20_000), StandardCharsets.UTF_8);
                return GSON.fromJson(text, JsonObject.class);
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- the update notice

    // the newer version, or null when this copy is up to date (or we don't know yet)
    public static String update() {
        String v = latestVersion;
        return v != null && AuctionState.INSTANCE.config.checkUpdates && newer(v, version()) ? v : null;
    }

    public static String noticeId() {
        return latestId;
    }

    public static String notes() {
        return latestNotes;
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("[.+-]"), y = b.split("[.+-]");
        for (int i = 0; i < 3; i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) {
                return p > q;
            }
        }
        return false;
    }

    private static int num(String s) {
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

    // why this copy is locked ("link", "banned", "check", "buy", "offline"), or null when it may be used.
    // Every edition needs a linked zzmods account (Free too - unlinking switches it off); only paid ones also need to
    // own it. The Personal build (the owner's own copy) never locks.
    public static String lockReason() {
        if (Edition.NAME.equals("Personal")) return null;
        if (AuctionState.INSTANCE.config.linkSecret == null || AuctionState.INSTANCE.config.linkedEmail == null) return "link";
        if (AuctionState.INSTANCE.config.banned) return "banned";
        if (!paid()) return null;
        if (!AuctionState.INSTANCE.config.licenseOk) return AuctionState.INSTANCE.config.licenseCheckedAt > 0 ? "buy" : "check";
        if (System.currentTimeMillis() - AuctionState.INSTANCE.config.licenseCheckedAt > LICENSE_GRACE) return "offline";
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
        open(SITE + "/get.html?mod=auction&edition=" + Edition.NAME.toLowerCase(Locale.ROOT));
    }

    public static void relink() {
        unlink();
        startLink();
    }

    public static void checkNow() {
        if (AuctionState.INSTANCE.config.linkSecret != null) pollLink();
    }

    // a small card while the copy is locked
    public static void renderLocked(GuiGraphicsExtractor g, String key, int y) {
        Font font = Minecraft.getInstance().font;
        String r = lockReason();
        String l1 = "Auction Overlay " + Edition.NAME + " is locked";
        String l2 = ("banned".equals(r) ? "This zzmods account is banned" : "buy".equals(r) ? "This account doesn't own " + Edition.NAME : "offline".equals(r) ? "Couldn't check your license"
                : "check".equals(r) ? "Checking your license..." : "Link your zzmods account to use it") + " - press " + key;
        int w = Math.max(Txt.width(font, l1), Txt.width(font, l2)) + 20;
        Style.panel(g, 6, y, w, 30, 235);
        Txt.draw(g, font, l1, 16, y + 6, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, l2, 16, y + 17, Colors.MUTED, Style.shadow);
    }

    public static boolean blocked() {
        String v = AuctionState.INSTANCE.config.requiredUpdate;
        return v != null && newer(v, version());
    }

    public static String requiredVersion() {
        return AuctionState.INSTANCE.config.requiredUpdate == null ? "" : AuctionState.INSTANCE.config.requiredUpdate;
    }

    public static String requiredNotes() {
        return AuctionState.INSTANCE.config.requiredNotes == null ? "" : AuctionState.INSTANCE.config.requiredNotes;
    }

    public static void openRequired() {
        open(DOWNLOAD);
    }

    // a small card while the mod is off
    public static void renderBlocked(GuiGraphicsExtractor g, String key) {
        Font font = Minecraft.getInstance().font;
        String l1 = "Auction Overlay is off until you update to " + requiredVersion();
        String l2 = "Press " + key + " for details - get it on zzmods.com";
        int w = Math.max(Txt.width(font, l1), Txt.width(font, l2)) + 20;
        Style.panel(g, 6, 6, w, 30, 235);
        Txt.draw(g, font, l1, 16, 6 + 6, Colors.YELLOW, Style.shadow);
        Txt.draw(g, font, l2, 16, 6 + 17, Colors.MUTED, Style.shadow);
    }

    public static void openDownloads() {
        open(latestUrl);
    }

    static void open(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] cmd = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", url}
                    : os.contains("mac") ? new String[]{"open", url} : new String[]{"xdg-open", url};
            new ProcessBuilder(cmd).start();
        } catch (IOException ignored) {
        }
    }

    // notifications in the top left (slide in, stay, slide out): update available, linked / unlocked, new announcement
    public static void renderToast(GuiGraphicsExtractor g) {
        if (Minecraft.getInstance().gui.screen() != null) return;
        long now = System.currentTimeMillis();
        if (noteTitle != null && now < noteUntil) {
            notice(g, "ZZMODS", Colors.AQUA, noteTitle, noteSub, null, noteAt, noteUntil);
            return;
        }
        String v = update();
        if (v == null) return;
        if (!toastShown || !latestId.equals(shownId)) { // once per game start, and again for each new notice
            toastShown = true;
            shownId = latestId;
            toastAt = now;
            toastUntil = now + 20_000;
            AuctionState.playSound(false);
        }
        if (now > toastUntil) return;
        String sub = latestNotes.isBlank() ? "You have " + version() + "." : latestNotes;
        String hint = Updater.ownJar() != null ? "Open the panel to update in one click" : "Get it on zzmods.com - the download page is in About";
        notice(g, "UPDATE AVAILABLE", Colors.GREEN, "Auction Overlay " + v + " is out!", sub, hint, toastAt, toastUntil);
    }

    private static void notice(GuiGraphicsExtractor g, String head, int headColor, String title, String sub, String hint, long at, long until) {
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();
        float in = Math.min(1F, (now - at) / 250F), out = Math.min(1F, (until - now) / 250F);
        float k = Math.max(0F, Math.min(in, out));
        k = 1 - (1 - k) * (1 - k); // ease out
        int maxW = Math.min(240, g.guiWidth() / 2);
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (sub != null && !sub.isBlank()) lines.addAll(Tour.wrap(font, sub, maxW - 24));
        while (lines.size() > 3) lines.remove(lines.size() - 1);
        int w = Txt.width(font, head) + 24;
        w = Math.max(w, Txt.width(font, title) + 24);
        for (String l : lines) w = Math.max(w, Txt.width(font, l) + 24);
        if (hint != null) w = Math.max(w, Txt.width(font, hint) + 24);
        w = Math.min(maxW, w);
        int h = 30 + lines.size() * 10 + (hint != null ? 11 : 0);
        int x = Math.round(6 - (w + 12) * (1 - k)), y = 6;
        Style.panel(g, x, y, w, h, 240);
        g.fill(x + 1, y + 3, x + 3, y + h - 3, 0xFF000000 | headColor); // a coloured edge on the left
        Txt.draw(g, font, head, x + 10, y + 6, headColor, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, title, w - 18), x + 10, y + 17, Colors.TITLE, Style.shadow);
        int ly = y + 29;
        for (String l : lines) {
            Txt.draw(g, font, Txt.cut(font, l, w - 18), x + 10, ly, Colors.TEXT, Style.shadow);
            ly += 10;
        }
        if (hint != null) Txt.draw(g, font, Txt.cut(font, hint, w - 18), x + 10, ly + 1, Colors.MUTED, Style.shadow);
    }
}
