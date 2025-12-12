package com.pathmind;

import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.screen.PathmindMainMenuIntegration;
import com.pathmind.screen.PathmindVisualEditorScreen;
import com.pathmind.ui.overlay.ActiveNodeOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client-side mod class for Pathmind.
 * This class initializes client-specific features and event handlers.
 */
public class PathmindClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pathmind/Client");
    private ActiveNodeOverlay activeNodeOverlay;
    private volatile boolean worldShutdownHandled;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Pathmind client mod");

        PresetManager.initialize();

        // Initialize the active node overlay
        this.activeNodeOverlay = new ActiveNodeOverlay();
        
        // Register keybindings
        PathmindKeybinds.registerKeybinds();
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.OPEN_VISUAL_EDITOR);
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.PLAY_GRAPHS);
        KeyBindingHelper.registerKeyBinding(PathmindKeybinds.STOP_GRAPHS);

        // Hook into the main menu for button and keyboard support
        PathmindMainMenuIntegration.register();

        // Register client tick events for keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeybinds(client);
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            if (world != null) {
                worldShutdownHandled = false;
            } else {
                handleClientShutdown("world change (null)", false);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldShutdownHandled = false;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handleClientShutdown("play disconnect", false);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            handleClientShutdown("client stopping", true);
        });
        
        // Register HUD render callback for the active node overlay
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.textRenderer != null) {
                activeNodeOverlay.render(drawContext, client.textRenderer, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            }
        });
        
        LOGGER.info("Pathmind client mod initialized successfully");
    }

    private void handleClientShutdown(String reason) {
        handleClientShutdown(reason, false);
    }

    private void handleClientShutdown(String reason, boolean force) {
        if (!force && worldShutdownHandled) {
            return;
        }
        worldShutdownHandled = true;
        LOGGER.info("Pathmind: handling client shutdown due to {}", reason);
        ExecutionManager.getInstance().requestStopAll();
    }

    private void handleKeybinds(MinecraftClient client) {
        ExecutionManager manager = ExecutionManager.getInstance();
        manager.setSingleplayerPaused(
            client != null && client.isInSingleplayer() && client.isPaused()
        );

        if (client == null || client.world == null) {
            handleClientShutdown("world unavailable", false);
            return;
        }

        // Check if visual editor keybind was pressed
        while (PathmindKeybinds.OPEN_VISUAL_EDITOR.wasPressed()) {
            if (!(client.currentScreen instanceof PathmindVisualEditorScreen)
                    && (client.currentScreen == null || client.currentScreen instanceof TitleScreen)) {
                client.setScreen(new PathmindVisualEditorScreen());
            }
        }

        while (PathmindKeybinds.STOP_GRAPHS.wasPressed()) {
            ExecutionManager.getInstance().requestStopAll();
        }

        if (client.player == null) {
            return;
        }

        while (PathmindKeybinds.PLAY_GRAPHS.wasPressed()) {
            ExecutionManager.getInstance().playAllGraphs();
        }
    }
}
