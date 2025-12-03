package com.pathmind.nodes;

import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Locale;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IExploreProcess;
import baritone.api.process.IGetToBlockProcess;
import baritone.api.process.IMineProcess;
import baritone.api.process.IFarmProcess;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BlockOptionalMeta;
import com.pathmind.execution.PreciseCompletionTracker;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * Represents a single node in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each node has inputs, outputs, and parameters.
 */
public class Node {
    public static final int NO_OUTPUT = -1;
    private final String id;
    private final NodeType type;
    private NodeMode mode;
    private int x, y;
    private static final int MIN_WIDTH = 92;
    private static final int MIN_HEIGHT = 44;
    private static final int EVENT_FUNCTION_MIN_HEIGHT = 36;
    private static final int CHAR_PIXEL_WIDTH = 6;
    private static final int HEADER_HEIGHT = 18;
    private static final int PARAM_LINE_HEIGHT = 10;
    private static final int PARAM_PADDING_TOP = 2;
    private static final int PARAM_PADDING_BOTTOM = 4;
    private static final int MAX_PARAMETER_LABEL_LENGTH = 20;
    private static final int BODY_PADDING_NO_PARAMS = 10;
    private static final int START_END_SIZE = 36;
    private static final String ERROR_MESSAGE_PREFIX = "\u00A74[\u00A7cPathmind\u00A74] \u00A77";
    private static final String INFO_MESSAGE_PREFIX = "\u00A7a[\u00A7bPathmind\u00A7a] \u00A77";
    private static final long CRAFTING_ACTION_DELAY_MS = 75L;
    private static final int CRAFTING_OUTPUT_POLL_LIMIT = 5;
    private static final int SENSOR_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int SENSOR_SLOT_INNER_PADDING = 4;
    private static final int SENSOR_SLOT_MIN_CONTENT_WIDTH = 60;
    private static final int SENSOR_SLOT_MIN_CONTENT_HEIGHT = 28;
    private static final int ACTION_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int ACTION_SLOT_INNER_PADDING = 4;
    private static final int ACTION_SLOT_MIN_CONTENT_WIDTH = 80;
    private static final int ACTION_SLOT_MIN_CONTENT_HEIGHT = 32;
    private static final int PARAMETER_SLOT_MARGIN_HORIZONTAL = 8;
    private static final int PARAMETER_SLOT_INNER_PADDING = 4;
    private static final int PARAMETER_SLOT_MIN_CONTENT_WIDTH = 88;
    private static final int PARAMETER_SLOT_MIN_CONTENT_HEIGHT = 32;
    private static final int PARAMETER_SLOT_LABEL_HEIGHT = 12;
    private static final int PARAMETER_SLOT_BOTTOM_PADDING = 6;
    private static final int SLOT_AREA_PADDING_TOP = 0;
    private static final int SLOT_AREA_PADDING_BOTTOM = 6;
    private static final int SLOT_VERTICAL_SPACING = 6;
    private static final int COORDINATE_FIELD_WIDTH = 44;
    private static final int COORDINATE_FIELD_HEIGHT = 16;
    private static final int COORDINATE_FIELD_SPACING = 6;
    private static final int COORDINATE_FIELD_TOP_MARGIN = 6;
    private static final int COORDINATE_FIELD_LABEL_HEIGHT = 10;
    private static final int COORDINATE_FIELD_BOTTOM_MARGIN = 6;
    private static final int AMOUNT_FIELD_TOP_MARGIN = 6;
    private static final int AMOUNT_FIELD_LABEL_HEIGHT = 10;
    private static final int AMOUNT_FIELD_HEIGHT = 16;
    private static final int AMOUNT_FIELD_BOTTOM_MARGIN = 6;
    private static final double PARAMETER_SEARCH_RADIUS = 64.0;
    private static final double DEFAULT_REACH_DISTANCE_SQUARED = 25.0D;
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile("[^a-z0-9_:/.-]");
    private int width;
    private int height;
    private int nextOutputSocket = 0;
    private int repeatRemainingIterations = 0;
    private boolean repeatActive = false;
    private boolean lastSensorResult = false;
    private boolean selected = false;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private final List<NodeParameter> parameters;
    private Node attachedSensor;
    private Node parentControl;
    private Node attachedActionNode;
    private Node parentActionControl;
    private final Map<Integer, Node> attachedParameters;
    private Node parentParameterHost;
    private int parentParameterSlotIndex;
    private boolean socketsHidden;
    private RuntimeParameterData runtimeParameterData;

