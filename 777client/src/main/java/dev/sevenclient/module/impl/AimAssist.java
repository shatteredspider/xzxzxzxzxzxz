package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.ModeSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.module.setting.Setting;
import dev.sevenclient.module.setting.StringSetting;
import dev.sevenclient.util.AimSolver;
import dev.sevenclient.util.Diagnostics;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.RotationSync;
import dev.sevenclient.util.Rotations;
import dev.sevenclient.util.SmoothNoise;
import dev.sevenclient.util.TargetUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Universal adaptive aim assistance. One knob: Smoothness.
 *
 * ---------------------------------------------------------------------------
 * TWO-TERM OUTPUT: TRACKING vs CORRECTION
 * ---------------------------------------------------------------------------
 * The single most important structural idea here. The output is the sum of two
 * separately gated terms:
 *
 *   TRACKING   = the target's angular velocity, gated only by acquisition.
 *   CORRECTION = a spring closing residual error, gated by the calm zone.
 *
 * Keeping a crosshair on a strafing opponent is not a correction -- it is
 * maintenance. Gating it behind the calm zone is what made slow strafes feel
 * laggy: authority fell to near zero on target, so the client stopped following
 * them at all, drifted to the hitbox edge, and only then produced a visible
 * catch-up jerk. Splitting the terms means it drifts WITH them while still
 * sitting perfectly still when nobody is moving.
 *
 * ---------------------------------------------------------------------------
 * SUB-TICK TARGET INTERPOLATION
 * ---------------------------------------------------------------------------
 * Entity positions update at 20Hz. Reading getBoundingBox() directly means the
 * error signal is a 20Hz staircase: at 117fps that is ~6 identical frames then a
 * jump, and the spring faithfully reproduces every jump. Building the hitbox
 * from getLerpedPos(tickProgress) instead gives a continuous error signal, which
 * removes a jitter source that no amount of output smoothing could fix.
 */
public class AimAssist extends Module {

    // ------------------------------------------------------------ the one knob
    private final NumberSetting smoothness = reg(new NumberSetting("Smoothness", 55, 0, 100, 1));

    // ------------------------------------------------------------- targeting
    private final NumberSetting range = reg(new NumberSetting("Max Range", 4.2, 1.0, 8.0, 0.1));
    private final ModeSetting activation = reg(new ModeSetting("Activation", "On Attack",
            "On Attack", "Always", "Attack Or Bind"));
    private final BoolSetting enemiesOnly = reg(new BoolSetting("Enemies Only", false));
    private final BoolSetting playersOnly = reg(new BoolSetting("Players Only", false));
    private final BoolSetting weaponsOnly = reg(new BoolSetting("Limit To Weapons", true));
    private final ModeSetting weaponFilter = reg(new ModeSetting("Weapon Filter", "Sword + Axe",
            "Sword + Axe", "Sword", "Axe", "Any Weapon", "Custom"));
    private final StringSetting customItems = reg(new StringSetting("Custom Keywords", "sword,axe,mace"));

    // ------------------------------------------------------------- advanced
    private final BoolSetting advanced = reg(new BoolSetting("Advanced", false));
    private final NumberSetting strength    = reg(new NumberSetting("Strength", 100, 0, 100, 1));
    private final NumberSetting tracking     = reg(new NumberSetting("Tracking", 85, 0, 100, 1));
    private final NumberSetting flickAssist = reg(new NumberSetting("Flick Assist", 100, 0, 200, 5));
    private final NumberSetting spinAssist  = reg(new NumberSetting("Spin Assist", 100, 0, 200, 5));
    private final NumberSetting pathVariety = reg(new NumberSetting("Path Variety", 60, 0, 100, 5));
    private final NumberSetting calmScale   = reg(new NumberSetting("Calm Zone", 100, 25, 250, 5));
    private final NumberSetting verticalRatio = reg(new NumberSetting("Vertical Ratio", 75, 10, 100, 1));
    private final NumberSetting tremorScale = reg(new NumberSetting("Tremor", 100, 0, 200, 5));
    private final NumberSetting inset       = reg(new NumberSetting("Hitbox Inset", 0.05, 0.0, 0.35, 0.01));
    private final BoolSetting stopInHitbox  = reg(new BoolSetting("Stop In Hitbox", true));
    private final BoolSetting subTick       = reg(new BoolSetting("Sub-Tick Target", true));
    private final BoolSetting tickSnap      = reg(new BoolSetting("Tick GCD Snap", true));

