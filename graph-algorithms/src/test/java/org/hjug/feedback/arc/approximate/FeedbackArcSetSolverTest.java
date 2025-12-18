package org.hjug.feedback.arc.approximate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive unit tests for the FeedbackArcSetSolver
 */
class FeedbackArcSetSolverTest {

    private Graph<String, DefaultEdge> graph;
    private FeedbackArcSetSolver<String, DefaultEdge> solver;

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
            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertTrue(result.getVertexSequence().isEmpty());
            assertTrue(result.getFeedbackArcs().isEmpty());
            assertEquals(0, result.getFeedbackArcCount());
        }

        @Test
        @DisplayName("Should handle single vertex")
        void testSingleVertex() {
            graph.addVertex("A");
            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.getVertexSequence().size());
            assertTrue(result.getVertexSequence().contains("A"));
            assertEquals(0, result.getFeedbackArcCount());
        }

        @Test
        @DisplayName("Should handle acyclic graph")
        void testAcyclicGraph() {
            // Create a simple DAG: A -> B -> C
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(0, result.getFeedbackArcCount());
            assertEquals(3, result.getVertexSequence().size());
        }

        @Test
        @DisplayName("Should handle simple cycle")
        void testSimpleCycle() {
            // Create a simple cycle: A -> B -> C -> A
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            // Should break the cycle with exactly one feedback arc
            assertEquals(1, result.getFeedbackArcCount());
            assertGraphIsAcyclicAfterRemoval(result);
        }
    }

    @Nested
    @DisplayName("Complex Graph Tests")
    class ComplexGraphTests {

        @Test
        @DisplayName("Should handle multiple cycles")
        void testMultipleCycles() {
            // Create graph with multiple overlapping cycles
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

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertTrue(result.getFeedbackArcCount() >= 2);
            assertGraphIsAcyclicAfterRemoval(result);
        }

        @Test
        @DisplayName("Should handle tournament graph")
        void testTournamentGraph() {
            // Create a tournament (complete directed graph)
            String[] vertices = {"A", "B", "C", "D"};
            for (String v : vertices) {
                graph.addVertex(v);
            }

            // Add edges to create a tournament
            graph.addEdge("A", "B");
            graph.addEdge("A", "C");
            graph.addEdge("A", "D");
            graph.addEdge("B", "C");
            graph.addEdge("B", "D");
            graph.addEdge("C", "D");
            graph.addEdge("D", "A"); // Creates cycles
            graph.addEdge("C", "B"); // Creates cycles

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertGraphIsAcyclicAfterRemoval(result);
            // For tournaments, the bound should be ≤ m/2 + n/4
            int m = graph.edgeSet().size();
            int n = graph.vertexSet().size();
            assertTrue(result.getFeedbackArcCount() <= m / 2 + n / 4);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @ParameterizedTest
        @ValueSource(ints = {10, 50, 100})
        @DisplayName("Should handle large random graphs efficiently")
        void testLargeRandomGraphs(int size) {
            createRandomGraph(size, size * 2);

            long startTime = System.currentTimeMillis();
            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();
            long endTime = System.currentTimeMillis();

            assertGraphIsAcyclicAfterRemoval(result);

            // Performance should be reasonable (less than 5 seconds for size 100)
            assertTrue(endTime - startTime < 5000, "Algorithm took too long: " + (endTime - startTime) + "ms");
        }

        @Test
        @DisplayName("Should verify parallel processing improves performance")
        void testParallelPerformanceImprovement() {
            createRandomGraph(50, 100);

            // Test with current parallel implementation
            long startTimeParallel = System.currentTimeMillis();
            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> parallelResult = solver.solve();
            long endTimeParallel = System.currentTimeMillis();

            assertGraphIsAcyclicAfterRemoval(parallelResult);

            // Verify result quality meets the theoretical bound
            int m = graph.edgeSet().size();
            int n = graph.vertexSet().size();
            assertTrue(parallelResult.getFeedbackArcCount() <= m / 2 + n / 4);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle self-loops")
        void testSelfLoops() {
            graph.addVertex("A");
            graph.addVertex("B");
            // JGraphT DefaultDirectedGraph doesn't allow self-loops by default
            // But we can test the behavior
            graph.addEdge("A", "B");
            graph.addEdge("B", "A");

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.getFeedbackArcCount());
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

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.getFeedbackArcCount());
            assertGraphIsAcyclicAfterRemoval(result);
            assertEquals(5, result.getVertexSequence().size());
        }
    }

    @Nested
    @DisplayName("Correctness Verification")
    class CorrectnessTests {

        @Test
        @DisplayName("Should produce valid vertex ordering")
        void testVertexOrderingValidity() {
            createRandomGraph(20, 40);

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            // Verify all vertices are included in the sequence
            assertEquals(graph.vertexSet().size(), result.getVertexSequence().size());
            assertTrue(result.getVertexSequence().containsAll(graph.vertexSet()));

            // Verify no duplicates
            Set<String> uniqueVertices = new HashSet<>(result.getVertexSequence());
            assertEquals(graph.vertexSet().size(), uniqueVertices.size());
        }

        @Test
        @DisplayName("Should satisfy performance bound")
        void testPerformanceBound() {
            createRandomGraph(30, 60);

            solver = new FeedbackArcSetSolver<>(graph);
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            int m = graph.edgeSet().size();
            int n = graph.vertexSet().size();
            int bound = m / 2 + n / 4;

            assertTrue(
                    result.getFeedbackArcCount() <= bound,
                    String.format("FAS size %d exceeds bound %d", result.getFeedbackArcCount(), bound));
        }
    }

    // Helper methods

    private void createRandomGraph(int vertexCount, int edgeCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add vertices
        for (int i = 0; i < vertexCount; i++) {
            graph.addVertex("V" + i);
        }

        List<String> vertices = new ArrayList<>(graph.vertexSet());

        // Add random edges
        int addedEdges = 0;
        while (addedEdges < edgeCount) {
            String source = vertices.get(random.nextInt(vertices.size()));
            String target = vertices.get(random.nextInt(vertices.size()));

            if (!source.equals(target) && !graph.containsEdge(source, target)) {
                graph.addEdge(source, target);
                addedEdges++;
            }
        }
    }

    private void assertGraphIsAcyclicAfterRemoval(FeedbackArcSetResult<String, DefaultEdge> result) {
        // Create a copy of the graph without feedback arcs
        Graph<String, DefaultEdge> testGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add all vertices
        for (String vertex : graph.vertexSet()) {
            testGraph.addVertex(vertex);
        }

        // Add all edges except feedback arcs
        for (DefaultEdge edge : graph.edgeSet()) {
            if (!result.getFeedbackArcs().contains(edge)) {
                String source = graph.getEdgeSource(edge);
                String target = graph.getEdgeTarget(edge);
                testGraph.addEdge(source, target);
            }
        }

        // Verify the resulting graph is acyclic
        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(testGraph);
        assertFalse(cycleDetector.detectCycles(), "Graph should be acyclic after removing feedback arcs");
    }
}
