package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class AuctionMod implements ClientModInitializer {
    private static final long TAP_MS = 400;
    private static final int KEY_NUMPAD_SUBTRACT = 333;

    private static boolean keyDown;
    private static boolean otherKeyPressed;
    private static long keyDownAt;
    private static int swallowKey = -1; // the key just picked as the open key: letting go of it must not toggle the panel

    // the game menu's Announcements button (mixin.PauseScreenMixin); with Gamble Tracker too, only one adds it
    public static boolean announcementsButton() {
        return Announcements.claimButton();
    }

    public static String announcementsLabel() {
        return Announcements.label();
    }

    // called when a key was just picked (Pick your key): that press and its release are done with
    static void consumeKey(int key) {
        swallowKey = key;
        keyDown = false;
    }

    @Override
    public void onInitializeClient() {
        AuctionState.INSTANCE.load();
        checkReinstall();
        Updater.init(); // an update downloaded last time that still has to be swapped in
    }

    // the mod was added (back) by hand - removed and re-added in the Modrinth app, another edition or a download put
    // in - so the tutorial shows again. Only the in-game "Update now" (which notes the jar it puts in) doesn't.
    private static void checkReinstall() {
        Path jar = Updater.ownJar();
        if (jar == null) return; // running from the dev folder
        try {
            BasicFileAttributes a = Files.readAttributes(jar, BasicFileAttributes.class);
            String name = jar.getFileName().toString();
            String id = name + "|" + a.size() + "|" + a.creationTime().toMillis() + "|" + a.lastModifiedTime().toMillis();
            AuctionState.Config c = AuctionState.INSTANCE.config;
            boolean ownUpdate = name.equals(c.selfUpdateJar);
            boolean addedByHand = c.jarId == null ? !lastGameHadMod() : !c.jarId.equals(id) && !ownUpdate;
            if (addedByHand) {
                c.tutorialDone = false;
            }
            String v = Online.version();
            if (!id.equals(c.jarId) || !v.equals(c.jarVersion)) {
                c.jarId = id;
                c.jarVersion = v;
                c.selfUpdateJar = null; // that update is in now
                AuctionState.INSTANCE.save();
            }
        } catch (Exception ignored) {
        }
    }

    // copies from before this check: was Auction Overlay loaded the last time the game ran? (Minecraft packs the last
    // game's log into logs/<date>-<n>.log.gz, which lists every mod with its version.) No = it was added since.
    private static boolean lastGameHadMod() {
        try {
            Path logs = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
            Path newest = null;
            try (var files = Files.newDirectoryStream(logs, "*.log.gz")) {
                for (Path p : files) {
                    if (newest == null || Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(newest)) > 0) newest = p;
                }
            }
            if (newest == null) return true; // nothing to go on
            try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.util.zip.GZIPInputStream(Files.newInputStream(newest)), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                for (int i = 0; i < 400 && (line = in.readLine()) != null; i++) {
                    if (line.contains("- auctionoverlay ")) return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public static void onKey(int action, KeyEvent event) {
        int key = event.key();
        if (key == swallowKey) {
            if (action == InputConstants.RELEASE) swallowKey = -1;
            return;
        }
        if (action != InputConstants.RELEASE && event.hasControlDown() && canResize()) {
            if (key == InputConstants.KEY_EQUALS || key == InputConstants.KEY_ADD) {
                AuctionState.INSTANCE.adjustScale(1);
            } else if (key == InputConstants.KEY_MINUS || key == KEY_NUMPAD_SUBTRACT) {
                AuctionState.INSTANCE.adjustScale(-1);
            } else if (key == InputConstants.KEY_0 || key == InputConstants.KEY_NUMPAD0) {
                AuctionState.INSTANCE.adjustScale(0);
            }
        }
        // premium: +time / sell / cancel / copy announcement keys, only while playing (no screen open)
        AuctionState.Config c = AuctionState.INSTANCE.config;
        // the tour's first step: the next key pressed in game becomes the open key
        if (Tour.capturingKey() && action == InputConstants.PRESS && Minecraft.getInstance().gui.screen() == null) {
            Tour.keyPressed(key);
            return;
        }
        // F7 always opens the panel, so nobody gets locked out by an odd key choice
        if (key == Tour.SAFETY_KEY && action == InputConstants.PRESS && !AuctionScreen.isRebinding() && key != c.toggleKey) {
            togglePanel();
            return;
        }
        if (Tour.active() && action == InputConstants.PRESS && key == InputConstants.KEY_BACKSPACE
                && Minecraft.getInstance().gui.screen() == null) {
            Tour.skip();
            return;
        }
        if (Edition.PRO && action == InputConstants.PRESS && key >= 0 && !AuctionScreen.isRebinding() && key != c.toggleKey) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null && mc.level != null) {
                int which = key == c.keyExtend ? 1 : key == c.keySell ? 2 : key == c.keyCancel ? 3 : key == c.keyAnnounce ? 4 : 0;
                if (which != 0) {
                    AuctionState.INSTANCE.key(which);
                }
            }
        }
        // only a quick tap toggles, so holding it for something else (or a combo) doesn't
        if (key == AuctionState.INSTANCE.config.toggleKey && !AuctionScreen.isRebinding()) {
            if (action == InputConstants.PRESS && !keyDown) {
                keyDown = true;
                otherKeyPressed = false;
                keyDownAt = System.currentTimeMillis();
            } else if (action == InputConstants.RELEASE && keyDown) {
                keyDown = false;
                if (!otherKeyPressed && System.currentTimeMillis() - keyDownAt <= TAP_MS) {
                    togglePanel();
                }
            }
        } else if (keyDown && action != InputConstants.RELEASE) {
            otherKeyPressed = true;
        }
    }

    private static boolean canResize() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        return screen instanceof AuctionScreen || (screen == null && mc.level != null && AuctionHud.visible());
    }

    private static void togglePanel() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (Online.blocked()) {
            if (screen instanceof UpdateRequiredScreen s) s.onClose();
            else if (screen == null && mc.level != null) mc.gui.setScreen(new UpdateRequiredScreen());
            return;
        }
        if (Online.locked()) { // a paid copy that isn't linked to the account that bought it
            if (screen instanceof LicenseScreen s) s.onClose();
            else if (screen == null && mc.level != null) mc.gui.setScreen(new LicenseScreen());
            return;
        }
        if (screen == null && mc.level != null && UpdatePromptScreen.wanted()) {
            mc.gui.setScreen(new UpdatePromptScreen(true)); // "update now?" once per update, then the panel
            return;
        }
        if (screen instanceof AuctionScreen panel) {
            panel.onClose();
        } else if ((screen == null || isGamblePanel(screen)) && mc.level != null) {
            mc.gui.setScreen(new AuctionScreen());
        }
    }

    // in the combined jar the auction key can switch straight over from the gamble panel
    private static boolean isGamblePanel(Screen screen) {
        return screen.getClass().getName().startsWith("com.gambletracker.");
    }

    private static int inWorldTicks;
    private static boolean waitForRejoin; // after a reset the welcome waits until you join a world again

    public static void resetTutorial() {
        Tour.cancel();
        AuctionState.INSTANCE.config.tutorialDone = false;
        AuctionState.INSTANCE.save();
        waitForRejoin = Minecraft.getInstance().level != null;
    }

    public static void onClientTick() {
        AuctionState.INSTANCE.tick();
        Tour.tick();
        Online.tick();
        // tells Gamble Tracker (if it's installed too) to wait until this tutorial is finished
        boolean pending = (!AuctionState.INSTANCE.config.tutorialDone || Tour.active() || Tour.linking()
                || Minecraft.getInstance().gui.screen() instanceof TutorialScreen);
        System.setProperty("zzmods.auctionTutorial", pending ? "busy" : "done");
        System.setProperty("zzmods.auctionKey", String.valueOf(AuctionState.INSTANCE.config.toggleKey));
        // the tutorial opens once (every edition), a couple of seconds after you first join a world
        Minecraft mc = Minecraft.getInstance();
        // unlinked while the panel is open (About > Unlink, or on the website): it locks right away
        Minecraft mcNow = Minecraft.getInstance();
        if (Online.locked() && mcNow.gui.screen() instanceof AuctionScreen && !Tour.active() && !Tour.linking()) {
            mcNow.gui.setScreen(new LicenseScreen());
        }
        boolean usable = !Online.blocked() && (!Online.locked() || "link".equals(Online.lockReason())); // the tour links it first
        if (usable && !AuctionState.INSTANCE.config.tutorialDone && !Tour.active() && !Tour.linking() && !waitForRejoin && mc.level != null && mc.player != null) {
            if (++inWorldTicks == 40 && mc.gui.screen() == null) {
                mc.gui.setScreen(new TutorialScreen());
            } else if (inWorldTicks > 40 && mc.gui.screen() == null) {
                inWorldTicks = 39; // something else was open (chat, a menu); try again when it closes
            }
        } else if (mc.level == null) {
            inWorldTicks = 0;
            waitForRejoin = false;
        }
    }
}
