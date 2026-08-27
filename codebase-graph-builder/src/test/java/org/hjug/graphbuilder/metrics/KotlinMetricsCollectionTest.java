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
 * Kotlin metrics-collection smoke test. Mirrors
 * {@link MetricsCollectionTest#collectClassMetrics()} and
 * {@link MetricsCollectionTest#detectGodClass()} against a plain-text
 * {@code .kt} fixture that is the Kotlin structural twin of
 * {@code GodClassExample.java}.
 *
 * <p>All {@code .kt} files live under
 * {@code src/test/resources/kotlinMetricsSrcDirectory} as plain-text inputs
 * to the OpenRewrite Kotlin parser — they are NOT compiled Kotlin source.
 */
class KotlinMetricsCollectionTest {

    @DisplayName("Kotlin God Class fixture yields LOC/NOM/ATFD/WMC metrics")
    @Test
    void collectKotlinClassMetrics() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinMetricsSrcDirectory");

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

        List<Path> list = Files.walk(Path.of(srcDirectory.getAbsolutePath()))
                .filter(p -> p.toString().endsWith(".kt"))
                .collect(Collectors.toList());
        kotlinParser
                .parse(list, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        ClassMetrics godClassMetrics =
                metricsCollector.getClassMetrics("com.ideacrest.parser.metrics.testclasses.GodClassKt");
        assertNotNull(godClassMetrics, "GodClassKt metrics should be collected");

        assertTrue(godClassMetrics.getLinesOfCode() > 0, "LOC should be greater than 0");
        assertTrue(
                godClassMetrics.getNumberOfMethods() >= 10,
                "Should have at least 10 methods, was: " + godClassMetrics.getNumberOfMethods());
        assertTrue(
                godClassMetrics.getNumberOfAttributes() > 0,
                "Should have attributes (1 per foreign-service field + record components if any)");

        System.out.println("\nGodClassKt Metrics:");
        System.out.println("  LOC: " + godClassMetrics.getLinesOfCode());
        System.out.println("  NOM: " + godClassMetrics.getNumberOfMethods());
        System.out.println("  NOA: " + godClassMetrics.getNumberOfAttributes());
        System.out.println("  WMC: " + godClassMetrics.getWeightedMethodCount());
        System.out.println("  ATFD: " + godClassMetrics.getAccessToForeignData());
        System.out.println("  TCC: " + godClassMetrics.getTightClassCohesion());
        System.out.println("  CBO: " + godClassMetrics.getCouplingBetweenObjects());
    }

    @DisplayName("Kotlin God Class fixture is detected as a God Class")
    @Test
    void detectKotlinGodClass() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinMetricsSrcDirectory");

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

        List<Path> list = Files.walk(Path.of(srcDirectory.getAbsolutePath()))
                .filter(p -> p.toString().endsWith(".kt"))
                .collect(Collectors.toList());
        kotlinParser
                .parse(list, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> godClasses = detector.detectGodClasses(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Kotlin God Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony disharmony : godClasses) {
            System.out.println(disharmony.getClassName() + ": " + disharmony.getDescription());
        }

        boolean foundGodClass = false;
        for (DisharmonyDetector.ClassDisharmony disharmony : godClasses) {
            if (disharmony.getClassName().contains("GodClassKt")) {
                foundGodClass = true;
                assertEquals(DisharmonyTypes.GOD_CLASS, disharmony.getDisharmonyType());
                break;
            }
        }
        assertTrue(foundGodClass, "GodClassKt should be detected as a God Class");
    }

    @DisplayName("Kotlin callable references bump numberOfCallableReferences on ClassMetrics")
    @Test
    void collectKotlinCallableReferenceCount() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinCallableRefSrcDirectory");

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

        List<Path> list = Files.walk(Path.of(srcDirectory.getAbsolutePath()))
                .filter(p -> p.toString().endsWith(".kt"))
                .collect(Collectors.toList());
        kotlinParser
                .parse(list, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        ClassMetrics userMetrics = metricsCollector.getClassMetrics("com.ideacrest.parser.callref.CallableRefUser");
        assertNotNull(userMetrics, "CallableRefUser metrics should be collected");

        System.out.println("\nCallableRefUser Callable References:");
        for (MethodMetrics method : userMetrics.getMethods().values()) {
            System.out.println("  " + method.getSignature() + ": " + method.getNumberOfCallableReferences());
        }
        System.out.println("  class-level total: " + userMetrics.getNumberOfCallableReferences());

        // The `methodScopedRefs()` method declares two method-local
        // callable references (`CallableRefTarget::alpha` and `CallableRefTarget::beta`),
        // both of which should bump the per-method (and class-aggregated)
        // numberOfCallableReferences counter.
        assertTrue(
                userMetrics.getNumberOfCallableReferences() >= 2,
                "CallableRefUser should have at least 2 callable references (from methodScopedRefs), was: "
                        + userMetrics.getNumberOfCallableReferences());
    }

    @DisplayName("Kotlin generic class/method/property type-parameter bounds populate typeParameterFqns "
            + "on ClassMetrics and MethodMetrics")
    @Test
    void collectTypeParameterFqnsMetrics() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinTypeParamSrcDirectory");

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

        List<Path> list = Files.walk(Path.of(srcDirectory.getAbsolutePath()))
                .filter(p -> p.toString().endsWith(".kt"))
                .collect(Collectors.toList());
        kotlinParser
                .parse(list, Path.of(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        ClassMetrics holderMetrics = metricsCollector.getClassMetrics("com.ideacrest.parser.typeparams.GenericHolder");
        assertNotNull(holderMetrics, "GenericHolder metrics should be collected");

        System.out.println("\nGenericHolder typeParameterFqns: " + holderMetrics.getTypeParameterFqns());
        for (MethodMetrics m : holderMetrics.getMethods().values()) {
            System.out.println("  " + m.getSignature() + " -> " + m.getTypeParameterFqns());
        }

        // The class-level bound (`class GenericHolder<T : MetaClassA>`) and
        // the method-level bound (`fun <U : MetaClassA> process(...)`) both
        // reference MetaClassA; both should land on the aggregrate class
        // typeParameterFqns set.
        assertTrue(
                holderMetrics.getTypeParameterFqns().contains("com.ideacrest.parser.typeparams.MetaClassA"),
                "Class typeParameterFqns should contain MetaClassA, was: " + holderMetrics.getTypeParameterFqns());

        // At least one method on GenericHolder should also record MetaClassA
        // as a per-method type-parameter bound.
        boolean foundMethodLevel = holderMetrics.getMethods().values().stream()
                .map(MethodMetrics::getTypeParameterFqns)
                .anyMatch(fqns -> fqns.contains("com.ideacrest.parser.typeparams.MetaClassA"));
        assertTrue(foundMethodLevel, "At least one method should have MetaClassA in its typeParameterFqns");
    }
}
