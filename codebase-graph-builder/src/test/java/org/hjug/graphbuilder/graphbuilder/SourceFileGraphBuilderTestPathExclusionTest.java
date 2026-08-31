package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that test path exclusion works correctly with both forward slashes (Unix-like)
 * and backslashes (Windows) in the testSourceDirectory configuration.
 *
 * <p>This regression test ensures that the fix for separator-independent test exclusion
 * works: both {@link JavaSourceFileGraphBuilder} and {@link KotlinSourceFileGraphBuilder}
 * normalize file paths and the test directory pattern to forward slashes before comparison.
 */
class SourceFileGraphBuilderTestPathExclusionTest {

    @Test
    void directoryMatchingDoesNotExcludeLongerDirectoryNames() {
        assertTrue(SourceFileGraphBuilder.isInConfiguredDirectory(
                Path.of("project/src/test/java/Example.java"), "src/test"));
        assertFalse(SourceFileGraphBuilder.isInConfiguredDirectory(
                Path.of("project/src/testFixtures/java/Example.java"), "src/test"));
    }

    @DisplayName("Java builder excludes test files with forward-slash testSourceDirectory on Windows-style paths")
    @Test
    void javaBuilder_excludesTestsWithForwardSlashPatternOnWindowsPaths(@TempDir Path tempDir) throws IOException {
        // Create main source file
        Path mainSrc = tempDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(
                mainSrc,
                "package com.example;\n" + "public class Main {\n" + "    public void mainMethod() {}\n" + "}\n");

        // Create test source file (should be excluded)
        Path testSrc = tempDir.resolve("src/test/java/com/example/MainTest.java");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc,
                "package com.example;\n" + "public class MainTest {\n" + "    public void testMethod() {}\n" + "}\n");

        // Use default config (testSourceDirectory = "src/test" with forward slash)
        // On Windows, file.toString() returns paths with backslashes
        JavaSourceFileGraphBuilder builder = new JavaSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(true)
                .testSourceDirectory("src/test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        // Main class should be included
        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");

        // Test class should be EXCLUDED (this verifies the fix works)
        assertFalse(classGraph.containsVertex("com.example.MainTest"), "MainTest class should be excluded from graph");
    }

    @DisplayName("Java builder excludes test files with backslash testSourceDirectory")
    @Test
    void javaBuilder_excludesTestsWithBackslashPattern(@TempDir Path tempDir) throws IOException {
        Path mainSrc = tempDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(
                mainSrc,
                "package com.example;\n" + "public class Main {\n" + "    public void mainMethod() {}\n" + "}\n");

        Path testSrc = tempDir.resolve("src/test/java/com/example/MainTest.java");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc,
                "package com.example;\n" + "public class MainTest {\n" + "    public void testMethod() {}\n" + "}\n");

        // Use backslash in testSourceDirectory (simulating Windows config)
        JavaSourceFileGraphBuilder builder = new JavaSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(true)
                .testSourceDirectory("src\\test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");
        assertFalse(
                classGraph.containsVertex("com.example.MainTest"),
                "MainTest class should be excluded with backslash pattern");
    }

    @DisplayName("Kotlin builder excludes test files with forward-slash testSourceDirectory on Windows-style paths")
    @Test
    void kotlinBuilder_excludesTestsWithForwardSlashPatternOnWindowsPaths(@TempDir Path tempDir) throws IOException {
        Path mainSrc = tempDir.resolve("src/main/kotlin/com/example/Main.kt");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(mainSrc, "package com.example\n" + "class Main {\n" + "    fun mainMethod() {}\n" + "}\n");

        Path testSrc = tempDir.resolve("src/test/kotlin/com/example/MainTest.kt");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc, "package com.example\n" + "class MainTest {\n" + "    fun testMethod() {}\n" + "}\n");

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(true)
                .testSourceDirectory("src/test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");
        assertFalse(classGraph.containsVertex("com.example.MainTest"), "MainTest class should be excluded from graph");
    }

    @DisplayName("Kotlin builder excludes test files with backslash testSourceDirectory")
    @Test
    void kotlinBuilder_excludesTestsWithBackslashPattern(@TempDir Path tempDir) throws IOException {
        Path mainSrc = tempDir.resolve("src/main/kotlin/com/example/Main.kt");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(mainSrc, "package com.example\n" + "class Main {\n" + "    fun mainMethod() {}\n" + "}\n");

        Path testSrc = tempDir.resolve("src/test/kotlin/com/example/MainTest.kt");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc, "package com.example\n" + "class MainTest {\n" + "    fun testMethod() {}\n" + "}\n");

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(true)
                .testSourceDirectory("src\\test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");
        assertFalse(
                classGraph.containsVertex("com.example.MainTest"),
                "MainTest class should be excluded with backslash pattern");
    }

    @DisplayName("Java builder includes test files when excludeTests=false")
    @Test
    void javaBuilder_includesTestsWhenExcludeTestsFalse(@TempDir Path tempDir) throws IOException {
        Path mainSrc = tempDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(
                mainSrc,
                "package com.example;\n" + "public class Main {\n" + "    public void mainMethod() {}\n" + "}\n");

        Path testSrc = tempDir.resolve("src/test/java/com/example/MainTest.java");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc,
                "package com.example;\n" + "public class MainTest {\n" + "    public void testMethod() {}\n" + "}\n");

        JavaSourceFileGraphBuilder builder = new JavaSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(false) // Disable test exclusion
                .testSourceDirectory("src/test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");
        assertTrue(
                classGraph.containsVertex("com.example.MainTest"),
                "MainTest class should be included when excludeTests=false");
    }

    @DisplayName("Kotlin builder includes test files when excludeTests=false")
    @Test
    void kotlinBuilder_includesTestsWhenExcludeTestsFalse(@TempDir Path tempDir) throws IOException {
        Path mainSrc = tempDir.resolve("src/main/kotlin/com/example/Main.kt");
        Files.createDirectories(mainSrc.getParent());
        Files.writeString(mainSrc, "package com.example\n" + "class Main {\n" + "    fun mainMethod() {}\n" + "}\n");

        Path testSrc = tempDir.resolve("src/test/kotlin/com/example/MainTest.kt");
        Files.createDirectories(testSrc.getParent());
        Files.writeString(
                testSrc, "package com.example\n" + "class MainTest {\n" + "    fun testMethod() {}\n" + "}\n");

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(false) // Disable test exclusion
                .testSourceDirectory("src/test")
                .build();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.Main"), "Main class should be in graph");
        assertTrue(
                classGraph.containsVertex("com.example.MainTest"),
                "MainTest class should be included when excludeTests=false");
    }
}
