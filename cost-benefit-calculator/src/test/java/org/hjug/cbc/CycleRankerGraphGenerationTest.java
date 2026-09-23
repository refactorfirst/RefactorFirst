package org.hjug.cbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Verifies {@link CycleRanker#generateClassReferencesGraph(boolean, String)}
 * builds a usable graph. (Replaces a deleted test that existed to exercise
 * the removed parser-forcing overload; parser selection is now entirely
 * runtime-driven via the JEP 238 multi-release jar design.)
 */
class CycleRankerGraphGenerationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generateClassReferencesGraph builds the graph")
    void generatesClassReferencesGraph() throws IOException {
        Path pkg = tempDir.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(
                pkg.resolve("Plain.java"),
                """
                package com.example;
                public class Plain {
                    public void hello() {}
                }
                """);

        CycleRanker ranker = new CycleRanker(tempDir.toString(), tempDir.toString());
        CodebaseGraphDTO dto = ranker.generateClassReferencesGraph(false, "");

        assertNotNull(dto);
        assertTrue(dto.getClassReferencesGraph().containsVertex("com.example.Plain"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("generateClassReferencesGraph rejects a missing source directory")
    void rejectsMissingSourceDirectory(String repositoryPath) {
        CycleRanker ranker = new CycleRanker(repositoryPath, tempDir.toString());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> ranker.generateClassReferencesGraph(false, "src/test"));

        assertEquals("Source directory cannot be null or empty", exception.getMessage());
        assertNull(ranker.getCodebaseGraphDTO(), "A rejected request must not populate the graph DTO");
    }

    @ParameterizedTest(name = "excludeTests={0} should include custom test source={1}")
    @CsvSource({"true, false", "false, true"})
    @DisplayName("generateClassReferencesGraph forwards test exclusion configuration")
    void forwardsTestExclusionConfiguration(boolean excludeTests, boolean expectTestSource) throws IOException {
        writeJava(tempDir.resolve("src/main/java/com/example/ProductionClass.java"), "com.example", "ProductionClass");
        writeJava(tempDir.resolve("custom-tests/com/example/FixtureClass.java"), "com.example", "FixtureClass");

        CycleRanker ranker = new CycleRanker(tempDir.toString(), tempDir.toString());
        CodebaseGraphDTO dto = ranker.generateClassReferencesGraph(excludeTests, "custom-tests");

        assertTrue(dto.getClassReferencesGraph().containsVertex("com.example.ProductionClass"));
        assertEquals(
                expectTestSource,
                dto.getClassReferencesGraph().containsVertex("com.example.FixtureClass"),
                "The custom test source directory must follow the caller's exclusion setting");
        assertSame(dto, ranker.getCodebaseGraphDTO(), "The generated DTO must remain available to cycle ranking");
    }

    private static void writeJava(Path path, String packageName, String className) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                """
                package %s;
                public class %s {}
                """
                        .formatted(packageName, className));
    }
}
