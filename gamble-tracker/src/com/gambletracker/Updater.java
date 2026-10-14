package com.gambletracker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// One-click update. Only after the player clicks "Update now": downloads the new jar from zzmods.com, checks its
// SHA-256 and its Ed25519 signature against UpdateKey (signed on the owner's PC, so a hacked website can't push a fake
// jar), saves it next to this jar as <name>.jar.zzupdate (Fabric ignores that), and when Minecraft closes a small
// helper waits for the game to exit, then swaps the old jar for the new one. The new version runs from the next start.
public final class Updater {
    private static final String SITE = "https://zzmods.netlify.app";
    private static final String MOD = "gamble", MOD_ID = "gambletracker", PREFIX = "GambleTracker-";
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final ExecutorService NET = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Gamble Tracker updater");
        t.setDaemon(true);
        return t;
    });

    public enum State { IDLE, WORKING, READY, FAILED }

    private static volatile State state = State.IDLE;
    private static volatile String message = "";
    private static Path swapOld, swapPending, swapTarget;

    private Updater() {}

    public static State state() {
        return state;
    }

    public static String message() {
        return message;
    }

    private static String secret() {
        return GambleOnline.linkSecret;
    }

    // the jar this mod was loaded from (null when it isn't a plain jar, e.g. in a dev setup)
    static Path ownJar() {
        try {
            var c = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
            if (c == null || c.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
            for (Path p : c.getOrigin().getPaths()) {
                if (p.getFileName().toString().endsWith(".jar") && Files.isRegularFile(p)) return p;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    // on start: an update downloaded last time that didn't get swapped in (game crashed) is armed again
    public static void init() {
        Path jar = ownJar();
        if (jar == null) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(jar.getParent(), PREFIX + "*.jar.zzupdate")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                String v = name.replaceAll("^" + PREFIX + "[A-Za-z]+-([0-9.]+)-mc.*$", "$1");
                if (GambleOnline.newer(v, GambleOnline.version())) {
                    arm(jar, p, p.resolveSibling(name.substring(0, name.length() - ".zzupdate".length())));
                    state = State.READY;
                    message = "Update " + v + " is ready - restart Minecraft to use it.";
                } else {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static void start() {
        if (state == State.WORKING || state == State.READY) return;
        Path jar = ownJar();
        if (jar == null) {
            fail("This copy can't update itself - get the new version on zzmods.com.");
            return;
        }
        state = State.WORKING;
        message = "Checking zzmods.com...";
        JsonObject body = new JsonObject();
        body.addProperty("mod", MOD);
        body.addProperty("ed", Edition.NAME.toLowerCase(Locale.ROOT));
        body.addProperty("mc", GambleOnline.minecraft());
        body.addProperty("v", GambleOnline.version());
        if (secret() != null) body.addProperty("secret", secret());
        String json = GSON.toJson(body);
        NET.submit(() -> download(jar, json));
    }

    private static void download(Path jar, String json) {
        try {
            JsonObject meta = GSON.fromJson(new String(post(SITE + "/api/modlink/update", json, 20_000), StandardCharsets.UTF_8), JsonObject.class);
            if (meta == null) {
                fail("Couldn't reach zzmods.com - try again in a minute.");
                return;
            }
            if (meta.has("error")) {
                fail(meta.get("error").getAsString());
                return;
            }
            if (meta.has("upToDate")) {
                state = State.IDLE;
                message = "You already have the newest version.";
                return;
            }
            String version = meta.get("version").getAsString(), file = meta.get("file").getAsString();
            if (!file.matches(PREFIX + "[A-Za-z]+-\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}-mc[0-9.x]{1,12}\\.jar")) {
                fail("The update file looks wrong - get it on zzmods.com.");
                return;
            }
            message = "Downloading " + version + "...";
            byte[] data = post(SITE + "/api/modlink/update-file", json, MAX_BYTES);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            if (!sha.equals(meta.get("sha256").getAsString()) || !signed(data, meta.get("sig").getAsString())) {
                fail("The download didn't pass the safety check, so it wasn't installed.");
                return;
            }
            Path target = jar.resolveSibling(file), pending = jar.resolveSibling(file + ".zzupdate");
            Files.write(pending, data);
            arm(jar, pending, target);
            state = State.READY;
            message = "Updated to " + version + " - restart Minecraft to use it.";
        } catch (Exception e) {
            fail("The update didn't work (" + e.getClass().getSimpleName() + ") - try again or get it on zzmods.com.");
        }
    }

    private static void fail(String why) {
        state = State.FAILED;
        message = why;
    }

    // signed with the owner's key?
    static boolean signed(byte[] data, String sigB64) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(UpdateKey.PUBLIC)));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(Base64.getDecoder().decode(sigB64));
        } catch (Exception e) {
            return false;
        }
    }

    // a JSON POST; the answer's bytes (also for 4xx answers, which carry {"error": ...})
    private static byte[] post(String url, String body, int limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(8000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", "GambleTracker/" + GambleOnline.version());
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
                if (in == null) throw new IOException("HTTP " + code);
                byte[] data = in.readNBytes(limit + 1);
                if (data.length > limit) throw new IOException("too big");
                if (code >= 400 && !new String(data, StandardCharsets.UTF_8).trim().startsWith("{")) throw new IOException("HTTP " + code);
                return data;
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- swapping the jar once Minecraft has closed (Windows keeps the running jar locked until then)

    // the swap helper starts right away - not when Minecraft closes, because a launcher's Stop button (or a crash) can end
    // the game without running any shutdown code. It waits for this game to exit, however that happens, then swaps the
    // jars. It's started through a second, short-lived shell so it isn't part of the game's process tree and survives the
    // launcher killing the game.
    private static synchronized void arm(Path oldJar, Path pending, Path target) {
        if (pending.equals(swapPending)) return; // already waiting for this one
        swapOld = oldJar;
        swapPending = pending;
        swapTarget = target;
        launchSwap();
    }

    private static void launchSwap() {
        if (swapPending == null || !Files.exists(swapPending)) return;
        long pid = ProcessHandle.current().pid();
        try {
            ProcessBuilder pb;
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                String inner = "$p=" + pid + "; while (Get-Process -Id $p -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 1 }; "
                        + "for ($i = 0; $i -lt 60; $i++) { try { Remove-Item -LiteralPath " + ps(swapOld) + " -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 500 } }; "
                        + "if (-not (Test-Path -LiteralPath " + ps(swapOld) + ")) { Move-Item -LiteralPath " + ps(swapPending) + " -Destination " + ps(swapTarget) + " -Force }";
                // [char]34 = a double quote (none in the command line itself, so Java passes it through untouched)
                String outer = "$s = '" + inner.replace("'", "''") + "'; Start-Process powershell -WindowStyle Hidden -ArgumentList "
                        + "'-NoProfile','-NonInteractive','-Command',([char]34 + $s + [char]34)";
                pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", outer);
            } else {
                String inner = "while kill -0 " + pid + " 2>/dev/null; do sleep 1; done; rm -f " + sh(swapOld) + " && mv -f " + sh(swapPending) + " " + sh(swapTarget);
                pb = new ProcessBuilder("sh", "-c", "nohup sh -c " + shq(inner) + " >/dev/null 2>&1 &");
            }
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException ignored) {
        }
    }

    private static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String ps(Path p) {
        return "'" + p.toAbsolutePath().toString().replace("'", "''") + "'";
    }

    private static String sh(Path p) {
        return "'" + p.toAbsolutePath().toString().replace("'", "'\\''") + "'";
    }
}
