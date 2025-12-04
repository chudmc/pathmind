package com.pathmind.util;

/**
 * Lightweight runtime check for Baritone API availability.
 * Uses reflection to avoid hard dependencies when the jar is missing.
 */
public final class BaritoneDependencyChecker {
    public static final String DOWNLOAD_URL = "https://github.com/cabaletta/baritone/releases/download/v1.15.0/baritone-api-fabric-1.15.0.jar";
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static Boolean cachedResult;

    private BaritoneDependencyChecker() {
    }

    /**
     * @return true if the Baritone API classes are available on the classpath.
     */
    public static boolean isBaritoneApiPresent() {
        if (cachedResult != null) {
            return cachedResult;
        }

        try {
            Class.forName(BARITONE_API_CLASS, false, BaritoneDependencyChecker.class.getClassLoader());
            cachedResult = Boolean.TRUE;
        } catch (ClassNotFoundException e) {
            cachedResult = Boolean.FALSE;
        }

        return cachedResult;
    }
}
