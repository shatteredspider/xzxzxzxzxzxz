package dev.sevenclient.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Aligns rotation to the mouse-step lattice ONCE PER TICK, at packet time.
 *
 * ---------------------------------------------------------------------------
 * THIS IS THE FIX FOR THE REMAINING "SKIPS"
 * ---------------------------------------------------------------------------
 * The old output stage quantised every FRAME. That was solving the wrong
 * problem: the server never observes intermediate frames. Rotation reaches it
 * only inside PlayerMoveC2S, emitted once per tick from
 * ClientPlayerEntity#sendMovementPackets. Between two ticks the client might
 * render 1 frame or 12 -- none of those values are transmitted.
 *
 * So per-frame quantisation bought nothing and cost everything: at 117fps with a
 * ~0.15 deg step, a smooth 100 deg/s sweep needs 0.85 steps per frame, and the
 * only representable options are 0 or 1. The visible result is stepping, which
 * is what was left of the jitter.
 *
 * Now the aim runs in continuous float for perfectly smooth rendering, and the
 * rotation is snapped to the lattice here, immediately before the packet is
 * built. Every value the server sees is still an exact whole number of mouse
 * counts from the previous one -- the property that matters is preserved
 * exactly, while the visual path is fully continuous.
 *
 * The correction applied at tick time is at most half a step (~0.075 deg), which
 * is far below anything perceptible.
 */
public final class RotationSync {

    private static double lastSentYaw = Double.NaN;
    private static double lastSentPitch = Double.NaN;

    /** Set by whatever wrote a non-lattice rotation this tick. */
    public static volatile boolean dirty = false;
    public static volatile boolean enabled = true;

    /**
     * Silent rotation: swapped in for the duration of packet construction only.
     *
     * The camera keeps the real rotation; the server receives this one. That is a
     * genuine client/server rotation divergence -- the one thing the rest of this
     * client deliberately avoids -- so it is opt-in per module and the offset is
     * kept small enough that the resulting hit still validates. See HitFlick.
     */
    public static volatile boolean silentActive = false;
    public static volatile float silentYaw = 0f;
    private static float restoreYaw = 0f;
    private static boolean restorePending = false;

    /** HEAD of sendMovementPackets: swap in the silent yaw. */
    public static void beginSilent(ClientPlayerEntity player) {
        restorePending = false;
        if (!silentActive || player == null) return;
        restoreYaw = player.getYaw();
        player.setYaw(silentYaw);
        player.setHeadYaw(silentYaw);
        restorePending = true;
    }

    /** RETURN of sendMovementPackets: hand the camera back its real rotation. */
    public static void endSilent(ClientPlayerEntity player) {
        if (!restorePending || player == null) return;
        restorePending = false;
        player.setYaw(restoreYaw);
        player.setHeadYaw(restoreYaw);
    }

    private RotationSync() { }

    public static void reset() {
        lastSentYaw = Double.NaN;
        lastSentPitch = Double.NaN;
        dirty = false;
    }

    /** Called from a HEAD injection on ClientPlayerEntity#sendMovementPackets. */
    public static void snapBeforeSend(ClientPlayerEntity player) {
        if (player == null) return;

        if (!enabled || !dirty) {
            // nothing of ours in the rotation: just track it so the next
            // correction is anchored to a value the server actually received
            lastSentYaw = player.getYaw();
            lastSentPitch = player.getPitch();
            return;
        }
        dirty = false;

        double step = Rotations.gcd();
        if (step <= 0.0d) return;

        if (Double.isNaN(lastSentYaw)) {
            lastSentYaw = player.getYaw();
            lastSentPitch = player.getPitch();
            return;
        }

        double dYaw = Rotations.wrap((float) (player.getYaw() - lastSentYaw));
        double dPitch = player.getPitch() - lastSentPitch;

        double qYaw = Math.round(dYaw / step) * step;
        double qPitch = Math.round(dPitch / step) * step;

        float newYaw = (float) (lastSentYaw + qYaw);
        float newPitch = MathHelper.clamp((float) (lastSentPitch + qPitch), -90f, 90f);

        player.setYaw(newYaw);
        player.setPitch(newPitch);
        player.setHeadYaw(newYaw);

        lastSentYaw = newYaw;
        lastSentPitch = newPitch;
    }
}