    // ------------------------------------------------------------------ state
    private final AimSolver yawSolver = new AimSolver();
    private final AimSolver pitchSolver = new AimSolver();
    private final SmoothNoise noiseYaw = new SmoothNoise(0x51ED2701L);
    private final SmoothNoise noisePitch = new SmoothNoise(0x7F4A7C15L);
    private final Random rng = new Random();

    private double lastYaw = Double.NaN, lastPitch = Double.NaN;
    /** Smoothed human input VELOCITY as a vector, deg/s. Not a per-frame delta. */
    private double humanVelYaw, humanVelPitch, humanRate;
    /** Fast-tracking copy used ONLY as the solver's closing-rate term. */
    private double humanFastYaw, humanFastPitch;
    private double spin, authEase, authSmooth;
    private double ffYaw, ffPitch;
    private double prevWinYaw = Double.NaN, prevWinPitch = Double.NaN;
    /** Continuous 0..1 hysteresis state. Was a boolean, which stepped the output. */
    private double calmness = 1.0d;
    private boolean spinCommit = false;

    private int targetId = -1;
    private long acquiredAt;
    private long reactionMs;
    private double arcSign = 1, arcAmount = 0.5, bJitter = 1.0, vJitter = 1.0;

    private final Deque<double[]> history = new ArrayDeque<>();
    private double clock;

    public AimAssist() {
        super("AimAssist", "Universal adaptive tracking. One knob: Smoothness.", Category.COMBAT);
        customItems.visibleWhen(() -> weaponFilter.is("Custom"));
        for (Setting<?> s : new Setting<?>[] {
                strength, tracking, flickAssist, spinAssist, pathVariety, calmScale,
                verticalRatio, tremorScale, inset, subTick, tickSnap }) {
            s.visibleWhen(advanced::is);
        }
        registerBindSettings();
    }

    @Override
    public void onEnable() {
        RotationSync.reset();
    }

    @Override
    public void onDisable() {
        yawSolver.reset();
        pitchSolver.reset();
        lastYaw = lastPitch = Double.NaN;
        prevWinYaw = prevWinPitch = Double.NaN;
        ffYaw = ffPitch = 0;
        humanVelYaw = humanVelPitch = humanRate = 0;
        humanFastYaw = humanFastPitch = 0;
        spin = authEase = authSmooth = 0;
        calmness = 1.0d;
        spinCommit = false;
        targetId = -1;
        history.clear();
        RotationSync.reset();
    }

    // ------------------------------------------------------- derived profile

    private double s() { return smoothness.val() / 100.0d; }

    private double fittsB()   { return MathHelper.lerp(s(), 0.050d, 0.200d) * bJitter; }
    private double deadzone() { return MathHelper.lerp(s(), 0.40d, 1.10d) * (calmScale.val() / 100.0d); }
    private double lagMs()    { return MathHelper.lerp(s(), 12.0d, 55.0d); }
    private double baseRate() { return MathHelper.lerp(s(), 520.0d, 300.0d); }
    private double tremorAmp(){ return MathHelper.lerp(s(), 0.035d, 0.11d) * (tremorScale.val() / 100.0d); }
    private double reactLo()  { return MathHelper.lerp(s(), 18.0d, 60.0d); }
    private double reactHi()  { return MathHelper.lerp(s(), 70.0d, 150.0d); }

    // ---------------------------------------------------------------- frame

