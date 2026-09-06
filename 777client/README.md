# 777 Client

Fabric utility client for Minecraft 1.21.11. Java 21, Fabric API, rendered
through Minecraft's own `DrawContext`.

**Supported platforms: Linux and Windows.** There is no native code, no
injection, and no platform-specific path handling -- all filesystem access goes
through `Path.resolve()`, so both behave identically.

---

## Versions

These are verified live against `meta.fabricmc.net` and Modrinth, and are already
correct in `gradle.properties`:

```properties
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6
loader_version=0.19.5
fabric_version=0.141.6+1.21.11
```

Minecraft 1.21.11 released 2025-12-09 and declares Java 21.

To re-derive them at any time (or target a different version):

```bash
python3 tools/check-versions.py 1.21.11
```

That prints paste-ready `gradle.properties` lines. **If a build ever fails on
dependency resolution, run it before anything else** -- a version that does not
exist in the repo is the single most common cause.

## Build

Requires **Gradle 9.x** (9.2 or newer) and a **Java 21** toolchain.

```bash
cd 777client
gradle build
```

Optionally pin a wrapper so the build is reproducible:

```bash
gradle wrapper --gradle-version 9.7.1
./gradlew build
```

This project uses **Fabric Loom 1.17.20**, the current release line, which
supports Gradle 9. Older Loom (1.9/1.10) targets Gradle 8.x and will either warn
heavily or fail outright on Gradle 9.

Output jar: `build/libs/777client-1.0.0.jar`

Drop it into your Fabric `mods/` folder alongside **Fabric API**.

- Linux: `~/.minecraft/mods` (Prism: `<instance>/minecraft/mods`)
- Windows: `%APPDATA%\.minecraft\mods`

## Run in a dev environment

```bash
./gradlew runClient
```

## UI typeface

**Inter is bundled** -- nothing to install. Two weights ship as resource fonts so
the UI has real typographic hierarchy, which matters more than usual here because
a monochrome palette has no colour to lean on:

| Font id | File | Used for |
|---|---|---|
| `sevenclient:ui` | `inter_medium.ttf` | body, labels, descriptions |
| `sevenclient:ui_bold` | `inter_semibold.ttf` | module names, headings, slider values |

Inter is licensed SIL OFL 1.1; see `LICENSE-Inter.txt`.

To swap in a different face, replace the TTFs here:

```
src/main/resources/assets/sevenclient/font/inter_medium.ttf
```

**Filenames must be lowercase.** Minecraft `Identifier` paths accept only
`[a-z0-9/._-]`. A capital letter throws `InvalidIdentifierException`, and if that
happens in a static initialiser the JVM marks the whole class permanently
uninitialisable -- every later reference then throws `NoClassDefFoundError`
instead of the real error. A file named `Inter-Medium.ttf` will take down the HUD
and then crash the game on opening the GUI. Rename it, don't fight it.

The provider is already wired in `assets/sevenclient/font/ui.json`.

**How the missing-font case is handled, and why it matters.** Minecraft does NOT
fall back to the default font when a `ttf` provider's file is missing -- it loads
the font as *empty*, and an empty font resolves every character to the
missing-glyph box. A missing TTF therefore renders the entire UI as tofu boxes.

`UiFont` guards against this by asking the resource manager whether the TTF
actually exists before attaching the font style to any text. No TTF means the
vanilla font is used and the UI reads normally. You will still see one harmless
warning in the log about the unresolved font provider.

Dropping the TTF in (lowercase!) and pressing **F3+T** picks it up without
restarting.
To force the vanilla font deliberately, set `UiFont.useCustomFont = false`.

---

## Interface

Window is **470x302** -- deliberately compact. Sidebar card on the left with
per-category module counts, content pane on the right.

### Monochrome by design

Black, white and grey only -- no hue anywhere in the project (there is a build
check for it). Two rules carry the hierarchy that colour would normally:

1. **A deliberate grey ramp** (`0D → 12 → 16 → 1F → 26 → 33`). Evenly spaced
   steps keep adjacent surfaces distinguishable without getting noisy, which is
   what stops a dark UI collapsing into mud.
2. **White is the accent, spent sparingly.** It appears only on things that are
   ON or SELECTED: active toggle, filled slider, nav indicator, primary label.
   Used everywhere, it would stop meaning anything.

Toggles invert rather than tint -- white track, near-black knob when on. That
reads as far more deliberate than a grey-to-slightly-lighter-grey transition.

