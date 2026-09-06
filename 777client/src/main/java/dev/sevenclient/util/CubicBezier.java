package dev.sevenclient.util;

/**
 * CSS-style cubic-bezier(x1,y1,x2,y2) easing, solved with Newton-Raphson and a
 * bisection fallback. Used to shape how aggressively rotation closes distance as
 * a function of remaining error, so acceleration and deceleration are curved
 * rather than linear.
 */
public final class CubicBezier {

    private final double x1, y1, x2, y2;

    public CubicBezier(double x1, double y1, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    private static double curve(double t, double a1, double a2) {
        double inv = 1.0d - t;
        return 3.0d * inv * inv * t * a1
             + 3.0d * inv * t * t * a2
             + t * t * t;
    }

    private static double slope(double t, double a1, double a2) {
        double inv = 1.0d - t;
        return 3.0d * inv * inv * a1
             + 6.0d * inv * t * (a2 - a1)
             + 3.0d * t * t * (1.0d - a2);
    }

    public double ease(double x) {
        if (x <= 0.0d) return 0.0d;
        if (x >= 1.0d) return 1.0d;

        double t = x;
        for (int i = 0; i < 6; i++) {
            double err = curve(t, x1, x2) - x;
            if (Math.abs(err) < 1e-6) return curve(t, y1, y2);
            double d = slope(t, x1, x2);
            if (Math.abs(d) < 1e-6) break;
            t -= err / d;
        }

        double lo = 0.0d, hi = 1.0d;
        t = x;
        for (int i = 0; i < 24; i++) {
            double v = curve(t, x1, x2);
            if (Math.abs(v - x) < 1e-6) break;
            if (v > x) hi = t; else lo = t;
            t = (lo + hi) * 0.5d;
        }
        return curve(t, y1, y2);
    }

    /** Fast, snappy start with a long soft tail -- good default for aim tracking. */
    public static final CubicBezier AIM = new CubicBezier(0.22d, 0.61d, 0.36d, 1.0d);
    /** Gentler, more deliberate. */
    public static final CubicBezier SMOOTH = new CubicBezier(0.45d, 0.05d, 0.55d, 0.95d);
}