    @Override
    public void onFrame(float dt) {
        if (mc.player == null || mc.world == null) return;
        clock += dt;

        double yaw = mc.player.getYaw();
        double pitch = mc.player.getPitch();

        /*
         * Human input as a SMOOTHED VELOCITY VECTOR.
         *
         * This was the flick jitter. Mouse input is quantised, so on any given
         * frame the player may have moved 0 counts. Deriving alignment from a raw
         * per-frame delta therefore produced |v|=0 on those frames -> alignment 0
         * -> boost 1, alternating with boost 3+ on frames that did have movement.
         * The authority flickered at frame rate, and the exponential amplified it.
         *
         * Smoothing the vector first makes both the rate and the direction
         * continuous, so the boost can no longer flicker.
         */
        if (!Double.isNaN(lastYaw)) {
            double instYaw = Rotations.wrap((float) (yaw - lastYaw)) / Math.max(1e-4d, dt);
            double instPitch = (pitch - lastPitch) / Math.max(1e-4d, dt);

            // slow (16/s): alignment, flick magnitude, yield. Needs to be stable.
            double aSlow = 1.0d - Math.exp(-16.0d * dt);
            humanVelYaw += (instYaw - humanVelYaw) * aSlow;
            humanVelPitch += (instPitch - humanVelPitch) * aSlow;

            /*
             * Fast (60/s ~ 17ms): the closing-rate term ONLY.
             *
             * This needs two time constants, not one. The slow estimator lags a
             * flick's onset by ~60ms, and during that window the solver could not
             * yet see that the player was closing the gap -- so it wound up full
             * velocity and then overshot once they stopped. Measured, moving just
             * this term from 16/s to 60/s cut overflick from 12.6 to 6.6 deg.
             *
             * It stays an EMA rather than the raw delta because mouse input is
             * quantised: at low speeds the instantaneous rate alternates between
             * zero and one step, and feeding that into the damping term would
             * inject jitter exactly where slow tracking needs to be smoothest.
             */
            double aFast = 1.0d - Math.exp(-60.0d * dt);
            humanFastYaw += (instYaw - humanFastYaw) * aFast;
            humanFastPitch += (instPitch - humanFastPitch) * aFast;
        }
        humanRate = Math.hypot(humanVelYaw, humanVelPitch);

        double spinWant = MathHelper.clamp((humanRate - 260.0d) / 380.0d, 0.0d, 1.0d);
        spin += (spinWant - spin) * (1.0d - Math.exp(-5.0d * dt));

        RotationSync.enabled = tickSnap.is();
        Diagnostics.aimSpin = spin;

        if (!active()) { relax(dt); return; }
        if (weaponsOnly.is() && !weaponOk(mc.player.getMainHandStack())) {
            Diagnostics.aimTarget = "held item rejected by weapon filter";
            relax(dt);
            return;
        }
        LivingEntity target = TargetUtil.find(range.val(), enemiesOnly.is(), playersOnly.is());
        if (target == null) {
            Diagnostics.aimTarget = "none";
            relax(dt);
            return;
        }
        if (target.getId() != targetId) newAcquisition(target.getId());
        Diagnostics.aimTarget = target.getName().getString();

        // ------------------------------------------- sub-tick smoothed hitbox
        float progress = 0f;
        Box full = target.getBoundingBox();
        if (subTick.is()) {
            try {
                progress = mc.getRenderTickCounter().getTickProgress(false);
                Vec3d feetNow = new Vec3d(target.getX(), target.getY(), target.getZ());
                full = full.offset(target.getLerpedPos(progress).subtract(feetNow));
            } catch (Throwable ignored) {
                // fall back to the un-interpolated box
            }
        }

        /*
         * FULL HITBOX WINDOW, SHOULDER-WEIGHTED ACQUISITION.
         *
         * The vertical trim to the upper torso is gone. It solved leg-aiming, but
         * it shrank the window, and a smaller window means the crosshair falls
         * OUTSIDE it far more often -- which at point blank, where the box
         * subtends a huge pitch range, meant constantly re-entering the active
         * state instead of resting.
         *
         * Stop In Hitbox makes the trim unnecessary. Anywhere on the hitbox is a
         * landing hit, so the generous window is the right one: it maximises the
         * time spent completely still. Legs are still not a destination, because
         * the shoulder bias below only applies while OUTSIDE the window -- so we
         * always travel toward the shoulder, we just do not care where on the body
         * we happen to already be.
         */
        double bh = full.getLengthY();
        Box box = full;
        double shoulderY = full.minY + bh * 0.78d;

        Vec3d centre = box.getCenter();
        long now = System.currentTimeMillis();
        history.addLast(new double[] { now, centre.x, centre.y, centre.z });
        while (history.size() > 80) history.removeFirst();
        Vec3d lagCentre = sampleLagged(now - (long) lagMs(), centre);
        Vec3d shift = lagCentre.subtract(centre);
        Box aimBox = box.offset(shift);
        Vec3d shoulder = new Vec3d(centre.x + shift.x, shoulderY + shift.y, centre.z + shift.z);

        /*
         * Our OWN eye position must be interpolated too. getEyePos() steps at
         * 20Hz, so while sprinting the origin of every angle we compute jumped
         * once per tick -- that is the shake that only showed up while moving
         * fast, and no amount of output smoothing could remove it because it was
         * in the input.
         */
        Vec3d eye = subTick.is() ? mc.player.getCameraPosVec(progress) : mc.player.getEyePos();

        float[] win = Rotations.angularWindow(eye, aimBox, inset.val(), (float) yaw);
        double loYaw = Math.min(win[0], win[1]), hiYaw = Math.max(win[0], win[1]);
        double loPitch = Math.min(win[2], win[3]), hiPitch = Math.max(win[2], win[3]);

        double dist = eye.distanceTo(aimBox.getCenter());
        double centreBias = MathHelper.clamp((3.2d - dist) / 2.4d, 0.0d, 0.9d);
        double centreYaw = (loYaw + hiYaw) * 0.5d;
        double shoulderPitch = MathHelper.clamp((double) Rotations.to(eye, shoulder)[1],
                loPitch, hiPitch);

        boolean inside = yaw >= loYaw && yaw <= hiYaw && pitch >= loPitch && pitch <= hiPitch;

        /*
         * CENTRE BIAS APPLIES ONLY WHEN OUTSIDE THE WINDOW.
         *
         * The previous version blended toward the box centre continuously,
         * weighted by proximity. That was backwards and it is what made close
         * range WORSE: the bearing to a nearby target's centre swings very fast
         * when you strafe, so biasing toward it created a permanently moving goal
         * and therefore permanent correction. Up close the window is enormous and
         * the crosshair is nearly always inside it -- which should mean perfect
         * stillness, and did, until I started chasing the centre.
         *
         * Inside the window the error is exactly zero again. The centre/shoulder
         * bias now only shapes WHERE we arrive when we actually have to move, so
         * we stop comfortably inside the hitbox instead of on its boundary.
         */
        double wantYaw, wantPitch;
        if (inside) {
            wantYaw = yaw;
            wantPitch = pitch;
        } else {
            wantYaw = MathHelper.lerp(0.45d, MathHelper.clamp(yaw, loYaw, hiYaw), centreYaw);
            wantPitch = MathHelper.lerp(0.60d, MathHelper.clamp(pitch, loPitch, hiPitch),
                    shoulderPitch);
        }

        double errYaw = Rotations.wrap((float) (wantYaw - yaw));
        double errPitch = wantPitch - pitch;

        /*
         * Hard stop inside the hitbox. The window clamp already yields zero error
         * here, but this guarantees nothing at all is emitted -- no tremor, no
         * residual solver velocity, no feed-forward drift.
         */
        if (stopInHitbox.is() && inside) {
            yawSolver.reset();
            pitchSolver.reset();
            double d0 = Math.exp(-9.0d * dt);
            authEase *= d0;
            authSmooth *= d0;
            Diagnostics.aimError = 0.0d;
            Diagnostics.aimAuthority = 0.0d;
            Diagnostics.aimTarget = target.getName().getString() + "  (in hitbox, holding)";
            lastYaw = yaw;
            lastPitch = pitch;
            return;
        }
        /*
         * COMMITTED SPIN DIRECTION.
         *
         * The error is normally the shortest angular path, which is right almost
         * always -- but mid-360 with the target nearly behind you, "shortest" can
         * point backwards against the way you are already turning. The assist then
         * hauls you back instead of carrying the spin around, which is the
         * "flicks back instead of doing a 360" case.
         *
         * When a spin is genuinely committed, take the path that CONTINUES it.
         * Latched with hysteresis (engage past 100 deg, release under 45) so it
         * cannot oscillate between the two choices.
         */
        double turnDir = Math.signum(humanVelYaw);
        // never at melee range: with the target 2 blocks away a 360 is never the
        // shorter intent, and close-range bearings swing fast enough to trip it
        if (dist > 3.0d && spin > 0.30d && Math.abs(humanVelYaw) > 150.0d && turnDir != 0.0d) {
            if (!spinCommit && Math.abs(errYaw) > 100.0d && Math.signum(errYaw) != turnDir
                    && Math.abs(errYaw + 360.0d * turnDir) < 260.0d) {
                spinCommit = true;
            }
            if (spinCommit && Math.signum(errYaw) != turnDir) {
                errYaw += 360.0d * turnDir;
            }
        }
        if (Math.abs(errYaw) < 45.0d || spin < 0.12d) spinCommit = false;

        double errMag = Math.hypot(errYaw, errPitch);
        Diagnostics.aimError = errMag;

        // ------------------------------------------------------ path variety
        if (errMag > 1.5d && pathVariety.val() > 0) {
            double px = -errPitch / errMag, py = errYaw / errMag;
            // fade in over 1.5..4 deg rather than appearing at a hard threshold
            double arcFade = MathHelper.clamp((errMag - 1.5d) / 2.5d, 0.0d, 1.0d);
            double bias = arcSign * arcAmount * Math.min(errMag, 25.0d)
                        * 0.10d * (pathVariety.val() / 100.0d) * arcFade;
            errYaw += px * bias;
            errPitch += py * bias;
            errMag = Math.hypot(errYaw, errPitch);
        }

        // ------------------------------------------------------- feed-forward
        double winYaw = (loYaw + hiYaw) * 0.5d, winPitch = (loPitch + hiPitch) * 0.5d;
        if (!Double.isNaN(prevWinYaw)) {
            double rawY = Rotations.wrap((float) (winYaw - prevWinYaw)) / Math.max(1e-4d, dt);
            double rawP = (winPitch - prevWinPitch) / Math.max(1e-4d, dt);
            double a = 1.0d - Math.exp(-9.0d * dt);
            ffYaw += (rawY - ffYaw) * a;
            ffPitch += (rawP - ffPitch) * a;
        }
        prevWinYaw = winYaw;
        prevWinPitch = winPitch;

        // ------------------------------------------- calm zone, with hysteresis
        /*
         * CONTINUOUS hysteresis. The previous version used a boolean Schmitt
         * trigger, so `outside` jumped from 0 to ~0.35 the instant it flipped --
         * measured at up to a 0.52 step out of a 0..1 range, ~3.3 times per second
         * while orbiting. Hysteresis is still wanted (it stops chatter at the
         * boundary), but it belongs in the THRESHOLD, which can move smoothly,
         * not in the output, which cannot.
         */
        double dz = Math.max(0.05d, deadzone());
        double dzEff = dz * (1.0d + 0.6d * calmness);
        double outside = MathHelper.clamp((errMag - dzEff) / (dz * 1.5d), 0.0d, 1.0d);
        double wantAuth = outside * outside * (3.0d - 2.0d * outside);
        calmness += ((1.0d - wantAuth) - calmness) * (1.0d - Math.exp(-4.0d * dt));

        // Release slowed 16 -> 9: dropping authority faster than it builds is what
        // made it look like it kept flinching away from the target.
        double easeRate = wantAuth > authEase ? 7.5d : 9.0d;
        authEase += (wantAuth - authEase) * (1.0d - Math.exp(-easeRate * dt));

        // ------------------------------------------------------- acquisition gate
        double since = now - acquiredAt;
        double acquire;
        if (since < reactionMs) {
            acquire = 0.0d;
        } else {
            double r = Math.min(1.0d, (since - reactionMs) / 110.0d);
            acquire = r * r * (3.0d - 2.0d * r);
        }

        // ---------------------------------------------------- directional response
        double align = 0.0d;
        if (humanRate > 1.0d && errMag > 1e-4d) {
            align = (humanVelYaw * errYaw + humanVelPitch * errPitch)
                  / (humanRate * errMag);
        }
        double flick = MathHelper.clamp(humanRate / 650.0d, 0.0d, 1.0d);
        Diagnostics.aimFlick = flick;

        /*
         * BOUNDED, SATURATING boost. The previous form was 1 + (e^(2.4f) - 1),
         * which reaches ~11x at full flick -- that is what made 360s snap on
         * instantly and overshoot. This keeps the same fast initial rise (still
         * exponential in shape) but saturates at a hard ceiling.
         */
        /*
         * URGENCY vs AUTHORITY -- these must not be the same number.
         *
         * The bug this replaces: the flick boost was multiplied into `gain`, and
         * the solver computes `delta = velocity * dt * gain`. A gain above 1 does
         * not make the controller react faster, it just scales its output past
         * what the spring asked for -- which destroys the critical-damping
         * guarantee outright. With an 11x ceiling the crosshair was being thrown
         * ~11x further per frame than the solver intended. THAT was the overflick,
         * not the size of the boost.
         *
         * Split in two:
         *   AUTHORITY, clamped to [0,1] -- a pure fade. Calm zone, reaction ramp,
         *   and yielding to opposed mouse movement all live here.
         *   URGENCY, >= 1 -- divides the Fitts movement time, raising the spring's
         *   natural frequency. "React faster", not "move further".
         *
         * A critically damped system does not overshoot at ANY frequency, so
         * urgency can be large and overshoot is still structurally impossible.
         */
        double maxBoost = 1.0d + 2.5d * (flickAssist.val() / 100.0d);
        double flickUrgency = 1.0d + (maxBoost - 1.0d)
                            * (1.0d - Math.exp(-2.8d * flick)) * Math.max(0.0d, align);
        double spinUrgency = 1.0d + 0.70d * (spinAssist.val() / 100.0d) * spin;
        /*
         * Taper urgency by remaining error. Flick assist exists to cover
         * DISTANCE; applying it during the final approach just makes the settle
         * hot and invites overshoot. Near the target urgency returns to 1, i.e.
         * plain Fitts timing.
         */
        double urgencyScale = Math.min(1.0d, errMag / 6.0d);
        double urgency = 1.0d + (flickUrgency * spinUrgency - 1.0d) * urgencyScale;

        double yieldF = 1.0d / (1.0d + (humanRate / 300.0d) * (humanRate / 300.0d));
        double yieldMul = MathHelper.lerp(Math.max(0.0d, -align), 1.0d, yieldF);

        /*
         * Urgency must NOT touch the rate ceiling -- that was a v10 mistake, and
         * it is what regressed 360s.
         *
         * Urgency already raises the spring's natural frequency, which is the
         * whole mechanism for "react faster". Multiplying the ceiling by it too
         * applied the same intent twice, and stacked on the spin multiplier the
         * cap reached 1704 deg/s -- roughly 4.5x baseline. The assist was then
         * permitted to perform the spin rather than assist it, and blew ~53 deg
         * past the target on a hard 360. Removing this single factor took that to
         * 1.8 deg.
         *
         * Absolute ceiling on top, because no combination of settings should let
         * the assist out-turn a human wrist.
         */
        double rate = Math.min(700.0d,
                baseRate() * (1.0d + 1.6d * (spinAssist.val() / 100.0d) * spin));

        double width = Math.min(Math.abs(hiYaw - loYaw), Math.abs(hiPitch - loPitch));
        // urgency shortens the movement time -> higher omega -> faster, still ZD
        double moveTime = AimSolver.fittsTime(errMag, width, 0.045d, fittsB()) / urgency;

        double auth = MathHelper.clamp(
                (strength.val() / 100.0d) * authEase * acquire * yieldMul, 0.0d, 1.0d);

        /*
         * COVERAGE -- stand down by however much the player is already doing.
         *
         * This is what fixed 360 overflick. Mid-spin the assist was pushing
         * forward to complete the turn while the player's wrist was ALSO
         * completing it, and the two added up: a clean 360 overshot by ~77 deg.
         *
         * Fitts gives a planned closing rate (errMag / moveTime). Compare it to
         * the rate the player is already supplying toward the target. If they are
         * meeting or beating the plan, there is nothing to assist, so authority
         * goes to zero. During a hard spin coverage saturates at 1 and the assist
         * effectively switches off, then fades back in as the wrist slows.
         *
         * Complements the solver's closing-rate term: that one stops the spring
         * accumulating unearned VELOCITY, this one stops it claiming unearned
         * AUTHORITY. Both are the same principle at different layers.
         */
        double needed = errMag / Math.max(0.05d, moveTime);
        double supplied = humanRate * Math.max(0.0d, align);
        double coverage = Math.min(1.0d, supplied / Math.max(1.0d, needed));
        auth *= 1.0d - coverage;
        Diagnostics.aimCoverage = coverage;

        /*
         * Far-field taper on top. The assist finishes approaches; it does not
         * execute gross movements. At 180 deg out the player's wrist owns the
         * sweep, so fade to 15% authority there.
         */
        auth *= 1.0d - Math.min(0.85d, Math.max(0.0d, (errMag - 60.0d) / 120.0d));

        // light slew limit: authority itself can never step
        double maxAuthStep = 9.0d * dt;
        authSmooth += MathHelper.clamp(auth - authSmooth, -maxAuthStep, maxAuthStep);
        auth = MathHelper.clamp(authSmooth, 0.0d, 1.0d);
        Diagnostics.aimAuthority = auth;
        Diagnostics.aimUrgency = urgency;

        // ---------------------------------------------------------------- solve
        double vr = (verticalRatio.val() / 100.0d) * vJitter;

        // CORRECTION: gated by the calm zone (feedForward deliberately 0 here)
        double corrYaw = yawSolver.step(errYaw, moveTime, 0.0d, humanFastYaw, rate, auth, dt);
        double corrPitch = pitchSolver.step(errPitch, moveTime, 0.0d, humanFastPitch,
                rate * vr, auth * vr, dt);

        // TRACKING: match their drift. Gated only by acquisition and mouse yield,
        // NOT by the calm zone -- this is the slow-strafe lag fix.
        double trackGain = (tracking.val() / 100.0d) * acquire
                         * MathHelper.lerp(Math.max(0.0d, -align), 1.0d, yieldF);
        double trackYaw = ffYaw * dt * trackGain;
        double trackPitch = ffPitch * dt * trackGain * vr;

        double dYaw = corrYaw + trackYaw;
        double dPitch = corrPitch + trackPitch;

        // -------------------------------------------------------------- tremor
        // scaled by authority, so a calm crosshair is genuinely motionless
        double engage = Math.min(1.0d, errMag / 8.0d);
        // also fades as the target fills the screen: micro-tremor at point blank
        // is pure noise, there is nothing left to correct
        double amp = tremorAmp() * (0.15d + 0.85d * engage) * Math.min(1.0d, auth)
                   * (1.0d - 0.7d * centreBias);
        if (amp > 0) {
            // slower drift reads as a steady hand; fast noise reads as nerves
            dYaw += noiseYaw.fbm(clock * 1.25d) * amp * dt * 60.0d;
            dPitch += noisePitch.fbm(clock * 1.05d) * amp * dt * 60.0d * 0.7d;
        }

        // combined cap: never pass the hitbox edge within one frame
        double capYaw = Math.abs(errYaw) + Math.abs(ffYaw * dt) + 0.03d;
        double capPitch = Math.abs(errPitch) + Math.abs(ffPitch * dt) + 0.03d;
        dYaw = MathHelper.clamp(dYaw, -capYaw, capYaw);
        dPitch = MathHelper.clamp(dPitch, -capPitch, capPitch);

        if (dYaw == 0.0d && dPitch == 0.0d) {
            lastYaw = yaw;
            lastPitch = pitch;
            return;
        }

        float newYaw = (float) (yaw + dYaw);
        float newPitch = MathHelper.clamp((float) (pitch + dPitch), -90f, 90f);
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.setHeadYaw(newYaw);
        RotationSync.dirty = true;

        Diagnostics.lastYawStep = dYaw;
        lastYaw = newYaw;
        lastPitch = newPitch;
    }

