package com.gambletracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

// One small panel with tabs: QUEUE (bet range, the queue, refunds, Next / Undo / Clear) and the settings split into
// LOOK, CARD, KEYS and ABOUT - each tab only shows its own few settings, so nothing has to be hunted for in a long list.
public class GambleScreen extends Screen {
    static final int W = 236;
    static final int PAD = 7;
    static final int CW = W - 2 * PAD; // content width
    private static final int MAX_LIST = 6;
    private static final int TAB_H = 14;

    private enum Bind { NONE, CARD, PANEL, NEXT, PAY }

    // the tabs, in order; "keys" is called Keys & pay when the edition has payouts
    enum Tab {
        QUEUE("Queue"), LOOK("Look"), CARD("Card"), KEYS("Keys"), ABOUT("About");

        final String label;

        Tab(String label) {
            this.label = label;
        }

        static Tab named(String name) {
            for (Tab t : values()) if (t.name().equalsIgnoreCase(name)) return t;
            return null;
        }
    }

    private static Bind rebinding = Bind.NONE;
    private static Tab tab = Tab.QUEUE;
    private static final int[] scrolls = new int[Tab.values().length];

    private record Btn(int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // one line in a settings tab; place() puts its button / box / slider at row y
    private record SRow(String label, boolean header, IntConsumer place) {}

    private record Shown(SRow row, int y) {}

    private final GambleState st = GambleState.INSTANCE;
    private final List<Btn> buttons = new ArrayList<>();
    private final List<SRow> srows = new ArrayList<>();
    private final List<Shown> shown = new ArrayList<>();
    private final Map<EditBox, int[]> frames = new IdentityHashMap<>();
    private EditBox minBox, maxBox, titleBox;

    private int px, py, ph, x0, scx, tabY, top;
    private float scale = 1.0F;
    private int vh;
    private int listY, refundY, buttonsY, checkY, ignoredY, mainEnd;
    private int setBottom;
    private boolean moreBelow, titleShown;
    private int sizeRowY, heightX, heightY, hueX, hueAY, hueBY, opX, opY;
    private int draggingHue;
    private boolean draggingOpacity, draggingHeight;

    public GambleScreen() {
        super(Component.literal("Gamble Tracker"));
    }

    public static boolean isRebinding() {
        return rebinding != Bind.NONE;
    }

    @Override
    protected void init() {
        frames.clear();
        minBox = textBox(CW / 2 - 3, st.minText, 20, v -> {
            st.minText = v.trim();
            st.save();
        });
        minBox.setHint(Component.literal("no minimum"));
        maxBox = textBox(CW / 2 - 3, st.maxText, 20, v -> {
            st.maxText = v.trim();
            st.save();
        });
        maxBox.setHint(Component.literal("no maximum"));
        titleBox = textBox(110, st.title, 24, v -> {
            st.title = v;
            st.save();
        });
        titleBox.setHint(Component.literal(Style.defaultTitle()));
        layout();
    }

    private EditBox textBox(int width, String value, int maxLength, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, width, 14, Component.literal(""));
        frames.put(box, new int[]{0, 0, width, 14});
        if (Style.modern()) {
            box.setBordered(false);
            box.setTextColor(Colors.TEXT);
            box.setWidth(width - 8);
            box.setHeight(11);
        }
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(onChange);
        addRenderableWidget(box);
        return box;
    }

    private static String tabLabel(Tab t) {
        if (t == Tab.KEYS && Edition.PAYOUTS) return "Keys";
        if (t == Tab.ABOUT && GambleOnline.update() != null) return "About !";
        return t.label;
    }

    // ---- layout

    private void layout() {
        String want = GambleTour.wantTab();
        if (want != null && Tab.named(want) != null) tab = Tab.named(want); // the tour shows the tab it talks about
        scale = st.scale();
        if (W * scale > width - 8) {
            scale = Math.max(0.5F, (width - 8) / (float) W); // too wide for this window: shrink it
        }
        int vw = (int) (width / scale);
        vh = (int) (height / scale);
        px = Math.max(0, (vw - W) / 2);
        py = 0;
        layoutAt();
        py = Math.max(0, (vh - ph) / 2);
        layoutAt();
    }

