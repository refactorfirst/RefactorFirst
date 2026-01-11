package org.hjug.feedback.vertex.kernelized;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
class ParameterComputerTest {

    private ParameterComputer<String, DefaultEdge> parameterComputer;
    private TreewidthComputer<String, DefaultEdge> treewidthComputer;
    private FeedbackVertexSetComputer<String, DefaultEdge> fvsComputer;
    private SuperTypeToken<DefaultEdge> token;

    @BeforeEach
    void setUp() {
        token = new SuperTypeToken<>() {};
        parameterComputer = new ParameterComputer<>(token);
        treewidthComputer = new TreewidthComputer<>();
        fvsComputer = new FeedbackVertexSetComputer<>(token);
    }

    @AfterEach
    void tearDown() {
        parameterComputer.shutdown();
        treewidthComputer.shutdown();
        fvsComputer.shutdown();
    }

    @Nested
    @DisplayName("Treewidth Computation Tests")
    class TreewidthComputationTests {

        @Test
        @DisplayName("Should compute eta=0 for empty graph")
        void testEmptyGraph() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            int eta = treewidthComputer.computeEta(graph, new HashSet<>());
            assertEquals(0, eta);
        }

        @Test
        @DisplayName("Should compute eta=0 for single vertex")
        void testSingleVertex() {
            Graph<String, DefaultEdge> graph = createSingleVertexGraph();
            int eta = treewidthComputer.computeEta(graph, new HashSet<>());
            assertEquals(0, eta);
        }

        @Test
        @DisplayName("Should compute eta=1 for path graph")
        void testPathGraph() {
            Graph<String, DefaultEdge> graph = createPathGraph(5);
            int eta = treewidthComputer.computeEta(graph, new HashSet<>());
            assertEquals(1, eta);
        }

        @Test
        @DisplayName("Should compute eta=2 for cycle graph")
        void testCycleGraph() {
            Graph<String, DefaultEdge> graph = createCycleGraph(5);
            int eta = treewidthComputer.computeEta(graph, new HashSet<>());
            assertTrue(eta >= 2);
        }

