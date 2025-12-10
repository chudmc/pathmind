package com.pathmind.ui.control;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Helper widget for selecting inventory/container slots via a visual grid.
 * Renders multiple Minecraft GUI layouts and reports the selected slot/mode.
 */
public class InventorySlotSelector {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 4;
    private static final int GRID_PADDING = 8;
    private static final int MODE_BUTTON_HEIGHT = 20;
    private static final int MODE_BUTTON_TEXT_PADDING = 6;
    private static final int INFO_TEXT_MARGIN = 6;
    private static final int DROPDOWN_OPTION_HEIGHT = 18;
    private static final int SECTION_SPACING = 8;

    public interface Listener {
        void onSlotChanged(int slotId, boolean isPlayerSection);
        void onModeChanged(String modeId);
        void requestLayoutRefresh();
    }

    private final Listener listener;
    private InventoryGuiMode mode;
    private int selectedSlotId = 0;
    private Boolean selectedSlotIsPlayerSection = null;
    private final List<SlotBox> slotBoxes = new ArrayList<>();

    private int renderX;
    private int renderY;
    private int renderWidth;
    private int lastRenderHeight;

    private boolean dropdownOpen = false;
    private int dropdownHoverIndex = -1;
    private int buttonX;
    private int buttonY;
    private int buttonWidth;
    private int buttonHeight;
    private int dropdownScrollIndex = 0;
    private static final int DROPDOWN_MAX_VISIBLE = 8;

    public InventorySlotSelector(Listener listener) {
        this.listener = listener;
        this.mode = InventoryGuiMode.PLAYER_INVENTORY;
    }

    public void setSelectedSlot(int slotId) {
        setSelectedSlot(slotId, null);
    }

    public void setSelectedSlot(int slotId, Boolean isPlayerSection) {
        this.selectedSlotId = slotId;
        this.selectedSlotIsPlayerSection = isPlayerSection;
    }

    public int getSelectedSlot() {
        return selectedSlotId;
    }

    public void setModeById(String id) {
        if (id != null) {
            int separatorIndex = id.indexOf('|');
            if (separatorIndex >= 0) {
                id = id.substring(0, separatorIndex);
            }
        }
        InventoryGuiMode resolved = InventoryGuiMode.fromId(id);
        if (resolved == null) {
            resolved = InventoryGuiMode.PLAYER_INVENTORY;
        }
        if (this.mode != resolved) {
            this.mode = resolved;
            if (listener != null) {
                listener.onModeChanged(this.mode.id);
                listener.requestLayoutRefresh();
            }
        }
    }

    public String getModeId() {
        return mode.id;
    }

    public int render(DrawContext context, TextRenderer textRenderer, int x, int y, int width, int mouseX, int mouseY) {
        this.renderX = x;
        this.renderY = y;
        this.renderWidth = width;

        int sectionY = y;

        // Mode label
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("Interface Mode:"),
            x,
            sectionY,
            0xFFE0E0E0
        );
        sectionY += textRenderer.fontHeight + 4;

        // Mode button
        buttonX = x;
        buttonY = sectionY;
        buttonWidth = width;
        buttonHeight = MODE_BUTTON_HEIGHT;