    private void layoutAt() {
        x0 = px + PAD;
        scx = x0 + CW - 80;
        tabY = py + 23;
        top = tabY + TAB_H + 6;
        buttons.clear();
        // X closes and hides the card, - just closes
        buttons.add(new Btn(px + W - PAD - 11, py + 4, 11, 11, Style.ICON_CLOSE, Colors.BTN, true, () -> {
            st.active = false;
            onClose();
        }));
        buttons.add(new Btn(px + W - PAD - 25, py + 4, 11, 11, Style.ICON_HIDE, Colors.BTN, true, this::onClose));
        // the tabs
        Tab[] tabs = Tab.values();
        int tw = (CW - 3 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = i == tabs.length - 1 ? x0 + CW - tw : x0 + i * (tw + 3);
            buttons.add(new Btn(x, tabY, tw, TAB_H, tabLabel(t), t == tab ? Colors.BTN_GO : Colors.BTN, true, () -> {
                tab = t;
                rebinding = Bind.NONE;
                setFocused(null);
            }));
        }
        show(minBox, tab == Tab.QUEUE);
        show(maxBox, tab == Tab.QUEUE);
        show(titleBox, false);
        titleShown = false;
        sizeRowY = heightY = hueAY = hueBY = opY = -100;
        if (tab == Tab.QUEUE) {
            layoutMain();
            ph = mainEnd - py + 6;
        } else {
            buildRows(tab);
            int full = 0;
            for (SRow r : srows) full += rowHeight(r);
            ph = Math.min(top - py + full + 8, Math.max(top - py + 60, vh - 8));
            setBottom = py + ph - 6;
            layoutSettings();
        }
    }

    // the part of the panel the tour is pointing at
    private int[] tourTarget() {
        String t = GambleTour.targetName();
        if (t == null) return null;
        if (t.startsWith("tab:")) return new int[]{x0, tabY, CW, py + ph - 4 - tabY};
        int third = (CW - 8) / 3;
        return switch (t) {
            case "bets" -> new int[]{x0, top, CW, 36};
            case "queue" -> new int[]{x0, top + 40, CW, Math.max(30, buttonsY - 4 - top - 40)};
            case "nextBtn" -> new int[]{x0, buttonsY, third, 18};
            default -> null;
        };
    }

    private void layoutMain() {
        int half = CW / 2 - 3;
        place(minBox, x0, top + 10);
        place(maxBox, x0 + CW - half, top + 10);

        int qy = top + 40;
        listY = qy + 11;
        List<GambleState.Bet> queue = st.queue();
        if (Edition.PAYOUTS) {
            buttons.add(new Btn(x0 + CW - 50, qy - 1, 50, 11, "Copy pay", Colors.BTN, !queue.isEmpty(), () -> st.copyPay(st.queue().get(0))));
        }
        int count = Math.min(queue.size(), MAX_LIST);
        for (int i = 0; i < count; i++) {
            GambleState.Bet b = queue.get(i);
            buttons.add(new Btn(x0 + CW - 14, listY + 3 + i * 12, 10, 10, "x", Colors.BTN, true, () -> st.remove(b)));
        }
        int y = listY + listHeight(count, queue.size() > MAX_LIST) + 4;

        List<GambleState.Bet> refunds = st.refunds();
        refundY = y;
        if (!refunds.isEmpty()) {
            int rShown = Math.min(refunds.size(), 3);
            for (int i = 0; i < rShown; i++) {
                GambleState.Bet b = refunds.get(i);
                buttons.add(new Btn(x0 + CW - 14, y + 14 + i * 12, 10, 10, "x", Colors.BTN, true, () -> st.remove(b)));
            }
            buttons.add(new Btn(x0 + CW - 50, y, 50, 11, "Clear all", Colors.BTN, true, st::clearRefunds));
            y += 11 + listHeight(rShown, refunds.size() > rShown) + 4;
        }

        int third = (CW - 8) / 3;
        buttonsY = y;
        buttons.add(new Btn(x0, y, third, 18, "Next  >>", Colors.BTN_GO, !queue.isEmpty(), () -> st.next("next button")));
        buttons.add(new Btn(x0 + third + 4, y, third, 18, "Undo", Colors.BTN, st.canUndo(), st::undo));
        buttons.add(new Btn(x0 + CW - third, y, third, 18, "Clear queue", Colors.BTN_STOP, !queue.isEmpty(), () -> st.clearQueue("clear button")));
        y += 23;
        checkY = y;
        y += 23;
        ignoredY = y;
        if (!st.lastUnmatched.isEmpty()) {
            y += 21;
        }
        mainEnd = y;
    }

