package com.pathmind.screen;

import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Simple warning screen shown when Baritone API is missing.
 */
public class MissingBaritoneApiScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;

    private final Screen parent;

    public MissingBaritoneApiScreen(Screen parent) {
        super(Text.literal("Pathmind - Missing Baritone API"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 + 10;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Open download link"),
            button -> openDownloadLink()
        ).dimensions(centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Copy link"),
            button -> copyDownloadLink()
        ).dimensions(centerX + BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(centerX - (BUTTON_WIDTH / 2), startY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void openDownloadLink() {
        if (this.client != null) {
            Util.getOperatingSystem().open(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    private void copyDownloadLink() {
        MinecraftClient mc = this.client;
        if (mc != null && mc.keyboard != null) {
            mc.keyboard.setClipboard(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int messageY = this.height / 2 - 40;

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Baritone API is required for Pathmind"), centerX, messageY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Add baritone-api-fabric-1.15.0.jar to your mods folder"), centerX, messageY + 16, 0xFFD7D7D7);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Download: " + BaritoneDependencyChecker.DOWNLOAD_URL), centerX, messageY + 32, 0xFF87CEEB);

        super.render(context, mouseX, mouseY, delta);
    }
}
