package com.auctionoverlay;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.*;

// Smooth text for the Sleek look. Minecraft only loads mod fonts through Fabric API, which this mod doesn't need,
// so the bundled Roboto font is turned into a texture here, one per on-screen size, and drawn in real screen
// pixels. Text sizes and positions use the same GUI units as Minecraft's own font.
public final class SmoothFont {
    private static final float EM = 8.0F;        // GUI pixels per em, a little under Minecraft's font for a low profile
    private static final float BASELINE = 6.6F;  // caps end near Minecraft's (y + 7), centred the same in a row
    private static final String CHARS;
    private static final int DOT = 0x25CF, RING = 0x25CB; // not in Roboto, drawn as circles instead
    private static final int MAX_ATLASES = 40;
    private static final int BUILDS_PER_FRAME = 2; // building a font texture is slow; never do lots at once

    static {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) sb.append(c);
        sb.append("•·…×€£¥–—°é");
        CHARS = sb.toString();
    }

    private record Glyph(int u, int v, int w, int h, int xoff, int yoff) {}

    private static final class Atlas {
        Identifier id;
        int texW, texH;
        final Map<Integer, Glyph> glyphs = new HashMap<>();
    }

    private static STBTTFontinfo info;
    private static ByteBuffer fontData; // stb keeps pointing into this, so it is never freed
    private static float guiScalePerUnit;
    private static boolean tried, ok;
    private static final Map<Integer, Float> ADVANCE = new HashMap<>();
    // least recently used first, so only the oldest size is thrown away when there are too many
    private static final java.util.LinkedHashMap<Integer, Atlas> ATLASES = new java.util.LinkedHashMap<>(16, 0.75F, true);
    private static final Map<Long, Float> KERN = new HashMap<>();
    private static long frameMs = -1;
    private static int builtThisFrame;
    private static int atlasCounter;

    private SmoothFont() {}

    // something went wrong drawing (an odd graphics driver, a texture limit): use Minecraft's font from now on
    public static void disable() {
        tried = true;
        ok = false;
    }

    public static boolean ready() {
        if (!tried) {
            tried = true;
            try (InputStream in = SmoothFont.class.getResourceAsStream("/assets/auctionoverlay/fonts/Roboto-Medium.ttf")) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    fontData = MemoryUtil.memAlloc(bytes.length);
                    fontData.put(bytes).flip();
                    info = STBTTFontinfo.create();
                    ok = stbtt_InitFont(info, fontData);
                    if (ok) {
                        guiScalePerUnit = stbtt_ScaleForMappingEmToPixels(info, EM);
                    }
                }
            } catch (Throwable t) {
                ok = false; // falls back to Minecraft's font
            }
        }
        return ok;
    }

    // width of one character in GUI pixels
    private static float advance(int cp) {
        if (cp == DOT || cp == RING) {
            return EM * 0.62F;
        }
        return ADVANCE.computeIfAbsent(cp, c -> {
            int glyph = stbtt_FindGlyphIndex(info, c);
            if (glyph == 0 && c != ' ') {
                glyph = stbtt_FindGlyphIndex(info, '?');
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer adv = stack.mallocInt(1), lsb = stack.mallocInt(1);
                stbtt_GetGlyphHMetrics(info, glyph, adv, lsb);
                return adv.get(0) * guiScalePerUnit;
            }
        });
    }

    private static float kern(int a, int b) {
        if (a < 0 || a == DOT || a == RING || b == DOT || b == RING) {
            return 0;
        }
        long key = ((long) a << 32) | (b & 0xFFFFFFFFL);
        Float cached = KERN.get(key);
        if (cached != null) {
            return cached;
        }
        float v = kern0(a, b);
        if (KERN.size() < 20_000) KERN.put(key, v);
        return v;
    }

    private static float kern0(int a, int b) {
        return stbtt_GetCodepointKernAdvance(info, a, b) * guiScalePerUnit;
    }

    public static float widthF(String text) {
        float w = 0;
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += kern(prev, cp) + advance(cp);
            prev = cp;
            i += Character.charCount(cp);
        }
        return w;
    }

    public static int width(String text) {
        return (int) Math.ceil(widthF(text));
    }

    // longest start of the text that fits in maxWidth
    public static String cut(String text, int maxWidth) {
        float w = 0;
        int prev = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += kern(prev, cp) + advance(cp);
            if (w > maxWidth) {
                return text.substring(0, i);
            }
            prev = cp;
            i += Character.charCount(cp);
        }
        return text;
    }

    public static void draw(GuiGraphicsExtractor g, String text, float x, float y, int color) {
        Smooth.Spot origin = Smooth.spot(g, x, y + BASELINE);
        float k = origin.k();
        int em = bucket(Math.round(EM * k));
        Atlas atlas = atlas(em);
        if (atlas == null) {
            return;
        }
        float pen = origin.x();
        int prev = -1;
        List<float[]> dots = null;
        Smooth.begin(g);
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                pen += kern(prev, cp) * k;
                prev = cp;
                if (cp == DOT || cp == RING) {
                    if (dots == null) dots = new ArrayList<>();
                    dots.add(new float[]{pen / k, cp});
                } else {
                    Glyph gl = atlas.glyphs.get(cp);
                    if (gl == null) gl = atlas.glyphs.get((int) '?');
                    if (gl != null && gl.w() > 0) {
                        g.blit(RenderPipelines.GUI_TEXTURED, atlas.id, Math.round(pen) + gl.xoff(), origin.y() + gl.yoff(),
                                gl.u(), gl.v(), gl.w(), gl.h(), atlas.texW, atlas.texH, color);
                    }
                }
                pen += advance(cp) * k;
            }
        } finally {
            Smooth.end(g);
        }
        if (dots != null) {
            // back to GUI units for Smooth; the dot sits on the middle of the lower-case letters
            float gs = Smooth.guiScale();
            for (float[] d : dots) {
                float physX = d[0] * k;
                float guiX = (physX / gs - g.pose().m20()) / g.pose().m00() + EM * 0.31F;
                float cy = y + 4.4F;
                if ((int) d[1] == DOT) {
                    Smooth.circle(g, guiX, cy, 2.0F, color);
                } else {
                    Smooth.circleRing(g, guiX, cy, 2.0F, Math.max(1, k * 0.7F), color);
                }
            }
        }
    }

    private static Atlas atlas(int em) {
        Atlas cached = ATLASES.get(em);
        if (cached != null || !ready()) {
            return cached;
        }
        long now = System.currentTimeMillis();
        if (now - frameMs > 30) {
            frameMs = now;
            builtThisFrame = 0;
        }
        if (builtThisFrame >= BUILDS_PER_FRAME && !ATLASES.isEmpty()) {
            return nearest(em); // make it next frame; draw with the closest size for now
        }
        builtThisFrame++;
        while (ATLASES.size() >= MAX_ATLASES) {
            Integer oldest = ATLASES.keySet().iterator().next();
            Atlas old = ATLASES.remove(oldest);
            Minecraft.getInstance().getTextureManager().release(old.id);
        }
        float scale = stbtt_ScaleForMappingEmToPixels(info, em);
        int texW = em > 60 ? 2048 : em > 28 ? 1024 : 512;
        Atlas atlas = new Atlas();
        int[][] boxes = new int[CHARS.length()][];
        int x = 1, y = 1, rowH = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
            for (int i = 0; i < CHARS.length(); i++) {
                int cp = CHARS.charAt(i);
                if (cp != ' ' && stbtt_FindGlyphIndex(info, cp) == 0) {
                    continue;
                }
                stbtt_GetCodepointBitmapBox(info, cp, scale, scale, x0, y0, x1, y1);
                int w = x1.get(0) - x0.get(0), h = y1.get(0) - y0.get(0);
                if (x + w + 1 > texW) {
                    x = 1;
                    y += rowH + 1;
                    rowH = 0;
                }
                boxes[i] = new int[]{x, y, w, h, x0.get(0), y0.get(0)};
                x += w + 1;
                rowH = Math.max(rowH, h);
            }
        }
        int texH = Integer.highestOneBit(Math.max(16, y + rowH + 1) - 1) << 1;
        NativeImage image = new NativeImage(texW, texH, true);
        for (int i = 0; i < CHARS.length(); i++) {
            int[] b = boxes[i];
            if (b == null) {
                continue;
            }
            int cp = CHARS.charAt(i);
            atlas.glyphs.put(cp, new Glyph(b[0], b[1], b[2], b[3], b[4], b[5]));
            if (b[2] <= 0 || b[3] <= 0) {
                continue;
            }
            ByteBuffer bmp = MemoryUtil.memAlloc(b[2] * b[3]);
            try {
                stbtt_MakeCodepointBitmap(info, bmp, b[2], b[3], b[2], scale, scale, cp);
                for (int py = 0; py < b[3]; py++) {
                    for (int px = 0; px < b[2]; px++) {
                        int a = bmp.get(py * b[2] + px) & 255;
                        if (a > 0) {
                            a = (int) Math.round(255 * Math.pow(a / 255.0, 0.82)); // a touch bolder, reads better on dark glass
                            image.setPixel(b[0] + px, b[1] + py, (a << 24) | 0xFFFFFF);
                        }
                    }
                }
            } finally {
                MemoryUtil.memFree(bmp);
            }
        }
        atlas.texW = texW;
        atlas.texH = texH;
        atlas.id = Identifier.fromNamespaceAndPath("auctionoverlay", "smooth_font_" + em + "_" + (atlasCounter++));
        DynamicTexture texture = new DynamicTexture(() -> "Auction Overlay font " + em, image);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(atlas.id, texture);
        ATLASES.put(em, atlas);
        return atlas;
    }

    // text sizes snap to a few steps so a handful of textures cover everything
    static int bucket(int em) {
        em = Math.max(4, Math.min(220, em));
        if (em <= 24) return em;
        if (em <= 48) return em & ~1;
        return Math.round(em / 4.0F) * 4;
    }

    private static Atlas nearest(int em) {
        Atlas best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Atlas> e : ATLASES.entrySet()) {
            int d = Math.abs(e.getKey() - em);
            if (d < bestDiff) {
                bestDiff = d;
                best = e.getValue();
            }
        }
        return best;
    }
}
