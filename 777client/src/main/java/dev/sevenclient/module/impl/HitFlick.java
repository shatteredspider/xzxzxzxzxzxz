package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.ModeSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.Diagnostics;
import dev.sevenclient.util.HumanRandom;
import dev.sevenclient.util.RotationSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * HitFlick -- knockback displacement.
 *
 * ---------------------------------------------------------------------------
 * THE MECHANIC
 * ---------------------------------------------------------------------------
 * A sprint attack applies a second, separate knockback on top of the normal
 * one, and unlike the base knockback (which is derived from the positions of
 * the two entities) this bonus is derived from the ATTACKER'S YAW. Vanilla
 * computes a horizontal direction from the attacker's facing and hands it to
 * LivingEntity#knockbackTarget / #takeKnockback.
 *
 * So the yaw you are facing at the instant the hit resolves steers where the
 * opponent goes. Flick it and you displace them somewhere other than straight
 * back -- off a bridge, away from their team, or laterally to break their
 * strafe and keep them in your combo.
 *
 * ---------------------------------------------------------------------------
 * WHY IT FIRES *BEFORE* THE HIT, NOT ON IT
 * ---------------------------------------------------------------------------
 * Packet order inside one client tick is: handleInputEvents (which sends the
 * attack) THEN the player tick (which sends rotation). So the yaw the server
 * uses to resolve an attack is the one from the PREVIOUS tick's movement packet.
 * Rotating when the attack fires is already too late -- it would only affect the
 * next hit.
 *
 * This module therefore arms one tick early, off the attack cooldown: when the
 * cooldown is nearly ready and a player is under the crosshair, the flick goes
 * into that tick's rotation packet, and the attack lands on the following tick
 * with the flicked yaw already registered server-side. That is also exactly what
 * a human doing this by hand produces -- their flick and click straddle a tick
 * boundary.
 */
public class HitFlick extends Module {

    private final ModeSetting rotation = reg(new ModeSetting("Rotation", "Silent", "Silent", "Real"));
    private final NumberSetting chance = reg(new NumberSetting("Chance", 50, 0, 100, 1));

    /**
     * Lateral offset applied to the knockback direction.
     *
     * 0 = straight away from you (vanilla behaviour, no displacement).
     * 90 = pure sideways, maximum displacement.
     *
     * Note the tradeoff as you raise it: the server resolves the hit from the
     * rotation it was given, and anticheat validates the target was actually in
     * view. Past roughly 60 the hit itself starts becoming hard to justify,
     * especially on Silent where your camera never corroborates it.
     */
    private final NumberSetting degrees = reg(new NumberSetting("Degrees", 42, 0, 90, 1));

    private boolean armed = false;
    private float armedYaw = 0f;
    private int armedTicks = 0;
    private boolean rolled = false;

    public HitFlick() {
        super("HitFlick", "Steers sprint knockback by flicking yaw before the hit.", Category.COMBAT);
        registerBindSettings();
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (armed) {
            armedTicks++;
            // give up if the expected attack never came
            if (armedTicks > 4) release();
            else if (rotation.is("Real")) {
                mc.player.setYaw(armedYaw);
                mc.player.setHeadYaw(armedYaw);
            }
            return;
        }

        if (!mc.options.attackKey.isPressed()) { rolled = false; return; }

        PlayerEntity target = crosshairPlayer();
        if (target == null) { rolled = false; return; }

        // the bonus only exists on a sprint attack
        if (!mc.player.isSprinting()) return;

        // arm one tick before the swing is ready, so the rotation packet leads it
        float progress = mc.player.getAttackCooldownProgress(0.0f);
        if (progress < 0.88f) { rolled = false; return; }

        if (rolled) return;
        rolled = true;
        if (!HumanRandom.chance(chance.val())) return;

        Float yaw = flickYaw(target);
        if (yaw == null) return;

        armed = true;
        armedTicks = 0;
        armedYaw = yaw;

        if (rotation.is("Silent")) {
            RotationSync.silentYaw = armedYaw;
            RotationSync.silentActive = true;
        } else {
            mc.player.setYaw(armedYaw);
            mc.player.setHeadYaw(armedYaw);
        }
        Diagnostics.hitFlickArmed++;
    }

    /** The hit landed -- the flick has served its purpose. */
    @Override
    public void onAttack(Entity attacked) {
        if (armed) release();
    }

    private void release() {
        armed = false;
        armedTicks = 0;
        RotationSync.silentActive = false;
    }

    /**
     * Yaw whose facing direction is the desired knockback vector.
     *
     * Blends "straight away from me" with a lateral component. The side is
     * chosen to push along the target's existing drift rather than against it,
     * because adding to momentum they already have displaces them considerably
     * further than fighting it.
     */
    private Float flickYaw(PlayerEntity target) {
        // getX/getY/getZ rather than a position accessor: Entity has no no-arg
        // getEntityPos() in this mapping set.
        Vec3d me = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d them = new Vec3d(target.getX(), target.getY(), target.getZ());
        double dx = them.x - me.x;
        double dz = them.z - me.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4d) return null;

        double ux = dx / len, uz = dz / len;      // away from me
        double px = -uz, pz = ux;                 // perpendicular

        Vec3d tv = target.getVelocity();
        double side = (tv.x * px + tv.z * pz) >= 0.0d ? 1.0d : -1.0d;

        double a = Math.toRadians(degrees.val());
        double dirX = ux * Math.cos(a) + px * side * Math.sin(a);
        double dirZ = uz * Math.cos(a) + pz * side * Math.sin(a);

        // yaw whose forward vector is (dirX, dirZ)
        return (float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0d);
    }

    private PlayerEntity crosshairPlayer() {
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity p
                && p != mc.player && !p.isSpectator()) {
            return p;
        }
        return null;
    }
}
