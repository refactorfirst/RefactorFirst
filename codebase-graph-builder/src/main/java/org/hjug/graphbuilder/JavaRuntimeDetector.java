package org.hjug.graphbuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects the Java feature version of the current JVM runtime.
 *
 * <p>The primary source is the {@code java.specification.version} system
 * property, which on modern JDKs returns the feature version (e.g.
 * {@code "25"} for Java 25). {@link Runtime#version()} is used as a fallback
 * for null or legacy (e.g. {@code "1.8"}) property values.
 */
@Slf4j
public final class JavaRuntimeDetector {

    private static final int JAVA_25_VERSION = 25;
    private static final String SPEC_VERSION_PROPERTY = "java.specification.version";

    private JavaRuntimeDetector() {
        // Utility class
    }

    /**
     * Detects if the current JVM runtime is Java 25 or higher.
     */
    public static boolean isJava25OrHigher() {
        int runtimeVersion = getRuntimeVersion();
        log.info("Detected Java runtime version: {}", runtimeVersion);
        return runtimeVersion >= JAVA_25_VERSION;
    }

    /**
     * Gets the current Java runtime feature version.
     */
    public static int getRuntimeVersion() {
        String javaVersion = System.getProperty(SPEC_VERSION_PROPERTY);
        if (javaVersion != null) {
            try {
                return Integer.parseInt(javaVersion);
            } catch (NumberFormatException e) {
                // Legacy formats such as "1.8" — fall through to Runtime.version()
            }
        }
        return Runtime.version().feature();
    }
}
