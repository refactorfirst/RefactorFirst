package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GraphMetricsCollector#hasKotlinMetrics()} — the gate
 * that lets the builder wiring skip the Kotlin-specific disharmony
 * detectors (including an O(N²) sealed-hierarchy scan) on Java-only builds.
 *
 * <p>These tests exercise the gate in isolation: they build a collector, add
 * {@link ClassMetrics} directly, and assert that {@code hasKotlinMetrics()}
 * distinguishes Java-only populations from any population carrying a
 * Kotlin-specific signal.
 */
class GraphMetricsCollectorTest {

    private GraphMetricsCollector newCollector() {
        return new GraphMetricsCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
    }

    private static ClassMetrics javaClass(String fqn) {
        // A metric set as the Java metrics visitor leaves it: every Kotlin-specific
        // Kotlin flag false/0/empty.
        ClassMetrics m = new ClassMetrics(fqn);
        m.setClassName(fqn.substring(fqn.lastIndexOf('.') + 1));
        m.setPackageName(fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "");
        return m;
    }

    @DisplayName("returns false for an empty collector")
    @Test
    void hasKotlinMetrics_returnsFalseForEmptyCollector() {
        GraphMetricsCollector collector = newCollector();
        assertFalse(collector.hasKotlinMetrics());
    }

    @DisplayName("returns false for a Java-only metrics population")
    @Test
    void hasKotlinMetrics_returnsFalseForJavaOnlyMetrics() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.JavaOne");
        collector.getOrCreateClassMetrics("com.example.JavaTwo");
        assertFalse(collector.hasKotlinMetrics());
    }

    @DisplayName("returns true when a data class is present")
    @Test
    void hasKotlinMetrics_returnsTrueWhenDataClassPresent() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        ClassMetrics kotlin = collector.getOrCreateClassMetrics("com.example.Money");
        kotlin.setDataClass(true);
        assertTrue(collector.hasKotlinMetrics());
    }

    @DisplayName("returns true when a sealed class is present")
    @Test
    void hasKotlinMetrics_returnsTrueWhenSealedPresent() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        ClassMetrics kotlin = collector.getOrCreateClassMetrics("com.example.Shape");
        kotlin.setSealed(true);
        assertTrue(collector.hasKotlinMetrics());
    }

    @DisplayName("returns true when a class declares extension functions")
    @Test
    void hasKotlinMetrics_returnsTrueWhenExtensionFunctionsPresent() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        ClassMetrics kotlin = collector.getOrCreateClassMetrics("com.example.Extensions");
        kotlin.setNumberOfExtensionFunctions(11);
        assertTrue(collector.hasKotlinMetrics());
    }

    @DisplayName("returns true when an extension receiver type is recorded")
    @Test
    void hasKotlinMetrics_returnsTrueWhenExtensionReceiverTypePresent() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        ClassMetrics kotlin = collector.getOrCreateClassMetrics("com.example.Extensions");
        kotlin.addExtensionReceiverType("com.example.Receiver");
        assertTrue(collector.hasKotlinMetrics());
    }

    @DisplayName("returns true when a sealed-hierarchy ancestor is recorded")
    @Test
    void hasKotlinMetrics_returnsTrueWhenSealedHierarchyAncestorPresent() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        ClassMetrics kotlin = collector.getOrCreateClassMetrics("com.example.Circle");
        kotlin.addSealedHierarchyAncestor("com.example.Shape");
        assertTrue(collector.hasKotlinMetrics());
    }

    @DisplayName("caches the result across repeated calls (no re-scan effects)")
    @Test
    void hasKotlinMetrics_isCachedAcrossCalls() {
        GraphMetricsCollector collector = newCollector();
        collector.getOrCreateClassMetrics("com.example.Java");
        // Force first computation (Java-only → false). Mutating a Java class
        // to carry a Kotlin signal afterwards would only matter if the cache
        // were re-resolved; this test asserts the cached value sticks.
        boolean first = collector.hasKotlinMetrics();
        collector.getOrCreateClassMetrics("com.example.Money").setDataClass(true);
        boolean second = collector.hasKotlinMetrics();
        assertEquals(first, second, "hasKotlinMetrics() must return the cached value on repeat calls");
        assertFalse(first, "sanity: first call saw a Java-only population");
    }

    @DisplayName("returns false before finalizeMetrics() for a Java-only population")
    @Test
    void hasKotlinMetrics_falseBeforeFinalizeMetrics_forJavaOnly() {
        GraphMetricsCollector collector = newCollector();
        ClassMetrics java = collector.getOrCreateClassMetrics("com.example.Java");
        // Set no Kotlin flags; finalizeMetrics() not invoked.
        assertFalse(collector.hasKotlinMetrics());
        // finalizeMetrics() must not flip the gate for a Java-only population.
        collector.finalizeMetrics();
        // Re-fetch a fresh collector to avoid the cache from the prior call.
        GraphMetricsCollector fresh = newCollector();
        fresh.getOrCreateClassMetrics("com.example.Java");
        fresh.finalizeMetrics();
        assertFalse(fresh.hasKotlinMetrics());
        // Silence unused-var lint by reading the original reference.
        assertNotNull(java);
    }
}
