package dev.sevenclient.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Tiny vector glyphs drawn from line/ring primitives.
 *
 * Bitmap icon sheets mean shipping a PNG per icon and a texture bind per draw;
 * these are a handful of fills and scale with the layout instead of being locked
 * to one resolution. Each draws inside a `size` box with its origin at (x, y).
 */
public final class Icons {

    private Icons() { }

    /** Crossed swords. */
    public static void combat(DrawContext ctx, float x, float y, float size, int color) {
        int x0 = (int) x, y0 = (int) y, s = (int) size;
        Render2D.line(ctx, x0 + 1, y0 + s - 2, x0 + s - 2, y0 + 1, color);
        Render2D.line(ctx, x0 + 2, y0 + s - 2, x0 + s - 1, y0 + 1, color);
        Render2D.line(ctx, x0 + 1, y0 + 1, x0 + s - 2, y0 + s - 2, color);
        Render2D.line(ctx, x0 + 2, y0 + 1, x0 + s - 1, y0 + s - 2, color);
    }

    /** Motion: chevrons pointing right. */
    public static void movement(DrawContext ctx, float x, float y, float size, int color) {
        int cx = (int) (x + size / 2f), cy = (int) (y + size / 2f);
        int h = (int) (size * 0.34f);
        for (int o = 0; o < 2; o++) {
            int ox = cx - 4 + o * 5;
            Render2D.line(ctx, ox, cy - h, ox + 3, cy, color);
            Render2D.line(ctx, ox + 3, cy, ox, cy + h, color);
        }
    }

    /** Head and shoulders. */
    public static void player(DrawContext ctx, float x, float y, float size, int color) {
        float cx = x + size / 2f;
        Render2D.ring(ctx, cx, y + size * 0.32f, size * 0.19f, color);
        int by = (int) (y + size * 0.62f);
        int bw = (int) (size * 0.32f);
        Render2D.line(ctx, (int) cx - bw, by + 3, (int) cx - bw, by + 1, color);
        Render2D.line(ctx, (int) cx - bw, by + 1, (int) cx + bw, by + 1, color);
        Render2D.line(ctx, (int) cx + bw, by + 1, (int) cx + bw, by + 3, color);
    }

    /** Eye. */
    public static void render(DrawContext ctx, float x, float y, float size, int color) {
        float cx = x + size / 2f, cy = y + size / 2f;
        int half = (int) (size * 0.42f);
        for (int i = -half; i <= half; i++) {
            double t = (double) i / half;
            int dy = (int) Math.round(Math.cos(t * Math.PI / 2) * size * 0.26f);
            ctx.fill((int) cx + i, (int) cy - dy, (int) cx + i + 1, (int) cy - dy + 1, color);
            ctx.fill((int) cx + i, (int) cy + dy, (int) cx + i + 1, (int) cy + dy + 1, color);
        }
        Render2D.disc(ctx, cx, cy, size * 0.13f, color);
    }

    /** Keyboard. */
    public static void keyboard(DrawContext ctx, float x, float y, float size, int color) {
        float w = size * 0.86f, h = size * 0.58f;
        float bx = x + (size - w) / 2f, by = y + (size - h) / 2f;
        Render2D.roundedBorder(ctx, bx, by, w, h, 2f, color);
        for (int i = 0; i < 3; i++) {
            int px = (int) (bx + 3 + i * 3);
            ctx.fill(px, (int) (by + 3), px + 1, (int) (by + 4), color);
        }
        ctx.fill((int) (bx + 3), (int) (by + h - 4), (int) (bx + w - 3), (int) (by + h - 3), color);
    }

    /** Sliders. */
    public static void sliders(DrawContext ctx, float x, float y, float size, int color) {
        for (int i = 0; i < 3; i++) {
            int ly = (int) (y + size * 0.25f + i * (size * 0.25f));
            ctx.fill((int) x + 1, ly, (int) (x + size - 1), ly + 1, color);
            int knob = (int) (x + 2 + ((i * 4) % (int) (size - 5)));
            Render2D.disc(ctx, knob + 1.5f, ly + 0.5f, 1.8f, color);
        }
    }

    /** Diamond app mark. */
    public static void mark(DrawContext ctx, float x, float y, float size, int color) {
        float cx = x + size / 2f, cy = y + size / 2f;
        int half = (int) (size * 0.34f);
        for (int i = -half; i <= half; i++) {
            int span = half - Math.abs(i);
            ctx.fill((int) (cx - span), (int) cy + i, (int) (cx + span + 1), (int) cy + i + 1, color);
        }
    }
}
