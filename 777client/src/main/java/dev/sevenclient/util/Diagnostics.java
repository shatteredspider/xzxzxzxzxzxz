package dev.sevenclient.util;

/**
 * Central place for "is this thing actually running" state.
 *
 * Every layer of the client writes one field here, so a single HUD readout can
 * tell you exactly where a failure is: whether frames dispatch, whether ticks
 * dispatch, whether a target is being found, whether an attack event ever fired,
 * and what the last thrown exception was.
 */
public final class Diagnostics {

    private Diagnostics() { }

    public static volatile long ticks = 0L;
    public static volatile long frames = 0L;
    public static volatile boolean frameSourceIsMixin = false;
    public static volatile float lastDt = 0f;

    public static volatile long attackEvents = 0L;
    public static volatile long hitFlickArmed = 0L;
    public static volatile String aimTarget = "none";
    public static volatile double lastYawStep = 0.0d;

    public static volatile double aimAuthority = 0.0d;
    public static volatile double aimFlick = 0.0d;
    public static volatile double aimSpin = 0.0d;
    public static volatile double aimError = 0.0d;
    public static volatile double aimUrgency = 1.0d;
    public static volatile double aimCoverage = 0.0d;

    public static volatile String lastError = "none";
    public static volatile String lastErrorModule = "";

    /** Frames dispatched per second, sampled over a rolling window. */
    private static long windowStart = 0L;
    private static long windowFrames = 0L;
    public static volatile double fps = 0.0d;

    public static void countFrame() {
        frames++;
        windowFrames++;
        long now = System.currentTimeMillis();
        if (windowStart == 0L) windowStart = now;
        if (now - windowStart >= 500L) {
            fps = windowFrames * 1000.0d / (now - windowStart);
            windowStart = now;
            windowFrames = 0L;
        }
    }

    public static void error(String module, Throwable t) {
        lastErrorModule = module;
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName());
        if (t.getMessage() != null) sb.append(": ").append(t.getMessage());
        StackTraceElement[] st = t.getStackTrace();
        if (st.length > 0) {
            sb.append(" @ ").append(st[0].getClassName().replace("dev.sevenclient.", ""))
              .append('.').append(st[0].getMethodName())
              .append(':').append(st[0].getLineNumber());
        }
        lastError = sb.toString();
    }
}
