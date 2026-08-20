package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;

/**
 * Kotlin-specific disharmony detection tests. Mirrors
 * {@link KotlinMetricsCollectionTest} against a plain-text {@code .kt}
 * fixture set under {@code src/test/resources/kotlinDisharmonySrcDirectory}
 * that exercises the three new disharmony detectors:
 *
 * <ul>
 *   <li>{@link DisharmonyDetector#detectExcessiveExtensions} —
 *       {@code ExtensionHost} declares 12 extension functions across
 *       11 distinct foreign receiver types.</li>
 *   <li>{@link DisharmonyDetector#detectLargeSealedHierarchy} —
 *       {@code Shape} is sealed with 12 permitted subtypes.</li>
 *   <li>{@link DisharmonyDetector#detectDataClassWithLogic} —
 *       {@code Money} is a data class with non-accessor methods
 *       (explicit logic); a control {@code PureData} data class with
 *       only the synthesized accessors must NOT be flagged.</li>
 * </ul>
 *
 * <p>Java-only classes from the existing test fixtures never get
 * {@link ClassMetrics#isDataClass()} set, so the Kotlin-specific
 * detectors also never trip against them (preserved parity).
 */
class KotlinDisharmonyTest {

    private GraphMetricsCollector loadFixtures() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinDisharmonySrcDirectory");

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
        return metricsCollector;
    }

    @DisplayName("Excessive Extensions: ExtensionHost (12 extension fns across ≥5 receiver types) is flagged")
    @Test
    void detectExcessiveExtensions() throws IOException {
        GraphMetricsCollector collector = loadFixtures();
        List<ClassDisharmony> flagged = new DisharmonyDetector()
                .detectExcessiveExtensions(
                        List.copyOf(collector.getAllClassMetrics().values()));

        System.out.println("\n=== Excessive Extensions ===");
        for (ClassDisharmony d : flagged) {
            System.out.println(d.getClassName() + " — " + d.getDescription());
        }

        boolean foundHost = flagged.stream()
                .anyMatch(d -> "com.ideacrest.parser.kotlin.disharmony.ExtensionHost".equals(d.getClassName()));
        // Sanity: theExtensionHost's metric set is what we expect
        ClassMetrics hostMetrics = collector.getClassMetrics("com.ideacrest.parser.kotlin.disharmony.ExtensionHost");
        System.out.println("ExtensionHost raw metrics:");
        System.out.println("  numberOfExtensionFunctions: " + hostMetrics.getNumberOfExtensionFunctions());
        System.out.println("  extensionReceiverTypes: " + hostMetrics.getExtensionReceiverTypes());
        assertTrue(foundHost, "ExtensionHost should be flagged as Excessive Extensions");
        assertNotNull(hostMetrics);
        assertTrue(
                hostMetrics.getNumberOfExtensionFunctions() >= 10,
                "expected ≥10 extension functions, was: " + hostMetrics.getNumberOfExtensionFunctions());
        assertTrue(
                hostMetrics.getExtensionReceiverTypes().size() >= 5,
                "expected ≥5 receiver types, was: "
                        + hostMetrics.getExtensionReceiverTypes().size());
        assertEquals(
                DisharmonyTypes.EXCESSIVE_EXTENSIONS,
                flagged.stream()
                        .filter(d -> "com.ideacrest.parser.kotlin.disharmony.ExtensionHost".equals(d.getClassName()))
                        .findFirst()
                        .orElseThrow()
                        .getDisharmonyType());
    }

    @DisplayName("Large Sealed Hierarchy: Shape (12 permitted subtypes) is flagged")
    @Test
    void detectLargeSealedHierarchy() throws IOException {
        GraphMetricsCollector collector = loadFixtures();
        List<ClassDisharmony> flagged = new DisharmonyDetector()
                .detectLargeSealedHierarchy(
                        List.copyOf(collector.getAllClassMetrics().values()));

        System.out.println("\n=== Large Sealed Hierarchy ===");
        for (ClassDisharmony d : flagged) {
            System.out.println(d.getClassName() + " — " + d.getDescription());
        }

        boolean foundShape =
                flagged.stream().anyMatch(d -> "com.ideacrest.parser.kotlin.disharmony.Shape".equals(d.getClassName()));
        assertTrue(foundShape, "Shape should be flagged as a Large Sealed Hierarchy");

        ClassMetrics shapeMetrics = collector.getClassMetrics("com.ideacrest.parser.kotlin.disharmony.Shape");
        assertNotNull(shapeMetrics);
        assertTrue(shapeMetrics.isSealed(), "Shape should be marked as sealed");
        assertEquals(
                DisharmonyTypes.LARGE_SEALED_HIERARCHY,
                flagged.stream()
                        .filter(d -> "com.ideacrest.parser.kotlin.disharmony.Shape".equals(d.getClassName()))
                        .findFirst()
                        .orElseThrow()
                        .getDisharmonyType());
    }

    @DisplayName("Data Class with Logic: Money (data class with add/subtract) is flagged; PureData is NOT")
    @Test
    void detectDataClassWithLogic() throws IOException {
        GraphMetricsCollector collector = loadFixtures();
        List<ClassDisharmony> flagged = new DisharmonyDetector()
                .detectDataClassWithLogic(
                        List.copyOf(collector.getAllClassMetrics().values()));

        System.out.println("\n=== Data Class with Logic ===");
        for (ClassDisharmony d : flagged) {
            System.out.println(d.getClassName() + " — " + d.getDescription());
        }

        ClassMetrics moneyMetrics = collector.getClassMetrics("com.ideacrest.parser.kotlin.disharmony.Money");
        ClassMetrics pureDataMetrics = collector.getClassMetrics("com.ideacrest.parser.kotlin.disharmony.PureData");
        assertNotNull(moneyMetrics, "Money metrics should be collected");
        assertNotNull(pureDataMetrics, "PureData metrics should be collected");

        assertTrue(moneyMetrics.isDataClass(), "Money should be a data class");
        assertTrue(moneyMetrics.isHasExplicitLogic(), "Money should have explicit logic (non-accessor methods)");
        assertTrue(pureDataMetrics.isDataClass(), "PureData should be a data class");
        assertFalse(
                pureDataMetrics.isHasExplicitLogic(),
                "PureData should NOT have explicit logic (no non-accessor methods)");

        boolean foundMoney =
                flagged.stream().anyMatch(d -> "com.ideacrest.parser.kotlin.disharmony.Money".equals(d.getClassName()));
        boolean foundPureData = flagged.stream()
                .anyMatch(d -> "com.ideacrest.parser.kotlin.disharmony.PureData".equals(d.getClassName()));
        assertTrue(foundMoney, "Money should be flagged as Data Class with Logic");
        assertFalse(foundPureData, "PureData should NOT be flagged as Data Class with Logic");
    }
}
