package dev.sevenclient.module.impl;

import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.ui.Theme;
import dev.sevenclient.ui.UiFont;
import net.minecraft.client.gui.DrawContext;

public class Watermark extends Module {

    private final BoolSetting showFps = reg(new BoolSetting("Show FPS", true));
    private final BoolSetting showEnemies = reg(new BoolSetting("Show Enemy Count", true));

    public Watermark() {
        super("Watermark", "Minimal corner watermark.", Category.RENDER);
        setEnabled(true);
        registerBindSettings();
    }

    @Override
    public void onHudRender(DrawContext ctx) {
        if (mc.currentScreen != null) return;

        StringBuilder sb = new StringBuilder("777");
        if (showFps.is()) sb.append("  ").append(mc.getCurrentFps()).append(" fps");
        if (showEnemies.is()) {
            int n = dev.sevenclient.SevenClient.get().enemies.size();
            if (n > 0) sb.append("  ").append(n).append(" enemy").append(n == 1 ? "" : "s");
        }
        String text = sb.toString();

        int w = UiFont.width(text) + 26;
        int h = 18;
        dev.sevenclient.ui.Render2D.shadow(ctx, 6, 6, w, h, 6f, 5, Theme.SHADOW);
        dev.sevenclient.ui.Render2D.panel(ctx, 6, 6, w, h, 6f, Theme.WINDOW, Theme.OUTLINE);
        dev.sevenclient.ui.Render2D.roundedRect(ctx, 11, 6 + h / 2f - 1.5f, 3, 3, 1.5f, Theme.ACCENT);
        UiFont.draw(ctx, text, 18, 6 + (h - UiFont.height()) / 2f + 0.5f, Theme.TEXT);
    }
}
