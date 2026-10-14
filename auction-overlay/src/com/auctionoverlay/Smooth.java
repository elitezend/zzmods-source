package com.auctionoverlay;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Anti-aliased shapes for the Sleek look. Everything here is drawn in real screen pixels instead of GUI pixels,
// so a rounded corner is a smooth curve and a border can be one screen pixel thin.
// A shape is drawn once per size into a small texture (white, coverage in the alpha), then every frame it's a single
// tinted quad. If textures ever fail, it falls back to filling rectangles of equal coverage.
public final class Smooth {
    interface Sdf {
        float at(float x, float y); // signed distance in screen pixels, negative inside
    }

    private static final int MAX_CACHED = 1500;
    private static final Map<String, int[]> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > MAX_CACHED;
        }
    };

    private record Tex(Identifier id, int w, int h) {}

    private static final int MAX_TEXTURES = 500;
    private static final LinkedHashMap<String, Tex> TEXTURES = new LinkedHashMap<>(64, 0.75F, true);
    private static int texCounter;
    private static boolean texturesBroken;

    private Smooth() {}

    // where (x, y) lands on the screen, and how many screen pixels one GUI pixel is right now
    record Spot(int x, int y, float k) {}

    static int guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    static Spot spot(GuiGraphicsExtractor g, float x, float y) {
        Matrix3x2f m = g.pose();
        int gs = guiScale();
        return new Spot(Math.round((m.m00() * x + m.m20()) * gs), Math.round((m.m11() * y + m.m21()) * gs), m.m00() * gs);
    }

    // draw in screen pixels until end()
    static void begin(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
        g.pose().identity();
        float inv = 1.0F / guiScale();
        g.pose().scale(inv, inv);
    }

    static void end(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    // groups of 5 ints: x, y, w, h, coverage 0-255
    private static int[] bake(int w, int h, Sdf sdf, float soft) {
        List<int[]> rects = new ArrayList<>();
        int[] prev = null;
        int prevY = 0;
        for (int y = 0; y <= h; y++) {
            int[] spans = y < h ? row(w, y, sdf, soft) : null;
            if (prev != null && spans != null && java.util.Arrays.equals(prev, spans)) {
                continue;
            }
            if (prev != null) {
                for (int i = 0; i < prev.length; i += 3) {
                    rects.add(new int[]{prev[i], prevY, prev[i + 1], y - prevY, prev[i + 2]});
                }
            }
            prev = spans;
            prevY = y;
        }
        int[] out = new int[rects.size() * 5];
        for (int i = 0; i < rects.size(); i++) {
            System.arraycopy(rects.get(i), 0, out, i * 5, 5);
        }
        return out;
    }

    // runs of equal coverage on one row: x, length, coverage
    private static int[] row(int w, int y, Sdf sdf, float soft) {
        int[] tmp = new int[w * 3];
        int n = 0;
        int runStart = 0, runA = -1;
        for (int x = 0; x <= w; x++) {
            int a = -1;
            if (x < w) {
                float d = sdf.at(x + 0.5F, y + 0.5F);
                float cov;
                if (soft > 0) {
                    // shadows: nothing inside, fading out over "soft" pixels outside
                    cov = d < 0 ? 0 : Math.max(0, 1 - d / soft);
                    cov = cov * cov;
                    a = Math.round(cov * 6) * 42; // few shades = few draw calls
                } else {
                    cov = Math.max(0, Math.min(1, 0.5F - d));
                    a = Math.round(cov * 32) * 8;
                }
                a = Math.min(255, a);
            }
            if (a != runA) {
                if (runA > 0) {
                    tmp[n++] = runStart;
                    tmp[n++] = x - runStart;
                    tmp[n++] = runA;
                }
                runStart = x;
                runA = a;
            }
        }
        return java.util.Arrays.copyOf(tmp, n);
    }

    private static void paint(GuiGraphicsExtractor g, int ox, int oy, int[] rects, int color) {
        int alpha = color >>> 24;
        int rgb = color & 0xFFFFFF;
        if (alpha == 0) {
            return;
        }
        for (int i = 0; i < rects.length; i += 5) {
            int a = alpha * rects[i + 4] / 255;
            if (a > 0) {
                int x = ox + rects[i], y = oy + rects[i + 1];
                g.fill(x, y, x + rects[i + 2], y + rects[i + 3], (a << 24) | rgb);
            }
        }
    }

    private static void shape(GuiGraphicsExtractor g, String key, int pad, float x, float y, float w, float h, int color,
                              float soft, java.util.function.Function<Spot, Sdf> make) {
        Spot s = spot(g, x, y);
        int pw = Math.max(1, Math.round(w * s.k())), ph = Math.max(1, Math.round(h * s.k()));
        shapeAt(g, key, pad, s, pw, ph, color, soft, make);
    }

    // the same, at a spot already in screen pixels
    private static void shapeAt(GuiGraphicsExtractor g, String key, int pad, Spot s, int pw, int ph, int color,
                                float soft, java.util.function.Function<Spot, Sdf> make) {
        String full = key + ":" + pw + "x" + ph + "@" + s.k() + "/" + soft;
        if (!texturesBroken) {
            Tex t = TEXTURES.get(full);
            if (t == null) {
                try {
                    t = texture(make.apply(new Spot(pw, ph, s.k())), pw + 2 * pad, ph + 2 * pad, pad, soft);
                    while (TEXTURES.size() >= MAX_TEXTURES) {
                        String oldest = TEXTURES.keySet().iterator().next();
                        Minecraft.getInstance().getTextureManager().release(TEXTURES.remove(oldest).id());
                    }
                    TEXTURES.put(full, t);
                } catch (RuntimeException | LinkageError e) {
                    texturesBroken = true; // odd graphics setup: use the fill fallback from now on
                }
            }
            if (t != null) {
                begin(g);
                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, t.id(), s.x() - pad, s.y() - pad, 0.0F, 0.0F, t.w(), t.h(), t.w(), t.h(), color);
                } finally {
                    end(g);
                }
                return;
            }
        }
        int[] rects = CACHE.get(full);
        if (rects == null) {
            Sdf sdf = make.apply(new Spot(pw, ph, s.k()));
            rects = bake(pw + 2 * pad, ph + 2 * pad, (px, py) -> sdf.at(px - pad, py - pad), soft);
            CACHE.put(full, rects);
        }
        begin(g);
        paint(g, s.x() - pad, s.y() - pad, rects, color);
        end(g);
    }

    // the shape as a white texture with its coverage in the alpha channel
    private static Tex texture(Sdf sdf, int w, int h, int pad, float soft) {
        NativeImage img = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float d = sdf.at(x - pad + 0.5F, y - pad + 0.5F);
                float cov;
                if (soft > 0) {
                    cov = d < 0 ? 0 : Math.max(0, 1 - d / soft);
                    cov = cov * cov;
                } else {
                    cov = Math.max(0, Math.min(1, 0.5F - d));
                }
                int a = Math.round(cov * 255);
                if (a > 0) {
                    img.setPixel(x, y, (a << 24) | 0xFFFFFF);
                }
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("auctionoverlay", "smooth_shape_" + (texCounter++));
        DynamicTexture tex = new DynamicTexture(() -> "Auction Overlay shape", img);
        tex.upload();
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return new Tex(id, w, h);
    }

    // distance to a w x h box with corner radius r, box from (0,0)
    static float boxDist(float px, float py, float w, float h, float r) {
        float qx = Math.abs(px - w / 2) - (w / 2 - r);
        float qy = Math.abs(py - h / 2) - (h / 2 - r);
        float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
    }

    // ---- what Style uses (sizes in GUI pixels, thickness in screen pixels)

    public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
        shape(g, "rect" + r, 1, x, y, w, h, color, 0, s -> {
            float rr = Math.min(r * s.k(), Math.min(s.x(), s.y()) / 2.0F);
            return (px, py) -> boxDist(px, py, s.x(), s.y(), rr);
        });
    }

    // a border lying just inside the edge of the same box
    public static void ring(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thickness, int color) {
        shape(g, "ring" + r + "," + thickness, 1, x, y, w, h, color, 0, s -> {
            float rr = Math.min(r * s.k(), Math.min(s.x(), s.y()) / 2.0F);
            return (px, py) -> Math.abs(boxDist(px, py, s.x(), s.y(), rr) + thickness / 2) - thickness / 2;
        });
    }

    // soft shadow around a box (nothing under the box itself, so see-through panels stay see-through)
    public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float spread, int color) {
        Spot s0 = spot(g, x, y);
        int pad = Math.round(spread * s0.k()) + 2;
        float soft = spread * s0.k();
        shape(g, "shadow" + r, pad, x, y, w, h, color, Math.max(1, soft), s -> {
            float rr = Math.min(r * s.k(), Math.min(s.x(), s.y()) / 2.0F);
            return (px, py) -> boxDist(px, py, s.x(), s.y(), rr);
        });
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
        rect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    // gear icon: body circle + 8 rounded teeth, minus a centre hole. "radius" is to the tooth tips, in GUI pixels
    public static void gear(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
        shape(g, "gear" + radius, 1, cx - radius, cy - radius, radius * 2, radius * 2, color, 0, s -> gearSdf(s.x()));
    }

    // a gear exactly in the middle of the box (x, y, w, h): worked out on the same screen pixels the button is drawn
    // on, with a size that leaves the same gap on both sides
    public static void gearIn(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        Spot b = spot(g, x, y);
        int bw = Math.max(1, Math.round(w * b.k())), bh = Math.max(1, Math.round(h * b.k()));
        int d = Math.max(3, Math.round(radius * 2 * b.k()));
        if ((bw - d) % 2 != 0) d--;
        shapeAt(g, "gear" + radius, 1, new Spot(b.x() + (bw - d) / 2, b.y() + (bh - d) / 2, b.k()), d, d, color, 0, s -> gearSdf(s.x()));
    }

    static Sdf gearSdf(float size) {
        float c = size / 2.0F;
        float tip = c, body = c * 0.72F, hole = c * 0.30F;
        float toothHalfW = c * 0.20F, toothLen = (tip - body) + c * 0.12F;
        return (px, py) -> {
            float x = px - c, y = py - c;
            float len = (float) Math.sqrt(x * x + y * y);
            float d = len - body;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float u = x * ca + y * sa - (tip - toothLen / 2); // along the tooth
                float v = -x * sa + y * ca;                       // across it
                float qx = Math.abs(u) - toothLen / 2 + 0.6F, qy = Math.abs(v) - toothHalfW + 0.6F;
                float out = (float) Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0));
                d = Math.min(d, out + Math.min(Math.max(qx, qy), 0) - 0.6F);
            }
            return Math.max(d, hole - len); // cut the hole
        };
    }

    public static void circleRing(GuiGraphicsExtractor g, float cx, float cy, float radius, float thickness, int color) {
        ring(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, thickness, color);
    }

    // a round-capped line from (x1, y1) to (x2, y2), "width" in GUI pixels
    public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float width, int color) {
        float minX = Math.min(x1, x2) - width, minY = Math.min(y1, y2) - width;
        float w = Math.abs(x2 - x1) + 2 * width, h = Math.abs(y2 - y1) + 2 * width;
        String key = "line" + (x1 - minX) + "," + (y1 - minY) + "," + (x2 - minX) + "," + (y2 - minY) + "," + width;
        shape(g, key, 1, minX, minY, w, h, color, 0, s -> {
            float k = s.k();
            float ax = (x1 - minX) * k, ay = (y1 - minY) * k, bx = (x2 - minX) * k, by = (y2 - minY) * k;
            float half = width * k / 2;
            return (px, py) -> {
                float dx = bx - ax, dy = by - ay;
                float len2 = dx * dx + dy * dy;
                float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
                float ex = px - (ax + t * dx), ey = py - (ay + t * dy);
                return (float) Math.sqrt(ex * ex + ey * ey) - half;
            };
        });
    }

    // a one-screen-pixel line, fading out at both ends when "fade" is set
    public static void hairline(GuiGraphicsExtractor g, float x, float y, float w, int color, boolean fade) {
        Spot s = spot(g, x, y);
        int pw = Math.max(1, Math.round(w * s.k()));
        int a = color >>> 24, rgb = color & 0xFFFFFF;
        begin(g);
        if (!fade) {
            g.fill(s.x(), s.y(), s.x() + pw, s.y() + 1, color);
        } else {
            int steps = Math.min(48, pw);
            for (int i = 0; i < steps; i++) {
                int x1 = s.x() + pw * i / steps, x2 = s.x() + pw * (i + 1) / steps;
                double t = (i + 0.5) / steps;
                int fa = (int) (a * Math.min(1, Math.sin(Math.PI * t) * 1.6));
                g.fill(x1, s.y(), x2, s.y() + 1, (fa << 24) | rgb);
            }
        }
        end(g);
    }
}