        boolean hoverButton = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                              mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        int buttonBg = hoverButton ? 0xFF3A3A3A : 0xFF2A2A2A;
        int borderColor = dropdownOpen ? 0xFF87CEEB : (hoverButton ? 0xFFAAAAAA : 0xFF555555);
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonBg);
        context.drawBorder(buttonX, buttonY, buttonWidth, buttonHeight, borderColor);
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(mode.displayName),
            buttonX + MODE_BUTTON_TEXT_PADDING,
            buttonY + 6,
            0xFFFFFFFF
        );
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("▼"),
            buttonX + buttonWidth - 12,
            buttonY + 6,
            0xFFE0E0E0
        );
        sectionY += MODE_BUTTON_HEIGHT + SECTION_SPACING;

        int gridHeight = renderGrid(context, textRenderer, sectionY, width, mouseX, mouseY);
        sectionY += gridHeight + INFO_TEXT_MARGIN;

        String selectionText = "Selected Slot: " + selectedSlotId;
        if (selectedSlotIsPlayerSection != null) {
            selectionText += selectedSlotIsPlayerSection ? " (Inventory)" : " (GUI)";
        }
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(selectionText),
            x,
            sectionY,
            0xFFAAAAAA
        );
        sectionY += textRenderer.fontHeight;

        if (dropdownOpen) {
            renderDropdown(context, textRenderer, mouseX, mouseY);
        }

        lastRenderHeight = sectionY - y;
        return lastRenderHeight;
    }

    public int getEstimatedHeight(int textHeight) {
        InventoryLayout layout = mode.getLayout();
        int header = textHeight + 4 + MODE_BUTTON_HEIGHT + SECTION_SPACING;
        int gridHeight = layout.height + GRID_PADDING * 2;
        int footer = INFO_TEXT_MARGIN + textHeight;
        return header + gridHeight + footer;
    }

    private int renderGrid(DrawContext context, TextRenderer textRenderer, int top, int width, int mouseX, int mouseY) {
        InventoryLayout layout = mode.getLayout();
        int backgroundWidth = Math.max(width, layout.width + GRID_PADDING * 2);
        int backgroundHeight = layout.height + GRID_PADDING * 2;
        int left = renderX;

        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF1A1A1A);
        context.drawBorder(left, top, backgroundWidth, backgroundHeight, 0xFF444444);

        slotBoxes.clear();
        int slotLeft = left + GRID_PADDING;
        int slotTop = top + GRID_PADDING;

        for (SlotPosition position : layout.positions) {
            int slotX = slotLeft + position.offsetX;
            int slotY = slotTop + position.offsetY;

            SlotBox box = new SlotBox(position.slotId, slotX, slotY, position.category);
            slotBoxes.add(box);

            boolean hovered = mouseX >= slotX && mouseX <= slotX + SLOT_SIZE &&
                              mouseY >= slotY && mouseY <= slotY + SLOT_SIZE;
            boolean selected = isSlotSelected(position.slotId, position.category.isPlayerSection());

            int fill = position.category.getColor();
            if (hovered) {
                fill = adjustColor(fill, 1.2f);
            }
            if (selected) {
                fill = 0xFF87CEEB;
            }
            context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, fill);
            context.drawBorder(slotX, slotY, SLOT_SIZE, SLOT_SIZE, selected ? 0xFFFFFFFF : 0xFF555555);

            String label = String.valueOf(position.slotId);
            int textWidth = textRenderer.getWidth(label);
            int textX = slotX + (SLOT_SIZE - textWidth) / 2;
            int textY = slotY + 4;
            context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY, 0xFFFFFFFF);
        }

        return backgroundHeight;
    }

    private boolean isSlotSelected(int slotId, boolean playerSection) {
        if (selectedSlotId != slotId) {
            return false;
        }
        if (selectedSlotIsPlayerSection == null) {
            return true;
        }
        return selectedSlotIsPlayerSection == playerSection;
    }

    private void renderDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        int dropdownX = buttonX;
        int dropdownY = buttonY + buttonHeight;
        int dropdownWidth = buttonWidth;
        int totalOptions = InventoryGuiMode.values().length;
        int visibleCount = Math.min(DROPDOWN_MAX_VISIBLE, totalOptions);
        if (totalOptions <= visibleCount) {
            dropdownScrollIndex = 0;
        } else {
            dropdownScrollIndex = Math.max(0, Math.min(dropdownScrollIndex, totalOptions - visibleCount));
        }
        int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xFF1A1A1A);
        context.drawBorder(dropdownX, dropdownY, dropdownWidth, dropdownHeight, 0xFF444444);
        context.drawHorizontalLine(dropdownX, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xFF444444);

        dropdownHoverIndex = -1;
        InventoryGuiMode[] modes = InventoryGuiMode.values();
        for (int i = 0; i < visibleCount; i++) {
            int optionIndex = dropdownScrollIndex + i;
            InventoryGuiMode option = modes[optionIndex];
            int optionTop = dropdownY + i * DROPDOWN_OPTION_HEIGHT;
            boolean hovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                              mouseY >= optionTop && mouseY <= optionTop + DROPDOWN_OPTION_HEIGHT;
            if (hovered) {
                dropdownHoverIndex = optionIndex;
            }

            int bg = option == mode ? 0xFF2D2D2D : 0xFF1A1A1A;
            if (hovered) {
                bg = adjustColor(bg, 1.2f);
            }
            context.fill(dropdownX + 1, optionTop, dropdownX + dropdownWidth - 1, optionTop + DROPDOWN_OPTION_HEIGHT, bg);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(option.displayName),
                dropdownX + MODE_BUTTON_TEXT_PADDING,
                optionTop + 5,
                0xFFFFFFFF
            );
        }

        if (totalOptions > visibleCount) {
            int indicatorColor = 0xFF666666;
            if (dropdownScrollIndex > 0) {
                context.drawTextWithShadow(textRenderer, Text.literal("▲"), dropdownX + dropdownWidth - 12, dropdownY + 2, indicatorColor);
            }
            if (dropdownScrollIndex + visibleCount < totalOptions) {
                context.drawTextWithShadow(textRenderer, Text.literal("▼"), dropdownX + dropdownWidth - 12, dropdownY + dropdownHeight - 14, indicatorColor);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        boolean buttonPressed = mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        if (buttonPressed) {
            dropdownOpen = !dropdownOpen;
            if (dropdownOpen) {
                dropdownScrollIndex = 0;
            }
            return true;
        }

        if (dropdownOpen) {
            if (handleDropdownClick(mouseX, mouseY)) {
                return true;
            }
            dropdownOpen = false;
        }

        for (SlotBox box : slotBoxes) {
            if (box.contains((int) mouseX, (int) mouseY)) {
                selectedSlotId = box.slotId;
                selectedSlotIsPlayerSection = box.category.isPlayerSection();
                if (listener != null) {
                    listener.onSlotChanged(selectedSlotId, selectedSlotIsPlayerSection);
                }
                return true;
            }
        }

        return false;
    }

    private boolean handleDropdownClick(double mouseX, double mouseY) {
        int dropdownX = buttonX;
        int dropdownY = buttonY + buttonHeight;
        int dropdownWidth = buttonWidth;
        int visibleCount = Math.min(DROPDOWN_MAX_VISIBLE, InventoryGuiMode.values().length);
        int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;

        boolean inside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                         mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        if (!inside) {
            dropdownOpen = false;
            return false;
        }

        int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
        InventoryGuiMode[] modes = InventoryGuiMode.values();
        int actualIndex = dropdownScrollIndex + optionIndex;
        if (actualIndex >= 0 && actualIndex < modes.length) {
            InventoryGuiMode selected = modes[actualIndex];
            if (selected != mode) {
                mode = selected;
                if (listener != null) {
                    listener.onModeChanged(mode.id);
                    listener.requestLayoutRefresh();
                }
            }
        }
        dropdownOpen = false;
        return true;
    }

    public void closeDropdown() {
        dropdownOpen = false;
    }

    public int getLastRenderHeight() {
        return lastRenderHeight;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!dropdownOpen) {
            return false;
        }
        int dropdownX = buttonX;
        int dropdownY = buttonY + buttonHeight;
        int dropdownWidth = buttonWidth;
        int visibleCount = Math.min(DROPDOWN_MAX_VISIBLE, InventoryGuiMode.values().length);
        int dropdownHeight = visibleCount * DROPDOWN_OPTION_HEIGHT;
        boolean inside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                         mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        if (!inside) {
            return false;
        }
        int maxIndex = Math.max(0, InventoryGuiMode.values().length - visibleCount);
        if (maxIndex == 0) {
            return false;
        }
        dropdownScrollIndex -= (int) Math.signum(amount);
        dropdownScrollIndex = Math.max(0, Math.min(maxIndex, dropdownScrollIndex));
        return true;
    }

    private static int adjustColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, (int)(r * factor));
        g = Math.min(255, (int)(g * factor));
        b = Math.min(255, (int)(b * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class SlotBox {
        final int slotId;
        final int x;
        final int y;
        final SlotCategory category;

        SlotBox(int slotId, int x, int y, SlotCategory category) {
            this.slotId = slotId;
            this.x = x;
            this.y = y;
            this.category = category;
        }

        boolean contains(int px, int py) {
            return px >= x && px <= x + SLOT_SIZE && py >= y && py <= y + SLOT_SIZE;
        }
    }

    private enum SlotCategory {
        PLAYER(0xFF3D6F7A, true),
        HOTBAR(0xFF4A5F9D, true),
        ARMOR(0xFFB0804F, true),
        OFFHAND(0xFF8A63C5, true),
        CRAFTING(0xFF4D8F52, false),
        RESULT(0xFFD0862D, false),
        CONTAINER(0xFF3C5E8A, false),
        INPUT(0xFF7A4A32, false),
        OUTPUT(0xFF6A6A6A, false),
        SPECIAL(0xFFD27A48, false);

        private final int color;
        private final boolean playerSection;

        SlotCategory(int color, boolean playerSection) {
            this.color = color;
            this.playerSection = playerSection;
        }

        int getColor() {
            return color;
        }

        boolean isPlayerSection() {
            return playerSection;
        }
    }

    private static final class SlotPosition {
        final int slotId;
        final int offsetX;
        final int offsetY;
        final SlotCategory category;

        SlotPosition(int slotId, int offsetX, int offsetY, SlotCategory category) {
            this.slotId = slotId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.category = category;
        }
    }

    private static final class InventoryLayout {
        final List<SlotPosition> positions;
        final int width;
        final int height;

        InventoryLayout(List<SlotPosition> positions, int width, int height) {
            this.positions = positions;
            this.width = width;
            this.height = height;
        }
    }

    private static class SlotLayoutBuilder {
        private final List<SlotPosition> positions = new ArrayList<>();
        private int width = 0;
        private int height = 0;

        SlotLayoutBuilder addSlot(int slotId, int x, int y, SlotCategory category) {
            positions.add(new SlotPosition(slotId, x, y, category));
            width = Math.max(width, x + SLOT_SIZE);
            height = Math.max(height, y + SLOT_SIZE);
            return this;
        }

        SlotLayoutBuilder addRow(int startSlot, int slots, int startX, int startY, SlotCategory category) {
            int slot = startSlot;
            for (int i = 0; i < slots; i++) {
                addSlot(slot++, startX + i * (SLOT_SIZE + SLOT_SPACING), startY, category);
            }
            return this;
        }

        SlotLayoutBuilder addGrid(int startSlot, int columns, int rows, int startX, int startY, SlotCategory category) {
            int slot = startSlot;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    addSlot(slot++, startX + col * (SLOT_SIZE + SLOT_SPACING), startY + row * (SLOT_SIZE + SLOT_SPACING), category);
                }
            }
            return this;
        }

        InventoryLayout build() {
            return new InventoryLayout(new ArrayList<>(positions), width, height);
        }
    }

    private static void addPlayerInventoryGrid(SlotLayoutBuilder builder, int startX, int startY) {
        builder.addGrid(9, 9, 3, startX, startY, SlotCategory.PLAYER);
    }

    private static void addPlayerHotbarRow(SlotLayoutBuilder builder, int startX, int startY) {
        builder.addRow(0, 9, startX, startY, SlotCategory.HOTBAR);
    }

    private enum InventoryGuiMode {
        PLAYER_INVENTORY("player_inventory", "Player Inventory", InventorySlotSelector::buildPlayerLayout),
        CRAFTING_TABLE("crafting_table", "Crafting Table", InventorySlotSelector::buildCraftingTableLayout),
        FURNACE("furnace", "Furnace", InventorySlotSelector::buildFurnaceLayout),
        BLAST_FURNACE("blast_furnace", "Blast Furnace", InventorySlotSelector::buildFurnaceLayout),
        SMOKER("smoker", "Smoker", InventorySlotSelector::buildFurnaceLayout),
        ENCHANTING_TABLE("enchanting_table", "Enchanting Table", InventorySlotSelector::buildEnchantingLayout),
        BREWING_STAND("brewing_stand", "Brewing Stand", InventorySlotSelector::buildBrewingLayout),
        ANVIL("anvil", "Anvil", InventorySlotSelector::buildAnvilLayout),
        GRINDSTONE("grindstone", "Grindstone", InventorySlotSelector::buildGrindstoneLayout),
        STONECUTTER("stonecutter", "Stonecutter", InventorySlotSelector::buildStonecutterLayout),
        SMITHING_TABLE("smithing_table", "Smithing Table", InventorySlotSelector::buildSmithingLayout),
        LOOM("loom", "Loom", InventorySlotSelector::buildLoomLayout),
        CARTOGRAPHY_TABLE("cartography_table", "Cartography Table", InventorySlotSelector::buildCartographyLayout),
        BARREL("barrel", "Barrel / Single Chest", InventorySlotSelector::buildSingleChestLayout),
        CHEST_DOUBLE("double_chest", "Double Chest", InventorySlotSelector::buildDoubleChestLayout),
        SHULKER_BOX("shulker_box", "Shulker Box", InventorySlotSelector::buildSingleChestLayout),
        HOPPER("hopper", "Hopper", InventorySlotSelector::buildHopperLayout),
        DISPENSER("dispenser", "Dispenser / Dropper", InventorySlotSelector::buildDispenserLayout),
        BEACON("beacon", "Beacon", InventorySlotSelector::buildBeaconLayout);

        final String id;
        final String displayName;
        private final Supplier<InventoryLayout> layoutSupplier;
        private InventoryLayout cachedLayout;

        InventoryGuiMode(String id, String displayName, Supplier<InventoryLayout> layoutSupplier) {
            this.id = id;
            this.displayName = displayName;
            this.layoutSupplier = layoutSupplier;
        }

        InventoryLayout getLayout() {
            if (cachedLayout == null) {
                cachedLayout = layoutSupplier.get();
            }
            return cachedLayout;
        }

        static InventoryGuiMode fromId(String id) {
            if (id == null || id.isEmpty()) {
                return PLAYER_INVENTORY;
            }
            String normalized = id.toLowerCase(Locale.ROOT);
            for (InventoryGuiMode mode : values()) {
                if (mode.id.equals(normalized)) {
                    return mode;
                }
            }
            return PLAYER_INVENTORY;
        }
    }

    private static InventoryLayout buildPlayerLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;

        int armorX = 0;
        builder.addSlot(39, armorX, 0, SlotCategory.ARMOR); // Helmet
        builder.addSlot(38, armorX, columnSpacing, SlotCategory.ARMOR); // Chest
        builder.addSlot(37, armorX, columnSpacing * 2, SlotCategory.ARMOR); // Legs
        builder.addSlot(36, armorX, columnSpacing * 3, SlotCategory.ARMOR); // Boots

        int craftStartX = armorX + columnSpacing * 2;
        builder.addGrid(1, 2, 2, craftStartX, 0, SlotCategory.CRAFTING);
        builder.addSlot(0, craftStartX + columnSpacing * 2 + SLOT_SPACING, columnSpacing / 2, SlotCategory.RESULT);

        int armorBottom = columnSpacing * 3 + SLOT_SIZE;
        int craftingBottom = columnSpacing + SLOT_SIZE;
        int topOffset = Math.max(armorBottom, craftingBottom);
        int mainStartY = topOffset + SLOT_SPACING * 4;
        builder.addGrid(9, 9, 3, 0, mainStartY, SlotCategory.PLAYER);

        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        builder.addRow(0, 9, 0, hotbarY, SlotCategory.HOTBAR);

        int offhandX = armorX + columnSpacing + SLOT_SPACING * 2;
        int offhandY = columnSpacing * 3;
        builder.addSlot(40, offhandX, offhandY, SlotCategory.OFFHAND);

        return builder.build();
    }

    private static InventoryLayout buildCraftingTableLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;

        builder.addGrid(1, 3, 3, 0, 0, SlotCategory.CRAFTING);
        builder.addSlot(0, columnSpacing * 3 + SLOT_SPACING, columnSpacing, SlotCategory.RESULT);

        int mainStartY = columnSpacing * 3 + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildFurnaceLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;

        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, 0, columnSpacing, SlotCategory.INPUT);
        builder.addSlot(2, columnSpacing * 2, columnSpacing / 2, SlotCategory.OUTPUT);

        int mainStartY = columnSpacing * 2 + 10;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildEnchantingLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, SLOT_SIZE + SLOT_SPACING, 0, SlotCategory.INPUT);

        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildBrewingLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;

        builder.addSlot(3, columnSpacing, 0, SlotCategory.INPUT); // ingredient
        builder.addSlot(4, 0, columnSpacing, SlotCategory.INPUT); // fuel
        builder.addRow(0, 3, columnSpacing, columnSpacing * 2, SlotCategory.CONTAINER); // bottles

        int mainStartY = columnSpacing * 3 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildAnvilLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;

        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, columnSpacing, 0, SlotCategory.INPUT);
        builder.addSlot(2, columnSpacing * 3, 0, SlotCategory.RESULT);

        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildGrindstoneLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;
        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, columnSpacing, 0, SlotCategory.INPUT);
        builder.addSlot(2, columnSpacing * 3, 0, SlotCategory.RESULT);

        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildStonecutterLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, (SLOT_SIZE + SLOT_SPACING) * 2, 0, SlotCategory.RESULT);

        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildSmithingLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;
        builder.addSlot(0, 0, 0, SlotCategory.INPUT); // template
        builder.addSlot(1, 0, columnSpacing, SlotCategory.INPUT); // base
        builder.addSlot(2, 0, columnSpacing * 2, SlotCategory.INPUT); // addition
        builder.addSlot(3, columnSpacing * 3, columnSpacing, SlotCategory.RESULT);

        int mainStartY = columnSpacing * 3 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildLoomLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;
        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, columnSpacing, 0, SlotCategory.INPUT);
        builder.addSlot(2, 0, columnSpacing, SlotCategory.INPUT);
        builder.addSlot(3, columnSpacing * 3, columnSpacing / 2, SlotCategory.RESULT);

        int mainStartY = columnSpacing * 2 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildCartographyLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        int columnSpacing = SLOT_SIZE + SLOT_SPACING;
        builder.addSlot(0, 0, 0, SlotCategory.INPUT);
        builder.addSlot(1, columnSpacing, 0, SlotCategory.INPUT);
        builder.addSlot(2, columnSpacing * 3, 0, SlotCategory.RESULT);

        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + columnSpacing * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildSingleChestLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addGrid(0, 9, 3, 0, 0, SlotCategory.CONTAINER);

        int mainStartY = (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildDoubleChestLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addGrid(0, 9, 6, 0, 0, SlotCategory.CONTAINER);

        int mainStartY = (SLOT_SIZE + SLOT_SPACING) * 6 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildHopperLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addRow(0, 5, 0, 0, SlotCategory.CONTAINER);
        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildDispenserLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addGrid(0, 3, 3, 0, 0, SlotCategory.CONTAINER);
        int mainStartY = (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }

    private static InventoryLayout buildBeaconLayout() {
        SlotLayoutBuilder builder = new SlotLayoutBuilder();
        builder.addSlot(0, 0, 0, SlotCategory.SPECIAL);
        int mainStartY = SLOT_SIZE + SLOT_SPACING * 2;
        addPlayerInventoryGrid(builder, 0, mainStartY);
        int hotbarY = mainStartY + (SLOT_SIZE + SLOT_SPACING) * 3 + SLOT_SPACING;
        addPlayerHotbarRow(builder, 0, hotbarY);
        return builder.build();
    }
}
