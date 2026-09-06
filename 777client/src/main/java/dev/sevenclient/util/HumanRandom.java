package dev.sevenclient.util;

import java.util.Random;

/**
 * Randomisation shaped like human input rather than uniform noise.
 *
 * Human reaction times are right-skewed: mostly near the fast end with a long
 * slow tail. A uniform roll between min and max produces a flat histogram, which
 * is itself a fingerprint. `reaction()` samples log-normally instead.
 */
public final class HumanRandom {

    private static final Random RNG = new Random();

    private HumanRandom() { }

    public static double uniform(double min, double max) {
        return min + RNG.nextDouble() * (max - min);
    }

    public static int uniformInt(int min, int max) {
        return min + RNG.nextInt(Math.max(1, max - min + 1));
    }

    public static boolean chance(double percent) {
        return RNG.nextDouble() * 100.0d < percent;
    }

    /** Zero-mean Gaussian clamped to +/-3 sigma so it can never produce a spike. */
    public static double gauss(double sigma) {
        double g = RNG.nextGaussian();
        if (g > 3.0d) g = 3.0d;
        if (g < -3.0d) g = -3.0d;
        return g * sigma;
    }

    /**
     * Log-normally distributed delay in milliseconds within [min, max].
     * Clusters toward the lower bound with an occasional slow outlier.
     */
    public static long reaction(double minMs, double maxMs) {
        if (maxMs <= minMs) return (long) minMs;
        double span = maxMs - minMs;
        // sigma 0.55 gives a realistic spread; mode sits around 20-30% of span
        double sample = Math.exp(RNG.nextGaussian() * 0.55d) - 1.0d;
        double t = sample / 3.0d;
        if (t < 0.0d) t = 0.0d;
        if (t > 1.0d) t = 1.0d;
        return (long) (minMs + span * t);
    }

    /**
     * Slowly drifting value in [-1,1] driven by summed sine waves at irrational
     * frequency ratios. Unlike per-frame white noise this is continuous, so it
     * reads as hand tremor instead of a jittery square wave.
     */
    public static double tremor(double timeSeconds) {
        return (Math.sin(timeSeconds * 6.7d) * 0.55d
              + Math.sin(timeSeconds * 11.3d + 1.7d) * 0.3d
              + Math.sin(timeSeconds * 19.1d + 0.4d) * 0.15d);
    }
}
