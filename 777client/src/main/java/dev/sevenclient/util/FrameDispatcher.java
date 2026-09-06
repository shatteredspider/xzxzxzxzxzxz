package dev.sevenclient.util;

import dev.sevenclient.SevenClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Drives per-frame module logic.
 *
 * Primary source is the Mouse mixin (identical call site to real look input, so
 * rotation changes are indistinguishable in ordering from a physical mouse).
 * If that mixin ever fails to apply on a new version, the world-render hook
 * transparently takes over after a few frames.
 */
public final class FrameDispatcher {

    private static long lastNanos = 0L;
    private static int framesSinceMixin = Integer.MAX_VALUE;

    private FrameDispatcher() { }

    public static void fromMouseMixin() {
        framesSinceMixin = 0;
        dev.sevenclient.util.Diagnostics.frameSourceIsMixin = true;
        dispatch();
    }

    public static void fromRenderFallback() {
        if (framesSinceMixin < 5) {
            framesSinceMixin++;
            return;
        }
        Diagnostics.frameSourceIsMixin = false;
        dispatch();
    }

    private static void dispatch() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (SevenClient.get() == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? (1f / 60f) : (float) ((now - lastNanos) / 1_000_000_000.0d);
        lastNanos = now;
        // guard against pauses / hitches producing a giant step
        dt = MathHelper.clamp(dt, 1f / 1000f, 0.1f);

        Diagnostics.lastDt = dt;
        Diagnostics.countFrame();
        SevenClient.get().modules.onFrame(dt);
    }
}
