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
 * <p>Also defines the <b>cyclic-depth semantics</b> of the public
 * {@link ClassMetrics#getSealedHierarchyDepth()} metric:
 *
 * <p>A class's depth is the length of the longest acyclic ancestor path that
 * terminates at a sealed root ({@code isSealed() == true} with no ancestors,
 * depth 1) <em>within the analyzed batch</em>. A class with no such path —
 * including a cycle member whose ancestor paths do not reach a sealed root,
 * a class whose only ancestor paths enter a cycle, or a class whose chain
 * dead-ends at a non-sealed class in the batch — has depth 0: it is not a
 * member of any observable sealed hierarchy. External ancestors (not present
 * in the batch) preserve the pre-existing minimum depth of 2 as a relationship
 * signal. When both cyclic and valid paths exist, the valid path's depth wins.
 */
class GraphMetricsCollectorSealedDepthTest {

    private GraphMetricsCollector newCollector() {
        return new GraphMetricsCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
    }

    // --- Termination (issue #215 regression) -------------------------------

    @DisplayName("a two-class ancestor cycle does not overflow the stack")
    @Test
    void twoClassAncestorCycle_terminates() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.A").addSealedHierarchyAncestor("com.example.B");
        collector.getOrCreateClassMetrics("com.example.B").addSealedHierarchyAncestor("com.example.A");

        assertDoesNotThrow(collector::finalizeMetrics, "cyclic sealed-hierarchy ancestors must not StackOverflowError");
    }

    @DisplayName("a self-referencing ancestor does not overflow the stack")
    @Test
    void selfReferencingAncestor_terminates() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.A").addSealedHierarchyAncestor("com.example.A");

        assertDoesNotThrow(
                collector::finalizeMetrics, "self-referencing sealed-hierarchy ancestor must not StackOverflowError");
    }

    // --- Cyclic-depth semantics ---------------------------------------------

    @DisplayName("cycle members have depth 0: a cycle has no sealed root")
    @Test
    void twoClassAncestorCycle_membersHaveDepthZero() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.A");

        collector.finalizeMetrics();

        assertEquals(0, a.getSealedHierarchyDepth(), "cycle member A has no path to a sealed root");
        assertEquals(0, b.getSealedHierarchyDepth(), "cycle member B has no path to a sealed root");
    }

    @DisplayName("a self-referencing class has depth 0")
    @Test
    void selfReferencingAncestor_hasDepthZero() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        a.addSealedHierarchyAncestor("com.example.A");

        collector.finalizeMetrics();

        assertEquals(0, a.getSealedHierarchyDepth());
    }

    @DisplayName("members of a three-class cycle all have depth 0")
    @Test
    void threeClassAncestorCycle_membersHaveDepthZero() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        ClassMetrics c = collector.getOrCreateClassMetrics("com.example.C");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.C");
        c.addSealedHierarchyAncestor("com.example.A");

        collector.finalizeMetrics();

        assertEquals(0, a.getSealedHierarchyDepth());
        assertEquals(0, b.getSealedHierarchyDepth());
        assertEquals(0, c.getSealedHierarchyDepth());
    }

    @DisplayName("a class whose only ancestor path enters a cycle has depth 0")
    @Test
    void descendantOfCycle_hasDepthZero() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        ClassMetrics e = collector.getOrCreateClassMetrics("com.example.E");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.A");
        e.addSealedHierarchyAncestor("com.example.A");

        collector.finalizeMetrics();

        assertEquals(0, e.getSealedHierarchyDepth(), "E's only path dead-ends in the A<->B cycle");
    }

    @DisplayName("a valid sealed-root path wins over a cyclic path")
    @Test
    void mixedCyclicAndValidPaths_validPathWins() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics root = collector.getOrCreateClassMetrics("com.example.Shape");
        root.setSealed(true);
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        ClassMetrics e = collector.getOrCreateClassMetrics("com.example.Circle");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.A");
        // Circle lists both a cyclic ancestor and a genuine sealed root
        e.addSealedHierarchyAncestor("com.example.A");
        e.addSealedHierarchyAncestor("com.example.Shape");

        collector.finalizeMetrics();

        assertEquals(
                2,
                e.getSealedHierarchyDepth(),
                "Circle's depth comes from the Shape root path; the cyclic path contributes nothing");
        assertEquals(1, root.getSealedHierarchyDepth());
    }

    @DisplayName("a cycle member with a sealed-root path receives its valid depth")
    @Test
    void cycleMemberWithSealedRootPath_hasValidDepth() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics root = collector.getOrCreateClassMetrics("com.example.Shape");
        root.setSealed(true);
        ClassMetrics a = collector.getOrCreateClassMetrics("com.example.A");
        ClassMetrics b = collector.getOrCreateClassMetrics("com.example.B");
        a.addSealedHierarchyAncestor("com.example.B");
        b.addSealedHierarchyAncestor("com.example.A");
        b.addSealedHierarchyAncestor("com.example.Shape");

        collector.finalizeMetrics();

        assertEquals(3, a.getSealedHierarchyDepth(), "A reaches the Shape root through B");
        assertEquals(2, b.getSealedHierarchyDepth(), "B reaches the Shape root directly");
    }

    @DisplayName("a chain ending at a non-sealed class in the batch has depth 0")
    @Test
    void chainEndingAtNonSealedClass_hasDepthZero() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.PlainInterface");
        ClassMetrics impl = collector.getOrCreateClassMetrics("com.example.Impl");
        impl.addSealedHierarchyAncestor("com.example.PlainInterface");

        collector.finalizeMetrics();

        assertEquals(0, impl.getSealedHierarchyDepth(), "no sealed root is reachable, so no depth is recorded");
    }

    // --- Unchanged semantics -------------------------------------------------

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

    @DisplayName("external-only ancestors keep minimum depth 2 (relationship signal)")
    @Test
    void externalOnlyAncestors_keepMinimumDepthTwo() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics impl = collector.getOrCreateClassMetrics("com.example.Circle");
        impl.addSealedHierarchyAncestor("com.thirdparty.Shape"); // not in batch

        collector.finalizeMetrics();

        assertEquals(
                2,
                impl.getSealedHierarchyDepth(),
                "external ancestors preserve minimum depth of 2 to record the relationship");
    }
}