    public Node(NodeType type, int x, int y) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.mode = NodeMode.getDefaultModeForNodeType(type);
        this.x = x;
        this.y = y;
        this.parameters = new ArrayList<>();
        this.attachedSensor = null;
        this.parentControl = null;
        this.attachedActionNode = null;
        this.parentActionControl = null;
        this.attachedParameters = new HashMap<>();
        this.parentParameterHost = null;
        this.parentParameterSlotIndex = -1;
        this.socketsHidden = false;
        initializeParameters();
        recalculateDimensions();
        resetControlState();
    }

    private static final class RuntimeParameterData {
        private BlockPos targetBlockPos;
        private Vec3d targetVector;
        private Entity targetEntity;
        private Item targetItem;
        private String targetBlockId;
        private List<String> targetBlockIds;
        private String targetPlayerName;
        private String targetItemId;
        private String targetEntityId;
        private String message;
        private Double durationSeconds;
        private Boolean booleanValue;
        private String handName;
        private Integer slotIndex;
        private String schematicName;
        private Double rangeValue;
        private Float resolvedYaw;
        private Float resolvedPitch;
        private Float resolvedYawOffset;
        private Float resolvedPitchOffset;
    }

    private static final class PlacementFailure extends RuntimeException {
        PlacementFailure(String message) {
            super(message);
        }
    }

    private enum ParameterHandlingResult {
        CONTINUE,
        COMPLETE
    }

    private enum ParameterUsage {
        POSITION,
        LOOK_ORIENTATION,
        TURN_OFFSET
    }

    private static String normalizeParameterKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void sendNodeErrorMessage(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() -> sendNodeErrorMessageOnClientThread(client, message));
    }

    private void sendNodeErrorMessageOnClientThread(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || client.player == null || message == null || message.isEmpty()) {
            return;
        }

        client.player.sendMessage(Text.literal(ERROR_MESSAGE_PREFIX + message), false);
    }

    private void sendNodeInfoMessage(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || message == null || message.isEmpty()) {
            return;
        }

        client.execute(() -> sendNodeInfoMessageOnClientThread(client, message));
    }

    private void sendNodeInfoMessageOnClientThread(net.minecraft.client.MinecraftClient client, String message) {
        if (client == null || client.player == null || message == null || message.isEmpty()) {
            return;
        }

        client.player.sendMessage(Text.literal(INFO_MESSAGE_PREFIX + message), false);
    }

    /**
     * Gets the Baritone instance for the current player
     * @return IBaritone instance or null if not available
     */
    private IBaritone getBaritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Exception e) {
            System.err.println("Failed to get Baritone instance: " + e.getMessage());
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }
    
    public NodeMode getMode() {
        return mode;
    }
    
    public void setMode(NodeMode mode) {
        this.mode = mode;
        // Reinitialize parameters when mode changes
        parameters.clear();
        initializeParameters();
        recalculateDimensions();
        resetControlState();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPosition(int x, int y) {
        setPositionSilently(x, y);
        if (attachedSensor != null) {
            updateAttachedSensorPosition();
        }
        if (attachedActionNode != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
    }

    private void setPositionSilently(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
    }

    public int getDragOffsetX() {
        return dragOffsetX;
    }

    public void setDragOffsetX(int dragOffsetX) {
        this.dragOffsetX = dragOffsetX;
    }

    public int getDragOffsetY() {
        return dragOffsetY;
    }

    public void setDragOffsetY(int dragOffsetY) {
        this.dragOffsetY = dragOffsetY;
    }

    public boolean containsPoint(int pointX, int pointY) {
        return pointX >= x && pointX <= x + getWidth() && pointY >= y && pointY <= y + getHeight();
    }

    public Text getDisplayName() {
        return Text.literal(type.getDisplayName());
    }

    public boolean isSensorNode() {
        return isSensorType(type);
    }

    public boolean isParameterNode() {
        return type.getCategory() == NodeCategory.PARAMETERS;
    }

    public static boolean isSensorType(NodeType nodeType) {
        if (nodeType == null) {
            return false;
        }
        switch (nodeType) {
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_BLOCK_BELOW:
            case SENSOR_LIGHT_LEVEL_BELOW:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ENTITY_NEARBY:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
                return true;
            default:
                return false;
        }
    }

    public static boolean isParameterType(NodeType nodeType) {
        return nodeType != null && nodeType.getCategory() == NodeCategory.PARAMETERS;
    }

    public boolean canAcceptSensor() {
        switch (type) {
            case CONTROL_IF:
            case CONTROL_IF_ELSE:
            case CONTROL_REPEAT_UNTIL:
                return true;
            default:
                return false;
        }
    }

    public boolean hasSensorSlot() {
        return canAcceptSensor();
    }

    public boolean canAcceptParameter() {
        if (type == NodeType.OPEN_INVENTORY || type == NodeType.CLOSE_GUI) {
            return false;
        }
        return !isParameterNode()
            && type != NodeType.START
            && type != NodeType.EVENT_CALL
            && type != NodeType.EVENT_FUNCTION
            && type != NodeType.SWING
            && type.getCategory() != NodeCategory.LOGIC;
    }

    public boolean hasParameterSlot() {
        return canAcceptParameter();
    }

    public boolean canAcceptParameterAt(int slotIndex) {
        if (!canAcceptParameter()) {
            return false;
        }
        return slotIndex >= 0 && slotIndex < getParameterSlotCount();
    }

    private boolean isParameterSlotRequired(int slotIndex) {
        if (!canAcceptParameterAt(slotIndex)) {
            return false;
        }
        if (type == NodeType.PLACE) {
            if (slotIndex == 0) {
                return true;
            }
            Node coordinateParameter = getAttachedParameter(slotIndex);
            if (coordinateParameter != null) {
                return true;
            }
            Node blockParameter = getAttachedParameter(0);
            return blockParameter == null || !parameterProvidesCoordinates(blockParameter);
        }
        return slotIndex == 0;
    }

    private boolean isParameterCompatibleWithSlot(Node parameter, int slotIndex) {
        if (parameter == null) {
            return false;
        }
        if (type != NodeType.PLACE) {
            return true;
        }
        NodeType parameterType = parameter.getType();
        if (slotIndex == 0) {
            switch (parameterType) {
                case PARAM_BLOCK:
                case PARAM_BLOCK_LIST:
                case PARAM_PLACE_TARGET:
                    return true;
                default:
                    return false;
            }
        }
        return slotIndex == 1 ? parameterProvidesCoordinates(parameterType) : true;
    }

    public boolean canAcceptActionNode() {
        switch (type) {
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_FOREVER:
                return true;
            default:
                return false;
        }
    }

    public boolean hasActionSlot() {
        return canAcceptActionNode();
    }

    public boolean hasAttachedSensor() {
        return attachedSensor != null;
    }

    public Node getAttachedSensor() {
        return attachedSensor;
    }

    public boolean isAttachedToControl() {
        return parentControl != null;
    }

    public Node getParentControl() {
        return parentControl;
    }

    public String getAttachedSensorId() {
        return attachedSensor != null ? attachedSensor.getId() : null;
    }

    public String getParentControlId() {
        return parentControl != null ? parentControl.getId() : null;
    }

    public boolean hasAttachedParameter() {
        return !attachedParameters.isEmpty();
    }

    public Node getAttachedParameter() {
        return getAttachedParameter(0);
    }

    public Node getAttachedParameter(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        return attachedParameters.get(slotIndex);
    }

    public Node getParentParameterHost() {
        return parentParameterHost;
    }

    public int getParentParameterSlotIndex() {
        return parentParameterSlotIndex;
    }

    public Map<Integer, Node> getAttachedParameters() {
        return Collections.unmodifiableMap(attachedParameters);
    }

    public String getAttachedParameterId() {
        Node parameter = getAttachedParameter();
        return parameter != null ? parameter.getId() : null;
    }

    public String getParentParameterHostId() {
        return parentParameterHost != null ? parentParameterHost.getId() : null;
    }

    public boolean hasAttachedActionNode() {
        return attachedActionNode != null;
    }

    public Node getAttachedActionNode() {
        return attachedActionNode;
    }

    public boolean isAttachedToActionControl() {
        return parentActionControl != null;
    }

    public Node getParentActionControl() {
        return parentActionControl;
    }

    public String getAttachedActionId() {
        return attachedActionNode != null ? attachedActionNode.getId() : null;
    }

    public String getParentActionControlId() {
        return parentActionControl != null ? parentActionControl.getId() : null;
    }

    public int getInputSocketCount() {
        if (type == NodeType.START || type == NodeType.EVENT_FUNCTION || isSensorNode() || isParameterNode()) {
            return 0;
        }
        return 1;
    }

    public int getOutputSocketCount() {
        if (isSensorNode() || isParameterNode()) {
            return 0;
        }
        if (type == NodeType.CONTROL_FOREVER) {
            return 0;
        }
        if (type == NodeType.CONTROL_IF_ELSE) {
            return 2;
        }
        return 1;
    }

    public int getOutputSocketColor(int socketIndex) {
        if (type == NodeType.CONTROL_IF_ELSE) {
            if (socketIndex == 0) {
                return 0xFF4CAF50; // Green for true branch
            } else if (socketIndex == 1) {
                return 0xFFF44336; // Red for false branch
            }
        }
        return getType().getColor();
    }

    public int getSocketY(int socketIndex, boolean isInput) {
        int socketHeight = 12;
        if (type == NodeType.START || type == NodeType.EVENT_FUNCTION) {
            // For START and END nodes, center the socket vertically
            return y + getHeight() / 2;
        } else {
            int headerHeight = 14;
            int contentStartY = y + headerHeight + 6; // Start sockets below header with some padding
            return contentStartY + socketIndex * socketHeight;
        }
    }
    
    public int getSocketX(boolean isInput) {
        return isInput ? x - 4 : x + getWidth() + 4;
    }
    
    public void setNextOutputSocket(int socketIndex) {
        this.nextOutputSocket = socketIndex < 0 ? NO_OUTPUT : Math.max(0, socketIndex);
    }

    public int consumeNextOutputSocket() {
        int value = this.nextOutputSocket;
        this.nextOutputSocket = 0;
        return value;
    }
    
    public boolean isSocketClicked(int mouseX, int mouseY, int socketIndex, boolean isInput) {
        if (socketsHidden) {
            return false;
        }
        int socketX = getSocketX(isInput);
        int socketY = getSocketY(socketIndex, isInput);
        int socketRadius = 6; // Smaller size for more space

        return Math.abs(mouseX - socketX) <= socketRadius && Math.abs(mouseY - socketY) <= socketRadius;
    }

    public int getSensorSlotLeft() {
        return x + SENSOR_SLOT_MARGIN_HORIZONTAL;
    }

    private int getSlotAreaStartY() {
        int top = y + HEADER_HEIGHT;
        if (isParameterNode()) {
            if (hasParameters()) {
                int lineCount = parameters.size();
                if (supportsModeSelection()) {
                    lineCount++;
                }
                top += PARAM_PADDING_TOP + lineCount * PARAM_LINE_HEIGHT + PARAM_PADDING_BOTTOM;
            } else {
                top += BODY_PADDING_NO_PARAMS;
            }
        } else if (hasParameterSlot()) {
            int slotCount = getParameterSlotCount();
            for (int i = 0; i < slotCount; i++) {
                top += PARAMETER_SLOT_LABEL_HEIGHT + getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING;
            }
            if (hasCoordinateInputFields()) {
                top += getCoordinateFieldDisplayHeight();
            }
            if (hasSensorSlot() || hasActionSlot()) {
                top += SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSensorSlot() || hasActionSlot()) {
            top += SLOT_AREA_PADDING_TOP;
        } else {
            top += BODY_PADDING_NO_PARAMS;
        }
        return top;
    }

    public int getSensorSlotTop() {
        return getSlotAreaStartY();
    }

    public int getSensorSlotWidth() {
        int minWidth = SENSOR_SLOT_MIN_CONTENT_WIDTH + 2 * SENSOR_SLOT_INNER_PADDING;
        int widthWithMargins = this.width - 2 * SENSOR_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    public int getSensorSlotHeight() {
        int sensorContentHeight = attachedSensor != null ? attachedSensor.getHeight() : SENSOR_SLOT_MIN_CONTENT_HEIGHT;
        return sensorContentHeight + 2 * SENSOR_SLOT_INNER_PADDING;
    }

    public boolean isPointInsideSensorSlot(int pointX, int pointY) {
        if (!hasSensorSlot()) {
            return false;
        }
        int slotLeft = getSensorSlotLeft();
        int slotTop = getSensorSlotTop();
        int slotWidth = getSensorSlotWidth();
        int slotHeight = getSensorSlotHeight();
        return pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
               pointY >= slotTop && pointY <= slotTop + slotHeight;
    }

    public int getParameterSlotCount() {
        if (!hasParameterSlot()) {
            return 0;
        }
        if (type == NodeType.PLACE) {
            return 2;
        }
        return 1;
    }

    public int getParameterSlotLeft() {
        return x + PARAMETER_SLOT_MARGIN_HORIZONTAL;
    }

    public int getParameterSlotTop(int slotIndex) {
        int top = y + HEADER_HEIGHT + PARAMETER_SLOT_LABEL_HEIGHT;
        for (int i = 0; i < slotIndex; i++) {
            top += getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING + PARAMETER_SLOT_LABEL_HEIGHT;
        }
        return top;
    }

    @Deprecated
    public int getParameterSlotTop() {
        return getParameterSlotTop(0);
    }

    public String getParameterSlotLabel(int slotIndex) {
        if (type == NodeType.PLACE) {
            return slotIndex == 0 ? "Block" : "Position";
        }
        return "Parameter";
    }

    public int getParameterSlotWidth() {
        int widthWithMargins = this.width - 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
        return Math.max(PARAMETER_SLOT_MIN_CONTENT_WIDTH, widthWithMargins);
    }

    public int getParameterSlotHeight(int slotIndex) {
        Node parameter = getAttachedParameter(slotIndex);
        int contentHeight = parameter != null ? parameter.getHeight() : PARAMETER_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * PARAMETER_SLOT_INNER_PADDING;
    }

    @Deprecated
    public int getParameterSlotHeight() {
        return getParameterSlotHeight(0);
    }

    private int getParameterSlotsBottom() {
        int slotCount = getParameterSlotCount();
        if (slotCount <= 0) {
            return y + HEADER_HEIGHT;
        }
        int lastIndex = slotCount - 1;
        return getParameterSlotTop(lastIndex) + getParameterSlotHeight(lastIndex);
    }

    public boolean hasCoordinateInputFields() {
        return false;
    }

    public int getCoordinateFieldDisplayHeight() {
        if (!hasCoordinateInputFields()) {
            return 0;
        }
        return COORDINATE_FIELD_TOP_MARGIN + COORDINATE_FIELD_LABEL_HEIGHT + COORDINATE_FIELD_HEIGHT + COORDINATE_FIELD_BOTTOM_MARGIN;
    }

    public int getCoordinateFieldLabelTop() {
        return getParameterSlotsBottom() + COORDINATE_FIELD_TOP_MARGIN;
    }

    public int getCoordinateFieldInputTop() {
        return getCoordinateFieldLabelTop() + COORDINATE_FIELD_LABEL_HEIGHT;
    }

    public int getCoordinateFieldLabelHeight() {
        return COORDINATE_FIELD_LABEL_HEIGHT;
    }

    public int getCoordinateFieldHeight() {
        return COORDINATE_FIELD_HEIGHT;
    }

    public int getCoordinateFieldWidth() {
        return COORDINATE_FIELD_WIDTH;
    }

    public int getCoordinateFieldSpacing() {
        return COORDINATE_FIELD_SPACING;
    }

    public int getCoordinateFieldStartX() {
        int slotLeft = getParameterSlotLeft();
        int slotWidth = getParameterSlotWidth();
        int totalFieldWidth = getCoordinateFieldTotalWidth();
        if (totalFieldWidth >= slotWidth) {
            return slotLeft;
        }
        return slotLeft + (slotWidth - totalFieldWidth) / 2;
    }

    public int getCoordinateFieldTotalWidth() {
        return (COORDINATE_FIELD_WIDTH * 3) + (COORDINATE_FIELD_SPACING * 2);
    }

    public boolean hasAmountInputField() {
        return type == NodeType.COLLECT && (mode == null || mode == NodeMode.COLLECT_SINGLE);
    }

    public int getAmountFieldDisplayHeight() {
        if (!hasAmountInputField()) {
            return 0;
        }
        return AMOUNT_FIELD_TOP_MARGIN + AMOUNT_FIELD_LABEL_HEIGHT + AMOUNT_FIELD_HEIGHT + AMOUNT_FIELD_BOTTOM_MARGIN;
    }

    public int getAmountFieldLabelTop() {
        int top = getParameterSlotsBottom();
        if (hasCoordinateInputFields()) {
            top += getCoordinateFieldDisplayHeight();
        }
        return top + AMOUNT_FIELD_TOP_MARGIN;
    }

    public int getAmountFieldInputTop() {
        return getAmountFieldLabelTop() + AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public int getAmountFieldLabelHeight() {
        return AMOUNT_FIELD_LABEL_HEIGHT;
    }

    public int getAmountFieldHeight() {
        return AMOUNT_FIELD_HEIGHT;
    }

    public int getAmountFieldWidth() {
        return getParameterSlotWidth();
    }

    public int getAmountFieldLeft() {
        return getParameterSlotLeft();
    }

    public boolean isPointInsideParameterSlot(int pointX, int pointY) {
        return getParameterSlotIndexAt(pointX, pointY) >= 0;
    }

    public int getParameterSlotIndexAt(int pointX, int pointY) {
        if (!hasParameterSlot()) {
            return -1;
        }
        int slotCount = getParameterSlotCount();
        int slotLeft = getParameterSlotLeft();
        int slotWidth = getParameterSlotWidth();
        for (int i = 0; i < slotCount; i++) {
            int slotTop = getParameterSlotTop(i);
            int slotHeight = getParameterSlotHeight(i);
            if (pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
                pointY >= slotTop && pointY <= slotTop + slotHeight) {
                return i;
            }
        }
        return -1;
    }

    public void updateAttachedParameterPositions() {
        for (Integer slotIndex : attachedParameters.keySet()) {
            updateAttachedParameterPosition(slotIndex);
        }
    }

    private void updateAttachedParameterPosition(int slotIndex) {
        Node parameter = getAttachedParameter(slotIndex);
        if (parameter == null) {
            return;
        }
        int slotX = getParameterSlotLeft() + PARAMETER_SLOT_INNER_PADDING;
        int slotY = getParameterSlotTop(slotIndex) + PARAMETER_SLOT_INNER_PADDING;
        int availableWidth = getParameterSlotWidth() - 2 * PARAMETER_SLOT_INNER_PADDING;
        int availableHeight = getParameterSlotHeight(slotIndex) - 2 * PARAMETER_SLOT_INNER_PADDING;
        int parameterX = slotX + Math.max(0, (availableWidth - parameter.getWidth()) / 2);
        int parameterY = slotY + Math.max(0, (availableHeight - parameter.getHeight()) / 2);
        parameter.setPositionSilently(parameterX, parameterY);
    }

    public int getActionSlotLeft() {
        return x + ACTION_SLOT_MARGIN_HORIZONTAL;
    }

    public int getActionSlotTop() {
        int top = getSlotAreaStartY();
        if (hasSensorSlot()) {
            top += getSensorSlotHeight();
            if (hasActionSlot()) {
                top += SLOT_VERTICAL_SPACING;
            }
        }
        return top;
    }

    public int getActionSlotWidth() {
        int minWidth = ACTION_SLOT_MIN_CONTENT_WIDTH + 2 * ACTION_SLOT_INNER_PADDING;
        int widthWithMargins = this.width - 2 * ACTION_SLOT_MARGIN_HORIZONTAL;
        return Math.max(minWidth, widthWithMargins);
    }

    public int getActionSlotHeight() {
        int contentHeight = attachedActionNode != null ? attachedActionNode.getHeight() : ACTION_SLOT_MIN_CONTENT_HEIGHT;
        return contentHeight + 2 * ACTION_SLOT_INNER_PADDING;
    }

    public boolean isPointInsideActionSlot(int pointX, int pointY) {
        if (!hasActionSlot()) {
            return false;
        }
        int slotLeft = getActionSlotLeft();
        int slotTop = getActionSlotTop();
        int slotWidth = getActionSlotWidth();
        int slotHeight = getActionSlotHeight();
        return pointX >= slotLeft && pointX <= slotLeft + slotWidth &&
               pointY >= slotTop && pointY <= slotTop + slotHeight;
    }

    public void updateAttachedSensorPosition() {
        if (attachedSensor == null) {
            return;
        }
        int slotX = getSensorSlotLeft() + SENSOR_SLOT_INNER_PADDING;
        int slotY = getSensorSlotTop() + SENSOR_SLOT_INNER_PADDING;
        int availableWidth = getSensorSlotWidth() - 2 * SENSOR_SLOT_INNER_PADDING;
        int availableHeight = getSensorSlotHeight() - 2 * SENSOR_SLOT_INNER_PADDING;
        int sensorX = slotX + Math.max(0, (availableWidth - attachedSensor.getWidth()) / 2);
        int sensorY = slotY + Math.max(0, (availableHeight - attachedSensor.getHeight()) / 2);
        attachedSensor.setPosition(sensorX, sensorY);
    }

    public void updateAttachedActionPosition() {
        if (attachedActionNode == null) {
            return;
        }
        int slotX = getActionSlotLeft() + ACTION_SLOT_INNER_PADDING;
        int slotY = getActionSlotTop() + ACTION_SLOT_INNER_PADDING;
        int availableWidth = getActionSlotWidth() - 2 * ACTION_SLOT_INNER_PADDING;
        int availableHeight = getActionSlotHeight() - 2 * ACTION_SLOT_INNER_PADDING;
        int nodeX = slotX + Math.max(0, (availableWidth - attachedActionNode.getWidth()) / 2);
        int nodeY = slotY + Math.max(0, (availableHeight - attachedActionNode.getHeight()) / 2);
        attachedActionNode.setPositionSilently(nodeX, nodeY);
    }

    public boolean attachSensor(Node sensor) {
        if (!canAcceptSensor() || sensor == null || !sensor.isSensorNode() || sensor == this) {
            return false;
        }

        if (sensor.parentControl == this && attachedSensor == sensor) {
            updateAttachedSensorPosition();
            return true;
        }

        if (sensor.parentControl != null) {
            sensor.parentControl.detachSensor();
        }

        if (attachedSensor != null && attachedSensor != sensor) {
            Node previousSensor = attachedSensor;
            previousSensor.parentControl = null;
            previousSensor.setDragging(false);
            previousSensor.setSelected(false);
            previousSensor.setPositionSilently(this.x + this.width + SENSOR_SLOT_MARGIN_HORIZONTAL, this.y);
        }

        attachedSensor = sensor;
        sensor.parentControl = this;
        sensor.setDragging(false);
        sensor.setSelected(false);

        recalculateDimensions();
        updateAttachedSensorPosition();
        return true;
    }

    public void detachSensor() {
        if (attachedSensor != null) {
            Node sensor = attachedSensor;
            sensor.parentControl = null;
            attachedSensor = null;
            recalculateDimensions();
        }
    }

    public boolean attachParameter(Node parameter) {
        return attachParameter(parameter, 0);
    }

    public boolean attachParameter(Node parameter, int slotIndex) {
        if (!canAcceptParameterAt(slotIndex) || parameter == null || !parameter.isParameterNode() || parameter == this) {
            return false;
        }

        if (parameter.parentParameterHost == this && parameter.parentParameterSlotIndex == slotIndex) {
            updateAttachedParameterPosition(slotIndex);
            return true;
        }

        if (!isParameterCompatibleWithSlot(parameter, slotIndex)) {
            sendIncompatibleParameterMessage(parameter);
            return false;
        }

        Node previousHost = parameter.parentParameterHost;
        int previousSlot = parameter.parentParameterSlotIndex;

        if (previousHost != null && (previousHost != this || previousSlot != slotIndex)) {
            previousHost.detachParameter(previousSlot);
        }

        Node existing = attachedParameters.get(slotIndex);
        if (existing != null && existing != parameter) {
            detachParameter(slotIndex);
        }

        attachedParameters.put(slotIndex, parameter);
        parameter.parentParameterHost = this;
        parameter.parentParameterSlotIndex = slotIndex;
        parameter.setDragging(false);
        parameter.setSelected(false);
        parameter.setSocketsHidden(true);
        parameter.recalculateDimensions();

        refreshAttachedParameterValues();

        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();

        boolean handledAtRuntime = canApplyParameterValues(parameter) || canHandleParameterRuntime(parameter);
        if (!handledAtRuntime) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            sendNodeErrorMessage(client, "Parameter \"" + parameter.getType().getDisplayName() + "\" cannot configure \"" + this.type.getDisplayName() + "\" and will be ignored.");
        }

        return true;
    }

    public void detachParameter() {
        detachParameter(0);
    }

    public void detachParameter(int slotIndex) {
        Node parameter = attachedParameters.remove(slotIndex);
        if (parameter == null) {
            return;
        }
        parameter.parentParameterHost = null;
        parameter.parentParameterSlotIndex = -1;
        parameter.setSocketsHidden(false);
        parameter.recalculateDimensions();
        parameter.setPositionSilently(this.x + this.width + PARAMETER_SLOT_MARGIN_HORIZONTAL, this.y);

        refreshAttachedParameterValues();
        recalculateDimensions();
        updateAttachedParameterPositions();
        updateParentControlLayout();
    }

    private void updateParentControlLayout() {
        if (parentControl != null) {
            parentControl.recalculateDimensions();
            parentControl.updateAttachedSensorPosition();
        }
    }

    private boolean applyParameterValuesFromNode(Node parameter) {
        if (parameter == null || !parameter.isParameterNode()) {
            return false;
        }
        if (isParameterNode()) {
            return false;
        }
        Map<String, String> exported = parameter.exportParameterValues();
        if (exported.isEmpty()) {
            return false;
        }

        Map<String, String> existing = exportParameterValues();
        resetParametersToDefaults();
        applyParameterValuesFromMap(existing);

        boolean applied = applyParameterValuesFromMap(exported);
        if (!applied) {
            applyParameterValuesFromMap(existing);
        }
        return applied;
    }

    private boolean applyParameterValuesFromMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean applied = false;
        for (NodeParameter target : parameters) {
            String key = target.getName();
            String value = values.get(key);
            if (value == null) {
                value = values.get(normalizeParameterKey(key));
            }
            if (value == null) {
                value = values.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null && "Resource".equalsIgnoreCase(key)) {
                value = values.get("Block");
                if (value == null) {
                    value = values.get(normalizeParameterKey("Block"));
                }
                if (value == null) {
                    value = values.get("Blocks");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Blocks"));
                }
                if (value == null) {
                    value = values.get("Item");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Item"));
                }
                if (value == null) {
                    value = values.get("Entity");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Entity"));
                }
                if (value == null) {
                    value = values.get("Player");
                }
                if (value == null) {
                    value = values.get(normalizeParameterKey("Player"));
                }
            }
            if (value != null) {
                target.setStringValue(value);
                applied = true;
            }
        }
        return applied;
    }

    private boolean canApplyParameterValues(Node parameter) {
        if (parameter == null || parameter.getParameters().isEmpty() || this.parameters.isEmpty()) {
            return false;
        }
        Map<String, String> exported = parameter.exportParameterValues();
        if (exported.isEmpty()) {
            return false;
        }
        for (NodeParameter target : this.parameters) {
            String key = target.getName();
            if (exported.containsKey(key)
                || exported.containsKey(normalizeParameterKey(key))
                || exported.containsKey(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void refreshAttachedParameterValues() {
        if (isParameterNode()) {
            return;
        }
        resetParametersToDefaults();
        if (attachedParameters.isEmpty()) {
            return;
        }
        List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachedParameters.get(slotIndex);
            if (parameter == null) {
                continue;
            }
            Map<String, String> exported = parameter.exportParameterValues();
            if (!exported.isEmpty()) {
                applyParameterValuesFromMap(exported);
            }
        }
    }

    private boolean canHandleParameterRuntime(Node parameter) {
        return parameter != null && parameter.isParameterNode();
    }

    public boolean canAcceptActionNode(Node node) {
        if (!canAcceptActionNode() || node == null || node == this || node.isSensorNode()) {
            return false;
        }
        if (node.getType() == NodeType.EVENT_FUNCTION) {
            return false;
        }
        if (attachedActionNode != null && attachedActionNode != node) {
            return false;
        }
        return true;
    }

    public boolean attachActionNode(Node node) {
        if (!canAcceptActionNode(node)) {
            return false;
        }

        if (node.parentActionControl == this && attachedActionNode == node) {
            updateAttachedActionPosition();
            return true;
        }

        if (node.parentActionControl != null) {
            node.parentActionControl.detachActionNode();
        }

        if (attachedActionNode != null && attachedActionNode != node) {
            Node previous = attachedActionNode;
            previous.parentActionControl = null;
            previous.setDragging(false);
            previous.setSelected(false);
            previous.setPositionSilently(this.x + this.width + ACTION_SLOT_MARGIN_HORIZONTAL, this.y);
        }

        attachedActionNode = node;
        node.parentActionControl = this;
        node.setDragging(false);
        node.setSelected(false);
        node.setSocketsHidden(true);

        recalculateDimensions();
        updateAttachedActionPosition();
        return true;
    }

    public void detachActionNode() {
        if (attachedActionNode != null) {
            Node node = attachedActionNode;
            node.parentActionControl = null;
            node.setSocketsHidden(false);
            attachedActionNode = null;
            recalculateDimensions();
        }
    }

    public void setSocketsHidden(boolean hidden) {
        this.socketsHidden = hidden;
    }

    public boolean shouldRenderSockets() {
        return !socketsHidden;
    }

    /**
     * Initialize default parameters for each node type and mode
     */
    private void resetParametersToDefaults() {
        if (isParameterNode()) {
            return;
        }
        parameters.clear();
        initializeParameters();
    }

    private void initializeParameters() {
        // Handle generalized nodes with modes
        if (mode != null) {
            switch (mode) {
                // GOTO modes
                case GOTO_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOTO_XZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOTO_Y:
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                    break;
                case GOTO_BLOCK:
                    parameters.add(new NodeParameter("Block", ParameterType.STRING, "stone"));
                    break;
                // GOAL modes
                case GOAL_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOAL_XZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case GOAL_Y:
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                    break;
                case GOAL_CURRENT:
                case GOAL_CLEAR:
                    // No parameters needed
                    break;
                    
                // COLLECT modes
                case COLLECT_SINGLE:
                    parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "minecraft:stone"));
                    parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "1"));
                    break;
                case COLLECT_MULTIPLE:
                    parameters.add(new NodeParameter("Blocks", ParameterType.STRING, "stone,dirt"));
                    break;
                    
                // BUILD modes
                case BUILD_PLAYER:
                    parameters.add(new NodeParameter("Schematic", ParameterType.STRING, "house.schematic"));
                    break;
                case BUILD_XYZ:
                    parameters.add(new NodeParameter("Schematic", ParameterType.STRING, "house.schematic"));
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                    
                // EXPLORE modes
                case EXPLORE_CURRENT:
                    // No parameters needed
                    break;
                case EXPLORE_XYZ:
                    parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                    parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                    break;
                case EXPLORE_FILTER:
                    parameters.add(new NodeParameter("Filter", ParameterType.STRING, "explore.txt"));
                    break;
                    
                // FOLLOW modes
                case FOLLOW_PLAYER:
                    parameters.add(new NodeParameter("Player", ParameterType.STRING, "PlayerName"));
                    break;
                case FOLLOW_PLAYERS:
                case FOLLOW_ENTITIES:
                    // No parameters needed
                    break;
                case FOLLOW_ENTITY_TYPE:
                    parameters.add(new NodeParameter("Entity", ParameterType.STRING, "cow"));
                    break;

                // CRAFT modes
                case CRAFT_PLAYER_GUI:
                case CRAFT_CRAFTING_TABLE:
                    parameters.add(new NodeParameter("Item", ParameterType.STRING, "stick"));
                    parameters.add(new NodeParameter("Quantity", ParameterType.INTEGER, "1"));
                    break;

                // FARM modes
                case FARM_RANGE:
                    parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                    break;
                case FARM_WAYPOINT:
                    parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "farm"));
                    parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                    break;
                    
                // STOP modes
                case STOP_NORMAL:
                case STOP_CANCEL:
                case STOP_FORCE:
                    // No parameters needed
                    break;
                    
                default:
                    // No parameters needed
                    break;
            }
            return;
        }
        
        // Handle node types that don't use modes
        switch (type) {
            case PLACE:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case WAIT:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                parameters.add(new NodeParameter("MinimumDurationSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("RandomVarianceSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case MESSAGE:
                parameters.add(new NodeParameter("Text", ParameterType.STRING, "Hello World"));
                break;
            case HOTBAR:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                break;
            case DROP_ITEM:
                parameters.add(new NodeParameter("All", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("IntervalSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case DROP_SLOT:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("EntireStack", ParameterType.BOOLEAN, "true"));
                break;
            case MOVE_ITEM:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("TargetSlot", ParameterType.INTEGER, "9"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "0"));
                break;
            case SWAP_SLOTS:
                parameters.add(new NodeParameter("FirstSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("SecondSlot", ParameterType.INTEGER, "9"));
                break;
            case CLEAR_SLOT:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("DropItems", ParameterType.BOOLEAN, "false"));
                break;
            case EQUIP_ARMOR:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("ArmorSlot", ParameterType.STRING, "head"));
                break;
            case UNEQUIP_ARMOR:
                parameters.add(new NodeParameter("ArmorSlot", ParameterType.STRING, "head"));
                parameters.add(new NodeParameter("TargetSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("DropIfFull", ParameterType.BOOLEAN, "true"));
                break;
            case EQUIP_HAND:
                parameters.add(new NodeParameter("SourceSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                break;
            case UNEQUIP_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("TargetSlot", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("DropIfFull", ParameterType.BOOLEAN, "true"));
                break;
            case USE:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("UseDurationSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("RepeatCount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("UseIntervalSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("StopIfUnavailable", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("UseUntilEmpty", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("AllowBlockInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AllowEntityInteraction", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingAfterUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileUsing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case INTERACT:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, ""));
                parameters.add(new NodeParameter("PreferEntity", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("PreferBlock", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("FallbackToItemUse", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SwingOnSuccess", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("SneakWhileInteracting", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case PLACE_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("SneakWhilePlacing", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("SwingOnPlace", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RequireBlockHit", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case ATTACK:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                parameters.add(new NodeParameter("SwingOnly", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("AttackEntities", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AttackBlocks", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("RepeatCount", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("AttackIntervalSeconds", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("SneakWhileAttacking", ParameterType.BOOLEAN, "false"));
                parameters.add(new NodeParameter("RestoreSneakState", ParameterType.BOOLEAN, "true"));
                break;
            case LOOK:
                parameters.add(new NodeParameter("Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Pitch", ParameterType.DOUBLE, "0.0"));
                break;
            case TURN:
                parameters.add(new NodeParameter("YawOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("PitchOffset", ParameterType.DOUBLE, "0.0"));
                break;
            case JUMP:
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "1"));
                parameters.add(new NodeParameter("IntervalSeconds", ParameterType.DOUBLE, "0.0"));
                break;
            case CROUCH:
                parameters.add(new NodeParameter("Active", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("ToggleKey", ParameterType.BOOLEAN, "false"));
                break;
            case SPRINT:
                parameters.add(new NodeParameter("Active", ParameterType.BOOLEAN, "true"));
                parameters.add(new NodeParameter("AllowFlying", ParameterType.BOOLEAN, "false"));
                break;
            case CONTROL_REPEAT:
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "10"));
                break;
            case CONTROL_IF:
                break;
            case CONTROL_IF_ELSE:
                break;
            case EVENT_FUNCTION:
                parameters.add(new NodeParameter("Name", ParameterType.STRING, "function"));
                break;
            case EVENT_CALL:
                parameters.add(new NodeParameter("Name", ParameterType.STRING, "function"));
                break;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_BLOCK_BELOW:
            case SENSOR_LIGHT_LEVEL_BELOW:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ENTITY_NEARBY:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_FALLING:
                break;
            case SENSOR_IS_RENDERED:
                parameters.add(new NodeParameter("Resource", ParameterType.STRING, "minecraft:stone"));
                break;
            case PARAM_COORDINATE:
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "64"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_BLOCK:
                parameters.add(new NodeParameter("Block", ParameterType.STRING, "minecraft:stone"));
                break;
            case PARAM_BLOCK_LIST:
                parameters.add(new NodeParameter("Blocks", ParameterType.STRING, "minecraft:stone,minecraft:dirt"));
                break;
            case PARAM_ITEM:
                parameters.add(new NodeParameter("Item", ParameterType.STRING, "minecraft:stick"));
                parameters.add(new NodeParameter("Count", ParameterType.INTEGER, "1"));
                break;
            case PARAM_ENTITY:
                parameters.add(new NodeParameter("Entity", ParameterType.STRING, "minecraft:cow"));
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "6"));
                break;
            case PARAM_PLAYER:
                parameters.add(new NodeParameter("Player", ParameterType.STRING, "PlayerName"));
                break;
            case PARAM_WAYPOINT:
                parameters.add(new NodeParameter("Waypoint", ParameterType.STRING, "home"));
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "10"));
                break;
            case PARAM_SCHEMATIC:
                parameters.add(new NodeParameter("Schematic", ParameterType.STRING, "structure.schematic"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_INVENTORY_SLOT:
                parameters.add(new NodeParameter("Slot", ParameterType.INTEGER, "0"));
                break;
            case PARAM_MESSAGE:
                parameters.add(new NodeParameter("Text", ParameterType.STRING, "Hello"));
                break;
            case PARAM_DURATION:
                parameters.add(new NodeParameter("Duration", ParameterType.DOUBLE, "1.0"));
                break;
            case PARAM_BOOLEAN:
                parameters.add(new NodeParameter("Toggle", ParameterType.BOOLEAN, "true"));
                break;
            case PARAM_HAND:
                parameters.add(new NodeParameter("Hand", ParameterType.STRING, "main"));
                break;
            case PARAM_RANGE:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "6"));
                break;
            case PARAM_ROTATION:
                parameters.add(new NodeParameter("Yaw", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("Pitch", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("YawOffset", ParameterType.DOUBLE, "0.0"));
                parameters.add(new NodeParameter("PitchOffset", ParameterType.DOUBLE, "0.0"));
                break;
            case PARAM_PLACE_TARGET:
                parameters.add(new NodeParameter("Block", ParameterType.BLOCK_TYPE, "minecraft:stone"));
                parameters.add(new NodeParameter("X", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Y", ParameterType.INTEGER, "0"));
                parameters.add(new NodeParameter("Z", ParameterType.INTEGER, "0"));
                break;
            case PARAM_CLOSEST:
                parameters.add(new NodeParameter("Range", ParameterType.INTEGER, "5"));
                parameters.add(new NodeParameter("RequireSolidGround", ParameterType.BOOLEAN, "true"));
                break;
            case PARAM_LIGHT_THRESHOLD:
                parameters.add(new NodeParameter("Threshold", ParameterType.INTEGER, "7"));
                break;
            case PARAM_HEALTH_THRESHOLD:
                parameters.add(new NodeParameter("Amount", ParameterType.DOUBLE, "10.0"));
                break;
            case PARAM_HUNGER_THRESHOLD:
                parameters.add(new NodeParameter("Amount", ParameterType.INTEGER, "10"));
                break;
            case PARAM_FALL_DISTANCE:
                parameters.add(new NodeParameter("Distance", ParameterType.DOUBLE, "2.0"));
                break;
            default:
                // No parameters needed
                break;
        }
    }

    /**
     * Get all parameters for this node
     */
    public List<NodeParameter> getParameters() {
        return parameters;
    }

    /**
     * Get a specific parameter by name
     */
    public NodeParameter getParameter(String name) {
        for (NodeParameter param : parameters) {
            if (param.getName().equals(name)) {
                return param;
            }
        }
        return null;
    }

    public void setParameterValueAndPropagate(String name, String value) {
        if (name == null || value == null) {
            return;
        }

        NodeParameter parameter = getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }

        if (!attachedParameters.isEmpty()) {
            for (Node parameterNode : attachedParameters.values()) {
                if (parameterNode == null || !parameterNode.isParameterNode()) {
                    continue;
                }
                NodeParameter attachedParam = parameterNode.getParameter(name);
                if (attachedParam != null) {
                    attachedParam.setStringValue(value);
                    parameterNode.recalculateDimensions();
                }
            }
        }
    }

    public String getParameterLabel(NodeParameter parameter) {
        if (parameter == null) {
            return "";
        }
        String text = parameter.getName() + ": " + parameter.getDisplayValue();
        if (text.length() <= MAX_PARAMETER_LABEL_LENGTH) {
            return text;
        }
        int maxContentLength = Math.max(0, MAX_PARAMETER_LABEL_LENGTH - 3);
        return text.substring(0, maxContentLength) + "...";
    }

    public Map<String, String> exportParameterValues() {
        Map<String, String> values = new HashMap<>();
        for (NodeParameter parameter : parameters) {
            String key = parameter.getName();
            String value = parameter.getStringValue();
            values.put(key, value);
            values.put(normalizeParameterKey(key), value);
        }

        switch (type) {
            case PARAM_BLOCK_LIST: {
                String list = values.get("Blocks");
                if (list != null) {
                    values.put("Block", list);
                    values.put(normalizeParameterKey("Block"), list);
                }
                break;
            }
            case PARAM_DURATION: {
                String duration = values.get("Duration");
                if (duration != null) {
                    values.put("IntervalSeconds", duration);
                    values.put(normalizeParameterKey("IntervalSeconds"), duration);
                    values.put("WaitSeconds", duration);
                    values.put(normalizeParameterKey("WaitSeconds"), duration);
                }
                break;
            }
            case PARAM_ITEM: {
                String count = values.get("Count");
                if (count != null) {
                    values.put("Quantity", count);
                    values.put(normalizeParameterKey("Quantity"), count);
                }
                break;
            }
            case PARAM_INVENTORY_SLOT: {
                String slot = values.get("Slot");
                if (slot != null) {
                    values.put("SourceSlot", slot);
                    values.put(normalizeParameterKey("SourceSlot"), slot);
                    values.put("TargetSlot", slot);
                    values.put(normalizeParameterKey("TargetSlot"), slot);
                    values.put("FirstSlot", slot);
                    values.put(normalizeParameterKey("FirstSlot"), slot);
                    values.put("SecondSlot", slot);
                    values.put(normalizeParameterKey("SecondSlot"), slot);
                }
                break;
            }
            case PARAM_PLAYER: {
                String player = values.get("Player");
                if (player != null) {
                    values.put("Name", player);
                    values.put(normalizeParameterKey("Name"), player);
                }
                break;
            }
            case PARAM_WAYPOINT: {
                String waypoint = values.get("Waypoint");
                if (waypoint != null) {
                    values.put("Name", waypoint);
                    values.put(normalizeParameterKey("Name"), waypoint);
                }
                break;
            }
            case PARAM_BOOLEAN: {
                String toggle = values.get("Toggle");
                if (toggle == null) {
                    toggle = values.get(normalizeParameterKey("Toggle"));
                }
                if (toggle != null) {
                    values.put("Active", toggle);
                    values.put(normalizeParameterKey("Active"), toggle);
                    values.put("Enabled", toggle);
                    values.put(normalizeParameterKey("Enabled"), toggle);
                }
                break;
            }
            case PARAM_BLOCK: {
                String block = values.get("Block");
                if (block != null) {
                    values.put("Blocks", block);
                    values.put(normalizeParameterKey("Blocks"), block);
                }
                break;
            }
            case PARAM_MESSAGE: {
                String text = values.get("Text");
                if (text != null) {
                    values.put("Message", text);
                    values.put(normalizeParameterKey("Message"), text);
                }
                break;
            }
            case PARAM_ENTITY: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                }
                break;
            }
            case PARAM_HAND: {
                String hand = values.get("Hand");
                if (hand != null) {
                    values.put("SourceHand", hand);
                    values.put(normalizeParameterKey("SourceHand"), hand);
                    values.put("TargetHand", hand);
                    values.put(normalizeParameterKey("TargetHand"), hand);
                    values.put("SelectedHand", hand);
                    values.put(normalizeParameterKey("SelectedHand"), hand);
                }
                break;
            }
            case PARAM_RANGE: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                    values.put("Radius", range);
                    values.put(normalizeParameterKey("Radius"), range);
                }
                break;
            }
            case PARAM_PLACE_TARGET: {
                String blockId = values.get("Block");
                if (blockId != null) {
                    values.put("BlockId", blockId);
                    values.put(normalizeParameterKey("BlockId"), blockId);
                }
                break;
            }
            case PARAM_CLOSEST: {
                String range = values.get("Range");
                if (range != null) {
                    values.put("Distance", range);
                    values.put(normalizeParameterKey("Distance"), range);
                }
                break;
            }
            case PARAM_ROTATION: {
                String yaw = values.get("Yaw");
                if (yaw != null) {
                    values.put("YawOffset", yaw);
                    values.put(normalizeParameterKey("YawOffset"), yaw);
                }
                String pitch = values.get("Pitch");
                if (pitch != null) {
                    values.put("PitchOffset", pitch);
                    values.put(normalizeParameterKey("PitchOffset"), pitch);
                }
                break;
            }
            default:
                break;
        }

        return values;
    }

    /**
     * Check if this node has parameters (Start nodes don't)
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    public boolean supportsModeSelection() {
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        return modes != null && modes.length > 0;
    }

    /**
     * Recalculate node dimensions based on current content
     */
    public void recalculateDimensions() {
        if (type == NodeType.START) {
            this.width = START_END_SIZE;
            this.height = START_END_SIZE;
            return;
        }

        int maxTextLength = Math.max(type.getDisplayName().length(), 1);
        if (isParameterNode()) {
            for (NodeParameter param : parameters) {
                String paramText = getParameterLabel(param);
                if (paramText.length() > maxTextLength) {
                    maxTextLength = paramText.length();
                }
            }

            if (supportsModeSelection()) {
                String modeLabel = getModeDisplayLabel();
                if (!modeLabel.isEmpty()) {
                    maxTextLength = Math.max(maxTextLength, modeLabel.length());
                }
            }
        }

        int computedWidth = maxTextLength * CHAR_PIXEL_WIDTH + 24; // padding and border allowance
        if (hasParameterSlot()) {
            int parameterContentWidth = PARAMETER_SLOT_MIN_CONTENT_WIDTH;
            if (!attachedParameters.isEmpty()) {
                for (Node parameterNode : attachedParameters.values()) {
                    if (parameterNode != null) {
                        parameterContentWidth = Math.max(parameterContentWidth, parameterNode.getWidth());
                    }
                }
            }
            int requiredWidth = parameterContentWidth + 2 * (PARAMETER_SLOT_INNER_PADDING + PARAMETER_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
            if (hasCoordinateInputFields()) {
                int coordinateWidth = getCoordinateFieldTotalWidth() + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
                computedWidth = Math.max(computedWidth, coordinateWidth);
            }
            if (hasAmountInputField()) {
                int amountWidth = PARAMETER_SLOT_MIN_CONTENT_WIDTH + 2 * PARAMETER_SLOT_MARGIN_HORIZONTAL;
                computedWidth = Math.max(computedWidth, amountWidth);
            }
        }
        if (hasSensorSlot()) {
            int sensorContentWidth = SENSOR_SLOT_MIN_CONTENT_WIDTH;
            if (attachedSensor != null) {
                sensorContentWidth = Math.max(sensorContentWidth, attachedSensor.getWidth());
            }
            int requiredWidth = sensorContentWidth + 2 * (SENSOR_SLOT_INNER_PADDING + SENSOR_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        if (hasActionSlot()) {
            int actionContentWidth = ACTION_SLOT_MIN_CONTENT_WIDTH;
            if (attachedActionNode != null) {
                actionContentWidth = Math.max(actionContentWidth, attachedActionNode.getWidth());
            }
            int requiredWidth = actionContentWidth + 2 * (ACTION_SLOT_INNER_PADDING + ACTION_SLOT_MARGIN_HORIZONTAL);
            computedWidth = Math.max(computedWidth, requiredWidth);
        }
        this.width = Math.max(MIN_WIDTH, computedWidth);

        int contentHeight = HEADER_HEIGHT;
        boolean hasSlots = hasSensorSlot() || hasActionSlot();

        if (isParameterNode()) {
            int parameterLineCount = parameters.size();
            if (supportsModeSelection()) {
                parameterLineCount++;
            }

            if (parameterLineCount > 0) {
                contentHeight += PARAM_PADDING_TOP + (parameterLineCount * PARAM_LINE_HEIGHT) + PARAM_PADDING_BOTTOM;
                if (hasSlots) {
                    contentHeight += SLOT_AREA_PADDING_TOP;
                }
            } else if (hasSlots) {
                contentHeight += SLOT_AREA_PADDING_TOP;
            } else {
                contentHeight += BODY_PADDING_NO_PARAMS;
            }
        } else if (hasParameterSlot()) {
            int slotCount = getParameterSlotCount();
            for (int i = 0; i < slotCount; i++) {
                contentHeight += PARAMETER_SLOT_LABEL_HEIGHT + getParameterSlotHeight(i) + PARAMETER_SLOT_BOTTOM_PADDING;
            }
            if (hasCoordinateInputFields()) {
                contentHeight += getCoordinateFieldDisplayHeight();
            }
            if (hasAmountInputField()) {
                contentHeight += getAmountFieldDisplayHeight();
            }
            if (hasSlots) {
                contentHeight += SLOT_AREA_PADDING_TOP;
            }
        } else if (hasSlots) {
            contentHeight += SLOT_AREA_PADDING_TOP;
        } else {
            contentHeight += BODY_PADDING_NO_PARAMS;
        }

        if (hasSensorSlot()) {
            contentHeight += getSensorSlotHeight();
        }

        if (hasActionSlot()) {
            if (hasSensorSlot()) {
                contentHeight += SLOT_VERTICAL_SPACING;
            }
            contentHeight += getActionSlotHeight();
        }

        if (hasSlots) {
            contentHeight += SLOT_AREA_PADDING_BOTTOM;
        }

        int computedHeight = Math.max(MIN_HEIGHT, contentHeight);
        if (type == NodeType.EVENT_FUNCTION) {
            this.height = Math.max(EVENT_FUNCTION_MIN_HEIGHT, contentHeight);
        } else {
            this.height = computedHeight;
        }

        // Function nodes used to be forced into a square layout. That made them as tall
        // as they were wide and left a large amount of empty space around their input
        // field. We now keep them compact by clamping their height to the minimal
        // content they need instead of expanding to match their width.

        if (attachedSensor != null) {
            updateAttachedSensorPosition();
        }
        if (attachedActionNode != null) {
            updateAttachedActionPosition();
        }
        updateAttachedParameterPositions();
    }

    /**
     * Get the height needed to display parameters
     */
    public int getParameterDisplayHeight() {
        if (!hasParameters() && !supportsModeSelection()) {
            return 0;
        }
        int parameterLineCount = parameters.size();
        if (supportsModeSelection()) {
            parameterLineCount++;
        }
        return PARAM_PADDING_TOP + parameterLineCount * PARAM_LINE_HEIGHT + PARAM_PADDING_BOTTOM;
    }

    public String getModeDisplayLabel() {
        if (!supportsModeSelection()) {
            return "";
        }
        NodeMode nodeMode = getMode();
        String modeName = nodeMode != null ? nodeMode.getDisplayName() : "Select Mode";
        return "Mode: " + modeName;
    }

    /**
     * Execute this node asynchronously.
     * Returns a CompletableFuture that completes when the node's command is finished.
     */
    public CompletableFuture<Void> execute() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Execute on the main Minecraft thread
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (hasParameterSlot()) {
            int requiredSlotCount = getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (isParameterSlotRequired(i) && getAttachedParameter(i) == null) {
                    String label = getParameterSlotLabel(i);
                    sendNodeErrorMessage(client, type.getDisplayName() + " requires a " + label.toLowerCase(Locale.ROOT) + " parameter before it can run.");
                    future.complete(null);
                    return future;
                }
            }
        }

        if (client != null) {
            client.execute(() -> {
                try {
                    executeNodeCommand(future);
                } catch (Exception e) {
                    System.err.println("Error executing node " + type + ": " + e.getMessage());
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
        } else {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
        }

        return future;
    }

    private ParameterHandlingResult preprocessAttachedParameter(EnumSet<ParameterUsage> usages, CompletableFuture<Void> future) {
        return preprocessParameterSlot(0, usages, future, true);
    }

    private ParameterHandlingResult preprocessParameterSlot(int slotIndex, EnumSet<ParameterUsage> usages, CompletableFuture<Void> future, boolean resetRuntimeData) {
        if (!canAcceptParameterAt(slotIndex)) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (resetRuntimeData) {
            runtimeParameterData = null;
        }
        Node parameterNode = getAttachedParameter(slotIndex);
        return preprocessParameterNode(parameterNode, usages, future);
    }

    private ParameterHandlingResult preprocessParameterNode(Node parameterNode, EnumSet<ParameterUsage> usages, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return ParameterHandlingResult.CONTINUE;
        }
        if (runtimeParameterData == null) {
            runtimeParameterData = new RuntimeParameterData();
        }

        boolean handled = false;

        Map<String, String> exported = parameterNode.exportParameterValues();
        if (!exported.isEmpty()) {
            handled = applyParameterValuesFromMap(exported);
        }

        if (usages.contains(ParameterUsage.POSITION)) {
            Optional<Vec3d> targetVec = resolvePositionTarget(parameterNode, runtimeParameterData, future);
            if (targetVec.isPresent()) {
                handled = true;
                runtimeParameterData.targetVector = targetVec.get();
                applyVectorToCoordinateParameters(targetVec.get());
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(ParameterUsage.LOOK_ORIENTATION)) {
            boolean oriented = resolveLookOrientation(parameterNode, runtimeParameterData, future);
            if (oriented) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(ParameterUsage.TURN_OFFSET)) {
            boolean offsets = resolveTurnOffsets(parameterNode, runtimeParameterData, future);
            if (offsets) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return ParameterHandlingResult.COMPLETE;
            }
        }

        if (!handled) {
            if (future != null && !future.isDone()) {
                sendIncompatibleParameterMessage(parameterNode);
                future.complete(null);
            }
            return ParameterHandlingResult.COMPLETE;
        }

        return ParameterHandlingResult.CONTINUE;
    }

    private Optional<Vec3d> resolvePositionTarget(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        if (data != null && data.targetVector != null) {
            return Optional.of(data.targetVector);
        }

        NodeType parameterType = parameterNode.getType();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        switch (parameterType) {
            case PARAM_COORDINATE: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_SCHEMATIC: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                    data.schematicName = getParameterString(parameterNode, "Schematic");
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_PLACE_TARGET: {
                int x = parseNodeInt(parameterNode, "X", 0);
                int y = parseNodeInt(parameterNode, "Y", 0);
                int z = parseNodeInt(parameterNode, "Z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                if (data != null) {
                    data.targetBlockPos = pos;
                    data.targetBlockId = getParameterString(parameterNode, "Block");
                }
                return Optional.of(Vec3d.ofCenter(pos));
            }
            case PARAM_ITEM: {
                if (client == null || client.player == null) {
                    return Optional.empty();
                }
                String itemId = getParameterString(parameterNode, "Item");
                if (itemId == null || itemId.isEmpty()) {
                    sendParameterSearchFailure("No item selected on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                Identifier identifier = Identifier.tryParse(itemId);
                if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                    sendParameterSearchFailure("Unknown item \"" + itemId + "\" for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                Item item = Registries.ITEM.get(identifier);
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                Optional<BlockPos> match = findNearestDroppedItem(client, item, range);
                if (match.isEmpty()) {
                    sendParameterSearchFailure("No dropped " + itemId + " found for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetBlockPos = match.get();
                    data.targetItem = item;
                    data.targetItemId = itemId;
                }
                return Optional.of(Vec3d.ofCenter(match.get()));
            }
            case PARAM_ENTITY: {
                if (client == null || client.player == null) {
                    return Optional.empty();
                }
                String entityId = getParameterString(parameterNode, "Entity");
                if (entityId == null || entityId.isEmpty()) {
                    sendParameterSearchFailure("No entity selected on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                Identifier identifier = Identifier.tryParse(entityId);
                if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                    sendParameterSearchFailure("Unknown entity \"" + entityId + "\" for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                Optional<Entity> entity = findNearestEntity(client, entityType, range);
                if (entity.isEmpty()) {
                    sendParameterSearchFailure("No nearby entity of type " + entityId + " for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetEntity = entity.get();
                    data.targetEntityId = entityId;
                    data.targetBlockPos = entity.get().getBlockPos();
                }
                return Optional.of(entity.get().getPos());
            }
            case PARAM_PLAYER: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                String playerName = getParameterString(parameterNode, "Player");
                if (playerName == null || playerName.isEmpty()) {
                    sendParameterSearchFailure("No player selected on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                Optional<AbstractClientPlayerEntity> player = client.world.getPlayers().stream()
                    .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(playerName))
                    .findFirst();
                if (player.isEmpty()) {
                    sendParameterSearchFailure("Player \"" + playerName + "\" is not nearby for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetPlayerName = playerName;
                    data.targetBlockPos = player.get().getBlockPos();
                }
                return Optional.of(player.get().getPos());
            }
            case PARAM_BLOCK:
            case PARAM_BLOCK_LIST: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                List<Block> blocks = resolveBlocksFromParameter(parameterNode);
                if (blocks.isEmpty()) {
                    sendParameterSearchFailure("No blocks defined on parameter for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                double range = parseNodeDouble(parameterNode, "Range", PARAMETER_SEARCH_RADIUS);
                Optional<BlockPos> match = findNearestBlock(client, blocks, range);
                if (match.isEmpty()) {
                    sendParameterSearchFailure("No matching block from parameter found for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetBlockPos = match.get();
                    data.targetBlockIds = new ArrayList<>();
                    for (Block block : blocks) {
                        Identifier id = Registries.BLOCK.getId(block);
                        if (id != null) {
                            data.targetBlockIds.add(id.toString());
                        }
                    }
                }
                return Optional.of(Vec3d.ofCenter(match.get()));
            }
            default: {
                String xValue = getParameterString(parameterNode, "X");
                String yValue = getParameterString(parameterNode, "Y");
                String zValue = getParameterString(parameterNode, "Z");
                if (xValue != null && yValue != null && zValue != null) {
                    try {
                        int x = Integer.parseInt(xValue.trim());
                        int y = Integer.parseInt(yValue.trim());
                        int z = Integer.parseInt(zValue.trim());
                        BlockPos pos = new BlockPos(x, y, z);
                        if (data != null) {
                            data.targetBlockPos = pos;
                        }
                        return Optional.of(Vec3d.ofCenter(pos));
                    } catch (NumberFormatException ignored) {
                        // fall through to empty optional
                    }
                }
                break;
            }
            case PARAM_CLOSEST: {
                if (client == null || client.player == null || client.world == null) {
                    return Optional.empty();
                }
                int range = Math.max(1, parseNodeInt(parameterNode, "Range", 5));
                String requireSolidGroundValue = getParameterString(parameterNode, "RequireSolidGround");
                boolean requireSolidGround = requireSolidGroundValue == null || Boolean.parseBoolean(requireSolidGroundValue);
                Optional<BlockPos> open = findNearestOpenBlock(client, range, requireSolidGround);
                if (open.isEmpty()) {
                    sendParameterSearchFailure("No open block found within range for " + type.getDisplayName() + ".", future);
                    return Optional.empty();
                }
                if (data != null) {
                    data.targetBlockPos = open.get();
                }
                return Optional.of(Vec3d.ofCenter(open.get()));
            }
        }

        return Optional.empty();
    }

    private void applyVectorToCoordinateParameters(Vec3d targetVec) {
        if (targetVec == null) {
            return;
        }
        int x = MathHelper.floor(targetVec.x);
        int y = MathHelper.floor(targetVec.y);
        int z = MathHelper.floor(targetVec.z);
        if (runtimeParameterData != null) {
            runtimeParameterData.targetBlockPos = new BlockPos(x, y, z);
        }
        setParameterValueAndPropagate("X", Integer.toString(x));
        setParameterValueAndPropagate("Y", Integer.toString(y));
        setParameterValueAndPropagate("Z", Integer.toString(z));
    }

    private boolean isPlayerAtCoordinates(Integer targetX, Integer targetY, Integer targetZ) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        if (targetX != null && playerPos.getX() != targetX) {
            return false;
        }
        if (targetY != null && playerPos.getY() != targetY) {
            return false;
        }
        if (targetZ != null && playerPos.getZ() != targetZ) {
            return false;
        }
        return true;
    }

    private boolean resolveLookOrientation(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        Float yawParam = parseNodeFloat(parameterNode, "Yaw");
        Float pitchParam = parseNodeFloat(parameterNode, "Pitch");
        if (yawParam != null || pitchParam != null) {
            if (yawParam != null) {
                setParameterIfPresent("Yaw", formatFloat(yawParam));
                if (data != null) {
                    data.resolvedYaw = yawParam;
                }
            }
            if (pitchParam != null) {
                float clamped = MathHelper.clamp(pitchParam, -90.0F, 90.0F);
                setParameterIfPresent("Pitch", formatFloat(clamped));
                if (data != null) {
                    data.resolvedPitch = clamped;
                }
            }
            return true;
        }

        Vec3d target = data != null ? data.targetVector : null;
        if (target == null) {
            Optional<Vec3d> resolved = resolvePositionTarget(parameterNode, data, future);
            if (resolved.isEmpty()) {
                return false;
            }
            target = resolved.get();
        }

        Vec3d eyes = client.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        if (delta.lengthSquared() < 1.0E-6) {
            return false;
        }
        float yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        float clampedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        setParameterIfPresent("Yaw", formatFloat(yaw));
        setParameterIfPresent("Pitch", formatFloat(clampedPitch));

        if (data != null) {
            data.resolvedYaw = yaw;
            data.resolvedPitch = clampedPitch;
        }
        return true;
    }

    private boolean resolveTurnOffsets(Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        Float yawOffset = parseNodeFloat(parameterNode, "YawOffset");
        Float pitchOffset = parseNodeFloat(parameterNode, "PitchOffset");
        if (yawOffset != null || pitchOffset != null) {
            if (yawOffset != null) {
                setParameterIfPresent("YawOffset", formatFloat(yawOffset));
                if (data != null) {
                    data.resolvedYawOffset = yawOffset;
                }
            }
            if (pitchOffset != null) {
                float clamped = MathHelper.clamp(pitchOffset, -180.0F, 180.0F);
                setParameterIfPresent("PitchOffset", formatFloat(clamped));
                if (data != null) {
                    data.resolvedPitchOffset = clamped;
                }
            }
            return true;
        }

        Vec3d target = data != null ? data.targetVector : null;
        if (target == null) {
            Optional<Vec3d> resolved = resolvePositionTarget(parameterNode, data, future);
            if (resolved.isEmpty()) {
                return false;
            }
            target = resolved.get();
        }

        Vec3d eyes = client.player.getEyePos();
        Vec3d delta = target.subtract(eyes);
        if (delta.lengthSquared() < 1.0E-6) {
            return false;
        }
        float targetYaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        float yawOffsetComputed = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        float pitchOffsetComputed = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        setParameterIfPresent("YawOffset", formatFloat(yawOffsetComputed));
        setParameterIfPresent("PitchOffset", formatFloat(pitchOffsetComputed));

        if (data != null) {
            data.resolvedYawOffset = yawOffsetComputed;
            data.resolvedPitchOffset = pitchOffsetComputed;
        }
        return true;
    }

    private void orientPlayerTowardsRuntimeTarget(net.minecraft.client.MinecraftClient client, RuntimeParameterData data) {
        if (client == null || client.player == null || data == null) {
            return;
        }

        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        boolean applyYaw = false;
        boolean applyPitch = false;

        Vec3d targetVector = null;
        if (data.targetEntity != null && data.targetEntity.isAlive()) {
            targetVector = data.targetEntity.getBoundingBox().getCenter();
        }
        if (targetVector == null && data.targetVector != null) {
            targetVector = data.targetVector;
        }
        if (targetVector == null && data.targetBlockPos != null) {
            targetVector = Vec3d.ofCenter(data.targetBlockPos);
        }

        if (targetVector != null) {
            Vec3d eyes = client.player.getEyePos();
            Vec3d delta = targetVector.subtract(eyes);
            if (delta.lengthSquared() > 1.0E-6) {
                yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
                pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
                pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
                applyYaw = true;
                applyPitch = true;
            }
        }

        if (!applyYaw && data.resolvedYaw != null) {
            yaw = data.resolvedYaw;
            applyYaw = true;
        }
        if (!applyPitch && data.resolvedPitch != null) {
            pitch = MathHelper.clamp(data.resolvedPitch, -90.0F, 90.0F);
            applyPitch = true;
        }

        if (!applyYaw && !applyPitch) {
            return;
        }

        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);

        if (applyYaw) {
            data.resolvedYaw = yaw;
        }
        if (applyPitch) {
            data.resolvedPitch = pitch;
        }
    }

    private void sendIncompatibleParameterMessage(Node parameterNode) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        if (parameterNode != null && this.type == NodeType.PLACE && parameterNode.getType() == NodeType.PARAM_CLOSEST) {
            return;
        }
        sendNodeErrorMessage(client, "Parameter \"" + parameterNode.getType().getDisplayName() + "\" cannot be used with \"" + this.type.getDisplayName() + "\".");
    }

    private void sendParameterSearchFailure(String message, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, message);
        }
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    private void setParameterIfPresent(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        NodeParameter parameter = getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static int parseNodeInt(Node node, String name, int defaultValue) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseNodeDouble(Node node, String name, double defaultValue) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Float parseNodeFloat(Node node, String name) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Block> resolveBlocksFromParameter(Node parameterNode) {
        List<Block> blocks = new ArrayList<>();
        String primary = getParameterString(parameterNode, "Block");
        String listValue = getParameterString(parameterNode, "Blocks");
        if (listValue != null && !listValue.isEmpty()) {
            for (String entry : listValue.split(",")) {
                addBlockById(blocks, entry.trim());
            }
        }
        if (primary != null && !primary.isEmpty()) {
            addBlockById(blocks, primary.trim());
        }
        return blocks;
    }

    private void addBlockById(List<Block> blocks, String idString) {
        if (idString == null || idString.isEmpty()) {
            return;
        }
        Identifier identifier = Identifier.tryParse(idString);
        if (identifier != null && Registries.BLOCK.containsId(identifier)) {
            blocks.add(Registries.BLOCK.get(identifier));
        }
    }

    private Optional<BlockPos> findNearestBlock(net.minecraft.client.MinecraftClient client, List<Block> blocks, double range) {
        if (client == null || client.player == null || client.world == null || blocks == null || blocks.isEmpty()) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.world.getBlockState(mutable);
                    if (blocks.contains(state.getBlock())) {
                        double distance = mutable.getSquaredDistance(playerPos);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestPos = mutable.toImmutable();
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    private Optional<BlockPos> findNearestOpenBlock(net.minecraft.client.MinecraftClient client, int range, boolean requireSolidGround) {
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min(range, 32));
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!client.world.getWorldBorder().contains(mutable)) {
                        continue;
                    }
                    if (!isBlockReplaceable(client.world, mutable)) {
                        continue;
                    }
                    if (requireSolidGround) {
                        BlockPos below = mutable.down();
                        BlockState belowState = client.world.getBlockState(below);
                        if (!belowState.isSolidBlock(client.world, below)) {
                            continue;
                        }
                    }
                    Box blockBox = new Box(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getY() + 1, mutable.getZ() + 1);
                    if (!client.world.getOtherEntities(null, blockBox).isEmpty()) {
                        continue;
                    }
                    double distance = mutable.getSquaredDistance(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.toImmutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    /**
     * Execute the actual command for this node type.
     * This method should be overridden by specific node implementations if needed.
     */
    private void executeNodeCommand(CompletableFuture<Void> future) {
        switch (type) {
            case START:
                // START node doesn't execute any command, just passes through
                System.out.println("START node - passing through");
                future.complete(null);
                break;
            case EVENT_FUNCTION:
                System.out.println("Function node - awaiting body execution");
                future.complete(null);
                break;
            case EVENT_CALL:
                System.out.println("Call Function node - dispatching handlers");
                future.complete(null);
                break;

            // Generalized nodes
            case GOTO:
                executeGotoCommand(future);
                break;
            case GOAL:
                executeGoalCommand(future);
                break;
            case COLLECT:
                executeCollectCommand(future);
                break;
            case BUILD:
                executeBuildCommand(future);
                break;
            case EXPLORE:
                executeExploreCommand(future);
                break;
            case FOLLOW:
                executeFollowCommand(future);
                break;
            case CONTROL_REPEAT:
                executeControlRepeat(future);
                break;
            case CONTROL_REPEAT_UNTIL:
                executeControlRepeatUntil(future);
                break;
            case CONTROL_FOREVER:
                executeControlForever(future);
                break;
            case CONTROL_IF:
                executeControlIf(future);
                break;
            case CONTROL_IF_ELSE:
                executeControlIfElse(future);
                break;
            case FARM:
                executeFarmCommand(future);
                break;
            case STOP:
                executeStopCommand(future);
                break;
            case PLACE:
                executePlaceCommand(future);
                break;
            case CRAFT:
                executeCraftCommand(future);
                break;
            case OPEN_INVENTORY:
                executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_OPEN);
                break;
            case CLOSE_GUI:
                executePlayerGuiCommand(future, NodeMode.PLAYER_GUI_CLOSE);
                break;
            case SCREEN_CONTROL:
                executeScreenControlCommand(future);
                break;
            case WAIT:
                executeWaitCommand(future);
                break;
            case MESSAGE:
                executeMessageCommand(future);
                break;
            case HOTBAR:
                executeHotbarCommand(future);
                break;
            case DROP_ITEM:
                executeDropItemCommand(future);
                break;
            case DROP_SLOT:
                executeDropSlotCommand(future);
                break;
            case MOVE_ITEM:
                executeMoveItemCommand(future);
                break;
            case SWAP_SLOTS:
                executeSwapSlotsCommand(future);
                break;
            case CLEAR_SLOT:
                executeClearSlotCommand(future);
                break;
            case USE:
                executeUseCommand(future);
                break;
            case PLACE_HAND:
                executePlaceHandCommand(future);
                break;
            case LOOK:
                executeLookCommand(future);
                break;
            case TURN:
                executeTurnCommand(future);
                break;
            case JUMP:
                executeJumpCommand(future);
                break;
            case CROUCH:
                executeCrouchCommand(future);
                break;
            case SPRINT:
                executeSprintCommand(future);
                break;
            case INTERACT:
                executeInteractCommand(future);
                break;
            case ATTACK:
                executeAttackCommand(future);
                break;
            case SWING:
                executeSwingCommand(future);
                break;
            case SWAP_HANDS:
                executeSwapHandsCommand(future);
                break;
            case EQUIP_ARMOR:
                executeEquipArmorCommand(future);
                break;
            case UNEQUIP_ARMOR:
                executeUnequipArmorCommand(future);
                break;
            case EQUIP_HAND:
                executeEquipHandCommand(future);
                break;
            case UNEQUIP_HAND:
                executeUnequipHandCommand(future);
                break;
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_BLOCK_BELOW:
            case SENSOR_LIGHT_LEVEL_BELOW:
            case SENSOR_IS_DAYTIME:
            case SENSOR_IS_RAINING:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ENTITY_NEARBY:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_IS_SWIMMING:
            case SENSOR_IS_IN_LAVA:
            case SENSOR_IS_UNDERWATER:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
                completeSensorEvaluation(future);
                break;
            
            // Legacy nodes
            case PATH:
                executePathCommand(future);
                break;
            case INVERT:
                executeInvertCommand(future);
                break;
            case COME:
                executeComeCommand(future);
                break;
            case SURFACE:
                executeSurfaceCommand(future);
                break;
            case TUNNEL:
                executeTunnelCommand(future);
                break;
                
            default:
                System.out.println("Unknown node type: " + type);
                future.complete(null);
                break;
        }
    }
    
    // Command execution methods that wait for Baritone completion
    private void executeGotoCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for GOTO node"));
            return;
        }

        IBaritone baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for goto command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }

        ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();

        if (tryExecuteGotoUsingAttachedParameter(baritone, customGoalProcess, future)) {
            return;
        }

        switch (mode) {
            case GOTO_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");

                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();

                if (isPlayerAtCoordinates(x, y, z)) {
                    future.complete(null);
                    return;
                }

                System.out.println("Executing goto to: " + x + ", " + y + ", " + z);
                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
                GoalBlock goal = new GoalBlock(x, y, z);
                customGoalProcess.setGoalAndPath(goal);
                break;
                
            case GOTO_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();

                if (isPlayerAtCoordinates(x2, null, z2)) {
                    future.complete(null);
                    return;
                }

                System.out.println("Executing goto to: " + x2 + ", " + z2);
                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
                GoalBlock goal2 = new GoalBlock(x2, 0, z2); // Y will be determined by pathfinding
                customGoalProcess.setGoalAndPath(goal2);
                break;
                
            case GOTO_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();
                
                System.out.println("Executing goto to Y level: " + y3);
                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
                // For Y-only movement, we need to get current X,Z and set goal there
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    if (isPlayerAtCoordinates(null, y3, null)) {
                        future.complete(null);
                        return;
                    }
                    int currentX = (int) client.player.getX();
                    int currentZ = (int) client.player.getZ();
                    GoalBlock goal3 = new GoalBlock(currentX, y3, currentZ);
                    customGoalProcess.setGoalAndPath(goal3);
                }
                break;
                
            case GOTO_BLOCK:
                String block = "stone";
                NodeParameter blockParam = getParameter("Block");
                if (blockParam != null) {
                    block = blockParam.getStringValue();
                }

                System.out.println("Executing goto to block: " + block);
                IGetToBlockProcess getToBlockProcess = baritone.getGetToBlockProcess();
                if (getToBlockProcess == null) {
                    future.completeExceptionally(new RuntimeException("GetToBlock process not available"));
                    break;
                }

                PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
                getToBlockProcess.getToBlock(new BlockOptionalMeta(block));
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown GOTO mode: " + mode));
                break;
        }
    }

    private boolean tryExecuteGotoUsingAttachedParameter(IBaritone baritone, ICustomGoalProcess customGoalProcess, CompletableFuture<Void> future) {
        Node parameterNode = getAttachedParameter();
        if (parameterNode == null) {
            return false;
        }

        switch (parameterNode.getType()) {
            case PARAM_ITEM:
                return gotoNearestDroppedItem(parameterNode, customGoalProcess, future);
            case PARAM_ENTITY:
                return gotoNearestEntity(parameterNode, customGoalProcess, future);
            case PARAM_PLAYER:
                return gotoNamedPlayer(parameterNode, customGoalProcess, future);
            case PARAM_BLOCK:
                return gotoBlockFromParameter(parameterNode, baritone, future);
            case PARAM_BLOCK_LIST:
                return gotoBlockListFromParameter(parameterNode, baritone, future);
            default:
                return false;
        }
    }

    private boolean gotoNearestDroppedItem(Node parameterNode, ICustomGoalProcess customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String itemId = getParameterString(parameterNode, "Item");
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }

        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            sendNodeErrorMessage(client, "Cannot navigate to item \"" + itemId + "\": unknown identifier.");
            future.complete(null);
            return true;
        }

        Item item = Registries.ITEM.get(identifier);
        double searchRange = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
        Optional<BlockPos> target = findNearestDroppedItem(client, item, searchRange);
        if (target.isEmpty()) {
            sendNodeErrorMessage(client, "No dropped " + itemId + " found nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to dropped item: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = target.get();
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
        customGoalProcess.setGoalAndPath(new GoalBlock(pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private boolean gotoNearestEntity(Node parameterNode, ICustomGoalProcess customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String entityId = getParameterString(parameterNode, "Entity");
        if (entityId == null || entityId.isEmpty()) {
            return false;
        }

        Identifier identifier = Identifier.tryParse(entityId);
        if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
            sendNodeErrorMessage(client, "Cannot navigate to entity \"" + entityId + "\": unknown identifier.");
            future.complete(null);
            return true;
        }

        EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
        double range = parseDoubleOrDefault(getParameterString(parameterNode, "Range"), PARAMETER_SEARCH_RADIUS);
        Optional<Entity> target = findNearestEntity(client, entityType, range);
        if (target.isEmpty()) {
            sendNodeErrorMessage(client, "No entity of type " + entityId + " found nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to entity: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = target.get().getBlockPos();
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
        customGoalProcess.setGoalAndPath(new GoalBlock(pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private boolean gotoNamedPlayer(Node parameterNode, ICustomGoalProcess customGoalProcess, CompletableFuture<Void> future) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        String playerName = getParameterString(parameterNode, "Player");
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }

        Optional<AbstractClientPlayerEntity> match = client.world.getPlayers().stream()
            .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(playerName))
            .findFirst();

        if (match.isEmpty()) {
            sendNodeErrorMessage(client, "Player \"" + playerName + "\" is not nearby for " + type.getDisplayName() + ".");
            future.complete(null);
            return true;
        }

        if (customGoalProcess == null) {
            sendNodeErrorMessage(client, "Cannot navigate to player: goal process unavailable.");
            future.complete(null);
            return true;
        }

        BlockPos pos = match.get().getBlockPos();
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
        customGoalProcess.setGoalAndPath(new GoalBlock(pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    private boolean gotoBlockFromParameter(Node parameterNode, IBaritone baritone, CompletableFuture<Void> future) {
        String blockId = getParameterString(parameterNode, "Block");
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }

        IGetToBlockProcess getToBlockProcess = baritone != null ? baritone.getGetToBlockProcess() : null;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (getToBlockProcess == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Cannot navigate to block: block search process unavailable.");
            }
            future.complete(null);
            return true;
        }

        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
        getToBlockProcess.getToBlock(new BlockOptionalMeta(blockId));
        return true;
    }

    private boolean gotoBlockListFromParameter(Node parameterNode, IBaritone baritone, CompletableFuture<Void> future) {
        String list = getParameterString(parameterNode, "Blocks");
        if (list == null || list.isEmpty()) {
            return false;
        }

        String[] parts = list.split("[,;]");
        for (String candidate : parts) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                return gotoBlockFromParameterValue(trimmed, baritone, future);
            }
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            sendNodeErrorMessage(client, "Block list parameter for " + type.getDisplayName() + " does not contain a valid block name.");
        }
        future.complete(null);
        return true;
    }

    private boolean gotoBlockFromParameterValue(String blockId, IBaritone baritone, CompletableFuture<Void> future) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        IGetToBlockProcess getToBlockProcess = baritone != null ? baritone.getGetToBlockProcess() : null;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (getToBlockProcess == null) {
            if (client != null) {
                sendNodeErrorMessage(client, "Cannot navigate to block: block search process unavailable.");
            }
            future.complete(null);
            return true;
        }
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_GOTO, future);
        getToBlockProcess.getToBlock(new BlockOptionalMeta(blockId));
        return true;
    }
    
    private void executeCollectCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }

        NodeMode collectMode = mode != null ? mode : NodeMode.COLLECT_SINGLE;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        IBaritone baritone = getBaritone();
        if (baritone == null) {
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }

        IMineProcess mineProcess = baritone.getMineProcess();
        if (mineProcess == null) {
            sendNodeErrorMessage(client, "Cannot mine: Baritone mine process unavailable.");
            future.complete(null);
            return;
        }

        boolean started;
        switch (collectMode) {
            case COLLECT_SINGLE:
                String blockId = "minecraft:stone";
                NodeParameter blockParam = getParameter("Block");
                if (blockParam != null) {
                    blockId = blockParam.getStringValue();
                }

                int amount = 1;
                NodeParameter amountParam = getParameter("Amount");
                if (amountParam != null) {
                    amount = amountParam.getIntValue();
                }

                amount = Math.max(1, amount);

                Identifier blockIdentifier = Identifier.tryParse(blockId);
                if (blockIdentifier == null || !Registries.BLOCK.containsId(blockIdentifier)) {
                    sendNodeErrorMessage(client, "Cannot mine: a valid block type is required.");
                    future.complete(null);
                    return;
                }

                mineProcess.mineByName(amount, blockId);
                started = mineProcess.isActive();
                break;

            case COLLECT_MULTIPLE:
                String blockList = "stone,dirt";
                NodeParameter blocksParam = getParameter("Blocks");
                if (blocksParam != null) {
                    blockList = blocksParam.getStringValue();
                }

                String[] targets = Arrays.stream(blockList.split(","))
                        .map(String::trim)
                        .filter(s -> {
                            Identifier parsed = Identifier.tryParse(s);
                            return parsed != null && Registries.BLOCK.containsId(parsed);
                        })
                        .toArray(String[]::new);

                if (targets.length == 0) {
                    sendNodeErrorMessage(client, "Cannot mine: specify at least one block type.");
                    future.complete(null);
                    return;
                }

                mineProcess.mineByName(targets);
                started = mineProcess.isActive();
                break;

            default:
                future.completeExceptionally(new IllegalStateException("Unknown COLLECT mode: " + collectMode));
                return;
        }

        if (!started) {
            sendNodeErrorMessage(client, "Failed to start mining task for Mine node.");
            future.complete(null);
            return;
        }

        // Cancel any in-progress mining to avoid overlapping tasks that can deadlock the process
        if (mineProcess.isActive()) {
            mineProcess.cancel();
        }

        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_COLLECT, future);
    }
    
    private void executeCraftCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String itemId = "stick";
        int quantity = 1;

        NodeParameter itemParam = getParameter("Item");
        NodeParameter quantityParam = getParameter("Quantity");

        if (itemParam != null) {
            itemId = itemParam.getStringValue();
        }
        if (quantityParam != null) {
            quantity = quantityParam.getIntValue();
        }

        NodeMode craftMode = mode != null ? mode : NodeMode.CRAFT_PLAYER_GUI;

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            sendNodeErrorMessage(client, "Cannot craft \"" + itemId + "\": unknown item identifier.");
            future.complete(null);
            return;
        }

        Item targetItem = Registries.ITEM.get(identifier);
        if (client == null || client.player == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        if (!isCraftingScreenAvailable(client, craftMode)) {
            String unavailableMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                    ? "Cannot craft: open a crafting table GUI before running this node."
                    : "Cannot craft: open your inventory or a crafting table GUI before running this node.";
            sendNodeErrorMessage(client, unavailableMessage);
            future.complete(null);
            return;
        }

        String itemDisplayName = targetItem.getName().getString();

        ScreenHandler handler = client.player.currentScreenHandler;
        if (!isCompatibleCraftingHandler(handler, craftMode)) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the crafting screen closed.");
            future.complete(null);
            return;
        }

        final NodeMode effectiveCraftMode;
        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingScreenHandler) {
            effectiveCraftMode = NodeMode.CRAFT_CRAFTING_TABLE;
        } else {
            effectiveCraftMode = craftMode;
        }

        RecipeEntry<CraftingRecipe> recipeEntry = findCraftingRecipe(client, targetItem, effectiveCraftMode);
        if (recipeEntry == null) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": no matching recipe found.");
            future.complete(null);
            return;
        }

        List<ItemStack> emptyGrid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        ItemStack outputTemplate = recipeEntry.value().craft(CraftingRecipeInput.create(3, 3, emptyGrid), client.player.getWorld().getRegistryManager());
        if (outputTemplate.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the recipe produced no output.");
            future.complete(null);
            return;
        }

        int desiredCount = Math.max(1, quantity);
        int perCraftOutput = Math.max(1, outputTemplate.getCount());
        int craftsRequested = Math.max(1, (int) Math.ceil(desiredCount / (double) perCraftOutput));

        List<GridIngredient> gridIngredients = resolveGridIngredients(recipeEntry.value(), effectiveCraftMode);
        if (gridIngredients.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot craft " + itemDisplayName + ": the recipe has no ingredients.");
            future.complete(null);
            return;
        }

        int[] craftingGridSlots = getCraftingGridSlots(effectiveCraftMode);

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return craftRecipeUsingScreen(client, effectiveCraftMode, recipeEntry, targetItem, craftsRequested, desiredCount, itemDisplayName, gridIngredients, craftingGridSlots);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(e);
                }
            })
            .whenComplete((summary, throwable) -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (!(cause instanceof InterruptedException)) {
                        sendNodeErrorMessageOnClientThread(client, "Cannot craft " + itemDisplayName + ": " + cause.getMessage());
                    }
                    future.complete(null);
                    return;
                }

                if (summary.failureMessage != null) {
                    sendNodeErrorMessageOnClientThread(client, summary.failureMessage);
                }

                future.complete(null);
            });
    }

    private void executeScreenControlCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode screenMode = mode != null ? mode : NodeMode.SCREEN_OPEN_CHAT;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        try {
            runOnClientThread(client, () -> {
                switch (screenMode) {
                    case SCREEN_OPEN_CHAT:
                        client.setScreen(new ChatScreen(""));
                        break;
                    case SCREEN_CLOSE_CURRENT:
                        if (client.player != null) {
                            client.player.closeHandledScreen();
                        }
                        client.setScreen(null);
                        break;
                    default:
                        throw new IllegalStateException("Unknown screen control mode: " + screenMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private void executePlayerGuiCommand(CompletableFuture<Void> future, NodeMode desiredMode) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        NodeMode playerGuiMode = desiredMode != null ? desiredMode : (mode != null ? mode : NodeMode.PLAYER_GUI_OPEN);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        if (client == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        try {
            runOnClientThread(client, () -> {
                switch (playerGuiMode) {
                    case PLAYER_GUI_OPEN:
                        if (client.player == null || client.player.networkHandler == null) {
                            throw new RuntimeException("Cannot open the player GUI without an active player.");
                        }

                        client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                                client.player,
                                ClientCommandC2SPacket.Mode.OPEN_INVENTORY
                        ));

                        if (!(client.currentScreen instanceof InventoryScreen)) {
                            client.setScreen(new InventoryScreen(client.player));
                        }
                        break;
                case PLAYER_GUI_CLOSE:
                    if (client.player == null) {
                        throw new RuntimeException("Cannot close the player GUI without an active player.");
                    }

                    if (client.currentScreen != null) {
                        client.player.closeHandledScreen();
                        client.setScreen(null);
                    }
                    break;
                default:
                        throw new IllegalStateException("Unknown player GUI mode: " + playerGuiMode);
                }
            });
            future.complete(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (RuntimeException e) {
            sendNodeErrorMessage(client, e.getMessage());
            future.complete(null);
        }
    }

    private boolean isCraftingScreenAvailable(net.minecraft.client.MinecraftClient client, NodeMode craftMode) {
        if (client == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return client.currentScreen instanceof CraftingScreen;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return client.currentScreen instanceof InventoryScreen || client.currentScreen instanceof CraftingScreen;
        }

        return false;
    }

    private boolean isCompatibleCraftingHandler(ScreenHandler handler, NodeMode craftMode) {
        if (handler == null) {
            return false;
        }

        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return handler instanceof CraftingScreenHandler;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
            return handler instanceof PlayerScreenHandler || handler instanceof CraftingScreenHandler;
        }

        return false;
    }

    private RecipeEntry<CraftingRecipe> findCraftingRecipe(net.minecraft.client.MinecraftClient client, Item targetItem, NodeMode craftMode) {
        MinecraftServer server = client.getServer();
        if (server == null) {
            return null;
        }

        ServerRecipeManager recipeManager = server.getRecipeManager();
        List<ServerRecipeManager.ServerRecipe> serverRecipes = getServerRecipeList(recipeManager);
        if (serverRecipes.isEmpty()) {
            return null;
        }

        List<ItemStack> emptyGrid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (ServerRecipeManager.ServerRecipe serverRecipe : serverRecipes) {
            RecipeEntry<?> entry = serverRecipe.parent();
            if (!(entry.value() instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }

            if (craftMode == NodeMode.CRAFT_PLAYER_GUI && !recipeFitsPlayerGrid(craftingRecipe.getIngredientPlacement())) {
                continue;
            }

            ItemStack result = craftingRecipe.craft(CraftingRecipeInput.create(3, 3, emptyGrid), client.player.getWorld().getRegistryManager());
            if (!result.isOf(targetItem)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            RecipeEntry<CraftingRecipe> castEntry = (RecipeEntry<CraftingRecipe>) entry;
            return castEntry;
        }

        return null;
    }

    private List<ServerRecipeManager.ServerRecipe> getServerRecipeList(ServerRecipeManager manager) {
        if (SERVER_RECIPES_FIELD == null) {
            return Collections.emptyList();
        }
        try {
            @SuppressWarnings("unchecked")
            List<ServerRecipeManager.ServerRecipe> recipes = (List<ServerRecipeManager.ServerRecipe>) SERVER_RECIPES_FIELD.get(manager);
            return recipes != null ? recipes : Collections.emptyList();
        } catch (IllegalAccessException e) {
            return Collections.emptyList();
        }
    }

    private boolean recipeFitsPlayerGrid(IngredientPlacement placement) {
        if (placement == null) {
            return false;
        }

        if (placement.hasNoPlacement()) {
            return placement.getIngredients().size() <= 4;
        }

        IntList slots = placement.getPlacementSlots();
        if (slots == null || slots.isEmpty()) {
            return placement.getIngredients().size() <= 4;
        }

        int minX = 3;
        int minY = 3;
        int maxX = -1;
        int maxY = -1;
        for (int slot : slots) {
            int x = slot % 3;
            int y = slot / 3;
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        return width <= 2 && height <= 2;
    }

    private static Field initServerRecipesField() {
        try {
            Field field = ServerRecipeManager.class.getDeclaredField("field_54641");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static final Field SERVER_RECIPES_FIELD = initServerRecipesField();

    private CraftingSummary craftRecipeUsingScreen(net.minecraft.client.MinecraftClient client,
                                                   NodeMode craftMode,
                                                   RecipeEntry<CraftingRecipe> recipeEntry,
                                                   Item targetItem,
                                                   int craftsRequested,
                                                   int desiredCount,
                                                   String itemDisplayName,
                                                   List<GridIngredient> gridIngredients,
                                                   int[] gridSlots) throws InterruptedException {
        int totalProduced = 0;
        String failureMessage = null;

        for (int attempt = 0; attempt < craftsRequested; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (!isCraftingScreenAvailable(client, craftMode)) {
                failureMessage = craftMode == NodeMode.CRAFT_CRAFTING_TABLE
                        ? "Cannot craft " + itemDisplayName + ": open a crafting table GUI before running this node."
                        : "Cannot craft " + itemDisplayName + ": open your inventory or a crafting table GUI before running this node.";
                break;
            }

            ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
            if (!isCompatibleCraftingHandler(handler, craftMode)) {
                failureMessage = "Cannot craft " + itemDisplayName + ": the crafting screen closed.";
                break;
            }

            CraftingAttemptResult attemptResult = performCraftingAttempt(client, targetItem, itemDisplayName, gridIngredients, gridSlots, craftMode);
            if (attemptResult.errorMessage != null) {
                failureMessage = attemptResult.errorMessage;
                if (attemptResult.produced > 0) {
                    totalProduced += attemptResult.produced;
                }
                break;
            }

            if (attemptResult.produced <= 0) {
                failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
                break;
            }

            totalProduced += attemptResult.produced;

            if (totalProduced >= desiredCount) {
                break;
            }
        }

        if (totalProduced <= 0 && failureMessage == null) {
            failureMessage = "Cannot craft " + itemDisplayName + ": missing required ingredients.";
        }

        return new CraftingSummary(totalProduced, failureMessage);
    }

    private CraftingAttemptResult performCraftingAttempt(net.minecraft.client.MinecraftClient client,
                                                         Item targetItem,
                                                         String itemDisplayName,
                                                         List<GridIngredient> gridIngredients,
                                                         int[] gridSlots,
                                                         NodeMode craftMode) throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger producedRef = new java.util.concurrent.atomic.AtomicInteger();

        runOnClientThread(client, () -> {
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                return;
            }

            ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
            if (handler == null) {
                errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                return;
            }

            clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
        });

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(0, errorRef.get());
        }

        for (GridIngredient ingredient : gridIngredients) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (ingredient == null || ingredient.ingredient().isEmpty()) {
                continue;
            }

            java.util.concurrent.atomic.AtomicBoolean placed = new java.util.concurrent.atomic.AtomicBoolean(false);

            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                int sourceSlot = findIngredientSourceSlot(handler, ingredient.ingredient());
                if (sourceSlot == -1) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": missing required ingredients.");
                    return;
                }

                int targetSlot = mapLogicalSlotToHandlerSlot(handler, craftMode, ingredient.slotIndex());
                if (targetSlot < 0) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting grid slot unavailable.");
                    return;
                }

                interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, client.player);
                interactionManager.clickSlot(handler.syncId, targetSlot, 1, SlotActionType.PICKUP, client.player);
                interactionManager.clickSlot(handler.syncId, sourceSlot, 0, SlotActionType.PICKUP, client.player);
                placed.set(true);
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            if (!placed.get()) {
                return new CraftingAttemptResult(producedRef.get(), "Cannot craft " + itemDisplayName + ": failed to place ingredients.");
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        for (int poll = 0; poll < CRAFTING_OUTPUT_POLL_LIMIT && producedRef.get() <= 0 && errorRef.get() == null; poll++) {
            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                Slot outputSlot;
                try {
                    outputSlot = handler.getSlot(0);
                } catch (IndexOutOfBoundsException e) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": crafting output unavailable.");
                    return;
                }

                ItemStack resultStack = outputSlot.getStack();
                if (resultStack.isEmpty() || !resultStack.isOf(targetItem)) {
                    return;
                }

                producedRef.set(resultStack.getCount());
                interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
            });

            if (producedRef.get() > 0 || errorRef.get() != null) {
                break;
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
        }

        if (producedRef.get() > 0) {
            runOnClientThread(client, () -> {
                ClientPlayerInteractionManager interactionManager = client.interactionManager;
                if (interactionManager == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": interaction manager unavailable.");
                    return;
                }

                ScreenHandler handler = client.player != null ? client.player.currentScreenHandler : null;
                if (handler == null) {
                    errorRef.set("Cannot craft " + itemDisplayName + ": the crafting screen closed.");
                    return;
                }

                clearCraftingGrid(client, interactionManager, handler, gridSlots, craftMode);
            });

            if (errorRef.get() != null) {
                return new CraftingAttemptResult(producedRef.get(), errorRef.get());
            }

            Thread.sleep(CRAFTING_ACTION_DELAY_MS);
            return new CraftingAttemptResult(producedRef.get(), null);
        }

        if (errorRef.get() != null) {
            return new CraftingAttemptResult(producedRef.get(), errorRef.get());
        }

        return new CraftingAttemptResult(0, "Cannot craft " + itemDisplayName + ": missing required ingredients.");
    }

    private void clearCraftingGrid(net.minecraft.client.MinecraftClient client,
                                   ClientPlayerInteractionManager interactionManager,
                                   ScreenHandler handler,
                                   int[] gridSlots,
                                   NodeMode craftMode) {
        if (client.player == null || interactionManager == null || handler == null || gridSlots == null) {
            return;
        }

        int[] actualSlots = mapGridSlotsForHandler(handler, craftMode, gridSlots);

        for (int slotIndex : actualSlots) {
            try {
                Slot slot = handler.getSlot(slotIndex);
                if (slot != null && slot.hasStack()) {
                    interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, client.player);
                }
            } catch (IndexOutOfBoundsException ignored) {
                // Ignore missing grid slots for the current handler.
            }
        }
    }

    private int findIngredientSourceSlot(ScreenHandler handler, Ingredient ingredient) {
        if (handler == null || ingredient == null || ingredient.isEmpty()) {
            return -1;
        }

        List<Slot> slots = handler.slots;
        for (int slotIdx = 0; slotIdx < slots.size(); slotIdx++) {
            Slot slot = slots.get(slotIdx);
            if (!(slot.inventory instanceof PlayerInventory)) {
                continue;
            }

            int inventoryIndex = slot.getIndex();
            if (inventoryIndex < 0 || inventoryIndex >= PlayerInventory.MAIN_SIZE) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }

            if (ingredient.test(stack)) {
                return slotIdx;
            }
        }

        return -1;
    }

    private int[] mapGridSlotsForHandler(ScreenHandler handler, NodeMode craftMode, int[] logicalSlots) {
        if (handler == null || logicalSlots == null) {
            return new int[0];
        }
        int[] mapped = new int[logicalSlots.length];
        int count = 0;
        for (int logical : logicalSlots) {
            int actual = mapLogicalSlotToHandlerSlot(handler, craftMode, logical);
            if (actual >= 0) {
                mapped[count++] = actual;
            }
        }
        if (count == mapped.length) {
            return mapped;
        }
        int[] compact = new int[count];
        for (int i = 0; i < count; i++) {
            compact[i] = mapped[i];
        }
        return compact;
    }

    private int mapLogicalSlotToHandlerSlot(ScreenHandler handler, NodeMode craftMode, int logicalSlot) {
        if (handler == null) {
            return -1;
        }

        if (craftMode == NodeMode.CRAFT_PLAYER_GUI && handler instanceof CraftingScreenHandler) {
            return switch (logicalSlot) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 4;
                case 4 -> 5;
                default -> -1;
            };
        }

        return logicalSlot;
    }

    private List<GridIngredient> resolveGridIngredients(CraftingRecipe recipe, NodeMode craftMode) {
        List<GridIngredient> result = new ArrayList<>();
        if (recipe == null) {
            return result;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                return resolvePlayerGridIngredients(shapedRecipe);
            }
            return resolveCraftingTableGridIngredients(shapedRecipe);
        }

        IngredientPlacement placement = recipe.getIngredientPlacement();
        if (placement == null) {
            return result;
        }

        List<Ingredient> ingredients = placement.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return result;
        }

        IntList slots = placement.getPlacementSlots();
        int gridLimit = craftMode == NodeMode.CRAFT_CRAFTING_TABLE ? 9 : 4;

        if (placement.hasNoPlacement() || slots == null || slots.isEmpty()) {
            int limit = Math.min(ingredients.size(), gridLimit);
            for (int i = 0; i < limit; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                result.add(new GridIngredient(1 + i, ingredient));
            }
            return result;
        }

        int limit = Math.min(ingredients.size(), slots.size());
        for (int i = 0; i < limit; i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            int logicalSlot = slots.getInt(i);
            int resolvedSlot;
            if (craftMode == NodeMode.CRAFT_PLAYER_GUI) {
                int localX = logicalSlot % 3;
                int localY = logicalSlot / 3;

                if (localX >= 2 || localY >= 2) {
                    continue;
                }

                resolvedSlot = 1 + localX + (localY * 2);
            } else {
                resolvedSlot = 1 + logicalSlot;
            }

            if (resolvedSlot > gridLimit) {
                continue;
            }

            result.add(new GridIngredient(resolvedSlot, ingredient));
        }

        return result;
    }

    private List<GridIngredient> resolvePlayerGridIngredients(ShapedRecipe recipe) {
        List<GridIngredient> result = new ArrayList<>();
        List<Optional<Ingredient>> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return result;
        }

        int width = Math.min(recipe.getWidth(), 2);
        int height = Math.min(recipe.getHeight(), 2);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Optional<Ingredient> optional = ingredients.get(index);
                if (optional == null || optional.isEmpty()) {
                    continue;
                }

                Ingredient ingredient = optional.get();
                if (ingredient.isEmpty()) {
                    continue;
                }

                int slotIndex = 1 + x + (y * 2);
                result.add(new GridIngredient(slotIndex, ingredient));
            }
        }

        return result;
    }

    private List<GridIngredient> resolveCraftingTableGridIngredients(ShapedRecipe recipe) {
        List<GridIngredient> result = new ArrayList<>();
        List<Optional<Ingredient>> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return result;
        }

        int width = Math.min(recipe.getWidth(), 3);
        int height = Math.min(recipe.getHeight(), 3);
        int recipeWidth = Math.max(recipe.getWidth(), 1);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + (y * recipeWidth);
                if (index < 0 || index >= ingredients.size()) {
                    continue;
                }

                Optional<Ingredient> optional = ingredients.get(index);
                if (optional == null || optional.isEmpty()) {
                    continue;
                }

                Ingredient ingredient = optional.get();
                if (ingredient.isEmpty()) {
                    continue;
                }

                int slotIndex = 1 + x + (y * 3);
                result.add(new GridIngredient(slotIndex, ingredient));
            }
        }

        return result;
    }

    private int[] getCraftingGridSlots(NodeMode craftMode) {
        if (craftMode == NodeMode.CRAFT_CRAFTING_TABLE) {
            return new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
        }
        return new int[] {1, 2, 3, 4};
    }

    private static class CraftingSummary {
        final int produced;
        final String failureMessage;

        CraftingSummary(int produced, String failureMessage) {
            this.produced = produced;
            this.failureMessage = failureMessage;
        }
    }

    private static class GridIngredient {
        private final int slotIndex;
        private final Ingredient ingredient;

        GridIngredient(int slotIndex, Ingredient ingredient) {
            this.slotIndex = slotIndex;
            this.ingredient = ingredient;
        }

        int slotIndex() {
            return slotIndex;
        }

        Ingredient ingredient() {
            return ingredient;
        }
    }

    private static class CraftingAttemptResult {
        final int produced;
        final String errorMessage;

        CraftingAttemptResult(int produced, String errorMessage) {
            this.produced = produced;
            this.errorMessage = errorMessage;
        }
    }

    private void executePlaceCommand(CompletableFuture<Void> future) {
        Node blockParameterNode = getAttachedParameter(0);
        Node coordinateParameterNode = getAttachedParameter(1);
        boolean coordinateHandledByBlockParam = coordinateParameterNode == null && parameterProvidesCoordinates(blockParameterNode);

        if (blockParameterNode != null) {
            EnumSet<ParameterUsage> blockUsages = coordinateHandledByBlockParam
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(0, blockUsages, future, true) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        } else {
            runtimeParameterData = null;
        }

        if (coordinateParameterNode != null) {
            EnumSet<ParameterUsage> coordinateUsages = parameterProvidesCoordinates(coordinateParameterNode)
                ? EnumSet.of(ParameterUsage.POSITION)
                : EnumSet.noneOf(ParameterUsage.class);
            if (preprocessParameterSlot(1, coordinateUsages, future, blockParameterNode == null) == ParameterHandlingResult.COMPLETE) {
                return;
            }
        }

        boolean inheritPlacementCoordinates = coordinateHandledByBlockParam
            || parameterProvidesCoordinates(coordinateParameterNode);
        String block = "stone";
        int x = 0, y = 0, z = 0;

        NodeParameter blockParam = getParameter("Block");
        NodeParameter xParam = getParameter("X");
        NodeParameter yParam = getParameter("Y");
        NodeParameter zParam = getParameter("Z");
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);

        if (blockParam != null) block = blockParam.getStringValue();
        if (xParam != null) x = xParam.getIntValue();
        if (yParam != null) y = yParam.getIntValue();
        if (zParam != null) z = zParam.getIntValue();

        RuntimeParameterData parameterData = runtimeParameterData;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                block = parameterData.targetBlockId;
                setParameterValueAndPropagate("Block", block);
            }
            if (inheritPlacementCoordinates && parameterData.targetBlockPos != null) {
                BlockPos resolved = parameterData.targetBlockPos;
                x = resolved.getX();
                y = resolved.getY();
                z = resolved.getZ();
            }
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        String originalBlockId = block;
        block = normalizeResourceId(block, "minecraft");
        if (!Objects.equals(originalBlockId, block)) {
            setParameterValueAndPropagate("Block", block);
        }

        if (block == null || block.isEmpty()) {
            sendNodeErrorMessage(client, "Cannot place block: no block selected.");
            future.complete(null);
            return;
        }

        System.out.println("Placing block '" + block + "' at " + x + ", " + y + ", " + z);

        BlockPos targetPos = new BlockPos(x, y, z);
        if (parameterData != null) {
            parameterData.targetBlockPos = targetPos;
            if (parameterData.targetBlockId == null || parameterData.targetBlockId.isEmpty()) {
                parameterData.targetBlockId = block;
            }
        }
        double reachSquared = getPlacementReachSquared(client);

        Block desiredBlock = resolveBlockForPlacement(block);
        if (desiredBlock == null) {
            sendNodeErrorMessage(client, "Cannot place block: unknown block \"" + block + "\".");
            future.complete(null);
            return;
        }

        final BlockPos placementPos = targetPos;
        final Block resolvedBlock = desiredBlock;
        final String resolvedBlockId = block;
        final Hand resolvedHand = hand;
        final double resolvedReachSquared = reachSquared;

        new Thread(() -> {
            try {
                BlockHitResult placementHitResult = supplyFromClient(client, () ->
                    preparePlacementHitResult(client, placementPos, resolvedBlockId, resolvedHand, resolvedReachSquared)
                );
                runOnClientThread(client, () -> {
                    if (client.world.getBlockState(placementPos).isOf(resolvedBlock)) {
                        return;
                    }

                    ActionResult result = client.interactionManager.interactBlock(client.player, resolvedHand, placementHitResult);
                    if (!result.isAccepted()) {
                        throw new PlacementFailure("Cannot place block at " + formatBlockPos(placementPos) + ": placement rejected (" + result + ").");
                    }
                    if (client.player != null) {
                        client.player.swingHand(resolvedHand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(resolvedHand));
                        }
                    }
                });
                boolean placed = waitForBlockPlacement(client, placementPos, resolvedBlock);
                if (!placed) {
                    sendNodeErrorMessage(client, "Attempted to place block \"" + resolvedBlockId + "\" at " + formatBlockPos(placementPos) + " but it did not appear. Make sure the space is clear and within reach.");
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } catch (PlacementFailure e) {
                sendNodeErrorMessage(client, e.getMessage());
                future.complete(null);
            } catch (RuntimeException e) {
                sendNodeErrorMessage(client, "Failed to place block \"" + resolvedBlockId + "\": " + e.getMessage());
                future.complete(null);
            }
        }, "Pathmind-Place").start();
    }

    private boolean shouldInheritPlacementCoordinates() {
        Node parameterNode = getAttachedParameter(1);
        if (parameterNode == null) {
            parameterNode = getAttachedParameter();
        }
        return parameterProvidesCoordinates(parameterNode);
    }

    private boolean parameterProvidesCoordinates(Node parameterNode) {
        if (parameterNode == null) {
            return false;
        }
        return parameterProvidesCoordinates(parameterNode.getType());
    }

    private boolean parameterProvidesCoordinates(NodeType parameterType) {
        if (parameterType == null) {
            return false;
        }
        switch (parameterType) {
            case PARAM_COORDINATE:
            case PARAM_SCHEMATIC:
            case PARAM_PLACE_TARGET:
            case PARAM_ITEM:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_BLOCK:
            case PARAM_BLOCK_LIST:
            case PARAM_WAYPOINT:
            case PARAM_CLOSEST:
                return true;
            default:
                return false;
        }
    }
    
    private void executeBuildCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for BUILD node"));
            return;
        }
        
        String schematic = "house.schematic";
        NodeParameter schematicParam = getParameter("Schematic");
        if (schematicParam != null) {
            schematic = schematicParam.getStringValue();
        }
        
        String command;
        switch (mode) {
            case BUILD_PLAYER:
                command = String.format("#build %s", schematic);
                System.out.println("Executing build at player location: " + command);
                break;
                
            case BUILD_XYZ:
                int x = 0, y = 0, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                command = String.format("#build %s %d %d %d", schematic, x, y, z);
                System.out.println("Executing build at coordinates: " + command);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown BUILD mode: " + mode));
                return;
        }
        
        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }
    
    private void executeExploreCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for EXPLORE node"));
            return;
        }
        
        IBaritone baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for explore command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        IExploreProcess exploreProcess = baritone.getExploreProcess();
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_EXPLORE, future);
        
        switch (mode) {
            case EXPLORE_CURRENT:
                System.out.println("Executing explore from current position");
                exploreProcess.explore(0, 0); // 0,0 means from current position
                break;
                
            case EXPLORE_XYZ:
                int x = 0, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                System.out.println("Executing explore at: " + x + ", " + z);
                exploreProcess.explore(x, z);
                break;
                
            case EXPLORE_FILTER:
                String filter = "explore.txt";
                NodeParameter filterParam = getParameter("Filter");
                if (filterParam != null) {
                    filter = filterParam.getStringValue();
                }
                
                System.out.println("Executing explore with filter: " + filter);
                // For filter-based exploration, we need to use a different approach
                executeCommand("#explore " + filter);
                future.complete(null); // Command-based exploration completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown EXPLORE mode: " + mode));
                return;
        }
    }
    
    private void executeFollowCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FOLLOW node"));
            return;
        }
        
        String command;
        switch (mode) {
            case FOLLOW_PLAYER:
                String player = "PlayerName";
                NodeParameter playerParam = getParameter("Player");
                if (playerParam != null) {
                    player = playerParam.getStringValue();
                }

                command = "#follow player " + player;
                System.out.println("Executing follow player: " + command);
                break;
                
            case FOLLOW_PLAYERS:
                command = "#follow players";
                System.out.println("Executing follow any players: " + command);
                break;
                
            case FOLLOW_ENTITIES:
                command = "#follow entities";
                System.out.println("Executing follow any entities: " + command);
                break;
                
            case FOLLOW_ENTITY_TYPE:
                String entity = "cow";
                NodeParameter entityParam = getParameter("Entity");
                if (entityParam != null) {
                    entity = entityParam.getStringValue();
                }

                command = "#follow entity " + entity;
                System.out.println("Executing follow entity type: " + command);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FOLLOW mode: " + mode));
                return;
        }
        
        executeCommand(command);
        future.complete(null); // Follow commands complete immediately
    }
    
    private void executeWaitCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        double baseDuration = Math.max(0.0, getDoubleParameter("Duration", 1.0));
        double minimum = Math.max(0.0, getDoubleParameter("MinimumDurationSeconds", 0.0));
        double variance = Math.max(0.0, getDoubleParameter("RandomVarianceSeconds", 0.0));

        double effectiveDuration = Math.max(baseDuration, minimum);
        if (variance > 0.0) {
            double randomOffset = (Math.random() * 2.0 - 1.0) * variance;
            effectiveDuration = Math.max(minimum, Math.max(0.0, effectiveDuration + randomOffset));
        }

        final double waitSeconds = effectiveDuration;
        System.out.println("Waiting for " + waitSeconds + " seconds (configured duration=" + baseDuration + ")");

        new Thread(() -> {
            try {
                Thread.sleep((long) (waitSeconds * 1000));
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Wait").start();
    }
    
    private void executeControlRepeat(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        int count = Math.max(0, getIntParameter("Count", 1));
        if (!repeatActive) {
            repeatRemainingIterations = count;
            repeatActive = true;
        }
        if (repeatRemainingIterations > 0) {
            repeatRemainingIterations--;
            setNextOutputSocket(0);
        } else {
            repeatRemainingIterations = 0;
            repeatActive = false;
            setNextOutputSocket(1);
        }
        future.complete(null);
    }
    
    private void executeControlRepeatUntil(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean conditionMet = evaluateConditionFromParameters();
        if (conditionMet) {
            repeatRemainingIterations = 0;
            repeatActive = false;
            setNextOutputSocket(1);
        } else {
            repeatActive = true;
            setNextOutputSocket(0);
        }
        future.complete(null);
    }
    
    private void executeControlForever(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        repeatActive = true;
        setNextOutputSocket(0);
        future.complete(null);
    }

    private void executeControlIf(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : NO_OUTPUT);
        future.complete(null);
    }

    private void executeControlIfElse(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        boolean condition = evaluateConditionFromParameters();
        setNextOutputSocket(condition ? 0 : 1);
        future.complete(null);
    }

    private void executeMessageCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String text = "Hello World";
        NodeParameter textParam = getParameter("Text");
        if (textParam != null) {
            text = textParam.getStringValue();
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatMessage(text);
        } else {
            System.err.println("Unable to send chat message: client or player not available");
        }
        future.complete(null); // Message commands complete immediately
    }
    
    private void executeGoalCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for GOAL node"));
            return;
        }

        IBaritone baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for goal command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
        
        switch (mode) {
            case GOAL_XYZ:
                int x = 0, y = 64, z = 0;
                NodeParameter xParam = getParameter("X");
                NodeParameter yParam = getParameter("Y");
                NodeParameter zParam = getParameter("Z");
                
                if (xParam != null) x = xParam.getIntValue();
                if (yParam != null) y = yParam.getIntValue();
                if (zParam != null) z = zParam.getIntValue();
                
                System.out.println("Setting goal to: " + x + ", " + y + ", " + z);
                GoalBlock goal = new GoalBlock(x, y, z);
                customGoalProcess.setGoal(goal);
                break;
                
            case GOAL_XZ:
                int x2 = 0, z2 = 0;
                NodeParameter xParam2 = getParameter("X");
                NodeParameter zParam2 = getParameter("Z");
                
                if (xParam2 != null) x2 = xParam2.getIntValue();
                if (zParam2 != null) z2 = zParam2.getIntValue();
                
                System.out.println("Setting goal to: " + x2 + ", " + z2);
                GoalBlock goal2 = new GoalBlock(x2, 0, z2); // Y will be determined by pathfinding
                customGoalProcess.setGoal(goal2);
                break;
                
            case GOAL_Y:
                int y3 = 64;
                NodeParameter yParam3 = getParameter("Y");
                if (yParam3 != null) y3 = yParam3.getIntValue();
                
                System.out.println("Setting goal to Y level: " + y3);
                // For Y-only goal, we need to get current X,Z and set goal there
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    int currentX = (int) client.player.getX();
                    int currentZ = (int) client.player.getZ();
                    GoalBlock goal3 = new GoalBlock(currentX, y3, currentZ);
                    customGoalProcess.setGoal(goal3);
                }
                break;
                
            case GOAL_CURRENT:
                System.out.println("Setting goal to current position");
                net.minecraft.client.MinecraftClient client2 = net.minecraft.client.MinecraftClient.getInstance();
                if (client2 != null && client2.player != null) {
                    int currentX = (int) client2.player.getX();
                    int currentY = (int) client2.player.getY();
                    int currentZ = (int) client2.player.getZ();
                    GoalBlock goal4 = new GoalBlock(currentX, currentY, currentZ);
                    customGoalProcess.setGoal(goal4);
                }
                break;
                
            case GOAL_CLEAR:
                System.out.println("Clearing current goal");
                customGoalProcess.setGoal(null);
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown GOAL mode: " + mode));
                return;
        }
        
        // Goal setting is immediate, no need to wait
        future.complete(null);
    }
    
    private void executePathCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }

        System.out.println("Executing path command");

        IBaritone baritone = getBaritone();
        if (baritone != null) {
            // Start precise tracking of this task
            PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_PATH, future);

            // Start the Baritone pathing task
            ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
            if (runtimeParameterData != null && runtimeParameterData.targetBlockPos != null) {
                BlockPos target = runtimeParameterData.targetBlockPos;
                customGoalProcess.setGoal(new GoalBlock(target.getX(), target.getY(), target.getZ()));
            }
            customGoalProcess.path();

            // The future will be completed by the PreciseCompletionTracker when the path actually reaches the goal
        } else {
            System.err.println("Baritone not available for path command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
        }
    }
    
    private void executeStopCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for STOP node"));
            return;
        }
        
        IBaritone baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for stop command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        switch (mode) {
            case STOP_NORMAL:
                System.out.println("Executing stop command");
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                baritone.getPathingBehavior().cancelEverything();
                break;
                
            case STOP_CANCEL:
                System.out.println("Executing cancel command");
                // Cancel all pending tasks first
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Stop all Baritone processes
                baritone.getPathingBehavior().cancelEverything();
                break;
                
            case STOP_FORCE:
                System.out.println("Executing force cancel command");
                // Force cancel all tasks
                PreciseCompletionTracker.getInstance().cancelAllTasks();
                // Force stop all Baritone processes
                baritone.getPathingBehavior().cancelEverything();
                break;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown STOP mode: " + mode));
                return;
        }
        
        // Complete immediately since stop is immediate
        future.complete(null);
    }
    
    private void executeInvertCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#invert";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // Invert commands complete immediately
    }

    private void executeComeCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#come";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    private void executeSurfaceCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#surface";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }

    private void executeTunnelCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        String command = "#tunnel";
        System.out.println("Executing command: " + command);

        executeCommand(command);
        future.complete(null); // These commands complete immediately
    }
    
    private void executeFarmCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        if (mode == null) {
            future.completeExceptionally(new RuntimeException("No mode set for FARM node"));
            return;
        }
        
        IBaritone baritone = getBaritone();
        if (baritone == null) {
            System.err.println("Baritone not available for farm command");
            future.completeExceptionally(new RuntimeException("Baritone not available"));
            return;
        }
        
        IFarmProcess farmProcess = baritone.getFarmProcess();
        PreciseCompletionTracker.getInstance().startTrackingTask(PreciseCompletionTracker.TASK_FARM, future);
        
        switch (mode) {
            case FARM_RANGE:
                int range = 10;
                NodeParameter rangeParam = getParameter("Range");
                if (rangeParam != null) {
                    range = rangeParam.getIntValue();
                }
                
                System.out.println("Executing farm within range: " + range);
                farmProcess.farm(range);
                break;
                
            case FARM_WAYPOINT:
                String waypoint = "farm";
                int waypointRange = 10;
                NodeParameter waypointParam = getParameter("Waypoint");
                NodeParameter waypointRangeParam = getParameter("Range");
                
                if (waypointParam != null) {
                    waypoint = waypointParam.getStringValue();
                }
                if (waypointRangeParam != null) {
                    waypointRange = waypointRangeParam.getIntValue();
                }
                
                System.out.println("Executing farm around waypoint: " + waypoint + " with range: " + waypointRange);
                // For waypoint-based farming, we need to use a different approach
                executeCommand("#farm " + waypoint + " " + waypointRange);
                future.complete(null); // Command-based farming completes immediately
                return;
                
            default:
                future.completeExceptionally(new RuntimeException("Unknown FARM mode: " + mode));
                return;
        }
    }
    
    private void executeHotbarCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        int slot = MathHelper.clamp(getIntParameter("Slot", 0), 0, 8);
        client.player.getInventory().setSelectedSlot(slot);
        client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        future.complete(null);
    }
    
    private void executeDropItemCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        boolean dropAll = getBooleanParameter("All", false);
        int count = Math.max(1, getIntParameter("Count", 1));
        double interval = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));

        if (dropAll) {
            count = 1; // Dropping all ignores repeat count
        }

        final int dropIterations = count;
        final boolean dropEntireStack = dropAll;

        new Thread(() -> {
            try {
                for (int i = 0; i < dropIterations; i++) {
                    runOnClientThread(client, () -> {
                        client.player.dropSelectedItem(dropEntireStack);
                        client.player.getInventory().markDirty();
                        client.player.playerScreenHandler.sendContentUpdates();
                    });

                    if (interval > 0.0 && i < dropIterations - 1) {
                        Thread.sleep((long) (interval * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-DropItem").start();
    }
    
    private void executeDropSlotCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int slot = clampInventorySlot(inventory, getIntParameter("Slot", 0));
        boolean entireStack = getBooleanParameter("EntireStack", true);
        int requestedCount = getIntParameter("Count", 0);
        
        ItemStack stack = inventory.getStack(slot);
        if (stack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack removed;
        if (entireStack || requestedCount <= 0 || requestedCount >= stack.getCount()) {
            removed = inventory.removeStack(slot);
        } else {
            removed = inventory.removeStack(slot, requestedCount);
        }
        
        if (!removed.isEmpty()) {
            client.player.dropItem(removed, true);
        }
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeMoveItemCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = clampInventorySlot(inventory, getIntParameter("SourceSlot", 0));
        int targetSlot = clampInventorySlot(inventory, getIntParameter("TargetSlot", 0));
        int requestedCount = getIntParameter("Count", 0);
        
        if (sourceSlot == targetSlot) {
            future.complete(null);
            return;
        }
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack movingStack;
        if (requestedCount <= 0 || requestedCount >= sourceStack.getCount()) {
            movingStack = sourceStack.copy();
            inventory.setStack(sourceSlot, ItemStack.EMPTY);
        } else {
            movingStack = sourceStack.copy();
            movingStack.setCount(requestedCount);
            sourceStack.decrement(requestedCount);
            inventory.setStack(sourceSlot, sourceStack.isEmpty() ? ItemStack.EMPTY : sourceStack);
        }
        
        ItemStack targetStack = inventory.getStack(targetSlot);
        if (targetStack.isEmpty()) {
            inventory.setStack(targetSlot, movingStack);
        } else if (canStacksCombine(targetStack, movingStack)) {
            int transferable = Math.min(targetStack.getMaxCount() - targetStack.getCount(), movingStack.getCount());
            if (transferable > 0) {
                targetStack.increment(transferable);
                movingStack.decrement(transferable);
            }
            if (!movingStack.isEmpty()) {
                if (inventory.getStack(sourceSlot).isEmpty()) {
                    inventory.setStack(sourceSlot, movingStack);
                } else {
                    client.player.dropItem(movingStack, true);
                }
            }
        } else {
            inventory.setStack(targetSlot, movingStack);
            if (inventory.getStack(sourceSlot).isEmpty()) {
                inventory.setStack(sourceSlot, targetStack);
            } else {
                client.player.dropItem(targetStack, true);
            }
        }
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeSwapSlotsCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int firstSlot = clampInventorySlot(inventory, getIntParameter("FirstSlot", 0));
        int secondSlot = clampInventorySlot(inventory, getIntParameter("SecondSlot", 0));
        
        if (firstSlot == secondSlot) {
            future.complete(null);
            return;
        }
        
        ItemStack first = inventory.getStack(firstSlot);
        ItemStack second = inventory.getStack(secondSlot);
        inventory.setStack(firstSlot, second);
        inventory.setStack(secondSlot, first);
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeClearSlotCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int slot = clampInventorySlot(inventory, getIntParameter("Slot", 0));
        boolean dropItems = getBooleanParameter("DropItems", false);
        
        ItemStack removed = inventory.removeStack(slot);
        if (!removed.isEmpty() && dropItems) {
            client.player.dropItem(removed, true);
        }
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeUseCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        int configuredCount = Math.max(0, getIntParameter("RepeatCount", 1));
        boolean useUntilEmpty = getBooleanParameter("UseUntilEmpty", false);
        boolean stopIfUnavailable = getBooleanParameter("StopIfUnavailable", true);
        double durationSeconds = Math.max(0.0, getDoubleParameter("UseDurationSeconds", 0.0));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("UseIntervalSeconds", 0.0));
        boolean allowBlock = getBooleanParameter("AllowBlockInteraction", true);
        boolean allowEntity = getBooleanParameter("AllowEntityInteraction", true);
        boolean swingAfterUse = getBooleanParameter("SwingAfterUse", true);
        boolean sneakWhileUsing = getBooleanParameter("SneakWhileUsing", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);

        if (!useUntilEmpty && configuredCount == 0) {
            future.complete(null);
            return;
        }

        final int maxIterations = configuredCount == 0 ? Integer.MAX_VALUE : configuredCount;

        new Thread(() -> {
            try {
                boolean previousSneak = false;

                if (sneakWhileUsing) {
                    previousSneak = supplyFromClient(client, () -> client.player.isSneaking());
                }

                int iteration = 0;
                while (iteration < maxIterations) {
                    ItemStack stack = supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                    if ((stack == null || stack.isEmpty()) && stopIfUnavailable) {
                        break;
                    }

                    if (sneakWhileUsing) {
                        runOnClientThread(client, () -> {
                            client.player.setSneaking(true);
                            if (client.options != null && client.options.sneakKey != null) {
                                client.options.sneakKey.setPressed(true);
                            }
                        });
                    }

                    runOnClientThread(client, () -> {
                        boolean performed = false;
                        HitResult target = client.crosshairTarget;
                        if (allowEntity && target instanceof EntityHitResult entityHit) {
                            ActionResult entityResult = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
                            performed = entityResult.isAccepted();
                        }
                        if (!performed && allowBlock && target instanceof BlockHitResult blockHit) {
                            ActionResult blockResult = client.interactionManager.interactBlock(client.player, hand, blockHit);
                            performed = blockResult.isAccepted();
                        }
                        if (!performed) {
                            client.interactionManager.interactItem(client.player, hand);
                        }

                        if (durationSeconds > 0.0 && client.options != null && client.options.useKey != null) {
                            client.options.useKey.setPressed(true);
                        }

                        if (swingAfterUse) {
                            client.player.swingHand(hand);
                            if (client.player.networkHandler != null) {
                                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                            }
                        }
                    });

                    if (durationSeconds > 0.0) {
                        Thread.sleep((long) (durationSeconds * 1000));
                        runOnClientThread(client, () -> {
                            if (client.options != null && client.options.useKey != null) {
                                client.options.useKey.setPressed(false);
                            }
                        });
                    }

                    if (sneakWhileUsing && restoreSneak) {
                        boolean sneakState = previousSneak;
                        runOnClientThread(client, () -> {
                            client.player.setSneaking(sneakState);
                            if (client.options != null && client.options.sneakKey != null) {
                                client.options.sneakKey.setPressed(sneakState);
                            }
                        });
                    }

                    if (useUntilEmpty) {
                        ItemStack afterUse = supplyFromClient(client, () -> client.player.getStackInHand(hand).copy());
                        if (afterUse == null || afterUse.isEmpty()) {
                            break;
                        }
                    }

                    iteration++;
                    if (iteration >= maxIterations) {
                        break;
                    }

                    if (intervalSeconds > 0.0) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Use").start();
    }

    private void executePlaceHandCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        boolean sneakWhilePlacing = getBooleanParameter("SneakWhilePlacing", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);
        boolean swingOnPlace = getBooleanParameter("SwingOnPlace", true);
        boolean requireBlockHit = getBooleanParameter("RequireBlockHit", true);

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhilePlacing) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        boolean placed = false;
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit) {
            ActionResult result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            placed = result.isAccepted();
            if (!placed && !requireBlockHit) {
                ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
                placed = fallback.isAccepted();
            }
        } else if (!requireBlockHit) {
            ActionResult fallback = client.interactionManager.interactItem(client.player, hand);
            placed = fallback.isAccepted();
        }

        if (swingOnPlace && placed) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        if (sneakWhilePlacing && restoreSneak) {
            client.player.setSneaking(previousSneak);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(previousSneak);
            }
        }

        future.complete(null);
    }

    private void ensureBlockInHand(net.minecraft.client.MinecraftClient client, String blockId, Hand hand) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": unknown block item.");
        }

        Item targetItem = Registries.ITEM.get(identifier);
        ItemStack current = client.player.getStackInHand(hand);
        if (!current.isEmpty() && current.isOf(targetItem)) {
            return;
        }

        PlayerInventory inventory = client.player.getInventory();
        int slot = findHotbarSlotWithItem(inventory, targetItem);
        if (slot == -1) {
            boolean elsewhere = inventory.contains(new ItemStack(targetItem));
            if (elsewhere) {
                throw new PlacementFailure("Cannot place block \"" + blockId + "\": move it to your hotbar first.");
            }
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": none available in your inventory.");
        }

        if (hand == Hand.MAIN_HAND) {
            if (inventory.getSelectedSlot() != slot) {
                inventory.setSelectedSlot(slot);
                if (client.player.networkHandler != null) {
                    client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                }
            }
            return;
        }

        ItemStack offhandStack = client.player.getOffHandStack();
        if (!offhandStack.isEmpty() && offhandStack.isOf(targetItem)) {
            return;
        }

        inventory.setSelectedSlot(slot);
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private boolean waitForBlockPlacement(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Block desiredBlock) throws InterruptedException {
        if (client == null || targetPos == null || desiredBlock == null) {
            return false;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean matches = supplyFromClient(client, () -> {
                if (client.world == null) {
                    return false;
                }
                return client.world.getBlockState(targetPos).isOf(desiredBlock);
            });
            if (matches) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

    private int findHotbarSlotWithItem(PlayerInventory inventory, Item targetItem) {
        int hotbarSize = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbarSize; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                return slot;
            }
        }
        return -1;
    }

    private BlockHitResult preparePlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, String blockId, Hand hand, double reachSquared) {
        if (client.player == null || client.world == null) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": client world is unavailable.");
        }

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(targetPos);
        if (eyePos.squaredDistanceTo(targetCenter) > reachSquared) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": target is out of reach.");
        }

        if (!isBlockReplaceable(client.world, targetPos)) {
            BlockState occupied = client.world.getBlockState(targetPos);
            throw new PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": target space contains " + describeBlockState(occupied) + "."
            );
        }

        BlockHitResult surface = createPlacementHitResult(client, targetPos, eyePos, reachSquared);
        if (surface == null) {
            throw new PlacementFailure("Cannot place block at " + formatBlockPos(targetPos) + ": no nearby surface to place against.");
        }

        ensureBlockInHand(client, blockId, hand);

        ItemStack stack = client.player.getStackInHand(hand);
        if (stack.isEmpty()) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": the selected hand is empty.");
        }

        Item heldItem = stack.getItem();
        if (!(heldItem instanceof BlockItem blockItem)) {
            throw new PlacementFailure("Cannot place block \"" + blockId + "\": the selected item cannot be placed as a block.");
        }

        if (!canPlaceBlockAt(client, hand, stack, blockItem, surface)) {
            throw new PlacementFailure(
                "Cannot place block at " + formatBlockPos(targetPos) + ": the location is obstructed or lacks support."
            );
        }

        return surface;
    }

    private BlockHitResult createPlacementHitResult(net.minecraft.client.MinecraftClient client, BlockPos targetPos, Vec3d eyePos, double reachSquared) {
        if (client.player == null || client.world == null) {
            return null;
        }

        BlockHitResult bestResult = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos supportPos = targetPos.offset(direction);
            BlockState supportState = client.world.getBlockState(supportPos);
            if (supportState.isAir()) {
                continue;
            }
            if (supportState.getCollisionShape(client.world, supportPos).isEmpty()) {
                continue;
            }

            Direction placementSide = direction.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(supportPos).add(
                placementSide.getOffsetX() * 0.5D,
                placementSide.getOffsetY() * 0.5D,
                placementSide.getOffsetZ() * 0.5D
            );
            Vec3d rayEnd = faceCenter.subtract(
                placementSide.getOffsetX() * 0.001D,
                placementSide.getOffsetY() * 0.001D,
                placementSide.getOffsetZ() * 0.001D
            );

            double distance = eyePos.squaredDistanceTo(rayEnd);
            if (distance > reachSquared) {
                continue;
            }

            RaycastContext context = new RaycastContext(
                eyePos,
                rayEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
            );
            BlockHitResult raycast = client.world.raycast(context);
            if (raycast.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            if (!raycast.getBlockPos().equals(supportPos)) {
                continue;
            }
            if (raycast.getSide() != placementSide) {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestResult = new BlockHitResult(faceCenter, placementSide, supportPos, false);
            }
        }

        return bestResult;
    }

    private boolean canPlaceBlockAt(net.minecraft.client.MinecraftClient client, Hand hand, ItemStack stack, BlockItem blockItem, BlockHitResult hitResult) {
        if (client.player == null || client.world == null) {
            return false;
        }

        ItemPlacementContext placementContext = new ItemPlacementContext(client.player, hand, stack.copy(), hitResult);
        if (!placementContext.canPlace()) {
            return false;
        }

        Block block = blockItem.getBlock();
        BlockState placementState = block.getPlacementState(placementContext);
        if (placementState == null) {
            return false;
        }

        return placementState.canPlaceAt(client.world, placementContext.getBlockPos());
    }

    private String sanitizeResourceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
        String sanitized = UNSAFE_RESOURCE_ID_PATTERN.matcher(lower).replaceAll("");
        int firstColon = sanitized.indexOf(':');
        if (firstColon != -1) {
            int nextColon = sanitized.indexOf(':', firstColon + 1);
            if (nextColon != -1) {
                sanitized = sanitized.substring(0, firstColon + 1) + sanitized.substring(firstColon + 1).replace(':', '_');
            }
        }
        return sanitized;
    }

    private String normalizeResourceId(String value, String defaultNamespace) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains(":")) {
            return defaultNamespace + ":" + trimmed;
        }
        return trimmed;
    }

    private static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(unknown)";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private Block resolveBlockForPlacement(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }

        return Registries.BLOCK.get(identifier);
    }

    private double getPlacementReachSquared(net.minecraft.client.MinecraftClient client) {
        return DEFAULT_REACH_DISTANCE_SQUARED;
    }

    private String describeBlockState(BlockState state) {
        if (state == null) {
            return "an unknown block";
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id != null ? id.toString() : "an unknown block";
    }

    private boolean isBlockReplaceable(net.minecraft.world.World world, BlockPos targetPos) {
        BlockState state = world.getBlockState(targetPos);
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        return state.getCollisionShape(world, targetPos).isEmpty();
    }

    private void executeLookCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.LOOK_ORIENTATION, ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        float yaw = (float) getDoubleParameter("Yaw", client.player.getYaw());
        float pitch = MathHelper.clamp((float) getDoubleParameter("Pitch", client.player.getPitch()), -90.0F, 90.0F);
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
        client.player.setHeadYaw(yaw);
        future.complete(null);
    }

    private void executeTurnCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.TURN_OFFSET, ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        float yawOffset = (float) getDoubleParameter("YawOffset", 0.0D);
        float pitchOffset = (float) getDoubleParameter("PitchOffset", 0.0D);
        float newYaw = client.player.getYaw() + yawOffset;
        float newPitch = MathHelper.clamp(client.player.getPitch() + pitchOffset, -90.0F, 90.0F);
        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
        client.player.setHeadYaw(newYaw);
        future.complete(null);
    }
    
    private void executeJumpCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        int count = Math.max(1, getIntParameter("Count", 1));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));

        new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    runOnClientThread(client, () -> client.player.jump());
                    if (intervalSeconds > 0.0 && i < count - 1) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Jump").start();
    }
    
    private void executeCrouchCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        boolean active = getBooleanParameter("Active", true);
        boolean toggleKey = getBooleanParameter("ToggleKey", false);
        client.player.setSneaking(active);
        if (client.options != null && client.options.sneakKey != null) {
            if (toggleKey) {
                client.options.sneakKey.setPressed(true);
                client.options.sneakKey.setPressed(false);
            } else {
                client.options.sneakKey.setPressed(active);
            }
        }
        future.complete(null);
    }

    private void executeSprintCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        boolean active = getBooleanParameter("Active", true);
        boolean allowFlying = getBooleanParameter("AllowFlying", false);

        if (!allowFlying && client.player.getAbilities() != null && client.player.getAbilities().flying) {
            future.complete(null);
            return;
        }

        boolean previous = client.player.isSprinting();
        client.player.setSprinting(active);
        if (client.player.networkHandler != null && previous != active) {
            ClientCommandC2SPacket.Mode mode = active ? ClientCommandC2SPacket.Mode.START_SPRINTING : ClientCommandC2SPacket.Mode.STOP_SPRINTING;
            client.player.networkHandler.sendPacket(new ClientCommandC2SPacket(client.player, mode));
        }
        future.complete(null);
    }
    
    private void executeInteractCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null || client.world == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        boolean preferEntity = getBooleanParameter("PreferEntity", true);
        boolean preferBlock = getBooleanParameter("PreferBlock", true);
        boolean fallbackToItem = getBooleanParameter("FallbackToItemUse", true);
        boolean swingOnSuccess = getBooleanParameter("SwingOnSuccess", true);
        boolean sneakWhileInteracting = getBooleanParameter("SneakWhileInteracting", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);

        boolean previousSneak = client.player.isSneaking();
        if (sneakWhileInteracting) {
            client.player.setSneaking(true);
            if (client.options != null && client.options.sneakKey != null) {
                client.options.sneakKey.setPressed(true);
            }
        }

        Runnable restoreSneakState = () -> {
            if (sneakWhileInteracting && restoreSneak) {
                client.player.setSneaking(previousSneak);
                if (client.options != null && client.options.sneakKey != null) {
                    client.options.sneakKey.setPressed(previousSneak);
                }
            }
        };

        RuntimeParameterData parameterData = runtimeParameterData;
        BlockPos parameterTargetPos = parameterData != null ? parameterData.targetBlockPos : null;

        NodeParameter blockParameter = getParameter("Block");
        String configuredBlockId = null;
        String requestedBlockLabel = null;
        if (parameterData != null) {
            if (parameterData.targetBlockId != null && !parameterData.targetBlockId.isEmpty()) {
                configuredBlockId = parameterData.targetBlockId;
            } else if (parameterData.targetBlockIds != null && !parameterData.targetBlockIds.isEmpty()) {
                configuredBlockId = parameterData.targetBlockIds.get(0);
            }
        }
        if (blockParameter != null) {
            String value = blockParameter.getStringValue();
            if (value != null && !value.trim().isEmpty()) {
                configuredBlockId = value.trim();
                requestedBlockLabel = value.trim();
            }
        }
        if (requestedBlockLabel == null) {
            requestedBlockLabel = configuredBlockId;
        }

        Block targetBlock = null;
        if (configuredBlockId != null && !configuredBlockId.isEmpty()) {
            String sanitized = sanitizeResourceId(configuredBlockId);
            String normalized = normalizeResourceId(sanitized, "minecraft");
            Identifier identifier = Identifier.tryParse(normalized);
            if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
                restoreSneakState.run();
                String label = requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : configuredBlockId;
                sendNodeErrorMessage(client, "Cannot interact with \"" + label + "\": unknown block identifier.");
                future.complete(null);
                return;
            }
            targetBlock = Registries.BLOCK.get(identifier);
            configuredBlockId = identifier.toString();
            setParameterValueAndPropagate("Block", configuredBlockId);
        }

        HitResult target = client.crosshairTarget;
        ActionResult result = ActionResult.PASS;
        boolean attemptedInteraction = false;

        if (targetBlock != null || parameterTargetPos != null) {
            BlockPos targetPos = parameterTargetPos;
            if (targetPos == null && targetBlock != null) {
                Optional<BlockPos> nearest = findNearestBlock(client, Collections.singletonList(targetBlock), PARAMETER_SEARCH_RADIUS);
                if (nearest.isPresent()) {
                    targetPos = nearest.get();
                }
            }
            if (targetPos == null) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is not nearby for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            BlockState state = client.world.getBlockState(targetPos);
            if (state.isAir()) {
                String name = targetBlock != null ? targetBlock.getName().getString()
                    : (requestedBlockLabel != null && !requestedBlockLabel.isEmpty() ? requestedBlockLabel : "block");
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is missing for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            if (targetBlock == null) {
                targetBlock = state.getBlock();
                Identifier stateId = Registries.BLOCK.getId(targetBlock);
                if (stateId != null) {
                    setParameterValueAndPropagate("Block", stateId.toString());
                }
            }

            if (targetBlock != null && !state.isOf(targetBlock)) {
                String name = targetBlock.getName().getString();
                restoreSneakState.run();
                sendNodeErrorMessage(client, name + " is not nearby for " + type.getDisplayName() + ".");
                future.complete(null);
                return;
            }

            String blockDisplayName = targetBlock.getName().getString();

            if (state.createScreenHandlerFactory(client.world, targetPos) == null) {
                restoreSneakState.run();
                sendNodeErrorMessage(client, blockDisplayName + " cannot be opened.");
                future.complete(null);
                return;
            }

            Vec3d eyePos = client.player.getEyePos();
            Vec3d hitVec = Vec3d.ofCenter(targetPos);
            if (eyePos.squaredDistanceTo(hitVec) > DEFAULT_REACH_DISTANCE_SQUARED) {
                restoreSneakState.run();
                sendNodeErrorMessage(client, blockDisplayName + " is too far away to interact with.");
                future.complete(null);
                return;
            }

            Direction facing = Direction.getFacing(hitVec.x - eyePos.x, hitVec.y - eyePos.y, hitVec.z - eyePos.z);
            BlockHitResult manualHit = new BlockHitResult(hitVec, facing == null ? Direction.UP : facing, targetPos, false);
            target = manualHit;
            result = client.interactionManager.interactBlock(client.player, hand, manualHit);
            attemptedInteraction = true;
        }

        if (!attemptedInteraction && preferEntity && target instanceof EntityHitResult entityHit) {
            result = client.interactionManager.interactEntity(client.player, entityHit.getEntity(), hand);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || !result.isAccepted()) && preferBlock && target instanceof BlockHitResult blockHit) {
            result = client.interactionManager.interactBlock(client.player, hand, blockHit);
            attemptedInteraction = true;
        }

        if ((!attemptedInteraction || (!result.isAccepted() && result != ActionResult.PASS)) && fallbackToItem) {
            result = client.interactionManager.interactItem(client.player, hand);
        }

        if (swingOnSuccess && (result.isAccepted() || result == ActionResult.PASS)) {
            client.player.swingHand(hand);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
        }

        restoreSneakState.run();
        future.complete(null);
    }
    
    private void executeAttackCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.of(ParameterUsage.LOOK_ORIENTATION, ParameterUsage.POSITION), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.interactionManager == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        boolean swingOnly = getBooleanParameter("SwingOnly", false);
        final boolean attackEntities = getBooleanParameter("AttackEntities", true);
        final boolean attackBlocks = getBooleanParameter("AttackBlocks", true);
        int repeatCount = Math.max(1, getIntParameter("RepeatCount", 1));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("AttackIntervalSeconds", 0.0));
        boolean sneakWhileAttacking = getBooleanParameter("SneakWhileAttacking", false);
        boolean restoreSneak = getBooleanParameter("RestoreSneakState", true);

        RuntimeParameterData parameterData = runtimeParameterData;

        orientPlayerTowardsRuntimeTarget(client, parameterData);

        if (!attackEntities && !attackBlocks) {
            swingOnly = true;
        }

        boolean previousSneak = client.player.isSneaking();
        final boolean finalSwingOnly = swingOnly;
        final boolean finalAttackEntities = attackEntities;
        final boolean finalAttackBlocks = attackBlocks;

        new Thread(() -> {
            try {
                if (sneakWhileAttacking) {
                    runOnClientThread(client, () -> {
                        client.player.setSneaking(true);
                        if (client.options != null && client.options.sneakKey != null) {
                            client.options.sneakKey.setPressed(true);
                        }
                    });
                }

                for (int i = 0; i < repeatCount; i++) {
                    runOnClientThread(client, () -> {
                        if (parameterData != null) {
                            if (parameterData.targetEntity != null && !parameterData.targetEntity.isAlive()) {
                                parameterData.targetEntity = null;
                            }
                            orientPlayerTowardsRuntimeTarget(client, parameterData);
                        }

                        boolean performedAttack = false;
                        HitResult target = client.crosshairTarget;
                        if (!finalSwingOnly && finalAttackEntities) {
                            Entity directEntity = null;
                            if (parameterData != null && parameterData.targetEntity != null && parameterData.targetEntity.isAlive()) {
                                directEntity = parameterData.targetEntity;
                            } else if (target instanceof EntityHitResult entityHit) {
                                directEntity = entityHit.getEntity();
                            }

                            if (directEntity != null) {
                                client.interactionManager.attackEntity(client.player, directEntity);
                                performedAttack = true;
                            }
                        }

                        if (!finalSwingOnly && !performedAttack && target instanceof BlockHitResult blockHit && finalAttackBlocks) {
                            client.interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
                            performedAttack = true;
                        }

                        client.player.swingHand(hand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                        }
                    });

                    if (intervalSeconds > 0.0 && i < repeatCount - 1) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }

                if (sneakWhileAttacking && restoreSneak) {
                    runOnClientThread(client, () -> {
                        client.player.setSneaking(previousSneak);
                        if (client.options != null && client.options.sneakKey != null) {
                            client.options.sneakKey.setPressed(previousSneak);
                        }
                    });
                }

                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Attack").start();
    }

    private void executeSwingCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }

        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        int count = Math.max(1, getIntParameter("Count", 1));
        double intervalSeconds = Math.max(0.0, getDoubleParameter("IntervalSeconds", 0.0));

        new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    runOnClientThread(client, () -> {
                        client.player.swingHand(hand);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                        }
                    });

                    if (intervalSeconds > 0.0 && i < count - 1) {
                        Thread.sleep((long) (intervalSeconds * 1000));
                    }
                }
                future.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        }, "Pathmind-Swing").start();
    }
    
    private void executeSwapHandsCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.networkHandler == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        ItemStack mainStack = inventory.getStack(selectedSlot).copy();
        ItemStack offStack = inventory.getStack(PlayerInventory.OFF_HAND_SLOT).copy();
        inventory.setStack(selectedSlot, offStack);
        inventory.setStack(PlayerInventory.OFF_HAND_SLOT, mainStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
        future.complete(null);
    }
    
    private void executeEquipArmorCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = clampInventorySlot(inventory, getIntParameter("SourceSlot", 0));
        EquipmentSlot equipmentSlot = parseEquipmentSlot(getParameter("ArmorSlot"), EquipmentSlot.HEAD);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack current = client.player.getEquippedStack(equipmentSlot);
        inventory.setStack(sourceSlot, current);
        client.player.equipStack(equipmentSlot, sourceStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeUnequipArmorCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        EquipmentSlot equipmentSlot = parseEquipmentSlot(getParameter("ArmorSlot"), EquipmentSlot.HEAD);
        int targetSlot = clampInventorySlot(inventory, getIntParameter("TargetSlot", 0));
        boolean dropIfFull = getBooleanParameter("DropIfFull", true);
        
        ItemStack equipped = client.player.getEquippedStack(equipmentSlot);
        if (equipped.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack targetStack = inventory.getStack(targetSlot);
        if (targetStack.isEmpty()) {
            inventory.setStack(targetSlot, equipped);
            client.player.equipStack(equipmentSlot, ItemStack.EMPTY);
        } else if (dropIfFull) {
            client.player.dropItem(equipped.copy(), true);
            client.player.equipStack(equipmentSlot, ItemStack.EMPTY);
        } else {
            client.player.equipStack(equipmentSlot, targetStack);
            inventory.setStack(targetSlot, equipped);
        }
        
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeEquipHandCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        int sourceSlot = clampInventorySlot(inventory, getIntParameter("SourceSlot", 0));
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        
        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack handStack = client.player.getStackInHand(hand);
        client.player.setStackInHand(hand, sourceStack);
        inventory.setStack(sourceSlot, handStack);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void executeUnequipHandCommand(CompletableFuture<Void> future) {
        if (preprocessAttachedParameter(EnumSet.noneOf(ParameterUsage.class), future) == ParameterHandlingResult.COMPLETE) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            future.completeExceptionally(new RuntimeException("Minecraft client not available"));
            return;
        }
        
        PlayerInventory inventory = client.player.getInventory();
        Hand hand = resolveHand(getParameter("Hand"), Hand.MAIN_HAND);
        int targetSlot = clampInventorySlot(inventory, getIntParameter("TargetSlot", 0));
        boolean dropIfFull = getBooleanParameter("DropIfFull", true);
        
        ItemStack handStack = client.player.getStackInHand(hand);
        if (handStack.isEmpty()) {
            future.complete(null);
            return;
        }
        
        ItemStack targetStack = inventory.getStack(targetSlot);
        if (targetStack.isEmpty()) {
            inventory.setStack(targetSlot, handStack);
        } else if (dropIfFull) {
            client.player.dropItem(handStack.copy(), true);
        } else {
            inventory.setStack(targetSlot, handStack);
            client.player.setStackInHand(hand, targetStack);
            inventory.markDirty();
            client.player.playerScreenHandler.sendContentUpdates();
            future.complete(null);
            return;
        }
        
        client.player.setStackInHand(hand, ItemStack.EMPTY);
        inventory.markDirty();
        client.player.playerScreenHandler.sendContentUpdates();
        future.complete(null);
    }
    
    private void completeSensorEvaluation(CompletableFuture<Void> future) {
        boolean result = evaluateSensor();
        setNextOutputSocket(result ? 0 : 1);
        future.complete(null);
    }

    private void runOnClientThread(net.minecraft.client.MinecraftClient client, Runnable task) throws InterruptedException {
        if (client == null || client.isOnThread()) {
            task.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
    }

    private <T> T supplyFromClient(net.minecraft.client.MinecraftClient client, java.util.function.Supplier<T> supplier) throws InterruptedException {
        if (client == null || client.isOnThread()) {
            return supplier.get();
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        client.execute(() -> {
            try {
                result.set(supplier.get());
            } catch (RuntimeException e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    private boolean canStacksCombine(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (!ItemStack.areItemsEqual(first, second)) {
            return false;
        }
        return first.getComponents().equals(second.getComponents());
    }

    private int clampInventorySlot(PlayerInventory inventory, int slot) {
        return MathHelper.clamp(slot, 0, inventory.size() - 1);
    }

    private EquipmentSlot parseEquipmentSlot(NodeParameter parameter, EquipmentSlot defaultSlot) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultSlot;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "head":
            case "helmet":
                return EquipmentSlot.HEAD;
            case "chest":
            case "chestplate":
                return EquipmentSlot.CHEST;
            case "legs":
            case "leggings":
                return EquipmentSlot.LEGS;
            case "feet":
            case "boots":
                return EquipmentSlot.FEET;
            default:
                return defaultSlot;
        }
    }

    private int getIntParameter(String name, int defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.INTEGER) {
            return param.getIntValue();
        }
        try {
            return Integer.parseInt(param.getStringValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private String getStringParameter(String name, String defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String value = param.getStringValue();
        return value != null ? value : defaultValue;
    }

    private static String getParameterString(Node node, String name) {
        if (node == null || name == null) {
            return null;
        }
        NodeParameter parameter = node.getParameter(name);
        if (parameter == null) {
            return null;
        }
        return parameter.getStringValue();
    }

    private double getDoubleParameter(String name, double defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.DOUBLE) {
            return param.getDoubleValue();
        }
        try {
            return Double.parseDouble(param.getStringValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getBooleanParameter(String name, boolean defaultValue) {
        NodeParameter param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        if (param.getType() == ParameterType.BOOLEAN) {
            return param.getBoolValue();
        }
        String value = param.getStringValue();
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Optional<BlockPos> findNearestDroppedItem(net.minecraft.client.MinecraftClient client, Item item, double range) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<ItemEntity> entities = client.world.getEntitiesByClass(ItemEntity.class, searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty() && entity.getStack().isOf(item));
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        ItemEntity nearest = Collections.min(entities, Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return Optional.of(nearest.getBlockPos());
    }

    private Optional<Entity> findNearestEntity(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, double range) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        List<Entity> matches = client.world.getOtherEntities(client.player, searchBox, entity -> entity.getType() == entityType);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        Entity nearest = Collections.min(matches, Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        return Optional.of(nearest);
    }
    
    private Hand resolveHand(NodeParameter parameter, Hand defaultHand) {
        if (parameter == null || parameter.getStringValue() == null) {
            return defaultHand;
        }
        String value = parameter.getStringValue().trim().toLowerCase(Locale.ROOT);
        if (value.equals("off") || value.equals("offhand") || value.equals("off_hand") || value.equals("off-hand")) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private void resetControlState() {
        this.repeatRemainingIterations = 0;
        this.repeatActive = false;
        this.lastSensorResult = false;
        this.nextOutputSocket = 0;
    }
    
    private enum SensorConditionType {
        TOUCHING_BLOCK("Touching Block"),
        TOUCHING_ENTITY("Touching Entity"),
        AT_COORDINATES("At Coordinates");

        private final String label;

        SensorConditionType(String label) {
            this.label = label;
        }

        static SensorConditionType fromLabel(String label) {
            if (label == null) {
                return TOUCHING_BLOCK;
            }
            String trimmed = label.trim();
            for (SensorConditionType type : values()) {
                if (type.label.equalsIgnoreCase(trimmed)) {
                    return type;
                }
            }
            return TOUCHING_BLOCK;
        }
    }

    private Node getAttachedParameterOfType(NodeType... allowedTypes) {
        if (attachedParameters.isEmpty()) {
            return null;
        }
        List<Integer> slotIndices = new ArrayList<>(attachedParameters.keySet());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachedParameters.get(slotIndex);
            if (parameter == null || !parameter.isParameterNode()) {
                continue;
            }
            NodeType parameterType = parameter.getType();
            for (NodeType allowed : allowedTypes) {
                if (parameterType == allowed) {
                    return parameter;
                }
            }
        }
        Node fallback = getAttachedParameter();
        if (fallback != null) {
            sendIncompatibleParameterMessage(fallback);
        }
        return null;
    }

    public boolean evaluateSensor() {
        if (!isSensorNode()) {
            return false;
        }

        boolean result;
        switch (type) {
            case SENSOR_TOUCHING_BLOCK: {
                String blockId = getStringParameter("Block", "minecraft:stone");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_BLOCK, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    String nodeBlock = getParameterString(parameterNode, "Block");
                    if (nodeBlock != null && !nodeBlock.isEmpty()) {
                        blockId = nodeBlock;
                    }
                }
                result = evaluateSensorCondition(SensorConditionType.TOUCHING_BLOCK, blockId, null, 0, 0, 0);
                break;
            }
            case SENSOR_TOUCHING_ENTITY: {
                String entityId = getStringParameter("Entity", "minecraft:zombie");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_ENTITY);
                if (parameterNode != null) {
                    String nodeEntity = getParameterString(parameterNode, "Entity");
                    if (nodeEntity != null && !nodeEntity.isEmpty()) {
                        entityId = nodeEntity;
                    }
                }
                result = evaluateSensorCondition(SensorConditionType.TOUCHING_ENTITY, null, entityId, 0, 0, 0);
                break;
            }
            case SENSOR_AT_COORDINATES: {
                int x = getIntParameter("X", 0);
                int y = getIntParameter("Y", 64);
                int z = getIntParameter("Z", 0);
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_COORDINATE, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    x = parseNodeInt(parameterNode, "X", x);
                    y = parseNodeInt(parameterNode, "Y", y);
                    z = parseNodeInt(parameterNode, "Z", z);
                }
                result = evaluateSensorCondition(SensorConditionType.AT_COORDINATES, null, null, x, y, z);
                break;
            }
            case SENSOR_BLOCK_AHEAD: {
                String blockId = getStringParameter("Block", "minecraft:stone");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_BLOCK, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    String nodeBlock = getParameterString(parameterNode, "Block");
                    if (nodeBlock != null && !nodeBlock.isEmpty()) {
                        blockId = nodeBlock;
                    }
                }
                result = isBlockAhead(blockId);
                break;
            }
            case SENSOR_BLOCK_BELOW: {
                String blockId = getStringParameter("Block", "minecraft:stone");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_BLOCK, NodeType.PARAM_PLACE_TARGET);
                if (parameterNode != null) {
                    String nodeBlock = getParameterString(parameterNode, "Block");
                    if (nodeBlock != null && !nodeBlock.isEmpty()) {
                        blockId = nodeBlock;
                    }
                }
                result = isBlockBelow(blockId);
                break;
            }
            case SENSOR_LIGHT_LEVEL_BELOW: {
                int threshold = MathHelper.clamp(getIntParameter("Threshold", 7), 0, 15);
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_LIGHT_THRESHOLD);
                if (parameterNode != null) {
                    threshold = MathHelper.clamp(parseNodeInt(parameterNode, "Threshold", threshold), 0, 15);
                }
                result = isLightLevelBelow(threshold);
                break;
            }
            case SENSOR_IS_DAYTIME:
                result = isDaytime();
                break;
            case SENSOR_IS_RAINING:
                result = isRaining();
                break;
            case SENSOR_HEALTH_BELOW: {
                double amount = MathHelper.clamp(getDoubleParameter("Amount", 10.0), 0.0, 40.0);
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_HEALTH_THRESHOLD);
                if (parameterNode != null) {
                    amount = MathHelper.clamp(parseNodeDouble(parameterNode, "Amount", amount), 0.0, 40.0);
                }
                result = isHealthBelow(amount);
                break;
            }
            case SENSOR_HUNGER_BELOW: {
                int amount = MathHelper.clamp(getIntParameter("Amount", 10), 0, 20);
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_HUNGER_THRESHOLD);
                if (parameterNode != null) {
                    amount = MathHelper.clamp(parseNodeInt(parameterNode, "Amount", amount), 0, 20);
                }
                result = isHungerBelow(amount);
                break;
            }
            case SENSOR_ENTITY_NEARBY: {
                String entityId = getStringParameter("Entity", "minecraft:zombie");
                double range = Math.max(1.0, getIntParameter("Range", 6));
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_ENTITY);
                if (parameterNode != null) {
                    String nodeEntity = getParameterString(parameterNode, "Entity");
                    if (nodeEntity != null && !nodeEntity.isEmpty()) {
                        entityId = nodeEntity;
                    }
                    range = Math.max(1.0, parseNodeDouble(parameterNode, "Range", range));
                }
                result = isEntityNearby(entityId, range);
                break;
            }
            case SENSOR_ITEM_IN_INVENTORY: {
                String itemId = getStringParameter("Item", "minecraft:stone");
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_ITEM);
                if (parameterNode != null) {
                    String nodeItem = getParameterString(parameterNode, "Item");
                    if (nodeItem != null && !nodeItem.isEmpty()) {
                        itemId = nodeItem;
                    }
                }
                result = hasItemInInventory(itemId);
                break;
            }
            case SENSOR_IS_SWIMMING:
                result = isSwimming();
                break;
            case SENSOR_IS_IN_LAVA:
                result = isInLava();
                break;
            case SENSOR_IS_UNDERWATER:
                result = isUnderwater();
                break;
            case SENSOR_IS_FALLING: {
                double distance = Math.max(0.0, getDoubleParameter("Distance", 2.0));
                Node parameterNode = getAttachedParameterOfType(NodeType.PARAM_FALL_DISTANCE);
                if (parameterNode != null) {
                    distance = Math.max(0.0, parseNodeDouble(parameterNode, "Distance", distance));
                }
                result = isFalling(distance);
                break;
            }
            case SENSOR_IS_RENDERED: {
                String resourceId = getStringParameter("Resource", "minecraft:stone");
                Node parameterNode = getAttachedParameterOfType(
                    NodeType.PARAM_BLOCK,
                    NodeType.PARAM_BLOCK_LIST,
                    NodeType.PARAM_ITEM,
                    NodeType.PARAM_ENTITY,
                    NodeType.PARAM_PLAYER,
                    NodeType.PARAM_PLACE_TARGET
                );
                if (parameterNode != null) {
                    NodeType parameterType = parameterNode.getType();
                    switch (parameterType) {
                        case PARAM_ITEM: {
                            String nodeItem = getParameterString(parameterNode, "Item");
                            if (nodeItem != null && !nodeItem.isEmpty()) {
                                resourceId = nodeItem;
                            }
                            break;
                        }
                        case PARAM_ENTITY: {
                            String nodeEntity = getParameterString(parameterNode, "Entity");
                            if (nodeEntity != null && !nodeEntity.isEmpty()) {
                                resourceId = nodeEntity;
                            }
                            break;
                        }
                        case PARAM_PLAYER: {
                            String nodePlayer = getParameterString(parameterNode, "Player");
                            if (nodePlayer != null && !nodePlayer.isEmpty()) {
                                resourceId = nodePlayer;
                            }
                            break;
                        }
                        case PARAM_BLOCK_LIST: {
                            String nodeBlocks = getParameterString(parameterNode, "Blocks");
                            if (nodeBlocks != null && !nodeBlocks.isEmpty()) {
                                resourceId = nodeBlocks;
                            }
                            break;
                        }
                        default: {
                            String nodeBlock = getParameterString(parameterNode, "Block");
                            if (nodeBlock != null && !nodeBlock.isEmpty()) {
                                resourceId = nodeBlock;
                            }
                            break;
                        }
                    }
                }
                result = isResourceRendered(resourceId);
                break;
            }
            default:
                result = false;
                break;
        }

        this.lastSensorResult = result;
        return result;
    }

    private boolean evaluateConditionFromParameters() {
        if (attachedSensor != null) {
            boolean result = attachedSensor.evaluateSensor();
            this.lastSensorResult = result;
            return result;
        }

        // Legacy fallback when no sensor is attached
        String condition = getStringParameter("Condition", "Touching Block");
        String blockId = getStringParameter("Block", "minecraft:stone");
        String entityId = getStringParameter("Entity", "minecraft:zombie");
        int x = getIntParameter("X", 0);
        int y = getIntParameter("Y", 64);
        int z = getIntParameter("Z", 0);
        boolean result = evaluateSensorCondition(SensorConditionType.fromLabel(condition), blockId, entityId, x, y, z);
        this.lastSensorResult = result;
        return result;
    }
    
    private boolean evaluateSensorCondition(SensorConditionType type, String blockId, String entityId, int x, int y, int z) {
        if (type == null) {
            type = SensorConditionType.TOUCHING_BLOCK;
        }
        switch (type) {
            case TOUCHING_BLOCK:
                return isTouchingBlock(blockId);
            case TOUCHING_ENTITY:
                return isTouchingEntity(entityId);
            case AT_COORDINATES:
                return isAtCoordinates(x, y, z);
            default:
                return false;
        }
    }
    
    private boolean isTouchingBlock(String blockId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || blockId == null || blockId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return false;
        }
        Block block = Registries.BLOCK.get(identifier);
        Box box = client.player.getBoundingBox().expand(0.05);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.floor(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    mutable.set(bx, by, bz);
                    if (client.player.getWorld().getBlockState(mutable).isOf(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean isTouchingEntity(String entityId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(entityId);
        if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
            return false;
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
        List<Entity> entities = client.player.getWorld().getOtherEntities(
            client.player,
            client.player.getBoundingBox().expand(0.15),
            entity -> entity.getType() == entityType
        );
        return !entities.isEmpty();
    }
    
    private boolean isAtCoordinates(int x, int y, int z) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.getBlockPos();
        return playerPos.getX() == x && playerPos.getY() == y && playerPos.getZ() == z;
    }

    private boolean isBlockAhead(String blockId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || blockId == null || blockId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return false;
        }
        Block block = Registries.BLOCK.get(identifier);
        Direction facing = client.player.getHorizontalFacing();
        BlockPos targetPos = client.player.getBlockPos().offset(facing);
        return client.player.getWorld().getBlockState(targetPos).isOf(block);
    }

    private boolean isBlockBelow(String blockId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || blockId == null || blockId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return false;
        }
        Block block = Registries.BLOCK.get(identifier);
        BlockPos below = client.player.getBlockPos().down();
        return client.player.getWorld().getBlockState(below).isOf(block);
    }

    private boolean isLightLevelBelow(int threshold) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getWorld() == null) {
            return false;
        }
        BlockPos pos = client.player.getBlockPos();
        return client.player.getWorld().getLightLevel(pos) < threshold;
    }

    private boolean isDaytime() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return false;
        }
        long time = client.world.getTimeOfDay() % 24000L;
        return time < 12000L;
    }

    private boolean isRaining() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return false;
        }
        return client.world.isRaining() || client.world.hasRain(client.player.getBlockPos());
    }

    private boolean isHealthBelow(double amount) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHealth() < amount;
    }

    private boolean isHungerBelow(int amount) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.getHungerManager().getFoodLevel() < amount;
    }

    private boolean isEntityNearby(String entityId, double range) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || entityId == null || entityId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(entityId);
        if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
            return false;
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
        Box searchBox = client.player.getBoundingBox().expand(range);
        List<Entity> entities = client.player.getWorld().getOtherEntities(
            client.player,
            searchBox,
            entity -> entity.getType() == entityType
        );
        return !entities.isEmpty();
    }

    private boolean hasItemInInventory(String itemId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return false;
        }
        net.minecraft.item.Item item = Registries.ITEM.get(identifier);
        return client.player.getInventory().count(item) > 0;
    }

    private boolean isResourceRendered(String resourceId) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        String trimmed = resourceId.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.indexOf(',') >= 0) {
            String[] parts = trimmed.split(",");
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty() && isResourceRendered(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        return isSingleResourceRendered(client, trimmed);
    }

    private boolean isSingleResourceRendered(net.minecraft.client.MinecraftClient client, String resourceId) {
        if (client == null || client.player == null || client.world == null || resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        String normalized = resourceId.contains(":")
            ? resourceId.toLowerCase(Locale.ROOT)
            : resourceId;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier != null) {
            if (Registries.BLOCK.containsId(identifier)) {
                Block block = Registries.BLOCK.get(identifier);
                return isBlockRendered(client, block);
            }
            if (Registries.ITEM.containsId(identifier)) {
                Item item = Registries.ITEM.get(identifier);
                return isItemRendered(client, item);
            }
            if (Registries.ENTITY_TYPE.containsId(identifier)) {
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                return isEntityRendered(client, entityType);
            }
        }
        return isPlayerRendered(client, resourceId);
    }

    private boolean isBlockRendered(net.minecraft.client.MinecraftClient client, Block block) {
        if (client == null || client.player == null || client.world == null || block == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            if (client.world.getBlockState(hitPos).isOf(block)) {
                return true;
            }
        }

        BlockPos playerPos = client.player.getBlockPos();
        int viewDistance = client.options.getViewDistance().getValue();
        int horizontalRadius = MathHelper.clamp(viewDistance * 4, 8, 48);
        int verticalRadius = MathHelper.clamp(viewDistance * 2, 6, 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    BlockState state = client.world.getBlockState(mutable);
                    if (state.isOf(block) && isBlockVisible(client, mutable)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockVisible(net.minecraft.client.MinecraftClient client, BlockPos pos) {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d target = Vec3d.ofCenter(pos);
        RaycastContext context = new RaycastContext(
            cameraPos,
            target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            client.player
        );
        BlockHitResult hit = client.world.raycast(context);
        if (hit == null) {
            return false;
        }
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private boolean isItemRendered(net.minecraft.client.MinecraftClient client, Item item) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return false;
        }

        if (client.player.getMainHandStack().isOf(item) || client.player.getOffHandStack().isOf(item)) {
            return true;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity targetEntity = entityHit.getEntity();
            if (targetEntity instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty() && itemEntity.getStack().isOf(item)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<ItemEntity> candidates = client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item) && client.player.canSee(entity)
        );
        return !candidates.isEmpty();
    }

    private boolean isEntityRendered(net.minecraft.client.MinecraftClient client, EntityType<?> entityType) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() != null && entityHit.getEntity().getType() == entityType) {
            return true;
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        List<Entity> matches = client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null && entity.isAlive() && entity.getType() == entityType && client.player.canSee(entity)
        );
        return !matches.isEmpty();
    }

    private boolean isPlayerRendered(net.minecraft.client.MinecraftClient client, String playerName) {
        if (client == null || client.player == null || client.world == null || playerName == null || playerName.isEmpty()) {
            return false;
        }

        String trimmed = playerName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractClientPlayerEntity targetPlayer) {
            if (targetPlayer.getGameProfile().getName().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }

        double renderDistance = Math.max(8.0, client.options.getViewDistance().getValue() * 4.0);
        for (AbstractClientPlayerEntity playerEntity : client.world.getPlayers()) {
            if (playerEntity == null || !playerEntity.isAlive()) {
                continue;
            }
            if (!playerEntity.getGameProfile().getName().equalsIgnoreCase(trimmed)) {
                continue;
            }
            if (playerEntity.squaredDistanceTo(client.player) > renderDistance * renderDistance) {
                continue;
            }
            if (client.player.canSee(playerEntity)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSwimming() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSwimming();
    }

    private boolean isInLava() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isInLava();
    }

    private boolean isUnderwater() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        return client != null && client.player != null && client.player.isSubmergedInWater();
    }

    private boolean isFalling(double distance) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        return client.player.fallDistance >= distance && !client.player.isOnGround();
    }
    
    private void executeCommand(String command) {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.networkHandler.sendChatMessage(command);
                System.out.println("Sent command to Minecraft: " + command);
            } else {
                System.out.println("Cannot execute command - client or player is null");
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
}
