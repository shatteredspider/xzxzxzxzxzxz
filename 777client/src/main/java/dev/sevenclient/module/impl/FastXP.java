package dev.sevenclient.module.impl;

import dev.sevenclient.mixin.MinecraftClientAccessor;
import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.Rotations;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

/**
 * High rate experience bottle throwing.
 *
 * Approach: drive the real use key and zero the vanilla item-use cooldown, so
 * every throw goes through MinecraftClient#doItemUse exactly as a physical click
 * would -- correct sequence number, correct hand, correct interaction packet.
 * Sending crafted PlayerInteractItemC2S packets instead would produce sequence
 * numbers and timing that never match the client's own interaction manager
 * state, which is trivially detectable and also desyncs the sequence counter.
 *
 * Hard ceiling is 20 CPS: handleInputEvents runs once per client tick. Anything
 * advertising more than that on an unmodified tick loop is sending raw packets.
 */
public class FastXP extends Module {

    private final NumberSetting cps = reg(new NumberSetting("CPS", 18, 1, 20, 1));
    private final NumberSetting pitchNoise = reg(new NumberSetting("Pitch Noise", 0.2, 0.0, 0.5, 0.05));
    private final BoolSetting onlyWithBottles = reg(new BoolSetting("Require Bottles", true));
    private final BoolSetting restoreRotation = reg(new BoolSetting("Restore Pitch", true));

    private final double[] residual = new double[2];
    private float basePitch = 0f;
    private boolean pressed = false;
    private double accumulator = 0.0d;

    public FastXP() {
        super("FastXP", "Throws experience bottles at up to 20 CPS through the real use path.", Category.PLAYER);
        registerBindSettings();
    }

    @Override
    public void onEnable() {
        if (mc.player != null) basePitch = mc.player.getPitch();
        accumulator = 0.0d;
    }

    @Override
    public void onDisable() {
        if (pressed) {
            mc.options.useKey.setPressed(false);
            pressed = false;
        }
        if (restoreRotation.is() && mc.player != null) {
            mc.player.setPitch(MathHelper.clamp(basePitch, -90f, 90f));
        }
        residual[0] = 0.0d;
        residual[1] = 0.0d;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (onlyWithBottles.is() && !holdingBottle()) {
            if (pressed) { mc.options.useKey.setPressed(false); pressed = false; }
            return;
        }

        // Duty cycle: at 20 CPS every tick throws, below that we skip ticks in a
        // pattern with slight jitter so the interval sequence isn't a clean period.
        accumulator += cps.val() / 20.0d;
        boolean fireThisTick = accumulator >= 1.0d;
        if (fireThisTick) accumulator -= 1.0d;

        if (!fireThisTick) {
            if (pressed) { mc.options.useKey.setPressed(false); pressed = false; }
            return;
        }

        /*
         * Micro pitch variance. A perfectly static rotation across hundreds of
         * consecutive interactions is a stronger signal than the click rate
         * itself. Kept sub-degree, GCD-quantised, and re-centred on the pitch the
         * player actually chose so aim is unaffected.
         */
        if (pitchNoise.val() > 0.0d) {
            double drift = HumanRandom.tremor(System.nanoTime() / 1_000_000_000.0d) * pitchNoise.val();
            float target = MathHelper.clamp(basePitch + (float) drift, -90f, 90f);
            float delta = Rotations.snap(target - mc.player.getPitch(), residual, 1);
            if (delta != 0f) {
                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + delta, -90f, 90f));
            }
        }

        // Clear the 4-tick vanilla gate, then let the real input path fire.
        // Cast through Object: MinecraftClient does not declare this interface at
        // compile time (mixin adds it at runtime), and on mapping builds where the
        // class is final a direct cast would not compile at all.
        try {
            ((MinecraftClientAccessor) (Object) mc).seven$setItemUseCooldown(0);
        } catch (Throwable ignored) {
            // accessor mixin failed to apply -- fall back to vanilla 5 CPS gate
        }
        mc.options.useKey.setPressed(true);
        pressed = true;
    }

    @Override
    public void onPostTick() {
        if (pressed) {
            mc.options.useKey.setPressed(false);
            pressed = false;
        }
    }

    private boolean holdingBottle() {
        return isBottle(mc.player.getMainHandStack()) || isBottle(mc.player.getOffHandStack());
    }

    private static boolean isBottle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().equals("experience_bottle");
    }
}
