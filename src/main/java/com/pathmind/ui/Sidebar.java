package com.pathmind.ui;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the sidebar with categorized draggable nodes.
 * Features a nested sidebar design with colored tabs like sticky notes.
 */
public class Sidebar {
    // Outer sidebar dimensions
    private static final int OUTER_SIDEBAR_WIDTH = 180;
    private static final int INNER_SIDEBAR_WIDTH = 40;
    private static final int TAB_SIZE = 24;
    private static final int TAB_SPACING = 8;
    private static final int TOP_PADDING = 8;
    private static final int TAB_COLUMNS = 2;
    private static final int TAB_COLUMN_MARGIN = 8;
    private static final int TAB_COLUMN_SPACING = 8;
    
    // Node display dimensions
    private static final int NODE_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final int CATEGORY_HEADER_HEIGHT = 20;
    private static final int GROUP_HEADER_HEIGHT = 16;
    
    // Colors
    private static final int DARK_GREY_ALT = 0xFF2A2A2A;
    private static final int DARKER_GREY = 0xFF1F1F1F;
    private static final int WHITE_MUTED = 0xFFE0E0E0;
    private static final int GREY_LINE = 0xFF666666;
    private static final int HOVER_COLOR = 0xFF404040;
    
    private final Map<NodeCategory, List<NodeType>> categoryNodes;
    private final Map<NodeCategory, List<NodeGroup>> groupedCategoryNodes;
    private final Map<NodeCategory, Boolean> categoryExpanded;
    private NodeType hoveredNodeType = null;
    private NodeCategory hoveredCategory = null;
    private NodeCategory selectedCategory = null;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int currentSidebarHeight = 400; // Store current sidebar height
    private int currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
    
    public Sidebar() {
        this.categoryExpanded = new HashMap<>();
        this.categoryNodes = new HashMap<>();
        this.groupedCategoryNodes = new HashMap<>();
        
        // Initialize categories as expanded by default
        for (NodeCategory category : NodeCategory.values()) {
            categoryExpanded.put(category, true);
        }

        // Organize nodes by category
        initializeCategoryNodes();
        calculateMaxScroll(400); // Default height for initialization
    }
    
    private void initializeCategoryNodes() {
        for (NodeCategory category : NodeCategory.values()) {
            List<NodeType> nodes = new ArrayList<>();

            if (category == NodeCategory.PARAMETERS) {
                List<NodeGroup> parameterGroups = createParameterGroups();
                groupedCategoryNodes.put(category, parameterGroups);
                for (NodeGroup group : parameterGroups) {
                    nodes.addAll(group.getNodes());
                }
            } else if (category == NodeCategory.SENSORS) {
                List<NodeGroup> sensorGroups = createSensorGroups();
                groupedCategoryNodes.put(category, sensorGroups);
                for (NodeGroup group : sensorGroups) {
                    nodes.addAll(group.getNodes());
                }
            } else {
                for (NodeType nodeType : NodeType.values()) {
                    if (nodeType == NodeType.PARAM_PLACE_TARGET) {
                        continue;
                    }
                    if (nodeType.getCategory() == category && nodeType.isDraggableFromSidebar()) {
                        nodes.add(nodeType);
                    }
                }
            }

            categoryNodes.put(category, nodes);
        }
    }

