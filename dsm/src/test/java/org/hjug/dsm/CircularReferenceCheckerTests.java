package org.hjug.dsm;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "([A, B, C], [(A,B), (B,C), (C,A)])",
                cyclesForEveryVertexMap.get("A").toString(),
                "Expected a different circular reference");
    }
}
