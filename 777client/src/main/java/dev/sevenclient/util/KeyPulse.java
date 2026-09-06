package dev.sevenclient.util;

import net.minecraft.client.option.KeyBinding;

/**
 * Holds a real KeyBinding down for a randomised duration.
 *
 * This matters: the alternative is sending crafted movement packets, which
 * desyncs client and server prediction. Driving the actual keybinding means
 * vanilla movement code produces the packets, so position, velocity and sprint
 * state all stay internally consistent -- there is no impossible transition to
 * detect, because the movement genuinely happened client-side.
 */
public final class KeyPulse {

    private final KeyBinding binding;
    private long releaseAt = 0L;
    private boolean holding = false;

    public KeyPulse(KeyBinding binding) {
        this.binding = binding;
    }

    public void press(long millis) {
        long now = System.currentTimeMillis();
        this.releaseAt = Math.max(this.releaseAt, now + Math.max(1L, millis));
        this.holding = true;
        binding.setPressed(true);
    }

    /** Call every client tick. */
    public void tick() {
        if (!holding) return;
        if (System.currentTimeMillis() >= releaseAt) {
            release();
        } else {
            // re-assert each tick; vanilla input polling can clear it
            binding.setPressed(true);
        }
    }

    public void release() {
        if (!holding) return;
        holding = false;
        releaseAt = 0L;
        binding.setPressed(false);
    }

    public boolean active() {
        return holding;
    }
}