    private static int listHeight(int rows, boolean more) {
        return Math.max(1, rows) * 12 + (more ? 10 : 0) + 4;
    }

    private static int rowHeight(SRow r) {
        return r.header() ? 14 : r.place() == null ? 11 : 16;
    }

    private void buildRows(Tab t) {
        srows.clear();
        switch (t) {
            case LOOK -> {
                if (!Edition.LOOKS) {
                    choice("Accent colour", Colors.SOLID_NAMES[Math.floorMod(st.accent, Colors.SOLID_NAMES.length)], true, () -> {
                        st.accent = (st.accent + 1) % Colors.SOLID_NAMES.length;
                        st.applyTheme();
                    });
                } else {
                    lookRows();
                }
                srows.add(new SRow("Panel opacity", false, y -> {
                    opX = scx + 10;
                    opY = y + 3;
                }));
                if (Edition.LOOKS) {
                    styleRows();
                } else {
                    srows.add(new SRow("More looks with Premium - zzmods.com", false, null));
                }
            }
            case CARD -> cardRows();
            case KEYS -> keyRows();
            case ABOUT -> aboutRows();
            default -> {
            }
        }
    }

    private void linkRow() {
        header("ZZMODS ACCOUNT");
        choice("zzmods account", GambleOnline.linkSecret == null ? "Link account" : GambleOnline.linkedEmail == null ? "Cancel" : "Unlink", true, () -> {
            if (GambleOnline.linkSecret == null) GambleOnline.startLink();
            else GambleOnline.unlink();
            rebuildWidgets();
        });
        srows.add(new SRow(GambleOnline.linkState(), false, null));
        srows.add(new SRow("Linked: shares your MC name + stats and", false, null));
        srows.add(new SRow("gets support's test payments.", false, null));
    }

    private void aboutRows() {
        String update = GambleOnline.update();
        if (update != null && Updater.ownJar() != null) {
            Updater.State us = Updater.state();
            choice("Update " + update + " is out!", us == Updater.State.WORKING ? "Updating..." : us == Updater.State.READY ? "Restart MC" : "Update now",
                    us != Updater.State.WORKING && us != Updater.State.READY, () -> minecraft.gui.setScreen(new UpdatePromptScreen(false)));
        } else {
            choice(update != null ? "Update " + update + " is out!" : "Gamble Tracker " + GambleOnline.version(), "Download page", true,
                    GambleOnline::openDownloads);
        }
        choice("Check for updates", onOff(GambleOnline.checkUpdates), true, () -> {
            GambleOnline.checkUpdates = !GambleOnline.checkUpdates;
            GambleOnline.save();
        });
        choice("Share anonymous stats", onOff(GambleOnline.shareStats), true, () -> {
            GambleOnline.shareStats = !GambleOnline.shareStats;
            GambleOnline.save();
        });
        srows.add(new SRow("Counts minutes used, payments, players and", false, null));
        srows.add(new SRow("winners. No names, chat or servers.", false, null));
        choice("Tutorial", "Start tour", true, () -> {
            onClose();
            GambleTour.start();
        });
        choice(st.tutorialDone ? "Reset tutorial" : "Shows on next join", "Reset", st.tutorialDone, () -> {
            GambleTour.cancel();
            st.tutorialDone = false;
        });
        linkRow();
    }

