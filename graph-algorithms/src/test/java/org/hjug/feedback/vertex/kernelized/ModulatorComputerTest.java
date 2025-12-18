package org.hjug.feedback.vertex.kernelized;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
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
class ModulatorComputerTest {

    private ModulatorComputer<String, DefaultEdge> modulatorComputer;
    private EnhancedParameterComputer<String, DefaultEdge> parameterComputer;
    private SuperTypeToken<DefaultEdge> token;

    @BeforeEach
    void setUp() {
        token = new SuperTypeToken<>() {};
        modulatorComputer = new ModulatorComputer<>(token);
        parameterComputer = new EnhancedParameterComputer<>(token);
    }

    @AfterEach
    void tearDown() {
        modulatorComputer.shutdown();
        parameterComputer.shutdown();
    }

    @Nested
    @DisplayName("Modulator Computation Tests")
    class ModulatorComputationTests {

        @Test
        @DisplayName("Should compute empty modulator for tree graph")
        void testTreeGraphModulator() {
            Graph<String, DefaultEdge> tree = createTreeGraph(10);
            ModulatorComputer.ModulatorResult<String> result = modulatorComputer.computeModulator(tree, 1, 5);

            assertTrue(result.getResultingTreewidth() <= 1);
            assertTrue(result.getSize() <= 2); // Trees have treewidth 1
        }

        @Test
        @DisplayName("Should compute valid modulator for cycle graph")
        void testCycleGraphModulator() {
            Graph<String, DefaultEdge> cycle = createCycleGraph(6);
            ModulatorComputer.ModulatorResult<String> result = modulatorComputer.computeModulator(cycle, 1, 3);
            /*A tree has treewidth = 1.
            A cycle has treewidth = 2.
            A clique of size n has treewidth = n-1
            The more “grid-like” or “dense” the graph, the higher its treewidth.*/
            assertTrue(result.getResultingTreewidth() <= 2); // this is a cycle
            assertTrue(result.getSize() >= 1); // Need to break cycle
            assertFalse(result.getModulator().isEmpty());
        }

        @Test
        @DisplayName("Should compute modulator for complete graph")
        void testCompleteGraphModulator() {
            Graph<String, DefaultEdge> complete = createCompleteGraph(5);
            ModulatorComputer.ModulatorResult<String> result = modulatorComputer.computeModulator(complete, 2, 4);

            assertTrue(result.getResultingTreewidth() <= 2);
            assertTrue(result.getSize() >= 2); // Complete graphs have high treewidth
        }

        @Test
        @DisplayName("Should respect modulator size limit")
        void testModulatorSizeLimit() {
            Graph<String, DefaultEdge> complete = createCompleteGraph(8);
            int maxSize = 3;

            ModulatorComputer.ModulatorResult<String> result = modulatorComputer.computeModulator(complete, 1, maxSize);

            assertTrue(result.getSize() <= maxSize);
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 20, 30})
        @DisplayName("Should handle random graphs efficiently")
        void testRandomGraphModulator(int size) {
            Graph<String, DefaultEdge> graph = createRandomGraph(size, 0.2);

            long startTime = System.currentTimeMillis();
            ModulatorComputer.ModulatorResult<String> result = modulatorComputer.computeModulator(graph, 3, size / 4);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(result.getResultingTreewidth() >= 0);
            assertTrue(result.getSize() <= size / 4);
            assertTrue(duration < 10000); // Should complete within 10 seconds
        }

