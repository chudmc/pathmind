package com.pathmind.util;

/**
 * Utility helpers for encoding and decoding inventory slot selection context.
 * The inventory slot selector stores its mode as "modeId" or "modeId|context",
 * where context indicates whether the currently selected slot belongs to the
 * player inventory or a GUI/container slot.
 */
public final class InventorySlotModeHelper {
    private static final String CONTEXT_SEPARATOR = "|";
    private static final String CONTEXT_PLAYER = "player";
    private static final String CONTEXT_CONTAINER = "container";

    private InventorySlotModeHelper() {
    }

    /**
     * Builds the persisted mode value by combining the base mode identifier with
     * the optional selection context.
     *
     * @param modeId            The base inventory GUI mode identifier.
     * @param isPlayerSelection True if the currently selected slot belongs to the
     *                          player inventory, false for GUI/container slots, or
     *                          null if no slot is selected.
     * @return The encoded mode string used by parameter nodes.
     */
    public static String buildStoredModeValue(String modeId, Boolean isPlayerSelection) {
        if (modeId == null || modeId.isEmpty()) {
            return "";
        }
        if (isPlayerSelection == null) {
            return modeId;
        }
        return modeId + CONTEXT_SEPARATOR + (isPlayerSelection ? CONTEXT_PLAYER : CONTEXT_CONTAINER);
    }

    /**
     * Extracts the base mode identifier from the stored mode string.
     */
    public static String extractModeId(String storedModeValue) {
        if (storedModeValue == null) {
            return null;
        }
        int separatorIndex = storedModeValue.indexOf(CONTEXT_SEPARATOR);
        if (separatorIndex < 0) {
            return storedModeValue;
        }
        if (separatorIndex == 0) {
            return "";
        }
        return storedModeValue.substring(0, separatorIndex);
    }

    /**
     * Extracts the selection-context flag from the stored mode string.
     *
     * @return Boolean.TRUE if the slot belongs to the player inventory, Boolean.FALSE
     * if it belongs to a GUI/container, or null if not encoded.
     */
    public static Boolean extractPlayerSelectionFlag(String storedModeValue) {
        if (storedModeValue == null) {
            return null;
        }
        int separatorIndex = storedModeValue.indexOf(CONTEXT_SEPARATOR);
        if (separatorIndex < 0 || separatorIndex >= storedModeValue.length() - 1) {
            return null;
        }
        String suffix = storedModeValue.substring(separatorIndex + 1).toLowerCase();
        if (CONTEXT_PLAYER.equals(suffix)) {
            return Boolean.TRUE;
        }
        if (CONTEXT_CONTAINER.equals(suffix)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
