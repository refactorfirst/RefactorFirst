package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for
 * <a href="https://github.com/refactorfirst/RefactorFirst/issues/215">issue #215</a>:
 * {@code StackOverflowError} in
 * {@link GraphMetricsCollector}'s {@code computeSealedDepth} when the collected
 * sealed-hierarchy ancestor data contains a cycle (e.g. a Java {@code implements}
 * chain recorded in a cycle by type attribution without the full classpath).
 *
 * <p>The depth computation must track the classes already on the current
 * traversal path and stop when one repeats, instead of recursing forever.
 */
class GraphMetricsCollectorSealedDepthTest {

    private GraphMetricsCollector newCollector() {
        return new GraphMetricsCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
    }

    @DisplayName("a two-class ancestor cycle does not overflow the stack")
    @Test
    void twoClassAncestorCycle_terminates() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.A");

        assertDoesNotThrow(collector::finalizeMetrics, "cyclic sealed-hierarchy ancestors must not StackOverflowError");

        assertTrue(a.getSealedHierarchyDepth() > 0, "A is in a hierarchy; a finite depth must be recorded");
        assertTrue(b.getSealedHierarchyDepth() > 0, "B is in a hierarchy; a finite depth must be recorded");
        assertEquals(
                a.getSealedHierarchyDepth(),
                b.getSealedHierarchyDepth(),
                "symmetric two-class cycle must yield equal depths");
    }

    @DisplayName("a self-referencing ancestor does not overflow the stack")
    @Test
    void selfReferencingAncestor_terminates() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        a.addSealedHierarchyAncestor("com.example.A");

        assertDoesNotThrow(
                collector::finalizeMetrics, "self-referencing sealed-hierarchy ancestor must not StackOverflowError");

        assertTrue(a.getSealedHierarchyDepth() > 0, "a finite depth must be recorded for a self loop");
    }

    @DisplayName("a three-class ancestor cycle does not overflow the stack")
    @Test
    void threeClassAncestorCycle_terminates() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        ClassMetrics c = collector.getOrCreateClassMetrics("com.example.C");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.C");
        c.addSealedHierarchyAncestor("com.example.A");

        assertDoesNotThrow(collector::finalizeMetrics, "cyclic sealed-hierarchy ancestors must not StackOverflowError");

        assertTrue(a.getSealedHierarchyDepth() > 0);
        assertTrue(b.getSealedHierarchyDepth() > 0);
        assertTrue(c.getSealedHierarchyDepth() > 0);
    }

    @DisplayName("acyclic sealed hierarchy depths are unchanged by cycle protection")
    @Test
    void acyclicHierarchy_depthsUnchanged() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics root = collector.getOrCreateClassMetrics("com.example.Shape");
        root.setSealed(true);
        ClassMetrics child = collector.getOrCreateClassMetrics("com.example.Circle");
        child.addSealedHierarchyAncestor("com.example.Shape");
        ClassMetrics grandchild = collector.getOrCreateClassMetrics("com.example.UnitCircle");
        grandchild.addSealedHierarchyAncestor("com.example.Circle");

        collector.finalizeMetrics();

        assertEquals(1, root.getSealedHierarchyDepth());
        assertEquals(2, child.getSealedHierarchyDepth());
        assertEquals(3, grandchild.getSealedHierarchyDepth());
    }
}
