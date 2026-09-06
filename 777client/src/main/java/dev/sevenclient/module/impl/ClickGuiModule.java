package dev.sevenclient.module.impl;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the ClickGUI. Forced into Toggle semantics: a Hold bind on a screen
 * opener would fight the screen's own input handling.
 */
public class ClickGuiModule extends Module {

    public ClickGuiModule() {
        super("ClickGUI", "Opens the 777 Client interface.", Category.RENDER);
        bind.bind(GLFW.GLFW_KEY_RIGHT_SHIFT, false);
        bindMode.set("Toggle");
        registerBindSettings();
    }

    @Override
    public void onEnable() {
        setEnabled(false);
        // Guard: config load runs inside mod init, before the client is ready to
        // accept a screen. Only open once we are actually in a world.
        if (mc.world != null) SevenClient.openClickGui();
    }
}
