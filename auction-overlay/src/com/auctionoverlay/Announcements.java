package com.auctionoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

// Announcements staff post on the dashboard (updates, discount offers, news). Fetched with the update check (when you
// join, then every 5 minutes) and read from the "Announcements" button in the game menu (Esc).
final class Announcements {
    record Item(String id, String kind, String title, String body, String code, String button, String url, long at) {}

    private static volatile List<Item> items = List.of();
    private static boolean toldThisRun;

    private Announcements() {}

    static List<Item> all() {
        return items;
    }

    // the server's answer: {announcements: [{id, kind, title, body, code, button, url, at}]}
    static void read(JsonObject o) {
        if (o == null || !o.has("announcements") || !o.get("announcements").isJsonArray()) return;
        JsonArray a = o.getAsJsonArray("announcements");
        List<Item> out = new ArrayList<>();
        for (JsonElement e : a) {
            if (!e.isJsonObject() || out.size() >= 30) continue;
            JsonObject j = e.getAsJsonObject();
            String id = str(j, "id", 40);
            String title = str(j, "title", 80);
            if (id.isEmpty() || title.isEmpty()) continue;
            String url = str(j, "url", 300);
            if (!url.isEmpty() && !url.startsWith("https://zzmods.")) url = ""; // only ever our own site
            out.add(new Item(id, str(j, "kind", 10), title, str(j, "body", 600), str(j, "code", 40), str(j, "button", 30), url,
                    j.has("at") ? j.get("at").getAsLong() : 0));
        }
        items = List.copyOf(out);
        if (!toldThisRun && unread() > 0) {
            toldThisRun = true;
            Online.note("New announcement from zzmods", "Press Esc - it's under ZZMODS on the right", 8000);
        }
    }

    private static String str(JsonObject o, String k, int max) {
        String s = o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString().replaceAll("[\\p{Cntrl}§&&[^\\n]]", "").trim() : "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    static int unread() {
        List<String> seen = AuctionState.INSTANCE.config.annSeen;
        int n = 0;
        for (Item i : items) if (seen == null || !seen.contains(i.id())) n++;
        return n;
    }

    static void markRead() {
        AuctionState.Config c = AuctionState.INSTANCE.config;
        if (c.annSeen == null) c.annSeen = new ArrayList<>();
        boolean changed = false;
        for (Item i : items) {
            if (!c.annSeen.contains(i.id())) {
                c.annSeen.add(i.id());
                changed = true;
            }
        }
        while (c.annSeen.size() > 200) c.annSeen.remove(0);
        if (changed) AuctionState.INSTANCE.save();
    }

    // with Gamble Tracker installed too, only one of them adds the button
    static boolean claimButton() {
        String who = System.getProperty("zzmods.announcements");
        if (who == null) {
            System.setProperty("zzmods.announcements", "auction");
            return true;
        }
        return who.equals("auction");
    }

    static String label() {
        int n = unread();
        return n > 0 ? "Announcements (" + n + ")" : "Announcements";
    }
}
