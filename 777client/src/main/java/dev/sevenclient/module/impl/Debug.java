package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.ui.Render2D;
import dev.sevenclient.ui.Theme;
import dev.sevenclient.ui.UiFont;
import dev.sevenclient.util.Diagnostics;
import dev.sevenclient.util.Rotations;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Live readout of every layer of the client.
 *
 * This exists because "the modules don't work" has at least six distinct causes
 * that all look identical from inside the game: the tick hook never registering,
 * the frame hook never registering, the attack mixin failing to apply, no valid
 * target existing, an activation condition being unmet, or a module throwing and
 * silently disabling itself. Each line below isolates one of them.
 *
 * Read it top to bottom -- the first line that looks wrong is your problem.
 */
public class Debug extends Module {

    private final BoolSetting toggleMessages = reg(new BoolSetting("Toggle Messages", true));
    private final BoolSetting showWhenGuiOpen = reg(new BoolSetting("Show Over GUI", true));

    public Debug() {
        super("Debug", "Live diagnostic readout. Turn this off once things work.", Category.RENDER);
        setEnabled(true);
        registerBindSettings();
    }

    @Override
    public void onTick() {
        Module.notifyToggles = toggleMessages.is();
    }

    @Override
    public void onHudRender(DrawContext ctx) {
        if (mc.currentScreen != null && !showWhenGuiOpen.is()) return;

        int active = 0;
        for (Module m : dev.sevenclient.SevenClient.get().modules.all()) {
            if (m.isEnabled()) active++;
        }

        List<String> lines = new ArrayList<>();
        lines.add("777 DIAGNOSTICS");
        lines.add("font      " + (UiFont.usingCustomFont() ? "custom TTF" : "vanilla (no TTF found)"));
        lines.add("ticks     " + Diagnostics.ticks + (Diagnostics.ticks > 0 ? "  ok" : "  TICK HOOK DEAD"));
        lines.add("frames    " + Diagnostics.frames + "  " + String.format("%.0f/s", Diagnostics.fps)
                + (Diagnostics.frames > 0 ? "" : "  FRAME HOOK DEAD"));
        lines.add("frame src " + (Diagnostics.frameSourceIsMixin ? "Mouse mixin" : "render fallback"));
        lines.add("dt        " + String.format("%.2f ms", Diagnostics.lastDt * 1000f)
                + String.format("  (~%.0f fps)", Diagnostics.lastDt > 0 ? 1f / Diagnostics.lastDt : 0f));
        lines.add("hitflick  armed " + Diagnostics.hitFlickArmed + "x");
        lines.add("attacks   " + Diagnostics.attackEvents
                + (Diagnostics.attackEvents > 0 ? "" : "  (hit something to test)"));
        lines.add("aim tgt   " + Diagnostics.aimTarget);
        lines.add("aim err   " + String.format("%.2f deg", Diagnostics.aimError));
        lines.add("authority " + String.format("%.2f", Diagnostics.aimAuthority)
                + (Diagnostics.aimAuthority < 0.02 ? "  (calm / in hitbox)" : ""));
        lines.add("coverage  " + String.format("%.2f", Diagnostics.aimCoverage)
                + (Diagnostics.aimCoverage > 0.9 ? "  (you've got it)" : ""));
        lines.add("flick     " + String.format("%.2f", Diagnostics.aimFlick)
                + String.format("  urgency x%.2f", Diagnostics.aimUrgency));
        lines.add("spin      " + String.format("%.2f", Diagnostics.aimSpin)
                + (Diagnostics.aimSpin > 0.3 ? "  SPIN" : ""));
        lines.add("yaw step  " + String.format("%.4f", Diagnostics.lastYawStep));
        lines.add("gcd step  " + String.format("%.4f", safeGcd()));
        lines.add("modules   " + active + " enabled");
        lines.add("error     " + (Diagnostics.lastError.equals("none")
                ? "none"
                : Diagnostics.lastErrorModule + " -> " + Diagnostics.lastError));

        int w = 0;
        for (String s : lines) w = Math.max(w, UiFont.width(s));
        int lh = UiFont.height() + 3;
        float boxW = w + 20;
        float boxH = lines.size() * lh + 14;
        // mc.getWindow().getScaledWidth() rather than ctx.getScaledWindowWidth():
        // the Window accessor has been stable for many versions.
        float x = mc.getWindow().getScaledWidth() - boxW - 6;
        float y = 6;

        Render2D.shadow(ctx, x, y, boxW, boxH, 6f, 6, Theme.SHADOW);
        Render2D.panel(ctx, x, y, boxW, boxH, 6f, Theme.WINDOW, Theme.OUTLINE);

        float ty = y + 7;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            int color = Theme.TEXT_DIM;
            if (i == 0) color = Theme.TEXT;
            // monochrome scheme: white is the attention signal, not red
            if (s.contains("DEAD")) color = Theme.ACCENT;
            if (s.startsWith("error") && !s.endsWith("none")) color = Theme.ACCENT;
            UiFont.draw(ctx, s, x + 10, ty, color);
            ty += lh;
        }
    }

    private static double safeGcd() {
        try {
            return Rotations.gcd();
        } catch (Throwable t) {
            return -1.0d;
        }
    }
}
