package com.auctionoverlay;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Premium: every sale goes into config/auction_overlay_sales.csv (opens in Excel / Google Sheets).
// The Sales tab adds it up and the setup screen shows what an item sold for last time.
public final class Sales {
    public record Sale(LocalDateTime when, String item, String icon, String winner, long amount, String prefix, String unit,
                       String mode, int bids) {}

    private static final String HEADER = "date,item,icon,winner,amount,currency,unit,type,bids";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<Sale> SALES = new ArrayList<>();

    private Sales() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("auction_overlay_sales.csv");
    }

    public static List<Sale> all() {
        return SALES;
    }

    public static void load() {
        SALES.clear();
        if (!Edition.PRO) {
            return;
        }
        try {
            if (!Files.exists(file())) {
                return;
            }
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                List<String> f = split(line);
                if (f.size() < 9 || f.get(0).equals("date")) {
                    continue;
                }
                try {
                    SALES.add(new Sale(LocalDateTime.parse(f.get(0), WHEN), f.get(1), f.get(2), f.get(3), Long.parseLong(f.get(4)),
                            f.get(5), f.get(6), f.get(7), Integer.parseInt(f.get(8))));
                } catch (RuntimeException ignored) {
                    // a line someone edited by hand
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static void record(AuctionState st, AuctionState.Bid winner, String mode) {
        if (!Edition.PRO || winner == null || Tour.active() || st.testing || st.isTest(winner)) {
            return; // the tour's test auction isn't a real sale
        }
        Sale s = new Sale(LocalDateTime.now().withNano(0), st.displayName(), st.icon, winner.name(), winner.amount(),
                st.payPrefix, st.payUnit, mode, st.bids.size());
        SALES.add(s);
        try {
            Files.createDirectories(file().getParent());
            boolean fresh = !Files.exists(file());
            String line = (fresh ? HEADER + "\n" : "") + String.join(",", csv(WHEN.format(s.when())), csv(s.item()), csv(s.icon()),
                    csv(s.winner()), String.valueOf(s.amount()), csv(s.prefix()), csv(s.unit()), s.mode(), String.valueOf(s.bids())) + "\n";
            Files.writeString(file(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    // what this item (or named lot) last sold for
    public static Sale last(String icon, String lotName) {
        for (int i = SALES.size() - 1; i >= 0; i--) {
            Sale s = SALES.get(i);
            if (lotName != null && !lotName.isBlank() ? s.item().equalsIgnoreCase(lotName.trim()) : s.icon().equals(icon)) {
                return s;
            }
        }
        return null;
    }

    public record Totals(int lots, long amount, String prefix, String unit) {}

    public static Totals since(LocalDate day) {
        int lots = 0;
        long total = 0;
        String prefix = "", unit = "";
        for (Sale s : SALES) {
            if (day == null || !s.when().toLocalDate().isBefore(day)) {
                lots++;
                total += s.amount();
                prefix = s.prefix();
                unit = s.unit();
            }
        }
        return new Totals(lots, total, prefix, unit);
    }

    public static Sale best() {
        Sale best = null;
        for (Sale s : SALES) {
            if (best == null || s.amount() > best.amount()) best = s;
        }
        return best;
    }

    // name, lots bought, total spent
    public record Buyer(String name, int lots, long total) {}

    public static Buyer topBuyer() {
        Map<String, Buyer> by = new HashMap<>();
        for (Sale s : SALES) {
            String key = s.winner().toLowerCase(Locale.ROOT);
            Buyer b = by.get(key);
            by.put(key, b == null ? new Buyer(s.winner(), 1, s.amount()) : new Buyer(b.name(), b.lots() + 1, b.total() + s.amount()));
        }
        Buyer top = null;
        for (Buyer b : by.values()) {
            if (top == null || b.total() > top.total()) top = b;
        }
        return top;
    }

    public static String money(long v, String prefix, String unit) {
        return prefix + AuctionState.compact(v) + (unit == null || unit.isEmpty() ? "" : " " + unit);
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