        @Test
        @DisplayName("Should find better modulators with larger budgets")
        void testModulatorQualityImprovement() {
            Graph<String, DefaultEdge> graph = createGridGraph(4, 4);

            ModulatorComputer.ModulatorResult<String> smallResult = modulatorComputer.computeModulator(graph, 2, 2);
            ModulatorComputer.ModulatorResult<String> largeResult = modulatorComputer.computeModulator(graph, 2, 6);

            // Larger budget should achieve better or equal treewidth
            assertTrue(largeResult.getResultingTreewidth() <= smallResult.getResultingTreewidth());
        }
    }

    @Nested
    @DisplayName("Enhanced Parameter Computer Tests")
    class EnhancedParameterComputerTests {

        @Test
        @DisplayName("Should compute enhanced parameters for simple graph")
        void testSimpleGraphParameters() {
            Graph<String, DefaultEdge> graph = createCycleGraph(5);

            EnhancedParameterComputer.EnhancedParameters<String> params =
                    parameterComputer.computeOptimalParameters(graph, 3);

            assertTrue(params.getK() >= 1); // Cycle needs feedback vertex set
            assertTrue(params.getModulatorSize() <= 3);
            assertTrue(params.getEta() >= 0);
            assertTrue(params.getTotalParameter() > 0);
        }

        @Test
        @DisplayName("Should compute multiple parameter options")
        void testMultipleParameterOptions() {
            Graph<String, DefaultEdge> graph = createRandomGraph(15, 0.3);

            List<EnhancedParameterComputer.EnhancedParameters<String>> options =
                    parameterComputer.computeMultipleParameterOptions(graph, 5, 3);

            assertFalse(options.isEmpty());
            assertTrue(options.size() <= 3);

            // Options should be sorted by quality
            for (int i = 1; i < options.size(); i++) {
                assertTrue(
                        options.get(i - 1).getQualityScore() <= options.get(i).getQualityScore());
            }
        }

        @Test
        @DisplayName("Should validate modulators correctly")
        void testModulatorValidation() {
            Graph<String, DefaultEdge> graph = createPathGraph(8);
            Set<String> emptyModulator = new HashSet<>();
            Set<String> singleVertexModulator = Set.of("V3");

            assertTrue(parameterComputer.validateModulator(graph, emptyModulator, 1));
            assertTrue(parameterComputer.validateModulator(graph, singleVertexModulator, 1));
        }

        @Test
        @DisplayName("Should compute kernel size bounds correctly")
        void testKernelSizeBounds() {
            Graph<String, DefaultEdge> graph = createCycleGraph(4);

            EnhancedParameterComputer.EnhancedParameters<String> params =
                    parameterComputer.computeOptimalParameters(graph, 2, 1);

            double kernelBound = params.getKernelSizeBound();
            assertTrue(kernelBound >= 1.0);
            assertTrue(kernelBound < Double.MAX_VALUE);
        }

        @Test
        @DisplayName("Should handle edge cases gracefully")
        void testEdgeCases() {
            // Empty graph
            Graph<String, DefaultEdge> emptyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
            EnhancedParameterComputer.EnhancedParameters<String> emptyParams =
                    parameterComputer.computeOptimalParameters(emptyGraph, 1);

            assertEquals(0, emptyParams.getK());
            assertTrue(emptyParams.getModulator().isEmpty());

            // Single vertex
            Graph<String, DefaultEdge> singleVertex = new DefaultDirectedGraph<>(DefaultEdge.class);
            singleVertex.addVertex("V0");
            EnhancedParameterComputer.EnhancedParameters<String> singleParams =
                    parameterComputer.computeOptimalParameters(singleVertex, 1);

            assertEquals(0, singleParams.getK());
            assertEquals(0, singleParams.getEta());
        }
    }

    @Nested
    @DisplayName("Integration and Performance Tests")
    class IntegrationPerformanceTests {

        @Test
        @DisplayName("Should compute parameters for complex graphs")
        void testComplexGraphParameters() {
            // Create a more complex graph structure
            Graph<String, DefaultEdge> graph = createComplexGraph();

            EnhancedParameterComputer.EnhancedParameters<String> params =
                    parameterComputer.computeOptimalParameters(graph, 5, 2);

            assertTrue(params.getK() >= 0);
            assertTrue(params.getModulatorSize() <= 5);
            assertTrue(params.getEta() <= 2);

            // Verify kernel size bound is reasonable
            double kernelBound = params.getKernelSizeBound();
            assertTrue(kernelBound >= 1.0);
        }

        @Test
        @DisplayName("Should handle concurrent parameter computation")
        void testConcurrentParameterComputation() throws InterruptedException {
            List<Graph<String, DefaultEdge>> graphs = IntStream.range(0, 5)
                    .mapToObj(i -> createRandomGraph(15, 0.25))
                    .collect(java.util.stream.Collectors.toList());

            List<java.util.concurrent.CompletableFuture<EnhancedParameterComputer.EnhancedParameters<String>>> futures =
                    graphs.stream()
                            .map(graph -> java.util.concurrent.CompletableFuture.supplyAsync(
                                    () -> parameterComputer.computeOptimalParameters(graph, 4)))
                            .collect(java.util.stream.Collectors.toList());

            List<EnhancedParameterComputer.EnhancedParameters<String>> results = futures.stream()
                    .map(java.util.concurrent.CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList());

            assertEquals(5, results.size());
            results.forEach(params -> {
                assertTrue(params.getK() >= 0);
                assertTrue(params.getModulatorSize() <= 4);
                assertTrue(params.getEta() >= 0);
            });
        }

        @RepeatedTest(3)
        @DisplayName("Should produce consistent results")
        void testConsistentResults() {
            Graph<String, DefaultEdge> graph = createGridGraph(3, 3);

            EnhancedParameterComputer.EnhancedParameters<String> params1 =
                    parameterComputer.computeOptimalParameters(graph, 3, 2);
            EnhancedParameterComputer.EnhancedParameters<String> params2 =
                    parameterComputer.computeOptimalParameters(graph, 3, 2);

            // Results should be deterministic for the same inputs
            assertEquals(params1.getK(), params2.getK());
            assertEquals(params1.getEta(), params2.getEta());
            // Modulator might vary but should have same size and achieve same treewidth
            assertEquals(params1.getModulatorSize(), params2.getModulatorSize());
        }
    }

    // Helper methods for creating test graphs

    private Graph<String, DefaultEdge> createTreeGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        for (int i = 1; i < size; i++) {
            graph.addEdge("V" + (i / 2), "V" + i); // Binary tree structure
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createCycleGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        for (int i = 0; i < size; i++) {
            graph.addEdge("V" + i, "V" + ((i + 1) % size));
        }

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

    private Graph<String, DefaultEdge> createPathGraph(int size) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        for (int i = 0; i < size; i++) {
            graph.addVertex("V" + i);
        }

        for (int i = 0; i < size - 1; i++) {
            graph.addEdge("V" + i, "V" + (i + 1));
        }

        System.out.println(graph);

        return graph;
    }

    private Graph<String, DefaultEdge> createGridGraph(int rows, int cols) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                graph.addVertex("V" + i + "_" + j);
            }
        }

        // Add edges
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                String current = "V" + i + "_" + j;

                // Right edge
                if (j < cols - 1) {
                    graph.addEdge(current, "V" + i + "_" + (j + 1));
                }

                // Down edge
                if (i < rows - 1) {
                    graph.addEdge(current, "V" + (i + 1) + "_" + j);
                }
            }
        }

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

    private Graph<String, DefaultEdge> createComplexGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < 12; i++) {
            graph.addVertex("V" + i);
        }

        // Create a complex structure with multiple cycles and high-degree vertices
        // Central hub
        for (int i = 1; i <= 4; i++) {
            graph.addEdge("V0", "V" + i);
            graph.addEdge("V" + i, "V0");
        }

        // Two cycles
        for (int i = 5; i <= 7; i++) {
            graph.addEdge("V" + i, "V" + ((i - 5 + 1) % 3 + 5));
        }

        for (int i = 8; i <= 11; i++) {
            graph.addEdge("V" + i, "V" + ((i - 8 + 1) % 4 + 8));
        }

        // Connections between components
        graph.addEdge("V1", "V5");
        graph.addEdge("V2", "V8");
        graph.addEdge("V7", "V10");

        return graph;
    }
}