| Action | Default |
|---|---|
| Open ClickGUI | Right Shift |
| Mark/unmark enemy | Middle click a player |

- **Left-click a card** expands it. **Left-click the switch**, or **right-click
  the card**, toggles the module.
- **Search box** filters the current category by name and description.
- **Keybinds** view lists every module with its bind and Toggle/Hold mode in one
  place. Click the bind chip to rebind, right-click to clear, click the mode chip
  to switch.
- **Config** view has save / reload / disable-all plus client status.
- Drag the window by its top strip. Position, open tab, expanded cards and
  scroll all persist between openings.

Every transition -- toggles, card expansion, chevrons, hover, scroll -- is eased
with `1 - exp(-speed * dt)` against a real frame delta, so animation runs at the
same speed at 60fps and 300fps.

### On ImGui

The reference look does not need ImGui, and using it here would be a bad trade.
`imgui-java` means shipping per-platform native binaries and running an OpenGL3
backend inside Minecraft's own render pass, fighting its GL state. Everything
that actually makes that style look good is achievable with `DrawContext#fill`:

- **Soft shadows** -- concentric rounded rects with quadratic alpha falloff
- **Gradients** -- per-row recolour inside the rounded-rect scanline algorithm
  (`roundedGradient`), not a square overlay that would square the corners back off
- **Vector icons** -- Bresenham strokes and rings, so they scale with the layout
  instead of being locked to a bitmap sheet
- **Antialiased corners** -- fractional pixel coverage on the edge pixel of each
  scanline

No shaders, no `RenderSystem` state changes, nothing that breaks on a version bump.

### A note on the preview image

`777_gui_preview.png` was rendered with a real TTF. **Without a TTF in
`assets/sevenclient/font/`, Minecraft's built-in font is used and the UI will
look noticeably chunkier than the preview** -- correct and readable, just less
refined. Drop `Inter-Medium.ttf` in to match it.

## Debugging: read the Debug panel first

The **Debug** module (Render tab) is **on by default** and draws a readout in the
top-right. Every line isolates one possible failure, so the first line that looks
wrong is the actual problem:

