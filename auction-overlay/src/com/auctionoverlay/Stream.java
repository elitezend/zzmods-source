package com.auctionoverlay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

// Premium stream extras:
//  - an animated OBS overlay: config/auction_overlay/overlay.html (add it as a Browser source, "Local file"),
//    fed by overlay_data.js which the mod rewrites whenever something changes
//  - Discord webhook posts when an auction starts, gets a new top bid, or ends
public final class Stream {
    private static final Gson GSON = new Gson();
    private static final Pattern WEBHOOK = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?(?:discord|discordapp)\\.com/api/webhooks/\\d+/[A-Za-z0-9_\\-]+$");
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Auction Overlay webhook");
        t.setDaemon(true);
        return t;
    });
    private static final int OVERLAY_VERSION = 1;

    private static String lastJson;
    private static boolean pendingBid;
    private static long lastBidPost;
    private static boolean wroteHtml;

    private Stream() {}

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("auction_overlay");
    }

    public static boolean validWebhook(String url) {
        return url != null && WEBHOOK.matcher(url.trim()).matches();
    }

    // "…/AbCd" so the full link never shows on stream
    public static String maskedWebhook(String url) {
        if (!validWebhook(url)) {
            return "Not set";
        }
        String t = url.trim();
        return "Set (..." + t.substring(t.length() - 4) + ")";
    }

    // ---- Discord

    public static void onStart(AuctionState st) {
        if (!Edition.PRO || !st.config.hookStart) {
            return;
        }
        String what = esc(st.displayName());
        if (st.gw) {
            post("**Giveaway!** " + esc(st.gwRule()) + " - draw in " + AuctionState.formatTime(st.remainingSeconds()));
        } else if (st.ftp) {
            post("**First to pay!** " + esc(st.ftpRule()) + " - starts in " + AuctionState.formatTime(st.remainingSeconds()));
        } else if (st.dutch) {
            post("**Dutch auction:** " + what + " - starts at " + esc(st.money(st.dutchPrice)) + " and drops "
                    + esc(st.money(st.dutchDrop)) + " every " + st.dutchEveryMs / 1000 + "s. First to pay the price wins!");
        } else {
            post("**Auction started:** " + what + (st.hasWorth() ? " (worth " + esc(st.worth()) + ")" : "")
                    + " - min pay " + esc(st.moneyExact(st.minPay)) + ", " + AuctionState.formatTime(st.remainingSeconds()) + " on the clock"
                    + (st.buyNow > 0 ? ", buy now " + esc(st.money(st.buyNow)) : "")
                    + (st.winnersN > 1 ? ", " + st.winnersN + " winners" : ""));
        }
    }

    public static void onBid(AuctionState st) {
        if (Edition.PRO && st.config.hookBids && !st.testing) { // test payments from support are never posted
            pendingBid = true; // sent from tick(), at most every 5 seconds
        }
    }

    public static void onResult(AuctionState st) {
        pendingBid = false;
        if (!Edition.PRO || !st.config.hookSold || st.testing || st.isTest(st.leader) || st.soldTo().stream().anyMatch(st::isTest)) {
            return;
        }
        if (st.gw) {
            post(st.leader != null && !st.live ? "**Giveaway winner:** " + esc(st.leader.name()) + " wins " + esc(st.ftpPrize)
                    : "Giveaway ended - " + esc(st.result));
            return;
        }
        if (st.ftp) {
            post(st.leader != null && !st.live ? "**First to pay winner:** " + esc(st.leader.name()) + " wins " + esc(st.ftpPrize)
                    : "First to pay ended - " + esc(st.result));
            return;
        }
        if (!st.soldTo().isEmpty()) {
            AuctionState.Bid b = st.soldTo().get(0);
            post("**SOLD!** " + esc(st.displayName()) + " to " + esc(st.soldNames()) + " for " + esc(st.money(b.amount()))
                    + (st.soldTo().size() > 1 ? " and up" : ""));
        } else {
            post(esc(st.displayName()) + ": " + esc(st.result));
        }
    }

    public static void tick(AuctionState st) {
        if (pendingBid && st.live && System.currentTimeMillis() - lastBidPost >= 5000) {
            pendingBid = false;
            lastBidPost = System.currentTimeMillis();
            AuctionState.Bid top = st.topBid();
            if (top != null) {
                post("New top bid on " + esc(st.displayName()) + ": **" + esc(st.money(top.amount())) + "** by " + esc(top.name())
                        + " - " + AuctionState.formatTime(st.remainingSeconds()) + " left");
            }
        }
        if (Edition.PRO && st.config.overlay) {
            writeOverlay(st);
        }
    }

    public static void test(String url) {
        send(url, "Auction Overlay is connected to this channel.");
    }

    private static void post(String content) {
        String url = AuctionState.INSTANCE.config.webhook;
        if (validWebhook(url) && !Tour.active()) {
            send(url.trim(), content);
        }
    }

    private static void send(String url, String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content.length() > 1900 ? content.substring(0, 1900) : content);
        body.addProperty("username", "Auction House");
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray()); // never ping @everyone, whatever a player calls themselves
        body.add("allowed_mentions", mentions);
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        NET.submit(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "AuctionOverlay (zzmods.com)");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }
                c.getResponseCode();
            } catch (IOException | RuntimeException ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private static String esc(String s) {
        return s == null ? "" : s.replaceAll("([\\\\*_~`|>#\\[\\]()])", "\\\\$1").replace("@", "@​");
    }

    // ---- OBS overlay

    public static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] cmd = os.contains("win") ? new String[]{"explorer.exe", dir.toString()}
                    : os.contains("mac") ? new String[]{"open", dir.toString()} : new String[]{"xdg-open", dir.toString()};
            new ProcessBuilder(cmd).start();
        } catch (IOException ignored) {
        }
    }

    private static void writeOverlay(AuctionState st) {
        JsonObject d = new JsonObject();
        AuctionState.Config c = st.config;
        boolean show = st.hasAuction && (st.live || System.currentTimeMillis() - st.finishedAtMs <= c.resultSecs * 1000L);
        d.addProperty("show", show);
        d.addProperty("mode", st.ftp ? "ftp" : st.dutch ? "dutch" : "auction");
        d.addProperty("live", st.live);
        d.addProperty("title", Style.title());
        d.addProperty("item", st.displayName());
        d.addProperty("contents", st.showContents() ? st.contents() : "");
        d.addProperty("worth", st.hasWorth() && c.worthInObs ? st.worthLabel() + " " + st.worth() : "");
        AuctionState.Bid top = st.topBid();
        d.addProperty("top", top == null ? "" : st.money(top.amount()));
        d.addProperty("bidder", top == null ? "" : top.name());
        d.addProperty("next", st.live && !st.instant() ? st.moneyExact(st.nextMin()) + "+" : "");
        d.addProperty("endsAt", st.live ? st.endTimeMs : 0);
        d.addProperty("result", st.live ? "" : st.result);
        d.addProperty("winners", st.winnersN);
        d.addProperty("buyNow", st.buyNow > 0 ? st.money(st.buyNow) : "");
        d.addProperty("reserveMet", st.reserve <= 0 || (top != null && top.amount() >= st.reserve));
        d.addProperty("price", st.dutch ? st.money(st.dutchPrice) : st.ftp ? st.money(st.ftpNeed) : "");
        d.addProperty("rule", st.ftp ? st.ftpRule() : st.dutch ? "Drops " + st.money(st.dutchDrop) + " every " + st.dutchEveryMs / 1000 + "s" : "");
        d.addProperty("go", st.ftp && st.ftpGo);
        d.addProperty("bids", st.bids.size());
        d.addProperty("accent", String.format("#%06X", Colors.ACCENT_A & 0xFFFFFF));
        d.addProperty("accent2", String.format("#%06X", Colors.ACCENT_B & 0xFFFFFF));
        String json = GSON.toJson(d);
        if (json.equals(lastJson)) {
            return;
        }
        lastJson = json;
        try {
            Files.createDirectories(folder());
            if (!wroteHtml) {
                Path html = folder().resolve("overlay.html");
                if (!Files.exists(html) || !Files.readString(html).contains("overlay-version:" + OVERLAY_VERSION)) {
                    Files.writeString(html, OVERLAY_HTML, StandardCharsets.UTF_8);
                }
                wroteHtml = true;
            }
            Files.writeString(folder().resolve("overlay_data.js"), "window.__auction = " + json + ";\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static final String OVERLAY_HTML = """
            <!doctype html>
            <!-- Auction Overlay for OBS (overlay-version:1). Add a Browser source, tick "Local file", pick this file.
                 Size 520 x 260. The mod updates overlay_data.js next to it. Get it on zzmods.com -->
            <html><head><meta charset="utf-8"><title>Auction overlay</title>
            <style>
              :root { --a: #8b5cf6; --b: #22d3ee; }
              html, body { margin: 0; background: transparent; font-family: "Segoe UI", Inter, Roboto, system-ui, sans-serif; color: #f3f5f8; }
              .card { position: absolute; left: 16px; top: 16px; width: 480px; padding: 18px 22px 20px; border-radius: 20px;
                background: rgba(16, 18, 23, .86); border: 1px solid rgba(255,255,255,.12); box-shadow: 0 18px 50px rgba(0,0,0,.45);
                backdrop-filter: blur(14px); transition: opacity .4s, transform .4s; }
              .card.hide { opacity: 0; transform: translateY(12px); }
              .top { display: flex; align-items: center; gap: 10px; font-size: 13px; letter-spacing: .14em; text-transform: uppercase; color: #8c94a3; }
              .live { display: inline-flex; align-items: center; gap: 6px; color: #f87171; font-weight: 700; }
              .live i { width: 8px; height: 8px; border-radius: 50%; background: #f87171; animation: blink 1s infinite; }
              .item { font-size: 28px; font-weight: 700; margin: 6px 0 2px; letter-spacing: -.01em; }
              .sub { font-size: 14px; color: #8c94a3; min-height: 18px; }
              .worth { display: inline-block; margin-top: 8px; padding: 3px 10px; border-radius: 999px; font-size: 13px; font-weight: 600;
                color: #f6c453; background: rgba(246,196,83,.12); border: 1px solid rgba(246,196,83,.4); }
              .row { display: flex; gap: 22px; margin-top: 14px; }
              .col { flex: 1; }
              .lab { font-size: 12px; letter-spacing: .14em; text-transform: uppercase; color: #8c94a3; }
              .big { font-size: 40px; font-weight: 800; letter-spacing: -.02em; font-variant-numeric: tabular-nums; }
              .bid { color: #3ddc97; }
              .who { font-size: 15px; color: #7dd3fc; margin-top: 2px; min-height: 19px; }
              .bar { height: 4px; border-radius: 4px; background: rgba(255,255,255,.1); margin-top: 16px; overflow: hidden; }
              .bar b { display: block; height: 100%; width: 100%; background: linear-gradient(90deg, var(--a), var(--b)); transition: width .25s linear; }
              .warn .big.t { color: #fde68a; animation: blink .5s infinite; }
              .pulse { animation: pulse .6s; }
              .result { position: absolute; inset: 0; display: none; place-items: center; border-radius: 20px; text-align: center;
                background: linear-gradient(135deg, rgba(16,18,23,.94), rgba(16,18,23,.82)); }
              .result.on { display: grid; animation: pop .5s; }
              .result .r1 { font-size: 46px; font-weight: 900; letter-spacing: .04em; background: linear-gradient(90deg, var(--a), var(--b));
                -webkit-background-clip: text; color: transparent; }
              .result .r2 { font-size: 18px; color: #f3f5f8; margin-top: 4px; }
              .foot { margin-top: 10px; font-size: 12px; color: #5c6370; display: flex; justify-content: space-between; }
              @keyframes blink { 50% { opacity: .35; } }
              @keyframes pulse { 0% { transform: scale(1); } 35% { transform: scale(1.08); } 100% { transform: scale(1); } }
              @keyframes pop { 0% { opacity: 0; transform: scale(.92); } 100% { opacity: 1; transform: scale(1); } }
            </style></head>
            <body>
            <div class="card hide" id="card">
              <div class="top"><span class="live" id="live"><i></i>Live</span><span id="title"></span></div>
              <div class="item" id="item"></div>
              <div class="sub" id="sub"></div>
              <span class="worth" id="worth" hidden></span>
              <div class="row">
                <div class="col"><div class="lab" id="tl">Time left</div><div class="big t" id="time">00:00</div></div>
                <div class="col"><div class="lab" id="bl">Top bid</div><div class="big bid" id="top">-</div><div class="who" id="who"></div></div>
              </div>
              <div class="bar"><b id="bar"></b></div>
              <div class="foot"><span id="next"></span><span id="extra"></span></div>
              <div class="result" id="result"><div><div class="r1" id="r1">SOLD</div><div class="r2" id="r2"></div></div></div>
            </div>
            <script>
            let last = "", lastTop = "", span = 0;
            const $ = (id) => document.getElementById(id);
            function load() {
              const s = document.createElement("script");
              s.src = "overlay_data.js?t=" + Date.now();
              s.onload = s.onerror = () => { s.remove(); render(); };
              document.body.appendChild(s);
            }
            function mmss(ms) { const t = Math.max(0, Math.ceil(ms / 1000)); return String(Math.floor(t / 60)).padStart(2, "0") + ":" + String(t % 60).padStart(2, "0"); }
            function render() {
              const d = window.__auction;
              if (!d) return;
              document.documentElement.style.setProperty("--a", d.accent);
              document.documentElement.style.setProperty("--b", d.accent2);
              $("card").classList.toggle("hide", !d.show);
              $("title").textContent = d.mode === "ftp" ? "First to pay" : d.mode === "dutch" ? "Dutch auction" : d.title;
              $("live").style.visibility = d.live ? "visible" : "hidden";
              $("item").textContent = d.mode === "ftp" ? d.rule : d.item;
              $("sub").textContent = d.contents ? "Includes: " + d.contents : d.mode === "dutch" ? d.rule : "";
              $("worth").hidden = !d.worth; $("worth").textContent = d.worth;
              const ftp = d.mode === "ftp", dutch = d.mode === "dutch";
              $("tl").textContent = dutch ? "Next drop" : ftp ? (d.go ? "Go!" : "Starts in") : "Time left";
              $("bl").textContent = dutch ? "Price now" : ftp ? "Pay" : "Top bid";
              const topText = dutch || ftp ? d.price : (d.top || "-");
              if (topText !== lastTop) { $("top").classList.remove("pulse"); void $("top").offsetWidth; if (lastTop) $("top").classList.add("pulse"); lastTop = topText; }
              $("top").textContent = topText;
              $("who").textContent = !dutch && !ftp && d.bidder ? "by " + d.bidder : "";
              $("next").textContent = d.next ? "Next bid " + d.next : "";
              $("extra").textContent = [d.winners > 1 ? d.winners + " winners" : "", d.buyNow ? "Buy now " + d.buyNow : "", d.reserveMet ? "" : "Reserve not met"].filter(Boolean).join("  ·  ");
              const r = !d.live && d.result;
              $("result").classList.toggle("on", !!r);
              if (r) { const sold = /^(SOLD|BOUGHT|WINNER)/.test(d.result); $("r1").textContent = sold ? (d.result.startsWith("WINNER") ? "WINNER" : "SOLD") : "ENDED"; $("r2").textContent = d.result.replace(/^(SOLD|BOUGHT NOW|WINNER:?)\\s*/, ""); }
            }
            function clock() {
              const d = window.__auction;
              if (d && d.live && d.endsAt) {
                const left = d.endsAt - Date.now();
                if (d.mode === "ftp" && d.go) { $("time").textContent = "PAY!"; } else { $("time").textContent = mmss(left); }
                if (left > span) span = left;
                $("bar").style.width = Math.max(0, Math.min(100, 100 * left / Math.max(1, span))) + "%";
                $("card").classList.toggle("warn", left < 10000 && !(d.mode === "ftp" && d.go));
              } else { span = 0; $("card").classList.remove("warn"); }
            }
            setInterval(load, 400); setInterval(clock, 100); load();
            </script>
            </body></html>
            """;
}
