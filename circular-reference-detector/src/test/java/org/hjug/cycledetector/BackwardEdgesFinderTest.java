package org.hjug.cycledetector;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        List<DefaultWeightedEdge> backwardEdges = finder.findBackwardEdges();

        Assertions.assertEquals("(D : A)", backwardEdges.get(0).toString());
        Assertions.assertEquals(4, graph.getEdgeWeight(backwardEdges.get(0)));

        Assertions.assertEquals("(C : A)", backwardEdges.get(1).toString());
        Assertions.assertEquals(5, graph.getEdgeWeight(backwardEdges.get(1)));
    }

}
