package dev.sevenclient.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Global enemy list, keyed by name so it survives respawns and entity id churn. */
public final class EnemyManager {

    private final Set<String> names = Collections.synchronizedSet(new LinkedHashSet<>());

    public boolean toggle(PlayerEntity player) {
        String key = key(player);
        if (names.contains(key)) {
            names.remove(key);
            return false;
        }
        names.add(key);
        return true;
    }

    public boolean is(Entity e) {
        if (!(e instanceof PlayerEntity p)) return false;
        return names.contains(key(p));
    }

    public void clear() {
        names.clear();
    }

    public int size() {
        return names.size();
    }

    public Set<String> names() {
        return names;
    }

    /**
     * Keyed via Nameable#getName rather than GameProfile.
     * Mojang's authlib turned GameProfile into a record, so getName() no longer
     * exists; going through Minecraft's own accessor avoids depending on authlib's
     * shape entirely.
     */
    private static String key(PlayerEntity p) {
        return p.getName().getString().toLowerCase(java.util.Locale.ROOT);
    }
}
