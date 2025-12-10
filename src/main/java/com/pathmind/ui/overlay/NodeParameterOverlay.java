package com.pathmind.ui.overlay;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.control.InventorySlotSelector;
import com.pathmind.util.InventorySlotModeHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Overlay widget for editing node parameters.
 * Appears on top of the existing GUI without replacing it.
 */
public class NodeParameterOverlay {
    private static final int CONTENT_START_OFFSET = 32;
    private static final int LABEL_TO_FIELD_OFFSET = 18;
    private static final int FIELD_HEIGHT = 20;
    private static final int SECTION_SPACING = 12;
    private static final int BUTTON_TOP_MARGIN = 8;
    private static final int BOTTOM_PADDING = 12;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_POPUP_HEIGHT = 140;
    private static final int DROPDOWN_OPTION_HEIGHT = 20;
    private static final int MIN_POPUP_WIDTH = 300;
    private static final int APPROX_CHAR_WIDTH = 6;
    private static final int POPUP_VERTICAL_MARGIN = 40;
    private static final int SCROLL_STEP = 18;
    private static final int SCROLLBAR_WIDTH = 6;

    private final Node node;
    private final List<String> parameterValues;
    private final List<Boolean> fieldFocused;
    private int popupWidth = MIN_POPUP_WIDTH;
    private final int screenWidth;
    private final int screenHeight;
    private final int topBarHeight;
    private int popupHeight;
    private int popupX;
    private int popupY;
    private int totalContentHeight;
    private int maxScroll;
    private int scrollOffset;
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    private final Runnable onClose;
    private final Consumer<Node> onSave;
    private final Supplier<List<String>> functionNameSupplier;
    private boolean visible = false;
    private int focusedFieldIndex = -1;
    
    // Mode selection fields
    private NodeMode selectedMode;
    private final List<NodeMode> availableModes;
    private boolean modeDropdownOpen = false;
    private int modeDropdownHoverIndex = -1;
    
    // Function selection dropdown (Call Function node)
    private final List<String> functionNameOptions;
    private int functionDropdownParamIndex = -1;
    private boolean functionDropdownOpen = false;
    private int functionDropdownHoverIndex = -1;
    private int functionDropdownFieldX = 0;
    private int functionDropdownFieldY = 0;
    private int functionDropdownFieldWidth = 0;
    private int functionDropdownFieldHeight = 0;
    private boolean functionDropdownEnabled = false;
    private final boolean inventorySlotEditorActive;
    private final int inventorySlotParamIndex;
    private final int inventoryModeParamIndex;
    private final InventorySlotSelector inventorySlotSelector;
    private Boolean inventorySlotSelectionIsPlayer = null;
    private boolean suppressInventorySelectorCallbacks = false;
    private int textLineHeight = 9;

    public NodeParameterOverlay(Node node, int screenWidth, int screenHeight, int topBarHeight, Runnable onClose,
                                Consumer<Node> onSave,
                                Supplier<List<String>> functionNameSupplier) {
        this.node = node;
        this.onClose = onClose;
        this.onSave = onSave;
        this.parameterValues = new ArrayList<>();
        this.fieldFocused = new ArrayList<>();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.topBarHeight = topBarHeight;
        this.functionNameSupplier = functionNameSupplier;
        
        // Initialize mode selection
        this.availableModes = new ArrayList<>();
        this.functionNameOptions = new ArrayList<>();
        NodeMode[] modes = NodeMode.getModesForNodeType(node.getType());
        if (modes != null) {
            for (NodeMode mode : modes) {
                this.availableModes.add(mode);
            }
        }
        this.selectedMode = node.getMode();
        this.functionDropdownParamIndex = findFunctionDropdownIndex(node);

        int slotIndex = -1;
        int modeIndex = -1;
        if (node.getType() == NodeType.PARAM_INVENTORY_SLOT) {
            List<NodeParameter> params = node.getParameters();
            for (int i = 0; i < params.size(); i++) {
                NodeParameter param = params.get(i);
                if (param == null) {
                    continue;
                }
                String name = param.getName();
                if ("Slot".equalsIgnoreCase(name)) {
                    slotIndex = i;
                } else if ("Mode".equalsIgnoreCase(name)) {
                    modeIndex = i;
                }
            }
        }
        this.inventorySlotParamIndex = slotIndex;
        this.inventoryModeParamIndex = modeIndex;
        this.inventorySlotEditorActive = slotIndex >= 0;
        if (inventorySlotEditorActive) {
            this.inventorySlotSelector = new InventorySlotSelector(new InventorySlotSelector.Listener() {
                @Override
                public void onSlotChanged(int slotId, boolean isPlayerSection) {
                    if (suppressInventorySelectorCallbacks) {
                        inventorySlotSelectionIsPlayer = isPlayerSection;
                        return;
                    }
                    inventorySlotSelectionIsPlayer = isPlayerSection;
                    if (inventorySlotParamIndex >= 0 && inventorySlotParamIndex < parameterValues.size()) {
                        parameterValues.set(inventorySlotParamIndex, String.valueOf(slotId));
                    }
                    persistInventorySlotModeValue();
                }

                @Override
                public void onModeChanged(String modeId) {
                    if (suppressInventorySelectorCallbacks) {
                        return;
                    }
                    inventorySlotSelectionIsPlayer = null;
                    if (inventoryModeParamIndex >= 0 && inventoryModeParamIndex < parameterValues.size()) {
                        parameterValues.set(inventoryModeParamIndex, InventorySlotModeHelper.buildStoredModeValue(modeId, null));
                    }
                }

                @Override
                public void requestLayoutRefresh() {
                    updatePopupDimensions();
                    recreateButtons();
                }
            });
        } else {
            this.inventorySlotSelector = null;
        }
        
        updatePopupDimensions();
    }

