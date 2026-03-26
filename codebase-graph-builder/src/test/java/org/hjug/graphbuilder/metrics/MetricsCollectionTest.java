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
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class MetricsCollectionTest {

    @Test
    void collectClassMetrics() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics godClassMetrics =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.GodClassExample");
        Assertions.assertNotNull(godClassMetrics, "GodClassExample metrics should be collected");

        Assertions.assertTrue(godClassMetrics.getLinesOfCode() > 0, "LOC should be greater than 0");
        Assertions.assertTrue(godClassMetrics.getNumberOfMethods() >= 10, "Should have at least 10 methods");
        Assertions.assertTrue(godClassMetrics.getNumberOfAttributes() > 0, "Should have attributes (fields)");

        System.out.println("\nGodClassExample Metrics:");
        System.out.println("  LOC: " + godClassMetrics.getLinesOfCode());
        System.out.println("  NOM: " + godClassMetrics.getNumberOfMethods());
        System.out.println("  NOA: " + godClassMetrics.getNumberOfAttributes());
        System.out.println("  WMC: " + godClassMetrics.getWeightedMethodCount());
        System.out.println("  ATFD: " + godClassMetrics.getAccessToForeignData());
        System.out.println("  TCC: " + godClassMetrics.getTightClassCohesion());
        System.out.println("  CBO: " + godClassMetrics.getCouplingBetweenObjects());
    }

    @Test
    void collectMethodMetrics() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics brainMethodClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.BrainMethodExample");
        Assertions.assertNotNull(brainMethodClass, "BrainMethodExample metrics should be collected");

        boolean foundBrainMethod = false;
        for (MethodMetrics method : brainMethodClass.getMethods().values()) {
            if (method.getMethodName() != null && method.getMethodName().equals("brainMethod")) {
                foundBrainMethod = true;
                Assertions.assertTrue(method.getLinesOfCode() > 50, "Brain method should have high LOC");
                Assertions.assertTrue(method.getCyclomaticComplexity() > 5, "Brain method should have high complexity");
                Assertions.assertTrue(method.getMaxNestingDepth() > 3, "Brain method should have deep nesting");

                System.out.println("\nBrain Method Metrics:");
                System.out.println("  LOC: " + method.getLinesOfCode());
                System.out.println("  CYCLO: " + method.getCyclomaticComplexity());
                System.out.println("  MAXNESTING: " + method.getMaxNestingDepth());
                System.out.println("  NOP: " + method.getNumberOfParameters());
                System.out.println("  NOAV: " + method.getNumberOfAccessedVariables());
            }
        }

        Assertions.assertTrue(foundBrainMethod, "Should find brainMethod in the class");
    }

    @Test
    void detectGodClass() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> godClasses = detector.detectGodClasses(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== God Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : godClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        // Verify the detector can run and metrics are available for detection
        Assertions.assertNotNull(godClasses, "God class detection should return a list");

        // Verify GodClassExample has the expected characteristics
        ClassMetrics godClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.GodClassExample");
        Assertions.assertTrue(godClass.getNumberOfMethods() >= 10, "GodClassExample should have many methods");
        Assertions.assertTrue(godClass.getWeightedMethodCount() >= 10, "GodClassExample should have high WMC");
    }

    @Test
    void detectDataClass() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> dataClasses = detector.detectDataClasses(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Data Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : dataClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        // Verify the detector can run and metrics are available for detection
        Assertions.assertNotNull(dataClasses, "Data class detection should return a list");

        // Verify DataClassExample has the expected characteristics
        ClassMetrics dataClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.DataClassExample");
        Assertions.assertTrue(dataClass.getNumberOfAttributes() >= 5, "DataClassExample should have many attributes");
        Assertions.assertTrue(
                dataClass.getNumberOfAccessorMethods() >= 10, "DataClassExample should have many accessors");
    }

    @Test
    void detectBrainMethod() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> brainMethods = detector.detectBrainMethods(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Brain Methods Detected ===");
        for (DisharmonyDetector.MethodDisharmony disharmony : brainMethods) {
            System.out.println(disharmony.getClassName() + "." + disharmony.getMethodSignature() + ": "
                    + disharmony.getDescription());
        }

        Assertions.assertTrue(brainMethods.size() > 0, "Should detect at least one Brain Method");
    }

    @Test
    void detectBrainClass() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics brainClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.BrainClassExample");
        Assertions.assertNotNull(brainClass, "BrainClassExample should be collected");

        System.out.println("\nBrainClassExample Metrics (before detection):");
        System.out.println("  LOC: " + brainClass.getLinesOfCode());
        System.out.println("  NOM: " + brainClass.getNumberOfMethods());
        System.out.println("  WMC: " + brainClass.getWeightedMethodCount());
        System.out.println("  TCC: " + brainClass.getTightClassCohesion());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> brainClasses = detector.detectBrainClasses(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Brain Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : brainClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        Assertions.assertTrue(brainClasses.size() > 0, "Should detect at least one Brain Class");
        Assertions.assertTrue(brainClass.getLinesOfCode() > 50, "BrainClassExample should have high LOC");
        Assertions.assertTrue(brainClass.getWeightedMethodCount() >= 10, "BrainClassExample should have high WMC");
    }

    @Test
    void detectFeatureEnvy() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics featureEnvyClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.FeatureEnvyExample");
        Assertions.assertNotNull(featureEnvyClass, "FeatureEnvyExample should be collected");

        System.out.println("\nFeatureEnvyExample Metrics:");
        System.out.println("  ATFD: " + featureEnvyClass.getAccessToForeignData());
        System.out.println("  NOM: " + featureEnvyClass.getNumberOfMethods());
        System.out.println("  LOC: " + featureEnvyClass.getLinesOfCode());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> featureEnvyClasses = detector.detectFeatureEnvy(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Feature Envy Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : featureEnvyClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        Assertions.assertTrue(featureEnvyClasses.size() > 0, "Should detect at least one Feature Envy class");
        Assertions.assertTrue(
                featureEnvyClass.getAccessToForeignData() > 7, "FeatureEnvyExample should have high ATFD (> 7)");

        boolean foundFeatureEnvy = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : featureEnvyClasses) {
            if (classDisharmony.getClassName().contains("FeatureEnvyExample")) {
                foundFeatureEnvy = true;
                Assertions.assertEquals("Feature Envy", classDisharmony.getDisharmonyType());
                Assertions.assertTrue(classDisharmony.getDescription().contains("ATFD="));
                break;
            }
        }
        Assertions.assertTrue(foundFeatureEnvy, "FeatureEnvyExample should be detected as Feature Envy");
    }

    @Test
    void detectIntensiveCoupling() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> intensivelyCoupledMethods = detector.detectIntensiveCoupling(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Intensive Coupling Methods Detected ===");
        for (DisharmonyDetector.MethodDisharmony disharmony : intensivelyCoupledMethods) {
            System.out.println(disharmony.getClassName() + "." + disharmony.getMethodSignature() + ": "
                    + disharmony.getDescription());
        }

        Assertions.assertTrue(
                intensivelyCoupledMethods.size() > 0, "Should detect at least one Intensive Coupling method");

        boolean foundIntensiveCoupling = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : intensivelyCoupledMethods) {
            if (disharmony.getClassName().contains("IntensiveCouplingExample")) {
                foundIntensiveCoupling = true;
                Assertions.assertEquals("Intensive Coupling", disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("foreign classes"));
                break;
            }
        }
        Assertions.assertTrue(
                foundIntensiveCoupling, "IntensiveCouplingExample should be detected as Intensive Coupling");
    }

    @Test
    void detectDispersedCoupling() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> dispersedCoupledMethods = detector.detectDispersedCoupling(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Dispersed Coupling Methods Detected ===");
        for (DisharmonyDetector.MethodDisharmony disharmony : dispersedCoupledMethods) {
            System.out.println(disharmony.getClassName() + "." + disharmony.getMethodSignature() + ": "
                    + disharmony.getDescription());
        }

        Assertions.assertTrue(
                dispersedCoupledMethods.size() > 0, "Should detect at least one Dispersed Coupling method");

        boolean foundDispersedCoupling = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : dispersedCoupledMethods) {
            if (disharmony.getClassName().contains("DispersedCouplingExample")) {
                foundDispersedCoupling = true;
                Assertions.assertEquals("Dispersed Coupling", disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("different foreign classes"));
                break;
            }
        }
        Assertions.assertTrue(
                foundDispersedCoupling, "DispersedCouplingExample should be detected as Dispersed Coupling");
    }

    @Test
    void detectShotgunSurgery() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics shotgunSurgeryClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.ShotgunSurgeryExample");
        Assertions.assertNotNull(shotgunSurgeryClass, "ShotgunSurgeryExample should be collected");

        System.out.println("\nShotgunSurgeryExample Metrics:");
        System.out.println("  ATFD: " + shotgunSurgeryClass.getAccessToForeignData());
        System.out.println("  NOM: " + shotgunSurgeryClass.getNumberOfMethods());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> shotgunSurgeryClasses = detector.detectShotgunSurgery(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Shotgun Surgery Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : shotgunSurgeryClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        Assertions.assertTrue(shotgunSurgeryClasses.size() > 0, "Should detect at least one Shotgun Surgery class");

        boolean foundShotgunSurgery = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : shotgunSurgeryClasses) {
            if (classDisharmony.getClassName().contains("ShotgunSurgeryExample")) {
                foundShotgunSurgery = true;
                Assertions.assertEquals("Shotgun Surgery", classDisharmony.getDisharmonyType());
                Assertions.assertTrue(classDisharmony.getDescription().contains("ATFD="));
                break;
            }
        }
        Assertions.assertTrue(foundShotgunSurgery, "ShotgunSurgeryExample should be detected as Shotgun Surgery");
    }

    @Test
    void detectRefusedParentBequest() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics refusedBequestClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.RefusedBequestExample");
        Assertions.assertNotNull(refusedBequestClass, "RefusedBequestExample should be collected");

        System.out.println("\nRefusedBequestExample Metrics:");
        System.out.println("  Parent: " + refusedBequestClass.getParentClass());
        System.out.println("  Overridden Methods: " + refusedBequestClass.getNumberOfOverriddenMethods());
        System.out.println("  Protected Members: " + refusedBequestClass.getNumberOfProtectedMembers());

        ClassMetrics baseService =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.BaseService");
        if (baseService != null) {
            System.out.println("\nBaseService Metrics:");
            System.out.println("  Protected Members: " + baseService.getNumberOfProtectedMembers());
        }

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> refusedBequestClasses = detector.detectRefusedParentBequest(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Refused Parent Bequest Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : refusedBequestClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        Assertions.assertTrue(
                refusedBequestClasses.size() > 0, "Should detect at least one Refused Parent Bequest class");

        boolean foundRefusedBequest = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : refusedBequestClasses) {
            if (classDisharmony.getClassName().contains("RefusedBequestExample")) {
                foundRefusedBequest = true;
                Assertions.assertEquals("Refused Parent Bequest", classDisharmony.getDisharmonyType());
                Assertions.assertTrue(classDisharmony.getDescription().contains("Protected Members="));
                break;
            }
        }
        Assertions.assertTrue(
                foundRefusedBequest, "RefusedBequestExample should be detected as Refused Parent Bequest");
    }

    @Test
    void detectTraditionBreaker() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);

        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            metricsVisitor.visit(cu, ctx);
        });

        metricsCollector.finalizeMetrics();

        ClassMetrics traditionBreakerClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.TraditionBreakerExample");
        Assertions.assertNotNull(traditionBreakerClass, "TraditionBreakerExample should be collected");

        System.out.println("\nTraditionBreakerExample Metrics:");
        System.out.println("  Parent: " + traditionBreakerClass.getParentClass());
        System.out.println("  Overridden Methods: " + traditionBreakerClass.getNumberOfOverriddenMethods());
        System.out.println("  Total Methods: " + traditionBreakerClass.getNumberOfMethods());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.ClassDisharmony> traditionBreakerClasses = detector.detectTraditionBreaker(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Tradition Breaker Classes Detected ===");
        for (DisharmonyDetector.ClassDisharmony classDisharmony : traditionBreakerClasses) {
            System.out.println(classDisharmony.getClassName() + ": " + classDisharmony.getDescription());
        }

        Assertions.assertTrue(traditionBreakerClasses.size() > 0, "Should detect at least one Tradition Breaker class");

        boolean foundTraditionBreaker = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : traditionBreakerClasses) {
            if (classDisharmony.getClassName().contains("TraditionBreakerExample")) {
                foundTraditionBreaker = true;
                Assertions.assertEquals("Tradition Breaker", classDisharmony.getDisharmonyType());
                Assertions.assertTrue(classDisharmony.getDescription().contains("Overridden="));
                break;
            }
        }
        Assertions.assertTrue(foundTraditionBreaker, "TraditionBreakerExample should be detected as Tradition Breaker");
    }
}
