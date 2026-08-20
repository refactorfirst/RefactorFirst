package org.hjug.dsm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircularReferenceCheckerTests {

    CircularReferenceChecker sutCircularReferenceChecker = new CircularReferenceChecker();

    @DisplayName("Detect 3 cycles from given graph.")
    @Test
    void detectCyclesTest() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");

        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cyclesForEveryVertexMap =
                sutCircularReferenceChecker.getCycles(classReferencesGraph);
        assertEquals(0, cyclesForEveryVertexMap.size(), "Not expecting any circular references at this point");

        classReferencesGraph.addEdge("C", "A");

        cyclesForEveryVertexMap = sutCircularReferenceChecker.getCycles(classReferencesGraph);
        assertEquals(1, cyclesForEveryVertexMap.size(), "Now we expect one circular reference");
        assertEquals(
                "([A, B, C], [(C,A), (A,B), (B,C)])",
                cyclesForEveryVertexMap.get("A").toString(),
                "Expected a different circular reference");
    }

    /**
     * Anonymous/synthetic classes (Java {@code Outer$1}/{@code Outer$2}, the literal Kotlin
     * {@code "<anonymous>"} string) are first-class graph members and may participate in cycles.
     * {@link CircularReferenceChecker} feeds every vertex straight into JGraphT's
     * {@link CycleDetector} and performs no own filtering — so when an anonymous vertex is
     * present in a cycle it is surfaced as an ordinary cycle member. This is the desired
     * behaviour: anonymous classes can contain antipatterns worth surfacing.
     *
     * <p>Render-time sink filtering (vertices with no outgoing edges) lives in
     * {@link org.hjug.refactorfirst.report.HtmlReport}; it does not affect cycle detection.
     */
    @DisplayName("anonymous vertices in cycles are surfaced as ordinary cycle members")
    @Test
    void cycles_onGraphWithAnonymousVertices_surfacesAnonymousMembers() {
        // A graph containing a Java anonymous inner-class vertex and a Kotlin <anonymous> vertex,
        // both participating in cycles alongside named classes.
        Graph<String, DefaultWeightedEdge> graph = new DefaultDirectedGraph<>(DefaultWeightedEdge.class);
        graph.addVertex("com.foo.A");
        graph.addVertex("com.foo.Outer$1");
        graph.addVertex("com.foo.B");
        graph.addVertex("com.foo.C");
        graph.addVertex("<anonymous>");
        graph.addEdge("com.foo.A", "com.foo.Outer$1");
        graph.addEdge("com.foo.Outer$1", "com.foo.A");
        graph.addEdge("com.foo.B", "com.foo.C");
        graph.addEdge("com.foo.C", "<anonymous>");
        graph.addEdge("<anonymous>", "com.foo.B");

        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles = sutCircularReferenceChecker.getCycles(graph);

        // Both the Java anonymous and the Kotlin <anonymous> vertices must appear as cycle members.
        boolean javaAnonymousInCycle =
                cycles.values().stream().anyMatch(sg -> sg.vertexSet().contains("com.foo.Outer$1"));
        boolean kotlinAnonymousInCycle =
                cycles.values().stream().anyMatch(sg -> sg.vertexSet().contains("<anonymous>"));
        assertTrue(javaAnonymousInCycle, "Java Outer$1 must be surfaced as a cycle member");
        assertTrue(kotlinAnonymousInCycle, "Kotlin <anonymous> must be surfaced as a cycle member");
        assertTrue(cycles.size() >= 2, "at least two distinct cycles should be detected");
    }
}
