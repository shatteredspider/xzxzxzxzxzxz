package dev.sevenclient.util;

/**
 * Single-axis aim controller: a critically damped spring whose natural frequency
 * is derived per-frame from Fitts's law.
 *
 * ---------------------------------------------------------------------------
 * WHY A SPRING RATHER THAN "MOVE X% OF THE WAY THERE"
 * ---------------------------------------------------------------------------
 * A first-order lerp (`pos += error * alpha`) has no state. Its velocity is a
 * direct function of the current error, so any change in the error -- the target
 * strafing, the player nudging the mouse, a new target -- instantly changes the
 * output velocity. That discontinuity is exactly what reads as robotic, and it
 * is why the old implementation felt jittery.
 *
 * A second-order system carries VELOCITY as state. Velocity can only change
 * through acceleration, so the output is C1 continuous by construction: no
 * matter how abruptly the target moves, the crosshair's motion stays smooth.
 * That momentum is the single biggest contributor to a fluid feel.
 *
 * Critically damped specifically (zeta = 1) because it is the fastest possible
 * convergence with ZERO overshoot. Underdamped would ring around the target
 * (visible wobble); overdamped would crawl in.
 *
 *     accel = omega^2 * error - 2 * omega * (velocity - feedForward)
 *
 * ---------------------------------------------------------------------------
 * WHY FITTS'S LAW SETS THE FREQUENCY
 * ---------------------------------------------------------------------------
 * Fitts (1954) established that human movement time to a target scales with
 * log2(2D/W) -- distance over target width. So instead of exposing a "speed"
 * slider, we ask: how long would a human plausibly take to cover this angular
 * distance to a target of this angular size? That gives a movement time T, and
 * omega = 5.8/T is the frequency whose critically damped step response settles
 * within ~2% at exactly t = T.
 *
 * The result is genuinely dynamic: a far target gets a long, sweeping,
 * bell-shaped approach; a near one gets a quick settle. It always arrives,
 * because T shrinks as the remaining distance shrinks.
 *
 * A critically damped step response is (1 + omega*t)*e^(-omega*t), whose
 * velocity curve is the bell shape that Flash & Hogan (1985) showed real human
 * reaching movements follow. We get the minimum-jerk velocity profile for free
 * rather than having to schedule a 5th-order polynomial and re-plan it every
 * frame as the target moves.
 *
 * ---------------------------------------------------------------------------
 * FEED-FORWARD
 * ---------------------------------------------------------------------------
 * The damping term is measured against `feedForward` (the target's angular
 * velocity) rather than against zero. A plain spring settles at velocity zero,
 * which means it permanently trails a moving target by a constant error. Damping
 * toward the target's own angular rate makes the steady state "matching the
 * target's speed" instead of "stopped", which eliminates that lag. This is what
 * Vape exposes as Strafe Increase, done as a control term instead of a fudge.
 */
public final class AimSolver {

    /** Settling constant: critically damped response is within ~2% at omega*t = 5.8. */
    private static final double SETTLE = 5.8d;

    private double velocity;

    public void reset() {
        velocity = 0.0d;
    }

    public double velocity() {
        return velocity;
    }

    /**
     * Shannon-form Fitts index of difficulty -> movement time in seconds.
     *
     * @param distanceDeg angular distance still to cover
     * @param widthDeg    angular width of the target (its hitbox, not a point)
     * @param a           intercept: irreducible reaction/initiation cost
     * @param b           slope: seconds per bit of difficulty (the "smoothness" knob)
     */
    public static double fittsTime(double distanceDeg, double widthDeg, double a, double b) {
        double w = Math.max(0.35d, widthDeg);
        double id = Math.log(2.0d * Math.abs(distanceDeg) / w + 1.0d) / 0.6931471805599453d;
        return a + b * id;
    }

    /**
     * Advance one frame.
     *
     * @param error       signed angular error, degrees
     * @param movementTime Fitts movement time, seconds
     * @param feedForward target's angular velocity, deg/s
     * @param external    rate the error is ALREADY closing from other sources
     *                    (the player's own mouse), deg/s
     * @param maxRate     hard cap on turn rate, deg/s
     * @param gain        0..1 authority (reaction ramp * mouse yield * strength)
     * @param dt          real seconds since last frame
     * @return signed rotation delta to apply this frame, degrees
     */
    public double step(double error, double movementTime, double feedForward,
                       double external, double maxRate, double gain, float dt) {

        double omega = SETTLE / Math.max(0.035d, movementTime);

        /*
         * Damping is measured against the TOTAL closing rate, not just our own.
         *
         * `external` is the rate at which something else is already closing the
         * error -- overwhelmingly the player's own mouse. Without this term the
         * spring watches the error shrink during a hard flick, credits itself,
         * and winds up velocity it never earned. The player then stops dead and
         * that borrowed velocity throws the crosshair straight past the target.
         * That is the overflick, and no amount of clamping the boost fixes it,
         * because the energy came from mis-attributed progress.
         *
         * Including it makes the controller cooperative: while the player is
         * flicking hard toward the target the spring contributes almost nothing,
         * and it takes over smoothly the instant they stop. It is also the
         * cleanest possible answer to "don't fight my mouse" -- there is nothing
         * to fight when both are accounted for in the same equation.
         */
        double accel = omega * omega * error
                     - 2.0d * omega * (velocity + external - feedForward);
        velocity += accel * dt;

        /*
         * OPPOSING VELOCITY BLEED -- continuous, and it replaces two discrete
         * guards that used to live here (a x0.30 cut on error sign-flip, and a
         * hard velocity = 0 when opposing).
         *
         * Both of those were VALUE jumps in the velocity state. Orbiting a player
         * flips the error sign constantly, so they fired ~6 times a second, and
         * every one of them was a step in the output derivative. That is what read
         * as bounciness -- the crosshair looked nervous because the controller
         * kept getting yanked to a new velocity.
         *
         * A single exponential bleed does the same job continuously. Rate chosen
         * by sweep: 250/s keeps overflick at 6.15 deg (the discrete pair managed
         * 6.63) while total drag against the player's own motion is 1.25 deg
         * across an entire flick, i.e. imperceptible.
         *
         * It also subsumes the crossing case for free: passing the target flips
         * the error sign, which makes our velocity opposing, which bleeds it.
         */
        if (error != 0.0d && velocity != 0.0d
                && Math.signum(velocity) != Math.signum(error)) {
            velocity *= Math.exp(-250.0d * dt);
        }

        /*
         * Bleed stored velocity while yielding to the player's mouse. Without
         * this, holding a flick would let the spring wind up and then lurch the
         * instant the player stopped -- the assist would feel like it was
         * fighting, then punishing. Exponential in dt so it is framerate
         * independent.
         */
        if (gain < 0.999d) {
            velocity *= Math.exp(-(1.0d - gain) * 9.0d * dt);
        }

        if (velocity > maxRate) velocity = maxRate;
        if (velocity < -maxRate) velocity = -maxRate;

        double delta = velocity * dt * gain;

        // never step past the target inside a single frame
        double cap = Math.abs(error) + Math.abs(feedForward * dt) + 0.02d;
        if (delta > cap) delta = cap;
        if (delta < -cap) delta = -cap;
        return delta;
    }
}
