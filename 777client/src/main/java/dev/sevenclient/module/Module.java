package dev.sevenclient.module;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.setting.KeySetting;
import dev.sevenclient.module.setting.ModeSetting;
import dev.sevenclient.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    /** Every module gets a bind + a bind mode for free. */
    public final KeySetting bind = new KeySetting("Bind");
    public final ModeSetting bindMode = new ModeSetting("Bind Mode", "Toggle", "Toggle", "Hold");

    private boolean enabled;

    /** Chat feedback on every state change. Controlled by the Debug module. */
    public static boolean notifyToggles = true;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    // ---------------------------------------------------------------- lifecycle

    public void onEnable() { }

    public void onDisable() { }

    /** Start of client tick, before the player ticks. Safe place to inject key state. */
    public void onTick() { }

    /** End of client tick. */
    public void onPostTick() { }

    /**
     * Once per rendered frame. dt is real seconds since the previous frame, clamped.
     * Use this (not onTick) for anything that must be smooth above 20Hz.
     */
    public void onFrame(float dt) { }

    public void onHudRender(DrawContext ctx) { }

    /** Fired from ClientPlayerInteractionManager#attackEntity. */
    public void onAttack(Entity target) { }

    // ---------------------------------------------------------------- state

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean state) {
        if (state == enabled) return;
        this.enabled = state;
        try {
            if (state) onEnable(); else onDisable();
        } catch (Throwable t) {
            SevenClient.LOG.error("Module {} threw on state change", name, t);
            dev.sevenclient.util.Diagnostics.error(name, t);
        }
        if (notifyToggles) {
            SevenClient.chat((state ? "\u00a7a+ \u00a7f" : "\u00a7c- \u00a77") + name);
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public BindMode mode() {
        return bindMode.is("Hold") ? BindMode.HOLD : BindMode.TOGGLE;
    }

    // ---------------------------------------------------------------- settings

    protected <T extends Setting<?>> T reg(T setting) {
        settings.add(setting);
        return setting;
    }

    /** Called by subclasses after building their own settings so bind rows sit last. */
    protected void registerBindSettings() {
        settings.add(bindMode);
        settings.add(bind);
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public String name() { return name; }
    public String description() { return description; }
    public Category category() { return category; }
}
