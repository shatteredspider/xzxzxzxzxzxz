package dev.sevenclient.module;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.impl.AimAssist;
import dev.sevenclient.module.impl.ClickGuiModule;
import dev.sevenclient.module.impl.EnemyMarker;
import dev.sevenclient.module.impl.FastXP;
import dev.sevenclient.module.impl.HitFlick;
import dev.sevenclient.module.impl.HitSwap;
import dev.sevenclient.module.impl.JumpReset;
import dev.sevenclient.module.impl.STap;
import dev.sevenclient.module.impl.ShiftTap;
import dev.sevenclient.module.impl.Debug;
import dev.sevenclient.module.impl.Watermark;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private final Map<Module, Boolean> lastBindState = new HashMap<>();

    public ModuleManager() {
        add(new AimAssist());
        add(new HitSwap());
        add(new HitFlick());
        add(new STap());
        add(new ShiftTap());
        add(new JumpReset());
        add(new FastXP());
        add(new EnemyMarker());
        add(new Watermark());
        add(new Debug());
        add(new ClickGuiModule());
    }

    private void add(Module m) {
        modules.add(m);
        lastBindState.put(m, false);
    }

    public List<Module> all() {
        return modules;
    }

    public List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.category() == c) out.add(m);
        return out;
    }

    public Module byName(String name) {
        for (Module m : modules) if (m.name().equalsIgnoreCase(name)) return m;
        return null;
    }

    /**
     * Polled bind handling instead of a key-callback mixin: works identically for
     * keyboard and mouse binds, and survives Minecraft version churn.
     */
    public void pollBinds() {
        // While a screen is open, keep tracking edges but never act on them --
        // otherwise typing in the ClickGUI would toggle modules behind it.
        boolean screenOpen = net.minecraft.client.MinecraftClient.getInstance().currentScreen != null;

        for (Module m : modules) {
            if (!m.bind.isBound()) continue;
            boolean down = m.bind.down();
            boolean was = lastBindState.getOrDefault(m, false);
            lastBindState.put(m, down);
            if (screenOpen) continue;

            if (m.mode() == BindMode.HOLD) {
                if (down != m.isEnabled()) m.setEnabled(down);
            } else if (down && !was) {
                m.toggle();
            }
        }
    }

    public void onTick() {
        dev.sevenclient.util.Diagnostics.ticks++;
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onTick(); } catch (Throwable t) { crash(m, t); }
        }
    }

    public void onPostTick() {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onPostTick(); } catch (Throwable t) { crash(m, t); }
        }
    }

    public void onFrame(float dt) {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onFrame(dt); } catch (Throwable t) { crash(m, t); }
        }
    }

    public void onHudRender(DrawContext ctx) {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onHudRender(ctx); } catch (Throwable t) { crash(m, t); }
        }
    }

    public void onAttack(Entity target) {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try { m.onAttack(target); } catch (Throwable t) { crash(m, t); }
        }
    }

    /**
     * A module that throws gets disabled -- but LOUDLY. Previously this failed
     * silently, which is indistinguishable from "the module does nothing", and
     * that ambiguity is exactly what makes this kind of bug hard to find.
     */
    private void crash(Module m, Throwable t) {
        SevenClient.LOG.error("Module {} threw, disabling.", m.name(), t);
        dev.sevenclient.util.Diagnostics.error(m.name(), t);
        SevenClient.chat("\u00a7cError in \u00a7f" + m.name() + "\u00a7c, disabled: \u00a77"
                + t.getClass().getSimpleName());
        m.setEnabled(false);
    }
}
