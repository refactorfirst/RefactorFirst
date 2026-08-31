package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;

/**
 * Kotlin disharmony detection parity for Kotlin source files.
 *
 * <p>Runs the Kotlin fixture set under
 * {@code src/test/resources/kotlinDisharmonyParitySrcDirectory} (the Kotlin-structural
 * twins of the Java metrics-testclasses) through the
 * {@link KotlinMetricsCollectingVisitor} and asserts that every one of the
 * 11 existing {@link DisharmonyDetector} detectors fires on the Kotlin
 * fixture that is its Java twin:
 *
 * <ol>
 *   <li>{@link DisharmonyDetector#detectGodClasses} — fixture
 *       {@code GodClassKt} loaded from {@code kotlinMetricsSrcDirectory}
 *       (existing GodClass Kotlin fixture; reasserted here for full parity coverage).</li>
 *   <li>{@link DisharmonyDetector#detectDataClasses} — {@code DataClassKt}.</li>
 *   <li>{@link DisharmonyDetector#detectBrainMethods} — {@code BrainClassKt.complexMethod1/2}.</li>
 *   <li>{@link DisharmonyDetector#detectBrainClasses} — {@code BrainClassKt}.</li>
 *   <li>{@link DisharmonyDetector#detectFeatureEnvy} — {@code FeatureEnvyKt.methodWithFeatureEnvy}.</li>
 *   <li>{@link DisharmonyDetector#detectIntensiveCoupling} — {@code IntensiveCouplingKt.methodWithIntensiveCoupling}.</li>
 *   <li>{@link DisharmonyDetector#detectDispersedCoupling} — {@code DispersedCouplingKt.methodWithDispersedCoupling}.</li>
 *   <li>{@link DisharmonyDetector#detectShotgunSurgery} — {@code ShotgunSurgeryKt.performService}.</li>
 *   <li>{@link DisharmonyDetector#detectRefusedParentBequest} — {@code RefusedBequestKt}.</li>
 *   <li>{@link DisharmonyDetector#detectTraditionBreaker} — {@code TraditionBreakerKt}.</li>
 *   <li>{@link DisharmonyDetector#detectSignificantDuplication} —
 *       {@code SignificantDuplicationCrossClassKtA/B}.</li>
 * </ol>
 *
 * <p>All fixtures are plain-text {@code .kt} resources (the Kotlin parser
 * is invoked at test time — these classes are NOT compiled by the build).
 */
class KotlinDisharmonyParityTest {

    private static GraphMetricsCollector parityCollector;
    private static GraphMetricsCollector godClassCollector;

    @BeforeAll
    static void loadFixtures() throws IOException {
        parityCollector = loadDirectory("src/test/resources/kotlinDisharmonyParitySrcDirectory");
        godClassCollector = loadDirectory("src/test/resources/kotlinMetricsSrcDirectory");
    }

