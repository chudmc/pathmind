package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.NodeGraph;
import com.pathmind.ui.NodeParameterOverlay;
import com.pathmind.ui.Sidebar;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The main visual editor screen for Pathmind.
 * This screen provides the interface for creating and editing node-based workflows.
 */
public class PathmindVisualEditorScreen extends Screen {
    private static final int TITLE_BAR_HEIGHT = 20;
    
    // Dark mode color palette
    private static final int DARK_GREY = 0xFF1A1A1A;        // Very dark grey background
    private static final int DARK_GREY_ALT = 0xFF2A2A2A;     // Slightly lighter dark grey
    private static final int GREY_LINE = 0xFF666666;         // Grey line color
    private static final int WHITE = 0xFFFFFFFF;             // Pure white text
    private static final int ACCENT_COLOR = 0xFF87CEEB;
    private static final int SUCCESS_COLOR = 0xFF6DCB5A;
    private static final int ERROR_COLOR = 0xFFE57373;
    private static final int OVERLAY_BACKGROUND = 0xAA000000;
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int PRESET_DROPDOWN_WIDTH = 160;
    private static final int PRESET_DROPDOWN_HEIGHT = 18;
    private static final int PRESET_DROPDOWN_MARGIN = 8;
    private static final int PRESET_OPTION_HEIGHT = 18;
    private static final int PRESET_TEXT_LEFT_PADDING = 6;
    private static final int PRESET_DELETE_ICON_SIZE = 8;
    private static final int PRESET_DELETE_ICON_MARGIN = 6;
    private static final int PRESET_DELETE_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_TEXT_ICON_GAP = 4;
    private static final int CREATE_PRESET_POPUP_WIDTH = 320;
    private static final int CREATE_PRESET_POPUP_HEIGHT = 170;
    private static final int PLAY_BUTTON_SIZE = 18;
    private static final int PLAY_BUTTON_MARGIN = 8;
    private static final int STOP_BUTTON_SIZE = 18;
    private static final int CONTROL_BUTTON_GAP = 6;
    private static final int ZOOM_BUTTON_SIZE = 14;
    private static final int ZOOM_BUTTON_MARGIN = 6;
    private static final int ZOOM_BUTTON_SPACING = 4;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    private static final int TITLE_INTERACTION_PADDING = 4;
    private static final String INFO_POPUP_AUTHOR = "ryduzz";
    private static final String INFO_POPUP_TARGET_VERSION = "1.21.8";
    private static final Text TITLE_TEXT = Text.literal("Pathmind Node Editor");

    private NodeGraph nodeGraph;
    private Sidebar sidebar;
    private NodeParameterOverlay parameterOverlay;

    // Drag and drop state
    private boolean isDraggingFromSidebar = false;
    private NodeType draggingNodeType = null;

    // Workspace dialogs
    private boolean clearPopupVisible = false;
    private boolean importExportPopupVisible = false;
    private Path lastImportExportPath;
    private String importExportStatus = "";
    private int importExportStatusColor = 0xFFCCCCCC;

    private boolean presetDropdownOpen = false;
    private List<String> availablePresets = new ArrayList<>();
    private String activePresetName = "";
    private boolean createPresetPopupVisible = false;
    private TextFieldWidget createPresetField;
    private String createPresetStatus = "";
    private int createPresetStatusColor = 0xFFCCCCCC;
    private boolean infoPopupVisible = false;

    public PathmindVisualEditorScreen() {
        super(Text.translatable("screen.pathmind.visual_editor.title"));
        this.nodeGraph = new NodeGraph();
        this.sidebar = new Sidebar();
        refreshAvailablePresets();
        this.nodeGraph.setActivePreset(activePresetName);
        updateImportExportPathFromPreset();
    }

    @Override
    protected void init() {
        super.init();
        // No buttons needed - just the title bar

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);

        if (createPresetField == null) {
            createPresetField = new TextFieldWidget(this.textRenderer, 0, 0, 200, 20, Text.literal("Preset Name"));
            createPresetField.setMaxLength(64);
            createPresetField.setDrawsBackground(false);
            createPresetField.setVisible(false);
            createPresetField.setEditable(false);
            createPresetField.setEditableColor(WHITE);
            createPresetField.setUneditableColor(0xFF888888);
            createPresetField.setChangedListener(value -> clearCreatePresetStatus());
            this.addSelectableChild(createPresetField);
        }

        updateImportExportPathFromPreset();

        // Try to load saved node graph first
        if (nodeGraph.hasSavedGraph()) {
            System.out.println("Found saved node graph, loading...");
            if (nodeGraph.load()) {
                System.out.println("Successfully loaded saved node graph");
                return; // Don't initialize default nodes if we loaded a saved graph
            } else {
                System.out.println("Failed to load saved node graph, using default");
            }
        }
        
        // Initialize node graph with proper centering based on screen dimensions
        nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fill background with dark grey theme
        context.fill(0, 0, this.width, this.height, DARK_GREY);
        
        // Render title bar at the top
        context.fill(0, 0, this.width, TITLE_BAR_HEIGHT, DARK_GREY_ALT);
        context.drawHorizontalLine(0, this.width, TITLE_BAR_HEIGHT, GREY_LINE);
        
        boolean titleHovered = isTitleHovered(mouseX, mouseY);

        // Render title bar text
        drawTitle(context, titleHovered);
        
        // Update mouse hover for socket highlighting
        nodeGraph.updateMouseHover(mouseX, mouseY);
        nodeGraph.setSidebarWidth(sidebar.getWidth());
        
        // Render node graph (stationary nodes only)
        renderNodeGraph(context, mouseX, mouseY, delta, false);

        // Workspace utilities should sit beneath the sidebar when it expands
        renderWorkspaceButtons(context, mouseX, mouseY);
        
        // Always render sidebar after node graph/buttons so expanded categories sit on top
        sidebar.render(context, this.textRenderer, mouseX, mouseY, TITLE_BAR_HEIGHT, this.height - TITLE_BAR_HEIGHT);
        
        // Render dragged nodes above sidebar
        renderNodeGraph(context, mouseX, mouseY, delta, true);
        
        // Render dragging node from sidebar
        if (isDraggingFromSidebar && draggingNodeType != null) {
            renderDraggingNode(context, mouseX, mouseY);
        }
        
        boolean controlsDisabled = isPopupObscuringWorkspace();
        renderZoomControls(context, mouseX, mouseY, controlsDisabled);

        if (shouldShowExecutionControls()) {
            renderStopButton(context, mouseX, mouseY, controlsDisabled);
            renderPlayButton(context, mouseX, mouseY, controlsDisabled);
        }
        renderPresetDropdown(context, mouseX, mouseY, controlsDisabled);

