package org.hjug.cbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
