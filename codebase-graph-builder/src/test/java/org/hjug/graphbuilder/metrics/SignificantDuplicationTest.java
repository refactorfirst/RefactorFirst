package org.hjug.graphbuilder.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignificantDuplicationTest {

    private static final String INTRA_CLASS_FQN =
            "org.hjug.graphbuilder.metrics.testclasses.SignificantDuplicationIntraClass";
    private static final String CROSS_CLASS_A_FQN =
            "org.hjug.graphbuilder.metrics.testclasses.SignificantDuplicationCrossClassA";
    private static final String CROSS_CLASS_B_FQN =
            "org.hjug.graphbuilder.metrics.testclasses.SignificantDuplicationCrossClassB";
    private static final String CLEAN_CLASS_FQN =
            "org.hjug.graphbuilder.metrics.testclasses.SignificantDuplicationCleanClass";

    private GraphMetricsCollector metricsCollector;
    private List<DisharmonyDetector.ClassDisharmony> detected;

    @BeforeAll
    void setup() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);
        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        detected = detector.detectSignificantDuplication(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Significant Duplication Detected ===");
        for (DisharmonyDetector.ClassDisharmony d : detected) {
            System.out.println(d.getClassName() + ": " + d.getDescription());
        }
    }

    @Test
    void bodyLinesPopulatedForFixtureMethods() {
        ClassMetrics intraClass = metricsCollector.getClassMetrics(INTRA_CLASS_FQN);
        Assertions.assertNotNull(intraClass, "IntraClass fixture should be collected");

        for (MethodMetrics method : intraClass.getMethods().values()) {
            if (method.getMethodName().equals("methodA")
                    || method.getMethodName().equals("methodB")) {
                System.out.println(method.getMethodName() + " body lines: "
                        + method.getNormalizedBodyLines().size());
                Assertions.assertFalse(
                        method.getNormalizedBodyLines().isEmpty(),
                        method.getMethodName() + " should have normalized body lines");
                Assertions.assertTrue(
                        method.getNormalizedBodyLines().size() >= 5,
                        method.getMethodName() + " should have at least FEW(5) body lines, got: "
                                + method.getNormalizedBodyLines().size());
            }
        }
    }

    @Test
    void detectsIntraClassSignificantDuplication() {
        boolean found = detected.stream().anyMatch(d -> d.getClassName().equals(INTRA_CLASS_FQN));
        Assertions.assertTrue(found, "SignificantDuplicationIntraClass should be flagged (intra-class chain)");

        detected.stream()
                .filter(d -> d.getClassName().equals(INTRA_CLASS_FQN))
                .findFirst()
                .ifPresent(d -> {
                    Assertions.assertEquals(DisharmonyTypes.SIGNIFICANT_DUPLICATION, d.getDisharmonyType());
                    Assertions.assertTrue(d.getDescription().contains("SEC="), "Description should include SEC=");
                    Assertions.assertTrue(d.getDescription().contains("SDC="), "Description should include SDC=");

                    double sec = d.getMetricValues().stream()
                            .filter(m -> m.getName().equals("SEC"))
                            .findFirst()
                            .map(DisharmonyMetric::getValue)
                            .orElse(0.0);
                    double sdc = d.getMetricValues().stream()
                            .filter(m -> m.getName().equals("SDC"))
                            .findFirst()
                            .map(DisharmonyMetric::getValue)
                            .orElse(0.0);

                    System.out.println("IntraClass SEC=" + sec + " SDC=" + sdc);
                    Assertions.assertTrue(sec > 0, "SEC should be > 0");
                    Assertions.assertTrue(sdc >= 13, "SDC should be >= 13 (chain threshold), was: " + sdc);
                });
    }

    @Test
    void detectsCrossClassSignificantDuplication() {
        boolean foundA = detected.stream().anyMatch(d -> d.getClassName().equals(CROSS_CLASS_A_FQN));
        boolean foundB = detected.stream().anyMatch(d -> d.getClassName().equals(CROSS_CLASS_B_FQN));

        Assertions.assertTrue(foundA, "SignificantDuplicationCrossClassA should be flagged");
        Assertions.assertTrue(foundB, "SignificantDuplicationCrossClassB should be flagged");

        detected.stream()
                .filter(d -> d.getClassName().equals(CROSS_CLASS_A_FQN))
                .findFirst()
                .ifPresent(d -> {
                    Assertions.assertEquals(DisharmonyTypes.SIGNIFICANT_DUPLICATION, d.getDisharmonyType());
                    double sdc = d.getMetricValues().stream()
                            .filter(m -> m.getName().equals("SDC"))
                            .findFirst()
                            .map(DisharmonyMetric::getValue)
                            .orElse(0.0);
                    Assertions.assertTrue(sdc >= 13, "CrossClassA SDC should be >= 13, was: " + sdc);
                });
    }

    @Test
    void cleanClassNotDetected() {
        ClassMetrics cleanClass = metricsCollector.getClassMetrics(CLEAN_CLASS_FQN);
        Assertions.assertNotNull(cleanClass, "CleanClass fixture should be collected");

        for (MethodMetrics method : cleanClass.getMethods().values()) {
            System.out.println("CleanClass." + method.getMethodName() + " body lines: "
                    + method.getNormalizedBodyLines().size());
            Assertions.assertTrue(
                    method.getNormalizedBodyLines().size() < 5,
                    "CleanClass methods should have < FEW(5) body lines and be ineligible, got: "
                            + method.getNormalizedBodyLines().size());
        }

        boolean found = detected.stream().anyMatch(d -> d.getClassName().equals(CLEAN_CLASS_FQN));
        Assertions.assertFalse(found, "SignificantDuplicationCleanClass should not be flagged");
    }

    @Test
    void detectedDuplicationPartnersIncludesMethodNames() {
        DisharmonyDetector.ClassDisharmony intraClass = detected.stream()
                .filter(d -> d.getClassName().equals(INTRA_CLASS_FQN))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(intraClass, "SignificantDuplicationIntraClass must be detected");

        String partners = intraClass.getDuplicationPartners();
        System.out.println("IntraClass duplicationPartners: " + partners);
        Assertions.assertNotNull(partners, "duplicationPartners should not be null");
        Assertions.assertTrue(partners.contains("methodA"), "Partners should contain 'methodA', was: " + partners);
        Assertions.assertTrue(partners.contains("methodB"), "Partners should contain 'methodB', was: " + partners);
    }

    @Test
    void detectedDuplicationPartnersIncludesPartnerClassForCrossClass() {
        DisharmonyDetector.ClassDisharmony crossA = detected.stream()
                .filter(d -> d.getClassName().equals(CROSS_CLASS_A_FQN))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(crossA, "SignificantDuplicationCrossClassA must be detected");

        String partners = crossA.getDuplicationPartners();
        System.out.println("CrossClassA duplicationPartners: " + partners);
        Assertions.assertNotNull(partners, "duplicationPartners should not be null");
        Assertions.assertTrue(
                partners.contains("CrossClassB"),
                "Partners should contain partner class 'CrossClassB', was: " + partners);
        Assertions.assertTrue(
                partners.contains("computeResult"),
                "Partners should contain partner method 'computeResult', was: " + partners);
    }

    @Test
    void detectedDisharmoniesHaveCorrectMetricStructure() {
        Assertions.assertFalse(detected.isEmpty(), "Should detect at least one Significant Duplication");

        for (DisharmonyDetector.ClassDisharmony d : detected) {
            Assertions.assertEquals(DisharmonyTypes.SIGNIFICANT_DUPLICATION, d.getDisharmonyType());
            Assertions.assertNotNull(d.getMetricValues(), "Metric values should not be null");
            Assertions.assertEquals(2, d.getMetricValues().size(), "Should have exactly 2 metrics (SEC and SDC)");

            boolean hasSEC =
                    d.getMetricValues().stream().anyMatch(m -> m.getName().equals("SEC"));
            boolean hasSDC =
                    d.getMetricValues().stream().anyMatch(m -> m.getName().equals("SDC"));
            Assertions.assertTrue(hasSEC, "Should have SEC metric for " + d.getClassName());
            Assertions.assertTrue(hasSDC, "Should have SDC metric for " + d.getClassName());

            Assertions.assertNotNull(d.getMetrics(), "ClassMetrics should not be null");
            Assertions.assertNotNull(d.getClassName(), "Class name should not be null");
        }
    }
}
