package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Java-fixture graph tests, driven through the orchestration entry
 * point {@link CompositeGraphBuilder} (which always runs the Java builder and
 * merges an empty Kotlin DTO for a Java-only source directory). The Java-only
 * fixture under {@code src/test/resources/javaSrcDirectory} produces the same
 * vertices/edges through the composite as the removed {@code JavaGraphBuilder}
 * once did.
 */
class JavaGraphBuilderTest {

    private final CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();

    @DisplayName("When source directory input param is empty or null throw IllegalArgumentException.")
    @Test
    void parseSourceDirectoryEmptyTest() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> compositeGraphBuilder.getCodebaseGraphDTO("", false, ""));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> compositeGraphBuilder.getCodebaseGraphDTO(null, false, ""));
    }

    @DisplayName("Given a valid source directory input parameter return a valid graph.")
    @Test
    void parseSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);
        assertEquals(5, classReferencesGraph.vertexSet().size());
        assertEquals(7, classReferencesGraph.edgeSet().size());
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.B"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.D"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.E"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.A", "com.ideacrest.parser.testclasses.B"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.B", "com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.C", "com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.C", "com.ideacrest.parser.testclasses.E"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.D", "com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.D", "com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.E", "com.ideacrest.parser.testclasses.D"));

        // confirm edge weight calculations
        assertEquals(
                1,
                getEdgeWeight(
                        classReferencesGraph,
                        "com.ideacrest.parser.testclasses.A",
                        "com.ideacrest.parser.testclasses.B"));
        assertEquals(
                2,
                getEdgeWeight(
                        classReferencesGraph,
                        "com.ideacrest.parser.testclasses.E",
                        "com.ideacrest.parser.testclasses.D"));
    }

    private static double getEdgeWeight(
            Graph<String, DefaultWeightedEdge> classReferencesGraph, String sourceVertex, String targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }
}
