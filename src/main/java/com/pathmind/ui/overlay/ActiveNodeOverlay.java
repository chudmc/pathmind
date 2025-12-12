package com.pathmind.ui.overlay;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * HUD overlay that displays the currently active node in the top right corner.
 * Shows the node type, execution time, and status information.
 */
public class ActiveNodeOverlay {
    private static final int OVERLAY_WIDTH = 150;
    private static final int OVERLAY_HEIGHT = 60;
    private static final int MARGIN = 10;
    private static final int BACKGROUND_COLOR = 0x80000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFF666666; // Grey border
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White text
    private static final int ACCENT_COLOR = 0xFF87CEEB; // Light blue accent
    private static final int COMPLETION_COLOR = 0xFFFF5555; // Red for completion state
    
    private final ExecutionManager executionManager;
    
    public ActiveNodeOverlay() {
        this.executionManager = ExecutionManager.getInstance();
    }
    
    /**
     * Render the overlay if execution is active
     */
    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        boolean isExecuting = executionManager.isExecuting();
        boolean showingCompletion = executionManager.isDisplayingCompletion();
        Node activeNode = executionManager.getActiveNode();

        if ((!isExecuting && !showingCompletion) || (!showingCompletion && activeNode == null)) {
            return;
        }
        int overlayX = screenWidth - OVERLAY_WIDTH - MARGIN;
        int overlayY = MARGIN;
        
        // Render semi-transparent background
        context.fill(overlayX, overlayY, overlayX + OVERLAY_WIDTH, overlayY + OVERLAY_HEIGHT, BACKGROUND_COLOR);
        
        // Render border
        context.drawBorder(overlayX, overlayY, OVERLAY_WIDTH, OVERLAY_HEIGHT, BORDER_COLOR);
        
        // Calculate right-aligned text positions
        int textRightX = overlayX + OVERLAY_WIDTH - 8;
        
        // Render title (right-aligned)
        String titleText = "Active Node";
        int titleWidth = textRenderer.getWidth(titleText);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(titleText),
            textRightX - titleWidth,
            overlayY + 6,
            ACCENT_COLOR
        );
        
        // Render node type with color (right-aligned)
        String nodeTypeName;
        int nodeColor;

        if (showingCompletion) {
            if (activeNode != null) {
                nodeTypeName = activeNode.getType().getDisplayName();
                nodeColor = activeNode.getType().getColor();
            } else {
                nodeTypeName = "End";
                nodeColor = COMPLETION_COLOR;
            }
        } else {
            nodeTypeName = activeNode != null ? activeNode.getType().getDisplayName() : "";
            nodeColor = activeNode != null ? activeNode.getType().getColor() : ACCENT_COLOR;
        }
        int nodeTypeWidth = textRenderer.getWidth(nodeTypeName);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(nodeTypeName),
            textRightX - nodeTypeWidth,
            overlayY + 18,
            nodeColor
        );
        
        // Render execution time (right-aligned)
        long executionDuration = executionManager.getActiveNodeDuration();
        String timeText = "Node Time: " + formatDuration(executionDuration);
        int timeWidth = textRenderer.getWidth(timeText);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(timeText),
            textRightX - timeWidth,
            overlayY + 30,
            TEXT_COLOR
        );
        
        // Render status indicator (right-aligned)
        String statusText = showingCompletion ? "Finished" : "Executing...";
        int statusWidth = textRenderer.getWidth(statusText);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(statusText),
            textRightX - statusWidth,
            overlayY + 42,
            showingCompletion ? COMPLETION_COLOR : ACCENT_COLOR
        );
        
        // Render a small colored indicator dot (top left)
        int dotX = overlayX + 8;
        int dotY = overlayY + 8;
        context.fill(dotX, dotY, dotX + 8, dotY + 8, nodeColor);
        context.drawBorder(dotX, dotY, 8, 8, BORDER_COLOR);
    }
    
    /**
     * Format duration in milliseconds to a readable format
     */
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d.%ds", seconds, (milliseconds % 1000) / 100);
        }
    }
}
