package com.auctionoverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Everything that can be auctioned: every item, plus a "<Mob> Spawner" for each spawn egg
// (id "spawner:<mob>", drawn as a spawner with the egg in the corner).
public final class Lots {
    public record Entry(String id, String name, Item item, Item egg) {
        public boolean isSpawner() {
            return egg != null;
        }
    }

    private static final String EGG = "_spawn_egg";
    private static List<Entry> all;
    private static final Map<String, Entry> byId = new HashMap<>();
    private static final Map<Entry, String> searchText = new HashMap<>();

    private Lots() {}

    public static List<Entry> all() {
        if (all != null) {
            return all;
        }
        List<Entry> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            String name = new ItemStack(item).getHoverName().getString();
            list.add(new Entry(id, name, item, null));
            String mob = id.endsWith(EGG) ? id.substring(0, id.length() - EGG.length()) : null;
            if (mob != null && (Edition.ALL_SPAWNERS || mob.equals("skeleton"))) {
                String mobName = name.endsWith(" Spawn Egg") ? name.substring(0, name.length() - 10) : words(mob);
                list.add(new Entry("spawner:" + mob, mobName + " Spawner", Items.SPAWNER, item));
            }
        }
        list.sort(Comparator.comparing(Entry::id));
        all = List.copyOf(list);
        for (Entry e : all) {
            byId.put(e.id(), e);
            searchText.put(e, (e.name() + " " + e.id().replace('_', ' ').replace(':', ' ')).toLowerCase(Locale.ROOT));
        }
        return all;
    }

    public static Entry get(String id) {
        all();
        return id == null ? null : byId.get(id);
    }

    public static String name(String id) {
        Entry e = get(id);
        return e == null ? id : e.name();
    }

    public static boolean matches(Entry e, String[] words) {
        String text = searchText.get(e);
        for (String w : words) {
            if (!text.contains(w)) {
                return false;
            }
        }
        return true;
    }

    // 16x16 icon at (x, y)
    public static void draw(GuiGraphicsExtractor g, String id, int x, int y) {
        Entry e = get(id);
        if (e == null) {
            return;
        }
        g.item(new ItemStack(e.item()), x, y);
        if (e.egg() != null) {
            g.pose().pushMatrix();
            g.pose().translate(x + 8, y + 8);
            g.pose().scale(0.5F, 0.5F);
            g.item(new ItemStack(e.egg()), 0, 0);
            g.pose().popMatrix();
        }
    }

    private static String words(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