    private List<NodeGroup> createParameterGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(new NodeGroup(
            "Spatial Data",
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_CLOSEST
        ));
        groups.add(new NodeGroup(
            "Targets & Objects",
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_BLOCK_LIST,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC
        ));
        groups.add(new NodeGroup(
            "Inventory & Equipment",
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_HAND
        ));
        groups.add(new NodeGroup(
            "Utility Data",
            NodeType.PARAM_DURATION,
            NodeType.PARAM_BOOLEAN,
            NodeType.PARAM_MESSAGE
        ));
        return groups;
    }

    private List<NodeGroup> createSensorGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(new NodeGroup(
            "Player State",
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_FALLING
        ));
        groups.add(new NodeGroup(
            "Position & Blocks",
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_TOUCHING_BLOCK,
            NodeType.SENSOR_BLOCK_AHEAD,
            NodeType.SENSOR_BLOCK_BELOW
        ));
        groups.add(new NodeGroup(
            "Entities & Visibility",
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_ENTITY_NEARBY,
            NodeType.SENSOR_IS_RENDERED
        ));
        groups.add(new NodeGroup(
            "Inventory & Items",
            NodeType.SENSOR_ITEM_IN_INVENTORY
        ));
        groups.add(new NodeGroup(
            "Player Stats",
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW
        ));
        groups.add(new NodeGroup(
            "Environment & Weather",
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING,
            NodeType.SENSOR_LIGHT_LEVEL_BELOW
        ));
        return groups;
    }

    private boolean hasGroupedContent(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        if (groups == null) {
            return false;
        }
        for (NodeGroup group : groups) {
            if (!group.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<NodeGroup> getGroupsForCategory(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        return groups != null ? groups : java.util.Collections.emptyList();
    }
    
    private void calculateMaxScroll(int sidebarHeight) {
        int totalHeight = 0;
        
        // Add space for category header and nodes (content starts at top)
        if (selectedCategory != null) {
            totalHeight += CATEGORY_HEADER_HEIGHT;
            
            if (hasGroupedContent(selectedCategory)) {
                for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                    if (group.isEmpty()) {
                        continue;
                    }
                    totalHeight += GROUP_HEADER_HEIGHT;
                    totalHeight += group.getNodes().size() * NODE_HEIGHT;
                }
            } else {
                // Add space for nodes in selected category
                List<NodeType> nodes = categoryNodes.get(selectedCategory);
                if (nodes != null) {
                    totalHeight += nodes.size() * NODE_HEIGHT;
                }
            }
        }
        
        // Add padding
        totalHeight += PADDING * 2;
        
        // Calculate max scroll with proper room for scrolling
        maxScroll = Math.max(0, totalHeight - sidebarHeight + 100); // Extra 100px for better scrolling
    }
    
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int sidebarStartY, int sidebarHeight) {
        // Store current sidebar height and update max scroll
        this.currentSidebarHeight = sidebarHeight;
        calculateMaxScroll(sidebarHeight);
        
        NodeCategory[] categories = NodeCategory.values();
        int totalVisibleTabs = 0;
        for (NodeCategory category : categories) {
            if (!categoryNodes.get(category).isEmpty()) {
                totalVisibleTabs++;
            }
        }

        int availableTabHeight = Math.max(TAB_SIZE, sidebarHeight - TOP_PADDING * 2);
        int rowsFromHeight = Math.max(1, (availableTabHeight + TAB_SPACING) / (TAB_SIZE + TAB_SPACING));
        int minRowsNeeded = totalVisibleTabs > 0 ? (int) Math.ceil(totalVisibleTabs / (double) TAB_COLUMNS) : 1;
        int rowsPerColumn = Math.max(rowsFromHeight, minRowsNeeded);
        int columnsUsed = totalVisibleTabs > 0 ? (int) Math.ceil(totalVisibleTabs / (double) rowsPerColumn) : 1;
        columnsUsed = Math.min(columnsUsed, TAB_COLUMNS);

        if (columnsUsed > 1) {
            currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH * 2 - 6;
        } else {
            currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
        }

        int totalWidth = getWidth();
        
        // Outer sidebar background
        int outerColor = totalWidth > currentInnerSidebarWidth ? DARK_GREY_ALT : DARKER_GREY;
        context.fill(0, sidebarStartY, totalWidth, sidebarStartY + sidebarHeight, outerColor);
        context.drawVerticalLine(totalWidth, sidebarStartY, sidebarStartY + sidebarHeight, GREY_LINE);
        
        // Inner sidebar background (for tabs)
        context.fill(0, sidebarStartY, currentInnerSidebarWidth, sidebarStartY + sidebarHeight, DARKER_GREY);
        context.drawVerticalLine(currentInnerSidebarWidth, sidebarStartY, sidebarStartY + sidebarHeight, GREY_LINE);

        // Tabs stay static (don't scroll with content)
        int currentY = sidebarStartY + TOP_PADDING;

        // Render colored tabs
        hoveredCategory = null;
        // rowsPerColumn already calculated above

        int visibleTabIndex = 0; // Track visible tabs separately from array index
        for (int i = 0; i < categories.length; i++) {
            NodeCategory category = categories[i];

            // Skip if category has no nodes
            if (categoryNodes.get(category).isEmpty()) {
                continue;
            }

            int column = visibleTabIndex / rowsPerColumn;
            int row = visibleTabIndex % rowsPerColumn;
            int tabY = currentY + row * (TAB_SIZE + TAB_SPACING);
            int tabX = TAB_COLUMN_MARGIN + column * (TAB_SIZE + TAB_COLUMN_SPACING);
            visibleTabIndex++; // Increment only for visible tabs
            
            // Check if tab is hovered
            boolean tabHovered = mouseX >= tabX && mouseX <= tabX + TAB_SIZE && 
                               mouseY >= tabY && mouseY < tabY + TAB_SIZE;
            
            // Check if tab is selected
            boolean tabSelected = category == selectedCategory;
            
            // Tab background color
            int baseColor = category.getColor();
            int tabColor = baseColor;
            if (tabSelected) {
                // Darken the category color for selected state
                tabColor = darkenColor(baseColor, 0.75f);
            } else if (tabHovered) {
                tabColor = lightenColor(baseColor, 1.2f);
            }
            
            // Render square tab
            context.fill(tabX, tabY, tabX + TAB_SIZE, tabY + TAB_SIZE, tabColor);
            
            // Tab outline slightly darker than base color
            int outlineColor = darkenColor(baseColor, 0.8f);
            context.drawBorder(tabX, tabY, TAB_SIZE, TAB_SIZE, outlineColor);
            
            // Render centered icon with bigger appearance
            String icon = category.getIcon();
            int iconX = tabX + (TAB_SIZE - textRenderer.getWidth(icon)) / 2;
            int iconY = tabY + (TAB_SIZE - textRenderer.fontHeight) / 2 + 1;
            
            context.drawTextWithShadow(textRenderer, icon, iconX, iconY, 0xFFFFFFFF);
            
            // Update hover state
            if (tabHovered) {
                hoveredCategory = category;
            }
        }
        
        // Render category name and nodes for selected category
        if (selectedCategory != null) {
            // Start content area at the very top of the sidebar, right after the title bar
            int contentY = sidebarStartY + PADDING - scrollOffset;
            int sidebarBottom = sidebarStartY + sidebarHeight;
            
            // Category header
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(selectedCategory.getDisplayName()),
                currentInnerSidebarWidth + 8,
                contentY + 4,
                selectedCategory.getColor()
            );
            
            contentY += CATEGORY_HEADER_HEIGHT;
            
            hoveredNodeType = null;

            if (hasGroupedContent(selectedCategory)) {
                outer:
                for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                    if (group.isEmpty()) {
                        continue;
                    }

                    if (contentY >= sidebarBottom) {
                        break;
                    }

                    context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(group.getTitle()),
                        currentInnerSidebarWidth + 8,
                        contentY + 2,
                        GREY_LINE
                    );

                    contentY += GROUP_HEADER_HEIGHT;

                    for (NodeType nodeType : group.getNodes()) {
                        if (contentY >= sidebarBottom) {
                            break outer;
                        }

                        boolean nodeHovered = mouseX >= currentInnerSidebarWidth && mouseX <= totalWidth &&
                                            mouseY >= contentY && mouseY < contentY + NODE_HEIGHT;

                        if (nodeHovered) {
                            hoveredNodeType = nodeType;
                            context.fill(currentInnerSidebarWidth, contentY, totalWidth, contentY + NODE_HEIGHT, HOVER_COLOR);
                        }

                        int indicatorSize = 12;
                        int indicatorX = currentInnerSidebarWidth + 8;
                        int indicatorY = contentY + 3;
                        context.fill(indicatorX, indicatorY, indicatorX + indicatorSize, indicatorY + indicatorSize, nodeType.getColor());
                        context.drawBorder(indicatorX, indicatorY, indicatorSize, indicatorSize, 0xFF000000);

                        context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(nodeType.getDisplayName()),
                            indicatorX + indicatorSize + 4,
                            contentY + 4,
                            WHITE_MUTED
                        );

                        contentY += NODE_HEIGHT;
                    }
                }
            } else {
                // Render nodes in selected category
                List<NodeType> nodes = categoryNodes.get(selectedCategory);
                if (nodes != null) {
                    for (NodeType nodeType : nodes) {
                        if (contentY >= sidebarBottom) break; // Don't render beyond sidebar
                        
                        boolean nodeHovered = mouseX >= currentInnerSidebarWidth && mouseX <= totalWidth &&
                                            mouseY >= contentY && mouseY < contentY + NODE_HEIGHT;

                        if (nodeHovered) {
                            hoveredNodeType = nodeType;
                            context.fill(currentInnerSidebarWidth, contentY, totalWidth, contentY + NODE_HEIGHT, HOVER_COLOR);
                        }

                        int indicatorSize = 12;
                        int indicatorX = currentInnerSidebarWidth + 8; // Align with category title
                        int indicatorY = contentY + 3;
                        context.fill(indicatorX, indicatorY, indicatorX + indicatorSize, indicatorY + indicatorSize, nodeType.getColor());
                        context.drawBorder(indicatorX, indicatorY, indicatorSize, indicatorSize, 0xFF000000);
                        
                        context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(nodeType.getDisplayName()),
                            indicatorX + indicatorSize + 4, // Position after the indicator with some spacing
                            contentY + 4,
                            WHITE_MUTED
                        );
                        
                        contentY += NODE_HEIGHT;
                    }
                }
            }
        }
        
        // Reset hover states if mouse is not in sidebar
        if (mouseX < 0 || mouseX > getWidth()) {
            hoveredNodeType = null;
            hoveredCategory = null;
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < 0 || mouseX > getWidth()) {
            return false;
        }
        
        if (button == 0) { // Left click
            // Check tab clicks
            if (mouseX >= 0 && mouseX <= currentInnerSidebarWidth && hoveredCategory != null) {
                if (selectedCategory != null && hoveredCategory == selectedCategory) {
                    selectedCategory = null;
                } else {
                    selectedCategory = hoveredCategory;
                }
                // Clear any hovered node when switching or collapsing categories
                hoveredNodeType = null;
                // Reset scroll to top when changing categories
                scrollOffset = 0;
                calculateMaxScroll(currentSidebarHeight);
                return true;
            }
            
            // Check node clicks for dragging
            if (hoveredNodeType != null) {
                return true; // Signal that dragging should start
            }
        }
        
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= 0 && mouseX <= getWidth()) {
            scrollOffset += (int)(-amount * 20); // Scroll speed
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }
    
    public NodeType getHoveredNodeType() {
        return hoveredNodeType;
    }
    
    public boolean isHoveringNode() {
        return hoveredNodeType != null;
    }
    
    public Node createNodeFromSidebar(int x, int y) {
        if (hoveredNodeType != null) {
            return new Node(hoveredNodeType, x, y);
        }
        return null;
    }
    
    public int getWidth() {
        return selectedCategory != null ? OUTER_SIDEBAR_WIDTH : currentInnerSidebarWidth;
    }

    /**
     * Returns the width of the sidebar when no category is expanded.
     * This is used for layout calculations that should remain stable even
     * when a category is opened (which visually overlays the workspace).
     */
    public static int getCollapsedWidth() {
        return INNER_SIDEBAR_WIDTH;
    }
    
    /**
     * Darkens a color by the specified factor
     */
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Lightens a color by the specified factor
     */
    private int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static class NodeGroup {
        private final String title;
        private final List<NodeType> nodes;

        NodeGroup(String title, NodeType... nodeTypes) {
            this.title = title;
            this.nodes = new ArrayList<>();
            if (nodeTypes != null) {
                for (NodeType type : nodeTypes) {
                    if (type != null && type.isDraggableFromSidebar()) {
                        this.nodes.add(type);
                    }
                }
            }
        }

        public String getTitle() {
            return title;
        }

        public List<NodeType> getNodes() {
            return nodes;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }
}