    public void init() {
        resetParameterFields();
        refreshFunctionNameOptions();
        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            Boolean storedSelection = null;
            if (inventoryModeParamIndex >= 0 && inventoryModeParamIndex < parameterValues.size()) {
                String storedModeValue = parameterValues.get(inventoryModeParamIndex);
                storedSelection = InventorySlotModeHelper.extractPlayerSelectionFlag(storedModeValue);
                String storedModeId = InventorySlotModeHelper.extractModeId(storedModeValue);
                suppressInventorySelectorCallbacks = true;
                inventorySlotSelector.setModeById(storedModeId);
                suppressInventorySelectorCallbacks = false;
            }
            if (inventorySlotParamIndex >= 0 && inventorySlotParamIndex < parameterValues.size()) {
                try {
                    int storedSlot = Integer.parseInt(parameterValues.get(inventorySlotParamIndex));
                    suppressInventorySelectorCallbacks = true;
                    inventorySlotSelector.setSelectedSlot(storedSlot, storedSelection);
                    suppressInventorySelectorCallbacks = false;
                    inventorySlotSelectionIsPlayer = storedSelection;
                } catch (NumberFormatException e) {
                    inventorySlotSelectionIsPlayer = null;
                    suppressInventorySelectorCallbacks = true;
                    inventorySlotSelector.setSelectedSlot(0, null);
                    suppressInventorySelectorCallbacks = false;
                }
            } else {
                inventorySlotSelectionIsPlayer = storedSelection;
            }
            persistInventorySlotModeValue();
        }
        updatePopupDimensions();
        recreateButtons();
        scrollOffset = Math.min(scrollOffset, maxScroll);
        updateButtonPositions();
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        // Render semi-transparent background overlay
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0x80000000);

        // Render popup background
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF2A2A2A);
        context.drawBorder(popupX, popupY, popupWidth, popupHeight, 0xFF666666); // Grey outline

        // Render title
        context.drawTextWithShadow(
            textRenderer,
            Text.literal("Edit Parameters: " + node.getType().getDisplayName()),
            popupX + 20,
            popupY + 15,
            0xFFFFFFFF
        );

        updateButtonPositions();

        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int contentRight = popupX + popupWidth;

        context.enableScissor(popupX + 1, contentTop, contentRight - 1, contentBottom);

        int sectionY = contentTop - scrollOffset;
        if (hasModeSelection()) {
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("Mode:"),
                popupX + 20,
                sectionY + 4,
                0xFFE0E0E0
            );

            int modeButtonX = popupX + 20;
            int modeButtonY = sectionY + LABEL_TO_FIELD_OFFSET;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            boolean modeButtonHovered = mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                                      mouseY >= modeButtonY && mouseY <= modeButtonY + modeButtonHeight;

            int modeBgColor = modeButtonHovered ? adjustColorBrightness(0xFF1A1A1A, 1.1f) : 0xFF1A1A1A;
            int modeBorderColor = modeButtonHovered ? 0xFF87CEEB : 0xFF666666;

            context.fill(modeButtonX, modeButtonY, modeButtonX + modeButtonWidth, modeButtonY + modeButtonHeight, modeBgColor);
            context.drawBorder(modeButtonX, modeButtonY, modeButtonWidth, modeButtonHeight, modeBorderColor);

            String modeText = selectedMode != null ? selectedMode.getDisplayName() : "Select Mode";
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(modeText),
                modeButtonX + 4,
                modeButtonY + 6,
                0xFFFFFFFF
            );

            context.drawTextWithShadow(
                textRenderer,
                Text.literal("▼"),
                modeButtonX + modeButtonWidth - 16,
                modeButtonY + 6,
                0xFFE0E0E0
            );

            sectionY = modeButtonY + modeButtonHeight + SECTION_SPACING;
        }

        this.textLineHeight = textRenderer.fontHeight;
        for (int i = 0; i < node.getParameters().size(); i++) {
            if (inventorySlotEditorActive && i == inventoryModeParamIndex) {
                continue;
            }
            NodeParameter param = node.getParameters().get(i);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(param.getName() + " (" + param.getType().getDisplayName() + "):"),
                popupX + 20,
                sectionY + 4,
                0xFFE0E0E0
            );

            int fieldX = popupX + 20;
            int fieldY = sectionY + LABEL_TO_FIELD_OFFSET;
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;
            if (inventorySlotEditorActive && i == inventorySlotParamIndex && inventorySlotSelector != null) {
                int selectorHeight = inventorySlotSelector.render(context, textRenderer, fieldX, fieldY, fieldWidth, mouseX, mouseY);
                sectionY = fieldY + selectorHeight + SECTION_SPACING;
                continue;
            }

            boolean isDropdownField = usesFunctionDropdownForIndex(i);
            if (isDropdownField) {
                functionDropdownFieldX = fieldX;
                functionDropdownFieldY = fieldY;
                functionDropdownFieldWidth = fieldWidth;
                functionDropdownFieldHeight = fieldHeight;
            }

            boolean dropdownActive = isDropdownField && functionDropdownOpen;
            boolean isFocused = !isDropdownField && i == focusedFieldIndex;
            int bgColor;
            int borderColor;
            if (dropdownActive) {
                bgColor = adjustColorBrightness(0xFF1A1A1A, 1.2f);
                borderColor = 0xFF87CEEB;
            } else if (isFocused) {
                bgColor = 0xFF2A2A2A;
                borderColor = 0xFF87CEEB;
            } else {
                bgColor = 0xFF1A1A1A;
                borderColor = 0xFF666666;
            }

            context.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, bgColor);
            context.drawBorder(fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

            String text = parameterValues.get(i);
            if (isDropdownField) {
                String displayValue;
                int textColor;
                if (!functionDropdownEnabled) {
                    displayValue = "";
                    textColor = 0xFF666666;
                } else {
                    displayValue = (text == null || text.isEmpty()) ? "select function" : text;
                    textColor = (text == null || text.isEmpty()) ? 0xFFAAAAAA : 0xFFFFFFFF;
                }
                int availableWidth = fieldWidth - 24;
                String displayText = trimDisplayString(textRenderer, displayValue, availableWidth);
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(displayText),
                    fieldX + 4,
                    fieldY + 6,
                    textColor
                );
                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("▼"),
                    fieldX + fieldWidth - 14,
                    fieldY + 6,
                    functionDropdownEnabled ? 0xFFE0E0E0 : 0xFF555555
                );
            } else if (text != null && !text.isEmpty()) {
                int availableWidth = fieldWidth - 8;
                String displayText = trimDisplayString(textRenderer, text, availableWidth);

                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(displayText),
                    fieldX + 4,
                    fieldY + 6,
                    0xFFFFFFFF
                );
            }

            if (!isDropdownField && isFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                String value = text != null ? text : "";
                int cursorX = fieldX + 4 + textRenderer.getWidth(value);
                cursorX = Math.min(cursorX, fieldX + fieldWidth - 2);
                context.fill(cursorX, fieldY + 4, cursorX + 1, fieldY + 16, 0xFFFFFFFF);
            }

            sectionY = fieldY + fieldHeight + SECTION_SPACING;
        }

        context.disableScissor();

        renderButton(context, textRenderer, saveButton, mouseX, mouseY);
        renderButton(context, textRenderer, cancelButton, mouseX, mouseY);

        if (hasModeSelection() && modeDropdownOpen) {
            int modeButtonX = popupX + 20;
            int modeButtonY = popupY + CONTENT_START_OFFSET + LABEL_TO_FIELD_OFFSET - scrollOffset;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            int dropdownY = modeButtonY + modeButtonHeight;
            int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

            modeDropdownHoverIndex = -1;
            if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                int hoverIndex = (mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT;
                if (hoverIndex >= 0 && hoverIndex < availableModes.size()) {
                    modeDropdownHoverIndex = hoverIndex;
                }
            }

            context.fill(modeButtonX, dropdownY, modeButtonX + modeButtonWidth, dropdownY + dropdownHeight, 0xFF1A1A1A);
            context.drawBorder(modeButtonX, dropdownY, modeButtonWidth, dropdownHeight, 0xFF666666);

            for (int i = 0; i < availableModes.size(); i++) {
                NodeMode mode = availableModes.get(i);
                int optionY = dropdownY + i * DROPDOWN_OPTION_HEIGHT;

                boolean isSelected = selectedMode == mode;
                boolean isHovered = i == modeDropdownHoverIndex;
                int optionColor = isSelected ? adjustColorBrightness(0xFF1A1A1A, 0.9f) : 0xFF1A1A1A;
                if (isHovered) {
                    optionColor = adjustColorBrightness(optionColor, 1.2f);
                }
                context.fill(modeButtonX, optionY, modeButtonX + modeButtonWidth, optionY + DROPDOWN_OPTION_HEIGHT, optionColor);

                context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(mode.getDisplayName()),
                    modeButtonX + 4,
                    optionY + 6,
                    0xFFFFFFFF
                );
            }
        }

        if (functionDropdownOpen) {
            renderFunctionDropdown(context, textRenderer, mouseX, mouseY);
        }

        renderScrollbar(context, contentTop, contentBottom);
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, ButtonWidget button, int mouseX, int mouseY) {
        if (button == null) {
            return;
        }

        boolean hovered = mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth() &&
                         mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();

        int bgColor = hovered ? 0xFF505050 : 0xFF3A3A3A;
        context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), bgColor);
        context.drawBorder(button.getX(), button.getY(), button.getWidth(), button.getHeight(), 0xFF666666);

        // Render button text
        context.drawCenteredTextWithShadow(
            textRenderer,
            button.getMessage(),
            button.getX() + button.getWidth() / 2,
            button.getY() + 6,
            0xFFFFFFFF
        );
    }

    private void renderScrollbar(DrawContext context, int contentTop, int contentBottom) {
        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = popupX + popupWidth - 12;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;
        int trackTop = contentTop;
        int trackBottom = contentBottom;
        int trackHeight = Math.max(1, trackBottom - trackTop);

        context.fill(trackLeft, trackTop, trackRight, trackBottom, 0xFF1A1A1A);
        context.drawBorder(trackLeft, trackTop, SCROLLBAR_WIDTH, trackHeight, 0xFF444444);

        int visibleScrollableHeight = Math.max(1, contentBottom - contentTop);
        int totalScrollableHeight = Math.max(visibleScrollableHeight, visibleScrollableHeight + maxScroll);
        int knobHeight = Math.max(20, (int) ((float) visibleScrollableHeight / totalScrollableHeight * trackHeight));
        int maxKnobTravel = Math.max(0, trackHeight - knobHeight);
        int knobOffset = maxKnobTravel <= 0 ? 0 : (int) ((float) scrollOffset / (float) maxScroll * maxKnobTravel);
        int knobTop = trackTop + knobOffset;

        context.fill(trackLeft + 1, knobTop, trackRight - 1, knobTop + knobHeight, 0xFF777777);
    }

    private void persistInventorySlotModeValue() {
        if (!inventorySlotEditorActive || inventorySlotSelector == null) {
            return;
        }
        if (inventoryModeParamIndex < 0 || inventoryModeParamIndex >= parameterValues.size()) {
            return;
        }
        parameterValues.set(
            inventoryModeParamIndex,
            InventorySlotModeHelper.buildStoredModeValue(
                inventorySlotSelector.getModeId(),
                inventorySlotSelectionIsPlayer
            )
        );
    }

    private void renderFunctionDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!functionDropdownOpen || functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return;
        }

        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        List<String> options = functionNameOptions;
        int optionCount = Math.max(1, options.isEmpty() ? 1 : options.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;

        boolean hoverInside = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                              mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
        functionDropdownHoverIndex = -1;
        if (hoverInside && !options.isEmpty()) {
            int hoverIndex = (mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT;
            if (hoverIndex >= 0 && hoverIndex < options.size()) {
                functionDropdownHoverIndex = hoverIndex;
            }
        }

        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xFF1A1A1A);
        context.drawBorder(dropdownX, dropdownY, dropdownWidth, dropdownHeight, 0xFF666666);

        if (options.isEmpty()) {
            int textY = dropdownY + (DROPDOWN_OPTION_HEIGHT - textRenderer.fontHeight) / 2 + 1;
            context.drawTextWithShadow(
                textRenderer,
                Text.literal("No functions available"),
                dropdownX + 4,
                textY,
                0xFFAAAAAA
            );
            return;
        }

        String currentValue = parameterValues.size() > functionDropdownParamIndex
            ? parameterValues.get(functionDropdownParamIndex)
            : null;

        for (int i = 0; i < options.size(); i++) {
            int optionTop = dropdownY + i * DROPDOWN_OPTION_HEIGHT;
            boolean isHovered = i == functionDropdownHoverIndex;
            boolean isSelected = currentValue != null && currentValue.equals(options.get(i));

            int optionColor = isSelected ? adjustColorBrightness(0xFF1A1A1A, 0.9f) : 0xFF1A1A1A;
            if (isHovered) {
                optionColor = adjustColorBrightness(optionColor, 1.2f);
            }

            context.fill(dropdownX, optionTop, dropdownX + dropdownWidth, optionTop + DROPDOWN_OPTION_HEIGHT, optionColor);
            String display = trimDisplayString(textRenderer, options.get(i), dropdownWidth - 8);
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(display),
                dropdownX + 4,
                optionTop + 6,
                0xFFFFFFFF
            );
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        updateButtonPositions();

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseClicked(mouseX, mouseY)) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
                modeDropdownOpen = false;
                modeDropdownHoverIndex = -1;
                return true;
            }
        }

        // Prepare scrollable bounds for subsequent hit checks
        int contentTop = getScrollAreaTop();
        int contentBottom = getScrollAreaBottom();
        int labelY = contentTop - scrollOffset;
        if (hasModeSelection()) {
            int modeButtonX = popupX + 20;
            int modeButtonY = labelY + LABEL_TO_FIELD_OFFSET;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;

            boolean modeVisible = modeButtonY <= contentBottom && modeButtonY + modeButtonHeight >= contentTop;

            if (modeDropdownOpen) {
                int dropdownY = modeButtonY + modeButtonHeight;
                int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

                if (mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                    mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
                    int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                    if (optionIndex >= 0 && optionIndex < availableModes.size()) {
                        selectedMode = availableModes.get(optionIndex);
                        node.setMode(selectedMode);
                        resetParameterFields();
                        updatePopupDimensions();
                        recreateButtons();
                        updateButtonPositions();
                    }
                    modeDropdownOpen = false;
                    modeDropdownHoverIndex = -1;
                    return true;
                }
            }

            if (modeVisible && mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                mouseY >= Math.max(modeButtonY, contentTop) && mouseY <= Math.min(modeButtonY + modeButtonHeight, contentBottom)) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
                modeDropdownOpen = !modeDropdownOpen;
                modeDropdownHoverIndex = -1;
                return true;
            }

            labelY = modeButtonY + modeButtonHeight + SECTION_SPACING;
        }

        // Check button clicks after handling dropdown interactions so dropdown selections aren't swallowed by buttons beneath
        if (saveButton != null && saveButton.isMouseOver(mouseX, mouseY)) {
            saveButton.onPress();
            return true;
        }
        if (cancelButton != null && cancelButton.isMouseOver(mouseX, mouseY)) {
            cancelButton.onPress();
            return true;
        }

        // Check field clicks
        for (int i = 0; i < node.getParameters().size(); i++) {
            int fieldX = popupX + 20;
            int fieldY = labelY + LABEL_TO_FIELD_OFFSET; // Match the rendering position
            int fieldWidth = popupWidth - 40;
            int fieldHeight = FIELD_HEIGHT;
            boolean isDropdownField = usesFunctionDropdownForIndex(i);
            if (isDropdownField) {
                functionDropdownFieldX = fieldX;
                functionDropdownFieldY = fieldY;
                functionDropdownFieldWidth = fieldWidth;
                functionDropdownFieldHeight = fieldHeight;
            }

            if (mouseX >= fieldX && mouseX <= fieldX + fieldWidth &&
                mouseY >= Math.max(fieldY, contentTop) && mouseY <= Math.min(fieldY + fieldHeight, contentBottom)) {
                if (isDropdownField) {
                    if (functionDropdownEnabled) {
                        toggleFunctionDropdown();
                    }
                } else {
                    focusedFieldIndex = i;
                }
                return true;
            }

            labelY = fieldY + fieldHeight + SECTION_SPACING;
        }

        if (handleFunctionDropdownClick(mouseX, mouseY)) {
            return true;
        }

        // Close dropdown if clicking outside of it
        if (hasModeSelection() && modeDropdownOpen) {
            int modeButtonX = popupX + 20;
            int modeButtonY = popupY + CONTENT_START_OFFSET + LABEL_TO_FIELD_OFFSET - scrollOffset;
            int modeButtonWidth = popupWidth - 40;
            int modeButtonHeight = FIELD_HEIGHT;
            int dropdownY = modeButtonY + modeButtonHeight;
            int dropdownHeight = availableModes.size() * DROPDOWN_OPTION_HEIGHT;

            // Check if click is outside dropdown area
            if (!(mouseX >= modeButtonX && mouseX <= modeButtonX + modeButtonWidth &&
                  mouseY >= modeButtonY && mouseY <= dropdownY + dropdownHeight)) {
                modeDropdownOpen = false; // Close dropdown
                modeDropdownHoverIndex = -1;
            }
        }
        
        if (functionDropdownOpen) {
            int dropdownX = functionDropdownFieldX;
            int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
            int dropdownWidth = functionDropdownFieldWidth;
            int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
            int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;
            boolean insideField = mouseX >= functionDropdownFieldX && mouseX <= functionDropdownFieldX + functionDropdownFieldWidth &&
                                  mouseY >= functionDropdownFieldY && mouseY <= functionDropdownFieldY + functionDropdownFieldHeight;
            boolean insideDropdown = mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
                                     mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
            if (!insideField && !insideDropdown) {
                functionDropdownOpen = false;
                functionDropdownHoverIndex = -1;
            }
        }
        
        // Close if clicking outside the popup
        boolean insidePopupBounds = mouseX >= popupX && mouseX <= popupX + popupWidth &&
                                    mouseY >= popupY && mouseY <= popupY + popupHeight;
        if (!insidePopupBounds && !isPointInFunctionDropdownArea(mouseX, mouseY)) {
            close();
            return true;
        }
        
        // Always consume mouse events when popup is visible to prevent underlying UI interaction
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!visible) return false;

        if (inventorySlotEditorActive && inventorySlotSelector != null) {
            if (inventorySlotSelector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }

        if (mouseX < popupX || mouseX > popupX + popupWidth || mouseY < popupY || mouseY > popupY + popupHeight) {
            return true;
        }

        if (maxScroll <= 0) {
            return true;
        }

        double newOffset = scrollOffset - verticalAmount * SCROLL_STEP;
        int clampedOffset = (int) Math.round(newOffset);
        clampedOffset = Math.max(0, Math.min(maxScroll, clampedOffset));
        if (clampedOffset != scrollOffset) {
            scrollOffset = clampedOffset;
            updateButtonPositions();
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        // Handle text input for focused field
        if (focusedFieldIndex >= 0 && focusedFieldIndex < parameterValues.size()) {
            String currentText = parameterValues.get(focusedFieldIndex);
            
            // Handle backspace
            if (keyCode == 259 && currentText.length() > 0) { // Backspace key
                parameterValues.set(focusedFieldIndex, currentText.substring(0, currentText.length() - 1));
                return true;
            }
            
            // Handle tab to move to next field
            if (keyCode == 258) { // Tab key
                focusNextEditableField();
                return true;
            }
        }
        
        // Close on Escape
        if (keyCode == 256) { // Escape key
            close();
            return true;
        }
        
        // Save on Enter
        if (keyCode == 257) { // Enter key
            saveParameters();
            return true;
        }
        
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        
        if (focusedFieldIndex >= 0 && focusedFieldIndex < parameterValues.size()) {
            String currentText = parameterValues.get(focusedFieldIndex);
            
            // Only allow printable characters and limit length to fit in the field
            int maxChars = (popupWidth - 44) / 6; // Calculate based on field width
            if (chr >= 32 && chr <= 126 && currentText.length() < maxChars) {
                parameterValues.set(focusedFieldIndex, currentText + chr);
                return true;
            }
        }
        
        return false;
    }

    private void saveParameters() {
        // Update node mode if applicable
        if (hasModeSelection() && selectedMode != null) {
            node.setMode(selectedMode);
        }
        
        // Update node parameters with field values
        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size() && i < parameterValues.size(); i++) {
            NodeParameter param = parameters.get(i);
            String value = parameterValues.get(i);
            param.setStringValue(value);
        }

        if (node.isParameterNode() && node.getParentParameterHost() != null) {
            Node host = node.getParentParameterHost();
            int slotIndex = node.getParentParameterSlotIndex();
            if (slotIndex < 0) {
                slotIndex = 0;
            }
            host.attachParameter(node, slotIndex);
        }

        node.recalculateDimensions();

        if (onSave != null) {
            onSave.accept(node);
        }

        close();
    }

    public void close() {
        visible = false;
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        functionDropdownOpen = false;
        functionDropdownHoverIndex = -1;
        if (inventorySlotSelector != null) {
            inventorySlotSelector.closeDropdown();
        }
        if (onClose != null) {
            onClose.run();
        }
    }

    public void show() {
        visible = true;
        focusedFieldIndex = -1;
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        functionDropdownOpen = false;
        functionDropdownHoverIndex = -1;
        scrollOffset = 0;
        updateButtonPositions();
    }

    public boolean isVisible() {
        return visible;
    }
    
    private void resetParameterFields() {
        parameterValues.clear();
        fieldFocused.clear();
        
        for (NodeParameter param : node.getParameters()) {
            parameterValues.add(param.getStringValue());
            fieldFocused.add(false);
        }
    }
    
    private void updatePopupDimensions() {
        int longestLineLength = ("Edit Parameters: " + node.getType().getDisplayName()).length();

        if (hasModeSelection()) {
            longestLineLength = Math.max(longestLineLength, "Mode:".length());
            String modeText = selectedMode != null ? selectedMode.getDisplayName() : "Select Mode";
            longestLineLength = Math.max(longestLineLength, modeText.length());
        }

        for (NodeParameter param : node.getParameters()) {
            String label = param.getName() + " (" + param.getType().getDisplayName() + "):";
            longestLineLength = Math.max(longestLineLength, label.length());
            String value = param.getStringValue();
            if (value != null) {
                longestLineLength = Math.max(longestLineLength, value.length());
            }
        }

        for (String value : parameterValues) {
            if (value != null) {
                longestLineLength = Math.max(longestLineLength, value.length());
            }
        }

        int computedWidth = longestLineLength * APPROX_CHAR_WIDTH + 64; // Padding for margins and borders
        int maxAllowedWidth = Math.max(MIN_POPUP_WIDTH, screenWidth - 40);
        this.popupWidth = Math.min(Math.max(MIN_POPUP_WIDTH, computedWidth), maxAllowedWidth);

        int contentHeight = CONTENT_START_OFFSET;
        if (hasModeSelection()) {
            contentHeight += LABEL_TO_FIELD_OFFSET + FIELD_HEIGHT;
            if (!node.getParameters().isEmpty()) {
                contentHeight += SECTION_SPACING;
            }
        }

        int paramCount = node.getParameters().size();
        for (int i = 0; i < paramCount; i++) {
            if (inventorySlotEditorActive && i == inventoryModeParamIndex) {
                continue;
            }
            if (inventorySlotEditorActive && i == inventorySlotParamIndex && inventorySlotSelector != null) {
                int estimatedHeight = inventorySlotSelector.getEstimatedHeight(textLineHeight);
                contentHeight += LABEL_TO_FIELD_OFFSET + estimatedHeight;
            } else {
                contentHeight += LABEL_TO_FIELD_OFFSET + FIELD_HEIGHT;
            }
            if (i < paramCount - 1) {
                contentHeight += SECTION_SPACING;
            }
        }

        contentHeight += BUTTON_TOP_MARGIN + BUTTON_HEIGHT + BOTTOM_PADDING;

        this.totalContentHeight = contentHeight;

        int maxPopupHeight = Math.max(MIN_POPUP_HEIGHT, screenHeight - topBarHeight - POPUP_VERTICAL_MARGIN);
        this.popupHeight = Math.min(Math.max(MIN_POPUP_HEIGHT, contentHeight), maxPopupHeight);
        this.popupX = Math.max(0, (screenWidth - popupWidth) / 2);
        int availableHeight = Math.max(0, screenHeight - topBarHeight);
        int centeredOffset = Math.max(0, (availableHeight - popupHeight) / 2);
        this.popupY = topBarHeight + centeredOffset;

        this.maxScroll = Math.max(0, totalContentHeight - popupHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }
    
    private void recreateButtons() {
        this.saveButton = ButtonWidget.builder(
            Text.literal("Save"),
            b -> saveParameters()
        ).dimensions(popupX + 20, computeVisibleButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build();

        this.cancelButton = ButtonWidget.builder(
            Text.literal("Cancel"),
            b -> close()
        ).dimensions(popupX + popupWidth - (BUTTON_WIDTH + 20), computeVisibleButtonY(), BUTTON_WIDTH, BUTTON_HEIGHT).build();

        updateButtonPositions();
    }
    
    private boolean hasModeSelection() {
        return !availableModes.isEmpty();
    }

    private int computeButtonY() {
        int contentEnd = totalContentHeight - (BUTTON_TOP_MARGIN + BUTTON_HEIGHT + BOTTOM_PADDING);
        return popupY + contentEnd + BUTTON_TOP_MARGIN;
    }

    private void updateButtonPositions() {
        int adjustedY = computeVisibleButtonY();

        if (saveButton != null) {
            saveButton.setX(popupX + 20);
            saveButton.setY(adjustedY);
        }

        if (cancelButton != null) {
            cancelButton.setX(popupX + popupWidth - (BUTTON_WIDTH + 20));
            cancelButton.setY(adjustedY);
        }
    }

    private int computeVisibleButtonY() {
        int base = computeButtonY();
        int bottomLimit = popupY + popupHeight - BOTTOM_PADDING - BUTTON_HEIGHT;
        int topLimit = popupY + CONTENT_START_OFFSET;
        int clamped = Math.min(base, bottomLimit);
        return Math.max(clamped, topLimit);
    }

    private int findFunctionDropdownIndex(Node node) {
        if (node == null || node.getType() != NodeType.EVENT_CALL) {
            return -1;
        }
        List<NodeParameter> params = node.getParameters();
        for (int i = 0; i < params.size(); i++) {
            NodeParameter param = params.get(i);
            if (param != null && "Name".equals(param.getName())) {
                return i;
            }
        }
        return -1;
    }

    private void focusNextEditableField() {
        int total = node.getParameters().size();
        if (total == 0) {
            focusedFieldIndex = -1;
            return;
        }
        int nextIndex = focusedFieldIndex;
        for (int attempts = 0; attempts < total; attempts++) {
            nextIndex = (nextIndex + 1 + total) % total;
            if (!usesFunctionDropdownForIndex(nextIndex)) {
                focusedFieldIndex = nextIndex;
                return;
            }
        }
        focusedFieldIndex = -1;
    }

    private boolean usesFunctionDropdownForIndex(int index) {
        return functionDropdownParamIndex >= 0 && index == functionDropdownParamIndex;
    }

    private void toggleFunctionDropdown() {
        if (functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return;
        }
        if (functionDropdownOpen) {
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
            return;
        }
        modeDropdownOpen = false;
        modeDropdownHoverIndex = -1;
        refreshFunctionNameOptions();
        functionDropdownOpen = true;
        functionDropdownHoverIndex = -1;
        focusedFieldIndex = -1;
    }

    private boolean handleFunctionDropdownClick(double mouseX, double mouseY) {
        if (!functionDropdownOpen || functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return false;
        }
        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;

        if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
            mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight) {
            if (!functionNameOptions.isEmpty()) {
                int optionIndex = (int) ((mouseY - dropdownY) / DROPDOWN_OPTION_HEIGHT);
                if (optionIndex >= 0 && optionIndex < functionNameOptions.size()) {
                    parameterValues.set(functionDropdownParamIndex, functionNameOptions.get(optionIndex));
                }
            }
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
            return true;
        }
        return false;
    }

    private void refreshFunctionNameOptions() {
        functionNameOptions.clear();
        if (functionNameSupplier != null) {
            List<String> supplied = functionNameSupplier.get();
            if (supplied != null) {
                for (String name : supplied) {
                    if (name == null) {
                        continue;
                    }
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty() && !functionNameOptions.contains(trimmed)) {
                        functionNameOptions.add(trimmed);
                    }
                }
            }
        }
        functionDropdownEnabled = !functionNameOptions.isEmpty();
        if (!functionDropdownEnabled) {
            functionDropdownOpen = false;
            functionDropdownHoverIndex = -1;
        }
    }

    private boolean isPointInFunctionDropdownArea(double mouseX, double mouseY) {
        if (functionDropdownParamIndex < 0 || !functionDropdownEnabled) {
            return false;
        }
        boolean insideField = mouseX >= functionDropdownFieldX &&
                              mouseX <= functionDropdownFieldX + functionDropdownFieldWidth &&
                              mouseY >= functionDropdownFieldY &&
                              mouseY <= functionDropdownFieldY + functionDropdownFieldHeight;
        if (insideField) {
            return true;
        }
        if (!functionDropdownOpen) {
            return false;
        }
        int dropdownX = functionDropdownFieldX;
        int dropdownY = functionDropdownFieldY + functionDropdownFieldHeight;
        int dropdownWidth = functionDropdownFieldWidth;
        int optionCount = Math.max(1, functionNameOptions.isEmpty() ? 1 : functionNameOptions.size());
        int dropdownHeight = optionCount * DROPDOWN_OPTION_HEIGHT;
        return mouseX >= dropdownX && mouseX <= dropdownX + dropdownWidth &&
               mouseY >= dropdownY && mouseY <= dropdownY + dropdownHeight;
    }

    private String trimDisplayString(TextRenderer renderer, String text, int availableWidth) {
        if (text == null) {
            return "";
        }
        if (renderer.getWidth(text) <= availableWidth) {
            return text;
        }
        int ellipsisWidth = renderer.getWidth("...");
        int trimmedWidth = Math.max(0, availableWidth - ellipsisWidth);
        String trimmed = renderer.trimToWidth(text, trimmedWidth);
        return trimmed + "...";
    }

    private int adjustColorBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * factor)));
        int g = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * factor)));
        int b = Math.min(255, Math.max(0, Math.round((color & 0xFF) * factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getScrollAreaTop() {
        return popupY + CONTENT_START_OFFSET;
    }

    private int getScrollAreaBottom() {
        int top = getScrollAreaTop();
        int baseBottom = popupY + popupHeight - BOTTOM_PADDING;
        int buttonTop = saveButton != null ? saveButton.getY() : computeVisibleButtonY();
        int limitedBottom = buttonTop - 4;
        int bottom = Math.min(baseBottom, limitedBottom);
        if (bottom <= top) {
            bottom = Math.max(top + 1, baseBottom);
        }
        return bottom;
    }
}
