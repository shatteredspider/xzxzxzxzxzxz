package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.KeyPulse;

/**
 * Converts incoming horizontal knockback into vertical momentum by jumping on
 * the tick damage registers.
 *
 * Detection uses the hurtTime edge (0 -> maxHurtTime) rather than a health
 * delta alone, because hurtTime is set on the exact tick the damage packet is
 * processed. Jumping a tick late means the knockback vector has already been
 * applied to horizontal velocity and the conversion is largely wasted.
 */
public class JumpReset extends Module {

    private final NumberSetting chance = reg(new NumberSetting("Chance", 70, 0, 100, 1));
    private final NumberSetting minDamage = reg(new NumberSetting("Damage Threshold", 0.5, 0.0, 10.0, 0.5));
    private final NumberSetting holdMs = reg(new NumberSetting("Hold MS", 60, 20, 200, 5));
    private final BoolSetting groundOnly = reg(new BoolSetting("Ground Only", true));
    private final BoolSetting requireSprint = reg(new BoolSetting("Require Sprint", false));

    private KeyPulse jump;
    private int lastHurtTime = 0;
    private float lastHealth = 20f;

    public JumpReset() {
        super("JumpReset", "Jumps on the damage tick to convert knockback into height.", Category.MOVEMENT);
        registerBindSettings();
    }

    private KeyPulse jump() {
        if (jump == null) jump = new KeyPulse(mc.options.jumpKey);
        return jump;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            lastHealth = mc.player.getHealth();
            lastHurtTime = mc.player.hurtTime;
        }
    }

    @Override
    public void onDisable() {
        jump().release();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        jump().tick();

        int hurt = mc.player.hurtTime;
        float health = mc.player.getHealth();
        float taken = lastHealth - health;

        boolean damageEdge = hurt > lastHurtTime;

        if (damageEdge && taken >= minDamage.val()) {
            boolean groundOk = !groundOnly.is() || mc.player.isOnGround();
            boolean sprintOk = !requireSprint.is() || mc.player.isSprinting();
            if (groundOk && sprintOk && HumanRandom.chance(chance.val())) {
                jump().press((long) holdMs.val() + (long) Math.abs(HumanRandom.gauss(6.0d)));
            }
        }

        lastHurtTime = hurt;
        lastHealth = health;
    }
}
