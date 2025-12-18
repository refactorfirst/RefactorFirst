package org.hjug.dsm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

class OptimalBackEdgeRemoverTest {

    @Test
    void noOptimalEdge() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");

        OptimalBackEdgeRemover<String, DefaultWeightedEdge> remover = new OptimalBackEdgeRemover(classReferencesGraph);
        Set<DefaultWeightedEdge> optimalEdges = remover.findOptimalBackEdgesToRemove();

        assertTrue(optimalEdges.isEmpty());
    }

    @Test
    void oneBackEdge() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");
        classReferencesGraph.addEdge("C", "A");

        OptimalBackEdgeRemover<String, DefaultWeightedEdge> remover = new OptimalBackEdgeRemover(classReferencesGraph);
        Set<DefaultWeightedEdge> optimalEdges = remover.findOptimalBackEdgesToRemove();

        // all are considered back edges since this is a cycle
        assertEquals(3, optimalEdges.size());
    }

    @Test
    void twoBackEdges() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addVertex("D");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");
        classReferencesGraph.addEdge("C", "D");
        classReferencesGraph.addEdge("C", "A"); // back edge
        classReferencesGraph.addEdge("D", "A"); // back edge

        OptimalBackEdgeRemover<String, DefaultWeightedEdge> remover = new OptimalBackEdgeRemover(classReferencesGraph);
        Set<DefaultWeightedEdge> optimalEdges = remover.findOptimalBackEdgesToRemove();

        assertEquals(2, optimalEdges.size());
    }

    @Test
    void multi() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addVertex("D");

        // Cycle 1
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");
        classReferencesGraph.addEdge("C", "D");
        classReferencesGraph.addEdge("B", "A"); // Adding a cycle
        classReferencesGraph.addEdge("C", "A"); // Adding a cycle
        classReferencesGraph.addEdge("D", "A"); // Adding a cycle

        // Cycle 2
        classReferencesGraph.addVertex("E");
        classReferencesGraph.addVertex("F");
        classReferencesGraph.addVertex("G");
        classReferencesGraph.addVertex("H");
        classReferencesGraph.addEdge("E", "F");
        classReferencesGraph.addEdge("F", "G");
        classReferencesGraph.addEdge("G", "H");
        classReferencesGraph.addEdge("H", "E"); // create cycle

        classReferencesGraph.addEdge("A", "E");
        classReferencesGraph.addEdge("E", "A"); // create cycle between cycles

        OptimalBackEdgeRemover<String, DefaultWeightedEdge> remover = new OptimalBackEdgeRemover(classReferencesGraph);
        Set<DefaultWeightedEdge> optimalEdges = remover.findOptimalBackEdgesToRemove();

        assertEquals(1, optimalEdges.size());
        assertEquals("(A : B)", new ArrayList<>(optimalEdges).get(0).toString());
    }
}
