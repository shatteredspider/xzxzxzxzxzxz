package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.KeyPulse;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared engine for S-Tap and Shift-Tap.
 *
 * A sprint reset lands more knockback because knockback scales with the
 * attacker's sprint state at the moment the hit resolves. Breaking sprint for a
 * few frames right after the hit packet re-arms that bonus for the next swing.
 *
 * Implemented by holding the real keybinding, so vanilla movement code generates
 * the position/velocity packets. Nothing about the resulting motion is
 * impossible -- the player genuinely moved.
 */
public abstract class SprintReset extends Module {

    protected final NumberSetting chance = reg(new NumberSetting("Chance", 100, 0, 100, 1));
    protected final NumberSetting minMs  = reg(new NumberSetting("Min MS", 40, 0, 300, 5));
    protected final NumberSetting maxMs  = reg(new NumberSetting("Max MS", 110, 0, 300, 5));
    protected final BoolSetting enemiesOnly = reg(new BoolSetting("Enemies Only", false));
    // default false so hitting a mob in a solo world visibly triggers the reset
    protected final BoolSetting playersOnly = reg(new BoolSetting("Players Only", false));

    private KeyPulse pulse;

    protected SprintReset(String name, String description) {
        super(name, description, Category.COMBAT);
    }

    /** Which vanilla keybinding this variant drives. */
    protected abstract KeyBinding binding();

    private KeyPulse pulse() {
        if (pulse == null) pulse = new KeyPulse(binding());
        return pulse;
    }

    @Override
    public void onDisable() {
        pulse().release();
    }

    @Override
    public void onTick() {
        pulse().tick();
    }

    @Override
    public void onAttack(Entity target) {
        if (playersOnly.is() && !(target instanceof PlayerEntity)) return;
        if (enemiesOnly.is() && !dev.sevenclient.SevenClient.get().enemies.is(target)) return;
        if (!HumanRandom.chance(chance.val())) return;

        double lo = Math.min(minMs.val(), maxMs.val());
        double hi = Math.max(minMs.val(), maxMs.val());
        // uniform-ish here, lightly skewed -- hold durations are a motor action,
        // not a reaction, so they cluster less sharply than reaction times
        long hold = (long) HumanRandom.uniform(lo, hi) + (long) Math.abs(HumanRandom.gauss(4.0d));
        pulse().press(hold);
    }
}
