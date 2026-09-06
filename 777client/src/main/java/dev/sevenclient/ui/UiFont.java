package dev.sevenclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Text drawing through bundled Inter (SIL OFL 1.1), in two weights.
 *
 * Two weights rather than one because weight is the only typographic hierarchy
 * available in a monochrome UI -- with colour off the table, Medium vs SemiBold
 * is what separates a label from its value.
 *
 * Identifier paths accept ONLY [a-z0-9/._-]. A capital letter throws, and doing
 * that in a static initialiser makes the JVM mark the class permanently
 * uninitialisable, turning one bad filename into NoClassDefFoundError at every
 * later call site. Hence lowercase filenames AND a guard that cannot throw.
 */
public final class UiFont {

    public static final Identifier FONT      = safeId("sevenclient", "ui");
    public static final Identifier FONT_BOLD = safeId("sevenclient", "ui_bold");

    private static final Identifier FILE_MEDIUM   = safeId("sevenclient", "font/inter_medium.ttf");
    private static final Identifier FILE_SEMIBOLD = safeId("sevenclient", "font/inter_semibold.ttf");

    /** Master switch. Set false to force the vanilla font. */
    public static boolean useCustomFont = true;

    private static boolean found = false;
    private static long lastCheck = 0L;

    private UiFont() { }

    private static Identifier safeId(String namespace, String path) {
        try {
            return Identifier.of(namespace, path);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void invalidate() {
        found = false;
        lastCheck = 0L;
    }

    /**
     * Cached permanently once found; re-tested every 5s while missing so a font
     * dropped in later is picked up on the next F3+T without needing a
     * resource-reload listener (and the extra API dependency that implies).
     */
    private static boolean fontAvailable() {
        if (!useCustomFont) return false;
        if (FONT == null || FONT_BOLD == null || FILE_MEDIUM == null || FILE_SEMIBOLD == null) return false;
        if (found) return true;

        long now = System.currentTimeMillis();
        if (now - lastCheck < 5000L) return false;
        lastCheck = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getResourceManager() == null) return false;

        try {
            found = mc.getResourceManager().getResource(FILE_MEDIUM).isPresent()
                 && mc.getResourceManager().getResource(FILE_SEMIBOLD).isPresent();
        } catch (Throwable t) {
            found = false;
        }
        return found;
    }

    public static boolean usingCustomFont() {
        return fontAvailable();
    }

    public static Text styled(String s, boolean bold) {
        MutableText t = Text.literal(s);
        if (!fontAvailable()) return t;
        return t.setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(bold ? FONT_BOLD : FONT)));
    }

    public static Text styled(String s) {
        return styled(s, false);
    }

    // ------------------------------------------------------------------ draw

    public static void draw(DrawContext ctx, String s, float x, float y, int color) {
        drawWeighted(ctx, s, x, y, color, false);
    }

    public static void drawBold(DrawContext ctx, String s, float x, float y, int color) {
        drawWeighted(ctx, s, x, y, color, true);
    }

    private static void drawWeighted(DrawContext ctx, String s, float x, float y, int color, boolean bold) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawText(mc.textRenderer, styled(s, bold), (int) x, (int) y, color, false);
    }

    public static void drawCentered(DrawContext ctx, String s, float cx, float y, int color) {
        draw(ctx, s, cx - width(s) / 2f, y, color);
    }

    public static void drawRight(DrawContext ctx, String s, float rx, float y, int color) {
        draw(ctx, s, rx - width(s), y, color);
    }

    public static void drawRightBold(DrawContext ctx, String s, float rx, float y, int color) {
        drawBold(ctx, s, rx - widthBold(s), y, color);
    }

    // ----------------------------------------------------------------- metrics

    public static int width(String s) {
        return MinecraftClient.getInstance().textRenderer.getWidth(styled(s, false));
    }

    public static int widthBold(String s) {
        return MinecraftClient.getInstance().textRenderer.getWidth(styled(s, true));
    }

    public static int height() {
        return MinecraftClient.getInstance().textRenderer.fontHeight;
    }

    public static String trim(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (width(sb.toString() + c + "...") > maxWidth) break;
            sb.append(c);
        }
        return sb + "...";
    }
}
