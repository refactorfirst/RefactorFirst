package org.hjug.feedback.arc.exact;

import java.util.Map;
import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class MinimumFeedbackArcSetExample {
    public static void main(String[] args) {
        // Create a directed graph with cycles
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        // Add edges creating cycles
        DefaultEdge e1 = graph.addEdge("A", "B");
        DefaultEdge e2 = graph.addEdge("B", "C");
        DefaultEdge e3 = graph.addEdge("C", "A"); // Creates cycle A->B->C->A
        DefaultEdge e4 = graph.addEdge("C", "D");
        DefaultEdge e5 = graph.addEdge("D", "A"); // Creates cycle A->B->C->D->A

        // Define edge weights (optional)
        Map<DefaultEdge, Double> weights = Map.of(e1, 1.0, e2, 2.0, e3, 1.5, e4, 1.0, e5, 1.0);

        // Solve the minimum feedback arc set problem
        MinimumFeedbackArcSetSolver<String, DefaultEdge> solver =
                new MinimumFeedbackArcSetSolver<>(graph, weights, new SuperTypeToken<>() {});
        FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

        System.out.println("Minimum feedback arc set: " + result.getFeedbackArcSet());
        System.out.println("Objective value: " + result.getObjectiveValue());
        System.out.println("Solution size: " + result.size());
    }
}
