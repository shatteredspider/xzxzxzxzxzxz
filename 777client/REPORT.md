# 777 Client — Development Report

**Build:** v15 · Minecraft 1.21.11 · Fabric · Java 21 · Linux + Windows
**Scope:** 46 Java files, 12 modules, ~4,900 lines
**Status:** working beta

---

## 1. Stack and versions

Verified live against `meta.fabricmc.net` and Modrinth. These are in `gradle.properties`:

```properties
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6
loader_version=0.19.5
fabric_version=0.141.6+1.21.11
```

Fabric Loom **1.17.20**, Gradle **9.x** (9.2+ required by Loom).

```bash
cd 777client && gradle build     # -> build/libs/777client-1.0.0.jar
python3 tools/check-versions.py 1.21.11   # re-derive versions any time
```

**Run the version checker before anything else if a build fails on dependency
resolution.** A version that does not exist in the repo is by far the most common
cause, and it is what broke the very first build (I shipped a `fabric_version`
that was never published).

---

## 2. The architectural decision everything rests on

**Nothing in this client sends a handcrafted packet.** Every module drives real
client state — rotation fields, keybindings, the selected hotbar slot, the use
key — and vanilla builds and sends the C2S packets itself, in its own order, with
its own sequence numbers.

This is not stylistic. Modern anticheat (Grim in particular) is a *prediction
engine*: it simulates what your client should have reported and flags divergence.
If the client genuinely performed the action, there is no divergence to find. The
entire smoothness effort is downstream of this — a fabricated packet is easier to
write and impossible to make consistent.

**One deliberate exception:** HitFlick's Silent mode. See §5.

### GCD quantisation, and where it actually belongs

Minecraft turns integer mouse counts into degrees via a fixed step:

```
step = (sensitivity * 0.6 + 0.2)^3 * 8 * 0.15
```

Every legitimate rotation delta is a whole multiple of it. Getting this right
took three attempts:

1. **Snap each frame's delta, carry the remainder.** Produced a sawtooth — several
   dead frames then one full step. Worse signature than not snapping, and it was
   most of the original "robotic" feel.
2. **Accumulate continuously, emit rounded difference.** Better, but `round()`
   flips state at half-step boundaries, so a settled crosshair dithered ±1 step
   every frame — a 240 Hz vibration while sitting on target.
3. **Snap once per tick, at packet time.** Correct. The server only ever sees
   rotation inside `PlayerMoveC2S`, emitted once per tick from
   `sendMovementPackets`. Intermediate frames are never transmitted, so
   quantising them bought nothing and cost all the smoothness.

Now: aim runs in continuous float for rendering, and `RotationSync.snapBeforeSend`
aligns to the lattice in a HEAD injection on `sendMovementPackets`. Max correction
0.054° (half a step is 0.075). Measured lattice residual: **9.4e-15** — exact.

---

## 3. Module inventory

| Module | Category | State |
|---|---|---|
| **AimAssist** | Combat | mature, see §4 |
| **HitFlick** | Combat | new, direction unverified |
| **HitSwap** | Combat | **never tested** |
| **S-Tap / Shift-Tap** | Combat | working |
| **JumpReset** | Movement | working |
| **FastXP** | Player | working |
| **EnemyMarker** | Player | working |
| **Watermark / Debug** | Render | working |
| **ClickGUI** | Render | working |

### Non-obvious implementation notes

**S-Tap / Shift-Tap** drive the real `KeyBinding`, so vanilla movement code
produces the packets. Position, velocity and sprint state stay internally
consistent — the movement genuinely happened client-side.

**HitSwap** has a *sequence guard*. `handleInputEvents` (where attacks originate)
runs BEFORE the player tick that emits the slot-update packet, so attacking on the
same tick as a swap would put the attack on the wire ahead of the slot change and
the server would resolve the hit with the old item. The guard suppresses the
attack for that one tick.

**FastXP** clears `itemUseCooldown` via an accessor mixin and drives the real use
key. Hard ceiling is **20 CPS** — `handleInputEvents` runs once per client tick.
Anything claiming more is sending raw packets.

**SlotUtil** resolves the selected-slot accessor by reflection because that API
changed shape across the 1.21 line. **MouseMixin** omits its descriptor with
`require = 0` for the same reason.

---

## 4. AimAssist — how it works

No speed slider. Speed is derived from the situation every frame. One user-facing
knob (**Smoothness**) drives Fitts slope, deadzone, reaction window, target lag,
tremor and rate ceiling together, because those are not independent choices.

### The controller

A **critically damped spring** whose natural frequency comes from **Fitts's law**:

```
accel = omega^2 * error - 2*omega*(velocity + external - feedForward)
omega = 5.8 / T,   T = a + b*log2(2D/W + 1)
```

Why each piece:

- **Second-order, not a lerp.** A lerp is stateless — output velocity is a pure
  function of current error, so anything changing the error changes velocity
  *instantly*. That discontinuity is what reads as robotic. A spring carries
  velocity as state, so motion is C1 continuous by construction.
- **Critically damped (ζ=1).** Fastest convergence with zero overshoot.
  Underdamped rings visibly; overdamped crawls.