    private void lookRows() {
        choice("Look", st.look.label, true, () -> {
            st.look = st.look.next();
            st.applyTheme();
            rebuildWidgets();
        });
        choice("Accent colours", Colors.ACCENT_NAMES[Math.floorMod(st.accent, Colors.ACCENT_NAMES.length)], Style.modern(), () -> {
            st.accent = (st.accent + 1) % Colors.ACCENT_NAMES.length;
            st.applyTheme();
        });
        srows.add(new SRow("Custom colour 1", false, y -> {
            hueX = scx + 10;
            hueAY = y + 3;
        }));
        srows.add(new SRow("Custom colour 2", false, y -> {
            hueX = scx + 10;
            hueBY = y + 3;
        }));
        srows.add(new SRow("Title", false, y -> {
            place(titleBox, x0 + CW - 110, y);
            titleShown = true;
        }));
    }

    private void cardRows() {
        choice("Card side", st.hud.label, true, () ->
                st.hud = GambleState.HudSpot.values()[(st.hud.ordinal() + 1) % GambleState.HudSpot.values().length]);
        srows.add(new SRow("Card up / down", false, y -> {
            heightX = scx + 10;
            heightY = y + 3;
        }));
        srows.add(new SRow("Size", false, y -> {
            sizeRowY = y;
            buttons.add(new Btn(scx, y, 16, 13, "-", Colors.BTN, true, () -> st.adjustScale(-1)));
            buttons.add(new Btn(scx + 64, y, 16, 13, "+", Colors.BTN, true, () -> st.adjustScale(1)));
        }));
        choice("Players on card", String.valueOf(st.rows), true, () -> st.rows = cycle(GambleState.ROW_CHOICES, st.rows));
        choice("Show refunds", onOff(st.showRefunds), true, () -> st.showRefunds = !st.showRefunds);
        choice("Sounds", onOff(st.sounds), true, () -> st.sounds = !st.sounds);
    }

    private void keyRows() {
        choice("Show / hide card", keyLabel(Bind.CARD, st.openKey), true, () -> rebinding = Bind.CARD);
        choice("Open this panel", keyLabel(Bind.PANEL, st.settingsKey), true, () -> rebinding = Bind.PANEL);
        choice("Next player", keyLabel(Bind.NEXT, st.nextKey), true, () -> rebinding = Bind.NEXT);
        choice("Next key removes", st.nextFirstOnly ? "First player" : "Everyone", true, () -> st.nextFirstOnly = !st.nextFirstOnly);
        srows.add(new SRow("F9 always opens this panel too.", false, null));
        if (Edition.PAYOUTS) {
            header("PAY");
            choice("Pay winner key", keyLabel(Bind.PAY, st.payKey), true, () -> rebinding = Bind.PAY);
            choice("Pay key does", st.payKeySends ? "Send /pay" : "Copy only", true, () -> st.payKeySends = !st.payKeySends);
            choice("Auto-copy /pay", onOff(st.copyPay), true, () -> st.copyPay = !st.copyPay);
            choice("Winner gets", GambleState.multiplierText(st.payoutPct) + " their bet", true,
                    () -> st.payoutPct = cycle(GambleState.PAYOUT_CHOICES, st.payoutPct));
        }
    }

    private void styleRows() {
        header("STYLE");
        choice("Corners", Style.CORNER_NAMES[Math.floorMod(st.corners, Style.CORNER_NAMES.length)], Style.modern(), () -> {
            st.corners = (st.corners + 1) % Style.CORNER_NAMES.length;
            st.applyTheme();
        });
        choice("Title colour", Colors.TITLE_COLOR_NAMES[Math.floorMod(st.titleColor, Colors.TITLE_COLOR_NAMES.length)], true, () -> {
            st.titleColor = (st.titleColor + 1) % Colors.TITLE_COLOR_NAMES.length;
            st.applyTheme();
        });
        choice("Text shadow", onOff(st.textShadow), true, () -> {
            st.textShadow = !st.textShadow;
            st.applyTheme();
        });
        choice("Reset the look", "Reset", true, () -> {
            st.look = Edition.LOOKS ? Look.SLEEK : Look.ROUNDED;
            st.accent = Edition.LOOKS ? 5 : 0;
            st.corners = 2;
            st.titleColor = 0;
            st.textShadow = false;
            st.opacity = 85;
            st.scale = 1.0F;
            st.applyTheme();
            rebuildWidgets();
        });
    }

