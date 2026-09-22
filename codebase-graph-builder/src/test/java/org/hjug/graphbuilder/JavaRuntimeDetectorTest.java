package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link JavaRuntimeDetector}. These tests set the
 * {@code java.specification.version} system property and always restore the
 * original value afterwards so other tests in the same JVM are unaffected.
 */
class JavaRuntimeDetectorTest {

    private static final String SPEC_VERSION_PROPERTY = "java.specification.version";

    private String originalSpecVersion;

    @BeforeEach
    void captureOriginal() {
        originalSpecVersion = System.getProperty(SPEC_VERSION_PROPERTY);
    }

    @AfterEach
    void restoreOriginal() {
        if (originalSpecVersion == null) {
            System.clearProperty(SPEC_VERSION_PROPERTY);
        } else {
            System.setProperty(SPEC_VERSION_PROPERTY, originalSpecVersion);
        }
    }

    @DisplayName("Java 25 and higher feature versions are detected as Java 25 or higher")
    @ParameterizedTest
    @ValueSource(strings = {"25", "26", "30"})
    void isJava25OrHigher_trueFor25AndAbove(String version) {
        System.setProperty(SPEC_VERSION_PROPERTY, version);
        assertTrue(JavaRuntimeDetector.isJava25OrHigher());
    }

    @DisplayName("Java 17 and 21 feature versions are not detected as Java 25 or higher")
    @ParameterizedTest
    @ValueSource(strings = {"17", "21", "24"})
    void isJava25OrHigher_falseBelow25(String version) {
        System.setProperty(SPEC_VERSION_PROPERTY, version);
        assertFalse(JavaRuntimeDetector.isJava25OrHigher());
    }

    @DisplayName("Legacy dotted version strings (e.g. 1.8) fall back to Runtime.version()")
    @Test
    void isJava25OrHigher_malformedVersionFallsBackToRuntimeApi() {
        System.setProperty(SPEC_VERSION_PROPERTY, "1.8");
        // Runtime.version().feature() of the JVM running these tests is a
        // modern feature version (>= 17), so the fallback must reflect that JVM
        // rather than the malformed property value.
        assertEquals(Runtime.version().feature() >= 25, JavaRuntimeDetector.isJava25OrHigher());
    }

    @DisplayName("Null system property falls back to Runtime.version()")
    @Test
    void isJava25OrHigher_nullPropertyFallsBackToRuntimeApi() {
        System.clearProperty(SPEC_VERSION_PROPERTY);
        assertEquals(Runtime.version().feature() >= 25, JavaRuntimeDetector.isJava25OrHigher());
    }

    @DisplayName("getRuntimeVersion returns the parsed feature version")
    @ParameterizedTest
    @ValueSource(strings = {"17", "21", "25"})
    void getRuntimeVersion_parsesFeatureVersion(String version) {
        System.setProperty(SPEC_VERSION_PROPERTY, version);
        assertEquals(Integer.parseInt(version), JavaRuntimeDetector.getRuntimeVersion());
    }

    @DisplayName("getRuntimeVersion falls back to Runtime.version() on malformed property")
    @Test
    void getRuntimeVersion_malformedFallsBackToRuntimeApi() {
        System.setProperty(SPEC_VERSION_PROPERTY, "1.8");
        assertEquals(Runtime.version().feature(), JavaRuntimeDetector.getRuntimeVersion());
    }

    @DisplayName("getRuntimeVersion falls back to Runtime.version() on null property")
    @Test
    void getRuntimeVersion_nullPropertyFallsBackToRuntimeApi() {
        System.clearProperty(SPEC_VERSION_PROPERTY);
        assertEquals(Runtime.version().feature(), JavaRuntimeDetector.getRuntimeVersion());
    }

    @DisplayName("isJava25OrHigher is consistent with getRuntimeVersion")
    @Test
    void detectionIsConsistent() {
        assertEquals(JavaRuntimeDetector.getRuntimeVersion() >= 25, JavaRuntimeDetector.isJava25OrHigher());
    }
}
