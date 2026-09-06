package dev.sevenclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Rotation math, including Minecraft's mouse quantisation step.
 *
 * Vanilla turns raw mouse counts into degrees like this:
 *     d    = sensitivity * 0.6 + 0.2
 *     step = d * d * d * 8.0 * 0.15
 * Every legitimate rotation delta is therefore an integer multiple of `step`.
 * Anything else is a rotation a physical mouse could not have produced, which is
 * exactly what statistical rotation checks look for. We snap to that lattice and
 * carry the remainder forward so no motion is silently thrown away.
 */
public final class Rotations {

    private Rotations() { }

    /** Degrees per single mouse count at the player's current sensitivity. */
    public static double gcd() {
        double sens = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
        double f = sens * 0.6d + 0.2d;
        return f * f * f * 8.0d * 0.15d;
    }

    /**
     * Snaps a delta down to the nearest whole number of mouse counts.
     * `residual[axis]` accumulates the discarded fraction and is folded back in
     * on the next call, so slow tracking still converges instead of stalling.
     */
    public static float snap(double delta, double[] residual, int axis) {
        double step = gcd();
        if (step <= 0.0d) return (float) delta;

        double wanted = delta + residual[axis];
        double counts = Math.floor(Math.abs(wanted) / step) * Math.signum(wanted);
        double applied = counts * step;
        residual[axis] = wanted - applied;

        // Keep the carried remainder bounded; a huge residual would produce a jerk.
        double cap = step * 8.0d;
        if (residual[axis] > cap) residual[axis] = cap;
        if (residual[axis] < -cap) residual[axis] = -cap;

        return (float) applied;
    }

    public static float wrap(float degrees) {
        return MathHelper.wrapDegrees(degrees);
    }

    /** Yaw/pitch from `from` to `to`. Index 0 = yaw, 1 = pitch. */
    public static float[] to(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[] { wrap(yaw), MathHelper.clamp(pitch, -90f, 90f) };
    }

    /**
     * Instead of aiming at a fixed point (a dead giveaway: perfectly centred
     * tracking), compute the angular window the target's hitbox occupies and clamp
     * the current rotation into it. If the crosshair is already on the target,
     * nothing moves at all -- which is what a human's aim looks like.
     *
     * @param inset shrink of the hitbox in blocks, keeps aim off the exact edge
     * @return {minYaw, maxYaw, minPitch, maxPitch}
     */
    public static float[] angularWindow(Vec3d eye, Entity target, double inset, float currentYaw) {
        return angularWindow(eye, target.getBoundingBox(), inset, currentYaw);
    }

    /** Box overload, so the window can be built from a time-lagged hitbox. */
    public static float[] angularWindow(Vec3d eye, Box raw, double inset, float currentYaw) {
        Box box = raw.expand(-inset);
        if (box.getLengthX() <= 0 || box.getLengthY() <= 0 || box.getLengthZ() <= 0) {
            box = raw;
        }

        float minYaw = Float.MAX_VALUE, maxYaw = -Float.MAX_VALUE;
        float minPitch = Float.MAX_VALUE, maxPitch = -Float.MAX_VALUE;

        double[] xs = { box.minX, box.maxX };
        double[] ys = { box.minY, box.maxY };
        double[] zs = { box.minZ, box.maxZ };

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    float[] rot = to(eye, new Vec3d(x, y, z));
                    // unwrap yaw relative to where we're looking so the -180/180
                    // seam never splits the window
                    float relYaw = currentYaw + wrap(rot[0] - currentYaw);
                    minYaw = Math.min(minYaw, relYaw);
                    maxYaw = Math.max(maxYaw, relYaw);
                    minPitch = Math.min(minPitch, rot[1]);
                    maxPitch = Math.max(maxPitch, rot[1]);
                }
            }
        }
        return new float[] { minYaw, maxYaw, minPitch, maxPitch };
    }
}
