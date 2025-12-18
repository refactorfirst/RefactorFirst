package org.hjug.feedback.vertex.approximate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Performance benchmark tests[8]
 */
class FeedbackVertexSetBenchmarkTest {

    @Test
    @DisplayName("Benchmark: Various graph sizes and densities")
    void benchmarkGraphSizes() {
        int[] sizes = {20, 50, 100, 200};
        double[] densities = {0.1, 0.3, 0.5};

        System.out.println("=== Feedback Vertex Set Benchmark ===");
        System.out.printf("%-10s %-15s %-15s %-15s %-15s%n", "Size", "Density", "Vertices", "Edges", "Time (ms)");

        for (int size : sizes) {
            for (double density : densities) {
                Graph<String, DefaultEdge> graph = createRandomGraph(size, density);

                long startTime = System.currentTimeMillis();
                FeedbackVertexSetSolver<String, DefaultEdge> solver =
                        new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
                FeedbackVertexSetResult<String> result = solver.solve();
                long endTime = System.currentTimeMillis();

                System.out.printf(
                        "%-10d %-15.1f %-15d %-15d %-15d%n",
                        size, density, graph.vertexSet().size(), graph.edgeSet().size(), endTime - startTime);
            }
        }
    }

    private Graph<String, DefaultEdge> createRandomGraph(int size, double density) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        IntStream.range(0, size).forEach(i -> graph.addVertex("V" + i));

        List<String> vertices = new ArrayList<>(graph.vertexSet());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int maxEdges = size * (size - 1);
        int targetEdges = (int) (maxEdges * density);

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
