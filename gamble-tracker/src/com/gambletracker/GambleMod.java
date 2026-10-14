package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

public class GambleMod implements ClientModInitializer {
    private static final long TAP_MS = 400;

    private static final Tap cardKey = new Tap();
    private static final Tap panelKey = new Tap();
    private static final Tap nextKey = new Tap();
    private static final Tap payKey = new Tap();

    @Override
    public void onInitializeClient() {
        GambleState.INSTANCE.load();
        Updater.init(); // an update downloaded last time that still has to be swapped in
    }

    public static void onKey(int action, KeyEvent event) {
        if (GambleScreen.isRebinding()) {
            return;
        }
        GambleState st = GambleState.INSTANCE;
        int key = event.key();
        boolean inGame = Minecraft.getInstance().gui.screen() == null;
        // the tour's first step: the next key pressed in game becomes the panel key
        if (GambleTour.capturingKey() && action == InputConstants.PRESS && inGame) {
            GambleTour.keyPressed(key);
            return;
        }
        if (GambleTour.active() && action == InputConstants.PRESS && key == InputConstants.KEY_BACKSPACE && inGame) {
            GambleTour.skip();
            return;
        }
        // F9 always opens the panel, so nobody gets locked out by an odd key choice
        if (key == GambleTour.SAFETY_KEY && action == InputConstants.PRESS && key != st.settingsKey) {
            togglePanel();
            return;
        }
        if (cardKey.tapped(st.openKey, action, key)) {
            toggleCard();
        }
        if (panelKey.tapped(st.settingsKey, action, key)) {
            togglePanel();
        }
        // these two only work in game, not while typing in chat or a menu
        if (st.nextKey >= 0 && nextKey.tapped(st.nextKey, action, key) && inGame) {
            st.onNextKey();
        }
        if (Edition.PAYOUTS && st.payKey >= 0 && payKey.tapped(st.payKey, action, key) && inGame) {
            st.payUpNext();
        }
    }

    // middle / side mouse buttons bound to a gamble key; true = Minecraft doesn't see the click
    public static boolean onMouse(int button, int action) {
        if (button < 2 || GambleScreen.isRebinding()) {
            return false;
        }
        GambleState st = GambleState.INSTANCE;
        int key = GambleState.MOUSE + button;
        Screen screen = Minecraft.getInstance().gui.screen();
        boolean inGame = screen == null;
        if (GambleTour.capturingKey() && inGame) {
            if (action == InputConstants.PRESS) GambleTour.keyPressed(key);
            return true;
        }
        boolean bound = key == st.openKey || key == st.settingsKey || key == st.nextKey
                || (Edition.PAYOUTS && key == st.payKey);
        if (!bound) {
            return false;
        }
        if (action != InputConstants.PRESS) {
            return inGame; // swallow the release too so pick block never half-fires
        }
        boolean panels = inGame || screen instanceof GambleScreen || isAuctionPanel(screen);
        if (key == st.openKey && panels) {
            toggleCard();
        } else if (key == st.settingsKey && panels) {
            togglePanel();
        } else if (key == st.nextKey && inGame) {
            st.onNextKey();
        } else if (Edition.PAYOUTS && key == st.payKey && inGame) {
            st.payUpNext();
        } else {
            return false;
        }
        return true;
    }

    // a quick press and release with no other key pressed in between
    private static final class Tap {
        private boolean down;
        private boolean chord;
        private long start;

        boolean tapped(int key, int action, int pressed) {
            if (pressed == key) {
                if (action == InputConstants.PRESS) {
                    down = true;
                    chord = false;
                    start = System.currentTimeMillis();
                } else if (action == InputConstants.RELEASE && down) {
                    down = false;
                    return !chord && System.currentTimeMillis() - start <= TAP_MS;
                }
            } else if (down && action != InputConstants.RELEASE) {
                chord = true;
            }
            return false;
        }
    }

    private static void togglePanel() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (GambleOnline.blocked()) {
            if (screen instanceof UpdateRequiredScreen s) s.onClose();
            else if (screen == null && mc.level != null) mc.gui.setScreen(new UpdateRequiredScreen());
            return;
        }
        if (GambleOnline.locked()) { // a paid copy that isn't linked to the account that bought it
            if (screen instanceof LicenseScreen s) s.onClose();
            else if (screen == null && mc.level != null) mc.gui.setScreen(new LicenseScreen());
            return;
        }
        if (screen == null && mc.level != null && UpdatePromptScreen.wanted()) {
            mc.gui.setScreen(new UpdatePromptScreen(true)); // "update now?" once per update, then the panel
            return;
        }
        if (screen instanceof GambleScreen panel) {
            panel.onClose();
        } else if ((screen == null || isAuctionPanel(screen)) && mc.level != null) {
            openPanel();
        }
    }

    private static void toggleCard() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (GambleOnline.off()) {
            togglePanel();
            return;
        }
        GambleState st = GambleState.INSTANCE;
        if (screen instanceof GambleScreen panel) {
            st.active = false;
            panel.onClose();
        } else if ((screen == null || isAuctionPanel(screen)) && mc.level != null) {
            st.active = !st.active;
        }
    }

    public static void openPanel() {
        if (GambleOnline.blocked()) {
            Minecraft.getInstance().gui.setScreen(new UpdateRequiredScreen());
            return;
        }
        if (GambleOnline.locked()) {
            Minecraft.getInstance().gui.setScreen(new LicenseScreen());
            return;
        }
        GambleState.INSTANCE.active = true;
        Minecraft.getInstance().gui.setScreen(new GambleScreen());
    }

    // in the combined jar the gamble keys also work while the auction panel is open
    private static boolean isAuctionPanel(Screen screen) {
        return screen.getClass().getName().startsWith("com.auctionoverlay.");
    }
}