    private void header(String text) {
        srows.add(new SRow(text, true, null));
    }

    private void choice(String label, String value, boolean enabled, Runnable action) {
        srows.add(new SRow(label, false, y -> buttons.add(new Btn(scx, y, 80, 13, value, Colors.BTN, enabled, () -> {
            action.run();
            st.save();
        }))));
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    private void layoutSettings() {
        shown.clear();
        int si = tab.ordinal();
        scrolls[si] = Math.max(0, Math.min(scrolls[si], srows.size() - 1));
        int y = top;
        int i = scrolls[si];
        while (i < srows.size()) {
            SRow r = srows.get(i);
            if (y + rowHeight(r) > setBottom) {
                break;
            }
            shown.add(new Shown(r, y));
            if (r.place() != null) {
                r.place().accept(y);
            }
            y += rowHeight(r);
            i++;
        }
        moreBelow = i < srows.size();
        show(titleBox, titleShown);
    }

    private String keyLabel(Bind bind, int key) {
        if (rebinding != bind) {
            return GambleState.keyName(key);
        }
        return bind == Bind.NEXT || bind == Bind.PAY ? "Key/mouse, Bksp=none" : "Key or mouse...";
    }

    private static int cycle(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private void show(EditBox box, boolean visible) {
        box.setVisible(visible);
        box.active = visible;
        if (!visible && getFocused() == box) {
            setFocused(null);
        }
    }

    private void place(EditBox box, int x, int y) {
        int[] frame = frames.get(box);
        frame[0] = x;
        frame[1] = y;
        box.setX(Style.modern() ? x + 4 : x);
        box.setY(Style.modern() ? y + 3 : y);
    }

    // ---- render

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // no dim / blur
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        int mouseX = (int) (realMouseX / scale);
        int mouseY = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int alpha = st.opacity * 255 / 100;
        Style.panel(g, px, py, W, ph, alpha);

        String toast = st.toast();
        String update = GambleOnline.update();
        String hint = toast != null ? toast : update != null ? "Update " + update + " is out (About)" : GambleState.keyName(st.settingsKey) + ": close";
        int hintColor = toast != null ? st.toastColor() : update != null ? Colors.YELLOW : Colors.MUTED;
        hint = Txt.cut(font, hint, Math.max(0, W - 2 * PAD - 34 - Txt.width(font, Style.title())));
        Txt.draw(g, font, Style.title(), x0, py + 6, Colors.TITLE, Style.shadow);
        Txt.draw(g, font, hint, px + W - PAD - 30 - Txt.width(font, hint), py + 6, hintColor, Style.shadow);
        Style.divider(g, x0, py + 19, W - 2 * PAD, alpha);

        if (tab == Tab.QUEUE) {
            renderMain(g);
        } else {
            renderSettings(g);
        }
        for (Btn b : buttons) {
            Style.button(g, font, b.x(), b.y(), b.w(), b.h(), b.label(), b.color(), b.enabled(),
                    b.enabled() && b.contains(mouseX, mouseY));
        }

        if (Style.modern()) {
            for (Map.Entry<EditBox, int[]> e : frames.entrySet()) {
                if (e.getKey().isVisible()) {
                    int[] f = e.getValue();
                    Style.field(g, f[0], f[1], f[2], f[3], e.getKey().isFocused());
                }
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        GambleTour.renderPanel(g, font, tourTarget(), px, py, W, ph, (int) (width / scale), vh, mouseX, mouseY);
        g.pose().popMatrix();
    }

    private void renderMain(GuiGraphicsExtractor g) {
        int half = CW / 2 - 3;
        Txt.draw(g, font, "Min bet", x0, top, Colors.TEXT, Style.shadow);
        Txt.draw(g, font, "Max bet", x0 + CW - half, top, Colors.TEXT, Style.shadow);
        Txt.draw(g, font, preview(st.minText), x0, top + 27, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, preview(st.maxText), x0 + CW - half, top + 27, Colors.MUTED, Style.shadow);

        List<GambleState.Bet> queue = st.queue();
        int qy = top + 40;
        Txt.draw(g, font, "QUEUE", x0, qy, Colors.TEXT, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, queue.size() + " paid  " + st.rangeText(), CW - (Edition.PAYOUTS ? 58 : 4) - Txt.width(font, "QUEUE") - 6),
                x0 + Txt.width(font, "QUEUE") + 6, qy, Colors.MUTED, Style.shadow);
        int count = Math.min(queue.size(), MAX_LIST);
        Style.box(g, x0, listY, CW, listHeight(count, queue.size() > MAX_LIST));
        if (queue.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, "Nobody yet - payments show up here", CW - 10), x0 + 5, listY + 4, Colors.MUTED, Style.shadow);
        }
        for (int i = 0; i < count; i++) {
            GambleState.Bet b = queue.get(i);
            int ry = listY + 3 + i * 12;
            if (i == 0) {
                Style.highlight(g, x0 + 2, ry - 1, CW - 18, 12, 255);
            }
            String amount = PaymentParser.full(b.amount());
            Txt.draw(g, font, (i + 1) + ".", x0 + 6, ry + 1, i == 0 ? Colors.GREEN : Colors.MUTED, Style.shadow);
            String nm = Txt.cut(font, b.name(), CW - 48 - Txt.width(font, amount) - (st.testTagged(b) ? 26 : 0));
            Txt.draw(g, font, nm, x0 + 22, ry + 1, i == 0 ? Colors.GREEN : Colors.TEXT, Style.shadow);
            if (st.testTagged(b)) {
                Txt.draw(g, font, "TEST", x0 + 26 + Txt.width(font, nm), ry + 1, Colors.AQUA, Style.shadow);
            }
            Txt.draw(g, font, amount, x0 + CW - 20 - Txt.width(font, amount), ry + 1, i == 0 ? Colors.GREEN : Colors.AQUA, Style.shadow);
        }
        if (queue.size() > MAX_LIST) {
            Txt.draw(g, font, "+" + (queue.size() - MAX_LIST) + " more", x0 + 22, listY + 3 + count * 12, Colors.MUTED, Style.shadow);
        }

        List<GambleState.Bet> refunds = st.refunds();
        if (!refunds.isEmpty()) {
            Style.pill(g, font, x0, refundY, "REFUND", Colors.RED);
            Txt.draw(g, font, "paid outside range", x0 + Txt.width(font, "REFUND") + 10, refundY + 1, Colors.MUTED, Style.shadow);
            int rShown = Math.min(refunds.size(), 3);
            int by = refundY + 11;
            Style.box(g, x0, by, CW, listHeight(rShown, refunds.size() > rShown));
            for (int i = 0; i < rShown; i++) {
                GambleState.Bet b = refunds.get(i);
                String amount = PaymentParser.full(b.amount()) + (b.underMin() ? " (low)" : " (high)");
                int ry = by + 3 + i * 12;
                Txt.draw(g, font, Txt.cut(font, b.name(), CW - 30 - Txt.width(font, amount)), x0 + 6, ry + 1, Colors.RED, Style.shadow);
                Txt.draw(g, font, amount, x0 + CW - 20 - Txt.width(font, amount), ry + 1, Colors.RED, Style.shadow);
            }
            if (refunds.size() > rShown) {
                Txt.draw(g, font, "+" + (refunds.size() - rShown) + " more", x0 + 6, by + 3 + rShown * 12, Colors.MUTED, Style.shadow);
            }
        }

        Style.check(g, font, x0, checkY, st.tracking, "Tracking payments");
        String next = st.nextKey < 0 ? "Next player key: not set (Keys tab)"
                : "Next player: tap " + GambleState.keyName(st.nextKey) + (st.nextFirstOnly ? "" : " (clears everyone)");
        Txt.draw(g, font, Txt.cut(font, next, CW - 13), x0 + 13, checkY + 11, st.nextKey < 0 ? Colors.YELLOW : Colors.MUTED, Style.shadow);

        if (!st.lastUnmatched.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, "Ignored (not an exact payment message):", CW), x0, ignoredY, Colors.RED, Style.shadow);
            Txt.draw(g, font, Txt.cut(font, st.lastUnmatched, CW), x0, ignoredY + 10, Colors.MUTED, Style.shadow);
        }
    }

    private void renderSettings(GuiGraphicsExtractor g) {
        for (Shown s : shown) {
            if (s.row().header()) {
                Txt.draw(g, font, s.row().label(), x0, s.y() + 3, Colors.ACCENT_B, Style.shadow);
                Style.divider(g, x0 + Txt.width(font, s.row().label()) + 6, s.y() + 7, CW - Txt.width(font, s.row().label()) - 6, 255);
            } else if (s.row().place() == null) { // a line of info: the whole width
                Txt.draw(g, font, Txt.cut(font, s.row().label(), CW), x0, s.y() + 1, Colors.MUTED, Style.shadow);
            } else {
                Txt.draw(g, font, Txt.cut(font, s.row().label(), CW - 84), x0, s.y() + 3, Colors.TEXT, Style.shadow);
            }
        }
        boolean custom = Math.floorMod(st.accent, Colors.ACCENT_NAMES.length) == Colors.CUSTOM_ACCENT && Style.modern();
        if (hueAY >= 0) {
            Style.hueSlider(g, hueX, hueAY, st.hueA, custom);
        }
        if (hueBY >= 0) {
            Style.hueSlider(g, hueX, hueBY, st.hueB, custom);
        }
        if (opY >= 0) {
            Style.slider(g, opX, opY, (st.opacity - 35) / 65.0F);
        }
        if (sizeRowY >= 0) {
            Txt.center(g, font, Math.round(st.scale() * 100) + "%", scx + 40, sizeRowY + 3, Colors.TEXT);
        }
        if (heightY >= 0) {
            Txt.draw(g, font, "Up", heightX - 12, heightY, Colors.MUTED, Style.shadow);
            Style.slider(g, heightX, heightY, st.heightPct / 100.0F);
            Txt.draw(g, font, "Dn", heightX + 62, heightY, Colors.MUTED, Style.shadow);
        }
        int si = tab.ordinal();
        if (!srows.isEmpty() && (scrolls[si] > 0 || moreBelow)) {
            int barH = setBottom - top;
            int thumb = Math.max(10, barH * shown.size() / srows.size());
            int t = (barH - thumb) * scrolls[si] / Math.max(1, srows.size() - shown.size());
            Style.scrollbar(g, px + W - 5, top, barH, Math.min(t, barH - thumb), thumb);
        }
    }

    private static String preview(String text) {
        if (text.isBlank()) {
            return "= none";
        }
        long v = PaymentParser.parseMoney(text);
        return v < 0 ? "use 10k / 1.5m / 10000" : "= " + PaymentParser.full(v);
    }

    // ---- input

    @Override
    public boolean mouseClicked(MouseButtonEvent realEvent, boolean doubleClick) {
        MouseButtonEvent event = scaled(realEvent);
        double mx = event.x(), my = event.y();
        if (rebinding != Bind.NONE && event.button() >= 2) {
            int key = GambleState.MOUSE + event.button();
            switch (rebinding) {
                case CARD -> st.openKey = key;
                case PANEL -> st.settingsKey = key;
                case NEXT -> st.nextKey = key;
                case PAY -> st.payKey = key;
                default -> {
                }
            }
            rebinding = Bind.NONE;
            st.save();
            return true;
        }
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (GambleTour.click(mx, my)) {
            return true;
        }
        for (Btn b : buttons) {
            if (b.enabled() && b.contains(mx, my)) {
                st.click();
                b.action().run();
                return true;
            }
        }
        if (opY >= 0 && my >= opY - 2 && my < opY + 10 && mx >= opX - 3 && mx < opX + 63) {
            draggingOpacity = true;
            setOpacity(mx);
            return true;
        }
        if (mx >= hueX - 3 && mx < hueX + 63) {
            int which = hueAY >= 0 && my >= hueAY - 2 && my < hueAY + 10 ? 1 : hueBY >= 0 && my >= hueBY - 2 && my < hueBY + 10 ? 2 : 0;
            if (which != 0) {
                draggingHue = which;
                st.accent = Colors.CUSTOM_ACCENT;
                setHue(mx);
                return true;
            }
        }
        if (heightY >= 0 && my >= heightY - 2 && my < heightY + 10 && mx >= heightX - 3 && mx < heightX + 63) {
            draggingHeight = true;
            setHeight(mx);
            return true;
        }
        if (tab == Tab.QUEUE && mx >= x0 && mx < x0 + 110 && my >= checkY - 1 && my < checkY + 10) {
            st.tracking = !st.tracking;
            st.save();
            st.click();
            return true;
        }
        if (Style.modern()) {
            event = intoField(event);
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) {
            setFocused(null);
        }
        return handled || (mx >= px && mx < px + W && my >= py && my < py + ph);
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double scrollX, double scrollY) {
        double mx = realX / scale;
        if (tab != Tab.QUEUE && mx >= px && mx < px + W) {
            int si = tab.ordinal();
            if (scrollY < 0 && moreBelow) {
                scrolls[si] += 2;
            } else if (scrollY > 0) {
                scrolls[si] = Math.max(0, scrolls[si] - 2);
            }
            return true;
        }
        return super.mouseScrolled(mx, realY / scale, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent realEvent, double dx, double dy) {
        MouseButtonEvent event = scaled(realEvent);
        if (draggingOpacity) {
            setOpacity(event.x());
            return true;
        }
        if (draggingHeight) {
            setHeight(event.x());
            return true;
        }
        if (draggingHue != 0) {
            setHue(event.x());
            return true;
        }
        return super.mouseDragged(event, dx / scale, dy / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent realEvent) {
        if (draggingOpacity || draggingHeight || draggingHue != 0) {
            st.save();
        }
        draggingOpacity = draggingHeight = false;
        draggingHue = 0;
        return super.mouseReleased(scaled(realEvent));
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x / scale, y / scale);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (rebinding == Bind.NONE) {
            // Tab / Shift+Tab switch tabs (unless typing in a box)
            if (event.key() == InputConstants.KEY_TAB && !(getFocused() instanceof EditBox)) {
                Tab[] tabs = Tab.values();
                tab = tabs[Math.floorMod(tab.ordinal() + (event.hasShiftDown() ? -1 : 1), tabs.length)];
                st.click();
                return true;
            }
            return super.keyPressed(event);
        }
        if (!event.isEscape()) {
            int key = event.key();
            boolean clear = key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE;
            switch (rebinding) {
                case CARD -> st.openKey = key;
                case PANEL -> st.settingsKey = key;
                case NEXT -> st.nextKey = clear ? -1 : key;
                case PAY -> st.payKey = clear ? -1 : key;
                default -> {
                }
            }
        }
        rebinding = Bind.NONE;
        st.save();
        return true;
    }

    private MouseButtonEvent intoField(MouseButtonEvent e) {
        for (Map.Entry<EditBox, int[]> entry : frames.entrySet()) {
            EditBox box = entry.getKey();
            int[] f = entry.getValue();
            if (box.isVisible() && e.x() >= f[0] && e.x() < f[0] + f[2] && e.y() >= f[1] && e.y() < f[1] + f[3]) {
                double cx = Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, e.x()));
                double cy = Math.max(box.getY(), Math.min(box.getY() + box.getHeight() - 1, e.y()));
                return new MouseButtonEvent(cx, cy, e.buttonInfo());
            }
        }
        return e;
    }

    private MouseButtonEvent scaled(MouseButtonEvent e) {
        return new MouseButtonEvent(e.x() / scale, e.y() / scale, e.buttonInfo());
    }

    private void setHue(double mx) {
        int hue = (int) Math.round(Math.max(0, Math.min(1, (mx - hueX) / 56.0)) * 359);
        if (draggingHue == 1) {
            st.hueA = hue;
        } else {
            st.hueB = hue;
        }
        st.applyTheme();
    }

    private void setHeight(double mx) {
        st.heightPct = (int) Math.round(Math.max(0, Math.min(1, (mx - heightX) / 60.0)) * 100);
    }

    private void setOpacity(double mx) {
        st.opacity = (int) Math.round(35 + Math.max(0, Math.min(1, (mx - opX) / 60.0)) * 65);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        rebinding = Bind.NONE;
        st.save();
        super.removed();
    }
}
