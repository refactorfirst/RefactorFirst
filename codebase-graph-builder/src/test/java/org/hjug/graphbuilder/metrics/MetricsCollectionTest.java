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
        Assertions.assertTrue(
                godClassMetrics.getWeightedMethodCount() >= 47,
                "GodClassExample WMC should be >= 47, was: " + godClassMetrics.getWeightedMethodCount());
        Assertions.assertTrue(
                godClassMetrics.getAccessToForeignData() > 5,
                "GodClassExample ATFD should be > 5, was: " + godClassMetrics.getAccessToForeignData());

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

        // Verify GodClassExample meets book-defined God Class thresholds
        // (Lanza & Marinescu: ATFD > 5, WMC >= 47, TCC < 1/3)
        ClassMetrics godClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.GodClassExample");
        Assertions.assertNotNull(godClass, "GodClassExample metrics should be collected");
        Assertions.assertTrue(
                godClass.getAccessToForeignData() > 5,
                "GodClassExample ATFD should be > 5 (accesses fields of more than 5 foreign classes), was: "
                        + godClass.getAccessToForeignData());
        Assertions.assertTrue(
                godClass.getWeightedMethodCount() >= 47,
                "GodClassExample WMC should be >= 47 (sum of cyclomatic complexity), was: "
                        + godClass.getWeightedMethodCount());
        Assertions.assertTrue(
                godClass.getTightClassCohesion() < 0.33,
                "GodClassExample TCC should be < 1/3, was: " + godClass.getTightClassCohesion());

        boolean foundGodClass = false;
        for (DisharmonyDetector.ClassDisharmony disharmony : godClasses) {
            if (disharmony.getClassName().contains("GodClassExample")) {
                foundGodClass = true;
                Assertions.assertEquals(DisharmonyTypes.GOD_CLASS, disharmony.getDisharmonyType());
                break;
            }
        }
        Assertions.assertTrue(foundGodClass, "GodClassExample should be detected as a God Class");
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

        boolean foundDataClass = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : dataClasses) {
            if (classDisharmony.getClassName().contains("DataClassExample")) {
                foundDataClass = true;
                Assertions.assertEquals(DisharmonyTypes.DATA_CLASS, classDisharmony.getDisharmonyType());
                break;
            }
        }
        Assertions.assertTrue(foundDataClass, "DataClassExample should be detected as a Data Class");
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

        // Count Brain Methods in BrainClassExample (Fig. 5.12 Term 1 requires > 1)
        DisharmonyDetector brainMethodDetector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> allBrainMethods = brainMethodDetector.detectBrainMethods(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));
        long brainMethodsInClass = allBrainMethods.stream()
                .filter(m -> m.getClassName().contains("BrainClassExample"))
                .count();
        Assertions.assertTrue(
                brainMethodsInClass > 1,
                "BrainClassExample should have > 1 Brain Methods (Term 1 path), found: " + brainMethodsInClass);

        Assertions.assertTrue(brainClasses.size() > 0, "Should detect at least one Brain Class");
        Assertions.assertTrue(
                brainClass.getLinesOfCode() >= 195,
                "BrainClassExample LOC should be >= 195 (VERY_HIGH), was: " + brainClass.getLinesOfCode());
        Assertions.assertTrue(
                brainClass.getWeightedMethodCount() >= 47,
                "BrainClassExample WMC should be >= 47 (VERY_HIGH), was: " + brainClass.getWeightedMethodCount());
        Assertions.assertTrue(
                brainClass.getTightClassCohesion() < 0.5,
                "BrainClassExample TCC should be < 0.5 (HALF), was: " + brainClass.getTightClassCohesion());

        boolean foundBrainClass = false;
        for (DisharmonyDetector.ClassDisharmony classDisharmony : brainClasses) {
            if (classDisharmony.getClassName().contains("BrainClassExample")) {
                foundBrainClass = true;
                Assertions.assertEquals(DisharmonyTypes.BRAIN_CLASS, classDisharmony.getDisharmonyType());
                break;
            }
        }
        Assertions.assertTrue(foundBrainClass, "BrainClassExample should be detected as a Brain Class");
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

        // Verify that methodWithFeatureEnvy meets the per-method thresholds
        MethodMetrics envyMethod = null;
        for (MethodMetrics m : featureEnvyClass.getMethods().values()) {
            if (m.getMethodName().equals("methodWithFeatureEnvy")) {
                envyMethod = m;
                break;
            }
        }
        Assertions.assertNotNull(envyMethod, "methodWithFeatureEnvy should be collected");

        System.out.println("\nFeatureEnvyExample.methodWithFeatureEnvy Metrics:");
        System.out.println("  ATFD: " + envyMethod.getAccessToForeignData());
        System.out.println("  LAA:  " + envyMethod.getLocalityOfAttributeAccess());
        System.out.println("  FDP:  " + envyMethod.getAccessedForeignClasses().size());

        Assertions.assertTrue(
                envyMethod.getAccessToForeignData() > 5,
                "methodWithFeatureEnvy ATFD should be > 5 (FEW), was: " + envyMethod.getAccessToForeignData());
        Assertions.assertTrue(
                envyMethod.getLocalityOfAttributeAccess() < 0.33,
                "methodWithFeatureEnvy LAA should be < 0.33 (ONE_THIRD), was: "
                        + envyMethod.getLocalityOfAttributeAccess());
        Assertions.assertTrue(
                envyMethod.getAccessedForeignClasses().size() <= 5,
                "methodWithFeatureEnvy FDP should be <= 5 (FEW), was: "
                        + envyMethod.getAccessedForeignClasses().size());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> featureEnvyMethods = detector.detectFeatureEnvy(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Feature Envy Methods Detected ===");
        for (DisharmonyDetector.MethodDisharmony disharmony : featureEnvyMethods) {
            System.out.println(disharmony.getClassName() + "." + disharmony.getMethodSignature() + ": "
                    + disharmony.getDescription());
        }

        Assertions.assertTrue(featureEnvyMethods.size() > 0, "Should detect at least one Feature Envy method");

        boolean foundFeatureEnvy = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : featureEnvyMethods) {
            if (disharmony.getClassName().contains("FeatureEnvyExample")
                    && disharmony.getMethodSignature().contains("methodWithFeatureEnvy")) {
                foundFeatureEnvy = true;
                Assertions.assertEquals(DisharmonyTypes.FEATURE_ENVY, disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("ATFD="));
                Assertions.assertTrue(disharmony.getDescription().contains("LAA="));
                Assertions.assertTrue(disharmony.getDescription().contains("FDP="));
                break;
            }
        }
        Assertions.assertTrue(
                foundFeatureEnvy, "FeatureEnvyExample.methodWithFeatureEnvy should be detected as Feature Envy");
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

        // Verify IntensiveCouplingExample.methodWithIntensiveCoupling metrics
        ClassMetrics intensiveClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.IntensiveCouplingExample");
        Assertions.assertNotNull(intensiveClass, "IntensiveCouplingExample should be collected");
        MethodMetrics intensiveMethod = null;
        for (MethodMetrics m : intensiveClass.getMethods().values()) {
            if (m.getMethodName().equals("methodWithIntensiveCoupling")) {
                intensiveMethod = m;
                break;
            }
        }
        Assertions.assertNotNull(intensiveMethod, "methodWithIntensiveCoupling should be collected");
        System.out.println("\nIntensiveCouplingExample.methodWithIntensiveCoupling Metrics:");
        System.out.println("  CINT: " + intensiveMethod.getCouplingIntensity());
        System.out.println("  CDISP: " + intensiveMethod.getCouplingDispersion());
        System.out.println("  MAXNESTING: " + intensiveMethod.getMaxNestingDepth());
        Assertions.assertTrue(
                intensiveMethod.getCouplingIntensity() > 7,
                "CINT should be > SHORT_MEMORY_CAP(7), was: " + intensiveMethod.getCouplingIntensity());
        Assertions.assertTrue(
                intensiveMethod.getCouplingDispersion() < 0.5,
                "CDISP should be < HALF(0.5), was: " + intensiveMethod.getCouplingDispersion());
        Assertions.assertTrue(
                intensiveMethod.getMaxNestingDepth() > 1,
                "MAXNESTING should be > SHALLOW(1), was: " + intensiveMethod.getMaxNestingDepth());

        Assertions.assertTrue(
                intensivelyCoupledMethods.size() > 0, "Should detect at least one Intensive Coupling method");

        boolean foundIntensiveCoupling = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : intensivelyCoupledMethods) {
            if (disharmony.getClassName().contains("IntensiveCouplingExample")) {
                foundIntensiveCoupling = true;
                Assertions.assertEquals(DisharmonyTypes.INTENSIVE_COUPLING, disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("CINT="));
                Assertions.assertTrue(disharmony.getDescription().contains("CDISP="));
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

        // Verify DispersedCouplingExample.methodWithDispersedCoupling metrics
        ClassMetrics dispersedClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.DispersedCouplingExample");
        Assertions.assertNotNull(dispersedClass, "DispersedCouplingExample should be collected");
        MethodMetrics dispersedMethod = null;
        for (MethodMetrics m : dispersedClass.getMethods().values()) {
            if (m.getMethodName().equals("methodWithDispersedCoupling")) {
                dispersedMethod = m;
                break;
            }
        }
        Assertions.assertNotNull(dispersedMethod, "methodWithDispersedCoupling should be collected");
        System.out.println("\nDispersedCouplingExample.methodWithDispersedCoupling Metrics:");
        System.out.println("  CINT: " + dispersedMethod.getCouplingIntensity());
        System.out.println("  CDISP: " + dispersedMethod.getCouplingDispersion());
        System.out.println("  MAXNESTING: " + dispersedMethod.getMaxNestingDepth());
        Assertions.assertTrue(
                dispersedMethod.getCouplingIntensity() > 7,
                "CINT should be > SHORT_MEMORY_CAP(7), was: " + dispersedMethod.getCouplingIntensity());
        Assertions.assertTrue(
                dispersedMethod.getCouplingDispersion() >= 0.5,
                "CDISP should be >= HALF(0.5), was: " + dispersedMethod.getCouplingDispersion());
        Assertions.assertTrue(
                dispersedMethod.getMaxNestingDepth() > 1,
                "MAXNESTING should be > SHALLOW(1), was: " + dispersedMethod.getMaxNestingDepth());

        Assertions.assertTrue(
                dispersedCoupledMethods.size() > 0, "Should detect at least one Dispersed Coupling method");

        boolean foundDispersedCoupling = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : dispersedCoupledMethods) {
            if (disharmony.getClassName().contains("DispersedCouplingExample")) {
                foundDispersedCoupling = true;
                Assertions.assertEquals(DisharmonyTypes.DISPERSED_COUPLING, disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("CINT="));
                Assertions.assertTrue(disharmony.getDescription().contains("CDISP="));
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

        // Verify that performService() has CM > 7 and CC > 7 (called by 8 distinct classes)
        ClassMetrics shotgunClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.ShotgunSurgeryExample");
        Assertions.assertNotNull(shotgunClass, "ShotgunSurgeryExample should be collected");
        MethodMetrics performService = null;
        for (MethodMetrics m : shotgunClass.getMethods().values()) {
            if (m.getMethodName().equals("performService")) {
                performService = m;
                break;
            }
        }
        Assertions.assertNotNull(performService, "performService should be collected");
        System.out.println("\nShotgunSurgeryExample.performService Metrics:");
        System.out.println("  CM: " + performService.getChangingMethodCount());
        System.out.println("  CC: " + performService.getChangingClassCount());
        Assertions.assertTrue(
                performService.getChangingMethodCount() > 7,
                "CM should be > SHORT_MEMORY_CAP(7), was: " + performService.getChangingMethodCount());
        Assertions.assertTrue(
                performService.getChangingClassCount() > 7,
                "CC should be > MANY(7), was: " + performService.getChangingClassCount());

        DisharmonyDetector detector = new DisharmonyDetector();
        List<DisharmonyDetector.MethodDisharmony> shotgunSurgeryMethods = detector.detectShotgunSurgery(
                List.copyOf(metricsCollector.getAllClassMetrics().values()));

        System.out.println("\n=== Shotgun Surgery Methods Detected ===");
        for (DisharmonyDetector.MethodDisharmony disharmony : shotgunSurgeryMethods) {
            System.out.println(disharmony.getClassName() + "." + disharmony.getMethodSignature() + ": "
                    + disharmony.getDescription());
        }

        Assertions.assertTrue(shotgunSurgeryMethods.size() > 0, "Should detect at least one Shotgun Surgery method");

        boolean foundShotgunSurgery = false;
        for (DisharmonyDetector.MethodDisharmony disharmony : shotgunSurgeryMethods) {
            if (disharmony.getClassName().contains("ShotgunSurgeryExample")
                    && disharmony.getMethodSignature().contains("performService")) {
                foundShotgunSurgery = true;
                Assertions.assertEquals(DisharmonyTypes.SHOTGUN_SURGERY, disharmony.getDisharmonyType());
                Assertions.assertTrue(disharmony.getDescription().contains("CM="));
                Assertions.assertTrue(disharmony.getDescription().contains("CC="));
                break;
            }
        }
        Assertions.assertTrue(
                foundShotgunSurgery, "ShotgunSurgeryExample.performService should be detected as Shotgun Surgery");
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
                Assertions.assertEquals(DisharmonyTypes.REFUSED_PARENT_BEQUEST, classDisharmony.getDisharmonyType());
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("NProtM="), "Description should include NProtM");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("BUR="), "Description should include BUR");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("BOvR="), "Description should include BOvR");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("NOM="), "Description should include NOM");
                ClassMetrics rbMetrics = classDisharmony.getMetrics();
                int nom = rbMetrics.getNumberOfMethods();
                int overridden = rbMetrics.getNumberOfOverriddenMethods();
                int nprotm = 15; // BaseService has 15 protected members
                int usedParent = rbMetrics.getNumberOfUsedParentMembers();
                int wmc = rbMetrics.getWeightedMethodCount();
                double amw = nom > 0 ? (double) wmc / nom : 0.0;
                // refusesBequest: BOvR < 1/3 OR (NProtM > FEW AND BUR < 1/3)
                double bovr = nom > 0 ? (double) overridden / nom : 0.0;
                double bur = nprotm > 0 ? (double) usedParent / nprotm : 0.0;
                boolean refusesBequest = bovr < (1.0 / 3.0) || (nprotm > 5 && bur < (1.0 / 3.0));
                Assertions.assertTrue(
                        refusesBequest,
                        "Should satisfy BOvR<1/3 OR (NProtM>5 AND BUR<1/3), got BOvR=" + bovr + " BUR=" + bur
                                + " NProtM=" + nprotm);
                // isLargeClass: NOM > 7 AND (AMW > 2.0 OR WMC > 14)
                Assertions.assertTrue(nom > 7, "NOM should be > 7 (NOM_AVERAGE), got: " + nom);
                Assertions.assertTrue(
                        amw > 2.0 || wmc > 14, "Should satisfy AMW > 2.0 OR WMC > 14, got AMW=" + amw + " WMC=" + wmc);
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
                Assertions.assertEquals(DisharmonyTypes.TRADITION_BREAKER, classDisharmony.getDisharmonyType());

                ClassMetrics tbMetrics = classDisharmony.getMetrics();
                int nom = tbMetrics.getNumberOfMethods();
                int overridden = tbMetrics.getNumberOfOverriddenMethods();
                int nas = nom - overridden;
                double pnas = nom > 0 ? (double) nas / nom : 0.0;
                int wmc = tbMetrics.getWeightedMethodCount();
                double amw = nom > 0 ? (double) wmc / nom : 0.0;

                // Condition 1: NAS >= 7 AND PNAS >= 0.67
                Assertions.assertTrue(nas >= 7, "NAS should be >= 7 (NOM_AVERAGE), got: " + nas);
                Assertions.assertTrue(pnas >= 0.67, "PNAS should be >= 0.67 (TWO_THIRDS), got: " + pnas);

                // Condition 2: NOM >= 12 AND (AMW > 2.0 OR WMC >= 47)
                Assertions.assertTrue(nom >= 12, "NOM should be >= 12 (NOM_HIGH), got: " + nom);
                Assertions.assertTrue(
                        amw > 2.0 || wmc >= 47,
                        "Should satisfy AMW > 2.0 OR WMC >= 47, got AMW=" + amw + " WMC=" + wmc);

                // Description includes key metrics
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("NAS="), "Description should include NAS=");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("PNAS="), "Description should include PNAS=");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("NOM="), "Description should include NOM=");
                Assertions.assertTrue(
                        classDisharmony.getDescription().contains("Overridden="),
                        "Description should include Overridden=");
                break;
            }
        }
        Assertions.assertTrue(foundTraditionBreaker, "TraditionBreakerExample should be detected as Tradition Breaker");
    }

    @Test
    void sourceFilePathCapturedForAllClasses() throws IOException {
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

        ClassMetrics godClass =
                metricsCollector.getClassMetrics("org.hjug.graphbuilder.metrics.testclasses.GodClassExample");
        Assertions.assertNotNull(godClass, "GodClassExample metrics should be collected");
        Assertions.assertNotNull(godClass.getSourceFilePath(), "GodClassExample sourceFilePath should not be null");
        Assertions.assertTrue(
                godClass.getSourceFilePath().contains("GodClassExample"),
                "sourceFilePath should reference the source file, was: " + godClass.getSourceFilePath());

        for (ClassMetrics classMetrics : metricsCollector.getAllClassMetrics().values()) {
            Assertions.assertNotNull(
                    classMetrics.getSourceFilePath(),
                    "sourceFilePath should not be null for " + classMetrics.getFullyQualifiedName());
        }
    }

    @Test
    void javadocMethodReferenceIsNotCountedAsForeignMethodCall() throws IOException {
        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/metrics/testclasses/javadoc");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphMetricsCollector metricsCollector = new GraphMetricsCollector(classGraph, packageGraph);
        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser
                .parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> metricsVisitor.visit(cu, ctx));

        metricsCollector.finalizeMetrics();

        ClassMetrics metrics = metricsCollector.getClassMetrics(
                "org.hjug.graphbuilder.metrics.testclasses.javadoc.MethodWithJavadocReference");
        Assertions.assertNotNull(metrics, "MethodWithJavadocReference should be collected");

        Assertions.assertEquals(
                0,
                metrics.getCouplingBetweenObjects(),
                "CBO must be 0: Semaphore.acquire() is Javadoc-only and must not be counted as a foreign dependency. Got: "
                        + metrics.getCouplingBetweenObjects());
    }
}