    private void newAcquisition(int id) {
        targetId = id;
        acquiredAt = System.currentTimeMillis();
        reactionMs = HumanRandom.reaction(reactLo(), reactHi());
        history.clear();
        prevWinYaw = prevWinPitch = Double.NaN;
        yawSolver.reset();
        pitchSolver.reset();
        authEase = 0.0d;
        authSmooth = 0.0d;
        calmness = 1.0d;
        spinCommit = false;

        arcSign = rng.nextBoolean() ? 1.0d : -1.0d;
        arcAmount = 0.25d + rng.nextDouble() * 0.75d;
        bJitter = 0.88d + rng.nextDouble() * 0.26d;
        vJitter = 0.92d + rng.nextDouble() * 0.16d;
    }

    private void relax(float dt) {
        double decay = Math.exp(-6.0d * dt);
        yawSolver.reset();
        pitchSolver.reset();
        ffYaw *= decay;
        ffPitch *= decay;
        authEase *= decay;
        authSmooth *= decay;
        calmness = 1.0d;
        spinCommit = false;
        targetId = -1;
        history.clear();
        prevWinYaw = prevWinPitch = Double.NaN;
        Diagnostics.aimAuthority = 0.0d;
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    private Vec3d sampleLagged(long when, Vec3d fallback) {
        if (history.size() < 2) return fallback;
        double[] older = null, newer = null;
        for (double[] sm : history) {
            if (sm[0] <= when) older = sm;
            else { newer = sm; break; }
        }
        if (older == null) {
            double[] first = history.peekFirst();
            return new Vec3d(first[1], first[2], first[3]);
        }
        if (newer == null) return fallback;
        double span = newer[0] - older[0];
        double t = span <= 0 ? 0.0d : (when - older[0]) / span;
        return new Vec3d(MathHelper.lerp(t, older[1], newer[1]),
                         MathHelper.lerp(t, older[2], newer[2]),
                         MathHelper.lerp(t, older[3], newer[3]));
    }

    private boolean active() {
        boolean attacking = mc.options.attackKey.isPressed();
        return switch (activation.get()) {
            case "Always" -> true;
            case "Attack Or Bind" -> attacking || bind.down();
            default -> attacking;
        };
    }

    private boolean weaponOk(ItemStack held) {
        return switch (weaponFilter.get()) {
            case "Sword" -> TargetUtil.isSword(held);
            case "Axe" -> TargetUtil.isAxe(held);
            case "Any Weapon" -> TargetUtil.isWeapon(held, null);
            case "Custom" -> TargetUtil.isWeapon(held, customItems.get());
            default -> TargetUtil.isSword(held) || TargetUtil.isAxe(held);
        };
    }
}
