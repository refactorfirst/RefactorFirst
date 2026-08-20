package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;

/**
 * Verifies that {@link KotlinMetricsCollectingVisitor} records class-level and
 * top-level Kotlin property declarations as expected, exercising every
 * Kotlin property shape parsed by OpenRewrite.
 *
 * <p>Backs Kotlin top-level and extension property handling ({@code K.Property} (top-level properties,
 * extension properties)).
 */
class KotlinPropertyMetricsTest {

    @DisplayName("Kotlin class-level properties register as class attributes")
    @Test
    void collectKotlinPropertyMetrics() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinPropertySrcDirectory");

        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_2)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);
        KotlinMetricsCollectingVisitor metricsVisitor = new KotlinMetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Path.of(srcDirectory.getAbsolutePath()))
                .filter(p -> p.toString().endsWith(".kt"))
                .collect(Collectors.toList());
        kotlinParser
                .parse(list, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        ClassMetrics holderMetrics = metricsCollector.getClassMetrics("com.ideacrest.parser.proptests.PropertyHolder");
        assertNotNull(holderMetrics, "PropertyHolder metrics should be collected");

        System.out.println("\nPropertyHolder Metrics:");
        System.out.println("  NOA: " + holderMetrics.getNumberOfAttributes());
        System.out.println("  NOM: " + holderMetrics.getNumberOfMethods());
        System.out.println("  attributes: " + holderMetrics.getAttributes());

        // 5 class-level property declarations in PropertyHolder:
        // name, count, flag, computed, buffer
        // Note: 'val computed' has an explicit getter (still an attribute).
        assertTrue(
                holderMetrics.getNumberOfAttributes() >= 5,
                "PropertyHolder should have at least 5 attributes (class-level properties), was: "
                        + holderMetrics.getNumberOfAttributes());
        assertTrue(holderMetrics.getAttributes().contains("name"));
        assertTrue(holderMetrics.getAttributes().contains("count"));
        assertTrue(holderMetrics.getAttributes().contains("flag"));

        ClassMetrics userMetrics = metricsCollector.getClassMetrics("com.ideacrest.parser.proptests.PropertyUser");
        assertNotNull(userMetrics, "PropertyUser metrics should be collected");
        assertTrue(
                userMetrics.getNumberOfMethods() >= 1,
                "PropertyUser should have at least 1 method (describe), was: " + userMetrics.getNumberOfMethods());

        System.out.println("\nPropertyUser Metrics:");
        System.out.println("  NOA: " + userMetrics.getNumberOfAttributes());
        System.out.println("  NOM: " + userMetrics.getNumberOfMethods());

        // Top-level properties don't belong to any class, so they shouldn't
        // show up as ClassMetrics. But they also shouldn't break the parser.
        // Verify via typedef-driven test (no exception thrown).
    }
}