| Line | Means |
|---|---|
| `font` | whether the custom TTF was found, or vanilla is in use |
| `ticks` | client tick hook. Frozen at 0 = `ClientTickEvents` never registered |
| `frames` | frame hook. 0 = neither the Mouse mixin nor the render fallback ran |
| `frame src` | `Mouse mixin` (ideal) or `render fallback` (mixin didn't apply) |
| `dt` | real frame delta feeding the aim math |
| `attacks` | attack mixin. Stays 0 after hitting something = mixin didn't apply |
| `aim tgt` | the found target, or the exact reason there isn't one |
| `yaw step` | last GCD-snapped yaw delta actually applied |
| `gcd step` | degrees per mouse count at your sensitivity |
| `error` | last module exception, with class and line |

Module errors are no longer silent: a module that throws prints to chat and names
the exception. Module toggles also print to chat, so a click is never ambiguous.

**If a combat module seems to do nothing, check `aim tgt` before anything else.**
`Players Only` used to default to `true`, which means a singleplayer world has
zero valid targets and every combat module correctly does nothing -- identical
from the outside to being broken. It now defaults to `false`, so mobs are valid
and you can verify behaviour solo.

Turn Debug off once you're satisfied.

## Modules

**AimAssist** (Combat) - Adaptive tracking. There is no speed slider: speed is
derived from the situation. See "How AimAssist works" below.

**HitSwap** (Combat) - Swaps to an axe when the opponent raises a shield and back
afterwards. Accuracy chance, faint chance, min/max reaction delay, return delay,
enemies-only, sequence guard.

**S-Tap** / **Shift-Tap** (Combat) - Independent sprint-reset modules on the back
and sneak keys. Both bindable separately, both run concurrently. Chance, min/max
hold in ms.

**JumpReset** (Movement) - Jumps on the tick damage registers. Chance, damage
threshold, hold duration, ground-only, require-sprint.

**FastXP** (Player) - Experience bottles up to 20 CPS through the real use path.
CPS, pitch noise, require-bottles, restore-pitch.

**EnemyMarker** (Player) - Middle-click to toggle players in the global enemy
list, with chat feedback. Extended pick range.

**Watermark** (Render) - Corner watermark with FPS and enemy count.

---

## Design decisions worth knowing

**Nothing sends handcrafted packets.** Every module drives real client state --
rotation fields, keybindings, the selected slot, the use key. Vanilla then builds
and sends the C2S packets itself, in its own order, with its own sequence
numbers. That is the whole architecture, and it is why there is no reconciliation
problem to solve: the client genuinely did the thing it reported.

**GCD quantisation.** Minecraft converts raw mouse counts to degrees via
`step = (sens*0.6+0.2)^3 * 8 * 0.15`. Every legitimate rotation delta is an
integer multiple of that step. `Rotations.snap` enforces it and carries the
discarded fraction forward, so slow tracking still converges instead of stalling
below one step.

**Frame-rate independence.** Aim uses `1 - exp(-k*dt)` rather than a fixed
per-tick fraction, so the crosshair traces the same real-time path at 30fps and
300fps. Tick-driven aim emits exactly 20 updates per second with dead frames
between them, which is a staircase, not a curve.

**Angular windows instead of aim points.** AimAssist computes the angular range
the target's hitbox occupies and clamps the current rotation into it. If the
crosshair is already on the target, nothing moves at all.

**Distribution shape over range.** Reaction delays are log-normal, not uniform. A
uniform roll between two numbers produces a flat histogram, which is its own
signature regardless of how wide the range is.

**Reflection where mappings churn.** `SlotUtil` resolves the selected-slot
accessor at runtime and `MouseMixin` omits its descriptor with `require = 0`,
because those two spots are where the 1.21 line broke source compatibility most
often. A mapping miss degrades gracefully instead of failing to launch.


---

## 1.21.11 API notes

The 1.21.9-1.21.11 range changed several APIs this client depends on. All of the
following were resolved against the actual `yarn 1.21.11+build.6` mappings and
the `fabric-api 0.141.6+1.21.11` jar, not from memory:

| What changed | Now |
|---|---|
| `Element` input methods take records | `mouseClicked(Click, boolean)`, `mouseDragged(Click, double, double)`, `mouseReleased(Click)`, `keyPressed(KeyInput)`, `charTyped(CharInput)` |
| `mouseScrolled` | unchanged, still `(double, double, double, double)` |
| `Style#withFont` | takes `StyleSpriteSource`; wrap an id with `new StyleSpriteSource.Font(id)` |
| `InputUtil#fromKeyCode` | takes a `KeyInput` record: `new KeyInput(key, scancode, modifiers)` |
| `WorldRenderEvents` | gone from `fabric-rendering-v1`; the per-frame fallback now rides `HudRenderCallback`, which fires once per frame anyway |
| authlib `GameProfile` | is a record, `getName()` removed. This client uses Minecraft's own `Nameable#getName` instead and never touches authlib |

Record accessors, for reference: `Click.x() / y() / button()`,
`KeyInput.key() / scancode() / modifiers()`, `CharInput.codepoint() / asString()`.

### Verifying APIs yourself

Rather than guessing at mappings, pull them and grep:

```bash
curl -sLO https://maven.fabricmc.net/net/fabricmc/yarn/1.21.11+build.6/yarn-1.21.11+build.6-v2.jar
unzip -o yarn-1.21.11+build.6-v2.jar
grep -n "StyleSpriteSource" mappings/mappings.tiny | head
```

`mappings.tiny` is tiny-v2, so it carries full descriptors -- you get exact
signatures, not just names. https://linkie.shedaniel.dev is the same data with a
web UI.

## Troubleshooting

**`Could not find net.fabricmc.fabric-api:fabric-api:<something>`**
The version does not exist. Run `python3 tools/check-versions.py 1.21.11` and
paste the output into `gradle.properties`.

**`Could not find net.fabricmc:yarn:<something>`**
Same cause, same fix. Yarn build numbers increment independently of the game
version, so `+build.1` and `+build.6` are both real but only some exist for a
given release.

**Loom complains about the Gradle version**
Check with `gradle --version`. Loom 1.17.20 wants Gradle 9.2+. If you are stuck
on Gradle 8, drop Loom to `1.11-SNAPSHOT` in `build.gradle` instead.

**Upgrading this project over an existing working copy**
`gradle.properties` and `build.gradle` are overwritten when you extract the zip.
If you had already adjusted them, back them up first, or just re-run the version
checker afterwards.


---

## How AimAssist works

No speed slider. Speed is computed from the situation every frame.

### The controller: critically damped spring, Fitts-tuned frequency

A first-order approach (`pos += error * alpha`) is stateless -- output velocity
is a pure function of current error, so anything that changes the error (target
strafing, your mouse, a target switch) changes velocity *instantly*. That
discontinuity is what reads as robotic.

A second-order system carries **velocity as state**. Velocity can only change via
acceleration, so motion is C1 continuous by construction. That momentum is the
single biggest contributor to a fluid feel.

```
accel = omega^2 * error - 2 * omega * (velocity - feedForward)
```

Critically damped (zeta = 1) is the fastest convergence with **zero overshoot**.
Underdamped would visibly ring around the target; overdamped would crawl.

`omega` comes from **Fitts's law** (1954): human movement time scales with
`log2(2D/W + 1)` over distance D and target width W. So rather than asking "what
speed", we ask "how long would a human plausibly take to cross this angular gap
to a target this angularly large" -- that gives T, and `omega = 5.8/T` is the
frequency that settles within ~2% at exactly t = T.

A critically damped step response is `(1 + omega*t) * e^(-omega*t)`, whose
velocity curve is the **bell shape Flash & Hogan (1985) showed real human
reaching movements follow**. The minimum-jerk profile comes out for free, instead
of having to schedule a 5th-order polynomial and re-plan it every frame as the
target moves.

### Measured behaviour

Simulated 25 deg acquisition against the previous implementation:

| | old (lerp + per-frame GCD) | new (spring + accumulated GCD) |
|---|---|---|
| relative roughness | 0.451 | **0.166** (2.7x smoother) |
| dead frames while travelling | 64.1% | **5.0%** |
| overshoot | - | **0.05 deg** |
| settle time, 30 -> 500 fps | varies | **0.233s -> 0.248s** |

### The four inputs, and the tell each one removes

**Error** is measured against the angular *window* the hitbox occupies, not a
point. Inside the window the clamp returns your current rotation, so the error is
exactly zero and nothing is emitted. Combined with `Deadzone` this is why it sits
still on target instead of micro-correcting forever.

**Feed-forward** is the target's own angular velocity. The damping term is
measured against it rather than zero, so the steady state is "matching their
strafe" instead of "stopped and trailing by a constant error". Vape exposes this
as Strafe Increase; here it is a control term rather than a fudge factor.

**Gain** = reaction ramp x mouse yield x strength. Yield is the one that stops it
fighting you: the Mouse mixin runs at the TAIL of `updateMouse`, so vanilla has
already applied real look input, and whatever changed since the rotation *we*
left behind is by definition the human. That rate feeds a rational falloff
(`1/(1+(rate/knee)^2)`) -- no threshold where assistance visibly cuts out. Stored
spring velocity is also bled off while yielding, otherwise holding a flick would
wind the spring up and lurch the moment you stopped.

**Movement time** from Fitts, above.

### Output stage

Rotation accumulates as a continuous `double`; only the change in its
GCD-rounded value is emitted. The obvious approach -- snap each frame's delta,
carry the remainder -- produces several zero frames then one full step. That
sawtooth was most of the old jitter, and it is a *worse* statistical signature
than not snapping at all. Accumulating instead gives monotonic single-step motion
where every emitted delta is still an exact multiple of the mouse step.

Tremor is continuous multi-octave value noise (`SmoothNoise`), not per-frame
Gaussian. Independent random samples every frame is white noise -- at 200fps
that is a 200Hz signal that reads as shimmer, and real hand tremor is
low-frequency and temporally correlated, not per-sample independent.

### Humanisation settings

| Setting | Default | Effect |
|---|---|---|
| Strength | 85 | overall authority |
| Smoothness | 55 | Fitts slope b (0.045 -> 0.20 s/bit). Higher = longer, smoother sweeps |
| Vertical Ratio | 70 | pitch moves slower than yaw, as humans do |
| Yield To Mouse | 60 | how readily it stands down while you move the mouse |
| Max Turn Rate | 420 | hard deg/s ceiling |
| Deadzone | 0.7 | error below this = do nothing (the "static on target" behaviour) |
| Reaction Min/Max MS | 25 / 95 | log-normal delay on target acquisition, then a 90ms smoothstep ramp |
| Target Lag MS | 35 | aims where the target *was*, from an interpolated position history |
| Strafe Lead | 90 | feed-forward scale |
| Tremor / Idle Tremor | 25 / 12 | moving vs resting tremor amplitude |

`Mode: Simple` swaps in the old fixed-rate exponential for comparison.
