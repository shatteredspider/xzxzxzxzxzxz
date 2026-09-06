package dev.sevenclient;

import dev.sevenclient.config.ConfigManager;
import dev.sevenclient.module.ModuleManager;
import dev.sevenclient.ui.ClickGuiScreen;
import dev.sevenclient.util.EnemyManager;
import dev.sevenclient.util.FrameDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 777 Client entry point.
 *
 * Design note: this client never fabricates rotation packets. Aim modules mutate the
 * real client rotation inside the same call path the mouse uses, so the outgoing
 * PlayerMoveC2S packets are produced by vanilla code in vanilla order.
 */
public final class SevenClient implements ClientModInitializer {

    public static final String NAME = "777 Client";
    public static final String TAG = "\u00a78[\u00a7f777\u00a78] \u00a7r";
    public static final Logger LOG = LoggerFactory.getLogger("777Client");

    private static SevenClient instance;

    public ModuleManager modules;
    public EnemyManager enemies;
    public ConfigManager config;

    public static SevenClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        this.enemies = new EnemyManager();
        this.modules = new ModuleManager();
        this.config = new ConfigManager(this.modules);
        this.config.load();

        // Input injection must happen BEFORE the player ticks, so keybinding state is
        // already set when KeyboardInput#tick samples it.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;
            modules.pollBinds();
            modules.onTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;
            modules.onPostTick();
        });

        // HUD render fires once per frame, so it doubles as the fallback per-frame
        // driver for when the Mouse mixin isn't landing. WorldRenderEvents was the
        // obvious choice but no longer exists in fabric-rendering-v1 on 1.21.11.
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            FrameDispatcher.fromRenderFallback();
            modules.onHudRender(ctx);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { config.save(); } catch (Throwable ignored) { }
        }));

        LOG.info("{} initialised.", NAME);
    }

    public static void openClickGui() {
        MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
    }

    public static void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(TAG + msg), false);
    }
}
