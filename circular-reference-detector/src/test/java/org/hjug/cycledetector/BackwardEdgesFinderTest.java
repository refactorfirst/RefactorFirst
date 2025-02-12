package org.hjug.cycledetector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

class BackwardEdgesFinderTest {

    @Test
    void findBackwardEdges() {
        Graph<String, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.setEdgeWeight(graph.addEdge("A", "B"), 1);
        graph.setEdgeWeight(graph.addEdge("B", "C"), 2);
        graph.setEdgeWeight(graph.addEdge("C", "D"), 3);
        graph.setEdgeWeight(graph.addEdge("D", "A"), 4);
        graph.setEdgeWeight(graph.addEdge("C", "A"), 5);

        BackwardEdgesFinder<String> finder = new BackwardEdgesFinder<>(graph);
        Map<DefaultWeightedEdge, Integer> backwardEdgesWithCounts = finder.findBackwardEdges();
        List<DefaultWeightedEdge> backwardEdges = new ArrayList<>(backwardEdgesWithCounts.keySet());

        assertEquals("(D : A)", backwardEdges.get(0).toString());
        assertEquals(4, graph.getEdgeWeight(backwardEdges.get(0)));
        assertEquals(1, backwardEdgesWithCounts.get(backwardEdges.get(0)));

        assertEquals("(C : A)", backwardEdges.get(1).toString());
        assertEquals(5, graph.getEdgeWeight(backwardEdges.get(1)));
        assertEquals(1, backwardEdgesWithCounts.get(backwardEdges.get(1)));
    }

    @Test
    void findBackwardEdgesWithSameWeight() {
        Graph<String, DefaultWeightedEdge> graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.setEdgeWeight(graph.addEdge("A", "B"), 1);
        graph.setEdgeWeight(graph.addEdge("B", "C"), 2);
        graph.setEdgeWeight(graph.addEdge("C", "D"), 3);
        graph.setEdgeWeight(graph.addEdge("D", "A"), 4);
        graph.setEdgeWeight(graph.addEdge("C", "A"), 4);

        BackwardEdgesFinder<String> finder = new BackwardEdgesFinder<>(graph);
        Map<DefaultWeightedEdge, Integer> backwardEdgesWithCounts = finder.findBackwardEdges();
        List<DefaultWeightedEdge> backwardEdges = new ArrayList<>(backwardEdgesWithCounts.keySet());

        assertEquals("(C : A)", backwardEdges.get(0).toString());
        assertEquals(4, graph.getEdgeWeight(backwardEdges.get(0)));
        assertEquals(1, backwardEdgesWithCounts.get(backwardEdges.get(0)));

        assertEquals("(D : A)", backwardEdges.get(1).toString());
        assertEquals(4, graph.getEdgeWeight(backwardEdges.get(1)));
        assertEquals(1, backwardEdgesWithCounts.get(backwardEdges.get(1)));
    }
}
