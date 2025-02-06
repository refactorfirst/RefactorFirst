package org.hjug.dsm;

import java.util.List;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DSMTest {

    Graph<String, DefaultWeightedEdge> graph;
    List<String> orderedVertices;

    @BeforeEach
    void setUp() {
        // Create a weighted graph
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        // Add vertices
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");

        // Add edges with weights
        Graphs.addEdgeWithVertices(graph, "A", "B", 1.0);
        Graphs.addEdgeWithVertices(graph, "A", "C", 1.0);
        Graphs.addEdgeWithVertices(graph, "B", "D", 3.0);
        Graphs.addEdgeWithVertices(graph, "C", "D", 4.0);
        Graphs.addEdgeWithVertices(graph, "D", "A", 5.0); // Adding a cycle

        // Order nodes according to the specified logic
        orderedVertices = DSM.orderVertices(graph);
    }

    @Test
    void minWeightEdges() {
        // Identify which edge above the diagonal in the set of cycles should be removed first
        List<DefaultWeightedEdge> edgesToRemove = DSM.identifyAllBackwardMinWeightEdgesToRemove(graph, orderedVertices);

        // Print the DSM
        DSM.printDSM(graph, orderedVertices);

        // find the edges to remove
        if (!edgesToRemove.isEmpty()) {
            for (DefaultWeightedEdge edgeToRemove : edgesToRemove) {
                System.out.println("Edge to remove: " + graph.getEdgeSource(edgeToRemove) + " -> "
                        + graph.getEdgeTarget(edgeToRemove));
            }
        } else {
            System.out.println("No cycles detected.");
        }

        // Contains "(A : B)" and "(A : C)"
        Assertions.assertEquals(2, edgesToRemove.size());
    }

    @Test
    void optimalBakcwardEdgeToRemove() {
        // Identify which edge above the diagonal in the set of cycles should be removed first
        DefaultWeightedEdge edgeToRemove = DSM.identifyOptimalBakcwardEdgeToRemove(graph, orderedVertices);
        Assertions.assertEquals("(A : B)", edgeToRemove.toString());
    }

    @Test
    void optimalEdge() {
        // Identify which edge above the diagonal in the set of cycles should be removed first
        List<DefaultWeightedEdge> edges = DSM.getAllBackwardEdges(graph, orderedVertices);
        Assertions.assertEquals(3, edges.size());
    }
}
