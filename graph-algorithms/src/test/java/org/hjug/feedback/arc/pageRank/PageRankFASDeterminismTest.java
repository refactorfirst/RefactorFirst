package org.hjug.feedback.arc.pageRank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests pinning the run-to-run determinism of {@link PageRankFAS#computeFeedbackArcSet()}.
 *
 * <p>Both the htmlReport and the jsonReport invoke the same cycle-removal pipeline on the same
 * package graph, so a non-deterministic feedback arc selection makes the two reports disagree on
 * which package relationships (and, transitively, which class relationships) to remove. This
 * suite reproduces the exact package-SCC shape that caused the htmlReport / jsonReport
 * discrepancy between the {@code org.hjug.feedback} and
 * {@code org.hjug.feedback.vertex.kernelized} packages.</p>
 */
class PageRankFASDeterminismTest {

    private static final String FEEDBACK = "org.hjug.feedback";
    private static final String PAGE_RANK = "org.hjug.feedback.arc.pageRank";
    private static final String KERNELIZED = "org.hjug.feedback.vertex.kernelized";

    /** Builds the package-SCC shape observed in the RefactorFirst report output. */
    private Graph<String, DefaultEdge> createFeedbackPackageScc() {
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        graph.addVertex(FEEDBACK);
        graph.addVertex(PAGE_RANK);
        graph.addVertex(KERNELIZED);
        graph.addEdge(FEEDBACK, PAGE_RANK);
        graph.addEdge(PAGE_RANK, FEEDBACK);
        graph.addEdge(FEEDBACK, KERNELIZED);
        graph.addEdge(KERNELIZED, FEEDBACK);
        return graph;
    }

    private Graph<String, DefaultEdge> copyGraph(Graph<String, DefaultEdge> original) {
        Graph<String, DefaultEdge> copy = new DefaultDirectedGraph<>(DefaultEdge.class);
        original.vertexSet().forEach(copy::addVertex);
        original.edgeSet()
                .forEach(edge ->
                        copy.addEdge(original.getEdgeSource(edge), original.getEdgeTarget(edge), new DefaultEdge()));
        return copy;
    }

    private Set<String> edgeKeys(Graph<String, DefaultEdge> graph, Set<DefaultEdge> edges) {
        Set<String> keys = new TreeSet<>();
        for (DefaultEdge edge : edges) {
            keys.add(graph.getEdgeSource(edge) + " -> " + graph.getEdgeTarget(edge));
        }
        return keys;
    }

    @DisplayName("computeFeedbackArcSet selects the same edges on every run for the feedback package SCC")
    @Test
    void computeFeedbackArcSet_isDeterministicAcrossRuns() {
        // Given: the package SCC from the RefactorFirst report whose tied PageRank scores made
        // htmlReport and jsonReport pick different package edges to remove
        Graph<String, DefaultEdge> template = createFeedbackPackageScc();

        // When: the same algorithm runs many times on fresh copies of the same graph
        Set<String> firstRun = null;
        for (int i = 0; i < 300; i++) {
            Graph<String, DefaultEdge> graph = copyGraph(template);
            PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
            Set<String> run = edgeKeys(graph, pageRankFAS.computeFeedbackArcSet());
            if (firstRun == null) {
                firstRun = run;
            } else {
                assertEquals(
                        firstRun,
                        run,
                        "computeFeedbackArcSet must select identical edges across runs; run " + i + " differed");
            }
        }

        // Then: the selection breaks both 2-cycles, so the graph is acyclic after removal
        Graph<String, DefaultEdge> graph = copyGraph(template);
        PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
        Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
        assertEquals(2, feedbackArcSet.size(), "Both 2-cycles require exactly one removed edge each");
        feedbackArcSet.forEach(graph::removeEdge);
        assertTrue(
                !new CycleDetector<>(graph).detectCycles(),
                "Removing the feedback arc set must leave the graph acyclic");
    }

    @DisplayName("computeFeedbackArcSet is deterministic for a graph with parallel tie candidates")
    @Test
    void computeFeedbackArcSet_isDeterministicForLargerTiedGraph() {
        // Given: a larger symmetric SCC where many edges tie on PageRank score
        Graph<String, DefaultEdge> template = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (int i = 0; i < 6; i++) {
            template.addVertex("P" + i);
        }
        for (int i = 0; i < 6; i++) {
            template.addEdge("P" + i, "P" + (i + 1) % 6);
            template.addEdge("P" + i, "P" + (i + 2) % 6);
        }

        // When: the algorithm runs repeatedly on fresh copies
        Set<String> firstRun = null;
        for (int i = 0; i < 100; i++) {
            Graph<String, DefaultEdge> graph = copyGraph(template);
            PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
            Set<String> run = edgeKeys(graph, pageRankFAS.computeFeedbackArcSet());
            if (firstRun == null) {
                firstRun = run;
            } else {
                assertEquals(
                        firstRun,
                        run,
                        "computeFeedbackArcSet must select identical edges across runs; run " + i + " differed");
            }
        }

        // Then: the selection is a valid feedback arc set
        Graph<String, DefaultEdge> graph = copyGraph(template);
        PageRankFAS<String, DefaultEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
        Set<DefaultEdge> feedbackArcSet = pageRankFAS.computeFeedbackArcSet();
        Set<DefaultEdge> removed = new HashSet<>(feedbackArcSet);
        removed.forEach(graph::removeEdge);
        assertTrue(
                !new CycleDetector<>(graph).detectCycles(),
                "Removing the feedback arc set must leave the graph acyclic");
    }
}
