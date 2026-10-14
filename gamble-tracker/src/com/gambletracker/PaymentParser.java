package com.gambletracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Strict on purpose: the whole message has to be the payment line ("Steve paid you $10K.",
// "You received $10,000 from Steve.", "$10,000 has been received from Steve."), so a DM or chat
// line like "[Steve -> You] Bob paid you $1B" can't fake a bet.
public final class PaymentParser {
    private static final String NAME = "(?<name>[.*]?[A-Za-z0-9_]{3,16})"; // Bedrock names start with "."
    private static final String AMOUNT = "\\$\\s*(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kKmMbBtT]?)";
    private static final String END = "\\s*[.!]?";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile(NAME + "\\s+(?:has\\s+)?paid\\s+you\\s+" + AMOUNT + END, FLAGS),
            Pattern.compile("you\\s+(?:have\\s+)?received\\s+" + AMOUNT + "\\s+from\\s+" + NAME + END, FLAGS),
            Pattern.compile(AMOUNT + "\\s+has\\s+been\\s+received\\s+from\\s+" + NAME + END, FLAGS)
    );

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern LOOKS_LIKE_PAYMENT = Pattern.compile("(paid you|received).*\\$|\\$.*(paid you|received)", FLAGS);
    private static final Pattern NUMBER = Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?", FLAGS);
    private static final String[] UNITS = {"K", "M", "B", "T"};

    public record Payment(String name, long amount) {}

    private PaymentParser() {}

    public static String clean(String message) {
        return FORMATTING.matcher(message).replaceAll("").trim();
    }

    public static Payment parse(String message) {
        String text = clean(message);
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.matches()) {
                long amount = parseMoney(m.group("amount"));
                if (amount > 0) {
                    return new Payment(m.group("name"), amount);
                }
            }
        }
        return null;
    }

    // mentions a payment but didn't match exactly - shown on the panel so you can see what got ignored
    public static boolean looksLikePayment(String message) {
        return LOOKS_LIKE_PAYMENT.matcher(clean(message)).find();
    }

    // "10k" / "$1.5M" / "10,000" -> number, blank or junk -> -1
    public static long parseMoney(String text) {
        if (text == null) {
            return -1;
        }
        Matcher m = NUMBER.matcher(text.trim());
        if (!m.matches()) {
            return -1;
        }
        BigDecimal value = new BigDecimal(m.group(1).replace(",", ""));
        int zeros = switch (m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT)) {
            case "k" -> 3;
            case "m" -> 6;
            case "b" -> 9;
            case "t" -> 12;
            default -> 0;
        };
        return value.movePointRight(zeros).setScale(0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Long.MAX_VALUE / 4)).longValue();
    }

    public static String full(long v) {
        return "$" + String.format(Locale.ROOT, "%,d", v);
    }

    // $12,005 under 100k, then $125.5K / $1.25M / $2.5B, rounded down
    public static String money(long v) {
        if (v < 100_000) {
            return full(v);
        }
        double n = v;
        int i = -1;
        while (n >= 1000 && i < UNITS.length - 1) {
            n /= 1000;
            i++;
        }
        double shown = Math.floor(n * 100) / 100;
        String num = shown == Math.floor(shown) ? String.valueOf((long) shown)
                : String.format(Locale.ROOT, "%.2f", shown).replaceAll("0+$", "");
        return "$" + num + UNITS[i];
    }
}
