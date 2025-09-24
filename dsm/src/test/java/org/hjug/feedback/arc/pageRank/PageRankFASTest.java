package org.hjug.feedback.arc.pageRank;


import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the PageRankFAS algorithm with custom LineDigraph
 */
class PageRankFASTest {

    private PageRankFAS<String, DefaultEdge> pageRankFAS;

    @Nested
    @DisplayName("LineDigraph Implementation Tests")
    class LineDigraphTests {

        @Test
        @DisplayName("Test LineDigraph basic operations")
        void testLineDigraphBasicOperations() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            // Test empty digraph
            assertTrue(lineDigraph.isEmpty());
            assertEquals(0, lineDigraph.vertexCount());
            assertEquals(0, lineDigraph.edgeCount());

            // Create test line vertices
            DefaultEdge edge1 = new DefaultEdge();
            DefaultEdge edge2 = new DefaultEdge();
            LineVertex<String, DefaultEdge> lv1 = new LineVertex<>("A", "B", edge1);
            LineVertex<String, DefaultEdge> lv2 = new LineVertex<>("B", "C", edge2);

            // Test adding vertices
            assertTrue(lineDigraph.addVertex(lv1));
            assertFalse(lineDigraph.addVertex(lv1)); // Should not add duplicate
            assertTrue(lineDigraph.addVertex(lv2));

            assertEquals(2, lineDigraph.vertexCount());
            assertTrue(lineDigraph.containsVertex(lv1));
            assertTrue(lineDigraph.containsVertex(lv2));

            // Test adding edges
            assertTrue(lineDigraph.addEdge(lv1, lv2));
            assertFalse(lineDigraph.addEdge(lv1, lv2)); // Should not add duplicate

            assertEquals(1, lineDigraph.edgeCount());
            assertTrue(lineDigraph.containsEdge(lv1, lv2));
            assertFalse(lineDigraph.containsEdge(lv2, lv1));
        }

        @Test
        @DisplayName("Test LineDigraph degree calculations")
        void testLineDigraphDegrees() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            DefaultEdge e1 = new DefaultEdge();
            DefaultEdge e2 = new DefaultEdge();
            DefaultEdge e3 = new DefaultEdge();

            LineVertex<String, DefaultEdge> lv1 = new LineVertex<>("A", "B", e1);
            LineVertex<String, DefaultEdge> lv2 = new LineVertex<>("B", "C", e2);
            LineVertex<String, DefaultEdge> lv3 = new LineVertex<>("C", "A", e3);

            lineDigraph.addVertex(lv1);
            lineDigraph.addVertex(lv2);
            lineDigraph.addVertex(lv3);

            lineDigraph.addEdge(lv1, lv2);
            lineDigraph.addEdge(lv2, lv3);
            lineDigraph.addEdge(lv3, lv1);

            // Test degrees
            assertEquals(1, lineDigraph.getOutDegree(lv1));
            assertEquals(1, lineDigraph.getInDegree(lv1));
            assertEquals(2, lineDigraph.getTotalDegree(lv1));

