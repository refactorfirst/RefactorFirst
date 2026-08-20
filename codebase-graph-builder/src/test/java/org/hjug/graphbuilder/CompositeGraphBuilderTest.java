package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that mixed Java + Kotlin source directories produce a single merged
 * graph with cross-language dependency edges.
 */
class CompositeGraphBuilderTest {

    @DisplayName("A directory with both Java and Kotlin source files produces a single graph with cross-language edges")
    @Test
    void parseMixedSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/mixedSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");

        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);

        // All 4 classes should be vertices
        assertEquals(4, classReferencesGraph.vertexSet().size());
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.mixedclasses.JavaClass"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.mixedclasses.KotlinClass"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.mixedclasses.SharedTarget"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.mixedclasses.KConsumer"));

        // Java -> Kotlin cross-language edge
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.mixedclasses.JavaClass", "com.ideacrest.parser.mixedclasses.KotlinClass"));

        // Kotlin -> Java cross-language edge
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.mixedclasses.KotlinClass", "com.ideacrest.parser.mixedclasses.JavaClass"));

        // Kotlin -> Java shared target edge
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.mixedclasses.KConsumer", "com.ideacrest.parser.mixedclasses.SharedTarget"));
    }

    @DisplayName("Cross-package Java-to-Kotlin reference is reconciled to the canonical Kotlin FQN")
    @Test
    void parseMixedSourceDirectoryCrossPackageTest() throws IOException {
        File srcDirectory = new File("src/test/resources/mixedSrcDirectoryCrossPackage");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");

        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);

        // The canonical Kotlin class FQN should be present
        assertTrue(
                classReferencesGraph.containsVertex("com.almasb.fxgl.app.GameSettings"),
                "Canonical Kotlin class should be in the graph");

        // The fabricated vertex (caller's package + simple name) should NOT be present
        assertFalse(
                classReferencesGraph.containsVertex("com.ideacrest.parser.mixedclasses.GameSettings"),
                "Fabricated cross-package vertex should be reconciled away");

        // The Java class should have an edge to the CANONICAL Kotlin FQN
        assertTrue(
                classReferencesGraph.containsEdge(
                        "com.ideacrest.parser.mixedclasses.JavaClass", "com.almasb.fxgl.app.GameSettings"),
                "Java class should reference the canonical Kotlin FQN after reconciliation");

        // Source path mapping should have the canonical FQN
        assertTrue(
                dto.getClassToSourceFilePathMapping().containsKey("com.almasb.fxgl.app.GameSettings"),
                "Source path mapping should have the canonical Kotlin FQN");

        // Package graph should have the cross-package edge
        Graph<String, DefaultWeightedEdge> packageReferencesGraph = dto.getPackageReferencesGraph();
        assertTrue(
                packageReferencesGraph.containsEdge("com.ideacrest.parser.mixedclasses", "com.almasb.fxgl.app"),
                "Package graph should have the cross-package edge after reconciliation");
    }
}
