package dev.sevenclient.module.impl;

import net.minecraft.client.option.KeyBinding;

/** Sprint reset by tapping backward. Independently bindable from ShiftTap. */
public class STap extends SprintReset {

    public STap() {
        super("S-Tap", "Taps backward on hit to reset sprint knockback.");
        registerBindSettings();
    }

    @Override
    protected KeyBinding binding() {
        return mc.options.backKey;
    }
}
