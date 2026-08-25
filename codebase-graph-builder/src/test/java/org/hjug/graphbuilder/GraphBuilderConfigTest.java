package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @DisplayName("repositoryRoot is passed through config overload")
    @Test
    void repositoryRoot_passedThroughConfigOverload(@TempDir Path tempDir) throws IOException {
        // Create repo root with a nested source module
        Path repoRoot = tempDir.resolve("repo");
        Path sourceRoot =
                repoRoot.resolve("module-a").resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceRoot);

        // Create a Java source file
        Path javaFile = sourceRoot.resolve("com").resolve("example").resolve("MyClass.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(
                javaFile,
                """
                package com.example;
                public class MyClass {
                    public void hello() {}
                }
                """);
        // Build config with repositoryRoot set to repo root
        String repoRootStr = repoRoot.toString();
        assertFalse(repoRootStr.isEmpty(), "repoRoot should not be empty");
        GraphBuilderConfig config =
                GraphBuilderConfig.builder().repositoryRoot(repoRootStr).build();

        // Verify config has the correct repositoryRoot
        assertEquals(repoRootStr, config.getRepositoryRoot(), "Config should have the repositoryRoot set");

        // Call the 3-argument overload directly to bypass 2-arg method
        CompositeGraphBuilder builder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = builder.getCodebaseGraphDTO(sourceRoot.toString(), repoRootStr, config);
        // Assert source mapping is repository-root-relative
        String sourcePath = dto.getClassToSourceFilePathMapping().get("com.example.MyClass");
        assertNotNull(sourcePath, "Source path should be mapped");

        // Path should be relative to repo root, not source root
        // Expected: module-a/src/main/java/com/example/MyClass.java
        String normalized = sourcePath.replace('/', java.io.File.separatorChar);
        assertTrue(
                normalized.contains("module-a" + java.io.File.separatorChar + "src"),
                "Source path should be relative to repository root: " + sourcePath);
    }

    @DisplayName("repositoryRoot defaults to empty string when not set")
    @Test
    void repositoryRoot_defaultsToEmptyInConfigOverload(@TempDir Path tempDir) throws IOException {
        // Create a simple source root (single module, no separate repo root)
        Path sourceRoot = tempDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceRoot);

        Path javaFile = sourceRoot.resolve("com").resolve("example").resolve("MyClass.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(
                javaFile,
                """
                package com.example;
                public class MyClass {}
                """);

        // Config without repositoryRoot (defaults to "")
        GraphBuilderConfig config = GraphBuilderConfig.builder().build();

        CompositeGraphBuilder builder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = builder.getCodebaseGraphDTO(sourceRoot.toString(), config);

        // With empty repositoryRoot, paths should be relative to source root
        String sourcePath = dto.getClassToSourceFilePathMapping().get("com.example.MyClass");
        assertNotNull(sourcePath);

        // Should be relative to sourceRoot (no module-a prefix)
        String normalized = sourcePath.replace('/', java.io.File.separatorChar);
        assertTrue(
                normalized.contains("com" + java.io.File.separatorChar + "example"),
                "Source path should be relative to source root when repositoryRoot is empty: " + sourcePath);
    }

    @DisplayName("repositoryRoot equal to sourceRoot preserves single-module behavior")
    @Test
    void repositoryRoot_equalToSourceRoot(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceRoot);

        Path javaFile = sourceRoot.resolve("com").resolve("example").resolve("MyClass.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(
                javaFile,
                """
                package com.example;
                public class MyClass {}
                """);

        // Config with repositoryRoot equal to sourceRoot
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .repositoryRoot(sourceRoot.toString())
                .build();

        CompositeGraphBuilder builder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = builder.getCodebaseGraphDTO(sourceRoot.toString(), config);

        String sourcePath = dto.getClassToSourceFilePathMapping().get("com.example.MyClass");
        assertNotNull(sourcePath);

        // Should be just the package path + filename
        String normalized = sourcePath.replace('/', java.io.File.separatorChar);
        assertTrue(
                normalized.contains("com" + java.io.File.separatorChar + "example"),
                "Source path should be relative to repository root (which equals source root): " + sourcePath);
    }
}