        @Test
        @DisplayName("Should handle modulator removal correctly")
        void testModulatorRemoval() {
            Graph<String, DefaultEdge> graph = createCompleteGraph(5);
            Set<String> modulator = Set.of("V0", "V1");

            int etaWithModulator = treewidthComputer.computeEta(graph, modulator);
            int etaWithoutModulator = treewidthComputer.computeEta(graph, new HashSet<>());

            assertTrue(etaWithModulator <= etaWithoutModulator);
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 25, 50})
        @DisplayName("Should handle random graphs efficiently")
        void testRandomGraphTreewidth(int size) {
            Graph<String, DefaultEdge> graph = createRandomGraph(size, 0.3);

            long startTime = System.currentTimeMillis();
            int eta = treewidthComputer.computeEta(graph, new HashSet<>());
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(eta >= 0);
            assertTrue(eta < size);
            assertTrue(duration < 5000); // Should complete within 5 seconds
        }
    }

    @Nested
    @DisplayName("Feedback Vertex Set Computation Tests")
    class FeedbackVertexSetComputationTests {

        @Test
        @DisplayName("Should compute k=0 for acyclic graph")
        void testAcyclicGraph() {
            Graph<String, DefaultEdge> graph = createPathGraph(5);
            int k = fvsComputer.computeK(graph);
            assertEquals(0, k);
        }

        @Test
        @DisplayName("Should compute k=1 for simple cycle")
        void testSimpleCycle() {
            Graph<String, DefaultEdge> graph = createCycleGraph(4);
            int k = fvsComputer.computeK(graph);
            assertEquals(1, k);
        }

        @Test
        @DisplayName("Should handle self-loops correctly")
        void testSelfLoops() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");
            graph.addEdge("A", "A");

            int k = fvsComputer.computeK(graph);
            assertEquals(1, k);
        }

        @Test
        @DisplayName("Should handle multiple cycles")
        void testMultipleCycles() {
            Graph<String, DefaultEdge> graph = createMultipleCyclesGraph();
            int k = fvsComputer.computeK(graph);
            assertEquals(1, k); // Removing node C breaks both cycles
        }

        @Test
        @DisplayName("Should handle disconnected components")
        void testDisconnectedComponents() {
            Graph<String, DefaultEdge> graph = createDisconnectedCyclesGraph();
            int k = fvsComputer.computeK(graph);
            assertTrue(k >= 2); // Each cycle needs at least one vertex removed
        }

        @ParameterizedTest
        @ValueSource(ints = {20, 50, 100})
        @DisplayName("Should handle large random graphs")
        void testLargeRandomGraphs(int size) {
            Graph<String, DefaultEdge> graph = createRandomGraph(size, 0.15);

            long startTime = System.currentTimeMillis();
            int k = fvsComputer.computeK(graph);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(k >= 0);
            assertTrue(k <= size);
            assertTrue(duration < 30000); // Should complete within 30 seconds
        }
    }

    @Nested
    @DisplayName("Parameter Computer Integration Tests")
    class ParameterComputerIntegrationTests {

        @Test
        @DisplayName("Should compute valid parameters for simple graphs")
        void testSimpleGraphParameters() {
            Graph<String, DefaultEdge> graph = createCycleGraph(4);
            ParameterComputer.Parameters params = parameterComputer.computeParameters(graph);

            assertTrue(params.getK() >= 1);
            assertTrue(params.getEta() >= 0);
            assertTrue(params.getModulatorSize() >= 0);
        }

        @Test
        @DisplayName("Should compute parameters with modulator")
        void testParametersWithModulator() {
            Graph<String, DefaultEdge> graph = createCompleteGraph(6);
            Set<String> modulator = Set.of("V0", "V1");

            ParameterComputer.Parameters params = parameterComputer.computeParameters(graph, modulator);

            assertEquals(2, params.getModulatorSize());
            assertTrue(params.getK() >= 0);
            assertTrue(params.getEta() >= 0);
        }

        @Test
        @DisplayName("Should find optimal modulator")
        void testOptimalModulatorFinding() {
            Graph<String, DefaultEdge> graph = createStarGraph(8);

            ParameterComputer.Parameters params = parameterComputer.computeParametersWithOptimalModulator(graph, 2);

            assertTrue(params.getModulatorSize() <= 2);
            assertTrue(params.getEta() >= 0);
        }

        @RepeatedTest(5)
        @DisplayName("Should produce consistent results")
        void testConsistentResults() {
            Graph<String, DefaultEdge> graph = createRandomGraph(30, 0.2);

            ParameterComputer.Parameters params1 = parameterComputer.computeParameters(graph);
            ParameterComputer.Parameters params2 = parameterComputer.computeParameters(graph);

            // Results should be deterministic for the same graph
            assertEquals(params1.getK(), params2.getK());
            assertEquals(params1.getEta(), params2.getEta());
        }
    }

    @Nested
    @DisplayName("Multithreading and Performance Tests")
    class MultithreadingPerformanceTests {

        @Test
        @DisplayName("Should handle concurrent parameter computation")
        void testConcurrentParameterComputation() throws InterruptedException {
            List<Graph<String, DefaultEdge>> graphs = IntStream.range(0, 10)
                    .mapToObj(i -> createRandomGraph(20, 0.25))
                    .collect(java.util.stream.Collectors.toList());

            List<CompletableFuture<ParameterComputer.Parameters>> futures = graphs.stream()
                    .map(graph -> CompletableFuture.supplyAsync(() -> parameterComputer.computeParameters(graph)))
                    .collect(java.util.stream.Collectors.toList());

            List<ParameterComputer.Parameters> results =
                    futures.stream().map(CompletableFuture::join).collect(java.util.stream.Collectors.toList());

            assertEquals(10, results.size());
            results.forEach(params -> {
                assertTrue(params.getK() >= 0);
                assertTrue(params.getEta() >= 0);
            });
        }

        @Test
        @DisplayName("Should scale with parallelism level")
        void testScalingWithParallelism() {
            Graph<String, DefaultEdge> graph = createRandomGraph(100, 0.1);

            // Test with different parallelism levels
            for (int parallelism : Arrays.asList(1, 2, 4)) {
                ParameterComputer<String, DefaultEdge> computer = new ParameterComputer<>(token, parallelism);

                long startTime = System.currentTimeMillis();
                ParameterComputer.Parameters params = computer.computeParameters(graph);
                long duration = System.currentTimeMillis() - startTime;

                assertTrue(params.getK() >= 0);
                assertTrue(duration < 35000); // Reasonable time limit

                computer.shutdown();
            }
        }
    }

    // Helper methods for creating test graphs

    private Graph<String, DefaultEdge> createSingleVertexGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex("V0");
        return graph;
    }

    private Graph<String, DefaultEdge> createPathGraph(int length) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < length; i++) {
            graph.addVertex("V" + i);
        }

        for (int i = 0; i < length - 1; i++) {
            graph.addEdge("V" + i, "V" + (i + 1));
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createCycleGraph(int size) {
        Graph<String, DefaultEdge> graph = createPathGraph(size);
        graph.addEdge("V" + (size - 1), "V0");
        return graph;
    }

    private Graph<String, DefaultEdge> createCompleteGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) {
                    graph.addEdge("V" + i, "V" + j);
                }
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createStarGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        graph.addVertex("center");
        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
            graph.addEdge("center", "V" + i);
            graph.addEdge("V" + i, "center");
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createMultipleCyclesGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // First cycle: A -> B -> C -> A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        // Second cycle: C -> D -> E -> C (overlapping)
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "C");

        return graph;
    }

    private Graph<String, DefaultEdge> createDisconnectedCyclesGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // First cycle
        graph.addVertex("A1");
        graph.addVertex("A2");
        graph.addVertex("A3");
        graph.addEdge("A1", "A2");
        graph.addEdge("A2", "A3");
        graph.addEdge("A3", "A1");

        // Second cycle (disconnected)
        graph.addVertex("B1");
        graph.addVertex("B2");
        graph.addVertex("B3");
        graph.addEdge("B1", "B2");
        graph.addEdge("B2", "B3");
        graph.addEdge("B3", "B1");

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
}
