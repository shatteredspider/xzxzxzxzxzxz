package dev.sevenclient.ui;

/**
 * Monochrome only: black, white, grey. No hue anywhere.
 *
 * Surfaces are defined as PAIRS rather than flat values. Every panel is a
 * vertical gradient between a lighter top and a darker bottom, which is what
 * separates a "rendered" look from a flat one: a real surface catches more light
 * at its upper edge. The steps are small (4-8 levels) so it reads as material
 * rather than as a visible gradient.
 *
 * The base black is genuinely near-black now (0x050505 -> 0x000000), which gives
 * the grey ramp above it far more room to separate.
 */
public final class Theme {

    private Theme() { }

    // backdrop
    public static final int SHADE        = 0xC4000000;
    public static final int SHADOW       = 0xAA000000;

    // window body: near-black, fading to true black at the bottom
    public static final int WINDOW       = 0xFF080808;
    public static final int WINDOW_GRAD  = 0xFF000000;
    public static final int OUTLINE      = 0xFF232323;
    public static final int OUTLINE_SOFT = 0xFF141414;
    public static final int HIGHLIGHT    = 0x1AFFFFFF;
    public static final int INNER_LIGHT  = 0x0DFFFFFF;

    // sidebar
    public static final int SIDEBAR      = 0xFF0E0E0E;
    public static final int SIDEBAR_GRAD = 0xFF090909;

    // cards / panels, as top->bottom pairs
    public static final int PANEL        = 0xFF141414;
    public static final int PANEL_GRAD   = 0xFF0D0D0D;
    public static final int PANEL_HOVER  = 0xFF1E1E1E;
    public static final int PANEL_HOVER_GRAD = 0xFF151515;
    public static final int NAV_ACTIVE   = 0xFF242424;
    public static final int NAV_ACTIVE_GRAD  = 0xFF161616;
    public static final int HEADER       = 0xFF0B0B0B;
    public static final int TRACK        = 0xFF1F1F1F;
    public static final int TRACK_GRAD   = 0xFF2A2A2A;

    // type ramp
    public static final int TEXT         = 0xFFFAFAFA;
    public static final int TEXT_DIM     = 0xFF9E9E9E;
    public static final int TEXT_FAINT   = 0xFF565656;

    // accent == white, spent carefully. Duotone: bright top, softer bottom.
    public static final int ACCENT       = 0xFFFFFFFF;
    public static final int ACCENT_GRAD  = 0xFFC8C8C8;
    public static final int ACCENT_HI    = 0xFFFFFFFF;
    public static final int ACCENT_SOFT  = 0xFF303030;
    public static final int ACCENT_GLOW  = 0x1FFFFFFF;

    // toggles: inverted when active
    public static final int ON           = 0xFFFFFFFF;
    public static final int ON_GRAD      = 0xFFD2D2D2;
    public static final int OFF          = 0xFF212121;
    public static final int OFF_GRAD     = 0xFF171717;
    public static final int KNOB         = 0xFF060606;

    public static int alpha(int color, float a) {
        int base = color & 0x00FFFFFF;
        int existing = (color >>> 24) & 0xFF;
        int out = (int) (existing * Math.max(0f, Math.min(1f, a)));
        return (out << 24) | base;
    }

    public static int withAlpha(int color, int a) {
        return ((a & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24)
             | ((int) (ar + (br - ar) * t) << 16)
             | ((int) (ag + (bg - ag) * t) << 8)
             |  (int) (ab + (bb - ab) * t);
    }
}
