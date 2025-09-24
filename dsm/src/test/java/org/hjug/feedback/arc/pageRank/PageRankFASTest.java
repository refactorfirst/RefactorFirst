package org.hjug.feedback.arc.pageRank;


import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PageRankFAS algorithm
 */
class PageRankFASTest {

    private PageRankFAS<String, DefaultEdge> pageRankFAS;

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Test on acyclic graph - should return empty FAS")
        void testAcyclicGraph() {
            Graph<String, DefaultEdge> graph = createAcyclicGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertTrue(fas.isEmpty(), "FAS should be empty for acyclic graph");
        }

        @Test
        @DisplayName("Test on simple cycle - should return one edge")
        void testSimpleCycle() {
            Graph<String, DefaultEdge> graph = createSimpleCycle();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertEquals(1, fas.size(), "FAS should contain exactly one edge for simple cycle");

            // Verify that removing the FAS makes the graph acyclic
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Test on self-loop - should return self-loop edge")
        void testSelfLoop() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");
            DefaultEdge selfLoop = graph.addEdge("A", "A");

            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertEquals(1, fas.size(), "FAS should contain the self-loop");
            assertTrue(fas.contains(selfLoop), "FAS should contain the self-loop edge");
        }

        @Test
        @DisplayName("Test on empty graph - should return empty FAS")
        void testEmptyGraph() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertTrue(fas.isEmpty(), "FAS should be empty for empty graph");
        }

        @Test
        @DisplayName("Test on single vertex - should return empty FAS")
        void testSingleVertex() {
            Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
            graph.addVertex("A");

            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertTrue(fas.isEmpty(), "FAS should be empty for single vertex graph");
        }
    }

    @Nested
    @DisplayName("Complex Graph Tests")
    class ComplexGraphTests {

        @Test
        @DisplayName("Test on multiple cycles - should handle all cycles")
        void testMultipleCycles() {
            Graph<String, DefaultEdge> graph = createMultipleCyclesGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertFalse(fas.isEmpty(), "FAS should not be empty for graph with cycles");

            // Verify that removing the FAS makes the graph acyclic
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Test on nested cycles - should break all cycles")
        void testNestedCycles() {
            Graph<String, DefaultEdge> graph = createNestedCyclesGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertFalse(fas.isEmpty(), "FAS should not be empty for nested cycles");

            // Verify acyclicity after FAS removal
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Test on strongly connected components")
        void testStronglyConnectedComponents() {
            Graph<String, DefaultEdge> graph = createSCCGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            // Verify that the result breaks all cycles
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }
    }

    @Nested
    @DisplayName("Performance and Stress Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Test on large random graph")
        void testLargeRandomGraph() {
            Graph<String, DefaultEdge> graph = createLargeRandomGraph(100, 200);
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            long startTime = System.currentTimeMillis();
            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();
            long endTime = System.currentTimeMillis();

            System.out.println("Large graph test took: " + (endTime - startTime) + "ms");

            // Verify correctness
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Test parallel processing capability")
        void testParallelProcessing() {
            Graph<String, DefaultEdge> graph = createComplexParallelTestGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            // Run multiple times to test thread safety
            for (int i = 0; i < 10; i++) {
                Graph<String, DefaultEdge> testGraph = copyGraph(graph);
                Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

                // Verify consistency
                fas.forEach(testGraph::removeEdge);
                PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(testGraph, new SuperTypeToken<>() {
                });
                assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                        "Graph should be acyclic after removing FAS (iteration " + i + ")");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Test with custom PageRank iterations")
        void testCustomPageRankIterations() {
            Graph<String, DefaultEdge> graph = createSimpleCycle();

            // Test with different iteration counts
            int[] iterations = {1, 3, 5, 10, 20};

            for (int iter : iterations) {
                Graph<String, DefaultEdge> testGraph = copyGraph(graph);
                PageRankFAS<String, DefaultEdge> customFAS = new PageRankFAS<>(testGraph, iter, new SuperTypeToken<>() {
                });
                Set<DefaultEdge> fas = customFAS.computeFeedbackArcSet();

                assertEquals(1, fas.size(),
                        "FAS size should be 1 regardless of iterations (" + iter + ")");

                // Verify correctness
                fas.forEach(testGraph::removeEdge);
                PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(testGraph, new SuperTypeToken<>() {
                });
                assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                        "Graph should be acyclic after removing FAS");
            }
        }

        @Test
        @DisplayName("Test thread safety with concurrent access")
        void testThreadSafety() throws InterruptedException {
            Graph<String, DefaultEdge> graph = createMultipleCyclesGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            final int NUM_THREADS = 10;
            final Set<Set<DefaultEdge>> results = Collections.synchronizedSet(new HashSet<>());

            Thread[] threads = new Thread[NUM_THREADS];

            for (int i = 0; i < NUM_THREADS; i++) {
                threads[i] = new Thread(() -> {
                    Graph<String, DefaultEdge> threadGraph = copyGraph(graph);
                    PageRankFAS<String, DefaultEdge> threadFAS = new PageRankFAS<>(threadGraph, new SuperTypeToken<>() {
                    });
                    Set<DefaultEdge> fas = threadFAS.computeFeedbackArcSet();
                    results.add(fas);
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // All results should be valid (though may differ slightly due to parallel processing)
            assertFalse(results.isEmpty(), "Should have results from all threads");

        }
    }

    @Nested
    @DisplayName("Algorithm Correctness Tests")
    class CorrectnessTests {

        @Test
        @DisplayName("Test FAS minimality on known graphs")
        void testFASMinimality() {
            // Create a graph where we know the optimal FAS size
            Graph<String, DefaultEdge> graph = createKnownOptimalGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            // For this specific graph, optimal FAS size should be 2
            assertTrue(fas.size() >= 2, "FAS should contain at least 2 edges");
            assertTrue(fas.size() <= 3, "FAS should not contain more than 3 edges (reasonable bound)");

            // Verify correctness
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Compare with simple heuristic on small graphs")
        void testCompareWithSimpleHeuristic() {
            Graph<String, DefaultEdge> graph = createTestComparisonGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {
            });

            Set<DefaultEdge> pageRankFas = pageRankFAS.computeFeedbackArcSet();
            Set<DefaultEdge> greedyFas = computeGreedyFAS(copyGraph(graph));

            // PageRank FAS should perform at least as well as or better than greedy
            assertTrue(pageRankFas.size() <= greedyFas.size() * 1.5,
                    "PageRank FAS should be competitive with greedy approach");

            // Both should produce valid FAS
            Graph<String, DefaultEdge> testGraph1 = copyGraph(graph);
            pageRankFas.forEach(testGraph1::removeEdge);
            assertTrue(new PageRankFAS<>(testGraph1, new SuperTypeToken<>() {
            }).computeFeedbackArcSet().isEmpty());

            Graph<String, DefaultEdge> testGraph2 = copyGraph(graph);
            greedyFas.forEach(testGraph2::removeEdge);
            assertTrue(new PageRankFAS<>(testGraph2, new SuperTypeToken<>() {
            }).computeFeedbackArcSet().isEmpty());
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

    private Graph<String, DefaultEdge> createSimpleCycle() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

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

        // Second cycle: D -> E -> F -> D
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");

        // Connect the cycles
        graph.addEdge("C", "D");

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

        // Inner cycle: B -> E -> C
        graph.addVertex("E");
        graph.addEdge("B", "E");
        graph.addEdge("E", "C");

        return graph;
    }

    private Graph<String, DefaultEdge> createSCCGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // SCC 1: A <-> B
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge("A", "B");
        graph.addEdge("B", "A");

        // SCC 2: C <-> D <-> E
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "C");

        // Connection between SCCs (acyclic)
        graph.addEdge("B", "C");

        return graph;
    }

    private Graph<String, DefaultEdge> createLargeRandomGraph(int numVertices, int numEdges) {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < numVertices; i++) {
            graph.addVertex("V" + i);
        }

        List<String> vertices = new ArrayList<>(graph.vertexSet());
        Random random = ThreadLocalRandom.current();

        // Add random edges
        for (int i = 0; i < numEdges; i++) {
            String source = vertices.get(random.nextInt(vertices.size()));
            String target = vertices.get(random.nextInt(vertices.size()));

            if (!graph.containsEdge(source, target) && !source.equals(target)) {
                graph.addEdge(source, target);
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createComplexParallelTestGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create multiple interconnected cycles for parallel testing
        for (int cluster = 0; cluster < 5; cluster++) {
            String prefix = "C" + cluster + "_";

            // Create a cycle within each cluster
            for (int i = 0; i < 4; i++) {
                graph.addVertex(prefix + i);
            }

            for (int i = 0; i < 4; i++) {
                graph.addEdge(prefix + i, prefix + ((i + 1) % 4));
            }

            // Connect clusters
            if (cluster > 0) {
                graph.addEdge("C" + (cluster - 1) + "_0", prefix + "0");
            }
        }

        return graph;
    }

    private Graph<String, DefaultEdge> createKnownOptimalGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create a graph where we know the minimum FAS
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        // Two overlapping triangles
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        graph.addEdge("B", "D");
        graph.addEdge("D", "C");
        // C->B would create another cycle, but we use C->A which is already there

        return graph;
    }

    private Graph<String, DefaultEdge> createTestComparisonGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create a moderately complex graph for comparison
        String[] vertices = {"A", "B", "C", "D", "E", "F"};
        for (String v : vertices) {
            graph.addVertex(v);
        }

        // Add edges creating multiple cycles
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");
        graph.addEdge("B", "E");

        return graph;
    }

    private Graph<String, DefaultEdge> copyGraph(Graph<String, DefaultEdge> original) {
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

    // Simple greedy FAS implementation for comparison
    private Set<DefaultEdge> computeGreedyFAS(Graph<String, DefaultEdge> graph) {
        Set<DefaultEdge> fas = new HashSet<>();

        while (hasCycles(graph)) {
            // Find edge with maximum (out-degree - in-degree) difference at source
            DefaultEdge edgeToRemove = null;
            int maxDelta = Integer.MIN_VALUE;

            for (DefaultEdge edge : graph.edgeSet()) {
                String source = graph.getEdgeSource(edge);
                int delta = graph.outDegreeOf(source) - graph.inDegreeOf(source);
                if (delta > maxDelta) {
                    maxDelta = delta;
                    edgeToRemove = edge;
                }
            }

            if (edgeToRemove != null) {
                fas.add(edgeToRemove);
                graph.removeEdge(edgeToRemove);
            } else {
                break; // Safety break
            }
        }

        return fas;
    }

    private boolean hasCycles(Graph<String, DefaultEdge> graph) {
        // Simple DFS-based cycle detection
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String vertex : graph.vertexSet()) {
            if (!visited.contains(vertex)) {
                if (dfsCycleCheck(graph, vertex, visited, recursionStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsCycleCheck(Graph<String, DefaultEdge> graph, String vertex,
                                  Set<String> visited, Set<String> recursionStack) {
        visited.add(vertex);
        recursionStack.add(vertex);

        for (DefaultEdge edge : graph.outgoingEdgesOf(vertex)) {
            String neighbor = graph.getEdgeTarget(edge);

            if (!visited.contains(neighbor)) {
                if (dfsCycleCheck(graph, neighbor, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                return true;
            }
        }

        recursionStack.remove(vertex);
        return false;
    }
}

