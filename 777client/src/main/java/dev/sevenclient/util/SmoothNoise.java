package dev.sevenclient.util;

/**
 * Continuous value noise, cubic-smoothed, summed over three octaves.
 *
 * WHY THIS REPLACED GAUSSIAN NOISE: sampling an independent random value every
 * frame produces white noise. At 200fps that is a 200Hz signal, which reads as
 * shimmer/jitter rather than movement, and it also makes the rotation delta
 * sequence statistically obvious -- real hand tremor is a low-frequency,
 * temporally correlated signal, not per-sample independent.
 *
 * This is C1 continuous (value and first derivative match at lattice points),
 * so the output is a smooth wander no matter how often you sample it. Sampling
 * the same time twice gives the same value, which is the whole point.
 */
public final class SmoothNoise {

    private final long seed;

    public SmoothNoise(long seed) {
        this.seed = seed;
    }

    /** SplitMix64-style avalanche, mapped to [-1, 1]. */
    private double hash(long i) {
        long h = i * 0x9E3779B97F4A7C15L + seed;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) / (double) (1L << 53)) * 2.0d - 1.0d;
    }

    /** Single octave with smoothstep interpolation. */
    public double at(double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double a = hash(i);
        double b = hash(i + 1);
        double t = f * f * (3.0d - 2.0d * f);
        return a + (b - a) * t;
    }

    /**
     * Fractional Brownian motion: three octaves at irrational frequency ratios
     * so the sum never repeats on a short period. Amplitude-normalised to ~[-1,1].
     */
    public double fbm(double x) {
        return at(x) * 0.60d
             + at(x * 2.17d + 11.3d) * 0.28d
             + at(x * 4.31d + 29.7d) * 0.12d;
    }
}
