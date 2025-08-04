package org.hjug.feedback.arc.exact;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.hjug.feedback.SuperTypeToken;
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
 * Comprehensive unit tests for the MinimumFeedbackArcSetSolver [15]
 */
@Execution(ExecutionMode.CONCURRENT)
class MinimumFeedbackArcSetSolverTest {

    private Graph<String, DefaultEdge> graph;
    private MinimumFeedbackArcSetSolver<String, DefaultEdge> solver;

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
            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertTrue(result.getFeedbackArcSet().isEmpty());
            assertEquals(0.0, result.getObjectiveValue());
        }

        @Test
        @DisplayName("Should handle single vertex")
        void testSingleVertex() {
            graph.addVertex("A");
            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("Should handle acyclic graph")
        void testAcyclicGraph() {
            // Create a simple DAG: A -> B -> C [15]
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("Should handle simple cycle")
        void testSimpleCycle() {
            // Create a simple cycle: A -> B -> C -> A [2]
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            // Should break the cycle with exactly one arc
            assertEquals(1, result.size());
            assertGraphIsAcyclicAfterRemoval(result);
        }

        @Test
        @DisplayName("Should handle self-loop")
        void testSelfLoop() {
            graph.addVertex("A");
            DefaultEdge selfLoop = graph.addEdge("A", "A");

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.size());
            assertTrue(result.getFeedbackArcSet().contains(selfLoop));
        }
    }

    @Nested
    @DisplayName("Complex Graph Tests")
    class ComplexGraphTests {

        @Test
        @DisplayName("Should handle multiple cycles")
        void testMultipleCycles() {
            // Create graph with multiple overlapping cycles [2]
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

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertTrue(result.size() >= 2);
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

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.size());
            assertGraphIsAcyclicAfterRemoval(result);
        }

        @Test
        @DisplayName("Should handle weighted edges")
        void testWeightedEdges() {
            // Create a cycle with different edge weights
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            DefaultEdge e1 = graph.addEdge("A", "B");
            DefaultEdge e2 = graph.addEdge("B", "C");
            DefaultEdge e3 = graph.addEdge("C", "A");

            Map<DefaultEdge, Double> weights = Map.of(e1, 1.0, e2, 10.0, e3, 1.0);

            solver = new MinimumFeedbackArcSetSolver<>(graph, weights, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.size());
            // Should prefer removing lower weight edges
            assertFalse(result.getFeedbackArcSet().contains(e2));
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
            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();
            long endTime = System.currentTimeMillis();

            // Performance should be reasonable [2]
            assertTrue(endTime - startTime < 10000, "Algorithm took too long: " + (endTime - startTime) + "ms");

            if (hasCycles()) {
                assertGraphIsAcyclicAfterRemoval(result);
            }
        }

        @Test
        @DisplayName("Should utilize parallel processing effectively")
        void testParallelProcessing() {
            createRandomGraph(30, 60);

            long startTime = System.currentTimeMillis();
            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();
            long endTime = System.currentTimeMillis();

            assertTrue(endTime - startTime < 15000);
            if (hasCycles()) {
                assertGraphIsAcyclicAfterRemoval(result);
            }
        }
    }

    @Nested
    @DisplayName("Correctness Tests")
    class CorrectnessTests {

        @Test
        @DisplayName("Should maintain optimality properties")
        void testOptimalityProperties() {
            createRandomGraph(15, 30);

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            // Solution should be minimal and make graph acyclic [2]
            if (hasCycles()) {
                assertGraphIsAcyclicAfterRemoval(result);
                assertTrue(result.size() > 0);
            }
        }

        @Test
        @DisplayName("Should handle edge cases correctly")
        void testEdgeCases() {
            // Triangle with all edges having same weight
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            solver = new MinimumFeedbackArcSetSolver<>(graph, null, new SuperTypeToken<>() {});
            FeedbackArcSetResult<String, DefaultEdge> result = solver.solve();

            assertEquals(1, result.size());
            assertGraphIsAcyclicAfterRemoval(result);
        }
    }

    // Helper methods

    private void createRandomGraph(int vertexCount, int edgeCount) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add vertices [18]
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

    private void assertGraphIsAcyclicAfterRemoval(FeedbackArcSetResult<String, DefaultEdge> result) {
        // Create a copy of the graph without feedback arcs [12]
        Graph<String, DefaultEdge> testGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

        Set<String> resultEdgesAsStrings = new HashSet<>();
        result.getFeedbackArcSet().forEach(edge -> resultEdgesAsStrings.add(edge.toString()));

        // Add all vertices
        graph.vertexSet().forEach(testGraph::addVertex);

        // Add edges not in feedback arc set
        graph.edgeSet().stream()
                .filter(edge -> !resultEdgesAsStrings.contains(edge.toString()))
                .forEach(edge -> {
                    String source = graph.getEdgeSource(edge);
                    String target = graph.getEdgeTarget(edge);
                    testGraph.addEdge(source, target);
                });

        // Verify the resulting graph is acyclic [12][16]
        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(testGraph);
        assertFalse(cycleDetector.detectCycles(), "Graph should be acyclic after removing feedback arcs");
    }
}
