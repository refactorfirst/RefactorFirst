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
 * Tests the {@code forceJava25Parser} plumbing through
 * {@link CycleRanker#generateClassReferencesGraph(boolean, String, boolean)}.
 * The forced load must be transparent: on runtimes older than Java 25 the
 * Java 25 parser load fails gracefully and the standard parser is used; on
 * Java 25+ runtimes the Java 25 parser is used. Either way the graph is built.
 */
class CycleRankerForceJava25ParserTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("3-arg overload with forceJava25Parser=true builds the graph")
    void forcedJava25ParserStillBuildsGraph() throws IOException {
        Path pkg = tempDir.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(
                pkg.resolve("Forced.java"),
                """
                package com.example;
                public class Forced {
                    public void hello() {}
                }
                """);

        CycleRanker ranker = new CycleRanker(tempDir.toString(), tempDir.toString());
        CodebaseGraphDTO dto = ranker.generateClassReferencesGraph(false, "", true);

        assertNotNull(dto);
        assertTrue(dto.getClassReferencesGraph().containsVertex("com.example.Forced"));
    }

    @Test
    @DisplayName("2-arg overload behaves identically to 3-arg overload with force=false")
    void twoArgOverloadUnchanged() throws IOException {
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
