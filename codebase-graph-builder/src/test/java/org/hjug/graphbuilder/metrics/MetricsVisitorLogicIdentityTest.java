package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

/**
 * Regression test for review.md item #8: the {@code instanceof
 * GraphMetricsCollector} special-case in {@link MetricsVisitorLogic#enterClass}
 * used to grab the backing metrics map by reference so the
 * {@link ClassMetrics} the visitor mutated was the same instance later
 * returned by {@link GraphMetricsCollector#getAllClassMetrics()}. The
 * {@code else} branch of that block constructed a {@code new
 * ClassMetrics(...)} that was never stored, so any non-
 * {@code GraphMetricsCollector} {@code MetricsCollector} would silently
 * discard every class's metrics.
 *
 * <p>The fix collapsed {@code MetricsCollector} into
 * {@code GraphMetricsCollector} and routed the get-or-create through
 * the canonical {@link GraphMetricsCollector#getOrCreateClassMetrics(String)}.
 * This test pins the invariant that fix established:
 *
 * <ul>
 *   <li>{@link #getOrCreateClassMetricsIsCanonicalIdentity()} — the helper
 *       returns the same instance for one FQN across calls and that
 *       instance is the one {@code getAllClassMetrics()} exposes.</li>
 *   <li>{@link #visitorMutatedInstanceIsReachableViaGetAllClassMetrics()} —
 *       an actual visitor walk produces a {@link ClassMetrics} that is
 *       the <em>same object</em> {@code getAllClassMetrics().get(fqn)}
 *       returns, proving the walk's writes survive to the read side
 *       (i.e. the orphaning path cannot recur).</li>
 * </ul>
 */
class MetricsVisitorLogicIdentityTest {

    @DisplayName("getOrCreateClassMetrics is canonical: repeated calls return the same instance, "
            + "identical to the one in getAllClassMetrics()")
    @Test
    void getOrCreateClassMetricsIsCanonicalIdentity() {
        GraphMetricsCollector collector = newGraphMetricsCollector();
        String fqn = "com.example.alpha.Alpha";

        ClassMetrics first = collector.getOrCreateClassMetrics(fqn);
        assertNotNull(first);

        ClassMetrics second = collector.getOrCreateClassMetrics(fqn);
        assertSame(first, second, "getOrCreateClassMetrics must be idempotent for an FQN");

        assertSame(
                first,
                collector.getAllClassMetrics().get(fqn),
                "getAllClassMetrics() must expose the same instance getOrCreateClassMetrics stores");
    }

    @DisplayName("Visitor-mutated ClassMetrics reach getAllClassMetrics() (no orphaned instance)")
    @Test
    void visitorMutatedInstanceIsReachableViaGetAllClassMetrics(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Beta.java");
        Files.writeString(
                source, "package com.example.beta;\n" + "public class Beta {\n" + "  public void hello() {}\n" + "}\n");

        GraphMetricsCollector collector = newGraphMetricsCollector();
        MetricsCollectingVisitor visitor = new MetricsCollectingVisitor(collector);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        List<Path> list = Collections.singletonList(source);
        javaParser.parse(list, tempDir, ctx).forEach(cu -> visitor.visit(cu, ctx));

        collector.finalizeMetrics();

        ClassMetrics mutatedInstance = collector.getAllClassMetrics().get("com.example.beta.Beta");
        assertNotNull(mutatedInstance, "Beta metrics should be collected by the visitor walk");
        // Same-object proof: the instance the walk wrote into must be the one
        // reachable from getAllClassMetrics(). The previous `else` branch of
        // enterClass would have failed this (it created an unstored instance).
        assertSame(
                collector.getOrCreateClassMetrics("com.example.beta.Beta"),
                mutatedInstance,
                "the visitor-written instance must be the same object getOrCreateClassMetrics returns");
        // And the read-only lookup agrees too.
        assertSame(
                collector.getClassMetrics("com.example.beta.Beta"),
                mutatedInstance,
                "getClassMetrics must return the same instance the walk stored");
    }

    @DisplayName(
            "the instance reachable from getAllClassMetrics() post-finalize rejects mutation (item #8 + #9 compose)")
    @Test
    void visitorMutatedInstanceIsImmutablePostFinalize(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Gamma.java");
        Files.writeString(
                source, "package com.example.gamma;\n" + "public class Gamma {\n" + "  public void hi() {}\n" + "}\n");

        GraphMetricsCollector collector = newGraphMetricsCollector();
        MetricsCollectingVisitor visitor = new MetricsCollectingVisitor(collector);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        javaParser.parse(Collections.singletonList(source), tempDir, ctx).forEach(cu -> visitor.visit(cu, ctx));

        collector.finalizeMetrics();

        ClassMetrics canon = collector.getAllClassMetrics().get("com.example.gamma.Gamma");
        assertNotNull(canon, "Gamma metrics should be collected");
        // item #8 invariant held: get-or-create returns the same instance
        assertSame(collector.getOrCreateClassMetrics("com.example.gamma.Gamma"), canon, "same canonical instance");
        // item #9 invariant: that same instance is now frozen and rejects mutation
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> canon.setLinesOfCode(999));
        assertTrue(
                ex.getMessage().contains("com.example.gamma.Gamma"),
                "frozen guard should name the FQN, was: " + ex.getMessage());
    }

    private static GraphMetricsCollector newGraphMetricsCollector() {
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        return new GraphMetricsCollector(classGraph, packageGraph);
    }
}
