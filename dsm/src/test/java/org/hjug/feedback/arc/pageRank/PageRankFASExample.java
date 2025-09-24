package org.hjug.feedback.arc.pageRank;


import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Set;

/**
 * Example usage of the PageRankFAS algorithm
 * Demonstrates how to use the algorithm with different types of graphs
 */
public class PageRankFASExample {

    public static void main(String[] args) {
        System.out.println("PageRankFAS Algorithm Examples");
        System.out.println("===============================");

        // Example 1: Simple cycle
        System.out.println("\n1. Simple Cycle Example:");
        demonstrateSimpleCycle();

        // Example 2: Multiple cycles
        System.out.println("\n2. Multiple Cycles Example:");
        demonstrateMultipleCycles();

        // Example 3: Complex graph with nested cycles
        System.out.println("\n3. Complex Graph Example:");
        demonstrateComplexGraph();

        // Example 4: Performance comparison
        System.out.println("\n4. Performance Comparison:");
        demonstratePerformanceComparison();

        // Example 5: Custom PageRank iterations
        System.out.println("\n5. Custom PageRank Iterations:");
        demonstrateCustomIterations();
    }

    /**
     * Demonstrate PageRankFAS on a simple 3-node cycle
     */
    private static void demonstrateSimpleCycle() {
        // Create a simple cycle: A -> B -> C -> A
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        DefaultEdge e1 = graph.addEdge("A", "B");
        DefaultEdge e2 = graph.addEdge("B", "C");
        DefaultEdge e3 = graph.addEdge("C", "A");

        System.out.println("Original graph: A -> B -> C -> A");
        System.out.println("Edges: " + graph.edgeSet().size());
        System.out.println("Vertices: " + graph.vertexSet().size());

        // Apply PageRankFAS
        PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
        });
        Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();

        System.out.println("Feedback Arc Set size: " + feedbackArcSet.size());
        System.out.println("FAS edges: " + feedbackArcSet);

        // Verify the result
        verifyAcyclicity(graph, feedbackArcSet);
    }

    /**
     * Demonstrate PageRankFAS on a graph with multiple cycles
     */
    private static void demonstrateMultipleCycles() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // First cycle: A -> B -> C -> A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        // Second cycle: D -> E -> F -> D
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");

        // Connect the cycles
        graph.addEdge("C", "D");

        // Add a larger cycle: A -> B -> E -> F -> A
        graph.addEdge("B", "E");
        graph.addEdge("F", "A");

        System.out.println("Graph with multiple interconnected cycles");
        System.out.println("Edges: " + graph.edgeSet().size());
        System.out.println("Vertices: " + graph.vertexSet().size());

        // Apply PageRankFAS
        PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
        });
        long startTime = System.currentTimeMillis();
        Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
        long endTime = System.currentTimeMillis();

        System.out.println("Feedback Arc Set size: " + feedbackArcSet.size());
        System.out.println("Computation time: " + (endTime - startTime) + "ms");

        verifyAcyclicity(graph, feedbackArcSet);
    }

    /**
     * Demonstrate PageRankFAS on a complex graph
     */
    private static void demonstrateComplexGraph() {
        Graph<String, DefaultEdge> graph = createComplexTestGraph();

        System.out.println("Complex graph with nested and overlapping cycles");
        System.out.println("Edges: " + graph.edgeSet().size());
        System.out.println("Vertices: " + graph.vertexSet().size());

        // Apply PageRankFAS with timing
        PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
        });
        long startTime = System.currentTimeMillis();
        Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
        long endTime = System.currentTimeMillis();

        System.out.println("Feedback Arc Set size: " + feedbackArcSet.size());
        System.out.println("Computation time: " + (endTime - startTime) + "ms");
        System.out.println("FAS ratio: " + String.format("%.2f%%",
                100.0 * feedbackArcSet.size() / graph.edgeSet().size()));

        verifyAcyclicity(graph, feedbackArcSet);
    }

    /**
     * Compare performance with different graph sizes
     */
    private static void demonstratePerformanceComparison() {
        int[] graphSizes = {50, 100, 200};

        System.out.println("Performance comparison on different graph sizes:");
        System.out.println("Size\tEdges\tFAS Size\tTime (ms)\tFAS Ratio");
        System.out.println("----\t-----\t--------\t---------\t---------");

        for (int size : graphSizes) {
            Graph<String, DefaultEdge> graph = createRandomGraph(size, size * 2);

            PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            long startTime = System.currentTimeMillis();
            Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
            long endTime = System.currentTimeMillis();

            double fasRatio = 100.0 * feedbackArcSet.size() / graph.edgeSet().size();

            System.out.printf("%d\t%d\t%d\t\t%d\t\t%.2f%%\n",
                    size, graph.edgeSet().size(), feedbackArcSet.size(),
                    (endTime - startTime), fasRatio);
        }
    }

    /**
     * Demonstrate the effect of different PageRank iteration counts
     */
    private static void demonstrateCustomIterations() {
        Graph<String, DefaultEdge> graph = createComplexTestGraph();
        int[] iterations = {1, 3, 5, 10, 20};

        System.out.println("Effect of PageRank iterations on FAS quality:");
        System.out.println("Iterations\tFAS Size\tTime (ms)");
        System.out.println("----------\t--------\t---------");

        for (int iter : iterations) {
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);

            PageRankFAS<String, DefaultEdge> pageRankFAS =
                    new PageRankFAS<>(testGraph, iter, new SuperTypeToken<>() {
                    });

            long startTime = System.currentTimeMillis();
            Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
            long endTime = System.currentTimeMillis();

            System.out.printf("%d\t\t%d\t\t%d\n",
                    iter, feedbackArcSet.size(), (endTime - startTime));
        }
    }

    /**
     * Create a complex test graph with various cycle structures
     */
    private static Graph<String, DefaultEdge> createComplexTestGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create vertices
        for (int i = 0; i < 15; i++) {
            graph.addVertex("V" + i);
        }

        // Create various cycle patterns

        // Triangle cycles
        graph.addEdge("V0", "V1");
        graph.addEdge("V1", "V2");
        graph.addEdge("V2", "V0");

        graph.addEdge("V3", "V4");
        graph.addEdge("V4", "V5");
        graph.addEdge("V5", "V3");

        // Square cycle
        graph.addEdge("V6", "V7");
        graph.addEdge("V7", "V8");
        graph.addEdge("V8", "V9");
        graph.addEdge("V9", "V6");

        // Overlapping cycles
        graph.addEdge("V2", "V6");  // Connect triangle to square
        graph.addEdge("V8", "V0");  // Create larger cycle

        // Additional complexity
        graph.addEdge("V10", "V11");
        graph.addEdge("V11", "V12");
        graph.addEdge("V12", "V13");
        graph.addEdge("V13", "V14");
        graph.addEdge("V14", "V10");  // Pentagon cycle

        // Connect to main component
        graph.addEdge("V5", "V10");
        graph.addEdge("V12", "V3");

        return graph;
    }

    /**
     * Create a random graph for testing
     */
    private static Graph<String, DefaultEdge> createRandomGraph(int numVertices, int numEdges) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < numVertices; i++) {
            graph.addVertex("V" + i);
        }

        // Add random edges
        java.util.Random random = new java.util.Random(42); // Fixed seed for reproducibility
        java.util.List<String> vertices = new java.util.ArrayList<>(graph.vertexSet());

        int edgesAdded = 0;
        int attempts = 0;
        while (edgesAdded < numEdges && attempts < numEdges * 3) {
            String source = vertices.get(random.nextInt(vertices.size()));
            String target = vertices.get(random.nextInt(vertices.size()));

            if (!source.equals(target) && !graph.containsEdge(source, target)) {
                graph.addEdge(source, target);
                edgesAdded++;
            }
            attempts++;
        }

        return graph;
    }

    /**
     * Copy a graph
     */
    private static Graph<String, DefaultEdge> copyGraph(Graph<String, DefaultEdge> original) {
        Graph<String, DefaultEdge> copy = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        original.vertexSet().forEach(copy::addVertex);

        // Add edges
        original.edgeSet().forEach(edge -> {
            String source = original.getEdgeSource(edge);
            String target = original.getEdgeTarget(edge);
            copy.addEdge(source, target);
        });

        return copy;
    }

    /**
     * Verify that removing the FAS makes the graph acyclic
     */
    private static void verifyAcyclicity(Graph<String, DefaultEdge> originalGraph,
                                         Set<DefaultEdge> feedbackArcSet) {
        Graph<String, DefaultEdge> testGraph = copyGraph(originalGraph);

        // Remove FAS edges
        feedbackArcSet.forEach(testGraph::removeEdge);

        // Check if acyclic
        PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(testGraph, new SuperTypeToken<>() {
        });
        Set<DefaultEdge> remainingFAS = verifier.computeFeedbackArcSet();

        if (remainingFAS.isEmpty()) {
            System.out.println("✓ Verification successful: Graph is acyclic after FAS removal");
        } else {
            System.out.println("✗ Verification failed: " + remainingFAS.size() +
                    " cycles remain after FAS removal");
        }
    }
}

