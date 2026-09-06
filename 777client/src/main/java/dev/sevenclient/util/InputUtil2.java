package dev.sevenclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Thin wrapper over GLFW polling so binds work for keys and mouse buttons alike. */
public final class InputUtil2 {

    private InputUtil2() { }

    public static boolean isDown(int code, boolean mouse) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return false;
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        int state = mouse
                ? GLFW.glfwGetMouseButton(handle, code)
                : GLFW.glfwGetKey(handle, code);
        return state == GLFW.GLFW_PRESS;
    }

    public static String keyName(int code) {
        try {
            // fromKeyCode takes a KeyInput record now: (key, scancode, modifiers)
            return InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(code, 0, 0))
                    .getLocalizedText().getString().toUpperCase();
        } catch (Throwable t) {
            String n = GLFW.glfwGetKeyName(code, 0);
            return n == null ? ("KEY" + code) : n.toUpperCase();
        }
    }

    public static String mouseName(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE1";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE2";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE3";
            default -> "MOUSE" + (button + 1);
        };
    }
}