    private static GraphMetricsCollector loadDirectory(String directory) throws IOException {
        File srcDirectory = new File(directory);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);
        KotlinMetricsCollectingVisitor metricsVisitor = new KotlinMetricsCollectingVisitor(metricsCollector);

        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser
                .parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();
        return metricsCollector;
    }

    private static List<ClassMetrics> allClassMetrics(GraphMetricsCollector collector) {
        return List.copyOf(collector.getAllClassMetrics().values());
    }

    private static ClassMetrics require(GraphMetricsCollector collector, String fqn) {
        ClassMetrics cm = collector.getClassMetrics(fqn);
        assertNotNull(cm, "Expected metrics for " + fqn + " but found none");
        return cm;
    }

    @DisplayName("1. GodClass detector fires on Kotlin GodClassKt")
    @Test
    void detectGodClass() {
        ClassMetrics god = require(godClassCollector, "com.ideacrest.parser.metrics.testclasses.GodClassKt");
        List<ClassDisharmony> flagged = new DisharmonyDetector().detectGodClasses(allClassMetrics(godClassCollector));
        boolean found = flagged.stream().anyMatch(d -> d.getClassName().equals(god.getFullyQualifiedName()));
        assertTrue(
                found,
                "GodClassKt should be flagged as God Class, got: "
                        + flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.joining(", ")));
    }

    @DisplayName("2. DataClass detector fires on Kotlin DataClassKt")
    @Test
    void detectDataClass() {
        ClassMetrics data = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.DataClassKt");
        List<ClassDisharmony> flagged = new DisharmonyDetector().detectDataClasses(allClassMetrics(parityCollector));
        boolean found = flagged.stream().anyMatch(d -> d.getClassName().equals(data.getFullyQualifiedName()));
        assertTrue(
                data.getNumberOfAttributes() >= 5,
                "DataClassKt should have >=5 attributes, was: " + data.getNumberOfAttributes());
        assertTrue(
                data.getNumberOfPublicAttributes() >= 5,
                "DataClassKt should have >=5 public attributes, was: " + data.getNumberOfPublicAttributes());
        assertTrue(
                found,
                "DataClassKt should be flagged as Data Class, got: "
                        + flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.joining(", ")));
    }

    @DisplayName("3. BrainMethod detector fires on Kotlin BrainClassKt.complexMethod*")
    @Test
    void detectBrainMethod() {
        ClassMetrics brainClass =
                require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.BrainClassKt");
        List<MethodDisharmony> flagged = new DisharmonyDetector().detectBrainMethods(allClassMetrics(parityCollector));
        boolean found = flagged.stream()
                .anyMatch(d -> d.getClassName().equals(brainClass.getFullyQualifiedName())
                        && (d.getMethodSignature().contains("complexMethod1")
                                || d.getMethodSignature().contains("complexMethod2")));
        assertTrue(
                found,
                "BrainClassKt.complexMethod* should be flagged as Brain Method, got: "
                        + flagged.stream()
                                .map(d -> d.getClassName() + "." + d.getMethodSignature())
                                .collect(Collectors.joining(", ")));
    }

    @DisplayName("4. BrainClass detector fires on Kotlin BrainClassKt")
    @Test
    void detectBrainClass() {
        ClassMetrics brainClass =
                require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.BrainClassKt");
        List<ClassDisharmony> flagged = new DisharmonyDetector().detectBrainClasses(allClassMetrics(parityCollector));
        boolean found = flagged.stream().anyMatch(d -> d.getClassName().equals(brainClass.getFullyQualifiedName()));
        assertTrue(
                found,
                "BrainClassKt should be flagged as Brain Class, got: "
                        + flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.joining(", ")));
    }

    @DisplayName("5. FeatureEnvy detector fires on Kotlin FeatureEnvyKt.methodWithFeatureEnvy")
    @Test
    void detectFeatureEnvy() {
        ClassMetrics envy = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.FeatureEnvyKt");
        List<MethodDisharmony> flagged = new DisharmonyDetector().detectFeatureEnvy(allClassMetrics(parityCollector));
        boolean found = flagged.stream()
                .anyMatch(d -> d.getClassName().equals(envy.getFullyQualifiedName())
                        && d.getMethodSignature().contains("methodWithFeatureEnvy"));
        assertTrue(
                found,
                "FeatureEnvyKt.methodWithFeatureEnvy should be flagged as Feature Envy, got: "
                        + flagged.stream()
                                .map(d -> d.getClassName() + "." + d.getMethodSignature())
                                .collect(Collectors.joining(", ")));
    }

    @DisplayName("6. IntensiveCoupling detector fires on Kotlin IntensiveCouplingKt")
    @Test
    void detectIntensiveCoupling() {
        ClassMetrics ic = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.IntensiveCouplingKt");
        List<MethodDisharmony> flagged =
                new DisharmonyDetector().detectIntensiveCoupling(allClassMetrics(parityCollector));
        boolean found = flagged.stream()
                .anyMatch(d -> d.getClassName().equals(ic.getFullyQualifiedName())
                        && d.getMethodSignature().contains("methodWithIntensiveCoupling"));
        assertTrue(
                found,
                "IntensiveCouplingKt should be flagged as Intensive Coupling, got: "
                        + flagged.stream()
                                .map(d -> d.getClassName() + "." + d.getMethodSignature())
                                .collect(Collectors.joining(", ")));
    }

    @DisplayName("7. DispersedCoupling detector fires on Kotlin DispersedCouplingKt")
    @Test
    void detectDispersedCoupling() {
        ClassMetrics dc = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.DispersedCouplingKt");
        List<MethodDisharmony> flagged =
                new DisharmonyDetector().detectDispersedCoupling(allClassMetrics(parityCollector));
        boolean found = flagged.stream()
                .anyMatch(d -> d.getClassName().equals(dc.getFullyQualifiedName())
                        && d.getMethodSignature().contains("methodWithDispersedCoupling"));
        assertTrue(
                found,
                "DispersedCouplingKt should be flagged as Dispersed Coupling, got: "
                        + flagged.stream()
                                .map(d -> d.getClassName() + "." + d.getMethodSignature())
                                .collect(Collectors.joining(", ")));
    }

    @DisplayName("8. ShotgunSurgery detector fires on Kotlin ShotgunSurgeryKt.performService")
    @Test
    void detectShotgunSurgery() {
        ClassMetrics target =
                require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.ShotgunSurgeryKt");
        List<MethodDisharmony> flagged =
                new DisharmonyDetector().detectShotgunSurgery(allClassMetrics(parityCollector));
        boolean found = flagged.stream()
                .anyMatch(d -> d.getClassName().equals(target.getFullyQualifiedName())
                        && d.getMethodSignature().contains("performService"));
        assertTrue(
                found,
                "ShotgunSurgeryKt.performService should be flagged as Shotgun Surgery, got: "
                        + flagged.stream()
                                .map(d -> d.getClassName() + "." + d.getMethodSignature())
                                .collect(Collectors.joining(", ")));
    }

    @DisplayName("9. RefusedParentBequest detector fires on Kotlin RefusedBequestKt")
    @Test
    void detectRefusedParentBequest() {
        ClassMetrics rb = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.RefusedBequestKt");
        List<ClassDisharmony> flagged =
                new DisharmonyDetector().detectRefusedParentBequest(allClassMetrics(parityCollector));
        boolean found = flagged.stream().anyMatch(d -> d.getClassName().equals(rb.getFullyQualifiedName()));
        assertTrue(
                found,
                "RefusedBequestKt should be flagged as Refused Parent Bequest, got: "
                        + flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.joining(", ")));
    }

    @DisplayName("10. TraditionBreaker detector fires on Kotlin TraditionBreakerKt")
    @Test
    void detectTraditionBreaker() {
        ClassMetrics tb = require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.TraditionBreakerKt");
        List<ClassDisharmony> flagged =
                new DisharmonyDetector().detectTraditionBreaker(allClassMetrics(parityCollector));
        boolean found = flagged.stream().anyMatch(d -> d.getClassName().equals(tb.getFullyQualifiedName()));
        assertTrue(
                found,
                "TraditionBreakerKt should be flagged as Tradition Breaker, got: "
                        + flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.joining(", ")));
    }

    @DisplayName("11. SignificantDuplication detector fires on Kotlin cross-class pair")
    @Test
    void detectSignificantDuplication() {
        require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.SignificantDuplicationCrossClassKtA");
        require(parityCollector, "com.ideacrest.parser.kotlin.disharmony.parity.SignificantDuplicationCrossClassKtB");
        List<ClassDisharmony> flagged =
                new DisharmonyDetector().detectSignificantDuplication(allClassMetrics(parityCollector));
        List<String> flaggedFqns =
                flagged.stream().map(ClassDisharmony::getClassName).collect(Collectors.toList());
        assertTrue(
                flaggedFqns.contains(
                                "com.ideacrest.parser.kotlin.disharmony.parity.SignificantDuplicationCrossClassKtA")
                        || flaggedFqns.contains(
                                "com.ideacrest.parser.kotlin.disharmony.parity.SignificantDuplicationCrossClassKtB"),
                "Kotlin cross-class pair should trigger Significant Duplication, got: " + flaggedFqns);
    }

    @DisplayName("All 11 detectors execute without throwing on Kotlin metrics")
    @Test
    void allDetectorsRunOnKotlinWithoutThrowing() {
        DisharmonyDetector detector = new DisharmonyDetector();
        List<ClassMetrics> all = new ArrayList<>(allClassMetrics(parityCollector));
        all.addAll(allClassMetrics(godClassCollector));
        assertDoesNotThrow(() -> detector.detectGodClasses(all));
        assertDoesNotThrow(() -> detector.detectDataClasses(all));
        assertDoesNotThrow(() -> detector.detectBrainMethods(all));
        assertDoesNotThrow(() -> detector.detectBrainClasses(all));
        assertDoesNotThrow(() -> detector.detectFeatureEnvy(all));
        assertDoesNotThrow(() -> detector.detectIntensiveCoupling(all));
        assertDoesNotThrow(() -> detector.detectDispersedCoupling(all));
        assertDoesNotThrow(() -> detector.detectShotgunSurgery(all));
        assertDoesNotThrow(() -> detector.detectRefusedParentBequest(all));
        assertDoesNotThrow(() -> detector.detectTraditionBreaker(all));
        assertDoesNotThrow(() -> detector.detectSignificantDuplication(all));
    }
}
