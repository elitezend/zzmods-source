package com.auctionoverlay;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Picks "someone paid you" lines out of server messages. Handles the usual plugin wordings:
//   "$500 has been received from Steve."   "You have received $500 from Steve"
//   "Steve has paid you $500"              "+$500 from Steve"
// The payment has to start the message (after optional [tags]) so "<Bob> Steve paid you $500" in chat doesn't count.
public final class PaymentDetector {
    private static final String NAME = "(?<name>[.*]?[A-Za-z0-9_]{3,16})"; // Bedrock names start with "."
    private static final String AMOUNT = "(?<amount>[$€£¥]?\\s*[0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kKmMbB]?(?![A-Za-z0-9]))";
    private static final String START = "^(?:\\[[^\\]]{0,24}\\]\\s*|[^\\w\\s<.*]{1,3}\\s*)*";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile(START + "(?:you\\s+)?(?:have\\s+)?(?:just\\s+)?received\\s+" + AMOUNT + "(?:\\s+[A-Za-z]+)?\\s+from\\s+" + NAME, FLAGS),
            Pattern.compile(START + AMOUNT + "(?:\\s+[A-Za-z]+)?\\s+(?:has\\s+been|was|were)\\s+(?:received|sent|paid|given)\\s+(?:to\\s+you\\s+)?(?:from|by)\\s+" + NAME, FLAGS),
            Pattern.compile(START + NAME + "\\s+(?:has\\s+)?(?:just\\s+)?(?:paid|sent|given|gave|transferred|payed)\\s+(?:you\\s+)?" + AMOUNT, FLAGS),
            Pattern.compile(START + "\\+\\s*" + AMOUNT + "(?:\\s+[A-Za-z]+)?\\s+from\\s+" + NAME, FLAGS)
    );

    private static final Pattern FORMATTING = Pattern.compile("§.");

    public record Payment(String name, long amount) {}

    private PaymentDetector() {}

    public static Payment parse(String message) {
        return parse(message, false);
    }

    // strict: no [tag] or <name> in front and nothing after the amount but a "." or "!", so a DM like
    // "[Steve -> You] Bob paid you $10m" can't win a first-to-pay game
    public static Payment parse(String message, boolean strict) {
        String text = FORMATTING.matcher(message).replaceAll("").trim();
        Pattern custom = customPattern();
        if (custom != null) {
            // premium: the server's own wording, e.g. "{name} sent you {amount} coins" - the whole message has to match
            Matcher m = custom.matcher(text);
            if (!m.matches()) {
                return null;
            }
            AuctionState.Amount amount = AuctionState.parseAmount(m.group("amount"));
            return amount.hasNumber() && amount.value() > 0 ? new Payment(m.group("name"), amount.value()) : null;
        }
        if (strict && (text.startsWith("[") || text.startsWith("<"))) {
            return null;
        }
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (!m.find()) {
                continue;
            }
            if (strict && (m.start() != 0 || !text.substring(m.end()).matches("\\s*(?:\\w+\\s*)?[.!]?"))) {
                continue;
            }
            String name = m.group("name");
            if (name.replaceFirst("^[.*]", "").toLowerCase(Locale.ROOT).equals("you")) {
                continue;
            }
            AuctionState.Amount amount = AuctionState.parseAmount(m.group("amount"));
            if (amount.hasNumber() && amount.value() > 0) {
                return new Payment(name, amount.value());
            }
        }
        return null;
    }

    private static String cachedFormat;
    private static Pattern cachedPattern;

    // null unless a custom payment message is switched on and has both {name} and {amount}
    public static Pattern customPattern() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (!Edition.PRO || !c.customFormat || !validFormat(c.payFormat)) {
            return null;
        }
        if (!c.payFormat.equals(cachedFormat)) {
            cachedFormat = c.payFormat;
            String format = c.payFormat.trim();
            StringBuilder re = new StringBuilder("^\\s*");
            Matcher m = Pattern.compile("\\{(name|amount)\\}", Pattern.CASE_INSENSITIVE).matcher(format);
            int at = 0;
            while (m.find()) {
                re.append(literal(format.substring(at, m.start())));
                re.append(m.group(1).equalsIgnoreCase("name") ? NAME : AMOUNT);
                at = m.end();
            }
            re.append(literal(format.substring(at))).append("\\s*$");
            cachedPattern = Pattern.compile(re.toString(), FLAGS);
        }
        return cachedPattern;
    }

    // the fixed words of the format; a space in it matches any run of spaces
    private static String literal(String part) {
        String[] words = part.split("\\s+", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append("\\s+");
            if (!words[i].isEmpty()) sb.append(Pattern.quote(words[i]));
        }
        return sb.toString();
    }

    public static boolean validFormat(String format) {
        if (format == null) return false;
        String f = format.toLowerCase(Locale.ROOT);
        return f.contains("{name}") && f.contains("{amount}") && f.indexOf("{name}") == f.lastIndexOf("{name}")
                && f.indexOf("{amount}") == f.lastIndexOf("{amount}");
    }

    public static void onServerMessage(String message) {
        if (Online.off()) {
            return; // a required update is waiting: the mod is off
        }
        AuctionState st = AuctionState.INSTANCE;
        if (st.ftpListening()) {
            Payment late = parse(message, true);
            if (late != null) {
                st.ftpLate(late.name(), late.amount());
            }
            return;
        }
        if (!st.live || !st.watchPayments) {
            return;
        }
        Payment payment = parse(message, st.instant());
        if (payment != null) {
            st.addPayment(payment.name(), payment.amount());
        }
    }
}
