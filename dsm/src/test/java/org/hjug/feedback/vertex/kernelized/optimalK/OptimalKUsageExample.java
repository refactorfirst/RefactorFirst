package org.hjug.feedback.vertex.kernelized.optimalK;

import org.hjug.feedback.SuperTypeToken;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetResult;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetSolver;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Example usage of OptimalKComputer with DirectedFeedbackVertexSetSolver integration
 */
public class OptimalKUsageExample {

    private static void main(String[] args) {
//    public static void main(String[] args) {
        // Create a sample directed graph with cycles
        Graph<String, DefaultEdge> graph = createSampleGraph();

        System.out.println("=== Optimal K Computation for DFVS ===\n");
        System.out.println("Graph: " + graph.vertexSet().size() + " vertices, "
                + graph.edgeSet().size() + " edges");

        // Compute optimal k
        OptimalKComputer<String, DefaultEdge> computer = new OptimalKComputer<>();

        try {
            // Compute bounds first
            System.out.println("\n1. Computing bounds...");
            OptimalKComputer.KBounds bounds = computer.computeKBounds(graph);
            System.out.println("Bounds: " + bounds);

            // Compute optimal k
            System.out.println("\n2. Computing optimal k...");
            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);
            System.out.println("Result: " + result);
            System.out.println("Feedback Vertex Set: " + result.getFeedbackVertexSet());

            // Integrate with DFVS solver
            System.out.println("\n3. Using optimal k with DFVS solver...");
            DirectedFeedbackVertexSetSolver<String, DefaultEdge> solver = new DirectedFeedbackVertexSetSolver<>(graph, null, null, 2, new SuperTypeToken<>() {});

            // Try with computed optimal k
            DirectedFeedbackVertexSetResult<String> solution = solver.solve(result.getOptimalK());
            boolean hasSolution = !solution.getFeedbackVertices().isEmpty();
            System.out.println("DFVS solver with k=" + result.getOptimalK() + ": "
                    + (hasSolution ? "Solution found" : "No solution"));

            if (hasSolution) {
                System.out.println("DFVS solution: " + solution);
            }

            // Try with k-1 to verify optimality
            if (result.getOptimalK() > 0) {
                boolean hasSuboptimal = !solver.solve(result.getOptimalK() - 1).getFeedbackVertices().isEmpty();
                System.out.println("DFVS solver with k=" + (result.getOptimalK() - 1) + ": "
                        + (hasSuboptimal ? "Solution found" : "No solution"));

                if (!hasSuboptimal) {
                    System.out.println("✓ Confirmed: k=" + result.getOptimalK() + " is optimal");
                }
            }

            // Demonstration with different graph types
            demonstrateOnDifferentGraphs();

        } finally {
            computer.shutdown();
        }
    }

    private static void demonstrateOnDifferentGraphs() {
        System.out.println("\n=== Testing on Different Graph Types ===");

        OptimalKComputer<String, DefaultEdge> computer = new OptimalKComputer<>(30, true);

        try {
            // Test on acyclic graph
            System.out.println("\n• Acyclic graph:");
            Graph<String, DefaultEdge> acyclic = createAcyclicGraph();
            testGraph(computer, acyclic, "Acyclic");

            // Test on simple cycle
            System.out.println("\n• Simple 3-cycle:");
            Graph<String, DefaultEdge> cycle = createSimpleCycle();
            testGraph(computer, cycle, "Simple cycle");

            // Test on complex graph
            System.out.println("\n• Complex graph with multiple SCCs:");
            Graph<String, DefaultEdge> complex = createComplexGraph();
            testGraph(computer, complex, "Complex graph");

        } finally {
            computer.shutdown();
        }
    }

    private static void testGraph(
            OptimalKComputer<String, DefaultEdge> computer, Graph<String, DefaultEdge> graph, String description) {
        System.out.println(description + " (" + graph.vertexSet().size() + " vertices, "
                + graph.edgeSet().size() + " edges)");

        OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);
        System.out.println("  Optimal k: " + result.getOptimalK());
        System.out.println("  Algorithm: " + result.getAlgorithmUsed());
        System.out.println("  Time: " + result.getComputationTimeMs() + "ms");

        // Validate with DFVS solver
        DirectedFeedbackVertexSetSolver<String, DefaultEdge> solver = new DirectedFeedbackVertexSetSolver<>(graph, null, null, 2, new SuperTypeToken<>() {});

        boolean hasOptimalSolution = !solver.solve(result.getOptimalK()).getFeedbackVertices().isEmpty();
        System.out.println("  DFVS validation: " + (hasOptimalSolution ? "✓" : "✗"));
    }

    private static Graph<String, DefaultEdge> createSampleGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create a graph with multiple cycles
        // Main cycle: A -> B -> C -> D -> A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "D");
        graph.addEdge("D", "A");

        // Secondary cycle: B -> E -> F -> C
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge("B", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "C");

        // Additional connections
        graph.addVertex("G");
        graph.addEdge("A", "G");
        graph.addEdge("G", "E");

        return graph;
    }

    private static Graph<String, DefaultEdge> createAcyclicGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");

        return graph;
    }

    private static Graph<String, DefaultEdge> createSimpleCycle() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        return graph;
    }

    private static Graph<String, DefaultEdge> createComplexGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // First SCC
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        // Second SCC
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge("D", "E");
        graph.addEdge("E", "D");

        // Third SCC
        graph.addVertex("F");
        graph.addVertex("G");
        graph.addVertex("H");
        graph.addEdge("F", "G");
        graph.addEdge("G", "H");
        graph.addEdge("H", "F");

        // Connections between SCCs
        graph.addEdge("C", "D");
        graph.addEdge("E", "F");
        graph.addEdge("H", "A");

        return graph;
    }
}
