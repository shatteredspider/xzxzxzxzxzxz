package dev.sevenclient.module.impl;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.SlotUtil;
import dev.sevenclient.util.TargetUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Swaps to an axe when the opponent raises a shield, then back to the sword.
 *
 * Two things keep this from reading as automation:
 *  - the reaction delay is log-normal, not uniform, so the delay histogram looks
 *    like a person's rather than a flat band between two numbers
 *  - "faints": a tunable share of swaps fire on no shield at all. A 100% hit rate
 *    on shield detection is superhuman; being occasionally wrong is the point.
 */
public class HitSwap extends Module {

    private final NumberSetting chance   = reg(new NumberSetting("Accuracy Chance", 82, 0, 100, 1));
    private final NumberSetting faint    = reg(new NumberSetting("Faint Chance", 12, 0, 100, 1));
    private final NumberSetting minDelay = reg(new NumberSetting("Min Delay MS", 40, 0, 400, 5));
    private final NumberSetting maxDelay = reg(new NumberSetting("Max Delay MS", 120, 0, 400, 5));
    private final NumberSetting holdBack = reg(new NumberSetting("Return Delay MS", 180, 50, 800, 10));
    private final NumberSetting range    = reg(new NumberSetting("Range", 4.2, 1.0, 8.0, 0.1));
    private final BoolSetting enemiesOnly = reg(new BoolSetting("Enemies Only", false));
    private final BoolSetting sequenceGuard = reg(new BoolSetting("Sequence Guard", true));

    private static final int NONE = -1;

    private long swapAt = 0L;
    private long returnAt = 0L;
    private int returnSlot = NONE;
    private boolean armed = false;
    private boolean lastBlocking = false;

    public HitSwap() {
        super("HitSwap", "Reactive axe swap on shield raise, with humanised delay and faints.", Category.COMBAT);
        registerBindSettings();
    }

    @Override
    public void onDisable() {
        restore();
        armed = false;
        swapAt = 0L;
        returnAt = 0L;
        lastBlocking = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();

        // return leg
        if (returnSlot != NONE && now >= returnAt) {
            restore();
        }

        // execute a scheduled swap
        if (armed && now >= swapAt) {
            armed = false;
            int axe = SlotUtil.findHotbar(TargetUtil::isAxe);
            if (axe != NONE) {
                returnSlot = SlotUtil.selected();
                SlotUtil.select(axe);
                returnAt = now + (long) holdBack.val();

                /*
                 * Sequence guard.
                 *
                 * MinecraftClient#handleInputEvents (where attacks originate) runs
                 * BEFORE the player ticks, and the selected-slot packet is emitted
                 * during the player tick. Attacking on the same tick we swap would
                 * therefore put the attack on the wire ahead of the slot update --
                 * the server would resolve the hit with the old item. Suppressing
                 * the attack for this one tick keeps the C2S order truthful.
                 */
                if (sequenceGuard.is()) {
                    mc.options.attackKey.setPressed(false);
                }
            }
        }

        // detection
        LivingEntity target = TargetUtil.find(range.val(), enemiesOnly.is(), true);
        boolean blocking = target instanceof PlayerEntity p && p.isBlocking();

        if (blocking && !lastBlocking && !armed && returnSlot == NONE) {
            if (HumanRandom.chance(chance.val())) schedule(now);
        } else if (!blocking && !armed && returnSlot == NONE && target != null) {
            // faint: fire on nothing, at a much lower rate and a longer delay
            if (HumanRandom.chance(faint.val() * 0.02d)) schedule(now);
        }

        lastBlocking = blocking;
    }

    private void schedule(long now) {
        double lo = Math.min(minDelay.val(), maxDelay.val());
        double hi = Math.max(minDelay.val(), maxDelay.val());
        swapAt = now + HumanRandom.reaction(lo, hi);
        armed = true;
    }

    private void restore() {
        if (returnSlot == NONE) return;
        SlotUtil.select(returnSlot);
        returnSlot = NONE;
        returnAt = 0L;
    }

    @Override
    public void onEnable() {
        if (SlotUtil.findHotbar(TargetUtil::isAxe) == NONE && mc.player != null) {
            SevenClient.chat("\u00a77HitSwap enabled \u00a78- \u00a77no axe in hotbar yet.");
        }
    }
}
