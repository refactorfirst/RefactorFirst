package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mirrors {@link JavaGraphBuilderTest#parseSourceDirectoryTest()} against Kotlin fixtures
 * that are semantically identical to the Java ones under
 * {@code src/test/resources/javaSrcDirectory}.
 *
 * <p>All {@code .kt} files live under {@code src/test/resources/kotlinSrcDirectory} as
 * plain-text inputs to the OpenRewrite Kotlin parser — they are NOT compiled Kotlin source.
 */
class KotlinGraphBuilderTest {

    private final CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();

    @DisplayName("Given a valid Kotlin source directory input parameter return a valid graph.")
    @Test
    void parseKotlinSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinSrcDirectory");
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

    @DisplayName("Kotlin callable references produce edges between caller and target's declaring class.")
    @Test
    void parseKotlinCallableReferenceTest() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinCallableRefSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);

        // Both classes should appear as vertices
        assertTrue(
                classReferencesGraph.containsVertex("com.ideacrest.parser.callref.CallableRefTarget"),
                "CallableRefTarget should be a graph vertex, vertices: " + classReferencesGraph.vertexSet());
        assertTrue(
                classReferencesGraph.containsVertex("com.ideacrest.parser.callref.CallableRefUser"),
                "CallableRefUser should be a graph vertex, vertices: " + classReferencesGraph.vertexSet());

        // The two callable references (`CallableRefTarget::alpha` and
        // `CallableRefTarget::beta`) plus `alphaRef.call(target)` invocation
        // should each add an edge `CallableRefUser -> CallableRefTarget`.
        // The implementation weight-merges duplicates, so we just require
        // the edge exists and its weight is at least 1.
        boolean edgeExists = classReferencesGraph.containsEdge(
                "com.ideacrest.parser.callref.CallableRefUser", "com.ideacrest.parser.callref.CallableRefTarget");
        assertTrue(
                edgeExists,
                "CallableRefUser -> CallableRefTarget edge should exist (callable references + .call() invocation), edges: "
                        + classReferencesGraph.edgeSet());

        if (edgeExists) {
            double weight = getEdgeWeight(
                    classReferencesGraph,
                    "com.ideacrest.parser.callref.CallableRefUser",
                    "com.ideacrest.parser.callref.CallableRefTarget");
            assertTrue(
                    weight >= 1.0, "CallableRefUser -> CallableRefTarget edge weight should be >= 1, was: " + weight);
        }
    }

    private static double getEdgeWeight(
            Graph<String, DefaultWeightedEdge> classReferencesGraph, String sourceVertex, String targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }
}
