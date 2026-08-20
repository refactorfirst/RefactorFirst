package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KotlinSourceFileGraphBuilder} focusing on handling of partial parse trees
 * (ParseError with erroneous source files).
 */
class KotlinSourceFileGraphBuilderPartialParseTest {

    @DisplayName("Kotlin file with license header parse error still registers classes from partial parse tree")
    @Test
    void kotlinFileWithLicenseHeaderParseError_registersClassesFromPartialTree() throws IOException {
        // Create a temporary directory with a Kotlin file that has a license header
        // The license header causes OpenRewrite's Kotlin parser to produce a ParseError,
        // but the erroneous source file should still contain a partial parse tree
        // with the class declarations that we can visit.
        Path tempDir = Files.createTempDirectory("kotlin-parse-test");
        tempDir.toFile().deleteOnExit();

        // Create a Kotlin file with a license header that causes ParseError
        String kotlinContent =
                """
            /*
             * Copyright (c) 2024 Test License
             * All rights reserved.
             */
            package com.example.parseerror

            class GameSettings {
                var title: String = "Test Game"
                var width: Int = 800
                var height: Int = 600
            }

            class ReadOnlyGameSettings internal constructor(
                val title: String,
                val width: Int,
                val height: Int
            )
            """;

        Path testFile = tempDir.resolve("Settings.kt");
        Files.writeString(testFile, kotlinContent);

        // Build the graph using the KotlinSourceFileGraphBuilder directly
        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.defaultConfig();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);

        // The classes should be registered despite the ParseError
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(
                classGraph.containsVertex("com.example.parseerror.GameSettings"),
                "GameSettings class should be registered despite ParseError");
        assertTrue(
                classGraph.containsVertex("com.example.parseerror.ReadOnlyGameSettings"),
                "ReadOnlyGameSettings class should be registered despite ParseError");

        // Verify the source path mapping is correct
        Map<String, String> pathMapping = dto.getClassToSourceFilePathMapping();
        assertTrue(
                pathMapping.containsKey("com.example.parseerror.GameSettings"),
                "GameSettings should have source path mapping");
        assertTrue(
                pathMapping.containsKey("com.example.parseerror.ReadOnlyGameSettings"),
                "ReadOnlyGameSettings should have source path mapping");

        // The source path should point to the actual file
        String gameSettingsPath = pathMapping.get("com.example.parseerror.GameSettings");
        assertTrue(
                gameSettingsPath.endsWith("Settings.kt"),
                "Source path should point to Settings.kt, got: " + gameSettingsPath);
    }

    @DisplayName("Kotlin file without parse error still works normally")
    @Test
    void kotlinFileWithoutParseError_worksNormally() throws IOException {
        Path tempDir = Files.createTempDirectory("kotlin-normal-test");
        tempDir.toFile().deleteOnExit();

        // Create a Kotlin file WITHOUT a license header (should parse cleanly)
        String kotlinContent =
                """
            package com.example.normal

            class NormalClass {
                val name: String = "test"
            }
            """;

        Path testFile = tempDir.resolve("Normal.kt");
        Files.writeString(testFile, kotlinContent);

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.defaultConfig();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);

        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.normal.NormalClass"), "NormalClass should be registered");
    }

    @DisplayName("ParseError without partial tree is handled gracefully")
    @Test
    void parseErrorWithoutPartialTree_handledGracefully() throws IOException {
        // This test verifies that if a ParseError occurs but there's no partial tree
        // (erroneous is not a CompilationUnit), the builder doesn't crash
        Path tempDir = Files.createTempDirectory("kotlin-parse-error-test");
        tempDir.toFile().deleteOnExit();

        // Create a file that will cause a ParseError but might not have a recoverable partial tree
        // Using an incomplete/invalid Kotlin file
        String invalidKotlinContent =
                """
            package com.example.invalid

            class IncompleteClass {
                // Missing closing brace
                fun incomplete() {
        """;

        Path testFile = tempDir.resolve("Invalid.kt");
        Files.writeString(testFile, invalidKotlinContent);

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.defaultConfig();

        // Should not throw an exception
        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);

        // The graph might be empty or have no vertices for this file, but shouldn't crash
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();
        // Just verify it doesn't throw and returns a valid DTO
        assertNotNull(dto);
        assertNotNull(classGraph);
    }

    @DisplayName("Multiple classes in file with ParseError are all registered")
    @Test
    void multipleClassesInFileWithParseError_allRegistered() throws IOException {
        Path tempDir = Files.createTempDirectory("kotlin-multi-class-test");
        tempDir.toFile().deleteOnExit();

        String kotlinContent =
                """
            /*
             * License header that causes ParseError
             */
            package com.example.multi

            class FirstClass {
                val id: Int = 1
            }

            class SecondClass {
                val name: String = "second"
            }

            data class ThirdDataClass(val value: String)
            """;

        Path testFile = tempDir.resolve("MultiClass.kt");
        Files.writeString(testFile, kotlinContent);

        KotlinSourceFileGraphBuilder builder = new KotlinSourceFileGraphBuilder();
        GraphBuilderConfig config = GraphBuilderConfig.defaultConfig();

        CodebaseGraphDTO dto = builder.buildGraph(tempDir.toString(), "", config);

        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();

        assertTrue(classGraph.containsVertex("com.example.multi.FirstClass"), "FirstClass should be registered");
        assertTrue(classGraph.containsVertex("com.example.multi.SecondClass"), "SecondClass should be registered");
        assertTrue(
                classGraph.containsVertex("com.example.multi.ThirdDataClass"), "ThirdDataClass should be registered");

        // Verify all have source path mappings
        Map<String, String> pathMapping = dto.getClassToSourceFilePathMapping();
        assertTrue(pathMapping.containsKey("com.example.multi.FirstClass"));
        assertTrue(pathMapping.containsKey("com.example.multi.SecondClass"));
        assertTrue(pathMapping.containsKey("com.example.multi.ThirdDataClass"));
    }
}
