package dev.sevenclient.module.setting;

import dev.sevenclient.util.InputUtil2;
import org.lwjgl.glfw.GLFW;

/**
 * A key OR mouse-button bind. Mouse buttons are stored with mouse=true so a
 * middle-click bind and the M key can never collide.
 */
public class KeySetting extends Setting<Integer> {

    private boolean mouse;

    public KeySetting(String name) {
        super(name, GLFW.GLFW_KEY_UNKNOWN);
    }

    public KeySetting(String name, int key) {
        super(name, key);
    }

    public int code() {
        return value;
    }

    public boolean isMouse() {
        return mouse;
    }

    public void bind(int code, boolean isMouse) {
        this.value = code;
        this.mouse = isMouse;
    }

    public void clear() {
        this.value = GLFW.GLFW_KEY_UNKNOWN;
        this.mouse = false;
    }

    public boolean isBound() {
        return value != GLFW.GLFW_KEY_UNKNOWN;
    }

    public boolean down() {
        return isBound() && InputUtil2.isDown(value, mouse);
    }

    public String label() {
        if (!isBound()) return "NONE";
        return mouse ? InputUtil2.mouseName(value) : InputUtil2.keyName(value);
    }

    @Override
    public String serialize() {
        return (mouse ? "M:" : "K:") + value;
    }

    @Override
    public void deserialize(String raw) {
        try {
            if (raw.startsWith("M:")) {
                bind(Integer.parseInt(raw.substring(2)), true);
            } else if (raw.startsWith("K:")) {
                bind(Integer.parseInt(raw.substring(2)), false);
            }
        } catch (NumberFormatException ignored) { }
    }
}
