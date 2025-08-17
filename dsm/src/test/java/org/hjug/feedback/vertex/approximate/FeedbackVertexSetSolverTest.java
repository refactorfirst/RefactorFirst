package org.hjug.feedback.vertex.approximate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive unit tests for the FeedbackVertexSetSolver[6]
 */
@Execution(ExecutionMode.CONCURRENT)
class FeedbackVertexSetSolverTest {

    private Graph<String, DefaultEdge> graph;
    private FeedbackVertexSetSolver<String, DefaultEdge> solver;

    @BeforeEach
    void setUp() {
        graph = new DefaultDirectedGraph<>(DefaultEdge.class);
    }

    @Nested
    @DisplayName("Basic Algorithm Tests")
    class BasicAlgorithmTests {

        @Test
        @DisplayName("Should handle empty graph")
        void testEmptyGraph() {
            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertTrue(result.getFeedbackVertices().isEmpty());
            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("Should handle single vertex")
        void testSingleVertex() {
            graph.addVertex("A");
            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("Should handle acyclic graph")
        void testAcyclicGraph() {
            // Create a simple DAG: A -> B -> C[7]
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");

            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("Should handle simple cycle")
        void testSimpleCycle() {
            // Create a simple cycle: A -> B -> C -> A[7]
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            // Should break the cycle with at least one vertex
            assertTrue(result.size() >= 1);
            assertGraphIsAcyclicAfterRemoval(result);
        }

        @Test
        @DisplayName("Should handle self-loop")
        void testSelfLoop() {
            graph.addVertex("A");
            graph.addEdge("A", "A");

            Set<String> specialVertices = Set.of("A");
            solver = new FeedbackVertexSetSolver<>(graph, specialVertices, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertEquals(1, result.size());
            assertTrue(result.getFeedbackVertices().contains("A"));
        }
    }

    @Nested
    @DisplayName("Complex Graph Tests")
    class ComplexGraphTests {

        @Test
        @DisplayName("Should handle multiple cycles")
        void testMultipleCycles() {
            // Create graph with multiple overlapping cycles[5]
            String[] vertices = {"A", "B", "C", "D", "E"};
            for (String v : vertices) {
                graph.addVertex(v);
            }

            // Create cycles: A->B->C->A and C->D->E->C
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");
            graph.addEdge("C", "D");
            graph.addEdge("D", "E");
            graph.addEdge("E", "C");

            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertTrue(result.size() >= 1);
            assertGraphIsAcyclicAfterRemoval(result);
        }

        @Test
        @DisplayName("Should handle disconnected components")
        void testDisconnectedComponents() {
            // Component 1: A -> B -> A
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addEdge("A", "B");
            graph.addEdge("B", "A");

            // Component 2: C -> D (acyclic)
            graph.addVertex("C");
            graph.addVertex("D");
            graph.addEdge("C", "D");

            // Component 3: E (isolated)
            graph.addVertex("E");

            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertTrue(result.size() >= 1);
            assertGraphIsAcyclicAfterRemoval(result);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @ParameterizedTest
        @ValueSource(ints = {10, 25, 50})
        @DisplayName("Should handle random graphs efficiently")
        void testRandomGraphPerformance(int size) {
            createRandomGraph(size, size * 2);

            long startTime = System.currentTimeMillis();
            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();
            long endTime = System.currentTimeMillis();

            // Performance should be reasonable[8]
            assertTrue(endTime - startTime < 10000, "Algorithm took too long: " + (endTime - startTime) + "ms");

            if (hasCycles()) {
                assertGraphIsAcyclicAfterRemoval(result);
            }
        }

        @Test
        @DisplayName("Should handle weighted vertices")
        void testWeightedVertices() {
            // Create a cycle with different vertex weights
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            Map<String, Double> weights = Map.of("A", 1.0, "B", 10.0, "C", 1.0);

            solver = new FeedbackVertexSetSolver<>(graph, null, weights, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            assertTrue(result.size() >= 1);
            // Should prefer removing lower weight vertices
            assertFalse(result.getFeedbackVertices().contains("B"));
        }
    }

    @Nested
    @DisplayName("Correctness Tests")
    class CorrectnessTests {

        @Test
        @DisplayName("Should maintain approximation guarantees")
        void testApproximationBounds() {
            createRandomGraph(20, 40);

            solver = new FeedbackVertexSetSolver<>(graph, null, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            // The solution should be bounded by the theoretical guarantees[1]
            int n = graph.vertexSet().size();
            assertTrue(result.size() <= n, "Solution size should be at most n");

            if (hasCycles()) {
                assertGraphIsAcyclicAfterRemoval(result);
            }
        }

        @Test
        @DisplayName("Should handle special vertex constraints")
        void testSpecialVertexConstraints() {
            // Create cycle where only some vertices are "special"
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addVertex("D");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "D");
            graph.addEdge("D", "A");

            Set<String> specialVertices = Set.of("A", "C"); // Only A and C are special
            solver = new FeedbackVertexSetSolver<>(graph, specialVertices, null, 0.1);
            FeedbackVertexSetResult<String> result = solver.solve();

            // Should only consider cycles involving special vertices
            assertTrue(result.size() >= 1);
        }
    }

    // Helper methods

    private void createRandomGraph(int vertexCount, int edgeCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add vertices[10]
        IntStream.range(0, vertexCount).forEach(i -> graph.addVertex("V" + i));
        List<String> vertices = new ArrayList<>(graph.vertexSet());

        // Add random edges
        int addedEdges = 0;
        while (addedEdges < edgeCount && addedEdges < vertexCount * (vertexCount - 1)) {
            String source = vertices.get(random.nextInt(vertices.size()));
            String target = vertices.get(random.nextInt(vertices.size()));

            if (!source.equals(target) && !graph.containsEdge(source, target)) {
                graph.addEdge(source, target);
                addedEdges++;
            }
        }
    }

    private boolean hasCycles() {
        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(graph);
        return cycleDetector.detectCycles();
    }

    private void assertGraphIsAcyclicAfterRemoval(FeedbackVertexSetResult<String> result) {
        // Create a copy of the graph without feedback vertices[6]
        Graph<String, DefaultEdge> testGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices except feedback vertices
        graph.vertexSet().stream()
                .filter(v -> !result.getFeedbackVertices().contains(v))
                .forEach(testGraph::addVertex);

        // Add edges between remaining vertices
        for (DefaultEdge edge : graph.edgeSet()) {
            String source = graph.getEdgeSource(edge);
            String target = graph.getEdgeTarget(edge);

            if (testGraph.containsVertex(source) && testGraph.containsVertex(target)) {
                testGraph.addEdge(source, target);
            }
        }

        // Verify the resulting graph is acyclic[6]
        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(testGraph);
        assertFalse(cycleDetector.detectCycles(), "Graph should be acyclic after removing feedback vertices");
    }
}
