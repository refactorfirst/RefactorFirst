package org.hjug.feedback.vertex.approximate;

import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class FeedbackVertexSetExample {
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

        // Define vertex weights (optional)
        Map<String, Double> weights = Map.of("A", 1.0, "B", 2.0, "C", 1.5, "D", 1.0);

        // Define special vertices (optional - all vertices by default)
        Set<String> specialVertices = Set.of("A", "B", "C", "D");

        // Solve the FVS problem
        FeedbackVertexSetSolver<String, DefaultEdge> solver =
                new FeedbackVertexSetSolver<>(graph, specialVertices, weights, 0.1);
        FeedbackVertexSetResult<String> result = solver.solve();

        System.out.println("Feedback vertex set: " + result.getFeedbackVertices());
        System.out.println("Solution size: " + result.size());
    }
}
