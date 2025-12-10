package com.pathmind.nodes;

/**
 * Enum representing different types of nodes in the Pathmind visual editor.
 * Similar to Blender's shader nodes, each type has specific properties and behaviors.
 */
public enum NodeType {
    // Event nodes
    START("Start", 0xFF4CAF50, "Begins the automation sequence"),
    EVENT_FUNCTION("Function", 0xFFE91E63, "Runs a named function body when triggered"),
    EVENT_CALL("Call Function", 0xFFE91E63, "Triggers the execution of a named function"),
    
    // Navigation Commands
    GOTO("Goto", 0xFF00BCD4, "Moves to specified coordinates"),
    GOAL("Goal", 0xFF2196F3, "Sets a goal at specified coordinates"),
    PATH("Path", 0xFF03DAC6, "Initiates pathfinding to the set goal"),
    STOP("Stop", 0xFFF44336, "Stops the current pathfinding task"),
    INVERT("Invert", 0xFFFF5722, "Inverts the current goal and path"),
    COME("Come", 0xFF9C27B0, "Moves towards the camera's direction"),
    SURFACE("Surface", 0xFF4CAF50, "Moves to the nearest surface"),
    
    // Resource collection and Building Commands
    COLLECT("Mine", 0xFF2196F3, "Configures mining-related options"),
    BUILD("Build", 0xFFFF9800, "Constructs structures from schematic files"),
    TUNNEL("Tunnel", 0xFF795548, "Digs a 2x3 tunnel forward automatically"),
    FARM("Farm", 0xFF4CAF50, "Automates harvesting and replanting crops"),
    PLACE("Place", 0xFF9C27B0, "Places blocks at specified coordinates"),
    CRAFT("Craft", 0xFFFF9800, "Crafts items using available materials"),
    
    // Exploration Commands
    EXPLORE("Explore", 0xFF673AB7, "Explores the world from origin coordinates"),
    FOLLOW("Follow", 0xFF3F51B5, "Follows a specified player"),
    
    // Control flow Commands
    CONTROL_REPEAT("Repeat", 0xFFFFC107, "Repeat enclosed nodes a set number of times"),
    CONTROL_REPEAT_UNTIL("Repeat Until", 0xFFFFC107, "Repeat until a condition becomes true"),
    CONTROL_FOREVER("Forever", 0xFFFFC107, "Loop enclosed nodes indefinitely"),
    CONTROL_IF("If", 0xFFFFC107, "Run the next branch only when a condition is true"),
    CONTROL_IF_ELSE("If Else", 0xFFFFC107, "Run one of two branches depending on a condition"),

    // Player movement commands
    LOOK("Look", 0xFF03A9F4, "Adjusts the player's view direction"),
    JUMP("Jump", 0xFF009688, "Makes the player jump"),
    CROUCH("Crouch", 0xFF607D8B, "Toggles crouching"),
    SPRINT("Sprint", 0xFFFFEB3B, "Toggles sprinting"),
    TURN("Turn", 0xFF4FC3F7, "Rotates the player by a relative angle"),
    
    // Player combat commands
    ATTACK("Attack", 0xFFE53935, "Attacks the targeted block or entity"),
    SWING("Swing", 0xFFFF7043, "Performs a hand swing without interaction"),
    
    // Player interaction commands
    USE("Use", 0xFF8BC34A, "Uses the selected hand with fine-grained control"),
    INTERACT("Interact", 0xFF4DB6AC, "Interacts with the targeted block or entity"),
    PLACE_HAND("Place", 0xFFBA68C8, "Places a block from the selected hand"),

    // GUI Commands
    HOTBAR("Hotbar Slot", 0xFFCDDC39, "Selects a hotbar slot"),
    DROP_ITEM("Drop Item", 0xFFFFAB91, "Drops the currently selected item"),
    DROP_SLOT("Drop Slot", 0xFFFF7043, "Drops items from a specific slot"),
    MOVE_ITEM("Move Item", 0xFFFFB74D, "Moves items between inventory slots"),
    CLEAR_SLOT("Clear Slot", 0xFFB0BEC5, "Clears a slot and optionally drops its contents"),
    OPEN_INVENTORY("Open Inventory", 0xFFB0BEC5, "Opens the player's inventory screen"),
    CLOSE_GUI("Close GUI", 0xFFB0BEC5, "Closes the currently open GUI"),

    // Equipment Commands
    EQUIP_ARMOR("Equip Armor", 0xFF7E57C2, "Equips armor from an inventory slot"),
    UNEQUIP_ARMOR("Unequip Armor", 0xFF9575CD, "Moves armor into an inventory slot"),
    EQUIP_HAND("Equip Hand", 0xFF5C6BC0, "Moves an inventory item into a hand"),
    UNEQUIP_HAND("Unequip Hand", 0xFF7986CB, "Moves a hand item into an inventory slot"),
    SWAP_HANDS("Swap Hands", 0xFFA1887F, "Swaps the items between hands"),
    
