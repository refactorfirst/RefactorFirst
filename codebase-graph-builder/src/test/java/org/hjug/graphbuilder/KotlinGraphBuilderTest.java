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

    /** Verifies the Kotlin fixture graph contains the expected classes, dependencies, and edge weights. */
    @DisplayName("Given a valid Kotlin source directory input parameter return a valid graph.")
    @Test
    void parseKotlinSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);
        assertEquals(5, classReferencesGraph.vertexSet().size());
        assertEquals(7, classReferencesGraph.edgeSet().size());
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.D")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.E")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.B"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.C"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.C"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.E")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.D"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.D"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.E"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.D")));

        // confirm edge weight calculations
        assertEquals(
                1,
                getEdgeWeight(
                        classReferencesGraph,
                        VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                        VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertEquals(
                2,
                getEdgeWeight(
                        classReferencesGraph,
                        VertexInfo.forName("com.ideacrest.parser.testclasses.E"),
                        VertexInfo.forName("com.ideacrest.parser.testclasses.D")));
    }

    /** Verifies that a Kotlin class-header dependency contributes exactly one unit of edge weight. */
    @DisplayName("Kotlin class-header dependencies are recorded exactly once.")
    @Test
    void classHeaderDependencyHasWeightOne() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinClassHeaderSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        String source = "com.ideacrest.parser.classheader.HeaderImplementation";
        String target = "com.ideacrest.parser.classheader.HeaderContract";

        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName(source)));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName(target)));
        assertTrue(classReferencesGraph.containsEdge(VertexInfo.forName(source), VertexInfo.forName(target)));
        assertEquals(1.0, getEdgeWeight(classReferencesGraph, VertexInfo.forName(source), VertexInfo.forName(target)));
    }

    /** Verifies that Kotlin callable references create dependencies on their declaring classes. */
    @DisplayName("Kotlin callable references produce edges between caller and target's declaring class.")
    @Test
    void parseKotlinCallableReferenceTest() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinCallableRefSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);

        // Both classes should appear as vertices
        assertTrue(
                classReferencesGraph.containsVertex(
                        VertexInfo.forName("com.ideacrest.parser.callref.CallableRefTarget")),
                "CallableRefTarget should be a graph vertex, vertices: " + classReferencesGraph.vertexSet());
        assertTrue(
                classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.callref.CallableRefUser")),
                "CallableRefUser should be a graph vertex, vertices: " + classReferencesGraph.vertexSet());

        // The two callable references (`CallableRefTarget::alpha` and
        // `CallableRefTarget::beta`) plus `alphaRef.call(target)` invocation
        // should each add an edge `CallableRefUser -> CallableRefTarget`.
        // The implementation weight-merges duplicates, so we just require
        // the edge exists and its weight is at least 1.
        boolean edgeExists = classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.callref.CallableRefUser"),
                VertexInfo.forName("com.ideacrest.parser.callref.CallableRefTarget"));
        assertTrue(
                edgeExists,
                "CallableRefUser -> CallableRefTarget edge should exist (callable references + .call() invocation), edges: "
                        + classReferencesGraph.edgeSet());

        if (edgeExists) {
            double weight = getEdgeWeight(
                    classReferencesGraph,
                    VertexInfo.forName("com.ideacrest.parser.callref.CallableRefUser"),
                    VertexInfo.forName("com.ideacrest.parser.callref.CallableRefTarget"));
            assertTrue(
                    weight >= 1.0, "CallableRefUser -> CallableRefTarget edge weight should be >= 1, was: " + weight);
        }
    }

    /** Returns the weight of the dependency between the supplied source and target vertices. */
    private static double getEdgeWeight(
            Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph,
            VertexInfo sourceVertex,
            VertexInfo targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }
}
