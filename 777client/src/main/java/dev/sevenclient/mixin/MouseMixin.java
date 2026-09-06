package dev.sevenclient.mixin;

import dev.sevenclient.util.FrameDispatcher;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame driver, injected immediately after vanilla applies real look input.
 *
 * This call site matters more than it looks. Rotation changes made here land in
 * the same place, in the same order, and with the same downstream packet path as
 * physical mouse movement -- there is no separate code path for the server to
 * see a difference in. Anything that hooks later (render, or worse, the packet
 * send itself) produces rotations that arrive out of step with the input frame.
 *
 * No descriptor is specified so this survives the signature change to
 * updateMouse across the 1.21 line, and defaultRequire is 0 so a mapping miss
 * degrades to the world-render fallback instead of crashing the game.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "updateMouse", at = @At("TAIL"), require = 0)
    private void seven$onUpdateMouse(CallbackInfo ci) {
        FrameDispatcher.fromMouseMixin();
    }
}