    // Sensor commands
    SENSOR_TOUCHING_BLOCK("Touching Block", 0xFF64B5F6, "Detect if player is touching a specific block"),
    SENSOR_TOUCHING_ENTITY("Touching Entity", 0xFF64B5F6, "Detect if player is touching an entity"),
    SENSOR_AT_COORDINATES("At Coordinates", 0xFF64B5F6, "Detect if player is at specific coordinates"),
    SENSOR_BLOCK_AHEAD("Block Ahead", 0xFF64B5F6, "Detect if a specific block is directly in front of the player"),
    SENSOR_BLOCK_BELOW("Block Below", 0xFF64B5F6, "Detect if a specific block is beneath the player"),
    SENSOR_LIGHT_LEVEL_BELOW("Light Below", 0xFF64B5F6, "Detect if the ambient light level is below a threshold"),
    SENSOR_IS_DAYTIME("Is Daytime", 0xFF64B5F6, "Detect if it is currently daytime"),
    SENSOR_IS_RAINING("Is Raining", 0xFF64B5F6, "Detect if it is raining or snowing"),
    SENSOR_HEALTH_BELOW("Health Below", 0xFF64B5F6, "Detect if player health is below a threshold"),
    SENSOR_HUNGER_BELOW("Hunger Below", 0xFF64B5F6, "Detect if player hunger is below a threshold"),
    SENSOR_ENTITY_NEARBY("Entity Nearby", 0xFF64B5F6, "Detect if an entity type is within range"),
    SENSOR_ITEM_IN_INVENTORY("Has Item", 0xFF64B5F6, "Detect if the player has a specific item"),
    SENSOR_IS_SWIMMING("Is Swimming", 0xFF64B5F6, "Detect if the player is swimming"),
    SENSOR_IS_IN_LAVA("In Lava", 0xFF64B5F6, "Detect if the player is touching lava"),
    SENSOR_IS_UNDERWATER("Underwater", 0xFF64B5F6, "Detect if the player is fully submerged"),
    SENSOR_IS_FALLING("Is Falling", 0xFF64B5F6, "Detect if the player is currently falling"),
    SENSOR_IS_RENDERED("Is Rendered", 0xFF64B5F6, "Detect if a specified block or item is currently visible to the player"),

    // Utility Commands
    SCREEN_CONTROL("Screen Control", 0xFF9E9E9E, "Open or close in-game screens"),
    WAIT("Wait", 0xFF607D8B, "Waits for specified duration"),
    MESSAGE("Message", 0xFF9E9E9E, "Sends a chat message"),

    // Parameter nodes
    PARAM_COORDINATE("Coordinate", 0xFF8BC34A, "Reusable XYZ coordinate data"),
    PARAM_BLOCK("Block", 0xFF8BC34A, "Represents a single block type"),
    PARAM_BLOCK_LIST("Block List", 0xFF8BC34A, "Represents multiple block types"),
    PARAM_ITEM("Item", 0xFF8BC34A, "Represents an item and quantity"),
    PARAM_ENTITY("Entity", 0xFF8BC34A, "Represents an entity and range"),
    PARAM_PLAYER("Player", 0xFF8BC34A, "Represents a specific player target"),
    PARAM_WAYPOINT("Waypoint", 0xFF8BC34A, "Represents a waypoint name and optional range"),
    PARAM_SCHEMATIC("Schematic", 0xFF8BC34A, "Represents schematic placement data"),
    PARAM_INVENTORY_SLOT("Inventory Slot", 0xFF8BC34A, "Represents an inventory slot reference"),
    PARAM_MESSAGE("Message Text", 0xFF8BC34A, "Represents reusable chat or sign text"),
    PARAM_DURATION("Duration", 0xFF8BC34A, "Represents a duration or interval"),
    PARAM_BOOLEAN("Toggle", 0xFF8BC34A, "Represents a boolean toggle value"),
    PARAM_HAND("Hand", 0xFF8BC34A, "Represents a preferred hand selection"),
    PARAM_RANGE("Range", 0xFF8BC34A, "Represents a generic radius or range"),
    PARAM_ROTATION("Rotation", 0xFF8BC34A, "Represents yaw and pitch angles"),
    PARAM_PLACE_TARGET("Place", 0xFF8BC34A, "Represents a block placement with coordinates"),
    PARAM_CLOSEST("Closest", 0xFF8BC34A, "Represents the nearest open block location");

    private final String displayName;
    private final String description;

    NodeType(String displayName, int color, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        // Special nodes keep their original colors
        if (this == START) {
            return 0xFF4CAF50; // Green
        }
        return getCategory().getColor();
    }

    public String getDescription() {
        return description;
    }

    public boolean isInputNode() {
        return this == START;
    }

