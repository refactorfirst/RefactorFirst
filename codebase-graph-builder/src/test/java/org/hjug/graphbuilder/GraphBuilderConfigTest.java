package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GraphBuilderConfig} configuration properties.
 */
class GraphBuilderConfigTest {

    @DisplayName("repositoryRoot field exists and defaults to empty string")
    @Test
    void repositoryRoot_defaultsToEmptyString() {
        GraphBuilderConfig config = GraphBuilderConfig.defaultConfig();
        assertNotNull(config.getRepositoryRoot());
        assertEquals("", config.getRepositoryRoot());
    }

    @DisplayName("repositoryRoot can be set via builder")
    @Test
    void repositoryRoot_canBeSetViaBuilder() {
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .repositoryRoot("/path/to/repo/root")
                .build();
        assertEquals("/path/to/repo/root", config.getRepositoryRoot());
    }

    @DisplayName("repositoryRoot is independent of other config fields")
    @Test
    void repositoryRoot_independentOfOtherFields() {
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(false)
                .testSourceDirectory("custom/test")
                .kotlinLanguageLevel("KOTLIN_2_1")
                .repositoryRoot("/repo/root")
                .build();

        assertFalse(config.isExcludeTests());
        assertEquals("custom/test", config.getTestSourceDirectory());
        assertEquals("KOTLIN_2_1", config.getKotlinLanguageLevel());
        assertEquals("/repo/root", config.getRepositoryRoot());
    }
}