- **Fitts sets omega.** Movement time from distance and *target width*. A far
  target gets a long sweeping approach, a near one a quick settle.
- **Free bonus:** a critically damped step response is `(1+ωt)e^(-ωt)`, whose
  velocity curve is the bell shape Flash & Hogan (1985) showed real human reaching
  follows. Minimum-jerk profile without scheduling a 5th-order polynomial.
- **`external`** is the rate the error is *already* closing from the player's own
  mouse. Without it the spring credits itself for your movement, winds up velocity
  it never earned, and your hand stopping throws that borrowed velocity past the
  target.
- **`feedForward`** is the target's angular velocity, so steady state is "matching
  their strafe" rather than "stopped and trailing".

### The two-term output

```
TRACKING   = target's angular velocity   — gated only by acquisition
CORRECTION = spring closing the error    — gated by the calm zone
```

Keeping a crosshair on a strafing opponent is *maintenance*, not correction.
Gating it behind the calm zone made slow strafes lag: authority fell to zero on
target, the client stopped following at all, drifted to the hitbox edge, then
produced a catch-up jerk.

### Authority vs urgency — keep these separate

- **Authority**, clamped [0,1] — a pure fade. Calm zone, reaction ramp, mouse yield.
- **Urgency**, ≥1 — divides the Fitts movement time, raising omega. "React
  faster", not "move further".

Multiplying a flick boost into authority scales the spring's output past what it
asked for, which destroys the damping guarantee outright. That was the 360
overflick, and capping the boost value barely helped because the mechanism was
wrong, not the number.

### Coverage — stand down by what the player already supplies

```
needed   = errMag / moveTime
supplied = humanRate * max(0, align)
auth    *= 1 - min(1, supplied / needed)
```

If you are meeting or beating the plan, there is nothing to assist. During a hard
spin this saturates and the assist effectively switches off, then fades back in as
your wrist slows. Took a clean 360 from 77° overshoot to 4.9°, and unexpectedly
took the plain flick to **0.00° with 0% drag**, settling 2.7× faster.

### Everything continuous

There are no boolean switches in the aim path. Discrete state changes are steps in
the output derivative, and while orbiting a player they fired **6 times a second**
— that was the "bouncy / afraid" feel. Removed:

- boolean `calm` Schmitt trigger → continuous `calmness` sliding the *threshold*
- `velocity *= 0.30` on error sign-flip → single exponential bleed at 250/s
- `velocity = 0` when opposing → same bleed (subsumes both cases)

Hysteresis still exists, it just lives in the threshold, which can move smoothly,
rather than the output, which cannot.

### Measured

| | before | after |
|---|---|---|
| relative roughness | 0.451 | 0.166 |
| dead frames while travelling | 64.1% | 5.0% |
| settle time 30→500 fps | varies | 0.233 → 0.248s |
| discrete jumps/sec (orbiting) | 19 | 0 |
| 360 overflick | 76.9° | 4.9° |
| static overshoot | — | 0.000° |

### Jitter sources found and killed

1. **20 Hz target stepping.** `getBoundingBox()` updates at tick rate → the error
   signal was a staircase. Fixed with `getLerpedPos(tickProgress)`. Ripple 5.47 →
   1.47 deg/s.
2. **Own eye position not interpolated.** `getEyePos()` also steps at 20 Hz, so
   while sprinting *the origin of every angle* jumped once per tick. This is why
   shake appeared only while moving fast. Fixed with `getCameraPosVec`.
3. **Alignment from a single frame.** Mouse input is quantised; on frames with 0
   counts, `|v|=0` → alignment 0 → boost 1×, alternating with 3×+. Fixed by
   smoothing the human velocity *vector*.
4. **Estimator needed two time constants.** 16/s for alignment/flick (stability),
   60/s for the closing-rate term (responsiveness). One constant cannot do both —
   the slow one lags a flick's onset by ~60ms, which is enough to wind up.

---

## 5. Known gaps and risks

### Untested
- **HitSwap** — never run. Needs an opponent with a shield. Watch the `attacks`
  counter climb; if it moves but no swap fires, the fault is shield detection, not
  the event.
