package org.hjug.feedback.arc.approximate;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class FeedbackArcSetExample {
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

        // Solve the FAS problem
        FeedbackArcSetSolver<String, DefaultEdge> solver = new FeedbackArcSetSolver<>(graph);
        FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

        System.out.println("Vertex sequence: " + result.getVertexSequence());
        System.out.println("Feedback arc count: " + result.getFeedbackArcCount());
        System.out.println("Feedback arcs: " + result.getFeedbackArcs());
    }
}
