package dev.sevenclient.module.impl;

import net.minecraft.client.option.KeyBinding;

/**
 * Sprint reset by tapping sneak. Runs concurrently with S-Tap when both are
 * enabled -- they drive different keybindings and hold their own timers, so
 * there is no shared state to contend over.
 */
public class ShiftTap extends SprintReset {

    public ShiftTap() {
        super("Shift-Tap", "Taps sneak on hit to reset sprint knockback.");
        registerBindSettings();
    }

    @Override
    protected KeyBinding binding() {
        return mc.options.sneakKey;
    }
}
