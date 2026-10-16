package com.auctionoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class AuctionScreen extends Screen {
    static final int W = 214;
    static final int PAD = 7;
    static final int SLOT = 20;
    static final int COLS = 9;
    static final int CW = W - 2 * PAD;
    private static final int LIST_ROWS = 3;
    private static final int LIST_ROW_H = 15;

    static final String[] POPULAR = {
            "diamond", "netherite_ingot", "diamond_sword", "netherite_sword", "mace",
            "elytra", "totem_of_undying", "enchanted_golden_apple", "golden_apple",
            "spawner:skeleton", "spawner:zombie", "spawner:blaze", "spawner:iron_golem", "spawner:creeper",
            "spawner:zombified_piglin", "spawner:spider", "spawner:pig", "spawner:cow",
            "diamond_pickaxe", "netherite_pickaxe", "trident", "nether_star", "beacon",
            "diamond_block", "netherite_block", "emerald", "emerald_block", "gold_ingot",
            "gold_block", "iron_ingot", "iron_block", "shulker_box", "ender_pearl",
            "enchanted_book", "experience_bottle", "end_crystal", "heavy_core",
            "dragon_egg", "spawner", "netherite_helmet", "netherite_chestplate",
            "netherite_leggings", "netherite_boots", "diamond_helmet",
            "diamond_chestplate", "diamond_leggings", "diamond_boots", "bow",
            "crossbow", "shield", "firework_rocket", "name_tag", "saddle",
            "golden_carrot", "cooked_beef", "obsidian", "tnt",
    };

    private static final int[] RESET_SECS = {5, 10, 15, 20, 30};
    private static final int[] EXTEND_SECS = {10, 15, 30, 60};
    private static final int[] RESULT_SECS = {5, 10, 15, 30, 60};
    private static final int[] WARN_SECS = {5, 10, 15, 20, 30};
    private static final int[] HISTORY_ROWS = {3, 4, 6, 8};
    private static final int[] CARD_SIZES = {0, 75, 100, 125, 150};
    private static final int[] VOLUMES = {100, 75, 50, 25, 0};
    private static final int[] QUEUE_BREAKS = {5, 10, 15, 30, 60};
    private static final String[] TABS = Edition.PRO
            ? new String[]{"Look", "Worth", "Card", "Clean", "Auction", "Keys", "Stream", "Bidders", "Sales", "About"}
            : Edition.LOOKS ? new String[]{"Look", "Worth", "Card", "Clean", "Auction", "About"} : new String[]{"Look", "Card", "Clean", "Auction", "About"};
    private static final int TAB_COLS = 5;
    private static int settingsTab;

    // kept between openings of the panel
    private static boolean setupOpen = true;
    private static boolean settingsOpen;
    private static boolean rebinding;
    private static int rebindWhich; // 0 open/hide, 1 +time, 2 sell, 3 cancel, 4 announcement
    private static boolean pickLogo;
    private static String searchText = "";
    private static Lots.Entry selected;
    private static int listScroll;

    private record Btn(int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Row(String label, int y) {}

    // a payment in a list that can be clicked to copy "pay <name> <amount>" back to them
    private record Refund(int x, int y, int w, int h, String name, long amount) {}

    private final AuctionState st = AuctionState.INSTANCE;
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Row> settingRows = new ArrayList<>();
    private final Map<EditBox, int[]> frames = new IdentityHashMap<>(); // x, y, w, h of each field's frame
    private List<Lots.Entry> filtered = new ArrayList<>();
    private int gridRow;
    private int rows = 4;

    private EditBox search, amountBox, durMinBox, durSecBox, minPayBox, raiseBox, worthBox, bidName, bidAmount, titleBox, nameBox, worthLabelBox;
    private EditBox ftpPayBox, ftpPrizeBox, ftpMinBox, ftpSecBox;
    private EditBox gwEntryBox, gwPrizeBox, gwMinBox, gwSecBox;
    private float setupTabScale = 1;
    private final java.util.Set<String> setupTabLabels = new java.util.HashSet<>();
    private EditBox reserveBox, buyNowBox, winnersBox, dutchStartBox, dutchDropBox, dutchEveryBox, dutchFloorBox;
    private EditBox blockBox, formatBox, announceBox;
    private final List<Refund> refundRows = new ArrayList<>();
    private final Map<String, int[]> tabRects = new java.util.HashMap<>();
    private int settingsTop;
    private int ftpY, proLabelY, proY, dutchListY, salesY, queueLineY = -100;
    private final EditBox[] countBoxes = new EditBox[LIST_ROWS];
    private String setupMsg = "";
    private int setupMsgColor;

    private boolean draggingPanel, draggingSlider, draggingScroll;
    private int draggingHue; // 1 = colour 1, 2 = colour 2
    private int hueX, hueAY = -100, hueBY = -100, previewY = -100;
    private int dragDX, dragDY;

    private int px, py, ph, x0;
    private float scale = 1.0F;
    private int vw, vh;
    private int gridY, hoverY, selY, amountY, nameY, logoY, listY, worthY, durLabelY, durY, checkY, payY, msgY, btnY, footerY;
    private int sliderX, sliderY, sizeRowY, liveShift;

    public AuctionScreen() {
        super(Component.literal("Auction House"));
    }

    public static boolean isRebinding() {
        return rebinding;
    }

    // ---- for the tour

    static void prepareForTour() {
        setupOpen = true;
        settingsOpen = false;
        rebinding = false;
        selected = null;
        searchText = "";
    }

    static boolean hasSelection() {
        return selected != null;
    }

    static boolean settingsShown() {
        return settingsOpen;
    }

    static String currentTab() {
        return tab();
    }

    // the part of the panel the current tour step is about, in panel coordinates
    private int[] tourTarget() {
        String t = Tour.active() ? targetName() : "";
        if (t.startsWith("settings:")) {
            String want = t.substring(9);
            if (!settingsOpen) return new int[]{px + W - PAD - 39, py + 4, 11, 11};
            if (!tab().equals(want)) return tabRects.get(want);
            return new int[]{x0, settingsTop, CW, Math.max(16, btnY - 4 - settingsTop)};
        }
        return switch (t) {
            case "grid" -> new int[]{x0, gridY, COLS * SLOT + 8, rows * SLOT};
            case "rules" -> new int[]{x0, durLabelY - 2, CW, (Edition.PRO ? proY + 14 : durY + 14) - (durLabelY - 2)};
            case "start" -> new int[]{x0, btnY, Edition.PRO ? 84 : 118, 18};
            case "bidRow" -> new int[]{x0, py + 110 + liveShift, CW, 14};
            case "liveButtons" -> new int[]{x0, btnY, CW, 18};
            case "result" -> new int[]{x0, py + 22, CW, btnY - py - 26};
            case "gear" -> new int[]{px + W - PAD - 39, py + 4, 11, 11};
            case "tabs" -> new int[]{x0, py + 22, CW, ((TABS.length - 1) / TAB_COLS) * 16 + 15};
            default -> null;
        };
    }

    private static String targetName() {
        String t = Tour.targetName();
        return t == null ? "" : t;
    }

    private boolean liveView() {
        return !settingsOpen && !setupOpen && st.hasAuction;
    }

    private List<AuctionState.LotItem> lotItems() {
        return st.config.customItems;
    }

    @Override
    protected void init() {
        if (selected == null && !st.config.item.isEmpty()) {
            selected = Lots.get(st.config.item);
        }
        if (st.live) {
            setupOpen = false;
        }
        frames.clear();

        search = textBox(CW, searchText, 50, s -> {
            searchText = s;
            applyFilter();
        });
        search.setHint(Component.literal("Search items..."));

        amountBox = numberBox(30, String.valueOf(st.config.amount), v -> st.config.amount = Math.max(1, v));
        durMinBox = numberBox(26, String.valueOf(st.config.durMin), v -> st.config.durMin = v);
        durSecBox = numberBox(26, String.format("%02d", st.config.durSec), v -> st.config.durSec = v);
        minPayBox = textBox(84, st.config.minPayText, 24, v -> st.config.minPayText = v);
        raiseBox = numberBox(40, String.valueOf(st.config.raiseBy), v -> st.config.raiseBy = Math.max(1, v));
        worthBox = textBox(84, st.config.worthText, 24, v -> st.config.worthText = v);
        worthBox.setHint(Component.literal("e.g. 5m"));
        for (EditBox b : new EditBox[]{amountBox, durMinBox, durSecBox}) {
            b.setCentered(true);
        }

        nameBox = textBox(84, st.config.customName, 32, v -> st.config.customName = v);
        nameBox.setHint(Component.literal("e.g. Starter Kit"));
        for (int i = 0; i < LIST_ROWS; i++) {
            int row = i;
            countBoxes[i] = numberBox(28, "", v -> setCount(row, v));
            countBoxes[i].setMaxLength(5);
            countBoxes[i].setCentered(true);
        }
        syncCounts();

        bidName = textBox(92, "", 24, v -> {});
        bidName.setHint(Component.literal("Viewer name"));
        bidAmount = textBox(52, "", 16, v -> {});
        bidAmount.setHint(Component.literal("Amount"));
        titleBox = textBox(110, st.config.title == null ? "" : st.config.title, 24, v -> st.config.title = v);
        titleBox.setHint(Component.literal(Style.defaultTitle()));
        worthLabelBox = textBox(80, st.config.worthLabel, 12, v -> st.config.worthLabel = v);
        ftpPayBox = textBox(84, st.config.ftpPayText, 24, v -> st.config.ftpPayText = v);
        ftpPayBox.setHint(Component.literal("e.g. 5m"));
        ftpPrizeBox = textBox(84, st.config.ftpPrizeText, 24, v -> st.config.ftpPrizeText = v);
        ftpPrizeBox.setHint(Component.literal("e.g. 10m"));
        gwEntryBox = textBox(84, st.config.gwEntryText, 24, v -> st.config.gwEntryText = v);
        gwEntryBox.setHint(Component.literal("e.g. 1m"));
        gwPrizeBox = textBox(84, st.config.gwPrizeText, 24, v -> st.config.gwPrizeText = v);
        gwPrizeBox.setHint(Component.literal("e.g. 10m"));
        gwMinBox = numberBox(26, String.valueOf(st.config.gwMin), v -> st.config.gwMin = v);
        gwSecBox = numberBox(26, String.format("%02d", st.config.gwSec), v -> st.config.gwSec = v);
        ftpMinBox = numberBox(26, String.valueOf(st.config.ftpMin), v -> st.config.ftpMin = v);
        ftpSecBox = numberBox(26, String.format("%02d", st.config.ftpSec), v -> st.config.ftpSec = v);
        ftpMinBox.setCentered(true);
        ftpSecBox.setCentered(true);
        worthLabelBox.setHint(Component.literal("WORTH"));

        AuctionState.Config c = st.config;
        reserveBox = textBox(60, c.reserveText, 24, v -> c.reserveText = v);
        reserveBox.setHint(Component.literal("none"));
        buyNowBox = textBox(60, c.buyNowText, 24, v -> c.buyNowText = v);
        buyNowBox.setHint(Component.literal("none"));
        winnersBox = numberBox(26, String.valueOf(c.winners), v -> c.winners = Math.max(1, Math.min(9, v)));
        winnersBox.setMaxLength(1);
        winnersBox.setCentered(true);
        dutchStartBox = textBox(60, c.dutchStartText, 24, v -> c.dutchStartText = v);
        dutchStartBox.setHint(Component.literal("e.g. 10m"));
        dutchDropBox = textBox(56, c.dutchDropText, 24, v -> c.dutchDropText = v);
        dutchDropBox.setHint(Component.literal("e.g. 500k"));
        dutchEveryBox = numberBox(26, String.valueOf(c.dutchEverySecs), v -> c.dutchEverySecs = Math.max(1, v));
        dutchEveryBox.setMaxLength(3);
        dutchEveryBox.setCentered(true);
        dutchFloorBox = textBox(40, c.dutchFloorText, 24, v -> c.dutchFloorText = v);
        dutchFloorBox.setHint(Component.literal("1m"));
        blockBox = textBox(CW, c.blockList, 300, v -> c.blockList = v);
        blockBox.setHint(Component.literal("names, split by spaces or commas"));
        formatBox = textBox(CW, c.payFormat, 120, v -> c.payFormat = v);
        formatBox.setHint(Component.literal("{name} paid you {amount}"));
        announceBox = textBox(CW, c.announceText, 200, v -> c.announceText = v);
        announceBox.setHint(Component.literal(AuctionState.DEFAULT_ANNOUNCE));

        applyFilter();
        layout();
    }

    private EditBox textBox(int width, String value, int maxLength, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, width, 14, Component.literal(""));
        styleField(box);
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setResponder(onChange);
        addRenderableWidget(box);
        return box;
    }

    private EditBox numberBox(int width, String value, IntConsumer onChange) {
        EditBox box = new EditBox(font, 0, 0, width, 14, Component.literal(""));
        styleField(box);
        box.setMaxLength(7);
        box.setValue(value);
        box.setResponder(s -> {
            String digits = s.replaceAll("[^0-9]", "");
            if (!digits.equals(s)) {
                box.setValue(digits);
                return;
            }
            onChange.accept(digits.isEmpty() ? 0 : Integer.parseInt(digits));
        });
        addRenderableWidget(box);
        return box;
    }

    // the modern looks draw their own frame, so the EditBox goes borderless inside it
    private void styleField(EditBox box) {
        frames.put(box, new int[]{0, 0, box.getWidth(), 14});
        if (Style.modern()) {
            box.setBordered(false);
            box.setTextColor(Colors.TEXT);
            box.setWidth(box.getWidth() - 8);
            box.setHeight(11);
        }
    }

    private void applyFilter() {
        String q = searchText.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if (q.isEmpty()) {
            Set<Lots.Entry> ordered = new LinkedHashSet<>();
            for (String id : POPULAR) {
                Lots.Entry e = Lots.get(id);
                if (e != null) {
                    ordered.add(e);
                }
            }
            ordered.addAll(Lots.all());
            filtered = new ArrayList<>(ordered);
        } else {
            String[] words = q.split("\\s+");
            filtered = Lots.all().stream().filter(e -> Lots.matches(e, words)).toList();
        }
        gridRow = 0;
    }

    private int maxRow() {
        return Math.max(0, (filtered.size() + COLS - 1) / COLS - rows);
    }

    // ---- layout

    private void layout() {
        scale = st.scale();
        rows = 4;
        if (!settingsOpen && !liveView() && !st.config.ftpTab) {
            int spare = (int) (height / scale) - 8 - measure();
            if (spare < 0) {
                rows = Math.max(st.config.custom ? 1 : 2, 4 + Math.floorDiv(spare, SLOT));
            }
        }
        ph = measure();
        if (ph * scale > height - 4) {
            scale = Math.max(0.5F, (height - 4) / (float) ph); // still too tall: shrink to fit
        }
        vw = (int) (width / scale);
        vh = (int) (height / scale);
        if (st.config.panelX < 0) {
            st.config.panelX = 10;
            st.config.panelY = 10;
        }
        px = Math.max(0, Math.min(vw - W, (int) (st.config.panelX / scale)));
        py = Math.max(0, Math.min(vh - ph, (int) (st.config.panelY / scale)));
        layoutAt();
    }

    private int measure() {
        px = py = 0;
        layoutAt();
        return footerY + 14;
    }

    private void layoutAt() {
        x0 = px + PAD;
        buttons.clear();
        settingRows.clear();
        buttons.add(new Btn(px + W - PAD - 11, py + 4, 11, 11, Style.ICON_CLOSE, Colors.BTN, true, this::onClose));
        buttons.add(new Btn(px + W - PAD - 25, py + 4, 11, 11, Style.ICON_HIDE, Colors.BTN, true, this::onClose));
        buttons.add(new Btn(px + W - PAD - 39, py + 4, 11, 11, Style.ICON_GEAR, settingsOpen ? Colors.BTN_GO : Colors.BTN, true, () -> {
            settingsOpen = !settingsOpen;
            rebinding = false;
            st.save();
        }));

        boolean live = liveView();
        boolean setup = !settingsOpen && !live;
        boolean gwSetup = setup && Edition.PRO && st.config.gwTab;
        boolean ftpSetup = setup && !gwSetup && st.config.ftpTab;
        boolean special = ftpSetup || gwSetup; // the first-to-pay / giveaway setup pages, no item picker
        boolean custom = setup && !special && st.config.custom;
        boolean dutchSetup = setup && !special && Edition.PRO && st.config.dutch;
        for (EditBox b : new EditBox[]{search, worthBox}) {
            show(b, setup && !special);
        }
        for (EditBox b : new EditBox[]{durMinBox, durSecBox, minPayBox, raiseBox}) {
            show(b, setup && !special && !dutchSetup);
        }
        for (EditBox b : new EditBox[]{reserveBox, buyNowBox, winnersBox}) {
            show(b, setup && !special && !dutchSetup && Edition.PRO);
        }
        for (EditBox b : new EditBox[]{gwEntryBox, gwPrizeBox, gwMinBox, gwSecBox}) {
            show(b, gwSetup);
        }
        for (EditBox b : new EditBox[]{dutchStartBox, dutchDropBox, dutchEveryBox, dutchFloorBox}) {
            show(b, dutchSetup);
        }
        show(blockBox, settingsOpen && tab().equals("Bidders"));
        show(formatBox, settingsOpen && tab().equals("Bidders") && st.config.customFormat);
        show(announceBox, settingsOpen && tab().equals("Stream"));
        for (EditBox b : new EditBox[]{ftpPayBox, ftpPrizeBox, ftpMinBox, ftpSecBox}) {
            show(b, ftpSetup);
        }
        show(amountBox, setup && !custom && !special);
        show(nameBox, custom);
        for (int i = 0; i < LIST_ROWS; i++) {
            show(countBoxes[i], custom && listScroll + i < lotItems().size());
        }
        show(bidName, live && st.live && !st.instant());
        show(bidAmount, live && st.live && !st.instant());
        show(titleBox, settingsOpen && Edition.LOOKS && tab().equals("Look"));
        show(worthLabelBox, settingsOpen && tab().equals("Worth"));
        hueAY = hueBY = previewY = sizeRowY = -100;

        queueLineY = -100;
        if (settingsOpen) {
            layoutSettings();
        } else if (live) {
            if (st.gw) {
                layoutGwLive();
            } else if (st.ftp) {
                layoutFtpLive();
            } else if (st.dutch) {
                layoutDutchLive();
            } else {
                layoutLive();
            }
        } else if (gwSetup) {
            setupTabs();
            layoutGwSetup();
        } else if (ftpSetup) {
            setupTabs();
            layoutFtpSetup();
        } else {
            setupTabs();
            layoutSetup(custom);
        }
        sliderX = x0 + Math.min(46, Txt.width(font, "Opacity") + 5); // right after the label, leaving room for the Discord link
        sliderY = footerY + 1;
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

    private void setupTabs() {
        int tab = Edition.PRO && st.config.gwTab ? 3 : st.config.ftpTab ? 2 : st.config.custom ? 1 : 0;
        String[] labels = Edition.PRO ? new String[]{"Item", "Custom lot", "First to pay", "Giveaway"} : new String[]{"Item", "Custom lot", "First to pay"};
        Runnable[] actions = {
                () -> {
                    st.config.custom = false;
                    st.config.ftpTab = false;
                    st.config.gwTab = false;
                },
                () -> {
                    st.config.custom = true;
                    st.config.ftpTab = false;
                    st.config.gwTab = false;
                    syncCounts();
                },
                () -> {
                    st.config.ftpTab = true;
                    st.config.gwTab = false;
                },
                () -> {
                    st.config.gwTab = true;
                    st.config.ftpTab = false;
                },
        };
        // sized to the text; when they don't all fit, every tab's text shrinks by the same amount
        int text = 0;
        for (String l : labels) text += Txt.width(font, l);
        int pads = labels.length * 10 + (labels.length - 1) * 3;
        setupTabScale = text + pads <= CW ? 1 : Math.max(0.6F, (float) Math.floor((CW - pads) / (float) text * 20) / 20);
        setupTabLabels.clear();
        int x = x0;
        for (int i = 0; i < labels.length; i++) {
            int w = Math.round(Txt.width(font, labels[i]) * setupTabScale) + 10;
            boolean has = i == 0 || (i == 1 ? Edition.CUSTOM_LOTS : i == 2 ? Edition.FIRST_TO_PAY : Edition.PRO);
            buttons.add(new Btn(x, py + 22, w, 13, labels[i], tab == i ? Colors.BTN_GO : Colors.BTN, has, actions[i]));
            setupTabLabels.add(labels[i]);
            x += w + 3;
        }
    }

    private void layoutGwSetup() {
        ftpY = py + 40;
        place(gwEntryBox, x0 + 62, ftpY);
        buttons.add(new Btn(x0 + 150, ftpY, 50, 14, st.config.gwMulti ? "Stack" : "Once", Colors.BTN, true,
                () -> st.config.gwMulti = !st.config.gwMulti));
        place(gwPrizeBox, x0 + 62, ftpY + 18);
        place(gwMinBox, x0 + 62, ftpY + 36);
        place(gwSecBox, x0 + 97, ftpY + 36);
        int qx = x0 + 128;
        for (int mins : new int[]{1, 2, 5}) {
            buttons.add(new Btn(qx, ftpY + 36, 22, 14, mins + "m", Colors.BTN, true, () -> {
                gwMinBox.setValue(String.valueOf(mins));
                gwSecBox.setValue("00");
            }));
            qx += 24;
        }
        checkY = -100;
        msgY = ftpY + 96;
        btnY = msgY + 11;
        footerY = btnY + 23;
        buttons.add(new Btn(x0, btnY, 118, 18, "Start giveaway", Colors.BTN_GO, true, this::startAuction));
        buttons.add(new Btn(x0 + 122, btnY, 78, 18, "Clear", Colors.BTN, true, this::clearSetup));
    }

    private void layoutGwLive() {
        int listY = py + 98;
        btnY = listY + historyRows() * 10 + 10;
        footerY = btnY + 23;
        if (st.live) {
            buttons.add(new Btn(x0, btnY, 64, 18, "+" + st.config.extendSecs + "s", Colors.BTN, true, () -> st.extend(st.config.extendSecs)));
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Draw now", Colors.BTN_GO, st.gwEntries() > 0, () -> st.finish(false)));
            buttons.add(new Btn(x0 + 136, btnY, 64, 18, "Cancel", Colors.BTN_STOP, true, () -> st.finish(true)));
        } else {
            buttons.add(new Btn(x0, btnY, 64, 18, "New", Colors.BTN_GO, true, this::newAuction));
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Run again", Colors.BTN, true, this::runAgain));
            buttons.add(new Btn(x0 + 136, btnY, 64, 18, "Copy pay", Colors.BTN, st.winnerName() != null, st::copyPrize));
        }
    }

    private void layoutFtpSetup() {
        ftpY = py + 40;
        place(ftpPayBox, x0 + 62, ftpY);
        buttons.add(new Btn(x0 + 150, ftpY, 50, 14, st.config.ftpExact ? "Exactly" : "At least", Colors.BTN, true,
                () -> st.config.ftpExact = !st.config.ftpExact));
        place(ftpPrizeBox, x0 + 62, ftpY + 18);
        place(ftpMinBox, x0 + 62, ftpY + 36);
        place(ftpSecBox, x0 + 97, ftpY + 36);
        int qx = x0 + 128;
        for (int secs : new int[]{5, 10, 30}) {
            buttons.add(new Btn(qx, ftpY + 36, 22, 14, secs + "s", Colors.BTN, true, () -> {
                ftpMinBox.setValue("0");
                ftpSecBox.setValue(String.format("%02d", secs));
            }));
            qx += 24;
        }
        checkY = ftpY + 56;
        msgY = ftpY + 96;
        btnY = msgY + 11;
        footerY = btnY + 23;
        buttons.add(new Btn(x0, btnY, 118, 18, "Start countdown", Colors.BTN_GO, true, this::startAuction));
        buttons.add(new Btn(x0 + 122, btnY, 78, 18, "Clear", Colors.BTN, true, this::clearSetup));
    }

    private void layoutFtpLive() {
        int listY = py + 98;
        btnY = listY + historyRows() * 10 + 10;
        footerY = btnY + 23;
        if (st.live) {
            if (!st.ftpGo) {
                buttons.add(new Btn(x0, btnY, 96, 18, "+" + st.config.extendSecs + "s", Colors.BTN, true, () -> st.extend(st.config.extendSecs)));
            }
            buttons.add(new Btn(x0 + CW - 96, btnY, 96, 18, "Cancel", Colors.BTN_STOP, true, () -> st.finish(true)));
        } else {
            buttons.add(new Btn(x0, btnY, 64, 18, "New game", Colors.BTN_GO, true, this::newAuction));
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Run again", Colors.BTN, true, this::runAgain));
            buttons.add(new Btn(x0 + 136, btnY, 64, 18, "Copy pay", Colors.BTN, st.winnerName() != null, st::copyPrize));
        }
    }

    // dutch: price box + a list of payments that were too low (to refund)
    private void layoutDutchLive() {
        liveShift = panelContents() ? 11 : 0;
        dutchListY = py + 112 + liveShift;
        btnY = dutchListY + historyRows() * 10 + 10;
        footerY = btnY + 23;
        if (st.live) {
            buttons.add(new Btn(x0 + CW - 96, btnY, 96, 18, "Cancel", Colors.BTN_STOP, true, () -> st.finish(true)));
        } else {
            finishedButtons();
        }
    }

    private void finishedButtons() {
        buttons.add(new Btn(x0, btnY, 64, 18, "New auction", Colors.BTN_GO, true, this::newAuction));
        if (Edition.PRO && !st.config.queue.isEmpty()) {
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Next lot", Colors.BTN, true, st::resumeQueue));
        } else {
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Run again", Colors.BTN, true, this::runAgain));
        }
        if (Edition.COPY_TPA) {
            buttons.add(new Btn(x0 + 136, btnY, 64, 18, "Copy tpa", Colors.BTN, st.winnerName() != null, st::copyTpa));
        }
    }

    private void newAuction() {
        st.queuePaused = true; // starting something by hand holds the queue
        setupOpen = true;
    }

    private void layoutSetup(boolean custom) {
        place(search, x0, py + 37);
        gridY = py + 55;
        hoverY = gridY + rows * SLOT + 3;

        int y = hoverY + 12;
        if (custom) {
            nameY = y;
            place(nameBox, x0 + 30, nameY);
            buttons.add(new Btn(x0 + 117, nameY, 30, 14, "Save", Colors.BTN, true, this::savePreset));
            buttons.add(new Btn(x0 + CW - 50, nameY, 50, 14, "Presets", Colors.BTN, true, this::openKits));
            logoY = nameY + 18;
            buttons.add(new Btn(x0 + 24, logoY + 3, 34, 14, "Logo", pickLogo ? Colors.BTN_GO : Colors.BTN, true, () -> pickLogo = true));
            buttons.add(new Btn(x0 + 60, logoY + 3, 30, 14, "Add", pickLogo ? Colors.BTN : Colors.BTN_GO, true, () -> pickLogo = false));
            buttons.add(new Btn(x0 + 94, logoY + 3, 34, 14, "Clear", Colors.BTN, !lotItems().isEmpty(), this::clearLot));
            AuctionState.Preset saved = st.findPreset(st.config.customName);
            buttons.add(new Btn(x0 + 132, logoY + 3, 68, 14, "Del saved", Colors.BTN, saved != null, () -> deletePreset(saved)));
            listY = logoY + 23;
            for (int i = 0; i < LIST_ROWS; i++) {
                int idx = listScroll + i;
                if (idx < lotItems().size()) {
                    place(countBoxes[i], x0 + 3, listY + 2 + i * LIST_ROW_H);
                    buttons.add(new Btn(x0 + CW - 14, listY + 4 + i * LIST_ROW_H, 10, 10, "x", Colors.BTN, true, () -> removeFromLot(idx)));
                }
            }
            y = listY + LIST_ROWS * LIST_ROW_H + 8;
        } else {
            selY = y;
            amountY = selY + 24;
            buttons.add(new Btn(x0 + 40, amountY, 12, 14, "-", Colors.BTN, true, () -> bumpAmount(-1)));
            place(amountBox, x0 + 54, amountY);
            buttons.add(new Btn(x0 + 86, amountY, 12, 14, "+", Colors.BTN, true, () -> bumpAmount(1)));
            buttons.add(new Btn(x0 + 102, amountY, 18, 14, "16", Colors.BTN, true, () -> amountBox.setValue("16")));
            buttons.add(new Btn(x0 + 122, amountY, 18, 14, "64", Colors.BTN, true, () -> amountBox.setValue("64")));
            if (Edition.CUSTOM_LOTS) {
                buttons.add(new Btn(x0 + CW - 50, amountY, 50, 14, "Presets", Colors.BTN, true, this::openKits)); // saved lots, like a neth kit
            }
            y = amountY + 18;
        }

        worthY = y;
        place(worthBox, x0 + 40, worthY);
        boolean dutch = Edition.PRO && st.config.dutch;
        if (Edition.PRO) {
            buttons.add(new Btn(x0 + 136, worthY, 64, 14, dutch ? "Type: Dutch" : "Type: Normal", dutch ? Colors.BTN_GO : Colors.BTN, true,
                    () -> st.config.dutch = !st.config.dutch));
        }
        durLabelY = worthY + 19;
        durY = durLabelY + 10;
        if (dutch) {
            place(dutchStartBox, x0, durY);
            place(dutchDropBox, x0 + 64, durY);
            place(dutchEveryBox, x0 + 124, durY);
            place(dutchFloorBox, x0 + 160, durY);
            proY = durY;
        } else {
            place(durMinBox, x0, durY);
            place(durSecBox, x0 + 35, durY);
            place(minPayBox, x0 + 68, durY);
            place(raiseBox, x0 + 160, durY);
            proY = durY;
            if (Edition.PRO) {
                proLabelY = durY + 19;
                proY = proLabelY + 10;
                place(reserveBox, x0, proY);
                place(buyNowBox, x0 + 68, proY);
                place(winnersBox, x0 + 136, proY);
                buttons.add(new Btn(x0 + 165, proY, 16, 14, "-", Colors.BTN, true, () -> winnersBox.setValue(String.valueOf(Math.max(1, st.config.winners - 1)))));
                buttons.add(new Btn(x0 + 184, proY, 16, 14, "+", Colors.BTN, true, () -> winnersBox.setValue(String.valueOf(Math.min(9, st.config.winners + 1)))));
            }
        }
        checkY = proY + 19;
        payY = checkY + 13;
        msgY = payY + 13;
        btnY = msgY + 11;
        footerY = btnY + 23;
        if (Edition.PRO) {
            buttons.add(new Btn(x0, btnY, 84, 18, "Start auction", Colors.BTN_GO, true, this::startAuction));
            buttons.add(new Btn(x0 + 88, btnY, 56, 18, "+ Queue", Colors.BTN, true, this::queueLot));
            buttons.add(new Btn(x0 + 148, btnY, 52, 18, "Clear", Colors.BTN, true, this::clearSetup));
        } else {
            buttons.add(new Btn(x0, btnY, 118, 18, "Start auction", Colors.BTN_GO, true, this::startAuction));
            buttons.add(new Btn(x0 + 122, btnY, 78, 18, "Clear", Colors.BTN, true, this::clearSetup));
        }
    }

    private int historyRows() {
        return Math.max(1, Math.min(8, st.config.historyRows));
    }

    private void layoutLive() {
        liveShift = (panelContents() ? 11 : 0) + (st.worthStyle() == 0 ? 19 : 0);
        int extraRows = (historyRows() - 4) * 10;
        int bidRowY = py + 110 + liveShift;
        btnY = py + 188 + liveShift + extraRows;
        footerY = py + 211 + liveShift + extraRows;
        place(bidName, x0, bidRowY);
        place(bidAmount, x0 + 96, bidRowY);
        if (st.live) {
            buttons.add(new Btn(x0 + 152, bidRowY, 48, 14, "Bid", Colors.BTN_GO, true, this::placeBid));
            buttons.add(new Btn(x0, btnY, 64, 18, "+" + st.config.extendSecs + "s", Colors.BTN, true, () -> st.extend(st.config.extendSecs)));
            buttons.add(new Btn(x0 + 68, btnY, 64, 18, "Stop & sell", Colors.BTN_STOP, true, () -> st.finish(false)));
            buttons.add(new Btn(x0 + 136, btnY, 64, 18, "Cancel", Colors.BTN, true, () -> st.finish(true)));
        } else {
            queueLineY = bidRowY + 3; // the bid boxes are gone once it's over
            finishedButtons();
        }
    }

    // one text size for all the settings tabs: the size the longest name needs (in 10% steps)
    private float tabTextScale() {
        float s = 1;
        for (Map.Entry<String, int[]> e : tabRects.entrySet()) {
            int tw = Txt.width(font, e.getKey());
            if (tw > e.getValue()[2] - 4) {
                s = Math.min(s, (e.getValue()[2] - 4) / (float) tw);
            }
        }
        return s >= 1 ? 1 : Math.max(0.6F, (float) Math.floor(s * 10) / 10);
    }

    private void layoutSettings() {
        int cols = Math.min(TAB_COLS, TABS.length);
        int tw = (CW - 3 * (cols - 1)) / cols;
        tabRects.clear();
        for (int i = 0; i < TABS.length; i++) {
            int tab = i;
            tabRects.put(TABS[i], new int[]{x0 + (i % cols) * (tw + 3), py + 24 + (i / cols) * 16, tw, 13});
            buttons.add(new Btn(x0 + (i % cols) * (tw + 3), py + 24 + (i / cols) * 16, tw, 13, TABS[i],
                    settingsTab == i ? Colors.BTN_GO : Colors.BTN, true, () -> {
                settingsTab = tab;
                rebinding = false;
            }));
        }
        int y = py + 43 + ((TABS.length - 1) / cols) * 16;
        settingsTop = y - 2;
        int cx = x0 + CW - 80;
        switch (tab()) {
            case "Look" -> y = lookTab(y, cx);
            case "Worth" -> y = worthTab(y, cx);
            case "Card" -> y = cardTab(y, cx);
            case "Clean" -> y = cleanTab(y, cx);
            case "Keys" -> y = keysTab(y, cx);
            case "Stream" -> y = streamTab(y, cx);
            case "Bidders" -> y = biddersTab(y, cx);
            case "Sales" -> y = salesTab(y, cx);
            case "About" -> y = aboutTab(y, cx);
            default -> y = auctionTab(y, cx);
        }
        btnY = y + 4;
        buttons.add(new Btn(x0, btnY, 96, 18, "Reset position", Colors.BTN, true, () -> {
            st.config.panelX = 10;
            st.config.panelY = 10;
            st.save();
        }));
        buttons.add(new Btn(x0 + CW - 96, btnY, 96, 18, "Done", Colors.BTN_GO, true, () -> {
            settingsOpen = false;
            rebinding = false;
            st.save();
        }));
        footerY = btnY + 23;
    }

    private static String tab() {
        return TABS[Math.floorMod(settingsTab, TABS.length)];
    }

    private int lookTab(int y, int cx) {
        AuctionState.Config c = st.config;
        if (!Edition.LOOKS) {
            row("Accent colour", y);
            setting(cx, y, Colors.SOLID_NAMES[Math.floorMod(c.accent, Colors.SOLID_NAMES.length)], true, () -> {
                c.accent = (c.accent + 1) % Colors.SOLID_NAMES.length;
                st.applyTheme();
            });
            y += 16;
            row("More looks: discord.gg/B64z86e9ZY", y);
            return y + 16;
        }
        row("Look", y);
        setting(cx, y, Style.look().label, true, () -> {
            c.look = Style.look().next().name();
            st.applyTheme();
            rebuildWidgets();
        });
        y += 16;
        row("Accent colours", y);
        setting(cx, y, Colors.ACCENT_NAMES[Math.floorMod(c.accent, Colors.ACCENT_NAMES.length)], Style.modern(), () -> {
            c.accent = (c.accent + 1) % Colors.ACCENT_NAMES.length;
            st.applyTheme();
        });
        y += 16;
        hueX = cx + 10;
        row("Custom colour 1", y);
        hueAY = y + 3;
        y += 16;
        row("Custom colour 2", y);
        hueBY = y + 3;
        y += 16;
        row("Corners", y);
        setting(cx, y, Style.CORNER_NAMES[Math.floorMod(c.corners, Style.CORNER_NAMES.length)], Style.modern(), () -> {
            c.corners = (c.corners + 1) % Style.CORNER_NAMES.length;
            st.applyTheme();
        });
        y += 16;
        row("Title", y);
        place(titleBox, x0 + CW - 110, y);
        y += 16;
        row("Title colour", y);
        setting(cx, y, Colors.TITLE_COLOR_NAMES[Math.floorMod(c.titleColor, Colors.TITLE_COLOR_NAMES.length)], true, () -> {
            c.titleColor = (c.titleColor + 1) % Colors.TITLE_COLOR_NAMES.length;
            st.applyTheme();
        });
        y += 16;
        row("Text shadow", y);
        setting(cx, y, onOff(c.textShadow), true, () -> {
            c.textShadow = !c.textShadow;
            st.applyTheme();
        });
        return y + 16;
    }

    private int worthTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row("Worth style", y);
        setting(cx, y, AuctionState.WORTH_STYLES[Math.floorMod(c.worthStyle, AuctionState.WORTH_STYLES.length)], true,
                () -> c.worthStyle = (c.worthStyle + 1) % AuctionState.WORTH_STYLES.length);
        y += 16;
        row("Worth colour", y);
        setting(cx, y, AuctionState.WORTH_COLOR_NAMES[Math.floorMod(c.worthColor, AuctionState.WORTH_COLOR_NAMES.length)], true,
                () -> c.worthColor = (c.worthColor + 1) % AuctionState.WORTH_COLOR_NAMES.length);
        y += 16;
        row("Worth label", y);
        place(worthLabelBox, cx, y);
        y += 16;
        row("Show on in-game card", y);
        setting(cx, y, onOff(c.worthOnCard), true, () -> c.worthOnCard = !c.worthOnCard);
        y += 16;
        row("Put in OBS text file", y);
        setting(cx, y, onOff(c.worthInObs), true, () -> c.worthInObs = !c.worthInObs);
        y += 18;
        row("Preview", y);
        previewY = y + 11;
        return previewY + 22;
    }

    private int cardTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row("In-game card", y);
        setting(cx, y, onOff(c.showCard), true, () -> c.showCard = !c.showCard);
        y += 16;
        row("Card position", y);
        setting(cx, y, AuctionState.CARD_SPOTS[Math.floorMod(c.cardSpot, AuctionState.CARD_SPOTS.length)], true,
                () -> c.cardSpot = (c.cardSpot + 1) % AuctionState.CARD_SPOTS.length);
        y += 16;
        row("Card size", y);
        setting(cx, y, c.cardSize <= 0 ? "Same as panel" : c.cardSize + "%", true, () -> c.cardSize = cycle(CARD_SIZES, c.cardSize));
        y += 16;
        row("Show next bid", y);
        setting(cx, y, onOff(c.cardNext), true, () -> c.cardNext = !c.cardNext);
        y += 16;
        row("Show top bidder", y);
        setting(cx, y, onOff(c.cardBidder), true, () -> c.cardBidder = !c.cardBidder);
        y += 16;
        row("Show lot contents", y);
        setting(cx, y, onOff(c.cardContents), true, () -> c.cardContents = !c.cardContents);
        y += 16;
        row("Show key hint", y);
        setting(cx, y, onOff(c.cardHint), true, () -> c.cardHint = !c.cardHint);
        y += 16;
        row("Show result for", y);
        setting(cx, y, c.resultSecs + "s", true, () -> c.resultSecs = cycle(RESULT_SECS, c.resultSecs));
        return y + 16;
    }

    // only what you want to see: "Essentials only" switches every extra off, then switch back on anything you miss
    private int cleanTab(int y, int cx) {
        AuctionState.Config c = st.config;
        boolean ess = st.essentialsOnly();
        row(ess ? "Essentials only - on" : "Essentials only", y);
        setting(cx, y, ess ? "Show everything" : "Turn on", true, () -> st.setEssentialsOnly(!ess));
        y += 18;
        row("Panel: key hint at the top", y);
        setting(cx, y, onOff(c.panelHint), true, () -> c.panelHint = !c.panelHint);
        y += 16;
        row("Panel: min / raise line", y);
        setting(cx, y, onOff(c.liveRules), true, () -> c.liveRules = !c.liveRules);
        y += 16;
        row("Panel: watching chat", y);
        setting(cx, y, onOff(c.watchingText), true, () -> c.watchingText = !c.watchingText);
        y += 16;
        row("Panel: lot contents line", y);
        setting(cx, y, onOff(c.panelContents), true, () -> c.panelContents = !c.panelContents);
        y += 16;
        row("Panel: Discord link", y);
        setting(cx, y, onOff(c.footerLink), true, () -> c.footerLink = !c.footerLink);
        y += 16;
        row("Blinking LIVE and timer", y);
        setting(cx, y, onOff(c.blink), true, () -> c.blink = !c.blink);
        y += 16;
        row("Card: TIME LEFT / TOP BID", y);
        setting(cx, y, onOff(c.cardLabels), true, () -> c.cardLabels = !c.cardLabels);
        y += 16;
        row("Card: next bid", y);
        setting(cx, y, onOff(c.cardNext), true, () -> c.cardNext = !c.cardNext);
        y += 16;
        row("Card: top bidder", y);
        setting(cx, y, onOff(c.cardBidder), true, () -> c.cardBidder = !c.cardBidder);
        y += 16;
        row("Card: lot contents", y);
        setting(cx, y, onOff(c.cardContents), true, () -> c.cardContents = !c.cardContents);
        y += 16;
        row("Card: key hint", y);
        setting(cx, y, onOff(c.cardHint), true, () -> c.cardHint = !c.cardHint);
        return y + 16;
    }

    private int auctionTab(int y, int cx) {
        AuctionState.Config c = st.config;
        if (!Edition.PRO) {
            row("Open / hide key", y);
            setting(cx, y, rebinding ? "Press a key..." : AuctionState.keyName(c.toggleKey), true, () -> rebind(0));
            y += 16;
        }
        row("Panel size (Ctrl +/-)", y);
        sizeRowY = y;
        buttons.add(new Btn(cx, y, 16, 13, "-", Colors.BTN, true, () -> st.adjustScale(-1)));
        buttons.add(new Btn(cx + 64, y, 16, 13, "+", Colors.BTN, true, () -> st.adjustScale(1)));
        y += 16;
        row("Late-bid timer reset", y);
        setting(cx, y, c.antiSnipeSecs + "s", true, () -> c.antiSnipeSecs = cycle(RESET_SECS, c.antiSnipeSecs));
        y += 16;
        row("Extend button", y);
        setting(cx, y, "+" + c.extendSecs + "s", true, () -> c.extendSecs = cycle(EXTEND_SECS, c.extendSecs));
        y += 16;
        row("Timer flashes at", y);
        setting(cx, y, c.warnSecs + "s left", true, () -> c.warnSecs = cycle(WARN_SECS, c.warnSecs));
        y += 16;
        row("Numbers", y);
        setting(cx, y, c.shortNumbers ? "Short (1.25m)" : "Full (1,250,000)", true, () -> c.shortNumbers = !c.shortNumbers);
        y += 16;
        row("Bid history rows", y);
        setting(cx, y, String.valueOf(c.historyRows), true, () -> c.historyRows = cycle(HISTORY_ROWS, c.historyRows));
        y += 16;
        if (Edition.COPY_TPA) {
            row("Copy tpa for winner", y);
            setting(cx, y, onOff(c.copyTpa), true, () -> c.copyTpa = !c.copyTpa);
            y += 16;
        }
        if (!Edition.PRO) {
            row("OBS text file", y);
            setting(cx, y, onOff(c.obsFile), true, () -> c.obsFile = !c.obsFile);
            y += 16;
        }
        row("Sounds", y);
        setting(cx, y, !c.sounds || c.volume <= 0 ? "Off" : c.volume + "%", true, () -> {
            c.volume = cycle(VOLUMES, c.sounds ? c.volume : 0);
            c.sounds = c.volume > 0;
        });
        y += 16;
        if (Edition.PRO) {
            row("Countdown bells", y);
            setting(cx, y, onOff(c.tickSounds), true, () -> c.tickSounds = !c.tickSounds);
            y += 16;
            row("Queue break", y);
            setting(cx, y, c.queueBreakSecs + "s", true, () -> c.queueBreakSecs = cycle(QUEUE_BREAKS, c.queueBreakSecs));
            y += 16;
            int n = c.queue.size();
            row(n == 0 ? "Queue is empty" : n + (n == 1 ? " lot" : " lots") + " queued", y);
            setting(cx, y, "Clear queue", n > 0, st::clearQueue);
            y += 16;
        }
        row("Tutorial", y);
        setting(cx, y, "Start tour", true, () -> {
            onClose();
            Tour.start();
        });
        y += 16;
        row(st.config.tutorialDone ? "Reset tutorial" : "Tutorial shows on next join", y);
        setting(cx, y, "Reset", st.config.tutorialDone, () -> {
            AuctionMod.resetTutorial();
            st.toast("Tutorial reset - it shows next time you join a world");
        });
        y += 16;
        return y;
    }

    private void rebind(int which) {
        rebinding = true;
        rebindWhich = which;
    }

    private String keyLabel(int which, int key) {
        return rebinding && rebindWhich == which ? "Press a key..." : AuctionState.keyName(key);
    }

    private int keysTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row("Open / hide panel", y);
        setting(cx, y, keyLabel(0, c.toggleKey), true, () -> rebind(0));
        y += 16;
        row("Add time (+" + c.extendSecs + "s)", y);
        setting(cx, y, keyLabel(1, c.keyExtend), true, () -> rebind(1));
        y += 16;
        row("Stop & sell", y);
        setting(cx, y, keyLabel(2, c.keySell), true, () -> rebind(2));
        y += 16;
        row("Cancel auction", y);
        setting(cx, y, keyLabel(3, c.keyCancel), true, () -> rebind(3));
        y += 16;
        row("Copy announcement", y);
        setting(cx, y, keyLabel(4, c.keyAnnounce), true, () -> rebind(4));
        y += 16;
        row("These work while you play.", y);
        y += 12;
        row("Backspace clears a key.", y);
        return y + 14;
    }

    private int streamTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row("OBS text file", y);
        setting(cx, y, onOff(c.obsFile), true, () -> c.obsFile = !c.obsFile);
        y += 16;
        row("OBS overlay", y);
        buttons.add(new Btn(cx, y, 38, 13, onOff(c.overlay), Colors.BTN, true, () -> c.overlay = !c.overlay));
        buttons.add(new Btn(cx + 42, y, 38, 13, "Folder", Colors.BTN, true, () -> Stream.openFolder(Stream.folder())));
        y += 16;
        row("Discord: " + Stream.maskedWebhook(c.webhook), y);
        boolean set = Stream.validWebhook(c.webhook);
        buttons.add(new Btn(cx, y, 36, 13, "Paste", Colors.BTN, true, this::pasteWebhook));
        buttons.add(new Btn(cx + 39, y, 26, 13, "Test", Colors.BTN, set, () -> {
            Stream.test(c.webhook.trim());
            st.toast("Sent a test message");
        }));
        buttons.add(new Btn(cx + 68, y, 12, 13, "x", Colors.BTN, set, () -> c.webhook = ""));
        y += 16;
        row("Post when it starts", y);
        setting(cx, y, onOff(c.hookStart), set, () -> c.hookStart = !c.hookStart);
        y += 16;
        row("Post new top bids", y);
        setting(cx, y, onOff(c.hookBids), set, () -> c.hookBids = !c.hookBids);
        y += 16;
        row("Post the result", y);
        setting(cx, y, onOff(c.hookSold), set, () -> c.hookSold = !c.hookSold);
        y += 16;
        row("Chat announcement (copy key):", y);
        y += 12;
        place(announceBox, x0, y);
        return y + 18;
    }

    private void pasteWebhook() {
        String clip = minecraft.keyboardHandler.getClipboard().trim();
        if (Stream.validWebhook(clip)) {
            st.config.webhook = clip;
            st.toast("Discord webhook saved");
        } else {
            st.toast("Copy the webhook link from Discord first");
        }
    }

    private int biddersTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row(c.allowOnly ? "Only these players can bid" : "Blocked players (never count)", y);
        y += 12;
        place(blockBox, x0, y);
        y += 18;
        row("The list means", y);
        setting(cx, y, c.allowOnly ? "Only these" : "Blocked", true, () -> c.allowOnly = !c.allowOnly);
        y += 16;
        row("Payment message", y);
        setting(cx, y, c.customFormat ? "Custom" : "Auto (most servers)", true, () -> c.customFormat = !c.customFormat);
        y += 16;
        if (c.customFormat) {
            place(formatBox, x0, y);
            y += 18;
            row(PaymentDetector.validFormat(c.payFormat) ? "Matches the whole chat line, e.g." : "Needs {name} and {amount} once each", y);
            y += 11;
            row("{name} sent you {amount} coins", y);
            y += 14;
        } else {
            row("Auto reads DonutSMP, EssentialsX and most", y);
            y += 11;
            row("other economy plugins.", y);
            y += 14;
        }
        return y;
    }

    private int aboutTab(int y, int cx) {
        AuctionState.Config c = st.config;
        row("Auction Overlay " + Edition.NAME + " " + Online.version(), y);
        y += 13;
        row("Minecraft " + Online.minecraft(), y);
        y += 16;
        String update = Online.update();
        row(update != null ? "Update " + update + " is out!" : c.checkUpdates ? "You're up to date" : "Update checks are off", y);
        if (update != null && Updater.ownJar() != null) {
            Updater.State us = Updater.state();
            setting(cx, y, us == Updater.State.WORKING ? "Updating..." : us == Updater.State.READY ? "Restart MC" : "Update now",
                    us != Updater.State.WORKING && us != Updater.State.READY, () -> minecraft.gui.setScreen(new UpdatePromptScreen(false)));
        } else {
            setting(cx, y, "Download page", true, Online::openDownloads);
        }
        y += 16;
        row("Check for updates", y);
        setting(cx, y, onOff(c.checkUpdates), true, () -> c.checkUpdates = !c.checkUpdates);
        y += 16;
        row("Share anonymous stats", y);
        setting(cx, y, onOff(c.shareStats), true, () -> c.shareStats = !c.shareStats);
        y += 16;
        row("Only counts minutes used, auctions", y);
        y += 11;
        row("run and sold, bids, edition, version.", y);
        y += 11;
        row("No names, chat, amounts or servers.", y);
        y += 16;
        row("zzmods account", y);
        setting(cx, y, c.linkSecret == null ? "Link account" : c.linkedEmail == null ? "Cancel" : "Unlink", true, () -> {
            if (c.linkSecret == null) Online.startLink();
            else Online.unlink();
        });
        y += 15;
        row(Txt.cut(font, Online.linkState(), CW), y);
        y += 11;
        row("Linked: shares your MC name + stats", y);
        y += 11;
        row("and gets support's test payments.", y);
        return y + 14;
    }

    private int salesTab(int y, int cx) {
        salesY = y;
        y += 6 * 13;
        row("Last price in setup", y);
        setting(cx, y, onOff(st.config.priceGuide), true, () -> st.config.priceGuide = !st.config.priceGuide);
        y += 16;
        buttons.add(new Btn(x0, y, CW, 14, "Open sales file (spreadsheet)", Colors.BTN, true,
                () -> Stream.openFolder(Sales.file().getParent())));
        return y + 18;
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    private void row(String label, int y) {
        settingRows.add(new Row(label, y));
    }

    // the one enabled button on the settings row under the mouse, or null (no row there, or it has several buttons)
    private Btn rowButton(double mx, double my) {
        if (mx < x0 || mx >= x0 + CW) return null;
        for (Row r : settingRows) {
            if (my < r.y() - 1 || my >= r.y() + 14) continue;
            Btn only = null;
            int n = 0;
            for (Btn b : buttons) {
                if (b.y() == r.y()) {
                    n++;
                    only = b;
                }
            }
            return n == 1 && only.enabled() ? only : null;
        }
        return null;
    }

    private void setting(int x, int y, String value, boolean enabled, Runnable action) {
        buttons.add(new Btn(x, y, 80, 13, value, Colors.BTN, enabled, action));
    }

    private static int cycle(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    // ---- actions

    private void message(String text, int color) {
        setupMsg = text;
        setupMsgColor = color;
    }

    private void bumpAmount(int delta) {
        amountBox.setValue(String.valueOf(Math.max(1, Math.min(9999, st.config.amount + delta))));
    }

    private void clearSetup() {
        if (Edition.PRO && st.config.gwTab) {
            gwEntryBox.setValue("");
            gwPrizeBox.setValue("");
            setupMsg = "";
            return;
        }
        if (st.config.ftpTab) {
            ftpPayBox.setValue("");
            ftpPrizeBox.setValue("");
            setupMsg = "";
            return;
        }
        if (st.config.custom) {
            nameBox.setValue("");
            clearLot();
            st.config.customIcon = "";
        } else {
            selected = null;
            amountBox.setValue("1");
        }
        worthBox.setValue("");
        search.setValue("");
        if (Edition.PRO) {
            reserveBox.setValue("");
            buyNowBox.setValue("");
            winnersBox.setValue("1");
        }
        setupMsg = "";
    }

    private void startAuction() {
        String err = Edition.PRO && st.config.gwTab ? st.startGiveaway() : st.config.ftpTab ? st.startFtp() : st.start(selected);
        if (err != null) {
            message(err, Colors.RED);
            return;
        }
        setupMsg = "";
        st.liveMsg = "";
        setupOpen = false;
        bidName.setValue("");
        bidAmount.setValue("");
        setFocused(null);
    }

    private void queueLot() {
        String err = st.enqueue(selected);
        if (err != null) {
            message(err, Colors.RED);
            return;
        }
        int n = st.config.queue.size();
        message("Queued #" + n + ": " + st.config.queue.get(n - 1).label(), Colors.GREEN);
    }

    private void copyRefund(Refund r) {
        String cmd = "pay " + r.name().trim().split("\\s+")[0] + " " + r.amount();
        minecraft.keyboardHandler.setClipboard(cmd);
        st.toast("Copied \"" + cmd + "\" - press / then paste");
    }

    private void runAgain() {
        if (st.runAgain() == null) {
            st.liveMsg = "";
            bidName.setValue("");
            bidAmount.setValue("");
        }
    }

    private void placeBid() {
        if (!st.live) {
            return;
        }
        AuctionState.Amount parsed = AuctionState.parseAmount(bidAmount.getValue());
        if (!parsed.hasNumber()) {
            st.liveMsg = "Type a bid amount.";
            st.liveMsgColor = Colors.RED;
            return;
        }
        boolean[] ok = new boolean[1];
        st.liveMsg = st.placeBid(bidName.getValue(), parsed.value(), ok);
        st.liveMsgColor = ok[0] ? Colors.GREEN : Colors.RED;
        if (ok[0]) {
            bidName.setValue("");
            bidAmount.setValue("");
            setFocused(bidName);
        }
    }

    private void pick(Lots.Entry e) {
        if (!st.config.custom) {
            selected = e;
        } else if (pickLogo) {
            st.config.customIcon = e.id();
            pickLogo = false;
        } else {
            addToLot(e.id());
        }
        setupMsg = "";
    }

    private void addToLot(String id) {
        List<AuctionState.LotItem> items = lotItems();
        int at = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                at = i;
            }
        }
        if (at >= 0) {
            items.get(at).count = Math.min(99999, items.get(at).count + 1);
        } else {
            items.add(new AuctionState.LotItem(id, 1));
            at = items.size() - 1;
        }
        if (st.config.customIcon.isEmpty()) {
            st.config.customIcon = id;
        }
        listScroll = Math.max(0, Math.min(at, items.size() - LIST_ROWS));
        syncCounts();
    }

    private void removeFromLot(int idx) {
        if (idx < lotItems().size()) {
            lotItems().remove(idx);
            listScroll = Math.max(0, Math.min(listScroll, lotItems().size() - LIST_ROWS));
            syncCounts();
        }
    }

    private void clearLot() {
        lotItems().clear();
        listScroll = 0;
        syncCounts();
    }

    private void setCount(int row, int value) {
        int idx = listScroll + row;
        if (value > 0 && idx < lotItems().size()) {
            lotItems().get(idx).count = value;
        }
    }

    private void syncCounts() {
        if (countBoxes[0] == null) {
            return;
        }
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = listScroll + i;
            countBoxes[i].setValue(idx < lotItems().size() ? String.valueOf(lotItems().get(idx).count) : "");
        }
    }

    private void savePreset() {
        String name = st.config.customName.trim();
        if (name.isEmpty()) {
            message("Give the lot a name before saving it.", Colors.RED);
            return;
        }
        boolean replaced = st.findPreset(name) != null;
        st.savePreset();
        message((replaced ? "Updated preset \"" : "Saved preset \"") + name + "\" - pick it any time from Presets", Colors.GREEN);
    }

    // the saved presets, to pick one (KitsScreen); Esc there comes back here
    private void openKits() {
        AuctionState.click();
        minecraft.gui.setScreen(new KitsScreen());
    }

    static void openSetup() {
        setupOpen = true;
        listScroll = 0;
    }

    private void loadNextPreset() {
        List<AuctionState.Preset> list = st.config.presets;
        if (list.isEmpty()) {
            return;
        }
        AuctionState.Preset current = st.findPreset(st.config.customName);
        int i = current == null ? 0 : (list.indexOf(current) + 1) % list.size();
        AuctionState.Preset p = list.get(i);
        st.loadPreset(p);
        nameBox.setValue(p.name);
        listScroll = 0;
        syncCounts();
        message("Loaded \"" + p.name + "\" (" + (i + 1) + "/" + list.size() + ")", Colors.GREEN);
    }

    private void deletePreset(AuctionState.Preset p) {
        if (p != null) {
            st.deletePreset(p);
            message("Deleted saved lot \"" + p.name + "\"", Colors.YELLOW);
        }
    }

    // ---- render

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // no dim / blur, the game stays visible behind the panel
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
        layout();
        int mouseX = (int) (realMouseX / scale);
        int mouseY = (int) (realMouseY / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int alpha = st.config.opacity * 255 / 100;
        Style.panel(g, px, py, W, ph, alpha);

        String toast = st.sizeToast();
        String hint = toast != null ? Txt.cut(font, toast, CW - 70) : st.config.panelHint ? AuctionState.keyName(st.config.toggleKey) + ": hide" : "";
        int hintX = px + W - PAD - 44 - Txt.width(font, hint);
        Txt.draw(g, font, Txt.cut(font, Style.title(), hintX - x0 - 6), x0, py + 6, Colors.TITLE, Style.shadow);
        Txt.draw(g, font, hint, hintX, py + 6, toast != null ? Colors.YELLOW : Colors.MUTED, Style.shadow);
        Style.divider(g, x0, py + 19, CW, alpha);

        refundRows.clear();
        if (settingsOpen) {
            renderSettings(g);
        } else if (liveView()) {
            if (st.gw) {
                renderGwLive(g, alpha);
            } else if (st.ftp) {
                renderFtpLive(g, alpha);
            } else if (st.dutch) {
                renderDutchLive(g, alpha);
            } else {
                renderLive(g);
            }
        } else if (Edition.PRO && st.config.gwTab) {
            renderGwSetup(g);
        } else if (st.config.ftpTab) {
            renderFtpSetup(g);
        } else {
            renderSetup(g, mouseX, mouseY, alpha);
        }
        float tabScale = tabTextScale();
        for (Btn b : buttons) {
            int[] tr = tabRects.get(b.label());
            Style.labelScale = tr != null && tr[0] == b.x() && tr[1] == b.y() ? tabScale // every tab the same size
                    : b.y() == py + 22 && setupTabLabels.contains(b.label()) && !settingsOpen ? setupTabScale : 0;
            Style.button(g, font, b.x(), b.y(), b.w(), b.h(), b.label(), b.color(), b.enabled(),
                    b.enabled() && b.contains(mouseX, mouseY));
        }
        Style.labelScale = 0;

        Txt.draw(g, font, "Opacity", x0, footerY, Colors.MUTED, Style.shadow);
        Style.slider(g, sliderX, sliderY, (st.config.opacity - 35) / 65.0F);
        String update = Online.update();
        String site = update != null ? "Update " + update + " out!" : st.config.footerLink ? "discord.gg/B64z86e9ZY" : "";
        // right of the opacity slider; shrinks (in fixed steps) when the panel is too narrow, so it never runs into it
        int room = x0 + CW - (sliderX + 67), siteW = Txt.width(font, site); // slider + knob end at sliderX + 63
        float ss = siteW <= room ? 1 : Math.max(0.6F, (float) Math.floor(room / (float) siteW * 20) / 20); // 5% steps
        g.pose().pushMatrix();
        // centred in the gap between the end of the slider and the panel's right edge (but never past the padding)
        int gapL = sliderX + 64, gapR = x0 + CW + PAD;
        float sx = Math.min(gapL + (gapR - gapL - siteW * ss) / 2F, x0 + CW - siteW * ss);
        g.pose().translate(Math.round(sx), footerY + (1 - ss) * 4);
        g.pose().scale(ss, ss);
        Txt.draw(g, font, site, 0, 0, update != null && blinkOn() ? Colors.YELLOW : Colors.ACCENT_B, Style.shadow);
        g.pose().popMatrix();

        if (Style.modern()) {
            for (Map.Entry<EditBox, int[]> e : frames.entrySet()) {
                if (e.getKey().isVisible()) {
                    int[] f = e.getValue();
                    Style.field(g, f[0], f[1], f[2], f[3], e.getKey().isFocused());
                }
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        Tour.renderPanel(g, font, tourTarget(), px, py, W, ph, vw, vh, mouseX, mouseY);
        g.pose().popMatrix();
    }

    private void renderSettings(GuiGraphicsExtractor g) {
        for (Row r : settingRows) {
            // room up to the first button on that row; a label that's too long is drawn a little smaller, then cut
            int room = CW;
            for (Btn b : buttons) {
                if (b.y() == r.y() && b.x() > x0 + 20) room = Math.min(room, b.x() - x0 - 4);
            }
            int w = Txt.width(font, r.label());
            if (w <= room) {
                Txt.draw(g, font, r.label(), x0, r.y() + 3, Colors.TEXT, Style.shadow);
            } else {
                float k = Math.max(0.75F, room / (float) w);
                g.pose().pushMatrix();
                g.pose().translate(x0, r.y() + 3 + (1 - k) * 4);
                g.pose().scale(k, k);
                Txt.draw(g, font, Txt.cut(font, r.label(), (int) (room / k)), 0, 0, Colors.TEXT, Style.shadow);
                g.pose().popMatrix();
            }
        }
        if (tab().equals("Sales")) {
            renderSales(g);
        }
        if (sizeRowY >= 0) {
            Txt.center(g, font, Math.round(st.scale() * 100) + "%", x0 + CW - 40, sizeRowY + 3, Colors.TEXT);
        }
        boolean custom = Math.floorMod(st.config.accent, Colors.ACCENT_NAMES.length) == Colors.CUSTOM_ACCENT && Style.modern();
        if (hueAY >= 0) {
            Style.hueSlider(g, hueX, hueAY, st.config.hueA, custom);
            Style.hueSlider(g, hueX, hueBY, st.config.hueB, custom);
        }
        if (previewY >= 0) {
            AuctionHud.worthPreview(g, font, st, x0, previewY, CW);
        }
    }

    private void renderSales(GuiGraphicsExtractor g) {
        java.time.LocalDate today = java.time.LocalDate.now();
        Sales.Totals day = Sales.since(today), week = Sales.since(today.minusDays(6)), all = Sales.since(null);
        Sales.Sale best = Sales.best();
        Sales.Buyer buyer = Sales.topBuyer();
        String[][] lines = {
                {"Today", day.lots() + " sold - " + Sales.money(day.amount(), day.prefix(), day.unit())},
                {"Last 7 days", week.lots() + " sold - " + Sales.money(week.amount(), week.prefix(), week.unit())},
                {"All time", all.lots() + " sold - " + Sales.money(all.amount(), all.prefix(), all.unit())},
                {"Best sale", best == null ? "-" : Sales.money(best.amount(), best.prefix(), best.unit()) + " " + best.item()},
                {"Top buyer", buyer == null ? "-" : buyer.name() + " (" + buyer.lots() + ", " + Sales.money(buyer.total(), all.prefix(), all.unit()) + ")"},
                {"Saved to", "auction_overlay_sales.csv"},
        };
        for (int i = 0; i < lines.length; i++) {
            int y = salesY + i * 13 + 2;
            Txt.draw(g, font, lines[i][0], x0, y, Colors.MUTED, Style.shadow);
            String v = Txt.cut(font, lines[i][1], CW - 62);
            Txt.draw(g, font, v, x0 + CW - Txt.width(font, v), y, i < 3 ? Colors.GREEN : Colors.TEXT, Style.shadow);
        }
    }

    private void renderSetup(GuiGraphicsExtractor g, int mouseX, int mouseY, int alpha) {
        boolean custom = st.config.custom;

        int start = gridRow * COLS;
        Lots.Entry hovered = null;
        for (int i = 0; i < rows * COLS; i++) {
            int idx = start + i;
            int sx = x0 + (i % COLS) * SLOT;
            int sy = gridY + (i / COLS) * SLOT;
            Lots.Entry e = idx < filtered.size() ? filtered.get(idx) : null;
            boolean over = e != null && mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
            Style.slot(g, sx, sy, SLOT, alpha, e != null && !custom && e == selected, over);
            if (e != null) {
                Lots.draw(g, e.id(), sx + 2, sy + 2);
                if (over) {
                    hovered = e;
                }
            }
        }
        if (filtered.isEmpty()) {
            Txt.center(g, font, "No items match", x0 + COLS * SLOT / 2, gridY + rows * SLOT / 2 - 4, Colors.MUTED);
        }
        int barH = rows * SLOT;
        int thumb = Math.max(10, barH * rows / Math.max(1, maxRow() + rows));
        int top = maxRow() == 0 ? 0 : (barH - thumb) * gridRow / maxRow();
        Style.scrollbar(g, x0 + COLS * SLOT + 3, gridY, barH, top, thumb);

        int inLot = lotItems().size();
        String under = hovered != null ? hovered.name()
                : !custom ? filtered.size() + " items  (scroll for more)"
                : pickLogo ? "Click an item to use it as the logo"
                : inLot + (inLot == 1 ? " item" : " items") + " in this lot" + (inLot > LIST_ROWS ? " - scroll the list" : "");
        Txt.draw(g, font, Txt.cut(font, under, CW), x0, hoverY, Colors.AQUA, Style.shadow);

        if (custom) {
            renderCustom(g, alpha);
        } else {
            Style.slot(g, x0, selY, SLOT, alpha, Style.modern() && selected != null, false);
            Sales.Sale last = Edition.PRO && st.config.priceGuide && selected != null ? Sales.last(selected.id(), null) : null;
            if (selected != null && last != null) {
                Lots.draw(g, selected.id(), x0 + 2, selY + 2);
                Txt.draw(g, font, Txt.cut(font, selected.name(), CW - 28), x0 + 28, selY + 1, Colors.ITEM_NAME, Style.shadow);
                String count = last.item().matches(".* x\\d+$") ? last.item().substring(last.item().lastIndexOf(' ') + 1) + ", " : "";
                String guide = "Last sold " + Sales.money(last.amount(), last.prefix(), last.unit()) + " (" + count
                        + java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).format(last.when()) + ")";
                Txt.draw(g, font, Txt.cut(font, guide, CW - 28), x0 + 28, selY + 11, Colors.GOLD, Style.shadow);
            } else if (selected != null) {
                Lots.draw(g, selected.id(), x0 + 2, selY + 2);
                Txt.draw(g, font, Txt.cut(font, selected.name(), CW - 28), x0 + 28, selY + 6, Colors.ITEM_NAME, Style.shadow);
            } else {
                Txt.draw(g, font, "No item selected", x0 + 28, selY + 6, Colors.MUTED, Style.shadow);
            }
            Txt.draw(g, font, "Amount", x0, amountY + 3, Colors.MUTED, Style.shadow);
        }

        Txt.draw(g, font, "Worth", x0, worthY + 3, Colors.MUTED, Style.shadow);
        if (!Edition.PRO) {
            Txt.draw(g, font, "optional", x0 + 130, worthY + 3, Colors.MUTED, Style.shadow);
        }
        boolean dutch = Edition.PRO && st.config.dutch;
        if (dutch) {
            Txt.draw(g, font, "Start", x0, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "Drops by", x0 + 64, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "Every s", x0 + 124, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "Lowest", x0 + 160, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "The price drops until someone pays it.", x0, checkY + 1, Colors.TEXT, Style.shadow);
            Txt.draw(g, font, "First payment at the price wins.", x0, payY + 1, Colors.MUTED, Style.shadow);
        } else {
            Txt.draw(g, font, "Time (m:s)", x0, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "Min pay", x0 + 68, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "Raise", x0 + 160, durLabelY, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, ":", x0 + 29, durY + 3, Colors.TEXT, Style.shadow);
            if (Edition.PRO) {
                Txt.draw(g, font, "Reserve", x0, proLabelY, Colors.MUTED, Style.shadow);
                Txt.draw(g, font, "Buy now", x0 + 68, proLabelY, Colors.MUTED, Style.shadow);
                Txt.draw(g, font, "Winners", x0 + 136, proLabelY, Colors.MUTED, Style.shadow);
            }
            Style.check(g, font, x0, checkY, st.config.antiSnipe, "Late bids reset timer to " + st.config.antiSnipeSecs + "s");
            Style.check(g, font, x0, payY, st.config.watchPayments, "Count chat payments as bids");
        }
        String msg = setupMsg;
        int msgColor = setupMsgColor;
        if (msg.isEmpty() && Edition.PRO && !st.config.queue.isEmpty()) {
            int n = st.config.queue.size();
            msg = n + (n == 1 ? " lot" : " lots") + " queued - next: " + st.config.queue.get(0).label();
            msgColor = Colors.AQUA;
        } else if (msg.isEmpty() && custom && Edition.PRO && st.config.priceGuide) {
            Sales.Sale last = Sales.last(st.config.customIcon, st.config.customName);
            if (last != null && !st.config.customName.isBlank()) {
                msg = "Last sold for " + Sales.money(last.amount(), last.prefix(), last.unit()) + " to " + last.winner();
                msgColor = Colors.GOLD;
            }
        }
        if (!msg.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, msg, CW), x0, msgY, msgColor, Style.shadow);
        }
    }

    private void renderFtpSetup(GuiGraphicsExtractor g) {
        Txt.draw(g, font, "They pay", x0, ftpY + 3, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "Prize", x0, ftpY + 21, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "Countdown", x0, ftpY + 39, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, ":", x0 + 91, ftpY + 39, Colors.TEXT, Style.shadow);
        Style.check(g, font, x0, checkY, st.config.ftpCopyPay, "Copy the pay command for the winner");
        AuctionState.Amount need = AuctionState.parseAmount(st.config.ftpPayText == null ? "" : st.config.ftpPayText);
        AuctionState.Amount prize = AuctionState.parseAmount(st.config.ftpPrizeText == null ? "" : st.config.ftpPrizeText);
        String sum = need.hasNumber() && prize.hasNumber()
                ? "\"First to pay " + (st.config.ftpExact ? "exactly " : "") + st.config.ftpPayText.trim() + " gets " + st.config.ftpPrizeText.trim() + "\""
                : "Type what they pay and what they win";
        Txt.draw(g, font, Txt.cut(font, sum, CW), x0, ftpY + 70, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, "Paying before GO doesn't count.", CW), x0, ftpY + 81, Colors.MUTED, Style.shadow);
        if (!setupMsg.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, setupMsg, CW), x0, msgY, setupMsgColor, Style.shadow);
        }
    }

    private void renderGwSetup(GuiGraphicsExtractor g) {
        Txt.draw(g, font, "Entry", x0, ftpY + 3, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "Prize", x0, ftpY + 21, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "Open for", x0, ftpY + 39, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, ":", x0 + 91, ftpY + 39, Colors.TEXT, Style.shadow);
        AuctionState.Amount entry = AuctionState.parseAmount(st.config.gwEntryText == null ? "" : st.config.gwEntryText);
        AuctionState.Amount prize = AuctionState.parseAmount(st.config.gwPrizeText == null ? "" : st.config.gwPrizeText);
        String sum = entry.hasNumber() && prize.hasNumber()
                ? "\"Pay " + st.config.gwEntryText.trim() + " to enter - one winner gets " + st.config.gwPrizeText.trim() + "\""
                : "Type the entry and the prize";
        Txt.draw(g, font, Txt.cut(font, sum, CW), x0, ftpY + 59, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, st.config.gwMulti ? "Stack: paying 2x the entry = 2 entries" : "Once: one entry per player, extra pays = refunds", CW),
                x0, ftpY + 70, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, Txt.cut(font, "A random entry wins; their pay is copied for you.", CW), x0, ftpY + 81, Colors.MUTED, Style.shadow);
        if (!setupMsg.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, setupMsg, CW), x0, msgY, setupMsgColor, Style.shadow);
        }
    }

    private void renderGwLive(GuiGraphicsExtractor g, int alpha) {
        String prize = "win " + st.ftpPrize;
        Txt.draw(g, font, "GIVEAWAY", x0, py + 24, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, prize, x0 + CW - Txt.width(font, prize), py + 24, Colors.GREEN, Style.shadow);
        if (st.live) {
            int secs = st.remainingSeconds();
            int color = secs > st.config.warnSecs ? Colors.TEXT : blinkOn() ? Colors.RED : Colors.YELLOW;
            Txt.draw(g, font, "DRAW IN", x0, py + 38, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "ENTRIES", x0 + 100, py + 38, Colors.MUTED, Style.shadow);
            AuctionHud.big(g, font, AuctionState.formatTime(secs), x0, py + 49, color, 96);
            AuctionHud.big(g, font, st.gwEntries() + (st.gwPlayers() != st.gwEntries() ? " (" + st.gwPlayers() + ")" : ""), x0 + 100, py + 49, Colors.GOLD, CW - 100);
        } else if (st.winnerName() != null) {
            Txt.draw(g, font, "WINNER", x0, py + 38, Colors.MUTED, Style.shadow);
            AuctionHud.big(g, font, st.leader.name(), x0, py + 49, Colors.GOLD, CW);
        } else {
            AuctionHud.big(g, font, st.result, x0, py + 45, st.resultColor, CW);
        }
        Txt.draw(g, font, Txt.cut(font, st.gwRule(), CW), x0, py + 74, Colors.MUTED, Style.shadow);
        if (st.live && !st.liveMsg.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, st.liveMsg, CW), x0, py + 86, st.liveMsgColor, Style.shadow);
        } else if (!st.live && st.copiedTpa != null) {
            Txt.draw(g, font, Txt.cut(font, "Copied \"" + st.copiedTpa + "\" - press / then paste", CW), x0, py + 86, Colors.GOLD, Style.shadow);
        }
        payList(g, py + 98, st.live ? "Payments show up here - entries in green" : "Red ones need a refund (click to copy)");
    }

    private void renderFtpLive(GuiGraphicsExtractor g, int alpha) {
        String prize = "win " + st.ftpPrize;
        Txt.draw(g, font, "FIRST TO PAY", x0, py + 24, Colors.GOLD, Style.shadow);
        Txt.draw(g, font, prize, x0 + CW - Txt.width(font, prize), py + 24, Colors.GREEN, Style.shadow);

        String need = st.money(st.ftpNeed);
        if (st.live && !st.ftpGo) {
            int secs = st.remainingSeconds();
            int color = secs > st.config.warnSecs ? Colors.TEXT : blinkOn() ? Colors.RED : Colors.YELLOW;
            Txt.draw(g, font, "STARTS IN", x0, py + 38, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, st.ftpExact ? "PAY EXACTLY" : "PAY", x0 + 100, py + 38, Colors.MUTED, Style.shadow);
            AuctionHud.big(g, font, AuctionState.formatTime(secs), x0, py + 49, color, 96);
            AuctionHud.big(g, font, need, x0 + 100, py + 49, Colors.GOLD, CW - 100);
        } else if (st.live) {
            Style.highlight(g, x0, py + 36, CW, 30, alpha);
            AuctionHud.big(g, font, "GO! PAY NOW", x0 + 6, py + 43, blinkOn() ? Colors.GREEN : Colors.YELLOW, CW - 12);
        } else if (st.winnerName() != null) {
            Txt.draw(g, font, "WINNER", x0, py + 38, Colors.MUTED, Style.shadow);
            AuctionHud.big(g, font, st.leader.name(), x0, py + 49, Colors.GOLD, CW);
        } else {
            AuctionHud.big(g, font, st.result, x0, py + 45, st.resultColor, CW);
        }
        Txt.draw(g, font, Txt.cut(font, st.ftpRule(), CW), x0, py + 74, Colors.MUTED, Style.shadow);
        if (st.live && !st.liveMsg.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, st.liveMsg, CW), x0, py + 86, st.liveMsgColor, Style.shadow);
        } else if (!st.live && st.copiedTpa != null) {
            Txt.draw(g, font, Txt.cut(font, "Copied \"" + st.copiedTpa + "\" - press / then paste", CW), x0, py + 86, Colors.GOLD, Style.shadow);
        }

        payList(g, py + 98, st.ftpGo ? "Waiting for the first payment..." : "Early payers show up here (refund them)");
    }

    private void renderCustom(GuiGraphicsExtractor g, int alpha) {
        Txt.draw(g, font, "Name", x0, nameY + 3, Colors.MUTED, Style.shadow);

        boolean hasLogo = Lots.get(st.config.customIcon) != null;
        Style.slot(g, x0, logoY, SLOT, alpha, pickLogo, false);
        if (hasLogo) {
            Lots.draw(g, st.config.customIcon, x0 + 2, logoY + 2);
        } else {
            Txt.center(g, font, "?", x0 + SLOT / 2, logoY + 6, Colors.MUTED);
        }

        List<AuctionState.LotItem> items = lotItems();
        Style.box(g, x0, listY, CW, LIST_ROWS * LIST_ROW_H + 3);
        if (items.isEmpty()) {
            Txt.draw(g, font, "Empty - click items above to add them", x0 + 5, listY + 5, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, "or just name it and pick a logo", x0 + 5, listY + 17, Colors.MUTED, Style.shadow);
        }
        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = listScroll + i;
            if (idx >= items.size()) {
                break;
            }
            int ry = listY + 2 + i * LIST_ROW_H;
            Txt.draw(g, font, "x", x0 + 34, ry + 3, Colors.MUTED, Style.shadow);
            Txt.draw(g, font, Txt.cut(font, Lots.name(items.get(idx).id), CW - 62), x0 + 42, ry + 3, Colors.TEXT, Style.shadow);
        }
    }

    private void renderLive(GuiGraphicsExtractor g) {
        int boxY = py + 24;
        Style.itemFrame(g, x0, boxY, 36);
        g.pose().pushMatrix();
        g.pose().translate(x0 + 2, boxY + 2);
        g.pose().scale(2.0F, 2.0F);
        Lots.draw(g, st.icon, 0, 0);
        g.pose().popMatrix();

        int tx = x0 + 42;
        int tw = CW - 42;
        Txt.draw(g, font, Txt.cut(font, st.displayName(), tw), tx, boxY + 2, Colors.ITEM_NAME, Style.shadow);
        int worthStyle = st.worthStyle();
        if (st.live) {
            String liveText = (blinkOn() ? "●" : "○") + " LIVE";
            Txt.draw(g, font, liveText, tx, boxY + 13, Colors.RED, Style.shadow);
            int after = tx + Txt.width(font, liveText);
            if (worthStyle == 1) {
                AuctionHud.worthBadge(g, font, st, after + 4, boxY + 12, tx + tw - after - 4);
            } else if (worthStyle == 2) {
                Txt.draw(g, font, Txt.cut(font, "  " + st.worthLabel().toLowerCase() + " " + st.worth(), tw - Txt.width(font, liveText)),
                        after, boxY + 13, st.worthColor(), Style.shadow);
            } else if (st.watchPayments && st.config.watchingText) {
                Txt.draw(g, font, "  watching chat", after, boxY + 13, Colors.MUTED, Style.shadow);
            }
        } else {
            Txt.draw(g, font, Txt.cut(font, st.result, tw), tx, boxY + 13, st.resultColor, Style.shadow);
        }
        String rules = "Min " + st.moneyExact(st.minPay) + " | Raise " + AuctionState.full(st.raiseBy);
        if (st.dutch) {
            rules = "Lowest " + st.money(st.dutchFloor) + " | -" + st.money(st.dutchDrop) + " / " + st.dutchEveryMs / 1000 + "s";
        }
        for (String extra : new String[]{
                st.winnersN > 1 ? st.winnersN + " winners" : null,
                st.buyNow > 0 ? "Buy now " + st.money(st.buyNow) : null,
                st.reserve > 0 ? (st.topBid() != null && st.topBid().amount() >= st.reserve ? "Reserve met" : "Reserve " + st.money(st.reserve)) : null,
                st.antiSnipe && !st.dutch ? "Anti-snipe" : null}) {
            if (extra != null && Txt.width(font, rules + " | " + extra) <= tw) {
                rules += " | " + extra;
            }
        }
        if (st.config.liveRules) {
            Txt.draw(g, font, Txt.cut(font, rules, tw), tx, boxY + 24, Colors.MUTED, Style.shadow);
        }
        int cursor = py + 62;
        if (panelContents()) {
            Txt.draw(g, font, Txt.cut(font, "Includes: " + st.contents(), CW), x0, cursor + 1, Colors.AQUA, Style.shadow);
            cursor += 11;
        }
        if (worthStyle == 0) {
            AuctionHud.worthBanner(g, font, st.worthLabel(), st.worth(), st.worthColor(), x0, cursor, CW, st.config.opacity * 255 / 100);
        }

        int y = py + liveShift;
        int colB = x0 + 100;
        Txt.draw(g, font, "TIME LEFT", x0, y + 66, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "TOP BID", colB, y + 66, Colors.MUTED, Style.shadow);

        int secs = st.live ? st.remainingSeconds() : 0;
        int timerColor = !st.live ? Colors.MUTED : secs > st.config.warnSecs ? Colors.TEXT : blinkOn() ? Colors.RED : Colors.YELLOW;
        AuctionHud.big(g, font, AuctionState.formatTime(secs), x0, y + 77, timerColor, Integer.MAX_VALUE);
        AuctionState.Bid top = st.topBid();
        String topText = top == null ? "-" : st.moneyNumber(top.amount());
        int topW = AuctionHud.big(g, font, topText, colB, y + 77, top == null ? Colors.MUTED : Colors.GREEN, CW - 100);
        int unitX = colB + topW + 3;
        if (top != null && !st.payUnit.isEmpty() && unitX < x0 + CW - 10) {
            Txt.draw(g, font, Txt.cut(font, st.payUnit, x0 + CW - unitX), unitX, y + 86, Colors.GREEN, Style.shadow);
        }
        Txt.draw(g, font, Txt.cut(font, top == null ? "no bids yet" : "by " + top.name(), CW - 100), colB, y + 97, Colors.AQUA, Style.shadow);
        if (st.live) {
            Txt.draw(g, font, AuctionHud.nextText(font, st, 96), x0, y + 97, Colors.MUTED, Style.shadow);
        }

        if (!st.liveMsg.isEmpty() && st.live) {
            Txt.draw(g, font, Txt.cut(font, st.liveMsg, CW), x0, y + 128, st.liveMsgColor, Style.shadow);
        } else if (!st.live && st.copiedTpa != null) {
            Txt.draw(g, font, Txt.cut(font, "Copied \"" + st.copiedTpa + "\" - press / then paste", CW), x0, y + 128, Colors.GOLD, Style.shadow);
        }

        if (queueLineY >= 0 && Edition.PRO) {
            int n = st.config.queue.size();
            int wait = st.queueCountdown();
            String q = n == 0 ? "" : wait >= 0 ? "Next: " + st.config.queue.get(0).label() + " in " + wait + "s"
                    : "Queue paused - " + n + (n == 1 ? " lot" : " lots") + " waiting";
            if (!q.isEmpty()) {
                Txt.draw(g, font, Txt.cut(font, q, CW), x0, queueLineY, Colors.AQUA, Style.shadow);
            }
        }

        int hy = y + 139;
        int historyRows = historyRows();
        Style.box(g, x0, hy, CW, historyRows * 10 + 4);
        List<AuctionState.Bid> refunds = Edition.PRO ? st.refunds() : List.of();
        if (!st.live && !refunds.isEmpty()) {
            // after the auction: what has to be paid back, click a row to copy the pay command
            for (int i = 0; i < historyRows && i < refunds.size(); i++) {
                AuctionState.Bid bid = refunds.get(refunds.size() - 1 - i);
                int ry = hy + 3 + i * 10;
                String amt = st.money(bid.amount());
                String tag = bid.blocked() ? "blocked" : "refund";
                Txt.draw(g, font, amt, x0 + CW - 5 - Txt.width(font, amt), ry, Colors.RED, Style.shadow);
                String who = Txt.cut(font, bid.name(), CW - 50 - Txt.width(font, amt));
                Txt.draw(g, font, who, x0 + 5, ry, Colors.TEXT, Style.shadow);
                Txt.draw(g, font, tag, x0 + 9 + Txt.width(font, who), ry, Colors.GOLD, Style.shadow);
                refundRows.add(new Refund(x0, ry - 1, CW, 10, bid.name(), bid.amount()));
            }
            if (st.copiedTpa == null || st.config.historyRows < 2) {
                Txt.draw(g, font, Txt.cut(font, refunds.size() + " to refund - click one to copy its pay", CW), x0, y + 128, Colors.GOLD, Style.shadow);
            }
            return;
        }
        List<AuctionState.Bid> winning = st.winnersN > 1 ? (st.live ? st.winningBids() : st.soldTo()) : List.of();
        for (int i = 0; i < historyRows && i < st.bids.size(); i++) {
            AuctionState.Bid bid = st.bids.get(st.bids.size() - 1 - i);
            boolean in = false;
            for (AuctionState.Bid w : winning) {
                in |= w == bid;
            }
            int color = bid.blocked() ? Colors.RED : st.winnersN > 1 ? (in ? Colors.GREEN : Colors.MUTED) : i == 0 ? Colors.GREEN : Colors.MUTED;
            String amt = st.money(bid.amount());
            Txt.draw(g, font, amt, x0 + CW - 5 - Txt.width(font, amt), hy + 3 + i * 10, color, Style.shadow);
            String who = Txt.cut(font, bid.name(), CW - 40 - Txt.width(font, amt));
            Txt.draw(g, font, who, x0 + 5, hy + 3 + i * 10, color, Style.shadow);
            if (st.testTagged(bid)) {
                Txt.draw(g, font, "TEST", x0 + 9 + Txt.width(font, who), hy + 3 + i * 10, Colors.AQUA, Style.shadow);
            } else if (bid.blocked()) {
                Txt.draw(g, font, "blocked", x0 + 9 + Txt.width(font, who), hy + 3 + i * 10, Colors.RED, Style.shadow);
            } else if (bid.paid()) {
                Txt.draw(g, font, in ? "in" : "paid", x0 + 9 + Txt.width(font, who), hy + 3 + i * 10, Colors.GOLD, Style.shadow);
            }
        }
    }

    private void renderDutchLive(GuiGraphicsExtractor g, int alpha) {
        int boxY = py + 24;
        Style.itemFrame(g, x0, boxY, 36);
        g.pose().pushMatrix();
        g.pose().translate(x0 + 2, boxY + 2);
        g.pose().scale(2.0F, 2.0F);
        Lots.draw(g, st.icon, 0, 0);
        g.pose().popMatrix();
        int tx = x0 + 42, tw = CW - 42;
        Txt.draw(g, font, Txt.cut(font, st.displayName(), tw), tx, boxY + 2, Colors.ITEM_NAME, Style.shadow);
        if (st.live) {
            Txt.draw(g, font, (blinkOn() ? "●" : "○") + " DUTCH AUCTION", tx, boxY + 13, Colors.RED, Style.shadow);
        } else {
            Txt.draw(g, font, Txt.cut(font, st.result, tw), tx, boxY + 13, st.resultColor, Style.shadow);
        }
        String rules = "Lowest " + st.money(st.dutchFloor) + " | -" + st.money(st.dutchDrop) + " every " + st.dutchEveryMs / 1000 + "s";
        if (st.config.liveRules) {
            Txt.draw(g, font, Txt.cut(font, rules, tw), tx, boxY + 24, Colors.MUTED, Style.shadow);
        }
        int y = py + 66;
        if (panelContents()) {
            Txt.draw(g, font, Txt.cut(font, "Includes: " + st.contents(), CW), x0, py + 63, Colors.AQUA, Style.shadow);
            y += 11;
        }
        int colB = x0 + 120;
        Txt.draw(g, font, st.live ? "PRICE NOW" : st.winnerName() != null ? "SOLD FOR" : "ENDED AT", x0, y, Colors.MUTED, Style.shadow);
        Txt.draw(g, font, "NEXT DROP", colB, y, Colors.MUTED, Style.shadow);
        long shown = !st.live && st.leader != null ? st.leader.amount() : st.dutchPrice;
        AuctionHud.big(g, font, st.moneyNumber(shown), x0, y + 11, st.live ? Colors.GREEN : Colors.GOLD, 116);
        int secs = st.live ? st.remainingSeconds() : 0;
        int color = !st.live ? Colors.MUTED : secs > 3 ? Colors.TEXT : blinkOn() ? Colors.RED : Colors.YELLOW;
        AuctionHud.big(g, font, st.live && st.dutchPrice <= st.dutchFloor ? "LAST" : AuctionState.formatTime(secs), colB, y + 11, color, CW - 120);
        if (!st.liveMsg.isEmpty() && st.live) {
            Txt.draw(g, font, Txt.cut(font, st.liveMsg, CW), x0, y + 34, st.liveMsgColor, Style.shadow);
        } else if (!st.live && st.copiedTpa != null) {
            Txt.draw(g, font, Txt.cut(font, "Copied \"" + st.copiedTpa + "\" - press / then paste", CW), x0, y + 34, Colors.GOLD, Style.shadow);
        }
        payList(g, dutchListY, st.live ? "Payments under the price show up here" : "Nobody else paid");
    }

    // first to pay / dutch payments; everything except the winner can be clicked to copy its refund
    private void payList(GuiGraphicsExtractor g, int listY, String empty) {
        int rowsShown = historyRows();
        Style.box(g, x0, listY, CW, rowsShown * 10 + 4);
        if (st.ftpPays.isEmpty()) {
            Txt.draw(g, font, Txt.cut(font, empty, CW - 10), x0 + 5, listY + 3, Colors.MUTED, Style.shadow);
        }
        for (int i = 0; i < rowsShown && i < st.ftpPays.size(); i++) {
            AuctionState.FtpPay pay = st.ftpPays.get(st.ftpPays.size() - 1 - i);
            int ry = listY + 3 + i * 10;
            int color = pay.won() ? Colors.GREEN : Colors.RED;
            String right = st.money(pay.amount()) + "  " + pay.note();
            Txt.draw(g, font, right, x0 + CW - 5 - Txt.width(font, right), ry, color, Style.shadow);
            Txt.draw(g, font, Txt.cut(font, pay.name(), CW - 14 - Txt.width(font, right)), x0 + 5, ry, color, Style.shadow);
            if (!pay.won() && Edition.PRO) {
                refundRows.add(new Refund(x0, ry - 1, CW, 10, pay.name(), pay.amount()));
            }
        }
    }

    private static boolean blinkOn() {
        return AuctionState.blink();
    }

    // the "Includes: ..." line on the panel (Clean tab can hide it)
    private boolean panelContents() {
        return st.showContents() && st.config.panelContents;
    }

    // ---- input

    @Override
    public boolean mouseClicked(MouseButtonEvent realEvent, boolean doubleClick) {
        MouseButtonEvent event = scaled(realEvent);
        double mx = event.x(), my = event.y();
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (Tour.click(mx, my)) {
            return true;
        }
        for (Btn b : buttons) {
            if (b.enabled() && b.contains(mx, my)) {
                AuctionState.click();
                b.action().run();
                return true;
            }
        }
        // clicking a setting's name does what its button does (settings rows with one button)
        if (settingsOpen) {
            Btn only = rowButton(mx, my);
            if (only != null) {
                AuctionState.click();
                only.action().run();
                return true;
            }
        }
        if (Online.update() != null && my >= footerY - 2 && my < footerY + 10 && mx >= x0 + CW - 70 && mx < x0 + CW) {
            AuctionState.click();
            Online.openDownloads();
            return true;
        }
        for (Refund r : refundRows) {
            if (mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h()) {
                AuctionState.click();
                copyRefund(r);
                return true;
            }
        }
        if (mx >= px && mx < px + W && my >= py && my < py + 19) {
            draggingPanel = true;
            dragDX = (int) mx - px;
            dragDY = (int) my - py;
            return true;
        }
        if (my >= sliderY - 2 && my < sliderY + 10 && mx >= sliderX - 3 && mx < sliderX + 63) {
            draggingSlider = true;
            setOpacity(mx);
            return true;
        }
        if (settingsOpen && hueAY >= 0 && mx >= hueX - 3 && mx < hueX + 63) {
            int which = my >= hueAY - 2 && my < hueAY + 10 ? 1 : my >= hueBY - 2 && my < hueBY + 10 ? 2 : 0;
            if (which != 0) {
                draggingHue = which;
                st.config.accent = Colors.CUSTOM_ACCENT; // touching a colour switches to the custom accent
                setHue(mx);
                return true;
            }
        }
        if (!liveView() && !settingsOpen && setupClick(mx, my)) {
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

    private boolean setupClick(double mx, double my) {
        // the giveaway page has no item grid: its boxes (Prize, Open for) sit where the grid would be, so a click there
        // must reach them (it used to be eaten as a grid click - you couldn't type in them)
        if (Edition.PRO && st.config.gwTab) {
            return false;
        }
        if (st.config.ftpTab) {
            if (mx >= x0 && mx < x0 + CW && my >= checkY - 1 && my < checkY + 10) {
                st.config.ftpCopyPay = !st.config.ftpCopyPay;
                AuctionState.click();
                return true;
            }
            return false;
        }
        int barX = x0 + COLS * SLOT + 1;
        if (mx >= barX && mx < barX + 8 && my >= gridY && my < gridY + rows * SLOT) {
            draggingScroll = true;
            scrollTo(my);
            return true;
        }
        if (mx >= x0 && mx < x0 + COLS * SLOT && my >= gridY && my < gridY + rows * SLOT) {
            int idx = (gridRow + (int) (my - gridY) / SLOT) * COLS + (int) (mx - x0) / SLOT;
            if (idx < filtered.size()) {
                pick(filtered.get(idx));
                AuctionState.click();
            }
            setFocused(null);
            return true;
        }
        if (st.config.custom && mx >= x0 && mx < x0 + SLOT && my >= logoY && my < logoY + SLOT) {
            pickLogo = !pickLogo;
            AuctionState.click();
            return true;
        }
        if (Edition.PRO && st.config.dutch) {
            return false;
        }
        if (mx >= x0 && mx < x0 + CW && my >= checkY - 1 && my < checkY + 10) {
            st.config.antiSnipe = !st.config.antiSnipe;
            AuctionState.click();
            return true;
        }
        if (mx >= x0 && mx < x0 + CW && my >= payY - 1 && my < payY + 10) {
            st.config.watchPayments = !st.config.watchPayments;
            AuctionState.click();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent realEvent, double dx, double dy) {
        MouseButtonEvent event = scaled(realEvent);
        if (draggingPanel) {
            st.config.panelX = Math.round(Math.max(0, Math.min(vw - W, (int) event.x() - dragDX)) * scale);
            st.config.panelY = Math.round(Math.max(0, Math.min(vh - ph, (int) event.y() - dragDY)) * scale);
            return true;
        }
        if (draggingSlider) {
            setOpacity(event.x());
            return true;
        }
        if (draggingScroll) {
            scrollTo(event.y());
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
        if (draggingPanel || draggingSlider || draggingHue != 0) {
            st.save();
        }
        draggingPanel = draggingSlider = draggingScroll = false;
        draggingHue = 0;
        return super.mouseReleased(scaled(realEvent));
    }

    @Override
    public boolean mouseScrolled(double realX, double realY, double scrollX, double scrollY) {
        double mx = realX / scale, my = realY / scale;
        int step = scrollY > 0 ? -1 : 1;
        if (!liveView() && !settingsOpen) {
            if (mx >= x0 && mx < x0 + COLS * SLOT + 8 && my >= gridY && my < gridY + rows * SLOT) {
                gridRow = Math.max(0, Math.min(maxRow(), gridRow + step));
                return true;
            }
            if (st.config.custom && mx >= x0 && mx < x0 + CW && my >= listY && my < listY + LIST_ROWS * LIST_ROW_H + 3) {
                listScroll = Math.max(0, Math.min(lotItems().size() - LIST_ROWS, listScroll + step));
                syncCounts();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (rebinding) {
            boolean clear = event.key() == InputConstants.KEY_BACKSPACE && rebindWhich != 0;
            if (!event.isEscape()) {
                int key = clear ? -1 : event.key();
                AuctionState.Config c = st.config;
                switch (rebindWhich) {
                    case 1 -> c.keyExtend = key;
                    case 2 -> c.keySell = key;
                    case 3 -> c.keyCancel = key;
                    case 4 -> c.keyAnnounce = key;
                    default -> c.toggleKey = key;
                }
            }
            rebinding = false;
            st.save();
            return true;
        }
        int key = event.key();
        boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (enter && st.live && liveView() && (getFocused() == bidName || getFocused() == bidAmount)) {
            placeBid();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x / scale, y / scale);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // Ctrl + / - resizes, don't also type the + or -
        var window = minecraft.getWindow();
        if (InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)) {
            return true;
        }
        return super.charTyped(event);
    }

    // clicking in a field's padding should still focus the smaller borderless box inside it
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
            st.config.hueA = hue;
        } else {
            st.config.hueB = hue;
        }
        st.applyTheme();
    }

    private void setOpacity(double mx) {
        st.config.opacity = (int) Math.round(35 + Math.max(0, Math.min(1, (mx - sliderX) / 60.0)) * 65);
    }

    private void scrollTo(double my) {
        double frac = Math.max(0, Math.min(1, (my - gridY) / (rows * SLOT)));
        gridRow = (int) Math.round(frac * maxRow());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        rebinding = false;
        st.save();
        super.removed();
    }
}