        // Render parameter overlay if visible
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.render(context, this.textRenderer, mouseX, mouseY, delta);
        }

        if (clearPopupVisible) {
            renderClearConfirmationPopup(context, mouseX, mouseY);
        }

        if (importExportPopupVisible) {
            renderImportExportPopup(context, mouseX, mouseY, delta);
        }

        if (createPresetPopupVisible) {
            renderCreatePresetPopup(context, mouseX, mouseY, delta);
        }

        if (infoPopupVisible) {
            renderInfoPopup(context, mouseX, mouseY);
        }

        // Re-render title bar on top of everything to ensure it's always visible
        context.fill(0, 0, this.width, TITLE_BAR_HEIGHT, DARK_GREY_ALT);
        context.drawHorizontalLine(0, this.width, TITLE_BAR_HEIGHT, GREY_LINE);
        drawTitle(context, titleHovered);

        // Controls are already rendered before overlays so they appear dimmed underneath
    }

    private boolean isPopupObscuringWorkspace() {
        boolean overlayVisible = parameterOverlay != null && parameterOverlay.isVisible();
        return overlayVisible || clearPopupVisible || importExportPopupVisible || createPresetPopupVisible || infoPopupVisible;
    }
    
    private boolean shouldShowExecutionControls() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null;
    }
    
    private void renderDraggingNode(DrawContext context, int mouseX, int mouseY) {
        if (draggingNodeType == null) return;
        
        // Create a temporary node for rendering
        Node tempNode = new Node(draggingNodeType, 0, 0);
        tempNode.setDragging(true);
        
        // Calculate proper centering based on node dimensions
        int width = tempNode.getWidth();
        int height = tempNode.getHeight();
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        
        // Update temp node position for rendering
        tempNode.setPosition(x, y);
        
        // Render the node with a slight transparency
        int alpha = 0x80;
        int nodeColor = (draggingNodeType.getColor() & 0x00FFFFFF) | alpha;
        
        // Node background with transparency
        context.fill(x, y, x + width, y + height, 0x802A2A2A);
        // Draw grey outline for dragging state
        context.drawBorder(x, y, width, height, 0xFFAAAAAA);
        
        // Node header
        if (draggingNodeType != NodeType.START && draggingNodeType != NodeType.EVENT_FUNCTION) {
            context.fill(x + 1, y + 1, x + width - 1, y + 14, nodeColor);
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(draggingNodeType.getDisplayName()),
                x + 4,
                y + 4,
                0xFFFFFFFF
            );
        }
    }

    private void renderZoomControls(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonY = getZoomButtonY();
        drawZoomButton(context, getZoomMinusButtonX(), buttonY, mouseX, mouseY, disabled, true, false);
        drawZoomButton(context, getZoomPlusButtonX(), buttonY, mouseX, mouseY, disabled, false, false);
    }

    private void drawZoomButton(DrawContext context, int x, int y, int mouseX, int mouseY, boolean disabled, boolean isMinus, boolean active) {
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
        int bgColor = active ? 0xFF353535 : 0xFF2A2A2A;
        if (hovered) {
            bgColor = active ? 0xFF3F3F3F : 0xFF333333;
        } else if (disabled) {
            bgColor = 0xFF1E1E1E;
        }
        context.fill(x + 1, y + 1, x + ZOOM_BUTTON_SIZE - 1, y + ZOOM_BUTTON_SIZE - 1, bgColor);

        int borderColor = active ? ACCENT_COLOR : GREY_LINE;
        if (hovered) {
            borderColor = ACCENT_COLOR;
        } else if (disabled) {
            borderColor = GREY_LINE;
        }
        context.drawBorder(x, y, ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE, borderColor);

        int iconColor;
        if (disabled) {
            iconColor = 0xFF555555;
        } else if (hovered || active) {
            iconColor = ACCENT_COLOR;
        } else {
            iconColor = WHITE;
        }

        Text iconText = Text.literal(isMinus ? "-" : "+");
        int iconWidth = this.textRenderer.getWidth(iconText);
        int iconX = x + (ZOOM_BUTTON_SIZE - iconWidth) / 2 + 1;
        int iconY = y + (ZOOM_BUTTON_SIZE - this.textRenderer.fontHeight) / 2 + 2;
        context.drawTextWithShadow(this.textRenderer, iconText, iconX, iconY, iconColor);
    }
    
    private void renderNodeGraph(DrawContext context, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        if (!onlyDragged) {
            // Node graph background
            context.fill(Sidebar.getCollapsedWidth(), TITLE_BAR_HEIGHT, this.width, this.height, DARK_GREY);
            
            // Render grid pattern for better visual organization
            renderGrid(context);
        }
        
        // Render nodes
        nodeGraph.render(context, this.textRenderer, mouseX, mouseY, delta, onlyDragged);
    }
    
    private void renderGrid(DrawContext context) {
        int gridSize = 20;
        int startX = Sidebar.getCollapsedWidth();
        int startY = TITLE_BAR_HEIGHT;
        int endX = this.width;
        int endY = this.height;

        int leftWorld = nodeGraph.screenToWorldX(startX);
        int rightWorld = nodeGraph.screenToWorldX(endX);
        if (rightWorld < leftWorld) {
            int swap = leftWorld;
            leftWorld = rightWorld;
            rightWorld = swap;
        }

        int topWorld = nodeGraph.screenToWorldY(startY);
        int bottomWorld = nodeGraph.screenToWorldY(endY);
        if (bottomWorld < topWorld) {
            int swap = topWorld;
            topWorld = bottomWorld;
            bottomWorld = swap;
        }

        int firstVertical = leftWorld - Math.floorMod(leftWorld, gridSize);
        for (int worldX = firstVertical; worldX <= rightWorld + gridSize; worldX += gridSize) {
            int screenX = nodeGraph.worldToScreenX(worldX);
            if (screenX < startX || screenX > endX) {
                continue;
            }
            context.drawVerticalLine(screenX, startY, endY, 0x40333333);
        }

        int firstHorizontal = topWorld - Math.floorMod(topWorld, gridSize);
        for (int worldY = firstHorizontal; worldY <= bottomWorld + gridSize; worldY += gridSize) {
            int screenY = nodeGraph.worldToScreenY(worldY);
            if (screenY < startY || screenY > endY) {
                continue;
            }
            context.drawHorizontalLine(startX, endX, screenY, 0x40333333);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (infoPopupVisible) {
            if (handleInfoPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (createPresetPopupVisible) {
            if (createPresetField != null && createPresetField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (handleCreatePresetPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (clearPopupVisible) {
            if (handleClearPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (importExportPopupVisible) {
            if (handleImportExportPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (!isPopupObscuringWorkspace() && button == 0 && shouldShowExecutionControls()) {
            if (isPointInPlayButton((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                startExecutingAllGraphs();
                return true;
            }
            if (isPointInStopButton((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                stopExecutingAllGraphs();
                return true;
            }
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (isPointInZoomMinus((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                nodeGraph.zoomOut(getWorkspaceCenterX(), getWorkspaceCenterY());
                return true;
            }
            if (isPointInZoomPlus((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                nodeGraph.zoomIn(getWorkspaceCenterX(), getWorkspaceCenterY());
                return true;
            }
        }

        if (button == 0) {
            if (isPointInRect((int)mouseX, (int)mouseY, getPresetDropdownX(), getPresetDropdownY(), PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT)) {
                presetDropdownOpen = !presetDropdownOpen;
                return true;
            }

            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                openInfoPopup();
                return true;
            }

            if (presetDropdownOpen && handlePresetDropdownSelection(mouseX, mouseY)) {
                return true;
            }
        }

        if (presetDropdownOpen && !isPointInRect((int)mouseX, (int)mouseY, getPresetDropdownX(), getPresetDropdownY(), PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT + getPresetDropdownOptionsHeight())) {
            presetDropdownOpen = false;
        }

        // Handle parameter overlay clicks first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Check if clicking home button
        if (isHomeButtonClicked((int)mouseX, (int)mouseY, button)) {
            nodeGraph.resetCamera();
            return true;
        }

        if (isClearButtonClicked((int)mouseX, (int)mouseY, button)) {
            openClearPopup();
            return true;
        }

        if (isImportExportButtonClicked((int)mouseX, (int)mouseY, button)) {
            openImportExportPopup();
            return true;
        }

        // Check if clicking in sidebar to add nodes
        if (mouseX < sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            if (sidebar.mouseClicked(mouseX, mouseY, button)) {
                // Check if we should start dragging a node from sidebar
                if (sidebar.isHoveringNode()) {
                    isDraggingFromSidebar = true;
                    draggingNodeType = sidebar.getHoveredNodeType();
                    nodeGraph.resetDropTargets();
                }
                return true;
            }
        }
        
        // Check if clicking on nodes in the graph area
        if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            // Handle right-click or middle-click for panning
            if (button == 1 || button == 2) { // Right click or middle click
                nodeGraph.startPanning((int)mouseX, (int)mouseY);
                return true;
            }
            
            if (button == 0 && nodeGraph.handleStartButtonClick((int) mouseX, (int) mouseY)) {
                presetDropdownOpen = false;
                if (nodeGraph.didLastStartButtonTriggerExecution()) {
                    dismissParameterOverlay();
                    isDraggingFromSidebar = false;
                    draggingNodeType = null;
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                }
                return true;
            }
            
            return handleNodeGraphClick(mouseX, mouseY, button);
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    
    private boolean handleNodeGraphClick(double mouseX, double mouseY, int button) {
        int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
        int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
        // FIRST check if clicking on ANY socket (before checking node body)
        for (Node node : nodeGraph.getNodes()) {
            if (!node.shouldRenderSockets()) {
                continue;
            }
            // Check input sockets
            for (int i = 0; i < node.getInputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, true)) {
                    if (button == 0) { // Left click - start dragging connection from input
                        nodeGraph.stopCoordinateEditing(true);
                        nodeGraph.stopAmountEditing(true);
                        nodeGraph.startDraggingConnection(node, i, false, (int)mouseX, (int)mouseY);
                        return true;
                    }
                }
            }

            // Check output sockets
            for (int i = 0; i < node.getOutputSocketCount(); i++) {
                if (node.isSocketClicked(worldMouseX, worldMouseY, i, false)) {
                    if (button == 0) { // Left click - start dragging connection from output
                        nodeGraph.stopCoordinateEditing(true);
                        nodeGraph.stopAmountEditing(true);
                        nodeGraph.startDraggingConnection(node, i, true, (int)mouseX, (int)mouseY);
                        return true;
                    }
                }
            }
        }
        
        // THEN check if clicking on node body
        Node clickedNode = nodeGraph.getNodeAt((int)mouseX, (int)mouseY);
        
        if (clickedNode != null) {
            // Node body clicked (not socket)
            if (button == 0) { // Left click - select node or start dragging
                int coordinateAxis = nodeGraph.getCoordinateFieldAxisAt(clickedNode, (int)mouseX, (int)mouseY);
                if (coordinateAxis != -1) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startCoordinateEditing(clickedNode, coordinateAxis);
                    return true;
                }

                if (nodeGraph.isPointInsideAmountField(clickedNode, (int)mouseX, (int)mouseY)) {
                    nodeGraph.selectNode(clickedNode);
                    nodeGraph.startAmountEditing(clickedNode);
                    return true;
                }

                nodeGraph.stopAmountEditing(true);
                nodeGraph.stopCoordinateEditing(true);

                // Check for double-click to open parameter editor
                boolean shouldOpenOverlay = clickedNode.isParameterNode()
                    || clickedNode.getType() == NodeType.EVENT_CALL
                    || clickedNode.getType() == NodeType.EVENT_FUNCTION;
                if (shouldOpenOverlay &&
                    nodeGraph.handleNodeClick(clickedNode, (int)mouseX, (int)mouseY)) {
                    // Open parameter overlay
                    nodeGraph.stopCoordinateEditing(true);
                    nodeGraph.stopAmountEditing(true);
                    parameterOverlay = new NodeParameterOverlay(
                        clickedNode,
                        this.width,
                        this.height,
                        TITLE_BAR_HEIGHT,
                        () -> parameterOverlay = null, // Clear reference on close
                        nodeGraph::notifyNodeParametersChanged,
                        () -> nodeGraph.getFunctionNames()
                    );
                    parameterOverlay.init();
                    parameterOverlay.show();
                    return true;
                }
                
                nodeGraph.selectNode(clickedNode);
                nodeGraph.startDragging(clickedNode, (int)mouseX, (int)mouseY);
                return true;
            }
        } else {
            // Check if clicking on a connection to delete it
            var connection = nodeGraph.getConnectionAt((int)mouseX, (int)mouseY);
            if (connection != null && button == 1) {
                nodeGraph.getConnections().remove(connection);
                return true;
            }
            
            // Clicked on empty space - deselect and stop dragging
            nodeGraph.selectNode(null);
            nodeGraph.stopDraggingConnection();
            if (button == 0) {
                nodeGraph.stopCoordinateEditing(true);
                nodeGraph.stopAmountEditing(true);
            }
            return true;
        }

        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (createPresetPopupVisible) {
            return true;
        }

        if (clearPopupVisible) {
            return true;
        }

        if (importExportPopupVisible) {
            return true;
        }

        // Handle dragging from sidebar
        if (isDraggingFromSidebar && button == 0) {
            if (draggingNodeType != null && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                nodeGraph.previewSidebarDrag(draggingNodeType, worldMouseX, worldMouseY);
            } else {
                nodeGraph.resetDropTargets();
            }
            return true; // Continue dragging
        }
        
        // Handle node dragging and connection dragging
        if (button == 0) {
            nodeGraph.updateDrag((int)mouseX, (int)mouseY);
            return true;
        }
        
        // Handle panning with right-click or middle-click
        if ((button == 1 || button == 2) && nodeGraph.isPanning()) {
            nodeGraph.updatePanning((int)mouseX, (int)mouseY);
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (infoPopupVisible) {
            return true;
        }

        if (createPresetPopupVisible) {
            if (createPresetField != null) {
                createPresetField.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (clearPopupVisible) {
            return true;
        }

        if (importExportPopupVisible) {
            return true;
        }

        if (button == 0) {
            // Handle dropping node from sidebar
            if (isDraggingFromSidebar) {
                if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
                    int worldMouseX = nodeGraph.screenToWorldX((int) mouseX);
                    int worldMouseY = nodeGraph.screenToWorldY((int) mouseY);
                    Node newNode = nodeGraph.handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
                    if (newNode != null) {
                        nodeGraph.selectNode(newNode);
                    }
                }
                // Reset drag state
                isDraggingFromSidebar = false;
                draggingNodeType = null;
                nodeGraph.resetDropTargets();
            } else {
                // Check if dragging node into sidebar for deletion (only if actually dragging)
                if (nodeGraph.getSelectedNode() != null && nodeGraph.getSelectedNode().isDragging()) {
                    nodeGraph.deleteNodeIfInSidebar(nodeGraph.getSelectedNode(), (int)mouseX, sidebar.getWidth());
                }
                
                nodeGraph.stopDragging();
                nodeGraph.stopDraggingConnection();
            }
        } else if (button == 1 || button == 2) {
            // Stop panning on right-click or middle-click release
            nodeGraph.stopPanning();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (infoPopupVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeInfoPopup();
                return true;
            }
            return true;
        }

        if (createPresetPopupVisible) {
            if (createPresetField != null && createPresetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeCreatePresetPopup();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptCreatePreset();
                return true;
            }

            return true;
        }

        if (clearPopupVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearPopupVisible = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmClearWorkspace();
                return true;
            }
            return true;
        }

        if (importExportPopupVisible) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeImportExportPopup();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptImport();
                return true;
            }

            return true;
        }

        if (presetDropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presetDropdownOpen = false;
            return true;
        }

        // Handle parameter overlay key presses first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (nodeGraph.handleAmountKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleCoordinateKeyPressed(keyCode, modifiers)) {
            return true;
        }

        // Close screen with Escape key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        boolean isCtrlOrCmdDown = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean isShiftDown = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (isCtrlOrCmdDown && keyCode == GLFW.GLFW_KEY_Z) {
            if (isShiftDown) {
                if (nodeGraph.redo()) {
                    return true;
                }
            } else {
                if (nodeGraph.undo()) {
                    return true;
                }
            }
        }

        if (isCtrlOrCmdDown && keyCode == GLFW.GLFW_KEY_Y) {
            if (nodeGraph.redo()) {
                return true;
            }
        }
        
        // Delete selected node with Delete key
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (nodeGraph.deleteSelectedNode()) {
                return true;
            }
        }

        if (isCtrlOrCmdDown && (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD)) {
            nodeGraph.zoomIn(getWorkspaceCenterX(), getWorkspaceCenterY());
            return true;
        }

        if (isCtrlOrCmdDown && (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT)) {
            nodeGraph.zoomOut(getWorkspaceCenterX(), getWorkspaceCenterY());
            return true;
        }

        // Copy/Paste/Duplicate selected node
        if (isCtrlOrCmdDown && keyCode == GLFW.GLFW_KEY_C) {
            if (nodeGraph.copySelectedNodeToClipboard()) {
                return true;
            }
        }

        if (isCtrlOrCmdDown && keyCode == GLFW.GLFW_KEY_V) {
            if (nodeGraph.pasteClipboardNode() != null) {
                return true;
            }
        }

        if (isCtrlOrCmdDown && keyCode == GLFW.GLFW_KEY_D) {
            if (nodeGraph.duplicateSelectedNode() != null) {
                return true;
            }
        }
        
        // Don't handle the opening keybind - let it be ignored
        // This prevents the screen from closing when the same key is pressed
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (infoPopupVisible) {
            return true;
        }

        if (createPresetPopupVisible) {
            if (createPresetField != null && createPresetField.charTyped(chr, modifiers)) {
                return true;
            }
            return true;
        }

        if (clearPopupVisible) {
            return true;
        }

        if (importExportPopupVisible) {
            return true;
        }

        // Handle parameter overlay character typing first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.charTyped(chr, modifiers)) {
                return true;
            }
        }

        if (nodeGraph.handleAmountCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        if (nodeGraph.handleCoordinateCharTyped(chr, modifiers, this.textRenderer)) {
            return true;
        }

        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (infoPopupVisible) {
            return true;
        }

        if (createPresetPopupVisible) {
            if (createPresetField != null && createPresetField.mouseScrolled(mouseX, mouseY, 0.0, verticalAmount)) {
                return true;
            }
            return true;
        }

        if (clearPopupVisible) {
            return true;
        }

        if (importExportPopupVisible) {
            return true;
        }

        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (presetDropdownOpen) {
            return true;
        }

        // Handle sidebar scrolling
        if (mouseX >= 0 && mouseX <= sidebar.getWidth()) {
            if (sidebar.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private boolean hasSavedOnClose = false;

    private void autoSaveWorkspace() {
        if (hasSavedOnClose) {
            return;
        }

        hasSavedOnClose = true;

        nodeGraph.stopCoordinateEditing(true);
        nodeGraph.stopAmountEditing(true);

        if (nodeGraph.save()) {
            System.out.println("Node graph auto-saved successfully");
        } else {
            System.err.println("Failed to auto-save node graph");
        }

        PresetManager.setActivePreset(activePresetName);
    }

    @Override
    public void close() {
        autoSaveWorkspace();
        super.close();
    }

    @Override
    public void removed() {
        autoSaveWorkspace();
        super.removed();
    }

    private void renderClearConfirmationPopup(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, this.height, OVERLAY_BACKGROUND);

        int popupWidth = 280;
        int popupHeight = 150;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, DARK_GREY_ALT);
        context.drawBorder(popupX, popupY, popupWidth, popupHeight, GREY_LINE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Clear workspace?"),
            popupX + popupWidth / 2,
            popupY + 14,
            WHITE
        );

        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("This will remove all nodes from the workspace."),
            popupX + 20,
            popupY + 48,
            0xFFCCCCCC
        );

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int confirmX = popupX + popupWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean confirmHovered = isPointInRect(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.literal("Cancel"), false);
        drawPopupButton(context, confirmX, buttonY, buttonWidth, buttonHeight, confirmHovered, Text.literal("Clear"), true);
    }

    private void renderImportExportPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, OVERLAY_BACKGROUND);

        int popupWidth = 360;
        int popupHeight = 210;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, DARK_GREY_ALT);
        context.drawBorder(popupX, popupY, popupWidth, popupHeight, GREY_LINE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Import / Export Workspace"),
            popupX + popupWidth / 2,
            popupY + 14,
            WHITE
        );

        int infoY = popupY + 44;
        String importInfo = "Click Import to load a saved workspace.";
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(importInfo),
            popupX + 20,
            infoY,
            0xFFCCCCCC
        );

        String exportInfo = "Click Export to choose where to save the current workspace.";
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(exportInfo),
            popupX + 20,
            infoY + 14,
            0xFFCCCCCC
        );

        Path defaultPath = NodeGraphPersistence.getDefaultSavePath();
        if (defaultPath != null) {
            String defaultLabel = "Default save: " + defaultPath.toString();
            String trimmedDefault = this.textRenderer.trimToWidth(defaultLabel, popupWidth - 40);
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(trimmedDefault),
                popupX + 20,
                infoY + 30,
                0xFF888888
            );
        }

        if (!importExportStatus.isEmpty()) {
            int textAreaWidth = popupWidth - 40;
            String statusText = this.textRenderer.trimToWidth(importExportStatus, textAreaWidth);
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(statusText),
                popupX + 20,
                popupY + popupHeight - 56,
                importExportStatusColor
            );
        }

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int importX = popupX + 20;
        int exportX = importX + buttonWidth + 8;
        int cancelX = popupX + popupWidth - buttonWidth - 20;

        boolean importHovered = isPointInRect(mouseX, mouseY, importX, buttonY, buttonWidth, buttonHeight);
        boolean exportHovered = isPointInRect(mouseX, mouseY, exportX, buttonY, buttonWidth, buttonHeight);
        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, importX, buttonY, buttonWidth, buttonHeight, importHovered, Text.literal("Import"), true);
        drawPopupButton(context, exportX, buttonY, buttonWidth, buttonHeight, exportHovered, Text.literal("Export"), false);
        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.literal("Close"), false);
    }

    private void renderInfoPopup(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, this.width, this.height, OVERLAY_BACKGROUND);

        int popupWidth = INFO_POPUP_WIDTH;
        int popupHeight = INFO_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, DARK_GREY_ALT);
        context.drawBorder(popupX, popupY, popupWidth, popupHeight, GREY_LINE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            TITLE_TEXT,
            popupX + popupWidth / 2,
            popupY + 14,
            WHITE
        );

        int textStartY = popupY + 42;
        int lineSpacing = 12;
        int centerX = popupX + popupWidth / 2;

        String authorLine = "Created by: " + INFO_POPUP_AUTHOR;
        String targetLine = "Built for Minecraft: " + INFO_POPUP_TARGET_VERSION;
        String currentLine = "Running Minecraft: " + getCurrentMinecraftVersion();
        String buildLine = "Current Build: " + getModVersion();
        String loaderLine = "Fabric Loader: " + getFabricLoaderVersion();

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(authorLine), centerX, textStartY, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(targetLine), centerX, textStartY + lineSpacing, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(currentLine), centerX, textStartY + lineSpacing * 2, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(buildLine), centerX, textStartY + lineSpacing * 3, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(loaderLine), centerX, textStartY + lineSpacing * 4, 0xFFCCCCCC);

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = popupX + (popupWidth - buttonWidth) / 2;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        boolean closeHovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, buttonX, buttonY, buttonWidth, buttonHeight, closeHovered, Text.literal("Close"), false);
    }

    private void drawTitle(DrawContext context, boolean underline) {
        int centerX = this.width / 2;
        int textY = (TITLE_BAR_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawCenteredTextWithShadow(this.textRenderer, TITLE_TEXT, centerX, textY, WHITE);

        if (underline) {
            int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
            int underlineStartX = centerX - textWidth / 2;
            int underlineY = textY + this.textRenderer.fontHeight;
            context.fill(underlineStartX, underlineY, underlineStartX + textWidth, underlineY + 1, WHITE);
        }
    }

    private boolean handleClearPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = 280;
        int popupHeight = 150;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int confirmX = popupX + popupWidth - buttonWidth - 20;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, confirmX, buttonY, buttonWidth, buttonHeight)) {
            confirmClearWorkspace();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            clearPopupVisible = false;
            return true;
        }

        return true;
    }

    private boolean handleImportExportPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = 360;
        int popupHeight = 210;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int importX = popupX + 20;
        int exportX = importX + buttonWidth + 8;
        int cancelX = popupX + popupWidth - buttonWidth - 20;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, importX, buttonY, buttonWidth, buttonHeight)) {
            attemptImport();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, exportX, buttonY, buttonWidth, buttonHeight)) {
            attemptExport();
            return true;
        }

        if (isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeImportExportPopup();
            return true;
        }

        return true;
    }

    private boolean handleInfoPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int popupWidth = INFO_POPUP_WIDTH;
        int popupHeight = INFO_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = popupX + (popupWidth - buttonWidth) / 2;
        int buttonY = popupY + popupHeight - buttonHeight - 16;

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeInfoPopup();
            return true;
        }

        if (!isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            closeInfoPopup();
            return true;
        }

        return true;
    }

    private void drawPopupButton(DrawContext context, int x, int y, int width, int height, boolean hovered, Text label, boolean primary) {
        int bgColor = primary ? (hovered ? 0xFF3B6A7D : 0xFF2F4F5C) : (hovered ? 0xFF505050 : 0xFF3A3A3A);
        int borderColor = hovered || primary ? ACCENT_COLOR : 0xFF666666;
        context.fill(x, y, x + width, y + height, bgColor);
        context.drawBorder(x, y, width, height, borderColor);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            label,
            x + width / 2,
            y + (height - this.textRenderer.fontHeight) / 2 + 1,
            WHITE
        );
    }

    private void openInfoPopup() {
        dismissParameterOverlay();
        clearPopupVisible = false;
        importExportPopupVisible = false;
        if (createPresetPopupVisible) {
            closeCreatePresetPopup();
        }
        presetDropdownOpen = false;
        infoPopupVisible = true;
    }

    private void closeInfoPopup() {
        infoPopupVisible = false;
    }

    private void openClearPopup() {
        dismissParameterOverlay();
        closeImportExportPopup();
        if (createPresetPopupVisible) {
            closeCreatePresetPopup();
        }
        closeInfoPopup();
        presetDropdownOpen = false;
        clearPopupVisible = true;
    }

    private void confirmClearWorkspace() {
        nodeGraph.clearWorkspace();
        clearPopupVisible = false;
    }

    private void openImportExportPopup() {
        dismissParameterOverlay();
        clearPopupVisible = false;
        if (createPresetPopupVisible) {
            closeCreatePresetPopup();
        }
        closeInfoPopup();
        presetDropdownOpen = false;
        importExportPopupVisible = true;
        clearImportExportStatus();
        if (lastImportExportPath == null) {
            lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
        }
    }

    private void closeImportExportPopup() {
        importExportPopupVisible = false;
    }

    private void attemptImport() {
        String defaultPath = lastImportExportPath != null
                ? lastImportExportPath.toString()
                : Optional.ofNullable(NodeGraphPersistence.getDefaultSavePath())
                    .map(Path::toString)
                    .orElse("");

        String selection;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json"));
            filters.flip();
            selection = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Workspace",
                    defaultPath,
                    filters,
                    "JSON Files",
                    false
            );
        }

        if (selection == null) {
            setImportExportStatus("Import cancelled.", 0xFFCCCCCC);
            return;
        }

        try {
            Path path = Paths.get(selection.trim());
            boolean success = nodeGraph.importFromPath(path);
            if (success) {
                lastImportExportPath = path;
                Path fileName = path.getFileName();
                setImportExportStatus("Imported workspace from " + (fileName != null ? fileName.toString() : path.toString()), SUCCESS_COLOR);
            } else {
                setImportExportStatus("Failed to import workspace from file.", ERROR_COLOR);
            }
        } catch (InvalidPathException ex) {
            setImportExportStatus("Invalid file path.", ERROR_COLOR);
        }
    }

    private void attemptExport() {
        Path defaultSavePath = Optional.ofNullable(lastImportExportPath)
                .orElseGet(NodeGraphPersistence::getDefaultSavePath);
        String defaultPathString = defaultSavePath != null ? defaultSavePath.toString() : "workspace.json";

        String selection;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json"));
            filters.flip();
            selection = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Workspace",
                    defaultPathString,
                    filters,
                    "JSON Files"
            );
        }

        if (selection == null) {
            setImportExportStatus("Export cancelled.", 0xFFCCCCCC);
            return;
        }

        try {
            Path path = Paths.get(selection.trim());
            boolean success = nodeGraph.exportToPath(path);
            if (success) {
                lastImportExportPath = path;
                Path fileName = path.getFileName();
                setImportExportStatus("Exported workspace to " + (fileName != null ? fileName.toString() : path.toString()), SUCCESS_COLOR);
            } else {
                setImportExportStatus("Failed to export workspace.", ERROR_COLOR);
            }
        } catch (InvalidPathException ex) {
            setImportExportStatus("Invalid file path.", ERROR_COLOR);
        }
    }

    private void setImportExportStatus(String message, int color) {
        importExportStatus = message != null ? message : "";
        importExportStatusColor = color;
    }

    private void clearImportExportStatus() {
        importExportStatus = "";
        importExportStatusColor = 0xFFCCCCCC;
    }

    private void dismissParameterOverlay() {
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.close();
        }
        parameterOverlay = null;
    }

    private void renderPlayButton(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getPlayButtonX();
        int buttonY = getPlayButtonY();
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();

        int bgColor = executing ? 0xFF243224 : 0xFF2A2A2A;
        if (hovered) {
            bgColor = executing ? 0xFF2F4531 : 0xFF353535;
        } else if (disabled && !executing) {
            bgColor = 0xFF242424;
        }

        int borderColor = executing ? SUCCESS_COLOR : GREY_LINE;
        if (hovered) {
            borderColor = SUCCESS_COLOR;
        } else if (disabled && !executing) {
            borderColor = GREY_LINE;
        }
        context.fill(buttonX + 1, buttonY + 1, buttonX + PLAY_BUTTON_SIZE - 1, buttonY + PLAY_BUTTON_SIZE - 1, bgColor);
        context.drawBorder(buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE, borderColor);

        int iconColor = executing ? SUCCESS_COLOR : 0xFF4CAF50;
        if (hovered) {
            iconColor = 0xFF8BE97A;
        } else if (disabled && !executing) {
            iconColor = 0xFF4A7C4A;
        }
        drawPlayIcon(context, buttonX, buttonY, iconColor);
    }

    private void drawPlayIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int triangleSize = Math.max(5, Math.min(PLAY_BUTTON_SIZE - 12, 7));
        int startX = buttonX + (PLAY_BUTTON_SIZE - triangleSize) / 2;
        int startY = buttonY + (PLAY_BUTTON_SIZE - triangleSize) / 2;

        for (int row = 0; row < triangleSize; row++) {
            int lineY = startY + row;
            int lineStartX = Math.max(startX + row / 2, buttonX + 2);
            int lineEndX = Math.min(startX + triangleSize - 1, buttonX + PLAY_BUTTON_SIZE - 3);
            if (lineStartX <= lineEndX && lineY >= buttonY + 2 && lineY <= buttonY + PLAY_BUTTON_SIZE - 3) {
                context.drawHorizontalLine(lineStartX, lineEndX, lineY, color);
            }
        }
    }

    private void renderStopButton(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = getStopButtonX();
        int buttonY = getStopButtonY();
        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();

        int bgColor = executing ? 0xFF8C1B1B : 0xFF2A2A2A;
        if (hovered) {
            bgColor = executing ? 0xFFA02525 : 0xFF353535;
        } else if (disabled && !executing) {
            bgColor = 0xFF242424;
        }

        int borderColor = executing ? 0xFFFF4C4C : GREY_LINE;
        if (hovered) {
            borderColor = executing ? 0xFFFF6666 : ERROR_COLOR;
        } else if (disabled && !executing) {
            borderColor = GREY_LINE;
        }

        context.fill(buttonX + 1, buttonY + 1, buttonX + STOP_BUTTON_SIZE - 1, buttonY + STOP_BUTTON_SIZE - 1, bgColor);
        context.drawBorder(buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE, borderColor);

        int iconColor = executing ? 0xFFFF6F6F : 0xFFFFA6A6;
        if (hovered) {
            iconColor = executing ? 0xFFFF8A8A : ERROR_COLOR;
        } else if (disabled && !executing) {
            iconColor = 0xFFB35E5E;
        }
        drawStopIcon(context, buttonX, buttonY, iconColor);
    }

    private void drawStopIcon(DrawContext context, int buttonX, int buttonY, int color) {
        int squareSize = Math.max(6, STOP_BUTTON_SIZE - 10);
        int left = buttonX + (STOP_BUTTON_SIZE - squareSize) / 2;
        int top = buttonY + (STOP_BUTTON_SIZE - squareSize) / 2;
        context.fill(left, top, left + squareSize, top + squareSize, color);
    }

    private void renderPresetDropdown(DrawContext context, int mouseX, int mouseY, boolean disabled) {
        int dropdownX = getPresetDropdownX();
        int dropdownY = getPresetDropdownY();

        if (disabled && presetDropdownOpen) {
            presetDropdownOpen = false;
        }

        boolean hovered = !disabled && isPointInRect(mouseX, mouseY, dropdownX, dropdownY, PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT);
        int backgroundColor = (hovered || presetDropdownOpen) ? 0xFF3A3A3A : 0xFF2F2F2F;
        if (disabled && !presetDropdownOpen) {
            backgroundColor = 0xFF2A2A2A;
        }
        context.fill(dropdownX, dropdownY, dropdownX + PRESET_DROPDOWN_WIDTH, dropdownY + PRESET_DROPDOWN_HEIGHT, backgroundColor);
        int borderColor = presetDropdownOpen ? ACCENT_COLOR : GREY_LINE;
        if (disabled && !presetDropdownOpen) {
            borderColor = GREY_LINE;
        }
        context.drawBorder(dropdownX, dropdownY, PRESET_DROPDOWN_WIDTH, PRESET_DROPDOWN_HEIGHT, borderColor);

        String displayName = activePresetName == null || activePresetName.isEmpty()
                ? PresetManager.getDefaultPresetName()
                : activePresetName;
        int activeTextX = dropdownX + PRESET_TEXT_LEFT_PADDING;
        int activeTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
        String trimmedName = this.textRenderer.trimToWidth(displayName, activeTextWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(trimmedName), activeTextX, dropdownY + 5, WHITE);

        int arrowCenterX = dropdownX + PRESET_DROPDOWN_WIDTH - 10;
        int arrowCenterY = dropdownY + PRESET_DROPDOWN_HEIGHT / 2;
        if (presetDropdownOpen) {
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY - 2, WHITE);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY - 1, WHITE);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY, WHITE);
        } else {
            context.drawHorizontalLine(arrowCenterX - 3, arrowCenterX + 3, arrowCenterY + 1, WHITE);
            context.drawHorizontalLine(arrowCenterX - 2, arrowCenterX + 2, arrowCenterY, WHITE);
            context.drawHorizontalLine(arrowCenterX - 1, arrowCenterX + 1, arrowCenterY - 1, WHITE);
        }

        if (!presetDropdownOpen) {
            return;
        }

        int optionStartY = dropdownY + PRESET_DROPDOWN_HEIGHT;
        int optionsHeight = getPresetDropdownOptionsHeight();
        context.fill(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + optionsHeight, DARK_GREY_ALT);

        int optionY = optionStartY;
        for (String preset : availablePresets) {
            boolean optionHovered = isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
            int optionColor = optionHovered ? 0xFF3F3F3F : 0xFF2B2B2B;
            context.fill(dropdownX + 1, optionY + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 1, optionY + PRESET_OPTION_HEIGHT, optionColor);
            int textColor = preset.equals(activePresetName) ? ACCENT_COLOR : WHITE;
            int textX = dropdownX + PRESET_TEXT_LEFT_PADDING;
            int textMaxWidth = PRESET_DROPDOWN_WIDTH
                    - PRESET_TEXT_LEFT_PADDING
                    - PRESET_DELETE_ICON_SIZE
                    - PRESET_DELETE_ICON_MARGIN
                    - PRESET_TEXT_ICON_GAP;
            String presetLabel = this.textRenderer.trimToWidth(preset, textMaxWidth);
            context.drawTextWithShadow(this.textRenderer, Text.literal(presetLabel), textX, optionY + 5, textColor);

            boolean deleteDisabled = isPresetDeleteDisabled(preset);
            int iconLeft = getPresetDeleteIconLeft(dropdownX);
            int iconTop = getPresetDeleteIconTop(optionY);
            boolean iconHovered = !deleteDisabled && isPointInPresetDeleteIcon(mouseX, mouseY, optionY, dropdownX);
            if (iconHovered) {
                context.fill(iconLeft - PRESET_DELETE_ICON_HITBOX_PADDING,
                        iconTop - PRESET_DELETE_ICON_HITBOX_PADDING,
                        iconLeft + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                        iconTop + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                        0x33555555);
            }

            int iconColor;
            if (deleteDisabled) {
                iconColor = 0xFF555555;
            } else if (iconHovered) {
                iconColor = ACCENT_COLOR;
            } else {
                iconColor = 0xFFCCCCCC;
            }
            drawTrashIcon(context, iconLeft, iconTop, iconColor);
            optionY += PRESET_OPTION_HEIGHT;
        }

        context.drawHorizontalLine(dropdownX + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 2, optionY, GREY_LINE);

        boolean createHovered = isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
        int createColor = createHovered ? 0xFF3F3F3F : 0xFF2B2B2B;
        context.fill(dropdownX + 1, optionY + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 1, optionY + PRESET_OPTION_HEIGHT, createColor);
        int createTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
        String createLabel = this.textRenderer.trimToWidth("+ Create new preset", createTextWidth);
        context.drawTextWithShadow(this.textRenderer, Text.literal(createLabel), dropdownX + PRESET_TEXT_LEFT_PADDING, optionY + 5, ACCENT_COLOR);

        context.drawBorder(dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, optionsHeight, GREY_LINE);
    }

    private int getPresetDropdownX() {
        if (shouldShowExecutionControls()) {
            return getStopButtonX() - PRESET_DROPDOWN_MARGIN - PRESET_DROPDOWN_WIDTH;
        }
        return this.width - PRESET_DROPDOWN_MARGIN - PRESET_DROPDOWN_WIDTH;
    }

    private int getPresetDropdownY() {
        return TITLE_BAR_HEIGHT + PRESET_DROPDOWN_MARGIN;
    }

    private int getPlayButtonX() {
        return this.width - PLAY_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    private int getPlayButtonY() {
        return TITLE_BAR_HEIGHT + PLAY_BUTTON_MARGIN;
    }

    private int getZoomPlusButtonX() {
        return this.width - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int getZoomMinusButtonX() {
        return getZoomPlusButtonX() - ZOOM_BUTTON_SIZE - ZOOM_BUTTON_SPACING;
    }

    private int getZoomButtonY() {
        return this.height - ZOOM_BUTTON_MARGIN - ZOOM_BUTTON_SIZE;
    }

    private int getWorkspaceCenterX() {
        int workspaceLeft = Sidebar.getCollapsedWidth();
        return workspaceLeft + (this.width - workspaceLeft) / 2;
    }

    private int getWorkspaceCenterY() {
        int workspaceTop = TITLE_BAR_HEIGHT;
        return workspaceTop + (this.height - workspaceTop) / 2;
    }

    private int getStopButtonX() {
        return getPlayButtonX() - CONTROL_BUTTON_GAP - STOP_BUTTON_SIZE;
    }

    private int getStopButtonY() {
        return getPlayButtonY();
    }

    private int getPresetDropdownOptionsHeight() {
        return (availablePresets.size() + 1) * PRESET_OPTION_HEIGHT;
    }

    private int getPresetDeleteIconLeft(int dropdownX) {
        return dropdownX + PRESET_DROPDOWN_WIDTH - PRESET_DELETE_ICON_MARGIN - PRESET_DELETE_ICON_SIZE;
    }

    private int getPresetDeleteIconTop(int optionTop) {
        return optionTop + (PRESET_OPTION_HEIGHT - PRESET_DELETE_ICON_SIZE) / 2;
    }

    private boolean isPointInPresetDeleteIcon(int mouseX, int mouseY, int optionTop, int dropdownX) {
        int iconLeft = getPresetDeleteIconLeft(dropdownX);
        int iconTop = getPresetDeleteIconTop(optionTop);
        int hitboxSize = PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING * 2;
        return isPointInRect(mouseX, mouseY, iconLeft - PRESET_DELETE_ICON_HITBOX_PADDING, iconTop - PRESET_DELETE_ICON_HITBOX_PADDING, hitboxSize, hitboxSize);
    }

    private boolean isPresetDeleteDisabled(String presetName) {
        if (presetName == null) {
            return true;
        }
        return presetName.equalsIgnoreCase(PresetManager.getDefaultPresetName());
    }

    private boolean handlePresetDropdownSelection(double mouseX, double mouseY) {
        int dropdownX = getPresetDropdownX();
        int optionStartY = getPresetDropdownY() + PRESET_DROPDOWN_HEIGHT;
        int optionsHeight = getPresetDropdownOptionsHeight();
        if (!isPointInRect((int) mouseX, (int) mouseY, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, optionsHeight)) {
            return false;
        }

        int relativeY = (int) mouseY - optionStartY;
        int presetAreaHeight = availablePresets.size() * PRESET_OPTION_HEIGHT;
        if (relativeY < presetAreaHeight) {
            int index = relativeY / PRESET_OPTION_HEIGHT;
            if (index >= 0 && index < availablePresets.size()) {
                String selectedPreset = availablePresets.get(index);
                int optionTop = optionStartY + index * PRESET_OPTION_HEIGHT;
                if (isPointInPresetDeleteIcon((int) mouseX, (int) mouseY, optionTop, dropdownX)) {
                    if (!isPresetDeleteDisabled(selectedPreset)) {
                        attemptDeletePreset(selectedPreset);
                    }
                    return true;
                }
                presetDropdownOpen = false;
                if (!selectedPreset.equals(activePresetName)) {
                    switchPreset(selectedPreset);
                }
                return true;
            }
        } else if (relativeY < presetAreaHeight + PRESET_OPTION_HEIGHT) {
            presetDropdownOpen = false;
            openCreatePresetPopup();
            return true;
        }

        presetDropdownOpen = false;
        return true;
    }

    private void openCreatePresetPopup() {
        presetDropdownOpen = false;
        clearCreatePresetStatus();
        closeInfoPopup();
        createPresetPopupVisible = true;
        if (createPresetField != null) {
            createPresetField.setText("");
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            createPresetField.setFocused(true);
        }
    }

    private void closeCreatePresetPopup() {
        createPresetPopupVisible = false;
        clearCreatePresetStatus();
        if (createPresetField != null) {
            createPresetField.setFocused(false);
            createPresetField.setVisible(false);
            createPresetField.setEditable(false);
        }
    }

    private boolean handleCreatePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int popupX = (this.width - CREATE_PRESET_POPUP_WIDTH) / 2;
        int popupY = (this.height - CREATE_PRESET_POPUP_HEIGHT) / 2;
        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + CREATE_PRESET_POPUP_HEIGHT - buttonHeight - 16;
        int cancelX = popupX + 20;
        int createX = popupX + CREATE_PRESET_POPUP_WIDTH - buttonWidth - 20;

        if (isPointInRect((int) mouseX, (int) mouseY, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeCreatePresetPopup();
            return true;
        }

        if (isPointInRect((int) mouseX, (int) mouseY, createX, buttonY, buttonWidth, buttonHeight)) {
            attemptCreatePreset();
            return true;
        }

        return false;
    }

    private void renderCreatePresetPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, OVERLAY_BACKGROUND);

        int popupWidth = CREATE_PRESET_POPUP_WIDTH;
        int popupHeight = CREATE_PRESET_POPUP_HEIGHT;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, DARK_GREY_ALT);
        context.drawBorder(popupX, popupY, popupWidth, popupHeight, GREY_LINE);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Create workspace preset"),
            popupX + popupWidth / 2,
            popupY + 14,
            WHITE
        );

        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Enter a name for the new preset."),
            popupX + 20,
            popupY + 44,
            0xFFCCCCCC
        );

        int fieldX = popupX + 20;
        int fieldY = popupY + 74;
        int fieldWidth = popupWidth - 40;
        int fieldHeight = 20;

        boolean fieldHovered = isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, 0xFF1F1F1F);
        boolean focused = createPresetField != null && createPresetField.isFocused();
        int borderColor = focused ? ACCENT_COLOR : (fieldHovered ? 0xFF888888 : 0xFF555555);
        context.drawBorder(fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

        if (createPresetField != null) {
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            createPresetField.setPosition(fieldX + 4, fieldY + 2);
            createPresetField.setWidth(fieldWidth - 8);
            createPresetField.render(context, mouseX, mouseY, delta);
        }

        if (!createPresetStatus.isEmpty()) {
            String status = this.textRenderer.trimToWidth(createPresetStatus, fieldWidth);
            context.drawTextWithShadow(this.textRenderer, Text.literal(status), fieldX, fieldY + fieldHeight + 8, createPresetStatusColor);
        }

        int buttonWidth = 90;
        int buttonHeight = 20;
        int buttonY = popupY + popupHeight - buttonHeight - 16;
        int cancelX = popupX + 20;
        int createX = popupX + popupWidth - buttonWidth - 20;

        boolean cancelHovered = isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean createHovered = isPointInRect(mouseX, mouseY, createX, buttonY, buttonWidth, buttonHeight);

        drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered, Text.literal("Cancel"), false);
        drawPopupButton(context, createX, buttonY, buttonWidth, buttonHeight, createHovered, Text.literal("Create"), true);
    }

    private void attemptCreatePreset() {
        if (createPresetField == null) {
            return;
        }

        String desiredName = createPresetField.getText();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setCreatePresetStatus("Enter a preset name.", ERROR_COLOR);
            return;
        }

        Optional<String> createdPreset = PresetManager.createPreset(desiredName);
        if (createdPreset.isEmpty()) {
            setCreatePresetStatus("Preset name already exists or is invalid.", ERROR_COLOR);
            return;
        }

        switchPreset(createdPreset.get());
        closeCreatePresetPopup();
    }

    private void attemptDeletePreset(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }

        if (isPresetDeleteDisabled(presetName)) {
            return;
        }

        boolean deletingActive = presetName.equals(activePresetName);
        String defaultPreset = PresetManager.getDefaultPresetName();
        String fallbackPreset = availablePresets.stream()
                .filter(name -> !name.equalsIgnoreCase(presetName))
                .findFirst()
                .orElse(defaultPreset);

        if (!PresetManager.deletePreset(presetName)) {
            return;
        }

        presetDropdownOpen = false;
        closeCreatePresetPopup();

        if (deletingActive) {
            PresetManager.setActivePreset(fallbackPreset);
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);

        if (deletingActive) {
            dismissParameterOverlay();
            isDraggingFromSidebar = false;
            draggingNodeType = null;
            clearPopupVisible = false;
            clearImportExportStatus();

            if (!nodeGraph.load()) {
                nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
            }
            nodeGraph.resetCamera();
            updateImportExportPathFromPreset();
        }
    }

    private void setCreatePresetStatus(String message, int color) {
        createPresetStatus = message != null ? message : "";
        createPresetStatusColor = color;
    }

    private void clearCreatePresetStatus() {
        createPresetStatus = "";
        createPresetStatusColor = 0xFFCCCCCC;
    }

    private void refreshAvailablePresets() {
        availablePresets = new ArrayList<>(PresetManager.getAvailablePresets());
        activePresetName = PresetManager.getActivePreset();
    }

    private void updateImportExportPathFromPreset() {
        lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
    }

    private void switchPreset(String presetName) {
        nodeGraph.save();
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        if (importExportPopupVisible) {
            closeImportExportPopup();
        }
        if (createPresetPopupVisible) {
            closeCreatePresetPopup();
        }
        clearPopupVisible = false;
        presetDropdownOpen = false;
        clearImportExportStatus();

        if (!nodeGraph.load()) {
            nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }
        nodeGraph.resetCamera();
        updateImportExportPathFromPreset();
    }

    private void renderWorkspaceButtons(DrawContext context, int mouseX, int mouseY) {
        int buttonY = getWorkspaceButtonY();
        boolean importHovered = renderImportExportButton(context, mouseX, mouseY, buttonY);
        boolean clearHovered = renderClearButton(context, mouseX, mouseY, buttonY);
        boolean homeHovered = renderHomeButton(context, mouseX, mouseY, buttonY);

        if (homeHovered) {
            drawWorkspaceTooltip(context, "Reset view", mouseX, mouseY);
        } else if (clearHovered) {
            drawWorkspaceTooltip(context, "Clear workspace", mouseX, mouseY);
        } else if (importHovered) {
            drawWorkspaceTooltip(context, "Import / Export", mouseX, mouseY);
        }
    }

    private boolean renderHomeButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getHomeButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, false);
        int iconColor = hovered ? ACCENT_COLOR : WHITE;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int centerY = buttonY + BOTTOM_BUTTON_SIZE / 2;

        context.drawHorizontalLine(centerX - 4, centerX + 2, centerY, iconColor);
        context.drawVerticalLine(centerX - 4, centerY - 4, centerY + 2, iconColor);
        context.drawHorizontalLine(centerX - 2, centerX, centerY - 2, iconColor);
        context.drawHorizontalLine(centerX - 3, centerX - 1, centerY - 1, iconColor);
        context.drawVerticalLine(centerX - 2, centerY - 2, centerY, iconColor);
        context.drawVerticalLine(centerX - 3, centerY - 3, centerY - 1, iconColor);
        return hovered;
    }

    private boolean renderClearButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getClearButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, clearPopupVisible);
        int iconColor = (hovered || clearPopupVisible) ? ACCENT_COLOR : WHITE;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int top = buttonY + 4;
        int bottom = buttonY + BOTTOM_BUTTON_SIZE - 4;

        context.drawHorizontalLine(centerX - 5, centerX + 4, top, iconColor);
        context.drawVerticalLine(centerX - 5, top, top + 2, iconColor);
        context.drawVerticalLine(centerX + 4, top, top + 2, iconColor);
        context.drawHorizontalLine(centerX - 4, centerX + 3, top + 2, iconColor);
        context.drawVerticalLine(centerX - 3, top + 2, bottom, iconColor);
        context.drawVerticalLine(centerX + 2, top + 2, bottom, iconColor);
        context.drawHorizontalLine(centerX - 3, centerX + 2, bottom, iconColor);
        return hovered;
    }

    private boolean renderImportExportButton(DrawContext context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getImportExportButtonX();
        boolean hovered = renderButtonBackground(context, buttonX, buttonY, mouseX, mouseY, importExportPopupVisible);
        int iconColor = (hovered || importExportPopupVisible) ? ACCENT_COLOR : WHITE;
        int centerX = buttonX + BOTTOM_BUTTON_SIZE / 2;
        int centerY = buttonY + BOTTOM_BUTTON_SIZE / 2;

        // Up arrow
        context.drawVerticalLine(centerX - 4, centerY - 5, centerY, iconColor);
        context.drawHorizontalLine(centerX - 6, centerX - 2, centerY - 5, iconColor);
        context.drawHorizontalLine(centerX - 5, centerX - 3, centerY - 4, iconColor);

        // Down arrow
        context.drawVerticalLine(centerX + 3, centerY, centerY + 5, iconColor);
        context.drawHorizontalLine(centerX + 1, centerX + 5, centerY + 5, iconColor);
        context.drawHorizontalLine(centerX + 2, centerX + 4, centerY + 4, iconColor);

        // Connector line
        context.drawHorizontalLine(centerX - 4, centerX + 3, centerY, iconColor);
        return hovered;
    }

    private void drawWorkspaceTooltip(DrawContext context, String text, int mouseX, int mouseY) {
        int paddingX = 6;
        int paddingY = 4;
        int textWidth = this.textRenderer.getWidth(text);
        int textHeight = this.textRenderer.fontHeight;
        int boxWidth = textWidth + paddingX * 2;
        int boxHeight = textHeight + paddingY * 2;

        int x = mouseX + 12;
        int y = mouseY + 12;

        if (x + boxWidth > this.width) {
            x = this.width - boxWidth - 4;
        }
        if (y + boxHeight > this.height) {
            y = this.height - boxHeight - 4;
        }

        int backgroundColor = 0xF01A1A1A;
        int borderColor = 0x80FFFFFF;
        context.fill(x, y, x + boxWidth, y + boxHeight, backgroundColor);
        context.drawBorder(x, y, boxWidth, boxHeight, borderColor);
        context.drawTextWithShadow(this.textRenderer, Text.literal(text), x + paddingX, y + paddingY, 0xFFE0E0E0);
    }

    private boolean renderButtonBackground(DrawContext context, int buttonX, int buttonY, int mouseX, int mouseY, boolean active) {
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        boolean highlight = hovered || active;

        int bgColor = highlight ? 0xFF505050 : 0xFF3A3A3A;
        context.fill(buttonX + 1, buttonY + 1, buttonX + BOTTOM_BUTTON_SIZE - 1, buttonY + BOTTOM_BUTTON_SIZE - 1, bgColor);

        int borderColor = highlight ? ACCENT_COLOR : 0xFF666666;
        context.drawHorizontalLine(buttonX, buttonX + BOTTOM_BUTTON_SIZE - 1, buttonY, borderColor);
        context.drawHorizontalLine(buttonX, buttonX + BOTTOM_BUTTON_SIZE - 1, buttonY + BOTTOM_BUTTON_SIZE - 1, borderColor);
        context.drawVerticalLine(buttonX, buttonY, buttonY + BOTTOM_BUTTON_SIZE - 1, borderColor);
        context.drawVerticalLine(buttonX + BOTTOM_BUTTON_SIZE - 1, buttonY, buttonY + BOTTOM_BUTTON_SIZE - 1, borderColor);

        return hovered;
    }

    private int getWorkspaceButtonY() {
        return TITLE_BAR_HEIGHT + BOTTOM_BUTTON_MARGIN;
    }

    private int getHomeButtonX() {
        return Sidebar.getCollapsedWidth() + BOTTOM_BUTTON_MARGIN + (BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING) * 2;
    }

    private int getClearButtonX() {
        return Sidebar.getCollapsedWidth() + BOTTOM_BUTTON_MARGIN + BOTTOM_BUTTON_SIZE + BOTTOM_BUTTON_SPACING;
    }

    private int getImportExportButtonX() {
        return Sidebar.getCollapsedWidth() + BOTTOM_BUTTON_MARGIN;
    }

    private boolean isHomeButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getHomeButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isClearButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getClearButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isImportExportButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getImportExportButtonX();
        int buttonY = getWorkspaceButtonY();
        return isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isPointInPlayButton(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getPlayButtonX(), getPlayButtonY(), PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private boolean isPointInStopButton(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getStopButtonX(), getStopButtonY(), STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
    }

    private boolean isPointInZoomMinus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomMinusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private boolean isPointInZoomPlus(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getZoomPlusButtonX(), getZoomButtonY(), ZOOM_BUTTON_SIZE, ZOOM_BUTTON_SIZE);
    }

    private void startExecutingAllGraphs() {
        dismissParameterOverlay();
        isDraggingFromSidebar = false;
        draggingNodeType = null;
        ExecutionManager.getInstance().executeGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void stopExecutingAllGraphs() {
        ExecutionManager.getInstance().requestStopAll();
    }

    private void drawTrashIcon(DrawContext context, int x, int y, int color) {
        int handleWidth = Math.max(2, PRESET_DELETE_ICON_SIZE / 2);
        int handleLeft = x + (PRESET_DELETE_ICON_SIZE - handleWidth) / 2;
        context.fill(handleLeft, y, handleLeft + handleWidth, y + 1, color);

        context.fill(x, y + 1, x + PRESET_DELETE_ICON_SIZE, y + 3, color);
        context.fill(x + 1, y + 3, x + PRESET_DELETE_ICON_SIZE - 1, y + PRESET_DELETE_ICON_SIZE, color);

        int slatColor = (color & 0x00FFFFFF) | 0x66000000;
        context.fill(x + 2, y + 4, x + 3, y + PRESET_DELETE_ICON_SIZE - 1, slatColor);
        context.fill(x + PRESET_DELETE_ICON_SIZE - 3, y + 4, x + PRESET_DELETE_ICON_SIZE - 2, y + PRESET_DELETE_ICON_SIZE - 1, slatColor);
    }

    private boolean isTitleClicked(int mouseX, int mouseY) {
        return isTitleHovered(mouseX, mouseY);
    }

    private boolean isTitleHovered(int mouseX, int mouseY) {
        int textWidth = this.textRenderer.getWidth(TITLE_TEXT);
        int textHeight = this.textRenderer.fontHeight;
        int textX = this.width / 2 - textWidth / 2;
        int textY = (TITLE_BAR_HEIGHT - textHeight) / 2;
        int hitboxX = textX - TITLE_INTERACTION_PADDING;
        int hitboxY = textY - TITLE_INTERACTION_PADDING;
        int hitboxWidth = textWidth + TITLE_INTERACTION_PADDING * 2;
        int hitboxHeight = textHeight + TITLE_INTERACTION_PADDING * 2;
        return isPointInRect(mouseX, mouseY, hitboxX, hitboxY, hitboxWidth, hitboxHeight);
    }

    private String getModVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(PathmindMod.MOD_ID);
        return container.map(value -> value.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
    }

    private String getFabricLoaderVersion() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("fabricloader");
        return container.map(value -> value.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
    }

    private String getCurrentMinecraftVersion() {
        return this.client != null ? this.client.getGameVersion() : "Unknown";
    }

    private boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

}