            // Test neighbors
            assertEquals(Set.of(lv2), lineDigraph.getOutgoingNeighbors(lv1));
            assertEquals(Set.of(lv3), lineDigraph.getIncomingNeighbors(lv1));
            assertEquals(Set.of(lv2, lv3), lineDigraph.getAllNeighbors(lv1));
        }

        @Test
        @DisplayName("Test LineDigraph sources and sinks")
        void testLineDigraphSourcesAndSinks() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            DefaultEdge e1 = new DefaultEdge();
            DefaultEdge e2 = new DefaultEdge();
            DefaultEdge e3 = new DefaultEdge();

            LineVertex<String, DefaultEdge> source = new LineVertex<>("A", "B", e1);
            LineVertex<String, DefaultEdge> middle = new LineVertex<>("B", "C", e2);
            LineVertex<String, DefaultEdge> sink = new LineVertex<>("C", "D", e3);

            lineDigraph.addVertex(source);
            lineDigraph.addVertex(middle);
            lineDigraph.addVertex(sink);

            lineDigraph.addEdge(source, middle);
            lineDigraph.addEdge(middle, sink);

            // Test sources and sinks
            assertEquals(Set.of(source), lineDigraph.getSources());
            assertEquals(Set.of(sink), lineDigraph.getSinks());
        }

        @Test
        @DisplayName("Test LineDigraph path finding")
        void testLineDigraphPathFinding() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            DefaultEdge e1 = new DefaultEdge();
            DefaultEdge e2 = new DefaultEdge();
            DefaultEdge e3 = new DefaultEdge();

            LineVertex<String, DefaultEdge> lv1 = new LineVertex<>("A", "B", e1);
            LineVertex<String, DefaultEdge> lv2 = new LineVertex<>("B", "C", e2);
            LineVertex<String, DefaultEdge> lv3 = new LineVertex<>("C", "D", e3);

            lineDigraph.addVertex(lv1);
            lineDigraph.addVertex(lv2);
            lineDigraph.addVertex(lv3);

            lineDigraph.addEdge(lv1, lv2);
            lineDigraph.addEdge(lv2, lv3);

            // Test path existence
            assertTrue(lineDigraph.hasPath(lv1, lv2));
            assertTrue(lineDigraph.hasPath(lv1, lv3));
            assertTrue(lineDigraph.hasPath(lv2, lv3));
            assertFalse(lineDigraph.hasPath(lv3, lv1));

            // Test reachable vertices
            Set<LineVertex<String, DefaultEdge>> reachable = lineDigraph.getReachableVertices(lv1);
            assertEquals(Set.of(lv1, lv2, lv3), reachable);
        }

        @Test
        @DisplayName("Test LineDigraph topological sort")
        void testLineDigraphTopologicalSort() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            DefaultEdge e1 = new DefaultEdge();
            DefaultEdge e2 = new DefaultEdge();
            DefaultEdge e3 = new DefaultEdge();

            LineVertex<String, DefaultEdge> lv1 = new LineVertex<>("A", "B", e1);
            LineVertex<String, DefaultEdge> lv2 = new LineVertex<>("B", "C", e2);
            LineVertex<String, DefaultEdge> lv3 = new LineVertex<>("C", "D", e3);

            lineDigraph.addVertex(lv1);
            lineDigraph.addVertex(lv2);
            lineDigraph.addVertex(lv3);

            lineDigraph.addEdge(lv1, lv2);
            lineDigraph.addEdge(lv2, lv3);

            // Test topological sort on acyclic graph
            List<LineVertex<String, DefaultEdge>> sorted = lineDigraph.topologicalSort();
            assertEquals(3, sorted.size());
            assertEquals(lv1, sorted.get(0));
            assertEquals(lv2, sorted.get(1));
            assertEquals(lv3, sorted.get(2));

            // Add cycle and test
            lineDigraph.addEdge(lv3, lv1);
            List<LineVertex<String, DefaultEdge>> cyclicSort = lineDigraph.topologicalSort();
            assertTrue(cyclicSort.isEmpty()); // Should return empty for cyclic graphs
        }

        @Test
        @DisplayName("Test LineDigraph consistency validation")
        void testLineDigraphConsistency() {
            LineDigraph<String, DefaultEdge> lineDigraph = new LineDigraph<>();

            DefaultEdge e1 = new DefaultEdge();
            DefaultEdge e2 = new DefaultEdge();

            LineVertex<String, DefaultEdge> lv1 = new LineVertex<>("A", "B", e1);
            LineVertex<String, DefaultEdge> lv2 = new LineVertex<>("B", "C", e2);

            lineDigraph.addVertex(lv1);
            lineDigraph.addVertex(lv2);
            lineDigraph.addEdge(lv1, lv2);

            // Should be consistent
            assertTrue(lineDigraph.validateConsistency());

            // Test copy operation
            LineDigraph<String, DefaultEdge> copy = lineDigraph.copy();
            assertEquals(lineDigraph.vertexCount(), copy.vertexCount());
            assertEquals(lineDigraph.edgeCount(), copy.edgeCount());
            assertTrue(copy.validateConsistency());
        }
    }

    @Nested
    @DisplayName("Updated PageRankFAS Algorithm Tests")
    class UpdatedAlgorithmTests {

        @Test
        @DisplayName("Test updated algorithm on simple cycle")
        void testUpdatedAlgorithmSimpleCycle() {
            Graph<String, DefaultEdge> graph = createSimpleCycle();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>(){});

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            assertEquals(1, fas.size(), "FAS should contain exactly one edge for simple cycle");

            // Verify that removing the FAS makes the graph acyclic
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>(){});
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");
        }

        @Test
        @DisplayName("Test updated algorithm execution statistics")
        void testExecutionStatistics() {
            Graph<String, DefaultEdge> graph = createComplexGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>(){});

            Map<String, Object> stats = pageRankFAS.getExecutionStatistics(graph);

            assertNotNull(stats);
            assertTrue(stats.containsKey("originalVertices"));
            assertTrue(stats.containsKey("originalEdges"));
            assertTrue(stats.containsKey("pageRankIterations"));
            assertTrue(stats.containsKey("sccCount"));
            assertTrue(stats.containsKey("trivialSCCs"));
            assertTrue(stats.containsKey("nonTrivialSCCs"));
            assertTrue(stats.containsKey("largestSCCSize"));

            assertEquals(graph.vertexSet().size(), stats.get("originalVertices"));
            assertEquals(graph.edgeSet().size(), stats.get("originalEdges"));
        }

        @Test
        @DisplayName("Test updated algorithm with multiple SCCs")
        void testMultipleSCCs() {
            Graph<String, DefaultEdge> graph = createMultipleSCCGraph();
            pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>(){});

            Set<DefaultEdge> fas = pageRankFAS.computeFeedbackArcSet();

            // Verify that the result breaks all cycles
            fas.forEach(graph::removeEdge);
            PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(graph, new SuperTypeToken<>(){});
            assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                    "Graph should be acyclic after removing FAS");

            // Check execution statistics
            Map<String, Object> stats = pageRankFAS.getExecutionStatistics(createMultipleSCCGraph());
            assertTrue((Integer) stats.get("nonTrivialSCCs") >= 2,
                    "Should have multiple non-trivial SCCs");
        }

        @Test
        @DisplayName("Test performance comparison with different PageRank iterations")
        void testPerformanceWithDifferentIterations() {
            Graph<String, DefaultEdge> graph = createComplexGraph();

            int[] iterations = {1, 3, 5, 10};
            Map<Integer, Integer> fasSize = new HashMap<>();
            Map<Integer, Long> executionTime = new HashMap<>();

            for (int iter : iterations) {
                Graph<String, DefaultEdge> testGraph = copyGraph(graph);
                PageRankFAS<String, DefaultEdge> algorithm = new PageRankFAS<>(testGraph, iter, new SuperTypeToken<>(){});

                long startTime = System.currentTimeMillis();
                Set<DefaultEdge> fas = algorithm.computeFeedbackArcSet();
                long endTime = System.currentTimeMillis();

                fasSize.put(iter, fas.size());
                executionTime.put(iter, endTime - startTime);

                // Verify correctness
                fas.forEach(testGraph::removeEdge);
                PageRankFAS<String, DefaultEdge> verifier = new PageRankFAS<>(testGraph, new SuperTypeToken<>(){});
                assertTrue(verifier.computeFeedbackArcSet().isEmpty(),
                        "Graph should be acyclic after removing FAS (iter=" + iter + ")");
            }

            // Log results for analysis
            System.out.println("Performance analysis:");
            for (int iter : iterations) {
                System.out.printf("Iterations: %d, FAS size: %d, Time: %dms%n",
                        iter, fasSize.get(iter), executionTime.get(iter));
            }
        }
    }

    // Helper methods for creating test graphs
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

    private Graph<String, DefaultEdge> createComplexGraph() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Create vertices
        for (int i = 0; i < 8; i++) {
            graph.addVertex("V" + i);
        }

        // Create multiple cycles
        graph.addEdge("V0", "V1");
        graph.addEdge("V1", "V2");
        graph.addEdge("V2", "V0");  // Triangle cycle

        graph.addEdge("V3", "V4");
        graph.addEdge("V4", "V5");
        graph.addEdge("V5", "V6");
        graph.addEdge("V6", "V3");  // Square cycle

        // Overlapping cycle
        graph.addEdge("V2", "V3");
        graph.addEdge("V5", "V7");
        graph.addEdge("V7", "V1");  // Creates larger cycle

        return graph;
    }

    private Graph<String, DefaultEdge> createMultipleSCCGraph() {
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

        // SCC 3: F -> G -> H -> F
        graph.addVertex("F");
        graph.addVertex("G");
        graph.addVertex("H");
        graph.addEdge("F", "G");
        graph.addEdge("G", "H");
        graph.addEdge("H", "F");

        // Connections between SCCs (acyclic)
        graph.addEdge("B", "C");
        graph.addEdge("E", "F");

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
}
