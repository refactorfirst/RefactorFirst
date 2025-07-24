package org.hjug.feedback.vertex.kernelized;

import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class DirectedFeedbackVertexSetExample {
    public static void main(String[] args) {
        // Create a directed graph with cycles
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        // Add edges creating cycles
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A"); // Creates cycle A->B->C->A
        graph.addEdge("C", "D");
        graph.addEdge("D", "A"); // Creates cycle A->B->C->D->A

        // Define treewidth modulator (optional)
        Set<String> modulator = Set.of("A", "C");

        // Define vertex weights (optional)
        Map<String, Double> weights = Map.of("A", 1.0, "B", 2.0, "C", 1.5, "D", 1.0);

        // Solve the DFVS problem with treewidth parameter η=2
        DirectedFeedbackVertexSetSolver<String, DefaultEdge> solver =
                new DirectedFeedbackVertexSetSolver<>(graph, modulator, weights, 2);
        DirectedFeedbackVertexSetResult<String> result = solver.solve(3);

        System.out.println("Feedback vertex set: " + result.getFeedbackVertices());
        System.out.println("Solution size: " + result.size());
    }
}
