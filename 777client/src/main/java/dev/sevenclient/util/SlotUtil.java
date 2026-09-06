package dev.sevenclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hotbar slot access through cached reflection.
 *
 * Rationale: the selected-slot accessor changed shape across the 1.21 line
 * (public `selectedSlot` field -> `setSelectedSlot`/`getSelectedSlot`). Direct
 * access would be a compile break on the wrong mapping build, so this resolves
 * whichever form exists once, at first use.
 *
 * Important: we only change the slot. The UpdateSelectedSlotC2S packet is still
 * emitted by vanilla ClientPlayerEntity#tick on the following tick, in vanilla
 * order, with vanilla sequencing. Nothing is hand-crafted.
 */
public final class SlotUtil {

    private static Method setter;
    private static Method getter;
    private static Field field;
    private static boolean resolved = false;

    private SlotUtil() { }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        for (Method m : PlayerInventory.class.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class
                    && (m.getName().equals("setSelectedSlot") || m.getName().equals("method_61496"))) {
                setter = m;
            }
            if (m.getParameterCount() == 0 && m.getReturnType() == int.class
                    && (m.getName().equals("getSelectedSlot") || m.getName().equals("method_61495"))) {
                getter = m;
            }
        }
        for (Field f : PlayerInventory.class.getFields()) {
            if (f.getType() == int.class
                    && (f.getName().equals("selectedSlot") || f.getName().equals("field_7545"))) {
                field = f;
            }
        }
    }

    public static int selected() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        resolve();
        PlayerInventory inv = mc.player.getInventory();
        try {
            if (getter != null) return (int) getter.invoke(inv);
            if (field != null) return field.getInt(inv);
        } catch (Throwable ignored) { }
        return 0;
    }

    public static void select(int slot) {
        if (slot < 0 || slot > 8) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (selected() == slot) return;
        resolve();
        PlayerInventory inv = mc.player.getInventory();
        try {
            if (setter != null) { setter.invoke(inv, slot); return; }
            if (field != null) { field.setInt(inv, slot); }
        } catch (Throwable ignored) { }
    }

    /** First hotbar slot whose stack passes the test, or -1. */
    public static int findHotbar(java.util.function.Predicate<ItemStack> test) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && test.test(stack)) return i;
        }
        return -1;
    }
}
