package org.hjug.feedback.arc.approximate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Benchmark tests for performance evaluation
 */
class FeedbackArcSetBenchmarkTest {

    @Test
    @DisplayName("Benchmark: Dense graphs with varying sizes")
    void benchmarkDenseGraphs() {
        int[] sizes = {10, 25, 50, 100};

        System.out.println("=== Dense Graph Benchmark ===");
        System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "Size", "Vertices", "Edges", "FAS Size", "Time (ms)");

        for (int size : sizes) {
            Graph<String, DefaultEdge> graph = createDenseGraph(size);

            long startTime = System.currentTimeMillis();
            FeedbackArcSetSolver<String, DefaultEdge> solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();
            long endTime = System.currentTimeMillis();

            System.out.printf(
                    "%-10d %-15d %-15d %-15d %-15d%n",
                    size,
                    graph.vertexSet().size(),
                    graph.edgeSet().size(),
                    result.getFeedbackArcCount(),
                    endTime - startTime);
        }
    }

    @Test
    @DisplayName("Benchmark: Sparse graphs with varying sizes")
    void benchmarkSparseGraphs() {
        int[] sizes = {50, 100, 200, 500, 1000, 1500};

        System.out.println("=== Sparse Graph Benchmark ===");
        System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "Size", "Vertices", "Edges", "FAS Size", "Time (ms)");

        for (int size : sizes) {
            Graph<String, DefaultEdge> graph = createSparseGraph(size);

            long startTime = System.currentTimeMillis();
            FeedbackArcSetSolver<String, DefaultEdge> solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();
            long endTime = System.currentTimeMillis();

            System.out.printf(
                    "%-10d %-15d %-15d %-15d %-15d%n",
                    size,
                    graph.vertexSet().size(),
                    graph.edgeSet().size(),
                    result.getFeedbackArcCount(),
                    endTime - startTime);
        }
    }

    private Graph<String, DefaultEdge> createDenseGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        List<String> vertices = new ArrayList<>(graph.vertexSet());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add edges with high probability
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j && random.nextDouble() < 0.6) {
                    graph.addEdge(vertices.get(i), vertices.get(j));
                }
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createSparseGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        List<String> vertices = new ArrayList<>(graph.vertexSet());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add approximately 2*size edges (sparse)
        int targetEdges = size * 2;
        int addedEdges = 0;

        while (addedEdges < targetEdges) {
            String source = vertices.get(random.nextInt(vertices.size()));
            String target = vertices.get(random.nextInt(vertices.size()));

            if (!source.equals(target) && !graph.containsEdge(source, target)) {
                graph.addEdge(source, target);
                addedEdges++;
            }
        }

        return graph;
    }
}