    public boolean isOutputNode() {
        return false;
    }

    public boolean isDraggableFromSidebar() {
        return true; // All nodes including START can be dragged from sidebar
    }
    
    /**
     * Get the category this node belongs to for sidebar organization
     */
    public NodeCategory getCategory() {
        switch (this) {
            case START:
            case EVENT_FUNCTION:
            case EVENT_CALL:
                return NodeCategory.EVENTS;
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_FOREVER:
            case CONTROL_IF:
            case CONTROL_IF_ELSE:
                return NodeCategory.LOGIC;
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
                return NodeCategory.SENSORS;
            case GOTO:
            case GOAL:
            case PATH:
            case STOP:
            case INVERT:
            case COME:
            case SURFACE:
            case EXPLORE:
            case FOLLOW:
            case LOOK:
            case JUMP:
            case CROUCH:
            case SPRINT:
            case TURN:
                return NodeCategory.MOVEMENT;
            case COLLECT:
            case BUILD:
            case TUNNEL:
            case FARM:
            case PLACE:
            case CRAFT:
                return NodeCategory.WORLD;
            case ATTACK:
            case SWING:
            case USE:
            case INTERACT:
            case PLACE_HAND:
                return NodeCategory.INTERACTION;
            case HOTBAR:
            case DROP_ITEM:
            case DROP_SLOT:
            case MOVE_ITEM:
            case CLEAR_SLOT:
            case OPEN_INVENTORY:
            case CLOSE_GUI:
            case EQUIP_ARMOR:
            case UNEQUIP_ARMOR:
            case EQUIP_HAND:
            case UNEQUIP_HAND:
            case SWAP_HANDS:
                return NodeCategory.GUI;
            case SCREEN_CONTROL:
            case WAIT:
            case MESSAGE:
                return NodeCategory.UTILITY;
            case PARAM_COORDINATE:
            case PARAM_BLOCK:
            case PARAM_BLOCK_LIST:
            case PARAM_ITEM:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_WAYPOINT:
            case PARAM_SCHEMATIC:
            case PARAM_INVENTORY_SLOT:
            case PARAM_MESSAGE:
            case PARAM_DURATION:
            case PARAM_BOOLEAN:
            case PARAM_HAND:
            case PARAM_RANGE:
            case PARAM_ROTATION:
            case PARAM_PLACE_TARGET:
            case PARAM_CLOSEST:
                return NodeCategory.PARAMETERS;
            default:
                return NodeCategory.UTILITY;
        }
    }
    
    /**
     * Check if this node type requires parameters
     */
    public boolean hasParameters() {
        switch (this) {
            case EVENT_FUNCTION:
            case EVENT_CALL:
            case GOTO:
            case GOAL:
            case COLLECT:
            case PLACE:
            case CRAFT:
            case BUILD:
            case EXPLORE:
            case FOLLOW:
            case WAIT:
            case MESSAGE:
            case HOTBAR:
            case DROP_ITEM:
            case USE:
            case LOOK:
            case JUMP:
            case CROUCH:
            case SPRINT:
            case INTERACT:
            case ATTACK:
            case TURN:
            case PLACE_HAND:
            case DROP_SLOT:
            case MOVE_ITEM:
            case CLEAR_SLOT:
            case EQUIP_ARMOR:
            case UNEQUIP_ARMOR:
            case EQUIP_HAND:
            case UNEQUIP_HAND:
            case CLOSE_GUI:
            case CONTROL_REPEAT:
            case CONTROL_REPEAT_UNTIL:
            case CONTROL_IF_ELSE:
            case SENSOR_TOUCHING_BLOCK:
            case SENSOR_TOUCHING_ENTITY:
            case SENSOR_AT_COORDINATES:
            case SENSOR_BLOCK_AHEAD:
            case SENSOR_BLOCK_BELOW:
            case SENSOR_LIGHT_LEVEL_BELOW:
            case SENSOR_HEALTH_BELOW:
            case SENSOR_HUNGER_BELOW:
            case SENSOR_ENTITY_NEARBY:
            case SENSOR_ITEM_IN_INVENTORY:
            case SENSOR_IS_FALLING:
            case SENSOR_IS_RENDERED:
            case PARAM_COORDINATE:
            case PARAM_BLOCK:
            case PARAM_BLOCK_LIST:
            case PARAM_ITEM:
            case PARAM_ENTITY:
            case PARAM_PLAYER:
            case PARAM_WAYPOINT:
            case PARAM_SCHEMATIC:
            case PARAM_INVENTORY_SLOT:
            case PARAM_MESSAGE:
            case PARAM_DURATION:
            case PARAM_BOOLEAN:
            case PARAM_HAND:
            case PARAM_RANGE:
            case PARAM_ROTATION:
            case PARAM_PLACE_TARGET:
            case PARAM_CLOSEST:
                return true;
            default:
                return false;
        }
    }
}
