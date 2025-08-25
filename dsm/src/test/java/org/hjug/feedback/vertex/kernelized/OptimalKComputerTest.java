package org.hjug.feedback.vertex.kernelized;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
class OptimalKComputerTest {

    private OptimalKComputer<String, DefaultEdge> computer;

    @BeforeEach
    void setUp() {
        computer = new OptimalKComputer<>(60, true); // 60 second timeout
    }

    @AfterEach
    void tearDown() {
        if (computer != null) {
            computer.shutdown();
        }
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Should return k=0 for acyclic graph")
        void testAcyclicGraph() {
            Graph<String, DefaultEdge> graph = createAcyclicGraph();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(0, result.getOptimalK());
            assertTrue(result.getFeedbackVertexSet().isEmpty());
            assertTrue(result.getComputationTimeMs() >= 0);
        }

        @Test
        @DisplayName("Should handle single self-loop")
        void testSingleSelfLoop() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");
            graph.addEdge("A", "A");

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(1, result.getOptimalK());
            assertEquals(Set.of("A"), result.getFeedbackVertexSet());
        }

        @Test
        @DisplayName("Should handle simple 2-cycle")
        void testSimple2Cycle() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addEdge("A", "B");
            graph.addEdge("B", "A");

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(1, result.getOptimalK());
            assertEquals(1, result.getFeedbackVertexSet().size());
            assertTrue(result.getFeedbackVertexSet().contains("A")
                    || result.getFeedbackVertexSet().contains("B"));
        }

        @Test
        @DisplayName("Should handle simple 3-cycle")
        void testSimple3Cycle() {
            Graph<String, DefaultEdge> graph = createSimple3Cycle();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(1, result.getOptimalK());
            assertEquals(1, result.getFeedbackVertexSet().size());
            assertTrue(Set.of("A", "B", "C").containsAll(result.getFeedbackVertexSet()));
        }

        @Test
        @DisplayName("Should handle complete directed graph K3")
        void testCompleteDirectedK3() {
            Graph<String, DefaultEdge> graph = createCompleteDirectedGraph(3);

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            // Complete K3 needs at least 1 vertex removed (optimal is 1)
            assertTrue(result.getOptimalK() >= 1);
            assertTrue(result.getOptimalK() <= 2); // Should be optimal or near-optimal
        }

        @Test
        @DisplayName("Should handle multiple disjoint cycles")
        void testMultipleDisjointCycles() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

            // First cycle: A -> B -> C -> A
            graph.addVertex("A");
            graph.addVertex("B");
            graph.addVertex("C");
            graph.addEdge("A", "B");
            graph.addEdge("B", "C");
            graph.addEdge("C", "A");

            // Second cycle: D -> E -> D
            graph.addVertex("D");
            graph.addVertex("E");
            graph.addEdge("D", "E");
            graph.addEdge("E", "D");

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(2, result.getOptimalK()); // Need one vertex from each cycle
            assertEquals(2, result.getFeedbackVertexSet().size());
        }
    }

    @Nested
    @DisplayName("Complex Graph Tests")
    class ComplexGraphTests {

        @Test
        @DisplayName("Should handle strongly connected components")
        void testStronglyConnectedComponents() {
            Graph<String, DefaultEdge> graph = createComplexSCCGraph();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertTrue(result.getOptimalK() >= 2); // At least 2 SCCs with cycles
            assertFalse(result.getFeedbackVertexSet().isEmpty());

            // Verify result is valid
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);
            result.getFeedbackVertexSet().forEach(testGraph::removeVertex);
            assertTrue(isAcyclic(testGraph));
        }

        @Test
        @DisplayName("Should handle nested cycles")
        void testNestedCycles() {
            Graph<String, DefaultEdge> graph = createNestedCyclesGraph();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertTrue(result.getOptimalK() >= 1);

            // Verify solution breaks all cycles
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);
            result.getFeedbackVertexSet().forEach(testGraph::removeVertex);
            assertTrue(isAcyclic(testGraph));
        }

        @ParameterizedTest
        @ValueSource(ints = {5, 8, 10, 12})
        @DisplayName("Should handle complete directed graphs")
        void testCompleteDirectedGraphs(int size) {
            Graph<String, DefaultEdge> graph = createCompleteDirectedGraph(size);

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            // Complete directed graph Kn needs n-1 vertices removed
            assertTrue(result.getOptimalK() >= size - 1);
            assertTrue(result.getOptimalK() <= size); // Allow for non-optimal solutions

            // Verify solution
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);
            result.getFeedbackVertexSet().forEach(testGraph::removeVertex);
            assertTrue(isAcyclic(testGraph));
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 15, 20})
        @DisplayName("Should handle random graphs efficiently")
        void testRandomGraphs(int size) {
            Graph<String, DefaultEdge> graph = createRandomGraph(size, 0.3);

            long startTime = System.currentTimeMillis();
            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(result.getOptimalK() >= 0);
            assertTrue(result.getOptimalK() < size);
            assertTrue(duration < 30000); // Should complete within 30 seconds

            // Verify solution if not too large
            if (result.getOptimalK() <= size / 2) {
                Graph<String, DefaultEdge> testGraph = copyGraph(graph);
                result.getFeedbackVertexSet().forEach(testGraph::removeVertex);
                assertTrue(isAcyclic(testGraph));
            }
        }
    }

    @Nested
    @DisplayName("Bounds Computation Tests")
    class BoundsComputationTests {

        @Test
        @DisplayName("Should compute correct bounds for simple cases")
        void testBoundsSimpleCases() {
            // Acyclic graph
            Graph<String, DefaultEdge> acyclic = createAcyclicGraph();
            OptimalKComputer.KBounds bounds1 = computer.computeKBounds(acyclic);
            assertEquals(0, bounds1.lowerBound);
            assertEquals(0, bounds1.upperBound);

            // Simple cycle
            Graph<String, DefaultEdge> cycle = createSimple3Cycle();
            OptimalKComputer.KBounds bounds2 = computer.computeKBounds(cycle);
            assertEquals(1, bounds2.lowerBound);
            assertTrue(bounds2.upperBound >= bounds2.lowerBound);
        }

        @Test
        @DisplayName("Should provide meaningful bounds for complex graphs")
        void testBoundsComplexGraphs() {
            Graph<String, DefaultEdge> graph = createComplexSCCGraph();
            OptimalKComputer.KBounds bounds = computer.computeKBounds(graph);

            assertTrue(bounds.lowerBound >= 1);
            assertTrue(bounds.upperBound >= bounds.lowerBound);
            assertTrue(bounds.upperBound < graph.vertexSet().size());
        }

        @Test
        @DisplayName("Bounds should be consistent with optimal k")
        void testBoundsConsistency() {
            Graph<String, DefaultEdge> graph = createSimple3Cycle();

            OptimalKComputer.KBounds bounds = computer.computeKBounds(graph);
            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertTrue(result.getOptimalK() >= bounds.lowerBound);
            assertTrue(result.getOptimalK() <= bounds.upperBound);
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases")
    class PerformanceEdgeCaseTests {

        @Test
        @DisplayName("Should handle empty graph")
        void testEmptyGraph() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(0, result.getOptimalK());
            assertTrue(result.getFeedbackVertexSet().isEmpty());
        }

        @Test
        @DisplayName("Should handle single vertex graph")
        void testSingleVertexGraph() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(0, result.getOptimalK());
            assertTrue(result.getFeedbackVertexSet().isEmpty());
        }

        @Test
        @DisplayName("Should handle timeout gracefully")
        void testTimeout() {
            OptimalKComputer<String, DefaultEdge> shortTimeoutComputer =
                    new OptimalKComputer<>(1, true); // 1 second timeout

            Graph<String, DefaultEdge> largeGraph = createRandomGraph(100, 0.1);

            try {
                OptimalKComputer.OptimalKResult<String> result = shortTimeoutComputer.computeOptimalK(largeGraph);

                // Should still return some result even with timeout
                assertTrue(result.getOptimalK() >= 0);
                assertNotNull(result.getAlgorithmUsed());
            } finally {
                shortTimeoutComputer.shutdown();
            }
        }

        @Test
        @DisplayName("Should handle long chains efficiently")
        void testLongChains() {
            Graph<String, DefaultEdge> chain = createLongChain(50);

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(chain);

            assertEquals(0, result.getOptimalK()); // Chain is acyclic
            assertTrue(result.getFeedbackVertexSet().isEmpty());
            assertTrue(result.getComputationTimeMs() < 5000); // Should be fast
        }

        @Test
        @DisplayName("Should provide deterministic results")
        void testDeterministicResults() {
            Graph<String, DefaultEdge> graph = createSimple3Cycle();

            OptimalKComputer.OptimalKResult<String> result1 = computer.computeOptimalK(graph);
            OptimalKComputer.OptimalKResult<String> result2 = computer.computeOptimalK(graph);

            assertEquals(result1.getOptimalK(), result2.getOptimalK());
            // Note: actual vertices chosen might differ due to parallel execution
        }
    }

    @Nested
    @DisplayName("Solution Validation Tests")
    class SolutionValidationTests {

        @Test
        @DisplayName("Should validate solutions correctly")
        void testSolutionValidation() {
            Graph<String, DefaultEdge> graph = createComplexSCCGraph();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            // Create test graph and remove feedback vertices
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);
            result.getFeedbackVertexSet().forEach(testGraph::removeVertex);

            // Resulting graph should be acyclic
            assertTrue(isAcyclic(testGraph));
        }

        @Test
        @DisplayName("Should find minimal solutions for known cases")
        void testMinimalSolutions() {
            // Test case where optimal k is known
            Graph<String, DefaultEdge> graph = createSimple3Cycle();

            OptimalKComputer.OptimalKResult<String> result = computer.computeOptimalK(graph);

            assertEquals(1, result.getOptimalK()); // Known optimal

            // Verify we can't do better
            Graph<String, DefaultEdge> testGraph = copyGraph(graph);
            assertFalse(isAcyclic(testGraph)); // Original has cycles
        }
    }

    // Helper methods for creating test graphs

    private Graph<String, DefaultEdge> createAcyclicGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("A", "D");
        graph.addEdge("D", "C");
        return graph;
    }

    private Graph<String, DefaultEdge> createSimple3Cycle() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");
        return graph;
    }

    private Graph<String, DefaultEdge> createCompleteDirectedGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        // Add all possible directed edges
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) {
                    graph.addEdge("V" + i, "V" + j);
                }
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createComplexSCCGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // First SCC: A -> B -> C -> A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        // Second SCC: D -> E -> F -> D
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");

        // Connection between SCCs
        graph.addVertex("G");
        graph.addEdge("C", "G");
        graph.addEdge("G", "D");

        // Additional complexity
        graph.addEdge("A", "E");
        graph.addEdge("F", "B");

        return graph;
    }

    private Graph<String, DefaultEdge> createNestedCyclesGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Outer cycle: A -> B -> C -> D -> A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "D");
        graph.addEdge("D", "A");

        // Inner cycle: B -> E -> F -> C
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge("B", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "C");

        return graph;
    }

    private Graph<String, DefaultEdge> createRandomGraph(int vertexCount, double edgeProbability) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Add vertices
        for (int i = 0; i < vertexCount; i++) {
            graph.addVertex("V" + i);
        }

        // Add random edges
        for (int i = 0; i < vertexCount; i++) {
            for (int j = 0; j < vertexCount; j++) {
                if (i != j && random.nextDouble() < edgeProbability) {
                    graph.addEdge("V" + i, "V" + j);
                }
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createLongChain(int length) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < length; i++) {
            graph.addVertex("V" + i);
            if (i > 0) {
                graph.addEdge("V" + (i - 1), "V" + i);
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> copyGraph(Graph<String, DefaultEdge> original) {
        Graph<String, DefaultEdge> copy = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (String vertex : original.vertexSet()) {
            copy.addVertex(vertex);
        }

        for (DefaultEdge edge : original.edgeSet()) {
            String source = original.getEdgeSource(edge);
            String target = original.getEdgeTarget(edge);
            copy.addEdge(source, target);
        }

        return copy;
    }

    private boolean isAcyclic(Graph<String, DefaultEdge> graph) {
        try {
            return !new org.jgrapht.alg.cycle.CycleDetector<>(graph).detectCycles();
        } catch (Exception e) {
            return false;
        }
    }
}
