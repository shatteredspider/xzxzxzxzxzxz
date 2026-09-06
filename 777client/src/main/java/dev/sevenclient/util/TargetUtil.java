package dev.sevenclient.util;

import dev.sevenclient.SevenClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public final class TargetUtil {

    private TargetUtil() { }

    /**
     * Best target by a blended score of distance and angular offset. Deliberately
     * has NO field-of-view cutoff: a hard FOV gate is what makes a lock "pop" on
     * fast turns, and it is also the behaviour that makes target switching look
     * mechanical. Angle only weights the score, it never disqualifies.
     */
    public static LivingEntity find(double range, boolean enemiesOnly, boolean playersOnly) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye = mc.player.getEyePos();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e == mc.player) continue;
            if (!e.isAlive() || e.isRemoved()) continue;
            if (living.isInvisible() && !(e instanceof PlayerEntity)) continue;
            if (playersOnly && !(e instanceof PlayerEntity)) continue;
            if (e instanceof PlayerEntity p && p.isSpectator()) continue;
            if (enemiesOnly && !SevenClient.get().enemies.is(e)) continue;

            double dist = eye.distanceTo(e.getEyePos());
            if (dist > range) continue;

            float[] rot = Rotations.to(eye, nearestPoint(eye, e));
            double dYaw = Math.abs(Rotations.wrap(rot[0] - mc.player.getYaw()));
            double dPitch = Math.abs(rot[1] - mc.player.getPitch());
            double angle = Math.sqrt(dYaw * dYaw + dPitch * dPitch);

            // distance dominates up close, angle breaks ties in a crowd
            double score = dist * 1.0d + angle * 0.045d;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    /** Point on the entity hitbox nearest to our eye position. */
    public static Vec3d nearestPoint(Vec3d eye, Entity e) {
        Box box = e.getBoundingBox();
        return new Vec3d(
                clamp(eye.x, box.minX, box.maxX),
                clamp(eye.y, box.minY, box.maxY),
                clamp(eye.z, box.minZ, box.maxZ)
        );
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Entity currently under the crosshair, if any. */
    public static Entity crosshairEntity() {
        MinecraftClient mc = MinecraftClient.getInstance();
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult ehr) return ehr.getEntity();
        return null;
    }

    /**
     * Weapon test by registry id rather than by item class. The 1.21 line moved
     * tools onto data components and reshuffled the item classes, so an
     * `instanceof SwordItem` check is a compile-time landmine across versions.
     * String matching against the registry id is stable and lets you extend the
     * list without touching code.
     */
    public static boolean isWeapon(ItemStack stack, String extraKeywords) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String path = id.getPath().toLowerCase(Locale.ROOT);

        if (path.endsWith("_sword") || path.endsWith("_axe") || path.equals("trident")
                || path.equals("mace") || path.equals("bow") || path.equals("crossbow")) {
            return true;
        }
        if (extraKeywords != null && !extraKeywords.isBlank()) {
            for (String kw : extraKeywords.split(",")) {
                String k = kw.trim().toLowerCase(Locale.ROOT);
                if (!k.isEmpty() && path.contains(k)) return true;
            }
        }
        return false;
    }

    public static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT).endsWith("_axe");
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT).endsWith("_sword");
    }
}