- **Faints** (HitSwap's probabilistic mis-swap) — same.

### Unverified
- **HitFlick direction.** The mechanic is solid: sprint attacks apply a second
  knockback derived from the **attacker's yaw**, so flicking steers it. The
  *direction blend* is my invention — 42° lateral (now a slider), side chosen to
  push along the target's existing drift. I could not watch the reference video
  and there is no JDK in my sandbox to disassemble `PlayerEntity.attack`, so this
  needs a human eye on where opponents actually land.

### Deliberate tradeoffs
- **HitFlick Silent** swaps the yaw in only during packet construction. Your
  camera never moves; the server sees the flick. That is a genuine client/server
  rotation divergence — the one thing everything else avoids. Rotation-consistency
  checks can see it. **Real** is fully consistent.
- **Degrees above ~60** makes the hit itself hard to justify: the server resolves
  it from the rotation it was given, and anticheat validates the target was in
  view.
- **Opposing-velocity bleed at 250/s** costs ~1° of overflick versus a hard clamp,
  in exchange for zero drag and full continuity. Deliberate.

### Blind spots
- **All mixins are `require = 0`.** A mapping miss fails *silently*. `frame src`
  in the Debug HUD reveals whether `MouseMixin` applied, but there is **no
  indicator for the tick-time GCD snap or the attack mixin**. If
  `sendMovementPackets` ever stops matching, you lose lattice alignment and would
  never know. **Adding a `gcd sync` HUD line is the single highest-value small
  task left.**
- **No compiler in my environment.** Validation is static: package/path agreement,
  brace balance, every cross-class call and constant checked against declarations,
  every Minecraft/Fabric reference checked against the real 1.21.11 jars, all
  `Identifier` paths checked for legal lowercase, palette checked for stray hue.
  That catches a lot — it caught a deleted `Render2D.panel()` that would have been
  a guaranteed compile failure — but it cannot catch type errors.

---

## 6. Bugs worth remembering

**Lowercase `Identifier` paths.** `Inter-Medium.ttf` threw
`InvalidIdentifierException` inside a *static initialiser*, which makes the JVM
mark the class permanently uninitialisable — every later reference throws
`NoClassDefFoundError` instead of the real error. One capital letter took down the
HUD, then the whole GUI. There is now a build check for this.

**Missing TTF does not fall back.** An empty font resolves every character to the
missing-glyph box. `UiFont` verifies the file exists before attaching the style.

**Draw calls are the GUI budget.** SDF-antialiased corners (one fill per corner
pixel) plus per-row gradients measured **16,304 fills/frame** → 5 fps. Run-based
corners (`4r²` → `6r`) and banded gradients brought it to 1,373. Prettier corners
are worthless at 5 fps; I should have costed it before shipping.

**Simulate the right scenario.** I removed a crossing damper based on a sweep that
only measured flicks onto *stationary* targets — the damper existed for the
sweep-through case, which that scenario structurally cannot produce. Shipped a 360
regression as a result.

**Measure the right quantity.** Twice I drew a conclusion from a metric that was
answering a different question (unnormalised roughness; max deviation on a moving
target, which is dominated by lag not overshoot).

---

## 7. What I would do next, in priority order

### 1. `gcd sync` HUD indicator (small, high value)
Have `RotationSync` set a flag when `snapBeforeSend` actually runs, and surface it
next to `frame src`. Closes the last silent-failure blind spot.

### 2. Verify HitFlick empirically (small)
Run `Real` at 20 / 42 / 70 degrees and record where opponents land. If the
technique wants a different blend, only the direction math changes.

### 3. Config profiles (medium)
`ConfigManager` already round-trips everything. Named profiles plus a dropdown in
the Config view would make A/B testing aim settings trivial — which matters,
because most remaining tuning is subjective and needs fast switching.

### 4. ESP / target overlay (medium)
Boxes and a tracer to the current AimAssist target. Beyond utility this is a
**debugging tool**: seeing exactly which entity is selected and where the aim
window sits would have made several targeting bugs obvious immediately.

### 5. Ping-aware target lag (medium, speculative)
`Target Lag MS` is currently a fixed human-reaction figure. But you are already
seeing the opponent late by roughly your ping, so the *effective* lag is
`ping + setting`. On a 150ms server the assist is aiming ~200ms behind reality.
Reading the ping and subtracting part of it — or feeding it into the feed-forward
lead instead — should measurably improve tracking on remote servers. **I would
test this before assuming it helps**; over-leading looks worse than trailing.

### 6. Threat-based target selection (medium)
Selection is currently distance + angle. Vape exposes Armour / Health / Threat
modes. The genuinely useful one is *who is about to hit me* — a player sprinting
toward you with a raised weapon deserves priority over a closer one running away.
Cheap to compute from velocity dot product and held item.

### 7. Per-engagement aim signature (speculative)
Right now arc, Fitts slope and vertical ratio are randomised per acquisition.
A stronger version: persist a per-session "style" so your aim traces are
*self-consistent* across a match, rather than freshly random each engagement.
Real players have a recognisable signature; perfect per-hit independence is itself
a pattern. Low effort, unproven benefit.

### 8. Batched rendering (larger)
Everything goes through `DrawContext#fill`, one quad each. A custom
`VertexConsumer` batching the whole GUI into a single draw would cut the cost by
another large factor and remove fill-count as a design constraint entirely. Only
worth it if you want much denser UI — 1,373 fills is fine as-is.

---

## 8. Tuning cheatsheet

| Symptom | Setting | Direction |
|---|---|---|
| feels too eager / snappy | Smoothness | up |
| feels sluggish | Smoothness | down |
| fights your mouse | Yield To Mouse | up |
| fidgets while on target | Calm Zone | up |
| won't leave the hitbox to re-acquire | Stop In Hitbox | off |
| 360s overshoot | Flick Assist | down |
| too obvious on clips | Reaction Min/Max | up |

Debug HUD lines worth watching: `coverage` (should pin near 1.00 mid-spin),
`authority` (near 0 on target), `frame src` (should read `Mouse mixin`), `dt`
(stability matters, not the value).
