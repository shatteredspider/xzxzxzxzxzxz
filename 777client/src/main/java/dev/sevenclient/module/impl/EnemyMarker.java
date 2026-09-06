package dev.sevenclient.module.impl;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.util.EnemyManager;
import dev.sevenclient.util.InputUtil2;
import dev.sevenclient.util.TargetUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Middle click a player to add or remove them from the global enemy list.
 *
 * Polls GLFW with edge detection rather than mixing into the mouse callback, so
 * it cannot swallow the event from other mods and needs no version-specific
 * method target.
 */
public class EnemyMarker extends Module {

    private final NumberSetting reach = reg(new NumberSetting("Pick Range", 24, 4, 128, 1));
    private final BoolSetting feedback = reg(new BoolSetting("Chat Feedback", true));

    private boolean lastDown = false;

    public EnemyMarker() {
        super("EnemyMarker", "Middle-click players to toggle them as enemies.", Category.PLAYER);
        registerBindSettings();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) { lastDown = false; return; }

        boolean down = InputUtil2.isDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true);
        if (down && !lastDown) {
            pick();
        }
        lastDown = down;
    }

    private void pick() {
        Entity hit = TargetUtil.crosshairEntity();
        if (hit == null) hit = raycast();
        if (!(hit instanceof PlayerEntity player)) return;
        if (player == mc.player) return;

        EnemyManager enemies = SevenClient.get().enemies;
        boolean added = enemies.toggle(player);

        if (feedback.is()) {
            String name = player.getName().getString();
            SevenClient.chat((added ? "\u00a7aAdded \u00a7f" : "\u00a7cRemoved \u00a7f")
                    + name + (added ? " \u00a77as an enemy." : " \u00a77as an enemy."));
        }
    }

    /**
     * Extended-range player raycast.
     *
     * Hand-rolled over Box#raycast rather than ProjectileUtil: that helper's
     * parameter list and return type have both moved around, whereas Box#raycast
     * returning Optional<Vec3d> has been stable for years. Picks the nearest
     * intersected player hitbox along the look vector.
     */
    private Entity raycast() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = mc.player.getRotationVec(1.0f);
        double max = reach.val();
        Vec3d end = eye.add(dir.multiply(max));

        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity)) continue;
            if (e == mc.player || e.isSpectator() || !e.isAlive()) continue;

            // small expand so grazing clicks still register
            java.util.Optional<Vec3d> hit = e.getBoundingBox().expand(0.15d).raycast(eye, end);
            if (hit.isEmpty()) continue;

            double d = eye.squaredDistanceTo(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
